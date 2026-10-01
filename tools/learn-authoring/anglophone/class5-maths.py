import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from p45_kit import *

TAG["v"] = "Class 5 Maths"
p = Pack("class5-maths", "Mathematics — Class 5", level="Class 5", subject="maths", cursus="primary",
         description="Class 5 mathematics for the English-speaking primary subsystem: large numbers, factors and multiples, fractions, decimals, percentages, ratio, measures, area and volume, angles and triangles, averages, simple interest, data and probability. Draft lessons with worked examples, exercises and self-checks.",
         programRef="MINEDUB primary school curriculum (English-speaking subsystem), Level III (Class 5) — Mathematics; to be checked against the official syllabus")
REF = p.programRef
gcd = math.gcd
lcm = lambda a, b: a * b // gcd(a, b)

# ======================================================================= 1. numbers
ch = p.chapter("numbers", "Numbers, factors and multiples", REF)

fig = table([["HTh", "TTh", "Th", "H", "T", "U"], ["3", "4", "5", "6", "7", "8"]], [60] * 6, rh=34, size=18)
fig2 = table([["M", "HTh", "TTh", "Th", "H", "T", "U"], ["2", "0", "5", "0", "0", "0", "0"]], [54] * 7, rh=34, size=16)
assert 2 * 1000000 + 50000 == 2050000 and round(345678, -3) == 346000
build(ch, "large-numbers", "Large numbers: hundreds of thousands and millions", 30,
      ["Read and write numbers up to millions.", "Say the place value of any digit.", "Round large numbers and compare them."],
      [("key", "definition", "Places and periods", "After the thousands come the **hundred thousands (HTh)** and then the **millions (M)**. 1,000,000 is **one million**: ten hundred thousands. We group digits in threes from the right: 2,050,000 is **two million and fifty thousand**. In 345,678 the 3 is worth 300,000."),
       ("fig", fig, "345,678 in a place-value table.", "A table with columns HTh, TTh, Th, H, T, U and the digits 3, 4, 5, 6, 7, 8."),
       ("fig", fig2, "2,050,000 in a place-value table.", "A table with columns M, HTh, TTh, Th, H, T, U and the digits 2, 0, 5, 0, 0, 0, 0."),
       ("key", "methode", "Rounding and comparing", "To compare, count the digits first, then compare from the left. To round to the nearest 1,000, look at the **hundreds digit**: 5 or more rounds up. So 345,678 is 346,000 to the nearest thousand and 350,000 to the nearest ten thousand."),
       ("key", "pieges", "Common mistakes", "- Writing too many or too few zeros: **one million** has six zeros.\n- Writing 'two million fifty thousand' as 2,50,000 instead of 2,050,000.\n- Changing digits to the left when rounding.\n- Thinking that 99,999 is bigger than 100,000.")],
      [("ex", "Example 1", "Write 'four million, three hundred and six thousand and twelve' in figures.", ["Millions: 4. Thousands: 306. Hundreds, tens, units: 012.", "Write them together: 4,306,012."], "4,306,012", None),
       ("ex", "Example 2", "Round 345,678 to the nearest 10,000.", ["The ten-thousands digit is 4; the next digit (thousands) is 5.", "5 rounds up, so 40,000 becomes 50,000: the answer is 350,000."], "350,000", None)],
      [M("application", "What is the value of 6 in 462,310?", "60,000", ["6,000", "600,000", "6"], "6 is in the ten thousands place."),
       N("application", "Write in figures: 3 million and 5,000.", 3005000, "3,000,000 + 5,000 = 3,005,000."),
       M("application", "Which number is the biggest?", "1,000,000", ["999,999", "990,000", "100,000"], "One million is 1 more than 999,999."),
       N("approfondissement", "Round 4,567,890 to the nearest million.", 5000000, "The hundred-thousands digit is 5, so round up.", tol=0),
       TF("approfondissement", "There are 10 hundred thousands in one million.", True, "10 × 100,000 = 1,000,000."),
       P("A cocoa cooperative in the Centre Region produced 1,250,000 kg of cocoa in one year and 980,000 kg in the next year.",
         [PN("How many kg did it produce in the two years together?", 1250000 + 980000, "1,250,000 + 980,000 = 2,230,000.", unit="kg"),
          PN("How many kg more in the first year?", 1250000 - 980000, "1,250,000 − 980,000 = 270,000.", unit="kg"),
          PN("Round the first year's amount to the nearest million.", 1000000, "The hundred thousands digit is 2: round down.", points=2)])],
      [("How many zeros are in one million?", "6", ["3", "5", "7"], "1,000,000 has six zeros."),
       ("The value of 7 in 7,430,000 is...", "7,000,000", ["700,000", "70,000", "7,000"], "7 millions."),
       ("Round 76,500 to the nearest thousand.", "77,000", ["76,000", "76,500", "80,000"], "5 goes up."),
       ("Which is 2,000,000 + 30,000 + 400?", "2,030,400", ["2,300,400", "2,034,000", "230,400"], "Place each part."),
       ("Which number is the smallest?", "989,899", ["998,899", "999,998", "1,000,000"], "989,899 has the smallest ten-thousands digit among the six-digit numbers.")],
      ["Millions are named; billions are not covered."])

f24 = [d for d in range(1, 25) if 24 % d == 0]
assert f24 == [1, 2, 3, 4, 6, 8, 12, 24] and gcd(12, 18) == 6 and lcm(4, 6) == 12
fig = grid([("1 × 24", "pair"), ("2 × 12", "pair"), ("3 × 8", "pair"), ("4 × 6", "pair")], 4, 88, 62, gap=8, size=15)
primes_to_30 = [n for n in range(2, 31) if all(n % d for d in range(2, n))]
assert primes_to_30 == [2, 3, 5, 7, 11, 13, 17, 19, 23, 29]
build(ch, "factors-multiples", "Factors, multiples, primes, HCF and LCM", 35,
      ["List the factors and multiples of a number.", "Tell prime and composite numbers apart.", "Find the HCF and the LCM of two small numbers."],
      [("key", "definition", "Factors and multiples", "A **factor** of a number divides it exactly: the factors of 24 are 1, 2, 3, 4, 6, 8, 12 and 24. A **multiple** of a number is in its times table: multiples of 6 are 6, 12, 18, 24... A **prime number** has exactly two factors, 1 and itself (2, 3, 5, 7, 11, 13...). 1 is not prime. Other numbers above 1 are **composite**."),
       ("fig", fig, "The factor pairs of 24.", "Four boxes with the factor pairs 1 times 24, 2 times 12, 3 times 8 and 4 times 6."),
       ("key", "methode", "HCF and LCM", "The **HCF** (highest common factor) is the biggest number that divides both: factors of 12 are 1, 2, 3, 4, 6, 12 and of 18 are 1, 2, 3, 6, 9, 18, so HCF = 6. The **LCM** (lowest common multiple) is the smallest number in both times tables: multiples of 4 are 4, 8, 12 and of 6 are 6, 12, so LCM = 12."),
       ("key", "retenir", "Divisibility tests", "A number is divisible by **2** if it ends in 0, 2, 4, 6 or 8; by **5** if it ends in 0 or 5; by **10** if it ends in 0; by **3** if the sum of its digits is divisible by 3; by **9** if the sum of its digits is divisible by 9."),
       ("key", "pieges", "Common mistakes", "- Saying that 1 is a prime number, or that 2 is not prime (2 is the only even prime).\n- Mixing factors and multiples: factors are smaller or equal, multiples are bigger or equal.\n- Giving the HCF when the question asks for the LCM.")],
      [("ex", "Example 1", "Find the HCF and the LCM of 12 and 18.", ["Factors of 12: 1, 2, 3, 4, 6, 12. Factors of 18: 1, 2, 3, 6, 9, 18. The biggest common factor is 6.", "Multiples of 12: 12, 24, 36. Multiples of 18: 18, 36. The first common multiple is 36."], "HCF 6; LCM 36.", None),
       ("ex", "Example 2", "Is 51 a prime number?", ["Test 3: 5 + 1 = 6, which is divisible by 3, so 51 is divisible by 3.", "51 = 3 × 17, so it has more than two factors: it is not prime."], "No, 51 = 3 × 17.", None)],
      [M("application", "Which of these is a prime number?", "13", ["15", "21", "27"], "13 has only the factors 1 and 13."),
       N("application", "How many factors has 24?", len(f24), "1, 2, 3, 4, 6, 8, 12, 24."),
       N("application", "Find the LCM of 3 and 5.", lcm(3, 5), "Multiples of 3: 3,6,9,12,15; of 5: 5,10,15."),
       N("approfondissement", "Find the HCF of 20 and 30.", gcd(20, 30), "Common factors: 1, 2, 5, 10. The highest is 10."),
       TF("approfondissement", "If a number ends in 5 it is divisible by 5.", True, "Test for 5."),
       P("Two buses leave the Bamenda motor park together. Bus A leaves again every 4 hours and bus B every 6 hours.",
         [PN("After how many hours will both leave together again?", lcm(4, 6), "LCM of 4 and 6 is 12.", unit="hours"),
          PM("What kind of number is 12?", "a composite number", ["a prime number", "an odd number", "a decimal"], "12 has more than two factors."),
          PN("A teacher has 24 pencils and 36 erasers and wants equal sets with nothing left over. What is the biggest number of sets?", gcd(24, 36), "HCF of 24 and 36 is 12.", points=2)])],
      [("Which number is a factor of 20?", "5", ["8", "3", "30"], "20 ÷ 5 = 4."),
       ("The first multiple of 7 after 14 is...", "21", ["28", "15", "17"], "7 × 3 = 21."),
       ("Which number is divisible by 9?", "729", ["731", "728", "730"], "7 + 2 + 9 = 18, divisible by 9."),
       ("The LCM of 2 and 3 is...", "6", ["1", "5", "23"], "Smallest number in both times tables."),
       ("The only even prime number is...", "2", ["4", "1", "0"], "All other even numbers have the factor 2.")],
      ["Whether HCF/LCM are taught by listing or by prime factors at this level to be checked; listing is used here."])

assert 8 + 4 * 3 == 20 and (8 + 4) * 3 == 36 and 20 - 12 / 4 == 17
fig = boxes(["B  — Brackets first", "O  — Orders (powers) next", "D M — Divide and Multiply, left to right", "A S — Add and Subtract, left to right"], size=14)
build(ch, "order-of-operations", "Order of operations (BODMAS)", 25,
      ["Use brackets, multiplication, division, addition and subtraction in the right order.", "Solve mixed calculations.", "Write a calculation for a story."],
      [("key", "methode", "BODMAS", "When a calculation has more than one operation, work in this order: **B**rackets, **O**rders (powers like 2²), **D**ivision and **M**ultiplication (left to right), **A**ddition and **S**ubtraction (left to right). So 8 + 4 × 3 = 8 + 12 = 20, but (8 + 4) × 3 = 12 × 3 = 36."),
       ("fig", fig, "The order of operations.", "Four stacked boxes: brackets first, orders next, divide and multiply from left to right, add and subtract from left to right."),
       ("key", "retenir", "Same level: left to right", "Division and multiplication have the **same rank**, so we go from left to right: 24 ÷ 4 × 3 = 6 × 3 = 18. Addition and subtraction also have the same rank: 10 − 3 + 2 = 7 + 2 = 9."),
       ("key", "pieges", "Common mistakes", "- Working from left to right without thinking: 8 + 4 × 3 is **not** 36.\n- Ignoring the brackets.\n- Doing the addition before the division: 20 − 12 ÷ 4 = 20 − 3 = 17, not 2.")],
      [("ex", "Example 1", "Work out 20 − 12 ÷ 4.", ["Division first: 12 ÷ 4 = 3.", "Then subtract: 20 − 3 = 17."], "17", None),
       ("ex", "Example 2", "Work out (15 + 5) × 3 − 4.", ["Brackets first: 15 + 5 = 20.", "Multiply: 20 × 3 = 60. Subtract: 60 − 4 = 56."], "56", None)],
      [N("application", "Work out 6 + 4 × 5.", 6 + 4 * 5, "Multiply first: 20, then add 6 = 26."),
       N("application", "Work out (6 + 4) × 5.", (6 + 4) * 5, "Brackets first: 10 × 5 = 50."),
       M("application", "Work out 30 − 10 ÷ 2.", "25", ["10", "20", "15"], "10 ÷ 2 = 5, then 30 − 5 = 25."),
       N("approfondissement", "Work out 24 ÷ 4 × 3.", 24 // 4 * 3, "Left to right: 6 × 3 = 18."),
       TF("approfondissement", "2 + 3 × 4 gives the same answer as (2 + 3) × 4.", False, "2 + 12 = 14 but 5 × 4 = 20."),
       P("Mama Ako buys 3 packets of biscuits at 250 FCFA and 2 bottles of juice at 400 FCFA. She pays 2,000 FCFA.",
         [PN("Write and work out 3 × 250 + 2 × 400.", 3 * 250 + 2 * 400, "750 + 800 = 1,550.", unit="FCFA"),
          PN("Work out the change: 2,000 − (3 × 250 + 2 × 400).", 2000 - (3 * 250 + 2 * 400), "2,000 − 1,550 = 450.", unit="FCFA"),
          PM("Which expression shows the cost of 5 items if 3 cost 250 each and 2 cost 400 each?", "3 × 250 + 2 × 400", ["(3 + 2) × (250 + 400)", "3 + 250 × 2 + 400", "5 × 650"], "Each group is multiplied, then added.", points=2)])],
      [("Work out 5 + 2 × 3.", "11", ["21", "13", "30"], "Multiply first."),
       ("What does the B in BODMAS stand for?", "Brackets", ["Big", "Before", "Bottom"], "Brackets come first."),
       ("Work out 18 ÷ (6 − 3).", "6", ["3", "0", "9"], "6 − 3 = 3, then 18 ÷ 3."),
       ("Work out 12 − 4 + 2.", "10", ["6", "14", "8"], "Left to right."),
       ("Work out 3 + 12 ÷ 3.", "7", ["5", "15", "4"], "12 ÷ 3 = 4, then 3 + 4.")],
      [])

# ======================================================================= 2. fractions, decimals, percentages
ch = p.chapter("fractions", "Fractions, decimals and percentages", REF)

fig = fracbars([(2, 4, "1/2 = 2/4"), (1, 4, "+ 1/4"), (3, 4, "= 3/4")], fill="green")
build(ch, "fractions-ops", "Fractions: adding, subtracting and multiplying", 35,
      ["Change fractions to a common denominator.", "Add and subtract fractions and mixed numbers.", "Multiply a fraction by a whole number or a fraction."],
      [("key", "methode", "Unlike denominators", "To add or subtract fractions with **different denominators**, first change them to the **same denominator** (use the LCM). $\\frac{1}{2} + \\frac{1}{4}$: change $\\frac{1}{2}$ to $\\frac{2}{4}$, then $\\frac{2}{4} + \\frac{1}{4} = \\frac{3}{4}$. Add or subtract the numerators; keep the denominator."),
       ("fig", fig, "1/2 + 1/4 = 3/4.", "Three bars on quarters: 2 quarters shaded, then 1 quarter, then 3 quarters."),
       ("key", "methode", "Multiplying fractions", "To multiply fractions, **multiply the numerators** and **multiply the denominators**: $\\frac{2}{3} \\times \\frac{3}{5} = \\frac{6}{15} = \\frac{2}{5}$. To multiply a fraction by a whole number, multiply the numerator: $\\frac{3}{4} \\times 8 = \\frac{24}{4} = 6$. Always simplify the answer."),
       ("key", "retenir", "Mixed numbers", "An **improper fraction** has a numerator bigger than the denominator: $\\frac{7}{4} = 1\\frac{3}{4}$ (one and three quarters). To change $2\\frac{1}{3}$ to an improper fraction: 2 × 3 + 1 = 7, so $\\frac{7}{3}$."),
       ("key", "pieges", "Common mistakes", "- Adding both numerators and denominators: $\\frac{1}{2} + \\frac{1}{4}$ is not $\\frac{2}{6}$.\n- Forgetting to change the numerator when you change the denominator.\n- Not simplifying: $\\frac{6}{15}$ should be $\\frac{2}{5}$.")],
      [("ex", "Example 1", "Work out 2/3 + 1/6.", ["The LCM of 3 and 6 is 6: 2/3 = 4/6.", "4/6 + 1/6 = 5/6."], "5/6", None),
       ("ex", "Example 2", "Work out 3/4 of 20 and 2/5 × 3/4.", ["3/4 of 20: 20 ÷ 4 = 5, then 5 × 3 = 15.", "2/5 × 3/4 = 6/20 = 3/10."], "15 and 3/10", None)],
      [M("application", "Work out 1/2 + 1/4.", "3/4", ["2/6", "2/4", "1/6"], "1/2 = 2/4, 2/4 + 1/4 = 3/4."),
       M("application", "Work out 5/6 − 1/3.", "1/2", ["4/3", "4/6", "1/3"], "1/3 = 2/6, so 3/6 = 1/2."),
       N("application", "Work out 3/4 × 8.", 6, "24/4 = 6."),
       M("approfondissement", "Change 7/4 to a mixed number.", "1 3/4", ["1 1/4", "2 3/4", "7 1/4"], "7 = 4 + 3."),
       TF("approfondissement", "2/3 × 3/5 = 2/5.", True, "6/15 = 2/5."),
       P("A farmer in Dschang uses 1/2 of his land for maize, 1/4 for beans and the rest for vegetables. The land is 20 acres.",
         [PM("What fraction is used for maize and beans together?", "3/4", ["2/6", "2/4", "1/6"], "1/2 + 1/4 = 3/4."),
          PM("What fraction is left for vegetables?", "1/4", ["3/4", "1/2", "2/4"], "1 − 3/4 = 1/4."),
          PN("How many acres are used for vegetables?", 20 // 4, "1/4 of 20 = 5.", unit="acres", points=2)])],
      [("Which is equal to 1/3 + 1/6?", "1/2", ["2/9", "1/9", "2/6"], "2/6 + 1/6 = 3/6 = 1/2."),
       ("Simplify 6/9.", "2/3", ["3/2", "1/3", "3/6"], "Divide by 3."),
       ("What is 1/2 × 1/2?", "1/4", ["1", "1/2", "2/4"], "1 × 1 over 2 × 2."),
       ("The mixed number 1 1/2 equals...", "3/2", ["2/3", "1/2", "11/2"], "1 × 2 + 1 = 3 halves."),
       ("1 − 3/8 =", "5/8", ["3/8", "2/8", "4/8"], "8/8 − 3/8.")],
      ["Mixed numbers are used simply; check whether the curriculum requires multiplication of two fractions at Class 5."])

assert 0.5 + 0.25 == 0.75 and round(3.6 * 4, 2) == 14.4 and round(7.5 / 5, 2) == 1.5
fig = table([["Place", "Value", "Example 4.52"], ["ones", "1", "4"], ["tenths", "0.1", "5"], ["hundredths", "0.01", "2"]], [120, 90, 130], rh=30, size=14)
build(ch, "decimals-ops", "Decimals: place value and the four operations", 30,
      ["Read decimals to hundredths and thousandths.", "Add, subtract, multiply and divide decimals.", "Multiply and divide by 10, 100 and 1,000."],
      [("key", "definition", "Place value", "In 4.52 the 4 is **ones**, the 5 is **5 tenths** (0.5) and the 2 is **2 hundredths** (0.02). We can write $4.52 = 4 + \\frac{5}{10} + \\frac{2}{100}$. Add zeros at the end without changing the value: 3.5 = 3.50."),
       ("fig", fig, "Place value of 4.52.", "A table with places ones, tenths, hundredths, their values 1, 0.1 and 0.01, and the digits 4, 5 and 2 of the number 4.52."),
       ("key", "methode", "The four operations", "**Add and subtract**: line up the decimal points. **Multiply**: ignore the points, multiply, then put back as many decimal places as there are in both numbers: 3.6 × 4 = 14.4. **Divide** by a whole number: divide as usual and keep the point in the same place: 7.5 ÷ 5 = 1.5."),
       ("key", "retenir", "Times and divided by 10, 100, 1,000", "To multiply by **10**, move the point **one place right**: 2.35 × 10 = 23.5. By 100: two places right. To divide by 10, move the point one place **left**: 45 ÷ 10 = 4.5. By 100: two places left."),
       ("key", "pieges", "Common mistakes", "- Not lining up the points when adding: 4.5 + 0.25 is 4.75, not 0.70.\n- Thinking 0.45 is bigger than 0.5.\n- Moving the point the wrong way when multiplying or dividing by 10.\n- Forgetting the point in the answer.")],
      [("ex", "Example 1", "Work out 12.75 − 4.9.", ["Write 4.9 as 4.90 and line up the points.", "12.75 − 4.90 = 7.85."], "7.85", None),
       ("ex", "Example 2", "A rope costs 350 FCFA per metre. How much do 2.5 m cost?", ["Multiply: 350 × 2.5 = 350 × 2 + 350 × 0.5.", "700 + 175 = 875."], "875 FCFA", None)],
      [N("application", "Work out 3.45 + 2.8.", 6.25, "3.45 + 2.80 = 6.25.", tol=0.001),
       N("application", "Work out 2.35 × 10.", 23.5, "Move the point one place right.", tol=0.001),
       M("application", "Which is the biggest?", "0.5", ["0.45", "0.405", "0.045"], "0.5 = 0.500 is more than 0.450."),
       N("approfondissement", "Work out 6.4 ÷ 8.", 0.8, "8 × 0.8 = 6.4.", tol=0.001),
       TF("approfondissement", "3.5 and 3.50 have the same value.", True, "A zero at the end does not change the value."),
       P("Mr Eko buys 2.5 kg of rice at 600 FCFA per kg, and 1.5 litres of oil at 1,200 FCFA per litre.",
         [PN("Cost of the rice?", 2.5 * 600, "2.5 × 600 = 1,500.", unit="FCFA", tol=0.01),
          PN("Cost of the oil?", 1.5 * 1200, "1.5 × 1,200 = 1,800.", unit="FCFA", tol=0.01),
          PN("Total cost?", 2.5 * 600 + 1.5 * 1200, "1,500 + 1,800 = 3,300.", unit="FCFA", tol=0.01, points=2)])],
      [("In 6.07 the 7 is worth...", "7 hundredths", ["7 tenths", "7 ones", "70"], "Second digit after the point."),
       ("Work out 0.5 + 0.25.", "0.75", ["0.30", "0.55", "0.525"], "50 + 25 hundredths."),
       ("45 ÷ 100 =", "0.45", ["4.5", "4,500", "0.045"], "Move the point two places left."),
       ("Which is the smallest?", "0.09", ["0.1", "0.19", "0.9"], "0.09 has only 9 hundredths."),
       ("Work out 1.2 × 3.", "3.6", ["3.2", "36", "0.36"], "12 × 3 = 36 with one decimal place.")],
      [])

fig = fracbars([(1, 4, "25% = 1/4"), (2, 4, "50% = 1/2"), (3, 4, "75% = 3/4"), (4, 4, "100% = whole")], fill="orange")
build(ch, "percentages", "Percentages", 30,
      ["Say what per cent means.", "Change fractions and decimals to percentages and back.", "Find a percentage of an amount and work out a discount."],
      [("key", "definition", "Per cent", "**Per cent** means **out of 100**. The symbol is **%**. 25% means $\\frac{25}{100}$ = 0.25 = $\\frac{1}{4}$. 50% is a half. 100% is the whole. We use percentages for marks, discounts, interest and the share of a crowd."),
       ("fig", fig, "Common percentages as fractions.", "Four bars: 1 of 4 parts is 25%, 2 of 4 is 50%, 3 of 4 is 75% and all 4 is 100%."),
       ("key", "methode", "Percentage of an amount", "To find **10%** divide by 10. To find **50%** divide by 2. To find **25%** divide by 4. For other percentages build up: 15% = 10% + 5%. Or use a fraction: 20% of 150 = $\\frac{20}{100}$ × 150 = 30. To change a fraction to a percentage, make the denominator 100: $\\frac{3}{5} = \\frac{60}{100} = 60\\%$."),
       ("key", "pieges", "Common mistakes", "- Writing 25% as 25 and not 25/100 or 0.25.\n- Thinking that 100% of anything is more than the whole: it is the whole.\n- A **discount** is taken **off** the price: the new price is smaller.\n- Mixing 10% and 1/10 with 1%: 1% is 1/100.")],
      [("ex", "Example 1", "A shirt costs 8,000 FCFA. The shop gives a 25% discount. What is the new price?", ["25% = 1/4. 8,000 ÷ 4 = 2,000 is the discount.", "New price: 8,000 − 2,000 = 6,000 FCFA."], "6,000 FCFA", None),
       ("ex", "Example 2", "Anne scored 18 out of 20. What is her mark as a percentage?", ["18/20 = 90/100 (multiply by 5).", "So her mark is 90%."], "90%", None)],
      [N("application", "Find 10% of 500.", 50, "500 ÷ 10 = 50."),
       M("application", "Which fraction is the same as 50%?", "1/2", ["1/5", "2/5", "5/2"], "50 out of 100."),
       N("application", "Find 25% of 80.", 20, "80 ÷ 4."),
       N("approfondissement", "Find 15% of 200.", 30, "10% = 20, 5% = 10; total 30."),
       TF("approfondissement", "0.75 is the same as 75%.", True, "75/100 = 0.75."),
       P("A trader in Bafoussam sells a bag of maize for 12,000 FCFA. During the market day he gives a 10% discount.",
         [PN("How much is the discount?", 1200, "10% of 12,000 = 1,200.", unit="FCFA"),
          PN("What is the price after the discount?", 12000 - 1200, "12,000 − 1,200 = 10,800.", unit="FCFA"),
          PM("What percentage of the price must a customer pay?", "90%", ["10%", "110%", "9%"], "100% − 10% = 90%.", points=2)])],
      [("Per cent means...", "out of 100", ["out of 10", "out of 1,000", "out of 50"], "From the Latin per centum."),
       ("10% of 360 is...", "36", ["3.6", "360", "100"], "Divide by 10."),
       ("1/4 as a percentage is...", "25%", ["14%", "4%", "40%"], "25/100."),
       ("A price of 2,000 FCFA reduced by 50% becomes...", "1,000 FCFA", ["500 FCFA", "2,500 FCFA", "1,500 FCFA"], "Half of 2,000."),
       ("A mark of 15 out of 20 is...", "75%", ["15%", "20%", "85%"], "15/20 = 75/100.")],
      ["Discounts are only a first application; no compound percentage questions."])

a_, b_ = 3, 5
assert 40 * 3 // 8 == 15
fig = shapes([CIRCLE(30 + 26 * k, 50, 11, fill="red") for k in range(3)] + [CIRCLE(30 + 26 * (3 + k), 50, 11, fill="blue") for k in range(5)] +
             [T(70, 100, "3 red", 14, bold=True, color="red"), T(160, 100, "5 blue", 14, bold=True, color="blue"), T(300, 55, "red : blue = 3 : 5", 15, bold=True)], 400, 120)
build(ch, "ratio-proportion", "Ratio and proportion", 30,
      ["Write a ratio and simplify it.", "Share an amount in a given ratio.", "Solve simple direct proportion problems."],
      [("key", "definition", "Ratio", "A **ratio** compares two amounts of the same kind. If a bag has 3 red and 5 blue balls, the ratio of red to blue is **3 : 5** (read '3 to 5'). We can **simplify** a ratio like a fraction: 6 : 9 = 2 : 3 (divide both by 3). Order matters: 3 : 5 is not 5 : 3."),
       ("fig", fig, "Three red counters to five blue counters is 3 : 5.", "Eight circles in a row: three red and five blue, with the label red to blue equals 3 to 5."),
       ("key", "methode", "Sharing in a ratio", "To share 40,000 FCFA in the ratio 3 : 5: add the parts 3 + 5 = 8. One part = 40,000 ÷ 8 = 5,000. Then 3 parts = 15,000 and 5 parts = 25,000. **Check**: 15,000 + 25,000 = 40,000."),
       ("key", "methode", "Direct proportion", "If 4 loaves cost 1,000 FCFA, then 8 loaves cost twice as much. Use the **unit method**: find the price of 1, then multiply. 1,000 ÷ 4 = 250 FCFA for 1 loaf, so 7 loaves cost 7 × 250 = 1,750 FCFA."),
       ("key", "pieges", "Common mistakes", "- Writing the ratio in the wrong order.\n- Dividing by one of the numbers of the ratio: first **add the parts**, then divide the total by that number.\n- Mixing units: compare 50 cm to 1 m as 50 : 100 = 1 : 2, not 50 : 1.\n- Forgetting to check that the shares add to the total.")],
      [("ex", "Example 1", "Share 40,000 FCFA between Ako and Bih in the ratio 3 : 5.", ["Number of parts: 3 + 5 = 8. One part: 40,000 ÷ 8 = 5,000.", "Ako: 3 × 5,000 = 15,000. Bih: 5 × 5,000 = 25,000."], "Ako 15,000 FCFA; Bih 25,000 FCFA.", None),
       ("ex", "Example 2", "5 kg of rice cost 3,000 FCFA. How much do 8 kg cost?", ["Price of 1 kg: 3,000 ÷ 5 = 600 FCFA.", "Price of 8 kg: 8 × 600 = 4,800 FCFA."], "4,800 FCFA", None)],
      [M("application", "Simplify the ratio 6 : 9.", "2 : 3", ["3 : 2", "1 : 3", "6 : 3"], "Divide both by 3."),
       N("application", "Share 60 sweets in the ratio 1 : 2. How many does the bigger share get?", 40, "3 parts: 20 each; 2 parts = 40."),
       N("application", "3 pens cost 450 FCFA. What is the cost of 1 pen?", 150, "450 ÷ 3 = 150.", unit="FCFA"),
       N("approfondissement", "Share 90,000 FCFA in the ratio 4 : 5. How much is the bigger share?", 90000 // 9 * 5, "9 parts of 10,000; 5 parts = 50,000.", unit="FCFA"),
       TF("approfondissement", "The ratio 4 : 6 is equal to 2 : 3.", True, "Divide both by 2."),
       P("In a class in Kumba there are 12 girls and 18 boys.",
         [PM("What is the ratio of girls to boys in its simplest form?", "2 : 3", ["3 : 2", "12 : 18 cannot be simplified", "1 : 2"], "Divide by 6."),
          PN("How many pupils are in the class?", 30, "12 + 18 = 30."),
          PN("In another class with the same ratio there are 20 girls. How many boys?", 30, "2 : 3 = 20 : 30.", points=2)])],
      [("The ratio of 2 oranges to 6 oranges is...", "1 : 3", ["3 : 1", "2 : 6 cannot be simplified", "1 : 6"], "Divide by 2."),
       ("To share 100 in the ratio 1 : 4 we first add...", "1 + 4 = 5", ["1 × 4", "4 − 1", "100 + 5"], "There are 5 parts."),
       ("If 2 books cost 1,000 FCFA, 6 books cost...", "3,000 FCFA", ["2,000 FCFA", "6,000 FCFA", "1,500 FCFA"], "3 times as many."),
       ("5 : 10 in simplest form is...", "1 : 2", ["2 : 1", "5 : 2", "1 : 10"], "Divide by 5."),
       ("The ratio of 50 cm to 1 m is...", "1 : 2", ["50 : 1", "2 : 1", "1 : 50"], "1 m = 100 cm.")],
      [])

# ======================================================================= 3. measures and geometry
ch = p.chapter("measures", "Measures, area and volume", REF)

fig = table([["Quantity", "Conversion"], ["Length", "1 km = 1,000 m; 1 m = 100 cm"], ["Mass", "1 kg = 1,000 g; 1 t = 1,000 kg"], ["Capacity", "1 l = 1,000 ml"], ["Time", "1 h = 60 min; 1 min = 60 s"]], [90, 250], rh=28, size=13)
assert 3.5 * 1000 == 3500 and 450 / 1000 == 0.45 and 2 * 60 + 15 == 135
build(ch, "conversions", "Measures: converting units", 30,
      ["Convert between units of length, mass, capacity and time.", "Use decimals in measures.", "Solve problems with measures."],
      [("key", "definition", "Conversion table", "Learn the links between the units: **length**: 1 km = 1,000 m, 1 m = 100 cm, 1 cm = 10 mm. **Mass**: 1 t = 1,000 kg, 1 kg = 1,000 g. **Capacity**: 1 l = 1,000 ml. **Time**: 1 h = 60 min, 1 min = 60 s. A decimal can help: 3 km 500 m = 3.5 km."),
       ("fig", fig, "Conversion table for length, mass, capacity and time.", "A table with four rows: length km m cm, mass t kg g, capacity litre and ml, time hour minute and second, with their conversion values."),
       ("key", "methode", "Which way?", "**Big unit to small unit: multiply** (3.5 km = 3.5 × 1,000 = 3,500 m). **Small unit to big unit: divide** (450 g = 450 ÷ 1,000 = 0.45 kg). For time use 60: 2 h 15 min = 2 × 60 + 15 = 135 min."),
       ("key", "pieges", "Common mistakes", "- Using 100 for time: there are 60 minutes in an hour.\n- Multiplying instead of dividing.\n- Adding measures in different units without converting.\n- Using the wrong unit in the answer (km instead of m).")],
      [("ex", "Example 1", "A farmer has 2.5 kg of beans and buys 750 g more. How many kg in all?", ["Convert 750 g to kg: 750 ÷ 1,000 = 0.75 kg.", "2.5 + 0.75 = 3.25 kg."], "3.25 kg", None),
       ("ex", "Example 2", "How many minutes in 2 hours 15 minutes?", ["2 hours = 2 × 60 = 120 minutes.", "120 + 15 = 135 minutes."], "135 minutes", None)],
      [N("application", "Change 3.5 km into metres.", 3500, "3.5 × 1,000.", unit="m"),
       N("application", "Change 450 g into kg.", 0.45, "450 ÷ 1,000.", tol=0.001, unit="kg"),
       M("application", "Which is the biggest?", "2 km", ["1,500 m", "900 m", "1,999 m"], "2 km = 2,000 m."),
       N("approfondissement", "A tank of 20 litres is filled with bottles of 250 ml. How many bottles are needed?", 20000 // 250, "20 l = 20,000 ml; 20,000 ÷ 250 = 80."),
       TF("approfondissement", "3 hours 30 minutes is 210 minutes.", True, "3 × 60 + 30 = 210."),
       P("A lorry carries 3.5 tonnes of cocoa from Kumba to Douala. It unloads 1,250 kg at a first store.",
         [PN("How many kg did the lorry carry at first?", 3500, "3.5 × 1,000 = 3,500 kg.", unit="kg"),
          PN("How many kg are left?", 3500 - 1250, "3,500 − 1,250 = 2,250.", unit="kg"),
          PN("How many tonnes is that?", 2.25, "2,250 ÷ 1,000 = 2.25.", tol=0.001, unit="t", points=2)])],
      [("1 tonne is...", "1,000 kg", ["100 kg", "10 kg", "10,000 kg"], "Tonne = 1,000 kg."),
       ("How many ml in 3 litres?", "3,000", ["300", "30", "30,000"], "3 × 1,000."),
       ("2 h 30 min in minutes is...", "150", ["230", "130", "90"], "120 + 30."),
       ("750 m in km is...", "0.75 km", ["7.5 km", "75 km", "0.075 km"], "750 ÷ 1,000."),
       ("To change a big unit to a small unit we...", "multiply", ["divide", "add", "subtract"], "More small units fit in a big unit.")],
      [])

fig = shapes([POLY([60, 130, 220, 130, 160, 40], fill="lightyellow", width=3), LINE(160, 40, 160, 130, dash=True, color="red", width=2), T(140, 150, "base 10 cm", 13, bold=True),
              T(168, 90, "height 6 cm", 13, anchor="start", color="red", bold=True), T(300, 100, "Area = ½ × 10 × 6", 14, bold=True)], 400, 170)
assert 10 * 6 / 2 == 30 and 8 * 5 == 40 and 20 * 12 - 6 * 5 == 210
build(ch, "area", "Area of rectangles, triangles and compound shapes", 35,
      ["Use the formulae for the area of a rectangle, a square and a triangle.", "Find the area of a compound shape by adding or subtracting.", "Choose the right unit (cm², m²)."],
      [("key", "formule", "Formulae", "**Rectangle**: area = length × width. **Square**: area = side × side. **Triangle**: area = ½ × base × height, where the **height is at right angles to the base**. **Parallelogram**: area = base × height."),
       ("fig", fig, "A triangle with base 10 cm and height 6 cm.", "A triangle with the base marked 10 centimetres and a dashed red line showing the height 6 centimetres. The area is a half of 10 times 6."),
       ("key", "methode", "Compound shapes", "To find the area of an L-shape, **split** it into rectangles and **add** the areas, or take a big rectangle and **subtract** the missing part. Write the unit: cm² or m². A triangle is half of a rectangle with the same base and height."),
       ("key", "pieges", "Common mistakes", "- Using the slanting side instead of the perpendicular height for a triangle.\n- Forgetting the ½.\n- Writing m instead of m² for area.\n- Mixing units: change cm to m before multiplying.")],
      [("ex", "Example 1", "Find the area of a triangle with base 10 cm and height 6 cm.", ["Area = ½ × base × height.", "½ × 10 × 6 = 30."], "30 cm²", fig),
       ("ex", "Example 2", "A rectangular plot measures 20 m by 12 m. A corner piece 6 m by 5 m is cut out. Find the area that is left.", ["Whole rectangle: 20 × 12 = 240 m². Cut-out: 6 × 5 = 30 m².", "240 − 30 = 210 m²."], "210 m²", None)],
      [N("application", "Area of a rectangle 9 m by 4 m.", 36, "9 × 4.", unit="m²"),
       N("application", "Area of a triangle: base 8 cm, height 5 cm.", 20, "½ × 8 × 5 = 20.", unit="cm²"),
       M("application", "The area of a square of side 7 cm is...", "49 cm²", ["28 cm", "14 cm²", "49 cm"], "7 × 7 = 49."),
       N("approfondissement", "A parallelogram has base 12 cm and height 5 cm. Area?", 60, "12 × 5.", unit="cm²"),
       TF("approfondissement", "A triangle with the same base and height as a rectangle has half its area.", True, "That is why we use ½."),
       P("A school in Bamenda has a rectangular playground 30 m by 20 m. A triangular flower bed with base 8 m and height 5 m is in one corner.",
         [PN("Area of the playground?", 600, "30 × 20 = 600.", unit="m²"),
          PN("Area of the flower bed?", 20, "½ × 8 × 5 = 20.", unit="m²"),
          PN("Area left for playing?", 580, "600 − 20 = 580.", unit="m²", points=2)])],
      [("Area of a triangle is...", "½ × base × height", ["base × height", "base + height", "2 × base × height"], "Half of a rectangle."),
       ("Area of a 6 m by 3 m rectangle is...", "18 m²", ["18 m", "9 m²", "24 m²"], "6 × 3."),
       ("The height of a triangle is measured...", "at right angles to the base", ["along the slanting side", "round the shape", "in litres"], "Perpendicular height."),
       ("Area is measured in...", "square units", ["cubic units", "litres", "degrees"], "cm², m²."),
       ("A rectangle has area 48 cm² and width 6 cm. Its length is...", "8 cm", ["42 cm", "9 cm", "288 cm"], "48 ÷ 6.")],
      [])

fig = shapes(cuboid(60, 30, 140, 70, 40, fill="lightblue") + [T(130, 148, "length 8 cm", 13, bold=True), T(270, 100, "height 4 cm", 13, anchor="start", bold=True), T(268, 40, "width 5 cm", 13, anchor="start", bold=True),
                                                         T(300, 160, "V = 8 × 5 × 4", 14, bold=True)], 400, 175)
assert 8 * 5 * 4 == 160 and 2 * 1 * 1 == 2
build(ch, "volume", "Volume and capacity of cuboids", 30,
      ["Say what volume is and use cm³ and m³.", "Find the volume of a cuboid and a cube.", "Link volume and capacity (1,000 cm³ = 1 litre)."],
      [("key", "definition", "Volume", "**Volume** is the amount of space a solid takes up. We measure it in **cubic units**: cm³ and m³. A cube of side 1 cm has a volume of 1 cm³. For a **cuboid**: volume = length × width × height. For a **cube** of side $s$: volume = $s \\times s \\times s$."),
       ("fig", fig, "A cuboid 8 cm long, 5 cm wide and 4 cm high.", "A box drawn in 3-D with the length 8 cm, the width 5 cm and the height 4 cm marked. The volume is 8 times 5 times 4."),
       ("key", "retenir", "Capacity", "**1,000 cm³ = 1 litre** and 1 cm³ = 1 ml. So a tank of volume 20,000 cm³ holds 20 litres. A container of 1 m³ holds 1,000 litres. Volume is about the **space**, capacity is about how much a container **holds**."),
       ("key", "pieges", "Common mistakes", "- Adding the three sides instead of multiplying.\n- Writing the volume in cm² (that is for area).\n- Mixing units: if the sides are in cm the volume is in cm³.\n- Forgetting that 1 litre is 1,000 cm³, not 100.")],
      [("ex", "Example 1", "Find the volume of a box 8 cm long, 5 cm wide and 4 cm high.", ["Volume = length × width × height.", "8 × 5 × 4 = 160."], "160 cm³", fig),
       ("ex", "Example 2", "A tank is 50 cm long, 40 cm wide and 30 cm high. How many litres of water does it hold when full?", ["Volume = 50 × 40 × 30 = 60,000 cm³.", "1,000 cm³ = 1 litre, so 60,000 ÷ 1,000 = 60 litres."], "60 litres", None)],
      [N("application", "Volume of a cube with side 3 cm.", 27, "3 × 3 × 3.", unit="cm³"),
       N("application", "Volume of a cuboid 10 cm × 4 cm × 2 cm.", 80, "10 × 4 × 2.", unit="cm³"),
       M("application", "Which unit is used for volume?", "cm³", ["cm²", "cm", "kg"], "Cubic units."),
       N("approfondissement", "A tank has a volume of 5,000 cm³. How many litres is that?", 5, "5,000 ÷ 1,000.", unit="l"),
       TF("approfondissement", "A cube with side 2 cm has a volume of 8 cm³.", True, "2 × 2 × 2 = 8."),
       P("A water tank in a house in Buea is a cuboid 100 cm long, 50 cm wide and 60 cm high.",
         [PN("Volume of the tank?", 100 * 50 * 60, "100 × 50 × 60 = 300,000.", unit="cm³"),
          PN("How many litres can it hold?", 300, "300,000 ÷ 1,000.", unit="litres"),
          PN("The tank is filled with 20-litre buckets. How many full buckets fill it?", 15, "300 ÷ 20 = 15.", points=2)])],
      [("The volume of a cuboid is...", "length × width × height", ["length + width + height", "2 × (length + width)", "base × height"], "Multiply the three sides."),
       ("1 litre is equal to...", "1,000 cm³", ["100 cm³", "10 cm³", "1 cm³"], "1,000 cm³ = 1 l."),
       ("A cube with side 5 cm has volume...", "125 cm³", ["15 cm³", "25 cm³", "625 cm³"], "5 × 5 × 5."),
       ("Which is a unit of volume?", "m³", ["m²", "m", "kg"], "Cubic metre."),
       ("A box of volume 2,000 cm³ holds...", "2 litres", ["20 litres", "0.2 litres", "200 litres"], "2,000 ÷ 1,000.")],
      [])

fig = shapes([POLY([60, 150, 300, 150, 180, 30], fill="lightgreen", width=3), T(100, 140, "50°", 13, bold=True), T(255, 140, "60°", 13, bold=True), T(180, 70, "70°", 13, bold=True), T(180, 175, "50° + 60° + 70° = 180°", 14, bold=True)], 400, 195)
assert 50 + 60 + 70 == 180 and 180 - 50 - 60 == 70
build(ch, "triangles-angles", "Angles and triangles", 30,
      ["Measure and estimate angles.", "Name triangles by their sides and angles.", "Use the fact that the angles of a triangle add up to 180°."],
      [("key", "definition", "Triangles", "By **sides**: an **equilateral** triangle has 3 equal sides; an **isosceles** triangle has 2 equal sides; a **scalene** triangle has no equal sides. By **angles**: a **right-angled** triangle has one angle of 90°; an **acute-angled** triangle has all angles less than 90°; an **obtuse-angled** triangle has one angle more than 90°."),
       ("fig", fig, "The angles of a triangle add up to 180°.", "A triangle with angles 50, 60 and 70 degrees. Under it is written 50 plus 60 plus 70 equals 180 degrees."),
       ("key", "propriete", "Angle sum", "The three angles of **any** triangle add up to **180°**. If two angles are 50° and 60°, the third is 180° − 50° − 60° = 70°. Angles on a straight line add up to 180°, and angles all round a point add up to 360°. In an isosceles triangle the two angles at the base are equal."),
       ("key", "pieges", "Common mistakes", "- Using 360° for the angles of a triangle: that is for a full turn (and for a four-sided shape).\n- Subtracting only one angle from 180°.\n- Reading the wrong scale on a protractor (check if the angle is acute or obtuse).")],
      [("ex", "Example 1", "Two angles of a triangle are 50° and 60°. Find the third.", ["Add the two angles: 50 + 60 = 110.", "Subtract from 180: 180 − 110 = 70."], "70°", fig),
       ("ex", "Example 2", "An isosceles triangle has a top angle of 40°. Find the two equal base angles.", ["Angles left for the base: 180 − 40 = 140.", "The base angles are equal: 140 ÷ 2 = 70."], "70° each", None)],
      [N("application", "A triangle has angles 90° and 35°. Find the third angle.", 55, "180 − 90 − 35 = 55.", unit="°"),
       M("application", "A triangle with 3 equal sides is...", "equilateral", ["scalene", "isosceles", "right-angled"], "Equi = equal."),
       N("application", "The angles of a triangle add up to how many degrees?", 180, "Always 180°.", unit="°"),
       N("approfondissement", "Each angle of an equilateral triangle is how many degrees?", 60, "180 ÷ 3.", unit="°"),
       TF("approfondissement", "A triangle can have two right angles.", False, "90 + 90 = 180, leaving 0° for the third."),
       P("Neba draws a triangle with angles 45°, 45° and a third angle.",
         [PN("Find the third angle.", 90, "180 − 45 − 45 = 90.", unit="°"),
          PM("What kind of triangle is it by its angles?", "right-angled", ["acute-angled", "obtuse-angled", "straight"], "One angle is 90°."),
          PM("What kind of triangle is it by its sides?", "isosceles", ["equilateral", "scalene", "square"], "Two angles are equal, so two sides are equal.", points=2)])],
      [("The sum of the angles in a triangle is...", "180°", ["90°", "360°", "100°"], "Always 180°."),
       ("Angles 60°, 60° and 60° belong to a(n)...", "equilateral triangle", ["right-angled triangle", "scalene triangle", "obtuse triangle"], "All equal."),
       ("A triangle with an angle of 120° is...", "obtuse-angled", ["acute-angled", "right-angled", "equilateral"], "More than 90°."),
       ("Two angles are 30° and 70°. The third is...", "80°", ["100°", "70°", "60°"], "180 − 100 = 80."),
       ("Angles round a point add up to...", "360°", ["180°", "90°", "720°"], "A full turn.")],
      ["Constructing angles with a protractor is not covered (needs a physical protractor and a teacher)."])

# ======================================================================= 4. data and money
ch = p.chapter("data-money", "Data, probability and money", REF)

vals = [("Ako", 12), ("Bih", 16), ("Che", 9), ("Dina", 14)]
mean = sum(v for _, v in vals) / len(vals)
assert mean == 12.75 and sorted([3, 5, 7, 9, 11])[2] == 7
fig = safe_bars(vals, unit="Marks out of 20", w=400, h=240)
build(ch, "averages", "Averages: mean, median, mode and range", 30,
      ["Calculate the mean (average) of a set of numbers.", "Find the median, the mode and the range.", "Use the mean to solve problems."],
      [("key", "definition", "Mean", "The **mean** (or average) = **total ÷ number of items**. The marks 12, 16, 9 and 14 add up to 51; there are 4 marks; the mean is 51 ÷ 4 = 12.75. The mean can be a decimal, and it need not be one of the numbers."),
       ("fig", fig, "The marks of four pupils out of 20.", "A bar chart: Ako 12, Bih 16, Che 9, Dina 14 marks out of 20."),
       ("key", "definition", "Median, mode and range", "Put the numbers in **order**. The **median** is the **middle** number (for 3, 5, 7, 9, 11 it is 7; with two middle numbers, take the number halfway between). The **mode** is the number that appears **most often**. The **range** is the **biggest minus the smallest**: for 9, 12, 14, 15 it is 15 − 9 = 6."),
       ("key", "pieges", "Common mistakes", "- Dividing by the wrong number: divide by the **number of items**, not by the biggest value.\n- Not putting the numbers in order before finding the median.\n- Saying that there is no mode when two numbers tie (both are modes).\n- Mixing range and mean.")],
      [("ex", "Example 1", "Find the mean mark of the four pupils: 12, 16, 9, 14.", ["Add: 12 + 16 + 9 + 14 = 51.", "Divide by 4: 51 ÷ 4 = 12.75."], "12.75", fig),
       ("ex", "Example 2", "Find the median, mode and range of 4, 7, 4, 9, 6, 4, 8.", ["Order: 4, 4, 4, 6, 7, 8, 9. The middle (4th) number is 6. The number 4 appears 3 times.", "Range = 9 − 4 = 5."], "Median 6; mode 4; range 5.", None)],
      [N("application", "Find the mean of 6, 8 and 10.", 8, "24 ÷ 3 = 8."),
       N("application", "Find the range of 3, 11, 7, 5.", 8, "11 − 3 = 8."),
       M("application", "The mode of 2, 3, 3, 5, 3, 7 is...", "3", ["2", "5", "7"], "3 appears most often."),
       N("approfondissement", "The mean of 5 numbers is 12. What is their total?", 60, "5 × 12 = 60."),
       TF("approfondissement", "The median of 3, 5, 7, 9, 11 is 7.", True, "7 is the middle number."),
       P("A Class 5 teacher in Bamenda records the number of books read by 6 pupils: 4, 6, 3, 6, 8, 5.",
         [PN("What is the total?", 32, "4+6+3+6+8+5 = 32."),
          PN("What is the mean? (Give a decimal.)", 32 / 6, "32 ÷ 6 = 5.33...", tol=0.01),
          PM("What is the mode?", "6", ["3", "5", "8"], "6 appears twice.", points=2)])],
      [("The mean of 4, 6 and 8 is...", "6", ["8", "18", "4"], "18 ÷ 3 = 6."),
       ("The range of 12, 20, 15 is...", "8", ["20", "12", "47"], "20 − 12."),
       ("The mode is the number that...", "appears most often", ["is in the middle", "is the biggest", "is the total"], "Most frequent."),
       ("The median of 1, 3, 5 is...", "3", ["1", "5", "9"], "The middle."),
       ("To find a mean we add the numbers then...", "divide by how many there are", ["multiply by how many", "subtract the smallest", "take the middle"], "Total ÷ number of items.")],
      ["Median for an even number of values may be left out by the school; it is mentioned briefly."])

fig = shapes([CIRCLE(60 + 40 * k, 50, 14, fill="red") for k in range(3)] + [CIRCLE(60 + 40 * (3 + k), 50, 14, fill="green") for k in range(2)] +
             [RECT(30, 20, 220, 60, stroke="ink", width=2), T(140, 110, "A bag: 3 red and 2 green marbles", 14), T(140, 135, "P(red) = 3/5   P(green) = 2/5", 14, bold=True)], 400, 160)
build(ch, "probability", "Chance and probability (introduction)", 25,
      ["Use words such as impossible, unlikely, likely and certain.", "Write the probability of an event as a fraction.", "List the possible outcomes of simple experiments."],
      [("key", "definition", "Chance", "**Probability** tells how likely something is. An event can be **impossible** (cannot happen), **unlikely**, **even chance**, **likely** or **certain** (must happen). Probability is a number from **0** (impossible) to **1** (certain). Examples: the sun rising tomorrow is certain; a goat flying is impossible."),
       ("fig", fig, "A bag with 3 red and 2 green marbles.", "A box with three red and two green marbles. Below, the probability of red is 3 over 5 and of green is 2 over 5."),
       ("key", "formule", "Counting outcomes", "If all outcomes are **equally likely**: probability = (number of ways the event can happen) ÷ (total number of outcomes). With 3 red and 2 green marbles there are 5 equally likely marbles, so P(red) = $\\frac{3}{5}$. A fair coin has 2 outcomes: heads or tails, each with probability $\\frac{1}{2}$."),
       ("key", "pieges", "Common mistakes", "- Writing the probability as more than 1 (it cannot be).\n- Counting only the favourable outcomes and not the total.\n- Thinking that after 3 heads a coin is 'due' tails: each toss is independent.\n- Confusing 'unlikely' with 'impossible'.")],
      [("ex", "Example 1", "A bag has 3 red and 2 green marbles. What is the probability of taking a red marble?", ["Favourable outcomes (red) = 3. Total marbles = 5.", "Probability = 3/5."], "3/5", fig),
       ("ex", "Example 2", "A fair dice is rolled. What is the probability of getting an even number?", ["The even numbers are 2, 4 and 6: 3 favourable outcomes out of 6.", "3/6 = 1/2."], "1/2", None)],
      [M("application", "Which event is certain?", "Tomorrow comes after today", ["It rains in the desert every day", "A goat flies", "The next baby is a boy"], "Certain events must happen."),
       N("application", "A dice is rolled. How many outcomes are there?", 6, "1, 2, 3, 4, 5, 6."),
       M("application", "A fair coin is tossed. The probability of heads is...", "1/2", ["1", "2", "0"], "One of two equal outcomes."),
       M("approfondissement", "A bag has 4 blue and 6 yellow balls. The probability of blue is...", "4/10", ["4/6", "6/10", "10/4"], "4 favourable out of 10 balls."),
       TF("approfondissement", "A probability can be 3/2.", False, "Probability is never more than 1."),
       P("A spinner has 8 equal sections: 4 are red, 3 are blue and 1 is green.",
         [PM("What is the probability of red?", "4/8", ["4/4", "1/8", "8/4"], "4 red out of 8."),
          PM("What is the probability of green?", "1/8", ["1/4", "1/1", "7/8"], "1 green out of 8."),
          PM("Which colour is the most likely?", "red", ["blue", "green", "all the same"], "Red has the most sections.", points=2)])],
      [("A probability of 0 means the event is...", "impossible", ["certain", "likely", "even"], "It cannot happen."),
       ("A probability of 1 means the event is...", "certain", ["impossible", "unlikely", "even"], "It must happen."),
       ("The chance of getting tails on a fair coin is...", "1/2", ["1/3", "2/3", "1/4"], "Two equal outcomes."),
       ("A bag has 2 red and 3 blue counters. P(blue) =", "3/5", ["2/5", "3/2", "2/3"], "3 out of 5."),
       ("We can write a probability as a...", "fraction", ["length", "mass", "time"], "For example 1/6.")],
      ["Probability is only an introduction (equally likely outcomes)."])

fig = table([["Cost price", "Selling price", "Result"], ["5,000", "6,500", "Profit 1,500"], ["8,000", "7,000", "Loss 1,000"]], [110, 110, 120], rh=32, size=14)
assert 6500 - 5000 == 1500 and 1500 / 5000 * 100 == 30
build(ch, "profit-loss", "Profit, loss and percentage profit", 30,
      ["Say what cost price and selling price are.", "Find profit or loss.", "Find the profit or loss as a percentage of the cost price."],
      [("key", "definition", "Profit and loss", "The **cost price (CP)** is what the trader pays to buy goods. The **selling price (SP)** is what the trader sells them for. If SP is **more** than CP there is a **profit**: profit = SP − CP. If SP is **less** than CP there is a **loss**: loss = CP − SP."),
       ("fig", fig, "Profit and loss examples.", "A table with cost price, selling price and result: 5,000 to 6,500 is a profit of 1,500; 8,000 to 7,000 is a loss of 1,000."),
       ("key", "formule", "Percentage profit", "Percentage profit = (profit ÷ cost price) × 100. If a trader buys for 5,000 FCFA and sells for 6,500 FCFA, the profit is 1,500 FCFA and the percentage profit is 1,500 ÷ 5,000 × 100 = 30%. Always divide by the **cost price**, not by the selling price."),
       ("key", "pieges", "Common mistakes", "- Dividing the profit by the selling price.\n- Mixing profit and loss: check which price is bigger.\n- Forgetting the unit FCFA.\n- Forgetting that profit is not the same as the money received.")],
      [("ex", "Example 1", "A trader buys a sack of rice for 18,000 FCFA and sells it for 21,000 FCFA. Find the profit.", ["SP is bigger than CP, so there is a profit.", "Profit = 21,000 − 18,000 = 3,000."], "3,000 FCFA profit.", None),
       ("ex", "Example 2", "Find the percentage profit in Example 1.", ["Percentage profit = profit ÷ CP × 100 = 3,000 ÷ 18,000 × 100.", "3,000 ÷ 18,000 = 1/6, and 1/6 × 100 = 16.7 (to 1 decimal place)."], "About 16.7%", None)],
      [N("application", "CP = 5,000 FCFA, SP = 6,500 FCFA. Find the profit.", 1500, "6,500 − 5,000.", unit="FCFA"),
       N("application", "CP = 8,000 FCFA, SP = 7,000 FCFA. Find the loss.", 1000, "8,000 − 7,000.", unit="FCFA"),
       M("application", "If the selling price is bigger than the cost price there is a...", "profit", ["loss", "discount", "tax"], "Profit = SP − CP."),
       N("approfondissement", "A trader buys 20 bags of charcoal at 1,500 FCFA each and sells all for 36,000 FCFA. Find the profit.", 36000 - 20 * 1500, "CP = 30,000; profit = 6,000.", unit="FCFA"),
       N("approfondissement", "CP = 4,000 FCFA and profit = 1,000 FCFA. Find the percentage profit.", 25, "1,000 ÷ 4,000 × 100 = 25.", unit="%"),
       P("Mama Bih buys 50 pineapples at 200 FCFA each. She sells 40 of them at 350 FCFA each, and the other 10 at 100 FCFA each.",
         [PN("What did she pay for the pineapples?", 50 * 200, "50 × 200 = 10,000.", unit="FCFA"),
          PN("How much did she receive in all?", 40 * 350 + 10 * 100, "40 × 350 = 14,000 and 10 × 100 = 1,000; total 15,000.", unit="FCFA"),
          PN("What is her percentage profit?", 50, "Profit 5,000; 5,000 ÷ 10,000 × 100 = 50.", unit="%", points=2)])],
      [("Profit = ...", "selling price − cost price", ["cost price − selling price", "cost price + selling price", "selling price × cost price"], "When SP > CP."),
       ("Buying at 2,000 FCFA and selling at 1,500 FCFA gives a...", "loss of 500 FCFA", ["profit of 500 FCFA", "profit of 3,500 FCFA", "loss of 1,500 FCFA"], "CP − SP = 500."),
       ("Percentage profit is found by dividing profit by...", "the cost price", ["the selling price", "the loss", "the number of items"], "Then multiply by 100."),
       ("The price a trader pays to buy goods is the...", "cost price", ["selling price", "profit", "discount"], "CP."),
       ("CP 200 and profit 50: percentage profit =", "25%", ["50%", "250%", "4%"], "50/200 = 1/4.")],
      ["Percentage profit is based on cost price, as is usual in school books; check local convention."])

assert 50000 * 5 * 2 / 100 == 5000
fig = table([["Principal", "Rate", "Time", "Interest"], ["50,000", "5% a year", "2 years", "5,000"]], [90, 90, 80, 80], rh=34, size=14)
build(ch, "simple-interest", "Simple interest (introduction)", 25,
      ["Say what principal, rate, time and interest are.", "Calculate simple interest with the formula.", "Find the total amount after some years."],
      [("key", "definition", "Interest", "When you **save** or **borrow** money, a bank or a savings group (njangi) may pay or charge **interest**. The money saved or borrowed is the **principal (P)**. The **rate (R)** is the percentage per year. The **time (T)** is the number of years. **Simple interest** is the same amount each year."),
       ("fig", fig, "An example of simple interest.", "A table: principal 50,000 FCFA, rate 5 per cent a year, time 2 years, interest 5,000 FCFA."),
       ("key", "formule", "Formula", "Simple interest $I = \\frac{P \\times R \\times T}{100}$. Example: P = 50,000 FCFA, R = 5%, T = 2 years: I = 50,000 × 5 × 2 ÷ 100 = 5,000 FCFA. The **amount** at the end = principal + interest = 55,000 FCFA."),
       ("key", "pieges", "Common mistakes", "- Forgetting to divide by 100.\n- Using months instead of years.\n- Giving only the interest when the question asks for the total amount.\n- Thinking the interest grows each year (that is compound interest, not studied here).")],
      [("ex", "Example 1", "Find the simple interest on 50,000 FCFA at 5% a year for 2 years.", ["Use I = P × R × T ÷ 100 = 50,000 × 5 × 2 ÷ 100.", "50,000 × 5 × 2 = 500,000, and 500,000 ÷ 100 = 5,000."], "5,000 FCFA", fig),
       ("ex", "Example 2", "What is the total amount after those 2 years?", ["Amount = principal + interest.", "50,000 + 5,000 = 55,000."], "55,000 FCFA", None)],
      [N("application", "Simple interest on 20,000 FCFA at 10% a year for 1 year.", 2000, "20,000 × 10 × 1 ÷ 100.", unit="FCFA"),
       N("application", "Simple interest on 10,000 FCFA at 4% a year for 3 years.", 1200, "10,000 × 4 × 3 ÷ 100.", unit="FCFA"),
       M("application", "In the formula the letter R stands for...", "rate", ["rice", "ratio", "result"], "Rate per cent per year."),
       N("approfondissement", "How much money is there after 2 years if you save 40,000 FCFA at 5% simple interest a year?", 40000 + 40000 * 5 * 2 // 100, "Interest 4,000; total 44,000.", unit="FCFA"),
       TF("approfondissement", "With simple interest, the interest is the same each year.", True, "It is always calculated on the first principal."),
       P("A savings group in Bafoussam lends Mr Ngu 80,000 FCFA at 6% a year for 2 years.",
         [PN("What is the interest?", 80000 * 6 * 2 // 100, "80,000 × 6 × 2 ÷ 100 = 9,600.", unit="FCFA"),
          PN("What is the total he must pay back?", 80000 + 9600, "80,000 + 9,600 = 89,600.", unit="FCFA"),
          PN("How much does he pay back each month if he pays equally over the 24 months?", 89600 / 24, "89,600 ÷ 24 = about 3,733.", tol=1, unit="FCFA", points=2)])],
      [("The money you save or borrow is the...", "principal", ["interest", "rate", "amount"], "Principal = starting money."),
       ("Simple interest on 1,000 FCFA at 10% for 1 year is...", "100 FCFA", ["10 FCFA", "1,010 FCFA", "1,000 FCFA"], "1,000 × 10 ÷ 100."),
       ("Amount =", "principal + interest", ["principal − interest", "principal × rate", "interest ÷ time"], "The total at the end."),
       ("In simple interest the time T is in...", "years", ["days", "months", "hours"], "Rate is per year."),
       ("Interest on 5,000 FCFA at 2% for 2 years is...", "200 FCFA", ["20 FCFA", "2,000 FCFA", "500 FCFA"], "5,000 × 2 × 2 ÷ 100.")],
      ["Interest and 'njangi' are mentioned as examples; whether the curriculum includes simple interest in Class 5 is to be checked (it may be Class 6)."])

p.write()
