"""GCE O Level Economics - firms, costs, market structures, money and banking."""
from eco_lib import *
TAG = "O Level — "

def build(p):
    # ================================================================ CH 3 PRODUCTION AND FIRMS
    c3 = p.chapter("firms", "Production, firms and costs", programRef="GCE O Level Economics — production, business organisation, costs and revenue (to be checked)")

    # ---- 6 production
    chain = [200000, 350000, 600000]
    va = [chain[0], chain[1] - chain[0], chain[2] - chain[1]]; assert va == [200000, 150000, 250000] and sum(va) == chain[2]
    fig = vflow(["Primary: farmer grows cocoa", "Secondary: factory makes chocolate", "Tertiary: shop sells to buyers"], w=420, bw=330, notes=None, title="A chain of production")
    sc = [
        ("Farming, fishing and mining belong to the", "primary sector", ["secondary sector", "tertiary sector", "public sector"], "They take products directly from nature."),
        ("A bakery making bread from flour is part of the", "secondary sector", ["primary sector", "tertiary sector", "informal sector only"], "Manufacturing turns raw materials into finished goods."),
        ("Teaching, banking and transport belong to the", "tertiary sector", ["primary sector", "secondary sector", "extractive sector"], "Services are in the tertiary sector."),
        ("Subsistence production means producing", "mainly for the producer's own family use", ["only for export", "only for the government", "only luxury goods"], "Little or nothing is sold."),
        ("Value added at a stage of production equals", "value of output minus value of inputs bought in", ["total profit of the firm", "price of the raw materials", "wages only"], "It is the extra value created at that stage."),
    ]
    lesson(c3, "production", "Types and stages of production",
        ["Define production and the primary, secondary and tertiary sectors.", "Distinguish subsistence from commercial production.", "Calculate value added along a chain of production."],
        [("definition", "Production and its sectors", "**Production** is the creation of goods and services that satisfy wants. **Primary production** takes resources from nature (farming, fishing, forestry, mining, oil drilling). **Secondary production** manufactures and builds (processing cocoa, brewing, construction). **Tertiary production** provides services (trade, transport, banking, teaching, health). Together they make up the economy."),
         ("retenir", "Direct, indirect, subsistence and commercial", "**Direct (subsistence) production** is for the producer's own use; **indirect (commercial) production** is for sale in the market, so it needs exchange and money. **Consumer goods** satisfy wants directly; **capital goods** (machines, trucks) help to produce other goods. **Large-scale** production uses more capital and labour, which can lower the cost per unit."),
         ("retenir", "Chain of production and value added", "Most goods pass through **stages**: raw material, processing, distribution and sale. **Value added** at each stage = value of output − cost of inputs bought in. The sum of all value added equals the value of the final product. It helps avoid **double counting** when national output is measured."),
         ("pieges", "Classifying correctly", "A product can belong to different sectors at different stages: a farmer is primary, the trader who carries the cocoa is tertiary and the chocolate factory is secondary. Do not mix up **services** with **goods**: a haircut is a service because nothing physical is left. Do not call value added the **profit**: wages, rent and interest are paid out of it.")],
        [("Worked example — value added", "A farmer in the Centre region sells cocoa for 200 000 FCFA to a factory. The factory makes paste and sells it for 350 000 FCFA to a chocolate maker, who sells chocolate for 600 000 FCFA to shops (illustrative). Find the value added at each stage.", ["Farmer: 200 000 (no bought inputs assumed).", "Factory: 350 000 − 200 000 = %d FCFA." % va[1], "Chocolate maker: 600 000 − 350 000 = %d FCFA." % va[2]], "200 000; 150 000; 250 000 FCFA (total 600 000)"),
         ("Worked example — classify", "Classify these activities: a fisherman in Limbe, a shoe factory in Douala and a taxi driver in Yaoundé. Which sector is each?", ["The fisherman takes fish from the sea: primary.", "The shoe factory manufactures goods: secondary.", "The taxi driver sells a transport service: tertiary."], "Primary, secondary, tertiary")],
        [M("Which of the following is an example of tertiary production?", "A bank providing loans", ["A cocoa farm", "A sawmill making planks", "A fish pond"], "Banking is a service."),
         TF("Subsistence farmers produce mainly to sell in the market.", False, "Subsistence production is mainly for own use."),
         N("A factory buys flour for 80 000 FCFA and sells the bread it makes for 130 000 FCFA (illustrative). What is the value added in FCFA?", 130000 - 80000, "130 000 − 80 000 = 50 000 FCFA.", unit="FCFA")],
        [O("Explain why value added rather than the selling price of every product is added up when measuring national output.", "If every sale were added, the cocoa would be counted again in the paste and again in the chocolate (double counting). Adding value added at each stage counts each part of output once and equals the value of the final product.", ["Double counting named (1)", "Value added concept (1)", "Equals final value (1)"]),
         M("A lorry that carries cocoa from Kumba to Douala is classified as", "tertiary production", ["primary production", "secondary production", "subsistence production"], "Transport is a service.", tier="approfondissement")],
        P("Wood is cut by a logger for 100 000 FCFA, sawn into planks sold at 180 000 FCFA, and made into furniture sold at 320 000 FCFA (illustrative).",
          [pn("Value added by the sawmill (FCFA)?", 180000 - 100000, "180 000 − 100 000 = 80 000.", 0, "FCFA", 1),
           pn("Value added by the furniture maker (FCFA)?", 320000 - 180000, "320 000 − 180 000 = 140 000.", 0, "FCFA", 1),
           pm("Which sector is the logger in?", "primary", ["secondary", "tertiary", "public"], "Forestry takes products from nature.", 1),
           po("Explain why the total value added equals the price of the furniture.", "Each stage adds only the extra value it creates: 100 000 + 80 000 + 140 000 = 320 000, the final price.", ["Sum shown (1)"], 1)]),
        tag(TAG, sc), (fig, "A chain of production: primary, secondary and tertiary stages.", "Three boxes joined by arrows from farming to manufacturing to selling."),
        notes=[NOTE_MONEY, "The chain values are invented teaching figures.", NOTE_SYL])

    # ---- 7 business organisations
    fig = tabfig(["", "Liability", "Owners", "Capital from"], [["Sole trader", "unlimited", "one", "own savings"], ["Partnership", "unlimited", "few", "partners"], ["Private co.", "limited", "few shareholders", "shares"], ["Public co.", "limited", "many shareholders", "public shares"], ["Cooperative", "limited", "members", "members' shares"]], w=450, colw=[100, 90, 130, 130], size=11, title="Forms of business")
    pa, pb = 3, 2; prof = 500000; sa = prof * pa / (pa + pb); sb = prof * pb / (pa + pb); assert (sa, sb) == (300000, 200000)
    div = 1200000 / 40000; assert div == 30
    sc = [
        ("A sole trader has", "unlimited liability", ["limited liability", "no owner", "many shareholders"], "The owner is personally responsible for all debts."),
        ("In a partnership the owners are called", "partners", ["shareholders", "members only", "directors only"], "Partners share profits and risks."),
        ("The liability of shareholders in a limited company is", "limited to the amount they invested", ["unlimited", "equal to the firm's profit", "zero tax"], "Their personal property is protected."),
        ("A cooperative society is owned by", "its members, who share the surplus", ["a single sole trader", "the government alone", "foreign shareholders only"], "Members often have one vote each."),
        ("A public corporation is a business that is", "owned and run by the state", ["owned by one person", "owned by a family", "owned by the church"], "Public enterprises provide goods and services under state ownership."),
    ]
    lesson(c3, "business-forms", "Forms of business organisation",
        ["Describe the sole trader, partnership, company, cooperative and public enterprise.", "Compare their advantages and disadvantages.", "Share profits and dividends in simple calculations."],
        [("definition", "Private-sector forms", "A **sole trader** owns and runs the business alone (a market stall, a tailor). The owner keeps all profit but has **unlimited liability** and limited capital. A **partnership** has several owners who share profits and decisions and have capital and skills but may quarrel; liability is normally unlimited. A **company** is a separate legal body owned by **shareholders** who have **limited liability**."),
         ("retenir", "Private and public companies", "A **private limited company** sells shares only to a few people (often a family or friends). A **public limited company** can sell shares to the general public, so it can raise much more capital; owners can sell shares more easily. Profit paid to shareholders is the **dividend**. Companies are controlled by directors; the business can continue even if an owner dies."),
         ("retenir", "Cooperatives and public enterprises", "A **cooperative** is formed by members (such as cocoa or coffee farmers) to buy inputs, process or market products together; each member usually has **one vote** and profits (surplus) are shared according to use. A **public enterprise** is owned by the state to provide important services or goods, aims to serve the public as well as to earn money, and may receive subsidies."),
         ("pieges", "Liability and law", "Limited liability protects owners' **personal** property; it does not stop the company from losing its money. Company rules in Cameroon follow a regional business law (the OHADA treaty), so names of company types may differ from those used in other countries. Do not say 'public company' when you mean a **public enterprise**: the first has private shareholders, the second is owned by the state.")],
        [("Worked example — sharing profits", "Ndi and Mbah run a partnership. Ndi puts in 3 parts of the capital and Mbah 2 parts, and profits are shared in the same ratio. The year's profit is 500 000 FCFA (illustrative). How much does each receive?", ["Total parts = 3 + 2 = 5.", "Ndi: 3 ÷ 5 × 500 000 = %d FCFA." % sa, "Mbah: 2 ÷ 5 × 500 000 = %d FCFA." % sb], "Ndi 300 000 FCFA; Mbah 200 000 FCFA"),
         ("Worked example — dividend per share", "A company has 40 000 shares and decides to pay out 1 200 000 FCFA as dividends (illustrative). What is the dividend per share? What does a shareholder with 150 shares receive?", ["Dividend per share = 1 200 000 ÷ 40 000 = %d FCFA." % div, "Shareholder: 150 × 30 = %d FCFA." % (150 * 30), "The shareholder cannot lose more than the amount paid for the shares."], "30 FCFA per share; 4 500 FCFA")],
        [M("Which form of business has unlimited liability?", "Sole trader", ["Public limited company", "Private limited company", "Shareholder in a company"], "The sole trader is personally liable."),
         TF("Shareholders in a limited company can lose their personal home if the company fails.", False, "Their loss is limited to the money they invested."),
         N("A company pays total dividends of 600 000 FCFA on 20 000 shares (illustrative). What is the dividend per share in FCFA?", 600000 / 20000, "600 000 ÷ 20 000 = 30.", unit="FCFA")],
        [O("Give two advantages and one disadvantage of operating as a sole trader.", "Advantages: the owner takes all decisions quickly and keeps all the profit. Disadvantage: the owner has unlimited liability and finds it difficult to raise large capital.", ["Two advantages (2)", "One disadvantage (1)"]),
         M("A group of cocoa farmers in the Centre region forms a society to sell their beans together. This is a", "cooperative", ["sole trader", "public corporation", "monopoly"], "Members jointly own and use the society.", tier="approfondissement")],
        P("Three friends in Bamenda start a bakery as a partnership with capital of 1 000 000 FCFA put in by A (400 000), B (350 000) and C (250 000). Profit of 800 000 FCFA is shared in proportion to capital (illustrative).",
          [pn("Find A's share of the profit (FCFA).", 800000 * 400 / 1000, "400 ÷ 1000 × 800 000 = 320 000.", 0, "FCFA", 1),
           pn("Find C's share of the profit (FCFA).", 800000 * 250 / 1000, "250 ÷ 1000 × 800 000 = 200 000.", 0, "FCFA", 1),
           po("Why might the partners decide to become a limited company later?", "To raise more capital by selling shares and to give the owners limited liability, so their personal property is protected.", ["Two valid reasons (2)"], 2)]),
        tag(TAG, sc), (fig, "Forms of business compared by liability, owners and source of capital.", "A table with five rows for sole trader, partnership, private company, public company and cooperative."),
        notes=[NOTE_MONEY, "Company law details (OHADA) are mentioned only in outline; verify the current forms and names before teaching them.", NOTE_SYL])

    # ---- 8 costs and revenue
    fc, vc_u, price = 60000, 100, 250
    be = fc / (price - vc_u); assert be == 400
    fig = plot(0, 8, 0, 24, curves=[("6+x", "red", "TC"), ("2.5*x", "green", "TR")], points=[(4, 10, "break-even", "blue")], xlabel="loaves (hundreds)", ylabel="FCFA (tens of thousands)", h=280)
    q = 700; tc = fc + vc_u * q; tr = price * q; pr = tr - tc; ac = tc / q
    assert (tc, tr, pr) == (130000, 175000, 45000) and round(ac, 2) == 185.71
    sc = [
        ("Fixed costs are costs that", "do not change with the level of output in the short run", ["rise with every unit made", "are paid only by the government", "are always zero"], "Rent and insurance are examples."),
        ("Total cost is equal to", "fixed cost plus variable cost", ["price times quantity", "revenue minus profit only", "fixed cost minus variable cost"], "TC = FC + VC."),
        ("Total revenue is calculated as", "price multiplied by quantity sold", ["price divided by quantity", "cost plus profit", "fixed cost times output"], "TR = P × Q."),
        ("Average cost is", "total cost divided by output", ["total revenue divided by cost", "fixed cost plus profit", "price minus cost"], "AC = TC ÷ Q."),
        ("A firm makes a profit when", "total revenue is greater than total cost", ["total cost is greater than revenue", "output is zero", "fixed costs are zero"], "Profit = TR − TC."),
    ]
    lesson(c3, "costs-revenue", "Costs, revenue and profit",
        ["Distinguish fixed and variable costs.", "Calculate total, average cost, revenue and profit.", "Find the break-even output."],
        [("definition", "Costs", "**Fixed costs (FC)** do not change when output changes in the short run (rent, insurance, manager's salary). **Variable costs (VC)** change with output (raw materials, casual labour, fuel). **Total cost: TC = FC + VC**. **Average cost: AC = TC ÷ Q**. As output rises, fixed cost is spread over more units, so average cost often falls at first."),
         ("formule", "Revenue and profit", "**Total revenue: TR = price × quantity sold**. **Profit = TR − TC**. A loss occurs when TC is bigger than TR. The **break-even point** is the output at which TR = TC, so profit is zero. With a constant price and constant cost per unit: **break-even output = FC ÷ (price − variable cost per unit)**.", "\\text{TR} = P \\times Q, \\quad \\text{Profit} = \\text{TR} - \\text{TC}"),
         ("pieges", "Frequent errors", "Do not add price to cost: **revenue** comes from selling, **cost** from producing. Fixed costs still exist when output is **zero**. Average cost is not the same as variable cost per unit. Remember to use the **same time period** for all figures (a month, a year). Break-even tells you the output needed to cover all costs, not the maximum profit."),
         ("retenir", "Why costs matter", "Firms compare TR and TC to decide how much to produce and whether to continue. Reducing costs (cheaper inputs, better technology, **economies of scale** from producing on a larger scale) raises profit. **Diseconomies** may appear if the firm becomes too large to manage. Cost data also help governments judge whether an industry can survive without help.")],
        [("Worked example — profit", "A bakery in Douala has fixed costs of 60 000 FCFA a month, variable cost of 100 FCFA per loaf and sells each loaf at 250 FCFA. It sells 700 loaves (illustrative). Find TC, TR, profit and average cost.", ["TC = 60 000 + 100 × 700 = %d FCFA; TR = 250 × 700 = %d FCFA." % (tc, tr), "Profit = %d − %d = %d FCFA." % (tr, tc, pr), "AC = %d ÷ 700 = about %.0f FCFA per loaf." % (tc, ac)], "TC 130 000; TR 175 000; profit 45 000; AC about 186 FCFA"),
         ("Worked example — break-even", "For the same bakery find the number of loaves that must be sold to break even.", ["Contribution per loaf = 250 − 100 = %d FCFA." % (price - vc_u), "Break-even output = 60 000 ÷ 150 = %d loaves." % be, "Check: TR = 250 × 400 = 100 000 and TC = 60 000 + 100 × 400 = 100 000."], "400 loaves")],
        [M("Which of the following is a variable cost for a tailor?", "Cloth bought for each order", ["Annual shop rent", "Insurance of the shop", "Manager's fixed salary"], "Cloth use depends on output."),
         TF("Fixed costs are still paid even when no output is produced in the short run.", True, "Rent and similar costs must be paid anyway."),
         N("A firm sells 50 sacks at 4 000 FCFA each (illustrative). What is its total revenue in FCFA?", 4000 * 50, "4 000 × 50 = 200 000.", unit="FCFA")],
        [O("Explain why average cost per loaf may fall when a bakery makes more loaves.", "Fixed costs such as rent are spread over more loaves, so fixed cost per loaf falls; bulk buying of flour and better use of the oven may also lower cost per unit.", ["Fixed costs spread (1)", "Per-unit effect (1)", "Another reason (1)"]),
         N("A workshop has fixed costs of 90 000 FCFA, variable cost 300 FCFA per chair-leg and sells each at 450 FCFA (illustrative). Find the break-even output.", 90000 / (450 - 300), "90 000 ÷ 150 = 600.", unit="units", tier="approfondissement")],
        P("A small printing business in Buea has fixed costs of 80 000 FCFA a month and variable cost of 200 FCFA per page-set. Each set sells for 400 FCFA. It sells 500 sets (illustrative).",
          [pn("Total cost (FCFA)?", 80000 + 200 * 500, "80 000 + 200 × 500 = 180 000.", 0, "FCFA", 1),
           pn("Total revenue (FCFA)?", 400 * 500, "400 × 500 = 200 000.", 0, "FCFA", 1),
           pn("Profit (FCFA)?", 400 * 500 - (80000 + 200 * 500), "200 000 − 180 000 = 20 000.", 0, "FCFA", 1),
           pn("Break-even number of sets?", 80000 / (400 - 200), "80 000 ÷ 200 = 400.", 0, "sets", 1)]),
        tag(TAG, sc), (fig, "A break-even chart: revenue (TR) crosses total cost (TC) at 400 loaves (4 hundreds), in tens of thousands of FCFA.", "A rising revenue line from the origin crossing a total cost line that starts above zero, with the crossing marked break-even."),
        notes=[NOTE_MONEY, "The chart uses units of hundreds of loaves and tens of thousands of FCFA.", NOTE_SYL])

    # ---- 9 market structures
    shares = [40, 30, 20, 10]; top2 = sum(shares[:2]); assert top2 == 70 and sum(shares) == 100
    fig = tabfig(["", "Sellers", "Product", "Entry"], [["Perfect comp.", "very many", "identical", "free"], ["Monopoly", "one", "no close substitute", "blocked"], ["Oligopoly", "few large", "similar or different", "difficult"], ["Monop. comp.", "many", "differentiated", "easy"]], w=450, colw=[110, 90, 150, 100], size=11, title="Market structures in outline")
    sc = [
        ("A monopoly is a market with", "one seller of a product with no close substitutes", ["many sellers of identical goods", "two buyers only", "a government price"], "A single firm controls supply."),
        ("A feature of perfect competition is", "many buyers and sellers who are price takers", ["a single seller", "high barriers to entry", "branded products only"], "No firm can influence the price."),
        ("An oligopoly is a market dominated by", "a few large firms", ["one government firm", "thousands of equal firms", "no firms"], "Firms depend on each other's decisions."),
        ("A barrier to entry is", "anything that makes it hard for new firms to enter a market", ["a tax on consumers", "a price cut", "a loan from a bank"], "Examples are high start-up costs and licences."),
        ("Hair salons and small restaurants in a city are often examples of", "monopolistic competition", ["pure monopoly", "perfect competition", "a command economy"], "Many sellers offer slightly different services."),
    ]
    lesson(c3, "market-structures", "Market structures in outline",
        ["Describe perfect competition, monopoly, oligopoly and monopolistic competition.", "Explain barriers to entry.", "Calculate a simple market-share concentration."],
        [("definition", "Four structures", "The **market structure** describes how many firms sell, how alike their products are and how easy it is to enter. **Perfect competition**: very many buyers and sellers, identical goods, free entry, so each seller is a **price taker**. **Monopoly**: one seller, no close substitute, entry blocked. **Oligopoly**: a few large firms that watch each other. **Monopolistic competition**: many sellers of slightly different goods."),
         ("retenir", "Barriers and effects", "**Barriers to entry** include large start-up costs, patents and licences, control of key inputs and strong brands. A firm with market power may charge higher prices and produce less than in competition; governments may **regulate** prices or break up monopolies. In oligopoly, firms often compete using **advertising and quality** rather than price cuts, or may agree secretly (a **cartel**), which is usually illegal."),
         ("pieges", "Pure types are rare", "Real markets are mixtures. A village food market is often described as 'close to' perfect competition, but goods differ in quality and location. A state electricity or water supplier is often a **natural monopoly** because one network is cheaper than several. Do not say that a monopoly **must** be bad: it may gain economies of scale and fund research, but may also abuse its power."),
         ("retenir", "Concentration", "A simple measure of market power is the **market share** of the largest firms: sales of a firm ÷ total sales × 100. The higher the share of the top few firms, the more **concentrated** (and closer to oligopoly or monopoly) the market is. Competition policy tries to protect consumers from abuse of market power.")],
        [("Worked example — market shares", "In an imaginary market for packaged water four firms have sales of 400, 300, 200 and 100 million FCFA. Find the market share of each and the share of the top two firms (illustrative).", ["Total sales = 400 + 300 + 200 + 100 = %d." % 1000, "Shares: 40 %, 30 %, 20 %, 10 %.", "Top two = 40 + 30 = %d %%: the market is concentrated, like an oligopoly." % top2], "40, 30, 20, 10 %; top two 70 %"),
         ("Worked example — identify the structure", "In a town in the West region 300 farmers sell identical maize at the same price, and anyone can start selling. Name the structure and give two reasons.", ["There are very many sellers of an identical product.", "No seller can change the price and entry is free.", "This is close to perfect competition."], "Perfect competition (approximately)")],
        [M("Which market structure has one seller only?", "Monopoly", ["Oligopoly", "Perfect competition", "Monopolistic competition"], "Mono means one."),
         TF("In perfect competition each firm can set its own price.", False, "Firms are price takers."),
         N("Firm A sells 250 million FCFA of the 1 000 million FCFA total in a market (illustrative). What is its market share in %?", 250 / 1000 * 100, "250 ÷ 1000 × 100 = 25 %.", unit="%")],
        [O("Explain why a firm with high start-up costs and a patented product might be a monopoly.", "Other firms cannot afford the start-up costs and cannot legally copy the product, so there are barriers to entry; the firm is the only seller and can control price and quantity.", ["Barriers named (1)", "Only seller (1)", "Market power result (1)"]),
         M("Which would be the best example of a barrier to entry?", "A licence that only one firm holds", ["Many customers", "A fall in price", "Free advertising on the radio"], "Legal licences stop others from entering.", tier="approfondissement")],
        P("In an imaginary city three phone-credit sellers have sales of 600, 300 and 100 million FCFA in a year (illustrative).",
          [pn("Find the market share of the largest seller (%).", 600 / 1000 * 100, "600 ÷ 1000 × 100 = 60.", 0, "%", 1),
           pn("Find the share of the two largest sellers (%).", 90, "(600 + 300) ÷ 1000 × 100 = 90.", 0, "%", 1),
           pm("This market is best described as", "an oligopoly", ["perfect competition", "a command economy", "a free good"], "Few large firms dominate.", 1),
           po("Give one way the government could protect consumers.", "It could regulate prices, set quality rules, or encourage new firms to enter the market.", ["Valid method (1)"], 1)]),
        tag(TAG, sc), (fig, "The four market structures compared by sellers, product and entry.", "A table with four rows: perfect competition, monopoly, oligopoly and monopolistic competition."),
        notes=["Market share example is invented. Real market structures of Cameroonian industries are not stated.", NOTE_SYL])

    # ================================================================ CH 4 MONEY AND BANKING
    c4 = p.chapter("money", "Money and banking", programRef="GCE O Level Economics — money, banks, central bank and financial institutions (to be checked)")

    # ---- 10 money
    n = 5; pb_ = n * (n - 1) // 2; assert pb_ == 10
    fig = hflow(["Farmer has\nmaize", "wants shoes", "Shoemaker\nwants fish", "No match:\nbarter fails"], w=460, size=11, title="The problem of barter")
    sc = [
        ("Barter is", "the direct exchange of goods for goods", ["exchange using money", "a tax", "a bank loan"], "No money is used."),
        ("A problem of barter is the", "need for a double coincidence of wants", ["use of coins", "existence of banks", "use of FCFA"], "Each person must want what the other offers."),
        ("Money used to measure and compare values is acting as a", "unit of account", ["store of value", "standard of lending", "store of goods"], "Prices are expressed in money."),
        ("Which is a quality of good money?", "It is acceptable, durable and divisible", ["It spoils quickly", "It is very heavy", "It cannot be divided"], "Good money is acceptable, durable, portable, divisible and scarce."),
        ("Saving money for future use is the function of money as a", "store of value", ["medium of exchange", "unit of account", "legal tender only"], "Money keeps purchasing power over time."),
    ]
    lesson(c4, "money-functions", "Barter and the functions of money",
        ["Explain the problems of barter.", "State the four functions and the qualities of money.", "Compute the number of prices in barter."],
        [("definition", "Barter and its problems", "**Barter** is the exchange of goods for goods without money. Its problems: it needs a **double coincidence of wants** (each person must want what the other has); there is no common **measure of value**; goods may be **indivisible**; value cannot be easily **stored** (a goat may die); and **deferred payments** are hard to arrange. Money solves these problems."),
         ("definition", "Functions of money", "Money is anything generally accepted as payment. Its functions: **medium of exchange** (used to buy and sell), **unit of account** (measures and compares values), **store of value** (keeps purchasing power for the future) and **standard of deferred payment** (debts and loans are fixed in money terms). In Cameroon the main money is the FCFA, issued by the central bank."),
         ("retenir", "Qualities of good money", "Good money is **acceptable** to everyone, **durable** (does not wear out), **portable**, **divisible** into small units, **scarce** enough to keep value, **uniform** (identical units) and **stable** in value. Forms of money: coins and notes, bank deposits (cheques, cards) and mobile money balances. **Legal tender** is money that the law says must be accepted for debts."),
         ("pieges", "Money is not wealth", "Money is a **means** of exchange, not the same as **wealth** (goods, land, skills). Printing more money does not make a country richer; it can cause **inflation**. A store of value can lose its worth if prices rise quickly. Do not write that money is the cause of all problems of exchange: banks and trust are also needed. Mobile money is a **form** of money, held on phone accounts.")],
        [("Worked example — number of prices", "In a barter economy of 5 goods, every good must have a price in terms of each other good. How many exchange rates are needed? How many prices are needed if one good is chosen as money?", ["Pairs of goods = 5 × 4 ÷ 2 = %d exchange rates." % pb_, "With money, each of the 5 goods has one price; the money good has price 1 itself, so 4 prices are needed.", "Money greatly reduces the number of prices to remember."], "10 exchange rates in barter against 4 prices with money"),
         ("Worked example — identify the function", "A trader in Garoua writes down that a goat costs 40 000 FCFA, keeps 20 000 FCFA in a drawer for next month, and pays for his goods by hand. Identify the functions of money shown.", ["Stating the goat's price in FCFA is unit of account.", "Keeping 20 000 for next month is store of value.", "Paying for goods is medium of exchange."], "Unit of account; store of value; medium of exchange")],
        [M("Which is NOT a function of money?", "Creating wants", ["Medium of exchange", "Unit of account", "Store of value"], "Money does not create wants."),
         TF("Barter requires a double coincidence of wants.", True, "Each party must want what the other has."),
         N("In a barter economy there are 6 goods. How many exchange rates are needed?", 6 * 5 / 2, "6 × 5 ÷ 2 = 15.", unit="rates")],
        [O("Explain two problems of barter and show how money overcomes them.", "A double coincidence of wants is needed: money lets a seller accept money from anyone and then buy what is wanted. Barter has no common measure of value: prices in money make comparison easy.", ["Two problems (2)", "Solutions given (1)"]),
         M("Why is livestock a poor form of money?", "It is not divisible or uniform and can die", ["It is portable", "It is acceptable", "It is scarce"], "Poor divisibility and poor durability.", tier="approfondissement")],
        P("A farmer in Dschang wants salt. The salt seller wants tomatoes. The farmer has only maize (illustrative).",
          [pm("The farmer's difficulty is", "lack of a double coincidence of wants", ["inflation", "too many banks", "a tax"], "The seller does not want maize.", 1),
           pm("How can money help?", "The farmer sells maize for money and buys salt with it", ["The farmer throws away the maize", "The seller must accept anything", "The salt becomes free"], "Money is a medium of exchange.", 1),
           po("State two qualities of money that make it better than maize as a means of exchange.", "Money is more durable, more divisible and more portable and acceptable to all; maize can rot and is bulky.", ["Two valid qualities (2)"], 2)]),
        tag(TAG, sc), (fig, "A failed barter exchange: the farmer, the shoemaker and their different wants.", "Four boxes in a row showing the farmer with maize, wanting shoes, the shoemaker wanting fish and the failure of barter."),
        notes=["Money in Cameroon: the FCFA is the legal currency; check the current legal status of electronic money before teaching more.", NOTE_SYL])

    # ---- 11 banking BEAC
    ex_rate = 655.957
    dep = 10_000_000; margin = 12 - 4; gain = dep * margin / 100; assert gain == 800000
    fig = hflow(["Savers\n(deposits)", "Commercial\nbank", "Borrowers\n(loans)"], w=440, size=12, title="What a commercial bank does")
    sc = [
        ("A commercial bank makes most of its profit from", "the difference between interest charged on loans and paid on deposits", ["printing money", "collecting taxes", "setting the exchange rate"], "Borrowers pay more than the bank pays savers."),
        ("An overdraft allows a customer to", "withdraw more than the account holds, up to an agreed limit", ["save without limit", "avoid all interest", "borrow from the BEAC directly"], "It is a short-term form of borrowing."),
        ("The central bank of the CEMAC states is the", "BEAC", ["BCEAO", "World Bank", "a commercial bank in Douala"], "The Bank of Central African States issues the FCFA for the CEMAC zone."),
        ("A function of a central bank is to", "issue notes and act as banker to the government and to commercial banks", ["lend only to farmers", "compete with commercial banks for deposits", "set the prices of all goods"], "It is the bankers' bank."),
        ("The central bank acts as lender of last resort when it", "lends to banks that cannot find funds elsewhere", ["refuses all loans", "lends only to households", "stops printing money"], "This keeps the banking system stable."),
    ]
    lesson(c4, "banks", "Commercial banks, the BEAC and the CEMAC",
        ["List the services of commercial banks.", "Explain the role of the central bank, BEAC, in the CEMAC.", "Calculate simple interest and the bank's margin."],
        [("definition", "Commercial banks", "**Commercial banks** accept **deposits** (current and savings accounts), make **loans and overdrafts**, transfer money, change foreign currency and keep valuables safe. They earn profit mainly from the **interest margin**: they lend at a higher rate than they pay on deposits. They balance **liquidity** (cash ready for customers), **profitability** and **safety**. Simple interest: **I = P × r × t** (principal × rate × years)."),
         ("definition", "BEAC and the CEMAC", "The **CEMAC** (Central African Economic and Monetary Community) groups six countries: Cameroon, the Central African Republic, Chad, the Republic of the Congo, Equatorial Guinea and Gabon. They share one currency, the **FCFA**, issued by the **BEAC** (Bank of Central African States), whose headquarters are in Yaoundé. The FCFA has a fixed rate with the euro (655.957 FCFA = 1 euro)."),
         ("retenir", "Functions of the central bank", "A central bank **issues notes and coins**, is **banker to the government** and **to commercial banks**, holds the country's **reserves of foreign currency**, acts as **lender of last resort** and carries out **monetary policy** (managing the supply of money and interest rates) to keep prices stable. A separate regional body supervises commercial banks in the zone (COBAC)."),
         ("pieges", "Do not confuse banks", "The BEAC serves banks and governments; ordinary people do not usually keep accounts there. A commercial bank cannot print FCFA. A fixed exchange rate gives stability but means a country cannot freely change its rate to help exports. Check carefully whether a question asks about the **zone** (CEMAC) or the **country** (Cameroon).")],
        [("Worked example — bank margin", "A bank pays 4 % interest a year on deposits and charges 12 % on loans. It lends out 10 000 000 FCFA of deposits for a year (illustrative). Find the interest margin earned in FCFA.", ["Margin rate = 12 %% − 4 %% = %d %%." % margin, "Interest margin = 10 000 000 × 8 ÷ 100.", "= %d FCFA before the bank's running costs." % gain], "800 000 FCFA before costs"),
         ("Worked example — simple interest", "Manga borrows 500 000 FCFA at 10 % simple interest a year for 3 years (illustrative). How much will she repay in total?", ["Interest = 500 000 × 10 ÷ 100 × 3 = %d FCFA." % (500000 * 10 / 100 * 3), "Total repaid = 500 000 + 150 000.", "= %d FCFA." % (500000 + 150000)], "650 000 FCFA")],
        [M("Which institution issues the FCFA in the CEMAC zone?", "BEAC", ["COBAC", "A local bank", "The ministry of trade"], "The BEAC is the zone's central bank."),
         TF("Cameroon is a member of the CEMAC.", True, "It is one of the six members."),
         N("Calculate simple interest on 200 000 FCFA at 5 % per year for 2 years (illustrative).", 200000 * 5 / 100 * 2, "200 000 × 0.05 × 2 = 20 000.", unit="FCFA")],
        [O("Describe three functions of a central bank such as the BEAC.", "It issues the currency; it is banker to governments and to commercial banks; it holds reserves of foreign currency; it acts as lender of last resort; it manages monetary policy.", ["Any three functions (3)"]),
         M("A bank pays 3 % on savings and charges 11 % on loans. Its interest margin is", "8 percentage points", ["3 percentage points", "14 percentage points", "11 percentage points"], "11 − 3 = 8.", tier="approfondissement")],
        P("A commercial bank in Yaoundé receives deposits of 20 000 000 FCFA and lends 15 000 000 FCFA (illustrative).",
          [pn("What percentage of deposits is lent out?", 15000000 / 20000000 * 100, "15 ÷ 20 × 100 = 75 %.", 0, "%", 1),
           pn("Cash left in the bank (FCFA)?", 5000000, "20 000 000 − 15 000 000 = 5 000 000.", 0, "FCFA", 1),
           po("Explain why a bank must keep some cash and cannot lend all deposits.", "Customers withdraw money every day, so the bank needs liquid cash to pay them; lending everything could cause a crisis of confidence.", ["Liquidity idea (1)", "Link to withdrawals (1)"], 2)]),
        tag(TAG, sc), (fig, "A commercial bank sits between savers who deposit money and borrowers who take loans.", "Three boxes joined by arrows: savers, commercial bank, borrowers."),
        notes=["CEMAC membership, the BEAC headquarters in Yaoundé, and the fixed rate of 655.957 FCFA per euro are standard facts; the role of COBAC as regional banking supervisor is stated in outline and should be checked.", NOTE_MONEY, NOTE_SYL])

    # ---- 12 microfinance njangi
    members = 10; contrib = 20000; pot = members * contrib; assert pot == 200000
    fig = ring(["Member 1", "Member 2", "Member 3", "Member 4", "Member 5"], w=420, h=300, title="A njangi round: the pot goes to the next member")
    sc = [
        ("A njangi (tontine) is", "a group that saves and lends by turns with regular contributions", ["a government bank", "a form of tax", "a foreign aid scheme"], "Members take turns to receive the pot."),
        ("A microfinance institution mainly gives", "small loans and savings services to people and small firms", ["large loans to governments", "money to the central bank", "insurance on ships only"], "It serves people who often lack access to big banks."),
        ("An advantage of a njangi is", "it is based on trust and often has no interest charges", ["it is regulated like a central bank", "it always pays dividends", "it prints its own money"], "It is flexible and local."),
        ("A risk in a njangi is", "a member may fail to pay after receiving the pot", ["the BEAC will close it", "it creates inflation", "it has too many branches"], "Informal groups have little legal protection."),
        ("A cooperative credit union is owned by", "its members", ["the BEAC", "foreign governments", "a single sole trader"], "Members save and borrow among themselves."),
    ]
    lesson(c4, "microfinance-njangi", "Microfinance, credit unions and njangi",
        ["Describe njangi/tontines, credit unions and microfinance institutions.", "Compare them with commercial banks.", "Calculate pots, contributions and simple loan costs."],
        [("definition", "Informal savings groups", "A **njangi** (also called a **tontine**) is a **rotating savings and credit association**. Members meet regularly, each pays a fixed contribution, and the total (the **pot**) goes to one member in turn until everyone has received it. It needs **trust** and rules, usually has no interest, and helps people save for rent, school fees or business capital."),
         ("definition", "Microfinance and credit unions", "**Microfinance institutions (MFIs)** offer small savings accounts and **small loans** to households and small traders who may find it hard to deal with a big bank. **Credit unions** are cooperatives owned by their members, who save and borrow among themselves. Both are usually licensed and supervised. Their loans often have higher interest rates and short terms because costs per small loan are high."),
         ("retenir", "Advantages and disadvantages", "**Njangi**: quick, local, flexible, no collateral; but no legal protection, little interest earned, risk of default and fixed amounts. **MFIs/credit unions**: provide small loans, teach saving and may give training; but interest rates can be high, and borrowers may become over-indebted. **Commercial banks**: safer for large sums and wide services but need documents and collateral."),
         ("pieges", "Facts to avoid", "Do not say that all informal groups are illegal or all MFIs are cheap. Rules depend on the type of group and on the law of the time. The CEMAC zone has regional rules for microfinance, but you should not quote details in exams unless you are certain. Keep to the **economic role**: saving, borrowing and risk.")],
        [("Worked example — the pot", "A njangi has %d members who each pay %d FCFA at every meeting (illustrative). How much is the pot? How much does a member pay in total in a full round and how much does she receive?" % (members, contrib), ["Pot = 10 × 20 000 = %d FCFA." % pot, "A member pays 20 000 at each of 10 meetings = %d FCFA." % (contrib * members), "She receives the pot once = 200 000 FCFA, so she earns no interest."], "Pot 200 000; pays 200 000 and receives 200 000"),
         ("Worked example — cost of a small loan", "An MFI lends 100 000 FCFA for 6 months and charges 2 % interest per month on the original sum (illustrative). Find the total interest and the total to repay.", ["Interest each month = 2 %% × 100 000 = %d FCFA." % (100000 * 2 / 100), "For 6 months = 2 000 × 6 = %d FCFA." % (2000 * 6), "Total repaid = 100 000 + 12 000 = %d FCFA." % 112000], "12 000 FCFA interest; 112 000 FCFA total")],
        [M("Who owns a credit union?", "Its members", ["The BEAC", "A single sole trader", "The government only"], "It is a cooperative of savers and borrowers."),
         TF("In a simple njangi each member normally receives more than she paid in.", False, "Over a full round she gets back what she paid."),
         N("A njangi has 8 members each paying 15 000 FCFA per meeting (illustrative). What is the pot in FCFA?", 8 * 15000, "8 × 15 000 = 120 000.", unit="FCFA")],
        [O("Explain one advantage and one risk of saving in a njangi rather than in a bank.", "Advantage: it is easy and flexible and based on trust, with no account fees. Risk: it has little legal protection, so a member may fail to pay or the treasurer may disappear.", ["Advantage (1)", "Risk (1)", "Link to informality (1)"]),
         M("A trader who needs 300 000 FCFA now and has no collateral may prefer", "a microfinance loan or taking an early turn in a njangi", ["selling the BEAC", "buying shares in a bank", "waiting for the CEMAC"], "These sources need less collateral than a large bank loan.", tier="approfondissement")],
        P("A njangi of 12 members pays 25 000 FCFA per meeting (illustrative). Odile receives the pot at meeting 2 and Paul at meeting 12.",
          [pn("Find the pot (FCFA).", 12 * 25000, "12 × 25 000 = 300 000.", 0, "FCFA", 1),
           pm("Who gains more, from the time value of money?", "Odile, who received money earlier", ["Paul, who received it later", "They gain exactly the same in every way", "Neither pays anything"], "Early receivers can use the money sooner; late receivers are in effect saving.", 1),
           po("Suggest one rule to reduce the risk of default.", "A guarantor, a fine for late payment, keeping a savings reserve or letting only trusted people join.", ["Valid rule (1)"], 1)]),
        tag(TAG, sc), (fig, "A njangi round: members contribute and the pot goes to each member in turn.", "Five member boxes arranged in a circle with arrows showing the order in which they receive the pot."),
        notes=["Njangi/tontine rules differ by group; the lesson gives a typical simple model.", "CamCCUL and regional microfinance regulations are deliberately not detailed.", NOTE_MONEY, NOTE_SYL])
