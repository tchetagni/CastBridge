"""Import of batches written by experts or by an AI, to be reviewed by a human before they are played.

A batch is a JSON file: either a list of questions or {"batch": "name", "author": "...", "date": "...", "questions": [...]}.
A question needs: course (any key of core.COURSES, e.g. general | cm2 | l1-droit | l1-geo; the 26 higher-education cells are
in courses_sup.py, see docs/QUIZ-CONTENT-HIGHER.md) or track/level/field, category,
difficulty 1..5, question, choices (4), answer (0-3 or A-D), explanation, source; and region (CM | AF | WORLD) for
general knowledge. Imported questions always enter as status "review": only approvals.json (a named human reviewer)
makes them "approved". Invalid questions are listed and left out, the valid ones are stored normalized in
content/quiz/batches/<name>.json.
"""
import json
from pathlib import Path

from . import qc
from .core import COURSES, STATUS_REVIEW, norm, sha

BY_TRIPLE = {(c["track"], c["level"], c["field"]): k for k, c in COURSES.items()}


def normalize(raw, batch, author):
    """One raw question -> bank question (or (None, reason))."""
    if "course" in raw:
        if raw["course"] not in COURSES:
            return None, "parcours inconnu : %r" % raw["course"]
        course = raw["course"]
    else:
        course = BY_TRIPLE.get((raw.get("track", "general"), raw.get("level"), raw.get("field")))
        if course is None:
            return None, "parcours inconnu (track/level/field)"
    c = COURSES[course]
    ans = raw.get("answer")
    if isinstance(ans, str) and ans.strip().upper() in "ABCD" and len(ans.strip()) == 1:
        ans = "ABCD".index(ans.strip().upper())
    region = raw.get("region") or ("CM" if course == "general" else "WORLD")
    text = (raw.get("question") or "").strip()
    right = (raw.get("choices") or [""] * 4)[ans] if isinstance(ans, int) and 0 <= ans < len(raw.get("choices") or []) else ""
    q = {
        "id": raw.get("id") or "%s-%s" % (c["prefix"], sha(course + "|" + text + "|" + right)[:8]),
        "track": c["track"], "level": c["level"], "field": c["field"], "region": region,
        "category": raw.get("category", ""), "difficulty": raw.get("difficulty"), "question": text,
        "choices": [str(x).strip() for x in raw.get("choices", [])], "answer": ans,
        "explanation": (raw.get("explanation") or "").strip(), "source": (raw.get("source") or "").strip(),
        "status": STATUS_REVIEW, "verif": "import", "lang": raw.get("lang", "fr"), "tpl": None,
        "batch": batch, "author": author,
    }
    return q, None


def import_file(path, out_dir):
    """Validates a batch file; writes the valid questions to out_dir/<batch>.json. Returns (count, problems)."""
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, list):
        data = {"questions": data}
    batch = data.get("batch") or path.stem
    author = data.get("author", "")
    good, problems, seen = [], [], set()
    for i, raw in enumerate(data.get("questions", []), 1):
        q, why = normalize(raw, batch, author)
        if q is None:
            problems.append("#%d %s" % (i, why)); continue
        e, _ = qc.check_question(q)
        key = norm(q["question"])
        if key in seen:
            e.append("doublon dans le lot")
        seen.add(key)
        if e:
            problems.append("#%d %s : %s" % (i, q["question"][:50], "; ".join(e)))
            continue
        good.append(q)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / (batch + ".json")).write_text(json.dumps({"batch": batch, "author": author, "questions": good}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return len(good), problems


def load_batches(dir_):
    out = []
    if dir_.is_dir():
        for f in sorted(dir_.glob("*.json")):
            data = json.loads(f.read_text(encoding="utf-8"))
            for q in data.get("questions", []):
                q.setdefault("tpl", None)
                q["verif"] = "import"
                out.append(q)
    return out
