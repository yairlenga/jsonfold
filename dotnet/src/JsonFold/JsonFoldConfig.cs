namespace JsonFold;

public sealed record JsonFoldConfig
{
    public const int DefaultWidth = 100;
    public const int MaxArrayItems = 1000;
    public const int MaxObjItems = 1000;
    public const int MaxNesting = 10;
    public const int MaxGridLines = 1000;
    public const int MaxWidth = 255;

    public int Width { get; init; } = DefaultWidth;

    public int PackArrayItems { get; init; } = 10;
    public int PackObjItems { get; init; } = 5;
    public int PackNesting { get; init; } = 1;

    public int FoldArrayItems { get; init; } = 10;
    public int FoldObjItems { get; init; } = 5;
    public int FoldNesting { get; init; } = 2;

    public int GridArrayItems { get; init; } = MaxArrayItems;
    public int GridObjItems { get; init; } = MaxObjItems;
    public int GridMinLines { get; init; } = 3;
    public int GridMaxLines { get; init; } = 100;
    public int GridArrayMin { get; init; } = 3;
    public int GridObjMin { get; init; } = 3;

    public int JoinArrayItems { get; init; } = 8;
    public int JoinObjItems { get; init; } = 4;
    public int JoinNesting { get; init; } = 1;

    public static JsonFoldConfig Default { get; } = new();

    public static JsonFoldConfig None { get; } = new()
    {
        PackArrayItems = 0, PackObjItems = 0, PackNesting = 0,
        FoldArrayItems = 0, FoldObjItems = 0, FoldNesting = 0,
        GridArrayItems = 0, GridObjItems = 0, GridMinLines = 0, GridMaxLines = 0,
        GridArrayMin = 0, GridObjMin = 0,
        JoinArrayItems = 0, JoinObjItems = 0, JoinNesting = 0,
    };

    public static JsonFoldConfig? Preset(string? name)
    {
        name ??= "";
        return name switch
        {
            "off" => None with {Width = 0},
            "" or "default" => Default,
            "none" => None,
            "classic" => Default with { GridMaxLines = 0 },
            "low" => Default with { FoldNesting = 0, JoinNesting = 0, GridMaxLines = 0 },
            "med" => Default with { JoinNesting = 0, GridMaxLines = 0 },
            "high" => Default with
            {
                PackArrayItems = 20, PackObjItems = 10, PackNesting = 4,
                FoldArrayItems = 20, FoldObjItems = 10, FoldNesting = 4,
                GridArrayMin = 4, GridObjMin = 4,
                JoinArrayItems = 16, JoinObjItems = 8, JoinNesting = 2,
            },
            "max" => Default with
            {
                Width = MaxWidth,
                PackArrayItems = MaxArrayItems, PackObjItems = MaxObjItems, PackNesting = MaxNesting,
                FoldArrayItems = MaxArrayItems, FoldObjItems = MaxObjItems, FoldNesting = MaxNesting,
                GridArrayItems = MaxArrayItems, GridObjItems = MaxObjItems,
                GridMinLines = 3, GridMaxLines = MaxGridLines,
                GridArrayMin = 4, GridObjMin = 4,
                JoinArrayItems = MaxArrayItems, JoinObjItems = MaxObjItems, JoinNesting = MaxNesting,
            },
            "pack" => None with { PackArrayItems = MaxArrayItems, PackObjItems = MaxObjItems, PackNesting = MaxNesting },
            "fold" => None with { FoldArrayItems = MaxArrayItems, FoldObjItems = MaxObjItems, FoldNesting = MaxNesting },
            "grid" => None with
            {
                PackArrayItems = MaxArrayItems, PackObjItems = MaxObjItems, PackNesting = MaxNesting,
                FoldArrayItems = MaxArrayItems, FoldObjItems = MaxObjItems, FoldNesting = MaxNesting,
                GridArrayItems = MaxArrayItems, GridObjItems = MaxObjItems,
                GridMinLines = 3, GridMaxLines = MaxGridLines,
            },
            "join" => None with
            {
                FoldArrayItems = MaxArrayItems, FoldObjItems = MaxObjItems, FoldNesting = MaxNesting,
                JoinArrayItems = MaxArrayItems, JoinObjItems = MaxObjItems, JoinNesting = MaxNesting,
            },
            _ => throw new ArgumentException($"Unknown JSONFold preset: {name}", nameof(name)),
        };
    }

    public static JsonFoldConfig? Resolve(JsonFoldConfig? config, int? width = null)
    {
        if (config is null) return null;
        return width is > 0 && width.Value != config.Width ? config with { Width = width.Value } : config;
    }

    public static JsonFoldConfig? Resolve(string? preset, int? width = null) => Resolve(Preset(preset), width);
}
