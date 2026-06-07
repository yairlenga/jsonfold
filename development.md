format_json(obj, cfg=None, **json_options) -> str
write_json(obj, fp, cfg=None, **json_options) -> None
filter_stream(fp, cfg=None, close_underlying=False) -> stream
preset(name="default", width=None, **overrides) -> cfg

I prefer close_underlying over allow_close; it says exactly what happens.

I would define it as:

Native-compatible API:
  dump / dumps / stringify / Jackson helpers / etc.

Generic API:
  format_json
  write_json
  filter_stream
  preset
  JSONFold config object