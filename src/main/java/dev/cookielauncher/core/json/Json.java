package dev.cookielauncher.core.json;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Strict RFC 8259 JSON reader and writer with no external dependencies.
 * Integers parse to {@link Long}, anything with a fraction or exponent to {@link Double}.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.skip();
        if (parser.index < text.length()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    public static JsonObject parseObject(String text) {
        Object value = parse(text);
        if (value instanceof JsonObject object) {
            return object;
        }
        throw new JsonException("expected object at root, got " + name(value));
    }

    public static Object parse(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static JsonObject parseObject(Path file) throws IOException {
        Object value = parse(file);
        if (value instanceof JsonObject object) {
            return object;
        }
        throw new JsonException("expected object in " + file);
    }

    public static Object parse(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            builder.append(buffer, 0, read);
        }
        return parse(builder.toString());
    }

    public static String write(Object value) {
        StringBuilder builder = new StringBuilder(256);
        write(builder, value, -1, 0);
        return builder.toString();
    }

    public static String pretty(Object value) {
        StringBuilder builder = new StringBuilder(512);
        write(builder, value, 2, 0);
        return builder.toString();
    }

    private static void write(StringBuilder out, Object value, int indent, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            quote(out, string);
        } else if (value instanceof Boolean flag) {
            out.append(flag);
        } else if (value instanceof Number number) {
            number(out, number);
        } else if (value instanceof JsonObject object) {
            object(out, object, indent, depth);
        } else if (value instanceof JsonArray array) {
            array(out, array, indent, depth);
        } else if (value instanceof Map<?, ?> map) {
            JsonObject wrapped = new JsonObject();
            map.forEach((key, item) -> wrapped.put(String.valueOf(key), item));
            object(out, wrapped, indent, depth);
        } else if (value instanceof Iterable<?> iterable) {
            array(out, JsonArray.from(iterable), indent, depth);
        } else {
            quote(out, String.valueOf(value));
        }
    }

    private static void object(StringBuilder out, JsonObject object, int indent, int depth) {
        if (object.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : object.asMap().entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, indent, depth + 1);
            quote(out, entry.getKey());
            out.append(indent >= 0 ? ": " : ":");
            write(out, entry.getValue(), indent, depth + 1);
        }
        newline(out, indent, depth);
        out.append('}');
    }

    private static void array(StringBuilder out, JsonArray array, int indent, int depth) {
        if (array.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (Object value : array) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, indent, depth + 1);
            write(out, value, indent, depth + 1);
        }
        newline(out, indent, depth);
        out.append(']');
    }

    private static void newline(StringBuilder out, int indent, int depth) {
        if (indent < 0) {
            return;
        }
        out.append('\n');
        out.append(" ".repeat(indent * depth));
    }

    private static void number(StringBuilder out, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new JsonException("non-finite number");
            }
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                out.append((long) value);
            } else {
                out.append(value);
            }
        } else {
            out.append(number.longValue());
        }
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static String name(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static final class Parser {

        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        JsonException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(index, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new JsonException(message + " at line " + line + " column " + column);
        }

        void skip() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        char peek() {
            if (index >= text.length()) {
                throw error("unexpected end of input");
            }
            return text.charAt(index);
        }

        void expect(char c) {
            if (index >= text.length() || text.charAt(index) != c) {
                throw error("expected '" + c + "'");
            }
            index++;
        }

        Object value() {
            skip();
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        JsonObject object() {
            expect('{');
            JsonObject result = new JsonObject();
            skip();
            if (peek() == '}') {
                index++;
                return result;
            }
            while (true) {
                skip();
                if (peek() != '"') {
                    throw error("expected object key");
                }
                String key = string();
                skip();
                expect(':');
                result.put(key, value());
                skip();
                char next = peek();
                if (next == ',') {
                    index++;
                    continue;
                }
                if (next == '}') {
                    index++;
                    return result;
                }
                throw error("expected ',' or '}'");
            }
        }

        JsonArray array() {
            expect('[');
            JsonArray result = new JsonArray();
            skip();
            if (peek() == ']') {
                index++;
                return result;
            }
            while (true) {
                result.add(value());
                skip();
                char next = peek();
                if (next == ',') {
                    index++;
                    continue;
                }
                if (next == ']') {
                    index++;
                    return result;
                }
                throw error("expected ',' or ']'");
            }
        }

        String string() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (true) {
                if (index >= text.length()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\') {
                    if (index >= text.length()) {
                        throw error("unterminated escape");
                    }
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            if (index + 4 > text.length()) {
                                throw error("truncated unicode escape");
                            }
                            String hex = text.substring(index, index + 4);
                            index += 4;
                            try {
                                builder.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("bad unicode escape " + hex);
                            }
                        }
                        default -> throw error("bad escape '\\" + escape + "'");
                    }
                } else if (c < 0x20) {
                    throw error("control character in string");
                } else {
                    builder.append(c);
                }
            }
        }

        Object number() {
            int start = index;
            if (peek() == '-') {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            boolean fractional = false;
            if (index < text.length() && text.charAt(index) == '.') {
                fractional = true;
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                fractional = true;
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String raw = text.substring(start, index);
            // A ternary mixing Double and long promotes both branches to double,
            // which would silently turn every integer into a Double. Use branches.
            if (!fractional) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    // Out of long range: fall through to double.
                }
            }
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw error("bad number '" + raw + "'");
            }
        }

        Object literal(String literal, Object result) {
            if (!text.startsWith(literal, index)) {
                throw error("bad literal");
            }
            index += literal.length();
            return result;
        }
    }
}
