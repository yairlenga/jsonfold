#! /bin/sh
prog=$(dirname $0)/jsonfold.exe
case "$*" in
	*--demo*) exec $prog "$@" ; exit $? ;;
esac
jq | $prog "$@"
