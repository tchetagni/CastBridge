"""Helpers for the Further Mathematics packs: exact small-matrix algebra and matrix figures."""
from fractions import Fraction as Fr
from _maths import *

def mm(A, B): return [[sum(A[i][k] * B[k][j] for k in range(len(B))) for j in range(len(B[0]))] for i in range(len(A))]
def det2(A): return A[0][0] * A[1][1] - A[0][1] * A[1][0]
def det3(A):
    a, b, c = A[0]; d, e, f = A[1]; g, h, i = A[2]
    return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
def inv2(A):
    d = Fr(det2(A)); return [[A[1][1] / d, -A[0][1] / d], [-A[1][0] / d, A[0][0] / d]]
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def dot(a, b): return sum(x * y for x, y in zip(a, b))
def S(s): return str(s).replace("-", "−")

def mat_items(x, y, rows, size=16, cell=34, rh=24):
    """matrix with brackets, top-left corner of the first cell centre at (x, y); returns (items, width)"""
    n, m = len(rows), len(rows[0]); w = m * cell; it = []
    top, bot = y - rh * .75, y + rh * (n - 1) + rh * .6
    left, right = x - cell / 2 - 4, x + (m - 1) * cell + cell / 2 + 4
    it += [LINE(left, top, left, bot, width=2), LINE(left, top, left + 6, top, width=2), LINE(left, bot, left + 6, bot, width=2),
           LINE(right, top, right, bot, width=2), LINE(right, top, right - 6, top, width=2), LINE(right, bot, right - 6, bot, width=2)]
    for i, r in enumerate(rows):
        for j, v in enumerate(r): it.append(T(x + j * cell, y + i * rh + 5, S(v), size))
    return it, right - left
