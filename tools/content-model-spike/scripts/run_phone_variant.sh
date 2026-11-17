#!/usr/bin/env bash
# One phone run of the content-spike harness; writes results/device/<tag>.* (gitignored: names private photos).
# usage: run_phone_variant.sh <kind> <variant> <budget> [limit=100] [threads=4] [timeout_s=3000]
# Needs ANDROID_HOME exported. Pushes the model/inputs if absent, waits for the phone to cool, runs, pulls, records state.
# Files go into the app's INTERNAL files dir through `run-as` (the harness is a debug build): files pushed by the adb shell
# into Android/data are owned by uid shell and the app cannot read them.
set -u
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ADB="$ANDROID_HOME/platform-tools/adb.exe"
D="$ADB -s RFCT703FBKR"
DT="timeout 45 $D"   # bounded adb for state reads: a stuck adbd must not hang the driver
PKG=com.davide.seddio.contentspike
RA="run-as $PKG"
kind=$1; variant=$2; budget=$3; limit=${4:-100}; threads=${5:-4}; tmo=${6:-3000}
if [ "$kind" = gemma ]; then tag="$kind-$variant-b$budget"; else tag="$kind-$variant"; fi
tag="$tag${TAG_SUFFIX:-}"   # TAG_SUFFIX (e.g. -fg) keeps a re-run from overwriting an earlier result
OUT="$HERE/results/device"; mkdir -p "$OUT"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/$tag.run.log"; }
push() { # push <local file> <path under files/>
  $D shell $RA mkdir -p "files/$(dirname "$2")"
  $D exec-in "$RA sh -c 'cat > files/$2'" < "$1"
}
pull() { $D exec-out "$RA cat files/$1" > "$2" 2>/dev/null; }
exists() { $D shell "$RA ls files/$1" >/dev/null 2>&1; }
state() { # AP/BAT temp, thermal status, MemAvailable, SwapFree, battery saver, charging
  local t ap bat st mem swap lp plug
  t=$($DT shell dumpsys thermalservice | tr -d '\r')
  ap=$(echo "$t" | grep -m1 'mName=AP,' | sed 's/.*mValue=\([0-9.]*\).*/\1/')
  bat=$(echo "$t" | grep -m1 'mName=BAT,' | sed 's/.*mValue=\([0-9.]*\).*/\1/')
  st=$(echo "$t" | grep -m1 'Thermal Status' | sed 's/.*: //')
  mem=$($DT shell grep MemAvailable /proc/meminfo | tr -d '\r' | awk '{print $2}')
  swap=$($DT shell grep SwapFree /proc/meminfo | tr -d '\r' | awk '{print $2}')
  lp=$($DT shell settings get global low_power | tr -d '\r')
  plug=$($DT shell dumpsys battery | tr -d '\r' | grep -E 'USB powered|AC powered' | tr -d ' ' | paste -sd,)
  echo "ap_c=$ap bat_c=$bat thermal_status=$st mem_available_kB=$mem swap_free_kB=$swap battery_saver=$lp $plug $(fs)"
}

# Foreground/screen state: a background app is moved to cpuset /background (3 little cores) and gets oom_score_adj >= 700.
fs() { # focus=<window> wake=<wakefulness> display=<state>
  local o f w s
  o=$($DT shell 'dumpsys window | grep -m1 mCurrentFocus; dumpsys power | grep -m1 mWakefulness=; dumpsys display | grep -m1 "Display State="' 2>/dev/null | tr -d '\r')
  f=$(echo "$o" | grep -m1 mCurrentFocus | sed 's/.*u0 \([^}]*\)}.*/\1/'); case "$f" in *mCurrentFocus*) f=none ;; esac; w=$(echo "$o" | grep -m1 mWakefulness= | sed 's/.*=//')
  s=$(echo "$o" | grep -m1 'Display State=' | sed 's/.*=//')
  echo "focus=${f:-none} wake=${w:-?} display=${s:-?}"
}

IDLE_AP=${IDLE_AP:-30.8}
log "== $tag limit=$limit threads=$threads"
$D shell am force-stop $PKG
# inputs (small, once)
if ! exists inputs/gemma_queries.json; then
  log "pushing inputs"
  push "$HERE/results/photo_ids.txt" inputs/photo_ids.txt
  push "$HERE/raw/inputs/gemma_queries.json" inputs/gemma_queries.json
  [ -f "$HERE/raw/inputs/siglip_queries.json" ] && push "$HERE/raw/inputs/siglip_queries.json" inputs/siglip_queries.json
fi
if [ "$kind" = gemma ] && ! exists .markers/parity_b$budget; then
  log "pushing parity inputs b$budget"
  for f in "$HERE"/raw/inputs/gemma_parity_b$budget/*.bin; do push "$f" "inputs/gemma_parity_b$budget/$(basename "$f")"; done
  $D shell $RA mkdir -p files/.markers; $D shell $RA touch files/.markers/parity_b$budget
fi
if [ "$kind" = siglip ] && ! exists .markers/siglip_inputs; then
  log "pushing siglip inputs"
  push "$HERE/raw/inputs/siglip_queries.json" inputs/siglip_queries.json
  for f in "$HERE"/raw/inputs/siglip_pixels/*.bin; do push "$f" "inputs/siglip_pixels/$(basename "$f")"; done
  $D shell $RA mkdir -p files/.markers; $D shell $RA touch files/.markers/siglip_inputs
fi
# model present on the phone?
if ! exists .markers/model_${kind}_$variant; then
  log "pushing models/$kind/$variant"
  for f in "$HERE"/models/$kind/$variant/*; do push "$f" "models/$kind/$variant/$(basename "$f")"; done
  $D shell $RA mkdir -p files/.markers; $D shell $RA touch files/.markers/model_${kind}_$variant
  $D shell "$RA ls -l files/models/$kind/$variant" | tee -a "$OUT/$tag.run.log"
fi
# cool down with the screen off (screen on alone holds AP ~7 C above idle): status 0 and AP within 3 C of idle
$D shell input keyevent KEYCODE_SLEEP
for i in $(seq 1 90); do
  s=$(state); ap=$(echo "$s" | sed 's/.*ap_c=\([0-9.]*\).*/\1/'); st=$(echo "$s" | sed 's/.*thermal_status=\([0-9]*\).*/\1/')
  if [ "$st" = 0 ] && awk "BEGIN{exit !($ap <= $IDLE_AP + 3)}"; then break; fi
  log "cooling: $s"; sleep 20
done
$D shell $RA rm -f "files/results/$tag.done" "files/results/$tag.json"
$D shell input keyevent KEYCODE_WAKEUP; $D shell wm dismiss-keyguard; sleep 3
pre=$(state); log "PRE  $pre"
$D logcat -c
start=$(date +%s)
$D shell am start -W -n $PKG/.MainActivity --es kind "$kind" --es variant "$variant" --ei budget "$budget" \
  --ei limit "$limit" --ei threads "$threads" --es tag "$tag" | tr -d '\r' | grep -E 'Status|TotalTime' | tee -a "$OUT/$tag.run.log"
log "START $(fs)"
echo "t_s,pid,VmHWM_kB,VmRSS_kB,RssAnon_kB,RssFile_kB,VmSwap_kB,MemAvail_kB,SwapFree_kB,ap_c,oom_score_adj,cpuset,cpus_allowed,focus,wake,display" > "$OUT/$tag.samples.csv"
seen=0; died=0; maxhwm=0; fg_lost=0; refocus=0; nsamp=0
while :; do
  now=$(date +%s); el=$((now-start))
  pid=$($D shell pidof $PKG | tr -d '\r')
  if [ -n "$pid" ]; then
    seen=1
    st=$($DT shell "cat /proc/$pid/status; grep -E 'MemAvailable|SwapFree' /proc/meminfo; echo oom_adj: \$(cat /proc/$pid/oom_score_adj); echo cpuset: \$(cat /proc/$pid/cpuset)" 2>/dev/null | tr -d '\r')
    g() { echo "$st" | grep -m1 "^$1:" | awk '{print $2}'; }
    hwm=$(g VmHWM); [ -n "$hwm" ] && [ "$hwm" -gt "$maxhwm" ] && maxhwm=$hwm
    apt=$($DT shell dumpsys thermalservice | tr -d '\r' | grep -m1 'mName=AP,' | sed 's/.*mValue=\([0-9.]*\).*/\1/')
    fsv=$(fs); focus=$(echo "$fsv" | sed 's/focus=\([^ ]*\) .*/\1/'); wake=$(echo "$fsv" | sed 's/.*wake=\([^ ]*\) .*/\1/'); disp=$(echo "$fsv" | sed 's/.*display=//')
    echo "$el,$pid,$hwm,$(g VmRSS),$(g RssAnon),$(g RssFile),$(g VmSwap),$(g MemAvailable),$(g SwapFree),$apt,$(g oom_adj),$(g cpuset),$(echo "$st" | grep -m1 Cpus_allowed_list | awk '{print $2}'),$focus,$wake,$disp" >> "$OUT/$tag.samples.csv"
    nsamp=$((nsamp+1))
    case "$focus" in com.davide.seddio.contentspike/*) ;; *) fg_lost=$((fg_lost+1)); log "FOCUS NOT HARNESS at ${el}s: $fsv (oom_adj=$(g oom_adj) cpuset=$(g cpuset))"
      if [ $refocus -lt 3 ] && [ "$focus" != none ]; then refocus=$((refocus+1)); $D shell input keyevent KEYCODE_BACK; log "sent KEYCODE_BACK ($refocus) to dismiss the foreground intruder"; fi ;; esac
  elif [ $seen = 1 ]; then
    if exists results/$tag.done; then break; fi
    died=1; log "PROCESS GONE without .done after ${el}s"; break
  fi
  if exists results/$tag.done; then break; fi
  if [ $el -gt $tmo ]; then log "TIMEOUT after ${el}s (harness left running: results are pulled again below)"; died=2; break; fi
  sleep 3
done
end=$(date +%s)
log "wall=$((end-start))s died=$died max_external_VmHWM_kB=$maxhwm"
post=$(state); log "POST $post"
$D logcat -d -v time > "$OUT/$tag.logcat.full.txt" 2>&1
grep -E "SPIKE|lmkd|am_kill|am_proc_died|Killing|OutOfMemory|signal 9|FATAL" "$OUT/$tag.logcat.full.txt" | grep -v -E "adbd|SurfaceFlinger" > "$OUT/$tag.logcat.txt"
grep -E "lmkd|am_kill|Killing|am_proc_died|am_low_memory" "$OUT/$tag.logcat.full.txt" | grep -i -E "$PKG|contentspike|low_memory" | tee -a "$OUT/$tag.run.log"
rm -f "$OUT/$tag.logcat.full.txt"
for f in "$tag.json" "$tag.done" "${tag}_qry.f32" "${tag}_img.f32" "${tag}_parity.f32"; do pull "results/$f" "$OUT/$f"; done
ls -la "$OUT" | grep "$tag" | tee -a "$OUT/$tag.run.log"
echo "{\"tag\":\"$tag\",\"pre\":\"$pre\",\"post\":\"$post\",\"died\":$died,\"external_max_VmHWM_kB\":$maxhwm,\"wall_s\":$((end-start)),\"limit\":$limit,\"threads\":$threads,\"samples\":$nsamp,\"samples_focus_not_harness\":$fg_lost,\"back_presses\":$refocus}" > "$OUT/$tag.runmeta.json"
