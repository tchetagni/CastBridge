"""Shared helpers for the O Level / Form 4 economics packs (re-uses _l6)."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _l6 import *

NOTE_MONEY = "All prices, quantities and FCFA amounts are invented teaching figures, not real market data."
NOTE_SYL = "The order and depth of topics are an editorial choice to be checked against the Cameroon GCE Board O Level Economics syllabus."

def tag(prefix, items):
    """prefix self-check prompts so that they are unique across all content"""
    return [(prefix + q[0],) + tuple(q[1:]) for q in items]
