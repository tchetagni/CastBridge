"""Informatique du second cycle (2nde, 1re, Tle) et GCE Computer Science (Ordinary, Advanced Level), en deux langues.
Représentation des nombres, logique, algorithmique (les traces d'algorithme sont EXÉCUTÉES par Python), réseaux, bases de données."""
import math

from .. import lycee
from ..core import Draft
from .lycee_common import N, T, both

F2, F1, FT = lycee.fr("2nde", "info"), lycee.fr("1re", "info"), lycee.fr("tle", "info")
O5, A6, A12 = lycee.en("f5", "cs"), lycee.en("l6", "cs"), lycee.en("u6", "cs")
SRC_FR = "Programme MINESEC d'informatique - réponse calculée (les algorithmes sont exécutés)"
SRC_EN = "GCE Computer Science syllabus topic - answer computed (algorithms are executed)"
FR2, FR1, FRT = [(F2, "fr")], [(F1, "fr")], [(FT, "fr")]
EN_O, EN_A = [(O5, "en")], [(A6, "en"), (A12, "en")]
ALL_FR = FR2 + FR1 + FRT
EN_ALL = EN_O + EN_A


def S(lang):
    return SRC_FR if lang == "fr" else SRC_EN


def cat(lang, a, b):
    return a if lang == "fr" else b


def bits(n, w=0):
    return format(n, "b").zfill(w)


def near_bin(rng, n, w):
    out = []
    for v in (n + 1, n - 1, n ^ 1, n ^ 2, n ^ 4, (n << 1) % (1 << w), n >> 1 if n > 1 else n + 2, n ^ (1 << (w - 1))):
        if v != n and v >= 0 and bits(v, w) not in out:
            out.append(bits(v, w))
    return out


@both(FR2 + FR1 + EN_O, "inf-dec-to-bin", cap=240, cat="Représentation des nombres")
def dec_to_bin(rng, d, lang):
    n = rng.randint(5, 250)
    w = max(4, n.bit_length())
    assert int(bits(n), 2) == n
    return Draft(T(lang, f"Comment s'écrit {n} en base 2 ?", f"What is {n} in binary?"), bits(n), [b.lstrip("0") or "0" for b in near_bin(rng, n, w)], T(lang, "On divise successivement par 2 et on lit les restes de bas en haut.", "Divide repeatedly by 2 and read the remainders from the bottom up."),
                 src=S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"))


@both(FR2 + FR1 + EN_O, "inf-bin-to-dec", cap=240, cat="Représentation des nombres")
def bin_to_dec(rng, d, lang):
    n = rng.randint(5, 250)
    b = bits(n)
    wr = [n + 1, n - 1, int(b[::-1], 2) if int(b[::-1], 2) != n else n + 2, n * 2, n // 2 + 1, n + 2]
    return Draft(T(lang, f"Quelle est la valeur décimale du nombre binaire {b} ?", f"What is the denary (decimal) value of the binary number {b}?"), str(n), [str(w) for w in wr], T(lang, "On additionne les puissances de 2 correspondant aux bits à 1.", "Add the powers of 2 where the bit is 1."),
                 src=S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"))


@both(FR1 + FRT + EN_A, "inf-hex", cap=240, cat="Représentation des nombres")
def hexa(rng, d, lang):
    n = rng.randint(16, 4095)
    h = format(n, "X")
    if rng.random() < 0.5:
        wr = [format(n + k, "X") for k in (1, -1, 16, -16, 256, 2)] + [format(int(h[::-1], 16), "X")]
        return Draft(T(lang, f"Comment s'écrit {n} en hexadécimal ?", f"What is {n} in hexadecimal?"), h, [w for w in wr if w != h], T(lang, "On divise par 16 et on convertit les restes (10 = A … 15 = F).", "Divide by 16 and convert the remainders (10 = A … 15 = F)."), src=S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"))
    return Draft(T(lang, f"Quelle est la valeur décimale de 0x{h} (hexadécimal) ?", f"What is the denary value of the hexadecimal number {h}?"), str(n), [str(n + k) for k in (1, -1, 16, -16, 256, 10)], T(lang, "Chaque chiffre est multiplié par une puissance de 16.", "Each digit is multiplied by a power of 16."), src=S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"))


@both(FR1 + EN_A + EN_O, "inf-bin-add", cap=200, cat="Représentation des nombres")
def bin_add(rng, d, lang):
    a, b = rng.randint(3, 120), rng.randint(3, 120)
    s = a + b
    w = s.bit_length()
    wr = near_bin(rng, s, w) + [bits(a ^ b), bits(a | b)]
    wr = [x.lstrip("0") or "0" for x in wr]
    return Draft(T(lang, f"Quel est le résultat, en binaire, de {bits(a)} + {bits(b)} ?", f"What is {bits(a)} + {bits(b)} in binary?"), bits(s), [x for x in wr if x != bits(s)], T(lang, "On additionne colonne par colonne avec retenue (1 + 1 = 10).", "Add column by column with carries (1 + 1 = 10)."),
                 src=S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"), diff=3)


@both(FR1 + FRT + EN_A, "inf-twos-complement", cap=160, cat="Représentation des nombres")
def twos(rng, d, lang):
    n = rng.randint(1, 120)
    w = 8
    right = bits((1 << w) - n, w)
    wr = [bits(n, w), bits((1 << w) - n + 1, w) if n > 1 else bits(n + 1, w), bits(255 - n, w) if 255 - n != (1 << w) - n else bits(n + 2, w), "1" + bits(n, 7), bits((1 << w) - n - 1, w)]
    wr = [x for x in wr if x != right]
    assert int(right, 2) - 256 == -n
    return Draft(T(lang, f"Comment s'écrit −{n} en complément à deux sur 8 bits ?", f"How is −{n} represented in 8-bit two's complement?"), right, wr, T(lang, "On inverse tous les bits de la valeur absolue puis on ajoute 1.", "Invert all the bits of the positive value, then add 1."),
                 src=S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"), diff=4)


@both(FR2 + EN_O, "inf-units", cap=160, cat="Unités")
def units(rng, d, lang):
    k = rng.choice([2, 3, 4, 5, 8, 10, 16, 32, 64])
    typ = rng.choice(["ko-o", "mo-ko", "o-bits", "go-mo"])
    if typ == "ko-o":
        ans, txt = k * 1024, T(lang, f"{k} Ko (1 Ko = 1 024 octets) valent combien d'octets ?", f"How many bytes are in {k} KB (1 KB = 1024 bytes)?")
    elif typ == "mo-ko":
        ans, txt = k * 1024, T(lang, f"{k} Mo (1 Mo = 1 024 Ko) valent combien de Ko ?", f"How many KB are in {k} MB (1 MB = 1024 KB)?")
    elif typ == "o-bits":
        ans, txt = k * 8, T(lang, f"{k} octets valent combien de bits ?", f"How many bits are in {k} bytes?")
    else:
        ans, txt = k * 1024, T(lang, f"{k} Go (1 Go = 1 024 Mo) valent combien de Mo ?", f"How many MB are in {k} GB (1 GB = 1024 MB)?")
    return Draft(txt, N(lang, ans) if ans < 10000 else str(ans), [str(w) for w in (k * 1000, ans * 8, ans // 8 if ans % 8 == 0 else ans + 8, k * 100, ans + 24, k * 1024 * 1024)], T(lang, "Un octet = 8 bits ; chaque unité vaut 1 024 fois la précédente.", "A byte is 8 bits; each unit is 1024 times the previous one."),
                 src=S(lang), cat=cat(lang, "Unités", "Units"))


@both(FR2 + FR1 + EN_ALL, "inf-download", cap=200, cat="Réseaux")
def download(rng, d, lang):
    size, rate = rng.choice([10, 20, 50, 100, 200, 500, 1000]), rng.choice([1, 2, 4, 5, 8, 10, 20, 50])
    t = size * 8 / rate
    return Draft(T(lang, f"Un fichier de {size} Mo est téléchargé avec un débit de {rate} Mbit/s. Combien de secondes dure le téléchargement ? (1 octet = 8 bits)", f"A {size} MB file is downloaded at {rate} Mbit/s. How many seconds does it take? (1 byte = 8 bits)"),
                 N(lang, t, 2), [N(lang, w, 2) for w in (size / rate, size * rate, size * 8 * rate, size / (rate * 8), t * 2, t / 2)], "temps = taille en bits ÷ débit." if lang == "fr" else "time = size in bits ÷ rate.", src=S(lang), cat=cat(lang, "Réseaux", "Networks"))


@both(FR1 + FRT + EN_A, "inf-image-size", cap=160, cat="Représentation des données")
def image_size(rng, d, lang):
    w, h, depth = rng.choice([(100, 100), (640, 480), (800, 600), (1024, 768), (200, 50), (1920, 1080), (32, 32), (64, 64)]), None, None
    (w, h), depth = w if isinstance(w, tuple) else (w, h), rng.choice([1, 8, 16, 24])
    size_bytes = w * h * depth / 8
    return Draft(T(lang, f"Quelle est la taille, en octets, d'une image non compressée de {w} × {h} pixels codée avec {depth} bit(s) par pixel ?", f"What is the size, in bytes, of an uncompressed {w} × {h} pixel image with {depth} bit(s) per pixel?"),
                 N(lang, size_bytes, 0) if size_bytes == int(size_bytes) else N(lang, size_bytes, 1), [str(int(x)) if x == int(x) else N(lang, x, 1) for x in (w * h * depth, w * h * depth / 1024 / 8 if w * h * depth > 1 else 5, w * h, w * h * depth * 8, size_bytes * 2, size_bytes / 2)],
                 "taille = largeur × hauteur × profondeur de couleur ÷ 8." if lang == "fr" else "size = width × height × colour depth ÷ 8.", src=S(lang), cat=cat(lang, "Représentation des données", "Data representation"), diff=3)


@both(FRT + EN_A, "inf-sound-size", cap=100, cat="Représentation des données")
def sound_size(rng, d, lang):
    fs, bd, ch, t = rng.choice([8000, 11025, 22050, 44100]), rng.choice([8, 16]), rng.choice([1, 2]), rng.choice([2, 5, 10, 30, 60])
    size = fs * bd * ch * t / 8
    return Draft(T(lang, f"Un son est échantillonné à {fs} Hz sur {bd} bits, {'mono' if ch == 1 else 'stéréo (2 voies)'}, pendant {t} s. Quelle est la taille non compressée, en octets ?", f"A sound is sampled at {fs} Hz with {bd}-bit samples, {'mono' if ch == 1 else 'stereo (2 channels)'}, for {t} s. What is the uncompressed size, in bytes?"),
                 str(int(size)), [str(int(x)) for x in (size * 8, size / 8, fs * bd * t, size / ch if ch == 2 else size * 2, size * 2 if ch == 1 else size * 4)], "fréquence × résolution × voies × durée ÷ 8." if lang == "fr" else "rate × resolution × channels × time ÷ 8.", src=S(lang), cat=cat(lang, "Représentation des données", "Data representation"), diff=4)


@both(FR1 + FRT + EN_ALL, "inf-logic-eval", cap=240, cat="Logique")
def logic_eval(rng, d, lang):
    forms = [("A ET B", lambda a, b, c: a & b, "A AND B", 2), ("A OU B", lambda a, b, c: a | b, "A OR B", 2), ("NON A OU B", lambda a, b, c: (1 - a) | b, "NOT A OR B", 2),
             ("A OU NON B", lambda a, b, c: a | (1 - b), "A OR NOT B", 2), ("(A ET B) OU C", lambda a, b, c: (a & b) | c, "(A AND B) OR C", 3), ("A ET (B OU C)", lambda a, b, c: a & (b | c), "A AND (B OR C)", 3),
             ("NON (A ET B)", lambda a, b, c: 1 - (a & b), "NOT (A AND B)", 2), ("A XOR B", lambda a, b, c: a ^ b, "A XOR B", 2), ("NON (A OU B)", lambda a, b, c: 1 - (a | b), "NOT (A OR B)", 2),
             ("(A OU B) ET NON C", lambda a, b, c: (a | b) & (1 - c), "(A OR B) AND NOT C", 3), ("(A XOR B) ET C", lambda a, b, c: (a ^ b) & c, "(A XOR B) AND C", 3),
             ("A ET B ET C", lambda a, b, c: a & b & c, "A AND B AND C", 3), ("A OU B OU C", lambda a, b, c: a | b | c, "A OR B OR C", 3), ("(A OU B) ET (A OU C)", lambda a, b, c: (a | b) & (a | c), "(A OR B) AND (A OR C)", 3)]
    ftxt, f, etxt, nv = rng.choice(forms)
    rows = [(x, y, z) for x in (0, 1) for y in (0, 1) for z in ((0, 1) if nv == 3 else (0,))]
    v = sum(f(*r) for r in rows)
    lines = len(rows)
    wr = [lines - v, v + 1, v - 1, lines // 2 if lines // 2 != v else lines // 2 + 1, v + 2, lines]
    return Draft(T(lang, f"Dans la table de vérité de l'expression {ftxt} ({lines} lignes), combien de lignes donnent la valeur 1 ?", f"In the truth table of {etxt} ({lines} rows), how many rows give the value 1?"), str(v), [str(w) for w in wr if w != v and 0 <= w <= lines],
                 T(lang, "On évalue l'expression pour chaque combinaison des variables (le programme a énuméré toutes les lignes).", "Evaluate the expression for every combination of the inputs."), src=S(lang), cat=cat(lang, "Logique", "Logic"), diff=3)


@both(FR2 + FR1 + EN_O, "inf-ascii", cap=80, cat="Représentation des données")
def ascii_(rng, d, lang):
    ch = rng.choice("BCDEFGHIJKLMNOPQRSTUVWXYZbcdefghijklmnopqrstuvwxyz123456789")
    code = ord(ch)
    return Draft(T(lang, f"Dans le code ASCII, quel est le code décimal du caractère « {ch} » ? (indice : 'A' = 65, 'a' = 97, '0' = 48)", f"In ASCII, what is the decimal code of the character '{ch}'? (hint: 'A' = 65, 'a' = 97, '0' = 48)"), str(code), [str(code + k) for k in (1, -1, 32, -32, 2, 10) if code + k > 0 and code + k != code],
                 T(lang, "Les lettres et les chiffres sont codés dans l'ordre.", "Letters and digits are coded in order."), src=S(lang), cat=cat(lang, "Représentation des données", "Data representation"))


@both(FR1 + FRT + EN_ALL, "inf-caesar", cap=200, cat="Algorithmique")
def caesar(rng, d, lang):
    word = rng.choice(["CAMEROUN", "YAOUNDE", "DOUALA", "ECOLE", "LYCEE", "BAC", "MATHS", "RESEAU", "CODE", "SERVEUR", "PYTHON", "BINAIRE", "LOGICIEL", "DONNEES", "ALGORITHME"])
    k = rng.randint(1, 9)
    enc = "".join(chr((ord(c) - 65 + k) % 26 + 65) for c in word)
    dec = "".join(chr((ord(c) - 65 - k) % 26 + 65) for c in enc)
    assert dec == word
    others = ["".join(chr((ord(c) - 65 + j) % 26 + 65) for c in word) for j in (k - 1, k + 1, -k, k + 2, 26 - k + 1) if j % 26 != k]
    return Draft(T(lang, f"Avec le chiffre de César de décalage {k} (A devient {chr(65 + k)}), que devient le mot {word} ?", f"Using a Caesar cipher with a shift of {k} (A becomes {chr(65 + k)}), what does {word} become?"), enc, [o for o in others if o != enc],
                 T(lang, "Chaque lettre est décalée de k rangs dans l'alphabet.", "Each letter moves k places along the alphabet."), src=S(lang), cat=cat(lang, "Algorithmique", "Algorithms"), diff=3)


def run_loop(kind, a, b, k):
    """Executes the small loop of the question with Python; returns the final value."""
    if kind == "sum":
        s = 0
        for i in range(a, b + 1):
            s += i * k
        return s
    if kind == "count":
        c = 0
        for i in range(a, b + 1):
            if i % k == 0:
                c += 1
        return c
    if kind == "evenodd":
        s = 0
        for i in range(a, b + 1):
            if i % 2 == 0:
                s += i
            else:
                s -= k
        return s
    if kind == "while":
        x, n = a, 0
        while x < b:
            x = x * k
            n += 1
        return n
    raise ValueError(kind)


@both(FR1 + FRT + EN_ALL, "inf-trace-loop", cap=300, cat="Algorithmique")
def trace_loop(rng, d, lang):
    kind = rng.choice(["sum", "count", "evenodd", "while"])
    a, b, k = rng.randint(1, 4), rng.randint(5, 14), rng.randint(2, 5)
    if kind == "sum":
        v = run_loop(kind, a, b, k)
        code_fr = f"s ← 0\npour i de {a} à {b} faire\n    s ← s + i × {k}\nfin pour"
        txt = T(lang, f"Que vaut s à la fin de l'algorithme : s ← 0 ; pour i de {a} à {b} : s ← s + i × {k} ?", f"What is the value of s at the end: s ← 0; for i from {a} to {b}: s ← s + i × {k}?")
        wr = [v - k * a, v + k, run_loop("sum", a, b - 1, k), run_loop("sum", a + 1, b, k), v + b, v * 2]
    elif kind == "count":
        v = run_loop(kind, a, b, k)
        txt = T(lang, f"Combien de fois la condition « i est divisible par {k} » est-elle vraie lorsque i prend les valeurs de {a} à {b} ?", f"How many times is the condition 'i is divisible by {k}' true as i takes the values {a} to {b}?")
        wr = [v + 1, v - 1 if v else v + 2, b - a + 1, (b - a + 1) // k + 2, v + 2]
    elif kind == "evenodd":
        v = run_loop(kind, a, b, k)
        txt = T(lang, f"s ← 0 ; pour i de {a} à {b} : si i est pair, s ← s + i, sinon s ← s − {k}. Que vaut s à la fin ?", f"s ← 0; for i from {a} to {b}: if i is even then s ← s + i, otherwise s ← s − {k}. What is s at the end?")
        wr = [v + k, v - k, v + 2 * k, sum(range(a, b + 1)), v * 2]
    else:
        v = run_loop(kind, a, b + 20, k)
        txt = T(lang, f"x ← {a} ; n ← 0 ; tant que x < {b + 20} : x ← x × {k} ; n ← n + 1. Que vaut n à la fin ?", f"x ← {a}; n ← 0; while x < {b + 20}: x ← x × {k}; n ← n + 1. What is n at the end?")
        wr = [v + 1, v - 1 if v > 1 else v + 2, v + 2, v * 2, b + 20]
    return Draft(txt, str(v), [str(w) for w in wr if w != v and w >= 0], T(lang, "On exécute l'algorithme pas à pas (ici : le programme a été exécuté pour calculer la réponse).", "Trace the algorithm step by step."), src=S(lang), cat=cat(lang, "Algorithmique", "Algorithms"), diff=3)


@both(FR1 + FRT + EN_A, "inf-binary-search", cap=60, cat="Algorithmique")
def binary_search(rng, d, lang):
    n = rng.choice([8, 16, 32, 64, 100, 128, 256, 500, 1000, 1024, 5000, 1000000])
    worst = math.floor(math.log2(n)) + 1
    wr = [worst + 1, worst - 1, n // 2, n, math.ceil(math.log2(n)) + 2]
    return Draft(T(lang, f"Dans une liste triée de {n} éléments, combien de comparaisons au maximum faut-il pour trouver un élément par recherche dichotomique ?", f"In a sorted list of {n} items, what is the maximum number of comparisons a binary search needs?"), str(worst), [str(w) for w in wr if w != worst],
                 "Chaque étape divise la liste par deux : ⌊log₂ n⌋ + 1." if lang == "fr" else "Each step halves the list: ⌊log₂ n⌋ + 1.", src=S(lang), cat=cat(lang, "Algorithmique", "Algorithms"), diff=4)


@both(FR1 + FRT + EN_A, "inf-nested-loops", cap=100, cat="Algorithmique")
def nested_loops(rng, d, lang):
    n, m = rng.randint(3, 12), rng.randint(2, 9)
    c = 0
    for i in range(n):
        for j in range(m):
            c += 1
    assert c == n * m
    tri = sum(i for i in range(n))
    return Draft(T(lang, f"Combien de fois s'exécute l'instruction du corps de la double boucle « pour i de 1 à {n} : pour j de 1 à {m} » ?", f"How many times does the body of the nested loop 'for i from 1 to {n}: for j from 1 to {m}' run?"), str(c), [str(w) for w in (n + m, n * m + 1, n * m - 1, n ** 2, m ** 2, n * m * 2) if w != c],
                 "n × m exécutions." if lang == "fr" else "The inner body runs n × m times.", src=S(lang), cat=cat(lang, "Algorithmique", "Algorithms"))


@both(FRT + EN_A, "inf-ipv4-hosts", cap=60, cat="Réseaux")
def ipv4_hosts(rng, d, lang):
    p = rng.choice([8, 16, 20, 22, 24, 25, 26, 27, 28, 29, 30])
    hosts = 2 ** (32 - p) - 2
    return Draft(T(lang, f"Combien d'adresses IPv4 utilisables par des machines contient un réseau de masque /{p} ? (adresse de réseau et de diffusion exclues)", f"How many usable host addresses are there in an IPv4 network with a /{p} mask? (network and broadcast addresses excluded)"), str(hosts),
                 [str(w) for w in (hosts + 2, hosts + 1, 2 ** (32 - p) * 2, 2 ** (32 - p) - 1 if hosts + 1 != 2 ** (32 - p) - 1 else hosts - 3, hosts // 2, 2 ** p - 2)], "2^(32 − p) − 2 adresses." if lang == "fr" else "2^(32 − p) − 2 usable addresses.", src=S(lang), cat=cat(lang, "Réseaux · Tle", "Networks"), diff=4)


@both(FRT + EN_A, "inf-mask-dotted", cap=30, cat="Réseaux")
def mask_dotted(rng, d, lang):
    p = rng.choice([8, 16, 24, 25, 26, 27, 28, 20, 22, 30])
    m = (0xFFFFFFFF << (32 - p)) & 0xFFFFFFFF
    dotted = ".".join(str((m >> s) & 255) for s in (24, 16, 8, 0))
    wr = []
    for q in (p - 1, p + 1, p - 8, p + 8, p + 2):
        if 1 <= q <= 31 and q != p:
            mm = (0xFFFFFFFF << (32 - q)) & 0xFFFFFFFF
            wr.append(".".join(str((mm >> s) & 255) for s in (24, 16, 8, 0)))
    return Draft(T(lang, f"Quel est le masque de sous-réseau en notation décimale pointée pour /{p} ?", f"What is the subnet mask in dotted decimal notation for /{p}?"), dotted, wr, "Les p premiers bits sont à 1, les autres à 0." if lang == "fr" else "The first p bits are 1, the rest 0.", src=S(lang), cat=cat(lang, "Réseaux · Tle", "Networks"), diff=4)


@both(FRT + EN_A, "inf-sql-aggregate", cap=160, cat="Bases de données")
def sql_aggregate(rng, d, lang):
    names = ["Ali", "Brice", "Carine", "Doris", "Eric", "Fanta", "Gaston", "Hawa"]
    rows = [(names[i], rng.randint(6, 19)) for i in range(rng.randint(5, 8))]
    thr = rng.randint(9, 15)
    kind = rng.choice(["count", "sum", "max", "min", "avg_int"])
    vals = [v for _, v in rows]
    table = ", ".join(f"{n} : {v}" for n, v in rows)
    if kind == "count":
        v = sum(1 for x in vals if x >= thr); q = f"SELECT COUNT(*) FROM notes WHERE note >= {thr}"
    elif kind == "sum":
        v = sum(x for x in vals if x >= thr); q = f"SELECT SUM(note) FROM notes WHERE note >= {thr}"
    elif kind == "max":
        v = max(vals); q = "SELECT MAX(note) FROM notes"
    elif kind == "min":
        v = min(vals); q = "SELECT MIN(note) FROM notes"
    else:
        s = sum(vals)
        if s % len(vals):
            return None
        v = s // len(vals); q = "SELECT AVG(note) FROM notes"
    wr = [v + 1, v - 1, v + 2, len(vals), sum(vals), v * 2, max(vals) if kind != "max" else min(vals)]
    return Draft(T(lang, f"La table notes(nom, note) contient : {table}. Quel est le résultat de « {q} » ?", f"The table notes(name, note) contains: {table}. What does '{q}' return?"), str(v), [str(w) for w in wr if w != v and w >= 0],
                 T(lang, "On applique la clause WHERE puis la fonction d'agrégation.", "Apply the WHERE clause first, then the aggregate function."), src=S(lang), cat=cat(lang, "Bases de données · Tle", "Databases"), diff=3)


@both(FRT + EN_A, "inf-python-expr", cap=200, cat="Programmation")
def python_expr(rng, d, lang):
    a, b = rng.randint(7, 60), rng.randint(2, 9)
    exprs = [f"{a} // {b}", f"{a} % {b}", f"{a} // {b} + {a} % {b}", f"{b} ** 3", f"{a} % {b} == 0", f"len('{rng.choice(['cameroun', 'yaounde', 'douala', 'python', 'reseau'])}')",
             f"'ab' * {b}", f"({a} + {b}) % {b}", f"{a} // {b} * {b}", f"max({a}, {b} * 10)", f"abs({b} - {a})", f"{a} - {b} * ({a} // {b})"]
    e = rng.choice(exprs)
    v = eval(e, {"__builtins__": {"len": len, "max": max, "abs": abs}}, {})
    s = str(v)
    if isinstance(v, bool):
        s = "True"if v else "False"
        wr = ["False" if v else "True", "None", "0", "1"]
    elif isinstance(v, str):
        wr = [v + "ab", "ab" + str(b), v[:-2], f"ab{b}" if f"ab{b}" != v else "abab", "Erreur" if lang == "fr" else "Error"]
    else:
        wr = [v + 1, v - 1, v + b, v * 2, v // 2 + 1, -v if v else 7]
        wr = [str(w) for w in wr if w != v]
    return Draft(T(lang, f"En Python, que vaut l'expression {e} ?", f"In Python, what is the value of {e}?"), s, wr, T(lang, "// est la division entière, % le reste, ** la puissance.", "// is integer division, % the remainder and ** the power."), src=S(lang), cat=cat(lang, "Programmation", "Programming"), diff=3)
