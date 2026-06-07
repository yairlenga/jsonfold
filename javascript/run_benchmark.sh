#! /bin/bash -uex

rm -f -- bench.out
rm -rf -- prof

mkdir prof
node benchmark.js "${@-1000}" > bench.out
node --expose-gc benchmark.js "${@-1000}" > prof/memory.out

