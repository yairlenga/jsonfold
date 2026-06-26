#! /bin/bash -uex

rm -rf bench.out prof
mkdir prof
perl script/benchmark.pl "${@-1000}" > bench.out
NYTPROF=file=prof/nytprof.out perl -d:NYTProf script/benchmark.pl "${@-100}" > prof/bench-nytprof.out
nytprofhtml -f prof/nytprof.out -out prof
nytprofcsv -f prof/nytprof.out -out prof
