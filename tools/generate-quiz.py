#!/usr/bin/env python3
"""Génère une banque de questions de mathématiques (calculables) au format du serveur CastBridge.

Usage : python3 tools/generate-quiz.py [--count N] [--seed S] [--out FILE]
Sans --count, vise ~30 Mo (≈ 8 000 questions). Déterministe (seed). Import : POST /api/v1/admin/quiz/import.
"""
import argparse, json, random
from datetime import datetime, timezone

NOW = datetime.now(timezone.utc).isoformat()
FIELDS = "mathématiques"
REGION = "AF"


def fmt(n):
    return f"{n:,}".replace(",", " ")


def choices_for(rng, correct, spread=20, unit=""):
    """4 choix (entiers proches), le bon à une position aléatoire. Renvoie (liste, index)."""
    vals = {correct}
    guard = 0
    while len(vals) < 4 and guard < 200:
        guard += 1
        v = correct + rng.randint(-spread, spread)
        if v >= 0 and v != correct and v not in vals:
            vals.add(v)
    cs = list(vals)
    rng.shuffle(cs)
    return [f"{v}{unit}" for v in cs], cs.index(correct)


def make(rng, track, level, category, difficulty, question, correct, explanation, unit=""):
    choices, answer = choices_for(rng, correct, unit=unit)
    return {
        "lang": "fr", "track": track, "level": level, "field": FIELDS, "region": REGION,
        "category": category, "difficulty": difficulty, "question": question,
        "choices": choices, "answer": answer, "explanation": explanation,
        "source": f"Généré automatiquement ({category})", "reviewStatus": "reviewed",
        "status": "approved", "review": False, "updatedAt": NOW, "version": 1,
    }


# Chaque générateur renvoie (track, level, catégorie, difficulté, question, réponse, explication[, unité]).
GENERATORS = []


def reg(f):
    GENERATORS.append(f)
    return f


@reg
def addition(rng):
    a, b = rng.randint(10, 999), rng.randint(10, 999)
    c = a + b
    return ("primary", "CE2", "Addition", 1, f"Calcule : {fmt(a)} + {fmt(b)} = ?", c, f"{fmt(a)} + {fmt(b)} = {fmt(c)}")


@reg
def soustraction(rng):
    a = rng.randint(50, 999); b = rng.randint(10, a)
    c = a - b
    return ("primary", "CE2", "Soustraction", 1, f"Calcule : {fmt(a)} − {fmt(b)} = ?", c, f"{fmt(a)} − {fmt(b)} = {fmt(c)}")


@reg
def multiplication(rng):
    a, b = rng.randint(2, 99), rng.randint(2, 12)
    c = a * b
    return ("primary", "CM1", "Multiplication", 1, f"Calcule : {fmt(a)} × {fmt(b)} = ?", c, f"{fmt(a)} × {fmt(b)} = {fmt(c)}")


@reg
def division(rng):
    b = rng.randint(2, 12); c = rng.randint(2, 99); a = b * c
    return ("primary", "CM2", "Division", 2, f"Calcule : {fmt(a)} ÷ {fmt(b)} = ?", c, f"{fmt(a)} ÷ {fmt(b)} = {fmt(c)}")


@reg
def pourcentage(rng):
    p = rng.randint(5, 95); n = rng.randint(2, 50) * 10
    c = p * n // 100
    return ("secondary", "5e", "Pourcentages", 2, f"Combien font {p} % de {fmt(n)} ?", c, f"{p} % de {fmt(n)} = {fmt(n)} × {p}/100 = {fmt(c)}")


@reg
def equation(rng):
    a = rng.randint(2, 9); b = rng.randint(1, 40); x = rng.randint(1, 20)
    c = a * x + b
    return ("secondary", "4e", "Équations", 3, f"Résous : {a}x + {b} = {c}. Que vaut x ?", x, f"{a}x + {b} = {c}  ⇒  {a}x = {c - b}  ⇒  x = {x}")


@reg
def perimetre(rng):
    L, l = rng.randint(3, 30), rng.randint(2, 30)
    c = 2 * (L + l)
    return ("primary", "CM2", "Géométrie", 2, f"Un rectangle mesure {fmt(L)} cm de long et {fmt(l)} cm de large. Quel est son périmètre ?", c, f"Périmètre = 2 × (L + l) = 2 × ({fmt(L)} + {fmt(l)}) = {fmt(c)} cm", " cm")


@reg
def aire_rectangle(rng):
    L, l = rng.randint(3, 30), rng.randint(2, 30)
    c = L * l
    return ("primary", "CM2", "Géométrie", 2, f"Un rectangle mesure {fmt(L)} m par {fmt(l)} m. Quelle est son aire ?", c, f"Aire = L × l = {fmt(L)} × {fmt(l)} = {fmt(c)} m²", " m²")


@reg
def aire_triangle(rng):
    b = rng.randint(4, 40); h = rng.randint(4, 40)
    if (b * h) % 2: b += 1
    c = b * h // 2
    return ("secondary", "5e", "Géométrie", 2, f"Un triangle a une base de {fmt(b)} cm et une hauteur de {fmt(h)} cm. Quelle est son aire ?", c, f"Aire = (base × hauteur) ÷ 2 = ({fmt(b)} × {fmt(h)}) ÷ 2 = {fmt(c)} cm²", " cm²")


@reg
def pythagore(rng):
    a = rng.randint(3, 20); b = rng.randint(4, 20)
    c2 = a * a + b * b
    c = int(c2 ** 0.5)
    if c * c != c2:
        return None
    return ("secondary", "3e", "Théorème de Pythagore", 3, f"Un triangle rectangle a des côtés de l'angle droit de {fmt(a)} et {fmt(b)}. Quelle est l'hypoténuse ?", c, f"c² = {fmt(a)}² + {fmt(b)}² = {fmt(c2)}  ⇒  c = √{fmt(c2)} = {fmt(c)}")


@reg
def moyenne(rng):
    vals = [rng.randint(1, 20) for _ in range(5)]
    if sum(vals) % 5:
        return None
    c = sum(vals) // 5
    return ("secondary", "4e", "Statistiques", 2, f"Quelle est la moyenne de {', '.join(fmt(v) for v in vals)} ?", c, f"Moyenne = ({'+'.join(fmt(v) for v in vals)}) ÷ 5 = {fmt(c)}")


@reg
def fraction(rng):
    k = rng.randint(2, 9); b = rng.randint(2, 12); a = k * b
    return ("secondary", "5e", "Fractions", 2, f"Simplifie la fraction {a}/{b}.", k, f"{a}/{b} = ({fmt(k)} × {fmt(b)})/{fmt(b)} = {fmt(k)}")


@reg
def suite(rng):
    u1 = rng.randint(1, 20); r = rng.randint(1, 10)
    terms = [u1 + i * r for i in range(4)]
    c = u1 + 4 * r
    return ("secondary", "3e", "Suites", 3, f"Suite : {', '.join(fmt(t) for t in terms)}. Quel est le terme suivant ?", c, f"Raison = {fmt(r)} ; terme suivant = {fmt(terms[-1])} + {fmt(r)} = {fmt(c)}")


@reg
def puissance(rng):
    a = rng.randint(2, 12); n = rng.randint(2, 3)
    c = a ** n
    expr = " × ".join([str(a)] * n)
    return ("secondary", "4e", "Puissances", 2, f"Calcule : {a} puissance {n} (c'est-à-dire {expr}).", c, f"{a}^{n} = {fmt(c)}")


@reg
def racine(rng):
    a = rng.randint(2, 15); c = a * a
    return ("secondary", "3e", "Racines carrées", 3, f"Calcule : √{fmt(c)}.", a, f"√{fmt(c)} = {fmt(a)} car {fmt(a)}² = {fmt(c)}")


@reg
def diviseur_commun(rng):
    a = rng.randint(2, 12); b = rng.randint(2, 12); c = rng.randint(2, 20)
    return ("secondary", "4e", "Arithmétique", 3, f"Quel est le plus grand diviseur commun de {fmt(a * c)} et {fmt(b * c)} ?", c, f"PGCD({fmt(a * c)}, {fmt(b * c)}) = {fmt(c)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=0, help="nombre de questions (0 = viser ~30 Mo)")
    ap.add_argument("--seed", type=int, default=12345)
    ap.add_argument("--out", default="questions-generated.json")
    a = ap.parse_args()
    rng = random.Random(a.seed)
    target = a.count
    if target == 0:
        target = (30 * 1024 * 1024) // 3200  # ~3,2 Ko par question
    seen = set()
    questions = []
    guard = 0
    while len(questions) < target and guard < target * 40:
        guard += 1
        q = random.choice(GENERATORS)(rng)
        if q is None:
            continue
        question = q[4]
        if question in seen:
            continue
        seen.add(question)
        questions.append(make(rng, *q))
    # identifiants stables
    for i, q in enumerate(questions):
        q["id"] = f"gen-{q['track']}-{i:05d}"
        q["uuid"] = q["id"]
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump({"version": 2, "questions": questions}, f, ensure_ascii=False)
    size = len(json.dumps({"version": 2, "questions": questions}, ensure_ascii=False).encode("utf-8"))
    print(f"{len(questions)} questions -> {a.out} ({size/1024/1024:.1f} Mo)")


if __name__ == "__main__":
    main()
