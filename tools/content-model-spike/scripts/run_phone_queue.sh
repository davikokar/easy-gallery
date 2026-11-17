#!/usr/bin/env bash
# Sequential phone runs. usage: run_phone_queue.sh "<kind> <variant> <budget> <limit> <tag_suffix|-> <timeout_s>" ...
# Needs ANDROID_HOME exported. Stops the queue if adb stops answering (a hung phone must not be fed more work).
set -u
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="$ANDROID_HOME/platform-tools/adb.exe"
for spec in "$@"; do
  set -- $spec
  kind=$1; variant=$2; budget=$3; limit=$4; suffix=$5; tmo=$6
  [ "$suffix" = - ] && suffix=""
  if ! timeout 20 "$ADB" -s RFCT703FBKR shell echo ok >/dev/null 2>&1; then echo "[$(date +%H:%M:%S)] adb not answering before $kind $variant $budget: queue stopped"; exit 3; fi
  echo "[$(date +%H:%M:%S)] queue: $kind $variant b$budget limit=$limit suffix='$suffix'"
  TAG_SUFFIX="$suffix" bash "$HERE/run_phone_variant.sh" "$kind" "$variant" "$budget" "$limit" 4 "$tmo"
done
echo QUEUE_DONE
