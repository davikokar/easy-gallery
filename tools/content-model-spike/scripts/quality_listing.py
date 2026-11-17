"""results/quality_for_maintainer.md: top-8 photo ids per query and model, over the same 60 MediaStore photos.

usage (from scripts/): ../.venv/Scripts/python.exe quality_listing.py <tag> [<tag> ...]     tags = results/device/<tag>.*
The output names PRIVATE photos (by MediaStore id): results/ is gitignored, keep it that way and never paste it anywhere.
An id is the MediaStore _ID of an image: content://media/external/images/media/<id> on the phone, and `id` in raw/photos/manifest.json
(which also holds the file name of the pulled sample). Scores are cosine similarity of the on-device query and image embeddings.
"""
import json
import sys

import numpy as np

import evaluate as ev
from queries import QUERIES

DEV = ev.RES / "device"


def section(tag):
    r = json.loads((DEV / f"{tag}.json").read_text())
    dim = r["embedding_dim"]
    img = ev.norm(np.fromfile(DEV / f"{tag}_img.f32", "<f4").reshape(-1, dim))
    qry = ev.norm(np.fromfile(DEV / f"{tag}_qry.f32", "<f4").reshape(-1, dim))
    ids = r["image_ids"]
    score = qry @ img.T
    lines = [f"## {tag}  ({r['kind']} {r['variant']}, budget {r['budget']}, {len(ids)} photos, {len(QUERIES)} queries)", ""]
    for qi, q in enumerate(QUERIES):
        top = np.argsort(-score[qi])[:ev.K]
        lines.append(f"- [{q['lang']}] {q['text']}: " + ", ".join(f"{ids[j]} ({score[qi, j]:.3f})" for j in top))
    return lines + [""]


def main():
    out = ["# Quality listing for the maintainer (PRIVATE: names photos by id)", "",
           "Photo id = MediaStore `_ID`: open it on the phone via `content://media/external/images/media/<id>`; "
           "`raw/photos/manifest.json` maps the same id to the pulled sample file. Same 60 photos for every model. "
           "Format: `id (cosine)`, best first, top 8 per query.", ""]
    for tag in sys.argv[1:]:
        out += section(tag)
    (ev.RES / "quality_for_maintainer.md").write_text("\n".join(out), encoding="utf-8")
    print("wrote", ev.RES / "quality_for_maintainer.md", len(sys.argv) - 1, "models")


if __name__ == "__main__":
    main()
