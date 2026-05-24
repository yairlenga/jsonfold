// bench-jsonfold.js
import { performance } from "node:perf_hooks";

import { dump as dumpFold } from "./jsonfold.js";
import { dump as dumpStream } from "./jsonfoldstream.js";

class NullWriter {
  constructor(t0) {
    this.t0 = t0;
    this.firstWrite = undefined;
    this.bytes = 0;
    this.writes = 0;
  }

  write(s) {
    if (this.firstWrite === undefined) {
      this.firstWrite = performance.now();
    }

    this.bytes += s.length;
    this.writes++;
    return s.length;
  }

  ttfbMs() {
    return this.firstWrite === undefined
      ? null
      : this.firstWrite - this.t0;
  }
}

function makeData(rows = 100_000) {
  return {
    meta: {
      version: 1,
      ok: true,
      name: "jsonfold benchmark",
    },
    rows: Array.from({ length: rows }, (_, i) => ({
      id: i,
      name: `name_${i}`,
      active: i % 3 === 0,
      score: i * 1.25,
      tags: ["alpha", "beta", "gamma", "delta"],
      pos: { x: i, y: i + 1, z: i + 2 },
      values: [i, i + 1, i + 2, i + 3, i + 4],
    })),
  };
}

function mb(n) {
  return +(n / 1024 / 1024).toFixed(1);
}

function bench(results, rows, name, fn) {
  global.gc?.();

  process.stdout.write(`${name} ... `);

  const heap0 = process.memoryUsage().heapUsed;
  const rss0 = process.memoryUsage().rss;
  const t0 = performance.now();

  const w = fn(t0);

  const t1 = performance.now();
  const heap1 = process.memoryUsage().heapUsed;
  const rss1 = process.memoryUsage().rss;

  const elapsed = +(t1 - t0).toFixed(1);
  console.log(`${elapsed} ms`);

  const ttfb = w.ttfbMs();

  results.push({
    rows,
    name,
    ms: +elapsed,
    ttfbMs: ttfb == null ? "" : +ttfb.toFixed(3),
    outMB: mb(w.bytes),
    heapMB: mb(heap1 - heap0),
    rssMB: mb(rss1 - rss0),
    writes: w.writes,
  });
}

function runOneSize(rows) {
  const results = [];
  const data = makeData(rows);

  bench(results, rows, "JSON.stringify plain", (t0) => {
    const w = new NullWriter(t0);
    const s = JSON.stringify(data);
    w.write(s);
    return w;
  });

  bench(results, rows, "JSON.stringify pretty", (t0) => {
    const w = new NullWriter(t0);
    const s = JSON.stringify(data, null, 2);
    w.write(s);
    return w;
  });

  for (const compact of ["none", "default", "max"]) {
    bench(results, rows, `jsonfold.js ${compact}`, (t0) => {
      const w = new NullWriter(t0);
      dumpFold(data, w, { compact, indent: 2 });
      return w;
    });

    bench(results, rows, `jsonfoldstream.js ${compact}`, (t0) => {
      const w = new NullWriter(t0);
      dumpStream(data, w, { compact, indent: 2 });
      return w;
    });
  }

  console.log(`\nrows=${rows}`);
  console.table(results);
}

const sizes =
  process.argv.length > 2
    ? process.argv.slice(2).map(Number).filter((n) => Number.isFinite(n) && n > 0)
    : [100_000];

for (const rows of sizes) {
  runOneSize(rows);
}