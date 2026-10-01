import math
from math import comb, erf, sqrt, exp, pi
from fractions import Fraction as Fr
from _maths import *
from l6m_a import REF
Phi = lambda z: .5 * (1 + erf(z / sqrt(2)))

def build(p):
    ch = p.chapter("mechanics", "Introductory mechanics", "Cameroon GCE Board — Advanced Level Mathematics, Mechanics (to be checked against the official syllabus)")
    # ------------------------------------------------------------- kinematics
    assert (20 - 5) / 6 == 2.5 and (5 + 20) / 2 * 6 == 75 and 400 / 20 == 20 and 20 / 10 == 2
    assert 4 * 5 + .5 * 2 * 25 == 45 and 15 / .5 == 30 and 30 ** 2 / (2 * 5) == 90
    v = lambda t: 3 * t * t - 12 * t + 9; a_ = lambda t: 6 * t - 12; assert v(2) == -3 and a_(2) == 0
    assert 1.5 * 10 == 15 and .5 * 1.5 * 100 == 75 and 15 * 20 == 300 and 75 + 300 == 375
    K = Axes(-2, 33, -3, 18, w=400, h=240, xticks=[10, 30], yticks=[15])
    K.poly([(0, 0), (10, 15), (30, 15), (30, 0)], fill="yellow", color="blue", width=2)
    K.text(20, 6, "area = distance", 13).text(4, 11.5, "gradient = a", 12).text(20, 16.5, "15", 12)
    lesson(ch, "kinematics", "Motion in a straight line (constant acceleration)",
        ["Use the five constant-acceleration (suvat) equations", "Read distance and acceleration from velocity–time graphs", "Use calculus for variable motion"],
        [("formule", "Constant acceleration", "With initial velocity $u$, final velocity $v$, acceleration $a$, time $t$, displacement $s$: $v=u+at$; $s=ut+\\frac12at^2$; $v^2=u^2+2as$; $s=\\frac{(u+v)}{2}t$. Units: m, m/s, m/s², s. Falling or thrown objects: $a=g=10$ m/s² (we use 10; some books use 9.8)."),
         ("propriete", "Graphs", "On a velocity–time graph the **gradient** is the acceleration and the **area** under the graph is the distance travelled (displacement if signed). On a displacement–time graph the gradient is the velocity."),
         ("formule", "Calculus link", "For variable acceleration: velocity $v=\\frac{ds}{dt}$ and acceleration $a=\\frac{dv}{dt}=\\frac{d^2s}{dt^2}$. Conversely $v=\\int a\\,dt$ and $s=\\int v\\,dt$ (with initial conditions to fix $C$)."),
         ("methode", "Method", "1. List $s,u,v,a,t$ with signs (choose a positive direction). 2. Choose the equation that contains the unknown and three known quantities. 3. Solve, then check the sign and units. For an object thrown up, $v=0$ at the top.")],
        [("Example 1", "A car accelerates uniformly from 5 m/s to 20 m/s in 6 s. Find the acceleration and the distance covered.", ["$a=\\frac{v-u}{t}=\\frac{20-5}{6}=2.5$ m/s².", "$s=\\frac{u+v}{2}t=\\frac{25}{2}\\times6=75$ m."], "$a=2.5$ m/s²; $s=75$ m"),
         ("Example 2", "A ball is thrown upwards at 20 m/s ($g=10$ m/s²). Find the greatest height and the total time in the air.", ["Up is positive, $a=-10$. At the top $v=0$: $0=400-20s$, so $s=20$ m.", "Time to the top: $0=20-10t$, so $t=2$ s; the fall takes the same time."], "Height 20 m; total time 4 s", K.fig())],
        [N("A particle has $u=4$ m/s and $a=2$ m/s². Find its displacement after 5 s (m).", 45, "$s=ut+\\frac12at^2=20+25$.", unit="m"),
         M("A train starts from rest with $a=0.5$ m/s². How long to reach 15 m/s?", "30 s", ["7.5 s", "15 s", "60 s"], "$t=\\frac{v-u}{a}=\\frac{15}{0.5}$."),
         TF("On a velocity–time graph the gradient gives the acceleration.", True, "Gradient $=\\frac{\\Delta v}{\\Delta t}=a$.")],
        [M("A van brakes from 30 m/s with deceleration 5 m/s². The stopping distance is", "90 m", ["45 m", "180 m", "6 m"], "$0=900-10s$, so $s=90$."),
         N("A particle moves so that $s=t^3-6t^2+9t$ metres. Find its velocity (m/s) at $t=2$ s.", -3, "$v=\\frac{ds}{dt}=3t^2-12t+9=12-24+9=-3$ (it is moving backwards). Also $a=6t-12=0$ at $t=2$.", unit="m/s")],
        P("A motorbike leaves a junction in Garoua from rest and accelerates uniformly at 1.5 m/s² for 10 s, then travels at constant speed for a further 20 s.", [pn("Find its speed after 10 s (m/s).", 15, "$v=at=1.5\\times10$.", pts=1), pn("Find the distance covered while accelerating (m).", 75, "$s=\\frac12at^2=\\frac12\\times1.5\\times100$.", pts=2), pn("Find the total distance in the 30 s (m).", 375, "$75+15\\times20=375$ m (the area of the trapezium on the graph).", pts=2)], figure=K.fig()),
        [("The unit of acceleration is", "m/s²", ["m/s", "m", "s²"], "Change in velocity per second."),
         ("$v=u+at$ with $u=2$, $a=3$, $t=4$ gives $v=$", "$14$", ["$10$", "$24$", "$9$"], "$2+12$."),
         ("The area under a velocity–time graph gives", "distance travelled", ["acceleration", "speed", "time"], "$\\int v\\,dt$."),
         ("At the highest point of a ball thrown upwards, the velocity is", "$0$", ["$g$", "$u$", "maximum"], "It changes direction."),
         ("If $s=5t^2$, the acceleration is", "$10$ m/s²", ["$5$ m/s²", "$10t$ m/s²", "$0$"], "$v=10t$, $a=10$.")],
        ill=None, notes=["g=10 m/s² used throughout (state which value the syllabus expects: 9.8 or 10).", "Projectiles are in the Upper Sixth pack.", "Check how the Cameroon GCE Board splits Mechanics and Statistics between Lower and Upper Sixth (or separate papers): this placement is an assumption."], ref=REF.replace("Pure Mathematics", "Mechanics"))

    # ------------------------------------------------------------- forces
    assert (3000 - 600) / 1200 == 2 and 0.4 * 50 == 20 and (30 - 20) / 5 == 2
    a_at = (5 - 3) * 10 / 8; T_at = 3 * (10 + a_at); assert a_at == 2.5 and T_at == 37.5 and abs(10 * math.sin(math.pi / 6) - 5) < 1e-9
    assert 60 * (10 + 1.5) == 690 and 60 * (10 - 2) == 480
    forces = shapes([RECT(160, 100, 80, 50, fill="lightgrey", stroke="ink"), T(200, 130, "5 kg", 14),
                     LINE(240, 125, 330, 125, color="blue", width=3, arrow="end"), T(335, 118, "30 N", 13, anchor="start"),
                     LINE(160, 125, 100, 125, color="red", width=3, arrow="end"), T(30, 130, "friction 20 N", 13, anchor="start"),
                     LINE(200, 100, 200, 30, color="green", width=3, arrow="end"), T(210, 40, "R = 50 N", 13, anchor="start"),
                     LINE(200, 150, 200, 215, color="orange", width=3, arrow="end"), T(210, 212, "W = 50 N", 13, anchor="start"),
                     LINE(100, 150, 340, 150, color="ink", width=1)], 400, 240)
    lesson(ch, "forces", "Forces and Newton's laws",
        ["Apply $F=ma$ to a particle", "Resolve forces; use weight, normal reaction and friction", "Solve connected-particle and lift problems"],
        [("propriete", "Newton's laws", "1st: a particle stays at rest or moves at constant velocity unless a resultant force acts. 2nd: resultant force $F=ma$ (newtons, kg, m/s²). 3rd: every action has an equal and opposite reaction. Weight $W=mg$ (with $g=10$ m/s²)."),
         ("formule", "Friction", "On a rough surface friction $F\\leq\\mu R$ where $R$ is the normal reaction and $\\mu$ the coefficient of friction. When the particle is about to slip, or is sliding, $F=\\mu R$ (limiting friction). Friction opposes motion (or the tendency to move)."),
         ("methode", "Method", "1. Draw a force diagram. 2. Choose the direction of motion as positive and resolve in two perpendicular directions. 3. Write $F_{net}=ma$ for each particle. For connected particles over a pulley the tension is the same on both sides and both have the same acceleration. 4. Solve. On a smooth slope of angle $\\alpha$ the acceleration down the slope is $g\\sin\\alpha$."),
         ("pieges", "Common mistakes", "- Mixing mass and weight: weight is a force in N.\n- On a slope the normal reaction is $mg\\cos\\alpha$, not $mg$.\n- Writing $F=\\mu R$ when the particle is not at the point of slipping (then $F<\\mu R$).\n- In a lift, $R\\neq mg$ unless the acceleration is zero.")],
        [("Example 1", "A 1200 kg car has an engine force of 3000 N and resistance 600 N. Find its acceleration.", ["Resultant force $=3000-600=2400$ N.", "$a=\\frac{F}{m}=\\frac{2400}{1200}$."], "$a=2$ m/s²"),
         ("Example 2", "A 5 kg block on a rough horizontal table ($\\mu=0.4$) is pulled by a horizontal force of 30 N. Find its acceleration ($g=10$).", ["Vertically $R=mg=50$ N, so friction $F=\\mu R=0.4\\times50=20$ N.", "Horizontally $30-20=5a$, so $a=2$ m/s²."], "$a=2$ m/s²", forces)],
        [N("Find the weight in N of a 70 kg person ($g=10$).", 700, "$W=mg=70\\times10$.", unit="N"),
         M("A force of 24 N is the net force on an 8 kg mass. Its acceleration is", "3 m/s²", ["192 m/s²", "0.33 m/s²", "32 m/s²"], "$a=\\frac{F}{m}=\\frac{24}{8}$."),
         TF("Friction always equals $\\mu R$.", False, "Only when the particle is moving or about to move; otherwise $F\\leq\\mu R$.")],
        [M("A 10 kg block slides down a smooth slope inclined at $30\\degree$. Its acceleration is ($g=10$)", "5 m/s²", ["10 m/s²", "8.66 m/s²", "50 m/s²"], "$a=g\\sin30\\degree=10\\times0.5$."),
         N("Masses 3 kg and 5 kg hang on either side of a smooth pulley, connected by a light string. Find the tension in N ($g=10$).", 37.5, "Acceleration $=\\frac{(5-3)g}{8}=2.5$ m/s². For the 3 kg mass: $T-30=3\\times2.5$, so $T=37.5$ N.", unit="N")],
        P("A 60 kg woman stands on the floor of a lift in a Douala office tower ($g=10$ m/s²). Let $R$ be the reaction of the floor on her.", [pn("The lift accelerates upwards at 1.5 m/s². Find $R$ (N).", 690, "$R-600=60\\times1.5$, so $R=690$.", pts=2), pn("The lift accelerates downwards at 2 m/s². Find $R$ (N).", 480, "$600-R=60\\times2$, so $R=480$.", pts=2), pt("When the lift moves at constant speed, $R=600$ N.", True, "No acceleration, so $R=mg$.", pts=1)]),
        [("Newton's second law is", "$F=ma$", ["$F=mv$", "$F=m/a$", "$F=a/m$"], "Resultant force."),
         ("The weight of 5 kg ($g=10$) is", "50 N", ["5 N", "0.5 N", "50 kg"], "$mg$."),
         ("A particle at constant velocity has resultant force", "zero", ["equal to its weight", "constant and non-zero", "infinite"], "Newton's first law."),
         ("The unit of force is the", "newton", ["joule", "watt", "pascal"], "$1\\text{ N}=1\\text{ kg m/s}^2$."),
         ("On a horizontal surface, $R$ for a block of mass $m$ at rest is", "$mg$", ["$0$", "$\\frac{mg}{2}$", "$\\mu mg$"], "Vertical equilibrium.")],
        ill=None, notes=["Moments and equilibrium of rigid bodies are in the Upper Sixth pack."], ref=REF.replace("Pure Mathematics", "Mechanics"))

    # ------------------------------------------------------------- statistics
    ch = p.chapter("statistics", "Probability and statistics", "Cameroon GCE Board — Advanced Level Mathematics, Statistics (to be checked against the official syllabus)")
    assert abs(.5 + .4 - .2 - .7) < 1e-12 and abs(.5 * .4 - .2) < 1e-12
    assert Fr(5, 8) * Fr(4, 7) == Fr(5, 14) and 2 * Fr(5, 8) * Fr(3, 7) == Fr(15, 28)
    assert abs(.3 * .6 - .18) < 1e-12 and abs(.12 / .4 - .3) < 1e-12 and 1 - Fr(5, 6) ** 3 == Fr(91, 216)
    assert 30 - (18 + 12 - 7) == 7
    pl = .3 * .5 + .7 * .1; pr = .3 * .5 / pl; assert abs(pl - .22) < 1e-12 and abs(pr - .6818) < 1e-3
    tree = shapes([LINE(40, 120, 170, 55), LINE(40, 120, 170, 185), T(105, 70, "0.3", 12), T(105, 185, "0.7", 12),
                   T(190, 55, "Rain", 13, anchor="start"), T(190, 185, "No rain", 13, anchor="start"),
                   LINE(250, 45, 330, 20), LINE(250, 55, 330, 85), T(300, 25, "0.5", 12, anchor="end"), T(300, 95, "0.5", 12, anchor="end"),
                   T(335, 20, "late", 12, anchor="start"), T(335, 85, "on time", 12, anchor="start"),
                   LINE(270, 185, 340, 160), LINE(270, 190, 340, 220), T(300, 168, "0.1", 12, anchor="end"), T(300, 222, "0.9", 12, anchor="end"),
                   T(345, 160, "late", 12, anchor="start"), T(345, 224, "on time", 12, anchor="start")], 400, 240)
    lesson(ch, "probability", "Probability",
        ["Use the addition and multiplication rules", "Use conditional probability and independence", "Draw and use tree diagrams"],
        [("formule", "Rules", "$P(A\\cup B)=P(A)+P(B)-P(A\\cap B)$. Complement: $P(A')=1-P(A)$. Conditional: $P(A|B)=\\frac{P(A\\cap B)}{P(B)}$. Always $0\\leq P\\leq1$."),
         ("definition", "Independent and mutually exclusive", "$A$ and $B$ are **independent** if $P(A\\cap B)=P(A)P(B)$ (one does not affect the other). They are **mutually exclusive** if $P(A\\cap B)=0$. Two events with positive probabilities cannot be both."),
         ("methode", "Tree diagrams", "Draw branches for each stage with their probabilities (the branches from a point add to 1). Multiply along a path for 'and'; add the paths for 'or'. For draws without replacement the probabilities of the second stage change."),
         ("pieges", "Common mistakes", "- Adding probabilities for 'and' (multiply!).\n- Forgetting to subtract $P(A\\cap B)$ in the addition rule.\n- Treating draws without replacement as independent.\n- Confusing $P(A|B)$ with $P(B|A)$.")],
        [("Example 1", "A bag has 5 red and 3 blue balls. Two are drawn without replacement. Find $P$(both red) and $P$(one of each colour).", ["Both red: $\\frac58\\times\\frac47=\\frac{20}{56}=\\frac{5}{14}$.", "RB or BR: $\\frac58\\times\\frac37+\\frac38\\times\\frac57=\\frac{15}{56}+\\frac{15}{56}=\\frac{15}{28}$."], "$\\frac{5}{14}$ and $\\frac{15}{28}$"),
         ("Example 2", "In Buea it rains on a given morning with probability 0.3. If it rains the bus is late with probability 0.5, otherwise 0.1. Find $P$(late).", ["Rain and late: $0.3\\times0.5=0.15$.", "No rain and late: $0.7\\times0.1=0.07$. Add."], "$P(\\text{late})=0.22$", tree)],
        [N("$A$ and $B$ are independent, $P(A)=0.3$, $P(B)=0.6$. Find $P(A\\cap B)$.", .18, "$0.3\\times0.6$.", tol=0.001),
         M("$P(A\\cap B)=0.12$ and $P(B)=0.4$. Then $P(A|B)=$", "0.3", ["0.048", "0.12", "3.33"], "$\\frac{0.12}{0.4}$."),
         TF("Two mutually exclusive events, each with positive probability, are independent.", False, "$P(A\\cap B)=0\\neq P(A)P(B)>0$.")],
        [M("A fair die is rolled 3 times. $P$(at least one six) is", "$\\frac{91}{216}$", ["$\\frac12$", "$\\frac{125}{216}$", "$\\frac{1}{216}$"], "$1-\\left(\\frac56\\right)^3=1-\\frac{125}{216}$."),
         O("Among 30 students, 18 study mathematics, 12 study physics and 7 study both. Find the number who study neither, and $P$(maths | physics).", "$|M\\cup P|=18+12-7=23$, so $30-23=7$ study neither. Of the 12 physics students 7 also study maths, so $P(M|P)=\\frac{7}{12}$.", ["Uses addition rule (1)", "Neither $=7$ (1)", "$\\frac{7}{12}$ (1)"])],
        P("Use the tree diagram of Example 2 (rain 0.3; late 0.5 if rain, 0.1 if no rain).", [pn("Find $P$(late).", .22, "$0.15+0.07$.", tol=0.001, pts=2), pn("Given that the bus is late, find $P$(it rained), to 3 d.p.", round(pr, 3), "$\\frac{P(\\text{rain and late})}{P(\\text{late})}=\\frac{0.15}{0.22}=0.682$.", tol=0.001, pts=3)], figure=tree),
        [("$P(A')$ when $P(A)=0.35$ is", "0.65", ["0.35", "0.45", "1.35"], "Complement."),
         ("For independent events $P(A\\cap B)=$", "$P(A)P(B)$", ["$P(A)+P(B)$", "$P(A)-P(B)$", "$0$"], "Definition."),
         ("$P(A)=0.5$, $P(B)=0.4$, $P(A\\cap B)=0.2$. $P(A\\cup B)=$", "0.7", ["0.9", "0.1", "0.2"], "$0.5+0.4-0.2$."),
         ("Probabilities on branches from one point add to", "1", ["0", "100", "the number of branches"], "Exhaustive outcomes."),
         ("Drawing two cards without replacement makes the draws", "dependent", ["independent", "exclusive", "impossible"], "Second probability changes.")],
        ill=None, notes=["Permutations/combinations for counting probability are only touched through the binomial coefficient.", "Check that Statistics (probability, data, distributions) is examined in Lower Sixth on the official syllabus: placement is an assumption."], ref=REF.replace("Pure Mathematics", "Statistics"))

    # data
    d = [4, 7, 8, 9, 12]; mean = sum(d) / 5; var = sum((x - mean) ** 2 for x in d) / 5
    assert mean == 8 and abs(var - 6.8) < 1e-12 and abs(var ** .5 - 2.608) < 1e-3
    fx_ = [(1, 2), (2, 5), (3, 8), (4, 5)]; n = 20; m2 = sum(x * f for x, f in fx_) / n; v2 = sum(x * x * f for x, f in fx_) / n - m2 ** 2
    assert m2 == 2.8 and abs(v2 - .86) < 1e-12 and abs(v2 ** .5 - .927) < 1e-3
    marks = [12, 15, 15, 16, 18, 18, 18, 20, 22, 26]; mm = sum(marks) / 10; vm = sum(x * x for x in marks) / 10 - mm ** 2
    assert mm == 18 and sorted(marks)[4:6] == [18, 18]
    lesson(ch, "data", "Describing data: mean, variance and standard deviation",
        ["Calculate mean, median, mode, variance and standard deviation", "Work with frequency tables and coding", "Compare data sets"],
        [("formule", "Measures", "Mean $\\bar x=\\frac{\\sum x}{n}$ (or $\\frac{\\sum fx}{\\sum f}$). Variance $\\sigma^2=\\frac{\\sum x^2}{n}-\\bar x^2$ (or $\\frac{\\sum fx^2}{\\sum f}-\\bar x^2$). Standard deviation $\\sigma=\\sqrt{\\sigma^2}$, in the same unit as the data."),
         ("propriete", "Coding", "If $y=ax+b$ then $\\bar y=a\\bar x+b$ and $\\sigma_y=|a|\\sigma_x$. Adding a constant changes the mean but NOT the spread; multiplying scales both."),
         ("methode", "Choosing a measure", "Median (middle value, or mean of the two middle values) and the interquartile range resist outliers. The mean and standard deviation use every value, so an outlier pulls them. The mode is the most frequent value."),
         ("pieges", "Common mistakes", "- Forgetting the square root at the end (variance is not the standard deviation).\n- Using $(\\sum x)^2$ instead of $\\sum x^2$.\n- Subtracting the mean before squaring in the shortcut formula: the shortcut is $\\frac{\\sum x^2}{n}-\\bar x^2$.")],
        [("Example 1", "Find the mean and standard deviation of 4, 7, 8, 9, 12.", ["$\\bar x=\\frac{40}{5}=8$.", "$\\sum(x-8)^2=16+1+0+1+16=34$, variance $=\\frac{34}{5}=6.8$.", "$\\sigma=\\sqrt{6.8}\\approx2.61$."], "Mean 8; $\\sigma\\approx2.61$"),
         ("Example 2", "Score $x$: 1, 2, 3, 4 with frequencies 2, 5, 8, 5. Find the mean and variance.", ["$n=20$, $\\sum fx=2+10+24+20=56$, $\\bar x=2.8$.", "$\\sum fx^2=2+20+72+80=174$; variance $=\\frac{174}{20}-2.8^2=8.7-7.84$."], "Mean 2.8; variance 0.86 ($\\sigma\\approx0.93$)", bars([("1", 2), ("2", 5), ("3", 8), ("4", 5)], unit="students"))],
        [N("Find the mean of 3, 5, 7, 9, 11.", 7, "$\\frac{35}{5}$."),
         M("The variance can be found from", "$\\frac{\\sum x^2}{n}-\\bar x^2$", ["$\\frac{\\sum x}{n}$", "$\\left(\\frac{\\sum x}{n}\\right)^2$", "$\\sum x^2-\\bar x$"], "Mean of squares minus square of the mean."),
         TF("Adding 5 to every value changes the standard deviation.", False, "The spread is unchanged; only the mean increases by 5.")],
        [M("If $\\bar x=10$ and $\\sigma_x=4$, and $y=2x+3$, then $\\bar y$ and $\\sigma_y$ are", "23 and 8", ["23 and 11", "20 and 8", "13 and 8"], "$\\bar y=2(10)+3$; $\\sigma_y=2\\times4$."),
         O("Salaries in a small workshop in Bafoussam: 90, 95, 100, 100, 105, 400 (thousand FCFA). Which is a better 'typical' value, the mean or the median? Explain.", "Mean $=\\frac{890}{6}\\approx148$; median $=100$. The value 400 is an outlier that pulls the mean well above what most workers earn, so the median (100) is more representative.", ["Computes both averages (1)", "Identifies the outlier (1)", "Justified choice of median (1)"])],
        P("Marks of 10 students: 12, 15, 15, 16, 18, 18, 18, 20, 22, 26.", [pn("Find the mean mark.", 18, "$\\frac{180}{10}=18$.", pts=1), pn("Find the standard deviation (2 d.p.).", round(vm ** .5, 2), "$\\sum x^2=%d$, variance $=\\frac{%d}{10}-18^2=%.1f$, $\\sigma\\approx%.2f$." % (sum(x * x for x in marks), sum(x * x for x in marks), vm, vm ** .5), tol=0.01, pts=3), pm("The median is", "18", ["17", "16", "19"], "Middle two values are both 18.", pts=1)]),
        [("Median of 2, 9, 4, 7, 5 is", "5", ["4", "7", "5.4"], "Sorted: 2, 4, 5, 7, 9."),
         ("The mode of 1, 2, 2, 3, 2, 5 is", "2", ["3", "2.5", "5"], "Most frequent."),
         ("Standard deviation is the square root of", "the variance", ["the mean", "the range", "the median"], "Definition."),
         ("An outlier mostly affects the", "mean", ["median", "mode", "range of the middle half"], "The mean uses every value."),
         ("If all values are doubled, the standard deviation is", "doubled", ["unchanged", "squared", "halved"], "$\\sigma_y=2\\sigma_x$.")],
        ill=None, notes=["Grouped data with histograms, cumulative frequency and quartiles are not worked in this lesson."], ref=REF.replace("Pure Mathematics", "Statistics"))

    # binomial and normal
    pb3 = comb(10, 3) * .3 ** 3 * .7 ** 7; assert abs(pb3 - .2668) < 1e-4
    assert abs(Phi(1) - .8413) < 1e-4 and abs(Phi(1.5) - .9332) < 1e-4 and abs(Phi(1.96) - .9750) < 1e-4 and abs(Phi(2) - .9772) < 1e-4
    assert abs(.8 ** 5 - .32768) < 1e-12 and 20 * .25 == 5 and Fr(9, 256) == Fr(comb(8, 7) + comb(8, 8), 256) and (74 - 62) / 6 == 2
    bell = plot(-3.5, 3.5, -0.1, 1.15, curves=[("exp(-x^2/2)", "blue", None)], segments=[(1.5, 0, 1.5, 0.3247, "red", True, "z = 1.5")], points=[(0, 1, "mean", "red")], xlabel="z")
    lesson(ch, "distributions", "Binomial and normal distributions",
        ["Use the binomial distribution $B(n,p)$", "Standardise a normal variable and use tables", "Solve practical problems"],
        [("formule", "Binomial $B(n,p)$", "Used when there are $n$ independent trials, each with two outcomes and constant success probability $p$. $P(X=r)=C(n,r)p^r(1-p)^{n-r}$. Mean $np$, variance $np(1-p)$."),
         ("formule", "Normal $N(\\mu,\\sigma^2)$", "A symmetric bell-shaped curve centred on $\\mu$. Standardise: $Z=\\frac{X-\\mu}{\\sigma}$, which follows $N(0,1)$. Use $\\Phi(z)=P(Z<z)$ from tables. Symmetry: $\\Phi(-z)=1-\\Phi(z)$; $P(Z>z)=1-\\Phi(z)$."),
         ("propriete", "Table values used here", "$\\Phi(1)=0.8413$, $\\Phi(1.5)=0.9332$, $\\Phi(1.96)=0.9750$, $\\Phi(2)=0.9772$. About 68% of a normal distribution lies within $1\\sigma$ of the mean, 95% within $1.96\\sigma$."),
         ("pieges", "Common mistakes", "- Using the binomial when trials are not independent or $p$ changes.\n- Forgetting to divide by $\\sigma$ (not $\\sigma^2$) when standardising: if $N(55,10^2)$ then $\\sigma=10$.\n- For $P(Z>z)$, subtract the table value from 1.")],
        [("Example 1", "$X\\sim B(10,0.3)$. Find $P(X=3)$.", ["$P=C(10,3)(0.3)^3(0.7)^7$ with $C(10,3)=120$.", "$=120\\times0.027\\times0.08235=0.2668$."], "$P(X=3)\\approx0.267$"),
         ("Example 2", "Heights of adults in a village are $N(170,8^2)$ cm. Find $P(X>182)$.", ["$z=\\frac{182-170}{8}=1.5$.", "$P(Z>1.5)=1-\\Phi(1.5)=1-0.9332$."], "$0.0668$", bell)],
        [N("Find the mean of $B(20,0.25)$.", 5, "$np=20\\times0.25$."),
         M("$X\\sim B(5,0.2)$. $P(X=0)=$", "0.32768", ["0.2", "0.00032", "0.8"], "$(0.8)^5$."),
         TF("A normal distribution is symmetric about its mean.", True, "The bell curve mirrors about $x=\\mu$.")],
        [M("Exam scores are $N(62,6^2)$. The $z$-value of a score of 74 is", "2", ["12", "0.5", "6"], "$\\frac{74-62}{6}=2$."),
         N("$X\\sim B(8,0.5)$. Find $P(X\\geq7)$ (4 d.p.).", round(9 / 256, 4), "$P(7)+P(8)=\\frac{8}{256}+\\frac{1}{256}=\\frac{9}{256}=0.0352$.", tol=0.0002)],
        P("Marks in a school examination are normally distributed with mean 55 and standard deviation 10.", [pn("Find the proportion of students scoring more than 65 (4 d.p.).", round(1 - Phi(1), 4), "$z=1$: $1-0.8413=0.1587$.", tol=0.0003, pts=2), pn("Find $P(45<X<65)$ (4 d.p.).", round(2 * Phi(1) - 1, 4), "$z$ between $-1$ and $1$: $2\\Phi(1)-1=0.6826$ (0.6827 with more accurate tables).", tol=0.0003, pts=2), pn("Find the mark exceeded by only 2.5% of students (1 d.p.).", 74.6, "$\\Phi(z)=0.975$ gives $z=1.96$: $55+1.96\\times10=74.6$.", tol=0.1, pts=1)]),
        [("In $B(n,p)$ the mean is", "$np$", ["$n+p$", "$\\frac{n}{p}$", "$np(1-p)$"], "Definition."),
         ("$X\\sim N(100,25)$ means $\\sigma=$", "5", ["25", "10", "100"], "Variance 25."),
         ("$P(Z<0)$ equals", "0.5", ["0", "1", "0.25"], "Symmetry."),
         ("$P(Z>1.96)=$", "0.025", ["0.975", "0.05", "0.5"], "$1-0.975$."),
         ("A binomial model needs trials that are", "independent", ["dependent", "three or more outcomes", "without a fixed $n$"], "Conditions.")],
        ill=None, notes=["Table values are computed from the normal CDF and rounded to 4 d.p.; if the Board's tables differ in the last digit, adjust tolerances.", "Normal approximation to the binomial and continuity corrections are not included; Poisson is in the Upper Sixth pack."], ref=REF.replace("Pure Mathematics", "Statistics"))
