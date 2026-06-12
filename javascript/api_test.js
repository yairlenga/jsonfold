#!/usr/bin/env node

// api-test.js
import assert from "node:assert/strict";

import {
  JSONFoldConfig,
  JSONFoldStats,
  config,
  format_json,
  write_json,
  filter_stream,
  dump,
  dumps,
} from "./jsonfold.js";

function runApiTest() {
  const data = {
    ids: [1, 2, 3, 4],
    meta: { version: 1, ok: true },
    items: [{ id: 1 }, { id: 2 }],
  };

  // config()
  const cfg = config("high", 120);
  assert(cfg instanceof JSONFoldConfig);
  assert.equal(cfg.width, 120);

  const cfg2 = config(cfg, undefined, { foldNesting: 1 });
  assert(cfg2 instanceof JSONFoldConfig);
  assert.equal(cfg2.width, 120);
  assert.equal(cfg2.foldNesting, 1);

  // format_json()
  let text = format_json(data, 80);
  assert.equal(typeof text, "string");
  assert.deepEqual(JSON.parse(text), data);

  text = format_json(data, 120, {
    config: "high",
    indent: 4,
    sortKeys: true,
  });
  assert.equal(typeof text, "string");
  assert.deepEqual(JSON.parse(text), data);

  // write_json()
  const out1 = { text: "", write(s) { this.text += s; return s.length; } };
  const stats = write_json(data, out1, 80);
  assert(stats instanceof JSONFoldStats);
  assert.deepEqual(JSON.parse(out1.text), data);
  assert(stats.bytesOut > 0);
  assert(stats.linesOut > 0);

  // filter_stream()
  const out2 = { text: "", write(s) { this.text += s; return s.length; } };
  const fp = filter_stream(out2, 80);
  fp.write(JSON.stringify(data, null, 2));
  fp.write("\n");
  fp.finish();
  assert.deepEqual(JSON.parse(out2.text), data);

  // dump()
  const out3 = { text: "", write(s) { this.text += s; return s.length; } };
  const ret = dump(data, out3, { width: 80 });
  assert.equal(ret, undefined);
  assert.deepEqual(JSON.parse(out3.text), data);

  // dumps()
  text = dumps(data, { width: 80 });
  assert.equal(typeof text, "string");
  assert.deepEqual(JSON.parse(text), data);

  // compact/config object compatibility API
  text = dumps(data, { compact: cfg });
  assert.deepEqual(JSON.parse(text), data);

  const out4 = { text: "", write(s) { this.text += s; return s.length; } };
  dump(data, out4, {
    compact: cfg,
    indent: 4,
    sortKeys: true,
  });
  assert.deepEqual(JSON.parse(out4.text), data);

  return 0;
}

try {
  const stat = runApiTest();
  console.log(`${process.argv[1]} api_test Passed`);
  process.exit(stat);
} catch (e) {
  console.error(`${process.argv[1]}: api_test failed: ${e.message}`);
  throw e;
}
