package dev.jsonfold.format;

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

    /** Line text without leading indentation or trailing newline. */
    String text;

    /** Container kind of the parent frame, or {@link Kind#NONE}. */
    Kind parentKind;

    /** Logical item count represented by this line. */
    int items = 1;

    /** Number of scalar leaf values represented by this line. */
    int leafs = 1;

    /**
     * Maximum folded child nesting represented by this line.
     *
     * <p>A value of {@code -1} means this is a scalar/body line.
     */
    int childNesting = -1;

    /** Opening container kind, or {@link Kind#NONE}. */
    Kind opener = Kind.NONE;

    /** Closing container kind, or {@link Kind#NONE}. */
    Kind closer = Kind.NONE;

    /** Whether this line may be packed with another scalar line. */
    boolean canPack;

    /** Whether this line may be joined with another line. */
    boolean canJoin;

    private Line() {
    }

    /**
     * Parse one pretty-printed JSON line.
     *
     * @param s line text without trailing newline
     * @param parentKind parent container kind
     * @return parsed line metadata
     */
    static Line parse(String s, Kind parentKind) {
        Line line = new Line();

        int start = 0;
        while (start < s.length() && Character.isWhitespace(s.charAt(start))) {
            start++;
        }

        String body = rstrip(s.substring(start));

        line.indent = start;
        line.text = body;
        line.parentKind = parentKind;

        if (body.endsWith("{")) {
            line.opener = Kind.DICT;
        } else if (body.endsWith("[")) {
            line.opener = Kind.LIST;
        }

        line.closer = closingKind(body);

        boolean isBodyLine =
                parentKind != Kind.NONE
                        && line.opener == Kind.NONE
                        && line.closer == Kind.NONE;

        line.canPack = isBodyLine;
        line.canJoin = isBodyLine;

        return line;
    }

    /**
     * Return this line as output text.
     *
     * @return indentation, line text, and trailing newline
     */
    String raw() {
        return " ".repeat(indent) + text + "\n";
    }

    /**
     * Return physical output width.
     *
     * @return indentation plus text length
     */
    int width() {
        return indent + text.length();
    }

    /**
     * Merge another line into this line.
     *
     * <p>Used by pack/join phases after width and count checks have passed.
     *
     * @param other line to append
     */
    void joinLine(Line other) {
        text += " " + other.text;
        items += other.items;
        leafs += other.leafs;

        if (other.childNesting > childNesting) {
            childNesting = other.childNesting;
            canPack = false;
        }
    }

    private static Kind closingKind(String body) {
        return switch (body) {
            case "}", "}," -> Kind.DICT;
            case "]", "]," -> Kind.LIST;
            default -> Kind.NONE;
        };
    }

    private static String rstrip(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}