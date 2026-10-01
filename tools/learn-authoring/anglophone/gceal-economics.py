"""Generator: content/learn/gceal-economics (Economics - GCE A Level, Upper Sixth exam pack). Run: python3 gceal-economics.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _l6 import *

p = Pack("gceal-economics", "Economics — GCE A Level", level="Upper Sixth", subject="economie", cursus="secondary", exam="GCE-AL", series=[],
         description="Upper Sixth Economics for the GCE Advanced Level: aggregate demand and supply, the multiplier, inflation, unemployment, growth, money and banking (BEAC, CEMAC, FCFA), public finance, international trade, balance of payments, exchange rates, development and the Cameroonian economy, with a mock paper.",
         programRef="Cameroon GCE Board — Advanced Level Economics syllabus (Upper Sixth) — to be checked against the official texts")
NOTE_ORDER = "The split of topics between Lower and Upper Sixth is an editorial choice to be checked against the GCE Board syllabus."
NOTE_NUM = "All figures in worked examples and exercises are invented teaching numbers, not real statistics."

# =====================================================================  CH 1  AD / AS AND THE MULTIPLIER
c1 = p.chapter("macro", "Aggregate demand, aggregate supply and the multiplier", programRef="GCE A Level Economics — Aggregate demand and supply; multiplier (to be checked)")

# ---- 1 AD / AS
fig = plot(0, 10, 0, 12, curves=[("10-x", "blue", "AD"), ("2+x", "red", "SRAS")], points=[(4, 6, "E", "green")], segments=[(6, 0, 6, 12, "grey", True, "LRAS")], xlabel="real GDP", ylabel="price level", h=270)
ad = 600 + 150 + 200 + (100 - 120); assert ad == 930
sc1 = [
    ("Aggregate demand (AD) is", "C + I + G + (X − M)", ["C + S + T", "W + R + I + P", "M + X"], "Total planned spending on domestic output."),
    ("The AD curve slopes downward mainly because", "a lower price level raises real wealth, lowers interest rates and makes exports cheaper", ["producers supply less", "taxes fall automatically", "imports become scarce"], "Wealth, interest-rate and trade effects."),
    ("Which would shift AD to the right?", "A rise in government spending", ["A rise in taxes", "A fall in consumer confidence", "A rise in interest rates"], "G is a component of AD."),
    ("In the classical view the long-run aggregate supply curve is", "vertical at the full-employment level of output", ["horizontal", "upward-sloping", "downward-sloping"], "Output is determined by supply-side factors."),
    ("A rise in the cost of imported oil shifts SRAS", "to the left", ["to the right", "along the curve", "nowhere"], "Costs of production rise."),
]
lesson(c1, "ad-as", "Aggregate demand and aggregate supply",
       ["List the components of AD and explain why the AD curve slopes downward.", "Explain short-run and long-run aggregate supply and macroeconomic equilibrium.", "Analyse the effect of shifts in AD and AS on output and the price level."],
       [("definition", "Aggregate demand", "**Aggregate demand** is total planned spending on a country's output: **AD = C + I + G + (X − M)**. The AD curve slopes downward because a lower general price level (i) raises the real value of wealth, (ii) tends to lower interest rates and raise spending, and (iii) makes exports cheaper and imports dearer. A **shift** of AD follows changes in any component: confidence, interest rates, taxes, government spending, exchange rates, world incomes."),
        ("retenir", "Aggregate supply", "**Short-run aggregate supply (SRAS)** slopes upward: when prices rise faster than costs, firms supply more. It shifts with **costs** (wages, raw materials, oil prices), **taxes**, and **productivity**. **Long-run aggregate supply (LRAS)** is the economy's potential output: classical economists draw it **vertical** at full employment; Keynesians accept that it may be horizontal below full employment. LRAS shifts right with more resources, better technology, education, infrastructure."),
        ("pieges", "Shifts versus movements, and the price level", "The vertical axis is the **general price level**, not the price of one good. A rise in the price level moves you along AD (a contraction of planned spending), it does not shift it. When AD shifts right, output rises **and** the price level rises (in the upward-sloping range); only in the **Keynesian horizontal** range does output rise without inflation, and only on the **vertical** LRAS does AD raise prices only. Always say which part of the curve you are on."),
        ("retenir", "Equilibrium and shocks", "Macroeconomic equilibrium is where **AD = SRAS**. A **demand-side shock** (fall in exports) reduces output and employment; an **adverse supply shock** (oil price rise) causes **stagflation** (falling output and rising prices). In the long run, wages and prices adjust; classical economists expect output to return to LRAS, Keynesians stress that adjustment may be slow and policy may help.")],
       [("Worked example — AD", "In an economy C = 600, I = 150, G = 200, X = 100 and M = 120 (billion FCFA). Calculate AD.", ["AD = C + I + G + (X − M).", "Net exports = 100 − 120 = −20.", "AD = 600 + 150 + 200 − 20 = %d billion FCFA." % ad], "930 billion FCFA"),
        ("Worked example — shift of AD", "Investment rises because of increased business confidence. Describe the effect in the short run on a normal diagram.", ["I is a component of AD, so AD shifts right.", "Along the upward SRAS, output rises and the price level rises.", "Employment rises; inflationary pressure increases."], "Higher output and price level")],
       [M("Which of the following would shift AD to the left?", "A fall in business confidence", ["A tax cut", "A rise in exports", "A rise in government spending"], "Investment falls."),
        TF("A fall in the price level causes AD to shift right.", False, "It causes an expansion along AD; a shift needs a change in a component."),
        N("C = 800, I = 200, G = 300, X = 250, M = 300 (billion FCFA). Calculate AD.", 800 + 200 + 300 + 250 - 300, "800 + 200 + 300 + (250 − 300) = 1250.", unit="billion FCFA")],
       [O("Explain how an increase in oil prices might affect an oil-importing economy using an AD/AS diagram.", "Higher oil prices raise costs of production, so SRAS shifts left. Output falls and the price level rises (stagflation); unemployment may rise and real incomes fall.", ["SRAS shifts left (1)", "Price level up, output down (1)", "Stagflation/unemployment (1)"]),
        M("According to the classical view an increase in AD in the long run will", "raise the price level but not output", ["raise output only", "lower the price level", "raise output and employment permanently"], "LRAS is vertical.", tier="approfondissement")],
       P("An economy is initially in equilibrium with output below full employment. The government raises spending on roads.",
         [pm("Which curve shifts?", "AD shifts right", ["SRAS shifts left", "LRAS shifts left", "No curve shifts"], "G is a component of AD.", 1),
          pm("What happens to output in the short run?", "It rises", ["It falls", "It stays the same", "It cannot be predicted"], "Equilibrium moves along SRAS.", 1),
          po("Explain one possible long-run supply-side benefit of better roads.", "Lower transport costs and better access raise productivity and may shift LRAS to the right.", ["Productivity/costs (1)", "LRAS shifts (1)"], 2)]),
       sc1, (fig, "Aggregate demand and short-run aggregate supply, with a vertical long-run aggregate supply curve.", "A downward AD line, an upward SRAS line meeting at E and a vertical dashed LRAS line."),
       notes=[NOTE_NUM, "Keynesian and classical views are summarised briefly; check how deeply the syllabus expects the debate.", NOTE_ORDER])

# ---- 2 multiplier
mpc = 0.8; k = 1 / (1 - mpc); assert abs(k - 5) < 1e-9
Y = (200 + 100 + 100) / (1 - 0.8); assert abs(Y - 2000) < 1e-9
assert abs(200 + 0.8 * Y + 100 + 100 - Y) < 1e-9
dY = k * 50; assert abs(dY - 250) < 1e-9
k2 = 1 / (0.2 + 0.1 + 0.1); assert abs(k2 - 2.5) < 1e-9
rounds = [100 * mpc ** i for i in range(5)]
fig = bars([("Round 1", round(rounds[0], 1)), ("Round 2", round(rounds[1], 1)), ("Round 3", round(rounds[2], 1)), ("Round 4", round(rounds[3], 1)), ("Round 5", round(rounds[4], 1))], w=420, h=240)
sc2 = [
    ("The multiplier is calculated as", "1 ÷ (1 − MPC)", ["1 − MPC", "MPC ÷ (1 − MPC)", "MPC ÷ MPS"], "Equivalent to 1 ÷ MPS in a simple model."),
    ("If MPC = 0.75, the simple multiplier is", "4", ["0.75", "1.33", "3"], "1 ÷ 0.25 = 4."),
    ("Withdrawals from the circular flow include", "saving, taxes and imports", ["investment, government spending and exports", "wages and profits", "loans and grants"], "Leakages reduce the multiplier."),
    ("The multiplier is larger when", "the marginal propensity to withdraw is smaller", ["the marginal propensity to save is larger", "taxes are higher", "imports are a larger share"], "k = 1 ÷ marginal propensity to withdraw."),
    ("Equilibrium national income in the Keynesian model is where", "planned injections equal planned withdrawals", ["C = S", "G = T always", "X = M only"], "Aggregate demand equals output."),
]
lesson(c1, "multiplier", "The consumption function and the multiplier",
       ["Define MPC, MPS and the consumption function.", "Calculate equilibrium income and the multiplier.", "Explain how leakages reduce the multiplier and evaluate its usefulness."],
       [("definition", "Consumption function", "Consumption depends mainly on disposable income: **C = a + bY**, where **a** is autonomous consumption and **b** is the **marginal propensity to consume (MPC)**, the fraction of an extra unit of income spent. The **marginal propensity to save (MPS) = 1 − MPC** (with no taxes or imports). **APC** = C ÷ Y; it falls as income rises when a > 0."),
        ("formule", "The multiplier", "An initial injection (extra I, G or X) raises incomes, part of which is re-spent, creating further income in successive rounds. **k = 1 ÷ (1 − MPC) = 1 ÷ MPS**. When there are other leakages: k = 1 ÷ (MPS + MPT + MPM), where MPT is the marginal propensity to tax and MPM to import. Change in income = k × change in injection."),
        ("pieges", "Real-life limits of the multiplier", "The formula assumes spare capacity, constant prices and a fixed MPC. If the economy is near full employment the extra spending raises prices instead of output, so the multiplier is **smaller**. Time lags, crowding out, and **import-heavy economies** (large MPM) also reduce it. The multiplier works in **both directions**: a fall in exports or investment causes a **multiple fall** in income. Do not confuse MPC with APC."),
        ("retenir", "Equilibrium", "In the Keynesian cross, equilibrium Y satisfies **Y = C + I + G (+ X − M)**, equivalently planned **injections = planned withdrawals**. With C = a + bY, Y = (a + I + G) ÷ (1 − b). Government may use **fiscal policy** to raise AD in a recession, and the multiplier tells it how large the injection needs to be to close a **deflationary gap**.")],
       [("Worked example — equilibrium", "C = 200 + 0.8Y, I = 100, G = 100. Find equilibrium income and the multiplier.", ["Y = C + I + G = 200 + 0.8Y + 200.", "0.2Y = 400, so Y = %d." % Y, "k = 1 ÷ (1 − 0.8) = %d." % k], "Y = 2000; k = 5"),
        ("Worked example — change in income", "Investment rises by 50. With k = 5, find the change in income; then find k if MPS = 0.2, MPT = 0.1 and MPM = 0.1.", ["ΔY = k × ΔI = 5 × 50 = %d." % dY, "With all leakages: k = 1 ÷ (0.2 + 0.1 + 0.1) = 1 ÷ 0.4.", "= %.1f: the multiplier is smaller when taxes and imports leak out." % k2], "ΔY = 250; k = 2.5")],
       [M("If MPS is 0.25 the multiplier is", "4", ["0.25", "2.5", "1.25"], "1 ÷ 0.25 = 4."),
        TF("A higher marginal propensity to import increases the multiplier.", False, "More income leaks abroad, so the multiplier is smaller."),
        N("MPC = 0.9. Government spending rises by 20 (billion FCFA). Calculate the change in national income.", round(1 / (1 - 0.9) * 20), "k = 10; ΔY = 200.", unit="billion FCFA")],
       [O("Explain why the actual multiplier is usually smaller than the simple formula suggests.", "Households pay taxes and buy imports as well as save, so more income leaks from each round; near full employment extra demand raises prices, not output; and time lags and crowding out reduce the real effect.", ["Extra leakages (1)", "Prices rise near full employment (1)", "Lags/crowding out (1)"]),
        M("An economy has MPS = 0.1, MPT = 0.2 and MPM = 0.2. The multiplier is", "2", ["10", "5", "1.25"], "k = 1 ÷ 0.5 = 2.", tier="approfondissement")],
       P("In an economy C = 300 + 0.75Y, I = 150 and G = 150 (billion FCFA).",
         [pn("Calculate equilibrium income (billion FCFA).", (300 + 150 + 150) / 0.25, "Y = 600 ÷ 0.25 = 2400.", 0, None, 2),
          pn("What is the multiplier?", 4, "1 ÷ (1 − 0.75) = 4.", 0, None, 1),
          pn("Investment rises by 25. By how much does income rise?", 100, "4 × 25 = 100.", 0, "billion FCFA", 1),
          po("Why might the actual increase be smaller?", "Some of the extra income is taxed or spent on imports; the economy may be close to full capacity so prices rise.", ["Valid reason (1)"], 1)]),
       sc2, (fig, "Extra income generated in successive rounds of spending (initial injection 100, MPC 0.8); the total is 500.", "A bar chart with five decreasing bars: 100, 80, 64, 51.2 and 41."),
       notes=[NOTE_NUM, "Government and foreign sectors are included only through the leakage formula; a full four-sector model may be needed."])
assert (300 + 150 + 150) / 0.25 == 2400

# =====================================================================  CH 2  INFLATION, UNEMPLOYMENT, GROWTH
c2 = p.chapter("problems", "Inflation, unemployment and economic growth", programRef="GCE A Level Economics — Macroeconomic objectives: inflation, unemployment, growth (to be checked)")

# ---- 3 inflation
cpi_w = [0.4, 0.25, 0.15, 0.2]; cpi_i = [110, 105, 120, 102]; cpi = sum(a * b for a, b in zip(cpi_w, cpi_i)); assert abs(cpi - 108.65) < 1e-9
infl = (126 - 120) / 120 * 100; assert infl == 5
rw = (1.08 / 1.05 - 1) * 100; assert abs(rw - 2.857) < 0.01
fig = tabfig(["Cause", "What happens", "Example"], [["Demand-pull", "AD outpaces supply", "boom in spending"], ["Cost-push", "costs rise, SRAS falls", "oil price rise"], ["Imported", "import prices rise", "weaker currency"], ["Expectations", "wage-price spiral", "high wage claims"]], w=440, colw=[110, 170, 160], size=12, title="Causes of inflation")
sc3 = [
    ("Inflation is", "a sustained rise in the general price level", ["a rise in the price of one good", "a fall in the price level", "a rise in GDP"], "Measured by the rate of change of a price index."),
    ("The consumer price index (CPI) uses", "a weighted basket of goods and services bought by households", ["only food prices", "producer prices", "export prices only"], "Weights reflect spending shares."),
    ("Demand-pull inflation is caused by", "excess aggregate demand", ["higher wages only", "lower productivity only", "a stronger currency"], "Too much spending chasing too few goods."),
    ("Unanticipated inflation hurts", "savers and those on fixed incomes", ["borrowers at fixed rates", "debtors", "owners of real assets"], "The real value of money falls."),
    ("Deflation is", "a sustained fall in the general price level", ["a fall in the rate of inflation", "a rise in prices", "a rise in unemployment"], "A fall in the rate of inflation is disinflation."),
]
lesson(c2, "inflation", "Inflation",
       ["Define inflation and calculate a weighted price index and inflation rate.", "Explain the causes of demand-pull and cost-push inflation.", "Evaluate the effects of inflation and policies to control it."],
       [("definition", "Measuring inflation", "**Inflation** is a sustained rise in the general price level. It is measured by the percentage change in a **price index** such as the **consumer price index (CPI)**. The CPI follows the cost of a **basket** of goods and services typical of households, with **weights** equal to their share of spending. Inflation rate = (CPI₁ − CPI₀) ÷ CPI₀ × 100. **Disinflation** is a fall in the inflation rate; **deflation** is a fall in the price level."),
        ("retenir", "Causes", "**Demand-pull**: AD rises faster than the economy's capacity (excess spending, credit growth, fiscal expansion). **Cost-push**: rising costs (wages, oil, imported inputs, taxes) shift SRAS left. **Imported inflation**: a depreciating currency raises import prices. **Monetarists** emphasise growth of the money supply faster than output (the quantity theory MV = PT). Expectations can create a **wage-price spiral**."),
        ("pieges", "Effects need to be balanced", "Inflation **redistributes**: it benefits borrowers with fixed-rate debts and hurts savers and fixed-income households; it creates **menu costs** and uncertainty, can reduce export competitiveness (if higher than competitors') and may reduce investment. But **low, stable** inflation is not harmful and is usually targeted. Do not say inflation is always bad, and distinguish **nominal** from **real** values (real wage ≈ nominal wage growth − inflation)."),
        ("retenir", "Policies", "**Demand-side**: raise interest rates, cut government spending or raise taxes (reduce AD). **Supply-side**: improve productivity, competition, infrastructure. **Monetary rules** and independent central banks aim to control money growth. In a currency union such as the CEMAC, the common central bank (BEAC) sets monetary policy for all members. Price controls can give short-run relief but cause shortages.")],
       [("Worked example — weighted index", "A simplified CPI has weights: food 0.40, housing 0.25, transport 0.15, other 0.20, with price indices 110, 105, 120 and 102. Calculate the CPI.", ["CPI = Σ(weight × index).", "= 0.40 × 110 + 0.25 × 105 + 0.15 × 120 + 0.20 × 102.", "= 44 + 26.25 + 18 + 20.4 = %.2f." % cpi], "108.65"),
        ("Worked example — inflation and real wages", "CPI rises from 120 to 126. Nominal wages rise 8 %. Calculate inflation and the approximate change in real wages.", ["Inflation = (126 − 120) ÷ 120 × 100 = %.0f %%." % infl, "Real wage change = (1.08 ÷ 1.05 − 1) × 100 = %.1f %%." % rw, "Real wages rise by about 2.9 %: workers are better off."], "5 %; about +2.9 %")],
       [M("A rise in the CPI from 200 to 210 means inflation of", "5 %", ["10 %", "2.1 %", "50 %"], "10 ÷ 200 × 100 = 5."),
        TF("Cost-push inflation is caused by a fall in costs.", False, "It is caused by rising costs shifting SRAS left."),
        N("The CPI is 140 in year 1 and 154 in year 2. Calculate the inflation rate (%).", 10, "(154 − 140) ÷ 140 × 100 = 10 %.", unit="%")],
       [O("Distinguish between demand-pull and cost-push inflation using AD/AS analysis.", "Demand-pull: AD shifts right along a rising SRAS, raising the price level and output. Cost-push: SRAS shifts left, raising the price level while output falls.", ["Demand-pull: AD right (1)", "Cost-push: SRAS left (1)", "Output effects contrasted (1)"]),
        M("Inflation is 6 % and a saver earns a nominal interest rate of 4 %. The approximate real interest rate is", "−2 %", ["2 %", "10 %", "4 %"], "4 − 6 = −2 %.", tier="approfondissement")],
       P("The price index of a country rose from 100 to 104 in year 1 and to 109.2 in year 2.",
         [pn("Calculate inflation in year 1 (%).", 4, "(104 − 100) ÷ 100 × 100 = 4.", 0, "%", 1),
          pn("Calculate inflation in year 2 (%).", 5, "(109.2 − 104) ÷ 104 × 100 = 5.", 0.01, "%", 2),
          po("Explain two groups likely to lose from unanticipated inflation.", "Savers with fixed nominal interest rates and people with fixed incomes such as pensioners, because the real value of their money falls.", ["Two groups with reasons (2)"], 2)]),
       sc3, (fig, "Main causes of inflation with a short description and an example of each.", "A table with four rows: demand-pull, cost-push, imported inflation and expectations."),
       notes=[NOTE_NUM, "Basket weights and price indices are invented; CPI details used by Cameroon's statistics institute are not described.", "CEMAC monetary arrangements are covered in the money and central banking lessons."])
assert (109.2 - 104) / 104 * 100 == 5 or abs((109.2 - 104) / 104 * 100 - 5) < 1e-9

# ---- 4 unemployment
ur = 1.2 / 12 * 100; assert ur == 10
part = 12 / 20 * 100; assert part == 60
fig = tabfig(["Type", "Cause", "Example"], [["Frictional", "time to find a job", "new graduates"], ["Structural", "skills mismatch", "mine closure"], ["Cyclical", "low AD", "recession"], ["Seasonal", "seasonal work", "harvest season"]], w=440, colw=[110, 170, 160], size=12, title="Types of unemployment")
sc4 = [
    ("The unemployment rate is", "unemployed ÷ labour force × 100", ["unemployed ÷ population × 100", "employed ÷ labour force × 100", "labour force ÷ population × 100"], "Labour force = employed + unemployed (those seeking work)."),
    ("Structural unemployment is caused by", "a mismatch between workers' skills or location and the jobs available", ["a fall in AD", "temporary job search", "seasonal demand"], "Industries decline or technology changes."),
    ("Cyclical (demand-deficient) unemployment is reduced by", "raising aggregate demand", ["raising the minimum wage", "reducing training", "cutting AD"], "Keynesian policy."),
    ("Underemployment means that workers", "work fewer hours or in jobs below their skills", ["have no job at all", "are retired", "are students"], "Common in economies with a large informal sector."),
    ("Supply-side policies against structural unemployment include", "education and training", ["raising AD only", "price controls", "lower exports"], "They improve skills and mobility."),
]
lesson(c2, "unemployment", "Unemployment",
       ["Define unemployment and calculate the unemployment and participation rates.", "Distinguish frictional, structural, cyclical and seasonal unemployment.", "Evaluate policies and the effects of unemployment, including underemployment."],
       [("definition", "Definition and measurement", "A person is **unemployed** if they are without work, available for work and actively seeking it. **Labour force** = employed + unemployed. **Unemployment rate** = unemployed ÷ labour force × 100. **Participation rate** = labour force ÷ working-age population × 100. In many developing economies official rates understate the problem because most people work in the **informal sector** or are **underemployed** (too few hours, or work below their skill)."),
        ("retenir", "Types and causes", "**Frictional**: short-term, workers between jobs. **Structural**: permanent decline of an industry or a skills/location mismatch. **Cyclical (demand-deficient)**: low AD in a recession. **Seasonal**: farm work, tourism. **Real-wage (classical)**: wages above the market-clearing level (e.g. minimum wages, union power). Rural-urban migration can increase urban unemployment and informal work."),
        ("pieges", "Matching policy to type", "Raising AD helps **cyclical** unemployment but may only cause inflation if the problem is **structural**. Training and education address structural and frictional problems; better job information shortens frictional unemployment. Do not assume that all unemployment is involuntary or that a fall in the rate always means more jobs (discouraged workers may leave the labour force)."),
        ("retenir", "Consequences", "Costs: lost output, lower incomes and living standards, social problems, higher government benefit spending or lost tax revenue, and **hysteresis** (skills erode, making long-term unemployment persistent). Youth unemployment and underemployment are major concerns in many African economies; policy options include vocational training, entrepreneurship support and rural development.")],
       [("Worked example — unemployment rate", "A country has 12 million people in the labour force, of whom 1.2 million are unemployed. Calculate the unemployment rate.", ["Unemployment rate = unemployed ÷ labour force × 100.", "= 1.2 ÷ 12 × 100.", "= %.0f %%." % ur], "10 %"),
        ("Worked example — participation rate", "The working-age population is 20 million and the labour force is 12 million. Calculate the participation rate.", ["Participation rate = labour force ÷ working-age population × 100.", "= 12 ÷ 20 × 100.", "= %.0f %%." % part], "60 %")],
       [M("Which type of unemployment is caused by seasonal demand for farm labour?", "Seasonal", ["Frictional", "Cyclical", "Structural"], "Work varies with the season."),
        TF("The labour force includes people who have given up looking for work.", False, "Only those employed or actively seeking work are included."),
        N("There are 40 000 unemployed and 360 000 employed. Calculate the unemployment rate (%).", 10, "40 000 ÷ 400 000 × 100 = 10 %.", unit="%")],
       [O("Distinguish between structural and cyclical unemployment and suggest a policy for each.", "Structural unemployment results from a mismatch of skills or location; policy: education, retraining, mobility help. Cyclical unemployment results from low aggregate demand in a recession; policy: raise AD by fiscal or monetary policy.", ["Structural defined (1)", "Cyclical defined (1)", "Matching policies (1)"]),
        M("Why might the official unemployment rate be low in an economy with a large informal sector?", "Many people work a few hours in informal activities and are counted as employed", ["Everyone has a formal job", "Informal workers are always unemployed", "The labour force is zero"], "Underemployment is not captured.", tier="approfondissement")],
       P("In a region the working-age population is 500 000; 300 000 are employed and 30 000 are unemployed.",
         [pn("Calculate the labour force.", 330000, "300 000 + 30 000.", 0, None, 1),
          pn("Calculate the unemployment rate (%, 1 d.p.).", round(30000 / 330000 * 100, 1), "30 000 ÷ 330 000 × 100 = 9.1 %.", 0.05, "%", 2),
          pn("Calculate the participation rate (%).", 66, "330 000 ÷ 500 000 × 100 = 66 %.", 0.1, "%", 1),
          po("Suggest one reason a rise in the participation rate may increase measured unemployment.", "Discouraged workers who re-enter the search for jobs are counted as unemployed if they do not find work immediately.", ["Valid reason (1)"], 1)]),
       sc4, (fig, "Four main types of unemployment with their causes and examples.", "A table with rows for frictional, structural, cyclical and seasonal unemployment."),
       notes=[NOTE_NUM, "Cameroonian labour-market descriptions are qualitative; official data on unemployment and the informal sector must be taken from current statistics."])
assert round(30000 / 330000 * 100, 1) == 9.1

# ---- 5 economic growth
g = 3.5; dbl = 70 / g; assert dbl == 20
gr = (1.03) * (1 - 0.0); real_pc = (1.035 / 1.025 - 1) * 100; assert abs(real_pc - 0.9756) < 0.001
fig = plot(0, 12, 0, 12, curves=[("sqrt(100-x^2)", "blue", "PPF 1"), ("sqrt(144-x^2)", "green", "PPF 2")], xlabel="capital goods", ylabel="consumer goods", h=280)
sc5 = [
    ("Economic growth is", "an increase in real GDP over time", ["an increase in nominal GDP", "a rise in prices", "a fall in unemployment only"], "Real output rises."),
    ("Actual growth is shown by", "a movement towards the PPF", ["an outward shift of the PPF", "an inward shift of the PPF", "a point beyond the PPF"], "Using spare capacity."),
    ("Potential growth is shown by", "an outward shift of the PPF or LRAS", ["a movement along the PPF", "a fall in real GDP", "a rise in prices"], "Productive capacity increases."),
    ("Which is a cost of rapid growth?", "Environmental damage and resource depletion", ["Higher incomes", "More jobs", "More tax revenue"], "Sustainability concerns."),
    ("Investment in education raises growth by improving", "human capital", ["interest rates", "tariffs", "money supply only"], "More productive labour."),
]
lesson(c2, "growth", "Economic growth",
       ["Distinguish actual from potential growth and calculate growth rates.", "Explain the determinants of growth.", "Evaluate the benefits and costs of growth and sustainable development."],
       [("definition", "Actual and potential growth", "**Economic growth** is an increase in **real GDP** over time (per capita is more informative for living standards). **Actual growth** is the increase in real output, shown by movement towards the PPF or a rightward shift of AD along SRAS. **Potential growth** is an increase in productive capacity: an outward shift of the PPF or LRAS. Growth rate = (real GDP₁ − real GDP₀) ÷ real GDP₀ × 100."),
        ("formule", "Rule of 70 and per-capita growth", "The **doubling time** of a variable growing at g % per year is about 70 ÷ g years (an approximation). Real GDP per capita growth ≈ real GDP growth − population growth; more precisely (1 + g_Y) ÷ (1 + g_N) − 1. A country growing at 3.5 % doubles its real GDP in about 20 years."),
        ("retenir", "Determinants", "Growth comes from more or better **factors of production**: **capital accumulation** (investment), **human capital** (education, health), **technology** and innovation, **natural resources**, **institutions** (property rights, rule of law, stable government), **infrastructure** and **trade openness**. Demand-side factors determine whether capacity is used (consumer and business confidence, exports)."),
        ("pieges", "Benefits and costs", "Benefits: higher incomes, more jobs, more tax revenue for health and education, reduced poverty. Costs: **environmental damage**, resource depletion, inequality (benefits may be uneven), inflationary pressure if growth is too fast, and **opportunity cost** of current consumption when resources go into investment. **Sustainable development** meets current needs without harming future generations. GDP growth does not guarantee development.")],
       [("Worked example — growth rate and doubling time", "Real GDP rises from 13 600 to 14 076 billion FCFA. Calculate the growth rate and the approximate doubling time at this rate.", ["Growth = (14 076 − 13 600) ÷ 13 600 × 100 = %.1f %%." % ((14076 - 13600) / 13600 * 100), "Doubling time ≈ 70 ÷ 3.5.", "= %.0f years." % dbl], "3.5 %; about 20 years"),
        ("Worked example — per capita growth", "Real GDP grows by 3.5 % and the population by 2.5 %. Calculate the approximate growth in real GDP per capita.", ["Exact: (1.035 ÷ 1.025 − 1) × 100.", "= %.2f %%." % real_pc, "Approximate: 3.5 − 2.5 = 1.0 %."], "About 1 % per year")],
       [M("Which of these would shift LRAS to the right?", "Investment in education and infrastructure", ["A tax cut that raises consumption only", "A fall in unemployment benefit only", "An increase in imports"], "It raises productive capacity."),
        TF("Economic growth always improves living standards equally for all citizens.", False, "Benefits may be unequally shared and environmental costs may arise."),
        N("A country's real GDP rises from 500 to 520. Calculate the growth rate (%).", 4, "(520 − 500) ÷ 500 × 100 = 4 %.", unit="%")],
       [O("Evaluate whether high economic growth is always desirable.", "Growth raises incomes and creates jobs, but may cause environmental damage, inequality and resource depletion; the quality of growth and its distribution matter, and growth must be sustainable.", ["Benefit (1)", "Cost (1)", "Evaluative judgement (1)"]),
        M("Real GDP grows 6 % and population grows 3 %. Real GDP per capita grows by about", "3 %", ["9 %", "6 %", "2 %"], "6 − 3 = 3 % (approximately).", tier="approfondissement")],
       P("An economy's real GDP is 10 000 (billion FCFA) in year 1 and 10 400 in year 2; its population rises by 1 %.",
         [pn("Calculate the growth rate of real GDP (%).", 4, "(10 400 − 10 000) ÷ 10 000 × 100.", 0, "%", 1),
          pn("Approximate growth in real GDP per capita (%).", 3, "4 − 1 = 3.", 0.1, "%", 1),
          po("Name two factors that could raise the country's potential output.", "Investment in capital and infrastructure; better education and technology.", ["Two valid factors (2)"], 2)]),
       sc5, (fig, "Potential growth shown as an outward shift of the production possibility frontier.", "Two quarter-circle curves, the second further from the origin than the first."),
       notes=[NOTE_NUM, "The rule of 70 is an approximation, not an exact formula.", NOTE_ORDER])

# =====================================================================  CH 3  MONEY, BANKING, PUBLIC FINANCE
c3 = p.chapter("money", "Money, banking, the central bank and public finance", programRef="GCE A Level Economics — Money and banking; monetary policy; public finance (to be checked)")

# ---- 6 money and banking
dep = 1_000_000; r = 0.10; mult = 1 / r; tot_dep = dep * mult; lend = tot_dep - dep
assert mult == 10 and tot_dep == 10_000_000 and lend == 9_000_000
price = 500 * 4 / 1000; assert price == 2
fig = vflow(["Households deposit 1 000 000 FCFA", "Bank keeps 10 % reserve: 100 000", "Bank lends 900 000 FCFA", "The loan is spent and re-deposited", "Another bank keeps 90 000 and lends 810 000"], w=440, size=12, bw=340, title="Credit creation (reserve ratio 10 %)")
sc6 = [
    ("Which is NOT a function of money?", "A source of government revenue", ["A medium of exchange", "A unit of account", "A store of value"], "Money's functions: medium, unit of account, store of value, standard of deferred payment."),
    ("The money multiplier is", "1 ÷ reserve ratio", ["reserve ratio ÷ 1", "1 − reserve ratio", "reserve ratio × deposits"], "A 10 % reserve ratio gives 10."),
    ("Banks create money when they", "make loans that are re-deposited", ["print banknotes", "pay taxes", "buy imports"], "Loans create deposits."),
    ("In the quantity theory MV = PT, V is", "the velocity of circulation", ["the value of output", "the volume of trade", "the variable cost"], "How many times money changes hands per period."),
    ("Which is the best example of a store of value?", "Savings held in a bank account", ["A bus ticket", "A restaurant bill", "A debt"], "Money keeps purchasing power over time."),
]
lesson(c3, "money-banking", "Money and commercial banking",
       ["State the functions and qualities of money.", "Explain credit creation and calculate the money multiplier.", "Use the quantity theory of money MV = PT."],
       [("definition", "Functions of money", "Money is anything generally accepted as payment. Its functions: **medium of exchange** (solves the double coincidence of wants in barter), **unit of account**, **store of value** and **standard of deferred payment**. Good money is acceptable, durable, divisible, portable, scarce and stable in value. **Narrow money** (M1) is notes, coins and current-account deposits; **broad money** adds savings and time deposits. Mobile money is widely used in Cameroon as a means of payment."),
        ("retenir", "Commercial banks", "Commercial banks accept **deposits**, make **loans**, provide payment services and manage risk. Their balance-sheet aim is to balance **liquidity** (cash on hand), **profitability** (loans earn interest) and **security** (low risk). Banks hold a fraction of deposits as **reserves** and lend the rest; the loans are spent and re-deposited in the banking system, creating further deposits: **credit creation**."),
        ("formule", "Credit creation and quantity theory", "If banks hold a fraction **r** of deposits as reserves, the maximum **money multiplier** is **1 ÷ r**; an initial deposit D can support total deposits D ÷ r. **Quantity theory**: **MV = PT** (money supply × velocity = price level × volume of transactions). If V and T are stable, a rise in M raises the price level, so inflation is a monetary phenomenon (the monetarist claim)."),
        ("pieges", "Limits of the multiplier", "The multiplier is a **maximum**: in practice banks hold excess reserves, borrowers may hold cash, and loan demand may be weak, so less money is created. Do not say that banks 'lend out the same money again'; each loan **creates a new deposit**. Velocity and trade are **not** constant, so the quantity theory is a framework rather than a precise law. Informal finance (tontines, rotating savings) is important but is not counted in bank deposits.")],
       [("Worked example — credit creation", "A household deposits 1 000 000 FCFA. Banks keep 10 % as reserves. Find the maximum total deposits and the extra deposits created.", ["Money multiplier = 1 ÷ 0.10 = %d." % mult, "Maximum total deposits = 1 000 000 × 10 = %s FCFA." % format(tot_dep, ","), "Extra deposits = %s − 1 000 000 = %s FCFA." % (format(tot_dep, ","), format(lend, ","))], "10 000 000 FCFA in total; 9 000 000 FCFA created"),
        ("Worked example — quantity theory", "In a simple economy M = 500, V = 4 and T = 1000. Find the price level P. If M rises to 600 with V and T unchanged, what is the new P?", ["MV = PT gives P = 500 × 4 ÷ 1000 = %.1f." % price, "With M = 600: P = 600 × 4 ÷ 1000 = %.1f." % (600 * 4 / 1000), "Prices rise by 20 %, proportional to the rise in M."], "P = 2.0, then 2.4")],
       [M("A bank must keep 20 % of deposits as reserves. The maximum money multiplier is", "5", ["20", "0.2", "2"], "1 ÷ 0.20 = 5."),
        TF("Banks create money by making loans.", True, "Loans create deposits."),
        N("A deposit of 2 000 000 FCFA with a reserve ratio of 25 %. What is the maximum total deposits (FCFA)?", 8_000_000, "Multiplier 4; 2 000 000 × 4 = 8 000 000.", unit="FCFA")],
       [O("Explain why the actual credit multiplier is smaller than 1 ÷ reserve ratio.", "Banks may hold excess reserves, some people hold cash rather than depositing it, and borrowers may be unwilling to borrow or banks unwilling to lend in uncertain times, so less lending and deposit creation occurs.", ["Excess reserves (1)", "Cash held by public (1)", "Weak loan demand (1)"]),
        M("According to the quantity theory, if M doubles while V and T are constant, P", "doubles", ["halves", "stays the same", "rises by 10 %"], "MV = PT, so P is proportional to M.", tier="approfondissement")],
       P("A bank in Douala receives a new deposit of 5 000 000 FCFA. The required reserve ratio is 10 %.",
         [pn("How much can the bank lend initially (FCFA)?", 4_500_000, "5 000 000 × 0.9 = 4 500 000.", 0, "FCFA", 1),
          pn("What is the maximum increase in total deposits in the banking system (FCFA)?", 50_000_000, "5 000 000 ÷ 0.10 = 50 000 000.", 0, "FCFA", 2),
          po("State two reasons why the actual increase will be lower.", "Banks may keep excess reserves; customers may withdraw cash; loan demand may be low.", ["Two valid reasons (2)"], 2)]),
       sc6, (fig, "Credit creation: each deposit supports further lending and deposits.", "Five boxes in a vertical chain showing a deposit, the reserve kept, the loan, redeposit and a further loan."),
       notes=[NOTE_NUM, "Reserve requirements in the CEMAC are set by the BEAC and differ from the simple 10 % used here.", "Mobile money is mentioned only as a general fact."])

# ---- 7 central bank, BEAC, CEMAC, FCFA
conv = 200 * 655.957; back = 500000 / 655.957
assert abs(conv - 131191.4) < 1e-6 and abs(back - 762.245) < 0.01
fig = tabfig(["Item", "Fact (to be verified)"], [["Monetary union", "CEMAC, six member states"], ["Central bank", "BEAC, headquarters in Yaoundé"], ["Currency", "FCFA (XAF), issued by BEAC"], ["Fixed rate", "655.957 FCFA = 1 euro"], ["Bank supervision", "COBAC"]], w=440, colw=[140, 300], size=12, title="Money in the CEMAC")
sc7 = [
    ("The central bank of the CEMAC countries is the", "BEAC", ["BCEAO", "COBAC", "the IMF"], "Banque des États de l'Afrique Centrale."),
    ("The FCFA used in Cameroon is", "linked to the euro at a fixed rate", ["freely floating", "linked to the dollar", "not convertible by design"], "1 euro = 655.957 FCFA."),
    ("An increase in the central bank's policy interest rate is meant to", "reduce borrowing and aggregate demand", ["raise AD", "lower exports", "raise government spending"], "Tight monetary policy."),
    ("A disadvantage of a fixed exchange-rate peg is", "loss of an independent monetary policy and a flexible exchange rate", ["very high inflation always", "no trade", "volatile currency"], "The central bank must defend the peg."),
    ("Open-market operations involve the central bank", "buying or selling securities to change liquidity", ["printing money for the government only", "setting tax rates", "regulating trade"], "They influence interest rates and the money supply."),
]
lesson(c3, "central-bank", "The central bank, BEAC, CEMAC and the FCFA",
       ["State the functions of a central bank and the tools of monetary policy.", "Describe the CEMAC monetary arrangements and the fixed link between the FCFA and the euro.", "Evaluate the benefits and costs of a monetary union with a fixed exchange rate."],
       [("definition", "Functions of a central bank", "A central bank issues notes and coins, acts as **banker to the government and to commercial banks**, is the **lender of last resort**, manages the **exchange rate** and reserves, supervises the financial system and conducts **monetary policy**: controlling interest rates and the money supply to achieve price stability and support the economy. Tools: **policy (refinancing) interest rates**, **reserve requirements**, **open-market operations** and guidance."),
        ("retenir", "CEMAC, BEAC and the FCFA (outline)", "Cameroon belongs to the **CEMAC** (Communauté économique et monétaire de l'Afrique centrale), a monetary union of six states: Cameroon, Central African Republic, Chad, Republic of Congo, Equatorial Guinea and Gabon. Their common central bank is the **BEAC** (headquarters in Yaoundé), which issues the **FCFA (XAF)**. The FCFA is linked to the euro at a **fixed rate of 655.957 FCFA = 1 euro**, with convertibility supported by a guarantee from France. The **COBAC** supervises banks in the zone. West Africa's CFA franc (BCEAO) is a separate currency."),
        ("pieges", "Facts to state carefully", "Do not describe the FCFA as the 'French franc' or confuse the **two CFA zones** (central Africa with BEAC and XAF; west Africa with BCEAO and XOF). Say 'fixed to the euro' rather than 'controlled by France'. Detailed rules (reserve deposits, guarantee terms, policy rates) change over time and must be checked in current official sources. Remember that the BEAC sets policy for the whole zone, not for Cameroon alone."),
        ("retenir", "Evaluation of the arrangement", "**Benefits**: price stability and low inflation, credibility, no exchange-rate risk within the zone and against the euro, easier trade and capital movements, discipline on government borrowing. **Costs**: no independent monetary or exchange-rate policy to respond to country-specific shocks; the peg may leave the currency **over- or undervalued**, affecting export competitiveness; policy rates reflect average needs of six economies; dependence on euro-area conditions.")],
       [("Worked example — conversion", "Using the fixed rate of 655.957 FCFA per euro, how many FCFA are 200 euros? How many euros is 500 000 FCFA?", ["200 euros × 655.957 = %.1f FCFA." % conv, "500 000 ÷ 655.957 = %.1f euros." % back, "Because the rate is fixed, these conversions stay the same over time."], "131 191.4 FCFA; about 762.2 euros"),
        ("Worked example — policy transmission", "Describe how a rise in the BEAC's policy rate may reduce inflationary pressure.", ["Higher policy rates raise lending rates charged by banks.", "Borrowing for consumption and investment falls, so AD falls or grows more slowly.", "Lower demand reduces demand-pull price pressure."], "AD growth slows and inflation pressure falls")],
       [M("Which institution issues the FCFA used in Cameroon?", "BEAC", ["BCEAO", "COBAC", "The European Central Bank"], "BEAC issues the XAF for CEMAC."),
        TF("The FCFA used in Cameroon and the CFA franc used in Senegal are the same currency.", False, "They are two separate currencies with a common peg to the euro."),
        N("Convert 1000 euros to FCFA at 655.957 (round to the nearest whole FCFA).", round(1000 * 655.957), "1000 × 655.957 = 655 957.", tol=1, unit="FCFA")],
       [O("Evaluate the advantages and disadvantages for Cameroon of having its currency fixed to the euro.", "Advantages: stable exchange rate and low inflation, credibility, no exchange-rate risk in trade with the euro area. Disadvantages: no independent monetary or exchange-rate policy to respond to shocks such as a fall in oil or cocoa prices; the peg may overvalue the currency, harming exports.", ["Advantage (1)", "Disadvantage (1)", "Link to Cameroon's context (1)"]),
        M("An external shock reduces oil export earnings of a CEMAC country. Which policy tool is NOT available to it independently?", "Devaluing its own currency", ["Cutting public spending", "Seeking external financing", "Supply-side reforms"], "The exchange rate is fixed within the union.", tier="approfondissement")],
       P("A trader in Douala imports goods worth 2 000 euros and sells them for 1 500 000 FCFA.",
         [pn("Cost of the goods in FCFA at 655.957 (round to the nearest FCFA).", round(2000 * 655.957), "2000 × 655.957 = 1 311 914.", 1, "FCFA", 1),
          pn("Gross profit in FCFA (nearest FCFA).", round(1500000 - 2000 * 655.957), "1 500 000 − 1 311 914 = 188 086.", 1, "FCFA", 2),
          po("Explain one benefit of the fixed rate for this trader.", "There is no exchange-rate risk when buying in euros, so the trader can plan costs and prices with confidence.", ["Benefit (1)", "Explanation (1)"], 2)]),
       sc7, (fig, "Key facts on the CEMAC monetary arrangements, flagged for verification.", "A table of five rows listing the monetary union, the central bank, the currency, the fixed rate and the supervisory body."),
       notes=["FACTS TO VERIFY from official BEAC/CEMAC sources: the six member states; BEAC headquarters in Yaoundé; the fixed parity 655.957 FCFA per euro; the French guarantee and any reserve-deposit obligations (rules have changed over time); COBAC's role; current policy instruments and rates.", "Reform discussions about the CFA franc are deliberately not covered (politically contested; neutral treatment needed).", NOTE_NUM])
assert round(2000 * 655.957) == 1311914 and round(1500000 - 2000 * 655.957) == 188086

# ---- 8 public finance
bud_rev = 4000; bud_exp = 4400; deficit = bud_exp - bud_rev; gdp_b = 17000; dpct = deficit / gdp_b * 100
assert deficit == 400 and abs(dpct - 2.3529) < 1e-3
inc = 180000; taxv = 0.10 * (100000 - 50000) + 0.20 * (inc - 100000); avg = taxv / inc * 100
assert taxv == 21000 and abs(avg - 11.6667) < 1e-3
fig = bars([("Revenue", 4000, "green"), ("Expenditure", 4400, "red")], w=380, h=240)
sc8 = [
    ("A budget deficit occurs when", "government spending exceeds revenue", ["revenue exceeds spending", "exports exceed imports", "taxes are zero"], "Deficit = spending − revenue."),
    ("A progressive tax is one where", "the average rate rises as income rises", ["everyone pays the same amount", "the poor pay a larger proportion", "the rate falls with income"], "Higher incomes pay a higher percentage."),
    ("An indirect tax is", "a tax on spending such as VAT", ["a tax on income", "a tax on wealth", "a fine"], "Paid when buying goods and services."),
    ("Expansionary fiscal policy involves", "raising government spending or cutting taxes", ["cutting spending", "raising taxes", "raising interest rates"], "To increase AD."),
    ("Crowding out occurs when", "government borrowing raises interest rates and reduces private investment", ["taxes are cut", "exports fall", "wages rise"], "Government competes for funds."),
]
lesson(c3, "public-finance", "Public finance and fiscal policy",
       ["Describe government spending, taxation and the budget balance.", "Classify taxes (direct/indirect, progressive/regressive/proportional) and calculate tax payable.", "Evaluate fiscal policy and the burden of public debt."],
       [("definition", "Government budget", "Governments spend on **current expenditure** (salaries, services), **capital expenditure** (roads, schools) and **transfers**, financed by **tax revenue** and borrowing. **Budget balance** = revenue − expenditure; a **deficit** (spending > revenue) is financed by borrowing, adding to the **national debt**. **Fiscal policy** is the use of spending and taxation to influence AD, employment, prices and growth."),
        ("retenir", "Types of tax", "**Direct** taxes are levied on income, profit and wealth (income tax, company tax); **indirect** taxes on spending (VAT, excise, customs duties). A tax is **progressive** if the average rate rises with income, **proportional** if constant, **regressive** if it takes a larger proportion from the poor (a flat tax on a basic good). Good taxes are equitable, certain, convenient, economical and efficient. High informality limits revenue collection in many developing economies."),
        ("pieges", "Average and marginal rates", "The **average tax rate** = tax paid ÷ income; the **marginal rate** applies to the **last** unit of income. In a progressive system with bands, only the income **within each band** is taxed at that band's rate, not the whole income. A 20 % marginal rate does not mean 20 % of all income is taxed. A deficit is a **flow** (one year) and the debt is a **stock** (accumulated)."),
        ("retenir", "Evaluation of fiscal policy", "Expansionary policy (more G or lower T) raises AD in a recession, helped by the multiplier; but may cause inflation, a larger deficit and **crowding out**. Contractionary policy reduces inflation and debt but may raise unemployment. Problems: **time lags**, inaccurate forecasting and political constraints. **Automatic stabilisers** (progressive taxes, benefits) dampen cycles without new decisions. High debt service reduces money for health and education; **debt sustainability** depends on growth, interest rates and the debt-to-GDP ratio.")],
       [("Worked example — budget balance", "An imaginary government has revenue of 4000 and spending of 4400 (billion FCFA), with GDP of 17 000. Calculate the deficit and the deficit as a percentage of GDP.", ["Deficit = 4400 − 4000 = %d billion FCFA." % deficit, "As a percentage of GDP: 400 ÷ 17 000 × 100.", "= %.2f %%." % dpct], "400 billion FCFA; 2.35 % of GDP"),
        ("Worked example — progressive tax", "Imaginary bands: first 50 000 FCFA taxed at 0 %, 50 000 to 100 000 at 10 %, above 100 000 at 20 %. Find the tax on a monthly income of 180 000 FCFA and the average rate.", ["Tax on band 2: 10 % × 50 000 = 5000. Tax on band 3: 20 % × 80 000 = 16 000.", "Total tax = 0 + 5000 + 16 000 = %d FCFA." % taxv, "Average rate = 21 000 ÷ 180 000 × 100 = %.1f %%; the marginal rate is 20 %%." % avg], "21 000 FCFA; average 11.7 %")],
       [M("Which of these is a direct tax?", "Income tax", ["Value added tax", "Customs duty", "Excise duty"], "Levied directly on income."),
        TF("A regressive tax takes a larger proportion of income from the poor than from the rich.", True, "That is the definition of regressive."),
        N("Revenue is 900 and expenditure 1000 (billion FCFA). What is the deficit?", 100, "1000 − 900 = 100.", unit="billion FCFA")],
       [O("Evaluate the use of expansionary fiscal policy to reduce unemployment.", "Higher spending or lower taxes raises AD and, through the multiplier, output and jobs. But it may increase the deficit and debt, cause inflation near full employment, crowd out private investment and take time to work; it is less effective for structural unemployment.", ["Mechanism (1)", "Limitation (1)", "Judgement on unemployment type (1)"]),
        M("Which of these acts as an automatic stabiliser?", "A progressive income tax", ["A road-building programme", "A new import duty", "A change in the policy rate"], "Tax revenue falls automatically in a recession.", tier="approfondissement")],
       P("Using the imaginary bands (0 % to 50 000; 10 % from 50 000 to 100 000; 20 % above 100 000):",
         [pn("Calculate the tax payable on 150 000 FCFA.", 0.1 * 50000 + 0.2 * 50000, "5000 + 10 000 = 15 000.", 0, "FCFA", 2),
          pn("Calculate the average tax rate (%).", 10, "15 000 ÷ 150 000 × 100 = 10 %.", 0.01, "%", 1),
          pm("The tax is", "progressive", ["regressive", "proportional", "an indirect tax"], "The average rate rises with income.", 1),
          po("Name one disadvantage of high marginal tax rates.", "They may reduce the incentive to work or encourage avoidance and evasion.", ["Valid disadvantage (1)"], 1)]),
       sc8, (fig, "An imaginary budget: revenue below expenditure gives a deficit of 400.", "A bar chart with two bars: revenue 4000 and expenditure 4400 billion FCFA."),
       notes=[NOTE_NUM + " Tax bands are invented, not Cameroon's tax code.", "Tax rates, VAT and public-debt facts for Cameroon are not stated; check the current finance act and debt statistics.", NOTE_ORDER])
assert 0.1 * 50000 + 0.2 * 50000 == 15000

# =====================================================================  CH 4  INTERNATIONAL ECONOMICS
c4 = p.chapter("international", "International trade, balance of payments and exchange rates", programRef="GCE A Level Economics — International trade; balance of payments; exchange rates (to be checked)")

# ---- 9 international trade
ocA_c = 5 / 10; ocB_c = 4 / 6; ocA_f = 10 / 5; ocB_f = 6 / 4
assert ocA_c < ocB_c and ocB_f < ocA_f
tariff_rev = 100 * 0.20 * 1000; tot = 130 / 120 * 100
assert tariff_rev == 2000 * 10 and abs(tot - 108.333) < 1e-3
fig = tabfig(["", "Country A", "Country B"], [["Cocoa per worker-day", "10 t", "6 t"], ["Coffee per worker-day", "5 t", "4 t"], ["Opp. cost of 1 t cocoa", "0.5 t coffee", "0.67 t coffee"], ["Opp. cost of 1 t coffee", "2 t cocoa", "1.5 t cocoa"]], w=450, colw=[190, 130, 130], size=12, title="Comparative advantage (imaginary data)")
sc9 = [
    ("A country has comparative advantage in a good if it", "has the lower opportunity cost of producing it", ["has the higher absolute output", "has the lower price only", "uses more labour"], "Even a country with no absolute advantage can gain from trade."),
    ("A tariff is", "a tax on imports", ["a tax on exports", "a subsidy", "a quota"], "It raises the domestic price of imports."),
    ("Which is an argument for protection?", "Protecting infant industries", ["Lower prices for consumers", "Greater consumer choice", "Economies of scale"], "Temporary protection while industries develop."),
    ("The terms of trade index is", "export price index ÷ import price index × 100", ["import price index ÷ export price index × 100", "exports − imports", "export volume ÷ import volume"], "A rise means each export buys more imports."),
    ("A quota is", "a physical limit on the quantity of imports", ["a tax on imports", "a subsidy to exports", "a tax on profits"], "It restricts supply and raises prices."),
]
lesson(c4, "trade", "International trade and protection",
       ["Explain absolute and comparative advantage and calculate opportunity costs.", "Describe the effects of tariffs, quotas and subsidies.", "Evaluate arguments for free trade and protection, including terms of trade."],
       [("definition", "Gains from trade", "Countries trade because they differ in resources, skills and costs. **Absolute advantage**: producing more with the same resources. **Comparative advantage**: producing a good at a **lower opportunity cost** than another country. By **specialising** where they have a comparative advantage and trading, both countries can consume beyond their own production possibilities. Assumptions: no transport costs, constant costs, free mobility within countries, no trade barriers."),
        ("retenir", "Protection", "**Tariff**: tax on imports (raises price, protects domestic firms, raises government revenue, reduces consumer surplus). **Quota**: limit on quantity. **Subsidies** to domestic producers; **embargoes**; **administrative barriers**. Arguments for: **infant industries**, protecting jobs, national security, anti-dumping, raising revenue, correcting a trade deficit. Arguments against: higher prices, less choice, inefficiency, retaliation, reduced competitiveness. Regional groupings (customs unions, free-trade areas) reduce barriers between members."),
        ("pieges", "How to find comparative advantage", "Compare **opportunity costs**, not absolute output. In the table, Country A is better at both goods (absolute advantage in both) but its opportunity cost of cocoa (0.5 coffee) is lower than B's (0.67 coffee), so A should specialise in cocoa and B in coffee. A common mistake is to say the country with the higher output has the comparative advantage. The mutually beneficial terms of trade lie **between** the two opportunity costs."),
        ("retenir", "Terms of trade and primary commodities", "**Terms of trade** = (export price index ÷ import price index) × 100. A rise is favourable. Exporters of primary commodities (cocoa, coffee, cotton, crude oil) face **volatile prices** because of inelastic demand and supply and changes in world conditions, which can destabilise export earnings and government revenue; diversification and processing add value.")],
       [("Worked example — opportunity costs", "Per worker-day, Country A makes 10 t of cocoa or 5 t of coffee; Country B 6 t of cocoa or 4 t of coffee. Find the opportunity cost of cocoa in each country and who should specialise in cocoa.", ["A: 5 ÷ 10 = %.2f t coffee per t cocoa. B: 4 ÷ 6 = %.2f." % (ocA_c, ocB_c), "A has the lower opportunity cost of cocoa.", "So A specialises in cocoa and B in coffee (check: coffee costs 2 cocoa in A, 1.5 in B)."], "A: cocoa, B: coffee"),
        ("Worked example — tariff and terms of trade", "(a) The world price is 100 FCFA per unit; a 20 % tariff is imposed and 2000 units are still imported. Find revenue and the domestic price. (b) Export prices index 130, import prices index 120: find the terms of trade.", ["Domestic price = 100 × 1.20 = 120 FCFA; revenue = 20 × 2000 = %d FCFA." % tariff_rev, "Terms of trade = 130 ÷ 120 × 100 = %.1f." % tot, "A value above 100 means export prices have risen more than import prices."], "Revenue 40 000 FCFA; terms of trade 108.3")],
       [M("Comparative advantage depends on", "opportunity cost", ["absolute cost", "the exchange rate only", "the size of the country"], "Lower opportunity cost."),
        TF("A tariff raises the domestic price of the imported good.", True, "The tariff is added to the world price."),
        N("Export price index 110, import price index 125. Calculate the terms of trade (1 d.p.).", round(110 / 125 * 100, 1), "110 ÷ 125 × 100 = 88.0.", tol=0.1)],
       [O("Evaluate the case for protecting an infant industry.", "Protection (tariffs, subsidies) gives a young industry time to grow and reach economies of scale, so it can compete later. But protection raises prices for consumers, may encourage inefficiency, and it is difficult to remove; retaliation is possible and the industry may never mature.", ["Case in favour (1)", "Case against (1)", "Judgement (1)"]),
        M("A country's export prices rise by 4 % and import prices rise by 10 %. Its terms of trade", "worsen", ["improve", "stay the same", "cannot be known"], "Each export buys fewer imports.", tier="approfondissement")],
       P("A country imports 5000 units of a good at the world price of 200 FCFA. A 25 % tariff is imposed and imports fall to 4000 units.",
         [pn("New domestic price (FCFA).", 250, "200 × 1.25 = 250.", 0, None, 1),
          pn("Tariff revenue (FCFA).", 50 * 4000, "50 × 4000 = 200 000.", 0, "FCFA", 2),
          po("Who gains and who loses from the tariff?", "Government gains revenue and domestic producers gain from higher prices and sales; consumers lose from higher prices and less choice.", ["Gainers (1)", "Losers (1)"], 2)]),
       sc9, (fig, "Opportunity costs in two imaginary countries: A has comparative advantage in cocoa, B in coffee.", "A table of output per worker-day and opportunity costs for countries A and B."),
       notes=[NOTE_NUM, "Gains-from-trade arithmetic is simplified; regional trade agreements are mentioned only in general.", NOTE_ORDER])
assert 50 * 4000 == 200000

# ---- 10 balance of payments
gb = 3500 - 4200; sv = 500 - 700; pi = -300; si = 400
ca = gb + sv + pi + si; assert (gb, sv, ca) == (-700, -200, -800)
fig = tabfig(["Account", "Items", "Imaginary figure"], [["Goods", "visible exports − imports", "−700"], ["Services", "transport, tourism", "−200"], ["Primary inc.", "profits, interest", "−300"], ["Secondary inc.", "remittances, aid", "+400"], ["Current account", "sum of the four", "−800"]], w=440, colw=[130, 180, 130], size=12, title="Current account (billion FCFA)")
sc10 = [
    ("The current account includes", "trade in goods and services and income flows", ["only trade in goods", "loans and investments", "foreign reserves only"], "Goods, services, primary and secondary income."),
    ("Remittances from workers abroad are recorded in", "secondary income in the current account", ["the goods balance", "the capital account", "services"], "A transfer, not payment for goods."),
    ("A current account deficit must be financed by", "a surplus on the capital and financial account (or running down reserves)", ["a trade surplus", "higher exports only", "no action"], "The BoP balances in accounting terms."),
    ("Foreign direct investment is recorded in", "the financial account", ["the goods balance", "services", "secondary income"], "Capital inflow."),
    ("A persistent large current account deficit may be corrected by", "policies that raise exports or reduce imports", ["lower exports", "higher imports", "no policy"], "Expenditure-switching and reducing policies."),
]
lesson(c4, "bop", "The balance of payments",
       ["Describe the components of the balance of payments.", "Calculate the current account balance.", "Explain causes of deficits and policy responses, including the constraints within a currency union."],
       [("definition", "Structure", "The **balance of payments** records a country's transactions with the rest of the world. The **current account** has: the **balance of trade in goods** (visible), **services** (invisible: transport, tourism, banking), **primary income** (profits, interest, wages across borders) and **secondary income** (transfers: remittances, aid). The **capital account** (small transfers) and **financial account** (direct and portfolio investment, loans, change in reserves) record how the current account is **financed**."),
        ("retenir", "Balance", "In accounting terms, the whole BoP balances: **current account + capital and financial account + errors and omissions = 0**. A current account **deficit** is financed by net capital inflows (borrowing, foreign investment, aid) or by using foreign exchange **reserves**. Persistent deficits may lead to rising debt and loss of confidence. Causes: strong domestic demand, uncompetitive exports, falling commodity prices, a high exchange rate."),
        ("pieges", "Deficit is not automatically bad", "A current account deficit is not necessarily a problem if it finances **productive investment** or is temporary. Likewise a surplus may reflect weak domestic demand. Do not confuse the **balance of trade** (goods only) with the **current account**, or a **deficit on the current account** with a **deficit on the whole BoP** (which always balances). Commodity exporters may see the balance swing with world prices."),
        ("retenir", "Policy responses", "**Expenditure-switching**: depreciation or tariffs shift spending from imports to home goods. **Expenditure-reducing**: contractionary fiscal and monetary policy reduce import demand. **Supply-side** policies raise competitiveness (productivity, infrastructure, diversification). Within a currency union with a fixed rate such as the CEMAC's, a country cannot devalue alone, so it relies on fiscal adjustment, supply-side policy, external financing and regional reserves.")],
       [("Worked example — current account", "In billion FCFA: goods exports 3500, goods imports 4200; services exports 500, services imports 700; net primary income −300; net secondary income +400. Calculate the goods balance, services balance and the current account.", ["Goods = 3500 − 4200 = %d. Services = 500 − 700 = %d." % (gb, sv), "Add income flows: −300 + 400 = +100.", "Current account = −700 − 200 + 100 = %d billion FCFA." % ca], "Goods −700; services −200; current account −800"),
        ("Worked example — financing", "The current account is −800 and net errors and omissions are zero. What must the capital and financial account (including reserves) show?", ["Total BoP must be zero.", "Capital and financial account = +800.", "So net inflows or a fall in reserves of 800 billion FCFA finance the deficit."], "+800 billion FCFA")],
       [M("Which item is recorded in the financial account?", "Foreign direct investment", ["Exports of cocoa", "Tourism receipts", "Remittances"], "It is a capital flow."),
        TF("The balance of trade and the current account balance are the same thing.", False, "The current account also includes services, income and transfers."),
        N("Goods exports 900, imports 1100; services exports 200, imports 150; net income and transfers +50 (billion FCFA). Calculate the current account balance.", 900 - 1100 + 200 - 150 + 50, "−200 + 50 + 50 = −100.", unit="billion FCFA")],
       [O("Explain two policies a country might use to reduce a current account deficit and one limitation of each.", "Depreciation makes exports cheaper and imports dearer but works slowly (J-curve) and raises import costs; deflationary policy reduces import demand but increases unemployment and slows growth.", ["Policy 1 and limitation (1.5)", "Policy 2 and limitation (1.5)"]),
        M("Why is it difficult for a CEMAC member state to devalue its currency on its own?", "The exchange rate is fixed and set for the whole union", ["Because it has no exports", "Because exports are all inelastic", "Because it has no reserves"], "Exchange-rate policy belongs to the union.", tier="approfondissement")],
       P("Imaginary data (billion FCFA): goods exports 3000, goods imports 3600; services balance −150; net primary income −200; net secondary income +300.",
         [pn("Goods balance.", -600, "3000 − 3600 = −600.", 0, None, 1),
          pn("Current account balance.", -600 - 150 - 200 + 300, "−600 − 150 − 200 + 300 = −650.", 0, "billion FCFA", 2),
          po("State two ways this deficit could be financed.", "Foreign direct investment or borrowing from abroad; running down foreign exchange reserves; aid.", ["Two valid ways (2)"], 2)]),
       sc10, (fig, "An imaginary current account with its four components and the balance.", "A table with goods, services, primary income, secondary income and the total current account balance."),
       notes=[NOTE_NUM, "Cameroon's actual trade and current account data are not given; use current official statistics if needed.", "Regional reserve-sharing arrangements within the CEMAC are not described."])
assert -600 - 150 - 200 + 300 == -650

# ---- 11 exchange rates
d1 = (600 - 560) / 560 * 100; d2 = (1 / 600 - 1 / 560) / (1 / 560) * 100
assert abs(d1 - 7.1429) < 1e-3 and abs(d2 + 6.6667) < 1e-3
fig = plot(0, 10, 0, 20, curves=[("20-x", "blue", "demand for USD"), ("2+x", "red", "supply of USD")], points=[(9, 11, "market rate", "green")], segments=[(0, 8, 10, 8, "orange", True, "fixed rate")], xlabel="quantity of USD", ylabel="FCFA per USD (hundreds)", h=270)
sc11 = [
    ("An appreciation of the FCFA against the dollar means", "one FCFA buys more dollars", ["one FCFA buys fewer dollars", "the dollar is weaker", "exports are cheaper"], "The currency gains value."),
    ("A fall in the exchange rate (depreciation) tends to", "make exports cheaper and imports dearer", ["make exports dearer", "lower inflation", "worsen competitiveness"], "Price effects improve competitiveness."),
    ("In a fixed exchange rate system the central bank must", "buy or sell foreign currency to maintain the rate", ["let the rate float", "raise tariffs", "control wages"], "Reserves are used to defend the peg."),
    ("The Marshall–Lerner condition says depreciation improves the current account if", "the sum of the price elasticities of demand for exports and imports is greater than 1", ["demand is inelastic", "exports are zero", "imports are fixed"], "Quantity effects dominate price effects."),
    ("A floating exchange rate is determined by", "demand for and supply of the currency", ["the central bank only", "the IMF", "tariffs"], "Market forces."),
]
lesson(c4, "exchange-rates", "Exchange rates",
       ["Distinguish floating, fixed and managed exchange rates.", "Calculate exchange-rate conversions and percentage changes.", "Analyse the effects of depreciation and the Marshall–Lerner condition."],
       [("definition", "Exchange-rate systems", "An **exchange rate** is the price of one currency in terms of another. In a **floating** system it is determined by the **demand for and supply of the currency** (for trade, investment and speculation). In a **fixed** system the central bank sets a rate and buys or sells foreign currency to defend it. In a **managed float** the central bank intervenes occasionally. The FCFA is fixed to the euro, and floats against other currencies such as the US dollar because the euro does."),
        ("retenir", "Determinants and effects", "A currency tends to **appreciate** with higher export demand, higher interest rates (capital inflows), lower inflation and confidence. **Depreciation** makes exports cheaper abroad and imports dearer, but raises the cost of imported inputs and debt in foreign currency, and may cause **imported inflation**. The effect on the current account depends on elasticities (**Marshall–Lerner**: PED for exports + PED for imports > 1) and on time (**J-curve**: the balance may worsen first)."),
        ("pieges", "Which way is the rate quoted?", "If the rate is quoted as **FCFA per dollar**, a rise from 560 to 600 means the **FCFA has depreciated** (more FCFA needed for one dollar), not appreciated. Calculate percentage changes for the **right currency**: the dollar appreciates by (600 − 560) ÷ 560 = 7.1 %, whereas the FCFA depreciates by about 6.7 % in dollar terms. State the currency each time and avoid mixing the two."),
        ("retenir", "Historical note", "In January 1994 the CFA franc was devalued by 50 % against the French franc to restore competitiveness during an economic crisis; this raised the CFA price of imports and gave exporters a boost. The case is often used to discuss expenditure-switching and its side effects on prices and living standards. Check the date and the size of the change before using it in teaching.")],
       [("Worked example — conversion and change", "The rate rises from 560 to 600 FCFA per dollar. Find the percentage change in the dollar's value in FCFA and in the FCFA's value in dollars.", ["Dollar: (600 − 560) ÷ 560 × 100 = %.1f %% (appreciation)." % d1, "FCFA: 1 ÷ 560 = 0.001786 dollars before; 1 ÷ 600 = 0.001667 after: %.1f %%." % d2, "The FCFA has depreciated against the dollar."], "Dollar +7.1 %; FCFA −6.7 %"),
        ("Worked example — price effects", "A shipment of cocoa is priced at 1 200 000 FCFA. Find its price in dollars at 560 and at 600 FCFA per dollar.", ["At 560: 1 200 000 ÷ 560 = %.0f dollars." % (1200000 / 560), "At 600: 1 200 000 ÷ 600 = %.0f dollars." % (1200000 / 600), "A weaker FCFA makes the same cocoa cheaper for foreign buyers."], "About 2143 dollars; then 2000 dollars")],
       [M("The exchange rate changes from 500 to 550 FCFA per dollar. The FCFA has", "depreciated", ["appreciated", "stayed the same", "been fixed"], "More FCFA are needed for a dollar."),
        TF("Depreciation always improves the current account immediately.", False, "In the short run the J-curve effect may worsen it."),
        N("An import costs 80 dollars. How many FCFA at 600 FCFA per dollar?", 48000, "80 × 600 = 48 000.", unit="FCFA")],
       [O("Explain how a depreciation of a currency may improve the current account and why the effect may be delayed.", "Exports become cheaper abroad and imports dearer at home; if demand is elastic (Marshall–Lerner) export revenue rises and import spending falls. Initially volumes adjust slowly because contracts are fixed, so import costs rise first (J-curve).", ["Price effects (1)", "Elasticity condition (1)", "J-curve delay (1)"]),
        M("A government fixes its currency above the equilibrium rate. This leads to", "excess demand for foreign currency and falling reserves", ["a surplus of foreign currency", "higher exports", "lower imports"], "The overvalued rate makes imports cheap and exports dear.", tier="approfondissement")],
       P("The exchange rate was 650 FCFA per dollar and becomes 585 FCFA per dollar.",
         [pm("Has the FCFA appreciated or depreciated against the dollar?", "Appreciated", ["Depreciated", "Fixed", "Unchanged"], "Fewer FCFA are needed for a dollar.", 1),
          pn("What is the price in FCFA of an import costing 200 dollars at the new rate?", 117000, "200 × 585 = 117 000.", 0, "FCFA", 1),
          po("State the likely effect on exporters of cocoa priced in dollars.", "Dollar earnings are worth fewer FCFA, so exporters' income in FCFA falls and competitiveness may fall.", ["Income in FCFA falls (1)", "Competitiveness (1)"], 2)]),
       sc11, (fig, "The market for dollars: the equilibrium rate is 11 and a fixed rate set at 8 would create excess demand for dollars.", "Downward demand and upward supply lines meeting at the market rate with a horizontal dashed line below it labelled fixed rate."),
       notes=["The 1994 CFA franc devaluation (50 % against the French franc, January 1994) is widely documented; verify date and size and treat it neutrally.", "FCFA per dollar values are illustrative, not actual rates.", NOTE_NUM])
assert 200 * 585 == 117000

# =====================================================================  CH 5  DEVELOPMENT AND THE CAMEROONIAN ECONOMY
c5 = p.chapter("development", "Development economics and the Cameroonian economy", programRef="GCE A Level Economics — Economic development; the Cameroonian economy (to be checked)")

# ---- 12 development economics
gini = 1 - 2 * (1 / 3); assert abs(gini - 1 / 3) < 1e-12
hdi = (0.6 * 0.5 * 0.55) ** (1 / 3); assert abs(hdi - 0.5481) < 1e-3
gini2 = 1 - 2 * 0.35; assert abs(gini2 - 0.30) < 1e-12
fig = plot(0, 1, 0, 1, curves=[("x", "grey", "equality"), ("x^2", "blue", "Lorenz curve")], xlabel="cumulative share of population", ylabel="cumulative share of income", grid=0.25, h=280)
sc12 = [
    ("Economic development differs from economic growth because it also includes", "improvements in health, education and living standards", ["only a rise in GDP", "only exports", "only investment"], "Development is broader and about people."),
    ("The Human Development Index combines", "income, education and life expectancy", ["income, exports and imports", "growth, inflation and unemployment", "tax, spending and debt"], "Three dimensions."),
    ("A Lorenz curve further from the line of equality shows", "greater inequality", ["less inequality", "no inequality", "higher growth"], "The Gini coefficient is larger."),
    ("The Gini coefficient lies between", "0 and 1", ["−1 and 1", "0 and 100 million", "1 and 10"], "0 is perfect equality, 1 maximum inequality."),
    ("Which is a common obstacle to development?", "Heavy debt and poor infrastructure", ["High savings", "High productivity", "Good institutions"], "Others: poverty traps, low human capital."),
]
lesson(c5, "development", "Economic development: measurement, obstacles and strategies",
       ["Distinguish growth from development and describe indicators such as the HDI and the Gini coefficient.", "Explain obstacles to development and strategies for development.", "Evaluate aid, trade and debt relief."],
       [("definition", "Growth and development", "**Economic growth** is a rise in real GDP; **economic development** is a wider improvement in **living standards, health, education, freedom and reduced poverty and inequality**. A country can grow without developing if the gains are concentrated or the environment is damaged. **Indicators**: GDP per capita, **HDI** (a composite of life expectancy, education and income, combined as a geometric mean of three indices), poverty headcount, infant mortality, literacy and **inequality measures**."),
        ("formule", "Lorenz curve and Gini coefficient", "A **Lorenz curve** plots the cumulative share of income against the cumulative share of the population; the diagonal is perfect equality. **Gini coefficient** = area between the diagonal and the Lorenz curve ÷ area under the diagonal = **1 − 2 × (area under the Lorenz curve)**. 0 means perfect equality; 1 means one person has everything. The HDI = (health index × education index × income index)^(1/3)."),
        ("retenir", "Obstacles and strategies", "**Obstacles**: low incomes and a poverty cycle (low saving and investment), debt burden, dependence on primary commodities with volatile prices, weak infrastructure (energy, roads), limited human capital, weak institutions, conflict and disease. **Strategies**: investing in education and health, infrastructure, diversification and industrialisation, agricultural productivity, microfinance, regional integration, attracting FDI, **aid** and **debt relief**, good governance and macroeconomic stability."),
        ("pieges", "Evaluate, do not just list", "Aid can fund health, education and infrastructure but may create dependency, be tied to donor goods, or be misused; trade openness can raise growth but may expose countries to commodity price shocks; debt relief frees resources but needs conditions to be effective. Avoid generalisations about 'Africa': conditions differ between countries. Link indicators to evidence and state their limits (averages hide inequality; the HDI omits environmental and political freedom).")],
       [("Worked example — Gini coefficient", "A Lorenz curve is y = x². The area under it is 1/3. Calculate the Gini coefficient.", ["Gini = 1 − 2 × area under Lorenz curve.", "= 1 − 2 × 1/3.", "= %.2f." % gini], "0.33"),
        ("Worked example — HDI", "A country has a health index of 0.60, an education index of 0.50 and an income index of 0.55. Calculate the HDI (geometric mean).", ["HDI = (0.60 × 0.50 × 0.55)^(1/3).", "Product = 0.165.", "Cube root of 0.165 = %.2f." % hdi], "About 0.55")],
       [M("The Gini coefficient is 0 when", "everyone has the same income", ["one person has all the income", "income is falling", "GDP is zero"], "Perfect equality."),
        TF("Economic growth and economic development mean exactly the same thing.", False, "Development includes health, education and distribution."),
        N("The area under a Lorenz curve is 0.35. Calculate the Gini coefficient.", gini2, "1 − 2 × 0.35 = 0.30.", tol=0.001)],
       [O("Evaluate the role of foreign aid in promoting development.", "Aid can finance health, education and infrastructure and relieve poverty, but it may create dependency, come with conditions or be tied to donor goods, and may be wasted through weak governance; trade and domestic investment are also important.", ["Benefit (1)", "Limitation (1)", "Judgement (1)"]),
        M("Which statement about a country with a rising GDP per capita but increasing inequality is most accurate?", "It has experienced growth but not necessarily development", ["It has developed fully", "Growth has fallen", "Inequality cannot rise during growth"], "Gains may be unevenly shared.", tier="approfondissement")],
       P("Two imaginary countries have the same GDP per capita. Country X has a Gini coefficient of 0.25; Country Y has 0.55.",
         [pm("Which country has the more equal distribution of income?", "X", ["Y", "They are equal", "Cannot tell"], "Lower Gini means more equal.", 1),
          pn("Area under the Lorenz curve for Y.", round((1 - 0.55) / 2, 3), "Gini = 1 − 2A so A = (1 − 0.55) ÷ 2 = 0.225.", 0.001, None, 2),
          po("Explain why GDP per capita alone is an incomplete measure of development.", "It is an average and ignores distribution, health, education and environmental quality; two countries with the same GDP per capita may have very different living standards.", ["Average hides inequality (1)", "Other dimensions omitted (1)"], 2)]),
       sc12, (fig, "A Lorenz curve (y = x²) below the line of equality: the Gini coefficient is 1/3.", "A diagonal line of perfect equality and a curve below it from corner to corner."),
       notes=["The HDI formula is the current UNDP geometric-mean method; older versions used an arithmetic mean. Check which version the syllabus uses.", NOTE_NUM, NOTE_ORDER])
assert abs((1 - 0.55) / 2 - 0.225) < 1e-12

# ---- 13 Cameroonian economy
rev = 20_000 * 1200; assert rev == 24_000_000
tot2 = 120 / 100 * 100
fig = tabfig(["Sector", "Examples (qualitative)"], [["Primary", "cocoa, coffee, cotton, banana, timber, fishing"], ["Oil and gas", "crude oil extraction, refining"], ["Secondary", "agro-processing, brewing, construction"], ["Tertiary", "trade, transport, telecoms, banking"], ["Informal", "street trade, small farms, transport"]], w=470, colw=[110, 360], size=12, title="Structure of the Cameroonian economy")
sc13 = [
    ("Cameroon is a member of", "CEMAC, whose common currency is the FCFA", ["UEMOA with the CFA franc of BCEAO", "the euro area", "the dollar zone"], "Central African currency union."),
    ("Which of these is a traditional cash crop export of Cameroon?", "Cocoa", ["Wheat", "Tulips", "Tea only"], "Cocoa, coffee, cotton and bananas are major cash crops."),
    ("A large informal sector means that", "many workers and firms are unregistered and not covered by regulation or taxes", ["everyone pays tax", "there is no unemployment", "firms are all large"], "Informality affects revenue and productivity."),
    ("Dependence on a few primary commodity exports exposes an economy to", "volatile world prices", ["stable revenue", "no external shocks", "higher industrial output"], "Export earnings fall when prices fall."),
    ("Douala is important to the economy mainly because it", "is the main seaport and commercial centre", ["is the political capital", "has no industry", "is landlocked"], "It serves Cameroon and neighbouring landlocked countries."),
]
lesson(c5, "cameroon", "The Cameroonian economy: structure, trade and challenges",
       ["Describe the main sectors and exports of the Cameroonian economy.", "Explain the importance of agriculture, oil and the informal sector.", "Evaluate challenges and strategies such as diversification, processing and regional integration."],
       [("definition", "Structure (qualitative)", "Cameroon has a **mixed economy** with a diversified resource base for central Africa. The **primary sector** includes **agriculture** (cocoa, coffee, cotton, bananas, rubber, palm oil, cassava, maize, plantain), **livestock**, **forestry (timber)** and **fishing**; a large share of the population depends on farming. **Crude oil** has been an important export and source of government revenue. **Industry** includes agro-processing, brewing, construction and some manufacturing; **services** (trade, transport, telecommunications, banking) are large, and the **informal sector** employs many people."),
        ("retenir", "Trade and regional role", "Typical **exports** are crude oil, timber, cocoa, bananas, cotton and coffee; typical **imports** are fuel products, machinery, vehicles and some foodstuffs such as rice and wheat. The seaport of **Douala** (and the newer deep-sea port at **Kribi**) is a gateway for trade with landlocked neighbours such as Chad and the Central African Republic. Cameroon belongs to the **CEMAC** and uses the **FCFA**. Cash crops are produced mainly by smallholders in cocoa-growing areas of the Centre, South and Southwest and cotton in the North."),
        ("pieges", "Be careful with statistics", "Avoid quoting percentages or monetary values from memory: GDP, export shares, growth and unemployment change every year and different sources disagree. Use qualitative comparisons ('oil has been a major export') unless you have a current official source. Do not generalise from a single region: farming systems in the Northwest differ from those of the coast. Keep a neutral tone when discussing policy and regional issues."),
        ("retenir", "Challenges and strategies", "**Challenges**: dependence on commodity exports with volatile prices, limited industrial processing, infrastructure gaps (energy, roads), a large informal sector with low productivity and little tax contribution, youth unemployment and underemployment, climate and pests affecting crops, and a heavy burden of imported finished goods. **Strategies**: **diversification**, **local processing** (e.g. cocoa into chocolate, cotton into cloth), improved infrastructure, vocational training, access to credit for farmers and small firms, better governance, and **regional integration**.")],
       [("Worked example — export revenue", "A cooperative in the South region sells 20 000 kg of cocoa at an illustrative price of 1200 FCFA per kg. Calculate revenue. If the price falls 20 % what is the new revenue?", ["Revenue = 20 000 × 1200 = %s FCFA." % format(rev, ","), "A 20 % fall gives a price of 960 FCFA.", "New revenue = 20 000 × 960 = %s FCFA." % format(20000 * 960, ",")], "24 000 000 FCFA then 19 200 000 FCFA"),
        ("Worked example — value added by processing", "Raw cocoa is exported for 1200 FCFA per kg; processed into cocoa butter and powder it sells for the equivalent of 2000 FCFA per kg of beans. Calculate the value added and say why this matters.", ["Value added = 2000 − 1200 = %d FCFA per kg of beans." % (2000 - 1200), "The extra income can pay wages and profit in the home economy.", "Processing also reduces dependence on volatile raw-material prices."], "800 FCFA per kg; more jobs and income at home")],
       [M("Which of these is a primary-sector activity?", "Growing cocoa", ["Operating a bank", "Making soap in a factory", "Running a taxi company"], "Primary = extraction or farming."),
        TF("Cameroon has no informal sector.", False, "The informal sector is large."),
        N("A farmer sells 800 kg of coffee at 900 FCFA per kg. Calculate revenue (FCFA).", 800 * 900, "800 × 900 = 720 000.", unit="FCFA")],
       [O("Explain why dependence on primary commodity exports can create problems for an economy such as Cameroon's.", "Prices of primary commodities are volatile because demand and supply are inelastic, so export earnings and government revenue swing widely; the terms of trade may worsen over time relative to manufactured imports; dependence discourages diversification and industrial processing.", ["Price volatility (1)", "Revenue/terms of trade (1)", "Lack of diversification (1)"]),
        M("Which policy would most directly add value to Cameroon's cocoa exports?", "Processing cocoa locally before export", ["Exporting more raw beans", "Importing more chocolate", "Cutting tax on imports only"], "Processing captures more of the value chain.", tier="approfondissement")],
       P("A cooperative in the Southwest sells cocoa beans at an illustrative price of 1500 FCFA per kg and considers building a small processing unit that would sell cocoa butter worth 2100 FCFA per kg of beans.",
         [pn("Value added per kg of beans (FCFA).", 600, "2100 − 1500 = 600.", 0, "FCFA", 1),
          pn("Extra revenue for 10 000 kg of beans (FCFA).", 6_000_000, "600 × 10 000 = 6 000 000.", 0, "FCFA", 2),
          po("Suggest two challenges the cooperative may face in setting up processing.", "Lack of finance or credit, unreliable electricity, skills and equipment, and marketing/access to buyers.", ["Two valid challenges (2)"], 2)]),
       sc13, (fig, "The main sectors of the Cameroonian economy (qualitative).", "A table with five rows for primary, oil and gas, secondary, tertiary and informal sectors and examples of each."),
       notes=["QUALITATIVE ONLY: no GDP, export-share or employment statistics are given. All prices (FCFA per kg) are illustrative.", "Facts to verify: main export products, the role of Douala and Kribi ports, regional crop zones, the importance of cotton in the North, and Cameroon's current development strategy documents (e.g. Vision 2035) before use.", "Political and security issues affecting parts of the country are deliberately not discussed."])
assert (2100 - 1500) * 10000 == 6_000_000 and 800 * 900 == 720_000

# =====================================================================  MOCK PAPER
Lm = c5.lessons[-1]
mock_paper(p, Lm, "paper-1", "GCE A Level Economics — mock paper 1", 45,
           "Answer all four questions. Marks are shown for each part; the paper is marked out of 20. Show your working for calculations. This is an original practice paper; its format and level are to be checked against the official texts of the Cameroon GCE Board.",
           [("Question 1 — Aggregate demand and the multiplier (5 marks)", P("In an imaginary economy consumption is C = 100 + 0.75Y, investment is 50 and government spending is 50 (billion FCFA).",
              [pn("Calculate the marginal propensity to save.", 0.25, "MPS = 1 − MPC = 0.25.", 0.001, None, 1),
               pn("Calculate the equilibrium level of national income (billion FCFA).", 800, "Y = (100 + 50 + 50) ÷ 0.25 = 800.", 0, None, 2),
               pn("Government spending rises by 20. Calculate the change in income (billion FCFA).", 80, "Multiplier 4; 4 × 20 = 80.", 0, None, 1),
               po("State one reason why the real-world increase may be smaller.", "Some extra income is taxed or spent on imports, and prices may rise near full employment.", ["Valid reason (1)"], 1)])),
            ("Question 2 — Inflation and unemployment (5 marks)", P("The consumer price index rose from 150 to 159 in a year. In the labour force of 250 000 people, 15 000 are unemployed.",
              [pn("Calculate the rate of inflation (%).", 6, "(159 − 150) ÷ 150 × 100 = 6.", 0.01, "%", 1),
               pn("Calculate the unemployment rate (%).", 6, "15 000 ÷ 250 000 × 100 = 6.", 0.01, "%", 1),
               pm("A rise in the world price of oil, an input for many firms, is likely to cause", "cost-push inflation", ["demand-pull inflation", "deflation", "a fall in costs"], "SRAS shifts left.", 1),
               po("Explain how a rise in interest rates may reduce inflation.", "Higher interest rates raise the cost of borrowing and the reward for saving, so consumption and investment fall, AD falls or grows more slowly and demand-pull pressure on prices eases.", ["Borrowing and spending fall (1)", "AD falls, price pressure eases (1)"], 2)])),
            ("Question 3 — Money and the BEAC (5 marks)", P("Banks in an imaginary economy must keep 20 % of deposits as reserves.",
              [pn("Calculate the maximum money multiplier.", 5, "1 ÷ 0.20 = 5.", 0, None, 1),
               pn("A new deposit of 3 000 000 FCFA is made. Calculate the maximum total deposits it can support (FCFA).", 15_000_000, "3 000 000 × 5 = 15 000 000.", 0, "FCFA", 2),
               pm("Which institution issues the FCFA used in Cameroon?", "BEAC", ["BCEAO", "COBAC", "The IMF"], "BEAC is the central bank of the CEMAC.", 1),
               po("State one advantage of fixing the FCFA to the euro.", "It gives exchange-rate stability and low inflation, helping trade and planning with the euro area.", ["Valid advantage (1)"], 1)])),
            ("Question 4 — International economics (5 marks)", P("Imaginary data (billion FCFA): goods exports 3500, goods imports 4200; services exports 500, services imports 700; net primary income −300; net secondary income +400.",
              [pn("Calculate the current account balance (billion FCFA).", 3500 - 4200 + 500 - 700 - 300 + 400, "−700 − 200 − 300 + 400 = −800.", 0, None, 2),
               pn("Convert 100 euros into FCFA at 655.957 FCFA per euro.", 65595.7, "100 × 655.957 = 65 595.7.", 0.1, "FCFA", 1),
               pm("Depreciation of a currency improves the current account if", "the sum of the price elasticities of demand for exports and imports is greater than 1", ["demand is perfectly inelastic", "exports are fixed", "imports rise"], "Marshall–Lerner condition.", 1),
               po("Explain why a depreciation may worsen the current account in the short run.", "Contracts and habits delay quantity changes, so import costs rise before the volumes adjust (J-curve).", ["J-curve explained (1)"], 1)]))])
write_compact(p)
