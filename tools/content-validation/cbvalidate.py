#!/usr/bin/env python3
"""CastBridge content validation tool (docs/CONTENT-VALIDATION.md).

    cbvalidate.py check                     read the validation records, check format / transitions / stale hashes against the sources
    cbvalidate.py apply [--dry-run]         write the effective decisions into the sources (quiz: content/quiz/approvals.json,
                                            Apprendre: status / review / state of the lessons and exercises in content/learn/**)
    cbvalidate.py rebuild                   apply, then rebuild the quiz lots (tools/quiz-bank/quizbank.py build)
                                            and the Apprendre lots (tools/build-learn-packs)
    cbvalidate.py index [--out FILE]        one JSON line per item (kind, id, lot, class, subject, hash, state, text) for the server's
                                            review queue (admin page « Contenus » > import)
    cbvalidate.py import-csv FILE           append the decisions of a reviewer's CSV (id;state;reviewer;date;note) to the records
    cbvalidate.py budget [--limit-gb 3]     total size of the lots (dist + built packs), per feature and per lot; exit 1 above the limit

Records: content/validation/*.jsonl, one JSON object per line {id, kind, state, reviewer, date, note, hash, lot}; they are never
part of a signed lot. Only the Python standard library is needed. The content hash recipe is the one of
android/core castbridge.core.content.ContentHash (test vectors in both).
"""
import argparse
import csv
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VALIDATION = ROOT / "content" / "validation"
QUIZ_DIST = ROOT / "content" / "quiz" / "dist"
APPROVALS = ROOT / "content" / "quiz" / "approvals.json"
LEARN = ROOT / "content" / "learn"
LEARN_BUILT = ROOT / "android" / "core" / "build" / "learn-packs"

STATES = ("review", "validated", "rejected", "needs-fix")
NEXT = {"review": {"validated", "rejected", "needs-fix"}, "validated": {"review", "needs-fix", "rejected"},
        "needs-fix": {"review", "rejected"}, "rejected": {"review", "needs-fix"}}
ALIASES = {"draft": "review", "beta": "review", "reviewed": "review", "approved": "validated", "needs_fix": "needs-fix", "needsfix": "needs-fix"}
ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.:+-]{0,63}")
DATE = re.compile(r"\d{4}-\d{2}-\d{2}")
HASH = re.compile(r"[0-9a-f]{16}")


# ---------------------------------------------------------------- content hash (same recipe as ContentHash.kt)
_WS = re.compile(r"[ \t\n\r\x0b\x0c]+")


def norm(s):
    return _WS.sub(" ", s or "").strip(" \t\n\r\x0b\x0c")


def h(*parts):
    return hashlib.sha256("\x1f".join(norm(p) for p in parts).encode("utf-8")).hexdigest()[:16]


def _list(xs):
    return "\x1e".join(sorted(norm(x) for x in xs))


def num(x):
    return format(Decimal(repr(float(x))).normalize(), "f")


def question_hash(q):
    ch = q["choices"]
    return h("q", q["question"], _list(ch), ch[q["answer"]] if 0 <= q["answer"] < len(ch) else "", q.get("explanation") or "")


def exercise_hash(x):
    kind = x["kind"]
    ans = x.get("answer")
    ch = x.get("choices") or []
    if kind == "mcq":
        a = ch[ans] if isinstance(ans, int) and 0 <= ans < len(ch) else ""
    elif kind == "truefalse":
        a = "true" if ans is True else "false" if ans is False else ""
    elif kind == "numeric":
        a = (num(ans) if ans is not None else "") + "|" + num(x.get("tolerance", 0.0))
    elif kind == "matching":
        a = _list([p[0] + "=" + p[1] for p in x.get("pairs") or []])
    elif kind == "open":
        a = x.get("model") or ""
    else:
        a = ",".join(exercise_hash(p) for p in x.get("parts") or [])
    return h("x", kind, x["prompt"], x.get("tex") or "", _list(ch), a, x.get("explanation") or "")


def lesson_hash(lesson):
    """Lessons are hashed from their canonical JSON without the validation keys (status, state, review)."""
    def strip(v):
        if isinstance(v, dict):
            return {k: strip(x) for k, x in v.items() if k not in ("status", "state", "review")}
        if isinstance(v, list):
            return [strip(x) for x in v]
        return v
    text = json.dumps(strip(lesson), sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]


# ---------------------------------------------------------------- sources
def quiz_items():
    """Questions of the built lots (content/quiz/dist): id -> dict(kind, lot, cls, subject, hash, text, status)."""
    out = {}
    for z in sorted(QUIZ_DIST.glob("*.quiz.zip")):
        with zipfile.ZipFile(z) as zf:
            m = json.loads(zf.read("manifest.json"))
            for q in json.loads(zf.read("questions.json"))["questions"]:
                out[q["id"]] = {"kind": "question", "id": q["id"], "lot": "quiz/" + m["course"], "cls": q.get("level") or q["track"],
                                "subject": q.get("category") or "", "hash": question_hash(q), "text": q["question"][:140],
                                "state": norm(q.get("status") or "review"), "difficulty": q.get("difficulty")}
    return out


def learn_files():
    return sorted(LEARN.glob("*/lessons/*.json"))


def learn_items():
    """Lessons and exercises of content/learn: id -> dict."""
    out = {}
    for pd in sorted(p for p in LEARN.iterdir() if (p / "pack.json").is_file()):
        pack = json.loads((pd / "pack.json").read_text(encoding="utf-8"))
        for f in sorted((pd / "lessons").glob("*.json")):
            d = json.loads(f.read_text(encoding="utf-8"))
            for l in d.get("lessons", []):
                out[l["id"]] = {"kind": "lesson", "id": l["id"], "lot": "learn/" + pack["id"], "cls": pack.get("level", ""),
                                "subject": pack.get("subject", ""), "hash": lesson_hash(l), "text": l["title"][:140],
                                "state": l.get("state") or ("validated" if l.get("status") == "validated" else "review")}
            for x in d.get("exercises", []):
                out[x["id"]] = {"kind": "exercise", "id": x["id"], "lot": "learn/" + pack["id"], "cls": pack.get("level", ""),
                                "subject": pack.get("subject", ""), "hash": exercise_hash(x), "text": x["prompt"][:140],
                                "state": x.get("state") or ("review" if x.get("review") else "validated"), "difficulty": x.get("difficulty")}
    return out


def all_items():
    items = quiz_items()
    items.update(learn_items())
    return items


# ---------------------------------------------------------------- records
def state_of(raw):
    s = (raw or "").strip().lower()
    s = ALIASES.get(s, s)
    return s if s in STATES else None


def problems(r):
    e = []
    if not ID.fullmatch(r.get("id", "")):
        e.append("id invalide")
    if r.get("kind") not in ("question", "lesson", "exercise"):
        e.append("kind inconnu")
    if state_of(r.get("state")) is None:
        e.append("state inconnu")
    rv = r.get("reviewer", "")
    if not rv.strip() or len(rv) > 60:
        e.append("relecteur manquant")
    if not DATE.fullmatch(r.get("date", "")):
        e.append("date invalide (AAAA-MM-JJ)")
    if len(r.get("note", "")) > 500:
        e.append("note trop longue")
    st = state_of(r.get("state"))
    if st in ("rejected", "needs-fix") and not r.get("note", "").strip():
        e.append("une note est obligatoire pour " + str(st))
    if st == "validated" and not HASH.fullmatch(r.get("hash") or ""):
        e.append("empreinte (hash) obligatoire pour valider")
    return e


def load_records(errors):
    """Last record of each id wins; every move must be legal. Returns (effective, history)."""
    history = {}
    for f in sorted(VALIDATION.glob("*.jsonl")):
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if not line.strip() or line.startswith("#"):
                continue
            try:
                r = json.loads(line)
            except ValueError:
                errors.append("%s:%d: JSON illisible" % (f.name, n))
                continue
            bad = problems(r)
            if bad:
                errors.append("%s:%d: %s: %s" % (f.name, n, r.get("id"), ", ".join(bad)))
                continue
            r["state"] = state_of(r["state"])
            hist = history.setdefault(r["id"], [])
            cur = hist[-1]["state"] if hist else "review"
            if r["state"] != cur and r["state"] not in NEXT[cur]:
                errors.append("%s:%d: %s: %s -> %s n'est pas une transition permise" % (f.name, n, r["id"], cur, r["state"]))
                continue
            hist.append(r)
    return {i: hs[-1] for i, hs in history.items()}, history


def effective_state(rec, current_hash):
    """(state, stale): a decision made on another content hash is void, the item goes back to review."""
    if rec.get("hash") and current_hash and rec["hash"] != current_hash:
        return "review", True
    return rec["state"], False


# ---------------------------------------------------------------- textual patching of the learn sources
def _scan_string(t, i):
    i += 1
    while t[i] != '"':
        i += 2 if t[i] == "\\" else 1
    return i + 1


def _skip_ws(t, i):
    while t[i] in " \t\r\n":
        i += 1
    return i


def _skip_value(t, i):
    i = _skip_ws(t, i)
    c = t[i]
    if c == '"':
        return _scan_string(t, i)
    if c in "{[":
        depth = 0
        while True:
            c = t[i]
            if c == '"':
                i = _scan_string(t, i)
                continue
            if c in "{[":
                depth += 1
            elif c in "}]":
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
    j = i
    while t[j] not in ",}] \t\r\n":
        j += 1
    return j


def object_keys(t, start):
    """For the object starting at t[start] == '{': (end, {key: (key_start, value_start, value_end)}) of its top-level keys."""
    keys = {}
    i = start + 1
    while True:
        i = _skip_ws(t, i)
        if t[i] == "}":
            return i + 1, keys
        if t[i] == ",":
            i += 1
            continue
        ks = i
        ke = _scan_string(t, i)
        key = json.loads(t[ks:ke])
        i = _skip_ws(t, ke)
        assert t[i] == ":"
        vs = _skip_ws(t, i + 1)
        ve = _skip_value(t, vs)
        keys[key] = (ks, vs, ve)
        i = ve


def array_objects(t, start):
    """Spans (start, end) of the objects of the array starting at t[start] == '['."""
    spans = []
    i = start + 1
    while True:
        i = _skip_ws(t, i)
        if t[i] == "]":
            return spans
        if t[i] == ",":
            i += 1
            continue
        end = _skip_value(t, i)
        if t[i] == "{":
            spans.append((i, end))
        i = end


def patch_object(t, start, changes):
    """Sets keys of the object at t[start] textually: existing values are replaced in place, new keys go right after '{'."""
    end, keys = object_keys(t, start)
    edits = []
    new = []
    for k, v in changes.items():
        text = json.dumps(v, ensure_ascii=False)
        if k in keys:
            if t[keys[k][1]:keys[k][2]] != text:
                edits.append((keys[k][1], keys[k][2], text))
        elif v is not None:
            new.append((k, text))
    if new:
        # indentation of the first key of the object, to keep the file's style
        first = min(keys.values())[0] if keys else start + 1
        ls = t.rfind("\n", 0, first) + 1
        indent = t[ls:first] if t[ls:first].strip() == "" else "  "
        ins = "".join("\n%s%s: %s," % (indent, json.dumps(k), x) for k, x in new)
        edits.append((start + 1, start + 1, ins))
    for a, b, x in sorted(edits, reverse=True):
        t = t[:a] + x + t[b:]
    return t


def apply_learn(effective, dry):
    """Writes status / review / state of the lessons and exercises that have a record. Returns the number of changed objects."""
    changed = 0
    for f in learn_files():
        t = f.read_text(encoding="utf-8")
        d = json.loads(t)
        orig = t
        for arr in ("exercises", "lessons"):
            for item in reversed(d.get(arr, [])):
                rec = effective.get(item["id"])
                if rec is None:
                    continue
                cur = lesson_hash(item) if arr == "lessons" else exercise_hash(item)
                st, _ = effective_state(rec, cur)
                ch = {"state": st}
                if arr == "lessons":
                    ch["status"] = "validated" if st == "validated" else "draft"
                else:
                    ch["review"] = st != "validated"
                # locate the object textually: the Nth object of the array
                m = re.search(r'"%s"\s*:\s*\[' % arr, t)
                spans = array_objects(t, m.end() - 1)
                idx = [x["id"] for x in d[arr]].index(item["id"])
                a, b = spans[idx]
                new = patch_object(t, a, ch)
                if new != t:
                    t = new
                    changed += 1
        if t != orig:
            json.loads(t)
            if not dry:
                f.write_text(t, encoding="utf-8")
    return changed


def apply_quiz(effective, items, dry):
    cur = json.loads(APPROVALS.read_text(encoding="utf-8")) if APPROVALS.is_file() else {}
    new = {}
    for i, a in cur.items():  # hand-made approvals (quizbank.py approve) stay unless a record says otherwise
        if i not in effective:
            new[i] = a
    for i, rec in effective.items():
        if rec["kind"] != "question":
            continue
        it = items.get(i)
        st, stale = effective_state(rec, it["hash"] if it else None)
        if stale or st == "review":
            continue
        new[i] = {"status": "approved" if st == "validated" else st, "by": rec["reviewer"], "on": rec["date"], "note": rec.get("note", ""),
                  "hash": rec.get("hash")}
    new = dict(sorted(new.items()))
    changed = new != cur
    if changed and not dry:
        APPROVALS.parent.mkdir(parents=True, exist_ok=True)
        APPROVALS.write_text(json.dumps(new, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return changed


# ---------------------------------------------------------------- commands
def cmd_check(args):
    errors = []
    eff, hist = load_records(errors)
    items = all_items()
    stale = unknown = 0
    for i, r in eff.items():
        it = items.get(i)
        if it is None:
            unknown += 1
            print("INCONNU %s : aucun contenu avec cet id" % i)
            continue
        if effective_state(r, it["hash"])[1]:
            stale += 1
            print("PÉRIMÉ %s : le contenu a changé depuis la décision du %s (%s) : retour en relecture" % (i, r["date"], r["reviewer"]))
    for e in errors:
        print("ERREUR", e)
    by = {}
    for r in eff.values():
        by[r["state"]] = by.get(r["state"], 0) + 1
    print("%d décisions (%s), %d périmées, %d ids inconnus, %d éléments dans les sources" % (len(eff), by, stale, unknown, len(items)))
    return 1 if errors else 0


def cmd_apply(args):
    errors = []
    eff, _ = load_records(errors)
    for e in errors:
        print("ERREUR", e)
    if errors:
        return 1
    q = apply_quiz(eff, quiz_items(), args.dry_run)
    n = apply_learn(eff, args.dry_run)
    print("quiz : approvals.json %s ; Apprendre : %d objet(s) modifié(s)%s" % ("modifié" if q else "inchangé", n, " (essai à blanc)" if args.dry_run else ""))
    return 0


def cmd_rebuild(args):
    if cmd_apply(argparse.Namespace(dry_run=False)):
        return 1
    subprocess.check_call([sys.executable, str(ROOT / "tools" / "quiz-bank" / "quizbank.py"), "build"])
    subprocess.check_call([str(ROOT / "tools" / "build-learn-packs")])
    return 0


def cmd_index(args):
    out = open(args.out, "w", encoding="utf-8") if args.out else sys.stdout
    for it in all_items().values():
        out.write(json.dumps({k: it[k] for k in ("kind", "id", "lot", "cls", "subject", "hash", "text", "state", "difficulty") if it.get(k) is not None},
                             ensure_ascii=False) + "\n")
    if args.out:
        out.close()
    return 0


def cmd_import_csv(args):
    items = all_items()
    added, bad = 0, 0
    with open(args.file, encoding="utf-8-sig", newline="") as fh:
        sample = fh.read(2048)
        fh.seek(0)
        dialect = csv.Sniffer().sniff(sample, delimiters=";,\t") if sample else csv.excel
        rows = list(csv.DictReader(fh, dialect=dialect))
    out = []
    for n, row in enumerate(rows, 2):
        i = (row.get("id") or "").strip()
        st = state_of(row.get("state"))
        if not i or st is None or st == "review":
            continue
        it = items.get(i)
        if it is None:
            print("ligne %d : id inconnu %s" % (n, i))
            bad += 1
            continue
        rec = {"id": i, "kind": it["kind"], "state": st, "reviewer": (row.get("reviewer") or "").strip(), "date": (row.get("date") or "").strip(),
               "note": (row.get("note") or "").strip(), "hash": it["hash"], "lot": it["lot"]}
        p = problems(rec)
        if p:
            print("ligne %d : %s : %s" % (n, i, ", ".join(p)))
            bad += 1
            continue
        out.append(rec)
    if out and not args.dry_run:
        VALIDATION.mkdir(parents=True, exist_ok=True)
        with open(VALIDATION / args.into, "a", encoding="utf-8") as fh:
            for r in out:
                fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    print("%d décision(s) ajoutée(s), %d ligne(s) refusée(s)" % (len(out), bad))
    return 1 if bad else 0


def cmd_budget(args):
    limit = int(args.limit_gb * 1e9)
    lots = []
    for z in sorted(QUIZ_DIST.glob("*.zip")):
        lots.append(("quiz", z.name, z.stat().st_size))
    for d in (LEARN_BUILT,):
        for z in sorted(d.glob("*.zip")) if d.is_dir() else []:
            lots.append(("learn", z.name, z.stat().st_size))
    for extra in args.dir or []:
        for z in sorted(Path(extra).glob("*.zip")):
            lots.append(("autre", z.name, z.stat().st_size))
    total = sum(s for _, _, s in lots)
    per = {}
    for f, _, s in lots:
        per[f] = per.get(f, 0) + s
    for f, s in sorted(per.items()):
        print("%-8s %10.2f Mo" % (f, s / 1e6))
    for f, n, s in sorted(lots, key=lambda x: -x[2])[:10]:
        print("   %-8s %-44s %10.2f Mo" % (f, n, s / 1e6))
    print("TOTAL    %10.2f Mo sur %.0f Mo autorisés (%d %%)" % (total / 1e6, limit / 1e6, 100 * total // limit))
    if total > limit:
        print("ÉCHEC : le budget de contenu est dépassé")
        return 1
    if total > 0.8 * limit:
        print("ATTENTION : plus de 80 % du budget de contenu")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check").set_defaults(fn=cmd_check)
    a = sub.add_parser("apply"); a.add_argument("--dry-run", action="store_true"); a.set_defaults(fn=cmd_apply)
    sub.add_parser("rebuild").set_defaults(fn=cmd_rebuild)
    i = sub.add_parser("index"); i.add_argument("--out"); i.set_defaults(fn=cmd_index)
    c = sub.add_parser("import-csv"); c.add_argument("file"); c.add_argument("--into", default="imports.jsonl"); c.add_argument("--dry-run", action="store_true")
    c.set_defaults(fn=cmd_import_csv)
    b = sub.add_parser("budget"); b.add_argument("--limit-gb", type=float, default=3.0); b.add_argument("--dir", action="append"); b.set_defaults(fn=cmd_budget)
    args = ap.parse_args(argv)
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
