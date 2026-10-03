"""Quiz lots (docs/QUIZ.md, section Lots): one self-contained pack per SCOPE (a class, a level, a field, or one region of
general knowledge). A lot is the pack format (manifest.json + questions.json) plus index.json (version, question count, hash
of every question). The lot version changes only when its content changes (lots-state.json remembers the last build).
"""
import hashlib
import json
from collections import Counter

from .core import COURSES
from .courses_sup import SUP_SCOPES
from .pack import _zip_bytes, question_json, PACK_SUFFIX

MAX_LOT_BYTES = 3 << 20      # a lot above this is a build error (a TV downloads through a flaky phone link)
FEATURE = "quiz"

# scope -> (course key of core.COURSES, region filter or None, title). Keep in sync with QuizLotScopes.specs (Kotlin).
SCOPES = {
    "culture-cm": ("general", "CM", "Culture générale · Cameroun"),
    "culture-afrique": ("general", "AF", "Culture générale · Afrique"),
    "culture-monde": ("general", "WORLD", "Culture générale · Monde"),
    "cm2": ("cm2", None, "Primaire · CM2"),
    "3e": ("3e", None, "Secondaire · 3e"),
    "tle": ("tle", None, "Secondaire · Terminale"),
    "droit-l1": ("l1-droit", None, "Supérieur · L1 Droit"),
    "eco-l1": ("l1-eco", None, "Supérieur · L1 Économie"),
    "maths-l1": ("l1-maths", None, "Supérieur · L1 Mathématiques"),
    "cp": ("cp", None, "Primaire · CP"),
    "ce1": ("ce1", None, "Primaire · CE1"),
    "ce2": ("ce2", None, "Primaire · CE2"),
    "cm1": ("cm1", None, "Primaire · CM1"),
    "6e": ("6e", None, "Secondaire · 6e"),
    "5e": ("5e", None, "Secondaire · 5e"),
    "4e": ("4e", None, "Secondaire · 4e"),
    "class-1": ("class1", None, "Primary (EN) · Class 1"),
    "class-2": ("class2", None, "Primary (EN) · Class 2"),
    "class-3": ("class3", None, "Primary (EN) · Class 3"),
    "class-4": ("class4", None, "Primary (EN) · Class 4"),
    "class-5": ("class5", None, "Primary (EN) · Class 5"),
    "class-6": ("class6", None, "Primary (EN) · Class 6"),
    "form-1": ("form1", None, "Secondary (EN) · Form 1"),
    "form-2": ("form2", None, "Secondary (EN) · Form 2"),
    "form-3": ("form3", None, "Secondary (EN) · Form 3"),
    "droit-l2": ("l2-droit", None, "Supérieur · L2 Droit"),
    "droit-l3": ("l3-droit", None, "Supérieur · L3 Droit"),
    "eco-l2": ("l2-eco", None, "Supérieur · L2 Économie"),
    "eco-l3": ("l3-eco", None, "Supérieur · L3 Économie"),
    "maths-l2": ("l2-maths", None, "Supérieur · L2 Mathématiques"),
    "informatique-l1": ("l1-info", None, "Supérieur · L1 Informatique"),
    "informatique-l2": ("l2-info", None, "Supérieur · L2 Informatique"),
    "informatique-l3": ("l3-info", None, "Supérieur · L3 Informatique"),
    "biologie-l1": ("l1-bio", None, "Supérieur · L1 Biologie (santé publique de base)"),
    "sociologie-l1": ("l1-socio", None, "Supérieur · L1 Sociologie (méthodologie)"),
    "2nde-chimie": ("2nde-chim", None, "Lycée · 2nde · Chimie"),
    "1re-chimie": ("1re-chim", None, "Lycée · 1re · Chimie"),
    "tle-chimie": ("tle-chim", None, "Lycée · Tle · Chimie"),
    "form-5-chimie": ("f5-chem", None, "GCE Ordinary Level · Chemistry"),
    "lower-sixth-chimie": ("l6-chem", None, "GCE Advanced Level, Lower Sixth · Chemistry"),
    "upper-sixth-chimie": ("u6-chem", None, "GCE Advanced Level, Upper Sixth · Chemistry"),
    "2nde-litterature": ("2nde-fran", None, "Lycée · 2nde · Français / Littérature / Anglais"),
    "1re-litterature": ("1re-fran", None, "Lycée · 1re · Français / Littérature / Anglais"),
    "tle-litterature": ("tle-fran", None, "Lycée · Tle · Français / Littérature / Anglais"),
    "form-5-litterature": ("f5-lit", None, "GCE Ordinary Level · English Language and Literature"),
    "lower-sixth-litterature": ("l6-lit", None, "GCE Advanced Level, Lower Sixth · English Language and Literature"),
    "upper-sixth-litterature": ("u6-lit", None, "GCE Advanced Level, Upper Sixth · English Language and Literature"),
    "2nde-informatique": ("2nde-info", None, "Lycée · 2nde · Informatique"),
    "1re-informatique": ("1re-info", None, "Lycée · 1re · Informatique"),
    "tle-informatique": ("tle-info", None, "Lycée · Tle · Informatique"),
    "form-5-informatique": ("f5-cs", None, "GCE Ordinary Level · Computer Science"),
    "lower-sixth-informatique": ("l6-cs", None, "GCE Advanced Level, Lower Sixth · Computer Science"),
    "upper-sixth-informatique": ("u6-cs", None, "GCE Advanced Level, Upper Sixth · Computer Science"),
    "2nde-biologie": ("2nde-svt", None, "Lycée · 2nde · SVT"),
    "1re-biologie": ("1re-svt", None, "Lycée · 1re · SVT"),
    "tle-biologie": ("tle-svt", None, "Lycée · Tle · SVT"),
    "form-5-biologie": ("f5-bio", None, "GCE Ordinary Level · Biology"),
    "lower-sixth-biologie": ("l6-bio", None, "GCE Advanced Level, Lower Sixth · Biology"),
    "upper-sixth-biologie": ("u6-bio", None, "GCE Advanced Level, Upper Sixth · Biology"),
    "2nde-economie": ("2nde-eco", None, "Lycée · 2nde · Économie"),
    "1re-economie": ("1re-eco", None, "Lycée · 1re · Économie"),
    "tle-economie": ("tle-eco", None, "Lycée · Tle · Économie"),
    "form-5-economie": ("f5-econ", None, "GCE Ordinary Level · Economics"),
    "lower-sixth-economie": ("l6-econ", None, "GCE Advanced Level, Lower Sixth · Economics"),
    "upper-sixth-economie": ("u6-econ", None, "GCE Advanced Level, Upper Sixth · Economics"),
    "2nde-geographie": ("2nde-geo", None, "Lycée · 2nde · Géographie"),
    "1re-geographie": ("1re-geo", None, "Lycée · 1re · Géographie"),
    "tle-geographie": ("tle-geo", None, "Lycée · Tle · Géographie"),
    "form-5-geographie": ("f5-geo", None, "GCE Ordinary Level · Geography"),
    "lower-sixth-geographie": ("l6-geo", None, "GCE Advanced Level, Lower Sixth · Geography"),
    "upper-sixth-geographie": ("u6-geo", None, "GCE Advanced Level, Upper Sixth · Geography"),
    "form-5-mathematiques": ("f5-math", None, "GCE Ordinary Level · Mathematics"),
    "lower-sixth-mathematiques": ("l6-math", None, "GCE Advanced Level, Lower Sixth · Mathematics"),
    "upper-sixth-mathematiques": ("u6-math", None, "GCE Advanced Level, Upper Sixth · Mathematics"),
    "2nde-mathematiques": ("2nde-maths", None, "Lycée · 2nde · Mathématiques"),
    "1re-mathematiques": ("1re-maths", None, "Lycée · 1re · Mathématiques"),
    "tle-mathematiques": ("tle-maths", None, "Lycée · Tle · Mathématiques"),
    "2nde-physique": ("2nde-phys", None, "Lycée · 2nde · Physique"),
    "1re-physique": ("1re-phys", None, "Lycée · 1re · Physique"),
    "tle-physique": ("tle-phys", None, "Lycée · Tle · Physique"),
    "form-5-physique": ("f5-phys", None, "GCE Ordinary Level · Physics"),
    "lower-sixth-physique": ("l6-phys", None, "GCE Advanced Level, Lower Sixth · Physics"),
    "upper-sixth-physique": ("u6-phys", None, "GCE Advanced Level, Upper Sixth · Physics"),
    "1re-histoire": ("1re-hist", None, "Lycée · 1re · Histoire"),
    "tle-histoire": ("tle-hist", None, "Lycée · Tle · Histoire"),
    "2nde-histoire": ("2nde-hist", None, "Lycée · 2nde · Histoire"),
    "lower-sixth-histoire": ("l6-hist", None, "GCE Advanced Level, Lower Sixth · History"),
    "upper-sixth-histoire": ("u6-hist", None, "GCE Advanced Level, Upper Sixth · History"),
    "form-5-histoire": ("f5-hist", None, "GCE Ordinary Level · History"),
    "2nde-droit": ("2nde-ecm", None, "Lycée · 2nde · ECM"),
    "1re-droit": ("1re-ecm", None, "Lycée · 1re · ECM"),
    "tle-droit": ("tle-ecm", None, "Lycée · Tle · ECM"),
    "tle-philosophie": ("tle-philo", None, "Lycée · Tle · Philosophie"),
}
# Supérieur : 26 cellules niveau × filière (physique-l1, geographie-l2, mathematiques-l3...), voir courses_sup.py.
SCOPES.update(SUP_SCOPES)


def lot_file(scope, version):
    return "quiz-%s-p1-v%d%s" % (scope, version, PACK_SUFFIX)


def question_hash(q):
    """First 8 hex digits of SHA-256 of the fields joined by U+001F (choices by U+001E): same as QuizLotIndex.questionHash (Kotlin)."""
    f = [q["id"], q.get("track") or "general", q.get("level") or "", q.get("field") or "", q.get("region") or "", q.get("category") or "",
         str(int(q["difficulty"])), q["question"], "\u001e".join(q["choices"]), str(int(q["answer"])), q.get("explanation") or "",
         q.get("source") or "", q.get("status") or "", q.get("verif") or "", q.get("lang") or "fr"]
    return hashlib.sha256("\u001f".join(f).encode("utf-8")).hexdigest()[:8]


def content_hash(hashes):
    return hashlib.sha256("".join("%s:%s\n" % (k, hashes[k]) for k in sorted(hashes)).encode("utf-8")).hexdigest()


def lot_questions(questions, scope):
    course, region, _ = SCOPES[scope]
    c = COURSES[course]
    return sorted((q for q in questions if (q["track"], q["level"], q["field"]) == (c["track"], c["level"], c["field"])
                   and (region is None or q["region"] == region)), key=lambda q: q["id"])


def make_lot(scope, qs, version):
    course, _, title = SCOPES[scope]
    c = COURSES[course]
    qs = [question_json(q) for q in qs]
    body = "{\"version\":2,\"questions\":[\n" + ",\n".join(json.dumps(q, ensure_ascii=False, separators=(",", ":")) for q in qs) + "\n]}\n"
    qbytes = body.encode("utf-8")
    hashes = {q["id"]: question_hash(q) for q in qs}
    chash = content_hash(hashes)
    index = {"v": 1, "scope": scope, "version": version, "count": len(qs), "contentHash": chash, "q": dict(sorted(hashes.items()))}
    ibytes = json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    manifest = {
        "format": 1, "id": scope, "course": course, "track": c["track"], "level": c["level"], "field": c["field"],
        "part": 1, "parts": 1, "version": version, "questions": len(qs),
        "lot": {"feature": FEATURE, "scope": scope, "title": title, "contentHash": chash},
        "byRegion": dict(sorted(Counter(q["region"] for q in qs).items())),
        "byDifficulty": {str(d): n for d, n in sorted(Counter(q["difficulty"] for q in qs).items())},
        "statuses": dict(sorted(Counter(q["status"] for q in qs).items())),
        "files": {"questions.json": {"size": len(qbytes), "sha256": hashlib.sha256(qbytes).hexdigest()},
                  "index.json": {"size": len(ibytes), "sha256": hashlib.sha256(ibytes).hexdigest()}},
    }
    data = _zip_bytes({"manifest.json": json.dumps(manifest, ensure_ascii=False, indent=1).encode("utf-8"), "questions.json": qbytes, "index.json": ibytes})
    return manifest, chash, data


class LotTooBig(Exception):
    pass


def build_lots(questions, out_dir, max_bytes=MAX_LOT_BYTES):
    """Writes every lot + catalog-lots.json + lots-state.json into out_dir. Returns (catalog dict, report lines).
    Raises LotTooBig (after writing nothing) when one lot exceeds max_bytes."""
    state_file = out_dir / "lots-state.json"
    state = json.loads(state_file.read_text(encoding="utf-8")) if state_file.is_file() else {}
    built, new_state = [], {}
    for scope, (course, region, title) in SCOPES.items():
        qs = lot_questions(questions, scope)
        if not qs:
            continue
        old = state.get(scope)
        _, chash, _ = make_lot(scope, qs, 0)
        version = 1 if old is None else (old["version"] if old["contentHash"] == chash else old["version"] + 1)
        manifest, chash, data = make_lot(scope, qs, version)
        if len(data) > max_bytes:
            raise LotTooBig("lot %s : %d octets, plus que le plafond de %d octets" % (scope, len(data), max_bytes))
        new_state[scope] = {"version": version, "contentHash": chash}
        c = COURSES[course]
        built.append({"feature": FEATURE, "scope": scope, "version": version, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                      "title": title, "minAppVersion": 0, "file": lot_file(scope, version), "questions": len(qs), "contentHash": chash,
                      "id": scope, "course": course, "track": c["track"], "level": c["level"], "field": c["field"], "part": 1, "parts": 1,
                      "_data": data, "_changed": old is None or old["contentHash"] != chash})
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*" + PACK_SUFFIX):
        old.unlink()
    for e in built:
        (out_dir / e["file"]).write_bytes(e.pop("_data"))
    changed = {e["scope"]: e.pop("_changed") for e in built}
    total = sum(e["bytes"] for e in built)
    catalog = {"format": 1, "feature": FEATURE, "totalBytes": total, "lots": built}
    (out_dir / "catalog-lots.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    state_file.write_text(json.dumps(new_state, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    lines = ["%-16s v%-3d %6d questions %9d octets  %s%s" % (e["scope"], e["version"], e["questions"], e["bytes"], e["file"], "  (nouvelle version)" if changed[e["scope"]] else "")
             for e in built]
    lines.append("%-16s      %6d questions %9d octets  (plafond par lot : %d)" % ("TOTAL", sum(e["questions"] for e in built), total, max_bytes))
    return catalog, lines
