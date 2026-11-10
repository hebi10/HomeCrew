#!/usr/bin/env bash
set -u
adb shell cmd uimode night no
set +e
./gradlew --no-daemon connectedDebugAndroidTest --stacktrace
test_exit=$?
mkdir -p qa-screenshots
adb pull /sdcard/Android/data/com.hebi.homecrew/files/screen-qa/. qa-screenshots/
pull_exit=$?
if [ "$test_exit" -ne 0 ]; then
  echo "::error::Android instrumentation failed ($test_exit); screenshots collected when available."
  exit "$test_exit"
fi
if [ "$pull_exit" -ne 0 ] || [ -z "$(find qa-screenshots -maxdepth 1 -name '*.png' -print -quit)" ]; then
  echo "::error::Instrumentation passed but no screenshots were collected."
  exit 1
fi
echo "Screenshot QA captured: $(find qa-screenshots -maxdepth 1 -name '*.png' | wc -l) PNG files"
