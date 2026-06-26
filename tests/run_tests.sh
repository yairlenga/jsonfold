#!/bin/sh -ue

root=../..
mode=

case "${1-}" in
	-mjava) shift ; mode=java ; JSONFOLD="java -jar $root/java/jsonfold-cli/target/jsonfold.jar" ;;
	-mpython | -mpy ) shift ; mode=python ; JSONFOLD="python3 $root/python/cli.py" ;;
	-mjavascript | -mjs | -mnode ) shift ; mode=javascript ; JSONFOLD="node --expose-gc $root/javascript/cli.js" ;;
	-mperl | -mpl ) shift ; mode=perl ; JSONFOLD="perl $root/perl/script/jsonfold.pl" ;;
	-mc) shift ; mode=c ; JSONFOLD="$root/c/jsonfold.exe" ;;
esac

echo "Using: JSONFOLD=${JSONFOLD?No JSONFOLD}"

read_args() {
    # print every line that starts with '--' or lines with args=...
    sed -n -e '/^--/p' -e 's/^args=//p' "$1"
}

passed=0
failed=0
skipped=0

[ "$*" ] || set -- *.args

echo "Testing: $(pwd)" >&2

for arg ;
do
    base=$(basename "$arg" .args)
    gold="$base.gold"
    json="$base.json"
    out="$base.out"
    args="$base.args"

    if [ -f "$args" -a -f "$gold" -a -f "$json" ] ; then
        label=$base
	skip=$(grep "^skip.$mode=" $args || true)
	if [ "$skip" ] ; then
	    echo "SKIP $label ($mode): ${skip#*=}"
            skipped=$((skipped+1))
	    continue
	fi

        ARGS=$(read_args "$arg")
	if [ "$mode" -a -f "$base.$mode.gold" ] ; then
	    gold="$base.$mode.gold"
	    label="$base ($mode)"
        fi

        $JSONFOLD $ARGS < "$json" > "$out"

        if diff -u "$gold" "$out"
        then
            echo "OK $label"
            passed=$((passed+1))
        else
            failed=$((failed+1))
            echo "FAIL $label" >&2
        fi
    else
        echo "UNKNWON test: $arg" >&2
    fi
done

echo "Passed: $passed, failed: $failed, skiped: $skipped" >&2
[ $failed = 0 ]
