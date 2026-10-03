#!/usr/bin/env python3
"""Prépare le dossier CastBridge/store/ à copier sur une clé USB pour CastBridge-TV (catalogue de la Boutique hors ligne).

Copie seulement : les deux catalogues SIGNÉS (déjà signés ailleurs) et des lots (avec leur preuve signée). Ne signe rien, ne
contient et ne lit aucune clé privée, n'ouvre aucun réseau ; refuse un document non signé, trop gros, ou qui ressemble à une clé.
CastBridge-TV revérifie tout à l'import (signature, date, tailles, SHA-256) : cet outil n'est qu'un garde-fou de préparation.
Bibliothèque standard seulement.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import sys

MAX_LOTS = 256 * 1024
MAX_BUNDLES = 64 * 1024
MAX_PROOF = 256 * 1024
LOT_RE = re.compile(r"^castbridge-lot-([a-z0-9]{1,32})-([a-z0-9][a-z0-9-]{0,31})-v(\d{1,9})\.lot$")
AT_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
SECRET_MARKERS = ("PRIVATE KEY", "BEGIN OPENSSH", "BEGIN PGP")
SUBDIR = os.path.join("CastBridge", "store")


class Refus(Exception):
    """Refus de préparation, avec la phrase française à montrer."""


def lire(path, plafond, nom):
    if os.path.islink(path) or not os.path.isfile(path):
        raise Refus("%s : fichier ordinaire attendu (ni dossier ni lien) : %s" % (nom, path))
    if os.path.getsize(path) > plafond:
        raise Refus("%s : trop volumineux (plus de %d Ko) : refusé" % (nom, plafond // 1024))
    with open(path, "rb") as f:
        data = f.read()
    if b"\x00" in data:
        raise Refus("%s : octets de remplissage ou de contrôle (zéros) : refusé" % nom)
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        raise Refus("%s : UTF-8 attendu : refusé" % nom)
    if any(m in text for m in SECRET_MARKERS):
        raise Refus("%s : ressemble à une clé privée : refusé, rien n'est copié" % nom)
    return data, text


def verifier_document(text, nom, cle_liste):
    """Garde-fou léger (la vraie vérification de signature est faite par CastBridge-TV) : objet JSON, daté, signé."""
    try:
        doc = json.loads(text)
    except ValueError:
        raise Refus("%s : JSON illisible : refusé" % nom)
    if not isinstance(doc, dict):
        raise Refus("%s : un objet JSON est attendu : refusé" % nom)
    if not AT_RE.match(str(doc.get("generatedAt", ""))):
        raise Refus("%s : date de génération absente ou invalide : refusé" % nom)
    sig = doc.get("signature")
    if not isinstance(sig, str) or not sig.strip() or sig == "UNSIGNED":
        raise Refus("%s : non signé (UNSIGNED) : signez-le avant de le mettre sur la clé" % nom)
    if not isinstance(doc.get(cle_liste), list):
        raise Refus("%s : liste « %s » absente : refusé" % (nom, cle_liste))
    return doc


def verifier_preuve(text, nom_lot, donnees):
    m = LOT_RE.match(nom_lot)
    doc = verifier_document(text, nom_lot + ".json", "lots")
    for e in doc["lots"]:
        if isinstance(e, dict) and e.get("feature") == m.group(1) and e.get("scope") == m.group(2) and e.get("version") == int(m.group(3)):
            if e.get("bytes") != len(donnees):
                raise Refus("%s : taille %d octets au lieu de %s annoncés par la preuve : refusé" % (nom_lot, len(donnees), e.get("bytes")))
            if str(e.get("sha256", "")).lower() != hashlib.sha256(donnees).hexdigest():
                raise Refus("%s : empreinte SHA-256 différente de la preuve : refusé" % nom_lot)
            return
    raise Refus("%s : la preuve ne contient pas ce lot : refusé" % nom_lot)


def preparer(sortie, lots_catalog=None, bundles_catalog=None, lots=(), remplacer=False):
    """Écrit <sortie>/CastBridge/store/ ; retourne la liste des fichiers écrits. Tout est vérifié AVANT la première écriture."""
    if not (lots_catalog or bundles_catalog or lots):
        raise Refus("Rien à copier : donnez au moins un catalogue ou un lot.")
    plan = []
    if lots_catalog:
        data, text = lire(lots_catalog, MAX_LOTS, "Catalogue des lots")
        verifier_document(text, "Catalogue des lots", "lots")
        catalogue_texte = text
        plan.append(("lots-catalog.json", data))
    if bundles_catalog:
        data, text = lire(bundles_catalog, MAX_BUNDLES, "Catalogue des bouquets")
        verifier_document(text, "Catalogue des bouquets", "bundles")
        plan.append(("bundles-catalog.json", data))
    vus = set()
    for chemin in lots:
        nom = os.path.basename(chemin)
        if not LOT_RE.match(nom):
            raise Refus("Nom de lot inattendu (castbridge-lot-<fonction>-<portée>-v<N>.lot) : %s" % nom)
        if nom in vus:
            raise Refus("Lot donné deux fois : %s" % nom)
        vus.add(nom)
        if os.path.islink(chemin) or not os.path.isfile(chemin):
            raise Refus("%s : fichier ordinaire attendu : refusé" % nom)
        with open(chemin, "rb") as f:
            donnees = f.read()
        preuve = chemin + ".json"
        if os.path.isfile(preuve):
            _, ptext = lire(preuve, MAX_PROOF, nom + ".json")
            verifier_preuve(ptext, nom, donnees)
            plan.append((nom + ".json", ptext.encode("utf-8")))
        elif not lots_catalog:
            raise Refus("%s : pas de preuve signée (%s.json) et pas de catalogue des lots : refusé" % (nom, nom))
        else:
            verifier_preuve(catalogue_texte, nom, donnees)
        plan.append((nom, donnees))
    dossier = os.path.join(sortie, SUBDIR)
    if os.path.isdir(dossier) and os.listdir(dossier) and not remplacer:
        raise Refus("%s existe déjà et n'est pas vide : utilisez --remplacer pour le vider d'abord." % dossier)
    if remplacer and os.path.isdir(dossier):
        shutil.rmtree(dossier)
    os.makedirs(dossier, exist_ok=True)
    ecrits = []
    for nom, data in plan:
        cible = os.path.join(dossier, nom)
        with open(cible, "wb") as f:
            f.write(data)
        ecrits.append(cible)
    return ecrits


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--sortie", required=True, help="racine de la clé (ou d'un dossier de préparation) ; CastBridge/store/ y est créé")
    p.add_argument("--catalogue-lots", help="lots-catalog.json signé")
    p.add_argument("--catalogue-bouquets", help="bundles-catalog.json signé")
    p.add_argument("--lot", action="append", default=[], help="fichier .lot (sa preuve <lot>.json est cherchée à côté) ; répétable")
    p.add_argument("--remplacer", action="store_true", help="vide d'abord CastBridge/store/ s'il existe")
    a = p.parse_args(argv)
    try:
        ecrits = preparer(a.sortie, a.catalogue_lots, a.catalogue_bouquets, a.lot, a.remplacer)
    except Refus as e:
        print("REFUSÉ : %s" % e, file=sys.stderr)
        return 1
    for c in ecrits:
        print("copié :", c)
    print("Prêt : copiez le dossier CastBridge sur la clé, branchez-la à CastBridge-TV.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
