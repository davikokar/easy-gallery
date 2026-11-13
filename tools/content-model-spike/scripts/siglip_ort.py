"""Desktop onnxruntime driver for the exported SigLIP 2 pieces (any variant dir under models/siglip/)."""
import numpy as np
import onnxruntime as ort

import siglip_common as s

MODELS = s.ROOT / "models" / "siglip"


class SiglipOrt:
    def __init__(self, variant="fp32", threads=8):
        d = MODELS / variant
        so = ort.SessionOptions()
        so.intra_op_num_threads = threads
        self.vision = ort.InferenceSession(str(d / "vision.onnx"), so, providers=["CPUExecutionProvider"])
        self.text_s = ort.InferenceSession(str(d / "text.onnx"), so, providers=["CPUExecutionProvider"])
        self.table = np.memmap(d / "embed_table_f32.bin", dtype="<f4", mode="r").reshape(-1, 768)

    def image(self, pixel_values):
        return self.vision.run(None, {"pixel_values": pixel_values.astype(np.float32)})[0][0]

    def text(self, ids):
        return self.text_s.run(None, {"inputs_embeds": np.asarray(self.table[ids])[None].astype(np.float32)})[0][0]
