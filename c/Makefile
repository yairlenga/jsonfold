default: all

CC = gcc -std=c2x
CFLAGS = -Wall -W
CFLAGS = -g -D_DEFAULT_SOURCE -Og -march=native -std=c11 -Wall -Wextra -Werror
LDFLAGS =-lm

all: generated.exe jsonfold.exe

clean:
	rm -f *.o *.exe

jsonfold.o: jsonfold.h
cli.o: jsonfold.h

jsonfold.exe: jsonfold.o cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

generated.o: generated.h
gen_cli.o: generated.h

generated.exe: generated.o gen_cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

