"""Export EmbeddingGemma 2 (text+vision) to float32 ONNX pieces under models/gemma/fp32/."""
import json
import sys
import warnings

import numpy as np
import torch

import gemma_common as g

warnings.filterwarnings("ignore")
OUT = g.ROOT / "models" / "gemma" / "fp32"
OUT.mkdir(parents=True, exist_ok=True)

model = g.load_model()
vision, lm, table = g.VisionPart(model), g.LmPart(model), g.scaled_table(model)
proc = g.load_processor()

# Example inputs fix tracing shapes only; P and S stay dynamic.
pv = torch.rand(1, 540, 768)
gx, gy = torch.meshgrid(torch.arange(30), torch.arange(18), indexing="xy")
pos = torch.stack([gx, gy], dim=-1).reshape(1, 540, 2).long()

with torch.no_grad():
    torch.onnx.export(vision, (pv, pos), str(OUT / "vision.onnx"), input_names=["pixel_values", "position_ids"],
                      output_names=["soft_tokens"], dynamic_axes={"pixel_values": {1: "P"}, "position_ids": {1: "P"},
                                                                  "soft_tokens": {1: "N"}},
                      opset_version=18, dynamo=False)
    print("vision exported")
    torch.onnx.export(lm, (torch.rand(1, 40, 512),), str(OUT / "lm.onnx"), input_names=["inputs_embeds"],
                      output_names=["embedding"], dynamic_axes={"inputs_embeds": {1: "S"}}, opset_version=18,
                      dynamo=False)
    print("lm exported")

table.numpy().astype("<f4").tofile(OUT / "embed_table_f32.bin")
g.special_vectors(table).numpy().astype("<f4").tofile(OUT / "special_f32.bin")
(OUT / "meta.json").write_text(json.dumps({
    "model": "google/embeddinggemma-2", "kind": "gemma", "dtype": "fp32", "hidden": 512, "vocab": int(table.shape[0]),
    "special_order": ["BOS", "boi", "eoi", "EOS"], "embedding_dim": 768,
}, indent=1))
print("done", {p.name: p.stat().st_size for p in OUT.iterdir()})
