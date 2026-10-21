package dev.cookielauncher.test;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;

/** JSON parser and writer behaviour, including the failure modes that matter. */
public final class JsonTest {

    private JsonTest() {
    }

    public static void testParsesObjectAndScalars() {
        JsonObject object = Json.parseObject(
                "{\"name\":\"cookie\",\"count\":42,\"ratio\":0.5,\"flag\":true,\"none\":null}");
        Assert.equal("cookie", object.getString("name"), "string value");
        Assert.equal(42, object.getInt("count", -1), "integer value");
        Assert.equal(0.5, object.getDouble("ratio", -1), "double value");
        Assert.isTrue(object.getBoolean("flag", false), "boolean value");
        Assert.isTrue(object.has("none"), "null member still counts as present");
    }

    public static void testNestedStructuresPreserveOrder() {
        JsonObject object = Json.parseObject(
                "{\"b\":1,\"a\":2,\"list\":[{\"x\":1},{\"x\":2}]}");
        var keys = new java.util.ArrayList<>(object.keys());
        Assert.equal("b", keys.get(0), "first key stays first");
        Assert.equal("a", keys.get(1), "second key stays second");
        JsonArray list = object.getArray("list");
        Assert.notNull(list, "nested array parsed");
        Assert.equal(2, list.size(), "array size");
        Assert.equal(2, list.getObject(1).getInt("x", -1), "nested object value");
    }

    public static void testEscapesRoundTrip() {
        String original = "quote \" backslash \\ newline \n tab \t unicode \u00e9";
        JsonObject object = JsonObject.of("text", original);
        String encoded = Json.write(object);
        JsonObject decoded = Json.parseObject(encoded);
        Assert.equal(original, decoded.getString("text"), "escaped text survives a round trip");
        Assert.notContains(encoded, "\n", "literal newline is escaped in output");
    }

    public static void testNumbersKeepIntegerAndDecimalTypes() {
        JsonObject object = Json.parseObject("{\"i\":7,\"d\":7.25,\"e\":1500000000000}");
        Assert.isTrue(object.get("i") instanceof Long, "integer parses as Long");
        Assert.isTrue(object.get("d") instanceof Double, "decimal parses as Double");
        Assert.isTrue(object.get("e") instanceof Long, "large integer stays Long");
    }

    public static void testRejectsMalformedInput() {
        Assert.fails(RuntimeException.class, () -> Json.parse("{"), "unterminated object");
        Assert.fails(RuntimeException.class, () -> Json.parse("{,}"), "leading comma");
        Assert.fails(RuntimeException.class, () -> Json.parse("{\"a\":1,}"), "trailing comma");
        Assert.fails(RuntimeException.class, () -> Json.parse("[1,2"), "unterminated array");
        Assert.fails(RuntimeException.class, () -> Json.parse("{'a':1}"), "single quotes");
        Assert.fails(RuntimeException.class, () -> Json.parse("{\"a\":1} extra"), "trailing content");
    }

    public static void testEmptyStructures() {
        Assert.equal("{}", Json.write(new JsonObject()), "empty object writes as {}");
        Assert.equal("[]", Json.write(new JsonArray()), "empty array writes as []");
        Assert.equal(0, Json.parseObject("{}").size(), "empty object parses");
        Assert.equal(0, Json.parseObject("{\"a\":[]}").getArray("a").size(), "empty nested array");
    }

    public static void testPrettyOutputIsReparseable() {
        JsonObject object = JsonObject.of("a", 1,
                "nested", JsonObject.of("b", JsonArray.of("x", "y")));
        String pretty = Json.pretty(object);
        Assert.contains(pretty, "\n", "pretty output is indented");
        JsonObject reparsed = Json.parseObject(pretty);
        Assert.equal(1, reparsed.getInt("a", -1), "pretty output re-parses");
        Assert.equal(2, reparsed.getObject("nested").getArray("b").size(), "nested array survives");
    }

    public static void testUnicodeEscapesDecode() {
        JsonObject object = Json.parseObject("{\"emoji\":\"\\u25a0\",\"accent\":\"\\u00e9\"}");
        Assert.equal("\u25a0", object.getString("emoji"), "BMP escape decodes");
        Assert.equal("\u00e9", object.getString("accent"), "accented escape decodes");
    }

    public static void testCopyIsDeep() {
        JsonObject source = JsonObject.of("nested", JsonObject.of("value", 1));
        JsonObject copy = source.copy();
        copy.getObject("nested").put("value", 2);
        Assert.equal(1, source.getObject("nested").getInt("value", -1),
                "mutating a copy does not touch the source");
    }

    public static void testDefaultsForMissingKeys() {
        JsonObject object = Json.parseObject("{}");
        Assert.equal("fallback", object.getString("missing", "fallback"), "string default");
        Assert.equal(9, object.getInt("missing", 9), "int default");
        Assert.isFalse(object.getBoolean("missing", false), "boolean default");
        Assert.equal(null, object.getObject("missing"), "missing object is null");
    }
}
