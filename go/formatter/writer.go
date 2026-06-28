package jsonfold

import (
	"io"
	"strings"
)

type Writer struct {
	fp      io.Writer
	stats   Stats
	cfg     Config
	enabled bool
	pending string
	stack   []frame
}

func NewWriter(fp io.Writer, cfg Config) *Writer {
	return &Writer{fp: fp, cfg: cfg, enabled: true}
}

func NewOffWriter(fp io.Writer) *Writer {
	return &Writer{fp: fp, enabled: false}
}

func (w *Writer) Write(p []byte) (int, error) {
	s := string(p)
	sLen := len(s)
	w.stats.BytesIn += sLen

	if !w.enabled {
		w.stats.LinesIn += strings.Count(s, "\n")
		_, err := w.writeString(s)
		if err != nil {
			return 0, err
		}
		return len(p), nil
	}

	nlPos := strings.IndexByte(s, '\n')
	if nlPos < 0 {
		w.pending += s
		return len(p), nil
	}

	nl2Pos := strings.IndexByte(s[nlPos+1:], '\n')
	if nl2Pos < 0 {
		w.stats.LinesIn++
		s2 := w.pending + s[:nlPos]
		w.pending = s[nlPos+1:]
		if err := w.feed(parseLine(s2)); err != nil {
			return 0, err
		}
		return len(p), nil
	}

	parts := splitLinesKeepEnds(s)
	w.stats.LinesIn += len(parts) - 1

	if w.pending != "" {
		parts[0] = w.pending + parts[0]
		w.pending = ""
	}

	if len(parts) > 0 && !strings.HasSuffix(parts[len(parts)-1], "\n") {
		w.pending = parts[len(parts)-1]
		parts = parts[:len(parts)-1]
	}

	for _, part := range parts {
		if err := w.feed(parseLine(strings.TrimSuffix(part, "\n"))); err != nil {
			return 0, err
		}
	}
	return len(p), nil
}

func (w *Writer) Finish() error {
	if w.pending != "" {
		if err := w.feed(parseLine(w.pending)); err != nil {
			return err
		}
		w.pending = ""
	}

	for i := range w.stack {
		frame := &w.stack[i]
		for _, ln := range frame.lines {
			if err := w.writeLine(ln); err != nil {
				return err
			}
		}
	}
	w.stack = nil
	return nil
}

func (w *Writer) Flush() error {
	if err := w.Finish(); err != nil {
		return err
	}
	if flusher, ok := w.fp.(interface{ Flush() error }); ok {
		return flusher.Flush()
	}
	return nil
}

func (w *Writer) Close() error {
	if err := w.Flush(); err != nil {
		return err
	}
	if closer, ok := w.fp.(io.Closer); ok {
		return closer.Close()
	}
	return nil
}

func (w *Writer) Stats() Stats {
	return w.stats
}

func (w *Writer) writeString(s string) (int, error) {
	n, err := io.WriteString(w.fp, s)
	w.stats.BytesOut += n
	w.stats.LinesOut += strings.Count(s[:n], "\n")
	return n, err
}

func (w *Writer) writeLine(ln lineType) error {
	_, err := w.writeString(ln.raw())
	return err
}

func (w *Writer) feed(ln lineType) error {
	if ln.Opener != kindNone {
		frame := newFrame(
			ln.Opener,
			ln.Indent,
			len(w.stack),
			w.packLimit(ln.Opener),
			w.foldLimit(ln.Opener),
			w.joinLimit(ln.Opener),
			w.gridLimit(ln.Opener),
			w.gridMinItems(ln.Opener),
		)
		frame.addLine(ln)
		w.stack = append(w.stack, frame)
		return nil
	}

	if len(w.stack) == 0 {
		return w.writeLine(ln)
	}

	frame := &w.stack[len(w.stack)-1]
	if ln.Closer != kindNone {
		if frame.kind != ln.Closer {
			frame.foldOk = false
			frame.gridOk = false
		}
		frame.addLine(ln)
		return w.closeFrame()
	}

	if ln.Items >= frame.packLimit {
		ln.CanPack = false
	}
	if ln.Items >= frame.joinLimit {
		ln.CanJoin = false
	}
	return w.addToFrame(frame, ln)
}

func (w *Writer) emitLines(lines []lineType, depth *int) error {
	if len(lines) == 0 {
		return nil
	}
	targetDepth := len(w.stack) - 1
	if depth != nil {
		targetDepth = *depth
	}
	if targetDepth < 0 {
		for _, ln := range lines {
			if err := w.writeLine(ln); err != nil {
				return err
			}
		}
		return nil
	}
	for _, ln := range lines {
		if err := w.addToFrame(&w.stack[targetDepth], ln); err != nil {
			return err
		}
	}
	return nil
}

func (w *Writer) chooseLimit(kind kind, defaultValue int, listLimit int, dictLimit int) int {
	if kind == kindList {
		return listLimit
	}
	if kind == kindDict {
		return dictLimit
	}
	return defaultValue
}

func (w *Writer) packLimit(kind kind) int {
	return w.chooseLimit(kind, 0, w.cfg.PackArrayItems, w.cfg.PackObjItems)
}

func (w *Writer) foldLimit(kind kind) int {
	return w.chooseLimit(kind, 0, w.cfg.FoldArrayItems, w.cfg.FoldObjItems)
}

func (w *Writer) joinLimit(kind kind) int {
	return w.chooseLimit(kind, 0, w.cfg.JoinArrayItems, w.cfg.JoinObjItems)
}

func (w *Writer) gridLimit(kind kind) int {
	return w.chooseLimit(kind, 0, w.cfg.GridArrayItems, w.cfg.GridObjItems)
}

func (w *Writer) gridMinItems(kind kind) int {
	return w.chooseLimit(kind, 0, w.cfg.GridArrayMin, w.cfg.GridObjMin)
}

func (w *Writer) addToFrame(frame *frame, ln lineType) error {
	if len(frame.lines) > 0 {
		if !frame.gridOk {
			prev := &frame.lines[len(frame.lines)-1]
			if ln.CanPack && prev.CanPack && w.tryPack(frame, prev, ln) {
				return nil
			}
			if ln.CanJoin && prev.CanJoin && w.tryJoin(frame, prev, ln) {
				return nil
			}
		}
	} else if !frame.foldOk && !ln.CanPack && !ln.CanJoin {
		return w.writeLine(ln)
	}

	frame.addLine(ln)

	if frame.foldOk && ln.width() > w.cfg.Width {
		w.markNoFold()
	}

	if ln.Closer == kindNone {
		if frame.foldOk && !frame.checkFoldLimits(w.cfg) {
			w.markNoFold()
		}

		if frame.gridOk && !ln.CanGrid {
			w.markNoGrid()
			frame.joinLines(w.cfg)
		}
	}

	if !frame.foldOk && !frame.gridOk {
		return w.streamFrame(frame)
	}
	return nil
}

func (w *Writer) mergeIntoFrame(frame *frame, prev *lineType, ln lineType) error {
	prev.mergeLine(ln)

	if prev.Items >= frame.packLimit || prev.ChildNesting >= w.cfg.PackNesting {
		prev.CanPack = false
	}
	if prev.Items >= frame.joinLimit || prev.ChildNesting >= w.cfg.JoinNesting {
		prev.CanJoin = false
	}

	frame.updateStats(ln)

	if frame.foldOk && !frame.checkFoldLimits(w.cfg) {
		w.markNoFold()
		return w.streamFrame(frame)
	}
	return nil
}

func (w *Writer) tryPack(frame *frame, prev *lineType, ln lineType) bool {
	if frame.packLimit <= 1 || !prev.canMerge(ln, frame.packLimit, w.cfg.Width) {
		return false
	}
	_ = w.mergeIntoFrame(frame, prev, ln)
	if !prev.CanPack {
		prev.CanJoin = false
	}
	return true
}

func (w *Writer) tryJoin(frame *frame, prev *lineType, ln lineType) bool {
	if frame.joinLimit <= 1 || !prev.canMerge(ln, frame.joinLimit, w.cfg.Width) {
		return false
	}
	_ = w.mergeIntoFrame(frame, prev, ln)
	return true
}

func (w *Writer) closeFrame() error {
	frame := w.stack[len(w.stack)-1]
	w.stack = w.stack[:len(w.stack)-1]

	if frame.gridOk {
		if w.tryGrid(&frame) {
			w.markNoGrid()
		} else {
			w.markNoGrid()
			frame.joinLines(w.cfg)
			frame.foldOk = frame.checkFoldLimits(w.cfg)
		}
	}

	if frame.foldOk && w.tryFold(&frame) {
		if len(w.stack) > 0 && frame.lines[0].CanGrid {
			parentFrame := &w.stack[len(w.stack)-1]
			if parentFrame.contentLines == 0 {
				parentFrame.gridOk = true
			}
		}
	}

	return w.emitLines(frame.lines, nil)
}

func (w *Writer) tryFold(frame *frame) bool {
	if !frame.foldOk ||
		frame.contentLines != 1 ||
		len(frame.lines) != 3 ||
		frame.indent+frame.partsLength > w.cfg.Width {
		return false
	}
	frame.foldLines(w.cfg)
	return true
}

func (w *Writer) tryGrid(frame *frame) bool {
	if frame.kind != kindList {
		return false
	}
	lineCount := len(frame.lines) - 2
	if lineCount < 2 || lineCount < w.cfg.GridMinLines || lineCount > w.cfg.GridMaxLines {
		return false
	}

	lines := frame.lines[1 : len(frame.lines)-1]
	firstLine := lines[0]
	partCount := len(firstLine.Parts)
	if partCount < 4 || partCount-2 < frame.gridMinItems {
		return false
	}

	for _, ln := range lines {
		if len(ln.Parts) != partCount {
			return false
		}
	}

	if firstLine.Kind == kindDict {
		dictSignature, ok := firstLine.dictSignature()
		if !ok {
			return false
		}
		for _, ln := range lines {
			lineSignature, ok := ln.dictSignature()
			if !ok || !signaturesEqual(lineSignature, dictSignature) {
				return false
			}
		}
	}

	widths := make([]int, partCount)
	for i := 0; i < partCount; i++ {
		maxWidth := 0
		for _, ln := range lines {
			if len(ln.Parts[i]) > maxWidth {
				maxWidth = len(ln.Parts[i])
			}
		}
		widths[i] = maxWidth
	}

	gridedLength := 0
	for _, width := range widths {
		gridedLength += 1 + width
	}
	gridedLength--
	if frame.lines[0].Indent+gridedLength > w.cfg.Width {
		return false
	}

	for i := 1; i < len(frame.lines)-1; i++ {
		frame.lines[i].applyGrid(widths)
		frame.lines[i].CanPack = false
		frame.lines[i].CanJoin = false
		frame.lines[i].CanGrid = false
	}
	return true
}

func (w *Writer) streamFrame(frame *frame) error {
	lines := frame.lines
	if len(lines) == 0 {
		return nil
	}

	last := lines[len(lines)-1]
	keepLast := last.CanPack || last.CanJoin
	if keepLast {
		lines = lines[:len(lines)-1]
	}

	depth := frame.depth - 1
	if err := w.emitLines(lines, &depth); err != nil {
		return err
	}
	frame.lines = frame.lines[:0]
	if keepLast {
		frame.lines = append(frame.lines, last)
	}
	return nil
}

func (w *Writer) markNoFold() {
	for i := range w.stack {
		w.stack[i].foldOk = false
	}
}

func (w *Writer) markNoGrid() {
	for i := range w.stack {
		w.stack[i].gridOk = false
	}
}

func splitLinesKeepEnds(s string) []string {
	if s == "" {
		return nil
	}
	var lines []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			lines = append(lines, s[start:i+1])
			start = i + 1
		}
	}
	if start < len(s) {
		lines = append(lines, s[start:])
	}
	return lines
}
