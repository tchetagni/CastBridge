#!/usr/bin/env python3
"""Liste les routes HTTP servies par CastBridge-TV (extraites du code) dans tools/routes/routes.txt.

Usage : list_routes.py [--check]   (--check : échoue si routes.txt committé diffère de l'extraction)
Les lignes de `routes.txt` après un commentaire `# manuel` sont conservées telles quelles (routes que l'extraction rate).
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "tools" / "routes" / "routes.txt"
SRC = [ROOT / "android/core/src/main/kotlin/castbridge/core", ROOT / "android/receiver/src/main/kotlin/castbridge/receiver"]
LIT = r'"(/(?:api|upload|stream|quiz|chess)(?:/[A-Za-z0-9_.\-/]*)?|/)"'
CMP = re.compile(r'(?:\b(?:path|uri|p)\s*(?:==|!=|\.startsWith\()\s*' + LIT + r'|^\s*' + LIT + r'(?:\s*,\s*"[^"]*")*\s*->)')
CMP_ANY = re.compile(r'\b(?:path|uri)\s*(?:==|!=|\.startsWith\(|\.removePrefix\()|\bwantsBody|^\s*"/[^"]*"(?:\s*,\s*"[^"]*")*\s*->')
LITS = re.compile(LIT)
MANUAL = "# manuel"


def extract():
    routes = set()
    for base in SRC:
        for f in sorted(base.rglob("*.kt")):
            if "/test/" in str(f):
                continue
            for line in f.read_text(encoding="utf-8").splitlines():
                if not CMP_ANY.search(line) or "http://" in line or "https://" in line:
                    continue
                for m in LITS.finditer(line):
                    r = m.group(1)
                    # un préfixe `startsWith("/api/")` n'est pas une route
                    if r.endswith("/") and r != "/" and not r.startswith(("/stream", "/upload", "/quiz", "/chess")):
                        continue
                    routes.add(r if r == "/" else r.rstrip("/") if r.startswith(("/quiz", "/chess")) else r)
    return routes


def manual_lines():
    if not OUT.exists():
        return []
    txt = OUT.read_text(encoding="utf-8").splitlines()
    if MANUAL in txt:
        return [l for l in txt[txt.index(MANUAL) + 1:] if l.strip()]
    return []


def render():
    man = manual_lines()
    auto = sorted(extract() - set(man))
    head = ["# Routes HTTP servies par CastBridge-TV (générées par tools/routes/list_routes.py ; ne pas éditer hors section manuelle)"]
    return "\n".join(head + auto + [MANUAL] + man) + "\n"


def routes_of(path=OUT):
    return [l.strip() for l in path.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]


if __name__ == "__main__":
    out = render()
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != out:
            print("routes.txt n'est pas à jour : relancer tools/routes/list_routes.py", file=sys.stderr)
            sys.exit(1)
        print("ok")
    else:
        OUT.write_text(out, encoding="utf-8")
        print(f"{len(routes_of())} routes")
