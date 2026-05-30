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

    /** Stack depth of this frame. */
    final int depth;

    /** Buffered lines belonging to the frame. */
    final List<Line> lines = new ArrayList<>();

    /** Packing item limit. */
    final int packLimit;

    /** Folding item limit. */
    final int foldLimit;

    /** Join item limit. */
    final int joinLimit;

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

    Frame(
            Kind kind,
            int depth,
            int packLimit,
            int foldLimit,
            int joinLimit) {

        this.kind = kind;
        this.depth = depth;
        this.packLimit = packLimit;
        this.foldLimit = foldLimit;
        this.joinLimit = joinLimit;
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
