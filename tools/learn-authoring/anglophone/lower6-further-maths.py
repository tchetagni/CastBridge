import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _fm import *
import l6f_a, l6f_b
p = Pack("lower6-further-maths", "Further Mathematics — Lower Sixth", level="Lower Sixth", subject="maths", cursus="secondary", wanted="further-maths",
         description="Further pure mathematics, year 1: matrices, complex numbers, roots of polynomials, induction, series, hyperbolic functions and vector products.",
         programRef="Cameroon GCE Board — Advanced Level Further Mathematics (Lower Sixth part) — to be checked against the official syllabus")
for m in (l6f_a, l6f_b):
    m.build(p)
p.write()
