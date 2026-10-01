#!/usr/bin/env python3
"""Sous-ensemble latin (français) et axes utiles des polices variables de branding/fonts -> branding/fonts/subset/*.ttf
(celles qui sont embarquées dans les apps, res/font). Les polices complètes restent dans branding/fonts (source, licence OFL).

python3 branding/tools/subset_fonts.py     Dépendance : fonttools (pip install fonttools)
"""
import io
import os
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

BR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC, DST = BR + "/fonts", BR + "/fonts/subset"
os.makedirs(DST, exist_ok=True)

# Latin de base + Latin-1 (accents français) + Œ/œ, ponctuation typographique, flèches et signes utilisés par les écrans
UNICODES = list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) + [0x152, 0x153, 0x178, 0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2020, 0x2022, 0x2026,
                                                                  0x2039, 0x203A, 0x20AC, 0x2190, 0x2192, 0x2212, 0x2264, 0x2265, 0x00D7, 0x2022]
# (fichier, axes : valeur fixe ou (min, max))
SPEC = {
    "Inter": {"opsz": 14, "wght": (400, 700)},
    "BricolageGrotesque": {"opsz": 48, "wdth": 100, "wght": (500, 800)},
    "Sora": {"wght": (400, 800)},
    "Manrope": {"wght": (400, 800)},
}
for name, axes in SPEC.items():
    f = TTFont(f"{SRC}/{name}.ttf")
    f = instancer.instantiateVariableFont(f, {k: (v if isinstance(v, (int, float)) else v) for k, v in axes.items()}, inplace=False)
    buf = io.BytesIO(); f.save(buf); buf.seek(0); f = TTFont(buf)   # reload: the subsetter needs the tables fully loaded
    opts = subset.Options()
    opts.layout_features = ["kern", "liga", "ccmp", "locl", "mark", "mkmk"]
    opts.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
    opts.notdef_outline = True
    opts.hinting = False
    s = subset.Subsetter(opts)
    s.populate(unicodes=UNICODES)
    s.subset(f)
    out = f"{DST}/{name}.ttf"
    f.save(out)
    print(name, os.path.getsize(out), "octets")
