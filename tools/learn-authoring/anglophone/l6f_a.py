import math, cmath
from math import pi, sin, cos, sqrt, atan2, degrees, radians
from _fm import *
from gal_a import close
REF = "Cameroon GCE Board — Advanced Level Further Mathematics syllabus (Lower Sixth part; scope to be checked against the official text)"
NOTE = "Whether this topic is on the Cameroon GCE Board Further Mathematics syllabus (and in which year) is to be confirmed: it is standard A-Level Further Maths content."

def mfig(parts, w=400, h=110, y=45):
    items = []
    for kind, x, val in parts:
        if kind == "m": it, _ = mat_items(x, y, val); items += it
        else: items.append(T(x, y + 5, val, 20))
    return shapes(items, w, h)

def build(p):
    ch = p.chapter("matrices", "Matrices and transformations", REF + " — matrices")
    A = [[2, 1], [3, 4]]; B = [[1, 0], [-1, 2]]
    assert mm(A, B) == [[1, 2], [-1, 8]] and mm(B, A) == [[2, 1], [4, 7]] and det2(A) == 5 and det2(B) == 2 and det2(mm(A, B)) == 10
    assert inv2(A) == [[Fr(4, 5), Fr(-1, 5)], [Fr(-3, 5), Fr(2, 5)]]
    X = mm(inv2(A), [[5], [10]]); assert X == [[2], [1]]
    assert det2([[3, 2], [1, 4]]) == 10 and [k for k in range(-10, 10) if det2([[k, 2], [3, k + 1]]) == 0] == [-3, 2]
    Mx = [[1, 2], [3, 5]]; assert det2(Mx) == -1 and inv2(Mx) == [[-5, 2], [3, -1]] and mm(inv2(Mx), [[4], [9]]) == [[-2], [3]]
    fig = mfig([("m", 50, A), ("t", 140, "×"), ("m", 180, B), ("t", 262, "="), ("m", 300, mm(A, B))])
    lesson(ch, "matrices-2x2", "Matrices: algebra, determinant and inverse (2×2)",
        ["Add, multiply and scale matrices", "Find determinants and inverses of 2×2 matrices", "Solve simultaneous equations with matrices"],
        [("definition", "Matrices", "A matrix is a rectangular array of numbers. Here a matrix is written row by row: [[2, 1], [3, 4]] has rows (2 1) and (3 4). Add or subtract matrices of the same size entry by entry. Multiply row of the first by column of the second: an $m\\times n$ matrix times an $n\\times p$ matrix gives $m\\times p$. Usually $AB\\neq BA$."),
         ("formule", "Determinant and inverse", "For $A=$ [[a, b], [c, d]]: $\\det A=ad-bc$. If $\\det A\\neq0$: $A^{-1}=\\frac{1}{ad-bc}$ [[d, −b], [−c, a]]. If $\\det A=0$ the matrix is **singular** (no inverse). $AA^{-1}=A^{-1}A=I$ (identity) and $(AB)^{-1}=B^{-1}A^{-1}$; $\\det(AB)=\\det A\\,\\det B$."),
         ("methode", "Simultaneous equations", "Write the system as $A\\mathbf{x}=\\mathbf{b}$. If $\\det A\\neq0$ then $\\mathbf{x}=A^{-1}\\mathbf{b}$ (multiply on the LEFT). If $\\det A=0$ there is no unique solution (none or infinitely many)."),
         ("pieges", "Common mistakes", "- Multiplying entry by entry: that is NOT matrix multiplication.\n- $(AB)^{-1}=B^{-1}A^{-1}$: the order reverses.\n- In the inverse, swap $a$ and $d$ and CHANGE the signs of $b$ and $c$, then divide by the determinant.")],
        [("Example 1", "$A=$ [[2, 1], [3, 4]] and $B=$ [[1, 0], [−1, 2]]. Find $AB$ and $BA$.", ["$AB$: row 1 of $A$ with columns of $B$: $2(1)+1(-1)=1$ and $2(0)+1(2)=2$. Row 2: $3(1)+4(-1)=-1$ and $3(0)+4(2)=8$. So $AB=$ [[1, 2], [−1, 8]].", "$BA$: $1(2)+0(3)=2$, $1(1)+0(4)=1$; $-1(2)+2(3)=4$, $-1(1)+2(4)=7$. So $BA=$ [[2, 1], [4, 7]], different from $AB$."], "$AB=$ [[1, 2], [−1, 8]]; $BA=$ [[2, 1], [4, 7]]", fig),
         ("Example 2", "Find $A^{-1}$ for $A=$ [[2, 1], [3, 4]] and solve $2x+y=5$, $3x+4y=10$.", ["$\\det A=8-3=5$, so $A^{-1}=\\frac15$ [[4, −1], [−3, 2]].", "$\\mathbf{x}=A^{-1}$ (5, 10) $=\\frac15$ (20−10, −15+20) $=$ (2, 1). Check: $2(2)+1=5$, $3(2)+4(1)=10$."], "$x=2$, $y=1$")],
        [N("Find $\\det$ [[3, 2], [1, 4]].", 10, "$3\\times4-2\\times1=10$."),
         M("$(AB)^{-1}$ equals", "$B^{-1}A^{-1}$", ["$A^{-1}B^{-1}$", "$A^{-1}+B^{-1}$", "$\\frac{1}{AB}$ entrywise"], "Reverse order."),
         TF("$AB=BA$ for all $2\\times2$ matrices.", False, "Example 1: $AB\\neq BA$.")],
        [M("[[k, 2], [3, k+1]] is singular when", "$k=2$ or $k=-3$", ["$k=2$ only", "$k=3$ or $k=-2$", "$k=6$"], "$k(k+1)-6=0$: $(k-2)(k+3)=0$."),
         O("For $A=$ [[2, 1], [3, 4]] and $B=$ [[1, 0], [−1, 2]] verify that $\\det(AB)=\\det A\\,\\det B$.", "$\\det A=5$, $\\det B=2$, so the product is 10. $AB=$ [[1, 2], [−1, 8]] and $\\det(AB)=1\\times8-2\\times(-1)=10$.", ["$\\det A$ and $\\det B$ (1)", "$AB$ (1)", "Equality shown (1)"])],
        P("$M=$ [[1, 2], [3, 5]].", [pn("Find $\\det M$.", -1, "$5-6=-1$.", pts=1), pn("The inverse is $M^{-1}=$ [[p, 2], [3, −1]]. Find $p$.", -5, "$M^{-1}=\\frac{1}{-1}$ [[5, −2], [−3, 1]] $=$ [[−5, 2], [3, −1]].", pts=1), pn("Solve $x+2y=4$, $3x+5y=9$. Find $x$.", -2, "$\\mathbf{x}=M^{-1}$ (4, 9) $=$ (−20+18, 12−9) $=$ (−2, 3).", pts=1), pn("Find $y$.", 3, "From the same product, $y=3$. Check: $3(-2)+5(3)=9$.", pts=1)]),
        [("The identity matrix $I$ satisfies", "$AI=IA=A$", ["$AI=0$", "$AI=A^{-1}$", "$AI=2A$"], "Definition."),
         ("A matrix with $\\det=0$ is called", "singular", ["identity", "orthogonal", "diagonal"], "No inverse."),
         ("A $2\\times3$ matrix times a $3\\times2$ matrix is", "$2\\times2$", ["$3\\times3$", "$2\\times3$", "not defined"], "Outer sizes."),
         ("$\\det$ [[1, 2], [3, 4]] is", "$-2$", ["$2$", "$10$", "$-10$"], "$4-6$."),
         ("To solve $A\\mathbf x=\\mathbf b$ compute", "$A^{-1}\\mathbf b$", ["$\\mathbf bA^{-1}$", "$A\\mathbf b$", "$\\mathbf b-A$"], "Left multiplication.")],
        ill=None, notes=[NOTE], ref=REF)

    # ---------------------------------------------------------- transformations
    assert mm([[0, -1], [1, 0]], [[3], [1]]) == [[-1], [3]] and mm([[1, 2], [0, 1]], [[2], [1]]) == [[4], [1]]
    assert det2([[3, 1], [2, 4]]) == 10 and mm([[0, -1], [1, 0]], [[1, 0], [0, -1]]) == [[0, 1], [1, 0]]
    assert det2([[2, 1], [1, 3]]) == 5 and mm([[2, 1], [1, 3]], [[1], [1]]) == [[3], [4]]
    Mt = [[2, 1], [1, 1]]; assert det2(Mt) == 1 and mm(Mt, [[1], [1]]) == [[3], [2]]
    tf = Axes(-0.5, 3.5, -0.5, 2.5, unit=60, xticks=[1, 2, 3], yticks=[1, 2]).poly([(0, 0), (1, 0), (1, 1), (0, 1)], fill="yellow", color="orange").poly([(0, 0), (2, 1), (3, 2), (1, 1)], fill=None, color="blue")
    tf.text(0.5, 0.4, "unit square", 11).text(2.3, 0.45, "image", 12, anchor="start")
    lesson(ch, "transformations", "Linear transformations",
        ["Find the matrix of a transformation from the images of $(1,0)$ and $(0,1)$", "Use rotation, reflection, enlargement and shear matrices", "Use the determinant as the area scale factor and combine transformations"],
        [("propriete", "Matrix of a transformation", "The matrix [[a, b], [c, d]] maps the point $(x,y)$ to $(ax+by,\;cx+dy)$. Its FIRST column is the image of $(1,0)$ and its SECOND column the image of $(0,1)$. So you can write the matrix straight from the images of the unit vectors."),
         ("propriete", "Standard matrices", "Rotation through $\\theta$ anticlockwise about the origin: [[cos θ, −sin θ], [sin θ, cos θ]]. Reflection in the x-axis [[1, 0], [0, −1]]; in the y-axis [[−1, 0], [0, 1]]; in $y=x$ [[0, 1], [1, 0]]. Enlargement scale factor $k$ centre $O$: [[k, 0], [0, k]]. Shear (x-direction, factor $s$): [[1, s], [0, 1]]."),
         ("formule", "Area and composition", "Area of image $=|\\det M|\\times$ area of object. A negative determinant means the orientation is reversed (a reflection). 'Transformation $B$ followed by $A$' has matrix $AB$ (the first transformation is on the RIGHT). Invariant points satisfy $M\\mathbf x=\\mathbf x$."),
         ("pieges", "Common mistakes", "Writing the images as rows instead of columns. Reversing the order of composition. Forgetting the modulus in the area factor.")],
        [("Example 1", "Find the image of $(3,1)$ under a rotation of $90\\degree$ anticlockwise about $O$.", ["$(1,0)\\to(0,1)$ and $(0,1)\\to(-1,0)$, so the matrix is [[0, −1], [1, 0]].", "Image of $(3,1)$: $(0\\cdot3-1\\cdot1,\;1\\cdot3+0\\cdot1)=(-1,3)$."], "$(-1,3)$"),
         ("Example 2", "$M=$ [[2, 1], [1, 1]]. Find the images of the corners of the unit square and the area scale factor.", ["Images: $(1,0)\\to(2,1)$, $(0,1)\\to(1,1)$, $(1,1)\\to(3,2)$, $O\\to O$.", "$\\det M=2\\times1-1\\times1=1$, so the area stays 1 (the parallelogram on the diagram)."], "Area scale factor 1", tf.fig())],
        [N("[[1, 2], [0, 1]] maps $(2,1)$ to $(x',y')$. Find $x'$.", 4, "$x'=1(2)+2(1)=4$."),
         M("The matrix of reflection in the x-axis is", "[[1, 0], [0, −1]]", ["[[−1, 0], [0, 1]]", "[[0, 1], [1, 0]]", "[[−1, 0], [0, −1]]"], "$(x,y)\\to(x,-y)$."),
         TF("A matrix with determinant $-2$ doubles areas and reverses orientation.", True, "$|\\det|=2$ and the sign is negative.")],
        [M("The unit square is mapped by [[3, 1], [2, 4]]. The area of the image is", "10", ["12", "14", "5"], "$\\det=12-2=10$."),
         O("Show that reflection in the x-axis followed by a $90\\degree$ anticlockwise rotation about $O$ is reflection in the line $y=x$.", "Reflection $X=$ [[1, 0], [0, −1]]; rotation $R=$ [[0, −1], [1, 0]]. 'X then R' is $RX=$ [[0·1+(−1)·0, 0·0+(−1)(−1)], [1·1+0, 0+0]] $=$ [[0, 1], [1, 0]], which is the matrix of reflection in $y=x$.", ["Both matrices (1)", "Product in the right order (1)", "Identifies [[0,1],[1,0]] (1)"])],
        P("The matrix $M=$ [[2, 1], [1, 3]] maps triangle $T$ of area 6 to triangle $T'$.", [pn("Find $\\det M$.", 5, "$6-1=5$.", pts=1), pn("Find the area of $T'$.", 30, "$5\\times6=30$.", pts=1), pn("Find the y-coordinate of the image of $(1,1)$.", 4, "$M(1,1)=(3,4)$.", pts=1), pt("$M$ has an inverse.", True, "$\\det M\\neq0$.", pts=1)]),
        [("The first column of a transformation matrix is the image of", "$(1,0)$", ["$(0,1)$", "$(1,1)$", "$(0,0)$"], "Unit vector."),
         ("Enlargement with scale factor 3 about $O$ has matrix", "[[3, 0], [0, 3]]", ["[[3, 3], [3, 3]]", "[[1, 3], [3, 1]]", "[[0, 3], [3, 0]]"], "Diagonal."),
         ("A rotation matrix has determinant", "1", ["$-1$", "0", "$\\cos\\theta$"], "Area preserved."),
         ("'$B$ then $A$' has matrix", "$AB$", ["$BA$", "$A+B$", "$A^{-1}B$"], "First on the right."),
         ("Invariant points of $M$ satisfy", "$M\\mathbf x=\\mathbf x$", ["$M\\mathbf x=\\mathbf 0$", "$M=I$", "$\\det M=0$"], "Unchanged.")],
        ill=None, notes=[NOTE], ref=REF)

    # ---------------------------------------------------------- complex
    ch = p.chapter("complex", "Complex numbers", REF + " — complex numbers")
    f = lambda z: z ** 3 - 4 * z ** 2 + 6 * z - 4; assert f(2) == 0 and abs(f(1 + 1j)) < 1e-12 and abs(f(1 - 1j)) < 1e-12
    assert (2 + 1j) ** 2 == 3 + 4j and ((2 + 3j) * (4 - 1j)).real == 11 and abs((1 + 1j) ** 4 + 4) < 1e-12
    g = lambda z: z ** 3 - 5 * z ** 2 + 11 * z - 15; assert g(3) == 0 and abs(g(1 + 2j)) < 1e-12 and abs(abs(1 + 2j) - sqrt(5)) < 1e-12
    arg = Axes(-1, 4, -3, 3, unit=40, xticks=[1, 2, 3], yticks=[-2, 2])
    arg.pt(2, 0, "2", color="red", dy=-10).pt(1, 1, "1 + i", color="red", dx=8, dy=-8, anchor="start").pt(1, -1, "1 − i", color="red", dx=8, dy=16, anchor="start")
    lesson(ch, "complex-algebra", "Complex numbers: algebra and polynomial equations",
        ["Calculate with complex numbers and find square roots", "Use conjugate pairs for polynomials with real coefficients", "Solve cubic equations with a complex root"],
        [("definition", "Review", "$z=a+bi$, $i^2=-1$, $\\bar z=a-bi$, $|z|=\\sqrt{a^2+b^2}$, $z\\bar z=|z|^2$. Division: multiply top and bottom by the conjugate of the denominator. Also $|z_1z_2|=|z_1||z_2|$ and $\\overline{z_1z_2}=\\bar z_1\\bar z_2$."),
         ("propriete", "Conjugate root theorem", "If a polynomial has REAL coefficients and $a+bi$ is a root then so is $a-bi$. Hence a real cubic has either three real roots or one real root and a conjugate pair; a real quartic can have two conjugate pairs. The quadratic with roots $a\\pm bi$ is $z^2-2az+(a^2+b^2)=0$."),
         ("methode", "Square root of a complex number", "To find $\\sqrt{x+yi}$ let it equal $a+bi$: then $a^2-b^2=x$ and $2ab=y$. Solve; there are two roots, one the negative of the other."),
         ("pieges", "Common mistakes", "Forgetting the conjugate root when the coefficients are real. Giving only one square root. Writing $\\sqrt{-4}=\\pm2i$ without checking which values the equation needs.")],
        [("Example 1", "$1+i$ is a root of $z^3-4z^2+6z-4=0$. Find all the roots.", ["Coefficients are real, so $1-i$ is also a root. The quadratic factor is $z^2-2z+2$ (sum 2, product 2).", "Divide: $z^3-4z^2+6z-4=(z-2)(z^2-2z+2)$. The third root is $z=2$."], "$z=2,\;1+i,\;1-i$", arg),
         ("Example 2", "Find the square roots of $3+4i$.", ["Let $(a+bi)^2=3+4i$: $a^2-b^2=3$ and $2ab=4$ so $b=\\frac2a$.", "$a^2-\\frac{4}{a^2}=3$ gives $a^4-3a^2-4=0$, $(a^2-4)(a^2+1)=0$, $a=\\pm2$, $b=\\pm1$."], "$\\pm(2+i)$")],
        [N("Find the real part of $(2+3i)(4-i)$.", 11, "$8-2i+12i-3i^2=11+10i$."),
         M("$|z_1z_2|$ equals", "$|z_1||z_2|$", ["$|z_1|+|z_2|$", "$|z_1|\\,/\\,|z_2|$", "$|z_1|^2$"], "Property of modulus."),
         TF("$|z_1z_2|=|z_1||z_2|$ for all complex numbers.", True, "The modulus is multiplicative.")],
        [M("$(1+i)^4=$", "$-4$", ["$4$", "$4i$", "$-4i$"], "$(1+i)^2=2i$; $(2i)^2=-4$."),
         O("A quadratic $z^2+pz+q=0$ with real $p,q$ has $2-i$ as a root. Find $p$ and $q$.", "The other root is $2+i$. Sum $=4=-p$, so $p=-4$. Product $=(2-i)(2+i)=5=q$. The equation is $z^2-4z+5=0$.", ["States the conjugate root (1)", "$p=-4$ (1)", "$q=5$ (1)"])],
        P("$f(z)=z^3-5z^2+11z-15$.", [pn("Find $f(3)$.", 0, "$27-45+33-15=0$.", pts=1), pm("The other two roots are", "$1\\pm2i$", ["$1\\pm i$", "$2\\pm i$", "$-1\\pm2i$"], "$f(z)=(z-3)(z^2-2z+5)$ and $z=1\\pm\\sqrt{-4}$.", pts=2), pn("Find the modulus of one complex root (3 d.p.).", 2.236, "$|1+2i|=\\sqrt5=2.236$.", tol=0.001, pts=1)]),
        [("The conjugate of $3-2i$ is", "$3+2i$", ["$-3+2i$", "$-3-2i$", "$2-3i$"], "Change the sign of the imaginary part."),
         ("The product of the roots $a\\pm bi$ is", "$a^2+b^2$", ["$a^2-b^2$", "$2a$", "$2b$"], "$z\\bar z$."),
         ("A real cubic has", "at least one real root", ["no real roots", "exactly two real roots", "no roots"], "Complex roots come in pairs."),
         ("$i^4=$", "$1$", ["$-1$", "$i$", "$-i$"], "$(i^2)^2$."),
         ("$\\frac{1}{1+i}=$", "$\\frac{1-i}{2}$", ["$1-i$", "$\\frac{1+i}{2}$", "$\\frac{1}{2}$"], "Multiply by the conjugate.")],
        ill=None, notes=[NOTE], ref=REF)

    # polar
    assert abs(abs(1 + 1j * sqrt(3)) - 2) < 1e-12 and abs(cmath.phase(1 + 1j * sqrt(3)) - pi / 3) < 1e-12 and abs((1 + 1j * sqrt(3)) ** 6 - 64) < 1e-9
    z1 = 2 * cmath.exp(1j * pi / 6); z2 = 3 * cmath.exp(1j * pi / 3); assert abs(z1 * z2 - 6j) < 1e-12
    assert abs(abs((3 + 4j) * (5 - 12j)) - 65) < 1e-12 and abs(cmath.phase(-1 + 1j) - 3 * pi / 4) < 1e-12 and abs((1 - 1j) ** 8 - 16) < 1e-9
    close(sqrt(2), 1.4142, 1e-4); close(-pi / 4, -.7854, 1e-4)
    pol = Axes(-1, 3, -1, 3, unit=50, xticks=[1, 2], yticks=[1, 2]).line(0, 0, 1, sqrt(3), color="blue", width=3, arrow="end").pt(1, sqrt(3), "1 + i√3", dx=10, dy=-6, anchor="start")
    pol.line(0, 0, 1, 0, color="grey", width=1).text(0.55, 0.12, "θ = π/3", 11, anchor="start").text(0.3, 1.2, "r = 2", 12)
    lesson(ch, "complex-polar", "Modulus–argument form and De Moivre's theorem",
        ["Write complex numbers in modulus–argument form", "Multiply and divide in polar form", "Use De Moivre's theorem for powers and trigonometric identities"],
        [("formule", "Polar form", "$z=r(\\cos\\theta+i\\sin\\theta)$ with $r=|z|$ and $\\theta=\\arg z$, $-\\pi<\\theta\\leq\\pi$ (principal argument). Find $\\theta$ from $\\tan\\theta=\\frac ba$, checking the quadrant on the Argand diagram."),
         ("propriete", "Products and quotients", "$|z_1z_2|=|z_1||z_2|$ and $\\arg(z_1z_2)=\\arg z_1+\\arg z_2$; $\\left|\\frac{z_1}{z_2}\\right|=\\frac{|z_1|}{|z_2|}$ and $\\arg\\frac{z_1}{z_2}=\\arg z_1-\\arg z_2$ (mod $2\\pi$). Multiplying by $z$ scales by $|z|$ and rotates by $\\arg z$."),
         ("formule", "De Moivre's theorem", "For any integer $n$: $[r(\\cos\\theta+i\\sin\\theta)]^n=r^n(\\cos n\\theta+i\\sin n\\theta)$. Compact notation: $e^{i\\theta}=\\cos\\theta+i\\sin\\theta$. It gives $(\\cos\\theta+i\\sin\\theta)^3=\\cos3\\theta+i\\sin3\\theta$, from which multiple-angle identities follow by comparing real and imaginary parts."),
         ("pieges", "Common mistakes", "A calculator gives $\\arctan\\frac ba$ only in the first or fourth quadrant: $-1+i$ has argument $\\frac{3\\pi}{4}$, not $-\\frac{\\pi}{4}$. Adding arguments may exceed $\\pi$: subtract $2\\pi$ to return to the principal range.")],
        [("Example 1", "Write $1+i\\sqrt3$ in polar form and find $(1+i\\sqrt3)^6$.", ["$r=\\sqrt{1+3}=2$, $\\tan\\theta=\\sqrt3$ in the first quadrant: $\\theta=\\frac\\pi3$.", "$(1+i\\sqrt3)^6=2^6(\\cos2\\pi+i\\sin2\\pi)=64$."], "$2\\left(\\cos\\frac\\pi3+i\\sin\\frac\\pi3\\right)$; the sixth power is 64", pol.fig()),
         ("Example 2", "$z_1=2\\left(\\cos\\frac\\pi6+i\\sin\\frac\\pi6\\right)$, $z_2=3\\left(\\cos\\frac\\pi3+i\\sin\\frac\\pi3\\right)$. Find $z_1z_2$.", ["Multiply moduli: $2\\times3=6$. Add arguments: $\\frac\\pi6+\\frac\\pi3=\\frac\\pi2$.", "$z_1z_2=6\\left(\\cos\\frac\\pi2+i\\sin\\frac\\pi2\\right)=6i$."], "$6i$")],
        [N("Find $|(3+4i)(5-12i)|$.", 65, "$5\\times13$."),
         M("The principal argument of $-1+i$ is", "$\\frac{3\\pi}{4}$", ["$-\\frac\\pi4$", "$\\frac\\pi4$", "$-\\frac{3\\pi}{4}$"], "Second quadrant."),
         TF("$\\arg(z_1z_2)=\\arg z_1+\\arg z_2$ (mod $2\\pi$).", True, "Arguments add on multiplication.")],
        [M("$(\\cos\\theta+i\\sin\\theta)^3=$", "$\\cos3\\theta+i\\sin3\\theta$", ["$\\cos^3\\theta+i\\sin^3\\theta$", "$3\\cos\\theta+3i\\sin\\theta$", "$\\cos\\theta^3+i\\sin\\theta^3$"], "De Moivre."),
         O("Use De Moivre's theorem to show that $\\cos3\\theta=4\\cos^3\\theta-3\\cos\\theta$.", "$(\\cos\\theta+i\\sin\\theta)^3=\\cos^3\\theta+3i\\cos^2\\theta\\sin\\theta-3\\cos\\theta\\sin^2\\theta-i\\sin^3\\theta$. The real part is $\\cos^3\\theta-3\\cos\\theta\\sin^2\\theta=\\cos^3\\theta-3\\cos\\theta(1-\\cos^2\\theta)=4\\cos^3\\theta-3\\cos\\theta$. By De Moivre it also equals $\\cos3\\theta$.", ["Binomial expansion (1)", "Real part (1)", "Uses $\\sin^2=1-\\cos^2$ (1)"])],
        P("Let $z=1-i$.", [pn("Find $|z|$ (4 d.p.).", 1.4142, "$\\sqrt{1+1}=\\sqrt2$.", tol=0.0002, pts=1), pn("Find $\\arg z$ in radians (4 d.p.).", -.7854, "Fourth quadrant: $-\\frac\\pi4$.", tol=0.0002, pts=1), pn("Find $z^8$.", 16, "$(\\sqrt2)^8\\left(\\cos(-2\\pi)+i\\sin(-2\\pi)\\right)=16$.", pts=2)]),
        [("$|z|$ for $z=-3+4i$ is", "$5$", ["$1$", "$7$", "$-5$"], "$\\sqrt{9+16}$."),
         ("$e^{i\\pi}=$", "$-1$", ["$1$", "$i$", "$-i$"], "$\\cos\\pi+i\\sin\\pi$."),
         ("$\\arg(z^n)=$", "$n\\arg z$ (mod $2\\pi$)", ["$\\arg z$", "$(\\arg z)^n$", "$\\frac{\\arg z}{n}$"], "De Moivre."),
         ("$\\arg\\frac{z_1}{z_2}=$", "$\\arg z_1-\\arg z_2$", ["$\\arg z_1+\\arg z_2$", "$\\frac{\\arg z_1}{\\arg z_2}$", "$\\arg z_1\\arg z_2$"], "Subtract."),
         ("$i$ in polar form has modulus and argument", "1 and $\\frac\\pi2$", ["1 and 0", "0 and $\\frac\\pi2$", "$\\frac\\pi2$ and 1"], "On the positive imaginary axis.")],
        ill=None, notes=[NOTE], ref=REF)
