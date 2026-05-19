// jsonfold.js
// Hybrid pretty/compact JSON output for JavaScript.
//
// Structure intentionally mirrors the Python version:
//   JSONFold          immutable-style configuration + presets
//   Kind              container kind enum
//   Line              physical pretty-print line with packing metadata
//   Frame             open container frame
//   JSONFoldStats     input/output counters
//   JSONFoldFilter    file-like writer/filter
//   dump              write folded JSON to a writer
//   dumpi             write folded JSON and return stats
//   dumps             return folded JSON as a string
//
// Unlike Python, JavaScript has no built-in incremental JSON encoder like
// json.dump(obj, fp). This file therefore includes a small incremental encoder
// that emits pretty JSON lines into JSONFoldFilter.

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

export class JSONFold {
  constructor({
    width = 80,
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
    return new JSONFold({ ...this, ...overrides });
  }

  static preset(name = "") {
    const cfg = JSONFold.PRESETS[name];
    if (!cfg) throw new Error(`unknown JSONFold preset: ${name}`);
    return cfg;
  }
}

JSONFold.NONE = new JSONFold({
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

JSONFold.DEFAULT = new JSONFold();

JSONFold.PRESETS = Object.freeze({
  "": JSONFold.DEFAULT,
  default: JSONFold.DEFAULT,
  none: JSONFold.NONE,

  low: JSONFold.DEFAULT.replace({
    joinNesting: 0,
  }),

  med: JSONFold.DEFAULT.replace({
    joinNesting: 1,
  }),

  high: JSONFold.DEFAULT.replace({
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

  max: JSONFold.NONE.replace({
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

  pack: JSONFold.NONE.replace({
    packArrayItems: MAX_ARRAY_ITEMS,
    packObjItems: MAX_OBJ_ITEMS,
    packNesting: MAX_NESTING,
  }),

  fold: JSONFold.NONE.replace({
    foldArrayItems: MAX_ARRAY_ITEMS,
    foldObjItems: MAX_OBJ_ITEMS,
    foldNesting: MAX_NESTING,
  }),

  join: JSONFold.NONE.replace({
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
  } = {}) {
    this.indent = indent;
    this.text = text;
    this.parentKind = parentKind;
    this.items = items;
    this.leafs = leafs;
    this.childNesting = childNesting;
    this.opener = opener;
    this.closer = closer;
  }

  static parse(s, parentKind = Kind.NONE) {
    const stripped = s.trimStart();
    const body = stripped.trimEnd();
    const opener = body.endsWith("{") ? Kind.DICT : body.endsWith("[") ? Kind.LIST : Kind.NONE;
    const closer = CLOSING_KIND[body] ?? Kind.NONE;

    return new Line({
      indent: s.length - stripped.length,
      text: body,
      parentKind,
      opener,
      closer,
    });
  }

  raw() {
    return " ".repeat(this.indent) + this.text + String.fromCharCode(10);
  }

  width() {
    return this.indent + this.text.length;
  }

  isPackable() {
    return (
      this.childNesting < 0 &&
      this.parentKind !== Kind.NONE &&
      this.opener === Kind.NONE &&
      this.closer === Kind.NONE
    );
  }

  isJoinable() {
    return (
      this.parentKind !== Kind.NONE &&
      this.opener === Kind.NONE &&
      this.closer === Kind.NONE
    );
  }

  joinLine(other) {
    this.text += " " + other.text;
    this.items += other.items;
    this.leafs += other.leafs;
    this.childNesting = Math.max(this.childNesting, other.childNesting);
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

export class JSONFoldStats {
  constructor() {
    this.bytesIn = 0;
    this.bytesOut = 0;
    this.linesIn = 0;
    this.linesOut = 0;
  }
}

function asConfig(compact = "") {
  if (compact instanceof JSONFold) return compact;
  if (typeof compact === "string") return JSONFold.preset(compact);
  if (compact && typeof compact === "object") return new JSONFold(compact);
  return JSONFold.DEFAULT;
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

export class JSONFoldFilter {
  constructor(fp, { compact = "" } = {}) {
    this.fp = fp;
    this.stats = new JSONFoldStats();
    this.cfg = asConfig(compact);
    this.pending = "";
    this.stack = [];
  }

  write(s) {
    s = String(s);
    this.stats.bytesIn += s.length;
    this.stats.linesIn += countNewlines(s);

    if (!this.cfg) return this._writeStr(s);

    const nl = String.fromCharCode(10);
    let start = 0;

    if (this.pending) {
      s = this.pending + s;
      this.pending = "";
    }

    for (let i = 0; i < s.length; i++) {
      if (s[i] !== nl) continue;
      const part = s.slice(start, i);
      this._feed(Line.parse(part, this._parentKind()));
      start = i + 1;
    }

    if (start < s.length) {
      this.pending = s.slice(start);
      if (this.pending.trimEnd().length > this.cfg.width) this._markNoFold();
    }

    return s.length;
  }

  flush() {
    this.finish();
    if (this.fp && typeof this.fp.flush === "function") this.fp.flush();
  }

  close() {
    this.finish();
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

  dump(obj, options = {}) {
    return dump(obj, this, { compact: this.cfg, ...options });
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

      if (line.width() > this.cfg.width) this._markNoFold();
      return;
    }

    const closer = line.closer;
    if (closer !== Kind.NONE) {
      this._closeFrame(line, closer);
      return;
    }

    this._emitLine(line);
  }

  _emitLine(line) {
    if (this.stack.length) {
      this._addToFrame(this.stack[this.stack.length - 1], line);
    } else {
      this._writeLine(line);
    }
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
    if (this._tryPack(frame, line)) return;
    if (this._tryJoin(frame, line)) return;

    frame.lines.push(line);
    this._updateFrame(frame, line);

    if (frame.foldOk && line.width() > this.cfg.width) this._markNoFold();
    if (!frame.foldOk) this._streamFrame(frame, { keepLast: true });
  }

  _canJoin(prev, line, limit) {
    return (
      prev.indent === line.indent &&
      prev.items + line.items <= limit &&
      prev.indent + prev.text.length + 1 + line.text.length <= this.cfg.width
    );
  }

  _joinIntoFrame(frame, prev, line) {
    prev.joinLine(line);

    if (line.parentKind === frame.kind) {
      frame.items += line.items;
      frame.leafs += line.leafs;
    }

    this._checkFoldLimits(frame);
  }

  _tryPack(frame, line) {
    if (
      frame.lines.length === 0 ||
      frame.packLimit <= 1 ||
      !line.isPackable()
    ) {
      return false;
    }

    const prev = frame.lines[frame.lines.length - 1];
    if (!(prev.isPackable() && this._canJoin(prev, line, frame.packLimit))) return false;

    this._joinIntoFrame(frame, prev, line);
    return true;
  }

  _tryJoin(frame, line) {
    if (
      frame.lines.length === 0 ||
      frame.joinLimit <= 1 ||
      !line.isJoinable() ||
      line.childNesting > this.cfg.joinNesting
    ) {
      return false;
    }

    const prev = frame.lines[frame.lines.length - 1];
    if (!(
      prev.isJoinable() &&
      prev.childNesting <= this.cfg.joinNesting &&
      this._canJoin(prev, line, frame.joinLimit)
    )) {
      return false;
    }

    this._joinIntoFrame(frame, prev, line);
    return true;
  }

  _updateFrame(frame, line) {
    if (line.closer !== Kind.NONE) return;

    frame.contentLines += 1;

    if (line.parentKind === frame.kind) {
      frame.leafs += line.leafs;
      frame.items += line.items;
    }

    if (line.childNesting >= 0) {
      frame.childNesting = Math.max(frame.childNesting, line.childNesting + 1);
    }

    this._checkFoldLimits(frame);
  }

  _checkFoldLimits(frame) {
    if (frame.contentLines > 1) frame.foldOk = false;
    if (frame.items > frame.foldLimit) frame.foldOk = false;
    if (frame.childNesting > this.cfg.foldNesting) frame.foldOk = false;
  }

  _closeFrame(closer, closingKind) {
    if (this.stack.length === 0) {
      this._writeLine(closer);
      return;
    }

    const frame = this.stack.pop();
    frame.lines.push(closer);

    if (frame.kind !== closingKind) frame.foldOk = false;

    const folded = this._tryFold(frame);
    if (folded !== null) frame.lines = [folded];

    for (const line of frame.lines) this._emitLine(line);
    frame.lines.length = 0;
  }

  _tryFold(frame) {
    if (!frame.foldOk || frame.contentLines !== 1 || frame.lines.length !== 3) {
      return null;
    }

    const foldedLength = frame.lines.reduce((sum, line) => sum + 1 + line.text.length, 0) - 1;
    if (frame.lines[0].indent + foldedLength > this.cfg.width) return null;

    return new Line({
      indent: frame.lines[0].indent,
      text: frame.lines.map((line) => line.text).join(" "),
      parentKind: this._parentKind(),
      items: 1,
      leafs: frame.leafs,
      childNesting: Math.max(0, frame.childNesting),
    });
  }

  _streamFrame(frame, { keepLast }) {
    const last = frame.lines[frame.lines.length - 1];
    const keep = keepLast && last && last.isPackable() ? 1 : 0;
    const emitLines = keep ? frame.lines.slice(0, -keep) : frame.lines;
    frame.lines = keep ? frame.lines.slice(-keep) : [];

    for (const line of emitLines) {
      if (frame.depth === 0) {
        this._writeLine(line);
      } else {
        this._addToFrame(this.stack[frame.depth - 1], line);
      }
    }
  }

  _markNoFold() {
    for (const frame of this.stack) frame.foldOk = false;
    if (this.stack.length) this._streamFrame(this.stack[this.stack.length - 1], { keepLast: true });
  }

  _parentKind() {
    return this.stack.length ? this.stack[this.stack.length - 1].kind : Kind.NONE;
  }
}

function countNewlines(s) {
  let n = 0;
  const nl = String.fromCharCode(10);
  for (let i = 0; i < s.length; i++) if (s[i] === nl) n++;
  return n;
}

function spaces(n) {
  return " ".repeat(n);
}

function encodeString(s) {
  return JSON.stringify(String(s));
}

function isArrayIndexProperty(key) {
  const n = Number(key);
  return String(n) === key && n >= 0 && Number.isInteger(n) && n < 4294967295;
}

function applyToJSON(value, key) {
  if (value && typeof value === "object" && typeof value.toJSON === "function") {
    return value.toJSON(key);
  }
  return value;
}

function makeReplacer(replacer) {
  if (replacer === undefined || replacer === null) return null;
  if (typeof replacer === "function") return replacer;

  if (Array.isArray(replacer)) {
    const allowed = new Set();
    for (const item of replacer) {
      if (typeof item === "string" || typeof item === "number") allowed.add(String(item));
    }
    return { allowed };
  }

  return null;
}

function normalizeJSONValue(value, key, holder, replacerInfo) {
  value = applyToJSON(value, key);

  if (typeof replacerInfo === "function") {
    value = replacerInfo.call(holder, key, value);
  }

  if (value instanceof Number || value instanceof String || value instanceof Boolean) {
    value = value.valueOf();
  }

  return value;
}

function scalarJSON(value) {
  if (value === null) return "null";

  switch (typeof value) {
    case "string":
      return JSON.stringify(value);
    case "number":
      return Number.isFinite(value) ? String(value) : "null";
    case "boolean":
      return value ? "true" : "false";
    case "bigint":
      throw new TypeError("Do not know how to serialize a BigInt");
    default:
      return undefined;
  }
}

class IncrementalJSONEncoder {
  constructor(writer, { indent = 2, replacer = undefined, sortKeys = false } = {}) {
    this.writer = writer;
    this.indent = indent;
    this.replacerInfo = makeReplacer(replacer);
    this.sortKeys = sortKeys;
    this.seen = new Set();
  }

  encode(value) {
    const holder = { "": value };
    this._writeValue(value, "", holder, 0, true);
  }

  _writeLine(indent, text) {
    this.writer.write(spaces(indent) + text + String.fromCharCode(10));
  }

  _writeValue(value, key, holder, depth, topLevel = false) {
    value = normalizeJSONValue(value, key, holder, this.replacerInfo);

    const scalar = scalarJSON(value);
    if (scalar !== undefined) {
      this._writeLine(depth * this.indent, scalar);
      return true;
    }

    if (value === undefined || typeof value === "function" || typeof value === "symbol") {
      if (topLevel) return false;
      return false;
    }

    if (Array.isArray(value)) {
      this._writeArray(value, depth);
      return true;
    }

    if (typeof value === "object" && value !== null) {
      this._writeObject(value, depth);
      return true;
    }

    return false;
  }

  _enter(value) {
    if (this.seen.has(value)) throw new TypeError("Converting circular structure to JSON");
    this.seen.add(value);
  }

  _leave(value) {
    this.seen.delete(value);
  }

  _writeArray(array, depth) {
    this._enter(array);

    if (array.length === 0) {
      this._writeLine(depth * this.indent, "[]");
      this._leave(array);
      return;
    }

    this._writeLine(depth * this.indent, "[");

    for (let i = 0; i < array.length; i++) {
      const last = i === array.length - 1;
      const before = new CaptureWriter();
      const child = new IncrementalJSONEncoder(before, {
        indent: this.indent,
        replacer: this.replacerInfo,
        sortKeys: this.sortKeys,
      });
      child.seen = this.seen;

      let value = normalizeJSONValue(array[i], String(i), array, this.replacerInfo);
      if (value === undefined || typeof value === "function" || typeof value === "symbol") value = null;

      child._writeValue(value, String(i), array, depth + 1);
      writeCapturedWithComma(this.writer, before.text, last);
    }

    this._writeLine(depth * this.indent, "]");
    this._leave(array);
  }

  _writeObject(obj, depth) {
    this._enter(obj);

    let keys = Object.keys(obj).filter((key) => !isArrayIndexProperty(key) || Object.prototype.propertyIsEnumerable.call(obj, key));

    if (this.replacerInfo && this.replacerInfo.allowed) {
      keys = keys.filter((key) => this.replacerInfo.allowed.has(key));
    }

    if (this.sortKeys) keys.sort();

    const valid = [];
    for (const key of keys) {
      const value = normalizeJSONValue(obj[key], key, obj, this.replacerInfo);
      if (value === undefined || typeof value === "function" || typeof value === "symbol") continue;
      valid.push([key, value]);
    }

    if (valid.length === 0) {
      this._writeLine(depth * this.indent, "{}");
      this._leave(obj);
      return;
    }

    this._writeLine(depth * this.indent, "{");

    for (let i = 0; i < valid.length; i++) {
      const [key, value] = valid[i];
      const last = i === valid.length - 1;
      const before = new CaptureWriter();
      const child = new IncrementalJSONEncoder(before, {
        indent: this.indent,
        replacer: this.replacerInfo,
        sortKeys: this.sortKeys,
      });
      child.seen = this.seen;
      child._writeValue(value, key, obj, depth + 1);

      const captured = before.text;
      const firstNl = captured.indexOf(String.fromCharCode(10));
      const first = firstNl >= 0 ? captured.slice(0, firstNl) : captured;
      const rest = firstNl >= 0 ? captured.slice(firstNl + 1) : "";
      const prefix = spaces((depth + 1) * this.indent) + encodeString(key) + ": ";
      const childIndent = spaces((depth + 1) * this.indent);
      const firstBody = first.startsWith(childIndent) ? first.slice(childIndent.length) : first.trimStart();

      this.writer.write(prefix + firstBody + (rest ? String.fromCharCode(10) : ""));
      if (rest) writeCapturedWithComma(this.writer, rest, last);
      else if (!last) this.writer.write("," + String.fromCharCode(10));
      else this.writer.write(String.fromCharCode(10));
    }

    this._writeLine(depth * this.indent, "}");
    this._leave(obj);
  }
}

class CaptureWriter {
  constructor() {
    this.text = "";
  }

  write(s) {
    this.text += s;
    return s.length;
  }
}

function writeCapturedWithComma(writer, text, last) {
  if (last) {
    writer.write(text);
    return;
  }

  const nl = String.fromCharCode(10);
  if (text.endsWith(nl)) text = text.slice(0, -1);
  writer.write(text + "," + nl);
}

export function dump(obj, fp, {
  compact = "",
  indent = 2,
  replacer = undefined,
  sortKeys = false,
} = {}) {
  const out = fp instanceof JSONFoldFilter ? fp : new JSONFoldFilter(fp, { compact });
  const enc = new IncrementalJSONEncoder(out, { indent, replacer, sortKeys });
  enc.encode(obj);
  out.finish();
}

export function dumpi(obj, fp, options = {}) {
  const out = fp instanceof JSONFoldFilter ? fp : new JSONFoldFilter(fp, { compact: options.compact ?? "" });
  dump(obj, out, options);
  return out.stats;
}

export function dumps(obj, options = {}) {
  let text = "";
  dump(obj, (s) => { text += s; }, options);
  return text;
}

export default {
  JSONFold,
  JSONFoldFilter,
  JSONFoldStats,
  Kind,
  Line,
  Frame,
  dump,
  dumpi,
  dumps,
};
