"""Agreement of converted/on-device embeddings with the float32 desktop reference, and the quality listing.

usage: evaluate.py                -> results/summary.json + results/summary.md
Reference (float32, Hugging Face / desktop onnxruntime fp32): raw/ref/*.npy.
Candidates: results/emb/desktop_*_{img,qry}.npy (desktop quantised) and results/device/<tag>_{img,qry,parity}.f32 + .json.
Metrics per candidate:
  img cos   mean/min over photos of cos(candidate image embedding, reference image embedding)
  qry cos   same over the 24 queries
  top8      mean/min over queries of |top8(candidate scores) & top8(reference scores)| / 8, candidate scores built
            from the candidate's own query and image embeddings
  trunc     (gemma) the same top8 after truncating candidate AND reference to d dims and re-normalising, against the
            reference's own 768-d top8: the Matryoshka cost
"""
import json
from pathlib import Path

import numpy as np

from queries import QUERIES

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "results"
REF = ROOT / "raw" / "ref"
MAN = json.loads((ROOT / "raw/photos/manifest.json").read_text())
K = 8


def norm(x):
    return x / np.linalg.norm(x, axis=-1, keepdims=True)


def topk(q, i):
    return np.argsort(-(norm(q) @ norm(i).T), axis=1)[:, :K]


def overlap(a, b):
    o = np.array([len(set(x) & set(y)) / K for x, y in zip(a, b)])
    return float(o.mean()), float(o.min())


def trunc(x, d):
    return norm(x[:, :d])


def reference(kind, budget):
    if kind == "gemma":
        return np.load(REF / f"gemma_fp32_b{budget}.npy"), np.load(REF / "gemma_fp32_queries.npy")
    return np.load(REF / "siglip_fp32_images.npy"), np.load(REF / "siglip_fp32_queries.npy")


def metrics(kind, budget, cand_i, cand_q, idx=None):
    ref_i, ref_q = reference(kind, budget)
    if idx is not None:
        ref_i = ref_i[idx]
    cos_i = (norm(cand_i) * norm(ref_i)).sum(1)
    cos_q = (norm(cand_q) * norm(ref_q)).sum(1)
    t_ref = topk(ref_q, ref_i)
    m_top, n_top = overlap(topk(cand_q, cand_i), t_ref)
    out = {"n_images": int(len(cand_i)), "img_cos_mean": float(cos_i.mean()), "img_cos_min": float(cos_i.min()),
           "qry_cos_mean": float(cos_q.mean()), "qry_cos_min": float(cos_q.min()),
           "top8_mean": m_top, "top8_min": n_top}
    if kind == "gemma":
        for d in (768, 512, 256, 128):
            a, b = overlap(topk(trunc(cand_q, d), trunc(cand_i, d)), t_ref)
            out[f"trunc{d}_top8_mean"], out[f"trunc{d}_top8_min"] = a, b
    return out


def parse_name(stem):
    parts = stem.replace("desktop_", "").split("_")
    kind, variant = parts[0], parts[1]
    budget = int(parts[2][1:]) if len(parts) > 2 else 0
    return kind, variant, budget


def main():
    rows = {}
    for f in sorted((RES / "emb").glob("desktop_*_img.npy")):
        stem = f.name[:-len("_img.npy")]
        kind, variant, budget = parse_name(stem)
        ci, cq = np.load(f), np.load(RES / "emb" / f"{stem}_qry.npy")
        rows[f"desktop {stem[len('desktop_'):]}"] = metrics(kind, budget, ci, cq, idx=slice(0, len(ci)))
    dev = RES / "device"
    for j in sorted(dev.glob("*.json")) if dev.exists() else []:
        r = json.loads(j.read_text())
        if r.get("status") != "ok":
            continue
        tag, kind, budget = r["tag"], r["kind"], r["budget"]
        dim = r["embedding_dim"]
        q = np.fromfile(dev / f"{tag}_qry.f32", "<f4").reshape(-1, dim)
        ids = r["image_ids"]
        pos = {item["id"]: n for n, item in enumerate(MAN)}
        idx = [pos[i] for i in ids]
        if idx:
            img = np.fromfile(dev / f"{tag}_img.f32", "<f4").reshape(-1, dim)
            rows[f"device {tag} (MediaStore pipeline)"] = metrics(kind, budget, img, q, idx=idx)
        pids = r.get("parity_ids", [])
        if pids:
            par = np.fromfile(dev / f"{tag}_parity.f32", "<f4").reshape(-1, dim)
            ref_i, _ = reference(kind, budget)
            pidx = [pos[int(i)] for i in pids]
            c = (norm(par) * norm(ref_i[pidx])).sum(1)
            rows[f"device {tag} (desktop-preprocessed inputs)"] = {
                "n_images": len(pids), "img_cos_mean": float(c.mean()), "img_cos_min": float(c.min())}
    (RES / "summary.json").write_text(json.dumps(rows, indent=1))
    cols = ["n_images", "img_cos_mean", "img_cos_min", "qry_cos_mean", "top8_mean", "top8_min",
            "trunc512_top8_mean", "trunc256_top8_mean", "trunc128_top8_mean"]
    lines = ["| candidate | " + " | ".join(cols) + " |", "|---|" + "---|" * len(cols)]
    for k, v in rows.items():
        lines.append(f"| {k} | " + " | ".join(f"{v[c]:.4f}" if isinstance(v.get(c), float) else str(v.get(c, "")) for c in cols) + " |")
    (RES / "summary.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
