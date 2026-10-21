package dev.cookielauncher.core.json;

/** Thrown when JSON text cannot be parsed or a value cannot be serialized. */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
