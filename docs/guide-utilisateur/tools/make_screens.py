#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Transforme les captures brutes (png + dump uiautomator) en docs/guide-utilisateur/screens/ :
  - masque les codes de connexion affichés en clair par la TV (pastilles « •••••• »),
  - calcule les cadres des éléments annotés depuis les bornes réelles du dump,
  - écrit screens/sNN-nom.png et screens/annotations.json.

Usage : python3 make_screens.py --raw /chemin/des/captures-brutes
"""
import argparse, json, os, sys
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from spec import SCREENS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "screens")


def parse_bounds(s):
    a, b = s.replace("][", ",").strip("[]").split(",", 1)[0], None
    nums = [int(x) for x in s.replace("][", ",").strip("[]").split(",")]
    return tuple(nums)


def load_nodes(path):
    nodes = []
    for n in ET.parse(path).iter("node"):
        nodes.append(dict(text=(n.get("text") or n.get("content-desc") or ""),
                          b=parse_bounds(n.get("bounds")), clk=n.get("clickable") == "true"))
    return nodes


def find(nodes, a, size):
    if "box" in a:
        return tuple(a["box"])
    cands = []
    for n in nodes:
        t = n["text"]
        if not t:
            continue
        ok = (t == a["text"]) if a.get("exact") else (a["text"] in t)
        if not ok:
            continue
        x1, y1, x2, y2 = n["b"]
        if x2 <= x1 or y2 <= y1 or x2 - x1 > size[0] * 1.01:
            continue
        if "ymin" in a and y1 < a["ymin"]:
            continue
        if "ymax" in a and y1 > a["ymax"]:
            continue
        cands.append(n)
    if len(cands) <= a.get("nth", 0):
        raise SystemExit("introuvable : %r" % a)
    n = cands[a.get("nth", 0)]
    x1, y1, x2, y2 = n["b"]
    if a.get("clk"):
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        best = None
        for m in nodes:
            if m["clk"] and m["b"][0] <= cx <= m["b"][2] and m["b"][1] <= cy <= m["b"][3]:
                area = (m["b"][2] - m["b"][0]) * (m["b"][3] - m["b"][1])
                if best is None or area < best[0]:
                    best = (area, m["b"])
        if best:
            return best[1]
    p = a.get("pad", 6)
    return (max(0, x1 - p), max(0, y1 - p), min(size[0], x2 + p), min(size[1], y2 + p))


def redact(img, boxes):
    d = ImageDraw.Draw(img)
    px = img.load()
    for (x1, y1, x2, y2) in boxes:
        from collections import Counter
        bg = Counter(px[x, y][:3] for x in range(x1, x2) for y in range(y1, y2)).most_common(1)[0][0]
        best, fg = -1, (255, 255, 255)
        for x in range(x1, x2):
            for y in range(y1, y2):
                c = px[x, y][:3]
                dist = sum(abs(c[i] - bg[i]) for i in range(3))
                if dist > best:
                    best, fg = dist, c
        d.rectangle((x1, y1, x2, y2), fill=bg)
        r = max(2, (y2 - y1) // 9)
        cy = (y1 + y2) // 2 + 1
        step = (x2 - x1 - 2 * r) / 5
        for i in range(6):
            cx = x1 + r + step * i
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fg)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True)
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        if old.endswith(".png"):
            os.remove(os.path.join(OUT, old))
    result = []
    for i, s in enumerate(SCREENS, 1):
        png = os.path.join(args.raw, s["raw"] + ".png")
        xmlp = os.path.join(args.raw, s["raw"] + ".xml")
        img = Image.open(png).convert("RGB")
        if s.get("redact"):
            img = redact(img, s["redact"])
        sid = "s%02d" % i
        fname = "%s-%s.png" % (sid, s["name"])
        img.save(os.path.join(OUT, fname), optimize=True)
        nodes = load_nodes(xmlp) if s["annots"] else []
        an = []
        for a in s["annots"]:
            bx = find(nodes, a, img.size)
            an.append(dict(key=a["key"], label=a["label"], bounds=list(bx)))
        result.append(dict(id=sid, name=s["name"], file=fname, kind=s["kind"], size=list(img.size),
                           shows=s["shows"], source_capture=s["raw"], annotations=an))
        print(fname, img.size, len(an), "repères")
    with open(os.path.join(OUT, "annotations.json"), "w", encoding="utf-8") as f:
        json.dump(dict(note="Bornes en pixels de l'image (issues de uiautomator dump). "
                            "step = numéro d'étape du guide, ajouté par build_guide.py.",
                       screens=result), f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
