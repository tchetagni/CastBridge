#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble docs/guide-utilisateur/guide-utilisateur.html (un seul fichier, hors ligne) à partir de
screens/*.png et screens/annotations.json. Aucune requête externe : CSS/JS en ligne, polices système,
captures en data: URI (JPEG réduit), repères dessinés en HTML/CSS aux bornes réelles des éléments.

Usage : python3 build_guide.py
"""
import base64, io, json, os, re
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
SCR = os.path.join(ROOT, "screens")
OUTFILE = os.path.join(ROOT, "guide-utilisateur.html")

DATA = json.load(open(os.path.join(SCR, "annotations.json"), encoding="utf-8"))
BY = {s["name"]: s for s in DATA["screens"]}
USED = []          # ordre d'apparition (pour n'embarquer que ce qui sert)
STEPS = {}         # name -> [{"step": "4.2", "keys": [...]}]  (écrit dans annotations.json)
sentences = []     # contrôle des 12 mots


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def words(t):
    return len([w for w in re.sub(r"<[^>]+>", "", t).split() if w not in ("«", "»", "·", ":")])


def fig(name, keys, stepno, alt):
    s = BY[name]
    if name not in USED:
        USED.append(name)
    W, H = s["size"]
    kind = {"phone": "ph", "phoneland": "pl", "tvshot": "tv"}[s["kind"]]
    ann = {a["key"]: a for a in s["annotations"]}
    out = ['<figure class="fig %s" style="aspect-ratio:%d/%d"><div class="im im-%s" role="img" aria-label="%s"></div>'
           % (kind, W, H, s["id"], esc(alt))]
    legend = []
    for n, k in enumerate(keys, 1):
        a = ann[k]
        x1, y1, x2, y2 = a["bounds"]
        out.append('<span class="hl" style="left:%.2f%%;top:%.2f%%;width:%.2f%%;height:%.2f%%"></span>'
                   % (100 * x1 / W, 100 * y1 / H, 100 * (x2 - x1) / W, 100 * (y2 - y1) / H))
        bx = min(max(100 * x2 / W, 6), 95)
        by = min(max(100 * y1 / H, 3), 97)
        out.append('<span class="bd" style="left:%.2f%%;top:%.2f%%">%d</span>' % (bx, by, n))
        legend.append((n, a["label"]))
    out.append("</figure>")
    STEPS.setdefault(name, []).append(dict(step=stepno, keys=keys))
    return "".join(out), legend


def step(stepno, name, keys, sentence, alt, note=None, cls=""):
    f, legend = fig(name, keys, stepno, alt)
    assert words(sentence) <= 12, (stepno, sentence, words(sentence))
    sentences.append(sentence)
    leg = "".join('<li><b>%d</b><span>%s</span></li>' % (n, esc(l)) for n, l in legend)
    nt = '<p class="note">%s</p>' % note if note else ""
    return ('<article class="step %s" id="e%s"><span class="no">%s</span>%s<ol class="leg">%s</ol>'
            '<p class="s">%s</p>%s</article>' % (cls, stepno.replace(".", "-"), stepno, f, leg, sentence, nt))


def drawn(stepno, svg, sentence, labels=None, note=None, cls=""):
    assert words(sentence) <= 12, (stepno, sentence, words(sentence))
    sentences.append(sentence)
    leg = ""
    if labels:
        leg = '<ol class="leg">' + "".join('<li><b>%d</b><span>%s</span></li>' % (i, esc(l)) for i, l in enumerate(labels, 1)) + "</ol>"
    nt = '<p class="note">%s</p>' % note if note else ""
    return ('<article class="step drawn %s" id="e%s"><span class="no">%s</span><figure class="dr">%s'
            '<figcaption>Dessin, pas une capture</figcaption></figure>%s<p class="s">%s</p>%s</article>'
            % (cls, stepno.replace(".", "-"), stepno, svg, leg, sentence, nt))


# ------------------------------------------------------------------ dessins SVG
def svg(w, h, body, label):
    return ('<svg viewBox="0 0 %d %d" role="img" aria-label="%s">%s</svg>'
            % (w, h, esc(label), body))


def tv(x, y, w=120, h=72, fill="none", inner=""):
    return ('<g><rect x="%d" y="%d" width="%d" height="%d" rx="6" class="st" fill="%s"/>'
            '<rect x="%d" y="%d" width="44" height="6" rx="3" class="fl"/>%s</g>'
            % (x, y, w, h, fill, x + w // 2 - 22, y + h + 6, inner))


def phone(x, y, w=44, h=84, inner="", fill="none"):
    return ('<g><rect x="%d" y="%d" width="%d" height="%d" rx="8" class="st" fill="%s"/>'
            '<circle cx="%d" cy="%d" r="2.5" class="fl"/>%s</g>' % (x, y, w, h, fill, x + w // 2, y + h - 7, inner))


def wifi(cx, cy, r=26, cls="ac"):
    return ('<g class="%s"><path d="M%d %d a%d %d 0 0 1 %d 0" class="ln"/>'
            '<path d="M%d %d a%d %d 0 0 1 %d 0" class="ln"/><circle cx="%d" cy="%d" r="4" class="fa"/></g>'
            % (cls, cx - r, cy - r // 3, r, r, 2 * r, cx - r * 2 // 3, cy, r * 2 // 3, r * 2 // 3,
               r * 4 // 3, cx, cy + r // 3))


def txt(x, y, t, size=12, anchor="middle", cls="tx"):
    return '<text x="%d" y="%d" font-size="%d" text-anchor="%s" class="%s">%s</text>' % (x, y, size, anchor, cls, esc(t))


def arrow(x1, y1, x2, y2):
    return '<path d="M%d %d L%d %d" class="ln ac" marker-end="url(#ah)"/>' % (x1, y1, x2, y2)


DEFS = ('<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>'
        '<marker id="ah" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">'
        '<path d="M0 0 L10 5 L0 10 z" class="fa"/></marker></defs></svg>')

ICON_TV = svg(120, 100, tv(10, 8, 100, 62) + wifi(60, 40, 18, "ac"), "Une TV Android")
ICON_PHONE = svg(120, 100, phone(38, 6, 44, 88, wifi(60, 42, 14)), "Un téléphone Android")
ICON_WIFI = svg(160, 100, phone(6, 10, 34, 70) + tv(90, 14, 64, 42) + wifi(75, 70, 24) , "Le même Wi-Fi pour le téléphone et la TV")


def svg_installer_phone():
    app = '<rect x="14" y="30" width="32" height="32" rx="8" class="st" fill="#f5b025"/>' \
          '<path d="M22 50 a8 8 0 0 1 16 0" class="ln" style="stroke:#1b2540"/>'
    return svg(200, 110, phone(30, 8, 56, 94, '<g transform="translate(16 20)">%s</g>' % app.replace('x="14"', 'x="8"')) +
               txt(110, 50, "CastBridge", 14, "start", "tx b") + arrow(108, 62, 92, 64), "Installer CastBridge sur le téléphone")


def svg_tv_install():
    p1 = svg(150, 100, '<rect x="30" y="38" width="64" height="26" rx="4" class="st"/><rect x="94" y="45" width="20" height="12" class="fl"/>'
             + txt(62, 55, "USB", 11) + txt(75, 88, "castbridge-tv.apk", 11), "Clé USB avec le fichier castbridge-tv.apk")
    p2 = svg(150, 100, tv(18, 10, 100, 58) + '<rect x="118" y="72" width="26" height="12" class="fl"/>'
             + arrow(142, 92, 128, 86) + txt(75, 96, "Branchez la clé", 11), "Clé USB branchée sur la TV")
    p3 = svg(150, 100, '<rect x="14" y="10" width="122" height="78" rx="6" class="st"/><rect x="14" y="10" width="122" height="14" rx="6" class="fl"/>'
             + '<rect x="24" y="34" width="14" height="14" rx="2" class="fa"/>' + txt(44, 45, "Download", 10, "start")
             + '<rect x="24" y="56" width="14" height="14" rx="2" class="fa"/>' + txt(44, 67, "castbridge-tv.apk", 10, "start"),
             "Explorateur de fichiers de la TV avec le fichier castbridge-tv.apk")
    p4 = svg(150, 100, '<rect x="20" y="14" width="110" height="72" rx="8" class="st"/>' + txt(75, 40, "castbridge-tv.apk", 11)
             + '<rect x="40" y="52" width="70" height="24" rx="12" class="fa"/>' + '<text x="75" y="68" font-size="12" text-anchor="middle" fill="#1b2540" font-weight="700">Installer</text>',
             "Bouton Installer d'Android")
    caps = ["Copiez castbridge-tv.apk sur une clé USB.", "Branchez la clé sur la TV.",
            "Ouvrez l'explorateur de fichiers, dossier Download.", "Touchez castbridge-tv.apk, puis Installer."]
    return ('<div class="quad">' + "".join('<div class="q"><span class="qn">%d</span>%s<p>%s</p></div>' % (i + 1, s, esc(c))
                                          for i, (s, c) in enumerate(zip([p1, p2, p3, p4], caps))) + '</div>')


def svg_activation():
    p1 = svg(150, 100, tv(14, 8, 122, 66, inner="") + txt(75, 30, "Code d'appareil", 10) + txt(75, 52, "XXXX-XXXX-XXXX-XXXX", 9, "middle", "tx b"),
             "La TV affiche son code d'appareil")
    p2 = svg(150, 100, phone(20, 8, 40, 80) + phone(90, 8, 40, 80) + arrow(62, 40, 88, 40) + txt(75, 98, "code → votre agent", 10),
             "Le code d'appareil est envoyé à votre agent")
    p3 = svg(150, 100, phone(20, 8, 40, 80) + tv(76, 14, 64, 40) + arrow(62, 40, 74, 36) +
             '<path d="M92 34 l6 6 l12 -14" class="ln ok"/>' + txt(108, 80, "Activée", 11), "La clé d'activation envoyée à la TV")
    caps = ["La TV affiche son « Code d'appareil ».", "Donnez ce code à votre agent.", "Collez la clé reçue sur le téléphone."]
    return ('<div class="quad tri">' + "".join('<div class="q"><span class="qn">%d</span>%s<p>%s</p></div>' % (i + 1, s, esc(c))
                                              for i, (s, c) in enumerate(zip([p1, p2, p3], caps))) + '</div>')


def trio(items, cls="quad tri"):
    return ('<div class="%s">' % cls + "".join('<div class="q"><span class="qn">%d</span>%s<p>%s</p></div>' % (i + 1, sv, esc(c))
                                              for i, (sv, c) in enumerate(items)) + '</div>')


def svg_activation_wifi():
    p1 = svg(150, 100, tv(14, 8, 122, 66) + txt(75, 28, "Par le Wi-Fi", 10) + txt(75, 44, "code de connexion", 9) +
             txt(75, 62, "482915 · TV 192.168.1.20", 8, "middle", "tx b"), "La TV verrouillée affiche son code de connexion")
    p2 = svg(150, 100, phone(14, 8, 40, 80) + wifi(75, 40, 16) + tv(88, 18, 56, 36) +
             '<rect x="90" y="62" width="52" height="16" rx="8" class="fa"/>' + txt(116, 74, "Ma TV", 9), "Le téléphone trouve la TV sur le Wi-Fi")
    p3 = svg(150, 100, phone(14, 8, 40, 80) + tv(76, 14, 64, 40) + arrow(56, 40, 74, 36) +
             '<rect x="26" y="30" width="16" height="12" rx="3" class="fa"/>' + '<path d="M92 34 l6 6 l12 -14" class="ln ok"/>' + txt(108, 80, "Activée", 11),
             "Le code de connexion active la TV")
    return trio([(p1, "La TV affiche « Par le Wi-Fi : code de connexion »."),
                 (p2, "Sur le téléphone, « Activer la TV » : touchez votre TV."),
                 (p3, "Tapez le code de connexion : la TV s'active.")])


def svg_usb_picker():
    p1 = svg(150, 100, '<rect x="14" y="8" width="122" height="84" rx="6" class="st"/><rect x="14" y="8" width="122" height="14" rx="6" class="fl"/>'
             + '<rect x="22" y="30" width="12" height="12" rx="2" class="fa"/>' + txt(40, 40, "notes.txt", 10, "start")
             + '<rect x="22" y="50" width="12" height="12" rx="2" class="fa"/>' + txt(40, 60, "activation", 10, "start")
             + '<rect x="22" y="70" width="12" height="12" rx="2" class="fa"/>' + txt(40, 80, "photo.jpg", 10, "start"),
             "L'explorateur de la TV liste tous les fichiers")
    p2 = svg(150, 100, '<rect x="14" y="8" width="122" height="84" rx="6" class="st"/><rect x="22" y="30" width="106" height="16" rx="4" class="st" style="stroke:var(--hl)"/>'
             + txt(32, 42, "activation", 10, "start") + txt(75, 70, "La clé est cherchée", 10) + txt(75, 84, "dans le fichier", 10), "La clé est cherchée dans le fichier choisi")
    return trio([(p1, "L'explorateur de la TV liste tous les fichiers."),
                 (p2, "Touchez votre fichier : la clé peut être n'importe où.")], "quad")


def svg_code_box():
    b = ('<rect x="10" y="6" width="180" height="108" rx="10" class="st"/>' + txt(100, 26, "La TV ne reconnaît plus ce téléphone", 9, "middle", "tx b")
         + '<rect x="24" y="36" width="152" height="26" rx="6" class="st"/>' + txt(32, 53, "Code affiché sur la TV", 10, "start")
         + '<text x="168" y="54" font-size="14" text-anchor="end" class="tx">••••••</text>'
         + '<rect x="40" y="74" width="120" height="28" rx="14" class="fa"/>'
         + '<text x="100" y="92" font-size="12" text-anchor="middle" fill="#1b2540" font-weight="700">Valider et envoyer</text>')
    return svg(200, 120, b, "Boîte d'envoi avec le champ Code affiché sur la TV")


def svg_progress():
    b = ('<rect x="10" y="8" width="200" height="90" rx="10" class="st"/>' + txt(110, 28, "Film.mp4", 11, "middle", "tx b")
         + '<rect x="24" y="38" width="172" height="10" rx="5" class="st"/><rect x="24" y="38" width="124" height="10" rx="5" class="fa"/>'
         + '<rect x="24" y="38" width="104" height="10" rx="5" class="fl"/>'
         + txt(24, 66, "74 % envoyés · 60 % confirmés", 10, "start") + txt(24, 84, "Wi-Fi de la TV lent : ~300 Ko/s · ~20 min restantes", 8, "start", "tx b"))
    return svg(220, 106, b, "Copie : envoyés, confirmés et Wi-Fi de la TV lent")


def svg_stop_cause():
    b = (tv(20, 10, 80, 52) + '<path d="M50 28 l20 20 M70 28 l-20 20" class="ln er"/>' + phone(116, 12, 32, 60)
         + txt(75, 100, "Copie arrêtée : la cause est dite", 10))
    return svg(160, 108, b, "Une copie impossible s'arrête avec sa cause")


def svg_remote():
    b = ('<rect x="50" y="4" width="60" height="132" rx="18" class="st"/><circle cx="80" cy="46" r="20" class="st"/>'
         '<circle cx="80" cy="46" r="8" class="fa"/>' + txt(80, 50, "OK", 8, "middle", "tx b") +
         '<path d="M80 22 l-5 6 h10 z M80 70 l-5 -6 h10 z M56 46 l6 -5 v10 z M104 46 l-6 -5 v10 z" class="fl"/>'
         '<rect x="58" y="80" width="20" height="14" rx="6" class="st"/><text x="68" y="91" font-size="8" text-anchor="middle" class="tx">|◀◀</text>'
         '<rect x="82" y="80" width="20" height="14" rx="6" class="st"/><text x="92" y="91" font-size="8" text-anchor="middle" class="tx">▶▶|</text>'
         '<rect x="58" y="102" width="44" height="14" rx="7" class="st"/>' + txt(80, 112, "Retour", 8))
    return svg(160, 140, b, "Télécommande : pavé, OK, retour, boutons de saut")


def svg_wd5():
    b = tv(20, 14, 70, 44) + phone(112, 10, 30, 56) + wifi(80, 84, 20) + txt(80, 106, "Wi-Fi Direct : 5 GHz", 11, "middle", "tx b")
    return svg(160, 114, b, "Wi-Fi Direct entre la TV et le téléphone")


def svg_menu_bg():
    b = ('<rect x="14" y="8" width="132" height="84" rx="8" class="st"/>' + txt(80, 26, "MENU", 10, "middle", "tx b")
         + '<rect x="24" y="36" width="112" height="30" rx="6" class="st" style="stroke:var(--hl)"/>'
         + txt(80, 48, "Autoriser CastBridge-TV", 8) + txt(80, 60, "à rester actif en arrière-plan", 8))
    return svg(160, 100, b, "Ligne du MENU pour rester actif en arrière-plan")


def svg_decision():
    cards = [
        ("Copier sur la TV et lire", "Je regarde tout de suite et je garde le fichier.",
         tv(6, 6, 60, 36) + '<path d="M30 16 l12 8 l-12 8 z" class="fa"/>' + phone(80, 4, 26, 46) + arrow(78, 28, 70, 26)),
        ("Copier sur la TV", "Je garde pour plus tard, sans lecture.",
         tv(6, 6, 60, 36) + '<rect x="24" y="14" width="24" height="18" rx="3" class="fa"/>' + phone(80, 4, 26, 46) + arrow(78, 28, 70, 26)),
        ("Déplacer vers la TV", "Je libère de la place sur le téléphone.",
         tv(6, 6, 60, 36) + '<rect x="24" y="14" width="24" height="18" rx="3" class="fa"/>' + phone(80, 4, 26, 46, '<path d="M88 20 l10 10 M98 20 l-10 10" class="ln er"/>') + arrow(78, 28, 70, 26)),
        ("Lire en direct", "Je regarde sans rien garder, même Wi-Fi.",
         tv(6, 6, 60, 36) + '<path d="M30 16 l12 8 l-12 8 z" class="fa"/>' + phone(80, 4, 26, 46) + wifi(75, 58, 12)),
    ]
    h = ['<div class="decide">']
    for t, d, ic in cards:
        h.append('<div class="dc"><svg viewBox="0 0 116 66" role="img" aria-label="%s">%s</svg><b>« %s »</b><p>%s</p></div>'
                 % (esc(t), ic, esc(t), esc(d)))
    h.append("</div>")
    return "".join(h)


def svg_queue():
    return ('<div class="qbar" role="img" aria-label="Barre : Envoi vers la TV, 2 en attente">'
            '<span>Envoi vers la TV : « Film » · 2 en attente</span><b>Voir</b></div>')


def svg_case(kind):
    if kind == "tvoff":
        b = tv(20, 10, 80, 52, "none") + '<rect x="20" y="10" width="80" height="52" rx="6" fill="#0003"/>' + phone(110, 12, 32, 60) + \
            '<path d="M70 82 h40" class="ln ac"/><path d="M96 76 l8 6 l-8 6" class="ln ac"/>' + txt(60, 98, "en attente…", 10)
        return svg(150, 104, b, "TV éteinte : les fichiers attendent")
    if kind == "mute":
        b = tv(20, 10, 90, 56) + '<path d="M60 30 h8 l10 -8 v28 l-10 -8 h-8z" class="fa"/><path d="M84 28 l16 16 M100 28 l-16 16" class="ln er"/>'
        return svg(150, 100, b, "Pas de son")
    if kind == "sleep":
        b = phone(50, 8, 50, 88, "") + '<rect x="50" y="8" width="50" height="88" rx="8" fill="#0006"/>' + txt(118, 24, "z", 18, "start", "tx b") + txt(128, 14, "z", 13, "start", "tx b")
        return svg(150, 104, b, "Téléphone en veille")
    if kind == "wifi2":
        b = phone(12, 14, 34, 66) + wifi(29, 96, 10, "er") + tv(70, 18, 72, 44) + wifi(106, 90, 14, "ac") + '<path d="M54 46 l10 0" class="ln er"/>' + txt(29, 108, "A", 11) + txt(106, 108, "B", 11)
        return svg(150, 112, b, "Téléphone et TV sur deux Wi-Fi différents")
    if kind == "help":
        b = '<circle cx="75" cy="44" r="32" class="st"/><text x="75" y="58" font-size="40" text-anchor="middle" class="tx b">?</text>'
        return svg(150, 90, b, "Demander de l'aide")
    if kind == "code":
        return svg_code_box()
    if kind == "slow":
        return svg_progress()
    if kind == "stop":
        return svg_stop_cause()
    if kind == "reboot":
        b = tv(20, 10, 80, 52) + '<path d="M60 26 a12 12 0 1 1 -10 6" class="ln ac"/><path d="M46 24 l4 10 l-10 0" class="ln ac"/>' + txt(75, 96, "La TV repart seule", 10)
        return svg(150, 104, b, "La TV redémarre")
    if kind == "bg":
        return svg_menu_bg()
    if kind == "telegram":
        b = phone(50, 6, 50, 88) + '<path d="M62 30 l26 10 l-26 10 l6 -10 z" class="fa"/>' + txt(75, 104, "Repartagez le fichier", 10)
        return svg(150, 110, b, "Partager à nouveau le fichier")
    raise KeyError(kind)


def case(no, title, svgx, sentence, extra=""):
    assert words(sentence) <= 12, sentence
    sentences.append(sentence)
    return ('<article class="step drawn case" id="c%d"><span class="no">%d</span><figure class="dr">%s<figcaption>Dessin, pas une capture</figcaption></figure>'
            '<h4>%s</h4><p class="s">%s</p>%s</article>' % (no, no, svgx, title, sentence, extra))


def case_real(no, title, name, keys, sentence, alt):
    f, legend = fig(name, keys, "7.%d" % no, alt)
    assert words(sentence) <= 12, sentence
    sentences.append(sentence)
    leg = "".join('<li><b>%d</b><span>%s</span></li>' % (n, esc(l)) for n, l in legend)
    return ('<article class="step case" id="c%d"><span class="no">%d</span>%s<h4>%s</h4><ol class="leg">%s</ol><p class="s">%s</p></article>'
            % (no, no, f, title, leg, sentence))


def grid(items, cls):
    return '<div class="steps %s">%s</div>' % (cls, "".join(items))


# ---------------------------------------------------------------------- contenu
def build_body():
    B = []
    # 1 -------------------------------------------------------------------
    B.append('<section id="materiel"><h2><span>1</span>Ce qu\'il vous faut</h2>'
             '<div class="need">'
             '<div class="nd">%s<b>Une TV Android</b></div>'
             '<div class="nd">%s<b>Un téléphone Android</b></div>'
             '<div class="nd">%s<b>Le même Wi-Fi</b></div></div>'
             '<p class="note c">Une box ou un partage de connexion suffit. La TV n\'a pas besoin d\'Internet.</p></section>'
             % (ICON_TV, ICON_PHONE, ICON_WIFI))
    # 2 -------------------------------------------------------------------
    s2 = [
        drawn("2.1", svg_installer_phone(), "Installez CastBridge sur le téléphone."),
        step("2.2", "telephone-donnees-et-consentement", ["essentiel", "stats"],
             "Choisissez ce que vous partagez : l'essentiel, ou aussi les statistiques.",
             "Écran CastBridge et vos données du téléphone"),
    ]
    B.append('<section id="installer"><h2><span>2</span>Installer et activer</h2>'
             '<h3>Sur le téléphone</h3>' + grid(s2, "ph") +
             '<h3>Sur la TV : installer depuis une clé USB</h3>' + svg_tv_install() +
             '<h3>Activer la TV : par le Wi-Fi, en premier</h3>' + svg_activation_wifi() +
             '<p class="note c">Pas de Wi-Fi commun ? Le Bluetooth prend le relais.</p>' +
             '<h3>Activer la TV : avec un code d\'appareil</h3>' + svg_activation() +
             grid([step("2.3", "telephone-activer-la-tv", ["conditions", "etapes"],
                        "Lisez les conditions, puis collez la clé d'activation reçue.",
                        "Écran Activer la TV du téléphone",
                        note="Autre voie : le fichier « activation » sur une clé USB branchée sur la TV.")], "ph") +
             '<h3>Activer la TV : avec une clé USB</h3>' + svg_usb_picker() +
             '<p class="note c">La clé peut être n\'importe où dans le fichier.</p>' +
             '<aside class="box"><h4>Si votre agent vous a remis une location…</h4>'
             '<p>Ouvrez « Activer la TV », puis « Locations ».</p>' +
             grid([step("2.4", "telephone-locations", ["choisir", "loc", "envoyer"],
                        "Touchez « Choisir les fichiers », puis « 2. Envoyer à la TV ».",
                        "Écran Locations sur la TV du téléphone")], "ph") + '</aside></section>')
    # 3 -------------------------------------------------------------------
    chips = "".join('<span class="chip">%s</span>' % t for t in ["TV DLNA", "CastBridge TV", "Jeux", "Sur le téléphone", "Apprendre", "Parental"])
    intro = [
        step("3.1", "telephone-onglet-castbridge-tv", ["onglet", "ajouter"],
             "Ouvrez CastBridge : l'onglet « CastBridge TV » s'affiche en premier.", "Premier écran du téléphone"),
        step("3.2", "telephone-onglets-suite", ["surtel", "apprendre", "parental"],
             "Faites glisser les onglets : il y en a six.", "Onglets du téléphone, suite", note=chips),
        step("3.3", "tv-accueil-avec-videos", ["biblio", "ajouter", "apprendre", "langues", "jeux", "telech"],
             "Sur la TV, l'accueil montre une tuile par fonction.", "Accueil de CastBridge-TV", cls="wide"),
        step("3.4", "tv-tuile-recevoir-du-telephone", ["recevoir", "usb", "bt", "internet", "wd"],
             "Les tuiles de droite : code, clé USB, Bluetooth, Internet.", "Tuiles suivantes de l'accueil de la TV", cls="wide"),
    ]
    wayA = [
        step("3.5", "tv-accueil-avec-videos", ["ajouter"], "Sur la TV, ouvrez la tuile « Ajouter un téléphone ».", "Tuile Ajouter un téléphone", cls="wide"),
        step("3.6", "tv-bluetooth-visible", ["allow"], "Si la TV demande d'être visible en Bluetooth, acceptez.", "Fenêtre Bluetooth de la TV", cls="wide"),
        step("3.7", "tv-ajouter-un-telephone", ["consigne", "visible", "prolonger"], "La TV attend votre téléphone pendant quelques minutes.", "Écran Ajouter un téléphone", cls="wide"),
        step("3.8", "telephone-onglet-castbridge-tv", ["ajouter"], "Sur le téléphone, touchez « Ajouter ma TV (Bluetooth, sans code) ».", "Bouton Ajouter ma TV"),
    ]
    wayB = [
        step("3.9", "tv-tuile-recevoir-du-telephone", ["recevoir"], "Sur la TV, ouvrez la tuile « Recevoir du téléphone ».", "Tuile Recevoir du téléphone", cls="wide"),
        step("3.10", "tv-aide-recevoir-du-telephone", ["titre", "compris"], "La TV montre son code (masqué ici) : touchez « Compris ».", "Aide de la TV", cls="wide"),
        step("3.11", "telephone-onglet-castbridge-tv", ["manuel"], "Sur le téléphone, touchez « Ma TV n'apparaît pas… ».", "Lien Ma TV n'apparaît pas"),
        step("3.12", "telephone-adresse-manuelle", ["case", "ip", "pin"], "Cochez la case, puis tapez l'adresse de la TV et son code.", "Saisie manuelle de la TV",
             note="L'adresse de la TV est dans « Connexion &amp; réglages » sur la TV."),
        step("3.13", "telephone-code-saisi-masque", ["ip", "pin"], "Le code que vous tapez reste caché par des points.", "Champs adresse et code remplis"),
        step("3.14", "telephone-tv-jointe", ["surlatv"], "Quand « SUR LA TV » apparaît, la TV est jointe.", "Téléphone relié à la TV"),
    ]
    B.append('<section id="relier"><h2><span>3</span>Relier le téléphone à la TV</h2>' + grid(intro[:2], "ph") + grid(intro[2:], "tv") +
             '<h3>Voie A : Bluetooth, sans code</h3>' + grid(wayA[:3], "tv") + grid(wayA[3:], "ph") +
             '<h3>Voie B : avec le code de la TV</h3>' + grid(wayB[:2], "tv") + grid(wayB[2:], "ph") +
             '<h3>Wi-Fi Direct</h3>' + grid([drawn("3.15", svg_wd5(), "En Wi-Fi Direct, la TV demande la 5 GHz si possible.")], "ph") + '</section>')
    # 4 -------------------------------------------------------------------
    s4 = [
        step("4.1", "telephone-sur-le-telephone", ["videos", "video"], "Dans « Sur le téléphone », touchez la vidéo à envoyer.", "Onglet Sur le téléphone"),
        step("4.2", "telephone-ouvrir-avec-castbridge", ["copierlire", "copier", "deplacer", "lireici"],
             "Dans « Ouvrir avec CastBridge », choisissez comment envoyer.", "Ouvrir avec CastBridge",
             note="Les boutons sont grisés sur cette capture : aucune TV n'était reliée."),
        step("4.3", "telephone-lecteur", ["lire"], "Dans le lecteur, touchez « Lire sur la TV ».", "Lecteur du téléphone", cls="wide2"),
        step("4.4", "telephone-feuille-lire-sur-la-tv", ["titre", "tv"], "La feuille liste les TV trouvées et les façons d'envoyer.", "Feuille Lire sur la TV", cls="wide2"),
    ]
    s4b = [
        drawn("4.5", svg_queue(), "La barre du haut montre l'envoi et les fichiers en attente."),
        step("4.6", "tv-bibliotheque", ["toutes", "carte1", "carte2"], "Les vidéos reçues arrivent dans la « Bibliothèque » de la TV.", "Bibliothèque de la TV", cls="wide"),
        step("4.7", "tv-lecture", [], "Touchez OK sur une vidéo : elle se lit.", "Une vidéo se lit sur la TV", cls="wide"),
    ]
    B.append('<section id="envoyer"><h2><span>4</span>Envoyer une vidéo</h2>' + grid(s4[:2], "ph") + grid(s4[2:], "land") +
             '<h3>Quelle façon choisir ?</h3>' + svg_decision() + '<h3>Suivre l\'envoi</h3>' + grid(s4b, "tv") +
             grid([drawn("4.8", svg_progress(), "La copie montre « envoyés » et « confirmés ».",
                         note="Sur un Wi-Fi lent, la ligne le dit : débit et temps restant."),
                   drawn("4.9", svg_stop_cause(), "Une copie impossible s'arrête, avec sa cause.")], "ph") +
             '<h3>Quand la TV demande le code</h3>' +
             grid([drawn("4.10", svg_code_box(), "Si la TV ne vous reconnaît plus, tapez son code.",
                         note="Touchez « Valider et envoyer » : le code est gardé ensuite.")], "ph") +
             '<h3>Télécommande</h3>' +
             grid([drawn("4.11", svg_remote(), "Pavé et OK pour choisir, Retour pour revenir.", note="Avancer ou reculer de 10 secondes : ▶▶| avance, |◀◀ recule.")], "ph") + '</section>')
    # 5 -------------------------------------------------------------------
    langs = "".join('<span class="chip">%s</span>' % t for t in ["Chinois", "anglais", "allemand", "français", "italien", "espagnol", "japonais"])
    s5a = [
        step("5.1", "telephone-apprendre", ["lecons", "piloter", "parents", "lots"], "L'onglet « Apprendre » propose des leçons, de la maternelle à la licence.", "Onglet Apprendre"),
        step("5.2", "telephone-jeux", ["quiz", "echecs", "sudoku"], "L'onglet « Jeux » propose Quiz des Millions, Échecs, Sudoku.", "Onglet Jeux"),
    ]
    s5b = [
        step("5.3", "tv-apprendre", ["eleve", "classe", "contenus"], "Sur la TV, la tuile « Apprendre » ouvre les élèves.", "Apprendre sur la TV"),
        step("5.4", "tv-jeux", ["quiz", "echecs", "sudoku"], "Sur la TV, la tuile « Jeux » ouvre les mêmes jeux.", "Jeux sur la TV"),
        step("5.5", "tv-quiz-des-millions", ["amis", "entrainement", "scores"], "Dans Quiz des Millions, choisissez un mode, puis jouez.", "Menu du Quiz des Millions"),
    ]
    s5c = [
        step("5.6", "tv-langues", ["chinois", "japonais", "maj"], "La tuile « Langues » liste les langues déjà reçues.", "Langues sur la TV"),
        step("5.7", "tv-langue-niveaux", ["niveau"], "Choisissez un niveau : les leçons se lisent sans Internet.", "Niveaux d'une langue"),
    ]
    s5d = [
        step("5.8", "telephone-donnees-hors-ligne", ["wifi", "maj", "envoyer"], "Dans « Données hors ligne » : mettez à jour, puis envoyez.", "Données hors ligne"),
        step("5.9", "telephone-donnees-hors-ligne", ["langue", "telecharger"], "Choisissez la langue, puis « Télécharger mes leçons de langue ».", "Section Langues de Données hors ligne",
             note="Pour un lot précis : « Envoyer à la TV » ou « Télécharger et envoyer »."),
        step("5.10", "telephone-donnees-hors-ligne-suite", ["actualiser", "voir", "surlatv"], "Plus bas : les leçons du serveur et l'état de la TV.", "Données hors ligne, suite"),
    ]
    B.append('<section id="apprendre"><h2><span>5</span>Apprendre, Quiz, Langues</h2><h3>Sur le téléphone</h3>' + grid(s5a, "ph") +
             '<h3>Sur la TV</h3>' + grid(s5b, "tv") +
             '<h3>Langues : gratuit</h3><p class="chips">%s</p>' % langs + grid(s5c, "tv") +
             '<h3>Télécharger des leçons, les envoyer à la TV</h3><p class="note">« Wi-Fi uniquement » évite d\'utiliser vos données mobiles.</p>' +
             grid(s5d, "ph") + '</section>')
    # 6 -------------------------------------------------------------------
    s6 = [
        step("6.1", "tv-tuile-controle-parental", ["parental"], "Sur la TV, ouvrez la tuile « Contrôle parental ».", "Tuile Contrôle parental"),
        step("6.2", "tv-parental-accueil", ["creer"], "Créez un code parental de 4 à 6 chiffres.", "Créer le code parental"),
        step("6.3", "tv-parental-clavier-code", ["valider"], "Tapez le code deux fois, puis « Valider ».", "Clavier du code parental"),
        step("6.4", "tv-parental-reglages", ["etat", "profils"], "Ouvrez « Profils des enfants ».", "Réglages des parents"),
        step("6.5", "tv-parental-profils", ["ajouter"], "Touchez « + Ajouter un profil » pour chaque enfant.", "Profils des enfants"),
    ]
    s6b = [step("6.6", "telephone-onglets-suite", ["parental"], "Sur le téléphone, l'onglet « Parental » suit la TV.", "Onglet Parental du téléphone")]
    B.append('<section id="parental"><h2><span>6</span>Contrôle parental</h2>' + grid(s6, "tv") + grid(s6b, "ph") +
             '<p class="note c">L\'écran « Parental » du téléphone est protégé : il ne se capture pas.</p></section>')
    # 7 -------------------------------------------------------------------
    cases = [
        case_real(1, "La TV n'est pas trouvée", "telephone-onglet-castbridge-tv", ["chercher", "manuel"],
                  "Allumez la TV, ouvrez CastBridge-TV, puis « Chercher à nouveau ».", "Boutons de recherche de la TV"),
        case_real(2, "« Le code de la TV a changé »", "telephone-adresse-manuelle", ["pin"],
                  "Saisissez à nouveau le code affiché sur la TV.", "Champ du code de la TV"),
        case(9, "La TV ne reconnaît plus le téléphone", svg_case("code"),
             "Tapez le code de la TV, puis « Valider et envoyer »."),
        case(3, "La copie n'avance pas", svg_case("tvoff"), "TV éteinte : les fichiers attendent, puis repartent."),
        case(10, "La copie est lente", svg_case("slow"), "« Wi-Fi de la TV lent » : rapprochez la TV de la box."),
        case(11, "La copie s'est arrêtée", svg_case("stop"), "Lisez la cause affichée, puis relancez la copie."),
        case(12, "La TV ne répond plus", svg_case("reboot"), "Redémarrez-la : CastBridge-TV repart seule."),
        case(13, "La TV s'endort en arrière-plan", svg_case("bg"), "Dans MENU : « Autoriser CastBridge-TV à rester actif en arrière-plan »."),
        case(14, "Fichier partagé depuis Telegram", svg_case("telegram"), "Si CastBridge ne peut plus le lire, repartagez-le."),
        case_real(4, "La TV est pleine", "tv-cle-usb", ["ranger"], "Libérez de la place ou rangez sur une clé USB.", "Menu Clé USB de la TV"),
        case(5, "Pas de son", svg_case("mute"), "Montez le volume de la TV, puis du téléphone."),
        case(6, "Le téléphone s'endort", svg_case("sleep"), "Gardez l'écran allumé pendant « Lire en direct »."),
        case(7, "Mauvais Wi-Fi", svg_case("wifi2"), "Mettez le téléphone sur le même Wi-Fi que la TV."),
        case(8, "Demander de l'aide", svg_case("help"), "Écrivez au support : +237 686 03 10 70."),
    ]
    B.append('<section id="probleme"><h2><span>7</span>Si ça ne marche pas</h2><div class="steps cases">%s</div>'
             '<p class="note c">« Déplacer vers la TV » libère la place du téléphone, pas celle de la TV.</p></section>' % "".join(cases))
    # 8 -------------------------------------------------------------------
    lines = [
        ("La TV n'a pas besoin d'Internet.", '<svg viewBox="0 0 40 40"><path d="M6 20 h28 M20 6 v28" class="ln er"/><circle cx="20" cy="20" r="14" class="st"/></svg>'),
        ("Les statistiques d'usage : seulement si vous acceptez.", '<svg viewBox="0 0 40 40"><path d="M8 22 l8 8 l16 -18" class="ln ok"/></svg>'),
        ("Vous changez d'avis à tout moment dans « Réglages ».", '<svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="8" class="st"/><path d="M20 4 v6 M20 30 v6 M4 20 h6 M30 20 h6" class="ln"/></svg>'),
    ]
    s8 = [
        step("8.1", "telephone-donnees-hors-ligne-suite", ["surlatv"], "Sur la TV : « La TV n'a jamais besoin d'Internet ».", "Texte Sur la TV"),
        step("8.2", "telephone-donnees-et-consentement", ["essentiel", "stats"], "Vous choisissez au premier lancement.", "Choix des statistiques"),
        step("8.3", "telephone-reglages", ["stats", "mesdonnees", "effacer"], "Dans « Réglages » : « Mes données » et « Effacer mes données ».", "Réglages du téléphone"),
    ]
    B.append('<section id="donnees"><h2><span>8</span>Vos données</h2><ul class="three">%s</ul>' %
             "".join('<li>%s<span>%s</span></li>' % (ic, esc(t)) for t, ic in lines) + grid(s8, "ph") + '</section>')
    # 9 -------------------------------------------------------------------
    B.append('<section id="aide"><h2><span>9</span>Aide</h2>'
             '<div class="help" role="note"><p class="hp">Écrivez-nous sur WhatsApp ou appelez :</p>'
             '<p class="num" id="tel">+237 686 03 10 70</p>'
             '<button type="button" class="cp" onclick="var t=document.getElementById(\'tel\').textContent,b=this;try{navigator.clipboard.writeText(t).then(function(){b.textContent=\'Copié\'})}catch(e){var r=document.createRange();r.selectNode(document.getElementById(\'tel\'));getSelection().removeAllRanges();getSelection().addRange(r)}">Copier</button>'
             '</div></section>')
    return "".join(B)


CSS = r"""
:root{--bg:#fbfaf6;--fg:#1b2133;--mut:#566074;--card:#fff;--line:#d9dce4;--ac:#d98a00;--ac2:#f5b025;--ok:#1a8f5a;--er:#c63d3d;--hl:#e8590c;--chip:#eef1f7}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0c111c;--fg:#eef1f8;--mut:#a5afc4;--card:#151c2c;--line:#2a3550;--ac:#f5b025;--ac2:#f5b025;--ok:#43d391;--er:#ff7b7b;--hl:#ffb347;--chip:#1e2842}}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:64px}
body{margin:0;background:var(--bg);color:var(--fg);font:18px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
.wrap{max-width:1000px;margin:0 auto;padding:0 16px 48px}
header.top{padding:20px 0 8px}h1{font-size:1.7rem;margin:0 0 4px}.sub{color:var(--mut);margin:0}
nav.toc{position:sticky;top:0;z-index:20;background:var(--bg);border-bottom:1px solid var(--line);margin:0 -16px;padding:8px 16px;display:flex;gap:8px;overflow-x:auto;white-space:nowrap;-webkit-overflow-scrolling:touch}
nav.toc a{flex:none;min-height:44px;display:inline-flex;align-items:center;gap:6px;padding:0 14px;border-radius:22px;background:var(--chip);color:var(--fg);text-decoration:none;font-weight:600;font-size:.95rem}
nav.toc a b{background:var(--ac2);color:#1b2133;border-radius:50%;width:22px;height:22px;display:inline-grid;place-items:center;font-size:.8rem}
section{padding-top:28px}h2{font-size:1.5rem;margin:0 0 14px;display:flex;align-items:center;gap:10px}
h2 span{background:var(--ac2);color:#1b2133;border-radius:50%;min-width:36px;height:36px;display:inline-grid;place-items:center;font-size:1.1rem}
h3{font-size:1.15rem;margin:26px 0 10px;color:var(--ac)}h4{margin:8px 0 4px;font-size:1.05rem}
.note{color:var(--mut);font-size:.9rem;margin:6px 0 0}.note.c{text-align:center}
.steps{display:grid;gap:16px;align-items:start;margin:12px 0}
.steps.ph{grid-template-columns:repeat(auto-fit,minmax(min(100%,250px),300px));justify-content:center}
.steps.tv{grid-template-columns:repeat(auto-fit,minmax(min(100%,420px),1fr))}
.steps.land{grid-template-columns:repeat(auto-fit,minmax(min(100%,360px),1fr))}
.steps.cases{grid-template-columns:repeat(auto-fit,minmax(min(100%,230px),1fr))}
.step{position:relative;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:12px;display:flex;flex-direction:column;gap:8px}
.step .no{position:absolute;top:-10px;left:-6px;background:var(--fg);color:var(--bg);font-size:.78rem;font-weight:700;border-radius:12px;padding:2px 9px;z-index:3}
.fig{position:relative;margin:0;width:100%;border-radius:10px;overflow:hidden;background:#000;container-type:inline-size}
.fig.ph{max-width:300px;margin:0 auto}
.im{position:absolute;inset:0;background-size:100% 100%;background-repeat:no-repeat}
.hl{position:absolute;border:3px solid var(--hl);border-radius:6px;box-shadow:0 0 0 2px rgba(0,0,0,.55);pointer-events:none}
.bd{position:absolute;transform:translate(-55%,-55%);width:24px;height:24px;border-radius:50%;background:var(--hl);color:#111;font:700 14px/24px system-ui,sans-serif;text-align:center;box-shadow:0 0 0 2px #fff,0 1px 6px rgba(0,0,0,.6)}
.leg{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:4px}
.leg li{display:flex;gap:8px;align-items:flex-start;font-size:.92rem}
.leg b{flex:none;width:24px;height:24px;border-radius:50%;background:var(--hl);color:#111;display:inline-grid;place-items:center;font-size:.85rem}
.s{margin:0;font-weight:700;font-size:1.05rem}
.drawn .dr,.case .dr{margin:0;text-align:center}.dr svg{width:100%;max-height:150px}
.dr figcaption{font-size:.72rem;color:var(--mut);margin-top:2px}
.st{stroke:var(--fg);stroke-width:3;fill:none}.fl{fill:var(--mut)}.fa{fill:var(--ac2)}.ac{color:var(--ac)}
.ln{stroke:var(--ac);stroke-width:4;fill:none;stroke-linecap:round;stroke-linejoin:round}.ln.ok{stroke:var(--ok)}.ln.er,.er .ln{stroke:var(--er)}
.tx{fill:var(--fg);font-family:system-ui,sans-serif}.tx.b{font-weight:700}
svg .st[fill="#f5b025"]{fill:#f5b025}
.need{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,200px),1fr));gap:14px}
.nd{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:14px;text-align:center;display:flex;flex-direction:column;gap:6px;align-items:center}
.nd svg{width:140px;height:auto}
.quad{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,200px),1fr));gap:12px;margin:10px 0}
.q{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:10px;position:relative;text-align:center}
.q p{margin:4px 0 0;font-weight:700;font-size:.98rem}.q svg{width:100%;max-height:120px}
.qn{position:absolute;top:-8px;left:-6px;background:var(--hl);color:#111;border-radius:50%;width:26px;height:26px;display:grid;place-items:center;font-weight:700}
.decide{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,220px),1fr));gap:12px}
.dc{background:var(--card);border:2px solid var(--ac2);border-radius:16px;padding:12px;text-align:center}.dc svg{width:100%;max-height:90px}.dc p{margin:4px 0 0;font-size:.95rem}
.qbar{background:var(--card);border:2px solid var(--ac2);border-radius:12px;padding:12px 14px;display:flex;justify-content:space-between;gap:10px;align-items:center;font-size:.92rem}
.qbar b{color:var(--ac);min-height:44px;display:inline-grid;place-items:center;padding:0 8px}
.chip{display:inline-block;background:var(--chip);border-radius:16px;padding:4px 12px;margin:3px 3px 0 0;font-weight:600;font-size:.92rem}
.chips{margin:0 0 6px}
.box{background:var(--chip);border:1px dashed var(--ac);border-radius:16px;padding:12px 14px;margin-top:22px}.box h4{margin-top:0}
.case h4{margin:4px 0 0}
.three{list-style:none;margin:0;padding:0;display:grid;gap:10px}.three li{display:flex;gap:12px;align-items:center;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:10px 14px;font-weight:700}
.three svg{width:38px;height:38px;flex:none}
.help{border:3px dashed var(--er);border-radius:18px;padding:24px;text-align:center;font-size:1.2rem;background:var(--card)}.help p{margin:0}.help .num{font-size:1.8rem;font-weight:700;margin:8px 0;user-select:all;-webkit-user-select:all}.help .cp{min-height:44px;padding:0 20px;border-radius:22px;border:2px solid var(--ac2);background:var(--chip);color:var(--fg);font:600 1rem system-ui,sans-serif}
footer{margin-top:36px;color:var(--mut);font-size:.82rem;text-align:center}
@media (min-width:700px){.step.wide{grid-column:auto}}
"""


def jpeg_uri(path, kind):
    im = Image.open(path).convert("RGB")
    maxw = 960 if kind == "tvshot" else 720
    if im.width > maxw:
        im = im.resize((maxw, round(im.height * maxw / im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=70, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def main():
    body = build_body()
    css_imgs = []
    for name in USED:
        s = BY[name]
        css_imgs.append(".im-%s{background-image:url(%s)}" % (s["id"], jpeg_uri(os.path.join(SCR, s["file"]), s["kind"])))
    toc = [("materiel", "1", "Matériel"), ("installer", "2", "Installer"), ("relier", "3", "Relier"), ("envoyer", "4", "Envoyer"),
           ("apprendre", "5", "Apprendre"), ("parental", "6", "Parental"), ("probleme", "7", "Problèmes"), ("donnees", "8", "Données"), ("aide", "9", "Aide")]
    nav = "".join('<a href="#%s"><b>%s</b>%s</a>' % (i, n, t) for i, n, t in toc)
    html = ('<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>Guide CastBridge</title><meta name="color-scheme" content="light dark"><style>%s%s</style></head><body>%s'
            '<div class="wrap"><header class="top"><h1>Guide CastBridge</h1><p class="sub">Envoyer vos vidéos du téléphone vers la TV. CastBridge sur le téléphone, CastBridge-TV sur la TV.</p></header>'
            '<nav class="toc" aria-label="Sommaire">%s</nav>%s'
            '<footer>Les captures viennent d\'un émulateur avec des vidéos de test et des codes masqués. Les dessins sont signalés.<br>Version du guide : 2026-10-07 · CastBridge 1.2.50 · CastBridge-TV 0.14.43</footer></div></body></html>'
            % (CSS, "".join(css_imgs), DEFS, nav, body))
    open(OUTFILE, "w", encoding="utf-8").write(html)
    # numéros d'étapes dans annotations.json
    for s in DATA["screens"]:
        s["steps"] = STEPS.get(s["name"], [])
    json.dump(DATA, open(os.path.join(SCR, "annotations.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("écrit", OUTFILE, "%.2f Mo" % (len(html.encode()) / 1e6), "| captures embarquées :", len(USED), "sur", len(DATA["screens"]))
    unused = [s["name"] for s in DATA["screens"] if s["name"] not in USED]
    print("non utilisées :", unused)
    print("phrases :", len(sentences), "max mots :", max(words(x) for x in sentences))


if __name__ == "__main__":
    main()
