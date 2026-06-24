package dev.jsonfold.format;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an open JSON container currently under evaluation.
 *
 * <p>A frame accumulates lines belonging to a JSON object or array
 * until folding decisions can be made.
 */
final class Frame {

    /** Container type. */
    final Kind kind;

    /** Indentation (leading spaces) */
    final int indent ;

    /** Stack depth of this frame. */
    final int depth;

    /** Buffered lines belonging to the frame. */
    final List<Line> lines = new ArrayList<>();

    /** Total length of parts in lines */
    int partsLength;

    /** Packing item limit. */
    final int packLimit;

    /** Folding item limit. */
    final int foldLimit;

    /** Join item limit. */
    final int joinLimit;

    /** Grid item Limit */
    final int gridLimit ;

    /** Grid Min Items */
    final int gridMinItems ;

    /** Number of content lines. */
    int contentLines;

    /** Number of logical items. */
    int items;

    /** Number of leaf values. */
    int leafs;

    /** Whether folding is still possible. */
    boolean foldOk = true;

    /** Maximum child nesting depth. */
    int childNesting = -1;

    /** Whether grid can be used */
    boolean gridOk = false;

    Frame(
        Kind kind,
        int indent,
        int depth,
        int packLimit,
        int foldLimit,
        int joinLimit,
        int gridLimit,
        int gridMinItems) {

        this.kind = kind;
        this.indent = indent;
        this.depth = depth;
        this.packLimit = packLimit;
        this.foldLimit = foldLimit;
        this.joinLimit = joinLimit;
        this.gridLimit = gridLimit;
        this.gridMinItems = gridMinItems;
    }

    /**
     * Return true if the frame currently contains no lines.
     */
    boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Return the most recently buffered line.
     *
     * @return last line or null if frame is empty
     */
    Line lastLine() {
        return lines.isEmpty()
                ? null
                : lines.get(lines.size() - 1);
    }

    void updateStats(Line line) {
        leafs += line.leafs;
        items += line.items;
        partsLength += line.partsLength + (partsLength > 0 ? 1 : 0);

        if (line.childNesting >= childNesting) {
            childNesting = line.childNesting + 1;
        }
    }

    void addLine(Line line) {
        lines.add(line);
        if (line.opener == Kind.NONE && line.closer == Kind.NONE) {
            contentLines++;
        }
        updateStats(line);
    }



    void mergeLine(Line prev, Line line) {
        prev.mergeLine(line);
        updateStats(line);
    }

    boolean checkFoldLimits(Config cfg) {
        if (partsLength > cfg.width) {
            return false;
        }

        if (items > foldLimit) {
            return false;
        }

        if (childNesting >= cfg.foldNesting) {
            return false;
        }

        return true;
    }

    /**
     * Create a folded line from opener, body, and closer lines.
     */
    void foldLines(Config config) {

        List<String> parts = new ArrayList<>();
        for (Line line: lines) {
            parts.addAll(line.parts) ;
        };


        Line first = lines.get(0);

        Line line = new Line(first.indent);
        line.setParts(parts);
        line.kind = kind ;

        line.items = 1;
        line.leafs = leafs;
        line.childNesting = childNesting;
        line.opener = Kind.NONE;
        line.closer = Kind.NONE;

        line.canJoin = childNesting < config.joinNesting;
        line.canGrid = config.gridMaxLines > 0 && items <= gridLimit;

        lines.clear();
        lines.add(line) ;
    }

    void joinLines(Config cfg) {
        int n = lines.size();
        if (n < 2) {
            return;
        }

        Line prev = lines.get(0);
        int writePos = 1;

        for (int readPos = 1; readPos < n; readPos++) {
            Line line = lines.get(readPos);

            if (prev.canJoin
                    && line.canJoin
                    && prev.canMerge(line, joinLimit, cfg.width)) {

                prev.mergeLine(line);
                prev.canPack = false;

            } else {
                if (readPos != writePos) {
                    lines.set(writePos, line);
                }

                prev = line;
                writePos++;
            }
        }

        while (lines.size() > writePos) {
            lines.remove(lines.size() - 1);
        }

        contentLines -= (n - writePos);
    }




    @Override
    public String toString() {
        return "Frame{" +
                "kind=" + kind +
                ", depth=" + depth +
                ", lines=" + lines.size() +
                ", items=" + items +
                ", leafs=" + leafs +
                ", contentLines=" + contentLines +
                ", foldOk=" + foldOk +
                ", childNesting=" + childNesting +
                '}';
    }
}
