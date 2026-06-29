default: build

MODULES = core cli jackson gson

clean:
	mvn clean

realclean:

config:

VERSION := $(shell cat ../VERSION)
setup:
	mkdir -vp ${MODULES:%=jsonfold-%/src/main/resources}
	mkdir -vp ${MODULES:%=jsonfold-%/src/test/resources}
	mvn versions:set -DnewVersion=$(VERSION) -DgenerateBackupPoms=false

	# include validate, compile, test and pacakge
build:
	mvn package

run-test:
	./run.sh --demo
	./run.sh d.j.f.Main --demo
	./run.sh d.j.f.Benchmark 10
	cd ../tests/unit && ../run_tests.sh -mjava
	cd ../tests/samples && ../run_tests.sh -mjava
	cd ../tests/future && ../run_tests.sh -mjava

test:
	make run-test > test.log

benchmark:
	./run.sh dev.jsonfold.format.Benchmark | tee bench.out

package:
	mvn install -P release -Dgpg.skip=true

verify:
	mvn verify -P release -Dgpg.skip=true

publish:
	GPG_TTY=$$(tty) mvn deploy -P release -DskipTests

world:
	make realclean
	make setup
	make build
	make test
	make benchmark
	make package
	make verify
