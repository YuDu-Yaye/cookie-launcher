package dev.cookielauncher.core.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-wide logger with a bounded in-memory history, an append-only file and
 * listener fan-out. The log page renders the history; the game log uses the same
 * listeners so the UI has one stream.
 */
public final class Log {

    private static final int CAPACITY = 5000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Deque<String> HISTORY = new ArrayDeque<>(CAPACITY);
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile Path file;
    private static volatile boolean debug = Boolean.getBoolean("cookielauncher.debug");

    private Log() {
    }

    public static void setFile(Path path) {
        file = path;
        if (path != null) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException ignored) {
                file = null;
            }
        }
    }

    public static Path file() {
        return file;
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void addListener(Consumer<String> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void error(String message) {
        write("ERROR", message, null);
    }

    public static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    public static void debug(String message) {
        if (debug) {
            write("DEBUG", message, null);
        }
    }

    /** Appends a raw line (already formatted), used for game output. */
    public static void line(String level, String raw) {
        write(level, raw, null);
    }

    private static void write(String level, String message, Throwable error) {
        StringBuilder builder = new StringBuilder(160);
        builder.append('[').append(LocalTime.now().format(TIME)).append("] [")
                .append(level).append("] ").append(message == null ? "" : message);
        if (error != null) {
            StringWriter writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            builder.append(System.lineSeparator()).append(writer);
        }
        String line = builder.toString();

        synchronized (HISTORY) {
            HISTORY.addLast(line);
            while (HISTORY.size() > CAPACITY) {
                HISTORY.removeFirst();
            }
        }
        for (Consumer<String> listener : LISTENERS) {
            try {
                listener.accept(line);
            } catch (RuntimeException ignored) {
                // A broken consumer must never break logging.
            }
        }
        Path target = file;
        if (target != null) {
            try {
                Files.writeString(target, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Keep going: the in-memory history still works.
            }
        }
    }

    public static List<String> history() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    public static String recent(int count) {
        List<String> all = history();
        int from = Math.max(0, all.size() - Math.max(0, count));
        return String.join(System.lineSeparator(), all.subList(from, all.size()));
    }
}
