import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from common import *
import en_vocab, en_struct, en_writing, en_skills, en_mock
import subprocess, os, glob
R = "/home/user/CastBridge"
# idempotent: restore the committed pack.json and drop previously generated chapter files (extend=True reads pack.json)
subprocess.check_call(["git", "checkout", "HEAD", "--", "content/learn/gceol-english/pack.json"], cwd=R)
tracked = set(subprocess.check_output(["git", "ls-files", "content/learn/gceol-english/lessons"], cwd=R, text=True).split())
for f in glob.glob(R + "/content/learn/gceol-english/lessons/*.json"):
    if os.path.relpath(f, R) not in tracked: os.remove(f)
p = Pack("gceol-english", None, None, None, None, extend=True)
en_vocab.vocab(p)
en_struct.struct(p)
en_writing.writing(p)
en_skills.skills(p)
en_skills.oral(p)
en_mock.mock(p)
p.write()

import json
f = R + "/content/learn/gceol-english/pack.json"
d = json.load(open(f, encoding="utf-8"))
d["description"] = ("Revision sheets, step-by-step worked examples, GCE O Level style exercises with model answers and two mock papers: reading comprehension, grammar, "
                    "reported speech and passive voice, vocabulary and usage, sentence structure, punctuation and spelling, letters, reports, speeches, articles, "
                    "narrative and descriptive essays, summary, inference and figurative language, oral English and listening.")
json.dump(d, open(f, "w", encoding="utf-8"), ensure_ascii=False, indent=2); open(f, "a").write("\n")
