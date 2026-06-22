default: build

PYTHON = python3

config:

setup:
	npm install

build:
	npm build --if-present

test:
	./api_test.js
	cd ../tests/unit && ../run_tests.sh -mjavascript
	cd ../tests/samples && ../run_tests.sh -mjavascript
	cd ../tests/future && ../run_tests.sh -mjavascript

package:
	npm pack

publish:
	echo npm publish

benchmark:
	./run_benchmark.sh

clean:

realclean: clean
	rm -f bench.out
	rm -rf node_modules coverage dist prof
