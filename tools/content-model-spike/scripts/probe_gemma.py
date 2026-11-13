import time, json
from pathlib import Path
import numpy as np
import torch
from PIL import Image
from sentence_transformers import SentenceTransformer

ROOT = Path(__file__).resolve().parent.parent
t = time.time()
m = SentenceTransformer(str(ROOT / "raw/embeddinggemma-2"), config_kwargs={"audio_config": None},
                        model_kwargs={"torch_dtype": torch.float32})
print("load", time.time() - t)
print(m)
q = m.encode("task: search result | query: a dog", normalize_embeddings=True)
print(q.shape, np.linalg.norm(q))
man = json.loads((ROOT / "raw/photos/manifest.json").read_text())
im = Image.open(ROOT / "raw/photos" / man[0]["file"]).convert("RGB")
print(im.size)
t = time.time()
e = m.encode({"image": im, "text": "<|image|>"}, normalize_embeddings=True)
print("img", e.shape, time.time() - t, float(q @ e))
