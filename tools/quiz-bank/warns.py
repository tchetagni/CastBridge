#!/usr/bin/env python3
"""Developer aid: prints some warned questions with the question they resemble: warns.py [n]."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402

n = int(sys.argv[1]) if len(sys.argv) > 1 else 15
qs, good, res, _ = quizbank.prepare()
byid = {q["id"]: q for q in qs}
shown = 0
for i, ws in res["warnings"].items():
    for w in ws:
        parts = w.split(" ")
        other = byid.get(parts[2]) if w.startswith("quasi-doublon") else None
        q = byid[i]
        print(w[:60], "|", q["tpl"], "|", q["question"], "->", q["choices"][q["answer"]])
        if other:
            print("      ~", other["tpl"], "|", other["question"])
        shown += 1
    if shown >= n:
        break
