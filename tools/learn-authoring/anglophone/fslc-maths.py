import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _b import *

reset_extension("fslc-maths", 4, {"fslc-maths-mock"})
p = Pack("fslc-maths", None, None, None, None, extend=True)
REF = "MINEDUB National Syllabus, Mathematics, Level III (Classes 5-6) — to be checked against the official text"
MOCKL = None   # a lesson used to hold the second mock paper's exercises

# =========================================================== chapter 5: place value, factors, patterns
ch = p.chapter("place-factors", "Large numbers, factors and patterns", REF)

# ---- place value
def val(n, pos): return int(str(n)[-pos]) * 10 ** (pos - 1)
assert val(3472150, 5) == 70000
n = 4236508
assert val(n, 6) == 200000 and val(n, 5) == 30000
def rnd(x, b): return (x + b // 2) // b * b
assert rnd(365482, 1000) == 365000 and rnd(365482, 10000) == 370000 and rnd(8649731, 100000) == 8600000
fig_pv = shapes([RECT(25 + 50 * i, 40, 50, 50, fill="lightblue" if i < 3 else "lightyellow") for i in range(7)] +
                [T(50 + 50 * i, 72, h, 15, bold=True) for i, h in enumerate(["M", "HTh", "TTh", "Th", "H", "T", "O"])] +
                [RECT(25 + 50 * i, 90, 50, 50) for i in range(7)] +
                [T(50 + 50 * i, 125, d, 22, bold=True) for i, d in enumerate("4236508")] +
                [T(200, 175, "M = millions   HTh = hundred thousands   TTh = ten thousands", 12),
                 T(200, 195, "Th = thousands   H = hundreds   T = tens   O = ones", 12)], 400, 220)
L = build(ch, "place-value", "Place value and large numbers", 25,
    ["Read, write and compare numbers up to hundreds of millions.", "Give the value of a digit in a large number.", "Round a number to the nearest 10, 100, 1,000 or 100,000."],
    [("key", "definition", "Place value", "The **value** of a digit depends on its **place** in the number. In 4,236,508 the digit 2 is in the hundred-thousands place, so its value is 200,000. The digit 3 is in the ten-thousands place, so its value is 30,000."),
     ("fig", fig_pv, "The number 4,236,508 in a place-value table.", "A table with seven boxes. The headings are M, HTh, TTh, Th, H, T and O. Under them are the digits 4, 2, 3, 6, 5, 0 and 8."),
     ("key", "retenir", "Reading big numbers", "Group the digits in threes from the right: 4,236,508. Read each group and add its name. We say: four **million**, two hundred and thirty-six **thousand**, five hundred and eight. A zero in a place means that place is empty, but we must write the zero."),
     ("key", "methode", "Rounding", "To round to the nearest 1,000: look at the digit just on the right (the hundreds digit). If it is **5 or more**, add one to the thousands digit. If it is **less than 5**, keep it. Then put zeros after. Example: 365,482 gives 365,000 because the hundreds digit is 4."),
     ("key", "pieges", "Common mistakes", "- Writing 'four million two hundred thirty-six' for 4,236,508: you forget the thousands.\n- Confusing the **digit** (3) with its **value** (30,000).\n- Dropping a zero: five million and sixty-three is 5,000,063, not 563."),
     ("ex", "Example 1", "Write 4,236,508 in words. What is the value of the digit 3?", ["Group the digits: 4 | 236 | 508.", "Read: four million, two hundred and thirty-six thousand, five hundred and eight.", "The 3 is in the ten-thousands place: its value is 3 × 10,000 = 30,000."], "Four million, two hundred and thirty-six thousand, five hundred and eight; the value of 3 is 30,000."),
     ("ex", "Example 2", "Round 365,482 to the nearest 1,000 and to the nearest 10,000.", ["Nearest 1,000: the hundreds digit is 4 (less than 5), so we keep 365 thousand: 365,000.", "Nearest 10,000: look at the thousands digit, 5. It is 5 or more, so the 6 ten-thousands becomes 7: 370,000."], "365,000 and 370,000.")],
    [N("application", "What is the value of the digit 7 in 3,472,150?", val(3472150, 5), "The 7 is in the ten-thousands place: 7 × 10,000 = 70,000.", unit=None),
     M("application", "Which number is the largest?", "1,905,000", ["1,090,500", "1,009,500", "1,095,000"], "Compare the millions first (all 1), then the hundred-thousands digit: 9 is the biggest in 1,905,000."),
     N("application", "Round 8,649,731 to the nearest 100,000.", rnd(8649731, 100000), "The ten-thousands digit is 4 (less than 5), so we keep 8,600,000.", unit=None),
     TF("approfondissement", "In 5,060,203 the digit 6 has the value 60,000.", True, "5,060,203: the 5 is millions, the 0 hundred-thousands, the 6 ten-thousands. So 6 is worth 60,000."),
     N("approfondissement", "Write as a number: 2 millions, 5 ten-thousands, 7 hundreds and 3 ones.", 2 * 10**6 + 5 * 10**4 + 700 + 3, "2,000,000 + 50,000 + 700 + 3 = 2,050,703. The empty places (hundred-thousands, thousands, tens) are zeros."),
     P("A cocoa cooperative sold 3,479,260 kg of cocoa in one season.",
       [PN("What is the value of the digit 4?", 400000, "The 4 is in the hundred-thousands place: 400,000.", unit="kg"),
        PN("Round the mass to the nearest 100,000 kg.", rnd(3479260, 100000), "The ten-thousands digit is 7, so we round up: 3,500,000.", unit="kg"),
        PN("How many more kilograms are needed to reach 4,000,000 kg?", 4000000 - 3479260, "4,000,000 − 3,479,260 = 520,740.", unit="kg", points=2)])],
    [("What is the value of 5 in 6,250,310?", "50,000", ["5,000", "500,000", "5"], "The 5 is in the ten-thousands place."),
     ("How do we write 'two million, forty thousand and nine'?", "2,040,009", ["2,400,009", "2,040,090", "200,409"], "2,000,000 + 40,000 + 9. Empty places are zeros."),
     ("Round 74,860 to the nearest 1,000.", "75,000", ["74,000", "74,900", "70,000"], "The hundreds digit is 8, so round up to 75,000."),
     ("Which is smaller: 3,099,999 or 3,100,000?", "3,099,999", ["3,100,000", "They are equal", "Cannot say"], "Both have 3 millions; 099,999 is less than 100,000."),
     ("In 9,008,004 how many hundred-thousands are there?", "0", ["8", "9", "4"], "The hundred-thousands place holds 0.")],
    ["Names of places above millions (billions) are left out; the Class 6 syllabus limit (millions or hundreds of millions) is to be checked.",
     "Use of commas vs spaces to group digits: commas used here, as in Cameroonian anglophone textbooks (to be checked)."],
    prereq=["fslc-maths-whole-numbers"])

# ---- factors, multiples, HCF, LCM
assert math.gcd(24, 36) == 12 and math.lcm(12, 18) == 36 and math.gcd(18, 45) == 9 and math.lcm(4, 6, 10) == 60
assert math.lcm(15, 20) == 60 and math.gcd(48, 72) == 24
def factors(n): return [i for i in range(1, n + 1) if n % i == 0]
assert factors(24) == [1, 2, 3, 4, 6, 8, 12, 24]
primes_to_30 = [i for i in range(2, 31) if len(factors(i)) == 2]
assert primes_to_30 == [2, 3, 5, 7, 11, 13, 17, 19, 23, 29]
fig_tree = shapes([LINE(200, 45, 130, 95), LINE(200, 45, 270, 95), LINE(130, 125, 90, 170), LINE(130, 125, 170, 170),
                   LINE(270, 125, 230, 170), LINE(270, 125, 310, 170),
                   CIRCLE(200, 30, 18, fill="lightyellow"), T(200, 36, "60", 16, bold=True),
                   CIRCLE(130, 110, 18, fill="lightblue"), T(130, 116, "6", 16, bold=True),
                   CIRCLE(270, 110, 18, fill="lightblue"), T(270, 116, "10", 16, bold=True),
                   CIRCLE(90, 188, 18, fill="lightgreen"), T(90, 194, "2", 16, bold=True),
                   CIRCLE(170, 188, 18, fill="lightgreen"), T(170, 194, "3", 16, bold=True),
                   CIRCLE(230, 188, 18, fill="lightgreen"), T(230, 194, "2", 16, bold=True),
                   CIRCLE(310, 188, 18, fill="lightgreen"), T(310, 194, "5", 16, bold=True)], 400, 225)
build(ch, "factors-multiples", "Factors, multiples, HCF and LCM", 30,
    ["Find the factors and multiples of a number.", "Recognise prime numbers and write a number as a product of primes.", "Find the HCF and the LCM of two or three numbers."],
    [("key", "definition", "Factors and multiples", "A **factor** of a number divides it exactly. The factors of 24 are 1, 2, 3, 4, 6, 8, 12 and 24. A **multiple** of a number is in its times table: the multiples of 6 are 6, 12, 18, 24, 30... A **prime number** has exactly two factors, 1 and itself: 2, 3, 5, 7, 11, 13... The number 1 is not prime."),
     ("fig", fig_tree, "A factor tree for 60: 60 = 2 × 2 × 3 × 5.", "A tree. 60 splits into 6 and 10. Then 6 splits into 2 and 3, and 10 splits into 2 and 5. The bottom numbers 2, 3, 2 and 5 are prime."),
     ("key", "methode", "HCF and LCM", "The **HCF** (highest common factor) is the biggest number that divides both numbers. The **LCM** (lowest common multiple) is the smallest number that both numbers divide. Method: list the factors (or multiples) of each number, then pick the biggest common factor (or the smallest common multiple)."),
     ("key", "pieges", "Common mistakes", "- Mixing up HCF and LCM: the HCF is **small** (never bigger than the numbers), the LCM is **big** (never smaller than the numbers).\n- Saying that 1 is a prime number, or that 9 is prime (9 = 3 × 3).\n- Forgetting that 2 is the only **even** prime number."),
     ("ex", "Example 1", "Find the HCF of 24 and 36.", ["Factors of 24: 1, 2, 3, 4, 6, 8, 12, 24.", "Factors of 36: 1, 2, 3, 4, 6, 9, 12, 18, 36.", "The common factors are 1, 2, 3, 4, 6 and 12. The biggest is 12."], "HCF = 12."),
     ("ex", "Example 2", "Find the LCM of 12 and 18.", ["Multiples of 12: 12, 24, 36, 48...", "Multiples of 18: 18, 36, 54...", "The first number in both lists is 36."], "LCM = 36.")],
    [M("application", "Which of these numbers is prime?", "29", ["21", "27", "39"], "21 = 3 × 7, 27 = 3 × 9 and 39 = 3 × 13. Only 29 has just two factors."),
     N("application", "Find the HCF of 18 and 45.", math.gcd(18, 45), "Factors of 18: 1, 2, 3, 6, 9, 18. Factors of 45: 1, 3, 5, 9, 15, 45. The biggest common one is 9."),
     N("application", "Find the LCM of 15 and 20.", math.lcm(15, 20), "Multiples of 20: 20, 40, 60. 60 is also a multiple of 15 (15 × 4)."),
     N("approfondissement", "How many factors does 24 have?", len(factors(24)), "1, 2, 3, 4, 6, 8, 12, 24: that is 8 factors."),
     N("approfondissement", "Find the LCM of 4, 6 and 10.", math.lcm(4, 6, 10), "Multiples of 10: 10, 20, 30, 40, 50, 60. The first one that 4 and 6 also divide is 60."),
     P("A baker in Bafoussam packs 48 buns and 72 puff-puffs into identical bags. Each bag has the same number of buns and the same number of puff-puffs, with nothing left over. He wants as many bags as possible.",
       [PN("How many bags can he make?", math.gcd(48, 72), "This is the HCF of 48 and 72. 48 = 2 × 2 × 2 × 2 × 3 and 72 = 2 × 2 × 2 × 3 × 3, so HCF = 2 × 2 × 2 × 3 = 24.", points=2),
        PN("How many buns are in each bag?", 48 // math.gcd(48, 72), "48 ÷ 24 = 2 buns."),
        PN("How many puff-puffs are in each bag?", 72 // math.gcd(48, 72), "72 ÷ 24 = 3 puff-puffs.")])],
    [("Which number is a factor of 30?", "15", ["4", "9", "20"], "30 ÷ 15 = 2 exactly. The others do not divide 30."),
     ("What is the HCF of 12 and 30?", "6", ["3", "12", "60"], "Common factors are 1, 2, 3, 6. The highest is 6."),
     ("What is the LCM of 6 and 8?", "24", ["2", "14", "48"], "Multiples of 8: 8, 16, 24. 24 is a multiple of 6 too."),
     ("Which list has only prime numbers?", "2, 11, 17", ["2, 9, 13", "1, 3, 5", "3, 15, 19"], "9 and 15 are not prime and 1 is not prime."),
     ("Two bells ring every 4 minutes and every 6 minutes. They ring together now. After how many minutes will they ring together again?", "12", ["2", "10", "24"], "LCM of 4 and 6 is 12.")],
    ["Prime factorisation and HCF/LCM by prime factors are shown briefly; check that the syllabus expects the listing method.",
     "Divisibility tests (by 3, 9...) are not covered in this lesson."],
    prereq=["fslc-maths-place-value"])

# ---- number patterns
def nth(a, d, n): return a + (n - 1) * d
assert [nth(3, 4, n) for n in range(1, 5)] == [3, 7, 11, 15] and nth(3, 4, 20) == 79
assert [n * n for n in range(1, 6)] == [1, 4, 9, 16, 25]
assert [n * (n + 1) // 2 for n in range(1, 6)] == [1, 3, 6, 10, 15]
fig_sq = bars([("1", 1), ("4", 4), ("9", 9), ("16", 16), ("25", 25)], None, 400, 230)
build(ch, "number-patterns", "Number patterns", 25,
    ["Find the rule of a number pattern and continue it.", "Recognise square, triangular, odd and even numbers.", "Use a rule to find any term of a simple pattern."],
    [("key", "definition", "Number patterns", "A **sequence** is a list of numbers that follows a rule. Each number is a **term**. In 3, 7, 11, 15... we **add 4** each time. In 2, 6, 18, 54... we **multiply by 3** each time. To find the rule, look at the difference or the ratio between neighbouring terms."),
     ("fig", fig_sq, "The first five square numbers: 1, 4, 9, 16, 25.", "A bar chart with five bars of growing height. The bars are 1, 4, 9, 16 and 25. They are the squares of 1, 2, 3, 4 and 5."),
     ("key", "retenir", "Special sequences", "- **Square numbers**: 1, 4, 9, 16, 25... (1×1, 2×2, 3×3...).\n- **Triangular numbers**: 1, 3, 6, 10, 15... (add 2, then 3, then 4...).\n- **Odd numbers**: 1, 3, 5, 7... **Even numbers**: 2, 4, 6, 8...\n- **Powers of 2**: 1, 2, 4, 8, 16, 32..."),
     ("key", "methode", "Rule for any term", "For a pattern that adds the same number d each time and starts at a, the term number n is: a + (n − 1) × d. Example: 3, 7, 11, 15... has a = 3 and d = 4. The 20th term is 3 + 19 × 4 = 79."),
     ("key", "pieges", "Common mistakes", "- Checking only the first two terms. Always check the rule on **all** the terms you know.\n- In the 'n-th term' formula, using n × d instead of (n − 1) × d: the first term has no jump yet."),
     ("ex", "Example 1", "Find the next two terms: 5, 9, 13, 17, ...", ["Differences: 9 − 5 = 4, 13 − 9 = 4, 17 − 13 = 4. The rule is add 4.", "17 + 4 = 21 and 21 + 4 = 25."], "21 and 25."),
     ("ex", "Example 2", "Pattern: 4, 7, 10, 13... What is the 15th term?", ["The first term is 4 and we add 3 each time.", "15th term = 4 + (15 − 1) × 3 = 4 + 42 = 46."], "46.")],
    [N("application", "Find the next term: 2, 6, 18, 54, ...", 54 * 3, "Each term is multiplied by 3: 54 × 3 = 162."),
     M("application", "Which of these is a square number?", "49", ["48", "50", "45"], "49 = 7 × 7."),
     N("application", "Find the missing term: 100, 90, 80, ?, 60.", 70, "We subtract 10 each time: 80 − 10 = 70."),
     N("approfondissement", "The pattern 6, 11, 16, 21... adds 5 each time. What is the 10th term?", nth(6, 5, 10), "6 + 9 × 5 = 6 + 45 = 51."),
     TF("approfondissement", "The sum of the first three odd numbers (1 + 3 + 5) is a square number.", True, "1 + 3 + 5 = 9 = 3 × 3."),
     P("Mbarga builds triangles with stones. Triangle 1 uses 1 stone, triangle 2 uses 3, triangle 3 uses 6, triangle 4 uses 10.",
       [PN("How many stones are in triangle 5?", 15, "We add 2, 3, 4, then 5: 10 + 5 = 15.", unit="stones"),
        PN("How many stones are in triangle 7?", 28, "Triangle 6 = 15 + 6 = 21; triangle 7 = 21 + 7 = 28.", unit="stones"),
        PM("What is the rule?", "Add one more stone than last time", ["Add 2 stones each time", "Double the last number", "Add 4 stones each time"], "The jumps are 2, 3, 4, 5: they grow by one each time.", points=2)])],
    [("What is the next term of 1, 4, 9, 16, ...?", "25", ["20", "24", "32"], "These are square numbers: 5 × 5 = 25."),
     ("What is the rule of 80, 40, 20, 10?", "Divide by 2", ["Subtract 40", "Subtract 10", "Divide by 4"], "Each term is half of the one before."),
     ("The sequence starts 7, 12, 17... What is the 4th term?", "22", ["20", "27", "24"], "Add 5 each time: 17 + 5 = 22."),
     ("Which sequence is made of even numbers only?", "6, 12, 18, 24", ["5, 10, 15, 20", "2, 4, 7, 8", "1, 2, 4, 8, 9"], "Every term of 6, 12, 18, 24 is divisible by 2."),
     ("Find the 10th term of the sequence 3, 6, 9, 12...", "30", ["27", "33", "13"], "It is the 3 times table: 3 × 10 = 30.")],
    ["Triangular numbers named in the lesson; check that they are in the syllabus (otherwise they are enrichment)."],
    prereq=["fslc-maths-factors-multiples"])

def f(n): return "{:,}".format(n)

# =========================================================== chapter 6: ratio, money, business, averages
ch = p.chapter("ratio-money", "Ratio, money, business and averages", REF)

# ---- ratio and proportion
tot = 56000; ada = tot // 8 * 3; bih = tot // 8 * 5
assert ada == 21000 and bih == 35000 and ada + bih == tot
assert 4500 // 5 * 8 == 7200
assert 90 * 2 // 3 == 60 and 1800 // 4 * 7 == 3150 and 12 * 2 // 3 == 8
assert 15 + 9 == 24 and 6 * 8 // 4 == 12
fig_ratio = shapes([RECT(40 + 40 * i, 60, 40, 50, fill="lightblue" if i < 3 else "lightyellow") for i in range(8)] +
                   [T(120, 140, "3 parts", 16, bold=True), T(260, 140, "5 parts", 16, bold=True),
                    T(200, 30, "Ratio 3 : 5  (8 parts in all)", 16, bold=True),
                    T(200, 185, "56,000 FCFA ÷ 8 = 7,000 FCFA for one part", 14)], 400, 215)
build(ch, "ratio-proportion", "Ratio and proportion", 30,
    ["Write and simplify a ratio.", "Share a quantity in a given ratio.", "Solve direct proportion problems with the unitary method."],
    [("key", "definition", "Ratio", "A **ratio** compares two quantities of the same kind. If a class has 12 girls and 18 boys, the ratio of girls to boys is 12 : 18. We simplify it by dividing both numbers by their HCF (6): 12 : 18 = 2 : 3. The order matters: boys to girls is 3 : 2."),
     ("fig", fig_ratio, "Sharing 56,000 FCFA in the ratio 3 : 5.", "A bar cut into 8 equal boxes. The first 3 boxes are blue and the other 5 are yellow. Text says one part is 7,000 FCFA."),
     ("key", "methode", "Sharing in a ratio", "1. Add the parts: 3 + 5 = 8 parts.\n2. Find one part: total ÷ number of parts.\n3. Multiply by each ratio number.\n4. Check: the shares must add up to the total."),
     ("key", "methode", "Direct proportion (unitary method)", "When one quantity doubles, the other doubles too. First find the value of **one** unit, then multiply. If 5 kg of rice cost 4,500 FCFA, then 1 kg costs 4,500 ÷ 5 = 900 FCFA, and 8 kg cost 8 × 900 = 7,200 FCFA."),
     ("key", "pieges", "Common mistakes", "- Sharing the total by the **ratio numbers** (3 and 5) instead of by the **number of parts** (8).\n- Giving the answers in the wrong order.\n- Using direct proportion when it is **inverse**: more workers means **fewer** days."),
     ("ex", "Example 1", "Share 56,000 FCFA between Ada and Bih in the ratio 3 : 5.", ["Number of parts: 3 + 5 = 8.", "One part: 56,000 ÷ 8 = 7,000 FCFA.", "Ada: 3 × 7,000 = 21,000 FCFA. Bih: 5 × 7,000 = 35,000 FCFA. Check: 21,000 + 35,000 = 56,000."], "Ada gets 21,000 FCFA and Bih gets 35,000 FCFA."),
     ("ex", "Example 2", "5 kg of rice cost 4,500 FCFA. How much do 8 kg cost?", ["One kilogram: 4,500 ÷ 5 = 900 FCFA.", "Eight kilograms: 8 × 900 = 7,200 FCFA."], "7,200 FCFA.")],
    [M("application", "Simplify the ratio 24 : 36.", "2 : 3", ["3 : 2", "4 : 6", "12 : 18"], "The HCF of 24 and 36 is 12. 24 ÷ 12 = 2 and 36 ÷ 12 = 3."),
     N("application", "Share 90 mangoes between two children in the ratio 2 : 1. How many mangoes does the bigger share have?", 60, "3 parts: one part is 30. The bigger share is 2 × 30 = 60 mangoes."),
     N("application", "4 exercise books cost 1,800 FCFA. What is the cost of 7 books?", 3150, "One book: 1,800 ÷ 4 = 450 FCFA. Seven books: 7 × 450 = 3,150 FCFA."),
     N("approfondissement", "To make a green paint, blue and yellow are mixed in the ratio 2 : 3. How many litres of blue are needed with 12 litres of yellow?", 8, "12 litres of yellow is 4 litres per part (12 ÷ 3). Blue = 2 × 4 = 8 litres."),
     TF("approfondissement", "The ratios 4 : 6 and 6 : 9 are equal.", True, "Both simplify to 2 : 3."),
     P("A farmer has 24 hectares. He plants maize and cassava in the ratio 5 : 3.",
       [PN("How many hectares of maize?", 15, "8 parts: one part = 24 ÷ 8 = 3 ha. Maize: 5 × 3 = 15 ha.", unit="ha"),
        PN("How many hectares of cassava?", 9, "3 × 3 = 9 ha.", unit="ha"),
        PN("6 workers weed the whole farm in 8 days. How many days will 4 workers need, working at the same speed?", 12, "Total work: 6 × 8 = 48 worker-days. 48 ÷ 4 = 12 days. (Fewer workers means more days.)", unit="days", points=2)])],
    [("Simplify 15 : 25.", "3 : 5", ["5 : 3", "15 : 5", "1 : 2"], "Divide both by 5."),
     ("Share 60 sweets in the ratio 1 : 2. What is the smaller share?", "20", ["30", "40", "15"], "3 parts, 60 ÷ 3 = 20 for one part."),
     ("3 pens cost 450 FCFA. How much do 5 pens cost?", "750 FCFA", ["675 FCFA", "900 FCFA", "150 FCFA"], "One pen costs 150 FCFA; five cost 750 FCFA."),
     ("In a class, boys : girls = 4 : 5. There are 20 boys. How many girls?", "25", ["16", "9", "30"], "One part = 20 ÷ 4 = 5. Girls: 5 × 5 = 25."),
     ("2 tap-fillers fill a tank in 6 hours. How long will 3 equal fillers take?", "4 hours", ["9 hours", "3 hours", "12 hours"], "Work = 2 × 6 = 12. 12 ÷ 3 = 4 hours.")],
    ["The existing word-problems lesson also uses the unitary method; overlap is deliberate (revision).", "Inverse proportion is introduced only informally."],
    prereq=["fslc-maths-factors-multiples", "fslc-maths-fractions"])

# ---- money
bill = 3 * 600 + 2 * 450 + 1 * 1250
assert bill == 3950 and 5000 - bill == 1050
assert 10000 - 7350 == 2650 and 3 * 250 + 2 * 100 == 950 and 12000 // 500 == 24
assert 1200 / 3 == 400 and 1900 / 5 == 380 and 18000 // 1500 == 12
assert 6 * 150 == 900 and 1400 / 10 == 140
fig_money = shapes([RECT(20, 40, 110, 60, fill="lightgreen", radius=6), T(75, 78, "1,000", 18, bold=True),
                    RECT(145, 40, 110, 60, fill="lightyellow", radius=6), T(200, 78, "2,000", 18, bold=True),
                    RECT(270, 40, 110, 60, fill="lightblue", radius=6), T(325, 78, "5,000", 18, bold=True),
                    T(200, 130, "1 note of 5,000 FCFA = 5 notes of 1,000 FCFA", 14),
                    T(200, 160, "Total = unit price × quantity", 14, bold=True)], 400, 190)
build(ch, "money-fcfa", "Money and FCFA: bills, change and budgets", 25,
    ["Add, subtract and multiply amounts of money in FCFA.", "Work out the change, a total bill and the best buy.", "Plan a simple budget and savings."],
    [("key", "definition", "Money in Cameroon", "In Cameroon we use the **franc CFA** (FCFA). We use notes (for example 1,000, 2,000, 5,000 and 10,000 FCFA) and coins. **Total cost = unit price × quantity**. **Change = money given − total bill**. A good habit: estimate first, then calculate exactly."),
     ("fig", fig_money, "Three FCFA notes and the rule for a total.", "Three rectangles shaped like notes, marked 1,000, 2,000 and 5,000. Under them are two lines of text about notes and the total."),
     ("key", "methode", "Best buy", "To compare two offers, find the **price of one** item in each offer. The offer with the lower price per item is the best buy. Example: 3 pens for 1,200 FCFA is 1,200 ÷ 3 = 400 FCFA each; 5 pens for 1,900 FCFA is 380 FCFA each: the second offer is better."),
     ("key", "pieges", "Common mistakes", "- Forgetting a line of the shopping list when you add.\n- Counting the change from the wrong note.\n- Comparing the **total** prices of offers with different quantities: always compare the price **of one**."),
     ("ex", "Example 1", "At Mokolo market, Mama Joy buys 3 kg of tomatoes at 600 FCFA per kg, 2 loaves of bread at 450 FCFA each and one bottle of oil at 1,250 FCFA. She pays with a 5,000 FCFA note. What is her change?", ["Tomatoes: 3 × 600 = 1,800 FCFA.", "Bread: 2 × 450 = 900 FCFA. Oil: 1,250 FCFA.", "Total bill: 1,800 + 900 + 1,250 = 3,950 FCFA.", "Change: 5,000 − 3,950 = 1,050 FCFA."], "Her change is 1,050 FCFA."),
     ("ex", "Example 2", "A pupil saves 1,500 FCFA each week. In how many weeks will she have 18,000 FCFA?", ["Number of weeks = 18,000 ÷ 1,500.", "1,500 × 10 = 15,000 and 1,500 × 2 = 3,000, so 12 weeks give 18,000."], "12 weeks.")],
    [N("application", "A bill is 7,350 FCFA. You pay with 10,000 FCFA. How much is your change (in FCFA)?", 2650, "10,000 − 7,350 = 2,650 FCFA."),
     M("application", "3 notebooks cost 250 FCFA each and 2 pens cost 100 FCFA each. What is the total?", "950 FCFA", ["850 FCFA", "1,050 FCFA", "350 FCFA"], "3 × 250 = 750; 2 × 100 = 200; 750 + 200 = 950 FCFA."),
     N("application", "How many 500 FCFA notes make 12,000 FCFA?", 24, "12,000 ÷ 500 = 24."),
     M("approfondissement", "Which is the best buy: 3 bars of soap for 1,200 FCFA, or 5 bars for 1,900 FCFA?", "5 bars for 1,900 FCFA", ["3 bars for 1,200 FCFA", "They cost the same per bar", "Not possible to say"], "1,200 ÷ 3 = 400 per bar; 1,900 ÷ 5 = 380 per bar. 380 is cheaper."),
     N("approfondissement", "Ngu earns 45,000 FCFA a month. He spends 3/5 of it. How much does he keep (in FCFA)?", 45000 * 2 // 5, "He spends 45,000 ÷ 5 × 3 = 27,000. He keeps 45,000 − 27,000 = 18,000 FCFA."),
     P("Before the school year, Mrs Fon buys 4 exercise books at 350 FCFA each, a school bag at 6,500 FCFA and 3 pencils at 100 FCFA each.",
       [PN("What is the total cost of the exercise books?", 4 * 350, "4 × 350 = 1,400 FCFA.", unit="FCFA"),
        PN("What is the total bill?", 4 * 350 + 6500 + 300, "1,400 + 6,500 + 300 = 8,200 FCFA.", unit="FCFA"),
        PN("She pays with two 5,000 FCFA notes. What is her change?", 10000 - 8200, "10,000 − 8,200 = 1,800 FCFA.", unit="FCFA", points=2)])],
    [("A pupil buys 4 mangoes at 150 FCFA each. How much does she pay?", "600 FCFA", ["550 FCFA", "154 FCFA", "300 FCFA"], "4 × 150 = 600 FCFA."),
     ("What is the change from 2,000 FCFA after buying goods for 1,450 FCFA?", "550 FCFA", ["450 FCFA", "650 FCFA", "3,450 FCFA"], "2,000 − 1,450 = 550."),
     ("How many 1,000 FCFA notes are there in 15,000 FCFA?", "15", ["5", "150", "10"], "15,000 ÷ 1,000 = 15."),
     ("6 pencils cost 900 FCFA. What does one pencil cost?", "150 FCFA", ["540 FCFA", "6,000 FCFA", "300 FCFA"], "900 ÷ 6 = 150."),
     ("Total cost = ...", "unit price × quantity", ["unit price + quantity", "unit price ÷ quantity", "quantity − unit price"], "We multiply the price of one by the number of items.")],
    ["Banknote denominations quoted (1,000 / 2,000 / 5,000 / 10,000 FCFA) and 'coins' are given in general terms; check that they match the notes and coins in circulation."],
    prereq=["fslc-maths-whole-numbers"])

# ---- profit, loss, discount, simple interest
assert 10000 - 8000 == 2000 and 2000 * 100 // 8000 == 25 and (12000 - 9000) * 100 // 12000 == 25
assert 20000 * 85 // 100 == 17000 and 50000 * 6 * 3 // 100 == 9000 and 5400 - 4500 == 900
assert (2000 - 1500) * 100 // 2000 == 25 and 20000 * 5 * 2 // 100 == 2000 and 12000 * 90 // 100 == 10800
assert 6400 * 100 // (40000 * 2) == 8
fig_pl = shapes([RECT(30, 40, 160, 60, fill="lightblue", radius=6), T(110, 66, "Cost price", 16, bold=True), T(110, 88, "CP", 14),
                 RECT(210, 40, 160, 60, fill="lightyellow", radius=6), T(290, 66, "Selling price", 16, bold=True), T(290, 88, "SP", 14),
                 T(200, 140, "SP > CP: profit = SP − CP", 15, color="green", bold=True),
                 T(200, 170, "SP < CP: loss = CP − SP", 15, color="red", bold=True),
                 T(200, 200, "Percentage = profit (or loss) ÷ CP × 100", 14)], 400, 225)
build(ch, "profit-loss-interest", "Profit, loss, discount and simple interest", 30,
    ["Calculate profit, loss and percentage profit or loss.", "Calculate a discount and a sale price.", "Use the simple interest formula."],
    [("key", "definition", "Profit and loss", "The **cost price** (CP) is what a trader pays to buy goods. The **selling price** (SP) is what he sells them for. If SP is more than CP: **profit = SP − CP**. If SP is less than CP: **loss = CP − SP**. The percentage is always taken on the **cost price**: percentage profit = (profit ÷ CP) × 100."),
     ("fig", fig_pl, "Cost price, selling price, profit and loss.", "Two boxes labelled cost price and selling price. Below, three lines say how to find profit, loss and the percentage."),
     ("key", "formule", "Discount and simple interest", "A **discount** is a reduction of the marked price: discount = marked price × rate ÷ 100; sale price = marked price − discount. **Simple interest**: I = (P × R × T) ÷ 100, where P is the money saved or borrowed, R the yearly rate in % and T the time in years. The **amount** is P + I."),
     ("key", "pieges", "Common mistakes", "- Dividing the profit by the **selling** price instead of the **cost** price.\n- Using months for T: the rate is **per year**, so change months to years first (6 months = 0.5 year).\n- Forgetting to add the interest to the money saved when the question asks for the **amount**."),
     ("ex", "Example 1", "A trader buys goods for 8,000 FCFA and sells them for 10,000 FCFA. Find his percentage profit.", ["Profit = 10,000 − 8,000 = 2,000 FCFA.", "Percentage profit = 2,000 ÷ 8,000 × 100 = 25%."], "Profit 2,000 FCFA, that is 25%."),
     ("ex", "Example 2", "Find the simple interest on 50,000 FCFA at 6% a year for 3 years, and the amount.", ["I = 50,000 × 6 × 3 ÷ 100 = 900,000 ÷ 100 = 9,000 FCFA.", "Amount = 50,000 + 9,000 = 59,000 FCFA."], "Interest 9,000 FCFA; amount 59,000 FCFA.")],
    [N("application", "A seller buys a radio for 4,500 FCFA and sells it for 5,400 FCFA. What is his profit (in FCFA)?", 900, "5,400 − 4,500 = 900 FCFA."),
     N("application", "An item costing 2,000 FCFA is sold for 1,500 FCFA. What is the percentage loss?", 25, "Loss = 500 FCFA; 500 ÷ 2,000 × 100 = 25%.", unit="%"),
     N("application", "Find the simple interest on 20,000 FCFA at 5% a year for 2 years (in FCFA).", 2000, "20,000 × 5 × 2 ÷ 100 = 2,000 FCFA."),
     N("approfondissement", "A shirt is marked 12,000 FCFA. In a sale there is a 10% discount. What is the sale price (in FCFA)?", 10800, "Discount = 1,200 FCFA. Sale price = 12,000 − 1,200 = 10,800 FCFA."),
     N("approfondissement", "A man saves 40,000 FCFA. After 2 years the simple interest is 6,400 FCFA. What is the yearly rate?", 8, "R = I × 100 ÷ (P × T) = 640,000 ÷ 80,000 = 8%.", unit="%"),
     P("A trader in Kumba buys 50 bags of groundnuts at 4,000 FCFA a bag. He sells 40 bags at 5,000 FCFA a bag and the remaining 10 bags at 3,000 FCFA a bag.",
       [PN("What did he pay for all the bags?", 50 * 4000, "50 × 4,000 = 200,000 FCFA.", unit="FCFA"),
        PN("How much money did he receive in total?", 40 * 5000 + 10 * 3000, "40 × 5,000 = 200,000 and 10 × 3,000 = 30,000. Total 230,000 FCFA.", unit="FCFA"),
        PN("What is his percentage profit?", (230000 - 200000) * 100 / 200000, "Profit = 30,000. 30,000 ÷ 200,000 × 100 = 15%.", unit="%", points=2)])],
    [("CP = 6,000 FCFA and SP = 7,500 FCFA. What is the profit?", "1,500 FCFA", ["13,500 FCFA", "25 FCFA", "1,000 FCFA"], "7,500 − 6,000 = 1,500."),
     ("We calculate a percentage profit on the...", "cost price", ["selling price", "profit", "discount"], "Percentage profit = profit ÷ cost price × 100."),
     ("A discount of 20% on 5,000 FCFA is...", "1,000 FCFA", ["100 FCFA", "4,000 FCFA", "2,000 FCFA"], "20 ÷ 100 × 5,000 = 1,000 FCFA."),
     ("Simple interest on 10,000 FCFA at 10% for 1 year is...", "1,000 FCFA", ["100 FCFA", "10,000 FCFA", "11,000 FCFA"], "10,000 × 10 × 1 ÷ 100 = 1,000."),
     ("CP = 800 FCFA, SP = 600 FCFA. The result is...", "a loss of 200 FCFA", ["a profit of 200 FCFA", "a loss of 600 FCFA", "no profit and no loss"], "SP is less than CP, so it is a loss: 800 − 600 = 200.")],
    ["Interest rates and amounts are only for practice, not real bank rates. Check that the syllabus asks for simple interest with time in years only."],
    prereq=["fslc-maths-ratio-proportion", "fslc-maths-money-fcfa"])

# ---- averages
marks = [8, 12, 10, 14, 6]; assert sum(marks) / 5 == 10
assert sorted([3, 9, 5, 7, 1])[2] == 5
from statistics import mode, median
assert mode([4, 6, 6, 7, 9, 6, 4]) == 6 and median([2, 4, 6, 10]) == 5
assert 5 * 20 - (18 + 22 + 25 + 15) == 20
sales = [4500, 5200, 3800, 6100, 5000, 5400]; assert sum(sales) == 30000 and sum(sales) / 6 == 5000 and max(sales) - min(sales) == 2300 and 7 * 5200 - sum(sales) == 6400
assert 4 * 8 + 13 == 45
fig_avg = bars([("Ada", 12), ("Bih", 15), ("Che", 9), ("Dina", 14), ("Eko", 10)], "marks", 400, 240)
build(ch, "averages", "Averages: mean, median, mode and range", 25,
    ["Calculate the mean (average) of a set of numbers.", "Find the median, the mode and the range.", "Solve simple problems about averages."],
    [("key", "formule", "The mean (average)", "**Mean = total of the values ÷ number of values.** Example: the marks 12, 15, 9, 14 and 10 add up to 60. There are 5 pupils, so the mean is 60 ÷ 5 = 12. The mean can be a number that is not in the list."),
     ("fig", fig_avg, "Marks of five pupils: the mean is 12.", "A bar chart of five pupils called Ada, Bih, Che, Dina and Eko. Their marks are 12, 15, 9, 14 and 10."),
     ("key", "definition", "Median, mode and range", "- **Median**: the middle value when the numbers are in order. With an even number of values, find the mean of the two middle ones.\n- **Mode**: the value that appears **most often**.\n- **Range**: the biggest value minus the smallest value."),
     ("key", "pieges", "Common mistakes", "- Finding the median **without** putting the numbers in order first.\n- Dividing the total by the wrong number of values.\n- Mixing up the mode (most frequent) and the mean."),
     ("ex", "Example 1", "Four numbers have a mean of 8. A fifth number is added and the mean becomes 9. Find the fifth number.", ["Total of the four numbers: 4 × 8 = 32.", "Total of five numbers: 5 × 9 = 45.", "Fifth number: 45 − 32 = 13."], "The fifth number is 13."),
     ("ex", "Example 2", "Find the median and the range of 2, 10, 4, 6.", ["Put in order: 2, 4, 6, 10.", "There are 4 values, so the median is the mean of the two middle ones: (4 + 6) ÷ 2 = 5.", "Range = 10 − 2 = 8."], "Median 5; range 8.")],
    [N("application", "Find the mean of 8, 12, 10, 14 and 6.", 10, "Total = 50. 50 ÷ 5 = 10."),
     M("application", "What is the median of 3, 9, 5, 7, 1?", "5", ["9", "3", "7"], "In order: 1, 3, 5, 7, 9. The middle value is 5."),
     M("application", "What is the mode of 4, 6, 6, 7, 9, 6, 4?", "6", ["4", "7", "9"], "6 appears three times, more than any other number."),
     N("approfondissement", "The mean of five numbers is 20. Four of them are 18, 22, 25 and 15. Find the fifth number.", 20, "Total = 5 × 20 = 100. The four add to 80. The fifth is 100 − 80 = 20."),
     TF("approfondissement", "The range of 3, 8, 5, 11 and 2 is 9.", True, "Biggest 11, smallest 2: 11 − 2 = 9."),
     P("A puff-puff seller's daily sales (FCFA) from Monday to Saturday were: 4,500; 5,200; 3,800; 6,100; 5,000; 5,400.",
       [PN("What is the total of the six days?", 30000, "4,500 + 5,200 + 3,800 + 6,100 + 5,000 + 5,400 = 30,000 FCFA.", unit="FCFA"),
        PN("What is the mean daily sale?", 5000, "30,000 ÷ 6 = 5,000 FCFA.", unit="FCFA"),
        PN("What is the range of the sales?", 2300, "6,100 − 3,800 = 2,300 FCFA.", unit="FCFA"),
        PN("How much must she sell on Sunday so that the mean of the 7 days is 5,200 FCFA?", 6400, "7 × 5,200 = 36,400. 36,400 − 30,000 = 6,400 FCFA.", unit="FCFA", points=2)])],
    [("The mean of 5, 7 and 12 is...", "8", ["7", "24", "12"], "Total 24 ÷ 3 = 8."),
     ("The range of 14, 6, 9 and 20 is...", "14", ["20", "6", "29"], "20 − 6 = 14."),
     ("The median of 2, 4, 6, 8, 10 is...", "6", ["5", "8", "30"], "The numbers are in order; the middle one is 6."),
     ("In the list 1, 2, 2, 3, 3, 3, 4 the mode is...", "3", ["2", "4", "2.5"], "3 appears most often."),
     ("Total 90 for 6 values. The mean is...", "15", ["540", "84", "96"], "90 ÷ 6 = 15.")],
    ["Median with an even number of values and the range: confirm they are in the Class 6 syllabus (enrichment otherwise)."],
    prereq=["fslc-maths-whole-numbers"])

# =========================================================== chapter 7: time, units, speed
import datetime
ch = p.chapter("time-units-speed", "Time, units and speed", REF)

# ---- time and calendar
assert (datetime.date(2025, 4, 5) - datetime.date(2025, 3, 12)).days == 24
assert (datetime.date(2025, 3, 1) - datetime.date(2025, 2, 1)).days == 28 and (datetime.date(2024, 3, 1) - datetime.date(2024, 2, 1)).days == 29
assert 15 * 60 + 45 == 945 and (11 * 60 + 5) - (7 * 60 + 20) == 225
assert (8 * 60 + 45 + 3 * 60 + 50) == 12 * 60 + 35 and 12 * 60 + 35 + 25 == 13 * 60
assert 365 // 7 == 52 and 365 % 7 == 1 and [y for y in (1900, 2023, 2024, 2025, 2028) if y % 4 == 0] == [1900, 2024, 2028]
fig_clock = shapes([CIRCLE(200, 120, 90, fill="lightyellow"), CIRCLE(200, 120, 5, fill="ink"),
                    LINE(200, 120, 246, 139, width=5), LINE(200, 120, 125, 120, width=3),
                    T(200, 22, "12", 16, bold=True), T(305, 126, "3", 16, bold=True), T(200, 228, "6", 16, bold=True), T(95, 126, "9", 16, bold=True)], 400, 240)
build(ch, "time-calendar", "Time and the calendar", 25,
    ["Read the time on a clock and use the 12-hour and 24-hour systems.", "Calculate durations, start times and finishing times.", "Use the calendar: days, weeks, months, years and leap years."],
    [("key", "definition", "Units of time", "60 seconds = 1 minute; 60 minutes = 1 hour; 24 hours = 1 day; 7 days = 1 week; 12 months = 1 year; 365 days = 1 year, and 366 days in a **leap year** (every 4 years, for example 2024 and 2028). **30 days** have April, June, September and November. February has 28 days (29 in a leap year). The other months have 31."),
     ("fig", fig_clock, "A clock showing 3:45.", "A clock face. The short hour hand points between 3 and 4. The long minute hand points to 9. The clock shows quarter to four."),
     ("key", "retenir", "12-hour and 24-hour clock", "The 24-hour clock counts from 00:00 to 23:59. From 13:00 onwards subtract 12 to get the pm time: 15:45 is 3:45 pm. Add 12 to a pm time to get the 24-hour time: 8:30 pm is 20:30. Midnight is 00:00 and midday is 12:00."),
     ("key", "pieges", "Common mistakes", "- Working with 100 minutes in an hour: there are only **60**. 2 h 45 min + 30 min = 3 h 15 min, not 2 h 75 min.\n- Forgetting that some months have 30 days and others 31.\n- Counting the first day twice when you find the number of days between two dates."),
     ("ex", "Example 1", "A bus leaves at 08:45 and the journey lasts 3 hours 50 minutes. At what time does it arrive?", ["Add the hours: 08:45 + 3 h = 11:45.", "Add the minutes: 11:45 + 50 min = 11:95, which is 12:35 (95 − 60 = 35 and one more hour)."], "It arrives at 12:35 (12:35 pm)."),
     ("ex", "Example 2", "How many days are there from 12 March to 5 April in a year that is not a leap year?", ["From 12 March to 31 March: 31 − 12 = 19 days.", "From 31 March to 5 April: 5 more days.", "Total: 19 + 5 = 24 days."], "24 days.")],
    [M("application", "Write 3:45 pm in the 24-hour clock.", "15:45", ["03:45", "13:45", "17:45"], "Add 12 hours to 3:45 pm: 15:45."),
     N("application", "A lesson starts at 07:20 and ends at 11:05. How many minutes does it last?", 225, "From 07:20 to 11:00 is 3 h 40 min; plus 5 min makes 3 h 45 min = 3 × 60 + 45 = 225 minutes.", unit="minutes"),
     M("application", "How many days has September?", "30", ["28", "31", "29"], "September is one of the 30-day months: April, June, September, November."),
     N("approfondissement", "How many days are there from 12 March to 5 April (not a leap year)?", 24, "19 days to the end of March, then 5 days: 24 days.", unit="days"),
     TF("approfondissement", "The year 2024 is a leap year, so February 2024 has 29 days.", True, "2024 is divisible by 4, so it is a leap year."),
     P("A bus leaves Bafoussam at 08:45. The journey takes 3 hours 50 minutes.",
       [PM("At what time does the bus arrive?", "12:35", ["11:95", "12:05", "13:35"], "08:45 + 3 h 50 min = 12:35.", points=2),
        PM("The bus is 25 minutes late. It arrives at...", "13:00", ["12:60", "12:35", "13:25"], "12:35 + 25 min = 13:00, which is 1:00 pm.", points=1),
        PN("How many minutes is the planned journey?", 230, "3 × 60 + 50 = 230 minutes.", unit="minutes", points=1)])],
    [("How many minutes are there in 2 hours 15 minutes?", "135", ["215", "175", "120"], "2 × 60 + 15 = 135."),
     ("8:30 pm in the 24-hour clock is...", "20:30", ["08:30", "18:30", "22:30"], "8 + 12 = 20."),
     ("How many days are there in a leap year?", "366", ["365", "364", "360"], "A leap year has one extra day, 29 February."),
     ("A film starts at 18:40 and lasts 1 h 35 min. It ends at...", "20:15", ["19:75", "20:05", "19:15"], "18:40 + 1 h = 19:40; + 35 min = 20:15."),
     ("Which month has 31 days?", "July", ["June", "April", "November"], "June, April and November have 30 days.")],
    ["Leap-year rule: only 'every 4 years' is taught; the exception for century years (1900, 2100) is not covered at this level.", "12-hour/24-hour clock: check which one the FSLC paper uses."],
    prereq=["fslc-maths-measurement"])

# ---- unit conversion
assert 3.5 * 1000 == 3500 and 2750 / 1000 == 2.75 and 4.2 * 1000 == 4200 and 1 * 60 + 45 == 105 and 500 - 340 == 160
assert 2700 + 1450 == 4150 and 12 * 750 == 9000 and 5000 // 750 == 6
fig_units = shapes([RECT(20, 80, 70, 40, fill="lightblue"), T(55, 107, "km", 16, bold=True),
                    RECT(120, 80, 70, 40, fill="lightblue"), T(155, 107, "m", 16, bold=True),
                    RECT(220, 80, 70, 40, fill="lightblue"), T(255, 107, "cm", 16, bold=True),
                    RECT(320, 80, 70, 40, fill="lightblue"), T(355, 107, "mm", 16, bold=True),
                    LINE(92, 90, 118, 90, arrow="end"), LINE(192, 90, 218, 90, arrow="end"), LINE(292, 90, 318, 90, arrow="end"),
                    T(105, 60, "×1,000", 12), T(205, 60, "×100", 12), T(305, 60, "×10", 12),
                    LINE(118, 110, 92, 110, arrow="end"), LINE(218, 110, 192, 110, arrow="end"), LINE(318, 110, 292, 110, arrow="end"),
                    T(105, 145, "÷1,000", 12), T(205, 145, "÷100", 12), T(305, 145, "÷10", 12),
                    T(200, 185, "Big unit to small unit: multiply. Small to big: divide.", 13)], 400, 210)
build(ch, "units-conversion", "Converting units of length, mass, capacity and time", 25,
    ["Convert between units of length, mass, capacity and time.", "Add and subtract measures with mixed units.", "Solve problems that need a change of units."],
    [("key", "formule", "Units to remember", "- Length: 1 km = 1,000 m; 1 m = 100 cm; 1 cm = 10 mm.\n- Mass: 1 kg = 1,000 g; 1 tonne = 1,000 kg.\n- Capacity: 1 litre = 1,000 ml (millilitres).\n- Time: 1 h = 60 min; 1 min = 60 s.\nTo go from a **big** unit to a **small** one, multiply. To go from small to big, divide."),
     ("fig", fig_units, "How to change between kilometres, metres, centimetres and millimetres.", "Four boxes in a row: km, m, cm and mm. Arrows going right say times 1,000, times 100 and times 10. Arrows going left say divide by the same numbers."),
     ("key", "methode", "Mixed units", "To add or subtract, change everything to the **same small unit** first. Example: 2 kg 700 g + 1 kg 450 g = 2,700 g + 1,450 g = 4,150 g = 4 kg 150 g. For time, do not use 100: 1 h 45 min = 105 minutes."),
     ("key", "pieges", "Common mistakes", "- Multiplying when you should divide (km to m: multiply; g to kg: divide).\n- Adding numbers with **different units** (like 2 m + 50 cm = 52!). Change first: 2 m + 50 cm = 250 cm.\n- Using 100 for time: there are 60 minutes in an hour."),
     ("ex", "Example 1", "Change 3.5 km to metres, and 2,750 g to kilograms.", ["Kilometres to metres: 3.5 × 1,000 = 3,500 m.", "Grams to kilograms: 2,750 ÷ 1,000 = 2.75 kg."], "3,500 m and 2.75 kg."),
     ("ex", "Example 2", "A tailor has a roll of cloth 5 m long. He cuts 3 m 40 cm. How much cloth is left?", ["Change to centimetres: 5 m = 500 cm and 3 m 40 cm = 340 cm.", "Subtract: 500 − 340 = 160 cm = 1 m 60 cm."], "1 m 60 cm (160 cm).")],
    [N("application", "Change 3.5 km to metres.", 3500, "3.5 × 1,000 = 3,500.", unit="m"),
     M("application", "2,750 g is equal to...", "2.75 kg", ["27.5 kg", "275 kg", "0.275 kg"], "Divide by 1,000: 2,750 ÷ 1,000 = 2.75."),
     N("application", "Change 4.2 litres to millilitres.", 4200, "4.2 × 1,000 = 4,200.", unit="ml"),
     N("approfondissement", "How many minutes are there in 1 hour 45 minutes?", 105, "60 + 45 = 105 minutes.", unit="minutes"),
     N("approfondissement", "Add 2 kg 700 g and 1 kg 450 g. Give the answer in grams.", 4150, "2,700 + 1,450 = 4,150 g.", unit="g"),
     P("A shop sells cooking oil in bottles of 750 ml.",
       [PN("How many millilitres are in 12 bottles?", 9000, "12 × 750 = 9,000 ml.", unit="ml"),
        PN("How many litres is that?", 9, "9,000 ÷ 1,000 = 9 litres.", unit="litres"),
        PN("How many full bottles can be filled from a 5-litre can?", 6, "5 litres = 5,000 ml. 5,000 ÷ 750 = 6 remainder 500, so 6 full bottles.", unit="bottles", points=2)])],
    [("5 km is equal to...", "5,000 m", ["500 m", "50 m", "50,000 m"], "1 km = 1,000 m."),
     ("3 kg 250 g in grams is...", "3,250 g", ["325 g", "3,025 g", "32,500 g"], "3 × 1,000 + 250 = 3,250."),
     ("How many cm are there in 2.5 m?", "250", ["25", "2,500", "2.5"], "2.5 × 100 = 250."),
     ("1 hour 20 minutes = ... minutes", "80", ["120", "100", "120.20"], "60 + 20 = 80."),
     ("A jug holds 1.5 litres. In millilitres this is...", "1,500 ml", ["150 ml", "15 ml", "15,000 ml"], "1.5 × 1,000 = 1,500.")],
    ["The existing 'Measurement' lesson also covers basic conversions; this lesson adds mixed-unit sums and problems."],
    prereq=["fslc-maths-measurement"])

# ---- speed, distance, time
assert 240 / 4 == 60 and 80 * 3 == 240 and 150 / 50 == 3 and 60 * 0.75 == 45
assert abs((100 + 100) / (2 + 1) - 66.67) < 0.01
assert 18 * 2.5 == 45 and 45 / 15 == 3
fig_sdt = shapes([RECT(130, 25, 140, 130, fill="lightyellow"), LINE(130, 80, 270, 80), LINE(200, 80, 200, 155),
                  T(200, 62, "D", 24, bold=True), T(165, 128, "S", 24, bold=True), T(235, 128, "T", 24, bold=True),
                  T(200, 185, "D = S × T    S = D ÷ T    T = D ÷ S", 14, bold=True),
                  T(200, 208, "D: distance   S: speed   T: time", 12)], 400, 225)
build(ch, "speed-distance-time", "Speed, distance and time", 30,
    ["Calculate speed, distance or time from the other two.", "Change minutes to hours in speed problems.", "Find an average speed for a whole journey."],
    [("key", "formule", "The speed rule", "**Speed = distance ÷ time.** If a car travels 180 km in 3 hours, its speed is 180 ÷ 3 = 60 km/h (kilometres per hour). From this we get: **distance = speed × time** and **time = distance ÷ speed**. The units must agree: km with hours gives km/h; metres with seconds gives m/s."),
     ("fig", fig_sdt, "The speed triangle: cover the one you want.", "A box split into three parts: D on the top, S at bottom left and T at bottom right. Below are the three formulas for D, S and T."),
     ("key", "methode", "Minutes and hours", "Change minutes to hours **before** you use km/h: 30 min = 0.5 h; 45 min = 0.75 h; 15 min = 0.25 h; 20 min = 1/3 h. Example: at 60 km/h for 45 minutes, the distance is 60 × 0.75 = 45 km."),
     ("key", "pieges", "Common mistakes", "- Using minutes with km/h without changing to hours.\n- Finding an **average speed** by adding two speeds and dividing by 2. The correct way: **total distance ÷ total time**.\n- Dividing the wrong way: time is distance ÷ speed, not speed ÷ distance."),
     ("ex", "Example 1", "A bus travels 150 km at 50 km/h. How long does the journey take?", ["Time = distance ÷ speed.", "150 ÷ 50 = 3 hours."], "3 hours."),
     ("ex", "Example 2", "A man goes 100 km at 50 km/h, then another 100 km at 100 km/h. What is his average speed for the whole journey?", ["Time for the first part: 100 ÷ 50 = 2 h. Second part: 100 ÷ 100 = 1 h.", "Total distance 200 km; total time 3 h.", "Average speed = 200 ÷ 3 ≈ 66.7 km/h (not 75!)."], "About 66.7 km/h.")],
    [N("application", "A car travels 240 km in 4 hours. What is its speed?", 60, "240 ÷ 4 = 60.", unit="km/h"),
     N("application", "A bus drives at 80 km/h for 3 hours. What distance does it cover?", 240, "80 × 3 = 240.", unit="km"),
     N("application", "A cyclist rides 30 km at 15 km/h. How long does it take?", 2, "30 ÷ 15 = 2.", unit="hours"),
     N("approfondissement", "A motorbike goes at 60 km/h for 45 minutes. How far does it travel?", 45, "45 min = 0.75 h. 60 × 0.75 = 45 km.", unit="km"),
     N("approfondissement", "A traveller goes 100 km in 2 hours and then 100 km in 1 hour. What is his average speed (to the nearest km/h)?", 67, "200 km in 3 hours: 200 ÷ 3 = 66.7, about 67 km/h.", tol=0.5, unit="km/h"),
     P("A cyclist leaves home at 07:30 and rides at 18 km/h for 2 hours 30 minutes.",
       [PN("How far does he ride?", 45, "2 h 30 min = 2.5 h. 18 × 2.5 = 45 km.", unit="km", points=2),
        PM("At what time does he arrive?", "10:00", ["09:30", "10:30", "09:00"], "07:30 + 2 h 30 min = 10:00."),
        PN("He returns by the same road at 15 km/h. How many hours does the return take?", 3, "45 ÷ 15 = 3 hours.", unit="hours")])],
    [("A runner does 100 m in 20 seconds. His speed is...", "5 m/s", ["20 m/s", "0.2 m/s", "2,000 m/s"], "100 ÷ 20 = 5."),
     ("A train at 90 km/h travels for 2 hours. The distance is...", "180 km", ["45 km", "92 km", "88 km"], "90 × 2 = 180."),
     ("How long does 120 km take at 40 km/h?", "3 hours", ["30 hours", "80 hours", "5 hours"], "120 ÷ 40 = 3."),
     ("30 minutes in hours is...", "0.5 h", ["30 h", "0.3 h", "5 h"], "30 ÷ 60 = 0.5."),
     ("Speed = ...", "distance ÷ time", ["distance × time", "time ÷ distance", "distance + time"], "Speed is how far we go in one hour.")],
    ["Average speed uses 'total distance ÷ total time'; check this is expected at Class 6."],
    prereq=["fslc-maths-time-calendar", "fslc-maths-units-conversion"])

# =========================================================== chapter 8: geometry
ch = p.chapter("geometry-more", "Angles, triangles, circles and solids", REF)
PI = 3.14

# ---- angles and triangles
assert 180 - 65 - 50 == 65 and (180 - 40) / 2 == 70 and 180 - 118 == 62 and 360 - 90 - 120 - 110 == 40
assert 180 - 72 - 48 == 60 and (180 - 50) / 2 == 65 and 180 - 48 - 67 == 65 and 180 - 65 == 115 and 360 - 90 - 130 - 75 == 65
fig_tri = shapes([POLY([60, 190, 340, 190, 220, 40], fill="lightblue"),
                  T(60, 215, "50°", 16, bold=True), T(340, 215, "65°", 16, bold=True), T(220, 28, "?", 18, bold=True, color="red"),
                  T(200, 180, "a triangle: 50° + 65° + ? = 180°", 12)], 400, 235)
build(ch, "angles-triangles", "Angles and triangles", 30,
    ["Name acute, right, obtuse, straight and reflex angles.", "Use the angle facts: 180° in a triangle and on a straight line, 360° round a point.", "Name triangles by their sides and angles and find missing angles."],
    [("key", "definition", "Kinds of angles", "We measure angles in degrees (°). An **acute** angle is less than 90°. A **right angle** is exactly 90°. An **obtuse** angle is between 90° and 180°. A **straight** angle is 180°. A **reflex** angle is between 180° and 360°. A full turn is 360°."),
     ("fig", fig_tri, "The three angles of a triangle add up to 180°.", "A blue triangle. Two corners are marked 50 degrees and 65 degrees. The top corner has a question mark. The angles add up to 180 degrees."),
     ("key", "propriete", "Angle facts", "- The angles of a **triangle** add up to **180°**.\n- Angles on a **straight line** add up to **180°**.\n- Angles round a **point** add up to **360°**.\n- In an **isosceles** triangle two sides are equal and the two base angles are equal. In an **equilateral** triangle all sides are equal and each angle is 60°."),
     ("key", "pieges", "Common mistakes", "- Using 360° for a triangle (that is for a point).\n- In an isosceles triangle, writing the same angle for **all three** corners. Only the two base angles are equal.\n- Reading the wrong scale on a protractor: start from 0 on the side where the arm points."),
     ("ex", "Example 1", "Two angles of a triangle are 65° and 50°. Find the third angle.", ["The three angles add up to 180°.", "65° + 50° = 115°.", "Third angle = 180° − 115° = 65°."], "65°."),
     ("ex", "Example 2", "An isosceles triangle has an angle of 40° at the top (between the two equal sides). Find the base angles.", ["Sum of the two base angles: 180° − 40° = 140°.", "The two base angles are equal: 140° ÷ 2 = 70° each."], "70° and 70°.")],
    [N("application", "Two angles of a triangle are 72° and 48°. What is the third angle?", 60, "72 + 48 = 120. 180 − 120 = 60.", unit="°"),
     M("application", "An angle of 135° is...", "obtuse", ["acute", "right", "reflex"], "It is between 90° and 180°, so it is obtuse."),
     N("application", "Two angles on a straight line are 118° and x. Find x.", 62, "180 − 118 = 62.", unit="°"),
     N("approfondissement", "An isosceles triangle has a top angle of 50°. How big is each base angle?", 65, "(180 − 50) ÷ 2 = 65.", unit="°"),
     TF("approfondissement", "A triangle can have two right angles.", False, "Two right angles already make 180°, so there would be no angle left for the third corner."),
     P("In triangle PQR, angle P = 48° and angle Q = 67°. The side QR is extended in a straight line to a point S.",
       [PN("Find angle R inside the triangle.", 65, "180 − 48 − 67 = 65°.", unit="°"),
        PM("What type of triangle is PQR by its angles?", "acute-angled", ["right-angled", "obtuse-angled", "equilateral"], "All three angles (48°, 67°, 65°) are less than 90°."),
        PN("Find angle PRS (on the straight line QS, next to angle R).", 115, "Angles on a straight line add to 180°: 180 − 65 = 115°.", unit="°", points=2)])],
    [("How many degrees are there in a full turn?", "360°", ["180°", "90°", "100°"], "A full turn is 360°."),
     ("The angles of a triangle add up to...", "180°", ["90°", "360°", "100°"], "This is true for every triangle."),
     ("A triangle with 3 equal sides is called...", "equilateral", ["isosceles", "scalene", "right-angled"], "Equi means equal; each angle is 60°."),
     ("Which angle is acute?", "35°", ["90°", "120°", "200°"], "An acute angle is less than 90°."),
     ("Angles round a point add up to...", "360°", ["180°", "90°", "270°"], "They make a full turn.")],
    ["Constructing angles with a protractor is not included in this lesson (practical work); to be added by the teacher."],
    prereq=["fslc-maths-measurement"])

# ---- circles
def C(r): return round(2 * PI * r, 2)
def A(r): return round(PI * r * r, 2)
assert C(7) == 43.96 and A(7) == 153.86 and A(10) == 314 and C(5) == 31.4 and round(31.4 * 800) == 25120
assert round(PI * 70, 2) == 219.8 and round(219.8 * 100, 2) == 21980
fig_circ = shapes([CIRCLE(200, 120, 80, fill="lightyellow"), CIRCLE(200, 120, 3, fill="ink"),
                   LINE(120, 120, 280, 120, color="blue", width=2), LINE(200, 120, 258, 63, color="red", width=2),
                   T(160, 110, "diameter", 13, color="blue", bold=True), T(255, 100, "radius", 13, color="red", bold=True, anchor="start"),
                   T(200, 228, "circumference = the distance round the circle", 13)], 400, 240)
build(ch, "circles", "Circles: circumference and area", 30,
    ["Name the parts of a circle: centre, radius, diameter, circumference.", "Calculate the circumference and the area using π = 3.14.", "Solve simple problems about wheels and round gardens."],
    [("key", "definition", "Parts of a circle", "The **radius** (r) goes from the centre to the edge. The **diameter** (d) goes right across through the centre: d = 2 × r. The **circumference** is the distance round the circle. In this course we use **π = 3.14** (π is about 3.14159, and we round it to 3.14)."),
     ("fig", fig_circ, "Diameter, radius and circumference.", "A circle with its centre marked. A blue line across the circle through the centre is the diameter. A red line from the centre to the edge is the radius."),
     ("key", "formule", "Circle formulas (π = 3.14)", "- **Circumference**: C = π × d = 2 × π × r.\n- **Area**: A = π × r × r (radius **squared**).\nThe circumference is a length (cm, m). The area is in square units (cm², m²). Always find the **radius** first: if you are given the diameter, divide it by 2."),
     ("key", "pieges", "Common mistakes", "- Using the **diameter** in the area formula. Find r = d ÷ 2 first.\n- Writing r × 2 instead of r × r for the area: for r = 7, r × r = 49, not 14.\n- Writing cm instead of cm² for an area."),
     ("ex", "Example 1", "A circle has a radius of 7 cm. Find its circumference and its area (π = 3.14).", ["Circumference = 2 × 3.14 × 7 = 6.28 × 7 = 43.96 cm.", "Area = 3.14 × 7 × 7 = 3.14 × 49 = 153.86 cm²."], "C = 43.96 cm; A = 153.86 cm²."),
     ("ex", "Example 2", "A round flower bed has a diameter of 20 m. What is its area (π = 3.14)?", ["Radius = 20 ÷ 2 = 10 m.", "Area = 3.14 × 10 × 10 = 314 m²."], "314 m².")],
    [N("application", "Find the circumference of a circle with radius 7 cm (π = 3.14).", 43.96, "2 × 3.14 × 7 = 43.96.", tol=0.01, unit="cm"),
     N("application", "Find the area of a circle of radius 10 m (π = 3.14).", 314, "3.14 × 10 × 10 = 314.", unit="m²"),
     M("application", "The diameter of a circle is 18 cm. Its radius is...", "9 cm", ["36 cm", "18 cm", "6 cm"], "Radius = diameter ÷ 2 = 9 cm."),
     N("approfondissement", "A round garden has a radius of 5 m. Fencing costs 800 FCFA per metre. What is the cost of fencing all round it (π = 3.14, in FCFA)?", 25120, "C = 2 × 3.14 × 5 = 31.4 m. Cost = 31.4 × 800 = 25,120 FCFA.", tol=1),
     TF("approfondissement", "If the radius of a circle doubles, its circumference doubles too.", True, "C = 2 × π × r, so twice the radius gives twice the circumference. (The area becomes four times bigger!)"),
     P("The wheel of a bicycle has a diameter of 70 cm. Use π = 3.14.",
       [PN("Find the circumference of the wheel.", 219.8, "C = 3.14 × 70 = 219.8 cm.", tol=0.1, unit="cm", points=2),
        PN("How far does the bicycle go in 100 turns of the wheel (in cm)?", 21980, "219.8 × 100 = 21,980 cm.", tol=1, unit="cm"),
        PN("Give this distance in metres.", 219.8, "21,980 ÷ 100 = 219.8 m.", tol=0.1, unit="m")])],
    [("The distance round a circle is called its...", "circumference", ["radius", "diameter", "area"], "It is the perimeter of the circle."),
     ("A circle has radius 3 cm. What is its diameter?", "6 cm", ["1.5 cm", "9 cm", "3 cm"], "Diameter = 2 × radius."),
     ("Which formula gives the area of a circle?", "π × r × r", ["2 × π × r", "π × d", "r × r × 2"], "The area uses the radius squared."),
     ("Area of a circle with radius 2 m (π = 3.14)?", "12.56 m²", ["6.28 m²", "12.56 m", "25.12 m²"], "3.14 × 2 × 2 = 12.56 m²."),
     ("The value of π that we use is about...", "3.14", ["1.34", "31.4", "3"], "π ≈ 3.14.")],
    ["The text states clearly that π = 3.14 is used; check that the FSLC uses 3.14 (some books use 22/7)."],
    prereq=["fslc-maths-measurement"])

# ---- area, perimeter, volume: harder shapes
assert 12 * 8 - 4 * 3 == 84 and 2 * (12 + 8) == 40 and 10 * 6 == 60 and 50 * 40 * 30 == 60000 and 60000 // 1000 == 60
assert (6 * 4 * 10000) // (50 * 50) == 96 and 80 * 50 * 40 == 160000 and 160000 // 1000 == 160 and 160 * 3 // 4 == 120 and 120 // 10 == 12
assert 12 * 8 - 4 * 3 == 84 and 18 * 2 + 2 * 10 == 56
fig_L = shapes([POLY([80, 30, 240, 30, 240, 90, 320, 90, 320, 190, 80, 190], fill="lightblue"),
                T(200, 212, "12 m", 14, bold=True), T(62, 112, "8 m", 14, bold=True, anchor="end"),
                T(280, 62, "4 m", 13), T(342, 142, "3 m", 13, anchor="start"),
                T(160, 112, "L-shape", 14)], 400, 230)
build(ch, "area-volume-more", "Area, perimeter and volume: compound shapes and solids", 35,
    ["Find the area and perimeter of compound (L-shaped) figures.", "Use the area of a parallelogram and a triangle.", "Find the volume of a cuboid and change cm³ to litres."],
    [("key", "formule", "More formulas", "- **Rectangle**: A = l × w; P = 2 × (l + w).\n- **Parallelogram**: A = base × height (the height is straight up, not the slanted side).\n- **Triangle**: A = (base × height) ÷ 2.\n- **Cuboid**: V = l × w × h, in cm³ or m³.\n- **Capacity**: 1 litre = 1,000 cm³ (so 1 m³ = 1,000 litres)."),
     ("fig", fig_L, "An L-shape: a 12 m by 8 m rectangle with a 4 m by 3 m corner removed.", "A blue L-shaped figure. The bottom is 12 metres and the left side is 8 metres. The missing corner at the top right is 4 metres wide and 3 metres high."),
     ("key", "methode", "Compound shapes", "Cut the shape into rectangles (or triangles) you know, or take a small piece away from a big rectangle. For the L-shape: area = 12 × 8 − 4 × 3 = 96 − 12 = 84 m². For the **perimeter**, add all the outside sides. Taking a corner out of a rectangle does not change its perimeter."),
     ("key", "pieges", "Common mistakes", "- Adding the **inside** lines to the perimeter.\n- Using the slanted side as the height of a parallelogram or triangle.\n- Forgetting to change units: cm³ to litres means **divide by 1,000**. Do not mix m and cm in the same formula."),
     ("ex", "Example 1", "Find the area and perimeter of the L-shape in the picture (12 m by 8 m with a 4 m by 3 m corner removed).", ["Area of the big rectangle: 12 × 8 = 96 m². Area removed: 4 × 3 = 12 m².", "Area of the L-shape: 96 − 12 = 84 m².", "Perimeter = 12 + 8 + 12 + 8 = 40 m (same as the big rectangle)."], "Area 84 m²; perimeter 40 m."),
     ("ex", "Example 2", "A water tank is 50 cm long, 40 cm wide and 30 cm high. How many litres can it hold?", ["Volume = 50 × 40 × 30 = 60,000 cm³.", "1 litre = 1,000 cm³, so 60,000 ÷ 1,000 = 60 litres."], "60 litres.")],
    [N("application", "A parallelogram has a base of 10 cm and a height of 6 cm. Find its area.", 60, "10 × 6 = 60.", unit="cm²"),
     N("application", "Find the volume of a cuboid 8 cm long, 5 cm wide and 3 cm high.", 8 * 5 * 3, "8 × 5 × 3 = 120.", unit="cm³"),
     N("application", "A tank has a volume of 60,000 cm³. How many litres is that?", 60, "60,000 ÷ 1,000 = 60.", unit="litres"),
     N("approfondissement", "A room is 6 m by 4 m. It is covered with square tiles of side 50 cm. How many tiles are needed?", 96, "Floor: 6 m × 4 m = 24 m² = 240,000 cm². One tile: 50 × 50 = 2,500 cm². 240,000 ÷ 2,500 = 96 tiles."),
     N("approfondissement", "An L-shaped field is a 20 m by 15 m rectangle with a 5 m by 4 m corner removed. What is its area?", 20 * 15 - 5 * 4, "300 − 20 = 280 m².", unit="m²"),
     P("A rectangular water tank is 80 cm long, 50 cm wide and 40 cm high.",
       [PN("What is the volume of the tank in cm³?", 160000, "80 × 50 × 40 = 160,000 cm³.", unit="cm³"),
        PN("How many litres can it hold when it is full?", 160, "160,000 ÷ 1,000 = 160 litres.", unit="litres"),
        PN("It is filled to 3/4 of its height. How many 10-litre buckets of water are in it?", 12, "3/4 of 160 = 120 litres. 120 ÷ 10 = 12 buckets.", unit="buckets", points=2)])],
    [("The volume of a cuboid is found by...", "l × w × h", ["l + w + h", "2 × (l + w)", "l × w ÷ 2"], "We multiply the three dimensions."),
     ("A triangle has base 8 cm and height 5 cm. Its area is...", "20 cm²", ["40 cm²", "13 cm²", "20 cm"], "(8 × 5) ÷ 2 = 20 cm²."),
     ("1 litre equals...", "1,000 cm³", ["100 cm³", "10 cm³", "10,000 cm³"], "A litre is a cube of 10 cm: 10 × 10 × 10 = 1,000 cm³."),
     ("Volume is measured in...", "cubic units (cm³)", ["square units (cm²)", "units (cm)", "degrees"], "Volume is three-dimensional."),
     ("A square has a side of 9 m. Its perimeter is...", "36 m", ["81 m", "18 m", "27 m"], "4 × 9 = 36 m.")],
    ["This lesson goes beyond the basic 'Measurement and geometry' lesson (compound shapes, capacity). Check the syllabus for parallelogram and compound shapes."],
    prereq=["fslc-maths-measurement"])

# =========================================================== chapter 9: data, probability, algebra
ch = p.chapter("data-algebra", "Data, probability and introduction to algebra", REF)

# ---- data handling
fruit = [("Mango", 12), ("Pawpaw", 8), ("Orange", 10), ("Banana", 6), ("Pineapple", 4)]
assert sum(v for _, v in fruit) == 40 and 12 * 100 // 40 == 30 and 12 - 4 == 8
sales = [("Mon", 20), ("Tue", 35), ("Wed", 25), ("Thu", 40), ("Fri", 30)]; assert sum(v for _, v in sales) == 150 and 150 // 5 == 30 and 40 - 20 == 20
build(ch, "data-handling", "Data: tables, tally charts, pictographs and bar charts", 25,
    ["Collect data with a tally chart and make a frequency table.", "Read and draw a pictograph and a bar chart.", "Answer questions about data (total, most popular, difference)."],
    [("key", "definition", "Collecting and showing data", "**Data** are pieces of information, for example the favourite fruit of each pupil. We count with a **tally chart**: one stroke for each answer, and a bar across for every fifth one. The numbers form a **frequency table**. Then we can show them in a **pictograph** (one symbol stands for a number of items) or in a **bar chart** (the height of each bar shows the number)."),
     ("fig", bars(fruit, None, 400, 240), "Favourite fruit of 40 pupils.", "A bar chart. Mango is 12 pupils, pawpaw 8, orange 10, banana 6 and pineapple 4."),
     ("key", "methode", "Reading a chart", "1. Read the title and the labels.\n2. Look at the scale: what does one step stand for?\n3. Read the value of each bar from the top of the bar.\n4. Check that the values add up to the total number asked.\nFor a pictograph, read the key: if one ☺ stands for 4 pupils, then 3 ☺ stand for 12 pupils."),
     ("key", "pieges", "Common mistakes", "- Not reading the **scale** or the key of the pictograph.\n- Reading the bar next to the right one.\n- Giving the **name** (mango) when the question asks for the **number** (12), or the opposite."),
     ("ex", "Example 1", "40 pupils chose their favourite fruit: mango 12, pawpaw 8, orange 10, banana 6, pineapple 4. What fraction of the pupils chose mango? Give it as a percentage.", ["Fraction: 12 out of 40, that is 12/40.", "Percentage: 12 ÷ 40 × 100 = 30%."], "12/40 = 3/10 = 30%."),
     ("ex", "Example 2", "In a pictograph, one ☺ stands for 4 pupils. How many ☺ are needed for 10 pupils?", ["10 ÷ 4 = 2 remainder 2.", "We draw 2 whole ☺ and a half ☺ (which stands for 2 pupils)."], "2 and a half symbols.")],
    [N("application", "Total number of pupils in the survey: 12 + 8 + 10 + 6 + 4. Find the total.", 40, "12 + 8 = 20; 20 + 10 = 30; 30 + 6 = 36; 36 + 4 = 40.", figure=bars(fruit, None, 400, 240)),
     M("application", "Which fruit is the most popular?", "Mango", ["Pawpaw", "Banana", "Pineapple"], "Mango has the tallest bar (12).", figure=bars(fruit, None, 400, 240)),
     N("application", "How many more pupils chose mango than pineapple?", 8, "12 − 4 = 8.", figure=bars(fruit, None, 400, 240)),
     N("approfondissement", "In a pictograph, one symbol stands for 5 books. A class has 35 books. How many symbols are drawn?", 7, "35 ÷ 5 = 7."),
     TF("approfondissement", "In the fruit survey, pawpaw was chosen by 20% of the pupils.", True, "8 ÷ 40 × 100 = 20%."),
     P("A shopkeeper sold these numbers of bags of rice on five days: Monday 20, Tuesday 35, Wednesday 25, Thursday 40, Friday 30.",
       [PN("How many bags did he sell in all?", 150, "20 + 35 + 25 + 40 + 30 = 150.", unit="bags"),
        PN("What was the mean number of bags sold per day?", 30, "150 ÷ 5 = 30.", unit="bags"),
        PN("What is the difference between the best day and the worst day?", 20, "Best 40 (Thursday), worst 20 (Monday). 40 − 20 = 20.", unit="bags", points=2)],
       figure=bars(sales, "bags", 400, 240))],
    [("A tally chart shows ||||| |||. What number is this?", "8", ["5", "7", "13"], "||||| is 5, plus 3 more strokes = 8."),
     ("In a pictograph, ☺ = 6 pupils. How many pupils are shown by 4 ☺?", "24", ["10", "4", "64"], "4 × 6 = 24."),
     ("Which chart uses the height of bars?", "Bar chart", ["Pictograph", "Tally chart", "Frequency table"], "A bar chart shows the numbers by the height of its bars."),
     ("A frequency table shows...", "how many times each item occurs", ["the average of the data", "the order of the pupils", "the date of the survey"], "Frequency means the number of times."),
     ("6 out of 24 pupils walk to school. What percentage is this?", "25%", ["6%", "30%", "75%"], "6 ÷ 24 = 1/4 = 25%.")],
    ["Data in the survey are invented for practice. Drawing charts on squared paper is practical work not tested here."],
    prereq=["fslc-maths-averages", "fslc-maths-fractions"])

# ---- probability
assert 3 / 10 == 0.3 and 8 / 10 == 0.8 and 3 / 6 == 0.5 and 2 / 8 == 0.25 and (3 + 3) / 8 == 0.75 and 40 * 2 // 8 == 10
fig_prob = shapes([LINE(40, 100, 360, 100, width=3), LINE(40, 90, 40, 110), LINE(200, 90, 200, 110), LINE(360, 90, 360, 110),
                   T(40, 70, "0", 16, bold=True), T(200, 70, "1/2", 16, bold=True), T(360, 70, "1", 16, bold=True),
                   T(50, 135, "Impossible", 13), T(200, 135, "Even chance", 13), T(350, 135, "Certain", 13),
                   T(200, 180, "Probability is always between 0 and 1", 14)], 400, 210)
build(ch, "probability-intro", "Introduction to probability", 25,
    ["Use the words impossible, unlikely, even chance, likely and certain.", "Calculate the probability of an event as a fraction or a decimal.", "Use a coin, a die and a bag of coloured balls as examples."],
    [("key", "definition", "Probability", "**Probability** tells us how likely it is that something happens. It is a number from **0** (impossible) to **1** (certain). **Probability = number of favourable outcomes ÷ number of possible outcomes.** If all outcomes are equally likely, a coin shows heads with probability 1/2."),
     ("fig", fig_prob, "The probability scale from 0 to 1.", "A line with three marks. At the left is 0, impossible. In the middle is 1/2, even chance. At the right is 1, certain."),
     ("key", "retenir", "Examples", "- A coin: 2 outcomes (head, tail). P(head) = 1/2 = 0.5.\n- A die has 6 faces. P(even number) = 3/6 = 1/2, because 2, 4 and 6 are even.\n- A bag with 3 red, 5 blue and 2 green balls has 10 balls. P(red) = 3/10 = 0.3.\n- P(not red) = 1 − 0.3 = 0.7."),
     ("key", "pieges", "Common mistakes", "- Dividing by the number of **other** outcomes instead of the **total** number (the bag has 10 balls, not 7).\n- Thinking that after 3 heads the next coin is 'sure' to be a tail. Each throw is new: the chance is still 1/2.\n- Giving a probability bigger than 1 or smaller than 0."),
     ("ex", "Example 1", "A bag holds 3 red, 5 blue and 2 green balls. One ball is taken without looking. Find the probability that it is blue.", ["Total number of balls: 3 + 5 + 2 = 10.", "Blue balls: 5.", "P(blue) = 5/10 = 1/2 = 0.5."], "P(blue) = 1/2."),
     ("ex", "Example 2", "A fair die is rolled once. What is the probability of a number greater than 4?", ["The numbers greater than 4 are 5 and 6: 2 outcomes.", "There are 6 possible outcomes.", "P = 2/6 = 1/3."], "1/3.")],
    [M("application", "What is the probability of getting a head when you toss a fair coin?", "1/2", ["1", "1/4", "2"], "There are two equally likely outcomes; one is a head."),
     N("application", "A fair die is rolled. What is the probability of an even number? (Give a decimal.)", 0.5, "3 even numbers out of 6: 3/6 = 0.5.", tol=0.01),
     N("application", "A bag has 3 red, 5 blue and 2 green balls. What is the probability of taking a red ball? (Give a decimal.)", 0.3, "3 red out of 10 balls: 3/10 = 0.3.", tol=0.01),
     N("approfondissement", "In the same bag, what is the probability that the ball is NOT green? (Give a decimal.)", 0.8, "8 balls are not green: 8/10 = 0.8. Or 1 − 0.2 = 0.8.", tol=0.01),
     TF("approfondissement", "A probability can be equal to 1.5.", False, "A probability is never more than 1 (certain)."),
     P("A spinner has 8 equal sections: 3 red, 2 blue and 3 yellow. It is spun once.",
       [PN("What is the probability of blue? (decimal)", 0.25, "2/8 = 1/4 = 0.25.", tol=0.01),
        PN("What is the probability of red or yellow? (decimal)", 0.75, "(3 + 3)/8 = 6/8 = 0.75.", tol=0.01, points=2),
        PN("The spinner is spun 40 times. About how many times do you expect blue?", 10, "1/4 of 40 = 10 times (this is only an expected number; the real result may differ).", points=1)])],
    [("The probability of an impossible event is...", "0", ["1", "1/2", "100"], "Impossible means it can never happen: 0."),
     ("A bag has 4 white and 6 black balls. P(white) = ", "4/10", ["4/6", "6/10", "1/4"], "4 white out of 10 balls."),
     ("Which probability means 'certain'?", "1", ["0", "0.5", "10"], "Certain events have probability 1."),
     ("A die is rolled. The probability of getting 6 is...", "1/6", ["6", "1/2", "1/3"], "One face out of six."),
     ("If P(rain) = 0.3, then P(no rain) = ", "0.7", ["0.3", "1.3", "0"], "1 − 0.3 = 0.7.")],
    ["Probability may be only an enrichment topic at Class 6; check with the official syllabus (not all versions include it)."],
    prereq=["fslc-maths-fractions"])

# ---- algebra introduction
assert 15 - 7 == 8 and 21 // 3 == 7 and (17 - 5) // 2 == 6 and 5 * 4 == 20 and (11 + 4) // 3 == 5 and 3 * 4 + 2 == 14
assert (2100 - 500) // 4 == 400 and 3 * 400 == 1200
fig_bal = shapes([LINE(60, 90, 340, 90, width=4), POLY([200, 90, 175, 160, 225, 160], fill="lightyellow"),
                  RECT(70, 90, 100, 14, fill="lightblue"), RECT(230, 90, 100, 14, fill="lightgreen"),
                  T(120, 70, "x + 7", 18, bold=True), T(280, 70, "15", 18, bold=True),
                  T(200, 195, "Both sides are equal: take 7 from each side, x = 8", 13)], 400, 215)
build(ch, "algebra-intro", "Introduction to algebra: simple equations", 30,
    ["Use a letter for an unknown number.", "Solve one-step and two-step equations by doing the same to both sides.", "Write and solve an equation from a word problem."],
    [("key", "definition", "Letters for numbers", "In algebra a **letter** stands for a number we do not know yet. **3x** means 3 × x. An **equation** says that two things are equal, for example x + 7 = 15. To **solve** it we find the value of x that makes it true. To check, put the answer back in the equation."),
     ("fig", fig_bal, "An equation is like a balance: do the same on both sides.", "A balance with a blue pan holding x plus 7 and a green pan holding 15. Text says to take 7 from each side, so x is 8."),
     ("key", "methode", "Solving an equation", "Do the **opposite** operation, on **both** sides, to get x alone. x + 7 = 15: subtract 7 from both sides, x = 8. 3x = 21: divide both sides by 3, x = 7. x ÷ 4 = 5: multiply both sides by 4, x = 20. For 2x + 5 = 17: first subtract 5 (2x = 12), then divide by 2 (x = 6)."),
     ("key", "pieges", "Common mistakes", "- Changing only **one** side of the equation.\n- Using the **same** operation instead of the opposite: for x + 7 = 15 writing x = 15 + 7.\n- In a two-step equation, dividing first and forgetting to divide **everything**."),
     ("ex", "Example 1", "Solve 2x + 5 = 17.", ["Subtract 5 from both sides: 2x = 17 − 5 = 12.", "Divide both sides by 2: x = 6.", "Check: 2 × 6 + 5 = 17. It is correct."], "x = 6."),
     ("ex", "Example 2", "I think of a number. I multiply it by 3 and subtract 4. The answer is 11. What is my number?", ["Let the number be x. Then 3x − 4 = 11.", "Add 4 to both sides: 3x = 15.", "Divide by 3: x = 5."], "The number is 5.")],
    [N("application", "Solve x + 7 = 15.", 8, "Subtract 7 from both sides: x = 15 − 7 = 8."),
     N("application", "Solve 3x = 21.", 7, "Divide both sides by 3: x = 7."),
     N("application", "Find the value of 3a + 2 when a = 4.", 14, "3 × 4 + 2 = 12 + 2 = 14."),
     N("approfondissement", "Solve x ÷ 4 = 5.", 20, "Multiply both sides by 4: x = 20."),
     N("approfondissement", "I think of a number, multiply it by 3 and subtract 4. The answer is 11. What is the number?", 5, "3x − 4 = 11, so 3x = 15 and x = 5."),
     P("Mr Che buys 4 books at x FCFA each and a school bag for 500 FCFA. He pays 2,100 FCFA in all.",
       [PM("Which equation shows this?", "4x + 500 = 2100", ["4 + x + 500 = 2100", "4x − 500 = 2100", "x + 500 = 2100"], "4 books cost 4x. Adding the bag: 4x + 500 = 2,100.", points=2),
        PN("Solve it to find x (the price of one book, in FCFA).", 400, "4x = 2,100 − 500 = 1,600. x = 1,600 ÷ 4 = 400.", unit="FCFA", points=2),
        PN("What do 3 of these books cost?", 1200, "3 × 400 = 1,200 FCFA.", unit="FCFA")])],
    [("In algebra, 5y means...", "5 × y", ["5 + y", "5 − y", "50 + y"], "A number next to a letter means multiply."),
     ("Solve x − 4 = 10.", "x = 14", ["x = 6", "x = 40", "x = 2.5"], "Add 4 to both sides: x = 14."),
     ("Solve 5x = 35.", "x = 7", ["x = 30", "x = 175", "x = 40"], "Divide both sides by 5."),
     ("If 2x + 1 = 9, then x is...", "4", ["8", "5", "10"], "2x = 8, so x = 4."),
     ("To solve x + 6 = 10 we...", "subtract 6 from both sides", ["add 6 to both sides", "multiply both sides by 6", "divide both sides by 6"], "The opposite of + 6 is − 6.")],
    ["Only equations with one unknown and whole-number solutions; check the syllabus level (algebra may be limited to 'finding the missing number')."],
    prereq=["fslc-maths-whole-numbers"])

# =========================================================== second mock paper (20 marks)
LM = ch.lessons[-1]
assert rnd(4865129, 100000) == 4900000 and math.gcd(36, 48) == 12 and math.lcm(6, 9) == 18 and 48 // 4 == 12
assert sum([6, 9, 12, 15, 18]) / 5 == 12 and 180 - 38 - 79 == 63 and A(4) == 50.24 and (19 - 3) // 2 == 8 and 150 / 2.5 == 60
assert 20 * 2000 == 40000 and 20 * 2300 == 46000 and 6000 * 100 / 40000 == 15
assert 60 * 40 * 50 == 120000 and 120 / 8 == 15 and 8 * 6 / 120 == 0.4
a = [add(LM, N("application", "Round 4,865,129 to the nearest 100,000.", 4900000, "The ten-thousands digit is 6, so round up: 4,900,000."), True),
     add(LM, M("application", "What is the HCF of 36 and 48?", "12", ["6", "24", "144"], "Common factors: 1, 2, 3, 4, 6, 12. The highest is 12."), True),
     add(LM, N("application", "Find the LCM of 6 and 9.", 18, "Multiples of 9: 9, 18. 18 is also a multiple of 6."), True),
     add(LM, N("application", "Share 48 mangoes between Ada and Bih in the ratio 1 : 3. How many mangoes does Ada (the smaller share) get?", 12, "4 parts: 48 ÷ 4 = 12 for one part. Ada gets 12."), True),
     add(LM, N("application", "Find the mean of 6, 9, 12, 15 and 18.", 12, "Total 60 ÷ 5 = 12."), True),
     add(LM, N("application", "Two angles of a triangle are 38° and 79°. Find the third angle.", 63, "180 − 38 − 79 = 63.", unit="°"), True),
     add(LM, N("application", "Find the area of a circle of radius 4 cm (π = 3.14).", 50.24, "3.14 × 4 × 4 = 50.24 cm².", tol=0.01, unit="cm²"), True),
     add(LM, N("application", "Solve 2x + 3 = 19.", 8, "2x = 16, so x = 8."), True)]
b1 = add(LM, P("A shopkeeper in Limbe buys 20 crates of eggs at 2,000 FCFA a crate. He sells all of them at 2,300 FCFA a crate.",
               [PN("How much did he pay for the 20 crates?", 40000, "20 × 2,000 = 40,000 FCFA.", unit="FCFA", points=2),
                PN("What is his total profit?", 6000, "He receives 20 × 2,300 = 46,000 FCFA. Profit = 46,000 − 40,000 = 6,000 FCFA.", unit="FCFA", points=2),
                PN("What is his percentage profit?", 15, "6,000 ÷ 40,000 × 100 = 15%.", unit="%", points=2)]), True)
b2 = add(LM, P("A rectangular tank is 60 cm long, 40 cm wide and 50 cm high. A tap fills it at 8 litres per minute.",
               [PN("What is the volume of the tank in litres?", 120, "60 × 40 × 50 = 120,000 cm³ = 120 litres.", unit="litres", points=2),
                PN("How many minutes does the tap take to fill it?", 15, "120 ÷ 8 = 15 minutes.", unit="minutes", points=2),
                PN("What percentage of the tank is full after 6 minutes?", 40, "In 6 minutes: 6 × 8 = 48 litres. 48 ÷ 120 × 100 = 40%.", unit="%", points=2)]), True)
p.mock("2", "FSLC mock paper 2 — Mathematics", 90,
       "Answer ALL the questions. Show your working for the problems in Section B. Use π = 3.14. Total: 20 marks. Practice paper written in the style of the FSLC; format, duration and marking are to be checked against the official texts.",
       [("Section A — Short questions (8 marks)", a), ("Section B — Problems (12 marks)", [b1, b2])])
p.write()
