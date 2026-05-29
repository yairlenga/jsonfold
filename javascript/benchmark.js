#!/usr/bin/env node

import { dump, dumps } from "./jsonfold.js";

const REPEATS = 3;

class NullWriter {
  constructor(t0) {
    this.t0 = t0;
    this.firstWrite = null;
    this.bytes = 0;
    this.writes = 0;
  }

  write(s) {
    if (this.firstWrite === null) this.firstWrite = performance.now();
    this.bytes += s.length;
    this.writes++;
    return s.length;
  }

  ttfbMs() {
    return this.firstWrite === null ? null : this.firstWrite - this.t0;
  }
}

function size_label() { return "Kb" ; }

function size_units(n) { return n/1024 ; }

function makeData(rows) {
  return {
    meta: {
      version: 1,
      ok: true,
      name: "jsonfold benchmark",
    },

    long_ids: Array.from({ length: 100 }, (_, i) => i),

    long_obj: Object.fromEntries(
      Array.from({ length: 50 }, (_, i) => [`k${i}`, i])
    ),

    rows: Array.from({ length: rows }, (_, i) => ({
      id: i,
      name: `name_${i}`,
      active: i % 3 === 0,
      score: i * 1.25,

      tags: [
        "alpha",
        "beta",
        "gamma",
        "delta",
      ],

      pos: {
        x: i,
        y: i + 1,
        z: i + 2,
      },

      values: [
        i,
        i + 1,
        i + 2,
        i + 3,
        i + 4,
      ],
    })),
  };
}

function runCase(data, name, t0) {
  const w = new NullWriter(t0);

  if (name === "baseline.dumps.plain") {
    w.write(JSON.stringify(data));
    return w;
  }

  if (name === "baseline.dumps.pretty") {
    w.write(JSON.stringify(data, null, 2));
    return w;
  }

  if (name.startsWith("jsonfold.dumps.")) {
    const compact = name.split(".")[2];
    w.write(dumps(data, { compact, indent: 2 }));
    return w;
  }

  if (name.startsWith("jsonfold.dump.")) {
    const compact = name.split(".")[2];
    dump(data, w, { compact, indent: 2 });
    return w;
  }

  throw new Error(`unknown test: ${name}`);
}

function validateCase(data, name) {
  let text;

  if (name === "baseline.dumps.plain") {
    text = JSON.stringify(data);
  } else if (name === "baseline.dumps.pretty") {
    text = JSON.stringify(data, null, 2);
  } else if (name.startsWith("jsonfold.dumps.")) {
    text = dumps(data, { compact: name.split(".")[2], indent: 2 });
  } else if (name.startsWith("jsonfold.dump.")) {
    let out = "";
    dump(data, s => { out += s; }, { compact: name.split(".")[2], indent: 2 });
    text = out;
  } else {
    throw new Error(`unknown test: ${name}`);
  }

  JSON.parse(text);
  return true;
}

function timeOne(data, name) {
  let best = null;
  let bestDt = Infinity;

  for (let i = 0; i < REPEATS; i++) {
    if (global.gc) global.gc();

    const t0 = performance.now();
    const cpu0 = process.cpuUsage();

    const w = runCase(data, name, t0);

    const cpu = process.cpuUsage(cpu0);
    const t1 = performance.now();
    const dt = t1 - t0;

    const row = {
      "time(ms)": Number(dt.toFixed(1)),
      "CPU(ms)": Number(((cpu.user + cpu.system) / 1000).toFixed(1)),
      "ttfb(ms)": w.ttfbMs() === null ? "" : Number(w.ttfbMs().toFixed(1)),
      "out(KB)": Number(size_units(w.bytes).toFixed(1)),
      writes: w.writes,
    };

    if (dt < bestDt) {
      bestDt = dt;
      best = row;
    }
  }

  return best;
}

function memoryOne(data, name) {
  if (global.gc) global.gc();

  const before = process.memoryUsage().heapUsed;
  const t0 = performance.now();

  runCase(data, name, t0);

  if (global.gc) global.gc();

  const after = process.memoryUsage().heapUsed;
  return Math.max(0, after - before)
}

function runOneSize(rows, onlyName = null) {
  const data = makeData(rows);

  const names = onlyName ? [onlyName] : [
    "baseline.dumps.plain",
    "baseline.dumps.pretty",
    "jsonfold.dump.off",
    "jsonfold.dump.none",
    "jsonfold.dump.default",
    "jsonfold.dump.high",
    "jsonfold.dump.max",
    "jsonfold.dumps.none",
    "jsonfold.dumps.default",
    "jsonfold.dumps.high",
    "jsonfold.dumps.max",
  ];

  const results = [];

  for (const name of names) {
    let speed = {}
    let peakMem = '-'
    let msg = "FAIL"
    process.stderr.write(`${name} (${rows})... `);
    const rowT0 = performance.now();
    try {
        speed = timeOne(data, name);
        peakMem = memoryOne(data, name);
        validateCase(data, name);
        const rowMs = performance.now() - rowT0;
        msg = Number(rowMs.toFixed(1))
    } catch (e) {
      console.error(e);
      msg = "ERROR";
    }
    process.stderr.write(`${msg} ms\n`)
    results.push({
      rows,
      name,
      ...speed,
      peakMB: Number(size_units(peakMem).toFixed(1)),
      "duration(ms)": msg,
    });

  }

  return results;
}

function main(argv) {
  let filter = null;
  let lastSize = null;
  const results = [];

  for (const arg of argv.slice(2)) {
    const n = Number(arg);
    if (Number.isInteger(n) && n > 0) {
      lastSize = n;
      results.push(...runOneSize(n, filter));
    } else {
      filter = arg;
    }
  }

  if (lastSize === null) {
    results.push(...runOneSize(1000, filter));
  }

  console.table(results);
}

main(process.argv);