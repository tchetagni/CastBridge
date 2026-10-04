#!/usr/bin/env python3
"""Marque CastBridge : insère le contour officiel du Cameroun entre les écouteurs du casque, sous l'arc,
et (re)génère le sous-titre du mot-symbole dans tous les logos de branding/logo/ puis les exports PNG.

    python3 branding/tools/gen_cameroun_mark.py            # logos SVG + exports PNG/ICO (idempotent)
    python3 branding/tools/gen_cameroun_mark.py --svg-only # logos SVG seulement
    python3 branding/tools/gen_cameroun_mark.py --check    # distances de dégagement, sans rien écrire

Ensuite, pour les ressources Android :
    python3 branding/tools/gen_android_icons.py   (drawables d'icône adaptative, d'après la géométrie de la marque)
    python3 branding/tools/gen_app_assets.py      (res/ des deux apps : logo_*, ic_launcher*, banner, notification)

Source du contour : Natural Earth (domaine public) via github.com/datasets/geo-countries (ODC-PDDL), polygone CMR,
simplifié (Douglas-Peucker 0,08 degré, 92 points) dans branding/tools/cameroun-contour.json ; ici seulement
lissé (Visvalingam-Whyatt) et rempli avec un joint arrondi. Rien n'est redessiné à la main.
"""
import json
import math
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BR = os.path.dirname(HERE)
REPO = os.path.dirname(BR)
LOGO = os.path.join(BR, "logo")
EXPORT = os.path.join(BR, "export")
sys.path.insert(0, HERE)

# ======================================================================================================================
# CONFIGURATION DU TEXTE (une seule place : changer le libellé = modifier une ligne ci-dessous, puis relancer)
# ======================================================================================================================
WORDMARK = {
    "line1": ("Cast", "Bridge"),         # ligne 1 : (début, fin colorée) ; les variantes TV ajoutent le badge « TV »
    "subtitle": "MBOKO",                 # ligne 2 (sous-titre) ; "" pour la supprimer partout
    "tv_badge": "TV",
    "banner_tagline": "Téléphone vers TV",
}
SUB_RATIO = 0.38      # taille du sous-titre / taille de la ligne 1 (la bannière TV l'agrandit, voir LAYOUTS)
SUB_TRACK = 0.18      # interlettrage en em
SUB_WEIGHT = 700
SUB_ON_DARK = "#2E9E6B"
SUB_ON_LIGHT = "#1C7C53"

# ======================================================================================================================
# GÉOMÉTRIE (repère propre du symbole `#m`)
# ======================================================================================================================
YELLOW, YELLOW_LIGHT_BG, INK = "#F5B025", "#946219", "#0A0F1E"
CMR_BOTTOM = 194.0                # aligné sur le bas des écouteurs
CMR_CX = 117.0                    # milieu de l'intervalle entre les écouteurs (84..150)
LEFT_EAR = (56, 148, 28, 46)      # x y w h
RIGHT_EAR = (150, 142, 44, 52)
ARC = ((70, 148), (120, 78), (172, 142))   # courbe quadratique, trait 14
ARC_HALF = 7.0
MIN_CLEAR = 6.0                   # dégagement minimal exigé (unités du symbole)

# variantes : mode de couleur de la silhouette, niveau de détail
#   dark = jaune #F5B025 ; light = #946219 (équivalent foncé du primaire sur fond clair) ; white/ink = une seule couleur
FILES = {
    "castbridge-symbol": ("dark", "small"), "castbridge-tv": ("dark", "small"), "castbridge-icon-512": ("dark", "full"),
    "castbridge-mark": ("dark", "full"), "castbridge-symbol-light": ("light", "small"),
    "castbridge-logo-horizontal": ("dark", "full"), "castbridge-logo-vertical": ("dark", "full"),
    "castbridge-tv-horizontal": ("dark", "full"), "tv-banner-320x180": ("dark", "full"),
    "castbridge-logo-horizontal-light": ("light", "full"), "castbridge-logo-vertical-light": ("light", "full"),
    "castbridge-logo-monochrome-white": ("#FFFFFF", "full"), "castbridge-logo-monochrome-ink": (INK, "full"),
    # petites tailles (lanceur, notification) : contour allégé et joint plus épais
    "adaptive-foreground": ("dark", "small"), "adaptive-tv-foreground": ("dark", "small"),
    "adaptive-monochrome": ("#FFFFFF", "small"), "adaptive-tv-monochrome": ("#FFFFFF", "small"),
    "castbridge-notification": ("#FFFFFF", "small"),
}
DETAIL = {"full": (70, 1.3, 64.0), "small": (36, 2.4, 66.0)}   # (points conservés, épaisseur du joint arrondi, hauteur)


def load_outline(npts):
    d = json.load(open(os.path.join(HERE, "cameroun-contour.json"), encoding="utf-8"))
    pts = [tuple(p) for p in d["points"]]
    # Visvalingam-Whyatt : supprime d'abord les pointes minuscules (les « épines » côtières) puis garde la forme générale
    def area(a, b, c):
        return abs((b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])) / 2
    pts = pts[:]
    while len(pts) > npts:
        n = len(pts)
        k = min(range(n), key=lambda i: area(pts[i - 1], pts[i], pts[(i + 1) % n]))
        del pts[k]
    return pts


def placed(detail):
    npts, _, CMR_H = DETAIL[detail]
    pts = load_outline(npts)
    xs = [p[0] for p in pts]
    x0 = CMR_CX - (max(xs) + min(xs)) / 2 * CMR_H
    y0 = CMR_BOTTOM - CMR_H
    return [(x0 + x * CMR_H, y0 + y * CMR_H) for x, y in pts]


def seg_dist(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = max(0, min(1, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(p[0] - ax - t * dx, p[1] - ay - t * dy)


def clearances(poly, join):
    """Dégagements minimaux (joint arrondi compris) avec chaque écouteur et avec l'arc (bord extérieur du trait)."""
    samples = []
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        samples += [(a[0] + (b[0] - a[0]) * t / 20, a[1] + (b[1] - a[1]) * t / 20) for t in range(20)]

    def rect_d(p, r):
        x, y, w, h = r
        return math.hypot(max(x - p[0], 0, p[0] - x - w), max(y - p[1], 0, p[1] - y - h))
    arc = [((1 - t) ** 2 * ARC[0][0] + 2 * t * (1 - t) * ARC[1][0] + t * t * ARC[2][0],
            (1 - t) ** 2 * ARC[0][1] + 2 * t * (1 - t) * ARC[1][1] + t * t * ARC[2][1]) for t in [i / 200 for i in range(201)]]
    left = min(rect_d(p, LEFT_EAR) for p in samples) - join / 2
    right = min(rect_d(p, RIGHT_EAR) for p in samples) - join / 2
    arcd = min(math.hypot(p[0] - q[0], p[1] - q[1]) for p in samples for q in arc) - ARC_HALF - join / 2
    return left, right, arcd


def polygon_svg(detail, fill):
    poly = placed(detail)
    join = DETAIL[detail][1]
    pts = " ".join(f"{x:.2f},{y:.2f}" for x, y in poly)
    return (f'<polygon points="{pts}" fill="{fill}" stroke="{fill}" stroke-width="{join}" stroke-linejoin="round" opacity="1"/>')


MARK_BEGIN, MARK_END = "<!-- cameroun:begin", "<!-- cameroun:end -->"
SOURCE_NOTE = ("Contour du Cameroun : Natural Earth (domaine public) via github.com/datasets/geo-countries (ODC-PDDL), "
               "simplifié ; généré par branding/tools/gen_cameroun_mark.py")
RIGHT_EAR_RE = re.compile(r'<rect x="150" y="142" width="44" height="52" rx="10"[^>]*/>')
BLOCK_RE = re.compile(r"\s*<!-- cameroun:begin.*?<!-- cameroun:end -->", re.S)


def colour_for(mode):
    return {"dark": YELLOW, "light": YELLOW_LIGHT_BG}.get(mode, mode)


def inject_mark(svg, mode, detail):
    svg = BLOCK_RE.sub("", svg)
    if not RIGHT_EAR_RE.search(svg):
        raise SystemExit("oreillette droite introuvable")
    block = f"\n    {MARK_BEGIN}: {SOURCE_NOTE} -->\n    {polygon_svg(detail, colour_for(mode))}\n    {MARK_END}"
    return RIGHT_EAR_RE.sub(lambda m: m.group(0) + block, svg, count=1)


# ======================================================================================================================
# MOT-SYMBOLE : ligne 1 (+ badge TV) et sous-titre sous la ligne 1, mêmes variantes que ci-dessus
# ======================================================================================================================
# x, y1 (ligne de base ligne 1), taille ligne 1, ancre, y2 (ligne de base du sous-titre), taille du sous-titre
LAYOUTS = {
    "castbridge-logo-horizontal": dict(x=196, y1=92, fs=82, anchor="start", y2=127, fs2=31),
    "castbridge-logo-horizontal-light": dict(x=196, y1=92, fs=82, anchor="start", y2=127, fs2=31),
    "castbridge-logo-monochrome-white": dict(x=196, y1=92, fs=82, anchor="start", y2=127, fs2=31),
    "castbridge-logo-monochrome-ink": dict(x=196, y1=92, fs=82, anchor="start", y2=127, fs2=31),
    "castbridge-tv-horizontal": dict(x=188, y1=84, fs=66, anchor="start", y2=115, fs2=25),
    "castbridge-logo-vertical": dict(x=160, y1=208, fs=46, anchor="middle", y2=235, fs2=17.5),
    "castbridge-logo-vertical-light": dict(x=160, y1=208, fs=46, anchor="middle", y2=235, fs2=17.5),
    "tv-banner-320x180": dict(x=146, y1=80, fs=30, anchor="start", y2=105, fs2=14),   # 47 % : lisible à 3 m
}
BRAND_RE = re.compile(r"\s*<!-- wm:begin -->.*?<!-- wm:end -->|[ \t]*<text[^>]*>Cast<tspan[^>]*>Bridge</tspan></text>|"
                      r"[ \t]*<text[^>]*>CastBridge</text>", re.S)


def wordmark_block(name, mode):
    L = LAYOUTS[name]
    a, b = WORDMARK["line1"]
    mono = mode in ("#FFFFFF", INK) or name.endswith("monochrome-white") or name.endswith("monochrome-ink")
    light = name.endswith("-light")
    if name.endswith("monochrome-white"):
        txt = sub = "#FFFFFF"
    elif name.endswith("monochrome-ink"):
        txt = sub = INK
    elif light:
        txt, acc, sub = "#0B1220", "#B7791F", SUB_ON_LIGHT
    else:
        txt, acc, sub = "#F4F6FB", YELLOW, SUB_ON_DARK
    anchor = f' text-anchor="{L["anchor"]}"' if L["anchor"] != "start" else ""
    inner = f"{a}{b}" if mono else f'{a}<tspan fill="{acc}">{b}</tspan>'
    out = [f'  <!-- wm:begin -->',
           f'  <text x="{L["x"]}" y="{L["y1"]}"{anchor} font-family="Bricolage Grotesque" font-weight="800" font-size="{L["fs"]}" fill="{txt}">{inner}</text>']
    if WORDMARK["subtitle"]:
        ls = L["fs2"] * SUB_TRACK
        # l'interlettrage s'ajoute après chaque lettre : en centré, on décale de la moitié pour garder l'axe
        x2 = L["x"] + (ls / 2 if L["anchor"] == "middle" else 0)
        out.append(f'  <text x="{x2:g}" y="{L["y2"]}"{anchor} font-family="Bricolage Grotesque" font-weight="{SUB_WEIGHT}" '
                   f'font-size="{L["fs2"]:g}" letter-spacing="{ls:.2f}" fill="{sub}">{WORDMARK["subtitle"]}</text>')
    out.append("  <!-- wm:end -->")
    return "\n".join(out)


def apply_wordmark(svg, name, mode):
    if name not in LAYOUTS:
        return svg
    new = wordmark_block(name, mode)
    if not BRAND_RE.search(svg):
        raise SystemExit("mot-symbole introuvable dans " + name)
    svg = BRAND_RE.sub(lambda m: "\n" + new if m.group(0).startswith("\n") or "wm:begin" in m.group(0) else new, svg, count=1)
    # badge TV et accroche : lus dans WORDMARK
    svg = re.sub(r'(<text[^>]*>)TV(</text>)', lambda m: m.group(1) + WORDMARK["tv_badge"] + m.group(2), svg)
    svg = re.sub(r'(<text[^>]*>)Téléphone vers TV(</text>)', lambda m: m.group(1) + WORDMARK["banner_tagline"] + m.group(2), svg)
    return svg


def fix_viewbox(svg, name):
    # le vertical gagne de la hauteur pour loger le sous-titre sous le mot-symbole
    if name.startswith("castbridge-logo-vertical") and WORDMARK["subtitle"]:
        svg = svg.replace('viewBox="0 0 320 240" width="320" height="240"', 'viewBox="0 0 320 248" width="320" height="248"')
    elif name.startswith("castbridge-logo-vertical"):
        svg = svg.replace('viewBox="0 0 320 248" width="320" height="248"', 'viewBox="0 0 320 240" width="320" height="240"')
    return svg


# ======================================================================================================================
def write_logos():
    for name, (mode, detail) in FILES.items():
        p = os.path.join(LOGO, name + ".svg")
        svg = open(p, encoding="utf-8").read()
        svg = inject_mark(svg, mode, detail)
        svg = apply_wordmark(svg, name, mode)
        svg = fix_viewbox(svg, name)
        open(p, "w", encoding="utf-8").write(svg)
    write_tv_compact()
    write_admin_assets()


def write_tv_compact():
    """Variante SANS sous-titre du logo TV horizontal, pour l'accueil de la TV (hauteur <= 60 dp, vue à 3 m) :
    le sous-titre MBOKO y serait de ~6 dp, illisible. Le mot-symbole descend pour rester centré sur la marque."""
    src = open(os.path.join(LOGO, "castbridge-tv-horizontal.svg"), encoding="utf-8").read()
    svg = re.sub(r'\n[ \t]*<text[^>]*letter-spacing[^>]*>[^<]*</text>', "", src, count=1)     # le sous-titre est la seule ligne interlettrée
    svg = svg.replace('<text x="188" y="84"', '<text x="188" y="96"').replace("CastBridge TV horizontal", "CastBridge TV horizontal compact")
    open(os.path.join(LOGO, "castbridge-tv-horizontal-compact.svg"), "w", encoding="utf-8").write(svg)


def write_admin_assets():
    """Copies pour les pages /admin du serveur : favicon (= symbole) et logo horizontal avec texte en contours."""
    import svg2vd
    adm = os.path.join(REPO, "backend", "src", "main", "resources", "static", "admin", "assets")
    if not os.path.isdir(adm):
        return
    sym = open(os.path.join(LOGO, "castbridge-symbol.svg"), encoding="utf-8").read()
    open(os.path.join(adm, "favicon.svg"), "w", encoding="utf-8").write(sym)
    open(os.path.join(EXPORT, "favicon.svg"), "w", encoding="utf-8").write(sym)
    h = open(os.path.join(LOGO, "castbridge-logo-horizontal.svg"), encoding="utf-8").read()
    defs = re.search(r'<g id="m">(.*?)</g>\s*</defs>', h, re.S).group(1)
    body = re.search(r"</defs>(.*)</svg>", h, re.S).group(1)
    body = re.sub(r'<use href="#m" transform="([^"]*)"/>', lambda m: f'<g transform="{m.group(1)}">{defs}</g>', body)

    def outline(m):
        attrs, inner = m.group(1), m.group(2)
        g = lambda k, dflt=None: (re.search(k + r'="([^"]*)"', attrs) or [None, dflt])[1]
        runs, base = [], g("fill", "#000")
        pos = 0
        for t in re.finditer(r'<tspan fill="([^"]*)">(.*?)</tspan>', inner):
            if inner[pos:t.start()]:
                runs.append((inner[pos:t.start()], base))
            runs.append((t.group(2), t.group(1)))
            pos = t.end()
        if inner[pos:]:
            runs.append((inner[pos:], base))
        fs = float(g("font-size"))
        ls = float(g("letter-spacing", "0"))
        paths = svg2vd.text_paths(runs, float(g("x")), float(g("y")), g("text-anchor", "start"), g("font-family").split(",")[0],
                                  int(g("font-weight")), fs, "#000", ls)
        return "".join(f'<path d="{d}" fill="{f}"/>' for d, f in paths)
    body = re.sub(r"<text([^>]*)>(.*?)</text>", outline, body, flags=re.S)
    body = re.sub(r"<!-- wm:(begin|end) -->\s*", "", body)
    out = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 160" width="640" height="160" role="img" aria-label="CastBridge">\n'
           "  <!-- texte converti en contours : lisible en <img> sans la police (branding/logo/castbridge-logo-horizontal.svg) ; "
           "généré par branding/tools/gen_cameroun_mark.py -->\n" + body.strip("\n") + "\n</svg>\n")
    open(os.path.join(adm, "castbridge-logo.svg"), "w", encoding="utf-8").write(out)


def rsvg(svg_path, out, w, h=None, bg=None):
    cmd = ["rsvg-convert", "-w", str(w), "-h", str(h or w)]
    if bg:
        cmd += ["-b", bg]
    subprocess.run(cmd + [svg_path, "-o", out], check=True)


def write_exports():
    from PIL import Image
    L = lambda n: os.path.join(LOGO, n + ".svg")
    png = os.path.join(EXPORT, "png")
    rsvg(L("castbridge-icon-512"), os.path.join(png, "playstore-512.png"), 512)
    rsvg(L("tv-banner-320x180"), os.path.join(png, "tv-banner-320x180.png"), 320, 180)
    rsvg(L("castbridge-symbol"), os.path.join(png, "apple-touch-icon-180.png"), 180)
    for s in (16, 32, 48):
        rsvg(os.path.join(EXPORT, "favicon.svg"), os.path.join(png, f"favicon-{s}.png"), s)
    Image.open(os.path.join(png, "favicon-32.png")).save(os.path.join(EXPORT, "favicon.ico"), sizes=[(32, 32)])
    for n, src in (("ic_launcher_foreground", "adaptive-foreground"), ("ic_launcher_background", "adaptive-background"),
                   ("ic_launcher_monochrome", "adaptive-monochrome")):
        rsvg(L(src), os.path.join(png, "adaptive", n + ".png"), 432)
    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for dn, k in dens.items():
        d = os.path.join(EXPORT, "android", f"mipmap-{dn}")
        rsvg(L("adaptive-foreground"), os.path.join(d, "ic_launcher_foreground.png"), int(108 * k))
        rsvg(L("adaptive-background"), os.path.join(d, "ic_launcher_background.png"), int(108 * k))
        # PNG de repli (Android < 8) : fond + avant-plan adaptatifs composés, carré puis rond (même composition qu'avant)
        px = int(48 * k)
        bg = Image.open(os.path.join(d, "ic_launcher_background.png")).convert("RGBA").resize((px, px), Image.LANCZOS)
        fg = Image.open(os.path.join(d, "ic_launcher_foreground.png")).convert("RGBA").resize((px, px), Image.LANCZOS)
        bg.alpha_composite(fg)
        bg.save(os.path.join(d, "ic_launcher.png"))
        mask = Image.new("L", (px * 4, px * 4), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
        bg.putalpha(mask.resize((px, px), Image.LANCZOS))
        bg.save(os.path.join(d, "ic_launcher_round.png"))


def main():
    if "--check" in sys.argv:
        for det in ("full", "small"):
            l, r, a = clearances(placed(det), DETAIL[det][1])
            print(f"{det}: dégagement écouteur gauche {l:.1f}, droit {r:.1f}, arc {a:.1f} (min exigé {MIN_CLEAR})")
        return
    for det in ("full", "small"):
        l, r, a = clearances(placed(det), DETAIL[det][1])
        assert min(l, r, a) >= MIN_CLEAR, f"{det}: dégagement insuffisant {l:.1f}/{r:.1f}/{a:.1f}"
    write_logos()
    if "--svg-only" not in sys.argv:
        write_exports()
    print("ok")


if __name__ == "__main__":
    main()
