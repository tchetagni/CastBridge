"""GCE O Level Economics - public finance, macro, trade, development, Cameroon, unions and consumers, mock."""
from eco_lib import *
TAG = "O Level — "

def build(p):
    # ================================================================ CH 5 PUBLIC FINANCE
    c5 = p.chapter("public-finance", "Public finance: taxes and the budget", programRef="GCE O Level Economics — government revenue and expenditure (to be checked)")

    def income_tax(y):
        t = 0
        if y > 100000: t += (min(y, 300000) - 100000) * 0.10
        if y > 300000: t += (y - 300000) * 0.20
        return t
    t400 = income_tax(400000); assert t400 == 40000; avg = t400 / 400000 * 100; assert avg == 10
    t_low = income_tax(200000); assert t_low == 10000
    vat = 20000 * 10 / 100; assert vat == 2000
    rev, spend = 62, 78; deficit = spend - rev; assert deficit == 16
    fig = bars([("Revenue", rev, "green"), ("Spending", spend, "red")], unit="billion FCFA (imaginary budget)", w=360, h=250)
    sc = [
        ("A direct tax is one that", "is paid directly to the government by the person or firm on whom it is placed", ["is included in the price of goods", "is paid only by foreigners", "is never paid by firms"], "Income tax and company tax are direct taxes."),
        ("Value added tax (VAT) is an example of", "an indirect tax", ["a direct tax", "a subsidy", "a loan"], "It is added to the price paid on goods and services."),
        ("A progressive tax takes", "a larger percentage of income as income rises", ["the same percentage from everyone", "a smaller percentage as income rises", "nothing from the poor and the rich"], "The tax rate increases with income."),
        ("A budget deficit occurs when", "government spending is greater than its revenue", ["revenue is greater than spending", "taxes equal spending", "the budget is not published"], "The gap must be financed by borrowing or other means."),
        ("Which is a good quality of a tax according to the canons of taxation?", "It is certain and fair to taxpayers", ["It changes every day", "It costs more to collect than it earns", "It is hidden from taxpayers"], "Equity, certainty, convenience and economy are the classic qualities."),
    ]
    lesson(c5, "taxes-budget", "Taxes, government spending and the budget",
        ["Define direct and indirect taxes and progressive, proportional and regressive taxes.", "Describe the purposes of taxation and the government budget.", "Calculate income tax, VAT and a budget balance."],
        [("definition", "Taxes", "A **tax** is a compulsory payment to the government. **Direct taxes** are paid directly by the person or firm on whom they fall: **income tax**, **company tax**. **Indirect taxes** are placed on goods and services and paid when buying: **VAT** (value added tax) and **customs duties** on imports. Governments use taxes to raise **revenue**, to redistribute income, to discourage harmful goods and to protect local industry."),
         ("retenir", "Progressive, proportional and regressive", "**Progressive**: the percentage of income paid in tax rises as income rises (income tax with bands). **Proportional**: the same percentage at all incomes. **Regressive**: the percentage falls as income rises (a flat tax on a good takes more of a poor person's income). Good taxes are **fair, certain, convenient** and **cheap to collect** (the canons of taxation)."),
         ("retenir", "The government budget", "The **budget** is the government's plan for **revenue** (taxes, income from state firms, oil and other resources, borrowing) and **expenditure** (education, health, roads, defence, wages of public workers, debt payments). If revenue is greater than spending there is a **surplus**; if smaller, a **deficit**, which is financed by borrowing at home or abroad. In Cameroon the budget is passed each year by Parliament as a finance law."),
         ("pieges", "Frequent mistakes", "Do not call any payment to government a tax: **fees** and **fines** are different. Indirect taxes are paid by consumers even though shops pay them to the state. A progressive tax is not the same as a **high** tax. A deficit is not the same as **national debt**: the debt is the total accumulated, the deficit is the one-year gap. Taxes can also change behaviour: high taxes may discourage work or encourage tax evasion.")],
        [("Worked example — progressive income tax", "In an imaginary system the first 100 000 FCFA of yearly income is untaxed, the next 200 000 is taxed at 10 % and anything above 300 000 at 20 %. Find the tax and the average rate for an income of 400 000 FCFA.", ["Band 1: 0.", "Band 2: 200 000 × 10 %% = %d. Band 3: 100 000 × 20 %% = %d." % (20000, 20000), "Total tax = %d FCFA; average rate = %d ÷ 400 000 × 100 = %d %%." % (t400, t400, avg)], "40 000 FCFA, an average rate of 10 %"),
         ("Worked example — VAT and the budget", "A shop sells a product for 20 000 FCFA before tax and VAT is charged at 10 % (illustrative rate). Find the final price. A government expects revenue of 62 and spends 78 (billion FCFA). What is the budget balance?", ["VAT = 20 000 × 10 ÷ 100 = %d FCFA, so the price is 22 000 FCFA." % vat, "Budget balance = revenue − spending = 62 − 78.", "= −%d billion FCFA, a deficit." % deficit], "22 000 FCFA; deficit of 16 billion FCFA")],
        [M("Which of the following is an indirect tax?", "Value added tax", ["Income tax", "Company profits tax", "A fine"], "VAT is added to the price."),
         TF("A progressive tax takes a higher percentage from higher incomes.", True, "Rates rise as income rises."),
         N("Using the same tax bands as the worked example (0 % to 100 000, 10 % up to 300 000, 20 % above), calculate the tax on an income of 250 000 FCFA.", income_tax(250000), "(250 000 − 100 000) × 10 % = 15 000.", unit="FCFA")],
        [O("Explain two reasons why the government taxes alcohol and tobacco more heavily than most goods.", "To raise revenue because demand is not very responsive to price, and to discourage consumption because these goods harm health and impose costs on society.", ["Revenue reason (1)", "Discouragement/health reason (1)", "Link to demand or social cost (1)"]),
         M("An imaginary government spends 90 billion FCFA and collects 84 billion FCFA. It has", "a deficit of 6 billion FCFA", ["a surplus of 6 billion FCFA", "a deficit of 174 billion FCFA", "a balanced budget"], "Spending is greater than revenue by 6.", tier="approfondissement")],
        P("Fotso earns 200 000 FCFA a year and Njie earns 400 000 FCFA, taxed under the imaginary bands in the lesson (0 % up to 100 000, 10 % to 300 000, 20 % above).",
          [pn("Tax paid by Fotso (FCFA)?", t_low, "(200 000 − 100 000) × 10 % = 10 000.", 0, "FCFA", 1),
           pn("Tax paid by Njie (FCFA)?", t400, "20 000 + 20 000 = 40 000.", 0, "FCFA", 1),
           pn("Average tax rate paid by Fotso (%)?", t_low / 200000 * 100, "10 000 ÷ 200 000 × 100 = 5 %.", 0.01, "%", 1),
           po("Is the system progressive? Give a reason.", "Yes: Fotso pays 5 % and Njie pays 10 % on average, so the average rate rises with income.", ["Yes with figures (2)"], 2)]),
        tag(TAG, sc), (fig, "An imaginary budget: spending of 78 is greater than revenue of 62, giving a deficit of 16.", "Two bars: revenue 62 and spending 78 in billion FCFA."),
        notes=["The VAT rate and tax bands are invented for teaching; actual Cameroonian rates are not given and must be taken from the current tax code.", "Annual passing of the finance law by Parliament is stated in outline.", NOTE_MONEY, NOTE_SYL])

    # ================================================================ CH 6 NATIONAL INCOME, POPULATION, LABOUR, INFLATION
    c6 = p.chapter("macro", "National income, population, labour and prices", programRef="GCE O Level Economics — national income, population, employment and inflation (to be checked)")

    # ---- 14 national income
    gdp, pop = 6000e9, 20e6; pc = gdp / pop; assert pc == 300000
    g1 = (2100 - 2000) / 2000 * 100; assert g1 == 5
    gpc = ((1.05 / 1.02) - 1) * 100; assert round(gpc, 2) == 2.94
    fig = bars([("Year 1", 52, "blue"), ("Year 2", 55, "blue"), ("Year 3", 59, "blue"), ("Year 4", 61, "blue")], unit="GDP, imaginary economy (billion FCFA)", w=380, h=250)
    sc = [
        ("Gross domestic product (GDP) is the", "total value of goods and services produced in a country in a year", ["total value of a country's imports", "government's tax revenue", "total savings of households"], "It measures output inside the borders."),
        ("National income can be measured by the", "output, income and expenditure methods", ["tax, tariff and quota methods", "supply, demand and price methods", "barter and credit methods"], "All three should give the same total."),
        ("GDP per head is found by", "dividing GDP by the population", ["multiplying GDP by population", "adding GDP to taxes", "dividing population by GDP"], "It is an average income per person."),
        ("Real GDP is", "GDP adjusted for changes in prices", ["GDP in current prices only", "GDP per head", "GDP without services"], "It removes the effect of inflation."),
        ("A limitation of GDP as a measure of well-being is that it", "ignores unpaid work and how income is shared", ["counts every person's wealth exactly", "includes only exports", "measures happiness directly"], "Housework and informal activity are partly missed and averages hide inequality."),
    ]
    lesson(c6, "national-income", "National income and living standards",
        ["Define national income and GDP.", "Describe the three measurement methods and calculate GDP per head and growth.", "Explain limitations of GDP as a measure of living standards."],
        [("definition", "National income and GDP", "**GDP** (gross domestic product) is the total value of final goods and services produced within a country in a year. **GNP/GNI** adds income earned abroad by citizens and subtracts income sent abroad by foreigners. National income can be measured by the **output** method (add value added), the **income** method (wages, rent, interest, profit) and the **expenditure** method (consumption, investment, government spending, exports minus imports)."),
         ("formule", "GDP per head and growth", "**GDP per head = GDP ÷ population**. **Growth rate of GDP = (new GDP − old GDP) ÷ old GDP × 100**. Economic **growth** is a rise in real GDP; **real** GDP is nominal GDP corrected for price changes. Per-head growth is approximately the growth rate of GDP minus the growth rate of population.", "\\text{GDP per head} = \\dfrac{\\text{GDP}}{\\text{population}}"),
         ("pieges", "Limitations of GDP", "GDP leaves out **unpaid work** (housework, caring) and much of the **informal sector** (small trading, farming for own use), so it may under-state output. It says nothing about **inequality**, **the environment**, **leisure** or **quality** of services. Rising GDP does not by itself mean better living standards. Always compare **real** values over time, not nominal values."),
         ("retenir", "Other indicators", "To judge living standards, economists also look at life expectancy, literacy, school enrolment, access to health care and clean water, poverty rates and employment. Composite measures such as the **Human Development Index** combine income, education and health. National income data help governments plan, compare years and compare countries.")],
        [("Worked example — GDP per head", "An imaginary country has a GDP of 6 000 billion FCFA and a population of 20 million. Find GDP per head.", ["6 000 billion = 6 000 × 10⁹ = 6 × 10¹² FCFA.", "20 million = 2 × 10⁷ people.", "GDP per head = 6 × 10¹² ÷ 2 × 10⁷ = %d FCFA." % pc], "300 000 FCFA"),
         ("Worked example — growth and growth per head", "Real GDP rises from 2 000 to 2 100 billion FCFA in a year (illustrative) while population rises by 2 %. Find the growth rate and the growth in GDP per head.", ["Growth = (2 100 − 2 000) ÷ 2 000 × 100 = %d %%." % g1, "GDP per head = real GDP ÷ population; it is multiplied by 1.05 ÷ 1.02.", "= %.2f, so GDP per head rises by about %.2f %%." % (1.05 / 1.02, gpc)], "5 % growth; about 2.9 % growth per head")],
        [M("Which of these is excluded from GDP?", "Unpaid housework", ["The wages of a teacher", "A new bridge", "Exports of cocoa"], "Unpaid work is not recorded."),
         TF("GDP per head is a perfect measure of how well off each person is.", False, "It is an average and ignores inequality and other aspects of life."),
         N("A country has GDP of 800 billion FCFA and a population of 4 million (imaginary). Find GDP per head in thousand FCFA.", 800e9 / 4e6 / 1000, "800 × 10⁹ ÷ 4 × 10⁶ = 200 000 FCFA = 200 thousand.", unit="thousand FCFA")],
        [O("Explain two reasons why a rise in GDP might not improve the lives of most people.", "The extra income may go mainly to a few people (inequality); prices may have risen so real output has not changed; population may have risen as fast as GDP; growth may damage the environment or exclude unpaid work.", ["First valid reason (1)", "Second valid reason (1)", "Development of one (1)"]),
         N("Nominal GDP rises from 3 000 to 3 300 billion FCFA while prices rise by 10 % (imaginary). By what percentage has real GDP changed?", 0, "3 300 ÷ 3 000 = 1.10, and prices also rose by 1.10, so real GDP is unchanged (0 %).", unit="%", tier="approfondissement")],
        P("An imaginary economy has a GDP of 52 billion FCFA in year 1 and 55 billion in year 2 (see the figure). The population is 2 million in year 2.",
          [pn("Growth rate between year 1 and year 2 (%, one decimal place)?", round((55 - 52) / 52 * 100, 1), "3 ÷ 52 × 100 = 5.8 %.", 0.1, "%", 2),
           pn("GDP per head in year 2 (FCFA)?", 55e9 / 2e6, "55 × 10⁹ ÷ 2 × 10⁶ = 27 500 FCFA.", 0.5, "FCFA", 1),
           po("State one reason why this figure may not show the real standard of living.", "It is an average that hides inequality and ignores unpaid work and the informal sector.", ["Valid reason (1)"], 1)]),
        tag(TAG, sc), (fig, "An imaginary economy's GDP for four years, in billion FCFA.", "Four bars rising from year 1 to year 4."),
        notes=["The economy and numbers are imaginary and not Cameroon's actual data.", NOTE_MONEY, NOTE_SYL])

    # ---- 15 population
    n0, n14, n65 = 1000, 400, 40; wa = n0 - n14 - n65; dr = (n14 + n65) / wa * 100; assert wa == 560 and round(dr, 2) == 78.57
    birth, death = 38, 12; nat = (birth - death) / 10; assert nat == 2.6
    fig = bars([("0-14", n14, "orange"), ("15-64", wa, "blue"), ("65+", n65, "grey")], unit="people in an imaginary town", w=380, h=250)
    sc = [
        ("The birth rate is", "the number of live births per 1 000 people in a year", ["the total number of babies ever born", "the number of deaths per 1 000", "the number of marriages"], "Birth and death rates use rates per thousand."),
        ("Natural increase of population equals", "birth rate minus death rate", ["birth rate plus death rate", "immigration minus emigration", "population divided by area"], "Natural increase excludes migration."),
        ("The dependency ratio compares", "the young and old with the working-age group", ["men with women", "rich with poor", "urban with rural people"], "Dependants are usually those under 15 and over 64."),
        ("Rural-urban migration is the movement of people", "from the countryside to towns and cities", ["from towns to other countries only", "between two villages only", "from one continent to another only"], "It is common as people look for jobs and services."),
        ("A very young population with a high dependency ratio creates", "pressure on schools and health services", ["a fall in the need for schools", "lower birth rates at once", "no need for jobs"], "Many children need education and care while fewer adults earn."),
    ]
    lesson(c6, "population", "Population and its structure",
        ["Define birth rate, death rate, natural increase and migration.", "Describe age structure and the dependency ratio.", "Discuss effects of rapid population growth."],
        [("definition", "Population change", "Population changes through **births, deaths and migration**. The **birth rate** and **death rate** are the number of live births and of deaths per **1 000** people in a year. **Natural increase = birth rate − death rate** (per 1 000). **Net migration = immigrants − emigrants**. Population grows if natural increase plus net migration is positive. Many developing countries have high birth rates and a falling death rate."),
         ("retenir", "Age structure and dependency", "The **age structure** shows the shares of children (under 15), adults of working age (15 to 64) and older people (65 and over). The **dependency ratio = (number under 15 + number 65 and over) ÷ number aged 15 to 64 × 100**. A high ratio means each worker supports many dependants. Countries with many young people need many schools and later many jobs, while countries with many old people need pensions and health care."),
         ("retenir", "Distribution and migration", "**Population density** = population ÷ area. Population is not spread evenly: it is higher near ports, cities and fertile land. **Rural-urban migration** moves people from farms to towns, which can cause crowded housing, pressure on services and unemployment, while leaving fewer workers in agriculture. **Emigration** and **immigration** also change the labour force and skills."),
         ("pieges", "Be careful", "A growing population is not always a problem: it depends on **resources, production and jobs**. **Optimum population** is the size at which output per head is highest. A high birth rate does not mean population must keep rising if the death rate is also high. Always state units (per 1 000). Do not quote real national statistics unless you are certain: use illustrative numbers.")],
        [("Worked example — natural increase", "In an imaginary region the birth rate is 38 per 1 000 and the death rate is 12 per 1 000. Find natural increase per 1 000 and as a percentage per year.", ["Natural increase = 38 − 12 = 26 per 1 000.", "As a percentage: 26 ÷ 1 000 × 100 = %.1f %%." % nat, "If there is no migration the population grows by 2.6 % a year."], "26 per 1 000, or 2.6 % a year"),
         ("Worked example — dependency ratio", "An imaginary town has 1 000 people: 400 aged under 15, 40 aged 65 or over and the rest aged 15 to 64. Find the dependency ratio.", ["Working-age group = 1 000 − 400 − 40 = %d." % wa, "Dependants = 400 + 40 = 440.", "Ratio = 440 ÷ 560 × 100 = %.1f." % dr], "About 78.6 dependants per 100 workers")],
        [M("The death rate is measured per", "1 000 people", ["10 people", "100 000 people", "household"], "Rates per thousand are standard."),
         TF("Natural increase takes migration into account.", False, "It is only births minus deaths."),
         N("A town has a birth rate of 30 per 1 000 and a death rate of 10 per 1 000 (illustrative). Find natural increase per 1 000.", 30 - 10, "30 − 10 = 20.", unit="per 1 000")],
        [O("Explain two problems that rapid population growth may cause for a developing country.", "Pressure on schools, health care, housing and food supply; need for many jobs; resources shared among more people so output per head may fall; rapid urban growth.", ["First problem (1)", "Second problem (1)", "Explanation (1)"]),
         N("A village has 300 children under 15, 60 people aged 65 or over and 640 people aged 15 to 64 (imaginary). Find the dependency ratio as a percentage, to one decimal place.", round((300 + 60) / 640 * 100, 1), "360 ÷ 640 × 100 = 56.25, about 56.3.", tol=0.1, unit="%", tier="approfondissement")],
        P("Imaginary town data: 1 000 people, 400 under 15, 40 aged 65+; births 36 and deaths 9 in the year; 20 people moved in and 6 moved out.",
          [pn("Natural increase (number of people)?", 36 - 9, "36 − 9 = 27.", 0, "people", 1),
           pn("Net migration?", 20 - 6, "20 − 6 = 14.", 0, "people", 1),
           pn("Total change in population?", 27 + 14, "27 + 14 = 41.", 0, "people", 1),
           po("Describe one effect of a high share of under-15s.", "More schools, teachers and child health services are needed, and later many jobs will be needed.", ["Valid effect (1)"], 1)]),
        tag(TAG, sc), (fig, "An imaginary town's population by age group.", "Three bars for ages 0 to 14, 15 to 64 and 65 and over."),
        notes=["All population numbers are imaginary; Cameroon's age structure is described only qualitatively elsewhere.", NOTE_SYL])
    assert 36 - 9 + 20 - 6 == 41

    # ---- 16 labour and unemployment
    lf = 60000 + 15000; ur = 15000 / lf * 100; assert lf == 75000 and ur == 20
    fig = vflow(["Total population: 140 000 (imaginary)", "Working-age people: 100 000", "Labour force: 75 000", "Employed 60 000 | Unemployed 15 000"], w=440, bw=380, bh=30, gap=14, title="Who is in the labour force?")
    sc = [
        ("The labour force consists of", "people who are working or actively seeking work", ["all the people in the country", "only government workers", "only students"], "Employed plus unemployed."),
        ("Unemployment rate is calculated as", "unemployed ÷ labour force × 100", ["employed ÷ population × 100", "unemployed ÷ population under 15", "labour force ÷ unemployed"], "It is expressed as a percentage of the labour force."),
        ("Seasonal unemployment is common among", "farm and tourism workers", ["central bankers", "civil servants with permanent posts", "teachers at all times"], "Demand for their work varies with the seasons."),
        ("Structural unemployment is caused by", "the decline of an industry or a mismatch of skills", ["a short wait between jobs", "bad weather only", "a public holiday"], "Skills no longer match the jobs available."),
        ("Underemployment means", "working fewer hours or at a lower skill level than one could", ["having two full-time jobs", "retiring early", "paying no taxes"], "People work but not fully."),
    ]
    lesson(c6, "labour-unemployment", "The labour force and unemployment",
        ["Define labour force, employment and unemployment.", "Explain types, causes and effects of unemployment.", "Calculate the unemployment rate."],
        [("definition", "Labour force and unemployment", "The **labour force** (working population) is everyone **able and willing to work**: the employed plus those unemployed who are **seeking** work. Students, full-time carers and retired people are **not** in it. **Unemployment** is being without a job but able, willing and looking for one. **Unemployment rate = unemployed ÷ labour force × 100**. **Underemployment** is working less than one could (part-time when full-time wanted)."),
         ("retenir", "Types of unemployment", "**Frictional**: short gaps between jobs. **Seasonal**: work depends on the season (farm labour, tourism). **Structural**: an industry declines or skills do not match jobs. **Cyclical**: not enough demand in a downturn. **Disguised**: more workers than needed, common on small family farms. Many developing countries also have a large **informal sector** of small traders and artisans, which is hard to measure."),
         ("retenir", "Causes, effects and cures", "**Causes**: rapid growth of the labour force, lack of skills or capital, weak demand, rural-urban migration. **Effects**: lost output, lower incomes, poverty, crime and social problems, and a cost to government. **Cures**: investment and training, support for small firms and farming, better infrastructure, job-creation programmes, and improved links between schools and employers."),
         ("pieges", "Counting mistakes", "Do not divide the unemployed by the **population**: the denominator is the **labour force**. People who have stopped looking for work may not be counted. A person doing a little informal work may be counted as employed even though underemployed. Official figures may differ by definition, so do not quote real national unemployment rates unless certain; use illustrative data.")],
        [("Worked example — unemployment rate", "In an imaginary town 60 000 people are employed, 15 000 are unemployed and seeking work, and 25 000 are students, retirees or carers. Find the labour force and the unemployment rate.", ["Labour force = employed + unemployed = 60 000 + 15 000 = %d." % lf, "The 25 000 others are not in the labour force.", "Unemployment rate = 15 000 ÷ 75 000 × 100 = %d %%." % ur], "75 000; 20 %"),
         ("Worked example — identify the type", "Name the type of unemployment: (a) a cocoa picker with no work between harvests, (b) a graduate who spends two months looking for a first job, (c) typists after offices adopt computers.", ["(a) Work depends on the season: seasonal.", "(b) A short gap while searching: frictional.", "(c) Skills no longer match the jobs: structural."], "(a) seasonal, (b) frictional, (c) structural")],
        [M("Which of these is part of the labour force?", "A person seeking work", ["A full-time student", "A retired teacher", "A full-time carer at home"], "Those actively seeking work are counted as unemployed and in the labour force."),
         TF("Seasonal unemployment is caused by changes in demand for a product at different times of year.", True, "Farm and tourism work vary with the season."),
         N("An imaginary town has a labour force of 40 000 of whom 6 000 are unemployed. Find the unemployment rate in %.", 6000 / 40000 * 100, "6 000 ÷ 40 000 × 100 = 15 %.", unit="%")],
        [O("Explain two measures a government could use to reduce structural unemployment.", "Retraining programmes so workers gain new skills; investment and support for new industries; improved education linked to employers' needs; help for workers to move to areas with jobs.", ["First measure (1)", "Second measure (1)", "Link to structural cause (1)"]),
         M("Graduates in a small village who work as farm labourers but want office jobs are best described as", "underemployed", ["frictionally unemployed", "outside the labour force", "unemployed by choice"], "They work but not at their skill level.", tier="approfondissement")],
        P("An imaginary region has 200 000 working-age people. 120 000 are employed, 30 000 are unemployed and seeking work, and the rest are not seeking work.",
          [pn("Labour force?", 150000, "120 000 + 30 000 = 150 000.", 0, None, 1),
           pn("Unemployment rate (%)?", 20, "30 000 ÷ 150 000 × 100 = 20 %.", 0.01, "%", 2),
           po("Why might the true level of joblessness be higher than this rate?", "People who have given up looking are not counted, and underemployed people are counted as employed.", ["Valid reason (1)"], 1)]),
        tag(TAG, sc), (fig, "From total population to the labour force and unemployed people (imaginary numbers).", "Four boxes joined by arrows from total population to employed and unemployed."),
        notes=["Numbers are imaginary. Definitions of unemployment in official statistics may differ from the simple one used here.", NOTE_SYL])

    # ---- 17 inflation
    cpi = (50 * 120 + 20 * 110 + 30 * 105) / 100; assert cpi == 113.5
    rate = (cpi - 100) / 100 * 100; assert rate == 13.5
    fig = tabfig(["", "Demand-pull", "Cost-push"], [["Cause", "too much spending", "rising costs"], ["Example", "easy credit", "dearer fuel"], ["Result", "prices rise", "prices rise"], ["Cure", "cut demand", "cut costs"]], w=440, colw=[90, 175, 175], size=12, title="Two main causes of inflation")
    sc = [
        ("Inflation is", "a sustained rise in the general level of prices", ["a fall in all prices", "a rise in the price of one good only", "a rise in output"], "It is a continuing general price rise."),
        ("The consumer price index (CPI) measures", "changes in the cost of a typical basket of goods and services", ["the total value of GDP", "the profits of firms", "the level of exports"], "Weights show the importance of each item."),
        ("Demand-pull inflation is caused by", "total demand exceeding total supply", ["a fall in wages", "a fall in the money supply", "cheaper imports"], "Too much money chasing too few goods."),
        ("Which group is usually hurt most by inflation?", "People on fixed incomes", ["Borrowers with fixed-rate debts", "Owners of land whose price rises", "Importers only"], "Their purchasing power falls."),
        ("A government could reduce inflation by", "reducing spending or raising interest rates", ["printing more money", "raising wages by large amounts", "cutting taxes sharply during a boom"], "These reduce total demand."),
    ]
    lesson(c6, "inflation", "Inflation and the price index",
        ["Define inflation and describe how a price index is built.", "Explain causes, effects and ways of reducing inflation.", "Calculate an index and an inflation rate."],
        [("definition", "Inflation and the CPI", "**Inflation** is a **sustained rise in the general level of prices**, so money buys less. The **consumer price index (CPI)** measures it. Statisticians choose a typical **basket** of goods, give each a **weight** according to its share of spending, compare prices with a **base year** (index 100) and combine them. **Inflation rate = (new CPI − old CPI) ÷ old CPI × 100**. **Deflation** is a fall in the general price level."),
         ("retenir", "Causes of inflation", "**Demand-pull**: total demand grows faster than output (easy credit, high government spending, a rise in exports). **Cost-push**: costs such as wages, fuel or imported inputs rise and firms pass them on. **Imported inflation**: dearer imports raise costs. Inflation may also rise when the **money supply** grows faster than output."),
         ("retenir", "Effects and policies", "**Effects**: savers and people on fixed incomes lose; borrowers with fixed repayments gain; exports can become less competitive; uncertainty discourages investment. **Policies**: **monetary** (central bank raises interest rates, controls the money supply), **fiscal** (cut government spending, raise taxes) and **supply-side** (raise output and productivity). In the CEMAC zone the BEAC conducts monetary policy for all six members."),
         ("pieges", "Index mistakes", "A rise in the **index** is not the **rate** of inflation: compute the percentage change. If the index rises from 120 to 126 the rate is 5 %, not 6 %. A slower rise in prices (lower inflation) is **not** a fall in prices. Weights must add up to the right total (100 or 1). Do not compare indices from different base years without rebasing.")],
        [("Worked example — weighted index", "A basket has weights food 50, transport 20, housing 30 (total 100). Since the base year, price indices are food 120, transport 110, housing 105 (illustrative). Find the overall index and the inflation since the base year.", ["Weighted sum = 50 × 120 + 20 × 110 + 30 × 105 = 6 000 + 2 200 + 3 150 = 11 350.", "Index = 11 350 ÷ 100 = %.1f." % cpi, "Inflation since the base year = (113.5 − 100) ÷ 100 × 100 = %.1f %%." % rate], "Index 113.5; 13.5 % since the base year"),
         ("Worked example — annual inflation rate", "The CPI rises from 120 to 126 in one year (illustrative). Find the rate of inflation. A worker's wage rises by 3 % in the same year. Did real wages rise?", ["Inflation = (126 − 120) ÷ 120 × 100 = %.0f %%." % ((126 - 120) / 120 * 100), "Wages rose by 3 %, prices by 5 %.", "Wages rose less than prices, so real wages fell."], "5 %; real wages fell")],
        [M("Which of the following causes cost-push inflation?", "A rise in the price of fuel used by firms", ["A fall in consumer spending", "A fall in costs", "An increase in unemployment"], "Higher costs push prices up."),
         TF("A fall in the rate of inflation means prices are falling.", False, "Prices are still rising, but more slowly."),
         N("The CPI rises from 150 to 159 (illustrative). Find the inflation rate in %.", (159 - 150) / 150 * 100, "9 ÷ 150 × 100 = 6 %.", unit="%")],
        [O("Explain who gains and who loses from unexpected inflation.", "Savers and people on fixed incomes lose because the money's buying power falls. Borrowers with fixed-interest debts gain because they repay with less valuable money. Producers may gain if their selling prices rise faster than costs.", ["A loser (1)", "A gainer (1)", "Reason (1)"]),
         N("A basket has weights of 70 for food and 30 for transport. Food index 110 and transport index 130 (imaginary). Find the weighted index.", (70 * 110 + 30 * 130) / 100, "(7 700 + 3 900) ÷ 100 = 116.", unit=None, tier="approfondissement")],
        P("The CPI of an imaginary country was 200 last year and 214 this year. Wages rose by 4 %.",
          [pn("Inflation rate (%)?", 7, "14 ÷ 200 × 100 = 7 %.", 0.01, "%", 1),
           pm("Real wages", "fell, because prices rose faster than wages", ["rose", "stayed the same", "cannot be said"], "4 % is less than 7 %.", 1),
           po("Name one policy a central bank could use to reduce inflation and say how it works.", "Raise interest rates: borrowing becomes dearer, spending and investment fall, demand falls and price pressure eases.", ["Policy (1)", "Mechanism (1)"], 2)]),
        tag(TAG, sc), (fig, "Demand-pull and cost-push inflation compared.", "A table with two columns, demand-pull and cost-push, and rows for cause, example, result and cure."),
        notes=["Index weights and numbers are invented. Statistics of actual Cameroonian inflation are not given.", "BEAC conducting monetary policy for the six CEMAC members: stated in outline.", NOTE_SYL])

    # ================================================================ CH 7 TRADE
    c7 = p.chapter("trade", "International trade and the balance of payments", programRef="GCE O Level Economics — international trade (to be checked)")
    exp, imp = 520, 640; bot = exp - imp; assert bot == -120
    ca = 520 - 640 + 150 - 110 + 30; assert ca == -50
    fig = bars([("Exports", 520, "green"), ("Imports", 640, "red")], unit="goods, billion FCFA (imaginary)", w=360, h=250)
    sc = [
        ("Countries trade with each other mainly because", "they have different resources and can gain from specialisation", ["all countries make the same goods", "trade is forbidden", "money is free"], "Specialisation and exchange raise total output."),
        ("Visible trade consists of", "goods that can be seen and counted", ["services such as tourism", "transfers of money only", "loans from the IMF"], "Goods such as cocoa and machinery are visible."),
        ("The balance of trade is", "the value of visible exports minus the value of visible imports", ["exports plus imports", "imports minus services", "taxes minus spending"], "It considers goods only."),
        ("A tariff is", "a tax on imported goods", ["a subsidy to exporters", "a limit on the number of imports", "a loan"], "It raises the price of imports."),
        ("A quota is", "a limit on the quantity of a good that may be imported", ["a tax on imports", "a free trade agreement", "a rise in exports"], "It restricts quantity directly."),
    ]
    lesson(c7, "trade-bop", "International trade and the balance of payments",
        ["Explain why countries trade and the idea of comparative advantage.", "Distinguish visible and invisible trade, balance of trade and balance of payments.", "Describe protection methods and calculate trade balances."],
        [("definition", "Why countries trade", "Countries have different **resources, climate, skills and technology**, so each is better at making some goods. By **specialising** in what it makes at lower opportunity cost (**comparative advantage**) and trading, each can have more goods than by making everything itself. Trade also brings variety, larger markets and competition. Cameroon exports products such as crude oil, cocoa, coffee, bananas, timber and cotton, and imports machinery, vehicles and some foodstuffs."),
         ("retenir", "Visible, invisible and the accounts", "**Visible trade** = goods. **Invisible trade** = services (tourism, banking, shipping, insurance) and income flows. **Balance of trade = visible exports − visible imports**. The **balance of payments** records **all** money flowing into and out of a country: the **current account** (goods, services, income, transfers) and the **capital and financial accounts** (investment and loans). A deficit on one part must be financed by the others or by reserves."),
         ("retenir", "Free trade and protection", "**Protection** helps home industries by **tariffs** (taxes on imports), **quotas** (limits on quantity), **subsidies** to local producers and import bans. Arguments for it: protect **infant industries**, jobs and national security, and stop **dumping**. Arguments against: higher prices, less choice, retaliation by other countries and less efficient industries. Regional groupings such as the CEMAC try to promote trade among members."),
         ("pieges", "Frequent mistakes", "A trade **deficit** is not the same as a balance-of-payments deficit. A deficit in goods may be offset by surpluses on services or by capital inflows. Comparative advantage depends on **opportunity cost**, not on who can make the good with fewer workers. Do not describe tariffs as paid by foreign countries: they are paid by importers and usually passed on to **consumers** at home.")],
        [("Worked example — balance of trade", "In an imaginary year a country exports goods worth 520 billion FCFA and imports goods worth 640 billion FCFA. Find the balance of trade. Services exports are 150, services imports 110 and net transfers received are 30 (all billion FCFA). Find the current account balance.", ["Balance of trade = 520 − 640 = %d billion FCFA, a deficit." % bot, "Services balance = 150 − 110 = +40; transfers = +30.", "Current account = −120 + 40 + 30 = %d billion FCFA." % ca], "−120 (trade); −50 (current account)"),
         ("Worked example — comparative advantage", "In one day country A can make 12 tonnes of cocoa or 6 tonnes of timber. Country B can make 6 tonnes of cocoa or 4 tonnes of timber (illustrative). Which has comparative advantage in cocoa?", ["A: 1 tonne of cocoa costs 6 ÷ 12 = %.2f tonnes of timber." % (6 / 12), "B: 1 tonne of cocoa costs 4 ÷ 6 = %.2f tonnes of timber." % (4 / 6), "A's opportunity cost is lower, so A should specialise in cocoa and B in timber."], "Country A")],
        [M("Which of the following is invisible trade?", "Tourism services bought by foreigners in Limbe", ["Cocoa exported to Europe", "Imported machinery", "Timber shipped abroad"], "Services are invisible."),
         TF("A tariff is a tax on imports.", True, "It raises the price of imported goods."),
         N("A country exports goods worth 300 and imports goods worth 410 (billion FCFA, imaginary). Find the balance of trade.", 300 - 410, "300 − 410 = −110.", unit="billion FCFA")],
        [O("Explain two arguments for protecting a new local industry.", "Infant industries need time to grow and reach economies of scale; protection can save jobs; it can reduce dependence on imports; it can stop dumping.", ["First argument (1)", "Second argument (1)", "Development of one (1)"]),
         M("If country X can produce a good at a lower opportunity cost than country Y, then X has", "a comparative advantage in that good", ["an absolute disadvantage only", "no gain from trade", "a trade deficit"], "Definition of comparative advantage.", tier="approfondissement")],
        P("An imaginary country had these flows (billion FCFA): goods exports 400, goods imports 460, services exports 90, services imports 130, net transfers received 20.",
          [pn("Balance of trade?", 400 - 460, "400 − 460 = −60.", 0, None, 1),
           pn("Current account balance?", 400 - 460 + 90 - 130 + 20, "−60 − 40 + 20 = −80.", 0, None, 2),
           po("Suggest one way the country could reduce a deficit.", "Raise exports by supporting producers, improve quality, cut imports with tariffs or promote import substitution.", ["Valid way (1)"], 1)]),
        tag(TAG, sc), (fig, "Goods exports and imports of an imaginary country: imports exceed exports by 120.", "Two bars: exports 520 and imports 640 in billion FCFA."),
        notes=["Cameroon's main exports and imports are listed from general knowledge; confirm current composition before teaching more detail.", "All numbers are imaginary.", NOTE_SYL])

    # ================================================================ CH 8 DEVELOPMENT AND CAMEROON
    c8 = p.chapter("development", "Economic development and the Cameroonian economy", programRef="GCE O Level Economics — development; agriculture, industry and services (to be checked)")
    lit = 640 / 800 * 100; assert lit == 80
    fig = tabfig(["Indicator", "Low-income country", "High-income country"], [["Income per head", "low", "high"], ["Share in farming", "large", "small"], ["Literacy", "often lower", "high"], ["Life expectancy", "lower", "higher"], ["Savings", "low", "high"]], w=470, colw=[140, 165, 165], size=12, title="Typical differences (general pattern)")
    sc = [
        ("Economic growth means", "a rise in real output (real GDP) over time", ["a rise in prices", "a rise in the population only", "a fall in taxes"], "Real output increases."),
        ("Economic development is wider than growth because it includes", "better health, education and living standards", ["only a rise in profits", "only more imports", "only a bigger army"], "It covers improvements in people's lives."),
        ("A common feature of developing countries is", "a large share of the workforce in farming", ["very few young people", "the lowest dependency ratio in the world", "no trade"], "Agriculture often employs many workers at low productivity."),
        ("Over-dependence on a few primary exports is risky because", "world prices of these products may fall", ["they never change in price", "they cannot be sold", "they need no labour"], "Export income becomes unstable."),
        ("Diversification of the economy means", "developing a wider range of products and industries", ["producing only one good", "closing all farms", "stopping trade"], "It lowers dependence on one product."),
    ]
    lesson(c8, "development", "Economic growth and development",
        ["Distinguish economic growth from economic development.", "Describe indicators and obstacles of development.", "Explain measures to promote development."],
        [("definition", "Growth and development", "**Economic growth** is a rise in a country's real output (real GDP, or GDP per head). **Economic development** is wider: higher living standards, better health, education, nutrition, housing, a fairer sharing of income and sustainable use of resources. **Indicators**: GDP per head, literacy, school enrolment, life expectancy, child survival, access to clean water, poverty rates and the Human Development Index."),
         ("retenir", "Obstacles to development", "Common obstacles in developing countries: **low incomes and low savings**, shortage of **capital and skills**, **rapid population growth**, poor **infrastructure** (roads, electricity, ports), dependence on a few **primary exports**, **debt**, weak institutions and corruption, poor health and education. Climate and political instability can also hinder progress; each country's mix of obstacles differs."),
         ("retenir", "Measures to promote development", "**Education and training**; investing in **roads, power and water**; **diversifying** and **processing** raw materials to add value; supporting **small firms and farmers** with credit and technology; encouraging **savings and investment**; good governance; **regional trade** and foreign investment; and careful use of **aid** and loans. Environmental protection matters for lasting development."),
         ("pieges", "Beware of simple answers", "Growth does not guarantee development if benefits go to a few. Do not say that developing countries are poor 'because of their people': history, geography, trade terms and policies all matter. Do not quote exact rankings or indices for Cameroon unless certain. In an essay give **balanced** points: describe the obstacle, explain its effect and give a measure with its limit.")],
        [("Worked example — literacy rate", "In an imaginary district 800 adults are asked and 640 can read and write. What is the literacy rate?", ["Literate adults = 640 out of 800.", "Rate = 640 ÷ 800 × 100.", "= %d %%." % lit], "80 %"),
         ("Worked example — growth and development", "A country's GDP per head rises by 4 % in a year but all the gains go to a few city businesses and school enrolment falls. Has there been growth? development?", ["Real GDP per head rose: there was growth.", "The gains were not shared and schooling got worse.", "So development did not clearly improve: growth without development."], "Growth yes, development doubtful")],
        [M("Which of the following is an indicator of development?", "Life expectancy", ["The number of cars imported", "The price of one export", "The number of shops"], "It reflects health and living standards."),
         TF("Economic growth always leads to economic development.", False, "Growth may not be shared or improve health and education."),
         N("In a village 450 of 500 children aged 6 to 11 are enrolled in school (imaginary). What is the enrolment rate in %?", 450 / 500 * 100, "450 ÷ 500 × 100 = 90 %.", unit="%")],
        [O("Explain two ways that processing raw cocoa in Cameroon instead of exporting it unprocessed could help development.", "It adds value, raising export earnings; it creates jobs in factories and transport; it reduces dependence on raw exports; it can build skills and technology.", ["First way (1)", "Second way (1)", "Explanation (1)"]),
         M("A country that depends on one export for most of its income is vulnerable because", "a fall in that product's world price can cut income sharply", ["its currency is fixed", "it has many industries", "its taxes are too low"], "Lack of diversification.", tier="approfondissement")],
        P("A developing country's GDP per head rises from 400 000 to 424 000 FCFA (imaginary) while literacy rises from 70 % to 72 %.",
          [pn("Growth rate of GDP per head (%)?", (424000 - 400000) / 400000 * 100, "24 000 ÷ 400 000 × 100 = 6 %.", 0.01, "%", 1),
           pm("Literacy is an indicator of", "development", ["inflation", "tariffs", "dependence on exports"], "It measures education.", 1),
           po("Name two obstacles to development a country like this may face.", "Shortage of capital, poor roads and power, rapid population growth, debt, low skills.", ["Two valid obstacles (2)"], 2)]),
        tag(TAG, sc), (fig, "General differences between low-income and high-income countries (typical patterns, not data).", "A table with indicators in rows and low-income and high-income countries in columns."),
        notes=["Comparisons are general patterns; no statistics are given.", NOTE_SYL])

    # ---- 20 sectors of Cameroon
    fig = tabfig(["Sector", "Examples in Cameroon"], [["Agriculture", "cocoa, coffee, banana, cotton, cassava"], ["Forestry/fishing", "timber, river and sea fish"], ["Industry", "oil, brewing, cement, power"], ["Services", "trade, transport, banking, tourism"]], w=470, colw=[130, 340], size=12, title="Sectors of the Cameroonian economy")
    sh = [30, 25, 45]; assert sum(sh) == 100
    sc = [
        ("Which of these is a cash crop grown in Cameroon?", "Cocoa", ["Sand", "Gold coins", "Bricks"], "Cocoa is grown mainly for sale and export."),
        ("Which of these is a food crop widely grown in Cameroon?", "Cassava", ["Cocoa only", "Crude oil", "Rubber only"], "Cassava, plantain and maize are important staples."),
        ("Douala is important for the economy because it has", "the country's main seaport", ["Mount Cameroon", "the BEAC headquarters", "Lake Nyos"], "Many imports and exports pass through the port."),
        ("Processing raw materials before export", "adds value and creates jobs", ["lowers all prices to zero", "removes the need for farmers", "makes goods useless"], "Value added rises."),
        ("A major problem facing industry in many developing countries is", "unreliable power and poor transport", ["too many roads", "an excess of skilled labour", "no demand for any goods"], "Infrastructure limits growth."),
    ]
    lesson(c8, "sectors-cameroon", "Agriculture, industry and services in Cameroon",
        ["Describe the main activities of agriculture, industry and services in Cameroon.", "Explain the importance and problems of each sector.", "Interpret a simple sector-share calculation."],
        [("definition", "Agriculture", "Agriculture, with forestry and fishing, gives work to a large part of the people. **Cash crops** include cocoa, coffee, bananas, cotton, rubber and palm oil; **food crops** include cassava, plantain, maize, yams and groundnuts. Livestock farming and fishing add to food supply. Regions specialise: for example cocoa in the Centre and South, coffee in the West and North West, cotton in the North."),
         ("definition", "Industry and services", "**Industry** includes oil production and refining, food and drink processing, wood processing, cement, aluminium and electricity, mostly generated by water power. **Services** include trade, transport, banking, telecommunications, education, health and tourism. Douala is the main port and a gateway for neighbouring land-locked countries, and Yaoundé is the capital and a centre of administration."),
         ("retenir", "Importance and problems", "**Importance**: jobs, food, exports and foreign exchange, raw materials for factories, tax revenue, incomes in rural areas. **Problems**: poor feeder roads and storage, low yields and old methods, price changes of exports, shortage of credit, power cuts, competition from imports, **informal activity** that escapes tax and records. Many farmers and traders depend on small family units."),
         ("pieges", "Examples and exam style", "Always **name** the crop, region or industry and link it to an economic idea (value added, exports, diversification). Do not give invented figures such as the share of GDP of a sector; say 'a large share'. Remember Cameroon has several regions with different activities. A strong answer states **importance**, then a **problem**, then a **measure**.")],
        [("Worked example — sector shares", "In an imaginary economy agriculture produces 30 %, industry 25 % and services the remaining share. Find the share of services. If total GDP is 4 000 billion FCFA, find the value of services.", ["Services = 100 − 30 − 25 = %d %%." % sh[2], "Value of services = 45 ÷ 100 × 4 000.", "= %d billion FCFA." % (45 * 40)], "45 %; 1 800 billion FCFA"),
         ("Worked example — value of processing", "Cocoa beans worth 300 000 FCFA are processed in Cameroon into cocoa paste worth 420 000 FCFA (illustrative). Find the value added and the percentage rise in value.", ["Value added = 420 000 − 300 000 = %d FCFA." % 120000, "Percentage rise = 120 000 ÷ 300 000 × 100.", "= %d %%." % 40], "120 000 FCFA; 40 %")],
        [M("Which of these is a service?", "Banking", ["Cocoa growing", "Sawmilling", "Cement making"], "Banking is a service."),
         TF("Cassava is a cash crop only and is never eaten at home.", False, "Cassava is widely eaten as a food crop."),
         N("In an imaginary economy industry produces 20 % and agriculture 35 % of GDP. What percentage is services?", 100 - 20 - 35, "100 − 20 − 35 = 45.", unit="%")],
        [O("Explain two problems farmers in rural Cameroon face in getting their crops to market.", "Poor feeder roads that become impassable in the rainy season; lack of storage so perishable crops spoil; high transport costs; lack of market information; middlemen.", ["First problem (1)", "Second problem (1)", "Effect on income (1)"]),
         M("Which measure would best help to add value to Cameroon's cocoa exports?", "Building factories to process cocoa", ["Exporting more raw beans only", "Importing chocolate", "Cutting all cocoa farms"], "Processing adds value.", tier="approfondissement")],
        P("An imaginary region grows plantain and maize for sale in Douala. Roads are poor and about a quarter of the crop spoils on the way.",
          [pn("Out of 2 000 kg harvested, how many kg are lost if a quarter spoils?", 500, "2 000 ÷ 4 = 500.", 0, "kg", 1),
           pn("If the remaining crop sells at 150 FCFA per kg, what is the revenue?", (2000 - 500) * 150, "1 500 × 150 = 225 000 FCFA.", 0, "FCFA", 1),
           po("Suggest two ways to reduce losses.", "Better roads, drying or storing crops, faster transport, market centres near farms.", ["Two valid ways (2)"], 2)]),
        tag(TAG, sc), (fig, "Main economic activities in Cameroon, by sector (examples).", "A table listing four sectors with examples of activities in each."),
        notes=["Regional specialisations (cocoa in Centre/South, coffee in West/North West, cotton in North) are general knowledge and should be checked by a teacher.", "Statements about water-generated electricity and Douala as a gateway are qualitative.", "No sector-share statistics for Cameroon are given; the ones used are imaginary.", NOTE_SYL])

    # ================================================================ CH 9 SOCIETY
    c9 = p.chapter("society", "Trade unions and consumer protection", programRef="GCE O Level Economics — trade unions, consumer protection (to be checked)")
    w0, w1, infl = 60000, 66000, 6
    inc = (w1 - w0) / w0 * 100; assert inc == 10
    real = (1.10 / 1.06 - 1) * 100; assert round(real, 2) == 3.77
    u1, u2 = 1200 / 500 * 100, 2200 / 1000 * 100; assert (round(u1), round(u2)) == (240, 220)
    fig = tabfig(["Consumer right", "Meaning"], [["Safety", "goods must not harm"], ["Information", "true labels and prices"], ["Choice", "real alternatives"], ["Redress", "fair complaint handling"]], w=460, colw=[140, 320], size=12, title="Basic consumer rights")
    sc = [
        ("A trade union is", "an organisation of workers that protects their interests", ["a group of employers only", "a government ministry", "a type of bank"], "It acts for its members at work."),
        ("Collective bargaining is", "negotiation between a union and employers about pay and conditions", ["a strike", "an individual loan", "a government tax"], "Workers negotiate as a group."),
        ("Which is a weapon a union may use if talks fail?", "A lawful strike", ["Printing money", "Raising taxes", "Closing the border"], "Workers withhold their labour."),
        ("A consumer right is", "the right to accurate information about goods", ["the right to damage goods", "freedom from all prices", "the right to ignore the law"], "Labels and prices must be honest."),
        ("Which body or measure helps protect consumers?", "Product standards and inspection", ["Longer queues", "Higher import duties only", "Hidden prices"], "Standards and inspection reduce fake or unsafe goods."),
    ]
    lesson(c9, "unions-consumers", "Trade unions and consumer protection",
        ["Describe the aims and methods of trade unions and employers' associations.", "Explain consumer rights and ways of protecting consumers.", "Calculate wage rises, real wage changes and unit prices."],
        [("definition", "Trade unions", "A **trade union** is an organisation of workers that protects their interests: **pay, working hours, safety, job security** and fair treatment. Methods: **collective bargaining** with employers, representing members in disputes, lawful **strikes** or go-slows, and lobbying government. Employers also form **associations**. The law of each country sets rules on unions, strikes and labour relations."),
         ("retenir", "Effects of union action", "Unions can raise wages and improve conditions and give workers a voice. But very large wage rises may raise firms' costs and **prices**, or reduce the number of jobs; strikes cause lost output and income. In a country with a large **informal sector**, many workers are not in a union and have little protection. A balanced answer shows both sides."),
         ("definition", "Consumer protection", "**Consumers** are buyers who use goods and services. They need protection from **unsafe, fake, wrongly weighed or wrongly labelled** goods, false advertising and unfair prices. Basic rights include **safety, information, choice and redress** (a way to complain). Protection comes from **laws and standards**, inspection of weights, measures and food, **consumer associations**, price display and the courts. Cameroon has a law on consumer protection, flagged for teacher checking."),
         ("pieges", "Practical advice", "Do not confuse a union's **aim** (better pay) with its **method** (bargaining, strikes). A strike is a last resort, not the only method. Consumers also have duties: to check goods, keep receipts, report problems and avoid buying counterfeit goods. When comparing prices use the **unit price** (price per gram or litre), not the total price.")],
        [("Worked example — wages", "A worker earns 60 000 FCFA a month. After union talks the wage rises to 66 000 FCFA, while prices rise by 6 % (illustrative). Find the percentage rise in the wage and the approximate change in real wage.", ["Rise = 6 000 ÷ 60 000 × 100 = %d %%." % inc, "Prices rose 6 %, so real wage = 1.10 ÷ 1.06.", "= %.3f, so real wage rises by about %.1f %%." % (1.10 / 1.06, real)], "10 %; real wage up by about 3.8 %"),
         ("Worked example — unit price", "A 500 g tin of milk powder costs 1 200 FCFA and a 1 kg tin costs 2 200 FCFA (illustrative). Which is cheaper per 100 g?", ["500 g: 1 200 ÷ 5 = %d FCFA per 100 g." % (1200 / 5), "1 kg = 1 000 g: 2 200 ÷ 10 = %d FCFA per 100 g." % (2200 / 10), "The larger tin costs less per 100 g."], "The 1 kg tin (220 against 240 FCFA per 100 g)")],
        [M("Which is a method used by trade unions?", "Collective bargaining", ["Printing money", "Setting taxes", "Issuing licences"], "Unions negotiate with employers."),
         TF("Consumers have a right to accurate information on labels.", True, "Information is a basic consumer right."),
         N("A 2 litre bottle costs 1 500 FCFA (imaginary). What is the price per litre in FCFA?", 1500 / 2, "1 500 ÷ 2 = 750.", unit="FCFA")],
        [O("Explain two ways in which strong trade unions might both help and harm the economy.", "Help: higher wages raise living standards and spending; better conditions improve motivation. Harm: higher wage costs may raise prices or cut jobs; strikes reduce output.", ["A benefit (1)", "A cost (1)", "Link to economy (1)"]),
         M("Which action best protects consumers of packaged foods?", "Checking and enforcing quality and labelling standards", ["Removing all labels", "Banning all advertising of every good", "Raising prices"], "Standards and inspection reduce risk.", tier="approfondissement")],
        P("Teachers in an imaginary private school have a wage of 100 000 FCFA a month. The union asks for 12 % more, the employer offers 8 %, and prices are rising by 5 %.",
          [pn("Wage after the union's demand is met (FCFA)?", 112000, "100 000 × 1.12 = 112 000.", 0, "FCFA", 1),
           pn("Wage after the employer's offer (FCFA)?", 108000, "100 000 × 1.08 = 108 000.", 0, "FCFA", 1),
           pm("With 5 % inflation, the employer's offer would give", "a small real wage increase", ["a fall in real wage", "no change in wages", "a rise in prices of 8 %"], "108 ÷ 105 > 1.", 1),
           po("Give one argument the employer might use against the 12 % demand.", "Higher costs may force the school to raise fees or hire fewer teachers.", ["Valid argument (1)"], 1)]),
        tag(TAG, sc), (fig, "Four basic consumer rights.", "A table with rights in the first column and their meaning in the second."),
        notes=["The existence of a Cameroonian consumer-protection law is mentioned in outline; check its exact title and content before teaching it.", "Rules on union registration and strikes are not detailed.", NOTE_MONEY, NOTE_SYL])

    # ================================================================ MOCK
    Lm = c9.lessons[-1]
    d_a = lambda q: 36 - 3 * q; s_a = lambda q: 6 + 3 * q
    eq = (36 - 6) / 6; peq = d_a(eq); assert (eq, peq) == (5, 21)
    tc = 120000 + 150 * 800; tr = 400 * 800; assert (tc, tr) == (240000, 320000)
    be = 120000 / (400 - 150); assert be == 480
    mock_paper(p, Lm, "paper-1", "GCE O Level Economics — mock paper 1", 45,
        "Answer all four questions. Marks are shown for each part; the paper is marked out of 20. Show your working for calculations. This is an original practice paper whose format and level are to be checked against the official texts of the Cameroon GCE Board; all FCFA figures are illustrative.",
        [("Question 1 — Demand, supply and price (5 marks)", P("In a market for fish the weekly demand is P = 36 − 3Q and the supply is P = 6 + 3Q, where P is the price in hundreds of FCFA per kg and Q is in tonnes.",
            [pn("Find the equilibrium quantity (tonnes).", eq, "36 − 3Q = 6 + 3Q gives 6Q = 30, Q = 5.", 0.001, "tonnes", 2),
             pn("Find the equilibrium price (FCFA per kg).", peq * 100, "P = 36 − 15 = 21 hundred = 2 100 FCFA.", 0.5, "FCFA", 1),
             pm("A rise in incomes shifts the demand curve to the right. The new equilibrium price will be", "higher", ["lower", "unchanged", "zero"], "Greater demand raises price along the supply curve.", 1),
             po("State one cause of a fall in the supply of fish.", "A rise in fuel costs, bad weather, a tax on fishermen or a fall in the number of boats.", ["Valid cause (1)"], 1)])),
         ("Question 2 — Firms, costs and revenue (5 marks)", P("A tailoring workshop in Bamenda has fixed costs of 120 000 FCFA a month and variable costs of 150 FCFA per item. Each item sells for 400 FCFA. It makes and sells 800 items (illustrative).",
            [pn("Total cost (FCFA).", tc, "120 000 + 150 × 800 = 240 000.", 0, "FCFA", 1),
             pn("Profit (FCFA).", tr - tc, "400 × 800 = 320 000; 320 000 − 240 000 = 80 000.", 0, "FCFA", 2),
             pn("Break-even number of items.", be, "120 000 ÷ (400 − 150) = 480.", 0, "items", 1),
             po("State one advantage of forming a limited company for this workshop.", "Limited liability protects the owners' personal property and shares can raise extra capital.", ["Valid advantage (1)"], 1)])),
         ("Question 3 — Money, banks and public finance (5 marks)", P("Banks in an imaginary economy pay 3 % per year on deposits and charge 11 % on loans. A government plans to raise 85 billion FCFA and spend 92 billion FCFA.",
            [pn("A bank lends 5 000 000 FCFA for one year. How much interest does it earn at 11 % (FCFA)?", 550000, "5 000 000 × 11 ÷ 100 = 550 000.", 0, "FCFA", 1),
             pn("What is the government's budget balance (billion FCFA, negative for a deficit)?", 85 - 92, "85 − 92 = −7, a deficit.", 0, None, 1),
             pm("Which institution issues the FCFA used in Cameroon?", "BEAC", ["COBAC", "A commercial bank", "The tax office"], "BEAC is the central bank of the CEMAC.", 1),
             po("State two ways a government could finance a budget deficit.", "Borrowing from banks or the public, borrowing abroad, using reserves, or raising taxes later.", ["Two valid ways (2)"], 2)])),
         ("Question 4 — National income, labour and trade (5 marks)", P("An imaginary economy has GDP of 9 000 billion FCFA and a population of 30 million. The labour force is 12 million, of whom 1.8 million are unemployed. Goods exports are 700 and goods imports 820 (billion FCFA).",
            [pn("GDP per head (thousand FCFA).", 9000e9 / 30e6 / 1000, "9 × 10¹² ÷ 3 × 10⁷ = 300 000 FCFA = 300 thousand.", 0.5, "thousand FCFA", 1),
             pn("Unemployment rate (%).", 1.8 / 12 * 100, "1.8 ÷ 12 × 100 = 15 %.", 0.01, "%", 1),
             pn("Balance of trade (billion FCFA).", 700 - 820, "700 − 820 = −120.", 0, None, 1),
             po("State two effects of high unemployment on the economy.", "Loss of output and income, more poverty, lower tax revenue and higher costs of support, social problems.", ["Two valid effects (2)"], 2)]))])
