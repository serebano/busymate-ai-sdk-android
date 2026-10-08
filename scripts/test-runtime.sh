#!/bin/bash
set -euo pipefail
SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
[[ -n "$SDK_ROOT" ]] || { echo 'ANDROID_SDK_ROOT or ANDROID_HOME is required.' >&2; exit 1; }
RESULTS="$PWD/.runtime-results"
mkdir -p "$RESULTS"
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
TEST_STATUS=0
./gradlew :example-app:connectedDebugAndroidTest --no-daemon || TEST_STATUS=$?
# Retain the real rendered hierarchy and screenshots even when an assertion
# fails; reporting must never replace the instrumentation exit status.
adb pull /sdcard/Android/data/ai.busymate.sample/files/runtime "$RESULTS/screenshots" || {
  [[ "$TEST_STATUS" -ne 0 ]] || TEST_STATUS=1
}
adb shell dumpsys package ai.busymate.sample > "$RESULTS/package-permissions.txt" || {
  [[ "$TEST_STATUS" -ne 0 ]] || TEST_STATUS=1
}
exit "$TEST_STATUS"
