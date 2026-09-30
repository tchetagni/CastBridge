#!/usr/bin/env python3
"""Génère les drawables vectoriels et manifestes de l'icône adaptative CastBridge.
Reproductible : les coordonnées sont dérivées de la géométrie de la marque.
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (
        f"M{x:.2f},{y + r:.2f}"
        f" a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{-r:.2f}"
        f" h{w - 2 * r:.2f}"
        f" a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{r:.2f}"
        f" v{h - 2 * r:.2f}"
        f" a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{r:.2f}"
        f" h{-(w - 2 * r):.2f}"
        f" a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{-r:.2f} z"
    )


def circle(cx, cy, r):
    return (
        f"M{cx - r:.2f},{cy:.2f}"
        f" a{r:.2f},{r:.2f} 0 1 0 {2 * r:.2f},0"
        f" a{r:.2f},{r:.2f} 0 1 0 {-2 * r:.2f},0 z"
    )


# Transformation : centrer la marque (centre 125,143.5) dans 120,120 puis échelle 0.9.
def T(p):
    x, y = p
    return (120 + 0.9 * (x - 125), 120 + 0.9 * (y - 143.5))


ARC = T((70, 148)), T((120, 78)), T((172, 142))
PHONE = T((56, 148))
TV = T((150, 142))
DOT = T((120, 111))

arc_path = f"M{ARC[0][0]:.2f},{ARC[0][1]:.2f} Q{ARC[1][0]:.2f},{ARC[1][1]:.2f} {ARC[2][0]:.2f},{ARC[2][1]:.2f}"
phone_path = rrect(PHONE[0], PHONE[1], 28 * 0.9, 46 * 0.9, 8 * 0.9)
tv_path = rrect(TV[0], TV[1], 44 * 0.9, 52 * 0.9, 10 * 0.9)
dot_path = circle(DOT[0], DOT[1], 7 * 0.9)
stroke = 14 * 0.9


def vector(width, height, viewport, children):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width}dp" android:height="{height}dp"\n'
        f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
        f"{children}\n</vector>\n"
    )


background = vector(108, 108, 108,
                    '    <path android:fillColor="#0A0F1E" android:pathData="M0,0h108v108h-108z"/>')

foreground = vector(108, 108, 240,
                    f'    <path android:strokeColor="#F5B025" android:strokeWidth="{stroke:.2f}"\n'
                    f'        android:strokeLineCap="round" android:fillColor="#00000000"\n'
                    f'        android:pathData="{arc_path}"/>\n'
                    f'    <path android:fillColor="#F5B025" android:pathData="{phone_path}"/>\n'
                    f'    <path android:fillColor="#F5B025" android:pathData="{tv_path}"/>\n'
                    f'    <path android:fillColor="#FFE1A6" android:pathData="{dot_path}"/>')

monochrome = vector(108, 108, 240,
                    f'    <path android:strokeColor="#FFFFFF" android:strokeWidth="{stroke:.2f}"\n'
                    f'        android:strokeLineCap="round" android:fillColor="#00000000"\n'
                    f'        android:pathData="{arc_path}"/>\n'
                    f'    <path android:fillColor="#FFFFFF" android:pathData="{phone_path}"/>\n'
                    f'    <path android:fillColor="#FFFFFF" android:pathData="{tv_path}"/>\n'
                    f'    <path android:fillColor="#FFFFFF" android:pathData="{dot_path}"/>')

adaptive = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
    '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
    '</adaptive-icon>\n'
)

drawable = os.path.join(ROOT, "export", "android", "drawable")
mipmap = os.path.join(ROOT, "export", "android", "mipmap-anydpi-v26")
os.makedirs(drawable, exist_ok=True)
os.makedirs(mipmap, exist_ok=True)

for name, content in [
    ("ic_launcher_background.xml", background),
    ("ic_launcher_foreground.xml", foreground),
    ("ic_launcher_monochrome.xml", monochrome),
]:
    with open(os.path.join(drawable, name), "w") as f:
        f.write(content)

for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    with open(os.path.join(mipmap, name), "w") as f:
        f.write(adaptive)

print("Generated:", sorted(os.listdir(drawable)) + sorted(os.listdir(mipmap)))
