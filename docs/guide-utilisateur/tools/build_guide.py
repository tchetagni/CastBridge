#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assemble docs/guide-utilisateur/guide-utilisateur.html (un seul fichier, hors ligne) à partir de
screens/*.png et screens/annotations.json. Aucune requête externe : CSS/JS en ligne, polices système,
captures en data: URI (JPEG réduit), repères dessinés en HTML/CSS aux bornes réelles des éléments.

Usage : python3 build_guide.py
"""
import base64, io, json, os, re
from html import unescape as html_unescape
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
VIOLATIONS = []    # phrases de plus de 12 mots : le build échoue à la fin s'il y en a
MISSING = []       # illustrations qui manquent : une légende est laissée dans le guide (affichée en fin de build)


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def words(t):
    """Nombre de mots d'une phrase : les signes seuls (« » · : ? › …) ne comptent pas."""
    return len([w for w in re.sub(r"<[^>]+>", "", t).split() if re.search(r"\w", w)])


def typo(html):
    """Typographie française dans le texte (jamais dans les balises) : espace insécable avant « : ; ? ! » et dans les guillemets,
    pour qu'une ligne ne finisse pas par « ni ne commence par : »."""
    parts = re.split(r"(<[^>]+>)", html)
    for i in range(0, len(parts), 2):          # les éléments pairs sont du texte, les impairs des balises
        t = re.sub(r" ([:;?!»])", "\u00a0" + r"\1", parts[i])
        parts[i] = re.sub(r"(«) ", r"\1" + "\u00a0", t)
    return "".join(parts)


def check(sentence, where=""):
    """Contrôle des 12 mots : mémorise la phrase ; le build échoue à la fin si l'une dépasse."""
    n = words(sentence)
    if n > 12:
        VIOLATIONS.append("%s (%d mots) : %s" % (where, n, re.sub(r"<[^>]+>", "", sentence)))
    sentences.append(sentence)
    return sentence


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
    check(sentence, stepno)
    leg = "".join('<li><b>%d</b><span>%s</span></li>' % (n, esc(l)) for n, l in legend)
    nt = '<p class="note">%s</p>' % note if note else ""
    return ('<article class="step %s" id="e%s"><span class="no">%s</span>%s<ol class="leg">%s</ol>'
            '<p class="s">%s</p>%s</article>' % (cls, stepno.replace(".", "-"), stepno, f, leg, sentence, nt))


def drawn(stepno, svg, sentence, labels=None, note=None, cls=""):
    check(sentence, stepno)
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


FILE_TV = "castbridge-tv-….apk"    # le fichier téléchargé s'appelle castbridge-tv-<version>-…apk (docs/RELEASES.md § 8)


def svg_tv_install():
    p1 = svg(150, 100, '<rect x="30" y="38" width="64" height="26" rx="4" class="st"/><rect x="94" y="45" width="20" height="12" class="fl"/>'
             + txt(62, 55, "USB", 11) + txt(75, 88, FILE_TV, 10), "Clé USB avec le fichier castbridge-tv téléchargé")
    p2 = svg(150, 100, tv(18, 10, 100, 58) + '<rect x="118" y="72" width="26" height="12" class="fl"/>'
             + arrow(142, 92, 128, 86) + txt(75, 96, "Branchez la clé", 11), "Clé USB branchée sur la TV")
    p3 = svg(150, 100, '<rect x="14" y="10" width="122" height="78" rx="6" class="st"/><rect x="14" y="10" width="122" height="14" rx="6" class="fl"/>'
             + '<rect x="24" y="34" width="14" height="14" rx="2" class="fa"/>' + txt(44, 45, "Download", 10, "start")
             + '<rect x="24" y="56" width="14" height="14" rx="2" class="fa"/>' + txt(44, 67, FILE_TV, 9, "start"),
             "Explorateur de fichiers de la TV avec le fichier castbridge-tv")
    p4 = svg(150, 100, '<rect x="20" y="14" width="110" height="72" rx="8" class="st"/>' + txt(75, 40, FILE_TV, 10)
             + '<rect x="40" y="52" width="70" height="24" rx="12" class="fa"/>' + '<text x="75" y="68" font-size="12" text-anchor="middle" fill="#1b2540" font-weight="700">Installer</text>',
             "Bouton Installer d'Android")
    caps = ["Téléchargez le fichier, puis copiez-le dans Download sur une clé USB.", "Branchez la clé sur la TV.",
            "Ouvrez l'explorateur de fichiers, dossier Download.", "Touchez le fichier castbridge-tv, puis Installer."]
    return ('<div class="quad">' + "".join('<div class="q"><span class="qn">%d</span>%s<p>%s</p></div>' % (i + 1, s, esc(c))
                                          for i, (s, c) in enumerate(zip([p1, p2, p3, p4], caps))) + '</div>')


def trio(items, cls="quad tri"):
    return ('<div class="%s">' % cls + "".join('<div class="q"><span class="qn">%d</span>%s<p>%s</p></div>' % (i + 1, sv, esc(c))
                                              for i, (sv, c) in enumerate(items)) + '</div>')


def svg_activation_code():
    """Activer en 3 gestes (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md) : mêmes dessins qu'avant, textes mis à jour."""
    p1 = svg(150, 100, tv(14, 8, 122, 66) + txt(75, 30, "Code de la TV", 10) + txt(75, 57, "482 913", 20, "middle", "tx b"),
             "La TV affiche son code à 6 chiffres")
    p2 = svg(150, 100, phone(14, 8, 40, 80) + wifi(75, 40, 16) + tv(88, 18, 56, 36) +
             '<rect x="90" y="62" width="52" height="16" rx="8" class="fa"/>' + txt(116, 74, "TV trouvée", 8), "Le téléphone trouve la TV avec le code")
    p3 = svg(150, 100, phone(14, 8, 40, 80) + tv(76, 14, 64, 40) + arrow(56, 40, 74, 36) +
             '<rect x="26" y="30" width="16" height="12" rx="3" class="fa"/>' + '<path d="M92 34 l6 6 l12 -14" class="ln ok"/>' + txt(108, 80, "Activée", 11),
             "La clé reçue active la TV")
    return trio([(p1, "La TV affiche un code à 6 chiffres."),
                 (p2, "Tapez ce code dans « Activer la TV » : le téléphone trouve la TV."),
                 (p3, "Collez la clé reçue : la TV s'active.")])


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
    check(sentence, "cas %d" % no)
    return ('<article class="step drawn case" id="c%d"><span class="no">%d</span><figure class="dr">%s<figcaption>Dessin, pas une capture</figcaption></figure>'
            '<h4>%s</h4><p class="s">%s</p>%s</article>' % (no, no, svgx, title, sentence, extra))


def case_real(no, title, name, keys, sentence, alt):
    f, legend = fig(name, keys, sn("probleme", no), alt)
    check(sentence, "cas %d" % no)
    leg = "".join('<li><b>%d</b><span>%s</span></li>' % (n, esc(l)) for n, l in legend)
    return ('<article class="step case" id="c%d"><span class="no">%d</span>%s<h4>%s</h4><ol class="leg">%s</ol><p class="s">%s</p></article>'
            % (no, no, f, title, leg, sentence))


def grid(items, cls):
    return '<div class="steps %s">%s</div>' % (cls, "".join(items))


# ------------------------------------------------ sections, repères de version et blocs de texte
URL_PAGE = "https://bridge.sti-cm.com/telecharger"            # docs/RELEASES.md § 8 (serveur 1.2.5)
URL_TV = "https://bridge.sti-cm.com/dl/tv/latest.apk"
URL_PHONE = "https://bridge.sti-cm.com/dl/phone/latest.apk"
VERS = "TV 0.14.45 ou plus récente · téléphone 1.2.53 ou plus récent"     # nouveautés du 2026-10-07
VERS_TV = "TV 0.14.45 ou plus récente"

# l'ordre fait la numérotation (« 7 Internet »…) ; les identifiants (ancres) ne changent jamais
SECTIONS = [("materiel", "Matériel"), ("installer", "Installer"), ("relier", "Relier"), ("envoyer", "Envoyer"),
            ("apprendre", "Apprendre"), ("jeux", "Jeux"), ("internet", "Internet"), ("usb", "Clé USB"),
            ("parental", "Parental"), ("probleme", "Problèmes"), ("donnees", "Données"), ("aide", "Aide")]
SEC = {sid: i for i, (sid, _) in enumerate(SECTIONS, 1)}


def sn(sec, k):
    """Numéro d'étape « <numéro de section>.<k> »."""
    return "%d.%d" % (SEC[sec], k)


def seq(sec):
    """Numéroteur d'étapes d'une section, dans l'ordre d'appel (= l'ordre d'apparition)."""
    c = [0]

    def nxt():
        c[0] += 1
        return sn(sec, c[0])
    return nxt


def h2(sid, title):
    return '<section id="%s"><h2><span>%d</span>%s</h2>' % (sid, SEC[sid], title)


def ver(t=VERS, cls=""):
    """Repère de version : la fonction demande ces versions."""
    return '<p class="ver %s">%s</p>' % (cls, esc(t))


def h3(t, anchor=None, v=None):
    return '<h3%s>%s</h3>%s' % (' id="%s"' % anchor if anchor else "", t, ver(v) if v else "")


def gh(t):
    return '<h4 class="gh">%s</h4>' % t


def xref(sid, label=None):
    return '<a class="lk" href="#%s">section %d%s</a>' % (sid, SEC[sid], " « %s »" % label if label else "")


def sent(t):
    return check(t, "texte")


def p(t, cls="s2"):
    return '<p class="%s">%s</p>' % (cls, sent(t))


def nt(*ss, cls=""):
    return '<p class="note %s">%s</p>' % (cls, " ".join(sent(s) for s in ss))


def lead(*ss):
    return '<p class="lead">%s</p>' % " ".join(sent(s) for s in ss)


def ns(items):
    """Étapes numérotées, texte seul (une phrase de 12 mots au plus par étape)."""
    return '<ol class="ns">%s</ol>' % "".join('<li><b>%d</b><span>%s</span></li>' % (i, sent(t)) for i, t in enumerate(items, 1))


def bl(items):
    return '<ul class="bl">%s</ul>' % "".join('<li><span>%s</span></li>' % sent(t) for t in items)


def say(who, text):
    """Ce que dit l'écran, mot pour mot (texte des documents)."""
    return '<blockquote class="say"><b>%s</b>%s</blockquote>' % (who, text)


def qa(who, msg, *todo):
    return '<div class="qa">%s<p><b>Que faire :</b> %s</p></div>' % (say(who, msg), " ".join(sent(t) for t in todo))


def qas(items):
    return '<div class="qas">%s</div>' % "".join(items)


def card(title, body, cls=""):
    return '<div class="card %s"><h4>%s</h4>%s</div>' % (cls, title, body)


def cards(items, cls=""):
    return '<div class="cards %s">%s</div>' % (cls, "".join(items))


def split(left, right):
    return '<div class="split">%s%s</div>' % (left, right)


def miss(caption):
    """Illustration qui manque : on n'invente pas d'image, on laisse une légende."""
    MISSING.append(re.sub(r"<[^>]+>", "", caption))
    return '<figure class="miss"><b>Illustration à venir</b><figcaption>%s</figcaption></figure>' % caption


def missrow(*captions):
    return '<div class="missrow">%s</div>' % "".join(miss(c) for c in captions)


# ---------------------------------------------------------------------- contenu
def build_body():
    B = []
    # 1 -------------------------------------------------------------------
    B.append(h2("materiel", "Ce qu'il vous faut") +
             '<div class="need">'
             '<div class="nd">%s<b>Une TV Android</b></div>'
             '<div class="nd">%s<b>Un téléphone Android</b></div>'
             '<div class="nd">%s<b>Le même Wi-Fi</b></div></div>' % (ICON_TV, ICON_PHONE, ICON_WIFI) +
             nt("Une box ou un partage de connexion suffit.", "La TV n'a pas besoin d'Internet, sauf pour jouer en ligne.", cls="c") +
             ver(VERS, "c") + nt("Ce repère marque une nouveauté : elle demande ces versions.", cls="c") + '</section>')

    # 2 -------------------------------------------------------------------
    n2 = seq("installer")

    def dl_card(icon, name, who, url):
        return ('<div class="card dlc">%s<h4>%s</h4><p class="s2">%s</p><p class="url"><a class="lk" href="%s">%s</a></p></div>'
                % (icon, name, who, url, url))

    telecharger = (
        h3("Télécharger les applications", "telecharger") +
        card("La page de téléchargement",
             '<p class="url"><a class="lk" href="%s">%s</a></p>' % (URL_PAGE, URL_PAGE) +
             p("Pour chaque application : version, date, taille, bouton « Télécharger », code QR.") +
             ns(["Ouvrez cette page sur l'ordinateur ou sur le téléphone.",
                 "Touchez « Télécharger », ou scannez le code QR avec le téléphone."]), "dlp") +
        cards([dl_card(ICON_PHONE, "CastBridge", "Pour le téléphone", URL_PHONE),
               dl_card(ICON_TV, "CastBridge-TV", "Pour la TV", URL_TV)]) +
        nt("Ces deux liens mènent toujours à la dernière version.", cls="c"))
    s2 = [
        drawn(n2(), svg_installer_phone(), "Téléchargez et installez CastBridge sur le téléphone.",
              note="Android peut demander « Installer des apps inconnues » : acceptez pour le navigateur."),
        step(n2(), "telephone-donnees-et-consentement", ["essentiel", "stats"],
             "Choisissez ce que vous partagez : l'essentiel, ou aussi les statistiques.",
             "Écran CastBridge et vos données du téléphone"),
    ]

    # -- activer avec le seul code (docs/TV-ACTIVATION-CLE-USB.md, DESIGN-ACTIVATION-SIMPLE-2026-10-07.md)
    act = [
        h3("Activer en 3 gestes : le code de la TV suffit", "activer-code", VERS),
        lead("Le code de la TV suffit.", "Pas de Wi-Fi à régler, pas de fichier à copier.", "Votre téléphone garde son Internet."),
        svg_activation_code(),
        gh("Geste 1 : lisez le code sur la TV"),
        cards([
            card("Sur la TV",
                 ns(["Ouvrez CastBridge-TV : l'écran d'activation s'affiche.",
                     "Cochez « J'ai lu et j'accepte les conditions d'usage ».",
                     "La TV affiche son code à 6 chiffres, en très gros."]) +
                 nt("Exemple : 482 913.", "Sans la case cochée, la TV refuse toute clé.")),
            card("La TV n'est sur aucun Wi-Fi, ou elle est sur câble",
                 p("Elle crée son réseau tout de suite.") + p("Elle affiche le code et un QR code.") +
                 say("La TV dit", "« Réseau direct prêt · en attente du téléphone »")),
            card("La TV est sur le Wi-Fi de votre box",
                 p("Elle affiche le code, sans QR code.") + p("Mettez le téléphone sur le même Wi-Fi.") +
                 say("La TV dit", "« Téléphone sur le même Wi-Fi : tapez le code dans CastBridge › Activer la TV »")),
        ]),
        miss("Capture à ajouter : l'écran d'activation de CastBridge-TV, avec le code en très gros et le QR code."),
        gh("Geste 2 : tapez le code sur le téléphone"),
        split(
            step(n2(), "telephone-onglet-castbridge-tv", ["activer"], "Sur le téléphone, ouvrez « Activer la TV ».",
                 "Bouton Activer la TV, en haut du téléphone"),
            cards([
                card("Sur le téléphone",
                     ns(["Tapez les 6 chiffres dans « Code affiché sur la TV ».",
                         "Au sixième chiffre, la recherche part seule : aucun bouton.",
                         "Si Android demande « Se connecter ? », touchez « Connecter ».",
                         "Le téléphone lit seul la demande de la TV : rien à recopier.",
                         "Il dit « TV trouvée », avec le nom de la TV.",
                         "Comparez le code d'appareil avec celui de la TV."]) +
                     say("Le téléphone dit", "« TV trouvée : &lt;nom de la TV&gt; · code d'appareil ABCD-… »")),
                card("Le téléphone essaie trois chemins",
                     ns(["Réseau local : la TV et le téléphone sont sur le même Wi-Fi.",
                         "Réseau de la TV : le téléphone rejoint le réseau qu'elle crée.",
                         "Bluetooth : le recours qui marche sur tous les boîtiers."]) +
                     nt("Temps limite de chaque chemin : 10 s, 50 s, 20 s.", "Un appairage Bluetooth à valider peut allonger l'attente.")),
                card("Pendant ce temps",
                     bl(["Votre Internet mobile reste actif.",
                         "Wi-Fi du téléphone éteint : le Bluetooth commence tout de suite.",
                         "Android 13 et plus : acceptez « Appareils à proximité », une fois."])),
            ], "one")),
        missrow("Capture à ajouter : l'écran « Activer la TV » avec le champ « Code affiché sur la TV ».",
                "Capture à ajouter : la boîte d'Android « Se connecter ? » avec le bouton « Connecter »."),
        gh("Geste 3 : donnez la clé à la TV"),
        lead("La clé active cette TV, et elle seule.", "Elle vient de votre agent.", "Posez-la dans les 48 heures."),
        split(
            step(n2(), "telephone-activer-la-tv", ["conditions", "etapes"], "Lisez les conditions, puis collez la clé reçue.",
                 "Écran Activer la TV du téléphone",
                 note="Capture de la version précédente : le champ « Code affiché sur la TV » n'y est pas encore."),
            cards([
                card("Votre agent est là",
                     p("Il émet la clé avec sa console : « Émettre et installer ».") + p("La TV s'active tout de suite.")),
                card("Votre agent n'est pas là",
                     ns(["Touchez « Partager la demande », ou « Copier ».",
                         "Envoyez-la à votre agent : WhatsApp, SMS ou e-mail.",
                         "Copiez la clé qu'il vous renvoie.",
                         "Revenez dans CastBridge : touchez « Installer cette clé »."]) +
                     nt("Sinon, collez la clé dans « Coller la clé reçue ».", "Une clé dans un fichier ? Touchez « Choisir un fichier ».")),
                card("Votre agent a préparé une clé USB",
                     p("Branchez-la sur la TV.") + p('Suivez <a class="lk" href="#activer-usb">« Activer avec une clé USB »</a>.')),
            ], "one")),
        miss("Capture à ajouter : « TV trouvée », « Partager la demande », « Copier » et « Coller la clé reçue » sur le téléphone."),
        cards([
            card("Avant l'envoi, le téléphone dit quelle clé c'est",
                 say("Le téléphone dit", "« Clé de production illimitée, faite pour cette TV. »") +
                 say("Le téléphone dit", "« Clé d'essai de 30 jours… »") +
                 say("Le téléphone dit", "« clé d'une autre TV »") +
                 p("Une clé d'une autre TV n'est jamais envoyée.")),
            card("Quand la clé est acceptée",
                 ns(["La TV répond « Clé acceptée par la TV. »",
                     "Une notification « TV activée » apparaît sur le téléphone.",
                     "CastBridge propose « Ajouter ma TV » : touchez-le pour lier ce téléphone."]) +
                 nt("Le code de connexion de la TV peut changer une fois.", "Les téléphones de confiance ne sont pas touchés.",
                    "Les autres téléphones lisent le nouveau code dans « Connexion &amp; réglages ».")),
        ]),
        gh("Cas particuliers"),
        cards([
            card("Le téléphone n'est pas sur le Wi-Fi de la TV",
                 ns(["Sur la TV, descendez jusqu'à la ligne sous le code.",
                     "Appuyez sur OK : « Réseau direct en préparation… ».",
                     "Le QR code et « Réseau direct prêt » apparaissent.",
                     "Tapez le code sur le téléphone."]) +
                 say("La TV dit", "« Le téléphone n'est pas sur ce Wi-Fi ? OK : réseau direct »") +
                 say("La TV dit", "« Le Wi-Fi de la TV peut se couper le temps de l'activation. »") +
                 nt("Il revient ensuite, sur la plupart des boîtiers.", "Sinon : Paramètres › Réseau de la TV.")),
            card("Téléphone sous Android 9 ou plus ancien",
                 ns(["Le téléphone montre le nom « DIRECT-CB-… » et son mot de passe.",
                     "Connectez-le à ce Wi-Fi dans les réglages d'Android.",
                     "Touchez « C'est fait » dans CastBridge."]) +
                 nt("Ou touchez « Passer au Bluetooth ».")),
            card("Le QR code de la TV",
                 p("Scannez-le avec l'appareil photo du téléphone.") + p("Il propose de rejoindre le réseau « DIRECT-CB-… ».") +
                 p("Ensuite, tapez le code dans « Activer la TV ».")),
            card("TV ou téléphone plus anciens",
                 bl(["TV 0.14.43 et téléphone récent : réseau local ou Bluetooth.",
                     "Le téléphone dit alors de mettre la TV à jour.",
                     "TV récente et téléphone ancien : scannez le QR code.",
                     "Puis ouvrez « Activer la TV » comme avant."])),
        ]),
        nt("Dernier recours : collez la clé en bas de l'écran de la TV.", "Puis touchez « Valider la clé ».", cls="c"),
        '<p class="note c">Un message d\'erreur ? Voir la <a class="lk" href="#probleme-activation">section %d, « Activer la TV avec le code »</a>.</p>' % SEC["probleme"],
    ]
    usbact = [
        h3("Activer avec une clé USB préparée par l'agent", "activer-usb", VERS_TV),
        lead("L'agent prépare votre clé USB sur son ordinateur.", "Vous n'avez ni fichier à copier, ni réglage Android à faire."),
        cards([
            card("Sur la TV",
                 ns(["Branchez la clé sur la TV, dans n'importe quelle prise USB.",
                     "Ouvrez l'écran d'activation et cochez les conditions d'usage.",
                     "Un bandeau apparaît en haut de l'écran.",
                     "Appuyez sur OK sur le bandeau : la TV est activée."]) +
                 say("La TV dit", "« Clé USB : activation trouvée pour cette TV › Activer »")),
            card("À savoir",
                 bl(["La TV lit la clé dès le branchement.",
                     "Le bandeau arrive en quelques secondes.",
                     "L'agent prépare la clé juste avant de vous la remettre.",
                     "Utilisez-la dans les 48 heures.",
                     "Elle marche avec ou sans « Accès à tous les fichiers »."])),
        ]),
        miss("Capture à ajouter : le bandeau « Clé USB : activation trouvée pour cette TV › Activer » sur CastBridge-TV."),
        gh("Si la TV ne montre pas le bandeau"),
        bl(["Touchez « Chercher la clé sur la clé USB ».",
            "Sinon, touchez « Choisir le fichier d'activation (explorateur) »."]),
        svg_usb_picker(),
        nt("La clé peut être n'importe où dans le fichier.", cls="c"),
    ]
    locations = ('<aside class="box"><h4>Si votre agent vous a remis une location…</h4>'
                 '<p>Ouvrez « Activer la TV », puis « Locations ».</p>' +
                 grid([step(n2(), "telephone-locations", ["choisir", "loc", "envoyer"],
                            "Touchez « Choisir les fichiers », puis « 2. Envoyer à la TV ».",
                            "Écran Locations sur la TV du téléphone")], "ph") + '</aside>')
    B.append(h2("installer", "Télécharger, installer, activer") + telecharger +
             h3("Sur le téléphone", "installer-telephone") + grid(s2, "ph") +
             h3("Sur la TV : installer depuis une clé USB", "installer-tv") + svg_tv_install() +
             nt("Android peut demander « Installer des apps inconnues » pour l'explorateur de fichiers.",
                "Acceptez une fois, puis touchez Installer.", cls="c") +
             "".join(act) + "".join(usbact) + locations + '</section>')

    # 3 -------------------------------------------------------------------
    chips = "".join('<span class="chip">%s</span>' % t for t in ["TV DLNA", "CastBridge TV", "Jeux", "Sur le téléphone", "Apprendre", "Parental"])
    intro = [
        step(sn("relier", 1), "telephone-onglet-castbridge-tv", ["onglet", "ajouter"],
             "Ouvrez CastBridge : l'onglet « CastBridge TV » s'affiche en premier.", "Premier écran du téléphone"),
        step(sn("relier", 2), "telephone-onglets-suite", ["surtel", "apprendre", "parental"],
             "Faites glisser les onglets : il y en a six.", "Onglets du téléphone, suite", note=chips),
        step(sn("relier", 3), "tv-accueil-avec-videos", ["biblio", "ajouter", "apprendre", "langues", "jeux", "telech"],
             "Sur la TV, l'accueil montre une tuile par fonction.", "Accueil de CastBridge-TV", cls="wide"),
        step(sn("relier", 4), "tv-tuile-recevoir-du-telephone", ["recevoir", "usb", "bt", "internet", "wd"],
             "Les tuiles de droite : code, clé USB, Bluetooth, Internet.", "Tuiles suivantes de l'accueil de la TV", cls="wide"),
    ]
    wayA = [
        step(sn("relier", 5), "tv-accueil-avec-videos", ["ajouter"], "Sur la TV, ouvrez la tuile « Ajouter un téléphone ».", "Tuile Ajouter un téléphone", cls="wide"),
        step(sn("relier", 6), "tv-bluetooth-visible", ["allow"], "Si la TV demande d'être visible en Bluetooth, acceptez.", "Fenêtre Bluetooth de la TV", cls="wide"),
        step(sn("relier", 7), "tv-ajouter-un-telephone", ["consigne", "visible", "prolonger"], "La TV attend votre téléphone pendant quelques minutes.", "Écran Ajouter un téléphone", cls="wide"),
        step(sn("relier", 8), "telephone-onglet-castbridge-tv", ["ajouter"], "Sur le téléphone, touchez « Ajouter ma TV (Bluetooth, sans code) ».", "Bouton Ajouter ma TV"),
    ]
    wayB = [
        step(sn("relier", 9), "tv-tuile-recevoir-du-telephone", ["recevoir"], "Sur la TV, ouvrez la tuile « Recevoir du téléphone ».", "Tuile Recevoir du téléphone", cls="wide"),
        step(sn("relier", 10), "tv-aide-recevoir-du-telephone", ["titre", "compris"], "La TV montre son code (masqué ici) : touchez « Compris ».", "Aide de la TV", cls="wide"),
        step(sn("relier", 11), "telephone-onglet-castbridge-tv", ["manuel"], "Sur le téléphone, touchez « Ma TV n'apparaît pas… ».", "Lien Ma TV n'apparaît pas"),
        step(sn("relier", 12), "telephone-adresse-manuelle", ["case", "ip", "pin"], "Cochez la case, puis tapez l'adresse de la TV et son code.", "Saisie manuelle de la TV",
             note="L'adresse de la TV est dans « Connexion &amp; réglages » sur la TV."),
        step(sn("relier", 13), "telephone-code-saisi-masque", ["ip", "pin"], "Le code que vous tapez reste caché par des points.", "Champs adresse et code remplis"),
        step(sn("relier", 14), "telephone-tv-jointe", ["surlatv"], "Quand « SUR LA TV » apparaît, la TV est jointe.", "Téléphone relié à la TV"),
    ]
    B.append(h2("relier", "Relier le téléphone à la TV") + grid(intro[:2], "ph") + grid(intro[2:], "tv") +
             '<h3>Voie A : Bluetooth, sans code</h3>' + grid(wayA[:3], "tv") + grid(wayA[3:], "ph") +
             '<h3>Voie B : avec le code de la TV</h3>' + grid(wayB[:2], "tv") + grid(wayB[2:], "ph") +
             '<h3>Wi-Fi Direct</h3>' + grid([drawn(sn("relier", 15), svg_wd5(), "En Wi-Fi Direct, la TV demande la 5 GHz si possible.")], "ph") + '</section>')

    # 4 -------------------------------------------------------------------
    s4 = [
        step(sn("envoyer", 1), "telephone-sur-le-telephone", ["videos", "video"], "Dans « Sur le téléphone », touchez la vidéo à envoyer.", "Onglet Sur le téléphone"),
        step(sn("envoyer", 2), "telephone-ouvrir-avec-castbridge", ["copierlire", "copier", "deplacer", "lireici"],
             "Dans « Ouvrir avec CastBridge », choisissez comment envoyer.", "Ouvrir avec CastBridge",
             note="Les boutons sont grisés sur cette capture : aucune TV n'était reliée."),
        step(sn("envoyer", 3), "telephone-lecteur", ["lire"], "Dans le lecteur, touchez « Lire sur la TV ».", "Lecteur du téléphone", cls="wide2"),
        step(sn("envoyer", 4), "telephone-feuille-lire-sur-la-tv", ["titre", "tv"], "La feuille liste les TV trouvées et les façons d'envoyer.", "Feuille Lire sur la TV", cls="wide2"),
    ]
    s4b = [
        drawn(sn("envoyer", 5), svg_queue(), "La barre du haut montre l'envoi et les fichiers en attente."),
        step(sn("envoyer", 6), "tv-bibliotheque", ["toutes", "carte1", "carte2"], "Les vidéos reçues arrivent dans la « Bibliothèque » de la TV.", "Bibliothèque de la TV", cls="wide"),
        step(sn("envoyer", 7), "tv-lecture", [], "Touchez OK sur une vidéo : elle se lit.", "Une vidéo se lit sur la TV", cls="wide"),
    ]
    # -- Ouvrir sur la TV (docs/REMOTE.md, « Ouvrir CastBridge-TV depuis le téléphone »)
    ouvrir = (
        h3("Ouvrir CastBridge-TV depuis le téléphone", "ouvrir-tv", VERS) +
        lead("Un geste sur le téléphone fait apparaître CastBridge-TV à l'écran.", "Même si une autre application est devant.") +
        cards([
            card("1. Le bouton", p("Touchez « Ouvrir sur la TV », en haut de l'onglet « CastBridge TV ».")),
            card("2. La télécommande", p("Touchez la touche « TV », après Retour, Accueil, Menu, Info et Clavier.")),
            card("3. L'icône", p("Faites un appui long sur l'icône CastBridge, puis « Ouvrir CastBridge-TV ».")),
        ]) +
        miss("Capture à ajouter : le bouton « Ouvrir sur la TV » de l'onglet « CastBridge TV » et la touche « TV » de la télécommande.") +
        cards([
            card("Si CastBridge-TV est déjà devant",
                 p("Le bouton ouvre alors la télécommande.") + p("Rien n'est touché sur la TV : la vidéo ne s'arrête pas.")),
            card("Ce qui peut se passer sur la TV",
                 bl(["CastBridge-TV s'ouvre devant les autres applications.",
                     "Ou la TV affiche une notification : validez-la avec sa télécommande.",
                     "Ou la TV demande une autorisation « par-dessus » : acceptez-la."]) +
                 say("Dans MENU, une seule fois", "« Autoriser CastBridge-TV à s'afficher par-dessus les autres applications »")),
            card("Les limites",
                 bl(["Cela n'allume pas la TV : allumez-la d'abord.",
                     "Une TV éteinte ou en veille profonde ne répond pas.",
                     "Android ne permet pas à CastBridge de l'allumer.",
                     "La touche « TV » manque si « Ma TV » est d'une autre marque."])),
        ]) +
        gh("Ce que dit le téléphone") +
        nt("Il essaie plusieurs voies : Wi-Fi, puis Bluetooth.", "Il attend 10 secondes au plus, puis affiche une seule ligne.") +
        qas([
            qa("Le téléphone dit", "« CastBridge-TV est à l'écran »", "C'est fait."),
            qa("Le téléphone dit", "« La TV demande une autorisation : MENU › Afficher par-dessus »", "Sur la TV, ouvrez MENU, puis cette ligne."),
            qa("Le téléphone dit", "« La TV affiche une notification « CastBridge-TV » : validez-la avec la télécommande de la TV »",
               "Validez la notification avec la télécommande de la TV."),
            qa("Le téléphone dit", "« La TV ne répond pas : allumez-la (le Wi-Fi ou le Bluetooth de la TV est éteint) »", "Allumez la TV, puis réessayez."),
            qa("Le téléphone dit", "« Aucune TV n'est associée : touchez « Ajouter ma TV »… »", "Reliez d'abord le téléphone à la TV."),
            qa("Le téléphone dit", "« La TV ne reconnaît pas ce téléphone : réassociez-la… ou saisissez son code »",
               "Reliez de nouveau le téléphone, ou tapez le code de la TV."),
            qa("Le téléphone dit", "« Cette TV ne connaît pas encore ce raccourci : mettez CastBridge-TV à jour »", "Mettez CastBridge-TV à jour."),
        ]))
    B.append(h2("envoyer", "Envoyer une vidéo") + grid(s4[:2], "ph") + grid(s4[2:], "land") +
             '<h3>Quelle façon choisir ?</h3>' + svg_decision() + '<h3>Suivre l\'envoi</h3>' + grid(s4b, "tv") +
             grid([drawn(sn("envoyer", 8), svg_progress(), "La copie montre « envoyés » et « confirmés ».",
                         note="Sur un Wi-Fi lent, la ligne le dit : débit et temps restant."),
                   drawn(sn("envoyer", 9), svg_stop_cause(), "Une copie impossible s'arrête, avec sa cause.")], "ph") +
             '<h3>Quand la TV demande le code</h3>' +
             grid([drawn(sn("envoyer", 10), svg_code_box(), "Si la TV ne vous reconnaît plus, tapez son code.",
                         note="Touchez « Valider et envoyer » : le code est gardé ensuite.")], "ph") +
             '<h3>Télécommande</h3>' +
             grid([drawn(sn("envoyer", 11), svg_remote(), "Pavé et OK pour choisir, Retour pour revenir.", note="Avancer ou reculer de 10 secondes : ▶▶| avance, |◀◀ recule.")], "ph") +
             ouvrir + '</section>')

    # 5 -------------------------------------------------------------------
    n5 = seq("apprendre")
    langs = "".join('<span class="chip">%s</span>' % t for t in ["Chinois", "anglais", "allemand", "français", "italien", "espagnol", "japonais"])
    s5a = [
        step(n5(), "telephone-apprendre", ["lecons", "piloter", "parents", "lots"], "L'onglet « Apprendre » propose des leçons, de la maternelle à la licence.", "Onglet Apprendre"),
    ]
    s5b = [
        step(n5(), "tv-apprendre", ["eleve", "classe", "contenus"], "Sur la TV, la tuile « Apprendre » ouvre les élèves.", "Apprendre sur la TV"),
    ]
    s5c = [
        step(n5(), "tv-langues", ["chinois", "japonais", "maj"], "La tuile « Langues » liste les langues déjà reçues.", "Langues sur la TV"),
        step(n5(), "tv-langue-niveaux", ["niveau"], "Choisissez un niveau : les leçons se lisent sans Internet.", "Niveaux d'une langue"),
    ]
    s5d = [
        step(n5(), "telephone-donnees-hors-ligne", ["wifi", "maj", "envoyer"], "Dans « Données hors ligne » : mettez à jour, puis envoyez.", "Données hors ligne"),
        step(n5(), "telephone-donnees-hors-ligne", ["langue", "telecharger"], "Choisissez la langue, puis « Télécharger mes leçons de langue ».", "Section Langues de Données hors ligne",
             note="Pour un lot précis : « Envoyer à la TV » ou « Télécharger et envoyer »."),
        step(n5(), "telephone-donnees-hors-ligne-suite", ["actualiser", "voir", "surlatv"], "Plus bas : les leçons du serveur et l'état de la TV.", "Données hors ligne, suite"),
    ]
    B.append(h2("apprendre", "Apprendre et Langues") + '<h3>Sur le téléphone</h3>' + grid(s5a, "ph") +
             '<h3>Sur la TV</h3>' + grid(s5b, "tv1") +
             '<h3>Langues : gratuit</h3><p class="chips">%s</p>' % langs + grid(s5c, "tv") +
             '<h3>Télécharger des leçons, les envoyer à la TV</h3><p class="note">« Wi-Fi uniquement » évite d\'utiliser vos données mobiles.</p>' +
             grid(s5d, "ph") + '</section>')

    # 6 : Jeux (docs/GAMES.md, docs/CHESS.md § 6) ------------------------------
    n6 = seq("jeux")
    s6a = [step(n6(), "telephone-jeux", ["quiz", "echecs", "sudoku"], "L'onglet « Jeux » propose Quiz, Échecs, Sudoku et Bataille.", "Onglet Jeux",
                note="Capture d'avant la Bataille. Fap-Fap et Agraham Tia y figurent aussi, « Bientôt ».")]
    s6b = [step(n6(), "tv-jeux", ["quiz", "echecs", "sudoku"], "Sur la TV, la tuile « Jeux » ouvre la liste des jeux.", "Jeux sur la TV",
                note="Capture d'avant la Bataille. La liste montre aussi Fap-Fap et Agraham Tia, « Bientôt ». La tuile « Jeux » de l'accueil annonce « 4 jeux ».")]
    s6c = [step(n6(), "tv-quiz-des-millions", ["amis", "entrainement", "scores"], "Dans Quiz des Millions, choisissez un mode, puis jouez.", "Menu du Quiz des Millions")]
    # Quiz en ligne avec mise (docs/QUIZ.md § 5 bis, chantier games-G5) : jamais « jetons » pour une mise ; les points de la maison n'ont aucune valeur
    quizmise = (
        h3("Quiz en ligne : libre, ou avec une mise", "quiz-mise") +
        lead("Jouez au Quiz contre d'autres TV, sur Internet.", "Partie libre, ou avec une mise en NDEM ou MBOKO.",
             "Les téléphones de chaque TV jouent par leur TV.") +
        cards([
            card("Ce qu'il faut",
                 bl(["Une CastBridge-TV à jour, activée, avec Internet.",
                     'Pas d\'Internet ? Le téléphone peut en prêter : <a class="lk" href="#internet">section %d</a>.' % SEC["internet"],
                     "Une TV d'essai joue en partie libre seulement.",
                     "Avec une mise : au moins deux TV, un joueur chacune.",
                     "Les téléphones restent sur le Wi-Fi de leur TV."])),
            card("Créer une partie",
                 ns(["Sur la TV : « Quiz », puis « Partie Internet ».",
                     "Touchez « Créer une partie ».",
                     "Choisissez la « Mise » : Libre, NDEM ou MBOKO.",
                     "Avec une mise, choisissez aussi le « Montant ».",
                     "Choisissez combien de joueurs de cette TV misent.",
                     "Touchez « Créer la partie », puis « Bloquer ma mise et créer »."]) +
                 nt("Chaque joueur qui mise paie une mise, du compte de la TV.",
                    "Seules les monnaies que votre TV peut miser sont proposées.")),
            card("Rejoindre une partie",
                 ns(["Choisissez « Rejoindre avec un code ».",
                     "Entrez les 8 symboles du code, puis « Valider ».",
                     "La TV annonce la mise avant de bloquer la vôtre.",
                     "Choisissez combien de joueurs de cette TV misent.",
                     "Touchez « Bloquer ma mise et rejoindre »."]) +
                 nt("Une partie libre se rejoint sans rien bloquer.")),
        ]) +
        miss("Capture à ajouter : Quiz › Partie Internet › « Créer une partie », avec « Mise », « Montant » et le nombre de joueurs.") +
        cards([
            card("Les règles à connaître",
                 bl(["Chaque joueur qui mise paie la même mise.",
                     "La cagnotte est partagée selon le classement.",
                     "À deux joueurs : 70 % et 30 %.",
                     "À trois joueurs et plus : 60 %, 30 % et 10 %.",
                     "Personne n'a marqué : chacun reprend sa mise.",
                     "La partie commence quand deux TV au moins ont misé.",
                     "Après le départ, plus personne n'entre.",
                     "Quitter la partie commencée fait perdre la mise.",
                     "Plus de 60 secondes hors ligne : vos joueurs ne marquent plus.",
                     "Hôte parti avant le départ : les mises sont rendues.",
                     "Partie interrompue : chaque mise est rendue.",
                     "Une partie avec mise se joue une seule fois."])),
            card("Ce que dit la TV",
                 say("La TV demande (exemple)", "« Créer une partie avec mise ? 20 NDEM par joueur · 2 joueurs ici · bloqué : 40 NDEM · votre solde 150 NDEM »") +
                 say("La TV dit", "« Cette partie se joue avec une mise de 20 NDEM par joueur. Combien de joueurs de cette TV misent ? »") +
                 say("Pendant la partie", "« Mise : 20 NDEM par joueur · cagnotte 60 NDEM »") +
                 say("En fin de partie", "« Vous gagnez 2 NDEM » · « Vous perdez 2 NDEM » · « Partie interrompue : mise rendue »") +
                 say("Après une partie avec mise", "« Voir « Mes jetons » »") +
                 say("Hors ligne en fin de partie", "« Règlement en attente… »") +
                 say("Si la TV ne peut pas miser", "« Mises NDEM/MBOKO : mettez à jour »")),
            card("Sur le téléphone",
                 p("Le téléphone ne parle qu'à sa TV.") +
                 say("La page dit, avant le départ", "« Mise : 20 NDEM par joueur — la cagnotte est partagée selon le classement. »") +
                 say("La page dit, à la fin", "« Votre part de la cagnotte : 42 NDEM (mise : 20 NDEM) »") +
                 say("Si la partie est interrompue", "« Mise rendue : partie interrompue. »")),
        ]) +
        h3("Compétition à points : à la maison", "quiz-points") +
        lead("Entre les téléphones d'une même TV, on joue en points.", "Ces points n'ont aucune valeur.") +
        cards([
            card("Ce qu'il faut savoir",
                 bl(["Rien à payer, rien à gagner.",
                     "Choisissez 50, 100 ou 200 points par joueur.",
                     "Les points en jeu sont partagés selon le classement.",
                     "Ce ne sont ni des NDEM ni des MBOKO."]))]))
    bataille = (
        h3("Bataille (démonstration)", "bataille", VERS) +
        lead("Un jeu de cartes simple, à deux joueurs.", "C'est une démonstration des jeux à tour de rôle.") +
        cards([
            card("Les règles",
                 bl(["52 cartes : chacun en reçoit 26, faces cachées.",
                     "Chacun retourne la carte du dessus.",
                     "La plus forte carte gagne les deux cartes.",
                     "L'as est la plus forte ; les couleurs ne comptent pas.",
                     "Égalité : c'est la bataille.",
                     "Chacun pose une carte cachée, puis retourne une carte visible.",
                     "La plus forte carte visible prend toute la table.",
                     "Qui n'a plus de carte perd la partie.",
                     "Au bout de 200 retournements, le plus gros paquet gagne.",
                     "Paquets égaux : partie nulle."])),
            card("Seul contre l'ordinateur",
                 ns(["Sur la TV, ouvrez « Jeux », puis « Bataille (démonstration) ».",
                     "Réglez « Mode » sur le solo contre l'ordinateur.",
                     "Touchez « Commencer la partie ».",
                     "Appuyez sur OK pour retourner votre carte."]) +
                 nt("Troisième mode : la télécommande de la TV contre un téléphone.")),
            card("À deux téléphones, à la maison",
                 ns(["Réglez « Mode » sur le jeu avec les téléphones.",
                     "La TV affiche un QR code et un code à 4 chiffres.",
                     "Sur chaque téléphone, scannez le QR code.",
                     "Entrez votre pseudo et le code.",
                     "La TV liste les places : ● présent, ○ attendu.",
                     "Quand les deux sont là, touchez « Commencer la partie ».",
                     "À votre tour, touchez « Retourner ma carte »."]) +
                 nt("Ou, dans CastBridge, onglet « Jeux », carte « Bataille » : « Rejoindre avec ce téléphone ».",
                    "Sans QR code, ouvrez http://&lt;adresse de la TV&gt;:8765/jeux/bataille.",
                    "L'adresse de la TV est dans « Connexion &amp; réglages ».")),
        ]) +
        missrow("Capture à ajouter : la liste « Jeux » de la TV avec la Bataille, Fap-Fap et Agraham Tia.",
                "Capture à ajouter : le salon de la Bataille sur la TV (QR code, code à 4 chiffres, places).",
                "Capture à ajouter : la table de la Bataille sur un téléphone, avec « Retourner ma carte ».") +
        cards([
            card("Si un téléphone se coupe",
                 bl(["Rouvrez la page : même place, même partie.",
                     "Sans retour au bout de 60 secondes, vous perdez la partie.",
                     "Si tout le monde part, la partie est abandonnée, sans perdant."]))]))
    echecs = (
        h3("Échecs en ligne : une TV contre une autre TV", "echecs-ligne", VERS) +
        lead("Jouez aux échecs contre une autre TV, sur Internet.", "Partie libre, ou avec une mise de jetons NDEM ou MBOKO.",
             "Les parties en ligne marchent quand le service est ouvert.") +
        cards([
            card("Ce qu'il faut",
                 bl(["Une TV activée, avec Internet.",
                     'Pas d\'Internet ? Le téléphone peut en prêter : <a class="lk" href="#internet">section %d</a>.' % SEC["internet"],
                     "Une TV d'essai joue en partie libre seulement.",
                     "Le téléphone ne joue pas : il regarde par sa TV."])),
            card("Créer une partie",
                 ns(["Sur la TV : « Jeux », « Échecs », puis « Adversaire : En ligne (Internet) ».",
                     "Touchez « Créer une partie ».",
                     "Choisissez la « Mise » : Libre, NDEM ou MBOKO.",
                     "Avec une mise, choisissez aussi le « Montant ».",
                     "Choisissez la couleur et le compte à rebours.",
                     "Touchez « Créer la partie »."]) +
                 nt("Seules les monnaies que votre TV peut miser sont proposées.")),
            card("Rejoindre ou regarder",
                 ns(["Choisissez « Rejoindre avec un code ».",
                     "Entrez les 8 symboles du code avec les touches ‹ et ›.",
                     "Une partie avec mise : la TV l'annonce avant de bloquer vos jetons.",
                     "Sans mise : « Regarder une partie avec un code ».",
                     "Partie fermée par erreur ? « Reprendre la partie en cours »."]) +
                 nt("La place est gardée 6 minutes au plus.")),
        ]) +
        miss("Capture à ajouter : Échecs › Adversaire : En ligne (Internet), avec « Créer une partie », « Mise » et « Montant ».") +
        cards([
            card("Les règles à connaître",
                 bl(["Chaque coup : 10 à 60 secondes, jamais plus.",
                     "Compétition : temps dépassé, partie perdue.",
                     "Une partie avec mise se joue toujours en compétition.",
                     "Abandonner ou quitter la partie fait perdre la mise.",
                     "Plus de 60 secondes hors ligne : c'est un abandon.",
                     "Victoire : le gagnant reçoit les deux mises, moins les frais éventuels.",
                     "Nulle ou partie interrompue : chaque mise est rendue."])),
            card("Ce que dit la TV",
                 say("La TV demande (exemple)", "« Créer une partie avec mise ? 20 NDEM par joueur · votre solde 150 NDEM »") +
                 say("La TV dit", "« Cette partie se joue avec une mise — 20 NDEM par joueur · votre solde… »") +
                 say("En fin de partie", "« Vous avez gagné ! » · « Partie nulle » · « Partie interrompue »") +
                 say("Après une partie avec mise", "« Voir « Mes jetons » »") +
                 say("Hors ligne en fin de partie", "« Règlement en attente… »")),
            card("Quand la TV ne peut pas jouer",
                 say("La TV dit", "« Connexion Internet requise »") +
                 say("La TV dit", "« Les échecs en ligne ne sont pas encore ouverts sur le service CastBridge… »") +
                 p("La TV dit toujours pourquoi : jamais un écran vide.") +
                 p("Solde trop bas ? « Regarder seulement » reste possible.")),
        ]))
    bientot = (
        h3("Bientôt : Fap-Fap et Agraham Tia", "bientot") +
        lead("Deux jeux de cartes arrivent bientôt.", "Ils figurent dans la liste, grisés, avec « Bientôt ».", "On ne peut pas encore les lancer."))
    B.append(h2("jeux", "Jeux") +
             lead("Quiz, Échecs, Sudoku, Bataille : on joue sur la TV.", "Le téléphone sert de manette ou de joueur.") +
             h3("Choisir un jeu", "jeux-liste") + grid(s6a, "ph") + grid(s6b, "tv1") +
             h3("Quiz des Millions") + grid(s6c, "tv1") + quizmise +
             h3("Sudoku") + p("Touchez « Jouer sur la TV », puis pilotez avec la manette du téléphone.") +
             nt("Quatre niveaux : Facile, Moyen, Difficile, Expert.") +
             bataille + echecs + bientot + '</section>')

    # 7 : Internet par le téléphone (docs/REMOTE-TUNNEL-TV.md § 5, docs/QUIZ.md § 4 bis) -----------------
    internet = (
        h2("internet", "Internet par le téléphone") + ver() +
        lead("Votre TV n'a pas Internet ? Votre téléphone peut lui prêter le sien.", "La TV le demande elle-même.",
             "Le téléphone répond sans poser de question.") +
        cards([
            card("À quoi ça sert",
                 bl(["Jouer en ligne : « Partie Internet » du Quiz, échecs en ligne.",
                     "Utiliser vos jetons.",
                     "Mettre la TV à jour tout de suite.",
                     "Recevoir l'assistance que vous demandez."])),
            card("Ce qu'il faut",
                 bl(["Le téléphone est déjà relié à la TV (code donné une fois).",
                     "C'est votre accord : aucun écran de plus.",
                     "Le Bluetooth du téléphone est allumé.",
                     "CastBridge est à jour : 1.2.53 ou plus récent."])),
        ]) +
        h3("Comment ça se passe", "internet-comment") +
        cards([
            card("Étape par étape",
                 ns(["La TV a besoin d'Internet et n'en a pas.",
                     "Elle dit : « Demande d'Internet au téléphone… ».",
                     "Le téléphone ouvre le tuyau, sans rien demander.",
                     "Une seule notification : « CastBridge relaie pour &lt;nom de la TV&gt; ».",
                     "L'opération se fait : partie en ligne, jetons ou mise à jour.",
                     "Le tuyau se ferme 10 minutes après la dernière connexion."])),
            card("Ce que vous voyez",
                 say("La TV dit", "« Demande d'Internet au téléphone… »") +
                 say("Notification du téléphone", "« CastBridge relaie pour &lt;nom de la TV&gt; »") +
                 p("La notification est discrète : elle a un bouton « Arrêter ».")),
        ]) +
        missrow("Capture à ajouter : « Demande d'Internet au téléphone… » sur CastBridge-TV.",
                "Capture à ajouter : la notification « CastBridge relaie pour &lt;TV&gt; » sur le téléphone.") +
        h3("Vos données mobiles : vous gardez la main", "internet-donnees") +
        cards([
            card("Les règles du téléphone",
                 bl(["Sur Wi-Fi : le tuyau est libre.",
                     "Sur données mobiles : seulement le serveur CastBridge, 5 Mo par jour.",
                     "Pas de gros téléchargement sur données mobiles.",
                     "Exemple : une mise à jour de 41 Mo.",
                     "Sauf si vous activez « Données mobiles pour la TV »."])),
            card("Vos réglages",
                 bl(["« Données mobiles pour la TV » : autorise les gros téléchargements.",
                     "« Ne plus relayer pour cette TV » : le téléphone refuse pour cette TV.",
                     "« Ne plus relayer » est éteint au départ, pour chaque TV.",
                     "« Arrêter » dans la notification : cette TV attend 10 minutes."])),
        ]) +
        h3("Si le téléphone refuse", "internet-refus") +
        qas([
            qa("La TV dit", "« Le téléphone a atteint son plafond de données mobiles pour aujourd'hui »",
               "Passez le téléphone en Wi-Fi, ou réessayez demain."),
            qa("La TV dit", "« Ouvrez CastBridge sur le téléphone… »", "Ouvrez CastBridge sur le téléphone, puis réessayez."),
            qa("La TV dit", "« Aucun téléphone n'est synchronisé avec la TV… »", "Reliez d'abord le téléphone à la TV, avec son code."),
            qa("La TV dit", "« Mettez CastBridge à jour pour l'Internet par relais »", "Mettez CastBridge à jour : 1.2.53 ou plus récent."),
        ]) +
        h3("Jouer en ligne par le téléphone", "internet-jeu") +
        cards([
            card("Partie par relais",
                 say("La TV dit", "« Partie par relais : liaison lente »") +
                 bl(["Ce n'est pas une alarme : la partie continue.",
                     "Aucune pénalité de classement n'est liée au relais.",
                     "Mises à jour et gros téléchargements attendent la fin de la partie.",
                     "Une coupure de moins de 60 secondes ne coûte rien."])),
            card("Ce que le téléphone laisse passer",
                 bl(["Pour la TV, le téléphone n'ouvre que le serveur CastBridge.",
                     "Aucun autre site, aucun test de débit : tout le reste est refusé.",
                     "La notification est neutre, avec un bouton « Arrêter »."])),
        ]) + '</section>')
    B.append(internet)

    # 8 : Clé USB mal éjectée (docs/STORAGE.md § 11) -----------------------------------
    n8 = seq("usb")
    usb = (
        h2("usb", "La clé USB") + ver(VERS_TV) +
        lead("La TV lit votre clé USB dès qu'on la branche.", "Retirée sans éjection, une clé peut demander un contrôle.") +
        h3("Retirer la clé sans risque", "usb-retrait") +
        grid([step(n8(), "tv-tuile-recevoir-du-telephone", ["usb"], "La tuile « Clé USB » dit si une clé est là.", "Tuile Clé USB de la TV", cls="wide")], "tv1") +
        cards([
            card("Avant de retirer la clé",
                 ns(["Sur la TV, appuyez sur MENU.",
                     "Choisissez « Préparer le retrait de la clé USB ».",
                     "Une copie écrit sur la clé ? Choisissez : attendre, ou mettre en pause.",
                     "Attendez le message : vous pouvez retirer la clé.",
                     "Retirez la clé."]) +
                 nt("La même ligne existe dans la tuile « Clé USB » de l'accueil.")),
            card("Si des copies écrivent sur la clé",
                 bl(["« Attendre la fin de la copie » : les copies en cours finissent.",
                     "« Mettre en pause et préparer le retrait » : elles s'arrêtent aussitôt.",
                     "Les copies en pause reprendront quand vous remettrez la clé.",
                     "Dix minutes sans retrait : les copies reprennent toutes seules.",
                     "« Reprendre l'utilisation de la clé » la rend à tout moment."])),
            card("Ce que dit la TV",
                 say("La TV dit", "« Vous pouvez retirer la clé « Lexar ». »") +
                 say("La TV dit", "« Pour une éjection complète : Réglages › Stockage › Éjecter »") +
                 p("Le bouton « Ouvrir les réglages de stockage » mène aux Réglages.") +
                 p("Android seul peut éjecter complètement la clé.")),
        ]) +
        h3("Clé retirée sans éjection : la TV la vérifie", "usb-verification") +
        lead("Au branchement, Android contrôle d'abord la clé.", "Cela dure quelques secondes, parfois plusieurs minutes.") +
        cards([
            card("Ce que vous voyez",
                 ns(["Branchez la clé : la TV dit « vérification par Android ».",
                     "Ne retirez pas la clé : patientez.",
                     "Après 20 secondes, la TV donne la durée écoulée.",
                     "Après 2 minutes, la TV dit « c'est long ».",
                     "Quand la clé est prête, la TV le dit."])),
            card("Ce que dit la TV",
                 say("La TV dit", "« Clé « Lexar » : vérification par Android (elle a été retirée sans éjection)… patientez »") +
                 say("La TV dit", "« Clé « Lexar » prête »") +
                 p("La TV reprend seule ce qu'elle cherchait sur la clé.")),
            card("Toujours en vérification ?",
                 p("Laissez-la faire encore quelques minutes.") +
                 p("Sinon, retirez la clé et vérifiez-la sur un ordinateur.")),
        ]) +
        missrow("Capture à ajouter : « Clé « Lexar » : vérification par Android… patientez » sur CastBridge-TV.",
                "Capture à ajouter : l'écran « Préparer le retrait de la clé USB » (MENU).") +
        h3("Clé illisible : la marche à suivre", "usb-illisible") +
        say("La TV dit", "« Clé illisible : Android n'a pas pu la réparer. Sur un ordinateur : Mac › Utilitaire de disque › S.O.S ; "
                         "Windows › clic droit › Propriétés › Outils › Vérifier ; ou Réglages de la TV › Stockage › Réparer/Formater "
                         "(le formatage efface tout) »") +
        cards([
            card("Sur un ordinateur",
                 bl(["Mac : Utilitaire de disque, puis S.O.S.",
                     "Windows : clic droit sur la clé, Propriétés, Outils, Vérifier."])),
            card("Sur la TV",
                 bl(["Réglages de la TV › Stockage › Réparer ou Formater.",
                     "Le formatage efface tout."]) +
                 p("Le bouton « Ouvrir les réglages de stockage » est dans l'explorateur de fichiers.") +
                 p("On le trouve aussi dans la bibliothèque et dans « Préparer le retrait ».")),
            card("Une promesse", p("CastBridge-TV ne répare et ne formate jamais rien lui-même.")),
        ]) +
        miss("Capture à ajouter : le guide « Clé illisible » avec le bouton « Ouvrir les réglages de stockage ».") +
        h3("Les autres messages de la TV", "usb-messages") +
        qas([
            qa("La TV dit", "« … lecture seule. Les vidéos se lisent, mais rien ne peut y être copié. Retirez le verrou de la clé, ou réparez-la sur un ordinateur »",
               "Retirez le verrou de la clé, ou réparez-la."),
            qa("La TV dit", "« … format non reconnu par la TV (clé vierge, ou format qu'Android ne lit pas). Sur un ordinateur, formatez-la en exFAT (le formatage efface tout)… »",
               "Formatez-la en exFAT sur un ordinateur : cela efface tout."),
            qa("La TV dit", "« Clé retirée pendant une copie : le fichier « … » est incomplet, il sera repris »",
               "Remettez la clé : la copie reprend là où elle s'est arrêtée."),
            qa("La TV dit", "« Clé « … » retirée sans éjection. La prochaine fois : MENU › Préparer le retrait de la clé USB »",
               "La prochaine fois, préparez le retrait avant de débrancher."),
            qa("La TV dit (en vert)", "« Clé « Lexar » retirée : elle était préparée, rien n'est perdu. Au prochain branchement Android la vérifiera : c'est normal sans éjection »",
               "Rien à faire : tout était écrit."),
            qa("La TV dit", "« éjection en cours, ne la retirez pas encore » · « éjectée : vous pouvez la retirer »",
               "Attendez le message « éjectée » avant de retirer la clé."),
            qa("La TV dit", "« La copie « … » finit sa vérification : attendez-la, puis réessayez »",
               "Attendez la fin de la vérification, puis réessayez."),
            qa("La TV dit", "« La TV n'a pas pu confirmer que tout est écrit sur la clé. Ne la retirez pas tout de suite »",
               "Ne retirez pas la clé : réessayez, ou reprenez l'utilisation de la clé."),
        ]) + '</section>')
    B.append(usb)

    # 9 -------------------------------------------------------------------
    s6 = [
        step(sn("parental", 1), "tv-tuile-controle-parental", ["parental"], "Sur la TV, ouvrez la tuile « Contrôle parental ».", "Tuile Contrôle parental"),
        step(sn("parental", 2), "tv-parental-accueil", ["creer"], "Créez un code parental de 4 à 6 chiffres.", "Créer le code parental"),
        step(sn("parental", 3), "tv-parental-clavier-code", ["valider"], "Tapez le code deux fois, puis « Valider ».", "Clavier du code parental"),
        step(sn("parental", 4), "tv-parental-reglages", ["etat", "profils"], "Ouvrez « Profils des enfants ».", "Réglages des parents"),
        step(sn("parental", 5), "tv-parental-profils", ["ajouter"], "Touchez « + Ajouter un profil » pour chaque enfant.", "Profils des enfants"),
    ]
    s6b = [step(sn("parental", 6), "telephone-onglets-suite", ["parental"], "Sur le téléphone, l'onglet « Parental » suit la TV.", "Onglet Parental du téléphone")]
    B.append(h2("parental", "Contrôle parental") + grid(s6, "tv") + grid(s6b, "ph") +
             '<p class="note c">L\'écran « Parental » du téléphone est protégé : il ne se capture pas.</p></section>')

    # 10 ------------------------------------------------------------------
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
    pb_act = (
        h3("Activer la TV avec le code", "probleme-activation") +
        qas([
            qa("Le téléphone dit", "« La TV n'a pas pu être jointe avec ce code. » (une ligne par chemin : réseau local, réseau de la TV, Bluetooth)",
               "Relisez les 6 chiffres, puis réessayez.", "Cochez les conditions sur la TV.", "Gardez la TV sur son écran d'activation."),
            qa("Le téléphone dit", "« Réseau de la TV introuvable. Si la TV affiche « Le téléphone n'est pas sur ce Wi-Fi ? », appuyez sur OK sur la TV, puis « Réessayer ». »",
               "Sur la TV, appuyez sur OK sur cette ligne.", "Puis touchez « Réessayer » sur le téléphone."),
            qa("Le téléphone dit", "« Mettez la TV à jour pour l'activation sans réseau. »",
               "Mettez CastBridge-TV à jour : 0.14.45 ou plus récente.", "Ou touchez « Lire la demande par Bluetooth »."),
            qa("Le téléphone propose", "« Changer le code »",
               "Retapez le code affiché sur la TV.", "Après 5 codes faux, la TV attend 60 secondes."),
            qa("La TV dit", "« Cochez d'abord les conditions d'usage : la clé USB sera lue ensuite »",
               "Cochez la case sur la TV, puis recommencez."),
            qa("Le téléphone propose", "« Autoriser »",
               "Acceptez « Appareils à proximité » : le téléphone en a besoin."),
            qa("Le téléphone ou la TV dit", "« clé d'une autre TV : refaites la demande avec le code d'appareil de cette TV »",
               "Demandez à l'agent une clé faite pour cette TV."),
            qa("La TV dit", "« clé périmée (valable 48 h) »", "Demandez une nouvelle clé à votre agent."),
            qa("Le téléphone dit", "« Installation… »",
               "Rien n'est cassé si vous quittez l'écran.", "Regardez la TV : elle est peut-être activée."),
            qa("La TV dit", "« Aucune clé USB détectée : branchez-la, la TV la lit aussitôt »",
               "Rebranchez la clé, ou essayez une autre prise USB."),
            qa("La TV dit", "« Android n'autorise pas CastBridge-TV à lire Download : donnez « Accès à tous les fichiers » (Réglages), ou déposez le fichier dans le dossier de l'application : &lt;chemin&gt; »",
               "Demandez une clé préparée par l'agent, ou utilisez le téléphone."),
        ]))
    pb_autres = (
        h3("Clé USB", "probleme-usb") +
        qas([
            qa("La TV dit", "« … vérification par Android … patientez »", "Patientez, sans retirer la clé.", "Voir la " + xref("usb", "La clé USB") + "."),
            qa("La TV dit", "« Clé illisible : Android n'a pas pu la réparer… »", "Réparez la clé sur un ordinateur.", "Voir la " + xref("usb", "La clé USB") + "."),
            qa("La TV dit", "« Clé retirée pendant une copie… »", "Remettez la clé : la copie reprend."),
        ]) +
        h3("Ouvrir sur la TV", "probleme-ouvrir") +
        qas([
            qa("Le téléphone dit", "« La TV ne répond pas : allumez-la… »", "Allumez la TV : « Ouvrir sur la TV » ne l'allume pas.",
               "Voir la " + xref("envoyer", "Envoyer une vidéo") + "."),
        ]) +
        h3("Internet par le téléphone", "probleme-internet") +
        qas([
            qa("La TV dit", "« Aucun téléphone n'est synchronisé avec la TV… »", "Reliez d'abord le téléphone à la TV.",
               "Voir la " + xref("internet", "Internet par le téléphone") + "."),
        ]) +
        h3("Jeux", "probleme-jeux") +
        qas([
            qa("La TV et les autres joueurs voient", "« déconnecté · reprise possible encore NN s »",
               "Le joueur coupé rouvre la page : même place, même partie."),
            qa("La TV dit", "« Connexion Internet requise »", "Donnez Internet à la TV, ou utilisez le tuyau du téléphone."),
            qa("La TV dit", "« Les échecs en ligne ne sont pas encore ouverts sur le service CastBridge… »",
               "Le service n'est pas ouvert : réessayez plus tard."),
        ]))
    B.append(h2("probleme", "Si ça ne marche pas") + '<div class="steps cases">%s</div>' % "".join(cases) +
             '<p class="note c">« Déplacer vers la TV » libère la place du téléphone, pas celle de la TV.</p>' +
             pb_act + pb_autres + '</section>')

    # 11 ------------------------------------------------------------------
    lines = [
        ("La TV n'a pas besoin d'Internet, sauf pour jouer en ligne.", '<svg viewBox="0 0 40 40"><path d="M6 20 h28 M20 6 v28" class="ln er"/><circle cx="20" cy="20" r="14" class="st"/></svg>'),
        ("Les statistiques d'usage : seulement si vous acceptez.", '<svg viewBox="0 0 40 40"><path d="M8 22 l8 8 l16 -18" class="ln ok"/></svg>'),
        ("Vous changez d'avis à tout moment dans « Réglages ».", '<svg viewBox="0 0 40 40"><circle cx="20" cy="20" r="8" class="st"/><path d="M20 4 v6 M20 30 v6 M4 20 h6 M30 20 h6" class="ln"/></svg>'),
        ("Si le téléphone prête son Internet, seul le serveur CastBridge est joignable.", '<svg viewBox="0 0 40 40"><path d="M8 22 l8 8 l16 -18" class="ln ok"/></svg>'),
    ]
    for t, _ in lines:
        check(t, "données")
    s8 = [
        step(sn("donnees", 1), "telephone-donnees-hors-ligne-suite", ["surlatv"], "Sur la TV : « La TV n'a jamais besoin d'Internet ».", "Texte Sur la TV"),
        step(sn("donnees", 2), "telephone-donnees-et-consentement", ["essentiel", "stats"], "Vous choisissez au premier lancement.", "Choix des statistiques"),
        step(sn("donnees", 3), "telephone-reglages", ["stats", "mesdonnees", "effacer"], "Dans « Réglages » : « Mes données » et « Effacer mes données ».", "Réglages du téléphone"),
    ]
    B.append(h2("donnees", "Vos données") + '<ul class="three">%s</ul>' %
             "".join('<li>%s<span>%s</span></li>' % (ic, esc(t)) for t, ic in lines) + grid(s8, "ph") + '</section>')
    # 12 ------------------------------------------------------------------
    B.append(h2("aide", "Aide") +
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
.steps.tv1{grid-template-columns:minmax(0,560px);justify-content:center}
a.lk{color:var(--ac);font-weight:700}
.lead{margin:4px 0 12px;font-size:1.05rem}
.ver{display:block;width:fit-content;max-width:100%;margin:2px 0 12px;padding:3px 12px;border:1px solid var(--ac);border-radius:14px;background:var(--chip);color:var(--ac);font-size:.8rem;font-weight:700}
.ver.c{margin:10px auto 0;text-align:center}
.gh{margin:24px 0 6px;font-size:1.12rem}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,270px),1fr));gap:14px;margin:12px 0;align-items:start}
.cards.one{grid-template-columns:minmax(0,1fr);margin:0}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:14px;display:flex;flex-direction:column;gap:8px;min-width:0}
.card h4{margin:0}.card .s2{margin:0}
.card.dlp{margin:12px 0}
.dlc{align-items:center;text-align:center}.dlc svg{width:110px;height:auto}
.url{margin:0;font-weight:700;font-size:.9rem;word-break:break-all;user-select:all;-webkit-user-select:all}
.ns{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:8px}
.ns li{display:flex;gap:10px;align-items:flex-start;font-weight:600}
.ns li b{flex:none;width:26px;height:26px;border-radius:50%;background:var(--hl);color:#111;display:inline-grid;place-items:center;font-size:.85rem;margin-top:1px}
.bl{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:6px}
.bl li{display:flex;gap:8px;align-items:flex-start;font-weight:600}
.bl li::before{content:"•";color:var(--ac);font-weight:700}
.say{margin:2px 0;padding:8px 12px;border-left:4px solid var(--ac2);background:var(--chip);border-radius:8px;font-size:.92rem;font-weight:600}
.say b{display:block;font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:var(--mut);margin-bottom:2px}
.miss{margin:8px 0;padding:10px 12px;border:2px dashed var(--line);border-radius:12px;color:var(--mut);font-size:.84rem;text-align:center}
.miss b{display:block;color:var(--fg);font-size:.72rem;text-transform:uppercase;letter-spacing:.05em}
.miss figcaption{margin-top:2px}
.missrow{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr));gap:10px;margin:8px 0}
.missrow .miss{margin:0}
.split{display:grid;gap:16px;align-items:start;margin:12px 0}
@media (min-width:700px){.split{grid-template-columns:300px minmax(0,1fr)}}
.qas{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,300px),1fr));gap:12px;margin:12px 0;align-items:stretch}
.qa{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:10px 12px;display:flex;flex-direction:column;gap:6px;min-width:0}
.qa .say{margin:0}.qa p{margin:0;font-weight:700;font-size:.98rem}.qa p b{color:var(--ac)}
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
    if VIOLATIONS:
        print("phrases de plus de 12 mots (%d) :" % len(VIOLATIONS))
        for v in VIOLATIONS:
            print("  -", v)
        raise SystemExit(1)
    body = typo(body)
    css_imgs = []
    for name in USED:
        s = BY[name]
        css_imgs.append(".im-%s{background-image:url(%s)}" % (s["id"], jpeg_uri(os.path.join(SCR, s["file"]), s["kind"])))
    toc = [(sid, str(i), label) for i, (sid, label) in enumerate(SECTIONS, 1)]
    nav = "".join('<a href="#%s"><b>%s</b>%s</a>' % (i, n, t) for i, n, t in toc)
    html = ('<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>Guide CastBridge</title><meta name="color-scheme" content="light dark"><style>%s%s</style></head><body>%s'
            '<div class="wrap"><header class="top"><h1>Guide CastBridge</h1><p class="sub">Envoyer vos vidéos du téléphone vers la TV. CastBridge sur le téléphone, CastBridge-TV sur la TV.</p></header>'
            '<nav class="toc" aria-label="Sommaire">%s</nav>%s'
            '<footer>Les captures viennent d\'un émulateur avec des vidéos de test et des codes masqués. Les dessins sont signalés. '
            'Un cadre en pointillés marque une illustration qui manque encore.<br>'
            'Version du guide : 2026-10-07 · CastBridge 1.2.53 · CastBridge-TV 0.14.45 (captures de CastBridge 1.2.50 et CastBridge-TV 0.14.43)</footer></div></body></html>'
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
    print("illustrations manquantes (légendes laissées dans le guide) :", len(MISSING))
    for m in MISSING:
        print("  -", html_unescape(m))


if __name__ == "__main__":
    main()
