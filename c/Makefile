default: all

CC = gcc -std=c2x

all: generated.exe jsonfold.exe

jsonfold.o: jsonfold.h
cli.o: jsonfold.h

jsonfold.exe: jsonfold.o cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

generated.o: generated.h
gen_cli.o: generated.h

generated.exe: generated.o gen_cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@

