#!/usr/bin/env python3
"""Contrôle de cohérence des versions de CastBridge (lecture seule, bibliothèque standard uniquement).

Vérifie :
  a) version.properties : forme, SemVer, versionCode strictement croissant par rapport à HEAD et aux tags
     tv-*, phone-*, owner-*, server-* ;
  b) la règle de la variante TV verrouillée (code +1, suffixe -verrouillee) documentée de façon cohérente ;
  c) avec --apk : versionName/versionCode lus par aapt2, comparés à version.properties ;
  d) l'état git : branche, avance/retard sur origin, fichiers non commités / non suivis, HEAD étiqueté ;
  e) backend/pom.xml comparé à server.versionName (proposition de lignes server.* si absentes).

Codes de sortie : 0 = conforme (avertissements possibles), 1 = au moins une erreur (ou un avertissement avec --strict),
2 = usage ou environnement invalide (pas un dépôt git, version.properties illisible).

Ne modifie rien : aucune écriture dans le dépôt, aucun accès réseau.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-beta)?$")
APPS = ("phone", "tv", "owner", "dev")
TAG_APPS = ("tv", "phone", "owner", "server")
PACKAGE_APP = {"castbridge.receiver": "tv", "castbridge.sender": "phone",
               "castbridge.owner": "owner", "castbridge.dev": "dev"}
LOCK_SUFFIX = "-verrouillee"

OK, WARN, ERR = "ok", "avert", "erreur"


class Report:
    def __init__(self):
        self.items = []

    def add(self, level, check, message):
        self.items.append({"niveau": level, "controle": check, "message": message})

    def count(self, level):
        return sum(1 for i in self.items if i["niveau"] == level)


def git(root, *args):
    try:
        p = subprocess.run(["git", "-C", root] + list(args), capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.SubprocessError):
        return 127, ""
    return p.returncode, p.stdout


def parse_properties(text):
    """Retourne (dict, [lignes mal formées], [clés dupliquées])."""
    props, bad, dup = {}, [], []
    for n, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            bad.append("ligne %d : « %s »" % (n, line[:60]))
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if not re.match(r"^[A-Za-z][A-Za-z0-9_.]*$", key):
            bad.append("ligne %d : clé invalide « %s »" % (n, key[:40]))
            continue
        if key in props:
            dup.append(key)
        props[key] = value
    return props, bad, dup


def semver_key(name):
    m = SEMVER.match(name or "")
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)), 0 if m.group(4) else 1)


def to_int(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def app_version(props, app):
    return props.get(app + ".versionName"), to_int(props.get(app + ".versionCode"))


# ---------------------------------------------------------------------------------------------- a)
def check_properties(props, bad, dup, rep):
    for b in bad:
        rep.add(ERR, "version.properties", "ligne mal formée : " + b)
    for d in sorted(set(dup)):
        rep.add(ERR, "version.properties", "clé dupliquée : " + d)
    for app in APPS:
        name, code = app_version(props, app)
        if name is None:
            rep.add(ERR, "version.properties", "%s.versionName absent" % app)
        elif semver_key(name) is None:
            rep.add(ERR, "version.properties", "%s.versionName « %s » n'est pas du SemVer X.Y.Z[-beta]" % (app, name))
        if code is None:
            rep.add(ERR, "version.properties", "%s.versionCode absent ou non entier" % app)
        elif code < 1:
            rep.add(ERR, "version.properties", "%s.versionCode doit être >= 1 (valeur %d)" % (app, code))
    gs = props.get("lock.graceStartMs")
    if gs is None or to_int(gs) is None or to_int(gs) <= 0:
        rep.add(ERR, "version.properties", "lock.graceStartMs absent ou invalide (millisecondes UTC > 0)")
    gd = props.get("lock.graceDays")
    if gd is not None and (to_int(gd) is None or not 0 <= to_int(gd) <= 365):
        rep.add(ERR, "version.properties", "lock.graceDays doit être un entier de 0 à 365")
    sname, scode = props.get("server.versionName"), props.get("server.versionCode")
    if (sname is None) != (scode is None):
        rep.add(ERR, "version.properties", "server.versionName et server.versionCode vont par paire")
    elif sname is not None:
        if semver_key(sname) is None and not re.match(r"^\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?$", sname):
            rep.add(ERR, "version.properties", "server.versionName « %s » invalide" % sname)
        if to_int(scode) is None or to_int(scode) < 1:
            rep.add(ERR, "version.properties", "server.versionCode doit être un entier >= 1")
    if not any(i["controle"] == "version.properties" for i in rep.items):
        rep.add(OK, "version.properties", "bien formé (%d clés)" % len(props))


def check_vs_head(root, props, rep):
    rc, text = git(root, "show", "HEAD:version.properties")
    if rc != 0:
        rep.add(OK, "monotonie/HEAD", "pas de version.properties dans HEAD : rien à comparer")
        return
    old, _, _ = parse_properties(text)
    apps = list(APPS) + (["server"] if "server.versionCode" in props and "server.versionCode" in old else [])
    problems = 0
    for app in apps:
        n_new, c_new = app_version(props, app)
        n_old, c_old = app_version(old, app)
        if c_new is None or c_old is None or n_new is None or n_old is None:
            continue
        if n_new == n_old:
            if c_new != c_old:
                rep.add(ERR, "monotonie/HEAD", "%s : versionCode changé (%d -> %d) sans changer le nom %s" % (app, c_old, c_new, n_new))
                problems += 1
            continue
        k_new, k_old = semver_key(n_new), semver_key(n_old)
        if k_new is not None and k_old is not None and k_new < k_old:
            rep.add(ERR, "monotonie/HEAD", "%s : version rétrogradée %s -> %s" % (app, n_old, n_new))
            problems += 1
        floor = c_old + (2 if app == "tv" else 1)
        if c_new < floor:
            extra = " (la variante verrouillée de la version précédente a pris le code %d)" % (c_old + 1) if app == "tv" else ""
            rep.add(ERR, "monotonie/HEAD", "%s : versionCode %d -> %d, attendu >= %d%s" % (app, c_old, c_new, floor, extra))
            problems += 1
        else:
            rep.add(OK, "monotonie/HEAD", "%s : %s (%d) -> %s (%d)" % (app, n_old, c_old, n_new, c_new))
    if problems == 0 and not any(i["controle"] == "monotonie/HEAD" for i in rep.items):
        rep.add(OK, "monotonie/HEAD", "versions identiques à HEAD (aucune montée de version non commitée)")


def list_tags(root, app):
    rc, out = git(root, "tag", "-l", app + "-*")
    return [t for t in out.split() if t.startswith(app + "-")] if rc == 0 else []


def check_vs_tags(root, props, rep):
    seen = False
    for app in TAG_APPS:
        n_cur, c_cur = app_version(props, app)
        tags = list_tags(root, app)
        if not tags:
            continue
        seen = True
        max_code, max_tag = None, None
        for tag in tags:
            tname = tag[len(app) + 1:]
            if semver_key(tname) is None and app != "server":
                rep.add(WARN, "tags", "tag %s : nom de version hors SemVer X.Y.Z[-beta]" % tag)
            rc, text = git(root, "show", "%s:version.properties" % tag)
            if rc != 0:
                continue
            tprops, _, _ = parse_properties(text)
            _, tcode = app_version(tprops, app)
            if tcode is None:
                continue
            if n_cur == tname and c_cur is not None and c_cur != tcode:
                rep.add(ERR, "tags", "%s : le tag a le code %d mais version.properties dit %d pour la même version" % (tag, tcode, c_cur))
            if max_code is None or tcode > max_code:
                max_code, max_tag = tcode, tag
            reserved = tcode + (1 if app == "tv" else 0)  # TV : tcode+1 est pris par la variante verrouillée
            if n_cur != tname and c_cur is not None and reserved >= c_cur:
                rep.add(ERR, "tags", "%s : code %d%s >= code actuel %d (%s) : versionCode non strictement croissant"
                        % (tag, tcode, " (+1 variante verrouillée)" if app == "tv" else "", c_cur, n_cur))
        if max_code is not None and c_cur is not None and not any(
                i["niveau"] == ERR and i["controle"] == "tags" and ("%s-" % app) in i["message"] for i in rep.items):
            rep.add(OK, "tags", "%s : code actuel %s > plus haut code étiqueté %d (%s)" % (app, c_cur, max_code, max_tag)
                    if n_cur not in [t[len(app) + 1:] for t in tags] else
                    "%s : %s déjà étiqueté, codes identiques" % (app, n_cur))
    if not seen:
        rep.add(WARN, "tags", "aucun tag tv-*, phone-*, owner-*, server-* : rien n'est étiqueté (voir tools/release/tag-plan.sh)")
    # tags de la version courante
    for app in ("tv", "phone", "owner"):
        n_cur, _ = app_version(props, app)
        if n_cur and "%s-%s" % (app, n_cur) not in list_tags(root, app):
            rep.add(WARN, "tags", "tag %s-%s absent" % (app, n_cur))


# ---------------------------------------------------------------------------------------------- b)
def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def check_lock_rule(root, rep):
    start = len(rep.items)
    sources = {
        "version.properties": read(os.path.join(root, "version.properties")),
        "docs/RELEASES.md": read(os.path.join(root, "docs", "RELEASES.md")),
        "android/receiver/build.gradle.kts": read(os.path.join(root, "android", "receiver", "build.gradle.kts")),
    }
    for label, text in sources.items():
        if text is None:
            rep.add(WARN, "variante verrouillée", "%s introuvable : règle non vérifiable à cet endroit" % label)
            continue
        if re.search(r"-verrouill(?!ee)", text):
            rep.add(ERR, "variante verrouillée", "%s : suffixe incohérent (attendu « %s »)" % (label, LOCK_SUFFIX))
    props_text, doc, gradle = sources["version.properties"], sources["docs/RELEASES.md"], sources["android/receiver/build.gradle.kts"]
    if props_text is not None and not (LOCK_SUFFIX in props_text and re.search(r"\+\s*1", props_text)):
        rep.add(ERR, "variante verrouillée", "version.properties ne documente pas « +1 » et « %s »" % LOCK_SUFFIX)
    if doc is not None:
        if LOCK_SUFFIX not in doc or not re.search(r"\+\s*1", doc):
            rep.add(ERR, "variante verrouillée", "docs/RELEASES.md ne documente pas « +1 » et « %s »" % LOCK_SUFFIX)
        for m in re.finditer(r"sans verrou\w*\s*=\s*(\d+)[^\n]*?verrouill\w*\s*=\s*(\d+)", doc, re.I):
            if int(m.group(2)) != int(m.group(1)) + 1:
                rep.add(ERR, "variante verrouillée", "docs/RELEASES.md : exemple %s -> %s (attendu +1)" % (m.group(1), m.group(2)))
        m = re.search(r"Code d'installation\s*:\s*ex\.\s*(\d+)(?:.|\n){0,400}?Code d'installation\s*:[^\n]*?\+\s*1\s*\(ex\.\s*(\d+)\)", doc)
        if m and int(m.group(2)) != int(m.group(1)) + 1:
            rep.add(ERR, "variante verrouillée", "docs/RELEASES.md : codes d'exemple %s et %s incohérents (+1)" % (m.group(1), m.group(2)))
    if gradle is not None:
        if LOCK_SUFFIX not in gradle or not re.search(r"versionCode.*\+\s*if\s*\(locked\)\s*1", gradle):
            rep.add(ERR, "variante verrouillée", "android/receiver/build.gradle.kts n'applique pas « code +1 » et « %s »" % LOCK_SUFFIX)
    if not any(i["niveau"] in (ERR,) and i["controle"] == "variante verrouillée" for i in rep.items[start:]):
        rep.add(OK, "variante verrouillée", "règle « code +1, suffixe %s » documentée de façon cohérente" % LOCK_SUFFIX)


# ---------------------------------------------------------------------------------------------- c)
def find_aapt2():
    env = os.environ.get("CASTBRIDGE_AAPT2")
    if env:
        return env if os.path.isfile(env) else None
    candidates = []
    homes = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
             os.path.expanduser("~/Library/Android/sdk"), os.path.expanduser("~/Android/Sdk")]
    for h in homes:
        if h:
            candidates += glob.glob(os.path.join(h, "build-tools", "*", "aapt2"))

    def key(p):
        v = os.path.basename(os.path.dirname(p))
        return [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", v)]
    if candidates:
        return sorted(candidates, key=key)[-1]
    return shutil.which("aapt2")


def read_badging(aapt2, apk):
    try:
        p = subprocess.run([aapt2, "dump", "badging", apk], capture_output=True, text=True, timeout=120)
    except (OSError, subprocess.SubprocessError) as e:
        return None, "exécution d'aapt2 impossible : %s" % e
    if p.returncode != 0:
        return None, "aapt2 a échoué (%s)" % (p.stderr.strip().splitlines() or ["?"])[-1][:120]
    m = re.search(r"package: name='([^']*)' versionCode='(\d*)' versionName='([^']*)'", p.stdout)
    if not m:
        return None, "ligne « package: » introuvable dans la sortie d'aapt2"
    return {"package": m.group(1), "versionCode": to_int(m.group(2)), "versionName": m.group(3)}, None


def guess_app(package, path):
    if package in PACKAGE_APP:
        return PACKAGE_APP[package]
    base = os.path.basename(path).lower()
    for needle, app in (("receiver", "tv"), ("castbridge-tv", "tv"), ("sender", "phone"), ("owner", "owner"), ("dev", "dev")):
        if needle in base:
            return app
    return None


def check_apks(props, apks, rep):
    if not apks:
        return
    aapt2 = find_aapt2()
    if not aapt2:
        rep.add(WARN, "apk", "aapt2 introuvable (cherché dans ~/Library/Android/sdk/build-tools/*/aapt2, ANDROID_HOME, PATH) : %d APK non vérifié(s)" % len(apks))
        return
    for apk in apks:
        if not os.path.isfile(apk):
            rep.add(ERR, "apk", "%s : fichier introuvable" % apk)
            continue
        info, err = read_badging(aapt2, apk)
        if err:
            rep.add(ERR, "apk", "%s : %s" % (os.path.basename(apk), err))
            continue
        app = guess_app(info["package"], apk)
        label = "%s (%s %s/%s)" % (os.path.basename(apk), info["package"], info["versionName"], info["versionCode"])
        if app is None:
            rep.add(WARN, "apk", "%s : application non reconnue, comparaison impossible" % label)
            continue
        name, code = app_version(props, app)
        variants = [(name, code, "sans verrou")]
        if app == "tv" and code is not None and name:
            variants.append((name + LOCK_SUFFIX, code + 1, "verrouillée"))
        match = [v for v in variants if v[0] == info["versionName"] and v[1] == info["versionCode"]]
        if match:
            rep.add(OK, "apk", "%s conforme à version.properties (%s, variante %s)" % (label, app, match[0][2]))
        else:
            expected = " ou ".join("%s/%s" % (v[0], v[1]) for v in variants)
            rep.add(ERR, "apk", "%s : écart avec version.properties, attendu %s" % (label, expected))


# ---------------------------------------------------------------------------------------------- d)
def git_state(root, rep):
    state = {}
    rc, out = git(root, "rev-parse", "--abbrev-ref", "HEAD")
    branch = out.strip() if rc == 0 else None
    state["branche"] = branch
    rc, out = git(root, "rev-parse", "--short", "HEAD")
    state["commit"] = out.strip() if rc == 0 else None
    upstream = None
    rc, out = git(root, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
    if rc == 0:
        upstream = out.strip()
    elif branch:
        rc2, _ = git(root, "rev-parse", "--verify", "-q", "refs/remotes/origin/%s" % branch)
        if rc2 == 0:
            upstream = "origin/%s" % branch
    state["amont"] = upstream
    if upstream:
        rc, out = git(root, "rev-list", "--left-right", "--count", "HEAD...%s" % upstream)
        if rc == 0 and len(out.split()) == 2:
            ahead, behind = (int(x) for x in out.split())
            state["avance"], state["retard"] = ahead, behind
            lvl = WARN if (ahead or behind) else OK
            rep.add(lvl, "git", "branche %s vs %s : %d en avance, %d en retard (d'après les références locales ; faire « git fetch » pour être à jour)" % (branch, upstream, ahead, behind))
    else:
        rep.add(WARN, "git", "branche %s : aucun amont origin connu" % branch)
    rc, out = git(root, "status", "--porcelain")
    lines = [l for l in out.splitlines() if l.strip()] if rc == 0 else []
    untracked = [l for l in lines if l.startswith("??")]
    modified = [l for l in lines if not l.startswith("??")]
    state["modifies"], state["non_suivis"] = len(modified), len(untracked)
    if modified or untracked:
        rep.add(WARN, "git", "arbre de travail : %d fichier(s) modifié(s) non commité(s), %d non suivi(s)" % (len(modified), len(untracked)))
    else:
        rep.add(OK, "git", "arbre de travail propre")
    if any(l[3:].strip() == "version.properties" for l in modified):
        rep.add(WARN, "git", "version.properties modifié mais non commité")
    rc, out = git(root, "tag", "--points-at", "HEAD")
    tags = out.split() if rc == 0 else []
    state["tags_head"] = tags
    rep.add(OK if tags else WARN, "git", "HEAD étiqueté : %s" % (", ".join(tags) if tags else "non (aucun tag sur ce commit)"))
    return state


# ---------------------------------------------------------------------------------------------- e)
def pom_version(path):
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return None
    ns = ""
    if root.tag.startswith("{"):
        ns = root.tag[:root.tag.index("}") + 1]
    el = root.find(ns + "version")  # enfant direct du projet (pas celui de <parent>)
    return el.text.strip() if el is not None and el.text else None


def check_server(root, props, rep):
    pv = pom_version(os.path.join(root, "backend", "pom.xml"))
    if pv is None:
        rep.add(WARN, "serveur", "backend/pom.xml introuvable ou sans <version>")
        return
    sname, scode = props.get("server.versionName"), props.get("server.versionCode")
    if sname is None:
        rep.add(WARN, "serveur", "pas de server.versionName dans version.properties (pom.xml : %s). Lignes à ajouter : server.versionName=%s et server.versionCode=1" % (pv, pv))
        return
    base = pv.replace("-SNAPSHOT", "")
    if sname != pv and sname != base:
        rep.add(ERR, "serveur", "server.versionName=%s différent de backend/pom.xml <version>%s</version>" % (sname, pv))
    else:
        rep.add(OK, "serveur", "server.versionName=%s (code %s) cohérent avec backend/pom.xml" % (sname, scode))


# ---------------------------------------------------------------------------------------------- main
def run(root, apks):
    rep = Report()
    path = os.path.join(root, "version.properties")
    text = read(path)
    if text is None:
        return rep, None, 2, "version.properties illisible : %s" % path
    rc, _ = git(root, "rev-parse", "--git-dir")
    if rc != 0:
        return rep, None, 2, "%s n'est pas un dépôt git" % root
    props, bad, dup = parse_properties(text)
    check_properties(props, bad, dup, rep)
    check_vs_head(root, props, rep)
    check_vs_tags(root, props, rep)
    check_lock_rule(root, rep)
    check_apks(props, apks, rep)
    state = git_state(root, rep)
    check_server(root, props, rep)
    return rep, state, 0, None


def main(argv=None):
    ap = argparse.ArgumentParser(description="Contrôle de cohérence des versions de CastBridge (lecture seule).",
                                 epilog="Codes de sortie : 0 conforme, 1 erreur(s), 2 usage/environnement.")
    ap.add_argument("--root", default=os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")),
                    help="racine du dépôt (défaut : celle de ce script)")
    ap.add_argument("--apk", nargs="+", default=[], metavar="FILE", help="APK à comparer à version.properties (aapt2)")
    ap.add_argument("--json", action="store_true", help="sortie JSON")
    ap.add_argument("--strict", action="store_true", help="les avertissements donnent aussi le code 1")
    args = ap.parse_args(argv)
    root = os.path.abspath(args.root)
    rep, state, code, fatal = run(root, args.apk)
    if fatal:
        if args.json:
            print(json.dumps({"code": 2, "erreur": fatal}, ensure_ascii=False))
        else:
            print("ERREUR : " + fatal, file=sys.stderr)
        return 2
    errors, warns = rep.count(ERR), rep.count(WARN)
    code = 1 if errors or (args.strict and warns) else 0
    if args.json:
        print(json.dumps({"code": code, "erreurs": errors, "avertissements": warns, "git": state,
                          "controles": rep.items}, ensure_ascii=False, indent=2))
    else:
        tag = {OK: "[OK]    ", WARN: "[AVERT] ", ERR: "[ERREUR]"}
        print("Contrôle des versions CastBridge : %s" % root)
        for i in rep.items:
            print("%s %-22s %s" % (tag[i["niveau"]], i["controle"], i["message"]))
        print("Bilan : %d erreur(s), %d avertissement(s) -> code %d" % (errors, warns, code))
    return code


if __name__ == "__main__":
    sys.exit(main())
