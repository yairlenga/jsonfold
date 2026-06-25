package dev.jsonfold.format;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a single pretty-printed JSON line.
 *
 * <p>
 * Lines are the basic unit processed by the folding engine.
 * A line may represent:
 *
 * <ul>
 * <li>a scalar value or property</li>
 * <li>an opening container ({ or [)</li>
 * <li>a closing container (} or ])</li>
 * <li>a previously folded container</li>
 * </ul>
 *
 * <p>
 * Additional metadata tracks packing and folding eligibility.
 */
final class Line {

    /** Leading indentation width in spaces. */
    final int indent;

    /** For folded lines - the line kind */
    Kind kind = Kind.NONE ;

    List<String> parts = new ArrayList<>() ;
    int partsLength = 0 ;

    /** Line text without leading indentation or trailing newline. */
//    String text;

    /** Logical item count represented by this line. */
    int items ;

    /** Number of scalar leaf values represented by this line. */
    int leafs ;

    /**
     * Maximum folded child nesting represented by this line.
     *
     * <p>
     * A value of {@code -1} means this is a scalar/body line.
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

    /** Whether this line may participate in a grid. */   
    boolean canGrid;

    static int partsLength(List<String> parts) {
        if (parts.isEmpty()) {
            return 0;
        }

        int length = parts.size() - 1;   // spaces between parts

        for (String part : parts) {
            length += part.length();
        }

        return length;
    }

    void setParts(List<String> parts) {
        this.parts = parts;
        this.partsLength = partsLength(parts);
    }

    Line(int indent) {
        this.indent = indent;
    }

    /**
     * Parse one pretty-printed JSON line.
     *
     * @param s          line text without trailing newline
     * @return parsed line metadata
     */
    static final String EMPTY_OBJECT = "{ }" ;
    static final String EMPTY_OBJECT1 = "{ }," ;
    static final String EMPTY_LIST = "[ ]" ;
    static final String EMPTY_LIST1 = "[ ]," ;

    static Line parse(String s) {

        int start = 0;
        while (start < s.length() && Character.isWhitespace(s.charAt(start))) {
            start++;
        }

        String body = rstrip(s.substring(start));

        Line line = new Line(start) ;

        if (body.endsWith("{")) {
            line.opener = Kind.DICT;
        } else if (body.endsWith("[")) {
            line.opener = Kind.LIST;
        } else if (body.endsWith(EMPTY_OBJECT)) {
            body = body.substring(0, body.length()-EMPTY_OBJECT.length()) + "{}" ;
        } else if (body.endsWith(EMPTY_OBJECT1)) {
            body = body.substring(0, body.length()-EMPTY_OBJECT1.length()) + "{}," ;
        } else if (body.endsWith(EMPTY_LIST)) {
            body = body.substring(0, body.length()-EMPTY_LIST.length()) + "[]" ;
        } else if (body.endsWith(EMPTY_LIST1)){
            body = body.substring(0, body.length()-EMPTY_LIST1.length()) + "[]," ;
        } else {
            line.closer = closingKind(body);
        }

        line.parts.add(body) ;
        line.partsLength = body.length() ;


        boolean isBodyLine = line.opener == Kind.NONE && line.closer == Kind.NONE;

        line.canPack = isBodyLine;
        line.canJoin = isBodyLine;
        line.leafs = isBodyLine ? 1 : 0 ;
        line.items = isBodyLine ? 1 : 0 ;

        return line;
    }

    /**
     * Return this line as output text.
     *
     * @return indentation, line text, and trailing newline
     */
    String raw() {
        return " ".repeat(indent) + String.join(" ", parts) + "\n";
    }

    /**
     * Return physical output width.
     *
     * @return indentation plus text length
     */
    int width() {
        return indent + partsLength;
    }

    boolean canMerge(Line other, int itemLimit, int widthLimit) {
        return (
            this.indent == other.indent
            && this.items + other.items <= itemLimit
            && this.indent + this.partsLength + 1 + other.partsLength <= widthLimit
        );
    }

    /**
     * Merge another line into this line.
     *
     * <p>
     * Used by pack/join phases after width and count checks have passed.
     *
     * @param other line to append
     */
    void mergeLine(Line other) {
        if ( other.parts.isEmpty() ) return ;
        this.parts.addAll(other.parts) ;
        this.partsLength += 1 + other.partsLength;
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



    private static final Pattern KEY_RE = Pattern.compile(
            "^\\s*(?:\"[^\"\\\\]*\"|'[^'\\\\]*'|[A-Za-z_$][A-Za-z0-9_$]*)\\s*:"
    );

    List<String> dictSignature() {
        ArrayList<String> signature = new ArrayList<>();

        for (int i = 1; i < parts.size() - 1; i++) {
            Matcher m = KEY_RE.matcher(parts.get(i));
            if (!m.find()) {
                return null;
            }
            signature.add(m.group());
        }

        return signature;
    }

    private static List<String> formatParts(List<String> parts, int[] widths) {
        ArrayList<String> out = new ArrayList<>(parts.size());
        int last = widths.length - 1;

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (!part.isEmpty() && "-0123456789".indexOf(part.charAt(0)) >= 0) {
                out.add(" ".repeat(widths[i] - part.length()) + part);
            } else if (i < last) {
                out.add(part + " ".repeat(widths[i] - part.length()));
            } else {
                out.add(part);
            }
        }

        return out;
    }

    void applyGrid(int[] widths) {
        List<String> new_parts = formatParts(parts, widths) ;
        setParts(new_parts);
    }

}