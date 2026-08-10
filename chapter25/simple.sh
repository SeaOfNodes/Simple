#! /bin/sh
here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
release="$here/build/release"
jar="$release/Simple.jar"

if command -v cygpath >/dev/null 2>&1; then
  release=$(cygpath -w "$release")
  jar=$(cygpath -w "$jar")
fi

export SIMPLE_HOME="${SIMPLE_HOME:-$release}"
exec java -jar "$jar" "$@"
