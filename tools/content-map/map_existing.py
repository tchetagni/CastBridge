#!/usr/bin/env python3
"""Proposes skill ids, levels and lot scopes for the EXISTING Apprendre lessons and quiz packs (never edits them).

  map_existing.py [--content DIR] [--out content/graph/mapping/existing.json] [--min-score 0.12]

For each lesson of content/learn/<pack>/lessons: the best-matching skills of the graph in the same domain and class (title,
programme reference and objectives against the skill titles); for each quiz pack: its lot and, per question category, the best
skill. The result is a PROPOSAL for the content agents (who then add the optional fields `skill`, `level`, `lot` to their JSON);
low scores are listed under `unmatched`. Deterministic.
"""
import argparse, json, os, re, sys, unicodedata, zipfile
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "content-lib"))
import lotlib as L  # noqa: E402

STOP = set("le la les un une des de du d l et ou en au aux a the of and to in on for with is are par pour dans sur avec sans qui que quoi son sa ses leur leurs ce cette ces notions notion cours fiche lecons lecon programme verifier".split())


def toks(s):
    s = unicodedata.normalize("NFKD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    out = set()
    for w in re.findall(r"[a-z0-9]+", s):
        if w in STOP or len(w) < 3:
            continue
        out.add(w[:-1] if w.endswith("s") and len(w) > 4 else w)
    return out


def score(a, b):
    if not a or not b:
        return 0.0
    inter = len(a & b)
    return inter / (min(len(a), len(b)) ** 0.5 * max(len(a), len(b)) ** 0.5)


def load_graph(content):
    skills = []
    gd = os.path.join(content, "graph")
    for fn in sorted(os.listdir(gd)):
        if fn.endswith(".json") and fn not in ("scopes.json", "paths.json"):
            d = L.read_json(os.path.join(gd, fn))
            for s in d["skills"]:
                s["domain"] = d["domain"]
                s["_t"] = toks(s["title"]["fr"] + " " + s["title"]["en"] + " " + " ".join(s.get("tags", [])))
                skills.append(s)
    return skills


def best(text, domain, scope, skills, ords, n=3, level="N1"):
    domains = domain if isinstance(domain, (list, tuple)) else [domain]
    y = ords.get(scope)
    t = toks(text)
    cand = []
    for s in skills:
        if s["domain"] not in domains or s["level"] != level:
            continue
        if y is not None and not (min(s["years"]) - 1 <= y <= max(s["years"]) + 1):
            continue
        sc = score(t, s["_t"])
        # skills introduced in the class of the content win ties
        if y is not None and y in s["years"]:
            sc += 0.05
        cand.append((sc, s))
    cand.sort(key=lambda x: (-x[0], x[1]["id"]))
    return [{"skill": s["id"], "score": round(sc, 3), "level": s["level"], "lot": scope} for sc, s in cand[:n] if sc > 0]


def run(content, min_score):
    skills = load_graph(content)
    ords = {s["id"]: s["ord"] for s in L.read_json(os.path.join(content, "graph", "scopes.json"))["scopes"]}
    out = {"format": 1, "generatedBy": "tools/content-map/map_existing.py", "minScore": min_score, "learn": [], "quiz": [], "unmatched": []}
    learn = os.path.join(content, "learn")
    for d in sorted(os.listdir(learn)):
        pj = os.path.join(learn, d, "pack.json")
        if not os.path.isfile(pj):
            continue
        pack = L.read_json(pj); scope = L.learn_scope(pack); dom = L.SUBJECT_DOMAIN.get(pack.get("subject"), pack.get("subject"))
        chap = {c["id"]: c for c in pack.get("chapters", [])}
        ld = os.path.join(learn, d, "lessons")
        for fn in sorted(os.listdir(ld)) if os.path.isdir(ld) else []:
            doc = L.read_json(os.path.join(ld, fn))
            for les in doc.get("lessons", []):
                text = " ".join([les.get("title", ""), les.get("programRef", "") or "", " ".join(les.get("objectives", [])), chap.get(les.get("chapter", doc.get("chapter")), {}).get("title", "")])
                props = best(text, [dom, "physique-chimie"] if pack.get("subject") == "sciences" else dom, scope, skills, ords)
                row = {"pack": pack["id"], "lesson": les["id"], "title": les.get("title"), "domain": dom, "lot": "learn:" + str(scope), "proposed": dict(props[0], confidence="high" if props[0]["score"] >= 0.25 else "medium" if props[0]["score"] >= 0.15 else "low") if props else None, "alternatives": props[1:]}
                out["learn"].append(row)
                if not props or props[0]["score"] < min_score:
                    out["unmatched"].append({"kind": "lesson", "id": les["id"], "title": les.get("title")})
    dist = os.path.join(content, "quiz", "dist")
    cat = L.read_json(os.path.join(dist, "catalog.json"))
    for e in cat["packs"]:
        scope = L.quiz_scope(e)
        row = {"pack": e["id"], "lot": "quiz:" + str(scope), "level": "N1", "categories": []}
        with zipfile.ZipFile(os.path.join(dist, e["file"])) as z:
            qs = json.loads(z.read("questions.json").decode("utf-8"))["questions"]
        cats = {}
        for q in qs:
            cats.setdefault(q.get("category", "?"), 0)
            cats[q.get("category", "?")] += 1
        # quiz categories of school tracks follow the subject of the class; general knowledge and fields have no school domain
        doms = ["mathematiques", "francais", "svt", "physique-chimie", "histoire-geo-ecm", "english", "informatique"] if e.get("track") in ("primary", "secondary") else []
        for c, n in sorted(cats.items()):
            props = []
            for dom in doms:
                props += best(c, dom, scope, skills, ords, n=1)
            props.sort(key=lambda p: -p["score"])
            row["categories"].append({"category": c, "questions": n, "proposed": props[0] if props and props[0]["score"] >= min_score else None})
        out["quiz"].append(row)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--content", default=L.CONTENT)
    ap.add_argument("--out", default=None)
    ap.add_argument("--min-score", type=float, default=0.12)
    a = ap.parse_args(argv)
    res = run(a.content, a.min_score)
    out = a.out or os.path.join(a.content, "graph", "mapping", "existing.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1); f.write("\n")
    ok = sum(1 for r in res["learn"] if r["proposed"] and r["proposed"]["score"] >= a.min_score)
    print("%d/%d leçons rattachées à une compétence, %d paquets de quiz → %s" % (ok, len(res["learn"]), len(res["quiz"]), out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
