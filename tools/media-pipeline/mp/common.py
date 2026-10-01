"""Briques communes : JSON canonique, empreintes, lecture/écriture déterministes, détection de secrets. Python 3.9+, bibliothèque standard seulement."""
import hashlib
import json
import re
from pathlib import Path

STATUT = "bêta : non validé"
KINDS = ("audio", "image", "video")


def canonical(obj):
    """JSON canonique (clés triées, UTF-8, sans espaces) : mêmes données = mêmes octets."""
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, obj):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")


def read_jsonl(path):
    return [json.loads(l) for l in Path(path).read_text(encoding="utf-8").splitlines() if l.strip()]


def write_jsonl(path, rows):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text("".join(canonical(r) + "\n" for r in rows), encoding="utf-8")


# --- secrets : on ne recopie JAMAIS la valeur trouvée, seulement le chemin et le type générique --------------------------------------
SECRET_PATTERNS = [
    ("clé d'API (forme AIza…)", re.compile(r"AIza[0-9A-Za-z_\-]{30,}")),
    ("clé privée PEM", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")),
    ("jeton GitHub", re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[0-9A-Za-z_]{20,}")),
    ("compte de service (JSON)", re.compile(r'"type"\s*:\s*"service_account"')),
    ("clé privée dans un JSON", re.compile(r'"private_key"\s*:\s*"-----BEGIN')),
    ("en-tête d'autorisation", re.compile(r"(?i)authorization\s*[:=]\s*bearer\s+[A-Za-z0-9._\-]{16,}")),
    ("affectation de secret", re.compile(r"(?i)\b(?:api[_-]?key|secret|password|passwd|token)\b\s*[:=]\s*[\"']?[A-Za-z0-9/+_\-]{16,}")),
]
SECRET_FILENAMES = re.compile(r"(^|/)(\.env(\..*)?|.*\.pem|.*\.key|service-account.*\.json|google-credentials.*\.json)$")


def scan_secrets(root, skip_dirs=(".git",)):
    """[(chemin relatif, type générique)] — jamais la valeur."""
    found = []
    root = Path(root)
    for p in sorted(root.rglob("*")):
        if not p.is_file() or any(part in skip_dirs for part in p.relative_to(root).parts):
            continue
        rel = p.relative_to(root).as_posix()
        if SECRET_FILENAMES.search(rel):
            found.append((rel, "fichier de secrets potentiel (nom)"))
            continue
        if p.stat().st_size > 2_000_000:
            continue
        try:
            text = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for kind, rx in SECRET_PATTERNS:
            if rx.search(text):
                found.append((rel, kind))
                break
    return found
