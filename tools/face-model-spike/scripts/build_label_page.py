"""Build the labelling page: private/label/index.html (self-contained; the crops are loaded by relative path from ../g990b/full/).

usage: build_label_page.py <g990b dir> <analysis dir> <label dir>

Every clustered face (smaller box side >= MIN_SIDE) is labelled once; the three recognisers share the same faces, so the labels are reused
for all of them. Faces are presented in the cards of one base partition: SFace, average-linkage, cosine distance 0.55. The cards are only a
convenience for typing one name per group; every face ends up with its own label, so a recogniser's groups are scored from the face labels later.
"""
import json
import sys
from pathlib import Path

import numpy as np

from analyse import agglomerative, distance_matrix, groups_from_labels, load_faces, RECS

BASE_REC, BASE_T = "sface", 0.55
PUBLISHER = {"sface": "0.637", "openface": "0.5", "dlib": "0.6"}
LETTER = {"sface": "S", "openface": "O", "dlib": "D"}


def main():
    data, analysis, label_dir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    every, faces, mats = load_faces(data / "full.faces.jsonl")
    n = len(faces)
    d = distance_matrix(BASE_REC, mats[BASE_REC])
    groups = groups_from_labels(list(range(n)), agglomerative(d, list(range(n)), BASE_T))
    multi = sorted((g for g in groups if len(g) > 1), key=len, reverse=True)
    singles = [g[0] for g in groups if len(g) == 1]
    # singles sit next to the other faces of the same photo, which is the only cheap grouping that helps recognise them
    singles.sort(key=lambda i: (faces[i]["id"], faces[i]["k"]))

    # thumbnails of every shown grouping, by original face index
    thumbs = {}
    for p in sorted(analysis.glob("groups_*.json")):
        g = json.loads(p.read_text())
        tag = f'{LETTER[g["rec"]]}@{g["threshold"]}'
        for grp in g["groups"]:
            thumbs.setdefault(grp["thumb"], []).append((tag, str(g["threshold"]) == PUBLISHER[g["rec"]]))

    def tile(i):
        f = faces[i]
        tags = thumbs.get(f["orig"], [])
        return {"idx": f["orig"], "id": f["id"], "k": f["k"], "small": min(f["box"][2], f["box"][3]) < 60,
                "thumb": [t for t, _ in tags], "badge": "".join(sorted({t[0] for t, pub in tags if pub}))}

    cards = []
    for g in multi:
        medoid = int(min(g, key=lambda i: float(d[i, g].mean())))
        order = sorted(g, key=lambda i: (i != medoid, float(d[i, medoid])))
        cards.append([tile(i) for i in order])
    page = {"cards": cards, "singles": [tile(i) for i in singles], "base": f"{BASE_REC}/average/{BASE_T}", "faces_total": n}

    label_dir.mkdir(parents=True, exist_ok=True)
    (label_dir / "index.html").write_text(TEMPLATE.replace("__DATA__", json.dumps(page)), encoding="utf-8")
    print(f"cards with >=2 faces: {len(cards)} ({sum(len(c) for c in cards)} faces); singles: {len(singles)}; total {n}")


TEMPLATE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Face labelling</title>
<style>
 body{font:14px system-ui,sans-serif;margin:0;background:#fafafa;color:#111}
 header{position:sticky;top:0;z-index:5;background:#fff;border-bottom:1px solid #ccc;padding:8px 12px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
 header b{font-size:15px} button{padding:4px 10px;cursor:pointer} #status{color:#555}
 main{padding:8px 12px 80px}
 .card{background:#fff;border:1px solid #ccc;border-radius:6px;margin:8px 0;padding:6px 8px}
 .card.done{border-color:#4a4;background:#f4fbf4}
 .head{display:flex;gap:8px;align-items:center;margin-bottom:4px}
 .head input{width:14em}
 .tiles{display:flex;flex-wrap:wrap;gap:6px}
 .tile{width:104px;font-size:11px;text-align:center}
 .tile img{width:104px;height:104px;object-fit:cover;border:3px solid transparent;border-radius:4px;cursor:pointer;display:block}
 .tile.x img{border-color:#d22;opacity:.6}
 .tile input{width:98px;margin-top:2px;display:none}
 .tile.x input{display:block}
 .badge{color:#fff;background:#37c;border-radius:3px;padding:0 3px;margin-left:2px}
 .singles .tile input{display:block}
 .hint{color:#555;max-width:60em}
</style></head><body>
<header><b>Face labelling</b><span id="progress"></span>
<button id="save">Save</button><button id="dl">Download JSON</button><span id="status"></span>
<label><input type="checkbox" id="todo"> only show cards with unlabelled faces</label></header>
<main>
<p class="hint">One name per card. Names are matched ignoring case, so typing the same name on two cards says they are the same person. Click a face that does NOT belong to its card: it gets a red outline and its own name box (leave it empty to skip that face; type <b>!</b> if it is not a face at all). Leave a card's name empty to skip the whole card. Faces marked S, O or D are the thumbnail the corresponding recogniser would show.</p>
<div id="cards"></div><h3>Faces that did not group (one per box; name only the ones you recognise)</h3><div id="singles" class="singles card"><div class="tiles"></div></div>
</main>
<datalist id="names"></datalist>
<script>
const DATA = __DATA__;
const KEY = "facelabels-v1";
let st = JSON.parse(localStorage.getItem(KEY) || '{"card":{},"x":{},"own":{}}');
const canon = new Map();
function canonical(s){ s=(s||"").trim(); if(!s) return ""; const k=s.toLowerCase(); if(!canon.has(k)) canon.set(k,s); return canon.get(k); }
function effective(card, t){ return st.x[t.idx] ? canonical(st.own[t.idx]) : canonical(st.card[card]); }
function img(t){ return "../g990b/full/"+t.id+"_"+t.k+".jpg"; }
function tileEl(t, card){
  const d=document.createElement("div"); d.className="tile"+(st.x[t.idx]?" x":"");
  const im=document.createElement("img"); im.src=img(t); im.loading="lazy"; im.title=(t.thumb.length?"thumbnail of "+t.thumb.join(", ")+(t.small?"; small face":""):(t.small?"small face":""));
  const inp=document.createElement("input"); inp.setAttribute("list","names"); inp.placeholder=card==="s"?"name":"own name"; inp.value=st.own[t.idx]||"";
  inp.oninput=()=>{ st.own[t.idx]=inp.value; changed(); };
  if(card!=="s") im.onclick=()=>{ st.x[t.idx]=!st.x[t.idx]; d.classList.toggle("x",!!st.x[t.idx]); if(st.x[t.idx]) inp.focus(); changed(); };
  d.append(im); if(t.badge){ const b=document.createElement("div"); b.innerHTML=[...t.badge].map(c=>'<span class="badge">'+c+'</span>').join(""); d.append(b); }
  d.append(inp); return d;
}
function build(){
  const root=document.getElementById("cards");
  DATA.cards.forEach((faces,ci)=>{
    const c=document.createElement("div"); c.className="card"; c.id="c"+ci;
    const h=document.createElement("div"); h.className="head";
    const inp=document.createElement("input"); inp.setAttribute("list","names"); inp.placeholder="name for all "+faces.length; inp.value=st.card[ci]||"";
    inp.oninput=()=>{ st.card[ci]=inp.value; changed(); };
    inp.onkeydown=e=>{ if(e.key==="Enter"){ const n=document.querySelector("#c"+(ci+1)+" .head input"); if(n){ n.focus(); n.scrollIntoView({block:"center"}); } } };
    h.append("Card "+(ci+1)+" \u00b7 "+faces.length+" faces ", inp); c.append(h);
    const tiles=document.createElement("div"); tiles.className="tiles"; faces.forEach(t=>tiles.append(tileEl(t,ci))); c.append(tiles); root.append(c);
  });
  const s=document.querySelector("#singles .tiles"); DATA.singles.forEach(t=>s.append(tileEl(t,"s")));
}
function allLabels(){
  const out=[]; DATA.cards.forEach((faces,ci)=>faces.forEach(t=>{ const n=effective(ci,t); if(n) out.push({idx:t.idx,id:t.id,k:t.k,name:n,card:ci}); }));
  DATA.singles.forEach(t=>{ const n=canonical(st.own[t.idx]); if(n) out.push({idx:t.idx,id:t.id,k:t.k,name:n,card:null}); });
  return out;
}
function refresh(){
  const labels=allLabels(); canon.clear(); allLabels();
  document.getElementById("progress").textContent=labels.length+" of "+DATA.faces_total+" faces labelled";
  const names=[...new Set(allLabels().map(l=>l.name))].sort(); document.getElementById("names").innerHTML=names.map(n=>'<option value="'+n.replace(/"/g,"&quot;")+'">').join("");
  const only=document.getElementById("todo").checked;
  DATA.cards.forEach((faces,ci)=>{ const el=document.getElementById("c"+ci); const done=faces.every(t=>effective(ci,t)!=="");
    el.classList.toggle("done",done); el.style.display=(only&&done)?"none":""; });
}
let timer=null;
function changed(){ localStorage.setItem(KEY,JSON.stringify(st)); refresh(); clearTimeout(timer); timer=setTimeout(()=>save(true),3000); }
function payload(){ return JSON.stringify({version:1,base:DATA.base,saved:new Date().toISOString(),faces_total:DATA.faces_total,labels:allLabels()},null,1); }
async function save(quiet){
  const s=document.getElementById("status");
  if(location.protocol.startsWith("http")){
    try{ const r=await fetch("/save",{method:"POST",headers:{"Content-Type":"application/json"},body:payload()}); s.textContent=r.ok?"saved to private/labels/labels.json "+new Date().toLocaleTimeString():"save failed: "+r.status; return; }catch(e){ s.textContent="save failed: "+e; return; }
  }
  if(!quiet) download(); else s.textContent="progress kept in this browser (opened as a file: use Download JSON when done)";
}
function download(){ const a=document.createElement("a"); a.href=URL.createObjectURL(new Blob([payload()],{type:"application/json"})); a.download="labels.json"; a.click(); }
document.getElementById("save").onclick=()=>save(false); document.getElementById("dl").onclick=download; document.getElementById("todo").onchange=refresh;
build(); refresh();
</script></body></html>
"""

if __name__ == "__main__":
    main()
