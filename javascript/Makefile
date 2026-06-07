default: build

PYTHON = python3

config:

setup:
	npm install

build:

test:
	cd ../tests/unit && ./run_tests.sh javascript
	cd ../tests/samples && ./run_tests.sh javascript

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
