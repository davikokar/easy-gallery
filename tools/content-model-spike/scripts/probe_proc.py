import json
from pathlib import Path
import torch
from PIL import Image
from transformers import AutoProcessor

ROOT = Path(__file__).resolve().parent.parent
proc = AutoProcessor.from_pretrained(str(ROOT / "raw/embeddinggemma-2"))
man = json.loads((ROOT / "raw/photos/manifest.json").read_text())
im = Image.open(ROOT / "raw/photos" / man[0]["file"]).convert("RGB")
print(im.size)
for b in (70, 280):
    out = proc(images=[[im]], text=["<|image|>"], images_kwargs={"max_soft_tokens": b}, return_tensors="pt")
    print(b, {k: (tuple(v.shape), v.dtype) if hasattr(v, "shape") else v for k, v in out.items()})
    ids = out["input_ids"][0]
    print("ids head", ids[:4].tolist(), "tail", ids[-4:].tolist(), "len", len(ids), "n_image_tok", int((ids == 258880).sum()))
t = proc(text=["task: search result | query: castles"], return_tensors="pt")
print(t["input_ids"].tolist(), t.keys())
print(proc.tokenizer.padding_side)
