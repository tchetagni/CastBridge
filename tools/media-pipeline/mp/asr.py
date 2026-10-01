"""Interface de la vérification par reconnaissance vocale (ASR) — SANS implémenter l'ASR (docs/MEDIA-PIPELINE.md § 7).

Contrat : l'exécutant qui détient les accès (Agy) transcrit chaque piste et écrit `asr-results.jsonl`, une ligne par piste :
  {"id": "...", "fingerprint": "...", "engine": "...", "transcript": "...", "checkedOn": "AAAA-MM-JJ"}
Ce module ne fait QUE la comparaison locale, déterministe : écart avec le texte source = piste rejetée par défaut. Aucune correction de la source ni de la transcription.
Absence de résultat = « contrôle ASR non vérifiable dans cet environnement » (bloquant, jamais une acceptation).
"""
import re
import unicodedata

NOT_VERIFIABLE = "contrôle ASR non vérifiable dans cet environnement"
_PUNCT = re.compile(r"[\W_]+", re.UNICODE)


def normalize(text, lang):
    """Normalisation de COMPARAISON seulement (casse, ponctuation, espaces) : ne change ni la source ni la transcription conservées."""
    t = unicodedata.normalize("NFKC", text or "").casefold()
    t = _PUNCT.sub("" if lang in ("zh", "ja") else " ", t)
    return " ".join(t.split()) if lang not in ("zh", "ja") else t


def source_text(request):
    pl = request["payload"]
    if pl.get("segments"):
        return "\n".join(s["text"] for s in pl["segments"])
    return pl.get("text") or ""


def compare(request, result):
    """('accepte'|'rejete'|'non_verifiable', motif)."""
    if result is None:
        return "non_verifiable", NOT_VERIFIABLE
    if result.get("fingerprint") not in (None, request["fingerprint"]):
        return "rejete", "résultat ASR d'une autre demande (empreinte différente)"
    a, b = normalize(source_text(request), request["lang"]), normalize(result.get("transcript", ""), request["lang"])
    if a == b:
        return "accepte", None
    return "rejete", "écart ASR avec le texte source (source %d signes, transcription %d signes)" % (len(a), len(b))
