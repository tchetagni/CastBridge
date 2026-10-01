import math, cmath
from math import pi, sin, cos, tan, exp, log, sqrt, atan, degrees, radians, comb, factorial
from _maths import *
from gal_a import REF, D, I, close

def isprime(n): return n > 1 and all(n % k for k in range(2, int(n ** .5) + 1))

def build(p):
    ch = p.chapter("de-numeric", "Differential equations and numerical methods", REF + " — differential equations; numerical methods")
    # ------------------------------------------------------------ ODE
    Tf = lambda t: 25 + 70 * exp(-.1 * t); t40 = 10 * log(70 / 15)
    close(Tf(10), 50.7516, 1e-3); close(Tf(t40), 40, 1e-9); close(t40, 15.404, 1e-3)
    close(log(2) / .05, 13.863, 1e-3); close(2 * exp(3), 40.171, 1e-3)
    y = lambda x: 1 / (1 - x); close(D(y, .3), y(.3) ** 2, 1e-5)
    yy = lambda x: sqrt(2 * x * x + 2); close(yy(1), 2); close(D(yy, .7) * yy(.7), 2 * .7, 1e-5)
    cool = plot(0, 40, 0, 100, curves=[("25+70exp(-0.1x)", "blue", None)], points=[(t40, 40, "(15.4, 40)", "red")], segments=[(0, 25, 40, 25, "grey", True, None)], xlabel="t (minutes)", ylabel="T (°C)")
    lesson(ch, "differential-equations", "First-order differential equations (separable)",
        ["Solve $\\frac{dy}{dx}=f(x)g(y)$ by separating variables", "Use initial conditions", "Form and solve models (growth, cooling)"],
        [("methode", "Separation of variables", "If $\\frac{dy}{dx}=f(x)\\,g(y)$ then $\\int\\frac{1}{g(y)}dy=\\int f(x)\\,dx$. Integrate both sides (one constant $C$ is enough), then use the initial condition to find $C$, then make $y$ the subject if possible."),
         ("formule", "Standard models", "Growth/decay: $\\frac{dP}{dt}=kP$ has solution $P=P_0e^{kt}$. Newton's law of cooling: $\\frac{dT}{dt}=-k(T-T_s)$ with surroundings $T_s$, solution $T=T_s+(T_0-T_s)e^{-kt}$."),
         ("pieges", "Common mistakes", "- Putting $C$ on both sides or forgetting it.\n- Integrating the wrong side: all $y$ terms with $dy$, all $x$ terms with $dx$.\n- In modelling, forgetting the units of $t$ or the sign of $k$.")],
        [("Example 1", "Solve $\\frac{dy}{dx}=2xy$ given $y=3$ when $x=0$.", ["Separate: $\\int\\frac{1}{y}dy=\\int2x\\,dx$, so $\\ln y=x^2+C$.", "$y=Ae^{x^2}$ with $A=e^C$. At $x=0$: $3=A$."], "$y=3e^{x^2}$"),
         ("Example 2", "A population of bacteria satisfies $\\frac{dP}{dt}=0.05P$ with $P=1000$ at $t=0$. When does it double?", ["$P=1000e^{0.05t}$.", "$2=e^{0.05t}$, so $t=\\frac{\\ln2}{0.05}=13.86$."], "About 13.9 time units")],
        [N("$\\frac{dy}{dx}=3y$ and $y(0)=2$. Find $y(1)$ (2 d.p.).", 40.17, "$y=2e^{3x}$, so $y(1)=2e^3=40.17$.", tol=0.01),
         M("The general solution of $\\frac{dy}{dx}=\\frac xy$ is", "$y^2=x^2+C$", ["$y=x^2+C$", "$y=\\ln x+C$", "$\\frac{y^2}{2}=x$"], "$\\int y\\,dy=\\int x\\,dx$ gives $\\frac{y^2}{2}=\\frac{x^2}{2}+c$."),
         TF("A separable equation can always be solved by separating variables and integrating.", True, "Separation reduces it to two integrals (although they may not always be easy).")],
        [M("$\\frac{dy}{dx}=y^2$, $y(0)=1$ has solution", "$y=\\frac{1}{1-x}$", ["$y=e^x$", "$y=1+x^2$", "$y=\\frac{1}{1+x}$"], "$-\\frac1y=x+C$ with $C=-1$."),
         O("Solve $y\\frac{dy}{dx}=2x$ given $y=2$ when $x=1$. State $y$ in terms of $x$ for $y>0$.", "$\\int y\\,dy=\\int2x\\,dx$: $\\frac{y^2}{2}=x^2+C$. At $(1,2)$: $2=1+C$, $C=1$. So $y^2=2x^2+2$ and $y=\\sqrt{2x^2+2}$.", ["Separates correctly (1)", "$C=1$ (1)", "Final $y=\\sqrt{2x^2+2}$ (1)"])],
        P("A cup of tea at 95 degrees C is left in a room at 25 degrees C. Its temperature $T$ satisfies $\\frac{dT}{dt}=-0.1(T-25)$ ($t$ in minutes).", [pn("Find $T$ when $t=10$ (1 d.p. in degrees C).", 50.8, "$T=25+70e^{-0.1t}$; at $t=10$: $25+70e^{-1}=50.75$.", tol=0.1, pts=2), pn("After how many minutes does the tea reach 40 degrees C (1 d.p.)?", 15.4, "$70e^{-0.1t}=15$, $t=10\\ln\\frac{70}{15}=15.4$.", tol=0.1, pts=3)], figure=cool),
        [("To separate $\\frac{dy}{dx}=xy$ we write", "$\\int\\frac{dy}{y}=\\int x\\,dx$", ["$\\int y\\,dy=\\int x\\,dx$", "$\\int\\frac{dy}{x}=\\int y\\,dx$", "$y=x\\,y'$"], "$y$-terms with $dy$."),
         ("$\\frac{dP}{dt}=kP$ gives", "$P=P_0e^{kt}$", ["$P=P_0+kt$", "$P=kP_0t$", "$P=\\ln t$"], "Exponential."),
         ("In Newton's cooling, as $t\\to\\infty$, $T\\to$", "the surrounding temperature", ["0", "infinity", "the initial temperature"], "$e^{-kt}\\to0$."),
         ("An initial condition is used to find", "the constant $C$", ["the variable $x$", "the derivative", "the integral sign"], "Particular solution."),
         ("If $\\ln y=x+C$ then $y=$", "$Ae^x$", ["$e^x+C$", "$x+A$", "$A\\ln x$"], "$A=e^C$.")],
        ill=(cool, "Newton cooling: the temperature falls towards the room temperature of 25 degrees C.", "Decaying exponential curve from 95 degrees C levelling off at 25 degrees C, with the point (15.4, 40) marked."), notes=["Second-order and integrating-factor equations are not included."], ref=REF)

    # ------------------------------------------------------------ numerical
    f = lambda x: x ** 3 - x - 2; fp = lambda x: 3 * x * x - 1
    x1 = 1.5 - f(1.5) / fp(1.5); x2 = x1 - f(x1) / fp(x1); close(x1, 1.521739, 1e-5); close(x2, 1.521380, 1e-5)
    h = .25; xs = [i * h for i in range(5)]; tr = h / 2 * (1 + .5 + 2 * sum(1 / (1 + x * x) for x in xs[1:4])); close(tr, 0.78279, 1e-5); close(pi / 4, .78540, 1e-4)
    g = lambda x: x ** 3 + 2 * x - 5; gp = lambda x: 3 * x * x + 2
    n1 = 1.3 - g(1.3) / gp(1.3); n2 = n1 - g(n1) / gp(n1); assert g(1) < 0 < g(2); close(n1, 1.32871, 1e-4)
    it = [1.0]
    for _ in range(3): it.append(sqrt(it[-1] + 2))
    close(it[1], 1.7321, 1e-4); close(it[3], 1.9829, 1e-4); close(1.5 - .25 / 3, 1.41667, 1e-4); assert 1 ** 3 + 1 - 3 < 0 < 8 + 2 - 3
    assert 1 / 2 * (0 + 4 + 2 * 1) == 3
    nr = plot(0.5, 2.5, -1.5, 4.5, curves=[("x^2-2", "blue", None), ("3x-4.25", "orange", None)], points=[(1.5, .25, "x0 = 1.5", "red"), (1.41667, 0, "x1", "green")], xlabel="x")
    lesson(ch, "numerical-methods", "Numerical methods",
        ["Locate roots by sign change", "Use fixed-point iteration and Newton-Raphson", "Use the trapezium rule"],
        [("propriete", "Sign change", "If $f$ is continuous and $f(a)$, $f(b)$ have opposite signs then $f(x)=0$ has a root between $a$ and $b$. Repeating (interval bisection) narrows the root. Always say 'continuous'."),
         ("formule", "Iteration and Newton-Raphson", "Rearrange $f(x)=0$ as $x=g(x)$ and iterate $x_{n+1}=g(x_n)$; it converges if $|g'|<1$ near the root. Newton-Raphson: $x_{n+1}=x_n-\\frac{f(x_n)}{f'(x_n)}$ (tangent meets the x-axis). It can fail if $f'(x_n)$ is near 0 or if the start is poor."),
         ("formule", "Trapezium rule", "$\\int_a^bf(x)\\,dx\\approx\\frac h2\\Big[y_0+y_n+2(y_1+\\cdots+y_{n-1})\\Big]$ with $n$ strips of width $h=\\frac{b-a}{n}$. More strips give a better estimate."),
         ("pieges", "Common mistakes", "In the trapezium rule the middle values are DOUBLED, the end values counted once. A calculator must be in radian mode for trig functions. Newton-Raphson needs $f'$: do not forget to differentiate correctly.")],
        [("Example 1", "Use Newton-Raphson on $f(x)=x^3-x-2$ with $x_0=1.5$ to find $x_1$ and $x_2$.", ["$f(1.5)=3.375-1.5-2=-0.125$; $f'(1.5)=3(2.25)-1=5.75$. $x_1=1.5+\\frac{0.125}{5.75}=1.5217$.", "$f(1.5217)=0.00201$, $f'(1.5217)=5.9467$, so $x_2=1.5217-0.00034=1.5214$."], "$x_1\\approx1.5217$, $x_2\\approx1.5214$", nr),
         ("Example 2", "Estimate $\\int_0^1\\frac{1}{1+x^2}dx$ using 4 strips.", ["$h=0.25$: $y_0=1$, $y_1=0.9412$, $y_2=0.8$, $y_3=0.64$, $y_4=0.5$.", "$\\frac{0.25}{2}\\big[1+0.5+2(0.9412+0.8+0.64)\\big]=0.125\\times6.2624$."], "$\\approx0.783$ (exact value $\\frac\\pi4=0.785$)")],
        [N("One Newton-Raphson step for $x^2-2=0$ from $x_0=1.5$ gives $x_1=$ (4 d.p.)", 1.4167, "$1.5-\\frac{0.25}{3}$.", tol=0.0002),
         M("$f(x)=x^3+x-3$ has a root in the interval", "$1<x<2$", ["$0<x<1$", "$2<x<3$", "$-1<x<0$"], "$f(1)=-1<0$, $f(2)=7>0$."),
         TF("Newton-Raphson always converges to a root.", False, "It can diverge or jump to another root when the start is poor or $f'$ is near 0.")],
        [N("Trapezium rule with 2 strips for $\\int_0^2x^2dx$. Find the estimate.", 3, "$h=1$: $\\frac12[0+4+2(1)]=3$ (exact $\\frac83$).", tol=0.001),
         O("The iteration $x_{n+1}=\\sqrt{x_n+2}$ with $x_0=1$ is used. Find $x_1,x_2,x_3$ and say what root it approaches, and why.", "$x_1=1.7321$, $x_2=1.9319$, $x_3=1.9829$. It approaches 2 because the limit satisfies $x=\\sqrt{x+2}$, so $x^2=x+2$, $(x-2)(x+1)=0$, and only $x=2$ is non-negative.", ["Three values (1)", "States limit 2 (1)", "Solves $x^2=x+2$ (1)"])],
        P("$f(x)=x^3+2x-5$.", [pm("$f(1)=-2$ and $f(2)=7$ show that", "a root lies between 1 and 2", ["the root is 1.5", "there are three roots", "the root is negative"], "Sign change of a continuous function.", pts=1), pn("Use Newton-Raphson from $x_0=1.3$ to find $x_1$ (4 d.p.).", round(n1, 4), "$f(1.3)=-0.203$, $f'(1.3)=7.07$; $x_1=1.3+\\frac{0.203}{7.07}=1.3287$.", tol=0.0002, pts=3)]),
        [("Newton-Raphson formula is", "$x_n-\\frac{f(x_n)}{f'(x_n)}$", ["$x_n+f(x_n)$", "$\\frac{f'(x_n)}{f(x_n)}$", "$x_n-f'(x_n)$"], "Tangent method."),
         ("In the trapezium rule the first and last ordinates are", "counted once", ["doubled", "ignored", "squared"], "Only middle ones doubled."),
         ("A sign change proves a root provided $f$ is", "continuous", ["differentiable at 0", "positive", "linear"], "Needed for the root."),
         ("More strips in the trapezium rule give", "a more accurate estimate", ["a less accurate estimate", "an exact value always", "no change"], "Smaller $h$."),
         ("An iteration converges when $|g'(x)|$ near the root is", "less than 1", ["greater than 1", "equal to 0 only", "negative"], "Fixed-point condition.")],
        ill=None, notes=["Simpson's rule not included; check whether the syllabus requires it."], ref=REF)

    # ------------------------------------------------------------ complex
    ch = p.chapter("complex-series", "Complex numbers and series", REF + " — complex numbers (to be checked: may be Further Mathematics content), Maclaurin series")
    close((3 + 2j) * (1 - 4j), 11 - 10j); close((2 + 1j) / (1 - 1j), .5 + 1.5j)
    close(abs(3 + 4j), 5); close(degrees(cmath.phase(3 + 4j)), 53.1301, 1e-3)
    assert (2 + 3j) ** 2 - 4 * (2 + 3j) + 13 == 0 and (2 - 3j) ** 2 - 4 * (2 - 3j) + 13 == 0
    z = 1 + 2j; close(z + 2 * z.conjugate(), 3 - 2j)
    r1 = (6 + cmath.sqrt(36 - 100)) / 2; close(r1, 3 + 4j); close(r1 * r1.conjugate(), 25)
    ar = Axes(-1, 5, -5, 5, unit=32, xticks=[1, 3, 5], yticks=[-4, 4]).line(0, 0, 3, 4, color="blue", width=2, arrow="end").line(0, 0, 3, -4, color="orange", width=2, arrow="end")
    ar.pt(3, 4, "3 + 4i", dx=10, dy=-6, anchor="start").pt(3, -4, "3 − 4i", dx=10, dy=16, anchor="start").text(0.8, 0.9, "θ", 13, anchor="start")
    lesson(ch, "complex-numbers", "Complex numbers",
        ["Perform arithmetic with complex numbers", "Use the Argand diagram, modulus and argument", "Solve quadratics with complex roots"],
        [("definition", "Complex numbers", "$i^2=-1$. A complex number is $z=a+bi$ with real part $a$ and imaginary part $b$. Add/subtract real and imaginary parts separately; multiply like brackets and replace $i^2$ by $-1$. The **conjugate** of $z$ is $\\bar z=a-bi$ and $z\\bar z=a^2+b^2$ is real."),
         ("methode", "Division", "To divide, multiply numerator and denominator by the conjugate of the denominator: $\\frac{2+i}{1-i}=\\frac{(2+i)(1+i)}{(1-i)(1+i)}=\\frac{1+3i}{2}$."),
         ("formule", "Argand diagram, modulus, argument", "$z=a+bi$ is the point $(a,b)$. Modulus $|z|=\\sqrt{a^2+b^2}$; argument $\\theta=\\text{arg }z$ with $\\tan\\theta=\\frac ba$ (check the quadrant). A quadratic with real coefficients and $\\Delta<0$ has two roots that are complex conjugates: $x=\\frac{-b\\pm i\\sqrt{-\\Delta}}{2a}$."),
         ("pieges", "Common mistakes", "$i^2=-1$ not $+1$. The conjugate of $2-5i$ is $2+5i$ (only the sign of the imaginary part changes). Take care with the quadrant of the argument.")],
        [("Example 1", "Calculate $(3+2i)(1-4i)$.", ["Expand: $3-12i+2i-8i^2$.", "$i^2=-1$: $3-10i+8=11-10i$."], "$11-10i$"),
         ("Example 2", "Solve $x^2-4x+13=0$.", ["$\\Delta=16-52=-36$, so $x=\\frac{4\\pm\\sqrt{-36}}{2}=\\frac{4\\pm6i}{2}$.", "Check: sum of roots $=4$, product $=(2+3i)(2-3i)=13$."], "$x=2\\pm3i$", ar.fig())],
        [N("Find $|3+4i|$.", 5, "$\\sqrt{9+16}$."),
         M("$i^3=$", "$-i$", ["$i$", "$1$", "$-1$"], "$i^3=i^2\\cdot i=-i$."),
         TF("The conjugate of $2-5i$ is $-2+5i$.", False, "It is $2+5i$.")],
        [M("The sum of the roots of $x^2+2x+5=0$ is", "$-2$", ["$2$", "$5$", "$-5$"], "Roots $-1\\pm2i$; sum $=-2$, product $=5$."),
         O("Find the complex number $z$ such that $z+2\\bar z=3-2i$.", "Let $z=a+bi$. Then $z+2\\bar z=3a-bi$. So $3a=3$ and $-b=-2$, giving $z=1+2i$. Check: $1+2i+2(1-2i)=3-2i$.", ["Sets $z=a+bi$ (1)", "Two equations (1)", "$z=1+2i$ (1)"])],
        P("The quadratic $x^2-6x+25=0$ has roots $z_1=3+4i$ and $z_2=3-4i$.", [pn("Find $|z_1|$.", 5, "$\\sqrt{9+16}=5$.", pts=1), pn("Find $\\text{arg }z_1$ in degrees (2 d.p.).", 53.13, "$\\tan\\theta=\\frac43$ in the first quadrant: $\\theta=53.13\\degree$.", tol=0.01, pts=2), pn("Find the product $z_1z_2$.", 25, "$(3+4i)(3-4i)=9+16=25$, equal to $\\frac ca$.", pts=1)]),
        [("$(1+i)^2=$", "$2i$", ["$2$", "$1+i$", "$0$"], "$1+2i-1$."),
         ("$z\\bar z$ for $z=3+4i$ is", "$25$", ["$-7$", "$5$", "$24i$"], "$a^2+b^2$."),
         ("Complex roots of a real quadratic come in", "conjugate pairs", ["equal pairs", "pairs with real sum 0 always", "triples"], "Real coefficients."),
         ("The point representing $-2+i$ on the Argand diagram is", "$(-2,1)$", ["$(2,-1)$", "$(1,-2)$", "$(-2,-1)$"], "(real, imaginary)."),
         ("$\\frac{1}{i}=$", "$-i$", ["$i$", "$1$", "$-1$"], "$\\frac{i}{i^2}$.")],
        ill=None, notes=["IMPORTANT: complex numbers may belong to the Further Mathematics syllabus rather than Advanced Level Mathematics in Cameroon: confirm with the official syllabus before release; delete this lesson if not on the syllabus.", "Polar form and De Moivre are in the Further Mathematics packs."], ref=REF)

    # maclaurin
    ser = lambda x: x - x ** 3 / 6; close(sin(.2), .2 - .008 / 6, 1e-5); close(log(1.1), .1 - .005 + .001 / 3, 1e-4)
    close(exp(.1) * sin(.1), .1 + .01 + .001 / 3, 5e-6)
    close(1 * 1 + 2 * 1, 3); close(-1 / 2, -.5)
    mac = plot(-3, 3, -2, 2, curves=[("sin(x)", "blue", "sin x"), ("x-x^3/6", "orange", None)], xlabel="x")
    lesson(ch, "maclaurin", "Maclaurin series",
        ["Find Maclaurin series from derivatives", "Use standard series for $e^x$, $\\sin x$, $\\cos x$, $\\ln(1+x)$", "Approximate values and combine series"],
        [("formule", "Maclaurin series", "$f(x)=f(0)+f'(0)x+\\frac{f''(0)}{2!}x^2+\\frac{f'''(0)}{3!}x^3+\\cdots$. It requires $f$ and its derivatives to exist at 0 (so $\\ln x$ has no Maclaurin series, but $\\ln(1+x)$ does)."),
         ("propriete", "Standard series", "$e^x=1+x+\\frac{x^2}{2!}+\\frac{x^3}{3!}+\\cdots$ (all $x$); $\\sin x=x-\\frac{x^3}{3!}+\\frac{x^5}{5!}-\\cdots$; $\\cos x=1-\\frac{x^2}{2!}+\\frac{x^4}{4!}-\\cdots$ (all $x$, in radians); $\\ln(1+x)=x-\\frac{x^2}{2}+\\frac{x^3}{3}-\\cdots$ for $-1<x\\leq1$."),
         ("methode", "Using them", "Replace $x$ by a function ($e^{2x}$: use $2x$), multiply series and keep terms up to the required power. For approximations use $x$ small: the first few terms are very accurate near 0, as in the picture."),
         ("pieges", "Common mistakes", "Forgetting the factorials. Replacing $x$ by $2x$ must also change $x^2$ to $(2x)^2=4x^2$. Using a series outside its range of validity (e.g. $\\ln(1+x)$ with $x=3$).")],
        [("Example 1", "Find the series for $e^{2x}$ up to $x^3$.", ["Replace $x$ by $2x$: $1+2x+\\frac{(2x)^2}{2}+\\frac{(2x)^3}{6}$.", "$=1+2x+2x^2+\\frac43x^3$."], "$1+2x+2x^2+\\frac43x^3$", mac),
         ("Example 2", "Estimate $\\sin0.2$ using two terms, and $\\ln1.1$ using three terms.", ["$\\sin0.2\\approx0.2-\\frac{0.008}{6}=0.19867$ (true value 0.198669).", "$\\ln1.1\\approx0.1-\\frac{0.01}{2}+\\frac{0.001}{3}=0.09533$ (true value 0.09531)."], "$0.19867$ and $0.09533$")],
        [N("Find the coefficient of $x^2$ in the Maclaurin series of $\\cos x$.", -.5, "$-\\frac{1}{2!}=-\\frac12$.", tol=0.001),
         M("The first two terms of $e^{-x}$ are", "$1-x$", ["$1+x$", "$-1-x$", "$1-\\frac{x}{2}$"], "Replace $x$ by $-x$."),
         TF("$\\ln x$ has a Maclaurin series.", False, "$\\ln x$ is not defined at $x=0$; use $\\ln(1+x)$.")],
        [M("The coefficient of $x^3$ in the series of $\\sin2x$ is", "$-\\frac43$", ["$-\\frac13$", "$\\frac43$", "$-\\frac{8}{3}$"], "$-\\frac{(2x)^3}{6}=-\\frac{8}{6}x^3$."),
         O("Find the series for $(1+x)e^x$ up to $x^2$.", "$e^x=1+x+\\frac{x^2}{2}+\\cdots$. Multiply by $(1+x)$: $1+x+\\frac{x^2}{2}+x+x^2=1+2x+\\frac32x^2$.", ["Series of $e^x$ (1)", "Multiplication (1)", "$1+2x+\\frac32x^2$ (1)"])],
        P("Consider $e^x\\sin x$.", [pn("Find the coefficient of $x^3$ in its series.", round(1 / 3, 4), "$\\left(1+x+\\frac{x^2}{2}\\right)\\left(x-\\frac{x^3}{6}\\right)=x+x^2+\\left(\\frac12-\\frac16\\right)x^3$, so $\\frac13$.", tol=0.001, pts=2), pn("Use $x+x^2+\\frac{x^3}{3}$ to estimate $e^{0.1}\\sin0.1$ (4 d.p.).", 0.1103, "$0.1+0.01+0.000333=0.110333$ (true value 0.110333).", tol=0.0001, pts=2)]),
        [("$e^x$ expanded begins", "$1+x+\\frac{x^2}{2}+\\cdots$", ["$1+x+x^2+\\cdots$", "$x+\\frac{x^2}{2}+\\cdots$", "$1+\\frac{x}{2}+\\cdots$"], "Factorials."),
         ("$\\sin x$ has only", "odd powers of $x$", ["even powers of $x$", "all powers", "no powers"], "$x,x^3,x^5,\\ldots$"),
         ("The series of $\\cos x$ starts with", "$1$", ["$x$", "$0$", "$\\frac12$"], "$\\cos0=1$."),
         ("$\\ln(1+x)$ series is valid for", "$-1<x\\leq1$", ["all $x$", "$x>1$", "$x<-1$"], "Convergence range."),
         ("$f(0)=1$, $f'(0)=2$, $f''(0)=6$: the series begins", "$1+2x+3x^2$", ["$1+2x+6x^2$", "$1+2x+x^2$", "$1+x+3x^2$"], "$\\frac{6}{2!}=3$.")],
        ill=None, notes=["Range of validity and the binomial series for rational $n$ are only mentioned."], ref=REF)

    # ------------------------------------------------------------ vectors 3D
    ch = p.chapter("vectors3d", "Vectors in three dimensions and proof", REF + " — vectors in 3D; proof")
    dot = lambda a, b: sum(x * y for x, y in zip(a, b)); nrm = lambda a: sqrt(dot(a, a))
    a3, b3 = (1, 2, 2), (2, -1, 2); close(degrees(math.acos(dot(a3, b3) / (nrm(a3) * nrm(b3)))), 63.612, 1e-3)
    t = 3; pt3 = (1 + 2 * t, 2 - t, 3 + t); assert pt3 == (7, -1, 6) and sum(pt3) == 12 and 2 * 1 + 0 - 2 == 0
    AB = (2, -1, 1); close(nrm(AB), 2.449, 1e-3); close(90 - degrees(math.acos(dot(AB, (1, 1, 1)) / (nrm(AB) * sqrt(3)))), 28.13, 1e-2)
    assert nrm((2, 3, 6)) == 7 and abs(degrees(math.acos(1 / sqrt(2))) - 45) < 1e-9
    assert dot((1, 1, -1), (2, -1, 1)) == 0
    v3 = shapes([POLY([90, 190, 290, 190, 330, 130, 130, 130], fill="lightgrey", stroke="ink", width=2),
                 LINE(60, 220, 330, 40, color="blue", width=3, arrow="end"), CIRCLE(205, 134, 5, fill="red", stroke="red"),
                 T(205, 122, "P", 13), T(340, 40, "line", 13, anchor="start"), T(310, 180, "plane", 13, anchor="start")], 400, 240)
    lesson(ch, "vectors-3d", "Vectors in three dimensions: lines and planes",
        ["Use 3-D vectors, magnitude and the scalar product", "Write and use vector equations of lines", "Use the equation of a plane $ax+by+cz=d$ and find intersections"],
        [("formule", "Basics in 3-D", "$|(x,y,z)|=\\sqrt{x^2+y^2+z^2}$. Scalar product: $\\vec a\\cdot\\vec b=a_1b_1+a_2b_2+a_3b_3=|\\vec a||\\vec b|\\cos\\theta$. Perpendicular vectors have scalar product 0."),
         ("formule", "Lines and planes", "Line through $A$ with direction $\\vec d$: $\\vec r=\\vec a+t\\vec d$. A plane with normal $\\vec n=(a,b,c)$ through a point has equation $ax+by+cz=d$ (with $d=\\vec n\\cdot$ point). The angle between a line and a plane is $90\\degree$ minus the angle between the line and the normal."),
         ("methode", "Line meets plane", "Write the point of the line as $(x(t),y(t),z(t))$, substitute into the plane equation and solve for $t$. If the $t$ terms cancel and the equation is false the line is parallel to the plane (no intersection); if true the line lies in the plane."),
         ("pieges", "Common mistakes", "Two lines in 3-D that do not meet are not always parallel: they may be **skew**. The angle between two lines uses their DIRECTION vectors, not position vectors. Remember to take the acute angle when needed.")],
        [("Example 1", "Find the angle between lines with directions $(1,2,2)$ and $(2,-1,2)$.", ["$\\vec a\\cdot\\vec b=2-2+4=4$; $|\\vec a|=3$, $|\\vec b|=3$.", "$\\cos\\theta=\\frac49$, so $\\theta=63.6\\degree$."], "$\\theta\\approx63.6\\degree$"),
         ("Example 2", "Find where the line $\\vec r=(1,2,3)+t(2,-1,1)$ meets the plane $x+y+z=12$.", ["Point: $(1+2t,\;2-t,\;3+t)$. Sum $=6+2t=12$, so $t=3$.", "Point: $(7,-1,6)$. Check: $7-1+6=12$."], "$(7,-1,6)$", v3)],
        [N("Find $|(2,3,6)|$.", 7, "$\\sqrt{4+9+36}=7$."),
         M("The angle between $(1,0,0)$ and $(1,1,0)$ is", "$45\\degree$", ["$90\\degree$", "$30\\degree$", "$60\\degree$"], "$\\cos\\theta=\\frac{1}{\\sqrt2}$."),
         TF("Two lines in 3-D that never meet must be parallel.", False, "They may be skew (not parallel, not meeting).")],
        [M("The plane through the origin with normal $(2,1,-1)$ has equation", "$2x+y-z=0$", ["$x+2y-z=0$", "$2x+y-z=1$", "$2x-y+z=0$"], "$ax+by+cz=d$ with $d=0$."),
         O("Show that the line $\\vec r=(0,1,2)+t(1,1,-1)$ does not meet the plane $2x-y+z=5$.", "Point: $(t,1+t,2-t)$. Substitute: $2t-(1+t)+(2-t)=1$. The $t$ terms cancel and $1\\neq5$, so there is no solution: the line is parallel to the plane and does not meet it.", ["Parametric point (1)", "Substitution gives $1=5$ (1)", "Conclusion (1)"])],
        P("$A(1,2,3)$ and $B(3,1,4)$. The line $AB$ meets the plane $x+y+z=12$ at $P$.", [pn("Find $|\\vec{AB}|$ (3 d.p.).", 2.449, "$\\vec{AB}=(2,-1,1)$, $\\sqrt{6}=2.449$.", tol=0.001, pts=1), pn("Find the x-coordinate of $P$.", 7, "Line $(1+2t,2-t,3+t)$; $6+2t=12$ so $t=3$ and $x=7$.", pts=1), pn("Find the angle between $AB$ and the plane (1 d.p., degrees).", 28.1, "Angle with the normal $(1,1,1)$: $\\cos\\phi=\\frac{2}{\\sqrt6\\sqrt3}=0.4714$, $\\phi=61.9\\degree$; the angle with the plane is $90-61.9=28.1\\degree$.", tol=0.1, pts=2)]),
        [("$(1,2,3)\\cdot(4,-5,6)=$", "$12$", ["$32$", "$-12$", "$(4,-10,18)$"], "$4-10+18$."),
         ("The direction of $\\vec r=(1,0,2)+t(3,1,-2)$ is", "$(3,1,-2)$", ["$(1,0,2)$", "$(4,1,0)$", "$t$"], "Coefficient of $t$."),
         ("A normal to $3x-y+2z=7$ is", "$(3,-1,2)$", ["$(3,1,2)$", "$(7,0,0)$", "$(1,1,1)$"], "Coefficients."),
         ("$(2,-1,3)\\perp(1,k,0)$ when $k=$", "$2$", ["$-2$", "$0$", "$\\frac12$"], "$2-k=0$."),
         ("Skew lines are", "not parallel and do not meet", ["parallel", "intersecting", "the same line"], "Only in 3-D.")],
        ill=None, notes=["Cross product, shortest distance between skew lines and plane in parametric form are not included."], ref=REF)

    # ------------------------------------------------------------ proof
    assert all(isprime(n * n + n + 41) for n in range(40)) and not isprime(40 * 40 + 40 + 41) and 40 * 40 + 40 + 41 == 41 ** 2
    assert all((n ** 3 - n) % 6 == 0 for n in range(-50, 50)) and (1, 1) and 1 ** 2 <= 1
    pr = shapes([RECT(40, 20, 320, 34, stroke="blue", radius=6), T(200, 42, "Assume √2 = a/b (lowest terms)", 14),
                 LINE(200, 54, 200, 78, arrow="end"),
                 RECT(40, 78, 320, 34, stroke="blue", radius=6), T(200, 100, "a² = 2b²  so a is even, a = 2k", 14),
                 LINE(200, 112, 200, 136, arrow="end"),
                 RECT(40, 136, 320, 34, stroke="blue", radius=6), T(200, 158, "4k² = 2b²  so b² = 2k², b is even", 14),
                 LINE(200, 170, 200, 194, arrow="end"),
                 RECT(40, 194, 320, 34, stroke="red", radius=6), T(200, 216, "a, b both even: contradiction", 14)], 400, 240)
    lesson(ch, "proof", "Proof",
        ["Prove statements by deduction", "Disprove by counterexample", "Prove by contradiction"],
        [("definition", "Types of proof", "**Deduction**: start from known facts and argue step by step (use algebra, e.g. odd number $=2k+1$). **Counterexample**: one case where a statement fails disproves it. **Contradiction**: assume the statement is false, derive something impossible, so the statement is true."),
         ("methode", "Algebraic proofs", "Represent even numbers as $2k$, odd numbers as $2k+1$, consecutive integers as $n$, $n+1$. Show the final expression has the required form (e.g. is a multiple of 2). Writing 'for all integers $k$' and a clear concluding sentence earns the marks."),
         ("pieges", "Common mistakes", "- Checking a few examples is NOT a proof (the statement $n^2+n+41$ is prime for $n=0,\\dots,39$ but fails at $n=40$).\n- Starting from the result you want to prove and 'working backwards' without logical equivalence.\n- Forgetting to state the conclusion.")],
        [("Example 1", "Prove that the sum of two odd numbers is even.", ["Let the numbers be $2m+1$ and $2n+1$ where $m,n$ are integers.", "Sum $=2m+2n+2=2(m+n+1)$, a multiple of 2, so it is even."], "Proved", pr),
         ("Example 2", "Show that $n^2>n$ is false for some positive integer $n$.", ["A single counterexample is enough: take $n=1$.", "$1^2=1$ and $1>1$ is false."], "Counterexample $n=1$")],
        [N("What is the smallest positive integer $n$ for which $n^2+n+41$ is not prime?", 40, "For $n=40$: $40^2+40+41=1681=41^2$. Every smaller $n\\geq1$ gives a prime."),
         M("A counterexample to 'every prime is odd' is", "$2$", ["$9$", "$1$", "$4$"], "$2$ is prime and even."),
         TF("Checking that a statement holds for 5 examples proves it is true.", False, "Examples never prove a general statement; one counterexample can disprove it.")],
        [M("Prove by contradiction that there is no largest integer. The first step is to", "assume there is a largest integer $N$", ["assume $N+1$ is not an integer", "choose $N=0$", "show $N$ is even"], "Then $N+1>N$ is an integer, a contradiction."),
         O("Prove that the product of two consecutive integers is even.", "Let the integers be $n$ and $n+1$. If $n$ is even, $n=2k$ and the product is $2k(n+1)$, even. If $n$ is odd, $n+1$ is even, $n+1=2k$, and the product $n\\cdot2k$ is even. In both cases the product is even.", ["Sets up $n$, $n+1$ (1)", "Considers both cases (1)", "Concludes (1)"])],
        P("Consider the statement: 'for every positive integer $n$, $n^3-n$ is divisible by 6'.", [pm("$n^3-n$ factorises as", "$(n-1)n(n+1)$", ["$n(n-1)^2$", "$(n^2-1)(n+1)$", "$n(n+1)^2$"], "$n^3-n=n(n^2-1)=n(n-1)(n+1)$.", pts=1), pt("The product of three consecutive integers always contains a multiple of 2 and a multiple of 3, which proves the statement.", True, "A product with a factor 2 and a factor 3 (different numbers, coprime) is divisible by 6.", pts=1), pn("Evaluate $n^3-n$ for $n=5$.", 120, "$125-5=120=6\\times20$.", pts=1), pt("The statement 'for all integers $n$, $n^2+n+41$ is prime' is false.", True, "$n=40$ gives $41^2$.", pts=1)]),
        [("An even number can be written as", "$2k$", ["$2k+1$", "$k^2$", "$k+2$"], "Multiple of 2."),
         ("An odd number can be written as", "$2k+1$", ["$2k$", "$k^2$", "$2k-2$"], "One more than a multiple of 2."),
         ("To disprove a general statement it is enough to give", "one counterexample", ["three examples", "a graph", "a calculation with decimals"], "Logic."),
         ("In proof by contradiction we assume", "the opposite of what we want to prove", ["the statement is true", "nothing", "a specific example"], "Then derive impossibility."),
         ("$(2m+1)^2$ is", "odd", ["even", "negative", "a multiple of 4"], "$4m^2+4m+1$.")],
        ill=None, notes=["Proof by induction is included in the Further Mathematics packs; check whether the Advanced Level Mathematics syllabus also includes it."], ref=REF)
