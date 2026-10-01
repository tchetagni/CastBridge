"""Ready-made didactic animations (docs/LEARN.md § Animations). Each template builds an `Anim` from a few parameters and
returns a complete « illustration » block (static fallback + animation). Texts are French; every template has an `example()`
in EXAMPLES, rendered and linted by the tests (LearnAnimationExamplesTest) and by tools/anim/build.py."""
import math, random
from animlib import *

W, H = 480, 300


def _fmt(n):
    return str(int(n)) if float(n) == int(n) else str(n).replace(".", ",")


# 1 ------------------------------------------------------------------------------------------------ number line jumps
def number_line_jumps(start=3, jumps=(4, -2, 5), caption="Additions et soustractions sur la droite graduée"):
    pos = [start]
    for j in jumps:
        pos.append(pos[-1] + j)
    lo, hi = min(pos) - 1, max(pos) + 1
    X = lambda n: 40 + (n - lo) * 400.0 / (hi - lo)
    a = Anim(W, H)
    a.add(line(30, 200, 450, 200, "ink", 2, arrow="end"))
    for n in range(lo, hi + 1):
        a.add(line(X(n), 194, X(n), 206)); a.add(text(X(n), 224, str(n), 14))
    tok = a.add(circle(X(start), 184, 9, "red", "ink"), alpha=0)
    expr = str(start)
    with a.step(f"On part de {start}.") as s:
        s.show(tok)
    for k, j in enumerate(jumps):
        x1, x2 = X(pos[k]), X(pos[k + 1])
        arc = a.add(path(f"M{r(x1)} 176Q{r((x1 + x2) / 2)} 110 {r(x2)} 176", None, "blue", 2.5), draw=0)
        lab = a.add(text((x1 + x2) / 2, 118, f"{'+' if j > 0 else '−'}{abs(j)}", 18, "blue", bold=True), alpha=0)
        expr += f" {'+' if j > 0 else '−'} {abs(j)}"
        with a.step(f"{'On avance' if j > 0 else 'On recule'} de {abs(j)} : on arrive en {pos[k + 1]}.") as s:
            s.draw(arc, 0.8); s.show(lab, delay=0.5); s.move(tok, dx=x2 - X(start), d=0.8, delay=0.4)
    res = a.add(text(240, 270, f"{expr} = {pos[-1]}", 22, "ink", "start", True), typed=0)
    # centre the result text by estimate: start anchor needed for typing
    a.items[-1]["x"] = r(240 - len(f"{expr} = {pos[-1]}") * 11.0 / 2)
    with a.step(f"Résultat : {pos[-1]}.") as s:
        s.type(res, 1.0)
    return a.block(f"Droite graduée de {lo} à {hi} : un jeton part de {start} et fait des sauts ({', '.join(('+' if j > 0 else '−') + str(abs(j)) for j in jumps)}) jusqu'à {pos[-1]}.", caption)


# 2 ------------------------------------------------------------------------------------------------ fraction area model
def fraction_area(num=3, den=5, caption="Une fraction de l'unité"):
    x0, y0, w, h = 80, 80, 320, 110
    a = Anim(W, H)
    fill = a.add(rect(x0, y0, w * num / den, h, "orange", None), wipe=0)
    a.add(rect(x0, y0, w, h, None, "ink", 3))
    cuts = [a.add(line(x0 + w * k / den, y0, x0 + w * k / den, y0 + h, "ink", 2), alpha=0) for k in range(1, den)]
    lab = a.add(text(240, 262, f"{num}/{den}", 48, "orange", bold=True), alpha=0)
    cap = a.add(text(240, 222, f"{num} part{'s' if num > 1 else ''} sur {den}", 20, "ink"), alpha=0)
    with a.step("Le rectangle représente l'unité (1 entier).") as s:
        s.wait(0.4)
    with a.step(f"On le partage en {den} parts égales.") as s:
        for k, c in enumerate(cuts):
            s.show(c, 0.3, delay=0.25 * k)
        if not cuts:
            s.wait(0.4)
    with a.step(f"On colorie {num} part{'s' if num > 1 else ''}.") as s:
        s.wipe(fill, 1.0, ease="linear"); s.show(cap, 0.4, delay=0.8)
    with a.step(f"La fraction colorée est {num}/{den}.") as s:
        s.show(lab, 0.5)
    return a.block(f"Un rectangle partagé en {den} parts égales dont {num} sont coloriées : la fraction {num}/{den}.", caption)


# 3 ------------------------------------------------------------------------------------------------ multiplication area model
def area_model_mult(rows=3, cols=4, caption="Multiplier, c'est compter des carreaux"):
    cell = 40; x0 = (W - cols * cell) // 2; y0 = 60
    a = Anim(W, H)
    cells = []
    for i in range(rows):
        row = [a.add(rect(x0 + j * cell + 2, y0 + i * cell + 2, cell - 4, cell - 4, "lightblue", "blue", 1.5, 4), g=f"r{i}", alpha=0) for j in range(cols)]
        cells.append(row)
    a.add(text(x0 - 22, y0 + rows * cell / 2 + 6, str(rows), 22, "red", bold=True))
    a.add(text(x0 + cols * cell / 2, y0 - 12, str(cols), 22, "green", bold=True))
    cnt = a.add(text(240, y0 + rows * cell + 40, "{v} carreaux", 24, "ink", bold=True), v=0)
    eq = a.add(text(240, y0 + rows * cell + 76, f"{rows} × {cols} = {rows * cols}", 24, "blue", bold=True), alpha=0)
    for i in range(rows):
        with a.step(f"Ligne {i + 1} : {cols} carreaux.") as s:
            for j, c in enumerate(cells[i]):
                s.show(c, 0.3, delay=0.15 * j)
            s.count(cnt, (i + 1) * cols, 0.15 * cols + 0.3)
    with a.step(f"{rows} lignes de {cols} : {rows} × {cols} = {rows * cols}.") as s:
        s.show(eq, 0.5)
    return a.block(f"Un rectangle de {rows} lignes et {cols} colonnes se remplit ligne par ligne : {rows} × {cols} = {rows * cols} carreaux.", caption)


# 4 ------------------------------------------------------------------------------------------------ angle construction
def angle_construction(deg=60, caption="Construire un angle au rapporteur"):
    vx, vy, L, R = 130, 230, 260, 55
    a = Anim(W, H)
    base = a.add(line(vx, vy, vx + L, vy, "ink", 3), draw=0)
    ray = a.add(line(vx, vy, vx + L, vy, "red", 3), alpha=0, pivot=(vx, vy))
    mid = math.radians(deg / 2)
    arc = a.add(path("M" + "L".join(f"{r(vx + R * math.cos(math.radians(d)))} {r(vy - R * math.sin(math.radians(d)))}" for d in [k * deg / 24 for k in range(25)]), None, "red", 2.5), draw=0)
    lab = a.add(text(vx + (R + 40) * math.cos(mid), vy - (R + 40) * math.sin(mid) + 6, "{v}°", 22, "red", bold=True), alpha=0, v=0)
    a.add(circle(vx, vy, 4, "ink", None))
    with a.step("On trace une demi-droite : c'est un côté de l'angle.") as s:
        s.draw(base, 0.8)
    with a.step(f"On fait pivoter une seconde demi-droite de {deg}° autour du sommet.") as s:
        s.show(ray, 0.2); s.show(lab, 0.2); s.rotate(ray, -deg, max(1.0, deg / 45), 0.2, "linear"); s.count(lab, deg, max(1.0, deg / 45), 0.2)
    with a.step(f"On marque l'angle : il mesure {deg}°.") as s:
        s.draw(arc, 0.8)
    return a.block(f"Deux demi-droites de même origine : l'une horizontale, l'autre tournée de {deg}° ; l'arc de cercle marque l'angle de {deg}°.", caption)


# 5 ------------------------------------------------------------------------------------------------ triangle construction + angle sum
def triangle_construction(A=(100, 230), B=(380, 230), C=(250, 70), caption="Un triangle et la somme de ses angles"):
    a = Anim(W, H)
    sides = [("AB", A, B), ("BC", B, C), ("CA", C, A)]
    ids = [a.add(line(p[0], p[1], q[0], q[1], "blue", 3), draw=0) for _, p, q in sides]
    labs = [a.add(text(A[0] - 20, A[1] + 8, "A", 20, bold=True), alpha=0), a.add(text(B[0] + 20, B[1] + 8, "B", 20, bold=True), alpha=0),
            a.add(text(C[0], C[1] - 14, "C", 20, bold=True), alpha=0)]
    tot = a.add(text(60, 275, "A + B + C = {v}°", 22, "red", "start", True), alpha=0, v=0)
    for k, (nm, p, q) in enumerate(sides):
        with a.step(f"On trace le côté [{nm}].") as s:
            s.draw(ids[k], 0.9); s.show(labs[k], 0.3, delay=0.3)
    with a.step("Les trois angles du triangle ont pour somme 180°.") as s:
        s.show(tot, 0.3); s.count(tot, 180, 1.2, 0.2)
    return a.block("Un triangle ABC dont les trois côtés sont tracés l'un après l'autre ; on rappelle que la somme de ses angles est 180°.", caption)


# 6 ------------------------------------------------------------------------------------------------ function graph tracing
def function_graph_trace(fn=lambda x: x * x, expr="y = x²", xmin=-3, xmax=3, ymin=-1, ymax=9, caption="Tracer la courbe d'une fonction"):
    ml, mr, mt, mb = 50, 450, 30, 270
    X = lambda x: ml + (x - xmin) / (xmax - xmin) * (mr - ml)
    Y = lambda y: mb - (y - ymin) / (ymax - ymin) * (mb - mt)
    a = Anim(W, H)
    ax = [a.add(line(ml, Y(0), mr + 10, Y(0), "grey", 2, arrow="end"), alpha=0), a.add(line(X(0), mb, X(0), mt - 8, "grey", 2, arrow="end"), alpha=0)]
    ticks = []
    for n in range(math.ceil(xmin), math.floor(xmax) + 1):
        if n: ticks.append(a.add(text(X(n), Y(0) + 16, str(n), 12, "grey"), alpha=0))
    for n in range(math.ceil(ymin), math.floor(ymax) + 1, max(1, (ymax - ymin) // 5)):
        if n: ticks.append(a.add(text(X(0) - 8, Y(n) + 4, str(n), 12, "grey", "end"), alpha=0))
    pts = [(X(xmin + (xmax - xmin) * k / 60), Y(fn(xmin + (xmax - xmin) * k / 60))) for k in range(61)]
    pts = [p for p in pts if mt - 4 <= p[1] <= mb + 4]
    curve = a.add(path("M" + "L".join(f"{r(x)} {r(y)}" for x, y in pts), None, "blue", 3), draw=0)
    dot = a.add(circle(pts[0][0], pts[0][1], 6, "red", None), alpha=0)
    lab = a.add(text(mr - 10, mt + 14, expr, 20, "blue", "end", True), alpha=0)
    with a.step("On place le repère : l'axe des abscisses et l'axe des ordonnées.") as s:
        for e in ax + ticks: s.show(e, 0.4)
    # waypoints at equal arc length so the dot follows the growing curve
    cum = [0.0]
    for k in range(1, len(pts)):
        cum.append(cum[-1] + math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1]))
    total_d = 3.2; n = 16
    with a.step(f"On calcule des points de {expr} et on les relie.") as s:
        s.show(dot, 0.2); s.show(lab, 0.5); s.draw(curve, total_d)
        idx = 0
        for w in range(1, n + 1):
            target = cum[-1] * w / n
            while idx < len(cum) - 1 and cum[idx] < target: idx += 1
            s.move(dot, dx=pts[idx][0] - pts[0][0], dy=pts[idx][1] - pts[0][1], d=total_d / n, delay=(w - 1) * total_d / n, ease="linear")
    return a.block(f"Un repère dans lequel on trace point par point la courbe de {expr}.", caption)


# 7 ------------------------------------------------------------------------------------------------ vector addition
def vector_addition(u=(160, -70), v=(110, 90), caption="Additionner deux vecteurs"):
    ox, oy = 90, 150 + max(0, -min(u[1], u[1] + v[1], 0)) * 0.0
    ox, oy = 90, 170
    a = Anim(W, H)
    ua = a.add(line(ox, oy, ox + u[0], oy + u[1], "blue", 3.5, arrow="end"), draw=0)
    ul = a.add(text(ox + u[0] / 2 - 12, oy + u[1] / 2 - 10, "u", 22, "blue", bold=True), alpha=0)
    va = a.add(line(ox + u[0], oy + u[1], ox + u[0] + v[0], oy + u[1] + v[1], "green", 3.5, arrow="end"), draw=0)
    vl = a.add(text(ox + u[0] + v[0] / 2 + 16, oy + u[1] + v[1] / 2 - 8, "v", 22, "green", bold=True), alpha=0)
    ra = a.add(line(ox, oy, ox + u[0] + v[0], oy + u[1] + v[1], "red", 3.5, arrow="end"), draw=0)
    rl = a.add(text(ox + (u[0] + v[0]) / 2 - 20, oy + (u[1] + v[1]) / 2 + 26, "u + v", 22, "red", bold=True), alpha=0)
    a.add(circle(ox, oy, 4, "ink", None))
    with a.step("Le vecteur u part de l'origine.") as s:
        s.draw(ua, 0.9); s.show(ul, 0.3, delay=0.5)
    with a.step("On place v à la suite de u : l'origine de v est l'extrémité de u.") as s:
        s.draw(va, 0.9); s.show(vl, 0.3, delay=0.5)
    with a.step("u + v va de l'origine de u à l'extrémité de v.") as s:
        s.draw(ra, 1.0); s.show(rl, 0.3, delay=0.6)
    return a.block("Deux vecteurs mis bout à bout : u en bleu, puis v en vert ; leur somme u + v, en rouge, relie le début de u à la fin de v.", caption)


# 8 ------------------------------------------------------------------------------------------------ unit conversion
def unit_conversion(value=2.5, unit_from="km", unit_to="m", factor=1000, caption="Convertir des unités"):
    res = value * factor
    dec = 0 if float(res) == int(res) else 1
    a = Anim(W, H)
    left = a.add(text(100, 150, f"{_fmt(value)} {unit_from}", 36, "blue", bold=True), alpha=0)
    arr = a.add(line(165, 142, 305, 142, "grey", 3, arrow="end"), draw=0)
    mul = a.add(text(235, 120, f"× {factor}", 24, "red", bold=True), alpha=0)
    right = a.add(text(390, 150, "{v} " + unit_to, 36, "green", bold=True), alpha=0, v=0, dec=dec)
    eq = a.add(text(240, 240, f"{_fmt(value)} {unit_from} = {_fmt(res)} {unit_to}", 24, "ink", "start", True), typed=0)
    a.items[-1]["x"] = r(240 - len(f"{_fmt(value)} {unit_from} = {_fmt(res)} {unit_to}") * 12.0 / 2)
    with a.step(f"On part de {_fmt(value)} {unit_from}.") as s:
        s.show(left, 0.5)
    with a.step(f"1 {unit_from} = {factor} {unit_to} : on multiplie par {factor}.") as s:
        s.draw(arr, 0.7); s.show(mul, 0.4, delay=0.4)
    with a.step(f"On obtient {_fmt(res)} {unit_to}.") as s:
        s.show(right, 0.2); s.count(right, res, 1.4, 0.1)
    with a.step("L'égalité complète.") as s:
        s.type(eq, 1.0)
    return a.block(f"Conversion de {_fmt(value)} {unit_from} en {unit_to} : on multiplie par {factor} et on obtient {_fmt(res)} {unit_to}.", caption)


# 9 ------------------------------------------------------------------------------------------------ food chain
def food_chain(names=("Herbe", "Sauterelle", "Grenouille", "Serpent"), caption="Une chaîne alimentaire"):
    n = len(names); bw = 88; gap = (W - 2 * 10 - n * bw) / (n - 1) if n > 1 else 0
    xs = [10 + k * (bw + gap) for k in range(n)]
    cols = ["lightgreen", "lightyellow", "lightblue", "lightorange", "pink", "lightgrey"]
    a = Anim(W, H)
    boxes, arrows = [], []
    for k, nm in enumerate(names):
        boxes.append((a.add(rect(xs[k], 120, bw, 56, cols[k % len(cols)], "ink", 2, 8), g=f"o{k}", alpha=0), a.add(text(xs[k] + bw / 2, 154, nm, 15, bold=True), g=f"o{k}", alpha=0)))
        if k:
            arrows.append(a.add(line(xs[k - 1] + bw + 3, 148, xs[k] - 3, 148, "red", 3, arrow="end"), draw=0))
    ener = a.add(circle(xs[0] + bw / 2, 100, 9, "yellow", "orange"), alpha=0)
    elab = a.add(text(xs[0] + bw / 2, 78, "énergie", 14, "orange", bold=True), alpha=0)
    with a.step(f"{names[0]} : le producteur, il fabrique sa matière avec la lumière.") as s:
        s.show("o0", 0.5)
    for k in range(1, n):
        with a.step(f"{names[k]} mange {names[k - 1].lower()}.") as s:
            s.show(f"o{k}", 0.5, delay=0.5); s.draw(arrows[k - 1], 0.5)
    with a.step("La flèche va de l'être mangé vers celui qui le mange : l'énergie circule.") as s:
        s.show(ener, 0.3); s.show(elab, 0.3)
        for k in range(1, n):
            s.move(ener, dx=xs[k] - xs[0], d=0.8, delay=0.3 + 0.9 * (k - 1), ease="inout")
            s.move(elab, dx=xs[k] - xs[0], d=0.8, delay=0.3 + 0.9 * (k - 1), ease="inout")
    return a.block("Chaîne alimentaire : " + " → ".join(names) + ". Les flèches vont de l'être mangé vers son prédateur ; l'énergie circule dans ce sens.", caption)


# 10 ----------------------------------------------------------------------------------------------- water cycle
def water_cycle(caption="Le cycle de l'eau"):
    a = Anim(W, H)
    a.add(rect(0, 232, 480, 68, "lightblue", None))
    a.add(poly([(320, 232), (400, 132), (480, 232)], "lightgrey", "grey"))
    a.add(circle(55, 55, 28, "yellow", "orange"))
    evap = a.add(line(120, 226, 150, 118, "orange", 4, arrow="end"), draw=0)
    elab = a.add(text(20, 170, "Évaporation", 14, "orange", "start", True), alpha=0)
    cloud = [a.add(circle(x, y, rad, "white", "grey"), g="cloud", alpha=0) for x, y, rad in ((205, 62, 24), (235, 50, 28), (268, 62, 24))]
    clab = a.add(text(235, 22, "Condensation", 14, "grey", bold=True), alpha=0)
    drops = [a.add(line(x, 100, x, 112, "blue", 3), alpha=0) for x in (215, 235, 255)]
    plab = a.add(text(268, 160, "Précipitations", 14, "blue", "start", True), alpha=0)
    run = a.add(line(392, 150, 330, 222, "blue", 4, arrow="end"), draw=0)
    rlab = a.add(text(470, 118, "Ruissellement", 14, "blue", "end", True), alpha=0)
    with a.step("Le soleil chauffe la mer : l'eau s'évapore et monte.") as s:
        s.draw(evap, 1.0); s.show(elab, 0.4, delay=0.6)
    with a.step("En haut, la vapeur refroidit et forme des nuages.") as s:
        s.show("cloud", 0.8); s.show(clab, 0.4, delay=0.6)
    with a.step("L'eau retombe en pluie.") as s:
        for k, d in enumerate(drops):
            s.show(d, 0.2, delay=0.2 * k); s.move(d, dy=110, d=1.0, delay=0.2 * k, ease="in"); s.hide(d, 0.2, delay=0.2 * k + 1.0)
        s.show(plab, 0.4, delay=0.3)
    with a.step("Elle ruisselle jusqu'à la mer et le cycle recommence.") as s:
        s.draw(run, 0.9); s.show(rlab, 0.4, delay=0.5)
    return a.block("Cycle de l'eau : le soleil évapore l'eau de la mer, la vapeur se condense en nuage, la pluie tombe puis l'eau ruisselle vers la mer.", caption)


# 11 ----------------------------------------------------------------------------------------------- photosynthesis
def photosynthesis(caption="La photosynthèse"):
    a = Anim(W, H)
    a.add(path("M240 200C170 180 150 100 240 40C330 100 310 180 240 200Z", "lightgreen", "green", 3))
    a.add(line(240, 200, 240, 150, "green", 3)); a.add(line(240, 150, 215, 120, "green", 2)); a.add(line(240, 140, 268, 110, "green", 2))
    sun = a.add(circle(55, 55, 26, "yellow", "orange"))
    light = a.add(line(85, 85, 180, 130, "orange", 4, arrow="end"), draw=0); ll = a.add(text(60, 130, "Lumière", 14, "orange", "start", True), alpha=0)
    co2 = a.add(line(400, 180, 300, 150, "grey", 4, arrow="end"), draw=0); cl = a.add(text(405, 175, "CO2", 15, "grey", "start", True), alpha=0)
    wat = a.add(line(240, 285, 240, 215, "blue", 4, arrow="end"), draw=0); wl = a.add(text(262, 262, "Eau", 15, "blue", "start", True), alpha=0)
    o2 = a.add(line(300, 90, 400, 60, "cyan", 4, arrow="end"), draw=0); ol = a.add(text(405, 62, "O2", 15, "cyan", "start", True), alpha=0)
    eq = a.add(text(20, 24, "CO2 + eau + lumière → sucre + O2", 15, "ink", "start", True), typed=0)
    with a.step("La feuille reçoit la lumière du soleil.") as s:
        s.draw(light, 0.9); s.show(ll, 0.4, delay=0.5)
    with a.step("Elle absorbe le dioxyde de carbone de l'air (CO2).") as s:
        s.draw(co2, 0.9); s.show(cl, 0.4, delay=0.5)
    with a.step("Les racines apportent l'eau.") as s:
        s.draw(wat, 0.9); s.show(wl, 0.4, delay=0.5)
    with a.step("La plante fabrique du sucre et rejette de l'oxygène (O2).") as s:
        s.draw(o2, 0.9); s.show(ol, 0.4, delay=0.5); s.type(eq, 1.6, 0.3)
    return a.block("Une feuille reçoit la lumière, le CO2 et l'eau ; elle fabrique du sucre et libère de l'oxygène.", caption)


# 12 ----------------------------------------------------------------------------------------------- circuit with moving charges
def circuit_current(n=8, period=8.0, caption="Le courant dans un circuit"):
    x0, y0, x1, y1 = 120, 80, 360, 220
    corners = [(x0, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0)]
    lens = [math.hypot(corners[k + 1][0] - corners[k][0], corners[k + 1][1] - corners[k][1]) for k in range(4)]
    P = sum(lens); speed = P / period
    a = Anim(W, H, mode="auto", loop=True)
    a.add(rect(x0, y0, x1 - x0, y1 - y0, None, "ink", 3))
    a.add(rect(x0 - 24, 130, 48, 40, "white", None))                                   # gap for the battery
    a.add(line(x0 - 16, 138, x0 + 16, 138, "ink", 3)); a.add(line(x0 - 8, 154, x0 + 8, 154, "ink", 6))
    a.add(text(x0 - 30, 142, "+", 18, "red", "end", True)); a.add(text(x0 - 30, 164, "−", 18, "blue", "end", True)); a.add(text(60, 112, "Pile", 14, "ink", "end"))
    a.add(circle(x1, 150, 20, "yellow", "orange", 3)); a.add(line(x1 - 12, 138, x1 + 12, 162, "orange", 2)); a.add(line(x1 - 12, 162, x1 + 12, 138, "orange", 2))
    a.add(text(x1 + 34, 154, "Lampe", 14, "ink", "start"))
    a.add(text(240, 66, "Sens du courant →", 14, "grey", bold=True))

    def at(dist):
        dist %= P
        for k in range(4):
            if dist <= lens[k] + 1e-9:
                f = dist / lens[k]; return corners[k][0] + (corners[k + 1][0] - corners[k][0]) * f, corners[k][1] + (corners[k + 1][1] - corners[k][1]) * f
            dist -= lens[k]
        return corners[0]
    bounds = [0]
    for l in lens: bounds.append(bounds[-1] + l)
    for k in range(n):
        s0 = (k + 0.37) * P / n
        px, py = at(s0)
        c = a.add(circle(px, py, 6, "blue", "ink", 1.5))
        # waypoints (corners, then wrap to the start, then corners again up to s0)
        wps = []  # (time, x, y, jump)
        d = s0
        legs = [b for b in bounds[1:] if b > s0 + 1e-9] + [P]
        t = 0.0; prev = s0
        for b in legs:
            t += (b - prev) / speed; prev = b; wps.append((t, *at(b), False))
        wps[-1] = (wps[-1][0], corners[0][0], corners[0][1], False)
        wps.append((wps[-1][0], corners[0][0], corners[0][1], True))   # instant jump to the start of the loop
        prev = 0.0
        for b in [b for b in bounds[1:] if b < s0 - 1e-9] + [s0]:
            t += (b - prev) / speed; prev = b; wps.append((t, *at(b), False))
        # drop legs shorter than 0.2 s: merge into the next
        out = []; t_prev = 0.0
        for (tt, x, y, jump) in wps:
            if jump:
                out.append((tt, x, y, 0)); t_prev = tt; continue
            if tt - t_prev < 0.2 - 1e-9 and (tt, x, y, jump) is not wps[-1]:
                continue
            out.append((tt, x, y, tt - t_prev)); t_prev = tt
        t_start = 0.0
        for (tt, x, y, dur) in out:
            if dur == 0:
                a.act("move", c, tt, 0, dx=x - px, dy=y - py)
            else:
                a.act("move", c, t_start, dur, "linear", dx=x - px, dy=y - py)
            t_start = tt
    a.stops.append({"at": period, "say": "Les charges électriques circulent en boucle : pile, fils, lampe, pile."})
    return a.block("Un circuit fermé avec une pile et une lampe ; de petites charges bleues circulent en boucle dans les fils, de la pile vers la lampe.", caption)


# 13 ----------------------------------------------------------------------------------------------- states of matter
def states_of_matter(seed=7, period=4.0, caption="Solide, liquide, gaz"):
    rnd = random.Random(seed)
    a = Anim(W, H, mode="auto", loop=True)
    panels = [(20, "Solide", "Elles vibrent"), (170, "Liquide", "Elles glissent"), (320, "Gaz", "Elles volent libres")]
    cols = ["blue", "cyan", "red"]
    for k, (x, nm, cap) in enumerate(panels):
        a.add(rect(x, 50, 140, 170, "paper", "grey", 2, 6)); a.add(text(x + 70, 38, nm, 18, "ink", bold=True)); a.add(text(x + 70, 248, cap, 12, "grey"))
    parts = []
    for i in range(4):
        for j in range(3): parts.append((0, 32 + j * 36, 80 + i * 32, 2.5))
    for i in range(10): parts.append((1, 28 + (i % 4) * 28 + rnd.uniform(-4, 4), 160 + (i // 4) * 24 + rnd.uniform(-3, 3), 9))
    for i in range(6): parts.append((2, 30 + rnd.uniform(0, 80), 75 + rnd.uniform(0, 120), 40))
    for (kind, px, py, amp) in parts:
        x = panels[kind][0] + px
        c = a.add(circle(x, py, 6, cols[kind], "ink", 1.2))
        n = 4
        for q in range(n):
            if q == n - 1:
                dx = dy = 0.0
            else:
                dx, dy = rnd.uniform(-amp, amp), rnd.uniform(-amp, amp)
                if kind == 2:   # stay inside the box
                    dx = max(min(dx, 140 - 8 - px), 8 - px); dy = max(min(dy, 220 - 8 - py), 56 - py)
            a.act("move", c, q * period / n, period / n, "linear" if kind == 2 else None, dx=dx, dy=dy)
    a.stops.append({"at": period, "say": "Solide : particules serrées qui vibrent. Liquide : proches mais qui glissent. Gaz : éloignées et libres."})
    return a.block("Trois boîtes : dans le solide les particules sont serrées en réseau et vibrent ; dans le liquide elles glissent les unes sur les autres ; dans le gaz elles sont éloignées et se déplacent librement.", caption)


# 14 ----------------------------------------------------------------------------------------------- timeline of events
def timeline_events(events=((1884, "Conférence de Berlin"), (1914, "Début de la Grande Guerre"), (1960, "Indépendances africaines"), (1990, "Retour du multipartisme")), caption="Frise chronologique"):
    lo = events[0][0] - 10; hi = events[-1][0] + 10
    X = lambda y: 40 + (y - lo) * 400.0 / (hi - lo)
    a = Anim(W, H)
    axis = a.add(line(30, 150, 450, 150, "ink", 3, arrow="end"), draw=0)
    els = []
    for k, (yr, lb) in enumerate(events):
        up = k % 2 == 0
        els.append((a.add(circle(X(yr), 150, 7, "red", "ink"), g=f"e{k}", alpha=0), a.add(text(X(yr), 128 if up else 182, str(yr), 15, "red", bold=True), g=f"e{k}", alpha=0),
                    a.add(text(X(yr), 110 if up else 200, lb, 11, "ink"), g=f"e{k}", alpha=0)))
    with a.step("Une frise place les événements dans l'ordre du temps.") as s:
        s.draw(axis, 1.0)
    for k, (yr, lb) in enumerate(events):
        with a.step(f"{yr} : {lb}.") as s:
            s.show(f"e{k}", 0.5)
    return a.block("Frise chronologique : " + "; ".join(f"{y} {l}" for y, l in events) + ".", caption)


# 15 / 16 ------------------------------------------------------------------------------------------ maps (author supplies polygons)
DEMO_REGIONS = [("Nord", [(150, 40), (330, 50), (350, 120), (240, 130), (140, 110)], (245, 90)),
                ("Ouest", [(60, 130), (140, 110), (240, 130), (230, 210), (90, 240), (50, 190)], (140, 175)),
                ("Est", [(240, 130), (350, 120), (430, 170), (400, 240), (230, 210)], (330, 190)),
                ("Sud", [(90, 240), (230, 210), (400, 240), (360, 280), (130, 285)], (240, 258))]

def map_highlights(regions=DEMO_REGIONS, caption="Les régions d'un pays (carte schématique)"):
    a = Anim(W, H)
    polys = [a.add(poly(p, "lightgrey", "grey", 2)) for _, p, _ in regions]
    labs = [a.add(text(c[0], c[1], nm, 16, "ink", bold=True), alpha=0) for nm, _, c in regions]
    with a.step("La carte : le pays est divisé en régions.") as s:
        s.wait(0.4)
    for k, (nm, _, _) in enumerate(regions):
        with a.step(f"Région {nm}.") as s:
            s.color(polys[k], fill="orange", d=0.5); s.show(labs[k], 0.4, delay=0.2)
            if k: s.color(polys[k - 1], fill="lightorange", d=0.5)
    return a.block("Carte schématique : les régions " + ", ".join(n for n, _, _ in regions) + " sont mises en évidence l'une après l'autre.", caption)

def map_expansion(regions=DEMO_REGIONS, years=(1900, 1920, 1945, 1960), caption="Un territoire qui s'étend au fil du temps"):
    a = Anim(W, H)
    polys = [a.add(poly(p, "white", "grey", 2)) for _, p, _ in regions]
    labs = [a.add(text(c[0], c[1], nm, 16, "ink", bold=True), alpha=0) for nm, _, c in regions]
    yr = a.add(text(445, 30, "{v}", 28, "red", "end", True), v=years[0])
    with a.step(f"En {years[0]}, le territoire est réduit à une région : {regions[0][0]}.") as s:
        s.color(polys[0], fill="orange", d=0.5); s.show(labs[0], 0.4)
    for k in range(1, len(regions)):
        with a.step(f"En {years[k]} : la région {regions[k][0]} s'ajoute.") as s:
            s.color(polys[k], fill="orange", d=0.6); s.show(labs[k], 0.4, delay=0.2); s.count(yr, years[k], 0.8)
    return a.block("Carte schématique : le territoire grandit région par région, de " + str(years[0]) + " à " + str(years[-1]) + ".", caption)


# 17 ----------------------------------------------------------------------------------------------- flowchart stepping
def flowchart_stepping(caption="Un algorithme pas à pas : pair ou impair ?"):
    a = Anim(W, H)
    a.add(text(430, 40, "n = 7", 18, "blue", "end", True))
    nodes = {}
    def box(k, x, y, w, h, label, fill, r_=None):
        nodes[k] = (x, y, w, h)
        rid = a.add(rect(x - w / 2, y - h / 2, w, h, fill, "ink", 2.5, r_), id="n" + k)
        a.add(text(x, y + 5, label, 15, "ink", bold=True))
        return rid
    box("s", 240, 30, 110, 34, "Début", "lightgreen", 17)
    box("r", 240, 90, 150, 34, "Lire n", "lightblue")
    nodes["d"] = (240, 160, 160, 60)
    a.add(poly([(240, 130), (320, 160), (240, 190), (160, 160)], "lightyellow", "ink", 2.5), id="nd"); a.add(text(240, 165, "n est pair ?", 14, bold=True))
    box("p", 110, 250, 150, 34, "n est pair", "lightorange"); box("i", 370, 250, 150, 34, "n est impair", "lightorange")
    ar = [a.add(line(240, 47, 240, 73, "grey", 2.5, arrow="end"), draw=0), a.add(line(240, 107, 240, 128, "grey", 2.5, arrow="end"), draw=0),
          a.add(line(165, 172, 120, 232, "grey", 2.5, arrow="end"), draw=0), a.add(line(315, 172, 360, 232, "grey", 2.5, arrow="end"), draw=0)]
    lb = [a.add(text(122, 190, "oui", 14, "green", bold=True), alpha=0), a.add(text(358, 190, "non", 14, "red", bold=True), alpha=0)]
    order = [("ns", None, "On commence."), ("nr", ar[0], "On lit le nombre n = 7."), ("nd", ar[1], "Test : 7 est-il pair ? Il n'est pas divisible par 2."), ("ni", ar[3], "Non : on suit la branche de droite.")]
    prev = None
    for k, (nid, arrow, say) in enumerate(order):
        with a.step(say) as s:
            if arrow: s.draw(arrow, 0.6)
            if prev: s.color(prev, stroke="ink", d=0.3)
            s.color(nid, stroke="red", d=0.4, delay=0.3 if arrow else 0)
            if nid == "nd": s.show(lb[0], 0.3, delay=0.5); s.show(lb[1], 0.3, delay=0.5)
        prev = nid
    return a.block("Organigramme : Début, Lire n, test « n est pair ? » ; pour n = 7 la réponse est non et l'algorithme conclut « n est impair ».", caption)


# 18 ----------------------------------------------------------------------------------------------- sentence diagram
def sentence_diagram(parts=(("Le chat", "Sujet", "blue"), ("mange", "Verbe", "red"), ("la souris", "COD", "green")), caption="Analyser une phrase"):
    size = 26.0
    while True:
        widths = [len(t) * size * 0.62 for t, _, _ in parts]
        total = sum(widths) + 26 * (len(parts) - 1)
        if total <= 440: break
        size -= 1
    a = Anim(W, H)
    x = (W - total) / 2
    for k, (t, role, col) in enumerate(parts):
        w = widths[k]
        te = a.add(text(x, 130, t, size, "ink", "start", True), typed=0)
        ul = a.add(rect(x - 2, 142, w + 4, 8, col, None, radius=4), wipe=0)
        lb = a.add(text(x + w / 2, 185, role, 18, col, bold=True), alpha=0)
        with a.step(f"{t} : {role}.") as s:
            s.type(te, 0.8); s.wipe(ul, 0.6, delay=0.8); s.show(lb, 0.4, delay=1.2)
        x += w + 26
    return a.block("Phrase décomposée : " + ", ".join(f"« {t} » ({role})" for t, role, _ in parts) + ".", caption)


# 19 ----------------------------------------------------------------------------------------------- conjugation table
def conjugation_table(verb="chanter", tense="présent", forms=(("je", "chante", "-e"), ("tu", "chantes", "-es"), ("il / elle", "chante", "-e"), ("nous", "chantons", "-ons"), ("vous", "chantez", "-ez"), ("ils / elles", "chantent", "-ent")), caption="Conjuguer un verbe"):
    a = Anim(W, H)
    a.add(text(240, 36, f"{verb} — {tense}", 22, "blue", bold=True))
    a.add(line(100, 50, 380, 50, "grey", 2))
    prs, fs, es = [], [], []
    for k, (pr, f, e) in enumerate(forms):
        y = 88 + k * 34
        prs.append(a.add(text(185, y, pr, 20, "grey", "end"), alpha=0)); fs.append(a.add(text(200, y, f, 20, "ink", "start", True), alpha=0))
        es.append(a.add(text(340, y, e, 20, "red", "start", True), alpha=0))
    hd = a.add(text(340, 70, "terminaison", 12, "red", "start"), alpha=0)
    with a.step("Les pronoms personnels sujets.") as s:
        for k, p in enumerate(prs): s.show(p, 0.3, delay=0.15 * k)
    with a.step(f"Le verbe {verb} se conjugue : le radical reste, la fin change.") as s:
        for k, f in enumerate(fs): s.show(f, 0.3, delay=0.15 * k)
    with a.step("On repère les terminaisons.") as s:
        s.show(hd, 0.3)
        for k, e in enumerate(es): s.show(e, 0.3, delay=0.15 * k)
    return a.block(f"Tableau de conjugaison de {verb} au {tense} : " + "; ".join(f"{p} {f}" for p, f, _ in forms) + ".", caption)


# 20 ----------------------------------------------------------------------------------------------- bar chart growth
def bar_chart_growth(data=(("2020", 12, "blue"), ("2021", 18, "green"), ("2022", 25, "orange"), ("2023", 31, "red")), unit="élèves (en milliers)", caption="Un diagramme en barres qui grandit"):
    n = len(data); vmax = max(v for _, v, _ in data) * 1.15
    bw = 56; gap = (400 - n * bw) / (n + 1); y0, top = 250, 50
    a = Anim(W, H)
    a.add(line(50, y0, 450, y0, "ink", 2)); a.add(line(50, y0, 50, top - 10, "ink", 2, arrow="end")); a.add(text(60, 34, unit, 13, "grey", "start"))
    bars = []
    for k, (lb, v, col) in enumerate(data):
        x = 50 + gap + k * (bw + gap); hgt = (y0 - top) * v / vmax
        b = a.add(rect(x, y0 - hgt, bw, hgt, col, "ink", 2), wipe=0, dir="up")
        val = a.add(text(x + bw / 2, y0 - hgt - 8, "{v}", 18, "ink", bold=True), alpha=0, v=0)
        a.add(text(x + bw / 2, y0 + 20, lb, 15, "ink"))
        bars.append((b, val, v, lb))
    with a.step("Les axes : chaque barre représente une valeur.") as s:
        s.wait(0.4)
    for b, val, v, lb in bars:
        with a.step(f"{lb} : {v}.") as s:
            s.wipe(b, 1.0, ease="cubic"); s.show(val, 0.2, delay=0.8); s.count(val, v, 0.6, 0.8)
    return a.block("Diagramme en barres : " + ", ".join(f"{lb} {v}" for lb, v, _ in data) + ", chaque barre grandit à son tour.", caption)


# 21 ----------------------------------------------------------------------------------------------- sorting algorithm
def sorting_algorithm(values=(5, 2, 4, 1, 3), caption="Tri à bulles"):
    n = len(values); cw, gap = 60, 14; x0 = (W - (n * cw + (n - 1) * gap)) / 2; y = 120; pitch = cw + gap
    a = Anim(W, H)
    slot = list(range(n))          # slot[k] = position of cell k
    for k, v in enumerate(values):
        a.add(rect(x0 + k * pitch, y, cw, cw, "lightblue", "blue", 3, 8), id=f"r{k}", g=f"c{k}")
        a.add(text(x0 + k * pitch + cw / 2, y + 40, str(v), 28, "ink", bold=True), g=f"c{k}")
    arr = list(range(n))           # arr[pos] = cell index
    vals = list(values)
    with a.step("On veut ranger les nombres du plus petit au plus grand.") as s:
        s.wait(0.4)
    for p in range(n - 1):
        for i in range(n - 1 - p):
            c1, c2 = arr[i], arr[i + 1]; v1, v2 = vals[c1], vals[c2]
            swap = v1 > v2
            with a.step(f"On compare {v1} et {v2} : " + ("le premier est plus grand, on les échange." if swap else "ils sont dans l'ordre, on ne change rien.")) as s:
                s.color(f"r{c1}", fill="orange", d=0.3); s.color(f"r{c2}", fill="orange", d=0.3)
                if swap:
                    arr[i], arr[i + 1] = c2, c1; slot[c1], slot[c2] = i + 1, i
                    s.move(f"c{c1}", dx=(slot[c1] - c1) * pitch, d=0.7, delay=0.4); s.move(f"c{c2}", dx=(slot[c2] - c2) * pitch, d=0.7, delay=0.4)
                s.color(f"r{c1}", fill="lightblue", d=0.3, delay=1.2 if swap else 0.5); s.color(f"r{c2}", fill="lightblue", d=0.3, delay=1.2 if swap else 0.5)
        # nothing else
    with a.step("Terminé : les nombres sont rangés.") as s:
        for k in range(n): s.color(f"r{k}", fill="lightgreen", d=0.5)
    return a.block(f"Tri à bulles de la liste {', '.join(map(str, values))} : on compare les voisins et on échange ceux qui sont mal rangés jusqu'à obtenir {', '.join(map(str, sorted(values)))}.", caption)


# 22 ----------------------------------------------------------------------------------------------- balancing an equation
def balance_equation(caption="Équilibrer : H2 + O2 → H2O"):
    a = Anim(W, H)
    c1 = a.add(text(50, 90, "{v}", 30, "blue", bold=True), v=1); a.add(text(100, 90, "H2", 30, bold=True)); a.add(text(150, 90, "+", 30, "grey"))
    c2 = a.add(text(190, 90, "{v}", 30, "blue", bold=True), v=1); a.add(text(240, 90, "O2", 30, bold=True)); a.add(text(300, 90, "→", 30, "grey"))
    c3 = a.add(text(350, 90, "{v}", 30, "blue", bold=True), v=1); a.add(text(410, 90, "H2O", 30, bold=True))
    a.add(text(200, 140, "gauche", 14, "grey")); a.add(text(300, 140, "droite", 14, "grey"))
    a.add(text(140, 185, "H", 22, "ink", "end", True)); a.add(text(140, 235, "O", 22, "ink", "end", True))
    hl = a.add(text(200, 185, "{v}", 22, "ink", bold=True), v=2); hr = a.add(text(300, 185, "{v}", 22, "ink", bold=True), v=2)
    ol = a.add(text(200, 235, "{v}", 22, "ink", bold=True), v=2); orr = a.add(text(300, 235, "{v}", 22, "ink", bold=True), v=1)
    heq = a.add(text(250, 185, "=", 24, "green", bold=True)); hne = a.add(text(250, 185, "≠", 24, "red", bold=True), alpha=0)
    oeq = a.add(text(250, 235, "=", 24, "green", bold=True), alpha=0); one = a.add(text(250, 235, "≠", 24, "red", bold=True))
    with a.step("Au départ : 2 atomes d'oxygène à gauche mais 1 seul à droite (O ≠).") as s:
        s.wait(0.4)
    with a.step("On met le coefficient 2 devant H2O : il y a maintenant 2 O à droite, mais 4 H.") as s:
        s.count(c3, 2, 0.6); s.count(hr, 4, 0.6); s.count(orr, 2, 0.6)
        s.hide(heq, 0.3, delay=0.6); s.show(hne, 0.3, delay=0.6); s.hide(one, 0.3, delay=0.6); s.show(oeq, 0.3, delay=0.6)
    with a.step("On met le coefficient 2 devant H2 : 4 H à gauche. Tout est équilibré !") as s:
        s.count(c1, 2, 0.6); s.count(hl, 4, 0.6)
        s.hide(hne, 0.3, delay=0.6); s.show(heq, 0.3, delay=0.6)
    return a.block("Équilibrage de la réaction H2 + O2 → H2O : on obtient 2 H2 + O2 → 2 H2O, avec 4 atomes d'hydrogène et 2 d'oxygène de chaque côté.", caption)


# 23 ----------------------------------------------------------------------------------------------- Pythagoras squares
def pythagoras_squares(caption="Le théorème de Pythagore"):
    C, A, B = (160, 170), (160, 110), (240, 170)     # right angle at C, legs 3 and 4 (unit 20)
    a = Anim(W, H)
    legs = [a.add(line(*C, *A, "ink", 3), draw=0), a.add(line(*C, *B, "ink", 3), draw=0), a.add(line(*A, *B, "ink", 3), draw=0)]
    rm = a.add(poly([(160, 158), (172, 158), (172, 170)], None, "ink", 1.5, False), alpha=0)
    sa = a.add(rect(100, 110, 60, 60, "lightgreen", "green", 2), g="sa", alpha=0); ta = a.add(text(130, 146, "{v}", 18, "green", bold=True), g="sa", alpha=0, v=0)
    sb = a.add(rect(160, 170, 80, 80, "lightblue", "blue", 2), g="sb", alpha=0); tb = a.add(text(200, 216, "{v}", 18, "blue", bold=True), g="sb", alpha=0, v=0)
    sc = a.add(poly([A, B, (300, 90), (220, 30)], "lightorange", "orange", 2), g="sc", alpha=0); tc = a.add(text(230, 106, "{v}", 18, "orange", bold=True), g="sc", alpha=0, v=0)
    eq = a.add(text(300, 205, "a² + b² = c²", 22, "ink", "start", True), typed=0); eq2 = a.add(text(300, 240, "9 + 16 = 25", 22, "red", "start", True), typed=0)
    with a.step("Un triangle rectangle de côtés 3, 4 et 5 (unités).") as s:
        for k, l in enumerate(legs): s.draw(l, 0.6, delay=0.5 * k)
        s.show(rm, 0.3, delay=1.6)
    for sq, t, v, nm in ((("sa"), ta, 9, "a = 3 : l'aire du carré vaut 3² = 9."), ("sb", tb, 16, "b = 4 : l'aire du carré vaut 4² = 16."), ("sc", tc, 25, "c = 5 : l'aire du carré vaut 5² = 25.")):
        with a.step(nm) as s:
            s.show(sq, 0.6); s.count(t, v, 0.8, 0.3)
    with a.step("Les deux petits carrés ont ensemble la même aire que le grand : 9 + 16 = 25.") as s:
        s.type(eq, 1.0); s.type(eq2, 1.0, 1.0)
    return a.block("Triangle rectangle de côtés 3, 4, 5 avec un carré sur chaque côté : les aires 9 et 16 s'additionnent pour faire 25, ce qui illustre a² + b² = c².", caption)


EXAMPLES = {f.__name__: f for f in (
    number_line_jumps, fraction_area, area_model_mult, angle_construction, triangle_construction, function_graph_trace, vector_addition,
    unit_conversion, food_chain, water_cycle, photosynthesis, circuit_current, states_of_matter, timeline_events, map_highlights,
    map_expansion, flowchart_stepping, sentence_diagram, conjugation_table, bar_chart_growth, sorting_algorithm, balance_equation,
    pythagoras_squares)}
