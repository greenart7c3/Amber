#!/bin/bash

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <version> <appName>"
  exit 1
fi

version=$1
appName=$2

./gradlew clean bundleRelease --stacktrace
./gradlew assembleRelease --stacktrace
rm ~/release/amber-*
rm ~/release/citrine-*
rm ~/release/manifest-*
mv app/build/outputs/bundle/playRelease/app-play-release.aab ~/release/
mv app/build/outputs/bundle/offlineRelease/app-offline-release.aab ~/release/
mv app/build/outputs/bundle/freeRelease/app-free-release.aab ~/release/
mv app/build/outputs/apk/offline/release/app-offline-* ~/release/
mv app/build/outputs/apk/free/release/app-free-* ~/release/
mv app/build/outputs/apk/play/release/app-play-* ~/release/
./gradlew --stop
cd ~/release
./generate_manifest.sh ${version} ${appName}
