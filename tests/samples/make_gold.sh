#!/bin/sh
set -e

PYTHON=${PYTHON:-python3}
JSONFOLD=${JSONFOLD:-./jsonfold.py}

read_args() {
    sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$1"
}

for f in *.json
do
    base=$(basename "$f" .json)
    ARGS=$(read_args "$base.args")

    $PYTHON $JSONFOLD $ARGS < "$f" > "$base.gold"
    echo "generated $base.gold"
done
