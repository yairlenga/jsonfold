default: build

PYTHON = python3

config:
	sudo apt install pipx
	pipx install twine


setup:
	# pip3 install build
	# pip3 install twine

build:
	$(PYTHON) -m py_compile jsonfold.py
	$(PYTHON) -m py_compile benchmark.py
#	$(PYTHON) -m py_compile cli.py

test:
	./api_test.py
	./jsonfold.py --demo
	./jsonfold_cli.py --demo
	./benchmark.py 10
	cd ../tests/unit && ./run_tests.sh python
	cd ../tests/samples && ./run_tests.sh python

package:
	rm -rf dist
	$(PYTHON) -m build
	twine check dist/*

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
	cd $R && python3 -S -m runpy jsonfold_cli --demo
	cd $R && python3 -S -c 'import runpy ; import os ; runpy.run_path("${CURDIR}/benchmark.py", run_name="__main__")' 10

verify:
	$(MAKE) setup-verify
	PYTHONPATH=$(CURDIR)/$T $(MAKE) run-verify > verify.log
	wc verify.log
	echo "Passed: $@"

publish:
	echo twine upload dist/*


benchmark:
	./run_benchmark.sh

clean:
	rm -rf dist *.egg-info

realclean: clean
	rm -f *.out  *.prof *.lprof *.cover
	rm -rf __pycache__ .pytest_cache .mypy_cache .coverage htmlcov .venv

