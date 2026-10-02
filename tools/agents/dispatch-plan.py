#!/usr/bin/env python3
"""dispatch-plan.py : quels cahiers peuvent partir maintenant ?

Lit docs/agent-briefs/routing.json (table de routage Fable, 2026-10-02) et imprime, par vague puis par groupe,
les cahiers lançables étant donné ceux déjà terminés (fusionnés). Aucun réseau, aucune écriture.

Exemples :
  python3 tools/agents/dispatch-plan.py                          # tout ce qui est lançable sans prérequis
  python3 tools/agents/dispatch-plan.py --done w1-05,w1-04       # après fusion de w1-05 et w1-04
  python3 tools/agents/dispatch-plan.py --done-file done.txt     # un id par ligne (ou séparés par des espaces)
  python3 tools/agents/dispatch-plan.py --wave W4a --done-file done.txt
  python3 tools/agents/dispatch-plan.py --blocked                # inclure les cahiers BLOQUÉ (décision du propriétaire)
  python3 tools/agents/dispatch-plan.py --json                   # sortie machine
  python3 tools/agents/dispatch-plan.py --cost                   # totaux de jauge par vague
Code de sortie 0 ; 2 si routing.json est introuvable.
"""
import argparse, json, pathlib, sys
from collections import OrderedDict

MAX_PARALLEL = 3  # 8 Go de RAM : au plus 3 agents, un seul build JVM (tools/agents/gradle-lock.sh)

def load(path):
    try:
        return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"introuvable : {path}", file=sys.stderr); sys.exit(2)

def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0], formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__)
    ap.add_argument("--routing", default=str(pathlib.Path(__file__).resolve().parents[2] / "docs/agent-briefs/routing.json"))
    ap.add_argument("--done", default="", help="ids terminés, séparés par des virgules")
    ap.add_argument("--done-file", help="fichier d'ids terminés")
    ap.add_argument("--wave", help="ne montrer qu'une vague (W1, W2, W3, W4a, W4b, W4c, W5a…W5e, W6a…W6e, PROTECT)")
    ap.add_argument("--blocked", action="store_true", help="inclure les cahiers BLOQUÉ")
    ap.add_argument("--all", action="store_true", help="montrer aussi les cahiers en attente de prérequis")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--cost", action="store_true", help="totaux de jauge par vague")
    a = ap.parse_args()

    doc = load(a.routing)
    briefs = doc["briefs"]
    done = {x.strip() for x in a.done.split(",") if x.strip()}
    if a.done_file:
        done |= set(pathlib.Path(a.done_file).read_text(encoding="utf-8").replace(",", " ").split())
    known = {b["id"] for b in briefs}
    for d in done - known:
        print(f"avertissement : id inconnu dans --done : {d}", file=sys.stderr)
    # un cahier non vivant (FAIT, FUSIONNÉ, REMPLACÉ) compte comme terminé pour les dépendances
    done |= {b["id"] for b in briefs if not b["live"] and not b["status"].startswith("LANCÉ")}

    if a.cost:
        waves = OrderedDict()
        for b in briefs:
            if b["live"]:
                w = waves.setdefault(b["wave"], dict(n=0, h=0, tin=0, tout=0, cost=0.0, naive=0.0))
                w["n"] += 1; w["h"] += b["model"] == "haiku"; w["tin"] += b["tokens_in"]; w["tout"] += b["tokens_out"]
                w["cost"] += b["cost_usd"]; w["naive"] += b["naive_cost_usd"]
        print(f"{'vague':8} {'n':>3} {'haiku':>5} {'entrée':>9} {'sortie':>8} {'coût $':>7} {'naïf $':>7} {'écon.':>6}")
        T = dict(n=0, h=0, tin=0, tout=0, cost=0.0, naive=0.0)
        for k, w in waves.items():
            for f in T: T[f] += w[f]
            print(f"{k:8} {w['n']:3} {w['h']:5} {w['tin']/1e6:8.2f}M {w['tout']/1e3:7.0f}k {w['cost']:7.2f} {w['naive']:7.2f} {(1-w['cost']/w['naive'])*100:5.0f}%")
        print(f"{'TOTAL':8} {T['n']:3} {T['h']:5} {T['tin']/1e6:8.2f}M {T['tout']/1e3:7.0f}k {T['cost']:7.2f} {T['naive']:7.2f} {(1-T['cost']/T['naive'])*100:5.0f}%")
        return

    runnable, waiting = [], []
    for b in briefs:
        if not b["live"] or b["id"] in done: continue
        if a.wave and b["wave"].lower() != a.wave.lower(): continue
        blocked = b["status"].startswith("BLOQUÉ") and not b["status"].startswith("BLOQUÉ partiel")
        if blocked and not a.blocked: continue
        missing = [d for d in b["deps"] if d not in done]
        (runnable if not missing else waiting).append((b, missing))

    if a.json:
        print(json.dumps({"runnable": [dict(id=b["id"], model=b["model"], group=b["group"], wave=b["wave"], gate=b["gate"], audit=b["audit"]) for b, _ in runnable],
                          "waiting": [dict(id=b["id"], missing=m) for b, m in waiting]}, ensure_ascii=False, indent=1))
        return

    print(f"Terminés pris en compte : {len(done)} ; lançables : {len(runnable)} ; en attente : {len(waiting)}")
    print(f"Limite : {MAX_PARALLEL} agents en parallèle, un seul build JVM à la fois (gradle-lock.sh).\n")
    groups = OrderedDict()
    for b, _ in runnable:
        groups.setdefault((b["wave"], b["group"]), []).append(b)
    for (w, g), bs in groups.items():
        print(f"[{w} · groupe {g}]")
        for b in bs:
            flag = " (BLOQUÉ partiel : faire ce qui ne dépend pas de la décision)" if b["status"].startswith("BLOQUÉ") else ""
            print(f"  {b['id']:12} modèle={b['model']:6} effort={b['effort']} audit={'oui' if b['audit'] else 'non'} jauge={b['tokens_in']//1000}k/{b['tokens_out']//1000}k{flag}")
            print(f"               porte : {b['gate']}")
    if a.all and waiting:
        print("\nEn attente de prérequis :")
        for b, m in waiting:
            print(f"  {b['id']:12} attend {', '.join(m)}")

if __name__ == "__main__":
    main()
