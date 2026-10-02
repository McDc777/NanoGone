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
wait_gone() { # wait until text $1 is no longer on screen (max $2 seconds)
  for i in $(seq 1 $2); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml | grep -q "text=\"$1\"" || { echo "gone after ${i}s: $1"; return 0; }
    sleep 1
  done; echo "still there: $1"; }
wait_for() { # wait until text $1 appears (max $2 seconds)
  for i in $(seq 1 $2); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml | grep -q "text=\"$1\"" && { echo "found after ${i}s: $1"; return 0; }
    sleep 1
  done; echo "never found: $1"; }
adb install -r out/NanoGone-debug.apk
# Tiny fake deep brain + the PC's answers: the app checks its deep-brain wiring at start.
adb shell mkdir -p /sdcard/Android/data/app.nanogone/files/brains-selftest
adb push tools/testdata/deep_selftest/. /sdcard/Android/data/app.nanogone/files/brains-selftest/ >/dev/null
python3 tools/make_test_photo.py /tmp/test_beach.jpg
# Ultra HDR version (the bin glows), so the test also checks the HDR layer is repaired.
if [ -x /tmp/ultrahdr_app ]; then
  python3 tools/make_uhdr.py /tmp/ultrahdr_app /tmp/test_beach.jpg /tmp/test_beach_hdr.jpg --red-glows && mv /tmp/test_beach_hdr.jpg /tmp/test_beach.jpg
fi
adb push /tmp/test_beach.jpg /sdcard/Pictures/test_beach.jpg
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/test_beach.jpg
sleep 4
adb logcat -c
adb shell am start -n app.nanogone/.MainActivity; wait_for "Choose a photo" 60; sleep 2; shot 01-home
ID=$(adb shell content query --uri content://media/external/images/media --projection _id:_display_name | grep test_beach | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)
echo "media id $ID"
adb shell am start -a android.intent.action.EDIT -d content://media/external/images/media/$ID -t image/jpeg --grant-read-uri-permission -n app.nanogone/.MainActivity
wait_for "Remove" 60; sleep 3; shot 02-editor
# Test 1: magic tap on the bin, then Remove.
read TX TY TT TB <<< "$(find_bounds NanoGone)"
read RX RY RT RB <<< "$(find_bounds Remove)"
# Find the photo area (largest plain view between the panes) and aim at the bin centre (2000, 1490) of 4000 x 3000.
read MIDX MIDY <<< "$(python3 - <<'PY'
import re
xml = open("out/smoke/ui.xml", encoding="utf-8").read()
best = None
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    b = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1))))
    w, h = b[2] - b[0], b[3] - b[1]
    if 'text=""' in n and b[1] > 250 and h < 1700 and w > 800 and h > 600:
        if best is None or w * h > best[2] * best[3]: best = (b[0], b[1], w, h)
x, y, w, h = best
fit = min(w / 4000, h / 3000)
ox = x + (w - 4000 * fit) / 2; oy = y + (h - 3000 * fit) / 2
print(int(ox + 2000 * fit), int(oy + 1490 * fit))
PY
)"
echo "aim $MIDX $MIDY" > $OUT/aim.txt
read KX KY KT KB <<< "$(find_bounds Tap)"
adb shell input tap $KX $KY; sleep 1
START=$(date +%s); adb shell input tap $MIDX $MIDY; sleep 1
wait_gone "Finding its edges" 180; echo "magic tap took $(( $(date +%s) - START ))s" >> $OUT/timing.txt
sleep 2; shot 03-selected
read RX RY RT RB <<< "$(find_bounds Remove)"
adb shell input tap $RX $RY
sleep 1; shot 04-lifting
START=$(date +%s); wait_gone "Lifting the mist" 180; echo "removal took $(( $(date +%s) - START ))s" > $OUT/timing.txt
sleep 2; shot 05-removed
read SX SY ST SB <<< "$(find_bounds Save)"
adb shell input tap $SX $SY; sleep 3; shot 06-save-sheet
read JX JY JT JB <<< "$(find_bounds 'Top-quality JPEG')"
START=$(date +%s); adb shell input tap $JX $JY; wait_for "Saved" 180; echo "save took $(( $(date +%s) - START ))s" >> $OUT/timing.txt; sleep 1; shot 07-saved
adb shell ls -la /sdcard/Pictures/NanoGone/ > $OUT/saved-files.txt 2>&1
# Close the save sheet, then try Find distractions and open Enhance (screenshots only).
adb shell input keyevent 4; sleep 2
read FX FY FT FB <<< "$(find_bounds Find)"
adb shell input tap $FX $FY; sleep 2; shot 08-find-sheet
read DX DY DT DB <<< "$(find_bounds 'Find distractions')"
START=$(date +%s); adb shell input tap $DX $DY; sleep 1
wait_gone "Looking for distractions" 240; echo "find distractions took $(( $(date +%s) - START ))s" >> $OUT/timing.txt
sleep 2; shot 09-found
read EX EY ET EB <<< "$(find_bounds Enhance)"
adb shell input tap $EX $EY; sleep 2; shot 10-enhance-sheet
adb shell input keyevent 4; sleep 1
adb pull /sdcard/Pictures/NanoGone/test_beach_NanoGone.jpg $OUT/saved.jpg
if [ -f $OUT/saved.jpg ]; then python3 tools/check_saved.py /tmp/test_beach.jpg $OUT/saved.jpg > $OUT/check.txt 2>&1; fi
adb logcat -d -s AndroidRuntime:E app.nanogone:* > $OUT/crash.log
adb logcat -d | grep -iE "nanogone|FATAL" | tail -200 > $OUT/logcat.txt
adb logcat -d -s NanoGone:I > $OUT/timing-steps.txt
grep -h "deep selftest\|face fix\|HDR gain map\|face_landmarker\|gfpgan" $OUT/timing-steps.txt $OUT/logcat.txt | sort -u | head -12 >> $OUT/timing.txt
true
