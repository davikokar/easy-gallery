#!/usr/bin/env bash
# Device helpers for the face spike. Source it: . scripts/device.sh ; then call the functions with a serial.
# Logs carry aggregates only (focus window names, wakefulness); never photo names.
export MSYS_NO_PATHCONV=1
ADB="${LOCALAPPDATA//\\//}/Android/Sdk/platform-tools/adb.exe"
PKG=com.davide.seddio.facespike
SPIKE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -W)"

adbs() { local s="$1"; shift; "$ADB" -s "$s" "$@"; }

# install the harness, grant the media permission, push the models into app-private storage
install_harness() {
  local s="$1"
  adbs "$s" install -r "$SPIKE/harness/app/build/outputs/apk/debug/app-debug.apk" || return 1
  adbs "$s" shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
  adbs "$s" shell rm -rf /data/local/tmp/facespike_models
  adbs "$s" shell mkdir -p /data/local/tmp/facespike_models
  for f in yunet_2023mar.onnx yunet_2023mar_int8.onnx sface_2021dec.onnx sface_2021dec_int8.onnx openface_nn4.small2.v1.t7 dlib_face_recognition_resnet_model_v1.dat; do
    adbs "$s" push "$SPIKE/models/$f" /data/local/tmp/facespike_models/ >/dev/null
  done
  adbs "$s" shell "run-as $PKG sh -c 'mkdir -p files/models && cp /data/local/tmp/facespike_models/* files/models/'"
  adbs "$s" shell rm -rf /data/local/tmp/facespike_models
  adbs "$s" shell "run-as $PKG ls -l files/models"
}

wake() {
  local s="$1"
  adbs "$s" shell input keyevent KEYCODE_WAKEUP
  adbs "$s" shell wm dismiss-keyguard
  sleep 2
  adbs "$s" shell dumpsys power | grep -m1 mWakefulness=
  adbs "$s" shell dumpsys window | grep -m1 mCurrentFocus
}

# run_job <serial> <tag> [am extras...]  e.g. run_job RFCT703FBKR smoke --es recs sface,openface --ei limit 30 --ez dump true
run_job() {
  local s="$1" tag="$2"; shift 2
  adbs "$s" shell am force-stop $PKG
  adbs "$s" shell "run-as $PKG rm -f files/results/$tag.done"
  wake "$s"
  echo "focus-before: $(adbs "$s" shell dumpsys window | grep -m1 mCurrentFocus)"
  adbs "$s" shell am start -n $PKG/.MainActivity --es tag "$tag" "$@"
  local i=0
  until adbs "$s" shell "run-as $PKG test -f files/results/$tag.done" 2>/dev/null; do
    sleep 10; i=$((i+1))
    if [ $((i % 6)) -eq 0 ]; then echo "waiting... $((i*10))s focus=$(adbs "$s" shell dumpsys window | grep -m1 mCurrentFocus | tr -s ' ')"; fi
    if [ $i -gt 720 ]; then echo "TIMEOUT"; return 1; fi
  done
  echo "focus-after: $(adbs "$s" shell dumpsys window | grep -m1 mCurrentFocus)"
}

# pull_result <serial> <tag> <destdir>  (json only; add pull_dump for the SM-G990B)
pull_result() {
  local s="$1" tag="$2" dest="$3"
  mkdir -p "$dest"
  adbs "$s" exec-out "run-as $PKG cat files/results/$tag.json" > "$dest/$tag.json"
}

# pull_dump <serial> <tag> <destdir> : per-face records, per-photo csv and crops. SM-G990B ONLY.
pull_dump() {
  local s="$1" tag="$2" dest="$3"
  mkdir -p "$dest"
  adbs "$s" exec-out "run-as $PKG cat files/results/$tag.faces.jsonl" > "$dest/$tag.faces.jsonl"
  adbs "$s" exec-out "run-as $PKG cat files/results/$tag.photos.csv" > "$dest/$tag.photos.csv"
  adbs "$s" exec-out "run-as $PKG tar -C files/results -cf - $tag" > "$dest/$tag.crops.tar" 2>/dev/null
}
