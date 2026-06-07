default: build

clean: clean.all

build: build.all

test: test.all

benchmark: benchmark.all

%.all:
	(make -C python clean)
	(make -C javascript clean)
	(make -C java clean)
	(make -C perl clean)
