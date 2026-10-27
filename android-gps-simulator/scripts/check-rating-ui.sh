#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p ../rating-screenshots
trap 'adb pull /data/local/tmp/rating-screenshots ../rating-screenshots || true' EXIT
./gradlew connectedDebugAndroidTest
adb pull /data/local/tmp/rating-screenshots ../rating-screenshots
adb shell wm size 640x960
adb shell wm density 320
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rork.gpssimulator.ui.rating.PremiumRatingDialogTest#largeFontsRemainScrollable
adb pull /data/local/tmp/rating-screenshots/rating-large-font.png ../rating-screenshots/rating-small-large-font.png
adb shell wm size 1280x720
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rork.gpssimulator.ui.rating.PremiumRatingDialogTest#largeFontsRemainScrollable
adb pull /data/local/tmp/rating-screenshots/rating-large-font.png ../rating-screenshots/rating-landscape-large-font.png
