#!/usr/bin/env python3
"""Chaîne de production multimédia (demandes, coûts, registres, réception). Aucun appel réseau, aucune clé. Voir docs/MEDIA-PIPELINE.md."""
import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from mp import estimate, registries, receive, requests_gen  # noqa: E402
from mp.common import read_json, read_jsonl, scan_secrets, write_json  # noqa: E402


def cmd_requests(a):
    policy = read_json(a.policy) if a.policy else None
    rows, conflicts = requests_gen.run(a.packs, a.out, policy)
    blocked = sum(1 for r in rows if r.get("blocked"))
    print("%d demandes (%d bloquées) -> %s" % (len(rows), blocked, a.out))
    for c in conflicts:
        print("CONFLIT %s : %s" % (c["id"], c["message"]), file=sys.stderr)
    return 1 if conflicts else 0


def cmd_estimate(a):
    rows = read_jsonl(a.requests)
    s = estimate.summarize(rows, estimate.load_pricing(a.pricing))
    print(json.dumps(s, ensure_ascii=False, indent=1))
    return 0


def _caps(a):
    caps = {}
    for k, v in (("maxCost", a.max_cost), ("maxChars", a.max_chars), ("maxImages", a.max_images), ("maxVideoSeconds", a.max_video_seconds), ("maxRequests", a.max_requests)):
        if v is not None:
            caps[k] = v
    return caps


def cmd_plan(a):
    rows = read_jsonl(a.requests)
    pricing = estimate.load_pricing(a.pricing)
    state = estimate.State(a.state) if a.state else None
    try:
        kept, later, stop = estimate.plan(rows, pricing, _caps(a), state)
    except estimate.PricingError as e:
        print("REFUSÉ : %s" % e, file=sys.stderr)
        return 2
    write_json(a.out, {"format": 1, "plafonds": _caps(a), "arret": stop, "retenues": [r["id"] for r in kept], "reportees": [{"id": r["id"], "motif": m} for r, m in later],
                       "resume": estimate.summarize(kept, pricing)})
    print("%d demandes retenues, %d reportées%s" % (len(kept), len(later), (" ; ARRÊT : plafond %s atteint" % stop) if stop else ""))
    return 3 if stop else 0


def cmd_record(a):
    rows = {r["id"]: r for r in read_jsonl(a.requests)}
    state = estimate.State(a.state)
    pricing = estimate.load_pricing(a.pricing)
    n = 0
    for rid in a.ids:
        n += 1 if state.record(rows[rid], pricing, a.cost) else 0
    print("%d enregistrée(s), dépense cumulée : %s" % (n, state.data["spent"]))
    return 0


def cmd_registries(a):
    engines, voices = registries.load(a.engines, a.voices)
    problems = registries.validate_registries(engines, voices)
    for p in problems:
        print("PROBLÈME :", p)
    if a.requests:
        rows = read_jsonl(a.requests)
        ready = registries.readiness(rows, engines, voices)
        todo = sorted({m for m in ready.values() if m})
        print("%d demandes audio prêtes, %d en attente d'un choix du propriétaire" % (sum(1 for v in ready.values() if v is None), sum(1 for v in ready.values() if v)))
        print("Classes de voix à faire choisir :", *registries.classes_needed(rows), sep="\n  ")
    return 1 if problems else 0


def cmd_receive(a):
    engines, voices = registries.load(a.engines, a.voices)
    res, acc, rej, wait = receive.run(a.requests, a.workdir, a.produced, engines, voices, a.asr, a.family, a.out)
    print(json.dumps(res, ensure_ascii=False))
    return 1 if (res["bloquants"] or res["rejetes"]) else 0


def cmd_secrets(a):
    found = scan_secrets(a.root)
    for rel, kind in found:
        print("SECRET POTENTIEL : %s (%s) — à retirer" % (rel, kind))
    return 1 if found else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("requests", help="génère media-requests.jsonl depuis les paquets de langue"); s.add_argument("packs"); s.add_argument("out"); s.add_argument("--policy"); s.set_defaults(fn=cmd_requests)
    s = sub.add_parser("estimate", help="caractères, images, secondes, coût (tarifs de pricing.json)"); s.add_argument("requests"); s.add_argument("--pricing"); s.set_defaults(fn=cmd_estimate)
    s = sub.add_parser("plan", help="tranche de production sous plafonds"); s.add_argument("requests"); s.add_argument("out"); s.add_argument("--pricing"); s.add_argument("--state")
    for f, t in (("max-cost", float), ("max-chars", int), ("max-images", int), ("max-video-seconds", int), ("max-requests", int)):
        s.add_argument("--" + f, type=t)
    s.set_defaults(fn=cmd_plan)
    s = sub.add_parser("record", help="enregistre des demandes produites (reprise)"); s.add_argument("requests"); s.add_argument("state"); s.add_argument("ids", nargs="+"); s.add_argument("--pricing"); s.add_argument("--cost", type=float); s.set_defaults(fn=cmd_record)
    s = sub.add_parser("registries", help="valide les registres et dit quelles voix manquent"); s.add_argument("engines"); s.add_argument("voices"); s.add_argument("--requests"); s.set_defaults(fn=cmd_registries)
    s = sub.add_parser("receive", help="contrôle un lot produit"); s.add_argument("requests"); s.add_argument("workdir"); s.add_argument("produced"); s.add_argument("engines"); s.add_argument("voices")
    s.add_argument("--asr"); s.add_argument("--family", default="reserve", choices=["libre", "reserve"]); s.add_argument("--out", required=True); s.set_defaults(fn=cmd_receive)
    s = sub.add_parser("secrets", help="cherche des secrets (chemin et type seulement)"); s.add_argument("root"); s.set_defaults(fn=cmd_secrets)
    a = ap.parse_args(argv)
    return a.fn(a)


if __name__ == "__main__":
    sys.exit(main())
