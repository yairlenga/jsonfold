#! /bin/bash
JSONFOLD=../../python/jsonfold.py
mkdir -p out
function run1 {
	local out=out/geojson-$1.json
	shift
	python3 $JSONFOLD < geojson.json > $out "$@"
	echo "$(wc < $out)" \
		"$(awk 'length > max_len { max_len = length } END { print max_len }' $out)" \
		$1

}

run1 none --compact=none --width=120
run1 low --compact=low --width=120
run1 med --compact=med --width=120
run1 default --compact=default --width=120
run1 high --compact=high --width=120
run1 max --compact=max 
