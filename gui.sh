#!/usr/bin/env sh

if [ ! -e ./client/build/libs/client.jar ]; then
  echo "Executable jar file not found, generate new one with gradle"
  ./gradlew client:shadowJar
fi

java -jar ./client/build/libs/client.jar $@
