import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _kit import *
import gm_number, gm_commercial, gm_algebra, gm_graphs, gm_geometry, gm_mensur, gm_vecmat, gm_trig_stats, gm_mock
p = Pack("gceol-maths", None, None, None, None, extend=True)
gm_number.build(p)
gm_commercial.build(p)
gm_algebra.build(p)
gm_graphs.build(p)
gm_geometry.build(p)
gm_mensur.build(p)
gm_vecmat.build(p)
gm_trig_stats.build(p)
gm_mock.build(p)
p.write()
