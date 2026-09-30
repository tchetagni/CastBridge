#!/usr/bin/env python3
"""Génère, depuis branding/design-tokens.json (source de vérité), les tokens consommés par le code :

  * android/core/src/main/kotlin/castbridge/core/brand/BrandTokens.kt  (Kotlin pur : couleurs ARGB, rayons, espacements, durées, focus, typo)
  * backend/src/main/resources/static/admin/assets/cb-tokens.css      (variables CSS --cb-*)

python3 branding/tools/gen_tokens.py   (aucune dépendance). Un test JVM (BrandTokensTest) vérifie que le Kotlin généré correspond au JSON.
"""
import json
import os
import re

BR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(BR)
d = json.load(open(os.path.join(BR, "design-tokens.json"), encoding="utf-8"))
col = d["color"]


def snake(n):
    return re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", n).upper()


def kebab(n):
    return re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", n).lower()


def argb(h):
    return "0xFF" + h.lstrip("#").upper() + ".toInt()"


def num(v):
    return float(re.match(r"-?[\d.]+", str(v)).group(0))


def ms(v):
    return int(num(v))


# ---------------- Kotlin ----------------
out = ["// GÉNÉRÉ par branding/tools/gen_tokens.py depuis branding/design-tokens.json : ne pas modifier à la main.",
       "package castbridge.core.brand", "",
       "/** Tokens de la charte graphique CastBridge (couleurs ARGB, dp, ms). Pur Kotlin : partagé par :receiver, :sender et les tests. */",
       "object BrandTokens {"]


def group(name, items, indent="    "):
    out.append(f"{indent}object {name} {{")
    for k, v in items.items():
        out.append(f"{indent}    const val {snake(k)} = {argb(v['value'])}")
    out.append(f"{indent}}}")


group("Dark", col["dark"])
group("Light", col["light"])
out.append("    object Semantic {")
for s, v in col["semantic"].items():
    out.append(f"        const val {snake(s)}_DARK = {argb(v['dark']['value'])}")
    out.append(f"        const val {snake(s)}_LIGHT = {argb(v['light']['value'])}")
out.append("    }")
for b, v in col["brand"].items():
    group(b[0].upper() + b[1:], v)
out.append("")
for k, v in d["radius"].items():
    out.append(f"    const val RADIUS_{snake(k)}_DP = {int(num(v['value']))}")
for k, v in d["spacing"].items():
    out.append(f"    const val SPACE_{k}_DP = {int(num(v['value']))}")
for k, v in d["motion"]["duration"].items():
    out.append(f"    const val DURATION_{snake(k)}_MS = {ms(v['value'])}L")
for k, v in d["motion"]["easing"].items():
    pts = re.findall(r"-?[\d.]+", v["value"])
    out.append(f"    val EASING_{snake(k)} = floatArrayOf({', '.join(p + 'f' for p in pts)})")
f = d["focus"]["tv"]
out.append(f"    const val FOCUS_RING_WIDTH_DP = {int(num(f['ringWidth']['value']))}")
out.append(f"    const val FOCUS_RING_OFFSET_DP = {int(num(f['ringOffset']['value']))}")
out.append(f"    const val FOCUS_SCALE = {f['scale']['value']}f")
out.append(f"    const val FOCUS_MOBILE_RING_WIDTH_DP = {int(num(d['focus']['mobile']['ringWidth']['value']))}")
for plat in ("tv", "mobile"):
    for k, v in d["typography"]["scale"][plat].items():
        if "lineHeight" in v:
            out.append(f"    const val {plat.upper()}_{snake(k)}_SIZE = {int(num(v['value']))}  // {'px (canevas 1920)' if plat == 'tv' else 'sp'}")
            out.append(f"    const val {plat.upper()}_{snake(k)}_WEIGHT = {v['weight']}")
out.append(f"    const val TV_MINIMUM_SIZE = {int(num(d['typography']['scale']['tv']['minimum']['value']))}  // px (canevas 1920)")
out.append("}")
kt = os.path.join(REPO, "android", "core", "src", "main", "kotlin", "castbridge", "core", "brand", "BrandTokens.kt")
os.makedirs(os.path.dirname(kt), exist_ok=True)
open(kt, "w", encoding="utf-8").write("\n".join(out) + "\n")

# ---------------- CSS ----------------
css = ["/* GÉNÉRÉ par branding/tools/gen_tokens.py depuis branding/design-tokens.json : ne pas modifier à la main. */", ":root {"]
for k, v in col["dark"].items():
    css.append(f"  --cb-{kebab(k)}: {v['value']};")
for k, v in col["light"].items():
    css.append(f"  --cb-light-{kebab(k)}: {v['value']};")
for s, v in col["semantic"].items():
    css.append(f"  --cb-{s}: {v['dark']['value']};")
    css.append(f"  --cb-light-{s}: {v['light']['value']};")
for b, v in col["brand"].items():
    for k, x in v.items():
        css.append(f"  --cb-{kebab(b)}-{kebab(k)}: {x['value']};")
css.append("  --cb-font-display: 'Bricolage Grotesque', 'Inter', system-ui, sans-serif;")
css.append("  --cb-font-body: 'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif;")
for k, v in d["radius"].items():
    css.append(f"  --cb-radius-{k}: {v['value'].replace('dp', 'px')};")
for k, v in d["spacing"].items():
    css.append(f"  --cb-space-{k}: {v['value'].replace('dp', 'px')};")
for k, v in d["elevation"].items():
    css.append(f"  --cb-elevation-{k[-1]}: {v['value']};")
for k, v in d["motion"]["duration"].items():
    css.append(f"  --cb-duration-{k}: {v['value']};")
for k, v in d["motion"]["easing"].items():
    css.append(f"  --cb-ease-{k}: {v['value']};")
css.append(f"  --cb-focus-ring-width: {d['focus']['tv']['ringWidth']['value'].replace('dp', 'px')};")
css.append("}")
cp = os.path.join(REPO, "backend", "src", "main", "resources", "static", "admin", "assets", "cb-tokens.css")
open(cp, "w", encoding="utf-8").write("\n".join(css) + "\n")
# ---------------- Android colors.xml (thèmes XML, splash, dialogues) ----------------
xml = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- GÉNÉRÉ par branding/tools/gen_tokens.py depuis branding/design-tokens.json : ne pas modifier à la main. -->", "<resources>"]
for k, v in col["dark"].items():
    xml.append(f'    <color name="cb_{snake(k).lower()}">{v["value"]}</color>')
for b in ("quizDesMillions", "echecs", "apprendre"):
    for k, v in col["brand"][b].items():
        xml.append(f'    <color name="cb_{snake(b).lower()}_{snake(k).lower()}">{v["value"]}</color>')
xml.append("</resources>")
for app in ("receiver", "sender"):
    xp = os.path.join(REPO, "android", app, "src", "main", "res", "values", "cb_colors.xml")
    os.makedirs(os.path.dirname(xp), exist_ok=True)
    open(xp, "w", encoding="utf-8").write("\n".join(xml) + "\n")
print("ok")
