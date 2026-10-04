#!/usr/bin/env python3
"""Génère les ressources Android des deux apps à partir de branding/ (source de vérité) : à relancer après tout changement
de logo ou d'icône.  python3 branding/tools/gen_app_assets.py

  * icônes (25 + 4 créées) : branding/icons/*.svg -> res/drawable/ic_cb_<nom>.xml (VectorDrawable 24 dp, blanc, à teinter)
  * logos : branding/logo/*.svg -> res/drawable/logo_<nom>.xml (texte converti en contours : pas de police à embarquer)
  * icône de lanceur adaptative (fond + avant-plan + monochrome Android 13+) : sender = export/android ; receiver = variante TV
  * PNG hdpi..xxxhdpi de repli (lanceur), bannière Android TV (mdpi/hdpi/xhdpi), icône de notification
Dépendances : fonttools (texte), rsvg-convert (PNG : brew install librsvg).
"""
import glob
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
BR = os.path.dirname(HERE)
REPO = os.path.dirname(BR)
sys.path.insert(0, HERE)
import svg2vd  # noqa: E402

RES = {app: os.path.join(REPO, "android", app, "src", "main", "res") for app in ("sender", "receiver")}

# logos nécessaires par app (nom du fichier SVG sans extension)
LOGOS = {
    "sender": ["castbridge-logo-horizontal", "castbridge-logo-horizontal-light", "castbridge-symbol", "castbridge-mark",
               "quiz-des-millions", "quiz-des-millions-horizontal", "echecs", "echecs-horizontal", "apprendre", "apprendre-horizontal"],
    "receiver": ["castbridge-tv-horizontal", "castbridge-tv-horizontal-compact", "castbridge-tv", "castbridge-mark", "castbridge-logo-horizontal",
                 "quiz-des-millions", "quiz-des-millions-horizontal", "echecs", "echecs-horizontal", "apprendre", "apprendre-horizontal"],
}
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def cp(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy(src, dst)


def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def rsvg(svg_text, out, width, height=None):
    tmp = out + ".tmp.svg"
    w(tmp, svg_text)
    cmd = ["rsvg-convert", "-w", str(width), "-h", str(height or width), tmp, "-o", out]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    subprocess.run(cmd, check=True)
    os.remove(tmp)


def main():
    icons = sorted(glob.glob(os.path.join(BR, "icons", "*.svg")))
    for app, res in RES.items():
        d = os.path.join(res, "drawable")
        for f in icons:
            n = os.path.basename(f)[:-4].replace("-", "_")
            w(os.path.join(d, f"ic_cb_{n}.xml"), svg2vd.convert(f))
        for n in LOGOS[app]:
            f = os.path.join(BR, "logo", n + ".svg")
            w(os.path.join(d, "logo_" + n.replace("-", "_") + ".xml"), svg2vd.convert(f))
        # icône de notification (monochrome, petit symbole blanc)
        stat = svg2vd.convert(os.path.join(BR, "logo", "castbridge-notification.svg"), 24)
        w(os.path.join(d, "ic_stat_castbridge.xml"), stat)
        if app == "sender":  # notification of the media session (Media3 looks this resource up by name)
            w(os.path.join(d, "media3_notification_small_icon.xml"), stat)

    # ---- lanceur adaptatif ----
    ex = os.path.join(BR, "export", "android")
    send = RES["sender"]
    for n in ("ic_launcher_background", "ic_launcher_foreground", "ic_launcher_monochrome"):
        cp(os.path.join(ex, "drawable", n + ".xml"), os.path.join(send, "drawable", n + ".xml"))
    for n in ("ic_launcher", "ic_launcher_round"):
        cp(os.path.join(ex, "mipmap-anydpi-v26", n + ".xml"), os.path.join(send, "mipmap-anydpi-v26", n + ".xml"))
        for dn in DENS:
            dst = os.path.join(send, f"mipmap-{dn}", n + ".png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            cp(os.path.join(ex, f"mipmap-{dn}", n + ".png"), dst)

    rcv = RES["receiver"]
    w(os.path.join(rcv, "drawable", "ic_launcher_background.xml"), open(os.path.join(ex, "drawable", "ic_launcher_background.xml")).read())
    w(os.path.join(rcv, "drawable", "ic_launcher_foreground.xml"), svg2vd.convert(os.path.join(BR, "logo", "adaptive-tv-foreground.svg"), 108))
    w(os.path.join(rcv, "drawable", "ic_launcher_monochrome.xml"), svg2vd.convert(os.path.join(BR, "logo", "adaptive-tv-monochrome.svg"), 108))
    for n in ("ic_launcher", "ic_launcher_round"):
        w(os.path.join(rcv, "mipmap-anydpi-v26", n + ".xml"), open(os.path.join(ex, "mipmap-anydpi-v26", n + ".xml")).read())
    # PNG de repli de la variante TV : carré arrondi (castbridge-tv.svg) et rond
    tv = open(os.path.join(BR, "logo", "castbridge-tv.svg"), encoding="utf-8").read()
    inner = tv.split("</defs>", 1)[1].rsplit("</svg>", 1)[0].replace('<rect width="240" height="240" rx="56" fill="url(#tvbg)"/>', "")
    round_svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240"><defs><clipPath id="c"><circle cx="120" cy="120" r="120"/></clipPath></defs>'
                 f'<g clip-path="url(#c)"><rect width="240" height="240" fill="#0F1630"/>'
                 f'<g transform="translate(120 120) scale(0.92) translate(-120 -120)">{inner}</g></g></svg>')
    for dn, k in DENS.items():
        px = int(48 * k)
        rsvg(tv, os.path.join(rcv, f"mipmap-{dn}", "ic_launcher.png"), px)
        # le clipPath n'étant pas supporté par notre convertisseur VD, il ne sert qu'ici (PNG)
        rsvg(round_svg, os.path.join(rcv, f"mipmap-{dn}", "ic_launcher_round.png"), px)

    # ---- bannière Android TV : PNG (mdpi 320x180 = taille de la charte, hdpi, xhdpi) ----
    banner = open(os.path.join(BR, "logo", "tv-banner-320x180.svg"), encoding="utf-8").read()
    for dn in ("mdpi", "hdpi", "xhdpi"):
        rsvg(banner, os.path.join(rcv, f"drawable-{dn}", "banner.png"), int(320 * DENS[dn]), int(180 * DENS[dn]))
    # ---- polices (sous-ensembles latin de branding/fonts/subset, voir subset_fonts.py) : res/font ----
    fams = {"inter": ("Inter", (400, 500, 600, 700)), "bricolage_grotesque": ("BricolageGrotesque", (500, 700, 800)),
            "sora": ("Sora", (400, 600, 700, 800)), "manrope": ("Manrope", (400, 600, 700, 800))}
    for app, res in RES.items():
        for fam, (file, weights) in fams.items():
            cp(os.path.join(BR, "fonts", "subset", file + ".ttf"), os.path.join(res, "font", fam + ".ttf"))
            items = "\n".join(f'    <font android:font="@font/{fam}" android:fontStyle="normal" android:fontWeight="{wt}" '
                              f'android:fontVariationSettings="\'wght\' {wt}"/>' for wt in weights)
            w(os.path.join(res, "font", f"cb_{fam}.xml"), '<?xml version="1.0" encoding="utf-8"?>\n'
              '<!-- Famille de la charte (police variable, un axe wght par entrée) : généré par branding/tools/gen_app_assets.py -->\n'
              f'<font-family xmlns:android="http://schemas.android.com/apk/res/android">\n{items}\n</font-family>\n')
    print("ok")


if __name__ == "__main__":
    main()
