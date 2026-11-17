"""Phase-1 phone report: one row per run in results/device/<tag>.runmeta.json (gitignored folder: it names private photos).

usage (from scripts/): ../.venv/Scripts/python.exe phone_report.py -> results/phone_summary.md + .csv + .json
Aggregates only. Agreement is against raw/ref (float32 desktop reference), computed with evaluate.metrics:
  parity     the 50 desktop-preprocessed tensors (isolates runtime + quantisation, no Android decode/resize)
  mediastore the first N photos decoded and preprocessed on the phone (adds the Android decode/resize path)
A run that died has no result json: its row says so and keeps what the driver recorded (and the query embeddings, if they were written).
The `note` column flags runs whose measurements cannot be trusted: tags ending -fg carry per-sample focus/CPU-set logging; the budget-280 runs
made before that logging ran while the USB-mode dialog held the focus (harness in the background, 3 little cores): see NOTES.md 2026-11-16 20:10.
"""
import csv
import json
import re

import numpy as np

import evaluate as ev

DEV = ev.RES / "device"
NAN = float("nan")


def pct(a, q):
    return float(np.percentile(np.asarray(a, dtype=float), q)) if len(a) else NAN


def stat(a):
    return f"{pct(a, 50):.0f}/{pct(a, 95):.0f}" if len(a) else "-"


def kv(line, key):
    m = re.search(rf"{key}=([0-9.]+)", line or "")
    return float(m.group(1)) if m else NAN


def load_samples(tag):
    f = DEV / f"{tag}.samples.csv"
    rows = list(csv.DictReader(open(f))) if f.exists() else []

    def col(k):
        return [float(r[k]) for r in rows if r.get(k) not in (None, "")]

    return {"samples_n": len(rows), "max_ap_c": max(col("ap_c"), default=NAN),
            "min_mem_avail_kB": min(col("MemAvail_kB"), default=NAN), "max_vmswap_kB": max(col("VmSwap_kB"), default=NAN),
            "max_rss_kB": max(col("VmRSS_kB"), default=NAN), "max_vmhwm_kB": max(col("VmHWM_kB"), default=NAN)}


def read_json(path):
    try:
        d = json.loads(path.read_text())
        return d if isinstance(d, dict) else {}
    except (OSError, ValueError):
        return {}


def read_floats(path, dim):
    if not path.exists() or path.stat().st_size == 0 or path.stat().st_size % (4 * dim):
        return None
    return np.fromfile(path, "<f4").reshape(-1, dim)


CONTAMINATED = {"gemma-nbits4-b280": "background run (USB dialog on top, NOTES 20:10); timed out in the driver, harness finished later",
                "gemma-dyn8-b280": "background run (USB dialog on top, NOTES 20:10); lmkd kill at oom_score_adj 850"}


def note_for(tag, meta):
    n = []
    if tag in CONTAMINATED:
        n.append("CONTAMINATED: " + CONTAMINATED[tag])
    if meta.get("died") == 2:
        n.append("partial_timeout: driver pulled partial files")
    if "samples" not in meta:
        n.append("focus not recorded")
    elif meta.get("samples_focus_not_harness"):
        n.append(f"focus not harness in {meta['samples_focus_not_harness']}/{meta['samples']} samples, {meta.get('back_presses', 0)} BACK presses")
    else:
        n.append(f"foreground all {meta['samples']} samples")
    if tag.endswith("-smoke"):
        n.append("smoke (few photos)")
    return "; ".join(n)


def lmkd_lines(tag):
    f = DEV / f"{tag}.run.log"
    return [l for l in (f.read_text(errors="replace").splitlines() if f.exists() else []) if "lmkd" in l and "contentspike" in l]


def main():
    pos = {item["id"]: n for n, item in enumerate(ev.MAN)}
    rows = []
    for mf in sorted(DEV.glob("*.runmeta.json")):
        meta = read_json(mf)
        tag = meta["tag"]
        r = read_json(DEV / f"{tag}.json")
        if not r.get("tag"):
            r = {}
        kind = tag.split("-")[0]
        variant = tag.split("-")[1]
        mb_ = re.search(r"-b(\d+)", tag)
        budget = int(mb_.group(1)) if mb_ else (224 if kind == "siglip" else 0)
        row = {"tag": tag, "model": kind, "variant": variant, "budget": budget, "limit": meta.get("limit"),
               "threads": meta.get("threads"), "wall_s": meta.get("wall_s"), "died": meta.get("died"),
               "samples_focus_not_harness": meta.get("samples_focus_not_harness"), "back_presses": meta.get("back_presses"),
               "note": note_for(tag, meta)}
        row["status"] = r.get("status") or ("killed" if meta.get("died") == 1 else "timeout" if meta.get("died") == 2 else "no result")
        row["error"] = r.get("error")
        pre, post = meta.get("pre", ""), meta.get("post", "")
        row.update(ap_pre_c=kv(pre, "ap_c"), ap_post_c=kv(post, "ap_c"), thermal_pre=kv(pre, "thermal_status"),
                   thermal_post=kv(post, "thermal_status"), mem_avail_pre_kB=kv(pre, "mem_available_kB"),
                   swap_free_pre_kB=kv(pre, "swap_free_kB"), external_max_vmhwm_kB=meta.get("external_max_VmHWM_kB"))
        row["load_ms"] = r.get("load_total_ms")
        row["load_vision_ms"] = r.get("load_parts_ms", {}).get("vision_session")
        row["load_lm_ms"] = r.get("load_parts_ms", {}).get("lm_session")
        row["peak_pss_sampled_kB"] = r.get("peak_pss_kB_sampled")
        row["vmhwm_end_kB"] = r.get("proc_status_end", {}).get("VmHWM_kB")
        row["rss_after_load_kB"] = r.get("proc_status_after_load", {}).get("VmRSS_kB")
        row.update(load_samples(tag))
        row["lmkd"] = "; ".join(lmkd_lines(tag))[:300]
        row["kills"] = int(any("Reclaim 'com.davide" in l or "Killing" in l for l in lmkd_lines(tag)) or meta.get("died") == 1)
        pre_ms, vis_ms, lm_ms = (r.get(k, []) for k in ("image_decode_preprocess_ms", "image_vision_ms", "image_lm_ms"))
        total = [a + b + c for a, b, c in zip(pre_ms, vis_ms, lm_ms)]
        row["img_n"] = len(total)
        row["img_total_med_p95"] = stat(total)
        row["decode_pre_med_p95"] = stat(pre_ms)
        row["vision_med_p95"] = stat(vis_ms)
        row["lm_med_p95"] = stat(lm_ms)
        row["parity_n"] = len(r.get("parity_ms", []))
        row["parity_med_p95"] = stat(r.get("parity_ms", []))
        qms = r.get("query_ms", [])
        row["query_n"] = len(qms)
        row["query_med_p95"] = stat(qms)
        row["query_first_ms"] = qms[0] if qms else NAN
        dim = r.get("embedding_dim") or 768
        ref_i, ref_q = ev.reference(kind, budget)
        q = read_floats(DEV / f"{tag}_qry.f32", dim)
        if q is not None and len(q) == len(ref_q):
            c = (ev.norm(q) * ev.norm(ref_q)).sum(1)
            row["qry_cos_mean"], row["qry_cos_min"] = float(c.mean()), float(c.min())
        if r.get("status") == "ok":
            if r.get("parity_ids"):
                par = read_floats(DEV / f"{tag}_parity.f32", dim)
                m = ev.metrics(kind, budget, par, q, idx=[pos[int(i)] for i in r["parity_ids"]])
                row.update({"par_n": m["n_images"], "par_cos_mean": m["img_cos_mean"], "par_cos_min": m["img_cos_min"],
                            "par_top8_mean": m["top8_mean"], "par_top8_min": m["top8_min"]})
            if r.get("image_ids"):
                img = read_floats(DEV / f"{tag}_img.f32", dim)
                m = ev.metrics(kind, budget, img, q, idx=[pos[i] for i in r["image_ids"]])
                row.update({"ms_n": m["n_images"], "ms_cos_mean": m["img_cos_mean"], "ms_cos_min": m["img_cos_min"],
                            "ms_top8_mean": m["top8_mean"], "ms_top8_min": m["top8_min"]})
        rows.append(row)

    (ev.RES / "phone_summary.json").write_text(json.dumps(rows, indent=1, default=str))
    keys = []
    for r in rows:
        keys += [k for k in r if k not in keys]
    with open(ev.RES / "phone_summary.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(rows)

    def mb(x):
        return f"{x / 1024:.0f}" if x and x == x else "-"

    def f4(r, k):
        return f"{r[k]:.4f}" if r.get(k) is not None and r.get(k) == r.get(k) else "-"

    perf = ["| run | status | note | kills | wall s | load ms | peak PSS MB (sampled) | VmHWM MB (harness end) | VmHWM MB (host max) | min MemAvail MB during | img N | img total med/p95 ms | decode+pre | vision | LM | parity N | parity med/p95 | qry N | qry med/p95 | AP pre/post/max C | thermal pre/post | MemAvail before MB |",
            "|" + "---|" * 22]
    for r in rows:
        perf.append("| {tag} | {st} | {note} | {kills} | {wall} | {load} | {pss} | {hwm} | {ext} | {mina} | {n} | {tot} | {pre} | {vis} | {lm} | {pn} | {pm} | {qn} | {qm} | {ap} | {th} | {mem} |".format(
            tag=r["tag"], st=r["status"], note=r["note"], kills=r["kills"], wall=r["wall_s"], load=f"{r['load_ms']:.0f}" if r["load_ms"] else "-",
            pss=mb(r["peak_pss_sampled_kB"]), hwm=mb(r["vmhwm_end_kB"]), ext=mb(r["external_max_vmhwm_kB"]),
            mina=mb(r["min_mem_avail_kB"]), n=r["img_n"], tot=r["img_total_med_p95"], pre=r["decode_pre_med_p95"],
            vis=r["vision_med_p95"], lm=r["lm_med_p95"], pn=r["parity_n"], pm=r["parity_med_p95"], qn=r["query_n"],
            qm=r["query_med_p95"], ap=f"{r['ap_pre_c']:.1f}/{r['ap_post_c']:.1f}/{r['max_ap_c']:.1f}",
            th=f"{r['thermal_pre']:.0f}/{r['thermal_post']:.0f}", mem=mb(r["mem_avail_pre_kB"])))
    agree = ["| run | status | qry cos mean/min | parity N | parity cos mean/min | parity top-8 mean/min | MediaStore N | MediaStore cos mean/min | MediaStore top-8 mean/min |",
             "|" + "---|" * 9]
    for r in rows:
        agree.append("| {} | {} | {} | {} | {} | {} | {} | {} | {} |".format(
            r["tag"], r["status"],
            f"{f4(r, 'qry_cos_mean')}/{f4(r, 'qry_cos_min')}" if "qry_cos_mean" in r else "-",
            r.get("par_n", "-"), f"{f4(r, 'par_cos_mean')}/{f4(r, 'par_cos_min')}" if "par_n" in r else "-",
            f"{r['par_top8_mean']:.3f}/{r['par_top8_min']:.3f}" if "par_n" in r else "-",
            r.get("ms_n", "-"), f"{f4(r, 'ms_cos_mean')}/{f4(r, 'ms_cos_min')}" if "ms_n" in r else "-",
            f"{r['ms_top8_mean']:.3f}/{r['ms_top8_min']:.3f}" if "ms_n" in r else "-"))
    text = "\n".join(perf) + "\n\n" + "\n".join(agree) + "\n"
    (ev.RES / "phone_summary.md").write_text(text)
    print(text)
    for r in rows:
        if r["lmkd"]:
            print(r["tag"], "lmkd:", r["lmkd"])


if __name__ == "__main__":
    main()
