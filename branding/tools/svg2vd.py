#!/usr/bin/env python3
"""SVG (sous-ensemble de la charte) -> Android VectorDrawable propre.

Sous-ensemble géré : svg/g/use/defs, path, rect(rx), circle, line, polygon, polyline, text/tspan (convertis en contours
avec fontTools : aucune police n'est nécessaire sur l'appareil), linearGradient (objectBoundingBox ou userSpaceOnUse),
opacity, transform translate/scale. Les filtres, flous et masques ne sont pas supportés par Android : ils sont refusés
explicitement (aucun n'est utilisé dans branding/).

Usage : svg2vd.py in.svg out.xml [--size DP] [--tint COLOR] [--keep-aspect]
  --tint COLOR : valeur donnée à `currentColor` (défaut #FFFFFFFF : le drawable est teinté à l'usage).
Dépendance : fonttools (seulement si le SVG contient du texte).
"""
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(os.path.dirname(HERE), "fonts")
FONT_FILES = {"Bricolage Grotesque": "BricolageGrotesque.ttf", "Inter": "Inter.ttf", "Sora": "Sora.ttf", "Manrope": "Manrope.ttf"}
NS = {"svg": "http://www.w3.org/2000/svg"}
_font_cache = {}


def q(tag):
    return tag.split("}")[-1]


def num(v, default=0.0):
    if v is None:
        return default
    m = re.match(r"\s*(-?\d*\.?\d+(?:[eE][-+]?\d+)?)", v)
    return float(m.group(1)) if m else default


def fmt(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def color(v, tint):
    if v is None or v == "none":
        return None
    if v == "currentColor":
        return tint
    v = v.strip()
    if re.fullmatch(r"#[0-9a-fA-F]{3}", v):
        v = "#" + "".join(c * 2 for c in v[1:])
    if re.fullmatch(r"#[0-9a-fA-F]{6}", v):
        return "#FF" + v[1:].upper()
    if v.lower() in ("white",):
        return "#FFFFFFFF"
    if v.lower() in ("black",):
        return "#FF000000"
    raise ValueError("couleur non gérée : " + v)


def rrect_path(x, y, w, h, rx, ry=None):
    r = min(rx, w / 2, h / 2)
    if r <= 0:
        return f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z"
    return (f"M{fmt(x)},{fmt(y + r)}a{fmt(r)},{fmt(r)} 0 0 1 {fmt(r)},{fmt(-r)}h{fmt(w - 2 * r)}"
            f"a{fmt(r)},{fmt(r)} 0 0 1 {fmt(r)},{fmt(r)}v{fmt(h - 2 * r)}a{fmt(r)},{fmt(r)} 0 0 1 {fmt(-r)},{fmt(r)}"
            f"h{fmt(-(w - 2 * r))}a{fmt(r)},{fmt(r)} 0 0 1 {fmt(-r)},{fmt(-r)}z")


def circle_path(cx, cy, r):
    return f"M{fmt(cx - r)},{fmt(cy)}a{fmt(r)},{fmt(r)} 0 1 0 {fmt(2 * r)},0a{fmt(r)},{fmt(r)} 0 1 0 {fmt(-2 * r)},0z"


def path_bbox(d):
    """Boîte englobante approchée (points de contrôle échantillonnés) : sert aux dégradés objectBoundingBox."""
    toks = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?", d)
    xs, ys = [], []
    i, cx, cy, sx, sy, cmd = 0, 0.0, 0.0, 0.0, 0.0, None
    cnt = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}
    while i < len(toks):
        if re.fullmatch(r"[A-Za-z]", toks[i]):
            cmd = toks[i]
            i += 1
            if cmd in "Zz":
                cx, cy = sx, sy
                continue
        n = cnt[cmd.upper()]
        a = [float(t) for t in toks[i:i + n]]
        i += n
        rel = cmd.islower()
        c = cmd.upper()
        if c == "H":
            cx = a[0] + (cx if rel else 0)
            xs.append(cx); ys.append(cy)
            continue
        if c == "V":
            cy = a[0] + (cy if rel else 0)
            xs.append(cx); ys.append(cy)
            continue
        pts = []
        if c == "A":
            pts = [(a[5] + (cx if rel else 0), a[6] + (cy if rel else 0))]
            cr = a[0]
            # cercle : on élargit par le rayon (approximation sûre pour nos icônes)
            px, py = pts[0]
            xs += [px - 0, px]; ys += [py, py]
            if not rel:
                pass
        else:
            for k in range(0, n, 2):
                pts.append((a[k] + (cx if rel else 0), a[k + 1] + (cy if rel else 0)))
        for p in pts:
            xs.append(p[0]); ys.append(p[1])
        cx, cy = pts[-1]
        if c == "M":
            sx, sy = cx, cy
            cmd = "l" if rel else "L"
    return (min(xs), min(ys), max(xs), max(ys)) if xs else (0, 0, 1, 1)


# ---------- texte -> contours ----------
def load_font(family, weight, size):
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
    key = (family, weight, round(size))
    if key in _font_cache:
        return _font_cache[key]
    f = TTFont(os.path.join(FONTS, FONT_FILES[family]))
    if "fvar" in f:
        axes = {a.axisTag: (a.minValue, a.defaultValue, a.maxValue) for a in f["fvar"].axes}
        loc = {}
        if "wght" in axes:
            loc["wght"] = max(axes["wght"][0], min(axes["wght"][2], weight))
        if "opsz" in axes:
            loc["opsz"] = max(axes["opsz"][0], min(axes["opsz"][2], size))
        if "wdth" in axes:
            loc["wdth"] = axes["wdth"][1]
        f = instancer.instantiateVariableFont(f, loc, inplace=False)
    _font_cache[key] = f
    return f


def text_paths(runs, x, y, anchor, family, weight, size, tint, spacing=0.0):
    """runs = [(texte, fill)] -> [(pathData, fill)] ; y = ligne de base, comme en SVG."""
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    font = load_font(family, weight, size)
    cmap, hmtx, gs = font.getBestCmap(), font["hmtx"], font.getGlyphSet()
    scale = size / font["head"].unitsPerEm
    total = sum(hmtx[cmap[ord(ch)]][0] for t, _ in runs for ch in t) * scale
    total += spacing * (sum(len(t) for t, _ in runs) if anchor != "start" else 0)  # comme SVG : l'interlettrage suit aussi la dernière lettre
    cx = x - (total / 2 if anchor == "middle" else total if anchor == "end" else 0)
    out = []
    for text, fill in runs:
        pen = SVGPathPen(gs, ntos=fmt)
        for ch in text:
            g = cmap[ord(ch)]
            gs[g].draw(TransformPen(pen, (scale, 0, 0, -scale, cx, y)))
            cx += hmtx[g][0] * scale + spacing
        d = pen.getCommands()
        if d:
            out.append((d, fill))
    return out


# ---------- conversion ----------
class Conv:
    def __init__(self, tint):
        self.tint = tint
        self.defs = {}
        self.grads = {}
        self.lines = []

    def collect(self, root):
        for el in root.iter():
            i = el.get("id")
            if i:
                self.defs[i] = el
            if q(el.tag) == "linearGradient":
                self.grads[i] = el
            if q(el.tag) in ("filter", "mask", "clipPath", "feGaussianBlur", "radialGradient", "pattern"):
                raise ValueError("élément non supporté par Android : " + q(el.tag))

    def style(self, el, inh):
        s = dict(inh)
        for k in ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "opacity", "fill-opacity", "font-family",
                  "font-weight", "font-size", "text-anchor", "letter-spacing"):
            if el.get(k) is not None:
                s[k] = el.get(k)
        return s

    def grad_xml(self, gid, bbox, attr, ind):
        g = self.grads[gid]
        units = g.get("gradientUnits", "objectBoundingBox")
        x1, y1, x2, y2 = (num(g.get(k), d) for k, d in (("x1", 0), ("y1", 0), ("x2", 1), ("y2", 0)))
        if units == "objectBoundingBox":
            bx0, by0, bx1, by1 = bbox
            w, h = bx1 - bx0, by1 - by0
            x1, x2, y1, y2 = bx0 + x1 * w, bx0 + x2 * w, by0 + y1 * h, by0 + y2 * h
        p = " " * ind
        out = [f'{p}<aapt:attr name="android:{attr}">', f'{p}    <gradient android:type="linear" android:startX="{fmt(x1)}" android:startY="{fmt(y1)}" '
               f'android:endX="{fmt(x2)}" android:endY="{fmt(y2)}">']
        for st in g.findall("svg:stop", NS):
            out.append(f'{p}        <item android:offset="{fmt(num(st.get("offset")))}" android:color="{color(st.get("stop-color"), self.tint)}"/>')
        out += [f"{p}    </gradient>", f"{p}</aapt:attr>"]
        return out

    def emit_path(self, d, st, bbox, ind, fill_override=None):
        p = " " * ind
        fill = fill_override if fill_override is not None else st.get("fill", "#000000")
        stroke = st.get("stroke")
        attrs, kids = [], []
        gid = lambda v: re.fullmatch(r"url\(#([^)]+)\)", v or "")
        if fill and fill != "none":
            m = gid(fill)
            if m:
                kids += self.grad_xml(m.group(1), bbox, "fillColor", ind + 4)
            else:
                attrs.append(f'android:fillColor="{color(fill, self.tint)}"')
        if stroke and stroke != "none":
            m = gid(stroke)
            if m:
                kids += self.grad_xml(m.group(1), bbox, "strokeColor", ind + 4)
            else:
                attrs.append(f'android:strokeColor="{color(stroke, self.tint)}"')
            attrs.append(f'android:strokeWidth="{fmt(num(st.get("stroke-width"), 1))}"')
            attrs.append(f'android:strokeLineCap="{st.get("stroke-linecap", "butt")}"')
            attrs.append(f'android:strokeLineJoin="{st.get("stroke-linejoin", "miter")}"')
        op = st.get("opacity")
        if op is not None and float(op) < 1:
            attrs.append(f'android:fillAlpha="{fmt(float(op))}"')
            if stroke and stroke != "none":
                attrs.append(f'android:strokeAlpha="{fmt(float(op))}"')
        attrs.append(f'android:pathData="{d}"')
        if kids:
            self.lines.append(f"{p}<path " + " ".join(a for a in attrs if "fillColor" not in a or True) + ">")
            self.lines += kids
            self.lines.append(f"{p}</path>")
        else:
            self.lines.append(f"{p}<path " + " ".join(attrs) + "/>")

    def parse_transform(self, t):
        if not t:
            return None
        tx = ty = 0.0
        s = 1.0
        # composition de gauche à droite (translate(a) scale(b) translate(c) ...) -> matrice affine uniforme
        a, e, f = 1.0, 0.0, 0.0  # x' = a*x + e
        for name, args in re.findall(r"(\w+)\(([^)]*)\)", t):
            v = [float(x) for x in re.split(r"[ ,]+", args.strip())]
            if name == "translate":
                e += a * v[0]; f += a * (v[1] if len(v) > 1 else 0)
            elif name == "scale":
                a *= v[0]
            else:
                raise ValueError("transform non géré : " + name)
        return a, e, f

    def node(self, el, inh, ind):
        tag = q(el.tag)
        if tag in ("defs", "linearGradient", "title", "desc"):
            return
        st = self.style(el, inh)
        tr = self.parse_transform(el.get("transform"))
        group = tag in ("g", "use") or tr
        p = " " * ind
        if tr and tr != (1.0, 0.0, 0.0):
            a, e, f = tr
            self.lines.append(f'{p}<group android:pivotX="0" android:pivotY="0" android:scaleX="{fmt(a) if a != 1 else 1}" '
                              f'android:scaleY="{fmt(a) if a != 1 else 1}" android:translateX="{fmt(e)}" android:translateY="{fmt(f)}">')
            ind2, opened = ind + 4, True
        else:
            ind2, opened = ind, False
        if tag == "g":
            for c in el:
                self.node(c, st, ind2)
        elif tag == "use":
            ref = self.defs[(el.get("{http://www.w3.org/1999/xlink}href") or el.get("href"))[1:]]
            self.node(ref, st, ind2) if q(ref.tag) != "g" else [self.node(c, self.style(ref, st), ind2) for c in ref]
        elif tag == "path":
            d = el.get("d")
            self.emit_path(d, st, path_bbox(d), ind2)
        elif tag == "rect":
            x, y, w, h = (num(el.get(k)) for k in ("x", "y", "width", "height"))
            self.emit_path(rrect_path(x, y, w, h, num(el.get("rx"))), st, (x, y, x + w, y + h), ind2)
        elif tag == "circle":
            cx, cy, r = num(el.get("cx")), num(el.get("cy")), num(el.get("r"))
            self.emit_path(circle_path(cx, cy, r), st, (cx - r, cy - r, cx + r, cy + r), ind2)
        elif tag == "line":
            x1, y1, x2, y2 = (num(el.get(k)) for k in ("x1", "y1", "x2", "y2"))
            self.emit_path(f"M{fmt(x1)},{fmt(y1)}L{fmt(x2)},{fmt(y2)}", st, (x1, y1, x2, y2), ind2)
        elif tag in ("polygon", "polyline"):
            pts = [float(v) for v in re.split(r"[ ,]+", el.get("points").strip())]
            d = "M" + "L".join(f"{fmt(pts[i])},{fmt(pts[i + 1])}" for i in range(0, len(pts), 2)) + ("z" if tag == "polygon" else "")
            self.emit_path(d, st, path_bbox(d), ind2)
        elif tag == "text":
            runs = []
            base = st.get("fill", "#000000")
            if el.text and el.text.strip():
                runs.append((el.text, base))
            for ts in el:
                runs.append((ts.text or "", ts.get("fill", base)))
                if ts.tail:
                    runs.append((ts.tail, base))
            size = num(st.get("font-size"), 16)
            paths = text_paths(runs, num(el.get("x")), num(el.get("y")), st.get("text-anchor", "start"), st.get("font-family", "Inter").split(",")[0].strip("'\" "),
                               int(num(st.get("font-weight"), 400)), size, self.tint, num(st.get("letter-spacing"), 0.0))
            tst = {k: v for k, v in st.items() if k in ("opacity",)}
            for d, fill in paths:
                self.emit_path(d, tst, None, ind2, fill_override=fill)
        else:
            raise ValueError("balise non supportée : " + tag)
        if opened:
            self.lines.append(f"{p}</group>")


def convert(svg_path, size_dp=None, tint="#FFFFFFFF", aspect=True):
    root = ET.parse(svg_path).getroot()
    vb = [float(v) for v in re.split(r"[ ,]+", root.get("viewBox").strip())]
    c = Conv(tint)
    c.collect(root)
    base = c.style(root, {"fill": "#000000"})
    for el in root:
        c.node(el, base, 4)
    w, h = vb[2], vb[3]
    if size_dp:
        if w >= h:
            dw, dh = size_dp, size_dp * h / w
        else:
            dw, dh = size_dp * w / h, size_dp
    else:
        dw, dh = w, h
    head = ['<?xml version="1.0" encoding="utf-8"?>',
            f'<!-- Généré par branding/tools/svg2vd.py depuis {os.path.basename(svg_path)} : ne pas modifier à la main. -->',
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
            '    xmlns:aapt="http://schemas.android.com/aapt"',
            f'    android:width="{fmt(dw)}dp" android:height="{fmt(dh)}dp"',
            f'    android:viewportWidth="{fmt(w)}" android:viewportHeight="{fmt(h)}">']
    body = c.lines
    if vb[0] or vb[1]:
        body = [f'    <group android:translateX="{fmt(-vb[0])}" android:translateY="{fmt(-vb[1])}">'] + ["    " + l for l in body] + ["    </group>"]
    return "\n".join(head + body + ["</vector>", ""])


if __name__ == "__main__":
    args = sys.argv[1:]
    size, tint = None, "#FFFFFFFF"
    if "--size" in args:
        i = args.index("--size"); size = float(args[i + 1]); del args[i:i + 2]
    if "--tint" in args:
        i = args.index("--tint"); tint = "#FF" + args[i + 1].lstrip("#").upper(); del args[i:i + 2]
    src, dst = args
    open(dst, "w", encoding="utf-8").write(convert(src, size, tint))
