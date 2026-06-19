
# Generic API


## Object Oriented API
- formatter = new (width, config, overrides) -> Formatter
- formatter->write(data, stream) -> stats
- formatter->format(data) -> text
- formatter->fold(text) -> text

## Convenience API

- format_json(obj, width, cfg=None, indent, **json_options) -> str
- write_json(obj, fp, cfg=None, **json_options) -> stats
- create_writer(fp, width, cfg=None, close_fp=False) -> stream
- jsonfold_config(cfg="default", width=None, **overrides) -> cfg
- fold_text(text, $width, $config) => $text
- jsonfold_config(base, width, overrides) -> config

# Implementation Specific

## Python API

### Compatibility:
- dump(..., compact="default", width=80)
- dumps(..., compact="default", width=80)

## Perl

### compatibility

- encode_json(data, [options] )

  Options may include compact => name, width => width

to_json(data, [options] )

  Options may include compact => name, width => width

encode (self, data):

  Same as $json->encode

