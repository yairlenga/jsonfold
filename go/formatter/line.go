package jsonfold

import (
	"regexp"
	"strings"
)

type kind int

const (
	kindNone kind = iota
	kindDict
	kindList
)

var keyRE = regexp.MustCompile(`^\s*(?:"[^"\\]*"|'[^'\\]*'|[A-Za-z_$][A-Za-z0-9_$]*|)\s*:`)

type lineType struct {
	Indent       int
	Parts        []string
	PartsLength  int
	Kind         kind
	Items        int
	Leafs        int
	ChildNesting int
	Opener       kind
	Closer       kind
	CanPack      bool
	CanJoin      bool
	CanGrid      bool
}

func parseLine(s string) lineType {
	stripped := strings.TrimLeft(s, " \t")
	body := strings.TrimRight(stripped, " \t\r")

	opener := kindNone
	if strings.HasSuffix(body, "{") {
		opener = kindDict
	} else if strings.HasSuffix(body, "[") {
		opener = kindList
	}

	closer := closingKind(body)
	isBodyLine := opener == kindNone && closer == kindNone
	items := 0
	leafs := 0
	if isBodyLine {
		items = 1
		leafs = 1
	}

	return lineType{
		Indent:       len(s) - len(stripped),
		Parts:        []string{body},
		PartsLength:  len(body),
		Opener:       opener,
		Closer:       closer,
		CanJoin:      isBodyLine,
		CanPack:      isBodyLine,
		Items:        items,
		Leafs:        leafs,
		ChildNesting: -1,
	}
}

func closingKind(s string) kind {
	switch s {
	case "}", "},":
		return kindDict
	case "]", "],":
		return kindList
	default:
		return kindNone
	}
}

func (line lineType) raw() string {
	return strings.Repeat(" ", line.Indent) + strings.Join(line.Parts, " ") + "\n"
}

func (line lineType) width() int {
	return line.Indent + line.PartsLength
}

func (line lineType) canMerge(other lineType, itemLimit int, widthLimit int) bool {
	return line.Indent == other.Indent &&
		line.Items+other.Items <= itemLimit &&
		line.Indent+line.PartsLength+1+other.PartsLength <= widthLimit
}

func (line *lineType) mergeLine(other lineType) {
	line.Parts = append(line.Parts, other.Parts...)
	if len(other.Parts) > 0 {
		line.PartsLength += 1 + other.PartsLength
	}
	line.Items += other.Items
	line.Leafs += other.Leafs
	if other.ChildNesting > line.ChildNesting {
		line.ChildNesting = other.ChildNesting
		line.CanPack = false
	}
}

func (line *lineType) setParts(parts []string) {
	line.Parts = parts
	line.PartsLength = calcPartsLength(parts)
}

func (line lineType) dictSignature() ([]string, bool) {
	if len(line.Parts) < 2 {
		return nil, false
	}
	signature := make([]string, 0, len(line.Parts)-2)
	for _, part := range line.Parts[1 : len(line.Parts)-1] {
		m := keyRE.FindString(part)
		if m == "" {
			return nil, false
		}
		signature = append(signature, m)
	}
	return signature, true
}

func (line *lineType) applyGrid(widths []int) {
	parts := formatParts(line.Parts, widths)
	line.setParts(parts)
}

func calcPartsLength(parts []string) int {
	if len(parts) == 0 {
		return 0
	}
	total := 0
	for _, part := range parts {
		total += len(part) + 1
	}
	return total - 1
}

func formatParts(parts []string, widths []int) []string {
	out := make([]string, len(parts))
	last := len(widths) - 1
	for i, part := range parts {
		if i >= len(widths) {
			out[i] = part
			continue
		}
		if isNumberStart(part) {
			out[i] = leftPad(part, widths[i])
		} else if i < last {
			out[i] = rightPad(part, widths[i])
		} else {
			out[i] = part
		}
	}
	return out
}

func isNumberStart(s string) bool {
	if s == "" {
		return false
	}
	return strings.ContainsRune("-0123456789", rune(s[0]))
}

func leftPad(s string, width int) string {
	if len(s) >= width {
		return s
	}
	return strings.Repeat(" ", width-len(s)) + s
}

func rightPad(s string, width int) string {
	if len(s) >= width {
		return s
	}
	return s + strings.Repeat(" ", width-len(s))
}

func signaturesEqual(a []string, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
