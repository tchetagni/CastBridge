#!/usr/bin/env python3
"""Archive des contenus libres de CastBridge (CC BY-SA), reproductible, bibliothèque standard seule.

Règle d'or : seul un contenu EXPLICITEMENT étiqueté « CC BY-SA » (avec sa version) entre dans l'archive. Rien n'est déduit, rien n'est inventé.
Un contenu enregistré « réservé » n'y entre JAMAIS : l'outil refuse (code de sortie 2). Voir docs/FREE-CONTENT.md.

  python3 tools/free-content/build_free_archive.py --dry-run                       # inventaire seulement
  python3 tools/free-content/build_free_archive.py --out dist/                     # construit castbridge-contenus-libres-<aaaammjj>-v<N>.zip
  python3 tools/free-content/build_free_archive.py --check dist/castbridge-contenus-libres-….zip

Codes de sortie : 0 ok ; 2 refus (contenu réservé, étiquette ambiguë, média inconnu, archive vide…) ; 3 usage ou fichier illisible ; 4 --check : archive altérée.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

BUILDER_VERSION = "1.0.0"
HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
ZIP_TIME = (1980, 1, 1, 0, 0, 0)
IGNORED = {".DS_Store", "Thumbs.db"}

LICENSE_KEYS = ("license", "licence", "licenses", "licences")
SA_RE = re.compile(r"\bCC[\s_-]*BY[\s_-]*SA(?:[\s_-]*v?(\d\.\d))?\b", re.I)
OTHER_CC_RE = re.compile(r"\bCC[\s_-]*(?:BY|0|ZERO)\b", re.I)
MIX_RE = re.compile(r"[/+&;,|]|\b(?:et|ou|and|or|avec|with)\b", re.I)

# Licences de médias acceptées dans l'archive (docs/LANGUES.md § 5/7, LangLicences). CASTBRIDGE-ORIGINAL = œuvre de CastBridge, publiée avec le contenu en CC BY-SA 4.0 (décision du 2026-10-01).
MEDIA_OK = {"CASTBRIDGE-ORIGINAL", "CC0", "PD", "PUBLIC-DOMAIN", "CC-BY-2.0", "CC-BY-3.0", "CC-BY-4.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0"}
MEDIA_NEEDS_ATTRIBUTION = {"CC-BY-2.0", "CC-BY-3.0", "CC-BY-4.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0"}
LICENSE_URLS = {"4.0": ("https://creativecommons.org/licenses/by-sa/4.0/", "https://creativecommons.org/licenses/by-sa/4.0/legalcode"),
                "3.0": ("https://creativecommons.org/licenses/by-sa/3.0/", "https://creativecommons.org/licenses/by-sa/3.0/legalcode")}
VERBATIM_MARK = {"4.0": "Attribution-ShareAlike 4.0 International", "3.0": "Attribution-ShareAlike 3.0"}


class Refusal(Exception):
    pass


# ------------------------------------------------------------------ étiquettes de licence

def parse_tag(raw):
    """raw : str | list | None. -> dict(kind: none|by-sa|other|ambiguous, version, text)."""
    if raw is None or raw == "" or raw == []:
        return {"kind": "none", "version": None, "text": ""}
    items = raw if isinstance(raw, list) else [raw]
    items = [str(x).strip() for x in items if str(x).strip()]
    if not items:
        return {"kind": "none", "version": None, "text": ""}
    text = " | ".join(items)
    norm = {re.sub(r"[\s_-]+", " ", x.upper()) for x in items}
    has_sa = [SA_RE.search(x) for x in items]
    if len(norm) > 1 or any(MIX_RE.search(x) for x in items):
        return {"kind": "ambiguous", "version": None, "text": text, "hasSA": any(has_sa)}
    m = has_sa[0]
    if m:
        # « CC BY-SA 4.0 International » accepté ; tout autre reste ambigu
        rest = SA_RE.sub("", items[0]).strip(" ()-_,.")
        if rest and not re.fullmatch(r"(?i)(international|unported|licen[cs]e)?", rest):
            return {"kind": "ambiguous", "version": m.group(1), "text": text, "hasSA": True}
        return {"kind": "by-sa", "version": m.group(1), "text": text}
    return {"kind": "other", "version": None, "text": text}


def matches_filter(tag, flt):
    """flt : « CC BY-SA » ou « CC BY-SA 4.0 »."""
    fm = SA_RE.search(flt)
    if not fm:
        return tag["text"].strip().lower() == flt.strip().lower()
    if tag["kind"] == "ambiguous":
        return bool(tag.get("hasSA"))
    if tag["kind"] != "by-sa":
        return False
    return fm.group(1) is None or fm.group(1) == tag["version"]


def collect_tags(directory, tags_file_value):
    """Étiquettes trouvées : au niveau du pack (fichier principal) + fichiers JSON du dossier (clé de premier niveau seulement)."""
    found = []  # (source, valeur)
    for dp, dn, fn in os.walk(directory):
        dn.sort()
        for f in sorted(fn):
            if not f.endswith(".json"):
                continue
            p = os.path.join(dp, f)
            try:
                d = read_json(p)
            except (OSError, ValueError):
                continue
            if isinstance(d, dict):
                for k in LICENSE_KEYS:
                    if k in d:
                        found.append((os.path.relpath(p, directory).replace(os.sep, "/") + ":" + k, d[k]))
    if tags_file_value is not None:
        found.append(("license-tags.json", tags_file_value))
    return found


def merge_tags(found):
    if not found:
        return parse_tag(None), "aucune"
    parsed = [(s, parse_tag(v)) for s, v in found]
    texts = {re.sub(r"[\s_-]+", " ", t["text"].upper()) for _, t in parsed}
    src = ",".join(s for s, _ in parsed)
    if len(texts) > 1:
        return {"kind": "ambiguous", "version": None, "text": " | ".join(t["text"] for _, t in parsed), "hasSA": any(SA_RE.search(t["text"]) for _, t in parsed)}, src
    return parsed[0][1], src


# ------------------------------------------------------------------ inventaire

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_bytes(path):
    with open(path, "rb") as f:
        return f.read()


def read_text(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_registry(repo):
    free, reserved = set(), set()
    cdir = os.path.join(repo, "content")
    if os.path.isdir(cdir):
        for name in sorted(os.listdir(cdir)):
            p = os.path.join(cdir, name, "lots.json")
            if os.path.isfile(p):
                try:
                    d = read_json(p)
                except (OSError, ValueError):
                    continue
                free |= set(d.get("free") or [])
                reserved |= set(d.get("reserved") or [])
    return free, reserved


def tracked_files(repo):
    try:
        r = subprocess.run(["git", "-C", repo, "ls-files", "--", "content"], capture_output=True, text=True, timeout=60)
        if r.returncode != 0:
            return None
        return set(r.stdout.split("\n"))
    except (OSError, subprocess.SubprocessError):
        return None


def list_files(directory):
    out = []
    for dp, dn, fn in os.walk(directory):
        dn.sort()
        for f in sorted(fn):
            if f in IGNORED or f.endswith(".pyc"):
                continue
            out.append(os.path.join(dp, f))
    return sorted(out)


def twin_media_dir(meta):
    return "%s-%s-%s" % (meta.get("target"), meta.get("level"), meta.get("theme"))


def scan_repo(repo, tags_file=None):
    """-> liste d'enregistrements, un par pack (learn et langues), triés par (type, id)."""
    tags_decl = {}
    if tags_file and os.path.isfile(tags_file):
        tags_decl = (read_json(tags_file).get("tags") or {})
    free, reserved = load_registry(repo)
    tracked = tracked_files(repo)
    recs = []
    content = os.path.join(repo, "content")
    for kind, sub, main in (("learn", "learn", "pack.json"), ("langues", "langues", "langue.json")):
        base = os.path.join(content, sub)
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base)):
            d = os.path.join(base, name)
            mp = os.path.join(d, main)
            if not os.path.isfile(mp):
                continue
            try:
                meta = read_json(mp)
            except (OSError, ValueError) as e:
                recs.append({"id": name, "kind": kind, "dir": d, "error": "illisible : %s" % e, "tag": parse_tag(None), "family": None, "meta": {}, "warnings": [], "problems": ["fichier principal illisible"]})
                continue
            pid = meta.get("id", name)
            rec = {"id": pid, "kind": kind, "dir": d, "main": main, "meta": meta, "version": meta.get("version"), "title": meta.get("title", pid), "warnings": [], "problems": []}
            found = collect_tags(d, tags_decl.get(pid))
            rec["tag"], rec["tagSource"] = merge_tags(found)
            key = "%s:%s" % (kind, pid)
            rec["lotKey"] = key
            rec["family"] = "libre" if key in free else "réservé" if key in reserved else None
            if key in free and key in reserved:
                rec["family"] = "libre+réservé"
            rec["tracked"] = None if tracked is None else any(t.startswith("content/%s/%s/" % (sub, name)) for t in tracked)
            rec["files"] = [(os.path.relpath(f, repo).replace(os.sep, "/"), f) for f in list_files(d)]
            rec["media"] = []
            if kind == "langues":
                rec["media"] = scan_langues_media(repo, rec)
            else:
                scan_learn_media(rec)
            recs.append(rec)
    recs.sort(key=lambda r: (r["kind"], r["id"]))
    return recs


def scan_langues_media(repo, rec):
    mp = os.path.join(rec["dir"], "media.json")
    items = []
    if not os.path.isfile(mp):
        return items
    try:
        mj = read_json(mp)
    except (OSError, ValueError):
        rec["problems"].append("media.json illisible")
        return items
    twin = os.path.join(repo, "content", "langues-media", twin_media_dir(rec["meta"]))
    for m in mj.get("media") or []:
        lic = m.get("license")
        it = {"id": m.get("id"), "license": lic, "file": m.get("file"), "item": m, "twin": twin}
        rel = m.get("file") or ""
        full = os.path.normpath(os.path.join(twin, rel))
        it["path"] = full if os.path.isfile(full) and full.startswith(os.path.normpath(twin) + os.sep) else None
        items.append(it)
    return items


def scan_learn_media(rec):
    def walk(o):
        if isinstance(o, dict):
            for k, v in o.items():
                if k == "media" and v:
                    rec["learnMedia"] = True
                walk(v)
        elif isinstance(o, list):
            for x in o:
                walk(x)
    for _, p in rec["files"]:
        if p.endswith(".json"):
            try:
                walk(read_json(p))
            except (OSError, ValueError):
                pass


def media_problems(rec):
    """Raisons pour lesquelles les médias d'un pack candidat bloquent l'archive."""
    out = []
    for it in rec["media"]:
        lic = it["license"]
        if not lic:
            out.append("média « %s » sans licence" % it["id"])
        elif lic not in MEDIA_OK:
            out.append("média « %s » : licence « %s » inconnue ou non libre" % (it["id"], lic))
        elif lic in MEDIA_NEEDS_ATTRIBUTION and not (it["item"].get("author") and it["item"].get("source")):
            out.append("média « %s » (%s) : auteur ou source manquant" % (it["id"], lic))
        if it["path"] is None:
            out.append("média « %s » : fichier « %s » introuvable dans le lot média jumeau" % (it["id"], it["file"]))
    if rec.get("learnMedia"):
        out.append("médias référencés dans les leçons : licence non vérifiable")
    return out


def classify(recs, flt, require_registered=False):
    """Pose rec['status'] ∈ included | excluded | refused et rec['reason']."""
    for r in recs:
        tag = r["tag"]
        if r.get("error"):
            r["status"], r["reason"] = "excluded", r["error"]
            continue
        # contradictions et signalements (toujours calculés)
        if r["family"] == "libre" and tag["kind"] != "by-sa":
            r["warnings"].append("enregistré LIBRE mais pas étiqueté CC BY-SA")
        if r["family"] is None:
            r["warnings"].append("non enregistré dans lots.json (famille inconnue)")
        if r["tracked"] is False:
            r["warnings"].append("non suivi par git (non enregistré / à valider)")
        if not matches_filter(tag, flt):
            r["status"] = "excluded"
            r["reason"] = ("aucune étiquette de licence" if tag["kind"] == "none"
                           else "licence « %s » ≠ %s" % (tag["text"], flt))
            if tag["kind"] == "none":
                r["warnings"].append("sans étiquette de licence : exclu")
            continue
        probs = []
        if r["family"] == "réservé":
            probs.append("étiqueté CC BY-SA mais enregistré RÉSERVÉ (contradiction : un contenu réservé n'entre jamais dans l'archive libre)")
        if r["family"] == "libre+réservé":
            probs.append("enregistré à la fois libre et réservé")
        if tag["kind"] == "ambiguous":
            probs.append("étiquette ambiguë ou mixte : « %s »" % tag["text"])
        elif tag["kind"] == "by-sa" and tag["version"] not in LICENSE_URLS:
            probs.append("version de licence absente ou non gérée (« %s ») : impossible de choisir le texte légal" % tag["text"])
        if require_registered and r["family"] != "libre":
            probs.append("non enregistré libre dans lots.json (--exiger-enregistre)")
        probs += media_problems(r)
        if probs:
            r["status"], r["reason"], r["problems"] = "refused", " ; ".join(probs), probs
        else:
            r["status"], r["reason"] = "included", "étiqueté %s" % tag["text"]
    return recs


def summary(recs):
    c = {"included": 0, "excluded": 0, "refused": 0}
    for r in recs:
        c[r["status"]] += 1
    c["excluded_untagged"] = sum(1 for r in recs if r["status"] == "excluded" and r["tag"]["kind"] == "none")
    c["excluded_other_license"] = sum(1 for r in recs if r["status"] == "excluded" and r["tag"]["kind"] not in ("none",))
    c["registered_free_untagged"] = sum(1 for r in recs if r["family"] == "libre" and r["tag"]["kind"] != "by-sa")
    c["registered_reserved"] = sum(1 for r in recs if r["family"] == "réservé")
    c["unregistered"] = sum(1 for r in recs if r["family"] is None)
    c["untracked"] = sum(1 for r in recs if r["tracked"] is False)
    c["total"] = len(recs)
    c["by_kind"] = {}
    for r in recs:
        c["by_kind"].setdefault(r["kind"], {"included": 0, "excluded": 0, "refused": 0})[r["status"]] += 1
    return c


def print_table(recs, out=sys.stdout):
    rows = [("Pack", "Type", "Étiquette trouvée", "Famille", "Suivi git", "Statut", "Raison")]
    for r in recs:
        rows.append((r["id"], r["kind"], r["tag"]["text"] or "(aucune)", r["family"] or "inconnue",
                     {True: "oui", False: "NON", None: "?"}[r["tracked"]], {"included": "INCLUS", "excluded": "exclu", "refused": "REFUSÉ"}[r["status"]], r["reason"]))
    w = [min(max(len(x[i]) for x in rows), 46) for i in range(len(rows[0]))]
    for i, row in enumerate(rows):
        out.write("  ".join(c[:w[j]].ljust(w[j]) if j < len(row) - 1 else c for j, c in enumerate(row)) + "\n")
        if i == 0:
            out.write("  ".join("-" * x for x in w) + "\n")


def print_summary(recs, out=sys.stdout):
    s = summary(recs)
    out.write("\nRésumé : %d pack(s) examiné(s) : %d inclus, %d exclu(s), %d refusé(s)\n" % (s["total"], s["included"], s["excluded"], s["refused"]))
    out.write("  exclus sans étiquette de licence : %d ; exclus avec une autre licence : %d\n" % (s["excluded_untagged"], s["excluded_other_license"]))
    out.write("  enregistrés LIBRES mais non étiquetés CC BY-SA : %d ; enregistrés RÉSERVÉS : %d ; famille inconnue : %d ; non suivis par git : %d\n"
              % (s["registered_free_untagged"], s["registered_reserved"], s["unregistered"], s["untracked"]))
    for k, v in sorted(s["by_kind"].items()):
        out.write("  %-8s inclus %d, exclus %d, refusés %d\n" % (k, v["included"], v["excluded"], v["refused"]))
    groups = {}
    for r in recs:
        for w in r["warnings"]:
            groups.setdefault(w, []).append(r["id"])
    if groups:
        out.write("\nAvertissements :\n")
        for w, ids in sorted(groups.items()):
            out.write("  - %s : %s\n" % (w, ", ".join(ids) if len(ids) <= 12 else "%d packs (ex. %s…)" % (len(ids), ", ".join(ids[:3]))))
    return s


# ------------------------------------------------------------------ construction

def versions_used(included):
    v = set()
    for r in included:
        if r["tag"]["kind"] == "by-sa":
            v.add(r["tag"]["version"])
        for it in r["media"]:
            m = re.fullmatch(r"CC-BY-SA-(\d\.\d)", it["license"] or "")
            if m:
                v.add(m.group(1))
    return sorted(v)


def license_text(version):
    """-> (contenu, verbatim). Le texte officiel n'est reproduit que s'il est fourni tel quel dans tools/free-content/licenses/."""
    p = os.path.join(HERE, "licenses", "CC-BY-SA-%s.txt" % version)
    if os.path.isfile(p):
        t = read_text(p)
        if VERBATIM_MARK[version].lower() in t.lower() and len(t) > 15000:
            return t, True
    url, legal = LICENSE_URLS[version]
    t = ("Creative Commons Attribution-ShareAlike %s International (CC BY-SA %s)\n\n"
         "ATTENTION : ce fichier n'est PAS le texte légal intégral. Le texte officiel et faisant foi est ici :\n"
         "  Résumé lisible : %s\n  Code juridique (texte légal complet) : %s\n\n"
         "Le propriétaire de CastBridge doit remplacer ce fichier par le texte officiel reproduit à l'identique\n"
         "(dépôt : tools/free-content/licenses/CC-BY-SA-%s.txt, puis régénérer l'archive).\n"
         % (version, version, url, legal, version))
    return t, False


def attribution_md(included, versions, verbatim):
    L = ["# Attribution des contenus libres de CastBridge", "",
         "Ces contenus sont diffusés sous licence **Creative Commons Attribution - Partage dans les Mêmes Conditions (CC BY-SA)**.", ""]
    for v in versions:
        L.append("- CC BY-SA %s : %s (texte légal : %s)" % (v, LICENSE_URLS[v][0], LICENSE_URLS[v][1]))
    L += ["", "Chaque pack ci-dessous a été **modifié par CastBridge** (mise en forme, adaptation, découpage en packs et lots). Aucune garantie.", ""]
    for r in included:
        meta = r["meta"]
        authors = meta.get("authors")
        authors = ", ".join(authors) if isinstance(authors, list) and authors else (authors if isinstance(authors, str) and authors else "CastBridge (auteur non précisé dans le pack)")
        L.append("## %s (`%s`)" % (r["title"], r["id"]))
        L.append("")
        L.append("- Type : %s, version %s" % ("pack Apprendre" if r["kind"] == "learn" else "pack Langues", r["version"]))
        L.append("- Auteurs / crédits : %s" % authors)
        src = meta.get("attribution") or meta.get("programRef")
        if src:
            L.append("- Source / attribution déclarée : %s" % src)
        else:
            L.append("- Source : non précisée dans le pack (œuvre de CastBridge)")
        url = meta.get("url") or meta.get("sourceUrl") or meta.get("originalUrl")
        if url:
            L.append("- Œuvre originale : %s" % url)
        L.append("- Licence : %s (étiquette trouvée : « %s »)" % ("CC BY-SA %s" % r["tag"]["version"], r["tag"]["text"]))
        L.append("- Modifications : modifié par CastBridge")
        if r["media"]:
            L.append("- Médias :")
            for it in r["media"]:
                m = it["item"]
                bits = ["licence %s" % it["license"]]
                if m.get("author"):
                    bits.append("auteur %s" % m["author"])
                if m.get("source"):
                    bits.append("source %s" % m["source"])
                if m.get("url"):
                    bits.append("lien %s" % m["url"])
                if m.get("synthetic"):
                    bits.append("voix de synthèse (marquée synthétique)")
                if m.get("voiceLicense"):
                    bits.append("moteur sous %s" % m["voiceLicense"])
                L.append("  - `%s` : %s" % (it["id"], ", ".join(bits)))
        L.append("")
    if not verbatim:
        L += ["---", "", "Note : le texte légal intégral n'est pas encore joint à cette archive (voir `LICENSE-*.txt` : seulement les liens officiels).", ""]
    return "\n".join(L)


def readme_txt(included, versions, repo, generated_at):
    vp = {}
    try:
        for line in read_text(os.path.join(repo, "version.properties")).splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.strip().split("=", 1)
                vp[k] = v
    except OSError:
        pass
    tel = vp.get("phone.versionName", "voir version.properties")
    tv = vp.get("tv.versionName", "voir version.properties")
    lic = ", ".join("CC BY-SA %s" % v for v in versions)
    return """CONTENUS LIBRES DE CASTBRIDGE
==============================

Ce que c'est
------------
Cette archive réunit les contenus éducatifs de CastBridge diffusés sous licence libre (%s).
Elle contient %d pack(s). Aucune clé, aucune activation, aucun compte ne sont nécessaires pour la lire.
Généré le %s.

Ce que vous pouvez faire (CC BY-SA)
-----------------------------------
Vous pouvez copier, partager, adapter et réutiliser ces contenus, y compris commercialement, à condition de :
  1. Attribuer l'œuvre : citer les auteurs, donner le lien vers la licence et indiquer les modifications (voir ATTRIBUTION.md) ;
  2. Partager vos adaptations dans les mêmes conditions : sous la même licence CC BY-SA ;
  3. Ne pas ajouter de restriction juridique ou technique qui empêche les autres d'exercer ces droits.
Le texte de la licence est dans les fichiers LICENSE-CC-BY-SA-*.txt. En cas de doute, le texte officiel de Creative Commons fait foi.

Comment lire les packs
----------------------
- contenus/learn/<pack>/ : pack Apprendre (format 1) : pack.json + lessons/*.json (leçons, exercices, figures vectorielles en JSON).
- contenus/langues/<pack>/ : pack Langues (format 1) : langue.json (unités, vocabulaire, dialogues, exercices) + media.json (liste des médias) + figures/*.json.
- contenus/langues-media/<pack>/audio/ : fichiers audio référencés par media.json (identifiants « m:<id> »). Les voix de synthèse sont marquées comme telles.
Ce sont des fichiers JSON lisibles par n'importe quel outil. Pour les installer dans CastBridge, ils sont empaquetés en « lots » (un lot = archive zip déterministe ;
lot Langues : langue.json et media.json à la racine). Versions de ce dépôt au moment de la génération : CastBridge (téléphone) %s, CastBridge-TV %s.
MANIFEST.json donne l'empreinte SHA-256 de chaque fichier : vérifiez l'intégrité avec « build_free_archive.py --check ».

Aucune garantie
---------------
Ces contenus sont fournis « en l'état », sans garantie d'aucune sorte, expresse ou implicite, notamment d'exactitude pédagogique, d'adéquation à un programme officiel
ou d'absence d'erreur. Beaucoup sont des brouillons bêta à faire vérifier par un enseignant ou un locuteur natif.
""" % (lic, len(included), generated_at, tel, tv)


def zip_bytes(entries, path):
    """entries : dict nom -> bytes. Zip déterministe : trié, horodatage fixe, mêmes attributs."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(entries):
            zi = zipfile.ZipInfo(name, ZIP_TIME)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.create_system = 3
            zi.external_attr = 0o644 << 16
            z.writestr(zi, entries[name], compresslevel=9)


def build(repo, recs, out_dir, generated_at, number, date_str):
    included = [r for r in recs if r["status"] == "included"]
    versions = versions_used(included)
    entries, packs_manifest = {}, []
    verbatim_all = True
    for v in versions:
        text, verb = license_text(v)
        verbatim_all = verbatim_all and verb
        entries["LICENSE-CC-BY-SA-%s.txt" % v] = text.encode("utf-8")
    entries["ATTRIBUTION.md"] = attribution_md(included, versions, verbatim_all).encode("utf-8")
    entries["LISEZ-MOI.txt"] = readme_txt(included, versions, repo, generated_at).encode("utf-8")
    media_seen = {}
    for r in included:
        files = []
        for rel, full in r["files"]:
            arc = "contenus/" + rel[len("content/"):]
            data = read_bytes(full)
            entries[arc] = data
            files.append(arc)
        for it in r["media"]:
            rel = "contenus/langues-media/%s/%s" % (os.path.basename(it["twin"]), it["file"])
            if rel not in entries:
                entries[rel] = read_bytes(it["path"])
            files.append(rel)
            media_seen[rel] = it["license"]
        files = sorted(set(files))
        packs_manifest.append({"id": r["id"], "kind": r["kind"], "version": r["version"], "title": r["title"], "license": "CC BY-SA", "licenseVersion": r["tag"]["version"],
                               "licenseTag": r["tag"]["text"], "tagSource": r["tagSource"], "family": r["family"], "files": [
                                   {"path": f, "sha256": hashlib.sha256(entries[f]).hexdigest(), "bytes": len(entries[f])} for f in files]})
    all_files = {n: {"sha256": hashlib.sha256(b).hexdigest(), "bytes": len(b)} for n, b in entries.items()}
    manifest = {"format": 1, "builder": {"name": "build_free_archive.py", "version": BUILDER_VERSION}, "generatedAt": generated_at, "build": number,
                "licenseTextVerbatim": verbatim_all and bool(versions), "licenseVersions": versions,
                "totalBytes": sum(len(b) for b in entries.values()), "fileCount": len(entries), "packs": packs_manifest,
                "files": dict(sorted(all_files.items())), "mediaLicenses": dict(sorted(media_seen.items()))}
    entries["MANIFEST.json"] = (json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8")
    name = "castbridge-contenus-libres-%s-v%d.zip" % (date_str, number)
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, name)
    zip_bytes(entries, path)
    return path, manifest


def check_archive(path, out=sys.stdout):
    """Vérifie un zip contre son MANIFEST. -> liste d'anomalies (vide = intègre)."""
    bad = []
    try:
        z = zipfile.ZipFile(path)
    except (OSError, zipfile.BadZipFile) as e:
        return ["archive illisible : %s" % e]
    with z:
        if "MANIFEST.json" not in z.namelist():
            return ["MANIFEST.json absent"]
        try:
            man = json.loads(z.read("MANIFEST.json").decode("utf-8"))
        except ValueError:
            return ["MANIFEST.json illisible"]
        try:
            first = z.testzip()
        except Exception as e:  # noqa: BLE001
            first = str(e)
        if first:
            bad.append("entrée corrompue : %s" % first)
        listed = man.get("files") or {}
        names = set(z.namelist()) - {"MANIFEST.json"}
        for n in sorted(listed):
            if n not in names:
                bad.append("fichier manquant : %s" % n)
                continue
            try:
                data = z.read(n)
            except Exception as e:  # noqa: BLE001
                bad.append("illisible : %s (%s)" % (n, e))
                continue
            if hashlib.sha256(data).hexdigest() != listed[n]["sha256"] or len(data) != listed[n]["bytes"]:
                bad.append("empreinte différente (fichier modifié) : %s" % n)
        for n in sorted(names - set(listed)):
            bad.append("fichier absent du manifeste (ajouté) : %s" % n)
        if len(names) != man.get("fileCount"):
            bad.append("nombre de fichiers différent du manifeste")
    return bad


# ------------------------------------------------------------------ CLI

def main(argv=None, out=None, err=None):
    out = out or sys.stdout
    err = err or sys.stderr
    ap = argparse.ArgumentParser(description="Construit l'archive des contenus libres CC BY-SA de CastBridge.")
    ap.add_argument("--repo", default=DEFAULT_REPO, help="racine du dépôt (défaut : celui de l'outil)")
    ap.add_argument("--out", default=".", metavar="DIR", help="dossier de sortie du zip")
    ap.add_argument("--only-tagged-as", default="CC BY-SA", metavar="LICENCE", help="étiquette retenue (défaut « CC BY-SA » ; « CC BY-SA 4.0 » pour une version précise)")
    ap.add_argument("--dry-run", action="store_true", help="affiche l'inventaire sans rien écrire")
    ap.add_argument("--check", metavar="ZIP", help="vérifie une archive existante contre son MANIFEST.json")
    ap.add_argument("--tags", default=os.path.join(HERE, "license-tags.json"), metavar="FICHIER", help="étiquettes déclarées par le propriétaire (défaut : license-tags.json)")
    ap.add_argument("--generated-at", help="horodatage ISO 8601 injecté (reproductibilité) ; défaut : maintenant UTC")
    ap.add_argument("--build", type=int, default=1, metavar="N", help="numéro de version de l'archive (v<N>)")
    ap.add_argument("--require-registered", action="store_true", help="refuse aussi un pack étiqueté mais non enregistré libre dans lots.json")
    ap.add_argument("--allow-empty", action="store_true", help="n'échoue pas si aucun pack n'est retenu (construit une archive vide)")
    a = ap.parse_args(argv)

    if a.check:
        bad = check_archive(a.check)
        if bad:
            err.write("ARCHIVE ALTÉRÉE ou invalide :\n" + "".join("  - %s\n" % b for b in bad))
            return 4
        out.write("Archive intègre : %s\n" % a.check)
        return 0

    repo = os.path.abspath(a.repo)
    if not os.path.isdir(os.path.join(repo, "content")):
        err.write("Dossier « content » introuvable dans %s\n" % repo)
        return 3
    try:
        recs = scan_repo(repo, a.tags)
    except (OSError, ValueError) as e:
        err.write("Lecture impossible : %s\n" % e)
        return 3
    classify(recs, a.only_tagged_as, a.require_registered)
    print_table(recs, out)
    print_summary(recs, out)

    refused = [r for r in recs if r["status"] == "refused"]
    if refused:
        err.write("\nREFUS : l'archive n'est pas construite.\n")
        for r in refused:
            err.write("  - %s : %s\n" % (r["id"], r["reason"]))
        return 2
    included = [r for r in recs if r["status"] == "included"]
    if not included and not a.allow_empty:
        err.write("\n%s : aucun contenu n'est étiqueté « %s » dans le dépôt ; une archive vide n'est pas construite.\n"
                  "Étiquetez les packs (champ « license »: « CC BY-SA 4.0 » dans pack.json / langue.json) ou renseignez tools/free-content/license-tags.json, sur décision du propriétaire.\n"
                  % ("ATTENTION" if a.dry_run else "REFUS", a.only_tagged_as))
        if not a.dry_run:
            return 2
    if a.dry_run:
        out.write("\n(--dry-run : aucune archive écrite)\n")
        return 0
    generated_at = a.generated_at or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    date_str = re.sub(r"[^0-9]", "", generated_at)[:8]
    if len(date_str) != 8:
        err.write("--generated-at invalide (ISO 8601 attendu) : %s\n" % generated_at)
        return 3
    path, man = build(repo, recs, a.out, generated_at, a.build, date_str)
    out.write("\nArchive écrite : %s (%d fichiers, %d octets, texte de licence intégral : %s)\n" % (path, man["fileCount"], man["totalBytes"], "oui" if man["licenseTextVerbatim"] else "NON"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
