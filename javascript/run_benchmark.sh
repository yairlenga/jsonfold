#! /bin/bash -uex

D=prof
HERE=$(pwd)
rm -f -- prof.out
rm -rf -- prof

mkdir prof
node benchmark.js "${@-1000}" > prof.out
node --expose-gc benchmark.js "${@-1000}" > $D/bench.out
node --expose-gc benchmark.js "${@-1000}" > $D/memory.out
# CPU
node --cpu-prof --cpu-prof-dir=$D --cpu-prof-name=cpu.log --heap-prof --heap-prof-dir=$D --heap-prof-name=heap.log benchmark.js "${@-1000}"
# Heap


(cd $D ; node --prof $HERE/benchmark.js "${@-1000}" ; node --prof-process isolate-*.log > prof.out)
