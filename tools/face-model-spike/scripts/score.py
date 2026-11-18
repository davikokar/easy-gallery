"""M5: score each recogniser's offered groups and albums against the maintainer's labels.

usage: score.py <g990b dir> <labels.json> <out dir> [--pick sface=0.45:0.637:0.5,openface=0.3:0.5:0.4,dlib=0.45:0.6:0.5]

Truth: label text = person (compared ignoring case and surrounding space), `!` = not a face, absent or empty = skipped (excluded).
Persons are renamed P1, P2, ... by descending image count inside this process; no name reaches any output file or the console.
Clustering is the order-independent one from M4 (average linkage). Only the faces M4 clusters are scored (box side >= 40 px).

Writes <out>/m5_scores.json (aggregates only, under the gitignored private/) and prints markdown tables.
"""
import argparse
import json
from collections import Counter, defaultdict
from itertools import combinations
from pathlib import Path

import numpy as np

import analyse as A

SWEEP = {
    "sface": [0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.637],
    "openface": [0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50],
    "dlib": [0.40, 0.45, 0.50, 0.55, 0.60],
}
STRICT = {"sface": 0.45, "openface": 0.30, "dlib": 0.45}
PUBLISHER = {"sface": 0.637, "openface": 0.50, "dlib": 0.60}
BASE_RECOGNISER, BASE_T = "sface", 0.55  # the partition the maintainer labelled from
REGULAR_IMAGES = 5  # "regulars": persons with at least this many images in the truth; the 2-image persons make every median trivially 1.0


def clean(name):
    return " ".join(name.split()).casefold()


def load_truth(labels_path, faces):
    doc = json.loads(Path(labels_path).read_text(encoding="utf-8"))
    entries = {e["idx"]: e for e in doc["labels"]}
    cleaned = []
    for f in faces:
        e = entries.get(f["orig"])
        text = clean(e["name"]) if e else ""
        cleaned.append(None if not text else ("!" if text == "!" else text))
    images_of = defaultdict(set)
    for f, c in zip(faces, cleaned):
        if c not in (None, "!"):
            images_of[c].add(f["id"])
    order = sorted(images_of, key=lambda p: (-len(images_of[p]), p))
    pid = {p: f"P{k + 1}" for k, p in enumerate(order)}
    truth = [pid.get(c, c) for c in cleaned]
    return doc, entries, truth


def stats(values):
    if not values:
        return None
    v = sorted(values)
    n = len(v)
    median = v[n // 2] if n % 2 else (v[n // 2 - 1] + v[n // 2]) / 2
    return {"n": n, "median": round(median, 3), "min": round(v[0], 3), "mean": round(sum(v) / n, 3)}


def prf(counts, truth_size):
    conf, wrong, unknown = counts["confirmed"], counts["wrong"], counts["unknown"]
    p = conf / (conf + wrong) if conf + wrong else None
    r = conf / truth_size if truth_size else None
    return p, r


def evaluate(groups, thumbs, c):
    """Everything on the brief's list for one clustering: groups are lists of local face indices, thumbs their thumbnail faces."""
    truth, image_ids, truth_images = c["truth"], c["image_ids"], c["truth_images"]
    recs = []
    for g, th in zip(groups, thumbs):
        cnt = Counter(truth[i] for i in g if truth[i] not in (None, "!"))
        labelled = sum(cnt.values())
        top = cnt.most_common()
        majority = None
        if labelled:
            best = max(cnt.values())
            tied = sorted(p for p, v in cnt.items() if v == best)
            majority = truth[th] if truth[th] in tied else tied[0]
        recs.append({"faces": g, "thumb": th, "cnt": cnt, "labelled": labelled, "majority": majority,
                     "share": (cnt[majority] / labelled) if labelled else None, "evaluable": labelled >= 2,
                     "bang": sum(1 for i in g if truth[i] == "!"), "skipped": sum(1 for i in g if truth[i] is None)})
    ev = [r for r in recs if r["evaluable"]]
    m = {"offered": len(recs), "faces_in_offered": sum(len(r["faces"]) for r in recs),
         "labelled_faces_in_offered": sum(r["labelled"] for r in recs), "evaluable": len(ev), "not_evaluable": len(recs) - len(ev)}
    # 1
    m["persons_covered_by_majority"] = len({r["majority"] for r in ev})
    # thumbnail-face person: what the user sees
    thumb_person = [truth[r["thumb"]] if truth[r["thumb"]] not in (None, "!") else None for r in recs]
    m["distinct_persons_on_thumbnails"] = len({p for p in thumb_person if p})
    # 2
    cands = defaultdict(list)
    for r, p in zip(recs, thumb_person):
        if p:
            cands[p].append(r)
    t2 = c["t2"]
    found = [p for p in t2 if p in cands]
    m["t2_persons"] = len(t2)
    m["t2_found"] = len(found)
    m["t2_missing"] = len(t2) - len(found)
    maj_persons = {r["majority"] for r in ev}
    m["t2_found_by_majority"] = len([p for p in t2 if p in maj_persons])
    # 3
    multi = {p: v for p, v in cands.items() if len(v) > 1}
    m["split_persons"] = len(multi)
    m["split_extra_thumbnails"] = sum(len(v) - 1 for v in multi.values())
    m["persons_on_thumbnails"] = len(cands)
    # 4
    m["mixed_lt_1"] = sum(1 for r in ev if r["share"] < 1.0)
    m["mixed_lt_0.8"] = sum(1 for r in ev if r["share"] < 0.8)
    m["groups_with_non_face"] = sum(1 for r in recs if r["bang"])
    m["groups_no_person_labelled"] = sum(1 for r in recs if r["labelled"] == 0)
    # 5
    m["thumb_is_majority_person"] = sum(1 for r in ev if truth[r["thumb"]] == r["majority"])
    m["thumb_other_person"] = sum(1 for r in ev if truth[r["thumb"]] not in (None, "!", r["majority"]))
    m["thumb_non_face"] = sum(1 for r in recs if truth[r["thumb"]] == "!")
    m["thumb_unlabelled"] = sum(1 for r in recs if truth[r["thumb"]] is None)
    # 6
    for n in (3, 5):
        m[f"strangers_lt_{n}_images"] = sum(1 for r in ev if len(truth_images[r["majority"]]) < n)

    # 7 single-person albums
    def album_images(group):
        return sorted({image_ids[i] for i in group})

    def single_counts(p, r):
        gset = set(r["faces"])
        counts = Counter()
        for img in album_images(r["faces"]):
            fs = [i for i in c["faces_by_image"][img] if i in gset]
            ok = any(truth[i] == p for i in fs)
            counts["confirmed" if ok else ("unknown" if any(truth[i] is None for i in fs) else "wrong")] += 1
        return counts

    def holds(p, r):
        return len({image_ids[i] for i in r["faces"] if truth[i] == p})

    prec, rec, worst, per_person = [], [], [], {}
    sum_conf = sum_wrong = sum_truth = unknown_total = 0
    chosen = {}
    for p in found:
        # the group a user would pick: the one holding most of this person's images, then the purest
        chosen[p] = max(cands[p], key=lambda r: (holds(p, r), r["share"] or 0.0))
        counts = single_counts(p, chosen[p])
        pr, rc = prf(counts, len(truth_images[p]))
        if pr is not None:
            prec.append(pr)
        rec.append(rc)
        per_person[p] = (pr, rc)
        sum_conf += counts["confirmed"]
        sum_wrong += counts["wrong"]
        sum_truth += len(truth_images[p])
        unknown_total += counts["unknown"]
        if len(cands[p]) > 1:
            worst.append(min(prf(single_counts(p, r), len(truth_images[p]))[1] for r in cands[p]))
    regulars = [p for p in t2 if len(truth_images[p]) >= REGULAR_IMAGES]
    m["regulars"] = {"persons": len(regulars), "found": len([p for p in regulars if p in cands]),
                     "precision": stats([per_person[p][0] for p in regulars if p in per_person and per_person[p][0] is not None]),
                     "recall": stats([per_person[p][1] for p in regulars if p in per_person]),
                     "recall_missing_as_zero": stats([per_person[p][1] if p in per_person else 0.0 for p in regulars]),
                     "per_person": {p: {"images": len(truth_images[p]), "thumbnails": len(cands.get(p, [])),
                                        "precision": None if p not in per_person or per_person[p][0] is None else round(per_person[p][0], 3),
                                        "recall": None if p not in per_person else round(per_person[p][1], 3)} for p in regulars}}
    m["single"] = {"persons": len(found), "precision": stats(prec), "recall": stats(rec),
                   "micro_precision": round(sum_conf / (sum_conf + sum_wrong), 3) if sum_conf + sum_wrong else None,
                   "micro_recall": round(sum_conf / sum_truth, 3) if sum_truth else None, "unknown_images": unknown_total,
                   "wrong_images": sum_wrong, "confirmed_images": sum_conf,
                   "recall_worst_group": stats(worst), "persons_with_several_groups": len(worst),
                   "recall_mean_missing_as_zero": round(sum(rec) / len(t2), 3) if t2 else None,
                   "persons_precision_1": sum(1 for x in prec if x >= 0.9999), "persons_recall_1": sum(1 for x in rec if x >= 0.9999)}

    # 8 two-person albums
    pair_prec, pair_rec = [], []
    pc = pw = pt = pu = 0
    buildable = 0
    for (p, q), imgs in c["pair_truth"].items():
        if len(imgs) < 2:
            continue
        if p not in chosen or q not in chosen:
            continue
        buildable += 1
        gp, gq = set(chosen[p]["faces"]), set(chosen[q]["faces"])
        counts = Counter()
        for img in {image_ids[i] for i in gp} & {image_ids[i] for i in gq}:
            fp = [i for i in c["faces_by_image"][img] if i in gp]
            fq = [i for i in c["faces_by_image"][img] if i in gq]
            if chosen[p] is chosen[q] and len(fp) < 2:
                continue
            ok = img in imgs
            counts["confirmed" if ok else ("unknown" if any(truth[i] is None for i in fp + fq) else "wrong")] += 1
        pr, rc = prf(counts, len(imgs))
        if pr is not None:
            pair_prec.append(pr)
        pair_rec.append(rc)
        pc += counts["confirmed"]
        pw += counts["wrong"]
        pt += len(imgs)
        pu += counts["unknown"]
    qualifying = sum(1 for (p, q), imgs in c["pair_truth"].items() if len(imgs) >= 2)
    m["pairs"] = {"qualifying": qualifying, "buildable": buildable, "precision": stats(pair_prec), "recall": stats(pair_rec),
                  "micro_precision": round(pc / (pc + pw), 3) if pc + pw else None, "micro_recall": round(pc / pt, 3) if pt else None,
                  "unknown_images": pu, "empty_or_zero_recall": sum(1 for x in pair_rec if x == 0), "recall_mean_unbuildable_as_zero": round(sum(pair_rec) / qualifying, 3) if qualifying else None}
    return m


def fmt(s, key="median"):
    return "-" if s is None else f"{s[key]:.2f}"


def curve_row(t, m):
    s, p = m["single"], m["pairs"]
    return (f"| {t} | {m['offered']} | {m['distinct_persons_on_thumbnails']} | {m['t2_found']}/{m['t2_persons']} | "
            f"{m['split_persons']}/{m['split_extra_thumbnails']} | {m['mixed_lt_1']}/{m['mixed_lt_0.8']} of {m['evaluable']} | {m['groups_with_non_face']} | "
            f"{m['thumb_is_majority_person']}/{m['evaluable']} | {m['strangers_lt_3_images']}/{m['strangers_lt_5_images']} | "
            f"{fmt(s['precision'])}/{fmt(s['precision'], 'min')} | {fmt(s['recall'])}/{fmt(s['recall'], 'min')} | {s['micro_precision']}/{s['micro_recall']} | "
            f"{fmt(s['recall_worst_group'])} | {p['buildable']}/{p['qualifying']} | {fmt(p['precision'])}/{fmt(p['precision'], 'min')} | {fmt(p['recall'])}/{fmt(p['recall'], 'min')} |")


def compact_row(t, m):
    pp = m["regulars"]["per_person"]
    s = m["single"]
    return (f"| {t} | {m['offered']} | {m['t2_found']}/{m['t2_persons']} | {m['split_persons']}/{m['split_extra_thumbnails']} | "
            f"{m['mixed_lt_1']}/{m['evaluable']} | {m['groups_with_non_face']} | {s['micro_precision']}/{s['micro_recall']} | "
            f"{pp['P1']['recall']} ({pp['P1']['thumbnails']}) / {pp['P2']['recall']} ({pp['P2']['thumbnails']}) |")


COMPACT_HEAD = ("| t | offered | people found | split persons/extra | mixed/evaluable | with `!` | 7 micro precision/recall | P1 / P2 recall (thumbnails) |\n"
                "|---|---|---|---|---|---|---|---|")
CURVE_HEAD = ("| t | 1 offered | 1 persons on thumbnails | 2 people found | 3 split persons/extra | 4 mixed <1.0/<0.8 of evaluable | 4 with `!` | "
              "5 thumb = majority | 6 strangers <3/<5 | 7 prec med/min | 7 rec med/min | 7 micro p/r | 7 worst-group rec med | 8 pairs built/qualifying | 8 prec med/min | 8 rec med/min |\n"
              "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")


def detail_table(rec, picks, results):
    names = [f"{label} {t}" for label, t in picks]
    ms = [results[(rec, t)] for _, t in picks]
    rows = [
        ("1 Offered thumbnails", lambda m: m["offered"]),
        ("1 Distinct persons they cover (group majority)", lambda m: m["persons_covered_by_majority"]),
        ("1 Groups without a evaluable person label (not evaluable)", lambda m: m["not_evaluable"]),
        ("2 Persons with >=2 images in truth", lambda m: m["t2_persons"]),
        ("2 ... with at least one thumbnail showing them", lambda m: m["t2_found"]),
        ("2 ... with none", lambda m: m["t2_missing"]),
        ("3 Persons on more than one thumbnail", lambda m: m["split_persons"]),
        ("3 Extra thumbnails from splits", lambda m: m["split_extra_thumbnails"]),
        ("4 Mixed groups, majority share < 1.0 (of evaluable)", lambda m: f"{m['mixed_lt_1']} of {m['evaluable']}"),
        ("4 Mixed groups, majority share < 0.8", lambda m: m["mixed_lt_0.8"]),
        ("4 Groups containing a non-face", lambda m: m["groups_with_non_face"]),
        ("5 Thumbnail face = group's majority person", lambda m: f"{m['thumb_is_majority_person']} of {m['evaluable']}"),
        ("5 Thumbnail face is another person / a non-face / unlabelled", lambda m: f"{m['thumb_other_person']} / {m['thumb_non_face']} / {m['thumb_unlabelled']}"),
        ("6 Strangers: majority person < 3 images (of evaluable)", lambda m: m["strangers_lt_3_images"]),
        ("6 Strangers: majority person < 5 images", lambda m: m["strangers_lt_5_images"]),
        ("7 Single-person albums built", lambda m: m["single"]["persons"]),
        ("7 Precision median / min / mean", lambda m: f"{fmt(m['single']['precision'])} / {fmt(m['single']['precision'], 'min')} / {fmt(m['single']['precision'], 'mean')}"),
        ("7 Recall median / min / mean", lambda m: f"{fmt(m['single']['recall'])} / {fmt(m['single']['recall'], 'min')} / {fmt(m['single']['recall'], 'mean')}"),
        ("7 Weighted (micro) precision / recall", lambda m: f"{m['single']['micro_precision']} / {m['single']['micro_recall']}"),
        ("7 Persons with precision 1.0 / recall 1.0", lambda m: f"{m['single']['persons_precision_1']} / {m['single']['persons_recall_1']}"),
        ("7 Regulars (>=5 images): found / persons", lambda m: f"{m['regulars']['found']} / {m['regulars']['persons']}"),
        ("7 Regulars: precision median / min", lambda m: f"{fmt(m['regulars']['precision'])} / {fmt(m['regulars']['precision'], 'min')}"),
        ("7 Regulars: recall median / min (missing counted as 0)", lambda m: f"{fmt(m['regulars']['recall_missing_as_zero'])} / {fmt(m['regulars']['recall_missing_as_zero'], 'min')}"),
        ("7 Recall if the worst split group was picked: median / min (persons with several groups)", lambda m: f"{fmt(m['single']['recall_worst_group'])} / {fmt(m['single']['recall_worst_group'], 'min')} ({m['single']['persons_with_several_groups']})"),
        ("7 Album images the truth cannot judge (unlabelled faces)", lambda m: m["single"]["unknown_images"]),
        ("8 Pairs co-appearing in >=2 images / with both persons pickable", lambda m: f"{m['pairs']['qualifying']} / {m['pairs']['buildable']}"),
        ("8 Precision median / min", lambda m: f"{fmt(m['pairs']['precision'])} / {fmt(m['pairs']['precision'], 'min')}"),
        ("8 Recall median / min / mean", lambda m: f"{fmt(m['pairs']['recall'])} / {fmt(m['pairs']['recall'], 'min')} / {fmt(m['pairs']['recall'], 'mean')}"),
        ("8 Weighted (micro) precision / recall", lambda m: f"{m['pairs']['micro_precision']} / {m['pairs']['micro_recall']}"),
        ("8 Pairs whose album has recall 0", lambda m: m["pairs"]["empty_or_zero_recall"]),
        ("Main subjects P1 / P2: recall (thumbnails)", lambda m: " / ".join(f"{m['regulars']['per_person'][p]['recall']} ({m['regulars']['per_person'][p]['thumbnails']})" for p in ("P1", "P2"))),
    ]
    out = ["| | " + " | ".join(names) + " |", "|---|" + "---|" * len(names)]
    for label, fn in rows:
        out.append(f"| {label} | " + " | ".join(str(fn(m)) for m in ms) + " |")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("data")
    ap.add_argument("labels")
    ap.add_argument("out")
    ap.add_argument("--pick", default="")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    every, faces, mats = A.load_faces(Path(args.data) / "full.faces.jsonl")
    n = len(faces)
    image_ids = [f["id"] for f in faces]
    min_side = [min(f["box"][2], f["box"][3]) for f in faces]
    doc, entries, truth = load_truth(args.labels, faces)

    truth_images = defaultdict(set)
    faces_by_image = defaultdict(list)
    for i, f in enumerate(faces):
        faces_by_image[f["id"]].append(i)
        if truth[i] not in (None, "!"):
            truth_images[truth[i]].add(f["id"])
    pair_truth = defaultdict(set)
    for img, idxs in faces_by_image.items():
        present = sorted({truth[i] for i in idxs if truth[i] not in (None, "!")})
        for p, q in combinations(present, 2):
            pair_truth[(p, q)].add(img)
    t2 = sorted(p for p, s in truth_images.items() if len(s) >= 2)
    c = {"truth": truth, "image_ids": image_ids, "truth_images": truth_images, "faces_by_image": faces_by_image, "t2": t2, "pair_truth": pair_truth}

    person_faces = sum(1 for t in truth if t not in (None, "!"))
    counts = {"faces_scored": n, "faces_with_person_label": person_faces, "persons": len(truth_images),
              "faces_not_a_face": sum(1 for t in truth if t == "!"), "faces_skipped": sum(1 for t in truth if t is None),
              "images_with_a_person_label": len({image_ids[i] for i in range(n) if truth[i] not in (None, "!")}),
              "persons_with_ge2_images": len(t2), "persons_with_1_image": len(truth_images) - len(t2),
              "person_image_counts_top": sorted((len(s) for s in truth_images.values()), reverse=True)[:6],
              "pairs_coappearing_ge2_images": sum(1 for s in pair_truth.values() if len(s) >= 2),
              "faces_in_the_two_largest_persons": sum(1 for t in truth if t in {p for p, _ in sorted(truth_images.items(), key=lambda kv: -len(kv[1]))[:2]})}

    # would a detector-score cut remove the non-faces? (YuNet score of each labelled face)
    bang_scores = sorted(faces[i]["score"] for i in range(n) if truth[i] == "!")
    person_scores = sorted(faces[i]["score"] for i in range(n) if truth[i] not in (None, "!"))
    if bang_scores:
        counts["non_face_detector_scores_min_max"] = [bang_scores[0], bang_scores[-1]]
        counts["person_faces_with_score_at_or_below_the_highest_non_face"] = sum(1 for x in person_scores if x <= bang_scores[-1])
        counts["person_faces_min_score"] = person_scores[0]
    counts["non_face_box_side_px_min_max"] = [round(min(min_side[i] for i in range(n) if truth[i] == "!"), 1), round(max(min_side[i] for i in range(n) if truth[i] == "!"), 1)] if bang_scores else None

    dist = {r: A.distance_matrix(r, mats[r]) for r in A.RECS}
    results = {}
    for rec in A.RECS:
        for t in SWEEP[rec]:
            groups = A.groups_from_labels(list(range(n)), A.agglomerative(dist[rec], list(range(n)), t))
            off = A.offered(groups, image_ids)
            thumbs = [A.thumbnail(g, dist[rec], min_side) for g in off]
            results[(rec, t)] = evaluate(off, thumbs, c)

    # bias check: how the labelled partition (SFace average 0.55) was treated by the maintainer
    base_groups = A.groups_from_labels(list(range(n)), A.agglomerative(dist[BASE_RECOGNISER], list(range(n)), BASE_T))
    local_of = {f["orig"]: i for i, f in enumerate(faces)}
    by_card = defaultdict(list)
    singles_labelled = []
    for e in doc["labels"]:
        (singles_labelled if e["card"] is None else by_card[e["card"]]).append(local_of[e["idx"]])
    base_multi = [frozenset(g) for g in base_groups if len(g) >= 2]
    in_cards = set().union(*base_multi)
    bias = {"labels_file_base": doc["base"], "cards_shown": len(base_multi), "cards_with_at_least_one_label": len(by_card),
            "faces_in_cards": len(in_cards), "singles_shown": n - len(in_cards), "singles_named": len(singles_labelled),
            "singles_skipped": sum(1 for i in range(n) if i not in in_cards and truth[i] is None),
            "skipped_faces_inside_cards": sum(1 for i in in_cards if truth[i] is None)}
    differing = agreeing = 0
    for v in by_card.values():
        names = Counter(truth[i] for i in v)
        top = names.most_common(1)[0][1]
        agreeing += top
        differing += len(v) - top
    bias["labelled_card_faces_agreeing_with_card_majority"] = agreeing
    bias["labelled_card_faces_differing_from_card_majority"] = differing
    bias["non_face_faces_inside_cards"] = sum(1 for i in in_cards if truth[i] == "!")
    # persons the labelling found split by SFace 0.55: faces in more than one card, or in a card and in a named single
    where = defaultdict(set)
    for gi, g in enumerate(base_multi):
        for i in g:
            if truth[i] not in (None, "!"):
                where[truth[i]].add(gi)
    for i in singles_labelled:
        if truth[i] not in (None, "!"):
            where[truth[i]].add(("single", i))
    bias["persons_spread_over_several_cards_or_singles"] = sum(1 for v in where.values() if len(v) > 1)
    bias["card_label_sets_inside_one_recomputed_cluster"] = sum(1 for v in by_card.values() if any(set(v) <= b for b in base_multi))
    # persons whose every truth image comes from one single card (so only SFace's own grouping, confirmed by eye, supports them)
    bias["persons_in_exactly_one_card_and_no_single"] = sum(1 for v in where.values() if len(v) == 1 and not any(isinstance(x, tuple) for x in v))

    picks = {}
    for kv in args.pick.split(","):
        if kv:
            r, ts = kv.split("=")
            tl = [float(x) for x in ts.split(":")]
            picks[r] = [("strict", tl[0]), ("publisher", tl[1])] + ([("recommended", tl[2])] if len(tl) > 2 and tl[2] not in tl[:2] else [])

    print("COUNTS", json.dumps(counts))
    print("BIAS", json.dumps(bias))
    for rec in A.RECS:
        print(f"\n### {rec} curve\n")
        print(CURVE_HEAD)
        for t in SWEEP[rec]:
            print(curve_row(t, results[(rec, t)]))
    for rec in A.RECS:
        print(f"\n### {rec} compact\n")
        print(COMPACT_HEAD)
        for t in SWEEP[rec]:
            print(compact_row(t, results[(rec, t)]))
    for rec, pl in picks.items():
        print(f"\n### {rec} detail\n")
        print(detail_table(rec, pl, results))
    (out / "m5_scores.json").write_text(json.dumps({"counts": counts, "bias": bias, "results": {f"{r}@{t}": m for (r, t), m in results.items()}}, indent=1, default=lambda o: sorted(o) if isinstance(o, set) else str(o)))


if __name__ == "__main__":
    main()
