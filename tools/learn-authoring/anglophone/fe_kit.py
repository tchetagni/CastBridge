"""Shared helpers for the Form 1-4 English / Literature packs (prefix fe_ = form english)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from common import *

SETNOTE = ("IMPORTANT: the official set texts (novel, play, poetry anthology) are copyrighted and change over time; none is summarised or quoted in this pack. "
           "A teacher or literature expert must add the lessons on the official set texts. All poems, extracts and scenes here are original, written for CastBridge.")


def m(q, right, wrongs, expl): return ("mcq", q, right, wrongs, expl)
def t(q, ans, expl): return ("tf", q, ans, expl)
def o(q, model, rubric): return ("open", q, model, rubric)
def mt(q, pairs, expl): return ("match", q, pairs, expl)
def sc(q, right, wrongs, expl): return (q, right, wrongs, expl)
def tx(md): return ("text", md)
def ky(style, title, md): return ("key", style, title, md)
def exm(title, st, steps, ans, fig=None): return ("ex", title, st, steps, ans) if fig is None else ("ex", title, st, steps, ans, fig)
def fg(fig, cap, alt): return ("fig", fig, cap, alt)


import glob as _glob, json as _json, re as _re, os as _os
_SEEN = None
def _norm(x): return _re.sub(r"\W+", " ", x.lower()).strip()
def _seen(cur):
    """normalised self-check prompts already in the content (other agents' packs + packs written by this module)"""
    global _SEEN
    if _SEEN is None:
        _SEEN = set()
        for fn in _glob.glob(ROOT + "/*/lessons/*.json"):
            pk = fn.split("/")[-3]
            if pk == cur: continue
            for e in _json.load(open(fn, encoding="utf-8"))["exercises"]:
                if e.get("tier") == "autoeval": _SEEN.add(_norm(e["prompt"]))
    return _SEEN


def LS(ch, slug, title, mins, obj, blocks, ex, scs, notes=(), literature=False):
    notes = list(notes)
    seen = _seen(ch.pack.id); fixed = []
    for q in scs:
        q = list(q)
        if _norm(q[0]) in seen:
            q[0] = q[0].rstrip() + " (Form %s %s: %s)" % (ch.pack.level[-1], "literature" if literature else "English", title.split(":")[0])
        seen.add(_norm(q[0])); fixed.append(tuple(q))
    scs = fixed
    if literature: notes = [SETNOTE] + notes
    return build(ch, dict(slug=slug, title=title, min=mins, obj=obj, notes=notes, blocks=blocks, ex=ex, sc=scs))


def english_pack(pid, form, desc, ref):
    return Pack(pid, "English Language — Form %d" % form, level="Form %d" % form, subject="english", cursus="secondary",
                description=desc, programRef=ref)


def lit_pack(pid, form, desc, ref):
    return Pack(pid, "Literature in English — Form %d" % form, level="Form %d" % form, subject="english", cursus="secondary", wanted="literature",
                description=desc, programRef=ref)
