import math
from _maths import *
REF = "Cameroon GCE Board — Advanced Level Mathematics, Pure Mathematics (to be checked against the official syllabus)"
S2 = math.sqrt(2); S3 = math.sqrt(3)

def build(p):
    ch = p.chapter("algebra", "Algebra and functions", REF + " — algebra and functions")
    # ---------------------------------------------------------------- indices and surds
    ex1 = 16 ** 0.75 * 9 ** -0.5; assert abs(ex1 - 8 / 3) < 1e-12
    assert abs(6 / (3 - S3) - (3 + S3)) < 1e-12
    assert abs((2 + S3) / (2 - S3) - (7 + 4 * S3)) < 1e-9
    assert abs(1 / (math.sqrt(5) - 2) - (math.sqrt(5) + 2)) < 1e-12
    assert abs(math.sqrt(50) + math.sqrt(18) - 8 * S2) < 1e-12 and 125 ** (2 / 3) - 25 < 1e-9
    lesson(ch, "indices-surds", "Indices and surds",
        ["Use the laws of indices with integer and fractional powers", "Simplify surds and rationalise denominators"],
        [("definition", "Laws of indices", "For $a>0$: $a^m \\times a^n = a^{m+n}$, $a^m \\div a^n = a^{m-n}$, $(a^m)^n = a^{mn}$, $a^0 = 1$, $a^{-n} = \\frac{1}{a^n}$. Fractional powers are roots: $a^{1/n} = \\sqrt[n]{a}$ and $a^{m/n} = (\\sqrt[n]{a})^m$."),
         ("methode", "Surds", "A surd is an irrational root such as $\\sqrt{2}$. Rules: $\\sqrt{a}\\sqrt{b} = \\sqrt{ab}$ and $\\sqrt{a/b} = \\sqrt{a}/\\sqrt{b}$. Simplify by taking out square factors: $\\sqrt{72} = \\sqrt{36 \\times 2} = 6\\sqrt{2}$."),
         ("methode", "Rationalising the denominator", "To remove a surd from a denominator multiply top and bottom by the **conjugate**. The conjugate of $a + \\sqrt{b}$ is $a - \\sqrt{b}$, and $(a+\\sqrt{b})(a-\\sqrt{b}) = a^2 - b$ is rational."),
         ("pieges", "Common mistakes", "- $(a+b)^2 \\neq a^2 + b^2$: there is a middle term $2ab$.\n- $\\sqrt{a+b} \\neq \\sqrt{a} + \\sqrt{b}$.\n- $2^{-3}$ is $\\frac{1}{8}$, not $-8$.\n- $\\sqrt{x^2} = |x|$, not simply $x$.")],
        [("Example 1", "Evaluate $16^{3/4} \\times 9^{-1/2}$.", ["$16^{3/4} = (\\sqrt[4]{16})^3 = 2^3 = 8$.", "$9^{-1/2} = \\frac{1}{\\sqrt{9}} = \\frac{1}{3}$.", "Multiply: $8 \\times \\frac{1}{3}$."], "$\\frac{8}{3}$"),
         ("Example 2", "Rationalise $\\frac{6}{3-\\sqrt{3}}$.", ["Multiply top and bottom by the conjugate $3+\\sqrt{3}$.", "Denominator: $(3-\\sqrt{3})(3+\\sqrt{3}) = 9-3 = 6$.", "Numerator: $6(3+\\sqrt{3})$, so the fraction is $\\frac{6(3+\\sqrt{3})}{6}$."], "$3+\\sqrt{3}$")],
        [N("Evaluate $125^{2/3}$.", 25, "$125^{1/3}=5$, then $5^2=25$."),
         M("Simplify $\\sqrt{50} + \\sqrt{18}$.", "$8\\sqrt{2}$", ["$\\sqrt{68}$", "$8\\sqrt{2}\\,/\\,2$", "$15\\sqrt{2}$"], "$\\sqrt{50}=5\\sqrt{2}$ and $\\sqrt{18}=3\\sqrt{2}$; add the like surds to get $8\\sqrt{2}$."),
         TF("$(a+b)^2 = a^2 + b^2$ for all real $a,b$.", False, "False. $(a+b)^2 = a^2+2ab+b^2$; for example $(1+2)^2=9$ but $1+4=5$.")],
        [M("If $4^x = 8$, then $x$ equals", "$\\frac{3}{2}$", ["$2$", "$\\frac{2}{3}$", "$\\frac{1}{2}$"], "$4^x=2^{2x}$ and $8=2^3$, so $2x=3$ and $x=\\frac{3}{2}$."),
         O("Show that $\\frac{1}{\\sqrt{5}-2} = \\sqrt{5}+2$. Hence simplify $\\frac{1}{\\sqrt{5}-2} + \\frac{1}{\\sqrt{5}+2}$.", "Multiply by the conjugate: $\\frac{\\sqrt{5}+2}{(\\sqrt{5}-2)(\\sqrt{5}+2)} = \\frac{\\sqrt{5}+2}{5-4} = \\sqrt{5}+2$. In the same way $\\frac{1}{\\sqrt{5}+2} = \\sqrt{5}-2$. The sum is $(\\sqrt{5}+2)+(\\sqrt{5}-2) = 2\\sqrt{5}$.", ["Uses the conjugate correctly (1)", "Denominator becomes 1 (1)", "Final answer $2\\sqrt{5}$ (1)"])],
        P("Answer the following.", [pn("Solve $9^x = 27^{x-1}$ for $x$.", 3, "$3^{2x}=3^{3(x-1)}$, so $2x=3x-3$ and $x=3$.", pts=2), pn("Write $\\frac{2+\\sqrt{3}}{2-\\sqrt{3}}$ in the form $a+b\\sqrt{3}$. Find $a+b$.", 11, "Multiply by $2+\\sqrt{3}$: numerator $(2+\\sqrt{3})^2 = 7+4\\sqrt{3}$, denominator $4-3=1$. So $a=7$, $b=4$ and $a+b=11$.", pts=3)]),
        [("$2^3 \\times 2^4$ equals", "$2^7$", ["$2^{12}$", "$4^7$", "$2^1$"], "Add the powers when multiplying with the same base: $3+4=7$."),
         ("$9^{1/2}$ equals", "$3$", ["$4.5$", "$81$", "$\\frac{1}{3}$"], "A power of $\\frac12$ is a square root."),
         ("$3^{-2}$ equals", "$\\frac{1}{9}$", ["$-9$", "$-6$", "$\\frac{1}{6}$"], "A negative power means a reciprocal: $\\frac{1}{3^2}$."),
         ("$\\sqrt{12}$ simplifies to", "$2\\sqrt{3}$", ["$3\\sqrt{2}$", "$6$", "$4\\sqrt{3}$"], "$\\sqrt{12}=\\sqrt{4\\times3}=2\\sqrt{3}$."),
         ("$\\sqrt{3}\\times\\sqrt{12}$ equals", "$6$", ["$\\sqrt{15}$", "$12$", "$3\\sqrt{2}$"], "$\\sqrt{3\\times12}=\\sqrt{36}=6$.")],
        ill=(plot(-3, 3, 0, 9, curves=[("2^x", "blue", "2^x"), ("(1/2)^x", "orange", None)], points=[(0, 1, "(0, 1)", "red")], xlabel="x", ylabel="y"),
             "Graphs of $y=2^x$ (growth) and $y=(1/2)^x$ (decay) both pass through $(0,1)$ because $a^0=1$.", "Two exponential curves crossing the y-axis at 1, one rising and one falling."),
        notes=["Scope of surds/indices in the GCE Board syllabus (fractional and negative powers assumed standard) to be confirmed."], ref=REF)

    # ---------------------------------------------------------------- logarithms
    import math as m
    x5 = m.log(40) / m.log(5); x3 = m.log(20) / m.log(3)
    assert abs(5 ** x5 - 40) < 1e-9 and abs(3 ** x3 - 20) < 1e-9 and abs(x3 - 2.7268) < 1e-3
    n_ci = m.log(1.6) / m.log(1.04); assert 11.9 < n_ci < 12 and 500000 * 1.04 ** 11 < 800000 < 500000 * 1.04 ** 12
    x_l = 5; assert m.isclose(m.log2(x_l + 3) + m.log2(x_l - 3), 4)
    lesson(ch, "logarithms", "Logarithms and exponential equations",
        ["Convert between index and logarithm form", "Use the laws of logarithms", "Solve equations of the form $a^x = b$"],
        [("definition", "Definition and laws", "$\\log_a x = y$ means $a^y = x$ (with $a>0$, $a\\neq1$, $x>0$). Laws: $\\log_a(xy)=\\log_a x+\\log_a y$, $\\log_a\\frac{x}{y}=\\log_a x-\\log_a y$, $\\log_a x^k = k\\log_a x$. Also $\\log_a a=1$ and $\\log_a 1=0$."),
         ("methode", "Solving $a^x = b$", "Take logarithms of both sides (base 10 or $\\ln$): $x\\log a = \\log b$, so $x = \\frac{\\log b}{\\log a}$. The change of base formula is $\\log_a b = \\frac{\\log b}{\\log a}$."),
         ("pieges", "Common mistakes", "- $\\log(a+b)$ is NOT $\\log a + \\log b$.\n- $\\frac{\\log a}{\\log b}$ is NOT $\\log a - \\log b$.\n- In equations with logs, check every answer: the argument of a log must stay positive, so some roots must be rejected.")],
        [("Example 1", "Solve $\\log_2(x+3) + \\log_2(x-3) = 4$.", ["Combine: $\\log_2\\big((x+3)(x-3)\\big)=4$, so $x^2-9 = 2^4 = 16$.", "$x^2 = 25$, so $x=5$ or $x=-5$.", "The arguments must be positive: $x>3$. So $x=-5$ is rejected."], "$x=5$"),
         ("Example 2", "Solve $3^x = 20$ to 3 significant figures.", ["Take $\\log$ of both sides: $x\\log 3=\\log 20$.", "$x=\\frac{\\log 20}{\\log 3}=\\frac{1.3010}{0.4771}$."], "$x \\approx 2.73$")],
        [N("Evaluate $\\log_2 32$.", 5, "$2^5=32$."),
         M("$\\log 4 + \\log 25$ (base 10) equals", "$2$", ["$\\log 29$", "$1$", "$100$"], "$\\log(4\\times25)=\\log100=2$."),
         TF("$\\log(a+b) = \\log a + \\log b$.", False, "False: it is $\\log(ab)$ that equals $\\log a+\\log b$. Try $a=b=1$: $\\log 2\\neq 0$.")],
        [M("Solve $\\log_3 x + \\log_3(x-2) = 1$.", "$x=3$", ["$x=-1$", "$x=3$ and $x=-1$", "$x=\\frac{1}{2}$"], "$x(x-2)=3$ gives $x^2-2x-3=0$, so $x=3$ or $-1$; $x=-1$ makes a log undefined, so only $x=3$ (check: $\\log_3 3+\\log_3 1=1$)."),
         N("Solve $5^x = 40$. Give $x$ to 2 decimal places.", round(x5, 2), "$x=\\frac{\\ln 40}{\\ln 5}\\approx 2.292$.", tol=0.01)],
        P("500 000 FCFA is saved in a Douala cooperative at 4% compound interest per year, so after $n$ years it is worth $500000\\times1.04^n$.", [pn("Solve $500000\\times1.04^n = 800000$ for $n$ (2 d.p.).", round(n_ci, 2), "$1.04^n=1.6$, so $n=\\frac{\\ln1.6}{\\ln1.04}\\approx 11.98$.", tol=0.02, pts=3), pm("After how many whole years does the savings first exceed 800 000 FCFA?", "12", ["11", "13", "10"], "At $n=11$ the value is about 769 000 (below), at $n=12$ about 800 500 (above).", pts=1)]),
        [("$\\log_{10}1000$ equals", "$3$", ["$10$", "$100$", "$\\frac{1}{3}$"], "$10^3=1000$."),
         ("$\\log_a a^5$ equals", "$5$", ["$a^5$", "$5a$", "$\\log 5$"], "$\\log_a a^5 = 5\\log_a a=5$."),
         ("$2\\log 3 + \\log 4$ equals", "$\\log 36$", ["$\\log 10$", "$\\log 24$", "$\\log 12$"], "$2\\log3=\\log9$; $\\log9+\\log4=\\log36$."),
         ("If $2^x = 7$ then $x$ equals", "$\\frac{\\log 7}{\\log 2}$", ["$\\frac{\\log 2}{\\log 7}$", "$\\log 7-\\log 2$", "$\\frac{7}{2}$"], "Take logs: $x\\log2=\\log7$."),
         ("$\\log_2 \\frac{1}{8}$ equals", "$-3$", ["$3$", "$-\\frac{1}{3}$", "$\\frac{1}{8}$"], "$2^{-3}=\\frac18$.")],
        ill=(plot(0.1, 8.5, -3, 3.5, curves=[("ln(x)/ln(2)", "blue", None)], points=[(1, 0, "(1, 0)", "red"), (2, 1, "(2, 1)", "red"), (8, 3, "(8, 3)", "red")], xlabel="x"),
             "The graph of $y=\\log_2 x$: it passes through $(1,0)$, $(2,1)$, $(8,3)$ and is undefined for $x\\leq0$.", "Increasing logarithm curve through (1,0), (2,1) and (8,3) with the y-axis as a vertical asymptote."),
        notes=["Natural logarithm (ln) is assumed known from this lesson; the exponential function itself is treated in the 'Exponential and logarithmic functions' lesson."], ref=REF)

    # ---------------------------------------------------------------- quadratics
    assert (3 ** 2 - 4 * 1 * 3 * 0) and 4 - 60 < 0
    A = Axes(-1, 7, -6, 12, xticks=[1, 3, 5, 7], yticks=[-5, 5, 10]).curve(lambda x: x * x - 6 * x + 4).pt(3, -5, "(3, −5)", dy=16)
    assert all(abs(x * (20 - x) - 91) < 1e-9 for x in (7, 13)) and 20 * 10 - 100 == 100
    lesson(ch, "quadratics", "Quadratic functions and equations",
        ["Complete the square and find the turning point", "Use the discriminant to describe the roots", "Use sum and product of roots"],
        [("definition", "Completing the square", "$ax^2+bx+c = a\\left(x+\\frac{b}{2a}\\right)^2 + c-\\frac{b^2}{4a}$. The parabola has its turning point at $x=-\\frac{b}{2a}$, a minimum if $a>0$ and a maximum if $a<0$."),
         ("formule", "Discriminant", "For $ax^2+bx+c=0$ the discriminant is $\\Delta=b^2-4ac$. If $\\Delta>0$: two distinct real roots; $\\Delta=0$: one repeated root; $\\Delta<0$: no real roots. Roots: $x=\\frac{-b\\pm\\sqrt{\\Delta}}{2a}$."),
         ("propriete", "Sum and product of roots", "If $\\alpha$ and $\\beta$ are the roots of $ax^2+bx+c=0$ then $\\alpha+\\beta=-\\frac{b}{a}$ and $\\alpha\\beta=\\frac{c}{a}$. These let you build a new equation from given roots."),
         ("attention", "Watch out", "Equal roots means $\\Delta=0$ exactly. Always move everything to one side ($=0$) before reading off $a$, $b$, $c$. Divide by $a$ BEFORE completing the square.")],
        [("Example 1", "Write $x^2-6x+4$ in the form $(x-p)^2+q$ and state the minimum value.", ["Half of $-6$ is $-3$: $x^2-6x=(x-3)^2-9$.", "So $x^2-6x+4=(x-3)^2-9+4=(x-3)^2-5$.", "A square is at least 0, so the minimum is $-5$, at $x=3$."], "$(x-3)^2-5$; minimum $-5$ at $x=3$", A.fig()),
         ("Example 2", "Find the values of $k$ for which $x^2+kx+9=0$ has equal roots.", ["Equal roots: $\\Delta=k^2-4(1)(9)=0$.", "$k^2=36$."], "$k=6$ or $k=-6$")],
        [N("Find the larger root of $2x^2-5x-3=0$.", 3, "$(2x+1)(x-3)=0$, roots $-\\frac12$ and $3$."),
         M("The discriminant of $3x^2-2x+5$ is", "$-56$", ["$64$", "$-16$", "$56$"], "$b^2-4ac=4-60=-56<0$: no real roots."),
         TF("The sum of the roots of $x^2-7x+10=0$ is 7.", True, "$-\\frac{b}{a}=7$ (roots 2 and 5).")],
        [M("The equation $x^2+4x+k=0$ has two distinct real roots when", "$k<4$", ["$k>4$", "$k<-4$", "$k=4$"], "$\\Delta=16-4k>0$ gives $k<4$."),
         O("Show that $x^2+2x+5>0$ for every real $x$.", "$x^2+2x+5=(x+1)^2+4$. Since $(x+1)^2\\geq0$, the expression is at least 4, hence positive. (Equivalently $\\Delta=4-20<0$ and the parabola opens upwards.)", ["Completes the square correctly (1)", "States $(x+1)^2\\geq0$ (1)", "Concludes the expression $\\geq4>0$ (1)"])],
        P("A rectangular plot in Bamenda has perimeter 40 m, and one side is $x$ m.", [pn("Its area is 91 m². Find the shorter side (m).", 7, "Other side $20-x$: $x(20-x)=91$, so $x^2-20x+91=0$, $(x-7)(x-13)=0$. Sides 7 m and 13 m.", pts=2), pn("Find the largest possible area (m²) of such a plot.", 100, "$A=20x-x^2=100-(x-10)^2\\leq100$, equal when $x=10$ (a square).", pts=2)]),
        [("The turning point of $y=x^2-4x+9$ has $x=$", "$2$", ["$-2$", "$4$", "$9$"], "$x=-\\frac{b}{2a}=2$."),
         ("If $\\Delta<0$ the parabola", "does not cross the x-axis", ["touches the x-axis", "crosses the x-axis twice", "opens downwards"], "No real roots means no x-intercepts."),
         ("Roots of $x^2-3x-10=0$ are", "$5$ and $-2$", ["$5$ and $2$", "$-5$ and $2$", "$10$ and $-1$"], "$(x-5)(x+2)$."),
         ("Product of the roots of $2x^2+5x-3=0$ is", "$-\\frac{3}{2}$", ["$\\frac{3}{2}$", "$-\\frac{5}{2}$", "$-3$"], "$\\frac{c}{a}=-\\frac32$."),
         ("$x^2+6x+1=(x+3)^2+\\ldots$", "$-8$", ["$1$", "$8$", "$-9$"], "$(x+3)^2=x^2+6x+9$, so subtract 8.")],
        ill=None, notes=[], ref=REF)

    # ---------------------------------------------------------------- inequalities
    from _kit import num_line
    assert 14 ** 2 < 200 < 15 ** 2 and [x for x in range(-5, 6) if x * x < 10] == list(range(-3, 4))
    lesson(ch, "inequalities", "Inequalities and the modulus",
        ["Solve linear and quadratic inequalities", "Solve simple modulus equations and inequalities"],
        [("methode", "Quadratic inequalities", "Find the roots of the related equation, sketch the parabola, then read the answer. For $ax^2+bx+c>0$ with $a>0$: outside the roots; for $<0$: between the roots. Write answers like $x<-2$ or $x>3$."),
         ("definition", "Modulus", "$|x|=x$ if $x\\geq0$ and $|x|=-x$ if $x<0$; it is the distance from 0. $|x-a|<r$ means $a-r<x<a+r$; $|x-a|>r$ means $x<a-r$ or $x>a+r$."),
         ("pieges", "Common mistakes", "- Multiplying or dividing by a NEGATIVE number reverses the inequality sign.\n- Never square-root or divide by $x$ without checking its sign.\n- $|x|=a$ has two solutions for $a>0$; check each against the original equation.")],
        [("Example 1", "Solve $x^2-x-6>0$.", ["Factorise: $(x-3)(x+2)>0$, roots $x=3$ and $x=-2$.", "The parabola opens upwards, so it is positive outside the roots."], "$x<-2$ or $x>3$", num_line(-4, 5, [], step=1, hollow=[-2, 3], arrow_from=None, arrow_to=-2)),
         ("Example 2", "Solve $|2x-1|\\leq5$.", ["$-5\\leq2x-1\\leq5$.", "Add 1: $-4\\leq2x\\leq6$; divide by 2."], "$-2\\leq x\\leq3$")],
        [N("How many integers satisfy $x^2<10$?", 7, "$-\\sqrt{10}<x<\\sqrt{10}$, about $-3.16<x<3.16$: the integers are $-3,\\dots,3$, which is 7."),
         M("Solve $3x-2>10$.", "$x>4$", ["$x<4$", "$x>\\frac{8}{3}$", "$x>12$"], "$3x>12$."),
         TF("If $a>b$ then $a^2>b^2$.", False, "False when negatives are involved: $1>-2$ but $1<4$.")],
        [M("Solve $(x-1)(x-4)\\leq0$.", "$1\\leq x\\leq4$", ["$x\\leq1$ or $x\\geq4$", "$1<x<4$", "$x\\leq4$"], "Upward parabola is $\\leq0$ between (and at) its roots."),
         O("Solve $|x-3|=2x+1$.", "Case $x\\geq3$: $x-3=2x+1$ gives $x=-4$, rejected (not $\\geq3$). Case $x<3$: $3-x=2x+1$ gives $x=\\frac23$, valid. Check: $|\\frac23-3|=\\frac73$ and $2\\cdot\\frac23+1=\\frac73$. Answer $x=\\frac23$.", ["Splits into two cases (1)", "Rejects $x=-4$ (1)", "Finds and checks $x=\\frac23$ (1)"])],
        P("Answer the following.", [pm("Solve $2x^2-3x-2\\geq0$.", "$x\\leq-\\frac12$ or $x\\geq2$", ["$-\\frac12\\leq x\\leq2$", "$x\\leq-2$ or $x\\geq\\frac12$", "$x\\geq2$"], "$(2x+1)(x-2)\\geq0$; roots $-\\frac12$ and 2; upward parabola, so outside.", pts=2), pn("Find the smallest positive integer $n$ with $n^2>200$.", 15, "$14^2=196<200$ and $15^2=225>200$.", pts=2), pt("The solution of $x^2\\leq9$ is $x\\leq3$.", False, "It is $-3\\leq x\\leq3$.")]),
        [("Solve $-2x<6$.", "$x>-3$", ["$x<-3$", "$x>3$", "$x<3$"], "Dividing by $-2$ reverses the sign."),
         ("$|x|<4$ means", "$-4<x<4$", ["$x<4$", "$x<-4$ or $x>4$", "$x>-4$"], "Distance from 0 less than 4."),
         ("$x^2>25$ means", "$x<-5$ or $x>5$", ["$x>5$", "$-5<x<5$", "$x>\\pm5$"], "Outside the roots."),
         ("$|x-2|=3$ has solutions", "$x=5$ and $x=-1$", ["$x=5$ only", "$x=1$ and $x=-5$", "$x=3$"], "$x-2=3$ or $x-2=-3$."),
         ("The inequality $x^2+1<0$ has", "no real solutions", ["$x<-1$", "$-1<x<1$", "all real solutions"], "$x^2+1\\geq1$ always.")],
        ill=(num_line(-4, 5, [], step=1, hollow=[-2, 3], arrow_from=-2, arrow_to=3), "Number line for $-2<x<3$: open circles at the ends are not included.", "Number line from -4 to 5 with open circles at -2 and 3 and the segment between them shaded."),
        notes=[], ref=REF)

    # ---------------------------------------------------------------- polynomials
    f = lambda x: x ** 3 - 2 * x ** 2 - 5 * x + 6
    assert f(1) == f(3) == f(-2) == 0
    assert 2 * (-2) ** 3 + (-2) ** 2 - 3 * (-2) + 5 == -1
    g = lambda x: x ** 3 - 6 * x ** 2 + 11 * x - 6
    assert g(2) == 0 and g(-1) != 0 and g(-2) != 0 and g(4) != 0
    assert 2 ** 3 + 2 * 4 - 10 - 6 == 0 and (-1) ** 3 + 2 - 5 * (-1) * -1 * 0 == 1
    assert (1 + 6 - 13 + 6 == 0) and (8 + 24 - 26 + 6 == 12)
    h = lambda x: 2 * x ** 3 - 3 * x ** 2 - 11 * x + 6
    assert h(3) == 0 and h(.5) == 0 and h(-2) == 0
    F = plot(-2.5, 3.5, -12, 12, curves=[("x^3-2x^2-5x+6", "blue", None)], points=[(1, 0, "1", "red"), (3, 0, "3", "red"), (-2, 0, "-2", "red")], xlabel="x")
    lesson(ch, "polynomials", "Polynomials: factor and remainder theorems",
        ["Divide polynomials", "Use the remainder and factor theorems", "Factorise cubics and solve $f(x)=0$"],
        [("propriete", "Remainder theorem", "When a polynomial $f(x)$ is divided by $(x-a)$ the remainder is $f(a)$. For $(ax-b)$ the remainder is $f\\left(\\frac{b}{a}\\right)$."),
         ("propriete", "Factor theorem", "$(x-a)$ is a factor of $f(x)$ if and only if $f(a)=0$. Then $a$ is a root of $f(x)=0$ and the graph crosses the x-axis at $x=a$."),
         ("methode", "Factorising a cubic", "1. Try $x=\\pm1,\\pm2,\\pm3,\\dots$ (factors of the constant term) until $f(a)=0$.\n2. Divide $f(x)$ by $(x-a)$ (long division or inspection) to get a quadratic.\n3. Factorise the quadratic.\n4. List all roots."),
         ("pieges", "Common mistakes", "A factor $(x+3)$ corresponds to the root $x=-3$, not $+3$. Remember the sign: test $f(-3)$ for the factor $(x+3)$.")],
        [("Example 1", "Factorise $f(x)=x^3-2x^2-5x+6$ completely.", ["$f(1)=1-2-5+6=0$, so $(x-1)$ is a factor.", "Divide: $f(x)=(x-1)(x^2-x-6)$.", "$x^2-x-6=(x-3)(x+2)$."], "$f(x)=(x-1)(x-3)(x+2)$", F),
         ("Example 2", "Find the remainder when $2x^3+x^2-3x+5$ is divided by $(x+2)$.", ["Remainder theorem with $a=-2$: $f(-2)=2(-8)+4+6+5$.", "$=-16+15$."], "Remainder $-1$")],
        [N("Find $f(2)$ for $f(x)=x^3-4x+1$.", 1, "$8-8+1=1$."),
         M("Which is a factor of $x^3-6x^2+11x-6$?", "$(x-2)$", ["$(x+1)$", "$(x+2)$", "$(x-4)$"], "Only $f(2)=8-24+22-6=0$. The others give $-24$, $-60$, $6$."),
         TF("If $f(3)=0$ then $(x+3)$ is a factor of $f(x)$.", False, "False: $f(3)=0$ means $(x-3)$ is a factor.")],
        [M("$x^3+kx^2-5x-6$ is divisible by $(x+1)$. Find $k$.", "$k=2$", ["$k=0$", "$k=-2$", "$k=10$"], "$f(-1)=-1+k+5-6=k-2=0$."),
         O("$f(x)=x^3+ax^2+bx+6$ has $(x-1)$ as a factor and leaves remainder 12 when divided by $(x-2)$. Find $a$ and $b$.", "$f(1)=0$: $a+b=-7$. $f(2)=12$: $8+4a+2b+6=12$, so $2a+b=-1$. Subtract: $a=6$, then $b=-13$. Check $f(2)=8+24-26+6=12$.", ["Two equations from the two theorems (2)", "Solves $a=6$, $b=-13$ (1)"])],
        P("Let $f(x)=2x^3-3x^2-11x+6$.", [pn("Find $f(3)$.", 0, "$54-27-33+6=0$, so $(x-3)$ is a factor.", pts=1), pn("Factorise $f(x)=(x-3)(2x-1)(x+2)$ and find the sum of the three roots.", 1.5, "Dividing by $(x-3)$ gives $2x^2+3x-2=(2x-1)(x+2)$. Roots $3,\\frac12,-2$; sum $=\\frac32$, matching $-\\frac{b}{a}=\\frac32$.", tol=0.001, pts=3)]),
        [("The remainder when $x^2+3x+1$ is divided by $(x-1)$ is", "$5$", ["$1$", "$3$", "$-1$"], "$f(1)=5$."),
         ("If $f(-2)=0$, a factor is", "$(x+2)$", ["$(x-2)$", "$(2x+1)$", "$(x+1)$"], "$a=-2$ gives $(x-(-2))$."),
         ("A cubic has at most", "3 real roots", ["2 real roots", "4 real roots", "1 real root"], "Degree 3."),
         ("$x^3-x=x(x-1)(x+\\ldots)$", "$1$", ["$-1$", "$0$", "$2$"], "$x^3-x=x(x^2-1)$."),
         ("The roots of $(x-1)(x-3)(x+2)=0$ are", "$1,3,-2$", ["$-1,-3,2$", "$1,3,2$", "$1,-3,-2$"], "Each factor gives a root.")],
        ill=None, notes=[], ref=REF)

    # ---------------------------------------------------------------- functions
    fg = lambda x: 2 * (x * x) + 3
    assert fg(-2) == 11
    f_ = lambda x: (x + 1) / (x - 2); fi = lambda x: (2 * x + 1) / (x - 1)
    assert abs(fi(f_(3)) - 3) < 1e-12 and abs(f_(fi(5)) - 5) < 1e-12
    assert 3 * (2 + 4) - 1 == 17
    ff = lambda x: 2 * x - 1; gg = lambda x: x * x + 1
    assert ff(gg(3)) == 19 and [x for x in (2, -1) if gg(ff(x)) == 10] == [2, -1] and ff(gg(2)) != gg(ff(2))
    G = plot(-5, 5, -5, 5, curves=[("2x+3", "blue", "f"), ("(x-3)/2", "orange", "f inverse"), ("x", "grey", None)], xlabel="x")
    lesson(ch, "functions", "Functions: composite and inverse",
        ["Find domain and range", "Form composite functions $fg$", "Find inverse functions and relate their graphs"],
        [("definition", "Function, domain, range", "A function $f$ gives exactly one output for each input. The **domain** is the set of allowed inputs, the **range** the set of outputs. Examples: $\\sqrt{x-3}$ needs $x\\geq3$; $\\frac{1}{x-5}$ needs $x\\neq5$."),
         ("definition", "Composite function", "$fg(x)=f(g(x))$: apply $g$ first, then $f$. In general $fg\\neq gf$. Work from the inside out."),
         ("methode", "Finding an inverse", "Write $y=f(x)$, make $x$ the subject, then swap letters: $f^{-1}(x)$. The graph of $y=f^{-1}(x)$ is the reflection of $y=f(x)$ in the line $y=x$. Only one-to-one functions have an inverse; restrict the domain if needed. The domain of $f^{-1}$ is the range of $f$."),
         ("pieges", "Common mistakes", "$f^{-1}(x)$ is NOT $\\frac{1}{f(x)}$. For $fg(x)$ do $g$ first. A function like $x^2$ has no inverse on all of $\\mathbb{R}$ because two inputs give one output.")],
        [("Example 1", "$f(x)=2x+3$, $g(x)=x^2$. Find $fg(x)$, $gf(x)$ and $fg(-2)$.", ["$fg(x)=f(x^2)=2x^2+3$.", "$gf(x)=g(2x+3)=(2x+3)^2$.", "$fg(-2)=2(4)+3=11$."], "$fg=2x^2+3$, $gf=(2x+3)^2$, $fg(-2)=11$", G),
         ("Example 2", "Find the inverse of $f(x)=\\frac{x+1}{x-2}$, $x\\neq2$.", ["$y(x-2)=x+1$, so $yx-2y=x+1$ and $x(y-1)=2y+1$.", "$x=\\frac{2y+1}{y-1}$. Swap letters. Check: $f(3)=4$ and $f^{-1}(4)=\\frac{9}{3}=3$."], "$f^{-1}(x)=\\frac{2x+1}{x-1}$, $x\\neq1$")],
        [N("$f(x)=3x-1$, $g(x)=x+4$. Find $fg(2)$.", 17, "$g(2)=6$, $f(6)=17$."),
         M("The inverse of $f(x)=2x-5$ is", "$\\frac{x+5}{2}$", ["$\\frac{x-5}{2}$", "$\\frac{1}{2x-5}$", "$2x+5$"], "$y=2x-5$ gives $x=\\frac{y+5}{2}$."),
         TF("Every function has an inverse function on its whole natural domain.", False, "False. $f(x)=x^2$ gives the same output for 2 and $-2$, so it is not one-to-one.")],
        [M("The largest domain of $\\frac{\\sqrt{x-3}}{x-5}$ is", "$x\\geq3$, $x\\neq5$", ["$x>3$", "$x\\neq5$", "$x\\geq5$"], "Square root: $x\\geq3$; denominator: $x\\neq5$."),
         O("$f(x)=x^2-4x$ for $x\\geq2$. Find $f^{-1}(x)$ and state its domain.", "$y=(x-2)^2-4$, so $(x-2)^2=y+4$ and, since $x\\geq2$, $x=2+\\sqrt{y+4}$. So $f^{-1}(x)=2+\\sqrt{x+4}$. The range of $f$ is $y\\geq-4$, so the domain of $f^{-1}$ is $x\\geq-4$.", ["Completes the square (1)", "Chooses the + root because $x\\geq2$ (1)", "Domain $x\\geq-4$ (1)"])],
        P("$f(x)=2x-1$ and $g(x)=x^2+1$.", [pn("Find $fg(3)$.", 19, "$g(3)=10$, $f(10)=19$.", pts=1), pn("Solve $gf(x)=10$. Give the larger solution.", 2, "$(2x-1)^2+1=10$, so $(2x-1)^2=9$, $2x-1=\\pm3$: $x=2$ or $x=-1$.", pts=2), pt("$fg(x)=gf(x)$ for all $x$.", False, "$fg(x)=2x^2+1$ but $gf(x)=4x^2-4x+2$.", pts=1)]),
        [("If $f(x)=x+2$ and $g(x)=3x$ then $gf(1)$ is", "$9$", ["$5$", "$3$", "$11$"], "$f(1)=3$, $g(3)=9$."),
         ("The graph of $f^{-1}$ is the reflection of $f$ in", "$y=x$", ["the x-axis", "the y-axis", "$y=-x$"], "Swapping $x$ and $y$."),
         ("Range of $f(x)=x^2+1$ is", "$y\\geq1$", ["$y\\geq0$", "all reals", "$y\\leq1$"], "$x^2\\geq0$."),
         ("$f(x)=\\frac{1}{x}$ has inverse", "itself", ["$-\\frac1x$", "$x$", "none"], "$y=\\frac1x$ gives $x=\\frac1y$."),
         ("A function needs", "one output for each input", ["one input for each output", "two outputs", "a formula only"], "Definition.")],
        ill=None, notes=["Check whether the syllabus asks for one-to-one/many-to-one terminology and graph transformations (translations, stretches); transformations of graphs are not covered in this pack."], ref=REF)
