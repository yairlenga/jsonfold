package dev.jsonfold.format;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class FrameTest {

    @Test
    void frameFoldSimpleObject() {
        Config cfg = new Config();

        Frame frame = new Frame(Kind.DICT, 2, 0, 5, 5, 4, 1000, 3);
        frame.addLine(Line.parse("  {"));
        frame.addLine(Line.parse("    \"x\": 1"));
        frame.addLine(Line.parse("  }"));

        frame.foldLines(cfg);
        Line folded = frame.lines.get(0);

        assertEquals(2, folded.indent);
        assertEquals(List.of("{", "\"x\": 1", "}"), folded.parts);
        assertEquals("{ \"x\": 1 }\n", folded.raw());
        assertEquals(Kind.DICT, folded.kind);
        assertEquals(Kind.NONE, folded.opener);
        assertEquals(Kind.NONE, folded.closer);
        assertEquals(1, folded.items);
        assertEquals(1, folded.leafs);
        assertEquals(0, folded.childNesting);
        assertFalse(folded.canPack);
        assertTrue(folded.canJoin);
    }

    @Test
    void frameFoldSimpleArray() {
        Config cfg = new Config();

        Frame frame = new Frame(Kind.LIST, 2, 0, 5, 5, 4, 1000, 3);
        frame.addLine(Line.parse("  ["));
        frame.addLine(Line.parse("    123"));
        frame.addLine(Line.parse("  ]"));

        frame.foldLines(cfg);
        Line folded = frame.lines.get(0);

        assertEquals(2, folded.indent);
        assertEquals(List.of("[", "123", "]"), folded.parts);
        assertEquals("[ 123 ]\n", folded.raw());
        assertEquals(Kind.LIST, folded.kind);
        assertFalse(folded.canPack);
        assertTrue(folded.canJoin);
    }

    @Test
    void frameFoldPreservesLeafAndChildMetadata() {
        Config cfg = new Config();

        Frame frame = new Frame(Kind.LIST, 2, 0, 5, 5, 4, 1000, 3);
        frame.addLine(Line.parse("  ["));

        Line child = Line.parse("    { \"x\": 1 }");
        child.kind = Kind.DICT;
        child.leafs = 3;
        child.childNesting = 2;
        frame.addLine(child);

        frame.addLine(Line.parse("  ]"));

        frame.foldLines(cfg);
        Line folded = frame.lines.get(0);

        assertEquals(3, folded.leafs);
        assertEquals(2, folded.childNesting);
        assertEquals(Kind.LIST, folded.kind);
        assertFalse(folded.canPack);
    }

}