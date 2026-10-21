package dev.cookielauncher.core.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Insertion-ordered JSON object. */
public final class JsonObject {

    private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

    public static JsonObject of(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key/value pairs");
        }
        JsonObject object = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            object.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return object;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String getString(String key, String fallback) {
        String value = getString(key);
        return value == null ? fallback : value;
    }

    public int getInt(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public long getLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    public double getDouble(String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    public JsonObject getObject(String key) {
        Object value = values.get(key);
        return value instanceof JsonObject object ? object : null;
    }

    public JsonArray getArray(String key) {
        Object value = values.get(key);
        return value instanceof JsonArray array ? array : null;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public JsonObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public JsonObject remove(String key) {
        values.remove(key);
        return this;
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public JsonObject copy() {
        JsonObject copy = new JsonObject();
        values.forEach((key, value) -> {
            if (value instanceof JsonObject object) {
                copy.put(key, object.copy());
            } else if (value instanceof JsonArray array) {
                copy.put(key, array.copy());
            } else {
                copy.put(key, value);
            }
        });
        return copy;
    }

    @Override
    public String toString() {
        return Json.write(this);
    }
}
