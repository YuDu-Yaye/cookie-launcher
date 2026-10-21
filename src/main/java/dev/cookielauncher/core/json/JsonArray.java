package dev.cookielauncher.core.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Insertion-ordered JSON array. */
public final class JsonArray implements Iterable<Object> {

    private final ArrayList<Object> values = new ArrayList<>();

    public static JsonArray of(Object... items) {
        JsonArray array = new JsonArray();
        for (Object item : items) {
            array.add(item);
        }
        return array;
    }

    public static JsonArray from(Iterable<?> items) {
        JsonArray array = new JsonArray();
        items.forEach(array::add);
        return array;
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Object get(int index) {
        return values.get(index);
    }

    public String getString(int index) {
        Object value = values.get(index);
        return value == null ? null : String.valueOf(value);
    }

    public JsonObject getObject(int index) {
        Object value = values.get(index);
        return value instanceof JsonObject object ? object : null;
    }

    public JsonArray getArray(int index) {
        Object value = values.get(index);
        return value instanceof JsonArray array ? array : null;
    }

    public JsonArray add(Object value) {
        values.add(value);
        return this;
    }

    public List<Object> asList() {
        return values;
    }

    public List<JsonObject> objects() {
        List<JsonObject> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof JsonObject object) {
                result.add(object);
            }
        }
        return result;
    }

    public List<String> strings() {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    public JsonArray copy() {
        JsonArray copy = new JsonArray();
        for (Object value : values) {
            if (value instanceof JsonObject object) {
                copy.add(object.copy());
            } else if (value instanceof JsonArray array) {
                copy.add(array.copy());
            } else {
                copy.add(value);
            }
        }
        return copy;
    }

    @Override
    public Iterator<Object> iterator() {
        return values.iterator();
    }

    @Override
    public String toString() {
        return Json.write(this);
    }
}
