"""EmbeddingGemma 2 (text + vision only) split into pieces a mobile runtime can run.

vision : pixel_values [1,P,768] + position_ids [1,P,2]  -> soft tokens [1,P/9,512]
lm     : inputs_embeds [1,S,512]                          -> L2-normalised 768-d embedding [1,768]
table  : token embedding table (host-side gather; it is 134M of the 440M parameters)

The host builds the LM input as [BOS, boi, soft tokens..., eoi, EOS] (images, no padding) or the embedded
query tokens, exactly as the Hugging Face processor lays them out. Padding patches are never fed.
"""
import math
from pathlib import Path

import torch
from torch import nn
from transformers import AutoModel, AutoProcessor

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw" / "embeddinggemma-2"
BOS, BOI, EOI, EOS, IMAGE_TOKEN = 2, 255999, 258882, 1, 258880
QUERY_PREFIX = "task: search result | query: "
NEG = -1e9


def load_model(dtype=torch.float32):
    from transformers import AutoConfig

    config = AutoConfig.from_pretrained(RAW)
    config.audio_config = None
    config._attn_implementation = "eager"
    for sub in (config.text_config, config.vision_config):
        sub._attn_implementation = "eager"
    model = AutoModel.from_pretrained(RAW, config=config, dtype=dtype)
    return model.eval()


def load_processor():
    return AutoProcessor.from_pretrained(RAW)


class VisionPart(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.vt = model.vision_tower
        self.embed_vision = model.embed_vision

    def forward(self, pixel_values, position_ids):
        vt = self.vt
        pad = (position_ids == -1).all(dim=-1)
        x = vt.patch_embedder(pixel_values, position_ids, pad)
        mask = torch.where(pad[:, None, None, :], NEG, 0.0).to(x.dtype)
        cos_sin = vt.encoder.rotary_emb(x, position_ids)
        for layer in vt.encoder.layers:
            x = layer(x, attention_mask=mask, position_embeddings=cos_sin, position_ids=position_ids)
        x = x.masked_fill(pad.unsqueeze(-1), 0.0)
        # 3x3 average pooling by patch position, as Gemma4VisionPooler does (without its static-shape int maths)
        n_out = pixel_values.shape[1] // 9
        pos = position_ids.clamp(min=0)
        max_x = pos[..., 0].max(dim=-1, keepdim=True)[0] + 1
        cell = torch.div(pos, 3, rounding_mode="floor")
        cell = cell[..., 0] + torch.div(max_x, 3, rounding_mode="floor") * cell[..., 1]
        weights = (cell.unsqueeze(-1) == torch.arange(n_out, device=x.device)).to(torch.float32) / 9.0
        pooled = weights.transpose(1, 2) @ x.float()
        pooled = pooled * math.sqrt(vt.config.hidden_size)
        return self.embed_vision(pooled.to(x.dtype))


class LmPart(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.tm = model.language_model

    def forward(self, inputs_embeds):
        tm = self.tm
        seq = inputs_embeds.shape[1]
        position_ids = torch.arange(seq, device=inputs_embeds.device).unsqueeze(0)
        per_layer = tm.ple(inputs_embeds)
        cos_sin = {t: tm.rotary_emb(inputs_embeds, position_ids, t) for t in tm.unique_layer_types}
        x = inputs_embeds
        for i, layer in enumerate(tm.layers):
            t = tm.config.layer_types[i]
            x = layer(x, per_layer[:, :, i, :], attention_mask=None, position_embeddings=cos_sin[t])
        x = tm.embedding_projection(tm.norm(x))
        pooled = x.float().mean(dim=1)
        return pooled / pooled.norm(dim=-1, keepdim=True)


def scaled_table(model):
    tm = model.language_model
    return tm.embed_tokens.weight.detach().float() * tm.embed_tokens.scalar_embed_scale


def special_vectors(table):
    return table[[BOS, BOI, EOI, EOS]].contiguous()


def build_image_inputs(proc, pil_image, budget):
    """Desktop preprocessing with the model's own processor; padding patches stripped."""
    out = proc(images=[[pil_image]], text=["<|image|>"], images_kwargs={"max_soft_tokens": budget}, return_tensors="pt")
    pos = out["image_position_ids"][0]
    real = (pos != -1).all(dim=-1)
    return out["pixel_values"][0][real].unsqueeze(0).contiguous(), pos[real].unsqueeze(0).contiguous()


def image_embedding_torch(vision, lm, table, pixel_values, position_ids):
    soft = vision(pixel_values, position_ids)
    seq = torch.cat([table[BOS][None], table[BOI][None], soft[0], table[EOI][None], table[EOS][None]], dim=0)
    return lm(seq.unsqueeze(0))[0]


def tokenize_query(tokenizer, text):
    return tokenizer(QUERY_PREFIX + text, return_tensors="pt")["input_ids"][0]


def text_embedding_torch(lm, table, ids):
    return lm(table[ids].unsqueeze(0))[0]
