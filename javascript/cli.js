#! /usr/bin/env node

import * as jsonfold from "./jsonfold.js";


function demoData() {
  return {
    meta: { version: 1, ok: true },
    items: [{ id: 1, name: "alpha" }, { id: 2, name: "beta" }],
    matrix: [[1, 2], [3, 4]],
    long: [
      "this is a long message that may force the block to stay expanded",
      "second", "third", "fourth",
    ],
    "single-array": [1],
    "single-obj": { value: 2 },
    wide_array: Array.from({ length: 9 }, (_, i) => `abcdefghijklmnopqrstuvwxyz1${i + 1}`),
    wide_object: Object.fromEntries(
      Array.from({ length: 9 }, (_, i) => [`abcdefghijk${i + 1}`, `lmnopqrstuvwxyz${i + 1}`])
    ),
  };
}


export async function main(argv = process.argv.slice(2)) {
  const args = parseArgs(argv);

  if (args.help) {
    printUsage(process.stderr);
    return 0;
  }

  let cfg = jsonfold.JSONFold.preset(args.compact);
  const overrides = {};

  if (args.width === undefined) {
    overrides.width = process.stdout.isTTY ? process.stdout.columns : 80;
  }

  // Convenience shorthands
  if (args.packItems !== undefined) {
    overrides.packArrayItems = args.packItems;
    overrides.packObjItems = args.packItems;
  }

  if (args.foldItems !== undefined) {
    overrides.foldArrayItems = args.foldItems;
    overrides.foldObjItems = args.foldItems;
  }

  if (args.joinItems !== undefined) {
    overrides.joinArrayItems = args.joinItems;
    overrides.joinObjItems = args.joinItems;
  }

  // Individual overrides
  for (const [argName, cfgName] of [
    ["width", "width"],

    ["packArrayItems", "packArrayItems"],
    ["packObjItems", "packObjItems"],
    ["packNesting", "packNesting"],

    ["foldArrayItems", "foldArrayItems"],
    ["foldObjItems", "foldObjItems"],
    ["foldNesting", "foldNesting"],

    ["joinArrayItems", "joinArrayItems"],
    ["joinObjItems", "joinObjItems"],
    ["joinNesting", "joinNesting"],
  ]) {
    if (args[argName] !== undefined) {
      overrides[cfgName] = args[argName];
    }
  }

  cfg = cfg.replace(overrides);

  if (args.verbose) {
    console.error(cfg);
  }
  let backend = jsonfold
// TBD: Choose backend
//  if ( args.stream ) {
//    backend = await import ("./jsonfoldstream.js")
//  }

  const data = args.demo
    ? demoData()
    : JSON.parse(await readStdin());

  const stats = backend.dumpi(data, process.stdout, {
    compact: cfg,
    indent: args.indent,
    sortKeys: args.sortKeys,
  });

  if (args.verbose) {
    console.error(stats);
  }

  return 0;
}

function parseArgs(argv) {
  const out = {
    compact: "default",
    indent: 2,

    demo: false,
    verbose: false,
    sortKeys: false,
    help: false,
    stream: false,
  };

  const numeric = new Map([
    ["--width", "width"],

    ["--pack-items", "packItems"],
    ["--pack-array-items", "packArrayItems"],
    ["--pack-obj-items", "packObjItems"],
    ["--pack-nesting", "packNesting"],

    ["--fold-items", "foldItems"],
    ["--fold-array-items", "foldArrayItems"],
    ["--fold-obj-items", "foldObjItems"],
    ["--fold-nesting", "foldNesting"],

    ["--join-items", "joinItems"],
    ["--join-array-items", "joinArrayItems"],
    ["--join-obj-items", "joinObjItems"],
    ["--join-nesting", "joinNesting"],

    ["--indent", "indent"],
  ]);

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];

    switch (arg) {
      case "--demo":
        out.demo = true;
        continue;

      case "--verbose":
      case "-v":
        out.verbose = true;
        continue;

      case "--stream":
        out.stream = true;
        continue;

      case "--sort-keys":
        out.sortKeys = true;
        continue;

      case "--help":
      case "-h":
        out.help = true;
        continue;
    }

    if (arg === "--compact") {
      out.compact = requireValue(argv, ++i, arg);
      validateCompact(out.compact);
      continue;
    }

    if (arg.startsWith("--compact=")) {
      out.compact = arg.slice("--compact=".length);
      validateCompact(out.compact);
      continue;
    }

    const eq = arg.indexOf("=");

    const name =
      eq >= 0 ? arg.slice(0, eq) : arg;

    const value =
      eq >= 0 ? arg.slice(eq + 1) : undefined;

    if (numeric.has(name)) {
      const key = numeric.get(name);

      out[key] = parseInteger(
        value ?? requireValue(argv, ++i, name),
        name,
      );

      continue;
    }

    throw new Error(`unknown option: ${arg}`);
  }

  return out;
}

function requireValue(argv, i, name) {
  if (
    i >= argv.length ||
    argv[i].startsWith("--")
  ) {
    throw new Error(`${name} requires a value`);
  }

  return argv[i];
}

function parseInteger(s, name) {
  const n = Number(s);

  if (!Number.isInteger(n)) {
    throw new Error(`${name} requires an integer`);
  }

  return n;
}

function validateCompact(name) {
  if (!Object.hasOwn(jsonfold.JSONFold.PRESETS, name)) {
    throw new Error(`unknown compact preset: ${name}`);
  }
}

async function readStdin() {
  return await new Promise((resolve, reject) => {
    let data = "";

    process.stdin.setEncoding("utf8");

    process.stdin.on("data", (chunk) => {
      data += chunk;
    });

    process.stdin.on("end", () => {
      resolve(data);
    });

    process.stdin.on("error", reject);
  });
}

function printUsage(fp) {
  fp.write(`usage: node jsonfold.js [options] < input.json

options:
  --demo
  --compact default|none|low|med|high|max|pack|fold|join
  --width N
  --verbose, -v

pack phase:
  --pack-items N
  --pack-array-items N
  --pack-obj-items N
  --pack-nesting N

fold phase:
  --fold-items N
  --fold-array-items N
  --fold-obj-items N
  --fold-nesting N

join phase:
  --join-items N
  --join-array-items N
  --join-obj-items N
  --join-nesting N

json options:
  --indent N
  --sort-keys
  --help, -h
`);
}

main()
  .then((rc) => {
    process.exit(rc);
  })
  .catch((err) => {
    console.error(err?.stack ?? String(err));
    process.exit(1);
  });
