#!/usr/bin/env python3

# api_test.py
import io
import json
import sys

import jsonfold


def run_api_test() -> int:
    data = {
        "ids": [1, 2, 3, 4],
        "meta": {"version": 1, "ok": True},
        "items": [{"id": 1}, {"id": 2}],
    }

    # config()
    cfg = jsonfold.jsonfold_config("high", width=120)
    assert isinstance(cfg, jsonfold.JSONFoldConfig)
    assert cfg.width == 120

    cfg2 = jsonfold.jsonfold_config(cfg, fold_nesting=1)
    assert isinstance(cfg2, jsonfold.JSONFoldConfig)
    assert cfg2.width == 120
    assert cfg2.fold_nesting == 1

    # format_json()
    text = jsonfold.format_json(data, 80)
    assert isinstance(text, str)
    assert json.loads(text) == data

    text = jsonfold.format_json(data, 120, config="high", indent=4, sort_keys=True)
    assert isinstance(text, str)
    assert json.loads(text) == data

    # write_json()
    out = io.StringIO()
    stats = jsonfold.write_json(data, out, 80)
    assert isinstance(stats, jsonfold.JSONFoldStats)
    assert json.loads(out.getvalue()) == data
    assert stats.bytes_out > 0
    assert stats.lines_out > 0

    # filter_stream()
    out = io.StringIO()
    with jsonfold.create_writer(out, 80) as fp:
        json.dump(data, fp, indent=2)
    assert json.loads(out.getvalue()) == data

    # dump()
    out = io.StringIO()
    ret = jsonfold.dump(data, out, width=80)
    assert ret is None
    assert json.loads(out.getvalue()) == data

    # dumps()
    text = jsonfold.dumps(data, width=80)
    assert isinstance(text, str)
    assert json.loads(text) == data

    # compact/config object with compatibility API
    text = jsonfold.dumps(data, compact=cfg)
    assert json.loads(text) == data

    out = io.StringIO()
    jsonfold.dump(data, out, compact=cfg, indent=4, sort_keys=True)
    assert json.loads(out.getvalue()) == data

    return 0


if __name__ == "__main__":
    try:
        stat = run_api_test();
        print(f"{sys.argv[0]} api_test Passed")
        raise SystemExit(stat)
    except AssertionError as e:
        print(f"{sys.argv[0]}: api_test failed: {e}", file=sys.stderr)
        raise