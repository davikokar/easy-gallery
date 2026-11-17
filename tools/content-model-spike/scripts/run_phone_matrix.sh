#!/usr/bin/env bash
# Phase-1 matrix: EmbeddingGemma 2 dyn8, nbits4, fp32 at vision budgets 70 and 280 on the phone.
# usage: run_phone_matrix.sh [limit=60] "variant1 variant2 ..." "budget1 budget2"   (needs ANDROID_HOME exported)
# Models of one variant are removed from the phone after its budgets are done.
set -u
export MSYS_NO_PATHCONV=1
HERE="$(cd "$(dirname "$0")" && pwd)"
limit=${1:-60}; variants=${2:-"dyn8 nbits4 fp32"}; budgets=${3:-"70 280"}
D="$ANDROID_HOME/platform-tools/adb.exe -s RFCT703FBKR"
for v in $variants; do
  for b in $budgets; do
    bash "$HERE/run_phone_variant.sh" gemma "$v" "$b" "$limit" 4 3600
  done
  $D shell run-as com.davide.seddio.contentspike rm -rf "files/models/gemma/$v" "files/.markers/model_gemma_$v"
  echo "removed models/gemma/$v from the phone"
done
echo MATRIX_DONE
