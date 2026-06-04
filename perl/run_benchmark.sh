#! /bin/bash -uex

rm -rf bench.out nycprof.out prof
mkdir prof
perl script/benchmark.pl "${@-1000}" > bench.out
perl -d:NYTProf script/benchmark.pl "${@-100}" > bench-nytprof.out
nytprofhtml
nytprofcsv
