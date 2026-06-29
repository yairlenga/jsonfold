.PHONY: %

default: build

config:
	# sudo apt remove 'dotnet*' 'aspnetcore*' 'netstandard*'
	sudo apt install dotnet-sdk-8.0

VERSION := $(shell cat ../VERSION)
setup:
	TBD

build:
	dotnet build

.PHONY: %.t

run-test:
	go test ./...
	./jsonfold.exe --demo
	./benchmark.exe 10
	cd ../tests/unit && ../run_tests.sh -mgo
	cd ../tests/samples && ../run_tests.sh -mgo
	cd ../tests/future && ../run_tests.sh -mgo
	echo "Passed: $@"

test:
	$(MAKE) run-test | tee test.log

package:
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
	dotnet clean

realclean: clean
	rm -rf */*/bin/ */*/obj/
	rm -f *.old

world:
	make realclean
	make setup
	make build
	make test
	make benchmark
	make package
	make verify
