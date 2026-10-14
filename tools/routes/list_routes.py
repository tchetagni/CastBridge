#!/usr/bin/env python3
"""Liste les routes HTTP servies par CastBridge-TV (extraites du code) dans tools/routes/routes.txt.

Usage : list_routes.py [--check]   (--check : échoue si routes.txt committé diffère de l'extraction)
Les lignes de `routes.txt` après un commentaire `# manuel` sont conservées telles quelles (routes que l'extraction rate).

Salles de jeu : la plateforme de jeux monte ses routes avec `RoomHttp("/chess", …)` ; les sous-routes (`"$prefix/api/hello"`…) sont construites par RoomHttp.kt, donc
invisibles pour une recherche de littéraux. L'extraction reconnaît chaque montage à préfixe littéral et lui ajoute les sous-routes lues dans RoomHttp.kt lui-même (ajouter
une route à RoomHttp l'ajoute ici sans rien toucher) : sans cela `/chess` et `/chess/api/*`, toujours servies par la TV, disparaîtraient de la liste.
Ne sont PAS extraits : les montages à préfixe calculé (`RoomHttp("/jeux/${e.id}", …)`, GamesHttp.kt) et la liste `/jeux`. À lister à la main dans la section `# manuel`, et à
classer dans TrialRoutesTest (qui exige que toute route de ce fichier soit classée ouverte ou fermée en essai), le jour où les jeux à règles sont ouverts au public.
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
ROOM_HTTP = ROOT / "android/core/src/main/kotlin/castbridge/core/games/RoomHttp.kt"
ROOM_MOUNT = re.compile(r'\bRoomHttp\(\s*"(/[A-Za-z0-9_\-]+)"')            # montage d'une salle sur un préfixe littéral
ROOM_SUBROUTE = re.compile(r'"\$prefix(/api/[A-Za-z0-9_\-]+)"')            # sous-route construite par RoomHttp.serve


def room_routes(prefix):
    """Les routes d'une salle montée sur [prefix] : la page et chaque sous-route de RoomHttp.kt."""
    subs = sorted(set(ROOM_SUBROUTE.findall(ROOM_HTTP.read_text(encoding="utf-8")))) if ROOM_HTTP.exists() else []
    if not subs:
        sys.exit("list_routes: RoomHttp.kt ne déclare plus de sous-route \"$prefix/api/…\" : adapter ROOM_SUBROUTE (les salles de jeu disparaîtraient de routes.txt)")
    return {prefix} | {prefix + s for s in subs}


def extract():
    routes = set()
    for base in SRC:
        for f in sorted(base.rglob("*.kt")):
            if "/test/" in str(f):
                continue
            for line in f.read_text(encoding="utf-8").splitlines():
                for m in ROOM_MOUNT.finditer(line):
                    routes |= room_routes(m.group(1))
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
