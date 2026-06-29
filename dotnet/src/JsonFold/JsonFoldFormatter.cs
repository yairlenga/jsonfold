namespace JsonFold.Format;

using System.Text.Encodings.Web;
using System.Text.Json;

public sealed class JsonFoldFormatter
{
    private int Indent { get; init; } = 2;
    public JsonFoldConfig? Config { get; init; } = JsonFoldConfig.Default;
    public JsonSerializerOptions? SerializerOptions { get; init; }
    public bool Gold { get; init ; } = true ;

    public bool doClose { get ; init ; } = false ;

    public JsonFoldFormatter() { }

    public JsonFoldFormatter(int? width = null, JsonFoldConfig? config = null,
        JsonSerializerOptions? serializerOptions = null,
        int? indent = null)
    {
        Config = JsonFoldConfig.Resolve(config ?? JsonFoldConfig.Default, width);
        SerializerOptions = resolveOptions(serializerOptions);
        Indent = resolveIndent(indent) ;
    }

    public JsonFoldFormatter(int? width = null, string? preset = "default",
        JsonSerializerOptions? serializerOptions = null,
        int? indent = null) 
    {
        Config = JsonFoldConfig.Resolve(preset, width);
        SerializerOptions = resolveOptions(serializerOptions);
        Indent = resolveIndent(indent) ;    }

    public string FormatJson<T>(T value)
    {
        var pretty = SerializePretty(value);
        return FoldText(pretty);
    }

    private JsonFoldStats streamTo<T>(T value, TextWriter writer)
    {

        using var jfw = new JsonFoldWriter(writer, Config, doClose: doClose);
        using var jfs = new FormatterStream(jfw) ;
        JsonSerializer.Serialize(jfs, value, SerializerOptions) ;
        jfs.Flush();
        jfs.Close() ;
        jfw.Finish() ;
        jfw.Flush();
        jfw.Close() ;
        return jfw.Stats;
    }

    public JsonFoldStats WriteJson<T>(T value, TextWriter writer)
    {
        return streamTo(value, writer) ;    
    }

    public string FoldText(string prettyJson)
    {
        if (Config is null) return prettyJson;
        using var sw = new StringWriter();
        using var foldWriter = new JsonFoldWriter(sw, Config);
        foldWriter.Write(prettyJson);
        foldWriter.Finish();
        return sw.ToString();
    }

    private int resolveIndent(int? indent)
    {
        return indent.HasValue ? indent.Value : 2;
    }

    private JsonSerializerOptions? resolveOptions(JsonSerializerOptions ? options)
    {
        if ( options == null)
        {
            options = new JsonSerializerOptions {
                        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
                };
        }
        options.WriteIndented = true;
        return options ;        
    }

    private string SerializePretty<T>(T value)
    {
        var text = JsonSerializer.Serialize(value, SerializerOptions);
        return text.EndsWith('\n') ? text : text + "\n";
    }

    private static int CountNewlines(string s) => s.Count(ch => ch == '\n');



    public static JsonFoldConfig? JsonfoldConfig(string? preset = "default", int? width = null) => JsonFoldConfig.Resolve(preset, width);

    public static JsonFoldWriter CreateWriter(TextWriter writer, int? width = null, JsonFoldConfig? config = null, bool closeBase = false) =>
        new(writer, JsonFoldConfig.Resolve(config ?? JsonFoldConfig.Default, width), closeBase);

    public static string FoldText(string prettyJson, int? width = null, JsonFoldConfig? config = null) =>
        new JsonFoldFormatter(width, config ?? JsonFoldConfig.Default).FoldText(prettyJson);

    public static string FormatJson<T>(T value, int? width = null, JsonFoldConfig? config = null, JsonSerializerOptions? serializerOptions = null) =>
        new JsonFoldFormatter(width, config ?? JsonFoldConfig.Default, serializerOptions).FormatJson(value);

    public static JsonFoldStats WriteJson<T>(T value, TextWriter writer, int? width = null, JsonFoldConfig? config = null, JsonSerializerOptions? serializerOptions = null) =>
        new JsonFoldFormatter(width, config ?? JsonFoldConfig.Default, serializerOptions).WriteJson(value, writer);
}
