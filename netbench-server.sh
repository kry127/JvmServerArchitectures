#!/usr/bin/env sh

if [ ! -e ./server/build/libs/server.jar ]; then
  echo "Executable jar file not found, generate new one with gradle"
  ./gradlew server:shadowJar
fi

java -jar ./server/build/libs/server.jar $@
