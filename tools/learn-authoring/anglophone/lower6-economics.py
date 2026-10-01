"""Generator: content/learn/lower6-economics (Economics - Lower Sixth, GCE A Level syllabus, first year). Run: python3 lower6-economics.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _l6 import *

p = Pack("lower6-economics", "Economics — Lower Sixth", level="Lower Sixth", subject="economie", cursus="secondary",
         description="Lower Sixth Economics notes with worked calculations, diagrams, graded exercises and self-checks: the economic problem, economic systems, demand and supply, elasticity, costs, market structures, labour markets, market failure, government intervention and national income.",
         programRef="Cameroon GCE Board — Advanced Level Economics syllabus, first year (Lower Sixth) — to be checked against the official texts")
NOTE_ORDER = "The split of topics between Lower and Upper Sixth is an editorial choice to be checked against the GCE Board syllabus."
NOTE_MONEY = "Prices and quantities are invented teaching figures in FCFA, not real market data."

# =====================================================================  CH 1  THE ECONOMIC PROBLEM
c1 = p.chapter("basics", "The economic problem and economic systems", programRef="GCE A Level Economics — Basic economic problem; economic systems (to be checked)")

# ---- 1 scarcity, choice, opportunity cost
fig = plot(0, 10, 0, 10, curves=[("sqrt(100-x^2)", "blue", "PPF")], points=[(6, 8, "A on curve", "green"), (3, 4, "B inside", "orange"), (9, 8, "C unattainable", "red")], xlabel="cocoa (tonnes)", ylabel="maize (tonnes)", h=280)
sc1 = [
    ("The basic economic problem is", "scarce resources but unlimited wants", ["too much money", "high prices", "unemployment only"], "Resources are limited relative to wants, so choices must be made."),
    ("Opportunity cost is", "the next best alternative given up", ["the money price of a good", "the cost of production", "the total of all alternatives"], "It is the value of the best forgone alternative."),
    ("A point inside the production possibility frontier shows", "unemployed or inefficiently used resources", ["full employment", "an unattainable output", "economic growth"], "More could be produced with the same resources."),
    ("Outward shift of the PPF represents", "economic growth", ["a fall in output", "unemployment", "higher prices"], "Greater productive capacity."),
    ("Which is a factor of production?", "Land", ["Money", "Tax", "Price"], "Land, labour, capital and enterprise."),
]
lesson(c1, "scarcity", "Scarcity, choice and opportunity cost",
       ["Define scarcity, choice and opportunity cost and the factors of production.", "Interpret a production possibility frontier.", "Calculate opportunity costs from data."],
       [("definition", "The basic economic problem", "**Scarcity** exists because resources (the **factors of production**: land, labour, capital, enterprise) are limited while human wants are unlimited. Scarcity forces **choice**, and every choice has an **opportunity cost**: the value of the **next best alternative** given up. Economics studies how societies decide **what**, **how** and **for whom** to produce."),
        ("pieges", "Opportunity cost is not money spent", "Opportunity cost is the **best forgone alternative**, not the sum of all alternatives and not simply the price paid. If a student spends Saturday studying instead of working in a shop, the opportunity cost is the wage forgone (or the leisure, whichever is the next best use), not the cost of the textbook. Scarcity is not poverty: even rich countries face scarcity."),
        ("retenir", "Production possibility frontier (PPF)", "A **PPF** shows the maximum combinations of two goods that can be produced with all resources fully and efficiently used. Points **on** the curve are efficient; **inside** means unemployment or waste; **outside** is unattainable now. The slope shows opportunity cost. A bowed-out (concave) curve shows **increasing opportunity costs** because resources are not equally suited to both goods. **Growth** shifts the curve outwards."),
        ("retenir", "Positive and normative", "**Positive** statements can be tested with facts ('the price of cocoa rose by 10 %'); **normative** statements are value judgements ('the government ought to raise the price of cocoa'). Economists use models and the **ceteris paribus** assumption (all else equal) to isolate effects.")],
       [("Worked example — opportunity cost", "A farmer in Bamenda can use one hectare to produce 3000 kg of maize or 1200 kg of groundnut. What is the opportunity cost of one kg of groundnut in kg of maize?", ["Choosing groundnut gives up 3000 kg of maize.", "That 3000 kg is the cost of 1200 kg of groundnut.", "Per kg: 3000 ÷ 1200 = %.1f kg of maize." % (3000 / 1200)], "2.5 kg of maize"),
        ("Worked example — increasing costs", "An economy's PPF gives (cocoa, maize) in thousand tonnes: A (0, 100), B (20, 90), C (40, 70), D (60, 40), E (80, 0). Find the opportunity cost of 1 tonne of cocoa between B and C and between D and E.", ["B → C: cocoa +20; maize falls from 90 to 70 = 20; cost 20 ÷ 20 = %.1f." % (20 / 20), "D → E: cocoa +20; maize falls from 40 to 0 = 40; cost 40 ÷ 20 = %.1f." % (40 / 20), "The cost rises: increasing opportunity cost."], "1.0 then 2.0 tonnes of maize")],
       [M("Which of the following is the opportunity cost of attending university for a student?", "The best alternative use of the time and money (e.g. earnings from a job)", ["Only the tuition fees", "The total of all alternatives", "The cost of food"], "The next best alternative forgone."),
        TF("Scarcity exists only in poor countries.", False, "All societies face scarcity because wants exceed resources."),
        N("A potter can make 40 pots or 100 bowls in a day. What is the opportunity cost of one bowl in pots?", 40 / 100, "40 ÷ 100 = 0.4 pots.", tol=0.001, unit="pots")],
       [O("Explain why a PPF is usually concave to the origin.", "Resources are not equally productive in making both goods. Moving resources from one good to the other first uses those best suited to the second good, but later less suitable resources are switched over, so the opportunity cost of each extra unit rises.", ["Resources not perfectly adaptable (1)", "Rising opportunity cost (1)", "Linked to shape (1)"]),
        M("Which of these would shift the PPF outwards?", "An improvement in technology", ["A rise in unemployment", "A fall in the price of imports", "A shift from maize to cocoa"], "Higher productive capacity.", tier="approfondissement")],
       P("An economy produces cocoa and plantain. The PPF is a smooth curve; the economy is currently at a point inside it.",
         [pm("What does a point inside the PPF show?", "Resources are unemployed or used inefficiently", ["Full employment", "Economic growth", "A fall in scarcity"], "More output is possible.", 1),
          pn("Moving to the PPF could give 30 extra tonnes of plantain at no cost to cocoa. What is the opportunity cost?", 0, "Zero, because moving from inefficiency to the frontier needs no sacrifice.", 0, None, 2),
          po("Explain two ways in which the economy could shift its PPF outwards.", "Increase the quantity of resources (e.g. investment in capital, education); improve technology or productivity.", ["Two valid ways (2)"], 2)]),
       sc1, (fig, "A production possibility frontier for cocoa and maize: points on, inside and outside it.", "A concave curve from the top-left to the bottom-right with a point on it, one inside and one outside."),
       notes=[NOTE_MONEY, "The PPF figure is schematic.", NOTE_ORDER])
assert 3000 / 1200 == 2.5

# ---- 2 economic systems
fig = tabfig(["", "Market", "Command", "Mixed"], [["Who decides", "households", "the state", "both"], ["Allocation by", "prices", "state plan", "prices + plan"], ["Ownership", "private", "state", "mixed"], ["Main strength", "efficiency", "equity", "balance"], ["Main weakness", "inequality", "shortages", "complexity"]], w=440, colw=[110, 110, 110, 110], size=11, title="Economic systems compared")
sc2 = [
    ("In a market economy resources are allocated mainly by", "the price mechanism", ["central planning", "tradition only", "random choice"], "Prices signal scarcity and guide decisions."),
    ("In a command economy the state", "owns most resources and decides what to produce", ["leaves all decisions to firms", "has no role", "only sets taxes"], "Planning replaces markets."),
    ("Most countries, including Cameroon, are best described as", "mixed economies", ["pure market economies", "pure command economies", "barter economies"], "Both private firms and the state play major roles."),
    ("A free-rider problem arises with", "public goods", ["private goods", "inferior goods", "luxury goods"], "People cannot be excluded, so they may not pay."),
    ("A weakness of central planning is", "lack of information and incentives", ["too many prices", "too much competition", "too much consumer choice"], "Planners cannot know all preferences."),
]
lesson(c1, "systems", "Economic systems",
       ["Describe market, command and mixed economic systems.", "Evaluate their strengths and weaknesses.", "Explain the role of the state in a mixed economy such as Cameroon's."],
       [("definition", "Three systems", "A **market economy** relies on private ownership and the **price mechanism**. A **command (planned) economy** has state ownership and central planning of output and prices. A **mixed economy** combines both: private firms and markets work alongside state production, regulation, taxation and welfare. Most real economies, including Cameroon's, are mixed; they differ in the **degree** of state involvement."),
        ("retenir", "Strengths and weaknesses", "**Market**: efficient allocation, consumer choice, incentives to innovate; but inequality, under-provision of public goods, externalities and possible monopoly power. **Command**: can mobilise resources for national goals and reduce inequality; but information problems, lack of incentives, shortages or surpluses, little choice. **Mixed**: aims to combine efficiency and equity but may suffer from government failure."),
        ("pieges", "No pure systems in practice", "Avoid describing a country as 'purely' capitalist or communist. Questions often ask you to **evaluate** by judging which system performs better against criteria (efficiency, equity, growth, stability, choice), not simply to list features. Remember that 'free market' does not mean absence of government: property rights, law and money all require a state."),
        ("retenir", "Role of the state", "The state provides **public goods** (defence, street lighting), **merit goods** (education, health), regulates monopolies, redistributes income through tax and benefits, and aims for stable prices, jobs and growth. Privatisation shifts activities to private firms; nationalisation transfers them to the state.")],
       [("Worked example — classify", "Cameroon has private cocoa farmers and traders, state-owned companies in some sectors, public schools and hospitals, and a government that collects taxes. Classify the system and give reasons.", ["There are both private enterprises and state-owned organisations.", "Markets operate for most goods, while the state provides education and health.", "So it is a mixed economy."], "Mixed economy"),
        ("Worked example — evaluate", "Evaluate the claim that a command economy is always fairer than a market economy.", ["Planning can reduce income differences by controlling wages and providing services.", "But planners may allocate poorly, so shortages and low quality can reduce living standards of everyone.", "So it is not always fairer: it depends on how planning works in practice."], "Not always; depends on performance in practice")],
       [M("Which system uses prices to allocate resources?", "Market economy", ["Command economy", "Traditional economy only", "Planned economy"], "Prices signal scarcity and guide decisions."),
        TF("A mixed economy has no private ownership.", False, "It combines private and state ownership."),
        M("Which of these is a public good?", "Street lighting", ["A bar of soap", "A mobile phone", "A restaurant meal"], "Non-excludable and non-rival.")],
       [O("Explain why government intervention is needed even in a market economy.", "Markets may fail to provide public goods, may produce externalities such as pollution, may allow monopoly power and cause inequality. Government supplies public goods, regulates and redistributes to correct these problems.", ["Market failure named (1)", "Government response (1)", "Link between them (1)"]),
        M("Which is a likely disadvantage of central planning?", "Shortages or surpluses because planners lack information", ["Too many consumer goods", "Excessive competition", "Prices that change every minute"], "Information and incentive problems.", tier="approfondissement")],
       P("A country privatises its state-owned telephone company.",
         [pm("Privatisation means", "transfer of ownership from the state to the private sector", ["state takeover of a private firm", "a tax rise", "a fall in prices"], "Opposite of nationalisation.", 1),
          pm("A possible advantage of privatisation is", "improved efficiency from profit motive", ["lower unemployment guaranteed", "less competition", "higher taxes"], "Private owners have incentives to cut costs.", 1),
          po("Give one possible disadvantage and one safeguard.", "Disadvantage: a private monopoly may raise prices or exclude poor areas. Safeguard: regulation of prices and service obligations.", ["Disadvantage (1)", "Safeguard (1)"], 2)]),
       sc2, (fig, "Comparison of market, command and mixed economic systems.", "A table with three columns (market, command, mixed) and five rows describing who decides, allocation, ownership, strength and weakness."),
       notes=["Descriptions of the Cameroonian economy are qualitative; check the current role of the state before teaching specifics.", NOTE_ORDER])

# ---- 3 demand
fig = plot(0, 10, 0, 20, curves=[("20-2*x", "blue", "D1"), ("26-2*x", "red", "D2")], xlabel="quantity", ylabel="price (FCFA hundreds)", h=260)
sc3 = [
    ("The law of demand states that, ceteris paribus,", "as price falls, quantity demanded rises", ["as price rises, demand rises", "as income rises, price falls", "as supply rises, demand falls"], "The demand curve slopes downward."),
    ("A change in the price of the good itself causes", "a movement along the demand curve", ["a shift of the demand curve", "a change in supply", "a change in income"], "Contraction or extension."),
    ("An increase in consumers' income (normal good) causes", "a rightward shift of demand", ["a leftward shift", "a movement along the curve", "no change"], "More is demanded at each price."),
    ("Substitutes are goods for which a rise in the price of one", "raises demand for the other", ["lowers demand for the other", "has no effect", "raises supply of the other"], "e.g. rice and macabo."),
    ("The substitution effect of a price fall is that consumers", "switch towards the cheaper good", ["buy less of it", "save more", "stop buying"], "It looks cheaper relative to other goods."),
]
lesson(c1, "demand", "Demand",
       ["State the law of demand and draw a demand curve.", "Distinguish movements along from shifts of the curve and list the conditions of demand.", "Explain why demand curves slope downward."],
       [("definition", "Demand", "**Demand** is the quantity of a good that consumers are **willing and able** to buy at each price in a given period. The **law of demand**: *ceteris paribus*, a fall in price leads to a rise in **quantity demanded**. A demand curve slopes **downward**. Reasons: the **substitution effect** (the good becomes cheaper relative to others), the **income effect** (a lower price raises real income), and **diminishing marginal utility** (each extra unit gives less satisfaction)."),
        ("retenir", "Conditions of demand (shifters)", "A change in any of these shifts the whole curve: **income** (normal goods: up; inferior goods: down), **prices of related goods** (substitutes, complements), **tastes and fashion**, **population**, **advertising**, **expectations of future prices**, and **credit availability**. A rightward shift is an **increase in demand**; a leftward shift is a **decrease in demand**."),
        ("pieges", "Movement versus shift", "A change in the **price of the good itself** moves you **along** the same curve (extension or contraction of quantity demanded). A change in any **other** factor **shifts** the curve. Do not write 'demand falls because price rises': say '**quantity demanded** falls'. Do not draw a shift when the question describes a price change only."),
        ("retenir", "Market demand and utility", "**Market demand** is the sum of individual demands at each price. **Marginal utility** is the extra satisfaction from one more unit; because it declines, consumers pay less for additional units. A **consumer surplus** exists when a consumer pays less than they were willing to pay.")],
       [("Worked example — market demand", "At a price of 500 FCFA, Aminata demands 6 mangoes, Paul 4 and Esther 5 per week. What is the market demand if these are the only consumers?", ["Market demand is the sum of individual demands at the same price.", "6 + 4 + 5.", "= %d mangoes." % (6 + 4 + 5)], "15 mangoes per week"),
        ("Worked example — shift or movement", "A rise in incomes of households in Douala increases the demand for chicken at every price. Describe what happens on a diagram.", ["Income is a condition of demand, not the price of chicken.", "Chicken is a normal good, so more is demanded at every price.", "The whole demand curve shifts to the right (D1 to D2)."], "Rightward shift of the demand curve")],
       [M("A fall in the price of tea causes", "an extension of quantity demanded", ["a shift of the demand curve to the right", "a fall in demand", "a fall in supply"], "A movement along the curve."),
        TF("An increase in the price of a complement shifts demand for the other good to the right.", False, "Demand for the other good falls: the curve shifts left."),
        N("At 800 FCFA, demand is 20 units for household A and 35 for household B. What is the market demand?", 55, "20 + 35 = 55.")],
       [O("Explain why the demand curve for most goods slopes downward.", "When the price falls the good becomes cheaper relative to substitutes (substitution effect) and consumers feel richer (income effect); also, because marginal utility falls, consumers buy more only at lower prices.", ["Substitution effect (1)", "Income effect (1)", "Diminishing marginal utility (1)"]),
        M("The price of bread rises. Which is correct for the demand for margarine (a substitute)?", "Demand shifts right", ["Demand shifts left", "Quantity demanded falls", "No change"], "Consumers switch to the substitute.", tier="approfondissement")],
       P("A trader in Bafoussam records weekly demand for tomatoes (kg): price 400 FCFA: 120; 500: 100; 600: 80; 700: 60.",
         [pn("How many kg are demanded at 600 FCFA?", 80, "Read from the schedule.", 0, "kg", 1),
          pn("By how many kg does quantity demanded fall for each 100 FCFA rise?", 20, "120→100→80→60: 20 each time.", 0, "kg", 1),
          po("Explain what would happen to this demand schedule if incomes rose.", "More would be demanded at each price (tomatoes are normal goods), so the schedule shifts right.", ["Normal good (1)", "Shift right (1)"], 2)]),
       sc3, (fig, "A rightward shift of demand from D1 to D2.", "Two parallel downward-sloping lines labelled D1 and D2, with D2 to the right."),
       notes=[NOTE_MONEY, "Marginal utility theory is described in outline only; indifference curves are not covered."])

# ---- 4 supply and market equilibrium
Pq = 8; Qq = 6
assert 20 - 2 * Qq == Pq and 2 + Qq == Pq
fig = plot(0, 10, 0, 20, curves=[("20-2*x", "blue", "D"), ("2+x", "red", "S")], points=[(6, 8, "E", "green")], segments=[(0, 8, 6, 8, "grey", True), (6, 0, 6, 8, "grey", True)], xlabel="quantity (tonnes)", ylabel="price (FCFA hundreds)", h=270)
sc4 = [
    ("The law of supply says that, ceteris paribus, a rise in price causes", "an increase in quantity supplied", ["a fall in quantity supplied", "a fall in demand", "a fall in costs"], "The supply curve slopes upward."),
    ("At the equilibrium price", "quantity demanded equals quantity supplied", ["there is a shortage", "there is a surplus", "price must rise"], "The market clears."),
    ("If price is above equilibrium there is", "excess supply (a surplus)", ["excess demand", "equilibrium", "a shift in demand"], "Price tends to fall."),
    ("An improvement in technology shifts the supply curve", "to the right", ["to the left", "upward only", "no change"], "Costs fall, so more is supplied at every price."),
    ("A rise in the price of an input (e.g. fertiliser) shifts supply", "to the left", ["to the right", "along the curve", "nowhere"], "Costs rise, so less is supplied at each price."),
]
lesson(c1, "supply-equilibrium", "Supply and market equilibrium",
       ["State the law of supply and list the conditions of supply.", "Find the equilibrium price and quantity from schedules or equations.", "Analyse the effects of shifts in demand or supply."],
       [("definition", "Supply", "**Supply** is the quantity producers are willing and able to sell at each price. The **law of supply**: a rise in price increases **quantity supplied** because higher prices cover rising marginal costs and raise profit. Conditions of supply (shifters): **costs of production** (wages, raw materials), **technology**, **taxes and subsidies**, **prices of other goods** the firm could produce, **weather** (for crops), **number of firms** and **expectations**."),
        ("retenir", "Equilibrium", "The **equilibrium price** is where quantity demanded equals quantity supplied (the market **clears**). Above it there is **excess supply** (surplus), which pushes the price down; below it there is **excess demand** (shortage), which pushes the price up. A shift in **demand** or **supply** gives a new equilibrium: rightward shift of demand raises price and quantity; rightward shift of supply lowers price and raises quantity."),
        ("pieges", "Shift of supply versus change in quantity supplied", "A change in the **price of the product** is a movement along the supply curve. A change in cost, technology, taxes or weather **shifts** the curve. A good harvest in the cocoa-growing areas shifts supply right, so price falls and quantity traded rises; it does **not** shift demand. In diagrams, label axes, curves (D, S), the original and new equilibrium, and the arrows of change."),
        ("formule", "Equilibrium from equations", "Set quantity demanded equal to quantity supplied: for P = 20 − 2Q (demand) and P = 2 + Q (supply), 20 − 2Q = 2 + Q gives Q = 6 and P = 8. **Consumer surplus** is the area between demand and price; **producer surplus** is the area between price and supply.")],
       [("Worked example — equilibrium", "Demand: P = 20 − 2Q; supply: P = 2 + Q (P in hundreds of FCFA, Q in tonnes). Find the equilibrium.", ["Set demand = supply: 20 − 2Q = 2 + Q.", "20 − 2 = 3Q, so Q = 18 ÷ 3 = %d tonnes." % Qq, "P = 2 + 6 = %d hundred FCFA (800 FCFA)." % Pq], "Q = 6 tonnes, P = 800 FCFA"),
        ("Worked example — a supply shock", "Poor weather reduces maize supply by 4 tonnes at every price. With demand P = 20 − 2Q and new supply P = 6 + Q, find the new equilibrium.", ["New supply: P = 6 + Q (left shift).", "Set 20 − 2Q = 6 + Q: 14 = 3Q, so Q = %.2f." % (14 / 3), "P = 6 + %.2f = %.2f: price rises and quantity falls." % (14 / 3, 6 + 14 / 3)], "Q ≈ 4.67 tonnes, P ≈ 10.67 (about 1067 FCFA)")],
       [M("At a price below equilibrium there is", "excess demand", ["excess supply", "equilibrium", "no trade"], "Quantity demanded exceeds quantity supplied."),
        TF("A subsidy to producers shifts the supply curve to the right.", True, "Costs are reduced, so more is supplied at each price."),
        N("Demand: P = 30 − Q; supply: P = 10 + Q. Find the equilibrium quantity.", 10, "30 − Q = 10 + Q gives Q = 10.")],
       [O("Using a diagram, explain the effect on the price of plantain of a good harvest.", "A good harvest increases supply: the supply curve shifts right. At the old price there is excess supply, so the price falls until a new equilibrium is reached at a lower price and higher quantity.", ["Supply shifts right (1)", "Excess supply at old price (1)", "Lower price, more quantity (1)"]),
        M("Demand rises and supply falls at the same time. What is the certain effect?", "Price rises", ["Price falls", "Quantity rises", "Quantity falls"], "Quantity depends on the relative size of the shifts.", tier="approfondissement")],
       P("The market for fish in Limbe has demand P = 24 − 2Q and supply P = 6 + Q (P in hundreds of FCFA, Q in tonnes).",
         [pn("Find the equilibrium quantity.", 6, "24 − 2Q = 6 + Q so Q = 6.", 0, "tonnes", 2),
          pn("Find the equilibrium price (hundreds of FCFA).", 12, "P = 6 + 6 = 12 (1200 FCFA).", 0, None, 1),
          po("A new cold-storage plant lowers costs. Describe the effect on price and quantity.", "Supply shifts right, so the equilibrium price falls and quantity traded rises.", ["Supply right (1)", "Price falls, quantity rises (1)"], 2)]),
       sc4, (fig, "Market equilibrium at the intersection of demand and supply: price 8 (hundreds of FCFA), quantity 6 tonnes.", "A downward demand line and an upward supply line crossing at point E with dashed lines to both axes."),
       notes=[NOTE_MONEY, "Linear demand and supply equations are used for calculation practice only."])
assert abs((6 + 14 / 3) - 10.6667) < 1e-3 and 30 - 10 == 2 * 10 and 24 - 2 * 6 == 6 + 6

# =====================================================================  CH 2  ELASTICITY, COSTS, MARKET STRUCTURES
c2 = p.chapter("markets", "Elasticity, costs and market structures", programRef="GCE A Level Economics — Elasticity; costs and revenue; perfect competition, monopoly, oligopoly (to be checked)")

# ---- 5 elasticities
fig = tabfig(["PED (absolute)", "Type of demand", "Price rise: revenue"], [["greater than 1", "elastic", "falls"], ["less than 1", "inelastic", "rises"], ["equal to 1", "unit elastic", "unchanged"], ["0", "perfectly inelastic", "rises"]], w=440, colw=[130, 160, 150], size=12, title="Price elasticity of demand")
ped1 = ((150 - 200) / 200) / ((600 - 500) / 500); assert abs(ped1 + 1.25) < 1e-9
tr0, tr1 = 500 * 200, 600 * 150; assert (tr0, tr1) == (100000, 90000)
yed = 15 / 10; xed = 12 / 8; pes = 4 / 10
sc5 = [
    ("Price elasticity of demand is calculated as", "% change in quantity demanded ÷ % change in price", ["% change in price ÷ % change in quantity", "change in quantity ÷ change in price", "price × quantity"], "PED = %ΔQd ÷ %ΔP."),
    ("If PED = −0.4, demand is", "inelastic", ["elastic", "unit elastic", "perfectly elastic"], "Absolute value is less than 1."),
    ("A good with many close substitutes tends to have", "elastic demand", ["inelastic demand", "perfectly inelastic demand", "negative PED"], "Consumers can switch easily."),
    ("A negative income elasticity of demand shows the good is", "inferior", ["normal", "a luxury", "a complement"], "Demand falls as income rises."),
    ("A positive cross elasticity of demand shows that goods are", "substitutes", ["complements", "unrelated", "inferior goods"], "A rise in the price of one raises demand for the other."),
]
lesson(c2, "elasticity", "Elasticities of demand and supply",
       ["Calculate price, income and cross elasticity of demand and price elasticity of supply.", "Explain the determinants of elasticity.", "Relate PED to total revenue and to tax policy."],
       [("formule", "Formulae", "**PED** = % change in quantity demanded ÷ % change in price (usually negative; use the absolute value). **YED** = % change in quantity demanded ÷ % change in income. **XED** = % change in quantity demanded of A ÷ % change in price of B. **PES** = % change in quantity supplied ÷ % change in price. Percentage change = (new − old) ÷ old × 100."),
        ("retenir", "Interpreting values", "**PED**: greater than 1 elastic; less than 1 inelastic; equal to 1 unit elastic. **YED**: positive = normal good (greater than 1: luxury); negative = inferior good. **XED**: positive = substitutes; negative = complements; zero = unrelated. **PES**: greater than 1 elastic supply; less than 1 inelastic supply."),
        ("pieges", "Revenue and elasticity", "If a firm raises the price of a good with **elastic** demand, quantity falls proportionally more, so **total revenue falls**; with **inelastic** demand, revenue **rises**. Do not mix up elasticity with the slope of the curve: a straight demand line has different PED at different points. Always state whether you use the absolute value of PED, and note that the sign of YED and XED carries meaning."),
        ("retenir", "Determinants", "PED is higher (more elastic) when there are **many substitutes**, the good is a **luxury**, takes a **large share of income**, and over a **longer time**. PES is higher when **stocks** exist, **spare capacity** is available and **factors are mobile**; agricultural supply is inelastic in the short run (crops take time to grow). Governments tax goods with inelastic demand (fuel, tobacco) to raise revenue.")],
       [("Worked example — PED", "The price of a good rises from 500 to 600 FCFA and quantity demanded falls from 200 to 150. Calculate PED and the effect on total revenue.", ["%ΔP = (600 − 500) ÷ 500 = +20 %; %ΔQ = (150 − 200) ÷ 200 = −25 %.", "PED = −25 ÷ 20 = %.2f: elastic (absolute value above 1)." % ped1, "Revenue: 500 × 200 = %d falls to 600 × 150 = %d." % (tr0, tr1)], "PED = −1.25; revenue falls from 100 000 to 90 000 FCFA"),
        ("Worked example — other elasticities", "(a) Income rises 10 %, quantity demanded of a good rises 15 %. (b) The price of rice rises 8 % and demand for macabo rises 12 %. (c) Price rises 10 % and quantity supplied rises 4 %. Calculate YED, XED and PES.", ["(a) YED = 15 ÷ 10 = %.1f: a normal good, a luxury (above 1)." % yed, "(b) XED = 12 ÷ 8 = %.1f: positive, so macabo and rice are substitutes." % xed, "(c) PES = 4 ÷ 10 = %.1f: inelastic supply." % pes], "YED 1.5; XED 1.5; PES 0.4")],
       [M("A product has a PED of −2.5. A price rise will", "reduce total revenue", ["raise total revenue", "leave total revenue unchanged", "have no effect on quantity"], "Demand is elastic."),
        TF("A negative income elasticity of demand means the good is a normal good.", False, "It means the good is inferior."),
        N("Price rises by 10 % and quantity demanded falls by 25 %. Calculate PED (include the sign).", -2.5, "−25 ÷ 10 = −2.5.", tol=0.001)],
       [O("Explain why the demand for petrol is price inelastic in the short run.", "There are few close substitutes for petrol in the short run, it is a necessity for travel and transport, and people cannot change vehicles or habits quickly, so quantity demanded falls by a smaller percentage than price rises.", ["Few substitutes (1)", "Necessity (1)", "Short time to adjust (1)"]),
        M("A government wants to raise tax revenue with the least fall in sales. Which good should it tax?", "A good with inelastic demand", ["A good with elastic demand", "A good with elastic supply", "A good with a negative YED"], "Quantity falls little, so revenue rises.", tier="approfondissement")],
       P("A bakery in Yaoundé raises the price of bread from 1000 to 1100 FCFA per loaf and the quantity sold per day falls from 500 to 400 loaves.",
         [pn("Calculate the percentage change in price.", 10, "(1100 − 1000) ÷ 1000 × 100 = 10 %.", 0.01, "%", 1),
          pn("Calculate PED.", -2, "%ΔQ = (400 − 500) ÷ 500 = −20 %; PED = −20 ÷ 10 = −2.", 0.001, None, 2),
          pn("Calculate the change in daily revenue (FCFA).", 1100 * 400 - 1000 * 500, "440 000 − 500 000 = −60 000 FCFA.", 0, "FCFA", 1),
          po("What would you advise the bakery about future price rises?", "Demand is elastic (|PED| = 2), so raising the price further reduces revenue; the bakery should keep prices stable or lower them.", ["Elastic demand (1)", "Revenue falls with price rise (1)"], 1)]),
       sc5, (fig, "How the value of price elasticity of demand relates to the effect of a price rise on revenue.", "A table with PED ranges, the type of demand and the effect of a price rise on total revenue."),
       notes=[NOTE_MONEY, "Point (simple percentage) PED is used rather than the mid-point formula; check which the syllabus requires."])
assert 1100 * 400 - 1000 * 500 == -60000

# ---- 6 costs and revenue
Q_ = list(range(0, 9)); TC = [q * q + 4 * q + 36 for q in Q_]
MC = [TC[i] - TC[i - 1] for i in range(1, 9)]; assert MC == [5, 7, 9, 11, 13, 15, 17, 19]
AC6 = TC[6] / 6; assert AC6 == 16
fig = plot(1.5, 12, 0, 45, curves=[("x+4+36/x", "blue", "AC"), ("2*x+4", "red", "MC")], points=[(6, 16, "min AC", "green")], xlabel="output", ylabel="cost (FCFA hundreds)", h=270)
sc6 = [
    ("Fixed costs", "do not change with output in the short run", ["rise with output", "are zero in the short run", "fall with output"], "e.g. rent, insurance."),
    ("Marginal cost is", "the extra cost of producing one more unit", ["total cost ÷ output", "fixed cost ÷ output", "the price"], "MC = ΔTC ÷ ΔQ."),
    ("The MC curve cuts the AC curve", "at its minimum point", ["at its maximum point", "never", "at the y-axis"], "When MC is below AC, AC falls; when above, AC rises."),
    ("The law of diminishing returns says that adding more of a variable factor to fixed factors eventually", "gives lower additional output", ["raises additional output forever", "reduces fixed costs", "gives zero cost"], "Marginal product falls."),
    ("Economies of scale occur when", "average cost falls as output rises in the long run", ["average cost rises in the long run", "output is constant", "price rises"], "e.g. bulk buying, specialisation."),
]
lesson(c2, "costs-revenue", "Costs, revenue and profit",
       ["Define and calculate fixed, variable, total, average and marginal costs and revenue.", "Explain diminishing returns and economies of scale.", "Calculate profit and the profit-maximising output."],
       [("formule", "Costs", "TC = FC + VC. AC = TC ÷ Q. AVC = VC ÷ Q. **MC** = change in TC ÷ change in Q (the cost of the extra unit). In the short run at least one factor (e.g. capital) is fixed, so **FC** (rent, insurance) does not change with output, while **VC** (raw materials, labour) varies. **Revenue**: TR = P × Q; AR = TR ÷ Q = P; MR = change in TR ÷ change in Q."),
        ("retenir", "Shapes of curves", "Because of **diminishing returns** (the marginal product of the variable factor eventually falls), MC rises, giving a **U-shaped AC curve**. **MC cuts AC at the minimum of AC**. In the long run all factors vary: **economies of scale** (technical, purchasing, financial, managerial, marketing) lower AC; **diseconomies** (coordination problems) eventually raise it. The lowest point of long-run AC is the **minimum efficient scale**."),
        ("pieges", "Profit rule", "A firm maximises profit where **MC = MR** (and MC cuts MR from below), not where revenue or output is greatest. **Normal profit** is the minimum profit that keeps the firm in business (covers the opportunity cost of enterprise) and is included in costs; **supernormal (abnormal) profit** is above that. Do not confuse AC with MC or fixed cost with sunk cost."),
        ("retenir", "Short run and long run", "In the **short run** a firm may continue to produce at a loss if price covers **average variable cost** (it still contributes to fixed costs); it shuts down when P < AVC. In the long run it exits if it cannot cover all costs.")],
       [("Worked example — cost table", "A firm has TC = Q² + 4Q + 36 (in hundreds of FCFA). Calculate FC, VC, AC and MC when Q goes from 5 to 6.", ["FC = 36 (the cost when Q = 0).", "TC(5) = %d; TC(6) = %d; MC = %d − %d = %d." % (TC[5], TC[6], TC[6], TC[5], MC[5]), "VC(6) = 6² + 4 × 6 = %d; AC(6) = %d ÷ 6 = %d." % (6 * 6 + 24, TC[6], AC6)], "FC 36; MC 15; AC 16"),
        ("Worked example — profit", "The firm sells 6 units at 20 (hundreds of FCFA) each. Calculate profit.", ["TR = 20 × 6 = %d." % (20 * 6), "TC(6) = %d." % TC[6], "Profit = %d − %d = %d (hundreds of FCFA)." % (20 * 6, TC[6], 20 * 6 - TC[6])], "24 hundred FCFA (2400 FCFA)")],
       [M("Which cost does NOT vary with output in the short run?", "Rent of a factory", ["Raw materials", "Overtime wages", "Electricity used in production"], "Rent is a fixed cost."),
        TF("Marginal cost is the cost of producing one additional unit.", True, "MC = ΔTC ÷ ΔQ."),
        N("TC for 10 units is 500 and for 11 units is 530. Calculate the marginal cost of the 11th unit.", 30, "530 − 500 = 30.")],
       [O("Explain the law of diminishing returns and its effect on costs.", "When a variable factor is added to fixed factors, the extra output from each additional unit eventually falls; so each additional unit of output needs more resources and marginal cost rises, producing the upward part of the MC curve.", ["Definition (1)", "Marginal product falls (1)", "MC rises (1)"]),
        M("A firm has AC = 20 and MC = 15 at its current output. Average cost will", "fall if output rises", ["rise if output rises", "remain constant", "equal MC"], "MC below AC pulls AC down.", tier="approfondissement")],
       P("A soap factory in Douala has fixed costs of 40 000 FCFA. Total variable costs are 20 000 FCFA at 100 units, 36 000 FCFA at 200 units and 60 000 FCFA at 300 units.",
         [pn("Calculate total cost at 200 units.", 76000, "40 000 + 36 000 = 76 000.", 0, "FCFA", 1),
          pn("Calculate AC at 300 units.", round((40000 + 60000) / 300, 2), "100 000 ÷ 300 = 333.33.", 0.01, "FCFA", 1),
          pn("Calculate the marginal cost per unit between 200 and 300 units.", 240, "(60 000 − 36 000) ÷ 100 = 240.", 0, "FCFA", 2),
          po("Explain why AC may fall as output rises.", "Fixed costs are spread over more units, and economies of scale such as bulk buying lower unit costs.", ["Spreading fixed costs (1)", "Economies of scale (1)"], 1)]),
       sc6, (fig, "Average cost (AC) and marginal cost (MC) curves; MC cuts AC at its minimum.", "A U-shaped AC curve and an upward-sloping MC line crossing at the lowest point of AC."),
       notes=[NOTE_MONEY, "The cost function TC = Q² + 4Q + 36 is an invented teaching function."])
assert (40000 + 36000) == 76000 and (60000 - 36000) / 100 == 240

# ---- 7 perfect competition
fig = plot(1.5, 12, 0, 45, curves=[("x+4+36/x", "blue", "AC"), ("2*x+4", "red", "MC")], points=[(8, 20, "profit max", "green")], segments=[(0, 20, 12, 20, "grey", True, "P = MR = AR"), (8, 0, 8, 20, "grey", True)], xlabel="output", ylabel="price and cost", h=270)
pc_q = (20 - 4) / 2; pc_tc = pc_q ** 2 + 4 * pc_q + 36; pc_pi = 20 * pc_q - pc_tc
assert pc_q == 8 and pc_tc == 132 and pc_pi == 28
sc7 = [
    ("In perfect competition each firm is", "a price taker", ["a price maker", "a monopolist", "an oligopolist"], "It cannot influence market price."),
    ("The firm's demand curve in perfect competition is", "horizontal at the market price", ["downward-sloping", "vertical", "upward-sloping"], "It can sell any quantity at the market price."),
    ("The profit-maximising output is where", "MC = MR", ["AC is lowest", "TR is lowest", "MR = 0"], "Producing more adds more to cost than revenue."),
    ("In the long run firms in perfect competition earn", "normal profit only", ["supernormal profit", "permanent losses", "no revenue"], "Entry removes supernormal profit."),
    ("Which is a feature of perfect competition?", "Free entry and exit", ["A single seller", "Differentiated products", "High barriers to entry"], "No barriers."),
]
lesson(c2, "perfect-competition", "Perfect competition",
       ["List the assumptions of perfect competition.", "Determine the profit-maximising output and profit for a competitive firm.", "Explain long-run equilibrium and efficiency."],
       [("definition", "Characteristics", "Perfect competition has: **many buyers and sellers** (each too small to affect price), **homogeneous (identical) products**, **free entry and exit**, **perfect knowledge** and no transport costs. Firms are **price takers**: the market determines price, so the firm's demand (AR = MR) is horizontal. Agricultural markets (maize, plantain traded in a large market) approximate it, although no real market is perfect."),
        ("retenir", "Short-run profit maximisation", "A firm maximises profit at the output where **MC = MR (= P)**. If P > AC at that output it earns **supernormal profit** (area = (P − AC) × Q). If P < AC but P > AVC it makes a loss but should continue in the short run; if P < AVC it should shut down. Supernormal profits attract new firms, supply rises, price falls; losses cause exit."),
        ("pieges", "Long-run equilibrium", "In the long run, free entry and exit force price down to the **minimum of AC** so that only **normal profit** is made: P = MC = AC. At this point the firm is both **productively efficient** (produces at lowest AC) and **allocatively efficient** (P = MC). Do not claim that a perfectly competitive firm earns supernormal profit in the long run, and do not confuse the **firm's** horizontal demand with the downward-sloping **market** demand."),
        ("retenir", "Evaluation", "Benefits: efficiency, low prices, consumer sovereignty. Limits: no real market meets all assumptions; there may be no economies of scale, no product variety and little innovation (no supernormal profits to fund research). Markets with many small firms, such as street food or open markets, are the closest everyday examples.")],
       [("Worked example — profit maximising output", "A competitive firm faces price 20 (hundreds of FCFA). Its cost function is TC = Q² + 4Q + 36 so MC = 2Q + 4. Find output and profit.", ["Set MC = MR = P: 2Q + 4 = 20, so Q = %d." % pc_q, "TR = 20 × 8 = %d; TC = 64 + 32 + 36 = %d." % (20 * pc_q, pc_tc), "Profit = %d − %d = %d (hundreds of FCFA)." % (20 * pc_q, pc_tc, pc_pi)], "Q = 8; profit 28"),
        ("Worked example — shutdown decision", "At P = 10 the same firm produces where 2Q + 4 = 10, so Q = 3. AVC at Q = 3 is Q + 4 = 7. Should it continue in the short run?", ["TR = 10 × 3 = 30; TC = 9 + 12 + 36 = 57: a loss of 27.", "But P = 10 exceeds AVC = 7, so each unit contributes 3 towards fixed costs (total 9).", "Continuing loses 27; shutting down loses the whole fixed cost, 36. So it continues."], "Continue in the short run")],
       [M("Which feature does NOT belong to perfect competition?", "Barriers to entry", ["Many sellers", "Homogeneous goods", "Perfect knowledge"], "Free entry is an assumption."),
        TF("A perfectly competitive firm can set its own price.", False, "It is a price taker."),
        N("A competitive firm with MC = 3Q faces price 24. Find the profit-maximising output.", 8, "3Q = 24 gives Q = 8.")],
       [O("Explain why firms in perfect competition earn only normal profit in the long run.", "If firms earn supernormal profit new firms enter, supply increases and price falls until supernormal profit disappears; if losses occur firms leave, supply falls and price rises. So in equilibrium P = AC and only normal profit is earned.", ["Entry when supernormal profit (1)", "Price falls (1)", "Normal profit in equilibrium (1)"]),
        M("A firm's price is below AVC at all outputs. In the short run it should", "shut down", ["increase output", "continue to produce", "raise price"], "Each unit loses money even before fixed costs.", tier="approfondissement")],
       P("Cocoa traders in a village sell to many buyers at the market price of 20 (hundreds of FCFA per unit). A trader's cost: TC = Q² + 4Q + 36.",
         [pn("Find the profit-maximising output.", 8, "2Q + 4 = 20 gives Q = 8.", 0, None, 1),
          pn("Calculate total profit (hundreds of FCFA).", 28, "TR 160 − TC 132 = 28.", 0, None, 2),
          po("What will happen in the long run if many traders earn this profit?", "New traders enter, supply rises and price falls until profit falls to normal.", ["Entry (1)", "Price falls, profit falls (1)"], 2)]),
       sc7, (fig, "A competitive firm: price (P = MR = AR) is a horizontal line; profit is maximised where MC cuts it.", "AC and MC curves with a horizontal dashed price line and a vertical dashed line at the profit-maximising output where MC meets price."),
       notes=[NOTE_MONEY, "Cost data are invented. Cameroonian market examples are approximations; real agricultural markets also face imperfect information."])
assert 3 * 8 == 24

# ---- 8 monopoly and oligopoly
fig = plot(0, 12, 0, 45, curves=[("40-2*x", "blue", "AR = D"), ("40-4*x", "orange", "MR"), ("2*x+4", "red", "MC")], points=[(6, 28, "monopoly", "green"), (9, 22, "competitive", "orange")], xlabel="output", ylabel="price and cost", h=280)
mono_Q = 6; mono_P = 40 - 2 * mono_Q; mono_AC = mono_Q + 4 + 36 / mono_Q; mono_pi = (mono_P - mono_AC) * mono_Q
comp_Q = 9; comp_P = 40 - 2 * comp_Q; dwl = 0.5 * (comp_Q - mono_Q) * ((40 - 2 * mono_Q) - (2 * mono_Q + 4))
assert (mono_Q, mono_P, mono_AC, mono_pi) == (6, 28, 16, 72) and (comp_Q, comp_P) == (9, 22) and dwl == 18
assert 40 - 4 * mono_Q == 2 * mono_Q + 4 and 40 - 2 * comp_Q == 2 * comp_Q + 4
sc8 = [
    ("A pure monopoly is", "the sole supplier of a good with no close substitutes", ["one of many suppliers", "a buyer with market power", "a firm facing perfect knowledge"], "Barriers to entry protect it."),
    ("For a monopolist MR is", "below AR (price) at every output", ["equal to AR", "above AR", "always zero"], "To sell more it must lower the price on all units."),
    ("Compared with perfect competition a monopolist usually charges", "a higher price and produces less", ["a lower price and produces more", "the same price and output", "a lower price and produces less"], "The profit-maximising rule MC = MR gives a lower output and higher price."),
    ("In oligopoly firms are", "interdependent: each must consider rivals' reactions", ["unaffected by rivals", "price takers", "single sellers"], "Few large firms."),
    ("A cartel is", "an agreement among firms to restrict output or fix prices", ["a type of tax", "a government agency", "a form of competition"], "Often illegal; unstable because members may cheat."),
]
lesson(c2, "monopoly-oligopoly", "Monopoly and oligopoly",
       ["Describe the features and sources of monopoly power.", "Determine monopoly price, output and profit and compare with competition.", "Explain interdependence, collusion and non-price competition in oligopoly."],
       [("definition", "Monopoly", "A **monopoly** is the sole supplier of a product with no close substitutes. It arises from **barriers to entry**: legal protection (patents, licences), control of key resources, huge economies of scale (natural monopoly, such as an electricity grid), or aggressive tactics. A monopolist faces the market demand curve (AR = D), so to sell more it must reduce price, and **MR lies below AR**. It maximises profit where **MC = MR** and charges the price on the demand curve at that output."),
        ("retenir", "Monopoly versus competition", "A monopolist produces **less and charges more** than a competitive industry with the same costs, creating a **deadweight welfare loss** and possible **X-inefficiency**. Possible benefits: economies of scale, funds for research, and **natural monopolies** may justify regulation rather than break-up. **Price discrimination** (charging different prices to different groups) needs market power and separable markets (e.g. student and adult fares)."),
        ("pieges", "Reading the monopoly diagram", "Find output where MC cuts MR, then go **up to the demand (AR) curve** for the price (not to MR). Profit = (price − AC) × output, shown by the rectangle up to AC. Do not read the price from the MR curve, and do not claim that a monopolist always earns supernormal profit (it may make losses if demand is weak). Barriers to entry allow supernormal profits to persist in the long run."),
        ("retenir", "Oligopoly", "An **oligopoly** has a few large firms with high barriers to entry; products may be identical or differentiated; firms are **interdependent**. Examples in many countries include mobile telephone networks and brewing. Behaviour: **collusion** or a **cartel** (e.g. OPEC in oil), **price leadership**, and **non-price competition** (advertising, branding, service) because price cuts can start a price war. The **kinked demand curve** model explains price rigidity: rivals match price cuts but not price rises. Game theory shows incentives to cheat on collusive agreements.")],
       [("Worked example — monopoly output and price", "A monopolist faces P = 40 − 2Q so MR = 40 − 4Q, and MC = 2Q + 4. Find the profit-maximising output, price and profit if AC at that output is 16.", ["Set MR = MC: 40 − 4Q = 2Q + 4, so 36 = 6Q and Q = %d." % mono_Q, "Price from demand: P = 40 − 2 × 6 = %d." % mono_P, "Profit = (28 − 16) × 6 = %d (hundreds of FCFA)." % mono_pi], "Q = 6, P = 28, profit 72"),
        ("Worked example — compared with competition", "If the same industry were competitive (P = MC), find output and price, and the welfare loss under monopoly.", ["P = MC: 40 − 2Q = 2Q + 4, so Q = %d and P = 40 − 18 = %d." % (comp_Q, comp_P), "The monopolist restricts output by 9 − 6 = 3 units and charges 28 − 22 = 6 more.", "Welfare loss = ½ × 3 × (28 − 16) = %.0f (the triangle between D and MC)." % dwl], "Competitive: Q = 9, P = 22; welfare loss 18")],
       [M("Which of these is a barrier to entry?", "A patent", ["Many buyers", "Perfect knowledge", "Homogeneous products"], "Legal barrier."),
        TF("A monopolist always earns supernormal profit.", False, "It may earn a loss if demand is weak."),
        N("A monopolist's demand is P = 50 − 2Q and MR = 50 − 4Q, MC = 10. Find the profit-maximising output.", 10, "50 − 4Q = 10 gives Q = 10.")],
       [O("Explain why firms in an oligopoly often compete using non-price methods.", "Firms are interdependent: a price cut is quickly matched by rivals, so nobody gains and all lose revenue (price war). Non-price competition such as advertising and branding wins customers without triggering price wars.", ["Interdependence (1)", "Price cuts matched (1)", "Non-price methods named (1)"]),
        M("Cartels tend to be unstable because", "each member has an incentive to cheat by selling more", ["demand is perfectly elastic", "they are always legal", "there are no profits"], "Cheating raises one firm's profits but erodes the agreement.", tier="approfondissement")],
       P("A single company supplies electricity in a region; its demand is P = 40 − 2Q and its marginal cost MC = 2Q + 4 (hundreds of FCFA).",
         [pn("Find the profit-maximising output.", 6, "MR = 40 − 4Q = 2Q + 4 gives Q = 6.", 0, None, 2),
          pn("Find the price it charges.", 28, "P = 40 − 2 × 6 = 28.", 0, None, 1),
          po("Why might a government regulate the price of a natural monopoly?", "Without regulation it would restrict output and raise price above marginal cost, causing welfare loss; regulators can set a lower price nearer MC while allowing a normal profit.", ["Welfare loss (1)", "Regulation of price (1)"], 2)]),
       sc8, (fig, "A monopolist produces where MC = MR (output 6) and charges 28; a competitive industry would produce 9 at price 22.", "Downward-sloping demand, a steeper MR curve below it and an upward-sloping MC line, with two marked points."),
       notes=[NOTE_MONEY, "Local examples of oligopolies (telecommunications, brewing) are mentioned without naming firms or market shares; check current market structure.", "The kinked-demand-curve model is outlined only."])

# =====================================================================  CH 3  FACTOR MARKETS, MARKET FAILURE, GOVERNMENT, NATIONAL INCOME
c3 = p.chapter("failure", "Labour markets, market failure and national income", programRef="GCE A Level Economics — Labour markets; market failure; government intervention; national income (to be checked)")

# ---- 9 labour markets
fig = plot(0, 10, 0, 32, curves=[("30-2*x", "blue", "demand for labour"), ("6+2*x", "red", "supply of labour")], points=[(6, 18, "E", "green")], segments=[(0, 24, 10, 24, "orange", True, "minimum wage")], xlabel="workers (hundreds)", ylabel="wage (FCFA hundreds per hour)", h=270)
Lq, Wq = 6, 18; assert 30 - 2 * Lq == Wq == 6 + 2 * Lq
Ld = (30 - 24) / 2; Ls = (24 - 6) / 2; assert (Ld, Ls) == (3, 9)
mrp = 5 * 400; assert mrp == 2000
sc9 = [
    ("The demand for labour is a derived demand because", "it depends on the demand for the goods labour produces", ["workers want jobs", "it is fixed by law", "it equals supply"], "Firms hire workers to produce output."),
    ("Marginal revenue product (MRP) of labour is", "the extra revenue from employing one more worker", ["total wage bill", "the average wage", "total output"], "MRP = MPP × MR."),
    ("A minimum wage above the equilibrium wage may cause", "unemployment", ["excess demand for labour", "lower prices only", "a fall in supply of labour"], "Quantity of labour supplied exceeds quantity demanded."),
    ("A trade union can raise wages by", "restricting labour supply or bargaining collectively", ["lowering productivity", "closing the market", "reducing demand for goods"], "It may also cause unemployment among non-members."),
    ("Wage differentials arise partly because", "jobs require different skills and training", ["all workers are equally skilled", "wages are set at random", "labour is always immobile"], "Skills, risk, qualifications and bargaining power differ."),
]
lesson(c3, "labour", "Labour markets and wages",
       ["Explain the demand for and supply of labour.", "Use diagrams to analyse the effect of a minimum wage.", "Describe the role of trade unions and causes of wage differentials."],
       [("definition", "Demand for labour", "Firms hire labour as a **derived demand**: the demand for workers depends on the demand for the product. A profit-maximising firm hires workers up to the point where the wage equals the **marginal revenue product (MRP)** of labour: MRP = marginal physical product × marginal revenue (price in competition). Because of diminishing returns, the MRP curve (the demand curve for labour) slopes downward."),
        ("retenir", "Supply of labour and the wage", "The **supply of labour** to an occupation is upward-sloping: higher wages attract more workers. It shifts with **qualifications required, working conditions, migration, population, and wages in other jobs**. In a competitive labour market the **equilibrium wage** is where demand equals supply. **Wage differentials** reflect skills, training costs, risk, responsibility, and **market power** (including trade unions and monopsony employers)."),
        ("pieges", "Minimum wages: both sides of the evaluation", "A **minimum wage** above the equilibrium creates a surplus of labour (unemployment) in a competitive market model, but raises the wage of those who keep jobs and may improve motivation and reduce poverty. The size of the unemployment effect depends on the **elasticity** of demand for labour and on whether employers have monopsony power. Do not just state 'it causes unemployment': show it on a diagram and evaluate. Minimum-wage rules and enforcement in Cameroon should be checked from official texts."),
        ("retenir", "Trade unions and wage setting", "A **trade union** bargains collectively; it can raise wages above equilibrium by restricting supply (closed shop, licences) or raising productivity/threatening strikes. Possible costs: unemployment among excluded workers, higher costs, strikes. The **informal sector** (market traders, taxi drivers, small farmers) sets earnings largely by competition with little regulation or union cover.")],
       [("Worked example — MRP", "A worker produces 5 extra kg of groundnut paste per hour (MPP) and each kg sells for 400 FCFA. Calculate MRP and say whether to hire at a wage of 1800 FCFA per hour.", ["MRP = MPP × price = 5 × 400 = %d FCFA per hour." % mrp, "The wage is 1800, which is less than MRP of 2000.", "So hiring adds 200 FCFA of profit per hour: hire."], "MRP = 2000 FCFA; hire"),
        ("Worked example — minimum wage", "Labour demand: W = 30 − 2L; supply: W = 6 + 2L (W in hundreds of FCFA per hour, L in hundreds of workers). Find the equilibrium; then the effect of a minimum wage of 24.", ["Equilibrium: 30 − 2L = 6 + 2L gives L = %d and W = %d." % (Lq, Wq), "At W = 24: demand L = (30 − 24) ÷ 2 = %d; supply L = (24 − 6) ÷ 2 = %d." % (Ld, Ls), "Unemployment = %d − %d = %d hundred workers (600)." % (Ls, Ld, Ls - Ld)], "Equilibrium: W 18, L 6; with minimum wage 24, unemployment = 6 hundred workers")],
       [M("Which factor would shift the demand curve for labour to the right?", "A rise in demand for the firm's product", ["A fall in the price of the product", "An increase in population", "A fall in the wage"], "Derived demand."),
        TF("A minimum wage set below the equilibrium wage reduces employment.", False, "It has no effect because the market wage is higher."),
        N("MPP of an extra worker is 8 units per hour and each unit sells for 250 FCFA. Calculate MRP (FCFA per hour).", 2000, "8 × 250 = 2000.", unit="FCFA")],
       [O("Evaluate the likely effects of a rise in the minimum wage.", "It raises the incomes of low-paid workers who keep their jobs and may reduce poverty, but if the minimum is above the equilibrium wage employers may employ fewer workers, raising unemployment; the effect depends on the elasticity of labour demand and on the ability of employers to raise prices. In the informal sector it may not be enforced.", ["Benefit (1)", "Cost (unemployment) (1)", "Elasticity/enforcement (1)"]),
        M("Wages in a skilled occupation are higher mainly because", "labour supply is limited by long training", ["workers are less productive", "demand is perfectly elastic", "employers choose low wages"], "Few workers qualify, so supply is low.", tier="approfondissement")],
       P("Labour demand W = 30 − 2L and labour supply W = 6 + 2L (hundreds of FCFA per hour and hundreds of workers).",
         [pn("Find equilibrium employment (hundreds of workers).", 6, "30 − 2L = 6 + 2L gives L = 6.", 0, None, 1),
          pn("A minimum wage of 24 is introduced. How many hundred workers are unemployed?", 6, "Supply 9 − demand 3 = 6.", 0, None, 2),
          po("Name one reason the actual unemployment could be smaller.", "Demand for labour may be inelastic, or workers are productive enough that firms keep them, or monopsony power exists.", ["Valid reason (2)"], 2)]),
       sc9, (fig, "Labour market: a minimum wage above equilibrium causes unemployment.", "Downward demand-for-labour line and upward supply line crossing at E, with a dashed horizontal minimum-wage line above the intersection."),
       notes=[NOTE_MONEY, "The legal minimum wage in Cameroon (SMIG) is mentioned only in general; its current level and rules must be checked before use.", "Monopsony is mentioned only briefly."])

# ---- 10 market failure
fig = plot(0, 14, 0, 24, curves=[("22-x", "blue", "D = MSB"), ("2+x", "red", "MPC"), ("6+x", "orange", "MSC")], points=[(10, 12, "market", "red"), (8, 14, "optimum", "green")], xlabel="output", ylabel="price and cost", h=270)
Qm = 10; Qo = 8; assert 22 - Qm == 2 + Qm and 22 - Qo == 6 + Qo
wl = 0.5 * (Qm - Qo) * ((6 + Qm) - (2 + Qm)); assert wl == 4
sc10 = [
    ("A negative externality is", "a cost imposed on third parties not reflected in the price", ["a tax", "a benefit to buyers", "a subsidy"], "e.g. pollution."),
    ("A public good is", "non-excludable and non-rival", ["excludable and rival", "always provided by firms", "a luxury good"], "e.g. street lighting."),
    ("The free-rider problem occurs when", "people benefit without paying", ["producers raise prices", "consumers pay twice", "the state taxes the good"], "So private firms under-provide such goods."),
    ("Merit goods tend to be", "under-consumed in a free market", ["over-consumed", "always free", "never produced"], "e.g. education, vaccination."),
    ("At the market output of a good with a negative externality", "marginal social cost exceeds marginal social benefit", ["MSB exceeds MSC", "MPC exceeds MSC", "MSC equals zero"], "Output is above the socially optimal level."),
]
lesson(c3, "market-failure", "Market failure",
       ["Define market failure and list its main causes.", "Analyse negative externalities with a diagram and measure the welfare loss.", "Explain public goods, merit and demerit goods and information failure."],
       [("definition", "Market failure", "**Market failure** occurs when the free market leads to an **inefficient** allocation of resources (or an unfair one). Main causes: **externalities**, **public goods**, **merit and demerit goods**, **information failure**, **monopoly power** and **immobility of factors**, plus an **inequitable** distribution of income. At the market output, **marginal social cost (MSC) differs from marginal social benefit (MSB)**, causing a **welfare loss**."),
        ("retenir", "Externalities", "An **externality** is a cost or benefit to a third party. **Negative production externality** (e.g. a factory dumping waste in a river): MSC > MPC (marginal private cost), so the market output exceeds the social optimum and price is too low. **Positive externality** (e.g. vaccination, education): MSB > MPB, so too little is produced. The **welfare loss** is the triangle between MSC and MSB over the excess output."),
        ("pieges", "Public goods versus merit goods", "A **public good** is **non-excludable** (cannot stop non-payers using it) and **non-rival** (one person's use does not reduce another's); the **free-rider problem** means private firms do not provide it (defence, street lighting). A **merit good** (education, health care) **can** be sold privately but is **under-consumed** because people undervalue the benefits or lack information; a **demerit good** (tobacco) is over-consumed. Do not say that any good provided by the state is a public good."),
        ("retenir", "Other failures", "**Information failure**: consumers lack knowledge (e.g. counterfeit medicines). **Monopoly power**: higher price and lower output. **Factor immobility**: structural unemployment. **Inequality**: markets reward ability to pay, not need. Governments may respond with taxes, subsidies, regulation, provision, information campaigns or tradable permits, but may suffer **government failure** (poor information, cost, unintended consequences).")],
       [("Worked example — externality", "Demand (MSB): P = 22 − Q; MPC: P = 2 + Q; MSC: P = 6 + Q. Find the market output and the socially optimal output.", ["Market: 22 − Q = 2 + Q gives Q = %d and P = %d." % (Qm, 22 - Qm), "Social optimum: 22 − Q = 6 + Q gives Q = %d and P = %d." % (Qo, 22 - Qo), "The market over-produces by 2 units."], "Market Q = 10; optimum Q = 8"),
        ("Worked example — welfare loss", "Using the same data, calculate the welfare loss at the market output.", ["At Q = 10, MSC = 16 and MSB = 12: the gap is 4.", "The gap falls to 0 at Q = 8: a triangle.", "Welfare loss = ½ × 2 × 4 = %.0f (hundreds of FCFA)." % wl], "4")],
       [M("Which of the following is a positive externality?", "Vaccination protecting people who were not vaccinated", ["Factory smoke", "A tax on cigarettes", "A subsidy for farmers"], "A benefit to third parties."),
        TF("A good that is non-excludable can easily be sold by private firms.", False, "Free riders refuse to pay, so private provision is unlikely."),
        N("MSC is 18 and MPC is 12 at the market output of 100 units. Calculate the external cost per unit.", 6, "MSC − MPC = 6.")],
       [O("Explain why the free market produces too much of a good that causes pollution.", "The producer's costs ignore the harm to third parties, so marginal private cost is below marginal social cost. Price is too low and output is above the level where MSC equals MSB, giving a welfare loss.", ["External cost ignored (1)", "MPC < MSC (1)", "Output too high / welfare loss (1)"]),
        M("Education is best classified as", "a merit good with positive externalities", ["a demerit good", "a public good", "a good with no externalities"], "Private individuals under-value benefits to society.", tier="approfondissement")],
       P("A tannery discharges waste into a river. MSB: P = 22 − Q; MPC: P = 2 + Q; MSC: P = 6 + Q.",
         [pn("Market output.", 10, "22 − Q = 2 + Q: Q = 10.", 0, None, 1),
          pn("Socially optimal output.", 8, "22 − Q = 6 + Q: Q = 8.", 0, None, 1),
          pn("Welfare loss (hundreds of FCFA).", 4, "½ × 2 × 4 = 4.", 0, None, 1),
          po("Suggest one policy to correct the externality and one limitation.", "A tax equal to the external cost (4 per unit) to raise MPC to MSC; limitation: the external cost is hard to measure accurately.", ["Policy (1)", "Limitation (1)"], 2)]),
       sc10, (fig, "A negative externality: market output (10) exceeds the social optimum (8).", "Demand line and two upward lines for private and social costs, with points marked at the market and socially optimal outputs."),
       notes=[NOTE_MONEY, "Local examples (river pollution) are generic; do not name real companies."])
assert 0.5 * 2 * 4 == 4

# ---- 11 government intervention
fig = plot(0, 10, 0, 20, curves=[("20-2*x", "blue", "D"), ("2+x", "red", "S"), ("5+x", "orange", "S + tax")], points=[(6, 8, "E0", "grey"), (5, 10, "E1", "green")], xlabel="quantity", ylabel="price (FCFA hundreds)", h=270)
tax = 3; Q1 = (20 - 2 - tax) / 3; P1 = 20 - 2 * Q1
assert Q1 == 5 and P1 == 10
cons = (P1 - 8) * Q1; prod = (8 - (P1 - tax)) * Q1; rev = tax * Q1
assert (cons, prod, rev) == (10, 5, 15)
ceil_P = 6; qd = (20 - ceil_P) / 2; qs = ceil_P - 2; assert (qd, qs) == (7, 4)
sc11 = [
    ("An indirect tax per unit shifts the supply curve", "upward (to the left)", ["downward (to the right)", "no change", "along the curve"], "The tax adds to the cost of supply."),
    ("The incidence of a tax falls mainly on consumers when demand is", "inelastic", ["elastic", "perfectly elastic", "unit elastic only"], "Producers can pass on the tax."),
    ("A maximum price set below the equilibrium price causes", "a shortage", ["a surplus", "no change", "a fall in demand"], "Quantity demanded exceeds quantity supplied."),
    ("A minimum price (price floor) above equilibrium causes", "a surplus", ["a shortage", "equilibrium", "a lower price"], "e.g. guaranteed price for farmers."),
    ("A subsidy to producers", "shifts supply right and lowers the market price", ["shifts supply left", "raises the price", "has no effect"], "Costs of production fall."),
]
lesson(c3, "intervention", "Government intervention in markets",
       ["Analyse the effects of indirect taxes, subsidies and price controls using diagrams and calculations.", "Calculate tax incidence and revenue.", "Evaluate intervention and government failure."],
       [("definition", "Taxes and subsidies", "An **indirect tax** (e.g. VAT or excise) raises costs, so supply shifts **upward/left** by the amount of the tax. The price rises by **less than the tax** (unless demand is perfectly inelastic) and quantity falls. **Tax incidence** is the share of the tax borne by consumers (rise in price) and by producers (fall in net price). A **subsidy** lowers costs, shifting supply right: price falls and quantity rises; it costs the government money."),
        ("retenir", "Price controls", "A **maximum price (ceiling)** below the equilibrium price causes **excess demand** (shortage), queues and black markets (e.g. rent control, price caps on essentials). A **minimum price (floor)** above equilibrium causes **excess supply** (e.g. guaranteed prices for farmers, minimum wages); the government may have to buy the surplus. **Regulation** (standards, licences, competition law) and direct **provision** and **public ownership** are other tools."),
        ("pieges", "Who pays the tax?", "Do not assume the whole tax is passed on to consumers. The more **inelastic** demand is relative to supply, the more the burden falls on consumers; the more elastic demand, the more falls on producers. To measure incidence use the **price rise** for consumers and the **fall in net price** (after the tax) for producers. The tax revenue is tax per unit × new quantity, not the old quantity."),
        ("retenir", "Evaluating government action", "Intervention may improve efficiency or equity but there may be **government failure**: poor information, administration costs, unintended consequences (black markets, smuggling), political pressure, and delays. Evaluate by considering the **size** of the market failure, **elasticities**, **costs**, **time lags** and **who gains and loses**.")],
       [("Worked example — tax incidence", "D: P = 20 − 2Q; S: P = 2 + Q (P in hundreds of FCFA). A tax of 3 per unit is imposed. Find the new equilibrium, who bears the tax and the revenue.", ["New supply P = 5 + Q: 20 − 2Q = 5 + Q gives Q = %d and P = %d (before: Q = 6, P = 8)." % (Q1, P1), "Consumers pay 10 − 8 = 2 more per unit: %d in total; producers keep 10 − 3 = 7, i.e. 1 less per unit: %d in total." % (cons, prod), "Revenue = 3 × %d = %d (= %d + %d)." % (Q1, rev, cons, prod)], "Q = 5, P = 10; consumers 10, producers 5; revenue 15"),
        ("Worked example — maximum price", "With the same market (before the tax), a maximum price of 6 is imposed. Find the shortage.", ["Demand at 6: 20 − 2Q = 6 gives Q = %d." % qd, "Supply at 6: 2 + Q = 6 gives Q = %d." % qs, "Shortage = 7 − 4 = %d tonnes." % (qd - qs)], "Shortage of 3 tonnes")],
       [M("A government subsidy to farmers will normally", "lower the market price and raise quantity", ["raise price and lower quantity", "have no effect on price", "reduce supply"], "Supply shifts right."),
        TF("A maximum price below equilibrium creates a surplus.", False, "It creates a shortage."),
        N("Demand: P = 20 − 2Q; supply: P = 2 + Q. A maximum price of 4 is set. What is the excess demand?", (20 - 4) / 2 - (4 - 2), "Demand: Q = 8; supply: Q = 2; excess demand = 6.")],
       [O("Explain why a tax on a good with inelastic demand raises more revenue than a tax on a good with elastic demand.", "With inelastic demand quantity falls only a little when the price rises, so most of the tax is passed on and the quantity on which the tax is paid remains high, giving higher revenue; with elastic demand quantity falls sharply, so revenue is lower.", ["Inelastic: small fall in quantity (1)", "Tax passed on to consumers (1)", "Higher revenue (1)"]),
        M("Which is an example of government failure?", "A price ceiling that leads to a black market", ["A tax that reduces pollution", "A subsidy that lowers price", "Free vaccination"], "An unintended consequence of intervention.", tier="approfondissement")],
       P("In a market D: P = 20 − 2Q; S: P = 2 + Q (hundreds of FCFA), the government imposes a tax of 3 per unit.",
         [pn("New equilibrium quantity.", 5, "20 − 2Q = 5 + Q gives Q = 5.", 0, None, 1),
          pn("New market price (hundreds of FCFA).", 10, "P = 20 − 10 = 10.", 0, None, 1),
          pn("Total tax revenue.", 15, "3 × 5 = 15.", 0, None, 1),
          po("Which group bears the larger share of the tax? Explain.", "Consumers: their price rises by 2 of the 3 tax, while producers lose 1 per unit net, because demand is relatively inelastic compared with supply.", ["Consumers (1)", "Reason (1)"], 2)]),
       sc11, (fig, "A tax of 3 shifts supply from S to S + tax: the price rises from 8 to 10 and quantity falls from 6 to 5.", "Demand line and two upward supply lines, the second above the first, with points marked for the original and new equilibrium."),
       notes=[NOTE_MONEY, "Taxes in Cameroon (VAT, customs duties) are mentioned only generally here; rates and rules must be checked.", "Government failure is introduced only briefly."])

# ---- 12 national income
fig = shapes([T(215, 16, "Circular flow of income", 14, bold=True),
              RECT(30, 70, 120, 56, fill="lightblue", radius=8), T(90, 103, "Households", 14),
              RECT(280, 70, 120, 56, fill="lightgreen", radius=8), T(340, 103, "Firms", 14),
              LINE(150, 84, 280, 84, arrow="end", width=2), T(215, 76, "spending (C)", 12),
              LINE(280, 114, 150, 114, arrow="end", width=2), T(215, 138, "wages, rent, interest, profit", 12),
              T(215, 175, "Injections: investment (I), government (G), exports (X)", 12, color="green"),
              T(215, 195, "Leakages: saving (S), taxes (T), imports (M)", 12, color="red")], 430, 215)
C, I_, G, X, M_ = 12000, 3000, 2500, 2400, 2900
gdp = C + I_ + G + (X - M_); assert gdp == 17000
nia = -400; gni = gdp + nia; assert gni == 16600
defl = 125; real = gdp / defl * 100; assert real == 13600
pc = gdp * 1e9 / 20e6; assert pc == 850000
g_rate = (14076 - 13600) / 13600 * 100; assert abs(g_rate - 3.5) < 1e-9
sc12 = [
    ("GDP is the", "total value of goods and services produced within a country in a year", ["total value of exports", "total savings", "government spending"], "Production inside the borders."),
    ("The expenditure method of GDP is", "C + I + G + (X − M)", ["C + S + T", "W + R + I + P", "X + M"], "Consumption, investment, government, net exports."),
    ("Real GDP is", "GDP adjusted for changes in prices", ["GDP in current prices", "GDP per person", "GNI minus taxes"], "It allows comparison over time."),
    ("GNI differs from GDP by", "net income from abroad", ["taxes only", "imports", "subsidies"], "GNI = GDP + net property income from abroad."),
    ("Which is a limitation of GDP per capita as a measure of living standards?", "It ignores income distribution and the informal economy", ["It is too accurate", "It includes only exports", "It measures pollution"], "Averages hide inequality and unrecorded output."),
]
lesson(c3, "national-income", "National income and the circular flow",
       ["Describe the circular flow of income and the three methods of measuring national income.", "Calculate GDP, GNI, real GDP, per capita figures and growth rates.", "Evaluate GDP as a measure of living standards."],
       [("definition", "National income measures", "**GDP** (gross domestic product) is the total value of final goods and services produced in a country in a year. Three equivalent methods: **output** (value added in all sectors), **income** (wages, rent, interest, profit) and **expenditure**: **GDP = C + I + G + (X − M)**. **GNI** = GDP + net income from abroad. **Net** means after deducting capital consumption (depreciation). **Per capita** = GDP ÷ population."),
        ("retenir", "Circular flow", "Households supply factors to firms and receive income; they spend on goods. Saving (S), taxes (T) and imports (M) are **leakages**; investment (I), government spending (G) and exports (X) are **injections**. National income is in equilibrium when **leakages = injections**. **Nominal** GDP uses current prices; **real** GDP removes inflation: real GDP = nominal GDP ÷ price index × 100."),
        ("pieges", "What GDP leaves out", "GDP counts only **final** goods (to avoid double counting) and excludes unpaid work, most of the **informal economy** and the value of leisure, and does not subtract environmental damage. A high GDP per capita does not mean that income is evenly shared. Do not compare nominal GDP over time; compare **real** GDP. Transfer payments (pensions, grants) and second-hand sales are not included in GDP."),
        ("retenir", "Living standards", "Better indicators combine GDP per capita with the **Human Development Index** (income, education, life expectancy), poverty and inequality measures, and environmental data. International comparisons use **purchasing power parity** exchange rates. Growth in real GDP per capita is a rough guide to improvements in material well-being.")],
       [("Worked example — GDP and GNI", "An imaginary economy has C = 12 000, I = 3000, G = 2500, X = 2400, M = 2900 (billion FCFA) and net income from abroad of −400. Calculate GDP and GNI.", ["GDP = C + I + G + (X − M) = 12 000 + 3000 + 2500 + (2400 − 2900).", "GDP = %d billion FCFA." % gdp, "GNI = GDP + net income from abroad = %d − 400 = %d billion FCFA." % (gdp, gni)], "GDP = 17 000; GNI = 16 600 billion FCFA"),
        ("Worked example — real GDP and growth", "Using the nominal GDP of 17 000 with a price index of 125 (base year 100), find real GDP. If real GDP next year is 14 076, find the growth rate. Population is 20 million; find GDP per capita.", ["Real GDP = 17 000 ÷ 125 × 100 = %d billion FCFA." % real, "Growth = (14 076 − 13 600) ÷ 13 600 × 100 = %.1f %%." % g_rate, "GDP per capita = 17 000 × 10⁹ ÷ 20 × 10⁶ = %d FCFA." % pc], "Real GDP 13 600; growth 3.5 %; per capita 850 000 FCFA")],
       [M("Which of these is included in GDP?", "The value of a new school building", ["Pensions paid by the state", "Second-hand car sales", "Unpaid housework"], "Investment in new capital goods is counted; transfers and second-hand sales are not."),
        TF("Real GDP is adjusted for inflation.", True, "Nominal GDP divided by the price index × 100."),
        N("Nominal GDP is 24 000 and the price index is 120. Calculate real GDP.", 20000, "24 000 ÷ 120 × 100 = 20 000.")],
       [O("Explain why GDP per capita may be a poor measure of living standards.", "It is an average and hides inequality; it excludes unpaid and informal activity; it ignores leisure, environmental damage and quality of life; and it does not show how much goes on harmful activities.", ["Distribution ignored (1)", "Informal sector/unpaid work (1)", "Environment/quality of life (1)"]),
        M("An economy's real GDP rises by 3 % while population rises by 3 %. Real GDP per capita", "stays about the same", ["rises by 3 %", "falls by 3 %", "doubles"], "Both numerator and denominator grow equally.", tier="approfondissement")],
       P("In an imaginary economy C = 8000, I = 2000, G = 1500, X = 1800 and M = 1600 (billion FCFA); the price index is 110.",
         [pn("Calculate GDP (billion FCFA).", 8000 + 2000 + 1500 + 1800 - 1600, "8000 + 2000 + 1500 + (1800 − 1600) = 11 700.", 0, None, 1),
          pn("Calculate real GDP (billion FCFA, to the nearest whole number).", round(11700 / 110 * 100), "11 700 ÷ 110 × 100 = 10 636.4, i.e. 10 636.", 1, None, 2),
          po("State two reasons real GDP is preferred to nominal GDP for comparing years.", "Nominal GDP can rise just because prices rise; real GDP removes the effect of inflation, showing the change in the quantity of output.", ["Inflation distorts nominal (1)", "Real shows output changes (1)"], 2)]),
       sc12, (fig, "The circular flow of income between households and firms, with injections and leakages.", "Two boxes, households and firms, joined by arrows for spending and for factor incomes, with text lines for injections and leakages."),
       notes=[NOTE_MONEY + " The imaginary economy is not Cameroon.", "Illustrative scale used; do not interpret as Cameroon's GDP.", NOTE_ORDER])
assert round(11700 / 110 * 100) == 10636

write_compact(p)
