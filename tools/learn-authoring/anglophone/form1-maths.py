"""Generates form1-maths (run: python3 form1-maths.py)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from sm_common import *

REF = "Cameroon secondary (general education) Mathematics programme, Form 1 (MINESEC) — to be checked against the official programme"
p = mkpack(1, "", REF, "A first year of secondary mathematics: whole numbers and directed numbers, factors, fractions, percentages, ratio, sets, algebra basics, equations, angles, area and data handling.")

# ===================================================================== NUMBER
ch = p.chapter("number", "Number work", REF)

assert 8 + 4 * (12 - 7) ** 2 // 10 == 18 and 4 * 25 % 10 == 0
assert 5 * (3 + 4) - 6 // 2 == 32 and 28 * 52 == 1456 and 24 * 3500 == 84000
lesson(ch, "whole-numbers", "Whole numbers, order of operations and rounding",
  ["Read and write whole numbers using place value", "Use the order of operations (BODMAS)", "Round numbers to the nearest 10, 100 or 1 000", "Estimate an answer before calculating"],
  [("definition", "Place value", "In a whole number every digit has a **place value**: units, tens, hundreds, thousands and so on. In 4 305 the digit 4 means 4 thousands, 3 means 3 hundreds, 0 means no tens and 5 means 5 units. A zero keeps the other digits in their right places."),
   ("methode", "Order of operations (BODMAS)", "Work in this order: **B**rackets, **O**rders (powers and roots), **D**ivision and **M**ultiplication (from left to right), then **A**ddition and **S**ubtraction (from left to right). Example: 2 + 3 × 4 = 2 + 12 = 14, not 20."),
   ("methode", "Rounding", "To round to the nearest 10 (or 100, 1 000) look at the digit just to the right. If it is **5 or more**, round up; if it is **less than 5**, keep the digit and replace the rest with zeros. Estimating means rounding each number first, then calculating quickly."),
   ("pieges", "Common mistakes", "- Calculating from left to right and ignoring BODMAS.\n- Rounding 4 876 to the nearest hundred as 4 800: the next digit is 7, so the answer is 4 900.\n- Forgetting that 6 ÷ 2 × 3 is worked from left to right: it is 9, not 1.")],
  [("Example 1: BODMAS", "Calculate 8 + 4 × (12 − 7)² ÷ 10.",
    ["Brackets first: 12 − 7 = 5.", "Order: 5² = 25. The sum is now 8 + 4 × 25 ÷ 10.", "Multiply and divide from the left: 4 × 25 = 100, then 100 ÷ 10 = 10.", "Add: 8 + 10 = 18."], "**18**"),
   ("Example 2: rounding and estimating", "Round 4 876 to the nearest 10 and the nearest 100. Then estimate 28 × 52 and compare with the exact answer.",
    ["Nearest 10: the units digit is 6 (5 or more), so 4 876 → 4 880.", "Nearest 100: the tens digit is 7, so 4 876 → 4 900.", "Estimate: 28 ≈ 30 and 52 ≈ 50, so 30 × 50 = 1 500.", "Exact: 28 × 52 = 1 456. The estimate is close, so the answer is reasonable."], "**4 880; 4 900; estimate 1 500, exact 1 456.**")],
  [N("Calculate 5 × (3 + 4) − 6 ÷ 2.", 32, "Brackets: 7. Then 5 × 7 = 35 and 6 ÷ 2 = 3. So 35 − 3 = 32."),
   M("Round 6 450 to the nearest 100.", "6 500", ["6 400", "6 000", "6 450"], "The tens digit is 5, so round up to 6 500."),
   M("What is the value of the digit 7 in 37 412?", "7 thousands", ["7 hundreds", "7 tens", "7 ten-thousands"], "37 412 = 3 ten-thousands + 7 thousands + 4 hundreds + 1 ten + 2 units.")],
  [N("A trader in Douala buys 24 bags of rice at 3 500 FCFA each. How much does she pay in all (FCFA)?", 84000, "24 × 3 500 = 84 000 FCFA.", unit="FCFA"),
   O("Explain, with an example, why BODMAS is needed.", "Without an agreed order, 2 + 3 × 4 could be 20 or 14. The rule says multiplication comes before addition, so the answer is always 2 + 12 = 14.", "1 mark for the idea of one agreed order; 1 mark for a correct example; 1 mark for the right result.", points=3)],
  P("A school in Bamenda buys 36 desks at 8 450 FCFA each.",
    [pn("Estimate the total cost by rounding 8 450 to the nearest 1 000 and 36 to the nearest 10 (answer in FCFA).", 320000, "8 450 → 8 000 and 36 → 40, so 40 × 8 000 = 320 000 FCFA.", unit="FCFA", pts=2),
     pn("Calculate the exact total (FCFA).", 304200, "36 × 8 450 = 304 200 FCFA.", unit="FCFA", pts=2),
     pm("The school has 350 000 FCFA. Is that enough?", "Yes, with 45 800 FCFA left", ["No, 45 800 FCFA short", "Yes, with 4 200 FCFA left", "No, 4 200 FCFA short"], "350 000 − 304 200 = 45 800.", 2)]),
  [("In whole numbers, which operation is done first in 7 + 2 × 5?", "Multiplication", ["Addition", "Subtraction", "Whichever is on the left"], "BODMAS puts multiplication before addition."),
   ("Value of 7 + 2 × 5:", "17", ["45", "35", "14"], "2 × 5 = 10, then 7 + 10 = 17."),
   ("Round 3 649 to the nearest 100:", "3 600", ["3 700", "3 650", "3 000"], "The tens digit 4 is below 5."),
   ("In the number 5 062, the digit 0 stands for:", "no hundreds", ["no units", "no thousands", "nothing at all"], "It holds the hundreds place."),
   ("To estimate 48 × 21 we can calculate:", "50 × 20", ["40 × 30", "50 × 10", "40 × 10"], "Round each number to the nearest 10.")],
  ill=(ptable(["Thousands", "Hundreds", "Tens", "Units"], [["4", "3", "0", "5"]], colw=90), "Place value in 4 305.", "A table with the digits 4, 3, 0 and 5 under thousands, hundreds, tens and units."),
  notes=["BODMAS wording: some schools use BIDMAS or PEMDAS; both are shown as equivalent."])

assert -7 + 12 == 5 and -3 - 4 == -7 and (-6) * (-4) == 24 and 20 // (-5) == -4 and 3 - (-8) == 11
lesson(ch, "directed-numbers", "Directed numbers (positive and negative)",
  ["Place positive and negative numbers on a number line and order them", "Add and subtract directed numbers", "Multiply and divide directed numbers using the sign rules", "Solve simple problems with temperature and money"],
  [("definition", "Directed numbers", "**Directed numbers** have a sign: positive (+) or negative (−). On a number line, numbers increase to the right. −5 is smaller than −2, and any negative number is smaller than any positive number. Negative numbers describe temperatures below 0 °C, money owed, or a level below the sea."),
   ("methode", "Adding and subtracting", "Adding a negative number is the same as subtracting: 5 + (−3) = 5 − 3 = 2. Subtracting a negative number is the same as adding: 3 − (−8) = 3 + 8 = 11. On the number line, add means move right, subtract means move left."),
   ("propriete", "Sign rules for × and ÷", "- same signs give a **positive** answer: (−6) × (−4) = 24 and (−20) ÷ (−5) = 4;\n- different signs give a **negative** answer: 6 × (−4) = −24 and 20 ÷ (−5) = −4."),
   ("pieges", "Common mistakes", "- Thinking −9 is larger than −2: on the number line −9 is further left.\n- Writing 3 − (−8) = −5; two minus signs together make a plus: 3 + 8 = 11.\n- Applying the 'two negatives make a positive' rule to addition: −3 + (−4) = −7.")],
  [("Example 1: add and subtract", "Calculate (a) −7 + 12, (b) −3 − 4, (c) 3 − (−8).",
    ["(a) Start at −7 and move 12 to the right: −7 + 12 = 5.", "(b) Start at −3 and move 4 to the left: −7.", "(c) Subtracting −8 is adding 8: 3 + 8 = 11."], "**(a) 5, (b) −7, (c) 11**"),
   ("Example 2: multiply and divide", "Calculate (a) (−6) × (−4), (b) 20 ÷ (−5), (c) (−2) × 3 × (−5).",
    ["(a) Same signs, positive: 6 × 4 = 24.", "(b) Different signs, negative: 20 ÷ 5 = 4, so −4.", "(c) (−2) × 3 = −6, then (−6) × (−5) = 30."], "**(a) 24, (b) −4, (c) 30**")],
  [N("Calculate −9 + 4.", -5, "Start at −9 and move 4 to the right: −5."),
   N("Calculate (−8) × (−3).", 24, "Same signs give a positive: 24."),
   M("Which list is in increasing order?", "−7, −2, 0, 3", ["3, 0, −2, −7", "−2, −7, 0, 3", "0, −2, 3, −7"], "Smaller numbers are further left on the number line.")],
  [N("The temperature on top of a high mountain is −4 °C in the morning and rises by 9 °C by noon. What is the noon temperature?", 5, "−4 + 9 = 5 °C.", unit="°C"),
   N("Calculate 6 − (−9) − 10.", 5, "6 + 9 = 15, and 15 − 10 = 5.")],
  P("Ngu keeps a note of his savings in FCFA. A negative number means money he owes his brother.",
    [pn("On Monday Ngu has −2 000 (he owes 2 000). He earns 5 000. What is the new balance (FCFA)?", 3000, "−2 000 + 5 000 = 3 000.", unit="FCFA", pts=1),
     pn("On Tuesday he spends 4 500. What is the balance (FCFA)?", -1500, "3 000 − 4 500 = −1 500: he owes 1 500 FCFA.", unit="FCFA", pts=2),
     pm("Which statement is true about −1 500 and −2 000?", "−1 500 is greater than −2 000", ["−1 500 is smaller than −2 000", "They are equal", "Neither can be compared"], "−1 500 is to the right of −2 000 on the number line.", 2)]),
  [("The value of −5 − (−2):", "−3", ["−7", "3", "7"], "−5 + 2 = −3."),
   ("The product (−7) × 3:", "−21", ["21", "−4", "4"], "Different signs give a negative."),
   ("The quotient (−18) ÷ (−6):", "3", ["−3", "12", "−12"], "Same signs give a positive."),
   ("The smallest of these directed numbers: 2, −9, −1, 0", "−9", ["2", "−1", "0"], "−9 is furthest left."),
   ("Adding a negative number to a number is like:", "subtracting a positive number", ["multiplying it", "dividing it", "doing nothing"], "5 + (−3) = 5 − 3.")],
  ill=(num_line(-6, 6, range(-6, 7), 1, w=420, h=130, filled=[-2, 3]), "Number line from −6 to 6: −2 lies left of 3.", "A number line from minus 6 to 6 with points marked at −2 and 3."),
  notes=["The first key block mentions temperature; check that the wording is suitable."])

import math as _m
assert _m.gcd(36, 48) == 12 and 36 * 48 // 12 == 144 and 2**3 * 3**2 * 5 == 360 and _m.lcm(12, 18) == 36 and _m.gcd(84, 126) == 42
assert 2 * 2 * 3 * 5 == 60
tree = shapes([T(200, 22, "60", 16, bold=True), LINE(190, 30, 130, 60), LINE(210, 30, 270, 60), T(125, 78, "6", 16), T(275, 78, "10", 16),
               LINE(118, 85, 85, 115), LINE(132, 85, 165, 115), LINE(268, 85, 235, 115), LINE(282, 85, 315, 115),
               T(80, 132, "2", 16, color="red", bold=True), T(168, 132, "3", 16, color="red", bold=True), T(230, 132, "2", 16, color="red", bold=True), T(318, 132, "5", 16, color="red", bold=True)], 400, 150)
lesson(ch, "factors-multiples", "Factors, multiples, primes, HCF and LCM",
  ["Find the factors and multiples of a number", "Recognise prime numbers and write a number as a product of primes", "Find the HCF and the LCM of two numbers", "Use divisibility tests"],
  [("definition", "Factors, multiples and primes", "A **factor** of a number divides it exactly: the factors of 12 are 1, 2, 3, 4, 6, 12. A **multiple** of 12 is in its times table: 12, 24, 36… A **prime number** has exactly two factors, 1 and itself (2, 3, 5, 7, 11, 13…). The number 1 is not prime."),
   ("retenir", "Divisibility tests", "- by 2: the last digit is even;\n- by 3: the sum of the digits is divisible by 3;\n- by 5: the last digit is 0 or 5;\n- by 9: the sum of the digits is divisible by 9;\n- by 10: the last digit is 0."),
   ("methode", "HCF and LCM by prime factors", "Write each number as a product of primes (use a factor tree). The **HCF** (highest common factor) is the product of the primes common to both, with the **smallest** powers. The **LCM** (lowest common multiple) is the product of all primes, with the **largest** powers."),
   ("pieges", "Common mistakes", "- Saying 1 is a prime number, or that 2 is not prime (2 is the only even prime).\n- Mixing up HCF (a factor, never bigger than the numbers) and LCM (a multiple, never smaller).\n- Stopping a factor tree before every branch ends in a prime.")],
  [("Example 1: prime factors", "Write 60 as a product of prime numbers.",
    ["Split 60 = 6 × 10.", "Split again: 6 = 2 × 3 and 10 = 2 × 5, so every end is prime.", "60 = 2 × 2 × 3 × 5 = $2^2 \\times 3 \\times 5$."], "**60 = 2² × 3 × 5**", tree),
   ("Example 2: HCF and LCM", "Find the HCF and LCM of 36 and 48.",
    ["36 = $2^2 \\times 3^2$ and 48 = $2^4 \\times 3$.", "HCF: smallest powers, $2^2 \\times 3 = 12$.", "LCM: largest powers, $2^4 \\times 3^2 = 144$.", "Check: HCF × LCM = 12 × 144 = 1 728 = 36 × 48."], "**HCF = 12, LCM = 144**")],
  [N("Find the HCF of 18 and 24.", 6, "18 = 2 × 3², 24 = 2³ × 3; the common smallest powers give 2 × 3 = 6."),
   M("Which number is prime?", "29", ["27", "33", "39"], "27, 33 and 39 are all divisible by 3; 29 has no other factor."),
   N("Find the LCM of 6 and 8.", 24, "Multiples of 8: 8, 16, 24; 24 is the first one divisible by 6.")],
  [N("Two buses leave the Bafoussam station together. One leaves every 12 minutes and the other every 18 minutes. After how many minutes do they next leave together?", 36, "LCM of 12 and 18 is 36 minutes.", unit="min"),
   O("Is 4 725 divisible by 3? By 5? By 9? Give reasons.", "The digit sum is 4+7+2+5 = 18, which is divisible by 3 and by 9, so yes to both. The last digit is 5, so it is divisible by 5 as well.", "1 mark for each correct test and conclusion.", points=3)],
  P("A tailor in Garoua has two pieces of cloth, 84 m and 126 m long. He cuts both into equal lengths with no waste.",
    [pn("Write 84 as a product of primes and find the number of 2s used (an integer).", 2, "84 = 2 × 2 × 3 × 7, so two 2s.", pts=1),
     pn("What is the greatest possible length of each piece (m)?", 42, "HCF(84, 126) = 42.", unit="m", pts=2),
     pn("How many pieces does he get in total?", 5, "84 ÷ 42 = 2 and 126 ÷ 42 = 3, total 5.", pts=2)]),
  [("The factors of 10 are:", "1, 2, 5, 10", ["2, 5", "1, 10", "10, 20, 30"], "A factor divides 10 exactly."),
   ("Which statement is true about factors and prime numbers?", "2 is the only even prime", ["1 is prime", "9 is prime", "all odd numbers are prime"], "Every other even number has the factor 2."),
   ("The HCF of 12 and 20 is:", "4", ["60", "2", "8"], "12 = 2² × 3 and 20 = 2² × 5."),
   ("The LCM of 4 and 10 is:", "20", ["40", "2", "14"], "First common multiple."),
   ("A number is divisible by 9 when:", "the digit sum is divisible by 9", ["it ends in 9", "it is odd", "it ends in 0"], "Test for 9.")],
  ill=(tree, "A factor tree for 60 ending in primes 2, 3, 2, 5.", "A factor tree: 60 splits into 6 and 10, then into the primes 2, 3, 2 and 5."),
  notes=["Prime factor notation uses powers; check that powers are introduced in Form 1 in the official scheme."])

# ===================================================================== FRACTIONS, PERCENT, RATIO
ch = p.chapter("fractions", "Fractions, percentages and ratio", REF)

assert Fraction(2, 3) + Fraction(3, 4) == Fraction(17, 12) and Fraction(5, 6) - Fraction(1, 4) == Fraction(7, 12)
assert Fraction(3, 4) * Fraction(8, 9) == Fraction(2, 3) and Fraction(3, 5) / Fraction(9, 10) == Fraction(2, 3)
assert Fraction(7, 3) == Fraction(2) + Fraction(1, 3) and Fraction(3, 8) * 480 == 180
frac_fig = shapes([RECT(40, 40, 320, 50, fill="white", width=2)] + [RECT(40 + i * 80, 40, 80, 50, fill="lightorange" if i < 3 else "white", width=2) for i in range(4)] +
                  [T(200, 120, "3 out of 4 equal parts shaded: 3/4", 14)], 400, 150)
lesson(ch, "fractions", "Fractions and mixed numbers",
  ["Simplify and compare fractions", "Add, subtract, multiply and divide fractions", "Convert between mixed numbers and improper fractions", "Solve word problems with fractions"],
  [("definition", "Fractions", "A fraction $\\frac{a}{b}$ shows a part of a whole: b equal parts, a of them taken. $\\frac{3}{4}$ means 3 of 4 equal parts. Equivalent fractions have the same value: $\\frac{1}{2} = \\frac{2}{4} = \\frac{3}{6}$. To simplify, divide the top and bottom by their HCF. A mixed number such as $2\\frac{1}{3}$ equals the improper fraction $\\frac{7}{3}$."),
   ("methode", "Adding and subtracting", "Write both fractions with the same denominator (use the LCM), then add or subtract the numerators and keep the denominator. Example: $\\frac{2}{3} + \\frac{3}{4} = \\frac{8}{12} + \\frac{9}{12} = \\frac{17}{12} = 1\\frac{5}{12}$."),
   ("methode", "Multiplying and dividing", "To multiply, multiply the tops and multiply the bottoms (cancel first if you can). To divide by a fraction, **multiply by its reciprocal** (turn it upside down): $\\frac{3}{5} \\div \\frac{9}{10} = \\frac{3}{5} \\times \\frac{10}{9} = \\frac{2}{3}$. Change mixed numbers to improper fractions first."),
   ("pieges", "Common mistakes", "- Adding the denominators: $\\frac{1}{2} + \\frac{1}{3}$ is not $\\frac{2}{5}$.\n- Finding a common denominator for × and ÷; it is not needed.\n- Flipping the first fraction instead of the one you divide by.")],
  [("Example 1: add and subtract", "Calculate $\\frac{2}{3} + \\frac{3}{4}$ and $\\frac{5}{6} - \\frac{1}{4}$.",
    ["LCM of 3 and 4 is 12: $\\frac{8}{12} + \\frac{9}{12} = \\frac{17}{12} = 1\\frac{5}{12}$.", "LCM of 6 and 4 is 12: $\\frac{10}{12} - \\frac{3}{12} = \\frac{7}{12}$."], "**$1\\frac{5}{12}$ and $\\frac{7}{12}$**"),
   ("Example 2: fraction of a quantity", "A farmer in Bafoussam harvests 480 kg of maize and sells $\\frac{3}{8}$ of it. How much does he sell and how much is left?",
    ["Divide by the denominator: 480 ÷ 8 = 60.", "Multiply by the numerator: 60 × 3 = 180 kg sold.", "Left: 480 − 180 = 300 kg."], "**180 kg sold; 300 kg left.**", frac_fig)],
  [N("Calculate $\\frac{1}{2} + \\frac{1}{4}$ as a decimal.", 0.75, "$\\frac{2}{4} + \\frac{1}{4} = \\frac{3}{4} = 0.75$."),
   M("Simplify $\\frac{18}{24}$.", "$\\frac{3}{4}$", ["$\\frac{9}{12}$", "$\\frac{6}{8}$", "$\\frac{2}{3}$"], "Divide by the HCF 6: $\\frac{3}{4}$; the others are not fully simplified or are wrong."),
   N("Write $\\frac{17}{5}$ as a mixed number: what is the whole-number part?", 3, "17 ÷ 5 = 3 remainder 2, so $3\\frac{2}{5}$.")],
  [N("Calculate $\\frac{3}{5} \\div \\frac{9}{10}$ as a decimal (3 d.p.).", 0.667, "$\\frac{3}{5} \\times \\frac{10}{9} = \\frac{2}{3} \\approx 0.667$.", tol=0.001),
   O("A student writes $\\frac{1}{3} + \\frac{1}{4} = \\frac{2}{7}$. Explain the mistake and give the correct answer.", "She added the numerators and the denominators. The fractions need a common denominator: $\\frac{4}{12} + \\frac{3}{12} = \\frac{7}{12}$.", "1 mark for the mistake, 1 mark for the common denominator, 1 mark for 7/12.", points=3)],
  P("Mama Ngo in Kumba shares 360 balls of fufu corn among three markets. The first gets $\\frac{1}{3}$ and the second gets $\\frac{1}{4}$.",
    [pn("How many go to the first market?", 120, "360 ÷ 3 = 120.", pts=1),
     pn("How many go to the second market?", 90, "360 ÷ 4 = 90.", pts=1),
     pn("What fraction of the balls goes to the third market? Give a decimal to 3 d.p.", 0.417, "1 − 1/3 − 1/4 = 5/12 ≈ 0.417; that is 150 balls.", tol=0.001, pts=2),
     pn("How many balls go to the third market?", 150, "360 − 120 − 90 = 150.", pts=1)]),
  [("A fraction equivalent to $\\frac{2}{5}$:", "$\\frac{4}{10}$", ["$\\frac{2}{10}$", "$\\frac{5}{2}$", "$\\frac{3}{6}$"], "Multiply top and bottom by 2."),
   ("To divide by $\\frac{2}{3}$ we multiply by:", "$\\frac{3}{2}$", ["$\\frac{2}{3}$", "$\\frac{3}{3}$", "$\\frac{2}{2}$"], "Use the reciprocal."),
   ("The improper fraction for $3\\frac{1}{2}$:", "$\\frac{7}{2}$", ["$\\frac{4}{2}$", "$\\frac{5}{2}$", "$\\frac{3}{2}$"], "3 × 2 + 1 = 7."),
   ("What is $\\frac{1}{2}$ of 18 kg of cassava?", "9 kg", ["36 kg", "6 kg", "2 kg"], "Divide by 2."),
   ("The value of $\\frac{1}{3} \\times \\frac{3}{4}$:", "$\\frac{1}{4}$", ["$\\frac{4}{7}$", "$\\frac{1}{12}$", "$\\frac{4}{3}$"], "Multiply tops and bottoms and cancel: $\\frac{3}{12}$.")],
  ill=(frac_fig, "A bar divided into four equal parts, three shaded.", "A rectangle split into four equal parts with three of them shaded."),
  notes=["Question 4 of the exam problem is a conditional on the previous parts; check clarity."])

assert 0.15 * 24000 == 3600 and 12500 * 1.08 == 13500 and 45000 * 0.8 == 36000 and 18 / 40 == 0.45 and 3 / 8 == 0.375
lesson(ch, "decimals-percentages", "Decimals and percentages",
  ["Convert between fractions, decimals and percentages", "Find a percentage of a quantity", "Increase and decrease a quantity by a percentage", "Express one quantity as a percentage of another"],
  [("definition", "Percentages", "A **percentage** is a fraction out of 100: 45% means $\\frac{45}{100} = 0.45$. To change a fraction to a percentage, divide and multiply by 100: $\\frac{3}{8} = 0.375 = 37.5\\%$. To change a percentage to a decimal, divide by 100."),
   ("methode", "Percentage of a quantity", "To find p% of an amount, multiply the amount by $\\frac{p}{100}$. Example: 15% of 24 000 FCFA = 0.15 × 24 000 = 3 600 FCFA. To express a as a percentage of b, calculate $\\frac{a}{b} \\times 100$: 18 out of 40 is $\\frac{18}{40} \\times 100 = 45\\%$."),
   ("methode", "Increase and decrease", "To **increase** by p%, multiply by $1 + \\frac{p}{100}$; to **decrease** by p%, multiply by $1 - \\frac{p}{100}$. A price of 12 500 FCFA increased by 8% becomes 12 500 × 1.08 = 13 500 FCFA. A price of 45 000 FCFA reduced by 20% becomes 45 000 × 0.8 = 36 000 FCFA."),
   ("pieges", "Common mistakes", "- Giving the **amount of the change** when the question asks for the new price.\n- Calculating the percentage on the wrong quantity (always use the **original** value).\n- Writing 0.5% for one half: one half is 50%.")],
  [("Example 1: percentage of an amount", "A trader gives a 15% discount on a bag of rice that costs 24 000 FCFA. Find the discount and the new price.",
    ["Discount = 15% of 24 000 = 0.15 × 24 000 = 3 600 FCFA.", "New price = 24 000 − 3 600 = 20 400 FCFA.", "Check by the multiplier 0.85: 24 000 × 0.85 = 20 400."], "**Discount 3 600 FCFA; new price 20 400 FCFA.**"),
   ("Example 2: as a percentage", "In a class of 40 pupils at a school in Limbe, 18 are girls. What percentage are girls? What percentage are boys?",
    ["Girls: $\\frac{18}{40} \\times 100 = 45\\%$.", "Boys: 40 − 18 = 22 pupils, so $\\frac{22}{40} \\times 100 = 55\\%$.", "Check: 45% + 55% = 100%."], "**45% girls, 55% boys.**")],
  [N("Write $\\frac{3}{8}$ as a percentage.", 37.5, "3 ÷ 8 = 0.375 and 0.375 × 100 = 37.5%.", unit="%"),
   N("Find 20% of 9 500 FCFA.", 1900, "0.2 × 9 500 = 1 900.", unit="FCFA"),
   M("The decimal for 7% is:", "0.07", ["0.7", "7.0", "0.007"], "Divide by 100.")],
  [N("The price of a bag of cement at Yaoundé is 12 500 FCFA and rises by 8%. What is the new price (FCFA)?", 13500, "12 500 × 1.08 = 13 500.", unit="FCFA"),
   N("A phone costing 45 000 FCFA is sold with a 20% reduction. Find the selling price (FCFA).", 36000, "45 000 × 0.8 = 36 000.", unit="FCFA")],
  P("At a market in Bamenda, 250 kg of tomatoes are bought. 10% is spoiled and the rest is sold at 400 FCFA per kg.",
    [pn("How many kilograms are spoiled?", 25, "10% of 250 = 25 kg.", unit="kg", pts=1),
     pn("How many kilograms are sold?", 225, "250 − 25 = 225 kg.", unit="kg", pts=1),
     pn("How much money is received (FCFA)?", 90000, "225 × 400 = 90 000 FCFA.", unit="FCFA", pts=2)]),
  [("45% as a decimal:", "0.45", ["4.5", "0.045", "45"], "45 ÷ 100."),
   ("As a percentage, one fifth is:", "20%", ["5%", "15%", "25%"], "0.2 × 100."),
   ("To decrease by 10% we multiply by:", "0.9", ["0.1", "1.1", "10"], "1 − 0.1 = 0.9."),
   ("What is 50% of 2 000 FCFA?", "1 000 FCFA", ["500 FCFA", "100 FCFA", "2 050 FCFA"], "Half of the amount."),
   ("12 out of 48 expressed as a percentage:", "25%", ["12%", "48%", "4%"], "12/48 = 1/4.")],
  ill=(bars([("Girls", 45, "orange"), ("Boys", 55, "blue")], unit="%", h=260), "Percentage of girls and boys in a class of 40.", "A bar chart with girls 45 percent and boys 55 percent."),
  notes=["The bar chart tallest bar is 55 with automatic scale; check that the label does not overlap the top gridline."])

assert 90000 * 2 // 9 == 20000 and 3500 / 5 * 8 == 5600 and 6 * 10 / 4 == 15 and 50000 // 5 * 2 == 20000
rb = shapes([RECT(50 + i * 60, 70, 60, 50, fill="lightorange" if i < 2 else "lightblue", width=2) for i in range(5)] +
            [T(50 + i * 60 + 30, 100, "10 000", 12) for i in range(5)] + [T(110, 58, "Ako: 20 000", 13, bold=True), T(260, 58, "Beri: 30 000", 13, bold=True),
             T(200, 150, "50 000 FCFA shared in the ratio 2 : 3", 13)], 400, 180)
lesson(ch, "ratio-proportion", "Ratio and proportion",
  ["Write and simplify ratios", "Share a quantity in a given ratio", "Solve direct proportion problems by the unitary method", "Recognise inverse proportion in simple situations"],
  [("definition", "Ratio", "A **ratio** compares quantities of the same kind: 20 000 FCFA to 30 000 FCFA is the ratio 20 000 : 30 000 = 2 : 3. Simplify a ratio by dividing both parts by the same number. Both quantities must be in the same unit (change 2 m and 50 cm to 200 cm : 50 cm = 4 : 1)."),
   ("methode", "Sharing in a ratio", "To share an amount in the ratio a : b: add the parts (a + b), divide the amount by the total number of parts to find **one part**, then multiply by a and by b. Check that the shares add up to the amount."),
   ("methode", "Direct and inverse proportion", "In **direct proportion** both quantities grow together: find the value of **one unit**, then multiply. If 5 kg cost 3 500 FCFA, 1 kg costs 700 FCFA and 8 kg cost 5 600 FCFA. In **inverse proportion** one quantity grows when the other falls: 6 workers need 10 days, so the total work is 60 worker-days, and 4 workers need 60 ÷ 4 = 15 days."),
   ("pieges", "Common mistakes", "- Putting the parts in the wrong order: 'boys to girls' is not 'girls to boys'.\n- Dividing the amount by one part of the ratio instead of the total number of parts.\n- Treating an inverse situation (more workers, fewer days) as direct.")],
  [("Example 1: sharing", "Ako and Beri share 50 000 FCFA in the ratio 2 : 3. How much does each get?",
    ["Total parts: 2 + 3 = 5.", "One part: 50 000 ÷ 5 = 10 000 FCFA.", "Ako: 2 × 10 000 = 20 000 FCFA; Beri: 3 × 10 000 = 30 000 FCFA.", "Check: 20 000 + 30 000 = 50 000."], "**Ako 20 000 FCFA, Beri 30 000 FCFA.**", rb),
   ("Example 2: proportion", "(a) 5 kg of tomatoes cost 3 500 FCFA. Find the cost of 8 kg. (b) 6 workers build a wall in 10 days. How long would 4 workers take?",
    ["(a) 1 kg costs 3 500 ÷ 5 = 700 FCFA, so 8 kg cost 8 × 700 = 5 600 FCFA.", "(b) Work = 6 × 10 = 60 worker-days.", "(b) With 4 workers: 60 ÷ 4 = 15 days."], "**(a) 5 600 FCFA; (b) 15 days.**")],
  [N("Simplify the ratio 24 : 36: give the first number of the simplest form.", 2, "Divide by 12: 2 : 3."),
   N("Share 90 000 FCFA in the ratio 2 : 3 : 4. How much is the smallest share (FCFA)?", 20000, "9 parts; one part is 10 000; smallest share is 2 × 10 000.", unit="FCFA"),
   M("The ratio 2 m : 50 cm in its simplest form is:", "4 : 1", ["2 : 50", "1 : 25", "40 : 1"], "200 cm : 50 cm = 4 : 1.")],
  [N("A taxi in Douala uses 12 litres of fuel for 150 km. How many litres for 250 km?", 20, "1 km uses 12 ÷ 150 = 0.08 litre; 250 × 0.08 = 20.", unit="l"),
   N("8 workers dig a drain in 9 days. How many days would 12 workers need (same pace)?", 6, "72 worker-days ÷ 12 = 6.", unit="days")],
  P("A cocoa farmer at Mbalmayo mixes fertiliser and sand in the ratio 1 : 4 to make 30 kg of mixture.",
    [pn("How many parts are there in all?", 5, "1 + 4 = 5.", pts=1),
     pn("How much fertiliser is in the mixture (kg)?", 6, "30 ÷ 5 = 6 kg.", unit="kg", pts=2),
     pn("How much sand is in the mixture (kg)?", 24, "4 × 6 = 24 kg.", unit="kg", pts=2)]),
  [("The ratio 10 : 15 simplified is:", "2 : 3", ["10 : 3", "5 : 3", "3 : 2"], "Divide both by 5."),
   ("Sharing 60 in the ratio 1 : 2 gives:", "20 and 40", ["30 and 30", "10 and 50", "15 and 45"], "3 parts of 20."),
   ("Which pair is in direct proportion?", "kilograms of rice and its cost", ["workers and days to build a house", "speed and journey time", "pupils and chalk left"], "Twice the rice costs twice as much."),
   ("If 3 books cost 2 400 FCFA, one book costs:", "800 FCFA", ["7 200 FCFA", "600 FCFA", "2 397 FCFA"], "Divide by 3."),
   ("More workers on the same job means:", "fewer days", ["more days", "the same days", "no work"], "Inverse proportion.")],
  ill=(rb, "A bar of five equal parts sharing 50 000 FCFA in the ratio 2 : 3.", "A bar split in five equal parts, two for Ako and three for Beri."),
  notes=["Inverse proportion is only introduced informally here; the official scheme may place it in Form 2."])

# ===================================================================== SETS & ALGEBRA
ch = p.chapter("algebra", "Sets and algebra", REF)

vf = venn2(6, 4, 8, 7, "French", "English")
assert 6 + 4 + 8 + 7 == 25 and 6 + 4 == 10 and 4 + 8 == 12
lesson(ch, "sets-basics", "Sets and Venn diagrams",
  ["Describe a set by listing or by a rule", "Use the symbols ∈, ⊂, ∪, ∩ and ∅", "Draw and read a Venn diagram with two sets", "Solve a simple survey problem"],
  [("definition", "Sets", "A **set** is a well-defined collection of objects, written in curly brackets: A = {2, 4, 6, 8}. Each object is an **element**: 4 ∈ A means '4 belongs to A'; 5 ∉ A. The number of elements is n(A) = 4. The empty set ∅ has no elements. B is a **subset** of A, written B ⊂ A, if every element of B is in A."),
   ("propriete", "Union and intersection", "- The **union** A ∪ B contains the elements that are in A **or** in B (or both).\n- The **intersection** A ∩ B contains the elements in **both** A and B.\n- The **universal set** ξ contains all the elements being discussed; the **complement** A′ holds the elements of ξ not in A.\n- n(A ∪ B) = n(A) + n(B) − n(A ∩ B)."),
   ("methode", "Venn diagrams", "Draw a rectangle for ξ and one circle per set, overlapping if the sets share elements. Fill the **intersection first**, then the rest of each set, and finally the outside. The numbers in all regions add up to n(ξ)."),
   ("pieges", "Common mistakes", "- Counting the elements in the overlap twice when finding n(A ∪ B).\n- Writing the elements of the intersection in only one circle.\n- Forgetting the elements that are in neither set (outside the circles but inside the rectangle).")],
  [("Example 1: listing", "A = {1, 2, 3, 4, 5, 6} and B = {2, 4, 6, 8}. Find A ∩ B, A ∪ B and n(A ∪ B).",
    ["A ∩ B = elements in both = {2, 4, 6}.", "A ∪ B = all elements in either, listed once = {1, 2, 3, 4, 5, 6, 8}.", "n(A ∪ B) = 7. Check: 6 + 4 − 3 = 7."], "**A ∩ B = {2, 4, 6}; A ∪ B = {1, 2, 3, 4, 5, 6, 8}; n(A ∪ B) = 7**"),
   ("Example 2: survey", "In a class of 25 pupils in Yaoundé, 10 speak French at home, 12 speak English at home, and 4 speak both. How many speak only French, only English, and neither?",
    ["Both = 4. Only French = 10 − 4 = 6; only English = 12 − 4 = 8.", "At least one language: 6 + 4 + 8 = 18.", "Neither = 25 − 18 = 7."], "**6 only French, 8 only English, 7 neither.**", vf)],
  [N("A = {3, 6, 9, 12}. Find n(A).", 4, "Count the elements: 4."),
   M("Which symbol means 'is an element of'?", "∈", ["⊂", "∪", "∩"], "∈ is membership; ⊂ is subset."),
   N("n(A) = 8, n(B) = 5 and n(A ∩ B) = 2. Find n(A ∪ B).", 11, "8 + 5 − 2 = 11.")],
  [N("In a class of 30, 18 play football, 14 play handball and 6 play both. How many play neither?", 4, "18 + 14 − 6 = 26 play at least one; 30 − 26 = 4.", ),
   O("List all the subsets of {a, b}.", "∅, {a}, {b} and {a, b}: four subsets.", "1 mark each for ∅ and {a, b}, 1 mark for the two single-element sets.", points=3)],
  P("Of 40 families surveyed in a Kumba neighbourhood, 22 grow cassava (C), 15 grow plantain (P) and 7 grow both.",
    [pn("How many families grow only cassava?", 15, "22 − 7 = 15.", pts=1),
     pn("How many families grow only plantain?", 8, "15 − 7 = 8.", pts=1),
     pn("How many families grow neither?", 10, "15 + 7 + 8 = 30 grow something; 40 − 30 = 10.", pts=2)]),
  [("The set {2, 4, 6} ∩ {4, 6, 8} is:", "{4, 6}", ["{2, 4, 6, 8}", "{2, 8}", "∅"], "Elements common to both."),
   ("The union of {1, 2} and {2, 3} contains:", "3 elements", ["2 elements", "4 elements", "1 element"], "{1, 2, 3}."),
   ("The empty set has:", "0 elements", ["1 element", "the element 0", "infinitely many elements"], "∅ = { }."),
   ("In a Venn diagram, the overlap of two circles shows:", "the intersection", ["the union", "the complement", "the empty set"], "Elements belonging to both sets."),
   ("If B ⊂ A, then:", "every element of B is in A", ["every element of A is in B", "A and B have nothing in common", "n(B) > n(A)"], "That is the definition of subset.")],
  ill=(vf, "A Venn diagram: 6 speak only French, 4 both, 8 only English, 7 neither.", "Two overlapping circles labelled French and English with 6, 4, 8 inside and 7 outside."),
  notes=["Set-builder notation is not introduced; check whether the official Form 1 scheme requires it."])

assert 3*2 - 4*2 == -2 and 2 * 3 ** 2 - (-2) == 20 and 4 * 5 + 12 == 32
ar = shapes([RECT(60, 50, 200, 100, fill="lightyellow", width=2), LINE(160, 50, 160, 150, dash=True, color="grey"),
             T(110, 105, "4x", 15), T(210, 105, "12", 15), T(110, 40, "x", 14), T(210, 40, "3", 14), T(285, 105, "4", 14, anchor="start"),
             T(160, 180, "Area = 4(x + 3) = 4x + 12", 14)], 400, 200)
lesson(ch, "algebra-basics", "Introduction to algebra",
  ["Use letters to stand for numbers", "Simplify expressions by collecting like terms", "Substitute numbers into expressions", "Expand brackets with a number outside"],
  [("definition", "Letters and terms", "In algebra a letter stands for a number. 3a means 3 × a. A **term** is a number, a letter or a product such as 5b or x². **Like terms** have the same letters: 3a and 5a are like terms, but 3a and 3b are not. An **expression** has terms joined by + and −; it has no equals sign."),
   ("methode", "Simplifying and expanding", "Collect like terms by adding or subtracting their numbers: 3a + 5b − a + 2b = 2a + 7b. To **expand** a bracket, multiply every term inside by the number outside: 3(2x − 4) = 6x − 12. Take care with signs: −2(x − 5) = −2x + 10."),
   ("methode", "Substitution", "Replace each letter by its value, keep brackets round negative numbers, then use BODMAS. If x = 3 and y = −2, then 2x² − y = 2 × 3² − (−2) = 18 + 2 = 20."),
   ("pieges", "Common mistakes", "- Adding unlike terms: 3a + 2b is **not** 5ab.\n- Expanding only the first term: 3(x + 4) is 3x + 12, not 3x + 4.\n- Writing x² as 2x: x² means x × x, so with x = 3 it is 9, not 6.")],
  [("Example 1: simplify and expand", "Simplify 4a + 3b − a + 2b, then expand 3(2x − 4).",
    ["Collect the a terms: 4a − a = 3a. Collect the b terms: 3b + 2b = 5b. So 3a + 5b.", "Expand: 3 × 2x = 6x and 3 × (−4) = −12, so 6x − 12."], "**3a + 5b; 6x − 12**"),
   ("Example 2: a rectangle", "A rectangular plot at Buea has length (x + 3) m and width 4 m. Write its area in expanded form and find it when x = 5.",
    ["Area = 4(x + 3) = 4x + 12.", "Substitute x = 5: 4 × 5 + 12 = 32.", "The area is 32 m²."], "**4x + 12; 32 m²**", ar)],
  [N("Simplify 7x − 3x + 2 and find its value when x = 2.", 10, "7x − 3x + 2 = 4x + 2; with x = 2, 8 + 2 = 10."),
   M("Expand 5(y − 2).", "5y − 10", ["5y − 2", "5y + 10", "y − 10"], "Multiply both terms by 5."),
   N("Find 3a − b when a = 4 and b = −5.", 17, "3 × 4 − (−5) = 12 + 5 = 17.")],
  [N("Find the value of 2x² − y when x = 3 and y = −2.", 20, "2 × 9 + 2 = 20."),
   O("Explain why 3a + 2b cannot be simplified to 5ab.", "3a and 2b are unlike terms: a and b are different numbers, so they cannot be added. Example: a = 1, b = 2 gives 3 + 4 = 7, but 5ab = 10.", "1 mark for like/unlike terms; 1 mark for a numerical check; 1 mark for the conclusion.", points=3)],
  P("A mobile-money agent in Limbe charges 100 FCFA plus 2 FCFA for each 100 FCFA sent. For n hundreds the charge is C = 100 + 2n.",
    [pn("Find the charge for sending 5 000 FCFA (n = 50).", 200, "100 + 2 × 50 = 200 FCFA.", unit="FCFA", pts=1),
     pn("Find n when the charge is 160 FCFA.", 30, "100 + 2n = 160, so 2n = 60 and n = 30.", pts=2),
     pm("Which expression gives the charge for a person sending x hundreds twice?", "2(100 + 2x)", ["100 + 4x + 100x", "200 + 2x + 2x + 100", "100 + 2x²"], "Two transfers each cost 100 + 2x: 2(100 + 2x) = 200 + 4x.", 2)]),
  [("In 5x the number 5 is called:", "the coefficient", ["the variable", "the power", "the constant term"], "It multiplies the letter."),
   ("Simplify 2a + 3a:", "5a", ["5a²", "6a", "5"], "Add the coefficients."),
   ("Expand 2(x + 6):", "2x + 12", ["2x + 6", "x + 12", "2x + 8"], "Multiply every term."),
   ("When n = 4, the value of n² is:", "16", ["8", "6", "2"], "4 × 4."),
   ("Which of these are like terms?", "4xy and 7xy", ["3x and 3y", "x² and x", "2a and 2"], "Same letters.")],
  ill=(ar, "A rectangle of width 4 split into x and 3: area 4x + 12.", "A rectangle split into two parts with areas 4x and 12."),
  notes=["The mobile-money charge formula is invented for practice and is not a real tariff; flagged for a teacher check."])

assert 3 * 5 + 7 == 22 and 2 * (10 - 3) == 10 + 4 and 14 + 3 * 14 == 56
ef = shapes([RECT(10, 40, 70, 40, fill="lightyellow"), T(45, 65, "x", 15), LINE(80, 60, 110, 60, arrow="end"), RECT(110, 40, 70, 40, fill="lightblue"), T(145, 65, "× 3", 15),
             LINE(180, 60, 210, 60, arrow="end"), RECT(210, 40, 70, 40, fill="lightblue"), T(245, 65, "+ 7", 15), LINE(280, 60, 310, 60, arrow="end"), RECT(310, 40, 70, 40, fill="lightorange"), T(345, 65, "22", 15),
             LINE(310, 120, 280, 120, arrow="end"), LINE(210, 120, 180, 120, arrow="end"), T(195, 150, "undo: − 7", 12), T(345, 150, "undo: ÷ 3", 12),
             T(345, 115, "5", 15, color="red", bold=True)], 400, 180)
lesson(ch, "linear-equations", "Solving linear equations",
  ["Solve one-step and two-step equations", "Solve equations with brackets and with x on both sides", "Form an equation from a word problem and solve it", "Check an answer by substitution"],
  [("definition", "Equation", "An **equation** states that two expressions are equal: 3x + 7 = 22. To **solve** it means finding the value of x that makes it true. We keep the equation balanced: whatever we do to one side we do to the other side."),
   ("methode", "Method", "Use **inverse operations** to undo what has been done to x, in reverse order. 3x + 7 = 22: subtract 7 to get 3x = 15, then divide by 3 to get x = 5. Check: 3 × 5 + 7 = 22. With x on both sides, move the x terms to one side and the numbers to the other."),
   ("methode", "Brackets and word problems", "Expand brackets first: 2(x − 3) = x + 4 becomes 2x − 6 = x + 4, so x = 10. For a word problem, define a letter (let x be …), write the equation from the story, solve it, and answer in words with the unit."),
   ("pieges", "Common mistakes", "- Doing the operation on only one side of the equation.\n- Dividing only the x term: in 3x + 6 = 15 divide **everything** by 3, or first subtract 6.\n- Changing the sign wrongly when a term moves across the equals sign.")],
  [("Example 1: two-step and brackets", "Solve (a) 3x + 7 = 22 and (b) 2(x − 3) = x + 4.",
    ["(a) Subtract 7: 3x = 15. Divide by 3: x = 5. Check 3 × 5 + 7 = 22.", "(b) Expand: 2x − 6 = x + 4.", "(b) Subtract x: x − 6 = 4. Add 6: x = 10. Check 2 × 7 = 14 = 10 + 4."], "**(a) x = 5; (b) x = 10**", ef),
   ("Example 2: a word problem", "A mother is 3 times as old as her son. The sum of their ages is 56. Find the son's age.",
    ["Let the son's age be x; the mother's age is 3x.", "x + 3x = 56, so 4x = 56.", "x = 14. Mother: 3 × 14 = 42. Check: 14 + 42 = 56."], "**The son is 14 years old.**")],
  [N("Solve x + 9 = 15.", 6, "Subtract 9 from both sides."),
   N("Solve 4x − 3 = 17.", 5, "4x = 20, so x = 5."),
   M("Solve 5x = 40:", "x = 8", ["x = 35", "x = 45", "x = 200"], "Divide both sides by 5.")],
  [N("Solve 5x + 2 = 3x + 14.", 6, "2x = 12, so x = 6."),
   N("Solve 3(x − 2) = 15.", 7, "x − 2 = 5, so x = 7.")],
  P("A trader in Douala buys 3 sacks of rice and pays 2 500 FCFA for transport. The total bill is 77 500 FCFA. Let x FCFA be the price of one sack.",
    [pm("Which equation fits the story?", "3x + 2 500 = 77 500", ["3x − 2 500 = 77 500", "x + 3 + 2 500 = 77 500", "3 + x = 77 500"], "Three sacks cost 3x, plus transport.", 1),
     pn("Solve for x (FCFA).", 25000, "3x = 75 000, so x = 25 000.", unit="FCFA", pts=2),
     pn("The trader sells each sack for 28 500 FCFA. What is her profit on the three sacks, excluding transport (FCFA)?", 10500, "3 × (28 500 − 25 000) = 10 500.", unit="FCFA", pts=2)]),
  [("The solution of x − 4 = 10 is:", "14", ["6", "40", "−6"], "Add 4 to both sides."),
   ("To solve 2x + 5 = 11 the first step is to:", "subtract 5", ["divide by 5", "add 5", "multiply by 2"], "Undo + 5 first."),
   ("Solve 3x = −12:", "x = −4", ["x = 4", "x = −36", "x = 36"], "Divide by 3."),
   ("If x = 3, which equation is true?", "2x + 1 = 7", ["x − 1 = 4", "3x = 6", "x + 5 = 6"], "2 × 3 + 1 = 7."),
   ("Solve x/2 = 6:", "x = 12", ["x = 3", "x = 4", "x = 8"], "Multiply both sides by 2.")],
  ill=(ef, "Flow chart for 3x + 7 = 22 and the inverse steps.", "A flow chart: x times 3 plus 7 gives 22; undoing with minus 7 then divide by 3 gives 5."),
  notes=["Equations with fractions are left for Form 2."])

# ===================================================================== GEOMETRY
ch = p.chapter("geometry", "Geometry and measurement", REF)

assert 6 * 25 + 30 == 180 and 25 + 60 + 95 == 180 and 180 - 65 == 115
cx_, cy_ = 200, 140
ang = math.radians(65)
ex_, ey_ = 110 * math.cos(ang), 110 * math.sin(ang)
def pol(r, deg): return (round(cx_ + r * cosd(deg), 1), round(cy_ - r * sind(deg), 1))
ia = shapes([LINE(70, cy_, 330, cy_, width=2), LINE(cx_ - ex_, cy_ + ey_, cx_ + ex_, cy_ - ey_, width=2),
             T(*pol(48, 32.5), "65°", 13, color="red", bold=True), T(*pol(52, 122.5), "115°", 13, color="red", bold=True),
             T(*pol(52, 212.5), "65°", 13, color="red", bold=True), T(*pol(48, 302.5), "115°", 13, color="red", bold=True)], 400, 250)
lesson(ch, "angles", "Angles and parallel lines",
  ["Name types of angle and measure them with a protractor", "Use angle facts on a straight line, round a point and for vertically opposite angles", "Use corresponding, alternate and co-interior angles on parallel lines", "Find unknown angles using equations"],
  [("definition", "Types of angle", "An angle measures a turn. An **acute** angle is less than 90°, a **right** angle is exactly 90°, an **obtuse** angle is between 90° and 180°, a **straight** angle is 180° and a **reflex** angle is between 180° and 360°. Two angles are **complementary** if they add up to 90° and **supplementary** if they add up to 180°."),
   ("propriete", "Angle facts", "- Angles on a straight line add up to 180°.\n- Angles round a point add up to 360°.\n- **Vertically opposite** angles (made by two crossing lines) are equal.\n- Check measuring: place the centre of the protractor on the vertex and read the scale that starts at 0° on one arm."),
   ("propriete", "Parallel lines", "A line crossing two parallel lines makes: **corresponding** angles (F shape) that are equal; **alternate** angles (Z shape) that are equal; **co-interior** angles (C or U shape) that add up to 180°. These rules only work when the lines are parallel."),
   ("pieges", "Common mistakes", "- Reading the wrong scale on the protractor: an obtuse angle cannot be 40°.\n- Using Z or F rules on lines that are not parallel.\n- Forgetting that co-interior angles add up to 180° but are not equal.")],
  [("Example 1: straight line", "Three angles on a straight line are x, 2x + 10 and 3x + 20 (in degrees). Find x and the angles.",
    ["The angles add up to 180°: x + 2x + 10 + 3x + 20 = 180.", "6x + 30 = 180, so 6x = 150 and x = 25.", "The angles are 25°, 60° and 95°. Check: 25 + 60 + 95 = 180."], "**x = 25; angles 25°, 60°, 95°.**"),
   ("Example 2: crossing lines", "Two straight lines cross and one angle is 65°. Find the other three angles.",
    ["The angle next to it is on a straight line: 180° − 65° = 115°.", "The angle vertically opposite 65° is also 65°.", "The angle vertically opposite 115° is also 115°."], "**65°, 115°, 65°, 115°.**", ia)],
  [N("Find the angle that is supplementary to 112°.", 68, "180 − 112 = 68°.", unit="°"),
   N("Two straight lines cross. One angle is 38°. Find the angle next to it.", 142, "180 − 38 = 142°.", unit="°"),
   M("A transversal crosses two parallel lines. Alternate angles are:", "equal", ["supplementary", "always 90°", "never equal"], "Z-shaped angles are equal.")],
  [N("Four angles round a point are 90°, 110°, x and 2x. Find x.", 53.333, "90 + 110 + 3x = 360, so 3x = 160 and x = 53.3°.", tol=0.01, unit="°"),
   O("Two parallel roads are crossed by a third road. One angle at the first junction is 70°. Find the co-interior angle at the second junction and explain.", "Co-interior angles add up to 180° between parallel lines, so the angle is 180° − 70° = 110°.", "1 mark for the rule, 1 mark for the subtraction, 1 mark for 110°.", points=3)],
  P("Two parallel roads in Yaoundé are crossed by an avenue. At the first junction an angle is 65°.",
    [pn("Find the corresponding angle at the second junction (°).", 65, "Corresponding angles are equal.", unit="°", pts=1),
     pn("Find the co-interior angle on the same side of the avenue as the 65° at the first junction (°).", 115, "Co-interior angles add up to 180°: 180 − 65 = 115.", unit="°", pts=2),
     pm("Which fact do you use for alternate angles?", "They are equal when the lines are parallel", ["They add up to 180°", "They are always 90°", "They are equal for any two lines"], "The Z rule needs parallel lines.", 1)]),
  [("A 120° angle is:", "obtuse", ["acute", "right", "reflex"], "Between 90° and 180°."),
   ("Angles round a point add up to:", "360°", ["180°", "90°", "270°"], "A full turn."),
   ("Vertically opposite angles are:", "equal", ["supplementary", "complementary", "unrelated"], "Made by crossing lines."),
   ("The complement of 35° is:", "55°", ["145°", "35°", "65°"], "90 − 35."),
   ("Co-interior angles between parallel lines add up to:", "180°", ["90°", "360°", "they are equal"], "C-shape rule.")],
  ill=(ia, "Two crossing lines: the four angles are 65°, 115°, 65°, 115°.", "Two lines crossing; opposite angles equal 65 and 115 degrees."),
  notes=["The figure is schematic; angle marks are not drawn to scale with arcs."])

# triangle drawing
AB = 240; A_ = (80, 190); B_ = (80 + AB, 190); tt = AB * sind(60) / sind(70)
C_ = (round(80 + tt * cosd(50), 1), round(190 - tt * sind(50), 1))
tri_ = shapes([POLY([*A_, *B_, *C_], fill="lightyellow"), T(A_[0] + 40, 182, "50°", 13, color="red", bold=True), T(B_[0] - 40, 182, "60°", 13, color="red", bold=True),
               T(C_[0], C_[1] + 38, "x", 14, color="red", bold=True), T(A_[0] - 12, 205, "A", 14), T(B_[0] + 14, 205, "B", 14), T(C_[0], C_[1] - 10, "C", 14)], 400, 230)
assert 180 - 50 - 60 == 70 and 180 - 2 * 40 == 100 and 360 - (80 + 100 + 120) == 60
lesson(ch, "triangles-quadrilaterals", "Triangles and quadrilaterals",
  ["Classify triangles by sides and by angles", "Use the angle sum of a triangle and the exterior angle property", "Name the special quadrilaterals and their properties", "Use the angle sum of a quadrilateral"],
  [("definition", "Triangles", "A triangle has three sides and three angles that **add up to 180°**. By sides: **equilateral** (3 equal sides, all angles 60°), **isosceles** (2 equal sides and the two angles opposite them equal), **scalene** (no equal sides). By angles: acute, right (one angle 90°) or obtuse (one angle over 90°). The **exterior angle** of a triangle equals the sum of the two opposite interior angles."),
   ("propriete", "Quadrilaterals", "The angles of a quadrilateral add up to 360°. A **square** has 4 equal sides and 4 right angles. A **rectangle** has opposite sides equal and 4 right angles. A **parallelogram** has opposite sides parallel and equal. A **rhombus** has 4 equal sides. A **trapezium** has one pair of parallel sides. A **kite** has two pairs of equal adjacent sides."),
   ("pieges", "Common mistakes", "- Thinking a square is not a rectangle; a square is a special rectangle and a special rhombus.\n- Forgetting that the two base angles of an isosceles triangle are equal.\n- Using 360° for a triangle or 180° for a quadrilateral.")],
  [("Example 1: triangle angles", "In triangle ABC, angle A = 50° and angle B = 60°. Find angle C. Then find the angles of an isosceles triangle whose apex angle is 40°.",
    ["Angle C = 180° − 50° − 60° = 70°.", "In the isosceles triangle the two base angles are equal: (180° − 40°) ÷ 2 = 70°.", "So its angles are 40°, 70°, 70°."], "**C = 70°; 40°, 70°, 70°.**", tri_),
   ("Example 2: quadrilateral", "Three angles of a quadrilateral are 80°, 100° and 120°. Find the fourth. Then find all the angles of a parallelogram with one angle of 80°.",
    ["The four angles add up to 360°: 80 + 100 + 120 = 300, so the fourth angle is 60°.", "In a parallelogram opposite angles are equal, so another angle is 80°.", "Neighbouring angles add up to 180°, so the other two are 100° each. Check: 80 + 100 + 80 + 100 = 360."], "**60°; the parallelogram has 80°, 100°, 80°, 100°.**")],
  [N("Two angles of a triangle are 35° and 80°. Find the third.", 65, "180 − 115 = 65°.", unit="°"),
   M("A triangle with two equal sides is called:", "isosceles", ["scalene", "equilateral", "right"], "Isosceles has two equal sides."),
   N("The three angles of a triangle are x, x + 20 and x + 40. Find x.", 40, "3x + 60 = 180, so x = 40.", unit="°")],
  [N("An exterior angle of a triangle is 125° and one opposite interior angle is 70°. Find the other opposite interior angle.", 55, "The exterior angle is the sum of the two opposite interior angles: 125 − 70 = 55°.", unit="°"),
   O("List three properties of a rhombus.", "Four equal sides; opposite sides parallel; opposite angles equal; diagonals cross at right angles and bisect each other.", "1 mark for each correct property, up to 3.", points=3)],
  P("The roof of a house in Bafoussam is an isosceles triangle with apex angle 100°.",
    [pn("Find each base angle (°).", 40, "(180 − 100) ÷ 2 = 40°.", unit="°", pts=2),
     pn("A support beam makes an exterior angle at one base corner. Find that exterior angle (°).", 140, "Exterior angle = 180 − 40 = 140° (or 40 + 100).", unit="°", pts=2),
     pm("What type of triangle by angles is the roof?", "Obtuse", ["Acute", "Right", "Reflex"], "The apex angle is 100°, more than 90°.", 1)]),
  [("The angles of a triangle add up to:", "180°", ["360°", "90°", "270°"], "Triangle angle sum."),
   ("A quadrilateral with 4 equal sides and no right angles is a:", "rhombus", ["rectangle", "square", "trapezium"], "Rhombus."),
   ("The angles of a quadrilateral add up to:", "360°", ["180°", "540°", "720°"], "Two triangles."),
   ("An equilateral triangle has each angle:", "60°", ["90°", "45°", "30°"], "180 ÷ 3."),
   ("A trapezium has:", "one pair of parallel sides", ["no parallel sides", "four equal sides", "four right angles"], "Definition.")],
  ill=(tri_, "Triangle ABC with angles 50°, 60° and x = 70°.", "A triangle with angles 50 and 60 degrees at the base and x at the top."),
  notes=["Some textbooks define a trapezium as having at least one pair of parallel sides, others exactly one; confirm the local convention."])

off = math.sqrt(100 ** 2 - 80 ** 2); assert abs(off - 60) < 1e-9
pb = shapes([CIRCLE(140, 150, 100, stroke="grey", width=1), CIRCLE(300, 150, 100, stroke="grey", width=1), LINE(100 + 0, 150, 340, 150, width=2) if False else LINE(140, 150, 300, 150, width=2),
             LINE(220, 150 - off - 15, 220, 150 + off + 15, color="red", width=2), T(125, 165, "A", 14), T(315, 165, "B", 14), T(232, 170, "M", 14, anchor="start")], 440, 300)
lesson(ch, "constructions", "Constructions with ruler and compasses",
  ["Construct a triangle given three sides", "Construct the perpendicular bisector of a line", "Construct the bisector of an angle", "Construct angles of 60° and 90°"],
  [("methode", "Perpendicular bisector", "To bisect the segment AB: set the compasses to **more than half** of AB. Draw an arc above and below from A, then the same arcs from B. Join the two crossing points with a ruler. This line cuts AB at its midpoint M at 90°. Every point on it is the same distance from A and from B."),
   ("methode", "Angle bisector and a 60° angle", "To bisect an angle at O: draw an arc that cuts both arms; from those two points draw equal arcs that cross inside the angle; join O to the crossing. To make **60°**: from A draw an arc of any radius cutting the base at P; with the same radius, centre P, cut the arc at Q; join A to Q. Bisect that to get 30°."),
   ("methode", "Triangle with three sides", "To construct a triangle with sides 7 cm, 5 cm and 4 cm: draw the longest side AB = 7 cm. With compasses at A and radius 5 cm draw an arc; with compasses at B and radius 4 cm draw another arc. The arcs cross at C. Join AC and BC. Never rub out the arcs."),
   ("pieges", "Common mistakes", "- Changing the compass setting between the two arcs of a bisector: use the same radius.\n- Setting the radius to **less than half** of AB: the arcs will not cross.\n- Measuring with a ruler instead of using arcs when a construction is asked: show all construction marks.")],
  [("Example 1: perpendicular bisector", "AB = 8 cm. Describe how to find the point equidistant from A and B on a road map, and state the distance AM.",
    ["Open the compasses to 5 cm (more than 4 cm, half of AB).", "Draw arcs from A and from B above and below AB; join the crossing points.", "The line crosses AB at M, the midpoint, so AM = 4 cm."], "**M is at 4 cm from A; the bisector is at 90° to AB.**", pb),
   ("Example 2: triangle", "Describe the construction of triangle ABC with AB = 7 cm, AC = 5 cm and BC = 4 cm. Is it possible?",
    ["Check: 5 + 4 = 9 is more than 7, so the arcs will cross: a triangle exists.", "Draw AB = 7 cm; arc radius 5 cm from A; arc radius 4 cm from B.", "Mark the crossing point C and join AC and BC."], "**Possible; C is where the two arcs meet.**")],
  [M("The perpendicular bisector of AB passes through:", "the midpoint of AB at 90°", ["A only", "B only", "a point 1 cm from A"], "It cuts AB in two equal parts at a right angle."),
   N("To bisect a 80° angle you get two angles of how many degrees each?", 40, "80 ÷ 2 = 40°.", unit="°"),
   TF("A triangle with sides 3 cm, 4 cm and 8 cm can be constructed.", False, "3 + 4 = 7 is less than 8, so the arcs do not meet.")],
  [N("Which angle do you get by bisecting a 60° angle (construction)?", 30, "Half of 60° is 30°.", unit="°"),
   O("List the steps to construct a 90° angle at a point P on a line.", "Draw equal arcs on both sides of P on the line; with a larger radius, draw arcs from those two points that cross above P; join P to the crossing: this is the perpendicular.", "1 mark for the equal arcs, 1 mark for the crossing arcs, 1 mark for joining.", points=3)],
  P("A surveyor wants a point P that is the same distance from two villages A and B, 10 km apart.",
    [pm("On which line does P lie?", "the perpendicular bisector of AB", ["the line AB only", "any circle through A", "a line parallel to AB"], "All points equidistant from A and B lie on it.", 1),
     pn("The midpoint M of AB is how far from A (km)?", 5, "10 ÷ 2 = 5 km.", unit="km", pts=1),
     pn("A point P on the bisector is 12 km from M. Find PA in km (use Pythagoras).", 13, "PA = sqrt(5² + 12²) = 13 km.", unit="km", pts=3)]),
  [("The compasses are used to draw:", "arcs and circles", ["straight lines only", "angles in degrees only", "tables"], "Arcs and circles."),
   ("When bisecting a line, the compass radius must be:", "more than half the line", ["less than half the line", "exactly 1 cm", "zero"], "Otherwise the arcs do not cross."),
   ("Each angle of an equilateral triangle constructed with arcs is:", "60°", ["30°", "90°", "45°"], "All sides are equal."),
   ("A perpendicular line makes an angle of:", "90°", ["60°", "45°", "180°"], "Right angle."),
   ("A triangle exists when the two shorter sides together are:", "longer than the third side", ["shorter than the third side", "equal to double", "equal to zero"], "Triangle inequality.")],
  ill=(pb, "Perpendicular bisector of AB made with two pairs of arcs.", "Two circles centred on A and B crossing; a vertical line through the crossing points meets AB at M."),
  notes=["The figure shows complete circles instead of arcs for clarity; construction marks in an exam are arcs only."])

assert 6 * 5 == 30 and 600 * 500 // 2500 == 120 and 120 * 450 == 54000 and (30 + 50) * 20 // 2 == 800 and 2 * (12 + 7) == 38 and 12 * 7 == 84
tz = shapes([POLY([100, 170, 320, 170, 270, 70, 150, 70], fill="lightyellow"), LINE(150, 70, 150, 170, dash=True, color="grey"), T(210, 190, "50 m", 14), T(210, 60, "30 m", 14),
             T(135, 125, "20 m", 13, anchor="end")], 400, 220)
lesson(ch, "perimeter-area", "Perimeter and area",
  ["Find the perimeter of polygons", "Use the area formulas for the rectangle, square, triangle, parallelogram and trapezium", "Convert between units of length and area", "Solve practical problems on land and floors"],
  [("formule", "Area formulas", "The **perimeter** is the distance round a shape. The **area** is the surface it covers, in square units:", "A_{\\text{rectangle}} = l \\times w \\qquad A_{\\text{triangle}} = \\frac{1}{2} b h \\qquad A_{\\text{parallelogram}} = b h"),
   ("formule", "Trapezium", "For a trapezium with parallel sides a and b and height h (the distance between them):", "A = \\frac{1}{2}(a + b) \\times h"),
   ("retenir", "Units", "1 m = 100 cm, so 1 m² = 100 × 100 = 10 000 cm². 1 hectare = 10 000 m². Use the same unit for all measurements before calculating. The **height** is always at right angles to the base, not along a slanted side."),
   ("pieges", "Common mistakes", "- Using the slanted side instead of the perpendicular height.\n- Forgetting the ½ in the area of a triangle or trapezium.\n- Writing area in cm instead of cm², or converting 1 m² to 100 cm² instead of 10 000 cm².")],
  [("Example 1: rectangle and floor tiles", "A classroom floor at Kumba is 6 m by 5 m. Square tiles of side 50 cm cost 450 FCFA each. How many tiles are needed and what do they cost?",
    ["Floor area = 6 × 5 = 30 m² = 300 000 cm²... use 600 cm × 500 cm = 300 000 cm².", "One tile: 50 × 50 = 2 500 cm². Tiles = 300 000 ÷ 2 500 = 120.", "Cost = 120 × 450 = 54 000 FCFA."], "**120 tiles; 54 000 FCFA.**"),
   ("Example 2: trapezium", "A plot of land at Bamenda is a trapezium with parallel sides 30 m and 50 m, 20 m apart. Find its area.",
    ["Use A = ½ (a + b) × h with a = 30, b = 50 and h = 20.", "A = ½ × 80 × 20 = 800.", "The area is 800 m²."], "**800 m²**", tz)],
  [N("Find the perimeter of a rectangle 12 m by 7 m.", 38, "2 × (12 + 7) = 38 m.", unit="m"),
   N("Find the area of a triangle with base 10 cm and height 6 cm.", 30, "½ × 10 × 6 = 30 cm².", unit="cm²"),
   M("1 m² equals:", "10 000 cm²", ["100 cm²", "1 000 cm²", "1 000 000 cm²"], "100 cm × 100 cm.")],
  [N("A parallelogram has base 14 cm and height 9 cm. Find its area.", 126, "14 × 9 = 126 cm².", unit="cm²"),
   O("A square has the perimeter 36 cm. Find its side and its area, showing the steps.", "Side = 36 ÷ 4 = 9 cm. Area = 9 × 9 = 81 cm².", "1 mark for the side, 1 mark for the method of the area, 1 mark for 81 cm².", points=3)],
  P("A rectangular farm in Bali measures 80 m by 50 m. A path 2 m wide runs round the inside edge of the farm.",
    [pn("Find the area of the whole farm (m²).", 4000, "80 × 50 = 4 000.", unit="m²", pts=1),
     pn("The inner part (inside the path) measures 76 m by 46 m. Find its area (m²).", 3496, "76 × 46 = 3 496.", unit="m²", pts=2),
     pn("Find the area of the path (m²).", 504, "4 000 − 3 496 = 504.", unit="m²", pts=2)]),
  [("The area of a rectangle 5 cm by 4 cm:", "20 cm²", ["9 cm²", "18 cm²", "20 cm"], "Length × width; area is in square units."),
   ("The area of a triangle with base 8 and height 5:", "20", ["40", "13", "26"], "½ × 8 × 5."),
   ("The perimeter of a square of side 6 cm:", "24 cm", ["36 cm", "12 cm", "6 cm"], "4 × 6."),
   ("Area of a trapezium with parallel sides 4 and 6, height 5:", "25", ["50", "20", "30"], "½ × 10 × 5."),
   ("1 hectare equals:", "10 000 m²", ["100 m²", "1 000 m²", "100 000 m²"], "100 m × 100 m.")],
  ill=(tz, "A trapezium plot with parallel sides 30 m and 50 m and height 20 m.", "A trapezium with its top side 30 m, bottom 50 m and a dashed height of 20 m."),
  notes=["Circle circumference and area are taught in Form 2 in this scheme; adjust if the official scheme differs."])

# ===================================================================== DATA
ch = p.chapter("data", "Data handling", REF)

dat = [3, 5, 4, 3, 6, 3, 5, 4, 3, 5, 4, 2, 3, 5, 6, 4, 3, 4, 5, 3]
from collections import Counter
cnt = Counter(dat); assert len(dat) == 20 and sum(dat) == 80 and cnt[3] == 7 and cnt[4] == 5 and cnt[5] == 5 and cnt[6] == 2 and cnt[2] == 1
srt = sorted(dat); assert (srt[9] + srt[10]) / 2 == 4 and sum(dat) / 20 == 4 and max(dat) - min(dat) == 4
lesson(ch, "data-handling", "Collecting and presenting data; mean, median, mode",
  ["Collect data with a tally chart and a frequency table", "Draw and read a bar chart", "Calculate the mean, median, mode and range", "Choose the best average for a situation"],
  [("definition", "Data and frequency", "**Data** are pieces of information collected by counting or measuring. A **tally chart** counts with strokes (groups of five: ||||). The **frequency** of a value is the number of times it occurs. A **frequency table** lists each value with its frequency. A **bar chart** shows each value as a bar with equal width and gaps; the height shows the frequency."),
   ("formule", "Averages", "The **mean** is the total divided by the number of values. The **median** is the middle value when the data are in order (for an even number take the middle two and average them). The **mode** is the most frequent value. The **range** is the largest value minus the smallest.", "\\text{mean} = \\frac{\\text{sum of all values}}{\\text{number of values}}"),
   ("methode", "Which average?", "Use the **mean** when values are similar; it uses all values but is pulled by very large or small ones. Use the **median** when there are extreme values (house prices, salaries). Use the **mode** for the most popular choice, such as the most common shoe size sold."),
   ("pieges", "Common mistakes", "- Finding the median without ordering the data first.\n- Calling the frequency 'the mode': the mode is the **value** with the highest frequency.\n- Dividing the sum by the number of **different** values instead of the number of values.")],
  [("Example 1: frequency table", "20 families in a street in Douala were asked how many people live in their home: 3, 5, 4, 3, 6, 3, 5, 4, 3, 5, 4, 2, 3, 5, 6, 4, 3, 4, 5, 3. Find the mode, median and mean.",
    ["Frequencies: 2 → 1, 3 → 7, 4 → 5, 5 → 5, 6 → 2. Mode = 3 (most frequent).", "In order, the 10th and 11th values are both 4, so the median = 4.", "Sum = 80 and 80 ÷ 20 = 4. Range = 6 − 2 = 4."], "**Mode 3, median 4, mean 4, range 4.**", bars([("2", 1), ("3", 7), ("4", 5), ("5", 5), ("6", 2)], unit="families", h=260)),
   ("Example 2: choosing the average", "The monthly earnings (FCFA) of five workers are 40 000, 45 000, 50 000, 55 000 and 360 000. Find the mean and the median. Which describes a typical worker better?",
    ["Mean = (40 000 + 45 000 + 50 000 + 55 000 + 360 000) ÷ 5 = 550 000 ÷ 5 = 110 000.", "Median = the middle value = 50 000.", "One very high value pulls the mean up, so the median is better here."], "**Mean 110 000 FCFA, median 50 000 FCFA; the median is more typical.**")],
  [N("Find the mean of 4, 8, 6, 10, 7.", 7, "35 ÷ 5 = 7."),
   N("Find the median of 3, 9, 4, 7, 5.", 5, "Order: 3, 4, 5, 7, 9; the middle is 5."),
   M("The mode of 2, 3, 3, 4, 3, 5, 5 is:", "3", ["5", "4", "3.57"], "3 appears three times.")],
  [N("The marks of 6 pupils are 12, 15, 11, 15, 14, 13. Find the mean mark.", 13.333, "80 ÷ 6 = 13.33.", tol=0.01),
   O("Five houses at a quarter in Limbe sell for 8, 9, 9, 10 and 80 million FCFA. Which average is better to describe the usual price and why?", "The median (9 million), because the 80 million is unusually high and pulls the mean up to 23.2 million.", "1 mark for the median, 1 mark for the reason, 1 mark for correct values.", points=3)],
  P("A shop in Bafoussam recorded the number of bags of cement sold each day for 7 days: 8, 12, 9, 12, 15, 10, 12.",
    [pn("Find the mode.", 12, "12 appears three times.", pts=1),
     pn("Find the median.", 12, "Ordered: 8, 9, 10, 12, 12, 12, 15; the middle (4th) is 12.", pts=1),
     pn("Find the mean (2 d.p.).", 11.14, "78 ÷ 7 = 11.14.", tol=0.01, pts=2),
     pn("Find the range.", 7, "15 − 8 = 7.", pts=1)]),
  [("The range of 3, 8, 5, 12, 6 is:", "9", ["12", "3", "34"], "12 − 3."),
   ("The mean is found by:", "adding all values and dividing by how many there are", ["picking the middle", "choosing the most frequent", "subtracting"], "Definition."),
   ("The median of 2, 4, 6, 8 is:", "5", ["4", "6", "20"], "Average of 4 and 6."),
   ("A tally mark group of five looks like:", "four strokes crossed by a fifth", ["five separate strokes", "one long stroke", "a circle"], "Groups of five."),
   ("In a bar chart, the bars should have:", "equal width and gaps", ["different widths", "no labels", "touching bars of unequal width"], "Standard bar chart.")],
  ill=(bars([("2", 1), ("3", 7), ("4", 5), ("5", 5), ("6", 2)], unit="families", h=260), "Bar chart of the number of people per household in 20 families.", "A bar chart: 1 family with 2 people, 7 with 3, 5 with 4, 5 with 5 and 2 with 6."),
  notes=["Pie charts and grouped data are left for Form 2; the tallest bar (7) must have headroom — check the figure."])

p.write()
