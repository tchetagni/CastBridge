#!/usr/bin/env python3
"""Logo compact EduPlay (emblème + « EduPlay ») en VectorDrawable, à partir du SVG vtracer du client (rien de redessiné ni de recoloré).
python3 gen_compact_vector.py <eduplay-logo.svg> <sortie.xml> <aperçu.svg> 1000 0.5
  - écarte « THE », le slogan, « par TechnoHub Enterprise » et le filet vert au-dessus du E ;
  - coordonnées entières relatives à l'échelle 0,5 (1 unité = 2 px du SVG) ; les petites bandes de dégradé vert/bleu (< 1000 px² du SVG) sont écartées
    (les petits tracés d'autres teintes - boutons de la télécommande, robot - sont gardés).
Sortie utilisée : android/sender/res/drawable/logo_castbridge_logo_horizontal.xml et ..._light.xml (identiques)."""
import re,sys,subprocess
SVG,OUT,PREV=sys.argv[1:4]
s=open(SVG).read()
items=[]
for m in re.finditer(r'<path d="([^"]*)" fill="([^"]*)" transform="translate\(([-\d.]+),([-\d.]+)\)"',s):
    d,fill,tx,ty=m.group(1),m.group(2),float(m.group(3)),float(m.group(4))
    toks=re.findall(r'[MCLZ]|-?[\d.]+',d)
    assert set(t for t in toks if t.isalpha())<=set('MCLZ'),set(toks)
    out=[];nums=[];cmd=None;xy=0
    for t in toks:
        if t.isalpha(): out.append(t)
        else:
            v=float(t)+(tx if xy%2==0 else ty); xy+=1; out.append(('%.1f'%v).rstrip('0').rstrip('.'))
    pts=[(float(out[i]),float(out[i+1])) for i in range(len(out)-1) if not out[i].isalpha() and not out[i+1].isalpha() and True]
    # bbox from all coordinate numbers (alternating x,y)
    nums=[float(t) for t in out if not t.isalpha()]
    xs=nums[0::2];ys=nums[1::2]
    if not xs: continue
    items.append((' '.join(out),fill,(min(xs),min(ys),max(xs),max(ys))))
print(len(items))
def masked(b):
    x0,y0,x1,y1=b
    if x1<372 and y0>=540 and y1<670: return True       # THE
    if x0>=372 and x1<460 and y0>=632 and y1<670: return True  # filet vert
    if y0>=892 and x1<1020: return True                 # slogan, par TechnoHub
    if y0>=1000: return True
    if x0>=270 and x1<=462 and y0>=640 and y1<=668: return True   # filet vert au-dessus du E, du d et du u
    return False
keep=[i for i in items if not masked(i[2])]
print('kept',len(keep))
# crop bbox
X0=min(i[2][0] for i in keep);Y0=min(i[2][1] for i in keep);X1=max(i[2][2] for i in keep);Y1=max(i[2][3] for i in keep)
print('bbox',X0,Y0,X1,Y1)
SC=float(sys.argv[5]) if len(sys.argv)>5 else 1
MINAREA=float(sys.argv[4]) if len(sys.argv)>4 else 0
def enc(d):
    t=d.split();o=[];k=0;cx=cy=0;px=py=0;i=0;cur=None;buf=[]
    # absolute -> shifted integer absolute -> relative
    out=[];i=0
    while i<len(t):
        c=t[i];i+=1
        n={'M':2,'L':2,'C':6,'Z':0}[c]
        vals=[float(x) for x in t[i:i+n]];i+=n
        vals=[round((v-(X0 if j%2==0 else Y0))*SC) for j,v in enumerate(vals)]
        if c=='Z': out.append('z'); continue
        if c=='M': out.append('M%d %d'%(vals[0],vals[1])); px,py=vals[0],vals[1]; sx,sy=px,py; continue
        if c=='L': out.append('l%d %d'%(vals[0]-px,vals[1]-py)); px,py=vals[0],vals[1]; continue
        rel=[vals[0]-px,vals[1]-py,vals[2]-px,vals[3]-py,vals[4]-px,vals[5]-py]
        px,py=vals[4],vals[5]
        if not any(rel): continue
        s=' '.join(str(v) for v in rel)
        if out and out[-1][0]=='c': out[-1]+=' '+s
        else: out.append('c'+s)
    return ''.join(x if x[0] in 'zM' or True else x for x in out)
merged=[]
for d,f,b in keep:
    import colorsys
    r,g,bl=[int(f[i:i+2],16)/255 for i in (1,3,5)]
    h,sa,v=colorsys.rgb_to_hsv(r,g,bl); h*=360
    shading = sa>0.35 and 70<=h<=250     # bandes de dégradé vertes/bleues : seules écartées quand elles sont petites
    if shading and (b[2]-b[0])*(b[3]-b[1])<MINAREA: continue
    d=enc(d)
    if merged and merged[-1][1]==f: merged[-1][0]+=d
    else: merged.append([d,f])
W=(X1-X0)*SC;H=(Y1-Y0)*SC
print('paths',len(merged),'size',W,H)
def fmt(v): return ('%.1f'%v).rstrip('0').rstrip('.')
vd=['<?xml version="1.0" encoding="utf-8"?>',
'<!-- Logo compact EduPlay (emblème + mot « EduPlay »), vectorisé du SVG fourni par le client (branding/clients/eduplay/logo/eduplay-logo.svg) : « THE », slogan et « par TechnoHub Enterprise » écartés, rien de redessiné ni de recoloré. Généré par branding/clients/eduplay/tools/gen_compact_vector.py : ne pas modifier à la main. -->',
'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
'    android:width="%sdp" android:height="%sdp"'%(fmt(W/4/SC),fmt(H/4/SC)),
'    android:viewportWidth="%s" android:viewportHeight="%s">'%(fmt(W),fmt(H))]
for d,f in merged: vd.append('    <path android:fillColor="#FF%s" android:pathData="%s"/>'%(f[1:].upper(),d))
vd.append('</vector>')
open(OUT,'w').write('\n'.join(vd)+'\n')
sv=['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %s %s">'%(W,H,fmt(W),fmt(H))]
for d,f in merged: sv.append('<path d="%s" fill="%s"/>'%(d,f))
sv.append('</svg>');open(PREV,'w').write('\n'.join(sv))
