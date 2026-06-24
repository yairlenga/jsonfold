package dev.jsonfold.format;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LineTest {

    @Test
    void parsesScalarObjectLine() {
        Line line = Line.parse("    \"x\": 1,");

        assertEquals(4, line.indent);
        assertEquals(1, line.parts.size());
        assertEquals("\"x\": 1,", line.parts.get(0));
        assertEquals(Kind.NONE, line.opener);
        assertEquals(Kind.NONE, line.closer);
        assertTrue(line.canPack);
        assertTrue(line.canJoin);
    }

    @Test
    void parsesOpenDict() {
        Line line = Line.parse("  {");

        assertEquals(2, line.indent);
        assertEquals(1, line.parts.size());
        assertEquals("{", line.parts.get(0));
        assertEquals(Kind.DICT, line.opener);
        assertEquals(Kind.NONE, line.closer);
        assertFalse(line.canPack);
        assertFalse(line.canJoin);
    }

    @Test
    void parsesOpenList() {
        Line line = Line.parse("  \"items\": [");

        assertEquals(2, line.indent);
        assertEquals(1, line.parts.size());
        assertEquals("\"items\": [", line.parts.get(0));
        assertEquals(Kind.LIST, line.opener);
        assertEquals(Kind.NONE, line.closer);
        assertFalse(line.canPack);
        assertFalse(line.canJoin);
    }

    @Test
    void parsesClosers() {
        assertEquals(Kind.DICT, Line.parse("}").closer);
        assertEquals(Kind.DICT, Line.parse("},").closer);
        assertEquals(Kind.LIST, Line.parse("]").closer);
        assertEquals(Kind.LIST, Line.parse("],").closer);
    }

    @Test
    void scalarOutsideContainerIsNotPackableOrJoinable() {
        Line line = Line.parse("123");

        assertFalse(line.canPack);
        assertFalse(line.canJoin);
    }

    @Test
    void rawRestoresIndentAndNewline() {
        Line line = Line.parse("    123,");

        assertEquals("    123,\n", line.raw());
        assertEquals(8, line.width());
    }

    @Test
    void packMergesScalarLines() {
        Line a = Line.parse("    1,");
        Line b = Line.parse("    2,");

        a.mergeLine(b);
        assertEquals(1, a.parts.size());
        assertEquals("1, 2,", a.parts.get(0)) ;
        assertEquals(2, a.items);
        assertEquals(2, a.leafs);
        assertEquals(-1, a.childNesting);
        assertTrue(a.canPack);
        assertTrue(a.canJoin);
    }

    @Test
    void joinMergesLines() {
        Line a = Line.parse("    { \"x\": 1 },");
        Line b = Line.parse("    { \"y\": 2 },");

        a.mergeLine(b);
        assertEquals("1, 2,", String.join(" ", a.parts)) ;
        assertEquals(1, a.parts.size());
        assertEquals("{ \"x\": 1 }, { \"y\": 2 },", a.parts.get(0));
        assertEquals(2, a.items);
        assertEquals(2, a.leafs);
        assertEquals(-1, a.childNesting);
        assertTrue(a.canJoin);
    }

    @Test
    void packPropagatesChildNestingAndDisablesPack() {
        Line a = Line.parse("    1,");
        Line b = Line.parse("    2,");
        b.childNesting = 2;

        a.mergeLine(b);

        assertEquals(1, a.parts.size());
        assertEquals("1, 2,", a.parts.get(0));
        assertEquals(2, a.childNesting);
        assertFalse(a.canPack);
    }

}