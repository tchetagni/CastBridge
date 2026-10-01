import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _fm import *
import uf_a, uf_b
p = Pack("gceal-further-maths", "Further Mathematics — GCE A Level", level="Upper Sixth", subject="maths", cursus="secondary", exam="GCE-AL", series=[], wanted="further-maths",
         description="Further pure mathematics and applications: matrices, complex numbers, polar coordinates, conics, differential equations, mechanics and statistics, with a mock paper.",
         programRef="Cameroon GCE Board — Advanced Level Further Mathematics syllabus (Upper Sixth part) — to be checked against the official text")
for m in (uf_a, uf_b):
    m.build(p)
# ---------------------------------------------------------------- mock paper (20 marks)
import cmath
from math import pi, exp, sqrt
chap = {c.slug: c for c in p.chapters}
Lm, Lc, Lp, Lk, La = (chap["matrices"].lessons[0], chap["complex-geometry"].lessons[0], chap["polar-conics"].lessons[0], chap["calculus-ext"].lessons[0], chap["applications"].lessons[0])
Lr = chap["applications"].lessons[1]
tr, dt = 7, 4 * 3 - 2 * 1; assert tr == 4 + 3 and dt == 10 and sorted([2, 5]) == [r for r in range(1, 8) if r * r - tr * r + dt == 0]
assert abs(((1 + 1j) ** 10).imag - 32) < 1e-9 and abs(sqrt(1 - 5 / 9) - 2 / 3) < 1e-12 and abs(2 * pi / 4 - pi / 2) < 1e-12
yy = lambda x: (1 + 3 * x) * exp(-3 * x); assert abs(yy(1) - 4 * exp(-3)) < 1e-12 and abs(4 * exp(-3) - .1991) < 1e-4
assert abs(1.5 * pi - 4.7124) < 1e-4 and (1 - 1 / 8) == .875 and abs(4 ** (1 / 3) - 1.5874) < 1e-4
q = [mockq(Lm, M("The eigenvalues of [[4, 2], [1, 3]] are", "2 and 5", ["1 and 6", "$-2$ and $-5$", "3 and 4"], "Trace 7 and determinant $12-2=10$: $\\lambda^2-7\\lambda+10=0$, $\\lambda=2,5$.", points=2)),
     mockq(Lc, N("Find the imaginary part of $(1+i)^{10}$.", 32, "$(\\sqrt2)^{10}\\left(\\cos\\frac{10\\pi}{4}+i\\sin\\frac{10\\pi}{4}\\right)=32\\left(\\cos\\frac{5\\pi}{2}+i\\sin\\frac{5\\pi}{2}\\right)=32i$.", points=2)),
     mockq(Lp, M("The eccentricity of the ellipse $\\frac{x^2}{9}+\\frac{y^2}{5}=1$ is", "$\\frac23$", ["$\\frac49$", "$\\frac{\\sqrt5}{3}$", "$\\frac{2}{\\sqrt5}$"], "$e=\\sqrt{1-\\frac59}=\\sqrt{\\frac49}=\\frac23$.", points=2)),
     mockq(La, N("A particle satisfies $\\ddot x=-16x$. Find its period in seconds (4 d.p.).", round(pi / 2, 4), "$\\omega=4$, $T=\\frac{2\\pi}{4}=1.5708$.", tol=0.0002, points=2))]
q.append(mockq(Lk, P("Solve $y''+6y'+9y=0$ with $y(0)=1$ and $y'(0)=0$.", [pm("The general solution has the form", "$(A+Bx)e^{-3x}$", ["$Ae^{3x}+Be^{-3x}$", "$e^{-3x}(A\\cos3x+B\\sin3x)$", "$Ae^{-3x}+B$"], "Repeated root $m=-3$ from $(m+3)^2=0$.", 2), pn("Find $y(1)$ (4 d.p.).", round(4 * exp(-3), 4), "$A=1$; $y'(0)=B-3A=0$ gives $B=3$; $y=(1+3x)e^{-3x}$ and $y(1)=4e^{-3}=0.1991$.", tol=0.0003, pts=2)])))
q.append(mockq(Lp, P("A curve has polar equation $r=1+\\cos\\theta$ for $0\\leq\\theta\\leq2\\pi$.", [pn("Find $r$ when $\\theta=\\frac\\pi2$.", 1, "$1+0=1$.", pts=1), pn("Find the area enclosed by the curve (4 d.p.).", round(1.5 * pi, 4), "$\\frac12\\int_0^{2\\pi}(1+\\cos\\theta)^2d\\theta=\\frac12(2\\pi+0+\\pi)=\\frac{3\\pi}{2}=4.7124$.", tol=0.0003, pts=3)])))
q.append(mockq(Lr, P("A random variable $X$ has pdf $f(x)=\\frac{3x^2}{8}$ for $0\\leq x\\leq2$, and 0 otherwise.", [pn("Find $P(X>1)$.", .875, "$1-\\int_0^1\\frac{3x^2}{8}dx=1-\\frac18$.", tol=0.001, pts=1), pn("Find $E(X)$.", 1.5, "$\\int_0^2\\frac{3x^3}{8}dx=\\frac{3}{8}\\cdot4$.", tol=0.001, pts=2), pn("Find the median (3 d.p.).", 1.587, "$\\frac{m^3}{8}=\\frac12$, $m=\\sqrt[3]{4}=1.587$.", tol=0.001, pts=1)])))
p.mock("paper-1", "GCE A Level mock — Further Mathematics", 90,
       "Answer all questions. Show your working in full: marks are given for method as well as for accuracy. Calculators allowed.",
       [("Section A — short questions (8 marks)", q[0:4]), ("Section B — structured questions (12 marks)", q[4:7])])
p.write()
