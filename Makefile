default: build

SUBDIRS = python javascript java perl c
# Lifecycle:
# 	config - install external dependecies
#	setup - one time makefile setup
#	build - build the code for local testing
#	test - run local tests
#	benchmark - run benchmark code
#	clean - remove  build/test/benchmark artifacts.
#	package - create deployable package
#	verify - test deployable package
#	publish - not automated - upload code
#	realclean - remove everything, including built package
ACTIONS = config setup clean build test benchmark package verify realclean

.PHONY: default clean build test benchmark

$(ACTIONS): %: %.all

%.go:
	make -C $(word 1, $(subst ., , $*)) -f makefile.dev $(wordlist 2, 99, $(subst ., , $*))

%.all: $(foreach d, $(SUBDIRS), $d.%.go)
	echo "ALL"

