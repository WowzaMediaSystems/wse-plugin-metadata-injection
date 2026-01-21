#!/bin/bash

builddir="${1:-.}"

if [ ! -f "`realpath $builddir`/gradlew" ]; then
	echo "Error: gradlew not found (`realpath $builddir`/gradlew)"
	exit 0
fi

gradle_cmd="${2:-Build}"

echo "${gradle_cmd}ing: `realpath $builddir`"
docker run -it -v `realpath $builddir`:/code -e GRADLECMD=$gradle_cmd wse-builder:local
#docker run -it -v `realpath $builddir`:/code -v `realpath $builddir`/libs:/code/build/libs -e GRADLECMD=$gradle_cmd wowza/wse-builder:latest
