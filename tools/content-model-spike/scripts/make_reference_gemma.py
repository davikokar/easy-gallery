"""Desktop float32 reference for EmbeddingGemma 2 over the pulled photo sample.

Writes (all under raw/, gitignored):
  ref/gemma_fp32_b{70,280}.npy   [N,768] image embeddings, in manifest order
  ref/gemma_fp32_queries.npy     [Q,768] query embeddings (prefix 'task: search result | query: ')
  inputs/gemma_queries.json      token ids per query
  inputs/gemma_parity_b{B}/{id}.bin   header int32 P, then float32 pixel_values[P,768], int32 positions[P,2]
"""
import json
import struct
import sys
import time

import numpy as np
from PIL import Image, ImageOps

import gemma_common as g
from gemma_ort import GemmaOrt
from queries import QUERIES

PARITY_EVERY = 6  # every 6th photo of the sample -> 50 of 300
BUDGETS = [int(b) for b in sys.argv[2].split(",")] if len(sys.argv) > 2 else [70, 280]


def main(threads=10):
    ref = g.ROOT / "raw" / "ref"
    inp = g.ROOT / "raw" / "inputs"
    ref.mkdir(parents=True, exist_ok=True)
    inp.mkdir(parents=True, exist_ok=True)
    man = json.loads((g.ROOT / "raw/photos/manifest.json").read_text())
    proc = g.load_processor()
    m = GemmaOrt("fp32", threads)

    ids = [g.tokenize_query(proc.tokenizer, q["text"]).tolist() for q in QUERIES]
    (inp / "gemma_queries.json").write_text(json.dumps(ids))
    qe = np.stack([m.text(np.array(i)) for i in ids])
    np.save(ref / "gemma_fp32_queries.npy", qe)

    for budget in BUDGETS:
        pdir = inp / f"gemma_parity_b{budget}"
        pdir.mkdir(exist_ok=True)
        out = np.zeros((len(man), 768), np.float32)
        t0 = time.time()
        for n, item in enumerate(man):
            im = ImageOps.exif_transpose(Image.open(g.ROOT / "raw/photos" / item["file"])).convert("RGB")
            pv, pos = g.build_image_inputs(proc, im, budget)
            pv, pos = pv.numpy(), pos.numpy().astype(np.int32)
            out[n] = m.image(pv, pos)
            if n % PARITY_EVERY == 0:
                with open(pdir / f"{item['id']}.bin", "wb") as f:
                    f.write(struct.pack("<i", pv.shape[1]))
                    f.write(pv.astype("<f4").tobytes())
                    f.write(pos.astype("<i4").tobytes())
            if n % 20 == 0:
                print(f"budget {budget}: {n}/{len(man)} {time.time() - t0:.0f}s", flush=True)
        np.save(ref / f"gemma_fp32_b{budget}.npy", out)
    print("done")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 10)
