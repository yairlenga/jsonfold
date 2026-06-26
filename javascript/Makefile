default: build

PYTHON = python3

config:

VERSION = $(shell cat ../VERSION)
setup:
	sed -i -E 's/^([[:space:]]*"version"[[:space:]]*:[[:space:]]*")[^"]*(".*)/\1$(VERSION)\2/' package.json
	npm install

build:
	npm run build --if-present

test:
	./api_test.js
	cd ../tests/unit && ../run_tests.sh -mjavascript
	cd ../tests/samples && ../run_tests.sh -mjavascript
	cd ../tests/future && ../run_tests.sh -mjavascript

benchmark:
	./run_benchmark.sh

package:
	mkdir -p dist
	npm pack --pack-destination dist

T=tmp
verify:
	rm -rf $T
	mkdir -p $T
	npm init -y --prefix $T
	npm install  --prefix $T ./dist/jsonfold-core-$(VERSION).tgz

publish:
	npm publish --access public ./dist/jsonfold-core-$(VERSION).tgz

clean:

realclean: clean
	rm -f bench.out
	rm -rf node_modules coverage

world:
	make realclean
	make setup
	make build
	make test
	make benchmark
	make package
	make verify
