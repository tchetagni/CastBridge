import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from p45_kit import *

TAG["v"] = "Class 4 Maths"
p = Pack("class4-maths", "Mathematics — Class 4", level="Class 4", subject="maths", cursus="primary",
         description="Class 4 mathematics for the English-speaking primary subsystem: numbers to 100,000, the four operations, fractions and decimals, measures, money, time, perimeter and area, angles, shapes and data. Draft lessons with worked examples, exercises and self-checks.",
         programRef="MINEDUB primary school curriculum (English-speaking subsystem), Level II (Class 4) — Mathematics; to be checked against the official syllabus")
REF = p.programRef
f = lambda n: "{:,}".format(n)

# ======================================================================= 1. numbers
ch = p.chapter("numbers", "Numbers up to 100,000", REF)

fig = table([["TTh", "Th", "H", "T", "U"], ["4", "5", "3", "7", "2"]], [60] * 5, rh=34, size=18)
n1 = 45372
assert n1 == 4 * 10000 + 5 * 1000 + 3 * 100 + 7 * 10 + 2
build(ch, "place-value", "Place value up to 100,000", 25,
      ["Read and write numbers up to 100,000 in figures and in words.", "Say the value of each digit.", "Write a number in expanded form."],
      [("key", "definition", "Place value", "Each digit in a number has a **place** and a **value**. From the right we have: **units (U)**, **tens (T)**, **hundreds (H)**, **thousands (Th)** and **ten thousands (TTh)**. In 45,372 the digit 4 means 4 ten thousands, which is 40,000. The digit 5 means 5 thousands, which is 5,000."),
       ("fig", fig, "The number 45,372 in a place-value table.", "A table with five columns: TTh, Th, H, T, U. The digits written below are 4, 5, 3, 7 and 2."),
       ("key", "retenir", "Expanded form", "45,372 = 40,000 + 5,000 + 300 + 70 + 2. We use a comma between the thousands and the hundreds to make a big number easy to read. The biggest 5-digit number is 99,999. One more is **100,000**, which is one hundred thousand."),
       ("key", "pieges", "Common mistakes", "- Writing the comma in the wrong place: 45,372 is right, 4,5372 is wrong.\n- Forgetting the zero place holder: **thirty thousand and five** is 30,005, not 305.\n- Mixing the **digit** (5) with its **value** (5,000).")],
      [("ex", "Example 1", "Write the number 36,508 in words and in expanded form.", ["Read the places: 3 TTh, 6 Th, 5 H, 0 T, 8 U.", "Words: thirty-six thousand, five hundred and eight.", "Expanded form: 30,000 + 6,000 + 500 + 0 + 8."], "Thirty-six thousand, five hundred and eight; 30,000 + 6,000 + 500 + 8.", fig),
       ("ex", "Example 2", "What is the value of the digit 7 in 72,914?", ["Find the place of the 7: it is the first digit, so it is in the ten thousands place.", "Value = 7 × 10,000 = 70,000."], "70,000")],
      [M("application", "What is the value of the digit 6 in 56,420?", "6,000", ["600", "60,000", "6"], "6 is in the thousands place, so its value is 6,000."),
       N("application", "Write in figures: 8 thousands, 2 hundreds, 0 tens and 5 units.", 8205, "8,000 + 200 + 0 + 5 = 8,205."),
       M("application", "Which is the number 'twenty thousand and thirty'?", "20,030", ["2,030", "20,300", "200,030"], "20,000 + 30 = 20,030."),
       TF("approfondissement", "The digit 3 in 83,251 has the value 3,000.", True, "3 is in the thousands place."),
       MA("approfondissement", "Match each number to its words.", [("4,050", "four thousand and fifty"), ("40,500", "forty thousand, five hundred"), ("45,000", "forty-five thousand"), ("5,040", "five thousand and forty")], "Read the places carefully; zeros keep the places."),
       P("Ebot sells cocoa. One year he sold 65,430 kg and the next year 54,360 kg.",
         [PM("What is the value of the digit 5 in 65,430?", "5,000", ["50,000", "500", "5"], "5 is in the thousands place."),
          PN("How many thousands are there in 65,430? (Give the digit in the thousands place.)", 5, "65,430 = 6 TTh + 5 Th + ..."),
          PM("Which number is written in expanded form 50,000 + 4,000 + 300 + 60?", "54,360", ["54,306", "5,436", "45,360"], "Put each part in its place.", points=2)])],
      [("What is the value of 2 in 82,150?", "2,000", ["200", "20,000", "20"], "2 is in the thousands place."),
       ("Which number comes just after 99,999?", "100,000", ["99,990", "10,000", "999,999"], "99,999 + 1 = 100,000."),
       ("Which is 7,000 + 400 + 5?", "7,405", ["7,450", "74,005", "745"], "Write 7, 4, 0 and 5."),
       ("In 30,205, what does the digit 0 in the tens place show?", "no tens", ["no units", "zero thousands", "ten tens"], "The zero holds the place."),
       ("How many ten thousands are in 70,000?", "7", ["70", "700", "1"], "70,000 = 7 × 10,000.")],
      ["Number words follow common British usage (e.g. 'five hundred and eight'); check the local convention for commas versus spaces."])

fig = nline(60, 70, 1, marks=[(67, "67")])
assert round(67, -1) == 70 and round(4352, -2) == 4400 and round(7500, -3) == 8000
build(ch, "compare-order-round", "Comparing, ordering and rounding numbers", 25,
      ["Compare numbers with <, > and =.", "Order numbers from smallest to biggest and back.", "Round to the nearest 10, 100 and 1,000."],
      [("key", "definition", "Comparing", "To compare two numbers, first count the digits: the number with **more digits** is bigger. If they have the same number of digits, compare the **left-most digits** first. The sign **<** means *less than*, **>** means *greater than*. Example: 4,352 < 4,520 because 3 hundreds are less than 5 hundreds."),
       ("key", "methode", "Rounding", "Look at the digit **to the right** of the place you round to. If it is **0, 1, 2, 3 or 4**, keep the digit and make the rest zeros. If it is **5, 6, 7, 8 or 9**, add 1 to the digit. So 67 rounds to 70 (nearest ten) and 4,352 rounds to 4,400 (nearest hundred)."),
       ("fig", fig, "67 is closer to 70 than to 60.", "A number line from 60 to 70 with a red dot at 67, nearer to 70."),
       ("key", "pieges", "Common mistakes", "- Comparing only the last digits: 2,999 is **less** than 3,000, even though 9 is more than 0.\n- Rounding the wrong way when the digit is 5: **5 goes up**, so 250 rounds to 300 (nearest hundred).\n- Changing the digits on the left when you should keep them: rounding 4,352 to the nearest hundred gives 4,400, not 5,400.")],
      [("ex", "Example 1", "Put in order, smallest first: 5,208; 5,028; 5,280; 4,999.", ["Compare the thousands: 4,999 is the smallest (4 thousands).", "The others have 5 thousands. Compare hundreds: 5,028 has 0, 5,208 has 2 and 5,280 has 2. Then compare tens: 0 < 8, so 5,208 < 5,280."], "4,999; 5,028; 5,208; 5,280."),
       ("ex", "Example 2", "Round 6,748 to the nearest 10, 100 and 1,000.", ["Nearest ten: the units digit is 8, so go up: 6,750.", "Nearest hundred: the tens digit is 4, so stay: 6,700. Nearest thousand: the hundreds digit is 7, so go up: 7,000."], "6,750; 6,700; 7,000.", None)],
      [M("application", "Which sign makes this true? 7,450 __ 7,405", ">", ["<", "=", "+"], "The hundreds are the same; 5 tens > 0 tens."),
       N("application", "Round 3,462 to the nearest hundred.", 3500, "Tens digit is 6, so go up: 3,500."),
       M("application", "Which is the biggest?", "40,120", ["4,999", "9,999", "14,999"], "40,120 has 4 ten thousands; 14,999 has only 1 ten thousand; the other two have four digits."),
       TF("approfondissement", "Rounded to the nearest thousand, 8,500 becomes 9,000.", True, "The hundreds digit is 5, so the thousand goes up."),
       MA("approfondissement", "Match each number with its value rounded to the nearest 10.", [("73", "70"), ("78", "80"), ("125", "130"), ("124", "120")], "Look at the units digit: 5 or more goes up."),
       P("A market in Bafoussam counted the people on four days: 12,408; 12,840; 12,084; 12,480.",
         [PM("Which day had the most people?", "12,840", ["12,408", "12,084", "12,480"], "Compare hundreds: 8 is the biggest."),
          PN("Round the smallest number to the nearest thousand.", 12000, "The smallest is 12,084; hundreds digit is 0, so 12,000."),
          PM("Which list is in order, smallest first?", "12,084; 12,408; 12,480; 12,840", ["12,408; 12,084; 12,480; 12,840", "12,840; 12,480; 12,408; 12,084", "12,084; 12,480; 12,408; 12,840"], "Compare hundreds then tens.", points=2)])],
      [("Rounded to the nearest 10, 245 is...", "250", ["240", "200", "300"], "5 goes up."),
       ("Which number is the smallest?", "3,099", ["3,909", "3,990", "9,300"], "Compare hundreds: 0 is the smallest."),
       ("Rounded to the nearest thousand, 3,600 is...", "4,000", ["3,000", "3,600", "4,600"], "The hundreds digit 6 makes the thousands go up."),
       ("Which sign goes between 2,500 and 2,050?", ">", ["<", "=", "-"], "5 hundreds is more than 0 hundreds."),
       ("The number line shows 67 between 60 and 70; it is nearer to...", "70", ["60", "65", "100"], "67 is 3 away from 70 and 7 from 60.")],
      ["Rounding half up (5 goes up) is the convention taught in primary schools."])

seq = [5, 10, 15, 20, 25]
assert all(seq[i + 1] - seq[i] == 5 for i in range(4))
fig = grid([("5", "+5"), ("10", "+5"), ("15", "+5"), ("20", "+5")], 4, 80, 62, gap=10)
build(ch, "patterns", "Number patterns and sequences", 20,
      ["Find the rule of a number pattern.", "Continue a pattern up and down.", "Count in 2s, 5s, 10s, 25s, 100s and 1,000s."],
      [("key", "definition", "Number patterns", "A **number pattern** (or sequence) follows a **rule**. 5, 10, 15, 20, 25 is a pattern: the rule is **add 5**. In 100, 90, 80, 70 the rule is **subtract 10**. To find the rule, subtract two numbers that are side by side. Check that the rule works for every step."),
       ("fig", fig, "Counting in 5s: add 5 every time.", "Four boxes with the numbers 5, 10, 15 and 20. Under each is written plus 5."),
       ("key", "retenir", "Counting patterns", "Count in **2s**: 2, 4, 6, 8... (even numbers). Count in **5s**: numbers end in 0 or 5. Count in **25s**: 25, 50, 75, 100. Count in **1,000s**: 3,000, 4,000, 5,000. Some patterns **multiply**: 2, 4, 8, 16 (times 2)."),
       ("key", "pieges", "Common mistakes", "- Finding the rule from only one pair: check at least two steps.\n- Mixing add and multiply: 2, 4, 6 adds 2, but 2, 4, 8 doubles.\n- Counting backwards by mistake: 100, 75, 50 subtracts 25.")],
      [("ex", "Example 1", "Find the rule and the next two numbers: 120, 150, 180, 210, ...", ["Subtract neighbours: 150 − 120 = 30, 180 − 150 = 30.", "The rule is add 30. Next: 210 + 30 = 240 and 240 + 30 = 270."], "Add 30; 240, 270."),
       ("ex", "Example 2", "A pattern starts at 1,000 and goes down by 250. Write the first five terms.", ["Start: 1,000. Subtract 250 each time.", "1,000 − 250 = 750; 750 − 250 = 500; 500 − 250 = 250; 250 − 250 = 0."], "1,000; 750; 500; 250; 0.", None)],
      [M("application", "What is the rule? 7, 14, 21, 28", "add 7", ["add 14", "multiply by 7", "subtract 7"], "Each step adds 7."),
       N("application", "Continue the pattern: 50, 100, 150, 200, __", 250, "The rule is add 50."),
       M("application", "Which number is next? 3,000; 4,000; 5,000; __", "6,000", ["5,500", "5,100", "7,000"], "Add 1,000."),
       TF("approfondissement", "The pattern 2, 4, 8, 16 follows the rule add 2.", False, "2 + 2 = 4, but 4 + 2 = 6, not 8. The rule is multiply by 2."),
       MA("approfondissement", "Match each pattern to its rule.", [("10, 20, 30", "add 10"), ("90, 80, 70", "subtract 10"), ("3, 6, 12", "multiply by 2"), ("100, 75, 50", "subtract 25")], "Check two steps each time."),
       P("A trader in Limbe puts mangoes in heaps. Heap 1 has 12, heap 2 has 17, heap 3 has 22 and heap 4 has 27.",
         [PM("What is the rule?", "add 5", ["add 12", "add 10", "multiply by 2"], "17 − 12 = 5."),
          PN("How many mangoes are in heap 6?", 37, "Heap 5 = 32, heap 6 = 37."),
          PN("In which heap number will there be 52 mangoes? (Count: 12 is heap 1.)", 9, "52 = 12 + 5 × 8, so it is heap 9.", points=2)])],
      [("What is the next number in 6, 12, 18, 24?", "30", ["28", "36", "32"], "The rule is add 6."),
       ("Which number is in the pattern of counting in 5s?", "65", ["62", "68", "63"], "Numbers in 5s end in 0 or 5."),
       ("The rule of 80, 70, 60 is...", "subtract 10", ["add 10", "add 20", "multiply by 2"], "Each step is 10 less."),
       ("What is the next number in 1, 2, 4, 8?", "16", ["10", "12", "14"], "Multiply by 2."),
       ("What comes before 500 when counting in 100s?", "400", ["490", "499", "600"], "100 less.")],
      ["Position-to-term reasoning (heap 9) is an extension; teacher to decide if it is suitable for all pupils."])

# ======================================================================= 2. operations
ch = p.chapter("operations", "The four operations", REF)

a, b = 4256, 3879
assert a + b == 8135 and a - b == 377
fig = table([["Th", "H", "T", "U"], ["", "1", "1", ""], ["4", "2", "5", "6"], ["+ 3", "8", "7", "9"], ["8", "1", "3", "5"]], [60] * 4, rh=28, size=16)
build(ch, "add-subtract", "Adding and subtracting large numbers", 25,
      ["Add numbers with carrying.", "Subtract numbers with borrowing (exchanging).", "Check an answer by using the opposite operation."],
      [("key", "methode", "Adding", "Write the numbers under each other so that units are under units. Add from the **right**. If a column makes **10 or more**, write the units and **carry** the tens to the next column. Example: 6 + 9 = 15: write 5 and carry 1."),
       ("fig", fig, "4,256 + 3,879 = 8,135 with carrying.", "A column addition table: 4,256 plus 3,879 with the carried ones above, giving 8,135."),
       ("key", "methode", "Subtracting", "Subtract from the **right**. If the top digit is smaller, **exchange**: take 1 from the next column on the left and add 10 to the top digit. **Check**: the answer plus the number you took away must give back the first number."),
       ("key", "pieges", "Common mistakes", "- Not lining up the digits: 245 + 3,600 is not 3,845 if the 5 is under the 6.\n- Subtracting the smaller digit from the bigger digit in each column, whichever is on top.\n- Forgetting the carried 1.\n- Not checking the answer.")],
      [("ex", "Example 1", "A shop in Douala sold 4,256 pens in May and 3,879 pens in June. How many pens in all?", ["Add the units: 6 + 9 = 15. Write 5, carry 1.", "Tens: 5 + 7 + 1 = 13. Write 3, carry 1. Hundreds: 2 + 8 + 1 = 11. Write 1, carry 1. Thousands: 4 + 3 + 1 = 8."], "4,256 + 3,879 = 8,135 pens.", fig),
       ("ex", "Example 2", "Work out 4,256 − 3,879 and check.", ["Units: 6 − 9 cannot be done. Exchange: 16 − 9 = 7. Tens: 4 − 7 cannot: 14 − 7 = 7. Hundreds: 1 − 8: 11 − 8 = 3. Thousands: 3 − 3 = 0.", "Check: 377 + 3,879 = 4,256. Correct."], "377")],
      [N("application", "Work out 5,347 + 2,684.", 5347 + 2684, "Add column by column with carrying: 8,031."),
       N("application", "Work out 9,000 − 2,468.", 9000 - 2468, "Exchange across the zeros: 6,532."),
       M("application", "Which sum has the answer 10,000?", "6,500 + 3,500", ["6,500 + 3,000", "5,000 + 4,000", "7,500 + 2,000"], "6,500 + 3,500 = 10,000."),
       TF("approfondissement", "If 8,135 − 3,879 = 4,256, then 4,256 + 3,879 = 8,135.", True, "Addition and subtraction undo each other."),
       N("approfondissement", "A number plus 2,345 is 6,000. What is the number?", 6000 - 2345, "6,000 − 2,345 = 3,655."),
       P("A farmer in Bamenda harvested 12,450 kg of maize in one season and 9,875 kg in the next.",
         [PN("How many kg in the two seasons together?", 12450 + 9875, "12,450 + 9,875 = 22,325."),
          PN("How many kg more in the first season than in the second?", 12450 - 9875, "12,450 − 9,875 = 2,575."),
          PN("He sold 15,000 kg of the total. How many kg are left?", 12450 + 9875 - 15000, "22,325 − 15,000 = 7,325.", points=2)])],
      [("Work out 3,456 + 1,234.", "4,690", ["4,580", "4,790", "4,680"], "Add each column."),
       ("Work out 7,000 − 1,500.", "5,500", ["6,500", "5,000", "8,500"], "7,000 − 1,500 = 5,500."),
       ("What is 99,999 − 1?", "99,998", ["99,990", "100,000", "98,999"], "One less."),
       ("To check 5,000 − 2,300 = 2,700 we do...", "2,700 + 2,300", ["5,000 − 2,700 + 2,300", "5,000 + 2,300", "2,700 − 2,300"], "Adding back gives 5,000."),
       ("When a column adds up to 14, we write...", "4 and carry 1", ["14 in the column", "1 and carry 4", "0 and carry 14"], "Units go in the column; tens are carried.")],
      [])

assert 347 * 6 == 2082 and 52 * 14 == 728 and 238 * 21 == 4998
fig = table([["", "T", "U"], ["", "5", "2"], ["x", "1", "4"], ["2 0 8", "", ""], ["5 2 0", "", ""], ["7 2 8", "", ""]], [90, 60, 60], rh=26, size=15)
build(ch, "multiplication", "Multiplication", 30,
      ["Multiply by a 1-digit number.", "Multiply by a 2-digit number.", "Use multiplication tables and estimate answers."],
      [("key", "definition", "Multiplication", "Multiplication is **repeated addition**: 4 × 6 = 6 + 6 + 6 + 6 = 24. The order does not change the answer: 4 × 6 = 6 × 4. Learn the tables up to 12 × 12. Multiplying by **10** moves each digit one place left (add a zero): 45 × 10 = 450. Multiplying by **100** adds two zeros."),
       ("key", "methode", "Long multiplication", "To work out 52 × 14: multiply 52 by the **units** (4): 208. Then multiply 52 by the **tens** (10): 520. **Add** the two lines: 208 + 520 = 728. For a 1-digit number such as 347 × 6, multiply each digit by 6 from the right and carry."),
       ("fig", fig, "52 × 14 = 208 + 520 = 728.", "A long multiplication layout for 52 times 14 with partial products 208 and 520 and total 728."),
       ("key", "pieges", "Common mistakes", "- Forgetting the zero when multiplying by the tens digit.\n- Forgetting to add the carried number.\n- Using the wrong table: 7 × 8 is 56, not 54.\n- **Estimate** first: 52 × 14 is about 50 × 10 = 500, so 728 is sensible but 72 is not.")],
      [("ex", "Example 1", "A bunch of plantains costs 350 FCFA. How much do 6 bunches cost?", ["Multiply 350 × 6. 6 × 0 = 0; 6 × 5 = 30: write 0, carry 3; 6 × 3 = 18, plus 3 = 21.", "The answer is 2,100."], "2,100 FCFA", None),
       ("ex", "Example 2", "A school hall has 14 rows of 52 chairs. How many chairs in all?", ["52 × 4 = 208 (units line).", "52 × 10 = 520 (tens line). Add: 208 + 520 = 728."], "728 chairs", fig)],
      [N("application", "Work out 347 × 6.", 347 * 6, "300×6=1800, 40×6=240, 7×6=42; total 2,082."),
       N("application", "Work out 45 × 10.", 450, "Add one zero."),
       M("application", "Which is the best estimate of 49 × 11?", "500", ["50", "5,000", "100"], "About 50 × 10 = 500."),
       N("approfondissement", "Work out 238 × 21.", 238 * 21, "238 × 1 = 238; 238 × 20 = 4,760; total 4,998."),
       TF("approfondissement", "6 × 9 gives the same answer as 9 × 6.", True, "Order does not matter in multiplication."),
       P("A trader in Kumba buys 24 boxes of soap. Each box holds 36 bars.",
         [PN("How many bars in all?", 24 * 36, "24 × 36 = 864."),
          PN("Each bar costs 150 FCFA. What is the cost of 6 bars?", 6 * 150, "6 × 150 = 900.", unit="FCFA"),
          PM("Which estimate is closest to 24 × 36?", "about 800", ["about 80", "about 8,000", "about 300"], "20 × 40 = 800.", points=2)])],
      [("What is 8 × 7?", "56", ["54", "64", "48"], "Learn the 7 times table."),
       ("What is 300 × 10?", "3,000", ["300", "30", "30,000"], "Add one zero."),
       ("In 25 × 12, the first partial product (25 × 2) is...", "50", ["25", "300", "250"], "25 × 2 = 50."),
       ("Multiplication is repeated...", "addition", ["subtraction", "sharing", "rounding"], "6 × 4 = 6 + 6 + 6 + 6."),
       ("An estimate of 98 × 5 is about...", "500", ["50", "5,000", "95"], "100 × 5 = 500.")],
      [])

assert divmod(395, 4) == (98, 3) and 7 * 9 == 63
fig = grid([("Dividend", "395"), ("Divisor", "4"), ("Quotient", "98"), ("Remainder", "3")], 4, 90, 62, gap=6, size=13)
build(ch, "division", "Division with remainders", 30,
      ["Divide by a 1-digit number.", "Say what the quotient and the remainder are.", "Check a division by multiplying."],
      [("key", "definition", "Division", "Division means **sharing equally** or **grouping**. 24 ÷ 4 = 6 because 4 × 6 = 24. When things cannot be shared exactly, some are **left over**: the **remainder**. 395 ÷ 4 = 98 remainder 3, because 4 × 98 = 392 and 395 − 392 = 3."),
       ("fig", fig, "The parts of a division: 395 ÷ 4 = 98 remainder 3.", "Four boxes named dividend 395, divisor 4, quotient 98, remainder 3."),
       ("key", "methode", "Short division", "Divide the digits from the **left**. Example 395 ÷ 4: 3 ÷ 4 = 0, so take 39 ÷ 4 = 9 remainder 3. Bring down the 5: 35 ÷ 4 = 8 remainder 3. Quotient 98, remainder 3. **Check:** quotient × divisor + remainder = dividend: 98 × 4 + 3 = 395."),
       ("key", "pieges", "Common mistakes", "- A remainder must always be **smaller than the divisor**.\n- Forgetting a zero in the quotient: 612 ÷ 6 = 102, not 12.\n- Dividing the wrong way round: 4 ÷ 395 is not the same as 395 ÷ 4.\n- Forgetting the remainder in word problems.")],
      [("ex", "Example 1", "Share 395 oranges equally among 4 stalls in Buea. How many does each get and how many are left?", ["395 ÷ 4: 39 ÷ 4 = 9 rem 3; then 35 ÷ 4 = 8 rem 3.", "Each stall gets 98 oranges. 3 oranges are left."], "98 each, remainder 3.", fig),
       ("ex", "Example 2", "Work out 612 ÷ 6 and check.", ["6 ÷ 6 = 1; 1 ÷ 6 = 0 (write 0); bring down 2: 12 ÷ 6 = 2. The answer is 102.", "Check: 102 × 6 = 612."], "102")],
      [N("application", "Work out 456 ÷ 6.", 456 // 6, "6 × 76 = 456."),
       N("application", "What is the remainder when 47 is divided by 5?", 47 % 5, "5 × 9 = 45, 47 − 45 = 2."),
       M("application", "Which is true for 83 ÷ 9?", "9 remainder 2", ["8 remainder 11", "9 remainder 11", "10"], "9 × 9 = 81, 83 − 81 = 2."),
       TF("approfondissement", "In 100 ÷ 7 = 14 remainder 2, the remainder 2 is smaller than 7.", True, "98 + 2 = 100 and 2 < 7."),
       N("approfondissement", "Work out 2,408 ÷ 8.", 2408 // 8, "8 × 301 = 2,408."),
       P("A school in Garoua has 365 pupils. Each classroom takes 40 pupils.",
         [PN("How many full classrooms can be filled?", 365 // 40, "40 × 9 = 360."),
          PN("How many pupils are left over?", 365 % 40, "365 − 360 = 5."),
          PN("How many classrooms are needed in all?", 365 // 40 + 1, "The 5 left-over pupils need one more classroom: 10.", points=2)])],
      [("What is 72 ÷ 8?", "9", ["8", "7", "10"], "8 × 9 = 72."),
       ("The remainder of 25 ÷ 4 is...", "1", ["0", "6", "4"], "4 × 6 = 24."),
       ("We check 98 × 4 + 3 = 395 to check a...", "division", ["addition", "rounding", "pattern"], "Quotient × divisor + remainder = dividend."),
       ("What is 600 ÷ 6?", "100", ["10", "1,000", "60"], "6 hundreds ÷ 6 = 1 hundred."),
       ("Which remainder is impossible when dividing by 5?", "5", ["0", "3", "4"], "A remainder is smaller than the divisor.")],
      [])

assert (2500 - 1850) == 650 and 3 * 1200 + 2 * 450 == 4500
fig = boxes(["Read the problem again", "Find what you must work out", "Choose + − × or ÷", "Work it out and check"], size=14)
build(ch, "word-problems", "Solving word problems with the four operations", 30,
      ["Read a problem and choose the right operation.", "Solve problems with more than one step.", "Check that the answer makes sense."],
      [("key", "methode", "Steps", "1. **Read** the problem two times.\n2. Underline the numbers and say what you must find.\n3. Choose the operation: **add** to join, **subtract** to find the difference or what is left, **multiply** for equal groups, **divide** to share or group.\n4. **Work out** the answer, write the unit (FCFA, kg...), and **check** it."),
       ("fig", fig, "Four steps to solve a word problem.", "Four stacked boxes with the steps: read the problem again, find what you must work out, choose the operation, work it out and check."),
       ("key", "retenir", "Key words", "**Altogether, total, sum, more** often mean add. **Left, difference, change, how many more** often mean subtract. **Each, every, times, groups of** often mean multiply. **Share, equal parts, per** often mean divide. Be careful: always think about the story, not only the key word."),
       ("key", "pieges", "Common mistakes", "- Using only the key word without thinking.\n- Doing only the first step of a two-step problem.\n- Forgetting the unit.\n- Not checking: if 5 pens cost 1,000 FCFA, one pen cannot cost 5,000 FCFA.")],
      [("ex", "Example 1", "Mama Ngo buys 3 bags of rice at 1,200 FCFA each and 2 bottles of oil at 450 FCFA each. How much does she spend?", ["Rice: 3 × 1,200 = 3,600 FCFA. Oil: 2 × 450 = 900 FCFA.", "Total: 3,600 + 900 = 4,500 FCFA."], "4,500 FCFA", None),
       ("ex", "Example 2", "She pays with 5,000 FCFA. How much change does she get?", ["Change = money given − cost.", "5,000 − 4,500 = 500."], "500 FCFA")],
      [N("application", "A bus carries 48 passengers. How many passengers can 7 buses carry?", 48 * 7, "48 × 7 = 336.", unit="passengers"),
       N("application", "A rope of 250 m is cut into pieces of 5 m. How many pieces?", 250 // 5, "250 ÷ 5 = 50."),
       M("application", "Ada had 3,500 FCFA and spent 1,250 FCFA. Which sum finds what is left?", "3,500 − 1,250", ["3,500 + 1,250", "3,500 × 1,250", "3,500 ÷ 1,250"], "Left over means subtract."),
       N("approfondissement", "A farmer packs 360 eggs in trays of 30. Each tray sells for 1,500 FCFA. How much money for all the trays?", 360 // 30 * 1500, "360 ÷ 30 = 12 trays; 12 × 1,500 = 18,000.", unit="FCFA"),
       TF("approfondissement", "A word problem with 'how many more' is usually solved by subtraction.", True, "Find the difference between two amounts."),
       P("A school in Bali buys 25 desks at 12,500 FCFA each and 4 boards at 18,000 FCFA each.",
         [PN("Cost of the desks?", 25 * 12500, "25 × 12,500 = 312,500.", unit="FCFA"),
          PN("Cost of the boards?", 4 * 18000, "4 × 18,000 = 72,000.", unit="FCFA"),
          PN("Total cost?", 25 * 12500 + 4 * 18000, "312,500 + 72,000 = 384,500.", unit="FCFA", points=2)])],
      [("Total cost of 4 pens at 250 FCFA each is...", "1,000 FCFA", ["254 FCFA", "62 FCFA", "246 FCFA"], "4 × 250 = 1,000."),
       ("To share 84 mangoes among 7 children, we...", "divide 84 by 7", ["add 84 and 7", "subtract 7 from 84", "multiply 84 by 7"], "Sharing means divide."),
       ("Change from 2,000 FCFA after spending 1,350 FCFA is...", "650 FCFA", ["3,350 FCFA", "750 FCFA", "550 FCFA"], "2,000 − 1,350 = 650."),
       ("The first thing to do when solving a problem is...", "read it carefully", ["guess", "add all numbers", "multiply"], "Read, then choose the operation."),
       ("A two-step problem needs...", "two operations", ["no working", "only one number", "no units"], "Do one step, then the next.")],
      [])

# ======================================================================= 3. fractions and decimals
ch = p.chapter("fractions", "Fractions and decimals", REF)

fig = fracbars([(1, 2, "1/2"), (1, 4, "1/4"), (3, 4, "3/4"), (2, 8, "2/8 = 1/4")])
build(ch, "fractions-intro", "Fractions and equivalent fractions", 30,
      ["Name a fraction from a picture.", "Find equivalent fractions.", "Compare fractions with the same denominator or the same numerator."],
      [("key", "definition", "Fractions", "A **fraction** shows equal parts of a whole. The **denominator** (bottom number) tells how many equal parts the whole is cut into. The **numerator** (top number) tells how many parts we take. $\\frac{3}{4}$ means 3 out of 4 equal parts."),
       ("fig", fig, "Fraction bars: 1/2, 1/4, 3/4 and 2/8.", "Four bars. The first has 1 of 2 parts coloured, the second 1 of 4, the third 3 of 4 and the fourth 2 of 8, equal to one quarter."),
       ("key", "retenir", "Equivalent fractions", "Fractions that show the **same amount** are **equivalent**: $\\frac{1}{2} = \\frac{2}{4} = \\frac{4}{8}$. To make an equivalent fraction, **multiply** (or divide) the numerator and the denominator by the **same number**. With the same denominator, the bigger numerator is the bigger fraction: $\\frac{3}{8} > \\frac{2}{8}$."),
       ("key", "pieges", "Common mistakes", "- The parts must be **equal**: a rectangle cut in 3 unequal parts does not show thirds.\n- Thinking that a bigger denominator means a bigger fraction: $\\frac{1}{8}$ is smaller than $\\frac{1}{4}$.\n- Changing only the top or only the bottom number when finding an equivalent fraction.")],
      [("ex", "Example 1", "Write the fraction of a cake that is eaten when 3 of 8 equal slices are eaten.", ["The cake has 8 equal parts: the denominator is 8.", "3 parts are eaten: the numerator is 3."], "3/8 of the cake.", fracbars([(3, 8, "3/8")])),
       ("ex", "Example 2", "Find a fraction equivalent to 2/3 with denominator 12.", ["3 × 4 = 12, so multiply the top and the bottom by 4.", "2 × 4 = 8, so the fraction is 8/12."], "8/12")],
      [M("application", "Which fraction means 5 of 6 equal parts?", "5/6", ["6/5", "1/6", "5/5"], "Numerator 5, denominator 6."),
       N("application", "Complete: 1/2 = __/10.", 5, "Multiply top and bottom by 5."),
       M("application", "Which fraction is the biggest?", "5/8", ["3/8", "1/8", "2/8"], "Same denominator: the biggest numerator wins."),
       TF("approfondissement", "1/3 is bigger than 1/2.", False, "Thirds are smaller parts than halves, so 1/3 is smaller than 1/2."),
       N("approfondissement", "Complete: 3/4 = __/20.", 15, "4 × 5 = 20, so 3 × 5 = 15."),
       P("A baker in Yaoundé cuts a loaf into 8 equal pieces. He sells 3 and gives away 1.",
         [PM("What fraction was sold?", "3/8", ["3/5", "8/3", "5/8"], "3 out of 8."),
          PM("What fraction is left?", "4/8", ["5/8", "3/8", "2/8"], "8 − 3 − 1 = 4 pieces.", ),
          PM("Which fraction is the same as the fraction left?", "1/2", ["1/4", "1/8", "3/4"], "4/8 = 1/2.", points=2)])],
      [("In 3/5 the denominator is...", "5", ["3", "8", "2"], "The bottom number."),
       ("Which is equivalent to 1/2?", "4/8", ["1/4", "2/8", "3/8"], "Multiply top and bottom by 4."),
       ("Which fraction of a circle is shaded when 1 of 4 equal parts is shaded?", "1/4", ["4/1", "1/3", "2/4"], "One part out of four."),
       ("Which is smaller: 1/8 or 1/4?", "1/8", ["1/4", "they are equal", "3/4"], "Eighths are smaller parts."),
       ("2/6 is equivalent to...", "1/3", ["1/2", "2/3", "3/2"], "Divide top and bottom by 2.")],
      ["Fraction notation uses simple LaTeX \\frac; some teachers write fractions as a/b. Pupils meet halves, quarters and tenths first."])

fig = fracbars([(2, 8, "2/8"), (3, 8, "+ 3/8"), (5, 8, "= 5/8")], fill="green")
build(ch, "fractions-add", "Adding and subtracting fractions", 25,
      ["Add fractions with the same denominator.", "Subtract fractions with the same denominator.", "Find a fraction of a number."],
      [("key", "methode", "Same denominator", "When the denominators are the **same**, add or subtract the **numerators** and keep the denominator: $\\frac{2}{8} + \\frac{3}{8} = \\frac{5}{8}$ and $\\frac{7}{9} - \\frac{4}{9} = \\frac{3}{9}$. Do **not** add the denominators!"),
       ("fig", fig, "2/8 + 3/8 = 5/8.", "Three bars on eighths: 2 parts shaded, then 3 parts shaded, then 5 parts shaded."),
       ("key", "methode", "A fraction of a number", "To find $\\frac{1}{4}$ of 20, divide by the denominator: 20 ÷ 4 = 5. To find $\\frac{3}{4}$ of 20, find $\\frac{1}{4}$ and multiply by the numerator: 5 × 3 = 15. A whole is $\\frac{4}{4}$, so $1 - \\frac{1}{4} = \\frac{3}{4}$."),
       ("key", "pieges", "Common mistakes", "- Adding the denominators: $\\frac{1}{5} + \\frac{2}{5}$ is $\\frac{3}{5}$, not $\\frac{3}{10}$.\n- Dividing by the numerator instead of the denominator.\n- Forgetting that the sum can be more than one whole.")],
      [("ex", "Example 1", "Ada eats 2/8 of a cake and Neba eats 3/8. What fraction did they eat together and what is left?", ["Add: 2/8 + 3/8 = 5/8.", "A whole is 8/8, so the rest is 8/8 − 5/8 = 3/8."], "5/8 eaten; 3/8 left.", fig),
       ("ex", "Example 2", "A farmer has 24 goats. 3/4 of them are female. How many are female?", ["1/4 of 24 = 24 ÷ 4 = 6.", "3/4 of 24 = 6 × 3 = 18."], "18 goats")],
      [M("application", "Work out 3/7 + 2/7.", "5/7", ["5/14", "6/7", "1/7"], "3 + 2 = 5, denominator stays 7."),
       N("application", "Find 1/5 of 40.", 40 // 5, "40 ÷ 5 = 8."),
       M("application", "Work out 7/9 − 4/9.", "3/9", ["3/0", "11/9", "3/18"], "7 − 4 = 3."),
       N("approfondissement", "Find 3/5 of 35.", 35 // 5 * 3, "35 ÷ 5 = 7; 7 × 3 = 21."),
       TF("approfondissement", "2/6 + 3/6 = 5/12.", False, "Keep the denominator: 5/6."),
       P("A tank holds 60 litres of water. On Monday 1/3 was used and on Tuesday 1/6 was used (the whole tank is 6/6).",
         [PN("How many litres is 1/6 of 60?", 60 // 6, "60 ÷ 6 = 10.", unit="litres"),
          PN("How many litres is 1/3 of 60?", 60 // 3, "60 ÷ 3 = 20.", unit="litres"),
          PN("How many litres were used in all?", 60 // 3 + 60 // 6, "20 + 10 = 30.", unit="litres", points=2)])],
      [("5/10 + 3/10 =", "8/10", ["8/20", "15/10", "2/10"], "Add numerators."),
       ("1/4 of 28 is...", "7", ["4", "24", "14"], "28 ÷ 4 = 7."),
       ("1 − 2/5 =", "3/5", ["1/5", "2/5", "4/5"], "5/5 − 2/5 = 3/5."),
       ("To add fractions with the same denominator we...", "add the numerators", ["add the denominators", "multiply the numerators", "subtract"], "The denominator stays the same."),
       ("3/4 of 12 is...", "9", ["3", "4", "16"], "12 ÷ 4 = 3, 3 × 3 = 9.")],
      [])

fig = shapes([RECT(20, 20, 200, 30, fill="paper")] + [RECT(20 + k * 20, 20, 20, 30, fill=("lightgreen" if k < 3 else "paper"), width=1) for k in range(10)] +
             [RECT(20, 20, 200, 30, width=3), T(310, 40, "3 tenths = 0.3", 14, bold=True), T(120, 80, "0     0.1   0.2   0.3   ...   1", 12)], 400, 110)
build(ch, "decimals", "Introduction to decimals: tenths and hundredths", 30,
      ["Link tenths and hundredths with decimals.", "Read, write and compare decimals with one or two places.", "Add and subtract simple decimals (money)."],
      [("key", "definition", "Decimals", "A **decimal point** separates the whole part from the part that is smaller than 1. The first digit after the point is **tenths**, the second is **hundredths**. So $0.3 = \\frac{3}{10}$ and $0.25 = \\frac{25}{100}$. In 4.6 there are 4 wholes and 6 tenths."),
       ("fig", fig, "3 tenths of a strip is 0.3.", "A strip cut into ten equal parts with three parts coloured; the label says 3 tenths equals 0.3."),
       ("key", "methode", "Comparing and adding", "To compare, look at the whole part first, then tenths, then hundredths: 2.5 > 2.35 because 5 tenths > 3 tenths. To add or subtract, **line up the decimal points**: 2.5 + 1.75 = 4.25. Add a zero to help: 2.50 + 1.75. Money works the same: 2.50 means two and a half units."),
       ("key", "pieges", "Common mistakes", "- Thinking that 0.35 is bigger than 0.5 because 35 > 5. Compare **tenths first**: 0.5 = 0.50 > 0.35.\n- Not lining up the decimal points when adding.\n- Reading 0.05 as five tenths: it is five hundredths.")],
      [("ex", "Example 1", "Write 7/10 and 45/100 as decimals.", ["7 tenths is written 0.7.", "45 hundredths is written 0.45."], "0.7 and 0.45", None),
       ("ex", "Example 2", "Work out 2.5 + 1.75.", ["Write 2.50 and 1.75 with the points under each other.", "Add: 0 + 5 = 5; 5 + 7 = 12 (write 2, carry 1); 2 + 1 + 1 = 4. Answer 4.25."], "4.25")],
      [M("application", "Which decimal is the same as 3/10?", "0.3", ["0.03", "3.10", "30"], "3 tenths."),
       N("application", "Work out 3.4 + 2.5.", 5.9, "3.4 + 2.5 = 5.9.", tol=0.001),
       M("application", "Which decimal is the biggest?", "0.7", ["0.07", "0.17", "0.27"], "0.7 = 0.70 is more than 0.27."),
       TF("approfondissement", "0.5 is the same as 5/10 and 1/2.", True, "5/10 = 1/2 = 0.5."),
       N("approfondissement", "Work out 5.00 − 1.75.", 3.25, "5.00 − 1.75 = 3.25.", tol=0.001),
       P("A tailor in Limbe has a piece of cloth 2.5 m long and another piece 0.75 m long.",
         [PN("What is the total length in metres?", 3.25, "2.50 + 0.75 = 3.25.", tol=0.001, unit="m"),
          PN("What is the total length in centimetres?", 325, "3.25 × 100 = 325.", unit="cm"),
          PM("Which piece is longer?", "the 2.5 m piece", ["the 0.75 m piece", "they are equal", "we cannot tell"], "2.5 > 0.75.", points=2)])],
      [("0.6 means...", "6 tenths", ["6 hundredths", "60 tenths", "6 units"], "First digit after the point is tenths."),
       ("1.5 + 1.5 =", "3.0", ["2.5", "1.10", "3.5"], "Add tenths and wholes."),
       ("Which is smaller than 0.5?", "0.05", ["0.55", "0.6", "0.9"], "0.05 is only 5 hundredths."),
       ("0.25 is the same as...", "25/100", ["2/5", "25/10", "5/2"], "Two decimal places means hundredths."),
       ("The decimal point separates the...", "whole part from the fraction part", ["units from tens", "tenths from hundredths", "money from time"], "Digits on the right are smaller than one.")],
      ["Decimals are only introduced (one and two places); the link with money (2.50) is a simple analogy, check how the school presents it."])

# ======================================================================= 4. measures
ch = p.chapter("measures", "Measures, time and money", REF)

fig = table([["Length", "Mass", "Capacity"], ["1 km = 1,000 m", "1 kg = 1,000 g", "1 l = 1,000 ml"], ["1 m = 100 cm", "1 t = 1,000 kg", "1 l = 100 cl"], ["1 cm = 10 mm", "", ""]], [120, 120, 120], rh=30, size=13)
assert 2.5 * 1000 == 2500 and 3400 / 1000 == 3.4
build(ch, "length-mass-capacity", "Length, mass and capacity", 30,
      ["Know the units of length, mass and capacity.", "Change from a big unit to a small unit and back.", "Add and subtract measures."],
      [("key", "definition", "The units", "We measure **length** in kilometres (km), metres (m), centimetres (cm) and millimetres (mm). **Mass** (weight) is in tonnes (t), kilograms (kg) and grams (g). **Capacity** (how much a container holds) is in litres (l), centilitres (cl) and millilitres (ml)."),
       ("fig", fig, "Units of length, mass and capacity.", "A table with three columns: length (km, m, cm, mm), mass (kg, g, tonne) and capacity (litre, ml, cl) with their conversions."),
       ("key", "methode", "Changing units", "To change a **big unit to a small unit**, **multiply**: 2.5 km = 2.5 × 1,000 = 2,500 m. To change a **small unit to a big unit**, **divide**: 3,400 g = 3,400 ÷ 1,000 = 3.4 kg. Write the unit in every answer."),
       ("key", "pieges", "Common mistakes", "- Mixing metres and centimetres in the same sum: change first.\n- Multiplying when you should divide.\n- Writing 1 m = 10 cm (it is 100 cm).\n- Writing kg for litres or the other way round.")],
      [("ex", "Example 1", "Change 3 km 250 m into metres.", ["3 km = 3 × 1,000 = 3,000 m.", "3,000 m + 250 m = 3,250 m."], "3,250 m", None),
       ("ex", "Example 2", "A bag holds 2 kg of rice. How many grams are in 4 bags?", ["1 kg = 1,000 g, so 2 kg = 2,000 g.", "4 × 2,000 = 8,000 g."], "8,000 g (8 kg)")],
      [N("application", "Change 5 m into centimetres.", 500, "5 × 100 = 500.", unit="cm"),
       N("application", "Change 4,000 g into kilograms.", 4, "4,000 ÷ 1,000 = 4.", unit="kg"),
       M("application", "Which unit is best for measuring the distance from Douala to Yaoundé?", "kilometres", ["centimetres", "grams", "millilitres"], "Long distances are in km."),
       N("approfondissement", "A bottle holds 750 ml. How many ml in 4 bottles? (Answer in litres.)", 3, "4 × 750 = 3,000 ml = 3 l.", unit="l"),
       TF("approfondissement", "2.5 kg is the same as 2,500 g.", True, "2.5 × 1,000 = 2,500."),
       P("A trader in Bafoussam sells yams. He has a 50 kg bag and sells 12,500 g and then 8 kg 500 g.",
         [PN("How many kg is 12,500 g?", 12.5, "12,500 ÷ 1,000 = 12.5.", tol=0.001, unit="kg"),
          PN("How many kg did he sell in all?", 12.5 + 8.5, "12.5 + 8.5 = 21.", tol=0.001, unit="kg"),
          PN("How many kg are left in the bag?", 50 - 21, "50 − 21 = 29.", unit="kg", points=2)])],
      [("1 km equals...", "1,000 m", ["100 m", "10 m", "10,000 m"], "Kilo means thousand."),
       ("Which unit measures how much water a bucket holds?", "litre", ["kilogram", "metre", "hour"], "Capacity is in litres."),
       ("2 l = ... ml", "2,000", ["200", "20", "20,000"], "2 × 1,000."),
       ("To change kg into g we...", "multiply by 1,000", ["divide by 1,000", "add 1,000", "subtract 1,000"], "Big unit to small unit: multiply."),
       ("300 cm = ... m", "3", ["30", "300", "0.3"], "300 ÷ 100.")],
      ["Centilitre is included because it is used on some local packaging; teacher to decide emphasis."])

fig = clock(3, 45, label="3:45 (quarter to four)")
assert (14 * 60 + 25) - (9 * 60 + 40) == 285
build(ch, "time", "Telling the time and measuring time", 30,
      ["Read the time on a clock to 5 minutes and in the 24-hour form.", "Change between hours, minutes and seconds.", "Work out how long an activity lasts."],
      [("key", "definition", "Units of time", "1 minute = 60 seconds. 1 hour = 60 minutes. 1 day = 24 hours. 1 week = 7 days. 1 year = 12 months = 52 weeks (about). The **short hand** shows hours, the **long hand** shows minutes. When the long hand is at 9 it is **quarter to**; at 3 it is **quarter past**; at 6 it is **half past**."),
       ("fig", fig, "The clock shows a quarter to four.", "A clock face with the short hand near the 4 and the long hand on the 9, labelled 3:45."),
       ("key", "retenir", "24-hour clock", "After 12 noon, add 12 to the hour to get the 24-hour time: 2:30 p.m. is **14:30**, and 9:15 p.m. is **21:15**. Midnight is 00:00. To find how long something lasts, subtract the start time from the end time (change hours into minutes if needed)."),
       ("key", "pieges", "Common mistakes", "- Counting 100 minutes in an hour: there are 60.\n- Writing 3:45 as 'quarter past 3'.\n- Forgetting to add 12 after noon.\n- Subtracting hours and minutes as if they were decimals: 9:40 to 14:25 is 4 h 45 min, not 5 h 15.")],
      [("ex", "Example 1", "A film starts at 9:40 a.m. and ends at 2:25 p.m. How long is it?", ["Write the end in 24-hour time: 14:25. Change both to minutes: 14 h 25 = 865 min; 9 h 40 = 580 min.", "865 − 580 = 285 min = 4 h 45 min."], "4 hours 45 minutes", None),
       ("ex", "Example 2", "Write 8:15 p.m. in 24-hour time.", ["It is after noon, so add 12 to 8: 20.", "Keep the minutes: 20:15."], "20:15", clock(8, 15, label="8:15"))],
      [N("application", "How many minutes are in 3 hours?", 180, "3 × 60 = 180.", unit="min"),
       M("application", "What is 4:30 p.m. in 24-hour time?", "16:30", ["14:30", "4:30", "18:30"], "4 + 12 = 16."),
       M("application", "Which time is 'quarter past 7'?", "7:15", ["7:45", "6:45", "7:30"], "15 minutes after 7."),
       N("approfondissement", "School starts at 7:30 a.m. and ends at 1:15 p.m. How many minutes is that?", 345, "7:30 to 13:15 = 5 h 45 min = 345 min.", unit="min"),
       TF("approfondissement", "2 hours 30 minutes is the same as 150 minutes.", True, "2 × 60 + 30 = 150."),
       P("A bus leaves Bamenda at 6:45 a.m. and reaches Douala at 1:20 p.m.",
         [PM("What is the arrival time in 24-hour form?", "13:20", ["1:20", "3:20", "11:20"], "1 + 12 = 13."),
          PN("How many minutes was the journey?", 395, "6:45 to 13:20: 6 h 35 min = 395 min.", unit="min"),
          PM("How long is that in hours and minutes?", "6 h 35 min", ["6 h 20 min", "7 h 35 min", "5 h 35 min"], "395 = 6 × 60 + 35.", points=2)])],
      [("How many seconds are in 2 minutes?", "120", ["60", "100", "200"], "2 × 60."),
       ("Half past 6 is...", "6:30", ["6:15", "6:45", "7:30"], "Half an hour after 6."),
       ("9:00 p.m. in 24-hour time is...", "21:00", ["9:00", "19:00", "12:09"], "9 + 12 = 21."),
       ("How many days are in a week?", "7", ["5", "6", "10"], "Seven days."),
       ("From 10:15 to 11:00 is...", "45 minutes", ["15 minutes", "1 hour", "30 minutes"], "10:15 to 11:00.")],
      ["Months/weeks: '52 weeks (about)' used loosely; a year has 52 weeks and 1 or 2 days."])

fig = table([["Item", "Price (FCFA)"], ["Notebook", "250"], ["Pen", "100"], ["Ruler", "150"], ["Bag", "3,500"]], [150, 120], rh=28, size=14)
tot = 4 * 250 + 3 * 100 + 150
assert tot == 1450 and 2000 - 1450 == 550
build(ch, "money", "Money: FCFA, bills and change", 25,
      ["Add and subtract amounts in FCFA.", "Work out the total cost and the change.", "Make a simple bill."],
      [("key", "definition", "FCFA", "In Cameroon we use the **CFA franc (FCFA)**. Coins and notes are, for example, 100, 500 FCFA (coins) and 1,000, 2,000, 5,000 and 10,000 FCFA (notes). A **bill** (or receipt) lists the items, the price of each and the **total**. **Change** = money given − total cost."),
       ("fig", fig, "A price list at a school shop.", "A table of items and prices: notebook 250 FCFA, pen 100 FCFA, ruler 150 FCFA, bag 3,500 FCFA."),
       ("key", "methode", "Bills and change", "1. Multiply **quantity × price** for each item.\n2. **Add** the results to get the total.\n3. **Subtract** the total from the money given to find the change.\nProfit = selling price − cost price (introduced later)."),
       ("key", "pieges", "Common mistakes", "- Forgetting to multiply by the quantity.\n- Giving change that is bigger than the money paid.\n- Forgetting the unit FCFA.\n- Adding wrongly in the thousands column.")],
      [("ex", "Example 1", "Ndip buys 4 notebooks, 3 pens and 1 ruler using the price list. How much does he pay?", ["Notebooks: 4 × 250 = 1,000. Pens: 3 × 100 = 300. Ruler: 150.", "Total: 1,000 + 300 + 150 = 1,450 FCFA."], "1,450 FCFA", fig),
       ("ex", "Example 2", "He pays with a 2,000 FCFA note. What is his change?", ["Change = 2,000 − 1,450.", "2,000 − 1,450 = 550."], "550 FCFA")],
      [N("application", "5 pens cost 100 FCFA each. How much in all?", 500, "5 × 100 = 500.", unit="FCFA"),
       N("application", "Change from a 1,000 FCFA note after buying goods for 650 FCFA?", 350, "1,000 − 650 = 350.", unit="FCFA"),
       M("application", "Which note is worth the most?", "10,000 FCFA", ["5,000 FCFA", "2,000 FCFA", "1,000 FCFA"], "10,000 is the largest."),
       N("approfondissement", "A bag costs 3,500 FCFA. Ako has 2,750 FCFA. How much more does she need?", 750, "3,500 − 2,750 = 750.", unit="FCFA"),
       TF("approfondissement", "If a total is 1,450 FCFA and you pay 1,500 FCFA, the change is 50 FCFA.", True, "1,500 − 1,450 = 50."),
       P("Mama Sali sells 6 bunches of bananas at 400 FCFA each and 10 pieces of puff-puff at 50 FCFA each.",
         [PN("Money from the bananas?", 6 * 400, "6 × 400 = 2,400.", unit="FCFA"),
          PN("Money from the puff-puff?", 10 * 50, "10 × 50 = 500.", unit="FCFA"),
          PN("Total money?", 6 * 400 + 10 * 50, "2,400 + 500 = 2,900.", unit="FCFA", points=2)])],
      [("The money used in Cameroon is the...", "CFA franc (FCFA)", ["dollar", "naira", "euro"], "FCFA is the currency of Cameroon."),
       ("3 books at 500 FCFA each cost...", "1,500 FCFA", ["503 FCFA", "1,000 FCFA", "2,000 FCFA"], "3 × 500."),
       ("Change = ...", "money given − total cost", ["total cost − money given", "money given + total", "total × money"], "What is returned to you."),
       ("A list of items, prices and the total is a...", "bill", ["map", "poem", "pattern"], "Also called a receipt."),
       ("If you pay 5,000 FCFA for goods of 3,200 FCFA, the change is...", "1,800 FCFA", ["8,200 FCFA", "2,800 FCFA", "1,200 FCFA"], "5,000 − 3,200.")],
      ["Coins/notes list given as typical examples (verify current denominations). Prices are illustrative."])

# ======================================================================= 5. geometry
ch = p.chapter("geometry", "Perimeter, area, angles and shapes", REF)

fig = shapes([RECT(60, 30, 200, 100, fill="lightyellow", width=3), T(160, 20, "12 m", 14, bold=True), T(272, 85, "8 m", 14, anchor="start", bold=True), T(160, 85, "Area = 12 × 8", 14)], 400, 160)
assert 2 * (12 + 8) == 40 and 12 * 8 == 96
build(ch, "perimeter-area", "Perimeter and area of rectangles and squares", 30,
      ["Find the perimeter of a rectangle, a square and other shapes.", "Find the area of a rectangle and a square.", "Use the right units (m, m²)."],
      [("key", "definition", "Perimeter and area", "The **perimeter** is the **distance around** a shape: add all the sides. The **area** is the **space inside** a shape: we count square units (cm², m²). A rectangle with length $l$ and width $w$ has perimeter $2 \\times (l + w)$ and area $l \\times w$. A square with side $s$ has perimeter $4 \\times s$ and area $s \\times s$."),
       ("fig", fig, "A rectangle 12 m long and 8 m wide.", "A rectangle labelled 12 m on the top side and 8 m on the right side. Inside it is written area equals 12 times 8."),
       ("key", "retenir", "Units", "Perimeter is a **length**: cm, m, km. Area is in **square units**: cm², m², km². A square metre (1 m²) is the area of a square with sides of 1 m. A football pitch or a plot of land is measured in m²."),
       ("key", "pieges", "Common mistakes", "- Writing the area in metres instead of square metres.\n- Adding only two sides for the perimeter: add all four (or double the sum of length and width).\n- Multiplying for the perimeter or adding for the area.\n- Mixing up the units: change cm to m first.")],
      [("ex", "Example 1", "A rectangular school garden is 12 m long and 8 m wide. Find its perimeter and area.", ["Perimeter = 2 × (12 + 8) = 2 × 20 = 40 m.", "Area = 12 × 8 = 96 m²."], "Perimeter 40 m; area 96 m².", fig),
       ("ex", "Example 2", "A square classroom has a side of 7 m. What is its area?", ["Area of a square = side × side.", "7 × 7 = 49."], "49 m²")],
      [N("application", "Perimeter of a rectangle 9 cm by 5 cm.", 2 * (9 + 5), "2 × 14 = 28.", unit="cm"),
       N("application", "Area of a square of side 6 m.", 36, "6 × 6.", unit="m²"),
       M("application", "Which unit is used for area?", "m²", ["m", "kg", "litre"], "Area uses square units."),
       N("approfondissement", "A rectangle has area 60 m² and length 10 m. What is its width?", 6, "60 ÷ 10 = 6.", unit="m"),
       TF("approfondissement", "A square with a perimeter of 20 cm has sides of 5 cm.", True, "20 ÷ 4 = 5."),
       P("Mr Tabi has a rectangular plot in Bali, 25 m long and 20 m wide. He wants to put a fence around it and to plant maize in it.",
         [PN("What length of fence does he need?", 2 * (25 + 20), "2 × 45 = 90.", unit="m"),
          PN("What is the area of the plot?", 25 * 20, "25 × 20 = 500.", unit="m²"),
          PN("The fence costs 1,500 FCFA per metre. What is the cost?", 90 * 1500, "90 × 1,500 = 135,000.", unit="FCFA", points=2)])],
      [("The distance around a shape is its...", "perimeter", ["area", "volume", "angle"], "Add all sides."),
       ("Area of a rectangle 5 m by 4 m is...", "20 m²", ["18 m", "9 m²", "20 m"], "5 × 4 = 20 square metres."),
       ("Perimeter of a square with side 3 cm is...", "12 cm", ["9 cm", "6 cm", "9 cm²"], "4 × 3."),
       ("The area is measured in...", "square units", ["metres only", "litres", "kilograms"], "For example cm² or m²."),
       ("A rectangle 10 cm by 3 cm has perimeter...", "26 cm", ["30 cm", "13 cm", "36 cm"], "2 × (10 + 3).")],
      ["Area of rectangle and square only; triangles and other shapes belong to later classes."])

fig = shapes([LINE(60, 140, 200, 140, width=3), LINE(60, 140, 130, 70, width=3), ANGLE(60, 140, 0, 45, r=28, label="45°"),
              LINE(260, 140, 380, 140, width=3), LINE(260, 140, 260, 40, width=3), ANGLE(260, 140, 0, 90, r=24, right=True, label=None), T(300, 100, "90°", 14, color="red")], 400, 175)
build(ch, "angles", "Angles: right, acute and obtuse", 25,
      ["Say what an angle is and name its parts.", "Tell right, acute, obtuse and straight angles apart.", "Use a right angle as a test (set square)."],
      [("key", "definition", "Angles", "An **angle** is formed where two lines (arms) meet at a point (the vertex). We measure angles in **degrees (°)**. A **right angle** is **90°** (a square corner). An **acute angle** is **less than 90°**. An **obtuse angle** is **more than 90° and less than 180°**. A **straight angle** is **180°**."),
       ("fig", fig, "An acute angle (45°) and a right angle (90°).", "Two angles. The first has a 45 degree arc between a horizontal line and a slanted line. The second is a right angle with a square mark and 90 degrees."),
       ("key", "retenir", "Finding angles around us", "The corner of a page, a door or a window is a **right angle**. The hands of a clock at 3 o'clock make a right angle. A quarter turn is 90°, a half turn is 180°, a full turn is 360°. Test an angle by placing the corner of a sheet of paper on it."),
       ("key", "pieges", "Common mistakes", "- Thinking that a longer arm means a bigger angle: the **opening** matters, not the length of the lines.\n- Calling 120° acute: it is more than 90°, so it is **obtuse**.\n- Forgetting that a straight line is also an angle (180°).")],
      [("ex", "Example 1", "Say what kind of angle: 30°, 90°, 120°, 180°.", ["30° is less than 90°: acute. 90° is a right angle.", "120° is more than 90° but less than 180°: obtuse. 180° is a straight angle."], "acute, right, obtuse, straight.", None),
       ("ex", "Example 2", "How many degrees does the minute hand turn from 12 to 3 on the clock?", ["From 12 to 3 is a quarter of a whole turn.", "A whole turn is 360°, so 360 ÷ 4 = 90°."], "90°", clock(3, 0, label="3:00"))],
      [M("application", "An angle of 45° is...", "acute", ["right", "obtuse", "straight"], "It is less than 90°."),
       N("application", "How many degrees are in a right angle?", 90, "A square corner is 90°.", unit="°"),
       M("application", "Which angle is obtuse?", "135°", ["35°", "90°", "180°"], "Between 90° and 180°."),
       N("approfondissement", "How many degrees are in a half turn?", 180, "360 ÷ 2.", unit="°"),
       MA("approfondissement", "Match the angle with its name.", [("60°", "acute"), ("90°", "right angle"), ("150°", "obtuse"), ("180°", "straight angle")], "Compare with 90°."),
       P("Look at the clock at different times.",
         [PM("At 3:00, the angle between the hands is a...", "right angle", ["acute angle", "obtuse angle", "straight angle"], "Quarter turn = 90°."),
          PM("At 6:00, the angle between the hands is a...", "straight angle", ["right angle", "acute angle", "obtuse angle"], "Half turn = 180°."),
          PN("How many degrees is a quarter of a full turn?", 90, "360 ÷ 4 = 90.", unit="°", points=2)])],
      [("An angle less than 90° is called...", "acute", ["obtuse", "right", "straight"], "Acute angles are small."),
       ("A square corner is...", "90°", ["45°", "180°", "360°"], "A right angle."),
       ("The unit for measuring angles is the...", "degree", ["metre", "gram", "litre"], "Written °."),
       ("A full turn is...", "360°", ["90°", "180°", "100°"], "All the way round."),
       ("Which of these is obtuse?", "100°", ["80°", "90°", "20°"], "More than 90°, less than 180°.")],
      [])

fig = shapes([RECT(20, 20, 60, 60, fill="lightblue"), T(50, 100, "square", 13), RECT(110, 25, 90, 50, fill="lightyellow"), T(155, 100, "rectangle", 13),
              POLY([250, 80, 290, 80, 270, 25], fill="lightgreen"), T(270, 100, "triangle", 13), CIRCLE(350, 50, 30, fill="lightorange"), T(350, 100, "circle", 13)], 400, 125)
fig3 = shapes(cuboid(30, 20, 70, 50, 25, fill="lightblue") + [T(70, 120, "cuboid", 13)] +
              [RECT(150, 40, 55, 55, fill="lightgreen"), POLY([150, 40, 170, 20, 225, 20, 205, 40], fill="lightyellow"), POLY([205, 40, 225, 20, 225, 75, 205, 95], fill="lightorange"), T(190, 120, "cube", 13)] +
              [CIRCLE(310, 70, 38, fill="lightorange"), T(310, 130, "sphere", 13)], 400, 145)
build(ch, "shapes", "2-D and 3-D shapes", 30,
      ["Name and describe common 2-D shapes (sides, corners).", "Name common 3-D shapes (faces, edges, vertices).", "Find shapes in the things around us."],
      [("key", "definition", "2-D shapes", "A **2-D (flat) shape** has length and width. A **square** has 4 equal sides and 4 right angles. A **rectangle** has 4 sides (opposite sides equal) and 4 right angles. A **triangle** has 3 sides and 3 corners. A **circle** is round with no corners. A **pentagon** has 5 sides and a **hexagon** has 6."),
       ("fig", fig, "Four flat shapes.", "A square, a rectangle, a triangle and a circle, each with a name below."),
       ("key", "definition", "3-D shapes", "A **3-D (solid) shape** has length, width and height. A **cube** has 6 square faces, 12 edges and 8 vertices (corners). A **cuboid** has 6 rectangular faces, 12 edges and 8 vertices (a box). A **sphere** is round like a ball. A **cylinder** has two flat circles and one curved face. A **cone** has a circle and a point."),
       ("fig", fig3, "A cuboid, a cube and a sphere.", "Three solid shapes: a cuboid like a box, a cube and a sphere, each with a name."),
       ("key", "pieges", "Common mistakes", "- Saying that a square is not a rectangle: a square is a special rectangle with 4 equal sides.\n- Calling a flat shape (circle) a solid shape (sphere).\n- Counting the faces of a cube as 4: there are 6.")],
      [("ex", "Example 1", "Name the shape of: a football, a tin of milk, a dice, a door.", ["A football is round: sphere. A tin of milk is a cylinder.", "A dice is a cube. A door is a rectangle (its face)."], "sphere, cylinder, cube, rectangle.", None),
       ("ex", "Example 2", "How many faces, edges and vertices does a cuboid have?", ["Faces: top, bottom and four sides = 6.", "Edges = 12 and vertices (corners) = 8."], "6 faces, 12 edges, 8 vertices.")],
      [N("application", "How many sides has a hexagon?", 6, "Hex means six."),
       M("application", "What 3-D shape is a cooking gas bottle most like?", "cylinder", ["cube", "cone", "square"], "It has two circle ends and a curved side."),
       M("application", "Which shape has no corners?", "circle", ["square", "triangle", "rectangle"], "A circle is round."),
       N("approfondissement", "How many vertices has a cube?", 8, "A cube has 8 corners."),
       TF("approfondissement", "A square is a special kind of rectangle.", True, "It has four right angles and its sides are all equal."),
       P("Neba studies the objects in her classroom: a ball, a box of chalk, a blackboard and a tin.",
         [PM("A box of chalk is most like a...", "cuboid", ["cylinder", "sphere", "cone"], "It has 6 rectangular faces."),
          PM("The face of the blackboard is a...", "rectangle", ["circle", "triangle", "sphere"], "It has 4 right angles."),
          PN("How many edges has the box of chalk?", 12, "A cuboid has 12 edges.", points=2)])],
      [("A triangle has how many sides?", "3", ["4", "5", "6"], "Tri means three."),
       ("A dice is shaped like a...", "cube", ["sphere", "cone", "cylinder"], "It has 6 square faces."),
       ("A cuboid has how many faces?", "6", ["4", "8", "12"], "Top, bottom and 4 sides."),
       ("Which 3-D shape is like a ball?", "sphere", ["cube", "square", "cone"], "Round all over."),
       ("How many sides has a pentagon?", "5", ["4", "6", "8"], "Penta means five.")],
      ["Cone/cylinder given at a basic level; face/edge/vertex counts for cube and cuboid are standard."])

# ======================================================================= 6. data
ch = p.chapter("data", "Data handling", REF)

vals = [("Mon", 14), ("Tue", 18), ("Wed", 11), ("Thu", 16), ("Fri", 9)]
fig = safe_bars([(a, b) for a, b in vals], unit="Kg of tomatoes sold", w=400, h=250)
tot = sum(b for _, b in vals); assert tot == 68
build(ch, "pictograph-bar", "Tables, pictographs and bar charts", 30,
      ["Read information from a table, a pictograph and a bar chart.", "Draw a simple bar chart from a table.", "Answer questions about data (most, least, total, difference)."],
      [("key", "definition", "Data and charts", "**Data** is information we collect, for example the number of kg of tomatoes sold each day. We can show data in a **table**, in a **pictograph** (pictures, with a key such as 1 picture = 2 kg) or in a **bar chart** (bars: the taller the bar, the bigger the number)."),
       ("fig", fig, "Kilograms of tomatoes sold in a market in one week.", "A bar chart: Monday 14, Tuesday 18, Wednesday 11, Thursday 16, Friday 9."),
       ("key", "methode", "Reading a bar chart", "1. Read the **title** and the **labels**.\n2. Look at the **scale** on the side: how much is each line worth?\n3. Read the value at the top of each bar.\n4. Answer: **most** = tallest bar, **least** = shortest bar, **total** = add all bars, **difference** = subtract."),
       ("key", "pieges", "Common mistakes", "- Not reading the scale: the numbers on the side may go up in 2s or 5s.\n- Forgetting the key of a pictograph: one picture can stand for more than 1.\n- Saying that the longest word in the title is the most: read the data.")],
      [("ex", "Example 1", "Using the bar chart, on which day were the most tomatoes sold and how many in all?", ["The tallest bar is Tuesday: 18 kg.", "Total = 14 + 18 + 11 + 16 + 9 = 68 kg."], "Tuesday (18 kg); total 68 kg.", None),
       ("ex", "Example 2", "A pictograph shows that 1 picture = 5 mangoes. Ako has 6 pictures. How many mangoes?", ["Use the key: each picture is 5 mangoes.", "6 × 5 = 30."], "30 mangoes", count(6, "circle", "orange", 6, 400, 110))],
      [M("application", "On which day were the fewest tomatoes sold?", "Friday", ["Monday", "Tuesday", "Thursday"], "Friday has the shortest bar: 9 kg."),
       N("application", "How many kg were sold on Monday and Tuesday together?", 14 + 18, "14 + 18 = 32.", unit="kg"),
       N("application", "How many more kg were sold on Tuesday than on Wednesday?", 18 - 11, "18 − 11 = 7.", unit="kg"),
       TF("approfondissement", "In a pictograph, one picture always stands for exactly 1 thing.", False, "The key tells how many each picture is worth."),
       N("approfondissement", "In a pictograph 1 picture = 20 pupils. There are 7 pictures. How many pupils?", 140, "7 × 20 = 140."),
       P("A class of 40 pupils chose their favourite food: ndolé 12, rice 15, fufu corn 8, plantain 5.",
         [PN("Check the total: how many pupils voted?", 12 + 15 + 8 + 5, "12 + 15 + 8 + 5 = 40."),
          PM("Which food is the most popular?", "rice", ["ndolé", "fufu corn", "plantain"], "Rice has 15 votes."),
          PN("How many more pupils chose rice than plantain?", 15 - 5, "15 − 5 = 10.", points=2)])],
      [("In a bar chart the tallest bar shows...", "the biggest number", ["the smallest number", "the total", "the title"], "Taller = bigger."),
       ("1 picture = 10 books. 4 pictures mean...", "40 books", ["4 books", "14 books", "400 books"], "4 × 10."),
       ("Collected information is called...", "data", ["a picture", "a story", "a shape"], "Data can be shown in tables and charts."),
       ("To find a total from a chart we...", "add all the values", ["subtract the values", "multiply the bars", "divide by 2"], "Add every bar."),
       ("Tomatoes sold on Mon, Tue, Wed: 14, 18, 11 kg. Total?", "43 kg", ["33 kg", "44 kg", "13 kg"], "14 + 18 + 11 = 43.")],
      ["Data are invented for teaching."])

p.write()
