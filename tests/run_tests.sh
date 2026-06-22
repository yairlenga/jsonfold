#!/bin/sh -ue

root=../..
mode=

case "${1-}" in
	-mjava) shift ; mode=java ; JSONFOLD="java -jar $root/java/jsonfold-cli/target/jsonfold.jar" ;;
	-mpython | -mpy ) shift ; mode=python ; JSONFOLD="python3 $root/python/cli.py" ;;
	-mjavascript | -mjs | -mnode ) shift ; mode=node ; JSONFOLD="node --expose-gc $root/javascript/cli.js" ;;
	-mperl | -mpl ) shift ; mode=perl ; JSONFOLD="perl $root/perl/script/jsonfold.pl" ;;
	-mc) shift ; mode=c ; JSONFOLD="$root/c/jsonfold.exe" ;;
esac

echo "Using: JSONFOLD=${JSONFOLD?No JSONFOLD}"

read_args() {
    # Strip blank lines and comments from .args files.
    # This keeps .args human-readable while preserving shell execution.
    sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$1"
}

passed=0
failed=0

[ "$*" ] || set -- *.args

echo "Testing: $(pwd)" >&2

for arg ;
do
    base=$(basename "$arg" .args)
    gold="$base.gold"
    json="$base.json"
    out="$base.out"

    if [ -f "$gold" -a -f "$json" ] ; then
        label=$base
        ARGS=$(read_args "$arg")
	if [ "$mode" -a -f "$base.$mode.gold" ] ; then
	    gold="$base.$mode.gold"
	    label="$base ($mode)"
        fi

        $JSONFOLD $ARGS < "$json" > "$out"

        if diff -u "$gold" "$out"
        then
            echo "PASS $label"
            passed=$((passed+1))
        else
            failed=$((failed+1))
            echo "FAIL $label" >&2
        fi
    else
        echo "SKIP $base: no $base.gold"
    fi
done

echo "Passed: $passed, failed: $failed" >&2
[ $failed = 0 ]
