"""Applies qb/gen/lycee_caps.json to the lycée / GCE generators (this module must load after them: alphabetical order).
The file is produced by tools/quiz-bank/lycee_caps.py: {"caps": {course: {model: max variants}}, "salts": {course: salt}}. The salt re-draws the
positions of the right answers so that A/B/C/D stay within the pipeline's ±5 points. Without the file (or with LYCEE_CAPS=off) nothing changes."""
import json
import os
from pathlib import Path

from .. import core

_FILE = Path(__file__).with_name("lycee_caps.json")


def apply():
    if os.environ.get("LYCEE_CAPS") == "off" or not _FILE.is_file():
        return
    data = json.loads(_FILE.read_text(encoding="utf-8"))
    for g in core.GENS:
        c = data["caps"].get(g.course, {}).get(g.tpl)
        if c is not None:
            g.cap = c
    for course, salt in data.get("salts", {}).items():
        if course in core.COURSES:
            core.COURSES[course]["salt"] = salt


apply()
