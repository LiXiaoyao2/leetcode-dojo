package dojo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesNestedStructures() {
        Object parsed = Json.parse("""
                {"a": 1, "b": [1.5, true, null, "x\\"y"], "c": {"d": -2}}
                """);
        Map<?, ?> obj = (Map<?, ?>) parsed;
        assertEquals(1L, obj.get("a"));

        List<?> array = (List<?>) obj.get("b");
        assertEquals(1.5, (Double) array.get(0), 1e-9);
        assertEquals(Boolean.TRUE, array.get(1));
        assertNull(array.get(2));
        assertEquals("x\"y", array.get(3));

        assertEquals(-2L, ((Map<?, ?>) obj.get("c")).get("d"));
    }

    @Test
    void parsesIntegersAsLongAndDecimalsAsDouble() {
        assertEquals(42L, Json.parse("42"));
        assertEquals(4.25, (Double) Json.parse("4.25"), 1e-9);
        assertEquals(1000.0, (Double) Json.parse("1e3"), 1e-9);
    }

    @Test
    void readsNestedFields() {
        Object node = Json.parse("{\"return\":{\"type\":\"integer[]\"},\"params\":[{\"name\":\"nums\"}]}");
        assertEquals("integer[]", Json.strAt(node, "return", "type"));
        assertNull(Json.strAt(node, "return", "missing"));
        assertTrue(Json.at(node, "params") instanceof List<?>);
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
    }

    @Test
    void quotesStrings() {
        assertEquals("\"a\\\"b\\nc\"", Json.quote("a\"b\nc"));
        assertEquals("\"\"", Json.quote(""));
    }

    @Test
    void parsesStringArrays() {
        assertEquals(List.of("[1,2]\\n3", "[]"), Json.arrOfStrings(Json.parse("[\"[1,2]\\\\n3\",\"[]\"]")));
    }
}
