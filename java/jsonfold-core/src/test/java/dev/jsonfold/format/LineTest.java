package dev.jsonfold.format;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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
        assertEquals(Kind.NONE, line.closer);
        assertFalse(line.canPack);
        assertFalse(line.canJoin);
    }

    @Test
    void parsesClosers() {
        assertEquals(Kind.DICT, Line.parse("}", Kind.DICT).closer);
        assertEquals(Kind.DICT, Line.parse("},", Kind.DICT).closer);
        assertEquals(Kind.LIST, Line.parse("]", Kind.LIST).closer);
        assertEquals(Kind.LIST, Line.parse("],", Kind.LIST).closer);
    }

    @Test
    void scalarOutsideContainerIsNotPackableOrJoinable() {
        Line line = Line.parse("123", Kind.NONE);

        assertEquals(Kind.NONE, line.parentKind);
        assertFalse(line.canPack);
        assertFalse(line.canJoin);
    }

    @Test
    void rawRestoresIndentAndNewline() {
        Line line = Line.parse("    123,", Kind.LIST);

        assertEquals("    123,\n", line.raw());
        assertEquals(8, line.width());
    }

    @Test
    void packMergesScalarLines() {
        Line a = Line.parse("    1,", Kind.LIST);
        Line b = Line.parse("    2,", Kind.LIST);

        a.pack(b);

        assertEquals("1, 2,", a.text);
        assertEquals(2, a.items);
        assertEquals(2, a.leafs);
        assertEquals(-1, a.childNesting);
        assertTrue(a.canPack);
        assertTrue(a.canJoin);
    }

    @Test
    void joinMergesLines() {
        Line a = Line.parse("    { \"x\": 1 },", Kind.LIST);
        Line b = Line.parse("    { \"y\": 2 },", Kind.LIST);

        a.join(b);

        assertEquals("{ \"x\": 1 }, { \"y\": 2 },", a.text);
        assertEquals(2, a.items);
        assertEquals(2, a.leafs);
        assertEquals(-1, a.childNesting);
        assertTrue(a.canJoin);
    }

    @Test
    void packPropagatesChildNestingAndDisablesPack() {
        Line a = Line.parse("    1,", Kind.LIST);
        Line b = Line.parse("    2,", Kind.LIST);
        b.childNesting = 2;

        a.pack(b);

        assertEquals("1, 2,", a.text);
        assertEquals(2, a.childNesting);
        assertFalse(a.canPack);
    }

    @Test
    void foldSimpleObject() {
        List<Line> lines = List.of(
                Line.parse("  {", Kind.LIST),
                Line.parse("    \"x\": 1", Kind.DICT),
                Line.parse("  }", Kind.LIST));

        Line folded = Line.fold(lines, Kind.LIST, 1, 0);

        assertEquals(2, folded.indent);
        assertEquals("{ \"x\": 1 }", folded.text);
        assertEquals(Kind.LIST, folded.parentKind);
        assertEquals(Kind.NONE, folded.opener);
        assertEquals(Kind.NONE, folded.closer);
        assertEquals(1, folded.items);
        assertEquals(1, folded.leafs);
        assertEquals(0, folded.childNesting);
        assertFalse(folded.canPack);
        assertTrue(folded.canJoin);
    }

    @Test
    void foldSimpleArray() {
        List<Line> lines = List.of(
                Line.parse("  [", Kind.DICT),
                Line.parse("    123", Kind.LIST),
                Line.parse("  ]", Kind.DICT));

        Line folded = Line.fold(lines, Kind.DICT, 1, 0);

        assertEquals(2, folded.indent);
        assertEquals("[ 123 ]", folded.text);
        assertEquals(Kind.DICT, folded.parentKind);
        assertFalse(folded.canPack);
        assertTrue(folded.canJoin);
    }

    @Test
    void foldPreservesLeafAndChildMetadata() {
        List<Line> lines = List.of(
                Line.parse("  [", Kind.DICT),
                Line.parse("    { \"x\": 1 }", Kind.LIST),
                Line.parse("  ]", Kind.DICT));

        Line folded = Line.fold(lines, Kind.DICT, 3, 2);

        assertEquals(3, folded.leafs);
        assertEquals(2, folded.childNesting);
    }
}