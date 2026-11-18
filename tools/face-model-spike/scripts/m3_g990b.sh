#!/usr/bin/env bash
# M3 on the SM-G990B: one process per measurement run (fresh VmHWM), then the data run with per-face records.
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/.."
. scripts/device.sh
S=RFCT703FBKR
OUT=private/g990b
mkdir -p $OUT
run_job $S m_none && pull_result $S m_none $OUT
for r in sface openface dlib; do
  run_job $S m_$r --es recs $r && pull_result $S m_$r $OUT
done
run_job $S full --es recs sface,openface,dlib --ez dump true && pull_result $S full $OUT && pull_dump $S full $OUT
run_job $S det2 --ez dump true && pull_result $S det2 $OUT && pull_dump $S det2 $OUT
run_job $S det_int8 --es det yunet_int8 --ez dump true && pull_result $S det_int8 $OUT && pull_dump $S det_int8 $OUT
echo M3_G990B_DONE
