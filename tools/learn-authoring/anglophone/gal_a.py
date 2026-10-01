import math
from math import pi, sin, cos, tan, exp, log, sqrt, atan, degrees, radians
from _maths import *
REF = "Cameroon GCE Board — Advanced Level Mathematics, Pure Mathematics (Upper Sixth part; to be checked against the official syllabus)"
def D(f, x, h=1e-6): return (f(x + h) - f(x - h)) / (2 * h)
def I(f, a, b, n=20000):
    h = (b - a) / n
    return h * (f(a) / 2 + sum(f(a + i * h) for i in range(1, n)) + f(b) / 2)
def close(a, b, t=1e-6): assert abs(a - b) < t, (a, b)

def build(p):
    ch = p.chapter("differentiation", "Further differentiation", REF + " — differentiation")
    # --------------------------------------------------------- product / quotient
    close(D(lambda x: x * x * exp(3 * x), 0.7), (2 * .7 + 3 * .49) * exp(2.1), 1e-4)
    qf = lambda x: (2 * x + 1) / (x * x + 1); close(D(qf, 1), -.5, 1e-6)
    close(D(lambda x: x * exp(x), 0), 1, 1e-6)
    close(D(lambda x: x * exp(-x), 1), 0, 1e-6); close(1 * exp(-1), .36788, 1e-4)
    close(D(lambda x: x * log(x), exp(-1)), 0, 1e-6)
    lesson(ch, "diff-product-quotient", "Product and quotient rules",
        ["Differentiate products with the product rule", "Differentiate quotients with the quotient rule", "Find stationary points of such functions"],
        [("formule", "Product rule", "If $y=uv$ then $\\frac{dy}{dx}=u\\frac{dv}{dx}+v\\frac{du}{dx}$. Use it when two functions of $x$ are multiplied, for example $x^2e^{3x}$, $x\\ln x$, $x\\sin x$."),
         ("formule", "Quotient rule", "If $y=\\frac{u}{v}$ then $\\frac{dy}{dx}=\\frac{v\\frac{du}{dx}-u\\frac{dv}{dx}}{v^2}$. The order in the numerator matters: bottom times derivative of top, minus top times derivative of bottom."),
         ("methode", "Method", "1. Name $u$ and $v$ and differentiate each separately. 2. Substitute into the formula. 3. Factorise the result (common factors such as $e^{x}$) so that setting $\\frac{dy}{dx}=0$ is easy. Check with a number if possible."),
         ("pieges", "Common mistakes", "- $\\frac{d}{dx}(uv)\\neq\\frac{du}{dx}\\times\\frac{dv}{dx}$.\n- Reversing the numerator of the quotient rule gives the wrong sign.\n- Forgetting that $e^x$ is never zero when solving $\\frac{dy}{dx}=0$.")],
        [("Example 1", "Differentiate $y=x^2e^{3x}$.", ["Let $u=x^2$, $v=e^{3x}$: $u'=2x$, $v'=3e^{3x}$.", "$\\frac{dy}{dx}=x^2\\cdot3e^{3x}+e^{3x}\\cdot2x=xe^{3x}(3x+2)$."], "$\\frac{dy}{dx}=xe^{3x}(3x+2)$"),
         ("Example 2", "Find the gradient of $y=\\frac{2x+1}{x^2+1}$ at $x=1$.", ["$u=2x+1$, $v=x^2+1$: $\\frac{dy}{dx}=\\frac{(x^2+1)(2)-(2x+1)(2x)}{(x^2+1)^2}=\\frac{-2x^2-2x+2}{(x^2+1)^2}$.", "At $x=1$: $\\frac{-2-2+2}{4}=-\\frac12$."], "Gradient $-\\frac12$")],
        [N("Find $\\frac{d}{dx}(xe^x)$ at $x=0$.", 1, "$e^x+xe^x=e^x(1+x)$, which is 1 at $x=0$."),
         M("$\\frac{d}{dx}(x\\sin x)=$", "$\\sin x+x\\cos x$", ["$\\cos x$", "$x\\cos x$", "$\\sin x-x\\cos x$"], "Product rule with $u=x$, $v=\\sin x$."),
         TF("$\\frac{d}{dx}(uv)=\\frac{du}{dx}\\cdot\\frac{dv}{dx}$.", False, "False: $(x\\cdot x)'=2x$, but $1\\times1=1$.")],
        [M("The curve $y=xe^{-x}$ has a stationary point at", "$x=1$", ["$x=0$", "$x=-1$", "$x=e$"], "$\\frac{dy}{dx}=e^{-x}(1-x)=0$ gives $x=1$ and $y=e^{-1}$."),
         O("Use the quotient rule to show that $\\frac{d}{dx}(\\tan x)=\\frac{1}{\\cos^2x}$.", "$\\tan x=\\frac{\\sin x}{\\cos x}$. $\\frac{dy}{dx}=\\frac{\\cos x\\cdot\\cos x-\\sin x\\cdot(-\\sin x)}{\\cos^2x}=\\frac{\\cos^2x+\\sin^2x}{\\cos^2x}=\\frac{1}{\\cos^2x}$.", ["Correct $u,v$ and derivatives (1)", "Quotient rule applied (1)", "Uses $\\sin^2+\\cos^2=1$ (1)"])],
        P("The curve $C$ has equation $y=x\\ln x$ for $x>0$.", [pn("Find the $x$-coordinate of the stationary point (3 d.p.).", round(exp(-1), 3), "$\\frac{dy}{dx}=\\ln x+1=0$ gives $x=e^{-1}\\approx0.368$.", tol=0.001, pts=2), pn("Find the $y$-coordinate there (3 d.p.).", round(-exp(-1), 3), "$y=e^{-1}\\ln(e^{-1})=-e^{-1}\\approx-0.368$.", tol=0.001, pts=1), pm("The stationary point is", "a minimum", ["a maximum", "a point of inflexion", "not classified"], "$\\frac{d^2y}{dx^2}=\\frac1x>0$.", pts=1)]),
        [("$\\frac{d}{dx}(x^2\\ln x)=$", "$2x\\ln x+x$", ["$\\frac{2x}{x}$", "$2x\\ln x$", "$x\\ln x+x^2$"], "$u=x^2$, $v=\\ln x$."),
         ("In the quotient rule the denominator becomes", "$v^2$", ["$v$", "$u^2$", "$uv$"], "Square of the bottom."),
         ("$\\frac{d}{dx}\\left(\\frac{x}{x+1}\\right)=$", "$\\frac{1}{(x+1)^2}$", ["$\\frac{1}{x+1}$", "$\\frac{2x+1}{(x+1)^2}$", "$1$"], "$\\frac{(x+1)-x}{(x+1)^2}$."),
         ("$e^x\\neq0$ means $xe^x(2+x)=0$ gives", "$x=0$ or $x=-2$", ["$x=0$ only", "$x=-2$ only", "no solution"], "Other factors vanish."),
         ("The derivative of $x\\cos x$ is", "$\\cos x-x\\sin x$", ["$-\\sin x$", "$\\cos x+x\\sin x$", "$-x\\sin x$"], "Product rule.")],
        ill=(plot(0, 5, -0.1, 0.5, curves=[("x*exp(-x)", "blue", None)], points=[(1, 0.3679, "max (1, 0.37)", "red")], xlabel="x"),
             "The curve $y=xe^{-x}$ has its maximum at $(1,e^{-1})$, found with the product rule.", "A curve rising from the origin to a peak at x=1 then slowly decreasing to 0."),
        notes=[], ref=REF)

    # --------------------------------------------------------- chain rule & trig
    close(D(lambda x: (3 * x * x + 1) ** 5, 1), 30 * 1 * 4 ** 4, 1e-2)
    close(D(lambda x: exp(x) * sin(2 * x), .4), exp(.4) * (sin(.8) + 2 * cos(.8)), 1e-5)
    close(D(lambda x: (2 * x + 1) ** 3, 1), 54, 1e-4); close(D(lambda x: sqrt(4 * x + 1), 2), 2 / 3, 1e-6)
    close(D(lambda x: exp(-2 * x) * cos(x), 0), -2, 1e-6)
    lesson(ch, "diff-chain-trig", "Chain rule and trigonometric functions",
        ["Use the chain rule", "Differentiate $\\sin$, $\\cos$ and $\\tan$ (x in radians)", "Combine the chain, product and quotient rules"],
        [("formule", "Chain rule", "If $y=f(u)$ and $u=g(x)$ then $\\frac{dy}{dx}=\\frac{dy}{du}\\times\\frac{du}{dx}$. In practice: differentiate the outer function, keep the inside, then multiply by the derivative of the inside. E.g. $\\frac{d}{dx}(3x^2+1)^5=5(3x^2+1)^4\\times6x$."),
         ("formule", "Trigonometric derivatives (radians)", "$\\frac{d}{dx}\\sin x=\\cos x$, $\\frac{d}{dx}\\cos x=-\\sin x$, $\\frac{d}{dx}\\tan x=\\frac{1}{\\cos^2x}$. With a constant: $\\frac{d}{dx}\\sin kx=k\\cos kx$, $\\frac{d}{dx}\\cos kx=-k\\sin kx$. Also $\\frac{d}{dx}e^{f(x)}=f'(x)e^{f(x)}$ and $\\frac{d}{dx}\\ln f(x)=\\frac{f'(x)}{f(x)}$."),
         ("pieges", "Common mistakes", "- Forgetting the derivative of the inside: $\\frac{d}{dx}\\sin x^2=2x\\cos x^2$, not $\\cos x^2$.\n- These formulae are only valid when $x$ is in RADIANS.\n- $\\frac{d}{dx}\\cos x$ has a minus sign.")],
        [("Example 1", "Differentiate $y=(3x^2+1)^5$.", ["Outer: $u^5$ gives $5u^4$. Inner: $u=3x^2+1$ gives $6x$.", "$\\frac{dy}{dx}=5(3x^2+1)^4\\times6x=30x(3x^2+1)^4$."], "$30x(3x^2+1)^4$"),
         ("Example 2", "Differentiate $y=e^x\\sin2x$.", ["Product rule with $u=e^x$, $v=\\sin2x$ ($v'=2\\cos2x$ by the chain rule).", "$\\frac{dy}{dx}=e^x\\cdot2\\cos2x+\\sin2x\\cdot e^x=e^x(\\sin2x+2\\cos2x)$."], "$e^x(\\sin2x+2\\cos2x)$")],
        [N("Find the gradient of $y=(2x+1)^3$ at $x=1$.", 54, "$3(2x+1)^2\\times2=6\\times9$."),
         M("$\\frac{d}{dx}\\cos3x=$", "$-3\\sin3x$", ["$3\\sin3x$", "$-\\sin3x$", "$-3\\cos3x$"], "Chain factor 3 and the minus sign."),
         TF("$\\frac{d}{dx}\\sin x^2=\\cos x^2$.", False, "The chain rule gives $2x\\cos x^2$.")],
        [M("The gradient of $y=\\sqrt{4x+1}$ at $x=2$ is", "$\\frac23$", ["$\\frac13$", "$\\frac29$", "$2$"], "$\\frac12(4x+1)^{-1/2}\\times4=\\frac{2}{3}$ at $x=2$."),
         O("Find the equation of the tangent to $y=\\tan x$ at $x=\\frac{\\pi}{4}$.", "$y=\\tan\\frac{\\pi}{4}=1$ and $\\frac{dy}{dx}=\\frac{1}{\\cos^2x}=\\frac{1}{1/2}=2$ at $x=\\frac\\pi4$. Tangent: $y-1=2\\left(x-\\frac{\\pi}{4}\\right)$.", ["Point $(\\frac\\pi4,1)$ (1)", "Gradient 2 (1)", "Equation (1)"])],
        P("The curve $C$ is $y=e^{-2x}\\cos x$.", [pn("Find the gradient of $C$ at $x=0$.", -2, "$\\frac{dy}{dx}=-2e^{-2x}\\cos x-e^{-2x}\\sin x$; at $x=0$ this is $-2$.", pts=2), pn("The tangent at $(0,1)$ meets the x-axis at $(k,0)$. Find $k$.", 0.5, "Tangent $y=1-2x$; set $y=0$.", tol=0.001, pts=2)]),
        [("$\\frac{d}{dx}\\ln(3x)=$", "$\\frac1x$", ["$\\frac{1}{3x}$", "$\\frac{3}{x}$", "$3\\ln x$"], "$\\frac{3}{3x}$."),
         ("$\\frac{d}{dx}e^{x^2}=$", "$2xe^{x^2}$", ["$e^{x^2}$", "$x^2e^{x^2-1}$", "$2e^{x^2}$"], "Chain rule."),
         ("$\\frac{d}{dx}\\sin(2x+1)=$", "$2\\cos(2x+1)$", ["$\\cos(2x+1)$", "$2\\cos x$", "$-2\\cos(2x+1)$"], "Chain factor 2."),
         ("$\\frac{d}{dx}(x^2+1)^{-1}=$", "$-2x(x^2+1)^{-2}$", ["$-(x^2+1)^{-2}$", "$2x(x^2+1)^{-2}$", "$-2x(x^2+1)^{-1}$"], "Outer power rule, inner $2x$."),
         ("In $\\frac{d}{dx}\\sin x=\\cos x$, $x$ must be in", "radians", ["degrees", "gradians", "any unit"], "Limit $\\frac{\\sin x}{x}\\to1$ needs radians.")],
        ill=(plot(0, 6.4, -1.3, 1.3, curves=[("sin(x)", "blue", "sin x"), ("cos(x)", "orange", "cos x")], xlabel="x (radians)"),
             "$y=\\sin x$ (blue) and its gradient function $y=\\cos x$ (orange): where $\\sin x$ peaks, $\\cos x=0$.", "Sine and cosine curves over one period, cosine is the gradient of sine."),
        notes=["Derivatives of sec, cosec, cot, inverse trig functions not covered; check the syllabus."], ref=REF)

    # --------------------------------------------------------- implicit & parametric
    close(-3 / 4, -3 / 4); assert 3 * 3 + 4 * 4 - 25 == 0 and 1 + 2 + 4 == 7 and -(2 + 2) / (1 + 4) == -.8
    close(degrees(0), 0); close(-(2 / 3) / tan(pi / 3), -.3849, 1e-3)
    ell = Axes(-4, 4, -3, 3, unit=40, xticks=[-3, 3], yticks=[-2, 2]).param(lambda t: 3 * cos(t), lambda t: 2 * sin(t), 0, 2 * pi, n=100).pt(1.5, 2 * sin(pi / 3), "t = π/3", dx=14, dy=-8, anchor="start")
    lesson(ch, "implicit-parametric", "Implicit and parametric differentiation",
        ["Differentiate implicit functions", "Find $\\frac{dy}{dx}$ for parametric curves", "Find tangents and normals in these cases"],
        [("methode", "Implicit differentiation", "When $y$ cannot be isolated, differentiate every term with respect to $x$. A term in $y$ needs the chain rule: $\\frac{d}{dx}(y^2)=2y\\frac{dy}{dx}$ and $\\frac{d}{dx}(xy)=y+x\\frac{dy}{dx}$. Then collect the $\\frac{dy}{dx}$ terms and solve."),
         ("formule", "Parametric curves", "If $x=f(t)$ and $y=g(t)$ then $\\frac{dy}{dx}=\\frac{dy/dt}{dx/dt}$. The point at a given $t$ is found by substituting into both. Eliminate $t$ to get the Cartesian equation when possible, e.g. $x=3\\cos t$, $y=2\\sin t$ gives $\\frac{x^2}{9}+\\frac{y^2}{4}=1$."),
         ("pieges", "Common mistakes", "Forgetting $\\frac{dy}{dx}$ when differentiating a $y$ term. For parametric curves $\\frac{dy}{dx}$ is NOT $\\frac{dx}{dy}$ upside down by accident: divide $\\frac{dy}{dt}$ by $\\frac{dx}{dt}$, in that order.")],
        [("Example 1", "Find the gradient of $x^2+xy+y^2=7$ at $(1,2)$, after checking the point lies on the curve.", ["$1+2+4=7$, so the point is on the curve.", "Differentiate: $2x+y+x\\frac{dy}{dx}+2y\\frac{dy}{dx}=0$, so $\\frac{dy}{dx}=-\\frac{2x+y}{x+2y}$.", "At $(1,2)$: $-\\frac{4}{5}$."], "Gradient $-\\frac45$"),
         ("Example 2", "A curve has $x=t^2$, $y=2t$. Find $\\frac{dy}{dx}$ and the tangent at $t=2$.", ["$\\frac{dy}{dt}=2$, $\\frac{dx}{dt}=2t$, so $\\frac{dy}{dx}=\\frac1t$.", "At $t=2$: point $(4,4)$, gradient $\\frac12$; tangent $y-4=\\frac12(x-4)$, i.e. $y=\\frac{x}{2}+2$."], "$\\frac{dy}{dx}=\\frac{1}{t}$; tangent $y=\\frac{x}{2}+2$", ell)],
        [N("$x=3t$, $y=t^2$. Find $\\frac{dy}{dx}$ at $t=2$ (3 d.p.).", 1.333, "$\\frac{dy}{dx}=\\frac{2t}{3}=\\frac43$.", tol=0.001),
         M("For $x^2+y^2=25$, $\\frac{dy}{dx}=$", "$-\\frac{x}{y}$", ["$\\frac{x}{y}$", "$-\\frac{y}{x}$", "$-2x$"], "$2x+2y\\frac{dy}{dx}=0$."),
         TF("Implicit differentiation can only be used when $y$ can be written explicitly in terms of $x$.", False, "It is used precisely when $y$ cannot (or need not) be isolated.")],
        [M("$\\frac{d}{dx}(y^2)=$", "$2y\\frac{dy}{dx}$", ["$2y$", "$2x$", "$y\\frac{dy}{dx}$"], "Chain rule."),
         O("A curve has $x=t^2$, $y=t^3-3t$. Find the points where the tangent is horizontal.", "$\\frac{dy}{dt}=3t^2-3=0$ gives $t=\\pm1$ (and $\\frac{dx}{dt}=2t\\neq0$ there). At $t=1$: $(1,-2)$; at $t=-1$: $(1,2)$.", ["$\\frac{dy}{dt}=0$ (1)", "Both $t$ values (1)", "Both points (1)"])],
        P("A curve has parametric equations $x=3\\cos t$, $y=2\\sin t$.", [pn("Find $\\frac{dy}{dx}$ at $t=\\frac\\pi3$ (3 d.p.).", round(-(2 / 3) / tan(pi / 3), 3), "$\\frac{dy}{dx}=\\frac{2\\cos t}{-3\\sin t}=-\\frac23\\times\\frac{\\cos t}{\\sin t}$; at $\\frac\\pi3$: $-\\frac23\\times\\frac{1}{\\sqrt3}=-0.385$.", tol=0.001, pts=3), pm("The Cartesian equation of the curve is", "$\\frac{x^2}{9}+\\frac{y^2}{4}=1$", ["$x^2+y^2=1$", "$\\frac{x^2}{4}+\\frac{y^2}{9}=1$", "$3x+2y=1$"], "$\\cos^2t+\\sin^2t=1$ with $\\cos t=\\frac x3$, $\\sin t=\\frac y2$.", pts=1)]),
        [("$\\frac{d}{dx}(xy)=$", "$y+x\\frac{dy}{dx}$", ["$y$", "$x\\frac{dy}{dx}$", "$\\frac{dy}{dx}$"], "Product rule."),
         ("$x=t^2$, $y=t^3$: $\\frac{dy}{dx}=$", "$\\frac{3t}{2}$", ["$\\frac{2}{3t}$", "$3t^2$", "$\\frac{3t^2}{2t^2}$"], "$\\frac{3t^2}{2t}$."),
         ("In $x^2+y^2=25$ at $(3,4)$ the gradient is", "$-\\frac34$", ["$\\frac34$", "$-\\frac43$", "$\\frac43$"], "$-\\frac{x}{y}$."),
         ("A parametric curve has a vertical tangent where", "$\\frac{dx}{dt}=0$ (and $\\frac{dy}{dt}\\neq0$)", ["$\\frac{dy}{dt}=0$", "$t=0$", "$\\frac{dy}{dx}=0$"], "Gradient undefined."),
         ("Eliminating $t$ from $x=t+1$, $y=2t$ gives", "$y=2x-2$", ["$y=2x+2$", "$y=x-1$", "$y=\\frac{x}{2}$"], "$t=x-1$.")],
        ill=None, notes=[], ref=REF)

    # --------------------------------------------------------- trig compound
    close(sin(radians(75)), (sqrt(6) + sqrt(2)) / 4)
    al = degrees(atan(4 / 3)); close(al, 53.1301, 1e-3)
    s1 = degrees(math.asin(.4)); xs = [(s1 - al) % 360, (180 - s1 - al) % 360]; xs.sort()
    for x in xs: close(3 * sin(radians(x)) + 4 * cos(radians(x)), 2, 1e-9)
    assert abs(xs[0] - 103.29) < .01 and abs(xs[1] - 330.45) < .01
    assert abs(1 - 2 * .36 - .28) < 1e-12
    sols = [x for x in range(0, 361) if abs(cos(radians(2 * x)) - sin(radians(x))) < 1e-9]; assert sols == [30, 150, 270] and sum(sols) == 450
    ill = plot(0, 6.4, -6, 6, curves=[("3sin(x)+4cos(x)", "blue", None)], segments=[(0, 2, 6.4, 2, "grey", True, None)], points=[(radians(xs[0]), 2, None, "red"), (radians(xs[1]), 2, None, "red")], xlabel="x (radians)")
    lesson(ch, "trig-formulae", "Compound angles, double angles and the R-form",
        ["Use addition and double-angle formulae", "Write $a\\sin x+b\\cos x$ as $R\\sin(x+\\alpha)$", "Solve equations and prove identities"],
        [("formule", "Addition formulae", "$\\sin(A\\pm B)=\\sin A\\cos B\\pm\\cos A\\sin B$; $\\cos(A\\pm B)=\\cos A\\cos B\\mp\\sin A\\sin B$; $\\tan(A\\pm B)=\\frac{\\tan A\\pm\\tan B}{1\\mp\\tan A\\tan B}$. Note the sign flip in the cosine formula."),
         ("formule", "Double-angle formulae", "$\\sin2A=2\\sin A\\cos A$; $\\cos2A=\\cos^2A-\\sin^2A=2\\cos^2A-1=1-2\\sin^2A$; $\\tan2A=\\frac{2\\tan A}{1-\\tan^2A}$. Choose the form of $\\cos2A$ that leaves a single trig function."),
         ("methode", "The R-form", "$a\\sin x+b\\cos x=R\\sin(x+\\alpha)$ with $R=\\sqrt{a^2+b^2}$, $\\tan\\alpha=\\frac ba$ ($a,b>0$, $\\alpha$ acute). The maximum value is $R$ and the minimum $-R$. To solve $a\\sin x+b\\cos x=c$: solve $\\sin(x+\\alpha)=\\frac cR$, then subtract $\\alpha$."),
         ("pieges", "Common mistakes", "$\\sin(A+B)\\neq\\sin A+\\sin B$. $\\cos2A\\neq2\\cos A$. When solving $\\sin(x+\\alpha)=k$ list solutions for $x+\\alpha$ over the SHIFTED interval before subtracting $\\alpha$.")],
        [("Example 1", "Find the exact value of $\\sin75\\degree$.", ["$\\sin75\\degree=\\sin(45\\degree+30\\degree)=\\sin45\\cos30+\\cos45\\sin30$.", "$=\\frac{\\sqrt2}{2}\\cdot\\frac{\\sqrt3}{2}+\\frac{\\sqrt2}{2}\\cdot\\frac12=\\frac{\\sqrt6+\\sqrt2}{4}$."], "$\\frac{\\sqrt6+\\sqrt2}{4}\\approx0.966$"),
         ("Example 2", "Solve $3\\sin x+4\\cos x=2$ for $0\\degree\\leq x\\leq360\\degree$.", ["$R=5$, $\\tan\\alpha=\\frac43$, $\\alpha=53.13\\degree$: $5\\sin(x+53.13\\degree)=2$.", "$x+53.13\\degree=23.58\\degree$ (too small: add $360\\degree$ later) or $156.42\\degree$.", "$x=156.42-53.13=103.29\\degree$ or $x=23.58+360-53.13=330.45\\degree$."], "$x\\approx103.3\\degree$ or $330.5\\degree$", ill)],
        [N("If $\\sin x=0.6$, find $\\cos2x$.", .28, "$\\cos2x=1-2\\sin^2x=1-2(0.36)=0.28$.", tol=0.001),
         M("$2\\sin15\\degree\\cos15\\degree=$", "$\\frac12$", ["$\\frac{\\sqrt3}{2}$", "$\\sin15\\degree$", "$1$"], "$\\sin30\\degree$."),
         TF("$\\sin(A+B)=\\sin A+\\sin B$.", False, "Take $A=B=30\\degree$: LHS $=\\frac{\\sqrt3}{2}$, RHS $=1$.")],
        [M("The maximum value of $3\\sin x+4\\cos x$ is", "$5$", ["$7$", "$4$", "$\\sqrt7$"], "$R=\\sqrt{9+16}$."),
         O("Prove that $\\frac{\\sin2x}{1+\\cos2x}=\\tan x$.", "$\\sin2x=2\\sin x\\cos x$ and $1+\\cos2x=1+2\\cos^2x-1=2\\cos^2x$. So the fraction is $\\frac{2\\sin x\\cos x}{2\\cos^2x}=\\frac{\\sin x}{\\cos x}=\\tan x$.", ["Double-angle numerator (1)", "$1+\\cos2x=2\\cos^2x$ (1)", "Cancels to $\\tan x$ (1)"])],
        P("Solve $\\cos2x=\\sin x$ for $0\\degree\\leq x\\leq360\\degree$.", [pm("The equation can be written as", "$2\\sin^2x+\\sin x-1=0$", ["$2\\sin^2x-\\sin x+1=0$", "$\\sin^2x+\\sin x=0$", "$2\\cos^2x+\\sin x=0$"], "$1-2\\sin^2x=\\sin x$.", pts=1), pn("How many solutions are there?", 3, "$(2s-1)(s+1)=0$: $\\sin x=\\frac12$ gives $30\\degree,150\\degree$; $\\sin x=-1$ gives $270\\degree$.", pts=1), pn("Find the sum of the solutions (degrees).", 450, "$30+150+270$.", pts=2)]),
        [("$\\cos(A+B)=$", "$\\cos A\\cos B-\\sin A\\sin B$", ["$\\cos A\\cos B+\\sin A\\sin B$", "$\\cos A+\\cos B$", "$\\sin A\\cos B-\\cos A\\sin B$"], "Sign flip."),
         ("$\\sin2A=$", "$2\\sin A\\cos A$", ["$2\\sin A$", "$\\sin^2A$", "$\\sin A+\\cos A$"], "Double angle."),
         ("$\\cos2A$ in terms of $\\cos A$ only is", "$2\\cos^2A-1$", ["$1-2\\cos^2A$", "$2\\cos A$", "$\\cos^2A+1$"], "From $\\cos^2-\\sin^2$."),
         ("For $4\\sin x+3\\cos x=R\\sin(x+\\alpha)$, $R=$", "$5$", ["$7$", "$1$", "$12$"], "$\\sqrt{16+9}$."),
         ("$\\tan(A+B)$ equals", "$\\frac{\\tan A+\\tan B}{1-\\tan A\\tan B}$", ["$\\tan A+\\tan B$", "$\\frac{\\tan A-\\tan B}{1+\\tan A\\tan B}$", "$\\frac{\\tan A\\tan B}{1-\\tan A}$"], "Standard formula.")],
        ill=None, notes=["Half-angle (t) formulae and factor formulae not covered."], ref=REF)

    # --------------------------------------------------------- INTEGRATION
    ch = p.chapter("integration", "Integration techniques", REF + " — integration")
    close(I(lambda x: x * sqrt(1 + x * x), 0, 1), (2 * sqrt(2) - 1) / 3, 1e-6)
    close(I(lambda x: cos(x) * sin(x) ** 2, 0, pi / 2), 1 / 3); close(I(lambda x: x / (x * x + 1), 0, 2), .5 * log(5), 1e-6)
    close(I(lambda x: x * (1 - x * x) ** 3, 0, 1), .125)
    sh = Axes(-0.3, 1.5, -0.2, 2.2, w=400, h=240, xticks=[0.5, 1], yticks=[1, 2])
    sh.poly([(0, 0)] + [(i / 20, (i / 20) * sqrt(1 + (i / 20) ** 2)) for i in range(21)] + [(1, 0)], fill="yellow", color="orange", width=1)
    sh.curve(lambda x: x * sqrt(1 + x * x), 0, 1.4, color="blue").text(.55, .25, "area = 0.609", 12)
    lesson(ch, "integration-substitution", "Integration by substitution",
        ["Recognise when a substitution is needed", "Change variable and limits", "Integrate with the reverse chain rule"],
        [("methode", "Substitution", "1. Choose $u$ (often the inside of a bracket or root, or the denominator). 2. Find $\\frac{du}{dx}$ and replace $dx$ (and every $x$) by $u$-terms. 3. Integrate in $u$. 4. Return to $x$. For a definite integral change the limits to $u$-values and do NOT return to $x$."),
         ("propriete", "Reverse chain rule shortcuts", "$\\int f'(x)\\,[f(x)]^n\\,dx=\\frac{[f(x)]^{n+1}}{n+1}+C$ and $\\int\\frac{f'(x)}{f(x)}dx=\\ln|f(x)|+C$. For linear inside: $\\int(ax+b)^n\\,dx=\\frac{(ax+b)^{n+1}}{a(n+1)}+C$, $\\int\\frac{1}{ax+b}dx=\\frac1a\\ln|ax+b|+C$."),
         ("pieges", "Common mistakes", "- Forgetting the factor $\\frac1a$ in $\\int(ax+b)^ndx$.\n- Changing the variable but keeping the old limits.\n- Leaving some $x$ in an integral that should be entirely in $u$.")],
        [("Example 1", "Find $\\int2x(x^2+1)^3dx$.", ["Let $u=x^2+1$, so $du=2x\\,dx$.", "$\\int u^3du=\\frac{u^4}{4}+C$; substitute back."], "$\\frac{(x^2+1)^4}{4}+C$"),
         ("Example 2", "Evaluate $\\int_0^1x\\sqrt{1+x^2}\\,dx$.", ["$u=1+x^2$, $du=2x\\,dx$; limits: $x=0\\to u=1$, $x=1\\to u=2$.", "$\\frac12\\int_1^2u^{1/2}du=\\frac12\\cdot\\frac23\\left[u^{3/2}\\right]_1^2=\\frac13(2\\sqrt2-1)$."], "$\\frac{2\\sqrt2-1}{3}\\approx0.609$", sh.fig())],
        [N("Evaluate $\\int_0^{\\pi/2}\\cos x\\sin^2x\\,dx$ (3 d.p.).", .333, "$u=\\sin x$: $\\int_0^1u^2du=\\frac13$.", tol=0.001),
         M("$\\int xe^{x^2}dx=$", "$\\frac12e^{x^2}+C$", ["$e^{x^2}+C$", "$2e^{x^2}+C$", "$xe^{x^2}+C$"], "$u=x^2$, $du=2x\\,dx$."),
         TF("$\\int(2x+1)^4dx=\\frac{(2x+1)^5}{5}+C$.", False, "The inside has derivative 2, so the result is $\\frac{(2x+1)^5}{10}+C$.")],
        [M("$\\int\\frac{1}{2x+3}dx=$", "$\\frac12\\ln|2x+3|+C$", ["$\\ln|2x+3|+C$", "$2\\ln|2x+3|+C$", "$-\\frac{1}{(2x+3)^2}+C$"], "Reverse chain rule with factor $\\frac12$."),
         N("Evaluate $\\int_0^2\\frac{x}{x^2+1}dx$ (3 d.p.).", round(.5 * log(5), 3), "$\\frac12\\left[\\ln(x^2+1)\\right]_0^2=\\frac12\\ln5=0.805$.", tol=0.001)],
        P("Answer the following.", [pn("Evaluate $\\int_0^1x(1-x^2)^3dx$.", .125, "$u=1-x^2$, $du=-2x\\,dx$: $-\\frac12\\int_1^0u^3du=\\frac12\\cdot\\frac14=\\frac18$.", tol=0.001, pts=3), pm("$\\int\\tan x\\,dx=$", "$-\\ln|\\cos x|+C$", ["$\\ln|\\cos x|+C$", "$\\frac{1}{\\cos^2x}+C$", "$\\ln|\\sin x|+C$"], "$\\tan x=\\frac{\\sin x}{\\cos x}$ and $u=\\cos x$.", pts=1)]),
        [("The substitution for $\\int x\\sqrt{x^2+4}\\,dx$ is", "$u=x^2+4$", ["$u=x$", "$u=\\sqrt x$", "$u=4$"], "Inside the root."),
         ("$\\int\\cos2x\\,dx=$", "$\\frac12\\sin2x+C$", ["$\\sin2x+C$", "$2\\sin2x+C$", "$-\\frac12\\sin2x+C$"], "Factor $\\frac12$."),
         ("$\\int\\frac{2x}{x^2+5}dx=$", "$\\ln(x^2+5)+C$", ["$\\frac{1}{x^2+5}+C$", "$2\\ln x+C$", "$\\frac{x^2}{x^2+5}+C$"], "$f'/f$."),
         ("For a definite integral with substitution the limits", "are changed to $u$-values", ["stay the same", "are swapped", "become 0 and 1"], "Or return to $x$."),
         ("$\\int e^{3x}dx=$", "$\\frac13e^{3x}+C$", ["$3e^{3x}+C$", "$e^{3x}+C$", "$\\frac{e^{3x}}{x}+C$"], "Factor $\\frac13$.")],
        ill=None, notes=["Trigonometric substitutions are not included."], ref=REF)

    # by parts
    close(I(lambda x: x * exp(x), 0, 1), 1); close(I(lambda x: x * log(x), 1, exp(1)), (exp(2) + 1) / 4, 1e-6)
    close(I(lambda x: x * sin(x), 0, pi / 2), 1); close(I(lambda x: x * exp(-x), 0, 1), 1 - 2 / exp(1), 1e-6)
    pf = plot(0, 2.2, -0.2, 3.5, curves=[("x*exp(x)", "blue", None)], points=[(1, exp(1), "(1, e)", "red")], xlabel="x")
    lesson(ch, "integration-by-parts", "Integration by parts",
        ["Apply $\\int u\\,dv=uv-\\int v\\,du$", "Choose $u$ sensibly (LATE rule)", "Evaluate definite integrals by parts"],
        [("formule", "Integration by parts", "$\\int u\\frac{dv}{dx}dx=uv-\\int v\\frac{du}{dx}dx$. Definite: $\\int_a^bu\\,v'\\,dx=[uv]_a^b-\\int_a^bv\\,u'\\,dx$. It undoes the product rule."),
         ("methode", "Choosing $u$", "Pick $u$ as the part that becomes simpler when differentiated. A guide (LATE): Logarithm, then Algebraic ($x$, $x^2$), then Trigonometric, then Exponential. For $\\int\\ln x\\,dx$ take $u=\\ln x$ and $\\frac{dv}{dx}=1$. Repeat if needed ($x^2e^x$ needs two steps)."),
         ("pieges", "Common mistakes", "- Sign errors in $uv-\\int v\\,du$ when $v$ is a $\\cos$ or $-\\sin$.\n- Choosing $u=e^x$ and $\\frac{dv}{dx}=x$: the integral becomes harder.\n- Forgetting to apply limits to the $uv$ term.")],
        [("Example 1", "Find $\\int xe^x\\,dx$.", ["$u=x$, $\\frac{dv}{dx}=e^x$: $\\frac{du}{dx}=1$, $v=e^x$.", "$\\int xe^xdx=xe^x-\\int e^xdx=xe^x-e^x+C=(x-1)e^x+C$."], "$(x-1)e^x+C$", pf),
         ("Example 2", "Evaluate $\\int_1^ex\\ln x\\,dx$.", ["$u=\\ln x$, $\\frac{dv}{dx}=x$: $\\frac{du}{dx}=\\frac1x$, $v=\\frac{x^2}{2}$.", "$\\left[\\frac{x^2}{2}\\ln x\\right]_1^e-\\int_1^e\\frac x2dx=\\frac{e^2}{2}-\\left[\\frac{x^2}{4}\\right]_1^e=\\frac{e^2}{2}-\\frac{e^2-1}{4}$."], "$\\frac{e^2+1}{4}\\approx2.097$")],
        [N("Evaluate $\\int_0^1xe^x\\,dx$.", 1, "$[(x-1)e^x]_0^1=0-(-1)=1$."),
         M("$\\int x\\cos x\\,dx=$", "$x\\sin x+\\cos x+C$", ["$x\\sin x-\\cos x+C$", "$-x\\sin x+\\cos x+C$", "$\\frac{x^2}{2}\\sin x+C$"], "$u=x$, $v=\\sin x$: $x\\sin x-\\int\\sin x\\,dx$."),
         TF("$\\int xe^xdx=\\frac{x^2}{2}e^x+C$.", False, "Differentiating that gives $xe^x+\\frac{x^2}{2}e^x$, not $xe^x$.")],
        [M("$\\int\\ln x\\,dx=$", "$x\\ln x-x+C$", ["$\\frac1x+C$", "$x\\ln x+C$", "$\\frac{(\\ln x)^2}{2}+C$"], "$u=\\ln x$, $v'=1$."),
         O("Find $\\int x^2e^xdx$.", "$u=x^2$, $v'=e^x$: $x^2e^x-\\int2xe^xdx$. By Example 1, $\\int xe^xdx=(x-1)e^x$, so the result is $x^2e^x-2(x-1)e^x+C=(x^2-2x+2)e^x+C$.", ["First application (1)", "Second application (1)", "Simplified answer (1)"])],
        P("Answer the following.", [pm("To find $\\int x\\sin x\\,dx$ the best choice of $u$ is", "$u=x$", ["$u=\\sin x$", "$u=x\\sin x$", "$u=1$"], "$x$ differentiates to 1.", pts=1), pn("Evaluate $\\int_0^{\\pi/2}x\\sin x\\,dx$.", 1, "$[-x\\cos x]_0^{\\pi/2}+\\int_0^{\\pi/2}\\cos x\\,dx=0+1=1$.", tol=0.001, pts=2), pn("Find the area under $y=xe^{-x}$ from $x=0$ to $x=1$ (3 d.p.).", round(1 - 2 / exp(1), 3), "$\\int xe^{-x}dx=-(x+1)e^{-x}$, so $[ -(x+1)e^{-x}]_0^1=1-2e^{-1}=0.264$.", tol=0.001, pts=2)]),
        [("Integration by parts undoes the", "product rule", ["chain rule", "quotient rule", "power rule"], "From $(uv)'=u'v+uv'$."),
         ("For $\\int x^2\\ln x\\,dx$ take $u=$", "$\\ln x$", ["$x^2$", "$x^2\\ln x$", "$1$"], "LATE."),
         ("$\\int_0^\\pi\\sin x\\,dx=$", "$2$", ["$0$", "$1$", "$-2$"], "$[-\\cos x]_0^\\pi$."),
         ("$\\int xe^{2x}dx=$", "$\\frac{x}{2}e^{2x}-\\frac14e^{2x}+C$", ["$\\frac{x^2}{2}e^{2x}+C$", "$xe^{2x}-e^{2x}+C$", "$\\frac{x}{2}e^{2x}+C$"], "$u=x$, $v=\\frac12e^{2x}$."),
         ("How many applications of parts does $\\int x^2e^xdx$ need?", "2", ["1", "3", "0"], "Each reduces the power of $x$.")],
        ill=None, notes=[], ref=REF)

    # partial fractions
    close((5 * 0 + 1) / ((0 - 1) * (0 + 2)), 2 / (0 - 1) + 3 / (0 + 2)); close(I(lambda x: (5 * x + 1) / ((x - 1) * (x + 2)), 2, 3), 2 * log(2) + 3 * log(1.25), 1e-6)
    close(I(lambda x: (4 * x - 2) / ((x - 2) * (x + 1)), 3, 5), 2 * log(3) + 2 * log(1.5), 1e-6)
    close(2 * log(3) + 2 * log(1.5), 3.0081, 1e-3)
    close(2.7 ** 2 / (2.7 ** 2 - 1), 1 + .5 / 1.7 - .5 / 3.7, 1e-9)
    pfig = plot(1.2, 6, -2, 12, curves=[("(5x+1)/((x-1)(x+2))", "blue", None)], xlabel="x")
    lesson(ch, "partial-fractions", "Partial fractions and their integration",
        ["Split a rational function into partial fractions", "Handle improper fractions", "Integrate the results"],
        [("methode", "Partial fractions", "For distinct linear factors: $\\frac{px+q}{(x-a)(x-b)}=\\frac{A}{x-a}+\\frac{B}{x-b}$. Multiply by the denominator and substitute $x=a$ (to get $A$) and $x=b$ (to get $B$) - the cover-up method. For a repeated factor use $\\frac{A}{x-a}+\\frac{B}{(x-a)^2}$."),
         ("methode", "Improper fractions", "If the degree of the numerator is at least that of the denominator, divide first. Example: $\\frac{x^2}{x^2-1}=1+\\frac{1}{x^2-1}$, and then split $\\frac{1}{(x-1)(x+1)}=\\frac{1/2}{x-1}-\\frac{1/2}{x+1}$."),
         ("propriete", "Integrating", "$\\int\\frac{A}{x-a}dx=A\\ln|x-a|+C$. After integrating, combine logs with $\\ln p+\\ln q=\\ln pq$ and $\\ln p-\\ln q=\\ln\\frac pq$ to give an exact answer."),
         ("pieges", "Common mistakes", "Do not forget to check your constants (substitute another value of $x$). A fraction that is improper must be divided first. $\\int\\frac{1}{x-a}dx$ is a logarithm, not $-\\frac{1}{(x-a)^2}$.")],
        [("Example 1", "Express $\\frac{5x+1}{(x-1)(x+2)}$ in partial fractions.", ["Write $5x+1=A(x+2)+B(x-1)$.", "$x=1$: $6=3A$, $A=2$. $x=-2$: $-9=-3B$, $B=3$.", "Check at $x=0$: LHS $=-\\frac12$; RHS $=-2+\\frac32=-\\frac12$."], "$\\frac{2}{x-1}+\\frac{3}{x+2}$", pfig),
         ("Example 2", "Evaluate $\\int_2^3\\frac{5x+1}{(x-1)(x+2)}dx$.", ["$\\int\\left(\\frac{2}{x-1}+\\frac{3}{x+2}\\right)dx=2\\ln|x-1|+3\\ln|x+2|$.", "$[\\,]_2^3=(2\\ln2+3\\ln5)-(2\\ln1+3\\ln4)=2\\ln2+3\\ln\\frac54$."], "$2\\ln2+3\\ln\\frac54\\approx2.056$")],
        [N("In $\\frac{3x+5}{(x+1)(x+3)}=\\frac{A}{x+1}+\\frac{B}{x+3}$ find $B$.", 2, "$x=-3$: $-4=-2B$, $B=2$ (and $A=1$)."),
         M("$\\frac{1}{x(x+1)}=$", "$\\frac1x-\\frac{1}{x+1}$", ["$\\frac1x+\\frac1{x+1}$", "$\\frac{1}{x}-\\frac{1}{x-1}$", "$\\frac{1}{x^2+x}$ cannot be split"], "$1=A(x+1)+Bx$: $A=1$, $B=-1$."),
         TF("$\\int\\frac{1}{x-a}dx=-\\frac{1}{(x-a)^2}+C$.", False, "It is $\\ln|x-a|+C$.")],
        [M("$\\int\\frac{1}{(x-1)(x+1)}dx=$", "$\\frac12\\ln\\left|\\frac{x-1}{x+1}\\right|+C$", ["$\\ln|x^2-1|+C$", "$\\frac12\\ln|x^2-1|+C$", "$\\frac12\\ln\\left|\\frac{x+1}{x-1}\\right|+C$"], "$\\frac{1}{2}\\int\\left(\\frac{1}{x-1}-\\frac{1}{x+1}\\right)dx$."),
         O("Write $\\frac{x^2}{x^2-1}$ in the form $1+\\frac{A}{x-1}+\\frac{B}{x+1}$.", "$\\frac{x^2}{x^2-1}=1+\\frac{1}{x^2-1}$. Then $\\frac{1}{(x-1)(x+1)}=\\frac{A}{x-1}+\\frac{B}{x+1}$ with $1=A(x+1)+B(x-1)$: $x=1$ gives $A=\\frac12$; $x=-1$ gives $B=-\\frac12$.", ["Divides first (1)", "$A=\\frac12$ (1)", "$B=-\\frac12$ (1)"])],
        P("Let $f(x)=\\frac{4x-2}{(x-2)(x+1)}$.", [pn("Write $f(x)=\\frac{A}{x-2}+\\frac{B}{x+1}$. Find $A+B$.", 4, "$x=2$: $6=3A$, $A=2$. $x=-1$: $-6=-3B$, $B=2$. So $A+B=4$ (also equals the coefficient of $x$ in the numerator, 4).", pts=2), pn("Evaluate $\\int_3^5f(x)\\,dx$ (3 d.p.).", round(2 * log(3) + 2 * log(1.5), 3), "$[2\\ln(x-2)+2\\ln(x+1)]_3^5=2\\ln3+2\\ln\\frac{6}{4}=3.008$.", tol=0.002, pts=3)]),
        [("The cover-up method finds $A$ by", "substituting $x=a$", ["differentiating", "setting $A=1$", "adding fractions"], "Kills the other term."),
         ("$\\frac{x+1}{x(x+2)}$ splits as $\\frac{A}{x}+\\frac{B}{x+2}$ with $A=$", "$\\frac12$", ["$1$", "$-\\frac12$", "$2$"], "$x=0$: $1=2A$."),
         ("A fraction with numerator degree 2 and denominator degree 2 is", "improper", ["proper", "undefined", "a surd"], "Divide first."),
         ("$\\ln6-\\ln2=$", "$\\ln3$", ["$\\ln4$", "$\\ln12$", "$\\ln\\frac{1}{3}$"], "Quotient law."),
         ("$\\int\\frac{3}{x+2}dx=$", "$3\\ln|x+2|+C$", ["$\\frac{3}{(x+2)^2}+C$", "$\\ln|3x+6|+C$ only", "$3x\\ln|x+2|+C$"], "Constant multiple of $\\ln$.")],
        ill=None, notes=["Repeated-factor and quadratic-factor cases are described but only distinct linear factors are exercised."], ref=REF)
