#!/bin/sh -ue

case "${@-}" in
	java) JSONFOLD='java -jar ../../java/jsonfold-cli/target/jsonfold.jar' ;;
	javagold) JSONFOLD='java -jar ../../java/jsonfold-cli/target/jsonfold.jar --gold' ;;
	python) JSONFOLD='python3 ../../python/jsonfold_cli.py' ;;
	javascript) JSONFOLD='node --expose-gc ../../javascript/cli.js' ;;
	perl) JSONFOLD='perl ../../perl/script/jsonfold.pl' ;;
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