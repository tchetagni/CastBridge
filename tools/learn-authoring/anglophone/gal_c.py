import math
from math import pi, sin, cos, tan, exp, log, sqrt, atan, degrees, radians, comb, erf, factorial
from _maths import *
from gal_a import D, I, close
Phi = lambda z: .5 * (1 + erf(z / sqrt(2)))
MREF = "Cameroon GCE Board — Advanced Level Mathematics, Mechanics (Upper Sixth part; to be checked against the official syllabus)"
SREF = "Cameroon GCE Board — Advanced Level Mathematics, Statistics (Upper Sixth part; to be checked against the official syllabus)"

def build(p):
    ch = p.chapter("mechanics", "Mechanics", MREF)
    # ------------------------------------------------------------ projectiles
    g = 10
    u, al = 20, radians(30); T1 = 2 * u * sin(al) / g; R1 = u * cos(al) * T1; H1 = (u * sin(al)) ** 2 / (2 * g)
    close(T1, 2); close(R1, 34.641, 1e-3); close(H1, 5); close(sqrt(2 * 45 / g), 3); assert 12 * 3 == 36 and sqrt(2 * 20 / g) == 2
    close(30 ** 2 * sin(pi / 2) / g, 90)
    vx, vy = 20, 15; assert vx ** 2 + vy ** 2 == 625 and 2 * vy / g == 3 and vx * 3 == 60 and vy ** 2 / (2 * g) == 11.25
    pa = Axes(-2, 38, -1, 7, w=400, h=200, xticks=[10, 20, 30], yticks=[5]).param(lambda t: u * cos(al) * t, lambda t: u * sin(al) * t - 5 * t * t, 0, 2, n=60)
    pa.pt(u * cos(al) * 1, H1, "max 5 m", dy=-10).pt(R1, 0, "range 34.6 m", dy=-10, dx=-10, anchor="middle")
    lesson(ch, "projectiles", "Projectiles",
        ["Resolve the initial velocity into horizontal and vertical components", "Find time of flight, range and greatest height", "Use the trajectory equation"],
        [("propriete", "Model", "Neglect air resistance. Horizontally the velocity is constant; vertically the acceleration is $g$ downwards ($g=10$ m/s² here). For launch speed $u$ at angle $\\alpha$ above the horizontal: $u_x=u\\cos\\alpha$, $u_y=u\\sin\\alpha$."),
         ("formule", "Equations of motion", "At time $t$: $x=u\\cos\\alpha\\,t$, $y=u\\sin\\alpha\\,t-\\frac12gt^2$; $v_x=u\\cos\\alpha$, $v_y=u\\sin\\alpha-gt$. Landing at the same level: time of flight $T=\\frac{2u\\sin\\alpha}{g}$, range $R=\\frac{u^2\\sin2\\alpha}{g}$, greatest height $H=\\frac{u^2\\sin^2\\alpha}{2g}$ (when $v_y=0$)."),
         ("formule", "Trajectory", "Eliminating $t$: $y=x\\tan\\alpha-\\frac{gx^2}{2u^2\\cos^2\\alpha}$, a parabola. The speed at any time is $\\sqrt{v_x^2+v_y^2}$ and the angle with the horizontal is $\\tan^{-1}\\frac{|v_y|}{v_x}$."),
         ("pieges", "Common mistakes", "- At the highest point the velocity is NOT zero: only the vertical component is.\n- Use the vertical motion to find the time and then the horizontal motion for the distance.\n- Do not use $R=\\frac{u^2\\sin2\\alpha}{g}$ when the landing point is at a different height.")],
        [("Example 1", "A ball is hit from the ground at 20 m/s at $30\\degree$ to the horizontal ($g=10$). Find the time of flight, the range and the greatest height.", ["$u_y=10$, $u_x=17.32$. Flight time: $y=0$: $10t-5t^2=0$, $t=2$ s.", "Range $=17.32\\times2=34.6$ m.", "Greatest height: $v_y=0$ at $t=1$: $H=10(1)-5(1)^2=5$ m."], "$T=2$ s, $R\\approx34.6$ m, $H=5$ m", pa),
         ("Example 2", "A stone is thrown horizontally at 12 m/s from a cliff 45 m high. How far from the base does it land?", ["Vertical: $45=\\frac12(10)t^2$, so $t^2=9$, $t=3$ s.", "Horizontal distance $=12\\times3=36$ m."], "36 m")],
        [N("How long does an object take to fall 20 m from rest ($g=10$)?", 2, "$20=5t^2$, so $t=2$ s.", unit="s"),
         M("Range for $u=30$ m/s at $45\\degree$ ($g=10$):", "90 m", ["45 m", "180 m", "30 m"], "$\\frac{u^2\\sin90\\degree}{g}=\\frac{900}{10}$."),
         TF("At the highest point of its flight a projectile has zero velocity.", False, "The horizontal component $u\\cos\\alpha$ remains; only the vertical component is zero.")],
        [M("During the flight of a projectile the horizontal component of velocity", "stays constant", ["increases", "decreases", "is zero at the top"], "No horizontal force."),
         O("Show that a projectile launched from the ground with speed $u$ at angle $\\alpha$ has time of flight $\\frac{2u\\sin\\alpha}{g}$.", "Vertical displacement $y=u\\sin\\alpha\\,t-\\frac12gt^2$. It returns to the ground when $y=0$: $t\\left(u\\sin\\alpha-\\frac12gt\\right)=0$. The non-zero solution is $t=\\frac{2u\\sin\\alpha}{g}$.", ["Vertical equation (1)", "Sets $y=0$ and factorises (1)", "Correct time (1)"])],
        P("A ball is kicked from the ground at 25 m/s at an angle $\\alpha$ with $\\sin\\alpha=0.6$ ($\\cos\\alpha=0.8$). Take $g=10$.", [pn("Find the time of flight (s).", 3, "$u_y=15$: $T=\\frac{2\\times15}{10}=3$.", pts=1), pn("Find the range (m).", 60, "$u_x=20$: $20\\times3=60$.", pts=1), pn("Find the greatest height (m).", 11.25, "$H=\\frac{15^2}{20}=11.25$.", tol=0.01, pts=2)]),
        [("In projectile motion the horizontal acceleration is", "0", ["$g$", "$-g$", "$u$"], "No horizontal force."),
         ("$u=20$ at $\\alpha=90\\degree$ reaches greatest height ($g=10$)", "20 m", ["10 m", "40 m", "0"], "$\\frac{400}{20}$."),
         ("Maximum range on level ground occurs at", "$45\\degree$", ["$30\\degree$", "$60\\degree$", "$90\\degree$"], "$\\sin2\\alpha=1$."),
         ("A stone dropped and one thrown horizontally from the same height land", "at the same time", ["the dropped one first", "the thrown one first", "never"], "Same vertical motion."),
         ("The trajectory of a projectile is a", "parabola", ["circle", "straight line", "hyperbola"], "Quadratic in $x$.")],
        ill=None, notes=["g=10 m/s² used (check whether the Board uses 9.8)."], ref=MREF)

    # ------------------------------------------------------------ moments
    assert 300 * 1 + 200 * 2 == 700 and 700 / 4 == 175 and 500 - 175 == 325 and 40 * 1.5 / 30 == 2 and 25 * .4 == 10
    assert 120 * 1 / 2 == 60 and (240 * 3 + 80 * 6) / 4 == 300 and 320 - 300 == 20 and (300 * 2 + 400 * 1) / 4 == 250 and 700 - 250 == 450
    bm = shapes([RECT(40, 100, 320, 14, fill="lightgrey", stroke="ink"), POLY([40, 114, 28, 140, 52, 140], fill="orange", stroke="ink"), POLY([360, 114, 348, 140, 372, 140], fill="orange", stroke="ink"),
                 LINE(110, 60, 110, 98, color="red", width=3, arrow="end"), T(110, 52, "300 N", 12), LINE(200, 150, 200, 116, color="blue", width=1, arrow="none"),
                 LINE(200, 62, 200, 98, color="green", width=3, arrow="end"), T(200, 52, "200 N", 12),
                 LINE(40, 190, 40, 156, color="grey", width=3, arrow="end"), T(40, 205, "R_A", 12), LINE(360, 190, 360, 156, color="grey", width=3, arrow="end"), T(360, 205, "R_B", 12),
                 T(110, 175, "1 m", 12), T(250, 175, "beam 4 m", 12)], 400, 230)
    lesson(ch, "moments", "Moments and equilibrium",
        ["Calculate the moment of a force about a point", "Use the two conditions for equilibrium", "Solve beam, seesaw and pivot problems"],
        [("definition", "Moment", "The moment of a force $F$ about a point is $F\\times d$, where $d$ is the perpendicular distance from the point to the line of action of the force. Unit: N m. Its sense is clockwise or anticlockwise."),
         ("propriete", "Equilibrium of a rigid body", "A body is in equilibrium when (1) the resultant force is zero (upward forces = downward forces) and (2) the total moment about any point is zero (clockwise = anticlockwise). Take moments about a point where an unknown force acts to remove it from the equation."),
         ("methode", "Method", "1. Draw a diagram with all forces (weight of a uniform beam acts at its midpoint). 2. Choose a point to take moments about. 3. Sum clockwise moments = sum anticlockwise moments. 4. Resolve vertically to find the other reaction. 5. Check that both reactions are positive (a negative reaction means the beam lifts off that support)."),
         ("pieges", "Common mistakes", "- Using the distance along the beam when the force is inclined: you need the PERPENDICULAR distance.\n- Putting the weight at the end of a uniform beam instead of the middle.\n- Forgetting the weight of the beam itself.")],
        [("Example 1", "A uniform beam $AB$ of length 4 m and weight 200 N rests on supports at its ends. A child of weight 300 N stands 1 m from $A$. Find the reactions at $A$ and $B$.", ["Moments about $A$: $R_B\\times4=300\\times1+200\\times2=700$, so $R_B=175$ N.", "Vertically: $R_A+R_B=500$, so $R_A=325$ N."], "$R_A=325$ N, $R_B=175$ N", bm),
         ("Example 2", "A 40 kg child sits 1.5 m from the pivot of a seesaw. Where must a 30 kg child sit on the other side to balance it?", ["Moments about the pivot: $40g\\times1.5=30g\\times d$.", "$d=\\frac{40\\times1.5}{30}$ (the $g$ cancels)."], "$d=2$ m")],
        [N("Find the moment (N m) of a 25 N force acting 0.4 m from a pivot.", 10, "$25\\times0.4$.", unit="N m"),
         M("The unit of moment is", "N m", ["N", "N/m", "J/s"], "Force times distance."),
         TF("The moment of a force depends only on the size of the force.", False, "It also depends on the perpendicular distance from the pivot.")],
        [M("A uniform plank $AB$ of length 6 m and weight 120 N is pivoted at a point 2 m from $A$. The downward force $F$ at $A$ needed to hold it horizontal is", "60 N", ["120 N", "30 N", "40 N"], "Weight acts 1 m from the pivot: $120\\times1=F\\times2$."),
         O("A uniform beam $AB$ of length 6 m and weight 240 N is supported at $A$ and at $C$, where $AC=4$ m. A load of 80 N hangs at $B$. Find the reactions at $A$ and $C$.", "Moments about $A$: $R_C\\times4=240\\times3+80\\times6=1200$, so $R_C=300$ N. Vertically $R_A+R_C=320$, so $R_A=20$ N (positive, so the beam does not lift).", ["Moments about $A$ (1)", "$R_C=300$ N (1)", "$R_A=20$ N (1)"])],
        P("A uniform plank $AB$ of length 6 m and weight 300 N rests on supports $C$ (1 m from $A$) and $D$ (1 m from $B$). A load of 400 N is placed on the plank 2 m from $A$.", [pn("Find the reaction at $D$ (N).", 250, "Moments about $C$: $R_D\\times4=300\\times2+400\\times1=1000$, $R_D=250$.", pts=2), pn("Find the reaction at $C$ (N).", 450, "$R_C=700-250=450$.", pts=1), pt("The plank would remain in equilibrium if the load were moved to the point directly above $D$.", True, "The reactions would change but both stay positive: $R_C\\approx150$ N and $R_D\\approx550$ N.", pts=1)]),
        [("A uniform beam's weight acts at", "its midpoint", ["one end", "a support", "its heaviest point"], "Centre of mass."),
         ("In equilibrium the total clockwise moment is", "equal to the total anticlockwise moment", ["zero always", "double the anticlockwise", "unrelated"], "Moment condition."),
         ("A 10 N force at 3 m gives a moment of", "30 N m", ["13 N m", "3.3 N m", "7 N m"], "$10\\times3$."),
         ("To eliminate an unknown reaction take moments about", "the point where it acts", ["the centre of the beam always", "any point at random", "infinity"], "Its moment is zero."),
         ("Two conditions for equilibrium are", "zero resultant force and zero resultant moment", ["equal masses", "zero speed only", "equal distances"], "Standard result.")],
        ill=None, notes=["Ladder and inclined-force problems (friction at wall and ground) not included; check if required."], ref=MREF)

    # ------------------------------------------------------------ energy & momentum
    assert 2 * 10 * 5 == 100 and sqrt(2 * 10 * 5) == 10 and 3 * 4 / 4 == 3 and .5 * 3 * 16 == 24 and .5 * 4 * 9 == 18
    assert 1500 * 20 == 30000 and .5 * 1200 * 15 ** 2 == 135000 and .5 * (12 + 8) == 10
    assert (200 - 50) * 15 == 2250 and abs(sqrt(90) - 9.4868) < 1e-4
    assert 4000 * 6 / 5000 == 4.8 and .5 * 4000 * 36 == 72000 and abs(.5 * 5000 * 4.8 ** 2 - 57600) < 1e-6
    col = shapes([RECT(40, 40, 60, 40, fill="lightgrey"), T(70, 64, "4000 kg", 12), LINE(40, 100, 100, 100, color="blue", width=3, arrow="end"), T(70, 120, "6 m/s", 12),
                  RECT(180, 50, 40, 30, fill="lightgrey"), T(200, 70, "1000 kg", 12), T(200, 100, "at rest", 12),
                  T(200, 140, "before", 13), RECT(150, 170, 100, 40, fill="orange"), T(200, 195, "5000 kg", 12), LINE(250, 190, 330, 190, color="blue", width=3, arrow="end"), T(300, 175, "4.8 m/s", 12)], 400, 240)
    lesson(ch, "energy-momentum", "Work, energy, power and momentum",
        ["Use work, kinetic and potential energy and the work-energy principle", "Use power $P=Fv$", "Use conservation of momentum and impulse"],
        [("formule", "Energy and power", "Work done by a constant force $=Fd$ (in the direction of the force), in joules. Kinetic energy $=\\frac12mv^2$; gravitational potential energy $=mgh$. Work-energy principle: work done by the resultant force $=$ change in kinetic energy. Power $=\\frac{\\text{work}}{\\text{time}}=Fv$, in watts."),
         ("formule", "Momentum", "Momentum $p=mv$ (kg m/s). **Conservation**: in a collision with no external force, total momentum before = total momentum after. Impulse $=Ft=$ change in momentum $=mv-mu$ (N s). Kinetic energy is NOT conserved in an inelastic collision (energy is lost as heat and sound)."),
         ("methode", "Method", "For motion under gravity without friction use $\\frac12mv^2+mgh=$ constant. With friction: work by friction $=$ friction force $\\times$ distance reduces the energy. For collisions: choose a positive direction, write $m_1u_1+m_2u_2=m_1v_1+m_2v_2$, then compare kinetic energies if asked."),
         ("pieges", "Common mistakes", "Momentum is a vector: a rebound reverses the sign (impulse on a ball bouncing off a wall is $m(v_1+v_2)$). Do not use $F=\\frac{\\Delta KE}{t}$ for power. Check units: J, W, N s.")],
        [("Example 1", "A 2 kg ball is dropped from 5 m ($g=10$). Find its speed on impact and its kinetic energy.", ["Energy conservation: $mgh=\\frac12mv^2$, so $v^2=2gh=100$ and $v=10$ m/s.", "$KE=\\frac12(2)(100)=100$ J (equal to the lost potential energy $2\\times10\\times5$)."], "$v=10$ m/s, $KE=100$ J"),
         ("Example 2", "A 3 kg trolley at 4 m/s hits a stationary 1 kg trolley and they stick together. Find the common speed and the kinetic energy lost.", ["Momentum: $3\\times4=(3+1)v$, so $v=3$ m/s.", "KE before $=\\frac12(3)(16)=24$ J; after $=\\frac12(4)(9)=18$ J; lost $=6$ J."], "$v=3$ m/s; 6 J lost", col)],
        [N("Find the kinetic energy (J) of a 1200 kg car moving at 15 m/s.", 135000, "$\\frac12\\times1200\\times225$.", unit="J"),
         M("A car moves at constant 20 m/s with a driving force of 1500 N. The power developed is", "30 kW", ["75 W", "300 W", "3 kW"], "$P=Fv=1500\\times20=30000$ W."),
         TF("Momentum is conserved in a collision (no external forces) but kinetic energy need not be.", True, "In an inelastic collision some kinetic energy is converted to other forms.")],
        [M("A 0.5 kg ball hits a wall at 12 m/s and rebounds at 8 m/s. The impulse on the ball is", "10 N s", ["2 N s", "6 N s", "4 N s"], "$m(v+u)$ with direction reversed: $0.5(8+12)=10$."),
         N("A 50 kg trolley starts from rest and is pushed by a constant 200 N force for 15 m against a constant 50 N resistance. Find its final speed (m/s, 1 d.p.).", 9.5, "Net work $=(200-50)\\times15=2250$ J $=\\frac12(50)v^2$, so $v^2=90$, $v=9.49$.", tol=0.05, unit="m/s")],
        P("A truck of mass 4000 kg moving at 6 m/s collides with a stationary car of mass 1000 kg on a straight road near Bafoussam. They move together after impact.", [pn("Find their common speed (m/s).", 4.8, "$4000\\times6=5000v$, so $v=4.8$.", tol=0.01, pts=2), pn("Find the kinetic energy lost (J).", 14400, "Before: $\\frac12(4000)(36)=72000$ J; after: $\\frac12(5000)(23.04)=57600$ J; lost $=14400$ J.", tol=1, pts=2)], figure=col),
        [("The unit of power is", "the watt", ["the joule", "the newton", "the pascal"], "J/s."),
         ("$mgh$ for 2 kg at 3 m ($g=10$) is", "60 J", ["6 J", "30 J", "0.6 J"], "$2\\times10\\times3$."),
         ("Impulse equals", "change in momentum", ["change in energy", "force $\\times$ distance", "mass $\\times$ acceleration"], "$Ft=mv-mu$."),
         ("In an elastic collision", "kinetic energy is conserved", ["momentum is lost", "speed is constant", "mass changes"], "Definition."),
         ("Work done by a 20 N force over 5 m is", "100 J", ["25 J", "4 J", "15 J"], "$Fd$.")],
        ill=None, notes=["Coefficient of restitution and oblique impacts not included."], ref=MREF)

    # ------------------------------------------------------------ circular motion
    assert 16 / .8 == 20 and .5 * 20 == 10 and abs(sqrt(.4 * 10 * 50) - 14.142) < 1e-3 and 3 * 2 == 6 and abs(2 * pi / 2 - pi) < 1e-12
    w = 3 * 2 * pi; close(.2 * 1.2 * w ** 2, 85.27, 1e-2)
    w2 = 90 * 2 * pi / 60; close(w2, 9.4248, 1e-3); close(w2 * .4, 3.77, 1e-2); close(w2 ** 2 * .4, 35.53, 1e-2)
    cm = shapes([CIRCLE(190, 130, 70, stroke="blue"), CIRCLE(190, 130, 4, fill="ink"), LINE(190, 130, 260, 130, color="grey", width=1), T(225, 124, "r", 13),
                 CIRCLE(190, 60, 6, fill="red", stroke="red"), LINE(190, 60, 270, 60, color="green", width=3, arrow="end"), T(280, 58, "v", 14, anchor="start"),
                 LINE(190, 60, 190, 100, color="orange", width=3, arrow="end"), T(168, 92, "a", 14)], 400, 220)
    lesson(ch, "circular-motion", "Motion in a horizontal circle",
        ["Use angular speed $\\omega$, speed $v=r\\omega$ and period", "Use the centripetal acceleration $\\frac{v^2}{r}$ and force $\\frac{mv^2}{r}$", "Solve problems with tension and friction"],
        [("formule", "Angular motion", "A particle moving in a circle of radius $r$ with angular speed $\\omega$ (rad/s): $v=r\\omega$. The period $T=\\frac{2\\pi}{\\omega}$ and $\\omega=2\\pi f$ where $f$ is the frequency (rev/s). Convert revolutions per minute: $\\omega=\\text{rpm}\\times\\frac{2\\pi}{60}$."),
         ("propriete", "Acceleration towards the centre", "Even at constant speed the velocity changes direction, so there is an acceleration $a=\\frac{v^2}{r}=r\\omega^2$ directed towards the centre. The resultant force towards the centre is $F=\\frac{mv^2}{r}=mr\\omega^2$ (provided by tension, friction, a reaction...). It is not an extra force."),
         ("methode", "Method", "1. Identify the real forces (tension, friction, weight, reaction) that point towards the centre. 2. Write $F_{towards\;centre}=\\frac{mv^2}{r}$. 3. Resolve vertically when needed (balanced). 4. For a car on a flat bend friction gives $\\mu mg\\geq\\frac{mv^2}{r}$, so the maximum speed satisfies $v^2=\\mu gr$."),
         ("pieges", "Common mistakes", "There is no 'centrifugal force' pushing outwards in the equation of motion. Uniform circular motion has acceleration (not zero). Use radians per second, not revolutions per minute, in $v=r\\omega$.")],
        [("Example 1", "A 0.5 kg stone is whirled in a horizontal circle of radius 0.8 m at 4 m/s on a light string. Find the acceleration and the tension.", ["$a=\\frac{v^2}{r}=\\frac{16}{0.8}=20$ m/s².", "Tension provides the centripetal force: $T=ma=0.5\\times20=10$ N."], "$a=20$ m/s²; $T=10$ N", cm),
         ("Example 2", "A car goes round a flat bend of radius 50 m. The coefficient of friction is 0.4. Find the maximum speed ($g=10$).", ["Friction $\\mu mg=\\frac{mv^2}{r}$, so $v^2=\\mu gr=0.4\\times10\\times50=200$.", "$v=\\sqrt{200}=14.1$ m/s (about 51 km/h)."], "$v\\approx14.1$ m/s")],
        [N("A particle moves at $\\omega=3$ rad/s on a circle of radius 2 m. Find its speed (m/s).", 6, "$v=r\\omega=2\\times3$.", unit="m/s"),
         M("The acceleration of a particle in uniform circular motion is directed", "towards the centre", ["along the tangent", "away from the centre", "it has no acceleration"], "Velocity changes direction."),
         TF("A particle moving at constant speed in a circle has zero acceleration.", False, "Its velocity direction changes, so $a=\\frac{v^2}{r}\\neq0$.")],
        [M("The period of a particle with $\\omega=2$ rad/s is", "$\\pi$ s", ["$2\\pi$ s", "$\\frac{\\pi}{2}$ s", "$4\\pi$ s"], "$T=\\frac{2\\pi}{\\omega}$."),
         N("A 0.2 kg stone on a light string of length 1.2 m moves in a horizontal circle at 3 revolutions per second. Find the tension (N, nearest whole number).", 85, "$\\omega=6\\pi$ rad/s; $T=mr\\omega^2=0.2\\times1.2\\times(6\\pi)^2=85.3$.", tol=1, unit="N")],
        P("A potter's wheel rotates at 90 revolutions per minute. A point on its rim is 0.4 m from the centre.", [pn("Find the angular speed (rad/s, 2 d.p.).", 9.42, "$90\\times\\frac{2\\pi}{60}=3\\pi=9.42$.", tol=0.01, pts=1), pn("Find the speed of the point (m/s, 2 d.p.).", 3.77, "$v=r\\omega=0.4\\times9.42$.", tol=0.01, pts=1), pn("Find its acceleration (m/s², 1 d.p.).", 35.5, "$r\\omega^2=0.4\\times88.83=35.5$.", tol=0.1, pts=2)]),
        [("$v=r\\omega$: $r=3$, $\\omega=4$ gives $v=$", "12 m/s", ["7 m/s", "0.75 m/s", "48 m/s"], "Multiply."),
         ("Centripetal force formula is", "$\\frac{mv^2}{r}$", ["$mvr$", "$\\frac{mr}{v^2}$", "$mv^2r$"], "Newton's second law."),
         ("$\\omega$ for 60 rpm is", "$2\\pi$ rad/s", ["$60$ rad/s", "$\\pi$ rad/s", "$120$ rad/s"], "One revolution per second."),
         ("For a car on a flat bend the centripetal force is provided by", "friction", ["the engine", "the weight", "the air"], "Horizontal force."),
         ("If the speed doubles at the same radius the centripetal force is", "four times", ["doubled", "halved", "unchanged"], "$v^2$.")],
        ill=None, notes=["Vertical circles and banked tracks not included."], ref=MREF)

    # ------------------------------------------------------------ STATS
    ch = p.chapter("statistics", "Statistics", SREF)
    pois = lambda lam, r: exp(-lam) * lam ** r / factorial(r)
    close(pois(3, 2), .2240, 1e-4); close(pois(2.5, 0), .0821, 1e-4); close(1 - exp(-2.5) * 3.5, .7127, 1e-4); close(pois(1.2, 0), .3012, 1e-4)
    close(pois(4, 2), .1465, 1e-4); close(1 - exp(-4), .9817, 1e-4); close(exp(-3), .0498, 1e-4); close(1 - exp(-3) * 8.5, .5768, 1e-4)
    pr = [.1, .3, .4, .2]; EX = sum((i + 1) * q for i, q in enumerate(pr)); EX2 = sum((i + 1) ** 2 * q for i, q in enumerate(pr)); close(EX, 2.7); close(EX2, 8.1); close(EX2 - EX ** 2, .81)
    pbars = bars([("%d" % r, round(100 * pois(3, r), 1)) for r in range(9)], unit="%")
    lesson(ch, "poisson", "Discrete random variables and the Poisson distribution",
        ["Calculate $E(X)$ and $\\text{Var}(X)$ of a discrete random variable", "Use the Poisson distribution", "Model rare events in time or space"],
        [("formule", "Discrete random variable", "A discrete random variable $X$ with probabilities $p_i$ ($\\sum p_i=1$): $E(X)=\\sum x\\,p$, $E(X^2)=\\sum x^2p$ and $\\text{Var}(X)=E(X^2)-[E(X)]^2$. Also $E(aX+b)=aE(X)+b$ and $\\text{Var}(aX+b)=a^2\\text{Var}(X)$."),
         ("formule", "Poisson distribution", "$X\\sim\\text{Po}(\\lambda)$ models the number of events in a fixed interval when events occur singly, independently, at a constant average rate. $P(X=r)=\\frac{e^{-\\lambda}\\lambda^r}{r!}$ for $r=0,1,2,\\dots$ Mean $=$ variance $=\\lambda$. Over a longer interval multiply $\\lambda$ (e.g. 2 hours: $2\\lambda$)."),
         ("methode", "Method", "1. Identify $\\lambda$ for the interval asked. 2. Use $P(X=r)$ directly for exact values; for 'at least' use the complement: $P(X\\geq k)=1-P(X\\leq k-1)$. 3. State the answer to 3 or 4 decimal places."),
         ("pieges", "Common mistakes", "Forgetting to rescale $\\lambda$ when the interval changes. $P(X\\geq1)=1-P(X=0)$, not $1-P(X=1)$. The Poisson model fails if events cluster or happen simultaneously.")],
        [("Example 1", "$X$ takes values 1, 2, 3, 4 with probabilities 0.1, 0.3, 0.4, 0.2. Find $E(X)$ and $\\text{Var}(X)$.", ["$E(X)=0.1+0.6+1.2+0.8=2.7$.", "$E(X^2)=0.1+1.2+3.6+3.2=8.1$; $\\text{Var}=8.1-2.7^2=8.1-7.29=0.81$."], "$E(X)=2.7$, $\\text{Var}(X)=0.81$"),
         ("Example 2", "Calls to a call centre arrive at an average of 2.5 per minute ($\\text{Po}(2.5)$). Find $P(X=0)$ and $P(X\\geq2)$.", ["$P(0)=e^{-2.5}=0.0821$.", "$P(X\\geq2)=1-P(0)-P(1)=1-e^{-2.5}(1+2.5)=1-0.2873=0.7127$."], "0.0821 and 0.7127", pbars)],
        [N("$X\\sim\\text{Po}(3)$. Find $P(X=2)$ (4 d.p.).", .224, "$\\frac{e^{-3}3^2}{2}=4.5e^{-3}=0.2240$.", tol=0.0002),
         M("For a Poisson distribution with $\\lambda=4$ the variance is", "4", ["2", "16", "$\\sqrt4$"], "Mean = variance."),
         TF("For a Poisson distribution the mean equals the variance.", True, "Both equal $\\lambda$.")],
        [M("$X\\sim\\text{Po}(1.2)$. $P(X=0)=$", "0.3012", ["0.2", "0.8", "0.6988"], "$e^{-1.2}$. (0.6988 is $P(X\\geq1)$.)"),
         O("Accidents at a busy junction in Bamenda occur at an average of 4 per month. Find the probability of exactly 2 accidents in a month, and of at least 1.", "$X\\sim\\text{Po}(4)$. $P(X=2)=\\frac{e^{-4}\\,4^2}{2}=8e^{-4}=0.1465$. $P(X\\geq1)=1-e^{-4}=0.9817$.", ["Identifies $\\text{Po}(4)$ (1)", "$P(X=2)=0.1465$ (1)", "$P(X\\geq1)=0.9817$ (1)"])],
        P("Emergencies arrive at a clinic in Limbe at an average rate of 3 per hour, modelled by a Poisson distribution.", [pn("Find the probability that there are no emergencies in a given hour (4 d.p.).", .0498, "$e^{-3}=0.0498$.", tol=0.0002, pts=1), pn("Find $P(X\\geq3)$ in an hour (4 d.p.).", .5768, "$1-e^{-3}(1+3+4.5)=1-0.4232=0.5768$.", tol=0.0003, pts=2), pn("What is the mean number of emergencies in 2 hours?", 6, "$\\lambda$ doubles for a doubled interval.", pts=1)]),
        [("$E(X)$ is computed as", "$\\sum xp$", ["$\\sum p$", "$\\sum x^2$", "$\\frac{\\sum x}{n}$ only"], "Weighted mean."),
         ("$\\text{Var}(X)=$", "$E(X^2)-[E(X)]^2$", ["$E(X)^2-E(X^2)$", "$E(X)$", "$\\sum p$"], "Standard formula."),
         ("$\\text{Var}(2X+3)=$", "$4\\text{Var}(X)$", ["$2\\text{Var}(X)+3$", "$4\\text{Var}(X)+3$", "$2\\text{Var}(X)$"], "$a^2$, constant dropped."),
         ("Poisson $\\lambda$ for 3 minutes when the rate is 2 per minute is", "6", ["2", "3", "$\\frac23$"], "Scale."),
         ("$P(X\\geq1)$ for a Poisson equals", "$1-P(X=0)$", ["$1-P(X=1)$", "$P(X=0)$", "$P(X=1)$"], "Complement.")],
        ill=None, notes=["Poisson approximation to the binomial and sum of independent Poissons only briefly mentioned."], ref=SREF)

    # hypothesis testing
    p10 = (comb(12, 10) + comb(12, 11) + comb(12, 12)) / 4096; close(p10, .0193, 1e-4)
    z2 = (49.2 - 50) / (2 / 5); close(z2, -2); close(Phi(-2), .0228, 1e-4); close((52 - 50) / (6 / 6), 2)
    assert comb(10, 8) + comb(10, 9) + comb(10, 10) == 56 and 56 / 1024 > .05
    b15 = lambda c: sum(comb(15, k) * .8 ** k * .2 ** (15 - k) for k in range(c + 1)); close(b15(9), .0611, 1e-4); close(b15(8), .0181, 1e-4)
    hbell = plot(-3.5, 3.5, -0.1, 1.15, curves=[("exp(-x^2/2)", "blue", None)], segments=[(-1.645, 0, -1.645, 0.2585, "red", True, "critical z = -1.645")], points=[(-2, 0.1353, "z = -2", "green")], xlabel="z")
    lesson(ch, "hypothesis-testing", "Hypothesis testing",
        ["State null and alternative hypotheses", "Carry out one-tailed and two-tailed tests for a binomial proportion and a normal mean", "Interpret the conclusion in context"],
        [("definition", "Hypotheses and significance", "The **null hypothesis** $H_0$ is the claim being tested (e.g. $p=0.5$ or $\\mu=50$); the **alternative** $H_1$ says $p>0.5$ (one-tailed), $p<0.5$ (one-tailed) or $p\\neq0.5$ (two-tailed). The **significance level** $\\alpha$ (often 5%) is the probability of rejecting $H_0$ when it is true (a Type I error)."),
         ("methode", "Binomial test", "Assume $H_0$ and compute the probability of the observed result or more extreme under $B(n,p_0)$. If this probability is less than $\\alpha$ (halve $\\alpha$ for two-tailed), reject $H_0$. The **critical region** is the set of values leading to rejection."),
         ("methode", "Test for a normal mean", "For a sample of size $n$ from a normal population with known $\\sigma$, under $H_0$ the sample mean is $N\\left(\\mu_0,\\frac{\\sigma^2}{n}\\right)$. Test statistic $z=\\frac{\\bar x-\\mu_0}{\\sigma/\\sqrt n}$. Critical values: 5% one-tailed $\\pm1.645$; 5% two-tailed $\\pm1.96$."),
         ("pieges", "Common mistakes", "Failing to reject $H_0$ does NOT prove $H_0$ true: there is insufficient evidence against it. Write conclusions in context (e.g. 'there is evidence that the mean mass is less than 50 kg'). Do not divide by $\\sigma^2$ or forget $\\sqrt n$.")],
        [("Example 1", "A coin is tossed 12 times and shows 10 heads. Test at 5% whether it is biased towards heads.", ["$H_0:p=0.5$, $H_1:p>0.5$. Under $H_0$, $X\\sim B(12,0.5)$.", "$P(X\\geq10)=\\frac{66+12+1}{4096}=0.0193<0.05$: reject $H_0$; there is evidence of bias towards heads."], "Reject $H_0$ (p = 0.0193)"),
         ("Example 2", "Bags of maize flour are labelled 50 kg with $\\sigma=2$ kg. A sample of 25 has mean 49.2 kg. Test at 5% whether the mean is less than 50 kg.", ["$H_0:\\mu=50$, $H_1:\\mu<50$. $z=\\frac{49.2-50}{2/\\sqrt{25}}=\\frac{-0.8}{0.4}=-2$.", "$-2<-1.645$ (critical value): reject $H_0$; equivalently $P(Z<-2)=0.0228<0.05$."], "Reject $H_0$: evidence the mean is below 50 kg", hbell)],
        [N("Sample mean 52, $\\mu_0=50$, $\\sigma=6$, $n=36$. Find the test statistic $z$.", 2, "$\\frac{52-50}{6/6}=2$."),
         M("We reject $H_0$ when", "the probability of the observed result under $H_0$ is less than $\\alpha$", ["the probability is greater than $\\alpha$", "the sample mean equals $\\mu_0$", "$H_1$ is false"], "Decision rule."),
         TF("If we do not reject $H_0$ then we have proved $H_0$ is true.", False, "It only means there is insufficient evidence against it.")],
        [M("$X\\sim B(10,0.5)$. $P(X\\geq8)=0.0547$. At 5% (one-tailed) the conclusion is", "do not reject $H_0$", ["reject $H_0$", "accept $H_1$ proved", "the test is invalid"], "$0.0547>0.05$. ($45+10+1=56$; $56/1024=0.0547$.)"),
         O("Explain what a Type I error is, in the context of a 5% significance test of a new fertiliser.", "A Type I error is rejecting the null hypothesis ('the fertiliser has no effect') when it is actually true, i.e. concluding the fertiliser works when it does not. At a 5% significance level the probability of this error is 5%.", ["Defines rejecting a true $H_0$ (1)", "Links to the context (1)", "States probability 5% (1)"])],
        P("A seed supplier claims that 80% of its seeds germinate. A farmer in Kumba plants 15 seeds and 9 germinate. She tests at the 5% level whether the true proportion is lower than 80%, with $H_0:p=0.8$, $H_1:p<0.8$, and $X\\sim B(15,0.8)$ under $H_0$.", [pn("Find $P(X\\leq9)$ (4 d.p.).", .0611, "$P(X\\leq9)=0.0611$.", tol=0.0004, pts=2), pm("The conclusion is", "do not reject $H_0$: insufficient evidence that fewer than 80% germinate", ["reject $H_0$: the supplier is wrong", "reject $H_0$: all seeds fail", "accept that exactly 80% germinate"], "$0.0611>0.05$.", pts=2), pn("Find the largest value $c$ such that $P(X\\leq c)<0.05$ (the critical region is $X\\leq c$).", 8, "$P(X\\leq8)=0.0181<0.05$ but $P(X\\leq9)=0.0611>0.05$.", pts=1)]),
        [("$H_0$ is called the", "null hypothesis", ["alternative hypothesis", "critical value", "sample"], "Definition."),
         ("A two-tailed test at 5% uses critical values", "$\\pm1.96$", ["$\\pm1.645$", "$\\pm2.58$", "$\\pm1$"], "2.5% each tail."),
         ("The probability of a Type I error equals", "the significance level", ["$\\frac{1}{2}$", "the p-value of $H_1$", "zero"], "Definition."),
         ("The distribution of $\\bar X$ under $H_0$ is", "$N\\left(\\mu_0,\\frac{\\sigma^2}{n}\\right)$", ["$N(\\mu_0,\\sigma^2)$", "$N(0,1)$", "$N(\\mu_0,n\\sigma^2)$"], "Standard error $\\frac{\\sigma}{\\sqrt n}$."),
         ("$H_1:p\\neq0.5$ is", "two-tailed", ["one-tailed (upper)", "one-tailed (lower)", "a null hypothesis"], "Both directions.")],
        ill=None, notes=["Test for a Poisson mean and tests with unknown variance (t-test) not included."], ref=SREF)

    # correlation & regression
    x = [10, 20, 30, 40, 50]; y = [25, 38, 50, 66, 76]; mx, my = 30, 51
    sxx = sum((a - mx) ** 2 for a in x); sxy = sum((a - mx) * (b - my) for a, b in zip(x, y)); syy = sum((b - my) ** 2 for b in y); r = sxy / sqrt(sxx * syy)
    assert (sxx, sxy, syy) == (1000, 1300, 1696) and abs(r - .9982) < 1e-4 and 12 + 1.3 * 35 == 57.5
    xs = [1, 2, 3, 4, 5]; ys = [2, 4, 5, 4, 5]; assert sum((a - 3) * (b - 4) for a, b in zip(xs, ys)) == 6 and abs(6 / sqrt(10 * 6) - .7746) < 1e-4
    sc = Axes(0, 60, 0, 90, w=400, h=240, xticks=[10, 30, 50], yticks=[25, 50, 75])
    sc.line(5, 18.5, 55, 83.5, color="orange", width=2)
    for a, b in zip(x, y): sc.pt(a, b, color="blue", r=4)
    sc.text(50, 6, "advertising (thousand FCFA)", 11).text(38, 85, "sales (units)", 11, anchor="start")
    lesson(ch, "regression", "Correlation and regression",
        ["Calculate and interpret the product moment correlation coefficient $r$", "Find the least-squares regression line $y=a+bx$", "Use the line for prediction and comment on its reliability"],
        [("formule", "Summary statistics and $r$", "$S_{xx}=\\sum(x-\\bar x)^2$, $S_{yy}=\\sum(y-\\bar y)^2$, $S_{xy}=\\sum(x-\\bar x)(y-\\bar y)$. The correlation coefficient $r=\\frac{S_{xy}}{\\sqrt{S_{xx}S_{yy}}}$ satisfies $-1\\leq r\\leq1$: near $+1$ strong positive, near $-1$ strong negative, near 0 no linear correlation."),
         ("formule", "Regression line of $y$ on $x$", "$y=a+bx$ with gradient $b=\\frac{S_{xy}}{S_{xx}}$ and intercept $a=\\bar y-b\\bar x$. The line passes through $(\\bar x,\\bar y)$ and minimises the sum of squares of the vertical distances to the points. Use it to predict $y$ from $x$ (not $x$ from $y$)."),
         ("attention", "Interpretation", "Interpolation (predicting inside the range of the data) is reasonably reliable if $r$ is close to $\\pm1$; extrapolation (outside the range) is not. Correlation does not imply causation: two variables may both depend on a third one."),
         ("pieges", "Common mistakes", "Using the line of $y$ on $x$ to estimate $x$. Interpreting $r=0$ as 'no relationship' (there could be a non-linear one). Quoting $r$ with a value greater than 1 in magnitude means a calculation error.")],
        [("Example 1", "For $x=1,2,3,4,5$ and $y=2,4,5,4,5$ find $r$ and the regression line.", ["$\\bar x=3$, $\\bar y=4$; $S_{xx}=10$, $S_{yy}=6$, $S_{xy}=(-2)(-2)+0+0+0+(2)(1)=6$.", "$r=\\frac{6}{\\sqrt{60}}=0.775$; $b=\\frac{6}{10}=0.6$, $a=4-0.6\\times3=2.2$."], "$r\\approx0.775$; $y=2.2+0.6x$"),
         ("Example 2", "A shop records advertising spend $x$ (thousand FCFA) and sales $y$ (units): $(10,25),(20,38),(30,50),(40,66),(50,76)$. Find the regression line and predict sales at $x=35$.", ["$\\bar x=30$, $\\bar y=51$, $S_{xx}=1000$, $S_{xy}=1300$: $b=1.3$, $a=51-39=12$.", "$y=12+1.3x$; at $x=35$: $12+45.5=57.5$."], "$y=12+1.3x$; about 57.5 units", sc)],
        [N("$S_{xy}=1300$ and $S_{xx}=1000$. Find the gradient $b$ of the regression line.", 1.3, "$b=\\frac{1300}{1000}$.", tol=0.001),
         M("The correlation coefficient $r$ always lies between", "$-1$ and $1$", ["$0$ and $1$", "$-10$ and $10$", "$0$ and 100"], "Property."),
         TF("A high correlation between two variables proves one causes the other.", False, "Correlation does not imply causation.")],
        [M("$r=-0.92$ indicates", "strong negative linear correlation", ["weak positive correlation", "no correlation", "strong positive correlation"], "Close to $-1$."),
         O("In $y=12+1.3x$ ($x$ = advertising in thousand FCFA, $y$ = sales in units) interpret the gradient and say whether predicting sales at $x=200$ is reliable.", "The gradient 1.3 means that each extra thousand FCFA spent on advertising is associated with about 1.3 more units sold. $x=200$ lies far outside the data range (10 to 50): this is extrapolation, so the prediction is unreliable.", ["Interprets gradient in context (1)", "Identifies extrapolation (1)", "Concludes unreliable (1)"])],
        P("Using the data of Example 2: advertising $x=10,20,30,40,50$; sales $y=25,38,50,66,76$ with $S_{xx}=1000$, $S_{xy}=1300$, $S_{yy}=1696$.", [pn("Find $r$ (4 d.p.).", round(r, 4), "$r=\\frac{1300}{\\sqrt{1000\\times1696}}=0.9982$.", tol=0.0003, pts=2), pn("Predict the sales when $x=35$.", 57.5, "$12+1.3\\times35$.", tol=0.01, pts=1), pm("Using the line to predict sales when $x=200$ is", "unreliable because it is extrapolation", ["reliable because $r$ is close to 1", "reliable because the line is straight", "impossible"], "Outside the data range.", pts=1)]),
        [("The regression line passes through", "$(\\bar x,\\bar y)$", ["$(0,0)$", "the first point", "$(S_{xx},S_{yy})$"], "Property."),
         ("$b=$", "$\\frac{S_{xy}}{S_{xx}}$", ["$\\frac{S_{xx}}{S_{xy}}$", "$\\frac{S_{xy}}{S_{yy}}$", "$S_{xy}S_{xx}$"], "Least squares."),
         ("Predicting inside the range of the data is called", "interpolation", ["extrapolation", "regression", "correlation"], "Definition."),
         ("If $r=0$ then", "there is no linear correlation", ["there is no relationship of any kind", "the line is vertical", "$b=1$"], "Only linear."),
         ("The intercept is found by $a=$", "$\\bar y-b\\bar x$", ["$\\bar x-b\\bar y$", "$b\\bar x$", "$\\bar y+b\\bar x$"], "Line through the means.")],
        ill=None, notes=["Spearman's rank correlation and the regression of $x$ on $y$ are not included.", "Scatter diagram line points are plotted by hand from the computed regression line."], ref=SREF)
