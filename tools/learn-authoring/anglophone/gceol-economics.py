"""Generator: content/learn/gceol-economics (Economics - GCE O Level, Form 5). Run: python3 gceol-economics.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from eco_lib import *
import eco_f5_a
import importlib
p = Pack("gceol-economics", "Economics — GCE O Level", level="Form 5", subject="economie", cursus="secondary", exam="GCE-OL",
         description="O Level Economics notes for the GCE: basic concepts, factors of production, demand and supply, price, firms and costs, money and banking (BEAC, CEMAC, microfinance, njangi), public finance, national income, population and labour, inflation, trade, development, the Cameroonian economy, trade unions and consumer protection, with graded exercises and a mock paper.",
         programRef="Cameroon GCE Board — Ordinary Level Economics syllabus — to be checked against the official texts")
eco_f5_a.build(p)
for m in ("eco_f5_b", "eco_f5_c"):
    if os.path.exists(os.path.join(os.path.dirname(os.path.abspath(__file__)), m + ".py")):
        importlib.import_module(m).build(p)
if getattr(p, "mock_ready", False):
    pass
write_compact(p)
