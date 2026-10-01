import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from common import *
import lit_prose, lit_poetry, lit_drama, lit_oral, lit_exam, lit_mock
p = Pack("gceol-literature", "Literature in English — GCE O Level", level="Form 5", subject="english", cursus="secondary", exam="GCE-OL", wanted="literature",
         description="Transferable literature skills with original poems, extracts and scenes: prose, poetry, drama, African oral literature, literary terms, context questions, essays on a text and unseen appreciation, with a mock paper. Official set texts are not included.",
         programRef="Cameroon GCE Board, Ordinary Level Literature in English syllabus — general skills only; the official set texts must be added by a teacher (to be checked against the official texts)")
lit_prose.prose(p)
lit_poetry.poetry(p)
lit_drama.drama(p)
lit_oral.oral(p)
lit_exam.exam(p)
lit_mock.mock(p)
p.write()
