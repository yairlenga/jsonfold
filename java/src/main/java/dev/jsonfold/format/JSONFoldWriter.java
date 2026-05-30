package dev.jsonfold.format;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming writer that folds pretty-printed JSON.
 *
 * <p>{@code JSONFoldWriter} is intended to sit between a normal JSON
 * pretty-printer and the final output destination. It receives ordinary
 * indented JSON text, buffers only currently-open containers, and emits
 * a more compact but still readable representation.
 *
 * <p>The writer does not validate or fully parse JSON. It assumes input
 * follows the usual line-oriented style produced by JSON pretty printers.
 */
public final class JSONFoldWriter extends Writer {

    private final Writer fp;
    private final JSONFold cfg;
    boolean keepOpen = false;
    private final Stats stats = new Stats();

    /** Partial line data waiting for a newline. */
    private final StringBuilder pending = new StringBuilder();

    /** Stack of currently open JSON containers. */
    private final ArrayList<Frame> stack = new ArrayList<>();

    /**
     * Create a writer using the default configuration.
     */
    public JSONFoldWriter(Writer fp) {
        this(fp, new JSONFold(), false);
    }

    /**
     * Create a writer using a specific configuration.
     */
    public JSONFoldWriter(Writer fp, JSONFold cfg) {
        this(fp, cfg, false);
    }

    /**
     * Create a writer using a specific configuration, allow close to be skipped
     */
    public JSONFoldWriter(Writer fp, JSONFold cfg, boolean keepOpen) {
        this.fp = fp;
        this.cfg = cfg ;
        this.keepOpen = keepOpen ;
    }

    /**
     * Return statistics collected by this writer.
     */
    public Stats getStats() {
        return stats;
    }

    /**
     * Write a character buffer.
     */
    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        write(new String(cbuf, off, len));
    }

    /**
     * Write text into the folding filter.
     *
     * <p>Complete lines are parsed immediately. A trailing partial line is
     * retained in {@link #pending} until more data arrives or {@link #finish()}
     * is called.
     */
    @Override
    public void write(String s) throws IOException {
        if (s == null || s.isEmpty()) {
            return;
        }

        stats.addBytesIn(s.length());
        stats.addLinesIn(countNewlines(s));

        pending.append(s);

        int start = 0;
        while (true) {
            int nl = indexOfNewline(pending, start);
            if (nl < 0) {
                if (start > 0) {
                    pending.delete(0, start);
                }

                if (pending.length() > cfg.width) {
                    markNoFold();
                }
                return;
            }

            String part = pending.substring(start, nl);
            feed(Line.parse(part, parentKind()));
            start = nl + 1;
        }
    }

    /**
     * Finish processing any pending text and flush all open frames.
     *
     * <p>This should be called before reading the final output if the writer
     * is not closed.
     */
    public void finish() throws IOException {
        if (!pending.isEmpty()) {
            feed(Line.parse(pending.toString(), parentKind()));
            pending.setLength(0);
        }

        for (Frame frame : stack) {
            for (Line line : frame.lines) {
                writeLine(line);
            }
        }
        stack.clear();
    }

    /**
     * Finish folding and flush the underlying writer.
     */
    @Override
    public void flush() throws IOException {
        finish();
        fp.flush();
    }

    /**
     * Finish folding and close the underlying writer.
     */
    @Override
    public void close() throws IOException {
        finish();
        if ( !keepOpen ) fp.close();
    }

    /**
     * Process one parsed line.
     */
    private void feed(Line line) throws IOException {
        if (line.opener != Kind.NONE) {
            Frame frame = new Frame(
                    line.opener,
                    stack.size(),
                    packLimit(line.opener),
                    foldLimit(line.opener),
                    joinLimit(line.opener));

            frame.lines.add(line);
            stack.add(frame);

            if (line.width() > cfg.width) {
                markNoFold();
            }
            return;
        }

        if (line.closer != Kind.NONE) {
            closeFrame(line, line.closer);
            return;
        }

        emitLine(line);
    }

    /**
     * Emit a line either to the current frame or to the final writer.
     */
    private void emitLine(Line line) throws IOException {
        if (stack.isEmpty()) {
            writeLine(line);
        } else {
            addToFrame(stack.get(stack.size() - 1), line);
        }
    }

    /**
     * Add a line to a frame, trying pack/join first.
     */
    private void addToFrame(Frame frame, Line line) throws IOException {
        if (!frame.isEmpty()) {
            if (line.canPack && tryPack(frame, line)) {
                return;
            }

            if (line.canJoin && tryJoin(frame, line)) {
                return;
            }
        }

        frame.lines.add(line);
        updateFrame(frame, line);

        if (frame.foldOk && line.width() > cfg.width) {
            markNoFold();
        }

        if (!frame.foldOk) {
            streamFrame(frame);
        }
    }

    /**
     * Update frame counters after adding a non-merged line.
     */
    private void updateFrame(Frame frame, Line line) {
        if (line.closer != Kind.NONE) {
            return;
        }

        frame.contentLines++;
        frame.items += line.items;
        frame.leafs += line.leafs;

        if (line.childNesting >= 0) {
            frame.childNesting = Math.max(
                    frame.childNesting,
                    line.childNesting + 1);
        }

        if (frame.foldOk) {
            checkFoldLimits(frame);
        }
    }

    /**
     * Try scalar packing.
     */
    private boolean tryPack(Frame frame, Line line) {
        if (frame.packLimit <= 1 || !line.canPack || frame.isEmpty()) {
            return false;
        }

        Line prev = frame.lastLine();
        if (prev == null || !prev.canPack || !canMerge(prev, line, frame.packLimit)) {
            return false;
        }

        prev.pack(line);
        updateAfterMerge(frame, prev, line);
        return true;
    }

    /**
     * Try joining a scalar/folded line with the previous line.
     */
    private boolean tryJoin(Frame frame, Line line) {
        if (frame.joinLimit <= 1
                || !line.canJoin
                || line.childNesting > cfg.joinNesting
                || frame.isEmpty()) {
            return false;
        }

        Line prev = frame.lastLine();
        if (prev == null
                || !prev.canJoin
                || prev.childNesting > cfg.joinNesting
                || !canMerge(prev, line, frame.joinLimit)) {
            return false;
        }

        prev.join(line);
        updateAfterMerge(frame, prev, line);
        return true;
    }

    /**
     * Return true if two lines can physically fit on one line.
     */
    private boolean canMerge(Line prev, Line line, int limit) {
        return prev.indent == line.indent
                && prev.items + line.items <= limit
                && prev.indent + prev.text.length() + 1 + line.text.length() <= cfg.width;
    }

    /**
     * Update frame counters after pack/join merged {@code line} into {@code prev}.
     */
    private void updateAfterMerge(Frame frame, Line prev, Line line) {
        frame.items += line.items;
        frame.leafs += line.leafs;

        if (prev.items >= frame.packLimit) {
            prev.canPack = false;
        }

        if (prev.items >= frame.joinLimit) {
            prev.canJoin = false;
        }

        if (frame.foldOk) {
            checkFoldLimits(frame);
        }
    }

    /**
     * Check whether a frame can still be folded.
     */
    private void checkFoldLimits(Frame frame) {
        if (!frame.foldOk) {
            return;
        }

        if (frame.contentLines > 1) {
            frame.foldOk = false;
            return;
        }

        if (frame.items > frame.foldLimit) {
            frame.foldOk = false;
            return;
        }

        if (frame.childNesting > cfg.foldNesting) {
            frame.foldOk = false;
        }
    }

    /**
     * Close the current frame and emit either folded or original lines.
     */
    private void closeFrame(Line closer, Kind closingKind) throws IOException {
        if (stack.isEmpty()) {
            writeLine(closer);
            return;
        }

        Frame frame = stack.remove(stack.size() - 1);
        frame.lines.add(closer);

        if (frame.kind != closingKind) {
            frame.foldOk = false;
        }

        Line folded = tryFold(frame);
        if (folded != null) {
            frame.lines.clear();
            frame.lines.add(folded);
        }

        emitLines(frame.lines, null);
        frame.lines.clear();
    }

    /**
     * Try folding a complete frame into one line.
     */
    private Line tryFold(Frame frame) {
        if (!frame.foldOk || frame.contentLines != 1 || frame.lines.size() != 3) {
            return null;
        }

        int foldedLength = -1;
        for (Line line : frame.lines) {
            foldedLength += 1 + line.text.length();
        }

        Line first = frame.lines.get(0);
        if (first.indent + foldedLength > cfg.width) {
            return null;
        }

        return Line.fold(
                frame.lines,
                parentKind(),
                frame.leafs,
                Math.max(0, frame.childNesting));
    }

    /**
     * Stream buffered lines from a frame once folding is impossible.
     *
     * <p>The final packable/joinable line may be kept in the frame so future
     * incoming lines can still merge with it.
     */
    private void streamFrame(Frame frame) throws IOException {
        List<Line> lines = frame.lines;
        if (lines.isEmpty()) {
            return;
        }

        Line keep = null;
        Line last = lines.get(lines.size() - 1);

        if (last.canPack || last.canJoin) {
            keep = last;
            lines.remove(lines.size() - 1);
        }

        emitLines(lines, frame.depth - 1);
        lines.clear();

        if (keep != null) {
            lines.add(keep);
        }
    }

    /**
     * Mark all currently open frames as non-foldable.
     */
    private void markNoFold() throws IOException {
        for (Frame frame : stack) {
            frame.foldOk = false;
        }

        if (!stack.isEmpty()) {
            streamFrame(stack.get(stack.size() - 1));
        }
    }

    /**
     * Emit multiple lines to a parent frame or final output.
     */
    private void emitLines(List<Line> lines, Integer depth) throws IOException {
        if (lines.isEmpty()) {
            return;
        }

        int targetDepth = depth == null ? stack.size() - 1 : depth;

        if (targetDepth < 0) {
            for (Line line : lines) {
                writeLine(line);
            }
            return;
        }

        Frame frame = stack.get(targetDepth);
        for (Line line : lines) {
            addToFrame(frame, line);
        }
    }

    /**
     * Write one complete physical line.
     */
    private void writeLine(Line line) throws IOException {
        writeString(line.raw());
    }

    /**
     * Write raw text to the underlying writer and update output stats.
     */
    private void writeString(String s) throws IOException {
        fp.write(s);
        stats.addBytesOut(s.length());
        stats.addLinesOut(countNewlines(s));
    }

    /**
     * Return the current parent container kind.
     */
    private Kind parentKind() {
        return stack.isEmpty()
                ? Kind.NONE
                : stack.get(stack.size() - 1).kind;
    }

    private int packLimit(Kind kind) {
        return chooseLimit(kind, cfg.packArrayItems, cfg.packObjItems);
    }

    private int foldLimit(Kind kind) {
        return chooseLimit(kind, cfg.foldArrayItems, cfg.foldObjItems);
    }

    private int joinLimit(Kind kind) {
        return chooseLimit(kind, cfg.joinArrayItems, cfg.joinObjItems);
    }

    private static int chooseLimit(Kind kind, int listLimit, int dictLimit) {
        return switch (kind) {
            case LIST -> listLimit;
            case DICT -> dictLimit;
            case NONE -> 0;
        };
    }

    private static int countNewlines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static int indexOfNewline(StringBuilder sb, int start) {
        for (int i = start; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }
}