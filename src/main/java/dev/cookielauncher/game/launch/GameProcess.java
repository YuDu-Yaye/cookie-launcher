package dev.cookielauncher.game.launch;

import dev.cookielauncher.core.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A running Minecraft process: log pumping, exit callbacks and graceful stop. */
public final class GameProcess {

    private final Process process;
    private final long startedAt = System.currentTimeMillis();
    private final List<Consumer<Integer>> exitListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile int exitCode = Integer.MIN_VALUE;

    private GameProcess(Process process) {
        this.process = process;
    }

    public static GameProcess start(LaunchCommand command, Consumer<String> logSink) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command.parts());
        builder.directory(command.workingDirectory().toFile());
        builder.redirectErrorStream(true);
        for (Map.Entry<String, String> entry : command.environment().entrySet()) {
            builder.environment().put(entry.getKey(), entry.getValue());
        }
        Log.info("Launching: " + command.display());
        Process process = builder.start();
        GameProcess game = new GameProcess(process);
        game.pumpLogs(logSink);
        game.watchExit();
        return game;
    }

    private void pumpLogs(Consumer<String> logSink) {
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        logSink.accept(line);
                    } catch (RuntimeException ignored) {
                        // A broken consumer must never stall the game's stdout.
                    }
                }
            } catch (IOException e) {
                Log.debug("Log pump ended: " + e.getMessage());
            }
        }, "cookie-game-output");
        pump.setDaemon(true);
        pump.start();
    }

    private void watchExit() {
        Thread watcher = new Thread(() -> {
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exitCode = -1;
            }
            Log.info("Minecraft exited with code " + exitCode);
            for (Consumer<Integer> listener : exitListeners) {
                try {
                    listener.accept(exitCode);
                } catch (RuntimeException ignored) {
                    // Exit callbacks are best effort.
                }
            }
        }, "cookie-game-exit");
        watcher.setDaemon(true);
        watcher.start();
    }

    public boolean isRunning() {
        return process.isAlive();
    }

    public long pid() {
        return process.pid();
    }

    public int exitCode() {
        return exitCode;
    }

    public long startedAt() {
        return startedAt;
    }

    public long uptimeMillis() {
        return System.currentTimeMillis() - startedAt;
    }

    public void onExit(Consumer<Integer> listener) {
        if (listener == null) {
            return;
        }
        exitListeners.add(listener);
        if (exitCode != Integer.MIN_VALUE) {
            listener.accept(exitCode);
        }
    }

    /** Terminates politely, then forcibly after a five second grace period. */
    public void stop() {
        if (!process.isAlive() || !stopping.compareAndSet(false, true)) {
            return;
        }
        Log.info("Stopping Minecraft (pid " + process.pid() + ")");
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
