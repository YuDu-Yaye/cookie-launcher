package dev.cookielauncher.core.net;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Hashing;
import dev.cookielauncher.core.util.Progress;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared HTTP transport.
 *
 * <p>One {@link HttpClient} with HTTP/2 and a reused connection pool serves every
 * request, which is what actually makes launcher downloads fast: the TLS
 * handshake and TCP warm-up are paid once per host, not once per file. Range
 * requests make interrupted downloads resumable instead of restarting at zero.</p>
 */
public final class Http {

    private static final AtomicReference<HttpClient> CLIENT = new AtomicReference<>();
    private static final Map<String, Boolean> HOST_HEALTH = new ConcurrentHashMap<>();

    private static volatile String userAgent =
            "CookieLauncher/" + dev.cookielauncher.core.AppInfo.VERSION
                    + " (+https://github.com/YuDu-Yaye/cookie-launcher)";
    private static volatile String proxySpec = "";
    private static volatile Duration connectTimeout = Duration.ofSeconds(15);

    private Http() {
    }

    public static void setUserAgent(String value) {
        userAgent = value;
        reset();
    }

    public static void setProxy(String spec) {
        proxySpec = spec == null ? "" : spec.trim();
        reset();
    }

    public static String proxy() {
        return proxySpec;
    }

    public static void setConnectTimeoutSeconds(int seconds) {
        connectTimeout = Duration.ofSeconds(Math.max(1, seconds));
        reset();
    }

    private static void reset() {
        CLIENT.set(null);
        HOST_HEALTH.clear();
    }

    public static HttpClient client() {
        HttpClient current = CLIENT.get();
        if (current != null) {
            return current;
        }
        synchronized (Http.class) {
            current = CLIENT.get();
            if (current != null) {
                return current;
            }
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(connectTimeout)
                    .followRedirects(HttpClient.Redirect.NORMAL);
            String spec = proxySpec;
            if (spec != null && !spec.isBlank()) {
                String cleaned = spec.replace("http://", "").replace("https://", "");
                int colon = cleaned.lastIndexOf(':');
                if (colon > 0 && colon < cleaned.length() - 1) {
                    try {
                        builder.proxy(ProxySelector.of(new InetSocketAddress(
                                cleaned.substring(0, colon),
                                Integer.parseInt(cleaned.substring(colon + 1)))));
                    } catch (NumberFormatException ignored) {
                        Log.warn("Ignoring malformed proxy: " + spec);
                    }
                }
            }
            current = builder.build();
            CLIENT.set(current);
            return current;
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity");
    }

    public static String getString(String url) throws IOException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    public static JsonObject getJson(String url) throws IOException {
        return Json.parseObject(getString(url));
    }

    /** Parses a response whose root may be an object or an array. */
    public static Object getJsonValue(String url) throws IOException {
        return Json.parse(getString(url));
    }

    public static byte[] getBytes(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<byte[]> response = client().send(
                        request(url).timeout(Duration.ofMinutes(2)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }
                last = new IOException("HTTP " + status + " for " + url);
                if (status == 404 || status == 403) {
                    throw last;
                }
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted fetching " + url, e);
            }
            backoff(attempt);
        }
        throw last == null ? new IOException("Failed to fetch " + url) : last;
    }

    /**
     * Downloads {@code url} into {@code target}, resuming a partial {@code .part}
     * file when the server honours range requests.
     */
    public static void download(String url, Path target, String expectedSha1,
                                Progress.Listener progress) throws IOException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Fs.mkdirs(target.getParent());
        long existing = Files.isRegularFile(part) ? Files.size(part) : 0L;

        HttpRequest.Builder builder = request(url).timeout(Duration.ofMinutes(30));
        if (existing > 0) {
            builder.header("Range", "bytes=" + existing + "-");
        }
        HttpResponse<InputStream> response;
        try {
            response = client().send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted downloading " + url, e);
        }

        int status = response.statusCode();
        if (status == 416) {
            Hashing.moveReplacing(part, target);
            verify(target, expectedSha1);
            return;
        }
        if (status == 200 && existing > 0) {
            existing = 0L;
            Files.deleteIfExists(part);
        } else if (status != 200 && status != 206) {
            closeQuietly(response.body());
            throw new IOException("HTTP " + status + " for " + url);
        }

        long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
        long total = contentLength > 0 ? contentLength + existing : -1L;
        long written = existing;
        try (InputStream in = response.body();
             var out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            byte[] buffer = new byte[131072];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                written += read;
                if (progress != null) {
                    progress.onBytes(written, total);
                }
            }
        }

        if (total > 0 && written < total) {
            throw new IOException("Truncated download for " + url + " (" + written + "/" + total + ")");
        }
        Hashing.moveReplacing(part, target);
        verify(target, expectedSha1);
    }

    private static void verify(Path file, String expectedSha1) throws IOException {
        if (expectedSha1 != null && !expectedSha1.isBlank()
                && !Hashing.matchesSha1(file, expectedSha1)) {
            Files.deleteIfExists(file);
            throw new IOException("Checksum mismatch for " + file.getFileName());
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }

    /** True when a cheap HEAD request succeeds; results are cached per host. */
    public static boolean reachable(String url, int timeoutMillis) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (RuntimeException e) {
            return false;
        }
        Boolean cached = HOST_HEALTH.get(host);
        if (cached != null) {
            return cached;
        }
        boolean healthy;
        try {
            HttpRequest probe = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(500, timeoutMillis)))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", userAgent)
                    .build();
            HttpResponse<Void> response = client().send(probe, HttpResponse.BodyHandlers.discarding());
            healthy = response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception e) {
            healthy = false;
        }
        HOST_HEALTH.put(host, healthy);
        return healthy;
    }

    static void clearHealth() {
        HOST_HEALTH.clear();
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(3000L, 250L * attempt * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
