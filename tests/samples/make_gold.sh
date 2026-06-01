#!/bin/sh
set -e

JSONFOLD=${JSONFOLD:-python3 ../../python/jsonfold.py}

read_args() {
    sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$1"
}

for f in *.json
do
    base=$(basename "$f" .json)
    ARGS=$(read_args "$base.args")

    $JSONFOLD $ARGS < "$f" > "$base.gold"
    echo "generated $base.gold"
done
