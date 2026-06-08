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

FAILED=0

for f in *.json
do
    base=$(basename "$f" .json)

    if [ -f "$base.gold" ]; then
        ARGS=$(read_args "$base.args")

        $JSONFOLD $ARGS < "$f" > "$base.out"

        if diff -u "$base.gold" "$base.out"
        then
            echo "PASS $base" >&2
        else
            echo "FAIL $base" >&2
            FAILED=1
        fi
    fi
done

exit $FAILED
