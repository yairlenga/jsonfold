#! /bin/bash -uex

rm -f *.cover benchmark.out
rm -rf prof nytprof
mkdir prof
python3 benchmark.py "${@-1000}" > benchmark.out
python3 -m trace --count --coverdir=prof benchmark.py "${@-100}" > prof/count.out
python3 -m cProfile -s cumtime benchmark.py "${@-100}" > prof/cprof-cum.out
python3 -m cProfile -s tottime benchmark.py "${@-100}" > prof/cprof-tot.out
kernprof -z -v benchmark.py "${@-100}" | awk '/ncalls/,/END/ { if (NF >=5 && +$2 == 0 && +$4 == 0 ) next ; } 1' > prof/func_prof.out
kernprof -l -v benchmark.py "${@-100}" > prof/line_prof.out
#python3 -m scalene --cli --cpu-only --profile-only jsonfold benchmark.py "${@-100}" > prof/prof-scalene.out
