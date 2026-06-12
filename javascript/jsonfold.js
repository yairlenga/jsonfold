// jsonfold.js
// Hybrid pretty/compact JSON output for JavaScript.

export const Kind = Object.freeze({
  NONE: 0,
  DICT: 1,
  LIST: 2,
});

const CLOSING_KIND = Object.freeze({
  "}": Kind.DICT,
  "},": Kind.DICT,
  "]": Kind.LIST,
  "],": Kind.LIST,
});

export const MAX_ARRAY_ITEMS = 1000;
export const MAX_OBJ_ITEMS = 1000;
export const MAX_NESTING = 10;
export const DEFAULT_WIDTH = 100;


/**
 * Configuration for JSON folding.
 *
 * Packing, folding and joining are controlled by
 * item-count, nesting and width limits.
 */

export class JSONFoldConfig {
  constructor({
    width = DEFAULT_WIDTH,
    packArrayItems = 8,
    packObjItems = 4,
    packNesting = 1,
    foldArrayItems = 8,
    foldObjItems = 4,
    foldNesting = 1,
    joinArrayItems = 8,
    joinObjItems = 4,
    joinNesting = 1,
  } = {}) {
    this.width = width;

    this.packArrayItems = packArrayItems;
    this.packObjItems = packObjItems;
    this.packNesting = packNesting;

    this.foldArrayItems = foldArrayItems;
    this.foldObjItems = foldObjItems;
    this.foldNesting = foldNesting;

    this.joinArrayItems = joinArrayItems;
    this.joinObjItems = joinObjItems;
    this.joinNesting = joinNesting;

    Object.freeze(this);
  }

  replace(overrides = {}) {
    return new JSONFoldConfig({ ...this, ...overrides });
  }

  static preset(name = "") {
    if (!Object.prototype.hasOwnProperty.call(JSONFoldConfig.PRESETS, name)) {
      throw new Error(`unknown JSONFold preset: ${name}`);
    }
    return JSONFoldConfig.PRESETS[name];
  }
}

JSONFoldConfig.NONE = new JSONFoldConfig({
  packArrayItems: 0,
  packObjItems: 0,
  packNesting: 0,
  foldArrayItems: 0,
  foldObjItems: 0,
  foldNesting: 0,
  joinArrayItems: 0,
  joinObjItems: 0,
  joinNesting: 0,
});

JSONFoldConfig.DEFAULT = new JSONFoldConfig();

JSONFoldConfig.PRESETS = Object.freeze({
  off: null,
  "": JSONFoldConfig.DEFAULT,
  default: JSONFoldConfig.DEFAULT,
  none: JSONFoldConfig.NONE,

  low: JSONFoldConfig.DEFAULT.replace({
    foldNesting: 0,
    joinNesting: 0,
  }),

  med: JSONFoldConfig.DEFAULT.replace({
    joinNesting: 0,
  }),

  high: JSONFoldConfig.DEFAULT.replace({
    packArrayItems: 16,
    packObjItems: 8,
    packNesting: 4,
    foldArrayItems: 16,
    foldObjItems: 8,
    foldNesting: 4,
    joinArrayItems: 16,
    joinObjItems: 8,
    joinNesting: 2,
  }),

  max: JSONFoldConfig.NONE.replace({
    width: 255,
    packArrayItems: MAX_ARRAY_ITEMS,
    packObjItems: MAX_OBJ_ITEMS,
    packNesting: MAX_NESTING,
    foldArrayItems: MAX_ARRAY_ITEMS,
    foldObjItems: MAX_OBJ_ITEMS,
    foldNesting: MAX_NESTING,
    joinArrayItems: MAX_ARRAY_ITEMS,
    joinObjItems: MAX_OBJ_ITEMS,
    joinNesting: MAX_NESTING,
  }),

  pack: JSONFoldConfig.NONE.replace({
    packArrayItems: MAX_ARRAY_ITEMS,
    packObjItems: MAX_OBJ_ITEMS,
    packNesting: MAX_NESTING,
  }),

  fold: JSONFoldConfig.NONE.replace({
    foldArrayItems: MAX_ARRAY_ITEMS,
    foldObjItems: MAX_OBJ_ITEMS,
    foldNesting: MAX_NESTING,
  }),

  join: JSONFoldConfig.NONE.replace({
    foldArrayItems: MAX_ARRAY_ITEMS,
    foldObjItems: MAX_OBJ_ITEMS,
    foldNesting: MAX_NESTING,
    joinArrayItems: MAX_ARRAY_ITEMS,
    joinObjItems: MAX_OBJ_ITEMS,
    joinNesting: MAX_NESTING,
  }),
});

export class Line {
  constructor({
    indent = 0,
    text = "",
    parentKind = Kind.NONE,
    items = 1,
    leafs = 1,
    childNesting = -1,
    opener = Kind.NONE,
    closer = Kind.NONE,
    canJoin = true,
    canPack = true,
  } = {}) {
    this.indent = indent;
    this.text = text;
    this.parentKind = parentKind;
    this.items = items;
    this.leafs = leafs;
    this.childNesting = childNesting;
    this.opener = opener;
    this.closer = closer;
    this.canJoin = canJoin;
    this.canPack = canPack;
  }

  static parse(s, parentKind = Kind.NONE) {
    const stripped = s.trimStart();
    const body = stripped.trimEnd();
    const opener =
      body.endsWith("{") ? Kind.DICT :
      body.endsWith("[") ? Kind.LIST :
      Kind.NONE;

    const closer = CLOSING_KIND[body] ?? Kind.NONE;
    const isBodyLine =
      parentKind !== Kind.NONE &&
      opener === Kind.NONE &&
      closer === Kind.NONE;

    return new Line({
      indent: s.length - stripped.length,
      text: body,
      parentKind,
      opener,
      closer,
      canJoin: isBodyLine,
      canPack: isBodyLine,
    });
  }

  raw() {
    return " ".repeat(this.indent) + this.text + "\n";
  }

  width() {
    return this.indent + this.text.length;
  }

  joinLine(other) {
    this.text += " " + other.text;
    this.items += other.items;
    this.leafs += other.leafs;

    if (other.childNesting > this.childNesting) {
      this.childNesting = other.childNesting;
      this.canPack = false;
    }
  }
}

export class Frame {
  constructor({
    kind,
    depth,
    lines = [],
    packLimit = 0,
    foldLimit = 0,
    joinLimit = 0,
  }) {
    this.kind = kind;
    this.depth = depth;
    this.lines = lines;

    this.packLimit = packLimit;
    this.foldLimit = foldLimit;
    this.joinLimit = joinLimit;

    this.contentLines = 0;
    this.items = 0;
    this.leafs = 0;

    this.foldOk = true;
    this.childNesting = -1;
  }
}



/**
 * Runtime statistics collected by a JSONFoldFilter.
 *
 * Values are accumulated as data flows through the formatter.
 *
 * @property {number} bytesIn   Number of input characters received.
 * @property {number} bytesOut  Number of output characters written.
 * @property {number} linesIn   Number of input lines processed.
 * @property {number} linesOut  Number of output lines written.
 */
export class JSONFoldStats {
  constructor() {
    /** @type {number} Input character count. */
    this.bytesIn = 0;

    /** @type {number} Output character count. */
    this.bytesOut = 0;

    /** @type {number} Input line count. */
    this.linesIn = 0;

    /** @type {number} Output line count. */
    this.linesOut = 0;
  }
}

function asConfig(compact = "") {
  if (compact === null || compact === false) return null;
  if (compact instanceof JSONFoldConfig) return compact;
  if (typeof compact === "string") return JSONFoldConfig.preset(compact);
  if (compact && typeof compact === "object") return new JSONFoldConfig(compact);
  return JSONFoldConfig.DEFAULT;
}

function writeAny(writer, s) {
  if (writer == null) return s.length;

  if (typeof writer === "function") {
    writer(s);
    return s.length;
  }

  if (typeof writer.write === "function") {
    const ret = writer.write(s);
    return typeof ret === "number" ? ret : s.length;
  }

  throw new TypeError("writer must be a function or an object with write(s)");
}

function countNewlines(s) {
  let n = 0;
  for (let i = 0; i < s.length; i++) {
    if (s.charCodeAt(i) === 10) n++;
  }
  return n;
}

function sortObjectKeys(value) {
  if (Array.isArray(value)) {
    return value.map(sortObjectKeys);
  }

  if (
    value &&
    typeof value === "object" &&
    Object.getPrototypeOf(value) === Object.prototype
  ) {
    const out = {};

    for (const key of Object.keys(value).sort()) {
      out[key] = sortObjectKeys(value[key]);
    }

    return out;
  }

  return value;
}

function sortedReplacer(userReplacer) {
  return function replacer(key, value) {
    if (userReplacer) {
      value = userReplacer.call(this, key, value);
    }

    if (
      value &&
      typeof value === "object" &&
      !Array.isArray(value) &&
      Object.getPrototypeOf(value) === Object.prototype
    ) {
      const out = {};
      for (const k of Object.keys(value).sort()) {
        out[k] = value[k];
      }
      return out;
    }

    return value;
  };
}

export class JSONFoldFilter {
  constructor(fp, { config = "" } = {}) {
    this.fp = fp;
    this.stats = new JSONFoldStats();
    this.cfg = asConfig(config);
    this.pending = "";
    this.stack = [];
  }

  write(s) {
    s = String(s);
    const sLen = s.length;
    this.stats.bytesIn += sLen;

    const nlPos = s.indexOf("\n");

    if (!this.cfg) {
      if (nlPos >= 0) this.stats.linesIn += countNewlines(s);
      return this._writeStr(s);
    }

    if (nlPos < 0) {
      this.pending += s;
      return sLen;
    }

    const nl2Pos = s.indexOf("\n", nlPos + 1);

    if (nl2Pos < 0) {
      this.stats.linesIn += 1;

      const s2 = this.pending + s.slice(0, nlPos);
      this.pending = s.slice(nlPos + 1);

      this._feed(Line.parse(s2, this._parentKind()));

      if (this.pending.length > this.cfg.width) {
        this._markNoFold();
      }

      return sLen;
    }

    const parts = s.split(/(?<=\n)/);
    this.stats.linesIn += countNewlines(s);

    if (this.pending) {
      parts[0] = this.pending + parts[0];
      this.pending = "";
    }

    if (parts.length && !parts[parts.length - 1].endsWith("\n")) {
      this.pending = parts.pop();
    }

    for (const part of parts) {
      this._feed(Line.parse(part.slice(0, -1), this._parentKind()));
    }

    return sLen;
  }

  flush() {
    this.finish();
    if (this.fp && typeof this.fp.flush === "function") this.fp.flush();
  }

  close() {
    this.finish();
    if ( this.fp && typeof this.fp.flush === "function") this.fp.flush();
    if ( this.close_fp ) this.fp.close()
  }

  finish() {
    if (this.pending) {
      this._feed(Line.parse(this.pending, this._parentKind()));
      this.pending = "";
    }

    for (const frame of this.stack) {
      for (const line of frame.lines) this._writeLine(line);
    }

    this.stack.length = 0;
  }

  _writeStr(s) {
    const n = writeAny(this.fp, s);
    this.stats.bytesOut += n;
    this.stats.linesOut += countNewlines(s);
    return n;
  }

  _writeLine(line) {
    this._writeStr(line.raw());
  }

  _feed(line) {
    const opener = line.opener;

    if (opener !== Kind.NONE) {
      this.stack.push(new Frame({
        kind: opener,
        depth: this.stack.length,
        lines: [line],
        packLimit: this._packLimit(opener),
        foldLimit: this._foldLimit(opener),
        joinLimit: this._joinLimit(opener),
      }));

      if (line.width() > this.cfg.width) {
        this._markNoFold();
      }

      return;
    }

    const closer = line.closer;

    if (closer !== Kind.NONE) {
      this._closeFrame(line, closer);
      return;
    }

    if (this.stack.length) {
      const frame = this.stack[this.stack.length - 1];

      if (line.items >= frame.packLimit) line.canPack = false;
      if (line.items >= frame.joinLimit) line.canJoin = false;

      this._addToFrame(frame, line);
    } else {
      this._writeLine(line);
    }
  }

  _emitLines(lines, depth = this.stack.length - 1) {
    if (!lines.length) return;

    if (depth < 0) {
      for (const line of lines) this._writeLine(line);
      return;
    }

    const frame = this.stack[depth];
    for (const line of lines) this._addToFrame(frame, line);
  }

  _chooseLimit(kind, { defaultValue = 0, listLimit = 0, dictLimit = 0 } = {}) {
    if (kind === Kind.LIST) return listLimit;
    if (kind === Kind.DICT) return dictLimit;
    return defaultValue;
  }

  _packLimit(kind) {
    return this._chooseLimit(kind, {
      listLimit: this.cfg.packArrayItems,
      dictLimit: this.cfg.packObjItems,
    });
  }

  _foldLimit(kind) {
    return this._chooseLimit(kind, {
      listLimit: this.cfg.foldArrayItems,
      dictLimit: this.cfg.foldObjItems,
    });
  }

  _joinLimit(kind) {
    return this._chooseLimit(kind, {
      listLimit: this.cfg.joinArrayItems,
      dictLimit: this.cfg.joinObjItems,
    });
  }

  _addToFrame(frame, line) {
    if (frame.lines.length) {
      if (line.canPack && this._tryPack(frame, line)) return;
      if (line.canJoin && this._tryJoin(frame, line)) return;
    }

    frame.lines.push(line);

    if (line.closer === Kind.NONE) {
      frame.contentLines += 1;
      frame.items += line.items;
      frame.leafs += line.leafs;

      if (line.childNesting >= frame.childNesting) {
        frame.childNesting = line.childNesting + 1;
      }

      if (frame.foldOk) this._checkFoldLimits(frame);
    }

    if (frame.foldOk && line.width() > this.cfg.width) {
      this._markNoFold();
    }

    if (!frame.foldOk) {
      this._streamFrame(frame);
    }
  }

  _canMerge(prev, line, limit) {
    return (
      prev.indent === line.indent &&
      prev.items + line.items <= limit &&
      prev.indent + prev.text.length + 1 + line.text.length <= this.cfg.width
    );
  }

  _mergeIntoFrame(frame, prev, line) {
    prev.joinLine(line);

    frame.items += line.items;
    frame.leafs += line.leafs;

    if (prev.items >= frame.packLimit) prev.canPack = false;
    if (prev.items >= frame.joinLimit) prev.canJoin = false;

    if (frame.foldOk) this._checkFoldLimits(frame);
  }

  _tryPack(frame, line) {
    if (
      frame.packLimit <= 1 ||
      !frame.lines.length ||
      !line.canPack
    ) {
      return false;
    }

    const prev = frame.lines[frame.lines.length - 1];

    if (!(prev.canPack && this._canMerge(prev, line, frame.packLimit))) {
      return false;
    }

    this._mergeIntoFrame(frame, prev, line);
    if ( !prev.canPack) prev.canJoin = false
    return true;
  }

  _tryJoin(frame, line) {
    if (
      frame.joinLimit <= 1 ||
      !frame.lines.length ||
      !line.canJoin ||
      line.childNesting >= this.cfg.joinNesting
    ) {
      return false;
    }

    const prev = frame.lines[frame.lines.length - 1];

    if (
      !(
        prev.canJoin &&
        prev.childNesting < this.cfg.joinNesting &&
        this._canMerge(prev, line, frame.joinLimit)
      )
    ) {
      return false;
    }

    this._mergeIntoFrame(frame, prev, line);
    return true;
  }

  _checkFoldLimits(frame) {
    if (!frame.foldOk) return;

    if (frame.contentLines > 1) {
      frame.foldOk = false;
      return;
    }

    if (frame.items > frame.foldLimit) {
      frame.foldOk = false;
      return;
    }

    if (frame.childNesting >= this.cfg.foldNesting) {
      frame.foldOk = false;
    }
  }

  _closeFrame(closer, closingKind) {
    if (!this.stack.length) {
      this._writeLine(closer);
      return;
    }

    const frame = this.stack.pop();
    frame.lines.push(closer);

    if (frame.kind !== closingKind) {
      frame.foldOk = false;
    }

    const folded = this._tryFold(frame);
    if (folded !== null) frame.lines = [folded];

    this._emitLines(frame.lines);
    frame.lines.length = 0;
  }

  _tryFold(frame) {
    if (
      !frame.foldOk ||
      frame.contentLines !== 1 ||
      frame.lines.length !== 3
    ) {
      return null;
    }

    const foldedLength =
      frame.lines.reduce((sum, line) => sum + 1 + line.text.length, 0) - 1;

    if (frame.lines[0].indent + foldedLength > this.cfg.width) {
      return null;
    }

    return new Line({
      indent: frame.lines[0].indent,
      text: frame.lines.map(line => line.text).join(" "),
      parentKind: this._parentKind(),
      items: 1,
      leafs: frame.leafs,
      childNesting: Math.max(0, frame.childNesting),
      canPack: false,
      canJoin: true,
    });
  }

  _streamFrame(frame) {
    const lines = frame.lines;
    let keep = null;

    if (lines.length) {
      const last = lines[lines.length - 1];
      if (last.canPack || last.canJoin) {
        keep = lines.pop();
      }
    }

    this._emitLines(lines, frame.depth - 1);
    lines.length = 0;

    if (keep) {
      lines.push(keep);
    }
  }

  _markNoFold() {
    for (const frame of this.stack) {
      frame.foldOk = false;
    }

    if (this.stack.length) {
      this._streamFrame(this.stack[this.stack.length - 1]);
    }
  }

  _parentKind() {
    return this.stack.length ? this.stack[this.stack.length - 1].kind : Kind.NONE;
  }
}

function _config(base_config, width, overrides) {
  let cfg = asConfig(base_config)
  if ( overrides || width != null) {
    if (! overrides ) overrides = {}
    if ( width != null ) overrides.width = width
    if ( Object.keys(overrides).length ) {
      cfg = cfg.replace(overrides)
    }
  }
  return cfg
}

function _stream_text(text, fp, cfg)
{
  const out = new JSONFoldFilter(fp, { config: cfg })

  out.write(text);
  if ( ! text.endsWith("\n")) out.write("\n")
  out.finish();
  return out.stats
}

export function filter_stream(fp, { config= "", close_fp= false})
{
  return new JSONFoldFilter( fp , { config: config, close_fp: close_fp})
}

export function write_json(obj, fp, width, config, {
  indent = 2,
  sortKeys = false,
  replacer = undefined,
} = {} ) {
  const cfg = _config(config, width)
  if ( sortKeys ) replacer = sortedReplacer(replacer)

  const text = JSON.stringify(obj, replacer, indent);
  if ( typeof text != "string" ) return false

  let stats = _stream_text(text, fp, cfg)
  return stats
}

export function format_json(obj, width, config, {
  indent = 2,
  sortKeys = false,
  replacer = undefined,
} = {} ) {
  const cfg = _config(config, width)
  if ( sortKeys ) replacer = sortedReplacer(replacer)

  const text = JSON.stringify(obj, replacer, indent)
  if ( typeof text != "string" ) return text

  let buff = ""
  const fp = s => { buff += s ; }
  let stats = _stream_text(text, fp, width, cfg)
  return buff
}

export function config(config, width, overrides)
{
  return _config(config, width, overrides)
}

export function dump(obj, fp, {
  compact = "",
  width = undefined,
  indent = 2,
  replacer = undefined,
  sortKeys = false,
} = {}) {
  const cfg = _config(compact, width)
  if ( sortKeys ) replacer = sortedReplacer(replacer)

  const text = JSON.stringify(obj, replacer, indent);

  if ( typeof text != "string" ) return

  _stream_text(text, fp, cfg)
}

export function dumps(obj, {
  compact = "",
  width = undefined,
  indent = 2,
  replacer = undefined,
  sortKeys = false,
} = {}) {
  const cfg = _config(compact, width)
  if ( sortKeys ) replacer = sortedReplacer(replacer)


  const text = JSON.stringify(obj, replacer, indent)
  if ( typeof text != "string" ) return text

  let buff = ""
  const fp = s => { buff += s ; }
  let stats = _stream_text(text, fp, width, cfg)
  return buff
}

export function stringify(obj, replacer = null, space = null) {
  let text = "";

  let options = {}
  if ( space && typeof space === "object" && !Array.isArray(space)) {
    options = space
  } else {
    options.indent = space ?? 2 ;
  }

  if ( replacer != null ) options.replacer = replacer
  dump(obj, s => {
    text += s;
  }, options);

  return text;
}

export default {
  JSONFoldConfig,
  JSONFoldFilter,
  JSONFoldStats,
  format_json,
  write_json,
  filter_stream,
  config,
  stringify,
  dump,
  dumps,
};