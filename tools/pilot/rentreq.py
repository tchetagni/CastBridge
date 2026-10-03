#!/usr/bin/env python3
"""Lit une demande de location CastBridge-TV (fichier ou code court), la vérifie et IMPRIME la commande W16.

Ne lance rien, ne signe rien, n'écrit aucun registre, ne touche ni clé ni TV : le propriétaire relit la
commande puis la lance lui-même. Une demande est un mémo, jamais un droit ; le code court n'est jamais
une preuve (DESIGN-W17 § 4.1). Bibliothèque standard seulement.
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import shlex
import sys

HEADER = "castbridge-rent-request-v1"
FIELDS = ["at", "bundle", "choice", "kind", "nonce", "origin", "period", "tv"]
ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"  # Crockford, comme verify_vectors.py
DEFAULT_PILOT = "tools/pilot/pilot.json"
# Bouquets gratuits du pilote (décision du propriétaire : Langues est gratuit, jamais loué).
FREE_PREFIX = "langues"
# Alias de bouquet connus (code court) ; un alias inconnu exige --bouquet.
ALIASES = {"CM2": "classe-cm2"}
REMINDER = "Une demande ne vaut pas droit : vérifiez le foyer et les règles du pilote avant de lancer la commande."


class Refus(Exception):
    """Refus de la demande, avec la phrase française à montrer."""


def canonique(f):
    """Texte canonique : en-tête puis les huit champs triés par clé, sans retour final."""
    return HEADER + "\n" + "\n".join("%s=%s" % (k, f[k]) for k in FIELDS)


def analyser(text):
    """Analyse stricte (miroir de RentRequest.parse) : dict des champs, ou None si la demande est mal formée."""
    if len(text) > 1024:
        return None
    lines = text.split("\n")
    if lines[0] != HEADER:
        return None
    kv = {}
    for line in lines[1:]:
        k, eq, v = line.partition("=")
        if not eq or not k or k in kv:
            return None
        kv[k] = v
    if set(kv) != set(FIELDS):
        return None
    ok = (re.fullmatch(r"[0-9a-f]{16}", kv["tv"]) and re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", kv["bundle"])
          and re.fullmatch(r"defaut|[1-9][0-9]{0,2}[jh]", kv["choice"]) and re.fullmatch(r"[0-9a-f]{8}", kv["nonce"])
          and kv["kind"] in ("new", "extend") and kv["origin"] in ("tv", "phone")
          and re.fullmatch(r"[0-9]{1,15}", kv["at"]) and re.fullmatch(r"[0-9]{1,15}", kv["period"]))
    if not ok or (kv["kind"] == "new") != (int(kv["period"]) == 0):
        return None
    f = {**kv, "at": int(kv["at"]), "period": int(kv["period"])}
    return f if canonique(f) == text else None


def code_court(f, alias):
    """<ALIAS>-<CHOIX>-<4 Crockford> : 20 premiers bits de SHA-256 de castbridge-rent-request-v1|tv|bundle|choice|nonce."""
    d = hashlib.sha256(("%s|%s|%s|%s|%s" % (HEADER, f["tv"], f["bundle"], f["choice"], f["nonce"])).encode()).digest()
    bits = (d[0] << 12) | (d[1] << 4) | (d[2] >> 4)
    tail = "".join(ALPHABET[(bits >> s) & 31] for s in (15, 10, 5, 0))
    return "%s-%s-%s" % (alias, "DEF" if f["choice"] == "defaut" else f["choice"].upper(), tail)


def libelle(choice):
    """Libellés W16 exacts, jamais de conversion jours / heures."""
    if choice == "defaut":
        return "Sans durée précise : 30 jours"
    n = int(choice[:-1])
    if choice.endswith("h"):
        return "1 heure d'utilisation" if n == 1 else "%d heures d'utilisation" % n
    return "1 jour" if n == 1 else "%d jours" % n


def bouquets_gratuits(pilote):
    """Bouquets gratuits : tout « langues… » plus la liste facultative freeBundles de pilot.json (illisible = refus)."""
    extra = set()
    if os.path.isfile(pilote):
        try:
            with open(pilote, encoding="utf-8") as fh:
                doc = json.load(fh)
            extra = set(doc.get("freeBundles", [])) if isinstance(doc, dict) else set()
        except (OSError, ValueError, TypeError):
            raise Refus("le fichier du pilote est illisible (%s) : impossible de savoir quels bouquets sont gratuits" % pilote)
    return extra


def verifier_gratuit(bundle, pilote):
    if bundle == FREE_PREFIX or bundle.startswith(FREE_PREFIX + "-") or bundle in bouquets_gratuits(pilote):
        raise Refus("le bouquet %s est gratuit : il ne se loue pas, aucune commande à lancer" % bundle)


def commande(f, pilote):
    cmd = "louer.py location --tv %s --bouquet %s --choix %s --pilote %s" % (f["tv"], f["bundle"], f["choice"], shlex.quote(pilote))
    if f["kind"] == "extend":
        cmd += " --prolonger --periode %d" % f["period"]
    return cmd


def date_fr(ms):
    try:
        return datetime.datetime.fromtimestamp(ms / 1000, datetime.timezone.utc).strftime("%d/%m")
    except (OverflowError, OSError, ValueError):
        return "date illisible"


def sortie(f, pilote, avertissements, alias=None, as_json=False):
    alias = alias or next((a for a, b in ALIASES.items() if b == f["bundle"]), f["bundle"])
    origine = "la TV" if f["origin"] == "tv" else "le téléphone"
    resume = "Demande : %s · %s · née sur %s" % (alias, libelle(f["choice"]), origine)
    if f["at"] is not None:
        resume += " le " + date_fr(f["at"])
    if f["kind"] == "extend":
        resume += " (prolongation)"
    cmd = commande(f, pilote)
    avertissements = list(avertissements) + [REMINDER]
    if as_json:
        print(json.dumps({"resume": resume, "commande": cmd, "tv": f["tv"], "bundle": f["bundle"], "choice": f["choice"],
                          "kind": f["kind"], "period": f["period"], "origin": f["origin"], "at": f["at"],
                          "avertissements": avertissements}, ensure_ascii=False, indent=2))
        return
    print(resume)
    for a in avertissements:
        print("Attention : " + a)
    print("Commande à relire puis lancer vous-même :")
    print(cmd)


def lire(args):
    try:
        with open(args.fichier, "rb") as fh:
            raw = fh.read()
        text = raw.decode("utf-8")
    except (OSError, UnicodeDecodeError):
        raise Refus("fichier illisible : %s" % args.fichier)
    text = text.replace("\r\n", "\n")
    if text.endswith("\n"):
        text = text[:-1]
    f = analyser(text)
    if f is None:
        raise Refus("ce n'est pas une demande canonique castbridge-rent-request-v1 (champ manquant, en trop, hors format ou modifié)")
    verifier_gratuit(f["bundle"], args.pilote)
    return f, [], None


def par_code(args):
    m = re.fullmatch(r"([A-Z0-9]{1,6})-(DEF|[1-9][0-9]{0,2}[JH])-([0-9A-HJKMNP-TV-Z]{4})", args.code)
    if not m:
        raise Refus("code court mal formé : attendu du type CM2-12H-0PF8")
    alias, choix, tail = m.groups()
    if not re.fullmatch(r"[0-9a-f]{16}", args.tv):
        raise Refus("identifiant de TV invalide : 16 caractères hexadécimaux attendus après --tv")
    bundle = args.bouquet or ALIASES.get(alias)
    if not bundle or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", bundle):
        raise Refus("alias %s inconnu : indiquez le bouquet avec --bouquet" % alias)
    if args.nonce is not None and not re.fullmatch(r"[0-9a-f]{8}", args.nonce):
        raise Refus("nonce invalide : 8 caractères hexadécimaux attendus")
    if args.prolonger != (args.periode is not None):
        raise Refus("--prolonger et --periode vont ensemble")
    verifier_gratuit(bundle, args.pilote)
    choice = "defaut" if choix == "DEF" else choix.lower()
    f = {"tv": args.tv, "bundle": bundle, "choice": choice, "nonce": args.nonce or "00000000", "kind": "extend" if args.prolonger else "new",
         "period": args.periode or 0, "origin": "tv", "at": None}
    if args.nonce is None:
        avert = ["code court : non vérifiable (nonce inconnu) : confirmez avec le foyer"]
    elif code_court(f, alias) != "%s-%s-%s" % (alias, choix, tail):
        raise Refus("les 4 derniers caractères ne correspondent pas à la TV, au bouquet, au choix et au nonce donnés")
    else:
        avert = ["code court vérifié avec le nonce donné (cela prouve la cohérence, pas l'identité du demandeur)"]
    if ALIASES.get(alias) not in (None, bundle):
        avert.append("l'alias %s désigne d'ordinaire %s, pas %s" % (alias, ALIASES[alias], bundle))
    return f, avert, alias


def parser():
    commun = argparse.ArgumentParser(add_help=False)
    commun.add_argument("--pilote", default=DEFAULT_PILOT, help="chemin de pilot.json (défaut : %s)" % DEFAULT_PILOT)
    commun.add_argument("--json", action="store_true", help="sortie JSON au lieu du texte")
    p = argparse.ArgumentParser(
        prog="rentreq.py",
        description="Lit une demande de location faite sur la TV (fichier ou code court), la vérifie et imprime la commande "
                    "louer.py à lancer. Ne lance rien et ne signe rien. Une demande ne vaut pas droit : vérifiez le foyer "
                    "et les règles du pilote ; louer.py applique PilotRules.")
    sub = p.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("lire", parents=[commun], help="lire un fichier de demande castbridge-rent-request-v1")
    a.add_argument("fichier", help="fichier texte de la demande")
    b = sub.add_parser("code", parents=[commun], help="lire un code court (ex. CM2-12H-0PF8)")
    b.add_argument("code", help="code court de la demande")
    b.add_argument("--tv", required=True, help="identifiant de la TV (16 caractères hexadécimaux)")
    b.add_argument("--bouquet", help="bouquet (obligatoire si l'alias est inconnu)")
    b.add_argument("--nonce", help="nonce de la demande (8 hexadécimaux) pour vérifier le code court")
    b.add_argument("--prolonger", action="store_true", help="la demande est une prolongation (le code court ne le dit pas)")
    b.add_argument("--periode", type=int, help="period du contrat à prolonger (ms), avec --prolonger")
    return p


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        f, avert, alias = lire(args) if args.cmd == "lire" else par_code(args)
    except Refus as e:
        print("Demande refusée : %s." % e, file=sys.stderr)
        return 2
    sortie(f, args.pilote, avert, alias, args.json)
    return 0


if __name__ == "__main__":
    sys.exit(main())
