import { dumps } from "./jsonfold.js";

function run() {
  const input = document.getElementById("input").value;
  const compact = document.getElementById("compact").value;
  const width = Number(document.getElementById("width").value);
  const indent = Number(document.getElementById("indent").value);

  try {
    const obj = JSON.parse(input);

    document.getElementById("output").value = dumps(obj, {
      compact,
      width,
      indent,
    });
    updateStats();
  } catch (e) {
    document.getElementById("output").value = "ERROR: " + e.message;
  }
}

function lineCount(s) {
    if (!s) return 0;
    return s.endsWith("\n")
        ? s.split("\n").length - 1
        : s.split("\n").length;
}

function maxWidth(s) {
    if (!s) return 0;

    let max = 0;
    for (const line of s.split("\n")) {
        max = Math.max(max, line.length);
    }
    return max;
}

function updateStats() {
    const input = document.getElementById("input").value;
    const output = document.getElementById("output").value;

    const inLines = lineCount(input);
    const outLines = lineCount(output);

    const inBytes = input.length;
    const outBytes = output.length;

    const inWidth = maxWidth(input);
    const outWidth = maxWidth(output);

    const inArea = inLines * inWidth;
    const outArea = outLines * outWidth;

    const reduction =
        inArea > 0
            ? Math.round((1 - outArea / inArea) * 100)
            : 0;

    document.getElementById("stats").textContent =
        `Reduction: ${reduction}% | ` +
        `Lines: ${inLines}→${outLines} | ` +
        `Width: ${inWidth}→${outWidth} | ` +
        `Bytes: ${inBytes}→${outBytes}`;
}

const SAMPLE_JSON = `{
  "name": "jsonfold",
  "items": [
    1,
    2,
    3,
    4,
    5
  ],
  "nested": {
    "a": [
      {
        "x": 1
      },
      {
        "x": 2
      }
    ]
  }
}`;

document.getElementById("run").addEventListener("click", run);
const input = document.getElementById("input");

input.value = SAMPLE_JSON;
run();