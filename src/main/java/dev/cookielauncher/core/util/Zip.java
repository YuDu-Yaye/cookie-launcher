package dev.cookielauncher.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** ZIP extraction with path-traversal protection. */
public final class Zip {

    private Zip() {
    }

    public static List<String> entries(Path archive) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                names.add(enumeration.nextElement().getName());
            }
        }
        return names;
    }

    public static boolean hasEntry(Path archive, String name) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.getEntry(name) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** Extracts every entry accepted by {@code filter}; returns the file count. */
    public static int extract(Path archive, Path destination, Predicate<String> filter) throws IOException {
        Files.createDirectories(destination);
        int count = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                String name = entry.getName();
                if (!filter.test(name)) {
                    continue;
                }
                Path target = Fs.resolveSafely(destination, name);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
        }
        return count;
    }

    public static int extractAll(Path archive, Path destination) throws IOException {
        return extract(archive, destination, name -> true);
    }

    /** Reads one entry as UTF-8 text, or returns null when it does not exist. */
    public static String readEntry(Path archive, String name) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }

    /** Finds the first entry whose name satisfies the predicate. */
    public static String findEntry(Path archive, Predicate<String> predicate) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                String name = enumeration.nextElement().getName();
                if (predicate.test(name)) {
                    return name;
                }
            }
        }
        return null;
    }
}
