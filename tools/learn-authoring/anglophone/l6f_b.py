import math, cmath
from math import pi, sin, cos, sqrt, exp, log, sinh, cosh, tanh, asinh
from _fm import *
from gal_a import close, D, I
from l6f_a import REF, NOTE

def build(p):
    ch = p.chapter("roots-induction", "Polynomial roots, series and induction", REF + " — roots of polynomial equations; series; proof by induction")
    # ---------------------------------------------------------- roots of polynomials
    r = [1, 2, 3]; assert sum(r) == 6 and 1 * 2 + 1 * 3 + 2 * 3 == 11 and 1 * 2 * 3 == 6
    s1, s2 = 5 / 2, 1 / 2; assert s1 ** 2 - 2 * s2 == 5.25 and s1 / s2 == 5 and 1 / s2 == 2
    assert 4 - 2 * 3 == -2 and [1, 3, 5] and 1 + 3 + 5 == 9 and 1 * 3 + 1 * 5 + 3 * 5 == 23 and 1 * 3 * 5 == 15
    z = [1, 1 + 1j, 1 - 1j]; assert abs(sum(z) - 3) < 1e-12 and abs(z[0] * z[1] + z[0] * z[2] + z[1] * z[2] - 4) < 1e-12 and abs(sum(a * a for a in z) - 1) < 1e-12
    fig = plot(-0.5, 3.5, -14, 4, curves=[("x^3-6x^2+11x-6", "blue", None)], points=[(1, 0, "1", "red"), (2, 0, "2", "red"), (3, 0, "3", "red")], xlabel="x")
    lesson(ch, "polynomial-roots", "Roots of polynomial equations",
        ["Use the sum and products of roots of quadratics and cubics", "Find expressions such as $\\alpha^2+\\beta^2$ without solving", "Form equations with related roots"],
        [("formule", "Quadratic", "If $\\alpha,\\beta$ are the roots of $ax^2+bx+c=0$: $\\alpha+\\beta=-\\frac ba$, $\\alpha\\beta=\\frac ca$. Then $\\alpha^2+\\beta^2=(\\alpha+\\beta)^2-2\\alpha\\beta$ and $\\frac1\\alpha+\\frac1\\beta=\\frac{\\alpha+\\beta}{\\alpha\\beta}$."),
         ("formule", "Cubic", "If $\\alpha,\\beta,\\gamma$ are the roots of $ax^3+bx^2+cx+d=0$: $\\alpha+\\beta+\\gamma=-\\frac ba$, $\\alpha\\beta+\\beta\\gamma+\\gamma\\alpha=\\frac ca$, $\\alpha\\beta\\gamma=-\\frac da$. Then $\\alpha^2+\\beta^2+\\gamma^2=(\\alpha+\\beta+\\gamma)^2-2(\\alpha\\beta+\\beta\\gamma+\\gamma\\alpha)$."),
         ("methode", "New equations", "To find the equation whose roots are, say, $\\frac1\\alpha,\\frac1\\beta$: find the sum and product of the NEW roots using the old sum and product, then write $x^2-(\\text{sum})x+(\\text{product})=0$. Or substitute $x=\\frac1y$ (or $y=2x$, etc.) into the original equation."),
         ("pieges", "Common mistakes", "Signs: the sum of the roots is $-\\frac ba$, the product of the roots of a cubic is $-\\frac da$. Remember to multiply by the leading coefficient $a$ at the end if integer coefficients are asked.")],
        [("Example 1", "The roots of $2x^2-5x+1=0$ are $\\alpha,\\beta$. Find $\\alpha^2+\\beta^2$ and the equation with roots $\\frac1\\alpha,\\frac1\\beta$.", ["$\\alpha+\\beta=\\frac52$, $\\alpha\\beta=\\frac12$. $\\alpha^2+\\beta^2=\\frac{25}{4}-1=\\frac{21}{4}$.", "New sum $=\\frac{5/2}{1/2}=5$; new product $=\\frac{1}{1/2}=2$. Equation $x^2-5x+2=0$."], "$\\frac{21}{4}$; $x^2-5x+2=0$"),
         ("Example 2", "The roots of $x^3-6x^2+11x-6=0$ are $1,2,3$. Check the relations.", ["Sum $=6=-\\frac{-6}{1}$. Pairs: $2+3+6=11=\\frac{11}{1}$.", "Product $=6=-\\frac{-6}{1}$."], "All three relations hold", fig)],
        [N("For $x^3-4x^2+x+6=0$ find the product of the roots.", -6, "$-\\frac da=-6$."),
         M("For $2x^3+3x^2-5x+7=0$ the sum of the products of the roots in pairs is", "$-\\frac52$", ["$\\frac52$", "$-\\frac32$", "$-\\frac72$"], "$\\frac ca=-\\frac52$."),
         TF("The sum of the roots of $x^2-7x+10=0$ is $-7$.", False, "The sum is $-\\frac ba=7$.")],
        [M("The roots of $x^3-2x^2+3x-4=0$ are $\\alpha,\\beta,\\gamma$. Then $\\alpha^2+\\beta^2+\\gamma^2=$", "$-2$", ["$10$", "$4$", "$2$"], "$2^2-2(3)=-2$ (negative: some roots are complex)."),
         O("The roots of $x^3-9x^2+23x-15=0$ are in arithmetic progression. Find them.", "Let the roots be $a-d$, $a$, $a+d$. Sum $=3a=9$, so $a=3$. Product $=3(9-d^2)=15$, so $d^2=4$, $d=\\pm2$. Roots $1,3,5$. Check: pairs $3+5+15=23$.", ["Sum gives $a=3$ (1)", "Product gives $d=\\pm2$ (1)", "Roots $1,3,5$ checked (1)"])],
        P("The roots of $x^3-3x^2+4x-2=0$ are $\\alpha,\\beta,\\gamma$.", [pn("Find $\\alpha+\\beta+\\gamma$.", 3, "$-\\frac{-3}{1}$.", pts=1), pn("Find $\\alpha\\beta+\\beta\\gamma+\\gamma\\alpha$.", 4, "$\\frac41$.", pts=1), pn("Find $\\alpha^2+\\beta^2+\\gamma^2$.", 1, "$9-2(4)=1$. (Check with the roots $1,\\,1+i,\\,1-i$: $1+2i-2i=1$.)", pts=2)]),
        [("For $x^2+6x+4=0$ the sum of roots is", "$-6$", ["$6$", "$4$", "$-4$"], "$-\\frac ba$."),
         ("The product of the roots of $2x^2-8x+6=0$ is", "$3$", ["$-3$", "$4$", "$6$"], "$\\frac ca$."),
         ("$\\frac1\\alpha+\\frac1\\beta=$", "$\\frac{\\alpha+\\beta}{\\alpha\\beta}$", ["$\\frac{1}{\\alpha+\\beta}$", "$\\alpha\\beta$", "$\\frac{\\alpha\\beta}{\\alpha+\\beta}$"], "Common denominator."),
         ("A cubic's roots sum to 0 when", "$b=0$", ["$c=0$", "$d=0$", "$a=0$"], "$-\\frac ba$."),
         ("Roots $\\alpha,\\beta$ with sum 5 and product 6 satisfy", "$x^2-5x+6=0$", ["$x^2+5x+6=0$", "$x^2-6x+5=0$", "$x^2-5x-6=0$"], "$x^2-(\\text{sum})x+\\text{product}$.")],
        ill=None, notes=[NOTE], ref=REF)

    # ---------------------------------------------------------- induction
    assert sum(r * r for r in range(1, 6)) == 55 and all(sum(range(1, n + 1)) == n * (n + 1) // 2 for n in range(1, 50)) and all((7 ** n - 1) % 6 == 0 for n in range(1, 40))
    assert all(sum(r ** 3 for r in range(1, n + 1)) == n * n * (n + 1) ** 2 // 4 for n in range(1, 30)) and sum(r ** 3 for r in range(1, 4)) == 36 and sum(r ** 3 for r in range(1, 5)) == 100
    assert all(2 ** n > n for n in range(1, 60)) and all(sum(2 * r - 1 for r in range(1, n + 1)) == n * n for n in range(1, 40))
    dom = shapes([RECT(20 + 70 * i, 90, 50, 70, fill="lightgrey" if i else "orange", radius=6) for i in range(5)] +
                 [T(45 + 70 * i, 132, "P(%d)" % (i + 1), 14) for i in range(4)] + [T(45 + 70 * 4, 132, "P(k)", 14)] +
                 [LINE(72 + 70 * i, 40, 88 + 70 * i, 40, arrow="end", color="blue", width=2) for i in range(4)] +
                 [T(200, 25, "P(k) true  ⇒  P(k+1) true", 14), T(45, 180, "base case", 12), T(200, 200, "…so true for every n ≥ 1", 14)], 400, 220)
    lesson(ch, "induction", "Proof by induction",
        ["Understand the four steps of a proof by induction", "Prove summation formulae", "Prove divisibility and inequality results"],
        [("methode", "The four steps", "To prove $P(n)$ for all integers $n\\geq1$: (1) **Basis**: show $P(1)$ is true. (2) **Assumption**: assume $P(k)$ is true for some integer $k\\geq1$. (3) **Inductive step**: using the assumption, show that $P(k+1)$ is true. (4) **Conclusion**: 'If $P(k)$ is true then $P(k+1)$ is true; $P(1)$ is true; so by mathematical induction $P(n)$ is true for all positive integers $n$.'"),
         ("propriete", "Typical forms", "Series: add the next term to both sides of the assumed formula and simplify to the formula with $k+1$. Divisibility: write $f(k+1)=a\\,f(k)+(\\text{multiple of the divisor})$. Inequalities: multiply or add to the assumption and compare."),
         ("pieges", "Common mistakes", "Both the basis AND the inductive step are needed (a true step with no basis proves nothing). Do not assume $P(k+1)$ at the start. Always write the concluding sentence. Keep track of the target expression for $n=k+1$ before you start simplifying.")],
        [("Example 1", "Prove by induction that $\\sum_{r=1}^nr=\\frac{n(n+1)}{2}$.", ["Basis: $n=1$: LHS $=1$, RHS $=\\frac{1\\cdot2}{2}=1$. True.", "Assume $\\sum_{r=1}^kr=\\frac{k(k+1)}{2}$. Then $\\sum_{r=1}^{k+1}r=\\frac{k(k+1)}{2}+(k+1)=\\frac{(k+1)(k+2)}{2}$, which is the formula with $n=k+1$.", "So true for $n=1$ and $P(k)\\Rightarrow P(k+1)$: true for all $n\\geq1$."], "Proved", dom),
         ("Example 2", "Prove that $7^n-1$ is divisible by 6 for all $n\\geq1$.", ["Basis: $7^1-1=6$, divisible by 6. Assume $7^k-1=6m$, so $7^k=6m+1$.", "$7^{k+1}-1=7(6m+1)-1=42m+6=6(7m+1)$, divisible by 6. By induction true for all $n\\geq1$."], "Proved")],
        [N("Use $\\sum r^2=\\frac{n(n+1)(2n+1)}{6}$ to find $\\sum_{r=1}^5r^2$.", 55, "$\\frac{5\\cdot6\\cdot11}{6}=55$."),
         M("The first step of a proof by induction is to", "check the statement for the first value of $n$", ["assume the result for $n=k+1$", "differentiate both sides", "state the conclusion"], "Basis."),
         TF("Proving $P(k)\\Rightarrow P(k+1)$ alone is enough to prove $P(n)$ for all $n\\geq1$.", False, "A basis case is also required.")],
        [M("The sum of the first $n$ odd numbers $1+3+\\cdots+(2n-1)$ is", "$n^2$", ["$n(n+1)$", "$2n$", "$\\frac{n^2}{2}$"], "For $n=1,2,3$: $1,4,9$."),
         O("Prove by induction that $2^n>n$ for all integers $n\\geq1$.", "Basis: $2^1=2>1$. Assume $2^k>k$. Then $2^{k+1}=2\\cdot2^k>2k\\geq k+1$ (since $k\\geq1$). So $P(k+1)$ holds. By induction true for all $n\\geq1$.", ["Basis (1)", "Inductive step (1)", "Conclusion (1)"])],
        P("Prove $\\sum_{r=1}^nr^3=\\frac14n^2(n+1)^2$ by induction.", [pn("Evaluate the left side for $n=3$.", 36, "$1+8+27=36$ (and $\\frac14\\cdot9\\cdot16=36$).", pts=1), pm("In the inductive step one must show $\\frac14k^2(k+1)^2+(k+1)^3$ equals", "$\\frac14(k+1)^2(k+2)^2$", ["$\\frac14k^2(k+2)^2$", "$\\frac14(k+1)^2(k+1)^2$", "$\\frac14(k+1)^3(k+2)$"], "$(k+1)^2\\left[\\frac{k^2}{4}+k+1\\right]=\\frac{(k+1)^2(k+2)^2}{4}$.", pts=2), pt("The basis case $n=1$ is true: both sides equal 1.", True, "$1^3=1=\\frac14\\cdot1\\cdot4$.", pts=1)]),
        [("In an induction proof, 'assume $P(k)$' is used in", "the inductive step", ["the basis", "the conclusion only", "no step"], "It is the hypothesis."),
         ("To prove divisibility by 5 of $f(k+1)$ we usually show it equals", "a multiple of 5 plus a multiple of $f(k)$", ["$f(k)^2$", "$5+f(k)$ only", "$0$"], "Use the assumption."),
         ("Induction proves statements about", "all integers $n\\geq n_0$", ["all real numbers", "a single case", "negative numbers only"], "Natural numbers."),
         ("$\\sum_{r=1}^{n}1=$", "$n$", ["1", "$n^2$", "$n+1$"], "$n$ ones."),
         ("The concluding sentence must mention", "the basis and the step", ["only the basis", "only the step", "the answer's units"], "Standard form.")],
        ill=None, notes=[NOTE], ref=REF)

    # ---------------------------------------------------------- series
    assert sum(r * r + 3 * r for r in range(1, 11)) == 550 and sum(r ** 3 for r in range(1, 7)) == 441 and sum(2 * r + 1 for r in range(1, 6)) == 5 ** 2 + 2 * 5
    assert sum(r * (r + 1) for r in range(1, 4)) == 20 == 3 * 4 * 5 // 3 and sum(3 * r * r - 1 for r in range(1, 11)) == 1145
    from fractions import Fraction as Fr
    assert sum(Fr(1, r * (r + 1)) for r in range(1, 100)) == Fr(99, 100) and abs(float(sum(Fr(1, r * (r + 2)) for r in range(1, 2000))) - .75) < 1e-3
    bar = bars([("n=%d" % n, round(100 * n / (n + 1), 1)) for n in (1, 2, 3, 4, 6, 9)], unit="% of 1")
    lesson(ch, "series-sums", "Summation of series and the method of differences",
        ["Use the standard results for $\\sum r$, $\\sum r^2$, $\\sum r^3$", "Sum series using these results", "Use the method of differences with partial fractions"],
        [("formule", "Standard results", "$\\sum_{r=1}^nr=\\frac{n(n+1)}{2}$, $\\sum_{r=1}^nr^2=\\frac{n(n+1)(2n+1)}{6}$, $\\sum_{r=1}^nr^3=\\frac{n^2(n+1)^2}{4}$ and $\\sum_{r=1}^nc=cn$. Sums are linear: $\\sum(af(r)+bg(r))=a\\sum f+b\\sum g$. For a sum starting at $r=m+1$ subtract the first $m$ terms."),
         ("methode", "Method of differences", "Write the general term as a difference $f(r)-f(r+1)$ (often by partial fractions). When added, the middle terms cancel, leaving only the first and last: $\\sum_{r=1}^n\\big(f(r)-f(r+1)\\big)=f(1)-f(n+1)$. If $f(n+1)\\to0$ as $n\\to\\infty$ the sum to infinity is $f(1)$."),
         ("pieges", "Common mistakes", "$\\sum r^2\\neq\\left(\\sum r\\right)^2$. Expand brackets before using the formulae (e.g. $r(r+1)=r^2+r$). In the method of differences write out the first three and last two terms to see exactly which terms survive.")],
        [("Example 1", "Find $\\sum_{r=1}^{10}(r^2+3r)$.", ["$\\sum r^2=\\frac{10\\cdot11\\cdot21}{6}=385$ and $\\sum r=55$.", "Total $=385+3(55)=385+165=550$."], "550"),
         ("Example 2", "Find $\\sum_{r=1}^n\\frac{1}{r(r+1)}$ and its sum to infinity.", ["$\\frac{1}{r(r+1)}=\\frac1r-\\frac{1}{r+1}$, so the sum telescopes: $\\left(1-\\frac12\\right)+\\left(\\frac12-\\frac13\\right)+\\cdots+\\left(\\frac1n-\\frac{1}{n+1}\\right)=1-\\frac{1}{n+1}$.", "$=\\frac{n}{n+1}\\to1$ as $n\\to\\infty$."], "$\\frac{n}{n+1}$; sum to infinity 1", bar)],
        [N("Find $\\sum_{r=1}^6r^3$.", 441, "$\\frac{36\\cdot49}{4}=441$."),
         M("$\\sum_{r=1}^n(2r+1)=$", "$n^2+2n$", ["$n^2+1$", "$2n^2+n$", "$n(n+1)$"], "$n(n+1)+n=n^2+2n$."),
         TF("$\\sum r^2=\\left(\\sum r\\right)^2$.", False, "For $n=2$: $5\\neq9$. (It is $\\sum r^3$ that equals $\\left(\\sum r\\right)^2$.)")],
        [M("$\\sum_{r=1}^nr(r+1)=$", "$\\frac{n(n+1)(n+2)}{3}$", ["$\\frac{n(n+1)}{2}$", "$\\frac{n(n+1)(2n+1)}{6}$", "$n^2(n+1)$"], "Check $n=3$: $2+6+12=20=\\frac{3\\cdot4\\cdot5}{3}$."),
         O("Show that $\\frac{1}{r(r+2)}=\\frac12\\left(\\frac1r-\\frac{1}{r+2}\\right)$ and hence find $\\sum_{r=1}^\\infty\\frac{1}{r(r+2)}$.", "$\\frac12\\left(\\frac{(r+2)-r}{r(r+2)}\\right)=\\frac{1}{r(r+2)}$. Summing, the terms $\\frac1r-\\frac{1}{r+2}$ leave $1+\\frac12-\\frac{1}{n+1}-\\frac{1}{n+2}$, so $S_n=\\frac12\\left(\\frac32-\\frac{1}{n+1}-\\frac{1}{n+2}\\right)\\to\\frac34$.", ["Partial fractions (1)", "Cancelling pattern (1)", "Limit $\\frac34$ (1)"])],
        P("Answer the following.", [pn("Evaluate $\\sum_{r=1}^{10}(3r^2-1)$.", 1145, "$3(385)-10=1145$.", pts=2), pn("Evaluate $\\sum_{r=1}^{99}\\frac{1}{r(r+1)}$ as a decimal.", .99, "$\\frac{99}{100}$.", tol=0.001, pts=1), pt("The series $\\sum_{r=1}^\\infty\\frac{1}{r(r+1)}$ converges to 1.", True, "$\\frac{n}{n+1}\\to1$.", pts=1)]),
        [("$\\sum_{r=1}^{4}r=$", "$10$", ["$24$", "$16$", "$20$"], "$\\frac{4\\cdot5}{2}$."),
         ("$\\sum_{r=1}^{n}r^2$ for $n=3$ is", "$14$", ["$36$", "$9$", "$6$"], "$1+4+9$."),
         ("In $\\sum\\left(\\frac1r-\\frac1{r+1}\\right)$ the middle terms", "cancel", ["add up", "double", "remain"], "Telescoping."),
         ("$\\sum_{r=1}^{n}5=$", "$5n$", ["5", "$n^5$", "$5^n$"], "$n$ copies of 5."),
         ("$\\sum_{r=3}^{n}r=$", "$\\frac{n(n+1)}{2}-3$", ["$\\frac{n(n+1)}{2}-1$", "$\\frac{(n-3)(n-2)}{2}$", "$\\frac{n(n+1)}{2}+3$"], "Remove $1+2=3$.")],
        ill=None, notes=[NOTE], ref=REF)

    # ---------------------------------------------------------- hyperbolic
    ch = p.chapter("hyperbolic-vectors", "Hyperbolic functions and vector products", REF + " — hyperbolic functions; vectors")
    close(asinh(1), log(1 + sqrt(2))); close(log(1 + sqrt(2)), .8814, 1e-4); close(cosh(log(2)), 1.25); close(sinh(log(3)), 4 / 3)
    close(cosh(.3) ** 2 - sinh(.3) ** 2, 1); close(D(lambda x: x * cosh(x), .7), cosh(.7) + .7 * sinh(.7), 1e-6)
    x0 = math.acosh(1.5); close(x0, log(1.5 + sqrt(1.25))); close(x0, .9624, 1e-4)
    close(20 * cosh(.5), 22.5525, 1e-3); close(sinh(.5), .5211, 1e-4); close(cosh(2 * .8), 2 * cosh(.8) ** 2 - 1)
    hy = plot(-3, 3, -5, 5, curves=[("(exp(x)+exp(-x))/2", "blue", "cosh x"), ("(exp(x)-exp(-x))/2", "orange", "sinh x")], xlabel="x")
    lesson(ch, "hyperbolic", "Hyperbolic functions",
        ["Define $\\sinh$, $\\cosh$, $\\tanh$ and use their identities", "Differentiate hyperbolic functions", "Solve equations using logarithmic forms"],
        [("definition", "Definitions", "$\\sinh x=\\frac{e^x-e^{-x}}{2}$, $\\cosh x=\\frac{e^x+e^{-x}}{2}$, $\\tanh x=\\frac{\\sinh x}{\\cosh x}$. $\\cosh$ is even with minimum value 1 at $x=0$; $\\sinh$ is odd and increasing. 'Hyperbolic' because $(x,y)=(\\cosh t,\\sinh t)$ lies on the hyperbola $x^2-y^2=1$."),
         ("propriete", "Identities and derivatives", "$\\cosh^2x-\\sinh^2x=1$ (compare Osborn's rule: change the sign of each product of two sinh terms in the trigonometric identity). $\\sinh2x=2\\sinh x\\cosh x$, $\\cosh2x=\\cosh^2x+\\sinh^2x=2\\cosh^2x-1$. Derivatives: $\\frac{d}{dx}\\sinh x=\\cosh x$, $\\frac{d}{dx}\\cosh x=\\sinh x$, $\\frac{d}{dx}\\tanh x=\\frac{1}{\\cosh^2x}$."),
         ("formule", "Inverse functions", "$\\text{arsinh }x=\\ln\\left(x+\\sqrt{x^2+1}\\right)$ for all $x$; $\\text{arcosh }x=\\ln\\left(x+\\sqrt{x^2-1}\\right)$ for $x\\geq1$. To solve $\\sinh x=k$ or $\\cosh x=k$ either use these or write in terms of $e^x$ and solve the resulting quadratic in $e^x$."),
         ("pieges", "Common mistakes", "$\\frac{d}{dx}\\cosh x=+\\sinh x$ (no minus sign, unlike $\\cos$). $\\cosh x\\geq1$ always, so $\\cosh x=\\frac12$ has no solution. Do not forget the $\\pm$ in the identity changes: $\\cosh^2x-\\sinh^2x=1$.")],
        [("Example 1", "Solve $\\sinh x=1$.", ["$\\frac{e^x-e^{-x}}{2}=1$; put $u=e^x$: $u^2-2u-1=0$, $u=1\\pm\\sqrt2$.", "$e^x>0$ so $u=1+\\sqrt2$ and $x=\\ln(1+\\sqrt2)=0.881$."], "$x=\\ln(1+\\sqrt2)\\approx0.881$", hy),
         ("Example 2", "Evaluate $\\cosh(\\ln2)$ and differentiate $y=x\\cosh x$.", ["$\\cosh(\\ln2)=\\frac{2+\\frac12}{2}=1.25$.", "Product rule: $\\frac{dy}{dx}=\\cosh x+x\\sinh x$."], "$1.25$; $\\cosh x+x\\sinh x$")],
        [N("Find $\\sinh(\\ln3)$ (3 d.p.).", 1.333, "$\\frac{3-\\frac13}{2}=\\frac43$.", tol=0.001),
         M("$\\frac{d}{dx}\\cosh2x=$", "$2\\sinh2x$", ["$\\sinh2x$", "$-2\\sinh2x$", "$2\\cosh2x$"], "Chain rule."),
         TF("$\\cosh x\\geq1$ for all real $x$.", True, "$\\cosh x=\\frac{e^x+e^{-x}}{2}\\geq1$ by AM–GM; minimum 1 at $x=0$.")],
        [M("Which equation has NO real solution?", "$\\cosh x=\\frac12$", ["$\\sinh x=\\frac12$", "$\\cosh x=2$", "$\\tanh x=\\frac12$"], "$\\cosh x\\geq1$."),
         N("Solve $2\\cosh x=3$ for the positive solution (4 d.p.).", .9624, "$\\cosh x=1.5$: $x=\\ln\\left(1.5+\\sqrt{1.25}\\right)=0.9624$.", tol=0.0002)],
        P("A cable hangs in the shape $y=20\\cosh\\frac{x}{20}$ metres ($x$ measured from the lowest point).", [pn("Find the height of the lowest point (m).", 20, "$y(0)=20\\cosh0=20$.", pts=1), pn("Find the height at $x=10$ (m, 2 d.p.).", 22.55, "$20\\cosh0.5=20\\times1.1276=22.55$.", tol=0.01, pts=2), pn("Find the gradient of the cable at $x=10$ (4 d.p.).", .5211, "$\\frac{dy}{dx}=\\sinh\\frac{x}{20}=\\sinh0.5=0.5211$.", tol=0.0002, pts=1)]),
        [("$\\cosh0=$", "$1$", ["$0$", "$e$", "$\\frac12$"], "$\\frac{1+1}{2}$."),
         ("$\\sinh0=$", "$0$", ["$1$", "$e$", "$-1$"], "$\\frac{1-1}{2}$."),
         ("$\\cosh^2x-\\sinh^2x=$", "$1$", ["$0$", "$-1$", "$\\cosh2x$"], "Basic identity."),
         ("$\\frac{d}{dx}\\sinh x=$", "$\\cosh x$", ["$-\\cosh x$", "$\\sinh x$", "$\\frac{1}{\\cosh x}$"], "Derivative of the definition."),
         ("$\\text{arsinh }0=$", "$0$", ["$1$", "$\\ln2$", "undefined"], "$\\ln(0+1)$.")],
        ill=None, notes=[NOTE], ref=REF)

    # ---------------------------------------------------------- vector product
    a, b = (1, 2, 3), (4, 5, 6); assert cross(a, b) == (-3, 6, -3) and cross((2, 0, 0), (0, 3, 0)) == (0, 0, 6)
    n = cross((-1, 2, 0), (-1, 0, 3)); assert n == (6, 3, 2) and dot(n, (1, 0, 0)) == 6 == dot(n, (0, 2, 0)) == dot(n, (0, 0, 3)) and math.sqrt(dot(n, n)) == 7
    assert abs(abs(dot((1, 1, 1), (1, 2, 2)) - 3) / 3 - 2 / 3) < 1e-12
    pq, pr = (2, 0, -2), (1, 2, -1); c = cross(pq, pr); assert c == (4, 0, 4) and abs(.5 * sqrt(dot(c, c)) - 2 * sqrt(2)) < 1e-12
    n2 = cross((-2, 3, 0), (-2, 0, 6)); assert n2 == (18, 12, 6) and dot(n2, (2, 0, 0)) == 36 and dot(n2, (0, 3, 0)) == 36 and dot(n2, (0, 0, 6)) == 36
    close(.5 * sqrt(dot(n2, n2)), 11.225, 1e-3); close(6 / sqrt(14), 1.6036, 1e-3)
    vp = shapes([LINE(80, 180, 240, 180, color="blue", width=3, arrow="end"), T(250, 184, "a", 15, anchor="start"), LINE(80, 180, 150, 120, color="orange", width=3, arrow="end"), T(158, 116, "b", 15, anchor="start"),
                 LINE(80, 180, 80, 50, color="green", width=3, arrow="end"), T(92, 52, "a × b", 15, anchor="start"), T(130, 168, "θ", 14), T(250, 90, "|a × b| = |a||b| sin θ", 13, anchor="start"), T(250, 112, "= area of parallelogram", 13, anchor="start")], 420, 220)
    lesson(ch, "vector-product", "Vector product and planes",
        ["Calculate the vector product $\\vec a\\times\\vec b$", "Find areas of triangles and parallelograms", "Find the equation of a plane and the distance from a point to a plane"],
        [("formule", "Vector product", "$\\vec a\\times\\vec b=(a_2b_3-a_3b_2,\;a_3b_1-a_1b_3,\;a_1b_2-a_2b_1)$. It is perpendicular to both $\\vec a$ and $\\vec b$, $|\\vec a\\times\\vec b|=|\\vec a||\\vec b|\\sin\\theta$ and $\\vec b\\times\\vec a=-\\vec a\\times\\vec b$. $\\vec a\\times\\vec b=\\vec 0$ if and only if the vectors are parallel."),
         ("formule", "Areas", "Area of the parallelogram on $\\vec a,\\vec b$ is $|\\vec a\\times\\vec b|$; area of triangle $ABC$ is $\\frac12|\\vec{AB}\\times\\vec{AC}|$."),
         ("methode", "Planes", "A plane through a point with normal $\\vec n$ is $\\vec n\\cdot\\vec r=d$, i.e. $n_1x+n_2y+n_3z=d$. If you know three points $A,B,C$ use $\\vec n=\\vec{AB}\\times\\vec{AC}$. The distance from point $P$ to the plane is $\\frac{|\\vec n\\cdot\\vec p-d|}{|\\vec n|}$."),
         ("pieges", "Common mistakes", "The vector product is a VECTOR (the scalar product is a number). The order matters for the sign. Do not forget the middle component's order: $a_3b_1-a_1b_3$.")],
        [("Example 1", "Find $\\vec a\\times\\vec b$ for $\\vec a=(1,2,3)$ and $\\vec b=(4,5,6)$.", ["First: $2\\cdot6-3\\cdot5=-3$. Second: $3\\cdot4-1\\cdot6=6$.", "Third: $1\\cdot5-2\\cdot4=-3$."], "$(-3,6,-3)$", vp),
         ("Example 2", "$A(1,0,0)$, $B(0,2,0)$, $C(0,0,3)$. Find the plane $ABC$, the area of the triangle and the distance from $O$ to the plane.", ["$\\vec{AB}=(-1,2,0)$, $\\vec{AC}=(-1,0,3)$: $\\vec n=(6,3,2)$. Plane $6x+3y+2z=6$ (check $A$, $B$, $C$ each give 6).", "$|\\vec n|=\\sqrt{36+9+4}=7$. Area $=\\frac72=3.5$; distance from $O=\\frac{|0-6|}{7}=\\frac67$."], "$6x+3y+2z=6$; area 3.5; distance $\\frac67$")],
        [N("Find $|\\vec a\\times\\vec b|$ for $\\vec a=(2,0,0)$, $\\vec b=(0,3,0)$.", 6, "$\\vec a\\times\\vec b=(0,0,6)$."),
         M("$\\vec a\\times\\vec b$ equals", "$-\\vec b\\times\\vec a$", ["$\\vec b\\times\\vec a$", "$\\vec a\\cdot\\vec b$", "$|\\vec a||\\vec b|$"], "Anti-commutative."),
         TF("The vector product of two vectors is a scalar.", False, "It is a vector perpendicular to both.")],
        [M("The distance from $(1,1,1)$ to the plane $x+2y+2z=3$ is", "$\\frac23$", ["$\\frac13$", "$2$", "$\\frac43$"], "$\\frac{|1+2+2-3|}{\\sqrt{1+4+4}}=\\frac23$."),
         O("Find the area of the triangle $P(1,2,3)$, $Q(3,2,1)$, $R(2,4,2)$.", "$\\vec{PQ}=(2,0,-2)$, $\\vec{PR}=(1,2,-1)$. $\\vec{PQ}\\times\\vec{PR}=(0\\cdot(-1)-(-2)(2),\;(-2)(1)-2(-1),\;2\\cdot2-0)=(4,0,4)$, magnitude $\\sqrt{32}$. Area $=\\frac12\\sqrt{32}=2\\sqrt2\\approx2.83$.", ["Edge vectors (1)", "Cross product (1)", "Half the magnitude (1)"])],
        P("$A(2,0,0)$, $B(0,3,0)$, $C(0,0,6)$ lie on a plane.", [pn("$\\vec{AB}\\times\\vec{AC}=(18,12,6)$. The plane is $3x+2y+z=d$. Find $d$.", 6, "Using $A$: $3(2)=6$ (also $B$: $2(3)=6$, $C$: $6$).", pts=1), pn("Find the area of triangle $ABC$ (3 d.p.).", 11.225, "$\\frac12\\sqrt{18^2+12^2+6^2}=\\frac12\\sqrt{504}=11.225$.", tol=0.001, pts=2), pn("Find the distance from $O$ to the plane (3 d.p.).", 1.604, "$\\frac{6}{\\sqrt{14}}=1.604$.", tol=0.001, pts=1)]),
        [("$(1,0,0)\\times(0,1,0)=$", "$(0,0,1)$", ["$(0,0,-1)$", "$(1,1,0)$", "$0$"], "Right-hand rule."),
         ("$\\vec a\\times\\vec a=$", "$\\vec 0$", ["$|\\vec a|^2$", "$\\vec a$", "$1$"], "$\\sin0=0$."),
         ("A normal to a plane through $A,B,C$ is", "$\\vec{AB}\\times\\vec{AC}$", ["$\\vec{AB}\\cdot\\vec{AC}$", "$\\vec{AB}+\\vec{AC}$", "$\\vec{BC}$"], "Perpendicular to the plane."),
         ("The plane $2x-y+3z=4$ has normal", "$(2,-1,3)$", ["$(2,1,3)$", "$(4,0,0)$", "$(3,-1,2)$"], "Coefficients."),
         ("The area of a parallelogram on $\\vec a,\\vec b$ is", "$|\\vec a\\times\\vec b|$", ["$\\vec a\\cdot\\vec b$", "$\\frac12|\\vec a\\times\\vec b|$", "$|\\vec a||\\vec b|$"], "Magnitude of the cross product.")],
        ill=None, notes=[NOTE, "The vector equation of a plane in the form r = a + sb + tc is not included."], ref=REF)
