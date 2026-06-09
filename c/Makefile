default: all

CC = gcc -std=c2x

all: jsonfold.exe

jsonfold.o: jsonfold.h
cli.o: jsonfold.h

jsonfold.exe: jsonfold.o cli.o
	$(LINK.c) $^ $(LOADLIBES) $(LDLIBS) -o $@
