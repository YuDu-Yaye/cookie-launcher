package dev.cookielauncher.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Hashing and integrity helpers. */
public final class Hashing {

    private Hashing() {
    }

    public static String sha1Hex(byte[] data) {
        return digest("SHA-1", data);
    }

    public static String sha1Hex(String text) {
        return digest("SHA-1", text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) {
        return digest("SHA-256", data);
    }

    private static String digest(String algorithm, byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing digest " + algorithm, e);
        }
    }

    public static String fileSha1(Path file) throws IOException {
        return fileDigest("SHA-1", file);
    }

    public static String fileSha256(Path file) throws IOException {
        return fileDigest("SHA-256", file);
    }

    private static String fileDigest(String algorithm, Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing digest " + algorithm, e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static boolean matchesSha1(Path file, String expected) {
        if (expected == null || expected.isBlank() || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            return fileSha1(file).equalsIgnoreCase(expected.trim());
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isHex(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** Moves {@code from} to {@code to}, replacing the target atomically when possible. */
    public static void moveReplacing(Path from, Path to) throws IOException {
        if (to.getParent() != null) {
            Files.createDirectories(to.getParent());
        }
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
