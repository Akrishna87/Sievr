#!/usr/bin/env bash
# Installs the APK on a running emulator, walks through the main screens, saves screenshots
# and fails if the app crashes. Usage: smoke-test.sh <apk> <out-dir>
set -uo pipefail
apk="$1"; out="$2"; pkg=io.github.akrishna87.sievr
mkdir -p "$out"
adb wait-for-device
adb install -r "$apk" || exit 1
adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -n $pkg/.MainActivity

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$out/$1.png"; }
crashed() { adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: .*$pkg" >/dev/null; }
# Taps the centre of the Nth (default first) view whose text or content description is $1.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local xml; xml=$(adb shell cat /sdcard/ui.xml)
  local b; b=$(echo "$xml" | grep -o "<node[^>]*\(text\|content-desc\)=\"$1\"[^>]*" | sed -n "${2:-1}p" | grep -o 'bounds="[^"]*"' | head -1)
  if [ -z "$b" ]; then echo "  (no '$1' on screen)"; return 1; fi
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | grep -o '[0-9]\+' | tr '\n' ' ')"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

shot 01-welcome 8
tap "I understand"; tap "Allow" || true
# The market data download can take a little while on an emulator.
for i in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -q "Closing data for" && break
  sleep 3
done
shot 02-market 2
for tab in Screens Watchlist Portfolio More; do
  tap "$tab" && shot "03-$(echo $tab | tr A-Z a-z)" 3
done
tap Screens; sleep 2; tap "Magic Formula" && shot 04-magic-formula 4
tap "Back"; sleep 1; tap Market; sleep 2
tap "Search stocks" && sleep 2 && adb shell input text "RELIANCE" && sleep 2 && tap "RELIANCE" 2 && shot 05-stock 4

if crashed; then
  echo "=== The app crashed ==="
  adb logcat -d | grep -A40 "FATAL EXCEPTION" | head -80
  exit 1
fi
echo "No crashes. Screens saved to $out"
