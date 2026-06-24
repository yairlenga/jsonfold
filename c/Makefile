default: all

CC = gcc -std=c2x
CFLAGS = -Wall -W
CFLAGS = -g -D_DEFAULT_SOURCE -Og -march=native -std=c11 -Wall -Wextra -Werror
LDFLAGS =-lm

PROGS = generated.exe jsonfold.exe benchmark.exe

.PHONY: all clean benchmark test realclean config setup run-test

config:

setup:

all: generated.exe jsonfold.exe benchmark.exe

clean:
	rm -f *.o *.exe

benchmark: benchmark.exe
	./benchmark.exe > bench.out
	
run-test:
	./jsonfold.exe --demo
	./benchmark.exe
	cd ../tests/unit && ../run_tests.sh -mc
	cd ../tests/samples && ../run_tests.sh -mc


test: jsonfold.exe
	$(MAKE) run-test > test.log


jsonfold.o: jsonfold.h
cli.o: jsonfold.h

jsonfold.exe: jsonfold.o cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

benchmark.exe: benchmark.o jsonfold.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

generated.o: generated.h
gen_cli.o: generated.h

generated.exe: generated.o gen_cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

