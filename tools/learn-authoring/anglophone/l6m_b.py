import math
from math import comb, pi, sin, cos, radians, degrees
from _maths import *
from l6m_a import REF

def build(p):
    ch = p.chapter("sequences", "Sequences, series and the binomial theorem", REF + " — sequences and series; binomial expansion")
    # ------------------------------------------------------------ AP / GP
    assert -3 + 4 * 5 == 17 and -3 + 11 * 5 == 52 and 20 / 2 * (2 * -3 + 19 * 5) == 890
    assert abs(8 * 1.5 ** 5 - 60.75) < 1e-9 and 20 / (1 - .5) == 40
    assert 3 + 14 * 4 == 59 and sum(2 * 3 ** k for k in range(5)) == 242 and 6 * (4000 + 11 * 500) == 57000
    g6 = 800 * 1.1 ** 5; s6 = 800 * (1.1 ** 6 - 1) / .1
    assert abs(g6 - 1288.408) < 1e-3 and abs(s6 - 6172.488) < 1e-2
    vals = [800 * 1.1 ** k for k in range(6)]
    lesson(ch, "ap-gp", "Arithmetic and geometric series",
        ["Use the nth term and sum formulae of an arithmetic progression", "Use the nth term, finite sum and sum to infinity of a geometric progression", "Model growth in context"],
        [("formule", "Arithmetic progression (AP)", "First term $a$, common difference $d$: $u_n=a+(n-1)d$ and $S_n=\\frac{n}{2}\\big(2a+(n-1)d\\big)=\\frac{n}{2}(a+l)$ where $l$ is the last term."),
         ("formule", "Geometric progression (GP)", "First term $a$, common ratio $r$: $u_n=ar^{n-1}$ and $S_n=\\frac{a(1-r^n)}{1-r}$ ($r\\neq1$). If $|r|<1$ the series converges to $S_\\infty=\\frac{a}{1-r}$."),
         ("methode", "Method", "Write what you know as equations in $a$ and $d$ (or $a$ and $r$), solve them, then apply the sum formula. For a GP, find $r$ by dividing a term by the previous one. Always check $|r|<1$ before using $S_\\infty$."),
         ("pieges", "Common mistakes", "- The nth term uses $(n-1)$, not $n$.\n- $S_\\infty$ does NOT exist if $|r|\\geq1$.\n- Do not confuse $u_n$ (one term) with $S_n$ (sum of $n$ terms).")],
        [("Example 1", "An AP has 5th term 17 and 12th term 52. Find $a$, $d$ and $S_{20}$.", ["$u_{12}-u_5=7d=35$, so $d=5$.", "$a+4d=17$ gives $a=-3$.", "$S_{20}=10\\big(2(-3)+19\\times5\\big)=10\\times89$."], "$a=-3$, $d=5$, $S_{20}=890$"),
         ("Example 2", "A GP begins $8,\\,12,\\,18,\\dots$ Find $u_6$. Then find $S_\\infty$ of $20+10+5+\\cdots$", ["$r=\\frac{12}{8}=1.5$ and $u_6=8\\times1.5^5=60.75$.", "For $20+10+5+\\cdots$: $a=20$, $r=\\frac12$, so $S_\\infty=\\frac{20}{1-0.5}$."], "$u_6=60.75$; $S_\\infty=40$")],
        [N("Find the 15th term of $3,7,11,15,\\dots$", 59, "$a=3$, $d=4$: $u_{15}=3+14\\times4=59$."),
         M("The sum of $2+6+18+54+162$ is", "$242$", ["$162$", "$486$", "$240$"], "$a=2$, $r=3$: $S_5=\\frac{2(3^5-1)}{2}=242$."),
         TF("A GP with $r=-1.5$ has a sum to infinity.", False, "False: it needs $|r|<1$; here $|r|=1.5$.")],
        [M("The sum to infinity of $0.3+0.03+0.003+\\cdots$ is", "$\\frac{1}{3}$", ["$0.33$", "$\\frac{3}{10}$", "$3$"], "$a=0.3$, $r=0.1$: $\\frac{0.3}{0.9}=\\frac13$."),
         N("A trader in Kumba saves 2000 FCFA in month 1 and 500 FCFA more each month than the month before. How many FCFA has she saved in total after 12 months?", 57000, "AP: $a=2000$, $d=500$: $S_{12}=6(4000+11\\times500)=6\\times9500=57000$.")],
        P("A cocoa farmer near Mbalmayo harvests 800 kg in year 1, and each year's harvest is 10% bigger than the year before (a GP with $r=1.1$).", [pn("Find the harvest in year 6 (kg, nearest kg).", round(g6), "$800\\times1.1^5=1288.4$.", tol=1, pts=2), pn("Find the total harvest over the first 6 years (kg, nearest kg).", round(s6), "$S_6=\\frac{800(1.1^6-1)}{0.1}=6172.5$.", tol=1, pts=3)],
          figure=bars([("Y%d" % (i + 1), round(v)) for i, v in enumerate(vals)], unit="kg")),
        [("The common difference of $9,5,1,-3$ is", "$-4$", ["$4$", "$-3$", "$5$"], "Each term is 4 less."),
         ("The sum $1+2+3+\\cdots+100$ is", "$5050$", ["$5000$", "$10100$", "$5100$"], "$\\frac{100}{2}(1+100)$."),
         ("In a GP $a=3$, $r=2$: the 4th term is", "$24$", ["$12$", "$48$", "$9$"], "$3\\times2^3$."),
         ("$S_\\infty$ for $a=6$, $r=\\frac13$ is", "$9$", ["$18$", "$2$", "$6$"], "$\\frac{6}{2/3}=9$."),
         ("$4,\\,x,\\,9$ are consecutive terms of a GP with $x>0$. Then $x=$", "$6$", ["$6.5$", "$36$", "$\\sqrt{13}$"], "$x^2=36$.")],
        ill=(bars([("Y%d" % (i + 1), round(v)) for i, v in enumerate(vals)], unit="kg"), "Yearly cocoa harvest growing by 10% per year: a geometric progression.", "Six bars of increasing height from 800 kg to about 1288 kg."),
        notes=["Sigma notation and recurrence relations $u_{n+1}=f(u_n)$ are not covered here."], ref=REF)

    # ------------------------------------------------------------ binomial
    assert [comb(4, k) * 2 ** k for k in range(5)] == [1, 8, 24, 32, 16] and comb(6, 3) * 2 ** 3 * (-1) == -160
    assert comb(8, 3) == 56 and comb(5, 2) * 9 == 90 and comb(6, 3) == 20
    assert abs(1 + 6 * .01 + 15 * .01 ** 2 - 1.0615) < 1e-12 and abs(1.01 ** 6 - 1.06152) < 1e-4
    assert comb(5, 1) * 2 == 10 and comb(5, 2) * 4 == 40 and comb(5, 3) * 8 == 80
    rows = []
    for n in range(6):
        y = 30 + n * 38
        for k in range(n + 1):
            rows.append(T(200 + (k - n / 2) * 52, y, str(comb(n, k)), 15))
        rows.append(T(34, y, "n=%d" % n, 12))
    pascal = shapes(rows, 400, 250)
    lesson(ch, "binomial", "The binomial theorem",
        ["Expand $(a+b)^n$ for positive integer $n$", "Find a required term or coefficient", "Use the expansion to approximate powers"],
        [("formule", "Binomial expansion", "$(a+b)^n=a^n+C(n,1)a^{n-1}b+C(n,2)a^{n-2}b^2+\\cdots+b^n$ where $C(n,r)=\\frac{n!}{r!(n-r)!}$. The general term is $C(n,r)\\,a^{n-r}b^r$. There are $n+1$ terms."),
         ("methode", "Pascal's triangle", "Each entry is the sum of the two above it, and row $n$ gives the coefficients $C(n,0),\\dots,C(n,n)$. For small $n$ it is quick: row 4 is $1,4,6,4,1$."),
         ("propriete", "Useful form", "$(1+x)^n=1+nx+\\frac{n(n-1)}{2!}x^2+\\frac{n(n-1)(n-2)}{3!}x^3+\\cdots$ For small $x$ the first few terms give a good approximation, e.g. $1.01^6\\approx1+0.06+0.0015$."),
         ("pieges", "Common mistakes", "- Apply the power to the WHOLE term: in $(1+2x)^4$ the $x^2$ term uses $2^2=4$.\n- With $(2-x)^6$ remember signs: odd powers of $-x$ are negative.\n- The coefficient is not the term: the term in $x^3$ includes $x^3$.")],
        [("Example 1", "Expand $(1+2x)^4$.", ["Coefficients from row 4: $1,4,6,4,1$ and powers of $2x$: $1,\\,2x,\\,4x^2,\\,8x^3,\\,16x^4$.", "Multiply: $1,\;4\\times2=8,\;6\\times4=24,\;4\\times8=32,\;16$."], "$1+8x+24x^2+32x^3+16x^4$", pascal),
         ("Example 2", "Find the coefficient of $x^3$ in $(2-x)^6$.", ["General term: $C(6,r)\\,2^{6-r}(-x)^r$; take $r=3$.", "$C(6,3)=20$, $2^3=8$, $(-1)^3=-1$: $20\\times8\\times(-1)$."], "$-160$")],
        [N("Evaluate $C(8,3)$.", 56, "$\\frac{8\\times7\\times6}{3\\times2\\times1}=56$."),
         M("The coefficient of $x^2$ in $(1+3x)^5$ is", "$90$", ["$30$", "$10$", "$45$"], "$C(5,2)\\times3^2=10\\times9$."),
         TF("The expansion of $(a+b)^n$ has $n$ terms.", False, "It has $n+1$ terms: for $n=2$ we get $a^2+2ab+b^2$, three terms.")],
        [M("The term independent of $x$ in $\\left(x+\\frac{1}{x}\\right)^6$ is", "$20$", ["$15$", "$6$", "$1$"], "Need equal powers of $x$ and $\\frac1x$: $r=3$, $C(6,3)=20$."),
         O("Use the first three terms of the expansion of $(1+x)^6$ with $x=0.01$ to estimate $1.01^6$.", "$(1+x)^6\\approx1+6x+15x^2=1+0.06+15(0.0001)=1+0.06+0.0015=1.0615$. (The exact value is about $1.06152$.)", ["Correct coefficients $1,6,15$ (1)", "Substitutes $x=0.01$ (1)", "Result $1.0615$ (1)"])],
        P("The expansion of $(1+ax)^5$ begins $1+10x+bx^2+cx^3+\\cdots$", [pn("Find $a$.", 2, "The $x$ coefficient is $5a=10$.", pts=1), pn("Find $b$.", 40, "$b=C(5,2)a^2=10\\times4$.", pts=2), pn("Find $c$.", 80, "$c=C(5,3)a^3=10\\times8$.", pts=2)]),
        [("Row 5 of Pascal's triangle is", "$1,5,10,10,5,1$", ["$1,5,5,5,5,1$", "$1,4,6,4,1$", "$1,5,10,5,1$"], "Add adjacent entries of row 4."),
         ("$C(5,0)$ equals", "$1$", ["$0$", "$5$", "$\\frac15$"], "$\\frac{5!}{0!5!}$."),
         ("The $x^2$ term of $(1+x)^7$ has coefficient", "$21$", ["$14$", "$7$", "$42$"], "$\\frac{7\\times6}{2}$."),
         ("The first three terms of $(1-2x)^4$ are", "$1-8x+24x^2$", ["$1-8x+6x^2$", "$1+8x+24x^2$", "$1-2x+x^2$"], "$1,\;4(-2x),\;6(4x^2)$."),
         ("$(a+b)^3$ expands to", "$a^3+3a^2b+3ab^2+b^3$", ["$a^3+b^3$", "$a^3+3ab+b^3$", "$a^3+a^2b+ab^2+b^3$"], "Coefficients $1,3,3,1$.")],
        ill=None, notes=["Binomial series for negative or fractional $n$ is not included (check whether it belongs to Lower Sixth on the official syllabus)."], ref=REF)

    # ------------------------------------------------------------ TRIGONOMETRY
    ch = p.chapter("trig", "Trigonometry", REF + " — trigonometry")
    assert abs(radians(150) - 2.618) < 1e-3 and abs(10 * pi / 5 - 2 * pi) < 1e-12 and 6 + 6 + 6 * 1.5 == 21
    seg = .5 * 100 * (1.2 - sin(1.2)); assert abs(seg - 13.398) < 1e-3
    assert abs(12 / 5 - 2.4) < 1e-12 and .5 * 25 * 2.4 == 30 and .5 * 64 * .75 == 24 and 8 * .75 == 6
    arc = 12 * 2 * pi / 3; area = .5 * 144 * 2 * pi / 3; assert abs(arc - 25.1327) < 1e-3 and abs(area - 150.796) < 1e-3
    sector = []
    cx, cy, R, th = 70, 190, 150, 1.0
    pts = [cx, cy] + [v for i in range(31) for v in (cx + R * cos(th * i / 30), cy - R * sin(th * i / 30))]
    sector = shapes([POLY(pts, fill="yellow", stroke="blue", width=2), LINE(cx, cy, cx + R, cy, color="ink", width=2),
                     T(cx + R / 2, cy + 20, "r", 14), T(cx + 38, cy - 12, "θ", 16, anchor="start"), T(cx + R * cos(th / 2) + 22, cy - R * sin(th / 2) - 6, "arc s = rθ", 13, anchor="start"),
                     T(cx + 75, cy - 70, "area ½r²θ", 13)], 400, 240)
    lesson(ch, "radians", "Radians, arcs and sectors",
        ["Convert between degrees and radians", "Calculate arc length and sector area", "Know exact trig values of $\\frac{\\pi}{6},\\frac{\\pi}{4},\\frac{\\pi}{3}$"],
        [("definition", "The radian", "One radian is the angle at the centre of a circle subtended by an arc equal in length to the radius. $\\pi\\text{ rad}=180\\degree$, so $1\\text{ rad}\\approx57.3\\degree$. To convert: degrees $\\times\\frac{\\pi}{180}$, radians $\\times\\frac{180}{\\pi}$."),
         ("formule", "Arc and sector (θ in radians)", "Arc length $s=r\\theta$. Sector area $A=\\frac12r^2\\theta$. Segment area $=\\frac12r^2(\\theta-\\sin\\theta)$. The perimeter of a sector is $2r+r\\theta$."),
         ("propriete", "Exact values", "$\\sin\\frac{\\pi}{6}=\\frac12$, $\\cos\\frac{\\pi}{6}=\\frac{\\sqrt3}{2}$; $\\sin\\frac{\\pi}{4}=\\cos\\frac{\\pi}{4}=\\frac{\\sqrt2}{2}$; $\\sin\\frac{\\pi}{3}=\\frac{\\sqrt3}{2}$, $\\cos\\frac{\\pi}{3}=\\frac12$; $\\tan\\frac{\\pi}{4}=1$."),
         ("pieges", "Common mistakes", "The formulae $s=r\\theta$ and $\\frac12r^2\\theta$ are only true when $\\theta$ is in RADIANS. Set your calculator to radian mode for such calculations. Do not forget the two radii when finding the perimeter of a sector.")],
        [("Example 1", "Convert $135\\degree$ to radians. A sector has radius 8 cm and angle 0.75 rad: find arc length and area.", ["$135\\times\\frac{\\pi}{180}=\\frac{3\\pi}{4}$.", "Arc $=8\\times0.75=6$ cm.", "Area $=\\frac12\\times64\\times0.75=24$ cm²."], "$\\frac{3\\pi}{4}$ rad; arc 6 cm; area 24 cm²", sector),
         ("Example 2", "An arc of length 12 cm lies on a circle of radius 5 cm. Find the angle and the sector area.", ["$\\theta=\\frac{s}{r}=\\frac{12}{5}=2.4$ rad.", "$A=\\frac12\\times25\\times2.4=30$ cm²."], "$\\theta=2.4$ rad; $A=30$ cm²")],
        [N("Convert $150\\degree$ to radians (3 d.p.).", round(radians(150), 3), "$150\\times\\frac{\\pi}{180}=\\frac{5\\pi}{6}\\approx2.618$.", tol=0.002),
         M("The arc length for $r=10$ and $\\theta=\\frac{\\pi}{5}$ is", "$2\\pi$", ["$\\frac{\\pi}{50}$", "$50\\pi$", "$10\\pi$"], "$s=r\\theta=10\\times\\frac{\\pi}{5}$."),
         TF("$\\sin\\frac{\\pi}{6}=\\frac12$.", True, "$\\frac{\\pi}{6}=30\\degree$ and $\\sin30\\degree=\\frac12$.")],
        [M("A sector has $r=6$ cm and $\\theta=1.5$ rad. Its perimeter is", "$21$ cm", ["$9$ cm", "$12$ cm", "$27$ cm"], "$2r+r\\theta=12+9$."),
         N("Find the area of the segment of a circle with radius 10 cm cut off by a chord that subtends 1.2 rad at the centre (cm², 1 d.p.).", round(seg, 1), "$\\frac12r^2(\\theta-\\sin\\theta)=50(1.2-0.9320)=13.4$.", tol=0.1)],
        P("A circular flower bed in Yaoundé has radius 12 m. A sector of angle $\\frac{2\\pi}{3}$ is planted with roses.", [pn("Find the length of the curved edge (m, 2 d.p.).", round(arc, 2), "$s=12\\times\\frac{2\\pi}{3}=8\\pi\\approx25.13$.", tol=0.02, pts=2), pn("Find the area of the rose sector (m², 1 d.p.).", round(area, 1), "$\\frac12\\times144\\times\\frac{2\\pi}{3}=48\\pi\\approx150.8$.", tol=0.1, pts=2)]),
        [("$180\\degree$ in radians is", "$\\pi$", ["$2\\pi$", "$\\frac{\\pi}{2}$", "$180$"], "Definition."),
         ("$\\frac{\\pi}{3}$ radians is", "$60\\degree$", ["$30\\degree$", "$45\\degree$", "$90\\degree$"], "$\\frac{180}{3}$."),
         ("A full turn is", "$2\\pi$ rad", ["$\\pi$ rad", "$360$ rad", "$\\frac{\\pi}{2}$ rad"], "$360\\degree$."),
         ("Sector area formula (radians) is", "$\\frac12r^2\\theta$", ["$r\\theta$", "$\\pi r^2\\theta$", "$\\frac12r\\theta^2$"], "Fraction $\\frac{\\theta}{2\\pi}$ of $\\pi r^2$."),
         ("$\\tan\\frac{\\pi}{4}$ equals", "$1$", ["$0$", "$\\frac{\\sqrt3}{2}$", "$\\sqrt3$"], "$45\\degree$: equal sides.")],
        ill=None, notes=[], ref=REF)

    # trig equations
    assert sorted(x for x in range(0, 361) if abs(2 * sin(radians(x)) - 1) < 1e-9) == [30, 150]
    assert sorted(x for x in range(0, 361) if abs(2 * cos(radians(x)) ** 2 + sin(radians(x)) - 1) < 1e-9) == [90, 210, 330]
    assert sorted(x for x in range(0, 361) if abs(3 * sin(radians(x)) ** 2 - cos(radians(x)) ** 2) < 1e-9) == [30, 150, 210, 330]
    assert sorted(x for x in range(0, 181) if abs(sin(radians(2 * x)) - .5) < 1e-9) == [15, 75]
    sinfig = plot(0, 6.4, -1.3, 1.3, curves=[("sin(x)", "blue", None)], points=[(pi / 6, .5, "π/6", "red"), (5 * pi / 6, .5, "5π/6", "red")], segments=[(0, .5, 6.4, .5, "grey", True, None)], xlabel="x (radians)")
    lesson(ch, "trig-equations", "Trigonometric identities and equations",
        ["Use $\\sin^2x+\\cos^2x=1$ and $\\tan x=\\frac{\\sin x}{\\cos x}$", "Solve trig equations in a given interval", "Prove simple identities"],
        [("propriete", "Identities", "$\\sin^2x+\\cos^2x=1$ and $\\tan x=\\frac{\\sin x}{\\cos x}$. Also $\\sin(180\\degree-x)=\\sin x$, $\\cos(360\\degree-x)=\\cos x$, $\\tan(180\\degree+x)=\\tan x$. Hence $1+\\tan^2x=\\frac{1}{\\cos^2x}$."),
         ("methode", "Solving equations", "1. Reduce to one trig function (use the identities).\n2. Find the principal value with the inverse function.\n3. Use symmetry (CAST / the graph) to find all other solutions in the interval.\n4. For $\\sin kx$, first solve for $kx$ over the enlarged interval, then divide by $k$."),
         ("methode", "Proving identities", "Start from the more complicated side, replace $\\tan$ by $\\frac{\\sin}{\\cos}$, use $\\sin^2+\\cos^2=1$, and simplify until you reach the other side. Do not work on both sides at once."),
         ("pieges", "Common mistakes", "Never divide both sides by $\\sin x$ (you lose the solutions where $\\sin x=0$): factorise instead. A calculator gives only ONE solution; the interval usually contains more. For $\\sin2x=0.5$ in $0\\leq x\\leq180\\degree$ look for $2x$ up to $360\\degree$.")],
        [("Example 1", "Solve $2\\sin x=1$ for $0\\degree\\leq x\\leq360\\degree$.", ["$\\sin x=\\frac12$, principal value $x=30\\degree$.", "Sine is positive in quadrants 1 and 2: the second solution is $180\\degree-30\\degree=150\\degree$."], "$x=30\\degree$ or $150\\degree$", sinfig),
         ("Example 2", "Solve $2\\cos^2x+\\sin x-1=0$ for $0\\degree\\leq x\\leq360\\degree$.", ["Replace $\\cos^2x=1-\\sin^2x$: $2-2\\sin^2x+\\sin x-1=0$, i.e. $2s^2-s-1=0$ with $s=\\sin x$.", "$(2s+1)(s-1)=0$: $s=-\\frac12$ or $s=1$.", "$s=1$: $x=90\\degree$. $s=-\\frac12$: $x=210\\degree$ or $330\\degree$."], "$x=90\\degree,210\\degree,330\\degree$")],
        [N("Find the smallest positive solution (in degrees) of $2\\cos x=\\sqrt{3}$.", 30, "$\\cos x=\\frac{\\sqrt3}{2}$, so $x=30\\degree$."),
         M("$\\frac{\\sin^2x}{1-\\sin^2x}$ simplifies to", "$\\tan^2x$", ["$\\frac{1}{\\tan x}$", "$1$", "$\\sin x$"], "$1-\\sin^2x=\\cos^2x$."),
         TF("$\\sin(180\\degree-x)=\\sin x$.", True, "Sine is symmetric about $90\\degree$.")],
        [M("Solve $\\sin2x=0.5$ for $0\\degree\\leq x\\leq180\\degree$.", "$x=15\\degree,75\\degree$", ["$x=15\\degree$ only", "$x=30\\degree,150\\degree$", "$x=15\\degree,165\\degree$"], "$2x=30\\degree$ or $150\\degree$ (next ones, 390 and 510, are too big): $x=15\\degree,75\\degree$."),
         O("Prove that $\\tan x+\\frac{1}{\\tan x}=\\frac{1}{\\sin x\\cos x}$.", "LHS $=\\frac{\\sin x}{\\cos x}+\\frac{\\cos x}{\\sin x}=\\frac{\\sin^2x+\\cos^2x}{\\sin x\\cos x}=\\frac{1}{\\sin x\\cos x}$ = RHS.", ["Rewrites with $\\sin$ and $\\cos$ (1)", "Common denominator (1)", "Uses $\\sin^2+\\cos^2=1$ (1)"])],
        P("Consider $3\\sin^2x=\\cos^2x$ for $0\\degree\\leq x\\leq360\\degree$.", [pn("Show it gives $\\tan^2x=\\frac13$ and find the smallest positive solution (degrees).", 30, "Divide by $\\cos^2x$: $\\tan^2x=\\frac13$, $\\tan x=\\pm\\frac{1}{\\sqrt3}$, smallest positive $x=30\\degree$.", pts=2), pn("How many solutions are there in the interval?", 4, "$\\tan x=\\frac{1}{\\sqrt3}$: $30\\degree,210\\degree$; $\\tan x=-\\frac1{\\sqrt3}$: $150\\degree,330\\degree$. Total 4.", pts=2)]),
        [("$\\sin^2x+\\cos^2x$ equals", "$1$", ["$0$", "$2$", "$\\sin2x$"], "Fundamental identity."),
         ("$\\tan x$ equals", "$\\frac{\\sin x}{\\cos x}$", ["$\\frac{\\cos x}{\\sin x}$", "$\\sin x\\cos x$", "$\\frac{1}{\\sin x}$"], "Definition."),
         ("If $\\sin x=0.8$ and $x$ is acute then $\\cos x=$", "$0.6$", ["$0.2$", "$0.4$", "$0.64$"], "$\\cos^2x=1-0.64=0.36$."),
         ("$\\cos x=-\\frac12$ has solutions in $[0\\degree,360\\degree]$:", "$120\\degree,240\\degree$", ["$60\\degree,300\\degree$", "$120\\degree,300\\degree$", "$150\\degree,210\\degree$"], "Cosine is negative in quadrants 2 and 3."),
         ("$\\tan x=1$ has how many solutions in $[0\\degree,360\\degree]$?", "2", ["1", "4", "0"], "$45\\degree$ and $225\\degree$.")],
        ill=None, notes=["Compound-angle and double-angle formulae are treated in the Upper Sixth pack; check whether the syllabus puts them in Lower Sixth."], ref=REF)

    # ------------------------------------------------------------ COORDINATE GEOMETRY
    ch = p.chapter("coordinate", "Coordinate geometry", REF + " — coordinate geometry of the straight line and circle")
    import math as m
    A_, B_ = (2, 1), (8, 5); assert abs(m.dist(A_, B_) ** 2 - 52) < 1e-9
    assert 3 * 3 - 2 * (-2) - 13 == 0 and (3 * 3 + 2 * 1 * 0) and abs(m.dist((1, 2), (7, 10)) - 10) < 1e-12
    assert abs(m.dist((0, 0), (2, 3)) - m.dist((4, 0), (2, 3))) < 1e-12 and 0.5 * 4 * 3 == 6
    assert abs(m.dist((3.75, 0), (-1, 2)) - m.dist((3.75, 0), (5, 5))) < 1e-9
    axl = Axes(-1, 9, -1, 7, unit=30, xticks=[2, 4, 6, 8], yticks=[2, 4, 6])
    axl.line(2, 1, 8, 5, color="blue").pt(2, 1, "A(2, 1)", dx=-6, dy=-10, anchor="end").pt(8, 5, "B(8, 5)", dy=-10).pt(5, 3, "M(5, 3)", color="green", dx=8, dy=20, anchor="start")
    lesson(ch, "lines", "Straight lines",
        ["Find gradient, midpoint and distance", "Write the equation of a line in different forms", "Use parallel and perpendicular conditions"],
        [("formule", "Gradient, midpoint, distance", "For $A(x_1,y_1)$ and $B(x_2,y_2)$: gradient $m=\\frac{y_2-y_1}{x_2-x_1}$; midpoint $\\left(\\frac{x_1+x_2}{2},\\frac{y_1+y_2}{2}\\right)$; length $AB=\\sqrt{(x_2-x_1)^2+(y_2-y_1)^2}$."),
         ("formule", "Equation of a line", "Gradient form $y=mx+c$; through $(x_1,y_1)$ with gradient $m$: $y-y_1=m(x-x_1)$; through two points: use $m$ first; general form $ax+by+c=0$."),
         ("propriete", "Parallel and perpendicular", "Parallel lines have equal gradients. Perpendicular lines satisfy $m_1m_2=-1$, i.e. $m_2=-\\frac{1}{m_1}$. The perpendicular bisector of $AB$ passes through the midpoint of $AB$ with gradient $-\\frac{1}{m_{AB}}$."),
         ("pieges", "Common mistakes", "Subtract the coordinates in the SAME order in numerator and denominator. The gradient of $ax+by+c=0$ is $-\\frac{a}{b}$. Vertical lines ($x=k$) have no gradient.")],
        [("Example 1", "$A(2,1)$, $B(8,5)$. Find the gradient of $AB$, its midpoint and its length.", ["$m=\\frac{5-1}{8-2}=\\frac{4}{6}=\\frac23$.", "Midpoint $\\left(\\frac{2+8}{2},\\frac{1+5}{2}\\right)=(5,3)$.", "$AB=\\sqrt{6^2+4^2}=\\sqrt{52}=2\\sqrt{13}$."], "$m=\\frac23$; $M(5,3)$; $AB=2\\sqrt{13}$", axl.fig()),
         ("Example 2", "Find the line through $(3,-2)$ perpendicular to $2x+3y=6$.", ["$3y=-2x+6$ so the gradient is $-\\frac23$; the perpendicular gradient is $\\frac32$.", "$y+2=\\frac32(x-3)$, so $2y+4=3x-9$.", "Rearrange: $3x-2y-13=0$. Check $(3,-2)$: $9+4-13=0$."], "$3x-2y-13=0$")],
        [N("Find the distance between $(1,2)$ and $(7,10)$.", 10, "$\\sqrt{36+64}=10$."),
         M("The gradient of $4x-2y=7$ is", "$2$", ["$-2$", "$4$", "$\\frac12$"], "$y=2x-\\frac72$."),
         TF("The lines $y=2x+1$ and $y=-\\frac{x}{2}+3$ are perpendicular.", True, "$2\\times\\left(-\\frac12\\right)=-1$.")],
        [M("Which line is parallel to $3x+y=5$ and passes through $(0,0)$?", "$y=-3x$", ["$y=3x$", "$y=\\frac{x}{3}$", "$y=-\\frac{x}{3}$"], "Gradient $-3$ through the origin."),
         O("$P(0,0)$, $Q(4,0)$ and $R(2,3)$ are the vertices of a triangle. Show it is isosceles and find its area.", "$PR=\\sqrt{4+9}=\\sqrt{13}$ and $QR=\\sqrt{4+9}=\\sqrt{13}$, so $PR=QR$ (isosceles). Base $PQ=4$, height 3, area $=\\frac12\\times4\\times3=6$.", ["Computes both lengths (1)", "States isosceles (1)", "Area 6 (1)"])],
        P("$A(-1,2)$ and $B(5,5)$ are two points.", [pn("Find the gradient of $AB$.", 0.5, "$\\frac{5-2}{5+1}=\\frac12$.", tol=0.001, pts=1), pn("The perpendicular bisector of $AB$ meets the x-axis at $(k,0)$. Find $k$.", 3.75, "Midpoint $(2,3.5)$; gradient $-2$: $y-3.5=-2(x-2)$, i.e. $y=-2x+7.5$. Set $y=0$: $x=3.75$. Check: equal distances from $A$ and $B$.", tol=0.001, pts=3)]),
        [("Midpoint of $(2,4)$ and $(6,10)$ is", "$(4,7)$", ["$(8,14)$", "$(2,3)$", "$(4,6)$"], "Average each coordinate."),
         ("Gradient of the line through $(1,1)$ and $(3,7)$ is", "$3$", ["$\\frac13$", "$2$", "$-3$"], "$\\frac{6}{2}$."),
         ("A line perpendicular to gradient $\\frac25$ has gradient", "$-\\frac52$", ["$\\frac52$", "$-\\frac25$", "$\\frac25$"], "Negative reciprocal."),
         ("The line through $(0,3)$ with gradient $-2$ is", "$y=-2x+3$", ["$y=2x+3$", "$y=3x-2$", "$y=-2x-3$"], "$c=3$."),
         ("The distance from the origin to $(5,12)$ is", "$13$", ["$17$", "$7$", "$\\sqrt{17}$"], "$\\sqrt{25+144}$.")],
        ill=None, notes=[], ref=REF)

    # circles
    assert (7 - 3) ** 2 + (1 + 2) ** 2 == 25 and 4 * 7 + 3 * 1 - 31 == 0
    assert (5 - 2) ** 2 + (5 - 1) ** 2 == 25 and abs(35 / 3 - 11.667) < 1e-3
    circ = Axes(-4, 10, -8, 5, unit=17, xticks=[-2, 2, 6, 10], yticks=[-6, -2, 2, 4])
    circ.circle(3, -2, 5, color="blue").pt(3, -2, "C(3, −2)", color="red", dx=-8, dy=18, anchor="end").pt(7, 1, "P(7, 1)", color="green", dx=8, dy=-8, anchor="start")
    circ.line(3, -2, 7, 1, color="red", width=2).line(4.5, 13 / 3, 9.5, -7 / 3, color="orange", width=2)
    lesson(ch, "circles", "Circles",
        ["Write and interpret the equation of a circle", "Find centre and radius by completing the square", "Find tangents and line–circle intersections"],
        [("formule", "Equation of a circle", "Centre $(a,b)$, radius $r$: $(x-a)^2+(y-b)^2=r^2$. Expanded form: $x^2+y^2+2gx+2fy+c=0$ with centre $(-g,-f)$ and radius $\\sqrt{g^2+f^2-c}$."),
         ("methode", "Tangent at a point", "The tangent at a point $P$ on a circle is perpendicular to the radius $CP$. 1. Find the gradient of $CP$. 2. The tangent gradient is the negative reciprocal. 3. Use $y-y_1=m(x-x_1)$ through $P$."),
         ("methode", "Line meets circle", "Substitute the line's equation into the circle's equation to get a quadratic: $\\Delta>0$ two intersection points, $\\Delta=0$ tangent, $\\Delta<0$ no intersection. A point is inside, on or outside the circle if its distance to the centre is less than, equal to or greater than $r$."),
         ("attention", "Watch out", "The right side of $(x-a)^2+(y-b)^2=r^2$ is $r^2$, not $r$. Signs flip: $(x+1)^2$ means the centre has $x$-coordinate $-1$.")],
        [("Example 1", "Find the centre and radius of $x^2+y^2-6x+4y-12=0$.", ["Complete the squares: $(x-3)^2-9+(y+2)^2-4-12=0$.", "$(x-3)^2+(y+2)^2=25$."], "Centre $(3,-2)$, radius 5"),
         ("Example 2", "Show $P(7,1)$ is on that circle, and find the tangent at $P$.", ["$(7-3)^2+(1+2)^2=16+9=25$, so $P$ is on the circle.", "Gradient of $CP=\\frac{1-(-2)}{7-3}=\\frac34$; tangent gradient $-\\frac43$.", "$y-1=-\\frac43(x-7)$, so $3y-3=-4x+28$ and $4x+3y-31=0$."], "$4x+3y-31=0$", circ.fig())],
        [N("Find the radius of $x^2+y^2+2x-4y-4=0$.", 3, "$(x+1)^2+(y-2)^2=4+1+4=9$, so $r=3$."),
         M("The circle with centre $(2,-1)$ and radius 3 has equation", "$(x-2)^2+(y+1)^2=9$", ["$(x+2)^2+(y-1)^2=9$", "$(x-2)^2+(y+1)^2=3$", "$(x-2)^2+(y-1)^2=9$"], "Centre signs flip; the right side is $r^2$."),
         TF("The point $(3,4)$ is inside the circle $x^2+y^2=20$.", False, "$3^2+4^2=25>20$: the point is outside.")],
        [M("The line $y=x$ meets $x^2+y^2=8$ at", "$(2,2)$ and $(-2,-2)$", ["$(2,2)$ only", "$(\\sqrt8,\\sqrt8)$ only", "$(4,4)$ and $(-4,-4)$"], "$2x^2=8$ gives $x=\\pm2$."),
         O("Find the equation of the circle that has the line segment from $A(1,2)$ to $B(7,10)$ as a diameter.", "Centre = midpoint $(4,6)$. Diameter $AB=\\sqrt{36+64}=10$, so $r=5$. Equation $(x-4)^2+(y-6)^2=25$.", ["Midpoint as centre (1)", "Radius 5 (1)", "Correct equation (1)"])],
        P("A circle has equation $x^2+y^2-4x-2y-20=0$.", [pn("Find its radius.", 5, "$(x-2)^2+(y-1)^2=25$; centre $(2,1)$, $r=5$.", pts=1), pn("The point $(5,5)$ lies on the circle. The tangent there meets the x-axis at $(k,0)$. Find $k$ (2 d.p.).", 11.67, "Radius gradient $\\frac{5-1}{5-2}=\\frac43$, tangent gradient $-\\frac34$: $y-5=-\\frac34(x-5)$, i.e. $3x+4y-35=0$. At $y=0$: $x=\\frac{35}{3}\\approx11.67$.", tol=0.01, pts=3)]),
        [("The centre of $(x-1)^2+(y+4)^2=9$ is", "$(1,-4)$", ["$(-1,4)$", "$(1,4)$", "$(-1,-4)$"], "Signs flip."),
         ("The radius of $x^2+y^2=49$ is", "$7$", ["$49$", "$\\frac{49}{2}$", "$\\sqrt7$"], "$r^2=49$."),
         ("A tangent to a circle is ... to the radius at the point of contact", "perpendicular", ["parallel", "equal", "at $45\\degree$"], "Circle theorem."),
         ("A line meets a circle in 2 points when the discriminant is", "$>0$", ["$=0$", "$<0$", "negative"], "Two real roots."),
         ("Distance from $(0,0)$ to centre $(3,4)$ is", "$5$", ["$7$", "$25$", "$1$"], "$\\sqrt{9+16}$.")],
        ill=None, notes=["Figure drawn to equal scale on both axes."], ref=REF)
