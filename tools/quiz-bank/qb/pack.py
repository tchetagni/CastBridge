"""Content packs: one zip per course part (manifest.json + questions.json), plus catalog.json.

A pack is a STRATIFIED slice of a course (every part has the same region / difficulty mix), so any downloaded part is a
playable mini-bank and each further part adds about the same number of fresh games.
"""
import hashlib
import io
import json
import zipfile
from collections import Counter

from .core import COURSES, sha

PART_SIZE = 1500               # questions per part: ~100 games; roughly 200-280 KB zipped
PACK_SUFFIX = ".quiz.zip"
FIELDS = ("id", "track", "level", "field", "region", "category", "difficulty", "question", "choices", "answer",
          "explanation", "source", "status", "verif", "lang")


def pack_name(course, part, version):
    return "quiz-%s-p%d-v%d%s" % (course, part, version, PACK_SUFFIX)


def split_parts(qs, part_size=PART_SIZE):
    """Stratified split: sort by (region, difficulty, hash) and deal round-robin into ceil(n / part_size) parts."""
    n = max(1, -(-len(qs) // part_size))
    order = sorted(qs, key=lambda q: (q["region"], q["difficulty"], sha(q["id"])))
    parts = [[] for _ in range(n)]
    for i, q in enumerate(order):
        parts[i % n].append(q)
    return parts


def _zip_bytes(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(files.items()):
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, data, compresslevel=9)
    return buf.getvalue()


def question_json(q):
    return {k: q[k] for k in FIELDS if k in q}


def make_pack(course, part, parts, qs, version):
    c = COURSES[course]
    body = "{\"version\":2,\"questions\":[\n" + ",\n".join(json.dumps(question_json(q), ensure_ascii=False, separators=(",", ":")) for q in sorted(qs, key=lambda q: q["id"])) + "\n]}\n"
    qbytes = body.encode("utf-8")
    manifest = {
        "format": 1, "id": "%s-p%d" % (course, part), "course": course, "track": c["track"], "level": c["level"], "field": c["field"],
        "part": part, "parts": parts, "version": version, "questions": len(qs),
        "byRegion": dict(sorted(Counter(q["region"] for q in qs).items())),
        "byDifficulty": {str(d): n for d, n in sorted(Counter(q["difficulty"] for q in qs).items())},
        "statuses": dict(sorted(Counter(q["status"] for q in qs).items())),
        "files": {"questions.json": {"size": len(qbytes), "sha256": hashlib.sha256(qbytes).hexdigest()}},
    }
    data = _zip_bytes({"manifest.json": json.dumps(manifest, ensure_ascii=False, indent=1).encode("utf-8"), "questions.json": qbytes})
    return manifest, data


def build_packs(questions, out_dir, version, part_size=PART_SIZE):
    """Writes every pack + catalog.json into out_dir; returns the catalog dict."""
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*" + PACK_SUFFIX):
        old.unlink()
    entries = []
    for course, c in COURSES.items():
        qs = [q for q in questions if (q["track"], q["level"], q["field"]) == (c["track"], c["level"], c["field"])]
        if not qs:
            continue
        parts = split_parts(qs, part_size)
        for i, part in enumerate(parts, 1):
            manifest, data = make_pack(course, i, len(parts), part, version)
            name = pack_name(course, i, version)
            (out_dir / name).write_bytes(data)
            entries.append({"id": manifest["id"], "course": course, "track": c["track"], "level": c["level"], "field": c["field"],
                            "part": i, "parts": len(parts), "version": version, "file": name, "size": len(data),
                            "sha256": hashlib.sha256(data).hexdigest(), "questions": len(part),
                            "byRegion": manifest["byRegion"], "byDifficulty": manifest["byDifficulty"]})
    catalog = {"format": 1, "version": version, "packs": entries}
    (out_dir / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return catalog


def build_scope_packs(questions, out_dir, version, courses, part_size=PART_SIZE):
    """Comme build_packs mais limité à `courses` : les fichiers des autres parcours restent intacts. Les anciens fichiers de ces
    parcours (toute version) sont retirés, leurs entrées du catalogue remplacées, celles des autres parcours conservées."""
    out_dir.mkdir(parents=True, exist_ok=True)
    cat_file = out_dir / "catalog.json"
    old = json.loads(cat_file.read_text(encoding="utf-8")) if cat_file.is_file() else {"format": 1, "version": version, "packs": []}
    entries = [e for e in old["packs"] if e["course"] not in courses]
    for course in courses:
        for f in out_dir.glob("quiz-%s-p*%s" % (course, PACK_SUFFIX)):
            f.unlink()
        c = COURSES[course]
        qs = [q for q in questions if (q["track"], q["level"], q["field"]) == (c["track"], c["level"], c["field"])]
        if not qs:
            continue
        parts = split_parts(qs, part_size)
        for i, part in enumerate(parts, 1):
            manifest, data = make_pack(course, i, len(parts), part, version)
            name = pack_name(course, i, version)
            (out_dir / name).write_bytes(data)
            entries.append({"id": manifest["id"], "course": course, "track": c["track"], "level": c["level"], "field": c["field"],
                            "part": i, "parts": len(parts), "version": version, "file": name, "size": len(data),
                            "sha256": hashlib.sha256(data).hexdigest(), "questions": len(part),
                            "byRegion": manifest["byRegion"], "byDifficulty": manifest["byDifficulty"]})
    catalog = {"format": old.get("format", 1), "version": old.get("version", version), "packs": entries}
    cat_file.write_text(json.dumps(catalog, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return catalog
