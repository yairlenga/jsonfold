#! /bin/bash -uex

python3 benchmark.py "${@-1000}" > bench.out
python3 -m trace --count benchmark.py "${@-100}" > count.out
python3 -m cProfile -s cumtime benchmark.py "${@-100}" > cprof.out
( echo sort tottime ; echo stats) | python3 -m pstats prof.out > rprof.out
kernprof -i -v benchmark.py "${@-100}" > lprof.out
