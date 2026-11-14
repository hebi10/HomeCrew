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
  find "$source_dir" -type f \( -name '*.png' -o -name '*.xml' \) -print0 |
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
if [ -f app/google-services.json ]; then
  python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree

reports = Path('app/build/outputs/androidTest-results/connected').rglob('*.xml')
cases = [
    case for report in reports for case in ElementTree.parse(report).iter('testcase')
    if case.get('classname') == 'com.hebi.homecrew.qa.FirebaseBootstrapTest'
]
if not cases or any(child.tag in {'skipped', 'failure', 'error'} for case in cases for child in case):
    raise SystemExit('Firebase initialization test did not pass.')
print('Verified HomeCrew Firebase initialization on the Android emulator.')
PY
fi
if [ "$screenshots" -lt 7 ]; then
  echo "::error::Expected at least seven main/state QA PNGs, got $screenshots."
  exit 1
fi
