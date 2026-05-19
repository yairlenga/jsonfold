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



export class IncrementalJSONEncoder {
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

import {
  JSONFoldFilter,
} from "./jsonfold.js";

export function dump(obj, fp, {
  compact = "",
  indent = 2,
  replacer = undefined,
  sortKeys = false,
} = {}) {
  const out =
    fp instanceof JSONFoldFilter
      ? fp
      : new JSONFoldFilter(fp, { compact });

  const enc = new IncrementalJSONEncoder(out, {
    indent,
    replacer,
    sortKeys,
  });

  enc.encode(obj);
  out.finish();
}

export function dumpi(obj, fp, options = {}) {
  const out =
    fp instanceof JSONFoldFilter
      ? fp
      : new JSONFoldFilter(fp, {
          compact: options.compact ?? "",
        });

  dump(obj, out, options);
  return out.stats;
}

export function stringify(obj, options = {}) {
  let text = "";

  dump(obj, (s) => {
    text += s;
  }, options);

  return text;
}