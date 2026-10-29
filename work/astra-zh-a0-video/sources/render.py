#!/usr/bin/env python3
"""Rebuild only this delivery: Python 3 + Pillow, macOS fonts, ffmpeg/libx264.
No audio; deterministic geometry/text and existing Astra raster illustrations.
"""
from pathlib import Path
import json, math, subprocess, hashlib, struct
from PIL import Image, ImageDraw, ImageFont
ROOT = Path(__file__).resolve().parents[1]
W,H,FPS=854,480,24
BG='#fff4df'; INK='#173c40'; TEAL='#007d80'; RUST='#b9482d'
FONT='/System/Library/Fonts/Hiragino Sans GB.ttc'
LATIN='/System/Library/Fonts/Supplemental/Arial.ttf'
fonts={}
def text(im,xy,s,size=32,fill=INK,center=False,zh=False):
    key=(size,zh)
    if key not in fonts: fonts[key]=ImageFont.truetype(FONT if zh else LATIN,size)
    d=ImageDraw.Draw(im); f=fonts[key]
    if center: xy=(xy[0]-d.textlength(s,font=f)/2,xy[1])
    d.text(xy,s,font=f,fill=fill)
def base(title):
    im=Image.new('RGB',(W,H),BG)
    text(im,(42,13),title,38)
    ImageDraw.Draw(im).line((42,69,812,69), fill=TEAL,width=3)
    return im
salut=Image.open(ROOT/'sources/zh-a0-salut-astra-rencontre.webp').convert('RGB')
famille=Image.open(ROOT/'sources/zh-a0-famille-astra-scene.webp').convert('RGB')
def picture(im,src,box,alpha=1):
    x,y,w,h=box
    pic=src.resize((w,h),Image.Resampling.LANCZOS)
    if alpha<1: pic=Image.blend(Image.new('RGB',(w,h),BG),pic,max(0,alpha))
    im.paste(pic,(x,y))
def greetings(t):
    im=base('Une rencontre'); k=min(int(t/3),2)
    picture(im,salut,(42,95,450,300), min(1,t/.6))
    hz,py,fr=[('你好','nǐ hǎo','Bonjour'),('谢谢','xièxie','Merci'),('再见','zàijiàn','Au revoir')][k]
    text(im,(653,145),hz,62,center=True,zh=True)
    text(im,(653,227),py,38,center=True)
    text(im,(653,292),fr,30,center=True)
    text(im,(653,373),f'{k+1} / 3',36,center=True)
    return im
TONES=[('1er ton','mā',[5,5]),('2e ton','má',[3,5]),('3e ton isolé','mǎ',[2,1,4]),('4e ton','mà',[5,1])]
def tones(t):
    im=base('Les tons : suivre la hauteur'); k=min(int(t/3.6),4); local=t-k*3.6
    if k==4:
        text(im,(427,103),'Ton neutre : ma',44,center=True)
        text(im,(427,181),'Bref, sans contour fixe',36,center=True)
        text(im,(427,237),'Sa hauteur dépend du ton précédent.',37,center=True)
        text(im,(427,307),'Exemple : nǐ hǎo ma ?',36,center=True)
        text(im,(427,373),'Pas de « cinquième courbe » universelle.',37,center=True)
        return im
    label,py,levels=TONES[k]
    text(im,(628,125),label,33,center=True)
    text(im,(628,180),py,68,center=True)
    text(im,(628,272),'Écouter plus tard,',37,center=True)
    text(im,(628,316),'puis imiter.',37,center=True)
    d=ImageDraw.Draw(im)
    for level in range(1,6):
        y=335-(level-1)*50
        d.line((110,y,435,y),fill='#c8c9b8',width=2)
        text(im,(66,y-21),str(level),37)
    pts=[(120+i*300/(len(levels)-1),335-(v-1)*50) for i,v in enumerate(levels)]
    progress=min(1,max(0,(local-.4)/1.65))
    for i in range(len(pts)-1):
        p=min(1,max(0,progress*(len(pts)-1)-i))
        if p:
            a,b=pts[i],pts[i+1]; end=(a[0]+(b[0]-a[0])*p,a[1]+(b[1]-a[1])*p)
            d.line((a,end),fill=TEAL,width=10); d.ellipse((end[0]-7,end[1]-7,end[0]+7,end[1]+7),fill=RUST)
    text(im,(427,372),'Schéma isolé ;',37,center=True)
    text(im,(427,414),'en phrase, le 3e ton reste souvent bas.',37,center=True)
    return im
NUMS=[('一','yī'),('二','èr'),('三','sān'),('四','sì'),('五','wǔ'),('六','liù'),('七','qī'),('八','bā'),('九','jiǔ'),('十','shí')]
def count(t,start):
    n=start+min(int(t/3),4); im=base(f'Compter les cahiers : {start} à {start+4}')
    d=ImageDraw.Draw(im)
    for i in range(n):
        x=65+(i%5)*91; y=128+(i//5)*132
        d.rounded_rectangle((x,y,x+66,y+94),radius=5,fill=TEAL if i%2==0 else RUST,outline=INK,width=3)
        d.line((x+11,y+4,x+11,y+88),fill=BG,width=3)
        d.line((x+15,y+83,x+59,y+83),fill=BG,width=4)
    hz,py=NUMS[n-1]
    text(im,(670,102),str(n),74,center=True)
    text(im,(670,199),hz,62,center=True,zh=True)
    text(im,(670,285),py,46,center=True)
    text(im,(427,396),'Un cahier de plus à chaque étape.',37,center=True)
    return im
FAMILY=[('爸爸','bàba','Papa'),('妈妈','māma','Maman'),('哥哥','gēge','Frère aîné'),('姐姐','jiějie','Sœur aînée'),('弟弟','dìdi','Frère cadet'),('妹妹','mèimei','Sœur cadette')]
def family(t):
    k=min(int(t/3),5); hz,py,fr=FAMILY[k]; im=base('Les mots de la famille')
    picture(im,famille,(42,103,390,260),min(1,t/.5))
    text(im,(237,372),'Une famille fictive',37,center=True)
    text(im,(628,100),hz,62,center=True,zh=True)
    text(im,(628,184),py,40,center=True)
    text(im,(628,249),fr,32,center=True)
    if k>=2:
        text(im,(628,310),'Plus âgé(e) que moi' if k<4 else 'Plus jeune que moi',36,center=True)
    else: text(im,(628,310),'Deux syllabes',36,center=True)
    text(im,(600,376),'2e syllabe : ton neutre',36,center=True)
    return im
SPECS=[('salutations',9,greetings),('tons',18,tones),('nombres-1-5',15,lambda t:count(t,1)),('nombres-6-10',15,lambda t:count(t,6)),('famille',18,family)]
def main():
    (ROOT/'out').mkdir(exist_ok=True); (ROOT/'apercus').mkdir(exist_ok=True)
    proof=Image.new('RGB',(854,180),BG)
    text(proof,(30,15),'你好 三 爸爸 妈妈 哥哥 姐姐 弟弟 妹妹',40,zh=True)
    text(proof,(30,90),'mā má mǎ mà  nǐ hǎo  jiějie  ǚ ü',38)
    proof.save(ROOT/'sources/verification-polices.png')
    stats=[]
    for name,duration,render in SPECS:
        path=ROOT/'out'/f'zh-a0-{name}-astra.mp4'
        cmd=['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-f','rawvideo','-pixel_format','rgb24','-video_size',f'{W}x{H}','-framerate',str(FPS),'-i','pipe:0','-an','-c:v','libx264','-preset','medium','-crf','26','-maxrate','480k','-bufsize','960k','-pix_fmt','yuv420p','-movflags','+faststart',str(path)]
        p=subprocess.Popen(cmd,stdin=subprocess.PIPE)
        for frame in range(duration*FPS): p.stdin.write(render(frame/FPS).tobytes())
        p.stdin.close()
        if p.wait()!=0: raise RuntimeError(name)
        meta=json.loads(subprocess.check_output(['/opt/homebrew/bin/ffprobe','-v','error','-show_streams','-show_format','-of','json',str(path)]))
        streams=meta['streams']; v=streams[0]
        assert len(streams)==1 and v['codec_name']=='h264' and v['pix_fmt']=='yuv420p'
        assert v['width']==W and v['height']==H and v['r_frame_rate']=='24/1'
        assert float(meta['format']['duration'])<=20 and path.stat().st_size<=1_500_000
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-v','error','-i',str(path),'-f','null','-'],check=True)
        data=path.read_bytes(); pos=0; atoms=[]
        while pos<len(data):
            size,kind=struct.unpack('>I4s',data[pos:pos+8]); assert size>=8
            atoms.append(kind); pos+=size
        assert atoms.index(b'moov')<atoms.index(b'mdat')
        stats.append(dict(faststart=True,full_decode='OK',file=path.name,duration=duration,bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),codec=v['codec_name'],pix_fmt=v['pix_fmt'],fps=v['r_frame_rate'],width=W,height=H,audio=False))
        print(name,stats[-1]['bytes'],flush=True)
    assert sum(s['bytes'] for s in stats)<=6_000_000
    (ROOT/'VERIFICATIONS.json').write_text(json.dumps(stats,indent=2)+'\n')
    # Exactly three PNG contact sheets; all five videos are represented.
    sheets=[('01-salutations-famille',[('salutations',1),('salutations',7),('famille',1),('famille',13)]),('02-tons',[('tons',2.5),('tons',6),('tons',9.7),('tons',13.4),('tons',16.5)]),('03-nombres',[('nombres-1-5',1),('nombres-1-5',13),('nombres-6-10',1),('nombres-6-10',13)])]
    for sheet,items in sheets:
        canvas=Image.new('RGB',(W*2,H*math.ceil(len(items)/2)),BG)
        for i,(name,t) in enumerate(items):
            raw=subprocess.check_output(['/opt/homebrew/bin/ffmpeg','-v','error','-ss',str(t),'-i',str(ROOT/'out'/f'zh-a0-{name}-astra.mp4'),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
            canvas.paste(Image.frombytes('RGB',(W,H),raw),((i%2)*W,(i//2)*H))
        canvas.save(ROOT/'apercus'/f'{sheet}.png')
if __name__=='__main__': main()
