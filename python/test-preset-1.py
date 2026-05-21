#! /usr/bin/python3
import jsonfold
import sys
data = {
    "a" : { "b": { "c": "abc" } },
    "x" : { "y": { "z": "xyz" } },
    "ids": [1, 2, 3, 4, 5],
    "list": [ "a", "b", "c", "d", [ "e", "f", "g", "h" ], [ [ "i", "j", "k", "l" ] ], "m", "n" ],
    "meta": {"version": 1, "ok": True},
    "a" : { "b": { "c": "abc" } },
    "items": [{"id": 1, "name": "alpha"}, {"id": 2, "name": "beta"}],
}
data = {
    "a" : { "b": { "c": "abc" } },
    "x" : { "y": { "z": "xyz" } },
        }
for compact in ( "default", "low", "med", "high", "max"):
    print("--" + compact + "--")
    jsonfold.dump(data, sys.stdout, compact=compact)
