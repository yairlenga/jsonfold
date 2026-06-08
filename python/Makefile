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

publish:
	echo twine upload dist/*


benchmark:
	./run_benchmark.sh

clean:
	rm -rf dist *.egg-info

realclean: clean
	rm -f *.out  *.prof *.lprof *.cover
	rm -rf __pycache__ .pytest_cache .mypy_cache .coverage htmlcov .venv

