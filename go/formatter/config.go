package jsonfold

import "fmt"

const (
	DefaultWidth  = 100
	MaxArrayItems = 1000
	MaxObjItems   = 1000
	MaxNesting    = 10
	MaxGridLines  = 1000
	MaxWidth      = 255
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

func DefaultConfig() Config {
	return Config{
		Width: DefaultWidth,

		PackArrayItems: 10,
		PackObjItems:   5,
		PackNesting:    1,

		FoldArrayItems: 10,
		FoldObjItems:   5,
		FoldNesting:    2,

		GridArrayItems: MaxArrayItems,
		GridObjItems:   MaxObjItems,
		GridMinLines:   3,
		GridMaxLines:   100,
		GridArrayMin:   3,
		GridObjMin:     3,

		JoinArrayItems: 8,
		JoinObjItems:   4,
		JoinNesting:    1,
	}
}

func NoneConfig() Config {
	return Config{Width: DefaultWidth}
}

func presetConfig(name string) (Config, bool, error) {
	base := DefaultConfig()
	none := NoneConfig()

	packMax := func(c Config) Config {
		c.PackArrayItems = MaxArrayItems
		c.PackObjItems = MaxObjItems
		c.PackNesting = MaxNesting
		return c
	}
	foldMax := func(c Config) Config {
		c.FoldArrayItems = MaxArrayItems
		c.FoldObjItems = MaxObjItems
		c.FoldNesting = MaxNesting
		return c
	}
	joinMax := func(c Config) Config {
		c.JoinArrayItems = MaxArrayItems
		c.JoinObjItems = MaxObjItems
		c.JoinNesting = MaxNesting
		return c
	}
	gridMax := func(c Config) Config {
		c.GridArrayItems = MaxArrayItems
		c.GridObjItems = MaxObjItems
		c.GridMinLines = 3
		c.GridMaxLines = MaxGridLines
		return c
	}

	switch name {
	case "", "default":
		return base, true, nil
	case "off":
		return Config{}, false, nil
	case "none":
		return none, true, nil
	case "low":
		c := base
		c.FoldNesting = 0
		c.JoinNesting = 0
		c.GridMaxLines = 0
		return c, true, nil
	case "med":
		c := base
		c.JoinNesting = 0
		c.GridMaxLines = 0
		return c, true, nil
	case "classic":
		c := base
		c.GridMaxLines = 0
		return c, true, nil
	case "high":
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
		return c, true, nil
	case "max":
		c := base
		c.Width = MaxWidth
		c = packMax(c)
		c = foldMax(c)
		c = joinMax(c)
		c = gridMax(c)
		c.GridArrayMin = 4
		c.GridObjMin = 4
		return c, true, nil
	case "pack":
		return packMax(none), true, nil
	case "fold":
		return foldMax(none), true, nil
	case "grid":
		c := none
		c = packMax(c)
		c = foldMax(c)
		c = gridMax(c)
		return c, true, nil
	case "join":
		c := none
		c = foldMax(c)
		c.JoinArrayItems = MaxArrayItems
		c.JoinObjItems = MaxObjItems
		c.JoinNesting = MaxNesting
		return c, true, nil
	default:
		return Config{}, false, fmt.Errorf("unknown JSONFold preset: %s", name)
	}
}

func ConfigWithWidth(config Config, width int) Config {
	if width > 0 {
		config.Width = width
	}
	return config
}

func PresetConfigWithWidth(name string, width int) (Config, bool, error) {
	cfg, enabled, err := presetConfig(name)
	if err != nil || !enabled {
		return cfg, enabled, err
	}
	return ConfigWithWidth(cfg, width), true, nil
}
