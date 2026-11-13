"""SigLIP 2 Base (224) split into pieces a mobile runtime can run.

vision : pixel_values [1,3,224,224]  -> L2-normalised 768-d image embedding [1,768]
text   : inputs_embeds [1,64,768]    -> L2-normalised 768-d text embedding  [1,768]
table  : token embedding table [256000,768], host-side gather (it is ~196M of the ~375M parameters)

Text follows the model card: lower-cased, padded to max_length 64, no attention mask (the model was trained that way,
the pooled output is the last position's hidden state).
"""
from pathlib import Path

import torch
from torch import nn
from transformers import AutoConfig, AutoModel, AutoProcessor, AutoTokenizer

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw" / "siglip2-base-patch16-224"
TEXT_LEN = 64


def load_model(dtype=torch.float32):
    config = AutoConfig.from_pretrained(RAW)
    config._attn_implementation = "eager"
    for sub in (config.text_config, config.vision_config):
        sub._attn_implementation = "eager"
    return AutoModel.from_pretrained(RAW, config=config, dtype=dtype).eval()


def load_processor():
    return AutoProcessor.from_pretrained(RAW)


def load_tokenizer():
    return AutoTokenizer.from_pretrained(RAW)


class VisionPart(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.vm = model.vision_model

    def forward(self, pixel_values):
        e = self.vm(pixel_values=pixel_values).pooler_output
        return e / e.norm(dim=-1, keepdim=True)


class TextPart(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.tm = model.text_model

    def forward(self, inputs_embeds):
        tm = self.tm
        pos = tm.embeddings.position_embedding.weight[: inputs_embeds.shape[1]]
        x = inputs_embeds + pos
        for layer in tm.encoder.layers:
            x = layer(x, None)
        x = tm.final_layer_norm(x)
        e = tm.head(x[:, -1, :])
        return e / e.norm(dim=-1, keepdim=True)


def token_table(model):
    return model.text_model.embeddings.token_embedding.weight.detach().float()


def tokenize(tokenizer, text):
    ids = tokenizer(text.lower(), padding="max_length", max_length=TEXT_LEN, truncation=True, return_tensors="pt")
    return ids["input_ids"][0]


def reference_image(model, pixel_values):
    e = model.vision_model(pixel_values=pixel_values).pooler_output
    return e / e.norm(dim=-1, keepdim=True)


def reference_text(model, ids):
    e = model.text_model(input_ids=ids.unsqueeze(0)).pooler_output
    return e / e.norm(dim=-1, keepdim=True)
