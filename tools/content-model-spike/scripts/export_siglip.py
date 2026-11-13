"""Export SigLIP 2 Base 224 to float32 ONNX pieces under models/siglip/fp32/."""
import json
import warnings

import torch

import siglip_common as s

warnings.filterwarnings("ignore")
OUT = s.ROOT / "models" / "siglip" / "fp32"
OUT.mkdir(parents=True, exist_ok=True)

model = s.load_model()
vision, text, table = s.VisionPart(model), s.TextPart(model), s.token_table(model)

with torch.no_grad():
    torch.onnx.export(vision, (torch.rand(1, 3, 224, 224),), str(OUT / "vision.onnx"), input_names=["pixel_values"],
                      output_names=["embedding"], opset_version=18, dynamo=False)
    print("vision exported")
    torch.onnx.export(text, (torch.rand(1, s.TEXT_LEN, 768),), str(OUT / "text.onnx"), input_names=["inputs_embeds"],
                      output_names=["embedding"], opset_version=18, dynamo=False)
    print("text exported")

table.numpy().astype("<f4").tofile(OUT / "embed_table_f32.bin")
(OUT / "meta.json").write_text(json.dumps({
    "model": "google/siglip2-base-patch16-224", "kind": "siglip", "dtype": "fp32", "hidden": 768,
    "vocab": int(table.shape[0]), "text_len": s.TEXT_LEN, "embedding_dim": 768, "image_size": 224,
}, indent=1))
print("done", {p.name: p.stat().st_size for p in OUT.iterdir()})
