"""Builder of CastBridge « Apprendre » vector animations (docs/LEARN.md § Animations).

    a = Anim(480, 300)                     # step mode by default
    dot = a.add(circle(60, 150, 12, "blue"))
    with a.step("Le point avance.") as s:   # one pause of the step mode
        s.move(dot, dx=300, d=1.5)
    block = a.block(alt="Un point bleu avance de gauche à droite.", caption="Le point avance")

`block` is a complete « illustration » block: the static fallback `figure` (the last frame, computed here exactly like the
apps compute it) and the `animation` object. Output is compact JSON, a few KB. No dependency (standard library only).
"""
import json, math

PALETTE = {"ink", "black", "white", "grey", "lightgrey", "red", "blue", "green", "yellow", "orange", "purple", "brown", "pink",
           "cyan", "lightblue", "lightgreen", "lightyellow", "lightorange", "paper"}


def r(x, nd=1):
    """Short numbers: 1 decimal, no trailing .0."""
    v = round(float(x), nd)
    return int(v) if v == int(v) else v


# ---------------------------------------------------------------------------------------------- shapes (same keys as Figure.Shapes)
def _clean(d):
    return {k: v for k, v in d.items() if v is not None}

def line(x1, y1, x2, y2, color="ink", width=2, dash=None, arrow=None):
    return _clean(dict(t="line", x1=r(x1), y1=r(y1), x2=r(x2), y2=r(y2), color=color, width=width if width != 2 else None, dash=dash or None, arrow=arrow))

def circle(cx, cy, rad, fill=None, stroke="ink", width=2):
    return _clean(dict(t="circle", cx=r(cx), cy=r(cy), r=r(rad), fill=fill, stroke=None if stroke == "ink" else (stroke if stroke else "none"), width=width if width != 2 else None))

def rect(x, y, w, h, fill=None, stroke="ink", width=2, radius=None):
    return _clean(dict(t="rect", x=r(x), y=r(y), w=r(w), h=r(h), fill=fill, stroke=None if stroke == "ink" else (stroke if stroke else "none"), width=width if width != 2 else None, radius=radius))

def poly(pts, fill=None, stroke="ink", width=2, closed=True):
    flat = [r(v) for p in pts for v in p]
    return _clean(dict(t="poly", pts=flat, fill=fill, stroke=None if stroke == "ink" else (stroke if stroke else "none"), width=width if width != 2 else None, closed=None if closed else False))

def text(x, y, s, size=16, color="ink", anchor="middle", bold=False):
    return _clean(dict(t="text", x=r(x), y=r(y), text=s, size=size if size != 16 else None, color=None if color == "ink" else color,
                       anchor=None if anchor == "middle" else anchor, bold=True if bold else None))

def path(d, fill=None, stroke="ink", width=2):
    return _clean(dict(t="path", d=d, fill=fill, stroke=None if stroke == "ink" else (stroke if stroke else "none"), width=width if width != 2 else None))


# ---------------------------------------------------------------------------------------------- the builder
class Anim:
    def __init__(self, w=480, h=300, mode="steps", loop=False):
        assert mode in ("steps", "auto")
        self.w, self.h, self.mode, self.loop = w, h, mode, loop
        self.items, self.acts, self.stops = [], [], []
        self.t = 0.0           # start of the next step
        self._n = 0
        self.groups = {}

    # elements ------------------------------------------------------------------------------------------------
    def add(self, shape, id=None, g=None, alpha=None, draw=None, typed=None, v=None, dec=None, wipe=None, dir=None, pivot=None):
        """Adds an element; returns its id. alpha/draw/typed/wipe are the initial state (0 = hidden); v the initial counter value."""
        if id is None:
            id = _short(self._n); self._n += 1
        e = dict(shape)
        e["id"] = id
        for k, val in (("g", g), ("alpha", alpha), ("draw", draw), ("typed", typed), ("v", v), ("dec", dec), ("wipe", wipe), ("dir", dir)):
            if val is not None:
                e[k] = val
        if pivot is not None:
            e["pivot"] = [r(pivot[0]), r(pivot[1])]
        assert all(i["id"] != id for i in self.items), "duplicate id " + id
        self.items.append(e)
        return id

    def group_pivot(self, g, x, y):
        self.groups[g] = {"pivot": [r(x), r(y)]}

    def act(self, op, on, at, d=0.6, ease=None, **kw):
        assert d == 0 or d >= 0.2, f"duration {d} s: 0 or >= 0.2"
        a = {"at": r(at, 2), "d": r(d, 2), "op": op, "on": on}
        if ease and ease != "inout":
            a["ease"] = ease
        for k, v in kw.items():
            a[k] = r(v, 2) if isinstance(v, (int, float)) and not isinstance(v, bool) else v
        self.acts.append(a)
        return a["at"] + a["d"]

    def step(self, say, pause=0.0):
        return _Step(self, say, pause)

    def mark(self, say):
        """A caption stop at the current time without actions (rare)."""
        self.stops.append({"at": r(self.t, 2), "say": say})

    # output --------------------------------------------------------------------------------------------------
    def animation(self):
        out = {"mode": self.mode}
        if (self.w, self.h) != (480, 300):
            pass  # size always equals the fallback figure's: not repeated
        if self.loop:
            out["loop"] = True
        if self.groups:
            out["groups"] = self.groups
        out["items"] = self.items
        out["do"] = self.acts
        if self.stops:
            out["steps"] = self.stops
        return out

    def figure(self):
        """Static fallback: the last frame, as a « shapes » figure."""
        return {"kind": "shapes", "w": self.w, "h": self.h, "items": final_shapes(self.items, self.acts)}

    def block(self, alt, caption=None, fallback=None):
        b = {"type": "illustration"}
        if caption:
            b["caption"] = caption
        b["alt"] = alt
        b["figure"] = fallback or self.figure()
        b["animation"] = self.animation()
        return b


def _short(n):
    s = ""
    n += 1
    while n:
        n, m = divmod(n - 1, 26)
        s = chr(97 + m) + s
    return s


class _Step:
    def __init__(self, anim, say, pause):
        self.a, self.say, self.pause = anim, say, pause
        self.start = anim.t
        self.end = anim.t

    def __enter__(self):
        return self

    def do(self, op, on, d=0.6, delay=0.0, ease=None, **kw):
        end = self.a.act(op, on, self.start + delay, d, ease, **kw)
        self.end = max(self.end, end)
        return self

    # shortcuts
    def show(self, on, d=0.4, delay=0.0): return self.do("fade", on, d, delay, a=1)
    def hide(self, on, d=0.4, delay=0.0): return self.do("fade", on, d, delay, a=0)
    def draw(self, on, d=1.0, delay=0.0, ease="linear"): return self.do("draw", on, d, delay, ease, p=1)
    def type(self, on, d=1.0, delay=0.0): return self.do("type", on, d, delay, "linear", p=1)
    def wipe(self, on, d=1.0, delay=0.0, p=1, ease=None): return self.do("wipe", on, d, delay, ease, p=p)
    def move(self, on, dx=None, dy=None, d=0.8, delay=0.0, ease=None):
        kw = {}
        if dx is not None: kw["dx"] = dx
        if dy is not None: kw["dy"] = dy
        return self.do("move", on, d, delay, ease, **kw)
    def scale(self, on, s, d=0.6, delay=0.0): return self.do("scale", on, d, delay, s=s)
    def rotate(self, on, deg, d=0.8, delay=0.0, ease=None): return self.do("rotate", on, d, delay, ease, deg=deg)
    def color(self, on, fill=None, stroke=None, d=0.5, delay=0.0):
        kw = {}
        if fill: kw["fill"] = fill
        if stroke: kw["stroke"] = stroke
        return self.do("color", on, d, delay, **kw)
    def count(self, on, v, d=1.0, delay=0.0, ease="linear"): return self.do("count", on, d, delay, ease, v=v)
    def wait(self, d): self.end = max(self.end, self.start + d); return self

    def __exit__(self, *exc):
        if exc[0]:
            return False
        end = self.end + self.pause
        if end <= self.start:
            end = self.start + 0.2
        self.a.t = end
        self.a.stops.append({"at": r(end, 2), "say": self.say})
        return False


# ---------------------------------------------------------------------------------------------- final frame (static fallback)
def final_shapes(items, acts):
    """Applies the final value of every property to the base shapes (translation, scale, rotation about the pivot, colors,
    fade-out, counters, full text); matches what the apps draw at the end of the timeline."""
    last = {}   # (id or group, prop) -> value, chronological
    group_of = {i["id"]: i.get("g") for i in items}
    for a in sorted(acts, key=lambda a: a["at"]):
        for i in items:
            if i["id"] == a["on"] or i.get("g") == a["on"]:
                k = i["id"]
                op = a["op"]
                vals = {"move": [("tx", a.get("dx")), ("ty", a.get("dy"))], "scale": [("s", a.get("s"))], "rotate": [("rot", a.get("deg"))],
                        "fade": [("alpha", a.get("a", 1))], "color": [("fill", a.get("fill")), ("stroke", a.get("stroke"))],
                        "count": [("v", a.get("v"))], "wipe": [], "draw": [], "type": []}[op]
                for p, v in vals:
                    if v is not None:
                        last[(k, p)] = v
    out = []
    for i in items:
        k = i["id"]
        g = lambda p, d=None: last.get((k, p), d)
        if g("alpha", i.get("alpha", 1)) <= 0.004:
            continue
        # never revealed: initial draw / typed / wipe at 0 without an action that completes it
        hidden = False
        for prop, op in (("draw", "draw"), ("typed", "type"), ("wipe", "wipe")):
            if i.get(prop, 1) == 0 and not any(a["op"] == op and (a["on"] == k or a["on"] == i.get("g")) and a.get("p", 1) > 0 for a in acts):
                hidden = True
        if hidden:
            continue
        s = {kk: v for kk, v in i.items() if kk not in ("id", "g", "alpha", "draw", "typed", "v", "dec", "wipe", "dir", "pivot")}
        if "fill" in s or s["t"] == "text":
            fk = "color" if s["t"] == "text" else "fill"
            if g("fill") is not None:
                s[fk] = g("fill")
        if g("stroke") is not None:
            s["color" if s["t"] == "line" else "stroke"] = g("stroke")
        if s["t"] == "text" and "{v}" in s["text"]:
            v = g("v", i.get("v", 0)); dec = i.get("dec", 0)
            s["text"] = s["text"].replace("{v}", (f"{v:.{dec}f}".replace(".", ",") if dec else str(int(round(v)))))
        tx, ty, sc, rot = g("tx", 0), g("ty", 0), g("s", 1), g("rot", 0)
        if (tx, ty, sc, rot) != (0, 0, 1, 0):
            s = _transform(s, i, tx, ty, sc, rot)
        out.append(s)
    return out


def _bbox_center(s):
    t = s["t"]
    if t == "circle": return s["cx"], s["cy"]
    if t == "rect": return s["x"] + s["w"] / 2, s["y"] + s["h"] / 2
    if t == "line": return (s["x1"] + s["x2"]) / 2, (s["y1"] + s["y2"]) / 2
    if t == "poly":
        xs, ys = s["pts"][0::2], s["pts"][1::2]
        return (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    if t == "text": return s["x"], s["y"]
    raise ValueError("transform of a " + t + " is not supported by the fallback: give the figure explicitly")


def _transform(s, item, tx, ty, sc, rot):
    px, py = item.get("pivot") or _bbox_center(s)
    c, sn = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    X = lambda x, y: px + sc * (c * (x - px) - sn * (y - py)) + tx
    Y = lambda x, y: py + sc * (sn * (x - px) + c * (y - py)) + ty
    s = dict(s); t = s["t"]
    if t == "circle": s["cx"], s["cy"], s["r"] = r(X(s["cx"], s["cy"])), r(Y(s["cx"], s["cy"])), r(s["r"] * sc)
    elif t == "line": s["x1"], s["y1"], s["x2"], s["y2"] = r(X(s["x1"], s["y1"])), r(Y(s["x1"], s["y1"])), r(X(s["x2"], s["y2"])), r(Y(s["x2"], s["y2"]))
    elif t == "text": s["x"], s["y"] = r(X(s["x"], s["y"])), r(Y(s["x"], s["y"])); s["size"] = r(s.get("size", 16) * sc)
    elif t == "rect" and rot == 0:
        s["x"], s["y"], s["w"], s["h"] = r(X(s["x"], s["y"])), r(Y(s["x"], s["y"])), r(s["w"] * sc), r(s["h"] * sc)
    elif t == "rect":
        x, y, w, h = s["x"], s["y"], s["w"], s["h"]
        s = {"t": "poly", "pts": [r(v) for q in ((x, y), (x + w, y), (x + w, y + h), (x, y + h)) for v in (X(*q), Y(*q))],
             **{k: v for k, v in s.items() if k in ("fill", "stroke", "width")}}
    elif t == "poly":
        pts = s["pts"]; s["pts"] = [r(v) for k in range(0, len(pts), 2) for v in (X(pts[k], pts[k + 1]), Y(pts[k], pts[k + 1]))]
    else:
        raise ValueError("transform of a " + t + " is not supported by the fallback")
    return s


def dumps(block, indent=None):
    return json.dumps(block, ensure_ascii=False, separators=(",", ":") if indent is None else None, indent=indent)


def size_bytes(block):
    return len(dumps(block["animation"]).encode("utf-8"))
