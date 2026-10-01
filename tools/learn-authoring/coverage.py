"""Prints the per-level / per-subject counts table of the anglophone content (used by docs/coverage/learn-anglophone.md)."""
import json, os, glob, collections
ROOT = os.environ.get("LEARN_CONTENT", "/home/user/CastBridge/content/learn")
ORDER = ["Class 1","Class 2","Class 3","Class 4","Class 5","Class 6","Form 1","Form 2","Form 3","Form 4","Form 5","Lower Sixth","Upper Sixth"]
rows = []
for d in glob.glob(ROOT + "/*/pack.json"):
    p = json.load(open(d, encoding="utf-8"))
    if p["lang"] != "en" or p["cursus"] not in ("primary", "secondary"): continue
    base = os.path.dirname(d); nl = ne = nq = ni = 0; sz = os.path.getsize(d)
    for f in glob.glob(base + "/lessons/*.json"):
        j = json.load(open(f, encoding="utf-8")); sz += os.path.getsize(f); nl += len(j["lessons"])
        for e in j["exercises"]:
            if e.get("tier") == "autoeval": nq += 1
            elif not e["id"].split("-")[-1].startswith("m") or True: ne += 1
        for l in j["lessons"]:
            ni += sum(1 for b in l["blocks"] if b["type"] == "illustration" or (b["type"] == "example" and b.get("figure")))
    rows.append((ORDER.index(p["level"]), p["id"], p["title"], p.get("subjectWanted") or p["subject"], p.get("exam") or "-", nl, ne, nq, ni, len(p.get("mockExams", [])), sz // 1024))
rows.sort()
print("| Level | Pack | Subject | Exam | Lessons | Exercises | Self-checks | Illustrations | Mocks | KB |\n|---|---|---|---|---|---|---|---|---|---|")
lvl = collections.OrderedDict()
for r in rows:
    print("| %s | %s | %s | %s | %d | %d | %d | %d | %d | %d |" % (ORDER[r[0]], r[1], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10]))
    t = lvl.setdefault(ORDER[r[0]], [0, 0, 0, 0, 0, 0, 0]); t[0] += 1; t[1] += r[5]; t[2] += r[6]; t[3] += r[7]; t[4] += r[8]; t[5] += r[9]; t[6] += r[10]
print("\n| Level | Packs | Lessons | Exercises | Self-checks | Illustrations | Mocks | JSON KB (uncompressed) |\n|---|---|---|---|---|---|---|---|")
tot = [0] * 7
for k, t in lvl.items():
    print("| %s | %s |" % (k, " | ".join(str(x) for x in t)))
    tot = [a + b for a, b in zip(tot, t)]
print("| **Total** | %s |" % " | ".join("**%d**" % x for x in tot))
