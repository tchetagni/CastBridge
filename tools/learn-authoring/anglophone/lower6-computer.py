"""Generates lower6-computer (Computer Science — Lower Sixth). Pseudocode style: Python-like (stated in lessons)."""
import sys, sqlite3, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _cs import *

REF = "Cameroon GCE Board — Advanced Level Computer Science syllabus (Lower Sixth part) — to be checked against the official text"
p = Pack("lower6-computer", "Computer Science — Lower Sixth", level="Lower Sixth", subject="sciences", cursus="secondary",
         wanted="computer-science", programRef=REF,
         description="Lower Sixth Computer Science: architecture, memory, data representation, Boolean logic, data structures, searching and sorting, programming paradigms, relational databases and SQL, network protocols, operating systems and ethics.")
PSEUDO = "Pseudocode in this pack is Python-like: indentation shows blocks, `for i in range(1, 6)` runs i = 1 to 5."

def tb(n, w=8): return bin(n)[2:].zfill(w)
def twos(n, w=8): return bin(n & (2 ** w - 1))[2:].zfill(w)

# ====================================================================== CH1 architecture
c1 = p.chapter("architecture", "Computer architecture and memory", REF)

lesson(c1, "cpu-fde", "The CPU and the fetch-decode-execute cycle",
  ["Name the components of the CPU and their roles", "Describe the buses", "Describe the fetch-decode-execute cycle step by step", "Explain how clock speed, cores and cache affect performance"],
  [("definition", "Inside the CPU", "The **ALU** (arithmetic logic unit) performs calculations and comparisons. The **control unit (CU)** decodes instructions and coordinates the other parts. **Registers** are very fast storage inside the CPU: the **program counter (PC)** holds the address of the next instruction; the **memory address register (MAR)** holds the address being accessed; the **memory data register (MDR)** holds data just read or to be written; the **current instruction register (CIR)** holds the instruction being decoded; the **accumulator (ACC)** holds results."),
   ("definition", "Buses", "A **bus** is a set of wires carrying signals. The **address bus** carries memory addresses (one way, from the CPU). The **data bus** carries data and instructions (both ways). The **control bus** carries control signals such as read and write."),
   ("methode", "Fetch-decode-execute", "**Fetch**: the address in the PC is copied to the MAR; the instruction at that address travels to the MDR; it is copied to the CIR; the PC is incremented. **Decode**: the CU interprets the instruction in the CIR. **Execute**: the instruction is carried out (for example the ALU adds, or data is moved). Then the cycle repeats."),
   ("retenir", "Performance", "Higher **clock speed** (GHz) means more cycles per second. More **cores** allow several instructions to be processed in parallel (when the software can use them). **Cache** is small, fast memory close to the CPU that holds frequently used data. Performance depends on all of these together, not on clock speed alone."),
   ("pieges", "Common mistakes", "- The PC holds an address, not the instruction itself.\n- The PC is incremented during the fetch stage, before execution.\n- Doubling cores does not always double speed.\n- Registers are in the CPU; RAM is outside it.")],
  ("CPU diagram", shapes([T(210, 20, "Simplified CPU and memory", 15, bold=True),
     RECT(20, 40, 270, 150, fill="lightyellow", stroke="ink"), T(155, 56, "CPU", 13, bold=True),
     RECT(35, 70, 60, 30, fill="lightblue", radius=4), T(65, 90, "PC", 13), RECT(105, 70, 60, 30, fill="lightblue", radius=4), T(135, 90, "MAR", 13), RECT(175, 70, 60, 30, fill="lightblue", radius=4), T(205, 90, "MDR", 13),
     RECT(35, 110, 60, 30, fill="lightblue", radius=4), T(65, 130, "CIR", 13), RECT(105, 110, 60, 30, fill="lightblue", radius=4), T(135, 130, "ACC", 13),
     RECT(35, 150, 90, 30, fill="lightgreen", radius=4), T(80, 170, "CU", 13), RECT(135, 150, 90, 30, fill="lightgreen", radius=4), T(180, 170, "ALU", 13),
     RECT(330, 80, 80, 80, fill="lightorange", radius=6), T(370, 125, "Memory", 13),
     LINE(290, 95, 330, 95, arrow="both", width=2), LINE(290, 120, 330, 120, arrow="both", width=2), T(310, 70, "buses", 12)], 420, 205),
   "The CPU with its registers PC, MAR, MDR, CIR and ACC, the control unit and ALU, joined by buses to memory.", "Block diagram of a CPU containing registers, control unit and ALU connected to memory by buses."),
  [("Example 1 - One fetch", "PC = 100 and memory address 100 contains the instruction ADD 5. Describe the fetch stage.", ["PC (100) is copied to MAR. The contents of address 100 (ADD 5) are read into MDR and then copied to CIR.", "The PC is incremented to 101, ready for the next fetch."], "CIR = ADD 5, PC = 101"),
   ("Example 2 - Performance", "Computer A: 2 GHz, 2 cores. Computer B: 3 GHz, 1 core. Which is certain to be faster for a program that uses only one core?", ["A program using one core cannot use the second core of A.", "Then clock speed matters more: B at 3 GHz is likely faster (other factors such as cache and design also count)."], "B is likely faster for a single-core program.")],
  [M("Which register holds the address of the next instruction to be fetched?", "Program counter", ["Accumulator", "MDR", "CIR"], "The PC points to the next instruction."),
   M("The address bus carries", "memory addresses", ["only results", "only power", "pictures"], "Addresses travel from the CPU to memory."),
   TF("The control unit performs arithmetic.", False, "Arithmetic is done by the ALU; the CU controls and decodes.")],
  [MT("Match each register to its role.", [("PC", "Address of next instruction"), ("MAR", "Address being accessed in memory"), ("MDR", "Data read from or written to memory"), ("ACC", "Stores results of the ALU")], "These are the standard simplified-model roles."),
   O("Explain why the PC is incremented during the fetch stage.", "So that it already points to the next instruction when the current one has been fetched; after decoding and executing, the cycle can fetch the following instruction without waiting.", "1 mark: points to next instruction; 1 mark: done during fetch before execution; 1 mark: clear link to cycle.")],
  P("A program has an instruction ADD 7 at address 200. The accumulator holds 5 and the PC holds 200.",
    [pm("After the fetch stage, the PC holds", "201", ["200", "7", "12"], "PC incremented to 201."),
     pn("After execution of ADD 7 the ACC holds", 12, "5 + 7 = 12."),
     po("Name the part that carries out the addition and the part that decodes the instruction.", "ALU adds; the control unit decodes.", "1 mark each.")]),
  [("The ALU performs", "arithmetic and logic operations", ["fetching only", "storage of files", "printing"], "ALU."),
   ("Cache memory is", "small fast memory near the CPU", ["large slow storage", "a type of printer", "part of ROM only"], "Cache."),
   ("The data bus is", "bidirectional", ["one way from memory only", "used for power", "used for sound only"], "Data goes both ways."),
   ("Clock speed is measured in", "hertz (GHz)", ["bytes", "pixels", "watts only"], "Cycles per second."),
   ("The step after fetch is", "decode", ["print", "format", "delete"], "Fetch-decode-execute.")],
  notes=["Simplified Von Neumann model with named registers; the syllabus may use slightly different names (e.g. IR instead of CIR). Check."], minutes=30)

lesson(c1, "memory-storage", "Memory and storage",
  ["Compare RAM, ROM and cache", "Compare magnetic, optical and solid-state storage", "Explain virtual memory", "Choose suitable storage for a task"],
  [("definition", "Primary memory", "**RAM** is read/write, **volatile** and holds the programs and data in use. **ROM** is read-only and **non-volatile**; it holds start-up instructions (the boot program). **Cache** is faster than RAM and sits between the CPU and RAM. The faster a memory is, the more expensive it is per byte and the smaller it tends to be."),
   ("definition", "Secondary storage", "**Magnetic** (hard disk): large capacity, cheap, has moving parts. **Optical** (CD/DVD/Blu-ray): data read with a laser, used for distribution and archives. **Solid-state** (SSD, flash drive, memory card): no moving parts, fast, quiet, more robust, costlier per byte."),
   ("retenir", "Virtual memory", "When RAM is full, the operating system may move some data from RAM to a reserved area of the disk: **virtual memory**. This lets more programs run, but disk is much slower than RAM, so the computer slows down if it happens too much (**thrashing**)."),
   ("pieges", "Common mistakes", "- 'Memory' normally means RAM; 'storage' means disks.\n- ROM is not 'secondary storage'.\n- Virtual memory is not extra RAM chips.\n- Choose storage by capacity, speed, cost, portability and durability, not just one of them.")],
  ("Hierarchy", cols("Memory and storage: speed and size", ["Type", "Speed", "Size"], [["Registers", "Fastest", "Tiny"], ["Cache", "Very fast", "Small"], ["RAM", "Fast", "Medium"], ["SSD / disk", "Slower", "Large"]]),
   "A table ranking registers, cache, RAM and disks by speed and size.", "Table of the memory hierarchy: registers fastest and tiny, cache very fast and small, RAM fast and medium, disks slower and large."),
  [("Example 1", "A school server in Bafoussam stores 5 years of reports and needs large capacity at low cost. Choose a storage type.", ["Large capacity at low cost points to magnetic hard disks.", "An SSD would be faster but costs more per byte; the extra speed is not needed for archive reports."], "Magnetic hard disk (with a backup)."),
   ("Example 2", "A computer with 4 GB of RAM becomes very slow when many programs are open and the disk light flashes. Explain.", ["RAM is full so the OS moves data to virtual memory on the disk.", "Disk access is much slower than RAM, so the computer slows down."], "Heavy use of virtual memory (thrashing).")],
  [M("Which memory is non-volatile?", "ROM", ["RAM", "Cache", "Registers"], "ROM keeps its contents without power."),
   M("A storage type with no moving parts is", "solid-state", ["magnetic hard disk", "optical disc", "tape"], "SSDs have no moving parts."),
   TF("Virtual memory is stored on the secondary storage.", True, "A reserved area of the disk is used.")],
  [M("Why is cache faster to use than RAM for the CPU?", "It is smaller and placed closer to the CPU", ["It is larger", "It is a disk", "It is optical"], "Proximity and design give speed."),
   O("Compare magnetic and solid-state storage using three criteria.", "Magnetic: cheaper per byte, large capacity, moving parts so slower and more fragile. Solid-state: no moving parts, faster, silent, robust, more expensive per byte.", "1 mark per correct comparison criterion (3).")],
  P("A photographer in Limbe needs to carry 200 GB of photographs between towns.",
    [pm("Which medium is the most suitable?", "A solid-state external drive", ["ROM", "Cache", "A floppy disk"], "Portable, robust, high capacity."),
     pm("Why is RAM not suitable for this purpose?", "It is volatile", ["It is optical", "It is read-only", "It is too cheap"], "RAM loses data without power."),
     po("Suggest one reason for also keeping a second copy.", "Drives can fail, be lost or stolen; a backup prevents loss.", "1 mark.")]),
  [("RAM is", "volatile read/write memory", ["read-only and permanent", "optical", "magnetic"], "RAM."),
   ("The boot program is normally in", "ROM", ["RAM", "cache", "registers"], "ROM."),
   ("Optical discs are read using a", "laser", ["magnet", "keyboard", "printer"], "Laser."),
   ("Thrashing means", "too much swapping between RAM and disk", ["too many cores", "a virus", "data deleted"], "Slows the system."),
   ("Registers are", "inside the CPU", ["on the disk", "optical", "on the network"], "CPU registers.")],
  notes=["Cache levels (L1/L2/L3) deliberately omitted. Cost/speed statements are relative, not figures."], minutes=25)

# ====================================================================== CH2 data & logic
c2 = p.chapter("data", "Data representation and logic", REF)
assert twos(-45) == "11010011" and int("B7", 16) == 183 and tb(183) == "10110111"
assert -128 <= -128 and twos(-128) == "10000000" and twos(127) == "01111111"

lesson(c2, "data-representation", "Number systems, negative numbers and character sets",
  ["Convert between binary, denary and hexadecimal", "Represent negative integers in two's complement", "State the range of an n-bit number", "Explain ASCII and Unicode"],
  [("definition", "Bases and ranges", "Place values in binary are powers of 2. An n-bit **unsigned** integer ranges from 0 to 2ⁿ - 1 (8 bits: 0 to 255). Hexadecimal groups four bits: 1011 0111 = B7 = 11 x 16 + 7 = 183. Use hex to write long binary numbers compactly."),
   ("methode", "Two's complement", "In n-bit two's complement the leftmost bit has a **negative** weight (-128 in 8 bits). To negate a number: flip all bits and add 1. -45: 45 = 00101101, flipped 11010010, plus 1 gives **11010011**. Range for 8 bits: -128 to +127. Subtraction is addition of the negative."),
   ("retenir", "Characters", "Characters are stored as numeric codes. **ASCII** is a 7-bit code (128 characters: letters, digits, punctuation, control codes). **Unicode** extends this to represent most of the world's writing systems (including accented letters used in French and African-language scripts). Characters in a text file are stored as a sequence of codes."),
   ("pieges", "Common mistakes", "- 8-bit two's complement cannot store +128.\n- 11010011 as unsigned is 211, as two's complement is -45: the meaning depends on the interpretation.\n- Adding 1 after flipping: forgetting it gives the one's complement, not the negative.\n- ASCII 'A' (65) is not the digit 1.")],
  ("Bit weights", boxrow("Two's complement weights: 11010011 = -45", ["-128", "64", "32", "16", "8", "4", "2", "1"], sub=["1", "1", "0", "1", "0", "0", "1", "1"], colors=["lightorange", "lightgrey", "lightgrey", "lightgrey", "lightgrey", "lightgrey", "lightgrey", "lightgrey"], size=12),
   "Eight boxes with weights -128, 64, 32, 16, 8, 4, 2, 1 above the bits 11010011.", "Bit weights for an 8-bit two's complement number, the leftmost weight being -128."),
  [("Example 1 - Negative number", "Write -45 in 8-bit two's complement and check.", ["45 = 00101101. Flip: 11010010. Add 1: 11010011.", "Check weights: -128 + 64 + 16 + 2 + 1 = -128 + 83 = -45."], "11010011"),
   ("Example 2 - Subtraction", "Compute 20 - 35 in 8-bit two's complement.", ["20 = 00010100. -35: 35 = 00100011, flip 11011100, +1 = 11011101.", "Add: 00010100 + 11011101 = 11110001. Weights: -128+64+32+16+1 = -15."], "11110001 = -15")],
  [N("The range of unsigned 8-bit integers has largest value", 255, "2^8 - 1 = 255."),
   N("Convert B7 (hex) to denary.", 183, "11 x 16 + 7 = 183."),
   TF("In 8-bit two's complement the number +128 can be stored.", False, "The largest is +127.")],
  [N("The smallest 8-bit two's complement number is", -128, "10000000 = -128."),
   O("Explain the difference between ASCII and Unicode.", "ASCII uses 7 bits for 128 characters (mainly English letters, digits and symbols). Unicode uses more bits per character and covers many languages and symbols, so it can represent accented and African-language characters.", "1 mark per correct statement (3).")],
  P("Two's complement with 8 bits.",
    [pm("-1 is stored as", "11111111", ["10000001", "00000001", "11111110"], "Flip 00000001 = 11111110, add 1 = 11111111."),
     pn("The denary value of 11100000 in two's complement", -32, "-128 + 64 + 32 = -32."),
     pm("Which number cannot be stored in 8 bits (two's complement)?", "200", ["-100", "100", "-128"], "Max is 127.")]),
  [("An 8-bit unsigned integer can store up to", "255", ["256", "127", "128"], "2^8 - 1."),
   ("Hex 1F equals", "31", ["15", "16", "32"], "16 + 15."),
   ("To negate a two's complement number we", "flip the bits and add 1", ["only flip the bits", "add 1 then flip", "subtract 1 only"], "Method."),
   ("Unicode is used to", "represent many writing systems", ["store only digits", "draw pictures", "control the CPU"], "Wider than ASCII."),
   ("The leftmost bit of an 8-bit two's complement number has weight", "-128", ["+128", "-64", "0"], "Negative weight.")],
  notes=["Verified in Python (two's complement 8-bit). Syllabus may also expect sign-and-magnitude: not covered."], minutes=30)

lesson(c2, "boolean-logic", "Boolean algebra and logic gates",
  ["Describe NOT, AND, OR, NAND, NOR and XOR", "Complete truth tables", "Apply De Morgan's laws", "Write and interpret a logic expression"],
  [("definition", "Gates", "A **logic gate** takes one or more binary inputs and gives one output. **NOT**: output is the opposite of the input. **AND**: 1 only if all inputs are 1. **OR**: 1 if at least one input is 1. **NAND** = NOT AND, **NOR** = NOT OR. **XOR**: 1 if the inputs are different."),
   ("retenir", "Truth tables", "A **truth table** lists every input combination and the output. For two inputs there are 2² = 4 rows; for n inputs, 2ⁿ rows. Example: A AND B is 1 only in the row A=1, B=1; A OR B is 0 only in the row A=0, B=0."),
   ("retenir", "Laws", "**De Morgan**: NOT(A AND B) = (NOT A) OR (NOT B), and NOT(A OR B) = (NOT A) AND (NOT B). Other identities: A AND 1 = A; A OR 0 = A; A AND 0 = 0; A OR 1 = 1; A AND A = A; A OR (NOT A) = 1; NOT(NOT A) = A."),
   ("pieges", "Common mistakes", "- Confusing XOR (different inputs) with OR (at least one).\n- Forgetting to include all 2ⁿ rows.\n- Misapplying De Morgan: the operator changes **and** each term is negated.\n- Writing NAND as NOT A AND NOT B (that is NOR).")],
  ("AND gate", (lambda g: shapes([T(210, 20, "Q = A AND B", 15, bold=True), T(30, 80, "A", 14), T(30, 130, "B", 14), LINE(45, 76, 130, 76), LINE(45, 126, 130, 126), LINE(130, 76, 130, 90), LINE(130, 126, 130, 112)] +
       [PATH("M130 70 H170 A32 32 0 0 1 170 134 H130 Z", fill="lightyellow"), LINE(202, 102, 300, 102), T(320, 108, "Q", 14), T(166, 108, "AND", 13)], 420, 160))(None),
   "The symbol of an AND gate with inputs A and B and output Q.", "AND gate symbol: two inputs A and B on the left, output Q on the right."),
  [("Example 1 - Truth table", "Complete the truth table of Q = NOT(A AND B).", ["A AND B is 1 only for A=1,B=1, so Q = 0 there.", "Rows (A,B): (0,0)->1, (0,1)->1, (1,0)->1, (1,1)->0. This is NAND."], "1, 1, 1, 0"),
   ("Example 2 - De Morgan", "Show that NOT(A OR B) equals (NOT A) AND (NOT B) when A = 1, B = 0.", ["A OR B = 1, so NOT(A OR B) = 0.", "NOT A = 0, NOT B = 1, and 0 AND 1 = 0. Both sides are 0."], "Both sides give 0.")],
  [M("The output of an AND gate is 1 when", "all inputs are 1", ["any input is 1", "all inputs are 0", "inputs differ"], "AND definition."),
   N("How many rows in the truth table of a 3-input circuit?", 8, "2^3 = 8."),
   TF("XOR gives 1 when its two inputs are the same.", False, "XOR gives 1 when they are different.")],
  [M("NOT(A OR B) is equivalent to", "NOT A AND NOT B", ["NOT A OR NOT B", "A AND B", "A OR B"], "De Morgan."),
   O("Draw (describe) the truth table of Q = A XOR B.", "A=0,B=0 -> 0; A=0,B=1 -> 1; A=1,B=0 -> 1; A=1,B=1 -> 0.", "1 mark per two correct rows (2 marks).")],
  P("Q = (A AND B) OR (NOT C).",
    [pn("Q when A=1, B=1, C=1", 1, "(1 AND 1) = 1, so Q = 1 OR 0 = 1."),
     pn("Q when A=0, B=1, C=1", 0, "(0 AND 1) = 0 and NOT 1 = 0, so Q = 0."),
     po("How many rows does the full truth table have?", "8 rows (three inputs, 2^3).", "1 mark each.")]),
  [("NOT 1 equals", "0", ["1", "2", "10"], "NOT inverts."),
   ("A OR B is 0 only when", "both inputs are 0", ["both inputs are 1", "A is 1", "B is 1"], "OR."),
   ("NAND is", "NOT AND", ["AND NOT", "OR", "XOR"], "Definition."),
   ("A AND 0 equals", "0", ["A", "1", "NOT A"], "Identity."),
   ("A OR (NOT A) equals", "1", ["0", "A", "NOT A"], "Always true.")],
  notes=["Truth tables checked in Python (De Morgan verified for all inputs). Gate diagram drawn with a path."], minutes=30)
for a in (0, 1):
    for b in (0, 1):
        assert (not (a and b)) == ((not a) or (not b)) and (not (a or b)) == ((not a) and (not b))

# ====================================================================== CH3 data structures & algorithms
c3 = p.chapter("ds-algorithms", "Data structures and algorithms", REF)

lesson(c3, "arrays-stacks-queues", "Arrays, stacks and queues",
  ["Declare and use one- and two-dimensional arrays", "Describe a stack and its operations", "Describe a queue and a circular queue", "Choose a structure for a task"],
  [("definition", "Arrays", "An **array** is a fixed-size collection of items of the same type, accessed by an **index**. In this pack indexes start at 0, as in most languages (a 5-item array has indexes 0 to 4). A **two-dimensional array** is a table: grid[row][col]. " + PSEUDO),
   ("definition", "Stack", "A **stack** is **LIFO** (last in, first out). **push(x)** adds on top; **pop()** removes and returns the top item; **peek()** reads the top without removing. Uses: undo, the call stack of subroutines, reversing a sequence, checking brackets. Popping an empty stack is an **underflow**; pushing onto a full one is an **overflow**."),
   ("definition", "Queue", "A **queue** is **FIFO** (first in, first out). **enqueue(x)** adds at the rear; **dequeue()** removes from the front. Uses: print queues, keyboard buffers, scheduling. A **circular queue** reuses space by wrapping the rear pointer back to the start of the array (using MOD)."),
   ("pieges", "Common mistakes", "- Off-by-one: index 5 does not exist in a 5-item array.\n- Mixing up LIFO and FIFO.\n- Forgetting to check for empty/full before pop/dequeue/push.\n- A queue's front and rear move; the array itself is not shifted in a circular queue.")],
  ("Stack", shapes([T(210, 20, "Stack (LIFO)", 15, bold=True), RECT(150, 130, 120, 30, fill="lightblue"), T(210, 150, "12", 14), RECT(150, 100, 120, 30, fill="lightblue"), T(210, 120, "7", 14),
     RECT(150, 70, 120, 30, fill="lightgreen"), T(210, 90, "3", 14), T(330, 90, "Top", 13), LINE(305, 86, 275, 86, arrow="end"), T(80, 60, "push", 13), LINE(80, 66, 80, 80, arrow="end", color="red"), LINE(80, 80, 148, 84, color="red", arrow="end")], 420, 175),
   "A stack with 12 at the bottom, then 7, with 3 on top.", "Stack diagram: items 12, 7 and 3, the top item being 3."),
  [("Example 1 - Stack", "Start with an empty stack. push(4), push(9), pop(), push(2), pop(). What is on the stack and what values were popped?", ["push 4: [4]; push 9: [4, 9]; pop returns 9: [4]; push 2: [4, 2]; pop returns 2: [4].", "Popped values: 9 then 2. The stack holds [4]."], "Stack [4]; popped 9 and 2"),
   ("Example 2 - Queue", "Empty queue: enqueue A, B, C; dequeue; enqueue D. List the queue front to rear.", ["After A, B, C: front A, B, C rear. dequeue removes A: B, C.", "enqueue D: B, C, D."], "B, C, D")],
  [M("A stack follows", "LIFO", ["FIFO", "FILO only for queues", "random order"], "Last in, first out."),
   M("Which operation removes the item at the front of a queue?", "dequeue", ["push", "pop", "peek"], "Dequeue."),
   N("An array has 8 items indexed from 0. What is the last index?", 7, "Indexes 0 to 7.")],
  [N("Stack: push 5, push 8, push 1, pop, pop. What is on top now?", 5, "After two pops only 5 remains."),
   O("Explain why a circular queue is better than shifting all items forward after each dequeue.", "Shifting needs many moves for each dequeue so it is slow; in a circular queue only the front pointer moves and the rear pointer wraps around, reusing the empty space efficiently.", "1 mark each correct point (3).")],
  P("A circular queue uses an array of size 5 (indexes 0-4). The rear pointer is at index 4 and a new item is added.",
    [pn("The new rear index is (rear + 1) MOD 5 = ", 0, "(4 + 1) MOD 5 = 0.", ),
     pm("The pointer 'wraps around' because of", "the MOD operator", ["the stack", "a router", "ROM"], "MOD wraps to 0."),
     po("Name one use of a queue and one use of a stack.", "Queue: print jobs. Stack: undo operations or the call stack.", "1 mark each.")]),
  [("push adds an item to the", "top of the stack", ["bottom of a queue", "rear of a queue", "middle"], "Push."),
   ("An underflow occurs when", "you pop from an empty stack", ["you push", "you peek twice", "the array is sorted"], "Underflow."),
   ("In a queue the first item added is", "the first removed", ["the last removed", "never removed", "removed by peek"], "FIFO."),
   ("A 2D array can represent", "a table of rows and columns", ["a single number", "only text", "a network"], "Grid."),
   ("The call stack is used for", "keeping track of subroutine calls", ["storing photos", "drawing", "printing"], "Stack use.")],
  notes=["Indexing from 0 chosen; some Board papers use 1. State convention in exam answers. " + PSEUDO], minutes=30)
st = []; st.append(4); st.append(9); a = st.pop(); st.append(2); b = st.pop(); assert (a, b, st) == (9, 2, [4])
assert (4 + 1) % 5 == 0

def bubble(a):
    a = list(a); passes = 0; swaps = 0
    while True:
        passes += 1; sw = 0
        for i in range(len(a) - 1):
            if a[i] > a[i + 1]: a[i], a[i + 1] = a[i + 1], a[i]; sw += 1
        swaps += sw
        if sw == 0: break
    return a, passes, swaps
assert bubble([5, 1, 4, 2, 8]) == ([1, 2, 4, 5, 8], 3, 4)
def bsearch(a, t):
    lo, hi, steps = 0, len(a) - 1, 0
    while lo <= hi:
        steps += 1; mid = (lo + hi) // 2
        if a[mid] == t: return mid, steps
        if a[mid] < t: lo = mid + 1
        else: hi = mid - 1
    return -1, steps
assert bsearch([3, 8, 12, 17, 23, 31, 40], 23) == (4, 3)
assert math.floor(math.log2(1000)) + 1 == 10 and math.floor(math.log2(1024)) + 1 == 11

lesson(c3, "searching-sorting", "Searching and sorting algorithms",
  ["Perform linear and binary search", "State when binary search can be used", "Perform bubble sort and insertion sort", "Compare algorithms in outline (efficiency)"],
  [("definition", "Linear and binary search", "**Linear search** checks items one by one from the start; it works on any list; in the worst case it checks all n items. **Binary search** works only on a **sorted** list: compare the target with the middle item, discard the half that cannot contain it, repeat. It needs at most about log₂ n + 1 comparisons (1000 items: at most 10)."),
   ("definition", "Bubble sort", "Compare neighbouring items from the start and **swap** them if out of order; this moves the largest item to the end each pass. Repeat passes until a pass makes **no swaps**. Simple, but slow for long lists: roughly n² comparisons."),
   ("definition", "Insertion sort", "Take items one at a time and **insert** each into its correct place within the already sorted part on the left, shifting larger items right. Efficient for small or nearly sorted lists."),
   ("retenir", "Efficiency in outline", "We compare algorithms by how the number of steps grows with n. Linear search grows in proportion to n, binary search grows very slowly (about log₂ n), bubble and insertion sorts grow roughly with n². Merge sort and quick sort are faster for large lists (about n log n)."),
   ("pieges", "Common mistakes", "- Binary search on an unsorted list gives wrong answers.\n- Bubble sort stops only when a full pass has no swaps.\n- Mid index rounds down: (0 + 6) // 2 = 3.")],
  ("Bubble sort pass 1", shapes([T(210, 20, "Bubble sort: pass 1 of [5, 1, 4, 2, 8]", 14, bold=True)] +
        [x for i, v in enumerate([5, 1, 4, 2, 8]) for x in (RECT(70 + i * 56, 40, 50, 36, fill="lightblue"), T(95 + i * 56, 64, str(v), 15))] +
        [x for i, v in enumerate([1, 4, 2, 5, 8]) for x in (RECT(70 + i * 56, 120, 50, 36, fill="lightgreen"), T(95 + i * 56, 144, str(v), 15))] +
        [LINE(210, 80, 210, 116, arrow="end"), T(300, 102, "after pass 1 (3 swaps)", 12)], 420, 175),
   "A list 5 1 4 2 8 above the list 1 4 2 5 8 after the first bubble sort pass.", "Before and after of the first pass of bubble sort."),
  [("Example 1 - Binary search", "Search for 23 in [3, 8, 12, 17, 23, 31, 40] (indexes 0 to 6).", ["lo=0, hi=6, mid=3: 17 < 23, so lo=4. mid=(4+6)//2=5: 31 > 23, so hi=4.", "mid=4: 23 found at index 4 after 3 comparisons."], "Found at index 4 in 3 steps"),
   ("Example 2 - Bubble sort", "Sort [5, 1, 4, 2, 8] with bubble sort. How many passes and swaps?", ["Pass 1: 3 swaps -> [1,4,2,5,8]. Pass 2: 1 swap -> [1,2,4,5,8]. Pass 3: 0 swaps, stop.", "Total: 3 passes, 4 swaps."], "[1, 2, 4, 5, 8]; 3 passes; 4 swaps")],
  [M("Binary search needs the list to be", "sorted", ["short", "numeric only", "unique"], "Sorted order is required."),
   N("Maximum comparisons for a binary search on 1000 sorted items (log2 rule, whole number)", 10, "floor(log2 1000) + 1 = 10."),
   TF("Linear search can be used on an unsorted list.", True, "Yes.")],
  [N("How many swaps does bubble sort need to sort [3, 2, 1]? (count every swap)", 3, "[3,2,1] -> [2,3,1] -> [2,1,3] -> [1,2,3]: 3 swaps."),
   O("Explain why a 'no swaps' pass allows bubble sort to stop.", "If no neighbouring pair needed swapping then every item is already in order, so nothing more can change.", "1 mark each point (2).")],
  P("The sorted list is [2, 5, 9, 14, 20, 27, 33] (indexes 0-6). Search for 27 with binary search.",
    [pn("The first middle index", 3, "(0 + 6) // 2 = 3 (value 14)."),
     pn("The number of comparisons to find 27", 2, "mid 3 (14) < 27 so lo = 4; mid = (4+6)//2 = 5 holds 27: found, 2 comparisons."),
     po("State one advantage of binary search over linear search.", "It needs far fewer comparisons for large sorted lists.", "1 mark.")]),
  [("Insertion sort inserts each item into", "the sorted part of the list", ["a new file", "the CPU", "the cache"], "Insertion."),
   ("The worst case for linear search on n items is", "n comparisons", ["1 comparison", "log n comparisons", "0"], "All items."),
   ("The middle index of a list with indexes 0-8 is", "4", ["3", "5", "8"], "(0+8)//2."),
   ("Which sort is described as comparing neighbours and swapping?", "Bubble sort", ["Binary search", "Insertion search", "Linear search"], "Bubble."),
   ("For large lists, a faster sort is", "merge sort", ["bubble sort", "no sort", "random"], "n log n.")],
  notes=["Computed with Python: bubble sort 3 passes / 4 swaps; binary search steps. Complexity stated in outline only."], minutes=30)
# fix the problem part check: bsearch for 27
r = bsearch([2, 5, 9, 14, 20, 27, 33], 27); assert r == (5, 2)

# ====================================================================== CH4 programming
c4 = p.chapter("programming", "Programming and software", REF)

def fizz():
    out = []
    for i in range(1, 11):
        if i % 3 == 0 and i % 5 == 0: out.append("FizzBuzz")
        elif i % 3 == 0: out.append("Fizz")
        elif i % 5 == 0: out.append("Buzz")
        else: out.append(str(i))
    return out
tot = 0
for i in range(1, 6): tot += i * i
assert tot == 55
fact = 1
for i in range(1, 6): fact *= i
assert fact == 120

lesson(c4, "paradigms-programming", "Programming paradigms, translators and constructs",
  ["Compare procedural, object-oriented, functional and declarative paradigms", "Compare high- and low-level languages", "Explain compilers, interpreters and assemblers", "Trace programs using selection, iteration and subroutines"],
  [("definition", "Paradigms", "A **programming paradigm** is a style of programming. **Procedural**: a program is a sequence of steps grouped into procedures and functions (e.g. C, Pascal). **Object-oriented (OOP)**: data and the methods that act on it are combined in **objects** created from **classes** (e.g. Java, Python). **Functional**: programs are built from functions without changing state. **Declarative**: you state *what* result you want (e.g. SQL, Prolog). Many languages support several paradigms."),
   ("definition", "Levels and translators", "**Low-level** languages (machine code, assembly) are close to the hardware: fast and compact but hard to write and not portable. **High-level** languages are closer to human language, easier and portable. An **assembler** translates assembly into machine code. A **compiler** translates the whole program before it runs (fast execution, one executable). An **interpreter** translates and runs one statement at a time (easy debugging, slower execution)."),
   ("methode", "Constructs (Python-like pseudocode)", PSEUDO + " Variables are assigned with =. Selection: if cond: ... elif ...: ... else: .... Iteration: for i in range(1, 6): ... and while cond: .... Subroutines: def name(params): ... return value. Operators: // integer division, % remainder (MOD), and, or, not."),
   ("pieges", "Common mistakes", "- Confusing = (assignment) with == (comparison).\n- Forgetting that range(1, 6) stops at 5.\n- A compiler does not run the program; it translates it.\n- Naming a language as 'only' one paradigm: Python supports procedural and OOP.")],
  ("Paradigms", cols("Paradigms and examples", ["Paradigm", "Idea", "Example"], [["Procedural", "Steps", "C, Pascal"], ["OOP", "Objects", "Java"], ["Functional", "Functions", "Haskell"], ["Declarative", "What", "SQL"]], w=440, size=12),
   "A table of four programming paradigms with the idea and an example language of each.", "Table: procedural, object-oriented, functional and declarative paradigms, each with an example language."),
  [("Example 1 - Trace", "Trace: total = 0; for i in range(1, 6): total = total + i * i; print(total).", ["i = 1,2,3,4,5 gives squares 1, 4, 9, 16, 25.", "Sum: 1 + 4 + 9 + 16 + 25 = 55."], "55"),
   ("Example 2 - Subroutine", "def factorial(n): result = 1; for i in range(1, n + 1): result = result * i; return result. What is factorial(5)?", ["The loop multiplies 1 x 2 x 3 x 4 x 5.", "= 120."], "120")],
  [M("Which translator converts a whole program before it runs?", "Compiler", ["Interpreter", "Assembler only for Python", "Browser"], "A compiler translates the whole program."),
   M("SQL is mainly an example of which paradigm?", "Declarative", ["Procedural", "Functional only", "Machine code"], "You state what you want."),
   TF("Assembly language is a high-level language.", False, "Assembly is low-level.")],
  [N("Value printed: x = 7; y = x // 2; z = x % 2; print(y + z). (// is integer division)", 4, "7 // 2 = 3 and 7 % 2 = 1, so 3 + 1 = 4."),
   O("Give two advantages of high-level languages over low-level languages.", "They are easier to read and write; programs are portable between different computers; fewer errors and faster development.", "1 mark each, 2 marks.")],
  P("def f(n): count = 0; for i in range(1, n + 1): if i % 2 == 0: count = count + 1; return count",
    [pn("f(10)", 5, "Even numbers 2, 4, 6, 8, 10."),
     pn("f(7)", 3, "Even numbers 2, 4, 6."),
     po("What does the function f compute, in words?", "It counts how many even numbers there are from 1 to n.", "1 mark each part.")]),
  [("An interpreter", "translates and runs statements one at a time", ["translates the whole program once and stops", "stores files", "draws gates"], "Interpreter."),
   ("OOP organises code into", "objects and classes", ["only tables", "only gates", "only files"], "OOP."),
   ("In pseudocode, % usually gives the", "remainder", ["percentage only", "product", "quotient"], "Modulo."),
   ("range(1, 4) generates", "1, 2, 3", ["1, 2, 3, 4", "0, 1, 2, 3", "4"], "End excluded."),
   ("A function that calls itself is", "recursive", ["compiled", "static", "volatile"], "Recursion.")],
  notes=["Pseudocode is Python-like, not an exam-specific style; check the Board's pseudocode guide. Language examples are common ones, not a syllabus list."], minutes=30)
assert [str(x) for x in fizz()][:3] == ["1", "2", "Fizz"] and (7 // 2) + (7 % 2) == 4
assert sum(1 for i in range(1, 11) if i % 2 == 0) == 5 and sum(1 for i in range(1, 8) if i % 2 == 0) == 3

# ====================================================================== CH5 databases
c5 = p.chapter("databases", "Databases", REF)
db = sqlite3.connect(":memory:")
db.executescript("""CREATE TABLE Student(ID INTEGER PRIMARY KEY, Name TEXT, Town TEXT, Mark INTEGER);
INSERT INTO Student VALUES (1,'Ada','Buea',72),(2,'Paul','Limbe',48),(3,'Nina','Buea',55),(4,'Joel','Kumba',63),(5,'Mary','Buea',41);
CREATE TABLE Class(CID TEXT PRIMARY KEY, Teacher TEXT);""")
q1 = db.execute("SELECT Name FROM Student WHERE Town='Buea' AND Mark>=50 ORDER BY Mark DESC").fetchall()
assert q1 == [("Ada",), ("Nina",)]
q2 = db.execute("SELECT AVG(Mark) FROM Student").fetchone()[0]; assert q2 == (72 + 48 + 55 + 63 + 41) / 5 == 55.8
q3 = db.execute("SELECT COUNT(*) FROM Student WHERE Mark < 50").fetchone()[0]; assert q3 == 2
q4 = db.execute("SELECT Town, COUNT(*) FROM Student GROUP BY Town ORDER BY Town").fetchall(); assert q4 == [("Buea", 3), ("Kumba", 1), ("Limbe", 1)]

lesson(c5, "relational-sql", "Relational databases and SQL",
  ["Define table, record, field, primary key and foreign key", "Explain why relational databases reduce duplication", "Write SELECT, INSERT, UPDATE and DELETE statements", "Use WHERE, ORDER BY, COUNT, AVG and GROUP BY"],
  [("definition", "Relational model", "A **relational database** stores data in **tables** (relations). Each **record** (row) is about one entity; each **field** (column) holds one attribute. A **primary key** uniquely identifies a record. A **foreign key** is a field in one table that holds the primary key of another table, linking the two. Splitting data into related tables avoids repeating the same data many times and keeps it consistent."),
   ("methode", "Querying with SQL", "SQL (Structured Query Language) is declarative. **SELECT** columns **FROM** a table **WHERE** a condition **ORDER BY** a column (ASC or DESC). Text values are written in quotes. Functions: COUNT(*), SUM(), AVG(), MAX(), MIN(). **GROUP BY** groups rows to apply a function to each group."),
   ("methode", "Changing data", "INSERT INTO Student VALUES (6, 'Eve', 'Bali', 59); UPDATE Student SET Mark = 60 WHERE ID = 6; DELETE FROM Student WHERE ID = 6; Always include a WHERE in UPDATE and DELETE, or **every** row is changed or deleted."),
   ("pieges", "Common mistakes", "- UPDATE or DELETE without WHERE affects all rows.\n- Using = on a NULL value: use IS NULL.\n- A foreign key must refer to an existing primary key value.\n- Text comparisons are case/format sensitive in many systems: 'Buea' and 'buea' may differ.")],
  ("Table", cols("Student table", ["ID", "Name", "Town", "Mark"], [["1", "Ada", "Buea", "72"], ["2", "Paul", "Limbe", "48"], ["3", "Nina", "Buea", "55"], ["4", "Joel", "Kumba", "63"], ["5", "Mary", "Buea", "41"]]),
   "A student table of five records with ID, Name, Town and Mark.", "Table of five students with ID, name, town and mark."),
  [("Example 1 - Query", "Write SQL to list the names of students in Buea with Mark at least 50, highest mark first, using the table above.", ["SELECT Name FROM Student WHERE Town = 'Buea' AND Mark >= 50 ORDER BY Mark DESC;", "Buea students: Ada 72, Nina 55, Mary 41. Mark >= 50 keeps Ada and Nina; order: Ada, Nina."], "Ada, Nina"),
   ("Example 2 - Aggregate", "What do SELECT AVG(Mark) FROM Student; and SELECT COUNT(*) FROM Student WHERE Mark < 50; return?", ["AVG: (72 + 48 + 55 + 63 + 41) / 5 = 279 / 5 = 55.8.", "Marks below 50: Paul (48) and Mary (41), so COUNT = 2."], "55.8 and 2")],
  [M("A field that links to the primary key of another table is a", "foreign key", ["candidate sort", "query", "report"], "Foreign key."),
   N("SELECT COUNT(*) FROM Student WHERE Town = 'Buea' returns", 3, "Ada, Nina and Mary."),
   TF("UPDATE without a WHERE clause changes every record.", True, "Yes.")],
  [N("SELECT AVG(Mark) FROM Student WHERE Town = 'Buea' returns (to 1 decimal place)", 56, "(72 + 55 + 41) / 3 = 168 / 3 = 56.", tol=0.05),
   O("Explain two advantages of using two related tables (e.g. Student and Class) instead of one big table.", "Less repeated data (e.g. teacher name stored once); easier updates because a change is made in one place; fewer inconsistencies.", "1 mark each, 2 marks plus 1 for example.")],
  P("Student table as above.",
    [pm("Which SQL lists all names alphabetically?", "SELECT Name FROM Student ORDER BY Name;", ["SELECT Name ORDER Student;", "LIST Name FROM Student;", "SELECT * ORDER Name;"], "Standard form."),
     pn("How many rows does SELECT * FROM Student WHERE Mark > 50 return?", 3, "Ada 72, Nina 55, Joel 63."),
     po("Write SQL to increase Paul's mark to 50.", "UPDATE Student SET Mark = 50 WHERE Name = 'Paul';", "1 mark UPDATE/SET, 1 mark WHERE.")]),
  [("A primary key", "uniquely identifies each record", ["is a password", "repeats", "is a query"], "Unique."),
   ("SELECT is used to", "retrieve data", ["delete a table only", "draw gates", "set a password"], "Query."),
   ("GROUP BY is used to", "group rows for aggregate functions", ["sort items alphabetically only", "create folders", "format text"], "Grouping."),
   ("DELETE FROM Student WHERE ID = 3 removes", "one record", ["all records", "the table", "the database"], "WHERE limits."),
   ("SQL is an example of", "a declarative language", ["machine code", "an assembler", "a mouse"], "Declarative.")],
  notes=["All SQL here was run in SQLite to check the results. Dialect differences exist (e.g. quoting); the Board's exact SQL subset must be checked."], minutes=30)

# ====================================================================== CH6 networks & OS
c6 = p.chapter("networks-os", "Networks, operating systems and society", REF)
assert 2 ** 32 == 4294967296

lesson(c6, "networks-protocols", "Networks and protocols",
  ["Compare LAN/WAN and star, bus and mesh topologies", "Explain packet switching", "Describe the TCP/IP layers and common protocols", "Explain IP addresses, DNS and URLs"],
  [("definition", "Network types", "A **LAN** covers one site; a **WAN** links sites over long distances (the Internet is a WAN). **Star**: each device to a central switch (easy to extend; the switch is a single point of failure). **Bus**: one shared cable (cheap; collisions, one break stops all). **Mesh**: many direct links (reliable, expensive). Wireless networks use **access points**."),
   ("definition", "Packet switching", "Data is split into **packets**, each with a header (source and destination address, sequence number). Packets may travel by different routes through **routers** and are **reassembled** in order at the destination. Advantages: efficient use of links and resilience if a path fails."),
   ("definition", "TCP/IP and protocols", "A **protocol** is an agreed set of rules. The TCP/IP model has four layers: **Application** (HTTP/HTTPS for web, SMTP/POP3/IMAP for email, FTP for files, DNS), **Transport** (TCP: reliable, ordered delivery; UDP: faster, no guarantee), **Internet** (IP: addressing and routing) and **Link** (Ethernet, Wi-Fi). Each layer uses services of the one below."),
   ("retenir", "Addresses", "An **IPv4 address** has 32 bits, written as four numbers 0-255 (e.g. 192.168.1.10): 2³² = 4 294 967 296 possible addresses. IPv6 uses 128 bits. **DNS** translates a domain name (example.com) into an IP address. A **URL** has a protocol, a domain and a path: https://example.com/page."),
   ("pieges", "Common mistakes", "- HTTP and HTTPS are not the same: HTTPS is encrypted.\n- Packets may arrive out of order; that is why they carry sequence numbers.\n- An IP address is not the same as a MAC address (hardware address).\n- Mbps is megabits, not megabytes, per second.")],
  ("TCP/IP layers", cols("TCP/IP layers", ["Layer", "Example"], [["Application", "HTTP, SMTP, DNS"], ["Transport", "TCP, UDP"], ["Internet", "IP"], ["Link", "Ethernet, Wi-Fi"]]),
   "A table of the four TCP/IP layers with example protocols.", "TCP/IP layers: application, transport, internet and link with example protocols."),
  [("Example 1 - IPv4 count", "How many different IPv4 addresses are possible?", ["IPv4 uses 32 bits.", "2^32 = 4 294 967 296."], "4 294 967 296"),
   ("Example 2 - Which protocol?", "Choose the protocol: (a) sending an email, (b) loading a secure web page, (c) turning a domain name into an address.", ["(a) SMTP sends email. (b) HTTPS is the secure web protocol.", "(c) DNS resolves names to IP addresses."], "SMTP, HTTPS, DNS")],
  [M("Which protocol translates domain names into IP addresses?", "DNS", ["SMTP", "FTP", "TCP"], "DNS."),
   M("In packet switching, data is divided into", "packets", ["pixels", "folders", "cores"], "Packets."),
   TF("An IPv4 address has 32 bits.", True, "Yes.")],
  [MT("Match the protocol to its job.", [("HTTPS", "Secure web pages"), ("SMTP", "Sending email"), ("FTP", "Transferring files"), ("TCP", "Reliable ordered delivery")], "Standard roles."),
   O("Explain why packets carry a sequence number.", "Packets may take different routes and arrive out of order; the sequence numbers let the destination reassemble the data in the correct order and detect missing packets.", "1 mark each point (2) plus 1 for example.")],
  P("A school in Bamenda sends a 6 MB file over a link of 2 Mbps (use 1 byte = 8 bits, 1 MB = 1 000 000 bytes, ignore overheads).",
    [pn("File size in megabits", 48, "6 x 8 = 48 megabits."),
     pn("Time to send in seconds", 24, "48 / 2 = 24 s.", unit="s"),
     po("Name one reason the real time may be longer.", "Packet headers, retransmitted packets, other users sharing the link, slower real speed than the stated maximum.", "1 mark.")]),
  [("TCP provides", "reliable ordered delivery", ["screen output", "power", "file storage"], "TCP."),
   ("A router", "forwards packets between networks", ["stores files", "prints pages", "draws gates"], "Router."),
   ("The URL https://example.com/page uses the protocol", "HTTPS", ["DNS", "UDP", "IMAP"], "First part."),
   ("A mesh network is", "reliable but costly", ["cheap with one cable", "wireless only", "never used"], "Many links."),
   ("IPv6 addresses have", "128 bits", ["32 bits", "8 bits", "64 bytes"], "Longer addresses.")],
  notes=["Four-layer TCP/IP model used; some syllabi use the 7-layer OSI model - check. File-time calculation ignores overheads."], minutes=30)
assert 6 * 8 / 2 == 24

lesson(c6, "operating-systems", "Operating systems",
  ["List the functions of an operating system", "Explain multitasking and interrupts", "Describe memory and file management", "Compare types of operating system"],
  [("definition", "Functions", "The **operating system (OS)** manages hardware and provides services to programs. Main functions: **process management** (scheduling the CPU), **memory management**, **file management**, **device management** (drivers, input/output), **security** (accounts, permissions) and the **user interface**."),
   ("retenir", "Multitasking and interrupts", "In **multitasking** the CPU switches rapidly between processes so several seem to run at once. An **interrupt** is a signal (from hardware such as a keyboard, or software) that makes the CPU pause the current task, run an **interrupt service routine**, then resume. The OS uses interrupts to share the CPU and respond to events."),
   ("retenir", "Memory and files", "Memory management decides where programs and data are placed in RAM and keeps processes from overwriting each other; **virtual memory** extends RAM using disk. File management organises files in a directory tree, controls access and tracks free space."),
   ("retenir", "Types of OS", "**Single-user**, **multi-user** (many accounts at once, e.g. a server), **real-time** (must respond within a guaranteed time, e.g. control systems), **embedded** (inside a device such as a phone or washing machine) and **network** operating systems."),
   ("pieges", "Common mistakes", "- The OS is not the same as the user interface.\n- An interrupt does not stop the program forever: the CPU resumes it.\n- Real-time does not mean 'fast'; it means 'guaranteed response time'.")],
  ("Interrupt", flow("Interrupt handling", ["CPU runs program A", "Interrupt signal arrives", "Save state; run service routine", "Restore state; resume program A"], colors=["lightblue", "lightorange", "lightyellow", "lightgreen"]),
   "Four steps: running, interrupt, service routine, resume.", "Flow of interrupt handling from running a program to resuming it."),
  [("Example 1 - Which function?", "Name the OS function: (a) giving each user a password-protected account, (b) allocating RAM to programs, (c) installing a driver for a printer.", ["(a) is security; (b) is memory management.", "(c) is device management."], "Security; memory management; device management"),
   ("Example 2 - Choose the OS type", "Which type of OS suits an engine-control system that must react within a set time?", ["The response time is guaranteed and critical.", "That is a real-time operating system (often embedded)."], "Real-time OS")],
  [M("The OS function of switching the CPU between programs is", "process scheduling", ["file naming", "printing", "hashing"], "Scheduling."),
   M("An interrupt is a signal that", "makes the CPU pause and handle an event", ["deletes a file", "shuts down the network", "formats a disk"], "Interrupt."),
   TF("A real-time OS guarantees a response within a time limit.", True, "That is its definition.")],
  [M("A server where many people log in at the same time uses a", "multi-user OS", ["single-user OS", "no OS", "embedded OS only"], "Multi-user."),
   O("Describe what happens when a key is pressed while a program is running.", "The keyboard sends an interrupt; the CPU saves its current state, runs the keyboard service routine to read the key, then restores the state and resumes the program.", "1 mark each of interrupt, service routine, resume (3).")],
  P("A school server in Maroua runs a multi-user operating system.",
    [pm("Which function stops one user reading another user's private files?", "Security and permissions", ["Virtual memory", "Scheduling only", "Device drivers"], "Access control."),
     pm("Which function lets it print for many users in turn?", "Device management with a queue", ["Compilation", "Encryption only", "Backup"], "Spooling queue."),
     po("Name two other functions of the OS.", "Memory management, process management, file management, user interface.", "1 mark each.")]),
  [("An embedded OS is found", "inside a device such as a phone", ["only in servers", "in books", "in printers' paper"], "Embedded."),
   ("Virtual memory uses", "part of the disk", ["extra CPU", "ROM chips", "cache lines"], "Disk."),
   ("Device drivers are", "programs that let the OS control hardware", ["viruses", "pictures", "networks"], "Drivers."),
   ("Multitasking means", "switching quickly between processes", ["using two screens", "turning off", "compiling"], "Switching."),
   ("The OS manages", "hardware and software resources", ["only spreadsheets", "only images", "nothing"], "Resources.")],
  notes=["Scheduling algorithms are in the Upper Sixth pack. OS types list is a common textbook one."], minutes=25)

lesson(c6, "ethics-law-security", "Ethics, law and security",
  ["Describe threats to data and countermeasures", "Explain the principles of data protection", "Describe copyright and licences", "Discuss social, ethical and environmental impacts of computing"],
  [("definition", "Threats and countermeasures", "**Malware** (viruses, worms, ransomware, spyware), **phishing**, **hacking**, **denial-of-service** attacks and **social engineering** threaten data. Countermeasures: **authentication** (passwords, biometrics, two-factor), **encryption**, **firewalls**, **anti-malware**, **access rights**, updates, **backups** and staff training."),
   ("retenir", "Data protection", "Data about people should be collected for a stated purpose, kept accurate and up to date, kept only as long as needed, protected against loss and unauthorised access, and not passed on without a lawful reason. Many countries have laws on data protection and cybercrime; Cameroon has legislation on cybersecurity and electronic communications (the exact current laws are to be named by the teacher)."),
   ("retenir", "Copyright and licences", "**Copyright** gives the creator the right to control copying and distribution of a work, including software. Licence types: **proprietary** (you may use but not copy or change), **open source** (source code may be studied, changed and shared under conditions), **shareware** (try then pay) and **freeware** (free to use, not always open)."),
   ("retenir", "Wider impact", "Computing creates jobs, speeds communication and improves services (e-learning, mobile banking), but can cause job changes, unequal access (the **digital divide**), privacy loss, health issues and **e-waste**. Responsible practice includes safe disposal and recycling of old equipment and reducing energy use."),
   ("pieges", "Common mistakes", "- Open source is not the same as free of cost or free of licence terms.\n- Encryption protects data in transit or storage; it does not stop it being deleted.\n- 'It is on the Internet' does not mean 'free to copy'.")],
  ("Defence layers", cols("Threat and countermeasure", ["Threat", "Countermeasure"], [["Malware", "Anti-malware, updates"], ["Phishing", "Training, checks"], ["Unauthorised access", "Passwords, 2FA"], ["Interception", "Encryption"]]),
   "A table of four threats and the matching countermeasures.", "Table of threats (malware, phishing, unauthorised access, interception) and their countermeasures."),
  [("Example 1 - Suggest controls", "A clinic in Bafoussam stores patient records on a PC. List three controls.", ["Protect access: individual passwords and access rights.", "Protect against loss: regular backups kept off-site; protect against malware: antivirus and updates."], "Passwords/rights, backups, antivirus"),
   ("Example 2 - Licence", "A student wants to modify a program found online. What must he check first?", ["Check the licence: proprietary software usually forbids copying and changing.", "If it is open source, follow the licence conditions when changing and sharing."], "The software licence")],
  [M("Two-factor authentication means", "using two different proofs of identity", ["two passwords of the same type", "two screens", "two printers"], "Different factors."),
   M("Encrypting data is meant to", "make it unreadable to unauthorised people", ["delete it", "speed up the CPU", "shrink the screen"], "Confidentiality."),
   TF("Open-source software always has no licence conditions.", False, "Open source is distributed under a licence with conditions.")],
  [M("The digital divide is", "unequal access to ICT", ["a broken cable", "a new CPU", "a file extension"], "Access gap."),
   O("Discuss two environmental issues caused by computing and one way to reduce each.", "E-waste from old equipment - recycle and repair; energy use of data centres and devices - use energy-efficient devices and switch off when not needed.", "1 mark per issue, 1 per solution (up to 4).")],
  P("A small business in Douala keeps customer names and phone numbers.",
    [pm("Which data-protection principle is breached if the data is sold without permission?", "Not passing it on without a lawful reason", ["Keeping data up to date", "Making backups", "Using passwords"], "Unlawful disclosure."),
     pm("Which control protects against disk failure?", "Backups", ["Encryption alone", "Firewall", "Digital divide"], "Backups restore data."),
     po("Give two reasons for encrypting laptop disks.", "If the laptop is lost or stolen the data cannot be read; it protects customers' privacy and reduces the risk of legal problems.", "1 mark each.")]),
  [("Ransomware", "locks data until a payment is demanded", ["makes a fast CPU", "is a type of ROM", "is a protocol"], "Malware."),
   ("A firewall", "filters network traffic", ["prints reports", "writes poems", "encrypts the CPU"], "Firewall."),
   ("Biometric authentication uses", "body characteristics such as fingerprints", ["pictures of food", "long cables", "ROM"], "Biometrics."),
   ("Proprietary software", "usually cannot be copied or changed freely", ["is always free", "is always open source", "has no licence"], "Proprietary."),
   ("E-waste is", "discarded electronic equipment", ["a spreadsheet function", "a network cable", "a font"], "Waste.")],
  notes=["Cameroonian legislation is not cited on purpose (exact law names, dates and numbers to be added by a teacher). Licence categories are textbook-level."], minutes=25)
write(p)
