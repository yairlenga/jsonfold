#!/usr/bin/env node

// api-test.js
import assert from "node:assert/strict";

import {
  JSONFoldConfig,
  JSONFoldStats,
  format_json,
  write_json,
  fold_text,
  write_folded,
  create_writer,
  jsonfold_config,
} from "./jsonfold.js";

function runApiTest() {
  const data = {
    ids: [1, 2, 3, 4],
    meta: { version: 1, ok: true },
    items: [{ id: 1 }, { id: 2 }],
  };

  const cfg = jsonfold_config("high", 120);
  // config()
  {
    assert(cfg instanceof JSONFoldConfig);
    assert.equal(cfg.width, 120);
  }

  {
    const cfg2 = jsonfold_config(cfg, undefined, { foldNesting: 1 });
    assert(cfg2 instanceof JSONFoldConfig);
    assert.equal(cfg2.width, 120);
    assert.equal(cfg2.foldNesting, 1);
  }

  // format_json()
  {
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
  }

  // write_json()
  {
    const out1 = { text: "", write(s) { this.text += s; return s.length; } };
    const stats = write_json(data, out1, 80);
    assert(stats instanceof JSONFoldStats);
    assert.deepEqual(JSON.parse(out1.text), data);
    assert(stats.bytesOut > 0);
    assert(stats.linesOut > 0);
  }

  // write_folded()
  {
    const data_str = JSON.stringify(data, null, 2);
    const out1 = { text: "", write(s) { this.text += s; return s.length; } };
    const stats = write_folded(data_str, out1, 80);
    assert(stats instanceof JSONFoldStats);
    assert.deepEqual(JSON.parse(out1.text), data);
    assert(stats.bytesOut > 0);
    assert(stats.linesOut > 0);
  }

    // fold_text
  {
    const data_str = JSON.stringify(data, null, 2);
    const text = fold_text(data_str, 80);
    assert.deepEqual(JSON.parse(text), data);
  }

  // filter_stream()
  {
    const out2 = { text: "", write(s) { this.text += s; return s.length; } };
    const fp = create_writer(out2, 80);
    fp.write(JSON.stringify(data, null, 2));
    fp.write("\n");
    fp.finish();
    assert.deepEqual(JSON.parse(out2.text), data);
  }

  // write_json()
  {
    const out3 = { text: "", write(s) { this.text += s; return s.length; } };
    const stats3 = write_json(data, out3, { width: 80 });
    assert(stats3.bytesIn > 0);
    assert(stats3.bytesOut > 0);
    assert.deepEqual(JSON.parse(out3.text), data);
  }

  // format_json()
  {
    const text = format_json(data, { width: 80 });
    assert.equal(typeof text, "string");
    assert.deepEqual(JSON.parse(text), data);
  }

  // compact/config object compatibility API
  {
    const text = format_json(data, { compact: cfg });
    assert.deepEqual(JSON.parse(text), data);
  }

  {
    const out4 = { text: "", write(s) { this.text += s; return s.length; } };
    write_json(data, out4, {
      compact: cfg,
      indent: 4,
      sortKeys: true,
    });
    assert.deepEqual(JSON.parse(out4.text), data);
  }

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
