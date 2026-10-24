#!/usr/bin/env python3
"""Génère les PNG et polices de la superposition Android EduPlay (démo, icône PROVISOIRE).
python3 gen_android_overlay.py <dossier-polices-Poppins-ttf>     Dépendances : resvg-py pillow fonttools numpy
Sortie : branding/clients/eduplay/android/{sender,receiver}/res"""
import io, os, sys
import numpy as np, resvg_py
from PIL import Image, ImageDraw
from fontTools import subset
from fontTools.ttLib import TTFont

HERE = os.path.dirname(os.path.abspath(__file__))
CL = os.path.dirname(HERE)
SVG = CL + "/logo/eduplay-logo.svg"
OUT = CL + "/android"
FONTS = sys.argv[1]

def render():
    b = resvg_py.svg_to_bytes(svg_path=SVG, width=1254, height=1254)
    return Image.open(io.BytesIO(bytes(b))).convert("RGBA")

full1254 = render()
LOGO = full1254.crop((69, 84, 1191, 1099))                       # logo complet, rogné à son contenu
em = full1254.crop((372, 84, 1110, 655))                          # emblème : sans « THE » ni mot-symbole
ar = np.array(em); ar[(np.arange(em.height)[:, None] + 84 >= 632) & (np.arange(em.width)[None, :] + 372 < 460), 3] = 0
EMBLEM = Image.fromarray(ar); EMBLEM = EMBLEM.crop(EMBLEM.split()[3].getbbox())

# Version COMPACTE PROVISOIRE : emblème + mot « EduPlay » seulement, recadrés du même SVG (rien de redessiné ni de recoloré) ;
# on masque « THE », le slogan « Apprendre · Jouer · Se Divertir » et « par TechnoHub Enterprise » (la queue du « y » est conservée).
la = np.array(full1254)
yy, xx = np.mgrid[0:la.shape[0], 0:la.shape[1]]
la[(xx < 372) & (yy >= 540) & (yy < 670)] = 0                    # « THE »
la[(xx >= 372) & (xx < 460) & (yy >= 632) & (yy < 670)] = 0      # filet vert au-dessus du « E »
la[(yy >= 892) & (xx < 1020)] = 0                                  # slogan et « par TechnoHub Enterprise »
la[yy >= 1000] = 0
LOCKUP = Image.fromarray(la); LOCKUP = LOCKUP.crop(LOCKUP.split()[3].getbbox())

def fit(im, w, h):
    s = min(w / im.width, h / im.height); return im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)

def canvas(w, h, bg=(0, 0, 0, 0)): return Image.new("RGBA", (w, h), bg)

def paste_c(base, im, dy=0):
    base.alpha_composite(im, ((base.width - im.width) // 2, (base.height - im.height) // 2 + dy)); return base

def card(im, pad=0.08, radius=0.16, h=None):
    """Logo sur une carte blanche arrondie (la charte interdit de recolorer le logo : les textes bleu foncé sont illisibles sur bleu nuit)."""
    p = round(max(im.width, im.height) * pad)
    c = canvas(im.width + 2 * p, im.height + 2 * p)
    m = Image.new("L", c.size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, c.width - 1, c.height - 1), radius=round(min(c.size) * radius), fill=255)
    c.paste((255, 255, 255, 255), mask=m); c.alpha_composite(im, (p, p)); return c

def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True); im.save(path, optimize=True)

# ---- icône (PROVISOIRE : emblème sur fond blanc)
def fg(size=432, frac=0.58):
    c = canvas(size, size); return paste_c(c, fit(EMBLEM, round(size * frac), round(size * frac)))
def mono(size=432):
    f = np.array(fg(size)).astype(float); lum = f[..., :3].mean(-1)
    a = f[..., 3] * np.clip((255 - lum) / 120, 0, 1)                # les zones claires (écran, fond) disparaissent
    out = np.zeros_like(f); out[..., :3] = 255; out[..., 3] = a; return Image.fromarray(out.astype("uint8"))
def stat(size=96):
    a = np.array(fit(EMBLEM, size, size).convert("RGBA")); o = np.zeros_like(a); o[..., :3] = 255; o[..., 3] = np.where(a[..., 3] > 128, 255, 0)
    return paste_c(canvas(size, size), Image.fromarray(o))
def legacy(px, round_):
    c = canvas(px, px, (255, 255, 255, 255)); paste_c(c, fit(EMBLEM, round(px * 0.80), round(px * 0.80)))
    if round_:
        m = Image.new("L", (px * 4, px * 4), 0); ImageDraw.Draw(m).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255); m = m.resize((px, px), Image.LANCZOS)
        c.putalpha(m)
    return c

DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for mod in ("sender", "receiver"):
    r = f"{OUT}/{mod}/res"
    save(fg(), f"{r}/drawable/ic_launcher_foreground.png")
    save(mono(), f"{r}/drawable/ic_launcher_monochrome.png")
    for d, px in DENS.items():
        save(legacy(px, False), f"{r}/mipmap-{d}/ic_launcher.png"); save(legacy(px, True), f"{r}/mipmap-{d}/ic_launcher_round.png")

# ---- téléphone (fond clair) : logos de l'appli
S = f"{OUT}/sender/res/drawable"
save(fit(LOCKUP, 640, 640), f"{S}/logo_castbridge_logo_horizontal_light.png")        # fond clair : version compacte (emblème + EduPlay)
save(card(fit(LOCKUP, 560, 560)), f"{S}/logo_castbridge_logo_horizontal.png")         # fond sombre : compact sur carte blanche
save(fit(EMBLEM, 512, 512), f"{S}/logo_castbridge_mark.png")
save(fit(EMBLEM, 512, 512), f"{S}/logo_castbridge_symbol.png")
save(stat(), f"{S}/ic_stat_castbridge.png")
# ---- TV (fond bleu nuit) : logos sur carte blanche
T = f"{OUT}/receiver/res/drawable"
save(card(fit(LOCKUP, 400, 400), 0.08), f"{T}/logo_castbridge_tv_horizontal_compact.png")
save(card(fit(LOGO, 640, 640)), f"{T}/logo_castbridge_tv_horizontal.png")
save(card(fit(LOGO, 640, 640)), f"{T}/logo_castbridge_tv.png")
save(card(fit(LOGO, 640, 640)), f"{T}/logo_castbridge_logo_horizontal.png")
save(card(fit(EMBLEM, 512, 512)), f"{T}/logo_castbridge_mark.png")
save(stat(), f"{T}/ic_stat_castbridge.png")
for d, (w, h) in {"mdpi": (320, 180), "hdpi": (480, 270), "xhdpi": (640, 360)}.items():
    c = canvas(w, h, (255, 255, 255, 255)); paste_c(c, fit(LOGO, round(w * 0.9), round(h * 0.92)))
    save(c.convert("RGB"), f"{OUT}/receiver/res/drawable-{d}/banner.png")

# ---- polices : Poppins (OFL), sous-ensemble latin français
UNI = list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) + [0x152, 0x153, 0x178, 0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2020, 0x2022, 0x2026, 0x2039, 0x203A, 0x20AC, 0x2190, 0x2192, 0x2212, 0x2264, 0x2265, 0xD7]
def sub(name, dst):
    f = TTFont(f"{FONTS}/{name}.ttf"); o = subset.Options(); o.layout_features = ["kern", "liga", "ccmp", "locl", "mark", "mkmk"]
    o.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]; o.notdef_outline = True; o.hinting = False
    s = subset.Subsetter(o); s.populate(unicodes=UNI); s.subset(f); os.makedirs(os.path.dirname(dst), exist_ok=True); f.save(dst)
sub("Poppins-Bold", f"{OUT}/sender/res/font/bricolage_grotesque.ttf")            # le téléphone lit R.font.bricolage_grotesque pour les titres : remplacé par Poppins Bold
for n, f in (("bold", "Bold"), ("semibold", "SemiBold"), ("medium", "Medium")): sub(f"Poppins-{f}", f"{OUT}/receiver/res/font/poppins_{n}.ttf")
print("ok")
