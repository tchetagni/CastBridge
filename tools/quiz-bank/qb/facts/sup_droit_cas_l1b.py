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
     "Le pouvoir de dissolution aux mains de l'exécutif", ["Le droit de censure reconnu à l'assemblée nationale", "L'élection du chef de l'État au suffrage universel", "Le contrôle de constitutionnalité par un juge spécialisé"],
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
     "La séparation entre pouvoir exécutif et pouvoir judiciaire", ["La séparation des Églises et de l'État dans la commune", "La spécialité des personnes publiques dans leurs missions", "La non-rétroactivité des lois pénales plus sévères"],
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
     "Le juge contrôle la légalité des actes de l'administration", ["Le juge peut modifier lui-même le contenu de la loi", "L'administration juge elle-même la légalité de ses actes", "Le parlement nomme et révoque librement les juges"],
     "L'exécutif ne se juge pas lui-même : le juge sanctionne l'illégalité.", 3),
    ("Un parlement vote une loi qui retire à une juridiction toute compétence pour examiner la légalité des actes d'un ministre. Que peut-on lui objecter dans un État de droit ?",
     "Elle prive les citoyens d'un recours effectif", ["Elle viole la règle du quorum", "Elle viole le secret des délibérations", "Elle supprime le droit de vote"],
     "Un État de droit suppose que l'action de l'administration puisse être soumise à un contrôle juridictionnel.", 4),
    ("Dans la pensée de Montesquieu, quelle est la meilleure formule pour résumer le but de la séparation des pouvoirs ?",
     "Que le pouvoir arrête le pouvoir", ["Que le peuple gouverne directement", "Que le juge fasse la loi", "Que l'exécutif absorbe les autres"],
     "Séparer et équilibrer pour empêcher l'abus de pouvoir.", 2),
    ("Une loi de finances est votée par le parlement, mais l'exécutif décide seul d'ajouter une dépense non autorisée. Quel principe est ici contourné ?",
     "Le consentement des représentants à la dépense publique", ["Le principe du contradictoire dans la procédure juridictionnelle", "Le secret du vote lors des élections nationales", "L'inamovibilité des juges du siège"],
     "L'autorisation parlementaire de la dépense est un principe fondamental des régimes démocratiques.", 4),
    ("Le président d'une chambre refuse que les députés de l'opposition s'expriment dans le débat sur une loi. Quel droit est le plus directement concerné ?",
     "Le droit de l'opposition à participer au débat", ["Le droit de grâce reconnu au chef de l'État", "L'immunité du chef de l'État dans l'exercice de ses fonctions", "L'inamovibilité des magistrats du siège"],
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
     "Du pouvoir d'amendement et du vote de la loi de finances", ["Du pouvoir de promulgation des lois adoptées", "Du pouvoir de dissolution de l'assemblée nationale", "Du pouvoir de grâce reconnu au chef de l'État"],
     "Voter, amender ou rejeter les lois de finances est l'un des pouvoirs essentiels du parlement.", 3),
    ("Le chef de l'État annule seul, par décret, un jugement pénal qu'il juge injuste. Que reproche-t-on à cet acte ?",
     "Il empiète sur la fonction de juger", ["Il enfreint le principe de libre administration", "Il viole le secret du vote", "Il dépasse le domaine de la loi organique"],
     "Annuler une décision de justice appartient au juge d'appel ou de cassation, non à l'exécutif ; la grâce est un acte distinct.", 4),
    ("Un parlement vote une loi qui punit rétroactivement une action qui était légale au moment où elle a été commise. Que peut faire le juge constitutionnel ?",
     "La censurer, car la non-rétroactivité de la loi pénale plus sévère s'impose", ["La valider, car la loi exprime toujours la volonté générale", "La réécrire lui-même pour supprimer le caractère rétroactif", "Demander au gouvernement de l'abroger par simple décret"],
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
     "Celui qui a le plus de voix, même sans majorité absolue", ["Le plus âgé des deux candidats en présence", "Aucun des deux, car il faut forcément un second tour", "Celui qui a obtenu le plus de bulletins blancs"],
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
     "Elle n'est pas appliquée dans ce litige mais reste formellement en vigueur", ["Elle est abrogée pour tous les citoyens dès la décision du juge", "Elle est annulée avec un effet rétroactif général et absolu", "Elle est transmise au chef de l'État pour être promulguée à nouveau"],
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
     "Que l'organe de contrôle soit indépendant du pouvoir qu'il contrôle", ["Que l'organe soit nommé par le seul exécutif qu'il doit surveiller", "Que l'organe siège dans la capitale économique de l'État", "Que l'organe soit dispensé de motiver ses décisions"],
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
     "Une question préjudicielle de constitutionnalité", ["Un référé-liberté devant le juge administratif", "Un recours gracieux adressé à l'auteur de l'acte", "Un pourvoi pour excès de pouvoir contre un arrêté"],
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
     "Non, elle est en principe adressée aux services et n'a pas la force d'une loi", ["Oui, elle est supérieure à la loi et s'impose à tous les citoyens", "Oui, elle a la valeur d'un traité et lie les juges de façon générale", "Oui, elle remplace le décret qu'elle commente dans la hiérarchie"],
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
     "Non, le décret est d'un rang supérieur à l'arrêté ministériel", ["Oui, la norme la plus récente l'emporte toujours sur l'autre", "Oui, l'arrêté ministériel est plus précis que le décret", "Non, le décret doit lui-même respecter l'arrêté ministériel"],
     "La date ne suffit pas à renverser la hiérarchie des normes.", 3),
    ("Un parlement veut changer la Constitution par une loi ordinaire, sans suivre la procédure de révision. Quel est le vice de cette démarche ?",
     "La loi ordinaire ne peut pas modifier la Constitution", ["La loi est trop longue pour être adoptée sans débat", "Les ministres n'ont pas pris part au vote final", "Le décret doit d'abord être publié avant le vote"],
     "La Constitution se modifie selon sa propre procédure, plus exigeante qu'une loi ordinaire.", 3),
    ("En droit français, de quels textes se compose le bloc de constitutionnalité tel que le juge l'entend ?",
     "De la Constitution et des textes auxquels son préambule renvoie", ["De toutes les lois votées par le parlement depuis la dernière révision", "Des décrets pris en Conseil des ministres sur le sujet", "Des circulaires et instructions adressées aux préfets"],
     "Le préambule renvoie à la Déclaration de 1789 et à d'autres textes qui ont valeur constitutionnelle.", 5),
    ("Un règlement intérieur du parlement ajoute une règle qui change la Constitution. Peut-il le faire ?",
     "Non, il doit respecter la Constitution", ["Oui, le parlement est souverain", "Oui, si les députés l'adoptent à l'unanimité", "Oui, s'il est publié au journal officiel"],
     "Les règles internes du parlement sont subordonnées à la Constitution.", 4),
    ("Une coutume constitutionnelle apparaît dans la pratique des institutions, sans texte. Comment la qualifie-t-on par rapport au texte écrit ?",
     "Une règle non écrite d'origine coutumière", ["Une loi de ratification", "Un règlement d'application", "Une délibération municipale"],
     "La coutume constitutionnelle naît de la pratique répétée, avec la conviction d'être obligatoire.", 4),
    ("L'ordre juridique interne est organisé en pyramide : quelle forme d'ordre cette image traduit-elle ?",
     "Une hiérarchie où chaque norme tire sa validité d'une norme supérieure", ["Un ordre où toutes les normes ont exactement la même valeur", "Un ordre où la norme la plus récente est toujours suprême", "Un ordre sans aucune norme de référence commune"],
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
     "Une mesure disproportionnée et attentatoire à une liberté", ["Une mesure trop favorable aux manifestants de la commune", "Un défaut de publication au journal officiel local", "Une atteinte à l'indépendance du juge administratif"],
     "Une restriction générale et absolue n'est pas proportionnée à l'objectif d'ordre public.", 3),
    ("Le préfet interdit une manifestation précise car des heurts graves sont sérieusement redoutés et aucune autre mesure n'est suffisante. Quelle condition de légalité est invoquée ?",
     "La nécessité de la mesure pour protéger l'ordre public", ["Le principe du secret du vote pour tout électeur inscrit", "La gratuité de l'enseignement public à tous les niveaux", "L'inviolabilité du domicile des personnes visées"],
     "Une mesure de police doit être nécessaire et proportionnée à une menace réelle.", 3),
    ("Un citoyen est empêché de quitter son village sans raison, par une décision générale du chef de canton. Quelle liberté est la plus directement atteinte ?",
     "La liberté d'aller et venir", ["La liberté de la presse", "La liberté syndicale", "La liberté d'association"],
     "Elle garantit la possibilité de se déplacer librement à l'intérieur et vers l'extérieur du pays.", 2),
    ("Une personne est interdite de culte privé chez elle, au seul motif de sa religion. Quelle liberté est atteinte ?",
     "La liberté de religion, y compris sa pratique", ["La liberté d'entreprendre dans le secteur des cultes", "La liberté du commerce de l'objet de culte", "Le droit à l'éducation religieuse gratuite pour tous"],
     "La liberté religieuse couvre la croyance et le culte, sous réserve de l'ordre public.", 2),
    ("Une loi interdit un culte pratiqué en public lorsqu'il cause un trouble grave à l'ordre public. A-t-elle forcément violé la liberté religieuse ?",
     "Pas forcément, car l'exercice public d'un culte peut être limité par l'ordre public", ["Oui, aucune limite n'est jamais admise en matière de culte", "Oui, car la liberté de croire est absolue y compris en public", "Non, car l'ordre public prime en toutes circonstances sur tout"],
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
     "Le droit de propriété et son encadrement", ["Le droit de grève des salariés des services publics", "La liberté de la presse écrite et audiovisuelle", "Le secret du vote lors des élections nationales"],
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
     "Non, si la différence est justifiée par une situation différente et proportionnée", ["Oui, toute différence de traitement est interdite sans exception", "Oui, l'égalité exige un traitement identique dans tous les cas", "Non, car la loi n'est jamais soumise au principe d'égalité"],
     "L'égalité n'interdit pas de traiter différemment des situations différentes.", 4),
    ("Un groupe se réunit pour créer une association culturelle. Il lui suffit de déclarer sa création. Quel principe de la liberté d'association est ici illustré ?",
     "L'absence d'autorisation préalable obligatoire", ["L'obligation de recevoir l'accord du préfet", "Le monopole de l'État sur les associations", "L'interdiction de toute association"],
     "La liberté d'association est en principe soumise à simple déclaration, non à permission.", 3),
    ("Une association poursuit un but gravement contraire à l'ordre public. Peut-elle en principe être dissoute ?",
     "Oui, par décision juridictionnelle ou selon une procédure légale encadrée", ["Non, une association est intouchable et ne peut jamais être dissoute", "Oui, par simple décision d'un maire, sans recours possible", "Non, seul le chef de l'État peut décider de sa création ou de sa fin"],
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
     "L'effet horizontal des droits fondamentaux", ["L'immunité de juridiction des États étrangers", "La tutelle administrative sur les collectivités", "La séparation des pouvoirs entre les organes de l'État"],
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
     "Un régime d'exception, comme l'état d'urgence", ["Un régime de laïcité, comme la neutralité religieuse", "Un régime de décentralisation, comme la libre administration", "Une démocratie directe, comme le référendum d'initiative"],
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
     "Cette mesure dépasse ce que l'urgence permet et viole des garanties essentielles", ["Elle est toujours régulière dès lors qu'une urgence est invoquée", "Elle est exigée par tous les traités en cas de danger public", "Elle relève du pouvoir de grâce reconnu au chef de l'État"],
     "Les États ne peuvent pas priver les personnes des garanties fondamentales du procès, même pendant l'exception.", 4),
    ("Un chef d'État dissout le parlement, suspend la Constitution et gouverne seul sans base juridique. Comment qualifie-t-on juridiquement cet acte ?",
     "Un coup d'État", ["Un état d'urgence régulier", "Une révision constitutionnelle", "Une dissolution parlementaire"],
     "Une prise ou un maintien du pouvoir en violation de la Constitution est un coup d'État.", 3),
    ("Un gouvernement invoque les « pleins pouvoirs » pour légiférer à la place du parlement sans limite de matière ni de durée. Quel principe démocratique est atteint ?",
     "Le partage des fonctions entre pouvoirs", ["L'égalité de traitement fiscal", "La liberté d'entreprendre", "La liberté du culte"],
     "Concentrer indéfiniment les pouvoirs va à l'encontre de la séparation et de l'équilibre des pouvoirs.", 3),
    ("Lors d'une épidémie, une loi limite pour une période déterminée certains rassemblements publics, avec contrôle du juge. Cette limitation est-elle juridiquement concevable ?",
     "Oui, si elle est nécessaire, proportionnée et limitée dans le temps", ["Non, toute restriction de liberté est interdite en toute circonstance", "Oui, à condition qu'elle reste secrète pour les citoyens", "Oui, même si elle est décidée pour une durée perpétuelle"],
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
     "Non, l'État n'exerce qu'un contrôle prévu par la loi, non un pouvoir hiérarchique", ["Oui, comme pour un agent d'un service de l'État placé sous ses ordres", "Oui, sur simple appel téléphonique adressé au maire concerné", "Oui, par simple décision du ministre sans texte particulier"],
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
     "L'autonomie de décision d'une collectivité par un organe élu", ["La tutelle hiérarchique exercée par le ministre sur la commune", "La suppression de toute règle nationale à l'échelon local", "La souveraineté propre de la commune sur son territoire"],
     "Une collectivité dispose d'un organe délibérant et de compétences propres.", 2),
    ("Une commune se déclare « souveraine » et refuse d'appliquer une loi nationale. Quelle règle de l'État unitaire est ignorée ?",
     "L'unité de l'État : les collectivités n'ont pas de souveraineté propre", ["La division des pouvoirs entre États fédérés souverains", "L'obligation faite à l'État de dissoudre toute commune récalcitrante", "La séparation des Églises et de l'État dans la commune"],
     "Dans un État unitaire, les collectivités agissent dans le cadre de la loi nationale.", 3),
    ("Un préfet ou gouverneur, représentant de l'État dans une circonscription, est nommé par le pouvoir central. Dans quelle logique ce poste s'inscrit-il ?",
     "La déconcentration", ["La décentralisation", "Le fédéralisme", "La confédération"],
     "Il est l'agent du pouvoir central sur le territoire, soumis à la hiérarchie.", 3),
    ("Le chef d'un service de l'État dans une région applique une directive nationale précise sans pouvoir la modifier. Quelle relation le lie au pouvoir central ?",
     "Une relation hiérarchique", ["Une relation de tutelle", "Une relation de confédération", "Une relation d'autonomie financière"],
     "Dans la déconcentration, l'agent reste soumis à l'autorité du pouvoir central.", 3),
    ("Un établissement public est créé pour gérer un hôpital ; il a sa propre personnalité juridique mais un domaine d'action limité. Quel principe le gouverne ?",
     "Le principe de spécialité", ["Le principe de libre administration générale", "Le principe de souveraineté", "Le principe de parité"],
     "Un établissement public agit dans les limites de la mission que lui assigne le texte qui le crée.", 4),
    ("Une collectivité territoriale décentralisée a-t-elle en principe une compétence générale pour les affaires locales ?",
     "Oui, par opposition à l'établissement public spécialisé", ["Non, elle n'a jamais de compétence", "Oui, y compris en matière internationale", "Non, elle est un simple service de l'État"],
     "La collectivité s'occupe des affaires de son territoire, alors qu'un établissement public a une mission limitée.", 4),
    ("Un maire agit au nom de l'État pour enregistrer des actes d'état civil. En droit français, que traduit ce rôle ?",
     "Un dédoublement fonctionnel : il agit pour l'État", ["Une décentralisation complète de la fonction de l'état civil", "Une déconcentration pure sous l'autorité d'un ministre", "Une confédération de communes pour la tenue des registres"],
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
     "Un pouvoir de contrôle de l'État sur une collectivité décentralisée", ["Une absence totale de contrôle de l'État sur la collectivité", "Une souveraineté propre reconnue à la collectivité régionale", "Une structure confédérale entre l'État et la collectivité"],
     "La libre administration s'exerce dans les limites de la loi, avec contrôle de l'État.", 4),
    ("L'État décide de confier la gestion des écoles primaires aux communes avec les ressources correspondantes. Quel mouvement politique est décrit ?",
     "Un transfert de compétences, donc une décentralisation", ["Une simple déconcentration au profit d'agents de l'État", "Une privatisation de l'enseignement primaire local", "Une nationalisation des écoles par les autorités centrales"],
     "Les communes reçoivent une compétence qu'elles exercent par leurs organes élus.", 3),
    ("Un citoyen demande à la mairie de modifier une décision d'un ministre. La commune peut-elle le faire ?",
     "Non, la commune n'a pas pouvoir sur les actes de l'État", ["Oui, la commune dirige l'action du ministre concerné", "Oui, le maire est hiérarchiquement supérieur au ministre", "Oui, par simple vote du conseil municipal de la commune"],
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
     "D'un pouvoir de police administrative locale", ["D'un pouvoir juridictionnel exercé par le maire", "D'un pouvoir constituant délégué à la commune", "D'un pouvoir de ratification des traités internationaux"],
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
     "Non, il bénéficie de l'irresponsabilité pour ses opinions dans l'exercice de son mandat", ["Oui, comme tout citoyen, sans aucune protection particulière", "Oui, mais seulement sur plainte transmise par le chef de l'État", "Non, car le particulier offensé a perdu sa capacité d'agir"],
     "L'irresponsabilité parlementaire protège la liberté de parole de l'élu.", 3),
    ("Le même parlementaire insulte un voisin dans un café, sans rapport avec ses fonctions. L'irresponsabilité parlementaire le couvre-t-elle ?",
     "Non, elle ne protège que les actes liés à l'exercice du mandat", ["Oui, elle couvre toute sa vie privée pendant son mandat", "Oui, car il reste élu de la nation tant qu'il siège", "Non, car le parlementaire appartient à l'opposition"],
     "L'irresponsabilité vise les opinions et votes émis dans l'exercice des fonctions.", 4),
    ("Une loi interdit à un parlementaire d'exercer en même temps une charge de ministre. Comment nomme-t-on cette règle ?",
     "Une incompatibilité", ["Une inéligibilité", "Une immunité", "Une prérogative"],
     "L'incompatibilité oblige à choisir entre deux fonctions qu'on ne peut cumuler.", 3),
    ("Une personne condamnée pour fraude électorale est empêchée par la loi de se présenter à une élection pendant une période. Comment qualifie-t-on cette situation ?",
     "Une inéligibilité", ["Une incompatibilité", "Une immunité", "Un quorum"],
     "L'inéligibilité empêche d'être candidat ou élu.", 3),
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
    ("Un parlement ne peut siéger que si un nombre minimal de membres est présent. Quel terme désigne cette condition ?",
     "Le quorum", ["Le quotient", "Le contreseing", "Le scrutin"],
     "Le quorum est la présence minimale exigée pour délibérer valablement.", 2),
    ("Un parlement vote une résolution sans portée juridique, simple prise de position. Que peut-on dire de sa valeur ?",
     "Elle est politique, sans force de loi", ["Elle s'impose comme une loi organique", "Elle remplace la Constitution", "Elle lie les juges"],
     "Une résolution exprime une opinion ; elle n'est pas une loi.", 4),
    ("Un citoyen demande que soit abrogée une loi ; il s'adresse à l'assemblée. Quel droit exerce-t-il ?",
     "Le droit de pétition", ["Le droit d'amendement", "La question orale", "Le droit de veto"],
     "La pétition est une demande adressée aux pouvoirs publics.", 2),
    ("Dans un parlement à deux chambres, une proposition est adoptée par l'une et rejetée par l'autre. Sans mécanisme de conciliation, que se passe-t-il ?",
     "Le texte n'est pas adopté définitivement", ["Le texte est promulgué d'office", "Le chef de l'État choisit une version", "Le juge tranche"],
     "Dans un bicamérisme égalitaire, il faut l'accord des deux chambres.", 4),
])

# ============================================================================================================
# K. Pouvoir constituant et révision : cas
M("revision", [
    ("Un peuple, après une révolution, élabore une toute nouvelle Constitution sans être lié par l'ancienne. Quel pouvoir s'exerce ?",
     "Le pouvoir constituant originaire", ["Le pouvoir constituant dérivé", "Le pouvoir législatif ordinaire", "Le pouvoir réglementaire"],
     "Il crée la Constitution sans être encadré par un texte antérieur.", 3),
    ("Le parlement modifie un article de la Constitution en suivant la procédure de révision que celle-ci prévoit. Quel pouvoir s'exerce ?",
     "Le pouvoir constituant dérivé", ["Le pouvoir constituant originaire", "Le pouvoir réglementaire", "Le pouvoir juridictionnel"],
     "Il est institué et encadré par la Constitution elle-même.", 3),
    ("Un monarque accorde de lui-même une Constitution à son peuple, sans négociation. Comment la qualifie-t-on ?",
     "Une Constitution octroyée", ["Une Constitution référendaire", "Une Constitution conventionnelle", "Une Constitution coutumière"],
     "Le texte est une concession unilatérale du pouvoir.", 3),
    ("Une Constitution est adoptée par un référendum du peuple après élaboration par une assemblée. Comment qualifie-t-on ce mode d'établissement ?",
     "Démocratique, par référendum constituant", ["Octroyé par le chef de l'État à ses sujets", "Imposé de l'extérieur par une puissance étrangère", "Coutumier, issu d'une longue pratique non écrite"],
     "L'adoption populaire est un procédé démocratique d'établissement.", 3),
    ("Une assemblée constituante est élue pour rédiger la Constitution puis se retire. Quelle fonction a-t-elle ?",
     "Exercer le pouvoir constituant originaire pour rédiger le texte", ["Voter des lois ordinaires de manière permanente et régulière", "Contrôler en continu l'action du gouvernement en place", "Juger les ministres pour les fautes de leur fonction"],
     "Une constituante est une assemblée élue pour établir la Constitution.", 3),
    ("Un parlement veut réviser la Constitution à la majorité simple, alors que le texte exige une majorité renforcée. Quelle conséquence ?",
     "La révision est irrégulière si la procédure prévue n'est pas respectée", ["La révision est valable car le parlement est toujours souverain", "La révision est valable si le juge consent à la valider", "La Constitution devient souple de plein droit dans ce cas"],
     "La révision doit suivre la procédure que la Constitution fixe pour elle-même.", 3),
    ("Une Constitution interdit de réviser la forme républicaine du gouvernement. Comment qualifie-t-on cette interdiction ?",
     "Une limite matérielle à la révision", ["Une limite circonstancielle", "Une limite temporelle", "Une simple tradition politique"],
     "Certains principes sont placés hors de portée de la révision : on parle de clauses d'intangibilité.", 4),
    ("Une Constitution interdit toute révision pendant que le territoire est occupé par une armée étrangère. Comment qualifie-t-on cette limite ?",
     "Une limite circonstancielle", ["Une limite matérielle", "Une limite temporelle", "Une limite internationale"],
     "L'interdiction dépend des circonstances et non du contenu de la révision.", 4),
    ("Un texte constitutionnel interdit toute révision pendant une période fixée à partir de son adoption. Quel type de limite est décrit ?",
     "Une limite temporelle", ["Une limite matérielle", "Une limite circonstancielle", "Une limite territoriale"],
     "Elle interdit de réviser pendant une durée déterminée.", 4),
    ("La procédure de révision d'une Constitution exige un vote du parlement à une majorité spéciale puis un référendum. Que dit-on de cette Constitution ?",
     "Elle est rigide", ["Elle est souple", "Elle est coutumière", "Elle est octroyée"],
     "Une procédure de révision plus lourde que celle des lois ordinaires rend la Constitution rigide.", 3),
    ("Une Constitution peut être modifiée par une loi ordinaire adoptée à la majorité simple. Quelle est sa nature ?",
     "Elle est souple", ["Elle est rigide", "Elle est fédérale", "Elle est octroyée"],
     "Aucune procédure spéciale n'est exigée pour la modifier.", 3),
    ("Un dirigeant fait réviser la Constitution pour pouvoir se représenter indéfiniment aux élections. Quelle question de principe se pose ?",
     "Le respect de l'alternance et des limites au pouvoir", ["Le respect des règles de l'héritage dynastique", "La liberté du commerce intérieur et extérieur", "La fiscalité locale des collectivités territoriales"],
     "La règle de limitation des mandats vise à garantir l'alternance démocratique.", 4),
    ("Qu'est-ce qui distingue, en pratique, l'élaboration d'une nouvelle Constitution de la révision d'une ancienne ?",
     "La nouvelle Constitution rompt avec l'ancien texte ; la révision le modifie selon ses règles", ["La révision est toujours faite par le juge, la nouvelle par le peuple", "La nouvelle Constitution est toujours plus courte que l'ancienne", "La révision suppose toujours un référendum, la nouvelle jamais"],
     "Une révision conserve la continuité juridique ; l'élaboration d'un nouveau texte la rompt.", 4),
    ("Dans un État fédéral, la Constitution fédérale ne peut être modifiée sans l'accord des entités fédérées. Quelle fonction cela remplit-il ?",
     "Protéger l'autonomie des entités fédérées", ["Réduire les pouvoirs du juge", "Supprimer la hiérarchie des normes", "Remplacer la séparation des pouvoirs"],
     "L'exigence d'un accord des entités fédérées garantit leur place dans le système.", 4),
    ("Le préambule d'une Constitution proclame des droits fondamentaux. Un juge constitutionnel peut-il s'en servir pour censurer une loi ?",
     "Oui, s'il reconnaît à ce préambule une valeur constitutionnelle", ["Non, le préambule est toujours sans valeur juridique aucune", "Oui, mais seulement à la demande exclusive du chef de l'État", "Non, car un préambule n'est jamais une norme juridique"],
     "Tout dépend de la valeur reconnue au préambule ; beaucoup de juges lui reconnaissent la valeur de la Constitution.", 4),
])

# ============================================================================================================
# L. État, souveraineté, nation : cas
M("etat", [
    ("Un groupe de personnes vit sur un territoire sans autorité commune organisée ni pouvoir de contrainte. Manque-t-il un élément pour être un État ?",
     "Oui, un pouvoir politique organisé", ["Oui, un hymne national", "Oui, une monnaie propre", "Non, la population suffit"],
     "L'État suppose un territoire, une population et un pouvoir politique organisé.", 2),
    ("Un État décide seul de sa politique étrangère et n'est soumis à aucune puissance extérieure. Quel aspect de la souveraineté illustre-t-il ?",
     "La souveraineté externe", ["La souveraineté interne", "La souveraineté populaire", "La souveraineté partagée"],
     "L'indépendance à l'égard des autres États constitue la souveraineté externe.", 2),
    ("Un État fait respecter ses lois sur tout son territoire et n'admet aucun pouvoir concurrent. Quel aspect de la souveraineté est en cause ?",
     "La souveraineté interne", ["La souveraineté externe", "La souveraineté partagée", "La souveraineté confédérale"],
     "Le pouvoir de commander à tous sur le territoire, sans concurrent, est la souveraineté interne.", 2),
    ("Un État signe un traité par lequel il s'engage à respecter des limites. A-t-il perdu sa souveraineté ?",
     "Non, il l'a exercée en s'engageant volontairement", ["Oui, toute obligation internationale supprime la souveraineté", "Oui, dès qu'un traité est signé", "Non, car un traité n'engage jamais"],
     "S'engager par traité est un acte de souveraineté, non sa perte.", 4),
    ("Le peuple de l'État Zeta élit ses représentants et ceux-ci ne peuvent être révoqués par les électeurs entre deux élections. Quelle théorie y est liée ?",
     "La souveraineté nationale", ["La souveraineté populaire", "La souveraineté du monarque", "La souveraineté de Dieu"],
     "Dans la conception nationale, la souveraineté appartient à la nation, entité abstraite, et le mandat est représentatif.", 4),
    ("Dans un pays, chaque citoyen détient une fraction de la souveraineté et peut voter directement sur les lois. Quelle conception est la plus proche ?",
     "La souveraineté populaire", ["La souveraineté nationale", "La souveraineté monarchique", "La souveraineté de l'assemblée"],
     "La souveraineté populaire est fractionnée entre les citoyens ; elle justifie le suffrage universel et des procédures de démocratie directe.", 4),
    ("Deux États décident de fusionner et forment un seul État. Que devient le territoire de chacun ?",
     "Il fait partie du nouvel État unique", ["Il reste séparé et souverain", "Il devient une colonie", "Il disparaît sans propriétaire"],
     "La fusion crée un seul État.", 3),
    ("Dans un État, une minorité linguistique demande des droits culturels particuliers sans vouloir quitter l'État. Quelle notion est ici mobilisée ?",
     "La protection des minorités", ["La souveraineté confédérale", "La question de confiance", "L'état de siège"],
     "Il s'agit de droits culturels dans l'État, non de sécession.", 3),
    ("Une région demande à quitter l'État et à fonder le sien. Quel principe invoque-t-elle le plus souvent ?",
     "Le droit des peuples à disposer d'eux-mêmes", ["Le principe de légalité", "L'inviolabilité parlementaire", "Le principe du contradictoire"],
     "C'est un principe reconnu en droit international, dont la portée est discutée en cas de sécession.", 4),
    ("Dans un État, un citoyen possède la nationalité de l'État et un résident étranger n'en possède pas. Quel droit politique est en principe réservé au citoyen ?",
     "Le droit de vote aux élections nationales", ["Le droit de propriété", "Le droit de se marier", "Le droit de se défendre en justice"],
     "Les droits politiques, comme voter et être élu, sont en principe liés à la nationalité.", 2),
    ("Une personne n'a la nationalité d'aucun État. Comment la qualifie-t-on ?",
     "Apatride", ["Binationale", "Naturalisée", "Réfugiée de plein droit"],
     "L'apatride n'est rattaché à aucun État par la nationalité.", 2),
    ("Une personne acquiert la nationalité d'un État par décision de l'autorité, après avoir résidé longtemps. Quel mode d'acquisition est décrit ?",
     "La naturalisation", ["La filiation", "Le droit du sol automatique", "L'adoption plénière automatique"],
     "La naturalisation est accordée par décision de l'État à un étranger qui la demande.", 2),
    ("Une personne est née sur le territoire d'un État et acquiert sa nationalité parce qu'elle y est née. Quel critère est appliqué ?",
     "Le droit du sol", ["Le droit du sang", "La naturalisation", "La déchéance"],
     "Le droit du sol rattache la nationalité au lieu de naissance.", 2),
    ("Une personne reçoit la nationalité d'un parent à la naissance, peu importe le lieu où elle est née. Quel critère est appliqué ?",
     "Le droit du sang", ["Le droit du sol", "La naturalisation", "Le mandat représentatif"],
     "Le droit du sang rattache la nationalité à la filiation.", 2),
    ("Un État reconnaît à ses collectivités locales une libre administration et unit son droit sous une Constitution unique. Comment le qualifie-t-on ?",
     "Un État unitaire décentralisé", ["Un État fédéral", "Une confédération d'États", "Un protectorat"],
     "Il a un seul ordre constitutionnel et des collectivités autonomes pour leurs affaires.", 3),
    ("Dans une confédération, un État membre souhaite quitter l'union et le traité le permet. Le peut-il ?",
     "Oui, car chaque État garde sa souveraineté", ["Non, la confédération est indissoluble", "Oui, mais seulement avec l'accord du juge", "Non, car la confédération est un État"],
     "Une confédération est fondée sur un traité entre États qui restent souverains.", 4),
    ("Un peuple vit sous l'autorité d'un État étranger qui s'est installé sur son territoire par la force. De quelle situation relève-t-on ?",
     "D'une occupation ou d'une domination étrangère", ["D'une décentralisation voulue par les autorités nationales", "D'une déconcentration des services de l'État central", "D'une cohabitation entre le chef de l'État et l'assemblée"],
     "Le droit international encadre l'occupation et le droit des peuples à disposer d'eux-mêmes.", 4),
])

# ============================================================================================================
# M. Intrus
M("intrus", [
    ("Lequel de ces éléments n'est pas une condition de validité d'un suffrage universel moderne ?",
     "Le versement d'un impôt minimal", ["Le secret du vote", "L'égalité des voix", "La liberté de choix"],
     "Le suffrage universel exclut les conditions de fortune, au contraire du suffrage censitaire.", 2),
    ("Lequel de ces mécanismes n'est pas un moyen d'action du parlement sur le gouvernement ?",
     "La dissolution de l'assemblée", ["La motion de censure", "La question orale", "La commission d'enquête"],
     "La dissolution est un moyen d'action de l'exécutif sur l'assemblée.", 3),
    ("Lequel de ces mécanismes n'est pas un moyen d'action de l'exécutif sur le parlement ?",
     "La motion de censure", ["La dissolution", "L'initiative des lois", "La promulgation des lois"],
     "La censure est un moyen d'action du parlement sur le gouvernement.", 3),
    ("Lequel de ces régimes n'est pas un régime de séparation souple des pouvoirs ?",
     "Le régime présidentiel de type américain", ["Le régime parlementaire", "Le régime semi-présidentiel", "La monarchie parlementaire"],
     "Le régime présidentiel de type américain pratique la séparation stricte des pouvoirs.", 4),
    ("Lequel de ces auteurs n'a pas écrit sur la séparation des pouvoirs ou la souveraineté ?",
     "Adam Smith", ["Montesquieu", "Jean Bodin", "Jean-Jacques Rousseau"],
     "Adam Smith est un économiste, auteur de La Richesse des nations.", 2),
    ("Lequel de ces droits n'est pas une liberté publique classique ?",
     "Le droit à un logement fourni par l'État", ["La liberté d'aller et venir", "La liberté de réunion", "La liberté de la presse"],
     "Un droit à une prestation est un droit-créance, non une liberté classique.", 3),
    ("Lequel de ces actes n'émane pas d'une autorité administrative ?",
     "Un jugement d'un tribunal", ["Un décret du chef du gouvernement", "Un arrêté d'un maire", "Un arrêté d'un ministre"],
     "Un jugement émane d'une juridiction, non de l'administration.", 2),
    ("Lequel de ces organes n'est pas une juridiction ?",
     "Le conseil municipal", ["Une cour d'appel", "Un tribunal de première instance", "Une cour suprême"],
     "Le conseil municipal est un organe délibérant d'une collectivité.", 2),
    ("Lequel de ces termes ne désigne pas un mode de scrutin ?",
     "Le quorum", ["Le scrutin uninominal", "Le scrutin de liste", "La représentation proportionnelle"],
     "Le quorum est un nombre minimal de présents pour délibérer.", 2),
    ("Lequel de ces éléments n'est pas un organe d'une collectivité territoriale décentralisée classique ?",
     "Le directeur d'un service ministériel déconcentré", ["Le conseil élu de la collectivité territoriale", "Le maire ou l'exécutif local de la collectivité", "Le bureau de l'organe délibérant de la collectivité"],
     "Un service déconcentré appartient à l'État, non à la collectivité.", 4),
    ("Laquelle de ces notions ne désigne pas un contrôle de constitutionnalité ?",
     "Le recours pour excès de pouvoir contre un arrêté", ["Le contrôle a priori de la loi avant sa promulgation", "Le contrôle a posteriori de la loi déjà promulguée", "L'exception d'inconstitutionnalité soulevée par un justiciable"],
     "Le recours pour excès de pouvoir vise un acte administratif et relève de la légalité.", 4),
    ("Lequel de ces textes n'est pas un instrument de protection internationale des droits de l'homme ?",
     "Le Code civil national", ["La Déclaration universelle de 1948", "Le Pacte relatif aux droits civils et politiques", "La Charte africaine des droits de l'homme et des peuples"],
     "Un code national relève du droit interne.", 3),
    ("Lequel de ces droits est un droit civil et politique plutôt qu'un droit économique et social ?",
     "Le droit de vote", ["Le droit à la santé", "Le droit à l'éducation", "Le droit au travail"],
     "Le droit de vote est un droit politique de la première génération.", 3),
    ("Lequel de ces éléments est un droit économique, social ou culturel plutôt qu'une liberté classique ?",
     "Le droit à l'éducation", ["La liberté d'expression", "La liberté de conscience", "L'inviolabilité du domicile"],
     "Les droits à l'éducation, à la santé ou au travail exigent des prestations de l'État.", 3),
    ("Laquelle de ces fonctions ne procède pas d'une élection ?",
     "La fonction d'un juge nommé", ["La fonction d'un maire élu", "La fonction d'un député élu", "La fonction d'un conseiller municipal élu"],
     "Les juges nommés ne tiennent pas leur fonction d'une élection.", 3),
    ("Lequel de ces dispositifs n'est pas un outil de démocratie directe ou semi-directe ?",
     "La motion de censure", ["Le référendum", "L'initiative populaire", "Le droit de révocation d'un élu"],
     "La censure est un mécanisme parlementaire, non une consultation du peuple.", 3),
    ("Lequel de ces éléments n'est pas une garantie de l'indépendance des juges ?",
     "La possibilité pour le ministre de les muter à sa guise", ["L'inamovibilité garantie aux magistrats du siège", "Un statut protecteur fixé par la loi pour les magistrats", "Un conseil de la magistrature chargé de leur carrière"],
     "Une mutation à la discrétion de l'exécutif menace l'indépendance.", 3),
    ("Lequel de ces pouvoirs n'est pas exercé, en principe, par le chef de l'État d'un régime parlementaire classique ?",
     "Conduire au quotidien la politique du gouvernement", ["Promulguer les lois adoptées par l'assemblée", "Accréditer les ambassadeurs auprès des États étrangers", "Nommer le chef du gouvernement après les élections"],
     "Le chef du gouvernement conduit la politique, sous le contrôle du parlement.", 4),
    ("Lequel de ces actes n'est pas un acte d'exécution des lois ?",
     "Le vote d'un amendement", ["Un décret d'application", "Un arrêté ministériel", "Une circulaire d'explication"],
     "L'amendement concerne l'élaboration de la loi.", 3),
    ("Lequel de ces systèmes n'est pas un système de scrutin proportionnel ?",
     "Le scrutin uninominal à un tour", ["Le plus fort reste", "La plus forte moyenne", "Le quotient électoral"],
     "Le scrutin uninominal à un tour est majoritaire.", 3),
    ("Lequel de ces droits n'est pas indérogeable dans les grands traités de protection des droits ?",
     "La liberté de réunion", ["L'interdiction de la torture", "L'interdiction de l'esclavage", "La liberté de pensée, de conscience et de religion"],
     "Les droits du noyau dur ne tolèrent aucune dérogation ; la liberté de réunion, elle, peut être restreinte.", 4),
    ("Laquelle de ces institutions n'est pas une institution de la Constitution du Cameroun de 1996 ?",
     "La Cour de justice de l'Union européenne", ["Le Sénat", "L'Assemblée nationale", "Le Conseil constitutionnel"],
     "Une institution européenne n'est pas une institution camerounaise.", 2),
    ("Lequel de ces textes est de rang inférieur à la loi ?",
     "Un arrêté municipal", ["Une loi organique", "La Constitution", "Un traité régulièrement ratifié en droit français"],
     "Un arrêté est un acte réglementaire inférieur à la loi.", 3),
    ("Lequel de ces moyens ne sert pas à contester une élection devant le juge électoral ?",
     "Une pétition au maire", ["Une irrégularité dans le décompte", "Une fraude constatée", "Une inéligibilité du candidat élu"],
     "Le juge électoral examine des vices de l'élection, non des demandes adressées à un maire.", 4),
])

# ============================================================================================================
# N. Ordre logique des étapes
M("ordre", [
    ("Quel est l'ordre logique de la procédure législative ordinaire dans un régime parlementaire ?",
     "Initiative, examen et vote, promulgation, publication", ["Initiative, publication, examen et vote, promulgation", "Promulgation, publication, examen et vote, initiative", "Examen et vote, publication, promulgation, initiative"],
     "On propose le texte, on le débat et on le vote, puis on le promulgue et on le publie.", 2),
    ("Quel est l'ordre correct pour un contrôle a priori d'une loi ?",
     "Vote de la loi, saisine du juge, décision, promulgation", ["Promulgation, décision, vote de la loi, saisine du juge", "Saisine du juge, décision, vote de la loi, promulgation", "Saisine du juge, promulgation, vote de la loi, décision"],
     "Le contrôle a priori précède l'entrée en vigueur : la loi n'est promulguée qu'après la décision.", 3),
    ("Quel est l'ordre correct pour qu'une motion de censure entraîne le départ du gouvernement ?",
     "Dépôt de la motion, vote, adoption, démission du gouvernement", ["Démission du gouvernement, dépôt de la motion, vote, adoption", "Adoption, dépôt de la motion, démission du gouvernement, vote", "Vote, dépôt de la motion, démission du gouvernement, adoption"],
     "La motion est déposée, votée, adoptée, puis le gouvernement en tire la conséquence.", 3),
    ("Quel est l'ordre correct dans un scrutin uninominal à deux tours où personne n'est élu au premier ?",
     "Premier tour, constat de l'absence de majorité, second tour, proclamation", ["Constat de l'absence de majorité, proclamation, second tour, premier tour", "Premier tour, constat de l'absence de majorité, proclamation, second tour", "Constat de l'absence de majorité, premier tour, proclamation, second tour"],
     "Le second tour n'a lieu que si le premier n'a pas permis de désigner l'élu.", 3),
    ("Quel est l'ordre correct pour une exception d'inconstitutionnalité soulevée en cours de procès ?",
     "Procès en cours, moyen soulevé, examen par la juridiction constitutionnelle, reprise du procès", ["Examen par la juridiction constitutionnelle, reprise du procès, moyen soulevé, procès en cours", "Moyen soulevé, reprise du procès, examen par la juridiction constitutionnelle, procès en cours", "Reprise du procès, moyen soulevé, procès en cours, examen par la juridiction constitutionnelle"],
     "Le moyen est posé pendant l'instance, puis transmis, puis le juge ordinaire en tire la conséquence.", 4),
    ("Quel est l'ordre correct pour qu'un traité produise effet en droit interne selon le schéma le plus courant ?",
     "Négociation, signature, ratification, publication", ["Ratification, publication, négociation, signature", "Publication, négociation, ratification, signature", "Négociation, ratification, publication, signature"],
     "Un traité est négocié, signé, ratifié par l'État, puis publié pour être opposable.", 3),
    ("Quel est l'ordre correct d'une élection avec contentieux ?",
     "Vote, proclamation des résultats, saisine du juge électoral, décision", ["Décision, vote, saisine du juge électoral, proclamation des résultats", "Décision, saisine du juge électoral, vote, proclamation des résultats", "Vote, saisine du juge électoral, décision, proclamation des résultats"],
     "Le contentieux porte sur des résultats déjà proclamés.", 3),
    ("Quel est l'ordre correct pour une révision constitutionnelle classique ?",
     "Initiative, adoption selon la procédure spéciale, promulgation", ["Adoption selon la procédure spéciale, initiative, promulgation", "Adoption selon la procédure spéciale, promulgation, initiative", "Initiative, promulgation, adoption selon la procédure spéciale"],
     "Le texte est d'abord proposé, puis adopté selon la procédure de révision, enfin promulgué.", 3),
    ("Quel est l'ordre correct lorsqu'un chef d'État dissout l'assemblée dans un régime parlementaire ?",
     "Décision de dissolution, convocation des électeurs, élections, installation de la nouvelle assemblée", ["Élections, convocation des électeurs, installation de la nouvelle assemblée, décision de dissolution", "Décision de dissolution, élections, installation de la nouvelle assemblée, convocation des électeurs", "Décision de dissolution, installation de la nouvelle assemblée, convocation des électeurs, élections"],
     "On dissout, on convoque, on vote, puis la nouvelle assemblée siège.", 3),
    ("Quel est l'ordre correct pour contester un acte d'une commune devant le juge dans un système de contrôle de légalité ?",
     "Adoption de l'acte, transmission à l'État, saisine du juge, décision", ["Adoption de l'acte, transmission à l'État, décision, saisine du juge", "Décision, transmission à l'État, saisine du juge, adoption de l'acte", "Décision, saisine du juge, transmission à l'État, adoption de l'acte"],
     "L'acte est pris, communiqué, puis éventuellement attaqué devant le juge.", 4),
    ("Quel est l'ordre logique d'un référendum de ratification d'une réforme ?",
     "Question posée au peuple, campagne, vote, proclamation du résultat", ["Campagne, vote, proclamation du résultat, question posée au peuple", "Question posée au peuple, campagne, proclamation du résultat, vote", "Campagne, vote, question posée au peuple, proclamation du résultat"],
     "On pose d'abord la question ; le résultat vient en dernier.", 2),
    ("Quel est l'ordre correct d'une procédure d'ordonnance sur habilitation ?",
     "Loi d'habilitation, ordonnance, ratification par le parlement", ["Ratification par le parlement, ordonnance, loi d'habilitation", "Loi d'habilitation, ratification par le parlement, ordonnance", "Ordonnance, ratification par le parlement, loi d'habilitation"],
     "Le parlement habilite d'abord le gouvernement, qui prend l'ordonnance, laquelle est ensuite ratifiée.", 4),
])

# ============================================================================================================
# O. Distinctions voisines
M("distinct", [
    ("Le parlement vote une loi nouvelle qui supprime une ancienne loi devenue inadaptée. Quel terme convient ?",
     "L'abrogation", ["L'annulation", "L'amnistie", "La cassation"],
     "L'auteur de la norme la fait disparaître pour l'avenir.", 3),
    ("Le chef de l'État signe un texte pour constater qu'il a été voté et ordonner son exécution. Quel acte accomplit-il ?",
     "La promulgation", ["La publication", "L'abrogation", "La ratification"],
     "La promulgation atteste l'existence de la loi et la rend exécutoire.", 2),
    ("Un texte promulgué est inséré dans le journal officiel pour que le public le connaisse. Quel acte est accompli ?",
     "La publication", ["La promulgation", "La ratification", "L'amendement"],
     "La publication porte la loi à la connaissance de tous, condition de son opposabilité.", 2),
    ("Un électeur se déplace et dépose un bulletin sans nom ; un autre ne se déplace pas ; un troisième dépose un bulletin déchiré inutilisable. Lequel s'abstient ?",
     "Celui qui ne se déplace pas", ["Celui qui dépose un bulletin sans nom", "Celui qui dépose un bulletin déchiré", "Aucun des trois"],
     "L'abstention consiste à ne pas participer ; le bulletin blanc et le bulletin nul sont des votes non exprimés.", 3),
    ("Un candidat obtient plus de voix que chacun des autres mais moins de la moitié des suffrages. Quelle majorité a-t-il ?",
     "Une majorité relative", ["Une majorité absolue", "Une majorité qualifiée", "Une majorité unanime"],
     "La majorité relative est le plus grand nombre de voix, sans dépasser la moitié.", 2),
    ("Un candidat obtient plus de la moitié des suffrages exprimés. Quelle majorité a-t-il ?",
     "Une majorité absolue", ["Une majorité relative", "Une majorité qualifiée", "Une majorité unanime"],
     "La majorité absolue dépasse la moitié des suffrages exprimés.", 2),
    ("Un texte doit réunir une majorité renforcée, supérieure à la majorité absolue, pour être adopté. Comment la qualifie-t-on ?",
     "Une majorité qualifiée", ["Une majorité relative", "Une majorité simple", "Une majorité implicite"],
     "Une majorité qualifiée est supérieure à la majorité absolue, fixée par le texte.", 3),
    ("Un électeur vote pour un parti, mais l'élu qu'il a choisi change de parti après l'élection et conserve son siège. Quelle règle de principe protège cette liberté de l'élu ?",
     "L'interdiction du mandat impératif", ["Le caractère direct du suffrage", "L'inviolabilité parlementaire", "Le secret du vote"],
     "Le mandat représentatif exclut l'obligation juridique de suivre les consignes d'un parti ou d'un électeur.", 5),
    ("Quelle différence y a-t-il entre un référendum et un plébiscite, au sens classique ?",
     "Le référendum porte sur un texte ou une question ; le plébiscite sur la personne d'un dirigeant", ["Le référendum est toujours local, le plébiscite toujours national", "Le référendum est un vote indirect, le plébiscite un vote direct", "Ces mots désignent exactement la même procédure juridique"],
     "Le plébiscite engage la confiance dans une personne, le référendum une décision.", 4),
    ("Une action est portée devant un juge pour faire annuler un acte de l'administration. Comment qualifie-t-on ce recours ?",
     "Un recours contentieux", ["Un recours gracieux", "Un recours politique", "Un recours parlementaire"],
     "Le recours contentieux s'adresse à un juge ; le recours gracieux à l'auteur de l'acte.", 3),
    ("Un citoyen écrit à l'auteur d'une décision pour lui demander de la retirer, sans saisir un juge. Quel recours exerce-t-il ?",
     "Un recours gracieux", ["Un recours contentieux", "Un recours en cassation", "Un recours en révision"],
     "Le recours gracieux est adressé à l'autorité même qui a pris la décision.", 3),
    ("Un citoyen écrit au supérieur hiérarchique de l'auteur de la décision pour lui demander de l'annuler. Quel recours exerce-t-il ?",
     "Un recours hiérarchique", ["Un recours gracieux", "Un recours contentieux", "Un recours constitutionnel"],
     "Le recours hiérarchique est adressé à l'autorité supérieure de celui qui a décidé.", 4),
    ("Une loi est dite « organique ». En quoi se distingue-t-elle de la loi ordinaire ?",
     "Elle précise la Constitution et suit une procédure particulière", ["Elle est inférieure aux décrets pris par le gouvernement", "Elle émane directement du juge constitutionnel saisi", "Elle n'a jamais besoin d'être promulguée par le chef de l'État"],
     "Les lois organiques complètent la Constitution sur l'organisation des pouvoirs publics.", 3),
    ("Une Constitution est la source suprême du droit de l'État. Qu'est-ce qui la distingue d'un traité entre États ?",
     "Elle émane de l'ordre interne de l'État, le traité d'un accord entre États", ["Elle est signée par plusieurs États, le traité par un seul État", "Elle est valable sans publication, le traité exige un vote populaire", "Elle ne peut jamais être révisée, alors que le traité le peut"],
     "La Constitution organise l'ordre interne ; le traité relève du droit international.", 3),
    ("Qu'est-ce qui distingue la légitimité de la légalité d'un pouvoir ?",
     "La légalité tient au respect du droit ; la légitimité à l'acceptation du pouvoir", ["La légalité relève de la morale, la légitimité d'un texte écrit", "Elles sont toujours identiques et se confondent en droit", "La légitimité vient du juge, la légalité du seul peuple"],
     "Un pouvoir peut être légal sans être légitime, ou l'inverse.", 5),
    ("Qu'est-ce qui distingue une nation d'un État dans la pensée classique ?",
     "La nation est une communauté humaine ; l'État est une organisation juridique du pouvoir", ["La nation est toujours souveraine, l'État est une entité purement locale", "La nation est une personne juridique, l'État une simple communauté", "Ces notions sont strictement identiques dans la pensée classique"],
     "La nation tient à des liens culturels ou politiques ; l'État est la forme juridique du pouvoir.", 4),
    ("En quoi le gouvernement se distingue-t-il de l'État ?",
     "Le gouvernement est l'équipe au pouvoir ; l'État est la personne juridique permanente", ["Le gouvernement est permanent, l'État change à chaque élection", "Le gouvernement est un territoire, l'État est un ministère", "Il n'existe aucune différence entre ces deux notions en droit"],
     "Les gouvernements changent ; l'État reste.", 3),
    ("Dans quel cas parle-t-on de démocratie représentative plutôt que semi-directe ?",
     "Lorsque le peuple ne décide que par l'intermédiaire de représentants élus", ["Lorsque le peuple vote directement des lois sur demande", "Lorsque le chef de l'État vote seul à la place de tous", "Lorsque l'armée contrôle l'organisation des élections"],
     "Dans la démocratie représentative, les citoyens exercent le pouvoir surtout par leurs élus.", 3),
    ("Un électeur est en même temps appelé à voter pour des représentants et à trancher une loi par référendum. De quel système relève-t-il ?",
     "De la démocratie semi-directe", ["De la démocratie purement représentative", "De la démocratie par tirage au sort uniquement", "De la monarchie"],
     "Le peuple combine élection de représentants et procédures directes.", 3),
    ("Un veto du chef de l'État que le parlement peut surmonter par une majorité renforcée est dit comment ?",
     "Un veto suspensif", ["Un veto absolu", "Un veto constitutionnel", "Un veto populaire"],
     "Un veto suspensif retarde la loi ; absolu, il la bloque définitivement.", 4),
    ("Quel est le lien entre souveraineté et indépendance de l'État ?",
     "L'indépendance est l'aspect externe de la souveraineté", ["L'indépendance est l'aspect interne de la souveraineté", "L'indépendance est un synonyme de la décentralisation", "L'indépendance s'oppose à la souveraineté"],
     "La souveraineté externe signifie que l'État n'est subordonné à aucun autre.", 3),
    ("Un parti politique, un syndicat et une association : lequel vise à conquérir le pouvoir par les élections ?",
     "Le parti politique", ["Le syndicat", "L'association culturelle", "Le groupe de pression"],
     "Le parti présente des candidats pour exercer le pouvoir ; les autres défendent des intérêts ou des objectifs particuliers.", 2),
    ("Un groupement cherche à influencer des décisions publiques sans présenter de candidats aux élections. Comment le qualifie-t-on ?",
     "Un groupe de pression", ["Un parti politique", "Un exécutif collégial", "Une assemblée constituante"],
     "Le groupe de pression exerce une influence sur le pouvoir sans chercher à l'exercer.", 3),
    ("Quel est le rapport entre la décentralisation et la démocratie locale ?",
     "La décentralisation permet l'élection d'organes locaux qui décident", ["La décentralisation supprime toutes les élections locales", "La décentralisation transfère le pouvoir à l'armée", "La décentralisation supprime la loi nationale"],
     "Des organes élus gèrent les affaires locales : la démocratie se rapproche des citoyens.", 3),
    ("Comment distinguer un acte réglementaire d'un acte individuel ?",
     "Le réglementaire pose une règle générale ; l'individuel vise une ou plusieurs personnes déterminées", ["Le réglementaire est toujours une loi, l'individuel toujours un traité", "Le réglementaire émane d'un juge, l'individuel du parlement seul", "Ils ne diffèrent que par leur date de publication au journal"],
     "Un règlement s'applique à des situations générales, une décision individuelle à un destinataire précis.", 3),
    ("Entre le décret et l'arrêté, lequel émane de l'autorité de rang le plus élevé dans l'exécutif national ?",
     "Le décret, signé par le chef de l'État ou du gouvernement", ["L'arrêté ministériel signé par un ministre de tutelle", "L'arrêté municipal pris par le maire de la commune", "La circulaire signée par un directeur d'administration"],
     "Le décret est un acte du chef de l'État ou du gouvernement ; l'arrêté émane de ministres ou d'autorités locales.", 3),
    ("Dans quel cas un acte de police administrative est-il préventif et non répressif ?",
     "Quand il vise à éviter un trouble à venir", ["Quand il condamne une infraction déjà commise", "Quand il fixe une peine d'emprisonnement", "Quand il annule une élection"],
     "La police administrative prévient ; la police judiciaire et le juge répriment.", 3),
    ("Qui, de la police administrative et de la police judiciaire, a pour but de rechercher les auteurs d'une infraction ?",
     "La police judiciaire", ["La police administrative", "La police municipale d'ordre", "La police des frontières seule"],
     "La police judiciaire intervient après l'infraction pour constater et en rechercher les auteurs.", 3),
])

# ============================================================================================================
# P. Quelle juridiction, quelle autorité ?
M("juge", [
    ("Un particulier veut faire annuler un arrêté d'un maire qu'il juge illégal. Quel type de juge est en principe compétent ?",
     "Le juge administratif", ["Le juge pénal", "Le juge constitutionnel", "Le juge de l'exécution des peines"],
     "Le contentieux de la légalité des actes administratifs relève du juge administratif.", 2),
    ("Un candidat conteste le décompte des voix d'une élection locale. Quel juge est en principe saisi ?",
     "Le juge électoral", ["Le juge de l'application des peines", "Le juge des enfants", "Le juge commercial"],
     "Le contentieux des opérations électorales relève d'un juge spécialisé.", 2),
    ("Deux particuliers se disputent la propriété d'un terrain. Quel type de juge est en principe compétent ?",
     "Le juge judiciaire civil", ["Le juge constitutionnel", "Le juge administratif exclusivement", "Le parlement"],
     "Un litige entre particuliers relève du juge judiciaire.", 2),
    ("Un parlementaire souhaite faire vérifier la conformité d'une loi avant sa promulgation. Quelle autorité saisit-il en droit comparé typique ?",
     "La juridiction constitutionnelle", ["Le juge des référés commercial", "Le tribunal de simple police", "Le maire"],
     "Le contrôle de constitutionnalité est confié à une juridiction ou à un conseil spécialisé.", 3),
    ("Un citoyen veut obtenir réparation d'un dommage causé par un service public de l'État. Devant quel juge va-t-il en principe, en droit français ?",
     "Le juge administratif", ["Le juge pénal", "La juridiction constitutionnelle", "Le parlement"],
     "La responsabilité de la puissance publique relève en principe du juge administratif.", 3),
    ("Un citoyen veut obtenir la condamnation d'un voleur. Devant quel type de juridiction va-t-il ?",
     "Une juridiction pénale", ["Une juridiction administrative", "Une juridiction constitutionnelle", "Un conseil municipal"],
     "Les infractions relèvent des juridictions pénales.", 2),
    ("Une loi prévoit un juge unique pour contrôler toutes les élections nationales. Que fait ce juge si une irrégularité grave est avérée ?",
     "Il peut annuler l'élection ou corriger le résultat", ["Il remplace lui-même les électeurs défaillants du bureau", "Il dissout le parlement élu à l'issue du scrutin", "Il modifie la Constitution pour valider le scrutin"],
     "Le juge électoral peut annuler une élection ou rectifier les résultats lorsque la loi le permet.", 3),
    ("Un ministre est soupçonné d'une faute commise dans l'exercice de ses fonctions ; certaines Constitutions prévoient une juridiction spéciale. Pourquoi ?",
     "Parce que la fonction ministérielle justifie une procédure particulière", ["Parce que le ministre est placé au-dessus de la loi pénale", "Parce que le juge ordinaire n'existe pas dans ce pays", "Parce que le ministre est toujours élu au suffrage direct"],
     "Une juridiction particulière peut juger les actes accomplis dans l'exercice de fonctions publiques.", 4),
    ("Un étranger conteste l'application d'une mesure de refus de séjour par une préfecture. Quel juge est en principe compétent pour examiner la légalité de cette décision ?",
     "Le juge administratif", ["Le juge de la famille", "Le juge commercial", "Le juge de l'exécution des peines"],
     "La décision d'une autorité administrative est contestée devant le juge administratif.", 3),
    ("Une personne est arrêtée et demande à un juge de dire si la détention est régulière. Quelle liberté cette demande protège-t-elle ?",
     "La liberté individuelle", ["La liberté d'association", "La liberté de la presse", "Le droit de vote"],
     "Le juge est le gardien de la liberté individuelle contre les détentions arbitraires.", 3),
    ("Une loi interdit à un tribunal de connaître des recours contre les arrêtés d'un ministre. Quelle exigence est mise à mal ?",
     "L'accès au juge", ["La séparation des Églises et de l'État", "Le suffrage universel", "La publicité des débats"],
     "Chacun doit pouvoir saisir un juge pour contester une décision qui l'atteint.", 4),
    ("Un syndicat veut faire juger la légalité d'une circulaire qui ajoute des règles aux textes. Devant quel juge peut-il en principe attaquer cette circulaire dans un système de droit administratif ?",
     "Le juge administratif", ["Le juge pénal", "Le juge de la famille", "Le juge constitutionnel"],
     "Une circulaire qui crée des règles nouvelles est un acte administratif contestable.", 4),
])

# ============================================================================================================
# Q. Droits de l'homme : textes et cas
M("droits", [
    ("Quel texte proclame, au niveau mondial, un ensemble de droits et libertés pour tous les êtres humains, adopté en 1948 par l'Assemblée générale des Nations unies ?",
     "La Déclaration universelle des droits de l'homme", ["Le Pacte de la Société des Nations", "La Charte de l'Organisation de l'unité africaine", "La Convention de Genève sur les réfugiés"],
     "Elle fonde le droit international des droits de l'homme.", 2),
    ("Un État ratifie un traité de protection des droits de l'homme. Qui peut ensuite lui demander des comptes dans le cadre de ce traité ?",
     "Les organes de contrôle prévus par le traité et les autres États parties", ["Seulement le chef de l'État de l'État concerné, par décret", "Aucune autorité, car les traités ne prévoient aucun contrôle", "Seulement le maire de la capitale de l'État en cause"],
     "Les traités prévoient des mécanismes de contrôle, comités ou juridictions.", 4),
    ("Une personne dont les droits sont violés épuise les recours internes sans succès, puis saisit une juridiction régionale de protection des droits. Quelle condition de recevabilité cela illustre-t-il ?",
     "L'épuisement préalable des voies de recours internes", ["La ratification préalable du budget de l'État concerné", "Le consentement exprès du parlement de l'État concerné", "L'unanimité des chambres de l'État sur l'affaire"],
     "Le recours international suppose en principe d'avoir d'abord tenté les recours nationaux.", 4),
    ("Un texte reconnaît aux peuples le droit à leur développement et à un environnement satisfaisant. À quelle génération de droits rattache-t-on ces droits ?",
     "À la troisième génération, les droits de solidarité", ["À la première génération, les droits civils", "À la deuxième génération, les droits sociaux seulement", "À la quatrième génération, les droits fiscaux"],
     "Les droits de solidarité sont des droits collectifs comme le développement, la paix, l'environnement.", 4),
    ("Un texte garantit le droit de vote et la liberté d'expression. À quelle génération de droits appartient-il typiquement ?",
     "À la première génération, les droits civils et politiques", ["À la deuxième génération, les droits économiques et sociaux", "À la troisième génération, les droits de solidarité", "À la dernière génération, les droits de la procédure civile"],
     "Les droits civils et politiques forment la première génération.", 3),
    ("Un texte garantit le droit au travail, à la santé et à l'éducation. À quelle génération appartient-il typiquement ?",
     "À la deuxième génération, les droits économiques et sociaux", ["À la première génération, les droits civils et politiques", "À la troisième génération, les droits de solidarité", "À la quatrième génération, les droits de la procédure"],
     "Ces droits supposent des prestations de l'État.", 3),
    ("Un État refuse d'appliquer un traité de droits de l'homme en invoquant une disposition de son droit interne. Que dit le droit international en principe ?",
     "Un État ne peut invoquer son droit interne pour justifier la violation d'un traité", ["Son droit interne l'emporte toujours sur l'engagement pris", "Le traité est alors suspendu de plein droit à son égard", "Aucun juge international ne peut se prononcer sur ce point"],
     "Pacta sunt servanda : un traité doit être exécuté de bonne foi, sans excuse tirée du droit interne.", 4),
    ("Une Constitution reconnaît dans son préambule l'attachement à la Déclaration universelle de 1948. Quelle valeur peut en tirer le juge constitutionnel ?",
     "Une référence pour interpréter et protéger les droits fondamentaux", ["Une obligation d'abroger la Constitution en vigueur", "Une interdiction définitive de réviser le texte constitutionnel", "Une compétence du juge sur les traités des États étrangers"],
     "Le préambule peut servir de fondement à la protection des droits si la Constitution l'admet.", 4),
    ("Une victime dont les droits ont été violés souhaite obtenir réparation. Quelle exigence essentielle suppose un recours effectif ?",
     "Un recours devant une autorité indépendante capable de sanctionner la violation", ["Un recours devant le ministre concerné, qui décide seul sans appel", "Un recours sans aucune décision contraignante à la clé", "Un recours exclusivement diplomatique entre deux États"],
     "L'effectivité suppose indépendance et pouvoir de remédier.", 4),
    ("Un État refuse d'accueillir une personne persécutée dans son pays d'origine et la renvoie là-bas. Quel principe général de protection est en jeu ?",
     "Le principe de non-refoulement", ["Le principe de souveraineté fiscale", "Le principe de subsidiarité", "Le principe de laïcité"],
     "Il interdit de renvoyer une personne vers un lieu où elle risque des persécutions ou des mauvais traitements.", 4),
    ("Une personne est traitée de manière dégradante pendant sa garde à vue. Quel droit fondamental est visé ?",
     "L'interdiction des traitements inhumains ou dégradants", ["La liberté d'association et de réunion pacifique", "Le droit de pétition adressé aux autorités publiques", "La liberté du commerce et de l'industrie"],
     "L'interdiction de ces traitements fait partie du noyau dur des droits fondamentaux.", 3),
    ("Un tribunal refuse d'écouter un témoin cité par l'accusé sans motif. Quel principe est mis en cause ?",
     "Les droits de la défense", ["Le secret du vote", "La souveraineté nationale", "La décentralisation"],
     "L'accusé doit pouvoir faire entendre ses arguments et ses témoins.", 3),
    ("Une loi autorise la détention de personnes pendant une durée illimitée sans jugement. Quelle atteinte est caractérisée ?",
     "Une atteinte à la liberté individuelle et à un procès équitable", ["Une atteinte à la décentralisation des compétences locales", "Une atteinte au suffrage universel direct des électeurs", "Une atteinte à la seule liberté d'association"],
     "La détention sans jugement ni limite méconnaît les garanties procédurales essentielles.", 3),
    ("Un État refuse à une catégorie de ses citoyens l'accès aux emplois publics à cause de leur origine. Quel principe est violé ?",
     "Le principe de non-discrimination", ["Le principe de laïcité", "Le principe du contradictoire", "Le principe de séparation des pouvoirs"],
     "Aucune différence de traitement ne peut se fonder sur l'origine de la personne.", 2),
])

# ============================================================================================================
# R. Partis, référendum, élections : cas
M("partis", [
    ("Un pays autorise un seul parti et interdit toute autre organisation politique. Comment qualifie-t-on ce système ?",
     "Le monopartisme", ["Le bipartisme", "Le multipartisme", "La démocratie semi-directe"],
     "Un seul parti est autorisé : c'est le système du parti unique.", 2),
    ("Deux grands partis se disputent presque tous les sièges et alternent au pouvoir. Quel système de partis est décrit ?",
     "Le bipartisme", ["Le monopartisme", "Le multipartisme intégral", "Le parti dominant"],
     "Deux partis dominent la vie politique.", 2),
    ("Dans un pays, de nombreux partis obtiennent des sièges, et aucun n'a seul la majorité. Quel système de partis est décrit ?",
     "Le multipartisme", ["Le monopartisme", "Le bipartisme", "Le parti unique"],
     "Beaucoup de partis comptent, ce qui impose souvent des coalitions.", 2),
    ("Un parti est financé par une entreprise privée qui lui verse des sommes très élevées sans aucune transparence. Quel problème soulève cette situation ?",
     "Le risque d'influence de l'argent sur la politique", ["Un défaut de promulgation des lois de finances", "Une atteinte à l'inamovibilité des magistrats du siège", "Une rupture du contreseing ministériel obligatoire"],
     "La réglementation du financement vise la transparence et l'égalité entre les partis.", 3),
    ("Le peuple est appelé à voter par oui ou non sur un projet de loi précis proposé par une autorité. Quelle procédure est utilisée ?",
     "Un référendum", ["Une motion de censure", "Une question orale", "Une promulgation"],
     "Le référendum permet au peuple de se prononcer directement sur une question ou un texte.", 2),
    ("Un référendum dont le résultat ne lie pas juridiquement les autorités est qualifié de ?",
     "Consultatif", ["Décisionnel", "Constituant", "Obligatoire"],
     "Un référendum consultatif donne un avis sans engager juridiquement.", 3),
    ("Un référendum dont le résultat s'impose juridiquement est qualifié de ?",
     "Décisionnel", ["Consultatif", "Facultatif", "Partiel"],
     "Il lie les autorités qui l'ont organisé.", 3),
    ("Une Constitution impose qu'un texte soit soumis au peuple. L'autorité n'a aucun choix sur l'organisation du vote. Quel type de référendum est-ce ?",
     "Un référendum obligatoire", ["Un référendum facultatif", "Un référendum consultatif", "Un plébiscite"],
     "Le droit impose lui-même la consultation du peuple.", 3),
    ("Une autorité décide librement, selon son opportunité, d'organiser ou non un référendum sur une réforme. Quel type de référendum est-ce ?",
     "Un référendum facultatif", ["Un référendum obligatoire", "Un référendum d'initiative populaire", "Un référendum de plein droit"],
     "L'organisation est laissée à l'appréciation d'une autorité.", 3),
    ("Un groupe de citoyens réunit des signatures pour obliger l'autorité à organiser un vote sur une proposition. Quelle procédure exerce-t-il ?",
     "L'initiative populaire", ["La motion de censure", "La question de confiance", "La grâce"],
     "L'initiative populaire permet aux citoyens de déclencher une consultation.", 3),
    ("Un parti qui a obtenu la moitié des sièges plus un forme seul le gouvernement. Que dit-on de la situation ?",
     "Il dispose de la majorité absolue des sièges", ["Il a la majorité relative seulement", "Il est minoritaire", "Il est en cohabitation"],
     "Plus de la moitié des sièges lui donne la majorité absolue.", 2),
    ("Un élu quitte son parti après avoir été élu sur sa liste et siège désormais seul. Comment qualifie-t-on souvent ce parlementaire ?",
     "Un non-inscrit ou indépendant", ["Un suppléant", "Un contresignataire", "Un sénateur de droit"],
     "Il n'appartient plus à un groupe parlementaire.", 3),
    ("Une loi interdit à un candidat de dépenser davantage qu'un montant fixé pour sa campagne. Quel objectif poursuit-elle ?",
     "L'égalité des candidats et la transparence", ["La suppression du vote", "La centralisation des élections", "La séparation des Églises et de l'État"],
     "Plafonner les dépenses limite l'influence de l'argent sur le résultat.", 3),
    ("Des observateurs indépendants assistent au dépouillement d'un scrutin pour en constater la régularité. Quel principe électoral cela renforce-t-il ?",
     "La sincérité du scrutin", ["La rétroactivité des lois", "L'inamovibilité des juges", "La libre administration"],
     "La présence d'observateurs contribue à la transparence et à la confiance dans le résultat.", 3),
    ("Une commission électorale indépendante organise les élections au lieu du ministère de l'Intérieur. Quel objectif est visé ?",
     "Garantir l'impartialité de l'organisation des élections", ["Supprimer le suffrage universel", "Remplacer le juge électoral", "Transférer la compétence au chef de l'État"],
     "L'indépendance de l'organisme limite le soupçon de partialité.", 3),
    ("Un candidat refuse de reconnaître sa défaite et attaque les résultats. Quelle voie normale s'offre à lui ?",
     "Saisir le juge électoral d'une contestation", ["Dissoudre lui-même l'assemblée nouvellement élue", "Annuler lui-même l'élection qu'il a perdue", "Modifier la Constitution pour changer le résultat"],
     "Le contentieux électoral est la voie juridique pour contester un scrutin.", 2),
])

# ============================================================================================================
# S. Cameroun : organisation générale (cas)
M("cameroun", [
    ("Une région camerounaise adopte une « constitution régionale » qui contredit la Constitution nationale. Dans l'État unitaire décentralisé du Cameroun, que dit-on d'une telle initiative ?",
     "Une collectivité décentralisée n'a pas de constitution propre", ["Elle l'emporte sur la Constitution nationale en cas de conflit", "Elle est valable si les habitants de la région l'approuvent", "Elle est l'équivalent d'une loi fédérale dans l'État"],
     "Seul l'État unitaire a une Constitution ; les collectivités agissent dans le cadre fixé par elle et par la loi.", 3),
    ("Le Parlement camerounais est composé de l'Assemblée nationale et du Sénat. Comment qualifie-t-on ce système à deux chambres ?",
     "Le bicamérisme", ["L'unicamérisme", "Le monocéphalisme", "La confédération"],
     "Deux assemblées participent au vote de la loi.", 2),
    ("Une loi camerounaise confie à une commune la gestion des marchés locaux. Selon l'organisation générale, à quel mouvement correspond ce transfert ?",
     "À la décentralisation", ["À la déconcentration", "À la fédération", "À la confédération"],
     "La commune est une collectivité territoriale décentralisée du Cameroun.", 3),
    ("Un représentant du pouvoir central nommé dans une circonscription du Cameroun agit sous l'autorité hiérarchique de l'État. À quelle logique relève-t-il ?",
     "À la déconcentration", ["À la décentralisation", "Au fédéralisme", "À la souveraineté populaire"],
     "Ses fonctions procèdent de la hiérarchie de l'État, non d'une collectivité décentralisée élue.", 4),
    ("Au Cameroun, une personne d'une des régions dit que sa région est un État fédéré. Quelle réponse est correcte ?",
     "Non, les régions sont des collectivités d'un État unitaire décentralisé", ["Oui, le Cameroun est une fédération de régions souveraines", "Oui, mais seulement pour la région de langue anglaise", "Non, car les régions sont de simples communes rurales"],
     "La Constitution de 1996 organise un État unitaire décentralisé, non fédéral.", 3),
], region="CM")

# ============================================================================================================
# T. Chef de l'État : responsabilité et immunités (principes généraux)
M("chefetat", [
    ("Dans un régime parlementaire, le chef de l'État n'est en principe pas responsable politiquement de ses actes. Qui répond alors de ceux-ci devant l'assemblée ?",
     "Le gouvernement, par le contreseing des ministres", ["Le juge constitutionnel, qui en répond lui-même", "Le chef de l'État, devant le peuple tout entier", "Les électeurs, directement et sans intermédiaire"],
     "Le contreseing transfère la responsabilité politique au ministre qui contresigne.", 3),
    ("Un chef d'État étranger en visite officielle est poursuivi par un tribunal local pour un acte de sa fonction. Que dit en principe le droit international coutumier ?",
     "Il bénéficie d'une immunité de juridiction en raison de sa fonction", ["Il est soumis aux tribunaux locaux comme tout particulier", "Il perd sa nationalité dès qu'il franchit la frontière", "Il doit être jugé par le parlement de l'État d'accueil"],
     "Les chefs d'État en exercice bénéficient d'immunités dans l'ordre international.", 4),
    ("Un diplomate accrédité commet une infraction. Que ne peut en principe pas faire l'État d'accueil ?",
     "Le poursuivre devant ses propres tribunaux", ["Le déclarer persona non grata", "Demander son rappel", "Demander la levée de son immunité"],
     "L'immunité diplomatique protège contre la juridiction locale, mais l'État d'accueil peut l'expulser.", 4),
    ("Un diplomate commet un acte grave dans l'État d'accueil. Quelle mesure cet État peut-il prendre contre lui ?",
     "Le déclarer persona non grata", ["Le juger par ses propres tribunaux", "Confisquer ses biens", "Annuler la convention de Vienne"],
     "La déclaration de persona non grata oblige l'État d'envoi à le rappeler ou à mettre fin à ses fonctions.", 4),
    ("Une juridiction internationale peut juger des crimes graves commis par des individus, y compris des dirigeants. Que traduit ce principe ?",
     "La responsabilité pénale individuelle internationale", ["La souveraineté absolue des États dans leurs affaires", "L'immunité parlementaire des élus nationaux", "La décentralisation des compétences pénales"],
     "Les crimes les plus graves engagent la responsabilité de leurs auteurs, quelles que soient leurs fonctions.", 4),
    ("Le chef d'un gouvernement se voit reprocher une mauvaise politique économique. Quel type de sanction est normal dans un régime parlementaire ?",
     "Une sanction politique, comme la censure ou la perte de confiance", ["Une condamnation pénale prononcée automatiquement par le juge", "Une amende fiscale infligée par l'administration", "Une annulation du budget de l'État par le juge administratif"],
     "La responsabilité politique se règle par le vote du parlement ou des électeurs, non par un procès pénal.", 3),
    ("Une loi prévoit qu'un ministre répond pénalement devant une juridiction ordinaire de délits commis en dehors de ses fonctions. Est-ce contraire à la séparation des pouvoirs ?",
     "Non, il est en principe soumis au droit commun pour ces actes", ["Oui, l'exécutif est placé au-dessus des lois pénales ordinaires", "Oui, mais seulement après un vote du parlement sur le dossier", "Non, car le ministre est lui-même le juge de ses actes"],
     "Les ministres répondent de leurs actes personnels comme tout justiciable.", 4),
    ("Un chef d'État donne l'ordre à l'armée de fermer le parlement par la force. Quelle qualification générale lui convient ?",
     "Une violation grave de la Constitution et un coup de force", ["Un exercice normal du droit de dissolution du parlement", "Une promulgation exceptionnelle d'une loi en urgence", "Un contreseing ministériel donné à titre provisoire"],
     "Seule la Constitution organise les pouvoirs et la dissolution ; un acte de force est contraire au droit.", 3),
])

# ============================================================================================================
# U. Sources du droit constitutionnel, Constitution
M("constit", [
    ("Dans un pays, une règle de fonctionnement des institutions n'est inscrite nulle part mais est respectée depuis toujours par tous, avec le sentiment d'être obligatoire. Comment la qualifie-t-on ?",
     "Une coutume constitutionnelle", ["Une loi organique", "Un traité", "Un règlement d'application"],
     "La coutume naît d'une pratique répétée perçue comme obligatoire.", 3),
    ("Un texte contient des règles sur l'organisation des pouvoirs mais n'est pas dans la Constitution écrite ; il a pourtant un caractère constitutionnel par son objet. Quel concept l'exprime ?",
     "La Constitution au sens matériel", ["La Constitution au sens formel", "Le décret d'application", "La loi budgétaire"],
     "Le sens matériel retient l'objet des règles ; le sens formel, la procédure et le document.", 4),
    ("Une règle figure dans le document de la Constitution, même si son objet est secondaire. À quelle Constitution cette règle appartient-elle ?",
     "À la Constitution au sens formel", ["À la Constitution au sens matériel seulement", "À aucune Constitution", "À la loi ordinaire"],
     "La forme est celle du texte constitutionnel et de sa procédure de révision.", 4),
    ("Un étudiant découvre qu'un pays n'a pas de document unique appelé Constitution, mais des lois et des pratiques politiques organisent ses institutions. Que peut-on en dire ?",
     "Il peut avoir une Constitution non codifiée", ["Il n'a pas d'institutions politiques organisées", "Il est une confédération d'États souverains", "Il n'a pas de véritable existence juridique d'État"],
     "Une Constitution peut résulter de textes épars, de coutumes et de conventions, sans document unique.", 3),
    ("Un texte précise que les droits de l'homme proclamés dans le préambule font partie de la Constitution. Quelle valeur ont ces droits ?",
     "Une valeur constitutionnelle", ["Une valeur de simple circulaire", "Une valeur de décret", "Aucune valeur juridique"],
     "L'intégration du préambule lui donne la valeur de la Constitution.", 3),
    ("Dans un État, la Constitution est modifiée plus facilement qu'une loi ordinaire. Quel risque cela pose-t-il pour la protection des droits ?",
     "La protection constitutionnelle devient fragile face aux majorités du moment", ["La Constitution devient plus protectrice pour les minorités", "Les juges perdent toute compétence en matière de droits", "L'État devient automatiquement un État fédéral"],
     "La rigidité protège la Constitution ; la souplesse l'expose aux changements de majorité.", 4),
    ("Un texte organise les pouvoirs publics et ne peut être modifié que par une procédure spéciale. Dans la hiérarchie des normes, où se situe-t-il ?",
     "Au sommet de l'ordre juridique interne", ["Sous la loi", "Sous le décret", "Au niveau d'une circulaire"],
     "La Constitution est la norme suprême de l'ordre interne.", 2),
])

# ============================================================================================================
# V. Laïcité, asile, étrangers, libertés : autres cas
M("libertes2", [
    ("Un État reconnaît aux croyants de toutes religions la liberté de culte, tout en ne favorisant aucune religion officielle. Quel principe est décrit ?",
     "La laïcité", ["La théocratie", "Le monopartisme", "La confédération"],
     "La laïcité suppose la neutralité de l'État et la liberté de croire ou de ne pas croire.", 2),
    ("Une loi impose à tous de pratiquer une religion déterminée. Quel principe est violé ?",
     "La liberté de conscience et de religion", ["La liberté du commerce", "Le droit de grève", "La séparation des pouvoirs"],
     "La contrainte religieuse porte atteinte à la liberté de croire ou de ne pas croire.", 2),
    ("Un étranger fuit son pays où il est persécuté pour ses opinions politiques, et sollicite une protection. Quelle notion générale est concernée ?",
     "Le droit d'asile", ["Le droit de grève", "L'inviolabilité parlementaire", "Le droit de vote"],
     "L'asile est la protection accordée à une personne persécutée par un autre État.", 3),
    ("Un État refuse de remettre à un autre État une personne qu'il réclame au motif d'infractions politiques. Quel mécanisme est en jeu ?",
     "L'extradition, qui peut être refusée dans certains cas", ["La promulgation, qui peut être différée par le parlement", "L'amnistie, qui peut être refusée par le chef de l'État", "La grâce, qui peut être refusée par la juridiction saisie"],
     "L'extradition est la remise d'une personne à un autre État pour jugement ou exécution de peine ; des refus sont possibles.", 4),
    ("Un journaliste est licencié pour avoir refusé de dévoiler ses sources. Quel principe général est le plus lié à cette situation ?",
     "La protection de la liberté d'information", ["La liberté du commerce des périodiques", "La liberté de religion des journalistes", "Le suffrage universel des électeurs inscrits"],
     "La protection des sources soutient le rôle de la presse dans une société démocratique.", 4),
    ("Un citoyen filme des policiers pendant une manifestation et diffuse l'enregistrement. Quelle liberté est en jeu ?",
     "La liberté d'expression et d'information", ["Le droit de grève", "La liberté d'enseignement", "Le droit de vote"],
     "La diffusion d'informations relève de la liberté d'expression, qui peut être limitée par la loi.", 3),
    ("Des parents inscrivent leur enfant dans une école privée de leur choix. Quelle liberté exercent-ils ?",
     "La liberté de l'enseignement", ["La liberté du commerce uniquement", "Le droit de pétition", "L'immunité"],
     "La liberté d'enseignement permet de créer des écoles et de choisir, dans le cadre légal.", 3),
    ("Une personne a été jugée définitivement pour un fait. Peut-on la juger à nouveau pour exactement le même fait ? Quel principe répond ?",
     "Non, en application de la règle non bis in idem", ["Oui, toujours", "Oui, mais seulement par le parlement", "Non, car le fait est amnistié"],
     "Nul ne peut être poursuivi deux fois pour les mêmes faits après une décision définitive.", 4),
    ("Une administration refuse de communiquer un document public à un citoyen sans en donner de motif légal. Quelle exigence démocratique est ici en cause ?",
     "La transparence de l'action publique", ["La séparation des Églises et de l'État", "La souveraineté confédérale", "L'élection du chef de l'État"],
     "L'accès aux documents administratifs participe du contrôle démocratique de l'action publique.", 3),
    ("Un citoyen est interrogé sur ses opinions politiques à l'embauche par un employeur public. Quelle liberté est en jeu ?",
     "La liberté d'opinion", ["La liberté du commerce", "Le droit de grève", "Le secret de la correspondance"],
     "Nul ne doit être inquiété pour ses opinions, sous réserve de l'ordre public.", 3),
    ("Un organisme public conserve des fichiers sur la vie privée de citoyens et les communique à des tiers. Quel droit est concerné ?",
     "Le droit au respect de la vie privée", ["Le droit de grève", "Le suffrage universel", "La liberté d'association"],
     "La protection des données personnelles découle du respect de la vie privée.", 3),
    ("Une personne est arrêtée sans qu'on lui ait dit pourquoi, et aucun juge n'est saisi. Quelle garantie fondamentale est ignorée ?",
     "L'information sur les motifs de l'arrestation et le contrôle du juge", ["Le droit de vote et l'éligibilité aux élections locales", "La liberté de commerce et d'industrie de l'intéressé", "La libre administration des collectivités territoriales"],
     "La liberté individuelle suppose information et contrôle d'une autorité judiciaire.", 3),
])
