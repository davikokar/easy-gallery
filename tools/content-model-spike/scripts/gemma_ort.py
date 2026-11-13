"""Desktop onnxruntime driver for the exported Gemma pieces (any variant dir under models/gemma/)."""
import json
import sys
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort

import gemma_common as g

MODELS = g.ROOT / "models" / "gemma"


class GemmaOrt:
    def __init__(self, variant="fp32", threads=8):
        d = MODELS / variant
        self.dir = d
        so = ort.SessionOptions()
        so.intra_op_num_threads = threads
        self.vision = ort.InferenceSession(str(d / "vision.onnx"), so, providers=["CPUExecutionProvider"])
        self.lm = ort.InferenceSession(str(d / "lm.onnx"), so, providers=["CPUExecutionProvider"])
        self.table = np.memmap(d / "embed_table_f32.bin", dtype="<f4", mode="r").reshape(-1, 512)
        sp = np.fromfile(d / "special_f32.bin", dtype="<f4").reshape(4, 512)
        self.bos, self.boi, self.eoi, self.eos = sp

    def image(self, pixel_values, position_ids):
        soft = self.vision.run(None, {"pixel_values": pixel_values.astype(np.float32),
                                      "position_ids": position_ids.astype(np.int64)})[0][0]
        seq = np.concatenate([self.bos[None], self.boi[None], soft, self.eoi[None], self.eos[None]])[None]
        return self.lm.run(None, {"inputs_embeds": seq.astype(np.float32)})[0][0]

    def text(self, ids):
        return self.lm.run(None, {"inputs_embeds": np.asarray(self.table[ids])[None].astype(np.float32)})[0][0]


if __name__ == "__main__":
    import torch
    from PIL import Image

    variant = sys.argv[1] if len(sys.argv) > 1 else "fp32"
    m = GemmaOrt(variant)
    proc = g.load_processor()
    man = json.loads((g.ROOT / "raw/photos/manifest.json").read_text())
    torch_model = g.load_model()
    vision, lm, table = g.VisionPart(torch_model), g.LmPart(torch_model), g.scaled_table(torch_model)
    for text in ["castles", "una spiaggia al tramonto"]:
        ids = g.tokenize_query(proc.tokenizer, text)
        with torch.no_grad():
            ref = g.text_embedding_torch(lm, table, ids).numpy()
        print("text", text, "cos", float(ref @ m.text(ids.numpy())))
    for item in man[:2]:
        im = Image.open(g.ROOT / "raw/photos" / item["file"]).convert("RGB")
        for budget in (70, 280):
            pv, pos = g.build_image_inputs(proc, im, budget)
            t = time.time()
            e = m.image(pv.numpy(), pos.numpy())
            dt = time.time() - t
            with torch.no_grad():
                ref = g.image_embedding_torch(vision, lm, table, pv, pos).numpy()
            print("image", item["id"], "budget", budget, "ort s", round(dt, 2), "cos vs torch", float(ref @ e))
