#! /bin/bash -ue

rm -f *.cover benchmark.out
rm -rf prof nytprof
mkdir prof
python3 benchmark.py "${@-1000}" > benchmark.out
python3 -m trace --count --coverdir=prof benchmark.py "${@-100}" > prof/count.out
python3 -m cProfile -s cumtime benchmark.py "${@-100}" > prof/cprof-cum.out
python3 -m cProfile -s tottime benchmark.py "${@-100}" > prof/cprof-tot.out
kernprof -v benchmark.py "${@-100}" > prof/kern_prof.out
kernprof -l -v benchmark.py "${@-100}" > prof/line_prof.out
awk '
/function calls.*in.*/ && $NF == "seconds" { total = $(NF-1) }
NF >= 6 && $1 == "ncalls" && +total > 0 { filter = 1 ; header = $0  }
filter && NF >=6 && !/ncalls/ && +$1<=10 && +$2 < total*0.01 && +$4 < total*0.01 { next ; }
filter && NF >=6 && +$2 > total*0.005 {
	if ( length($1) > 8 ) $0 = sprintf("%8d*", +$1) substr($0, length($1)+1)
	top[++n_top] = $0;
	pct[n_top] = 100*$2/total
}
{ print }
END {
	if ( n_top ) {
		asorti(pct, idx, "@val_num_desc")
		print "Top Functions (self time)", n_top
		print "Percent", header
		for (i=1 ; i<=n_top ; i++ ) print sprintf("%7.1f", pct[idx[i]]), top[idx[i]]
		print "---"
	}
}
' < prof/kern_prof.out > prof/func_prof.out
#python3 -m scalene --cli --cpu-only --profile-only jsonfold benchmark.py "${@-100}" > prof/prof-scalene.out
