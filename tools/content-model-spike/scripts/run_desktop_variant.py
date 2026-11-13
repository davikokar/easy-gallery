"""Run one converted variant on the desktop (onnxruntime, CPU) over the photo sample and the query set.

usage: run_desktop_variant.py <gemma|siglip> <variant> [budget=280] [limit=300] [threads=8]
Writes results/emb/desktop_<kind>_<variant>[_b<budget>]_{img,qry}.npy and a _time.json of per-image seconds.
"""
import json
import sys
import time

import numpy as np
from PIL import Image, ImageOps

import gemma_common as g
import siglip_common as s
from queries import QUERIES

ROOT = g.ROOT


def main():
    kind, variant = sys.argv[1], sys.argv[2]
    budget = int(sys.argv[3]) if len(sys.argv) > 3 else 280
    limit = int(sys.argv[4]) if len(sys.argv) > 4 else 300
    threads = int(sys.argv[5]) if len(sys.argv) > 5 else 8
    out = ROOT / "results" / "emb"
    out.mkdir(parents=True, exist_ok=True)
    name = f"desktop_{kind}_{variant}" + (f"_b{budget}" if kind == "gemma" else "")
    man = json.loads((ROOT / "raw/photos/manifest.json").read_text())[:limit]

    if kind == "gemma":
        from gemma_ort import GemmaOrt
        m = GemmaOrt(variant, threads)
        proc = g.load_processor()
        qids = [g.tokenize_query(proc.tokenizer, q["text"]).numpy() for q in QUERIES]
        qe = np.stack([m.text(i) for i in qids])

        def embed(im):
            pv, pos = g.build_image_inputs(proc, im, budget)
            return m.image(pv.numpy(), pos.numpy())
    else:
        from siglip_ort import SiglipOrt
        m = SiglipOrt(variant, threads)
        proc, tok = s.load_processor(), s.load_tokenizer()
        qe = np.stack([m.text(s.tokenize(tok, q["text"]).numpy()) for q in QUERIES])

        def embed(im):
            return m.image(proc(images=im, return_tensors="np")["pixel_values"])

    embs, times = [], []
    for n, item in enumerate(man):
        im = ImageOps.exif_transpose(Image.open(ROOT / "raw/photos" / item["file"])).convert("RGB")
        t = time.time()
        embs.append(embed(im))
        times.append(time.time() - t)
        if n % 50 == 0:
            print(f"{name} {n}/{len(man)}", flush=True)
    np.save(out / f"{name}_img.npy", np.stack(embs))
    np.save(out / f"{name}_qry.npy", qe)
    (out / f"{name}_time.json").write_text(json.dumps({"image_seconds": times, "threads": threads}))
    print("done", name, "median image s", float(np.median(times)))


if __name__ == "__main__":
    main()
