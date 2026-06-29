using System.Text.Json;

namespace JsonFold;

public sealed class JsonFoldFormatter
{
    public int Indent { get; init; } = 2;
    public JsonFoldConfig? Config { get; init; } = JsonFoldConfig.Default;
    public JsonSerializerOptions? SerializerOptions { get; init; }

    public JsonFoldFormatter() { }

    public JsonFoldFormatter(int? width = null, JsonFoldConfig? config = null, JsonSerializerOptions? serializerOptions = null, int indent = 2)
    {
        Config = JsonFoldConfig.Resolve(config ?? JsonFoldConfig.Default, width);
        SerializerOptions = serializerOptions;
        Indent = indent;
    }

    public static JsonFoldFormatter FromPreset(string? preset = "default", int? width = null, JsonSerializerOptions? serializerOptions = null, int indent = 2) =>
        new() { Config = JsonFoldConfig.Resolve(preset, width), SerializerOptions = serializerOptions, Indent = indent };

    public string FormatJson<T>(T value)
    {
        var pretty = SerializePretty(value);
        return FoldText(pretty);
    }

    public JsonFoldStats WriteJson<T>(T value, TextWriter writer)
    {
        var pretty = SerializePretty(value);
        if (Config is null)
        {
            writer.Write(pretty);
            return new JsonFoldStats
            {
                BytesIn = pretty.Length,
                BytesOut = pretty.Length,
                LinesIn = CountNewlines(pretty),
                LinesOut = CountNewlines(pretty),
            };
        }

        using var foldWriter = new JsonFoldWriter(writer, Config, closeBase: false);
        foldWriter.Write(pretty);
        foldWriter.Finish();
        return foldWriter.Stats;
    }

    public string FoldText(string prettyJson)
    {
        if (Config is null) return prettyJson;
        using var sw = new StringWriter();
        using var foldWriter = new JsonFoldWriter(sw, Config, closeBase: false);
        foldWriter.Write(prettyJson);
        foldWriter.Finish();
        return sw.ToString();
    }

    private string SerializePretty<T>(T value)
    {
        var options = SerializerOptions is null ? new JsonSerializerOptions() : new JsonSerializerOptions(SerializerOptions);
        options.WriteIndented = true;
        var text = JsonSerializer.Serialize(value, options);
        return text.EndsWith('\n') ? text : text + "\n";
    }

    private static int CountNewlines(string s) => s.Count(ch => ch == '\n');
}

public static class JsonFold
{
    public static JsonFoldConfig? Config(string? preset = "default", int? width = null) => JsonFoldConfig.Resolve(preset, width);

    public static JsonFoldWriter CreateWriter(TextWriter writer, int? width = null, JsonFoldConfig? config = null, bool closeBase = false) =>
        new(writer, JsonFoldConfig.Resolve(config ?? JsonFoldConfig.Default, width), closeBase);

    public static string FoldText(string prettyJson, int? width = null, JsonFoldConfig? config = null) =>
        new JsonFoldFormatter(width, config ?? JsonFoldConfig.Default).FoldText(prettyJson);

    public static string FormatJson<T>(T value, int? width = null, JsonFoldConfig? config = null, JsonSerializerOptions? serializerOptions = null) =>
        new JsonFoldFormatter(width, config ?? JsonFoldConfig.Default, serializerOptions).FormatJson(value);

    public static JsonFoldStats WriteJson<T>(T value, TextWriter writer, int? width = null, JsonFoldConfig? config = null, JsonSerializerOptions? serializerOptions = null) =>
        new JsonFoldFormatter(width, config ?? JsonFoldConfig.Default, serializerOptions).WriteJson(value, writer);
}
