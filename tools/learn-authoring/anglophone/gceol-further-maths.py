import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _kit import *
import fm_algebra, fm_series, fm_trig_coord, fm_calc, fm_rest, fm_mock
p = Pack("gceol-further-maths", "Further Mathematics — GCE O Level", level="Form 5", subject="maths", cursus="secondary", exam="GCE-OL",
         description="Further (Additional) Mathematics for GCE O Level: algebra, trigonometry, coordinate geometry, calculus, vectors, matrices, probability and statistics, with worked examples, graded exercises and a mock paper.",
         programRef="Cameroon GCE Board — O Level Further/Additional Mathematics (to be checked against the official syllabus)", wanted="further-maths")
fm_algebra.build(p)
fm_series.build(p)
fm_trig_coord.build(p)
fm_calc.build(p)
fm_rest.build(p)
fm_mock.build(p)
EXTRA = {
    "differentiation": "Whether differentiation and integration are examined at Cameroon GCE O Level Further Mathematics (rather than only at A Level) is uncertain: flagged for syllabus check. Chain rule for (ax + b)^n is included.",
    "applications": "Optimisation, second-derivative test and kinematics: confirm that they are within the O Level Further Mathematics syllabus.",
    "integration": "Areas between curves and finding C from a point: confirm the syllabus level.",
    "binomial": "Binomial theorem for positive integer n only (no negative or fractional n): confirm the syllabus.",
    "progressions": "Sum to infinity of a GP and recurring decimals: confirm the syllabus.",
    "quadratic-functions": "Sum and product of roots and the discriminant conditions: confirm the syllabus.",
    "simultaneous": "Tangency by the discriminant is standard but confirm the syllabus level.",
    "lines": "The determinant-style area formula for a triangle: confirm that it is expected.",
    "circles": "Circle geometry in coordinates (centre-radius and general form): confirm the syllabus.",
    "matrices-fm": "Matrix transformations and determinant as area factor: confirm the syllabus.",
    "perm-comb": "Permutations and combinations: standard in further mathematics, confirm the syllabus.",
    "probability-fm": "Conditional probability and tree diagrams: confirm the syllabus.",
    "statistics-fm": "Variance and standard deviation: the course may use the population formula (divide by n) or the sample formula (n - 1): confirm which one the Cameroon syllabus uses; this lesson divides by n.",
    "radians": "Radian measure and the segment area formula: confirm the syllabus.",
    "identities-equations": "Double-angle formulae: confirm the syllabus.",
}
for slug, note in EXTRA.items():
    p.LS[slug].d["reviewNotes"].insert(0, note)
p.write()
