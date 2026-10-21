package dev.cookielauncher.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Filesystem helpers with predictable failure behaviour. */
public final class Fs {

    private Fs() {
    }

    public static Path mkdirs(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory is required");
        }
        if (Files.isDirectory(directory)) {
            return directory;
        }
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            // A regular file occupying the intended directory path means an earlier
            // layout wrote a file where a directory now belongs. Surface it clearly
            // instead of throwing a bare FileAlreadyExistsException.
            if (Files.isRegularFile(directory)) {
                throw new UncheckedIoException(
                        "Cannot create directory " + directory
                                + ": a file already exists at that path", e);
            }
            throw new UncheckedIoException("Cannot create " + directory, e);
        }
    }

    public static boolean exists(Path path) {
        return path != null && Files.exists(path);
    }

    public static boolean isDirectory(Path path) {
        return path != null && Files.isDirectory(path);
    }

    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static String readOrEmpty(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public static void write(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** Writes through a temp file then moves, so a crash never leaves half a file. */
    public static void writeAtomic(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        Hashing.moveReplacing(temp, file);
    }

    public static void copy(Path source, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static void copyStream(InputStream in, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (var out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
    }

    public static List<Path> list(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            List<Path> result = new ArrayList<>();
            stream.forEach(result::add);
            result.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()));
            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    public static List<Path> walk(Path directory, int maxDepth) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory, maxDepth)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static boolean deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return true;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> entries = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Resolves {@code relative} under {@code base}, refusing to escape it. */
    public static Path resolveSafely(Path base, String relative) {
        Path root = base.toAbsolutePath().normalize();
        Path resolved = root.resolve(relative.replace('\\', '/')).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes " + root + ": " + relative);
        }
        return resolved;
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    /** Strips one extension, e.g. {@code mod.jar.disabled} becomes {@code mod.jar}. */
    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /** Best-effort directory size in bytes. */
    public static long directorySize(Path directory) {
        long total = 0L;
        for (Path file : walk(directory, 12)) {
            total += Math.max(0L, size(file));
        }
        return total;
    }

    /** Unchecked wrapper so UI code does not have to declare IOExceptions. */
    public static final class UncheckedIoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UncheckedIoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
