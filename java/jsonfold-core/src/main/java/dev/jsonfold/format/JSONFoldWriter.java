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
    private final Config cfg;
    boolean closeFP = false;
    private final Stats stats = new Stats();

    /** Partial line data waiting for a newline. */
    private final StringBuilder pending = new StringBuilder();

    /** Stack of currently open JSON containers. */
    private final ArrayList<Frame> stack = new ArrayList<>();

    /**
     * Create a writer using the default configuration.
     */
    public JSONFoldWriter(Writer fp) {
        this(fp, new Config(), false);
    }

    /**
     * Create a writer using a specific configuration.
     */
    public JSONFoldWriter(Writer fp, Config cfg) {
        this(fp, cfg, false);
    }

    /**
     * Create a writer using a specific configuration, allow close to be skipped
     */
    public JSONFoldWriter(Writer fp, Config cfg, boolean closeFP) {
        this.fp = fp;
        this.cfg = cfg ;
        this.closeFP = closeFP ;
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

        if ( cfg == null ) {
            writeString(s);
            return ;
        }

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
            feed(Line.parse(part));
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
            feed(Line.parse(pending.toString()));
            pending.setLength(0);
            if ( cfg == null ) fp.write("\n") ;
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
        if ( closeFP ) fp.close();
    }

    /**
     * Process one parsed line.
     */
    private void feed(Line line) throws IOException {
        if (line.opener != Kind.NONE) {
            Frame frame = new Frame(
                    line.opener,
                    line.indent,
                    stack.size(),
                    packLimit(line.opener),
                    foldLimit(line.opener),
                    joinLimit(line.opener),
                    gridLimit(line.opener),
                    gridMinItems(line.opener)
                );

            frame.addLine(line);
            stack.add(frame);

            if (line.width() > cfg.width) {
                markNoFold();
            }
            return;
        }

        // Handle Bad JSON - data outside any frame
        if ( stack.isEmpty()) {
            writeLine(line);
            return ;
        }

        Frame frame = stack.get(stack.size() - 1) ;

        if (line.closer != Kind.NONE) {
            frame.addLine(line);
            closeFrame();
            return;
        }

        if ( line.items >= frame.packLimit ) line.canPack = false ;
        if ( line.items >= frame.joinLimit ) line.canJoin = false ;

        addToFrame(frame, line) ;
    }

    /**
     * Add a line to a frame, trying pack/join first.
     */
    private void addToFrame(Frame frame, Line line) throws IOException {
        if ( !frame.isEmpty()) {
            if (!frame.gridOk) {
                Line prev = frame.lastLine();

                if (line.canPack && prev.canPack && tryPack(frame, prev, line)) {
                    return;
                }

                if (line.canJoin && prev.canJoin && tryJoin(frame, prev, line)) {
                    return;
                }
            }
        } else if (!frame.foldOk && !line.canPack && !line.canJoin) {
            writeLine(line);
            return ;
        }

        frame.addLine(line);
        if ( frame.foldOk && line.width() > cfg.width) {
            markNoFold();
        }

        if (line.closer == Kind.NONE ) {
            if ( frame.foldOk && !frame.checkFoldLimits(cfg)) {
                markNoFold();
            }

            if ( frame.gridOk && !line.canGrid ) {
                markNoGrid();
                frame.joinLines(cfg);
            }
        }

        if (!frame.foldOk && !frame.gridOk) {
            streamFrame(frame);
        }
    }

    /**
     * Try scalar packing.
     */
    private boolean tryPack(Frame frame, Line prev, Line line)
    throws IOException {

        if (frame.packLimit <= 1 || !canMerge(prev, line, frame.packLimit)) {
            return false;
        }

        mergeIntoFrame(frame, prev, line);
        if (!prev.canPack) {
            prev.canJoin = false;
        }
        return true;
    }

    /**
     * Try joining a scalar/folded line with the previous line.
     */
    private boolean tryJoin(Frame frame, Line prev, Line line)
    throws IOException {
        if (frame.joinLimit <= 1 || !canMerge(prev, line, frame.joinLimit)) {
            return false;
        }

        mergeIntoFrame(frame, prev, line);
        return true;
    }

    /**
     * Return true if two lines can physically fit on one line.
     */
    private boolean canMerge(Line prev, Line line, int limit) {
        return prev.indent == line.indent
                && prev.items + line.items <= limit
                && prev.indent + prev.partsLength + 1 + line.partsLength <= cfg.width;
    }

    /**
     * Merge {@code line} into {@code prev} and update the owning frame.
     */
    private void mergeIntoFrame(Frame frame, Line prev, Line line)
    throws IOException {
        frame.mergeLine(prev, line);

        if (prev.items >= frame.packLimit || prev.childNesting >= cfg.packNesting) {
            prev.canPack = false;
        }

        if (prev.items >= frame.joinLimit || prev.childNesting >= cfg.joinNesting) {
            prev.canJoin = false;
        }

        if (frame.foldOk) {
            if ( !frame.checkFoldLimits(cfg) ) {
                markNoFold();
                streamFrame(frame) ;
            }
        }
    }

    /**
     * Close the current frame and emit either folded or original lines.
     */
    private void closeFrame() throws IOException {
        Frame frame = stack.remove(stack.size() - 1);

        if ( frame.gridOk ) {
            if ( tryGrid(frame) ) {
                markNoGrid();
            } else {
                markNoGrid();
                frame.joinLines(cfg);
                frame.foldOk = frame.checkFoldLimits(cfg) ;
            }
        }

        if (frame.foldOk) {
            if (tryFold(frame)) {
                if (!stack.isEmpty() && frame.lines.get(0).canGrid) {
                    Frame parent = stack.get(stack.size()-1);
                    if (parent.contentLines == 0) {
                        parent.gridOk = true;
                    }
                }
            }
        }

        emitLines(frame.lines, null);
        frame.lines.clear();
    }

    /**
     * Try folding a complete frame into one line.
     */
    private boolean tryFold(Frame frame) {
        if (!frame.foldOk ||
                frame.contentLines != 1 ||
                frame.lines.size() != 3 ||
                frame.indent + frame.partsLength > cfg.width) {
            return false;
        }

        frame.foldLines(cfg);
        return true;
    }

    private boolean tryGrid(Frame frame) {
        int lineCount = frame.lines.size() - 2;
        if (lineCount < 2 || lineCount < cfg.gridMinLines || lineCount > cfg.gridMaxLines) {
            return false;
        }

        List<Line> lines = frame.lines.subList(1, frame.lines.size() - 1);
        Line first = lines.get(0);
        int partCount = first.parts.size();
    
        if ( partCount < 4 || partCount-2 < frame.gridMinItems) {
            return false ;
        }

        for (Line line : lines) {
            if (line.parts.size() != partCount) return false;
        }

        if (first.kind == Kind.DICT) {
            List<String> sig = first.dictSignature();
            if (sig == null) return false;
            for (Line line : lines) {
                if (!sig.equals(line.dictSignature())) return false;
            }
        }

        int[] widths = new int[partCount];
        for (Line line : lines) {
            for (int i = 0; i < partCount; i++) {
                widths[i] = Math.max(widths[i], line.parts.get(i).length());
            }
        }

        int gridLength = -1;
        for (int w : widths) gridLength += 1 + w;

        if (frame.lines.get(0).indent + gridLength > cfg.width) {
            return false;
        }

        for (Line line : lines) {
            line.applyGrid(widths);
            line.canPack = false;
            line.canJoin = false;
            line.canGrid = false;
        }

        return true;
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
    private void markNoFold() {
        for (Frame frame : stack) {
            frame.foldOk = false;
        }
    }


    private void markNoGrid() {
        for (Frame f : stack) {
            f.gridOk = false;
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

    private int packLimit(Kind kind) {
        return chooseLimit(kind, cfg.packArrayItems, cfg.packObjItems);
    }

    private int foldLimit(Kind kind) {
        return chooseLimit(kind, cfg.foldArrayItems, cfg.foldObjItems);
    }

    private int joinLimit(Kind kind) {
        return chooseLimit(kind, cfg.joinArrayItems, cfg.joinObjItems);
    }

    private int gridLimit(Kind kind) {
        return chooseLimit(kind, cfg.gridArrayItems, cfg.gridObjItems);
    }

    private int gridMinItems(Kind kind) {
        return chooseLimit(kind, cfg.gridArrayMin, cfg.gridObjMin);
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