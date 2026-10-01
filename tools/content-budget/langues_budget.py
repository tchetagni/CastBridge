#!/usr/bin/env python3
"""Budget de l'enveloppe « Langues » (docs/LANGUES.md § 6). Même arithmétique que castbridge.core.langues.LangBudget (poids : content/langues/budget.json).
  tools/content-budget/langues_budget.py --plan [--md]      tableau chiffré (langue x niveau) ; --md = tableau Markdown
  tools/content-budget/langues_budget.py --check <dossier>  mesure les lots castbridge-lot-langues[-media]-<scope>-v<n>.lot du dossier ; code 1 si un plafond est dépassé
Le reste de la base (3 Go) et la TV (10 Mo) restent vérifiés par `gradle :core:checkStarterBudget` ; cet outil ne compte QUE les langues."""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
B = json.load(open(os.path.join(HERE, "..", "..", "content", "langues", "budget.json"), encoding="utf-8"))
LEVELS = B["levels"]
LANGS = list(B["languageWeightsPercent"])
NAMES = {"zh": "Chinois", "ja": "Japonais", "en": "Anglais", "de": "Allemand", "fr": "Français", "it": "Italien", "es": "Espagnol"}

def env_kb(): return B["envelopeMb"] * 1024
def lang_kb(l): return env_kb() * B["languageWeightsPercent"][l] // 100
def cell_kb(l, lv): return lang_kb(l) * B["levelWeightsPercent"][B["profileOf"][l]][lv] // 100
def text_kb(l): return B["textKbPerLanguageLevel"][B["profileOf"][l]]

def problems():
    e = []
    s = sum(B["languageWeightsPercent"].values()) + B["transversalPercent"] + B["reservePercent"]
    if s != 100: e.append(f"langues + transversal + réserve = {s} %")
    for p, m in B["levelWeightsPercent"].items():
        if sum(m.values()) != 100: e.append(f"profil {p} : niveaux = {sum(m.values())} %")
    if sum(B["mediaSplitPercent"].values()) != 100: e.append("répartition média != 100 %")
    return e

def mo(kb): return f"{kb / 1024:,.0f}".replace(",", " ")

def plan(md):
    rows = [["Langue", "Part"] + LEVELS + ["Total Mo"]]
    for l in LANGS:
        rows.append([NAMES[l], f"{B['languageWeightsPercent'][l]} %"] + [mo(cell_kb(l, lv)) for lv in LEVELS] + [mo(lang_kb(l))])
    rows.append(["Transversal", f"{B['transversalPercent']} %"] + [""] * len(LEVELS) + [mo(env_kb() * B['transversalPercent'] // 100)])
    rows.append(["Réserve", f"{B['reservePercent']} %"] + [""] * len(LEVELS) + [mo(env_kb() * B['reservePercent'] // 100)])
    rows.append(["**Total**", "100 %"] + [""] * len(LEVELS) + [mo(env_kb())])
    if md:
        print("| " + " | ".join(rows[0]) + " |"); print("|" + "---|" * len(rows[0]))
        for r in rows[1:]: print("| " + " | ".join(r) + " |")
    else:
        for r in rows: print("  ".join(c.rjust(9) if i else c.ljust(12) for i, c in enumerate(r)))
    cells = [(l, lv) for l in LANGS for lv in LEVELS]
    media_lots = sum(-(-(cell_kb(l, lv) - text_kb(l)) * 1024 // B["mediaLotMaxBytes"]) for l, lv in cells)
    print(f"\nTexte TV estimé : {sum(text_kb(l) for l, lv in cells) // 1024} Mo en tout ; lots média ≤ {B['mediaLotMaxBytes'] >> 20} Mo : au moins {media_lots} lots.")

def check(d):
    err = problems(); per = {}; total = 0
    for f in sorted(os.listdir(d)):
        m = re.fullmatch(r"castbridge-lot-(langues|langues-media)-([a-z0-9-]+)-v\d+\.lot", f)
        if not m: continue
        size = os.path.getsize(os.path.join(d, f)); media = m.group(1) == "langues-media"
        cap = B["mediaLotMaxBytes"] if media else B["textLotMaxBytes"]
        if size > cap: err.append(f"{f} : {size} octets > plafond {cap}")
        per[m.group(2)[:2]] = per.get(m.group(2)[:2], 0) + size; total += size
    for l, b in per.items():
        if l in B["languageWeightsPercent"] and b > lang_kb(l) * 1024: err.append(f"{l} : {b >> 20} Mo > part de {lang_kb(l) >> 10} Mo")
    if total > env_kb() * 1024: err.append(f"total langues {total >> 20} Mo > enveloppe {B['envelopeMb']} Mo")
    print(f"langues : {total / 1048576:.1f} Mo sur {B['envelopeMb']} Mo (séparés des {B['restOfBaseMb']} Mo du reste de la base)")
    for e in err: print("ERREUR", e)
    return 1 if err else 0

if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["--plan"]: plan("--md" in a); sys.exit(1 if problems() else 0)
    if a[:1] == ["--check"] and len(a) == 2: sys.exit(check(a[1]))
    print(__doc__); sys.exit(2)
