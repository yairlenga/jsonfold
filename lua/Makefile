default: build

PYTHON = python3

config:
	sudo apt install luarocks
	luarocks install dkjson --local
	echo "Add to ~/.bashrc:" 'eval "$$(luarocks path --local)"'

VERSION := $(shell cat ../VERSION)
setup:
	@echo "Updating pyproject.toml to $(VERSION)"
	sed -i -E 's/^(version *= *")[^"]*(")/\1$(VERSION)\2/' pyproject.toml

build:
	luac -p jsonfold.lua
	luac -p cli.lua
	luac -p benchmark.lua

.PHONY: %.t

run-test:
	#./api_test.py
	lua jsonfold.lua --demo
	lua cli.lua --demo
	lua benchmark.lua 10
	cd ../tests/unit && ../run_tests.sh -mlua
	cd ../tests/samples && ../run_tests.sh -mlua
	cd ../tests/future && ../run_tests.sh -mlua

test:
	$(MAKE) run-test > test.log

package:
	luarocks make
	rm -f dist/jsonfold-$(VERSION)[.-]*
	$(PYTHON) -m build
	twine check dist/jsonfold-$(VERSION)[.-]*

T=tmp
setup-verify:
	rm -rf $T
	mkdir -p $T
	python3 -m pip install --no-deps --target $T dist/jsonfold-*.whl

R=run
run-verify:
	rm -rf $R
	mkdir -p $R
	cd $R && python3 -S -c 'import runpy ; import os ; runpy.run_path("${CURDIR}/api_test.py", run_name="__main__")'
	cd $R && python3 -S -m runpy jsonfold --demo
	cd $R && python3 -S -m runpy cli --demo
	cd $R && python3 -S -c 'import runpy ; import os ; runpy.run_path("${CURDIR}/benchmark.py", run_name="__main__")' 10

verify:
	$(MAKE) setup-verify
	PYTHONPATH=$(CURDIR)/$T $(MAKE) run-verify > verify.log
	wc verify.log
	echo "Passed: $@"

publish:
	twine upload dist/jsonfold-$(VERSION)[.-]*

benchmark:
	./run_benchmark.sh

clean:
	rm -rf dist *.egg-info

realclean: clean
	rm -f *.out  *.prof *.lprof *.cover
	rm -rf __pycache__ .pytest_cache .mypy_cache .coverage htmlcov .venv

world:
	make realclean
	make setup
	make build
	make test
	make benchmark
	make package
	make verify
