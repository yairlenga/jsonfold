package dev.jsonfold;

/**
 * Represents a single pretty-printed JSON line.
 *
 * <p>Lines are the basic unit processed by the folding engine.
 * A line may represent:
 *
 * <ul>
 *   <li>a scalar value or property</li>
 *   <li>an opening container ({ or [)</li>
 *   <li>a closing container (} or ])</li>
 *   <li>a previously folded container</li>
 * </ul>
 *
 * <p>Additional metadata tracks packing and folding eligibility.
 */
final class Line {

    /** Leading indentation width in spaces. */
    int indent;

    /** Text content excluding indentation and trailing newline. */
    String text;

    /** Parent container type. */
    Kind parentKind = Kind.NONE;

    /** Number of packed logical items represented by this line. */
    int items = 1;

    /** Total leaf elements represented by this line. */
    int leafs = 1;

    /**
     * Maximum folded child nesting depth.
     *
     * <p>-1 indicates a scalar line.
     */
    int childNesting = -1;

    /** Opening container marker, if any. */
    Kind opener = Kind.NONE;

    /** Closing container marker, if any. */
    Kind closer = Kind.NONE;

    /** Whether this line may participate in packing. */
    boolean canPack = true;

    /** Whether this line may participate in joining. */
    boolean canJoin = true;

    /**
     * Parse a pretty-printed JSON line.
     *
     * @param text line text without trailing newline
     * @param parentKind parent container type
     * @return parsed line
     */
    static Line parse(String text, Kind parentKind) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the physical width of the line.
     *
     * @return indentation + text length
     */
    int width() {
        return indent + text.length();
    }

    /**
     * Convert back to output form.
     *
     * @return line including trailing newline
     */
    String raw() {
        return " ".repeat(indent) + text + "\n";
    }

    /**
     * Merge another line into this one.
     *
     * <p>Used during pack/join operations.
     *
     * @param other line to merge
     */
    void joinLine(Line other) {
        throw new UnsupportedOperationException();
    }
}