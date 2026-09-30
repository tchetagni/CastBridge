#!/usr/bin/env python3
"""Génère le guide PDF de la charte graphique CastBridge (reportlab)."""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Image, Table,
    TableStyle, PageBreak, NextPageTemplate, KeepTogether,
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMG = "/tmp/pdfimg"
FONT_DIR = os.path.join(ROOT, "fonts")

# ---- Palette ----
INK = colors.HexColor("#0A0F1E")
SURFACE = colors.HexColor("#151D37")
GOLD = colors.HexColor("#F5B025")
GOLD_LIGHT = colors.HexColor("#FFE1A6")
GREEN = colors.HexColor("#2E9E6B")
ORANGE = colors.HexColor("#FF8A3D")
CORAL = colors.HexColor("#FF5C39")
AMBER = colors.HexColor("#FFB020")
TEAL = colors.HexColor("#27C7B0")
FOREST = colors.HexColor("#0C1B14")
TEALDARK = colors.HexColor("#0B1B1E")
GREEN2 = colors.HexColor("#2FA96B")
GOLD2 = colors.HexColor("#F2C14E")
WHITE = colors.HexColor("#F4F6FB")
PAPER = colors.white
TEXT_MED = colors.HexColor("#B7C0D4")
TEXT_LOW = colors.HexColor("#6E7A93")
INK_TEXT = colors.HexColor("#111827")

# ---- Polices ----
def register():
    try:
        pdfmetrics.registerFont(TTFont("Bricolage", os.path.join(FONT_DIR, "BricolageGrotesque.ttf")))
        pdfmetrics.registerFont(TTFont("Inter", os.path.join(FONT_DIR, "Inter.ttf")))
        pdfmetrics.registerFont(TTFont("Sora", os.path.join(FONT_DIR, "Sora.ttf")))
        pdfmetrics.registerFont(TTFont("Manrope", os.path.join(FONT_DIR, "Manrope.ttf")))
        return "Bricolage", "Inter", "Sora", "Manrope"
    except Exception:
        return "Helvetica-Bold", "Helvetica", "Helvetica-Bold", "Helvetica"

HEAD, BODY, QUIZ, ECHECS = register()

styles = {
    "coverTitle": ParagraphStyle("coverTitle", fontName=HEAD, fontSize=40, leading=44,
                                 textColor=PAPER, alignment=TA_CENTER),
    "coverSub": ParagraphStyle("coverSub", fontName=BODY, fontSize=15, leading=20,
                               textColor=GOLD, alignment=TA_CENTER),
    "h1": ParagraphStyle("h1", fontName=HEAD, fontSize=21, leading=25, textColor=INK,
                         spaceBefore=2, spaceAfter=6),
    "h2": ParagraphStyle("h2", fontName=HEAD, fontSize=13.5, leading=17, textColor=GOLD,
                         spaceBefore=8, spaceAfter=3),
    "body": ParagraphStyle("body", fontName=BODY, fontSize=9.5, leading=14, textColor=INK_TEXT,
                           spaceAfter=4),
    "small": ParagraphStyle("small", fontName=BODY, fontSize=8, leading=11, textColor=TEXT_LOW),
    "chip": ParagraphStyle("chip", fontName=BODY, fontSize=8.5, leading=11, textColor=INK_TEXT),
    "iconLabel": ParagraphStyle("iconLabel", fontName=BODY, fontSize=7.2, leading=9,
                                textColor=INK_TEXT, alignment=TA_CENTER),
    "toc": ParagraphStyle("toc", fontName=BODY, fontSize=10, leading=16, textColor=INK_TEXT),
}


def P(text, style="body"):
    return Paragraph(text, styles[style])


def swatch_table(rows, col4=None):
    data = []
    for r in rows:
        label, hexv = r[0], r[1]
        usage = r[2] if len(r) > 2 else ""
        data.append(["", P(label, "chip"), P(hexv, "chip"), P(usage, "small")])
    t = Table(data, colWidths=[9 * mm, 38 * mm, 27 * mm, None])
    style = [
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LEFTPADDING", (1, 0), (1, -1), 0),
        ("LINEBELOW", (0, 0), (-1, -2), 0.3, colors.HexColor("#DDE3EF")),
    ]
    for i, r in enumerate(rows):
        style.append(("BACKGROUND", (0, i), (0, i), colors.HexColor(r[1])))
        style.append(("BOX", (0, i), (0, i), 0.3, colors.HexColor("#B9C2D4")))
    t.setStyle(TableStyle(style))
    return t


def plain_table(rows):
    data = [[P(r[0], "chip"), P(r[1], "chip"), P(r[2], "small")] for r in rows]
    t = Table(data, colWidths=[36 * mm, 30 * mm, None])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LINEBELOW", (0, 0), (-1, -2), 0.3, colors.HexColor("#DDE3EF")),
    ]))
    return t


def img_table(path, width_mm, bg=None, height_mm=None):
    img = Image(path, width=width_mm * mm, height=(height_mm * mm if height_mm else None))
    t = Table([[img]], colWidths=[width_mm * mm + 8 * mm])
    st = [("ALIGN", (0, 0), (-1, -1), "CENTER"),
          ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
          ("TOPPADDING", (0, 0), (-1, -1), 4),
          ("BOTTOMPADDING", (0, 0), (-1, -1), 4)]
    if bg:
        st += [("BACKGROUND", (0, 0), (-1, -1), bg),
               ("BOX", (0, 0), (-1, -1), 0.4, colors.HexColor("#2A3550"))]
    t.setStyle(TableStyle(st))
    return t


def icon_grid(items):
    data = []
    row = []
    for name, label in items:
        img = Image(os.path.join(IMG, f"icon-{name}.png"), width=13 * mm, height=13 * mm)
        cell = Table([[img], [P(label, "iconLabel")]], colWidths=[24 * mm])
        cell.setStyle(TableStyle([("ALIGN", (0, 0), (-1, -1), "CENTER"),
                                  ("VALIGN", (0, 0), (0, 0), "MIDDLE"),
                                  ("TOPPADDING", (0, 0), (0, 0), 2),
                                  ("BOTTOMPADDING", (0, 1), (0, 1), 0)]))
        row.append(cell)
        if len(row) == 6:
            data.append(row)
            row = []
    if row:
        data.append(row)
    t = Table(data, colWidths=[24 * mm] * 6)
    t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"),
                           ("TOPPADDING", (0, 0), (-1, -1), 5),
                           ("BOTTOMPADDING", (0, 0), (-1, -1), 5)]))
    return t


def header_footer(canvas, doc):
    canvas.saveState()
    if doc.page > 1:
        canvas.setFillColor(TEXT_LOW)
        canvas.setFont(BODY, 7.5)
        canvas.drawString(18 * mm, 10 * mm, "CastBridge - Charte graphique")
        canvas.drawRightString(A4[0] - 18 * mm, 10 * mm, f"Page {doc.page}")
        canvas.setStrokeColor(colors.HexColor("#E5E9F2"))
        canvas.line(18 * mm, 14 * mm, A4[0] - 18 * mm, 14 * mm)
    canvas.restoreState()


def build():
    out = os.path.join(ROOT, "guide", "CastBridge-charte-graphique.pdf")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    doc = BaseDocTemplate(out, pagesize=A4,
                          leftMargin=18 * mm, rightMargin=18 * mm,
                          topMargin=18 * mm, bottomMargin=18 * mm,
                          title="CastBridge - Charte graphique",
                          author="CastBridge")
    frame = Frame(18 * mm, 18 * mm, A4[0] - 36 * mm, A4[1] - 36 * mm, id="f")
    doc.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=header_footer)])

    story = []

    # ---- Couverture ----
    cover_cell = [
        [Spacer(1, 22 * mm)],
        [Image(os.path.join(IMG, "symbol.png"), width=42 * mm, height=42 * mm)],
        [Spacer(1, 8 * mm)],
        [P("CastBridge", "coverTitle")],
        [Spacer(1, 3 * mm)],
        [P("Charte graphique - Le pont simple entre vos écrans", "coverSub")],
        [Spacer(1, 6 * mm)],
        [P("Écosystème TV + smartphone - Cameroun & Afrique francophone", "small")],
    ]
    cover = Table(cover_cell, colWidths=[A4[0] - 36 * mm])
    cover.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("BACKGROUND", (0, 0), (-1, -1), INK),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    story.append(cover)
    story.append(NextPageTemplate("main"))
    story.append(PageBreak())

    # ---- Sommaire ----
    story.append(P("Sommaire", "h1"))
    for line in [
        "1. Plateforme de marque",
        "2. Architecture de marque",
        "3. Logo et symbolique",
        "4. Couleurs",
        "5. Typographie",
        "6. Grille, espacements, rayons, élévation",
        "7. États de focus et animations",
        "8. Iconographie",
        "9. Sous-marques",
        "10. Spécifications Android",
        "11. Livrables et fichiers",
    ]:
        story.append(P(line, "toc"))
    story.append(PageBreak())

    # ---- 1. Plateforme ----
    story.append(P("1. Plateforme de marque", "h1"))
    story.append(P("<font name='Bricolage'>Positionnement</font> : CastBridge relie le téléphone à la télévision pour partager, regarder et jouer ensemble, même sans connexion.", "body"))
    story.append(P("<font name='Bricolage'>Personnalité</font> : moderne, chaleureuse, fiable, fièrement africaine - sans folklore.", "body"))
    story.append(P("<font name='Bricolage'>Ton</font> : français courant, phrases courtes, verbes d'action (Envoie, Continue, Joue).", "body"))
    story.append(P("<font name='Bricolage'>Tutoiement</font> : le tutoiement est la règle dans l'app (proximité, public jeune et familial). Le vouvoiement est réservé au support et aux mentions légales.", "body"))
    story.append(P("2. Architecture de marque", "h1"))
    story.append(P("Les trois pistes créatives forment l'ADN de l'écosystème :", "body"))
    arch = Table([
        [Image(os.path.join(IMG, "symbol.png"), width=22 * mm, height=22 * mm),
         P("<font name='Bricolage'>CastBridge</font> (marque mère)", "chip"), P("Piste 1 « Le Pont » - or / indigo", "small")],
        [Image(os.path.join(IMG, "sub-tv.png"), width=22 * mm, height=22 * mm),
         P("<font name='Bricolage'>CastBridge TV</font>", "chip"), P("Le pont, icône de l'app TV", "small")],
        [Image(os.path.join(IMG, "sub-quiz.png"), width=22 * mm, height=22 * mm),
         P("<font name='Bricolage'>Quiz des Millions</font>", "chip"), P("Piste 2 « Le Signal » - corail / ambre", "small")],
        [Image(os.path.join(IMG, "sub-echecs.png"), width=22 * mm, height=22 * mm),
         P("<font name='Bricolage'>Échecs</font>", "chip"), P("Piste 3 « Le Lien » - vert / or", "small")],
    ], colWidths=[24 * mm, 48 * mm, 92 * mm])
    arch.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                              ("TOPPADDING", (0, 0), (-1, -1), 5),
                              ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
                              ("LINEBELOW", (0, 0), (-1, -2), 0.3, colors.HexColor("#DDE3EF"))]))
    story.append(arch)
    story.append(PageBreak())

    # ---- 3. Logo ----
    story.append(P("3. Logo et symbolique", "h1"))
    story.append(P("Le symbole : un pont-arc entre deux écrans (téléphone et télé), une lueur qui transite. Lisible dès la petite taille.", "body"))
    story.append(P("Symbole (fond sombre / fond clair)", "h2"))
    sym = Table([[img_table(os.path.join(IMG, "symbol.png"), 30),
                  img_table(os.path.join(IMG, "symbol-light.png"), 30, bg=PAPER)]],
                colWidths=[70 * mm, 70 * mm])
    sym.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP")]))
    story.append(sym)
    story.append(P("Verrouillages", "h2"))
    story.append(img_table(os.path.join(IMG, "horiz-dark.png"), 150, bg=INK))
    story.append(Spacer(1, 3 * mm))
    story.append(img_table(os.path.join(IMG, "horiz-light.png"), 150, bg=PAPER))
    story.append(Spacer(1, 3 * mm))
    story.append(img_table(os.path.join(IMG, "vert-dark.png"), 66, bg=INK))
    story.append(Spacer(1, 3 * mm))
    story.append(P("Monochrome (pour tampon, une seule couleur)", "h2"))
    story.append(img_table(os.path.join(IMG, "mono.png"), 150, bg=INK))
    story.append(P("Zone de protection : au minimum la hauteur du téléphone (pilier gauche) tout autour du logo. Taille minimale : 24 px pour le symbole seul, 96 px de large pour le verrouillage horizontal.", "small"))
    story.append(PageBreak())

    # ---- 4. Couleurs ----
    story.append(P("4. Couleurs", "h1"))
    story.append(P("Le thème sombre est premier (TV), le thème clair sert au téléphone. Les contrastes respectent WCAG AA.", "body"))
    story.append(P("Thème sombre (TV)", "h2"))
    story.append(swatch_table([
        ("Fond", "#0A0F1E", "arrière-plan principal"),
        ("Surface", "#151D37", "cartes, rangs"),
        ("Surface haute", "#1B2542", "survol, dialogues"),
        ("Primaire", "#F5B025", "actions, focus"),
        ("Secondaire", "#2E9E6B", "bibliothèque, succès"),
        ("Accent", "#FF8A3D", "caster"),
        ("Texte", "#F4F6FB", "texte principal"),
        ("Texte secondaire", "#B7C0D4", "métadonnées"),
        ("Contour", "#2A3550", "bordures"),
    ]))
    story.append(P("Thème clair (téléphone)", "h2"))
    story.append(swatch_table([
        ("Fond", "#F7F8FC", "arrière-plan"),
        ("Surface", "#FFFFFF", "cartes"),
        ("Primaire", "#B7791F", "actions, focus"),
        ("Secondaire", "#1F8A5C", "succès"),
        ("Accent", "#E4692E", "caster"),
        ("Texte", "#111827", "texte principal"),
        ("Texte secondaire", "#3E4A61", "métadonnées"),
        ("Contour", "#D7DCE7", "bordures"),
    ]))
    story.append(P("Couleurs sémantiques (sombre / clair)", "h2"))
    story.append(swatch_table([
        ("Succès", "#35C08A", "clair : #1F8A5C"),
        ("Alerte", "#F5B025", "clair : #9A6500"),
        ("Erreur", "#FF6B6B", "clair : #C5343A"),
        ("Info", "#6CB6FF", "clair : #1668C7"),
    ]))
    story.append(PageBreak())

    # ---- 5. Typo ----
    story.append(P("5. Typographie", "h1"))
    story.append(P("Polices Google Fonts libres (licence OFL) : Bricolage Grotesque (titres), Inter (texte), Sora (Quiz), Manrope (Échecs).", "body"))
    story.append(P("Hiérarchie TV (1080p, lecture à 3 m - minimum 24 px)", "h2"))
    story.append(plain_table([
        ("Display", "72 px / 800", "titre de page"),
        ("Titre", "40 px / 700", "titres de rangée"),
        ("Sous-titre", "32 px / 600", "sous-titres"),
        ("Corps", "28 px / 400", "texte courant"),
        ("Bouton", "28 px / 700", "libellés d'action"),
        ("Légende", "24 px / 500", "métadonnées (min.)"),
    ]))
    story.append(P("Hiérarchie mobile (sp)", "h2"))
    story.append(plain_table([
        ("Display", "32 sp / 800", ""),
        ("Titre", "22 sp / 700", ""),
        ("Corps", "16 sp / 400", ""),
        ("Légende", "12 sp / 500", ""),
    ]))
    story.append(P("6. Grille, espacements, rayons, élévation", "h1"))
    story.append(P("Grille TV : 12 colonnes, gouttière 24 dp, marge 48 dp, carte 260 dp en 16:9. Mobile : 4 colonnes, gouttière et marge 16 dp.", "body"))
    story.append(P("Espacements : 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96 dp.", "body"))
    story.append(P("Rayons : sm 8, md 12, lg 16, xl 24, pilule 999 dp.", "body"))
    story.append(P("Élévation : 3 niveaux d'ombre (2/8/24, 8/24, 16/40 px d'étalement), discrets sur fond sombre.", "body"))
    story.append(PageBreak())

    # ---- 7. Focus & motion ----
    story.append(P("7. États de focus et animations", "h1"))
    story.append(P("<font name='Bricolage'>Focus TV (critique à la télécommande)</font> : anneau lumineux #FFE1A6 de 3 dp, décalage 2 dp, et mise à l'échelle 1,04 de l'élément focalisé. Le focus doit rester visible à 3 m ; jamais de simple changement de couleur subtil.", "body"))
    story.append(P("<font name='Bricolage'>Mobile</font> : anneau 2 dp #B7791F.", "body"))
    story.append(P("<font name='Bricolage'>Animations</font> : durée rapide 120 ms, standard 200 ms, lente 320 ms. Courbes : standard cubic-bezier(0.2, 0, 0, 1), décélération cubic-bezier(0.05, 0.7, 0.1, 1), accélération cubic-bezier(0.3, 0, 1, 1).", "body"))
    story.append(P("Rappel mémoire : pas de vidéo ni d'images lourdes en fond - uniquement vecteurs et dégradés simples (TV ~ 1 Go de RAM).", "body"))
    story.append(P("8. Iconographie", "h1"))
    story.append(P("Pictogrammes en trait 1,8 dp, coins et extrémités arrondis, couleur currentColor (thématisables). Grille 24 x 24.", "body"))
    icons = [
        ("bibliotheque", "Bibliothèque"), ("quiz", "Quiz"), ("echecs", "Échecs"),
        ("recevoir-du-telephone", "Recevoir du tél."), ("caster", "Caster"), ("envoyer", "Envoyer"),
        ("deplacer-vers-tv", "Déplacer vers la TV"), ("copier", "Copier"), ("telechargements", "Téléchargements"),
        ("cle-usb", "Clé USB"), ("bluetooth", "Bluetooth"), ("wifi-direct", "Wi-Fi Direct"),
        ("wifi", "Wi-Fi"), ("test-internet", "Test Internet"), ("administration", "Administration"),
        ("mises-a-jour", "Mises à jour"), ("reglages", "Réglages"), ("aide", "Aide"),
        ("lecture", "Lecture"), ("pause", "Pause"), ("avance-10s", "Avancer 10 s"),
        ("recul-10s", "Reculer 10 s"), ("sous-titres", "Sous-titres"), ("pistes-audio", "Pistes audio"),
    ]
    story.append(icon_grid(icons))
    story.append(PageBreak())

    # ---- 9. Sous-marques ----
    story.append(P("9. Sous-marques", "h1"))
    story.append(P("Chaque sous-marque reprend la géométrie de la marque mère avec son propre accent, pour rester cohérent.", "body"))
    subs = Table([
        [img_table(os.path.join(IMG, "sub-tv.png"), 26), img_table(os.path.join(IMG, "sub-quiz.png"), 26), img_table(os.path.join(IMG, "sub-echecs.png"), 26)],
        [P("CastBridge TV", "chip"), P("Quiz des Millions", "chip"), P("Échecs", "chip")],
    ], colWidths=[48 * mm, 48 * mm, 48 * mm])
    subs.setStyle(TableStyle([("ALIGN", (0, 0), (-1, -1), "CENTER"),
                              ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                              ("TOPPADDING", (0, 0), (-1, -1), 4)]))
    story.append(subs)
    story.append(P("CastBridge TV - pont / or ; Quiz des Millions - corail #FF5C39 et ambre #FFB020 sur vert d'eau ; Échecs - vert #2FA96B et or #F2C14E sur vert forêt.", "small"))
    story.append(P("10. Spécifications Android", "h1"))
    story.append(P("<font name='Bricolage'>Icône adaptative</font> : canevas 108 dp, zone sûre 66 dp (le symbole occupe ~ 55 dp), calques avant / arrière + version monochrome (Android 13+).", "body"))
    story.append(P("<font name='Bricolage'>Bannière Android TV</font> : 320 x 180 px, sans vidéo.", "body"))
    story.append(P("<font name='Bricolage'>Play Store</font> : 512 x 512 px plein cadre.", "body"))
    story.append(P("<font name='Bricolage'>Favicon</font> : SVG + PNG 16/32/48/180 + .ico.", "body"))
    story.append(P("11. Livrables et fichiers", "h1"))
    story.append(P("logo/ (SVG du logo et des sous-marques), icons/ (24 SVG), design-tokens.json, export/ (PNG @1x/@2x/@3x + formats Android + drawables vectoriels), fonts/ (polices OFL), guide/ (ce PDF).", "body"))

    doc.build(story)
    print("Wrote", out)


if __name__ == "__main__":
    build()
