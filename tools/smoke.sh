#!/bin/bash
# Runs inside the Android emulator job. Drives NanoGone like a person and saves screenshots to out/.
set -x
OUT=out/smoke; mkdir -p $OUT
shot() { adb exec-out screencap -p > $OUT/$1.png; }
find_bounds() { # print centre x y of the first node whose text matches $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml $OUT/ui.xml >/dev/null
  python3 - "$1" <<'PY'
import re, sys
xml = open("out/smoke/ui.xml", encoding="utf-8").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r'text="([^"]*)"', n)
    if t and t.group(1) == sys.argv[1]:
        b = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1))))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, b[1], b[3]); break
PY
}
adb install -r out/NanoGone-debug.apk
python3 tools/make_test_photo.py /tmp/test_beach.jpg
adb push /tmp/test_beach.jpg /sdcard/Pictures/test_beach.jpg
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/test_beach.jpg
sleep 4
adb logcat -c
adb shell am start -n app.nanogone/.MainActivity; sleep 6; shot 01-home
ID=$(adb shell content query --uri content://media/external/images/media --projection _id:_display_name | grep test_beach | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)
echo "media id $ID"
adb shell am start -a android.intent.action.EDIT -d content://media/external/images/media/$ID -t image/jpeg --grant-read-uri-permission -n app.nanogone/.MainActivity
sleep 8; shot 02-editor
read TX TY TT TB <<< "$(find_bounds NanoGone)"
read RX RY RT RB <<< "$(find_bounds Remove)"
# The photo sits between the top pane and the bottom pane; the bin is in its middle.
TOPP=$((TB + 40)); BOTP=$((RT - 330))
MIDY=$(((TOPP + BOTP) / 2)); MIDX=540
adb shell input swipe $MIDX $((MIDY - 40)) $MIDX $((MIDY + 40)) 700
sleep 2; shot 03-selected
read RX RY RT RB <<< "$(find_bounds Remove)"
adb shell input tap $RX $RY
sleep 1; shot 04-lifting
sleep 25; shot 05-removed
read SX SY ST SB <<< "$(find_bounds Save)"
adb shell input tap $SX $SY; sleep 3; shot 06-save-sheet
read JX JY JT JB <<< "$(find_bounds 'Top-quality JPEG')"
adb shell input tap $JX $JY; sleep 25; shot 07-saved
adb shell ls -la /sdcard/Pictures/NanoGone/ > $OUT/saved-files.txt 2>&1
adb pull /sdcard/Pictures/NanoGone/test_beach_NanoGone.jpg $OUT/saved.jpg
if [ -f $OUT/saved.jpg ]; then python3 tools/check_saved.py /tmp/test_beach.jpg $OUT/saved.jpg > $OUT/check.txt 2>&1; fi
adb logcat -d -s AndroidRuntime:E app.nanogone:* > $OUT/crash.log
adb logcat -d | grep -iE "nanogone|FATAL" | tail -200 > $OUT/logcat.txt
true
