#!/usr/bin/env python3
import gc
import json
import sys
import time
import tracemalloc

import jsonfold


REPEATS = 3
MEM_FRACTION = 0.10


class NullWriter:
    def __init__(self, t0):
        self.t0 = t0
        self.first_write = None
        self.bytes = 0
        self.writes = 0

    def write(self, s):
        if self.first_write is None:
            self.first_write = time.perf_counter()
        self.bytes += len(s)
        self.writes += 1
        return len(s)

    def ttfb_ms(self):
        if self.first_write is None:
            return ""
        return round((self.first_write - self.t0) * 1000, 1)


def make_data(rows):
    return {
        "meta": {"version": 1, "ok": True, "name": "jsonfold benchmark"},
        "long_ids": list(range(100)),
        "long_obj": {f"k{i}": i for i in range(50)},
        "rows": [
            {
                "id": i,
                "name": f"name_{i}",
                "active": i % 3 == 0,
                "score": i * 1.25,
                "tags": ["alpha", "beta", "gamma", "delta"],
                "pos": {"x": i, "y": i + 1, "z": i + 2},
                "values": [i, i + 1, i + 2, i + 3, i + 4],
            }
            for i in range(rows)
        ],
    }


def mem_label():
    return "kb"

def mem_units(n):
    return round(n / 1024, 1)


def run_case(data, name):
    if name == "baseline.dumps.plain":
        return lambda t0: write_string(t0, json.dumps(data))
    if name == "baseline.dumps.pretty":
        return lambda t0: write_string(t0, json.dumps(data, indent=2))
    if name == "baseline.dump.pretty":
        return lambda t0: run_json_dump(data, t0)
    if name == "baseline.dump.plain":
        return lambda t0: run_json_dump_plain(data, t0)

    kind, func, compact = name.split(".")

    if kind == "jsonfold":
        if func == "dumps":
            return lambda t0: write_string(t0, jsonfold.dumps(data, compact=compact, indent=2))
        if func == "dump":
            return lambda t0: run_jsonfold_dump(data, t0, compact)

    raise ValueError(name)


def write_string(t0, s):
    w = NullWriter(t0)
    w.write(s)
    return w


def run_json_dump(data, t0):
    w = NullWriter(t0)
    json.dump(data, w, indent=2)
    return w


def run_jsonfold_dump(data, t0, compact):
    w = NullWriter(t0)
    jsonfold.dump(data, w, compact=compact, indent=2)
    return w

def run_json_dump_plain(data, t0):
    w = NullWriter(t0)
    json.dump(data, w)
    return w

def time_one(name, data):
    best = None
    best_dt = 0

    for _ in range(REPEATS):
        gc.collect()
        t0 = time.perf_counter()
        p0 = time.process_time()
        w = run_case(data, name)(t0)
        p1 = time.process_time()
        t1 = time.perf_counter()
        dt = t1-t0

        row = {
            "time(ms)": round(dt * 1000, 1),
            "CPU(ms)": round((p1 - p0) * 1000, 1),
            "ttfb(ms)": w.ttfb_ms(),
            f"out({mem_label()})": mem_units(w.bytes),
            "writes": w.writes,
        }

        if best is None or dt < best_dt:
            best = row
            best_dt = dt

    return best_dt, best


def memory_one(name, data):
    gc.collect()
    tracemalloc.start()

    t0 = time.perf_counter()
    run_case(data, name)(t0)

    _, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    return mem_units(peak)


def print_table(rows):
    cols = list(rows[0].keys())
    widths = {c: max(len(c), *(len(str(r[c])) for r in rows)) for c in cols}

    def is_num(v):
        return isinstance(v, (int, float)) and not isinstance(v, bool)

    numeric = {
        c: all(is_num(r[c]) or r[c] == "" for r in rows)
        for c in cols
    }

    def cell(c, v):
        s = str(v)
        return " " + (s.rjust(widths[c]) if numeric[c] else s.ljust(widths[c])) + " "

    line = "+" + "+".join("-" * (widths[c] + 2) for c in cols) + "+"

    print(line)
    print("|" + "|".join(cell(c, c) for c in cols) + "|")
    print(line)
    for r in rows:
        print("|" + "|".join(cell(c, r[c]) for c in cols) + "|")
    print(line)

def run_one_size(rows, testid):
    data = make_data(rows)

    names = [
        "baseline.dump.plain",
        "baseline.dump.pretty",
        "jsonfold.dump.off",
        "jsonfold.dump.none",
        "jsonfold.dump.default",
        "jsonfold.dump.low",
        "jsonfold.dump.med",
        "jsonfold.dump.high",
        "jsonfold.dump.max",
        "baseline.dumps.plain",
        "baseline.dumps.pretty",
        "jsonfold.dumps.none",
        "jsonfold.dumps.default",
        "jsonfold.dumps.max",
    ] if testid is None else [ testid ]
    results = []

    for name in names:
        print(f"{name} ({rows})... ", end="", file=sys.stderr, flush=True)

        dt, speed = time_one(name, data)
        peak_mb = memory_one(name, data)

        print(f"{round(dt*1000,0)} ms", file=sys.stderr)

        results.append({
            "rows": rows,
            "name": name,
            **speed,
            f"peak({mem_label()})": peak_mb,
        })

    return results


def main(argv):
    filter = None
    last_sz = None
    results = []
    for arg in argv[1:]:
        try:
            last_sz = int(arg)
        except ValueError:
            filter = arg
            continue
        results.extend(run_one_size(last_sz, filter))

    if last_sz is None:
        results.extend(run_one_size(1_000, filter))

    print_table(results)

if __name__ == "__main__":
    main(sys.argv)