#!/usr/bin/env python3
"""Size gate shared by the CODE repository and the CONTENT repository (docs/CONTENT-PUBLISH.md § 5).

  check_sizes.py [--root DIR] [--mode code|content]

  both:    no file above 50 MB
  code:    no binary above 5 MB, and NO heavy media (video, audio, images) under content/ (they live in the content repository)
  content: no binary above 5 MB unless its extension is tracked by Git LFS in .gitattributes
Exit 1 on any violation.
"""
import argparse, os, re, sys

MB = 1 << 20
HEAVY = {".webm", ".mp4", ".mkv", ".mov", ".opus", ".ogg", ".mp3", ".m4a", ".wav", ".webp", ".png", ".jpg", ".jpeg", ".gif"}
TEXT = {".json", ".md", ".txt", ".py", ".sh", ".kt", ".kts", ".java", ".xml", ".yml", ".yaml", ".html", ".css", ".js", ".sql", ".csv", ".svg", ".properties", ".swift", ".gradle"}
NATIVE_OK = {".so"}   # native libraries of the apps (e.g. aria2c) are code, not content
SKIP = {".git", "build", ".gradle", ".core-harness", "node_modules", "target", "__pycache__"}


def lfs_exts(root):
    p = os.path.join(root, ".gitattributes"); out = set()
    if os.path.isfile(p):
        for line in open(p, encoding="utf-8"):
            m = re.match(r"^\*(\.\w+)\s.*filter=lfs", line)
            if m:
                out.add(m.group(1).lower())
    return out


def check(root, mode):
    errs = []
    lfs = lfs_exts(root)
    for r, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP]
        for fn in files:
            p = os.path.join(r, fn); rel = os.path.relpath(p, root).replace(os.sep, "/"); ext = os.path.splitext(fn)[1].lower()
            size = os.path.getsize(p)
            if size > 50 * MB:
                errs.append("%s: %.1f Mo > 50 Mo" % (rel, size / MB)); continue
            if mode == "code" and rel.startswith("content/") and ext in HEAVY and not rel.startswith("content/quiz/"):
                errs.append("%s: média lourd dans le dépôt de code (à placer dans castbridge-content)" % rel)
            if ext not in TEXT and ext not in NATIVE_OK and ext != ".zip" and size > 5 * MB and not (mode == "content" and ext in lfs):
                errs.append("%s: binaire de %.1f Mo > 5 Mo%s" % (rel, size / MB, " (non suivi par Git LFS)" if mode == "content" else ""))
    return errs


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    here = os.path.dirname(os.path.abspath(__file__))
    ap.add_argument("--root", default=os.path.normpath(os.path.join(here, "..", ".."))); ap.add_argument("--mode", choices=("code", "content"), default="code")
    a = ap.parse_args(argv)
    errs = check(a.root, a.mode)
    for e in errs:
        print("ERREUR: " + e, file=sys.stderr)
    print("Tailles OK (%s)." % a.mode if not errs else "%d violation(s)." % len(errs))
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
