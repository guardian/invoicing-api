#!/bin/sh
# Compiles to Java classes, builds a jar, and then compiles to a native Linux executable

set -e

sbt clean assembly
docker build -f linuxbuild.dockerfile -t linuxbuild .
docker run -v "$(pwd -P)/target/scala-2.13":/target/scala-2.13 linuxbuild
zip -r -j target/scala-2.13/invoicing-api-native-linux.zip target/scala-2.13/bootstrap