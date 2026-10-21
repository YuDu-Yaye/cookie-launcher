package dev.cookielauncher.core.net;

import dev.cookielauncher.core.AppInfo;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Hashing;
import dev.cookielauncher.core.util.Progress;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Range-parallel downloader for large files.
 *
 * <p>A single TCP connection rarely saturates a fast link: throughput is capped by
 * window size and loss. Splitting one file into N ranges over N connections
 * typically recovers 2-4x on large artifacts such as client jars. Each range is
 * written at its own offset in one pre-allocated part file, so completion is a
 * single atomic rename.</p>
 *
 * <p>Falls back to {@link Http#download} whenever the server omits
 * {@code Accept-Ranges}, reports no length, or the file is small enough that the
 * extra requests cost more than they save.</p>
 */
public final class SegmentedDownload {

    /** Below this size a single stream beats N range requests. */
    public static final long MIN_SEGMENTED_BYTES = 4L * 1024L * 1024L;

    /** Upper bound on parallel ranges; more than this saturates the link anyway. */
    public static final int MAX_SEGMENTS = 8;

    private SegmentedDownload() {
    }

    /**
     * Downloads {@code url} to {@code target}, choosing a strategy from the
     * server's own advertisement.
     *
     * @param parallelism maximum number of parallel ranges
     * @return true when the segmented path was used
     */
    public static boolean download(String url, Path target, String expectedSha1, long declaredSize,
                                   int parallelism, Progress.Listener listener) throws IOException {
        long size = declaredSize > 0 ? declaredSize : probeLength(url);
        if (size < MIN_SEGMENTED_BYTES || parallelism <= 1 || !supportsRanges(url)) {
            Http.download(url, target, expectedSha1, listener);
            return false;
        }
        int segments = (int) Math.max(2, Math.min(MAX_SEGMENTS,
                Math.min(parallelism, size / (2L * 1024L * 1024L))));
        try {
            segmented(url, target, size, segments, listener);
            if (expectedSha1 != null && !Hashing.matchesSha1(target, expectedSha1)) {
                Files.deleteIfExists(target);
                throw new IOException("Checksum mismatch for " + target.getFileName());
            }
            return true;
        } catch (IOException e) {
            Log.warn("Segmented download failed (" + e.getMessage() + "); retrying single stream");
            Files.deleteIfExists(partOf(target));
            Http.download(url, target, expectedSha1, listener);
            return false;
        }
    }

    private static void segmented(String url, Path target, long size, int segments,
                                  Progress.Listener listener) throws IOException {
        Path part = partOf(target);
        Fs.mkdirs(target.getParent());
        Files.deleteIfExists(part);

        try (FileChannel channel = FileChannel.open(part,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.truncate(0);
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }

        long chunk = size / segments;
        List<long[]> ranges = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            long start = i * chunk;
            long end = (i == segments - 1) ? size - 1 : (start + chunk - 1);
            ranges.add(new long[]{start, end});
        }

        AtomicLong received = new AtomicLong(0L);
        ExecutorService pool = Executors.newFixedThreadPool(ranges.size(), runnable -> {
            Thread thread = new Thread(runnable, "cookie-range");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>(ranges.size());
            for (long[] range : ranges) {
                final long start = range[0];
                final long end = range[1];
                futures.add(pool.submit(() -> {
                    fetchRange(url, part, start, end, received, size, listener);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Segmented download interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    throw cause instanceof IOException io ? io
                            : new IOException("Range download failed", cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        Hashing.moveReplacing(part, target);
    }

    private static void fetchRange(String url, Path part, long start, long end,
                                   AtomicLong received, long total, Progress.Listener listener)
            throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(30))
                .header("Range", "bytes=" + start + "-" + end)
                .header("User-Agent", AppInfo.USER_AGENT)
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = Http.client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Range request interrupted", e);
        }
        int status = response.statusCode();
        if (status != 206 && status != 200) {
            try (InputStream ignored = response.body()) {
                // Drain so the connection returns to the pool.
            }
            throw new IOException("Range request returned HTTP " + status);
        }
        try (InputStream in = response.body();
             FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
            channel.position(start);
            byte[] buffer = new byte[131072];
            long written = 0L;
            long expected = end - start + 1;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                channel.write(ByteBuffer.wrap(buffer, 0, read));
                written += read;
                long overall = received.addAndGet(read);
                if (listener != null) {
                    listener.onBytes(overall, total);
                }
                if (written >= expected) {
                    break;
                }
            }
            if (written < expected) {
                throw new IOException("Range " + start + "-" + end + " ended early ("
                        + written + "/" + expected + ")");
            }
        }
    }

    /** Asks the server for the total size through a one-byte range request. */
    static long probeLength(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", AppInfo.USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = Http.client().send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream ignored = response.body()) {
                String contentRange = response.headers().firstValue("content-range").orElse("");
                int slash = contentRange.lastIndexOf('/');
                if (slash > 0) {
                    return Long.parseLong(contentRange.substring(slash + 1).trim());
                }
                return response.headers().firstValueAsLong("content-length").orElse(-1L);
            }
        } catch (Exception e) {
            return -1L;
        }
    }

    /** True when the server advertises byte-range support. */
    static boolean supportsRanges(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", AppInfo.USER_AGENT)
                    .build();
            HttpResponse<Void> response = Http.client().send(request,
                    HttpResponse.BodyHandlers.discarding());
            return response.headers().firstValue("accept-ranges").orElse("")
                    .toLowerCase().contains("bytes");
        } catch (Exception e) {
            return false;
        }
    }

    private static Path partOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".part");
    }
}
