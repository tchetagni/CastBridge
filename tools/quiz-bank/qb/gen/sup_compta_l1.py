"""L1 Économie : gestion et comptabilité générale, exercices calculés (équation comptable, stocks, amortissements, provisions,
régularisations, soldes intermédiaires, équilibre financier, ratios, seuil de rentabilité, coûts, budgets, facture, rapprochement
bancaire, TVA). Toutes les données (montants, pourcentages, taux de TVA) viennent de l'énoncé ; aucune règle chiffrée légale n'est
supposée connue. Chaque réponse est recalculée par une seconde méthode (assert) : une assertion qui échoue est un bogue."""
from collections import deque
from fractions import Fraction as F

from ..core import NB, Draft, fcfa, fr, gen

C = "l1-eco"
CAT = "Gestion et comptabilité"
SRC = "Exercice de gestion et comptabilité générale (L1), réponse calculée à partir des données de l'énoncé"
MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]


def pc(x, dec=2):
    return fr(float(x), dec) + NB + "%"


def is_int(x):
    return F(x).denominator == 1


def rd(x, dec=2):
    """Arrondi (demi supérieur) d'une fraction ; None si la valeur est trop proche d'un cas limite."""
    x = F(x)
    s = x * 10 ** dec
    fl = s.numerator // s.denominator
    if abs((s - fl) - F(1, 2)) < F(1, 1000):
        return None
    return F(int(fl + (1 if s - fl > F(1, 2) else 0)), 10 ** dec)


def money(rng, right, extras, step=None):
    """Montants faux : erreurs typiques d'abord, puis variations de repli ; jamais égaux à la bonne réponse."""
    right = int(right)
    out = []
    for x in extras:
        x = F(x)
        if x.denominator == 1 and int(x) != right and int(x) > 0 and int(x) not in out:
            out.append(int(x))
    for k in (F(11, 10), F(9, 10), F(12, 10), F(8, 10), 2, F(1, 2)):
        v = F(right) * k
        if v.denominator == 1 and int(v) != right and int(v) > 0 and int(v) not in out:
            out.append(int(v))
    return [fcfa(x) for x in out]


def vals(rng, right, extras, fmt):
    r = F(right)
    out, seen = [], {fmt(r)}
    for x in extras:
        s = fmt(F(x))
        if s not in seen:
            seen.add(s)
            out.append(s)
    return out


def ff(x, dec=2):
    return fr(float(x), dec)


def tag_pl(x, a, b):
    return f"{a} de {fcfa(abs(x))}" if x > 0 else f"{b} de {fcfa(abs(x))}"


# ------------------------------------------------------------------------------------------------ 1. équation comptable
@gen(C, "cpt-equation", cap=48, cat=CAT, source=SRC)
def equation(rng, d):
    s = 50000
    cp, em, fo = (rng.randrange(a, b, s) for a, b in ((1000000, 9000000), (500000, 6000000), (200000, 3000000)))
    fi = 0
    total = cp + em + fo + fi
    im = rng.randrange(total // 4 // s * s, total * 3 // 5 // s * s, s)
    st = rng.randrange(100000, total // 5 // s * s + s, s)
    cr = rng.randrange(100000, total // 5 // s * s + s, s)
    tr = total - im - st - cr
    if tr <= 0:
        return None
    act = [("les immobilisations", im), ("les stocks", st), ("les créances clients", cr), ("la trésorerie", tr)]
    pas = [("les capitaux propres", cp), ("les emprunts bancaires", em), ("les dettes fournisseurs", fo)]
    if fi:
        pas.append(("les dettes fiscales et sociales", fi))
    assert sum(v for _, v in act) == sum(v for _, v in pas)
    allv = act + pas
    h = rng.randrange(len(allv)) if d >= 2 else rng.choice([len(act) - 1, len(act)])
    name, right = allv[h]
    shown = [x for k, x in enumerate(allv) if k != h]
    side = act if h < len(act) else pas
    other = pas if h < len(act) else act
    # contrôle indépendant : la valeur cachée est l'écart entre les deux côtés
    if h < len(act):
        assert right == sum(v for _, v in pas) - sum(v for k, (_, v) in enumerate(act) if k != h)
    else:
        assert right == sum(v for _, v in act) - sum(v for k, (_, v) in enumerate(pas) if k != h - len(act))
    desc = " ; ".join(f"{n.split(" ", 1)[1]} {fr(v)}" for n, v in shown)
    tot_o = sum(v for _, v in other)
    extras = [sum(v for _, v in shown), tot_o, abs(tot_o - right) + 0, right + shown[0][1], right - shown[1][1], sum(v for _, v in side if _ != name)]
    return Draft(f"Bilan (FCFA) : {desc}. L'actif étant égal au passif, quel est le montant {("des " + name[4:] if name.startswith("les ") else "de " + name)} ?", fcfa(right),
                 money(rng, right, extras), "L'actif total est égal au passif total : le poste manquant est l'écart entre les deux côtés.", src=SRC)


# ------------------------------------------------------------------------------------------------ 2. résultat par les capitaux propres
@gen(C, "cpt-resultat-cp", cap=48, cat=CAT, source=SRC)
def resultat_cp(rng, d):
    s = 50000
    deb = rng.randrange(2000000, 20000000, s)
    ap = rng.choice([0, 0, 500000, 1000000, 2000000]) if d >= 2 else 0
    dv = rng.choice([0, 300000, 500000, 800000, 1000000]) if d >= 3 else 0
    res = rng.randrange(-1500000, 3000000, s)
    if res == 0:
        return None
    fin = deb + ap + res - dv
    assert fin - deb - ap + dv == res
    parts = [f"capitaux propres initiaux {fcfa(deb)}", f"capitaux propres finaux {fcfa(fin)}"]
    if ap:
        parts.append(f"augmentation de capital {fcfa(ap)}")
    if dv:
        parts.append(f"dividendes distribués {fcfa(dv)}")
    if rng.random() < 0.5 or d == 1:
        right = ("Bénéfice" if res > 0 else "Perte") + f" de {fcfa(abs(res))}"
        wr = [("Perte" if res > 0 else "Bénéfice") + f" de {fcfa(abs(res))}"]
        for x in (fin - deb, fin - deb - ap, fin - deb + dv - 0 if dv else fin - deb + 100000, fin - deb + ap + dv):
            if x != res and x != 0:
                wr.append(("Bénéfice" if x > 0 else "Perte") + f" de {fcfa(abs(x))}")
        return Draft("On relève : " + " ; ".join(parts) + ". Sans autre mouvement, quel est le résultat de l'exercice ?",
                     right, wr, "Résultat = capitaux propres finaux − capitaux propres initiaux − apports + distributions.", src=SRC)
    return Draft(f"Capitaux propres initiaux : {fcfa(deb)} ; " + ("bénéfice" if res > 0 else "perte") + f" de l'exercice : {fcfa(abs(res))}"
                 + (f" ; apport en numéraire : {fcfa(ap)}" if ap else "") + (f" ; dividendes distribués : {fcfa(dv)}" if dv else "")
                 + ". Quels sont les capitaux propres finaux, sans autre mouvement ?", fcfa(fin),
                 money(rng, fin, [deb + abs(res) if res < 0 else deb - res, fin + dv * 2, fin - ap * 2 if ap else fin + 150000, deb + res, fin + ap - dv]),
                 "Capitaux propres finaux = initiaux + apports + résultat − dividendes.", src=SRC)


# ------------------------------------------------------------------------------------------------ 3. CMUP
@gen(C, "cpt-cmup", cap=48, cat=CAT, source=SRC)
def cmup(rng, d):
    for _ in range(400):
        q0, q1 = rng.randrange(100, 600, 50), rng.randrange(100, 600, 50)
        p0, p1 = rng.randrange(500, 3000, 50), rng.randrange(500, 3000, 50)
        if p0 == p1:
            continue
        if d <= 3:
            qs = rng.randrange(50, q0 + q1 - 50, 50)
            tot_v, tot_q = q0 * p0 + q1 * p1, q0 + q1
            m = F(tot_v, tot_q)
            if not is_int(m):
                continue
            m = int(m)
            ask = rng.choice(["cmup", "sortie", "stock"])
            # contrôle : valeur des sorties + valeur du stock final = valeur totale
            assert qs * m + (tot_q - qs) * m == tot_v
            fiche = f"stock initial {fr(q0)} unités à {fcfa(p0)} ; entrée de {fr(q1)} unités à {fcfa(p1)} ; sortie de {fr(qs)} unités"
            if ask == "cmup":
                return Draft(f"Fiche de stock d'un article, tenue au CMUP de fin de période : {fiche}. Quel est le CMUP ?", fcfa(m),
                             money(rng, m, [F(p0 + p1, 2) if is_int(F(p0 + p1, 2)) else p0, p0, p1, (q0 * p0 + q1 * p1) // (q0 + q1 + 50), m + 50, m - 50]),
                             f"CMUP = ({fr(q0)}×{fr(p0)} + {fr(q1)}×{fr(p1)}) ÷ ({fr(q0)} + {fr(q1)}) = {fr(m)} FCFA.", src=SRC)
            if ask == "sortie":
                right = qs * m
                return Draft(f"Fiche de stock d'un article, tenue au CMUP de fin de période : {fiche}. Quelle est la valeur des sorties ?", fcfa(right),
                             money(rng, right, [qs * p0, qs * p1, qs * (p0 + p1) // 2, tot_v - right, (qs * p0 + qs * p1)]),
                             f"Valeur des sorties = {fr(qs)} × CMUP ({fr(m)}) = {fr(right)} FCFA.", src=SRC)
            right = (tot_q - qs) * m
            return Draft(f"Fiche de stock d'un article, tenue au CMUP de fin de période : {fiche}. Quelle est la valeur du stock final ?", fcfa(right),
                         money(rng, right, [(tot_q - qs) * p0, (tot_q - qs) * p1, tot_v, qs * m, (tot_q - qs) * (p0 + p1) // 2]),
                         f"Stock final = {fr(tot_q - qs)} unités × CMUP ({fr(m)}) = {fr(right)} FCFA.", src=SRC)
        # CMUP après chaque entrée (cumulé), deux entrées séparées par une sortie
        qs = rng.randrange(50, q0 + q1 - 50, 50)
        q2, p2 = rng.randrange(100, 500, 50), rng.randrange(500, 3000, 50)
        m1 = F(q0 * p0 + q1 * p1, q0 + q1)
        reste = q0 + q1 - qs
        val_reste = m1 * reste
        m2 = (val_reste + q2 * p2) / (reste + q2)
        if not is_int(m2) or not is_int(m1):
            continue
        # contrôle indépendant : valeur totale conservée
        assert (q0 * p0 + q1 * p1 - qs * m1 + q2 * p2) == m2 * (reste + q2)
        m2 = int(m2)
        return Draft(f"Un article est géré au CMUP calculé après chaque entrée. Stock initial : {fr(q0)} unités à {fcfa(p0)} ; entrée de {fr(q1)} unités à {fcfa(p1)} ; "
                     f"sortie de {fr(qs)} unités ; entrée de {fr(q2)} unités à {fcfa(p2)}. Quel est le CMUP après la dernière entrée ?", fcfa(m2),
                     money(rng, m2, [F(q0 * p0 + q1 * p1 + q2 * p2, q0 + q1 + q2), int(m1), (int(m1) + p2) // 2, p2, m2 + 100, m2 - 100]),
                     f"Après la 1re entrée CMUP = {fr(int(m1))} ; stock restant {fr(reste)} unités ; nouveau CMUP = ({fr(int(val_reste))} + {fr(q2 * p2)}) ÷ {fr(reste + q2)} = {fr(m2)} FCFA.", src=SRC)
    return None


# ------------------------------------------------------------------------------------------------ 4. FIFO
@gen(C, "cpt-fifo", cap=48, cat=CAT, source=SRC)
def fifo(rng, d):
    lots = [(rng.randrange(40, 200, 10), rng.randrange(500, 3000, 50)) for _ in range(2 if d <= 3 else 3)]
    if len({p for _, p in lots}) < len(lots):
        return None
    total = sum(q for q, _ in lots)
    qs = rng.randrange(lots[0][0] + 10, total - 10, 10)
    # méthode 1 : file de lots
    dq = deque(lots)
    need, val = qs, 0
    while need:
        q, p = dq.popleft()
        t = min(q, need)
        val += t * p
        need -= t
        if t < q:
            dq.appendleft((q - t, p))
    # méthode 2 : unités expansées
    units = [p for q, p in lots for _ in range(q)]
    assert val == sum(units[:qs])
    stock_val = sum(q * p for q, p in lots) - val
    assert stock_val == sum(units[qs:])
    lifo = sum(list(reversed(units))[:qs])
    cm = F(sum(q * p for q, p in lots), total)
    desc = " ; ".join((f"stock initial {fr(q)} unités à {fcfa(p)}" if k == 0 else f"entrée n° {k} de {fr(q)} unités à {fcfa(p)}") for k, (q, p) in enumerate(lots))
    if rng.random() < 0.5:
        return Draft(f"Un article est géré selon la méthode premier entré, premier sorti (FIFO) : {desc} ; puis sortie de {fr(qs)} unités. Quelle est la valeur de cette sortie ?", fcfa(val),
                     money(rng, val, [lifo, cm * qs, qs * lots[0][1], qs * lots[-1][1], stock_val]),
                     "En FIFO les sorties sont valorisées d'abord au coût des unités les plus anciennes, lot après lot.", src=SRC)
    return Draft(f"Un article est géré selon la méthode premier entré, premier sorti (FIFO) : {desc} ; puis sortie de {fr(qs)} unités. Quelle est la valeur du stock restant ?", fcfa(stock_val),
                 money(rng, stock_val, [sum(q * p for q, p in lots) - lifo, cm * (total - qs), (total - qs) * lots[0][1], (total - qs) * lots[-1][1], val]),
                 "Valeur du stock restant = valeur totale des lots − valeur des sorties, ces dernières étant prises sur les lots les plus anciens.", src=SRC)


# ------------------------------------------------------------------------------------------------ 5. amortissement dégressif
@gen(C, "cpt-amort-degressif", cap=48, cat=CAT, source=SRC)
def amort_degressif(rng, d):
    v = rng.choice([1000000, 2000000, 2400000, 3000000, 4800000, 6000000, 8000000, 12000000, 15000000, 20000000])
    n = rng.choice([4, 5, 6, 8, 10])
    c = rng.choice([F(3, 2), F(2), F(5, 2)])
    k = rng.randint(1, min(n - 1, 3 if d <= 3 else 4))
    t = c / n
    if t >= 1:
        return None
    # méthode 1 : tableau année par année
    vnc, tab = F(v), []
    for _ in range(k):
        a = vnc * t
        tab.append(a)
        vnc -= a
    # méthode 2 : formule fermée
    assert vnc == v * (1 - t) ** k and tab[-1] == v * (1 - t) ** (k - 1) * t
    ask_ann = rng.random() < 0.5
    x = tab[-1] if ask_ann else vnc
    xr = rd(x, 0)
    if xr is None:
        return None
    x = int(xr)
    lin = F(v, n)
    base = (f"Un matériel de {fcfa(v)} est amorti en dégressif sur {n} ans, taux dégressif = taux linéaire × {ff(c, 1)}, "
            f"appliqué à la VNC de début d'année (sans passage au linéaire ; arrondi au franc). ")
    rate_pct = t * 100
    if ask_ann:
        wr = [lin, v * t * k, vnc, tab[-1] * (1 + t), (tab[-2] if k > 1 else lin) ]
        return Draft(base + f"Quelle est la dotation de l'année {k} ?", fcfa(x), money(rng, x, [int(round(float(w))) for w in wr]),
                     f"Taux dégressif = {ff(rate_pct)} % ; dotation de l'année {k} = VNC début × taux = {fr(x)} FCFA.", src=SRC)
    wr = [v - lin * k, v * (1 - t * k) if t * k < 1 else v // 2, v - tab[-1], v * (1 - t) ** (k + 1), v * (1 - t) ** (k - 1) if k > 1 else lin]
    return Draft(base + f"Quelle est la VNC à la fin de l'année {k} ?", fcfa(x), money(rng, x, [int(round(float(w))) for w in wr]),
                 f"Taux dégressif = {ff(rate_pct)} % ; VNC fin d'année {k} = {fr(v)} × (1 − {ff(t, 4)})^{k} = {fr(x)} FCFA.", src=SRC)


# ------------------------------------------------------------------------------------------------ 6. amortissement linéaire avec prorata
@gen(C, "cpt-amort-lineaire", cap=48, cat=CAT, source=SRC)
def amort_lineaire(rng, d):
    v = rng.choice([1200000, 2400000, 3600000, 4800000, 6000000, 9600000, 14400000])
    n = rng.choice([4, 5, 6, 8, 10])
    res = rng.choice([0, 0, 200000, 400000, 600000]) if d >= 3 else 0
    m = rng.randint(1, 11)             # mois d'utilisation la 1re année (acquisition en début de mois)
    base = v - res
    mois_debut = 12 - m + 1            # numéro du mois d'acquisition
    first = F(base, n) * m / 12
    if not is_int(first):
        return None
    # contrôle mensuel
    mensuel = F(base, 12 * n)
    assert mensuel * m == first
    k = rng.choice([1, 2])
    cumul = first if k == 1 else first + F(base, n)
    vnc = v - cumul
    assert vnc == v - mensuel * (m + 12 * (k - 1))
    ent = (f"Un matériel de {fcfa(v)} est mis en service le 1er {MOIS[mois_debut - 1]}, amorti linéairement sur {n} ans au prorata des mois (exercice = année civile). ")
    if res:
        ent += f"Valeur résiduelle : {fcfa(res)}. "
    if rng.random() < 0.5:
        right = int(first)
        return Draft(ent + "Quelle est la dotation de la première année ?", fcfa(right),
                     money(rng, right, [F(base, n), F(v, n) * m / 12 if res else F(base, n) * (m + 1) / 12, F(base, n) * (m - 1) / 12, F(base, n) / 2, F(base, n) * (12 - m) / 12]),
                     f"Annuité pleine = {fr(base)} ÷ {n} ; pour {m} mois : × {m}/12 = {fr(right)} FCFA (base amortissable = valeur − résiduelle).", src=SRC)
    right = int(vnc)
    return Draft(ent + f"Quelle est la VNC à la fin de l'année {k} ?", fcfa(right),
                 money(rng, right, [v - cumul + res if res else v - first - F(base, n) * (k - 1) * 0 - F(base, n), v - cumul - F(base, n), v - F(base, n) * k, base - cumul, v - F(base, n) * (k - 1) * 1 - first * (k == 1) * 0]),
                 f"Amortissements cumulés après l'année {k} : {fr(int(cumul))} FCFA ; VNC = {fr(v)} − {fr(int(cumul))} = {fr(right)} FCFA.", src=SRC)


# ------------------------------------------------------------------------------------------------ 7. cession d'immobilisation
@gen(C, "cpt-cession", cap=48, cat=CAT, source=SRC)
def cession(rng, d):
    v = rng.choice([1000000, 2400000, 3000000, 4500000, 6000000, 9000000, 12000000])
    n = rng.choice([4, 5, 6, 8, 10])
    ans = rng.randint(1, n - 1)
    a = F(v, n)
    if not is_int(a):
        return None
    vnc = v - a * ans
    # contrôle : tableau
    cum = 0
    for _ in range(ans):
        cum += a
    assert vnc == v - cum
    prix = int(vnc) + rng.choice([-1, 1]) * rng.randrange(100000, 1500000, 50000)
    if prix <= 0:
        return None
    pv = prix - vnc
    right = tag_pl(pv, "Plus-value", "Moins-value")
    wr = [tag_pl(-pv, "Plus-value", "Moins-value"), tag_pl(prix - v, "Plus-value", "Moins-value"), f"Plus-value de {fcfa(prix)}",
          tag_pl(prix - (v - a * (ans + 1)), "Plus-value", "Moins-value"), tag_pl(prix - (v - a * (ans - 1)), "Plus-value", "Moins-value")]
    return Draft(f"Un équipement acquis pour {fcfa(v)} est amorti linéairement sur {n} ans. Après {ans} an{'s' if ans > 1 else ''} d'amortissement complet, il est cédé pour {fcfa(prix)}. "
                 "Quel est le résultat de la cession ?", right, [w for w in wr if w != right],
                 f"VNC = {fr(v)} − {ans} × {fr(int(a))} = {fr(int(vnc))} FCFA ; prix de cession − VNC = {fr(int(pv))} FCFA.", src=SRC)


# ------------------------------------------------------------------------------------------------ 8. provision pour créance douteuse
@gen(C, "cpt-provision-client", cap=48, cat=CAT, source=SRC)
def provision_client(rng, d):
    ht = rng.randrange(400000, 8000000, 100000)
    t = rng.choice([5, 10, 12, 15, 18, 20, 25])
    ttc = ht * (100 + t) // 100
    if ttc * 100 != ht * (100 + t):
        return None
    assert F(ttc * 100, 100 + t) == ht
    pct = rng.choice([20, 25, 30, 40, 50, 60, 75, 80])
    prov = ht * pct // 100
    if prov * 100 != ht * pct:
        return None
    ask = rng.choice(["ht", "prov", "dot"]) if d >= 2 else rng.choice(["ht", "prov"])
    ent = f"Un client douteux doit {fcfa(ttc)} TTC (TVA à {t} %). "
    if ask == "ht":
        return Draft(ent + "Quel est le montant hors taxes de la créance ?", fcfa(ht),
                     money(rng, ht, [ttc * (100 - t) // 100, ttc - t * 1000, ttc * t // 100, ttc, ht + ttc - ht * 0 - ttc // 2]),
                     f"HT = TTC ÷ (1 + {t} %) = {fr(ttc)} ÷ {ff(1 + F(t, 100), 2)} = {fr(ht)} FCFA.", src=SRC)
    ent += f"La perte estimée est de {pct} % de la créance HT. "
    if ask == "prov":
        return Draft(ent + "Quel est le montant de la provision à constituer ?", fcfa(prov),
                     money(rng, prov, [ttc * pct // 100, ht - prov, ttc - prov, prov + ht // 10]),
                     f"Une provision se calcule sur le montant hors taxes : {fr(ht)} × {pct} % = {fr(prov)} FCFA.", src=SRC)
    old = rng.choice([0, prov // 2, prov + 200000, prov - 100000])
    if old < 0:
        return None
    dot = prov - old
    if dot == 0:
        return None
    right = tag_pl(dot, "Dotation", "Reprise")
    return Draft(ent + f"Provision déjà constituée : {fcfa(old)}. Quelle opération de régularisation faut-il passer ?", right,
                 [tag_pl(-dot, "Dotation", "Reprise"), f"Dotation de {fcfa(prov)}", tag_pl(ttc * pct // 100 - old, "Dotation", "Reprise"), f"Reprise de {fcfa(old)}" if old else "Reprise de la totalité de la provision"],
                 f"Provision nécessaire {fr(prov)} − provision existante {fr(old)} = {fr(dot)} : " + ("dotation complémentaire." if dot > 0 else "reprise de l'excédent."), src=SRC)


# ------------------------------------------------------------------------------------------------ 9. régularisations de charges
@gen(C, "cpt-regularisation", cap=48, cat=CAT, source=SRC)
def regularisation(rng, d):
    if rng.random() < 0.6:
        N = rng.choice([3, 6, 9, 12] + ([18, 24] if d >= 4 else []))
        m = rng.randint(1, 12)
        pm = rng.randrange(20000, 300000, 10000)
        M = pm * N
        apres = max(0, m - 1 + N - 12)
        if apres == 0:
            return None
        # contrôle mois par mois
        cnt = sum(1 for j in range(N) if (m - 1 + j) // 12 >= 1)
        assert cnt == apres
        kind = rng.choice(["un loyer", "une prime d'assurance", "un abonnement"])
        cca = pm * apres
        ent = f"Le 1er {MOIS[m - 1]}, une entreprise paie {fcfa(M)} pour {kind} couvrant {N} mois à partir de cette date. L'exercice est l'année civile (clôture au 31 décembre). "
        if rng.random() < 0.5:
            return Draft(ent + "Quel montant constitue une charge constatée d'avance ?", fcfa(cca),
                         money(rng, cca, [pm * (N - apres), M, pm * (apres + 1), pm * (apres - 1) if apres > 1 else M // 2, M - pm * (N - apres) * 0 - pm]),
                         f"Mensualité {fr(pm)} FCFA ; {apres} mois relèvent de l'exercice suivant : {fr(cca)} FCFA.", src=SRC)
        ch = M - cca
        return Draft(ent + "Quelle part de ce paiement est une charge de l'exercice en cours ?", fcfa(ch),
                     money(rng, ch, [cca, M, pm * (N - apres + 1), pm * (N - apres - 1) if N - apres > 1 else M // 2]),
                     f"{N - apres} mois sur {N} relèvent de l'exercice : {fr(ch)} FCFA.", src=SRC)
    cap = rng.choice([6000000, 12000000, 18000000, 24000000, 30000000, 3600000])
    t = rng.choice([4, 5, 6, 8, 9, 10, 12])
    x = rng.randint(1, 10)           # intérêts payés le dernier jour du mois x
    nb = 12 - x
    mens = F(cap * t, 100 * 12)
    cour = mens * nb
    if not is_int(cour):
        return None
    assert cour == F(cap * t, 100) * nb / 12
    right = int(cour)
    return Draft(f"Un emprunt de {fcfa(cap)} porte un intérêt annuel simple de {t} % sur ce capital, payé une fois par an le dernier jour de {MOIS[x - 1]}. "
                 "À la clôture du 31 décembre, quel montant d'intérêts doit être comptabilisé en charges à payer ?", fcfa(right),
                 money(rng, right, [mens * (nb + 1), mens * (nb - 1) if nb > 1 else cap * t // 100, mens * x, F(cap * t, 100), mens * 12 - cour if nb != 6 else mens * 5]),
                 f"Intérêts courus sur {nb} mois : {fr(cap)} × {t} % × {nb}/12 = {fr(right)} FCFA.", src=SRC)


# ------------------------------------------------------------------------------------------------ 10. soldes intermédiaires de gestion
@gen(C, "cpt-sig", cap=48, cat=CAT, source=SRC)
def sig(rng, d):
    s = 50000
    vm = rng.randrange(8000000, 40000000, s)
    ach = rng.randrange(3000000, vm * 6 // 10 // s * s, s)
    si, sf = rng.randrange(0, 2000000, s), rng.randrange(0, 2000000, s)
    camv = ach + si - sf
    mc = vm - camv
    if mc <= 0:
        return None
    pv = rng.randrange(5000000, 30000000, s)
    ps = rng.randrange(0, 3000000, s)
    prod = pv + ps
    conso = rng.randrange(2000000, (mc + prod) // 2 // s * s, s)
    va = mc + prod - conso
    sub = rng.choice([0, 0, 500000, 1000000]) if d >= 3 else 0
    imp = rng.randrange(100000, 1000000, s)
    pers = rng.randrange(1000000, max(1100000, va // 2 // s * s), s)
    ebe = va + sub - imp - pers
    # 2e méthode : par différence depuis les produits et charges
    ebe2 = (vm + pv + ps + sub) - (ach + si - sf + conso + imp + pers)
    assert ebe == ebe2
    ask = rng.choice(["mc", "va", "ebe"]) if d >= 2 else rng.choice(["mc", "va"])
    data = [f"ventes {fr(vm)}", f"achats {fr(ach)}", f"stock initial {fr(si)}", f"stock final {fr(sf)}"]
    if ask == "mc":
        return Draft("Marchandises (en FCFA) : " + " ; ".join(data) + ". Quelle est la marge commerciale ?", fcfa(mc),
                     money(rng, mc, [vm - ach, vm - ach - si + sf - 0 if si == sf else vm - ach - si - sf, vm - ach + si + sf, vm - camv + si, vm - ach - sf if sf else vm - ach + 100000]),
                     f"Coût d'achat des marchandises vendues = {fr(ach)} + {fr(si)} − {fr(sf)} = {fr(camv)} ; marge commerciale = {fr(vm)} − {fr(camv)} = {fr(mc)} FCFA.", src=SRC)
    data += [f"production vendue {fr(pv)}", f"stockée {fr(ps)}", f"consommations de tiers {fr(conso)}"]
    if ask == "va":
        return Draft("En FCFA : " + " ; ".join(data) + f" ; marge commerciale {fr(mc)}. Quelle est la valeur ajoutée ?", fcfa(va),
                     money(rng, va, [mc + pv - conso, mc + pv + ps + conso, pv + ps - conso, mc - conso, va + si]),
                     f"VA = marge commerciale + production de l'exercice ({fr(prod)}) − consommations = {fr(va)} FCFA.", src=SRC)
    data = [f"valeur ajoutée {fr(va)}", f"subvention d'exploitation {fr(sub)}", f"impôts et taxes {fr(imp)}", f"charges de personnel {fr(pers)}"]
    return Draft("Données en FCFA : " + " ; ".join(data) + ". Quel est l'excédent brut d'exploitation (EBE) ?", fcfa(ebe),
                 [fcfa(x) for x in (va - imp, va - pers, va + sub - pers, va - imp - pers, va + sub + imp - pers) if x != ebe and x > 0] + [fcfa(abs(ebe) + 200000)],
                 f"EBE = VA + subvention − impôts et taxes − charges de personnel = {fr(ebe)} FCFA.", src=SRC) if ebe > 0 else None


# ------------------------------------------------------------------------------------------------ 11. FRNG, BFR, trésorerie
@gen(C, "cpt-frng-bfr", cap=48, cat=CAT, source=SRC)
def frng_bfr(rng, d):
    s = 100000
    cp, df = (rng.randrange(a, b, s) for a, b in ((4000000, 20000000), (1000000, 8000000)))
    im = rng.randrange(cp + df - 3000000, cp + df + 5000000, s)
    stk, cl = rng.randrange(500000, 6000000, s), rng.randrange(500000, 6000000, s)
    fo = rng.randrange(500000, 5000000, s)
    frng = cp + df - im
    bfr = stk + cl - fo
    tn = frng - bfr
    # répartition de la trésorerie nette en actif et passif
    tp = rng.choice([0, 200000, 500000, 1000000])
    ta = tn + tp
    if ta < 0 or tp < 0:
        return None
    # contrôle : total des ressources = total des emplois
    assert cp + df + fo + tp == im + stk + cl + ta
    ask = rng.choice(["frng", "bfr", "tn"])
    mn = lambda x: fr(float(F(x, 1000000)), 1)
    mm = lambda x: mn(x) + " millions de FCFA"
    ent = (f"Bilan (millions de FCFA) : capitaux propres {mn(cp)} ; dettes financières {mn(df)} ; immobilisations {mn(im)} ; "
           f"stocks {mn(stk)} ; clients {mn(cl)} ; fournisseurs {mn(fo)} ; trésorerie active {mn(ta)} ; concours bancaires {mn(tp)}. ")
    if ask == "frng":
        return Draft(ent + "Quel est le fonds de roulement net global ?", mm(frng),
                     [mm(x) for x in (cp - im, cp + df, cp + df + im, frng + bfr, frng + stk) if x != frng], "FRNG = ressources stables (capitaux propres + dettes financières) − emplois stables (immobilisations).", src=SRC)
    if ask == "bfr":
        return Draft(ent + "Quel est le besoin en fonds de roulement ?", mm(bfr),
                     [mm(x) for x in (stk + cl, stk + cl + fo, stk - fo, bfr + ta, bfr - tp if tp else bfr + 300000) if x != bfr], "BFR = stocks + créances clients − dettes fournisseurs.", src=SRC)
    return Draft(ent + "Quelle est la trésorerie nette ?", mm(tn),
                 [mm(x) for x in (ta, ta + tp, frng + bfr, bfr - frng, frng) if x != tn] + [mm(abs(tn) + 300000)],
                 f"Trésorerie nette = FRNG − BFR = ({mn(frng)}) − ({mn(bfr)}) ; on retrouve aussi trésorerie active − concours bancaires.", src=SRC)


# ------------------------------------------------------------------------------------------------ 12. ratios
@gen(C, "cpt-ratios", cap=48, cat=CAT, source=SRC)
def ratios(rng, d):
    s = 100000
    cp = rng.randrange(3000000, 15000000, s)
    dlt = rng.randrange(1000000, 10000000, s)
    dct = rng.randrange(1000000, 8000000, s)
    dettes = dlt + dct
    tot = cp + dettes
    ac = rng.randrange(dct // s * s + s, dct * 3 // s * s + 2 * s, s)
    rn = rng.randrange(200000, cp // 3 // s * s + s, s)
    ca = rng.randrange(rn * 4 // s * s, rn * 20, s)
    ask = rng.choice(["liq", "auto", "endet", "roe", "marge"])
    if ask == "liq":
        v = rd(F(ac, dct), 2)
        if v is None:
            return None
        assert abs(float(v) - ac / dct) < 0.005 + 1e-9
        return Draft(f"Une entreprise a un actif circulant de {fcfa(ac)} et des dettes à court terme de {fcfa(dct)}. Quel est son ratio de liquidité générale (arrondi à deux décimales) ?", ff(v),
                     vals(rng, v, [F(dct, ac), F(ac - dct, dct), F(ac, dct) * 100, F(ac, dettes)], lambda x: ff(rd(x, 2) or x)), "Liquidité générale = actif circulant ÷ dettes à court terme.", src=SRC)
    if ask == "auto":
        v = rd(F(cp, dettes), 2)
        if v is None:
            return None
        return Draft(f"Les capitaux propres d'une entreprise sont de {fcfa(cp)} et ses dettes totales de {fcfa(dettes)}. Quel est son ratio d'autonomie financière (capitaux propres ÷ dettes), arrondi à deux décimales ?", ff(v),
                     vals(rng, v, [F(dettes, cp), F(cp, tot), F(dlt, cp), F(cp, dlt)], lambda x: ff(rd(x, 2) or x)), "Autonomie financière = capitaux propres ÷ dettes totales.", src=SRC)
    if ask == "endet":
        v = rd(F(dettes * 100, tot), 1)
        if v is None:
            return None
        assert abs(float(v) - (100 - cp * 100 / tot)) < 0.06
        return Draft(f"Le total du passif d'une entreprise est de {fcfa(tot)}, dont {fcfa(cp)} de capitaux propres ; le reste est constitué de dettes. Quel est le taux d'endettement (dettes ÷ total du passif), en pourcentage arrondi à une décimale ?", pc(v, 1),
                     vals(rng, v, [F(cp * 100, tot), F(dettes * 100, cp), F(dlt * 100, tot), F(dct * 100, tot)], lambda x: pc(rd(x, 1) or x, 1)), "Taux d'endettement = dettes ÷ total du passif ; il complète le poids des capitaux propres à 100 %.", src=SRC)
    if ask == "roe":
        v = rd(F(rn * 100, cp), 1)
        if v is None:
            return None
        return Draft(f"Une entreprise réalise un résultat net de {fcfa(rn)} avec des capitaux propres de {fcfa(cp)}. Quelle est sa rentabilité financière (résultat net ÷ capitaux propres), arrondie à une décimale ?", pc(v, 1),
                     vals(rng, v, [F(rn * 100, tot), F(cp * 100, rn) / 100, F(rn * 100, ca), F(rn * 100, dettes)], lambda x: pc(rd(x, 1) or x, 1)), "Rentabilité financière = résultat net ÷ capitaux propres.", src=SRC)
    v = rd(F(rn * 100, ca), 1)
    if v is None:
        return None
    return Draft(f"Une entreprise réalise un chiffre d'affaires de {fcfa(ca)} et un résultat net de {fcfa(rn)}. Quel est son taux de marge nette (résultat net ÷ chiffre d'affaires), arrondi à une décimale ?", pc(v, 1),
                 vals(rng, v, [F(rn * 100, cp), F(ca * 100, rn) / 100, F(rn * 100, ca) * 10, F(rn * 100, ca) / 2], lambda x: pc(rd(x, 1) or x, 1)), "Marge nette = résultat net ÷ chiffre d'affaires.", src=SRC)


# ------------------------------------------------------------------------------------------------ 13. seuil de rentabilité
@gen(C, "cpt-seuil", cap=48, cat=CAT, source=SRC)
def seuil(rng, d):
    p = rng.choice([1000, 1500, 2000, 2500, 4000, 5000, 8000, 10000])
    cv = p * rng.choice([30, 40, 50, 60, 65, 70]) // 100
    mu = p - cv
    sr_q = rng.randrange(40, 400, 10)
    cf = mu * sr_q
    q = sr_q + rng.randrange(20, 300, 10)
    ca = q * p
    sr = sr_q * p
    # contrôles : résultat nul au seuil, résultat positif au-delà
    assert sr_q * p - sr_q * cv - cf == 0
    res = ca - q * cv - cf
    marge_s = ca - sr
    assert res == marge_s * F(mu, p)
    ask = rng.choice(["sr", "ms", "ind", "pm"])
    ent = (f"Une entreprise prévoit de vendre {fr(q)} unités à {fr(p)} FCFA ; coût variable unitaire {fr(cv)} ; charges fixes {fr(cf)} (en FCFA). ")
    if ask == "sr":
        return Draft(ent + "Quel est le seuil de rentabilité en chiffre d'affaires ?", fcfa(sr),
                     money(rng, sr, [cf, sr_q, cf * cv // mu if (cf * cv) % mu == 0 else cf + 1000000, sr + p * 20, ca]), f"Seuil en quantité = {fr(cf)} ÷ {fr(mu)} = {fr(sr_q)} unités ; en chiffre d'affaires : {fr(sr)} FCFA.", src=SRC)
    if ask == "ms":
        return Draft(ent + "Quelle est la marge de sécurité (en chiffre d'affaires) ?", fcfa(marge_s),
                     money(rng, marge_s, [sr, ca, (q - sr_q) * mu, ca - cf, (q - sr_q) * cv]), f"Marge de sécurité = chiffre d'affaires prévu − seuil = {fr(ca)} − {fr(sr)} = {fr(marge_s)} FCFA.", src=SRC)
    if ask == "ind":
        v = rd(F(marge_s * 100, ca), 1)
        if v is None:
            return None
        return Draft(ent + "Quel est l'indice de sécurité (marge de sécurité ÷ chiffre d'affaires prévu), en pourcentage arrondi à une décimale ?", pc(v, 1),
                     vals(rng, v, [F(sr * 100, ca), F(marge_s * 100, sr), F(mu * 100, p), F(res * 100, ca) + 5], lambda x: pc(rd(x, 1) or x, 1)), "Indice de sécurité = (CA − seuil) ÷ CA.", src=SRC)
    pm = F(sr * 12, ca)
    if not is_int(pm):
        pmr = rd(pm, 1)
        if pmr is None:
            return None
        txt, wr = ff(pmr, 1), vals(rng, pmr, [12 - pm, pm * 2, F(ca, sr), pm + 1, pm - 1], lambda x: ff(rd(x, 1) or x, 1))
        right = txt
    else:
        right, wr = str(int(pm)), [str(int(pm) + k) for k in (1, -1, 2, -2, 3) if int(pm) + k > 0]
    return Draft(ent + "Le chiffre d'affaires est uniforme sur 12 mois. Au bout de combien de mois (arrondi à une décimale) le seuil est-il atteint ?", right + " mois", [w + " mois" for w in wr],
                 f"Point mort = seuil ÷ CA prévu × 12 = {fr(sr)} ÷ {fr(ca)} × 12 mois.", src=SRC)


# ------------------------------------------------------------------------------------------------ 14. coût de revient
@gen(C, "cpt-cout-revient", cap=48, cat=CAT, source=SRC)
def cout_revient(rng, d):
    qa = rng.randrange(100, 1000, 50)
    pu = rng.randrange(200, 2000, 50)
    fa = rng.randrange(10000, 200000, 10000)
    ca = qa * pu + fa                                    # coût d'achat des matières
    mod = rng.randrange(100000, 1000000, 50000)
    fp = rng.randrange(50000, 500000, 50000)
    cprod = ca + mod + fp
    fd = rng.randrange(50000, 400000, 50000)
    cr = cprod + fd
    nb = rng.choice([50, 100, 125, 200, 250, 500])
    if cr % nb or cprod % nb:
        return None
    # 2e méthode : somme de tous les éléments
    assert cr == qa * pu + fa + mod + fp + fd
    pv = cr // nb + rng.choice([-40, 60, 100, 150, 200, 300])
    ask = rng.choice(["achat", "prod", "revient", "res"]) if d >= 2 else rng.choice(["achat", "prod"])
    ent = (f"Achat de {fr(qa)} kg de matière à {fr(pu)} FCFA le kilo (frais d'achat : {fr(fa)}) pour fabriquer {fr(nb)} unités "
           f"(main-d'œuvre {fr(mod)} ; production {fr(fp)} ; distribution {fr(fd)}, en FCFA). ")
    if ask == "achat":
        return Draft(ent + "Quel est le coût d'achat de la matière ?", fcfa(ca), money(rng, ca, [qa * pu, ca + fd, fa, cprod, qa * pu - fa]), "Coût d'achat = prix d'achat + frais d'achat.", src=SRC)
    if ask == "prod":
        return Draft(ent + "Quel est le coût de production de l'ensemble des unités ?", fcfa(cprod), money(rng, cprod, [ca + mod, ca + fp + mod + fd, mod + fp, cr, qa * pu + mod + fp]), "Coût de production = coût d'achat de la matière consommée + main-d'œuvre directe + frais de production.", src=SRC)
    if ask == "revient":
        u = cr // nb
        return Draft(ent + "Quel est le coût de revient unitaire ?", fcfa(u), money(rng, u, [cprod // nb, cr // (nb + 50), ca // nb, (cr + fa) // nb if (cr + fa) % nb == 0 else u + 20, u + 100, u - 100]),
                     f"Coût de revient = coût de production + distribution = {fr(cr)} FCFA, soit {fr(u)} FCFA par unité.", src=SRC)
    u = cr // nb
    r = pv - u
    right = tag_pl(r, "Bénéfice", "Perte") + " par unité"
    wr = [tag_pl(-r, "Bénéfice", "Perte") + " par unité", tag_pl(pv - cprod // nb, "Bénéfice", "Perte") + " par unité", tag_pl(pv - ca // nb, "Bénéfice", "Perte") + " par unité", tag_pl(pv, "Bénéfice", "Perte") + " par unité"]
    return Draft(ent + f"Chaque unité est vendue {fcfa(pv)}. Quel est le résultat analytique unitaire ?", right, [w for w in wr if w != right],
                 f"Résultat unitaire = prix de vente − coût de revient = {fr(pv)} − {fr(u)} = {fr(r)} FCFA.", src=SRC) if r != 0 else None


# ------------------------------------------------------------------------------------------------ 15. budgets
@gen(C, "cpt-budget", cap=48, cat=CAT, source=SRC)
def budget(rng, d):
    kind = rng.choice(["enc", "appro", "tres"])
    if kind == "enc":
        v = [rng.randrange(2000000, 12000000, 500000) for _ in range(3)]
        pc_ = rng.choice([20, 25, 40, 50, 60, 75, 80])
        k = rng.choice([2, 3])
        cpt = [x * pc_ // 100 for x in v]
        diff = [x - c for x, c in zip(v, cpt)]
        if any(x * pc_ % 100 for x in v):
            return None
        enc = cpt[k - 1] + diff[k - 2]
        # 2e méthode : simulation mois par mois
        flux = [0, 0, 0, 0]
        for i, x in enumerate(v):
            flux[i] += x * pc_ // 100
            flux[i + 1] += x - x * pc_ // 100
        assert flux[k - 1] == enc
        names = ["premier", "deuxième", "troisième"]
        return Draft(f"Les ventes prévues sont de {fcfa(v[0])} le mois 1, {fcfa(v[1])} le mois 2 et {fcfa(v[2])} le mois 3. {pc_} % de chaque vente est encaissé le mois de la vente et le solde un mois plus tard. "
                     f"Quel est l'encaissement du mois {k} ?", fcfa(enc), money(rng, enc, [v[k - 1], cpt[k - 1], diff[k - 2], v[k - 1] + v[k - 2], cpt[k - 1] + cpt[k - 2], enc + 500000]),
                     f"Encaissement = {pc_} % des ventes du mois {k} + {100 - pc_} % de celles du mois {k - 1} = {fr(enc)} FCFA.", src=SRC)
    if kind == "appro":
        vq = rng.randrange(500, 3000, 100)
        si, sf = rng.randrange(0, 600, 50), rng.randrange(0, 600, 50)
        if si == sf:
            return None
        pu = rng.randrange(500, 5000, 50)
        a = vq + sf - si
        assert si + a - vq == sf
        if a <= 0:
            return None
        if rng.random() < 0.5:
            return Draft(f"Pour le mois à venir, une entreprise prévoit de vendre {fr(vq)} unités d'un produit. Son stock initial est de {fr(si)} unités et elle souhaite un stock final de {fr(sf)} unités. Quelle quantité doit-elle acheter ?", fr(a),
                         [fr(x) for x in (vq + si - sf, vq - si - sf, vq + si + sf, vq, a + 100) if x != a and x > 0], "Achats = ventes + stock final souhaité − stock initial.", src=SRC)
        return Draft(f"Pour le mois à venir, une entreprise prévoit de vendre {fr(vq)} unités d'un produit. Son stock initial est de {fr(si)} unités et elle souhaite un stock final de {fr(sf)} unités. "
                     f"Chaque unité achetée coûte {fcfa(pu)}. Quel est le budget des achats ?", fcfa(a * pu), money(rng, a * pu, [(vq + si - sf) * pu, vq * pu, (vq + si + sf) * pu, (vq - si - sf) * pu]), f"Quantité à acheter {fr(a)} unités × {fr(pu)} FCFA.", src=SRC)
    s0 = rng.randrange(500000, 3000000, 100000)
    enc = rng.randrange(1000000, 8000000, 100000)
    dec = rng.randrange(1000000, 8000000, 100000)
    if enc == dec:
        return None
    sf = s0 + enc - dec
    assert sf - s0 == enc - dec
    right = fcfa(sf) if sf >= 0 else "−" + fcfa(-sf)
    wr = [x for x in (s0 + enc + dec, s0 - enc + dec, enc - dec, s0 + dec - enc + 0, s0 + enc - dec + 500000) if x != sf]
    return Draft(f"Le solde de trésorerie au début d'un mois est de {fcfa(s0)}. Les encaissements prévus sont de {fcfa(enc)} et les décaissements prévus de {fcfa(dec)}. Quel est le solde de trésorerie prévu en fin de mois ?",
                 right, [fcfa(x) if x >= 0 else "−" + fcfa(-x) for x in wr], "Solde final = solde initial + encaissements − décaissements.", src=SRC)


# ------------------------------------------------------------------------------------------------ 16. remises, escompte, frais accessoires
@gen(C, "cpt-facture", cap=48, cat=CAT, source=SRC)
def facture(rng, d):
    brut = rng.randrange(500000, 6000000, 100000)
    r1, r2 = rng.choice([2, 5, 10, 20]), rng.choice([2, 5, 10])
    r3 = rng.choice([0, 0, 2, 5]) if d >= 3 else 0
    esc = rng.choice([1, 2, 3, 5])
    tr = rng.randrange(20000, 200000, 10000)
    a1 = F(brut) * (100 - r1) / 100
    a2 = a1 * (100 - r2) / 100
    nc = a2 * (100 - r3) / 100
    # 2e méthode : produit des coefficients
    assert nc == brut * F(100 - r1, 100) * F(100 - r2, 100) * F(100 - r3, 100)
    if not is_int(nc) or not is_int(nc * esc / 100):
        return None
    nc = int(nc)
    ef = nc - nc * esc // 100
    cout = nc + tr
    ent = (f"Marchandises au prix brut de {fcfa(brut)} : rabais de {r1} %, puis remise de {r2} %"
           + (f", puis ristourne de {r3} %" if r3 else "") + f". Escompte de {esc} % sur le net commercial ; transport acheteur : {fcfa(tr)}. ")
    ask = rng.choice(["nc", "nf", "co"])
    simple = brut * (100 - r1 - r2 - r3) // 100
    if ask == "nc":
        return Draft(ent + "Quel est le net commercial ?", fcfa(nc), money(rng, nc, [simple, brut * (100 - r1) // 100, ef, nc + tr, brut - brut * (r1 + r2 + r3) // 100 + 100000]),
                     "Les réductions commerciales se déduisent successivement : on multiplie par (1 − taux) à chaque étape.", src=SRC)
    if ask == "nf":
        return Draft(ent + "Quel est le net financier (hors transport) ?", fcfa(ef), money(rng, ef, [nc, simple - simple * esc // 100, nc - brut * esc // 100, ef + tr, nc + tr - nc * esc // 100]),
                     f"Net commercial {fr(nc)} − escompte {esc} % ({fr(nc * esc // 100)}) = {fr(ef)} FCFA.", src=SRC)
    return Draft(ent + "Quel est le coût d'achat (frais accessoires inclus, escompte exclu) ?", fcfa(cout),
                 money(rng, cout, [ef + tr, nc, brut + tr, simple + tr, nc - tr]), f"Coût d'achat = net commercial {fr(nc)} + transport {fr(tr)} = {fr(cout)} FCFA.", src=SRC)


# ------------------------------------------------------------------------------------------------ 17. rapprochement bancaire
@gen(C, "cpt-rapprochement", cap=48, cat=CAT, source=SRC)
def rapprochement(rng, d):
    s = 10000
    T = rng.randrange(500000, 5000000, s)                         # solde réel
    rem = rng.randrange(50000, 800000, s)                         # remises non encore créditées par la banque
    chq = rng.randrange(50000, 800000, s)                         # chèques émis non encore débités
    frais = rng.randrange(5000, 60000, 5000)                      # frais bancaires non comptabilisés
    vir = rng.choice([0, 0, 150000, 300000]) if d >= 3 else 0     # virement reçu non comptabilisé
    releve = T - rem + chq
    compta = T + vir - frais
    # deux côtés cohérents : relevé + remises − chèques = comptabilité + virement − frais
    assert releve + rem - chq == compta - vir + frais
    left = [f"solde du relevé {fr(releve)}", f"remises non créditées {fr(rem)}", f"chèques non débités {fr(chq)}"]
    right_ = [f"solde comptable {fr(compta)}", f"frais bancaires non passés {fr(frais)}"]
    if vir:
        right_.append(f"virement reçu non passé {fr(vir)}")
    if rng.random() < 0.6:
        return Draft("Rapprochement (FCFA) : " + " ; ".join(left + right_) + ". Quel est le solde réel après rapprochement ?", fcfa(T),
                     money(rng, T, [releve, compta, releve - rem + chq, releve + rem + chq, compta - frais, releve - rem - chq]),
                     "Côté banque : relevé + remises non créditées − chèques non débités ; côté comptabilité : solde − frais non passés + virements reçus non passés ; les deux donnent le même solde réel.", src=SRC)
    return Draft("Rapprochement (FCFA) : " + " ; ".join(left + [right_[0]] + right_[2:]) + ". Quel est le montant des frais bancaires non passés en comptabilité ?", fcfa(frais),
              money(rng, frais, [frais * 10, frais + 10000, frais * 2, abs(compta - releve), abs(rem - chq)]),
              "Les deux côtés doivent aboutir au même solde réel : l'écart restant est expliqué par les frais non comptabilisés.", src=SRC)


# ------------------------------------------------------------------------------------------------ 18. TVA à partir d'un taux donné
@gen(C, "cpt-tva-taux", cap=48, cat=CAT, source=SRC)
def tva_taux(rng, d):
    t = rng.choice([5, 8, 10, 12, 15, 18, 20, 25])
    vht = rng.randrange(1000000, 12000000, 500000)
    aht = rng.randrange(500000, 8000000, 500000)
    iht = rng.choice([0, 0, 1000000, 2000000]) if d >= 3 else 0
    col = vht * t // 100
    ded = (aht + iht) * t // 100
    if col * 100 != vht * t or ded * 100 != (aht + iht) * t:
        return None
    due = col - ded
    # 2e méthode : TVA sur la différence des bases
    assert due * 100 == (vht - aht - iht) * t
    ent = f"Taux de TVA : {t} %. Un mois, une entreprise vend pour {fcfa(vht)} HT et achète {fcfa(aht)} HT de marchandises" + (f" et {fcfa(iht)} HT d'immobilisations" if iht else "") + ". "
    ask = rng.choice(["col", "ded", "due"])
    if ask == "col":
        return Draft(ent + "Quel est le montant de la TVA collectée ?", fcfa(col), money(rng, col, [ded, vht * (100 + t) // 100, col - ded, aht * t // 100 if not iht else vht * t // 50, vht // (100 + t) * t]), f"TVA collectée = {fr(vht)} × {t} % = {fr(col)} FCFA.", src=SRC)
    if ask == "ded":
        return Draft(ent + "Quel est le montant de la TVA déductible ?", fcfa(ded), money(rng, ded, [col, aht * t // 100 if iht else (aht + 1000000) * t // 100, (aht + iht) * (100 + t) // 100, ded + 50000, col - ded]),
                     f"La TVA sur les achats" + (" et les immobilisations" if iht else "") + f" est déductible : {fr(aht + iht)} × {t} % = {fr(ded)} FCFA.", src=SRC)
    if due == 0:
        return None
    right = tag_pl(due, "TVA à décaisser", "Crédit de TVA")
    return Draft(ent + "Quel est le solde de TVA de la période ?", right, [tag_pl(-due, "TVA à décaisser", "Crédit de TVA"), f"TVA à décaisser de {fcfa(col)}", f"TVA à décaisser de {fcfa(col + ded)}", f"Crédit de TVA de {fcfa(ded)}"],
                 f"TVA collectée {fr(col)} − TVA déductible {fr(ded)} = {fr(due)} FCFA.", src=SRC)
