package jsonfold

const (
	defaultWidth  = 100
	maxArrayItems = 1000
	maxObjItems   = 1000
	maxNesting    = 10
	maxGridLines  = 1000
	maxWidth      = 255
)

type Config struct {
	Width int

	PackArrayItems int
	PackObjItems   int
	PackNesting    int

	FoldArrayItems int
	FoldObjItems   int
	FoldNesting    int

	GridArrayItems int
	GridObjItems   int
	GridMinLines   int
	GridMaxLines   int
	GridArrayMin   int
	GridObjMin     int

	JoinArrayItems int
	JoinObjItems   int
	JoinNesting    int
}

var defaultConfig = Config{
	Width: defaultWidth,

	PackArrayItems: 10,
	PackObjItems:   5,
	PackNesting:    1,

	FoldArrayItems: 10,
	FoldObjItems:   5,
	FoldNesting:    2,

	GridArrayItems: maxArrayItems,
	GridObjItems:   maxObjItems,
	GridMinLines:   3,
	GridMaxLines:   100,
	GridArrayMin:   3,
	GridObjMin:     3,

	JoinArrayItems: 8,
	JoinObjItems:   4,
	JoinNesting:    1,
}

var noneConfig = Config{
	Width: defaultWidth,
}

var presets = createPresets()

func createPresets() map[string]Config {

	base := defaultConfig
	none := noneConfig

	packMax := func(c *Config) {
		c.PackArrayItems = maxArrayItems
		c.PackObjItems = maxObjItems
		c.PackNesting = maxNesting
	}
	foldMax := func(c *Config) {
		c.FoldArrayItems = maxArrayItems
		c.FoldObjItems = maxObjItems
		c.FoldNesting = maxNesting
	}
	joinMax := func(c *Config) {
		c.JoinArrayItems = maxArrayItems
		c.JoinObjItems = maxObjItems
		c.JoinNesting = maxNesting
	}
	gridMax := func(c *Config) {
		c.GridArrayItems = maxArrayItems
		c.GridObjItems = maxObjItems
		c.GridMinLines = 3
		c.GridMaxLines = maxGridLines
	}

	p := make(map[string]Config)

	p[""] = base
	p["default"] = base
	p["off"] = Config{}
	p["none"] = none

	{
		c := base
		c.FoldNesting = 0
		c.JoinNesting = 0
		c.GridMaxLines = 0
		p["low"] = c
	}
	{
		c := base
		c.JoinNesting = 0
		c.GridMaxLines = 0
		p["med"] = c
	}
	{
		c := base
		c.GridMaxLines = 0
		p["classic"] = c
	}
	{
		c := base
		c.PackArrayItems = 20
		c.PackObjItems = 10
		c.PackNesting = 4
		c.FoldArrayItems = 20
		c.FoldObjItems = 10
		c.FoldNesting = 4
		c.GridArrayMin = 4
		c.GridObjMin = 4
		c.JoinArrayItems = 16
		c.JoinObjItems = 8
		c.JoinNesting = 2
		p["high"] = c
	}
	{
		c := base
		c.Width = maxWidth
		packMax(&c)
		foldMax(&c)
		joinMax(&c)
		gridMax(&c)
		c.GridArrayMin = 4
		c.GridObjMin = 4
		p["max"] = c
	}
	{
		c := none
		packMax(&c)
		p["pack"] = c
	}
	{
		c := none
		foldMax(&c)
		p["fold"] = c
	}
	{
		c := none
		packMax(&c)
		foldMax(&c)
		gridMax(&c)
		p["grid"] = c
	}
	{
		c := none
		foldMax(&c)
		c.JoinArrayItems = maxArrayItems
		c.JoinObjItems = maxObjItems
		c.JoinNesting = maxNesting
		p["join"] = c
	}
	return p
}

func findPreset(name string) (Config, bool) {
	cfg, ok := presets[name]
	return cfg, ok
}
