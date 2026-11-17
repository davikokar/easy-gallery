"""Where does the phone's disagreement with the fp32 reference come from? Desktop quantised vs reference vs phone, same 60 photos.

usage (from scripts/): ../.venv/Scripts/python.exe desktop_vs_device.py <variant> [budget=70] [kind=gemma] [tag_suffix=]
needs results/emb/desktop_<kind>_<variant>[_b<budget>]_{img,qry}.npy (run_desktop_variant.py <kind> <variant> <budget> 60) and the phone run.
SigLIP has no budget (pass 224).
cos(desktop,ref)   quantisation cost with the desktop pipeline (PIL preprocess, desktop onnxruntime)
cos(phone,desktop) same quantised model: phone preprocess + phone onnxruntime vs desktop
cos(phone,ref)     what phone_report.py prints for the MediaStore pipeline
Aggregates only.
"""
import json
import sys

import numpy as np

import evaluate as ev

variant = sys.argv[1]
budget = int(sys.argv[2]) if len(sys.argv) > 2 else 70
kind = sys.argv[3] if len(sys.argv) > 3 else "gemma"
suffix = sys.argv[4] if len(sys.argv) > 4 else ""
tag = (f"gemma-{variant}-b{budget}" if kind == "gemma" else f"siglip-{variant}") + suffix
dname = f"desktop_{kind}_{variant}" + (f"_b{budget}" if kind == "gemma" else "")
D = ev.RES / "device"
r = json.loads((D / f"{tag}.json").read_text())
dim = r["embedding_dim"]
pos = {item["id"]: n for n, item in enumerate(ev.MAN)}
idx = [pos[i] for i in r["image_ids"]]
dev_i = np.fromfile(D / f"{tag}_img.f32", "<f4").reshape(-1, dim)
dev_q = np.fromfile(D / f"{tag}_qry.f32", "<f4").reshape(-1, dim)
desk_i = np.load(ev.RES / "emb" / f"{dname}_img.npy")
desk_q = np.load(ev.RES / "emb" / f"{dname}_qry.npy")
assert idx == list(range(len(idx))) and len(desk_i) == len(dev_i), "photo sets differ"
ref_i, ref_q = ev.reference(kind, budget)
ref_i = ref_i[idx]
m_desk = ev.metrics(kind, budget, desk_i, desk_q, idx=idx)
m_dev = ev.metrics(kind, budget, dev_i, dev_q, idx=idx)
c_pd = (ev.norm(dev_i) * ev.norm(desk_i)).sum(1)
cq_pd = (ev.norm(dev_q) * ev.norm(desk_q)).sum(1)
t_desk = ev.topk(desk_q, desk_i)
t_dev = ev.topk(dev_q, dev_i)
o_mean, o_min = ev.overlap(t_dev, t_desk)
out = {"kind": kind, "variant": variant, "budget": budget, "n": len(idx),
       "desktop_vs_ref_img_cos_mean": m_desk["img_cos_mean"], "desktop_vs_ref_img_cos_min": m_desk["img_cos_min"],
       "desktop_vs_ref_qry_cos_mean": m_desk["qry_cos_mean"], "desktop_vs_ref_top8_mean": m_desk["top8_mean"],
       "desktop_vs_ref_top8_min": m_desk["top8_min"],
       "phone_vs_desktop_img_cos_mean": float(c_pd.mean()), "phone_vs_desktop_img_cos_min": float(c_pd.min()),
       "phone_vs_desktop_qry_cos_mean": float(cq_pd.mean()), "phone_vs_desktop_qry_cos_min": float(cq_pd.min()),
       "phone_vs_desktop_top8_mean": o_mean, "phone_vs_desktop_top8_min": o_min,
       "phone_vs_ref_img_cos_mean": m_dev["img_cos_mean"], "phone_vs_ref_img_cos_min": m_dev["img_cos_min"],
       "phone_vs_ref_top8_mean": m_dev["top8_mean"], "phone_vs_ref_top8_min": m_dev["top8_min"]}
(ev.RES / f"desktop_vs_device_{tag}.json").write_text(json.dumps(out, indent=1))
for k, v in out.items():
    print(k, f"{v:.4f}" if isinstance(v, float) else v)
