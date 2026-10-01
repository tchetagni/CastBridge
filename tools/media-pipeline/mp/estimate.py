"""Estimation avant production, plafonds, plan de lot, reprise (docs/MEDIA-PIPELINE.md § 3).

AUCUN prix en dur : les tarifs viennent d'un fichier `pricing.json` rempli par le propriétaire (vide par défaut). Un tarif absent donne un coût INCONNU, jamais zéro ;
un plafond en monnaie sans tarif connu REFUSE la production (on ne dépense pas ce qu'on ne sait pas chiffrer).
"""
from pathlib import Path

from .common import read_json, write_json

EMPTY_PRICING = {
    "format": 1,
    "comment": "À remplir par le propriétaire d'après la grille Google EN VIGUEUR (date et référence de la grille ci-dessous). Laisser null = tarif inconnu.",
    "currency": None, "gridDate": None, "gridReference": None,
    "audio": {"perMillionChars": None},
    "image": {"perImage": None},
    "video": {"perSecond": None},
}

CAP_KEYS = ("maxCost", "maxChars", "maxImages", "maxVideoSeconds", "maxRequests")


class PricingError(Exception):
    pass


def load_pricing(path):
    if path is None or not Path(path).is_file():
        return dict(EMPTY_PRICING)
    p = read_json(path)
    for k in ("audio", "image", "video"):
        p.setdefault(k, {})
    return p


def measure(req):
    """Unités consommées par une demande : caractères (audio), images, secondes de vidéo (la durée MAXIMALE demandée : estimation par excès)."""
    if req["kind"] == "audio":
        pl = req["payload"]
        text = pl.get("text") or ""
        return {"chars": len(text), "images": 0, "videoSeconds": 0}
    if req["kind"] == "image":
        return {"chars": 0, "images": 1, "videoSeconds": 0}
    return {"chars": 0, "images": 0, "videoSeconds": req["constraints"]["maxDurationS"]}


def cost(units, pricing):
    """Coût estimé, ou None si un tarif nécessaire est inconnu (jamais 0 par défaut)."""
    total = 0.0
    for unit, key, price in ((units["chars"], "audio", pricing["audio"].get("perMillionChars")), (units["images"], "image", pricing["image"].get("perImage")),
                             (units["videoSeconds"], "video", pricing["video"].get("perSecond"))):
        if unit == 0:
            continue
        if price is None:
            return None
        total += unit / 1_000_000 * price if key == "audio" else unit * price
    return total


def summarize(requests, pricing):
    tot = {"chars": 0, "images": 0, "videoSeconds": 0, "requests": 0, "blocked": 0}
    unknown = False
    cost_total = 0.0
    for r in requests:
        if r.get("blocked"):
            tot["blocked"] += 1
            continue
        u = measure(r)
        for k in ("chars", "images", "videoSeconds"):
            tot[k] += u[k]
        tot["requests"] += 1
        c = cost(u, pricing)
        if c is None:
            unknown = True
        else:
            cost_total += c
    tot["cost"] = None if unknown else round(cost_total, 6)
    tot["currency"] = pricing.get("currency")
    tot["costNote"] = "coût inconnu : tarif non renseigné dans pricing.json" if unknown else "estimation d'après pricing.json (grille du %s)" % pricing.get("gridDate")
    return tot


class State:
    """Journal de reprise : empreintes déjà produites et dépense cumulée ; fichier JSON écrit à chaque enregistrement (reprise sûre après interruption)."""

    def __init__(self, path):
        self.path = Path(path)
        self.data = read_json(self.path) if self.path.is_file() else {"format": 1, "done": {}, "spent": {"cost": 0.0, "chars": 0, "images": 0, "videoSeconds": 0, "requests": 0}}

    def is_done(self, fingerprint):
        return fingerprint in self.data["done"]

    def record(self, request, pricing=None, actual_cost=None):
        """Marque une demande produite. Même empreinte deux fois : sans effet (idempotent), jamais comptée deux fois.
        Coût retenu : le coût réel s'il est donné, sinon l'estimation d'après [pricing], sinon 0 (inconnu : voir 'costKnown')."""
        fp = request["fingerprint"]
        if fp in self.data["done"]:
            return False
        u = measure(request)
        est = actual_cost if actual_cost is not None else (cost(u, pricing) if pricing is not None else None)
        self.data["done"][fp] = {"id": request["id"], "cost": est}
        s = self.data["spent"]
        for k in ("chars", "images", "videoSeconds"):
            s[k] += u[k]
        s["requests"] += 1
        s["cost"] = round(s["cost"] + (est or 0.0), 6)
        write_json(self.path, self.data)
        return True


def plan(requests, pricing, caps, state=None):
    """Prochaine tranche de demandes, dans l'ordre de priorité, sans dépasser les plafonds. Retourne (retenues, reportees, arret).

    Plafonds (tous optionnels) : maxCost (monnaie du pricing), maxChars, maxImages, maxVideoSeconds, maxRequests. La dépense déjà faite (state) compte.
    Arrêt « budget » si un plafond est atteint : on ne dépasse jamais, la demande qui ne rentre plus est reportée (pas tronquée).
    """
    for k in caps:
        if k not in CAP_KEYS:
            raise PricingError("plafond inconnu : %s" % k)
    spent = {"cost": 0.0, "chars": 0, "images": 0, "videoSeconds": 0, "requests": 0}
    if state is not None:
        spent = dict(state.data["spent"])
    if caps.get("maxCost") is not None and any(pricing[k].get(f) is None for k, f in (("audio", "perMillionChars"), ("image", "perImage"), ("video", "perSecond"))
                                               if any(r["kind"] == k and not r.get("blocked") for r in requests)):
        raise PricingError("plafond de dépense demandé mais tarif inconnu pour un type de média à produire : renseignez pricing.json (aucune dépense sans tarif)")
    kept, later, stop = [], [], None
    for r in requests:
        if r.get("blocked"):
            later.append((r, "bloquée : " + "; ".join(r["blocked"])))
            continue
        if state is not None and state.is_done(r["fingerprint"]):
            continue
        u = measure(r)
        c = cost(u, pricing) or 0.0
        trial = {"cost": spent["cost"] + c, "chars": spent["chars"] + u["chars"], "images": spent["images"] + u["images"],
                 "videoSeconds": spent["videoSeconds"] + u["videoSeconds"], "requests": spent["requests"] + 1}
        hit = next((k for k, cap in (("maxCost", caps.get("maxCost")),) if cap is not None and trial["cost"] > cap + 1e-12), None) \
            or next((k for k, field in (("maxChars", "chars"), ("maxImages", "images"), ("maxVideoSeconds", "videoSeconds"), ("maxRequests", "requests"))
                     if caps.get(k) is not None and trial[field] > caps[k]), None)
        if hit:
            stop = hit
            later.append((r, "plafond %s atteint" % hit))
            continue
        spent = trial
        kept.append(r)
    return kept, later, stop
