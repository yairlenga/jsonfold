using System.Text.RegularExpressions;

namespace JsonFold;

internal sealed class Line
{
    private static readonly Regex KeyRegex = new(@"^\s*(?:""[^""\\]*""|'[^'\\]*'|[A-Za-z_$][A-Za-z0-9_$]*)\s*:", RegexOptions.Compiled);

    public int Indent { get; }
    public Kind Kind { get; set; } = Kind.None;
    public List<string> Parts { get; private set; } = new();
    public int PartsLength { get; private set; }
    public int Items { get; set; }
    public int Leafs { get; set; }
    public int ChildNesting { get; set; } = -1;
    public Kind Opener { get; set; } = Kind.None;
    public Kind Closer { get; set; } = Kind.None;
    public bool CanPack { get; set; }
    public bool CanJoin { get; set; }
    public bool CanGrid { get; set; }

    internal Line(int indent) => Indent = indent;

    public static Line Parse(string s)
    {
        var start = 0;
        while (start < s.Length && char.IsWhiteSpace(s[start])) start++;
        var body = s[start..].TrimEnd();

        var line = new Line(start);
        line.SetParts(new List<string> { body });

        if (body.EndsWith('{')) line.Opener = Kind.Dict;
        else if (body.EndsWith('[')) line.Opener = Kind.List;

        line.Closer = body switch
        {
            "}" or "}," => Kind.Dict,
            "]" or "]," => Kind.List,
            _ => Kind.None,
        };

        var isBody = line.Opener == Kind.None && line.Closer == Kind.None;
        line.CanPack = isBody;
        line.CanJoin = isBody;
        line.Items = isBody ? 1 : 0;
        line.Leafs = isBody ? 1 : 0;
        return line;
    }

    public string Raw() => new string(' ', Indent) + string.Join(" ", Parts) + "\n";
    public int Width() => Indent + PartsLength;

    public bool CanMerge(Line other, int itemLimit, int widthLimit) =>
        Indent == other.Indent &&
        Items + other.Items <= itemLimit &&
        Indent + PartsLength + 1 + other.PartsLength <= widthLimit;

    public void MergeLine(Line other)
    {
        if (other.Parts.Count == 0) return;
        Parts.AddRange(other.Parts);
        PartsLength += 1 + other.PartsLength;
        Items += other.Items;
        Leafs += other.Leafs;
        if (other.ChildNesting > ChildNesting)
        {
            ChildNesting = other.ChildNesting;
            CanPack = false;
        }
    }

    public void SetParts(List<string> parts)
    {
        Parts = parts;
        PartsLength = CalculatePartsLength(parts);
    }

    public List<string>? DictSignature()
    {
        var signature = new List<string>();
        for (var i = 1; i < Parts.Count - 1; i++)
        {
            var match = KeyRegex.Match(Parts[i]);
            if (!match.Success) return null;
            signature.Add(match.Value);
        }
        return signature;
    }

    public void ApplyGrid(int[] widths) => SetParts(FormatParts(Parts, widths));

    private static int CalculatePartsLength(IReadOnlyCollection<string> parts)
    {
        if (parts.Count == 0) return 0;
        var length = parts.Count - 1;
        foreach (var part in parts) length += part.Length;
        return length;
    }

    private static List<string> FormatParts(IReadOnlyList<string> parts, IReadOnlyList<int> widths)
    {
        var output = new List<string>(parts.Count);
        var last = widths.Count - 1;
        for (var i = 0; i < parts.Count; i++)
        {
            var part = parts[i];
            if (part.Length > 0 && "-0123456789".Contains(part[0]))
                output.Add(part.PadLeft(widths[i]));
            else if (i < last)
                output.Add(part.PadRight(widths[i]));
            else
                output.Add(part);
        }
        return output;
    }
}
