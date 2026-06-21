default: build

MODULES = core cli jackson gson

clean:
	mvn clean

realclean:

config:

setup:
	mkdir -vp ${MODULES:%=jsonfold-%/src/main/resources}
	mkdir -vp ${MODULES:%=jsonfold-%/src/test/resources}

	# include validate, compile, test and pacakge
build:
	mvn package

test:
	cd ../tests/unit && ./run_tests.sh java
	cd ../tests/samples && ./run_tests.sh java

benchmark:
	./run.sh dev.jsonfold.format.Benchmark

package:
	mvn install -P release -Dgpg.skip=true

verify:
	mvn verify -P release -Dgpg.skip=true

publish:
	export GPG=$$(tty) mvn deploy -P relesae

