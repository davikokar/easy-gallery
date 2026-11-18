"""M4 desktop analysis over the SM-G990B per-face records. No labels are used or needed.

usage: analyse.py <g990b dir> <out dir> [--shown sface=0.45:0.637,openface=0.3:0.5,dlib=0.45:0.6]

Distance per recogniser, as its publisher defines the match test: SFace cosine distance; OpenFace cosine distance (its vectors are unit length, so
this is half the squared L2 distance the OpenFace paper thresholds); dlib Euclidean distance on the raw 128-d descriptor (cosine collapses for dlib:
its descriptors are not zero-centred, so every pair is 0.05 to 0.2 apart).

Outputs (all under the gitignored private/): <out>/summary.json (aggregates, safe to quote in NOTES.md),
<out>/faces.json and <out>/groups_<recogniser>_<threshold>.json (consumed by build_label_page.py and, later, the scoring against labels).
"""
import argparse
import json
import random
from pathlib import Path

import numpy as np
from scipy.cluster.hierarchy import fcluster, linkage
from scipy.spatial.distance import squareform

RECS = ["sface", "openface", "dlib"]
SWEEP = {"sface": [round(x, 2) for x in np.arange(0.05, 0.801, 0.05)], "openface": [round(x, 2) for x in np.arange(0.05, 0.801, 0.05)],
         "dlib": [round(x, 2) for x in np.arange(0.2, 1.001, 0.05)]}
THUMB_MIN_SIDE = 60  # working-image pixels; the thumbnail is the group's medoid among faces at least this big
MIN_SIDE = 40  # faces whose smaller box side is under this many working-image pixels are not clustered (reported separately)
SEEDS = 20
DROP_FRACTION = 0.10


def load_faces(path):
    every = [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]
    for i, f in enumerate(every):
        f["orig"] = i
    faces = [f for f in every if min(f["box"][2], f["box"][3]) >= MIN_SIDE]
    mats = {}
    for r in RECS:
        m = np.array([f["emb"][r] for f in faces], dtype=np.float64)
        mats[r] = m
    return every, faces, mats


def distance_matrix(rec, m):
    if rec == "dlib":
        sq = (m * m).sum(axis=1)
        d = np.sqrt(np.clip(sq[:, None] + sq[None, :] - 2.0 * (m @ m.T), 0.0, None))
    else:
        x = m / np.linalg.norm(m, axis=1, keepdims=True)
        d = np.clip(1.0 - x @ x.T, 0.0, 2.0)
    np.fill_diagonal(d, 0.0)
    return d


def agglomerative(d, idx, t):
    """Average-linkage on the cosine distance restricted to idx (a list of face indices); returns labels aligned with idx."""
    sub = d[np.ix_(idx, idx)]
    z = linkage(squareform(sub, checks=False), method="average")
    return fcluster(z, t, criterion="distance")


def incremental(d, idx, t):
    """Order-dependent: each face joins the cluster with the smallest mean distance to its members if that is below t, else starts one."""
    clusters = []
    labels = np.zeros(len(idx), dtype=int)
    for pos, i in enumerate(idx):
        best, best_d = None, t
        for ci, members in enumerate(clusters):
            md = d[i, members].mean()
            if md < best_d:
                best, best_d = ci, md
        if best is None:
            clusters.append([i])
            labels[pos] = len(clusters) - 1
        else:
            clusters[best].append(i)
            labels[pos] = best
    return labels


METHODS = {"agglomerative_average": agglomerative, "incremental_mean": incremental}


def groups_from_labels(idx, labels):
    out = {}
    for i, lab in zip(idx, labels):
        out.setdefault(int(lab), []).append(int(i))
    return list(out.values())


def offered(groups, image_ids):
    """Decision 40: a group is offered only if its faces come from at least two different images."""
    return [g for g in groups if len({image_ids[i] for i in g}) >= 2]


def thumbnail(group, d, min_side):
    """The group's medoid (smallest mean distance to the other members) among members whose box is at least THUMB_MIN_SIDE; all members if none is."""
    big = [i for i in group if min_side[i] >= THUMB_MIN_SIDE]
    pool = big if big else group
    return int(min(pool, key=lambda i: float(d[i, group].mean())))


def same_group(ref, cand):
    inter = len(set(ref) & set(cand))
    return inter / len(ref) >= 0.5 and inter / len(cand) >= 0.5


def iou(a, b):
    ax2, ay2, bx2, by2 = a[0] + a[2], a[1] + a[3], b[0] + b[2], b[1] + b[3]
    iw = max(0.0, min(ax2, bx2) - max(a[0], b[0]))
    ih = max(0.0, min(ay2, by2) - max(a[1], b[1]))
    inter = iw * ih
    union = a[2] * a[3] + b[2] * b[3] - inter
    return inter / union if union > 0 else 0.0


def detection_agreement(a_faces, b_faces):
    by_img_b = {}
    for f in b_faces:
        by_img_b.setdefault(f["id"], []).append(f)
    by_img_a = {}
    for f in a_faces:
        by_img_a.setdefault(f["id"], []).append(f)
    ious, unmatched_a, unmatched_b, images_count_differs = [], 0, 0, 0
    for img in set(by_img_a) | set(by_img_b):
        a, b = by_img_a.get(img, []), by_img_b.get(img, [])
        if len(a) != len(b):
            images_count_differs += 1
        pairs = sorted(((iou(x["box"], y["box"]), i, j) for i, x in enumerate(a) for j, y in enumerate(b)), reverse=True)
        used_a, used_b = set(), set()
        for v, i, j in pairs:
            if v <= 0 or i in used_a or j in used_b:
                continue
            used_a.add(i)
            used_b.add(j)
            ious.append(v)
        unmatched_a += len(a) - len(used_a)
        unmatched_b += len(b) - len(used_b)
    arr = np.array(ious) if ious else np.array([0.0])
    q = lambda p: float(np.quantile(arr, p))
    return {
        "faces_a": len(a_faces), "faces_b": len(b_faces), "matched": len(ious), "unmatched_a": unmatched_a, "unmatched_b": unmatched_b,
        "images_where_face_count_differs": images_count_differs, "iou_min": float(arr.min()), "iou_p01": q(0.01), "iou_p05": q(0.05),
        "iou_p50": q(0.5), "iou_mean": float(arr.mean()), "share_iou_ge_0.5": float((arr >= 0.5).mean()), "share_iou_ge_0.9": float((arr >= 0.9).mean()),
        "share_iou_eq_1": float((arr >= 0.99999).mean()),
    }


def stability(method, d, t, ref_groups, anchors, n, rng_seed, drop):
    """Per offered group: share of seeds where the anchor's cluster is the 'same' group (majority overlap both ways)."""
    fn = METHODS[method]
    hits = np.zeros(len(ref_groups))
    anchor_set = set(anchors)
    for s in range(SEEDS):
        rng = random.Random(rng_seed * 1000 + s)
        idx = list(range(n))
        if drop:
            idx = [i for i in idx if i in anchor_set or rng.random() >= DROP_FRACTION]
        rng.shuffle(idx)
        labels = fn(d, idx, t)
        where = {}
        for i, lab in zip(idx, labels):
            where.setdefault(int(lab), []).append(i)
        lab_of = {i: int(lab) for i, lab in zip(idx, labels)}
        for gi, (g, a) in enumerate(zip(ref_groups, anchors)):
            cand = where[lab_of[a]]
            if same_group(g, cand):
                hits[gi] += 1
    return hits / SEEDS


def summarise_stability(v):
    if len(v) == 0:
        return {"groups": 0}
    return {"groups": int(len(v)), "mean": float(v.mean()), "share_all_seeds": float((v >= 0.9999).mean()), "share_ge_0.9": float((v >= 0.9).mean()), "min": float(v.min())}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("data")
    ap.add_argument("out")
    ap.add_argument("--shown", default="")
    args = ap.parse_args()
    data, out = Path(args.data), Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    shown = {kv.split("=")[0]: [float(t) for t in kv.split("=")[1].split(":")] for kv in args.shown.split(",") if kv}

    every, faces, mats = load_faces(data / "full.faces.jsonl")
    n = len(faces)
    orig = [f["orig"] for f in faces]
    image_ids = [f["id"] for f in faces]
    min_side = [min(f["box"][2], f["box"][3]) for f in faces]
    summary = {"faces_detected": len(every), "faces_clustered": n, "faces_below_min_side": len(every) - n, "min_side_px": MIN_SIDE, "images_with_faces": len(set(image_ids)), "embedding_norms": {}, "pairwise_distance_quantiles": {}, "sweeps": {}, "shown": {}, "knn_agreement": {}}

    dist = {}
    for r in RECS:
        d = distance_matrix(r, mats[r])
        dist[r] = d
        norms = np.linalg.norm(mats[r], axis=1)
        summary["embedding_norms"][r] = {"min": float(norms.min()), "p50": float(np.median(norms)), "max": float(norms.max())}
        tri = d[np.triu_indices(n, 1)]
        summary["pairwise_distance_quantiles"][r] = {str(p): round(float(np.quantile(tri, p)), 3) for p in (0.001, 0.01, 0.05, 0.25, 0.5)}

    # agreement of nearest neighbours between recognisers: no labels, but says whether they see the same structure
    k = 5
    nn = {r: np.argsort(dist[r] + np.eye(n) * 9, axis=1)[:, :k] for r in RECS}
    for a in range(len(RECS)):
        for b in range(a + 1, len(RECS)):
            ra, rb = RECS[a], RECS[b]
            share = np.mean([len(set(nn[ra][i]) & set(nn[rb][i])) / k for i in range(n)])
            summary["knn_agreement"][f"{ra}-{rb}"] = round(float(share), 3)

    # threshold sweeps
    for r in RECS:
        for method, fn in METHODS.items():
            rows = []
            for t in SWEEP[r]:
                groups = groups_from_labels(list(range(n)), fn(dist[r], list(range(n)), t))
                off = offered(groups, image_ids)
                sizes = sorted((len(g) for g in groups), reverse=True)
                rows.append({"t": t, "clusters": len(groups), "singletons": sum(1 for s in sizes if s == 1), "offered": len(off),
                             "faces_in_offered": sum(len(g) for g in off), "largest": sizes[0], "second": sizes[1] if len(sizes) > 1 else 0})
            summary["sweeps"][f"{r}/{method}"] = rows

    # chosen thresholds -> offered groups, thumbnails, anchor stability, label-page inputs
    (out / "faces.json").write_text(json.dumps([{"idx": f["orig"], "id": f["id"], "k": f["k"], "w": f["w"], "h": f["h"], "box": f["box"], "score": f["score"], "clustered": min(f["box"][2], f["box"][3]) >= MIN_SIDE} for f in every]))
    for r in RECS:
        for t in shown.get(r, []):
            key = f"{r}@{t}"
            summary["shown"][key] = {}
            for method, fn in METHODS.items():
                groups = groups_from_labels(list(range(n)), fn(dist[r], list(range(n)), t))
                off = offered(groups, image_ids)
                anchors = [thumbnail(g, dist[r], min_side) for g in off]
                rnd = random.Random(7)
                random_anchors = [rnd.choice(g) for g in off]
                entry = {"clusters": len(groups), "singletons": sum(1 for g in groups if len(g) == 1), "offered": len(off), "faces_in_offered": sum(len(g) for g in off),
                         "offered_sizes_p50_max": [int(np.median([len(g) for g in off])), max(len(g) for g in off)] if off else [0, 0],
                         "offered_distinct_images_p50": float(np.median([len({image_ids[i] for i in g}) for g in off])) if off else 0}
                for variant, drop in (("shuffle", False), ("shuffle_drop10pct", True)):
                    entry[f"anchor_thumbnail_{variant}"] = summarise_stability(stability(method, dist[r], t, off, anchors, n, 11, drop))
                    entry[f"anchor_random_member_{variant}"] = summarise_stability(stability(method, dist[r], t, off, random_anchors, n, 13, drop))
                summary["shown"][key][method] = entry
                if method == "agglomerative_average":
                    (out / f"groups_{r}_{t}.json").write_text(json.dumps({"rec": r, "threshold": t, "method": method, "groups": [{"faces": [orig[i] for i in g], "thumb": orig[a]} for g, a in zip(off, anchors)],
                                                                          "ungrouped": [orig[i] for i in sorted(set(range(n)) - {i for g in off for i in g})]}))

    # detection repeatability (same phone, same images, same settings) and an fp32-vs-int8 point
    runs = {}
    for tag in ("full", "det2", "det_int8"):
        p = data / f"{tag}.faces.jsonl"
        if p.exists():
            runs[tag] = [json.loads(line) for line in p.read_text().splitlines() if line.strip()]
    if "det2" in runs:
        summary["detection_repeat_full_vs_det2"] = detection_agreement(runs["full"], runs["det2"])
    if "det_int8" in runs:
        summary["detection_fp32_vs_int8"] = detection_agreement(runs["full"], runs["det_int8"])

    (out / "summary.json").write_text(json.dumps(summary, indent=1))
    print(json.dumps(summary, indent=1))


if __name__ == "__main__":
    main()
