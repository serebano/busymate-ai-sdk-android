#!/bin/bash
set -euo pipefail
SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
[[ -n "$SDK_ROOT" ]] || { echo 'ANDROID_SDK_ROOT or ANDROID_HOME is required.' >&2; exit 1; }
MODE=${BUSYMATE_RUNTIME_MODE:-smoke}
case "$MODE" in
  smoke) RESULTS="$PWD/.runtime-results"; mkdir -p "$RESULTS" ;;
  permissions)
    RESULTS="$PWD/.permission-results"
    [[ ! -e "$RESULTS" ]] || { echo 'Existing permission evidence preserved; move it before another run.' >&2; exit 1; }
    python3 scripts/assert-hosted-release.py "$RESULTS/hosted-before.json" ;;
  *) echo 'Unknown runtime mode.' >&2; exit 1 ;;
esac
# The hosted Linux SDK-check runner provides KVM. Never install images locally
# merely to run this script; provide them first or use this repository's CI.
[[ -x "$SDK_ROOT/emulator/emulator" && -d "$SDK_ROOT/system-images/android-35/google_apis/x86_64" ]] || {
  echo 'Install emulator and Android35 Google APIs x86_64 system image first, or use CI.' >&2; exit 1;
}
[[ -w /dev/kvm ]] || { echo 'A writable KVM device is required.' >&2; exit 1; }
# Every adb operation addresses this test's emulator, never a connected phone.
export ANDROID_SERIAL=emulator-5580
if adb devices | awk '{print $1}' | grep -qx "$ANDROID_SERIAL"; then
  echo 'Reserved test emulator port5580 is already in use.' >&2; exit 1
fi
# avdmanager and current emulator releases can otherwise resolve different
# default config directories. Give both tools the same task-owned location.
export ANDROID_AVD_HOME="${RUNNER_TEMP:-/tmp}/busymate-sdk-avd-$$"
mkdir "$ANDROID_AVD_HOME"
AVD_NAME="busymate-sdk-runtime-$$"
printf 'no\n' | avdmanager create avd --name "$AVD_NAME" --package 'system-images;android-35;google_apis;x86_64' --device pixel_7
EMULATOR_PID=''
cleanup() {
  if [[ -n "$EMULATOR_PID" ]]; then kill "$EMULATOR_PID" 2>/dev/null || true; wait "$EMULATOR_PID" 2>/dev/null || true; fi
  avdmanager delete avd --name "$AVD_NAME"
  rmdir "$ANDROID_AVD_HOME"
}
trap cleanup EXIT
"$SDK_ROOT/emulator/emulator" -avd "$AVD_NAME" -port 5580 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$RESULTS/emulator.log" 2>&1 &
EMULATOR_PID=$!
for ((i=0; i<90; i++)); do
  if [[ "$(adb get-state 2>/dev/null || true)" == 'device' ]]; then break; fi
  if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    cat "$RESULTS/emulator.log" >&2
    echo 'Emulator process exited before adb connected.' >&2
    exit 1
  fi
  sleep 2
done
if [[ "$(adb get-state 2>/dev/null || true)" != 'device' ]]; then
  cat "$RESULTS/emulator.log" >&2
  echo 'Emulator did not connect to adb within180seconds.' >&2
  exit 1
fi
for ((i=0; i<120; i++)); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]] && break
  kill -0 "$EMULATOR_PID"
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == '1' ]] || { echo 'Emulator boot failed.' >&2; exit 1; }
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
if [[ "$MODE" == 'permissions' ]]; then
  # Install only on the fresh AVD this script owns. Clear this demo between
  # instrumentation processes; revoking inside a running test would kill it.
  ./gradlew :example-app:assembleDebug :example-app:assembleDebugAndroidTest --no-daemon
  adb install example-app/build/outputs/apk/debug/example-app-debug.apk
  for CASE in dictationGrant dictationDeny voiceGrant voiceDeny; do
    CASE_DIR="$RESULTS/$CASE"
    python3 scripts/assert-hosted-release.py "$CASE_DIR/hosted-before.json"
    adb shell pm clear ai.busymate.sample | tr -d '\r' | tee "$CASE_DIR/reset.txt"
    grep -qx 'Success' "$CASE_DIR/reset.txt"
    TEST_STATUS=0
    ./gradlew :example-app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class="ai.busymate.sample.FirstTapPermissionTest#$CASE" \
      -Pandroid.testInstrumentationRunnerArguments.permissionSuite=1 \
      -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon \
      > "$CASE_DIR/test.log" 2>&1 || TEST_STATUS=$?
    adb pull /sdcard/Android/data/ai.busymate.sample/files/permissions "$CASE_DIR/screenshots" || TEST_STATUS=1
    adb shell dumpsys package ai.busymate.sample > "$CASE_DIR/package-permissions.txt"
    cp -R example-app/build/outputs/androidTest-results "$CASE_DIR/test-results"
    [[ "$TEST_STATUS" -eq 0 ]] || { cat "$CASE_DIR/test.log" >&2; exit "$TEST_STATUS"; }
    python3 - "$CASE_DIR" "$CASE" <<'PY'
from pathlib import Path
import sys,xml.etree.ElementTree as ET
directory=Path(sys.argv[1])
suites=[ET.parse(p).getroot() for p in directory.joinpath('test-results').rglob('TEST-*.xml')]
suites=[s for s in suites if s.get('name') == 'ai.busymate.sample.FirstTapPermissionTest']
if len(suites) != 1: raise SystemExit('Expected one concrete permission test suite')
s=suites[0]
if not (s.get('tests') == '1' and all(s.get(k) == '0' for k in ['failures','errors','skipped'])):
    raise SystemExit(f'Expected exactly one passed, non-skipped permission case: {s.attrib}')
if [t.get('name') for t in s.findall('testcase')] != [sys.argv[2]]: raise SystemExit('Wrong test method')
permission='android.permission.RECORD_AUDIO: granted=' + ('true' if sys.argv[2].endswith('Grant') else 'false')
if permission not in directory.joinpath('package-permissions.txt').read_text(): raise SystemExit('OS permission readback differs')
PY
    python3 scripts/assert-hosted-release.py "$CASE_DIR/hosted-after.json"
  done
  python3 scripts/assert-hosted-release.py "$RESULTS/hosted-after.json"
  exit 0
fi
TEST_STATUS=0
# AGP normally uninstalls both test APKs and removes their external files before
# returning. Keep them only on this disposable emulator until evidence is pulled;
# the cleanup trap then removes the complete task-owned AVD.
./gradlew :example-app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ai.busymate.sample.NativeRuntimeSmokeTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon || TEST_STATUS=$?
# Retain the real rendered hierarchy and screenshots even when an assertion
# fails; reporting must never replace the instrumentation exit status.
adb pull /sdcard/Android/data/ai.busymate.sample/files/runtime "$RESULTS/screenshots" || {
  [[ "$TEST_STATUS" -ne 0 ]] || TEST_STATUS=1
}
adb shell dumpsys package ai.busymate.sample > "$RESULTS/package-permissions.txt" || {
  [[ "$TEST_STATUS" -ne 0 ]] || TEST_STATUS=1
}
exit "$TEST_STATUS"
