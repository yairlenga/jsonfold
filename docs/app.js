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

    stats.textContent =
        `Reduction: ${reduction}% | ` +
        `Raw: ${raw.lines}L/${raw.width}W/${raw.bytes}B | ` +
        `Pretty: ${pretty.lines}L/${pretty.width}W/${pretty.bytes}B | ` +
        `Folded: ${folded.lines}L/${folded.width}W/${folded.bytes}B`;
}

function format() {
    const rawText = input.value;
    const compactName = compactEl.value;
    const width = Number(widthEl.value) || 80;
    const indent = Number(indentEl.value) || 2;

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

runEl.addEventListener("click", format);

input.addEventListener("input", formatIfLive);
compactEl.addEventListener("change", formatIfLive);
widthEl.addEventListener("input", formatIfLive);
indentEl.addEventListener("input", formatIfLive);

liveEl.addEventListener("change", () => {
    if (liveEl.checked) {
        format();
    }
});

format();