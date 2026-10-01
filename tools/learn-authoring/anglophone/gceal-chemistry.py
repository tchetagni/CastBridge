"""Generator: gceal-chemistry (Chemistry — GCE A Level, Upper Sixth, Cameroon GCE Board). python3 gceal-chemistry.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pc import *

# Ar used: H 1.0, C 12.0, N 14.0, O 16.0, Na 23.0, Mg 24.0, Al 27.0, S 32.0, Cl 35.5, K 39.0, Ca 40.0, Fe 56.0, Cu 63.5, Zn 65.0, Ag 108.0, Ba 137.0
# R = 8.31 J/(mol K); F = 96500 C/mol; molar volume 24.0 dm3 (rtp); Kw = 1.0e-14 (25 C)
R_, F_ = 8.31, 96500.0
p = Pack("gceal-chemistry", "Chemistry — GCE A Level", level="Upper Sixth", subject="chemistry", cursus="secondary", exam="GCE-AL", series=[],
         description="Upper Sixth chemistry revision for the GCE Advanced Level: electrode potentials, equilibria, energetics, Groups 2 and 7, period 3, transition metals, carbonyls, "
                     "carboxylic acids, aromatic chemistry, amines and polymers, spectroscopy, industrial and environmental chemistry and practical skills, with a mock paper. Draft.",
         programRef="Cameroon GCE Board — Advanced Level Chemistry syllabus (Upper Sixth part) — to be checked against the official texts",
         source_note="Original exercise, GCE Advanced Level style")
SYL = "GCE Advanced Level Chemistry — %s (to be checked against the official syllabus)"
DATA = "Data used: Ar H 1.0, C 12.0, N 14.0, O 16.0, Na 23.0, Mg 24.0, Al 27.0, S 32.0, Cl 35.5, K 39.0, Ca 40.0, Fe 56.0, Cu 63.5, Zn 65.0, Ag 108.0, Ba 137.0; R = 8.31 J mol⁻¹ K⁻¹; F = 96 500 C mol⁻¹; molar volume 24.0 dm³ at room temperature and pressure; K_w = 1.0 × 10⁻¹⁴ at 25 °C."

# =============================================================== 1. PHYSICAL CHEMISTRY
ch = p.chapter("ch-physical", "Electrode potentials, equilibria and energetics", SYL % "physical chemistry")

# ---- 1.1 electrode potentials
E_ZnCu = 0.34 - (-0.76); dG = -2 * F_ * E_ZnCu / 1000; logK = 2 * E_ZnCu / 0.0592; E_nern = E_ZnCu - 0.0592 / 2 * math.log10(0.010 / 1.0)
assert abs(E_ZnCu - 1.10) < 1e-9 and abs(dG + 212.3) < 0.1 and abs(logK - 37.16) < 0.02 and abs(E_nern - 1.159) < 0.001
E_FeI = 0.77 - 0.54; E_CuAg = 0.80 - 0.34; assert abs(E_FeI - 0.23) < 1e-9 and abs(E_CuAg - 0.46) < 1e-9
def cell_fig():
    it = [RECT(40, 100, 110, 100, fill="lightblue"), RECT(250, 100, 110, 100, fill="lightorange"),
          LINE(90, 60, 90, 170, width=6, color="grey"), LINE(310, 60, 310, 170, width=6, color="brown"),
          T(95, 215, "Zn | ZnSO₄(aq)", 12), T(305, 215, "Cu | CuSO₄(aq)", 12),
          LINE(90, 60, 90, 25, width=2), LINE(310, 60, 310, 25, width=2), LINE(90, 25, 175, 25, width=2), LINE(225, 25, 310, 25, width=2),
          CIRCLE(200, 25, 24, fill="white"), T(200, 30, "V", 16, bold=True), T(200, 70, "1.10 V", 13),
          PATH("M 120 100 L 120 78 L 280 78 L 280 100", stroke="grey", width=8), T(200, 92, "salt bridge", 11, color="white"),
          T(60, 12, "negative (−)", 11, anchor="start"), T(260, 12, "positive (+)", 11, anchor="start")]
    return shapes(it, 400, 235)
lesson(ch, "electrode", "Electrode potentials and electrochemical cells",
  ["Define standard electrode potential E° using the standard hydrogen electrode and write cell diagrams",
   "Calculate E°cell, predict the feasibility of redox reactions and explain the limits of such predictions",
   "Relate E°cell to ΔG° and K, and describe the effect of concentration (Nernst equation) and common cells"],
  [("definition", "Standard electrode potential",
    "A **half-cell** is a metal in contact with a solution of its ions (or an inert electrode with two species in solution). The **standard electrode potential E°** is the emf of a half-cell connected to a **standard hydrogen electrode** (H₂ at 100 kPa, 1 mol dm⁻³ H⁺, platinum, 298 K) under standard conditions (1 mol dm⁻³ for all ions, 298 K): E°(H⁺/H₂) = 0.00 V.\n\n"
    "More **positive** E° means the species on the left (the oxidised form) is a stronger **oxidising agent**; more negative means the metal is a stronger reducing agent."),
   ("formule", "Cells and feasibility",
    "In a cell, the half-cell with the more negative E° is the **negative electrode** (oxidation, anode), the more positive is the **positive electrode** (reduction, cathode): **E°cell = E°(positive) − E°(negative)**. A reaction is **feasible** if E°cell > 0.\n\nValues at 298 K (V): Mg²⁺/Mg −2.37; Al³⁺/Al −1.66; Zn²⁺/Zn −0.76; Fe²⁺/Fe −0.44; H⁺/H₂ 0.00; Cu²⁺/Cu +0.34; I₂/I⁻ +0.54; Fe³⁺/Fe²⁺ +0.77; Ag⁺/Ag +0.80; Br₂/Br⁻ +1.07; Cl₂/Cl⁻ +1.36; Cr₂O₇²⁻/Cr³⁺ +1.33; MnO₄⁻/Mn²⁺ +1.51.",
    r"E^{\circ}_{cell}=E^{\circ}_{positive}-E^{\circ}_{negative}"),
   ("formule", "Energy, equilibrium and concentration",
    "**ΔG° = −nFE°cell** (n = moles of electrons in the equation); a positive E°cell means a negative ΔG° (spontaneous). Also **ln K = nFE°/RT**, i.e. at 298 K lg K = nE°/0.0592.\n\n"
    "For non-standard concentrations the **Nernst equation** at 298 K gives **E = E° − (0.0592/n) lg Q**, where Q is the reaction quotient (as for K but with the actual concentrations).",
    r"\Delta G^{\circ}=-nFE^{\circ}\qquad E=E^{\circ}-\frac{0.0592}{n}\log Q"),
   ("pieges", "Common mistakes",
    "- Multiplying E° by the stoichiometric coefficients when balancing: E° does **not** change.\n- Subtracting the wrong way: positive electrode minus negative electrode.\n- Saying 'feasible' means 'fast': a positive E°cell says nothing about the rate (kinetic barrier).\n- Applying standard values to non-standard conditions without commenting (e.g. concentrations, temperature).")],
  [("Example 1 — The Daniell cell", "A cell has a zinc electrode in 1.0 mol dm⁻³ ZnSO₄ and a copper electrode in 1.0 mol dm⁻³ CuSO₄ joined by a salt bridge. Find E°cell and ΔG° (E°: Zn²⁺/Zn −0.76 V, Cu²⁺/Cu +0.34 V).",
    ["Zinc is more negative, so zinc is the negative electrode (Zn → Zn²⁺ + 2e⁻) and copper the positive (Cu²⁺ + 2e⁻ → Cu).", "E°cell = +0.34 − (−0.76) = +1.10 V.", "ΔG° = −nFE° = −2 × 96 500 × 1.10 = −2.12 × 10⁵ J = −212 kJ mol⁻¹."], "E°cell = +1.10 V; ΔG° = −212 kJ mol⁻¹"),
   ("Example 2 — Feasibility and Nernst", "Is it feasible for Fe³⁺ to oxidise I⁻ to I₂? What is E for the Zn/Cu cell when [Zn²⁺] = 0.010 mol dm⁻³ and [Cu²⁺] = 1.0 mol dm⁻³?",
    ["2Fe³⁺ + 2I⁻ → 2Fe²⁺ + I₂: E°cell = 0.77 − 0.54 = +0.23 V > 0, feasible.", "Nernst: Q = [Zn²⁺]/[Cu²⁺] = 0.010; E = 1.10 − (0.0592/2) lg 0.010 = 1.10 + 0.0592 = 1.16 V.", "A lower [Zn²⁺] makes the cell reaction more favourable, so E rises, as Le Chatelier predicts."], "Feasible (+0.23 V); E = 1.16 V")],
  [N("Calculate E°cell for a cell made from Cu²⁺/Cu (+0.34 V) and Ag⁺/Ag (+0.80 V) (V).", E_CuAg, "E°cell = 0.80 − 0.34 = +0.46 V (silver is the positive electrode).", 0.005, "V"),
   M("Which of these is the strongest reducing agent (E° in V: Mg²⁺/Mg −2.37, Zn²⁺/Zn −0.76, Cu²⁺/Cu +0.34)?", "Mg", ["Zn", "Cu", "Mg²⁺"], "The most negative E° belongs to the metal that loses electrons most readily."),
   TF("True or false: multiplying a half-equation by 2 doubles its E° value.", False, "E° is an intensive property: it does not depend on the amount of substance.")],
  [N("Calculate ΔG° (kJ mol⁻¹, 3 s.f.) for Zn + Cu²⁺ → Zn²⁺ + Cu, using E°cell = 1.10 V and F = 96 500 C mol⁻¹.", sg(dG, 3), "ΔG° = −2 × 96 500 × 1.10 = −212 000 J mol⁻¹ = −212 kJ mol⁻¹.", 0.5, "kJ/mol", difficulty=2),
   M("Cl₂(aq) is added to a solution containing Br⁻. A reaction occurs because", "E°(Cl₂/Cl⁻) is more positive than E°(Br₂/Br⁻)", ["E°(Br₂/Br⁻) is more positive", "Cl₂ is a liquid", "Br⁻ is larger than Cl⁻"], "E°cell = 1.36 − 1.07 = +0.29 V > 0: Cl₂ oxidises Br⁻ to Br₂.")],
  P("A cell is set up from Fe³⁺/Fe²⁺ (E° = +0.77 V) and Cr₂O₇²⁻/Cr³⁺ (E° = +1.33 V) half-cells in acid (Pt electrodes). F = 96 500 C mol⁻¹.",
    [pm("The positive electrode is the", "Cr₂O₇²⁻/Cr³⁺ half-cell", ["Fe³⁺/Fe²⁺ half-cell", "salt bridge", "voltmeter"], "It has the more positive E°.", 1),
     pn("E°cell (V).", 0.56, "1.33 − 0.77 = +0.56 V.", 0.005, "V", 1),
     pn("ΔG° for the reaction in which 6 electrons are transferred (Cr₂O₇²⁻ + 14H⁺ + 6Fe²⁺ → 2Cr³⁺ + 7H₂O + 6Fe³⁺) in kJ mol⁻¹ (3 s.f.).", sg(-6 * F_ * 0.56 / 1000, 3), "ΔG° = −6 × 96 500 × 0.56 = −3.24 × 10⁵ J = −324 kJ mol⁻¹.", 1, "kJ/mol", 2),
     po("Why might a reaction with a positive E°cell still not occur at a noticeable rate?", "E°cell only shows that the reaction is thermodynamically feasible; it may have a high activation energy, so the rate can be very slow; non-standard conditions can also change E.", "1 mark: feasibility does not give the rate (kinetics/activation energy); 1 mark: conditions differ from standard.", 2)], figure=cell_fig()),
  [M("The standard hydrogen electrode uses", "H₂ at 100 kPa, 1 mol dm⁻³ H⁺, platinum", ["Zn in 1 mol dm⁻³ Zn²⁺", "O₂ at 1 atm in NaOH", "Cu in CuSO₄"], "It is the reference half-cell, E° = 0.00 V."),
   M("In a galvanic cell, oxidation takes place at the", "negative electrode (anode)", ["positive electrode", "salt bridge", "voltmeter"], "The metal with the more negative E° loses electrons."),
   M("The function of the salt bridge is to", "complete the circuit by allowing ion movement", ["supply electrons", "increase the emf", "react with the electrodes"], "It balances charge without mixing the solutions."),
   M("A positive E°cell means that ΔG° is", "negative", ["positive", "zero", "equal to F"], "ΔG° = −nFE°."),
   M("Increasing [Cu²⁺] in the Zn/Cu cell", "increases the emf", ["decreases the emf", "has no effect", "reverses the cell"], "Q decreases, so E increases (Nernst/Le Chatelier).")],
  (cell_fig(), "The zinc–copper cell: zinc is the negative electrode, copper the positive electrode (E°cell = +1.10 V).", "Two beakers with a zinc electrode in zinc sulfate and a copper electrode in copper sulfate joined by a salt bridge and a voltmeter reading 1.10 volts."),
  notes=[DATA, "E° values are standard 298 K values; check against the board's data booklet. The Nernst equation and the link to K may go beyond the syllabus: flagged for the teacher.", "Cell types (fuel cells, lead–acid battery) are not covered here."], minutes=40)

# ---- 1.2 Kp and Ksp
x_d = 0.30; ntot = 0.70 + 0.60; pN2O4 = 0.70 / ntot * 2.0; pNO2 = 0.60 / ntot * 2.0; Kp1 = pNO2 ** 2 / pN2O4
assert abs(pN2O4 - 1.0769) < 0.001 and abs(pNO2 - 0.9231) < 0.001 and abs(Kp1 - 0.7913) < 0.001
Ksp_AgCl = 1.8e-10; s_AgCl = math.sqrt(Ksp_AgCl); s_ci = Ksp_AgCl / 0.10; Ksp_PbI2 = 4 * (1.3e-3) ** 3
assert abs(s_AgCl - 1.342e-5) < 1e-8 and abs(s_ci - 1.8e-9) < 1e-15 and abs(Ksp_PbI2 - 8.79e-9) < 0.01e-9
Qpp = (1.0e-5) ** 2; assert Qpp < Ksp_AgCl
Kp2 = 2 ** 2 / (1.0 * 3.0 ** 3) if False else None
def ksp_fig():
    return bars([("s in water", 1.34e-5 * 1e5, "blue"), ("s in 0.1 M NaCl", 1.8e-9 * 1e5, "orange")], unit="solubility of AgCl (× 10⁻⁵ mol/dm³)", w=380, h=240)
lesson(ch, "kpksp", "Kp, solubility product and the common-ion effect",
  ["Write K_p expressions, calculate partial pressures and K_p for gaseous equilibria",
   "Write K_sp expressions and calculate K_sp from solubility and solubility from K_sp",
   "Predict precipitation and explain the common-ion effect"],
  [("formule", "K_p for gaseous equilibria",
    "For a gas mixture the **partial pressure** of A is p_A = (mole fraction of A) × p_total. For aA(g) + bB(g) ⇌ cC(g) + dD(g): **K_p = p_C^c p_D^d / (p_A^a p_B^b)** with the units following from the expression (e.g. atm or Pa to a power). K_p depends only on temperature.\n\n"
    "Relation to K_c: K_p = K_c (RT)^Δn, with Δn = moles of gaseous products − moles of gaseous reactants (SI units).",
    r"K_p=\frac{p_C^{\,c}\,p_D^{\,d}}{p_A^{\,a}\,p_B^{\,b}}\qquad K_p=K_c(RT)^{\Delta n}"),
   ("definition", "Solubility product",
    "For a sparingly soluble salt in equilibrium with its saturated solution, e.g. AgCl(s) ⇌ Ag⁺(aq) + Cl⁻(aq): **K_sp = [Ag⁺][Cl⁻]** (the solid is omitted). For PbI₂: K_sp = [Pb²⁺][I⁻]². If the solubility is s mol dm⁻³: AgCl: K_sp = s²; PbI₂: K_sp = s(2s)² = **4s³**; Mg(OH)₂: 4s³.\n\n"
    "A precipitate forms when the **ionic product Q exceeds K_sp**."),
   ("retenir", "Common-ion effect",
    "Adding a solution that contains one of the ions of the salt (e.g. NaCl to AgCl) shifts the equilibrium to the left (Le Chatelier): the solubility of the salt **decreases**, but K_sp is unchanged. The effect is used to precipitate ions completely (for example in gravimetric analysis) and explains why salts are less soluble in solutions of common ions."),
   ("pieges", "Common mistakes",
    "- Forgetting to take mole fractions or to use the total pressure when finding partial pressures.\n- Using the wrong power: for PbI₂ [I⁻] = 2s and is squared.\n- Not accounting for dilution when two solutions are mixed (concentrations are halved when equal volumes are mixed).\n- Saying K_sp changes in the presence of a common ion: only the solubility changes.")],
  [("Example 1 — K_p", "Initially 1.00 mol of N₂O₄ is heated in a vessel at a constant total pressure of 2.0 atm; at equilibrium 30 % has dissociated: N₂O₄ ⇌ 2NO₂. Find K_p.",
    ["At equilibrium: N₂O₄ 0.70 mol, NO₂ 0.60 mol, total 1.30 mol.", "Partial pressures: p(N₂O₄) = (0.70/1.30) × 2.0 = 1.08 atm; p(NO₂) = (0.60/1.30) × 2.0 = 0.923 atm.", "K_p = p(NO₂)²/p(N₂O₄) = 0.923²/1.08 = 0.79 atm."], "K_p ≈ 0.79 atm"),
   ("Example 2 — K_sp and precipitation", "K_sp(AgCl) = 1.8 × 10⁻¹⁰ mol² dm⁻⁶. Find its solubility in water and in 0.10 mol dm⁻³ NaCl. Will AgCl precipitate if 50 cm³ of 2.0 × 10⁻⁵ mol dm⁻³ AgNO₃ is mixed with 50 cm³ of 2.0 × 10⁻⁵ mol dm⁻³ NaCl?",
    ["In water: s = √K_sp = √(1.8 × 10⁻¹⁰) = 1.3 × 10⁻⁵ mol dm⁻³. In 0.10 M NaCl: [Cl⁻] ≈ 0.10 so s = K_sp/0.10 = 1.8 × 10⁻⁹ mol dm⁻³ (7000 times smaller).", "After mixing, each concentration is halved: [Ag⁺] = [Cl⁻] = 1.0 × 10⁻⁵ mol dm⁻³, so Q = 1.0 × 10⁻¹⁰.", "Q = 1.0 × 10⁻¹⁰ < K_sp = 1.8 × 10⁻¹⁰: no precipitate forms."], "s = 1.3 × 10⁻⁵ (water); 1.8 × 10⁻⁹ (0.10 M NaCl); no precipitate")],
  [M("The K_sp expression for Ca(OH)₂ is", "[Ca²⁺][OH⁻]²", ["[Ca²⁺][OH⁻]", "[Ca²⁺]²[OH⁻]", "[Ca(OH)₂]"], "The solid is omitted; [OH⁻] is squared."),
   N("A mixture contains 2.0 mol N₂ and 1.0 mol H₂ at a total pressure of 3.0 atm. Find p(N₂) (atm).", 2.0, "Mole fraction = 2.0/3.0; p = 2/3 × 3.0 = 2.0 atm.", 0.01, "atm"),
   TF("True or false: adding NaCl to a saturated solution of AgCl changes the value of K_sp.", False, "K_sp depends on temperature only; the solubility decreases (common-ion effect).")],
  [N("The solubility of PbI₂ is 1.3 × 10⁻³ mol dm⁻³. Calculate K_sp (units 10⁻⁹ mol³ dm⁻⁹, 2 s.f.).", sg(Ksp_PbI2 / 1e-9, 2), "K_sp = 4s³ = 4 × (1.3 × 10⁻³)³ = 8.8 × 10⁻⁹ mol³ dm⁻⁹.", 0.05, "× 10⁻⁹", difficulty=2),
   N("K_sp(AgCl) = 1.8 × 10⁻¹⁰. Find the solubility of AgCl in 0.10 mol dm⁻³ NaCl (10⁻⁹ mol dm⁻³).", s_ci / 1e-9, "s = K_sp/[Cl⁻] = 1.8 × 10⁻¹⁰/0.10 = 1.8 × 10⁻⁹ mol dm⁻³.", 0.01, "× 10⁻⁹ mol/dm³")],
  P("K_sp for Mg(OH)₂ is 1.8 × 10⁻¹¹ mol³ dm⁻⁹ at 25 °C (K_w = 1.0 × 10⁻¹⁴).",
    [pm("The solubility s is related to K_sp by", "K_sp = 4s³", ["K_sp = s²", "K_sp = 2s³", "K_sp = s³"], "[Mg²⁺] = s and [OH⁻] = 2s, so K_sp = s(2s)² = 4s³.", 1),
     pn("Solubility (10⁻⁴ mol dm⁻³, 3 s.f.).", sg((1.8e-11 / 4) ** (1 / 3) / 1e-4, 3), "s = (K_sp/4)^(1/3) = (4.5 × 10⁻¹²)^(1/3) = 1.65 × 10⁻⁴ mol dm⁻³.", 0.02, "× 10⁻⁴ mol/dm³", 2),
     pn("pH of the saturated solution (3 s.f.).", sg(14 + math.log10(2 * (1.8e-11 / 4) ** (1 / 3)), 3), "[OH⁻] = 2s = 3.30 × 10⁻⁴; pOH = 3.48; pH = 10.5.", 0.02, None, 2)]),
  [M("A precipitate forms when", "the ionic product exceeds K_sp", ["the ionic product equals zero", "K_sp exceeds the ionic product", "the solution is unsaturated"], "Q > K_sp means the solution is supersaturated."),
   M("The units of K_sp for Ag₂CrO₄ are", "mol³ dm⁻⁹", ["mol² dm⁻⁶", "mol dm⁻³", "dm⁶ mol⁻²"], "K_sp = [Ag⁺]²[CrO₄²⁻] has concentration to the power 3."),
   M("For an equilibrium with Δn = 0, K_p and K_c are", "equal", ["different by RT", "unrelated", "both zero"], "K_p = K_c(RT)⁰ = K_c."),
   M("The partial pressure of a gas in a mixture is", "mole fraction × total pressure", ["mass fraction × total pressure", "total pressure ÷ 2", "its volume"], "Dalton's law."),
   M("The solubility of AgCl is lowest in", "0.10 mol dm⁻³ NaCl", ["pure water", "0.10 mol dm⁻³ KNO₃", "0.10 mol dm⁻³ NaNO₃"], "Cl⁻ is the common ion.")],
  (ksp_fig(), "Solubility of AgCl in water and in 0.10 mol dm⁻³ NaCl: the common ion lowers the solubility (values × 10⁻⁵ mol dm⁻³).", "Two bars: a tall one for solubility in pure water and a very short one in sodium chloride solution."),
  notes=["K_sp(AgCl) = 1.8 × 10⁻¹⁰ is a typical textbook value; values in the board's data booklet may differ slightly.", "The bar chart shows 1.34 and 0.00018 (in units of 10⁻⁵ mol dm⁻³); the second bar is almost invisible by design."], minutes=40, prereq=["gceal-chemistry-electrode"])

# ---- 1.3 acid-base extras
Ka = 1.8e-5; pKa = -math.log10(Ka); Kw = 1e-14; Kb_ac = Kw / Ka; OH_ac = math.sqrt(Kb_ac * 0.10); pH_ac = 14 + math.log10(OH_ac)
OH_nh3 = math.sqrt(1.8e-5 * 0.10); pH_nh3 = 14 + math.log10(OH_nh3)
OH_eq = math.sqrt(Kb_ac * 0.0500); pH_eq = 14 + math.log10(OH_eq)
assert abs(pKa - 4.745) < 0.001 and abs(pH_ac - 8.872) < 0.001 and abs(pH_nh3 - 11.128) < 0.001 and abs(pH_eq - 8.72) < 0.01
def wa_pts():
    pts = []
    for v in [0, 2.5, 5, 7.5, 10, 12.5, 15, 20, 22.5, 24, 24.9, 25, 25.1, 26, 27.5, 30, 35, 40, 50]:
        if v == 0: y = -math.log10(math.sqrt(Ka * 0.10))
        elif v < 25: y = pKa + math.log10(v / (25 - v))
        elif v == 25: y = pH_eq
        else: y = 14 + math.log10(0.1 * (v - 25) / (25 + v))
        pts.append((v, y))
    return pts
def wa_fig():
    P_ = wa_pts()
    segs = [(a[0], a[1], b[0], b[1], "blue", False, None) for a, b in zip(P_, P_[1:])]
    return plot(0, 50, 0, 14, segments=segs + [(0, pKa, 12.5, pKa, "grey", True, None), (12.5, 0, 12.5, pKa, "grey", True, None)], points=[(12.5, pKa, "pH = pKa", "red"), (25, pH_eq, "equivalence", "green")], grid=5,
                xlabel="volume of NaOH / cm³", ylabel="pH", w=400, h=260)
lesson(ch, "acidbase", "Weak acids and bases: salts, titration curves and indicators",
  ["Calculate the pH of solutions of weak bases and of salts of weak acids (hydrolysis)",
   "Sketch and interpret the titration curve of a weak acid with a strong base, including the half-equivalence point",
   "Choose an indicator and relate pK_a to indicator range"],
  [("formule", "Weak bases and salts",
    "For a weak base B + H₂O ⇌ BH⁺ + OH⁻: K_b = [BH⁺][OH⁻]/[B], so [OH⁻] ≈ √(K_b c); then pOH = −lg[OH⁻] and pH = 14 − pOH. For a conjugate pair: **K_a × K_b = K_w**.\n\n"
    "The salt of a weak acid and a strong base (CH₃COONa) is **basic** in water because the anion hydrolyses: CH₃COO⁻ + H₂O ⇌ CH₃COOH + OH⁻, with K_b = K_w/K_a. The salt of a strong acid and a weak base (NH₄Cl) is acidic.",
    r"K_aK_b=K_w\qquad [\text{OH}^-]\approx\sqrt{K_bc}"),
   ("retenir", "Weak acid titrated with strong base",
    "The curve starts at a higher pH than for a strong acid, rises gradually in the **buffer region**, and has a steep rise at the equivalence point, which is above pH 7. At **half-equivalence** (half of the acid neutralised) [HA] = [A⁻], so **pH = pK_a**: a method to measure K_a. After the equivalence point the curve follows that of excess strong base."),
   ("methode", "Choosing an indicator",
    "An indicator is itself a weak acid HIn/In⁻ whose colours differ; the colour change range is about **pK_In ± 1**. The range must lie in the steep part of the titration curve:\n\n- strong acid + strong base: methyl orange (about 3.1–4.4) or phenolphthalein (8.3–10);\n- weak acid + strong base: **phenolphthalein**;\n- strong acid + weak base: **methyl orange**;\n- weak acid + weak base: no sharp change, use a pH meter."),
   ("pieges", "Common mistakes",
    "- Using K_a instead of K_b for the salt of a weak acid (the anion acts as a base).\n- Forgetting that mixing changes concentrations (the total volume rises).\n- Reading pH = 7 at equivalence for a weak acid titration.\n- Using the buffer equation at the equivalence point or beyond: there it is a salt solution or excess base.")],
  [("Example 1 — A salt of a weak acid", "Find the pH of 0.10 mol dm⁻³ sodium ethanoate (K_a(CH₃COOH) = 1.8 × 10⁻⁵, K_w = 1.0 × 10⁻¹⁴).",
    ["K_b = K_w/K_a = 1.0 × 10⁻¹⁴/1.8 × 10⁻⁵ = 5.56 × 10⁻¹⁰.", "[OH⁻] = √(K_b c) = √(5.56 × 10⁻¹¹) = 7.45 × 10⁻⁶ mol dm⁻³, pOH = 5.13.", "pH = 14 − 5.13 = 8.87 (basic)."], "pH = 8.87"),
   ("Example 2 — Titration of ethanoic acid", "25.0 cm³ of 0.100 mol dm⁻³ ethanoic acid is titrated with 0.100 mol dm⁻³ NaOH. Find the pH after 12.5 cm³ and at the equivalence point.",
    ["12.5 cm³ is half-equivalence: [HA] = [A⁻], so pH = pK_a = 4.74.", "At 25.0 cm³: n(A⁻) = 2.50 × 10⁻³ mol in 50.0 cm³: [A⁻] = 0.0500 mol dm⁻³; [OH⁻] = √(5.56 × 10⁻¹⁰ × 0.0500) = 5.27 × 10⁻⁶.", "pOH = 5.28, so pH = 8.72: phenolphthalein is suitable."], "pH 4.74 at half-equivalence; 8.72 at equivalence")],
  [N("Calculate K_b of the ethanoate ion (10⁻¹⁰ mol dm⁻³, 3 s.f.) from K_a = 1.8 × 10⁻⁵ and K_w = 1.0 × 10⁻¹⁴.", sg(Kb_ac / 1e-10, 3), "K_b = 1.0 × 10⁻¹⁴/1.8 × 10⁻⁵ = 5.56 × 10⁻¹⁰.", 0.2, "× 10⁻¹⁰"),
   M("A solution of sodium ethanoate has pH", "above 7", ["below 7", "exactly 7", "equal to pK_a"], "The anion hydrolyses to give OH⁻."),
   TF("True or false: at the half-equivalence point of a weak acid/strong base titration, pH = pK_a.", True, "[HA] = [A⁻], so lg([A⁻]/[HA]) = 0.")],
  [N("Calculate the pH of 0.10 mol dm⁻³ ammonia (K_b = 1.8 × 10⁻⁵ mol dm⁻³, 3 s.f.).", sg(pH_nh3, 3), "[OH⁻] = √(1.8 × 10⁻⁵ × 0.10) = 1.34 × 10⁻³; pOH = 2.87; pH = 11.1.", 0.02, None, difficulty=2),
   M("Which indicator is suitable for titrating ammonia solution with hydrochloric acid?", "methyl orange", ["phenolphthalein", "none", "starch"], "The equivalence point is acidic (NH₄⁺ salt), near pH 5.")],
  P("25.0 cm³ of 0.100 mol dm⁻³ ethanoic acid (K_a = 1.8 × 10⁻⁵) is titrated with 0.100 mol dm⁻³ NaOH.",
    [pn("Initial pH (3 s.f.).", sg(-math.log10(math.sqrt(Ka * 0.10)), 3), "[H⁺] = √(1.8 × 10⁻⁶) = 1.34 × 10⁻³; pH = 2.87.", 0.02, None, 1),
     pn("pH after 12.5 cm³ of NaOH (3 s.f.).", sg(pKa, 3), "Half-neutralised: pH = pK_a = 4.74.", 0.02, None, 1),
     pn("pH at the equivalence point (3 s.f.).", sg(pH_eq, 3), "[A⁻] = 0.0500; pOH = 5.28; pH = 8.72.", 0.03, None, 1),
     pm("A suitable indicator is", "phenolphthalein", ["methyl orange", "bromophenol blue (3.0–4.6)", "none: the change is too small"], "Its range (about 8.3–10) lies in the steep part of the curve above pH 7.", 1)], figure=wa_fig()),
  [M("K_a × K_b for a conjugate acid–base pair equals", "K_w", ["1", "K_a²", "pK_w"], "K_w = 1.0 × 10⁻¹⁴ at 25 °C."),
   M("The pH at the equivalence point of a weak acid/strong base titration is", "greater than 7", ["less than 7", "exactly 7", "equal to pK_a"], "The conjugate base is formed."),
   M("The salt NH₄Cl dissolves to give a solution that is", "slightly acidic", ["basic", "neutral", "strongly alkaline"], "NH₄⁺ is a weak acid."),
   M("An indicator changes colour when", "pH ≈ pK_In ± 1", ["pH = 7", "pH = 14", "pH = 0"], "[HIn] and [In⁻] are comparable."),
   M("In the buffer region of the curve, the pH changes", "slowly", ["rapidly", "not at all", "randomly"], "The mixture of HA and A⁻ resists change in pH.")],
  (wa_fig(), "Titration of a weak acid (ethanoic acid) with a strong base: pH = pK_a at half-equivalence (12.5 cm³), pH about 8.7 at equivalence.", "A titration curve beginning near pH 3, rising slowly through the buffer region, steeply at 25 cm³ to about 11 and flattening near 13."),
  notes=[DATA, "K_a = 1.8 × 10⁻⁵ (ethanoic acid) and K_b = 1.8 × 10⁻⁵ (ammonia) are standard textbook values (they happen to be equal); indicator ranges are approximate."], minutes=40, prereq=["gceal-chemistry-kpksp"])

# ---- 1.4 Born-Haber, entropy and free energy
dHf, dHatNa, IE, dHatCl, EA = -411.0, 107.0, 496.0, 122.0, -349.0
LE = dHf - (dHatNa + IE + dHatCl + EA); assert abs(LE + 787) < 1e-9
dH_rxn = -635.1 - 393.5 - (-1206.9); dS_rxn = 39.7 + 213.6 - 92.9; T_dec = dH_rxn * 1000 / dS_rxn
assert abs(dH_rxn - 178.3) < 1e-9 and abs(dS_rxn - 160.4) < 1e-9 and abs(T_dec - 1111.6) < 0.2
dG298 = dH_rxn - 298 * dS_rxn / 1000; assert abs(dG298 - 130.5) < 0.1
Keq = math.exp(10000 / (R_ * 298)); assert abs(Keq - 56.7) < 0.2
def bh_fig():
    lv = [("Na⁺(g) + Cl(g) + e⁻", 725), ("Na⁺(g) + Cl⁻(g)", 376), ("Na(g) + Cl(g)", 229), ("Na(g) + ½Cl₂(g)", 107), ("Na(s) + ½Cl₂(g)", 0), ("NaCl(s)", -411)]
    y = lambda E: 240 - (E + 411) * 0.2
    it = []
    for lab, E in lv:
        it += [LINE(140, y(E), 260, y(E), width=2), T(270, y(E) + 4, "%s  %+d" % (lab, E) if E else "%s  0" % lab, 11, anchor="start")]
    it += [LINE(120, y(376), 120, y(-411) - 2, color="red", width=3, arrow="end"), T(112, 150, "lattice", 12, anchor="end", color="red"), T(112, 166, "enthalpy −787", 12, anchor="end", color="red")]
    return shapes(it, 440, 262)
lesson(ch, "thermo", "Lattice enthalpy, entropy and free energy",
  ["Construct a Born–Haber cycle and calculate lattice enthalpy; explain trends in lattice enthalpy",
   "Explain entropy and calculate ΔS° and ΔG° = ΔH° − TΔS°; find the temperature at which a reaction becomes feasible",
   "Relate ΔG° to the equilibrium constant"],
  [("definition", "Lattice enthalpy and Born–Haber cycles",
    "The **lattice enthalpy** (of formation) is the enthalpy change when one mole of an ionic solid forms from its gaseous ions: Na⁺(g) + Cl⁻(g) → NaCl(s). It cannot be measured directly; a **Born–Haber cycle** applies Hess's law to: enthalpy of formation, **atomisation** of the elements, **ionisation energy** of the metal, **electron affinity** of the non-metal and the lattice enthalpy.\n\n"
    "Lattice enthalpy is more exothermic for ions with a **higher charge** and a **smaller radius** (MgO ≫ NaCl > KCl)."),
   ("formule", "Entropy and free energy",
    "**Entropy** S (J K⁻¹ mol⁻¹) measures the dispersal of energy/disorder: gas ≫ liquid > solid; more particles or moles of gas means higher S. **ΔS° = ΣS°(products) − ΣS°(reactants)**.\n\n"
    "The reaction is **feasible** if the free energy change **ΔG° = ΔH° − TΔS°** is negative (ΔS in kJ K⁻¹ mol⁻¹ = J/1000). For ΔH > 0 and ΔS > 0 the reaction becomes feasible above T = ΔH/ΔS. Also **ΔG° = −RT ln K**.",
    r"\Delta G^{\circ}=\Delta H^{\circ}-T\Delta S^{\circ}\qquad \Delta G^{\circ}=-RT\ln K"),
   ("retenir", "Reading ΔG",
    "ΔG < 0: feasible (spontaneous in the thermodynamic sense; the rate may still be slow). ΔG = 0: equilibrium. A large negative ΔG° means K ≫ 1; a large positive ΔG° means K ≪ 1. A reaction with ΔH < 0 and ΔS > 0 is feasible at all temperatures; ΔH > 0 and ΔS < 0 never."),
   ("pieges", "Common mistakes",
    "- Mixing the units of ΔS (J) and ΔH (kJ) in ΔG = ΔH − TΔS.\n- Using °C instead of kelvin.\n- Forgetting to multiply atomisation and ionisation steps by the correct number of moles.\n- Saying a negative ΔG means the reaction is fast.")],
  [("Example 1 — Born–Haber for NaCl", "Find the lattice enthalpy of NaCl from: ΔH_f(NaCl) = −411; atomisation of Na +107; first ionisation energy of Na +496; atomisation of ½Cl₂ +122; electron affinity of Cl −349 (all kJ mol⁻¹).",
    ["Hess's law: ΔH_f = ΔH_at(Na) + IE(Na) + ΔH_at(Cl) + EA(Cl) + ΔH_latt.", "Sum of the first four steps = 107 + 496 + 122 − 349 = +376 kJ mol⁻¹.", "ΔH_latt = −411 − 376 = −787 kJ mol⁻¹."], "Lattice enthalpy = −787 kJ mol⁻¹"),
   ("Example 2 — Decomposition of limestone", "CaCO₃(s) → CaO(s) + CO₂(g): ΔH_f (kJ mol⁻¹): CaCO₃ −1206.9, CaO −635.1, CO₂ −393.5; S° (J K⁻¹ mol⁻¹): CaCO₃ 92.9, CaO 39.7, CO₂ 213.6. Find ΔG° at 298 K and the temperature above which the reaction is feasible.",
    ["ΔH° = −635.1 − 393.5 + 1206.9 = +178.3 kJ mol⁻¹; ΔS° = 39.7 + 213.6 − 92.9 = +160.4 J K⁻¹ mol⁻¹.", "ΔG°(298) = 178.3 − 298 × 0.1604 = +130.5 kJ mol⁻¹ (not feasible at room temperature).", "Feasible when ΔG° < 0: T > 178 300/160.4 = 1110 K (about 840 °C)."], "ΔG°(298) = +130 kJ mol⁻¹; feasible above about 1110 K")],
  [N("Calculate ΔG° (kJ mol⁻¹) for ΔH° = −50.0 kJ mol⁻¹, ΔS° = −100 J K⁻¹ mol⁻¹ at 298 K (3 s.f.).", sg(-50.0 + 298 * 0.100, 3), "ΔG° = −50.0 − 298 × (−0.100) = −50.0 + 29.8 = −20.2 kJ mol⁻¹.", 0.2, "kJ/mol"),
   M("Which has the highest entropy at 298 K?", "1 mol of CO₂(g)", ["1 mol of H₂O(l)", "1 mol of NaCl(s)", "1 mol of diamond"], "Gases are the most disordered."),
   TF("True or false: ΔS is positive when a solid decomposes to give a gas and a solid.", True, "More moles of gas means more disorder.")],
  [N("Use ΔG° = −RT ln K to find K (3 s.f.) when ΔG° = −10.0 kJ mol⁻¹ at 298 K (R = 8.31).", sg(Keq, 3), "ln K = 10 000/(8.31 × 298) = 4.04, K = 56.7.", 0.5, None, difficulty=2),
   M("The lattice enthalpy of MgO is more exothermic than that of NaCl mainly because", "Mg²⁺ and O²⁻ have higher charges and smaller radii", ["MgO has covalent character only", "O is less electronegative than Cl", "Mg is a larger atom than Na"], "Attraction ∝ q₁q₂/r.")],
  P("Consider the reaction 2NO₂(g) → N₂O₄(g) with ΔH° = −57.2 kJ mol⁻¹ and ΔS° = −176 J K⁻¹ mol⁻¹ (per mole of N₂O₄ formed).",
    [pn("ΔG° at 298 K (kJ mol⁻¹, 3 s.f.).", sg(-57.2 + 298 * 0.176, 3), "ΔG° = −57.2 − 298 × (−0.176) = −57.2 + 52.45 = −4.75 kJ mol⁻¹.", 0.05, "kJ/mol", 2),
     pm("At 298 K the reaction is", "feasible", ["not feasible", "at equilibrium", "endothermic"], "ΔG° is negative.", 1),
     pn("Temperature above which the reaction is no longer feasible (K, 3 s.f.).", sg(57.2 / 0.176, 3), "ΔG° = 0 when T = ΔH/ΔS = 57 200/176 = 325 K.", 1, "K", 2),
     po("Explain why ΔS° is negative.", "Two moles of gas combine to give one mole of gas, so there are fewer gas particles and the system becomes less disordered.", "1 mark: fewer moles of gas; 1 mark: less disorder / lower entropy.", 2)]),
  [M("The lattice enthalpy of formation of an ionic solid is the enthalpy change when", "one mole of solid forms from its gaseous ions", ["one mole of solid dissolves", "an atom loses an electron", "one mole of gas forms"], "It is highly exothermic."),
   M("For ΔH > 0 and ΔS > 0, the reaction is feasible", "at high temperature", ["at all temperatures", "at low temperature only", "never"], "TΔS must exceed ΔH."),
   M("A spontaneous reaction has", "ΔG < 0", ["ΔG > 0", "ΔG = 1", "ΔS < 0 always"], "Negative free energy change."),
   M("Which step in a Born–Haber cycle for NaCl is exothermic?", "electron affinity of chlorine (first)", ["ionisation of sodium", "atomisation of sodium", "atomisation of chlorine"], "Chlorine gains an electron releasing energy (−349 kJ mol⁻¹)."),
   M("A large positive ΔG° means that K is", "much less than 1", ["much greater than 1", "equal to 1", "zero"], "ΔG° = −RT ln K.")],
  (bh_fig(), "Born–Haber cycle for NaCl (enthalpies in kJ mol⁻¹, not to scale).", "A ladder of energy levels from NaCl(s) at minus 411 up to gaseous sodium ions and chlorine atoms at plus 725, with a red arrow for the lattice enthalpy minus 787."),
  notes=[DATA, "Enthalpy, entropy and electron-affinity data are standard textbook values (check the board's data booklet). Entropy and Gibbs energy may be beyond the board's core syllabus for some options: flagged."], minutes=45, prereq=["gceal-chemistry-acidbase"])

# =============================================================== 2. INORGANIC CHEMISTRY
ch = p.chapter("ch-inorganic", "Inorganic chemistry: groups, period 3 and transition metals", SYL % "inorganic chemistry")

# ---- 2.1 group 2
s_CaOH = (5.5e-6 / 4) ** (1 / 3); pH_lw = 14 + math.log10(2 * s_CaOH)
assert abs(s_CaOH - 0.01111) < 0.0002 and abs(pH_lw - 12.35) < 0.02
m_CaO = 50.0 * 0.90 / 100.0 * 56.0 / 1.0; m_CaO = 50.0 * 0.90 * 56.0 / 100.0; assert abs(m_CaO - 25.2) < 1e-9
m_BaSO4 = 0.100 * 0.0250 * 233.0; assert abs(m_BaSO4 - 0.5825) < 1e-9
def g2_fig():
    return bars([("Be", 900, "orange"), ("Mg", 738, "orange"), ("Ca", 590, "orange"), ("Sr", 549, "orange"), ("Ba", 503, "orange")], unit="first ionisation energy / kJ mol⁻¹", w=400, h=240)
lesson(ch, "group2", "Group 2: the alkaline earth metals",
  ["Describe and explain trends in reactivity, hydroxide and sulfate solubility and thermal stability of Group 2 compounds",
   "Write equations for reactions with oxygen, water, acids and for the thermal decomposition of carbonates and nitrates",
   "Describe flame tests, tests for sulfate and uses of calcium and magnesium compounds"],
  [("definition", "Reactions of Group 2 metals",
    "The atoms have two outer s electrons and form M²⁺ ions. Reactivity **increases down the group** because the first and second ionisation energies fall (larger atoms, more shielding, outer electrons further from the nucleus).\n\n"
    "- With oxygen: 2M + O₂ → 2MO (Mg burns with a bright white flame).\n- With water: Mg reacts very slowly with cold water but quickly with steam (MgO + H₂); Ca, Sr and Ba react with cold water giving M(OH)₂ + H₂, increasingly vigorously.\n- With dilute acids: M + 2H⁺ → M²⁺ + H₂."),
   ("retenir", "Trends in compounds",
    "- **Hydroxides**: solubility **increases** down the group (Mg(OH)₂ is almost insoluble, Ba(OH)₂ is soluble); limewater is Ca(OH)₂(aq).\n- **Sulfates**: solubility **decreases** down the group (BaSO₄ is insoluble: used as the test for sulfate and as a 'barium meal').\n- **Thermal stability** of carbonates and nitrates **increases** down the group: the smaller, more highly polarising M²⁺ distorts the anion and weakens it. MCO₃ → MO + CO₂; 2M(NO₃)₂ → 2MO + 4NO₂ + O₂."),
   ("methode", "Tests and uses",
    "**Flame tests**: Ca brick-red, Sr crimson, Ba apple-green (Mg gives none). **Sulfate test**: acidify with dilute HCl (removes carbonate), add BaCl₂(aq): white precipitate of BaSO₄.\n\n"
    "Uses: **quicklime** CaO and **slaked lime** Ca(OH)₂ to neutralise acidic soil and in cement and mortar; limestone CaCO₃ in building and blast furnaces; **Mg(OH)₂** ('milk of magnesia') as an antacid; Mg alloys."),
   ("pieges", "Common mistakes",
    "- Explaining thermal stability by the size of the cation without mentioning polarisation of the anion.\n- Saying that Mg(OH)₂ is soluble because it is a hydroxide of a metal: solubility rises down the group.\n- Forgetting to acidify before adding barium chloride (carbonate would also precipitate).\n- Writing the wrong formula for the nitrate: Ca(NO₃)₂ gives 4NO₂ for every 2 formula units.")],
  [("Example 1 — Limewater", "K_sp for Ca(OH)₂ is 5.5 × 10⁻⁶ mol³ dm⁻⁹. Find the solubility and the pH of saturated limewater.",
    ["K_sp = 4s³ so s = (5.5 × 10⁻⁶/4)^(1/3) = 1.11 × 10⁻² mol dm⁻³.", "[OH⁻] = 2s = 2.22 × 10⁻² mol dm⁻³; pOH = 1.65.", "pH = 14 − 1.65 = 12.35, so limewater is strongly alkaline."], "s = 1.1 × 10⁻² mol dm⁻³; pH ≈ 12.4"),
   ("Example 2 — Quicklime", "Limestone (90 % CaCO₃) is heated: CaCO₃ → CaO + CO₂. Find the mass of CaO from 50.0 kg of limestone (A_r: Ca 40, C 12, O 16).",
    ["Mass of CaCO₃ = 0.90 × 50.0 = 45.0 kg = 45 000 g, so n = 45 000/100 = 450 mol.", "n(CaO) = 450 mol; M(CaO) = 56 g mol⁻¹.", "Mass = 450 × 56 = 25 200 g = 25.2 kg."], "25.2 kg of CaO")],
  [M("Which Group 2 metal reacts most vigorously with cold water?", "barium", ["magnesium", "beryllium", "calcium"], "Reactivity increases down the group."),
   N("A solution containing 0.0250 mol of sulfate ions is treated with excess BaCl₂. What mass of BaSO₄ forms? (M = 233 g/mol, g, 3 s.f.)", sg(0.0250 * 233, 3), "n(BaSO₄) = n(SO₄²⁻) = 0.0250 mol; m = 0.0250 × 233 = 5.83 g.", 0.02, "g"),
   TF("True or false: the thermal stability of the Group 2 carbonates increases down the group.", True, "The larger cations polarise the carbonate ion less, so more heat is needed to decompose them.")],
  [M("Which pair correctly describes solubility down Group 2?", "hydroxides increase, sulfates decrease", ["hydroxides decrease, sulfates increase", "both increase", "both decrease"], "Mg(OH)₂ is least soluble and BaSO₄ is least soluble."),
   N("What mass of CaO (g) is formed by decomposing 20.0 g of pure CaCO₃? (A_r: Ca 40, C 12, O 16; 3 s.f.)", sg(20.0 / 100 * 56, 3), "n = 20.0/100 = 0.200 mol; m(CaO) = 0.200 × 56 = 11.2 g.", 0.05, "g", difficulty=2)],
  P("A white solid X was heated and gave a colourless gas that turned limewater milky and a white residue Y. Y reacted with water giving an alkaline solution; X gave a brick-red flame.",
    [pm("X is", "calcium carbonate", ["calcium sulfate", "barium carbonate", "magnesium nitrate"], "A brick-red flame shows Ca; CO₂ with limewater shows carbonate.", 1),
     pm("Y is", "calcium oxide", ["calcium hydroxide", "calcium metal", "calcium chloride"], "CaCO₃ → CaO + CO₂.", 1),
     pn("Volume of CO₂ (dm³, 3 s.f.) from 5.00 g of X at room temperature and pressure (24.0 dm³/mol).", sg(5.00 / 100 * 24.0, 3), "n = 0.0500 mol; V = 0.0500 × 24.0 = 1.20 dm³.", 0.02, "dm³", 1),
     po("Write the equation for the reaction of Y with water and name the product.", "CaO + H₂O → Ca(OH)₂; calcium hydroxide (slaked lime).", "1 mark: correct equation; 1 mark: name of the product.", 2)]),
  [M("The flame colour of barium compounds is", "apple-green", ["brick-red", "crimson", "yellow"], "Ba²⁺ gives a green flame."),
   M("The first ionisation energy down Group 2", "decreases", ["increases", "stays the same", "is zero"], "The outer electrons are further away and more shielded."),
   M("Which is used to neutralise acid soil?", "slaked lime Ca(OH)₂", ["BaSO₄", "NaCl", "Mg metal"], "It is a cheap alkali."),
   M("In the test for sulfate ions, acid is added first to", "remove carbonate ions", ["make the solution neutral", "dissolve barium sulfate", "produce hydrogen"], "BaCO₃ would also precipitate in neutral solution."),
   M("The most thermally stable carbonate is", "BaCO₃", ["MgCO₃", "CaCO₃", "BeCO₃"], "Stability increases down the group.")],
  (g2_fig(), "First ionisation energy of the Group 2 elements: it falls down the group, so reactivity rises.", "A bar chart of the first ionisation energy for beryllium, magnesium, calcium, strontium and barium, decreasing from left to right."),
  notes=[DATA, "K_sp(Ca(OH)₂) = 5.5 × 10⁻⁶ is a typical textbook value. Use of lime in Cameroonian agriculture is stated generally; verify local practice.", "Ionisation energies are standard values (Be 900, Mg 738, Ca 590, Sr 549, Ba 503 kJ mol⁻¹)."], minutes=35, prereq=["gceal-chemistry-kpksp"])

# ---- 2.2 group 7
n_th = 0.100 * 20.00 / 1000; n_I2 = n_th / 2; c_ClO = n_I2 / 0.0250; assert abs(c_ClO - 0.0400) < 1e-12
def g7_fig():
    return bars([("F₂", 2.87, "yellow"), ("Cl₂", 1.36, "green"), ("Br₂", 1.07, "orange"), ("I₂", 0.54, "purple")], unit="E° / V", w=380, h=240)
lesson(ch, "group7", "Group 7: the halogens",
  ["Describe the trends in physical properties and oxidising power of the halogens and their displacement reactions",
   "Describe reactions of halide ions with silver nitrate and concentrated sulfuric acid, and disproportionation of chlorine",
   "Do calculations on halogen reactions, including the iodine–thiosulfate titration"],
  [("definition", "Trends in the halogens",
    "At room temperature: fluorine is a pale yellow gas, chlorine a pale green gas, bromine a red-brown liquid, iodine a grey-black solid (purple vapour). Melting and boiling points **increase** down the group (stronger London forces with more electrons).\n\n"
    "The halogens are **oxidising agents**; the oxidising power **decreases** down the group (E°: F₂ +2.87, Cl₂ +1.36, Br₂ +1.07, I₂ +0.54 V) because the atom gains an electron less readily as it gets larger."),
   ("retenir", "Displacement and halide ions",
    "A more reactive halogen displaces a less reactive one from its halide: Cl₂ + 2Br⁻ → 2Cl⁻ + Br₂ (orange solution); Cl₂ + 2I⁻ → 2Cl⁻ + I₂ (brown; purple in hexane); Br₂ + 2I⁻ → 2Br⁻ + I₂.\n\n"
    "**Halide reducing power increases down the group**: with concentrated H₂SO₄, NaCl gives only HCl (steamy fumes); NaBr gives HBr and also Br₂ and SO₂; NaI gives HI, I₂ and also S and H₂S. **AgNO₃(aq)**: AgCl white (dissolves in dilute NH₃), AgBr cream (in concentrated NH₃), AgI yellow (insoluble)."),
   ("methode", "Chlorine and disproportionation",
    "Chlorine in water: Cl₂ + H₂O ⇌ HCl + HClO (chlorine is both oxidised and reduced): HClO disinfects water. Cold dilute NaOH: Cl₂ + 2NaOH → NaCl + NaClO + H₂O (bleach, Cl: +1). Hot concentrated NaOH gives chlorate(V): 3Cl₂ + 6NaOH → 5NaCl + NaClO₃ + 3H₂O.\n\n**Iodine–thiosulfate titration**: I₂ + 2S₂O₃²⁻ → 2I⁻ + S₄O₆²⁻ (starch indicator, blue-black → colourless). Used to find the oxidising agent that liberates iodine from excess KI."),
   ("pieges", "Common mistakes",
    "- Saying that iodine is the strongest oxidising agent because it is the largest: it is the weakest.\n- Forgetting that silver halide tests need dilute nitric acid first.\n- Writing the products of H₂SO₄ + NaBr or NaI as only HBr and HI (these are further oxidised).\n- Mixing up 'oxidised' and 'reduced' in disproportionation: the same element does both.")],
  [("Example 1 — Bleach analysis", "25.0 cm³ of diluted bleach liberates iodine from excess acidified KI: ClO⁻ + 2I⁻ + 2H⁺ → Cl⁻ + I₂ + H₂O. The iodine needs 20.00 cm³ of 0.100 mol dm⁻³ Na₂S₂O₃. Find [ClO⁻] in the diluted bleach.",
    ["n(S₂O₃²⁻) = 0.100 × 20.00/1000 = 2.00 × 10⁻³ mol; ratio I₂ : S₂O₃²⁻ = 1 : 2, so n(I₂) = 1.00 × 10⁻³ mol.", "Ratio ClO⁻ : I₂ = 1 : 1, so n(ClO⁻) = 1.00 × 10⁻³ mol in 25.0 cm³.", "[ClO⁻] = 1.00 × 10⁻³/0.0250 = 0.0400 mol dm⁻³."], "[ClO⁻] = 0.0400 mol dm⁻³"),
   ("Example 2 — Reactions of halide ions", "Predict the observations when chlorine water is added to potassium iodide solution and the product is shaken with hexane.",
    ["Cl₂ is a stronger oxidising agent than I₂ (E° 1.36 V against 0.54 V), so it oxidises I⁻: Cl₂ + 2I⁻ → 2Cl⁻ + I₂.", "The aqueous layer turns brown (I₂ in water).", "On shaking with hexane, iodine dissolves in the upper hexane layer, which turns purple."], "Brown solution; purple hexane layer")],
  [M("Which halogen is a liquid at room temperature?", "bromine", ["chlorine", "iodine", "fluorine"], "Bromine is a red-brown liquid."),
   N("How many moles of I₂ react with 0.0040 mol of thiosulfate (I₂ + 2S₂O₃²⁻ → 2I⁻ + S₄O₆²⁻)?", 0.0020, "Mole ratio 1 : 2: 0.0040/2 = 0.0020 mol.", 0.0001, "mol"),
   TF("True or false: bromine can displace chloride ions from solution.", False, "Br₂ is a weaker oxidising agent than Cl₂, so no reaction occurs.")],
  [M("When concentrated sulfuric acid is added to solid sodium iodide, the products include", "iodine and hydrogen sulfide", ["only hydrogen iodide", "only sulfur dioxide", "chlorine"], "HI is a strong reducing agent and reduces H₂SO₄ to S and H₂S."),
   N("A bleach sample requires 12.50 cm³ of 0.100 mol dm⁻³ thiosulfate for the iodine from 25.0 cm³ (as in Example 1). Find [ClO⁻] (mol dm⁻³, 3 s.f.).", sg(0.100 * 12.50 / 1000 / 2 / 0.0250, 3), "n(S₂O₃²⁻) = 1.25 × 10⁻³; n(I₂) = 6.25 × 10⁻⁴ = n(ClO⁻); c = 6.25 × 10⁻⁴/0.0250 = 0.0250 mol dm⁻³.", 0.0003, "mol/dm³", difficulty=2)],
  P("Chlorine is bubbled into cold dilute sodium hydroxide solution.",
    [pm("The ionic equation is", "Cl₂ + 2OH⁻ → Cl⁻ + ClO⁻ + H₂O", ["Cl₂ + 2OH⁻ → 2ClO⁻ + H₂", "Cl₂ + OH⁻ → ClO₃⁻ + Cl⁻", "Cl₂ + 2OH⁻ → 2Cl⁻ + O₂ + H₂"], "Cold dilute alkali gives chloride and chlorate(I).", 1),
     pn("Oxidation number of chlorine in ClO⁻.", 1, "O is −2 and the ion is −1, so Cl = +1.", 0, None, 1),
     pt("This is a disproportionation reaction.", True, "Chlorine goes from 0 to −1 (reduced) and to +1 (oxidised).", 1),
     po("Give one use of the solution produced.", "It is bleach and disinfectant (sodium chlorate(I)), used to kill bacteria in water or to whiten fabrics.", "1 mark for bleach/disinfectant; 1 mark for the property (oxidising agent, kills microbes).", 2)], figure=g7_fig()),
  [M("The halogen with the highest boiling point is", "iodine", ["fluorine", "chlorine", "bromine"], "The largest molecule has the strongest London forces."),
   M("A cream precipitate with silver nitrate indicates", "bromide ions", ["chloride ions", "iodide ions", "sulfate ions"], "AgBr is cream."),
   M("The oxidising power of the halogens", "decreases down the group", ["increases down the group", "stays the same", "is zero"], "E° falls from F₂ to I₂."),
   M("The indicator used in the iodine–thiosulfate titration is", "starch", ["methyl orange", "phenolphthalein", "none"], "It gives a blue-black colour with iodine."),
   M("In Cl₂ + H₂O ⇌ HCl + HClO, chlorine", "is both oxidised and reduced", ["is only oxidised", "is only reduced", "is neither"], "Disproportionation: 0 → −1 and +1.")],
  (g7_fig(), "Standard electrode potentials of the halogens: the oxidising power decreases down the group.", "A bar chart of E° for fluorine, chlorine, bromine and iodine, decreasing from 2.87 to 0.54 volts."),
  notes=[DATA, "Colours and observations are standard textbook descriptions; to be checked by a teacher. Fluorine chemistry (hazards) is not developed."], minutes=40, prereq=["gceal-chemistry-electrode"])

# ---- 2.3 period 3
m_Na2O = 0.050 * 62.0; assert abs(m_Na2O - 3.1) < 1e-9
def p3_fig():
    items = [("Na₂O", "lightblue"), ("MgO", "lightblue"), ("Al₂O₃", "lightgreen"), ("SiO₂", "lightyellow"), ("P₄O₁₀", "lightorange"), ("SO₃", "lightorange")]
    it = []
    for i, (n, c) in enumerate(items):
        it += [RECT(10 + i * 66, 60, 62, 40, fill=c), T(41 + i * 66, 85, n, 13)]
    it += [T(77, 125, "basic", 13), T(210, 125, "amphoteric", 13), T(280, 150, "acidic", 13), LINE(20, 170, 380, 170, color="red", width=2, arrow="end"), T(200, 195, "increasing acidity of the oxide", 13), T(200, 35, "Period 3 oxides", 14, bold=True)]
    return shapes(it, 400, 215)
lesson(ch, "period3", "Period 3: oxides and chlorides",
  ["Describe the structure, bonding and acid–base character of the oxides of period 3",
   "Describe the reactions of the chlorides of period 3 with water and explain the differences using bonding",
   "Write equations for the reactions of the oxides with water, acids and alkalis"],
  [("definition", "Oxides",
    "Across period 3 the oxides change from ionic and basic to covalent and acidic:\n\n- **Na₂O** (ionic, basic): Na₂O + H₂O → 2NaOH (pH about 14). **MgO** (ionic, basic): slightly soluble; MgO + 2HCl → MgCl₂ + H₂O.\n- **Al₂O₃** (ionic with covalent character, **amphoteric**): reacts with both acid and alkali.\n- **SiO₂** (giant covalent, acidic): insoluble; reacts with hot concentrated NaOH.\n- **P₄O₁₀, SO₂/SO₃** (simple molecular, acidic): P₄O₁₀ + 6H₂O → 4H₃PO₄; SO₃ + H₂O → H₂SO₄."),
   ("retenir", "Amphoteric aluminium oxide and chlorides",
    "Al₂O₃ + 6HCl → 2AlCl₃ + 3H₂O and Al₂O₃ + 2NaOH + 3H₂O → 2Na[Al(OH)₄] (sodium tetrahydroxoaluminate).\n\n"
    "**Chlorides**: NaCl and MgCl₂ are ionic and dissolve in water to give neutral or slightly acidic solutions; **AlCl₃** and the covalent chlorides **SiCl₄**, **PCl₃/PCl₅** are hydrolysed by water, giving HCl (steamy fumes) and an acidic solution: SiCl₄ + 2H₂O → SiO₂ + 4HCl; PCl₅ + 4H₂O → H₃PO₄ + 5HCl."),
   ("methode", "Explaining the trend",
    "The difference in electronegativity between the element and oxygen (or chlorine) falls across the period: **large** (Na, Mg): ionic bonding, oxide ion O²⁻ reacts with water or H⁺ (basic); **small** (Si, P, S, Cl): covalent, the oxide reacts with water to give acid (non-metal oxides are acidic).\n\nThe melting points reflect the structure: ionic or giant covalent (high) and simple molecular (low)."),
   ("pieges", "Common mistakes",
    "- Saying that SiO₂ reacts readily with water: it is insoluble.\n- Writing Al₂O₃ as 'acidic' or 'basic' only: it is amphoteric.\n- Predicting that MgCl₂ is hydrolysed like AlCl₃: only partly (slightly acidic solution).\n- Forgetting that SO₂ forms sulfurous acid H₂SO₃ (pH about 3) while SO₃ gives sulfuric acid.")],
  [("Example 1 — pH of a basic oxide", "What mass of Na₂O must dissolve in water to make 1.0 dm³ of solution of pH 13 (A_r: Na 23, O 16)?",
    ["pH 13 means pOH 1, so [OH⁻] = 0.10 mol dm⁻³.", "Na₂O + H₂O → 2NaOH: each mole of Na₂O gives 2 mol OH⁻, so n(Na₂O) = 0.050 mol.", "Mass = 0.050 × 62.0 = 3.1 g."], "3.1 g of Na₂O"),
   ("Example 2 — Amphoteric behaviour", "Show that aluminium oxide is amphoteric by writing two equations, and explain why it is not typical of metal oxides.",
    ["With acid: Al₂O₃ + 6H⁺ → 2Al³⁺ + 3H₂O (acts as a base).", "With alkali: Al₂O₃ + 2OH⁻ + 3H₂O → 2[Al(OH)₄]⁻ (acts as an acid).", "The small, highly charged Al³⁺ polarises O²⁻ strongly, giving a bond with significant covalent character."], "Reacts with both acids and alkalis")],
  [M("Which oxide is amphoteric?", "Al₂O₃", ["Na₂O", "SiO₂", "SO₃"], "It reacts with acids and alkalis."),
   N("What is the pH of an aqueous solution of 0.010 mol dm⁻³ NaOH formed from Na₂O (3 s.f.)?", 12.0, "[OH⁻] = 0.010, pOH = 2, pH = 12.0.", 0.02, None),
   TF("True or false: P₄O₁₀ reacts with water to give an acidic solution.", True, "P₄O₁₀ + 6H₂O → 4H₃PO₄.")],
  [M("When SiCl₄ is added to water, the products are", "SiO₂ and HCl", ["Si and Cl₂", "SiH₄", "no reaction"], "SiCl₄ + 2H₂O → SiO₂ + 4HCl (steamy fumes)."),
   N("What mass of P₄O₁₀ (M = 284) is needed to make 0.40 mol of phosphoric acid (g, 3 s.f.)?", sg(0.40 / 4 * 284, 3), "P₄O₁₀ + 6H₂O → 4H₃PO₄: n(P₄O₁₀) = 0.40/4 = 0.100 mol; m = 28.4 g.", 0.1, "g", difficulty=2)],
  P("Oxides of period 3 elements: Na₂O, MgO, Al₂O₃, SiO₂, P₄O₁₀, SO₃.",
    [pm("Which has a giant covalent structure?", "SiO₂", ["Na₂O", "P₄O₁₀", "SO₃"], "Silicon dioxide is a giant covalent network.", 1),
     pm("Which of the following gives the lowest pH when added to water?", "SO₃", ["Na₂O", "MgO", "SiO₂"], "SO₃ forms sulfuric acid; SiO₂ is insoluble and Na₂O and MgO give alkaline solutions.", 1),
     pt("MgO reacts with dilute hydrochloric acid.", True, "It is a basic oxide: MgO + 2HCl → MgCl₂ + H₂O.", 1),
     po("Explain why Na₂O is basic but SO₃ is acidic.", "Na₂O is ionic and contains O²⁻ ions which accept protons from water to form OH⁻; SO₃ is a covalent molecular oxide of a non-metal which reacts with water to give H₂SO₄ (releasing H⁺).", "1 mark: Na₂O ionic, O²⁻ gives OH⁻; 1 mark: SO₃ covalent, forms an acid with water.", 2)]),
  [M("Which is the most acidic oxide?", "SO₃", ["MgO", "Al₂O₃", "Na₂O"], "It forms sulfuric acid."),
   M("The oxide of silicon is", "insoluble in water", ["very soluble", "basic", "a gas"], "Giant covalent network."),
   M("Na₂O contains", "ionic bonds", ["covalent bonds only", "metallic bonds", "hydrogen bonds"], "Na⁺ and O²⁻."),
   M("Aluminium chloride in water", "is hydrolysed to give an acidic solution", ["is neutral", "does not dissolve", "gives hydrogen"], "Al³⁺ polarises water ligands."),
   M("The pH of solution formed by SO₂ in water is about", "3", ["14", "7", "11"], "H₂SO₃ is a weak acid.")],
  (p3_fig(), "Period 3 oxides change from basic (ionic) through amphoteric to acidic (covalent).", "Six boxes labelled Na₂O, MgO, Al₂O₃, SiO₂, P₄O₁₀ and SO₃, coloured from basic through amphoteric to acidic, with an arrow showing increasing acidity."),
  notes=[DATA, "Approximate pH values of the solutions are textbook values (SO₂ about 3; P₄O₁₀ about 0–1); not given as exact data."], minutes=40, prereq=["gceal-chemistry-group2"])

# ---- 2.4 transition metals
n_cr = 0.0100 * 24.0 / 1000; n_fe2 = 6 * n_cr; c_fe2 = n_fe2 / 0.0250
assert abs(n_cr - 2.40e-4) < 1e-12 and abs(n_fe2 - 1.44e-3) < 1e-12 and abs(c_fe2 - 0.0576) < 1e-9
def cx_fig():
    cx, cy = 150, 125
    it = [CIRCLE(cx, cy, 16, fill="lightblue"), T(cx, cy + 5, "Cu", 14, bold=True)]
    for dx, dy in [(0, -80), (0, 80), (-80, 0), (80, 0), (-52, -52), (52, 52)]:
        it += [LINE(cx, cy, cx + dx * 0.78, cy + dy * 0.78, width=2), CIRCLE(cx + dx, cy + dy, 13, fill="lightyellow"), T(cx + dx, cy + dy + 4, "H₂O", 10)]
    it += [T(320, 100, "[Cu(H₂O)₆]²⁺", 14, bold=True), T(320, 124, "octahedral", 12), T(320, 144, "coordination number 6", 12), T(320, 164, "pale blue", 12)]
    return shapes(it, 440, 250)
lesson(ch, "transition", "Transition metals and complex ions",
  ["Define a transition element and explain variable oxidation states, coloured ions and catalytic activity",
   "Describe ligands, coordination number, shapes of complexes and ligand-exchange reactions",
   "Describe reactions of aqueous ions with NaOH and NH₃ and do calculations on redox titrations"],
  [("definition", "Transition elements",
    "A **transition element** forms at least one ion with a **partially filled d subshell** (Sc³⁺ and Zn²⁺ do not count). Features: variable oxidation states (the 4s and 3d electrons have similar energies), coloured compounds, complex ions, catalytic activity, paramagnetism.\n\n"
    "Configurations: Fe [Ar] 3d⁶ 4s²; Cu [Ar] 3d¹⁰ 4s¹; Cr [Ar] 3d⁵ 4s¹. Ions lose 4s electrons first: Fe³⁺ [Ar] 3d⁵, Cu²⁺ [Ar] 3d⁹, Zn²⁺ [Ar] 3d¹⁰ (colourless)."),
   ("retenir", "Complexes and colour",
    "A **complex ion** has a central metal ion bonded to **ligands** by dative covalent bonds (ligands have lone pairs: H₂O, NH₃, Cl⁻, CN⁻, OH⁻; EDTA⁴⁻ is polydentate). The **coordination number** is the number of dative bonds: 6 (octahedral, e.g. [Cu(H₂O)₆]²⁺, [Fe(CN)₆]³⁻), 4 (tetrahedral [CuCl₄]²⁻, square planar), 2 (linear [Ag(NH₃)₂]⁺).\n\n"
    "Colour: the ligands split the d orbitals; electrons absorb visible light to move between d orbitals and the **complementary colour** is seen. The colour changes with the ligand, oxidation state and coordination number."),
   ("methode", "Reactions of aqueous ions",
    "With NaOH(aq): Cu²⁺ → blue precipitate Cu(OH)₂; Fe²⁺ → green precipitate (turns brown in air); Fe³⁺ → red-brown Fe(OH)₃; Cr³⁺ → green Cr(OH)₃ which dissolves in excess to give a green solution (amphoteric); Mn²⁺ → pale precipitate that darkens.\n\n"
    "With NH₃(aq): the same precipitates form, but Cu(OH)₂ dissolves in excess to give a **deep blue** solution of [Cu(NH₃)₄(H₂O)₂]²⁺ (ligand exchange). Catalysis: Fe in the Haber process, V₂O₅ in the Contact process, Ni in hydrogenation."),
   ("pieges", "Common mistakes",
    "- Counting Zn or Sc as transition metals: their common ions have empty or full d subshells.\n- Removing 3d electrons before 4s when forming ions.\n- Forgetting the overall charge of a complex: it is the sum of the metal charge and the ligand charges.\n- Saying that colour arises from the metal alone: the ligand matters.")],
  [("Example 1 — Charge and configuration", "Find the oxidation number of iron in [Fe(CN)₆]³⁻ and the number of d electrons in Cu²⁺ and Fe³⁺.",
    ["Six CN⁻ ligands contribute −6; overall charge is −3, so Fe = −3 + 6 = +3.", "Cu²⁺: [Ar] 3d⁹ (9 d electrons); Fe³⁺: [Ar] 3d⁵ (5 d electrons).", "Zn²⁺ would be 3d¹⁰ and colourless because its d subshell is full."], "Fe is +3; Cu²⁺ has 9 d electrons, Fe³⁺ has 5"),
   ("Example 2 — Dichromate titration", "25.0 cm³ of iron(II) sulfate solution needs 24.0 cm³ of 0.0100 mol dm⁻³ K₂Cr₂O₇ in acid: Cr₂O₇²⁻ + 14H⁺ + 6Fe²⁺ → 2Cr³⁺ + 7H₂O + 6Fe³⁺. Find [Fe²⁺].",
    ["n(Cr₂O₇²⁻) = 0.0100 × 24.0/1000 = 2.40 × 10⁻⁴ mol.", "n(Fe²⁺) = 6 × 2.40 × 10⁻⁴ = 1.44 × 10⁻³ mol.", "[Fe²⁺] = 1.44 × 10⁻³/0.0250 = 0.0576 mol dm⁻³ (the orange dichromate turns green)."], "[Fe²⁺] = 0.0576 mol dm⁻³")],
  [M("The electron configuration of Cu²⁺ is", "[Ar] 3d⁹", ["[Ar] 3d¹⁰ 4s¹", "[Ar] 3d⁷ 4s²", "[Ar] 3d⁸ 4s¹"], "Cu loses its 4s electron and one 3d electron."),
   N("Find the coordination number of the complex [Co(NH₃)₆]³⁺.", 6, "Six NH₃ ligands each form one dative bond.", 0, None),
   TF("True or false: Zn²⁺ compounds are coloured.", False, "Zn²⁺ has a full 3d¹⁰ subshell: no d–d transitions, so compounds are colourless.")],
  [M("Addition of excess NH₃(aq) to Cu²⁺(aq) gives", "a deep blue solution", ["a green precipitate", "a brown precipitate", "no change"], "[Cu(NH₃)₄(H₂O)₂]²⁺ forms by ligand exchange."),
   N("25.0 cm³ of Fe²⁺ solution needs 18.5 cm³ of 0.0200 mol dm⁻³ KMnO₄ (1 MnO₄⁻ : 5 Fe²⁺). Find [Fe²⁺] (mol dm⁻³, 3 s.f.).", sg(5 * 0.0200 * 18.5 / 1000 / 0.0250, 3), "n(MnO₄⁻) = 3.70 × 10⁻⁴; n(Fe²⁺) = 1.85 × 10⁻³ mol; c = 0.0740 mol dm⁻³.", 0.0005, "mol/dm³", difficulty=2)],
  P("A blue solution of copper(II) sulfate gives a pale blue precipitate with a few drops of aqueous ammonia, which dissolves in excess ammonia to give a deep blue solution.",
    [pm("The pale blue precipitate is", "Cu(OH)₂", ["CuCO₃", "Cu(NH₃)₄²⁺", "CuO"], "NH₃ acts as a base: Cu²⁺ + 2OH⁻ → Cu(OH)₂.", 1),
     pm("The deep blue ion is", "[Cu(NH₃)₄(H₂O)₂]²⁺", ["[Cu(H₂O)₆]²⁺", "Cu₂O", "[CuCl₄]²⁻"], "NH₃ replaces four water ligands.", 1),
     pn("Number of dative bonds in the deep blue complex.", 6, "Four NH₃ and two H₂O ligands: coordination number 6.", 0, None, 1),
     po("Explain why the colour of the solution changes when ammonia replaces water as the ligand.", "Different ligands split the d orbitals by a different amount (ligand field), so the energy gap and thus the wavelength of light absorbed changes, giving a different colour.", "1 mark: ligands change d orbital splitting; 1 mark: different light absorbed / different colour.", 2)], figure=cx_fig()),
  [M("Which is NOT a feature of transition metals?", "all their ions are colourless", ["variable oxidation states", "complex formation", "catalytic activity"], "Most of their ions are coloured."),
   M("A ligand is a species that", "donates a lone pair to a metal ion", ["accepts electrons from a metal", "is always positive", "is a transition metal"], "It forms a dative bond."),
   M("The shape of a complex with coordination number 6 is usually", "octahedral", ["linear", "tetrahedral", "trigonal planar"], "Six ligands around a central ion."),
   M("Iron is used as a catalyst in the", "Haber process", ["Contact process", "Solvay process", "lime kiln"], "N₂ + 3H₂ ⇌ 2NH₃ with an iron catalyst."),
   M("The precipitate formed when NaOH is added to Fe³⁺(aq) is", "red-brown", ["blue", "white", "green"], "Fe(OH)₃.")],
  (cx_fig(), "[Cu(H₂O)₆]²⁺: an octahedral complex with six water ligands around a copper(II) ion.", "A copper ion at the centre with six water molecules around it in an octahedral arrangement."),
  notes=[DATA, "Precipitate colours are standard textbook descriptions; check against the board's list. Stability constants, ligand-field theory and spin-only magnetism are not covered."], minutes=45, prereq=["gceal-chemistry-electrode"])

# =============================================================== 3. ORGANIC CHEMISTRY
ch = p.chapter("ch-organic", "Organic chemistry: carbonyls, acids, arenes, amines and polymers", SYL % "organic chemistry")

# ---- 3.1 carbonyls
cC, cH, cO = 62.1 / 12, 10.3 / 1, 27.6 / 16; rr = (cC / cO, cH / cO, 1.0)
assert abs(rr[0] - 3.0) < 0.03 and abs(rr[1] - 6.0) < 0.05
m_cn = 5.8 / 58.0 * 85.0 * 0.70; assert abs(m_cn - 5.95) < 1e-9
m_etoh = 4.4 / 44.0 * 46.0; assert abs(m_etoh - 4.6) < 1e-9
def carb_fig():
    it = [RECT(130, 15, 140, 36, fill="lightyellow"), T(200, 38, "unknown C=O compound", 12), LINE(200, 52, 200, 80, width=2, arrow="end"),
          RECT(110, 82, 180, 36, fill="lightorange"), T(200, 105, "2,4-DNPH: orange precipitate", 12),
          LINE(160, 120, 90, 150, width=2, arrow="end"), LINE(240, 120, 310, 150, width=2, arrow="end"),
          RECT(10, 152, 160, 50, fill="lightgreen"), T(90, 172, "Tollens': silver mirror", 12), T(90, 190, "→ aldehyde", 12, bold=True),
          RECT(230, 152, 160, 50, fill="lightblue"), T(310, 172, "no silver mirror", 12), T(310, 190, "→ ketone", 12, bold=True)]
    return shapes(it, 400, 215)
lesson(ch, "carbonyls", "Aldehydes and ketones",
  ["Name aldehydes and ketones and describe their preparation by oxidation of alcohols",
   "Describe nucleophilic addition (with HCN) and reduction (with NaBH₄) and the mechanism of addition",
   "Use 2,4-DNPH, Tollens' and Fehling's tests to identify carbonyl compounds and deduce formulae"],
  [("definition", "Aldehydes and ketones",
    "Both contain the **carbonyl group C=O**. In an **aldehyde** the carbonyl carbon is at the end of a chain (–CHO, suffix **-al**: ethanal CH₃CHO); in a **ketone** it is in the middle (suffix **-one**: propanone CH₃COCH₃). They are made by oxidising alcohols: primary → aldehyde (distil off as formed), secondary → ketone, using acidified K₂Cr₂O₇.\n\n"
    "The C=O bond is polar (C δ⁺, O δ⁻); the carbon is attacked by nucleophiles."),
   ("formule", "Nucleophilic addition and reduction",
    "**With HCN** (KCN + dilute acid): C=O + HCN → C(OH)CN, a hydroxynitrile; the nucleophile CN⁻ attacks C δ⁺, the intermediate anion then picks up H⁺. The product has **one more carbon** (useful in synthesis).\n\n"
    "**Reduction** with NaBH₄ (or LiAlH₄): aldehyde → primary alcohol; ketone → secondary alcohol (H⁻ is the nucleophile). Aldehydes are also **oxidised** easily to carboxylic acids; ketones are not.",
    None),
   ("methode", "Tests",
    "- **2,4-DNPH** (2,4-dinitrophenylhydrazine): orange/yellow precipitate with any aldehyde or ketone (the melting point of the crystals identifies the compound).\n- **Tollens' reagent** (ammoniacal AgNO₃, warm): a **silver mirror** with aldehydes only (Ag⁺ → Ag).\n- **Fehling's/Benedict's solution** (warm): blue → brick-red Cu₂O with aliphatic aldehydes only.\n- **Iodoform test** (I₂/NaOH): yellow precipitate CHI₃ with CH₃CO– (or CH₃CH(OH)–) compounds."),
   ("pieges", "Common mistakes",
    "- Using 2,4-DNPH to distinguish an aldehyde from a ketone: it detects only C=O in general.\n- Forgetting to distil an aldehyde as it forms (it is further oxidised to the acid otherwise).\n- Saying that NaBH₄ reduces C=C bonds: it does not (under these conditions).\n- Drawing the curly arrow from the C atom instead of from the nucleophile's lone pair.")],
  [("Example 1 — Deducing a formula", "A compound has 62.1 % C, 10.3 % H and 27.6 % O by mass and M = 58. It forms an orange precipitate with 2,4-DNPH but no silver mirror with Tollens' reagent. Identify it.",
    ["Moles: C 62.1/12 = 5.18; H 10.3/1 = 10.3; O 27.6/16 = 1.73. Ratio ÷ 1.73 = 3 : 6 : 1, so the empirical formula is C₃H₆O (M = 58, the same).", "The 2,4-DNPH test shows C=O; no silver mirror means it is not an aldehyde, so it is a ketone.", "Propanone, CH₃COCH₃."], "Propanone (CH₃COCH₃)"),
   ("Example 2 — Mechanism and yield", "5.8 g of propanone (M = 58) reacts with HCN to give (CH₃)₂C(OH)CN (M = 85). Describe the first step of the mechanism and find the mass obtained at a 70 % yield.",
    ["Step 1: the cyanide ion CN⁻ (nucleophile) attacks the δ⁺ carbon of C=O; the π electrons move to oxygen, giving an anion (alkoxide) intermediate; step 2: this picks up H⁺ (from HCN or acid) to form the hydroxynitrile.", "n(propanone) = 5.8/58 = 0.100 mol; theoretical mass = 0.100 × 85 = 8.5 g.", "Actual mass = 0.70 × 8.5 = 5.95 g."], "Nucleophilic addition; 5.95 g")],
  [M("Which test gives a silver mirror?", "ethanal with Tollens' reagent", ["propanone with Tollens' reagent", "ethanol with 2,4-DNPH", "propanone with Fehling's solution"], "Only aldehydes reduce Tollens' reagent."),
   N("What mass of ethanol is obtained by reducing 4.4 g of ethanal (M = 44) completely (g, 2 s.f.)?", m_etoh, "n(CH₃CHO) = 0.100 mol → 0.100 mol ethanol × 46 = 4.6 g.", 0.05, "g"),
   TF("True or false: propanone can be oxidised easily by acidified potassium dichromate(VI).", False, "A ketone has no H on the carbonyl carbon, so it resists mild oxidation.")],
  [M("The reaction of an aldehyde with HCN is", "nucleophilic addition", ["electrophilic addition", "free-radical substitution", "elimination"], "CN⁻ attacks the δ⁺ carbon of C=O."),
   M("Which compound gives a yellow precipitate (CHI₃) with alkaline iodine?", "propanone", ["propanal", "ethane", "methanol"], "It contains the CH₃CO– group."),
   ],
  P("Compounds X (CH₃CH₂CHO) and Y (CH₃COCH₃) are isomers of formula C₃H₆O.",
    [pm("Which reagent distinguishes X from Y?", "Tollens' reagent", ["2,4-DNPH", "bromine water", "NaBH₄ followed by water"], "X gives a silver mirror; Y does not.", 1),
     pm("Reduction of X with NaBH₄ gives", "propan-1-ol", ["propan-2-ol", "propanoic acid", "propane"], "An aldehyde is reduced to a primary alcohol.", 1),
     pm("Reduction of Y with NaBH₄ gives", "propan-2-ol", ["propan-1-ol", "propanal", "propene"], "A ketone is reduced to a secondary alcohol.", 1),
     po("Draw (describe) the product of the reaction of X with HCN and explain how the carbon chain changes.", "CH₃CH₂CH(OH)CN: 2-hydroxybutanenitrile. The carbon chain is lengthened by one carbon (the CN carbon).", "1 mark: correct hydroxynitrile; 1 mark: chain lengthened by one carbon.", 2)], figure=carb_fig()),
  [M("The functional group of a ketone is", "C=O inside the carbon chain", ["–CHO at the chain end", "–OH", "–COOH"], "A ketone has a carbonyl bonded to two carbons."),
   M("Fehling's solution with an aldehyde gives", "a brick-red precipitate", ["a silver mirror", "no change", "a yellow precipitate"], "Cu²⁺ is reduced to Cu₂O."),
   M("The reagent used to reduce a ketone to a secondary alcohol is", "NaBH₄", ["K₂Cr₂O₇/H⁺", "concentrated H₂SO₄", "HCN"], "A source of the hydride nucleophile."),
   M("The empirical formula of a compound with 40.0 % C, 6.7 % H and 53.3 % O is", "CH₂O", ["CHO", "C₂H₄O", "CH₃O"], "Moles 3.33 : 6.7 : 3.33 = 1 : 2 : 1."),
   M("The product of the oxidation of propan-2-ol with acidified dichromate is", "propanone", ["propanal", "propanoic acid", "propene"], "A secondary alcohol gives a ketone.")],
  (carb_fig(), "Distinguishing aldehydes from ketones.", "A flow chart: an unknown carbonyl compound gives an orange precipitate with 2,4-DNPH; Tollens' reagent then separates an aldehyde (silver mirror) from a ketone (no mirror)."),
  notes=[DATA, "Mechanism drawn in words only (no curly arrows). Reagent details (Tollens' preparation, Fehling's) are descriptive; the practical syllabus may require more."], minutes=40)

# ---- 3.2 carboxylic acids and derivatives
m_ee = 6.0 / 60.0 * 88.0 * 0.65; assert abs(m_ee - 5.72) < 1e-9
Mtri = 57 * 12 + 110 + 6 * 16; n_tri = 1000 / Mtri; m_NaOH = 3 * n_tri * 40.0; m_soap = 3 * n_tri * 306.0
assert Mtri == 890 and abs(m_NaOH - 134.8) < 0.2 and abs(m_soap - 1031.5) < 0.6
V_CO2a = 6.0 / 60.0 / 2 * 24.0; assert abs(V_CO2a - 1.20) < 1e-9
def acid_fig():
    it = [RECT(150, 100, 100, 40, fill="lightyellow"), T(200, 125, "R–COOH", 14, bold=True),
          RECT(150, 20, 100, 36, fill="lightorange"), T(200, 43, "R–COCl", 13), RECT(10, 100, 100, 40, fill="lightgreen"), T(60, 125, "ester R–COOR′", 12),
          RECT(290, 100, 100, 40, fill="lightblue"), T(340, 125, "amide R–CONH₂", 12), RECT(130, 175, 140, 36, fill="lightgrey"), T(200, 198, "carboxylate salt", 13),
          LINE(200, 98, 200, 58, width=2, arrow="end"), T(250, 82, "SOCl₂ / PCl₅", 11, anchor="start"),
          LINE(148, 120, 112, 120, width=2, arrow="end"), T(130, 108, "R′OH, H⁺", 11), LINE(252, 120, 288, 120, width=2, arrow="end"), T(270, 108, "NH₃ (via RCOCl)", 11),
          LINE(200, 142, 200, 173, width=2, arrow="end"), T(210, 160, "NaOH / Na₂CO₃", 11, anchor="start")]
    return shapes(it, 400, 230)
lesson(ch, "acids", "Carboxylic acids and their derivatives",
  ["Describe the acidity and reactions of carboxylic acids and the esterification and hydrolysis of esters",
   "Describe the preparation and reactions of acyl chlorides and amides",
   "Explain the saponification of fats and oils and calculate masses and yields"],
  [("definition", "Carboxylic acids",
    "The functional group is **–COOH** (suffix -oic acid). They are **weak acids** (ethanoic acid pK_a ≈ 4.8, K_a ≈ 1.8 × 10⁻⁵) because the carboxylate ion is stabilised by delocalisation of the charge over the two oxygen atoms; they are stronger than alcohols and phenol. Reactions of an acid: with metals (H₂), with alkalis (salt + water), with **carbonates (CO₂ effervescence: the test that distinguishes them from phenols and alcohols)**.\n\n"
    "Boiling points are high because of hydrogen bonding (dimers); the lower members are soluble in water."),
   ("formule", "Esters",
    "**Esterification**: RCOOH + R′OH ⇌ RCOOR′ + H₂O, catalysed by concentrated H₂SO₄ and heated under reflux (reversible, K_c small). Esters have fruity smells and are used as flavourings, solvents and perfumes.\n\n"
    "**Hydrolysis**: with acid (reversible) the ester gives acid + alcohol; with **alkali** (NaOH, heat) it goes to completion giving the **carboxylate salt** + alcohol. The hydrolysis of a fat or oil (a **triglyceride**, an ester of glycerol with three fatty acids) with NaOH is **saponification**, giving glycerol and **soap** (sodium salts of fatty acids).",
    None),
   ("retenir", "Acyl chlorides and amides",
    "**Acyl chlorides** RCOCl (made from RCOOH + SOCl₂ or PCl₅) are far more reactive than the acids: they react at room temperature with water (→ acid + HCl), alcohols (→ ester + HCl), ammonia (→ amide) and amines (→ N-substituted amide): *nucleophilic addition–elimination*. **Amides** RCONH₂ are neutral and are hydrolysed by hot acid or alkali.\n\nSoap from palm oil or palm kernel oil with an alkali is a traditional local product."),
   ("pieges", "Common mistakes",
    "- Expecting complete conversion in an esterification (it is an equilibrium: use excess of one reagent or remove water).\n- Writing the ester as R′COOR: the acid part is the R–CO– part, the alcohol part follows the O.\n- Forgetting that alkaline hydrolysis gives a salt, not the free acid.\n- Saying acyl chlorides are less reactive than acids.")],
  [("Example 1 — Ester yield", "6.0 g of ethanoic acid (M = 60) is heated with excess ethanol and a little concentrated sulfuric acid to give ethyl ethanoate (M = 88). The yield is 65 %. Find the mass of ester.",
    ["n(CH₃COOH) = 6.0/60 = 0.100 mol; theoretical ester = 0.100 mol.", "Theoretical mass = 0.100 × 88 = 8.8 g.", "Actual = 0.65 × 8.8 = 5.72 g."], "5.7 g of ethyl ethanoate"),
   ("Example 2 — Saponification", "What mass of NaOH is needed to saponify 1.00 kg of tristearin, (C₁₇H₃₅COO)₃C₃H₅ (M = 890)? Find also the mass of soap, sodium stearate C₁₇H₃₅COONa (M = 306).",
    ["n(tristearin) = 1000/890 = 1.124 mol; each mole needs 3 mol NaOH, so n(NaOH) = 3.37 mol.", "Mass of NaOH = 3.37 × 40.0 = 135 g.", "Soap formed = 3.37 mol × 306 = 1.03 × 10³ g (about 1.03 kg), plus glycerol."], "135 g NaOH; 1.03 kg soap")],
  [M("Which reagent gives CO₂ with a carboxylic acid but not with a phenol?", "sodium carbonate solution", ["sodium hydroxide solution", "sodium metal", "bromine water"], "Phenol (pK_a about 10) is too weak an acid to release CO₂ from carbonate."),
   N("What volume of CO₂ (dm³, 3 s.f.) is released at rtp when 6.0 g ethanoic acid reacts with excess sodium carbonate (2CH₃COOH + Na₂CO₃ → 2CH₃COONa + H₂O + CO₂)?", sg(V_CO2a, 3), "n(acid) = 0.100 mol → n(CO₂) = 0.050 mol → 1.20 dm³ (24.0 dm³/mol).", 0.02, "dm³"),
   TF("True or false: hydrolysis of an ester with hot aqueous NaOH goes essentially to completion.", True, "The carboxylic acid formed is converted into its sodium salt, which removes it from the equilibrium.")],
  [N("What mass of CH₃COOC₂H₅ (M = 88) can be obtained from 4.6 g of ethanol (M = 46) with excess ethanoic acid at a 60 % yield (g, 3 s.f.)?", sg(4.6 / 46 * 88 * 0.60, 3), "n(ethanol) = 0.100 mol; theoretical = 8.80 g; × 0.60 = 5.28 g.", 0.03, "g", difficulty=2),
   M("Which is the most reactive towards water at room temperature?", "ethanoyl chloride", ["ethanoic acid", "ethyl ethanoate", "ethanamide"], "Acyl chlorides hydrolyse immediately, releasing HCl fumes.")],
  P("Ethyl ethanoate is hydrolysed by hot aqueous sodium hydroxide: CH₃COOC₂H₅ + NaOH → CH₃COONa + C₂H₅OH. 4.40 g of the ester (M = 88) is used with excess NaOH.",
    [pn("Moles of ester.", 0.0500, "4.40/88 = 0.0500 mol.", 0.0005, "mol", 1),
     pn("Mass of sodium ethanoate formed (M = 82; g, 3 s.f.).", sg(0.0500 * 82, 3), "0.0500 × 82 = 4.10 g.", 0.03, "g", 1),
     pn("Mass of ethanol formed (g, 3 s.f.).", sg(0.0500 * 46, 3), "0.0500 × 46 = 2.30 g.", 0.02, "g", 1),
     pm("This reaction is described as", "alkaline hydrolysis (saponification)", ["esterification", "condensation polymerisation", "reduction"], "An ester is split by water in the presence of base.", 1)], figure=acid_fig()),
  [M("The functional group of a carboxylic acid is", "–COOH", ["–CHO", "–COCl", "–CONH₂"], "Carboxyl group."),
   M("Esterification is catalysed by", "concentrated sulfuric acid", ["aqueous NaOH", "UV light", "iron"], "Acid catalyst and heat."),
   M("Soap is made by heating a fat or oil with", "sodium hydroxide solution", ["hydrochloric acid", "water only", "bromine"], "Saponification."),
   M("Which is formed when an acyl chloride reacts with ammonia?", "an amide", ["an amine", "an ester", "an acid"], "RCOCl + 2NH₃ → RCONH₂ + NH₄Cl."),
   M("The carboxylic acids have higher boiling points than alcohols of similar M because of", "stronger hydrogen bonding (dimers)", ["ionic bonding", "covalent bonding between molecules", "metallic bonding"], "Two hydrogen bonds link two acid molecules.")],
  (acid_fig(), "Carboxylic acid and its derivatives: reagents for the main conversions.", "A central box for the carboxylic acid with arrows to an acyl chloride, an ester, an amide and a carboxylate salt, each labelled with a reagent."),
  notes=[DATA + " M(tristearin) = 890, sodium stearate 306.", "Local soap-making with palm oil is mentioned generally; check local practice. Mechanism of addition–elimination is named only."], minutes=40, prereq=["gceal-chemistry-carbonyls"])

# ---- 3.3 aromatic
dH_calc = 3 * (-120); dH_obs = -208; stab = dH_obs - dH_calc; assert stab == 152
m_nb = 7.8 / 78.0 * 123.0 * 0.75; assert abs(m_nb - 9.225) < 1e-9
def benzene_fig():
    pts = []
    for i in range(6):
        a = math.radians(60 * i + 30)
        pts += [150 + 70 * math.cos(a), 120 - 70 * math.sin(a)]
    it = [POLY(pts, width=3), CIRCLE(150, 120, 46, stroke="blue", width=2), T(150, 20, "benzene C₆H₆", 14, bold=True), T(300, 90, "planar hexagon", 12, anchor="start"), T(300, 112, "all C–C = 0.139 nm", 12, anchor="start"),
          T(300, 134, "120° bond angles", 12, anchor="start"), T(300, 156, "delocalised π ring", 12, anchor="start", color="blue")]
    return shapes(it, 440, 230)
lesson(ch, "arenes", "Benzene and aromatic chemistry",
  ["Describe the structure of benzene and the evidence for delocalisation (bond lengths and enthalpy of hydrogenation)",
   "Describe electrophilic substitution of benzene (nitration, halogenation, Friedel–Crafts) and its mechanism",
   "Compare the reactivity of benzene, alkenes and phenol"],
  [("definition", "Structure of benzene",
    "Benzene, C₆H₆, is a **planar hexagon** with bond angles of 120°; each carbon uses three σ bonds, and the remaining p electrons overlap to form a **delocalised π ring** above and below the plane. Evidence: all six C–C bonds have the same length (0.139 nm, between C–C 0.154 and C=C 0.134 nm); benzene does not decolourise bromine water; and the **enthalpy of hydrogenation** is about −208 kJ mol⁻¹, much less than the −360 kJ mol⁻¹ expected for three localised C=C bonds (3 × −120): the difference, **152 kJ mol⁻¹**, is the delocalisation (stabilisation) energy."),
   ("retenir", "Electrophilic substitution",
    "Benzene's stable ring makes it undergo **substitution** (keeping the ring) rather than addition. The electron-rich π system attacks an **electrophile**.\n\n- **Nitration**: conc. HNO₃ + conc. H₂SO₄, about 50 °C → nitrobenzene; the electrophile is NO₂⁺ (HNO₃ + 2H₂SO₄ → NO₂⁺ + H₃O⁺ + 2HSO₄⁻).\n- **Halogenation**: Br₂ with FeBr₃ (or Fe) → bromobenzene.\n- **Friedel–Crafts**: RCl or RCOCl with AlCl₃ → alkylbenzene or phenyl ketone."),
   ("methode", "Mechanism",
    "1. The π electrons of the ring attack the electrophile E⁺, forming a bond to one carbon: the ring loses aromaticity and becomes a positively charged intermediate (arenium ion).\n2. A base (HSO₄⁻ or FeBr₄⁻) removes H⁺ from the carbon bearing E, and the π system is restored; the catalyst is regenerated.\n\n**Phenol** (C₆H₅OH) is more reactive than benzene (the O lone pair feeds into the ring): bromine water gives a white precipitate of 2,4,6-tribromophenol with no catalyst. Phenol is a weak acid (pK_a about 10): it reacts with NaOH but not with carbonates."),
   ("pieges", "Common mistakes",
    "- Drawing benzene with alternating single and double bonds as if they were fixed: use the delocalised circle (or Kekulé forms with a note).\n- Using bromine water with benzene as a test for unsaturation: no reaction occurs.\n- Forgetting the catalyst (FeBr₃, AlCl₃, H₂SO₄) in electrophilic substitution.\n- Saying benzene is unreactive: it reacts, but by substitution under harsher conditions.")],
  [("Example 1 — Delocalisation energy", "ΔH_hydrogenation is −120 kJ mol⁻¹ for cyclohexene and −208 kJ mol⁻¹ for benzene. Use these to estimate the stabilisation of benzene.",
    ["If benzene had three separate C=C bonds, the expected value = 3 × (−120) = −360 kJ mol⁻¹.", "The observed value is −208 kJ mol⁻¹: less energy released, so benzene is more stable than expected.", "Stabilisation = −208 − (−360) = 152 kJ mol⁻¹ (the delocalisation energy)."], "152 kJ mol⁻¹"),
   ("Example 2 — Nitration yield", "7.8 g of benzene (M = 78) is nitrated to nitrobenzene (M = 123) with a yield of 75 %. Find the mass of nitrobenzene and name the electrophile.",
    ["n(C₆H₆) = 7.8/78 = 0.100 mol; theoretical = 0.100 × 123 = 12.3 g.", "Actual = 0.75 × 12.3 = 9.2 g.", "The electrophile is the nitronium ion, NO₂⁺, formed from HNO₃ and H₂SO₄."], "9.2 g; NO₂⁺")],
  [M("The electrophile in the nitration of benzene is", "NO₂⁺", ["NO₃⁻", "NH₃", "H₃O⁺"], "Formed from conc. HNO₃ and conc. H₂SO₄."),
   N("The C–C bonds in benzene are all the same length. State this length in nm.", 0.139, "0.139 nm, between 0.154 (C–C) and 0.134 (C=C).", 0.002, "nm"),
   TF("True or false: benzene decolourises bromine water readily.", False, "The delocalised ring resists addition; benzene reacts only with Br₂ and a catalyst, by substitution.")],
  [N("Calculate the delocalisation energy (kJ mol⁻¹) if ΔH_hyd(cyclohexene) = −120 and ΔH_hyd(benzene) = −208 kJ mol⁻¹.", stab, "(−208) − 3(−120) = +152 kJ mol⁻¹ (benzene is more stable).", 0.5, "kJ/mol", difficulty=2),
   M("Phenol reacts with bromine water without a catalyst because", "the lone pair on oxygen increases the electron density of the ring", ["phenol is an alkene", "bromine is more reactive in water", "phenol is a salt"], "The activated ring reacts as an electron-rich nucleophile.")],
  P("Benzene is converted to bromobenzene with bromine in the presence of iron(III) bromide.",
    [pm("The reaction type is", "electrophilic substitution", ["electrophilic addition", "nucleophilic substitution", "radical addition"], "H is replaced by Br while the ring is retained.", 1),
     pm("The role of FeBr₃ is to", "polarise Br₂ to give an electrophile (Br⁺)", ["reduce benzene", "act as a solvent", "form a precipitate"], "FeBr₃ accepts Br⁻ from Br₂ to generate Br⁺ character.", 1),
     pn("Mass of bromobenzene (M = 157; g, 3 s.f.) from 3.90 g of benzene (M = 78) at a 60 % yield.", sg(3.90 / 78 * 157 * 0.60, 3), "n = 0.0500 mol; theoretical = 7.85 g; 60 % = 4.71 g.", 0.03, "g", 1),
     po("Explain why benzene undergoes substitution rather than addition.", "Addition would destroy the delocalised π system and lose the 152 kJ mol⁻¹ of stabilisation energy; substitution keeps the stable ring.", "1 mark: delocalised ring is stable; 1 mark: addition would lose stabilisation energy.", 2)], figure=benzene_fig()),
  [M("The shape of the benzene molecule is", "planar hexagonal", ["tetrahedral", "pyramidal", "linear"], "All six C and H atoms lie in one plane."),
   M("Which is the catalyst for the Friedel–Crafts alkylation?", "AlCl₃", ["FeBr₃", "H₂SO₄", "Ni"], "It generates the carbocation electrophile."),
   M("Phenol is", "a weak acid", ["a strong acid", "a base", "neutral and unreactive"], "pK_a about 10."),
   M("The arenium ion is", "the positively charged intermediate in electrophilic substitution", ["the product", "the electrophile", "the catalyst"], "The ring has lost its aromaticity temporarily."),
   M("Compared with ethene, benzene reacts with bromine", "less readily, by substitution", ["more readily, by addition", "in the same way", "not at all in any conditions"], "Delocalisation stabilises the ring.")],
  (benzene_fig(), "Benzene: planar hexagon with a delocalised π ring (shown as a circle).", "A hexagon with an inner circle, labelled with bond length 0.139 nm and 120 degree bond angles."),
  notes=[DATA + " ΔH_hyd values (cyclohexene −120, benzene −208 kJ mol⁻¹) are textbook values.", "Orientation effects (activating/deactivating groups) and diazonium/azo dyes are not covered; check the syllabus."], minutes=40, prereq=["gceal-chemistry-acids"])

# ---- 3.4 amines, amino acids, proteins
m_an = 12.3 / 123.0 * 93.0 * 0.80; assert abs(m_an - 7.44) < 1e-9
M_gly, M_ala = 75.0, 89.0; M_dip = M_gly + M_ala - 18.0; pI_gly = (2.34 + 9.60) / 2; assert M_dip == 146.0 and abs(pI_gly - 5.97) < 1e-9
n_tri = 20 ** 3; assert n_tri == 8000
def aa_fig():
    it = [T(200, 25, "amino acid (zwitterion form)", 14, bold=True), T(200, 55, "H₃N⁺ – CH(R) – COO⁻", 15),
          T(200, 100, "two amino acids join with loss of water", 13),
          T(200, 135, "H₂N–CH(R₁)–CO–NH–CH(R₂)–COOH", 14), RECT(188, 118, 34, 24, stroke="red", width=2), T(200, 168, "peptide bond (amide link)", 13, color="red"),
          T(200, 205, "proteins: chains of many amino acids", 13)]
    return shapes(it, 400, 225)
lesson(ch, "amines", "Amines, amino acids and proteins",
  ["Describe the basicity and preparation of amines and compare aliphatic and aromatic amines",
   "Describe amino acids as amphoteric zwitterions, the formation of peptides and protein structure",
   "Calculate the mass of peptides and the isoelectric point; describe electrophoresis and chromatography of amino acids"],
  [("definition", "Amines",
    "Amines contain the –NH₂ (primary), –NHR (secondary) or –NR₂ (tertiary) group; they are derived from ammonia. The lone pair on N makes them **bases** and **nucleophiles**: RNH₂ + H⁺ → RNH₃⁺. Alkylamines are **stronger bases** than ammonia (alkyl groups push electrons towards N), while **phenylamine** (aniline) is a much weaker base because the lone pair is delocalised into the ring.\n\n"
    "Preparation: halogenoalkane + excess NH₃ (ethanol, heat) → primary amine; reduction of a nitrile (LiAlH₄); phenylamine from nitrobenzene with Sn/conc. HCl, then NaOH."),
   ("retenir", "Amino acids",
    "An **α-amino acid** is RCH(NH₂)COOH. In solution it exists mainly as a **zwitterion** H₃N⁺CH(R)COO⁻, with no overall charge. It is **amphoteric**: with acid the –COO⁻ is protonated (cation), with alkali the –NH₃⁺ loses H⁺ (anion). At the **isoelectric point (pI)** the net charge is zero; for glycine pI = (pK₁ + pK₂)/2 = (2.34 + 9.60)/2 = 5.97.\n\nIn electrophoresis, at pH below the pI the amino acid moves to the cathode, above the pI to the anode."),
   ("methode", "Peptides and proteins",
    "Two amino acids join by a **condensation** reaction forming a **peptide (amide) bond** –CO–NH– and water. A chain of many is a polypeptide or **protein**. **Primary** structure: the amino-acid sequence; **secondary**: α-helix or β-sheet held by hydrogen bonds; **tertiary**: folding of the chain by R-group interactions (hydrogen bonds, ionic bonds, disulfide bridges S–S, hydrophobic interactions); **quaternary**: several chains.\n\nHydrolysis by hot 6 mol dm⁻³ HCl gives the amino acids, which can be separated by **chromatography** (R_f values, visualised with ninhydrin) or electrophoresis."),
   ("pieges", "Common mistakes",
    "- Forgetting to subtract the water when finding the mass of a peptide (M = ΣM(amino acids) − (n − 1) × 18).\n- Writing an amino acid as uncharged in water: it is mainly the zwitterion.\n- Saying aromatic amines are stronger bases than ammonia.\n- Confusing primary and secondary structure: the first is sequence, the second is local shape.")],
  [("Example 1 — A dipeptide", "Glycine (NH₂CH₂COOH, M = 75) and alanine (NH₂CH(CH₃)COOH, M = 89) form a dipeptide. Find its molar mass and the number of different dipeptides possible from these two amino acids.",
    ["Condensation loses one molecule of water (M = 18): M = 75 + 89 − 18 = 146.", "Gly–Ala (glycine's –COOH joined to alanine's –NH₂) and Ala–Gly are different compounds.", "So two different dipeptides are possible (with the same molar mass 146)."], "M = 146; two dipeptides: Gly–Ala and Ala–Gly"),
   ("Example 2 — Electrophoresis", "For glycine pK₁ = 2.34 (–COOH) and pK₂ = 9.60 (–NH₃⁺). Find the isoelectric point and the direction of movement at pH 2 and pH 11.",
    ["pI = (2.34 + 9.60)/2 = 5.97.", "At pH 2 (below the pI) the amino acid is mostly the cation H₃N⁺CH₂COOH, which moves to the cathode (−).", "At pH 11 (above the pI) it is the anion H₂NCH₂COO⁻, which moves to the anode (+)."], "pI = 5.97; cathode at pH 2, anode at pH 11")],
  [N("Find the molar mass of the tripeptide formed from three glycine molecules (M = 75 each).", 3 * 75 - 2 * 18, "M = 3 × 75 − 2 × 18 = 189.", 0.5, "g/mol"),
   M("A zwitterion has", "a positive and a negative charge in the same molecule", ["no polar groups", "only a positive charge", "a negative charge only"], "H₃N⁺–CH(R)–COO⁻."),
   TF("True or false: phenylamine is a stronger base than ethylamine.", False, "The N lone pair is delocalised into the benzene ring, so it is less available to accept H⁺.")],
  [N("How many different tripeptides can be made if each of the three amino acids Gly, Ala and Ser is used once (order matters)?", 6, "3! = 6 sequences.", 0, None, difficulty=2),
   M("At a pH below its isoelectric point an amino acid in an electric field moves towards the", "cathode (negative electrode)", ["anode (positive electrode)", "neither", "both"], "It carries a net positive charge.")],
  P("Nitrobenzene (M = 123) is reduced to phenylamine (M = 93) using tin and concentrated hydrochloric acid, then sodium hydroxide. 12.3 g of nitrobenzene is used with an 80 % yield.",
    [pn("Moles of nitrobenzene.", 0.100, "12.3/123 = 0.100 mol.", 0.001, "mol", 1),
     pn("Mass of phenylamine (g, 3 s.f.).", sg(m_an, 3), "Theoretical = 0.100 × 93 = 9.30 g; × 0.80 = 7.44 g.", 0.03, "g", 1),
     pm("Sodium hydroxide is added at the end to", "liberate the free amine from its salt", ["oxidise the amine", "reduce the nitro group", "make an amide"], "The product is first formed as C₆H₅NH₃⁺ (salt) in acid.", 1),
     po("Explain why phenylamine is a weaker base than ethylamine.", "In phenylamine the lone pair on nitrogen is delocalised into the benzene ring and is less available to accept a proton; in ethylamine the alkyl group pushes electrons towards N, making the lone pair more available.", "1 mark: lone pair delocalised in the ring (phenylamine); 1 mark: electron-donating alkyl group in ethylamine.", 2)], figure=aa_fig()),
  [M("An α-amino acid contains", "an –NH₂ group and a –COOH group on the same carbon", ["two –COOH groups", "only an –NH₂ group", "a C=O and a C=C"], "R–CH(NH₂)–COOH."),
   M("A peptide bond is", "–CO–NH–", ["–CH₂–O–", "–CO–O–", "–NH–NH–"], "An amide link."),
   M("The secondary structure of a protein is held by", "hydrogen bonds along the chain", ["peptide bonds only", "disulfide bridges only", "ionic bonds only"], "α-helices and β-sheets."),
   M("A primary amine is prepared from a halogenoalkane by heating with", "excess ammonia in ethanol", ["aqueous NaOH", "dilute acid", "bromine water"], "NH₃ is the nucleophile."),
   M("The isoelectric point is the pH at which an amino acid", "has no net charge", ["has a +2 charge", "is a gas", "is insoluble in water always"], "It is mostly the zwitterion.")],
  (aa_fig(), "An amino acid as a zwitterion, and the peptide bond in a dipeptide.", "Text drawings of the zwitterion form of an amino acid and of a dipeptide with the peptide bond boxed."),
  notes=[DATA, "pK values for glycine (2.34 and 9.60) are textbook values. Basic strengths are described qualitatively. Ninhydrin test and R_f are mentioned only."], minutes=40, prereq=["gceal-chemistry-acids"])

# ---- 3.5 polymers
Mnyl = 12 * 12 + 22 + 28 + 32; assert Mnyl == 226
n_ny = 22600 / 226; n_pe = 5.60e4 / 28; assert n_ny == 100 and n_pe == 2000
Mrep = 146 + 116 - 2 * 18; assert Mrep == 226
def poly_fig():
    it = [T(200, 25, "condensation polymerisation (nylon-6,6)", 14, bold=True), T(200, 60, "n HOOC–(CH₂)₄–COOH  +  n H₂N–(CH₂)₆–NH₂", 12),
          LINE(200, 70, 200, 100, width=3, arrow="end"), T(210, 90, "heat", 11, anchor="start"),
          T(200, 124, "–[CO–(CH₂)₄–CO–NH–(CH₂)₆–NH]ₙ–  +  2n H₂O", 12), T(200, 160, "repeat unit: M = 226 g/mol", 13, color="blue"),
          T(200, 195, "addition polymers lose nothing: poly(ethene) = –[CH₂–CH₂]ₙ–", 12)]
    return shapes(it, 400, 215)
lesson(ch, "polymers", "Polymers: addition and condensation",
  ["Distinguish addition and condensation polymers and draw repeat units",
   "Describe polyesters and polyamides (nylon) and their formation from monomers",
   "Discuss properties, biodegradability and disposal of polymers; calculate degrees of polymerisation"],
  [("definition", "Addition polymers",
    "**Addition polymerisation**: unsaturated monomers (alkenes) join with the opening of C=C and **no other product**. Examples: ethene → poly(ethene); propene → poly(propene); chloroethene → PVC; tetrafluoroethene → PTFE. The polymer has a saturated carbon chain with the repeat unit –[CH₂–CH₂]ₙ–; it is made of strong C–C bonds and non-polar, so is **not biodegradable**.\n\n"
    "Properties depend on chain length, branching and intermolecular forces (low-density and high-density poly(ethene))."),
   ("retenir", "Condensation polymers",
    "**Condensation polymerisation**: monomers with two functional groups join by forming a link and losing a small molecule (H₂O or HCl).\n\n- **Polyesters** (e.g. Terylene/PET): diol + dicarboxylic acid (or its acyl chloride) → ester links.\n- **Polyamides** (nylon-6,6): hexane-1,6-diamine + hexanedioic acid (or hexanedioyl chloride) → amide links –CO–NH–. Proteins are natural polyamides (polypeptides); starch and cellulose are polysaccharides made by condensation of sugars.\n\nCondensation polymers contain polar links and can be **hydrolysed** (partly biodegradable)."),
   ("methode", "Repeat units and degree of polymerisation",
    "To draw the repeat unit of an **addition** polymer, open the double bond and show the continuing bonds: CH₂=CHCl gives –[CH₂–CHCl]ₙ–. For a **condensation** polymer, remove the ends and join the two monomers' residues: M(repeat unit) = M(diacid) + M(diamine) − 2 × 18.\n\n"
    "**Degree of polymerisation** n = M(polymer)/M(repeat unit)."),
   ("pieges", "Common mistakes",
    "- Drawing C=C in the repeat unit of an addition polymer.\n- Forgetting the small molecule (H₂O or HCl) in condensation polymerisation.\n- Saying all plastics are biodegradable: addition polymers are not, and plastic waste persists in the environment.\n- Using the monomer mass for the repeat unit of a condensation polymer (two monomers form one unit).")],
  [("Example 1 — Nylon-6,6", "Find the molar mass of the repeat unit of nylon-6,6 from hexanedioic acid (M = 146) and hexane-1,6-diamine (M = 116), and the degree of polymerisation for a polymer of M = 22 600.",
    ["Each repeat unit uses one diacid and one diamine and loses two water molecules: 146 + 116 − 2 × 18 = 226.", "Check: C₁₂H₂₂N₂O₂ = 12 × 12 + 22 × 1 + 2 × 14 + 2 × 16 = 226.", "n = 22 600/226 = 100."], "M(repeat unit) = 226; n = 100"),
   ("Example 2 — Poly(ethene)", "A sample of poly(ethene) has M = 56 000. Find n and give the repeat unit. Explain why it is not biodegradable.",
    ["Repeat unit –[CH₂–CH₂]ₙ–, M = 28; n = 56 000/28 = 2000.", "The chain contains only non-polar C–C and C–H bonds, which microorganisms and water cannot attack.", "So it persists for a very long time as litter; recycling or energy recovery is preferred."], "n = 2000; non-biodegradable due to inert C–C chain")],
  [N("The monomer of PVC is chloroethene, CH₂=CHCl. What is the molar mass (g/mol, 3 s.f.) of the repeat unit?", 62.5, "CH₂–CHCl: 12 + 2 + 12 + 1 + 35.5 = 62.5 g mol⁻¹.", 0.1, "g/mol"),
   M("Which polymer is made by condensation polymerisation?", "nylon-6,6", ["poly(ethene)", "PVC", "poly(propene)"], "Diamine + diacid with loss of water."),
   TF("True or false: addition polymerisation produces water as a by-product.", False, "The monomers simply add together; nothing is lost.")],
  [N("A sample of PVC has M = 125 000. Find the degree of polymerisation (repeat unit 62.5).", 125000 / 62.5, "n = 125 000/62.5 = 2000.", 0.5, None, difficulty=2),
   M("Why are polyesters and polyamides partly biodegradable whereas poly(ethene) is not?", "they have polar ester/amide links that can be hydrolysed", ["they contain C=C bonds", "they are ionic", "they have lower M"], "The polar links are attacked by water, acids or enzymes.")],
  P("Terylene (PET) is formed from benzene-1,4-dicarboxylic acid (M = 166) and ethane-1,2-diol (M = 62).",
    [pm("The type of polymerisation is", "condensation", ["addition", "radical substitution", "electrophilic substitution"], "Ester links form and water is lost.", 1),
     pn("Molar mass of the repeat unit (g/mol).", 166 + 62 - 2 * 18, "166 + 62 − 2 × 18 = 192 g mol⁻¹.", 0.5, "g/mol", 1),
     pn("Degree of polymerisation for M = 19 200.", 19200 / 192, "19 200/192 = 100.", 0.5, None, 1),
     po("Suggest two problems caused by the disposal of plastics and one way to reduce them.", "Plastics persist in the environment (non-biodegradable), blocking drains and harming animals; burning can release toxic gases (HCl from PVC). Reduce by recycling, reuse, using biodegradable polymers and reducing single-use plastics.", "1 mark each for two problems; 1 mark for a valid way to reduce them.", 3)], figure=poly_fig()),
  [M("The repeat unit of poly(ethene) is", "–CH₂–CH₂–", ["–CH=CH–", "–CH₃–", "–CH₂–CH₂–O–"], "No C=C remains."),
   M("In nylon-6,6 the link between monomers is", "an amide link", ["an ester link", "an ether link", "a C–C bond"], "–CO–NH–."),
   M("Proteins are natural", "polyamides", ["polyesters", "polyalkenes", "polysaccharides"], "Peptide bonds are amide links."),
   M("A condensation polymer loses", "a small molecule such as water", ["nothing", "a metal", "a proton only"], "At each link formed."),
   M("The best way to reduce plastic waste is to", "reduce, reuse and recycle", ["burn it in the open", "bury it only", "throw it into rivers"], "Waste hierarchy.")],
  (poly_fig(), "Condensation polymerisation of nylon-6,6 from a diacid and a diamine.", "A scheme showing the diacid and diamine reacting with heat to give a polyamide and water, with the repeat unit mass 226."),
  notes=[DATA, "Local policies on plastic bags and waste are not stated (to be added by a teacher after verification).", "Natural rubber and thermosetting plastics are not covered; check the syllabus."], minutes=35, prereq=["gceal-chemistry-amines"])

# =============================================================== 4. ANALYSIS, INDUSTRY, PRACTICAL
ch = p.chapter("ch-analysis", "Analysis, industry and practical skills", SYL % "analytical techniques, industrial chemistry, practical work")
dbe = lambda c, h, n=0: (2 * c + 2 + n - h) / 2
assert dbe(4, 8) == 1 and dbe(6, 6) == 4 and dbe(3, 6) == 1
Rf = 3.2 / 8.0; nC = 4.4 / 1.1; assert abs(Rf - 0.40) < 1e-12 and abs(nC - 4.0) < 1e-12
ratioP = (48.6 / 12, 8.1 / 1, 43.2 / 16); assert abs(ratioP[0] / ratioP[2] - 1.5) < 0.01 and abs(ratioP[1] / ratioP[2] - 3.0) < 0.02
def nmr_fig():
    X = lambda d: 40 + (5 - d) * 70
    it = [LINE(30, 190, 400, 190, width=2)]
    for i, h in enumerate([15, 45, 45, 15]):
        x = X(4.1) + (i - 1.5) * 9; it.append(LINE(x, 190, x, 190 - h, width=3, color="blue"))
    it.append(LINE(X(2.0), 190, X(2.0), 120, width=3, color="blue"))
    for i, h in enumerate([22, 44, 22]):
        x = X(1.2) + (i - 1) * 9; it.append(LINE(x, 190, x, 190 - h, width=3, color="blue"))
    it += [T(X(4.1), 140, "OCH₂ (q)", 12), T(X(2.0), 108, "CH₃CO (s)", 12), T(X(1.2), 140, "CH₃ (t)", 12)]
    for d in range(0, 6):
        it.append(T(X(d), 212, str(d), 12))
    it += [T(215, 232, "δ / ppm  (ethyl ethanoate)", 12)]
    return shapes(it, 420, 245)
lesson(ch, "analysis", "Mass spectrometry, infrared and NMR spectroscopy",
  ["Interpret mass spectra (molecular ion, fragments, isotope patterns) and use the M+1 peak",
   "Use infrared absorption ranges to identify functional groups",
   "Interpret ¹H NMR spectra (number of signals, chemical shift, integration, splitting) and combine data to deduce a structure"],
  [("definition", "Mass spectrometry",
    "In the mass spectrometer a molecule loses an electron to give the **molecular ion M⁺**; its m/z gives the relative molecular mass. M⁺ breaks into **fragments** whose peaks reveal the structure (m/z 15 CH₃⁺, 29 C₂H₅⁺, 43 C₃H₇⁺ or CH₃CO⁺, 45 COOH⁺, 77 C₆H₅⁺). Compounds with **Br** show M and M+2 peaks of equal height (⁷⁹Br : ⁸¹Br about 1 : 1); with **Cl** about 3 : 1.\n\n"
    "The **M+1 peak** (due to ¹³C, 1.1 % abundance) gives the number of carbons: n ≈ (height of M+1 ÷ height of M) × 100/1.1."),
   ("retenir", "Infrared absorption (wavenumber / cm⁻¹)",
    "A bond absorbs IR when its vibration changes the dipole. Characteristic ranges: **O–H** (alcohol) 3200–3550 broad; **O–H** (carboxylic acid) 2500–3300 very broad; **N–H** 3300–3500; **C–H** 2850–3100; **C≡N** 2200–2260; **C=O** 1680–1750 (strong, sharp); **C=C** 1620–1680; **C–O** 1000–1300. The region below about 1500 cm⁻¹ is the **fingerprint region**, unique to each compound.\n\nExample: a strong band near 1715 cm⁻¹ and no broad O–H means a ketone or aldehyde."),
   ("formule", "¹H NMR",
    "Protons in different chemical environments give separate signals. **Number of signals** = number of environments; **chemical shift** δ (ppm, from TMS = 0): alkyl C–H 0.9–1.5; next to C=O 2.0–2.7; O–CH₂ 3.3–4.3; alkene 4.5–6.5; aromatic 6.5–8.0; aldehyde 9–10; carboxylic acid 10–12. **Integration** gives the ratio of H atoms. **Splitting**: a signal is split into (n + 1) lines by n protons on the adjacent carbons (singlet, doublet, triplet 1:2:1, quartet 1:3:3:1). OH and NH signals vanish on shaking with D₂O.",
    None),
   ("pieges", "Common mistakes",
    "- Reading M+2 for chlorine and bromine the wrong way round (Br 1 : 1, Cl 3 : 1).\n- Counting H atoms from the signal height instead of the integration.\n- Confusing C=O (about 1700 cm⁻¹) and C=C (about 1650 cm⁻¹).\n- Forgetting that equivalent protons do not split each other (for example CH₃–CH₂– gives a triplet and a quartet, each split only by the neighbour).")],
  [("Example 1 — Deducing a structure", "A compound C₄H₈O₂ has a strong IR absorption at 1740 cm⁻¹ and no broad band above 2500 cm⁻¹. Its ¹H NMR has three signals: δ 1.2 (triplet, 3H), δ 2.0 (singlet, 3H), δ 4.1 (quartet, 2H). Identify it.",
    ["Degree of unsaturation = (2 × 4 + 2 − 8)/2 = 1. The strong band at 1740 cm⁻¹ shows C=O; no broad O–H means it is not an acid: an ester.", "δ 4.1 quartet (2H) = O–CH₂ next to CH₃; δ 1.2 triplet (3H) = the CH₃ of the ethyl group; δ 2.0 singlet (3H) = CH₃ next to C=O with no neighbours.", "The compound is CH₃COOCH₂CH₃, ethyl ethanoate."], "Ethyl ethanoate"),
   ("Example 2 — Mass spectrum", "A compound shows peaks at m/z 108 and 110 of equal height, and a fragment at m/z 29. Identify it.",
    ["Equal M and M+2 peaks indicate one bromine atom (⁷⁹Br and ⁸¹Br).", "m/z 29 = C₂H₅⁺. M = 108 = C₂H₅ (29) + ⁷⁹Br (79).", "The compound is bromoethane, CH₃CH₂Br."], "Bromoethane")],
  [N("The molecular formula C₄H₈O₂ has what degree of unsaturation (double bond equivalents)?", 1, "(2 × 4 + 2 − 8)/2 = 1.", 0, None),
   N("In a TLC experiment the spot travels 3.2 cm and the solvent front 8.0 cm. Find R_f.", Rf, "R_f = 3.2/8.0 = 0.40.", 0.005, None),
   TF("True or false: a compound with a molecular ion pair at m/z 78 and 80 in the ratio 3 : 1 contains chlorine.", True, "The ³⁵Cl : ³⁷Cl ratio is about 3 : 1.")],
  [N("In a mass spectrum the M peak has height 100 and the M+1 peak has height 4.4. How many carbon atoms does the molecule contain?", nC, "n ≈ 4.4/100 × 100/1.1 = 4.", 0.2, None, difficulty=2),
   M("A strong, broad absorption between 2500 and 3300 cm⁻¹ together with a strong band at 1710 cm⁻¹ indicates", "a carboxylic acid", ["an ester", "a ketone", "an alkene"], "Very broad O–H and C=O together point to –COOH.")],
  P("Compound P has the composition 48.6 % C, 8.1 % H and 43.2 % O and M = 74. Its IR spectrum has a strong band at 1740 cm⁻¹ and no broad O–H band. Its ¹H NMR spectrum has signals at δ 3.7 (singlet, 3H), δ 2.3 (quartet, 2H) and δ 1.1 (triplet, 3H).",
    [pn("The number of carbon atoms in the molecular formula.", 3, "Empirical formula C₃H₆O₂ (moles 4.05 : 8.1 : 2.70 = 1.5 : 3 : 1 → ×2); M = 74 so the molecular formula is C₃H₆O₂ with 3 C.", 0, None, 1),
     pm("The functional group is", "an ester", ["a carboxylic acid", "an alcohol", "an aldehyde"], "C=O at 1740 cm⁻¹ and no O–H band.", 1),
     pm("The structure of P is", "CH₃CH₂COOCH₃", ["CH₃COOCH₂CH₃", "CH₃CH₂CH₂COOH", "CH₃COCH₂CH₂OH"], "δ 3.7 (3H, s) = O–CH₃; δ 2.3 (2H, q) and δ 1.1 (3H, t) = CH₃CH₂–C=O.", 1),
     po("State and explain why the signal at δ 2.3 is a quartet.", "The CH₂ protons have three neighbouring protons on the adjacent CH₃, so by the n + 1 rule they give 3 + 1 = 4 lines (a quartet).", "1 mark: three adjacent H; 1 mark: n + 1 rule gives four lines.", 2)], figure=nmr_fig()),
  [M("The molecular ion peak in a mass spectrum gives", "the relative molecular mass", ["the number of carbons only", "the functional group", "the boiling point"], "m/z of M⁺ = M for a singly charged ion."),
   M("A band at 1710 cm⁻¹ in the IR spectrum is due to", "C=O", ["O–H", "C≡N", "C–H"], "Carbonyl stretching."),
   M("In ¹H NMR, TMS is used as", "the reference at δ = 0", ["the solvent", "the sample", "the catalyst"], "All shifts are measured from tetramethylsilane."),
   M("A CH₃ group next to a CH₂ group gives a signal that is a", "triplet", ["quartet", "singlet", "doublet"], "n = 2 neighbours: 2 + 1 = 3 lines."),
   M("Which technique tells the relative molecular mass most directly?", "mass spectrometry", ["IR spectroscopy", "¹H NMR", "TLC"], "Molecular ion peak.")],
  (nmr_fig(), "Schematic ¹H NMR spectrum of ethyl ethanoate: a triplet (CH₃), a singlet (CH₃CO) and a quartet (OCH₂).", "A schematic NMR spectrum with a triplet near 1.2 ppm, a singlet near 2.0 ppm and a quartet near 4.1 ppm."),
  notes=["Absorption and chemical shift ranges are typical textbook values; the board's data booklet values may differ slightly.", "¹³C NMR and mass spectrometry of larger fragments are not covered; check the syllabus."], minutes=45)

# ---- industrial chemistry
mNH3 = 1.00 * 34.0 / 28.0; mH2SO4 = 1.00 * 98.0 / 32.0
xN2, xH2, xNH3 = 0.8, 2.4, 0.4; ntot = xN2 + xH2 + xNH3; fNH3 = xNH3 / ntot
assert abs(mNH3 - 1.214) < 0.001 and abs(mH2SO4 - 3.0625) < 1e-6 and abs(ntot - 3.6) < 1e-9 and abs(fNH3 - 0.1111) < 0.001
pN_amnit = 28.0 / 80.0 * 100; pN_urea = 28.0 / 60.0 * 100; pN_as = 28.0 / 132.0 * 100; massUrea = 25.0 / (28.0 / 60.0)
assert abs(pN_amnit - 35.0) < 1e-9 and abs(pN_urea - 46.67) < 0.01 and abs(pN_as - 21.21) < 0.01 and abs(massUrea - 53.57) < 0.01
ae_ferm = 92.0 / 180.0 * 100; assert abs(ae_ferm - 51.1) < 0.1
def haber_fig():
    it = [RECT(10, 30, 100, 34, fill="lightyellow"), T(60, 52, "N₂ + H₂ (1 : 3)", 12), RECT(150, 30, 100, 34, fill="lightblue"), T(200, 52, "compressor", 12), RECT(290, 30, 100, 34, fill="lightorange"), T(340, 46, "reactor", 12), T(340, 60, "Fe, 450 °C", 11),
          LINE(112, 47, 148, 47, width=2, arrow="end"), LINE(252, 47, 288, 47, width=2, arrow="end"),
          LINE(340, 66, 340, 100, width=2, arrow="end"), RECT(290, 102, 100, 34, fill="lightblue"), T(340, 124, "condenser", 12),
          LINE(288, 120, 252, 120, width=2, arrow="end"), RECT(150, 102, 100, 34, fill="lightgreen"), T(200, 124, "NH₃ (liquid)", 12),
          LINE(340, 138, 340, 175, width=2), LINE(340, 175, 60, 175, width=2), LINE(60, 175, 60, 66, width=2, arrow="end"), T(200, 195, "unreacted N₂ and H₂ recycled", 12), T(200, 10, "Haber process", 13, bold=True)]
    return shapes(it, 400, 215)
lesson(ch, "industry", "Industrial and environmental chemistry",
  ["Describe the Haber and Contact processes, justify the conditions and explain the compromise between rate and yield",
   "Calculate masses, yields, percentage nitrogen in fertilisers and atom economy",
   "Describe the causes and effects of acid rain, the greenhouse effect, ozone depletion and eutrophication"],
  [("definition", "The Haber process",
    "N₂(g) + 3H₂(g) ⇌ 2NH₃(g), ΔH = −92 kJ mol⁻¹. Conditions: iron catalyst, about **400–450 °C**, **150–250 atm**. A low temperature would give a better yield (exothermic) but too slow a rate; a high pressure favours fewer gas moles (4 → 2) but is expensive: so a **compromise**. The ammonia is condensed to a liquid and the unreacted gases are **recycled**. Ammonia is used to make fertilisers (ammonium nitrate, ammonium sulfate, urea), nitric acid and explosives."),
   ("retenir", "The Contact process",
    "S → SO₂ (burning sulfur or roasting sulfide ores), then **2SO₂ + O₂ ⇌ 2SO₃** (ΔH = −196 kJ mol⁻¹) with a **V₂O₅ catalyst**, about 450 °C and 1–2 atm (the yield is already high at this pressure, so a high pressure is not worth the cost). SO₃ is absorbed in concentrated H₂SO₄ to make oleum (H₂S₂O₇), which is diluted with water; SO₃ is not dissolved directly in water (a mist forms). Uses: fertilisers, detergents, batteries, paints, ore processing."),
   ("methode", "Calculations and green chemistry",
    "**Percentage yield** = actual/theoretical × 100. **Atom economy** = (M of desired product ÷ sum of M of all products, or reactants) × 100: higher is better. **Percentage N** in a fertiliser = (mass of N ÷ M) × 100: urea 46.7 %, NH₄NO₃ 35 %, (NH₄)₂SO₄ 21 %.\n\nPrinciples: use catalysts, recycle unreacted materials, reduce waste and energy, use renewable feedstocks."),
   ("pieges", "Common mistakes",
    "- Saying the best conditions are those giving the highest yield: cost, rate and safety matter too.\n- Claiming a catalyst increases the yield of a reversible reaction.\n- Forgetting to recycle unreacted gases when describing the Haber process.\n- Mixing up acid rain (SO₂, NOₓ) with the greenhouse effect (CO₂, CH₄) and ozone depletion (CFCs)."),
   ("retenir", "Environmental chemistry",
    "- **Acid rain**: SO₂ (from burning sulfur-containing fuels) and NOₓ dissolve in water to give H₂SO₄ and HNO₃; damages buildings, forests and lakes.\n- **Greenhouse effect**: CO₂, CH₄ and water vapour absorb infrared radiation; more CO₂ increases global temperature.\n- **Ozone depletion**: Cl• from CFCs catalyses the breakdown of ozone: Cl• + O₃ → ClO• + O₂.\n- **Eutrophication**: excess nitrate and phosphate (fertilisers) causes algal blooms that deplete oxygen in water.")],
  [("Example 1 — Ammonia and fertilisers", "How much ammonia can be made from 1.00 tonne of nitrogen at 100 % yield? What mass of urea, CO(NH₂)₂ (M = 60), supplies 25.0 kg of nitrogen to a field?",
    ["N₂ → 2NH₃: 28 g N₂ gives 34 g NH₃, so 1.00 t gives 34/28 = 1.21 t.", "Percentage N in urea = 28/60 × 100 = 46.7 % (two N atoms).", "Mass of urea for 25.0 kg N = 25.0/0.467 = 53.6 kg."], "1.21 t of ammonia; 53.6 kg of urea"),
   ("Example 2 — Conversion and atom economy", "In a Haber reactor 1.0 mol N₂ and 3.0 mol H₂ react until 20 % of the N₂ has reacted. Find the mole fraction of NH₃. Calculate the atom economy of making ethanol by fermentation: C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂.",
    ["N₂ left 0.80 mol, H₂ left 2.40 mol, NH₃ formed 0.40 mol; total = 3.60 mol; mole fraction of NH₃ = 0.40/3.60 = 0.111.", "Atom economy of fermentation (for ethanol) = 2 × 46/180 × 100 = 51 %.", "Hydration of ethene (C₂H₄ + H₂O → C₂H₅OH) has an atom economy of 100 %."], "x(NH₃) = 0.111; atom economy 51 %")],
  [N("Calculate the percentage of nitrogen in ammonium nitrate, NH₄NO₃ (M = 80) (%).", pN_amnit, "2 × 14/80 × 100 = 35 %.", 0.1, "%"),
   M("Why is the Contact process carried out at only 1–2 atm?", "the equilibrium yield is already high and high pressure is costly", ["the reaction is endothermic", "pressure has no effect", "the catalyst needs low pressure"], "A conversion above 95 % is obtained at 450 °C and low pressure."),
   TF("True or false: using a catalyst in the Haber process increases the equilibrium yield of ammonia.", False, "It increases the rate only; the position of equilibrium is unchanged.")],
  [N("Calculate the mass of sulfuric acid (tonnes, 3 s.f.) that could be made from 1.00 tonne of sulfur at 100 % yield (S → H₂SO₄; A_r: S 32, M(H₂SO₄) = 98).", sg(mH2SO4, 3), "98/32 = 3.06 tonnes.", 0.02, "t", difficulty=2),
   M("Which gas contributes most to acid rain?", "sulfur dioxide", ["carbon dioxide only", "oxygen", "nitrogen"], "SO₂ gives H₂SO₃/H₂SO₄ (and NOₓ gives HNO₃).")],
  P("In a Haber reactor 1.0 mol of N₂ and 3.0 mol of H₂ are heated until 20 % of the N₂ has reacted.",
    [pn("Moles of NH₃ formed.", 0.40, "0.20 mol N₂ reacts → 0.40 mol NH₃.", 0.005, "mol", 1),
     pn("Total moles of gas at the end.", 3.60, "0.80 + 2.40 + 0.40 = 3.60 mol.", 0.01, "mol", 1),
     pn("Mole fraction of NH₃ (3 s.f.).", sg(fNH3, 3), "0.40/3.60 = 0.111.", 0.002, None, 1),
     po("Explain why the temperature of 450 °C is a compromise.", "The reaction is exothermic so a lower temperature gives a higher equilibrium yield, but the rate would be too slow; at 450 °C the rate is acceptable with the catalyst and the yield is reasonable.", "1 mark: exothermic → lower T gives more NH₃; 1 mark: but rate too slow at low T.", 2)], figure=haber_fig()),
  [M("The catalyst in the Contact process is", "vanadium(V) oxide", ["iron", "nickel", "platinum gauze"], "V₂O₅."),
   M("Eutrophication is caused by", "excess nitrates and phosphates in water", ["acid rain", "CFCs", "carbon monoxide"], "Fertiliser runoff."),
   M("Which is a greenhouse gas?", "methane", ["nitrogen", "oxygen", "argon"], "CH₄ absorbs infrared radiation."),
   M("The atom economy of an addition reaction forming a single product is", "100 %", ["50 %", "0 %", "it cannot be calculated"], "All atoms of the reactants end up in the product."),
   M("Recycling unreacted N₂ and H₂ in the Haber process", "increases the overall yield and saves cost", ["decreases the rate", "removes the catalyst", "changes ΔH"], "The unreacted gases get another chance to react.")],
  (haber_fig(), "Flow diagram of the Haber process with recycling of unreacted gases.", "Boxes for the nitrogen–hydrogen feed, compressor, reactor, condenser and liquid ammonia with a recycle line back to the feed."),
  notes=[DATA, "Conditions for the Haber and Contact processes are typical textbook values (ranges). Cameroonian context (fertilisers used on cocoa, banana, maize; Lake Nyos 1986 carbon dioxide release) was NOT included in the lesson text because specific figures were not verified; a teacher may add them."], minutes=40, prereq=["gceal-chemistry-kpksp"])

# ---- practical skills and qualitative analysis
u_pip = 0.06 / 25.00 * 100; u_bur = 0.10 / 20.00 * 100; u_tot = u_pip + u_bur; c_x = 0.0800; du = c_x * u_tot / 100
assert abs(u_pip - 0.24) < 1e-9 and abs(u_bur - 0.50) < 1e-9 and abs(u_tot - 0.74) < 1e-9 and abs(du - 0.000592) < 1e-7
def tests_fig():
    rows = [("Cu²⁺", "blue ppt, dissolves in excess NH₃: deep blue", "lightblue"), ("Fe²⁺", "green ppt → brown in air", "lightgreen"), ("Fe³⁺", "red-brown ppt", "lightorange"),
            ("Al³⁺", "white ppt, dissolves in excess NaOH", "lightgrey"), ("NH₄⁺", "warm with NaOH: NH₃ turns damp red litmus blue", "lightyellow")]
    it = [T(210, 18, "tests with NaOH(aq) (and excess NH₃ for Cu²⁺)", 13, bold=True)]
    for i, (a, b, c) in enumerate(rows):
        y = 30 + i * 40
        it += [RECT(10, y, 60, 32, fill=c), T(40, y + 21, a, 13, bold=True), T(80, y + 21, b, 12, anchor="start")]
    return shapes(it, 440, 240)
lesson(ch, "practical", "Practical skills: titration, uncertainty and qualitative analysis",
  ["Describe good practice in titrations and calculate the percentage uncertainty from apparatus readings",
   "Plan an investigation and evaluate methods, including sources of error and improvements",
   "Identify ions and gases using standard qualitative tests"],
  [("methode", "Titration technique and uncertainty",
    "Rinse the pipette with the solution it will deliver and the burette with its own solution; use a white tile; swirl; add dropwise near the end-point; read at the bottom of the meniscus at eye level; repeat until **concordant titres** (within 0.10 cm³). Uncertainty: the burette has an uncertainty of ±0.05 cm³ for each reading, so a titre (two readings) has **±0.10 cm³**; a 25.00 cm³ pipette ±0.06 cm³ (typical). **Percentage uncertainty** = (absolute ÷ measured value) × 100; add the percentages of all measurements used in a quotient/product."),
   ("retenir", "Planning and evaluation",
    "State the independent, dependent and control variables; choose apparatus with suitable precision; give quantities and a method for **safety** (dilute acids, fume cupboards for chlorine/ammonia); predict the shape of the results. To evaluate: identify the largest percentage uncertainty (it limits the accuracy), distinguish systematic (zero errors, wrong indicator) from random errors, and suggest improvements (larger titres, a more precise balance, repeats)."),
   ("methode", "Qualitative tests",
    "**Gases**: H₂ burns with a squeaky pop; O₂ relights a glowing splint; CO₂ turns limewater milky; NH₃ turns damp red litmus blue; Cl₂ bleaches damp litmus; SO₂ turns acidified dichromate from orange to green. **Anions**: carbonate (dilute acid → CO₂); sulfate (acidified BaCl₂ → white precipitate); halides (acidified AgNO₃ → white/cream/yellow). **Cations**: NaOH(aq) and NH₃(aq) as in the figure; ammonium ions: warm with NaOH, NH₃ released; flame tests for Group 1 and 2 metals."),
   ("pieges", "Common mistakes",
    "- Using a measuring cylinder instead of a pipette for the accurate volume.\n- Including the first rough titre in the mean.\n- Rinsing the conical flask with the solution it will contain (the pipette should be rinsed, not the flask).\n- Reporting only 'a precipitate' without its colour, and writing 'turns the splint on' instead of 'relights a glowing splint'.")],
  [("Example 1 — Percentage uncertainty", "A 25.00 cm³ pipette (±0.06 cm³) delivers acid, and the burette titre is 20.00 cm³ (two readings, each ±0.05 cm³). The concentration is 0.0800 mol dm⁻³. Find the percentage uncertainty in the concentration and the absolute uncertainty.",
    ["Pipette: 0.06/25.00 × 100 = 0.24 %. Burette titre: 0.10/20.00 × 100 = 0.50 %.", "Total percentage uncertainty = 0.24 + 0.50 = 0.74 % (the concentration depends on both volumes).", "Absolute uncertainty = 0.74 % of 0.0800 = 0.0006 mol dm⁻³, so c = (0.0800 ± 0.0006) mol dm⁻³."], "0.74 % (± 0.0006 mol dm⁻³)"),
   ("Example 2 — Identifying an unknown", "Solid X dissolves in water. With NaOH(aq) it gives a green precipitate that turns brown on standing; with acidified BaCl₂ it gives a white precipitate. Identify X.",
    ["Green precipitate turning brown: Fe(OH)₂ oxidised in air to Fe(OH)₃, so the cation is Fe²⁺.", "White precipitate with acidified BaCl₂: sulfate ions (BaSO₄).", "X is iron(II) sulfate, FeSO₄."], "Iron(II) sulfate")],
  [N("A burette reading is 12.30 cm³ (initial 0.00, ±0.05 cm³ each reading). What is the percentage uncertainty in this titre? (%, 2 s.f.)", sg(0.10 / 12.30 * 100, 2), "0.10/12.30 × 100 = 0.81 %.", 0.02, "%"),
   M("Which test shows the presence of carbon dioxide?", "it turns limewater milky", ["it relights a glowing splint", "it burns with a pop", "it bleaches litmus"], "CO₂ + Ca(OH)₂ → CaCO₃ (white)."),
   TF("True or false: the rough (first) titre is normally included in the mean of concordant titres.", False, "It is a trial; only concordant accurate titres are averaged.")],
  [N("Titres obtained: 21.20, 20.55, 20.60, 20.50 cm³. Find the mean of the concordant titres (within 0.10 cm³; cm³, 2 d.p.).", 20.55, "Concordant titres: 20.55, 20.60 and 20.50 (the first rough value is excluded): mean = 20.55 cm³.", 0.02, "cm³", difficulty=2),
   M("A student wants to reduce the percentage uncertainty in a titration. The best improvement is to", "use a larger titre (more concentrated or smaller aliquot)", ["use a smaller burette reading", "stop at a pale colour", "use more indicator"], "The same ±0.10 cm³ is a smaller percentage of a larger volume.")],
  P("A student titrates 25.00 cm³ (pipette ±0.06 cm³) of sodium hydroxide solution with 0.1000 mol dm⁻³ hydrochloric acid. The mean titre is 21.50 cm³ (burette uncertainty ±0.10 cm³ for a titre).",
    [pn("Concentration of NaOH (mol dm⁻³, 3 s.f.).", sg(0.1000 * 21.50 / 25.00, 3), "n(HCl) = 0.1000 × 0.02150 = 2.150 × 10⁻³ mol = n(NaOH); c = 2.150 × 10⁻³/0.02500 = 0.0860 mol dm⁻³.", 0.0003, "mol/dm³", 1),
     pn("Percentage uncertainty in the burette titre (%, 2 s.f.).", sg(0.10 / 21.50 * 100, 2), "0.10/21.50 × 100 = 0.47 %.", 0.01, "%", 1),
     pn("Total percentage uncertainty in the concentration (pipette + burette; %, 2 s.f.).", sg(0.06 / 25.00 * 100 + 0.10 / 21.50 * 100, 2), "0.24 + 0.47 = 0.71 %.", 0.01, "%", 1),
     po("Suggest one change to reduce the uncertainty and explain why it helps.", "Use a more dilute acid so that the titre is larger (for example 40 cm³ with a 50 cm³ burette): the fixed ±0.10 cm³ is then a smaller percentage of the titre.", "1 mark: increase the titre volume (or use a more precise apparatus); 1 mark: smaller percentage uncertainty.", 2)], figure=tests_fig()),
  [M("A deep blue solution forms when excess ammonia is added to Cu²⁺(aq). The blue ion is", "[Cu(NH₃)₄(H₂O)₂]²⁺", ["Cu(OH)₂", "CuCO₃", "Cu₂O"], "Ligand exchange."),
   M("The gas that turns damp red litmus paper blue is", "ammonia", ["chlorine", "carbon dioxide", "hydrogen"], "NH₃ is alkaline."),
   M("A concordant titre is one that is", "within 0.10 cm³ of another titre", ["the largest", "the first", "exactly 25.00 cm³"], "Used to decide when enough titrations are done."),
   M("Which precipitate dissolves in excess aqueous sodium hydroxide?", "Al(OH)₃", ["Fe(OH)₃", "Cu(OH)₂", "Mg(OH)₂"], "Aluminium hydroxide is amphoteric."),
   M("The reading of a burette is taken at", "the bottom of the meniscus, eye level", ["the top of the meniscus", "any point", "the tap"], "To avoid parallax error.")],
  (tests_fig(), "Test results for some cations with sodium hydroxide solution.", "Five labelled rows listing ions Cu2+, Fe2+, Fe3+, Al3+ and NH4+ with the colour of the precipitate or the observation."),
  notes=[DATA, "Practical assessment format of the Cameroon GCE Board (practical examination, continuous assessment) is not described; apparatus uncertainty values are typical.", "Complete qualitative analysis schemes are not given."], minutes=40, prereq=["gceal-chemistry-transition"])

# =============================================================== MOCK
Lq = {l.slug: l for c in p.chapters for l in c.lessons}
s_agcl = math.sqrt(1.8e-10); pH_ba = 14 + math.log10(2 * 0.0010)
assert abs(s_agcl - 1.34e-5) < 1e-7 and abs(pH_ba - 11.30) < 0.005
Ecell = 0.77 - (-0.76); dG2 = -2 * F_ * Ecell / 1000; assert abs(Ecell - 1.53) < 1e-9 and abs(dG2 + 295.3) < 0.1
A1 = mockq(Lq["kpksp"], M("K_sp for AgCl is 1.8 × 10⁻¹⁰ mol² dm⁻⁶. The concentration of Ag⁺ in a saturated solution in pure water is", "1.3 × 10⁻⁵ mol dm⁻³", ["1.8 × 10⁻¹⁰ mol dm⁻³", "9.0 × 10⁻¹¹ mol dm⁻³", "3.6 × 10⁻¹⁰ mol dm⁻³"], "[Ag⁺] = [Cl⁻] = √K_sp = 1.3 × 10⁻⁵ mol dm⁻³.", points=2))
A2 = mockq(Lq["acidbase"], M("The pH of 0.0010 mol dm⁻³ Ba(OH)₂ (a strong base; K_w = 1.0 × 10⁻¹⁴) is", "11.3", ["11.0", "3.0", "2.7"], "[OH⁻] = 2 × 0.0010 = 2.0 × 10⁻³; pOH = 2.70; pH = 11.30.", points=2))
A3 = mockq(Lq["electrode"], M("Given E°(Zn²⁺/Zn) = −0.76 V, E°(Cu²⁺/Cu) = +0.34 V and E°(Ag⁺/Ag) = +0.80 V, the strongest oxidising agent is", "Ag⁺", ["Zn²⁺", "Cu²⁺", "Zn"], "The most positive E° belongs to the species that is reduced most readily.", points=2))
A4 = mockq(Lq["carbonyls"], M("Which reagent distinguishes propanal from propanone?", "Tollens' reagent", ["2,4-DNPH", "acidified KMnO₄ only", "aqueous NaOH"], "Only the aldehyde gives a silver mirror; both give an orange precipitate with 2,4-DNPH.", points=2))
B1 = mockq(Lq["electrode"], P("Question 5 (4 marks). A cell is made from a Zn²⁺/Zn half-cell (E° = −0.76 V) and a Fe³⁺/Fe²⁺ half-cell with a platinum electrode (E° = +0.77 V). F = 96 500 C mol⁻¹.",
    [pn("E°cell (V).", Ecell, "E° = +0.77 − (−0.76) = +1.53 V.", 0.005, "V", 1),
     pn("ΔG° (kJ mol⁻¹, 3 s.f.) for 2Fe³⁺ + Zn → 2Fe²⁺ + Zn²⁺ (n = 2).", sg(dG2, 3), "ΔG° = −nFE° = −2 × 96 500 × 1.53 = −2.95 × 10⁵ J = −295 kJ mol⁻¹.", 1, "kJ/mol", 2),
     pm("The reaction is", "feasible, because E°cell is positive", ["not feasible, because ΔG° is positive", "always fast", "at equilibrium"], "A positive E°cell means ΔG° < 0.", 1)]))
B2 = mockq(Lq["analysis"], P("Question 6 (4 marks). Compound P has 48.6 % C, 8.1 % H and 43.2 % O, M = 74. It shows a strong IR band at 1740 cm⁻¹ with no broad O–H band; its ¹H NMR has signals at δ 3.7 (3H, singlet), δ 2.3 (2H, quartet) and δ 1.1 (3H, triplet).",
    [pn("Number of carbon atoms in a molecule of P.", 3, "Ratio C : H : O = 1.5 : 3 : 1 = 3 : 6 : 2 (C₃H₆O₂, M = 74).", 0, None, 1),
     pm("The functional group in P is", "an ester", ["an alcohol", "a carboxylic acid", "a ketone"], "C=O at 1740 cm⁻¹; no O–H.", 1),
     pm("P is", "methyl propanoate, CH₃CH₂COOCH₃", ["ethyl ethanoate", "propanoic acid", "propyl methanoate"], "δ 3.7 (s, 3H) = OCH₃; δ 2.3 (q) and δ 1.1 (t) = CH₃CH₂–CO.", 2)]))
B3 = mockq(Lq["industry"], P("Question 7 (4 marks). In a Haber process reactor 1.0 mol of N₂ and 3.0 mol of H₂ react until 0.40 mol of NH₃ has formed. N₂ + 3H₂ ⇌ 2NH₃, ΔH = −92 kJ mol⁻¹.",
    [pn("Mole fraction of NH₃ in the equilibrium mixture (3 s.f.).", sg(fNH3, 3), "Moles: N₂ 0.80, H₂ 2.40, NH₃ 0.40; total 3.60; x = 0.40/3.60 = 0.111.", 0.002, None, 2),
     pm("Increasing the pressure at constant temperature will", "increase the yield of NH₃", ["decrease the yield of NH₃", "have no effect on the yield", "decrease the rate"], "There are fewer gas moles on the right (2 against 4).", 1),
     pm("The iron catalyst", "increases the rate but does not change the equilibrium yield", ["increases the yield", "decreases ΔH", "is used up"], "A catalyst speeds both reactions equally.", 1)]))
p.mock("1", "GCE A Level mock — Chemistry (paper 1)", 90,
       "Answer all questions. Section A has four short multiple-choice questions (2 marks each); Section B has three structured questions (4 marks each). The paper is marked out of 20. Data: F = 96 500 C mol⁻¹, K_w = 1.0 × 10⁻¹⁴, A_r: H 1, C 12, O 16. This is an original practice paper; its format is not that of the Cameroon GCE Board and should be checked against the official texts.",
       [("Section A — Short questions (8 marks)", [A1, A2, A3, A4]), ("Section B — Structured questions (12 marks)", [B1, B2, B3])])
p.write()
