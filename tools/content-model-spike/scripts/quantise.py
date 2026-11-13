"""Quantise the exported ONNX pieces. usage: quantise.py <gemma|siglip> <dyn8|nbits4|nbits8|table8>

dyn8    : onnxruntime dynamic quantisation, int8 weights, activations quantised on the fly (MatMul only)
nbitsN  : MatMulNBits weight-only blocked quantisation (block 32), int8 compute where the kernel supports it
table8  : per-row int8 for the host-side token embedding table (graph files are copied unchanged)
"""
import json
import shutil
import sys
from pathlib import Path

import numpy as np
import onnx

ROOT = Path(__file__).resolve().parent.parent


def dyn8(src, dst):
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quantize_dynamic(str(src), str(dst), weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul"])


def nbits(src, dst, bits):
    from onnxruntime.quantization import matmul_nbits_quantizer as q

    model = onnx.load(str(src))
    algo = q.DefaultWeightOnlyQuantConfig(block_size=32, is_symmetric=True, bits=bits,
                                          accuracy_level=4 if bits == 4 else None)
    quantizer = q.MatMulNBitsQuantizer(model, algo_config=algo)
    quantizer.process()
    quantizer.model.save_model_to_file(str(dst), use_external_data_format=False)


def table8(table_path, out_dir, dim):
    t = np.memmap(table_path, dtype="<f4", mode="r").reshape(-1, dim)
    scales = np.abs(t).max(axis=1) / 127.0
    scales[scales == 0] = 1.0
    q = np.clip(np.round(t / scales[:, None]), -127, 127).astype(np.int8)
    q.tofile(out_dir / "embed_table_i8.bin")
    scales.astype("<f4").tofile(out_dir / "embed_scales_f32.bin")


def main(kind, mode):
    base = ROOT / "models" / kind / "fp32"
    out = ROOT / "models" / kind / mode
    out.mkdir(parents=True, exist_ok=True)
    meta = json.loads((base / "meta.json").read_text())
    graphs = [p.name for p in base.glob("*.onnx")]
    for f in base.iterdir():
        if f.suffix not in (".onnx",) and "table" not in f.name:
            shutil.copy(f, out / f.name)
    if mode == "table8":
        for g in graphs:
            shutil.copy(base / g, out / g)
        table8(base / "embed_table_f32.bin", out, meta["hidden"])
        meta["table"] = "i8"
    else:
        # float table is kept: quantising the graph and the table are separate variables
        shutil.copy(base / "embed_table_f32.bin", out / "embed_table_f32.bin")
        for g in graphs:
            if mode == "dyn8":
                dyn8(base / g, out / g)
            else:
                nbits(base / g, out / g, 4 if mode == "nbits4" else 8)
    meta["dtype"] = mode
    (out / "meta.json").write_text(json.dumps(meta, indent=1))
    print({p.name: p.stat().st_size for p in out.iterdir()})


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
