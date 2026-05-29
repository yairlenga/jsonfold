package dev.jsonfold;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LineTest {

    @Test
    void parsesScalarObjectLine() {
        Line line = Line.parse("    \"x\": 1,", Kind.DICT);

        assertEquals(4, line.indent);
        assertEquals("\"x\": 1,", line.text);
        assertEquals(Kind.DICT, line.parentKind);
        assertEquals(Kind.NONE, line.opener);
        assertEquals(Kind.NONE, line.closer);
        assertTrue(line.canPack);
        assertTrue(line.canJoin);
    }

    @Test
    void parsesOpenDict() {
        Line line = Line.parse("  {", Kind.LIST);

        assertEquals(2, line.indent);
        assertEquals("{", line.text);
        assertEquals(Kind.DICT, line.opener);
        assertEquals(Kind.NONE, line.closer);
        assertFalse(line.canPack);
        assertFalse(line.canJoin);
    }

    @Test
    void parsesOpenList() {
        Line line = Line.parse("  \"items\": [", Kind.DICT);

        assertEquals(2, line.indent);
        assertEquals("\"items\": [", line.text);
        assertEquals(Kind.LIST, line.opener);
    }

    @Test
    void parsesClosers() {
        assertEquals(Kind.DICT, Line.parse("}", Kind.DICT).closer);
        assertEquals(Kind.DICT, Line.parse("},", Kind.DICT).closer);
        assertEquals(Kind.LIST, Line.parse("]", Kind.LIST).closer);
        assertEquals(Kind.LIST, Line.parse("],", Kind.LIST).closer);
    }

    @Test
    void rawRestoresIndentAndNewline() {
        Line line = Line.parse("    123,", Kind.LIST);

        assertEquals("    123,\n", line.raw());
        assertEquals(8, line.width());
    }

    @Test
    void joinLineMergesMetadata() {
        Line a = Line.parse("    1,", Kind.LIST);
        Line b = Line.parse("    2,", Kind.LIST);

        a.joinLine(b);

        assertEquals("1, 2,", a.text);
        assertEquals(2, a.items);
        assertEquals(2, a.leafs);
        assertEquals(-1, a.childNesting);
        assertTrue(a.canPack);
    }
}