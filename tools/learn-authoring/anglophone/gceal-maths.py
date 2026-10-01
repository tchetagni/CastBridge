import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _maths import *
import gal_a, gal_b, gal_c
p = Pack("gceal-maths", "Mathematics — GCE A Level", level="Upper Sixth", subject="maths", cursus="secondary", exam="GCE-AL", series=[],
         description="Pure mathematics year 2, mechanics and statistics: key points, worked examples, GCE Advanced Level style exercises with full solutions and a mock paper.",
         programRef="Cameroon GCE Board — Advanced Level Mathematics syllabus (Upper Sixth part) — to be checked against the official text")
for m in (gal_a, gal_b, gal_c):
    m.build(p)
# ---------------------------------------------------------------- mock paper (20 marks)
from math import exp, log, sqrt, erf, pi, acos, degrees
Phi = lambda z: .5 * (1 + erf(z / sqrt(2)))
chap = {c.slug: c for c in p.chapters}
Ld, Li, Lde, Lv, Lm, Ls = (chap["differentiation"].lessons[0], chap["integration"].lessons[0], chap["de-numeric"].lessons[0],
                           chap["vectors3d"].lessons[0], chap["mechanics"].lessons[0], chap["statistics"].lessons[0])
assert abs(2 * 1 / (1 + 1) - 1) < 1e-12
a2 = 2 * log(2) - 1; assert abs(a2 - 0.3863) < 1e-4
ang = degrees(acos(4 / 9)); assert abs(ang - 63.6) < .05 and abs((180 - ang) - 116.4) < .05
p2 = 2 * exp(-2); assert abs(p2 - 0.2707) < 1e-4
assert abs(1 / (1 - .5 ** 3) - 1.142857) < 1e-5
assert abs(2 * 12 / 10 - 2.4) < 1e-12 and 16 * 2.4 == 38.4 and 144 / 20 == 7.2
zz = (24.6 - 25) / (1.5 / 6); assert abs(zz + 1.6) < 1e-12 and abs(Phi(-1.6) - .0548) < 1e-4 and zz > -1.645
q = [mockq(Ld, M("The gradient of $y=\\ln(x^2+1)$ at $x=1$ is", "$1$", ["$2$", "$\\frac12$", "$\\ln2$"], "$\\frac{dy}{dx}=\\frac{2x}{x^2+1}=\\frac{2}{2}=1$ (chain rule).", points=2)),
     mockq(Li, N("Evaluate $\\int_1^2\\ln x\\,dx$ (4 d.p.).", round(a2, 4), "By parts: $[x\\ln x-x]_1^2=(2\\ln2-2)-(0-1)=2\\ln2-1=0.3863$.", tol=0.0003, points=2)),
     mockq(Lv, M("The angle between the lines with directions $(1,2,2)$ and $(2,-1,2)$ is", "$63.6\\degree$", ["$116.4\\degree$", "$26.4\\degree$", "$41.8\\degree$"], "$\\cos\\theta=\\frac{2-2+4}{3\\times3}=\\frac49$, so $\\theta=63.6\\degree$.", points=2)),
     mockq(Ls, N("$X\\sim\\text{Po}(2)$. Find $P(X=1)$ (4 d.p.).", round(p2, 4), "$e^{-2}\\times2=0.2707$.", tol=0.0003, points=2))]
q.append(mockq(Lde, P("Consider $\\frac{dy}{dx}=3x^2y^2$ with $y=1$ when $x=0$.", [pm("The solution is", "$y=\\frac{1}{1-x^3}$", ["$y=e^{x^3}$", "$y=1+x^3$", "$y=\\frac{1}{1+x^3}$"], "$\\int y^{-2}dy=\\int3x^2dx$ gives $-\\frac1y=x^3+C$; $C=-1$, so $y=\\frac{1}{1-x^3}$.", 2), pn("Find $y$ when $x=0.5$ (4 d.p.).", round(1 / (1 - .125), 4), "$\\frac{1}{1-0.125}=1.1429$.", tol=0.0003, pts=2)])))
q.append(mockq(Lm, P("A ball is projected from the ground at 20 m/s at an angle $\\alpha$ with $\\sin\\alpha=0.6$ ($\\cos\\alpha=0.8$). Take $g=10$.", [pn("Find the time of flight (s).", 2.4, "$u_y=12$: $T=\\frac{2\\times12}{10}=2.4$.", tol=0.01, pts=2), pn("Find the range (m).", 38.4, "$u_x=16$: $16\\times2.4=38.4$.", tol=0.05, pts=1), pn("Find the greatest height (m).", 7.2, "$\\frac{12^2}{20}=7.2$.", tol=0.02, pts=1)])))
q.append(mockq(Ls, P("Sacks of cassava flour in Bafoussam are labelled 25 kg and have $\\sigma=1.5$ kg. A sample of 36 sacks has mean 24.6 kg. Test at the 5% level whether the mean is below 25 kg: $H_0:\\mu=25$, $H_1:\\mu<25$.", [pn("Find the test statistic $z$.", -1.6, "$z=\\frac{24.6-25}{1.5/\\sqrt{36}}=\\frac{-0.4}{0.25}=-1.6$.", tol=0.01, pts=2), pm("The conclusion at 5% is", "do not reject $H_0$: insufficient evidence that the mean is below 25 kg", ["reject $H_0$: the mean is below 25 kg", "reject $H_0$ because $z<0$", "accept that $\\mu=24.6$"], "$-1.6>-1.645$ (critical value), equivalently $P(Z<-1.6)=0.0548>0.05$.", 2)])))
p.mock("paper-1", "GCE A Level mock — Mathematics (Pure, Mechanics, Statistics)", 90,
       "Answer all questions. Show your working in full: marks are given for method as well as for accuracy. Take g = 10 m/s². Normal critical values: 1.645 (5% one-tailed), 1.96 (5% two-tailed). Calculators allowed.",
       [("Section A — short questions (8 marks)", q[0:4]), ("Section B — structured questions (12 marks)", q[4:7])])
p.write()
