#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export ADB_INSTALL_TIMEOUT=10
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
trap 'adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0' EXIT
adb shell settings put global animator_duration_scale 0
for spec in 'small 640x1136 320 1.0' 'standard 720x1600 320 1.0' 'large 824x1830 320 1.0' 'large-font 640x1136 320 1.6'; do
  read -r label dimensions density font <<< "$spec"
  adb shell wm size "$dimensions"
  adb shell wm density "$density"
  adb shell settings put system font_scale "$font"
  for language in en ar; do
    case_name="$label-$language"
    adb shell am instrument -w -r -e class com.rork.gpssimulator.ui.onboarding.OnboardingLayoutTest \
      -e onboardingLanguage "$language" -e onboardingCase "$case_name" \
      com.rork.gpssimulator.test/androidx.test.runner.AndroidJUnitRunner | tee "/tmp/onboarding-$case_name.log"
    if ! grep -q 'OK (2 tests)' "/tmp/onboarding-$case_name.log"; then exit 1; fi
  done
done
adb shell wm size 720x1600
adb shell settings put system font_scale 1.0
adb shell settings put global animator_duration_scale 1
adb shell am instrument -w -r -e class com.rork.gpssimulator.ui.onboarding.OnboardingAnimationTest \
  com.rork.gpssimulator.test/androidx.test.runner.AndroidJUnitRunner | tee /tmp/onboarding-motion.log
if ! grep -q 'OK (1 test)' /tmp/onboarding-motion.log; then exit 1; fi
mkdir -p ../onboarding-screenshots
adb pull /sdcard/Android/data/com.rork.gpssimulator/files/onboarding-screenshots/. ../onboarding-screenshots/
