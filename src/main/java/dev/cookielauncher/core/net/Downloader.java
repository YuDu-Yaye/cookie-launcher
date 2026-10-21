package dev.cookielauncher.core.net;

import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Hashing;
import dev.cookielauncher.core.util.Progress;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel download engine with two speed mechanisms layered together.
 *
 * <p><b>Across files:</b> a bounded worker pool (12 by default) fetches many small
 * files concurrently over one shared HTTP/2 client, so the TLS handshake and TCP
 * warm-up are paid once per host rather than once per file.</p>
 *
 * <p><b>Within a file:</b> artifacts above {@link SegmentedDownload#MIN_SEGMENTED_BYTES}
 * that advertise {@code Accept-Ranges} are split into up to eight parallel range
 * requests, which is where the 2-4x on client jars comes from.</p>
 *
 * <p>Integrity is structural: files that already match their SHA-1 are excluded
 * from the plan, and a file failing verification is deleted before the next
 * mirror is tried.</p>
 */
public final class Downloader implements AutoCloseable {

    public static final int DEFAULT_WORKERS = 12;

    private final ExecutorService pool;
    private final int workers;
    private final boolean segmentationEnabled;
    private final List<String> failures = new CopyOnWriteArrayList<>();

    public Downloader() {
        this(DEFAULT_WORKERS, true);
    }

    public Downloader(int workers) {
        this(workers, true);
    }

    public Downloader(int workers, boolean segmentationEnabled) {
        this.workers = Math.max(1, Math.min(32, workers));
        this.segmentationEnabled = segmentationEnabled;
        this.pool = Executors.newFixedThreadPool(this.workers, runnable -> {
            Thread thread = new Thread(runnable, "cookie-download");
            thread.setDaemon(true);
            return thread;
        });
    }

    public int workers() {
        return workers;
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }

    /**
     * Executes the plan, skipping files that already verify.
     *
     * @return number of files that could not be fetched from any source
     */
    public int run(Collection<RemoteFile> plan, Progress progress) {
        Progress sink = progress == null ? Progress.noop() : progress;
        failures.clear();

        List<RemoteFile> pending = new ArrayList<>();
        long totalBytes = 0L;
        long segmentedBytes = 0L;
        for (RemoteFile file : plan) {
            if (!file.usable() || alreadyValid(file)) {
                continue;
            }
            pending.add(file);
            totalBytes += Math.max(0L, file.size);
            if (segmentationEnabled && file.size >= SegmentedDownload.MIN_SEGMENTED_BYTES) {
                segmentedBytes += file.size;
            }
        }

        if (pending.isEmpty()) {
            sink.status("All " + plan.size() + " files already verified");
            sink.step(1, 1);
            return 0;
        }

        String detail = segmentedBytes > 0
                ? " (" + Fmt.bytes(totalBytes) + ", " + Fmt.bytes(segmentedBytes) + " segmented)"
                : " (" + Fmt.bytes(totalBytes) + ")";
        sink.status("Downloading " + pending.size() + " of " + plan.size() + " files" + detail);

        AtomicLong downloaded = new AtomicLong(0L);
        AtomicInteger finished = new AtomicInteger();
        int count = pending.size();
        long knownTotal = totalBytes;

        List<Future<?>> futures = new ArrayList<>(count);
        for (RemoteFile file : pending) {
            futures.add(pool.submit(() -> fetch(file, downloaded, knownTotal, finished, count, sink)));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                Log.error("Download worker crashed", e.getCause());
            }
        }

        if (failures.isEmpty()) {
            sink.status("Downloaded " + count + " files");
        } else {
            sink.status(failures.size() + " file(s) failed");
        }
        sink.advance(Math.max(knownTotal, downloaded.get()), Math.max(1L, knownTotal));
        return failures.size();
    }

    private void fetch(RemoteFile file, AtomicLong downloaded, long total,
                       AtomicInteger finished, int count, Progress sink) {
        for (String url : file.urls()) {
            long baseline = downloaded.get();
            final long[] previous = {0L};
            Progress.Listener listener = (bytes, ignored) -> {
                long delta = bytes - previous[0];
                previous[0] = bytes;
                long overall = downloaded.addAndGet(Math.max(0L, delta));
                sink.advance(overall, total);
            };
            try {
                boolean parallel = false;
                if (segmentationEnabled && file.size >= SegmentedDownload.MIN_SEGMENTED_BYTES) {
                    parallel = SegmentedDownload.download(url, file.target, file.sha1, file.size,
                            SegmentedDownload.MAX_SEGMENTS, listener);
                }
                if (!parallel) {
                    Http.download(url, file.target, file.sha1, listener);
                }
                int done = finished.incrementAndGet();
                sink.status("[" + done + "/" + count + "] " + file.fileName()
                        + (parallel ? " (parallel)" : ""));
                return;
            } catch (IOException e) {
                downloaded.set(baseline);
                Log.warn("Mirror failed for " + file.fileName() + " (" + url + "): " + e.getMessage());
            }
        }
        failures.add(file.fileName());
        Log.error("Could not download " + file.fileName() + " from any source");
    }

    /** True when the file exists and satisfies its hash (or its declared size). */
    public static boolean alreadyValid(RemoteFile file) {
        if (!Files.isRegularFile(file.target)) {
            return false;
        }
        if (file.sha1 != null) {
            return Hashing.matchesSha1(file.target, file.sha1);
        }
        if (file.size > 0) {
            try {
                return Files.size(file.target) == file.size;
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
