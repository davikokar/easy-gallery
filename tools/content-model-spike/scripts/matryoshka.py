"""Matryoshka truncation cost for EmbeddingGemma, from the desktop float32 reference only (no phone, no model run).

usage (from scripts/): ../.venv/Scripts/python.exe matryoshka.py  -> results/matryoshka.md + .json
For d in 512/256/128: image and query embeddings are cut to d dims and re-normalised, scored against each other, and compared
with the 768-d scoring of the same reference: self cosine of the cut vectors with the full ones (images, queries),
Spearman rank correlation of each query's score list over all images (mean/min over queries), top-8 overlap (mean/min).
Aggregates only.
"""
import json

import numpy as np

import evaluate as ev


def ranks(x):
    return np.argsort(np.argsort(-x, axis=1), axis=1).astype(float)


def spearman(a, b):
    ra, rb = ranks(a), ranks(b)
    ra -= ra.mean(1, keepdims=True)
    rb -= rb.mean(1, keepdims=True)
    return (ra * rb).sum(1) / np.sqrt((ra * ra).sum(1) * (rb * rb).sum(1))


def main():
    q_full = np.load(ev.REF / "gemma_fp32_queries.npy")
    out = {}
    for budget in (70, 280):
        img = np.load(ev.REF / f"gemma_fp32_b{budget}.npy")
        s_full = ev.norm(q_full) @ ev.norm(img).T
        t_full = ev.topk(q_full, img)
        for d in (512, 256, 128):
            qi, ii = ev.trunc(q_full, d), ev.trunc(img, d)
            s = qi @ ii.T
            rho = spearman(s, s_full)
            ic = (ii * ev.norm(img)[:, :d]).sum(1)
            qc = (qi * ev.norm(q_full)[:, :d]).sum(1)
            top_mean, top_min = ev.overlap(ev.topk(qi, ii), t_full)
            out[f"b{budget}_d{d}"] = {
                "n_images": int(len(img)), "n_queries": int(len(q_full)),
                "img_self_cos_mean": float(ic.mean()), "img_self_cos_min": float(ic.min()),
                "qry_self_cos_mean": float(qc.mean()),
                "spearman_mean": float(rho.mean()), "spearman_min": float(rho.min()),
                "top8_mean": top_mean, "top8_min": top_min}
    (ev.RES / "matryoshka.json").write_text(json.dumps(out, indent=1))
    cols = ["n_images", "img_self_cos_mean", "img_self_cos_min", "qry_self_cos_mean", "spearman_mean", "spearman_min",
            "top8_mean", "top8_min"]
    lines = ["| run | " + " | ".join(cols) + " |", "|---|" + "---|" * len(cols)]
    for k, v in out.items():
        lines.append(f"| {k} | " + " | ".join(f"{v[c]:.4f}" if isinstance(v[c], float) else str(v[c]) for c in cols) + " |")
    (ev.RES / "matryoshka.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
