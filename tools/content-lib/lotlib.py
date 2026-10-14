"""Shared helpers of the content tools (budget, media check, lot builder, mapping): which lot does a file belong to?

Rule B (docs/CONTENT-ARCHITECTURE.md § 3): every lesson, exercise, question and media asset belongs to EXACTLY ONE lot
(feature, scope). Learn packs and quiz packs carry their class in `level`; media assets name their lot in MEDIA-MANIFEST.json.
"""
import json, os, re, subprocess, unicodedata, zlib

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CONTENT = os.path.join(REPO, "content")

TV_LOT_MAX = 3 << 20          # 3 MB per TV-eligible lot, figures and animations included
PHONE_PATH_MAX = 100 << 20    # 100 MB: all lots of a learner path on the phone (= LotBudget.PHONE_MAX_BYTES)
FILE_MAX = 50 << 20           # no single file above 50 MB
TOTAL_MAX = 3 << 30           # owner ceiling: 3 GB
TOTAL_WARN = int(2.5 * (1 << 30))

# pack `level` (as written in pack.json / quiz catalogs) -> lot scope
LEVEL_SCOPE = {"ms": "mat", "ps": "mat", "gs": "mat", "cm2": "cm2", "cm1": "cm1", "ce2": "ce2", "ce1": "ce1", "cp": "cp", "sil": "sil",
               "nursery 1": "nursery", "nursery 2": "nursery",
               "class 6": "class6", "class 5": "class5", "class 4": "class4", "class 3": "class3", "class 2": "class2", "class 1": "class1",
               "3e": "3e", "4e": "4e", "5e": "5e", "6e": "6e", "2nde": "2nde", "1re": "1ere", "1ere": "1ere", "tle": "tle",
               "form 1": "form1", "form 2": "form2", "form 3": "form3", "form 4": "form4", "form 5": "form5",
               "lower sixth": "lower-sixth", "upper sixth": "upper-sixth", "l1": "l1", "l2": "l2", "l3": "l2"}
FIELD_SCOPE = {"droit": "droit", "economie": "economie", "mathematiques": "mathematiques", "physique": "physique", "psychologie": "psychologie",
               "geographie": "geographie", "litterature": "litterature", "histoire": "histoire", "informatique": "informatique",
               "chimie": "chimie", "biologie": "biologie", "philosophie": "philosophie", "sociologie": "sociologie"}
# pack `subject` -> graph domain
SUBJECT_DOMAIN = {"maths": "mathematiques", "francais": "francais", "english": "english", "sciences": "svt", "biology": "svt", "svt": "svt",
                  "physique-chimie": "physique-chimie", "pct": "physique-chimie", "decouverte": "mathematiques", "hge": "histoire-geo-ecm",
                  "informatique": "informatique",
                  "histoire-geo": "histoire-geo-ecm", "physics": "physique-chimie", "chemistry": "physique-chimie"}
MEDIA_EXT = {"image": {".webp", ".png", ".jpg", ".jpeg", ".gif"}, "figure": {".svg"}, "audio": {".opus", ".ogg", ".mp3", ".m4a", ".wav"},
             "clip": {".mp4", ".webm", ".mkv", ".mov"}}


_GIT_HOOK_ENV = ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_PREFIX")   # posées par un hook git : elles ramèneraient à un autre dépôt que `root`


def git_ignore_filter(root, whole_repo=False):
    """Retourne `ignored(rel) -> bool` : git IGNORE-t-il ce chemin (relatif à `root`, séparé par « / ») ?

    Les contrôles de contenu jugent le DÉPÔT, pas le disque du poste : une copie locale que git ignore (le dossier `content/langues-media/` du propriétaire, règle de
    `.gitignore` : « jamais dans le dépôt de code, ils vont dans castbridge-content ») n'en fait pas partie. Le dépôt, c'est ce que `git add -A` y mettrait : les fichiers SUIVIS
    plus les fichiers non suivis NON ignorés, obtenus en UN seul appel `git ls-files -z --cached --others --exclude-standard` (un `git check-ignore` par fichier coûterait des
    milliers de processus). Un fichier non suivi et non ignoré reste donc jugé : c'est exactement le cas que la garde doit attraper.

    Repli sur le parcours complet du système de fichiers (`ignored` répond toujours False, comportement d'avant) quand l'avis de git ne vaut rien pour `root` : git absent ou en
    erreur, `root` hors de tout dépôt (dossier temporaire d'un test, export du dépôt de contenu avant `git init`), `root` lui-même ignoré par le dépôt qui le contient (un export
    sous `build/` : sinon la garde ne verrait plus rien), ou, avec `whole_repo=True`, `root` qui n'est pas la racine du dépôt (un export posé dans un autre dépôt)."""
    env = {k: v for k, v in os.environ.items() if k not in _GIT_HOOK_ENV}

    def git(*args):
        return subprocess.run(["git", "-C", root, *args], capture_output=True, env=env, timeout=120)

    try:
        r = git("rev-parse", "--is-inside-work-tree", "--show-toplevel")
        if r.returncode != 0:
            return lambda rel: False
        inside, _, top = r.stdout.decode("utf-8", "surrogateescape").partition("\n")
        if inside.strip() != "true" or (whole_repo and os.path.realpath(top.strip()) != os.path.realpath(root)):
            return lambda rel: False
        if git("check-ignore", "-q", "--", ".").returncode == 0:
            return lambda rel: False
        r = git("ls-files", "-z", "--cached", "--others", "--exclude-standard")
        if r.returncode != 0:
            return lambda rel: False
    except (OSError, subprocess.SubprocessError):
        return lambda rel: False
    norm = lambda path: unicodedata.normalize("NFC", path)   # macOS écrit parfois les accents décomposés : sans cela un fichier accentué passerait pour « ignoré »
    visible = {norm(f.decode("utf-8", "surrogateescape")) for f in r.stdout.split(b"\0") if f}
    return lambda rel: norm(rel) not in visible


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def learn_scope(pack):
    return LEVEL_SCOPE.get(str(pack.get("level", "")).strip().lower())


def quiz_scope(entry):
    if entry.get("track") == "general" or entry.get("course") == "general":
        return "culture-cm"
    lvl = str(entry.get("level") or "").strip().lower()
    if entry.get("field"):
        return FIELD_SCOPE.get(entry["field"], entry["field"]) + "-" + (lvl if lvl in ("l1", "l2", "l3") else "l1")
    return LEVEL_SCOPE.get(lvl)


def media_kind(path):
    ext = os.path.splitext(path)[1].lower()
    for k, s in MEDIA_EXT.items():
        if ext in s:
            return k
    return None


def deflated(data):
    """Size of the data once zipped (what a lot file weighs)."""
    return len(zlib.compress(data, 6))


def media_manifest(content=CONTENT):
    p = os.path.join(content, "MEDIA-MANIFEST.json")
    return read_json(p) if os.path.isfile(p) else {"format": 1, "assets": []}


def scan(content=CONTENT):
    """Returns a list of file records: {path, bytes, zipped, feature, scope, kind, domain} (feature/scope None = not in a lot)."""
    recs = []

    def add(path, feature, scope, kind, domain, zipped=None, rel=None):
        b = os.path.getsize(path)
        recs.append({"path": rel or os.path.relpath(path, content), "bytes": b, "zipped": zipped if zipped is not None else b,
                     "feature": feature, "scope": scope, "kind": kind, "domain": domain})

    learn = os.path.join(content, "learn")
    if os.path.isdir(learn):
        for d in sorted(os.listdir(learn)):
            pj = os.path.join(learn, d, "pack.json")
            if not os.path.isfile(pj):
                continue
            pack = read_json(pj)
            scope = learn_scope(pack); domain = SUBJECT_DOMAIN.get(pack.get("subject"), pack.get("subject"))
            for root, _, files in os.walk(os.path.join(learn, d)):
                for fn in sorted(files):
                    p = os.path.join(root, fn)
                    if not fn.endswith((".json", ".webp", ".png", ".jpg", ".svg", ".opus", ".ogg", ".mp3", ".mp4", ".webm")):
                        continue
                    k = media_kind(p) or "text"
                    with open(p, "rb") as f:
                        z = deflated(f.read()) if k == "text" or k == "image" and p.endswith(".svg") else os.path.getsize(p)
                    add(p, "learn", scope, k, domain, z)
    dist = os.path.join(content, "quiz", "dist")
    cat = os.path.join(dist, "catalog.json")
    if os.path.isfile(cat):
        entries = {e["file"]: e for e in read_json(cat).get("packs", [])}
        for fn in sorted(os.listdir(dist)):
            p = os.path.join(dist, fn)
            if fn.endswith(".quiz.zip") and fn in entries:
                e = entries[fn]
                add(p, "quiz", quiz_scope(e), "text", e.get("course"))
            else:
                add(p, None, None, "other", None)
    graph = os.path.join(content, "graph")
    if os.path.isdir(graph):
        for root, _, files in os.walk(graph):
            for fn in sorted(files):
                add(os.path.join(root, fn), None, None, "graph", None)
    # media assets of the manifest (they belong to the lot named by the manifest)
    mm = media_manifest(content)
    for a in mm.get("assets", []):
        p = os.path.join(content, a["path"])
        if os.path.isfile(p) and not any(r["path"] == a["path"] for r in recs):
            lot = a.get("lot", "")
            feat, _, sc = lot.partition(":")
            add(p, feat or None, sc or None, a.get("kind", "other"), a.get("domain"))
    return recs
