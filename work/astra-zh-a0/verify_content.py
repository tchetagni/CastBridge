"""Contrôles statiques ciblés ; ne remplace ni LangValidator ni la relecture native.
Exécution : python3 work/astra-zh-a0/verify_content.py (Pillow requis).
"""
import hashlib
import json
import subprocess
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]

def ids(obj):
    if isinstance(obj, dict):
        return ([obj['id']] if 'id' in obj else []) + [i for v in obj.values() for i in ids(v)]
    if isinstance(obj, list):
        return [i for v in obj for i in ids(v)]
    return []

def strings(obj):
    if isinstance(obj, str):
        yield obj
    elif isinstance(obj, dict):
        for v in obj.values():
            yield from strings(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from strings(v)

size = 0
for theme in ('salut', 'phonetique', 'nombres', 'famille'):
    path = Path(f'content/langues/zh-a0-{theme}-fr')
    p = json.loads((ROOT/path/'langue.json').read_text())
    before = json.loads(subprocess.check_output(['git', 'show', f'HEAD:{path}/langue.json'], cwd=ROOT))
    assert p['version'] == before['version'] and p['state'] == 'review'
    assert set(ids(before)) <= set(ids(p)) and len(ids(p)) == len(set(ids(p)))
    m = json.loads((ROOT/path/'media.json').read_text())['media']
    oldm = json.loads(subprocess.check_output(['git', 'show', f'HEAD:{path}/media.json'], cwd=ROOT))['media']
    assert all(entry in m for entry in oldm), 'Média préexistant altéré'
    media = {e['id']: e for e in m}
    assert len(media) == len(m)
    assert all(s[2:] in media for s in strings(p) if s.startswith('m:'))
    for u in p['units']:
        assert all(v.get('reading') for v in u['vocab'])
        assert all(len(g['md']) <= 650 for g in u['grammar'])
        for d in u['dialogues']:
            assert len(d['lines']) >= 2
            assert all(line.get('reading') and line.get('tr') for line in d['lines'])
        assert all(c['vocab'] in {v['id'] for v in u['vocab']} for c in u['cards'])
        for x in u['exercises']:
            assert x['kind'] in ('mcq', 'speak'), 'Interaction non prévue aux cinq touches'
            assert x.get('explanation') and len(x['prompt']) <= 650
            if x['kind'] == 'mcq':
                assert 2 <= len(x['choices']) <= 6
                assert len(set(x['choices'])) == len(x['choices'])
                assert 0 <= x['correct'] < len(x['choices'])
            else:
                assert x.get('model')
    for e in m:
        if '-astra-' not in e['id']:
            continue
        source = ROOT/path/'media'/Path(e['file']).name
        twin = ROOT/f'content/langues-media/zh-a0-{theme}'/e['file']
        assert source.read_bytes() == twin.read_bytes()
        assert source.stat().st_size == e['bytes'] <= 120_000
        with Image.open(source) as im:
            assert im.format == 'WEBP' and max(im.size) <= 1280
            im.verify()
        assert e['synthetic'] and e['state'] == 'review'
        assert e['voiceLicense'] == 'CASTBRIDGE-ORIGINAL'
        size += e['bytes']
        print(f'{source.relative_to(ROOT)} : {e["bytes"]} octets ; SHA256 {hashlib.sha256(source.read_bytes()).hexdigest()}')
    print(f'{p["id"]} : structure ciblée OK, version {p["version"]} inchangée')
fig = json.loads((ROOT/'content/langues/zh-a0-phonetique-fr/figures/zh-a0-phonetique-fr-u1-a1.json').read_text())
animated = [{k:v for k,v in item.items() if k not in ('id','draw')} for item in fig['animation']['items']]
assert animated == fig['figure']['items'], 'Repli différent du dernier état dessiné'
assert len({x['d'] for x in animated if x['t']=='path'}) == 4
assert size * 2 <= 10_000_000
print(f'OK — {size} octets de WebP uniques ; {size*2} avec les copies locales des lots médias. Aucun test Android exécuté.')
