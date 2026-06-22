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
export const MAX_GRID_LINES = 1000;
export const DEFAULT_WIDTH = 100;
export const MAX_WIDTH = 255 ;

/**
 * Configuration for JSON folding.
 *
 * Packing, folding and joining are controlled by
 * item-count, nesting and width limits.
 */

export class JSONFoldConfig {
  constructor({
    width = DEFAULT_WIDTH,
    packArrayItems = 10,
    packObjItems = 5,
    packNesting = 1,
    foldArrayItems = 10,
    foldObjItems = 5,
    foldNesting = 1,
    gridArrayItems = MAX_ARRAY_ITEMS,
    gridObjItems = MAX_OBJ_ITEMS,
    gridMinLines = 3,
    gridMaxLines = 100,
    gridArrayMin = 3,
    gridObjMin = 3,
    joinArrayItems = 8,
    joinObjItems = 4,
    joinNesting = 1,
  } = {}) {
    this.width = width;

    this.packArrayItems = packArrayItems
    this.packObjItems = packObjItems
    this.packNesting = packNesting

    this.foldArrayItems = foldArrayItems
    this.foldObjItems = foldObjItems
    this.foldNesting = foldNesting

    this.gridArrayItems = gridArrayItems
    this.gridObjItems = gridObjItems
    this.gridMinLines = gridMinLines
    this.gridMaxLines = gridMaxLines
    this.gridObjMin = gridObjMin
    this.gridArrayMin = gridArrayMin

    this.joinArrayItems = joinArrayItems;
    this.joinObjItems = joinObjItems;
    this.joinNesting = joinNesting;

    Object.freeze(this);
  }

  replace(overrides = {}) {
    return new JSONFoldConfig({ ...this, ...overrides });
  }

  static preset(name = "") {
    if (!Object.hasOwn(this.PRESETS, name)) {
      throw new Error(`unknown JSONFold preset: ${name}`);
    }
    return JSONFoldConfig.PRESETS[name];
  }

  static resolve(base_config, width, overrides) {
    let cfg = asConfig(base_config)
    if ( overrides || width != null) {
      overrides = overrides ? {...overrides } : {}
      if ( width != null ) overrides.width = width
      if ( Object.keys(overrides).length ) {
        cfg = cfg.replace(overrides)
      }
    }
    return cfg
  }

  static setup() {
    const base_cfg = new this()
    const none_cfg = new this( {
      packArrayItems: 0,
      packObjItems: 0,
      packNesting: 0,
      foldArrayItems: 0,
      foldObjItems: 0,
      foldNesting: 0,
      gridArrayItems: 0,
      gridObjItems: 0,
      gridMinLines: 0,
      gridMaxLines: 0,
      joinArrayItems: 0,
      joinObjItems: 0,
      joinNesting: 0,
    })

    const packMax = {
      "packArrayItems": MAX_ARRAY_ITEMS,
      "packObjItems": MAX_OBJ_ITEMS,
      "packNesting": MAX_NESTING,
    }

    const foldMax = {
      "foldArrayItems": MAX_ARRAY_ITEMS,
      "foldObjItems": MAX_OBJ_ITEMS,
      "foldNesting": MAX_NESTING,
    }

    const joinMax = {
      "joinArrayItems": MAX_ARRAY_ITEMS,
      "joinObjItems": MAX_OBJ_ITEMS,
      "joinNesting": MAX_NESTING,
    }

    const gridMax = {
      "gridArrayItems": MAX_ARRAY_ITEMS,
      "gridObjItems": MAX_OBJ_ITEMS,
      "gridMinLines": 3,
      "gridMaxLines": MAX_GRID_LINES,
    }

    this.DEFAULT = base_cfg
    this.NONE = none_cfg

    this.PRESETS = Object.freeze({
      off: null,

      default: base_cfg,
      "": base_cfg,

      none: none_cfg,

      low: base_cfg.replace({
        foldNesting: 0,
        joinNesting: 0,
        gridMaxLines: 0,
      }),

      med: base_cfg.replace({
        joinNesting: 0,
        gridMaxLines: 0,
      }),

      classic: base_cfg.replace({gridMaxLines: 0}),

      high: base_cfg.replace({
        packArrayItems: 20,
        packObjItems: 10,
        packNesting: 4,
        foldArrayItems: 20,
        foldObjItems: 10,
        foldNesting: 4,

        gridArrayMin: 4,
        gridObjMin: 4,

        joinArrayItems: 16,
        joinObjItems: 8,
        joinNesting: 2,
      }),

      max: none_cfg.replace({width: MAX_WIDTH, ...packMax, ...foldMax, ...joinMax, gridArrayMin:4, gridObjMin:4}),

      pack: none_cfg.replace({ ...packMax}),
      fold: none_cfg.replace({ ...foldMax}),
      grid: none_cfg.replace({ ...packMax, ...foldMax, ...gridMax}),
      join: none_cfg.replace({ ...foldMax, 
        joinArrayItems: MAX_ARRAY_ITEMS,
        joinObjItems: MAX_OBJ_ITEMS,
        joinNesting: MAX_NESTING,
      })
    });
  }
}
JSONFoldConfig.setup()


const KEY_RE = /^\s*(?:"[^"\\]*"|'[^'\\]*'|[A-Za-z_$][A-Za-z0-9_$]*|)\s*:/;

export class Line {
  constructor({
    indent = 0,
    parts = null,
    length = null,
    kind = Kind.NONE,
    parentKind = Kind.NONE,
    items = 1,
    leafs = 1,
    childNesting = -1,
    opener = Kind.NONE,
    closer = Kind.NONE,
    canJoin = true,
    canPack = true,
    canGrid = false,
  } = {}) {
    this.indent = indent;
    this.parts = parts ?? [];
    this.length = length ?? Line.partsLength(this.parts);
    this.kind = kind;
    this.parentKind = parentKind;
    this.items = items;
    this.leafs = leafs;
    this.childNesting = childNesting;
    this.opener = opener;
    this.closer = closer;
    this.canJoin = canJoin;
    this.canPack = canPack;
    this.canGrid = canGrid;
    Object.seal(this)
  }

  static partsLength(parts) {
    if (!parts.length) return 0;
    return parts.reduce((sum, part) => sum + part.length + 1, 0) - 1;
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
      parts: [body],
      length: body.length,
      parentKind,
      opener,
      closer,
      canJoin: isBodyLine,
      canPack: isBodyLine,
    });
  }

  raw() {
    return " ".repeat(this.indent) + this.parts.join(" ") + "\n";
  }

  width() {
    return this.indent + this.length;
  }

  joinLine(other) {
    if ( !other.parts.length ) return
    this.parts.push(...other.parts);
    this.length += 1 + other.length;
    this.items += other.items;
    this.leafs += other.leafs;

    if (other.childNesting > this.childNesting) {
      this.childNesting = other.childNesting;
      this.canPack = false;
    }
  }

  setParts(parts) {
    this.parts = parts;
    this.length = Line.partsLength(parts);
  }

  dictSignature() {
    const signature = [];

    for (const part of this.parts.slice(1, -1)) {
      const m = KEY_RE.exec(part);
      if (!m) return null;
      signature.push(m[0]);
    }

    return signature.join("\u0000");
  }
}

export class Frame {
  constructor({
    kind,
    depth,
    lines = [],
    length = 0,
    packLimit = 0,
    foldLimit = 0,
    joinLimit = 0,
    gridLimit = 0,
    gridMinItems = 0,

  }) {
    this.kind = kind;
    this.depth = depth
    this.lines = lines
    this.length = length
    this.packLimit = packLimit
    this.foldLimit = foldLimit
    this.joinLimit = joinLimit
    this.gridLimit = gridLimit
    this.gridMinItems = gridMinItems

    this.contentLines = 0
    this.items = 0
    this.leafs = 0

    this.foldOk = true
    this.gridOk = false
    this.childNesting = -1
    Object.seal(this)
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
    Object.seal(this)
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
  let pos = -1;

  while ((pos = s.indexOf("\n", pos + 1)) !== -1) {
    n++;
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
  constructor(fp, { config = "", doClose = false } = {}) {
    this.fp = fp;
    this.stats = new JSONFoldStats();
    this.cfg = asConfig(config);
    this.doClose = doClose
    this.pending = "";
    this.stack = [];
    Object.seal(this)
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

    const parts = s.split("\n")
    if ( this.pending ) {
      parts[0] = this.pending + parts[0]
      this.pending = ""
    }
    if ( ! s.endsWith("\n")) {
      this.pending = parts.pop()
    }

    this.stats.linesIn += parts.length
    for (const part of parts) {
      this._feed(Line.parse(part, this._parentKind()));
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
    if ( this.doClose ) this.fp.close()
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
        length: line.length,
        packLimit: this._packLimit(opener),
        foldLimit: this._foldLimit(opener),
        joinLimit: this._joinLimit(opener),
        gridLimit: this._gridLimit(opener),
        gridMinItems: this._gridMinItems(opener),
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
    for (const line of lines) this._addToFrame(frame, line)
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

  _gridLimit(kind) {
    return this._chooseLimit(kind, {
      listLimit: this.cfg.gridArrayItems,
      dictLimit: this.cfg.gridObjItems,
    });
  }

  _gridMinItems(kind) {
    return this._chooseLimit(kind, {
      listLimit: this.cfg.gridArrayMin,
      dictLimit: this.cfg.gridObjMin,
    });

  }

  _addToFrame(frame, line) {

    if (frame.lines.length) {
      if (!frame.gridOk) {
        const prev = frame.lines[frame.lines.length - 1];
        if (line.canPack && prev.canPack && this._tryPack(frame, prev, line)) return;
        if (line.canJoin && prev.canJoin && this._tryJoin(frame, prev, line)) return;
      }

    } else if (!frame.foldOk && !line.canPack && !line.canJoin) {
      this._writeLine(line);
      return;
    }

    frame.lines.push(line);
    frame.length += 1 + line.length

    if (frame.foldOk && line.width() > this.cfg.width) {
      this._markNoFold();
    }

    if (line.childNesting >= frame.childNesting) {
      frame.childNesting = line.childNesting + 1;
    }

    if (line.closer === Kind.NONE) {
      frame.contentLines += 1;
      frame.items += line.items;
      frame.leafs += line.leafs;

      if (frame.foldOk) {
        if (!this._checkFoldLimits(frame)) this._markNoFold();
      }

      if (frame.gridOk) {
        if (!line.canGrid) {
          this._markNoGrid()
          this._joinFrame(frame)
        }
      }
    }

    if (!frame.foldOk && !frame.gridOk) {
      this._streamFrame(frame);
    }
  }

  _canMerge(prev, line, limit) {
    return (
      prev.indent === line.indent &&
      prev.items + line.items <= limit &&
      prev.indent + prev.length + 1 + line.length <= this.cfg.width
    );
  }

  _mergeIntoFrame(frame, prev, line) {
    prev.joinLine(line);

    frame.items += line.items;
    frame.leafs += line.leafs;

    if (prev.items >= frame.packLimit || prev.childNesting >= this.cfg.packNesting) prev.canPack = false;
    if (prev.items >= frame.joinLimit || prev.childNesting >= this.cfg.joinNesting) prev.canJoin = false;

    if (frame.foldOk) {
      if (!this._checkFoldLimits(frame)) {
        this._markNoFold();
        this._streamFrame(frame);
      }
    }
  }

  _tryPack(frame, prev, line) {
    if (
      frame.packLimit <= 1 ||
      !this._canMerge(prev, line, frame.packLimit)
    ) {
      return false;
    }

    this._mergeIntoFrame(frame, prev, line);
    if ( !prev.canPack) prev.canJoin = false
    return true;
  }

  _tryJoin(frame, prev, line) {
    if (
      frame.joinLimit <= 1 ||
      !this._canMerge(prev, line, frame.joinLimit)
    ) {
      return false;
    }

    this._mergeIntoFrame(frame, prev, line);
    return true;
  }

  _joinFrame(frame) {
    const lines = frame.lines;
    const n = lines.length;

    if (n < 2) {
      return;
    }

    let prev = lines[0];
    let writePos = 1;

    for (let readPos = 1; readPos < n; readPos++) {
      const line = lines[readPos];

      if (
        prev.canJoin &&
        line.canJoin &&
        this._canMerge(prev, line, frame.joinLimit)
      ) {
        prev.joinLine(line);
        prev.canPack = false;
      } else {
        if (readPos !== writePos) {
          lines[writePos] = line;
        }
        prev = line;
        writePos++;
      }
    }

    lines.length = writePos;
    frame.contentLines -= (n - writePos);
  }

  _checkFoldLimits(frame) {
    if ( frame.length > this.cfg.width) {
      return false
    }

    if (frame.items > frame.foldLimit) {
      return false;
    }

    if (frame.childNesting >= this.cfg.foldNesting) {
      return false;
    }

    return true;
  }

  _closeFrame(closer, closingKind) {
    if (!this.stack.length) {
      this._writeLine(closer);
      return;
    }

    const frame = this.stack.pop();
    frame.lines.push(closer);

    if (frame.kind !== closingKind) {
      frame.foldOk = false
      frame.gridOk = false
    }

    if (frame.gridOk) {
      if (this._tryGrid(frame)) {
        this._markNoGrid();
      } else {
        this._markNoGrid();
        this._joinFrame(frame)
        frame.foldOk = this._checkFoldLimits(frame)
      }
    }

    if (frame.foldOk) {
      if ( this._tryFold(frame)) {
        if (this.stack.length && frame.lines[0].canGrid) {
          const parentFrame = this.stack[this.stack.length - 1];
          if (parentFrame.contentLines === 0) parentFrame.gridOk = true;
        }
      }
    }

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
      frame.lines.reduce((sum, line) => sum + 1 + line.length, 0) - 1;

    if (frame.lines[0].indent + foldedLength > this.cfg.width) {
      return null;
    }

    const line = new Line({
      indent: frame.lines[0].indent,
      parts: frame.lines.flatMap(line => line.parts),
      kind: frame.kind,
      parentKind: this._parentKind(),
      items: 1,
      leafs: frame.leafs,
      childNesting: Math.max(0, frame.childNesting),
      canPack: false,
      canJoin: frame.childNesting < this.cfg.joinNesting,
      canGrid: this.cfg.gridMaxLines > 0,
    });

    frame.lines = [ line ]
    return true
  }

  static _formatParts(parts, widths) {
    const last = widths.length - 1;
    return parts.map((part, i) => (
      "-0123456789".includes(part.slice(0, 1))
        ? part.padStart(widths[i])
        : i < last
          ? part.padEnd(widths[i])
          : part
    ));
  }

  _tryGrid(frame) {
    if (frame.kind != Kind.LIST ) return false

    const lineCount = frame.lines.length - 2
    if (
      lineCount < 2 ||
      lineCount < this.cfg.gridMinLines ||
      lineCount > this.cfg.gridMaxLines
    ) {
      return false;
    }

    const lines = frame.lines.slice(1, -1)
    const firstLine = lines[0];
    const partCount = firstLine.parts.length;

    if ( partCount< 4 || partCount-2 < frame.gridMinItems)
      return false

    if (lines.some(line => line.parts.length !== partCount)) {
      return false;
    }

    if (firstLine.kind === Kind.DICT) {
      const dictSignature = firstLine.dictSignature();
      if (!dictSignature) return false;
      if (lines.some(line => line.dictSignature() !== dictSignature)) {
        return false;
      }
    }

    const widths = Array.from({ length: partCount }, (_, i) =>
      Math.max(...lines.map(line => line.parts[i].length))
    );

    const gridedLength = widths.reduce((sum, width) => sum + 1 + width, 0) - 1;
    if (frame.lines[0].indent + gridedLength > this.cfg.width) {
      return false;
    }

    for (const line of lines) {
      const newParts = JSONFoldFilter._formatParts(line.parts, widths);
      line.setParts(newParts);
      line.canPack = false;
      line.canJoin = false;
      line.canGrid = false;
    }

    return true;
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
  }

  _markNoGrid() {
    for (const frame of this.stack) {
      frame.gridOk = false;
    }
  }

  _parentKind() {
    return this.stack.length ? this.stack[this.stack.length - 1].kind : Kind.NONE;
  }
}

// Public API - OO

export class JSONFold {

  constructor( width=undefined, config=undefined, {
    doClose = false,
    indent = 2,
    gold = true,
    json = JSON,
    replacer = undefined,
    sortKeys = false,
  } = {} ) 
  {
    if ( sortKeys ) replacer = sortedReplacer(replacer)

    this.width  = width
    this.config = JSONFoldConfig.resolve(config, width)
    this.json = json
    this.doClose = doClose
    this.gold = gold
    this.indent = indent
    this.replacer = replacer
    Object.seal(this)
  }

  format(data)
  {
    const text = this.json.stringify(data, this.replacer, this.indent)
    if ( typeof text != "string" ) return text

    let buff = ""
    const fp = s => { buff += s ; }
    const out = new JSONFoldFilter(fp, { config: this.config })
    out.write(text);
    if ( ! text.endsWith("\n")) out.write("\n")
    out.close()
    return buff  
  }

  write(data, fp)
  {
    const text = this.json.stringify(data, this.replacer, this.indent)
    if ( typeof text != "string" ) return text

    const out = new JSONFoldFilter(fp, { config: this.config })
    out.write(text);
    const stats = out.stats
    out.close()
    return stats
  }

  fold(text)
  {
    let buff = ""
    const fp = s => { buff += s ; }
    const out = new JSONFoldFilter(fp, { config: this.config })
    out.write(text)
    out.close()
    if ( ! text.endsWith("\n")) out.write("\n")
    return buff  
  }

  // Public API - Helpers

}

// Public API - Functional

export function create_writer(fp, { width=undefined, config= "", doClose= false} = {})
{
  const cfg = JSONFoldConfig.resolve(config, width)
  return new JSONFoldFilter( fp , { config: cfg, doClose})
}

export function write_json(data, fp, width, config = "", ...args)
{
  const fmt = new JSONFold(width, config, ...args )
  const stats = fmt.write(data, fp)
  return stats
}

export function format_json(data, width, config = "", ...args)
{
  const fmt = new JSONFold( width, config, ...args)
  return fmt.format(data)
}

export function jsonfold_config(config, width, overrides)
{
  return JSONFoldConfig.resolve(config, width, overrides)
}

export function fold_text(text, width, config = "")
{
  const fmt = new JSONFold(width, config)
  const output = fmt.fold(text)
  return output
}

// Compatability API

export function stringify(data, replacer = null, space = null) {
  let options = {}
  if ( space && typeof space === "object" && !Array.isArray(space)) {
    options = { ... space }
  } else {
    options.indent = space ?? 2
  }

  if ( replacer != null ) options.replacer = replacer

  const text = format_json(data, options.width, options.config, options);

  return text;
}

