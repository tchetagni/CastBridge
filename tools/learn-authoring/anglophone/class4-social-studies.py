import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from p45_kit import *

TAG["v"] = "Class 4 Social Studies"
p = Pack("class4-social-studies", "Social Studies — Class 4", level="Class 4", subject="histoire-geo", cursus="primary",
         description="Class 4 Social Studies for the English-speaking primary subsystem: family and community, the regions of Cameroon, maps and directions, land and climate, work and markets, transport and communication, peoples and culture, national symbols and civic life, and a simple history of Cameroon.",
         programRef="MINEDUB primary school curriculum (English-speaking subsystem), Level II (Class 4) — Social Studies; to be checked against the official syllabus",
         wanted="social-studies")
REF = p.programRef
SENS = "Facts about Cameroon are limited to well-established, neutral points; names and dates to be checked by a teacher against the official textbook."

regions = [("Adamawa", "Ngaoundéré"), ("Centre", "Yaoundé"), ("East", "Bertoua"), ("Far North", "Maroua"), ("Littoral", "Douala"),
           ("North", "Garoua"), ("North-West", "Bamenda"), ("West", "Bafoussam"), ("South", "Ebolowa"), ("South-West", "Buea")]
assert len(regions) == 10

# ===================================================== 1. community
ch = p.chapter("community", "My family and my community", REF)

fig = grid([("Family", "parents, children"), ("School", "teachers, pupils"), ("Village or town", "neighbours"), ("Region", "many towns")], 4, 90, 62, gap=6, size=12)
build(ch, "family-community", "My family, my school and my community", 25,
      ["Name the members of a family and say what each does.", "Say what a community is and name community helpers.", "Show respect and help in the community."],
      [("key", "definition", "Family and community", "A **family** is a group of people who live together and care for each other: parents, children, grandparents and sometimes aunts, uncles and cousins. A **community** is a group of people who live in the same place (a village or town) and share things like a market, a school and a health centre. Many communities together make a **region**, and all the regions make the **country**."),
       ("fig", fig, "From family to region.", "Four boxes: family with parents and children; school with teachers and pupils; village or town with neighbours; region with many towns."),
       ("key", "retenir", "Community helpers", "**Teachers** educate us. **Nurses and doctors** care for the sick. **Farmers** grow our food. **Drivers** carry people and goods. **Police officers** keep us safe. **Traders** sell goods in the market. **Chiefs and mayors** help to lead the community. Each person's work is important and we must respect it."),
       ("key", "pieges", "Common mistakes", "- Thinking that only adults can help the community: children can keep the school clean, help neighbours and be polite.\n- Looking down on a helper because of the kind of work he or she does.\n- Forgetting that every member of the family has duties as well as rights.")],
      [("ex", "Example 1", "Who helps you when you are ill, and who teaches you at school?", ["A nurse or a doctor helps when we are ill.", "A teacher teaches us at school."], "a nurse or doctor; a teacher.", None),
       ("ex", "Example 2", "Give two ways in which a pupil can help the school community.", ["Think of simple, daily actions.", "Keep the classroom clean and help a classmate who needs help."], "Keep the classroom clean; help a classmate.", None)],
      [M("application", "Who looks after sick people in a health centre?", "nurses and doctors", ["farmers", "drivers", "bakers"], "They are health workers."),
       M("application", "A group of people living in the same place is a...", "community", ["market only", "forest", "river"], "Community."),
       M("application", "Who grows our food?", "farmers", ["pilots", "judges", "tailors"], "Farmers grow food."),
       TF("approfondissement", "Only adults can help their community.", False, "Children can help too."),
       MA("approfondissement", "Match each helper with the job.", [("teacher", "educates pupils"), ("farmer", "grows food"), ("driver", "carries people and goods"), ("nurse", "cares for the sick")], "Each job helps the community."),
       P("In the village of Bali there is a school, a health centre, a market and a river. Mr Che is a farmer, Mrs Nche is a nurse and Miss Ako is the teacher.",
         [PM("Who treats a child with a fever?", "Mrs Nche", ["Mr Che", "Miss Ako", "the driver"], "She is the nurse."),
          PM("Where do people sell and buy food?", "at the market", ["at the health centre", "at the river only", "at the school"], "The market."),
          PM("Which one is a good way for a pupil to help?", "to keep the school compound clean", ["to throw rubbish in the river", "to stay away from school", "to shout in class"], "Good habits help everyone.", points=2)])],
      [("A family is...", "a group of people who care for each other", ["a market", "a river", "a road"], "Parents and children live together."),
       ("A community helper who teaches is a...", "teacher", ["driver", "farmer", "nurse"], "Teachers educate."),
       ("Many communities make a...", "region", ["room", "house", "compound"], "And regions make the country."),
       ("A good pupil in the community...", "respects others", ["fights", "steals", "lies"], "Respect is a duty."),
       ("Who grows food for the community?", "the farmer", ["the police", "the judge", "the pilot"], "Farming provides food.")],
      [SENS])

# ===================================================== 2. Cameroon and maps
ch = p.chapter("cameroon", "Cameroon: regions, maps and land", REF)

fig = shapes([x for i, (r, c) in enumerate(regions) for x in (
    RECT(8 + (i % 2) * 196, 8 + (i // 2) * 40, 188, 34, fill=("lightblue", "lightyellow", "lightgreen")[i % 3], radius=5),
    T(102 + (i % 2) * 196, 31 + (i // 2) * 40, r + ": " + c, 13, bold=True))], 400, 214)
build(ch, "regions", "Where is Cameroon? Regions and capitals", 30,
      ["Say where Cameroon is and name some neighbours.", "Name the ten regions and their capitals.", "Name the capital city and the official languages."],
      [("key", "definition", "Location", "Cameroon is in **Central Africa**, near the **Equator**. It has a coast on the **Atlantic Ocean**. Its neighbours are **Nigeria** (west), **Chad** (north-east), the **Central African Republic** (east) and the **Republic of the Congo**, **Gabon** and **Equatorial Guinea** (south). The capital is **Yaoundé**. The official languages are **English and French**."),
       ("fig", fig, "The ten regions of Cameroon and their capitals.", "Ten boxes. Each shows a region and its capital: Adamawa, Ngaoundéré; Centre, Yaoundé; East, Bertoua; Far North, Maroua; Littoral, Douala; North, Garoua; North-West, Bamenda; West, Bafoussam; South, Ebolowa; South-West, Buea."),
       ("key", "retenir", "Remember", "There are **10 regions**. Two are English-speaking: the **North-West** (capital Bamenda) and the **South-West** (capital Buea). **Douala** is the biggest city and main port. Each region has a regional capital and is divided into smaller units (divisions)."),
       ("key", "pieges", "Common mistakes", "- Saying that Douala is the capital of Cameroon: it is Yaoundé.\n- Mixing a region with its capital.\n- Writing *Northwest* in different ways: use *North-West*.\n- Forgetting that Cameroon has ten regions.")],
      [("ex", "Example 1", "What is the capital of the West Region?", ["Look at the list of regions and capitals.", "The West Region has Bafoussam as its capital."], "Bafoussam", None),
       ("ex", "Example 2", "Name two neighbours of Cameroon and say in which direction each lies.", ["Nigeria lies to the west.", "Chad lies to the north-east."], "Nigeria (west) and Chad (north-east).", None)],
      [M("application", "What is the capital of the South-West Region?", "Buea", ["Bamenda", "Bertoua", "Maroua"], "Buea."),
       N("application", "How many regions are there in Cameroon?", len(regions), "Ten."),
       M("application", "Which ocean is next to Cameroon?", "the Atlantic Ocean", ["the Indian Ocean", "the Pacific Ocean", "the Arctic Ocean"], "Cameroon has an Atlantic coast."),
       TF("approfondissement", "Douala is the political capital of Cameroon.", False, "Yaoundé is the capital; Douala is the biggest city and main port."),
       MA("approfondissement", "Match each region with its capital.", [("Littoral", "Douala"), ("North-West", "Bamenda"), ("East", "Bertoua"), ("South", "Ebolowa")], "Learn them in pairs."),
       P("A class studies the map. Neba lives in Bamenda, Ako lives in Maroua and Bih lives in Buea.",
         [PM("In which region does Neba live?", "North-West", ["South-West", "Far North", "West"], "Bamenda is in the North-West."),
          PM("In which region does Ako live?", "Far North", ["North", "Adamawa", "East"], "Maroua is the capital of the Far North."),
          PM("Which two regions are English-speaking?", "North-West and South-West", ["West and Littoral", "East and South", "North and Far North"], "Bamenda and Buea.", points=2)])],
      [("The capital of Cameroon is...", "Yaoundé", ["Douala", "Garoua", "Limbe"], "Yaoundé."),
       ("Cameroon is in...", "Central Africa", ["Europe", "Asia", "South America"], "Near the Equator."),
       ("The capital of the North Region is...", "Garoua", ["Maroua", "Bamenda", "Buea"], "Garoua."),
       ("The two official languages are...", "English and French", ["English and Spanish", "French and Arabic", "Fulfulde and Ewondo"], "English and French."),
       ("The biggest city and main port is...", "Douala", ["Yaoundé", "Bamenda", "Bertoua"], "Douala is on the Wouri.")],
      [SENS, "Regional capitals are standard facts; the number of regions (10) reflects the current administrative structure."])

# compass rose
cx, cy = 200, 100
fig = shapes([LINE(cx, cy + 75, cx, cy - 75, width=3, arrow="end"), LINE(cx - 90, cy, cx + 90, cy, width=3, arrow="both"),
              LINE(cx - 55, cy + 55, cx + 55, cy - 55, width=1, color="grey"), LINE(cx - 55, cy - 55, cx + 55, cy + 55, width=1, color="grey"),
              T(cx, cy - 85, "N", 18, bold=True, color="red"), T(cx, cy + 100, "S", 18, bold=True), T(cx + 105, cy + 6, "E", 18, bold=True), T(cx - 105, cy + 6, "W", 18, bold=True),
              T(cx + 62, cy - 62, "NE", 12), T(cx - 62, cy - 62, "NW", 12), T(cx + 62, cy + 70, "SE", 12), T(cx - 62, cy + 70, "SW", 12)], 400, 215)
fig_grid = shapes([RECT(40 + c * 80, 20 + r * 40, 80, 40, fill=("paper" if (r + c) % 2 else "lightyellow"), width=1) for r in range(4) for c in range(4)] +
                  [T(80 + 80 * c, 12, "ABCD"[c], 13, bold=True) for c in range(4)] + [T(26, 45 + 40 * r, str(r + 1), 13, bold=True) for r in range(4)] +
                  [T(80, 85, "school", 12), T(240, 45, "market", 12), T(320, 125, "river", 12, color="blue", bold=True), T(160, 125, "health centre", 11)], 400, 200)
build(ch, "maps-directions", "Maps and directions", 30,
      ["Name the four main directions and the points in between.", "Read a simple map using its key and a grid.", "Draw a simple plan of the classroom."],
      [("key", "definition", "Directions", "The four main directions (**cardinal points**) are **north (N), east (E), south (S) and west (W)**. In between are north-east (NE), south-east (SE), south-west (SW) and north-west (NW). The Sun **rises in the east** and **sets in the west**. A **compass** has a needle that points north."),
       ("fig", fig, "The compass rose.", "A compass rose with arrows north and south, east and west, and the points NE, NW, SE and SW marked between them."),
       ("key", "methode", "Reading a map", "A **map** is a drawing of a place seen from above. Read the **title** and the **key** (what each symbol means). **Grid references** use a letter for the column and a number for the row, written like **B3**. Always start with the **letter**. Maps use **symbols** (a square for a school, a blue line for a river) and a **scale**."),
       ("fig", fig_grid, "A simple map with a grid.", "A four by four grid with letters A to D across the top and numbers 1 to 4 down the side. The school is in B2, the market in C1, the river in D3 and the health centre in C3."),
       ("key", "pieges", "Common mistakes", "- Saying that the Sun rises in the west.\n- Writing a grid reference with the number first: write **B3**, not 3B.\n- Not using the key.\n- Thinking that a map shows what you see from the side.")],
      [("ex", "Example 1", "If you face north, in which direction is your right hand?", ["Imagine the compass: north is in front, east is to the right.", "Your right hand points to the east."], "East", fig),
       ("ex", "Example 2", "On the grid map, what is in square C1?", ["Find column C, then row 1.", "The market is there."], "The market", fig_grid)],
      [M("application", "In which direction does the Sun rise?", "east", ["west", "north", "south"], "The Sun rises in the east."),
       M("application", "The opposite of north is...", "south", ["east", "west", "north-east"], "South."),
       M("application", "A grid reference gives the...", "letter first, then the number", ["number first, then the letter", "only letters", "only numbers"], "Like B3."),
       TF("approfondissement", "A map key explains the symbols on the map.", True, "Always read it."),
       MA("approfondissement", "Match each direction with its opposite.", [("north", "south"), ("east", "west"), ("north-east", "south-west"), ("north-west", "south-east")], "Opposites are across the compass."),
       P("Look at the map in this lesson. The grid has letters A to D and numbers 1 to 4.",
         [PM("What is in square B2?", "the school", ["the market", "the river", "the health centre"], "The school is in B2."),
          PM("What is in square D3?", "the river", ["the school", "the market", "the road"], "River."),
          PM("In which direction from the school is the market? (the market is in C1)", "east", ["west", "south", "north-west"], "C is to the right of B.", points=2)])],
      [("The four main directions are...", "north, east, south and west", ["up, down, left and right", "front, back and side", "red, green and blue"], "Cardinal points."),
       ("A compass needle points to...", "north", ["south", "east", "west"], "Magnetic north."),
       ("A key on a map shows...", "what the symbols mean", ["the weather", "the time", "the prices"], "It explains the symbols."),
       ("The Sun sets in the...", "west", ["east", "north", "south"], "West."),
       ("A map is a drawing of a place seen from...", "above", ["below", "the side", "inside"], "A bird's eye view.")],
      [SENS, "The grid map is an invented example (not a real place)."])

fig = table([["Relief / zone", "Where", "Example"], ["coast", "the Atlantic side", "Limbe, Kribi"], ["highlands", "West and North-West", "Mount Oku area"], ["volcano", "South-West", "Mount Cameroon"], ["savanna", "Adamawa and North", "Garoua"], ["rainforest", "South and East", "near Ebolowa"]], [100, 130, 120], rh=28, size=12)
build(ch, "land-climate", "Land, rivers and climate of Cameroon", 30,
      ["Describe the main kinds of land in Cameroon.", "Name Mount Cameroon and some rivers.", "Describe the hot wet south and the hotter drier north."],
      [("key", "definition", "Relief", "Cameroon has a low **coastal plain** by the Atlantic, **plateaus** in the centre and south, **highlands** with hills and mountains in the West and North-West, and flatter, drier land in the **north**. **Mount Cameroon** (also called Fako) is the highest mountain; it is an **active volcano** in the South-West Region."),
       ("fig", fig, "Kinds of land in Cameroon.", "A table with relief or zone, where, and an example: coast near Limbe and Kribi, highlands in the West and North-West, volcano Mount Cameroon in the South-West, savanna in Adamawa and the North, rainforest in the South and East."),
       ("key", "retenir", "Rivers and climate", "Important rivers are the **Sanaga**, the **Wouri**, the **Benue** and the **Logone**; **Lake Chad** is in the far north. The **south** is **hot and wet**, with **two rainy seasons**. The **north** is **hotter and drier**, with **one rainy season**. The highlands are cooler. In the dry season the dusty **Harmattan** wind blows from the north."),
       ("key", "pieges", "Common mistakes", "- Saying that Cameroon has the same weather everywhere.\n- Thinking that all of Cameroon is rainforest.\n- Putting Mount Cameroon in the north.\n- Saying that the Wouri flows to Lake Chad (it flows into the Atlantic).")],
      [("ex", "Example 1", "In which region is Mount Cameroon?", ["Mount Cameroon is near Buea and Limbe.", "Buea and Limbe are in the South-West Region."], "The South-West Region", None),
       ("ex", "Example 2", "Why can cocoa grow in the south but cotton grows better in the north?", ["Cocoa needs a lot of rain and warmth: the south is wet.", "Cotton needs a long sunny dry season: the north is drier."], "Cocoa likes the wet south; cotton likes the drier north.", None)],
      [M("application", "What is the highest mountain in Cameroon?", "Mount Cameroon", ["Mount Kilimanjaro", "Mount Everest", "Mount Kenya"], "Fako."),
       M("application", "Which part of Cameroon is hotter and drier?", "the north", ["the south", "the coast", "the Centre only"], "The north."),
       M("application", "Which is a river of Cameroon?", "the Sanaga", ["the Nile", "the Amazon", "the Thames"], "The Sanaga."),
       TF("approfondissement", "The south of Cameroon has two rainy seasons.", True, "Yes."),
       MA("approfondissement", "Match each feature with its place.", [("Mount Cameroon", "South-West Region"), ("Lake Chad", "the far north"), ("Wouri River", "near Douala"), ("savanna", "Adamawa and the North")], "Well-known features."),
       P("Neba travels from Buea to Maroua. He sees a green forest near the coast, then hills, then a flat, dry land with few trees.",
         [PM("Which land is near the coast?", "a forest and a plain", ["a desert only", "snow mountains", "only savanna"], "The coast has forest and plains."),
          PM("Which part is flat and dry?", "the far north", ["the coast", "the highlands", "Buea"], "The north is drier."),
          PM("How many rainy seasons has the south of Cameroon?", "two", ["one", "three", "none"], "Two.", points=2)])],
      [("Mount Cameroon is a...", "volcano", ["lake", "desert", "river"], "Active volcano."),
       ("The Wouri flows near...", "Douala", ["Garoua", "Maroua", "Bamenda"], "Into the Atlantic at Douala."),
       ("The Harmattan is a...", "dry dusty wind", ["cold rain", "type of food", "river"], "It blows in the dry season."),
       ("Which is in the far north?", "Lake Chad", ["Mount Cameroon", "the Wouri", "the coast"], "Lake Chad."),
       ("Highlands are...", "cooler", ["hotter always", "the same as the coast", "under water"], "Cooler than the lowlands.")],
      [SENS, "Examples are simplified; see the Class 5 Social Studies and FSLC pack for more detail."])

# ===================================================== 3. work and travel
ch = p.chapter("work-travel", "Work, markets, transport and communication", REF)

fig = table([["Crop / product", "Where it is important"], ["cocoa", "Centre, South, South-West"], ["coffee", "West, South-West"], ["cotton", "North"], ["bananas, oil palm", "South-West, Littoral"], ["cattle", "Adamawa, North, North-West"], ["fish", "coast, rivers, Lake Chad"]], [150, 220], rh=28, size=13)
build(ch, "economic-activities", "Work: farming, fishing, trade and markets", 30,
      ["Name main activities by which people earn a living.", "Give crops and where they grow.", "Explain how a market works."],
      [("key", "definition", "Ways of earning a living", "People work to earn money. **Farmers** grow crops and keep animals. **Fishers** catch fish. **Traders** buy and sell goods. **Craftspeople** (tailors, carpenters, potters) make things. **Workers** in factories, offices, schools and hospitals give services. Farmers sell their crops, and the money buys what the family needs."),
       ("fig", fig, "Main crops and products and where they are important.", "A table: cocoa in the Centre, South and South-West; coffee in the West and South-West; cotton in the North; bananas and oil palm in the South-West and Littoral; cattle in Adamawa, the North and North-West; fish on the coast, in rivers and in Lake Chad."),
       ("key", "retenir", "Markets", "A **market** is a place where sellers and buyers meet. Farmers bring **food crops** (cassava, plantain, maize, yams, beans) and **traders** bring other goods. Prices go up when goods are **scarce** and down when there are **many** goods. Some towns have **market days**. Money (FCFA) is used to pay for goods. Some crops are **cash crops** (cocoa, coffee, cotton) sold to earn money; others are **food crops** eaten by families."),
       ("key", "pieges", "Common mistakes", "- Thinking that all farmers grow the same crops: the crops depend on the climate and soil.\n- Forgetting that teachers, nurses and drivers also work and earn money.\n- Saying that cash crops cannot be eaten: the name shows they are mainly grown to be sold.\n- Wasting food or money.")],
      [("ex", "Example 1", "Name a cash crop and a food crop.", ["A cash crop is grown mainly to be sold: cocoa.", "A food crop is mainly for eating: cassava."], "Cash crop: cocoa; food crop: cassava.", fig),
       ("ex", "Example 2", "Mama Ngo sells 4 bunches of plantains at 500 FCFA each. How much money does she get?", ["Multiply the number of bunches by the price.", "4 × 500 = 2,000."], "2,000 FCFA", None)],
      [M("application", "Which is a cash crop?", "cocoa", ["cassava", "plantain", "yam"], "Cocoa is sold to earn money."),
       M("application", "Where do buyers and sellers meet?", "at the market", ["in a forest", "in the river", "at the airport only"], "The market."),
       M("application", "Which crop is mainly grown in the North of Cameroon?", "cotton", ["cocoa", "oil palm", "rubber"], "The north is drier."),
       N("approfondissement", "A farmer sells 20 bags of maize at 6,000 FCFA each. How much money does he get?", 120000, "20 × 6,000 = 120,000.", unit="FCFA"),
       TF("approfondissement", "Tailors and carpenters are craftspeople.", True, "They make things."),
       P("A trader at the Bafoussam market buys 10 bags of beans at 15,000 FCFA each and sells all of them at 18,000 FCFA each.",
         [PN("How much did he pay in all?", 150000, "10 × 15,000 = 150,000.", unit="FCFA"),
          PN("How much did he get from selling all?", 180000, "10 × 18,000 = 180,000.", unit="FCFA"),
          PN("How much profit did he make?", 30000, "180,000 − 150,000 = 30,000.", unit="FCFA", points=2)])],
      [("A farmer...", "grows crops", ["catches fish only", "flies planes", "cooks only"], "Farmers grow crops."),
       ("A crop grown mainly to be sold is a...", "cash crop", ["food crop only", "weed", "flower only"], "Cocoa, coffee, cotton."),
       ("Where are cattle mainly reared?", "Adamawa and the North", ["the coast", "the sea", "the city only"], "Open grassland."),
       ("A market is where...", "buyers and sellers meet", ["only children play", "only farmers sleep", "planes land"], "Goods are bought and sold."),
       ("Which person offers a service?", "a nurse", ["a maize plant", "a cow", "a stone"], "A service helps people.")],
      [SENS, "Crop locations are simplified; many crops are grown in several regions."])

fig = table([["Means", "Examples", "Carries"], ["road", "bus, taxi, lorry", "people, goods"], ["rail", "train (Douala, Yaoundé, Ngaoundéré)", "people, goods"], ["water", "boat, ship (port of Douala)", "goods, people"], ["air", "aeroplane (Douala, Yaoundé)", "people, mail"]], [60, 220, 90], rh=30, size=12)
build(ch, "transport-communication", "Transport and communication", 30,
      ["Name means of transport on land, water and air.", "Name means of communication.", "Say why good roads and communication are important."],
      [("key", "definition", "Transport", "**Transport** carries people and goods. By **road**: motorbikes, taxis, buses and lorries (the most used in Cameroon). By **rail**: trains, for example from Douala to Yaoundé and on to Ngaoundéré. By **water**: canoes, boats and ships; **Douala** and **Kribi** have ports. By **air**: aeroplanes; there are international airports at Douala and Yaoundé."),
       ("fig", fig, "Means of transport in Cameroon.", "A table of road, rail, water and air with examples such as bus and lorry, the train between Douala, Yaoundé and Ngaoundéré, ships at the port of Douala and aeroplanes at Douala and Yaoundé."),
       ("key", "retenir", "Communication", "**Communication** is sharing information. **Old ways**: a talking drum, a messenger, a letter by post. **Modern ways**: telephone and mobile phone, radio, television, newspapers and the internet. Good roads and communication help farmers to sell crops, children to go to school and people to call for help."),
       ("key", "pieges", "Common mistakes", "- Crossing the road without looking.\n- Riding a motorbike without a helmet or travelling in an overloaded vehicle.\n- Believing everything we hear on the phone or the internet: check the facts.\n- Thinking that transport and communication are the same.")],
      [("ex", "Example 1", "How can goods go from Douala to Garoua?", ["Goods can travel by road, or by train to Ngaoundéré and then by road.", "Both are possible."], "By road, or by train and road.", None),
       ("ex", "Example 2", "Name two old and two new ways to send a message.", ["Old: a messenger and a letter by post.", "New: a mobile phone call and a text message."], "Old: messenger, letter. New: phone call, text message.", None)],
      [M("application", "Which is a means of air transport?", "an aeroplane", ["a canoe", "a lorry", "a train"], "Planes fly."),
       M("application", "Which is a means of communication?", "a mobile phone", ["a lorry", "a bridge", "a road"], "A phone sends messages."),
       M("application", "Which port is the biggest in Cameroon?", "Douala", ["Garoua", "Bamenda", "Maroua"], "Douala."),
       TF("approfondissement", "A letter sent by post is a means of communication.", True, "Yes."),
       MA("approfondissement", "Match each means with where it travels.", [("train", "on rails"), ("ship", "on the sea"), ("bus", "on the road"), ("aeroplane", "in the air")], "Land, water and air."),
       P("A school in Bali sends its football team by bus to Bamenda. The coach calls the parents on a mobile phone to say they have arrived safely.",
         [PM("Which means of transport is used?", "a bus on the road", ["a ship", "an aeroplane", "a train"], "Road transport."),
          PM("Which means of communication is used?", "a mobile phone", ["a letter", "a drum", "a newspaper"], "The coach calls."),
          PM("Why is a safe road important?", "it protects travellers and helps goods to arrive", ["it makes rain", "it is a river", "it is a market"], "Good roads help everyone.", points=2)])],
      [("Transport means...", "carrying people and goods", ["sharing information", "growing crops", "reading"], "Moving."),
       ("Communication means...", "sharing information", ["moving goods", "growing food", "building roads"], "Phones, radio, letters."),
       ("Which carries goods on water?", "a ship", ["a bus", "a plane", "a bicycle"], "At ports."),
       ("The train line goes from Douala to Yaoundé and on to...", "Ngaoundéré", ["Garoua only", "Bamenda", "Limbe"], "The railway."),
       ("A safe habit when riding a motorbike is to...", "wear a helmet", ["carry many people", "ride fast", "close your eyes"], "Safety first.")],
      [SENS, "Railway and airport statements are well-known facts; no dates or figures are given on purpose."])

# ===================================================== 4. culture and civics
ch = p.chapter("culture-civics", "People, culture and citizenship", REF)

fig = table([["Part of culture", "Examples"], ["languages", "English, French, Pidgin, local languages"], ["food", "ndolé, achu, eru, koki"], ["dress", "toghu, kaba, boubou"], ["art", "music, dance, masks"]], [120, 250], rh=30, size=13)
build(ch, "peoples-culture", "The peoples and cultures of Cameroon", 30,
      ["Say that Cameroon has many peoples and languages.", "Give examples of food, dress, music and dance.", "Show respect for all cultures."],
      [("key", "definition", "Many peoples", "Cameroon has **more than two hundred** ethnic groups and many local languages. People live as farmers, fishers, herders and traders, in villages and cities. **Culture** is the way of life of a people: language, food, dress, music, dance, stories, festivals and good manners. Each culture is **special** and deserves **respect**."),
       ("fig", fig, "Parts of culture.", "Four boxes: languages with English, French and many others; food with ndolé, achu, eru and koki; dress with toghu, kaba and boubou; art with music, dance and masks."),
       ("key", "retenir", "Examples", "**Food**: ndolé, achu, eru, koki, fufu corn and plantain. **Dress**: the *toghu* of the Grassfields, the *kaba* and the *boubou*. **Chiefs and fons** are respected leaders in many communities. **Festivals** such as the *Ngondo* (Sawa people, Douala) and the *Nguon* (Bamoun people, Foumban) bring communities together. People use **English, French and Cameroon Pidgin** to talk to each other."),
       ("key", "pieges", "Common mistakes", "- Saying that one culture is better than another.\n- Laughing at someone's language, accent or dress.\n- Thinking that all people in Cameroon eat the same food or dress the same way.\n- Forgetting that cultures change and share things.")],
      [("ex", "Example 1", "Name two traditional dishes of Cameroon.", ["Dishes are made from local foods.", "For example ndolé and achu (also eru, koki)."], "ndolé and achu", None),
       ("ex", "Example 2", "A new pupil in your class speaks another language at home. How should you treat him?", ["Welcome him, be polite and help him to learn.", "Respect his language, as it is part of his culture."], "Welcome and respect him.", None)],
      [M("application", "Which is a traditional dish of Cameroon?", "ndolé", ["pizza", "sushi", "hamburger"], "Ndolé is made with bitterleaf."),
       M("application", "Culture means...", "the way of life of a people", ["only dancing", "only food", "only money"], "Language, food, dress, music."),
       M("application", "Which languages are the official languages?", "English and French", ["English and Spanish", "French and Arabic", "Pidgin only"], "Official languages."),
       TF("approfondissement", "We should respect every culture.", True, "All cultures are valuable."),
       MA("approfondissement", "Match each item with its group.", [("achu", "food"), ("toghu", "dress"), ("Ngondo", "festival"), ("Cameroon Pidgin", "language")], "Parts of culture."),
       P("At a school cultural day in Bamenda, pupils bring dishes, wear traditional dress and perform songs and dances from different regions.",
         [PM("Which is a part of culture?", "traditional dress", ["a hammer", "the weather", "a road sign"], "Dress is cultural."),
          PM("What should pupils do while others perform?", "watch and listen politely", ["laugh at them", "talk loudly", "leave the room"], "Respect."),
          PM("Why is a cultural day a good idea?", "pupils learn about each other's cultures", ["to avoid lessons only", "to make people fight", "to hide traditions"], "It builds understanding.", points=2)])],
      [("Cameroon has...", "many peoples and languages", ["only one language", "no cultures", "only one people"], "More than two hundred groups."),
       ("A fon or chief is...", "a traditional leader", ["a type of food", "a school subject", "a river"], "Respected in his community."),
       ("Which is a traditional dress?", "toghu", ["jeans only", "a helmet", "a uniform only"], "A Grassfields dress."),
       ("To respect another culture we...", "listen and do not laugh", ["mock them", "ignore them", "fight them"], "Respect."),
       ("Which is a festival?", "Ngondo", ["ndolé", "Wouri", "Buea"], "A festival of the Sawa people.")],
      [SENS, "Festival and dress names are well known; the number of ethnic groups is given as 'more than two hundred' (commonly stated, to be checked). Cultural content should be reviewed by teachers from the communities concerned."])

fig = shapes([RECT(60, 30, 80, 100, fill="green"), RECT(140, 30, 80, 100, fill="red"), RECT(220, 30, 80, 100, fill="yellow"), POLY([180, 55, 190, 82, 160, 65, 200, 65, 170, 82], fill="yellow", width=1),
              T(100, 150, "green", 13, bold=True), T(180, 150, "red", 13, bold=True), T(260, 150, "yellow", 13, bold=True), T(180, 20, "the flag of Cameroon", 13, bold=True)], 400, 175)
build(ch, "national-symbols-civics", "National symbols, rules, rights and duties", 30,
      ["Describe the flag and name the motto and the anthem.", "Explain why we have rules at home, at school and in the country.", "Name some rights and duties of a child."],
      [("key", "definition", "National symbols", "The **flag** of Cameroon has three **vertical** bands: **green, red and yellow**, with a **yellow star** on the red band. The **motto** is **Peace, Work, Fatherland**. The **national anthem** is *O Cameroon, Cradle of Our Forefathers*. We stand and sing it with respect. **National Day** is on **20 May** and **Youth Day** on **11 February**."),
       ("fig", fig, "The flag of Cameroon.", "Three vertical bands: green on the left, red in the middle with a yellow star, and yellow on the right."),
       ("key", "retenir", "Rules, rights and duties", "**Rules** keep us safe and make life fair: at home, at school and in the country (the **laws**). Children have **rights**: to a name, to **education**, to **health care**, to **food and a home**, to **play** and to be **protected** from harm. Rights come with **duties**: respect others, obey school rules, be on time, look after school property, and tell the truth."),
       ("key", "pieges", "Common mistakes", "- Thinking that rights mean that we can do whatever we like: my rights end where the rights of others start.\n- Putting the flag colours in the wrong order or thinking that the star is on the green band.\n- Forgetting that children also have duties.\n- Breaking school rules because nobody is watching.")],
      [("ex", "Example 1", "Give one right and one duty of a pupil.", ["A right: to go to school and learn.", "A duty: to respect the teacher and the school rules."], "Right: education; duty: respect the school rules.", None),
       ("ex", "Example 2", "What colour is the star on the Cameroon flag and on which band is it?", ["The star is yellow.", "It is on the red band, in the middle."], "A yellow star on the red band.", fig)],
      [M("application", "What is the motto of Cameroon?", "Peace, Work, Fatherland", ["Unity, Faith, Love", "Freedom, Justice, Land", "Hard Work, Honesty, Duty"], "The national motto."),
       M("application", "What colours are in the flag of Cameroon?", "green, red and yellow", ["blue, white and red", "black, white and green", "red, white and yellow"], "Three vertical bands."),
       M("application", "On which date is National Day?", "20 May", ["11 February", "25 December", "1 September"], "20 May."),
       TF("approfondissement", "Children have only rights and no duties.", False, "Rights and duties go together."),
       MA("approfondissement", "Match each date with its celebration.", [("20 May", "National Day"), ("11 February", "Youth Day"), ("the first day of the year", "New Year's Day"), ("25 December", "Christmas Day")], "Learn the important days."),
       P("At the school assembly the flag is raised and the pupils sing the national anthem. Then the headmaster reminds everyone of the school rules.",
         [PM("How should pupils behave during the anthem?", "stand and sing with respect", ["talk and play", "sit and eat", "run around"], "Respect the anthem."),
          PM("Why do we have school rules?", "to keep everyone safe and make school fair", ["to make children sad", "to stop learning", "to punish everyone"], "Rules help everyone."),
          PM("Which is a duty of a pupil?", "to be on time", ["to break desks", "to shout in class", "to copy in tests"], "Being on time is a duty.", points=2)])],
      [("The star on the flag of Cameroon is...", "yellow", ["green", "red", "white"], "It is on the red band."),
       ("The first word of the motto is...", "Peace", ["Work", "Fatherland", "Union"], "Peace, Work, Fatherland."),
       ("A right of a child is the right to...", "education", ["steal", "fight", "drop litter"], "Every child has the right to learn."),
       ("A duty of a citizen is to...", "obey the law", ["break rules", "harm others", "waste public property"], "Rights come with duties."),
       ("Youth Day is on...", "11 February", ["20 May", "25 December", "1 January"], "11 February.")],
      [SENS, "Flag, motto, anthem title and the two national days are standard facts; check the anthem's title wording in the official English text."])

fig = timeline(1450, 1970, [(1472, "Portuguese reach the Wouri"), (1884, "German rule begins"), (1916, "Britain and France"), (1960, "Independence")], [], 480, 230)
assert 1960 - 1884 == 76
build(ch, "history-simple", "A very short history of Cameroon", 30,
      ["Say how Cameroon got its name.", "Put important events in order on a timeline.", "Say who ruled Cameroon before independence."],
      [("key", "definition", "The name Cameroon", "About **1472**, Portuguese sailors reached the River Wouri and saw many prawns. They named it *Rio dos Camarões*, 'river of prawns'. Over time the name became **Cameroon**. Long before that, many peoples already lived in the land, with their own chiefs and kingdoms."),
       ("fig", fig, "Four important dates.", "A timeline from 1450 to 1970 with four events: about 1472 Portuguese reach the Wouri, 1884 German rule begins, 1916 Britain and France take over, 1960 independence."),
       ("key", "retenir", "Colonial times and independence", "In **1884** the country became a German **protectorate** called *Kamerun*. After the First World War, in **1916**, the land was shared between **Britain** and **France**. **French Cameroun** became independent on **1 January 1960**. In **1961** the Southern Cameroons voted to join it. That is why Cameroon uses **English and French**."),
       ("key", "pieges", "Common mistakes", "- Thinking that Cameroon was always one country with the same borders.\n- Mixing the dates: 1472 (Portuguese), 1884 (Germans), 1916 (Britain and France), 1960 (independence).\n- Saying that the Portuguese ruled Cameroon: they only traded on the coast.\n- Giving opinions about politics: use facts.")],
      [("ex", "Example 1", "How many years are there between 1884 and 1960?", ["Subtract: 1960 − 1884.", "1960 − 1884 = 76."], "76 years", None),
       ("ex", "Example 2", "Why does Cameroon have two official languages?", ["Britain ruled a part and France ruled a larger part.", "Both left their languages: English and French."], "Because part of the country was ruled by Britain and part by France.", fig)],
      [M("application", "Who named the Wouri 'river of prawns'?", "Portuguese sailors", ["British soldiers", "French teachers", "German farmers"], "About 1472."),
       N("application", "How many years are there between 1916 and 1960?", 1960 - 1916, "1960 − 1916 = 44."),
       M("application", "Which country ruled Kamerun from 1884?", "Germany", ["Portugal", "Spain", "Nigeria"], "German protectorate."),
       TF("approfondissement", "Cameroon became independent in 1960 (French Cameroun).", True, "1 January 1960."),
       MA("approfondissement", "Match each date with the event.", [("about 1472", "Portuguese reach the Wouri"), ("1884", "Germany takes over Kamerun"), ("1916", "Britain and France divide the land"), ("1960", "French Cameroun becomes independent")], "Learn the order."),
       P("A teacher draws a timeline on the board with 1472, 1884, 1916 and 1960.",
         [PM("Which is the earliest date?", "1472", ["1884", "1916", "1960"], "Smallest number."),
          PN("How many years from 1472 to 1884?", 412, "1884 − 1472 = 412.", unit="years"),
          PM("Which date is nearest to today?", "1960", ["1472", "1884", "1916"], "Largest number.", points=2)])],
      [("Cameroon was named after the Portuguese word for...", "prawns", ["mountains", "gold", "trees"], "Camarões."),
       ("German rule began in...", "1884", ["1472", "1916", "1960"], "As a protectorate."),
       ("French Cameroun became independent in...", "1960", ["1884", "1916", "1472"], "1 January 1960."),
       ("A timeline shows...", "events in order of time", ["only places", "only people", "only money"], "From the oldest to the newest."),
       ("Britain and France shared Cameroon after...", "the First World War", ["the Second World War", "independence", "the Portuguese arrival"], "In 1916.")],
      [SENS, "Dates are those commonly taught; 1472 is approximate. The slave-trade period and political debates are deliberately not covered."])

p.write()
