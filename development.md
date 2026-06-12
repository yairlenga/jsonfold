# Generic API

## Object Oriented API
- jsonfold_preset(name, overrides) -> config
- jsonfold_create(callback, cxt, width, config) -> jsonfold
- jsonfold_write(jsonfold, string, sz)
- jsonfold_finish(jsonfold)
- jsonfold_get_stats(jsonfold) -> stats
- jsonfold_destroy(jsonfold)
- jsonfold_config_destroy(config)
- jsonfold_stats_destroy(stats)

## Convenience API

- format_json(obj, width, cfg=None, indent, **json_options) -> str
- write_json(obj, fp, cfg=None, **json_options) -> stats
- filter_stream(fp, width, cfg=None, close_fp=False) -> stream
- config(cfg="default", width=None, **overrides) -> cfg

# Implementation Specific

## Python API

### Public
- config(base_config="", **overrides) -> JSONFold
  
  Build a JSONFold configuration from a preset or existing config.

- format_json(obj, width, config="", indent=2, **json_options) -> str
 
  Serialize obj and return folded JSON text.

- write_json(obj, fp, width, config="", indent=2, **json_options) -> JSONFoldStats

  Serialize obj to fp and return formatting statistics.

- filter_stream(fp, width, config="") -> JSONFoldWriter
  
  Wrap a text stream with a JSONFold formatting filter.

### Compatibility:
- dump(..., compact="default", width=80)
- dumps(..., compact="default", width=80)
