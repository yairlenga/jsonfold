import { JSONFold, stringify } from "./jsonfold.js";

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

const examples = [
  {
    label: "Basic demo",
    url: "examples/sample.json"
  },
  {
    label: "GeoJSON states/provinces",
    url: "https://raw.githubusercontent.com/yairlenga/jsonfold/refs/heads/main/articles/samples/geojson-none.json"
  }
];

const input = document.getElementById("input");
const output = document.getElementById("output");
const stats = document.getElementById("stats");

const compactEl = document.getElementById("compact");
const widthEl = document.getElementById("width");
const indentEl = document.getElementById("indent");
const liveEl = document.getElementById("live");
const runEl = document.getElementById("run");

input.value = SAMPLE_JSON;

function textStats(s) {
    const bytes = s.length;

    if (!bytes) {
        return {
            lines: 0,
            width: 0,
            bytes: 0,
        };
    }

    let lines = 1;
    let width = 0;
    let curWidth = 0;

    for (let i = 0; i < bytes; i++) {
        if (s[i] === "\n") {
            lines++;
            if (curWidth > width) {
                width = curWidth;
            }
            curWidth = 0;
        } else {
            curWidth++;
        }
    }

    if (curWidth > width) {
        width = curWidth;
    }

    if (s.endsWith("\n")) {
        lines--;
    }

    return {
        lines,
        width,
        bytes,
    };
}

function complexity(lines, width) {
    return lines * width * Math.max(lines, width);
}

function readabilityIndex(stats, pretty) {
    const prettyComplexity =
        complexity(pretty.lines, pretty.width);

    const currentComplexity =
        complexity(stats.lines, stats.width);

    return currentComplexity > 0 ? prettyComplexity / currentComplexity : null
}

function fmtNumber(n) {
    return Number(n).toLocaleString();
}

function fmtBytes(bytes) {

    if ( bytes == undefined ) return "?"
    const units = ["B", "KB", "MB", "GB", "TB"];

    let value = bytes;
    let unit = 0;

    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit++;
    }

    return value >= 10
        ? `${value.toFixed(0)} ${units[unit]}`
        : `${value.toFixed(1)} ${units[unit]}`;
}

function fmtStats(s) {
    return `${fmtBytes(s.bytes)} (${fmtNumber(s.lines)}L X ${fmtNumber(s.width)}C)`;
}

function updateStats(rawText, prettyText, foldedText) {
    const raw = textStats(rawText);
    const pretty = textStats(prettyText);
    const folded = textStats(foldedText);

    const prettyArea = pretty.lines * pretty.width;
    const foldedArea = folded.lines * folded.width;

    const reduction =
        prettyArea > 0
            ? Math.round((1 - foldedArea / prettyArea) * 100)
            : 0;

    const ri = readabilityIndex(folded, pretty)
    console.log(ri)

    stats.textContent =
        `Reduction: ${reduction}% | ` + 
        `Raw: ${fmtStats(raw)} | Pretty: ${fmtStats(pretty)} | Folded: ${fmtStats(folded)} | ` +
        `Readability Index: ${ri ? ri.toFixed(1) : "NA"}`
}

function format() {
    const rawText = input.value;
    const compactName = compactEl.value;
    const width = Number(widthEl.value) || 80;
    const indent = Number(indentEl.value) || 2;
    output.value = "Processing ..."
    stats.textContent = "Processing ..."

    try {
        const obj = JSON.parse(rawText);

        const prettyText = JSON.stringify(obj, null, indent);

        let compact = JSONFold.preset(compactName);

        if (compact) {
            compact = compact.replace({ width });
        }

        const foldedText = stringify(obj, {
            compact,
            indent,
        });

        output.value = foldedText;

        updateStats(
            rawText,
            prettyText,
            foldedText
        );
    } catch (err) {
        output.value = String(err);
        stats.textContent = "Invalid JSON";
    }
}

function formatIfLive() {
    if (liveEl.checked) {
        format();
    }
}

async function loadExample(url) {
  const res = await fetch(url);

  if (!res.ok) {
    throw new Error(`Failed to load example: ${res.status}`);
  }

  const text = await res.text();

  // Optional validation / pretty input
  const data = JSON.parse(text);

  input.value = JSON.stringify(data, null, 2);
  format()
}

for (const ex of examples) {
  const opt = document.createElement("option");
  opt.value = ex.url;
  opt.textContent = ex.label;
  exampleSelect.appendChild(opt);
}


runEl.addEventListener("click", format);

input.addEventListener("input", formatIfLive);
compactEl.addEventListener("change", formatIfLive);
widthEl.addEventListener("input", formatIfLive);
indentEl.addEventListener("input", formatIfLive);

exampleSelect.addEventListener("change", async (e) => {
    const url = e.target.value;
    if (!url) return;

    await loadExample(url);

    e.target.selectedIndex = 0;   // No change event generated
});

liveEl.addEventListener("change", () => {
    if (liveEl.checked) {
        format();
    }
});

format();