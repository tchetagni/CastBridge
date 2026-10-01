"""Generates form2-maths (run: python3 form2-maths.py)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from sm_common import *

REF = "Cameroon secondary (general education) Mathematics programme, Form 2 (MINESEC) — to be checked against the official programme"
p = mkpack(2, "", REF, "Second year of secondary mathematics: indices and standard form, speed and rates, commercial arithmetic, algebra, inequalities, straight-line graphs, sets, polygons, bearings, circles, volume and statistics.")

# ===================================================================== NUMBER
ch = p.chapter("number", "Indices, approximation and rates", REF)

assert 2**3 * 2**4 == 128 and (3**2)**3 == 729 and Fraction(1, 5**2) == Fraction(1, 25) and 3**4 == 81 and 3**5 // 3**3 == 9
lesson(ch, "indices", "Indices (powers)",
  ["Write repeated multiplication as a power", "Use the laws of indices for multiplying, dividing and raising to a power", "Use the zero and negative indices", "Simplify algebraic expressions with powers"],
  [("definition", "Index notation", "In $a^n$, a is the **base** and n is the **index** (power or exponent): $2^5 = 2 \\times 2 \\times 2 \\times 2 \\times 2 = 32$. $a^2$ is 'a squared' and $a^3$ is 'a cubed'."),
   ("propriete", "Laws of indices", "- $a^m \\times a^n = a^{m+n}$\n- $a^m \\div a^n = a^{m-n}$\n- $(a^m)^n = a^{mn}$\n- $a^0 = 1$ (for a not zero)\n- $a^{-n} = \\frac{1}{a^n}$\n- $(ab)^n = a^n b^n$\nThe first two laws need the **same base**."),
   ("methode", "Method with algebra", "Multiply the numbers in front, and add the indices of the same letter: $2x^3 \\times 3x^4 = 6x^7$. For a bracket with a power, raise every factor: $(2a^2)^3 = 2^3 a^6 = 8a^6$. Divide the numbers and subtract the indices: $12x^5 \\div 4x^2 = 3x^3$."),
   ("pieges", "Common mistakes", "- $2^3 \\times 2^4$ is $2^7$, not $4^7$: the base stays the same.\n- $(2a)^3$ is $8a^3$, not $2a^3$.\n- $a^{-2}$ is $\\frac{1}{a^2}$, not a negative number; and $a^0$ is 1, not 0.")],
  [("Example 1: numbers", "Evaluate (a) $2^3 \\times 2^4$, (b) $(3^2)^3$, (c) $5^{-2}$.",
    ["(a) Add the indices: $2^{3+4} = 2^7 = 128$.", "(b) Multiply the indices: $3^{2 \\times 3} = 3^6 = 729$.", "(c) $5^{-2} = \\frac{1}{5^2} = \\frac{1}{25}$."], "**(a) 128, (b) 729, (c) 1/25**",
    ptable(["n", "0", "1", "2", "3", "4", "5"], [["2ⁿ", "1", "2", "4", "8", "16", "32"]], colw=50)),
   ("Example 2: algebra", "Simplify (a) $2x^3 \\times 3x^4$, (b) $x^8 \\div x^5$, (c) $(2a^2)^3$.",
    ["(a) 2 × 3 = 6 and $x^{3+4} = x^7$, so $6x^7$.", "(b) $x^{8-5} = x^3$.", "(c) $2^3 \\times a^{2 \\times 3} = 8a^6$."], "**(a) 6x⁷, (b) x³, (c) 8a⁶**")],
  [N("Evaluate $2^5$.", 32, "2 × 2 × 2 × 2 × 2 = 32."),
   M("Simplify $a^3 \\times a^4$.", "$a^7$", ["$a^{12}$", "$a^{1}$", "$2a^7$"], "Add the indices."),
   N("Evaluate $10^0 + 10^{-1}$ as a decimal.", 1.1, "1 + 0.1 = 1.1.")],
  [N("Evaluate $3^5 \\div 3^3$.", 9, "3² = 9."),
   O("Explain why $a^0 = 1$ using the law for dividing powers.", "$a^3 \\div a^3 = 1$, but by the law it is $a^{3-3} = a^0$. So $a^0 = 1$.", "1 mark for the division law, 1 mark for $a^3 \\div a^3 = 1$, 1 mark for the conclusion.", points=3)],
  P("A message is forwarded on a phone network in rounds. In round n the number of new people who receive it is $3^n$.",
    [pn("How many new people receive it in round 4?", 81, "3⁴ = 81.", pts=1),
     pn("How many times more people receive it in round 5 than in round 3?", 9, "3⁵ ÷ 3³ = 3² = 9.", pts=2),
     pn("How many people receive it in round 0 (the first sender)?", 1, "3⁰ = 1.", pts=1)]),
  [("The value of $4^2$:", "16", ["8", "6", "2"], "4 × 4."),
   ("$x^6 \\div x^2$ equals:", "$x^4$", ["$x^3$", "$x^8$", "$x^{12}$"], "Subtract the indices."),
   ("$(x^2)^3$ equals:", "$x^6$", ["$x^5$", "$x^8$", "$x^9$"], "Multiply the indices."),
   ("$2^{-3}$ equals:", "$\\frac{1}{8}$", ["−8", "−6", "$\\frac{1}{6}$"], "1 ÷ 2³."),
   ("Any non-zero number to the power 0 equals:", "1", ["0", "itself", "10"], "$a^0 = 1$.")],
  notes=["Fractional indices are left for Form 3; the official scheme may introduce them earlier."])

assert 3.6e5 / 6e1 == 6000 and 4.5e6 == 4500000 and abs(0.00032 - 3.2e-4) < 1e-12 and (2e3) * (3e4) == 6e7 and round(0.004567, 4) == 0.0046 and 3.6e5 / 4.5e2 == 800
lesson(ch, "standard-form", "Approximation and standard form",
  ["Round to a number of decimal places and significant figures", "Write large and small numbers in standard form", "Calculate with numbers in standard form", "Estimate and check answers"],
  [("definition", "Rounding", "To round to **n decimal places**, keep n digits after the decimal point and look at the next digit. To round to **n significant figures** (s.f.), count from the first non-zero digit. Examples: 3.456 → 3.5 (1 d.p.); 0.004567 → 0.0046 (2 s.f.); 45 678 → 45 700 (3 s.f.). Zeros that only show the size of the number are filled in."),
   ("definition", "Standard form", "A number is in **standard form** when it is written $a \\times 10^n$ with $1 \\leq a < 10$ and n an integer. Large numbers have a positive n: $4\\,500\\,000 = 4.5 \\times 10^6$. Small numbers have a negative n: $0.00032 = 3.2 \\times 10^{-4}$."),
   ("methode", "Calculating", "To multiply, multiply the numbers a and add the indices; to divide, divide the numbers and subtract the indices. Then adjust to standard form. $(2 \\times 10^3) \\times (3 \\times 10^4) = 6 \\times 10^7$. $(8 \\times 10^5) \\times (5 \\times 10^2) = 40 \\times 10^7 = 4 \\times 10^8$."),
   ("pieges", "Common mistakes", "- Writing 45 × 10⁵ in standard form: a must be between 1 and 10, so it is 4.5 × 10⁶.\n- Counting zeros as significant when they only hold place value.\n- Using a positive index for a small number: 0.003 is 3 × 10⁻³.")],
  [("Example 1: write in standard form", "Write 45 000 and 0.0032 in standard form. Round 0.004567 to 2 s.f.",
    ["45 000: move the decimal point 4 places left, so 4.5 × 10⁴.", "0.0032: move it 3 places right, so 3.2 × 10⁻³.", "0.004567: the first non-zero digit is 4; two s.f. gives 0.0046 (next digit 5 rounds up)."], "**4.5 × 10⁴; 3.2 × 10⁻³; 0.0046**",
    ptable(["Number", "Standard form"], [["45 000", "4.5 × 10⁴"], ["0.0032", "3.2 × 10⁻³"], ["600", "6 × 10²"]], colw=120)),
   ("Example 2: calculation", "A store at Douala port holds 3.6 × 10⁵ kg of cocoa in sacks of 6 × 10¹ kg each. Find the number of sacks.",
    ["Divide the numbers: 3.6 ÷ 6 = 0.6.", "Subtract the indices: 10^(5−1) = 10⁴.", "So 0.6 × 10⁴; in standard form 6 × 10³ = 6 000 sacks."], "**6 000 sacks**")],
  [N("Round 3.456 to 1 decimal place.", 3.5, "The next digit is 5, so round up."),
   M("Standard form of 5 600 000:", "$5.6 \\times 10^6$", ["$56 \\times 10^5$", "$5.6 \\times 10^5$", "$0.56 \\times 10^7$"], "Move the decimal point 6 places."),
   N("Write 0.0007 as a × 10ⁿ: what is n?", -4, "0.0007 = 7 × 10⁻⁴.")],
  [N("Calculate (2 × 10³) × (3 × 10⁴) and give the index n in a × 10ⁿ.", 7, "6 × 10⁷."),
   O("Explain why 45 × 10⁵ is not in standard form and rewrite it.", "The number in front must be at least 1 and less than 10. 45 × 10⁵ = 4.5 × 10 × 10⁵ = 4.5 × 10⁶.", "1 mark for the rule, 1 mark for the conversion, 1 mark for the answer.", points=3)],
  P("A village of 3 200 people at Foumban uses about 25 litres of water per person per day.",
    [pn("Total daily use in litres (exact).", 80000, "3 200 × 25 = 80 000.", unit="l", pts=1),
     pn("Write it as a × 10ⁿ and give a.", 8, "80 000 = 8 × 10⁴.", pts=1),
     pn("How many litres in 30 days, as a × 10ⁿ: give n.", 6, "80 000 × 30 = 2 400 000 = 2.4 × 10⁶.", pts=2)]),
  [("Round 78.46 to the nearest whole number:", "78", ["79", "80", "78.5"], "The first decimal is 4."),
   ("2.5 × 10³ equals:", "2 500", ["250", "25 000", "0.0025"], "Move the decimal point 3 places right."),
   ("In 0.0305 the number of significant figures is:", "3", ["5", "4", "2"], "3, 0 and 5; the leading zeros do not count."),
   ("Standard form of 0.0045:", "$4.5 \\times 10^{-3}$", ["$4.5 \\times 10^{3}$", "$45 \\times 10^{-4}$", "$0.45 \\times 10^{-2}$"], "3 places right."),
   ("Rounding 6 482 to 2 s.f. gives:", "6 500", ["6 400", "6 480", "65"], "Third digit 8 rounds up.")],
  notes=["Check how many significant figures the official scheme expects in Form 2."])

assert 120 / 2 == 60 and abs(240 / 4.5 - 53.333) < 1e-3 and 72 * 1000 / 3600 == 20 and 18 / 0.75 == 24 and 20 * 3.6 == 72
dt = plot(0, 5, 0, 140, segments=[(0, 0, 2, 120, "blue", False), (2, 120, 2.5, 120, "orange", False), (2.5, 120, 4.5, 0, "red", False)], grid=1, xlabel="time (hours)", ylabel="distance (km)", w=400, h=260)
lesson(ch, "speed-rates", "Speed, distance, time and rates",
  ["Use the relation between speed, distance and time", "Calculate average speed for a whole journey", "Convert between km/h and m/s", "Read a distance-time graph", "Solve problems with other rates"],
  [("formule", "Speed, distance and time", "Speed measures how fast something moves. For constant speed:", "\\text{speed} = \\frac{\\text{distance}}{\\text{time}} \\qquad \\text{distance} = \\text{speed} \\times \\text{time} \\qquad \\text{time} = \\frac{\\text{distance}}{\\text{speed}}"),
   ("methode", "Average speed", "**Average speed = total distance ÷ total time.** The total time includes stops. Convert times to hours: 45 minutes = 0.75 h and 2 h 30 min = 2.5 h. To change km/h into m/s multiply by 1 000 and divide by 3 600 (that is divide by 3.6): 72 km/h = 20 m/s."),
   ("methode", "Distance-time graphs", "On a distance-time graph the **gradient** is the speed. A rising line means moving away; a **horizontal** line means stopped; a falling line means coming back. A steeper line means a faster speed."),
   ("pieges", "Common mistakes", "- Writing 2 h 30 min as 2.30 h: it is 2.5 h.\n- Averaging two speeds instead of dividing total distance by total time.\n- Forgetting the rest time in the total time of the journey.")],
  [("Example 1: average speed", "A bus leaves Bamenda, travels 120 km in 2 hours, rests for 30 minutes and then returns the 120 km in 2 hours (see the graph). Find its speed on the way out and its average speed for the whole trip.",
    ["Speed out = 120 ÷ 2 = 60 km/h.", "Total distance = 120 + 120 = 240 km.", "Total time = 2 + 0.5 + 2 = 4.5 h, so average speed = 240 ÷ 4.5 ≈ 53.3 km/h."], "**60 km/h; about 53.3 km/h**", dt),
   ("Example 2: conversion and time", "Convert 72 km/h to m/s. A cyclist covers 18 km in 45 minutes: find the speed in km/h.",
    ["72 km/h = 72 × 1 000 ÷ 3 600 = 20 m/s.", "45 min = 0.75 h.", "Speed = 18 ÷ 0.75 = 24 km/h."], "**20 m/s; 24 km/h**")],
  [N("A car travels 150 km in 2.5 hours. Find its speed (km/h).", 60, "150 ÷ 2.5 = 60.", unit="km/h"),
   N("How far does a bike travelling at 15 km/h go in 3 hours (km)?", 45, "15 × 3 = 45.", unit="km"),
   M("2 h 15 min in hours equals:", "2.25 h", ["2.15 h", "2.5 h", "2.75 h"], "15 min = 0.25 h.")],
  [N("Convert 54 km/h to m/s.", 15, "54 ÷ 3.6 = 15.", unit="m/s"),
   N("A lorry travels 90 km at 45 km/h and then 90 km at 90 km/h. Find the average speed (km/h).", 60, "Total 180 km; time 2 + 1 = 3 h; 180 ÷ 3 = 60.", unit="km/h")],
  P("A family drives from Douala to Limbe, a journey of about 70 km, in 1 hour 24 minutes.",
    [pn("Write the time in hours.", 1.4, "24 ÷ 60 = 0.4, so 1.4 h.", unit="h", pts=1),
     pn("Find the average speed (km/h).", 50, "70 ÷ 1.4 = 50.", unit="km/h", pts=2),
     pn("At 50 km/h, how long (hours) for 125 km?", 2.5, "125 ÷ 50 = 2.5.", unit="h", pts=2)]),
  [("Speed equals:", "distance divided by time", ["distance times time", "time divided by distance", "distance plus time"], "Basic formula."),
   ("Distance = speed × time. At 40 km/h for 3 h, distance is:", "120 km", ["13 km", "43 km", "40 km"], "40 × 3."),
   ("A horizontal line on a distance-time graph means:", "the object is stationary", ["it moves fastest", "it comes back", "it speeds up"], "Distance does not change."),
   ("20 m/s equals:", "72 km/h", ["20 km/h", "5.6 km/h", "200 km/h"], "20 × 3.6."),
   ("45 minutes is how many hours?", "0.75", ["0.45", "4.5", "1.45"], "45 ÷ 60.")],
  notes=["The Bamenda trip figures are made up for practice; they are not real route data."])

assert 18000 - 15000 == 3000 and 3000 / 15000 * 100 == 20 and (40000 - 34000) / 40000 * 100 == 15 and 25000 * (1 - 0.12) == 22000 and 200000 * 6 * 3 / 100 == 36000 and 800000 * 0.05 == 40000
lesson(ch, "commercial", "Profit, loss, discount and simple interest",
  ["Calculate profit and loss and express them as percentages", "Find the selling price after a discount", "Calculate simple interest", "Work out commission"],
  [("formule", "Profit and loss", "Profit = selling price (SP) − cost price (CP). Loss = CP − SP. Percentages are always taken on the **cost price**:", "\\text{profit \\%} = \\frac{\\text{profit}}{\\text{cost price}} \\times 100"),
   ("methode", "Discount and commission", "A **discount** is a reduction of the marked price: selling price = marked price × $(1 - \\frac{d}{100})$. **Commission** is a percentage of the sales paid to an agent. A shop marked a phone at 25 000 FCFA with 12% off: 25 000 × 0.88 = 22 000 FCFA."),
   ("formule", "Simple interest", "Interest earned or paid on the **original** amount only. P = principal, R = rate per year (%), T = time in years:", "I = \\frac{P \\times R \\times T}{100}"),
   ("pieges", "Common mistakes", "- Calculating profit % on the selling price instead of the cost price.\n- Forgetting that the amount after interest is **principal + interest**.\n- Using months for T: change to years (6 months = 0.5 year).")],
  [("Example 1: profit and loss", "A trader in Bafoussam buys a bag of beans for 15 000 FCFA and sells it for 18 000 FCFA. Find the profit percent. Another bag costs 40 000 FCFA and is sold for 34 000 FCFA: find the loss percent.",
    ["Profit = 18 000 − 15 000 = 3 000. Profit % = 3 000 ÷ 15 000 × 100 = 20%.", "Loss = 40 000 − 34 000 = 6 000. Loss % = 6 000 ÷ 40 000 × 100 = 15%."], "**20% profit; 15% loss.**",
    ptable(["Item", "CP", "SP", "Result"], [["Beans", "15 000", "18 000", "20% profit"], ["Bag 2", "40 000", "34 000", "15% loss"]], colw=95)),
   ("Example 2: interest and discount", "A woman saves 200 000 FCFA at 6% simple interest per year for 3 years. Find the interest and the total. A shirt marked 25 000 FCFA is sold with 12% off: find the price.",
    ["I = 200 000 × 6 × 3 ÷ 100 = 36 000 FCFA. Total = 236 000 FCFA.", "Discount = 12% of 25 000 = 3 000, so the price is 22 000 FCFA (also 25 000 × 0.88)."], "**Interest 36 000 FCFA, total 236 000 FCFA; shirt 22 000 FCFA**")],
  [N("A radio is bought for 8 000 FCFA and sold for 10 000 FCFA. Find the profit % (on cost).", 25, "2 000 ÷ 8 000 × 100 = 25%.", unit="%"),
   N("Find the simple interest on 100 000 FCFA at 5% for 2 years (FCFA).", 10000, "100 000 × 5 × 2 ÷ 100 = 10 000.", unit="FCFA"),
   M("A discount of 10% on 5 000 FCFA gives a price of:", "4 500 FCFA", ["500 FCFA", "4 000 FCFA", "5 500 FCFA"], "5 000 × 0.9.")],
  [N("An agent earns 5% commission on sales of 800 000 FCFA. Find his commission (FCFA).", 40000, "800 000 × 0.05 = 40 000.", unit="FCFA"),
   O("A shopkeeper says: 'I gave a 20% discount, then a 10% discount, so the total discount is 30%.' Is she right? Test with a price of 10 000 FCFA.", "No. After 20% the price is 8 000; 10% of that is 800, giving 7 200 FCFA. The total discount is 2 800 FCFA, which is 28%, not 30%.", "1 mark for 8 000, 1 mark for 7 200, 1 mark for 28% and the conclusion.", points=3)],
  P("A tailor in Kumba buys cloth for 60 000 FCFA, makes 10 dresses and sells each for 8 000 FCFA.",
    [pn("Total sales (FCFA).", 80000, "10 × 8 000.", unit="FCFA", pts=1),
     pn("Profit (FCFA).", 20000, "80 000 − 60 000.", unit="FCFA", pts=1),
     pn("Profit % on the cost, to 1 d.p.", 33.3, "20 000 ÷ 60 000 × 100 = 33.3%.", tol=0.1, unit="%", pts=2),
     pn("She borrows 60 000 FCFA at 10% simple interest for 6 months (0.5 year). How much interest (FCFA)?", 3000, "60 000 × 10 × 0.5 ÷ 100 = 3 000.", unit="FCFA", pts=1)]),
  [("Profit is found by:", "selling price − cost price", ["cost price − selling price", "selling price × cost price", "cost price ÷ selling price"], "Profit definition."),
   ("In simple interest, interest is calculated on:", "the original amount only", ["the growing amount", "the interest only", "the time only"], "Simple versus compound."),
   ("A 25% discount on 4 000 FCFA is:", "1 000 FCFA", ["100 FCFA", "3 000 FCFA", "4 250 FCFA"], "A quarter."),
   ("Percentage profit is a percentage of:", "the cost price", ["the selling price", "the discount", "the time"], "Standard rule."),
   ("6 months in the interest formula is T =", "0.5", ["6", "12", "60"], "Years.")],
  notes=["Compound interest, VAT and hire purchase are left for Form 3."])

# ===================================================================== ALGEBRA
ch = p.chapter("algebra", "Algebra and graphs", REF)

for X in range(-5, 6):
    assert (X + 2) * (X + 3) == X**2 + 5*X + 6 and (2*X - 1) * (X + 4) == 2*X**2 + 7*X - 4 and 6*X**2 + 9*X == 3*X*(2*X + 3)
    assert (X + 5) * (X + 2) == X**2 + 7*X + 10 and (3*X - 2) * (X + 5) == 3*X**2 + 13*X - 10
assert 8 * 5 == 40 and 9 + 21 + 10 == 40
am = shapes([RECT(60, 40, 120, 80, fill="lightyellow", width=2), RECT(180, 40, 90, 80, fill="lightblue", width=2), RECT(60, 120, 120, 60, fill="lightblue", width=2), RECT(180, 120, 90, 60, fill="lightorange", width=2),
             T(120, 85, "x²", 16), T(225, 85, "3x", 16), T(120, 155, "2x", 16), T(225, 155, "6", 16), T(120, 32, "x", 14), T(225, 32, "3", 14), T(48, 85, "x", 14, anchor="end"), T(48, 155, "2", 14, anchor="end")], 400, 200)
lesson(ch, "algebraic-expressions", "Brackets, factors and formulae",
  ["Expand a product of two brackets", "Factorise by taking out a common factor", "Substitute into formulae", "Change the subject of a simple formula"],
  [("methode", "Expanding two brackets", "Multiply **each term of the first bracket by each term of the second**, then collect like terms: $(x + 2)(x + 3) = x^2 + 3x + 2x + 6 = x^2 + 5x + 6$. For a minus sign: $(2x - 1)(x + 4) = 2x^2 + 8x - x - 4 = 2x^2 + 7x - 4$. An area picture shows all four products."),
   ("methode", "Factorising: common factor", "**Factorising** is the opposite of expanding. Find the largest number and the lowest power of a letter that divide every term and write it outside a bracket: $6x^2 + 9x = 3x(2x + 3)$. Check by expanding the answer."),
   ("methode", "Changing the subject", "To make a letter the subject, undo the operations around it, doing the same to both sides. From $P = 2(l + w)$: divide by 2 to get $\\frac{P}{2} = l + w$, then subtract w: $l = \\frac{P}{2} - w$. From $v = u + at$: $t = \\frac{v - u}{a}$."),
   ("pieges", "Common mistakes", "- Writing $(x + 3)^2 = x^2 + 9$: it is $(x + 3)(x + 3) = x^2 + 6x + 9$.\n- Taking out a factor but leaving a term without a factor in the bracket: $6x^2 + 9x = 3x(2x + 3)$, not $3x(2x + 9)$.\n- Moving a term across the equals sign without changing its sign.")],
  [("Example 1: expand", "A garden has length (x + 5) m and width (x + 2) m. Expand its area and find it when x = 3.",
    ["Area = (x + 5)(x + 2) = x² + 2x + 5x + 10 = x² + 7x + 10.", "When x = 3: 9 + 21 + 10 = 40.", "Check: (3 + 5)(3 + 2) = 8 × 5 = 40 m²."], "**x² + 7x + 10; 40 m²**", am),
   ("Example 2: factorise and change the subject", "Factorise 6x² + 9x. Make l the subject of P = 2(l + w).",
    ["The common factor of 6x² and 9x is 3x. 6x² ÷ 3x = 2x and 9x ÷ 3x = 3, so 3x(2x + 3).", "Divide P = 2(l + w) by 2: P/2 = l + w.", "Subtract w: l = P/2 − w."], "**3x(2x + 3); l = P/2 − w**")],
  [M("Expand (x + 4)(x + 1).", "$x^2 + 5x + 4$", ["$x^2 + 4$", "$x^2 + 5x + 5$", "$x^2 + 3x + 4$"], "x² + x + 4x + 4."),
   M("Factorise 4a + 12.", "4(a + 3)", ["4(a + 12)", "a(4 + 12)", "4a(3)"], "4 is the common factor."),
   N("Find the value of P = 2(l + w) when l = 9 and w = 4.", 26, "2 × 13.")],
  [N("Expand (3x − 2)(x + 5) and give the coefficient of x.", 13, "3x² + 15x − 2x − 10 = 3x² + 13x − 10."),
   O("Make t the subject of v = u + at and explain each step.", "Subtract u from both sides: v − u = at. Divide both sides by a: t = (v − u) ÷ a.", "1 mark for subtracting u, 1 mark for dividing by a, 1 mark for the result.", points=3)],
  P("A shop in Limbe charges a delivery fee of 2 000 FCFA plus 500 FCFA per kilogram: C = 500k + 2 000.",
    [pn("Find the cost for 6 kg (FCFA).", 5000, "500 × 6 + 2 000 = 5 000.", unit="FCFA", pts=1),
     pn("Find k when C = 9 000.", 14, "500k = 7 000, so k = 14.", unit="kg", pts=2),
     pm("Which is k in terms of C?", "$k = \\frac{C - 2000}{500}$", ["$k = \\frac{C}{500} + 2000$", "$k = 500C - 2000$", "$k = \\frac{C + 2000}{500}$"], "Subtract 2 000 then divide by 500.", 2)]),
  [("Expand (x + 1)(x + 2):", "$x^2 + 3x + 2$", ["$x^2 + 2$", "$x^2 + 3x + 3$", "$2x + 3$"], "Four products."),
   ("A common factor of 5x and 15 is:", "5", ["x", "15", "3"], "Largest number dividing both."),
   ("In y = mx + c, making x the subject gives:", "$x = \\frac{y - c}{m}$", ["$x = y - c - m$", "$x = \\frac{y + c}{m}$", "$x = m(y - c)$"], "Subtract c then divide by m."),
   ("Factorise x² + 3x:", "x(x + 3)", ["x(x + 3x)", "3(x + x)", "x²(1 + 3)"], "Common factor x."),
   ("To check a common-factor factorisation we:", "expand it again", ["add the terms", "divide by x", "square it"], "It must give the original.")],
  notes=["Trinomial factorisation (x² + bx + c) and difference of two squares are left for Form 3."])

assert 15 / 3 + 2 == 7 and (2 * 8 - 1) / 3 == 5 and 3 * 5 - 2 > 10 and -2 * -3 == 6 and [i for i in range(-10, 11) if 2 < i <= 6] == [3, 4, 5, 6]
nl = num_line(0, 8, range(0, 9), 1, w=420, h=130, hollow=[4], arrow_from=4, arrow_to=8)
lesson(ch, "equations-inequalities", "Equations with fractions and linear inequalities",
  ["Solve equations containing fractions", "Use the inequality symbols < > ≤ ≥", "Solve linear inequalities, including multiplying by a negative number", "Show the solution on a number line"],
  [("methode", "Equations with fractions", "Multiply **every term** by the denominator to clear the fraction. $\\frac{x}{3} + 2 = 7$: subtract 2 to get $\\frac{x}{3} = 5$, then multiply by 3: x = 15. $\\frac{2x - 1}{3} = 5$: multiply by 3: 2x − 1 = 15, so x = 8. Always check by substituting."),
   ("definition", "Inequalities", "The symbols are: < (less than), > (greater than), ≤ (less than or equal to), ≥ (greater than or equal to). An inequality has **many solutions**: x > 4 is true for 5, 6, 4.5, 100 and so on. On a number line an **open circle** shows that the end point is not included; a **closed circle** shows that it is included."),
   ("propriete", "Solving inequalities", "Solve like an equation (same operation on both sides), with one big difference: **when you multiply or divide by a negative number, reverse the inequality sign.** −2x ≤ 6 gives x ≥ −3."),
   ("pieges", "Common mistakes", "- Forgetting to reverse the sign when dividing by a negative number.\n- Using a closed circle for > or <.\n- Multiplying only one term by the denominator in an equation with fractions.")],
  [("Example 1: fractions", "Solve (a) x/3 + 2 = 7 and (b) (2x − 1)/3 = 5.",
    ["(a) x/3 = 5, so x = 15. Check 15/3 + 2 = 7.", "(b) Multiply by 3: 2x − 1 = 15, so 2x = 16 and x = 8. Check (16 − 1)/3 = 5."], "**(a) x = 15; (b) x = 8**"),
   ("Example 2: inequalities", "Solve 3x − 2 > 10 and show it on a number line. Then solve −2x ≤ 6.",
    ["3x > 12, so x > 4. Open circle at 4 and an arrow to the right.", "Divide −2x ≤ 6 by −2 and reverse the sign: x ≥ −3."], "**x > 4; x ≥ −3**", nl)],
  [N("Solve x/4 = 6.", 24, "Multiply both sides by 4."),
   M("Solve 2x + 1 < 9:", "x < 4", ["x > 4", "x < 5", "x < 8"], "2x < 8."),
   N("What is the largest integer satisfying x < 7?", 6, "7 is not included.")],
  [N("List the integers satisfying 2 < x ≤ 6; how many are there?", 4, "3, 4, 5, 6: four integers."),
   N("Solve −3x > 12 and give the boundary value.", -4, "Divide by −3 and reverse: x < −4; the boundary is −4.")],
  P("A taxi driver at Yaoundé wants to earn at least 15 000 FCFA in a day. He earns 1 500 FCFA per trip and pays 3 000 FCFA for fuel. Let n be the number of trips.",
    [pm("Which inequality models the target?", "1 500n − 3 000 ≥ 15 000", ["1 500n + 3 000 ≥ 15 000", "1 500n − 3 000 ≤ 15 000", "1 500 − 3 000n ≥ 15 000"], "Earnings minus fuel is at least 15 000.", 1),
     pn("Solve for the smallest value of n.", 12, "1 500n ≥ 18 000, so n ≥ 12.", pts=2),
     pn("If he does 14 trips, how much does he earn after fuel (FCFA)?", 18000, "14 × 1 500 − 3 000 = 18 000.", unit="FCFA", pts=2)]),
  [("The symbol ≥ means:", "greater than or equal to", ["greater than", "less than or equal to", "not equal to"], "Symbol meaning."),
   ("Solve x + 3 > 8:", "x > 5", ["x < 5", "x > 11", "x < 11"], "Subtract 3."),
   ("When you divide an inequality by a negative number you:", "reverse the sign", ["keep the sign", "remove the sign", "add one to both sides"], "Rule."),
   ("An open circle on a number line shows that the point is:", "not included", ["included", "zero", "negative"], "Strict inequality."),
   ("Solve x/5 = 3:", "x = 15", ["x = 8", "x = 0.6", "x = 2"], "Multiply by 5.")],
  ill=(nl, "Number line showing x > 4: open circle at 4, arrow to the right.", "A number line from 0 to 8 with an open circle at 4 and a shaded line to the right."),
  notes=["Compound and simultaneous inequalities are left for Form 3."])

assert [2 * i + 1 for i in range(-2, 4)] == [-3, -1, 1, 3, 5, 7] and 300 + 150 * 4 == 900 and (1500 - 300) / 150 == 8
lesson(ch, "linear-graphs", "Coordinates and straight-line graphs",
  ["Plot points in the four quadrants", "Make a table of values and draw a straight line", "Find the gradient and y-intercept of y = mx + c", "Use a straight-line graph to solve a problem"],
  [("definition", "Coordinates", "A point is given by an ordered pair (x, y): x tells how far along the horizontal axis, y how far up the vertical axis. The axes meet at the **origin** O(0, 0) and divide the plane into four **quadrants**. The point (−2, 3) is 2 left and 3 up."),
   ("methode", "Drawing a line", "To draw y = 2x + 1 make a **table of values**: choose x = −2, −1, 0, 1, 2, 3 and calculate y = −3, −1, 1, 3, 5, 7. Plot the points; they lie on a straight line. Join them with a ruler and label the line. Two points are enough, a third checks."),
   ("propriete", "Gradient and intercept", "For y = mx + c the number m is the **gradient** (steepness): rise ÷ run. The number c is the **y-intercept**, where the line crosses the y-axis. y = 2x + 1 has gradient 2 and y-intercept 1; the line y = −x + 4 slopes down."),
   ("pieges", "Common mistakes", "- Plotting (x, y) as (y, x).\n- Mixing negative numbers in the table: with y = 2x + 1 and x = −2, y = −3, not 5.\n- Reading the gradient from the wrong scale when the axes have different scales.")],
  [("Example 1: table and line", "Draw y = 2x + 1 for x from −2 to 3 and state the gradient.",
    ["Table: (−2, −3), (−1, −1), (0, 1), (1, 3), (2, 5), (3, 7).", "Plot and join them: a straight line through (0, 1).", "From (0, 1) to (2, 5): rise 4, run 2, gradient 4 ÷ 2 = 2."], "**Gradient 2, y-intercept 1.**",
    plot(-3, 4, -4, 9, curves=[("2*x+1", "blue", "y = 2x + 1")], points=[(0, 1, "(0, 1)", "red"), (2, 5, "(2, 5)", "red")], grid=1, xlabel="x", ylabel="y")),
   ("Example 2: a taxi fare", "A taxi in Bamenda charges F = 300 + 150d FCFA for a trip of d km. Find the fare for 4 km, and the distance for a fare of 1 500 FCFA.",
    ["For 4 km: F = 300 + 150 × 4 = 900 FCFA.", "For 1 500 FCFA: 300 + 150d = 1 500, so 150d = 1 200.", "d = 8 km. The gradient 150 is the cost per km and 300 is the starting charge."], "**900 FCFA; 8 km.**")],
  [N("Find y when x = 4 in y = 3x − 2.", 10, "3 × 4 − 2 = 10."),
   M("The line y = 3x + 5 crosses the y-axis at:", "(0, 5)", ["(5, 0)", "(0, 3)", "(3, 0)"], "x = 0 gives y = 5."),
   N("Find the gradient of the line through (0, 1) and (3, 7).", 2, "(7 − 1) ÷ (3 − 0) = 2.")],
  [N("Find the gradient of the line through (1, 5) and (4, −1).", -2, "(−1 − 5) ÷ (4 − 1) = −2.", ),
   O("Describe the graph of y = −x + 4: its gradient, intercept and one point on it.", "It slopes down with gradient −1, crosses the y-axis at 4, and passes through (4, 0).", "1 mark for the gradient, 1 mark for the intercept, 1 mark for a correct point.", points=3)],
  P("The fare F = 300 + 150d FCFA is drawn on a graph with d on the horizontal axis.",
    [pn("What is the fare at d = 0 (FCFA)?", 300, "The y-intercept is 300.", unit="FCFA", pts=1),
     pn("What is the gradient of the fare line?", 150, "It is the cost per km.", pts=1),
     pn("A passenger pays 1 050 FCFA. How far did she travel (km)?", 5, "300 + 150d = 1 050, so d = 5.", unit="km", pts=2)]),
  [("The point (0, 0) is called:", "the origin", ["the axis", "the gradient", "the quadrant"], "Where the axes meet."),
   ("The line y = 2x passes through:", "(3, 6)", ["(3, 5)", "(2, 3)", "(6, 3)"], "y = 2 × 3."),
   ("In y = mx + c, c is:", "the y-intercept", ["the gradient", "the origin", "the x-value"], "Value of y at x = 0."),
   ("A line that slopes downward from left to right has:", "a negative gradient", ["a positive gradient", "gradient zero", "no gradient"], "Falling line."),
   ("The point (−3, 2) lies:", "3 units left and 2 up", ["3 right and 2 up", "3 left and 2 down", "2 left and 3 up"], "(x, y) order.")],
  notes=["The fare formulas are invented for practice, not real taxi tariffs."])

assert 21 + 18 + 15 - 7 - 6 - 5 + 2 == 38 and 60 - 38 == 22 and 21 - 5 - 4 - 2 == 10 and 7 - 2 == 5
v3 = venn3(10, 8, 6, 5, 4, 3, 2, 22)
lesson(ch, "sets-three", "Sets: complement, three sets and problems",
  ["Use the complement A′ and the universal set", "Draw and read a Venn diagram with three sets", "Use n(A ∪ B ∪ C) for three sets", "Solve survey problems"],
  [("definition", "Set notation", "The **complement** A′ is the set of elements in ξ that are not in A. The set {x : x is an even number, x < 10} reads 'the set of x such that x is even and less than 10' and equals {2, 4, 6, 8}. The notation A ∩ B ∩ C means the elements in all three sets."),
   ("methode", "Venn diagrams with three sets", "Draw three overlapping circles in a rectangle. There are **eight regions**. Work from the middle outwards: first A ∩ B ∩ C, then the regions with two sets only (subtract the centre), then the regions with one set only, and finally the outside. The total of all regions is n(ξ)."),
   ("formule", "Counting formula", "For three sets, the number in at least one set is:", "n(A \\cup B \\cup C) = n(A) + n(B) + n(C) - n(A \\cap B) - n(A \\cap C) - n(B \\cap C) + n(A \\cap B \\cap C)"),
   ("pieges", "Common mistakes", "- Writing n(A ∩ B) in the 'A and B only' region: that region excludes the centre.\n- Forgetting the elements outside all three circles.\n- Confusing A′ (complement) with the empty set.")],
  [("Example 1: fill a diagram", "In a school of 60 students: n(A) = 21 play football, n(B) = 18 volleyball, n(C) = 15 athletics; n(A ∩ B) = 7, n(A ∩ C) = 6, n(B ∩ C) = 5 and n(A ∩ B ∩ C) = 2. Find how many play only football.",
    ["Centre = 2. A and B only = 7 − 2 = 5; A and C only = 6 − 2 = 4; B and C only = 5 − 2 = 3.", "Only A = 21 − 5 − 4 − 2 = 10."], "**10 students play only football.**", v3),
   ("Example 2: none of them", "Using Example 1, find how many students play none of the three sports.",
    ["Only B = 18 − 5 − 3 − 2 = 8 and only C = 15 − 4 − 3 − 2 = 6.", "At least one sport: 10 + 8 + 6 + 5 + 4 + 3 + 2 = 38 (formula: 21 + 18 + 15 − 7 − 6 − 5 + 2 = 38).", "None = 60 − 38 = 22."], "**22 students play none of the three.**")],
  [N("ξ = {1, ..., 10} and A = {2, 4, 6, 8, 10}. Find n(A′).", 5, "A′ = {1, 3, 5, 7, 9}."),
   M("The set {x : x is a whole number, 2 < x < 6} is:", "{3, 4, 5}", ["{2, 3, 4, 5, 6}", "{3, 4, 5, 6}", "{2, 3, 4, 5}"], "Strict inequalities exclude 2 and 6."),
   N("How many regions does a Venn diagram of three overlapping sets have inside the circles?", 7, "Seven regions inside the circles, plus the outside region.")],
  [N("n(A) = 12, n(B) = 9, n(C) = 7, n(A ∩ B) = 4, n(A ∩ C) = 3, n(B ∩ C) = 2, n(A ∩ B ∩ C) = 1. Find n(A ∪ B ∪ C).", 20, "12 + 9 + 7 − 4 − 3 − 2 + 1 = 20."),
   O("In your own words, explain why n(A ∩ B ∩ C) is added back in the three-set counting formula.", "The elements in all three sets are counted three times by n(A) + n(B) + n(C) and removed three times by the three pairs, so they would not be counted at all. Adding the centre back counts them once.", "1 mark for triple counting, 1 mark for the removal, 1 mark for adding back.", points=3)],
  P("In a survey of 60 students, the figure shows how many take Football (A), Volleyball (B) and Athletics (C).",
    [pn("How many students take exactly two of the three sports?", 12, "5 + 4 + 3 = 12.", pts=2),
     pn("How many take at least one sport?", 38, "Sum of all seven regions = 38.", pts=1),
     pn("How many do not take football?", 39, "n(A′) = 60 − 21 = 39.", pts=2)], figure=v3),
  [("A′ means:", "the elements of ξ not in A", ["the elements of A", "the empty set", "A squared"], "Complement."),
   ("A ∩ B ∩ C contains elements that are:", "in all three sets", ["in any set", "in none", "in exactly one set"], "Triple intersection."),
   ("If n(ξ) = 30 and n(A) = 12, then n(A′) =", "18", ["12", "42", "360"], "30 − 12."),
   ("The centre of a three-set Venn diagram is:", "A ∩ B ∩ C", ["A ∪ B ∪ C", "A′", "∅"], "Common to all three."),
   ("How many subsets does {1, 2, 3} have?", "8", ["6", "3", "9"], "2³ = 8.")],
  ill=(v3, "Venn diagram of three sports: 10, 8, 6 only; 5, 4, 3 two sports; 2 all three; 22 none.", "Three overlapping circles with numbers in all regions and 22 outside."),
  notes=["The subsets count (2ⁿ) in the last self-check is extension content; check against the official scheme."])

# ===================================================================== GEOMETRY
ch = p.chapter("geometry", "Polygons, bearings, circles and volume", REF)

assert (6 - 2) * 180 == 720 and 720 - (110 + 120 + 130 + 140 + 150) == 70 and 360 / 9 == 40 and 180 - 40 == 140 and 135 * 2 + 90 == 360 and 120 * 3 == 360
hx = shapes([POLY([round(200 + 80 * cosd(a), 1) for a in range(0, 360, 60) for _ in (0,)] and [v for a in range(0, 360, 60) for v in (round(200 + 80 * cosd(a), 1), round(120 - 80 * sind(a), 1))], fill="lightyellow"),
             T(200, 125, "120° each", 14), T(200, 235, "Regular hexagon: (6 − 2) × 180 = 720°", 13)], 400, 250)
lesson(ch, "polygons", "Polygons: interior and exterior angles",
  ["Name polygons by their number of sides", "Find the sum of the interior angles of a polygon", "Find interior and exterior angles of regular polygons", "Decide which regular polygons fit round a point"],
  [("formule", "Sum of angles", "A polygon with n sides can be cut into (n − 2) triangles from one vertex, so the interior angles add up to:", "S = (n - 2) \\times 180^\\circ"),
   ("propriete", "Regular polygons", "A **regular** polygon has all sides equal and all angles equal. Its exterior angles add up to 360°, so each exterior angle is $\\frac{360^\\circ}{n}$ and each interior angle is $180^\\circ - \\frac{360^\\circ}{n}$. At a vertex, interior + exterior = 180°."),
   ("retenir", "Names", "3 sides: triangle; 4: quadrilateral; 5: pentagon; 6: hexagon; 7: heptagon; 8: octagon; 9: nonagon; 10: decagon; 12: dodecagon. In a **tessellation** shapes fit together with no gaps: the angles round each point must add up to 360°."),
   ("pieges", "Common mistakes", "- Using n × 180 instead of (n − 2) × 180.\n- Using the interior angle instead of the exterior angle to find the number of sides.\n- Assuming every irregular polygon has equal angles.")],
  [("Example 1: unknown angle", "Five angles of a hexagon are 110°, 120°, 130°, 140° and 150°. Find the sixth.",
    ["Sum for a hexagon: (6 − 2) × 180 = 720°.", "The five known angles add up to 110 + 120 + 130 + 140 + 150 = 650°.", "The sixth angle is 720 − 650 = 70°."], "**70°**", hx),
   ("Example 2: regular polygon", "Each exterior angle of a regular polygon is 40°. How many sides has it and what is each interior angle? Can the polygon tessellate?",
    ["Number of sides = 360 ÷ 40 = 9.", "Interior angle = 180 − 40 = 140°.", "140 does not divide 360, so identical regular nonagons alone cannot fill the plane without gaps."], "**9 sides; 140°; no tessellation.**")],
  [N("Find the sum of the interior angles of an octagon.", 1080, "(8 − 2) × 180 = 1 080°.", unit="°"),
   N("Find each exterior angle of a regular pentagon.", 72, "360 ÷ 5 = 72°.", unit="°"),
   M("Each interior angle of a regular hexagon is:", "120°", ["60°", "108°", "135°"], "720 ÷ 6 = 120.")],
  [N("Each interior angle of a regular polygon is 156°. How many sides does it have?", 15, "Exterior = 24°; 360 ÷ 24 = 15.", ),
   O("Show that two regular octagons and one square fit together round a point.", "Each octagon angle is 135° and a square angle is 90°. 135 + 135 + 90 = 360°, so there is no gap.", "1 mark for 135°, 1 mark for the 90°, 1 mark for the sum of 360°.", points=3)],
  P("A floor design in Douala uses regular hexagonal tiles.",
    [pn("Find each interior angle of a regular hexagon (°).", 120, "720 ÷ 6.", unit="°", pts=1),
     pn("How many hexagons meet at one point?", 3, "360 ÷ 120 = 3.", pts=2),
     pm("Which statement is correct?", "Regular hexagons tessellate", ["Regular pentagons tessellate", "Regular octagons alone tessellate", "Regular heptagons tessellate"], "Only 3, 4 and 6 sides give interior angles dividing 360.", 2)]),
  [("The interior angles of a pentagon add up to:", "540°", ["360°", "720°", "900°"], "(5 − 2) × 180."),
   ("A regular polygon with exterior angle 60° has:", "6 sides", ["5 sides", "8 sides", "12 sides"], "360 ÷ 60."),
   ("The exterior angles of any polygon add up to:", "360°", ["180°", "540°", "720°"], "One full turn."),
   ("A polygon with 10 sides is a:", "decagon", ["nonagon", "octagon", "hexagon"], "Names."),
   ("Interior plus exterior angle at one vertex is:", "180°", ["90°", "360°", "270°"], "They lie on a straight line.")],
  notes=["The hexagon drawing uses six vertices computed at 60° intervals."])

def pol2(cx, cy, r, bearing):  # screen point at a given bearing from the centre
    return (round(cx + r * sind(bearing), 1), round(cy - r * cosd(bearing), 1))
bA = (200, 175); bB = pol2(*bA, 110, 65)
bf = shapes([LINE(*bA, 200, 45, dash=True, color="grey", arrow="end"), T(200, 36, "N", 14, bold=True), LINE(*bA, *bB, arrow="end", width=2), T(bA[0] - 12, bA[1] + 18, "A", 14), T(bB[0] + 10, bB[1] - 6, "B", 14, anchor="start"),
             T(236, 118, "065°", 13, color="red", bold=True)], 400, 220)
ship_a = (80, 200); ship_b = (200, 200); ship_c = (200, 150)
sf = shapes([LINE(*ship_a, *ship_b, width=2), LINE(*ship_b, *ship_c, width=2), LINE(*ship_a, *ship_c, color="red", width=2, dash=True), T(ship_a[0] - 14, 205, "A", 14), T(ship_b[0] + 14, 205, "B", 14),
             T(ship_c[0] + 14, ship_c[1], "C", 14, anchor="start"), T(140, 220, "12 km east", 12), T(250, 178, "5 km north", 12, anchor="start"), T(120, 160, "13 km", 13, color="red")], 400, 240)
import math as _mm
brg = _mm.degrees(_mm.atan2(12, 5)); assert abs(brg - 67.38) < 0.01 and _mm.hypot(12, 5) == 13 and (65 + 180) == 245 and 4 * 50000 / 100000 == 2
lesson(ch, "bearings-scale", "Bearings and scale drawing",
  ["Measure and write three-figure bearings", "Find a back bearing", "Use scales on maps and scale drawings", "Solve simple navigation problems"],
  [("definition", "Three-figure bearings", "A **bearing** is an angle measured **clockwise from North**, always written with three figures: North = 000°, East = 090°, South = 180°, West = 270°. The bearing of B from A means: stand at A, face North, then turn clockwise to face B."),
   ("methode", "Back bearings", "The bearing of A from B differs from the bearing of B from A by 180°. If B is on a bearing of 065° from A, then A is on a bearing of 065° + 180° = 245° from B. If the first bearing is more than 180°, **subtract** 180° instead."),
   ("methode", "Scales", "A map scale 1 : 50 000 means 1 cm on the map stands for 50 000 cm = 500 m on the ground. To find a real distance multiply the map distance by the scale; for a scale drawing choose a convenient scale, such as 1 cm : 2 km, draw the north line at every point and measure bearings with a protractor."),
   ("pieges", "Common mistakes", "- Measuring anticlockwise instead of clockwise.\n- Writing 65° instead of 065°.\n- Measuring the angle from the wrong point: the bearing 'of B from A' starts at A.")],
  [("Example 1: back bearing and scale", "B is on a bearing of 065° from A. Find the bearing of A from B. On a map with scale 1 : 50 000, A and B are 4 cm apart; find the real distance.",
    ["Back bearing = 065° + 180° = 245°.", "Real distance = 4 × 50 000 = 200 000 cm.", "200 000 cm = 2 000 m = 2 km."], "**245°; 2 km.**", bf),
   ("Example 2: ship", "A boat leaves port A and sails 12 km due east to B, then 5 km due north to C. Find AC and the bearing of C from A.",
    ["Right-angled triangle: AC² = 12² + 5² = 169, so AC = 13 km.", "Measured clockwise from North, the angle is tan⁻¹(east ÷ north) = tan⁻¹(12 ÷ 5) ≈ 67.4°.", "The bearing is 067.4°, i.e. about 067°."], "**AC = 13 km; bearing about 067°.**", sf)],
  [M("The bearing of East is:", "090°", ["180°", "270°", "009°"], "East is a quarter turn clockwise from North."),
   N("B is on a bearing of 120° from A. Find the bearing of A from B.", 300, "120 + 180 = 300°.", unit="°"),
   N("Map scale 1 : 25 000: 6 cm on the map is how many metres on the ground?", 1500, "6 × 25 000 = 150 000 cm = 1 500 m.", unit="m")],
  [N("C is on a bearing of 250° from D. Find the bearing of D from C.", 70, "250 − 180 = 70°, written 070°.", unit="°"),
   O("Explain why the bearing 'of B from A' is not the same as 'of A from B' and how they are related.", "The first starts at A and the second at B, with North lines at each point. The two bearings differ by 180°: add 180° if the first is under 180°, subtract 180° if it is over.", "1 mark for different starting points, 1 mark for 180°, 1 mark for the add/subtract rule.", points=3)],
  P("A hiker walks from Buea 8 km due north and then 6 km due east.",
    [pn("How far is the hiker in a straight line from Buea (km)?", 10, "√(8² + 6²) = 10.", unit="km", pts=2),
     pn("Find the bearing of the hiker from Buea to the nearest degree (use tan⁻¹(6/8)).", 37, "tan⁻¹(6 ÷ 8) = 36.9°, so 037°.", tol=0.5, unit="°", pts=2),
     pn("Find the bearing of Buea from the hiker (°).", 217, "37 + 180 = 217°.", tol=0.5, unit="°", pts=1)]),
  [("A bearing is measured:", "clockwise from North", ["anticlockwise from North", "from East", "from the equator"], "Standard convention."),
   ("South has the bearing:", "180°", ["090°", "270°", "000°"], "Half turn."),
   ("The bearing 045° points:", "North-east", ["South-west", "North-west", "South-east"], "Halfway between N and E."),
   ("On a 1 : 100 000 map, 3 cm represents:", "3 km", ["300 m", "30 km", "30 m"], "3 × 100 000 cm = 3 km."),
   ("Bearings should be written with:", "three figures", ["one figure", "two figures", "a letter"], "e.g. 007°.")],
  ill=(bf, "A at the centre, North dashed up, B on a bearing of 065° from A.", "A diagram with north line and a line from A to B at 65 degrees clockwise from north."),
  notes=["The bearing figure is schematic. In the ship example the bearing is computed with tan⁻¹ (right-angled trigonometry is formally taught in Form 3); check where the scheme introduces it."])

assert abs(2 * 22 / 7 * 35 - 220) < 1e-9 and 110000 / 220 == 500 and abs(22 / 7 * 14 ** 2 - 616) < 1e-9 and 20 * 14 + 0.5 * 22 / 7 * 49 == 357 and abs(2 * 20 + 14 + 0.5 * 22 / 7 * 14 - 76) < 1e-9
cf = shapes([CIRCLE(200, 120, 80), LINE(120, 120, 280, 120, dash=True, color="grey"), LINE(200, 120, 200 + 80 * cosd(50), 120 - 80 * sind(50), color="red", width=2),
             T(200, 112, "d", 14), T(262, 70, "r", 14, color="red", bold=True), T(200, 225, "C = 2πr = πd,   A = πr²", 14)], 400, 240)
lesson(ch, "circles", "Circles: circumference and area",
  ["Name the parts of a circle", "Calculate circumference and area", "Use π = 22/7 or 3.14 as the question directs", "Find perimeters and areas of shapes made from circles and rectangles"],
  [("definition", "Parts of a circle", "The **circumference** is the distance round the circle. The **radius** r goes from the centre to the circle, the **diameter** d = 2r goes through the centre. An **arc** is part of the circumference, a **chord** joins two points, and a **sector** is a slice like a pizza slice. The number π (about 3.14, or 22/7) is the ratio circumference ÷ diameter for every circle."),
   ("formule", "Formulae", "Circumference and area of a circle of radius r:", "C = 2 \\pi r \\qquad A = \\pi r^2"),
   ("methode", "Method", "Check whether you are given the radius or the diameter (r = d ÷ 2). Use π = 22/7 when the radius is a multiple of 7, otherwise use 3.14 or the π key; say which value you use. For a **semicircle**, take half of the circle but remember to add the diameter when you want the perimeter."),
   ("pieges", "Common mistakes", "- Using the diameter in A = πr² without halving it.\n- Mixing up circumference (a length, in cm) and area (in cm²).\n- Forgetting the straight edge when the perimeter of a semicircle is needed.")],
  [("Example 1: a wheel", "A bicycle wheel has a diameter of 70 cm (take π = 22/7). Find its circumference. How many turns does it make on a 1.1 km road?",
    ["Circumference = π × d = 22/7 × 70 = 220 cm.", "1.1 km = 110 000 cm.", "Turns = 110 000 ÷ 220 = 500."], "**220 cm; 500 turns.**", cf),
   ("Example 2: a compound shape", "A play area at Garoua is a rectangle 20 m by 14 m with a semicircle of diameter 14 m added on one short side (π = 22/7). Find its area and perimeter.",
    ["Area = 20 × 14 + ½ × 22/7 × 7² = 280 + 77 = 357 m².", "Curved edge = ½ × 22/7 × 14 = 22 m.", "Perimeter = 20 + 20 + 14 + 22 = 76 m (the diameter side is inside)."], "**357 m²; 76 m**")],
  [N("Find the circumference of a circle of radius 7 cm (π = 22/7).", 44, "2 × 22/7 × 7 = 44 cm.", unit="cm"),
   N("Find the area of a circle of radius 14 m (π = 22/7).", 616, "22/7 × 196 = 616 m².", unit="m²"),
   M("The circumference of a circle with diameter 10 cm (π = 3.14) is:", "31.4 cm", ["78.5 cm", "62.8 cm", "314 cm"], "π × d.")],
  [N("A circular pond has circumference 88 m. Find its radius (π = 22/7).", 14, "2 × 22/7 × r = 88, so r = 14 m.", unit="m"),
   O("A student says the area of a circle of diameter 10 cm is 3.14 × 10² = 314 cm². Find the mistake and give the correct area.", "She used the diameter instead of the radius. r = 5 cm, so A = 3.14 × 25 = 78.5 cm².", "1 mark for the mistake, 1 mark for r = 5, 1 mark for 78.5.", points=3)],
  P("A circular market square in Bafoussam has radius 21 m (take π = 22/7).",
    [pn("Find its circumference (m).", 132, "2 × 22/7 × 21 = 132.", unit="m", pts=1),
     pn("Find its area (m²).", 1386, "22/7 × 441 = 1 386.", unit="m²", pts=2),
     pn("Paving costs 1 500 FCFA per m². Find the cost in millions of FCFA to 2 d.p.", 2.08, "1 386 × 1 500 = 2 079 000 FCFA ≈ 2.08 million.", tol=0.01, pts=2)]),
  [("The diameter is:", "twice the radius", ["half the radius", "equal to the radius", "π times the radius"], "d = 2r."),
   ("The formula for the area of a circle:", "$\\pi r^2$", ["$2\\pi r$", "$\\pi d^2$", "$\\pi r$"], "Area uses r²."),
   ("Circumference of a circle with radius 7 (π = 22/7):", "44", ["154", "22", "14"], "2πr."),
   ("The line joining two points on a circle is a:", "chord", ["radius", "sector", "tangent"], "Definition."),
   ("A sector is:", "a slice bounded by two radii and an arc", ["a straight line", "the circle's centre", "a diameter"], "Pizza slice.")],
  ill=(cf, "A circle with diameter d, radius r and the formulae for C and A.", "A circle with a dashed diameter and a red radius."),
  notes=["Arc length and sector area are left for Form 3. Check which value of π the exam convention requires."])

assert 2 * 1.5 * 1 == 3 and 3 * 1000 == 3000 and abs(22 / 7 * 49 * 10 - 1540) < 1e-9 and 0.5 * 6 * 4 * 10 == 120 and abs(22 / 7 * 0.49 * 2 - 3.08) < 1e-9
cb = cuboid(2, 1.5, 1, "2 m", "1.5 m", "1 m")
lesson(ch, "volume", "Volume and capacity of prisms and cylinders",
  ["Calculate the volume of cuboids, cubes and prisms", "Calculate the volume of a cylinder", "Convert between m³, cm³ and litres", "Solve problems on tanks and containers"],
  [("formule", "Volume formulae", "The volume of a **prism** (any solid with a constant cross-section) is the area of the cross-section times the length. For a cuboid and a cylinder:", "V_{\\text{cuboid}} = l \\times w \\times h \\qquad V_{\\text{cylinder}} = \\pi r^2 h"),
   ("retenir", "Units of volume and capacity", "1 m³ = 1 000 000 cm³. 1 litre = 1 000 cm³, so 1 m³ = 1 000 litres. Volume is measured in cubic units (cm³, m³). **Capacity** is how much a container holds, in litres. Always convert all lengths to the same unit before multiplying."),
   ("methode", "Method for prisms", "Step 1: find the area of the cross-section (for a triangle ½ × base × height). Step 2: multiply by the length of the prism. Step 3: write the unit. A triangular prism with base 6 cm, height 4 cm and length 10 cm has V = ½ × 6 × 4 × 10 = 120 cm³."),
   ("pieges", "Common mistakes", "- Writing volume in square units.\n- Forgetting the ½ for a triangular cross-section.\n- Mixing cm and m in the same product.")],
  [("Example 1: a water tank", "A rectangular tank at Bamenda measures 2 m by 1.5 m by 1 m. Find its volume and how many litres it holds when full.",
    ["V = 2 × 1.5 × 1 = 3 m³.", "1 m³ = 1 000 litres.", "Capacity = 3 000 litres."], "**3 m³ = 3 000 litres.**", cb),
   ("Example 2: cylinder", "A tin is a cylinder with radius 7 cm and height 10 cm (π = 22/7). Find its volume in cm³ and litres.",
    ["V = π r² h = 22/7 × 7² × 10 = 22/7 × 490 = 1 540 cm³.", "1 000 cm³ = 1 litre.", "V = 1.54 litres."], "**1 540 cm³ = 1.54 litres.**")],
  [N("Find the volume of a cube of edge 5 cm.", 125, "5³ = 125 cm³.", unit="cm³"),
   N("Find the volume of a cuboid 8 cm by 5 cm by 3 cm.", 120, "8 × 5 × 3.", unit="cm³"),
   M("2 500 cm³ is how many litres?", "2.5 litres", ["25 litres", "250 litres", "0.25 litres"], "Divide by 1 000.")],
  [N("A triangular prism has a triangular face with base 6 cm and height 4 cm, and is 10 cm long. Find its volume.", 120, "½ × 6 × 4 × 10 = 120 cm³.", unit="cm³"),
   O("A cylinder has radius 14 cm and height 5 cm. Show that its volume is 3 080 cm³ (π = 22/7).", "V = 22/7 × 14² × 5 = 22/7 × 196 × 5 = 22 × 28 × 5 = 3 080 cm³.", "1 mark for the formula, 1 mark for substituting r = 14, 1 mark for 3 080.", points=3)],
  P("A cylindrical water tank at Kumba has radius 0.7 m and height 2 m (π = 22/7).",
    [pn("Find its volume in m³.", 3.08, "22/7 × 0.49 × 2 = 3.08.", tol=0.001, unit="m³", pts=2),
     pn("Find its capacity in litres.", 3080, "3.08 × 1 000.", unit="l", pts=1),
     pn("Water costs 500 FCFA per m³. How much does it cost to fill the tank (FCFA)?", 1540, "3.08 × 500 = 1 540.", unit="FCFA", pts=2)]),
  [("The unit of volume is:", "cm³", ["cm²", "cm", "kg"], "Cubic units."),
   ("1 m³ equals:", "1 000 litres", ["100 litres", "10 litres", "1 000 000 litres"], "Common conversion."),
   ("The volume of a cylinder is:", "$\\pi r^2 h$", ["$2\\pi r h$", "$\\pi r h$", "$\\frac{1}{3}\\pi r^2 h$"], "Base area times height."),
   ("A cuboid 4 m by 3 m by 2 m has volume:", "24 m³", ["9 m³", "24 m²", "12 m³"], "4 × 3 × 2."),
   ("For capacity, 1 litre equals:", "1 000 cm³", ["100 cm³", "10 cm³", "10 000 cm³"], "Standard.")],
  ill=(cb, "A cuboid tank 2 m long, 1.5 m wide and 1 m high.", "A drawing of a rectangular box with its three dimensions labelled."),
  notes=["Cones, pyramids and spheres are left for Form 3."])

# ===================================================================== DATA
ch = p.chapter("data", "Statistics", REF)

fr = [(0, 3), (1, 7), (2, 6), (3, 3), (4, 1)]
tot = sum(f for _, f in fr); sfx = sum(x * f for x, f in fr)
assert tot == 20 and sfx == 32 and sfx / tot == 1.6 and 10 / 1 and 3 + 7 == 10 and 3 + 7 + 6 == 16
assert [100 / 240 * 360, 60 / 240 * 360, 40 / 240 * 360] == [150.0, 90.0, 60.0]
pf = pie([("Walk 150°", 150), ("Taxi 90°", 90), ("Bicycle 60°", 60), ("Car 60°", 60)])
lesson(ch, "statistics-tables-pie", "Frequency tables, mean and pie charts",
  ["Find the mean, median and mode from a frequency table", "Draw and read a pie chart", "Compare sets of data using averages and range", "Interpret statistics from real situations"],
  [("methode", "Mean from a frequency table", "Add a column f × x (value times frequency). **Mean = Σfx ÷ Σf**, where Σ means 'the sum of'. The **mode** is the value with the highest frequency. For the **median** use the cumulative frequency to find the middle position: the (n + 1) ÷ 2th value."),
   ("methode", "Pie charts", "A pie chart shows how a total is shared. The angle of a sector is $\\frac{\\text{frequency}}{\\text{total}} \\times 360^\\circ$. Draw a circle, draw one radius, and measure each angle in turn with a protractor, in order. The angles must add up to 360°."),
   ("pieges", "Common mistakes", "- Dividing Σfx by the number of values in the table instead of by Σf.\n- Taking the median as the middle row of the table rather than the middle of all the data.\n- Pie chart angles that do not add up to 360°.")],
  [("Example 1: frequency table", "20 families in a neighbourhood of Limbe were asked how many children they have: 0 children (3 families), 1 (7), 2 (6), 3 (3), 4 (1). Find the mean, median and mode.",
    ["Σf = 20 and Σfx = 0×3 + 1×7 + 2×6 + 3×3 + 4×1 = 32. Mean = 32 ÷ 20 = 1.6.", "Mode = 1 child (frequency 7).", "Median: the 10th and 11th values. Cumulative frequencies are 3, 10, 16: the 10th is 1 and the 11th is 2, so the median is 1.5."], "**Mean 1.6, mode 1, median 1.5.**",
    ptable(["Children x", "0", "1", "2", "3", "4"], [["Families f", "3", "7", "6", "3", "1"], ["f × x", "0", "7", "12", "9", "4"]], colw=75)),
   ("Example 2: pie chart", "240 pupils travel to school: 100 walk, 60 take a taxi, 40 cycle and 40 come by car. Find the angle for each sector.",
    ["Each pupil is 360 ÷ 240 = 1.5°.", "Walk: 100 × 1.5 = 150°; taxi: 60 × 1.5 = 90°.", "Bicycle: 40 × 1.5 = 60°; car: 60°. Total 150 + 90 + 60 + 60 = 360°."], "**150°, 90°, 60°, 60°**", pf)],
  [N("Find the mean of the data: value 1 (frequency 2), value 2 (frequency 3), value 3 (frequency 5).", 2.3, "(2 + 6 + 15) ÷ 10 = 2.3."),
   N("In a pie chart for 120 people, how many degrees represent 30 people?", 90, "30 ÷ 120 × 360.", unit="°"),
   M("The mode of a frequency table is:", "the value with the largest frequency", ["the largest value", "the middle value", "the total frequency"], "Definition.")],
  [N("The shoe sizes of 10 pupils: size 38 (3), size 39 (4), size 40 (3). Find the mean size.", 39, "(114 + 156 + 120) ÷ 10 = 39.", ),
   O("Class A has a mean mark of 12 and a range of 4; class B has a mean mark of 12 and a range of 12. Compare the classes.", "The average mark is the same, but class A's marks are close together, while class B's are widely spread, so class A is more consistent.", "1 mark for equal means, 1 mark for the range comparison, 1 mark for the conclusion about spread.", points=3)],
  P("The pie chart shows how 240 pupils travel to school: walk 150°, taxi 90°, bicycle 60° and car 60°.",
    [pn("How many pupils walk?", 100, "150 ÷ 360 × 240 = 100.", pts=1),
     pn("What percentage take a taxi (%)?", 25, "90 ÷ 360 × 100.", unit="%", pts=2),
     pm("Which statement is true?", "As many pupils cycle as come by car", ["More pupils cycle than take a taxi", "Fewer walk than cycle", "Taxi has the largest sector"], "Both sectors are 60°.", 1)], figure=pf),
  [("To find the mean from a frequency table we divide Σfx by:", "Σf", ["the number of rows", "the largest value", "the mode"], "Σf is the number of values."),
   ("A pie chart sector for 1/4 of the total has angle:", "90°", ["45°", "180°", "25°"], "360 ÷ 4."),
   ("The angles of a pie chart add up to:", "360°", ["180°", "100°", "90°"], "Full circle."),
   ("In a frequency table, the mean of values x with frequencies f is:", "Σfx ÷ Σf", ["Σx ÷ Σf", "Σf ÷ Σx", "Σfx"], "Standard formula."),
   ("The range measures:", "spread of data", ["the middle value", "the most common value", "the total"], "Largest − smallest.")],
  ill=(pf, "Pie chart of how 240 pupils travel to school.", "A pie chart with sectors for walking 150°, taxi 90°, bicycle 60° and car 60°."),
  notes=["Grouped frequency tables and histograms are left for Form 3-4."])

p.write()
