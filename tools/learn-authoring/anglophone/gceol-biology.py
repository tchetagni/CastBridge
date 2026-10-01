import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from biolib import *
import gb_a
MODS = [gb_a]
for name in ("gb_b", "gb_c", "gb_d", "gb_e", "gb_f", "gb_g", "gb_h"):
    try:
        MODS.append(__import__(name))
    except ImportError as e:
        if name not in str(e): raise
import subprocess  # extend is not idempotent on chapter order: restore the committed pack first
subprocess.run('cd /home/user/CastBridge && git checkout -- content/learn/gceol-biology && git clean -fdq content/learn/gceol-biology', shell=True, check=True)
p = Pack("gceol-biology", None, None, None, None, extend=True)
for m in MODS:
    m.add(p)
if hasattr(sys.modules.get("gb_mock"), "add") or __import__("os").path.exists("/home/user/CastBridge/tools/learn-authoring/anglophone/gb_mock.py"):
    import gb_mock; gb_mock.add(p)
p.write()
