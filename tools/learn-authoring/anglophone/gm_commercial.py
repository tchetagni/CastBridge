from _kit import *

def build(p):
    ch = p.chapter("commercial", "Commercial arithmetic: profit, interest, VAT and exchange", "O Level Mathematics — Commercial arithmetic (to be checked)")

    # ------------------------------------------------------------ profit & loss
    prof = (28000 - 25000) / 25000 * 100; assert abs(prof - 12) < 1e-9
    disc = 40000 * 0.85; assert disc == 34000
    cp = 36000 / 1.2; assert abs(cp - 30000) < 1e-9
    L = lesson(ch, "profit-loss", "Profit, loss and discount",
        ["Calculate profit, loss and percentage profit or loss.", "Calculate selling price after a discount or mark-up.", "Find the cost price from a selling price and a percentage."],
        [("formule", "Profit and loss", "Profit = selling price (SP) − cost price (CP) when SP > CP; loss = CP − SP when CP > SP. Percentages are always taken on the **cost price**:", "\\%\\text{ profit} = \\frac{\\text{profit}}{\\text{CP}} \\times 100"),
         ("methode", "Discount and reverse percentages", "- **Discount**: new price = marked price × (1 − rate); a 15% discount means × 0.85.\n- **Reverse**: if SP is 120% of CP, then CP = SP ÷ 1.2. Divide by the multiplier; never subtract the percentage from the SP.\n- **Commission** is a percentage of the sales made."),
         ("pieges", "Common mistakes", "- Calculating percentage profit on the selling price instead of the cost price.\n- Reverse percentage: 20% profit on CP does not mean CP = SP − 20% of SP.\n- A 10% increase followed by a 10% decrease does not return to the start (×1.1 × 0.9 = 0.99).")],
        [("Example 1: percentage profit", "A trader in Kumba buys a bunch of plantains for 25 000 FCFA and sells it for 28 000 FCFA. Find the percentage profit.",
          ["Profit = 28 000 − 25 000 = 3 000 FCFA.", "Percentage profit = 3 000 ÷ 25 000 × 100 = 12%."], "**12% profit.**"),
         ("Example 2: reverse percentage", "A shopkeeper sells a radio for 36 000 FCFA, making a profit of 20% on the cost price. Find the cost price.",
          ["SP = 120% of CP, multiplier 1.2.", "CP = 36 000 ÷ 1.2 = 30 000 FCFA.", "Check: 20% of 30 000 = 6 000 and 30 000 + 6 000 = 36 000."], "**30 000 FCFA.**")],
        [N("A shirt marked 40 000 FCFA is sold with a 15% discount. Find the price paid.", 34000, "0.85 × 40 000 = 34 000 FCFA.", unit="FCFA"),
         M("Bought for 8 000 FCFA, sold for 6 000 FCFA. The percentage loss is:", "25%", ["33.3%", "2 000%", "75%"], "Loss 2 000 ÷ 8 000 × 100 = 25% (on the cost price)."),
         N("A seller makes a profit of 4 500 FCFA on goods costing 30 000 FCFA. Find the percentage profit.", 15, "4 500 ÷ 30 000 × 100 = 15%.", unit="%")],
        [N("After a 20% discount a bag costs 12 000 FCFA. Find the marked price.", 15000, "12 000 is 80% of the marked price: 12 000 ÷ 0.8 = 15 000 FCFA.", unit="FCFA"),
         M("A price is increased by 10% and then decreased by 10%. The overall change is:", "1% decrease", ["No change", "1% increase", "10% decrease"], "1.1 × 0.9 = 0.99, a 1% decrease.")],
        P("A shopkeeper in Douala buys 50 packets of biscuits at 400 FCFA each and sells all of them at 520 FCFA each.",
          [pn("Find the total cost.", 20000, "50 × 400 = 20 000 FCFA.", unit="FCFA", pts=1),
           pn("Find the total profit.", 6000, "50 × 520 = 26 000; 26 000 − 20 000 = 6 000 FCFA.", unit="FCFA", pts=1),
           pn("Find the percentage profit.", 30, "6 000 ÷ 20 000 × 100 = 30%.", unit="%", pts=2)]),
        [("Profit % is calculated on:", "the cost price", ["the selling price", "the marked price", "the discount"], "Always on the cost price."),
         ("CP 5 000, SP 6 000. Profit:", "1 000 FCFA", ["5 000 FCFA", "20 FCFA", "11 000 FCFA"], "SP − CP."),
         ("A 25% discount multiplies the price by:", "0.75", ["0.25", "1.25", "0.025"], "100% − 25% = 75%."),
         ("SP is 150% of CP. The profit is:", "50%", ["150%", "15%", "5%"], "150% − 100%."),
         ("An item sold at 90% of cost made a:", "10% loss", ["10% profit", "90% loss", "no loss"], "100% − 90% = 10% loss.")],
        ill=(bars([("Cost", 25000, "blue"), ("Selling", 28000, "green")], unit="FCFA"), "Cost price and selling price (profit 3 000 FCFA)", "Two bars showing a cost of 25 000 FCFA and a selling price of 28 000 FCFA"), minutes=25)

    # ------------------------------------------------------------ interest
    si = 200000 * 5 * 3 / 100; assert si == 30000
    ci = 100000 * 1.1 ** 2; assert abs(ci - 121000) < 1e-6
    ci3 = 500000 * 1.06 ** 3; 
    L = lesson(ch, "interest", "Simple and compound interest",
        ["Use the simple interest formula I = PRT/100.", "Use the compound interest formula for amounts after n years.", "Compare simple and compound interest."],
        [("formule", "Simple interest", "Interest is calculated on the original sum P (the principal) at rate R% per year for T years:", "I = \\frac{P R T}{100} \\qquad A = P + I"),
         ("formule", "Compound interest", "Interest is added to the principal each year, so it earns interest too. After n years the amount is:", "A = P\\left(1 + \\frac{R}{100}\\right)^n \\qquad \\text{interest} = A - P"),
         ("pieges", "Common mistakes", "- Using the compound formula for the interest only: $A$ is the **amount** (principal + interest). Subtract P to get the interest.\n- Using months as years: for 6 months T = 0.5.\n- Writing R = 0.05 in $\\frac{PRT}{100}$: use R = 5.")],
        [("Example 1: simple interest", "Find the simple interest on 200 000 FCFA for 3 years at 5% per annum.",
          ["Use I = PRT/100 with P = 200 000, R = 5, T = 3.", "I = 200 000 × 5 × 3 ÷ 100 = 30 000 FCFA."], "**30 000 FCFA** (amount 230 000 FCFA)."),
         ("Example 2: compound interest", "A cooperative in Bamenda invests 100 000 FCFA at 10% compound interest per year. Find the amount after 2 years.",
          ["Multiplier for 10% growth: 1.1.", "A = 100 000 × 1.1² = 100 000 × 1.21 = 121 000 FCFA.", "Check by year: 110 000 after year 1; 110 000 × 1.1 = 121 000."], "**121 000 FCFA** (interest 21 000 FCFA).")],
        [N("Find the simple interest on 80 000 FCFA at 6% per year for 2 years.", 9600, "80 000 × 6 × 2 ÷ 100 = 9 600 FCFA.", unit="FCFA"),
         M("Which formula gives the amount after n years of compound interest?", "$P(1 + \\frac{R}{100})^n$", ["$P(1 + \\frac{R n}{100})$", "$P \\times R^n$", "$\\frac{PRn}{100}$"], "Compound interest multiplies by (1 + R/100) each year; the second option is the simple-interest amount."),
         N("What multiplier is used for a growth of 8% per year?", 1.08, "1 + 8/100 = 1.08.", tol=0.0001)],
        [N("Find the compound interest on 500 000 FCFA at 6% per annum for 3 years (to the nearest FCFA).", round(ci3 - 500000), "A = 500 000 × 1.06³ = %.0f; interest = A − P = %.0f FCFA." % (ci3, ci3 - 500000), tol=1, unit="FCFA"),
         N("At what simple interest rate (% per year) does 150 000 FCFA earn 18 000 FCFA in 2 years?", 6, "R = 100 I ÷ (PT) = 1 800 000 ÷ 300 000 = 6%.", unit="%")],
        P("Mrs Tabi deposits 400 000 FCFA in a savings account paying 5% interest per year.",
          [pn("Simple interest for 4 years (FCFA).", 80000, "400 000 × 5 × 4 ÷ 100 = 80 000.", unit="FCFA", pts=1),
           pn("Compound-interest amount after 2 years (FCFA).", 400000 * 1.05 ** 2, "400 000 × 1.1025 = 441 000.", tol=1, unit="FCFA", pts=2),
           pm("After 2 years, compared with simple interest, the compound interest is:", "greater", ["smaller", "equal", "impossible to tell"], "Simple: 40 000; compound: 41 000. Compound is greater since interest earns interest.", 1)]),
        [("Simple interest on 10 000 at 10% for 1 year:", "1 000", ["100", "10 000", "11 000"], "10 000 × 10 ÷ 100."),
         ("Compound interest uses interest on:", "principal plus past interest", ["the principal only", "the final amount only", "nothing"], "That is the definition."),
         ("T for 18 months is:", "1.5", ["18", "0.18", "15"], "18 ÷ 12 = 1.5 years."),
         ("100 at 20% compound for 2 years gives:", "144", ["140", "120", "104"], "100 × 1.44."),
         ("The 'amount' means:", "principal + interest", ["interest only", "principal only", "rate × time"], "A = P + I.")],
        ill=(bars([("Year 0", 100, "blue"), ("Year 1", 110, "green"), ("Year 2", 121, "orange"), ("Year 3", 133.1, "red")], unit="×1 000 FCFA"), "100 000 FCFA at 10% compound interest", "Bars showing the amount growing from 100 000 to 133 100 FCFA over three years"), minutes=25,
        notes=["Compound interest with n up to 3 is typical: check the exact limit in the syllabus."])

    # ------------------------------------------------------------ VAT & exchange
    vat = 80000 * 0.1925; assert abs(vat - 15400) < 1e-6
    eur = 65595.7 / 655.957; assert abs(eur - 100) < 1e-6
    usd = 120 * 600; assert usd == 72000
    L = lesson(ch, "vat-exchange", "VAT, taxes and exchange rates",
        ["Calculate VAT-inclusive and VAT-exclusive prices.", "Convert between FCFA and other currencies given an exchange rate.", "Solve problems with bills, rates and wages."],
        [("definition", "Value Added Tax (VAT)", "VAT is a percentage added to the price of goods and services. In these exercises the rate is taken as **19.25%** (the usual Cameroon rate), so price including VAT = price before VAT × 1.1925. To find the price **before** VAT, divide by 1.1925."),
         ("formule", "Exchange rates", "An exchange rate tells how much of one currency equals one unit of another. The FCFA is fixed to the euro: 1 euro = 655.957 FCFA. Other rates change daily; in exercises the rate is always given.", "\\text{FCFA} = \\text{foreign amount} \\times \\text{rate}"),
         ("pieges", "Common mistakes", "- To remove VAT, divide by 1.1925; do not subtract 19.25% of the VAT-inclusive price.\n- Multiplying when you should divide in a currency conversion: always ask whether the answer should be bigger (in FCFA) or smaller.\n- Mixing the rate and its inverse (FCFA per dollar, not dollars per FCFA).")],
        [("Example 1: adding VAT", "A generator costs 80 000 FCFA before VAT. VAT is 19.25%. Find the VAT and the price including VAT.",
          ["VAT = 19.25% of 80 000 = 0.1925 × 80 000 = 15 400 FCFA.", "Price including VAT = 80 000 + 15 400 = 95 400 FCFA."], "**VAT 15 400 FCFA; price 95 400 FCFA.**"),
         ("Example 2: exchange", "Take 1 USD = 600 FCFA (an assumed rate). (a) Convert 120 USD to FCFA. (b) How many dollars can be bought with 90 000 FCFA?",
          ["(a) 120 × 600 = 72 000 FCFA.", "(b) 90 000 ÷ 600 = 150 USD.", "Check: 150 USD × 600 = 90 000 FCFA."], "**(a) 72 000 FCFA; (b) 150 USD.**")],
        [N("VAT at 19.25% is added to an item priced 20 000 FCFA before VAT. Find the VAT.", 3850, "0.1925 × 20 000 = 3 850 FCFA.", unit="FCFA"),
         N("How many FCFA are equal to 50 euros? (1 euro = 655.957 FCFA; round to the nearest FCFA.)", round(50 * 655.957), "50 × 655.957 = 32 797.85, so 32 798 FCFA.", tol=1, unit="FCFA"),
         M("A price including 19.25% VAT is 119 250 FCFA. The price before VAT is:", "100 000 FCFA", ["96 250 FCFA", "99 000 FCFA", "142 200 FCFA"], "119 250 ÷ 1.1925 = 100 000. Subtracting 19.25% of 119 250 would be wrong.")],
        [N("Using 1 USD = 600 FCFA, how many dollars is a laptop priced 450 000 FCFA?", 750, "450 000 ÷ 600 = 750 USD.", unit="USD"),
         N("A fridge costs 238 500 FCFA including VAT at 19.25%. Find the price before VAT.", 200000, "238 500 ÷ 1.1925 = 200 000 FCFA.", unit="FCFA")],
        P("An electrician in Yaoundé charges 6 000 FCFA per hour plus VAT at 19.25%. He works 5 hours.",
          [pn("Find the charge before VAT.", 30000, "5 × 6 000 = 30 000 FCFA.", unit="FCFA", pts=1),
           pn("Find the VAT to be added.", 5775, "0.1925 × 30 000 = 5 775 FCFA.", unit="FCFA", pts=1),
           pn("The customer pays in euros (1 euro = 655.957 FCFA). Find the amount for the total bill, to the nearest euro.", round(35775 / 655.957), "35 775 ÷ 655.957 = 54.5 (approx), so about 55 euros.", tol=1, unit="euros", pts=2)]),
        [("VAT-inclusive price = price before VAT ×", "1.1925", ["0.1925", "0.8075", "19.25"], "100% + 19.25%."),
         ("1 USD = 600 FCFA. 3 USD =", "1 800 FCFA", ["200 FCFA", "603 FCFA", "18 000 FCFA"], "3 × 600."),
         ("To convert FCFA into dollars you:", "divide by the rate", ["multiply by the rate", "add the rate", "subtract"], "Fewer dollars than FCFA."),
         ("VAT on 10 000 FCFA at 19.25% is:", "1 925 FCFA", ["192.5 FCFA", "19 250 FCFA", "8 075 FCFA"], "10 000 × 0.1925."),
         ("1 euro is fixed at about:", "656 FCFA", ["100 FCFA", "1 000 FCFA", "6 560 FCFA"], "The CFA franc peg is 655.957 per euro.")],
        ill=(shapes([RECT(40, 30, 200, 40, fill="lightblue"), T(140, 56, "80 000 (before VAT)", 14), RECT(240, 30, 60, 40, fill="lightorange"), T(270, 56, "15 400", 13),
                     LINE(40, 95, 300, 95, arrow="both"), T(170, 120, "95 400 FCFA including VAT", 15, bold=True)], 340, 140),
             "Price before VAT plus VAT", "A bar with the price before VAT and a smaller block for the VAT added, giving 95 400 FCFA"), minutes=25,
        notes=["VAT rate 19.25% (17.5% + 1.75% council surcharge) and the euro peg 655.957 are standard figures but rates can change: check against current law. The 600 FCFA per USD rate is an assumption stated in the exercises."])
