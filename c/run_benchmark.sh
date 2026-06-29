#! /bin/sh
perf report --stdio --call-graph=none > summary.out
perf report --stdio --children > callgraph.out
perf annotate --stdio --source > annotate.out
