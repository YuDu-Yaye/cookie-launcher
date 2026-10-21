package dev.cookielauncher.core.net;

import dev.cookielauncher.core.log.Log;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Measures reachability and latency of the two download hosts and decides whether
 * the mirror should be preferred.
 *
 * <p>A launcher cannot read a user's country reliably from the JVM, so this probes
 * instead: three TCP connects to each host, take the median. The winner decides
 * the default mirror policy once per session, and the result is cached so the
 * decision costs under a second and never blocks launching.</p>
 */
public final class NetworkProbe {

    public static final String OFFICIAL_HOST = "piston-meta.mojang.com";
    public static final String MIRROR_HOST = "bmclapi2.bangbang93.com";

    private static final int PROBE_TIMEOUT_MS = 2500;
    private static final int SAMPLES = 3;

    private NetworkProbe() {
    }

    /** Result of a probe: latency per host in milliseconds, -1 when unreachable. */
    public record Result(long officialLatencyMs, long mirrorLatencyMs, Mirror.Policy recommendation) {

        public boolean mirrorFaster() {
            return recommendation == Mirror.Policy.MIRROR;
        }

        public String describe() {
            String official = officialLatencyMs < 0 ? "unreachable" : officialLatencyMs + " ms";
            String mirror = mirrorLatencyMs < 0 ? "unreachable" : mirrorLatencyMs + " ms";
            return "official " + official + ", mirror " + mirror
                    + " -> " + (mirrorFaster() ? "mirror preferred" : "official preferred");
        }
    }

    /**
     * Probes both hosts in parallel. Recommendation rules:
     * official unreachable and mirror reachable -> MIRROR;
     * mirror unreachable -> OFFICIAL;
     * both reachable -> the faster one, but only switch to the mirror when it is
     * at least 35% faster (avoids flapping on measurement noise).
     */
    public static Result probe() {
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "cookie-netprobe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Long> official = pool.submit(latency(OFFICIAL_HOST));
            Future<Long> mirror = pool.submit(latency(MIRROR_HOST));
            long officialMs = get(official);
            long mirrorMs = get(mirror);

            Mirror.Policy policy;
            if (officialMs < 0 && mirrorMs < 0) {
                policy = Mirror.Policy.OFFICIAL;
            } else if (officialMs < 0) {
                policy = Mirror.Policy.MIRROR;
            } else if (mirrorMs < 0) {
                policy = Mirror.Policy.OFFICIAL;
            } else if (mirrorMs * 100L < officialMs * 65L) {
                policy = Mirror.Policy.MIRROR;
            } else {
                policy = Mirror.Policy.OFFICIAL;
            }
            Result result = new Result(officialMs, mirrorMs, policy);
            Log.info("Network probe: " + result.describe());
            return result;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long get(Future<Long> future) {
        try {
            return future.get(PROBE_TIMEOUT_MS * SAMPLES + 2000L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return -1L;
        }
    }

    private static Callable<Long> latency(String host) {
        return () -> {
            long[] samples = new long[SAMPLES];
            int valid = 0;
            for (int i = 0; i < SAMPLES; i++) {
                long elapsed = connect(host);
                if (elapsed >= 0) {
                    samples[valid++] = elapsed;
                }
            }
            if (valid == 0) {
                return -1L;
            }
            java.util.Arrays.sort(samples, 0, valid);
            return samples[valid / 2];
        };
    }

    private static long connect(String host) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 443), PROBE_TIMEOUT_MS);
            return (System.nanoTime() - start) / 1_000_000L;
        } catch (Exception e) {
            return -1L;
        }
    }

    /** Resolves a host's addresses without connecting; useful for diagnostics. */
    public static Map<String, String> resolve(String host) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            for (var address : java.net.InetAddress.getAllByName(host)) {
                result.put(address.getHostAddress(), address.getHostName());
            }
        } catch (Exception e) {
            result.put("unresolved", e.getMessage());
        }
        return result;
    }

    static URI uri(String url) {
        return URI.create(url);
    }
}
