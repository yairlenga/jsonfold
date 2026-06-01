#! /bin/sh -ue
D=$(dirname $0)
JAR=$D/jsonfold-cli/target/jsonfold.jar 
case "${1-}" in
	d.j.f.*)
		P1=$1
		shift
		set -- dev.jsonfold.format.${P1#d.j.f.} "$@"
		;;
esac

case "${1-}" in
	dev.jsonfold.*)
		exec java -cp "$JAR" "$@"
		;;
esac
	
exec java -jar "$JAR" "$@"
