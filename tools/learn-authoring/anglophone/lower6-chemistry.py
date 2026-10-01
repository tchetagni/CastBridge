"""Generator: lower6-chemistry (Chemistry — Lower Sixth, GCE Advanced Level syllabus, Cameroon GCE Board). python3 lower6-chemistry.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pc import *

# Ar used: H 1.0, C 12.0, N 14.0, O 16.0, Na 23.0, Mg 24.0, Al 27.0, S 32.0, Cl 35.5, K 39.0, Ca 40.0, Fe 56.0, Cu 63.5, Zn 65.0, Br 80.0, Ag 108.0
# molar volume 24.0 dm3 (rtp, 25 C, 1 atm); 22.4 dm3 (stp); R = 8.31 J/(mol K); N_A = 6.02e23; F = 96500 C/mol; Kw = 1.0e-14 (25 C); c(water) = 4.18 J/(g K)
AR = dict(H=1.0, C=12.0, N=14.0, O=16.0, Na=23.0, Mg=24.0, Al=27.0, S=32.0, Cl=35.5, K=39.0, Ca=40.0, Fe=56.0, Cu=63.5, Zn=65.0, Br=80.0, Ag=108.0)
R_, NA_, F_ = 8.31, 6.02e23, 96500.0
p = Pack("lower6-chemistry", "Chemistry — Lower Sixth", level="Lower Sixth", subject="chemistry", cursus="secondary",
         description="Lower Sixth chemistry for the GCE Advanced Level course: atomic structure and periodicity, bonding and shapes, the mole and gases, energetics, kinetics, "
                     "equilibrium, acids and bases, redox and electrolysis, and the basics of organic chemistry, with worked examples, graded exercises and diagrams. Draft.",
         programRef="Cameroon GCE Board — Advanced Level Chemistry syllabus (Lower Sixth part) — to be checked against the official texts",
         source_note="Original exercise, GCE Advanced Level style")
SYL = "GCE Advanced Level Chemistry — %s (to be checked against the official syllabus)"
DATA = "Data used: Ar values rounded as in common school tables (H 1.0, C 12.0, N 14.0, O 16.0, Na 23.0, Mg 24.0, Al 27.0, S 32.0, Cl 35.5, K 39.0, Ca 40.0, Fe 56.0, Cu 63.5, Zn 65.0); molar volume of a gas 24.0 dm³ at room temperature and pressure (25 °C, 1 atm), 22.4 dm³ at stp; R = 8.31 J mol⁻¹ K⁻¹; N_A = 6.02 × 10²³ mol⁻¹."

# =============================================================== 1. ATOMS AND PERIODICITY
ch = p.chapter("ch-atoms", "Atomic structure and periodicity", SYL % "atomic structure and the Periodic Table")

# ---- 1.1 atomic structure
ArCl = (35 * 75 + 37 * 25) / 100; assert ArCl == 35.5
ArMg = (24 * 78.6 + 25 * 10.1 + 26 * 11.3) / 100; assert abs(ArMg - 24.327) < 1e-9
ArB = (10 * 20 + 11 * 80) / 100; assert abs(ArB - 10.8) < 1e-9
ArCu = (63 * 69.2 + 65 * 30.8) / 100; assert abs(ArCu - 63.616) < 0.001
def shells_fig():
    cx, cy = 150, 125
    it = [CIRCLE(cx, cy, 14, fill="lightorange"), T(cx, cy + 4, "11p", 10)]
    for r, n in [(36, 2), (62, 8), (88, 1)]:
        it.append(CIRCLE(cx, cy, r, stroke="grey", width=1))
        for i in range(n):
            a = 2 * math.pi * i / n + (0.6 if r == 62 else 0.3)
            it.append(CIRCLE(cx + r * math.cos(a), cy - r * math.sin(a), 5, fill="blue", stroke="blue"))
    it += [T(300, 90, "sodium", 14, bold=True), T(300, 114, "11 protons", 12), T(300, 134, "12 neutrons", 12), T(300, 154, "2, 8, 1", 13), T(300, 174, "1s² 2s² 2p⁶ 3s¹", 12)]
    return shapes(it, 400, 250)
lesson(ch, "atoms", "Atomic structure, isotopes and electron configuration",
  ["Describe protons, neutrons and electrons and write nuclide symbols; calculate relative atomic mass from isotopic abundances",
   "Write electronic configurations of atoms and ions using s, p and d subshells",
   "Define first ionisation energy and use successive ionisation energies to deduce the group of an element"],
  [("definition", "The atom and its isotopes",
    "An atom has a small, dense **nucleus** of protons (relative mass 1, charge +1) and neutrons (mass 1, charge 0) surrounded by electrons (mass about 1/1836, charge −1). The **atomic number** Z = number of protons; the **mass number** A = protons + neutrons.\n\n"
    "**Isotopes** are atoms of the same element (same Z) with different numbers of neutrons. They have the same chemical properties and slightly different physical properties."),
   ("formule", "Relative atomic mass",
    "The **relative atomic mass** A_r is the weighted mean mass of an atom of the element compared with 1/12 of the mass of an atom of carbon-12.\n\n**A_r = Σ(mass number × abundance %) ÷ 100.** A mass spectrometer ionises the atoms, accelerates them, deflects them in a magnetic field and detects them: each peak shows one isotope with its abundance.",
    r"A_r=\frac{\sum (\text{mass number}\times\text{abundance}\ \%)}{100}"),
   ("retenir", "Electron configuration",
    "Electrons occupy shells (n = 1, 2, 3…) made of subshells s (2 electrons, 1 orbital), p (6 electrons, 3 orbitals) and d (10 electrons, 5 orbitals). Fill in order of increasing energy: **1s 2s 2p 3s 3p 4s 3d 4p…** Each orbital holds two electrons of opposite spin; in a subshell, orbitals are filled singly first (Hund's rule).\n\nExceptions: Cr is [Ar] 3d⁵ 4s¹ and Cu is [Ar] 3d¹⁰ 4s¹ (half-full and full 3d are stable). Atoms form positive ions by losing the **4s** electrons before the 3d ones."),
   ("definition", "Ionisation energy",
    "The **first ionisation energy** is the energy needed to remove one electron from each atom in one mole of gaseous atoms: X(g) → X⁺(g) + e⁻ (kJ mol⁻¹). Successive ionisation energies increase, and a **large jump** shows the start of a new, inner shell: for example a big jump after the 2nd ionisation energy means 2 outer electrons (Group 2)."),
   ("pieges", "Common mistakes",
    "- Using the mass number (a whole number) instead of the weighted A_r (35.5 for chlorine).\n- Writing Fe²⁺ as losing 3d electrons first: the 4s electrons go first.\n- Saying isotopes differ in the number of protons or electrons.\n- Forgetting that A_r has no units (it is relative).")],
  [("Example 1 — Relative atomic mass", "Chlorine consists of 75 % ³⁵Cl and 25 % ³⁷Cl. Calculate A_r. Then give the number of protons, neutrons and electrons in ⁵⁶Fe³⁺.",
    ["A_r = (35 × 75 + 37 × 25)/100 = (2625 + 925)/100 = 35.5.", "Fe has Z = 26, so 26 protons; neutrons = 56 − 26 = 30.", "Fe³⁺ has lost three electrons: 26 − 3 = 23 electrons."], "A_r = 35.5; ⁵⁶Fe³⁺: 26 p, 30 n, 23 e"),
   ("Example 2 — Configurations", "Write the electron configurations of Fe, Fe²⁺ and Fe³⁺.",
    ["Fe (Z = 26): 1s² 2s² 2p⁶ 3s² 3p⁶ 3d⁶ 4s² (write 3d before 4s or after: both are accepted).", "Fe²⁺ loses the two 4s electrons: 1s² 2s² 2p⁶ 3s² 3p⁶ 3d⁶.", "Fe³⁺ then loses one 3d electron: 1s² 2s² 2p⁶ 3s² 3p⁶ 3d⁵ (a stable half-filled 3d subshell)."], "Fe³⁺ is [Ar] 3d⁵")],
  [N("Boron is 20 % ¹⁰B and 80 % ¹¹B. Calculate A_r (3 s.f.).", sg(ArB, 3), "(10 × 20 + 11 × 80)/100 = 10.8.", 0.01, None),
   M("Which is the electron configuration of the sulfide ion S²⁻ (Z = 16)?", "1s² 2s² 2p⁶ 3s² 3p⁶", ["1s² 2s² 2p⁶ 3s² 3p⁴", "1s² 2s² 2p⁶ 3s² 3p⁶ 4s²", "1s² 2s² 2p⁶ 3s² 3p²"], "S has 16 electrons; S²⁻ has 18, the same as argon."),
   TF("True or false: isotopes of an element have different numbers of protons.", False, "They have the same number of protons (same Z) and different numbers of neutrons.")],
  [N("Magnesium is 78.6 % ²⁴Mg, 10.1 % ²⁵Mg and 11.3 % ²⁶Mg. Calculate A_r (3 s.f.).", sg(ArMg, 3), "(24 × 78.6 + 25 × 10.1 + 26 × 11.3)/100 = 24.3.", 0.01, None, difficulty=2),
   M("The first ionisation energies of Be and B are 900 and 801 kJ mol⁻¹. The lower value for B is best explained by", "the 2p electron removed from B is at a higher energy and better shielded than the 2s electron removed from Be", ["B has a smaller nuclear charge than Be", "B has fewer electrons than Be", "B is a metal and Be is not"], "The 2p subshell is higher in energy and is shielded by the 2s electrons, so it is easier to remove.")],
  P("An element X in period 3 has the following successive ionisation energies (kJ mol⁻¹): 738, 1451, 7733, 10540, 13630.",
    [pn("After which ionisation energy is the big jump (give the number of the ionisation energy: 1st, 2nd…).", 2, "The jump from 1451 to 7733 occurs between the 2nd and 3rd.", 0, None, 1),
     pn("How many electrons are in the outer shell of X?", 2, "The first two electrons are removed from the outer shell; the third comes from an inner shell.", 0, None, 1),
     pm("X is", "Mg (Group 2)", ["Na (Group 1)", "Al (Group 3)", "Si (Group 4)"], "Two outer electrons and three shells: magnesium.", 1),
     po("Explain why the 3rd ionisation energy is much larger than the 2nd.", "The third electron is removed from an inner shell (n = 2) which is closer to the nucleus and less shielded, so the attraction is much stronger and much more energy is needed.", "1 mark: inner shell / closer to the nucleus; 1 mark: less shielding / stronger attraction.", 2)]),
  [M("An atom has 17 protons and 18 neutrons. Its mass number is", "35", ["17", "18", "52"], "A = 17 + 18 = 35."),
   M("The maximum number of electrons in a p subshell is", "6", ["2", "10", "3"], "Three orbitals of two electrons."),
   M("Which electron is lost first when Cu forms Cu⁺?", "a 4s electron", ["a 3d electron", "a 2p electron", "a 3s electron"], "Cu is [Ar] 3d¹⁰ 4s¹; the 4s electron goes first."),
   M("Isotopes of an element differ in", "the number of neutrons", ["the number of protons", "chemical reactions", "the number of electrons in the neutral atom"], "Same Z, different A."),
   M("The first ionisation energy is the energy needed to", "remove one electron from each gaseous atom in a mole", ["add an electron to an atom", "remove all electrons", "break a molecule"], "X(g) → X⁺(g) + e⁻ per mole.")],
  (shells_fig(), "Sodium atom: 11 protons, 12 neutrons and electrons in shells 2, 8, 1.", "A nucleus with three concentric circles holding two, eight and one electrons."),
  notes=[DATA, "Orbital shapes, spin-pair rules and Hund's rule are only mentioned; check the depth required."], minutes=35)

# ---- 1.2 periodicity
IE3 = [("Na", 496), ("Mg", 738), ("Al", 578), ("Si", 786), ("P", 1012), ("S", 1000), ("Cl", 1251), ("Ar", 1521)]
def ie_fig():
    return bars([(a, b, "blue" if a in ("Al", "S") else "orange") for a, b in IE3], unit="kJ/mol", w=420, h=250)
lesson(ch, "periodicity", "Periodic trends: radius, ionisation energy and structure",
  ["Describe and explain trends in atomic radius, first ionisation energy and electronegativity across period 3 and down a group",
   "Explain the dips in first ionisation energy at Al and S",
   "Relate melting points of the period 3 elements to their structure and bonding"],
  [("definition", "Atomic radius and shielding",
    "Across a period the nuclear charge increases while electrons are added to the same shell with similar shielding: the attraction increases and the **atomic radius decreases**. Down a group the outer electrons are in a higher shell, further from the nucleus and better shielded by the inner shells: the radius **increases** and the first ionisation energy **decreases**.\n\n"
    "**Electronegativity** (the power of an atom to attract the bonding electrons in a covalent bond) increases across a period and decreases down a group; fluorine is the most electronegative."),
   ("retenir", "First ionisation energy across period 3",
    "Generally the first ionisation energy **increases** across period 3 (greater nuclear charge, smaller radius). Two dips occur:\n\n- **Mg → Al**: the 3p electron of Al is in a higher-energy subshell than the 3s electrons and is shielded by them, so is easier to remove.\n- **P → S**: in S, the 3p⁴ configuration has a pair of electrons in one p orbital; the repulsion between them makes one easier to remove than the unpaired electrons of P (3p³)."),
   ("methode", "Structure and melting point (period 3)",
    "- **Na, Mg, Al**: metallic lattices; melting points rise (98 °C, 650 °C, 660 °C) as the ion charge (1+, 2+, 3+) and the number of delocalised electrons increase and the ions become smaller.\n- **Si**: giant covalent network: very high melting point (about 1410 °C).\n- **P₄, S₈, Cl₂**: simple molecules held by weak London forces: low melting points (S₈ > P₄ > Cl₂, as the molecules are larger).\n- **Ar**: single atoms, very low melting point."),
   ("pieges", "Common mistakes",
    "- Explaining ionisation energy trends only by 'more protons': also name the radius and shielding.\n- Saying that the shielding increases across a period: it stays about the same.\n- Explaining the melting points of molecular solids by the strength of covalent bonds: only the weak intermolecular forces are overcome.\n- Forgetting that noble gases have no bonds between atoms apart from London forces.")],
  [("Example 1 — Explaining a trend", "Explain why the first ionisation energy of Mg (738 kJ mol⁻¹) is greater than that of Na (496 kJ mol⁻¹), and why that of Al (578 kJ mol⁻¹) is lower than that of Mg.",
    ["Na → Mg: the nuclear charge increases from 11+ to 12+ while the outer electron is in the same shell (3s), so the attraction is stronger: IE increases.", "Mg → Al: Al's outer electron is a 3p electron, in a higher-energy subshell, shielded by the 3s electrons and by the filled inner shells.", "The extra distance and shielding outweigh the greater nuclear charge, so Al needs less energy: 578 < 738."], "Mg → Al dip: the 3p electron is higher in energy and shielded"),
   ("Example 2 — Predicting properties", "Predict which has the higher melting point, magnesium (2+, two delocalised electrons per atom) or sodium, and which of S₈ and Cl₂ has the higher melting point. Give reasons.",
    ["Mg²⁺ has a higher charge and is smaller than Na⁺ and contributes two electrons: stronger metallic bonding, so Mg melts higher (650 °C against 98 °C).", "S₈ and Cl₂ are simple molecules: melting needs only the London forces to be overcome.", "The larger S₈ molecule has more electrons and stronger London forces, so S₈ has the higher melting point."], "Mg > Na; S₈ > Cl₂")],
  [M("Across period 3 (Na to Cl) the atomic radius", "decreases", ["increases", "stays constant", "increases then decreases"], "Nuclear charge increases with similar shielding."),
   M("Which element has the highest first ionisation energy?", "Ar", ["Na", "Mg", "S"], "The noble gas at the end of the period has the largest value (1521 kJ/mol)."),
   TF("True or false: the first ionisation energy of potassium is lower than that of sodium.", True, "K's outer electron is in a higher shell and better shielded.")],
  [M("The first ionisation energy of S (1000) is lower than that of P (1012) because", "the paired 3p electrons in S repel each other", ["S has fewer protons than P", "S has a smaller nuclear charge", "S has a full 3p subshell"], "Electron–electron repulsion in the occupied 3p orbital makes one electron easier to remove."),
   MT("Match each element to the structure of its solid.", [("Si", "giant covalent"), ("Mg", "metallic lattice"), ("Cl₂", "simple molecules (London forces)"), ("Ar", "separate atoms (London forces)")], "Silicon forms a giant covalent network; magnesium is a metal; chlorine is Cl₂ molecules; argon is monatomic.")],
  P("The first ionisation energies (kJ mol⁻¹) of the period 3 elements Na to Ar are: 496, 738, 578, 786, 1012, 1000, 1251, 1521.",
    [pn("How many elements have a lower first ionisation energy than the element before them?", 2, "Al (578 < 738) and S (1000 < 1012).", 0, None, 1),
     pm("Which pair of elements shows the dips?", "Al and S", ["Mg and P", "Na and Cl", "Si and Cl"], "Al follows Mg and S follows P.", 1),
     po("Explain the dip at Al.", "Al's outer electron is in a 3p orbital, which is higher in energy than the 3s orbital of Mg, and it is partly shielded by the 3s electrons; so it is easier to remove despite the greater nuclear charge.", "1 mark: 3p higher energy than 3s; 1 mark: shielding by 3s electrons / further from nucleus.", 2),
     po("Explain why the first ionisation energy generally increases across the period.", "The nuclear charge increases while the electrons enter the same shell with similar shielding, so the radius decreases and the attraction on the outer electron increases.", "1 mark: increasing nuclear charge; 1 mark: same shell / similar shielding / smaller radius.", 2)]),
  [M("Down Group 1, the first ionisation energy", "decreases", ["increases", "stays the same", "is zero"], "The outer electron is further from the nucleus and more shielded."),
   M("Which has the highest melting point?", "Si", ["Cl₂", "P₄", "Ar"], "Silicon is a giant covalent network."),
   M("Electronegativity across a period", "increases", ["decreases", "stays constant", "is zero"], "Greater nuclear charge and smaller radius."),
   M("Metallic bonding is stronger in Al than Na because", "Al³⁺ is smaller and more highly charged and gives more delocalised electrons", ["Al is more electronegative", "Al has more neutrons", "Al is a gas"], "Greater charge density and more electrons in the sea."),
   M("Why do noble gases have the lowest melting points in their periods?", "only weak London forces act between single atoms", ["they have strong covalent bonds", "they are ionic", "they are metals"], "Little energy is needed to separate the atoms.")],
  (ie_fig(), "First ionisation energy across period 3 (kJ/mol): the dips are at Al and S.", "A bar chart of the first ionisation energies of sodium to argon with aluminium and sulfur lower than their predecessors."),
  notes=["The first ionisation energies are standard textbook values (Na 496, Mg 738, Al 578, Si 786, P 1012, S 1000, Cl 1251, Ar 1521 kJ/mol). Check the board's data booklet."], minutes=35, prereq=["lower6-chemistry-atoms"])

# =============================================================== 2. BONDING AND SHAPES
ch = p.chapter("ch-bonding", "Chemical bonding and the shapes of molecules", SYL % "chemical bonding")
def hbond_fig():
    it = []
    def water(ox, oy, flip=1):
        return [CIRCLE(ox, oy, 16, fill="lightblue"), T(ox, oy + 5, "O", 14, bold=True)]
    # molecule 1 on left, molecule 2 on right
    it += [CIRCLE(90, 130, 17, fill="pink"), T(90, 135, "O", 14, bold=True), CIRCLE(55, 175, 10, fill="lightyellow"), T(55, 179, "H", 11), CIRCLE(130, 100, 10, fill="lightyellow"), T(130, 104, "H", 11),
           LINE(90, 130, 58, 168, width=2), LINE(98, 120, 126, 106, width=2),
           CIRCLE(270, 80, 17, fill="pink"), T(270, 85, "O", 14, bold=True), CIRCLE(310, 115, 10, fill="lightyellow"), T(310, 119, "H", 11), CIRCLE(240, 40, 10, fill="lightyellow"), T(240, 44, "H", 11),
           LINE(270, 80, 303, 108, width=2), LINE(262, 70, 245, 48, width=2),
           LINE(140, 96, 253, 78, color="red", width=2, dash=True), T(200, 70, "hydrogen bond", 12, color="red"), T(70, 215, "δ⁻ on O, δ⁺ on H", 12, anchor="start")]
    return shapes(it, 400, 240)
lesson(ch, "bonding", "Ionic, covalent and metallic bonding; intermolecular forces",
  ["Describe ionic, covalent (including dative) and metallic bonding and the properties that follow from them",
   "Use electronegativity to predict bond polarity",
   "Explain London forces, permanent dipole forces and hydrogen bonding and their effect on boiling points"],
  [("definition", "Types of bonding",
    "**Ionic bond**: electrostatic attraction between oppositely charged ions in a giant lattice (metal + non-metal). High melting point; conducts only when molten or dissolved; brittle.\n\n"
    "**Covalent bond**: a shared pair of electrons between non-metal atoms. A **dative (coordinate) covalent bond** is one where both electrons come from the same atom (NH₄⁺, H₃O⁺). **Metallic bond**: attraction between positive ions and a sea of delocalised electrons; conducts and is malleable."),
   ("retenir", "Polarity and intermolecular forces",
    "Electronegativity differences make bonds **polar** (δ⁺ and δ⁻ charges). A molecule with polar bonds is polar only if the bond dipoles do not cancel (symmetry).\n\n"
    "- **London (dispersion) forces**: between all molecules; increase with the number of electrons (size).\n- **Permanent dipole–dipole forces**: between polar molecules.\n- **Hydrogen bonds**: strong dipole interaction between H bonded to N, O or F and a lone pair on N, O or F of a neighbouring molecule (H₂O, NH₃, HF)."),
   ("methode", "Explaining a boiling point",
    "1. State the structure (giant, or simple molecular).\n2. Name the force that must be overcome: ionic/metallic/covalent bonds in giant structures; intermolecular forces in simple molecules.\n3. Compare the **strength** of these forces: more electrons mean stronger London forces; hydrogen bonds are stronger than ordinary dipole forces.\n\nNever say covalent bonds break when a simple molecular substance boils."),
   ("pieges", "Common mistakes",
    "- Saying molten ionic compounds conduct because of electrons: they conduct by moving ions.\n- Calling a hydrogen bond a 'bond' within the molecule: it is an intermolecular force between molecules.\n- Forgetting that graphite conducts (delocalised electrons between layers) while diamond does not.\n- Thinking H₂O has a high boiling point because its O–H bonds are strong: the O–H bonds do not break.")],
  [("Example 1 — Ionic bonding", "Describe the bonding in magnesium chloride and deduce its formula.",
    ["Magnesium (2,8,2) loses two electrons to form Mg²⁺ (2,8).", "Each chlorine (2,8,7) gains one electron to form Cl⁻ (2,8,8), so two chlorine atoms are needed.", "The ions attract in a giant lattice: formula MgCl₂ (charges balance: 2+ and 2 × 1−)."], "MgCl₂: Mg²⁺ and two Cl⁻ in a giant ionic lattice"),
   ("Example 2 — Boiling points of hydrides", "The boiling points of H₂O, H₂S, H₂Se and H₂Te are about +100, −60, −41 and −2 °C. Explain why water breaks the trend.",
    ["From H₂S to H₂Te the boiling point rises because the molecules have more electrons, so London forces strengthen.", "Water has hydrogen bonds (O–H···O) as O is highly electronegative and has lone pairs: these are much stronger than the forces in H₂S.", "More energy is needed to separate the molecules, so water boils at 100 °C instead of near −80 °C (extrapolated)."], "Hydrogen bonding in water")],
  [M("Which type of bonding holds the atoms together in a solid metal?", "metallic bonding", ["ionic bonding", "hydrogen bonding", "covalent bonding between molecules"], "Positive ions are attracted to delocalised electrons."),
   N("Electronegativity (Pauling): H 2.1, Cl 3.0. Find the electronegativity difference in HCl.", 0.9, "3.0 − 2.1 = 0.9, so the H–Cl bond is polar covalent (δ⁺ on H, δ⁻ on Cl).", 0.01, None),
   TF("True or false: solid sodium chloride conducts electricity because it has delocalised electrons.", False, "The ions are fixed in the lattice and there are no free electrons; NaCl conducts only when molten or dissolved.")],
  [M("Which substance has hydrogen bonding between its molecules?", "NH₃", ["CH₄", "HCl", "CO₂"], "N is bonded to H and has a lone pair: hydrogen bonds form. HCl has only dipole forces (Cl is too large)."),
   MT("Match each substance with the strongest force broken when it melts.", [("NaCl", "ionic bonds in the lattice"), ("Ice", "hydrogen bonds"), ("Iodine, I₂", "London forces"), ("Copper", "metallic bonds")], "NaCl: ionic; ice: hydrogen bonds between H₂O; iodine: London forces; copper: metallic bonds.")],
  P("The boiling points of the Group 17 hydrides are HF +20 °C, HCl −85 °C, HBr −67 °C, HI −35 °C.",
    [pm("Which hydride has the strongest intermolecular forces?", "HF", ["HCl", "HBr", "HI"], "HF has the highest boiling point.", 1),
     pm("This is explained by", "hydrogen bonding in HF", ["stronger H–F covalent bonds breaking on boiling", "more electrons in HF", "ionic bonding in HF"], "H is bonded to the very electronegative F: hydrogen bonds form between molecules.", 1),
     po("Explain why the boiling point rises from HCl to HI.", "The molecules have more electrons from HCl to HI, so the London (dispersion) forces between them increase and more energy is needed to separate the molecules.", "1 mark: London forces; 1 mark: more electrons / larger molecules.", 2),
     pt("When HF boils, the H–F covalent bonds break.", False, "Only the intermolecular forces (hydrogen bonds) are overcome; the molecules remain intact.", 1)]),
  [M("A dative covalent bond is one where", "both shared electrons come from the same atom", ["electrons are transferred", "there is no sharing", "the atoms are metals"], "As in NH₄⁺ (N lone pair donated to H⁺)."),
   M("Diamond does not conduct electricity because", "all its outer electrons are used in localised covalent bonds", ["it is ionic", "it is a gas", "it has no electrons"], "No delocalised electrons are available."),
   M("London forces are present in", "all molecules", ["only polar molecules", "only ionic compounds", "only metals"], "Temporary dipoles occur in every atom or molecule."),
   M("Which is most likely to dissolve in water?", "ethanol (CH₃CH₂OH)", ["hexane", "iodine", "octane"], "Ethanol forms hydrogen bonds with water."),
   M("The ionic compound with the highest lattice energy among NaCl, MgO and KBr is likely", "MgO", ["NaCl", "KBr", "all equal"], "2+ and 2− small ions attract much more strongly than 1+ and 1−.")],
  (hbond_fig(), "Hydrogen bond (dashed) between two water molecules.", "Two water molecules, each with an oxygen and two hydrogen atoms, joined by a dashed red hydrogen bond between a hydrogen and the oxygen of the other."),
  notes=["Pauling electronegativities and boiling points are textbook values; check the board's data sheet.", "Lattice energy is only used qualitatively here; Born–Haber cycles are not included (check the syllabus)."], minutes=35, prereq=["lower6-chemistry-periodicity"])

# ---- 2.2 shapes (VSEPR)
def shapes_fig():
    it = []
    # CO2 linear
    it += [CIRCLE(60, 70, 13, fill="lightgreen"), T(60, 75, "C", 13, bold=True), CIRCLE(20, 70, 10, fill="pink"), T(20, 74, "O", 11), CIRCLE(100, 70, 10, fill="pink"), T(100, 74, "O", 11), LINE(30, 70, 47, 70, width=2), LINE(73, 70, 90, 70, width=2), T(60, 108, "linear 180°", 12)]
    # BF3 trigonal planar
    it += [CIRCLE(210, 60, 13, fill="lightgreen"), T(210, 65, "B", 13, bold=True)]
    for dx, dy in [(0, -45), (39, 22), (-39, 22)]:
        it += [LINE(210, 60, 210 + dx * 0.7, 60 + dy * 0.7, width=2), CIRCLE(210 + dx, 60 + dy, 9, fill="yellow"), T(210 + dx, 64 + dy, "F", 10)]
    it += [T(210, 125, "trigonal planar 120°", 12)]
    # CH4 tetrahedral (flat sketch)
    it += [CIRCLE(330, 60, 13, fill="lightgreen"), T(330, 65, "C", 13, bold=True)]
    for dx, dy in [(0, -48), (-42, 18), (42, 18), (0, 52)]:
        it += [LINE(330, 60, 330 + dx * 0.7, 60 + dy * 0.7, width=2), CIRCLE(330 + dx, 60 + dy, 8, fill="lightyellow"), T(330 + dx, 64 + dy, "H", 10)]
    it += [T(330, 142, "tetrahedral 109.5°", 12)]
    # H2O bent
    it += [CIRCLE(90, 190, 13, fill="pink"), T(90, 195, "O", 13, bold=True), LINE(90, 190, 60, 220, width=2), CIRCLE(52, 226, 8, fill="lightyellow"), T(52, 230, "H", 10),
           LINE(90, 190, 120, 220, width=2), CIRCLE(128, 226, 8, fill="lightyellow"), T(128, 230, "H", 10), T(90, 168, "..", 14), T(210, 215, "bent 104.5° (2 lone pairs)", 12)]
    return shapes(it, 420, 250)
lesson(ch, "shapes", "Shapes of molecules and ions (VSEPR)",
  ["Use the VSEPR theory to predict the shapes and bond angles of molecules and ions with up to six electron pairs",
   "Explain the effect of lone pairs on bond angles",
   "Decide whether a molecule is polar from its shape and bond polarity"],
  [("definition", "VSEPR theory",
    "In the **valence shell electron pair repulsion** theory, the pairs of electrons in the outer shell of the central atom repel each other and take positions as far apart as possible. The shape depends on the number of bonding pairs and lone pairs.\n\n"
    "Lone pairs repel more strongly than bonding pairs: **lone pair–lone pair > lone pair–bonding pair > bonding pair–bonding pair**, so they squeeze the bond angles."),
   ("retenir", "Shapes to know",
    "- 2 pairs (0 lone): **linear**, 180° (BeCl₂, CO₂).\n- 3 pairs: **trigonal planar**, 120° (BF₃, CO₃²⁻); with 1 lone pair: **bent** (SO₂, about 119°).\n- 4 pairs: **tetrahedral**, 109.5° (CH₄, NH₄⁺); 1 lone pair: **trigonal pyramidal**, about 107° (NH₃); 2 lone pairs: **bent**, about 104.5° (H₂O).\n- 5 pairs: **trigonal bipyramidal** (PCl₅, 90° and 120°).\n- 6 pairs: **octahedral**, 90° (SF₆)."),
   ("methode", "Method",
    "1. Count the outer electrons of the central atom and add or subtract for the charge of an ion.\n2. Each bonded atom shares one electron (a double bond counts as one **region** of electron density).\n3. Divide by two to find the number of electron pairs and subtract the bonding pairs to obtain the lone pairs.\n4. Name the shape from the number of bonding pairs and lone pairs. A molecule is polar only if the bond dipoles do not cancel."),
   ("pieges", "Common mistakes",
    "- Giving the shape of the electron-pair arrangement instead of the molecule: NH₃ has four pairs (tetrahedral arrangement) but its shape is trigonal pyramidal.\n- Forgetting that a double bond counts as one region.\n- Assuming every molecule with polar bonds is polar: CO₂ and CCl₄ are non-polar.\n- Quoting 109.5° for NH₃ and H₂O.")],
  [("Example 1 — Ammonia", "Predict the shape and bond angle of NH₃.",
    ["N has 5 outer electrons, and three are shared with H: 3 bonding pairs and (5 − 3) = 2 electrons = 1 lone pair.", "Four pairs, so a tetrahedral arrangement, but only three bonded atoms: trigonal pyramidal.", "The lone pair repels more strongly, so the angle is about 107°, below 109.5°."], "Trigonal pyramidal, about 107°"),
   ("Example 2 — Sulfur dioxide and carbon tetrachloride", "Predict the shape of SO₂ and say whether CCl₄ is polar.",
    ["SO₂: S has 6 outer electrons; two double bonds use 4 and leave 1 lone pair: three regions, so bent, about 119°.", "CCl₄: four bonding pairs, no lone pairs: tetrahedral. Each C–Cl bond is polar, but the dipoles cancel by symmetry.", "So CCl₄ is a non-polar molecule."], "SO₂ is bent (about 119°); CCl₄ is non-polar")],
  [M("What is the shape of the CH₄ molecule?", "tetrahedral", ["square planar", "trigonal pyramidal", "linear"], "Four bonding pairs and no lone pairs."),
   N("What is the H–C–H bond angle in methane (degrees)?", 109.5, "Four equal regions of electron density: 109.5°.", 0.1, "°"),
   TF("True or false: the water molecule is linear.", False, "It is bent (104.5°) because the oxygen has two lone pairs.")],
  [MT("Match each species to its shape.", [("CO₂", "linear"), ("BF₃", "trigonal planar"), ("NH₄⁺", "tetrahedral"), ("H₂O", "bent (V-shaped)")], "CO₂ has two regions, BF₃ three, NH₄⁺ four bonding pairs, H₂O two bonding and two lone pairs."),
   M("Why is the H–O–H angle in water (104.5°) smaller than the tetrahedral angle?", "the two lone pairs repel the bonding pairs more strongly", ["oxygen has only 6 electrons", "the O–H bonds are ionic", "hydrogen is small"], "Lone pairs occupy more space and push the bonds together.")],
  P("Consider the molecules BF₃, NH₃, PCl₅ and SF₆.",
    [pm("Which has a trigonal pyramidal shape?", "NH₃", ["BF₃", "PCl₅", "SF₆"], "NH₃ has three bonding pairs and one lone pair.", 1),
     pm("Which has bond angles of 90° only?", "SF₆", ["BF₃", "NH₃", "PCl₅"], "Octahedral: all F–S–F angles are 90° (or 180°).", 1),
     pt("BF₃ is a polar molecule.", False, "The three polar B–F bonds are arranged symmetrically (120°) so the dipoles cancel.", 1),
     po("State and explain the shape of PCl₅.", "Trigonal bipyramidal: five bonding pairs and no lone pairs around P; the pairs are as far apart as possible with angles of 90° and 120°.", "1 mark: trigonal bipyramidal; 1 mark: five bonding pairs, no lone pairs, repulsion argument.", 2)]),
  [M("SF₆ has the shape", "octahedral", ["tetrahedral", "trigonal bipyramidal", "square planar"], "Six bonding pairs, no lone pairs."),
   M("Which molecule is polar?", "NH₃", ["CO₂", "CCl₄", "BF₃"], "The dipoles do not cancel because of the pyramidal shape and lone pair."),
   M("The bond angle in BF₃ is", "120°", ["109.5°", "90°", "180°"], "Three regions: trigonal planar."),
   M("How many lone pairs are on the central atom of NH₃?", "1", ["0", "2", "3"], "N has 5 outer electrons: 3 in bonds, 2 as one lone pair."),
   M("The order of repulsion between electron pairs is", "lone–lone > lone–bonding > bonding–bonding", ["bonding–bonding > lone–bonding", "all equal", "lone–bonding > lone–lone"], "Lone pairs are closer to the nucleus and spread more.")],
  (shapes_fig(), "Shapes of CO₂ (linear), BF₃ (trigonal planar), CH₄ (tetrahedral) and H₂O (bent).", "Four molecular models: a linear molecule, a planar triangle with three outer atoms, a tetrahedral molecule and a bent water molecule."),
  notes=["Hybridisation (sp, sp², sp³) and σ/π bonds are not covered in this lesson; check the syllabus.", "The tetrahedral drawing is a flat sketch and does not show the 3D wedges."], minutes=35, prereq=["lower6-chemistry-bonding"])

# =============================================================== 3. THE MOLE AND GASES
ch = p.chapter("ch-mole", "The mole, stoichiometry and gases", SYL % "the mole, stoichiometry and gases")
Mcc = 40.0 + 12.0 + 48.0; n_cc = 5.00 / Mcc; V_co2 = n_cc * 24.0; assert Mcc == 100 and abs(n_cc - 0.05) < 1e-12 and abs(V_co2 - 1.20) < 1e-12
rFe, rO = 70.0 / 56.0, 30.0 / 16.0; assert abs(rFe - 1.25) < 1e-12 and abs(rO / rFe - 1.5) < 1e-12
n_Mg = 6.0 / 24.0; n_HCl = 0.100 * 1.0; n_H2 = n_HCl / 2; V_H2 = n_H2 * 24.0; Mg_left = (n_Mg - n_H2) * 24.0
assert n_Mg == 0.25 and abs(V_H2 - 1.20) < 1e-12 and abs(Mg_left - 4.8) < 1e-12
Mcu = 63.5 + 12 + 48; n_cu = 12.35 / Mcu; m_CuO = n_cu * (63.5 + 16); yld = 7.00 / m_CuO * 100
assert abs(Mcu - 123.5) < 1e-12 and abs(n_cu - 0.10) < 1e-12 and abs(m_CuO - 7.95) < 1e-9 and abs(yld - 88.05) < 0.05
def lim_fig():
    return bars([("Mg", 0.25, "blue"), ("HCl needed", 0.50, "orange"), ("HCl given", 0.10, "red")], unit="mol", w=380, h=240)
lesson(ch, "mole", "The mole, formulae and reacting masses",
  ["Define the mole and the Avogadro constant and interconvert mass, amount and number of particles",
   "Calculate empirical and molecular formulae and percentage composition",
   "Use balanced equations to find reacting masses, volumes of gas, limiting reagents and percentage yields"],
  [("definition", "The mole",
    "One **mole** is the amount of substance containing as many particles as there are atoms in 12 g of carbon-12: **6.02 × 10²³** (Avogadro constant, N_A). The **molar mass** M (g mol⁻¹) is numerically equal to the relative formula mass.\n\n"
    "**n = m/M**, number of particles N = n × N_A, and for a solution **n = c × V** (c in mol dm⁻³, V in dm³; 1000 cm³ = 1 dm³). For a gas at rtp: n = V/24.0 dm³ (22.4 dm³ at stp)."),
   ("methode", "Method for equation calculations",
    "1. Write the balanced equation.\n2. Convert the given quantity to **moles**.\n3. Use the mole ratio from the equation.\n4. Convert the moles of the wanted substance to mass, volume or concentration.\n\n"
    "**Limiting reagent**: compare moles with the coefficients; the one that runs out controls the yield. **Percentage yield** = (actual ÷ theoretical) × 100."),
   ("methode", "Empirical formula",
    "1. Divide each mass (or percentage) by A_r to get moles of each element.\n2. Divide all by the smallest number.\n3. If the ratios are not whole numbers (1.5, 1.33…), multiply to obtain whole numbers.\n\nThe **molecular formula** is a whole-number multiple of the empirical formula: multiple = M(molecular) ÷ M(empirical)."),
   ("pieges", "Common mistakes",
    "- Using cm³ directly in n = cV (divide by 1000 first).\n- Forgetting the coefficients in the equation (2HCl per Mg).\n- Rounding ratios such as 1.5 to 1 or 2: multiply by 2 instead.\n- Using the mass of the excess reagent to calculate the yield.\n- Not using 24.0 dm³ for gases at room temperature and 22.4 dm³ at stp consistently.")],
  [("Example 1 — Volume of gas", "Calculate the volume of carbon dioxide (at rtp) from 5.00 g of calcium carbonate with excess dilute hydrochloric acid. CaCO₃ + 2HCl → CaCl₂ + H₂O + CO₂.",
    ["M(CaCO₃) = 40.0 + 12.0 + 3 × 16.0 = 100.0 g mol⁻¹, so n = 5.00/100.0 = 0.0500 mol.", "The ratio CaCO₃ : CO₂ is 1 : 1, so n(CO₂) = 0.0500 mol.", "V = 0.0500 × 24.0 = 1.20 dm³."], "1.20 dm³ of CO₂"),
   ("Example 2 — Empirical formula", "An oxide of iron contains 70.0 % Fe and 30.0 % O by mass. Find its empirical formula.",
    ["Moles per 100 g: Fe 70.0/56.0 = 1.25; O 30.0/16.0 = 1.875.", "Divide by 1.25: Fe 1.00 : O 1.50.", "Multiply by 2 for whole numbers: Fe 2 : O 3, so the empirical formula is Fe₂O₃."], "Fe₂O₃")],
  [N("How many moles are in 18.0 g of water (M = 18.0 g/mol)?", 1.0, "n = 18.0/18.0 = 1.00 mol.", 0.005, "mol"),
   N("Calculate the concentration (mol dm⁻³) of a solution containing 4.00 g of NaOH (M = 40.0) in 250 cm³.", 0.400, "n = 4.00/40.0 = 0.100 mol; c = 0.100/0.250 = 0.400 mol dm⁻³.", 0.002, "mol/dm³"),
   TF("True or false: 1 mole of oxygen gas molecules (O₂) contains 6.02 × 10²³ atoms.", False, "It contains 6.02 × 10²³ molecules, i.e. 1.20 × 10²⁴ atoms.")],
  [N("6.0 g of magnesium is added to 100 cm³ of 1.0 mol dm⁻³ HCl: Mg + 2HCl → MgCl₂ + H₂. Find the volume of hydrogen at rtp (dm³, 3 s.f.).", sg(V_H2, 3), "Mg: 0.25 mol (excess). HCl: 0.100 mol is limiting; n(H₂) = 0.100/2 = 0.0500 mol; V = 1.20 dm³.", 0.01, "dm³", difficulty=2),
   N("In an experiment 6.4 g of product is obtained when the theoretical yield is 8.0 g. Find the percentage yield.", 80.0, "6.4/8.0 × 100 = 80 %.", 0.1, "%")],
  P("Copper(II) carbonate decomposes on heating: CuCO₃ → CuO + CO₂. 12.35 g of CuCO₃ is heated (A_r: Cu 63.5, C 12.0, O 16.0; molar volume 24.0 dm³).",
    [pn("Moles of CuCO₃ (3 s.f.).", sg(n_cu, 3), "M(CuCO₃) = 123.5; n = 12.35/123.5 = 0.100 mol.", 0.001, "mol", 1),
     pn("Volume of CO₂ released at rtp (dm³, 3 s.f.).", sg(n_cu * 24.0, 3), "n(CO₂) = 0.100 mol; V = 0.100 × 24.0 = 2.40 dm³.", 0.01, "dm³", 1),
     pn("Theoretical mass of CuO (g, 3 s.f.).", sg(m_CuO, 3), "0.100 × 79.5 = 7.95 g.", 0.01, "g", 1),
     pn("Percentage yield if 7.00 g of CuO is obtained (%, 3 s.f.).", sg(yld, 3), "7.00/7.95 × 100 = 88.1 %.", 0.2, "%", 1)], figure=lim_fig()),
  [M("The number of molecules in 0.50 mol of CO₂ is", "3.01 × 10²³", ["6.02 × 10²³", "1.20 × 10²⁴", "3.01 × 10²²"], "0.50 × 6.02 × 10²³."),
   M("A compound contains C 40.0 %, H 6.7 %, O 53.3 %. Its empirical formula is", "CH₂O", ["CHO", "C₂H₄O", "CH₄O"], "C 3.33, H 6.7, O 3.33: ratio 1 : 2 : 1."),
   M("What volume does 0.25 mol of gas occupy at rtp (24.0 dm³/mol)?", "6.0 dm³", ["0.25 dm³", "96 dm³", "60 dm³"], "0.25 × 24.0 = 6.0 dm³."),
   M("In 2H₂ + O₂ → 2H₂O, the amount of O₂ needed for 0.40 mol of H₂ is", "0.20 mol", ["0.80 mol", "0.40 mol", "0.10 mol"], "Mole ratio H₂ : O₂ = 2 : 1."),
   M("The limiting reagent is the reactant that", "is completely used up first", ["is present in greatest mass", "is a gas", "has the largest M"], "It limits the amount of product.")],
  (lim_fig(), "Mg and HCl in Example: 0.25 mol Mg needs 0.50 mol HCl but only 0.10 mol is supplied: HCl is limiting.", "A bar chart with three bars: 0.25 mole of magnesium, 0.50 mole of hydrochloric acid needed and 0.10 mole of acid given."),
  notes=[DATA], minutes=35, prereq=["lower6-chemistry-atoms"])

# ---- 3.2 gases
n_g = 1.0e5 * 2.5e-4 / (R_ * 300); M_g = 0.320 / n_g; assert abs(n_g - 0.010028) < 1e-5 and abs(M_g - 31.9) < 0.1
pN, pO = 2.0 / 2.5 * 100, 0.5 / 2.5 * 100; assert pN == 80 and pO == 20
V2g = 1.0 * 300 / 300 * 1.0
n_air = 101e3 * 24.0e-3 / (R_ * 298); assert abs(n_air - 0.9788) < 0.01   # check the 24 dm3 value
def charles_fig():
    return plot(-300, 100, 0, 1.5, curves=[("(x+273)/273", "blue", "V")], points=[(-273, 0, "−273 °C", "red")], grid=100, xlabel="θ / °C", ylabel="V (relative)", w=400, h=240)
lesson(ch, "gases", "Gases: gas laws and the ideal gas equation",
  ["State and use Boyle's, Charles' and the pressure laws and the combined gas law",
   "Use pV = nRT to find amounts, volumes, pressures and molar masses",
   "Use gas volumes in reactions (Avogadro) and Dalton's law of partial pressures"],
  [("formule", "Gas laws and the ideal gas equation",
    "For a fixed mass of gas: **Boyle** (T constant) pV = constant; **Charles** (p constant) V/T = constant; **pressure law** (V constant) p/T = constant, with T in kelvin (T = θ + 273). Combined: p₁V₁/T₁ = p₂V₂/T₂.\n\n"
    "**pV = nRT** (SI units: p in Pa, V in m³, T in K, R = 8.31 J mol⁻¹ K⁻¹; 1 dm³ = 10⁻³ m³, 1 atm ≈ 1.01 × 10⁵ Pa).",
    r"pV=nRT\qquad \frac{p_1V_1}{T_1}=\frac{p_2V_2}{T_2}"),
   ("definition", "Avogadro and partial pressures",
    "**Avogadro's law**: equal volumes of gases at the same temperature and pressure contain equal numbers of molecules, so gas volumes in an equation are in the mole ratio (at the same T and p). Molar volume: 24.0 dm³ at rtp, 22.4 dm³ at stp.\n\n"
    "**Dalton's law**: the total pressure of a gas mixture is the sum of the **partial pressures**: p_A = (mole fraction of A) × p_total."),
   ("retenir", "Ideal and real gases",
    "An ideal gas has no intermolecular forces and molecules of negligible volume. Real gases behave nearly ideally at **high temperature and low pressure**; at high pressure and low temperature the molecules' volume and attractions cause deviations (gases liquefy). Gas density = M p/RT."),
   ("pieges", "Common mistakes",
    "- Using °C in a gas-law calculation: always use kelvin.\n- Mixing units: cm³ or dm³ with R = 8.31 needs m³ and Pa.\n- Using the molar volume 24.0 dm³ for stp conditions (22.4 dm³).\n- Using the mass fraction instead of the mole fraction in Dalton's law.")],
  [("Example 1 — Molar mass of a gas", "A sample of gas of mass 0.320 g occupies 250 cm³ at 100 kPa and 27 °C. Find its molar mass.",
    ["T = 300 K, V = 2.50 × 10⁻⁴ m³, p = 1.00 × 10⁵ Pa.", "n = pV/RT = 1.00 × 10⁵ × 2.50 × 10⁻⁴ ÷ (8.31 × 300) = 0.01003 mol.", "M = m/n = 0.320/0.01003 = 31.9 g mol⁻¹: the gas is probably O₂ (32)."], "M ≈ 32 g/mol (probably O₂)"),
   ("Example 2 — Volumes of gases", "10 cm³ of a gaseous hydrocarbon is burnt in 60 cm³ of oxygen (excess). After cooling, the volume is 45 cm³, and it falls to 25 cm³ after shaking with potassium hydroxide solution (which absorbs CO₂). Find the formula.",
    ["CO₂ formed = 45 − 25 = 20 cm³, so each molecule contains 2 C (20/10).", "O₂ used = 60 − 25 = 35 cm³ = 10 (x + y/4) with x = 2: y/4 = 1.5, y = 6.", "The formula is C₂H₆ (ethane); water is liquid at room temperature and takes no volume."], "C₂H₆")],
  [N("A gas occupies 2.0 dm³ at 100 kPa. At constant temperature the pressure is raised to 250 kPa. Find the new volume (dm³).", 0.80, "Boyle: V₂ = 2.0 × 100/250 = 0.80 dm³.", 0.005, "dm³"),
   N("Find the amount of gas (mol) in a 5.0 dm³ container at 2.0 × 10⁵ Pa and 27 °C (3 s.f.; R = 8.31).", sg(2.0e5 * 5.0e-3 / (R_ * 300), 3), "n = pV/RT = 2.0 × 10⁵ × 5.0 × 10⁻³ ÷ (8.31 × 300) = 0.401 mol.", 0.003, "mol"),
   TF("True or false: a gas is most likely to behave ideally at high pressure and low temperature.", False, "It is most ideal at low pressure and high temperature, when molecular volume and forces are negligible.")],
  [N("A mixture of 2.0 mol N₂ and 0.50 mol O₂ has a total pressure of 100 kPa. Find the partial pressure of N₂ (kPa).", pN, "Mole fraction = 2.0/2.5 = 0.80, so p = 0.80 × 100 = 80 kPa.", 0.1, "kPa", difficulty=2),
   N("A gas of volume 3.0 dm³ at 27 °C is heated at constant pressure to 127 °C. Find the new volume (dm³, 3 s.f.).", sg(3.0 * 400 / 300, 3), "Charles: V₂ = 3.0 × 400/300 = 4.00 dm³.", 0.02, "dm³")],
  P("A cylinder of volume 10.0 dm³ contains an ideal gas at 3.0 × 10⁵ Pa and 27 °C (R = 8.31 J mol⁻¹ K⁻¹).",
    [pn("Amount of gas (mol, 3 s.f.).", sg(3.0e5 * 10.0e-3 / (R_ * 300), 3), "n = pV/RT = 3.0 × 10⁵ × 1.00 × 10⁻² ÷ (8.31 × 300) = 1.20 mol.", 0.01, "mol", 1),
     pn("Pressure (10⁵ Pa, 3 s.f.) when the cylinder is heated to 127 °C (volume constant).", sg(3.0 * 400 / 300, 3), "p₂ = p₁T₂/T₁ = 3.0 × 10⁵ × 400/300 = 4.00 × 10⁵ Pa.", 0.02, "× 10⁵ Pa", 2),
     pm("The pressure rises because", "the molecules move faster and hit the walls harder and more often", ["the molecules become larger", "there are more molecules", "the gas becomes denser"], "Higher T means a higher mean kinetic energy.", 1)]),
  [M("At the same temperature and pressure, 1 dm³ of H₂ and 1 dm³ of CO₂ contain", "the same number of molecules", ["the same mass", "the same number of atoms", "different numbers of molecules"], "Avogadro's law."),
   M("−273 °C is called", "absolute zero (0 K)", ["boiling point of water", "zero pressure", "standard temperature"], "The temperature at which the ideal gas volume would be zero."),
   M("Which units must be used for p, V in pV = nRT with R = 8.31?", "Pa and m³", ["atm and dm³", "kPa and cm³", "Pa and dm³"], "SI units."),
   M("The partial pressure of a gas in a mixture is", "mole fraction × total pressure", ["mass fraction × total pressure", "total pressure ÷ number of gases", "the same for every gas"], "Dalton's law."),
   M("100 cm³ of H₂ reacts with exactly what volume of O₂ (same T, p) in 2H₂ + O₂ → 2H₂O(g)?", "50 cm³", ["100 cm³", "200 cm³", "25 cm³"], "Volume ratio 2 : 1.")],
  (charles_fig(), "Charles' law: the volume of a gas at constant pressure extrapolates to zero at −273 °C.", "A straight line of relative gas volume against temperature in degrees Celsius meeting the axis at minus 273."),
  notes=[DATA, "Molar volumes 24.0 dm³ (rtp) and 22.4 dm³ (stp) both used; the board may prefer one (check).", "Kinetic theory of gases is treated in the physics pack."], minutes=35, prereq=["lower6-chemistry-mole"])

# =============================================================== 4. ENERGETICS, KINETICS, EQUILIBRIUM
ch = p.chapter("ch-energetics", "Energetics, kinetics and equilibrium", SYL % "energetics, kinetics and chemical equilibrium")
c_w = 4.18
q1 = 100 * c_w * 6.8; n1 = 0.0500; dH1 = -q1 / n1 / 1000; assert abs(q1 - 2842.4) < 0.1 and abs(dH1 + 56.85) < 0.01
q2 = 200 * c_w * 13.0; n2 = 0.46 / 46.0; dH2 = -q2 / n2 / 1000; assert abs(q2 - 10868) < 0.1 and abs(dH2 + 1086.8) < 0.1
lesson(ch, "enthalpy", "Enthalpy changes and calorimetry",
  ["Define exothermic and endothermic reactions and standard enthalpy changes of reaction, formation, combustion and neutralisation",
   "Draw and interpret enthalpy profile diagrams including the activation energy",
   "Use q = mcΔT to calculate enthalpy changes from experimental data"],
  [("definition", "Enthalpy change",
    "The **enthalpy change** ΔH is the heat exchanged with the surroundings at constant pressure. **Exothermic**: heat released, temperature of surroundings rises, ΔH negative. **Endothermic**: heat absorbed, ΔH positive.\n\n"
    "Standard conditions: 298 K (25 °C) and 100 kPa (1 atm is often used), substances in their standard states; the unit is kJ mol⁻¹."),
   ("retenir", "Standard enthalpy changes",
    "- **ΔH_f°** (formation): one mole of a compound formed from its elements in their standard states; ΔH_f° of an element is zero.\n- **ΔH_c°** (combustion): one mole of a substance burnt completely in oxygen.\n- **ΔH_neut°** (neutralisation): one mole of water formed from an acid and an alkali (about −57 kJ mol⁻¹ for strong acid and strong base).\n- **ΔH_r°**: for the reaction as written, with the molar quantities in the equation."),
   ("formule", "Calorimetry",
    "Heat absorbed by the water (or solution): **q = mcΔT** with m in g, c = 4.18 J g⁻¹ K⁻¹ and ΔT in K. Then **ΔH = −q/n** (in J mol⁻¹, convert to kJ), the sign being negative if the temperature rises.\n\nExperimental values are often less exothermic than data-book values because of **heat losses** and incomplete combustion; a lid, lagging and stirring reduce errors.",
    r"q=mc\Delta T\qquad \Delta H=-\frac{q}{n}"),
   ("pieges", "Common mistakes",
    "- Forgetting to convert q from joules to kilojoules before dividing by n.\n- Giving ΔH a positive sign when the temperature rises (exothermic = negative).\n- Using the mass of the reagents instead of the mass of **solution** (water) in q = mcΔT.\n- Mixing up the activation energy (the barrier) and ΔH (the overall energy change).")],
  [("Example 1 — Neutralisation", "50.0 cm³ of 1.00 mol dm⁻³ HCl and 50.0 cm³ of 1.00 mol dm⁻³ NaOH are mixed in a polystyrene cup: the temperature rises by 6.8 K. Find ΔH_neut (assume density 1.00 g cm⁻³, c = 4.18 J g⁻¹ K⁻¹).",
    ["Mass of solution = 100 g; q = 100 × 4.18 × 6.8 = 2842 J = 2.84 kJ.", "n(water) = 1.00 × 0.0500 = 0.0500 mol (equal amounts, no excess).", "ΔH = −2.84/0.0500 = −56.8 kJ mol⁻¹."], "ΔH = −56.8 kJ mol⁻¹"),
   ("Example 2 — Burning ethanol", "0.46 g of ethanol (M = 46.0) is burnt and heats 200 g of water by 13.0 K. Estimate ΔH_c and comment.",
    ["q = 200 × 4.18 × 13.0 = 10 868 J = 10.9 kJ; n = 0.46/46.0 = 0.0100 mol.", "ΔH = −10.9/0.0100 = −1087 kJ mol⁻¹.", "The data-book value is about −1367 kJ mol⁻¹: the experiment is less exothermic because of heat lost to the surroundings and incomplete combustion."], "−1087 kJ mol⁻¹ (heat losses explain the difference)")],
  [N("200 g of water is heated by 15 K. Calculate q in kJ (3 s.f.; c = 4.18 J g⁻¹ K⁻¹).", sg(200 * 4.18 * 15 / 1000, 3), "q = 200 × 4.18 × 15 = 12 540 J = 12.5 kJ.", 0.05, "kJ"),
   M("For an exothermic reaction, the products have", "less enthalpy than the reactants", ["more enthalpy than the reactants", "the same enthalpy as the reactants", "a higher activation energy"], "Heat is released: ΔH is negative."),
   TF("True or false: the standard enthalpy of formation of oxygen gas, O₂(g), is zero.", True, "Elements in their standard states have ΔH_f° = 0 by definition.")],
  [N("In an experiment, 0.0200 mol of a solid dissolves in 100 g of water and the temperature falls by 3.0 K. Calculate ΔH_solution in kJ mol⁻¹ (3 s.f.).", sg(100 * 4.18 * 3.0 / 1000 / 0.0200, 3), "q = 100 × 4.18 × 3.0 = 1254 J = 1.254 kJ; ΔH = +1.254/0.0200 = +62.7 kJ mol⁻¹ (endothermic: the temperature fell).", 0.2, "kJ/mol", difficulty=2),
   M("A catalyst increases the rate of reaction by", "providing a route with a lower activation energy", ["increasing ΔH", "increasing the temperature", "changing the products"], "ΔH is unchanged; the activation energy is lowered.")],
  P("In an experiment, 0.50 g of methanol (M = 32.0) is burnt in a spirit burner and heats 150 g of water from 20.0 °C to 34.5 °C.",
    [pn("Temperature rise (K).", 14.5, "34.5 − 20.0 = 14.5 K.", 0.01, "K", 1),
     pn("Heat absorbed by the water (kJ, 3 s.f.).", sg(150 * 4.18 * 14.5 / 1000, 3), "q = 150 × 4.18 × 14.5 = 9091 J = 9.09 kJ.", 0.02, "kJ", 1),
     pn("Enthalpy of combustion of methanol from these data (kJ mol⁻¹, 3 s.f.).", sg(-150 * 4.18 * 14.5 / 1000 / (0.50 / 32.0), 3), "n = 0.50/32.0 = 0.01563 mol; ΔH = −9.09/0.01563 = −582 kJ mol⁻¹.", 2, "kJ/mol", 2),
     po("The data-book value is about −726 kJ mol⁻¹. Suggest two reasons for the difference.", "Heat is lost to the surroundings (the apparatus, the air) and the combustion may be incomplete (soot); some methanol may evaporate; the water is not insulated.", "1 mark each for any two valid reasons: heat loss, incomplete combustion, evaporation, no lid, etc.", 2)]),
  [M("Which sign has ΔH for an endothermic reaction?", "positive", ["negative", "zero", "it depends on the catalyst"], "Heat is absorbed from the surroundings."),
   M("The standard enthalpy of combustion is defined for", "one mole of substance burnt completely in oxygen", ["one mole of oxygen", "one gram of fuel", "burning in air only"], "The amount in the definition is 1 mol of the fuel."),
   M("The temperature of the surroundings rises. The reaction is", "exothermic", ["endothermic", "at equilibrium", "a catalyst"], "Heat flows to the surroundings."),
   M("The activation energy is", "the minimum energy colliding particles need to react", ["the energy released by the reaction", "ΔH", "the energy of the products"], "It is the barrier on the enthalpy profile."),
   M("To reduce errors in a neutralisation experiment you should", "use a lid and lagging and stir", ["use a metal beaker open to the air", "heat the solution", "use less acid"], "These reduce heat loss.")],
  (energy_profile("Ea", "reactants", "products", exo=True), "Enthalpy profile of an exothermic reaction: Ea is the activation energy and ΔH is negative.", "A curve rising from the reactants line to a peak and falling to a lower products line, with arrows for activation energy and enthalpy change."),
  notes=[DATA, "Standard pressure: 100 kPa (IUPAC) vs 1 atm; check which the board uses.", "Data-book values quoted (ethanol −1367, methanol −726 kJ mol⁻¹) are textbook values; check them."], minutes=35, prereq=["lower6-chemistry-mole"])

# ---- 4.2 Hess's law and bond enthalpies
dHf_CH4 = -393.5 + 2 * (-285.8) - (-890.3); assert abs(dHf_CH4 + 74.8) < 1e-9
dHc_EtOH = 2 * (-393.5) + 3 * (-285.8) - (-277.7); assert abs(dHc_EtOH + 1366.7) < 1e-9
brk = 4 * 413 + 2 * 498; frm = 2 * 805 + 4 * 464; dHb = brk - frm; assert brk == 2648 and frm == 3466 and dHb == -818
dH_HCl = 436 + 243 - 2 * 432; assert dH_HCl == -185
def hess_fig():
    it = [T(210, 22, "C(s) + 2H₂(g) + 2O₂(g)", 13, bold=True), T(70, 205, "CH₄(g) + 2O₂(g)", 13, bold=True), T(350, 205, "CO₂(g) + 2H₂O(l)", 13, bold=True),
          LINE(170, 34, 90, 186, color="blue", width=3, arrow="end"), T(110, 100, "ΔH_f(CH₄)", 12, anchor="end", color="blue"),
          LINE(90, 205, 255, 205, color="red", width=3, arrow="end"), T(172, 232, "ΔH_c(CH₄)", 12, color="red"),
          LINE(250, 34, 340, 186, color="green", width=3, arrow="end"), T(310, 100, "ΔH_c(C) + 2ΔH_c(H₂)", 12, anchor="start", color="green")]
    return shapes(it, 440, 245)
lesson(ch, "hess", "Hess's law and bond enthalpies",
  ["State Hess's law and use enthalpy cycles with enthalpies of formation and combustion",
   "Define bond enthalpy and use mean bond enthalpies to estimate ΔH of gaseous reactions",
   "Explain why bond-enthalpy values are only estimates"],
  [("definition", "Hess's law",
    "**Hess's law**: the total enthalpy change of a reaction is the same whichever route is taken, provided the initial and final states are the same (a consequence of the conservation of energy). It is used to find enthalpy changes that cannot be measured directly."),
   ("formule", "Using enthalpies of formation and combustion",
    "- **ΔH_r = ΣΔH_f(products) − ΣΔH_f(reactants)** (formation route: via the elements).\n- **ΔH_r = ΣΔH_c(reactants) − ΣΔH_c(products)** (combustion route: via the oxidation products).\n\nMultiply each ΔH by the coefficient in the equation and take care with the signs.",
    r"\Delta H_r=\sum\Delta H_f(\text{products})-\sum\Delta H_f(\text{reactants})"),
   ("formule", "Bond enthalpies",
    "The **bond enthalpy** is the energy needed to break one mole of a covalent bond in the gaseous state (kJ mol⁻¹); **mean** bond enthalpies are averaged over several compounds. Breaking bonds is endothermic, making bonds is exothermic:\n\n**ΔH ≈ Σ(bonds broken) − Σ(bonds made)**. Typical values (kJ mol⁻¹): C–H 413, C–C 347, C=C 614, O=O 498, C=O (in CO₂) 805, O–H 464, H–H 436, Cl–Cl 243, H–Cl 432.",
    r"\Delta H\approx\sum E(\text{broken})-\sum E(\text{formed})"),
   ("pieges", "Common mistakes",
    "- Reversing the sign of the sum 'broken − formed'.\n- Using mean bond enthalpies for liquids: they apply to gaseous species (and give only estimates).\n- Forgetting the coefficient (4 C–H bonds in CH₄, 2 O=O).\n- Using ΔH_f of an element (zero) incorrectly, or ΔH_f of H₂O(l) when the water formed is a gas.")],
  [("Example 1 — Enthalpy of formation from combustion data", "Use ΔH_c: C(s) = −393.5, H₂(g) = −285.8, CH₄(g) = −890.3 kJ mol⁻¹ to find ΔH_f of methane: C(s) + 2H₂(g) → CH₄(g).",
    ["Combustion route: ΔH_f = ΣΔH_c(reactants) − ΣΔH_c(products).", "= [−393.5 + 2(−285.8)] − (−890.3) = −965.1 + 890.3.", "= −74.8 kJ mol⁻¹."], "ΔH_f(CH₄) = −74.8 kJ mol⁻¹"),
   ("Example 2 — Using bond enthalpies", "Estimate ΔH for CH₄ + 2O₂ → CO₂ + 2H₂O (all gases) using mean bond enthalpies (C–H 413, O=O 498, C=O 805, O–H 464 kJ mol⁻¹).",
    ["Bonds broken: 4 C–H + 2 O=O = 4(413) + 2(498) = 1652 + 996 = 2648 kJ.", "Bonds made: 2 C=O + 4 O–H = 2(805) + 4(464) = 1610 + 1856 = 3466 kJ.", "ΔH = 2648 − 3466 = −818 kJ mol⁻¹ (the measured value for gaseous water is about −802 kJ)."], "ΔH ≈ −818 kJ mol⁻¹")],
  [N("Calculate ΔH for H₂ + Cl₂ → 2HCl from the bond enthalpies H–H 436, Cl–Cl 243, H–Cl 432 kJ mol⁻¹ (kJ).", dH_HCl, "Broken: 436 + 243 = 679; made: 2 × 432 = 864; ΔH = 679 − 864 = −185 kJ.", 0.5, "kJ"),
   TF("True or false: ΔH_f° of liquid water is exactly the enthalpy change for the combustion of hydrogen gas, H₂(g) + ½O₂(g) → H₂O(l).", True, "Both describe the same reaction (formation of 1 mol H₂O(l) from its elements).") ,
   M("Hess's law is a consequence of", "conservation of energy", ["conservation of mass only", "the law of equilibrium", "Le Chatelier's principle"], "The total energy change cannot depend on the route.")],
  [N("Use ΔH_f° (kJ mol⁻¹): C₂H₅OH(l) −277.7, CO₂(g) −393.5, H₂O(l) −285.8 to find ΔH_c of ethanol (kJ mol⁻¹, 4 s.f.).", dHc_EtOH, "C₂H₅OH + 3O₂ → 2CO₂ + 3H₂O: ΔH = 2(−393.5) + 3(−285.8) − (−277.7) = −1366.7 kJ mol⁻¹.", 0.2, "kJ/mol", difficulty=2),
   N("A reaction breaks bonds totalling 2000 kJ and forms bonds totalling 2350 kJ. Find ΔH (kJ).", -350, "ΔH = broken − made = 2000 − 2350 = −350 kJ (exothermic).", 0.5, "kJ")],
  P("Use ΔH_c (kJ mol⁻¹): C(s) −393.5; H₂(g) −285.8; CH₄(g) −890.3.",
    [pn("ΔH for C(s) + 2H₂(g) → CH₄(g) (kJ mol⁻¹, 3 s.f.).", sg(dHf_CH4, 3), "ΔH = −393.5 + 2(−285.8) − (−890.3) = −74.8 kJ mol⁻¹.", 0.1, "kJ/mol", 2),
     pm("This value is", "the standard enthalpy of formation of methane", ["the enthalpy of combustion of methane", "the bond enthalpy of C–H", "positive"], "It forms 1 mol of CH₄ from its elements in standard states.", 1),
     pn("The mean C–H bond enthalpy is 413 kJ mol⁻¹. Find the energy needed to break all the C–H bonds in 1 mol of CH₄ (kJ).", 1652, "CH₄ has four C–H bonds: 4 × 413 = 1652 kJ.", 0.5, "kJ", 1),
     po("Why do bond-enthalpy calculations give only approximate values?", "Bond enthalpies are averages over many compounds, so the actual bond energies in a particular molecule differ; the data apply to gaseous species only.", "1 mark: mean/average values; 1 mark: gaseous states / differences in environment.", 2)], figure=hess_fig()),
  [M("ΔH_f° of an element in its standard state is", "zero", ["negative", "positive", "equal to ΔH_c"], "By definition."),
   M("Bond breaking is", "endothermic", ["exothermic", "neither", "always zero"], "Energy must be supplied."),
   M("Hess's law allows us to find ΔH values that", "cannot be measured directly", ["are always positive", "depend on the route", "are zero"], "Using known values and a cycle."),
   M("If the reverse of a reaction is considered, ΔH", "changes sign", ["stays the same", "doubles", "becomes zero"], "ΔH(reverse) = −ΔH(forward)."),
   M("If an equation is multiplied by 2, ΔH is", "multiplied by 2", ["unchanged", "halved", "negated"], "ΔH is for the amounts in the equation.")],
  (hess_fig(), "Enthalpy cycle for the formation of methane from combustion data.", "A triangular cycle with the elements at the top, methane and oxygen at the lower left, carbon dioxide and water at the lower right, joined by labelled arrows."),
  notes=["Mean bond enthalpies and ΔH_c/ΔH_f values are standard textbook values (to be checked against the board's data booklet).", "Born–Haber cycles and lattice enthalpies are not covered."], minutes=35, prereq=["lower6-chemistry-enthalpy"])

# ---- 4.3 kinetics
k_ex = 2.0e-3 / (0.10 * 0.10 ** 2); assert abs(k_ex - 2.0) < 1e-9
ratio_T = math.exp(50000 / R_ * (1 / 300 - 1 / 310)); assert abs(ratio_T - 1.91) < 0.01
t_half = math.log(2) / 0.0231; assert abs(t_half - 30.0) < 0.05
Ea_from = R_ * math.log(3.0) / (1 / 300 - 1 / 320) / 1000; assert abs(Ea_from - 43.9) < 0.1, Ea_from
def mb_fig():
    return plot(0, 8, 0, 0.4, curves=[("x*exp(-x)", "blue", "T₁ (lower)"), ("x*exp(-x/1.6)/2.56", "red", "T₂ (higher)")], segments=[(3.5, 0, 3.5, 0.4, "green", True, "Ea")],
                grid=2, xlabel="energy", ylabel="fraction of molecules", w=400, h=250)
lesson(ch, "kinetics", "Rates of reaction and rate equations",
  ["Explain rates of reaction using collision theory and the Maxwell–Boltzmann distribution",
   "Explain the effects of concentration, temperature, surface area and catalysts",
   "Use initial-rate data to find orders, write the rate equation and calculate the rate constant"],
  [("definition", "Rate and collision theory",
    "The **rate of reaction** is the change in concentration of a reactant or product per unit time (mol dm⁻³ s⁻¹). For a reaction to occur particles must **collide** with energy at least the **activation energy** E_a and with the correct orientation.\n\n"
    "Increasing the concentration (or pressure of a gas, or surface area of a solid) increases the frequency of collisions. Increasing the temperature raises the fraction of molecules with E ≥ E_a (the area under the Maxwell–Boltzmann curve beyond E_a) and also the collision frequency, but the first effect dominates."),
   ("retenir", "Catalysts",
    "A **catalyst** increases the rate by providing an alternative reaction route of **lower activation energy**; it is not used up and does not change ΔH or the equilibrium position. **Heterogeneous** catalysts (e.g. iron in the Haber process, a solid with gaseous reactants) work at their surface; **homogeneous** catalysts are in the same phase as the reactants. Enzymes are biological catalysts."),
   ("formule", "Rate equation",
    "For a reaction A + B → products, **rate = k[A]^m[B]^n**: m and n are the **orders** (found by experiment, not from the equation), k is the **rate constant** (its units depend on the overall order). **Initial rates**: doubling [A] and doubling the rate means first order in A; a four-fold change in rate means second order.\n\nFor a **first-order** reaction the half-life is constant: **t½ = 0.693/k**. The rate constant increases with temperature: k = A e^(−E_a/RT).",
    r"\text{rate}=k[A]^{m}[B]^{n}\qquad t_{1/2}=\frac{\ln 2}{k}"),
   ("pieges", "Common mistakes",
    "- Reading the orders from the coefficients of the balanced equation.\n- Saying a catalyst changes ΔH or gives a higher yield at equilibrium.\n- Saying that a higher temperature works because 'molecules collide more': the main effect is the larger fraction with E ≥ E_a.\n- Forgetting the units of k (mol⁻¹ dm³ s⁻¹ for second order overall).")],
  [("Example 1 — Orders from initial rates", "For A + B → C the initial rates were: [A] 0.10, [B] 0.10 → 2.0 × 10⁻³; [A] 0.20, [B] 0.10 → 4.0 × 10⁻³; [A] 0.10, [B] 0.20 → 8.0 × 10⁻³ (mol dm⁻³, mol dm⁻³ s⁻¹). Find the rate equation and k.",
    ["Doubling [A] doubles the rate: first order in A. Doubling [B] multiplies the rate by 4: second order in B.", "Rate = k[A][B]².", "k = rate/([A][B]²) = 2.0 × 10⁻³ ÷ (0.10 × 0.10²) = 2.0 dm⁶ mol⁻² s⁻¹."], "rate = k[A][B]², k = 2.0 dm⁶ mol⁻² s⁻¹"),
   ("Example 2 — Effect of temperature", "A reaction has E_a = 50 kJ mol⁻¹. By what factor does the rate constant increase when the temperature is raised from 300 K to 310 K? (R = 8.31)",
    ["ln(k₂/k₁) = (E_a/R)(1/T₁ − 1/T₂) = (50 000/8.31)(1/300 − 1/310).", "1/300 − 1/310 = 1.075 × 10⁻⁴, so ln(k₂/k₁) = 6017 × 1.075 × 10⁻⁴ = 0.647.", "k₂/k₁ = e^0.647 = 1.91: the rate roughly doubles for a 10 K rise."], "k increases by a factor of about 1.9")],
  [M("Which change does NOT increase the rate of a reaction between a metal and acid?", "diluting the acid with water", ["using powdered metal instead of lumps", "increasing the temperature", "using a more concentrated acid"], "Dilution lowers the concentration and so the collision frequency."),
   N("A first-order reaction has k = 0.0231 s⁻¹. Find the half-life (s, 3 s.f.).", sg(t_half, 3), "t½ = 0.693/0.0231 = 30.0 s.", 0.1, "s"),
   TF("True or false: a catalyst changes the enthalpy change ΔH of a reaction.", False, "A catalyst lowers E_a for both directions but ΔH is unchanged.")],
  [N("The rate of a reaction is first order in A. The initial rate is 4.0 × 10⁻⁴ mol dm⁻³ s⁻¹ when [A] = 0.20 mol dm⁻³. Find k (s⁻¹).", 4.0e-4 / 0.20 * 1e3, "k = rate/[A] = 4.0 × 10⁻⁴/0.20 = 2.0 × 10⁻³ s⁻¹, i.e. 2.0 in units of 10⁻³ s⁻¹.", 0.01, "× 10⁻³ s⁻¹", difficulty=2),
   N("Doubling the concentration of A changes the initial rate of a reaction from 1.0 to 4.0 units. Find the order with respect to A.", 2, "Rate ∝ [A]² gives a four-fold increase when [A] doubles: second order.", 0, None)],
  P("Rate measurements for 2NO + O₂ → 2NO₂ at constant temperature (concentrations in mol dm⁻³): [NO] 0.010, [O₂] 0.010: rate 2.5 × 10⁻⁶; [NO] 0.020, [O₂] 0.010: 1.0 × 10⁻⁵; [NO] 0.020, [O₂] 0.020: 2.0 × 10⁻⁵.",
    [pn("Order with respect to NO.", 2, "Doubling [NO] (second row/first) gives 4 times the rate: second order.", 0, None, 1),
     pn("Order with respect to O₂.", 1, "Doubling [O₂] (third/second) doubles the rate: first order.", 0, None, 1),
     pn("Value of k (mol⁻² dm⁶ s⁻¹; answer in units of 1, to 3 s.f.) using the first experiment.", sg(2.5e-6 / (0.010 ** 2 * 0.010), 3), "k = rate/([NO]²[O₂]) = 2.5 × 10⁻⁶ ÷ (1.0 × 10⁻⁴ × 0.010) = 2.50 mol⁻² dm⁶ s⁻¹.", 0.02, "dm⁶ mol⁻² s⁻¹", 2),
     po("Explain why the rate equation cannot be written from the balanced equation alone.", "The orders are determined by experiment; they depend on the mechanism (slow step), not on the coefficients of the overall equation.", "1 mark: orders determined experimentally; 1 mark: depend on the mechanism / rate-determining step.", 2)]),
  [M("According to collision theory a reaction occurs when particles collide with", "energy ≥ E_a and the correct orientation", ["any energy", "energy < E_a", "no orientation needed"], "Both conditions are needed."),
   M("Raising the temperature increases the rate mainly because", "more molecules have energy ≥ E_a", ["the activation energy decreases", "ΔH becomes more negative", "the catalyst is formed"], "The high-energy tail of the distribution grows."),
   M("The units of the rate constant for a first-order reaction are", "s⁻¹", ["mol dm⁻³ s⁻¹", "dm³ mol⁻¹ s⁻¹", "mol⁻² dm⁶ s⁻¹"], "rate = k[A]: k = (mol dm⁻³ s⁻¹)/(mol dm⁻³)."),
   M("A heterogeneous catalyst is", "in a different phase from the reactants", ["in the same phase", "always a liquid", "used up in the reaction"], "e.g. solid iron with gaseous reactants."),
   M("Which does a catalyst NOT change?", "the position of equilibrium", ["the rate", "the route (mechanism)", "the activation energy"], "It speeds forward and reverse reactions equally.")],
  (mb_fig(), "Maxwell–Boltzmann distributions at two temperatures: the shaded fraction beyond E_a is larger at the higher temperature.", "Two skewed curves of fraction of molecules against energy, the higher-temperature curve lower and flatter, with a vertical line marking the activation energy."),
  notes=["Arrhenius equation and half-life of first-order reactions may be tested only qualitatively at this level.", "The curve 'T₂' is drawn with equal area; units on the axes are arbitrary."], minutes=40, prereq=["lower6-chemistry-enthalpy"])

# ---- 4.4 equilibrium
Kc_HI = 1.56 ** 2 / (0.22 * 0.22); assert abs(Kc_HI - 50.28) < 0.05
x_n = (-0.2 + math.sqrt(0.2 ** 2 + 4 * 4 * 0.1)) / 8; NO2e, N2O4e = 2 * x_n, 0.5 - x_n
assert abs(x_n - 0.1351) < 0.0005 and abs(4 * x_n ** 2 / (0.5 - x_n) - 0.20) < 1e-9
Kc_SO3 = 0.40 ** 2 / (0.20 ** 2 * 0.10); assert abs(Kc_SO3 - 40) < 1e-9
x_es = 2 / 3; x_es2 = (12 - math.sqrt(144 - 96)) / 6; assert abs(x_es ** 2 / (1 - x_es) ** 2 - 4) < 1e-9 and abs(x_es2 - 0.845) < 0.001 and abs(x_es2 ** 2 / ((1 - x_es2) * (2 - x_es2)) - 4) < 1e-9
def eq_fig():
    return plot(0, 10, 0, 1.8, curves=[("1.56*(1-exp(-x/1.5))", "red", "[HI]"), ("1-0.78*(1-exp(-x/1.5))", "blue", "[H₂] = [I₂]")], grid=2, xlabel="time", ylabel="concentration / mol dm⁻³", w=400, h=250)
lesson(ch, "equilibrium", "Chemical equilibrium and the equilibrium constant Kc",
  ["Describe dynamic equilibrium and write expressions for Kc with units",
   "Calculate Kc and equilibrium concentrations from experimental data (including the quadratic case)",
   "Predict the effect of changing concentration, pressure, temperature and a catalyst using Le Chatelier's principle"],
  [("definition", "Dynamic equilibrium",
    "A reversible reaction reaches **dynamic equilibrium** in a closed system when the rate of the forward reaction equals the rate of the reverse reaction: the concentrations stay constant but both reactions continue.\n\n"
    "For aA + bB ⇌ cC + dD at constant temperature: **K_c = [C]^c[D]^d / ([A]^a[B]^b)** (equilibrium concentrations in mol dm⁻³). Its units follow from the expression. K_c is large (≫ 1) if products dominate, small if reactants dominate. Pure solids and liquids are omitted."),
   ("retenir", "Le Chatelier's principle",
    "If a system at equilibrium is disturbed, it shifts to **oppose** the change:\n\n- **Add reactant** (or remove product): shifts right (K_c unchanged).\n- **Increase pressure**: shifts to the side with fewer gas molecules (K_c unchanged).\n- **Increase temperature**: shifts in the endothermic direction, and **K_c changes** (decreases for an exothermic forward reaction).\n- **Catalyst**: no shift; equilibrium is reached faster."),
   ("methode", "Method for Kc calculations (ICE table)",
    "1. Write the equation and the K_c expression.\n2. Make a table: **I**nitial, **C**hange (use x with the coefficients), **E**quilibrium amounts.\n3. If the volume is the same on both sides, moles can be used directly (V cancels); otherwise divide by V to get concentrations.\n4. Substitute into K_c; solve (a quadratic may arise: use the formula and reject the impossible root)."),
   ("pieges", "Common mistakes",
    "- Using initial concentrations instead of equilibrium concentrations in K_c.\n- Including solids or liquids (such as water as solvent) in the expression.\n- Saying a catalyst increases the yield: it does not change K_c or the position.\n- Saying K_c changes when concentration or pressure changes: only temperature changes K_c.")],
  [("Example 1 — Calculating Kc", "1.00 mol of H₂ and 1.00 mol of I₂ react in a 1.00 dm³ flask: H₂ + I₂ ⇌ 2HI. At equilibrium 1.56 mol of HI is present. Find K_c.",
    ["HI formed = 1.56 mol, so H₂ and I₂ each used = 0.78 mol; equilibrium H₂ = I₂ = 1.00 − 0.78 = 0.22 mol dm⁻³.", "K_c = [HI]²/([H₂][I₂]) = 1.56²/(0.22 × 0.22) = 2.434/0.0484 = 50.", "There are equal moles on both sides, so K_c has no units."], "K_c ≈ 50 (no units)"),
   ("Example 2 — Equilibrium concentrations", "N₂O₄(g) ⇌ 2NO₂(g) has K_c = 0.20 mol dm⁻³ at a certain temperature. Initially [N₂O₄] = 0.500 mol dm⁻³. Find the equilibrium concentrations.",
    ["Let x mol dm⁻³ of N₂O₄ react: [N₂O₄] = 0.500 − x, [NO₂] = 2x.", "0.20 = (2x)²/(0.500 − x), so 4x² + 0.20x − 0.100 = 0 and x = [−0.20 + √(0.04 + 1.60)]/8 = 0.135.", "[NO₂] = 0.270 mol dm⁻³ and [N₂O₄] = 0.365 mol dm⁻³."], "[NO₂] = 0.270; [N₂O₄] = 0.365 mol dm⁻³")],
  [M("The K_c expression for N₂ + 3H₂ ⇌ 2NH₃ is", "[NH₃]²/([N₂][H₂]³)", ["[NH₃]/([N₂][H₂])", "[N₂][H₂]³/[NH₃]²", "[NH₃]²/([N₂][H₂])"], "Products over reactants, each raised to its coefficient."),
   N("At equilibrium [SO₂] = 0.20, [O₂] = 0.10 and [SO₃] = 0.40 mol dm⁻³ for 2SO₂ + O₂ ⇌ 2SO₃. Find K_c (mol⁻¹ dm³).", Kc_SO3, "K_c = 0.40²/(0.20² × 0.10) = 0.16/0.0040 = 40 mol⁻¹ dm³.", 0.2, "dm³/mol"),
   TF("True or false: adding a catalyst to a system at equilibrium shifts the equilibrium position to the right.", False, "A catalyst speeds up both directions equally, so no shift occurs.")],
  [M("For N₂ + 3H₂ ⇌ 2NH₃ (ΔH = −92 kJ mol⁻¹), the yield of ammonia is increased by", "increasing the pressure", ["increasing the temperature", "removing the catalyst", "removing nitrogen"], "There are fewer gas molecules on the right (2 against 4), so a higher pressure shifts the equilibrium to the right."),
   N("Ethanoic acid + ethanol ⇌ ethyl ethanoate + water has K_c = 4.0. Starting from 1.0 mol of each (any volume), find the moles of ester at equilibrium (3 s.f.).", sg(x_es, 3), "x²/(1 − x)² = 4 gives x/(1 − x) = 2, so x = 2/3 = 0.667 mol (the volume cancels).", 0.003, "mol", difficulty=2)],
  P("Ethanoic acid reacts with ethanol: CH₃COOH + C₂H₅OH ⇌ CH₃COOC₂H₅ + H₂O, K_c = 4.0 at the temperature used. 1.0 mol of each reactant is mixed.",
    [pm("The K_c expression is", "[ester][water]/([acid][ethanol])", ["[acid][ethanol]/([ester][water])", "[ester]/[acid]", "[water]²/[ester]"], "Products over reactants.", 1),
     pn("Moles of ester at equilibrium (3 s.f.).", sg(x_es, 3), "x²/(1 − x)² = 4 so x = 0.667 mol.", 0.003, "mol", 1),
     pn("Percentage of the acid converted to ester (%, 3 s.f.).", sg(x_es * 100, 3), "0.667/1.0 × 100 = 66.7 %.", 0.2, "%", 1),
     pn("A further 1.0 mol of ethanol is added to the original mixture (2.0 mol of ethanol and 1.0 mol of acid in total). Find the new moles of ester at equilibrium (3 s.f.).", sg(x_es2, 3), "x²/((1 − x)(2 − x)) = 4 gives 3x² − 12x + 8 = 0, x = (12 − √48)/6 = 0.845 mol: Le Chatelier — more ester.", 0.005, "mol", 2)]),
  [M("At equilibrium", "the forward and reverse rates are equal", ["the reaction has stopped", "the concentrations of reactants and products are equal", "there is no reverse reaction"], "It is a dynamic equilibrium."),
   M("Only a change in which quantity changes the value of K_c?", "temperature", ["pressure", "concentration of a reactant", "adding a catalyst"], "K_c depends on temperature only."),
   M("The forward reaction is exothermic. Raising the temperature", "shifts the position left and decreases K_c", ["shifts right and increases K_c", "has no effect", "increases K_c only"], "Equilibrium moves in the endothermic (reverse) direction."),
   M("In H₂ + I₂ ⇌ 2HI, increasing the pressure", "has no effect on the position", ["shifts right", "shifts left", "changes K_c"], "The number of gas moles is the same on both sides."),
   M("For solids in a heterogeneous equilibrium, the K_c expression", "does not include them", ["includes them as 1 mol dm⁻³", "includes their mass", "is not valid"], "Their concentration is constant.")],
  (eq_fig(), "Approach to equilibrium for H₂ + I₂ ⇌ 2HI: the concentrations become constant.", "Two curves: the concentration of HI rising to 1.56 and that of hydrogen and iodine falling to 0.22 and then both levelling off."),
  notes=["K_c values for the esterification (4.0) and N₂O₄ (0.20) are illustrative for practice, not data-book values.", "K_p and equilibria with solubility products are in the Upper Sixth pack."], minutes=40, prereq=["lower6-chemistry-kinetics"])

# =============================================================== 5. ACIDS, BASES, REDOX
ch = p.chapter("ch-acids", "Acids, bases and buffers", SYL % "acids, bases and buffers")
pH_HCl = -math.log10(0.020); pH_NaOH = 14 + math.log10(0.010); assert abs(pH_HCl - 1.699) < 0.001 and abs(pH_NaOH - 12.0) < 1e-9
Ka = 1.8e-5; H_ac = math.sqrt(Ka * 0.10); pH_ac = -math.log10(H_ac); alpha = H_ac / 0.10 * 100
assert abs(H_ac - 1.342e-3) < 1e-6 and abs(pH_ac - 2.872) < 0.001 and abs(alpha - 1.342) < 0.001
Ka_x = (1.0e-3) ** 2 / 0.050; assert abs(Ka_x - 2.0e-5) < 1e-12
pKa_ac = -math.log10(Ka); assert abs(pKa_ac - 4.745) < 0.001
def ph_fig():
    it = []
    labs = [(1, "1"), (3, "3"), (5, "5"), (7, "7"), (9, "9"), (11, "11"), (13, "13")]
    cols = ["red", "orange", "yellow", "green", "cyan", "blue", "purple"]
    for i in range(14):
        c = ["red", "red", "red", "orange", "orange", "yellow", "yellow", "green", "cyan", "cyan", "blue", "blue", "purple", "purple"][i]
        it.append(RECT(20 + i * 26, 70, 26, 40, fill=c, stroke="ink", width=1))
    for n in (0, 3, 7, 11, 14):
        it.append(T(20 + n * 26 + (0 if n == 14 else 13), 135, str(n), 13))
    it += [T(200, 40, "pH scale (25 °C)", 14, bold=True), T(60, 175, "acidic", 13), T(200, 175, "neutral", 13), T(335, 175, "alkaline", 13)]
    return shapes(it, 400, 200)
lesson(ch, "acids", "Acids, bases and pH",
  ["Define acids and bases (Brønsted–Lowry) and conjugate acid–base pairs; distinguish strong and weak",
   "Calculate pH of strong acids and bases using pH = −lg[H⁺] and K_w = [H⁺][OH⁻] = 1.0 × 10⁻¹⁴",
   "Use K_a to calculate the pH of a weak acid and to find K_a from pH"],
  [("definition", "Brønsted–Lowry theory",
    "An **acid** is a proton (H⁺) donor and a **base** is a proton acceptor. When HA donates a proton, A⁻ is its **conjugate base**; when B accepts a proton, BH⁺ is its conjugate acid.\n\n"
    "A **strong acid** (HCl, HNO₃, H₂SO₄ for the first proton) is completely ionised in water; a **weak acid** (ethanoic acid, carbonic acid) is only partly ionised: HA ⇌ H⁺ + A⁻."),
   ("formule", "pH calculations",
    "**pH = −lg[H⁺]** ([H⁺] in mol dm⁻³), so [H⁺] = 10^(−pH). Water ionises slightly: **K_w = [H⁺][OH⁻] = 1.0 × 10⁻¹⁴** at 25 °C, so pH + pOH = 14.\n\n"
    "Weak acid: **K_a = [H⁺][A⁻]/[HA]**. If ionisation is small and [H⁺] = [A⁻]: **[H⁺] ≈ √(K_a × c)**. pK_a = −lg K_a (a smaller pK_a means a stronger acid).",
    r"\text{pH}=-\log[\text{H}^+]\qquad K_a=\frac{[\text{H}^+][\text{A}^-]}{[\text{HA}]}"),
   ("methode", "Method",
    "- **Strong acid**: [H⁺] = c (times the number of H⁺ per formula); pH = −lg[H⁺].\n- **Strong base**: [OH⁻] = c; [H⁺] = K_w/[OH⁻]; or pOH = −lg[OH⁻] and pH = 14 − pOH.\n- **Weak acid**: use [H⁺] = √(K_a c) (state the approximation) and then pH. To find K_a from pH: [H⁺] = 10^(−pH), then K_a ≈ [H⁺]²/c."),
   ("pieges", "Common mistakes",
    "- Confusing strong with concentrated: strength is the extent of ionisation, concentration is the amount per dm³.\n- Taking pH = −lg c for a weak acid.\n- Forgetting that diprotic H₂SO₄ gives [H⁺] = 2c (to a first approximation).\n- Giving pH with too many figures: the number of decimal places in a pH equals the number of significant figures in [H⁺].")],
  [("Example 1 — Strong acid and base", "Find the pH of 0.020 mol dm⁻³ HCl and of 0.010 mol dm⁻³ NaOH (K_w = 1.0 × 10⁻¹⁴).",
    ["HCl is strong: [H⁺] = 0.020, so pH = −lg 0.020 = 1.70.", "NaOH is strong: [OH⁻] = 0.010, so pOH = 2.00.", "pH = 14.00 − 2.00 = 12.0."], "pH 1.70 and pH 12.0"),
   ("Example 2 — A weak acid", "Find the pH of 0.10 mol dm⁻³ ethanoic acid (K_a = 1.8 × 10⁻⁵ mol dm⁻³) and the percentage ionised.",
    ["[H⁺] ≈ √(K_a c) = √(1.8 × 10⁻⁵ × 0.10) = √(1.8 × 10⁻⁶) = 1.34 × 10⁻³ mol dm⁻³.", "pH = −lg(1.34 × 10⁻³) = 2.87.", "Percentage ionised = 1.34 × 10⁻³/0.10 × 100 = 1.3 % (so the approximation is reasonable)."], "pH = 2.87; 1.3 % ionised")],
  [N("Calculate the pH of 0.0010 mol dm⁻³ HNO₃ (strong acid).", 3.0, "[H⁺] = 1.0 × 10⁻³, so pH = 3.0.", 0.01, None),
   N("Calculate [H⁺] (mol dm⁻³, in units of 10⁻⁵) for a solution of pH 4.50 (3 s.f.).", sg(10 ** -4.5 / 1e-5, 3), "[H⁺] = 10⁻⁴·⁵ = 3.16 × 10⁻⁵ mol dm⁻³.", 0.02, "× 10⁻⁵ mol/dm³"),
   MT("Match each substance with its role in water.", [("HCl", "strong acid"), ("CH₃COOH", "weak acid"), ("NaOH", "strong base"), ("NH₃", "weak base")], "HCl and NaOH ionise/dissociate completely; ethanoic acid and ammonia only partly.")],
  [N("Find the pH of 0.050 mol dm⁻³ H₂SO₄, treating it as fully ionised to 2H⁺ (3 s.f.).", sg(-math.log10(0.10), 3), "[H⁺] = 2 × 0.050 = 0.10, pH = 1.00.", 0.01, None, difficulty=2),
   N("A weak acid HA of concentration 0.050 mol dm⁻³ has pH 3.00. Estimate K_a (in units of 10⁻⁵ mol dm⁻³, 2 s.f.).", 2.0, "[H⁺] = 1.0 × 10⁻³; K_a ≈ [H⁺]²/c = 10⁻⁶/0.050 = 2.0 × 10⁻⁵.", 0.1, "× 10⁻⁵ mol/dm³")],
  P("Ethanoic acid has K_a = 1.8 × 10⁻⁵ mol dm⁻³ at 25 °C.",
    [pn("[H⁺] in 0.10 mol dm⁻³ ethanoic acid (10⁻³ mol dm⁻³, 3 s.f.).", sg(H_ac / 1e-3, 3), "√(1.8 × 10⁻⁵ × 0.10) = 1.34 × 10⁻³ mol dm⁻³.", 0.01, "× 10⁻³ mol/dm³", 1),
     pn("pH of the solution (3 s.f.).", sg(pH_ac, 3), "pH = −lg(1.342 × 10⁻³) = 2.87.", 0.01, None, 1),
     pn("pK_a of ethanoic acid (3 s.f.).", sg(pKa_ac, 3), "pK_a = −lg(1.8 × 10⁻⁵) = 4.74 (4.745).", 0.01, None, 1),
     pm("If the solution is diluted tenfold, the pH", "rises by about 0.5", ["rises by 1", "stays the same", "falls"], "[H⁺] ∝ √c: tenfold dilution divides [H⁺] by √10, so pH rises by 0.5.", 1)], figure=ph_fig()),
  [M("A Brønsted–Lowry acid is", "a proton donor", ["a proton acceptor", "an electron donor", "a hydroxide donor"], "H⁺ donors."),
   M("The conjugate base of H₂O is", "OH⁻", ["H₃O⁺", "O²⁻", "H₂"], "Remove one H⁺."),
   M("A solution has [OH⁻] = 1.0 × 10⁻⁴ mol dm⁻³. Its pH is", "10", ["4", "11", "7"], "pOH = 4; pH = 14 − 4 = 10."),
   M("A weak acid", "is only partly ionised in solution", ["is always dilute", "has pH 7", "is a strong proton donor"], "HA ⇌ H⁺ + A⁻."),
   M("If pH changes from 3 to 5, [H⁺] changes by a factor of", "100 (decreases)", ["2", "10 (increases)", "1000 (decreases)"], "Each pH unit is a tenfold change: two units give 100.")],
  (ph_fig(), "The pH scale from 0 (strongly acidic) to 14 (strongly alkaline).", "A row of fourteen coloured boxes from red at pH 0 to purple at pH 14 with labels acidic, neutral and alkaline."),
  notes=["K_a of ethanoic acid 1.8 × 10⁻⁵ mol dm⁻³ and K_w = 1.0 × 10⁻¹⁴ (25 °C) are standard values.", "Hydrolysis of salts and pH of mixtures of acid and base are not included."], minutes=40, prereq=["lower6-chemistry-equilibrium"])

# ---- 5.2 buffers and titration
Hbuf0 = pKa_ac; pH_b2 = pKa_ac + math.log10(0.20 / 0.10)
assert abs(Hbuf0 - 4.745) < 0.001 and abs(pH_b2 - 5.046) < 0.001
nHA, nA = 0.100 * 0.100, 0.100 * 0.100; add = 0.0010; pH_after = pKa_ac + math.log10((nA - add) / (nHA + add))
assert abs(pH_after - 4.658) < 0.001, pH_after
c_HCl = 0.100 * 21.50 / 25.0; assert abs(c_HCl - 0.0860) < 1e-9
nB = 0.100 * 8.30 / 1000; c_dil = nB / 0.0250; c_vin = c_dil * 25; g_vin = c_vin * 60.0
assert abs(c_dil - 0.0332) < 1e-9 and abs(c_vin - 0.830) < 1e-9 and abs(g_vin - 49.8) < 1e-9, (c_dil, c_vin, g_vin)
def titr_pts():
    pts = []
    for v in [0, 5, 10, 15, 20, 22, 24, 24.5, 24.9, 25, 25.1, 25.5, 26, 28, 30, 35, 40, 50]:
        if v < 25: h = 0.1 * (25 - v) / (25 + v); y = -math.log10(h)
        elif v == 25: y = 7.0
        else: oh = 0.1 * (v - 25) / (25 + v); y = 14 + math.log10(oh)
        pts.append((v, y))
    return pts
def titr_fig():
    P_ = titr_pts()
    segs = [(a[0], a[1], b[0], b[1], "blue", False, None) for a, b in zip(P_, P_[1:])]
    return plot(0, 50, 0, 14, segments=segs, points=[(25, 7, "equivalence 25 cm³", "red")], grid=5, xlabel="volume of NaOH added / cm³", ylabel="pH", w=400, h=260)
lesson(ch, "buffers", "Buffers and acid–base titrations",
  ["Explain how a buffer solution resists changes in pH and calculate its pH using K_a (Henderson equation)",
   "Describe titration curves for strong/strong and weak/strong combinations and choose an indicator",
   "Do titration calculations including dilution factors"],
  [("definition", "Buffer solutions",
    "A **buffer** resists a change in pH when small amounts of acid or alkali are added. An acidic buffer is a **weak acid with its conjugate base** (e.g. CH₃COOH and CH₃COONa): the large reservoir of A⁻ removes added H⁺ (A⁻ + H⁺ → HA) and the large reservoir of HA removes added OH⁻ (HA + OH⁻ → A⁻ + H₂O).\n\n"
    "Examples: ethanoic acid/ethanoate; ammonia/ammonium (alkaline buffer); in blood, carbonic acid/hydrogencarbonate keeps the pH near 7.4."),
   ("formule", "Buffer pH",
    "From K_a = [H⁺][A⁻]/[HA]: **pH = pK_a + lg([A⁻]/[HA])** (Henderson–Hasselbalch). When [A⁻] = [HA], pH = pK_a. The ratio can be taken as the ratio of **moles** (the volume cancels). After adding a small amount of strong acid or base, recalculate the moles of HA and A⁻ and apply the equation again.",
    r"\text{pH}=\text{p}K_a+\log\frac{[\text{A}^-]}{[\text{HA}]}"),
   ("retenir", "Titration curves and indicators",
    "In a titration the volume of one solution needed to react exactly with another is measured. At the **equivalence point** the acid and base have reacted in the mole ratio of the equation. Strong acid + strong base: pH 7 at equivalence, a very steep rise. Weak acid + strong base: pH above 7 at equivalence (the salt is basic). Indicators change colour over about 2 pH units: **methyl orange** (about 3.1–4.4), **phenolphthalein** (about 8.3–10); choose one whose range lies in the steep part."),
   ("pieges", "Common mistakes",
    "- Saying the buffer 'neutralises' all added acid: it only limits the change in pH while the reservoirs last.\n- Using a buffer mixture of a weak acid with a strong acid salt (e.g. HA and NaCl): it needs the conjugate base.\n- Forgetting the dilution factor when the titrated solution was made up to a larger volume.\n- Choosing methyl orange for a weak acid/strong base titration: its range is far from the equivalence pH.")],
  [("Example 1 — Buffer pH", "A buffer is made from 0.10 mol dm⁻³ ethanoic acid and 0.10 mol dm⁻³ sodium ethanoate (K_a = 1.8 × 10⁻⁵, pK_a = 4.74). Find its pH, then the pH if 10.0 cm³ of 0.100 mol dm⁻³ HCl is added to 100 cm³ of the buffer.",
    ["pH = pK_a + lg(0.10/0.10) = 4.74 + 0 = 4.74.", "Moles: HA = A⁻ = 0.0100 mol; added H⁺ = 0.00100 mol reacts with A⁻: A⁻ = 0.0090, HA = 0.0110 mol.", "pH = 4.74 + lg(0.0090/0.0110) = 4.74 − 0.087 = 4.66 (only a change of 0.08)."], "pH = 4.74, then 4.66"),
   ("Example 2 — Titration of vinegar", "10.0 cm³ of vinegar is made up to 250 cm³. 25.0 cm³ of this solution needs 8.30 cm³ of 0.100 mol dm⁻³ NaOH. Find the concentration of ethanoic acid in the vinegar (CH₃COOH + NaOH → CH₃COONa + H₂O; M = 60.0).",
    ["n(NaOH) = 0.100 × 8.30/1000 = 8.30 × 10⁻⁴ mol = n(acid) in 25.0 cm³ (1 : 1).", "Concentration of the diluted solution = 8.30 × 10⁻⁴/0.0250 = 0.0332 mol dm⁻³; undiluted = × 25 = 0.830 mol dm⁻³.", "Mass concentration = 0.830 × 60.0 = 49.8 g dm⁻³, about 5 % (g per 100 cm³), a typical value for vinegar."], "0.830 mol dm⁻³ (about 50 g dm⁻³)")],
  [N("25.0 cm³ of NaOH is neutralised by 21.50 cm³ of 0.100 mol dm⁻³ HCl. Find the concentration of NaOH (mol dm⁻³, 3 s.f.).", sg(c_HCl, 3), "n(HCl) = 0.100 × 0.02150 = 2.15 × 10⁻³ mol = n(NaOH); c = 2.15 × 10⁻³/0.0250 = 0.0860 mol dm⁻³.", 0.0005, "mol/dm³"),
   M("A buffer solution can be made from", "ethanoic acid and sodium ethanoate", ["hydrochloric acid and sodium chloride", "sodium hydroxide and sodium chloride", "nitric acid and water"], "A weak acid and its salt (conjugate base)."),
   TF("True or false: a weak acid titrated with a strong base has an equivalence point at pH 7.", False, "The salt formed is basic (its anion hydrolyses), so the pH at equivalence is greater than 7.")],
  [N("A buffer has [HA] = 0.10 and [A⁻] = 0.20 mol dm⁻³ with pK_a = 4.74. Find the pH (3 s.f.).", sg(pH_b2, 3), "pH = 4.74 + lg 2 = 5.04 (5.05 using pK_a = 4.745).", 0.01, None, difficulty=2),
   M("Which indicator is suitable for titrating ethanoic acid with sodium hydroxide?", "phenolphthalein", ["methyl orange", "methyl red only", "no indicator can be used"], "The equivalence pH is about 8.7, within phenolphthalein's range (8.3–10).")],
  P("25.0 cm³ of 0.100 mol dm⁻³ hydrochloric acid is titrated with 0.100 mol dm⁻³ sodium hydroxide (see the titration curve).",
    [pn("Volume of NaOH needed to reach the equivalence point (cm³).", 25.0, "Equal concentrations and a 1 : 1 ratio: 25.0 cm³.", 0.05, "cm³", 1),
     pn("pH at the equivalence point.", 7.0, "Strong acid + strong base give a neutral salt: pH 7.", 0.1, None, 1),
     pm("A suitable indicator is", "methyl orange or phenolphthalein (the steep rise covers both ranges)", ["none: the pH change is too small", "universal indicator only", "starch"], "The jump from about pH 4 to 10 covers both ranges.", 1),
     pn("pH after adding 20.0 cm³ of NaOH (3 s.f.).", sg(titr_pts()[4][1], 3), "Excess H⁺: (25 − 20) × 0.100/45 = 0.0111 mol dm⁻³, so pH = 1.95.", 0.02, None, 2)], figure=titr_fig()),
  [M("In a buffer of HA and A⁻, added OH⁻ is removed by", "HA", ["A⁻", "water only", "the indicator"], "HA + OH⁻ → A⁻ + H₂O."),
   M("When [A⁻] = [HA] the pH of a buffer equals", "pK_a", ["7", "pK_w", "2 pK_a"], "lg 1 = 0."),
   M("At the equivalence point of a titration", "the acid and base have reacted in the ratio of the equation", ["the pH is always 7", "the indicator is colourless", "one solution is in excess"], "Stoichiometric amounts."),
   M("The pH in the blood is kept steady mainly by", "the carbonic acid/hydrogencarbonate buffer", ["methyl orange", "sodium chloride", "water alone"], "A natural buffer system."),
   M("Which is the correct apparatus to measure 25.0 cm³ of solution accurately?", "a pipette", ["a measuring cylinder", "a beaker", "a conical flask"], "Pipettes give the best accuracy.")],
  (titr_fig(), "Titration curve of a strong acid with a strong base: pH rises sharply near 25 cm³.", "A curve starting at pH 1 rising slowly then very steeply at 25 cm³ to about pH 12 and levelling near 13."),
  notes=["Indicator ranges (methyl orange 3.1–4.4; phenolphthalein 8.3–10) are standard textbook values.", "Vinegar strength of about 5 % is a typical value (to be verified). ", "Titration curve for weak acid/strong base is described but not drawn."], minutes=45, prereq=["lower6-chemistry-acids"])

# ---- 5.3 redox
n_perm = 0.0200 * 20.00 / 1000; n_fe = 5 * n_perm; c_fe = n_fe / 0.0250
assert abs(n_perm - 4.00e-4) < 1e-12 and abs(n_fe - 2.00e-3) < 1e-12 and abs(c_fe - 0.0800) < 1e-12
def burette_fig():
    it = [RECT(130, 20, 16, 120, stroke="ink"), LINE(138, 140, 138, 160, width=2), T(160, 70, "burette:", 12, anchor="start"), T(160, 88, "KMnO₄ (purple)", 12, anchor="start"),
          POLY([95, 230, 185, 230, 155, 170, 125, 170], fill="lightyellow", width=2), T(140, 255, "conical flask: Fe²⁺(aq)", 12)]
    return shapes(it, 330, 270)
lesson(ch, "redox", "Redox reactions and oxidation numbers",
  ["Define oxidation and reduction in terms of electron transfer and oxidation number; assign oxidation numbers",
   "Write and combine half-equations, including in acidic solution, and recognise disproportionation",
   "Do calculations for redox titrations (for example KMnO₄ with Fe²⁺)"],
  [("definition", "Oxidation and reduction",
    "**Oxidation** is loss of electrons (oxidation number increases); **reduction** is gain of electrons (oxidation number decreases): *OIL RIG*. An **oxidising agent** is reduced; a **reducing agent** is oxidised.\n\n"
    "Rules for **oxidation numbers**: elements 0; simple ions = charge; O is −2 (except in peroxides, −1); H is +1 (except in metal hydrides, −1); the sum in a neutral compound is 0 and in an ion equals its charge. Examples: Mn in MnO₄⁻ is +7; Cr in Cr₂O₇²⁻ is +6."),
   ("methode", "Balancing a redox equation",
    "1. Write the two half-equations (oxidation and reduction).\n2. Balance atoms other than O and H; then O using H₂O; then H using H⁺ (acidic solution); then charge using electrons.\n3. Multiply so that the electrons cancel and add the half-equations.\n\nExample: MnO₄⁻ + 8H⁺ + 5e⁻ → Mn²⁺ + 4H₂O and Fe²⁺ → Fe³⁺ + e⁻ give **MnO₄⁻ + 8H⁺ + 5Fe²⁺ → Mn²⁺ + 4H₂O + 5Fe³⁺**."),
   ("retenir", "Disproportionation and titrations",
    "In **disproportionation** the same element is both oxidised and reduced: Cl₂ + 2NaOH → NaCl + NaClO + H₂O (Cl: 0 → −1 and +1). In a **redox titration** with acidified potassium manganate(VII), the purple MnO₄⁻ is its own indicator: the end-point is the first permanent pale pink colour. Use dilute sulfuric acid (not hydrochloric acid, which MnO₄⁻ oxidises)."),
   ("pieges", "Common mistakes",
    "- Forgetting that oxidation numbers are written +7, −2 with the sign, and that they are per atom.\n- Not cancelling electrons (5Fe²⁺ for each MnO₄⁻).\n- Using HCl to acidify a permanganate titration.\n- Saying oxidation is gain of oxygen only: the electron definition is more general.")],
  [("Example 1 — Oxidation numbers", "Find the oxidation number of (a) S in H₂SO₄, (b) N in NH₄⁺, (c) Cr in Cr₂O₇²⁻. State what happens to Cl in Cl₂ + 2NaOH → NaCl + NaClO + H₂O.",
    ["(a) 2(+1) + S + 4(−2) = 0, so S = +6. (b) N + 4(+1) = +1, so N = −3. (c) 2Cr + 7(−2) = −2, so Cr = +6.", "Chlorine goes from 0 in Cl₂ to −1 in NaCl (reduced) and to +1 in NaClO (oxidised): disproportionation."], "S +6, N −3, Cr +6; Cl is disproportionated"),
   ("Example 2 — Redox titration", "25.0 cm³ of an iron(II) solution needs 20.00 cm³ of 0.0200 mol dm⁻³ KMnO₄ in acid. Find the concentration of Fe²⁺.",
    ["n(MnO₄⁻) = 0.0200 × 20.00/1000 = 4.00 × 10⁻⁴ mol.", "Ratio MnO₄⁻ : Fe²⁺ = 1 : 5, so n(Fe²⁺) = 2.00 × 10⁻³ mol.", "c = 2.00 × 10⁻³/0.0250 = 0.0800 mol dm⁻³."], "[Fe²⁺] = 0.0800 mol dm⁻³")],
  [M("What is the oxidation number of Mn in KMnO₄?", "+7", ["+2", "+4", "+6"], "K +1; four O at −2 = −8; so Mn = +7."),
   N("Find the oxidation number of Cr in K₂Cr₂O₇.", 6, "2(+1) + 2Cr + 7(−2) = 0, so Cr = +6.", 0, None),
   TF("True or false: in the reaction Zn + Cu²⁺ → Zn²⁺ + Cu, zinc is oxidised.", True, "Zn loses two electrons (0 → +2): it is oxidised and acts as the reducing agent.")],
  [N("25.0 cm³ of Fe²⁺ solution needs 15.00 cm³ of 0.0200 mol dm⁻³ KMnO₄. Find [Fe²⁺] (mol dm⁻³, 3 s.f.).", sg(5 * 0.0200 * 15.00 / 1000 / 0.0250, 3), "n(MnO₄⁻) = 3.00 × 10⁻⁴; n(Fe²⁺) = 1.50 × 10⁻³ mol; c = 0.0600 mol dm⁻³.", 0.0005, "mol/dm³", difficulty=2),
   M("In Cl₂ + 2NaOH → NaCl + NaClO + H₂O, chlorine is", "both oxidised and reduced", ["only oxidised", "only reduced", "neither"], "Its oxidation number goes 0 → −1 and 0 → +1.")],
  P("An iron tablet of mass 0.50 g is dissolved in dilute sulfuric acid and made up to 100 cm³. 25.0 cm³ of this solution needs 22.50 cm³ of 0.0100 mol dm⁻³ KMnO₄ (A_r: Fe 56.0).",
    [pn("Moles of MnO₄⁻ used (10⁻⁴ mol, 3 s.f.).", sg(0.0100 * 22.50 / 1000 / 1e-4, 3), "0.0100 × 0.02250 = 2.25 × 10⁻⁴ mol.", 0.01, "× 10⁻⁴ mol", 1),
     pn("Moles of Fe²⁺ in 25.0 cm³ (10⁻³ mol, 3 s.f.).", sg(5 * 0.0100 * 22.50 / 1000 / 1e-3, 3), "5 × 2.25 × 10⁻⁴ = 1.125 × 10⁻³ mol (1.12 or 1.13 in these units).", 0.01, "× 10⁻³ mol", 1),
     pn("Mass of iron in the whole tablet (g, 3 s.f.).", sg(5 * 0.0100 * 22.50 / 1000 * 4 * 56.0, 3), "Moles in 100 cm³ = 4 × 1.125 × 10⁻³ = 4.50 × 10⁻³ mol; mass = 4.50 × 10⁻³ × 56.0 = 0.252 g.", 0.002, "g", 1),
     pn("Percentage of iron in the tablet by mass (%, 3 s.f.).", sg(5 * 0.0100 * 22.50 / 1000 * 4 * 56.0 / 0.50 * 100, 3), "0.252/0.50 × 100 = 50.4 %.", 0.2, "%", 1)], figure=burette_fig()),
  [M("Oxidation is", "loss of electrons", ["gain of electrons", "gain of protons", "loss of neutrons"], "OIL RIG."),
   M("The oxidising agent in MnO₄⁻ + 8H⁺ + 5Fe²⁺ → Mn²⁺ + 4H₂O + 5Fe³⁺ is", "MnO₄⁻", ["Fe²⁺", "H⁺", "H₂O"], "MnO₄⁻ is reduced (+7 → +2), so it is the oxidising agent."),
   M("The oxidation number of oxygen in H₂O₂ is", "−1", ["−2", "0", "+1"], "In peroxides O is −1."),
   M("The end-point colour in a permanganate titration is", "permanent pale pink", ["blue-black", "colourless", "yellow"], "MnO₄⁻ acts as its own indicator."),
   M("Which acid is used to acidify KMnO₄ titrations?", "dilute sulfuric acid", ["hydrochloric acid", "nitric acid", "ethanoic acid"], "Neither Cl⁻ nor NO₃⁻ should interfere.")],
  (burette_fig(), "Titration apparatus: KMnO₄ from the burette is added to Fe²⁺ solution in the flask.", "A burette above a conical flask containing a pale solution."),
  notes=[DATA, "Mass of iron tablets used in problems is illustrative; chosen for round numbers.", "Balancing in alkaline solution is not covered."], minutes=40, prereq=["lower6-chemistry-mole"])

# ---- 5.4 electrolysis
Q_cu = 2.00 * 30.0 * 60; n_e = Q_cu / F_; m_cu = n_e / 2 * 63.5
assert abs(Q_cu - 3600) < 1e-9 and abs(n_e - 0.03731) < 1e-4 and abs(m_cu - 1.185) < 0.002, (n_e, m_cu)
Qw = 5.0 * 600; ne_w = Qw / F_; VH2 = ne_w / 2 * 24000; VO2 = ne_w / 4 * 24000
assert abs(ne_w - 0.03109) < 1e-4 and abs(VH2 - 373.1) < 0.5 and abs(VO2 - 186.5) < 0.5
Al_e = 1000 / 27.0 * 3; t_Al = Al_e * F_ / 1.0e5; assert abs(t_Al - 107.2) < 0.2, t_Al
Na_A = F_ / 1.60e-19; assert abs(Na_A - 6.03e23) < 0.01e23
def elec_fig():
    it = [RECT(110, 70, 160, 120, stroke="ink", fill="lightblue"), LINE(150, 50, 150, 150, width=6, color="grey"), LINE(230, 50, 230, 150, width=6, color="orange"),
          LINE(150, 50, 150, 20, width=2), LINE(230, 50, 230, 20, width=2), LINE(150, 20, 175, 20, width=2), LINE(205, 20, 230, 20, width=2),
          LINE(183, 10, 183, 30, width=2), LINE(197, 17, 197, 23, width=4), T(190, 8, "supply", 12),
          T(150, 175, "cathode (−)", 12, color="grey"), T(230, 175, "anode (+)", 12, color="orange"),
          T(150, 215, "reduction: Cu²⁺ + 2e⁻ → Cu", 12, anchor="start"), T(150, 235, "oxidation: 2Cl⁻ → Cl₂ + 2e⁻", 12, anchor="start")]
    return shapes(it, 420, 250)
lesson(ch, "electrolysis", "Electrolysis and Faraday's laws",
  ["Describe electrolysis of molten compounds and aqueous solutions and predict the products at each electrode",
   "Use Q = It and the Faraday constant to calculate masses and volumes deposited",
   "Describe applications: extraction of aluminium, electroplating and purification of copper"],
  [("definition", "Electrolysis",
    "**Electrolysis** is the decomposition of an ionic compound (molten or in solution) by an electric current through an **electrolyte** between two **electrodes**. At the **cathode** (−) cations are **reduced**; at the **anode** (+) anions are **oxidised** (the mnemonic: *RedCat, AnOx*).\n\n"
    "In molten compounds the ions are discharged directly (e.g. molten PbBr₂: Pb at the cathode, Br₂ at the anode)."),
   ("retenir", "Products from aqueous solutions",
    "At the **cathode** the less reactive metal ion is reduced (Cu²⁺ before H⁺; for K⁺, Na⁺, Ca²⁺, Al³⁺ hydrogen is formed). At the **anode**: halide ions (Cl⁻, Br⁻, I⁻) are discharged if reasonably concentrated; otherwise hydroxide (from water) is oxidised to O₂; sulfate and nitrate ions are not discharged. With copper electrodes in CuSO₄(aq) the anode dissolves and copper is deposited at the cathode (purification, electroplating)."),
   ("formule", "Faraday's laws",
    "**Q = It** (coulombs). The **Faraday constant** F = 96 500 C mol⁻¹ is the charge of one mole of electrons (F = N_A × e). Moles of electrons = Q/F. For an ion of charge z, moles of substance = Q/(zF). For a gas, 1 mol at rtp = 24.0 dm³.\n\nAluminium is extracted by electrolysis of alumina dissolved in molten cryolite (Al³⁺ + 3e⁻ → Al); the carbon anodes burn away.",
    r"Q=It\qquad n(e^-)=\frac{Q}{F}"),
   ("pieges", "Common mistakes",
    "- Using minutes instead of seconds in Q = It.\n- Forgetting the charge z: Cu²⁺ needs 2 mol of electrons per mole of copper; Al³⁺ needs 3.\n- Saying electrons flow through the electrolyte: they flow in the wires; ions carry the current in the electrolyte.\n- Confusing the polarity: anode is positive in electrolysis (the opposite of a galvanic cell's labelling by polarity).")],
  [("Example 1 — Copper plating", "A current of 2.00 A flows for 30.0 minutes through copper(II) sulfate solution. Find the mass of copper deposited (A_r Cu = 63.5, F = 96 500 C mol⁻¹).",
    ["Q = It = 2.00 × 30.0 × 60 = 3600 C; n(e⁻) = 3600/96 500 = 0.0373 mol.", "Cu²⁺ + 2e⁻ → Cu, so n(Cu) = 0.0373/2 = 0.01865 mol.", "m = 0.01865 × 63.5 = 1.18 g."], "1.18 g of copper"),
   ("Example 2 — Water", "A current of 5.0 A passes through dilute sulfuric acid for 10 minutes using inert electrodes. Find the volumes of H₂ and O₂ at rtp.",
    ["Q = 5.0 × 600 = 3000 C; n(e⁻) = 3000/96 500 = 0.0311 mol.", "Cathode: 2H⁺ + 2e⁻ → H₂ gives 0.01555 mol, i.e. 0.01555 × 24.0 = 0.373 dm³.", "Anode: 4OH⁻ → O₂ + 2H₂O + 4e⁻ gives 0.00777 mol = 0.187 dm³ (half the volume of H₂)."], "373 cm³ of H₂ and 187 cm³ of O₂")],
  [N("Find the charge passed by 0.50 A in 20 minutes (C).", 0.50 * 20 * 60, "Q = It = 0.50 × 1200 = 600 C.", 0.5, "C"),
   M("At the cathode during the electrolysis of aqueous copper(II) sulfate (inert electrodes), the product is", "copper", ["hydrogen", "oxygen", "sulfur"], "Cu²⁺ is reduced in preference to H⁺."),
   TF("True or false: in electrolysis, electrons flow through the electrolyte.", False, "Ions carry the current in the electrolyte; electrons flow in the external wires.")],
  [N("Calculate the mass of silver deposited by 0.500 A for 1.00 hour (A_r Ag = 108; Ag⁺ + e⁻ → Ag; g, 3 s.f.).", sg(0.500 * 3600 / F_ * 108, 3), "Q = 1800 C; n(e⁻) = 0.01865 mol = n(Ag); m = 0.01865 × 108 = 2.01 g.", 0.02, "g", difficulty=2),
   N("How long must 100 kA pass to deposit 1.00 kg of aluminium (Al³⁺ + 3e⁻ → Al; A_r = 27.0)? Give seconds (3 s.f.).", sg(t_Al, 3), "n(Al) = 1000/27.0 = 37.0 mol; n(e⁻) = 111 mol; Q = 111 × 96 500 = 1.07 × 10⁷ C; t = Q/I = 107 s.", 1, "s")],
  P("An aluminium smelter electrolyses alumina (Al₂O₃) dissolved in molten cryolite using a current of 80 kA (A_r Al = 27.0; F = 96 500 C mol⁻¹). Consider 1.0 hour at 100 % current efficiency.",
    [pn("Charge passed in one hour (10⁸ C, 3 s.f.).", sg(80e3 * 3600 / 1e8, 3), "Q = It = 8.0 × 10⁴ × 3600 = 2.88 × 10⁸ C.", 0.01, "× 10⁸ C", 1),
     pn("Moles of electrons (10³ mol, 3 s.f.).", sg(80e3 * 3600 / F_ / 1e3, 3), "n(e⁻) = 2.88 × 10⁸/96 500 = 2.98 × 10³ mol.", 0.02, "× 10³ mol", 1),
     pn("Mass of aluminium formed (kg, 3 s.f.).", sg(80e3 * 3600 / F_ / 3 * 27.0 / 1000, 3), "n(Al) = 2985/3 = 995 mol; m = 995 × 27.0 = 26.9 kg.", 0.2, "kg", 2),
     po("Explain why the carbon anodes must be replaced regularly.", "Oxygen formed at the anode reacts with the hot carbon to form carbon dioxide, so the anodes burn away.", "1 mark: oxygen produced at the anode; 1 mark: reacts with (hot) carbon to form CO₂/CO.", 2)], figure=elec_fig()),
  [M("The Faraday constant is the charge of", "one mole of electrons", ["one electron", "one mole of atoms", "one coulomb"], "F = N_A × e."),
   M("Which is produced at the anode when concentrated aqueous NaCl is electrolysed with inert electrodes?", "chlorine", ["sodium", "oxygen only", "hydrogen"], "Cl⁻ is discharged in preference to OH⁻ in concentrated solution."),
   M("To deposit 1 mol of Cu from Cu²⁺ you need", "2 mol of electrons", ["1 mol of electrons", "3 mol of electrons", "0.5 mol of electrons"], "Cu²⁺ + 2e⁻ → Cu."),
   M("In the purification of copper, the impure copper is the", "anode", ["cathode", "electrolyte", "salt bridge"], "It dissolves as Cu²⁺ and pure copper plates on the cathode."),
   M("Increasing the time of electrolysis by a factor of 3 (same current) changes the mass deposited by a factor of", "3", ["9", "1/3", "1"], "m ∝ Q = It.")],
  (elec_fig(), "An electrolysis cell: reduction at the cathode (−), oxidation at the anode (+).", "A beaker with two electrodes connected to a supply, the negative cathode and positive anode labelled with the half-reactions."),
  notes=[DATA + " F = 96 500 C mol⁻¹.", "A Cameroonian industrial example (aluminium smelter) was deliberately left out of the lesson text until a teacher can confirm the facts.", "Standard electrode potentials (to predict discharge) are in the Upper Sixth pack."], minutes=40, prereq=["lower6-chemistry-redox"])

# =============================================================== 6. ORGANIC CHEMISTRY
ch = p.chapter("ch-organic", "Introduction to organic chemistry", SYL % "organic chemistry")
def ez_fig():
    it = []
    def alkene(x1, up_left, up_right, down_left, down_right, title, tx):
        y = 110
        x2 = x1 + 55
        out = [LINE(x1, y - 3, x2, y - 3, width=2), LINE(x1, y + 3, x2, y + 3, width=2),
               LINE(x1, y, x1 - 30, y - 40 if up_left else y + 40, width=2), LINE(x2, y, x2 + 30, y - 40 if up_right else y + 40, width=2),
               LINE(x1, y, x1 - 30, y + 40 if up_left else y - 40, width=2), LINE(x2, y, x2 + 30, y + 40 if up_right else y - 40, width=2)]
        return out
    # Z isomer: both CH3 up
    it += [LINE(70, 107, 125, 107, width=2), LINE(70, 113, 125, 113, width=2), LINE(70, 110, 40, 70, width=2), LINE(125, 110, 155, 70, width=2), LINE(70, 110, 40, 150, width=2), LINE(125, 110, 155, 150, width=2),
           T(40, 62, "CH₃", 13), T(155, 62, "CH₃", 13), T(40, 168, "H", 13), T(155, 168, "H", 13), T(98, 200, "Z-but-2-ene (cis)", 13)]
    # E isomer: CH3 on opposite sides
    it += [LINE(270, 107, 325, 107, width=2), LINE(270, 113, 325, 113, width=2), LINE(270, 110, 240, 70, width=2), LINE(325, 110, 355, 150, width=2), LINE(270, 110, 240, 150, width=2), LINE(325, 110, 355, 70, width=2),
           T(240, 62, "CH₃", 13), T(355, 168, "CH₃", 13), T(240, 168, "H", 13), T(355, 62, "H", 13), T(298, 200, "E-but-2-ene (trans)", 13)]
    return shapes(it, 400, 215)
lesson(ch, "isomers", "Homologous series, naming and isomerism",
  ["Name and draw simple alkanes, alkenes, alcohols and halogenoalkanes (IUPAC) and give general formulae",
   "Distinguish chain, position and functional-group structural isomers",
   "Explain E/Z isomerism in alkenes and recognise a chiral carbon"],
  [("definition", "Homologous series",
    "A **homologous series** is a family of compounds with the same functional group and general formula; successive members differ by CH₂. **Alkanes** C_nH₂ₙ₊₂ (single bonds); **alkenes** C_nH₂ₙ (C=C); **alcohols** C_nH₂ₙ₊₁OH (–OH); **halogenoalkanes** C_nH₂ₙ₊₁X (–Cl, –Br, –I); also carboxylic acids (–COOH), aldehydes (–CHO) and ketones (C=O) in the Upper Sixth.\n\n"
    "The **functional group** gives the characteristic reactions. Physical properties change gradually along the series (boiling points rise with chain length)."),
   ("methode", "IUPAC naming",
    "1. Find the **longest carbon chain** containing the functional group: meth-, eth-, prop-, but-, pent-, hex-…\n2. Number from the end that gives the **lowest number** to the functional group (then to substituents).\n3. Name side groups (methyl, ethyl, chloro, bromo) as prefixes in **alphabetical order**, with locants.\n4. Add the ending: -ane, -ene, -an-1-ol, etc. Example: CH₃CH(CH₃)CH₂CH₃ = **2-methylbutane**; CH₃CH=CHCH₃ = **but-2-ene**; CH₃CH(OH)CH₃ = **propan-2-ol**."),
   ("retenir", "Isomerism",
    "**Isomers** have the same molecular formula but different structures. **Structural isomers**: *chain* (different carbon skeleton: butane and 2-methylpropane), *position* (but-1-ene and but-2-ene), *functional group* (ethanol and methoxymethane).\n\n"
    "**E/Z isomers** need a C=C (restricted rotation) with two different groups on each carbon: **Z** if the higher-priority groups are on the same side, **E** if opposite (cis/trans for simple cases). An **optical isomer** needs a **chiral carbon** with four different groups (two mirror-image forms)."),
   ("pieges", "Common mistakes",
    "- Choosing the longest chain without the functional group (the chain must include C=C or C–OH).\n- Numbering from the wrong end: the functional group gets the lowest locant.\n- Counting a rotated drawing as a new isomer: rotation about single bonds gives the same compound.\n- Expecting E/Z isomers where one carbon of the double bond has two identical groups (propene has none).")],
  [("Example 1 — Isomeric alkenes", "Draw and name the alkene isomers of C₄H₈ (open-chain).",
    ["Chain length 4: but-1-ene CH₂=CHCH₂CH₃ and but-2-ene CH₃CH=CHCH₃.", "But-2-ene has two different groups on each double-bonded carbon, so it exists as **E** and **Z** forms.", "Branched chain: 2-methylpropene (CH₃)₂C=CH₂ (no E/Z). Total: 4 alkenes (counting E and Z separately)."], "but-1-ene, (E)-but-2-ene, (Z)-but-2-ene, 2-methylpropene"),
   ("Example 2 — Isomeric alcohols", "Name the four alcohols with formula C₄H₁₀O and classify each as primary, secondary or tertiary.",
    ["Straight chain: butan-1-ol (primary, OH on a CH₂ at the end) and butan-2-ol (secondary).", "Branched chain: 2-methylpropan-1-ol (primary) and 2-methylpropan-2-ol (tertiary: the carbon with OH bears three carbon groups).", "Ethers such as ethoxyethane are further functional-group isomers but are not alcohols."], "butan-1-ol, butan-2-ol, 2-methylpropan-1-ol, 2-methylpropan-2-ol")],
  [N("How many hydrogen atoms are in an alkane with 8 carbon atoms?", 18, "C_nH₂ₙ₊₂: 2(8) + 2 = 18 (octane, C₈H₁₈).", 0, None),
   M("What is the name of CH₃CH₂CH(CH₃)CH₃?", "2-methylbutane", ["3-methylbutane", "pentane", "2-ethylpropane"], "The longest chain is four carbons; numbering from the end near the methyl gives 2-methylbutane."),
   TF("True or false: butane and 2-methylpropane are chain isomers.", True, "Both are C₄H₁₀ with different carbon skeletons.")],
  [N("How many structural isomers does pentane's formula C₅H₁₂ have (counting pentane itself)?", 3, "Pentane, 2-methylbutane and 2,2-dimethylpropane.", 0, None, difficulty=2),
   M("Which compound shows E/Z isomerism?", "but-2-ene", ["propene", "ethene", "2-methylpropene"], "Each carbon of the double bond in but-2-ene carries two different groups (H and CH₃).")],
  P("Consider the molecules A: CH₃CH₂CH₂CH₂OH, B: CH₃CH(OH)CH₂CH₃, C: (CH₃)₃COH and D: CH₃CH=CHCH₃.",
    [pm("Which pair are position isomers?", "A and B", ["A and C", "B and D", "C and D"], "Both are straight-chain butanols with OH on different carbons.", 1),
     pm("Which is a tertiary alcohol?", "C", ["A", "B", "D"], "The carbon bearing OH is bonded to three carbon atoms.", 1),
     pt("Compound D shows E/Z isomerism.", True, "Each double-bonded carbon has H and CH₃.", 1),
     po("State why 2-methylpropan-2-ol cannot show optical isomerism.", "It has no chiral carbon: the carbon bearing OH has three identical CH₃ groups, so it does not have four different groups.", "1 mark: no chiral carbon / not four different groups attached.", 2)], figure=ez_fig()),
  [M("The general formula of the alkenes is", "CₙH₂ₙ", ["CₙH₂ₙ₊₂", "CₙH₂ₙ₋₂", "CₙHₙ"], "One double bond: two fewer hydrogens than the alkane."),
   M("The functional group of an alcohol is", "–OH", ["–COOH", "C=C", "–Cl"], "Hydroxyl group."),
   M("Isomers have", "the same molecular formula but different structures", ["the same structure", "different molecular formulae", "the same boiling point"], "By definition."),
   M("A chiral carbon has", "four different groups attached", ["a double bond", "two identical groups", "three groups"], "It has non-superimposable mirror images."),
   M("The name of CH₃CH₂CH₂Br is", "1-bromopropane", ["2-bromopropane", "bromopropene", "propyl bromine"], "Br on carbon 1 of a three-carbon chain.")],
  (ez_fig(), "Z-but-2-ene (cis) and E-but-2-ene (trans): the methyl groups on the same side or on opposite sides of the double bond.", "Two drawings of but-2-ene with a double bond; in one the two methyl groups point the same way and in the other in opposite directions."),
  notes=["Optical isomerism and the Cahn–Ingold–Prelog rules are only introduced; detailed treatment (R/S) is not included."], minutes=40, prereq=["lower6-chemistry-bonding"])

# ---- 6.2 alkanes and alkenes
v_co2 = 3 * 20; v_o2_used = 5 * 20; v_left = 150 - v_o2_used; v_total = v_co2 + v_left
assert v_co2 == 60 and v_o2_used == 100 and v_left == 50 and v_total == 110
m_co2 = 11.0 / 44.0 * 3 * 44.0; assert abs(m_co2 - 33.0) < 1e-9
m_eth = 2.80 / 28.0 * 46.0 * 0.70; assert abs(m_eth - 3.22) < 1e-9
n_dod = 17.0 / 170.0; m_c2h4 = n_dod * 2 * 28.0; V_c2h4 = n_dod * 2 * 24.0; assert abs(m_c2h4 - 5.60) < 1e-9 and abs(V_c2h4 - 4.80) < 1e-9
def mech_fig():
    it = [RECT(10, 40, 120, 60, fill="lightyellow"), T(70, 66, "CH₂=CH₂", 14, bold=True), T(70, 86, "+ H–Br", 13),
          LINE(135, 70, 175, 70, width=3, arrow="end"), T(155, 55, "slow", 11),
          RECT(180, 40, 110, 60, fill="lightorange"), T(235, 62, "CH₃–CH₂⁺", 14, bold=True), T(235, 84, "+ Br⁻", 13),
          LINE(295, 70, 330, 70, width=3, arrow="end"), T(312, 55, "fast", 11),
          RECT(335, 40, 80, 60, fill="lightgreen"), T(375, 76, "CH₃CH₂Br", 13, bold=True),
          T(210, 150, "carbocation intermediate", 13), T(210, 180, "H⁺ is attacked by the π electrons (an electrophile)", 13)]
    return shapes(it, 425, 205)
lesson(ch, "hydrocarbons", "Alkanes and alkenes: reactions and mechanisms",
  ["Describe the combustion, cracking and free-radical substitution of alkanes",
   "Describe and explain electrophilic addition to alkenes, including Markovnikov's rule, and the test for unsaturation",
   "Describe addition polymerisation and calculate masses and volumes in these reactions"],
  [("definition", "Alkanes",
    "Alkanes (C_nH₂ₙ₊₂) are **saturated**, with strong, non-polar C–C and C–H bonds: unreactive except for combustion and substitution with halogens in UV light. They are obtained from crude oil by fractional distillation; heavier fractions are **cracked** (heat/catalyst) into shorter alkanes and **alkenes**.\n\n"
    "Complete combustion: CH₄ + 2O₂ → CO₂ + 2H₂O. In limited oxygen **carbon monoxide** (toxic: it binds to haemoglobin) or carbon (soot) forms."),
   ("methode", "Free-radical substitution (CH₄ + Cl₂, UV)",
    "- **Initiation**: Cl₂ → 2Cl• (homolytic fission by UV).\n- **Propagation**: CH₄ + Cl• → •CH₃ + HCl, then •CH₃ + Cl₂ → CH₃Cl + Cl• (a chain reaction).\n- **Termination**: two radicals combine: Cl• + Cl• → Cl₂; •CH₃ + Cl• → CH₃Cl; •CH₃ + •CH₃ → C₂H₆.\n\nA mixture of products (CH₂Cl₂, CHCl₃, CCl₄) forms because further substitution is possible."),
   ("retenir", "Alkenes and electrophilic addition",
    "Alkenes (C_nH₂ₙ) contain a C=C: a σ bond and an electron-rich π bond that is attacked by **electrophiles** (electron-pair acceptors). Reactions: with Br₂ (the orange bromine water is **decolourised**: test for C=C), with HBr (→ bromoalkane), with steam/H₃PO₄ (→ alcohol), with H₂/Ni (→ alkane).\n\n**Markovnikov's rule**: in the addition of HBr to propene the major product is 2-bromopropane, via the more stable (secondary) carbocation (tertiary > secondary > primary). **Addition polymerisation**: many alkene molecules join to give poly(ethene), poly(propene), PVC."),
   ("pieges", "Common mistakes",
    "- Calling alkane reactions with Cl₂ 'addition' (it is substitution via radicals).\n- Drawing the curly arrow from the H–Br bond instead of from the π bond.\n- Writing the monomer instead of the repeat unit in a polymer (poly(ethene) is –[CH₂–CH₂]ₙ–, with no C=C).\n- Using bromine water as a test with alkanes: it stays orange.")],
  [("Example 1 — Gas volumes in combustion", "20 cm³ of propane is burnt in 150 cm³ of oxygen at room conditions: C₃H₈ + 5O₂ → 3CO₂ + 4H₂O(l). Find the gas volume after cooling.",
    ["CO₂ formed = 3 × 20 = 60 cm³; O₂ used = 5 × 20 = 100 cm³.", "O₂ left = 150 − 100 = 50 cm³.", "Total gas = 60 + 50 = 110 cm³ (water is liquid and takes no volume)."], "110 cm³ (60 cm³ CO₂ and 50 cm³ O₂)"),
   ("Example 2 — Markovnikov's rule", "Predict and explain the major product of HBr + CH₃CH=CH₂.",
    ["H⁺ adds to either carbon: adding to C-1 gives CH₃–CH⁺–CH₃ (secondary); adding to C-2 gives CH₃CH₂CH₂⁺ (primary).", "Alkyl groups push electrons towards the positive carbon, so the secondary carbocation is more stable and is formed faster.", "Br⁻ attacks it: the major product is **2-bromopropane**, CH₃CHBrCH₃."], "2-bromopropane (major)")],
  [N("What mass of CO₂ is formed by complete combustion of 11.0 g of propane, C₃H₈ (A_r: C 12, H 1, O 16)? (g)", m_co2, "n(C₃H₈) = 11.0/44.0 = 0.250 mol; n(CO₂) = 0.750 mol; m = 0.750 × 44.0 = 33.0 g.", 0.1, "g"),
   M("Which reagent decolourises bromine water at room temperature in the dark?", "ethene", ["methane", "hexane", "ethane"], "Ethene is unsaturated and adds bromine."),
   TF("True or false: the reaction of methane with chlorine in UV light is an addition reaction.", False, "It is a free-radical substitution: H is replaced by Cl.")],
  [N("2.80 g of ethene is converted to ethanol by hydration with a yield of 70 % (C₂H₄ + H₂O → C₂H₅OH). Find the mass of ethanol (g, 3 s.f.).", sg(m_eth, 3), "n(C₂H₄) = 2.80/28.0 = 0.100 mol; theoretical ethanol = 4.60 g; 70 % of 4.60 = 3.22 g.", 0.02, "g", difficulty=2),
   M("Why is the major product of HBr with propene 2-bromopropane?", "the secondary carbocation is more stable than the primary one", ["Br is more electronegative than C", "propene has a primary carbon", "H⁺ cannot attack C-2"], "Markovnikov's rule is explained by carbocation stability.")],
  P("Dodecane (C₁₂H₂₆, M = 170) can be cracked: C₁₂H₂₆ → C₈H₁₈ + 2C₂H₄. 17.0 g of dodecane is cracked completely (A_r: C 12.0, H 1.0; molar volume 24.0 dm³).",
    [pn("Moles of dodecane.", 0.100, "17.0/170 = 0.100 mol.", 0.001, "mol", 1),
     pn("Mass of ethene produced (g, 3 s.f.).", sg(m_c2h4, 3), "n(C₂H₄) = 2 × 0.100 = 0.200 mol; m = 0.200 × 28.0 = 5.60 g.", 0.02, "g", 1),
     pn("Volume of ethene at rtp (dm³, 3 s.f.).", sg(V_c2h4, 3), "0.200 × 24.0 = 4.80 dm³.", 0.02, "dm³", 1),
     pm("The alkene product can be identified because it", "decolourises bromine water", ["burns with a clean flame", "reacts with sodium", "turns litmus red"], "Alkenes react by electrophilic addition with Br₂.", 1)], figure=mech_fig()),
  [M("The mechanism for the reaction of an alkene with HBr is", "electrophilic addition", ["free-radical substitution", "nucleophilic substitution", "elimination"], "The π bond attacks the electrophile H⁺."),
   M("The initiation step of the chlorination of methane is", "Cl₂ → 2Cl•", ["CH₄ → •CH₃ + H•", "Cl• + CH₄ → HCl + •CH₃", "2Cl• → Cl₂"], "UV light splits Cl₂ homolytically."),
   M("Carbon monoxide is dangerous because it", "binds to haemoglobin and prevents oxygen transport", ["is acidic", "is flammable only", "dissolves in blood"], "Incomplete combustion in poorly ventilated rooms can be fatal."),
   M("The repeat unit of poly(ethene) is", "–CH₂–CH₂–", ["–CH=CH–", "–CH₃", "–CH₂Cl"], "The double bond is used in polymerisation."),
   M("Cracking of long-chain alkanes is used to make", "shorter alkanes and alkenes", ["longer alkanes", "alcohols only", "carbon dioxide only"], "Heavy fractions are in less demand than petrol and alkenes for plastics.")],
  (mech_fig(), "Electrophilic addition of HBr to ethene through a carbocation intermediate.", "Three boxes joined by arrows: ethene plus hydrogen bromide, a carbocation with a bromide ion, then bromoethane."),
  notes=["Cameroonian context (refinery at Limbe, plastic waste) is not included until verified.", "Detailed curly-arrow drawings are not reproduced; the mechanism is given as a scheme."], minutes=40, prereq=["lower6-chemistry-isomers", "lower6-chemistry-enthalpy"])

# ---- 6.3 halogenoalkanes and alcohols
m_et = 23.0 / 46.0 * 28.0 * 0.70; assert abs(m_et - 9.8) < 1e-9
m_gl = 180.0 / 180.0 * 2 * 46.0 * 0.70; assert abs(m_gl - 64.4) < 1e-9
def alc_fig():
    it = [RECT(10, 20, 100, 36, fill="lightyellow"), T(60, 43, "primary alcohol", 12), RECT(150, 20, 90, 36, fill="lightorange"), T(195, 43, "aldehyde", 12), RECT(290, 20, 110, 36, fill="lightgreen"), T(345, 43, "carboxylic acid", 12),
          LINE(112, 38, 148, 38, width=2, arrow="end"), LINE(242, 38, 288, 38, width=2, arrow="end"),
          RECT(10, 90, 100, 36, fill="lightyellow"), T(60, 113, "secondary alcohol", 12), RECT(150, 90, 90, 36, fill="lightorange"), T(195, 113, "ketone", 12), LINE(112, 108, 148, 108, width=2, arrow="end"),
          RECT(10, 160, 100, 36, fill="lightyellow"), T(60, 183, "tertiary alcohol", 12), T(250, 183, "not oxidised", 13, color="red"),
          T(250, 213, "oxidising agent: K₂Cr₂O₇ / H⁺ (orange → green), heat", 12)]
    return shapes(it, 410, 235)
lesson(ch, "halogenoalcohols", "Halogenoalkanes and alcohols",
  ["Describe nucleophilic substitution and elimination of halogenoalkanes and the trend in reactivity C–Cl, C–Br, C–I",
   "Classify alcohols and describe their oxidation, dehydration and combustion",
   "Describe the manufacture of ethanol by hydration of ethene and by fermentation and calculate yields"],
  [("definition", "Halogenoalkanes",
    "In a halogenoalkane the C–X bond is **polar** (carbon δ⁺), so it is attacked by **nucleophiles** (lone-pair donors such as OH⁻, CN⁻, NH₃). **Nucleophilic substitution**: R–X + OH⁻ (aq, warm) → R–OH + X⁻ (alcohol); with KCN → nitrile (lengthens the chain); with excess NH₃ → amine.\n\n"
    "With **ethanolic** KOH (hot), **elimination** of HX gives an alkene. Reactivity: **C–I > C–Br > C–Cl** because the bond enthalpies fall (C–Cl 346, C–Br 290, C–I 228 kJ mol⁻¹): weaker bonds break more easily."),
   ("retenir", "Mechanisms and tests",
    "Primary halogenoalkanes react by **S_N2** (one step, nucleophile attacks as the halide leaves); tertiary ones by **S_N1** (carbocation intermediate). To identify the halogen: warm with NaOH, acidify with dilute HNO₃, add AgNO₃(aq): **AgCl white, AgBr cream, AgI yellow** precipitate.\n\nCFCs (chlorofluorocarbons) release Cl• in the upper atmosphere and destroy ozone: they are being phased out."),
   ("formule", "Alcohols",
    "Alcohols are **primary**, **secondary** or **tertiary** depending on how many carbon groups are attached to the carbon bearing –OH. With acidified K₂Cr₂O₇ (heat): primary → aldehyde (distil off) → carboxylic acid (reflux); secondary → ketone; tertiary → no reaction. The colour changes from orange to green.\n\n"
    "**Dehydration** (conc. H₂SO₄ or hot Al₂O₃): alcohol → alkene + H₂O. **Combustion**; reaction with sodium (H₂ + sodium alkoxide); esterification with carboxylic acids (Upper Sixth).",
    None),
   ("pieges", "Common mistakes",
    "- Using aqueous KOH when ethanolic KOH is needed for elimination, and vice versa.\n- Adding AgNO₃ without first acidifying: hydroxide ions would give a brown precipitate of Ag₂O.\n- Saying the C–F bond is the most reactive: C–F is the strongest.\n- Thinking tertiary alcohols are oxidised by acidified dichromate (they are not).")],
  [("Example 1 — Identifying a halogenoalkane", "A halogenoalkane is warmed with aqueous NaOH, acidified with dilute nitric acid, and silver nitrate solution is added. A cream precipitate forms. Which halogen is present, and why would the bromo compound react faster than the chloro one?",
    ["A cream precipitate is silver bromide: the compound is a bromoalkane (R–Br).", "Hydrolysis breaks the C–X bond; C–Br (290 kJ mol⁻¹) is weaker than C–Cl (346 kJ mol⁻¹).", "So the bromoalkane reacts faster than the chloroalkane (the polarity of the bond is not the deciding factor)."], "R–Br; the weaker C–Br bond breaks faster"),
   ("Example 2 — Ethanol by fermentation", "Glucose ferments to ethanol: C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂ (palm sap and other sugary juices ferment in this way). Find the mass of ethanol from 180 g of glucose with a 70 % yield (A_r: C 12, H 1, O 16).",
    ["M(glucose) = 180 g mol⁻¹, so n = 180/180 = 1.00 mol.", "Theoretical n(ethanol) = 2.00 mol = 2.00 × 46.0 = 92.0 g.", "Actual mass = 70 % × 92.0 = 64.4 g."], "64.4 g of ethanol")],
  [M("Which precipitate forms with AgNO₃(aq) after hydrolysis of an iodoalkane?", "yellow AgI", ["white AgCl", "cream AgBr", "black Ag₂O"], "Iodide gives a yellow precipitate."),
   M("Propan-2-ol is a", "secondary alcohol", ["primary alcohol", "tertiary alcohol", "carboxylic acid"], "The carbon bearing –OH is bonded to two other carbons."),
   TF("True or false: a tertiary alcohol is oxidised by acidified potassium dichromate(VI).", False, "A tertiary alcohol has no H on the carbon bearing OH, so it resists oxidation under these conditions.")],
  [N("23.0 g of ethanol is dehydrated to ethene (C₂H₅OH → C₂H₄ + H₂O) with a 70 % yield. Find the mass of ethene (g, 3 s.f.).", sg(m_et, 3), "n(ethanol) = 23.0/46.0 = 0.500 mol; theoretical ethene = 14.0 g; × 0.70 = 9.80 g.", 0.05, "g", difficulty=2),
   M("Which is the correct order of reactivity towards hydrolysis?", "R–I > R–Br > R–Cl", ["R–Cl > R–Br > R–I", "R–Br > R–I > R–Cl", "all the same"], "Reactivity follows the weakness of the C–X bond.")],
  P("Butan-2-ol, CH₃CH(OH)CH₂CH₃, and 2-methylpropan-2-ol, (CH₃)₃COH, are heated separately with acidified potassium dichromate(VI).",
    [pm("Butan-2-ol is oxidised to", "butanone", ["butanal", "butanoic acid", "but-2-ene"], "A secondary alcohol gives a ketone.", 1),
     pm("What colour change is seen with butan-2-ol?", "orange to green", ["green to orange", "colourless to purple", "no change"], "Cr₂O₇²⁻ (orange) is reduced to Cr³⁺ (green).", 1),
     pt("2-methylpropan-2-ol also changes the colour of the dichromate solution.", False, "Tertiary alcohols are not oxidised: the solution stays orange.", 1),
     po("Name the type of reaction when an alcohol forms an alkene with concentrated sulfuric acid and give the other product.", "Dehydration (an elimination reaction); the other product is water.", "1 mark: elimination/dehydration; 1 mark: water.", 2)], figure=alc_fig()),
  [M("Nucleophilic substitution of a bromoalkane with aqueous NaOH gives", "an alcohol", ["an alkene", "a carboxylic acid", "an alkane"], "OH⁻ replaces Br⁻."),
   M("Elimination of HBr from a bromoalkane needs", "ethanolic KOH, heat", ["aqueous KOH, cold", "dilute acid", "UV light"], "The alkoxide/ethanolic base removes H and Br."),
   M("Oxidation of a primary alcohol with excess oxidant under reflux gives", "a carboxylic acid", ["a ketone", "an alkene", "an alkane"], "First the aldehyde forms, then it is oxidised further."),
   M("CFCs are harmful because they", "release chlorine radicals that destroy ozone", ["are acidic", "are poisonous gases when burnt", "dissolve in rain only"], "Chlorine radicals catalyse ozone breakdown."),
   M("The best nucleophile among these is", "OH⁻", ["H₃O⁺", "CH₄", "Na⁺"], "A nucleophile has a lone pair to donate.")],
  (alc_fig(), "Oxidation of primary, secondary and tertiary alcohols with acidified potassium dichromate(VI).", "A flow chart: primary alcohol to aldehyde to carboxylic acid; secondary alcohol to ketone; tertiary alcohol not oxidised."),
  notes=[DATA, "C–X bond enthalpies (346, 290, 228 kJ mol⁻¹) are textbook values; check the data booklet.", "Fermentation of palm sap is mentioned as an example only; the yield is illustrative (real yields differ).", "S_N1/S_N2 are introduced qualitatively; the full curly-arrow mechanisms are not drawn."], minutes=40, prereq=["lower6-chemistry-hydrocarbons"])
p.write()
