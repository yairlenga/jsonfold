#!/bin/sh -ue

root=../..

case "${@-}" in
	java) JSONFOLD="java -jar $root/java/jsonfold-cli/target/jsonfold.jar" ;;
	python) JSONFOLD="python3 $root/python/cli.py" ;;
	javascript) JSONFOLD="node --expose-gc $root/javascript/cli.js" ;;
	perl) JSONFOLD="perl $root/perl/script/jsonfold.pl" ;;
	c) JSONFOLD="$root/c/jsonfold.exe" ;;
	?*) JSONFOLD="$@"
esac

echo "Using: JSONFOLD=${JSONFOLD?No JSONFOLD}"

read_args() {
    sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$1"
}

echo "Testing: $(pwd)" >&2

passed=0
failed=0

for f in *.json
do
    base=$(basename "$f" .json)

    if [ -f "$base.gold" ]; then
        ARGS=$(read_args "$base.args")

        $JSONFOLD $ARGS < "$f" > "$base.out"

        if diff -u "$base.gold" "$base.out"
        then
            passed=$((passed+1))
            echo "PASS $base"
        else
            failed=$((failed+1))
            echo "FAIL $base" >&2
        fi
    fi
done
echo "Passed: $passed, failed: $failed" >&2
[ $failed = 0 ]
