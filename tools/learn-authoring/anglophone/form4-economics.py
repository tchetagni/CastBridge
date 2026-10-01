"""Generator: content/learn/form4-economics (Economics - Form 4, first-year O Level course). Run: python3 form4-economics.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from eco_lib import *
TAG = "Form 4 — "
p = Pack("form4-economics", "Economics — Form 4", level="Form 4", subject="economie", cursus="secondary",
         description="A first course in Economics for Form 4 (the first year of the O Level course): wants and needs, factors of production, specialisation, demand and supply, kinds of business, money, banks and saving, household income and spending, population and the role of government, with worked examples, graded exercises and self-checks.",
         programRef="Cameroon GCE Board — Ordinary Level Economics syllabus, first year (Form 4) — to be checked against the official texts")
NOTE_ORDER = "The split of topics between Form 4 and Form 5 is an editorial choice to be checked against the official programme."

# ======================================================================= CH 1 CONCEPTS
c1 = p.chapter("concepts", "What is economics?", programRef="GCE O Level Economics — introduction (to be checked)")

# ---- 1 wants, needs, choice
spend = 10000 - 4500 - 3000; assert spend == 2500
fig = shapes([T(200, 22, "A scale of preference (most important first)", 14, bold=True),
              RECT(60, 40, 280, 34, fill="lightgreen", radius=6), T(200, 62, "1. School fees", 13),
              RECT(60, 84, 280, 34, fill="lightyellow", radius=6), T(200, 106, "2. Food", 13),
              RECT(60, 128, 280, 34, fill="lightorange", radius=6), T(200, 150, "3. A new football", 13),
              RECT(60, 172, 280, 34, fill="lightblue", radius=6), T(200, 194, "4. A cinema ticket", 13)], 400, 220)
sc = [
    ("Economics is the study of", "how people choose to use limited resources to satisfy unlimited wants", ["how to make money without work", "only the banks of a country", "how to avoid paying tax"], "Choice because of scarcity is the heart of the subject."),
    ("Which of these is a basic need?", "Food", ["A video game", "A bicycle for fun", "Fashion jewellery"], "Food is needed to live."),
    ("A person chooses a taxi over a bus. The opportunity cost of the taxi is", "the bus ride that was given up", ["the taxi fare only", "all other ways of travelling", "nothing at all"], "It is the next best alternative given up."),
    ("Goods differ from services because goods", "are physical things that can be touched", ["cannot be seen", "are never sold", "are always free"], "Services, like teaching, are activities."),
    ("Wants are said to be unlimited because", "once one want is met people think of new ones", ["the government fixes them", "money has no value", "there are few goods"], "Human wants keep growing."),
]
lesson(c1, "wants-choice", "Wants, needs and choice",
    ["Say what economics is about.", "Tell the difference between wants and needs and between goods and services.", "Explain choice and opportunity cost in simple examples."],
    [("definition", "What economics studies", "**Economics** studies how people, families, firms and governments use **limited** resources to satisfy **unlimited** wants. **Needs** are things we must have to live (food, water, shelter, clothing). **Wants** are things we would like to have (a phone, a football, fashionable shoes). **Goods** are things you can touch (rice, a book). **Services** are activities done for you (teaching, a haircut, a taxi ride)."),
     ("definition", "Scarcity, choice and opportunity cost", "Because resources and money are limited, we cannot satisfy all our wants. This is **scarcity**. We must **choose**. When we choose one thing, we give up another. The **opportunity cost** of a choice is the **next best thing** we give up. A **scale of preference** is a list of wants arranged from the most to the least important."),
     ("pieges", "Be careful", "A need for one person may be a want for another: a bicycle is a want for a child but a need for a farmer who must travel far. Opportunity cost is the **one** best alternative given up, not all the things you gave up and not the price you paid. Scarcity does not only affect poor people; everyone must choose."),
     ("retenir", "Free goods and economic goods", "A **free good** (such as air in an open place) costs nothing to use and is not scarce. An **economic good** is scarce and has a price (cassava, a mobile phone, a school uniform). Most goods and services are economic goods, so people need money, time and effort to get them.")],
    [("Worked example — choosing with money", "Etienne has 10 000 FCFA. His list is: school exercise books (4 500 FCFA), a football (3 000 FCFA) and a cinema ticket (4 000 FCFA). He buys the exercise books and the football. How much money is left and what did he give up?", ["He spends 4 500 + 3 000 = %d FCFA." % (4500 + 3000), "Money left = 10 000 − 7 500 = %d FCFA, which is not enough for the cinema ticket (4 000)." % spend, "He gave up the cinema ticket: that is the opportunity cost of buying both things."], "2 500 FCFA left; the cinema ticket was given up"),
     ("Worked example — goods or services", "Sort these into goods and services: a bag of rice, a haircut, a mobile phone, a bus ride.", ["A bag of rice and a mobile phone are things you can touch: goods.", "A haircut and a bus ride are activities done for you.", "So they are services."], "Goods: rice, phone. Services: haircut, bus ride")],
    [M("Which of these is a service?", "A bus ride", ["A bag of rice", "A pair of shoes", "A table"], "A bus ride is an activity."),
     TF("A want is something we must have to live.", False, "That describes a need; a want is something we would like to have."),
     N("Mireille has 6 000 FCFA. She buys a school bag for 3 500 FCFA. How many FCFA does she have left?", 6000 - 3500, "6 000 − 3 500 = 2 500.", unit="FCFA")],
    [O("Describe a choice you or your family made recently and say what the opportunity cost was.", "Example: the family had 20 000 FCFA and chose to buy school books rather than new shoes, so the opportunity cost was the new shoes. The answer must name the choice and the next best alternative given up.", ["Choice described (1)", "Alternative named (1)", "Opportunity cost explained (1)"]),
     M("Which statement about free goods is correct?", "They are not scarce and have no price", ["They are always the most expensive", "They are made in factories", "They do not exist anywhere"], "Air in an open place is an example.", tier="approfondissement")],
    P("Ngu has 15 000 FCFA to spend at the market in Bafoussam. He wants shoes (12 000 FCFA), a new shirt (6 000 FCFA) and school pens (3 000 FCFA).",
      [pm("Shoes and a shirt together would cost", "18 000 FCFA, so he cannot afford both", ["15 000 FCFA, so he can afford both", "9 000 FCFA", "3 000 FCFA"], "12 000 + 6 000 = 18 000, more than 15 000.", 1),
       pn("If he buys the shoes and the pens, how much money is left?", 15000 - 12000 - 3000, "15 000 − 12 000 − 3 000 = 0.", 0, "FCFA", 1),
       po("Explain why Ngu must make a choice.", "His money is limited but he wants more than he can pay for, so he cannot have everything. Choosing the shoes means giving up the shirt (opportunity cost).", ["Scarcity (1)", "Choice and what is given up (1)"], 2)]),
    tag(TAG, sc), (fig, "A scale of preference: wants listed from the most to the least important.", "Four stacked boxes numbered one to four showing school fees, food, a football and a cinema ticket."),
    notes=["Prices are invented teaching examples in FCFA.", NOTE_ORDER])

# ---- 2 factors of production
rev, wages, rent_, seeds = 300000, 90000, 40000, 50000
profit = rev - wages - rent_ - seeds; assert profit == 120000
fig = hflow(["Land", "Labour", "Capital", "Enterprise"], w=460, size=13, title="Four factors of production")
sc = [
    ("The four factors of production are", "land, labour, capital and enterprise", ["wages, rent, interest and profit", "goods, services, money and prices", "farming, trade, banking and transport"], "These are the resources used in production."),
    ("The reward for labour is", "wages", ["rent", "interest", "profit"], "Workers are paid wages."),
    ("In economics, capital means", "man-made things used to produce other goods", ["only cash in a drawer", "only land", "only wages"], "Machines and tools are capital."),
    ("Profit is the reward for", "enterprise", ["land", "labour", "capital"], "The entrepreneur organises production and takes the risk."),
    ("A tailor's sewing machine is an example of", "capital", ["land", "enterprise", "labour"], "It is a man-made good used in production."),
]
lesson(c1, "factors", "The factors of production",
    ["Name the four factors of production.", "Give examples of each and its reward.", "Calculate a simple profit from farm accounts."],
    [("definition", "Factors of production", "**Production** is making goods and services. To produce, we need four **factors of production**. **Land** means all natural resources: soil, forests, rivers, minerals. **Labour** is the work of people. **Capital** is man-made equipment used to produce, such as tools, machines and buildings. **Enterprise** is the person who organises the other factors and takes risks: the **entrepreneur**."),
     ("retenir", "Rewards", "Each factor earns a reward: land earns **rent**, labour earns **wages**, capital earns **interest** and enterprise earns **profit**. **Profit** is what is left from sales after all costs have been paid. If costs are bigger than sales, there is a **loss**."),
     ("pieges", "Common mistakes", "Capital is **not** simply money in the pocket: in economics it is the tools and machines used to make other goods. Do not mix up **rent** (reward for land) with house rent paid by a tenant, although the idea is similar. A worker's tools belong to capital, but the worker is labour. One person can supply more than one factor."),
     ("retenir", "Example from a farm", "On a farm in Bali, the field is land, the people who plant and harvest are labour, the hoes and the storage shed are capital and the farmer who plans the planting, buys seed and takes the risk of bad weather is the entrepreneur.")],
    [("Worked example — farm profit", "Mama Ngu sells crops worth 300 000 FCFA. She pays workers 90 000 FCFA, land rent 40 000 FCFA and 50 000 FCFA for seeds (illustrative). Find her profit.", ["Total costs = 90 000 + 40 000 + 50 000 = %d FCFA." % (wages + rent_ + seeds), "Profit = sales − costs = 300 000 − 180 000.", "= %d FCFA." % profit], "120 000 FCFA"),
     ("Worked example — name the factor", "A baker in Limbe uses an oven, hires two helpers, rents the shop and plans the day's baking. Name each factor.", ["The oven is capital and the shop's land and building are land and capital.", "The helpers are labour.", "The baker is the entrepreneur."], "Capital, land, labour, enterprise")],
    [M("Which factor of production earns wages?", "Labour", ["Land", "Capital", "Enterprise"], "Workers receive wages."),
     TF("A farm tractor is an example of capital.", True, "It is a man-made good used in production."),
     N("A trader sells goods worth 80 000 FCFA and has costs of 55 000 FCFA (illustrative). Find the profit.", 25000, "80 000 − 55 000 = 25 000.", unit="FCFA")],
    [O("Name the four factors of production and give one example of each from a village in Cameroon.", "Land: a cocoa farm; labour: the workers who harvest the pods; capital: machetes and drying racks; enterprise: the farmer who organises the work and takes the risk.", ["Four factors named (2)", "Example for each (1)"]),
     M("The reward paid to the owner of land is", "rent", ["wages", "interest", "profit"], "Rent is the reward of land.", tier="approfondissement")],
    P("A woman in Kumba runs a small restaurant. Her costs in a week are 25 000 FCFA for food, 18 000 FCFA wages and 7 000 FCFA rent. Her sales are 70 000 FCFA (illustrative).",
      [pn("Total costs (FCFA)?", 25000 + 18000 + 7000, "25 000 + 18 000 + 7 000 = 50 000.", 0, "FCFA", 1),
       pn("Profit (FCFA)?", 70000 - 50000, "70 000 − 50 000 = 20 000.", 0, "FCFA", 1),
       po("Which factor of production is the woman herself supplying as organiser?", "Enterprise: she organises the business and takes the risk.", ["Enterprise named (1)"], 1)]),
    tag(TAG, sc), (fig, "The four factors of production: land, labour, capital and enterprise.", "Four boxes in a row naming the factors."),
    notes=["Prices are invented teaching examples.", NOTE_ORDER])

# ======================================================================= CH 2 PRODUCTION
c2 = p.chapter("production", "Production and exchange", programRef="GCE O Level Economics — production, division of labour (to be checked)")
alone = 4; together = 72 / 12; assert together == 6
fig = tabfig(["Stage", "Worker", "Task"], [["1", "Ayuk", "cuts the cloth"], ["2", "Beatrice", "sews the shirt"], ["3", "Collins", "irons and packs"]], w=400, colw=[70, 130, 200], size=13, title="Division of labour in a tailoring shop")
sc = [
    ("Division of labour means", "dividing production into stages, each done by different workers", ["every worker doing all the jobs", "sharing profits equally", "workers dividing their wages"], "Tasks are shared out."),
    ("Specialisation means", "concentrating on the work one does best", ["doing a different job every hour", "producing only for one's family", "avoiding all trade"], "Workers, firms or regions focus on what they do best."),
    ("One advantage of division of labour is", "workers become faster at their task", ["no worker needs any skill", "output always falls", "no machines can be used"], "Practice improves speed and skill."),
    ("One disadvantage of division of labour is", "work may become boring and repetitive", ["output always falls", "workers earn nothing", "no tasks are shared"], "Doing the same small task all day can be tiring."),
    ("A baker who sells bread and buys cloth for clothes is taking part in", "exchange", ["subsistence production", "barter only", "free production"], "People exchange what they make for what they need."),
]
lesson(c2, "division-labour", "Specialisation and exchange",
    ["Explain division of labour and specialisation.", "Give advantages and disadvantages.", "Explain why specialisation needs exchange."],
    [("definition", "Dividing the work", "In **division of labour**, making a product is split into stages and each worker does one stage. In a shirt workshop one person cuts, another sews and a third irons. **Specialisation** means a person, a firm or a region concentrates on one thing it does well. Specialising means people make more than they need of one good, so they must **exchange** the extra for other goods."),
     ("retenir", "Advantages and disadvantages", "**Advantages**: workers become skilled and fast; time is saved; output rises; goods may become cheaper. **Disadvantages**: the work can be boring and repetitive; workers may not learn other skills; if one stage stops, the whole process stops; people depend on others for the goods they do not make."),
     ("retenir", "Why exchange matters", "A farmer in the West may specialise in maize and a fisherman in Limbe in fish. Each needs what the other produces, so they trade, using **money** to make exchange easy. The bigger the market, the more specialisation is possible. Good roads and markets help exchange."),
     ("attention", "Do not mix", "**Division of labour** is about **tasks inside** one production process; **specialisation** is wider and may concern whole regions or countries. Not all work can be divided: a single artist painting a picture cannot easily divide tasks. Too much division can make work dull.")],
    [("Worked example — more output", "Twelve tailors each make 4 shirts a week working alone. When tasks are divided, the group makes 72 shirts a week (illustrative). Find output per tailor before and after.", ["Before: 4 shirts each.", "After: 72 ÷ 12 = %d shirts each." % together, "Output per worker has risen by 2 shirts."], "4 shirts, then 6 shirts per tailor"),
     ("Worked example — why exchange", "A farmer in Bali grows only maize. She needs salt, soap and school books. How does she get them?", ["She keeps some maize for her family.", "She sells the extra maize at the market for money.", "She uses the money to buy the salt, soap and books."], "By selling her surplus and using the money")],
    [M("Which is an advantage of division of labour?", "Workers become more skilled at one task", ["Workers do not need to learn", "Every worker does all the jobs", "No exchange is needed"], "Practice improves skill."),
     TF("Specialisation reduces the need for exchange.", False, "Specialisation makes exchange more necessary."),
     N("Ten workers make 50 stools in a week working alone. After dividing tasks they make 80 stools. By how many stools does output per worker rise?", 80 / 10 - 50 / 10, "80 ÷ 10 = 8; 50 ÷ 10 = 5; the rise is 3 stools.", unit="stools")],
    [O("Explain two advantages and one disadvantage of division of labour.", "Advantages: workers become skilled and fast; output rises. Disadvantage: the work becomes repetitive and boring, so workers may lose interest.", ["Two advantages (2)", "One disadvantage (1)"]),
     M("Why do specialised farmers need markets and money?", "To exchange their surplus for other goods", ["To keep all their output", "To avoid selling", "To destroy extra crops"], "Exchange links producers.", tier="approfondissement")],
    P("A bakery in Buea has 5 workers. Alone each makes 40 loaves a day. After dividing tasks the bakery makes 300 loaves a day (illustrative).",
      [pn("Total output when working alone (loaves)?", 5 * 40, "5 × 40 = 200.", 0, "loaves", 1),
       pn("Output per worker after division (loaves)?", 300 / 5, "300 ÷ 5 = 60.", 0, "loaves", 1),
       po("State one possible disadvantage for the workers.", "The work becomes boring; if one worker is absent the other stages are delayed.", ["Valid disadvantage (1)"], 1)]),
    tag(TAG, sc), (fig, "Division of labour in a tailoring shop: three workers, three tasks.", "A three-row table showing the cutter, the sewer and the person who irons and packs."),
    notes=["Numbers are invented.", NOTE_ORDER])

# ======================================================================= CH 3 MARKETS
c3 = p.chapter("markets", "Demand, supply and price", programRef="GCE O Level Economics — demand and supply (to be checked)")
dem = {100: 55, 200: 45, 300: 35, 400: 25, 500: 15}; assert all(dem[k] == 65 - k / 10 for k in dem)
fig = bars([("100", 55, "blue"), ("200", 45, "blue"), ("300", 35, "blue"), ("400", 25, "blue"), ("500", 15, "blue")], unit="puff-puff bought at each price (FCFA)", w=400, h=250)
sc = [
    ("Demand is the quantity of a good that people", "are willing and able to buy at a given price", ["wish for but cannot pay for", "the shop owner makes", "the government sets"], "Demand needs both willingness and ability to pay."),
    ("When the price of a good rises, the quantity demanded usually", "falls", ["rises", "stays the same always", "doubles"], "This is the law of demand."),
    ("In a demand schedule the numbers show", "quantity demanded at different prices", ["wages at different jobs", "the profit of a shop", "the population of a town"], "It is a table of prices and quantities."),
    ("A rise in people's incomes will usually", "increase the demand for most goods", ["decrease the demand for all goods", "end all trade", "reduce prices to zero"], "People can afford more."),
    ("A change in the price of the good itself causes", "a movement along the demand curve", ["a new demand curve", "a change in tastes", "a change in income"], "Other changes shift the whole demand curve."),
]
lesson(c3, "demand", "Demand and the demand schedule",
    ["Define demand and the law of demand.", "Read a demand schedule and a bar chart.", "Name factors that change demand."],
    [("definition", "What is demand?", "**Demand** is the quantity of a good or service that people are **willing and able** to buy at a given price during a period. The **law of demand**: when the price falls, people buy more; when the price rises, they buy less. A **demand schedule** is a table of prices and the quantities bought. Drawn as a graph it gives the **demand curve**, which slopes downwards."),
     ("retenir", "What changes demand?", "Besides price, demand depends on **income** (more income, more demand for most goods), **tastes and fashion**, **prices of other goods** (if rice becomes dearer, people may buy more garri), the **number of buyers** and **advertising**. A change in price only moves us **along** the same curve; a change in income, tastes or another factor **shifts** the whole curve."),
     ("pieges", "Quantity or demand?", "If the **price** changes, write that the **quantity demanded** changes. If **income, taste or population** changes, write that **demand** increases or decreases. Do not confuse 'I want it' with demand: demand needs money to pay. A rise in price does not make people stop buying completely, it only reduces the amount."),
     ("retenir", "Reading a demand table", "Look down the table: as price goes up, quantity falls. To find the change in quantity between two prices, subtract. To find total spending at a price multiply **price × quantity**. Spending is not always the same at each price, so compare.")],
    [("Worked example — reading the schedule", "A seller of puff-puff in Kumba records the number bought each day: at 100 FCFA, 55; at 200 FCFA, 45; at 300 FCFA, 35; at 400 FCFA, 25; at 500 FCFA, 15 (illustrative). How many fewer are bought when the price rises from 200 to 400 FCFA?", ["At 200 FCFA, 45 are bought.", "At 400 FCFA, 25 are bought.", "45 − 25 = %d fewer puff-puff." % (45 - 25)], "20 fewer"),
     ("Worked example — spending", "Using the same table, how much do customers spend in total when the price is 300 FCFA?", ["At 300 FCFA, 35 are bought.", "Spending = price × quantity = 300 × 35.", "= %d FCFA." % (300 * 35)], "10 500 FCFA")],
    [M("When the price of oranges falls, the quantity demanded", "rises", ["falls", "stays the same", "becomes zero"], "Law of demand."),
     TF("A rise in income can increase demand for most goods.", True, "People can afford to buy more."),
     N("From the puff-puff table, how many are bought at 500 FCFA?", 15, "The table gives 15 at 500 FCFA.", unit="puff-puff")],
    [O("Explain why the demand for ice-cream may rise on a very hot day.", "People want cold food more, so tastes change, and demand increases at every price; the demand curve shifts right.", ["Cause named (1)", "Demand rises (1)", "Curve shift (1)"]),
     M("If garri becomes much dearer, the demand for cassava flour from other sources is likely to", "rise", ["fall", "end", "stay the same always"], "Consumers switch to a substitute.", tier="approfondissement")],
    P("A school canteen in Douala records the number of sandwiches sold: 80 at 300 FCFA, 60 at 400 FCFA and 40 at 500 FCFA (illustrative).",
      [pn("How many fewer sandwiches are sold at 500 FCFA than at 300 FCFA?", 80 - 40, "80 − 40 = 40.", 0, None, 1),
       pn("Spending at 400 FCFA (FCFA)?", 400 * 60, "400 × 60 = 24 000.", 0, "FCFA", 1),
       po("Does this table obey the law of demand? Explain.", "Yes: as the price rises from 300 to 500, quantity demanded falls from 80 to 40.", ["Yes with evidence (1)"], 1)]),
    tag(TAG, sc), (fig, "Demand for puff-puff at five prices: the quantity bought falls as the price rises.", "Five bars labelled with prices 100 to 500 FCFA, decreasing in height from 55 to 15."),
    notes=["The demand table is invented for teaching.", NOTE_ORDER])

sup = {100: 15, 200: 25, 300: 35, 400: 45, 500: 55}
fig = tabfig(["Price", "Demand", "Supply", "Result"], [["100", "55", "15", "shortage 40"], ["200", "45", "25", "shortage 20"], ["300", "35", "35", "balance"], ["400", "25", "45", "surplus 20"], ["500", "15", "55", "surplus 40"]], w=440, colw=[90, 100, 100, 150], size=12, title="Demand and supply of puff-puff (FCFA)")
assert dem[300] == sup[300]
sc = [
    ("Supply is the quantity of a good that sellers", "are willing and able to offer at a given price", ["wish to keep for themselves", "buy from other countries only", "are forced to produce"], "Supply depends on the price and costs."),
    ("When the price rises, the quantity supplied usually", "rises", ["falls", "stays the same", "disappears"], "Higher prices encourage producers to sell more."),
    ("The market price is where", "the quantity demanded equals the quantity supplied", ["supply is zero", "demand is zero", "the government sets the cost"], "This is the equilibrium."),
    ("If the price is above the equilibrium price there is", "a surplus", ["a shortage", "perfect balance", "no goods"], "Sellers offer more than buyers want."),
    ("A shortage will usually cause the price to", "rise", ["fall", "stay fixed", "become zero"], "Buyers compete for the goods."),
]
lesson(c3, "price", "Supply and the market price",
    ["Define supply and the law of supply.", "Find the equilibrium price from a table.", "Explain what happens with a surplus or a shortage."],
    [("definition", "Supply and the market price", "**Supply** is the quantity sellers are **willing and able to offer** at each price. The **law of supply**: when the price rises, sellers offer more; when it falls, they offer less. The **market price** (equilibrium price) is the price at which the quantity demanded equals the quantity supplied. At this price the market is **balanced**: everyone who wants to buy at that price can buy."),
     ("retenir", "Shortage and surplus", "If the price is **too low**, buyers want more than sellers offer: a **shortage**. Competing buyers push the price **up**. If the price is **too high**, sellers offer more than buyers want: a **surplus**. Sellers cut the price to sell the extra. The price moves toward the equilibrium. **Shortage = demand − supply**; **surplus = supply − demand** at a given price."),
     ("retenir", "What changes supply?", "Supply increases when **costs fall** (cheaper seeds, fuel or wages), when **technology** improves, when the **weather** is good for farm products, or when the government gives a **subsidy**. Supply decreases when costs rise, when there is drought, or when a tax is placed on sellers. The new supply shifts the market price."),
     ("pieges", "Do not mix", "A price change moves us **along** the curve; a change in costs, weather or technology **shifts** the supply curve. Do not say there is a surplus and a shortage at the same price. Equilibrium does not mean that everyone is happy: it only means demand equals supply.")],
    [("Worked example — equilibrium", "Use the table: at 200 FCFA demand is 45 and supply is 25; at 300 FCFA, demand 35 and supply 35; at 400 FCFA, demand 25 and supply 45. Find the equilibrium price.", ["At 200: demand − supply = 45 − 25 = 20, a shortage.", "At 300: demand 35 = supply 35, so there is balance.", "At 400: supply − demand = 20, a surplus. So the equilibrium price is 300 FCFA."], "300 FCFA"),
     ("Worked example — surplus", "Using the same table, find the surplus at 500 FCFA (demand 15, supply 55).", ["At 500 FCFA the quantity supplied is 55.", "The quantity demanded is 15.", "Surplus = 55 − 15 = %d. Sellers will cut the price." % (55 - 15)], "A surplus of 40")],
    [M("At the equilibrium price", "quantity demanded equals quantity supplied", ["there is always a shortage", "there is always a surplus", "supply is zero"], "That is the definition."),
     TF("A surplus tends to push the price down.", True, "Sellers cut prices to sell the extra goods."),
     N("At 100 FCFA demand is 55 and supply is 15 (see the table). How big is the shortage?", 55 - 15, "55 − 15 = 40.", unit="units")],
    [O("Explain what happens to the price of tomatoes in a market if a good harvest greatly increases supply.", "With more tomatoes the supply is greater than demand at the old price, so there is a surplus. Sellers lower the price until demand again equals supply at a lower price.", ["Supply rise (1)", "Surplus (1)", "Price falls (1)"]),
     M("A fall in the cost of fertiliser will probably", "increase the supply of maize", ["decrease the supply of maize", "stop all farming", "raise the price of fertiliser to zero"], "Lower costs make farming more profitable.", tier="approfondissement")],
    P("A market for garri has demand and supply (kg): at 400 FCFA demand 60, supply 20; at 500 FCFA demand 50, supply 40; at 600 FCFA demand 40, supply 40; at 700 FCFA demand 30, supply 60 (illustrative).",
      [pn("What is the equilibrium price (FCFA)?", 600, "At 600 FCFA demand = supply = 40.", 0, "FCFA", 1),
       pn("What is the shortage at 400 FCFA?", 60 - 20, "60 − 20 = 40.", 0, "kg", 1),
       po("What happens to the price if it is fixed at 700 FCFA? Explain.", "At 700 FCFA supply (60) is more than demand (30): a surplus of 30 kg, so some garri stays unsold and sellers will want to cut the price.", ["Surplus (1)", "Price pressure (1)"], 2)]),
    tag(TAG, sc), (fig, "A market table: balance at 300 FCFA, shortages below it and surpluses above it.", "A table with five prices and columns for demand, supply and result."),
    notes=["Numbers are invented.", NOTE_ORDER])

# ======================================================================= CH 4 FIRMS
c4 = p.chapter("firms", "Kinds of business", programRef="GCE O Level Economics — business organisations (to be checked)")
share = 240000 / 3; assert share == 80000
fig = tabfig(["Business", "Owner(s)", "Example"], [["Sole trader", "one person", "market stall"], ["Partnership", "a few people", "small clinic"], ["Company", "shareholders", "brewery"], ["Cooperative", "members", "cocoa society"], ["Public enterprise", "the state", "water supplier"]], w=440, colw=[140, 130, 170], size=12, title="Kinds of business")
sc = [
    ("A sole trader is a business owned by", "one person", ["the state", "all the workers", "many shareholders"], "One owner takes all the profit and the risk."),
    ("In a partnership profits are", "shared among the partners", ["kept by the government", "paid to customers", "never shared"], "According to an agreement."),
    ("Shareholders are owners of", "a company", ["a market stall", "a government ministry", "a village"], "They hold shares in the company."),
    ("A cooperative society is formed by", "members who work together for their common benefit", ["one trader", "the central bank", "foreign governments"], "Farmers' cooperatives sell crops together."),
    ("A public enterprise is owned by", "the state", ["a single trader", "a family", "shareholders only"], "Water or electricity supply is often public."),
]
lesson(c4, "business-kinds", "Sole traders, partnerships, companies and cooperatives",
    ["Describe the main kinds of business.", "Give an advantage and a disadvantage of each.", "Share profits in a partnership."],
    [("definition", "Private businesses", "A **sole trader** is owned and run by one person, who keeps all the profit but also bears all the losses and **unlimited liability** (must pay all debts from personal property). A **partnership** has two or more owners who share work, capital and profits. A **company** is owned by **shareholders**, who each own part of it and share in profits (**dividends**); in a **limited** company they cannot lose more than they invested."),
     ("retenir", "Cooperatives and public enterprises", "A **cooperative** is a group of people (for example cocoa or coffee farmers) who join to buy supplies, process or sell their products and share the benefit. Each member usually has **one vote**. A **public enterprise** is owned by the **state** to provide goods or services, such as water or transport, to the public. Public enterprises may aim to serve the people and not only to earn a profit."),
     ("retenir", "Advantages and disadvantages", "**Sole trader**: easy to start, quick decisions, keeps profit; but limited capital and unlimited liability. **Partnership**: more capital and skills; but disagreements, shared profit. **Company**: can raise large capital and owners have limited liability; but more rules and costs. **Cooperative**: members help each other; but slow decisions."),
     ("attention", "Do not confuse", "A **public company** (shares sold to the public, owned by shareholders) is not the same as a **public enterprise** (owned by the state). Limited liability protects the owner's personal property, not the company's money. In a partnership, partners usually agree on how to share profit; without an agreement the law may decide.")],
    [("Worked example — sharing profits", "Three friends run a hair salon in Yaoundé as partners and share profits equally. This year's profit is 240 000 FCFA (illustrative). How much does each partner receive?", ["Number of partners = 3.", "Share = 240 000 ÷ 3.", "= %d FCFA each." % share], "80 000 FCFA each"),
     ("Worked example — choosing a form", "A single farmer wants to sell cocoa at better prices. Suggest a form of business that can help and say why.", ["Many farmers together can negotiate better and buy supplies cheaply.", "They can form a cooperative society, owned by the members.", "Members share the extra income."], "A cooperative")],
    [M("A business owned by many shareholders is a", "company", ["sole trader", "market stall", "public enterprise only"], "Shareholders own a company."),
     TF("A sole trader has unlimited liability.", True, "The owner is personally responsible for all debts."),
     N("Two partners share profits equally. Profit is 150 000 FCFA (illustrative). How much does each receive?", 75000, "150 000 ÷ 2 = 75 000.", unit="FCFA")],
    [O("Give two advantages of forming a partnership instead of staying a sole trader.", "More capital and skills can be brought in and the work and risk are shared among the partners.", ["First advantage (1)", "Second advantage (1)", "Link to sole trader weakness (1)"]),
     M("A water supply owned and run by the state is an example of", "a public enterprise", ["a sole trader", "a partnership", "a cooperative of farmers"], "The state owns it.", tier="approfondissement")],
    P("Bih, Che and Dang start a bakery as partners and put in 300 000, 200 000 and 100 000 FCFA. They share profits in the same ratio as their capital. Profit is 120 000 FCFA (illustrative).",
      [pn("Total capital (FCFA)?", 600000, "300 000 + 200 000 + 100 000 = 600 000.", 0, "FCFA", 1),
       pn("Bih's share of profit (FCFA)?", 120000 * 300 / 600, "300 ÷ 600 × 120 000 = 60 000.", 0, "FCFA", 1),
       po("Name one problem partners may face.", "They may disagree about decisions or how to share profit.", ["Valid problem (1)"], 1)]),
    tag(TAG, sc), (fig, "Five kinds of business with their owners and an example of each.", "A table with five rows: sole trader, partnership, company, cooperative and public enterprise."),
    notes=["Numbers are invented. The legal details of company forms in Cameroon are not covered.", NOTE_ORDER])

# ======================================================================= CH 5 MONEY AND BANKS
c5 = p.chapter("money", "Money, banks and saving", programRef="GCE O Level Economics — money and banking (to be checked)")
fig = hflow(["Farmer sells\nmaize", "receives\nmoney", "buys shoes\nand salt"], w=440, size=12, title="Exchange using money")
sc = [
    ("Barter is the exchange of", "goods for goods without using money", ["money for money", "goods for taxes", "labour for loans"], "No money is involved."),
    ("The main use of money is as a", "medium of exchange", ["source of land", "type of food", "factor of production"], "People accept money in payment."),
    ("A problem with barter is that", "each person must want what the other offers", ["goods are too cheap", "everything has a price", "taxes are too high"], "A double coincidence of wants is needed."),
    ("Money used to compare prices acts as", "a unit of account", ["a store of goods", "a factor of production", "a free good"], "Prices are stated in money."),
    ("Which is a form of money in Cameroon today?", "FCFA notes and coins", ["Land titles", "Oranges", "A sack of cement"], "The FCFA is the legal currency; mobile money balances are also used."),
]
lesson(c5, "money", "Barter and money",
    ["Explain barter and its problems.", "State the main functions and qualities of money.", "Do simple money calculations."],
    [("definition", "Barter and money", "**Barter** is the direct exchange of one good for another. It works badly: you must find a person who has what you want and wants what you have (**double coincidence of wants**), goods may not divide into small parts, and it is hard to compare values. **Money** is anything generally accepted in payment. It makes exchange simple."),
     ("retenir", "Functions of money", "Money is a **medium of exchange** (we use it to buy and sell), a **unit of account** (prices are stated in money), a **store of value** (we can keep it to spend later) and a **standard for deferred payment** (we can borrow now and repay later). The money used in Cameroon is the **FCFA**."),
     ("retenir", "Qualities of good money", "Good money is **accepted** by everyone, **durable**, **easy to carry** (portable), **divisible** into smaller units (coins and notes), **scarce** enough to have value and **uniform** (all units alike). Today money includes coins, notes, bank accounts and mobile-money balances on phones."),
     ("pieges", "Do not confuse", "Money is not the same as wealth: land, houses, farms and skills are wealth. Having more notes printed does not make a country richer. A store of value can lose its worth if prices rise fast. Cows or cocoa used as money long ago were poor money because they could not be divided or could spoil.")],
    [("Worked example — barter values", "In a barter market 1 goat is worth 8 chickens and 1 chicken is worth 3 baskets of maize (illustrative). How many baskets of maize is a goat worth?", ["1 goat = 8 chickens.", "1 chicken = 3 baskets of maize.", "1 goat = 8 × 3 = %d baskets of maize." % (8 * 3)], "24 baskets"),
     ("Worked example — change", "A student buys a notebook for 600 FCFA and a pen for 250 FCFA and pays with a 1 000 FCFA note. How much change should she get?", ["Total cost = 600 + 250 = %d FCFA." % 850, "Change = 1 000 − 850.", "= %d FCFA." % 150], "150 FCFA")],
    [M("Which is a function of money?", "A medium of exchange", ["A factor of production", "A kind of tax", "A type of land"], "People accept it in payment."),
     TF("Barter needs a double coincidence of wants.", True, "Both traders must want what the other has."),
     N("Mina buys rice for 2 300 FCFA and oil for 1 200 FCFA (illustrative). How much does she spend in total?", 3500, "2 300 + 1 200 = 3 500.", unit="FCFA")],
    [O("Describe two problems of barter and how money solves them.", "Barter needs a double coincidence of wants: money lets sellers accept money and buy what they want later. It is hard to divide goods like a goat: money can be divided into small units.", ["Two problems (2)", "Money's solutions (1)"]),
     M("Why is a goat not good as money?", "It cannot be divided into small amounts", ["It is easy to carry", "It is accepted everywhere", "It is uniform"], "Poor divisibility.", tier="approfondissement")],
    P("In a village 1 sack of maize is worth 5 bunches of plantain and 1 bunch of plantain is worth 2 fish (illustrative).",
      [pn("How many fish is a sack of maize worth?", 5 * 2, "5 × 2 = 10.", 0, "fish", 1),
       pm("The difficulty of this system is", "finding people whose wants match", ["too much money", "too many banks", "inflation"], "Barter needs a double coincidence of wants.", 1),
       po("How would money make it simpler?", "Each good would have a price in FCFA and could be bought or sold without finding a matching partner.", ["Valid explanation (1)"], 1)]),
    tag(TAG, sc), (fig, "A farmer exchanges maize for money, then uses the money to buy other goods.", "Three boxes joined by arrows: sells maize, receives money, buys shoes and salt."),
    notes=["Numbers are invented. Mobile money is mentioned as a modern form of money.", NOTE_ORDER])

mon = 4500; tot12 = mon * 12; intr = tot12 * 4 / 100; assert (tot12, intr) == (54000, 2160)
fig = bars([("3 months", mon * 3 / 1000, "blue"), ("6 months", mon * 6 / 1000, "blue"), ("9 months", mon * 9 / 1000, "blue"), ("12 months", mon * 12 / 1000, "blue")], unit="savings (thousand FCFA), 4 500 FCFA a month", w=400, h=250)
sc = [
    ("Interest is", "the reward paid for saving or the price paid for borrowing", ["a type of tax", "money printed by banks", "the cost of rent"], "Savers receive interest; borrowers pay it."),
    ("A savings account at a bank is useful because it", "keeps money safe and may earn interest", ["makes the money disappear", "gives free goods", "removes all prices"], "Banks protect savings."),
    ("The central bank for the CEMAC countries is the", "BEAC", ["a village bank", "a market", "a school"], "It issues the FCFA for the six countries."),
    ("In a njangi each member", "pays a fixed amount and takes the whole pot in turn", ["pays taxes to the bank", "gets a loan from the BEAC", "buys shares in a factory"], "Members save together and take turns."),
    ("A microfinance institution gives", "small loans and savings services", ["only large loans to firms", "only cheques for exports", "free goods"], "It serves people who need small amounts."),
]
lesson(c5, "banks-saving", "Saving, banks, njangi and microfinance",
    ["Explain why people save and what banks do.", "Describe njangi and microfinance institutions.", "Calculate savings and simple interest."],
    [("definition", "Saving and banks", "**Saving** means not spending part of your income now so that you can use it later (for school fees, a business or emergencies). A **bank** keeps savings safe, lends money to borrowers and moves money between people. Banks pay **interest** to savers and charge a higher interest to borrowers; the difference helps pay their costs. Banks also give cheques, cards and transfers."),
     ("retenir", "Simple interest", "**Simple interest = principal × rate ÷ 100 × time (years)**. Example: 100 000 FCFA at 5 % for 2 years gives 100 000 × 5 ÷ 100 × 2 = 10 000 FCFA. The **principal** is the money saved or borrowed; the **rate** is a percentage per year."),
     ("retenir", "Njangi and microfinance", "In a **njangi** (or **tontine**), a group meets regularly; each member pays a fixed amount and each meeting one member takes the whole **pot**. It is based on trust and usually has no interest. **Microfinance institutions** and **credit unions** give small savings accounts and small loans to people and small traders who may not use big banks. They may charge higher interest."),
     ("attention", "The central bank", "The **BEAC** is the central bank of the six CEMAC countries, including Cameroon. It issues the FCFA notes and coins and works with commercial banks. Ordinary people use commercial banks and microfinance institutions, not the BEAC directly. Always keep your money in a safe, trusted place and be careful with loans that have high interest.")],
    [("Worked example — saving each month", "Anne saves 4 500 FCFA every month for 12 months (illustrative). How much has she saved? If a bank adds 4 % simple interest on that sum at the end, how much interest is added?", ["Total saved = 4 500 × 12 = %d FCFA." % tot12, "Interest = 54 000 × 4 ÷ 100.", "= %d FCFA." % intr], "54 000 FCFA saved; 2 160 FCFA interest"),
     ("Worked example — njangi pot", "Twelve people are in a njangi and each pays 5 000 FCFA at every meeting (illustrative). How much is the pot? How much does each person pay in the whole round?", ["Pot = 12 × 5 000 = %d FCFA." % (12 * 5000), "In 12 meetings each person pays 12 × 5 000 = %d FCFA." % (12 * 5000), "Each person also receives the pot once, 60 000 FCFA, so there is no interest."], "Pot 60 000 FCFA; each pays 60 000 FCFA")],
    [M("A njangi pot is", "the total collected at one meeting", ["a loan from the BEAC", "a tax", "a bank statement"], "It goes to one member in turn."),
     TF("The BEAC is the central bank of the CEMAC states.", True, "It issues the FCFA for the zone."),
     N("Find the simple interest on 80 000 FCFA at 5 % for 2 years (illustrative).", 80000 * 5 / 100 * 2, "80 000 × 5 ÷ 100 × 2 = 8 000.", unit="FCFA")],
    [O("Give two reasons why people save money in a bank rather than at home.", "The money is safer from theft and fire, and it can earn interest; it is easier to send or receive payments.", ["First reason (1)", "Second reason (1)", "Link to risk or interest (1)"]),
     M("Which is a risk of a njangi?", "A member may fail to pay after taking the pot", ["Too much interest", "The BEAC may close it", "It prints extra money"], "It depends on trust.", tier="approfondissement")],
    P("A group of 8 market traders in Bafoussam run a njangi with a contribution of 10 000 FCFA at each meeting (illustrative).",
      [pn("Pot at each meeting (FCFA)?", 80000, "8 × 10 000 = 80 000.", 0, "FCFA", 1),
       pn("Number of meetings before everyone has received the pot?", 8, "Each meeting one member takes the pot, so 8 meetings.", 0, "meetings", 1),
       po("Give one reason a trader may prefer a njangi to a loan.", "It has no interest, is flexible and does not require formal papers.", ["Valid reason (1)"], 1)]),
    tag(TAG, sc), (fig, "Savings of 4 500 FCFA a month grow over a year (thousand FCFA).", "Four bars for 3, 6, 9 and 12 months of saving, increasing in height."),
    notes=["Numbers are invented. Details on microfinance rules are omitted.", "BEAC and CEMAC are described in outline only.", NOTE_ORDER])

# ======================================================================= CH 6 HOUSEHOLDS AND POPULATION
c6 = p.chapter("households", "Income, spending and population", programRef="GCE O Level Economics — income, consumption and population (to be checked)")
inc = 150000; items = {"Food": 62000, "Rent": 36000, "Transport": 14000, "School": 22000, "Saving": 16000}; assert sum(items.values()) == inc
pct = 16000 / inc * 100
fig = bars([(k, v / 1000, c) for (k, v), c in zip(items.items(), ["orange", "blue", "green", "purple", "red"])], unit="monthly budget, thousand FCFA", w=420, h=250)
sc = [
    ("Income is the money a person receives", "from work, property or other sources over a period", ["only from the bank", "only from gifts", "only when spending"], "Wages, rent, interest, profit and transfers are income."),
    ("Consumption means", "the use of goods and services to satisfy wants", ["the production of machines", "saving at the bank", "paying taxes only"], "Spending on goods is consumption."),
    ("Saving is the part of income that is", "not spent", ["spent on food", "spent on rent", "paid as tax only"], "Income = consumption + saving (if no taxes)."),
    ("A household budget is", "a plan for income and spending", ["a bank loan", "a tax return", "a market price list"], "It shows how a family will use its income."),
    ("If a family's income rises, it will usually", "spend more and save more", ["spend nothing", "stop saving", "lose its wants"], "Both consumption and saving tend to rise."),
]
lesson(c6, "income-spending", "Income, consumption and the household budget",
    ["Define income, consumption and saving.", "Draw up and read a household budget.", "Calculate shares of income."],
    [("definition", "Income and consumption", "**Income** is what people receive: **wages** for work, **rent**, **interest**, **profit** and **transfers** such as a gift or a pension. **Consumption** is spending on goods and services to satisfy wants. **Saving** is the part of income not spent. So **income = consumption + saving** (ignoring tax). Families with higher incomes usually spend more in total and also save more."),
     ("retenir", "The household budget", "A **budget** is a plan listing expected income and planned spending. A good family budget puts needs first (**food, rent, school fees, transport, health**), then wants, and keeps something for **savings** and emergencies. If spending is bigger than income, the family has a **deficit** and must borrow or use savings."),
     ("retenir", "Shares of income", "To find the share of a budget item: **item ÷ income × 100**. A family that spends 62 000 FCFA on food out of an income of 150 000 FCFA spends about 41 % on food. Poor families usually spend a **larger share** of income on food than richer families."),
     ("pieges", "Be careful", "Do not forget the **saving** line when checking that a budget adds up to the income. A budget is a **plan**; the actual spending may be different. Do not spend money you have not yet received. Borrowing to buy wants can become a problem if repaying is hard.")],
    [("Worked example — checking the budget", "A family earns 150 000 FCFA a month. It plans: food 62 000, rent 36 000, transport 14 000, school 22 000 and saving 16 000 (illustrative). Does the budget balance? What share is saved?", ["Total = 62 000 + 36 000 + 14 000 + 22 000 + 16 000 = %d FCFA." % sum(items.values()), "This equals the income, so the budget balances.", "Share saved = 16 000 ÷ 150 000 × 100 = %.1f %%." % pct], "It balances; about 10.7 % is saved"),
     ("Worked example — share on food", "Using the same budget, what percentage of income is spent on food? (Give the answer to one decimal place.)", ["Food = 62 000 FCFA.", "Share = 62 000 ÷ 150 000 × 100.", "= %.1f %%." % (62000 / inc * 100)], "About 41.3 %")],
    [M("Which of these is income?", "Wages earned by a driver", ["Rent paid by a tenant", "A bus fare", "A school fee"], "Wages are income; the others are spending."),
     TF("A household that spends more than its income is in deficit.", True, "It must borrow or use savings."),
     N("A family has an income of 120 000 FCFA and spends 105 000 FCFA (illustrative). How much does it save?", 15000, "120 000 − 105 000 = 15 000.", unit="FCFA")],
    [O("List four items that should come first in a family's budget and explain why.", "Food, rent or housing, school fees and health care or transport: they are needs, so they must be met before wants.", ["Four items (2)", "Reason (1)"]),
     N("A family earns 200 000 FCFA and spends 56 000 FCFA on food. What percentage of income is spent on food?", 56000 / 200000 * 100, "56 000 ÷ 200 000 × 100 = 28 %.", unit="%", tier="approfondissement")],
    P("A teacher in Bamenda earns 180 000 FCFA a month and spends: food 60 000, rent 45 000, transport 18 000, school 24 000, other 20 000 (illustrative).",
      [pn("Total spending (FCFA)?", 60000 + 45000 + 18000 + 24000 + 20000, "60 000 + 45 000 + 18 000 + 24 000 + 20 000 = 167 000.", 0, "FCFA", 1),
       pn("Saving (FCFA)?", 180000 - 167000, "180 000 − 167 000 = 13 000.", 0, "FCFA", 1),
       po("Suggest one way to increase saving.", "Reduce spending on wants, find extra income or cut transport costs.", ["Valid way (1)"], 1)]),
    tag(TAG, sc), (fig, "A household budget: food, rent, transport, school and saving (thousand FCFA).", "Five bars of different heights for food, rent, transport, school and saving."),
    notes=["Numbers are invented.", NOTE_ORDER])

dens = 120000 / 400; assert dens == 300
fig = bars([("Town A", 38, "blue"), ("Town B", 56, "blue"), ("Town C", 21, "blue"), ("Town D", 47, "blue")], unit="imaginary towns, population in thousands", w=400, h=250)
sc = [
    ("The population of a country is", "the number of people living in it", ["the number of houses", "the amount of money it has", "its area in square kilometres"], "Population is a count of people."),
    ("A birth rate is the number of", "live births per 1 000 people in a year", ["people who move away", "babies a woman has", "marriages in a year"], "It is stated per thousand people."),
    ("Population density is", "the average number of people per square kilometre", ["the total area of a country", "the number of cities", "the number of births"], "Density = population ÷ area."),
    ("Migration from villages to towns is called", "rural-urban migration", ["emigration only", "natural increase", "birth control"], "People move to find jobs and services."),
    ("A country with a large share of children needs more", "schools and health care for children", ["old people's homes only", "airports only", "prisons only"], "Young populations need education services."),
]
lesson(c6, "population", "Population",
    ["Define population, birth rate, death rate and migration.", "Calculate natural increase and population density.", "Describe simple effects of population growth."],
    [("definition", "Population and its change", "The **population** is the number of people living in an area. It changes because of **births**, **deaths** and **migration** (people moving in or out of the area). The **birth rate** and **death rate** are the number of births and deaths for every 1 000 people in a year. **Natural increase = birth rate − death rate**. **Immigrants** move into a country; **emigrants** move out."),
     ("retenir", "Density and distribution", "**Population density = population ÷ area** (people per square kilometre). People do not live evenly: more people live near ports, in cities, in fertile areas and where there are jobs. **Rural-urban migration** moves people from farms to towns. This can crowd cities and leave villages with fewer workers, but it may also bring skills and money back to villages."),
     ("retenir", "Effects of growth", "A fast-growing population means **more workers in the future** and bigger markets, but also more need for **food, schools, health care, housing and jobs**. If output grows more slowly than population, **income per head falls**. A population with many children and few workers has a high **dependency**."),
     ("pieges", "Be careful", "A large population is not the same as a high density: density depends on the **area**. A country can be crowded and poor or crowded and rich. Do not quote real population figures for Cameroon unless you are sure; use the numbers in the question. Remember that rates are **per 1 000**, not per 100.")],
    [("Worked example — density", "A district has 120 000 people living in an area of 400 square kilometres (illustrative). Find its density.", ["Density = population ÷ area.", "= 120 000 ÷ 400.", "= %d people per square kilometre." % dens], "300 people per km²"),
     ("Worked example — natural increase", "In a town, the birth rate is 32 per 1 000 and the death rate is 14 per 1 000 (illustrative). Find the natural increase per 1 000 and the town's population growth in a year if the town has 50 000 people and there is no migration.", ["Natural increase = 32 − 14 = %d per 1 000." % (32 - 14), "For 50 000 people: 50 000 ÷ 1 000 × 18.", "= %d more people in the year." % (50000 / 1000 * 18)], "18 per 1 000; 900 people")],
    [M("Which of these changes population?", "Births, deaths and migration", ["Taxes only", "Interest rates only", "Prices only"], "These are the three causes."),
     TF("Rural-urban migration is the movement of people from towns to villages.", False, "It is the movement from the countryside to towns."),
     N("A town of 30 000 people covers 60 square kilometres (illustrative). Find the density in people per km².", 500, "30 000 ÷ 60 = 500.", unit="per km²")],
    [O("Explain two problems that rapid population growth may cause for a town.", "Crowded housing and pressure on water and health services; need for more schools and jobs.", ["First problem (1)", "Second problem (1)", "Explanation (1)"]),
     N("The birth rate is 40 per 1 000 and the death rate 15 per 1 000 (imaginary). What is the natural increase per 1 000?", 25, "40 − 15 = 25.", unit="per 1 000", tier="approfondissement")],
    P("Village X has 6 000 people on 20 square kilometres; Town Y has 45 000 people on 90 square kilometres (illustrative).",
      [pn("Density of village X (per km²)?", 300, "6 000 ÷ 20 = 300.", 0, "per km²", 1),
       pn("Density of town Y (per km²)?", 500, "45 000 ÷ 90 = 500.", 0, "per km²", 1),
       po("Which place is more crowded and why?", "Town Y, because it has more people per square kilometre (500 compared with 300).", ["Valid comparison (1)"], 1)]),
    tag(TAG, sc), (fig, "Populations of four imaginary towns, in thousands.", "Four bars of different heights labelled Town A to Town D."),
    notes=["All figures are imaginary; no real statistics are given.", NOTE_ORDER])

# ======================================================================= CH 7 GOVERNMENT
c7 = p.chapter("government", "The government and taxes", programRef="GCE O Level Economics — government and taxation (to be checked)")
vat = 5000 * 10 / 100; assert vat == 500
fig = hflow(["Taxpayers\n(people, firms)", "Government\n(budget)", "Public services\n(schools, roads)"], w=460, size=12, title="From taxes to services")
sc = [
    ("A tax is", "a compulsory payment to the government", ["a gift to a friend", "a bank loan", "a market price"], "People must pay taxes by law."),
    ("An example of a direct tax is", "income tax", ["VAT", "customs duty", "a bus fare"], "Direct taxes are paid directly from income or profit."),
    ("An example of an indirect tax is", "value added tax (VAT)", ["income tax", "company tax", "a school fee"], "It is added to the price of goods."),
    ("Which is a public service paid for by taxes?", "Public roads", ["A private bakery", "A taxi fare", "A shop's goods"], "Government provides roads, schools and many other services."),
    ("A government budget shows", "planned revenue and spending", ["the market prices of goods", "a family's savings", "the number of births"], "Revenue and expenditure are planned for the year."),
]
lesson(c7, "taxes", "Why the government collects taxes",
    ["Say what taxes are and why governments collect them.", "Tell direct from indirect taxes.", "Calculate a simple sales tax."],
    [("definition", "What is a tax?", "A **tax** is a compulsory payment to the government. The government uses the money to provide **public services** such as roads, schools, hospitals, security and street lighting, which people could not easily buy one by one. The government also uses taxes to reduce differences in income, to protect local industry and to discourage harmful goods."),
     ("retenir", "Direct and indirect taxes", "**Direct taxes** are paid directly by the person or firm on whom they fall, such as **income tax** and **company tax**. **Indirect taxes** are added to the price of goods and services, such as **VAT** (value added tax) and **customs duties** on imports; the shop collects them and passes them on to the government. Indirect taxes are paid by the buyer."),
     ("retenir", "The government budget", "The **budget** is the government's plan for the year: expected **revenue** (mostly taxes) and planned **spending** (schools, health, roads, wages of public workers). If spending is more than revenue, there is a **deficit**, which can be met by borrowing. Good tax systems are **fair**, **clear** and **cheap to collect**."),
     ("attention", "Taxes and services", "Paying taxes is a duty of citizens and firms. Tax evasion (hiding income to avoid tax) is illegal and reduces money for public services. Do not confuse a **fee** (paid for a specific service) with a **tax**. Tax rates in this lesson are invented for practice; real rates are set by law and may change.")],
    [("Worked example — sales tax", "A phone costs 5 000 FCFA before tax, and the government adds a sales tax of 10 % (illustrative rate). Find the tax and the price the buyer pays.", ["Tax = 5 000 × 10 ÷ 100 = %d FCFA." % vat, "Price = 5 000 + 500.", "= %d FCFA." % (5000 + vat)], "Tax 500 FCFA; price 5 500 FCFA"),
     ("Worked example — classify taxes", "Classify: (a) tax taken from a worker's wage, (b) tax added to the price of rice, (c) tax on a company's profit.", ["(a) Paid directly from income: direct.", "(b) Added to the price: indirect.", "(c) Paid directly on profit: direct."], "(a) direct (b) indirect (c) direct")],
    [M("Which of these is a direct tax?", "Income tax", ["VAT", "Customs duty on cars", "A tax in the price of fuel"], "Income tax is paid directly."),
     TF("Taxes help pay for public services such as schools and roads.", True, "That is a main use of tax revenue."),
     N("A bag of rice costs 20 000 FCFA before a 5 % sales tax (illustrative). How much is the tax?", 1000, "20 000 × 5 ÷ 100 = 1 000.", unit="FCFA")],
    [O("Explain two reasons why a government collects taxes.", "To raise money for schools, roads, health care and security; to reduce income differences or discourage harmful goods like tobacco.", ["First reason (1)", "Second reason (1)", "Example (1)"]),
     M("A government spends more than it collects. This is called", "a budget deficit", ["a budget surplus", "a balanced budget", "a tax holiday"], "Spending exceeds revenue.", tier="approfondissement")],
    P("A shop in Douala sells a radio for 12 000 FCFA before a 10 % sales tax (illustrative rate). The government plans revenue of 40 billion FCFA and spending of 46 billion FCFA.",
      [pn("The tax on the radio (FCFA)?", 1200, "12 000 × 10 ÷ 100 = 1 200.", 0, "FCFA", 1),
       pn("The price paid by the buyer (FCFA)?", 13200, "12 000 + 1 200 = 13 200.", 0, "FCFA", 1),
       pn("The government's deficit (billion FCFA)?", 6, "46 − 40 = 6.", 0, None, 1),
       po("Give one way the government could finance this deficit.", "By borrowing at home or abroad, or later raising taxes or reducing spending.", ["Valid way (1)"], 1)]),
    tag(TAG, sc), (fig, "Taxes paid by people and firms go to the government, which pays for public services.", "Three boxes joined by arrows: taxpayers, government and public services."),
    notes=["The 5 % and 10 % rates are invented; real tax rates are not stated.", NOTE_ORDER])

write_compact(p)
