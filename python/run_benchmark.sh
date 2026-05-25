#! /bin/bash -uex

rm -f *.cover
rm -f count.out cumprof.out totprof.out lprof.out
python3 benchmark.py "${@-1000}" > bench.out
python3 -m trace --count benchmark.py "${@-100}" > count.out
python3 -m cProfile -s cumtime benchmark.py "${@-100}" > cprof-cum.out
python3 -m cProfile -s tottime benchmark.py "${@-100}" > cprof-tot.out
kernprof -l -v benchmark.py "${@-100}" > line_prof.out
kernprof -v benchmark.py "${@-100}" > func_prof.out
