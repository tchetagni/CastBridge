#!/usr/bin/env python3
"""Generates tools/anim/examples/<template>.json (one complete « illustration » block per template) and prints their sizes.
Next: `cd android && gradle :core:test --tests 'castbridge.core.LearnAnimationExamplesTest'` parses, validates, lints and renders them
to PNG contact sheets (android/core/build/anim-sheets/). Usage: tools/anim/build.py [name ...]"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import animlib, templates

def main(names):
    out = os.path.join(os.path.dirname(__file__), "examples")
    os.makedirs(out, exist_ok=True)
    rows = []
    for name, fn in templates.EXAMPLES.items():
        if names and name not in names: continue
        block = fn()
        with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as f:
            f.write(animlib.dumps(block) + "\n")
        rows.append((name, animlib.size_bytes(block), len(animlib.dumps(block).encode())))
    for n, a, b in rows: print(f"{n:28s} animation {a:6d} octets   bloc complet {b:6d} octets")
    if rows: print(f"{len(rows)} modèles, animation moyenne {sum(a for _, a, _ in rows) // len(rows)} octets, max {max(a for _, a, _ in rows)}")

if __name__ == "__main__":
    main(sys.argv[1:])
