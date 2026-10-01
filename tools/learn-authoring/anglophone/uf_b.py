import math
from math import pi, sin, cos, tan, sqrt, exp, log, cosh, sinh, atan, asin
from _fm import *
from gal_a import close, I
from uf_a import REF, NOTE

def build(p):
    ch = p.chapter("calculus-ext", "Further calculus and differential equations", REF + " — calculus; differential equations")
    # ------------------------------------------------------------ second order ODE
    y = lambda x: 3 * exp(2 * x) - 2 * exp(3 * x); assert y(0) == 1
    h = 1e-6; assert abs((y(h) - y(-h)) / (2 * h)) < 1e-4
    assert 3 + (-2) == 1 and 3 * 2 + (-2) * 3 == 0
    close(2 * cosh(1), 3.0862, 1e-3); close(exp(3) - exp(1), 17.3673, 1e-3)
    yy = lambda x: (1 + 3 * x) * exp(-3 * x); close((yy(1e-6) - yy(-1e-6)) / 2e-6, 0, 1e-3); close(yy(1), 4 * exp(-3), 1e-12); close(4 * exp(-3), .1991, 1e-4)
    assert 2 + 0 == 2 and (-3) * (-2) == 6 and 4 * 4 == 16
    ode = plot(0, 1.5, -5, 95, curves=[("exp(3x)-exp(x)", "blue", None)], points=[(1, exp(3) - exp(1), "(1, 17.4)", "red")], xlabel="x")
    lesson(ch, "differential-equations-2", "Second-order linear differential equations",
        ["Solve $ay''+by'+cy=0$ using the auxiliary equation", "Find particular integrals for simple right-hand sides", "Apply initial conditions"],
        [("formule", "Homogeneous equation", "$ay''+by'+cy=0$ has auxiliary equation $am^2+bm+c=0$. Distinct real roots $m_1,m_2$: $y=Ae^{m_1x}+Be^{m_2x}$. Repeated root $m$: $y=(A+Bx)e^{mx}$. Complex roots $p\\pm qi$: $y=e^{px}(A\\cos qx+B\\sin qx)$. $A$ and $B$ are arbitrary constants."),
         ("methode", "Non-homogeneous equation", "$ay''+by'+cy=f(x)$: general solution = complementary function (the solution of the homogeneous equation) + particular integral (PI). Try a PI of the same form as $f(x)$: constant for a constant, $px+q$ for a linear $f$, $pe^{kx}$ for $e^{kx}$, $p\\cos kx+q\\sin kx$ for trig. If the trial is already in the complementary function multiply by $x$."),
         ("methode", "Initial conditions", "Two conditions (for example $y(0)$ and $y'(0)$) fix $A$ and $B$. Differentiate the general solution, substitute $x=0$ into both $y$ and $y'$, and solve the two equations."),
         ("pieges", "Common mistakes", "Applying the conditions to the complementary function before adding the PI. Forgetting the factor $x$ for repeated roots. Solving $y'=\\ldots$ conditions with the wrong derivative of $e^{mx}$ (it is $me^{mx}$).")],
        [("Example 1", "Solve $y''-5y'+6y=0$ with $y(0)=1$, $y'(0)=0$.", ["Auxiliary: $m^2-5m+6=0$, $m=2,3$: $y=Ae^{2x}+Be^{3x}$.", "$y(0)=A+B=1$; $y'(0)=2A+3B=0$. So $B=-2$, $A=3$."], "$y=3e^{2x}-2e^{3x}$"),
         ("Example 2", "Solve $y''+4y=0$ and $y''-3y'+2y=4$.", ["$m^2+4=0$, $m=\\pm2i$: $y=A\\cos2x+B\\sin2x$.", "For the second: $m=1,2$, complementary function $Ae^x+Be^{2x}$. Try the PI $y=c$: $2c=4$, $c=2$. General solution $y=Ae^x+Be^{2x}+2$."], "$y=A\\cos2x+B\\sin2x$; $y=Ae^x+Be^{2x}+2$")],
        [N("$y''-y=0$ with $y(0)=2$, $y'(0)=0$. Find $y(1)$ (4 d.p.).", 3.0862, "$y=Ae^x+Be^{-x}$: $A+B=2$, $A-B=0$, so $y=e^x+e^{-x}=2\\cosh x$; $y(1)=3.0862$.", tol=0.0003),
         M("The solution of $y''+6y'+9y=0$ has the form", "$(A+Bx)e^{-3x}$", ["$Ae^{3x}+Be^{-3x}$", "$e^{-3x}(A\\cos3x+B\\sin3x)$", "$Ae^{-3x}$"], "$m^2+6m+9=(m+3)^2$: repeated root $-3$."),
         TF("$y''=-\\omega^2y$ has solutions $A\\cos\\omega x+B\\sin\\omega x$.", True, "Auxiliary equation $m^2=-\\omega^2$, $m=\\pm i\\omega$.")],
        [M("$y''-4y'+13y=0$ has general solution", "$e^{2x}(A\\cos3x+B\\sin3x)$", ["$Ae^{2x}+Be^{3x}$", "$e^{3x}(A\\cos2x+B\\sin2x)$", "$(A+Bx)e^{2x}$"], "$m=2\\pm3i$."),
         O("Find the general solution of $y''+2y'-3y=6$.", "Auxiliary $m^2+2m-3=0$: $m=1,-3$, complementary function $Ae^x+Be^{-3x}$. Try $y=c$: $-3c=6$, $c=-2$. General solution $y=Ae^x+Be^{-3x}-2$.", ["Complementary function (1)", "Particular integral $-2$ (1)", "General solution (1)"])],
        P("Solve $y''-4y'+3y=0$ with $y(0)=0$ and $y'(0)=2$.", [pm("The auxiliary equation has roots", "1 and 3", ["$-1$ and $-3$", "2 and 2", "$2\\pm i$"], "$m^2-4m+3=(m-1)(m-3)$.", pts=1), pn("The solution is $y=Ae^x+Be^{3x}$. Find $A$.", -1, "$A+B=0$ and $A+3B=2$ give $B=1$, $A=-1$.", pts=1), pn("Find $y(1)$ (3 d.p.).", 17.367, "$y=e^{3x}-e^x$; $y(1)=e^3-e=17.367$.", tol=0.01, pts=2)], figure=ode),
        [("The auxiliary equation of $y''-3y'+2y=0$ is", "$m^2-3m+2=0$", ["$m^2+3m+2=0$", "$m-3=0$", "$m^3=2$"], "Replace $y''\\to m^2$, $y'\\to m$."),
         ("Complex roots $p\\pm qi$ give", "$e^{px}(A\\cos qx+B\\sin qx)$", ["$Ae^{px}+Be^{qx}$", "$(A+Bx)e^{px}$", "$A\\cos px$"], "Standard."),
         ("General solution of a non-homogeneous equation is", "complementary function + particular integral", ["particular integral only", "complementary function only", "product of the two"], "Superposition."),
         ("How many constants does the general solution of a second-order equation contain?", "2", ["1", "3", "0"], "Two integrations."),
         ("Trial PI for $f(x)=5$ is", "$y=c$", ["$y=cx$", "$y=ce^x$", "$y=c\\cos x$"], "Same form.")],
        ill=(ode, "The solution $y=e^{3x}-e^x$ of $y''-4y'+3y=0$ with $y(0)=0$, $y'(0)=2$ grows rapidly.", "Curve starting at the origin with gradient 2 and rising steeply to about 17.4 at x=1."), notes=[NOTE, "Resonance cases and systems of equations not included."], ref=REF)

    # ------------------------------------------------------------ calculus ext
    close(I(lambda x: 1 / (1 + x * x), 0, 1), pi / 4); close(atan(2) - pi / 4, .3218, 1e-4)
    close(I(lambda x: pi * x, 0, 4), 8 * pi); close(8 * pi, 25.133, 1e-3); close(I(lambda x: 1 / (4 + x * x), 0, 2), pi / 8, 1e-6)
    close(I(lambda x: 1 / (1 + x * x), 0, sqrt(3)), pi / 3, 1e-6); close(I(lambda x: 1 / sqrt(4 - x * x), 0, 1), pi / 6, 1e-6); close(I(lambda x: pi * x ** 4, 0, 1), pi / 5, 1e-6)
    close(I(lambda x: 1 / (x * x + 2 * x + 2), 0, 1), atan(2) - pi / 4, 1e-6)
    vf = Axes(-0.5, 4.6, -2.8, 2.8, unit=40, xticks=[1, 2, 3, 4], yticks=[-2, 2]).curve(lambda x: sqrt(x), 0, 4, color="blue").curve(lambda x: -sqrt(x), 0, 4, color="blue", width=1).line(4, -2, 4, 2, color="orange", width=2)
    vf.text(2, 0.3, "y = √x rotated about the x-axis", 11)
    lesson(ch, "calculus-extensions", "Inverse trigonometric integrals and volumes of revolution",
        ["Differentiate $\\arcsin x$ and $\\arctan x$", "Integrate expressions giving $\\arcsin$ and $\\arctan$", "Find volumes of revolution about the x-axis"],
        [("formule", "Derivatives", "$\\frac{d}{dx}\\arcsin x=\\frac{1}{\\sqrt{1-x^2}}$ for $|x|<1$ and $\\frac{d}{dx}\\arctan x=\\frac{1}{1+x^2}$. With a constant $a>0$: $\\frac{d}{dx}\\arctan\\frac xa=\\frac{a}{a^2+x^2}$."),
         ("formule", "Standard integrals", "$\\int\\frac{dx}{\\sqrt{a^2-x^2}}=\\arcsin\\frac xa+C$ and $\\int\\frac{dx}{a^2+x^2}=\\frac1a\\arctan\\frac xa+C$. For a quadratic denominator complete the square first: $x^2+2x+2=(x+1)^2+1$, so $\\int\\frac{dx}{x^2+2x+2}=\\arctan(x+1)+C$."),
         ("formule", "Volume of revolution", "Rotating the curve $y=f(x)$ between $x=a$ and $x=b$ through $360\\degree$ about the x-axis gives volume $V=\\pi\\int_a^by^2\\,dx$. (Think of thin discs of area $\\pi y^2$.)"),
         ("pieges", "Common mistakes", "Forgetting the factor $\\frac1a$ in the arctan integral. Using $\\arcsin$ for $\\frac{1}{a^2+x^2}$ (it is arctan) or the reverse. Forgetting to square $y$ in the volume formula and to include $\\pi$. Angles from arcsin/arctan are in radians.")],
        [("Example 1", "Evaluate $\\int_0^1\\frac{1}{x^2+2x+2}dx$.", ["$x^2+2x+2=(x+1)^2+1$: the integral is $[\\arctan(x+1)]_0^1$.", "$=\\arctan2-\\arctan1=1.1071-0.7854=0.3218$."], "$\\arctan2-\\frac\\pi4\\approx0.322$"),
         ("Example 2", "The curve $y=\\sqrt x$ for $0\\leq x\\leq4$ is rotated about the x-axis. Find the volume.", ["$V=\\pi\\int_0^4(\\sqrt x)^2dx=\\pi\\int_0^4x\\,dx$.", "$=\\pi\\left[\\frac{x^2}{2}\\right]_0^4=8\\pi$."], "$8\\pi\\approx25.1$ cubic units", vf.fig())],
        [N("Evaluate $\\int_0^2\\frac{dx}{4+x^2}$ (4 d.p.).", round(pi / 8, 4), "$\\frac12\\left[\\arctan\\frac x2\\right]_0^2=\\frac12\\cdot\\frac\\pi4=\\frac\\pi8=0.3927$.", tol=0.0002),
         M("$\\frac{d}{dx}\\arctan2x=$", "$\\frac{2}{1+4x^2}$", ["$\\frac{1}{1+4x^2}$", "$\\frac{2}{1+2x^2}$", "$\\frac{1}{1+2x}$"], "Chain rule."),
         TF("$\\int\\frac{dx}{1+x^2}=\\ln(1+x^2)+C$.", False, "It is $\\arctan x+C$ ($\\ln(1+x^2)$ comes from $\\int\\frac{2x}{1+x^2}dx$).")],
        [M("$\\int\\frac{dx}{\\sqrt{9-x^2}}=$", "$\\arcsin\\frac x3+C$", ["$\\arctan\\frac x3+C$", "$3\\arcsin x+C$", "$\\frac13\\arcsin\\frac x3+C$"], "$a=3$."),
         O("Find $\\int_0^1\\frac{dx}{x^2+2x+2}$ exactly.", "Complete the square: $(x+1)^2+1$. $\\int=\\left[\\arctan(x+1)\\right]_0^1=\\arctan2-\\arctan1=\\arctan2-\\frac\\pi4\\approx0.322$.", ["Completes the square (1)", "Antiderivative arctan (1)", "Limits applied (1)"])],
        P("Answer the following.", [pn("Evaluate $\\int_0^{\\sqrt3}\\frac{dx}{1+x^2}$ (4 d.p.).", round(pi / 3, 4), "$\\arctan\\sqrt3=\\frac\\pi3=1.0472$.", tol=0.0002, pts=1), pn("Evaluate $\\int_0^1\\frac{dx}{\\sqrt{4-x^2}}$ (4 d.p.).", round(pi / 6, 4), "$\\arcsin\\frac12=\\frac\\pi6=0.5236$.", tol=0.0002, pts=1), pn("The curve $y=x^2$ for $0\\leq x\\leq1$ is rotated about the x-axis. Find the volume (3 d.p.).", round(pi / 5, 3), "$V=\\pi\\int_0^1x^4dx=\\frac\\pi5=0.628$.", tol=0.001, pts=2)]),
        [("$\\int\\frac{dx}{1+x^2}=$", "$\\arctan x+C$", ["$\\arcsin x+C$", "$\\ln(1+x^2)+C$", "$\\frac{1}{x}+C$"], "Standard."),
         ("$\\arctan1=$", "$\\frac\\pi4$", ["$\\frac\\pi2$", "$1$", "$\\frac\\pi3$"], "$\\tan\\frac\\pi4=1$."),
         ("The volume of revolution about the x-axis is", "$\\pi\\int y^2dx$", ["$\\pi\\int y\\,dx$", "$2\\pi\\int y\\,dx$", "$\\int y^2dx$"], "Discs."),
         ("$\\frac{d}{dx}\\arcsin x=$", "$\\frac{1}{\\sqrt{1-x^2}}$", ["$\\frac{1}{1+x^2}$", "$\\sqrt{1-x^2}$", "$-\\frac{1}{\\sqrt{1-x^2}}$"], "Standard."),
         ("$\\int\\frac{dx}{x^2+16}=$", "$\\frac14\\arctan\\frac x4+C$", ["$\\arctan\\frac x4+C$", "$4\\arctan\\frac x4+C$", "$\\frac1{16}\\arctan x+C$"], "$\\frac1a$ factor with $a=4$.")],
        ill=None, notes=[NOTE, "Volumes of revolution may be treated in Pure Mathematics in the Cameroon syllabus; kept here for completeness. Arc length and surface area are not covered."], ref=REF)

    # ------------------------------------------------------------ maclaurin ext
    close(sqrt(1.02), 1 + .01 - .00005, 1e-6); close(.5 * (.5 - 1) / 2, -1 / 8)
    assert .5 * (.5 - 1) * (.5 - 2) / 6 == 1 / 16
    close((exp(1e-3) - 1 - 1e-3) / 1e-6, .5, 1e-3); close((sin(1e-2) - 1e-2) / 1e-6, -1 / 6, 1e-3); close((1 - cos(1e-3)) / 1e-6, .5, 1e-3)
    close(tan(.1), .1 + .001 / 3, 1e-5)
    xx = 1e-3; close((exp(xx) * log(1 + xx) - xx) / xx ** 2, .5, 2e-3)
    mc = plot(-1.2, 1.2, -3, 3, curves=[("sin(x)/cos(x)", "blue", "tan x"), ("x+x^3/3", "orange", "x + x³/3")], xlabel="x")
    lesson(ch, "series-further", "Further series and limits",
        ["Expand $(1+x)^n$ for rational $n$", "Find series of products and quotients such as $\\tan x$", "Evaluate limits using series"],
        [("formule", "Binomial series", "For any rational $n$ and $|x|<1$: $(1+x)^n=1+nx+\\frac{n(n-1)}{2!}x^2+\\frac{n(n-1)(n-2)}{3!}x^3+\\cdots$. Example: $\\sqrt{1+x}=1+\\frac x2-\\frac{x^2}{8}+\\frac{x^3}{16}-\\cdots$ and $\\frac{1}{1+x}=1-x+x^2-\\cdots$. For $(a+bx)^n$ factor out $a^n$ first."),
         ("methode", "Products, quotients, composites", "Multiply or divide the standard series ($e^x$, $\\sin x$, $\\cos x$, $\\ln(1+x)$) and keep terms up to the required power. For $\\tan x=\\frac{\\sin x}{\\cos x}$: $\\left(x-\\frac{x^3}{6}\\right)\\left(1+\\frac{x^2}{2}+\\cdots\\right)=x+\\frac{x^3}{3}+\\cdots$"),
         ("methode", "Limits", "To find $\\lim_{x\\to0}\\frac{f(x)}{g(x)}$ when both $\\to0$, expand numerator and denominator in series and divide by the lowest power of $x$. Example: $\\frac{e^x-1-x}{x^2}=\\frac{\\frac{x^2}{2}+\\frac{x^3}{6}+\\cdots}{x^2}\\to\\frac12$."),
         ("pieges", "Common mistakes", "Using the binomial series outside $|x|<1$ (for example for $(1+x)^{1/2}$ at $x=3$). Dropping a term of the lower order that does not cancel. Mixing degrees and radians: the series for $\\sin x$ need radians.")],
        [("Example 1", "Expand $\\sqrt{1+x}$ to $x^3$ and estimate $\\sqrt{1.02}$.", ["$n=\\frac12$: $1+\\frac x2+\\frac{\\frac12\\left(-\\frac12\\right)}{2}x^2+\\frac{\\frac12\\left(-\\frac12\\right)\\left(-\\frac32\\right)}{6}x^3=1+\\frac x2-\\frac{x^2}{8}+\\frac{x^3}{16}$.", "With $x=0.02$: $1+0.01-0.00005=1.00995$ (true value 1.009950)."], "$1+\\frac x2-\\frac{x^2}{8}+\\frac{x^3}{16}$; $\\sqrt{1.02}\\approx1.00995$"),
         ("Example 2", "Find $\\lim_{x\\to0}\\frac{\\sin x-x}{x^3}$.", ["$\\sin x-x=-\\frac{x^3}{6}+\\frac{x^5}{120}-\\cdots$", "Divide by $x^3$: $-\\frac16+\\frac{x^2}{120}-\\cdots\\to-\\frac16$."], "$-\\frac16$", mc)],
        [N("Find the coefficient of $x^3$ in the expansion of $\\sqrt{1+x}$.", .0625, "$\\frac{\\frac12\\left(-\\frac12\\right)\\left(-\\frac32\\right)}{6}=\\frac{1}{16}=0.0625$.", tol=0.0001),
         M("$\\frac{1}{1+x}=$", "$1-x+x^2-x^3+\\cdots$", ["$1+x+x^2+x^3+\\cdots$", "$1-x-x^2-\\cdots$", "$1-\\frac x2+\\cdots$"], "$n=-1$."),
         TF("The series for $\\frac{1}{1-x}$, $1+x+x^2+\\cdots$, is valid for $|x|<1$.", True, "Geometric series.")],
        [M("$\\lim_{x\\to0}\\frac{1-\\cos x}{x^2}=$", "$\\frac12$", ["$0$", "$1$", "$\\infty$"], "$1-\\cos x=\\frac{x^2}{2}-\\cdots$."),
         O("Use series to find $\\lim_{x\\to0}\\frac{e^x-1-x}{x^2}$.", "$e^x-1-x=\\frac{x^2}{2}+\\frac{x^3}{6}+\\cdots$. Dividing by $x^2$ gives $\\frac12+\\frac x6+\\cdots$, which tends to $\\frac12$.", ["Series of $e^x$ (1)", "Divides by $x^2$ (1)", "Limit $\\frac12$ (1)"])],
        P("Consider $e^x\\ln(1+x)$.", [pn("Find the coefficient of $x^2$ in its series.", .5, "$\\left(1+x+\\frac{x^2}{2}\\right)\\left(x-\\frac{x^2}{2}+\\frac{x^3}{3}\\right)$: $x^2$ terms: $-\\frac12+1=\\frac12$.", tol=0.001, pts=1), pn("Find the coefficient of $x^3$ (4 d.p.).", .3333, "$x^3$ terms: $\\frac13-\\frac12+\\frac12=\\frac13$.", tol=0.0003, pts=2), pn("Find $\\lim_{x\\to0}\\frac{e^x\\ln(1+x)-x}{x^2}$.", .5, "The numerator is $\\frac{x^2}{2}+\\frac{x^3}{3}+\\cdots$.", tol=0.001, pts=1)]),
        [("$\\tan x=$", "$x+\\frac{x^3}{3}+\\cdots$", ["$x-\\frac{x^3}{3}+\\cdots$", "$1+x^2+\\cdots$", "$x+\\frac{x^2}{2}+\\cdots$"], "$\\frac{\\sin x}{\\cos x}$."),
         ("$(1+x)^{-2}=$", "$1-2x+3x^2-\\cdots$", ["$1+2x+3x^2$", "$1-x+x^2$", "$1-2x+x^2$"], "$n=-2$: $\\frac{(-2)(-3)}{2}=3$."),
         ("The binomial series for rational $n$ is valid when", "$|x|<1$", ["$x>1$", "all $x$", "$x=0$ only"], "Convergence."),
         ("To evaluate $0/0$ limits with series we", "divide by the lowest power of $x$", ["set $x=0$ at once", "differentiate once", "multiply by $x$"], "Method."),
         ("$\\sqrt{1+x}\\approx$ for small $x$", "$1+\\frac x2$", ["$1+x$", "$1+2x$", "$x$"], "First two terms.")],
        ill=None, notes=[NOTE], ref=REF)

    # ------------------------------------------------------------ mechanics & stats
    ch = p.chapter("applications", "Further mechanics and statistics", "Cameroon GCE Board — Advanced Level Further Mathematics, Mechanics and Statistics (to be checked)")
    w = 3; close(2 * pi / w, 2.0944, 1e-4); close(.2 * w, .6); close(.2 * w * w, 1.8)
    w2 = 2 * pi / 4; close(w2 * sqrt(.25 - .09), .6283, 1e-4)
    w3 = 2 * pi / pi; close(w3, 2); close(.3 * w3, .6); close(.3 * w3 ** 2, 1.2); close(w3 * sqrt(.09 - .0225), .5196, 1e-4)
    close(2 * pi / 4, pi / 2); close(sqrt(8 / .5), 4); close(2 * pi / 4, 1.5708, 1e-4); close(2 * pi / 5, 2 * pi / 5)
    shm = plot(0, 3.2, -0.4, 0.4, curves=[("0.3*sin(2x)", "blue", None)], segments=[(0, 0.3, 3.2, 0.3, "grey", True, "amplitude 0.3")], xlabel="t (s)", ylabel="x (m)")
    lesson(ch, "shm", "Simple harmonic motion",
        ["Recognise and solve $\\ddot x=-\\omega^2x$", "Use the formulae for speed, acceleration and period", "Model a mass on a spring"],
        [("formule", "SHM", "A particle performs SHM about $O$ when its acceleration is $\\ddot x=-\\omega^2x$ (towards $O$ and proportional to the displacement). Solutions: $x=a\\sin(\\omega t+\\phi)$ or $x=a\\cos(\\omega t+\\phi)$. Amplitude $a$, period $T=\\frac{2\\pi}{\\omega}$, frequency $\\frac1T$."),
         ("formule", "Speed and acceleration", "$v^2=\\omega^2(a^2-x^2)$. Maximum speed $a\\omega$ at the centre $x=0$ (where the acceleration is 0). Maximum acceleration $a\\omega^2$ at the ends $x=\\pm a$ (where the speed is 0). Derivation: $v=\\frac{dx}{dt}$ and $\\ddot x=v\\frac{dv}{dx}$."),
         ("methode", "Springs", "A mass $m$ on a spring of stiffness $k$ (Hooke's law $F=kx$) gives $m\\ddot x=-kx$, so $\\omega^2=\\frac km$ and $T=2\\pi\\sqrt{\\frac mk}$. Always identify the equilibrium position as the centre of the motion."),
         ("pieges", "Common mistakes", "Maximum acceleration is at the ENDS, not at the centre. $\\omega$ is in rad/s, not the frequency. The period does not depend on the amplitude.")],
        [("Example 1", "A particle has displacement $x=0.2\\sin3t$ (metres). Find the period, maximum speed and maximum acceleration.", ["$\\omega=3$: $T=\\frac{2\\pi}{3}=2.09$ s.", "Max speed $a\\omega=0.2\\times3=0.6$ m/s; max acceleration $a\\omega^2=0.2\\times9=1.8$ m/s²."], "$T=2.09$ s; 0.6 m/s; 1.8 m/s²"),
         ("Example 2", "A particle in SHM has amplitude 0.5 m and period 4 s. Find its speed when 0.3 m from the centre.", ["$\\omega=\\frac{2\\pi}{4}=\\frac\\pi2$.", "$v=\\omega\\sqrt{a^2-x^2}=\\frac\\pi2\\sqrt{0.25-0.09}=\\frac\\pi2\\times0.4=0.628$ m/s."], "0.628 m/s", shm)],
        [N("Find the period (s, 4 d.p.) of SHM with $\\omega=4$ rad/s.", round(pi / 2, 4), "$T=\\frac{2\\pi}{4}=\\frac\\pi2=1.5708$.", tol=0.0002),
         M("The maximum speed in SHM of amplitude $a$ and angular frequency $\\omega$ is", "$a\\omega$", ["$a\\omega^2$", "$\\frac a\\omega$", "$a^2\\omega$"], "At the centre: $v=\\omega\\sqrt{a^2-0}$."),
         TF("The acceleration in SHM is greatest at the centre of the motion.", False, "It is zero at the centre and greatest ($a\\omega^2$) at the ends.")],
        [M("A particle satisfies $\\ddot x=-25x$. Its period is", "$\\frac{2\\pi}{5}$ s", ["$\\frac{2\\pi}{25}$ s", "$10\\pi$ s", "$5$ s"], "$\\omega^2=25$."),
         N("A 0.5 kg mass hangs on a spring of stiffness 8 N/m and oscillates. Find the period in seconds (4 d.p.).", round(pi / 2, 4), "$\\omega^2=\\frac km=16$, $\\omega=4$, $T=\\frac{2\\pi}{4}=1.5708$ s.", tol=0.0002)],
        P("A trolley oscillates in SHM with amplitude 0.3 m and period $\\pi$ s.", [pn("Find $\\omega$ (rad/s).", 2, "$\\omega=\\frac{2\\pi}{\\pi}$.", pts=1), pn("Find the maximum speed (m/s).", .6, "$a\\omega=0.3\\times2$.", tol=0.001, pts=1), pn("Find the maximum acceleration (m/s²).", 1.2, "$a\\omega^2=0.3\\times4$.", tol=0.001, pts=1), pn("Find the speed when $x=0.15$ m (m/s, 3 d.p.).", .52, "$2\\sqrt{0.09-0.0225}=2(0.2598)=0.520$.", tol=0.001, pts=1)], figure=shm),
        [("SHM satisfies", "$\\ddot x=-\\omega^2x$", ["$\\ddot x=\\omega^2x$", "$\\dot x=-\\omega x$", "$\\ddot x=$ constant"], "Restoring acceleration."),
         ("Period of SHM is", "$\\frac{2\\pi}{\\omega}$", ["$2\\pi\\omega$", "$\\frac{\\omega}{2\\pi}$", "$\\pi\\omega$"], "Definition."),
         ("At the ends of the motion the speed is", "zero", ["maximum", "$a\\omega$", "$\\frac{a}{\\omega}$"], "Turning points."),
         ("For a spring $\\omega^2=$", "$\\frac km$", ["$km$", "$\\frac mk$", "$k$"], "$m\\ddot x=-kx$."),
         ("The period of SHM", "does not depend on the amplitude", ["increases with amplitude", "decreases with amplitude", "is $a\\omega$"], "Isochronous.")],
        ill=None, notes=[NOTE, "Elastic strings and springs (Hooke's law, elastic potential energy), variable-force work and vertical circles are NOT covered here."], ref="Cameroon GCE Board — Advanced Level Further Mathematics, Mechanics (to be checked)")

    # continuous RV
    close(I(lambda x: x * x / 9, 0, 3), 1); close(I(lambda x: x ** 3 / 9, 0, 3), 2.25); close(I(lambda x: x ** 4 / 9, 0, 3), 5.4); close(5.4 - 2.25 ** 2, .3375, 1e-12)
    close(3 / 2 ** (1 / 3), 2.3811, 1e-4); close(1 - .25, .75); close(I(lambda x: x * 2 * x, 0, 1), 2 / 3, 1e-6)
    close(I(lambda x: (4 - x) / 8, 0, 4), 1, 1e-6); close(I(lambda x: x * (4 - x) / 8, 0, 4), 4 / 3, 1e-6)
    close(I(lambda x: 3 * x * x / 8, 0, 2), 1); close(I(lambda x: 3 * x * x / 8, 0, 1), .125); close(I(lambda x: x * 3 * x * x / 8, 0, 2), 1.5); close(4 ** (1 / 3), 1.5874, 1e-4); close(1 - .125, .875)
    pdf = plot(0, 3, -0.05, 1.05, curves=[("x^2/9", "blue", "f(x)")], segments=[(2.3811, 0, 2.3811, 0.63, "red", True, "median 2.38")], xlabel="x")
    lesson(ch, "continuous-rv", "Continuous random variables",
        ["Use a probability density function (pdf) and the cumulative distribution function", "Calculate mean, variance and median", "Find unknown constants"],
        [("definition", "pdf", "A continuous random variable $X$ has a pdf $f(x)\\geq0$ with $\\int_{-\\infty}^{\\infty}f(x)\\,dx=1$ and $P(a<X<b)=\\int_a^bf(x)\\,dx$. Note $P(X=a)=0$, and $f(x)$ may exceed 1 (it is a density, not a probability). The cumulative distribution function is $F(x)=P(X\\leq x)=\\int_{-\\infty}^xf(t)\\,dt$."),
         ("formule", "Mean, variance, median", "$E(X)=\\int x\\,f(x)\\,dx$, $E(X^2)=\\int x^2f(x)\\,dx$, $\\text{Var}(X)=E(X^2)-[E(X)]^2$. The median $m$ satisfies $F(m)=\\frac12$, i.e. $\\int_{-\\infty}^mf(x)dx=\\frac12$. The mode is where $f$ is maximum."),
         ("methode", "Method", "Find unknown constants by $\\int f=1$. Integrate only over the range where $f\\neq0$. For $P(X>c)$ use $1-F(c)$. For the median solve $F(m)=\\frac12$."),
         ("pieges", "Common mistakes", "Integrating over the wrong interval. Forgetting the factor $x$ in $E(X)$ ($\\int xf$, not $\\int f$). Using the mean when asked for the median.")],
        [("Example 1", "$f(x)=kx^2$ for $0\\leq x\\leq3$ (0 otherwise). Find $k$, $E(X)$ and $\\text{Var}(X)$.", ["$\\int_0^3kx^2dx=9k=1$, so $k=\\frac19$. $E(X)=\\int_0^3\\frac{x^3}{9}dx=\\frac{81}{36}=2.25$.", "$E(X^2)=\\int_0^3\\frac{x^4}{9}dx=\\frac{243}{45}=5.4$; $\\text{Var}=5.4-2.25^2=0.3375$."], "$k=\\frac19$; $E(X)=2.25$; $\\text{Var}(X)=0.3375$", pdf),
         ("Example 2", "Find the median of the same distribution.", ["$F(m)=\\int_0^m\\frac{x^2}{9}dx=\\frac{m^3}{27}=\\frac12$.", "$m^3=13.5$, so $m=\\sqrt[3]{13.5}=2.381$."], "$m\\approx2.381$")],
        [N("$f(x)=2x$ on $[0,1]$. Find $P(X>0.5)$.", .75, "$1-\\int_0^{0.5}2x\\,dx=1-0.25$."),
         M("For any pdf, $\\int_{-\\infty}^\\infty f(x)\\,dx$ equals", "1", ["0", "$E(X)$", "$\\text{Var}(X)$"], "Total probability."),
         TF("A pdf can never take values greater than 1.", False, "It is a density: e.g. $f(x)=2$ on $[0,\\frac12]$ is a valid pdf.")],
        [M("For $f(x)=2x$ on $[0,1]$, $E(X)=$", "$\\frac23$", ["$\\frac12$", "$1$", "$\\frac13$"], "$\\int_0^12x^2dx=\\frac23$."),
         O("$f(x)=k(4-x)$ for $0\\leq x\\leq4$. Find $k$ and $E(X)$.", "$\\int_0^4k(4-x)dx=k\\left[4x-\\frac{x^2}{2}\\right]_0^4=8k=1$, so $k=\\frac18$. $E(X)=\\int_0^4\\frac{x(4-x)}{8}dx=\\frac18\\left[2x^2-\\frac{x^3}{3}\\right]_0^4=\\frac18\\cdot\\frac{32}{3}=\\frac43$.", ["Finds $k=\\frac18$ (1)", "Sets up $\\int xf$ (1)", "$E(X)=\\frac43$ (1)"])],
        P("$X$ has pdf $f(x)=\\frac{3x^2}{8}$ for $0\\leq x\\leq2$ and 0 otherwise.", [pn("Find $P(X<1)$.", .125, "$\\int_0^1\\frac{3x^2}{8}dx=\\frac{1}{8}$.", tol=0.001, pts=1), pn("Find $E(X)$.", 1.5, "$\\int_0^2\\frac{3x^3}{8}dx=\\frac38\\cdot4=\\frac32$.", tol=0.001, pts=2), pn("Find the median (3 d.p.).", 1.587, "$F(m)=\\frac{m^3}{8}=\\frac12$, $m=\\sqrt[3]4=1.587$.", tol=0.001, pts=1)]),
        [("$P(a<X<b)$ equals", "$\\int_a^bf(x)dx$", ["$f(b)-f(a)$", "$f(b)$", "$\\sum f$"], "Area under the pdf."),
         ("$F(x)$ is", "$P(X\\leq x)$", ["$f'(x)$", "$P(X=x)$", "$E(X)$"], "Cumulative distribution function."),
         ("$f(x)=F'(x)$ means the pdf is", "the derivative of the cdf", ["the integral of the cdf", "the cdf squared", "constant"], "Fundamental theorem."),
         ("$P(X=3)$ for a continuous variable is", "0", ["$f(3)$", "1", "undefined"], "Area of a point."),
         ("The median $m$ satisfies", "$F(m)=\\frac12$", ["$m=E(X)$ always", "$m=0$", "$F(m)=1$"], "Half of the probability below.")],
        ill=None, notes=[NOTE, "Uniform and exponential distributions as named models, and chi-squared/other tests, are not covered."], ref="Cameroon GCE Board — Advanced Level Further Mathematics, Statistics (to be checked)")
