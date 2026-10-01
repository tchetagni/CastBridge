import math, cmath
from math import pi, sin, cos, sqrt, exp, log, cosh, sinh
from _fm import *
from gal_a import close, I
REF = "Cameroon GCE Board — Advanced Level Further Mathematics syllabus (Upper Sixth part; scope to be checked against the official text)"
NOTE = "Whether this topic is on the Cameroon GCE Board Further Mathematics syllabus is to be confirmed: it is standard A-Level Further Maths content."
def solve3(A, b):
    d = Fr(det3(A)); r = []
    for j in range(3):
        Mx = [row[:] for row in A]
        for i in range(3): Mx[i][j] = b[i]
        r.append(Fr(det3(Mx)) / d)
    return r

def build(p):
    ch = p.chapter("matrices", "Matrices: 3×3 systems and eigenvalues", REF + " — matrices")
    A = [[1, 2, 0], [0, 1, 3], [2, 0, 1]]; assert det3(A) == 13 and solve3(A, [4, -2, 3]) == [2, 1, -1]
    S = [[1, 1, 1], [2, -1, 1], [1, 2, -1]]; assert det3(S) == 7 and solve3(S, [6, 3, 2]) == [1, 2, 3]
    B = [[2, 0, 1], [1, 3, 2], [0, 1, 1]]; assert det3(B) == 3 and [[1, 2], [2, 4]] and det2([[1, 2], [2, 4]]) == 0
    assert det3([[1, 0, 0], [0, 1, 0], [0, 0, 1]]) == 1
    c11 = A[1][1] * A[2][2] - A[1][2] * A[2][1]; assert c11 == 1
    fig = shapes(mat_items(40, 50, A)[0] + [T(150, 56, "det = 13", 18, anchor="start")] + mat_items(250, 50, [[1, 1, 1], [2, -1, 1], [1, 2, -1]])[0] + [T(270, 130, "det = 7", 16, anchor="start")], 400, 150)
    lesson(ch, "matrices-3x3", "3×3 matrices and systems of equations",
        ["Evaluate $3\\times3$ determinants", "Find the inverse of a $3\\times3$ matrix", "Solve and classify systems of three linear equations"],
        [("formule", "Determinant (first row)", "For a $3\\times3$ matrix with rows $(a,b,c)$, $(d,e,f)$, $(g,h,i)$: $\\det=a(ei-fh)-b(di-fg)+c(dh-eg)$. Signs alternate $+,-,+$. Properties: swapping two rows changes the sign; $\\det(A^T)=\\det A$; $\\det(AB)=\\det A\\det B$; a zero row or two equal rows give $\\det=0$."),
         ("methode", "Inverse by cofactors", "1. Compute each minor and apply the alternating sign pattern $+,-,+$ / $-,+,-$ / $+,-,+$ (the cofactors). 2. Transpose the matrix of cofactors to get the adjugate. 3. $A^{-1}=\\frac{1}{\\det A}\\text{adj}(A)$. It exists only if $\\det A\\neq0$. Check by multiplying $AA^{-1}$."),
         ("methode", "Systems of equations", "$A\\vec x=\\vec b$ has a unique solution if $\\det A\\neq0$: $\\vec x=A^{-1}\\vec b$ (or Cramer's rule: $x_j=\\frac{\\det A_j}{\\det A}$ where column $j$ is replaced by $\\vec b$). If $\\det A=0$ the system has either no solution (inconsistent: the planes form a prism or are parallel) or infinitely many (planes share a line); eliminate to decide."),
         ("pieges", "Common mistakes", "Sign errors in the cofactor pattern. Forgetting to transpose for the adjugate. Concluding 'no solution' when $\\det A=0$ without checking consistency.")],
        [("Example 1", "Evaluate $\\det A$ for $A=$ [[1, 2, 0], [0, 1, 3], [2, 0, 1]].", ["Expand along row 1: $1(1\\cdot1-3\\cdot0)-2(0\\cdot1-3\\cdot2)+0$.", "$=1(1)-2(-6)=1+12$."], "$\\det A=13$", fig),
         ("Example 2", "Solve $x+y+z=6$, $2x-y+z=3$, $x+2y-z=2$ using Cramer's rule.", ["$\\det=1(1-2)-1(-2-1)+1(4+1)=-1+3+5=7$.", "$\\det A_x=6(1-2)-1(-3-2)+1(6+2)=7$, so $x=1$; similarly $y=2$, $z=3$. Check eq. 2: $2-2+3=3$."], "$x=1,\;y=2,\;z=3$")],
        [N("Find $\\det$ [[2, 0, 1], [1, 3, 2], [0, 1, 1]].", 3, "$2(3-2)-0+1(1-0)=2+1=3$."),
         M("A $3\\times3$ system $A\\vec x=\\vec b$ with $\\det A=0$", "has no unique solution", ["always has no solution", "always has a unique solution", "has exactly two solutions"], "Either none or infinitely many."),
         TF("$\\det(A^T)=\\det A$.", True, "Transposing does not change the determinant.")],
        [M("The inverse of the diagonal matrix with diagonal entries $2,3,4$ is the diagonal matrix with entries", "$\\frac12,\\frac13,\\frac14$", ["$-2,-3,-4$", "$2,3,4$", "$\\frac14,\\frac13,\\frac12$"], "Invert each diagonal entry."),
         O("The equations $x+2y=3$ and $2x+4y=k$ are written as $A\\vec x=\\vec b$. Find $\\det A$ and the value of $k$ for which there are infinitely many solutions.", "$\\det A=1\\cdot4-2\\cdot2=0$. The second equation is twice the first only if $k=6$: then the equations coincide and there are infinitely many solutions. If $k\\neq6$ they are parallel lines with no solution.", ["$\\det A=0$ (1)", "$k=6$ (1)", "Explains no solution otherwise (1)"])],
        P("$A=$ [[1, 2, 0], [0, 1, 3], [2, 0, 1]]. $A\\vec x=\\vec b$ with $\\vec b=(4,-2,3)$.", [pn("Find $\\det A$.", 13, "$1+12=13$.", pts=1), pn("The $(1,1)$ entry of $A^{-1}$ is $\\frac{C_{11}}{13}$ where $C_{11}=1\\cdot1-3\\cdot0$. Give it to 4 d.p.", 0.0769, "$\\frac1{13}=0.0769$.", tol=0.0002, pts=1), pn("Solve for $z$.", -1, "The solution is $(x,y,z)=(2,1,-1)$. Check: $2+2=4$; $1-3=-2$; $4-1=3$.", pts=2)]),
        [("A $3\\times3$ matrix with two equal rows has $\\det$", "$0$", ["$1$", "$-1$", "$2$"], "Property."),
         ("Cofactor signs alternate starting with", "$+$", ["$-$", "$0$", "$\\times$"], "Pattern $+-+$."),
         ("$A^{-1}=$", "$\\frac{1}{\\det A}\\text{adj}(A)$", ["$\\det A\\cdot\\text{adj}(A)$", "$A^T$", "$-A$"], "Adjugate formula."),
         ("If $\\det A\\neq0$ the system $A\\vec x=\\vec b$ has", "a unique solution", ["no solution", "infinitely many", "two solutions"], "Invertible."),
         ("Swapping two rows multiplies $\\det$ by", "$-1$", ["$2$", "$0$", "$1$"], "Property.")],
        ill=None, notes=[NOTE], ref=REF)

    # ------------------------------------------------------------ eigen
    A2 = [[4, 1], [2, 3]]; assert mm(A2, [[1], [1]]) == [[5], [5]] and mm(A2, [[1], [-2]]) == [[2], [-4]]
    A2sq = mm(A2, A2); assert [[A2sq[i][j] - 7 * A2[i][j] + (10 if i == j else 0) for j in range(2)] for i in range(2)] == [[0, 0], [0, 0]]
    Aa = [[2, 1], [1, 2]]; assert mm(Aa, [[1], [1]]) == [[3], [3]] and mm(Aa, [[1], [-1]]) == [[1], [-1]]
    P5 = Aa
    for _ in range(4): P5 = mm(P5, Aa)
    assert P5[0][0] == 122 and P5[0][1] == 121
    ef = Axes(-2.5, 3.5, -2.5, 3.5, unit=40, xticks=[-2, 2], yticks=[-2, 2]).line(0, 0, 1, 1, color="blue", width=3, arrow="end").line(0, 0, 3, 3, color="blue", width=1, dash=True, arrow="end")
    ef.line(0, 0, 1, -1, color="orange", width=3, arrow="end").text(1.2, 2.2, "×3", 13, anchor="start").text(1.4, -1.4, "×1", 13, anchor="start")
    lesson(ch, "eigenvalues", "Eigenvalues and eigenvectors (2×2)",
        ["Find eigenvalues and eigenvectors", "Diagonalise a matrix and find powers", "Use the Cayley–Hamilton theorem"],
        [("definition", "Eigenvalues", "A non-zero vector $\\vec v$ is an eigenvector of $A$ with eigenvalue $\\lambda$ if $A\\vec v=\\lambda\\vec v$: the transformation only stretches the vector (by $\\lambda$) without turning it. Eigenvalues satisfy the **characteristic equation** $\\det(A-\\lambda I)=0$; for $2\\times2$: $\\lambda^2-(\\text{trace})\\lambda+\\det A=0$."),
         ("methode", "Eigenvectors", "For each $\\lambda$ solve $(A-\\lambda I)\\vec v=\\vec 0$; the two equations are multiples of each other, so you get a direction (any non-zero multiple works). Sum of eigenvalues $=$ trace, product $=\\det A$ (a useful check)."),
         ("propriete", "Diagonalisation and Cayley–Hamilton", "If $P$ has the eigenvectors as columns and $D$ is diagonal with the eigenvalues, then $A=PDP^{-1}$ and $A^n=PD^nP^{-1}$. Cayley–Hamilton: $A$ satisfies its own characteristic equation, e.g. $A^2-(\\text{tr}A)A+(\\det A)I=0$, which gives $A^{-1}$ and $A^2$ quickly."),
         ("pieges", "Common mistakes", "The zero vector is NOT an eigenvector. Do not forget to check the trace and determinant. Write the eigenvectors as columns of $P$ in the same order as the eigenvalues in $D$.")],
        [("Example 1", "Find the eigenvalues and eigenvectors of $A=$ [[4, 1], [2, 3]].", ["Trace 7, determinant 10: $\\lambda^2-7\\lambda+10=0$, $\\lambda=5$ or $2$.", "$\\lambda=5$: $-x+y=0$, vector $(1,1)$. $\\lambda=2$: $2x+y=0$, vector $(1,-2)$. Check: $A(1,1)=(5,5)$; $A(1,-2)=(2,-4)$."], "5 with $(1,1)$; 2 with $(1,-2)$"),
         ("Example 2", "Show that $A=$ [[4, 1], [2, 3]] satisfies $A^2-7A+10I=0$ and deduce $A^{-1}$.", ["$A^2=$ [[18, 7], [14, 11]], $7A=$ [[28, 7], [14, 21]]; $A^2-7A+10I=$ [[0, 0], [0, 0]].", "Rearrange: $A(7I-A)=10I$, so $A^{-1}=\\frac{1}{10}(7I-A)=\\frac{1}{10}$ [[3, −1], [−2, 4]]."], "$A^{-1}=\\frac1{10}$ [[3, −1], [−2, 4]]", ef)],
        [N("Find the larger eigenvalue of [[3, 1], [0, 2]].", 3, "Triangular matrix: eigenvalues are the diagonal entries 3 and 2."),
         M("The eigenvalues of [[2, 0], [0, 5]] are", "2 and 5", ["0 and 7", "10 and 7", "2 and 0"], "Diagonal matrix."),
         TF("The zero vector can be an eigenvector.", False, "Eigenvectors are non-zero by definition.")],
        [M("The eigenvalues of [[2, 1], [1, 2]] are", "1 and 3", ["2 and 2", "0 and 4", "1 and 2"], "$\\lambda^2-4\\lambda+3=0$."),
         O("Find the eigenvalues of [[1, 2], [2, −2]].", "Trace $=-1$, determinant $=-2-4=-6$. $\\lambda^2+\\lambda-6=0$, $(\\lambda+3)(\\lambda-2)=0$: $\\lambda=2$ or $-3$.", ["Characteristic equation (1)", "Factorises (1)", "Eigenvalues 2 and $-3$ (1)"])],
        P("$A=$ [[2, 1], [1, 2]].", [pn("Find the larger eigenvalue.", 3, "$\\lambda^2-4\\lambda+3=0$: 1 and 3.", pts=1), pm("An eigenvector for $\\lambda=1$ is", "(1, −1)", ["(1, 1)", "(1, 0)", "(2, 1)"], "$x+y=0$.", pts=1), pn("Using $A^n=\\frac12$ [[3ⁿ+1, 3ⁿ−1], [3ⁿ−1, 3ⁿ+1]], find the top-left entry of $A^5$.", 122, "$\\frac{243+1}{2}=122$ (verified by repeated multiplication).", pts=2)], figure=ef),
        [("$A\\vec v=\\lambda\\vec v$ defines", "an eigenvector $\\vec v$ with eigenvalue $\\lambda$", ["the inverse", "the determinant", "a rotation"], "Definition."),
         ("The sum of the eigenvalues equals", "the trace", ["the determinant", "zero", "one"], "Property."),
         ("The product of the eigenvalues equals", "the determinant", ["the trace", "zero", "one"], "Property."),
         ("$A=PDP^{-1}$ implies $A^n=$", "$PD^nP^{-1}$", ["$P^nDP^{-n}$", "$D^n$", "$nPDP^{-1}$"], "Telescoping."),
         ("Cayley–Hamilton says a matrix satisfies", "its own characteristic equation", ["$A=I$", "$A^2=A$", "$\\det A=1$"], "Theorem.")],
        ill=None, notes=[NOTE, "Eigenvalues of 3×3 matrices and complex eigenvalues are not included."], ref=REF)

    # ------------------------------------------------------------ complex roots, loci
    ch = p.chapter("complex-geometry", "Complex numbers: roots and loci", REF + " — complex numbers")
    r3 = [2 * cmath.exp(2j * pi * k / 3) for k in range(3)]; assert all(abs(z ** 3 - 8) < 1e-9 for z in r3)
    r4 = [2, 2j, -2, -2j]; assert all(abs(z ** 4 - 16) < 1e-9 for z in r4) and 2 * 2j * -2 * -2j == -16
    assert abs(sum(r4)) < 1e-12 and abs(abs(32 ** (1 / 5)) - 2) < 1e-9
    for t in range(0, 360, 7):
        z = complex(-1 + 2 * cos(math.radians(t)), 2 * sin(math.radians(t))); assert abs(abs(z - 3) - 2 * abs(z)) < 1e-9
    cube = Axes(-3, 3, -3, 3, unit=40, xticks=[-2, 2], yticks=[-2, 2]).circle(0, 0, 2, color="grey")
    for z in r3: cube.pt(z.real, z.imag, None, color="red")
    cube.text(2.6, 0.35, "2", 12).text(-1.6, 1.95, "−1 + i√3", 11).text(-1.6, -2.1, "−1 − i√3", 11)
    lesson(ch, "complex-loci", "Roots of complex numbers and loci",
        ["Find $n$th roots of complex numbers and roots of unity", "Sketch loci such as $|z-a|=r$ and $\\arg(z-a)=\\theta$", "Find the Cartesian equation of a locus"],
        [("methode", "$n$th roots", "To solve $z^n=w$ write $w=r(\\cos\\theta+i\\sin\\theta)$ (and $\\theta+2\\pi k$). Then $z=r^{1/n}\\left[\\cos\\frac{\\theta+2\\pi k}{n}+i\\sin\\frac{\\theta+2\\pi k}{n}\\right]$ for $k=0,1,\\dots,n-1$. The $n$ roots lie on a circle of radius $r^{1/n}$, equally spaced at angles $\\frac{2\\pi}{n}$, forming a regular polygon. The $n$th roots of unity sum to 0."),
         ("propriete", "Standard loci", "$|z-a|=r$: circle, centre $a$, radius $r$. $|z-a|=|z-b|$: perpendicular bisector of the line joining $a$ and $b$. $\\arg(z-a)=\\theta$: a half-line from $a$ at angle $\\theta$ (not including $a$). $|z-a|<r$: inside the circle."),
         ("methode", "Cartesian equation", "Write $z=x+iy$ and use $|z-a|^2=(x-a_1)^2+(y-a_2)^2$. For $|z-a|=k|z-b|$ with $k\\neq1$ you get a circle: square both sides and complete the square."),
         ("pieges", "Common mistakes", "Giving fewer than $n$ roots. Using $\\arg z=\\frac{\\theta}{n}$ only and forgetting the $2\\pi k$ terms. In $|z-a|$ remember that $a$ is the CENTRE: $|z+1-2i|=3$ has centre $-1+2i$.")],
        [("Example 1", "Find the cube roots of 8.", ["$8=8(\\cos0+i\\sin0)$ so $z=2\\left[\\cos\\frac{2\\pi k}{3}+i\\sin\\frac{2\\pi k}{3}\\right]$.", "$k=0$: $2$. $k=1$: $2\\left(-\\frac12+\\frac{\\sqrt3}{2}i\\right)=-1+i\\sqrt3$. $k=2$: $-1-i\\sqrt3$."], "$2,\;-1+i\\sqrt3,\;-1-i\\sqrt3$", cube),
         ("Example 2", "Describe the locus $|z-1-2i|=3$.", ["Write as $|z-(1+2i)|=3$: the distance from $z$ to $1+2i$ is 3.", "A circle with centre $(1,2)$ and radius 3: $(x-1)^2+(y-2)^2=9$."], "Circle, centre $(1,2)$, radius 3")],
        [N("Find the modulus of each fifth root of 32.", 2, "$|z|=32^{1/5}=2$."),
         M("The sum of the $n$th roots of unity ($n\\geq2$) is", "0", ["1", "$n$", "$-1$"], "Roots are symmetric about the origin."),
         TF("$|z-3|=|z+3|$ describes the imaginary axis.", True, "Points equidistant from $3$ and $-3$ lie on the perpendicular bisector $x=0$.")],
        [M("The locus $|z-2|=|z|$ is", "the line $x=1$", ["the circle $|z|=1$", "the line $y=1$", "the line $x=2$"], "Perpendicular bisector of $0$ and $2$."),
         O("Find the Cartesian equation of the locus $|z-3|=2|z|$ and describe it.", "$(x-3)^2+y^2=4(x^2+y^2)$. So $x^2-6x+9+y^2=4x^2+4y^2$, i.e. $3x^2+6x+3y^2=9$, $x^2+2x+y^2=3$, $(x+1)^2+y^2=4$. A circle with centre $(-1,0)$ and radius 2.", ["Squares the moduli (1)", "Simplifies (1)", "Circle centre $(-1,0)$ radius 2 (1)"])],
        P("Consider the equation $z^4=16$.", [pn("How many roots does it have?", 4, "A polynomial of degree 4 has 4 roots: $2,2i,-2,-2i$.", pts=1), pn("Find the modulus of each root.", 2, "$16^{1/4}=2$.", pts=1), pn("Find the sum of the roots.", 0, "$2+2i-2-2i=0$.", pts=1), pn("Find the product of the roots.", -16, "$2\\cdot2i\\cdot(-2)\\cdot(-2i)=-16$, which equals the constant term of $z^4-16$.", pts=1)]),
        [("$z^5=1$ has", "5 roots", ["1 root", "4 roots", "infinitely many roots"], "Degree 5."),
         ("$|z-a|=r$ is", "a circle", ["a line", "a point", "a parabola"], "Fixed distance."),
         ("The roots of $z^n=w$ are spaced by angle", "$\\frac{2\\pi}{n}$", ["$\\frac{\\pi}{n}$", "$2\\pi n$", "$\\frac{\\pi}{2}$"], "Equal spacing."),
         ("The locus $\\arg(z-1)=\\frac\\pi4$ is", "a half-line from $1$", ["a full line", "a circle", "a segment"], "Fixed argument."),
         ("The centre of $|z+1-2i|=3$ is", "$-1+2i$", ["$1-2i$", "$1+2i$", "$-1-2i$"], "$z-(-1+2i)$.")],
        ill=None, notes=[NOTE], ref=REF)

    # ------------------------------------------------------------ polar
    ch = p.chapter("polar-conics", "Polar coordinates and conic sections", REF + " — polar coordinates; conics")
    close(I(lambda t: .5 * (2 * (1 + cos(t))) ** 2, 0, 2 * pi), 6 * pi, 1e-5); close(I(lambda t: .5 * cos(2 * t) ** 2, -pi / 4, pi / 4), pi / 8, 1e-6)
    close(I(lambda t: .5 * (3 * (1 + cos(t))) ** 2, 0, 2 * pi), 27 * pi / 2, 1e-4); close(27 * pi / 2, 42.41, 1e-2)
    close(4 * cos(pi / 3), 2); close(4 * sin(pi / 3), 3.4641, 1e-3); close(6 * cos(pi / 3), 3); close(I(lambda t: .5 * (1 + cos(t)) ** 2, 0, 2 * pi), 1.5 * pi, 1e-5)
    card = Axes(-1, 5, -3, 3, unit=40, xticks=[2, 4], yticks=[-2, 2]).param(lambda t: 2 * (1 + cos(t)) * cos(t), lambda t: 2 * (1 + cos(t)) * sin(t), 0, 2 * pi, n=120).pt(4, 0, "r = 4", dy=-10)
    lesson(ch, "polar-coordinates", "Polar coordinates and area",
        ["Convert between polar and Cartesian coordinates", "Sketch simple polar curves", "Find areas using $\\frac12\\int r^2d\\theta$"],
        [("formule", "Conversion", "A point has polar coordinates $(r,\\theta)$: $x=r\\cos\\theta$, $y=r\\sin\\theta$; $r^2=x^2+y^2$, $\\tan\\theta=\\frac yx$ (check the quadrant). The pole is the origin and $\\theta$ is measured anticlockwise from the initial line (positive x-axis)."),
         ("propriete", "Standard curves", "$r=a$: circle centre $O$. $\\theta=\\alpha$: half-line. $r=a(1+\\cos\\theta)$: cardioid (heart shape; $r=0$ at $\\theta=\\pi$). $r=a\\cos\\theta$: circle through $O$ with diameter $a$ (Cartesian: $x^2+y^2=ax$). $r=a\\cos2\\theta$: four-petalled rose. To sketch, tabulate $r$ for several $\\theta$ and look for symmetry and where $r=0$."),
         ("formule", "Area", "The area of the region enclosed by the curve $r=f(\\theta)$ and the half-lines $\\theta=\\alpha$, $\\theta=\\beta$ is $A=\\frac12\\int_\\alpha^\\beta r^2\\,d\\theta$. For $\\cos^2$ terms use $\\cos^2\\theta=\\frac12(1+\\cos2\\theta)$."),
         ("pieges", "Common mistakes", "Forgetting the $\\frac12$ in the area formula. Using limits that go round the curve more than once. Treating $r<0$ as impossible (it is a point in the opposite direction).")],
        [("Example 1", "Convert the polar point $\\left(4,\\frac\\pi3\\right)$ to Cartesian coordinates and write $r=2\\cos\\theta$ in Cartesian form.", ["$x=4\\cos\\frac\\pi3=2$, $y=4\\sin\\frac\\pi3=2\\sqrt3\\approx3.46$.", "$r=2\\cos\\theta$: multiply by $r$: $r^2=2r\\cos\\theta$, so $x^2+y^2=2x$ (a circle)."], "$(2,2\\sqrt3)$; $x^2+y^2=2x$"),
         ("Example 2", "Find the area enclosed by the cardioid $r=2(1+\\cos\\theta)$.", ["$A=\\frac12\\int_0^{2\\pi}4(1+\\cos\\theta)^2d\\theta=2\\int_0^{2\\pi}\\left(1+2\\cos\\theta+\\cos^2\\theta\\right)d\\theta$.", "$\\int_0^{2\\pi}\\cos\\theta\\,d\\theta=0$ and $\\int_0^{2\\pi}\\cos^2\\theta\\,d\\theta=\\pi$, so $A=2(2\\pi+0+\\pi)=6\\pi$."], "$6\\pi\\approx18.85$", card.fig())],
        [N("A point has polar coordinates $\\left(6,\\frac\\pi3\\right)$. Find its x-coordinate.", 3, "$6\\cos\\frac\\pi3=3$."),
         M("$r=4$ represents", "a circle of radius 4", ["a line", "a spiral", "a parabola"], "Constant distance from $O$."),
         TF("The area enclosed by a polar curve is $\\int r^2d\\theta$.", False, "There is a factor $\\frac12$: $A=\\frac12\\int r^2d\\theta$.")],
        [M("The Cartesian equation of $r=2\\cos\\theta$ is", "$x^2+y^2=2x$", ["$x^2+y^2=4$", "$y=2x$", "$x^2+y^2=2y$"], "Multiply by $r$."),
         N("Find the area of one petal of $r=\\cos2\\theta$ for $-\\frac\\pi4\\leq\\theta\\leq\\frac\\pi4$ (3 d.p.).", round(pi / 8, 3), "$\\frac12\\int_{-\\pi/4}^{\\pi/4}\\cos^22\\theta\\,d\\theta=\\frac12\\cdot\\frac\\pi4=\\frac\\pi8=0.393$.", tol=0.001)],
        P("A flower bed is bounded by the cardioid $r=3(1+\\cos\\theta)$ (metres).", [pn("Find the largest value of $r$ (m).", 6, "At $\\theta=0$: $3\\times2=6$.", pts=1), pn("Find $r$ when $\\theta=\\frac\\pi2$ (m).", 3, "$3(1+0)=3$.", pts=1), pn("Find the area enclosed (m², 2 d.p.).", 42.41, "$\\frac12\\int_0^{2\\pi}9(1+\\cos\\theta)^2d\\theta=\\frac92\\cdot3\\pi=\\frac{27\\pi}{2}=42.41$.", tol=0.02, pts=2)], figure=card),
        [("$x=r\\cos\\theta$ for $(2,\\pi)$ is", "$-2$", ["$2$", "$0$", "$-1$"], "$\\cos\\pi=-1$."),
         ("$r=a(1+\\cos\\theta)$ is called a", "cardioid", ["spiral", "lemniscate", "parabola"], "Heart shape."),
         ("$x^2+y^2$ equals", "$r^2$", ["$r$", "$\\theta$", "$2r$"], "Pythagoras."),
         ("The area formula is $A=$", "$\\frac12\\int r^2\\,d\\theta$", ["$\\int r\\,d\\theta$", "$\\int r^2\\,d\\theta$", "$\\frac12\\int r\\,d\\theta$"], "Sector of a circle."),
         ("$\\cos^2\\theta=$", "$\\frac12(1+\\cos2\\theta)$", ["$1-\\sin2\\theta$", "$\\cos2\\theta$", "$\\frac12\\cos2\\theta$"], "Double-angle.")],
        ill=None, notes=[NOTE], ref=REF)

    # conics
    assert 25 - 9 == 16 and sqrt(16) / 5 == .8 and 16 + 9 == 25 and sqrt(25) / 4 == 1.25 and 12 / 4 == 3 and abs(sqrt(1 - 1 / 4) - sqrt(3) / 2) < 1e-12
    assert sqrt((0 - 4) ** 2 + 3 ** 2) == 5 and sqrt((0 + 4) ** 2 + 3 ** 2) == 5
    el = Axes(-6, 6, -4, 4, unit=30, xticks=[-4, 4], yticks=[-3, 3]).param(lambda t: 5 * cos(t), lambda t: 3 * sin(t), 0, 2 * pi, n=100).pt(4, 0, "F2", color="red", dy=-10).pt(-4, 0, "F1", color="red", dy=-10)
    hyf = Axes(-8, 8, -6, 6, unit=24, xticks=[-4, 4], yticks=[-3, 3])
    hyf.param(lambda t: 4 * cosh(t), lambda t: 3 * sinh(t), -1.8, 1.8).param(lambda t: -4 * cosh(t), lambda t: 3 * sinh(t), -1.8, 1.8).line(-7, -5.25, 7, 5.25, color="grey", dash=True, width=1).line(-7, 5.25, 7, -5.25, color="grey", dash=True, width=1)
    lesson(ch, "conic-sections", "Conic sections",
        ["Know the standard equations of the parabola, ellipse and hyperbola", "Find foci, eccentricity, directrices and asymptotes", "Use parametric forms"],
        [("formule", "Parabola", "$y^2=4ax$: focus $(a,0)$, directrix $x=-a$, eccentricity $e=1$. Parametric: $(at^2,2at)$. Every point is equidistant from the focus and the directrix."),
         ("formule", "Ellipse", "$\\frac{x^2}{a^2}+\\frac{y^2}{b^2}=1$ ($a>b>0$): $e=\\sqrt{1-\\frac{b^2}{a^2}}$ ($0<e<1$), foci $(\\pm ae,0)$, directrices $x=\\pm\\frac ae$. Parametric $(a\\cos t,b\\sin t)$. The sum of the distances from any point to the two foci is $2a$."),
         ("formule", "Hyperbola", "$\\frac{x^2}{a^2}-\\frac{y^2}{b^2}=1$: $e=\\sqrt{1+\\frac{b^2}{a^2}}$ ($e>1$), foci $(\\pm ae,0)$, asymptotes $y=\\pm\\frac bax$. Parametric $(a\\cosh t,b\\sinh t)$ for one branch. The difference of the distances to the foci is $2a$."),
         ("pieges", "Common mistakes", "The ellipse needs $b<a$ for the foci to lie on the x-axis (otherwise the major axis is vertical). Eccentricity of an ellipse is less than 1, of a hyperbola greater than 1. Rewrite $9x^2+25y^2=225$ as $\\frac{x^2}{25}+\\frac{y^2}{9}=1$ (divide by 225) before reading $a$ and $b$.")],
        [("Example 1", "Find $e$ and the foci of $\\frac{x^2}{25}+\\frac{y^2}{9}=1$.", ["$a^2=25$, $b^2=9$: $e=\\sqrt{1-\\frac{9}{25}}=\\frac45$.", "Foci $(\\pm ae,0)=(\\pm4,0)$."], "$e=\\frac45$; foci $(\\pm4,0)$", el),
         ("Example 2", "Find $e$, foci and asymptotes of $\\frac{x^2}{16}-\\frac{y^2}{9}=1$.", ["$e=\\sqrt{1+\\frac{9}{16}}=\\frac54$; foci $(\\pm5,0)$.", "Asymptotes $y=\\pm\\frac34x$."], "$e=\\frac54$; foci $(\\pm5,0)$; $y=\\pm\\frac34x$", hyf)],
        [N("For the parabola $y^2=12x$ find the x-coordinate of the focus.", 3, "$4a=12$, $a=3$."),
         M("The eccentricity of a parabola is", "1", ["0", "less than 1", "greater than 1"], "Definition."),
         TF("For an ellipse the sum of the distances from a point on the curve to the foci is constant, $2a$.", True, "Defining property.")],
        [M("The eccentricity of $\\frac{x^2}{4}+y^2=1$ is", "$\\frac{\\sqrt3}{2}$", ["$\\frac{3}{4}$", "$\\frac12$", "$\\sqrt3$"], "$a^2=4$, $b^2=1$: $e=\\sqrt{1-\\frac14}$."),
         O("Show that the gradient of the parabola $y^2=4ax$ at the point $(at^2,2at)$ is $\\frac1t$.", "Parametric: $\\frac{dx}{dt}=2at$ and $\\frac{dy}{dt}=2a$. So $\\frac{dy}{dx}=\\frac{2a}{2at}=\\frac1t$. (Or implicitly: $2y\\frac{dy}{dx}=4a$, so $\\frac{dy}{dx}=\\frac{2a}{y}=\\frac{2a}{2at}$.)", ["Both derivatives (1)", "Divides correctly (1)", "Result $\\frac1t$ (1)"])],
        P("The ellipse $E$ has equation $9x^2+25y^2=225$.", [pn("Find its eccentricity.", .8, "Divide by 225: $\\frac{x^2}{25}+\\frac{y^2}{9}=1$, $e=\\frac45$.", tol=0.001, pts=1), pn("Find the x-coordinate of the focus on the positive x-axis.", 4, "$ae=5\\times0.8=4$.", pts=1), pn("The point $(0,3)$ is on $E$. Find its distance to the focus $(4,0)$.", 5, "$\\sqrt{16+9}=5=a$; by symmetry it is also 5 from $(-4,0)$ and the sum is $10=2a$.", pts=2)]),
        [("The standard hyperbola $\\frac{x^2}{a^2}-\\frac{y^2}{b^2}=1$ has asymptotes", "$y=\\pm\\frac bax$", ["$y=\\pm\\frac abx$", "$y=\\pm x$ always", "$x=\\pm a$"], "Standard."),
         ("The directrix of $y^2=4ax$ is", "$x=-a$", ["$x=a$", "$y=-a$", "$x=0$"], "Equidistant property."),
         ("For an ellipse $e$ satisfies", "$0<e<1$", ["$e=1$", "$e>1$", "$e<0$"], "Definition."),
         ("$(a\\cos t,b\\sin t)$ describes", "an ellipse", ["a hyperbola", "a parabola", "a circle only"], "$\\frac{x^2}{a^2}+\\frac{y^2}{b^2}=1$."),
         ("For $\\frac{x^2}{9}+\\frac{y^2}{4}=1$, $a=$", "$3$", ["$9$", "$2$", "$4$"], "$a^2=9$.")],
        ill=None, notes=[NOTE, "Tangents/normals to conics and the focus-directrix property are only stated, not developed."], ref=REF)
