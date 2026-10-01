"""Droit constitutionnel et institutions (L1 droit), seconde vague : cas pratiques, qualifications, petits calculs de scrutin.
Situations fictives, principes généraux seulement (aucun article, délai ni seuil légal). Tout est `review`."""
import random
from fractions import Fraction
from .sup_kit import *  # noqa: F401,F403
from ..core import fr

C = "l1-droit"
source("sup-cas-l1b", "Droit constitutionnel : cas pratiques et qualifications", "cours de L1 droit",
       "Comparer avec un manuel général de droit constitutionnel et d'institutions politiques ; pour les scrutins, les données sont fournies dans chaque énoncé "
       "(aucun seuil légal n'est supposé connu). Pour le Cameroun, Constitution du 18 janvier 1996 telle que modifiée.")
S = "sup-cas-l1b"
CAT = "Droit constitutionnel (cas)"


def M(tpl, rows, region="WORLD"):
    mcq(C, "cas-l1b-" + tpl, rows, cat=CAT, src=S, region=region)


# ============================================================================================================
# A. Petits calculs de répartition de sièges (données fournies ; vérification par une seconde méthode)
NAMES = ["A", "B", "C", "D"]


def _lists(rng, nl):
    return NAMES[:nl]


def _hamilton(votes, n):
    tot = sum(votes)
    q = Fraction(tot, n)
    base = [int(Fraction(v) // q) for v in votes]
    rest = n - sum(base)
    rems = [Fraction(v) - b * q for v, b in zip(votes, base)]
    order = sorted(range(len(votes)), key=lambda i: -rems[i])
    if rest and rest < len(votes):
        assert rems[order[rest - 1]] != rems[order[rest]]
    res = base[:]
    for i in order[:rest]:
        res[i] += 1
    return res, base, rest


def _hamilton_seq(votes, n):  # 2e méthode : on attribue un siège à la fois au plus fort reste restant
    tot = sum(votes)
    q = Fraction(tot, n)
    res = [int(Fraction(v) // q) for v in votes]
    rem = {i: Fraction(v) - res[i] * q for i, v in enumerate(votes)}
    while sum(res) < n:
        i = max(rem, key=lambda k: rem[k])
        res[i] += 1
        del rem[i]
    return res


def _divisors(votes, n, divs):
    res = [0] * len(votes)
    for _ in range(n):
        best = max(range(len(votes)), key=lambda i: Fraction(votes[i], divs(res[i])))
        res[best] += 1
    return res


def _by_table(votes, n, divs):  # 2e méthode : tableau de tous les quotients, on garde les n plus forts
    cells = sorted(((Fraction(v, divs(k)), i) for i, v in enumerate(votes) for k in range(n)), key=lambda c: -c[0])
    assert cells[n - 1][0] != cells[n][0]
    res = [0] * len(votes)
    for _, i in cells[:n]:
        res[i] += 1
    return res


def _three_wrong(right, cands):
    out = []
    for c in cands:
        if c != right and c not in out and c >= 0:
            out.append(c)
    k = 1
    while len(out) < 3:
        for c in (right + k, right - k):
            if c != right and c not in out and c >= 0 and len(out) < 3:
                out.append(c)
        k += 1
    return out[:3]


def seat_rows():
    rows = {k: [] for k in ("quot", "qsieges", "reste", "hamil", "dhondt", "laguë", "partic", "expr", "abs", "seuil", "disto", "plur")}
    rng = random.Random(20260101)
    # --- quotient électoral simple
    for _ in range(14):
        nl = rng.choice([3, 4])
        n = rng.choice([4, 5, 6, 7, 8, 9, 10])
        q = rng.choice(range(5000, 30001, 500))
        tot = q * n
        sh = [rng.randint(8, 40) for _ in range(nl)]
        votes = [(tot * s // sum(sh)) // 100 * 100 for s in sh]
        votes[-1] = tot - sum(votes[:-1])
        assert sum(votes) == tot and min(votes) > 0 and Fraction(tot, n) == q
        right = q
        wr = _three_wrong(right, [tot // (n + 1), tot // (n - 1), tot // nl, tot * n, tot // (n + 2)])
        txt = "Dans une circonscription à %d sièges, %d listes totalisent %s suffrages exprimés. Quel est le quotient électoral simple (suffrages exprimés divisés par les sièges) ?" % (n, nl, fr(tot))
        rows["quot"].append((txt, fr(right), [fr(x) for x in wr], "%s ÷ %d = %s voix par siège." % (fr(tot), n, fr(q)), 2))
    # --- sièges au quotient pour une liste
    for _ in range(14):
        nl = rng.choice([3, 4])
        n = rng.choice([5, 6, 7, 8, 9])
        q = rng.choice(range(6000, 20001, 1000))
        tot = q * n
        sh = [rng.randint(8, 40) for _ in range(nl)]
        votes = [(tot * s // sum(sh)) // 100 * 100 for s in sh]
        votes[-1] = tot - sum(votes[:-1])
        i = rng.randrange(nl)
        right = votes[i] // q
        assert Fraction(votes[i], q) >= right and right == int(Fraction(votes[i]) / q)
        wr = _three_wrong(right, [right + 1, right - 1 if right else right + 2, right + 2, round(votes[i] / q + 0.5) if votes[i] % q else right + 3])
        lst = ", ".join("%s : %s" % (NAMES[k], fr(v)) for k, v in enumerate(votes))
        txt = "%d sièges sont à pourvoir ; voix des listes — %s. Quotient électoral : %s. Combien de sièges la liste %s obtient-elle au quotient, avant la répartition des restes ?" % (n, lst, fr(q), NAMES[i])
        rows["qsieges"].append((txt, str(right), [str(x) for x in wr], "%s ÷ %s = %s, on ne garde que la partie entière." % (fr(votes[i]), fr(q), fr(votes[i] / q, 2)), 2))
    # --- nombre de sièges restant à répartir
    for _ in range(12):
        nl = rng.choice([3, 4])
        n = rng.choice([6, 7, 8, 9, 10])
        q = rng.choice(range(5000, 20001, 1000))
        tot = q * n
        sh = [rng.randint(8, 40) for _ in range(nl)]
        votes = [(tot * s // sum(sh)) // 100 * 100 for s in sh]
        votes[-1] = tot - sum(votes[:-1])
        res, base, rest = _hamilton(votes, n)
        assert rest == n - sum(v // q for v in votes)
        if rest == 0:
            continue
        lst = ", ".join("%s : %s" % (NAMES[k], fr(v)) for k, v in enumerate(votes))
        txt = "Répartition de %d sièges ; voix — %s ; quotient électoral %s. Après attribution des sièges au quotient, combien de sièges reste-t-il à répartir au plus fort reste ?" % (n, lst, fr(q))
        wr = _three_wrong(rest, [rest + 1, rest - 1, n, rest + 2, sum(base)])
        rows["reste"].append((txt, str(rest), [str(x) for x in wr], "%d sièges - %d attribués au quotient = %d." % (n, sum(base), rest), 3))
    # --- plus fort reste complet
    cnt = 0
    while cnt < 16:
        nl = rng.choice([3, 4])
        n = rng.choice([5, 6, 7, 8, 9, 10])
        votes = [rng.randrange(8000, 60000, 100) for _ in range(nl)]
        try:
            res, base, rest = _hamilton(votes, n)
        except AssertionError:
            continue
        assert res == _hamilton_seq(votes, n) and sum(res) == n
        if rest == 0:
            continue
        i = rng.randrange(nl)
        right = res[i]
        wr = _three_wrong(right, [base[i], right + 1, right - 1 if right > 0 else right + 2, right + 2, base[i] + 1])
        if len(wr) < 3:
            continue
        lst = ", ".join("%s : %s" % (NAMES[k], fr(v)) for k, v in enumerate(votes))
        txt = "Scrutin proportionnel, %d sièges, quotient simple et attribution des restes au plus fort reste. Voix — %s. Combien de sièges obtient la liste %s ?" % (n, lst, NAMES[i])
        rows["hamil"].append((txt, str(right), [str(x) for x in wr], "Au quotient (%s) la liste en a %d ; avec les restes elle atteint %d." % (fr(sum(votes) / n, 1), base[i], right), 3 if cnt < 8 else 4))
        cnt += 1
    # --- d'Hondt
    cnt = 0
    while cnt < 18:
        nl = rng.choice([3, 4])
        n = rng.choice([4, 5, 6, 7, 8, 9])
        votes = sorted([rng.randrange(8000, 80000, 100) for _ in range(nl)], reverse=True)
        try:
            a = _by_table(votes, n, lambda k: k + 1)
        except AssertionError:
            continue
        assert a == _divisors(votes, n, lambda s: s + 1)
        i = rng.randrange(nl)
        right = a[i]
        h = _hamilton(votes, n)[0] if True else None
        wr = _three_wrong(right, [right + 1, right - 1 if right else right + 2, right + 2, right - 2 if right > 1 else right + 3])
        if h[i] != right and h[i] not in wr:
            wr = [h[i]] + wr[:2]
        lst = ", ".join("%s : %s" % (NAMES[k], fr(v)) for k, v in enumerate(votes))
        txt = "%d sièges à pourvoir selon la méthode de la plus forte moyenne (d'Hondt, diviseurs 1, 2, 3…). Voix — %s. Combien de sièges revient à la liste %s ?" % (n, lst, NAMES[i])
        rows["dhondt"].append((txt, str(right), [str(x) for x in wr[:3]], "On classe les quotients V/1, V/2, V/3… et on retient les %d plus forts." % n, 4 if cnt % 2 else 3))
        cnt += 1
    # --- Sainte-Laguë (diviseurs 1, 3, 5…)
    cnt = 0
    while cnt < 12:
        nl = rng.choice([3, 4])
        n = rng.choice([4, 5, 6, 7])
        votes = sorted([rng.randrange(8000, 80000, 100) for _ in range(nl)], reverse=True)
        try:
            a = _by_table(votes, n, lambda k: 2 * k + 1)
        except AssertionError:
            continue
        assert a == _divisors(votes, n, lambda s: 2 * s + 1)
        d = _divisors(votes, n, lambda s: s + 1)
        if a == d:
            continue
        i = [k for k in range(nl) if a[k] != d[k]][0]
        right = a[i]
        wr = _three_wrong(right, [d[i], right + 1, right - 1 if right else right + 2, right + 2])
        lst = ", ".join("%s : %s" % (NAMES[k], fr(v)) for k, v in enumerate(votes))
        txt = "%d sièges, méthode de la plus forte moyenne avec les diviseurs 1, 3, 5, 7… (Sainte-Laguë). Voix — %s. Combien de sièges obtient la liste %s ?" % (n, lst, NAMES[i])
        rows["laguë"].append((txt, str(right), [str(x) for x in wr], "On classe les quotients V/1, V/3, V/5… et on retient les %d plus forts." % n, 4 if cnt % 2 else 5))
        cnt += 1
    # --- participation, suffrages exprimés
    for _ in range(12):
        ins = rng.randrange(80000, 400000, 1000)
        vot = ins * rng.randint(45, 85) // 100 // 10 * 10
        bn = rng.randrange(1000, 9000, 10)
        exp = vot - bn
        assert exp > 0
        txt = "Dans une circonscription : %s inscrits, %s votants, dont %s bulletins blancs ou nuls (on convient qu'ils ne sont pas des suffrages exprimés). Combien y a-t-il de suffrages exprimés ?" % (fr(ins), fr(vot), fr(bn))
        wr = _three_wrong(exp, [vot, ins - bn, vot + bn, ins - vot])
        rows["expr"].append((txt, fr(exp), [fr(x) for x in wr], "Suffrages exprimés = votants − blancs et nuls = %s − %s." % (fr(vot), fr(bn)), 2))
    for _ in range(10):
        ins = rng.choice([20000, 40000, 50000, 80000, 120000, 200000])
        pc = rng.choice([35, 40, 45, 52, 55, 60, 64, 65, 72, 75])
        vot = ins * pc // 100
        assert vot * 100 == ins * pc
        txt = "Dans une commune, %s électeurs sont inscrits et %s ont voté. Quel est le taux de participation ?" % (fr(ins), fr(vot))
        wr = _three_wrong(pc, [100 - pc, pc + 10, pc - 10, pc + 5, pc - 5])
        rows["partic"].append((txt, "%d %%" % pc, ["%d %%" % x for x in wr], "%s ÷ %s = %d %%." % (fr(vot), fr(ins), pc), 2))
    # --- majorité absolue / relative
    for _ in range(16):
        nc = rng.choice([3, 4])
        exp = rng.randrange(40000, 90000, 1000)
        sh = [rng.randint(15, 60) for _ in range(nc)]
        votes = sorted([exp * s // sum(sh) for s in sh], reverse=True)
        votes[0] += exp - sum(votes)
        votes = sorted(votes, reverse=True)
        if votes[0] == votes[1] or 2 * votes[0] == exp:
            continue
        absm = 2 * votes[0] > exp
        right = "Il est élu dès le premier tour" if absm else "Un second tour est nécessaire"
        wr = ["Un second tour est nécessaire" if absm else "Il est élu dès le premier tour",
              "Il est élu, car il a la majorité relative, quel que soit le scrutin",
              "L'élection est annulée, faute de quorum de participation"]
        wr = [w for w in wr if w != right][:3]
        txt = "Scrutin uninominal majoritaire à deux tours : l'élection au premier tour exige la majorité absolue des suffrages exprimés. Le candidat en tête a %s voix sur %s exprimés. Que se passe-t-il ?" % (fr(votes[0]), fr(exp))
        rows["abs"].append((txt, right, wr, "La majorité absolue est plus de la moitié de %s, soit plus de %s voix." % (fr(exp), fr(exp / 2, 1)), 3))
    # --- seuil d'éligibilité
    for _ in range(12):
        nl = 4
        exp = rng.choice([100000, 200000, 250000, 400000])
        pcs = sorted(rng.sample(range(2, 46), nl), reverse=True)
        pcs[0] += 100 - sum(pcs)
        if pcs[0] < 20:
            continue
        seuil = rng.choice([5, 7, 10])
        # éviter les cas limites ambigus
        if any(abs(p - seuil) < 1 for p in pcs):
            continue
        adm = [NAMES[k] for k, p in enumerate(pcs) if p >= seuil]
        if len(adm) in (0, nl):
            continue
        right = ", ".join(adm)
        wr = set()
        for cand in (NAMES[:len(adm) + 1], NAMES[:max(1, len(adm) - 1)], NAMES[1:len(adm) + 1], NAMES):
            s = ", ".join(cand)
            if s != right:
                wr.add(s)
        wr = sorted(wr)[:3]
        if len(wr) < 3:
            continue
        lst = ", ".join("%s : %d %%" % (NAMES[k], p) for k, p in enumerate(pcs))
        txt = "Un seuil de %d %% des suffrages exprimés est exigé pour participer à la répartition des sièges. Résultats — %s. Quelles listes participent à la répartition ?" % (seuil, lst)
        rows["seuil"].append((txt, right, wr, "Seules les listes à %d %% ou plus passent le seuil." % seuil, 2))
    # --- distorsion sièges / voix (scrutin uninominal)
    cnt = 0
    while cnt < 12:
        nc = rng.choice([5, 7])
        pairs_ = []
        for _k in range(nc):
            tot = rng.randrange(8000, 20000, 500)
            a = tot * rng.randint(30, 70) // 100 // 10 * 10
            pairs_.append((a, tot - a))
        sa = sum(1 for a, b in pairs_ if a > b)
        sb = sum(1 for a, b in pairs_ if b > a)
        va = sum(a for a, b in pairs_)
        vb = sum(b for a, b in pairs_)
        if sa == sb or va == vb or any(a == b for a, b in pairs_):
            continue
        if (sa > sb) == (va > vb) and cnt % 2 == 0:
            continue
        lst = " ; ".join("%s–%s" % (fr(a), fr(b)) for a, b in pairs_)
        opts = {(True, True): "A : plus de sièges et plus de voix au total",
                (True, False): "A : plus de sièges, mais moins de voix au total",
                (False, True): "A : moins de sièges, mais plus de voix au total",
                (False, False): "A : moins de sièges et moins de voix au total"}
        key = (sa > sb, va > vb)
        right = opts[key]
        txt = "%d circonscriptions, un siège chacune, scrutin majoritaire à un tour. Voix des partis A et B (A–B) : %s. Que dire de A par rapport à B ?" % (nc, lst)
        rows["disto"].append((txt, right, [v for k, v in opts.items() if k != key], "Sièges A/B : %d/%d ; voix A/B : %s/%s." % (sa, sb, fr(va), fr(vb)), 4 if key[0] == key[1] else 5))
        cnt += 1
    return rows


_r = seat_rows()
M("quot", _r["quot"])
M("qsieges", _r["qsieges"])
M("reste", _r["reste"])
M("hamil", _r["hamil"])
M("hondt", _r["dhondt"])
M("lague", _r["laguë"])
M("expr", _r["expr"])
M("partic", _r["partic"])
M("abs", _r["abs"])
M("seuil", _r["seuil"])
M("disto", _r["disto"])

# ============================================================================================================
# B. Régimes politiques : qualification de situations
M("regimes", [
    ("Dans l'État fictif de Norvia, le gouvernement doit démissionner s'il perd la confiance de l'assemblée, et le chef de l'État peut dissoudre celle-ci. Quel régime est typique de ce mécanisme ?",
     "Le régime parlementaire", ["Le régime présidentiel", "Le régime d'assemblée", "Le régime directorial"],
     "Responsabilité du gouvernement devant l'assemblée et dissolution : les deux armes réciproques du parlementarisme.", 2),
    ("Les ministres d'un pays sont nommés et révoqués librement par un président élu par le peuple ; le Congrès ne peut pas les renverser. De quel régime s'agit-il ?",
     "Le régime présidentiel", ["Le régime parlementaire", "Le régime d'assemblée", "Le régime directorial"],
     "Le gouvernement dépend du seul président et n'est pas responsable devant le parlement.", 2),
    ("Une assemblée nomme les ministres, peut les révoquer à tout moment et leur donne des instructions, sans que l'exécutif puisse la dissoudre. Quel régime est décrit ?",
     "Un régime d'assemblée", ["Un régime présidentiel", "Un régime parlementaire équilibré", "Une monarchie absolue"],
     "L'organe législatif domine l'exécutif, qui lui est subordonné : c'est le gouvernement d'assemblée.", 3),
    ("Un exécutif collégial est élu par le parlement pour une durée fixe ; il ne peut être renversé par lui et ne peut pas le dissoudre. Quel régime ce schéma illustre-t-il ?",
     "Un régime directorial", ["Un régime parlementaire", "Un régime présidentiel", "Un régime d'assemblée"],
     "Exécutif collégial issu du parlement mais non révocable par lui : le modèle directorial, dont la Suisse est l'exemple classique.", 4),
    ("Le chef de l'État est élu au suffrage universel direct et dispose de pouvoirs propres ; un Premier ministre, responsable devant l'assemblée, dirige le gouvernement. Quel régime est décrit ?",
     "Un régime semi-présidentiel", ["Un régime présidentiel strict", "Un régime d'assemblée", "Un régime directorial"],
     "Président élu par le peuple et gouvernement responsable devant le parlement : combinaison propre au semi-présidentialisme.", 3),
    ("Une motion de censure est adoptée contre le gouvernement dans un régime parlementaire. Quelle est la conséquence juridique de principe ?",
     "Le gouvernement doit démissionner", ["Le chef de l'État est destitué", "L'assemblée est dissoute d'office", "La Constitution est suspendue"],
     "La censure est la sanction de la responsabilité politique du gouvernement devant l'assemblée.", 2),
    ("Quel mécanisme permet qu'un acte du chef de l'État, politiquement irresponsable, engage la responsabilité d'un ministre ?",
     "Le contreseing ministériel", ["La promulgation", "Le droit de veto", "La question orale"],
     "Le ministre qui signe l'acte à côté du chef de l'État en assume la responsabilité politique.", 3),
    ("Dans un régime semi-présidentiel, le président et la majorité de l'assemblée appartiennent à des camps opposés. Comment appelle-t-on cette situation ?",
     "La cohabitation", ["La coalition", "L'alternance", "Le fait majoritaire"],
     "Le chef de l'État doit composer avec un gouvernement issu de la majorité adverse.", 3),
    ("Après des élections, aucun parti n'a la majorité ; trois partis s'accordent pour gouverner ensemble. Comment qualifie-t-on ce gouvernement ?",
     "Un gouvernement de coalition", ["Un gouvernement d'exception", "Un gouvernement de fait", "Un gouvernement d'assemblée"],
     "Plusieurs partis s'associent pour former une majorité de gouvernement.", 2),
    ("Dans un régime présidentiel de type américain, le président est en désaccord avec le Congrès. Peut-il le dissoudre ?",
     "Non, il ne dispose pas de ce pouvoir", ["Oui, par simple décret motivé", "Oui, après avis de la Cour suprême", "Oui, mais seulement une des deux chambres"],
     "La séparation est stricte : le président ne peut dissoudre le Congrès, qui ne peut le renverser par la censure.", 3),
    ("Le parlement d'un pays peut renverser le gouvernement, mais le chef de l'État ne peut jamais le dissoudre. Quel élément manque à l'équilibre parlementaire ?",
     "Le pouvoir de dissolution aux mains de l'exécutif", ["Le droit de censure de l'assemblée", "L'élection du chef de l'État", "Le contrôle de constitutionnalité"],
     "Le parlementarisme équilibré repose sur des armes réciproques : censure d'un côté, dissolution de l'autre.", 4),
    ("Le gouvernement est responsable à la fois devant le parlement et devant le chef de l'État, qui peut le révoquer. Quel type de parlementarisme est décrit ?",
     "Le parlementarisme dualiste", ["Le parlementarisme moniste", "Le régime d'assemblée", "Le régime présidentiel"],
     "Double responsabilité du gouvernement : devant l'assemblée et devant le chef de l'État.", 4),
    ("Le gouvernement n'est responsable que devant l'assemblée ; le chef de l'État ne peut pas le révoquer. Quel type de parlementarisme est décrit ?",
     "Le parlementarisme moniste", ["Le parlementarisme dualiste", "Le régime directorial", "Le régime présidentiel"],
     "Une seule responsabilité, devant l'assemblée : parlementarisme moniste.", 4),
    ("La Constitution d'un régime parlementaire encadre strictement les motions de censure pour stabiliser le gouvernement. Comment nomme-t-on cette technique ?",
     "Le parlementarisme rationalisé", ["Le parlementarisme dualiste", "Le gouvernement d'assemblée", "La démocratie directe"],
     "La rationalisation enferme dans des règles écrites les rapports entre parlement et gouvernement.", 3),
    ("Aux États-Unis, comment nomme-t-on la procédure par laquelle le Congrès peut mettre en accusation le président pour faute grave ?",
     "L'impeachment", ["Le veto", "L'obstruction parlementaire (filibuster)", "Le contreseing"],
     "Seule exception à l'absence de responsabilité politique du président devant le Congrès.", 3),
    ("Le président d'un régime présidentiel refuse de signer une loi votée et la renvoie au Congrès. Quel pouvoir exerce-t-il ?",
     "Le droit de veto", ["Le droit de dissolution", "Le droit de grâce", "Le pouvoir de contreseing"],
     "Le veto bloque ou retarde l'entrée en vigueur d'une loi adoptée par le parlement.", 2),
    ("Dans un régime présidentiel strict, un proche collaborateur du président peut-il siéger en même temps au Congrès ?",
     "Non, les fonctions sont en principe incompatibles", ["Oui, c'est le principe de ce régime", "Oui, s'il démissionne du parti", "Oui, mais sans droit de vote"],
     "La séparation stricte des pouvoirs interdit en principe de cumuler fonction exécutive et mandat parlementaire.", 3),
    ("Dans un pays, le roi règne mais ne gouverne pas ; le gouvernement issu de la majorité de l'assemblée conduit la politique. Quel régime est décrit ?",
     "Une monarchie parlementaire", ["Une monarchie absolue", "Un régime d'assemblée", "Un régime présidentiel"],
     "Le monarque est chef de l'État irresponsable ; le pouvoir réel revient au gouvernement responsable devant l'assemblée.", 2),
    ("Dans un régime parlementaire, un ministre est aussi député. Cette situation est-elle contraire à la logique du régime ?",
     "Non, la collaboration des pouvoirs la rend possible", ["Oui, elle viole toute séparation des pouvoirs", "Oui, elle rend la censure impossible", "Oui, elle est interdite dans tout État"],
     "Le parlementarisme repose sur une séparation souple, avec collaboration entre exécutif et législatif.", 4),
    ("Un chef d'État monarque héréditaire détient seul tous les pouvoirs et ne rend compte à aucune assemblée. Comment qualifie-t-on ce régime ?",
     "Une monarchie absolue", ["Une monarchie parlementaire", "Un régime semi-présidentiel", "Une démocratie directe"],
     "L'absence de limite constitutionnelle et de contrôle caractérise l'absolutisme.", 2),
    ("Un seul parti est autorisé par la Constitution, toutes les candidatures passent par lui. Quel principe démocratique est directement écarté ?",
     "Le pluralisme politique", ["Le suffrage universel des adultes", "L'existence d'un chef de l'État", "La publicité des lois"],
     "Le monopartisme interdit la concurrence entre courants d'opinion.", 3),
    ("Dans un régime parlementaire, le chef du gouvernement dépose la question de confiance et l'assemblée la refuse. Quel est l'effet de principe ?",
     "Le gouvernement doit se retirer ou provoquer des élections", ["Le chef de l'État est démis de ses fonctions", "Les lois en cours deviennent caduques", "La Cour suprême prend le pouvoir"],
     "Le refus de confiance engage la responsabilité du gouvernement, qui part ou, selon le système, recourt à la dissolution.", 3),
    ("Un président qui est à la fois chef de l'État et chef du gouvernement, élu pour un mandat fixe et non révocable par les députés, relève de quel schéma ?",
     "Du régime présidentiel", ["Du régime parlementaire moniste", "Du régime d'assemblée", "Du régime directorial"],
     "Exécutif monocéphale, mandat fixe, pas de responsabilité politique devant le parlement.", 2),
])

# ============================================================================================================
# C. Séparation des pouvoirs : cas
M("pouvoirs", [
    ("Un ministre décide seul de sanctionner un citoyen sans procès, en s'appuyant sur un décret. Quel principe, hérité de Montesquieu, est le plus directement mis à mal ?",
     "La séparation entre pouvoir exécutif et pouvoir judiciaire", ["La séparation des Églises et de l'État", "La spécialité des personnes publiques", "Le principe de non-rétroactivité"],
     "Punir relève du juge ; l'exécutif ne doit pas s'arroger la fonction de juger.", 3),
    ("Le Parlement vote une loi qui annule une décision de justice définitive rendue entre deux particuliers. Que lui reproche-t-on en principe ?",
     "D'empiéter sur la fonction juridictionnelle", ["De ne pas respecter l'ordre du jour", "D'exercer le droit de dissolution", "D'usurper le pouvoir réglementaire"],
     "Annuler une décision définitive revient à rejuger : c'est une atteinte à l'indépendance du judiciaire.", 4),
    ("Un chef d'État donne par téléphone des instructions à un juge sur la manière de trancher un dossier précis. Quelle règle fondamentale est violée ?",
     "L'indépendance du pouvoir judiciaire", ["Le principe de la souveraineté nationale", "L'inviolabilité parlementaire", "Le principe de libre administration"],
     "Un juge doit trancher sans ordres de l'exécutif ni du législatif.", 2),
    ("Un député qui dit des propos dans l'hémicycle ne peut en principe être poursuivi pour ceux-ci. Comment nomme-t-on cette protection ?",
     "L'irresponsabilité parlementaire", ["L'inviolabilité parlementaire", "L'immunité diplomatique", "L'inéligibilité"],
     "Elle couvre les opinions et votes émis dans l'exercice du mandat, de façon permanente.", 3),
    ("Un député est soupçonné d'un délit commun sans lien avec ses fonctions ; la procédure exige l'autorisation de sa chambre pour certaines mesures. Quelle protection est en jeu ?",
     "L'inviolabilité parlementaire", ["L'irresponsabilité parlementaire", "L'immunité de juridiction étrangère", "Le secret du vote"],
     "L'inviolabilité protège le parlementaire contre certaines poursuites ou arrestations, de façon temporaire et procédurale.", 4),
    ("Le gouvernement prend par décret une règle dans une matière que la Constitution réserve à la loi. Quel défaut juridique est en cause ?",
     "Une incompétence de l'auteur de l'acte", ["Un vice de forme tenant à la publication", "Une violation du secret du vote", "Un excès du pouvoir de dissolution"],
     "Le domaine de la loi est réservé au législateur : l'exécutif n'a pas compétence pour y intervenir sans habilitation.", 4),
    ("Le parlement autorise le gouvernement à prendre, pour une durée limitée, des mesures relevant normalement de la loi. Comment appelle-t-on ces actes ?",
     "Des ordonnances", ["Des arrêtés de police", "Des lois organiques", "Des décisions de justice"],
     "Les ordonnances sont prises sur habilitation du parlement, qui garde en principe un contrôle de ratification.", 3),
    ("Une commission d'enquête de l'assemblée convoque un ministre pour s'expliquer sur la gestion d'un ministère. De quelle fonction du parlement relève cette action ?",
     "Le contrôle de l'action du gouvernement", ["Le pouvoir constituant", "La fonction juridictionnelle", "L'initiative des lois de finances"],
     "Interpeller, enquêter, questionner : ce sont des moyens de contrôle parlementaire.", 2),
    ("Le Conseil des ministres adopte un projet, l'assemblée le discute et le vote, puis le chef de l'État ordonne son exécution. Quelle étape finale ce dernier accomplit-il ?",
     "La promulgation", ["L'initiative", "L'amendement", "La censure"],
     "La promulgation constate l'adoption de la loi et la rend exécutoire.", 2),
    ("Un tribunal refuse d'appliquer un acte administratif qu'il estime contraire à la loi. Quelle idée de la séparation des pouvoirs cela illustre-t-il ?",
     "Le juge contrôle la légalité des actes de l'administration", ["Le juge peut modifier la loi", "L'administration juge ses propres actes", "Le parlement nomme les juges"],
     "L'exécutif ne se juge pas lui-même : le juge sanctionne l'illégalité.", 3),
    ("Un parlement vote une loi qui retire à une juridiction toute compétence pour examiner la légalité des actes d'un ministre. Que peut-on lui objecter dans un État de droit ?",
     "Elle prive les citoyens d'un recours effectif", ["Elle viole la règle du quorum", "Elle viole le secret des délibérations", "Elle supprime le droit de vote"],
     "Un État de droit suppose que l'action de l'administration puisse être soumise à un contrôle juridictionnel.", 4),
    ("Dans la pensée de Montesquieu, quelle est la meilleure formule pour résumer le but de la séparation des pouvoirs ?",
     "Que le pouvoir arrête le pouvoir", ["Que le peuple gouverne directement", "Que le juge fasse la loi", "Que l'exécutif absorbe les autres"],
     "Séparer et équilibrer pour empêcher l'abus de pouvoir.", 2),
    ("Une loi de finances est votée par le parlement, mais l'exécutif décide seul d'ajouter une dépense non autorisée. Quel principe est ici contourné ?",
     "Le consentement des représentants à la dépense publique", ["Le principe du contradictoire", "Le secret du vote", "L'inamovibilité des juges"],
     "L'autorisation parlementaire de la dépense est un principe fondamental des régimes démocratiques.", 4),
    ("Le président d'une chambre refuse que les députés de l'opposition s'expriment dans le débat sur une loi. Quel droit est le plus directement concerné ?",
     "Le droit de l'opposition à participer au débat", ["Le droit de grâce", "L'immunité du chef de l'État", "L'inamovibilité des magistrats"],
     "Le pluralisme parlementaire suppose que les minorités puissent exprimer leur point de vue.", 3),
    ("Un juge est muté contre son gré dans une région isolée parce qu'il a rendu une décision déplaisante au gouvernement. Quelle garantie statutaire est ignorée ?",
     "L'inamovibilité des magistrats du siège", ["L'irresponsabilité parlementaire", "Le contreseing ministériel", "La libre administration locale"],
     "L'inamovibilité protège le juge de déplacements ou sanctions décidés pour influencer ses décisions.", 3),
    ("Un gouvernement fait voter un texte puis, par un autre texte, remplace des juges qui lui sont défavorables. Quelle valeur de l'État de droit est atteinte ?",
     "L'indépendance de la justice", ["La liberté d'association", "La souveraineté nationale externe", "Le suffrage universel"],
     "L'exécutif et le législatif ne doivent pas manipuler la composition des juridictions pour orienter leurs décisions.", 3),
    ("Un maire, autorité décentralisée, prend un arrêté qui modifie une loi nationale. Quelle règle de la hiérarchie des normes est violée ?",
     "L'arrêté ne peut contredire une norme supérieure", ["La loi doit toujours céder à l'arrêté local", "L'arrêté prime car il est plus récent", "Il n'existe aucune hiérarchie en pratique"],
     "L'acte inférieur doit respecter la loi : en cas de contradiction, il est illégal.", 3),
    ("Le parlement refuse de voter le budget que demande le gouvernement et propose d'en voter un autre. De quelle prérogative use-t-il ?",
     "Du pouvoir d'amendement et du vote de la loi de finances", ["Du pouvoir de promulgation", "Du pouvoir de dissolution", "Du pouvoir de grâce"],
     "Voter, amender ou rejeter les lois de finances est l'un des pouvoirs essentiels du parlement.", 3),
    ("Le chef de l'État annule seul, par décret, un jugement pénal qu'il juge injuste. Que reproche-t-on à cet acte ?",
     "Il empiète sur la fonction de juger", ["Il enfreint le principe de libre administration", "Il viole le secret du vote", "Il dépasse le domaine de la loi organique"],
     "Annuler une décision de justice appartient au juge d'appel ou de cassation, non à l'exécutif ; la grâce est un acte distinct.", 4),
    ("Un parlement vote une loi qui punit rétroactivement une action qui était légale au moment où elle a été commise. Que peut faire le juge constitutionnel ?",
     "La censurer, car la non-rétroactivité de la loi pénale plus sévère s'impose", ["La valider, car la loi est la volonté générale", "La réécrire à sa guise", "Demander au gouvernement de l'abroger"],
     "La loi pénale plus sévère ne rétroagit pas : principe de légalité des délits et des peines.", 4),
])

# ============================================================================================================
# D. Modes de scrutin : qualifications
M("scrutin-q", [
    ("Les habitants de chaque commune élisent des grands électeurs qui, à leur tour, désignent les sénateurs. Quel type de suffrage est décrit ?",
     "Un suffrage indirect", ["Un suffrage direct", "Un vote plural", "Un suffrage censitaire"],
     "Les électeurs ne désignent pas directement les élus : un collège intermédiaire intervient.", 2),
    ("Dans un pays, seuls les hommes payant un certain impôt peuvent voter. Comment qualifie-t-on ce suffrage ?",
     "Un suffrage censitaire", ["Un suffrage capacitaire", "Un suffrage universel", "Un vote obligatoire"],
     "Le droit de vote dépend de la richesse : c'est le suffrage censitaire.", 2),
    ("Seules les personnes ayant un diplôme déterminé peuvent voter. De quel suffrage s'agit-il ?",
     "Un suffrage capacitaire", ["Un suffrage censitaire", "Un suffrage universel", "Un suffrage direct"],
     "Le critère est l'instruction ou la compétence, non la fortune.", 3),
    ("Un électeur dispose de deux voix en raison de son diplôme, alors que les autres n'en ont qu'une. Quel principe est violé ?",
     "L'égalité du suffrage", ["Le secret du suffrage", "L'universalité du suffrage", "La liberté du suffrage"],
     "Le vote plural rompt le principe « une personne, une voix ».", 3),
    ("Un électeur est forcé par un employeur à lui montrer son bulletin avant de le glisser dans l'urne. Quel principe du vote est atteint ?",
     "Le secret du vote", ["L'universalité du suffrage", "Le caractère direct du suffrage", "L'égalité des suffrages"],
     "Le secret protège l'électeur contre les pressions et les représailles.", 2),
    ("Un candidat distribue de l'argent aux électeurs la veille du scrutin pour obtenir leur vote. Quel principe est le plus directement atteint ?",
     "La liberté du vote", ["L'égalité du suffrage", "Le caractère direct du suffrage", "Le secret du vote"],
     "La corruption électorale vicie la liberté de choix de l'électeur.", 3),
    ("Une loi interdit à toutes les femmes de voter. Quel caractère du suffrage universel est supprimé ?",
     "L'universalité", ["Le secret", "Le caractère direct", "Le caractère libre"],
     "Le suffrage universel exclut les restrictions fondées sur le sexe, la fortune ou la capacité.", 2),
    ("Deux candidats sont en lice pour un siège : le premier obtient plus de voix que le second, qui n'en a pas autant. Dans un scrutin à un tour, qui est élu ?",
     "Celui qui a le plus de voix, même sans majorité absolue", ["Le plus âgé des deux", "Aucun, il faut un second tour", "Celui qui a le plus de bulletins blancs"],
     "Le scrutin majoritaire à un tour donne le siège à la majorité relative.", 2),
    ("Dans un scrutin de liste à la proportionnelle, chaque liste obtient des sièges en fonction de ses voix. Quel effet est visé ?",
     "Une représentation proche du poids de chaque courant", ["Une majorité automatique pour le premier", "La disparition des petits partis", "L'élection d'un seul gagnant"],
     "La proportionnelle cherche à refléter la diversité des opinions dans l'assemblée.", 2),
    ("Dans un scrutin uninominal à deux tours, les candidats qualifiés pour le second tour sont ceux dont le score dépasse un certain pourcentage fixé par la loi. Comment appelle-t-on ce pourcentage ?",
     "Un seuil de qualification", ["Un quorum de session", "Un quotient électoral", "Un plafond de dépenses"],
     "Le seuil fixe les conditions pour se maintenir au second tour.", 3),
    ("Une liste obtient moins de voix que le minimum exigé pour avoir des sièges. Quel mécanisme la prive de représentation malgré ses voix ?",
     "Le seuil d'éligibilité", ["La prime majoritaire", "Le panachage", "Le scrutin binominal"],
     "Un seuil écarte les listes qui n'atteignent pas un pourcentage de voix donné.", 3),
    ("Un électeur peut composer son bulletin en choisissant des noms issus de plusieurs listes concurrentes. Comment appelle-t-on cette pratique ?",
     "Le panachage", ["Le vote blanc", "Le vote bloqué", "Le suffrage indirect"],
     "Le panachage permet de composer sa propre liste à partir de plusieurs listes.", 4),
    ("L'électeur doit voter pour une liste entière, sans rayer ni ajouter de noms ni changer leur ordre. Comment appelle-t-on ce type de scrutin de liste ?",
     "Un scrutin de liste bloquée", ["Un scrutin avec panachage", "Un scrutin uninominal", "Un scrutin à vote préférentiel"],
     "La liste est « bloquée » : l'ordre des candidats est fixé par le parti.", 3),
    ("Une liste arrivée en tête reçoit automatiquement une partie supplémentaire de sièges avant la répartition proportionnelle du reste. Quel nom porte ce mécanisme ?",
     "La prime majoritaire", ["Le quorum", "Le quotient", "Le scrutin binominal"],
     "La prime accorde un avantage à la liste gagnante pour dégager une majorité stable.", 4),
    ("Une carte électorale est redessinée pour regrouper les électeurs d'un camp dans quelques circonscriptions et affaiblir ce camp ailleurs. Comment appelle-t-on cette manipulation ?",
     "Le gerrymandering", ["Le panachage", "Le référendum", "Le quorum"],
     "Le découpage orienté des circonscriptions vise à avantager un camp.", 3),
    ("Un électeur a la possibilité de voter par un mandataire. Comment appelle-t-on ce procédé ?",
     "Le vote par procuration", ["Le vote blanc", "Le suffrage indirect", "Le vote préférentiel"],
     "Le mandataire vote à la place de l'électeur empêché, selon les conditions fixées par la loi.", 2),
    ("Une personne dépose dans l'urne un bulletin sans aucun nom. Comment qualifie-t-on ce bulletin ?",
     "Un bulletin blanc", ["Un bulletin censitaire", "Un bulletin indirect", "Un bulletin préférentiel"],
     "Le bulletin blanc exprime l'absence de choix, sans être pour autant une abstention.", 2),
    ("Une personne ne va pas voter le jour du scrutin. Quel terme désigne son comportement ?",
     "L'abstention", ["Le vote blanc", "Le panachage", "Le vote plural"],
     "L'abstention consiste à ne pas participer au scrutin, ce qui diffère du bulletin blanc.", 2),
    ("Une loi électorale réserve aux femmes une proportion de places sur les listes. Quelle expression désigne cette technique ?",
     "Un quota de représentation", ["Un quorum de session", "Un quotient électoral", "Un seuil de qualification"],
     "Les quotas sont des mesures destinées à favoriser l'accès des femmes aux mandats.", 3),
    ("Un électeur est inscrit sur les listes dans une circonscription où il n'habite plus depuis longtemps, et vote deux fois à deux endroits. De quoi s'agit-il ?",
     "D'une fraude électorale", ["D'un suffrage plural légal", "D'un panachage", "D'un référendum d'initiative"],
     "Voter plusieurs fois viole l'égalité du suffrage et constitue en principe une infraction.", 3),
    ("Un parti obtient 35 % des voix et 60 % des sièges, un autre 30 % des voix et 10 % des sièges. Quel effet des modes de scrutin est ici illustré ?",
     "La disproportion entre voix et sièges", ["L'indépendance du juge électoral", "La parité des candidatures", "La liberté de la presse"],
     "Certains scrutins majoritaires amplifient fortement les écarts de voix en sièges.", 3),
])

# ============================================================================================================
# E. Contrôle de constitutionnalité : cas
M("controle", [
    ("Avant la promulgation d'une loi, des parlementaires saisissent la juridiction constitutionnelle pour lui demander de la vérifier. De quel contrôle s'agit-il ?",
     "D'un contrôle a priori par voie d'action", ["D'un contrôle a posteriori par voie d'exception", "D'un contrôle de conventionnalité", "D'un contrôle de légalité"],
     "La loi est examinée avant d'entrer en vigueur, à la demande d'une autorité habilitée.", 2),
    ("Un justiciable poursuivi soutient devant un tribunal que la loi qu'on lui applique viole la Constitution. Comment appelle-t-on ce moyen de défense ?",
     "L'exception d'inconstitutionnalité", ["L'action directe en annulation", "Le veto présidentiel", "Le recours pour excès de pouvoir"],
     "Le moyen est soulevé à l'occasion d'un litige, contre une loi déjà en vigueur.", 3),
    ("Une loi est en vigueur depuis plusieurs années. À l'occasion d'un procès, on soutient qu'elle porte atteinte à un droit constitutionnel. Quel contrôle est ainsi mis en œuvre ?",
     "Un contrôle a posteriori", ["Un contrôle a priori", "Un contrôle de la promulgation", "Un contrôle de l'opportunité"],
     "Le contrôle intervient après l'entrée en vigueur de la loi.", 2),
    ("Dans un système de type américain, un juge ordinaire écarte une loi inconstitutionnelle dans un litige. Que devient formellement la loi ?",
     "Elle n'est pas appliquée dans ce litige mais reste formellement en vigueur", ["Elle est abrogée pour tous", "Elle est annulée avec effet rétroactif général", "Elle est transmise au président pour promulgation"],
     "Le juge ordinaire ne l'annule pas ; l'effet est limité au cas jugé, la solution pesant toutefois sur les juges futurs.", 4),
    ("Une cour constitutionnelle de type européen déclare une disposition législative inconstitutionnelle. Quel est l'effet habituel de sa décision ?",
     "La disposition est annulée à l'égard de tous", ["La disposition est écartée seulement entre les parties", "La disposition est amendée par la cour", "La cour renvoie l'affaire au parlement pour avis"],
     "Le modèle européen donne une portée générale (erga omnes) à l'annulation.", 3),
    ("Un juge refuse d'appliquer une loi parce qu'elle contredit un traité régulièrement ratifié. De quel contrôle parle-t-on ?",
     "D'un contrôle de conventionnalité", ["D'un contrôle de constitutionnalité", "D'un contrôle de légalité de l'acte administratif", "D'un contrôle de l'opportunité"],
     "Il s'agit de comparer la loi à une norme internationale, non à la Constitution.", 3),
    ("Un juge administratif annule un décret parce qu'il contredit une loi. Quel contrôle exerce-t-il ?",
     "Un contrôle de légalité", ["Un contrôle de constitutionnalité de la loi", "Un contrôle de conventionnalité", "Un contrôle politique"],
     "Le décret est un acte réglementaire soumis à la loi : le juge vérifie sa légalité.", 2),
    ("Une cour constitutionnelle déclare une loi conforme, à condition qu'elle soit interprétée de telle manière. Comment nomme-t-on cette technique ?",
     "Une réserve d'interprétation", ["Un veto suspensif", "Une promulgation partielle", "Une exception d'incompétence"],
     "Le juge sauve le texte en fixant le sens compatible avec la Constitution.", 4),
    ("Un amendement sans lien avec l'objet d'un projet de loi est glissé dans le texte pour le faire passer discrètement. Comment appelle-t-on cette disposition ?",
     "Un cavalier législatif", ["Un décret d'application", "Une loi de ratification", "Un veto législatif"],
     "Un cavalier est étranger à l'objet du texte ; les juges constitutionnels le censurent souvent.", 4),
    ("En droit français, un traité contient une clause contraire à la Constitution. Que faut-il en principe avant de pouvoir le ratifier ?",
     "Réviser la Constitution", ["Le faire promulguer par le Premier ministre", "Le faire valider par un décret", "L'appliquer à titre provisoire sans ratification"],
     "Un traité ne peut l'emporter sur la Constitution : on révise celle-ci ou on renonce au traité.", 4),
    ("Un citoyen saisit directement une cour constitutionnelle d'un recours contre une décision de justice qui, selon lui, viole ses droits fondamentaux. Comment nomme-t-on ce recours ?",
     "Un recours constitutionnel individuel", ["Une motion de censure", "Un référendum d'initiative", "Un contrôle de conventionnalité"],
     "Ce type de recours existe notamment en Allemagne devant la juridiction de Karlsruhe.", 4),
    ("Dans un système sans juge constitutionnel, le contrôle de la loi est confié à un organe politique, et non à un juge. Quel type de contrôle est-il ?",
     "Un contrôle politique", ["Un contrôle juridictionnel", "Un contrôle de proximité", "Un contrôle hiérarchique"],
     "Le caractère politique tient à la nature de l'organe, non à l'objet du contrôle.", 3),
    ("Selon la logique de la hiérarchie des normes, pourquoi une loi contraire à la Constitution peut-elle être sanctionnée ?",
     "Parce que la Constitution est supérieure à la loi", ["Parce que la loi est plus ancienne", "Parce que la Constitution est plus courte", "Parce que le juge peut la modifier"],
     "Le contrôle de constitutionnalité garantit la suprématie de la Constitution.", 2),
    ("Pour qu'un contrôle de constitutionnalité soit efficace, quelle condition est particulièrement nécessaire ?",
     "Que l'organe de contrôle soit indépendant du pouvoir qu'il contrôle", ["Que l'organe soit nommé par le seul exécutif", "Que l'organe siège dans la capitale économique", "Que l'organe n'ait pas à motiver ses décisions"],
     "L'indépendance garantit que le contrôle ne dépende pas de l'autorité dont la loi est contrôlée.", 3),
    ("Le juge constitutionnel est saisi d'une loi. Peut-il en principe refuser de la censurer au motif qu'elle serait inopportune politiquement ?",
     "Oui : il juge la conformité, non l'opportunité de la loi", ["Non : il juge toujours l'opportunité", "Non : il remplace le parlement", "Oui : mais seulement si le parti du juge y est opposé"],
     "Le juge vérifie le respect de la Constitution, sans substituer son appréciation politique à celle du législateur.", 4),
    ("Un parlement adopte une loi organique. En droit français, que se passe-t-il avant sa promulgation ?",
     "Elle est obligatoirement soumise au Conseil constitutionnel", ["Elle est transmise au Conseil d'État pour avis seulement", "Elle est soumise au seul vote du Sénat", "Elle est promulguée sans contrôle"],
     "Les lois organiques font l'objet d'un contrôle obligatoire, a priori.", 4),
    ("Un contrôle de constitutionnalité exercé à la demande d'une autorité publique avant la promulgation est-il à la portée du citoyen seul ?",
     "Non, il est réservé à des autorités désignées", ["Oui, tout citoyen peut le déclencher", "Oui, par simple lettre au chef de l'État", "Non, seul un juge ordinaire peut le déclencher"],
     "Dans la saisine a priori classique, ce sont des autorités politiques qui saisissent la juridiction.", 4),
    ("Un justiciable invoque un droit constitutionnel contre une loi ; le juge ordinaire ne peut pas lui-même la censurer et doit demander à la cour suprême ou constitutionnelle de se prononcer. Comment nomme-t-on cette transmission ?",
     "Une question préjudicielle de constitutionnalité", ["Un référé-liberté", "Un recours gracieux", "Un pourvoi pour excès de pouvoir"],
     "Le juge sursoit et pose la question à une juridiction spécialisée, qui répond sur la conformité de la loi.", 5),
])
M("controle-cm", [
    ("Au Cameroun, quel organe est présenté par la Constitution de 1996 comme chargé du contrôle de la constitutionnalité des lois ?",
     "Le Conseil constitutionnel", ["Le Conseil économique et social", "La Cour des comptes", "Le Conseil d'État"],
     "La Constitution camerounaise institue un Conseil constitutionnel, juge de la constitutionnalité des lois.", 3),
    ("Au Cameroun, quel organe est compétent, en droit constitutionnel, pour trancher les contestations relatives à l'élection présidentielle ?",
     "Le Conseil constitutionnel", ["La Cour des comptes", "Le tribunal de première instance", "Le Conseil économique et social"],
     "La Constitution lui attribue le contentieux de l'élection présidentielle et le contrôle des opérations référendaires.", 4),
], region="CM")

# ============================================================================================================
# F. Hiérarchie des normes : cas
M("normes", [
    ("Un décret contredit une loi ordinaire sur un point précis. Laquelle des deux normes doit en principe l'emporter ?",
     "La loi, car elle est supérieure au décret", ["Le décret, car il est plus précis", "Le décret, car il émane du gouvernement", "Aucune, elles sont de même rang"],
     "Dans la hiérarchie interne, le règlement est subordonné à la loi.", 2),
    ("Un arrêté municipal contredit un décret national. Quelle norme prévaut ?",
     "Le décret, car la norme nationale est supérieure", ["L'arrêté, car il est plus local", "L'arrêté, car il est plus récent", "Aucune, elles s'appliquent toutes deux"],
     "Une autorité locale ne peut contredire une norme de rang supérieur.", 2),
    ("Une loi est contraire à la Constitution. Quelle conséquence de principe en découle ?",
     "Elle peut être censurée ou écartée par le juge compétent", ["Elle devient une loi organique", "Elle reste valable car le parlement l'a voulue", "Elle remplace la disposition constitutionnelle"],
     "La Constitution est la norme suprême : une loi contraire est irrégulière.", 2),
    ("Deux lois de même rang se contredisent ; la plus récente abroge implicitement la plus ancienne dans la mesure de la contradiction. Quel adage l'exprime ?",
     "La loi postérieure déroge à la loi antérieure", ["La loi spéciale ne vaut que dans la ville", "Nul n'est censé ignorer la loi", "La loi supérieure déroge à la loi inférieure"],
     "Entre normes de même rang, la plus récente l'emporte sur la plus ancienne.", 4),
    ("Un texte général et un texte spécial de même rang règlent différemment la même question particulière. Quel principe guide en principe le choix ?",
     "Le texte spécial l'emporte sur le texte général", ["Le texte général l'emporte toujours", "Le plus long l'emporte", "Le plus ancien l'emporte"],
     "Specialia generalibus derogant : la règle spéciale déroge à la règle générale.", 4),
    ("Un ministre adresse à ses services une circulaire expliquant comment lire une loi. Cette circulaire s'impose-t-elle aux citoyens comme une loi ?",
     "Non, elle est en principe adressée aux services et n'a pas la force d'une loi", ["Oui, elle est supérieure à la loi", "Oui, elle a la valeur d'un traité", "Oui, elle remplace le décret"],
     "Une circulaire interprétative ne crée pas de droit ni d'obligation pour les administrés.", 4),
    ("Un traité est régulièrement ratifié et appliqué par l'autre partie. En droit français, quelle est sa place par rapport à la loi ?",
     "Il est supérieur à la loi", ["Il est inférieur à la loi", "Il est supérieur à la Constitution", "Il est de même rang que le décret"],
     "En droit français, le traité régulièrement ratifié et appliqué a une autorité supérieure à celle de la loi, mais inférieure à la Constitution.", 4),
    ("Un traité international contredit directement la Constitution d'un État. Dans le droit interne de cet État, quelle norme prévaut en principe ?",
     "La Constitution, norme suprême de l'ordre interne", ["Le traité, car il est international", "La plus récente des deux", "Celle que choisit le gouvernement"],
     "En droit interne, la Constitution est généralement placée au sommet, même si l'État peut engager sa responsabilité internationale.", 5),
    ("Une loi organique et une loi ordinaire sont en conflit. La première précise la Constitution, la seconde l'enfreint. Quelle conséquence ?",
     "La loi ordinaire peut être censurée", ["La loi organique est automatiquement abrogée", "Les lois sont suspendues ensemble", "Le décret tranche entre elles"],
     "La loi ordinaire doit respecter la loi organique prise en application de la Constitution.", 5),
    ("Un juge administratif français découvre un principe non écrit, qu'il applique contre des décisions d'administration. Comment nomme-t-on ce type de norme ?",
     "Un principe général du droit", ["Une loi de finances", "Un traité constitutif", "Une circulaire ministérielle"],
     "Les principes généraux du droit sont dégagés par le juge et s'imposent à l'administration.", 4),
    ("Un décret d'application doit mettre en œuvre une loi. Il va au-delà de ce que la loi prévoit et ajoute des obligations nouvelles. Quel défaut présente-t-il ?",
     "Il excède l'habilitation donnée par la loi", ["Il est inconstitutionnel par nature", "Il est supérieur à la loi", "Il est une loi organique"],
     "Un règlement d'application ne peut pas aller au-delà de ce que la loi permet.", 3),
    ("Un étudiant soutient qu'un arrêté ministériel l'emporte sur un décret du chef du gouvernement, car il est plus récent. A-t-il raison ?",
     "Non, le décret est d'un rang supérieur à l'arrêté ministériel", ["Oui, le plus récent l'emporte toujours", "Oui, l'arrêté est plus précis", "Non, le décret doit respecter l'arrêté"],
     "La date ne suffit pas à renverser la hiérarchie des normes.", 3),
    ("Un parlement veut changer la Constitution par une loi ordinaire, sans suivre la procédure de révision. Quel est le vice de cette démarche ?",
     "La loi ordinaire ne peut pas modifier la Constitution", ["La loi est trop longue", "Les ministres n'ont pas voté", "Le décret doit d'abord être publié"],
     "La Constitution se modifie selon sa propre procédure, plus exigeante qu'une loi ordinaire.", 3),
    ("En droit français, de quels textes se compose le bloc de constitutionnalité tel que le juge l'entend ?",
     "De la Constitution et des textes auxquels son préambule renvoie", ["De toutes les lois votées depuis 1958", "Des décrets pris en Conseil des ministres", "Des circulaires et instructions"],
     "Le préambule renvoie à la Déclaration de 1789 et à d'autres textes qui ont valeur constitutionnelle.", 5),
    ("Un règlement intérieur du parlement ajoute une règle qui change la Constitution. Peut-il le faire ?",
     "Non, il doit respecter la Constitution", ["Oui, le parlement est souverain", "Oui, si les députés l'adoptent à l'unanimité", "Oui, s'il est publié au journal officiel"],
     "Les règles internes du parlement sont subordonnées à la Constitution.", 4),
    ("Une coutume constitutionnelle apparaît dans la pratique des institutions, sans texte. Comment la qualifie-t-on par rapport au texte écrit ?",
     "Une règle non écrite d'origine coutumière", ["Une loi de ratification", "Un règlement d'application", "Une délibération municipale"],
     "La coutume constitutionnelle naît de la pratique répétée, avec la conviction d'être obligatoire.", 4),
    ("L'ordre juridique interne est organisé en pyramide : quelle forme d'ordre cette image traduit-elle ?",
     "Une hiérarchie où chaque norme tire sa validité d'une norme supérieure", ["Un ordre où toutes les normes sont égales", "Un ordre où la plus récente est suprême", "Un ordre sans norme de référence"],
     "C'est la théorie de Kelsen : chaque norme est valide parce qu'elle respecte la norme supérieure.", 3),
    ("Un acte administratif individuel est pris en violation d'une loi. Peut-il être annulé par le juge ?",
     "Oui, pour illégalité, car il est inférieur à la loi", ["Non, un acte individuel n'est jamais soumis à la loi", "Non, seul le chef de l'État peut l'annuler", "Oui, mais seulement pour opportunité"],
     "L'administration est liée par le principe de légalité, y compris pour les décisions individuelles.", 3),
    ("Le juge administratif écarte un règlement parce qu'il méconnaît un principe constitutionnel qui s'impose à lui. Que contrôle-t-il ?",
     "La conformité du règlement aux normes supérieures", ["L'opportunité politique du règlement", "L'ancienneté du règlement", "La popularité du règlement"],
     "Le règlement doit respecter l'ensemble des normes qui lui sont supérieures, Constitution comprise.", 3),
])

# ============================================================================================================
# G. Libertés publiques : cas
M("libertes", [
    ("Un groupe annonce à l'autorité administrative une manifestation sur la voie publique, sans demander d'autorisation préalable. De quel régime juridique relève cette formalité ?",
     "Du régime de déclaration préalable", ["Du régime d'autorisation préalable", "Du régime répressif pur", "Du régime d'interdiction générale"],
     "La déclaration informe l'autorité, qui peut interdire si l'ordre public l'exige, sans que l'accord soit nécessaire.", 3),
    ("Un journal publie un article diffamatoire et est poursuivi après sa parution. Quel régime caractérise la liberté de la presse dans ce cas ?",
     "Un régime répressif", ["Un régime préventif", "Un régime d'autorisation préalable", "Un régime d'interdiction générale"],
     "Aucune censure préalable : on sanctionne les abus après coup.", 2),
    ("Une commission doit voir et autoriser chaque film avant sa projection. Quel régime ce contrôle illustre-t-il ?",
     "Un régime préventif", ["Un régime répressif", "Un régime de simple déclaration", "Un régime d'abrogation"],
     "L'autorisation préalable subordonne l'exercice de la liberté à l'accord de l'autorité.", 3),
    ("Une mairie interdit de manière générale et absolue toute manifestation dans la commune, sans limite de temps. Que peut-on lui reprocher ?",
     "Une mesure disproportionnée et attentatoire à une liberté", ["Une mesure trop favorable aux manifestants", "Un défaut de publication au journal", "Une atteinte à l'indépendance du juge"],
     "Une restriction générale et absolue n'est pas proportionnée à l'objectif d'ordre public.", 3),
    ("Le préfet interdit une manifestation précise car des heurts graves sont sérieusement redoutés et aucune autre mesure n'est suffisante. Quelle condition de légalité est invoquée ?",
     "La nécessité de la mesure pour protéger l'ordre public", ["Le principe du secret du vote", "La gratuité de l'enseignement", "L'inviolabilité du domicile"],
     "Une mesure de police doit être nécessaire et proportionnée à une menace réelle.", 3),
    ("Un citoyen est empêché de quitter son village sans raison, par une décision générale du chef de canton. Quelle liberté est la plus directement atteinte ?",
     "La liberté d'aller et venir", ["La liberté de la presse", "La liberté syndicale", "La liberté d'association"],
     "Elle garantit la possibilité de se déplacer librement à l'intérieur et vers l'extérieur du pays.", 2),
    ("Une personne est interdite de culte privé chez elle, au seul motif de sa religion. Quelle liberté est atteinte ?",
     "La liberté de religion, y compris sa pratique", ["La liberté d'entreprendre", "La liberté du commerce", "Le droit à l'éducation"],
     "La liberté religieuse couvre la croyance et le culte, sous réserve de l'ordre public.", 2),
    ("Une loi interdit un culte pratiqué en public lorsqu'il cause un trouble grave à l'ordre public. A-t-elle forcément violé la liberté religieuse ?",
     "Pas forcément, car l'exercice public d'un culte peut être limité par l'ordre public", ["Oui, aucune limite n'est admise", "Oui, la liberté de croire est absolue sans nuance", "Non, l'ordre public prime sans condition"],
     "Croire est absolu, mais manifester sa croyance peut être limité si nécessaire et proportionné.", 4),
    ("Des policiers pénètrent sans autorisation légale dans une maison pour fouiller. Quelle liberté est directement atteinte ?",
     "L'inviolabilité du domicile", ["La liberté de réunion", "La liberté de conscience", "Le droit de vote"],
     "Le domicile ne peut être visité que dans les conditions fixées par la loi.", 2),
    ("Une autorité ouvre le courrier d'un particulier sans base légale. Quelle liberté est violée ?",
     "Le secret de la correspondance", ["La liberté du commerce", "Le droit de grève", "La liberté de réunion"],
     "La correspondance relève du respect de la vie privée.", 2),
    ("Un syndicat organise l'arrêt collectif du travail pour défendre des revendications professionnelles. Quel droit exerce-t-il ?",
     "Le droit de grève", ["Le droit de pétition", "La liberté d'association politique", "Le droit de vote"],
     "Le droit de grève est reconnu, mais son exercice est encadré par la loi.", 2),
    ("Un employeur licencie un salarié en raison de son appartenance à un syndicat. Quelle atteinte juridique est caractérisée ?",
     "Une discrimination liée à la liberté syndicale", ["Une application de la liberté d'entreprendre", "Un exercice du droit de grève", "Une mesure de police administrative"],
     "Nul ne peut être pénalisé pour son appartenance ou son action syndicale.", 3),
    ("On expropriera un terrain pour construire une école, à condition que l'utilité publique soit établie et qu'une indemnité juste soit versée au préalable. Quel principe cela illustre-t-il ?",
     "Le droit de propriété et son encadrement", ["Le droit de grève", "La liberté de la presse", "Le secret du vote"],
     "La propriété peut être sacrifiée pour cause d'utilité publique, sous garanties d'indemnisation.", 3),
    ("Une personne affirme à la radio un fait précis mensonger qui porte atteinte à l'honneur d'un particulier. Quelle limite à la liberté d'expression est en jeu ?",
     "La répression de la diffamation", ["La censure préalable", "La liberté d'association", "Le droit de pétition"],
     "La liberté d'expression a pour limite le respect de la réputation d'autrui, sanctionné après coup.", 3),
    ("Un orateur appelle publiquement à la haine violente contre un groupe ethnique. Peut-il en principe s'abriter derrière la liberté d'expression ?",
     "Non, l'appel à la haine peut être légalement sanctionné", ["Oui, la liberté d'expression est illimitée", "Oui, car il parle en public", "Non, car l'orateur n'est pas électeur"],
     "La liberté d'expression ne couvre pas les provocations à la haine ou à la violence.", 3),
    ("Un prévenu n'est pas informé des faits qui lui sont reprochés et n'a pas le droit de se défendre. Quel principe est violé ?",
     "Les droits de la défense", ["Le principe de laïcité", "Le droit d'asile", "Le droit de vote"],
     "Tout accusé doit connaître les accusations et pouvoir se défendre.", 2),
    ("Un journal présente comme coupable un suspect avant tout jugement. Quel principe de procédure est mis à mal ?",
     "La présomption d'innocence", ["La liberté d'aller et venir", "La séparation des pouvoirs", "Le secret du vote"],
     "Toute personne est présumée innocente jusqu'à ce que sa culpabilité soit établie.", 2),
    ("Une loi prévoit une peine plus lourde qu'au moment des faits pour une infraction déjà commise. Quel principe interdit en principe de l'appliquer ?",
     "La non-rétroactivité de la loi pénale plus sévère", ["La séparation des Églises et de l'État", "Le principe de subsidiarité", "Le droit de pétition"],
     "On ne peut punir plus sévèrement qu'au moment où l'acte a été commis.", 3),
    ("Un juge condamne une personne pour un comportement qu'aucun texte n'incriminait. Quel principe est méconnu ?",
     "La légalité des délits et des peines", ["La liberté d'association", "L'inviolabilité parlementaire", "Le principe de laïcité"],
     "Nul ne peut être puni sans texte clair et préalable (nullum crimen sine lege).", 3),
    ("Un étranger est expulsé sans pouvoir former le moindre recours devant un juge. Quelle garantie est la plus directement atteinte ?",
     "Le droit à un recours effectif", ["Le droit de grève", "Le suffrage universel", "La liberté de la presse"],
     "Toute décision grave doit pouvoir être contestée devant une autorité indépendante.", 3),
    ("Une loi traite différemment deux catégories de personnes placées dans des situations tout à fait comparables, sans raison. Quel principe est violé ?",
     "Le principe d'égalité devant la loi", ["La séparation des pouvoirs", "La liberté de la presse", "Le droit de pétition"],
     "L'égalité interdit les différences de traitement arbitraires.", 2),
    ("Une loi accorde un tarif réduit à des étudiants sans ressources, pas aux autres. Est-ce nécessairement contraire à l'égalité ?",
     "Non, si la différence est justifiée par une situation différente et proportionnée", ["Oui, toute différence est interdite", "Oui, l'égalité exige un traitement identique en tous cas", "Non, la loi n'est jamais soumise à l'égalité"],
     "L'égalité n'interdit pas de traiter différemment des situations différentes.", 4),
    ("Un groupe se réunit pour créer une association culturelle. Il lui suffit de déclarer sa création. Quel principe de la liberté d'association est ici illustré ?",
     "L'absence d'autorisation préalable obligatoire", ["L'obligation de recevoir l'accord du préfet", "Le monopole de l'État sur les associations", "L'interdiction de toute association"],
     "La liberté d'association est en principe soumise à simple déclaration, non à permission.", 3),
    ("Une association poursuit un but gravement contraire à l'ordre public. Peut-elle en principe être dissoute ?",
     "Oui, par décision juridictionnelle ou selon une procédure légale encadrée", ["Non, elle est intouchable", "Oui, par simple décision d'un maire", "Non, seul le chef de l'État peut la créer"],
     "La dissolution d'une association est possible mais encadrée par la loi, avec contrôle du juge.", 4),
    ("Une loi permet à tous les résidents de saisir un service public gratuit d'aide juridictionnelle. De quel type de droit est-ce un exemple ?",
     "Un droit-créance", ["Un droit-liberté", "Une liberté-résistance", "Un droit-prérogative de puissance publique"],
     "Les droits-créances exigent une prestation positive de l'État, par opposition aux libertés.", 4),
    ("Une personne réclame à l'État qu'il lui fournisse un logement. De quel type de droit relève, par nature, cette demande ?",
     "D'un droit-créance", ["D'un droit-liberté", "D'une liberté fondamentale classique", "D'une immunité"],
     "Le droit au logement suppose une action positive de l'État, ce qui le range parmi les droits-créances.", 4),
    ("Une personne exige que l'État s'abstienne de lire sa correspondance privée. De quel type de droit relève la demande ?",
     "D'un droit-liberté", ["D'un droit-créance", "D'un droit-prérogative", "D'une dette publique"],
     "Les libertés classiques supposent surtout l'abstention de l'État.", 3),
    ("Une personne dépose une pétition auprès d'une assemblée pour demander une réforme. Quel droit exerce-t-elle ?",
     "Le droit de pétition", ["Le droit de grève", "Le droit de dissolution", "Le droit de grâce"],
     "La pétition permet de s'adresser aux autorités pour demander une mesure.", 2),
    ("Une cour reconnaît à une personne le droit de demander réparation à un particulier qui a violé sa vie privée. Quel concept juridique est illustré ?",
     "L'effet horizontal des droits fondamentaux", ["L'immunité de juridiction", "La tutelle administrative", "La séparation des pouvoirs"],
     "Les droits fondamentaux peuvent aussi être invoqués dans les rapports entre particuliers.", 5),
    ("Une loi limite temporairement certaines libertés en raison d'un danger précis, avec contrôle du juge. Quelle exigence essentielle doit être respectée ?",
     "La proportionnalité de la restriction au but poursuivi", ["La gratuité de la mesure", "La majorité des deux tiers au référendum", "L'accord unanime du Conseil des ministres"],
     "La restriction d'une liberté doit être adaptée, nécessaire et proportionnée.", 3),
    ("Une mesure interdit à des journalistes de couvrir un événement public sans en indiquer d'autre motif que la gêne. Quelle liberté est atteinte ?",
     "La liberté de la presse", ["La liberté du culte", "Le droit de grève", "La liberté d'aller et venir"],
     "La presse peut être limitée pour des motifs précis, mais pas pour de simples convenances de l'autorité.", 3),
    ("Un enfant est empêché d'aller à l'école publique parce que ses parents n'ont pas payé des frais illégaux. Quelle exigence générale est méconnue ?",
     "L'égal accès à l'instruction", ["La liberté du commerce", "La liberté de réunion", "Le droit de grève"],
     "Le droit à l'éducation suppose un accès égal à l'enseignement, sans obstacles injustifiés.", 4),
])

# ============================================================================================================
# H. États d'exception et circonstances exceptionnelles
M("exception", [
    ("Face à une catastrophe grave menaçant la sécurité, le gouvernement prend temporairement des mesures de police plus étendues qu'en temps normal, encadrées par la loi. Comment qualifie-t-on ce régime ?",
     "Un régime d'exception, comme l'état d'urgence", ["Un régime de laïcité", "Un régime de décentralisation", "Une démocratie directe"],
     "Les régimes d'exception élargissent temporairement les pouvoirs des autorités pour faire face à un péril grave.", 2),
    ("Un régime d'exception est maintenu pendant des années sans réexamen, sans contrôle du parlement ni du juge. Quelle caractéristique de l'État de droit est perdue ?",
     "Le caractère temporaire et contrôlé de l'exception", ["La liberté des cultes", "La gratuité des services publics", "L'indépendance de la banque centrale"],
     "L'exception doit rester limitée dans le temps, nécessaire et soumise à contrôle.", 3),
    ("Pendant une inondation, un maire réquisitionne un bâtiment privé pour héberger les sinistrés, sans suivre la procédure habituelle. Quelle théorie peut justifier sa légalité ?",
     "La théorie des circonstances exceptionnelles", ["La théorie de la séparation des pouvoirs", "La théorie de l'imprévision contractuelle", "La théorie des majorités qualifiées"],
     "Face à une situation exceptionnelle, la légalité peut être appréciée plus souplement si l'action est nécessaire.", 4),
    ("En période d'urgence, un décret restreint la circulation pour une durée limitée, dans les zones concernées seulement. Quelle exigence cela respecte-t-il ?",
     "La proportionnalité de la mesure à la menace", ["La rétroactivité de la loi pénale", "Le suffrage universel", "La laïcité de l'État"],
     "La mesure doit être limitée dans le temps et dans l'espace à ce que la menace exige.", 3),
    ("En période d'état d'urgence, une personne conteste devant un juge une mesure qui la vise. Le juge est-il en principe compétent ?",
     "Oui, le contrôle du juge subsiste en principe", ["Non, le juge est suspendu pendant l'urgence", "Non, seul le parlement peut juger la mesure", "Oui, mais seulement après la fin de l'urgence"],
     "Dans un État de droit, l'exception n'écarte pas le contrôle juridictionnel.", 4),
    ("Dans une situation de guerre, les autorités militaires reçoivent une partie des pouvoirs de police normalement exercés par les autorités civiles. Quel régime est décrit ?",
     "L'état de siège", ["L'état d'urgence civil", "La démocratie semi-directe", "La décentralisation"],
     "L'état de siège transfère certains pouvoirs de police à l'autorité militaire.", 4),
    ("Un pacte international de droits de l'homme permet à un État de déroger à certains droits en cas de danger public exceptionnel. Quel droit ne peut jamais être suspendu même en pareil cas ?",
     "L'interdiction de la torture", ["La liberté de réunion", "La liberté d'aller et venir", "La liberté de la presse"],
     "La prohibition de la torture fait partie du noyau dur des droits indérogeables.", 3),
    ("Un gouvernement suspend, au nom de l'urgence, le droit à un procès équitable pour tous les accusés, sans limite de durée. Que peut-on en dire ?",
     "Cette mesure dépasse ce que l'urgence permet et viole des garanties essentielles", ["Elle est toujours régulière en cas d'urgence", "Elle est requise par tout traité", "Elle relève du pouvoir de grâce"],
     "Les États ne peuvent pas priver les personnes des garanties fondamentales du procès, même pendant l'exception.", 4),
    ("Un chef d'État dissout le parlement, suspend la Constitution et gouverne seul sans base juridique. Comment qualifie-t-on juridiquement cet acte ?",
     "Un coup d'État", ["Un état d'urgence régulier", "Une révision constitutionnelle", "Une dissolution parlementaire"],
     "Une prise ou un maintien du pouvoir en violation de la Constitution est un coup d'État.", 3),
    ("Un gouvernement invoque les « pleins pouvoirs » pour légiférer à la place du parlement sans limite de matière ni de durée. Quel principe démocratique est atteint ?",
     "Le partage des fonctions entre pouvoirs", ["L'égalité de traitement fiscal", "La liberté d'entreprendre", "La liberté du culte"],
     "Concentrer indéfiniment les pouvoirs va à l'encontre de la séparation et de l'équilibre des pouvoirs.", 3),
    ("Lors d'une épidémie, une loi limite pour une période déterminée certains rassemblements publics, avec contrôle du juge. Cette limitation est-elle juridiquement concevable ?",
     "Oui, si elle est nécessaire, proportionnée et limitée dans le temps", ["Non, toute restriction est interdite en tout cas", "Oui, si elle est secrète", "Oui, même si elle est perpétuelle"],
     "Les libertés peuvent être restreintes pour un motif légitime comme la santé publique, sous conditions.", 4),
    ("L'« état de nécessité » invoqué par un gouvernement pour justifier une mesure grave suffit-il, à lui seul, à la rendre légale ?",
     "Non, il faut en plus la proportionnalité et le contrôle", ["Oui, la nécessité efface tout contrôle", "Oui, car le juge doit toujours s'incliner", "Non, car la nécessité n'existe jamais en droit"],
     "La nécessité est une condition, jamais un blanc-seing.", 5),
    ("Le chef de l'État accorde individuellement une remise de peine à un condamné. Comment nomme-t-on cette prérogative ?",
     "Le droit de grâce", ["L'amnistie", "La promulgation", "Le droit de dissolution"],
     "La grâce est individuelle et dispense d'exécuter tout ou partie de la peine ; l'amnistie est collective et législative.", 4),
])

# ============================================================================================================
# I. Décentralisation et déconcentration : cas
M("decentr", [
    ("Un ministre délègue à son représentant dans une région le pouvoir de prendre certaines décisions locales, en restant son supérieur hiérarchique. De quoi s'agit-il ?",
     "D'une déconcentration", ["D'une décentralisation", "D'une fédération", "D'une privatisation"],
     "La décision est prise plus près du terrain, mais par un agent de l'État soumis à la hiérarchie.", 2),
    ("Les habitants d'une ville élisent un conseil qui règle seul, dans le cadre de la loi, les affaires locales. De quoi s'agit-il ?",
     "D'une décentralisation", ["D'une déconcentration", "D'une centralisation pure", "D'une confédération"],
     "Une collectivité distincte de l'État, dotée d'organes élus, gère ses propres affaires.", 2),
    ("Un supérieur hiérarchique peut annuler ou réformer la décision de son subordonné. Peut-il le faire pour un acte d'une commune décentralisée ?",
     "Non, l'État n'exerce qu'un contrôle prévu par la loi, non un pouvoir hiérarchique", ["Oui, comme pour un agent d'un service de l'État", "Oui, sur simple appel téléphonique", "Oui, mais seulement le chef du village"],
     "La tutelle sur les collectivités décentralisées est limitée aux cas prévus par les textes.", 4),
    ("L'État vérifie après coup, devant le juge, qu'un acte d'une commune respecte la loi, sans pouvoir le modifier lui-même. Comment nomme-t-on ce contrôle ?",
     "Un contrôle de légalité", ["Un contrôle hiérarchique", "Un contrôle d'opportunité", "Un contrôle préventif"],
     "L'autorité de contrôle saisit le juge si elle estime l'acte illégal.", 3),
    ("Un représentant de l'État a le pouvoir de refuser, par avance, d'approuver un acte d'une collectivité. Quel type de contrôle cela suppose-t-il ?",
     "Une tutelle d'approbation préalable", ["Un contrôle juridictionnel a posteriori", "Un pouvoir hiérarchique", "Un recours gracieux"],
     "La tutelle a priori subordonne la validité de l'acte à l'accord de l'autorité de tutelle.", 4),
    ("Une région reçoit une compétence transférée par la loi, mais aucun moyen financier pour l'exercer. Quel principe de la décentralisation est méconnu ?",
     "Le principe de compensation des charges transférées", ["Le principe de laïcité", "Le principe de la séparation des pouvoirs", "Le principe de non-rétroactivité"],
     "Un transfert de compétences doit s'accompagner des ressources nécessaires pour l'exercer.", 4),
    ("Un conseil municipal vote le budget d'une commune. Quelle caractéristique de la décentralisation cela traduit-il ?",
     "L'autonomie de décision d'une collectivité par un organe élu", ["La tutelle hiérarchique du ministre", "La suppression de toute règle nationale", "La souveraineté de la commune"],
     "Une collectivité dispose d'un organe délibérant et de compétences propres.", 2),
    ("Une commune se déclare « souveraine » et refuse d'appliquer une loi nationale. Quelle règle de l'État unitaire est ignorée ?",
     "L'unité de l'État : les collectivités n'ont pas de souveraineté propre", ["La division des pouvoirs entre États fédérés", "L'obligation de dissoudre la commune", "La séparation des Églises et de l'État"],
     "Dans un État unitaire, les collectivités agissent dans le cadre de la loi nationale.", 3),
    ("Un préfet ou gouverneur, représentant de l'État dans une circonscription, est nommé par le pouvoir central. Dans quelle logique ce poste s'inscrit-il ?",
     "La déconcentration", ["La décentralisation", "Le fédéralisme", "La confédération"],
     "Il est l'agent du pouvoir central sur le territoire, soumis à la hiérarchie.", 3),
    ("Le chef d'un service de l'État dans une région applique une directive nationale précise sans pouvoir la modifier. Quelle relation le lie à Paris ou à la capitale ?",
     "Une relation hiérarchique", ["Une relation de tutelle", "Une relation de confédération", "Une relation d'autonomie financière"],
     "Dans la déconcentration, l'agent reste soumis à l'autorité du pouvoir central.", 3),
    ("Un établissement public est créé pour gérer un hôpital ; il a sa propre personnalité juridique mais un domaine d'action limité. Quel principe le gouverne ?",
     "Le principe de spécialité", ["Le principe de libre administration générale", "Le principe de souveraineté", "Le principe de parité"],
     "Un établissement public agit dans les limites de la mission que lui assigne le texte qui le crée.", 4),
    ("Une collectivité territoriale décentralisée a-t-elle en principe une compétence générale pour les affaires locales ?",
     "Oui, par opposition à l'établissement public spécialisé", ["Non, elle n'a jamais de compétence", "Oui, y compris en matière internationale", "Non, elle est un simple service de l'État"],
     "La collectivité s'occupe des affaires de son territoire, alors qu'un établissement public a une mission limitée.", 4),
    ("Un maire agit au nom de l'État pour enregistrer des actes d'état civil. En droit français, que traduit ce rôle ?",
     "Un dédoublement fonctionnel : il agit pour l'État", ["Une décentralisation complète", "Une déconcentration pure", "Une confédération de communes"],
     "Le maire est à la fois agent de la commune et, pour certaines fonctions, agent de l'État.", 5),
    ("Un parlement fédéral et des États fédérés disposent chacun de leur constitution et de leur parlement. Quel système est décrit ?",
     "Un État fédéral", ["Un État unitaire décentralisé", "Un État unitaire centralisé", "Une monarchie absolue"],
     "Dans un État fédéral, les entités fédérées disposent d'une autonomie constitutionnelle.", 3),
    ("Dans un État où les collectivités ont des compétences, mais pas de constitution propre ni de pouvoir législatif autonome, de quel type d'État parle-t-on ?",
     "D'un État unitaire décentralisé", ["D'un État fédéral", "D'une confédération", "D'un empire"],
     "Les collectivités agissent dans le cadre de la Constitution et des lois de l'État.", 3),
    ("Plusieurs États souverains s'unissent par un traité en gardant leur souveraineté, avec un organe commun aux compétences limitées. Quelle forme d'union est-ce ?",
     "Une confédération", ["Une fédération", "Un État unitaire", "Une région autonome"],
     "Une confédération repose sur un traité, chaque État gardant sa souveraineté.", 3),
    ("Une région élit son conseil et gère ses compétences, mais l'État peut en dissoudre le conseil pour faute grave dans les cas fixés par la loi. Que cela suppose-t-il ?",
     "Un pouvoir de contrôle de l'État sur une collectivité décentralisée", ["Une absence totale de contrôle", "Une souveraineté régionale", "Une structure confédérale"],
     "La libre administration s'exerce dans les limites de la loi, avec contrôle de l'État.", 4),
    ("L'État décide de confier la gestion des écoles primaires aux communes avec les ressources correspondantes. Quel mouvement politique est décrit ?",
     "Un transfert de compétences, donc une décentralisation", ["Une déconcentration", "Une privatisation", "Une nationalisation"],
     "Les communes reçoivent une compétence qu'elles exercent par leurs organes élus.", 3),
    ("Un citoyen demande à la mairie de modifier une décision d'un ministre. La commune peut-elle le faire ?",
     "Non, la commune n'a pas pouvoir sur les actes de l'État", ["Oui, la commune dirige le ministre", "Oui, le maire est supérieur au ministre", "Oui, par simple vote du conseil"],
     "Les collectivités ne dominent pas l'État ; leurs compétences sont définies par la loi.", 3),
    ("Une commune vote un impôt local dans le cadre de ce que la loi lui permet. Quelle autonomie illustre cette situation ?",
     "L'autonomie financière encadrée par la loi", ["La souveraineté fiscale absolue", "La tutelle hiérarchique", "La confédération"],
     "Les ressources locales supposent une marge de décision, mais limitée par la loi.", 4),
    ("Un conseil régional prend une délibération contraire à une loi nationale. Que peut faire le représentant de l'État ?",
     "Saisir le juge pour la faire annuler", ["La réformer lui-même", "La ratifier aussitôt", "La remplacer par un décret local"],
     "Dans un système de contrôle de légalité, l'autorité de contrôle défère l'acte au juge.", 4),
    ("Un pays est un État unitaire ; la capitale décide de tout sans relais locaux, et des agents nommés l'exécutent. Quel qualificatif convient ?",
     "Centralisé", ["Fédéral", "Confédéral", "Décentralisé"],
     "Il n'y a ni collectivités autonomes ni déconcentration réelle.", 2),
    ("Une commune est dissoute par erreur par un préfet qui n'avait pas ce pouvoir. Quel vice affecte sa décision ?",
     "L'incompétence de l'auteur de l'acte", ["Un excès de proportionnalité", "Un défaut de souveraineté", "Une violation du secret du vote"],
     "Une autorité ne peut agir en dehors des compétences que les textes lui donnent.", 3),
    ("Un conseil de quartier donne un avis sur un projet communal mais ne décide pas. Quelle est la nature de son rôle ?",
     "Consultatif", ["Délibératif", "Souverain", "Hiérarchique"],
     "Un avis éclaire la décision sans la lier.", 2),
    ("Un maire exerce un pouvoir de police dans sa commune pour assurer l'ordre, la tranquillité et la salubrité. De quel type de pouvoir s'agit-il ?",
     "D'un pouvoir de police administrative locale", ["D'un pouvoir juridictionnel", "D'un pouvoir constituant", "D'un pouvoir de ratification"],
     "La police administrative vise à prévenir les atteintes à l'ordre public sur le territoire communal.", 3),
    ("Le maire interdit un spectacle à titre préventif au seul motif que son contenu lui déplaît. Que peut le juge reprocher à l'arrêté ?",
     "Un détournement ou un excès de pouvoir de police", ["Un défaut de souveraineté de la commune", "Une absence de mandat électoral", "Un excès du pouvoir constituant"],
     "La police administrative sert l'ordre public, non les préférences personnelles de l'autorité.", 4),
    ("Un transfert de compétences d'une collectivité vers une autre est-il possible par simple décision d'une des deux, sans texte ?",
     "Non, il faut que la loi ou la Constitution le prévoie", ["Oui, par accord oral", "Oui, par décision du maire seul", "Oui, par un simple vote de la population"],
     "Les compétences des collectivités sont définies par les textes de l'État.", 4),
    ("Plusieurs communes créent ensemble une structure pour gérer le ramassage des déchets. Quelle idée cela illustre-t-il ?",
     "La coopération intercommunale", ["La confédération d'États", "La déconcentration ministérielle", "La séparation des Églises et de l'État"],
     "Les communes peuvent s'associer pour exercer en commun certaines missions.", 3),
])

# ============================================================================================================
# J. Parlement : cas
M("parlement", [
    ("Un projet de loi émane du gouvernement ; une proposition de loi émane d'un parlementaire. Un député dépose un texte de sa propre initiative. De quoi s'agit-il ?",
     "D'une proposition de loi", ["D'un projet de loi", "D'une ordonnance", "D'un décret d'application"],
     "L'initiative parlementaire s'appelle proposition ; celle du gouvernement, projet.", 2),
    ("Le Premier ministre dépose devant l'assemblée un texte préparé par le gouvernement. Comment le nomme-t-on ?",
     "Un projet de loi", ["Une proposition de loi", "Une loi organique", "Un règlement intérieur"],
     "Le texte d'initiative gouvernementale est un projet de loi.", 2),
    ("Une loi, adoptée par chaque chambre dans des termes différents, fait des allers-retours entre elles pour trouver un texte commun. Comment nomme-t-on cette procédure ?",
     "La navette parlementaire", ["La motion de censure", "La question préjudicielle", "Le contreseing"],
     "Dans un parlement bicaméral, la navette vise à parvenir à une rédaction identique.", 3),
    ("Un député veut modifier le texte d'un projet en cours d'examen en séance, en supprimant un article. Quel droit exerce-t-il ?",
     "Le droit d'amendement", ["Le droit de veto", "Le droit de dissolution", "Le droit de grâce"],
     "L'amendement permet de proposer des modifications au texte discuté.", 2),
    ("Un parlementaire prononce à la tribune des propos qu'un particulier juge injurieux. Peut-il en principe être poursuivi pour ces propos ?",
     "Non, il bénéficie de l'irresponsabilité pour ses opinions dans l'exercice de son mandat", ["Oui, comme tout citoyen sans exception", "Oui, mais seulement par le chef de l'État", "Non, car le particulier a perdu sa capacité"],
     "L'irresponsabilité parlementaire protège la liberté de parole de l'élu.", 3),
    ("Le même parlementaire insulte un voisin dans un café, sans rapport avec ses fonctions. L'irresponsabilité parlementaire le couvre-t-elle ?",
     "Non, elle ne protège que les actes liés à l'exercice du mandat", ["Oui, elle couvre toute sa vie privée", "Oui, car il reste élu", "Non, car il est député de l'opposition"],
     "L'irresponsabilité vise les opinions et votes émis dans l'exercice des fonctions.", 4),
    ("Une loi interdit à un parlementaire d'exercer en même temps une charge de ministre. Comment nomme-t-on cette règle ?",
     "Une incompatibilité", ["Une inéligibilité", "Une immunité", "Une prérogative"],
     "L'incompatibilité oblige à choisir entre deux fonctions qu'on ne peut cumuler.", 3),
    ("Une personne condamnée pour fraude électorale est empêchée par la loi de se présenter à une élection pendant une période. Comment qualifie-t-on cette situation ?",
     "Une inéligibilité", ["Une incompatibilité", "Une immunité", "Un quorum"],
     "L'inéligibilité empêche d'être candidat ou élu.", 3),
    ("Un député est nommé ministre et doit renoncer à son siège pendant l'exercice de sa fonction gouvernementale. Quelle règle s'applique ?",
     "Une règle d'incompatibilité entre mandat parlementaire et fonction gouvernementale", ["Une règle d'inéligibilité définitive", "Une règle d'immunité", "Une règle de référendum"],
     "Dans certains systèmes, on ne peut être à la fois ministre et parlementaire.", 4),
    ("Le gouvernement demande à l'assemblée de lui accorder sa confiance sur un programme, sous peine de démission. Quelle procédure engage-t-il ?",
     "La question de confiance", ["La motion de défiance constructive", "La dissolution", "La question orale"],
     "Le gouvernement met en jeu sa responsabilité en demandant l'appui de l'assemblée.", 3),
    ("L'opposition dépose un texte demandant que le gouvernement soit renversé. Quelle procédure utilise-t-elle ?",
     "Une motion de censure", ["Une question de confiance", "Une proposition de loi", "Un amendement budgétaire"],
     "Le parlement peut renverser le gouvernement par ce moyen.", 2),
    ("Un parlement allemand ne peut renverser le chancelier qu'en élisant simultanément son successeur. Quel mécanisme est décrit ?",
     "La motion de défiance constructive", ["La question de confiance classique", "La dissolution anticipée", "La rationalisation budgétaire"],
     "Elle évite de renverser un gouvernement sans alternative.", 4),
    ("Le gouvernement doit exposer à la fois ses recettes et ses dépenses de l'année pour approbation par le parlement. Quel texte est concerné ?",
     "La loi de finances", ["La loi constitutionnelle", "La loi d'amnistie", "La loi de ratification"],
     "La loi de finances autorise les recettes et les dépenses publiques.", 2),
    ("Une loi autorise le président de la République à ratifier un traité important. Quelle fonction du parlement s'exerce ainsi ?",
     "L'autorisation de la ratification", ["Le pouvoir de dissolution", "Le droit de grâce", "Le contrôle de légalité"],
     "Pour certains traités, la ratification suppose l'accord préalable du parlement.", 3),
    ("Un parlementaire absent au vote demande à un collègue de voter à sa place. Quel mécanisme est ici invoqué ?",
     "La délégation de vote", ["La motion de censure", "L'immunité", "L'amendement"],
     "Dans certains parlements, un parlementaire absent peut déléguer son vote dans les limites fixées.", 3),
    ("Un parlementaire reçoit des instructions de ses électeurs et doit voter comme ils l'exigent, sous peine de perdre son mandat. De quel mandat s'agit-il ?",
     "Un mandat impératif", ["Un mandat représentatif", "Un mandat électif sans pouvoir", "Un mandat honorifique"],
     "Le mandat impératif lie l'élu à la volonté de ses électeurs.", 3),
    ("Un député vote selon sa conscience, sans être juridiquement lié par les instructions de ses électeurs. De quel mandat parle-t-on ?",
     "Un mandat représentatif", ["Un mandat impératif", "Un mandat honorifique", "Un mandat administratif"],
     "L'élu représente la nation entière, non seulement ses électeurs.", 3),
    ("Une chambre haute a un pouvoir égal à celle de la chambre basse pour adopter les lois. Comment qualifie-t-on le bicamérisme ?",
     "Égalitaire", ["Inégalitaire", "Directorial", "Plébiscitaire"],
     "Chaque chambre a les mêmes pouvoirs ou presque.", 3),
    ("Dans un parlement, la chambre basse peut avoir le dernier mot contre la chambre haute. Comment qualifie-t-on ce bicamérisme ?",
     "Inégalitaire", ["Égalitaire", "Fédératif", "Unicaméral"],
     "Les pouvoirs des deux chambres ne sont pas équivalents.", 3),
    ("Un parlement ne comprend qu'une seule assemblée. Quel nom porte ce système ?",
     "L'unicamérisme", ["Le bicamérisme", "Le fédéralisme", "Le monocéphalisme"],
     "Un seul organe vote la loi.", 2),
    ("Un parlementaire interroge le ministre en séance sur un point de politique précis et attend sa réponse immédiate. Quel moyen de contrôle utilise-t-il ?",
     "Une question orale au gouvernement", ["Une motion de censure", "Une loi de finances", "Un décret d'application"],
     "Les questions permettent aux élus de demander des comptes au gouvernement.", 2),
    ("Une assemblée constitue une commission pour examiner des faits précis et en rendre compte. Comment nomme-t-on ce moyen d'information ?",
     "Une commission d'enquête parlementaire", ["Une commission de ratification", "Un comité de grâce", "Un conseil de discipline"],
     "Elle permet d'éclairer l'assemblée sur la gestion d'un service ou un événement.", 3),
    ("Un chef de l'État décide de mettre fin aux pouvoirs de l'assemblée et de convoquer de nouvelles élections. Quelle prérogative exerce-t-il ?",
     "La dissolution", ["La censure", "L'amnistie", "Le veto"],
     "La dissolution renvoie l'assemblée devant les électeurs.", 2),
    ("Une loi votée sans lien avec les matières que la Constitution réserve au pouvoir réglementaire autonome est-elle automatiquement contraire à la Constitution ?",
     "Cela dépend de la distribution des compétences prévue par la Constitution", ["Oui, toujours", "Non, jamais", "Oui, car le règlement l'emporte toujours"],
     "Si la Constitution réserve un domaine à l'exécutif, la loi peut y empiéter ; sinon non.", 5),
    ("Un parlement ne peut siéger que si un nombre minimal de membres est présent. Quel terme désigne cette condition ?",
     "Le quorum", ["Le quotient", "Le contreseing", "Le scrutin"],
     "Le quorum est la présence minimale exigée pour délibérer valablement.", 2),
    ("Un parlement vote une résolution sans portée juridique, simple prise de position. Que peut-on dire de sa valeur ?",
     "Elle est politique, sans force de loi", ["Elle s'impose comme une loi organique", "Elle remplace la Constitution", "Elle lie les juges"],
     "Une résolution exprime une opinion ; elle n'est pas une loi.", 4),
    ("Un texte précise l'ordre du jour des travaux de l'assemblée pour la semaine. Qui le fixe en principe dans le régime parlementaire classique ?",
     "L'assemblée, avec parfois une priorité accordée au gouvernement", ["Le juge constitutionnel seul", "Le chef de l'État seul", "Les préfets"],
     "L'ordre du jour est défini par l'assemblée, la Constitution pouvant réserver une place au gouvernement.", 5),
    ("Un citoyen demande que soit abrogée une loi ; il s'adresse à l'assemblée. Quel droit exerce-t-il ?",
     "Le droit de pétition", ["Le droit d'amendement", "La question orale", "Le droit de veto"],
     "La pétition est une demande adressée aux pouvoirs publics.", 2),
    ("Dans un parlement à deux chambres, une proposition est adoptée par l'une et rejetée par l'autre. Sans mécanisme de conciliation, que se passe-t-il ?",
     "Le texte n'est pas adopté définitivement", ["Le texte est promulgué d'office", "Le chef de l'État choisit une version", "Le juge tranche"],
     "Dans un bicamérisme égalitaire, il faut l'accord des deux chambres.", 4),
    ("Une loi de finances est rejetée par l'assemblée. Quelle conséquence politique ce vote peut-il avoir dans un régime parlementaire ?",
     "Il peut engager la responsabilité du gouvernement", ["Il dissout automatiquement la Cour suprême", "Il révise la Constitution", "Il supprime l'impôt"],
     "Le rejet d'un texte essentiel peut être interprété comme un refus de confiance.", 4),
])
