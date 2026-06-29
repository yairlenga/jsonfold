using JsonFold;

var data = new
{
    meta = new { version = 1, ok = true, name = "jsonfold demo" },
    ids = new[] { 1, 2, 3, 4, 5, 6 },
    matrix = new object[][]
    {
        new object[] { 1, 20, "Red", 300 },
        new object[] { 4000, 50, "Yellow", 6 },
        new object[] { 70, 800, "Green", 9000 },
    },
};

Console.WriteLine(JsonFold.JsonFold.FormatJson(data, width: 100, config: JsonFoldConfig.Preset("default")));
