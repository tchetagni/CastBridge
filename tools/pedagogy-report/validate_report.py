#!/usr/bin/env python3
"""Validates a pedagogy QA report (docs/PEDAGOGY-RUBRIC.md § 4): one JSON report PER LOT, written by a QA agent or a teacher.

  validate_report.py report.json [more.json ...]       exit 1 on any error
  validate_report.py --decision report.json            prints the decision the thresholds give (pass | revise | reject)

The report states its decision; the validator recomputes it from the scores and the issues and refuses a report that
claims `pass` below the thresholds (a QA agent cannot talk a weak lot through).
"""
import json, re, sys

FORMAT = 1
CRITERIA = ["clarity", "progression", "workedExamples", "misconceptions", "culturalRelevance", "languageLevel", "cognitiveLoad",
            "accessibility", "assessmentAlignment", "levelFidelity"]
MIN_EACH = 2.0        # no criterion below 2 (0-4 scale)
MIN_OVERALL = 3.0     # mean of the criteria
MIN_ACCESS = 3.0      # accessibility is a hard gate (children, dyslexia, colour-blind, low vision, TV from 3 m)
MIN_FIDELITY_EXC = 3.0    # N2-N4 lots: the content must really reach the claimed standard
MIN_CHALLENGE_EXC = 3.0   # ... and a top learner must find it challenging
LOT = re.compile(r"^(learn|quiz):[a-z0-9][a-z0-9-]{0,31}$")
SEVERITY = ("blocker", "major", "minor")
STANDARDS = ("programme", "cm-excellence", "world-excellence", "bridge")
VERDICTS = ("meets", "partial", "below")
LEVELS = ("N0", "N1", "N2", "N3", "N4")
ACCESS_CHECKS = ("colourBlindSafe", "dyslexiaFriendly", "altTextEverywhere", "captionsAndTranscripts", "reducedMotionFallback", "noFlashing", "tvReadable3m")


def decision(rep):
    """pass | revise | reject computed from the scores (the rules of docs/PEDAGOGY-RUBRIC.md § 3)."""
    sc = rep["scores"]
    vals = [sc[c]["score"] for c in CRITERIA]
    issues = [i for c in CRITERIA for i in sc[c].get("issues", [])]
    if any(i.get("severity") == "blocker" for i in issues) or min(vals) < 1.0:
        return "reject"
    exc = any(l in ("N2", "N3", "N4") for l in rep.get("targetLevels", []))
    ok = min(vals) >= MIN_EACH and sum(vals) / len(vals) >= MIN_OVERALL and sc["accessibility"]["score"] >= MIN_ACCESS
    if exc:
        fid = rep.get("levelFidelity", {})
        ok = ok and sc["levelFidelity"]["score"] >= MIN_FIDELITY_EXC and fid.get("verdict") == "meets" and fid.get("challengeForTopLearner", 0) >= MIN_CHALLENGE_EXC
    return "pass" if ok else "revise"


def validate(rep):
    e = []
    if not isinstance(rep, dict):
        return ["le rapport doit être un objet JSON"]
    if rep.get("format") != FORMAT:
        e.append("format %r ≠ %d" % (rep.get("format"), FORMAT))
    if not LOT.match(str(rep.get("lot", ""))):
        e.append("lot « %s » invalide (feature:scope)" % rep.get("lot"))
    if not isinstance(rep.get("lotVersion"), int) or rep["lotVersion"] < 1:
        e.append("lotVersion entier ≥ 1 attendu")
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", str(rep.get("reviewedAt", ""))):
        e.append("reviewedAt (AAAA-MM-JJ) manquant")
    rv = rep.get("reviewer") or {}
    if rv.get("kind") not in ("agent", "human") or not rv.get("name"):
        e.append("reviewer.kind (agent|human) et reviewer.name obligatoires")
    tl = rep.get("targetLevels")
    if not isinstance(tl, list) or not tl or any(l not in LEVELS for l in tl):
        e.append("targetLevels: liste non vide de N0..N4")
    if rep.get("language") not in ("fr", "en"):
        e.append("language: fr ou en")
    sc = rep.get("scores")
    if not isinstance(sc, dict):
        return e + ["scores manquants"]
    for c in CRITERIA:
        s = sc.get(c)
        if not isinstance(s, dict) or not isinstance(s.get("score"), (int, float)) or not 0 <= s["score"] <= 4:
            e.append("scores.%s.score: nombre 0..4 obligatoire" % c); continue
        if not s.get("evidence"):
            e.append("scores.%s.evidence: preuve (élément cité) obligatoire" % c)
        if s["score"] <= 2 and not s.get("issues"):
            e.append("scores.%s: une note ≤ 2 exige au moins un problème détaillé" % c)
        for i in s.get("issues", []):
            if i.get("severity") not in SEVERITY or not i.get("ref") or not i.get("note"):
                e.append("scores.%s.issues: severity (%s), ref et note obligatoires" % (c, "|".join(SEVERITY)))
    acc = rep.get("accessibilityChecks")
    if not isinstance(acc, dict) or any(not isinstance(acc.get(k), bool) for k in ACCESS_CHECKS):
        e.append("accessibilityChecks: booléens obligatoires pour " + ", ".join(ACCESS_CHECKS))
    elif not all(acc.values()) and isinstance(sc.get("accessibility"), dict) and sc["accessibility"].get("score", 0) >= MIN_ACCESS:
        e.append("accessibility ≥ 3 incompatible avec une vérification d'accessibilité en échec")
    lf = rep.get("levelFidelity")
    if not isinstance(lf, dict) or lf.get("claimed") not in LEVELS or lf.get("standard") not in STANDARDS or lf.get("verdict") not in VERDICTS \
            or not isinstance(lf.get("challengeForTopLearner"), (int, float)) or not isinstance(lf.get("itemsChecked"), int):
        e.append("levelFidelity: claimed, standard, verdict, challengeForTopLearner (0..4), itemsChecked obligatoires")
    else:
        if lf["claimed"] in ("N2", "N3", "N4") and lf["itemsChecked"] < 5:
            e.append("levelFidelity: au moins 5 items vérifiés pour un lot N2-N4")
        want = {"N0": "programme", "N1": "programme", "N2": "cm-excellence", "N3": "bridge", "N4": "world-excellence"}[lf["claimed"]]
        if lf["standard"] != want:
            e.append("levelFidelity: le niveau %s se mesure au standard « %s », pas « %s »" % (lf["claimed"], want, lf["standard"]))
        if lf["claimed"] not in tl if isinstance(tl, list) else False:
            e.append("levelFidelity.claimed doit figurer dans targetLevels")
    hv = rep.get("humanValidation") or {}
    if hv.get("required") is not True or hv.get("dueAfterBetaMonths") != 3 or hv.get("status") not in ("pending", "done"):
        e.append("humanValidation: required=true, dueAfterBetaMonths=3, status pending|done (validation humaine 3 mois après la bêta)")
    if rep.get("decision") not in ("pass", "revise", "reject"):
        e.append("decision: pass | revise | reject")
    if not e:
        want = decision(rep)
        if rep["decision"] == "pass" and want != "pass":
            e.append("decision « pass » refusée : les seuils donnent « %s »" % want)
        elif rep["decision"] != want and want == "reject":
            e.append("decision « %s » refusée : un point bloquant impose « reject »" % rep["decision"])
    return e


def main(argv):
    show = "--decision" in argv
    files = [a for a in argv if not a.startswith("--")]
    if not files:
        print(__doc__); return 2
    bad = 0
    for f in files:
        rep = json.load(open(f, encoding="utf-8"))
        errs = validate(rep)
        if show and not errs:
            print("%s: %s" % (f, decision(rep)))
        for x in errs:
            print("%s: ERREUR: %s" % (f, x), file=sys.stderr)
        bad += bool(errs)
    if not show:
        print("%d rapport(s) valide(s)." % (len(files) - bad) if not bad else "%d rapport(s) invalide(s)." % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
