#!/bin/sh
set -eu

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
    # Strip blank lines and comments from .args files.
    # This keeps .args human-readable while preserving shell execution.
    sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$1"
}

FAILED=0

for f in [0-9][0-9][0-9].json
do
    base=$(basename "$f" .json)

    if [ -f "$base.gold" ]; then
        ARGS=$(read_args "$base.args")

        $JSONFOLD $ARGS < "$f" > "$base.out"

        if diff -u "$base.gold" "$base.out"
        then
            echo "PASS $base"
        else
            echo "FAIL $base"
            FAILED=1
        fi
    else
        echo "SKIP $base: no $base.gold"
    fi
done

exit $FAILED
