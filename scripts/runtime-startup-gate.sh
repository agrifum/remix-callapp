#!/usr/bin/env bash

set -uo pipefail

ADB_BIN="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/platform-tools/adb"
if [ ! -x "${ADB_BIN}" ]; then
  ADB_BIN="$(command -v adb || true)"
fi
if [ -z "${ADB_BIN}" ] || [ ! -x "${ADB_BIN}" ]; then
  echo "::error title=EMULATOR_INFRASTRUCTURE_FAILURE::adb binary not found (checked ANDROID_SDK_ROOT/ANDROID_HOME and PATH)."
  exit 1
fi
GRADLE_LOG="/tmp/connectedDebugAndroidTest.log"
TEST_FILTER="com.example.ExampleInstrumentedTest#mainActivity_startsAndRendersOnboardingEntryPoint,com.example.ExampleInstrumentedTest#mainActivity_showsOnboardingStepIndicator,com.example.ExampleInstrumentedTest#mainActivity_survivesSingleRecreation,com.example.ExampleInstrumentedTest#mainActivity_survivesRotationChange,com.example.MainActivityColdStartInstrumentedTest#mainActivity_coldStartSkipsOnboardingWhenCompleted"

./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="${TEST_FILTER}" 2>&1 | tee "${GRADLE_LOG}"
gradle_status=${PIPESTATUS[0]}

if [ "${gradle_status}" -ne 0 ]; then
  echo "::group::Runtime startup diagnostics"
  "${ADB_BIN}" devices -l || true
  emulator_state=$("${ADB_BIN}" -s emulator-5554 get-state 2>/dev/null || true)
  boot_completed=$("${ADB_BIN}" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  echo "adb emulator state: ${emulator_state:-unavailable}"
  echo "sys.boot_completed: ${boot_completed:-unavailable}"
  "${ADB_BIN}" -s emulator-5554 logcat -d -t 400 || true
  echo "Android test report candidates:"
  ls -R app/build/reports/androidTests 2>/dev/null || true
  ls -R app/build/outputs/androidTest-results 2>/dev/null || true

  if [ "${boot_completed}" != "1" ] || [ "${emulator_state}" != "device" ]; then
    echo "::error title=EMULATOR_INFRASTRUCTURE_FAILURE::Emulator was not ready when runtime-startup failed."
  elif grep -Eq "Task '\\\\' not found|Selection failed|Unknown command-line option|Cannot locate tasks" "${GRADLE_LOG}"; then
    echo "::error title=TEST_BUILD_INVOCATION_FAILURE::Gradle invocation/configuration failed before runtime test execution."
  elif grep -Eq "connectedDebugAndroidTest FAILED|There were failing tests|FAILURES!!!" "${GRADLE_LOG}"; then
    echo "::error title=APP_RUNTIME_TEST_FAILURE::Instrumentation tests executed and reported failures."
  else
    echo "::error title=TEST_BUILD_INVOCATION_FAILURE::connectedDebugAndroidTest failed before a classified runtime assertion failure."
  fi
  echo "::endgroup::"
fi

echo "::group::Runtime startup evidence"
echo "git HEAD: $(git rev-parse HEAD)"
echo "adb path: ${ADB_BIN}"
echo "adb emulator state: $("${ADB_BIN}" -s emulator-5554 get-state 2>/dev/null || echo unavailable)"
echo "sys.boot_completed: $("${ADB_BIN}" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo unavailable)"
echo "instrumentation filter: ${TEST_FILTER}"

if [ -d "app/build/outputs/androidTest-results/connected" ]; then
  total_tests="$(grep -rhoE 'tests="[0-9]+"' app/build/outputs/androidTest-results/connected | cut -d'"' -f2 | awk '{s+=$1} END {print s+0}')"
  total_failures="$(grep -rhoE 'failures="[0-9]+"' app/build/outputs/androidTest-results/connected | cut -d'"' -f2 | awk '{s+=$1} END {print s+0}')"
  echo "instrumentation totals: tests=${total_tests} failures=${total_failures}"
  echo "executed testcases:"
  grep -rhoE '<testcase name="[^"]+" classname="[^"]+"' app/build/outputs/androidTest-results/connected \
    | sed -E 's#<testcase name="([^"]+)" classname="([^"]+)".*#- \2#\1#' \
    | sort -u || true
  ls app/build/outputs/androidTest-results/connected || true
fi
echo "::endgroup::"

exit "${gradle_status}"
