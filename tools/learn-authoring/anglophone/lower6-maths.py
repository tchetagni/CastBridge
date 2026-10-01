import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _maths import *
import l6m_a, l6m_b, l6m_c, l6m_d
p = Pack("lower6-maths", "Mathematics — Lower Sixth", level="Lower Sixth", subject="maths", cursus="secondary",
         description="Pure mathematics year 1, with introductory mechanics and statistics: key points, worked examples, graded exercises with full solutions.",
         programRef="Cameroon GCE Board — Advanced Level Mathematics (Lower Sixth part) — to be checked against the official syllabus")
for m in (l6m_a, l6m_b, l6m_c, l6m_d):
    m.build(p)
p.write()
