#!/bin/sh
set -e

PYTHON=${PYTHON:-python3}
JSONFOLD=${JSONFOLD:-./jsonfold.py}

read_args() {
    sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$1"
}

FAILED=0

for f in *.json
do
    base=$(basename "$f" .json)

    if [ -f "$base.gold" ]; then
        ARGS=$(read_args "$base.args")

        $PYTHON $JSONFOLD $ARGS < "$f" > "$base.out"

        if diff -u "$base.gold" "$base.out"
        then
            echo "PASS $base"
        else
            echo "FAIL $base"
            FAILED=1
        fi
    fi
done

exit $FAILED
