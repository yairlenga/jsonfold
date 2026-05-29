#! /bin/bash -uex

rm -f *.cover
rm -f count.out cumprof.out totprof.out lprof.out

node --expose-gc benchmark.js "${@-1000}" > bench.out


#python/run_benchmark.sh


#python3 benchmark.py "${@-1000}" > bench.out
#python3 -m trace --count benchmark.py "${@-100}" > count.out
#python3 -m cProfile -s cumtime benchmark.py "${@-100}" > cprof-cum.out
#python3 -m cProfile -s tottime benchmark.py "${@-100}" > cprof-tot.out
#kernprof -z -v benchmark.py "${@-100}" | awk '/ncalls/,/END/ { if (NF >=5 && +$2 == 0 && +$4 == 0 ) next ; } 1' > func_prof.out
#kernprof -l -v benchmark.py "${@-100}" > line_prof.out
#python3 -m scalene --cli --cpu-only --profile-only jsonfold benchmark.py "${@-100}" > prof-scalene.out
