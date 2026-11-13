"""Check that the split pieces reproduce the SentenceTransformer reference (image + query) in float32."""
import json
import time

import numpy as np
import torch
from PIL import Image
from sentence_transformers import SentenceTransformer

import gemma_common as g

torch.set_num_threads(8)
model = g.load_model()
vision, lm, table = g.VisionPart(model), g.LmPart(model), g.scaled_table(model)
proc = g.load_processor()
man = json.loads((g.ROOT / "raw/photos/manifest.json").read_text())

st = SentenceTransformer(str(g.RAW), config_kwargs={"audio_config": None}, model_kwargs={"torch_dtype": torch.float32})
for text in ["castles", "una spiaggia al tramonto"]:
    ref = st.encode(g.QUERY_PREFIX + text, normalize_embeddings=True)
    ids = g.tokenize_query(proc.tokenizer, text)
    with torch.no_grad():
        mine = g.text_embedding_torch(lm, table, ids).numpy()
    print("text", text, "cos", float(ref @ mine))
for item in man[:3]:
    im = Image.open(g.ROOT / "raw/photos" / item["file"]).convert("RGB")
    for budget in (70, 280):
        ref = st.encode({"image": im, "text": "<|image|>"}, normalize_embeddings=True) if budget == 280 else None
        pv, pos = g.build_image_inputs(proc, im, budget)
        with torch.no_grad():
            t = time.time()
            mine = g.image_embedding_torch(vision, lm, table, pv, pos).numpy()
        print("image", item["id"], "budget", budget, "patches", pv.shape[1], "torch s", round(time.time() - t, 2),
              "cos vs ST" if ref is not None else "", float(ref @ mine) if ref is not None else "")
