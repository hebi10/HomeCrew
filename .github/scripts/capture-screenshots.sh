#!/usr/bin/env bash
set -u
adb shell cmd uimode night no
set +e
./gradlew --no-daemon connectedDebugAndroidTest --stacktrace
test_exit=$?
set -e

mkdir -p qa-screenshots
source_dir="app/build/outputs/connected_android_test_additional_output"
if [ -d "$source_dir" ]; then
  find "$source_dir" -type f \( -name '*.png' -o -name 'hierarchy-*.xml' \) -print0 |
    while IFS= read -r -d '' item; do
      cp -v "$item" qa-screenshots/
    done
fi

screenshots=$(find qa-screenshots -maxdepth 1 -name '*.png' | wc -l)
echo "Collected $screenshots screenshot PNG(s) from Gradle retained test outputs."
if [ "$test_exit" -ne 0 ]; then
  echo "::error::Android instrumentation failed ($test_exit)."
  exit "$test_exit"
fi
if [ "$screenshots" -lt 7 ]; then
  echo "::error::Expected at least seven main/state QA PNGs, got $screenshots."
  exit 1
fi
