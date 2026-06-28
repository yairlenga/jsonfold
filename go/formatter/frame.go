package jsonfold

type frame struct {
	kind         kind
	indent       int
	depth        int
	lines        []lineType
	partsLength  int
	packLimit    int
	foldLimit    int
	joinLimit    int
	gridLimit    int
	gridMinItems int

	contentLines int
	items        int
	leafs        int

	foldOk       bool
	gridOk       bool
	childNesting int
}

func newFrame(kind kind, indent int, depth int, packLimit int, foldLimit int, joinLimit int, gridLimit int, gridMinItems int) frame {
	return frame{
		kind:         kind,
		indent:       indent,
		depth:        depth,
		packLimit:    packLimit,
		foldLimit:    foldLimit,
		joinLimit:    joinLimit,
		gridLimit:    gridLimit,
		gridMinItems: gridMinItems,
		foldOk:       true,
		childNesting: -1,
	}
}

func (f *frame) updateStats(ln lineType) {
	f.leafs += ln.Leafs
	f.items += ln.Items
	if f.partsLength == 0 {
		f.partsLength += ln.PartsLength
	} else {
		f.partsLength += 1 + ln.PartsLength
	}
	if ln.ChildNesting >= f.childNesting {
		f.childNesting = ln.ChildNesting + 1
	}
}

func (f *frame) addLine(ln lineType) {
	f.lines = append(f.lines, ln)
	if ln.Opener == kindNone && ln.Closer == kindNone {
		f.contentLines++
	}
	f.updateStats(ln)
}

func (f frame) checkFoldLimits(config Config) bool {
	if f.partsLength > config.Width {
		return false
	}
	if f.items > f.foldLimit {
		return false
	}
	if f.childNesting >= config.FoldNesting {
		return false
	}
	return true
}

func (f *frame) foldLines(cfg Config) {
	parts := make([]string, 0)
	for _, ln := range f.lines {
		parts = append(parts, ln.Parts...)
	}

	folded_line := lineType{
		Indent:       f.indent,
		Parts:        parts,
		PartsLength:  f.partsLength,
		Kind:         f.kind,
		Items:        1,
		Leafs:        f.leafs,
		ChildNesting: f.childNesting,
		CanPack:      false,
		CanJoin:      f.childNesting < cfg.JoinNesting,
		CanGrid:      cfg.GridMaxLines > 0 && f.items <= f.gridLimit,
	}
	f.lines = []lineType{folded_line}
}

func (f *frame) joinLines(cfg Config) {
	lines := f.lines
	n := len(lines)
	if n < 2 {
		return
	}

	prevIndex := 0
	writePos := 1

	for readPos := 1; readPos < n; readPos++ {
		ln := lines[readPos]
		prev := &lines[prevIndex]
		if prev.CanJoin && ln.CanJoin && prev.canMerge(ln, f.joinLimit, cfg.Width) {
			prev.mergeLine(ln)
			prev.CanPack = false
		} else {
			if readPos != writePos {
				lines[writePos] = ln
			}
			prevIndex = writePos
			writePos++
		}
	}

	f.lines = lines[:writePos]
	f.contentLines -= n - writePos
}
