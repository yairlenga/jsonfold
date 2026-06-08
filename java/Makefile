default: build

clean:
	mvn clean

realclean:

config:

setup:

build:
	mvn validate
	mvn compile
	mvn test
	mvn package

test:
	cd ../tests/unit && ./run_tests.sh javagold
	cd ../tests/sample && ./run_tests.sh javagold

benchmark:
	./run.sh dev.jsonfold.format.Benchmark

package:
	mvn package -P release
	mvn install -P release

verify:
	mvn verify -P release

publish:
	mvn deploy -P relesae
