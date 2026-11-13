"""Desktop float32 reference for SigLIP 2 over the pulled photo sample, from the unmodified Hugging Face model.

Writes (all under raw/, gitignored):
  ref/siglip_fp32_images.npy     [N,768] image embeddings, in manifest order
  ref/siglip_fp32_queries.npy    [Q,768] query embeddings
  inputs/siglip_queries.json     token ids per query (lower-cased, padded to 64)
  inputs/siglip_pixels/{id}.bin  float32 pixel_values [3,224,224] for every PARITY_EVERY-th photo
Also checks that the exported split pieces (fp32 ONNX) reproduce the reference.
"""
import json
import sys
import time

import numpy as np
import torch
from PIL import Image, ImageOps

import siglip_common as s
from queries import QUERIES
from siglip_ort import SiglipOrt

PARITY_EVERY = 6


def main(threads=10):
    torch.set_num_threads(threads)
    ref = s.ROOT / "raw" / "ref"
    inp = s.ROOT / "raw" / "inputs"
    pdir = inp / "siglip_pixels"
    pdir.mkdir(parents=True, exist_ok=True)
    man = json.loads((s.ROOT / "raw/photos/manifest.json").read_text())
    model, proc, tok = s.load_model(), s.load_processor(), s.load_tokenizer()
    ort_fp32 = SiglipOrt("fp32", threads)

    ids = [s.tokenize(tok, q["text"]) for q in QUERIES]
    (inp / "siglip_queries.json").write_text(json.dumps([i.tolist() for i in ids]))
    with torch.no_grad():
        qe = np.stack([s.reference_text(model, i)[0].numpy() for i in ids])
    np.save(ref / "siglip_fp32_queries.npy", qe)
    q_ort = np.stack([ort_fp32.text(i.numpy()) for i in ids])
    print("text split vs HF cosine: min", float((qe * q_ort).sum(1).min()), "mean", float((qe * q_ort).sum(1).mean()))

    out = np.zeros((len(man), 768), np.float32)
    cos = []
    t0 = time.time()
    for n, item in enumerate(man):
        im = ImageOps.exif_transpose(Image.open(s.ROOT / "raw/photos" / item["file"])).convert("RGB")
        pv = proc(images=im, return_tensors="pt")["pixel_values"]
        with torch.no_grad():
            out[n] = s.reference_image(model, pv)[0].numpy()
        if n % PARITY_EVERY == 0:
            pv.numpy()[0].astype("<f4").tofile(pdir / f"{item['id']}.bin")
            cos.append(float(out[n] @ ort_fp32.image(pv.numpy())))
        if n % 25 == 0:
            print(f"{n}/{len(man)} {time.time() - t0:.0f}s", flush=True)
    np.save(ref / "siglip_fp32_images.npy", out)
    print("image split vs HF cosine (every 6th): min", min(cos), "mean", float(np.mean(cos)), "n", len(cos))


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 10)
