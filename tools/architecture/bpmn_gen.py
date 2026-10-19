#!/usr/bin/env python3
"""Générateur des diagrammes BPMN 2.0 de la page d'architecture (bibliothèque standard seulement).

    python3 tools/architecture/bpmn_gen.py              # écrit docs/architecture/bpmn/*.bpmn et met la page à jour
    python3 tools/architecture/bpmn_gen.py --check      # échoue si un fichier ou la page n'est pas à jour
    python3 tools/architecture/bpmn_gen.py --only copie-fichier --lint

Les processus sont décrits dans processes.py. Pour chacun, ce script calcule une mise en page déterministe
(rang des nœuds par plus long chemin, colonnes de 190 px, lanes empilées, routage orthogonal des flux), écrit
un fichier BPMN 2.0 valide avec son diagramme (bpmndi) et, entre les marqueurs <!-- BPMN:BEGIN --> et
<!-- BPMN:END --> de docs/architecture/ARCHITECTURE.html, un <script> qui contient tous les XML. Les cartes de
la section « Processus » (entre <!-- BPMN-CARDS:BEGIN --> et <!-- BPMN-CARDS:END -->) viennent du même fichier
processes.py : une seule source.
"""
import argparse
import heapq
import json
import math
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path
from xml.sax.saxutils import escape

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import processes  # noqa: E402

ROOT = HERE.parents[1]
OUT_DIR = ROOT / "docs" / "architecture" / "bpmn"
PAGE = ROOT / "docs" / "architecture" / "ARCHITECTURE.html"

# ------------------------------------------------------------------------------------------------ constantes
G = 5                                   # pas de la grille de routage (px)
POOL_X, LANE_X, CONTENT_X = 10, 40, 70  # bords gauches : pool, lane, début du contenu (bandeaux de 30 px)
COL0, COLW = 80, 190                    # x = 80 + rang × 190 (bord gauche d'une tâche de 110 px)
ROW_H = 110                             # pas vertical entre deux rangées d'une lane
LANE_MIN = 150                          # hauteur minimale d'une lane
TASKS = {"task", "user", "service", "send", "receive"}
GATES = {"xor", "and", "evgw", "or"}
DIRS = [(1, 0), (0, 1), (-1, 0), (0, -1)]     # E, S, W, N (y vers le bas)
SIDE_DIR = {"E": 0, "S": 1, "W": 2, "N": 3}
OPP = {"E": "W", "W": "E", "N": "S", "S": "N"}

# couleurs par famille d'acteur (fond de tâche, trait) : lisibles sur le fond clair du viewer
KIND_COLORS = {
    "user": ("#ECEFF1", "#455A64"),
    "phone": ("#D3ECE1", "#0A6A4B"),
    "tv": ("#FAE8B9", "#885600"),
    "server": ("#D9E6F0", "#2D5776"),
    "usb": ("#E8EAF6", "#3949AB"),
    "mac": ("#E0F2F1", "#00695C"),
    "other": ("#F1F1F1", "#616161"),
}

# ------------------------------------------------------------------------------------------------ texte
# Largeurs d'Arial en millièmes d'em (assez fidèles pour prévoir les retours à la ligne de bpmn-js)
_ARIAL = {" ": 278, "!": 278, '"': 355, "#": 556, "$": 556, "%": 889, "&": 667, "'": 191, "(": 333, ")": 333, "*": 389,
          "+": 584, ",": 278, "-": 333, ".": 278, "/": 278, ":": 278, ";": 278, "<": 584, "=": 584, ">": 584, "?": 556,
          "@": 1015, "[": 278, "\\": 278, "]": 278, "^": 469, "_": 556, "{": 334, "|": 260, "}": 334, "~": 584,
          "«": 556, "»": 556, "›": 333, "‹": 333, "’": 222, "…": 1000, "→": 1000, "≥": 549, "≤": 549, "≈": 549,
          "×": 584, "°": 400, "–": 556, "—": 1000, "·": 278, " ": 278}
for _c, _w in zip("0123456789", [556] * 10):
    _ARIAL[_c] = _w
for _c, _w in zip("abcdefghijklmnopqrstuvwxyz", [556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556,
                                                 333, 500, 278, 556, 500, 722, 500, 500, 500]):
    _ARIAL[_c] = _w
for _c, _w in zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ", [667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667,
                                                 778, 722, 667, 611, 722, 667, 944, 667, 667, 611]):
    _ARIAL[_c] = _w


def _strip(c):
    d = unicodedata.normalize("NFD", c)
    return d[0] if d else c


def text_w(s, size):
    return sum(_ARIAL.get(c, _ARIAL.get(_strip(c), 556)) for c in s) * size / 1000.0


def wrap(s, maxw, size):
    """Retours à la ligne gloutons comme diagram-js : on coupe aux espaces et après un tiret ; un mot trop long est coupé."""
    toks = [t for t in re.split(r"(\s+|-)", s) if t]
    lines, cur = [], ""
    for t in toks:
        if t.isspace():
            if cur:
                cur += " "
            continue
        cand = cur + t
        if text_w(cand.rstrip(), size) <= maxw:
            cur = cand
            continue
        if cur.strip():
            lines.append(cur.rstrip())
            cur = ""
        word = t
        while text_w(word, size) > maxw and len(word) > 1:
            k = len(word)
            while k > 1 and text_w(word[:k], size) > maxw:
                k -= 1
            lines.append(word[:k])
            word = word[k:]
        cur = word
    if cur.strip():
        lines.append(cur.rstrip())
    return lines or [""]


def ext_label_box(text, maxw=90, size=11):
    """(largeur, hauteur) d'un libellé externe. bpmn-js coupe les lignes à 90 px, centre le texte sur le milieu de la boîte
    donnée dans le diagramme et le pose à partir de son bord haut : la boîte sert donc à placer et à éviter le texte."""
    lines = wrap(text, maxw, size)
    w = int(math.ceil(max(text_w(l, size) for l in lines))) + 2
    return max(w, 16), int(math.ceil(len(lines) * size * 1.2)) + 2


# ------------------------------------------------------------------------------------------------ géométrie
def dims(t):
    if t in TASKS:
        return 110, 80
    if t in GATES:
        return 50, 50
    return 36, 36


def is_event(t):
    return t not in TASKS and t not in GATES


def ceil_g(v):
    return int(math.ceil(v / G - 1e-9)) * G


def floor_g(v):
    return int(math.floor(v / G + 1e-9)) * G


def rects_hit(a, b, pad=0):
    return not (a[2] + pad <= b[0] or b[2] + pad <= a[0] or a[3] + pad <= b[1] or b[3] + pad <= a[1])


def seg_hits_rect(p, q, r, pad=0):
    x0, y0, x1, y1 = r[0] - pad, r[1] - pad, r[2] + pad, r[3] + pad
    if p[0] == q[0]:
        x = p[0]
        lo, hi = sorted((p[1], q[1]))
        return x0 < x < x1 and not (hi <= y0 or lo >= y1)
    y = p[1]
    lo, hi = sorted((p[0], q[0]))
    return y0 < y < y1 and not (hi <= x0 or lo >= x1)


def segs_cross(p, q, r, s):
    """Croisement strict de deux segments orthogonaux."""
    if (p[0] == q[0]) == (r[0] == s[0]):
        return False
    if p[0] == q[0]:
        (vx, vy0, vy1), (hy, hx0, hx1) = (p[0], min(p[1], q[1]), max(p[1], q[1])), (r[1], min(r[0], s[0]), max(r[0], s[0]))
    else:
        (vx, vy0, vy1), (hy, hx0, hx1) = (r[0], min(r[1], s[1]), max(r[1], s[1])), (p[1], min(p[0], q[0]), max(p[0], q[0]))
    return hx0 < vx < hx1 and vy0 < hy < vy1


# ------------------------------------------------------------------------------------------------ rangs
def compute_ranks(P, nodes, order):
    """Rang = plus long chemin (flux de séquence +1, flux de message +0, bord d'un événement limite +0), après retrait
    des arcs de retour (boucles) trouvés par un parcours en profondeur depuis les débuts. Retourne l'ensemble des arcs
    de retour. Les rangs utilisés sont ensuite compactés (0, 1, 2…)."""
    succ = {i: [] for i in nodes}
    for f in P["flows"]:
        succ[f["src"]].append(("seq", f))
    for m in P["messages"]:
        succ[m["src"]].append(("msg", m))
    for i, n in nodes.items():
        if n.get("attach"):
            succ[n["attach"]].append(("att", {"id": "att:" + i, "src": n["attach"], "dst": i}))
    prio = {"att": 0, "seq": 1, "msg": 2}
    state, back = {}, set()

    def dfs(u):
        state[u] = 1
        for kind, e in sorted(succ[u], key=lambda ke: (prio[ke[0]], order.index(ke[1]["dst"]))):
            v = e["dst"]
            if state.get(v, 0) == 0:
                dfs(v)
            elif state[v] == 1:
                back.add(e["id"])
        state[u] = 2

    for r in [i for i in order if nodes[i]["type"].startswith("start")] + order:
        if state.get(r, 0) == 0:
            dfs(r)
    rank = {i: 0 for i in nodes}
    edges = []
    for u, lst in succ.items():
        for kind, e in lst:
            if e["id"] not in back:
                edges.append((u, e["dst"], (1 if kind == "seq" else 0) + (nodes[e["dst"]].get("rank_pad") or 0)))
    for _ in range(len(nodes) + 5):
        changed = False
        for a, b, w in edges:
            if rank[b] < rank[a] + w:
                rank[b] = rank[a] + w
                changed = True
        if not changed:
            break
    used = sorted(set(rank.values()))
    comp = {r: k for k, r in enumerate(used)}
    for i, n in nodes.items():
        n["rank"] = comp[rank[i]]
    return back


# ------------------------------------------------------------------------------------------------ rangées
def assign_rows(P, nodes, order, back):
    seq_in, seq_out = defaultdict(list), defaultdict(list)
    for f in P["flows"]:
        seq_out[f["src"]].append(f)
        seq_in[f["dst"]].append(f)
    occ, res = defaultdict(set), defaultdict(set)
    offs = [0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5]
    bslot = {}
    for i in order:
        n = nodes[i]
        if n.get("attach"):
            bslot[i] = sum(1 for j in order[:order.index(i)] if nodes[j].get("attach") == n["attach"])

    def free_row(lane, rank, d):
        for k in range(40):
            for r in ([d] if k == 0 else [d + k, d - k]):
                if (rank, r) not in occ[lane] and (rank, r) not in res[lane]:
                    return r
        return d

    for nid in sorted(order, key=lambda i: (nodes[i]["rank"], order.index(i))):
        n = nodes[nid]
        if n.get("attach"):
            continue
        d = 0
        fins = [f for f in seq_in[nid] if f["id"] not in back]
        cand = []
        for f in fins:
            p = nodes[f["src"]]
            host = nodes[p["attach"]] if p.get("attach") else p
            if host["lane"] == n["lane"] and "row" in host:
                cand.append(f)
        if cand:
            f0 = cand[0]
            p = nodes[f0["src"]]
            if p.get("attach"):
                host = nodes[p["attach"]]
                d = host["row"] + 1 + bslot[p["id"]] + seq_out[p["id"]].index(f0)
            else:
                same = [f for f in seq_out[p["id"]] if f["id"] not in back and nodes[f["dst"]]["lane"] == p["lane"]]
                idx = same.index(f0) if f0 in same else 0
                d = p["row"] + offs[min(idx, len(offs) - 1)]
        if n.get("row_hint") is not None:
            d = n["row_hint"]
        r = free_row(n["lane"], n["rank"], d)
        n["row"] = r
        occ[n["lane"]].add((n["rank"], r))
        if any(m.get("attach") == nid for m in nodes.values()):
            res[n["lane"]].add((n["rank"], r + 1))      # la rangée sous la tâche reste libre : les flux des événements limites y passent
        for f in seq_out[nid]:
            if f["id"] in back:
                continue
            gap = nodes[f["dst"]]["rank"] - n["rank"]
            for rr in range(n["rank"] + 1, n["rank"] + gap):
                res[n["lane"]].add((rr, r))
    lane_rows = defaultdict(list)
    for n in nodes.values():
        if "row" in n:
            lane_rows[n["lane"]].append(n["row"])
    for lane, rows in lane_rows.items():
        lo = min(rows)
        for n in nodes.values():
            if n["lane"] == lane and "row" in n:
                n["row"] -= lo
    for n in nodes.values():
        if n.get("attach"):
            n["row"] = nodes[n["attach"]]["row"]


# ------------------------------------------------------------------------------------------------ coordonnées
def place(P, nodes, order, back):
    """Hauteur des lanes, position des pools, centre de chaque nœud (boucles et événements limites compris)."""
    pools = P["pools"]
    lanes = {l["id"]: l for p in pools for l in p["lanes"]}
    for l in lanes.values():
        rows = [n["row"] for n in nodes.values() if n["lane"] == l["id"] and not n.get("attach")]
        l["nrows"] = (max(rows) + 1) if rows else 1
        h = LANE_MIN + ROW_H * (l["nrows"] - 1)
        if any(n.get("attach") and n["lane"] == l["id"] for n in nodes.values()):
            h += 30
        nb = sum(1 for f in P["flows"] if f["id"] in back and nodes[f["src"]]["lane"] == l["id"] and nodes[f["dst"]]["lane"] == l["id"])
        h += 12 * min(nb, 3)
        l["h"] = ceil_g(h)
    idx = {p["id"]: k for k, p in enumerate(pools)}
    cross = [0] * max(1, len(pools))
    for m in P["messages"]:
        a, b = sorted((idx[nodes[m["src"]]["pool"]], idx[nodes[m["dst"]]["pool"]]))
        for k in range(a, b):
            cross[k] += 1
    y = 20
    for k, p in enumerate(pools):
        p["y"] = y
        ly = y
        for l in p["lanes"]:
            l["y"] = ly
            ly += l["h"]
        p["h"] = ly - y
        y = ly + 40 + 14 * min(cross[k], 8)
    P["canvas_h"] = pools[-1]["y"] + pools[-1]["h"] + 20
    for n in nodes.values():
        if n.get("attach"):
            continue
        n["cx"] = COL0 + 55 + COLW * n["rank"]
        n["cy"] = lanes[n["lane"]]["y"] + 75 + ROW_H * n["row"]
    groups = defaultdict(list)
    for i in order:
        if nodes[i].get("attach"):
            groups[nodes[i]["attach"]].append(i)
    for host_id, ids in groups.items():
        host = nodes[host_id]
        k = len(ids)
        spread = {1: [-35], 2: [-35, 35], 3: [-35, 0, 35]}.get(k) or [int(-35 + 70 * j / (k - 1)) // G * G for j in range(k)]
        for j, bid in enumerate(ids):
            b = nodes[bid]
            b["cx"] = host["cx"] + (b["dx"] if b.get("dx") is not None else spread[j])
            b["cy"] = host["cy"] + 40
            b["bslot"] = (j, k)
    return lanes


def port_point(n, side, off=0):
    w, h = dims(n["type"])
    cx, cy = n["cx"], n["cy"]
    if side == "E":
        return (cx + w // 2, cy + off)
    if side == "W":
        return (cx - w // 2, cy + off)
    if side == "S":
        return (cx + off, cy + h // 2)
    return (cx + off, cy - h // 2)


def assign_ports(P, nodes, order, back):
    pools = P["pools"]
    pidx = {p["id"]: k for k, p in enumerate(pools)}
    seq_in, seq_out = defaultdict(list), defaultdict(list)
    for f in P["flows"]:
        seq_out[f["src"]].append(f)
        seq_in[f["dst"]].append(f)
        f["sp"] = f["tp"] = None
    for nid in order:
        n = nodes[nid]
        outs = seq_out[nid]
        if not outs:
            continue
        if n.get("attach"):
            for f in outs:
                f["sp"] = ("S", 0)
        elif n["type"] in GATES:
            fw = [f for f in outs if f["id"] not in back]
            used = set()
            for k, f in enumerate(sorted(fw, key=lambda f: (abs(nodes[f["dst"]]["cy"] - n["cy"]), outs.index(f)))):
                dy = nodes[f["dst"]]["cy"] - n["cy"]
                side = "E" if k == 0 else ("N" if dy < 0 else "S" if dy > 0 else "E")
                f["sp"] = (side, 0)
                used.add(side)
            for f in outs:
                if f["id"] in back:
                    f["sp"] = ("S", 0)
        else:
            for f in outs:
                f["sp"] = ("S", 0) if f["id"] in back else ("E", 0)
    for nid in order:
        n = nodes[nid]
        ins = seq_in[nid]
        fw = [f for f in ins if f["id"] not in back]
        if fw:
            prim = min(fw, key=lambda f: (abs(nodes[f["src"]]["cy"] - n["cy"]), ins.index(f)))
            for f in fw:
                dy = nodes[f["src"]]["cy"] - n["cy"]
                f["tp"] = ("W", 0) if f is prim or dy == 0 else (("N" if dy < 0 else "S"), 0)
        for f in ins:
            if f["id"] in back:
                f["tp"] = ("S", 0)
    for m in P["messages"]:
        ps, pt = pidx[nodes[m["src"]]["pool"]], pidx[nodes[m["dst"]]["pool"]]
        m["sp"], m["tp"] = (("S", 0), ("N", 0)) if ps < pt else (("N", 0), ("S", 0))
    users = defaultdict(list)
    for f in P["flows"] + P["messages"]:
        users[(f["src"], f["sp"][0])].append((f, "sp", f["dst"]))
        users[(f["dst"], f["tp"][0])].append((f, "tp", f["src"]))
    for (nid, side), lst in users.items():
        n = nodes[nid]
        if len(lst) < 2 or n["type"] in GATES:
            continue
        lst.sort(key=(lambda it: nodes[it[2]]["cx"]) if side in "NS" else (lambda it: nodes[it[2]]["cy"]))
        step = 20 if n["type"] in TASKS else 8
        k = len(lst)
        offs = [int(round((j - (k - 1) / 2.0) * step / G)) * G for j in range(k)]
        bevs = [b["cx"] - n["cx"] for b in nodes.values() if b.get("attach") == nid] if side == "S" else []
        if bevs and n["type"] in TASKS:
            # les événements limites sont au bord bas : les flux de la tâche passent à 27 px au moins de leur centre
            allowed = [o for o in range(-45, 50, G) if all(abs(o - dx) >= 27 for dx in bevs)]
            if allowed:
                mid = (allowed[0] + allowed[-1]) / 2.0
                chosen = []
                for o in sorted(allowed, key=lambda o: abs(o - mid)):
                    if all(abs(o - c) >= 14 for c in chosen):
                        chosen.append(o)
                    if len(chosen) == k:
                        break
                if len(chosen) == k:
                    offs = sorted(chosen)
        for j, (f, which, _) in enumerate(lst):
            f[which] = (side, offs[j])


def place_node_labels(P, nodes):
    """Libellés externes des événements et des passerelles (les tâches portent le leur) : du côté libre."""
    used = defaultdict(set)
    for f in P["flows"] + P["messages"]:
        used[f["src"]].add(f["sp"][0])
        used[f["dst"]].add(f["tp"][0])
    for n in nodes.values():
        n["lbl"] = None
        if n["type"] in TASKS or not n["label"]:
            continue
        cx, cy = n["cx"], n["cy"]
        r = dims(n["type"])[1] // 2
        if n.get("attach"):
            w, h = ext_label_box(n["label"])
            j, k = n["bslot"]
            left = (j == 0)
            n["lbl"] = (cx - 22 - w, cy + 2, cx - 22, cy + 2 + h) if left else (cx + 22, cy + 2, cx + 22 + w, cy + 2 + h)
            continue
        w, h = ext_label_box(n["label"])
        pref = n.get("lbl_side")
        sides = [pref] if pref else (["N", "S"] if n["type"] in GATES else ["S", "N"])
        side = next((s for s in sides + ["S", "N"] if s not in used[n["id"]]), None)
        if side == "S":
            n["lbl"] = (cx - w // 2, cy + r + 3, cx - w // 2 + w, cy + r + 3 + h)
        elif side == "N":
            n["lbl"] = (cx - w // 2, cy - r - 3 - h, cx - w // 2 + w, cy - r - 3)
        else:
            n["lbl"] = (cx + 12, cy - r - 3 - h, cx + 12 + w, cy - r - 3)


def node_rect(n):
    w, h = dims(n["type"])
    return (n["cx"] - w // 2, n["cy"] - h // 2, n["cx"] + w // 2, n["cy"] + h // 2)


# ------------------------------------------------------------------------------------------------ routage
class Grid:
    def __init__(self, width, height):
        self.W = int(width // G) + 4
        self.H = int(height // G) + 4
        size = self.W * self.H
        self.block_full = bytearray(size)     # nœuds (marge) et libellés
        self.block_nodes = bytearray(size)    # nœuds seuls (marge)
        self.block_core = bytearray(size)     # nœuds seuls, sans marge
        self.soft = bytearray(size)
        self.occ_h, self.occ_v = {}, {}

    def mark(self, arr, r, pad):
        i0 = max(0, int(math.ceil((r[0] - pad) / G - 1e-9)))
        i1 = min(self.W - 1, int(math.floor((r[2] + pad) / G + 1e-9)))
        j0 = max(0, int(math.ceil((r[1] - pad) / G - 1e-9)))
        j1 = min(self.H - 1, int(math.floor((r[3] + pad) / G + 1e-9)))
        if i1 < i0:
            return
        fill = b"\x01" * (i1 - i0 + 1)
        for j in range(j0, j1 + 1):
            arr[j * self.W + i0: j * self.W + i1 + 1] = fill

    def register(self, pts):
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            if y0 == y1:
                j = y0 // G
                for i in range(int(math.ceil(min(x0, x1) / G - 1e-9)), int(math.floor(max(x0, x1) / G + 1e-9)) + 1):
                    k = j * self.W + i
                    self.occ_h[k] = self.occ_h.get(k, 0) + 1
            else:
                i = x0 // G
                for j in range(int(math.ceil(min(y0, y1) / G - 1e-9)), int(math.floor(max(y0, y1) / G + 1e-9)) + 1):
                    k = j * self.W + i
                    self.occ_v[k] = self.occ_v.get(k, 0) + 1


TURN, OVER, CROSS = 8.0, 20.0, 3.0


def astar(grid, blocked, a, d0, b, dfinal, bounds, good_x):
    W = grid.W
    bi, bj = b
    bx0, by0, bx1, by1 = bounds
    soft, occ_h, occ_v = grid.soft, grid.occ_h, grid.occ_v
    heap = [(abs(a[0] - bi) + abs(a[1] - bj), 0.0, 0, a[0], a[1], d0)]
    best = {(a[0], a[1], d0): 0.0}
    parent = {}
    cnt = 1
    forbid = (dfinal + 2) % 4
    while heap:
        _, g, _, i, j, d = heapq.heappop(heap)
        if g > best.get((i, j, d), 1e18) + 1e-9:
            continue
        if i == bi and j == bj and d != forbid:
            path = [(i, j)]
            key = (i, j, d)
            while key in parent:
                key = parent[key]
                path.append((key[0], key[1]))
            path.reverse()
            return path
        for nd in (d, (d + 1) % 4, (d + 3) % 4):
            dx, dy = DIRS[nd]
            ni, nj = i + dx, j + dy
            if ni < bx0 or ni > bx1 or nj < by0 or nj > by1:
                continue
            idx = nj * W + ni
            if blocked[idx]:
                continue
            c = 1.0
            if nd != d:
                c += TURN
            if soft[idx]:
                c += 1.0
            if dy == 0:
                o = occ_h.get(idx)
                if o:
                    c += OVER * o
                if idx in occ_v:
                    c += CROSS
            else:
                o = occ_v.get(idx)
                if o:
                    c += OVER * o
                if idx in occ_h:
                    c += CROSS
                if (ni * G) not in good_x:
                    c += 0.4
            ng = g + c
            key = (ni, nj, nd)
            if ng < best.get(key, 1e18) - 1e-9:
                best[key] = ng
                parent[key] = (i, j, d)
                cnt += 1
                heapq.heappush(heap, (ng + abs(ni - bi) + abs(nj - bj), ng, cnt, ni, nj, nd))
    return None


def stub_point(p, side, stub=15):
    x, y = p
    if side == "E":
        return (ceil_g(x + stub), y)
    if side == "W":
        return (floor_g(x - stub), y)
    if side == "S":
        return (x, ceil_g(y + stub))
    return (x, floor_g(y - stub))


def simplify(pts):
    out = []
    for p in pts:
        if not out or out[-1] != p:
            out.append(p)
    res = [out[0]]
    for k in range(1, len(out) - 1):
        a, b, c = res[-1], out[k], out[k + 1]
        if (a[0] == b[0] == c[0]) or (a[1] == b[1] == c[1]):
            continue
        res.append(b)
    res.append(out[-1])
    return res


def route_edge(grid, p0, s0, p1, s1, bounds_px, good_x, warn, name):
    e0, e1 = stub_point(p0, s0), stub_point(p1, s1)
    a, b = (e0[0] // G, e0[1] // G), (e1[0] // G, e1[1] // G)
    d0, dfinal = SIDE_DIR[s0], (SIDE_DIR[s1] + 2) % 4
    bounds = (max(0, bounds_px[0] // G), max(0, bounds_px[1] // G), min(grid.W - 1, bounds_px[2] // G), min(grid.H - 1, bounds_px[3] // G))
    for level, blocked in enumerate((grid.block_full, grid.block_nodes, grid.block_core)):
        path = astar(grid, blocked, a, d0, b, dfinal, bounds, good_x)
        if path:
            if level:
                warn.append("routage assoupli (niveau %d) : %s" % (level, name))
            pts = [p0, e0] + [(i * G, j * G) for i, j in path] + [e1, p1]
            return simplify(pts)
    warn.append("ROUTAGE IMPOSSIBLE (tracé direct) : " + name)
    mx = (e0[0] + e1[0]) // 2 // G * G
    return simplify([p0, e0, (mx, e0[1]), (mx, e1[1]), e1, p1])


def flow_label_place(pts, text, obstacles, placed, others):
    """Place le libellé d'un flux près de sa source, du côté sans collision."""
    w, h = ext_label_box(text)
    cands = []
    segs = list(zip(pts, pts[1:]))
    order = sorted(range(len(segs)), key=lambda k: (k, ))
    for k in order:
        (x0, y0), (x1, y1) = segs[k]
        ln = abs(x1 - x0) + abs(y1 - y0)
        if ln < 20:
            continue
        if y0 == y1:
            sx = 1 if x1 > x0 else -1
            lx = x0 + 6 if sx > 0 else x0 - 6 - w
            lx = min(max(lx, min(x0, x1) + 2), max(x0, x1) - w - 2) if ln > w + 10 else lx
            cands.append((k, (lx, y0 - h - 2, lx + w, y0 - 2)))
            cands.append((k, (lx, y0 + 2, lx + w, y0 + 2 + h)))
        else:
            sy = 1 if y1 > y0 else -1
            ly = y0 + 6 if sy > 0 else y0 - 6 - h
            cands.append((k, (x0 + 4, ly, x0 + 4 + w, ly + h)))
            cands.append((k, (x0 - 4 - w, ly, x0 - 4, ly + h)))
    best, best_score = None, 1e9
    for pos, (k, r) in enumerate(cands):
        score = pos * 0.01
        for o in obstacles:
            if rects_hit(r, o, 1):
                score += 10
        for o in placed:
            if rects_hit(r, o, 1):
                score += 10
        for poly in others:
            for p, q in zip(poly, poly[1:]):
                if seg_hits_rect(p, q, r):
                    score += 3
        for p, q in segs:
            if seg_hits_rect(p, q, r):
                score += 3
        if score < best_score:
            best, best_score = r, score
            if score < 0.5:
                break
    if best is None:
        (x0, y0), (x1, y1) = segs[0]
        best = (x0 + 4, y0 - h - 2, x0 + 4 + w, y0 - 2)
    return best, best_score


def layout(P):
    """Calcule toute la mise en page d'un processus. Retourne un dict (nodes, lanes, warnings, largeur…)."""
    nodes = {}
    order = []
    for n in P["nodes"]:
        d = dict(n)
        d["w"], d["h"] = dims(d["type"])
        nodes[d["id"]] = d
        order.append(d["id"])
    back = compute_ranks(P, nodes, order)
    assign_rows(P, nodes, order, back)
    lanes = place(P, nodes, order, back)
    assign_ports(P, nodes, order, back)
    place_node_labels(P, nodes)
    warn = []
    right = max([node_rect(n)[2] for n in nodes.values()] + [n["lbl"][2] for n in nodes.values() if n["lbl"]])
    pool_w = right + 70 - POOL_X
    canvas_w = POOL_X + pool_w + 30
    grid = Grid(canvas_w, P["canvas_h"] + 30)
    for n in nodes.values():
        r = node_rect(n)
        grid.mark(grid.block_full, r, 6)
        grid.mark(grid.block_nodes, r, 6)
        grid.mark(grid.block_core, r, 1)
        grid.mark(grid.soft, r, 14)
        if n["lbl"]:
            grid.mark(grid.block_full, n["lbl"], 4)
    good_x = {COL0 + 55 + COLW * k + COLW // 2 for k in range(0, 40)}
    good_x = {x - x % G for x in good_x}
    pools = {p["id"]: p for p in P["pools"]}
    edges = []
    for f in P["flows"]:
        edges.append((0 if f["id"] not in back else 1, f))
    for m in P["messages"]:
        edges.append((2, m))
    def span(e):
        s, t = nodes[e[1]["src"]], nodes[e[1]["dst"]]
        return abs(s["cx"] - t["cx"]) + abs(s["cy"] - t["cy"])
    edges.sort(key=lambda e: (e[0], span(e), e[1]["id"]))
    for cls, e in edges:
        s, t = nodes[e["src"]], nodes[e["dst"]]
        p0 = port_point(s, e["sp"][0], e["sp"][1])
        p1 = port_point(t, e["tp"][0], e["tp"][1])
        if cls == 2:
            bounds = (CONTENT_X + 5, 8, POOL_X + pool_w - 8, P["canvas_h"] - 8)
        else:
            pl = pools[s["pool"]]
            bounds = (CONTENT_X + 5, pl["y"] + 8, POOL_X + pool_w - 8, pl["y"] + pl["h"] - 8)
        gx = set(good_x) | {p0[0], p1[0]}
        pts = route_edge(grid, p0, e["sp"][0], p1, e["tp"][0], bounds, gx, warn, "%s -> %s" % (e["src"], e["dst"]))
        grid.register(pts)
        e["pts"] = pts
    obstacles = [node_rect(n) for n in nodes.values()] + [n["lbl"] for n in nodes.values() if n["lbl"]]
    placed = []
    polys = [e["pts"] for _, e in edges]
    for cls, e in sorted(edges, key=lambda e: (e[0], e[1]["id"])):
        e["lbl"] = None
        text = e.get("label")
        if not text:
            continue
        r, sc = flow_label_place(e["pts"], text, obstacles, placed, [p for p in polys if p is not e["pts"]])
        e["lbl"] = r
        placed.append(r)
        if sc >= 10:
            warn.append("libellé de flux en collision : %s (%s)" % (text, e["id"]))
    maxx = max([pt[0] for _, e in edges for pt in e["pts"]] + [pool_w + POOL_X - 20])
    pool_w = max(pool_w, maxx + 30 - POOL_X)
    for _, e in edges:
        if e.get("lbl"):
            pool_w = max(pool_w, e["lbl"][2] + 20 - POOL_X)
    return {"nodes": nodes, "order": order, "lanes": lanes, "back": back, "warn": warn, "pool_w": pool_w, "edges": edges}


# ------------------------------------------------------------------------------------------------ XML BPMN 2.0
NS = ('xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" '
      'xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" '
      'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bioc="http://bpmn.io/schema/bpmn/biocolor/1.0"')
TASK_TAG = {"task": "bpmn:task", "user": "bpmn:userTask", "service": "bpmn:serviceTask", "send": "bpmn:sendTask", "receive": "bpmn:receiveTask"}
TASK_PFX = {"task": "Task", "user": "UserTask", "service": "ServiceTask", "send": "SendTask", "receive": "ReceiveTask"}
GATE_TAG = {"xor": "bpmn:exclusiveGateway", "and": "bpmn:parallelGateway", "evgw": "bpmn:eventBasedGateway", "or": "bpmn:inclusiveGateway"}
EVENT_TAG = {"start": ("bpmn:startEvent", "StartEvent"), "end": ("bpmn:endEvent", "EndEvent"), "catch": ("bpmn:intermediateCatchEvent", "Event"),
             "throw": ("bpmn:intermediateThrowEvent", "Event"), "boundary": ("bpmn:boundaryEvent", "BoundaryEvent")}
EVENT_DEF = {"message": "bpmn:messageEventDefinition", "timer": "bpmn:timerEventDefinition", "error": "bpmn:errorEventDefinition",
             "condition": "bpmn:conditionalEventDefinition", "signal": "bpmn:signalEventDefinition", "terminate": "bpmn:terminateEventDefinition"}


def q(s):
    return escape(str(s), {'"': "&quot;"})


def kind_colors(kind, tag):
    fill, stroke = KIND_COLORS.get(kind, KIND_COLORS["other"])
    return (fill if tag == "task" else "#FFFFFF"), stroke


def bpmn_xml(P, L):
    nodes, order, lanes = L["nodes"], L["order"], L["lanes"]
    pools = P["pools"]
    pool_w = L["pool_w"]
    pool_of = {p["id"]: p for p in pools}
    xid = {}
    for i in order:
        n = nodes[i]
        t = n["type"]
        if t in TASKS:
            pfx = TASK_PFX[t]
        elif t in GATES:
            pfx = "Gateway"
        else:
            pfx = EVENT_TAG[t.split(":")[0]][1]
        xid[i] = "%s_%s" % (pfx, i)
    fid = {f["id"]: "Flow_%s" % f["id"] for f in P["flows"]}
    mid = {m["id"]: "MessageFlow_%s" % m["id"] for m in P["messages"]}
    seq_in, seq_out = defaultdict(list), defaultdict(list)
    for f in P["flows"]:
        seq_out[f["src"]].append(f)
        seq_in[f["dst"]].append(f)
    cid = "Collaboration_" + P["id"].replace("-", "_")
    o = ['<?xml version="1.0" encoding="UTF-8"?>',
         '<bpmn:definitions %s id="Definitions_%s" targetNamespace="https://bridge.sti-cm.com/castbridge/architecture" '
         'exporter="CastBridge tools/architecture/bpmn_gen.py" exporterVersion="1.0">' % (NS, P["id"].replace("-", "_"))]
    o.append('  <bpmn:collaboration id="%s">' % cid)
    o.append("    <bpmn:documentation>%s</bpmn:documentation>" % q(P["title"] + " — " + P["summary"]))
    for p in pools:
        o.append('    <bpmn:participant id="Participant_%s" name="%s" processRef="Process_%s" />' % (p["id"], q(p["name"]), p["id"]))
    for m in P["messages"]:
        o.append('    <bpmn:messageFlow id="%s"%s sourceRef="%s" targetRef="%s" />' % (
            mid[m["id"]], (' name="%s"' % q(m["label"])) if m.get("label") else "", xid[m["src"]], xid[m["dst"]]))
    o.append("  </bpmn:collaboration>")
    for p in pools:
        o.append('  <bpmn:process id="Process_%s" isExecutable="false">' % p["id"])
        o.append('    <bpmn:laneSet id="LaneSet_%s">' % p["id"])
        for l in p["lanes"]:
            o.append('      <bpmn:lane id="Lane_%s" name="%s">' % (l["id"], q(l["name"])))
            for i in order:
                if nodes[i]["lane"] == l["id"]:
                    o.append("        <bpmn:flowNodeRef>%s</bpmn:flowNodeRef>" % xid[i])
            o.append("      </bpmn:lane>")
        o.append("    </bpmn:laneSet>")
        for i in order:
            n = nodes[i]
            if n["pool"] != p["id"]:
                continue
            t = n["type"]
            attrs = ' id="%s"' % xid[i]
            if n["label"]:
                attrs += ' name="%s"' % q(n["label"])
            kids = []
            if n.get("doc"):
                kids.append("<bpmn:documentation>%s</bpmn:documentation>" % q(n["doc"]))
            if t in TASKS:
                tag = TASK_TAG[t]
            elif t in GATES:
                tag = GATE_TAG[t]
                if t in ("xor", "or"):
                    dflt = [f for f in seq_out[i] if f.get("default")]
                    if dflt:
                        attrs += ' default="%s"' % fid[dflt[0]["id"]]
            else:
                base, _, kind = t.partition(":")
                tag = EVENT_TAG[base][0]
                if base == "boundary":
                    attrs += ' cancelActivity="true" attachedToRef="%s"' % xid[n["attach"]]
            for f in seq_in[i]:
                kids.append("<bpmn:incoming>%s</bpmn:incoming>" % fid[f["id"]])
            for f in seq_out[i]:
                kids.append("<bpmn:outgoing>%s</bpmn:outgoing>" % fid[f["id"]])
            if t not in TASKS and t not in GATES:
                _, _, kind = t.partition(":")
                if kind:
                    did = "%s_def" % xid[i]
                    if kind == "condition":
                        kids.append('<bpmn:conditionalEventDefinition id="%s"><bpmn:condition xsi:type="bpmn:tFormalExpression" /></bpmn:conditionalEventDefinition>' % did)
                    elif kind == "timer" and n.get("dur"):
                        kids.append('<bpmn:timerEventDefinition id="%s"><bpmn:timeDuration xsi:type="bpmn:tFormalExpression">%s</bpmn:timeDuration></bpmn:timerEventDefinition>' % (did, q(n["dur"])))
                    else:
                        kids.append('<%s id="%s" />' % (EVENT_DEF[kind], did))
            if kids:
                o.append("    <%s%s>" % (tag, attrs))
                o.extend("      " + k for k in kids)
                o.append("    </%s>" % tag)
            else:
                o.append("    <%s%s />" % (tag, attrs))
        for f in P["flows"]:
            if nodes[f["src"]]["pool"] == p["id"]:
                o.append('    <bpmn:sequenceFlow id="%s"%s sourceRef="%s" targetRef="%s" />' % (
                    fid[f["id"]], (' name="%s"' % q(f["label"])) if f.get("label") else "", xid[f["src"]], xid[f["dst"]]))
        o.append("  </bpmn:process>")
    pid = P["id"].replace("-", "_")
    o.append('  <bpmndi:BPMNDiagram id="BPMNDiagram_%s">' % pid)
    o.append('    <bpmndi:BPMNPlane id="BPMNPlane_%s" bpmnElement="%s">' % (pid, cid))
    for p in pools:
        o.append('      <bpmndi:BPMNShape id="Participant_%s_di" bpmnElement="Participant_%s" isHorizontal="true">' % (p["id"], p["id"]))
        o.append('        <dc:Bounds x="%d" y="%d" width="%d" height="%d" />' % (POOL_X, p["y"], pool_w, p["h"]))
        o.append("      </bpmndi:BPMNShape>")
        for l in p["lanes"]:
            o.append('      <bpmndi:BPMNShape id="Lane_%s_di" bpmnElement="Lane_%s" isHorizontal="true">' % (l["id"], l["id"]))
            o.append('        <dc:Bounds x="%d" y="%d" width="%d" height="%d" />' % (LANE_X, l["y"], pool_w - (LANE_X - POOL_X), l["h"]))
            o.append("      </bpmndi:BPMNShape>")
    for i in order:
        n = nodes[i]
        r = node_rect(n)
        fill, stroke = kind_colors(pool_of[n["pool"]]["kind"], "task" if n["type"] in TASKS else "other")
        extra = ' bioc:stroke="%s" bioc:fill="%s"' % (stroke, fill)
        if n["type"] == "xor":
            extra = ' isMarkerVisible="true"' + extra
        o.append('      <bpmndi:BPMNShape id="%s_di" bpmnElement="%s"%s>' % (xid[i], xid[i], extra))
        o.append('        <dc:Bounds x="%d" y="%d" width="%d" height="%d" />' % (r[0], r[1], r[2] - r[0], r[3] - r[1]))
        if n["lbl"]:
            lb = n["lbl"]
            o.append("        <bpmndi:BPMNLabel>")
            o.append('          <dc:Bounds x="%d" y="%d" width="%d" height="%d" />' % (lb[0], lb[1], lb[2] - lb[0], lb[3] - lb[1]))
            o.append("        </bpmndi:BPMNLabel>")
        o.append("      </bpmndi:BPMNShape>")
    for _, e in sorted(L["edges"], key=lambda e: (e[0] == 2, e[1]["id"])):
        is_msg = e["id"] in mid
        eid = mid[e["id"]] if is_msg else fid[e["id"]]
        o.append('      <bpmndi:BPMNEdge id="%s_di" bpmnElement="%s">' % (eid, eid))
        for x, y in e["pts"]:
            o.append('        <di:waypoint x="%d" y="%d" />' % (x, y))
        if e.get("lbl"):
            lb = e["lbl"]
            o.append("        <bpmndi:BPMNLabel>")
            o.append('          <dc:Bounds x="%d" y="%d" width="%d" height="%d" />' % (lb[0], lb[1], lb[2] - lb[0], lb[3] - lb[1]))
            o.append("        </bpmndi:BPMNLabel>")
        o.append("      </bpmndi:BPMNEdge>")
    o.append("    </bpmndi:BPMNPlane>")
    o.append("  </bpmndi:BPMNDiagram>")
    o.append("</bpmn:definitions>")
    return "\n".join(o) + "\n"


# ------------------------------------------------------------------------------------------------ contrôles
def lint(P, L):
    msgs = list(L["warn"])
    nodes = L["nodes"]
    n = len(P["nodes"])
    if not 12 <= n <= 30:
        msgs.append("%d nœuds (attendu : de 12 à 30)" % n)
    for node in nodes.values():
        t = node["type"]
        if t in TASKS and len(wrap(node["label"], 98, 12)) > (4 if t == "task" else 3):
            msgs.append("tâche au libellé trop long (%d lignes) : %s" % (len(wrap(node["label"], 98, 12)), node["label"]))
        if node["lbl"] and node["label"] and node["lbl"][3] - node["lbl"][1] > 45:
            msgs.append("libellé externe sur plus de 3 lignes : %s" % node["label"])
    for pool in P["pools"]:
        if text_w(pool["name"], 12) > pool["h"] - 2:
            msgs.append("nom de pool trop long pour sa hauteur : %s" % pool["name"])
        for lane in pool["lanes"]:
            if len(pool["lanes"]) > 1 or True:
                if text_w(lane["name"], 12) > lane["h"] - 16:
                    msgs.append("nom de lane trop long pour sa hauteur (%d px) : %s" % (lane["h"], lane["name"]))
    rects = {i: node_rect(nd) for i, nd in nodes.items()}
    for _, e in L["edges"]:
        for p, qq in zip(e["pts"], e["pts"][1:]):
            for i, r in rects.items():
                if i in (e["src"], e["dst"]):
                    continue
                if seg_hits_rect(p, qq, r, 0):
                    msgs.append("le flux %s traverse le nœud %s" % (e["id"], i))
    labels = [(i, nd["lbl"]) for i, nd in nodes.items() if nd["lbl"]]
    for k, (i, r) in enumerate(labels):
        for j, rr in rects.items():
            if j != i and rects_hit(r, rr, 0):
                msgs.append("le libellé de %s recouvre le nœud %s" % (i, j))
        for i2, r2 in labels[k + 1:]:
            if rects_hit(r, r2, 0):
                msgs.append("les libellés de %s et %s se recouvrent" % (i, i2))
    return sorted(set(msgs))


def counts(P, L):
    return {"noeuds": len(P["nodes"]), "pools": len(P["pools"]), "lanes": sum(len(p["lanes"]) for p in P["pools"]),
            "flux": len(P["flows"]), "messages": len(P["messages"])}


# ------------------------------------------------------------------------------------------------ vérification des fichiers
def verify_file(path):
    """Contrôles de structure d'un fichier .bpmn : XML bien formé, ids uniques, références résolues, un BPMNShape par nœud, pool et lane,
    un BPMNEdge par flux, flux orthogonaux qui partent et arrivent sur le bord de leurs formes, flux de message entre deux pools."""
    import xml.etree.ElementTree as ET
    B = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
    D = "{http://www.omg.org/spec/BPMN/20100524/DI}"
    C = "{http://www.omg.org/spec/DD/20100524/DC}"
    DI = "{http://www.omg.org/spec/DD/20100524/DI}"
    errs = []
    root = ET.parse(path).getroot()
    ids = {}
    for el in root.iter():
        i = el.get("id")
        if i is not None:
            if i in ids:
                errs.append("id en double : " + i)
            ids[i] = el
    flow_tags = {"startEvent", "endEvent", "intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent", "task", "userTask", "serviceTask",
                 "sendTask", "receiveTask", "exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway"}
    nodes = [el for el in root.iter() if el.tag.startswith(B) and el.tag[len(B):] in flow_tags]
    parts = list(root.iter(B + "participant"))
    lanes = list(root.iter(B + "lane"))
    seqs = list(root.iter(B + "sequenceFlow"))
    msgs = list(root.iter(B + "messageFlow"))
    shapes = list(root.iter(D + "BPMNShape"))
    edges = list(root.iter(D + "BPMNEdge"))
    if len(shapes) != len(nodes) + len(parts) + len(lanes):
        errs.append("BPMNShape : %d au lieu de %d (nœuds %d + pools %d + lanes %d)" % (len(shapes), len(nodes) + len(parts) + len(lanes), len(nodes), len(parts), len(lanes)))
    if len(edges) != len(seqs) + len(msgs):
        errs.append("BPMNEdge : %d au lieu de %d" % (len(edges), len(seqs) + len(msgs)))
    for el in list(shapes) + list(edges):
        if el.get("bpmnElement") not in ids:
            errs.append("bpmnElement inconnu : %s" % el.get("bpmnElement"))
    for el in parts + lanes:
        sh = [s for s in shapes if s.get("bpmnElement") == el.get("id")]
        if len(sh) != 1 or sh[0].get("isHorizontal") != "true":
            errs.append("pool ou lane sans forme horizontale : " + el.get("id"))
    node_ids = {n.get("id") for n in nodes}
    in_lane = defaultdict(int)
    for l in lanes:
        for ref in l.iter(B + "flowNodeRef"):
            in_lane[ref.text] += 1
            if ref.text not in node_ids:
                errs.append("flowNodeRef inconnu : " + str(ref.text))
    for n in node_ids:
        if in_lane[n] != 1:
            errs.append("nœud dans %d lanes : %s" % (in_lane[n], n))
    proc_of = {}
    for proc in root.iter(B + "process"):
        for el in proc:
            if el.get("id") in node_ids:
                proc_of[el.get("id")] = proc.get("id")
    for f in seqs:
        if f.get("sourceRef") not in node_ids or f.get("targetRef") not in node_ids:
            errs.append("flux de séquence sans extrémité : " + f.get("id"))
        elif proc_of[f.get("sourceRef")] != proc_of[f.get("targetRef")]:
            errs.append("flux de séquence entre deux pools : " + f.get("id"))
    for m in msgs:
        s, t = m.get("sourceRef"), m.get("targetRef")
        if s not in node_ids or t not in node_ids or proc_of[s] == proc_of[t]:
            errs.append("flux de message invalide : " + m.get("id"))
    bounds = {}
    for s in shapes:
        b = s.find(C + "Bounds")
        bounds[s.get("bpmnElement")] = (float(b.get("x")), float(b.get("y")), float(b.get("x")) + float(b.get("width")), float(b.get("y")) + float(b.get("height")))
    for e in edges:
        el = ids[e.get("bpmnElement")]
        pts = [(float(w.get("x")), float(w.get("y"))) for w in e.findall(DI + "waypoint")]
        if len(pts) < 2:
            errs.append("moins de deux points : " + e.get("bpmnElement"))
            continue
        for a, b in zip(pts, pts[1:]):
            if a[0] != b[0] and a[1] != b[1]:
                errs.append("segment oblique : " + e.get("bpmnElement"))
        for pt, ref in ((pts[0], el.get("sourceRef")), (pts[-1], el.get("targetRef"))):
            r = bounds.get(ref)
            if r is None:
                continue
            on_edge = (abs(pt[0] - r[0]) < 1.01 or abs(pt[0] - r[2]) < 1.01) and r[1] - 1 <= pt[1] <= r[3] + 1 or \
                      (abs(pt[1] - r[1]) < 1.01 or abs(pt[1] - r[3]) < 1.01) and r[0] - 1 <= pt[0] <= r[2] + 1
            if not on_edge:
                errs.append("l'extrémité de %s n'est pas sur le bord de %s" % (e.get("bpmnElement"), ref))
    return errs, {"noeuds": len(nodes), "pools": len(parts), "lanes": len(lanes), "shapes": len(shapes), "flux": len(seqs), "messages": len(msgs), "edges": len(edges)}


def verify_all():
    bad = 0
    files = sorted(OUT_DIR.glob("*.bpmn"))
    if not files:
        print("aucun fichier .bpmn dans " + str(OUT_DIR))
        return 1
    for f in files:
        errs, c = verify_file(f)
        print("%-26s nœuds %2d  pools %d  lanes %d  BPMNShape %2d  flux %2d  messages %2d  BPMNEdge %2d  %s" % (
            f.name, c["noeuds"], c["pools"], c["lanes"], c["shapes"], c["flux"], c["messages"], c["edges"], "OK" if not errs else "%d ERREUR(S)" % len(errs)))
        for e in errs[:10]:
            print("    - " + e)
        bad += bool(errs)
    return 1 if bad else 0


# ------------------------------------------------------------------------------------------------ page
def french(s):
    """Espaces insécables de la typographie française (hors code) : avant : ; ? ! », après «."""
    s = re.sub(r"(?<=\S) ([:;?!»])", "&nbsp;\\1", s)
    s = re.sub(r"(«) ", "\\1&nbsp;", s)
    return s.replace("'", "’")


def inline_code(s):
    s = escape(s)
    parts = re.split(r"(`[^`]+`)", s)
    return "".join("<code>%s</code>" % p[1:-1] if p.startswith("`") and p.endswith("`") and len(p) > 1 else french(p) for p in parts)


def card_html(P, L, k):
    c = counts(P, L)
    sec = "10.%d" % (k + 1)
    pid = P["id"]
    o = []
    o.append('<article class="proc" id="proc-%s" aria-labelledby="h-proc-%s">' % (pid, pid))
    o.append('  <div class="proc-head"><span class="n" aria-hidden="true">§&nbsp;%s</span><h3 id="h-proc-%s">%s</h3></div>' % (sec, pid, escape(P["title"])))
    o.append("  <p class=\"proc-sum\">%s</p>" % inline_code(P["summary"]))
    o.append('  <ul class="actors" aria-label="Acteurs du processus">')
    for p in P["pools"]:
        o.append('    <li class="act act-%s">%s</li>' % (p["kind"], escape(p["name"])))
    o.append("  </ul>")
    o.append("  <figure class=\"bpmn-fig\">")
    o.append('    <div class="bpmn-bar" role="group" aria-label="Commandes du diagramme %s">' % escape(P["title"]))
    o.append('      <button type="button" class="btn" data-act="out" aria-label="Réduire le diagramme">&minus;</button>')
    o.append('      <button type="button" class="btn" data-act="in" aria-label="Agrandir le diagramme">+</button>')
    o.append('      <button type="button" class="btn" data-act="fit">Ajuster</button>')
    o.append('      <button type="button" class="btn" data-act="copy">Copier le XML</button>')
    o.append('      <span class="bpmn-status" role="status" aria-live="polite"></span>')
    o.append("    </div>")
    o.append('    <div class="bpmn-view" data-bpmn="%s" tabindex="0" role="region" aria-label="Diagramme BPMN : %s. Glisser pour déplacer, flèches du clavier, + et - pour zoomer.">' % (pid, escape(P["title"])))
    o.append("      <noscript>Le diagramme demande JavaScript ; le fichier BPMN reste lisible dans un modeleur.</noscript>")
    o.append("    </div>")
    o.append('    <figcaption>Fichier <code>docs/architecture/bpmn/%s.bpmn</code> : %d nœuds, %d flux de séquence, %d flux de message, %d pools et %d lanes. Glisser pour déplacer, Ctrl + molette pour zoomer.</figcaption>' % (
        pid, c["noeuds"], c["flux"], c["messages"], c["pools"], c["lanes"]))
    o.append("  </figure>")
    if P.get("code"):
        o.append('  <div class="scroll" tabindex="0" role="region" aria-label="Repères dans le dépôt pour %s">' % escape(P["title"]))
        o.append("    <table>")
        o.append('      <caption class="sr-only">Repères dans le dépôt : %s</caption>' % escape(P["title"]))
        o.append('      <thead><tr><th scope="col">Étape ou règle</th><th scope="col">Repère dans le dépôt</th></tr></thead>')
        o.append("      <tbody>")
        for what, where in P["code"]:
            o.append('        <tr><th scope="row">%s</th><td>%s</td></tr>' % (inline_code(what), inline_code(where)))
        o.append("      </tbody>")
        o.append("    </table>")
        o.append("  </div>")
    if P.get("sources"):
        o.append('  <p class="note">Sources lues : %s.</p>' % "; ".join(inline_code(s) for s in P["sources"]))
    o.append("</article>")
    return "\n".join(o)


def js_str(s):
    return json.dumps(s, ensure_ascii=False).replace("</", "<\\/").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")


def script_block(xmls):
    items = ",\n".join("  %s: %s" % (js_str(k), js_str(v)) for k, v in xmls.items())
    return "<script>\n// Généré par tools/architecture/bpmn_gen.py : ne pas modifier à la main.\nconst BPMN_XML = {\n%s\n};\n</script>" % items


def replace_between(text, begin, end, new):
    a = text.find(begin)
    b = text.find(end)
    if a < 0 or b < 0 or b < a:
        raise SystemExit("marqueurs introuvables dans la page : %s … %s" % (begin, end))
    return text[:a + len(begin)] + "\n" + new + "\n" + text[b:]


def build_all(only=None):
    out = {}
    for k, P in enumerate(processes.PROCESSES):
        if only and P["id"] != only:
            continue
        L = layout(P)
        out[P["id"]] = (P, L, bpmn_xml(P, L), k)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="ne rien écrire ; échoue si les fichiers ou la page diffèrent")
    ap.add_argument("--only", help="un seul processus (ne met pas la page à jour)")
    ap.add_argument("--lint", action="store_true", help="affiche les avertissements de mise en page")
    ap.add_argument("--no-page", action="store_true", help="n'écrit que les fichiers .bpmn")
    ap.add_argument("--verify", action="store_true", help="vérifie la structure des fichiers .bpmn déjà écrits")
    args = ap.parse_args(argv)
    if args.verify:
        return verify_all()
    built = build_all(args.only)
    bad = 0
    for pid, (P, L, xml, k) in built.items():
        c = counts(P, L)
        msgs = lint(P, L)
        print("%-22s %2d nœuds  %2d flux  %d messages  %d pools  %d lanes  %6d o%s" % (
            pid, c["noeuds"], c["flux"], c["messages"], c["pools"], c["lanes"], len(xml.encode("utf-8")), "  [%d avertissement(s)]" % len(msgs) if msgs else ""))
        if args.lint:
            for m in msgs:
                print("    - " + m)
    if args.only:
        for pid, (P, L, xml, k) in built.items():
            OUT_DIR.mkdir(parents=True, exist_ok=True)
            (OUT_DIR / (pid + ".bpmn")).write_text(xml, encoding="utf-8")
        return 0
    xmls = {pid: v[2] for pid, v in built.items()}
    cards = "\n\n".join(card_html(P, L, k) for pid, (P, L, xml, k) in built.items())
    page_new = None
    if PAGE.exists() and not args.no_page:
        text = PAGE.read_text(encoding="utf-8")
        if "<!-- BPMN:BEGIN -->" in text and "<!-- BPMN-CARDS:BEGIN -->" in text:
            text = replace_between(text, "<!-- BPMN-CARDS:BEGIN -->", "<!-- BPMN-CARDS:END -->", cards)
            text = replace_between(text, "<!-- BPMN:BEGIN -->", "<!-- BPMN:END -->", script_block(xmls))
            page_new = text
        else:
            print("avertissement : la page n'a pas les marqueurs BPMN:BEGIN et BPMN-CARDS:BEGIN, elle n'est pas mise à jour")
    if args.check:
        for pid, xml in xmls.items():
            f = OUT_DIR / (pid + ".bpmn")
            if not f.exists() or f.read_text(encoding="utf-8") != xml:
                print("PAS À JOUR : " + str(f.relative_to(ROOT)))
                bad += 1
        if page_new is not None and PAGE.read_text(encoding="utf-8") != page_new:
            print("PAS À JOUR : " + str(PAGE.relative_to(ROOT)))
            bad += 1
        print("conforme" if not bad else "%d fichier(s) à régénérer : python3 tools/architecture/bpmn_gen.py" % bad)
        return 1 if bad else 0
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for pid, xml in xmls.items():
        (OUT_DIR / (pid + ".bpmn")).write_text(xml, encoding="utf-8")
    if page_new is not None:
        PAGE.write_text(page_new, encoding="utf-8")
        print("page mise à jour : " + str(PAGE.relative_to(ROOT)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
