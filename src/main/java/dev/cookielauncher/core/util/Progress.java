package dev.cookielauncher.core.util;

/**
 * Two-channel progress reporting plus speed measurement. Downloaders report
 * bytes; the UI renders a fraction, a byte count, an instant speed and an ETA
 * from the same object, so every long task has a consistent readout.
 */
public final class Progress {

    private long lastBytes;
    private long lastTime = System.currentTimeMillis();
    private long smoothedSpeed;
    private double fraction;
    private long completed;
    private long total;
    private String status = "";

    /** Called from worker threads; the sink decides how to marshal to the UI. */
    public interface Sink {
        void onStatus(String message);

        void onProgress(long completed, long total, double fraction);

        Sink NOOP = new Sink() {
            @Override
            public void onStatus(String message) {
            }

            @Override
            public void onProgress(long completed, long total, double fraction) {
            }
        };
    }

    public Progress(Sink sink) {
        this.sink = sink == null ? Sink.NOOP : sink;
    }

    private final Sink sink;

    public static Progress noop() {
        return new Progress(Sink.NOOP);
    }

    public synchronized void status(String message) {
        this.status = message == null ? "" : message;
        sink.onStatus(this.status);
    }

    public synchronized void advance(long bytes, long totalBytes) {
        this.completed = bytes;
        this.total = totalBytes;
        this.fraction = totalBytes > 0 ? Math.min(1.0, bytes / (double) totalBytes) : 0;
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime;
        if (elapsed >= 200) {
            long delta = bytes - lastBytes;
            if (delta >= 0) {
                long instant = delta * 1000L / Math.max(1, elapsed);
                // Exponential smoothing keeps the readout readable instead of jittery.
                smoothedSpeed = smoothedSpeed == 0 ? instant : (smoothedSpeed * 3 + instant) / 4;
            }
            lastBytes = bytes;
            lastTime = now;
        }
        sink.onProgress(bytes, totalBytes, fraction);
    }

    /** Reports completion of one item out of a known count. */
    public synchronized void step(long done, long count) {
        this.fraction = count > 0 ? Math.min(1.0, done / (double) count) : 0;
        sink.onProgress(done, count, fraction);
    }

    public synchronized double fraction() {
        return fraction;
    }

    public synchronized long completed() {
        return completed;
    }

    public synchronized long total() {
        return total;
    }

    public synchronized long speed() {
        return smoothedSpeed;
    }

    /** Remaining time in milliseconds, or -1 when it cannot be estimated. */
    public synchronized long remainingMillis() {
        if (smoothedSpeed <= 0 || total <= 0) {
            return -1;
        }
        long remaining = Math.max(0, total - completed);
        return remaining * 1000L / smoothedSpeed;
    }

    public synchronized String status() {
        return status;
    }

    /** Convenience for code that wants a plain callback. */
    public interface Listener {
        void onBytes(long completed, long total);
    }
}
