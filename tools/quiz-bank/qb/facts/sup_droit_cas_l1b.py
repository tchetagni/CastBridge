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
