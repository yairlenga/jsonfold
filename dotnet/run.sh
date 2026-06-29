#! /bin/sh
SELF=$(readlink -f $0)
exec ${SELF%/*}/cli/JsonFold.Cli/bin/Debug/net8.0/JsonFold.Cli "$@"
