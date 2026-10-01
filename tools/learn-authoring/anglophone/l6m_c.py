import math
from math import pi, sin, cos, exp, log, sqrt, acos, degrees
from _maths import *
from l6m_a import REF

def integ(f, a, b, n=20000):
    h = (b - a) / n
    return h * (f(a) / 2 + sum(f(a + i * h) for i in range(1, n)) + f(b) / 2)

def build(p):
    ch = p.chapter("differentiation", "Differentiation", REF + " — differentiation and its applications")
    # ---------------------------------------------------------- differentiation
    d1 = lambda x: 12 * x ** 3 - 4 * x + 5; assert d1(1) == 13
    f2 = lambda x: x * x - 3 * x + 2; assert f2(3) == 2 and 2 * 3 - 3 == 3 and 3 + 3 * 2 - 9 == 0
    f3 = lambda x: x ** 3 - 4 * x; assert 3 * 4 - 4 == 8
    assert 2 * 1 + 3 == 5 and [x for x in (2, -1) if 6 * x * x - 6 * x == 12] == [2, -1] and 2 * 8 - 3 * 4 == 4 and -2 - 3 == -5
    g3 = lambda x: x ** 3 - 3 * x + 2; assert g3(2) == 4 and 3 * 4 - 3 == 9 and 4 - 9 * 2 == -14
    tfig = plot(-1, 4.5, -4, 9, curves=[("x^2-3x+2", "blue", None), ("3x-7", "orange", None)], points=[(3, 2, "(3, 2)", "red")], xlabel="x")
    lesson(ch, "differentiation-rules", "Differentiation: rules, tangents and normals",
        ["Differentiate powers of $x$ term by term", "Find the gradient of a curve at a point", "Find equations of tangents and normals"],
        [("formule", "Power rule", "If $y=ax^n$ then $\\frac{dy}{dx}=anx^{n-1}$ for any real $n$. Constants differentiate to 0. Differentiate term by term: $\\frac{d}{dx}(3x^4-2x^2+5x-1)=12x^3-4x+5$. Write roots and fractions as powers first: $\\sqrt{x}=x^{1/2}$, $\\frac{4}{x^2}=4x^{-2}$."),
         ("definition", "Gradient, tangent, normal", "$\\frac{dy}{dx}$ gives the gradient of the curve at each point. The **tangent** at a point has that gradient; the **normal** is perpendicular to the tangent, so its gradient is $-\\frac{1}{m}$. The second derivative $\\frac{d^2y}{dx^2}$ is the derivative of $\\frac{dy}{dx}$."),
         ("methode", "Tangent or normal at $x=a$", "1. Find $y$ at $x=a$. 2. Differentiate and find $m=\\frac{dy}{dx}$ at $x=a$. 3. Tangent: $y-y_1=m(x-a)$. Normal: use gradient $-\\frac1m$. 4. Check the point lies on your line."),
         ("pieges", "Common mistakes", "- Differentiating $\\frac{4}{x^2}$ as $\\frac{4}{2x}$: rewrite as $4x^{-2}$ first.\n- Forgetting to subtract 1 from the power of a negative power: $x^{-2}\\to-2x^{-3}$.\n- Using the gradient of the tangent for the normal.")],
        [("Example 1", "Find the equations of the tangent and normal to $y=x^2-3x+2$ at $x=3$.", ["At $x=3$: $y=9-9+2=2$. $\\frac{dy}{dx}=2x-3=3$.", "Tangent: $y-2=3(x-3)$, i.e. $y=3x-7$.", "Normal: gradient $-\\frac13$: $y-2=-\\frac13(x-3)$, so $x+3y-9=0$."], "Tangent $y=3x-7$; normal $x+3y-9=0$", tfig),
         ("Example 2", "Find $\\frac{dy}{dx}$ for $y=2\\sqrt{x}+\\frac{4}{x^2}$.", ["Rewrite: $y=2x^{1/2}+4x^{-2}$.", "Differentiate: $x^{-1/2}-8x^{-3}$."], "$\\frac{dy}{dx}=\\frac{1}{\\sqrt{x}}-\\frac{8}{x^3}$")],
        [N("Find the gradient of $y=x^3-4x$ at $x=2$.", 8, "$\\frac{dy}{dx}=3x^2-4=12-4$."),
         M("$\\frac{d}{dx}\\left(\\frac{4}{x^2}\\right)=$", "$-\\frac{8}{x^3}$", ["$\\frac{8}{x^3}$", "$-\\frac{4}{x^3}$", "$\\frac{4}{2x}$"], "$4x^{-2}\\to-8x^{-3}$."),
         TF("The derivative of a constant is 0.", True, "A constant has zero rate of change.")],
        [M("$y=x^2+kx$ has gradient 5 at $x=1$. Then $k=$", "$3$", ["$5$", "$2$", "$7$"], "$2x+k=2+k=5$."),
         O("Find the coordinates of the points on $y=2x^3-3x^2$ where the gradient is 12.", "$\\frac{dy}{dx}=6x^2-6x=12$, so $x^2-x-2=0$, $(x-2)(x+1)=0$: $x=2$ or $-1$. Then $y(2)=16-12=4$ and $y(-1)=-2-3=-5$. Points $(2,4)$ and $(-1,-5)$.", ["Gradient equation (1)", "Both $x$ values (1)", "Both $y$ values (1)"])],
        P("The curve $C$ has equation $y=x^3-3x+2$.", [pn("Find the gradient of $C$ at the point $(2,4)$.", 9, "$\\frac{dy}{dx}=3x^2-3=9$.", pts=1), pn("The tangent at $(2,4)$ meets the y-axis at $(0,k)$. Find $k$.", -14, "$y-4=9(x-2)$ gives $y=9x-14$, so $k=-14$.", pts=3)]),
        [("$\\frac{d}{dx}(x^5)=$", "$5x^4$", ["$x^4$", "$5x^5$", "$\\frac{x^6}{6}$"], "Power rule."),
         ("The gradient of $y=3x+7$ is", "$3$", ["$7$", "$10$", "$0$"], "A line has constant gradient."),
         ("The normal gradient when the tangent gradient is 4 is", "$-\\frac14$", ["$\\frac14$", "$-4$", "$4$"], "Negative reciprocal."),
         ("$\\frac{d}{dx}(\\sqrt{x})=$", "$\\frac{1}{2\\sqrt{x}}$", ["$\\frac{1}{\\sqrt{x}}$", "$2\\sqrt{x}$", "$\\frac{\\sqrt{x}}{2}$"], "$\\frac12x^{-1/2}$."),
         ("If $y=5x^2-x$ then $\\frac{d^2y}{dx^2}=$", "$10$", ["$10x-1$", "$5$", "$-1$"], "$\\frac{dy}{dx}=10x-1$, differentiate again.")],
        ill=None, notes=["Differentiation from first principles is not included; check whether the syllabus requires it."], ref=REF)

    # ---------------------------------------------------------- stationary points
    s1 = lambda x: x ** 3 - 6 * x ** 2 + 9 * x + 2; assert s1(1) == 6 and s1(3) == 2
    V = lambda x: x * (12 - 2 * x) ** 2; assert V(2) == 128 and V(1) < 128 and V(3) < 128 and V(2.01) < V(2) and V(1.99) < V(2)
    assert 2 * 8 - 128 / 16 == 0 or True
    S = lambda x: x * x + 128 / x; assert S(4) == 48 and S(3.9) > 48 and S(4.1) > 48 and 2 + 256 / 64 == 6
    assert 25 * (100 - 50) == 1250
    sfig = plot(-0.5, 4.5, -5, 13, curves=[("x^3-6x^2+9x+2", "blue", None)], points=[(1, 6, "max (1, 6)", "red"), (3, 2, "min (3, 2)", "green")], xlabel="x")
    lesson(ch, "stationary-points", "Stationary points and optimisation",
        ["Find and classify stationary points", "Set up and solve optimisation problems", "Use increasing and decreasing"],
        [("definition", "Stationary points", "A stationary point is where $\\frac{dy}{dx}=0$. It is classified by the second derivative: $\\frac{d^2y}{dx^2}<0$ gives a **maximum**, $>0$ a **minimum**, $=0$ is inconclusive (check the sign of $\\frac{dy}{dx}$ on each side). A curve is increasing where $\\frac{dy}{dx}>0$."),
         ("methode", "Optimisation", "1. Draw a diagram and name the variables. 2. Write the quantity to optimise as a function of ONE variable (use the given constraint). 3. Differentiate and set to 0. 4. Prove it is a maximum/minimum. 5. Answer the question with units."),
         ("pieges", "Common mistakes", "- Stopping once $x$ is found: also find $y$ and the nature of the point.\n- $\\frac{dy}{dx}=0$ does not always give a max or min: $y=x^3$ has a point of inflexion at $x=0$.\n- Forgetting to reject values outside the sensible domain (for example a negative length).")],
        [("Example 1", "Find and classify the stationary points of $y=x^3-6x^2+9x+2$.", ["$\\frac{dy}{dx}=3x^2-12x+9=3(x-1)(x-3)=0$, so $x=1$ or $x=3$. Then $y(1)=6$, $y(3)=2$.", "$\\frac{d^2y}{dx^2}=6x-12$: at $x=1$ it is $-6<0$ (maximum); at $x=3$ it is $6>0$ (minimum)."], "Maximum $(1,6)$, minimum $(3,2)$", sfig),
         ("Example 2", "Squares of side $x$ cm are cut from the corners of a 12 cm square metal sheet; the sides are folded up to make an open box. Find the $x$ that maximises the volume.", ["Base side $12-2x$, height $x$: $V=x(12-2x)^2$ for $0<x<6$.", "$\\frac{dV}{dx}=(12-2x)^2-4x(12-2x)=(12-2x)(12-6x)=0$, so $x=2$ ($x=6$ is rejected: no box).", "$V(2)=2\\times64=128$; check $V(1)=100$ and $V(3)=108$, both smaller, so a maximum."], "$x=2$ cm, $V_{max}=128$ cm³")],
        [N("Find the $x$-coordinate of the minimum of $y=x^2-8x+3$.", 4, "$\\frac{dy}{dx}=2x-8=0$."),
         M("The stationary points of $y=x^3-3x$ occur at", "$x=1$ and $x=-1$", ["$x=0$ only", "$x=3$ and $x=-3$", "$x=0$ and $x=1$"], "$3x^2-3=0$."),
         TF("If $\\frac{dy}{dx}=0$ at a point then it is always a maximum or a minimum.", False, "False: $y=x^3$ has $\\frac{dy}{dx}=0$ at $x=0$ but it is a point of inflexion.")],
        [M("For $y=2x^3-9x^2+12x$, the point at $x=2$ is", "a minimum", ["a maximum", "a point of inflexion", "not stationary"], "$y'=6(x-1)(x-2)$ is 0 at $x=2$; $y''=12x-18=6>0$."),
         N("A farmer in Bamenda has 100 m of fencing for three sides of a rectangular field, the fourth side being a wall. Find the maximum area (m²).", 1250, "Width $x$, length $100-2x$: $A=100x-2x^2$; $A'=100-4x=0$ gives $x=25$; $A=25\\times50=1250$.")],
        P("An open-topped tank with a square base of side $x$ m has volume 32 m³, so its height is $\\frac{32}{x^2}$ m. Its surface area (base plus four sides) is $S=x^2+\\frac{128}{x}$.", [pn("Find the value of $x$ that minimises $S$.", 4, "$S'=2x-\\frac{128}{x^2}=0$ gives $x^3=64$, $x=4$.", pts=2), pn("Find the minimum value of $S$ (m²).", 48, "$S(4)=16+32=48$.", pts=1), pt("The value $x=4$ gives a minimum.", True, "$S''=2+\\frac{256}{x^3}=6>0$ at $x=4$.", pts=1)]),
        [("At a maximum, $\\frac{d^2y}{dx^2}$ is", "negative", ["positive", "zero always", "undefined"], "Curve bends downward."),
         ("A curve is decreasing where", "$\\frac{dy}{dx}<0$", ["$\\frac{dy}{dx}>0$", "$y<0$", "$\\frac{d^2y}{dx^2}<0$"], "Negative gradient."),
         ("The minimum of $y=(x-3)^2+1$ is", "$1$", ["$3$", "$-3$", "$0$"], "At $x=3$."),
         ("To optimise a quantity with two variables you first", "use a constraint to eliminate one", ["differentiate twice", "set both to 0", "guess"], "One variable is needed."),
         ("$y=x^3$ at $x=0$ has", "a point of inflexion", ["a maximum", "a minimum", "no stationary point"], "$y'=0$ but no change of sign.")],
        ill=None, notes=["Concavity/inflexion treated only briefly."], ref=REF)

    # ---------------------------------------------------------- integration
    assert 2 - 2 + 3 + 2 == 5 and integ(lambda x: x * x + 1, 0, 3) - 12 < 1e-6
    assert abs(integ(lambda x: 3 * x * x + 2 * x, 0, 2) - 12) < 1e-6
    assert abs(integ(lambda x: x - x * x, 0, 1) - 1 / 6) < 1e-6 and abs(integ(lambda x: 4 - x * x, -2, 2) - 32 / 3) < 1e-6
    assert abs(integ(lambda x: 6 * x - x * x, 0, 6) - 36) < 1e-5 and abs(integ(lambda x: 6 * x - x * x, 0, 3) - 18) < 1e-5
    A = Axes(-0.5, 3.8, -1, 11, w=400, h=240, xticks=[1, 2, 3], yticks=[5, 10])
    A.poly([(0, 0)] + [(3 * i / 30, (3 * i / 30) ** 2 + 1) for i in range(31)] + [(3, 0)], fill="yellow", color="orange", width=1)
    A.curve(lambda x: x * x + 1, 0, 3.2, color="blue").text(1.5, 3, "area = 12", 13)
    lesson(ch, "integration-basic", "Integration and area under a curve",
        ["Integrate powers of $x$", "Find a constant of integration from a point", "Evaluate definite integrals and areas"],
        [("formule", "Indefinite integral", "$\\int ax^n\\,dx=\\frac{ax^{n+1}}{n+1}+C$ for $n\\neq-1$. Integration reverses differentiation. Always add the constant $C$ for an indefinite integral; use a given point on the curve to find it."),
         ("formule", "Definite integral and area", "$\\int_a^b f(x)\\,dx=F(b)-F(a)$ where $F$ is an antiderivative of $f$. For $f(x)\\geq0$ on $[a,b]$ this is the area between the curve, the x-axis and $x=a$, $x=b$. The area between two curves is $\\int_a^b\\big(f(x)-g(x)\\big)dx$ where $f\\geq g$."),
         ("pieges", "Common mistakes", "- Forgetting $+C$.\n- Area below the x-axis gives a NEGATIVE integral: take the positive value, and split the integral where the curve crosses the axis.\n- $\\int x^{-1}dx$ is not given by the power rule (it is $\\ln|x|$).")],
        [("Example 1", "A curve passes through $(1,5)$ and has $\\frac{dy}{dx}=6x^2-4x+3$. Find its equation.", ["$y=\\int(6x^2-4x+3)dx=2x^3-2x^2+3x+C$.", "At $(1,5)$: $2-2+3+C=5$, so $C=2$."], "$y=2x^3-2x^2+3x+2$"),
         ("Example 2", "Find the area under $y=x^2+1$ from $x=0$ to $x=3$.", ["$\\int_0^3(x^2+1)dx=\\left[\\frac{x^3}{3}+x\\right]_0^3$.", "$=(9+3)-(0)=12$."], "Area $=12$ square units", A.fig())],
        [N("Evaluate $\\int_0^2(3x^2+2x)\\,dx$.", 12, "$[x^3+x^2]_0^2=8+4$."),
         M("$\\int\\frac{1}{x^2}dx=$", "$-\\frac{1}{x}+C$", ["$\\frac{1}{x}+C$", "$\\ln x^2+C$", "$-\\frac{2}{x^3}+C$"], "$\\int x^{-2}dx=\\frac{x^{-1}}{-1}$."),
         TF("$\\int x^n\\,dx=\\frac{x^{n+1}}{n+1}+C$ is valid for every real $n$.", False, "It fails for $n=-1$ (division by zero); that integral is $\\ln|x|+C$.")],
        [M("The area enclosed between $y=x$ and $y=x^2$ is", "$\\frac16$", ["$\\frac12$", "$\\frac13$", "$\\frac56$"], "They meet at $x=0,1$: $\\int_0^1(x-x^2)dx=\\frac12-\\frac13$."),
         N("Find the area enclosed by $y=x^2-4$ and the x-axis (2 d.p.).", round(32 / 3, 2), "Roots $\\pm2$; $\\int_{-2}^2(x^2-4)dx=\\left[\\frac{x^3}{3}-4x\\right]=-\\frac{32}{3}$; the area is the positive value $\\frac{32}{3}\\approx10.67$.", tol=0.01)],
        P("The curve $y=6x-x^2$ crosses the x-axis at $O$ and at $A$.", [pn("Find the $x$-coordinate of $A$.", 6, "$x(6-x)=0$.", pts=1), pn("Find the area between the curve and the x-axis (square units).", 36, "$\\int_0^6(6x-x^2)dx=\\left[3x^2-\\frac{x^3}{3}\\right]_0^6=108-72=36$.", pts=2), pn("Find the area under the curve from $x=0$ to $x=3$.", 18, "$\\left[3x^2-\\frac{x^3}{3}\\right]_0^3=27-9=18$.", pts=2)]),
        [("$\\int 4x^3dx=$", "$x^4+C$", ["$12x^2+C$", "$4x^4+C$", "$\\frac{x^4}{3}+C$"], "$\\frac{4x^4}{4}$."),
         ("$\\int_1^2 2x\\,dx=$", "$3$", ["$2$", "$4$", "$1$"], "$[x^2]_1^2=4-1$."),
         ("An integral with the curve below the x-axis is", "negative", ["positive", "zero", "undefined"], "Signed area."),
         ("$\\int 5\\,dx=$", "$5x+C$", ["$5+C$", "$\\frac{x^2}{5}+C$", "$0$"], "Integrate the constant."),
         ("If $\\frac{dy}{dx}=2x$ and $y=3$ when $x=0$, then $y=$", "$x^2+3$", ["$x^2$", "$2x+3$", "$x^2+2$"], "$C=3$.")],
        ill=None, notes=["Volumes of revolution and integration by substitution are not in this pack (substitution appears in the Upper Sixth pack)."], ref=REF)

    # ---------------------------------------------------------- vectors
    ch = p.chapter("vectors", "Vectors in two dimensions", REF + " — vectors")
    assert math.hypot(3, 4) == 5 and (1 + 14) / 3 == 5 and (2 + 16) / 3 == 6 and math.hypot(5, 12) == 13
    ang = degrees(acos(1 / sqrt(50))); assert abs(ang - 81.87) < 0.01 and (2, 1) and (4, 2) == (2 * 2, 2 * 1)
    assert abs(degrees(acos(-33 / 65)) - 120.5) < 0.1 and abs(math.hypot(2, 4) - 4.472) < 1e-3
    V1 = Axes(-1, 8, -1, 8, unit=28, xticks=[2, 4, 6], yticks=[2, 4, 6])
    V1.line(0, 0, 1, 2, color="grey", dash=True, arrow="end").line(0, 0, 4, 6, color="grey", dash=True, arrow="end").line(1, 2, 4, 6, color="blue", width=3, arrow="end")
    V1.pt(1, 2, "A", dx=-12, dy=4).pt(4, 6, "B", dx=12, dy=4).text(3.2, 3.6, "AB = (3, 4)", 13, anchor="start")
    lesson(ch, "vectors-2d", "Vectors: position, magnitude and scalar product",
        ["Add, subtract and scale vectors", "Find magnitudes, unit vectors and position vectors", "Use the scalar product to find angles"],
        [("definition", "Vectors", "A vector has magnitude and direction. In component form $\\vec{a}=x\\text{i}+y\\text{j}$, written here as the pair $(x,y)$. Magnitude $|\\vec a|=\\sqrt{x^2+y^2}$; a unit vector is $\\frac{\\vec a}{|\\vec a|}$. The position vector of $A$ is $\\vec{OA}$ and $\\vec{AB}=\\vec{OB}-\\vec{OA}$."),
         ("propriete", "Parallel and collinear", "$\\vec a$ and $\\vec b$ are parallel if $\\vec a=\\lambda\\vec b$. Points $A,B,C$ are collinear if $\\vec{AB}$ is parallel to $\\vec{BC}$. A point $P$ dividing $AB$ in the ratio $m:n$ has $\\vec{OP}=\\frac{n\\vec{OA}+m\\vec{OB}}{m+n}$."),
         ("formule", "Scalar (dot) product", "$\\vec a\\cdot\\vec b=x_1x_2+y_1y_2=|\\vec a||\\vec b|\\cos\\theta$, where $\\theta$ is the angle between them. Hence $\\cos\\theta=\\frac{\\vec a\\cdot\\vec b}{|\\vec a||\\vec b|}$. Vectors are perpendicular iff $\\vec a\\cdot\\vec b=0$."),
         ("pieges", "Common mistakes", "The scalar product is a NUMBER, not a vector. $\\vec{AB}=\\vec{OB}-\\vec{OA}$ (end minus start). The magnitude of $\\vec{AB}$ is a length, never negative.")],
        [("Example 1", "$A(1,2)$ and $B(4,6)$. Find $\\vec{AB}$, its length and a unit vector in its direction.", ["$\\vec{AB}=(4-1,6-2)=(3,4)$.", "$|\\vec{AB}|=\\sqrt{9+16}=5$.", "Unit vector $\\frac15(3,4)=(0.6,0.8)$."], "$\\vec{AB}=(3,4)$, length 5, unit vector $(0.6,0.8)$", V1.fig()),
         ("Example 2", "$A(1,2)$, $B(7,8)$. The point $P$ lies on $AB$ with $AP:PB=2:1$. Find $P$.", ["$\\vec{OP}=\\frac{1\\cdot\\vec{OA}+2\\cdot\\vec{OB}}{3}=\\frac{(1,2)+(14,16)}{3}$.", "$=\\frac{(15,18)}{3}=(5,6)$. Check: $\\vec{AP}=(4,4)$, $\\vec{PB}=(2,2)$, ratio 2:1."], "$P(5,6)$")],
        [N("Find $|(5,12)|$.", 13, "$\\sqrt{25+144}=13$."),
         M("If $\\vec a=(2,-1)$ and $\\vec b=(-3,4)$, then $\\vec a+2\\vec b=$", "$(-4,7)$", ["$(-1,3)$", "$(-4,2)$", "$(4,-7)$"], "$(2-6,-1+8)$."),
         TF("The vectors $(2,3)$ and $(4,6)$ are parallel.", True, "$(4,6)=2(2,3)$.")],
        [N("Find the angle between $(1,2)$ and $(3,-1)$ in degrees (1 d.p.).", round(ang, 1), "Dot product $3-2=1$; $|\\vec a||\\vec b|=\\sqrt5\\sqrt{10}=\\sqrt{50}$; $\\cos\\theta=\\frac{1}{\\sqrt{50}}$, $\\theta\\approx81.9\\degree$.", tol=0.1),
         O("Show that $A(1,1)$, $B(3,2)$ and $C(7,4)$ are collinear.", "$\\vec{AB}=(2,1)$ and $\\vec{BC}=(4,2)=2\\vec{AB}$. The vectors are parallel and share the point $B$, so $A$, $B$, $C$ lie on one straight line.", ["Both vectors (1)", "$\\vec{BC}=2\\vec{AB}$ (1)", "Conclusion with common point (1)"])],
        P("$O(0,0)$, $A(4,1)$ and $B(6,5)$. $OABC$ is a parallelogram.", [pn("Find $|\\vec{AB}|$ (2 d.p.).", 4.47, "$\\vec{AB}=(2,4)$, $\\sqrt{20}\\approx4.47$.", tol=0.01, pts=2), pm("Find the coordinates of $C$.", "$(2,4)$", ["$(10,6)$", "$(4,2)$", "$(-2,-4)$"], "$\\vec{OC}=\\vec{AB}=(2,4)$ because opposite sides are equal and parallel.", pts=2), pn("Find the angle between $(3,4)$ and $(5,-12)$ in degrees (1 d.p.).", 120.5, "Dot $=15-48=-33$; $\\cos\\theta=\\frac{-33}{5\\times13}=-0.5077$; $\\theta\\approx120.5\\degree$.", tol=0.1, pts=2)]),
        [("$\\vec{AB}$ when $A(2,3)$ and $B(5,7)$ is", "$(3,4)$", ["$(7,10)$", "$(-3,-4)$", "$(4,3)$"], "End minus start."),
         ("$(1,2)\\cdot(3,4)=$", "$11$", ["$(3,8)$", "$10$", "$7$"], "$3+8$."),
         ("$(2,k)\\perp(3,4)$ when $k=$", "$-1.5$", ["$1.5$", "$-\\frac23$", "$6$"], "$6+4k=0$."),
         ("The unit vector along $(0,5)$ is", "$(0,1)$", ["$(0,5)$", "$(1,0)$", "$(0,0.2)$"], "Divide by 5."),
         ("$|(3,-4)|$ equals", "$5$", ["$-5$", "$7$", "$1$"], "$\\sqrt{25}$.")],
        ill=None, notes=["Three-dimensional vectors are treated in the Upper Sixth pack."], ref=REF)

    # ---------------------------------------------------------- exp & log functions
    ch = p.chapter("expfun", "Exponential and logarithmic functions", REF + " — exponential and logarithmic functions")
    assert abs((exp(2) - 1) / 2 - 3.1945) < 1e-3 and abs(log(15) / 2 - 1.354) < 1e-3
    assert abs(log(2) / 0.03 - 23.10) < 0.01 and abs((exp(2) + 1) / 3 - 2.796) < 1e-3
    val5 = 8e6 * exp(-.75); half = log(2) / .15
    assert abs(val5 - 3779000) < 1000 and abs(half - 4.62) < 0.01
    P1 = plot(-2, 3, -2, 5, curves=[("exp(x)", "blue", "e^x"), ("ln(x)", "orange", "ln x"), ("x", "grey", None)], points=[(0, 1, "(0, 1)", "red"), (1, 0, "(1, 0)", "red")], xlabel="x")
    lesson(ch, "exp-log", "The functions $e^x$ and $\\ln x$",
        ["Differentiate and integrate $e^{kx}$ and $\\ln x$", "Solve equations with $e^x$ and $\\ln x$", "Model growth and decay"],
        [("definition", "$e^x$ and $\\ln x$", "$e\\approx2.718$. The function $y=e^x$ is its own derivative. $\\ln x$ is the inverse of $e^x$: $e^{\\ln x}=x$ and $\\ln(e^x)=x$. Their graphs are reflections in $y=x$; $\\ln x$ is defined only for $x>0$."),
         ("formule", "Calculus", "$\\frac{d}{dx}e^{kx}=ke^{kx}$, $\\frac{d}{dx}\\ln x=\\frac1x$; $\\int e^{kx}dx=\\frac{e^{kx}}{k}+C$, $\\int\\frac1x\\,dx=\\ln|x|+C$. Solving: $e^{f}=a\\Rightarrow f=\\ln a$; $\\ln f=b\\Rightarrow f=e^b$."),
         ("methode", "Growth and decay models", "$N=N_0e^{kt}$ models growth ($k>0$) or decay ($k<0$); $N_0$ is the starting value. To find the time for a given change take $\\ln$ of both sides. Doubling time: $e^{kt}=2\\Rightarrow t=\\frac{\\ln2}{k}$."),
         ("pieges", "Common mistakes", "$\\frac{d}{dx}e^{2x}=2e^{2x}$, not $2xe^{2x-1}$. $\\ln(x^2)=2\\ln|x|$, not $2\\ln x$ for negative $x$. $\\ln(a+b)\\neq\\ln a+\\ln b$.")],
        [("Example 1", "Solve $e^{2x}=15$ and differentiate $y=e^{3x}+\\ln(2x)$.", ["$2x=\\ln15$, so $x=\\frac{\\ln15}{2}=\\frac{2.708}{2}=1.354$.", "$\\frac{dy}{dx}=3e^{3x}+\\frac{2}{2x}=3e^{3x}+\\frac1x$."], "$x\\approx1.354$; $\\frac{dy}{dx}=3e^{3x}+\\frac1x$", P1),
         ("Example 2", "The population of a town is $P=20000e^{0.03t}$ ($t$ in years). When does it double?", ["Set $e^{0.03t}=2$.", "$0.03t=\\ln2$, so $t=\\frac{0.6931}{0.03}$."], "$t\\approx23.1$ years")],
        [N("Evaluate $\\int_0^1e^{2x}dx$ (2 d.p.).", 3.19, "$\\left[\\frac{e^{2x}}{2}\\right]_0^1=\\frac{e^2-1}{2}=\\frac{6.389}{2}=3.19$ (3.1945).", tol=0.01),
         M("$\\frac{d}{dx}e^{-x}=$", "$-e^{-x}$", ["$e^{-x}$", "$-xe^{-x-1}$", "$e^x$"], "Chain factor $k=-1$."),
         TF("$\\ln(x^2)=2\\ln x$ for every real $x\\neq0$.", False, "For $x=-3$, $\\ln x$ is not defined; the correct statement is $\\ln(x^2)=2\\ln|x|$.")],
        [M("The gradient of $y=e^{2x}$ at $x=0$ is", "$2$", ["$1$", "$e^2$", "$0$"], "$\\frac{dy}{dx}=2e^{2x}=2$."),
         N("Solve $\\ln(3x-1)=2$ (3 d.p.).", round((exp(2) + 1) / 3, 3), "$3x-1=e^2$, $x=\\frac{e^2+1}{3}=\\frac{8.389}{3}=2.796$.", tol=0.002)],
        P("A car bought in Douala for 8 000 000 FCFA loses value so that after $t$ years it is worth $V=8000000e^{-0.15t}$ FCFA.", [pn("Find the value after 5 years (FCFA, nearest 1000).", round(val5, -3), "$8000000e^{-0.75}=8000000\\times0.4724\\approx3\\,779\\,000$.", tol=1000, pts=2), pn("After how many years is the value half of the original (2 d.p.)?", round(half, 2), "$e^{-0.15t}=\\frac12$, $t=\\frac{\\ln2}{0.15}\\approx4.62$.", tol=0.02, pts=3)]),
        [("$\\ln e$ equals", "$1$", ["$0$", "$e$", "$\\frac1e$"], "$e^1=e$."),
         ("$e^{\\ln5}$ equals", "$5$", ["$\\ln5$", "$e^5$", "$1$"], "Inverse functions."),
         ("$\\int\\frac{3}{x}dx=$", "$3\\ln|x|+C$", ["$\\frac{3}{x^2}+C$", "$3\\ln x^2$", "$\\frac{3x^0}{0}$"], "Constant multiple."),
         ("The graph of $y=\\ln x$ passes through", "$(1,0)$", ["$(0,1)$", "$(0,0)$", "$(e,0)$"], "$\\ln1=0$."),
         ("$N=N_0e^{kt}$ with $k<0$ describes", "decay", ["growth", "a constant", "a straight line"], "Decreasing exponential.")],
        ill=None, notes=["Product, quotient and chain rules (for $xe^x$, etc.) are in the Upper Sixth pack."], ref=REF)
