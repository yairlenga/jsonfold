namespace JsonFold;

internal sealed class Frame
{
    public Kind Kind { get; }
    public int Indent { get; }
    public int Depth { get; }
    public List<Line> Lines { get; } = new();
    public int PartsLength { get; private set; }
    public int PackLimit { get; }
    public int FoldLimit { get; }
    public int JoinLimit { get; }
    public int GridLimit { get; }
    public int GridMinItems { get; }
    public int ContentLines { get; private set; }
    public int Items { get; private set; }
    public int Leafs { get; private set; }
    public bool FoldOk { get; set; } = true;
    public bool GridOk { get; set; }
    public int ChildNesting { get; private set; } = -1;

    public Frame(Kind kind, int indent, int depth, int packLimit, int foldLimit, int joinLimit, int gridLimit, int gridMinItems)
    {
        Kind = kind;
        Indent = indent;
        Depth = depth;
        PackLimit = packLimit;
        FoldLimit = foldLimit;
        JoinLimit = joinLimit;
        GridLimit = gridLimit;
        GridMinItems = gridMinItems;
    }

    public bool IsEmpty => Lines.Count == 0;
    public Line? LastLine => Lines.Count == 0 ? null : Lines[^1];

    public void AddLine(Line line)
    {
        Lines.Add(line);
        if (line.Opener == Kind.None && line.Closer == Kind.None) ContentLines++;
        UpdateStats(line);
    }

    public void MergeLine(Line prev, Line line)
    {
        prev.MergeLine(line);
        UpdateStats(line);
    }

    public bool CheckFoldLimits(JsonFoldConfig cfg) =>
        PartsLength <= cfg.Width &&
        Items <= FoldLimit &&
        ChildNesting < cfg.FoldNesting;

    public void FoldLines(JsonFoldConfig cfg)
    {
        var parts = new List<string>();
        foreach (var line in Lines) parts.AddRange(line.Parts);

        var folded = new Line(Lines[0].Indent);
        folded.SetParts(parts);
        folded.Kind = Kind;
        folded.Items = 1;
        folded.Leafs = Leafs;
        folded.ChildNesting = ChildNesting;
        folded.CanPack = false;
        folded.CanJoin = ChildNesting < cfg.JoinNesting;
        folded.CanGrid = cfg.GridMaxLines > 0 && Items <= GridLimit;

        Lines.Clear();
        Lines.Add(folded);
    }

    public void JoinLines(JsonFoldConfig cfg)
    {
        var n = Lines.Count;
        if (n < 2) return;

        var prev = Lines[0];
        var writePos = 1;
        for (var readPos = 1; readPos < n; readPos++)
        {
            var line = Lines[readPos];
            if (prev.CanJoin && line.CanJoin && prev.CanMerge(line, JoinLimit, cfg.Width))
            {
                prev.MergeLine(line);
                prev.CanPack = false;
            }
            else
            {
                if (readPos != writePos) Lines[writePos] = line;
                prev = line;
                writePos++;
            }
        }
        Lines.RemoveRange(writePos, Lines.Count - writePos);
        ContentLines -= n - writePos;
    }

    private void UpdateStats(Line line)
    {
        Leafs += line.Leafs;
        Items += line.Items;
        PartsLength += line.PartsLength + (PartsLength > 0 ? 1 : 0);
        if (line.ChildNesting >= ChildNesting) ChildNesting = line.ChildNesting + 1;
    }

}
