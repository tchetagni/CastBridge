"""Generator: gceal-physics (Physics — GCE A Level, Upper Sixth, Cameroon GCE Board). python3 gceal-physics.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pc import *

# constants used in this pack (stated in the lessons): e = 1.60e-19 C, h = 6.63e-34 J s, c = 3.00e8 m/s, m_e = 9.11e-31 kg, eps0 = 8.85e-12 F/m,
# mu0 = 4 pi e-7 H/m, u = 1.66e-27 kg = 931.5 MeV, g = 9.8 m/s2
e_, h_, c_, me_, eps0, mu0 = 1.60e-19, 6.63e-34, 3.00e8, 9.11e-31, 8.85e-12, 4 * math.pi * 1e-7
k_c = 1 / (4 * math.pi * eps0); assert abs(k_c - 8.99e9) < 0.01e9
g = 9.8
rad = math.radians
p = Pack("gceal-physics", "Physics — GCE A Level", level="Upper Sixth", subject="physics", cursus="secondary", exam="GCE-AL", series=[],
         description="Upper Sixth physics revision for the GCE Advanced Level: electric and magnetic fields, capacitors, induction, a.c., optics and waves, quantum "
                     "physics, nuclear physics, thermodynamics, electronics and practical skills, with worked examples, graded exercises and a mock paper. Draft.",
         programRef="Cameroon GCE Board — Advanced Level Physics syllabus (Upper Sixth part) — to be checked against the official texts",
         source_note="Original exercise, GCE Advanced Level style (g = 9.8 m/s²)")
SYL = "GCE Advanced Level Physics — %s (to be checked against the official syllabus)"
DATA = ("Constants used: e = 1.60 × 10⁻¹⁹ C; h = 6.63 × 10⁻³⁴ J s; c = 3.00 × 10⁸ m/s; mₑ = 9.11 × 10⁻³¹ kg; ε₀ = 8.85 × 10⁻¹² F/m; μ₀ = 4π × 10⁻⁷ H/m.")

# =============================================================== 1. ELECTRIC FIELDS AND CAPACITORS
ch = p.chapter("ch-electric", "Electric fields and capacitors", SYL % "electric fields and capacitors")

# ---- 1.1 electric fields
Vacc = 2000.0; v_e = math.sqrt(2 * e_ * Vacc / me_); assert abs(v_e - 2.65e7) < 0.02e7
Ep = 800 / 0.04; Fe = e_ * Ep; ae = Fe / me_; assert Ep == 2.0e4 and abs(Fe - 3.2e-15) < 1e-18 and abs(ae - 3.51e15) < 0.01e15
Epc = k_c * 2.0e-9 / 0.10 ** 2; Vpc = k_c * 2.0e-9 / 0.10; assert abs(Epc - 1798) < 2 and abs(Vpc - 179.8) < 0.2
Fq = k_c * 3e-6 * 5e-6 / 0.30 ** 2; assert abs(Fq - 1.498) < 0.01
Vd_ = 300.0; Ed_ = Vd_ / 0.020; ad_ = e_ * Ed_ / me_; td_ = 0.050 / 2.0e7; yd_ = 0.5 * ad_ * td_ ** 2
assert abs(Ed_ - 1.5e4) < 1e-6 and abs(ad_ - 2.634e15) < 0.01e15 and abs(td_ - 2.5e-9) < 1e-15 and abs(yd_ - 8.23e-3) < 0.02e-3, (ad_, yd_)
def plates_fig():
    it = [RECT(60, 40, 240, 8, fill="lightorange"), RECT(60, 170, 240, 8, fill="lightblue"), T(40, 48, "+", 18, bold=True), T(40, 182, "−", 20, bold=True),
          T(330, 110, "E = V / d", 14, bold=True)]
    for x in (100, 160, 220, 270):
        it.append(LINE(x, 52, x, 164, color="grey", width=1, arrow="end"))
    it += [PATH("M 70 110 Q 180 110 285 70", stroke="red", width=3), CIRCLE(70, 110, 6, fill="red", stroke="red"), T(78, 134, "electron", 12, anchor="start", color="red"),
           LINE(330, 48, 330, 170, color="purple", width=1, arrow="both"), T(342, 150, "d", 13, anchor="start", color="purple")]
    return shapes(it, 400, 230)
lesson(ch, "efield", "Electric fields and potential",
  ["Use Coulomb's law and define electric field strength E = F/q and electric potential V",
   "Use E = V/d for a uniform field and the potential V = Q/(4πε₀r) of a point charge",
   "Analyse the motion of a charged particle accelerated or deflected in an electric field"],
  [("definition", "Field and potential",
    "**Coulomb's law**: F = Q₁Q₂/(4πε₀r²) for two point charges (1/4πε₀ = 8.99 × 10⁹ N m² C⁻² using ε₀ = 8.85 × 10⁻¹² F m⁻¹); like charges repel, unlike attract.\n\n"
    "**Electric field strength** E = F/q (N C⁻¹ = V m⁻¹), the force per unit positive charge; for a point charge **E = Q/(4πε₀r²)**. **Potential** V = work per unit positive charge brought from infinity: **V = Q/(4πε₀r)** (J C⁻¹ = V)."),
   ("formule", "Uniform field and moving charges",
    "Between parallel plates separated by d with pd V: **E = V/d** (uniform). A charge q accelerated through a pd V gains kinetic energy **½mv² = qV**.\n\n"
    "In the field the force is F = qE and a = qE/m: a charge entering at right angles to the field follows a **parabola** (like a projectile: constant velocity along the plates, constant acceleration across them).",
    r"E=\frac{Q}{4\pi\varepsilon_0 r^{2}}\qquad V=\frac{Q}{4\pi\varepsilon_0 r}\qquad E=\frac{V}{d}"),
   ("retenir", "Field lines and equipotentials",
    "Field lines show the direction of the force on a positive charge: from + to −, closer together where the field is stronger, and they cross equipotentials at right angles. Field strength is minus the potential gradient: **E = −dV/dx**. No work is done moving a charge along an equipotential."),
   ("pieges", "Common mistakes",
    "- Treating E as a scalar: it is a vector (add fields from several charges as vectors); V is a scalar (add with signs).\n- Using r as the distance in cm: use metres.\n- Forgetting that the force on an electron is opposite to the field direction.\n- Gravity is usually negligible for electrons and ions: do not include it.")],
  [("Example 1 — Electron gun", "An electron is accelerated from rest through a pd of 2.0 kV. Find its final speed (e = 1.60 × 10⁻¹⁹ C, mₑ = 9.11 × 10⁻³¹ kg).",
    ["Energy gained: ½mv² = eV = 1.60 × 10⁻¹⁹ × 2000 = 3.2 × 10⁻¹⁶ J.", "v = √(2eV/m) = √(2 × 3.2 × 10⁻¹⁶ ÷ 9.11 × 10⁻³¹) = 2.65 × 10⁷ m/s (about 9 % of c: relativistic effects are small)."], "v = 2.65 × 10⁷ m/s"),
   ("Example 2 — Point charge and plates", "(a) Find E and V at 0.10 m from a point charge of 2.0 nC. (b) Find E between plates 4.0 cm apart with 800 V across them, and the force on an electron there.",
    ["(a) E = 8.99 × 10⁹ × 2.0 × 10⁻⁹ ÷ 0.10² = 1.8 × 10³ V/m; V = 8.99 × 10⁹ × 2.0 × 10⁻⁹ ÷ 0.10 = 1.8 × 10² V.", "(b) E = V/d = 800/0.040 = 2.0 × 10⁴ V/m.", "F = eE = 1.60 × 10⁻¹⁹ × 2.0 × 10⁴ = 3.2 × 10⁻¹⁵ N (weight of the electron is only 9 × 10⁻³⁰ N)."], "(a) 1.8 × 10³ V/m and 180 V; (b) 2.0 × 10⁴ V/m, 3.2 × 10⁻¹⁵ N")],
  [N("Find the force between charges 3.0 μC and 5.0 μC separated by 0.30 m in air (N, 2 s.f.).", sg(Fq, 2), "F = 8.99 × 10⁹ × 3.0 × 10⁻⁶ × 5.0 × 10⁻⁶ ÷ 0.30² = 1.5 N.", 0.03, "N"),
   N("Two plates 2.0 cm apart have a pd of 500 V. Find the field strength (V/m).", 500 / 0.020, "E = V/d = 500/0.020 = 2.5 × 10⁴ V/m.", 50, "V/m"),
   TF("True or false: the electric potential is a vector quantity.", False, "Potential is a scalar (energy per unit charge); field strength is the vector.")],
  [N("A proton (m = 1.67 × 10⁻²⁷ kg) is accelerated from rest through 1000 V. Find its speed (10⁵ m/s, 3 s.f.).", sg(math.sqrt(2 * e_ * 1000 / 1.67e-27) / 1e5, 3), "v = √(2eV/m) = √(2 × 1.60 × 10⁻¹⁹ × 1000 ÷ 1.67 × 10⁻²⁷) = 4.38 × 10⁵ m/s.", 0.03, "× 10⁵ m/s", difficulty=2),
   M("The distance from a point charge is doubled. The field strength becomes", "one quarter", ["half", "double", "one eighth"], "E ∝ 1/r², so (1/2)² = 1/4.")],
  P("An electron enters midway between two horizontal plates, 5.0 cm long and 2.0 cm apart, at 2.0 × 10⁷ m/s parallel to the plates. The pd between the plates is 300 V. (e = 1.60 × 10⁻¹⁹ C, mₑ = 9.11 × 10⁻³¹ kg; ignore gravity.)",
    [pn("Field strength between the plates (10⁴ V/m).", Ed_ / 1e4, "E = V/d = 300/0.020 = 1.5 × 10⁴ V/m.", 0.01, "× 10⁴ V/m", 1),
     pn("Acceleration of the electron (10¹⁵ m/s², 3 s.f.).", sg(ad_ / 1e15, 3), "a = eE/m = 1.60 × 10⁻¹⁹ × 1.5 × 10⁴ ÷ 9.11 × 10⁻³¹ = 2.63 × 10¹⁵ m/s².", 0.02, "× 10¹⁵ m/s²", 1),
     pn("Time between the plates (ns).", td_ * 1e9, "t = L/v = 0.050/2.0 × 10⁷ = 2.5 × 10⁻⁹ s = 2.5 ns.", 0.01, "ns", 1),
     pn("Deflection on leaving the plates (mm, 2 s.f.).", sg(yd_ * 1000, 2), "y = ½at² = 0.5 × 2.63 × 10¹⁵ × (2.5 × 10⁻⁹)² = 8.2 × 10⁻³ m = 8.2 mm (less than 10 mm, so it does not hit a plate).", 0.2, "mm", 2)], figure=plates_fig()),
  [M("The SI unit of electric field strength can be written as", "V m⁻¹", ["V m", "J C", "N C"], "E = V/d, equal to N C⁻¹."),
   M("An electron moves in a uniform field directed upward. The force on it is", "downward", ["upward", "zero", "sideways"], "The electron is negative: force opposite to E."),
   M("Work done moving a charge along an equipotential surface is", "zero", ["maximum", "equal to qE", "negative"], "There is no potential difference along an equipotential."),
   M("An α-particle (charge +2e) is accelerated through 1.0 kV. Its kinetic energy is", "2.0 keV", ["1.0 keV", "4.0 keV", "0.5 keV"], "KE = qV = 2e × 1000 V = 2000 eV."),
   M("Field lines are closest together where", "the field is strongest", ["the potential is zero", "the charge is smallest", "the field is zero"], "Density of lines represents the field strength.")],
  (plates_fig(), "Uniform field between parallel plates; an electron entering horizontally follows a parabola towards the positive plate.", "Two horizontal charged plates with vertical field lines and a red curved path of an electron bending toward the positive plate."),
  notes=[DATA, "Whether the syllabus requires gravitational–electric analogies and E = −dV/dx is unclear."], minutes=35)

# ---- 1.2 capacitors
Q1c = 100e-6 * 12; W1c = 0.5 * 100e-6 * 12 ** 2; assert abs(Q1c - 1.2e-3) < 1e-12 and abs(W1c - 7.2e-3) < 1e-12
tau = 470e-6 * 10e3; V10 = 9.0 * math.exp(-10 / tau); t3 = tau * math.log(3)
assert abs(tau - 4.7) < 1e-9 and abs(V10 - 1.072) < 0.001 and abs(t3 - 5.163) < 0.001, (V10, t3)
Cs = 1 / (1 / 2 + 1 / 3); assert abs(Cs - 1.2) < 1e-12
Cpp = eps0 * 1.0e-2 / 1.0e-3; assert abs(Cpp - 8.85e-11) < 1e-14
Wf = 0.5 * 1000e-6 * 300 ** 2; Qf = 1000e-6 * 300; Pf = Wf / 2e-3; assert abs(Wf - 45) < 1e-9 and abs(Qf - 0.30) < 1e-12 and abs(Pf - 22500) < 1e-6
def rc_fig():
    return plot(0, 15, 0, 10, curves=[("9*exp(-x/4.7)", "blue", "V")], points=[(4.7, 9 / math.e, "τ = RC", "red")], segments=[(4.7, 0, 4.7, 9 / math.e, "red", True, None)],
                grid=5, xlabel="t / s", ylabel="V / V", w=380, h=250)
lesson(ch, "capacitors", "Capacitors and the RC circuit",
  ["Define capacitance and use C = Q/V, the parallel-plate formula and the energy stored W = ½CV²",
   "Combine capacitors in series and parallel",
   "Analyse the charging and discharging of a capacitor through a resistor using the time constant RC"],
  [("definition", "Capacitance and energy",
    "A **capacitor** stores charge. **Capacitance C = Q/V** (farad, F = C V⁻¹). For two parallel plates of area A separated by d in air: **C = ε₀A/d** (ε₀ = 8.85 × 10⁻¹² F m⁻¹); a dielectric of relative permittivity ε_r multiplies C by ε_r.\n\n"
    "The energy stored is the area under the V–Q graph: **W = ½QV = ½CV² = Q²/2C**."),
   ("formule", "Combinations and the RC circuit",
    "Parallel: **C = C₁ + C₂ + …** (same V). Series: **1/C = 1/C₁ + 1/C₂ + …** (same Q).\n\n"
    "Discharge through a resistor R: **Q = Q₀e^(−t/RC)**, and V and I decay in the same way. The **time constant τ = RC** (seconds) is the time for Q to fall to 1/e ≈ 37 % of Q₀. Time to fall to half: t½ = 0.693 RC. Charging: Q = Q₀(1 − e^(−t/RC)).",
    r"C=\frac{Q}{V}\quad W=\frac{1}{2}CV^{2}\quad Q=Q_0e^{-t/RC}"),
   ("methode", "Method for RC problems",
    "1. Compute τ = RC with R in ohms and C in farads (μF = 10⁻⁶ F).\n2. Use V = V₀e^(−t/τ) for discharge; to find a time, take natural logs: t = τ ln(V₀/V).\n3. A graph of ln V against t is a straight line with gradient −1/RC.\n4. The initial current is I₀ = V₀/R."),
   ("pieges", "Common mistakes",
    "- Forgetting the units: μF must be converted to farads before multiplying by ohms.\n- Using the series formula for parallel capacitors (and vice versa).\n- Saying current flows through the dielectric: charge flows round the circuit and the plates charge up.\n- Using a calculator in degree mode with exponentials (not needed) or confusing ln and log.")],
  [("Example 1 — Charge and energy", "A 100 μF capacitor is charged to 12 V. Find the charge and the energy stored.",
    ["Q = CV = 100 × 10⁻⁶ × 12 = 1.2 × 10⁻³ C.", "W = ½CV² = ½ × 100 × 10⁻⁶ × 144 = 7.2 × 10⁻³ J (7.2 mJ)."], "Q = 1.2 mC; W = 7.2 mJ"),
   ("Example 2 — Discharge", "A 470 μF capacitor charged to 9.0 V discharges through a 10 kΩ resistor. Find the time constant, the pd after 10 s, and the time for the pd to fall to 3.0 V.",
    ["τ = RC = 10 × 10³ × 470 × 10⁻⁶ = 4.7 s.", "V = 9.0e^(−10/4.7) = 9.0 × 0.119 = 1.07 V.", "3.0 = 9.0e^(−t/4.7) gives t = 4.7 ln 3 = 4.7 × 1.099 = 5.2 s."], "τ = 4.7 s; V(10 s) = 1.07 V; t = 5.2 s")],
  [N("Find the charge on a 220 μF capacitor charged to 6.0 V (mC).", 220e-6 * 6.0 * 1e3, "Q = CV = 220 × 10⁻⁶ × 6.0 = 1.32 × 10⁻³ C = 1.32 mC.", 0.005, "mC"),
   N("Find the combined capacitance of 2.0 μF and 3.0 μF in series (μF).", Cs, "1/C = 1/2.0 + 1/3.0 = 5/6, so C = 1.2 μF.", 0.005, "μF"),
   TF("True or false: capacitors in parallel have a total capacitance equal to the sum of the capacitances.", True, "The plates areas effectively add: C = C₁ + C₂.")],
  [N("A parallel-plate capacitor in air has plates of area 1.0 × 10⁻² m² separated by 1.0 mm. Find C (pF, 3 s.f.; ε₀ = 8.85 × 10⁻¹² F/m).", sg(Cpp * 1e12, 3), "C = ε₀A/d = 8.85 × 10⁻¹² × 1.0 × 10⁻² ÷ 1.0 × 10⁻³ = 8.85 × 10⁻¹¹ F = 88.5 pF.", 0.2, "pF", difficulty=2),
   N("A capacitor discharges through a resistor with time constant 2.0 s. What fraction of the initial charge remains after 4.0 s (3 s.f.)?", sg(math.exp(-2), 3), "Q/Q₀ = e^(−t/τ) = e^(−2) = 0.135.", 0.002, None)],
  P("The flash of a camera uses a 1000 μF capacitor charged to 300 V. It discharges in about 2.0 ms.",
    [pn("Charge stored (C).", Qf, "Q = CV = 1000 × 10⁻⁶ × 300 = 0.30 C.", 0.001, "C", 1),
     pn("Energy stored (J).", Wf, "W = ½CV² = ½ × 1000 × 10⁻⁶ × 300² = 45 J.", 0.1, "J", 1),
     pn("Average power during the flash (kW).", Pf / 1000, "P = W/t = 45/0.0020 = 22 500 W = 22.5 kW.", 0.05, "kW", 2),
     pm("The capacitor is a good power source for a flash because it", "can release its energy in a very short time", ["stores more energy than a battery", "has no resistance", "creates energy"], "A battery stores far more energy but releases it slowly; a capacitor can discharge quickly.", 1)]),
  [M("The unit farad is equal to", "C V⁻¹", ["V C⁻¹", "J C", "A s V"], "C = Q/V."),
   M("The time constant of a circuit with R = 20 kΩ and C = 50 μF is", "1.0 s", ["10 s", "0.1 s", "1000 s"], "RC = 20 000 × 50 × 10⁻⁶ = 1.0 s."),
   M("If the charge on a capacitor is doubled, the stored energy (same C) is", "quadrupled", ["doubled", "halved", "unchanged"], "W = Q²/2C."),
   M("A graph of ln V against t for a discharging capacitor has gradient", "−1/RC", ["−RC", "+RC", "−V₀"], "ln V = ln V₀ − t/RC."),
   M("Two capacitors C and C in series have total capacitance", "C/2", ["2C", "C", "C²"], "1/C_total = 2/C.")],
  (rc_fig(), "Discharge of a capacitor: V = V₀e^(−t/RC) with τ = RC = 4.7 s; at t = τ the pd has fallen to 1/e of its initial value.", "An exponentially decaying curve starting at 9 volts and falling to about 3.3 volts at 4.7 seconds."),
  notes=[DATA, "The charging formula and half-time t½ = 0.693RC are stated without proof; whether the board requires the derivation by integration is unknown."], minutes=35, prereq=["gceal-physics-efield"])

# =============================================================== 2. MAGNETIC FIELDS AND INDUCTION
ch = p.chapter("ch-magnetic", "Magnetic fields, induction and a.c.", SYL % "magnetic fields and electromagnetic induction")
F_w = 0.40 * 8.0 * 0.25; assert abs(F_w - 0.80) < 1e-12
r_e = me_ * 2.0e7 / (e_ * 1.0e-3); T_e = 2 * math.pi * me_ / (e_ * 1.0e-3); assert abs(r_e - 0.1139) < 0.001 and abs(T_e - 3.577e-8) < 0.01e-8
B_w = mu0 * 10 / (2 * math.pi * 0.05); B_s = mu0 * 1000 * 2.0; assert abs(B_w - 4.0e-5) < 1e-9 and abs(B_s - 2.513e-3) < 1e-5
uu = 1.66e-27; Vms = 1000.0; Bms = 0.20
def r_ion(A): m = A * uu; return math.sqrt(2 * m * Vms / e_) / Bms
r20, r22 = r_ion(20), r_ion(22); sep = 2 * (r22 - r20); assert abs(r20 - 0.1019) < 0.0005 and abs(r22 - 0.1069) < 0.0005 and abs(sep - 0.0100) < 0.0005, (r20, r22, sep)
def bfield_fig():
    it = []
    for x in (60, 120, 180, 240, 300):
        for y in (40, 100, 160, 220):
            it.append(T(x, y, "×", 14, color="grey"))
    it += [CIRCLE(180, 130, 55, stroke="red", width=3), CIRCLE(235, 130, 7, fill="orange"), LINE(235, 130, 235, 70, color="blue", width=3, arrow="end"), T(250, 82, "v", 15, anchor="start", color="blue"),
           LINE(228, 130, 195, 130, color="green", width=3, arrow="end"), T(206, 150, "F", 15, color="green", bold=True), T(345, 130, "B into page", 12, anchor="end")]
    return shapes(it, 400, 250)
lesson(ch, "bfield", "Magnetic fields and forces on moving charges",
  ["Use F = BIl sin θ and F = Bqv sin θ with Fleming's left-hand rule",
   "Analyse a charged particle moving in a uniform magnetic field (r = mv/qB) and describe the velocity selector and mass spectrometer",
   "Calculate the magnetic field of a long wire and a solenoid and the force between parallel currents"],
  [("definition", "Magnetic flux density and forces",
    "The **magnetic flux density** B (tesla, T = N A⁻¹ m⁻¹) is defined by the force on a current-carrying conductor perpendicular to the field: **F = BIl** (in general F = BIl sin θ, θ = angle between the wire and B).\n\n"
    "On a moving charge: **F = Bqv sin θ**, always perpendicular to both v and B (Fleming's left-hand rule for a positive charge: first finger field, second finger current/velocity, thumb force). The force does **no work**, so the speed is unchanged."),
   ("formule", "Motion in a uniform field",
    "A charge entering a uniform field at right angles moves on a **circle**: Bqv = mv²/r gives **r = mv/(qB)**, with period **T = 2πm/(qB)** independent of the speed.\n\n"
    "**Velocity selector**: crossed fields E and B; particles with v = E/B pass undeflected. **Mass spectrometer**: ions accelerated through V (qV = ½mv²) move on a circle of radius r = (1/B)√(2mV/q), so r depends on the mass.",
    r"F=BIl\sin\theta\qquad r=\frac{mv}{qB}\qquad T=\frac{2\pi m}{qB}"),
   ("formule", "Fields of currents",
    "Long straight wire: **B = μ₀I/(2πr)** (direction by the right-hand grip rule). Long solenoid with n turns per metre: **B = μ₀nI** inside, almost zero outside. μ₀ = 4π × 10⁻⁷ T m A⁻¹.\n\n"
    "Two long parallel wires carrying I₁ and I₂ at distance d attract if the currents are in the same direction: **F/L = μ₀I₁I₂/(2πd)**. This defines the ampere (2 × 10⁻⁷ N m⁻¹ for 1 A in wires 1 m apart).",
    r"B=\frac{\mu_0 I}{2\pi r}\qquad B=\mu_0 nI"),
   ("pieges", "Common mistakes",
    "- Using the wrong hand: the left-hand rule is for the force on a conductor (motor effect); the right-hand rule is used for the field of a current.\n- Forgetting that the force on a negative charge is opposite to the left-hand rule direction.\n- Using θ as the angle to the perpendicular: it is between the field and the wire or velocity.\n- Believing the magnetic force changes the speed: it only changes the direction.")],
  [("Example 1 — Wire in a field", "A straight wire 0.25 m long carries 8.0 A at right angles to a field of 0.40 T. Find the force. Then find B at 5.0 cm from a long straight wire carrying 10 A.",
    ["F = BIl = 0.40 × 8.0 × 0.25 = 0.80 N.", "B = μ₀I/(2πr) = 4π × 10⁻⁷ × 10 ÷ (2π × 0.050) = 4.0 × 10⁻⁵ T (comparable with the Earth's field)."], "F = 0.80 N; B = 4.0 × 10⁻⁵ T"),
   ("Example 2 — Electron in a field", "An electron moves at 2.0 × 10⁷ m/s at right angles to a field of 1.0 mT. Find the radius and period of its circular path.",
    ["r = mv/(eB) = 9.11 × 10⁻³¹ × 2.0 × 10⁷ ÷ (1.60 × 10⁻¹⁹ × 1.0 × 10⁻³) = 0.114 m.", "T = 2πm/(eB) = 2π × 9.11 × 10⁻³¹ ÷ (1.60 × 10⁻¹⁹ × 1.0 × 10⁻³) = 3.6 × 10⁻⁸ s."], "r = 0.11 m; T = 3.6 × 10⁻⁸ s")],
  [N("A proton moves at 3.0 × 10⁶ m/s perpendicular to a field of 0.50 T. Find the force on it (10⁻¹³ N, 3 s.f.).", sg(e_ * 3.0e6 * 0.5 / 1e-13, 3), "F = Bqv = 0.50 × 1.60 × 10⁻¹⁹ × 3.0 × 10⁶ = 2.40 × 10⁻¹³ N.", 0.02, "× 10⁻¹³ N"),
   N("A solenoid has 800 turns per metre and carries 1.5 A. Find B inside (mT, 3 s.f.).", sg(mu0 * 800 * 1.5 * 1000, 3), "B = μ₀nI = 4π × 10⁻⁷ × 800 × 1.5 = 1.51 × 10⁻³ T = 1.51 mT.", 0.01, "mT"),
   TF("True or false: a magnetic field can change the speed of a charged particle.", False, "The force is perpendicular to the velocity, so it does no work: it changes only the direction.")],
  [N("Find the force per metre between two long parallel wires 0.10 m apart carrying 20 A each in the same direction (10⁻³ N/m).", mu0 * 20 * 20 / (2 * math.pi * 0.10) / 1e-3, "F/L = μ₀I₁I₂/(2πd) = 2 × 10⁻⁷ × 400/0.10 = 8.0 × 10⁻⁴ N/m, i.e. 0.80 in units of 10⁻³ N/m (attractive).", 0.01, "× 10⁻³ N/m", difficulty=2),
   N("Find the speed of ions that pass undeflected through crossed fields E = 5.0 × 10⁴ V/m and B = 0.20 T (m/s).", 5.0e4 / 0.20, "v = E/B = 2.5 × 10⁵ m/s.", 100, "m/s")],
  P("In a mass spectrometer, singly charged neon ions (q = e) of mass 20u and 22u (u = 1.66 × 10⁻²⁷ kg) are accelerated through 1000 V and enter a uniform field of 0.20 T perpendicular to their path.",
    [pn("Radius of the path of the 20u ions (cm, 3 s.f.).", sg(r20 * 100, 3), "r = (1/B)√(2mV/q) = 5 × √(2 × 3.32 × 10⁻²⁶ × 1000 ÷ 1.60 × 10⁻¹⁹) = 0.102 m = 10.2 cm.", 0.1, "cm", 2),
     pn("Radius of the path of the 22u ions (cm, 3 s.f.).", sg(r22 * 100, 3), "r ∝ √m: r = 10.19 × √(22/20) = 10.7 cm.", 0.1, "cm", 1),
     pn("Distance between the points where the two kinds of ion strike the detector after half a circle (mm, 2 s.f.).", sg(sep * 1000, 2), "Separation = 2(r₂₂ − r₂₀) = 2 × 0.50 cm = about 10 mm.", 0.6, "mm", 1)], figure=bfield_fig()),
  [M("The SI unit tesla is equivalent to", "N A⁻¹ m⁻¹", ["N A m", "N m A", "J A⁻¹"], "B = F/(Il)."),
   M("A charged particle moves in a circle in a uniform magnetic field. If its speed doubles, the radius", "doubles", ["halves", "quadruples", "stays the same"], "r = mv/qB ∝ v."),
   M("Two parallel wires carry currents in opposite directions. They", "repel each other", ["attract each other", "exert no force", "rotate"], "Opposite currents repel; same-direction currents attract."),
   M("The magnetic force on a charged particle moving parallel to the field is", "zero", ["maximum", "qvB", "mv²/r"], "sin 0° = 0."),
   M("Inside a long solenoid the field is", "uniform and proportional to the current", ["zero", "greatest at the ends", "independent of the current"], "B = μ₀nI.")],
  (bfield_fig(), "A charged particle moving at right angles to a field into the page: the force is perpendicular to the velocity, so the path is a circle.", "A grid of crosses showing a magnetic field into the page, a circular path with a velocity arrow along the tangent and a force arrow to the centre."),
  notes=[DATA, "Hall effect and the cyclotron are not covered; check the syllabus.", "Fleming's rules are described in words; the direction conventions should be checked against the board's preferred rule (left-hand/right-hand)."], minutes=40, prereq=["gceal-physics-efield"])

# ---- 2.2 electromagnetic induction
emf_r = 0.30 * 0.50 * 4.0; I_r = emf_r / 2.0; F_r = 0.30 * I_r * 0.50; P_r = F_r * 4.0
assert abs(emf_r - 0.60) < 1e-12 and abs(I_r - 0.30) < 1e-12 and abs(F_r - 0.045) < 1e-12 and abs(P_r - emf_r * I_r) < 1e-12
emf_c = 200 * (0.50 * 0.010) / 0.020; assert abs(emf_c - 50) < 1e-9
w50 = 2 * math.pi * 50; emf0 = 100 * 0.15 * 0.020 * w50; assert abs(emf0 - 94.25) < 0.01
def induction_fig():
    it = [LINE(40, 60, 320, 60, width=3), LINE(40, 190, 320, 190, width=3), LINE(40, 60, 40, 190, width=3),
          LINE(180, 50, 180, 200, color="red", width=5), T(190, 40, "rod, length l", 12, anchor="start", color="red"),
          LINE(185, 125, 255, 125, color="blue", width=3, arrow="end"), T(255, 112, "v", 15, anchor="start", color="blue"),
          T(60, 125, "emf = B l v", 14, anchor="start", bold=True)]
    for x in (100, 140, 230, 280):
        for y in (90, 160):
            it.append(T(x, y, "×", 14, color="grey"))
    return shapes(it, 380, 240)
lesson(ch, "induction", "Electromagnetic induction",
  ["Define magnetic flux and flux linkage and state Faraday's and Lenz's laws",
   "Calculate the emf induced in a moving conductor (ε = Blv), a changing field and a rotating coil",
   "Explain eddy currents and the operation of a simple a.c. generator"],
  [("definition", "Flux and the laws of induction",
    "**Magnetic flux** Φ = BA cos θ (weber, Wb = T m²) where θ is the angle between B and the normal to the area. **Flux linkage** = NΦ for a coil of N turns.\n\n"
    "**Faraday's law**: the induced emf equals the rate of change of flux linkage: **ε = −N ΔΦ/Δt**. **Lenz's law** (the minus sign): the induced current flows in a direction that opposes the change producing it (conservation of energy)."),
   ("formule", "Useful results",
    "- Straight conductor of length l moving at speed v at right angles to B: **ε = Blv**.\n- Coil of N turns, area A, rotating at angular speed ω in field B: **ε = NBAω sin ωt**, so the peak emf is ε₀ = NBAω.\n- Eddy currents: induced currents in a bulk conductor that oppose the motion (used in induction cookers and electromagnetic braking, and reduced by laminating transformer cores).",
    r"\varepsilon=-N\frac{\Delta\Phi}{\Delta t}\qquad \varepsilon=Blv\qquad \varepsilon_0=NBA\omega"),
   ("retenir", "Direction and energy",
    "If a magnet is pushed towards a coil, the coil end nearest the magnet becomes the same pole as the approaching pole, repelling it: work must be done to push it, and that work becomes electrical energy. If Lenz's law were reversed, energy would be created from nothing.\n\nFor a moving rod: electrical power εI = mechanical power Fv."),
   ("pieges", "Common mistakes",
    "- Thinking an emf is induced by a steady flux: only a **change** of flux induces an emf.\n- Forgetting N, the number of turns, in Faraday's law.\n- Treating the minus sign as meaning a negative emf rather than 'opposing the change'.\n- Mixing flux (Wb) with flux density (T).")],
  [("Example 1 — Moving rod", "A rod 0.50 m long moves at 4.0 m/s at right angles to a field of 0.30 T on rails joined to a 2.0 Ω resistor. Find the emf, the current, the magnetic force on the rod and the power.",
    ["ε = Blv = 0.30 × 0.50 × 4.0 = 0.60 V; I = 0.60/2.0 = 0.30 A.", "Force F = BIl = 0.30 × 0.30 × 0.50 = 0.045 N, opposing the motion.", "Power = Fv = 0.045 × 4.0 = 0.18 W = εI ✓ (the energy comes from whoever pushes the rod)."], "ε = 0.60 V, I = 0.30 A, F = 0.045 N, P = 0.18 W"),
   ("Example 2 — Changing field", "A coil of 200 turns and area 1.0 × 10⁻² m² has its axis along a field that falls from 0.50 T to zero in 0.020 s. Find the average emf.",
    ["Change of flux = ΔB × A = 0.50 × 1.0 × 10⁻² = 5.0 × 10⁻³ Wb.", "ε = NΔΦ/Δt = 200 × 5.0 × 10⁻³ ÷ 0.020 = 50 V."], "ε = 50 V")],
  [N("A wire 0.20 m long moves at 3.0 m/s across a field of 0.50 T, perpendicular to it. Find the induced emf (V).", 0.50 * 0.20 * 3.0, "ε = Blv = 0.50 × 0.20 × 3.0 = 0.30 V.", 0.005, "V"),
   N("A coil of area 5.0 × 10⁻³ m² is perpendicular to a field of 0.20 T. Find the flux through it (mWb).", 0.20 * 5.0e-3 * 1000, "Φ = BA = 0.20 × 5.0 × 10⁻³ = 1.0 × 10⁻³ Wb = 1.0 mWb.", 0.005, "mWb"),
   TF("True or false: a steady magnetic field through a stationary coil induces a constant emf.", False, "Only a changing flux linkage induces an emf; a steady flux induces none.")],
  [N("The flux linking a 50-turn coil changes uniformly from 8.0 mWb to 2.0 mWb in 0.10 s. Find the average induced emf (V).", 50 * 6.0e-3 / 0.10, "ε = NΔΦ/Δt = 50 × 6.0 × 10⁻³ ÷ 0.10 = 3.0 V.", 0.02, "V", difficulty=2),
   M("A magnet falls through a long copper tube much more slowly than through a plastic tube because", "eddy currents in the copper oppose the motion", ["copper is magnetic", "plastic is heavier", "air resistance is greater in copper"], "Induced currents in the copper produce a force opposing the magnet's motion (Lenz's law).")],
  P("A simple generator has a coil of 100 turns, area 2.0 × 10⁻² m², rotating at 50 revolutions per second in a uniform field of 0.15 T.",
    [pn("Angular speed ω (rad/s, 3 s.f.).", sg(w50, 3), "ω = 2π × 50 = 314 rad/s.", 0.5, "rad/s", 1),
     pn("Peak emf (V, 3 s.f.).", sg(emf0, 3), "ε₀ = NBAω = 100 × 0.15 × 2.0 × 10⁻² × 314 = 94.2 V.", 0.2, "V", 2),
     pn("Frequency of the output (Hz).", 50, "The emf has the same frequency as the rotation: 50 Hz.", 0, "Hz", 1),
     pm("If the coil rotated at twice the speed, the peak emf would", "double (and so would the frequency)", ["stay the same", "halve", "quadruple"], "ε₀ ∝ ω.", 1)]),
  [M("Lenz's law is a consequence of conservation of", "energy", ["charge", "momentum", "mass"], "The induced current opposes the change, so work must be done."),
   M("The SI unit of magnetic flux, the weber, is", "T m²", ["T m⁻²", "T", "N A⁻¹"], "Φ = BA."),
   M("A bar magnet is pushed into a coil faster. The induced emf", "increases", ["decreases", "is the same", "reverses"], "Rate of change of flux is larger."),
   M("Laminated cores in transformers reduce", "eddy current losses", ["the voltage", "the frequency", "the resistance of the wire"], "Thin insulated sheets break up the paths for eddy currents."),
   M("A rod moves parallel to the magnetic field lines. The induced emf is", "zero", ["Blv", "maximum", "negative"], "No field lines are cut.")],
  (induction_fig(), "A rod sliding on rails in a field into the page: emf = Blv.", "Two horizontal rails joined at the left, a vertical rod moving to the right, crosses showing a field into the page and the emf formula."),
  notes=["Self-inductance and energy stored in an inductor are not covered; check the syllabus.", "Direction rules (Fleming's right-hand rule) are described through Lenz's law only."], minutes=35, prereq=["gceal-physics-bfield"])

# ---- 2.3 alternating current, transformers
V0m = 220 * math.sqrt(2); assert abs(V0m - 311.1) < 0.05
Ns = 1100 * 12 / 220; assert Ns == 60
I_hv = 100e3 / 10e3; Ploss_hv = I_hv ** 2 * 5; I_lv = 100e3 / 1e3; Ploss_lv = I_lv ** 2 * 5
assert Ploss_hv == 500 and Ploss_lv == 50000
Xc = 1 / (2 * math.pi * 50 * 10e-6); I_xc = 220 / Xc; assert abs(Xc - 318.3) < 0.1 and abs(I_xc - 0.691) < 0.001
def ac_fig():
    return plot(0, 2, -1.3, 1.3, curves=[("sin(2*pi*x)", "blue", "V")], segments=[(0, 0.707, 2, 0.707, "red", True, "0.707 V₀ = V_rms")], grid=1, xlabel="t / T", ylabel="V / V₀", w=400, h=250)
lesson(ch, "ac", "Alternating current and transformers",
  ["Describe a sinusoidal alternating current and use peak, r.m.s. values, frequency and period",
   "Use P = I_rms V_rms and the transformer equation V_s/V_p = N_s/N_p; explain high-voltage power transmission",
   "Describe half-wave and full-wave rectification, and the reactance of a capacitor"],
  [("definition", "Alternating current and r.m.s. values",
    "An alternating current varies sinusoidally: **I = I₀ sin ωt**, **V = V₀ sin ωt** with ω = 2πf. The **r.m.s.** (root mean square) value is the steady d.c. value that would dissipate the same power in a resistor:\n\n**I_rms = I₀/√2**, **V_rms = V₀/√2**. Mean power P = I_rms V_rms = ½I₀V₀ = I_rms²R."),
   ("formule", "Transformers",
    "A transformer works by mutual induction in two coils on a soft-iron core and needs a.c. For an ideal transformer: **V_s/V_p = N_s/N_p** and (100 % efficient) V_pI_p = V_sI_s, so a step-up transformer gives a smaller current.\n\n"
    "Real transformers lose energy by resistance of the coils (heating), eddy currents, hysteresis and flux leakage; efficiency = P_out/P_in × 100 %.",
    r"\frac{V_s}{V_p}=\frac{N_s}{N_p}\qquad P=I_{rms}V_{rms}"),
   ("retenir", "Power transmission and rectification",
    "Electricity is transmitted at high voltage and low current to reduce the power lost in the cables (P_loss = I²R). A **diode** conducts in one direction only: **half-wave rectification** removes half the cycle; four diodes in a bridge give **full-wave rectification**; a smoothing capacitor reduces the ripple.\n\nThe **reactance** of a capacitor to a.c. is X_C = 1/(2πfC): it falls with frequency (blocks d.c.)."),
   ("pieges", "Common mistakes",
    "- Quoting mains as 220 V peak: 220 V is the r.m.s. value; the peak is about 311 V.\n- Using the peak value in P = V²/R: use r.m.s.\n- Believing a transformer works on d.c. (a steady current gives no changing flux).\n- Forgetting that a step-up transformer does not give more power: it steps down the current.")],
  [("Example 1 — Peak and r.m.s.", "A mains supply is rated 220 V (r.m.s.), 50 Hz. Find the peak voltage, the period, and the mean power in a 44 Ω heater.",
    ["V₀ = √2 × V_rms = 1.414 × 220 = 311 V; T = 1/f = 0.020 s.", "P = V_rms²/R = 220²/44 = 1100 W."], "V₀ = 311 V; T = 20 ms; P = 1.1 kW"),
   ("Example 2 — Transformer and transmission", "(a) A transformer has 1100 turns on the primary (220 V). How many secondary turns give 12 V? (b) A 100 kW supply is sent along cables of total resistance 5.0 Ω at 10 kV, and at 1.0 kV. Find the power loss.",
    ["(a) N_s = N_p V_s/V_p = 1100 × 12/220 = 60 turns.", "(b) At 10 kV: I = P/V = 100 000/10 000 = 10 A, loss = I²R = 100 × 5.0 = 500 W.", "At 1.0 kV: I = 100 A, loss = 100² × 5.0 = 50 000 W (half the power!). High voltage wastes far less."], "(a) 60 turns; (b) 500 W against 50 kW")],
  [N("A sinusoidal voltage has peak value 170 V. Find the r.m.s. value (V, 3 s.f.).", sg(170 / math.sqrt(2), 3), "V_rms = 170/√2 = 120 V.", 0.5, "V"),
   N("A transformer has 500 primary turns and 50 secondary turns. The primary voltage is 220 V. Find the secondary voltage (V).", 22, "V_s = 220 × 50/500 = 22 V.", 0.05, "V"),
   TF("True or false: a transformer will work with a steady direct current in the primary coil.", False, "A steady current produces a constant flux in the core, so no emf is induced in the secondary.")],
  [N("A transformer of efficiency 95 % delivers 190 W to a load. Find the input power (W).", 190 / 0.95, "P_in = P_out/efficiency = 190/0.95 = 200 W.", 0.5, "W", difficulty=2),
   N("Find the reactance of a 10 μF capacitor at 50 Hz (Ω, 3 s.f.).", sg(Xc, 3), "X_C = 1/(2πfC) = 1/(2π × 50 × 10 × 10⁻⁶) = 318 Ω.", 1, "Ω")],
  P("A school generator supplies 5.0 kW at 400 V (r.m.s.) to a distant workshop through cables of total resistance 2.0 Ω.",
    [pn("Current in the cables (A).", 5000 / 400, "I = P/V = 5000/400 = 12.5 A.", 0.05, "A", 1),
     pn("Power lost in the cables (W).", 12.5 ** 2 * 2.0, "P_loss = I²R = 12.5² × 2.0 = 312.5 W.", 0.5, "W", 1),
     pn("The voltage is stepped up to 4000 V by a transformer (and back down at the workshop). New power lost in the cables (W).", (5000 / 4000) ** 2 * 2.0, "I = 5000/4000 = 1.25 A; loss = 1.25² × 2.0 = 3.125 W, a hundred times smaller.", 0.05, "W", 2)]),
  [M("The r.m.s. value of an alternating current of peak 5.0 A is", "3.5 A", ["5.0 A", "7.1 A", "2.5 A"], "5.0/√2 = 3.54 A."),
   M("A step-up transformer", "increases the voltage and decreases the current", ["increases the power", "increases both", "works with d.c."], "Power in = power out (ideal)."),
   M("Which device converts a.c. to d.c.?", "diode (rectifier)", ["transformer", "capacitor alone", "resistor"], "Diodes conduct in one direction only."),
   M("The frequency of mains supply in Cameroon is", "50 Hz", ["60 Hz", "100 Hz", "25 Hz"], "50 Hz is the standard in Cameroon (to be confirmed by the teacher)."),
   M("Power transmission uses high voltage in order to", "reduce I²R losses", ["increase the current", "increase the frequency", "reduce the power needed"], "Lower current means smaller heating losses.")],
  (ac_fig(), "A sinusoidal alternating voltage: the r.m.s. value is 0.707 of the peak.", "A sine wave over two periods with a dashed horizontal line at 0.707 of the peak value."),
  notes=["Mains in Cameroon: 220 V (r.m.s.) and 50 Hz assumed; to be confirmed.", "Reactance, impedance of RLC circuits and resonance are only touched (capacitor reactance); check the syllabus before extending."], minutes=35, prereq=["gceal-physics-induction"])

# =============================================================== 3. EM WAVES AND OPTICS
ch = p.chapter("ch-optics", "Electromagnetic waves and optics", SYL % "electromagnetic waves, optics")
f_red = c_ / 656e-9; assert abs(f_red - 4.57e14) < 0.01e14
t_sun = 1.5e11 / c_; assert abs(t_sun - 500) < 1e-6
I_pol = 1 / 2 * math.cos(rad(60)) ** 2; assert abs(I_pol - 0.125) < 1e-12
lam_gsm = c_ / 900e6; assert abs(lam_gsm - 0.333) < 0.001
def spectrum_fig():
    bands = [("radio", "lightorange"), ("micro", "lightyellow"), ("IR", "pink"), ("light", "lightgreen"), ("UV", "lightblue"), ("X-ray", "lightgrey"), ("γ", "purple")]
    it = []
    for i, (n, col) in enumerate(bands):
        x = 12 + i * 55
        it += [RECT(x, 60, 52, 50, fill=col), T(x + 26, 90, n, 12)]
    it += [LINE(30, 140, 370, 140, color="red", width=2, arrow="end"), T(200, 165, "frequency and photon energy increase", 13), T(200, 40, "all travel at c = 3.00 × 10⁸ m/s in vacuum", 13)]
    return shapes(it, 400, 190)
lesson(ch, "emwaves", "The electromagnetic spectrum and polarisation",
  ["Describe the electromagnetic spectrum, its common properties and the uses and hazards of each region",
   "Use c = fλ and calculate times of travel of signals",
   "Explain polarisation as evidence that light is transverse and use Malus's law I = I₀cos²θ"],
  [("definition", "Electromagnetic waves",
    "Electromagnetic (e.m.) waves are oscillating, perpendicular electric and magnetic fields travelling together. They are **transverse**, need no medium, travel at **c = 3.00 × 10⁸ m/s** in vacuum, carry energy, and can be reflected, refracted, diffracted, polarised and can interfere.\n\n"
    "In order of increasing frequency: **radio, microwaves, infrared, visible light, ultraviolet, X-rays, γ-rays**. Always c = fλ."),
   ("retenir", "Uses and hazards",
    "- Radio: broadcasting (FM, mobile networks). Microwaves: mobile phones, radar, satellite links, cooking.\n- Infrared: remote controls, heaters, thermal imaging. Visible light: vision, optical fibres.\n- UV: sterilising water, security marks; overexposure damages skin and eyes. X-rays: medical imaging; ionising. γ-rays: cancer treatment, sterilisation; highly ionising.\n\nHigher-frequency radiation carries more energy per photon (E = hf), so is more hazardous."),
   ("formule", "Polarisation",
    "In **plane-polarised** light the electric field oscillates in one plane only. Only transverse waves can be polarised, so polarisation shows light is transverse. A polaroid transmits only one plane: unpolarised light passing through one polaroid has its intensity **halved**.\n\nFor plane-polarised light incident on a second polaroid (analyser) at angle θ: **I = I₀ cos²θ** (Malus's law).",
    r"I=I_0\cos^{2}\theta"),
   ("pieges", "Common mistakes",
    "- Saying sound can be polarised: sound is longitudinal.\n- Forgetting the factor ½ when unpolarised light meets the first polaroid.\n- Mixing up the order of the spectrum (gamma has the shortest wavelength).\n- Using wavelengths in nm or cm without converting to metres.")],
  [("Example 1 — Frequencies", "Find (a) the frequency of red light of wavelength 656 nm, (b) the wavelength of a 900 MHz mobile signal, (c) the time light takes to reach us from the Sun (1.5 × 10¹¹ m).",
    ["(a) f = c/λ = 3.00 × 10⁸ ÷ 656 × 10⁻⁹ = 4.57 × 10¹⁴ Hz.", "(b) λ = c/f = 3.00 × 10⁸ ÷ 9.0 × 10⁸ = 0.33 m.", "(c) t = d/c = 1.5 × 10¹¹ ÷ 3.00 × 10⁸ = 500 s ≈ 8.3 min."], "(a) 4.57 × 10¹⁴ Hz; (b) 0.33 m; (c) 500 s"),
   ("Example 2 — Two polaroids", "Unpolarised light of intensity I₀ passes through a polaroid, then through a second polaroid whose axis is at 60° to the first. Find the final intensity.",
    ["After the first polaroid: I₁ = I₀/2 (unpolarised light is halved).", "After the second: I₂ = I₁ cos² 60° = (I₀/2) × (0.5)² = I₀/8."], "I = I₀/8 = 0.125 I₀")],
  [N("Find the wavelength (m) of a radio wave of frequency 100 MHz (c = 3.00 × 10⁸ m/s).", 3.0, "λ = c/f = 3.00 × 10⁸ ÷ 1.00 × 10⁸ = 3.0 m.", 0.01, "m"),
   N("A signal is sent to a satellite 3.6 × 10⁷ m away. How long does it take to arrive (s)?", 3.6e7 / c_, "t = d/c = 3.6 × 10⁷ ÷ 3.00 × 10⁸ = 0.12 s.", 0.002, "s"),
   TF("True or false: X-rays and radio waves travel at the same speed in vacuum.", True, "All e.m. waves travel at c in vacuum.")],
  [N("Plane-polarised light of intensity 80 W m⁻² passes through an analyser at 30° to its plane. Find the transmitted intensity (W m⁻²).", 80 * math.cos(rad(30)) ** 2, "I = I₀cos²30° = 80 × 0.75 = 60 W m⁻².", 0.1, "W/m²", difficulty=2),
   M("Which radiation has the highest photon energy?", "γ-rays", ["X-rays", "ultraviolet", "microwaves"], "Photon energy E = hf increases with frequency; gamma has the highest frequency.")],
  P("A cellular network operates at 900 MHz and a satellite TV link at 12 GHz (c = 3.00 × 10⁸ m/s).",
    [pn("Wavelength of the 900 MHz signal (m, 3 s.f.).", sg(lam_gsm, 3), "λ = c/f = 3.00 × 10⁸ ÷ 9.00 × 10⁸ = 0.333 m.", 0.002, "m", 1),
     pn("Wavelength of the 12 GHz signal (cm, 3 s.f.).", sg(c_ / 12e9 * 100, 3), "λ = 3.00 × 10⁸ ÷ 1.2 × 10¹⁰ = 0.025 m = 2.50 cm.", 0.02, "cm", 1),
     pm("Which part of the spectrum are these signals in?", "microwaves", ["infrared", "visible light", "X-rays"], "Wavelengths from about 1 mm to 30 cm are microwaves.", 1),
     pm("Which is true of both signals?", "They are transverse and travel at c in a vacuum", ["They are longitudinal", "The 12 GHz signal is faster", "They need air to travel"], "All e.m. waves are transverse and travel at c in vacuum.", 1)]),
  [M("Which of these cannot be polarised?", "sound", ["light", "radio waves", "microwaves"], "Sound is longitudinal."),
   M("Electromagnetic waves are", "transverse", ["longitudinal", "mechanical", "always visible"], "E and B are perpendicular to the direction of travel."),
   M("The wavelength of green light is about", "550 nm", ["550 mm", "55 μm", "5.5 nm"], "Visible light is about 400–700 nm."),
   M("Unpolarised light of intensity I₀ passes through a single ideal polaroid. The transmitted intensity is", "I₀/2", ["I₀", "I₀/4", "0"], "Half the intensity is transmitted."),
   M("Which radiation is used to sterilise surgical equipment?", "gamma rays", ["radio waves", "microwaves", "infrared only"], "Highly ionising radiation kills microbes.")],
  (spectrum_fig(), "The electromagnetic spectrum from radio waves to γ-rays (not to scale).", "Seven coloured boxes in order: radio, microwaves, infrared, light, ultraviolet, X-ray, gamma, with an arrow showing increasing frequency."),
  notes=["Approximate wavelength ranges for the bands are deliberately not given; the board may expect them.", "Some syllabuses define microwaves/radio boundaries differently; 900 MHz for mobile networks is a typical figure (check local operators)."], minutes=30, prereq=["gceal-physics-ac"])

# ---- 3.2 refraction, TIR, lenses
th1 = 40.0; n_g = 1.50; th2 = math.degrees(math.asin(math.sin(rad(th1)) / n_g)); assert abs(th2 - 25.4) < 0.05
C_g = math.degrees(math.asin(1 / 1.5)); C_w = math.degrees(math.asin(1 / 1.33)); assert abs(C_g - 41.8) < 0.05 and abs(C_w - 48.75) < 0.1
f_l = 10.0; v1l = 1 / (1 / f_l - 1 / 30.0); m1l = v1l / 30.0; v2l = 1 / (1 / f_l - 1 / 5.0); m2l = v2l / 5.0
assert abs(v1l - 15) < 1e-9 and abs(m1l - 0.5) < 1e-9 and abs(v2l + 10) < 1e-9 and abs(m2l + 2) < 1e-9
def refr_fig():
    cx, cy, L = 190, 110, 100
    s1, c1, s2, c2 = math.sin(rad(th1)), math.cos(rad(th1)), math.sin(rad(th2)), math.cos(rad(th2))
    it = [RECT(40, cy, 300, 100, fill="lightblue", stroke="blue", width=1), T(310, 200, "glass", 13, color="blue"), T(310, 20, "air", 13),
          LINE(cx, 20, cx, 210, dash=True, color="grey", width=1), T(cx + 6, 26, "normal", 12, anchor="start", color="grey"),
          LINE(cx - L * s1, cy - L * c1, cx, cy, color="red", width=3, arrow="end"), LINE(cx, cy, cx + L * s2, cy + L * c2, color="red", width=3, arrow="end"),
          T(cx - 60, cy - 70, "θ₁ = 40°", 13, anchor="end"), T(cx + 60, cy + 40, "θ₂ = 25.4°", 13, anchor="start")]
    return shapes(it, 380, 230)
lesson(ch, "refraction", "Refraction, total internal reflection and lenses",
  ["Use Snell's law n₁ sin θ₁ = n₂ sin θ₂ and the critical angle sin C = 1/n",
   "Explain total internal reflection and the optical fibre",
   "Use the thin-lens equation 1/f = 1/u + 1/v with the 'real is positive' convention and magnification m = v/u"],
  [("definition", "Refractive index",
    "The **refractive index** of a medium n = c/v (speed of light in vacuum ÷ speed in the medium). **Snell's law**: n₁ sin θ₁ = n₂ sin θ₂, with angles measured from the **normal**. Light bends towards the normal on entering a denser medium (where it is slower and the wavelength is shorter; the frequency does not change).\n\n"
    "Typical values: water 1.33, glass about 1.5."),
   ("formule", "Total internal reflection",
    "Light travelling from a denser medium to a less dense medium is totally reflected when the angle of incidence exceeds the **critical angle** C, where **sin C = 1/n** (for a boundary with air). Conditions: denser → less dense, and θ > C.\n\n"
    "Uses: **optical fibres** (a core with a cladding of lower index), prisms in binoculars, reflectors. Fibres are used for telecommunications and endoscopes.",
    r"n=\frac{\sin\theta_1}{\sin\theta_2}\qquad \sin C=\frac{1}{n}"),
   ("formule", "Thin lenses",
    "For a thin lens: **1/f = 1/u + 1/v** (real-is-positive convention: u and v positive for real objects/images; v negative for a virtual image; f positive for a converging lens, negative for a diverging one). Magnification **m = v/u** = image height ÷ object height. Power P = 1/f in dioptres (f in metres).\n\n"
    "A converging lens gives a **real inverted** image if the object is beyond f and a **virtual magnified upright** image if it is within f (magnifying glass).",
    r"\frac{1}{f}=\frac{1}{u}+\frac{1}{v}\qquad m=\frac{v}{u}"),
   ("pieges", "Common mistakes",
    "- Measuring angles from the surface instead of from the normal.\n- Trying to have total internal reflection going from air to glass: it only occurs from the denser to the less dense medium.\n- Sign errors in the lens formula: a negative v means a virtual image on the same side as the object.\n- Forgetting that frequency does not change on refraction (only speed and wavelength do).")],
  [("Example 1 — Refraction in glass", "Light in air strikes glass (n = 1.50) at 40° to the normal. Find the angle of refraction and the speed of light in the glass.",
    ["sin θ₂ = sin 40°/1.50 = 0.6428/1.50 = 0.4285, so θ₂ = 25.4°.", "v = c/n = 3.00 × 10⁸/1.50 = 2.00 × 10⁸ m/s."], "θ₂ = 25.4°; v = 2.00 × 10⁸ m/s"),
   ("Example 2 — A converging lens", "A converging lens has f = 10 cm. Find the image position and magnification for an object (a) 30 cm and (b) 5 cm from the lens.",
    ["(a) 1/v = 1/10 − 1/30 = 2/30, so v = 15 cm (real); m = 15/30 = 0.50 (diminished, inverted).", "(b) 1/v = 1/10 − 1/5 = −1/10, so v = −10 cm (virtual, same side); m = −10/5 = −2 in this convention: magnified ×2 and upright."], "(a) v = 15 cm, m = 0.5; (b) v = −10 cm, magnified × 2")],
  [N("Find the critical angle for glass of n = 1.50 (degrees, 3 s.f.).", sg(C_g, 3), "sin C = 1/1.50 = 0.667, so C = 41.8°.", 0.1, "°"),
   N("Light travels in water (n = 1.33). Find its speed (10⁸ m/s, 3 s.f.).", sg(c_ / 1.33 / 1e8, 3), "v = c/n = 3.00 × 10⁸ ÷ 1.33 = 2.26 × 10⁸ m/s.", 0.01, "× 10⁸ m/s"),
   TF("True or false: the frequency of light changes when it passes from air into glass.", False, "The frequency is fixed by the source; the speed and wavelength decrease.")],
  [N("An object is 20 cm from a converging lens of focal length 15 cm. Find the image distance (cm).", 1 / (1 / 15 - 1 / 20), "1/v = 1/15 − 1/20 = 1/60, so v = 60 cm (real image).", 0.1, "cm", difficulty=2),
   N("A lens has focal length 25 cm. Find its power (dioptres).", 4.0, "P = 1/f = 1/0.25 = 4.0 D.", 0.01, "D")],
  P("A ray of light inside a glass block (n = 1.50) meets the glass–air boundary.",
    [pn("Critical angle (degrees, 3 s.f.).", sg(C_g, 3), "sin C = 1/1.50, C = 41.8°.", 0.1, "°", 1),
     pm("The angle of incidence is 50°. The ray is", "totally internally reflected", ["refracted into the air", "partly absorbed only", "not reflected at all"], "50° > 41.8°, so there is total internal reflection.", 1),
     pn("For an angle of incidence of 30° inside the glass, find the angle of refraction in air (degrees, 3 s.f.).", sg(math.degrees(math.asin(1.5 * math.sin(rad(30)))), 3), "1.50 sin 30° = 1 × sin θ₂: sin θ₂ = 0.75, θ₂ = 48.6°.", 0.1, "°", 2)], figure=refr_fig()),
  [M("The refractive index of a material is", "c divided by the speed of light in it", ["the speed of light in it divided by c", "the wavelength in it", "the frequency in it"], "n = c/v."),
   M("Total internal reflection can occur when light goes from", "glass to air at more than the critical angle", ["air to glass at any angle", "air to water at 45°", "glass to glass at 0°"], "Denser to less dense and θ > C."),
   M("A converging lens forms a virtual, upright, magnified image when the object is", "closer than the focal length", ["at 2f", "at infinity", "beyond 2f"], "Inside the focal length the rays still diverge after the lens."),
   M("A lens of focal length 0.50 m has a power of", "2.0 D", ["0.50 D", "5.0 D", "0.20 D"], "P = 1/f."),
   M("Which does NOT change when light enters glass from air?", "frequency", ["speed", "wavelength", "direction (in general)"], "The frequency is set by the source.")],
  (refr_fig(), "A ray entering glass at 40° bends towards the normal (θ₂ = 25.4°).", "A ray in air hitting a glass block at 40 degrees from the dashed normal and bending to 25.4 degrees inside."),
  notes=["n for water 1.33 and glass 1.50 are typical values.", "Real-is-positive sign convention is used; boards sometimes use Cartesian sign conventions — check.", "Optical instruments (microscope, telescope) are not covered."], minutes=35, prereq=["gceal-physics-emwaves"])

# ---- 3.3 diffraction grating
d_g = 1e-3 / 300; th1g = math.degrees(math.asin(589e-9 / d_g)); th2g = math.degrees(math.asin(2 * 589e-9 / d_g)); nmax = int(d_g / 589e-9)
assert abs(th1g - 10.18) < 0.02 and abs(th2g - 20.7) < 0.05 and nmax == 5
d5 = 1e-3 / 500; ang = lambda n, lm: math.degrees(math.asin(n * lm / d5))
assert abs(ang(1, 450e-9) - 13.0) < 0.05 and abs(ang(1, 650e-9) - 18.97) < 0.05 and abs(ang(2, 650e-9) - 40.5) < 0.1
def grating_fig():
    it = [LINE(60, 20, 60, 220, width=4), T(60, 238, "grating", 12), LINE(10, 120, 60, 120, color="red", width=3, arrow="end"), T(35, 105, "beam", 12)]
    for n, th, lab in [(0, 0, "n = 0"), (1, 10.2, "n = 1"), (-1, -10.2, "n = 1"), (2, 20.7, "n = 2"), (-2, -20.7, "n = 2")]:
        x2, y2 = 60 + 260 * math.cos(rad(th)), 120 - 260 * math.sin(rad(th))
        it += [LINE(60, 120, x2, y2, color="red", width=2, dash=(n != 0), arrow="end"), T(x2 + 6, y2 + (4 if th >= 0 else 4), lab, 12, anchor="start")]
    it.append(T(200, 20, "d sin θ = nλ", 14, bold=True))
    return shapes(it, 400, 250)
lesson(ch, "grating", "Diffraction gratings",
  ["State and use d sin θ = nλ for a diffraction grating and explain why the maxima are sharp",
   "Determine wavelengths and the maximum number of orders, and describe the spectrum from white light",
   "Compare the double-slit and the grating patterns and use the single-slit minimum a sin θ = λ"],
  [("definition", "The grating equation",
    "A **diffraction grating** has a large number of equally spaced parallel slits (often 300–600 lines per mm). Monochromatic light of wavelength λ gives bright maxima at angles θ given by **d sin θ = nλ**, where d is the slit spacing (d = 1/N for N lines per metre) and n = 0, 1, 2… is the **order**.\n\n"
    "A large number of slits makes the maxima narrow and bright, so wavelengths can be measured accurately."),
   ("methode", "Method",
    "1. Convert the line density to a spacing: d = 1/N (N in lines per metre; e.g. 300 lines/mm = 3.00 × 10⁵ m⁻¹, d = 3.33 × 10⁻⁶ m).\n2. For a given order: sin θ = nλ/d.\n3. The highest order occurs when sin θ ≤ 1: n_max is the largest integer ≤ d/λ.\n4. The central maximum (n = 0) of white light is white; the higher orders are spectra with violet nearest the centre and red furthest."),
   ("retenir", "Single slit and comparisons",
    "A single slit of width a produces a wide central maximum with the first minimum at **a sin θ = λ**; a narrower slit gives a wider pattern. The double-slit fringes have an envelope given by the single-slit pattern.\n\nThe grating gives fewer, sharper and brighter maxima at larger angles than two slits of the same spacing."),
   ("pieges", "Common mistakes",
    "- Using the number of lines per mm directly as d: d is the reciprocal and must be in metres.\n- Forgetting that n = 0 is a maximum (not counted among the 'orders' on each side).\n- Counting the total number of maxima as 2n_max rather than 2n_max + 1.\n- Saying red light is deviated less than violet: red has the longer wavelength, so it is deviated **more**.")],
  [("Example 1 — Sodium light", "A grating has 300 lines per mm. Sodium light of wavelength 589 nm falls normally on it. Find the angles of the first and second orders and the maximum number of orders.",
    ["d = 1.0 × 10⁻³/300 = 3.33 × 10⁻⁶ m.", "n = 1: sin θ = 589 × 10⁻⁹/3.33 × 10⁻⁶ = 0.1767, so θ = 10.2°. n = 2: sin θ = 0.3534, so θ = 20.7°.", "d/λ = 5.66, so n_max = 5 (sin θ ≤ 1); the total number of maxima is 2 × 5 + 1 = 11."], "10.2°, 20.7°; 5 orders each side; 11 maxima in all"),
   ("Example 2 — Two wavelengths", "A grating with 500 lines per mm receives light containing 450 nm and 650 nm. Find the angular separation of the two first-order maxima.",
    ["d = 1.0 × 10⁻³/500 = 2.0 × 10⁻⁶ m.", "450 nm: sin θ = 0.225, θ = 13.0°. 650 nm: sin θ = 0.325, θ = 19.0°.", "Separation = 19.0° − 13.0° = 6.0°."], "6.0° (the 650 nm light is deviated more)")],
  [N("A grating has 400 lines per mm. Find the spacing d (μm).", 1e3 / 400, "d = 1/400 mm = 2.5 × 10⁻³ mm = 2.5 μm.", 0.005, "μm"),
   N("Light of wavelength 500 nm passes through a grating of d = 2.0 μm. Find the angle of the first-order maximum (degrees, 3 s.f.).", sg(math.degrees(math.asin(500e-9 / 2.0e-6)), 3), "sin θ = λ/d = 0.25, θ = 14.5°.", 0.1, "°"),
   TF("True or false: in the spectrum of white light from a grating, red is deviated more than violet.", True, "θ increases with λ in d sin θ = nλ, so red (long λ) is deviated most.")],
  [N("Using a grating with 600 lines per mm, a second-order maximum for light is seen at 47.0°. Find the wavelength (nm, 3 s.f.).", sg(math.sin(rad(47.0)) * (1e-3 / 600) / 2 * 1e9, 3), "d = 1.667 × 10⁻⁶ m; λ = d sin θ/n = 1.667 × 10⁻⁶ × 0.7314 ÷ 2 = 6.09 × 10⁻⁷ m = 609 nm.", 2, "nm", difficulty=2),
   N("What is the highest order visible with light of 589 nm and a grating of 300 lines per mm?", 5, "d/λ = 3.33 × 10⁻⁶/5.89 × 10⁻⁷ = 5.66, so the highest order is 5.", 0, None)],
  P("A grating with 500 lines per mm is used with a laser of wavelength 633 nm, incident normally. Screen distance 1.50 m.",
    [pn("Slit spacing d (μm).", 2.0, "d = 1/500 mm = 2.0 μm.", 0.005, "μm", 1),
     pn("Angle of the first-order beam (degrees, 3 s.f.).", sg(ang(1, 633e-9), 3), "sin θ = 633 × 10⁻⁹/2.0 × 10⁻⁶ = 0.3165, θ = 18.5°.", 0.1, "°", 1),
     pn("Highest order observed.", 3, "d/λ = 2.0/0.633 = 3.16, so n_max = 3.", 0, None, 1),
     pn("Distance on the screen from the centre to the first-order spot (m, 2 s.f.).", sg(1.5 * math.tan(math.asin(633e-9 / 2.0e-6)), 2), "x = D tan θ = 1.50 × tan 18.45° = 0.50 m.", 0.02, "m", 1)]),
  [M("The condition for a maximum from a diffraction grating is", "d sin θ = nλ", ["d cos θ = nλ", "λ sin θ = nd", "d = nλ sin θ"], "Path difference between neighbouring slits is d sin θ."),
   M("A grating with more lines per mm gives a larger angle for the same order because", "d is smaller", ["λ is larger", "the light is faster", "the light is polarised"], "sin θ = nλ/d increases as d decreases."),
   M("The central maximum (n = 0) of white light is", "white", ["red", "violet", "dark"], "All wavelengths have zero path difference there."),
   M("For which wavelength is the second-order maximum at the largest angle?", "red light", ["blue light", "violet light", "the same for all"], "Larger λ means larger sin θ."),
   M("A single slit of width a: the first minimum is at", "a sin θ = λ", ["a sin θ = λ/2", "a cos θ = λ", "a = λ sin θ"], "Path difference of λ between waves from the top and the bottom edge of the slit.")],
  (grating_fig(), "Diffraction by a grating: beams at angles θ given by d sin θ = nλ.", "A grating with a central beam and first and second order beams at angles above and below."),
  notes=["The condition for the first minimum of a single slit is a sin θ = λ (small-angle form not used).", "Normal incidence is assumed; oblique incidence is not covered."], minutes=35, prereq=["gceal-physics-refraction"])

# =============================================================== 4. QUANTUM PHYSICS
ch = p.chapter("ch-quantum", "Quantum physics", SYL % "quantum physics")
phi_eV = 2.3; phi = phi_eV * e_; f0 = phi / h_; lam0 = c_ / f0
E_uv = h_ * c_ / 300e-9; K_uv = E_uv / e_ - phi_eV; v_k = math.sqrt(2 * K_uv * e_ / me_)
assert abs(f0 - 5.55e14) < 0.02e14 and abs(lam0 * 1e9 - 540) < 2 and abs(E_uv / e_ - 4.14) < 0.01 and abs(K_uv - 1.84) < 0.01 and abs(v_k - 8.0e5) < 0.1e5, (f0, lam0, K_uv, v_k)
slope = h_ / e_; V8 = slope * 8.0e14 - phi_eV; V11 = slope * 11.0e14 - phi_eV; h_est = (V11 - V8) / 3.0e14 * e_
assert abs(V8 - 1.014) < 0.01 and abs(V11 - 2.26) < 0.01 and abs(h_est - h_) < 1e-36
def pe_fig():
    return plot(0, 12, 0, 3, segments=[(0, 0, 5.55, 0, "blue", False, None), (5.55, 0, 12, 12 * 0 + (12 - 5.55) * slope * 1e14, "blue", False, None)],
                points=[(5.55, 0, "f₀", "red")], grid=3, xlabel="f / 10¹⁴ Hz", ylabel="K max / eV", w=380, h=240)
lesson(ch, "photoelectric", "The photoelectric effect",
  ["Describe the observations of the photoelectric effect and explain why the wave model fails",
   "Use Einstein's equation hf = φ + K_max, the threshold frequency and the stopping potential",
   "Determine the Planck constant from a graph of stopping potential against frequency"],
  [("definition", "Observations",
    "When light or ultraviolet radiation falls on a clean metal surface, electrons may be emitted (**photoelectrons**). Observations:\n\n- No emission below a **threshold frequency** f₀ whatever the intensity.\n- Above f₀ emission is instantaneous, even at very low intensity.\n- The maximum kinetic energy of the electrons depends on the frequency, not on the intensity.\n- The number emitted per second is proportional to the intensity."),
   ("formule", "Einstein's photon model",
    "Light consists of **photons**, each of energy **E = hf** (h = 6.63 × 10⁻³⁴ J s). One photon gives all its energy to one electron. Part (the **work function** φ) is needed to escape the metal; the rest is kinetic energy:\n\n**hf = φ + K_max**, with φ = hf₀. The **stopping potential** V_s satisfies eV_s = K_max.",
    r"hf=\varphi+K_{\max}\qquad \varphi=hf_0\qquad eV_s=K_{\max}"),
   ("retenir", "Why the wave model fails",
    "A wave model predicts that a stronger beam should give electrons more energy, that any frequency should work given enough time, and that there would be a delay while energy builds up. None of this is observed. The photon model explains the threshold, the instant emission and the dependence on frequency.\n\n1 electronvolt (eV) = 1.60 × 10⁻¹⁹ J: the energy gained by an electron through 1 V."),
   ("pieges", "Common mistakes",
    "- Thinking a brighter beam below the threshold frequency can release electrons: it cannot.\n- Mixing eV and joules in hf = φ + K: convert consistently.\n- Reading the intercept of the V_s–f graph as φ: the y-intercept is −φ/e, the x-intercept is f₀, the gradient is h/e.\n- Saying intensity affects the maximum kinetic energy: it affects only the number of electrons.")],
  [("Example 1 — Sodium", "Sodium has a work function of 2.3 eV (a textbook value). Find the threshold frequency and then K_max and the stopping potential for ultraviolet light of wavelength 300 nm.",
    ["φ = 2.3 × 1.60 × 10⁻¹⁹ = 3.68 × 10⁻¹⁹ J; f₀ = φ/h = 3.68 × 10⁻¹⁹ ÷ 6.63 × 10⁻³⁴ = 5.55 × 10¹⁴ Hz.", "Photon energy: E = hc/λ = 6.63 × 10⁻³⁴ × 3.00 × 10⁸ ÷ 3.00 × 10⁻⁷ = 6.63 × 10⁻¹⁹ J = 4.14 eV.", "K_max = 4.14 − 2.3 = 1.84 eV, so V_s = 1.84 V."], "f₀ = 5.55 × 10¹⁴ Hz; K_max = 1.84 eV; V_s = 1.84 V"),
   ("Example 2 — Planck constant from data", "A student finds V_s = 1.01 V at f = 8.0 × 10¹⁴ Hz and V_s = 2.26 V at f = 11.0 × 10¹⁴ Hz. Estimate h.",
    ["Since eV_s = hf − φ, the gradient of V_s against f is h/e.", "Gradient = (2.26 − 1.01)/(11.0 − 8.0) × 10⁻¹⁴ = 4.17 × 10⁻¹⁵ V s.", "h = gradient × e = 4.17 × 10⁻¹⁵ × 1.60 × 10⁻¹⁹ = 6.7 × 10⁻³⁴ J s."], "h ≈ 6.7 × 10⁻³⁴ J s (accepted value 6.63 × 10⁻³⁴)")],
  [N("Find the energy of a photon of frequency 6.0 × 10¹⁴ Hz (10⁻¹⁹ J, 3 s.f.).", sg(h_ * 6.0e14 / 1e-19, 3), "E = hf = 6.63 × 10⁻³⁴ × 6.0 × 10¹⁴ = 3.98 × 10⁻¹⁹ J.", 0.02, "× 10⁻¹⁹ J"),
   N("Convert 3.2 × 10⁻¹⁹ J into electronvolts.", 2.0, "E = 3.2 × 10⁻¹⁹ ÷ 1.60 × 10⁻¹⁹ = 2.0 eV.", 0.01, "eV"),
   TF("True or false: increasing the intensity of light below the threshold frequency eventually causes electron emission.", False, "Each photon is too weak; more photons do not help because electrons absorb one photon at a time.")],
  [N("A metal has work function 4.0 eV. Find the threshold wavelength (nm, 3 s.f.).", sg(h_ * c_ / (4.0 * e_) * 1e9, 3), "λ₀ = hc/φ = 6.63 × 10⁻³⁴ × 3.00 × 10⁸ ÷ (4.0 × 1.60 × 10⁻¹⁹) = 3.11 × 10⁻⁷ m = 311 nm.", 1, "nm", difficulty=2),
   N("Light of photon energy 5.0 eV falls on a metal of work function 2.0 eV. Find the stopping potential (V).", 3.0, "K_max = 5.0 − 2.0 = 3.0 eV, so V_s = 3.0 V.", 0.01, "V")],
  P("Ultraviolet light of wavelength 300 nm falls on a sodium surface (φ = 2.3 eV). h = 6.63 × 10⁻³⁴ J s, c = 3.00 × 10⁸ m/s, e = 1.60 × 10⁻¹⁹ C, mₑ = 9.11 × 10⁻³¹ kg.",
    [pn("Energy of each photon (eV, 3 s.f.).", sg(E_uv / e_, 3), "E = hc/λ = 6.63 × 10⁻¹⁹ J = 4.14 eV.", 0.02, "eV", 1),
     pn("Maximum kinetic energy of the photoelectrons (eV, 3 s.f.).", sg(K_uv, 3), "K_max = 4.14 − 2.3 = 1.84 eV.", 0.02, "eV", 1),
     pn("Their maximum speed (10⁵ m/s, 2 s.f.).", sg(v_k / 1e5, 2), "v = √(2K/m) = √(2 × 1.84 × 1.60 × 10⁻¹⁹ ÷ 9.11 × 10⁻³¹) = 8.0 × 10⁵ m/s.", 0.1, "× 10⁵ m/s", 2),
     pm("If the intensity of the ultraviolet light is doubled, the maximum kinetic energy", "stays the same, while the number of electrons emitted per second doubles", ["doubles", "halves", "falls to zero"], "K_max depends on the photon energy (frequency), not the intensity.", 1)]),
  [M("The work function of a metal is", "the minimum energy needed to remove an electron from the surface", ["the energy of the photon", "the maximum kinetic energy", "the charge of an electron"], "It is the energy needed to escape the metal."),
   M("Which graph is a straight line for the photoelectric effect?", "K_max against frequency", ["K_max against intensity", "current against wavelength", "K_max against time"], "K_max = hf − φ."),
   M("The gradient of a graph of stopping potential against frequency is", "h/e", ["h", "e/h", "φ"], "V_s = (h/e)f − φ/e."),
   M("1 eV is equal to", "1.60 × 10⁻¹⁹ J", ["1.60 × 10⁻¹⁹ V", "9.11 × 10⁻³¹ J", "6.63 × 10⁻³⁴ J"], "The energy gained by an electron through 1 V."),
   M("The photon theory explains instantaneous emission because", "one photon gives all its energy to one electron", ["electrons absorb energy slowly", "the wave is very strong", "the metal is heated"], "There is no build-up time.")],
  (pe_fig(), "Maximum kinetic energy of photoelectrons against frequency (sodium, φ = 2.3 eV): zero below f₀, then a straight line of gradient h.", "A graph that is flat at zero up to the threshold frequency near 5.5 times 10 to the 14 hertz and then rises in a straight line."),
  notes=[DATA, "φ for sodium varies with sources (about 2.3 eV); state the value in class."], minutes=35, prereq=["gceal-physics-emwaves"])

# ---- 4.2 wave-particle duality and line spectra
lam_e = h_ / math.sqrt(2 * me_ * e_ * 100.0); assert abs(lam_e - 1.23e-10) < 0.01e-10
lam_b = h_ / (0.145 * 40.0); assert lam_b < 1e-33
En = lambda n: -13.6 / n ** 2
dE32 = En(3) - En(2); lam32 = h_ * c_ / (dE32 * e_); dE21 = En(2) - En(1); lam21 = h_ * c_ / (dE21 * e_)
assert abs(dE32 - 1.889) < 0.001 and abs(lam32 * 1e9 - 658) < 1 and abs(dE21 - 10.2) < 1e-9 and abs(lam21 * 1e9 - 121.8) < 0.5, (lam32, lam21)
def levels_fig():
    y = lambda E: 30 + 13.4 * (-E)
    it = [LINE(80, y(0), 280, y(0), width=2), T(70, y(0) + 4, "n = ∞  0", 12, anchor="end"),
          LINE(80, y(En(4)), 280, y(En(4)), width=2), T(290, y(En(4)) + 4, "n = 4  −0.85", 12, anchor="start"),
          LINE(80, y(En(3)), 280, y(En(3)), width=2), T(70, y(En(3)) + 4, "n = 3  −1.51", 12, anchor="end"),
          LINE(80, y(En(2)), 280, y(En(2)), width=2), T(290, y(En(2)) + 4, "n = 2  −3.40", 12, anchor="start"),
          LINE(80, y(En(1)), 280, y(En(1)), width=2), T(290, y(En(1)) + 4, "n = 1  −13.6", 12, anchor="start"),
          LINE(150, y(En(3)), 150, y(En(2)) - 2, color="red", width=3, arrow="end"), T(142, 110, "656 nm", 12, anchor="end", color="red"),
          LINE(230, y(En(2)), 230, y(En(1)) - 2, color="blue", width=3, arrow="end"), T(222, 150, "122 nm", 12, anchor="end", color="blue"),
          T(40, 16, "E / eV (hydrogen)", 12, anchor="start")]
    return shapes(it, 400, 240)
lesson(ch, "duality", "Wave–particle duality and line spectra",
  ["Use the de Broglie relation λ = h/p and describe electron diffraction as evidence for matter waves",
   "Explain line spectra using energy levels and use hf = E₂ − E₁ with the hydrogen levels E_n = −13.6/n² eV",
   "Distinguish emission and absorption spectra and describe their uses"],
  [("definition", "Matter waves",
    "Particles show wave behaviour: the **de Broglie wavelength** of a particle of momentum p = mv is **λ = h/p**. The diffraction of electrons by a thin crystal or graphite shows rings like those of X-rays: this is evidence for the wave nature of matter.\n\n"
    "For an electron accelerated from rest through V volts: p = √(2m_eeV), so λ = h/√(2m_eeV). For everyday objects λ is far too small to observe."),
   ("formule", "Energy levels",
    "In an atom the electrons occupy discrete **energy levels**. In hydrogen the energy of level n is **E_n = −13.6/n² eV** (n = 1 is the ground state; ionisation energy 13.6 eV).\n\n"
    "When an electron falls from a higher level to a lower one, one photon is emitted: **hf = E_upper − E_lower**, so λ = hc/ΔE. Absorption of a photon of exactly this energy raises the electron to the higher level.",
    r"\lambda=\frac{h}{p}\qquad E_n=-\frac{13.6}{n^{2}}\ \text{eV}\qquad hf=E_2-E_1"),
   ("retenir", "Spectra",
    "A hot low-pressure gas emits an **emission line spectrum**: bright lines of definite wavelengths, characteristic of the element. White light passing through a cool gas gives an **absorption spectrum** (dark lines at the same wavelengths). Line spectra prove that atomic energy levels are discrete and are used to identify elements (for example in stars). A continuous spectrum comes from hot solids."),
   ("pieges", "Common mistakes",
    "- Taking E_n as positive: bound-state energies are negative.\n- Using ΔE in eV in λ = hc/ΔE without converting to joules.\n- Saying electrons are 'either waves or particles': both models are needed.\n- Thinking the photon energy depends on which level the electron starts from alone: it depends on the difference between two levels.")],
  [("Example 1 — Electron wavelength", "Find the de Broglie wavelength of an electron accelerated from rest through 100 V.",
    ["p = √(2meV) = √(2 × 9.11 × 10⁻³¹ × 1.60 × 10⁻¹⁹ × 100) = 5.40 × 10⁻²⁴ kg m/s.", "λ = h/p = 6.63 × 10⁻³⁴ ÷ 5.40 × 10⁻²⁴ = 1.23 × 10⁻¹⁰ m, about the size of an atom, so crystals diffract it."], "λ = 1.23 × 10⁻¹⁰ m"),
   ("Example 2 — Hydrogen line", "An electron in hydrogen falls from n = 3 to n = 2. Find the photon energy and wavelength.",
    ["E₃ = −13.6/9 = −1.51 eV; E₂ = −13.6/4 = −3.40 eV; ΔE = 1.89 eV = 3.02 × 10⁻¹⁹ J.", "λ = hc/ΔE = 6.63 × 10⁻³⁴ × 3.00 × 10⁸ ÷ 3.02 × 10⁻¹⁹ = 6.58 × 10⁻⁷ m = 658 nm (red; the measured line is 656 nm)."], "ΔE = 1.89 eV; λ ≈ 658 nm (red)")],
  [N("A 0.145 kg ball moves at 40 m/s. Find its de Broglie wavelength in units of 10⁻³⁴ m (3 s.f.).", sg(lam_b / 1e-34, 3), "p = 0.145 × 40 = 5.8 kg m/s; λ = 6.63 × 10⁻³⁴ ÷ 5.8 = 1.14 × 10⁻³⁴ m.", 0.01, "× 10⁻³⁴ m"),
   N("Find the energy of the n = 2 level of hydrogen (eV).", En(2), "E₂ = −13.6/4 = −3.40 eV.", 0.005, "eV"),
   TF("True or false: the electrons in a hydrogen atom can have any energy.", False, "Only discrete energy levels are allowed (−13.6/n² eV).")],
  [N("Find the wavelength (nm) of the photon emitted when hydrogen falls from n = 2 to n = 1.", sg(lam21 * 1e9, 3), "ΔE = 10.2 eV = 1.63 × 10⁻¹⁸ J; λ = hc/ΔE = 1.22 × 10⁻⁷ m = 122 nm (ultraviolet).", 1, "nm", difficulty=2),
   M("A line in an absorption spectrum of a star shows that", "a cool gas in the star's atmosphere absorbs light of that wavelength", ["the star is hot", "the star is moving", "the star has no light"], "Dark lines mark photon energies matching transitions in the intervening gas.")],
  P("Use h = 6.63 × 10⁻³⁴ J s, c = 3.00 × 10⁸ m/s, e = 1.60 × 10⁻¹⁹ C, mₑ = 9.11 × 10⁻³¹ kg. Hydrogen energy levels are E_n = −13.6/n² eV.",
    [pn("Energy released (eV, 3 s.f.) when an electron falls from n = 3 to n = 1.", sg(En(3) - En(1), 3), "ΔE = −1.51 − (−13.6) = 12.1 eV.", 0.02, "eV", 1),
     pn("Wavelength of that photon (nm, 3 s.f.).", sg(h_ * c_ / ((En(3) - En(1)) * e_) * 1e9, 3), "λ = hc/ΔE = 6.63 × 10⁻³⁴ × 3.00 × 10⁸ ÷ (12.09 × 1.60 × 10⁻¹⁹) = 1.03 × 10⁻⁷ m = 103 nm.", 1, "nm", 2),
     pn("Electron wavelength (10⁻¹⁰ m, 3 s.f.) for electrons accelerated through 100 V.", sg(lam_e / 1e-10, 3), "λ = h/√(2meV) = 1.23 × 10⁻¹⁰ m.", 0.01, "× 10⁻¹⁰ m", 1),
     pm("Electron diffraction is observed because the electron wavelength is", "comparable with the spacing of atoms in a crystal", ["much larger than atoms", "equal to that of visible light", "zero"], "Diffraction needs λ of the same order as the gap spacing.", 1)], figure=levels_fig()),
  [M("The de Broglie wavelength of a particle is", "h/p", ["p/h", "hp", "h/E"], "λ = h/p."),
   M("In hydrogen, the energy of the ground state is", "−13.6 eV", ["+13.6 eV", "0 eV", "−3.40 eV"], "E₁ = −13.6/1² eV."),
   M("The photon emitted when an electron falls between two levels has energy", "equal to the difference between the levels", ["equal to the lower level", "equal to the upper level", "zero"], "hf = E₂ − E₁."),
   M("A continuous spectrum is produced by", "a hot solid", ["a hot low-pressure gas", "a cool gas", "a discharge tube"], "Closely spaced energies in solids give all wavelengths."),
   M("Electron diffraction provides evidence that electrons", "behave as waves", ["are charged", "have mass", "are smaller than atoms"], "Diffraction is a wave property.")],
  (levels_fig(), "Energy levels of hydrogen and two transitions.", "A ladder of horizontal energy levels from n = 1 at −13.6 electron-volts to n = infinity at zero with two downward arrows labelled 656 nm and 122 nm."),
  notes=[DATA, "Ionisation energy and level formula are given for hydrogen only. Lasers (stimulated emission) and X-ray spectra are not covered; check the syllabus.", "The calculated 658 nm differs from the measured 656 nm because of rounding of constants."], minutes=35, prereq=["gceal-physics-photoelectric"])

# =============================================================== 5. NUCLEAR PHYSICS
ch = p.chapter("ch-nuclear", "Atomic and nuclear physics", SYL % "nuclear physics")
T8 = 8.0 * 86400; lam8 = math.log(2) / T8; A24 = 6.4e5 / 8; N0_8 = 6.4e5 / lam8
assert abs(lam8 - 1.003e-6) < 0.001e-6 and A24 == 8.0e4 and abs(N0_8 - 6.38e11) < 0.02e11
lamC = math.log(2) / 5730; t_C = -math.log(0.60) / lamC; assert abs(t_C - 4223) < 5, t_C
lamX = math.log(2) / (6.0 * 3600); AX = 3.2e6 / 2 ** 4; NX = 3.2e6 / lamX; assert abs(AX - 2.0e5) < 1e-6 and abs(NX - 9.97e10) < 0.03e10
def decay_fig():
    return plot(0, 20, 0, 100, curves=[("100*exp(-0.6931*x/5)", "blue", "N")], points=[(5, 50, "T½", "red"), (10, 25, None, "red"), (15, 12.5, None, "red")], grid=5, xlabel="t / days", ylabel="% of N₀", w=380, h=250)
lesson(ch, "radioactivity", "Radioactivity and the decay law",
  ["Describe the nature, ionising power and penetration of α, β and γ radiation and write balanced nuclear equations",
   "Use the decay law A = λN, N = N₀e^(−λt) and t½ = ln 2/λ",
   "Apply half-life ideas to radiocarbon dating, medical tracers and safety"],
  [("definition", "Nuclear radiation",
    "A nuclide ᴬ_Z X has Z protons and A − Z neutrons. Unstable nuclei decay **randomly** (the decay of any one nucleus cannot be predicted) and **spontaneously** (unaffected by temperature or chemistry).\n\n"
    "**α**: helium nucleus ⁴₂He, strongly ionising, stopped by paper. **β⁻**: fast electron (from n → p + e⁻ + antineutrino), stopped by a few mm of aluminium. **γ**: photon, weakly ionising, reduced by thick lead. In equations the **total A and Z are conserved**."),
   ("formule", "The decay law",
    "The **activity** A = −dN/dt = **λN**, where λ is the **decay constant** (s⁻¹); the unit of activity is the becquerel (Bq = 1 s⁻¹). Solving: **N = N₀e^(−λt)** and A = A₀e^(−λt).\n\nThe **half-life** t½ is the time for N (or A) to halve: **t½ = ln 2/λ = 0.693/λ**. After n half-lives the fraction left is (½)ⁿ.",
    r"A=\lambda N\qquad N=N_0e^{-\lambda t}\qquad t_{1/2}=\frac{\ln 2}{\lambda}"),
   ("retenir", "Applications and safety",
    "**Radiocarbon dating**: living things take in carbon-14 from the atmosphere at a steady proportion; after death it decays (half-life about 5730 years) so the remaining activity gives the age. **Tracers** with short half-lives are used in medicine. **Safety**: keep exposure short, distance large and use shielding; handle sources with tongs; stored in lead-lined containers. A Geiger counter's count rate must be corrected for **background radiation**."),
   ("pieges", "Common mistakes",
    "- Using the half-life in years with λ in s⁻¹ without converting.\n- Saying that after two half-lives all the nuclei have decayed (¼ remain).\n- Forgetting to subtract the background count rate.\n- Thinking radioactivity can be switched off or changed by heating: it cannot.")],
  [("Example 1 — Using half-life", "A source has half-life 8.0 days and an initial activity of 6.4 × 10⁵ Bq. Find the decay constant, the activity after 24 days, and the initial number of radioactive nuclei.",
    ["λ = ln 2/t½ = 0.693 ÷ (8.0 × 86 400 s) = 1.00 × 10⁻⁶ s⁻¹.", "24 days = 3 half-lives, so A = 6.4 × 10⁵ × (½)³ = 8.0 × 10⁴ Bq.", "N₀ = A₀/λ = 6.4 × 10⁵ ÷ 1.00 × 10⁻⁶ = 6.4 × 10¹¹ nuclei."], "λ = 1.0 × 10⁻⁶ s⁻¹; A = 8.0 × 10⁴ Bq; N₀ = 6.4 × 10¹¹"),
   ("Example 2 — Carbon dating", "A wooden object has 60 % of the carbon-14 activity per gram of living wood. Estimate its age (half-life 5730 years).",
    ["λ = 0.693/5730 = 1.21 × 10⁻⁴ yr⁻¹.", "A/A₀ = e^(−λt) = 0.60, so t = −ln(0.60)/λ = 0.511 ÷ 1.21 × 10⁻⁴ = 4.2 × 10³ years."], "About 4200 years")],
  [N("The half-life of a nuclide is 6.0 h. What fraction remains after 24 h (give a decimal)?", 1 / 16, "24 h = 4 half-lives, so (½)⁴ = 1/16 = 0.0625.", 0.0005, None),
   N("A source of decay constant 2.0 × 10⁻³ s⁻¹ contains 5.0 × 10⁶ nuclei. Find its activity (Bq).", 2.0e-3 * 5.0e6, "A = λN = 2.0 × 10⁻³ × 5.0 × 10⁶ = 1.0 × 10⁴ Bq.", 1, "Bq"),
   TF("True or false: heating a radioactive sample increases its rate of decay.", False, "Decay is spontaneous and independent of temperature and chemical state.")],
  [N("Radium-226 has a decay constant of 1.4 × 10⁻¹¹ s⁻¹. Find its half-life in years (3 s.f., 1 year = 3.15 × 10⁷ s).", sg(math.log(2) / 1.4e-11 / 3.15e7, 3), "t½ = 0.693 ÷ 1.4 × 10⁻¹¹ = 4.95 × 10¹⁰ s = 1.57 × 10³ years.", 5, "years", difficulty=2),
   N("A Geiger counter reads 340 counts per minute near a source. The background is 20 counts per minute. Find the corrected count rate (counts per minute).", 320, "340 − 20 = 320 counts per minute.", 0, "counts/min")],
  P("A radioisotope used as a tracer has a half-life of 6.0 hours. A sample has an initial activity of 3.2 × 10⁶ Bq.",
    [pn("Decay constant (10⁻⁵ s⁻¹, 3 s.f.).", sg(lamX / 1e-5, 3), "λ = ln 2/(6.0 × 3600) = 3.21 × 10⁻⁵ s⁻¹.", 0.02, "× 10⁻⁵ s⁻¹", 1),
     pn("Activity after 24 hours (10⁵ Bq).", AX / 1e5, "24 h = 4 half-lives: 3.2 × 10⁶/16 = 2.0 × 10⁵ Bq.", 0.01, "× 10⁵ Bq", 1),
     pn("Initial number of undecayed nuclei (10¹⁰, 3 s.f.).", sg(NX / 1e10, 3), "N₀ = A₀/λ = 3.2 × 10⁶ ÷ 3.21 × 10⁻⁵ = 9.97 × 10¹⁰.", 0.05, "× 10¹⁰", 2),
     pm("Why is a short half-life useful for a medical tracer?", "the radiation dose to the patient soon becomes small", ["the isotope is cheaper", "it emits no radiation", "it can be recycled"], "A short half-life limits exposure time.", 1)]),
  [M("Which radiation is the most strongly ionising?", "alpha", ["beta", "gamma", "all equally"], "α particles are heavy and doubly charged."),
   M("In β⁻ decay the proton number of the daughter nucleus", "increases by 1", ["decreases by 1", "decreases by 2", "stays the same"], "A neutron changes into a proton."),
   M("The unit becquerel is the same as", "s⁻¹", ["s", "J s⁻¹", "C s⁻¹"], "1 Bq = one decay per second."),
   M("The half-life of a source is 10 min. The activity falls to 1/8 of its initial value after", "30 min", ["80 min", "60 min", "15 min"], "Three half-lives."),
   M("Decay of an individual nucleus is", "random", ["predictable", "dependent on pressure", "dependent on temperature"], "Only probabilities can be given.")],
  (decay_fig(), "Radioactive decay: the number of nuclei halves every half-life (here 5 days).", "A decaying exponential curve from 100 per cent with red points at 50 per cent after 5 days, 25 per cent after 10 days and 12.5 per cent after 15 days."),
  notes=["Half-life of carbon-14: 5730 years (often rounded to 5700 or 5600 in textbooks) — check the board's value.", "β⁺ decay, electron capture and the neutrino are only mentioned; check the syllabus."], minutes=35, prereq=["gceal-physics-duality"])

# ---- 5.2 binding energy
mp, mn, mHe4 = 1.007276, 1.008665, 4.001506; uMeV = 931.5
dm = 2 * (mp + mn) - mHe4; BE = dm * uMeV; BEn = BE / 4
assert abs(dm - 0.030376) < 1e-6 and abs(BE - 28.30) < 0.02 and abs(BEn - 7.07) < 0.01
mD, mT, mHe, mn_ = 2.014102, 3.016049, 4.002603, 1.008665; dmf = (mD + mT) - (mHe + mn_); Ef = dmf * uMeV
assert abs(dmf - 0.018883) < 1e-6 and abs(Ef - 17.59) < 0.02
mRa, mRn = 226.025410, 222.017578; Q_a = (mRa - mRn - mHe) * uMeV; assert abs(Q_a - 4.87) < 0.02, Q_a
NU = 1000 / 235 * 6.02e23; E_kg = NU * 200 * 1.60e-13; assert abs(E_kg - 8.2e13) < 0.1e13
def be_fig():
    return bars([("²H", 1.11, "orange"), ("⁴He", 7.07, "orange"), ("¹²C", 7.68, "blue"), ("⁵⁶Fe", 8.79, "green"), ("²³⁵U", 7.59, "blue")], unit="MeV per nucleon", w=400, h=250)
lesson(ch, "binding", "Mass defect, binding energy, fission and fusion",
  ["Define mass defect and binding energy and use E = Δmc² with 1 u = 931.5 MeV",
   "Describe the binding energy per nucleon curve and explain why fission and fusion release energy",
   "Calculate energy released in nuclear reactions and decays"],
  [("definition", "Mass defect and binding energy",
    "The mass of a nucleus is **less** than the total mass of its separate nucleons. This **mass defect** is Δm = Zm_p + (A − Z)m_n − m_nucleus. The **binding energy** is the energy needed to separate the nucleus completely into nucleons: **E = Δmc²**.\n\n"
    "Useful: 1 u = 1.66 × 10⁻²⁷ kg and **1 u ≡ 931.5 MeV** (using E = mc²)."),
   ("formule", "Binding energy per nucleon",
    "The **binding energy per nucleon** (E_b/A) measures nuclear stability. It rises steeply for light nuclei, has a broad maximum (about 8.8 MeV) near iron-56, and falls slowly for heavy nuclei.\n\n"
    "**Fission** of a heavy nucleus (e.g. U-235 splitting after absorbing a neutron into two medium nuclei and 2–3 neutrons) and **fusion** of light nuclei (e.g. deuterium + tritium → helium-4 + neutron) both move to higher E_b/A, so energy is released.",
    r"E=\Delta m\,c^{2}\qquad 1\,\text{u}\equiv 931.5\ \text{MeV}"),
   ("retenir", "Reactions and reactors",
    "In a reaction, energy released Q = (total mass before − total mass after) × 931.5 MeV/u. A **chain reaction** occurs when the neutrons released cause further fission; in a reactor a **moderator** slows neutrons, **control rods** absorb them and a coolant removes heat. Fusion needs very high temperatures and pressures to overcome electrostatic repulsion (as in stars). One U-235 fission gives about 200 MeV."),
   ("pieges", "Common mistakes",
    "- Using the mass of the neutral atom with nuclear masses without care (the electron masses cancel only if you use atomic masses consistently).\n- Forgetting to convert u to MeV (× 931.5) or MeV to joules (× 1.60 × 10⁻¹³ J).\n- Confusing the binding energy with the energy released: binding energy is **needed** to break up the nucleus.\n- Saying the products have less mass because mass is 'destroyed': it is converted to energy.")],
  [("Example 1 — Helium-4", "Find the binding energy and binding energy per nucleon of ⁴He. (m_p = 1.007276 u, m_n = 1.008665 u, nuclear mass of ⁴He = 4.001506 u.)",
    ["Δm = 2(1.007276 + 1.008665) − 4.001506 = 4.031882 − 4.001506 = 0.030376 u.", "E = 0.030376 × 931.5 = 28.3 MeV.", "Per nucleon: 28.3/4 = 7.07 MeV."], "28.3 MeV in total; 7.07 MeV per nucleon"),
   ("Example 2 — Fusion", "Find the energy released in ²H + ³H → ⁴He + n (atomic masses: ²H 2.014102 u, ³H 3.016049 u, ⁴He 4.002603 u, n 1.008665 u).",
    ["Mass before = 2.014102 + 3.016049 = 5.030151 u.", "Mass after = 4.002603 + 1.008665 = 5.011268 u.", "Δm = 0.018883 u, so Q = 0.018883 × 931.5 = 17.6 MeV."], "Q = 17.6 MeV")],
  [N("A mass defect of 0.0300 u corresponds to what energy (MeV, 3 s.f.)?", sg(0.0300 * uMeV, 3), "E = 0.0300 × 931.5 = 27.9 MeV.", 0.1, "MeV"),
   N("A nucleus has binding energy 120 MeV and 16 nucleons. Find the binding energy per nucleon (MeV).", 7.5, "120/16 = 7.5 MeV.", 0.01, "MeV"),
   TF("True or false: fusion of two light nuclei releases energy because the product has a higher binding energy per nucleon.", True, "Moving towards the peak of the curve releases energy.")],
  [N("Radium-226 (226.025410 u) decays by α emission to radon-222 (222.017578 u); α mass = 4.002603 u. Find the energy released (MeV, 3 s.f.).", sg(Q_a, 3), "Δm = 226.025410 − (222.017578 + 4.002603) = 0.005229 u; Q = 0.005229 × 931.5 = 4.87 MeV.", 0.03, "MeV", difficulty=2),
   N("About 200 MeV is released per fission of U-235. Find the energy from 1.00 kg (10¹³ J, 2 s.f.; N_A = 6.02 × 10²³).", sg(E_kg / 1e13, 2), "N = (1000/235) × 6.02 × 10²³ = 2.56 × 10²⁴ nuclei; E = 2.56 × 10²⁴ × 200 × 1.60 × 10⁻¹³ = 8.2 × 10¹³ J.", 0.2, "× 10¹³ J")],
  P("Use 1 u = 931.5 MeV. A helium-4 nucleus (nuclear mass 4.001506 u) is made of 2 protons (1.007276 u each) and 2 neutrons (1.008665 u each).",
    [pn("Mass defect (in units of 10⁻² u, 3 s.f.).", sg(dm * 100, 3), "Δm = 4.031882 − 4.001506 = 0.03038 u = 3.04 × 10⁻² u.", 0.01, "× 10⁻² u", 2),
     pn("Binding energy (MeV, 3 s.f.).", sg(BE, 3), "E = 0.030376 × 931.5 = 28.3 MeV.", 0.1, "MeV", 1),
     pn("Binding energy per nucleon (MeV, 3 s.f.).", sg(BEn, 3), "28.3/4 = 7.07 MeV per nucleon.", 0.05, "MeV", 1),
     pm("Which nucleus is more stable according to the curve?", "iron-56 (8.8 MeV per nucleon)", ["helium-4 (7.07 MeV per nucleon)", "hydrogen-2 (1.1 MeV per nucleon)", "all equally stable"], "Higher binding energy per nucleon means greater stability.", 1)], figure=be_fig()),
  [M("1 atomic mass unit is equivalent to about", "931.5 MeV", ["93.15 MeV", "9315 MeV", "1.66 MeV"], "E = (1.66 × 10⁻²⁷ × 9.00 × 10¹⁶) J = 1.49 × 10⁻¹⁰ J = 931.5 MeV."),
   M("The most stable nuclei have", "the highest binding energy per nucleon", ["the highest mass", "the lowest number of protons", "the lowest binding energy"], "Stability increases with E_b/A."),
   M("In nuclear fission, the neutrons released can cause", "a chain reaction", ["fusion", "alpha decay only", "no further reaction"], "Each fission releases neutrons that induce further fissions."),
   M("Control rods in a reactor are made of a material that", "absorbs neutrons", ["moderates neutrons", "produces neutrons", "cools the core"], "They control the rate of the chain reaction."),
   M("In a nuclear reaction the total mass of the products is", "less than the mass of the reactants when energy is released", ["always greater", "always equal", "meaningless"], "The mass difference appears as released energy.")],
  (be_fig(), "Binding energy per nucleon for selected nuclei: the maximum is near iron-56.", "A bar chart of binding energy per nucleon for hydrogen-2, helium-4, carbon-12, iron-56 and uranium-235, with iron the tallest."),
  notes=["Nuclear and atomic masses are textbook values given to six decimal places; check them against the board's data sheet.", "Binding energy values per nucleon in the bar chart are standard rounded values (²H 1.11, ⁴He 7.07, ¹²C 7.68, ⁵⁶Fe 8.79, ²³⁵U 7.59 MeV)."], minutes=35, prereq=["gceal-physics-radioactivity"])

# =============================================================== 6. THERMODYNAMICS, ASTRONOMY, ELECTRONICS, PRACTICAL
ch = p.chapter("ch-thermo", "Thermodynamics, astronomy and electronics", SYL % "thermodynamics, cosmology, semiconductors")
R_ = 8.31
W1 = 2.0e5 * (5.0e-3 - 3.0e-3); dU1 = 1200 - W1; assert abs(W1 - 400) < 1e-9 and abs(dU1 - 800) < 1e-9
QV = 1.5 * R_ * 100; QP = 2.5 * R_ * 100; WP = R_ * 100; assert abs(QV - 1246.5) < 1e-9 and abs(QP - 2077.5) < 1e-9 and abs(WP - 831) < 1e-9
Wiso = R_ * 300 * math.log(2); assert abs(Wiso - 1728) < 1
gam = 5 / 3; p2a = 1.0e5 * 2 ** gam; assert abs(p2a - 3.175e5) < 0.01e5
eta = 1 - 500 / 800; etaC = 1 - 300 / 600; assert eta == 0.375 and etaC == 0.5
def pv_fig():
    return plot(1, 6, 0, 20, curves=[("12/x", "blue", "isothermal"), ("6*(2/x)^1.667", "red", "adiabatic")], points=[(2, 6, "start", "ink")], grid=1, xlabel="V", ylabel="p", w=380, h=250)
lesson(ch, "thermo", "The first law of thermodynamics and heat engines",
  ["State the first law Q = ΔU + W and calculate work done by a gas W = pΔV",
   "Describe isothermal, isobaric, isochoric and adiabatic changes of an ideal gas",
   "Explain the efficiency of a heat engine and the Carnot limit η = 1 − T_c/T_h"],
  [("definition", "First law",
    "The **first law of thermodynamics** (conservation of energy): **Q = ΔU + W**, where Q is the heat supplied to the gas, ΔU the increase in its internal energy and W the work done **by** the gas. For a gas at constant pressure, **W = pΔV**; in general W is the area under the p–V graph.\n\n"
    "For an ideal monatomic gas ΔU = (3/2)nRΔT (internal energy depends on T only)."),
   ("formule", "Four special changes",
    "- **Isothermal** (T constant): ΔU = 0, so Q = W; W = nRT ln(V₂/V₁); pV = constant.\n- **Isobaric** (p constant): W = pΔV; Q = (5/2)nRΔT for a monatomic gas.\n- **Isochoric** (V constant): W = 0, so Q = ΔU = (3/2)nRΔT.\n- **Adiabatic** (Q = 0): ΔU = −W; pV^γ = constant with γ = 5/3 for a monatomic gas; the gas cools when it expands.",
    r"Q=\Delta U+W\qquad W=p\Delta V\qquad pV^{\gamma}=\text{constant}"),
   ("retenir", "Heat engines",
    "A **heat engine** takes heat Q_h from a hot reservoir, does work W and rejects heat Q_c to a cold reservoir: **η = W/Q_h = 1 − Q_c/Q_h**. No engine can convert all the heat to work (**second law**). The maximum (Carnot) efficiency for reservoirs at absolute temperatures T_h and T_c is **η = 1 − T_c/T_h**; real engines are less efficient."),
   ("pieges", "Common mistakes",
    "- Mixing the sign conventions: here W is work done **by** the gas (positive when the gas expands).\n- Using °C in the Carnot efficiency: use kelvin.\n- Thinking the temperature stays constant in an adiabatic change.\n- Forgetting to convert dm³ or cm³ to m³ in W = pΔV.")],
  [("Example 1 — Expansion at constant pressure", "A gas at 2.0 × 10⁵ Pa expands from 3.0 × 10⁻³ m³ to 5.0 × 10⁻³ m³ while absorbing 1200 J of heat. Find the work done and the change in internal energy.",
    ["W = pΔV = 2.0 × 10⁵ × (5.0 − 3.0) × 10⁻³ = 400 J.", "ΔU = Q − W = 1200 − 400 = 800 J (the internal energy increases, so the gas gets hotter)."], "W = 400 J; ΔU = 800 J"),
   ("Example 2 — Heating one mole", "One mole of monatomic gas is heated by 100 K, (a) at constant volume and (b) at constant pressure (R = 8.31 J mol⁻¹ K⁻¹). Find Q in each case and the work in (b).",
    ["(a) Q = ΔU = (3/2)RΔT = 1.5 × 8.31 × 100 = 1250 J (1246.5).", "(b) W = pΔV = RΔT = 831 J; ΔU is the same, 1246.5 J.", "Q = ΔU + W = 1246.5 + 831 = 2077.5 J: more heat is needed at constant pressure."], "(a) 1.25 kJ; (b) 2.08 kJ with W = 831 J")],
  [N("A gas expands at a constant pressure of 1.5 × 10⁵ Pa by 4.0 × 10⁻³ m³. Find the work done by the gas (J).", 1.5e5 * 4.0e-3, "W = pΔV = 1.5 × 10⁵ × 4.0 × 10⁻³ = 600 J.", 1, "J"),
   N("An engine takes 800 J from the hot reservoir and rejects 500 J. Find the efficiency (%).", eta * 100, "W = 300 J; η = 300/800 = 0.375 = 37.5 %.", 0.1, "%"),
   TF("True or false: in an isothermal expansion of an ideal gas, the heat supplied equals the work done by the gas.", True, "ΔU = 0 as T is constant, so Q = W.")],
  [N("Find the maximum (Carnot) efficiency of an engine working between 600 K and 300 K (%).", etaC * 100, "η = 1 − 300/600 = 0.50 = 50 %.", 0.1, "%", difficulty=2),
   N("One mole of an ideal gas at 300 K expands isothermally to twice its volume. Find the work done by the gas (J, 3 s.f.; R = 8.31).", sg(Wiso, 3), "W = nRT ln(V₂/V₁) = 8.31 × 300 × ln 2 = 1730 J.", 5, "J")],
  P("An ideal monatomic gas (n = 1.00 mol, R = 8.31 J mol⁻¹ K⁻¹) is heated at constant pressure from 300 K to 400 K.",
    [pn("Increase in internal energy (J, 4 s.f.).", QV, "ΔU = (3/2)RΔT = 1.5 × 8.31 × 100 = 1246.5 J.", 1, "J", 1),
     pn("Work done by the gas (J).", WP, "pΔV = nRΔT = 8.31 × 100 = 831 J.", 1, "J", 1),
     pn("Heat supplied (J, 4 s.f.).", QP, "Q = ΔU + W = 1246.5 + 831 = 2077.5 J.", 1, "J", 1),
     pm("This needs more heat than heating at constant volume because", "some of the heat goes into work done by the expanding gas", ["the gas gets hotter", "the pressure falls", "the gas has more molecules"], "At constant volume W = 0 so all of Q goes into ΔU.", 1)]),
  [M("In an adiabatic expansion of an ideal gas, the temperature", "falls", ["rises", "stays constant", "is undefined"], "Q = 0 and the gas does work, so ΔU < 0."),
   M("The first law of thermodynamics is a statement of", "conservation of energy", ["conservation of momentum", "conservation of charge", "the second law"], "Q = ΔU + W."),
   M("The work done by a gas is represented by", "the area under the p–V graph", ["the gradient of the p–V graph", "the temperature", "the pressure alone"], "W = ∫p dV."),
   M("An engine's efficiency of 100 % is", "impossible according to the second law", ["common", "possible if there is no friction", "possible at high temperatures"], "Some heat must be rejected to a cold reservoir."),
   M("In an isochoric change, W =", "0", ["pΔV", "Q", "nRT"], "ΔV = 0.")],
  (pv_fig(), "p–V graph: an isothermal curve (pV constant) and a steeper adiabatic curve from the same starting point.", "Two curves of pressure against volume starting at the same point, the adiabatic one falling more steeply than the isothermal one."),
  notes=["Convention: W is the work done BY the gas (Q = ΔU + W); some boards use ΔU = Q + W (work done ON the gas) — check the board's convention.", "Carnot efficiency, entropy and γ are probably beyond the core syllabus; flagged for the teacher.", R_ and "R = 8.31 J mol⁻¹ K⁻¹."], minutes=35, prereq=[])

# ---- astronomy / cosmology
Lsun, dSE, sigma = 3.8e26, 1.5e11, 5.67e-8
F_s = Lsun / (4 * math.pi * dSE ** 2); T_sun = 2.90e-3 / 500e-9; R_sun = math.sqrt(Lsun / (4 * math.pi * sigma * T_sun ** 4))
assert abs(F_s - 1344) < 5 and abs(T_sun - 5800) < 1 and abs(R_sun - 6.9e8) < 0.2e8, (F_s, R_sun)
z_ = 0.010; v_gal = z_ * 3.00e5; d_gal = v_gal / 70; H0_inv_yr = 3.086e19 / 70 / 3.156e7
assert abs(v_gal - 3000) < 1e-9 and abs(d_gal - 42.86) < 0.01 and abs(H0_inv_yr - 1.397e10) < 0.01e10
def hubble_fig():
    pts = [(20, 1500, None, "red"), (40, 2700, None, "red"), (60, 4300, None, "red"), (80, 5500, None, "red"), (100, 7100, None, "red")]
    return plot(0, 100, 0, 8000, curves=[("70*x", "blue", None)], points=pts, grid=20, xlabel="distance / Mpc", ylabel="v / km s⁻¹", w=400, h=250)
lesson(ch, "cosmos", "Stars, redshift and the expanding Universe (to be checked against the syllabus)",
  ["Use the inverse-square law for stellar flux and the Stefan–Boltzmann and Wien laws",
   "Explain redshift, use z = Δλ/λ ≈ v/c and state Hubble's law v = H₀d",
   "Describe the evidence for the Big Bang and the expanding Universe"],
  [("definition", "Stars as black bodies",
    "A star of luminosity (power output) L at distance d gives a flux (intensity) **F = L/4πd²** (inverse-square law). A hot body radiates a spectrum with a peak wavelength given by **Wien's law: λ_max T = 2.90 × 10⁻³ m K**; the total power radiated by a star of radius R and surface temperature T is **L = 4πR²σT⁴** (Stefan–Boltzmann; σ = 5.67 × 10⁻⁸ W m⁻² K⁻⁴)."),
   ("formule", "Redshift and Hubble's law",
    "Light from receding sources is shifted to longer wavelengths (**Doppler redshift**): z = Δλ/λ ≈ v/c for v ≪ c. Distant galaxies show redshifts proportional to their distance: **Hubble's law v = H₀d** with H₀ ≈ 70 km s⁻¹ Mpc⁻¹ (1 Mpc ≈ 3.09 × 10¹⁹ km). This means that space itself is expanding.\n\nThe quantity 1/H₀ gives an estimate of the age of the Universe (about 1.4 × 10¹⁰ years).",
    r"F=\frac{L}{4\pi d^{2}}\quad L=4\pi R^{2}\sigma T^{4}\quad v=H_0d"),
   ("retenir", "Evidence for the Big Bang",
    "The Universe began in a hot, dense state about 14 billion years ago and has been expanding and cooling. Evidence: (1) the **redshift of galaxies**; (2) the **cosmic microwave background** radiation, a nearly uniform glow at about 2.7 K; (3) the observed proportions of hydrogen and helium. Values such as H₀ are still being refined and different methods give slightly different values."),
   ("pieges", "Common mistakes",
    "- Forgetting to convert km and Mpc to a common unit when finding the age (1/H₀).\n- Using °C for the temperature in Wien's or Stefan–Boltzmann law: use kelvin.\n- Thinking galaxies move away from a centre: every observer sees others receding.\n- Assuming the redshift is caused by the light being 'tired': it is due to the expansion of space.")],
  [("Example 1 — The Sun", "The Sun's luminosity is 3.8 × 10²⁶ W. (a) Find the flux at the Earth (1.5 × 10¹¹ m). (b) The peak wavelength of sunlight is about 500 nm: find the surface temperature, then the radius of the Sun.",
    ["(a) F = L/4πd² = 3.8 × 10²⁶ ÷ (4π × (1.5 × 10¹¹)²) = 1.34 × 10³ W m⁻².", "(b) T = 2.90 × 10⁻³ ÷ 5.0 × 10⁻⁷ = 5800 K.", "R = √(L/(4πσT⁴)) = √(3.8 × 10²⁶ ÷ (4π × 5.67 × 10⁻⁸ × 5800⁴)) = 6.9 × 10⁸ m."], "1.34 × 10³ W/m²; 5800 K; 6.9 × 10⁸ m"),
   ("Example 2 — A distant galaxy", "A galaxy's light has Δλ/λ = 0.010. Find its recession speed and its distance (c = 3.00 × 10⁵ km/s, H₀ = 70 km s⁻¹ Mpc⁻¹).",
    ["v = zc = 0.010 × 3.00 × 10⁵ = 3.0 × 10³ km/s.", "d = v/H₀ = 3000/70 = 43 Mpc."], "v = 3.0 × 10³ km/s; d ≈ 43 Mpc")],
  [N("A star has luminosity 4.0 × 10²⁷ W. Find the flux at 1.0 × 10¹⁸ m (10⁻¹⁰ W m⁻², 2 s.f.).", sg(4.0e27 / (4 * math.pi * 1.0e36) / 1e-10, 2), "F = L/4πd² = 4.0 × 10²⁷ ÷ (4π × 10³⁶) = 3.2 × 10⁻¹⁰ W m⁻².", 0.05, "× 10⁻¹⁰ W/m²"),
   N("A star's peak wavelength is 290 nm. Find its surface temperature (K).", 2.90e-3 / 290e-9, "T = 2.90 × 10⁻³ ÷ 2.9 × 10⁻⁷ = 10 000 K.", 5, "K"),
   TF("True or false: the Hubble law says that more distant galaxies recede faster.", True, "v = H₀d: speed is proportional to distance.")],
  [N("A galaxy recedes at 14 000 km/s. Find its distance (Mpc; H₀ = 70 km s⁻¹ Mpc⁻¹).", 14000 / 70, "d = v/H₀ = 14 000/70 = 200 Mpc.", 0.5, "Mpc", difficulty=2),
   N("Estimate the age of the Universe 1/H₀ in 10⁹ years (H₀ = 70 km s⁻¹ Mpc⁻¹; 1 Mpc = 3.09 × 10¹⁹ km; 1 yr = 3.16 × 10⁷ s).", sg(3.09e19 / 70 / 3.16e7 / 1e9, 3), "1/H₀ = 3.09 × 10¹⁹ ÷ 70 = 4.41 × 10¹⁷ s = 1.40 × 10¹⁰ years = 14.0 × 10⁹ years.", 0.1, "× 10⁹ years")],
  P("The spectral line of hydrogen of rest wavelength 656.3 nm is observed at 662.9 nm in the light from a distant galaxy (c = 3.00 × 10⁵ km/s, H₀ = 70 km s⁻¹ Mpc⁻¹).",
    [pn("Redshift z = Δλ/λ (3 s.f.).", sg((662.9 - 656.3) / 656.3, 3), "z = 6.6/656.3 = 0.0101.", 0.0002, None, 1),
     pn("Recession speed (km/s, 3 s.f.).", sg((662.9 - 656.3) / 656.3 * 3.00e5, 3), "v = zc = 0.01006 × 3.00 × 10⁵ = 3.02 × 10³ km/s.", 15, "km/s", 1),
     pn("Distance of the galaxy (Mpc, 2 s.f.).", sg((662.9 - 656.3) / 656.3 * 3.00e5 / 70, 2), "d = v/H₀ = 3017/70 = 43 Mpc.", 0.5, "Mpc", 1),
     pm("A redshift means the galaxy is", "moving away from us", ["moving towards us", "at rest", "hotter than the Sun"], "Wavelengths are stretched.", 1)]),
  [M("Wien's law relates", "peak wavelength to temperature", ["flux to distance", "speed to distance", "mass to luminosity"], "λ_max T = constant."),
   M("The cosmic microwave background is evidence for", "the Big Bang", ["black holes", "dark matter only", "the Moon's formation"], "It is the cooled radiation from the early hot Universe."),
   M("If the distance to a star doubles, the flux received", "falls to one quarter", ["halves", "doubles", "is unchanged"], "F ∝ 1/d²."),
   M("The redshift of a galaxy is caused by", "its recession (expansion of space)", ["its high temperature", "its mass only", "the Earth's motion only"], "The wavelengths are stretched as space expands."),
   M("Doubling the radius of a star at constant temperature multiplies its luminosity by", "4", ["2", "8", "16"], "L ∝ R².")],
  (hubble_fig(), "Hubble's law: recession velocity against distance for galaxies (illustrative points, line v = 70d).", "A scatter of points close to a straight line through the origin showing velocity increasing with distance."),
  notes=["THIS LESSON MAY NOT BE ON THE CAMEROON GCE ADVANCED LEVEL SYLLABUS: it is included as optional enrichment and must be checked by a teacher.", "H₀ ≈ 70 km s⁻¹ Mpc⁻¹ and L_Sun = 3.8 × 10²⁶ W are rounded values. The Hubble plot points are illustrative, not real data.", "Black holes and dark matter are not covered."], minutes=30)

# ---- semiconductors & electronics
Eg = h_ * c_ / 650e-9 / e_; Rled = (5.0 - 2.0) / 0.020; assert abs(Eg - 1.91) < 0.01 and abs(Rled - 150) < 1e-9
G_inv = -100 / 10; Vinv = G_inv * 0.30; G_ni = 1 + 90 / 10; assert abs(Vinv + 3.0) < 1e-9 and G_ni == 10
def opamp_fig():
    it = [POLY([170, 80, 170, 180, 280, 130], width=2), T(182, 108, "−", 18, bold=True, anchor="start"), T(182, 160, "+", 16, bold=True, anchor="start")]
    it += branch((40, 100), (170, 100), [(0.5, "res", "R_in")]) + branch((170, 160), (130, 160)) + branch((130, 160), (130, 190)) + [LINE(115, 190, 145, 190, width=3), LINE(122, 197, 138, 197, width=2)]
    it += branch((280, 130), (360, 130)) + branch((320, 130), (320, 50)) + branch((320, 50), (115, 50), [(0.5, "res", "R_f")]) + branch((115, 50), (115, 100))
    it += [dot(115, 100), dot(320, 130), T(30, 90, "V_in", 13, anchor="end"), T(365, 134, "V_out", 13, anchor="start")]
    return shapes(it, 420, 230)
lesson(ch, "electronics", "Semiconductors, diodes and operational amplifiers",
  ["Describe intrinsic and extrinsic (n-type, p-type) semiconductors and the action of a p–n junction diode, including the LED",
   "Use the ideal op-amp rules for inverting and non-inverting amplifiers",
   "Describe a transistor or logic gate as a switch (qualitative)"],
  [("definition", "Semiconductors and the diode",
    "In a **semiconductor** (silicon, germanium) the resistance falls as the temperature rises, because more charge carriers are released. **Doping** adds impurities: **n-type** (extra electrons, e.g. phosphorus) and **p-type** (extra 'holes', e.g. boron).\n\n"
    "A **p–n junction** has a **depletion layer**. Forward-biased (p to +), it conducts above about 0.6 V (silicon); reverse-biased it conducts almost nothing. Applications: rectifiers; **light-emitting diodes** emit photons of energy about equal to the band gap (E = hc/λ)."),
   ("formule", "The ideal operational amplifier",
    "An **op-amp** has two inputs (− inverting, + non-inverting) and one output. Ideal properties: infinite open-loop gain, infinite input resistance, zero output resistance. With negative feedback the two inputs are at the **same potential**, and no current enters the inputs.\n\n"
    "**Inverting amplifier**: V_out = −(R_f/R_in)V_in. **Non-inverting amplifier**: V_out = (1 + R_f/R₁)V_in. The output cannot exceed the supply rails: it **saturates**.",
    r"V_{out}=-\frac{R_f}{R_{in}}V_{in}\qquad V_{out}=\left(1+\frac{R_f}{R_1}\right)V_{in}"),
   ("retenir", "Switching circuits",
    "A **transistor** (npn) acts as a switch: when the base–emitter voltage exceeds about 0.6 V the collector–emitter path conducts and a relay, lamp or buzzer in the collector circuit can be switched on. A potential divider with an LDR or thermistor can supply the base voltage (light- or temperature-operated switch). **Logic gates** (AND, OR, NOT, NAND, NOR) process digital signals (0 or 1); NOT gives the opposite of its input."),
   ("pieges", "Common mistakes",
    "- Forgetting the series resistor for an LED: without it the current is too high.\n- Using the inverting formula without the minus sign.\n- Ignoring saturation: the output can never be larger than the supply voltage.\n- Thinking current flows into an ideal op-amp input.")],
  [("Example 1 — An LED", "A red LED emits light of wavelength 650 nm. Estimate the energy gap. The LED needs 2.0 V and 20 mA from a 5.0 V supply: find the series resistor.",
    ["E = hc/λ = 6.63 × 10⁻³⁴ × 3.00 × 10⁸ ÷ 6.50 × 10⁻⁷ = 3.06 × 10⁻¹⁹ J = 1.91 eV.", "The resistor takes 5.0 − 2.0 = 3.0 V at 0.020 A: R = 3.0/0.020 = 150 Ω."], "E ≈ 1.9 eV; R = 150 Ω"),
   ("Example 2 — Amplifiers", "An inverting amplifier has R_in = 10 kΩ and R_f = 100 kΩ. Find V_out for V_in = +0.30 V. What would a non-inverting amplifier with R₁ = 10 kΩ and R_f = 90 kΩ give for the same input?",
    ["Inverting: V_out = −(100/10) × 0.30 = −3.0 V.", "Non-inverting: gain = 1 + 90/10 = 10, so V_out = 10 × 0.30 = 3.0 V."], "−3.0 V (inverting); +3.0 V (non-inverting)")],
  [N("Find the voltage gain of an inverting amplifier with R_in = 5.0 kΩ and R_f = 50 kΩ.", -10.0, "Gain = −R_f/R_in = −50/5.0 = −10.", 0.01, None),
   M("In n-type silicon, the majority charge carriers are", "electrons", ["holes", "protons", "neutrons"], "Doping with phosphorus adds free electrons."),
   TF("True or false: a silicon diode conducts well when forward biased by more than about 0.6 V.", True, "Above the threshold voltage the forward current rises steeply.")],
  [N("A non-inverting amplifier has R₁ = 2.0 kΩ and R_f = 18 kΩ. For V_in = 0.40 V find V_out (V), ignoring saturation.", (1 + 18 / 2.0) * 0.40, "Gain = 1 + 18/2.0 = 10; V_out = 4.0 V.", 0.02, "V", difficulty=2),
   N("An op-amp inverting amplifier of gain −8 is supplied with ±9 V and V_in = 2.0 V. What is the output (V)?", -9.0, "The ideal output −16 V exceeds the supply, so the amplifier saturates near −9 V (a little less in practice).", 0.5, "V")],
  P("An inverting amplifier uses R_in = 10 kΩ and R_f = 47 kΩ with a ±12 V supply. A sensor gives V_in = 0.50 V.",
    [pn("Voltage gain.", -4.7, "Gain = −R_f/R_in = −47/10 = −4.7.", 0.01, None, 1),
     pn("Output voltage (V).", -2.35, "V_out = −4.7 × 0.50 = −2.35 V.", 0.01, "V", 1),
     pn("Input voltage at which the output just saturates at −12 V (V, 3 s.f.).", sg(12 / 4.7, 3), "V_in = 12/4.7 = 2.55 V.", 0.02, "V", 1),
     pm("The point of the inverting input of an ideal op-amp with negative feedback is at", "virtually the same potential as the non-inverting input (earth here)", ["the supply voltage", "twice the output voltage", "infinite potential"], "Because the gain is very large, the input voltage difference is almost zero (a virtual earth).", 1)], figure=opamp_fig()),
  [M("Doping silicon with boron produces", "p-type material", ["n-type material", "an insulator", "a superconductor"], "Boron has one fewer valence electron: holes."),
   M("In an op-amp amplifier saturation means", "the output is limited by the supply voltage", ["the input current is large", "the gain is zero", "the circuit oscillates"], "The output cannot exceed the rails."),
   M("The resistance of a semiconductor as the temperature rises", "decreases", ["increases", "is constant", "is zero"], "More carriers are excited."),
   M("A reverse-biased p–n junction", "conducts almost no current", ["conducts a large current", "emits light", "has no depletion layer"], "The depletion layer widens."),
   M("A NOT gate with an input of 1 gives an output of", "0", ["1", "undefined", "2"], "NOT inverts the signal.")],
  (opamp_fig(), "An inverting op-amp amplifier: V_out = −(R_f/R_in)V_in.", "A triangular amplifier symbol with an input resistor to the inverting input, a feedback resistor from the output back to that input, and the non-inverting input earthed."),
  notes=["Transistor and logic-gate content is only qualitative and may be examined differently; band theory is simplified.", "Op-amp lessons may be outside the board's syllabus at this level: check."], minutes=35, prereq=["gceal-physics-ac"])

# ---- practical skills
Ls = [0.20, 0.40, 0.60, 0.80, 1.00]; Ts = [0.90, 1.27, 1.55, 1.80, 2.01]; T2 = [t * t for t in Ts]
grad = (T2[-1] - T2[0]) / (Ls[-1] - Ls[0]); g_exp = 4 * math.pi ** 2 / grad
assert abs(grad - 4.04) < 0.01 and abs(g_exp - 9.78) < 0.03, (grad, g_exp)
tA = [0, 10, 20, 30, 40]; AA = [800 * 0.5 ** (t / 20) for t in tA]; lnA = [math.log(a) for a in AA]
slopeA = (lnA[-1] - lnA[0]) / (tA[-1] - tA[0]); thalf = math.log(2) / -slopeA; assert abs(thalf - 20) < 1e-9
def prac_fig():
    pts = [(t / 10, la, None, "blue") for t, la in zip(tA, lnA)]
    return plot(0, 4, 5, 7, points=pts, segments=[(0, lnA[0], 4, lnA[-1], "red", False, None)], grid=1, xlabel="t / 10 min", ylabel="ln A", w=380, h=240)
lesson(ch, "practical", "Practical skills and data analysis",
  ["Plan an investigation: identify independent, dependent and control variables, choose apparatus and reduce uncertainty",
   "Linearise relationships (y = mx + c) and find physical quantities from gradients and intercepts, with uncertainties",
   "Evaluate an experiment: sources of error and improvements"],
  [("definition", "Planning",
    "In a good investigation you state the **independent variable** (changed), the **dependent variable** (measured) and the **control variables** (kept constant). Plan to take a **range** of at least five values, repeat readings, choose instruments of suitable precision (micrometer 0.01 mm for wire diameter, vernier caliper 0.1 mm or better, stopwatch for **many** oscillations to reduce reaction-time error), and include a safety consideration."),
   ("methode", "Linearising a relationship",
    "Rearrange the equation into **y = mx + c** so that a straight-line graph gives the unknown from the gradient m or intercept c:\n\n- T = 2π√(l/g) → plot **T² against l**: gradient = 4π²/g.\n- N = N₀e^(−λt) → plot **ln N against t**: gradient = −λ.\n- y = kxⁿ → plot **lg y against lg x**: gradient = n, intercept = lg k.\n- V = E − Ir → plot V against I: intercept E, gradient −r."),
   ("retenir", "Graphs and uncertainty",
    "Choose scales that fill more than half the graph, label axes with quantities **and units**, plot points accurately, draw a best-fit line with points balanced above and below. Find the gradient using a **large triangle** on the line (not data points). The uncertainty in the gradient comes from the steepest and shallowest acceptable lines: Δm = ½(m_max − m_min). Use percentage uncertainties to decide which measurement limits accuracy."),
   ("pieges", "Common mistakes",
    "- Calculating the gradient from two data points that are close together.\n- Forgetting units on axis labels or in the gradient.\n- Calling an unavoidable random scatter a 'human error': name the specific source (for example, reaction time when starting the stopwatch).\n- Changing more than one variable at a time.")],
  [("Example 1 — g from a pendulum", "A student measures the period T for lengths l = 0.20 to 1.00 m and plots T² against l. T² = 0.81 s² at 0.20 m and 4.04 s² at 1.00 m (two points on the best-fit line). Find g.",
    ["Gradient = (4.04 − 0.81)/(1.00 − 0.20) = 4.04 s² m⁻¹.", "T² = (4π²/g) l, so g = 4π²/gradient = 39.48/4.04 = 9.77 m s⁻²."], "g = 9.8 m s⁻² (to 2 s.f.)"),
   ("Example 2 — Half-life by a straight line", "Corrected count rates fall from 800 to 200 counts/min in 40 min. A graph of ln A against t has a gradient of −0.0347 min⁻¹ . Find the half-life.",
    ["ln A = ln A₀ − λt, so the gradient is −λ and λ = 0.0347 min⁻¹.", "t½ = ln 2/λ = 0.693/0.0347 = 20.0 min (check: 800 → 400 → 200 in two half-lives of 20 min)."], "t½ = 20 min")],
  [N("The gradient of a graph of T² against l for a pendulum is 3.95 s² m⁻¹. Find g (m s⁻², 3 s.f.).", sg(4 * math.pi ** 2 / 3.95, 3), "g = 4π²/gradient = 39.48/3.95 = 9.99 m s⁻².", 0.03, "m/s²"),
   M("To find the Young modulus of a wire by a graph of extension against load, the gradient of that graph is", "L/(AE)", ["AE/L", "E/A", "LAE"], "x = (L/AE)F."),
   TF("True or false: it is better to time 20 oscillations than 1 oscillation of a pendulum.", True, "The reaction-time error is then shared over 20 periods, so the percentage uncertainty in T is much smaller.")],
  [N("A graph of lg y against lg x for y = kxⁿ has a gradient 1.5. If x is doubled, by what factor does y increase (3 s.f.)?", sg(2 ** 1.5, 3), "y ∝ x^1.5 so doubling x multiplies y by 2^1.5 = 2.83.", 0.02, None, difficulty=2),
   N("The steepest and shallowest acceptable lines for a graph have gradients 4.20 and 3.80 (units s² m⁻¹). Find the uncertainty in the gradient.", 0.2, "Δm = ½(4.20 − 3.80) = 0.20 s² m⁻¹.", 0.005, "s²/m")],
  P("In a radioactivity experiment, corrected count rates A (counts/min) were taken every 10 min. A graph of ln A against t is a straight line (see figure).",
    [pn("The gradient of the line (min⁻¹, 3 s.f.). Take the line from t = 0 (ln A = 6.685) to t = 40 min (ln A = 5.298).", sg(slopeA, 3), "Gradient = (5.298 − 6.685)/40 = −0.0347 min⁻¹.", 0.0003, "min⁻¹", 2),
     pn("The decay constant λ (min⁻¹, 3 s.f.).", sg(-slopeA, 3), "λ = −gradient = 0.0347 min⁻¹.", 0.0003, "min⁻¹", 1),
     pn("The half-life (min, 3 s.f.).", sg(thalf, 3), "t½ = ln 2/λ = 0.693/0.0347 = 20.0 min.", 0.2, "min", 1)], figure=prac_fig()),
  [M("To reduce the percentage uncertainty in a period measurement you should", "time many oscillations", ["use a longer string", "time only one swing", "use heavier bob"], "Fixed reaction time errors become a smaller percentage of the total."),
   M("A straight line through the origin on a graph of F against x shows that", "F is proportional to x", ["F is inversely proportional to x", "F = x", "the experiment is wrong"], "y = mx with c = 0."),
   M("The best way to find a gradient on a graph is to use", "a large triangle on the best-fit line", ["two adjacent data points", "the largest data point only", "the origin and any point"], "Large triangles reduce the percentage uncertainty."),
   M("A control variable is", "kept constant during the experiment", ["the quantity measured", "the quantity changed", "the final result"], "Only the independent variable should change."),
   M("A micrometer screw gauge usually reads to", "0.01 mm", ["1 mm", "0.1 cm", "0.5 mm"], "It is used for the diameter of thin wires.")],
  (prac_fig(), "Linearised decay data: ln A against t gives a straight line of gradient −λ.", "Five points on a downward sloping straight line of the natural logarithm of count rate against time."),
  notes=["The graphs shown use generated data with t½ = 20 min exactly; real data would scatter.", "The board's practical assessment (continuous assessment, practical examination) format is not described; check.", "Instrument precision values are typical (micrometer 0.01 mm, vernier 0.1 mm)."], minutes=35, prereq=["gceal-physics-radioactivity"])

# =============================================================== MOCK
chs = p.chapters
Lq = {l.slug: l for c in chs for l in c.lessons}
r_p = math.sqrt(2 * 2.5e3 * e_ / 1.67e-27); KEp = 2.5e3 * e_; rp = 1.67e-27 * r_p / (e_ * 0.10)
assert abs(KEp - 4.0e-16) < 1e-19 and abs(r_p - 6.92e5) < 0.01e5 and abs(rp - 0.0722) < 0.0005
lamH = math.log(2) / (12 * 3600); AH = 4.8e5 / 8; NH = 4.8e5 / lamH
assert abs(lamH - 1.604e-5) < 0.001e-5 and AH == 6.0e4 and abs(NH - 2.99e10) < 0.02e10
d4 = 1e-3 / 400; t1_4 = math.degrees(math.asin(589e-9 / d4)); t2_4 = math.degrees(math.asin(2 * 589e-9 / d4)); n4 = int(d4 / 589e-9)
assert abs(t1_4 - 13.63) < 0.02 and abs(t2_4 - 28.1) < 0.1 and n4 == 4
Wcap = 0.5 * 220e-6 * 9.0 ** 2; assert abs(Wcap - 8.91e-3) < 1e-6
Cn = math.degrees(math.asin(1 / 1.60)); assert abs(Cn - 38.7) < 0.05
A1 = mockq(Lq["capacitors"], M("A 220 μF capacitor is charged to 9.0 V. The energy stored is", "8.9 mJ", ["17.8 mJ", "2.0 mJ", "1.98 mJ"], "W = ½CV² = ½ × 220 × 10⁻⁶ × 81 = 8.9 × 10⁻³ J.", points=2))
A2 = mockq(Lq["photoelectric"], M("Photons of energy 3.0 eV fall on a metal of work function 2.0 eV. The stopping potential is", "1.0 V", ["5.0 V", "2.0 V", "3.0 V"], "K_max = 3.0 − 2.0 = 1.0 eV, so V_s = 1.0 V.", points=2))
A3 = mockq(Lq["radioactivity"], M("The activity of a source falls from 800 Bq to 100 Bq in 30 minutes. Its half-life is", "10 minutes", ["15 minutes", "30 minutes", "7.5 minutes"], "800 → 400 → 200 → 100 is three half-lives, so t½ = 30/3 = 10 min.", points=2))
A4 = mockq(Lq["refraction"], M("The critical angle for a glass of refractive index 1.60 (glass to air) is", "38.7°", ["51.3°", "32.0°", "60.0°"], "sin C = 1/1.60 = 0.625 gives C = 38.7°.", points=2))
B1 = mockq(Lq["bfield"], P("Question 5 (4 marks). A proton (m = 1.67 × 10⁻²⁷ kg) is accelerated from rest through 2.5 kV and then enters a uniform magnetic field of 0.10 T at right angles to the field.",
    [pn("Kinetic energy gained (10⁻¹⁶ J).", KEp / 1e-16, "KE = eV = 1.60 × 10⁻¹⁹ × 2500 = 4.0 × 10⁻¹⁶ J.", 0.01, "× 10⁻¹⁶ J", 1),
     pn("Speed of the proton (10⁵ m/s, 3 s.f.).", sg(r_p / 1e5, 3), "v = √(2KE/m) = √(8.0 × 10⁻¹⁶ ÷ 1.67 × 10⁻²⁷) = 6.92 × 10⁵ m/s.", 0.05, "× 10⁵ m/s", 1),
     pn("Radius of its circular path in the field (cm, 3 s.f.).", sg(rp * 100, 3), "r = mv/(qB) = 1.67 × 10⁻²⁷ × 6.92 × 10⁵ ÷ (1.60 × 10⁻¹⁹ × 0.10) = 0.0722 m = 7.22 cm.", 0.1, "cm", 2)]))
B2 = mockq(Lq["radioactivity"], P("Question 6 (4 marks). A radioactive tracer has half-life 12 hours and an initial activity of 4.8 × 10⁵ Bq.",
    [pn("Decay constant (10⁻⁵ s⁻¹, 3 s.f.).", sg(lamH / 1e-5, 3), "λ = ln 2/(12 × 3600) = 1.60 × 10⁻⁵ s⁻¹.", 0.01, "× 10⁻⁵ s⁻¹", 1),
     pn("Activity after 36 hours (10⁴ Bq).", AH / 1e4, "36 h = 3 half-lives: 4.8 × 10⁵/8 = 6.0 × 10⁴ Bq.", 0.01, "× 10⁴ Bq", 1),
     pn("Initial number of radioactive nuclei (10¹⁰, 3 s.f.).", sg(NH / 1e10, 3), "N₀ = A₀/λ = 4.8 × 10⁵ ÷ 1.604 × 10⁻⁵ = 2.99 × 10¹⁰.", 0.03, "× 10¹⁰", 1),
     pm("Which radiation from the tracer is best detected outside the body?", "gamma rays", ["alpha particles", "beta particles stopped in the skin only", "radio waves"], "γ-rays penetrate tissue; α and β are absorbed.", 1)]))
B3 = mockq(Lq["grating"], P("Question 7 (4 marks). Sodium light (wavelength 589 nm) falls normally on a diffraction grating with 400 lines per mm.",
    [pn("Slit spacing d (μm).", 2.5, "d = 1/400 mm = 2.5 μm.", 0.005, "μm", 1),
     pn("Angle of the first-order maximum (degrees, 3 s.f.).", sg(t1_4, 3), "sin θ = λ/d = 589 × 10⁻⁹/2.5 × 10⁻⁶ = 0.2356, θ = 13.6°.", 0.1, "°", 1),
     pn("The greatest order of maximum that can be observed.", n4, "d/λ = 2.5/0.589 = 4.24, so n_max = 4.", 0, None, 1),
     pn("Angle of the second-order maximum (degrees, 3 s.f.).", sg(t2_4, 3), "sin θ = 2 × 0.2356 = 0.4712, θ = 28.1°.", 0.1, "°", 1)]))
p.mock("1", "GCE A Level mock — Physics (paper 1)", 90,
       "Answer all questions. Section A has four short multiple-choice questions (2 marks each); Section B has three structured questions (4 marks each). The paper is marked out of 20. Constants: e = 1.60 × 10⁻¹⁹ C, h = 6.63 × 10⁻³⁴ J s, c = 3.00 × 10⁸ m/s, ln 2 = 0.693. This is an original practice paper; its format is not that of the Cameroon GCE Board and should be checked against the official texts.",
       [("Section A — Short questions (8 marks)", [A1, A2, A3, A4]), ("Section B — Structured questions (12 marks)", [B1, B2, B3])])
p.write()
