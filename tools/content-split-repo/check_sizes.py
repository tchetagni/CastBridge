#!/usr/bin/env python3
"""Size gate shared by the CODE repository and the CONTENT repository (docs/CONTENT-PUBLISH.md § 5).

  check_sizes.py [--root DIR] [--mode code|content]

  both:    no file above 50 MB
  code:    no binary above 5 MB, and NO heavy media (video, audio, images) under content/ (they live in the content repository)
  content: no binary above 5 MB unless its extension is tracked by Git LFS in .gitattributes
Exit 1 on any violation.

Ce que la garde juge (choix du 2026-10-14) : le DÉPÔT, pas le disque du poste. Les fichiers que git IGNORE (copies locales du propriétaire, par exemple
`content/langues-media/`, règle de `.gitignore` « jamais dans le dépôt de code, ils vont dans castbridge-content ») ne sont pas jugés. L'ensemble jugé est celui de
`git ls-files --cached --others --exclude-standard` (fichiers suivis + non suivis non ignorés = ce que `git add -A` y mettrait), lu en UN seul appel : un
`git check-ignore` par fichier serait bien plus lent. Un média lourd non suivi ET non ignoré reste donc refusé. Repli sur le parcours complet du système de fichiers
(comportement d'avant) quand git est absent, quand `--root` n'est pas la racine d'un dépôt git (export du dépôt de contenu avant `git init`, même posé sous `build/`
d'un autre dépôt) : voir `lotlib.git_ignore_filter`.
"""
import argparse, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "content-lib"))   # copié avec ce script dans castbridge-content (split_repo.sh)
import lotlib as L  # noqa: E402

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
    ignored = L.git_ignore_filter(root, whole_repo=True)   # chemins que git ignore : hors du dépôt, donc non jugés
    for r, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in SKIP]
        for fn in files:
            p = os.path.join(r, fn); rel = os.path.relpath(p, root).replace(os.sep, "/"); ext = os.path.splitext(fn)[1].lower()
            if ignored(rel):
                continue
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
