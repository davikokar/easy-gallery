"""Print the per-recogniser measurement table (aggregates only) from the m_*.json runs of one device directory.

usage: summarise_runs.py private/g990b
"""
import json
import sys
from pathlib import Path

d = Path(sys.argv[1])


def load(tag):
    p = d / f"{tag}.json"
    return json.loads(p.read_text()) if p.exists() else None


base = load("m_none")
rows = []
for tag, rec in [("m_none", None), ("m_sface", "sface"), ("m_openface", "openface"), ("m_dlib", "dlib")]:
    r = load(tag)
    if not r:
        continue
    ri = r["recognisers"].get(rec, {}) if rec else {}
    rt = r.get("recogniser_timings", {}).get(rec, {}) if rec else {}
    rows.append({
        "run": tag, "status": r["status"], "model_bytes": ri.get("model_bytes"), "load_ms": ri.get("load_ms"),
        "det_ms_mean": r["detect_ms_per_photo"]["mean"], "det_ms_p50": r["detect_ms_per_photo"]["p50"], "det_ms_p90": r["detect_ms_per_photo"]["p90"],
        "align_ms_mean": rt.get("align_ms_per_face", {}).get("mean"), "embed_ms_mean": rt.get("embed_ms_per_face", {}).get("mean"),
        "embed_ms_p50": rt.get("embed_ms_per_face", {}).get("p50"), "embed_ms_p90": rt.get("embed_ms_per_face", {}).get("p90"),
        "embed_n": rt.get("embed_ms_per_face", {}).get("n"),
        "VmHWM_MB": r["mem_end"]["VmHWM_kB"] / 1024, "VmHWM_after_load_MB": r["mem_after_load"]["VmHWM_kB"] / 1024,
        "faces": r["faces_found"], "images_ok": r["images_ok"], "images_failed": r["images_failed"],
        "wall_s": r["wall_s"], "thermal_end": r["env_end"]["thermal_status"], "batt_c_start": r["env_start"]["battery_temp_c"], "batt_c_end": r["env_end"]["battery_temp_c"],
    })
for row in rows:
    print({k: (round(v, 2) if isinstance(v, float) else v) for k, v in row.items()})
if base:
    print("still_images_total", base["still_images_total"], "mime", base["mediastore_images_by_mime"], "device", base["device"], "threads", base["threads"])
    print("decode_ms", {k: round(v, 1) for k, v in base["decode_ms_per_photo"].items()})
    print("faces_per_image_buckets", base["faces_per_image_buckets"], "small<40px", base["faces_min_side_lt_40px_working"])
