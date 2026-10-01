"""Generates gceal-computer (Computer Science — GCE A Level). Pseudocode: Python-like."""
import sys, sqlite3, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _cs import *

REF = "Cameroon GCE Board — Advanced Level Computer Science syllabus (Upper Sixth part) — to be checked against the official text"
p = Pack("gceal-computer", "Computer Science — GCE A Level", level="Upper Sixth", subject="sciences", cursus="secondary", exam="GCE-AL",
         wanted="computer-science", programRef=REF,
         description="Upper Sixth Computer Science for GCE A Level: low-level programming, floating point, linked lists and trees, recursion and complexity, object-oriented programming, normalisation, network security, scheduling, software engineering, logic circuits and translators, with a mock paper.")
PSEUDO = "Pseudocode here is Python-like (indentation = block)."

# ======================================================================= CH1 low level
c1 = p.chapter("low-level", "Low-level programming and number representation", REF)

# toy machine: LDA, ADD, STA, SUB, JMP, BRZ, HLT -- run in Python to check
def run(prog, mem, maxsteps=100):
    acc = 0; pc = 0; steps = 0; mem = dict(mem)
    while steps < maxsteps:
        op, arg = prog[pc]; pc += 1; steps += 1
        if op == "LDA": acc = mem[arg]
        elif op == "ADD": acc += mem[arg]
        elif op == "SUB": acc -= mem[arg]
        elif op == "STA": mem[arg] = acc
        elif op == "LDI": acc = arg
        elif op == "JMP": pc = arg
        elif op == "BRZ":
            if acc == 0: pc = arg
        elif op == "HLT": break
    return acc, mem, steps
prog = [("LDA", "x"), ("ADD", "y"), ("STA", "z"), ("HLT", None)]
acc, mem, st = run(prog, {"x": 12, "y": 30, "z": 0}); assert mem["z"] == 42 and st == 4
# countdown loop: n=3, sum
prog2 = [("LDA", "n"), ("BRZ", 7), ("ADD", "s"), ("STA", "s"), ("LDA", "n"), ("SUB", "one"), ("STA", "n"), ("HLT", None)]
# simple (no loop jump back) - not used in text

lesson(c1, "assembly-addressing", "Assembly language and addressing modes",
  ["Describe the structure of a machine instruction", "Explain immediate, direct and indirect addressing", "Trace a short assembly program", "Compare assembly with high-level code"],
  [("definition", "Instruction format", "A machine instruction has an **opcode** (the operation) and an **operand** (the data or the address of the data). The instruction set is the list of instructions a CPU understands. **Assembly language** uses mnemonics such as LDA (load) or ADD instead of binary opcodes; an **assembler** converts it to machine code, one assembly line to one machine instruction. The instruction set below is a **teaching set invented for this lesson**, not a real CPU."),
   ("definition", "Addressing modes", "**Immediate**: the operand is the value itself (LDI 5 puts 5 in the accumulator). **Direct**: the operand is the memory address of the value (LDA 20 loads the contents of address 20). **Indirect**: the operand is the address of a memory location that **holds the address** of the data. Immediate is fastest; indirect allows pointer-like access."),
   ("methode", "Teaching instruction set", "LDI n: ACC = n. LDA a: ACC = memory[a]. STA a: memory[a] = ACC. ADD a: ACC = ACC + memory[a]. SUB a: ACC = ACC - memory[a]. JMP k: PC = k. BRZ k: if ACC = 0 then PC = k. HLT: stop. Trace by writing the PC, instruction, ACC and memory after each line."),
   ("pieges", "Common mistakes", "- Confusing the operand value with an address (immediate vs direct).\n- Forgetting that the PC already points to the next instruction while the current one executes.\n- Assuming assembly is portable: each CPU family has its own instruction set.")],
  ("Trace table", cols("Trace: x=12, y=30", ["Line", "ACC", "z"], [["LDA x", "12", "0"], ["ADD y", "42", "0"], ["STA z", "42", "42"], ["HLT", "42", "42"]]),
   "A trace table of a four-line program that adds x and y and stores the result in z.", "Trace of LDA x, ADD y, STA z, HLT with accumulator values 12, 42, 42, 42."),
  [("Example 1 - Trace", "Memory: x = 12, y = 30. Program: LDA x; ADD y; STA z; HLT. What is stored in z?", ["LDA x gives ACC = 12. ADD y gives ACC = 12 + 30 = 42.", "STA z writes 42 to z; HLT stops."], "z = 42"),
   ("Example 2 - Addressing modes", "Memory address 20 holds 35 and address 35 holds 8. What does ACC contain after (a) LDI 20, (b) LDA 20, (c) an indirect load using address 20?", ["(a) Immediate: ACC = 20 itself. (b) Direct: ACC = memory[20] = 35.", "(c) Indirect: memory[20] = 35 is an address; ACC = memory[35] = 8."], "(a) 20, (b) 35, (c) 8")],
  [M("In immediate addressing the operand is", "the value to use", ["an address of a pointer", "a file name", "a register name only"], "Immediate means the value itself."),
   M("An assembler translates", "assembly language into machine code", ["Python into C", "machine code into English", "spreadsheets into databases"], "Assembler."),
   TF("Machine code for one CPU family runs unchanged on any other CPU.", False, "Instruction sets differ.")],
  [N("Memory x = 7, y = 5. Program: LDA x; SUB y; STA z. What is z?", 2, "7 - 5 = 2."),
   O("State two advantages and one disadvantage of assembly language compared with a high-level language.", "Advantages: fine control of hardware; compact and fast code. Disadvantage: hard to write and debug, not portable.", "1 mark per correct point (3).")],
  P("Memory: address 10 holds 4, address 11 holds 9, address 12 holds 0. Program: LDA 10; ADD 11; ADD 10; STA 12; HLT.",
    [pn("ACC after ADD 11", 13, "4 + 9 = 13."),
     pn("The value stored in address 12", 17, "13 + 4 = 17."),
     pm("LDA 10 uses which addressing mode?", "Direct", ["Immediate", "Indirect", "Relative"], "The operand is an address holding the value.")]),
  [("An opcode specifies", "the operation to perform", ["the file name", "the screen colour", "the network"], "Opcode."),
   ("Indirect addressing means the operand is", "the address of a location holding the data's address", ["the data itself", "always zero", "a register's colour"], "Indirect."),
   ("HLT in the teaching set", "stops the program", ["adds", "loads", "branches"], "Halt."),
   ("BRZ k jumps when", "ACC is zero", ["ACC is one", "always", "never"], "Branch if zero."),
   ("The assembler produces", "machine code", ["pseudocode", "flowcharts", "SQL"], "Output of assembler.")],
  notes=["The instruction set is an invented teaching set (clearly stated); the Board will use its own instruction set (e.g. a Little Man Computer style or a real CPU). Replace the mnemonics after checking the syllabus."], minutes=35)

# ---- floating point (8-bit mantissa 1 sign+7 frac, 4-bit exponent two's complement)
def fp_value(mant, exp):
    m = int(mant[1:], 2) / 2 ** 7
    if mant[0] == "1": m -= 1
    e = int(exp, 2) - (16 if exp[0] == "1" else 0)
    return m * 2 ** e
assert fp_value("01101000", "0011") == 6.5 and fp_value("01010100", "0011") == 5.25
assert fp_value("10110000", "0010") == -2.5 and fp_value("01000000", "1110") == 0.5 * 2 ** -2 == 0.125
assert fp_value("01111111", "0111") == (127 / 128) * 128 == 127.0
assert fp_value("10000000", "0111") == -128.0

lesson(c1, "floating-point", "Floating-point numbers",
  ["Explain mantissa and exponent", "Convert a normalised floating-point number to denary", "Convert a denary number to floating point", "Explain precision, range and rounding errors"],
  [("definition", "Mantissa and exponent", "A **floating-point** number is stored as a **mantissa** (the significant digits) and an **exponent** (the power of 2), like scientific notation. Value = mantissa x 2^exponent. In this lesson: an 8-bit mantissa in two's complement with the binary point after the first bit (value from -1 to just under +1) and a 4-bit two's complement exponent. **Real formats (such as the IEEE standard) differ**; the Board will state its own format."),
   ("methode", "Normalised form", "A positive mantissa is **normalised** when it starts 0.1 (between 0.5 and 1); a negative one starts 1.0. Normalising uses all the bits for precision: shift the mantissa left until it starts 01 or 10 and reduce the exponent by the same number of places."),
   ("methode", "Converting to denary", "Example: mantissa 0.1101000, exponent 0011. Mantissa = 0.5 + 0.25 + 0.0625 = 0.8125; exponent = 3, so the value = 0.8125 x 2^3 = 6.5. Moving the binary point 3 places right gives 110.1 = 6.5, the same."),
   ("retenir", "Range and precision", "More **mantissa bits** give more **precision** (more accurate values); more **exponent bits** give a wider **range**. With a fixed total size, one is traded for the other. Some decimals (like 0.1) have no exact finite binary form, so they are stored with a small **rounding error**; comparing floats with = can fail."),
   ("pieges", "Common mistakes", "- Forgetting that the exponent is two's complement and can be negative.\n- Leaving a positive mantissa as 0.01... when it could be normalised.\n- Believing floating point stores every decimal exactly.")],
  ("Layout", shapes([T(210, 22, "Mantissa 01101000, exponent 0011", 14, bold=True)] + [x for i, b in enumerate("01101000") for x in (RECT(20 + i * 30, 50, 30, 34, fill="lightblue"), T(35 + i * 30, 73, b, 14))] +
            [x for i, b in enumerate("0011") for x in (RECT(290 + i * 30, 50, 30, 34, fill="lightorange"), T(305 + i * 30, 73, b, 14))] +
            [T(135, 110, "mantissa", 13), T(350, 110, "exponent", 13), T(210, 150, "0.8125 x 2^3 = 6.5", 14)], 420, 170),
   "Eight mantissa bits 01101000 in blue and four exponent bits 0011 in orange, giving 6.5.", "Floating-point layout: mantissa 01101000 and exponent 0011 equal 6.5."),
  [("Example 1 - To denary", "Find the value of mantissa 01010100, exponent 0011.", ["Mantissa = 0.1010100 = 0.5 + 0.125 + 0.03125 = 0.65625.", "Exponent 3: 0.65625 x 8 = 5.25."], "5.25"),
   ("Example 2 - To floating point", "Represent -2.5 as normalised floating point (8-bit mantissa, 4-bit exponent).", ["2.5 = 10.1 in binary = 0.101 x 2^2. Negative mantissa: -0.625 = 1.0110000 (since -1 + 0.375 = -0.625).", "Exponent 2 = 0010. Value: -0.625 x 4 = -2.5."], "Mantissa 10110000, exponent 0010")],
  [N("Value of mantissa 01101000, exponent 0011", 6.5, "0.8125 x 8 = 6.5.", tol=0.001),
   M("More bits in the exponent mainly increase the", "range of numbers", ["precision only", "speed only", "colour depth"], "Range."),
   TF("A normalised positive mantissa starts with 0.1.", True, "That is the definition for positive mantissa.")],
  [N("Value of mantissa 01000000, exponent 1110 (the exponent 1110 is -2)", 0.125, "0.5 x 2^-2 = 0.125.", tol=0.0001),
   O("Explain why 0.1 cannot be stored exactly in binary floating point, and give one consequence.", "In binary 0.1 is a recurring fraction (0.000110011...), so only an approximation can be stored. Consequence: calculations such as 0.1 + 0.2 may not equal 0.3 exactly, so equality tests on floats are unreliable.", "1 mark recurring/approximation; 1 mark consequence; 1 mark example.")],
  P("Floating-point (8-bit mantissa, 4-bit exponent, as in the lesson).",
    [pn("Value of mantissa 10110000, exponent 0010", -2.5, "-0.625 x 4 = -2.5.", tol=0.001),
     pn("Largest positive value: mantissa 01111111, exponent 0111", 127, "(127/128) x 128 = 127.", tol=0.001),
     po("State what would be gained and lost by using 6 mantissa bits and 6 exponent bits instead.", "Gain: wider range of numbers. Loss: lower precision.", "1 mark each.")]),
  [("The mantissa holds", "the significant digits", ["the power of 2", "the address", "the opcode"], "Mantissa."),
   ("A normalised negative mantissa starts", "10", ["01", "11", "00"], "1.0..."),
   ("Rounding errors occur because", "some decimals have no exact binary form", ["CPUs are slow", "ROM is volatile", "RAM is small"], "Binary fractions."),
   ("More mantissa bits give more", "precision", ["range only", "volume", "colour"], "Precision."),
   ("Value = mantissa x", "2 raised to the exponent", ["10 raised to the exponent", "the exponent plus 2", "the mantissa again"], "Binary floating point.")],
  notes=["Floating-point format is an assumed teaching format (8-bit two's complement mantissa with point after first bit, 4-bit exponent). The Board may use a different size; all worked examples verified in Python."], minutes=35)

# ======================================================================= CH2 data structures
c2 = p.chapter("data-structures", "Data structures, recursion and complexity", REF)

class Node:
    def __init__(s, k): s.k, s.l, s.r = k, None, None
def ins(t, k):
    if t is None: return Node(k)
    if k < t.k: t.l = ins(t.l, k)
    else: t.r = ins(t.r, k)
    return t
def inorder(t): return inorder(t.l) + [t.k] + inorder(t.r) if t else []
def preorder(t): return [t.k] + preorder(t.l) + preorder(t.r) if t else []
def postorder(t): return postorder(t.l) + postorder(t.r) + [t.k] if t else []
def height(t): return 0 if t is None else 1 + max(height(t.l), height(t.r))
root = None
for k in [50, 30, 70, 20, 40, 60, 80]: root = ins(root, k)
assert inorder(root) == [20, 30, 40, 50, 60, 70, 80] and preorder(root) == [50, 30, 20, 40, 70, 60, 80] and postorder(root) == [20, 40, 30, 60, 80, 70, 50] and height(root) == 3

lesson(c2, "linked-lists-trees", "Linked lists, trees and hash tables",
  ["Describe a linked list and its operations", "Describe a binary search tree and its traversals", "Insert into a BST", "Describe a hash table and collisions in outline"],
  [("definition", "Linked list", "A **linked list** is a chain of **nodes**; each node holds data and a **pointer** (the index or address of the next node). The **head** pointer marks the start; the last node's pointer is null. Inserting or deleting in the middle only changes pointers (no shifting of items), but finding the k-th item requires following k pointers. " + PSEUDO),
   ("definition", "Binary search tree (BST)", "A **tree** has a **root** and nodes with **children**; a **leaf** has none. In a **binary** tree each node has at most two children. In a **BST**, for every node all keys in the left subtree are smaller and all keys in the right subtree are larger. To insert: compare with the root and go left or right until an empty place is found."),
   ("methode", "Traversals", "**In-order** (left, root, right) gives a BST's keys in ascending order. **Pre-order** (root, left, right) is used to copy a tree. **Post-order** (left, right, root) is used to delete a tree or evaluate an expression tree. The **height** is the number of levels."),
   ("retenir", "Hash tables", "A **hash table** stores items at an index computed by a **hash function** from the key (e.g. key MOD table size). Lookup is very fast on average. A **collision** is when two keys give the same index; it is handled, for example, by **chaining** (a list at each slot) or by probing the next free slot."),
   ("pieges", "Common mistakes", "- Searching a BST that was built from sorted data is slow because it becomes a chain.\n- Inserting a node: update **both** pointers (new node's next and previous node's next).\n- In-order traversal sorts only a **binary search** tree.\n- A good hash function spreads keys evenly.")],
  ("BST", shapes([T(210, 16, "BST built from 50, 30, 70, 20, 40, 60, 80", 13, bold=True),
     CIRCLE(210, 50, 17, fill="lightyellow"), T(210, 55, "50", 13), LINE(198, 63, 130, 95), LINE(222, 63, 290, 95),
     CIRCLE(120, 105, 17, fill="lightblue"), T(120, 110, "30", 13), CIRCLE(300, 105, 17, fill="lightblue"), T(300, 110, "70", 13),
     LINE(110, 120, 70, 150), LINE(130, 120, 170, 150), LINE(290, 120, 250, 150), LINE(310, 120, 350, 150),
     CIRCLE(60, 160, 17, fill="lightgreen"), T(60, 165, "20", 13), CIRCLE(180, 160, 17, fill="lightgreen"), T(180, 165, "40", 13),
     CIRCLE(240, 160, 17, fill="lightgreen"), T(240, 165, "60", 13), CIRCLE(360, 160, 17, fill="lightgreen"), T(360, 165, "80", 13)], 420, 190),
   "A binary search tree with root 50, children 30 and 70, and leaves 20, 40, 60 and 80.", "BST diagram: root 50; left child 30 with leaves 20 and 40; right child 70 with leaves 60 and 80."),
  [("Example 1 - Traversals", "For the tree built by inserting 50, 30, 70, 20, 40, 60, 80, list the in-order and pre-order traversals.", ["In-order (left, root, right): 20, 30, 40, 50, 60, 70, 80 (sorted).", "Pre-order (root, left, right): 50, 30, 20, 40, 70, 60, 80."], "In-order 20 30 40 50 60 70 80; pre-order 50 30 20 40 70 60 80"),
   ("Example 2 - Hash", "Use hash = key MOD 7 to place keys 15, 22, 9 in a table of 7 slots (0-6). Show collisions.", ["15 MOD 7 = 1; 22 MOD 7 = 1; 9 MOD 7 = 2.", "15 and 22 collide at slot 1: with chaining slot 1 holds the list 15, 22."], "Slot 1: 15, 22; slot 2: 9")],
  [M("In-order traversal of a BST gives the keys", "in ascending order", ["in random order", "in descending order only", "level by level"], "Left-root-right."),
   N("The height (number of levels) of the BST in the lesson is", 3, "Levels: 50; 30 and 70; four leaves."),
   TF("Inserting into a linked list in the middle needs all later items to move.", False, "Only pointers change.")],
  [N("Insert 45 into the BST in the lesson. Its parent is the node with key", 40, "45 < 50 so go left; 45 > 30 go right to 40; 45 > 40 so it becomes the right child of 40."),
   O("Compare an array and a linked list for inserting an item in the middle.", "In an array, later items must be shifted along, which is slow for large arrays; in a linked list only the pointers of the neighbouring nodes change. However, a linked list cannot jump directly to an item by index.", "1 mark each (3).")],
  P("Keys 14, 3, 20, 8, 1 are inserted in this order into an empty BST.",
    [pm("The root is", "14", ["1", "3", "20"], "First key inserted."),
     pn("The in-order traversal's third key is", 8, "In-order: 1, 3, 8, 14, 20."),
     pm("Pre-order traversal is", "14, 3, 1, 8, 20", ["1, 3, 8, 14, 20", "14, 20, 3, 8, 1", "1, 8, 3, 20, 14"], "Root, left subtree (3 with children 1 and 8), then right (20).")]),
  [("A node in a linked list contains", "data and a pointer", ["only data", "only a pointer", "a CPU"], "Node."),
   ("A collision in a hash table occurs when", "two keys hash to the same index", ["a table is empty", "memory is full", "a key is deleted"], "Collision."),
   ("Post-order traversal visits the root", "last", ["first", "second", "never"], "Left, right, root."),
   ("A leaf node has", "no children", ["two children", "one parent only", "three children"], "Leaf."),
   ("The head pointer of a linked list", "points to the first node", ["holds the last value", "sorts the list", "deletes nodes"], "Head.")],
  notes=["Traversal and hash results computed in Python. Pointer representation (array of nodes) not drawn."], minutes=35)
assert 15 % 7 == 1 and 22 % 7 == 1 and 9 % 7 == 2
t2 = None
for k in [14, 3, 20, 8, 1]: t2 = ins(t2, k)
assert inorder(t2) == [1, 3, 8, 14, 20] and preorder(t2) == [14, 3, 1, 8, 20]

def hanoi(n): return 0 if n == 0 else 2 * hanoi(n - 1) + 1
assert hanoi(5) == 31 and hanoi(3) == 7
def fib(n): return n if n < 2 else fib(n - 1) + fib(n - 2)
assert fib(6) == 8
def msort(a):
    if len(a) <= 1: return a
    m = len(a) // 2; l, r = msort(a[:m]), msort(a[m:]); o = []
    while l and r: o.append(l.pop(0) if l[0] <= r[0] else r.pop(0))
    return o + l + r
assert msort([38, 27, 43, 3, 9, 82, 10]) == [3, 9, 10, 27, 38, 43, 82]

lesson(c2, "recursion-complexity", "Recursion and algorithm efficiency",
  ["Define recursion, base case and recursive case", "Trace recursive functions using the call stack", "Describe merge sort in outline", "Compare algorithms using Big-O notation"],
  [("definition", "Recursion", "A **recursive** subroutine calls itself on a **smaller** version of the problem. It needs a **base case** (stops the recursion) and a **recursive case**. " + PSEUDO + " def fact(n): if n == 0: return 1 else: return n * fact(n - 1). Each call is stored as a **stack frame**; too many nested calls cause a **stack overflow**."),
   ("methode", "Tracing", "Trace by writing each call until the base case, then **unwind**: fact(3) = 3 x fact(2) = 3 x 2 x fact(1) = 3 x 2 x 1 x fact(0) = 3 x 2 x 1 x 1 = 6. Recursion suits problems with self-similar structure: trees, divide-and-conquer, Towers of Hanoi. A loop is often faster and uses less memory."),
   ("definition", "Big-O notation", "**Big-O** describes how running time (or memory) grows with input size n in the worst case, ignoring constants. **O(1)** constant; **O(log n)** logarithmic (binary search); **O(n)** linear (linear search); **O(n log n)** (merge sort); **O(n²)** quadratic (bubble sort, nested loops); **O(2ⁿ)** exponential (naive Fibonacci)."),
   ("retenir", "Merge sort (outline)", "Divide the list into halves until each has one item, then **merge** sorted halves repeatedly. It always takes about n log n steps but needs extra memory. **Quick sort** picks a pivot and partitions the list; it is O(n log n) on average but O(n²) in the worst case."),
   ("pieges", "Common mistakes", "- Forgetting the base case gives infinite recursion.\n- The recursive call must move **towards** the base case.\n- Big-O ignores constants: 5n and n are both O(n).\n- O(n²) is not 'twice as slow': doubling n quadruples the time.")],
  ("Growth", cols("Growth for n = 1024", ["Complexity", "Steps (about)"], [["O(log n)", "10"], ["O(n)", "1 024"], ["O(n log n)", "10 240"], ["O(n squared)", "1 048 576"]]),
   "Table comparing about how many steps grow for n equal to 1024.", "Table of growth: log n 10, n 1024, n log n 10240 and n squared 1048576."),
  [("Example 1 - Trace", "def fact(n): if n == 0: return 1 else: return n * fact(n - 1). Trace fact(4).", ["fact(4) = 4 x fact(3) = 4 x 3 x fact(2) = 4 x 3 x 2 x fact(1) = 4 x 3 x 2 x 1 x fact(0).", "fact(0) = 1, so the result is 4 x 3 x 2 x 1 x 1 = 24."], "24"),
   ("Example 2 - Towers of Hanoi", "The minimum number of moves for n discs satisfies moves(n) = 2 x moves(n-1) + 1, moves(0) = 0. Find moves(5).", ["moves(1) = 1, moves(2) = 3, moves(3) = 7, moves(4) = 15.", "moves(5) = 2 x 15 + 1 = 31 (this equals 2^5 - 1)."], "31")],
  [M("A base case is", "the condition that stops the recursion", ["the first call", "a loop", "a stack"], "Base case."),
   M("Binary search has complexity", "O(log n)", ["O(n)", "O(n squared)", "O(1) always"], "Halving each step."),
   N("fact(5) equals", 120, "5 x 4 x 3 x 2 x 1.")],
  [N("Fibonacci: fib(0)=0, fib(1)=1, fib(n)=fib(n-1)+fib(n-2). Find fib(6).", 8, "0,1,1,2,3,5,8."),
   O("Explain why a recursive function without a base case fails.", "It calls itself forever, each call adds a stack frame, and eventually the stack is exhausted (stack overflow).", "1 mark infinite calls; 1 mark stack frames; 1 mark overflow.")],
  P("Algorithm A takes about n squared steps; algorithm B takes about n log2 n steps. Take n = 1024 (log2 1024 = 10).",
    [pn("Steps for A", 1048576, "1024 x 1024 = 1 048 576."),
     pn("Steps for B", 10240, "1024 x 10 = 10 240."),
     po("Which algorithm is better for large n, and why?", "B, because n log n grows much more slowly than n squared.", "1 mark each.")]),
  [("O(n) means the time grows", "in proportion to n", ["with n squared", "not at all", "exponentially"], "Linear."),
   ("Merge sort works by", "dividing and merging", ["swapping neighbours", "hashing", "compiling"], "Divide and conquer."),
   ("Each recursive call is held in a", "stack frame", ["queue", "cache line", "file"], "Call stack."),
   ("The worst case of quick sort is", "O(n squared)", ["O(1)", "O(log n)", "O(n)"], "Bad pivots."),
   ("Naive recursive Fibonacci is roughly", "exponential", ["constant", "logarithmic", "linear"], "Repeated work.")],
  notes=["Verified values (hanoi 31, fib(6)=8, merge sort result). Big-O definitions at A-level outline."], minutes=35)

# ======================================================================= CH3 OOP
c3 = p.chapter("oop", "Object-oriented programming", REF)
class Account:
    def __init__(self, owner, balance): self.owner = owner; self.__balance = balance
    def deposit(self, a):
        if a > 0: self.__balance += a
    def withdraw(self, a):
        if 0 < a <= self.__balance: self.__balance -= a; return True
        return False
    def balance(self): return self.__balance
class Savings(Account):
    def __init__(self, owner, balance, rate): super().__init__(owner, balance); self.rate = rate
    def add_interest(self): self.deposit(self.balance() * self.rate / 100)
a = Account("Ada", 10000); a.deposit(5000); ok1 = a.withdraw(20000); ok2 = a.withdraw(3000)
assert (ok1, ok2, a.balance()) == (False, True, 12000)
s = Savings("Paul", 20000, 5); s.add_interest(); assert s.balance() == 21000

lesson(c3, "oop", "Object-oriented programming concepts",
  ["Define class, object, attribute and method", "Explain encapsulation, inheritance and polymorphism", "Read and trace OOP pseudocode", "Compare OOP with procedural programming"],
  [("definition", "Classes and objects", "A **class** is a blueprint that defines **attributes** (data) and **methods** (operations). An **object** is one **instance** of a class, with its own attribute values. A **constructor** sets up a new object. " + PSEUDO),
   ("definition", "Encapsulation", "**Encapsulation** bundles data and methods and **hides** the data: attributes are private and changed only through public methods, which can **validate** the changes. Example: a bank account balance can change only through deposit() and withdraw(), which reject invalid amounts."),
   ("definition", "Inheritance and polymorphism", "**Inheritance**: a **subclass** (child) takes the attributes and methods of a **superclass** (parent) and can add or **override** them; this promotes reuse. **Polymorphism**: the same method name behaves differently depending on the object's class (for example, area() for Circle and Square)."),
   ("methode", "Pseudocode style", "class Account: def deposit(self, amount): if amount > 0: self.balance = self.balance + amount. A subclass: class Savings(Account): ... . Create objects: acc = Account('Ada', 10000). Call: acc.deposit(5000)."),
   ("pieges", "Common mistakes", "- A class is not an object; objects are created from classes.\n- Inheritance is an 'is-a' relationship (Savings is an Account); do not inherit when the relation is 'has-a'.\n- Private attributes should not be set directly from outside.\n- Overriding changes behaviour in the subclass only.")],
  ("Inheritance", shapes([T(210, 18, "Class diagram", 15, bold=True), RECT(120, 36, 180, 56, fill="lightyellow", radius=4), T(210, 56, "Account", 14, bold=True), T(210, 76, "balance, deposit(), withdraw()", 11),
     LINE(210, 92, 210, 122, arrow="end"), T(260, 112, "inherits", 12), RECT(120, 122, 180, 56, fill="lightgreen", radius=4), T(210, 142, "Savings", 14, bold=True), T(210, 162, "rate, add_interest()", 11)], 420, 190),
   "A class Account with attributes and methods, and a class Savings inheriting from it.", "Class diagram: Savings inherits from Account."),
  [("Example 1 - Encapsulation", "acc = Account('Ada', 10000); acc.deposit(5000); acc.withdraw(20000); acc.withdraw(3000). Balance? (withdraw refuses amounts above the balance).", ["After the deposit, balance = 15000. withdraw(20000) is refused (more than the balance).", "withdraw(3000) succeeds: 15000 - 3000 = 12000."], "12000"),
   ("Example 2 - Inheritance", "Savings(Account) has a rate. s = Savings('Paul', 20000, 5); add_interest() deposits balance x rate / 100. What is the balance after add_interest()?", ["Interest = 20000 x 5 / 100 = 1000.", "Balance = 20000 + 1000 = 21000. Savings uses deposit() inherited from Account."], "21000")],
  [M("An object is", "an instance of a class", ["a blueprint", "a file extension", "a compiler"], "Instance."),
   M("Hiding data and giving access through methods is", "encapsulation", ["polymorphism", "recursion", "hashing"], "Encapsulation."),
   TF("A subclass can add new methods.", True, "Yes.")],
  [M("The same method name behaving differently for different classes is", "polymorphism", ["encapsulation", "iteration", "normalisation"], "Polymorphism."),
   O("Give two benefits of encapsulation.", "Protects data from invalid changes because methods can validate input; hides implementation so it can change without breaking other code; easier maintenance.", "1 mark each, 2 marks + 1 for example.")],
  P("class Shape: def area(self): return 0. class Square(Shape): def __init__(self, s): self.s = s. def area(self): return self.s * self.s. class Rect(Shape): def __init__(self, w, h): self.w = w. self.h = h. def area(self): return self.w * self.h.",
    [pn("area() of Square(7)", 49, "7 x 7 = 49."),
     pn("area() of Rect(6, 4)", 24, "6 x 4 = 24."),
     pm("Square and Rect both define area(); this is an example of", "polymorphism (overriding)", ["encapsulation only", "recursion", "a compiler"], "Same method, different behaviour.")]),
  [("A constructor", "sets up a new object", ["deletes an object", "prints a class", "is a hash"], "Constructor."),
   ("Inheritance gives a subclass", "the attributes and methods of its superclass", ["nothing", "the screen", "a network"], "Reuse."),
   ("An attribute is", "a data item belonging to an object", ["a loop", "a gate", "a protocol"], "Attribute."),
   ("Private attributes are accessed through", "methods", ["direct assignment from anywhere", "the keyboard", "the cache"], "Encapsulation."),
   ("Savings is an Account is an", "is-a relationship", ["has-a relationship", "hash", "queue"], "Inheritance.")],
  notes=["Python-like pseudocode; examples run in Python to check results. The Board may require a specific language (e.g. Java/Python/Visual Basic) - adapt."], minutes=35)
assert 7 * 7 == 49 and 6 * 4 == 24

# ======================================================================= CH4 databases
c4 = p.chapter("databases", "Databases: design and normalisation", REF)
db = sqlite3.connect(":memory:")
db.executescript("""CREATE TABLE Customer(CID INTEGER PRIMARY KEY, CName TEXT, Town TEXT);
CREATE TABLE Product(PID INTEGER PRIMARY KEY, PName TEXT, Price INTEGER);
CREATE TABLE Orders(OID INTEGER PRIMARY KEY, CID INTEGER, PID INTEGER, Qty INTEGER);
INSERT INTO Customer VALUES (1,'Ada','Buea'),(2,'Paul','Limbe'),(3,'Nina','Kumba');
INSERT INTO Product VALUES (10,'Rice',600),(11,'Oil',1500),(12,'Sugar',800);
INSERT INTO Orders VALUES (100,1,10,3),(101,1,11,1),(102,2,10,2),(103,3,12,5);""")
j1 = db.execute("SELECT CName, PName, Qty*Price FROM Orders JOIN Customer ON Orders.CID=Customer.CID JOIN Product ON Orders.PID=Product.PID ORDER BY OID").fetchall()
assert j1 == [("Ada", "Rice", 1800), ("Ada", "Oil", 1500), ("Paul", "Rice", 1200), ("Nina", "Sugar", 4000)]
j2 = db.execute("SELECT CName, SUM(Qty*Price) FROM Orders JOIN Customer ON Orders.CID=Customer.CID JOIN Product ON Orders.PID=Product.PID GROUP BY CName ORDER BY CName").fetchall()
assert j2 == [("Ada", 3300), ("Nina", 4000), ("Paul", 1200)]

lesson(c4, "normalisation-joins", "Entity relationships, normalisation and SQL joins",
  ["Describe one-to-many and many-to-many relationships", "Explain 1NF, 2NF and 3NF", "Normalise a table by removing redundancy", "Write SQL joins and grouped queries"],
  [("definition", "Relationships", "An **entity** is a thing about which data is stored (Customer, Product). Relationships: **one-to-one**, **one-to-many** (one customer places many orders) and **many-to-many** (orders and products), which is resolved with a **link table** holding two foreign keys. An **entity-relationship (E-R) diagram** shows entities and relationships."),
   ("definition", "Normal forms", "**1NF**: every field holds one atomic value (no repeating groups or lists in a cell) and each record is unique. **2NF**: in 1NF, and every non-key field depends on the **whole** primary key (no partial dependency; relevant for composite keys). **3NF**: in 2NF, and no non-key field depends on **another non-key field** (no transitive dependency). Informally: 'the key, the whole key and nothing but the key'."),
   ("methode", "Normalising an example", "Unnormalised: Orders(OrderID, CustomerName, CustomerTown, ProductName, Price, Qty). Customer details depend on the customer, product details on the product. Split into Customer(CID, CName, Town), Product(PID, PName, Price) and Orders(OID, CID, PID, Qty) with foreign keys: no duplicated customer or product data and each fact is stored once."),
   ("methode", "SQL joins", "SELECT CName, PName, Qty * Price FROM Orders JOIN Customer ON Orders.CID = Customer.CID JOIN Product ON Orders.PID = Product.PID; Add GROUP BY CName with SUM(...) for totals per customer. Aggregates ignore NULL."),
   ("pieges", "Common mistakes", "- Storing a calculated value (Qty x Price) that can be derived.\n- A join without the ON condition multiplies rows (every combination).\n- 'Repeating a customer name' is a sign that data is not normalised.\n- Using a name instead of an ID as a foreign key.")],
  ("Tables", cols("Orders table (normalised)", ["OID", "CID", "PID", "Qty"], [["100", "1", "10", "3"], ["101", "1", "11", "1"], ["102", "2", "10", "2"], ["103", "3", "12", "5"]]),
   "The Orders table with order, customer and product identifiers and quantity.", "Orders table with four rows: OID, CID, PID and Qty."),
  [("Example 1 - Total per order", "Product prices: 10 Rice 600, 11 Oil 1500, 12 Sugar 800. Order 100 is customer 1, product 10, quantity 3. Compute the line total.", ["Join Orders to Product on PID: product 10 is Rice at 600.", "Line total = Qty x Price = 3 x 600 = 1800 FCFA."], "1800 FCFA"),
   ("Example 2 - Total per customer", "Customer 1 (Ada) has orders 100 (Rice, 3) and 101 (Oil, 1). What does SUM(Qty*Price) GROUP BY CName give for Ada?", ["Order 100: 3 x 600 = 1800. Order 101: 1 x 1500 = 1500.", "Total = 3300."], "3300 FCFA")],
  [M("Resolving a many-to-many relationship needs", "a link table", ["a bigger table", "no keys", "a spreadsheet"], "Link table."),
   M("1NF requires", "atomic values in each field", ["no primary key", "no relationships", "only text"], "Atomic."),
   TF("3NF removes dependencies between non-key fields.", True, "Transitive dependencies.")],
  [N("Using the data: line total of Nina's order (customer 3, Sugar 800, quantity 5)", 4000, "5 x 800 = 4000."),
   O("A table Student(ID, Name, ClassName, ClassTeacher) repeats the teacher for each student. Which normal form is broken and how do you fix it?", "3NF is broken because ClassTeacher depends on ClassName (a non-key field). Create a Class(ClassName, ClassTeacher) table and keep ClassName in Student as a foreign key.", "1 mark 3NF; 1 mark reason; 1 mark fix.")],
  P("Customer(CID, CName, Town), Product(PID, PName, Price), Orders(OID, CID, PID, Qty) as in the lesson.",
    [pn("Total for customer Paul: SUM(Qty x Price)", 1200, "Paul ordered Rice twice: 2 x 600 = 1200."),
     pm("Which keys are foreign keys in Orders?", "CID and PID", ["OID only", "Qty", "PName"], "They link to Customer and Product."),
     po("Why is Qty x Price not stored as a field?", "It can be calculated, so storing it duplicates data and may become inconsistent if the price changes.", "1 mark each reason.")]),
  [("An entity is", "something data is stored about", ["a CPU register", "a network cable", "a loop"], "Entity."),
   ("A composite key", "uses more than one field", ["is always a name", "has no fields", "is a foreign table"], "Composite."),
   ("2NF removes", "partial dependencies on part of a composite key", ["all tables", "primary keys", "SQL"], "Partial."),
   ("A join without ON gives", "every combination of rows", ["no rows", "one row", "an error always"], "Cartesian product."),
   ("Redundant data can cause", "update inconsistencies", ["faster CPUs", "smaller screens", "no problems"], "Anomalies.")],
  notes=["All SQL and totals checked in SQLite. Normal-form wording is the usual informal one; exam answers may require formal dependency language."], minutes=35)

# ======================================================================= CH5 networks security
c5 = p.chapter("networks-security", "Network security and cryptography", REF)
def caesar(t, k): return "".join(chr((ord(c) - 65 + k) % 26 + 65) if c.isalpha() else c for c in t)
assert caesar("CAMEROON", 3) == "FDPHURRQ" and caesar("FDPHURRQ", -3) == "CAMEROON"
n_, e_, d_ = 33, 3, 7
assert (e_ * d_) % ((3 - 1) * (11 - 1)) == 1 and pow(4, e_, n_) == 31 and pow(31, d_, n_) == 4

lesson(c5, "cryptography-security", "Encryption, authentication and secure communication",
  ["Explain symmetric and asymmetric encryption", "Use a Caesar cipher", "Describe digital signatures and hashing in outline", "Describe HTTPS and how a firewall protects a network"],
  [("definition", "Encryption", "**Plaintext** is turned into **ciphertext** by an **algorithm** and a **key**. **Symmetric** encryption uses one shared secret key for both encrypting and decrypting: fast, but the key must be shared securely. **Asymmetric** (public-key) encryption uses a **key pair**: anyone may encrypt with the **public key**, but only the **private key** decrypts."),
   ("methode", "Caesar cipher", "Shift every letter k places along the alphabet. With k = 3: A becomes D, B becomes E ... and X wraps around to A. Decrypt by shifting back k places. CAMEROON becomes FDPHURRQ. A Caesar cipher is **easy to break** (only 25 keys; letter-frequency analysis) and is used only to teach the idea."),
   ("retenir", "Hashing and signatures", "A **hash function** turns data of any size into a fixed-size **hash (digest)**; the same input always gives the same output, a small change gives a very different output, and it cannot be reversed in practice. Uses: storing passwords (store the hash, not the password), checking a download is unchanged. A **digital signature** is made by encrypting a hash with the sender's private key; anyone can verify it with the public key, proving who sent it and that it was not changed."),
   ("retenir", "HTTPS and firewalls", "**HTTPS** (HTTP over TLS) uses asymmetric encryption to agree a shared secret and then fast symmetric encryption for the session; **digital certificates** show that the public key belongs to the named site. A **firewall** filters packets by rules (addresses, ports, protocols) to block unwanted traffic."),
   ("pieges", "Common mistakes", "- Encryption does not hide that communication is happening; it protects the content.\n- A hash is not encryption: it cannot be 'decrypted'.\n- The private key must never be shared.\n- HTTPS protects data in transit, not against a malicious site.")],
  ("Public key", flow("Public-key encryption", ["Sender encrypts with the receiver's PUBLIC key", "Ciphertext travels over the network", "Receiver decrypts with the PRIVATE key"], colors=["lightblue", "lightyellow", "lightgreen"]),
   "Three steps: encrypt with the public key, send the ciphertext, decrypt with the private key.", "Flow showing public-key encryption from sender to receiver."),
  [("Example 1 - Caesar", "Encrypt CAMEROON with a Caesar shift of 3.", ["C to F, A to D, M to P, E to H.", "R to U, O to R, O to R, N to Q: FDPHURRQ."], "FDPHURRQ"),
   ("Example 2 - Toy public key (RSA idea)", "Public key (n = 33, e = 3), private key d = 7 (a tiny example for learning only). Encrypt the message 4 and decrypt it.", ["Encrypt: 4^3 mod 33 = 64 mod 33 = 31.", "Decrypt: 31^7 mod 33 = 4, the original message."], "Cipher 31; decrypts back to 4")],
  [M("In symmetric encryption", "the same key encrypts and decrypts", ["two different keys are used", "no key is used", "only hashes are used"], "Shared key."),
   N("Number the letters A=1, B=2, ... Z=26. With a Caesar shift of 3, the letter Y (25) becomes the letter at position", 2, "25 + 3 = 28; subtract 26 to wrap around: 2, which is B."),
   TF("A hash can be reversed to recover the original password easily.", False, "Hash functions are one-way.")],
  [M("Which key decrypts a message encrypted with someone's public key?", "Their private key", ["The same public key", "Any key", "No key"], "Asymmetric."),
   O("Explain why websites store password hashes rather than passwords.", "If the database is stolen, attackers get only hashes, which cannot be reversed easily; when a user logs in, the entered password is hashed and compared with the stored hash.", "1 mark each of: hash not reversible, stolen data less useful, comparing hashes (3).")],
  P("A school in Douala wants to protect its online exam platform.",
    [pm("Which protocol gives encrypted web connections?", "HTTPS", ["HTTP", "FTP", "SMTP"], "HTTPS."),
     pm("Which device filters unwanted incoming traffic?", "A firewall", ["A scanner", "A printer", "A monitor"], "Firewall."),
     po("Explain one weakness of the Caesar cipher.", "There are only 25 possible shifts, and letter frequencies are unchanged so it is easily broken.", "1 mark each.")]),
  [("A digital certificate", "links a public key to an identity", ["encrypts the CPU", "stores photos", "prints"], "Certificate."),
   ("Asymmetric encryption uses", "a public key and a private key", ["one shared key only", "no keys", "three keys"], "Pair."),
   ("A Caesar cipher with shift 3 turns B into", "E", ["A", "C", "Y"], "B+3 = E."),
   ("A firewall decides using", "rules about addresses and ports", ["screen size", "RAM speed", "file names only"], "Rules."),
   ("A hash of the same data is", "always the same", ["different each time", "random", "empty"], "Deterministic.")],
  notes=["Toy RSA numbers (n=33, e=3, d=7) are the standard classroom example (checked in Python); real keys are huge. TLS described in outline."], minutes=35)

# ======================================================================= CH6 OS
c6 = p.chapter("operating-systems", "Operating systems: scheduling and memory", REF)
def rr(bursts, q):
    rem = dict(bursts); t = 0; done = {}; order = list(bursts)
    queue = list(order)
    while queue:
        pid = queue.pop(0)
        run_ = min(q, rem[pid]); t += run_; rem[pid] -= run_
        if rem[pid] == 0: done[pid] = t
        else: queue.append(pid)
    return done
assert rr({"P1": 5, "P2": 3, "P3": 2}, 2) == {"P3": 6, "P2": 9, "P1": 10}
def fcfs_wait(b):
    t = 0; w = []
    for x in b: w.append(t); t += x
    return w
assert fcfs_wait([24, 3, 3]) == [0, 24, 27] and sum(fcfs_wait([24, 3, 3])) / 3 == 17.0 and fcfs_wait([3, 3, 24]) == [0, 3, 6] and sum(fcfs_wait([3, 3, 24])) / 3 == 3.0
assert divmod(10000, 4096) == (2, 1808)

lesson(c6, "scheduling-memory", "Process scheduling and memory management",
  ["Describe process states", "Compare FCFS, shortest-job-first and round-robin scheduling", "Calculate waiting and completion times", "Explain paging and virtual memory"],
  [("definition", "Processes", "A **process** is a program being executed. States: **ready** (waiting for the CPU), **running** (using the CPU), **blocked/waiting** (waiting for I/O). The **scheduler** chooses which ready process runs next; a **context switch** saves the state of one process and loads another. The aim: keep the CPU busy and give fair, quick responses."),
   ("methode", "Scheduling algorithms", "**FCFS** (first come, first served): run in order of arrival, simple but a long job delays short ones. **SJF** (shortest job first): run the shortest burst first; minimises average waiting time but needs the burst length known. **Round robin**: each process gets a fixed **time slice (quantum)**; if unfinished it goes to the back of the queue; fair and good for interactive use."),
   ("methode", "Waiting time", "Waiting time = time spent in the ready queue before running (with all arrivals at time 0 and no pre-emption: the sum of earlier burst times). Example: bursts 24, 3, 3 (order P1, P2, P3) under FCFS: waits 0, 24, 27, mean 17. With SJF the order is P2, P3, P1: waits 0, 3, 6, mean 3 (see the worked example)."),
   ("retenir", "Paging", "In **paging** memory is split into fixed-size **frames** and each program into **pages** of the same size; a **page table** maps pages to frames, so a program need not be in contiguous memory. A logical address = page number and **offset**. With 4096-byte pages, address 10000 is page 10000 // 4096 = 2, offset 10000 mod 4096 = 1808. Pages not in RAM are kept on disk (virtual memory); a **page fault** loads the page when needed."),
   ("pieges", "Common mistakes", "- Round robin: a process that finishes early gives up the CPU immediately.\n- SJF can starve long jobs.\n- A small quantum causes many context switches (overhead); a very large quantum behaves like FCFS.\n- Offset is the remainder after dividing by the page size.")],
  ("Gantt", shapes([T(210, 16, "Round robin, quantum 2: P1=5, P2=3, P3=2", 13, bold=True)] +
        [RECT(20 + 36 * 0, 50, 72, 36, fill="lightblue"), T(56, 73, "P1", 13), RECT(92, 50, 72, 36, fill="lightgreen"), T(128, 73, "P2", 13), RECT(164, 50, 72, 36, fill="lightyellow"), T(200, 73, "P3", 13),
         RECT(236, 50, 72, 36, fill="lightblue"), T(272, 73, "P1", 13), RECT(308, 50, 36, 36, fill="lightgreen"), T(326, 73, "P2", 12), RECT(344, 50, 36, 36, fill="lightblue"), T(362, 73, "P1", 12),
         T(20, 108, "0", 12), T(92, 108, "2", 12), T(164, 108, "4", 12), T(236, 108, "6", 12), T(308, 108, "8", 12), T(344, 108, "9", 12), T(380, 108, "10", 12), T(210, 140, "Finish: P3 at 6, P2 at 9, P1 at 10", 13)], 420, 160),
   "A Gantt chart of round-robin scheduling with quantum 2 showing P1, P2, P3, P1, P2, P1 from time 0 to 10.", "Gantt chart: P1 0-2, P2 2-4, P3 4-6, P1 6-8, P2 8-9, P1 9-10."),
  [("Example 1 - FCFS vs SJF", "Three jobs arrive at time 0 with bursts P1 = 24, P2 = 3, P3 = 3. Compare the mean waiting time under FCFS (order P1, P2, P3) and SJF.", ["FCFS: waits 0, 24, 27, so the mean is (0 + 24 + 27) / 3 = 17.", "SJF order P2, P3, P1: waits 0, 3, 6, mean 9 / 3 = 3. SJF is much lower."], "FCFS 17, SJF 3"),
   ("Example 2 - Paging", "Page size 4096 bytes. A logical address is 10000. Find the page number and offset.", ["Page = 10000 // 4096 = 2 (2 x 4096 = 8192).", "Offset = 10000 - 8192 = 1808."], "Page 2, offset 1808")],
  [M("A process waiting for disk input is in the", "blocked state", ["running state", "finished state", "ready state"], "Waiting for I/O."),
   M("Round robin gives each process", "a fixed time slice in turn", ["the CPU until it finishes", "no CPU time", "a random file"], "Quantum."),
   N("Page size 1024 bytes. Address 3000 is in page number (starting from 0)", 2, "3000 // 1024 = 2.")],
  [N("Round robin, quantum 2, bursts P1=5, P2=3, P3=2 (all at time 0, order P1, P2, P3). At what time does P2 finish?", 9, "P1 0-2, P2 2-4, P3 4-6 (done), P1 6-8, P2 8-9 (done)."),
   O("Explain one advantage and one disadvantage of SJF scheduling.", "Advantage: lowest average waiting time. Disadvantage: long jobs may starve, and burst length must be known in advance.", "1 mark each (2) plus 1 for explanation.")],
  P("Jobs P1 = 6, P2 = 2, P3 = 4 arrive at time 0. Use FCFS in the order P1, P2, P3.",
    [pn("Waiting time of P3", 8, "P3 waits for P1 and P2: 6 + 2 = 8."),
     pn("Total of the three waiting times", 14, "0 + 6 + 8 = 14."),
     pm("Which algorithm would give the lowest mean waiting time?", "SJF", ["FCFS", "Round robin always", "Random"], "Shortest first.")]),
  [("A context switch", "saves one process's state and loads another's", ["deletes memory", "prints a page", "formats a disk"], "Context switch."),
   ("A page table maps", "pages to frames", ["files to folders", "IP to MAC only", "keys to values only"], "Paging."),
   ("A page fault occurs when", "a needed page is not in RAM", ["RAM is full of zeros", "the CPU overheats", "a file is deleted"], "Load from disk."),
   ("A very large round-robin quantum behaves like", "FCFS", ["SJF", "random scheduling", "paging"], "No pre-emption."),
   ("The ready state means", "waiting for the CPU", ["using the CPU", "waiting for I/O", "terminated"], "Ready.")],
  notes=["Computed in Python: round robin finish times, FCFS waits, paging offset. Pre-emptive SJF and priority scheduling not covered."], minutes=35)

# ======================================================================= CH7 software engineering
c7 = p.chapter("software-engineering", "Software engineering and testing", REF)
lesson(c7, "sdlc-testing", "Software development life cycle and testing",
  ["Describe the stages of the life cycle", "Compare waterfall, prototyping and agile approaches", "Design a test plan with normal, boundary and erroneous data", "Distinguish types of testing and maintenance"],
  [("definition", "Life cycle stages", "**Analysis** (find and record requirements, feasibility study), **design** (structure, data, interface, algorithms), **implementation** (coding), **testing**, **deployment** (installation, training), **maintenance** (corrective, adaptive, perfective). Documentation (user and technical) is produced along the way."),
   ("retenir", "Development models", "**Waterfall**: stages in strict sequence; clear and well documented but inflexible if requirements change. **Prototyping**: build a quick working model, get user feedback and refine; good when requirements are unclear. **Agile**: short **iterations** (sprints), working software each time, close contact with the customer, accepts changing requirements; less documentation."),
   ("methode", "Testing", "A **test plan** lists the test, the input data, the expected result and the actual result. Use **normal** data (valid), **boundary** data (at the limits of the valid range) and **erroneous** data (invalid). Example: marks must be 0-100: normal 55, boundary 0 and 100 (and 101 or -1 just outside), erroneous 'abc'. Types: **unit**, **integration**, **system**, **acceptance** (by the user), **alpha/beta**; **black-box** (inputs and outputs only) and **white-box** (internal logic)."),
   ("pieges", "Common mistakes", "- Testing only normal data.\n- Writing the 'expected result' after seeing the program's output.\n- Choosing a model by habit: the right model depends on how clear and stable the requirements are.\n- Maintenance is part of the life cycle, not a sign of failure.")],
  ("Waterfall", flow("Waterfall model", ["Analysis", "Design", "Implementation", "Testing", "Maintenance"], colors=["lightblue", "lightgreen", "lightyellow", "lightorange", "lightblue"]),
   "Five stages from analysis to maintenance, each leading to the next.", "Vertical flow of the waterfall stages: analysis, design, implementation, testing, maintenance."),
  [("Example 1 - Test data", "A program accepts a mark from 0 to 100. Choose normal, boundary and erroneous test data.", ["Normal: 55. Boundary: 0 and 100 (and just outside: -1 and 101 should be rejected).", "Erroneous: abc (text) or an empty entry; expected result: an error message."], "55; 0, 100, -1, 101; abc"),
   ("Example 2 - Choose a model", "A clinic in Bamenda wants an appointment system but is not sure what it needs. Which approach is suitable?", ["Requirements are unclear and will change after users see something.", "Prototyping or agile development with frequent feedback suits this."], "Prototyping or agile")],
  [M("Which stage comes immediately after implementation in the waterfall model?", "Testing", ["Analysis", "Design", "Maintenance"], "Order."),
   M("Data at the edge of the valid range is called", "boundary data", ["normal data", "erroneous data only", "random data"], "Boundary."),
   TF("Agile development works in short iterations.", True, "Yes.")],
  [M("Testing done by the customer to confirm the system meets needs is", "acceptance testing", ["unit testing", "white-box only", "alpha only"], "Acceptance."),
   O("Give two advantages of agile over waterfall.", "Changing requirements can be handled; users see working software early; problems are found sooner.", "1 mark each (2) plus 1 for explanation.")],
  P("A program accepts an age from 11 to 19 inclusive.",
    [pm("Which is boundary data (valid)?", "11", ["15", "abc", "30"], "11 is the lowest valid age."),
     pm("Which is erroneous data?", "abc", ["12", "19", "11"], "Text where a number is expected."),
     po("Give two other test values just outside the range, with the expected result.", "10 and 20; the program should reject them with a message.", "1 mark each plus expected result.")]),
  [("Corrective maintenance", "fixes faults found after release", ["adds a new feature only", "changes hardware", "writes the first design"], "Corrective."),
   ("A prototype is", "an early working model", ["a final product always", "a virus", "a hash"], "Prototype."),
   ("Black-box testing considers", "inputs and expected outputs only", ["the source code lines", "the keyboard", "network cables"], "Black box."),
   ("The feasibility study is part of", "analysis", ["testing", "maintenance", "design only"], "Early stage."),
   ("A test plan includes", "test data and expected results", ["only the final marks", "only the colour scheme", "nothing"], "Plan.")],
  notes=["SDLC stage names vary between textbooks; the Board's list of models should be checked."], minutes=30)

# ======================================================================= CH8 logic circuits
c8 = p.chapter("logic-circuits", "Logic circuits and Boolean simplification", REF)
def fa(a, b, c): s = a ^ b ^ c; co = (a & b) | (c & (a ^ b)); return s, co
assert [fa(a, b, c) for a in (0, 1) for b in (0, 1) for c in (0, 1)] == [(0, 0), (1, 0), (1, 0), (0, 1), (1, 0), (0, 1), (0, 1), (1, 1)]
for a in (0, 1):
    for b in (0, 1):
        for c in (0, 1):
            assert (((1 - a) & b & c) | (a & b & c) | (a & (1 - b) & c) | (a & b & (1 - c))) == ((a & b) | (b & c) | (a & c))

lesson(c8, "logic-circuits", "Half and full adders, simplification and flip-flops",
  ["Build a half adder and a full adder from gates", "Simplify Boolean expressions with laws and Karnaugh maps", "Describe a flip-flop as one bit of memory", "Read a combinational circuit"],
  [("definition", "Adders", "A **half adder** adds two bits: **Sum = A XOR B**, **Carry = A AND B**. A **full adder** adds two bits **and a carry-in**: Sum = A XOR B XOR Cin; Carry-out = (A AND B) OR (Cin AND (A XOR B)). Chaining full adders adds multi-bit binary numbers."),
   ("methode", "Karnaugh map (K-map)", "A K-map arranges a truth table in a grid in which neighbouring cells differ in **one** variable (Gray-code order: 00, 01, 11, 10). Group adjacent 1s in rectangles of 1, 2, 4, 8 (powers of two); bigger groups give simpler terms; groups may overlap and wrap around edges. Each group gives one product term from the variables that **do not change** inside it."),
   ("methode", "Example", "Q = A'BC + ABC + AB'C + ABC' (A' means NOT A). The 1s are at 011, 111, 101, 110, which group as: (B and C), (A and C), (A and B). Simplified: Q = AB + BC + AC. This is the **majority** function: 1 when at least two inputs are 1. Check: A=1,B=1,C=0 gives AB = 1, correct."),
   ("retenir", "Flip-flop", "A **flip-flop** is a circuit with two stable states that stores **one bit**. An **SR** flip-flop (two cross-coupled NOR or NAND gates) has Set and Reset inputs; a **D flip-flop** copies its D input on the clock edge. Registers are groups of flip-flops. Sequential circuits depend on stored state as well as inputs."),
   ("pieges", "Common mistakes", "- K-map rows and columns are in Gray-code order, not 00, 01, 10, 11.\n- Groups must be rectangles whose size is a power of two.\n- XOR is not OR: 1 + 1 gives sum 0 with carry 1.\n- The inputs 'S = R = 1' is forbidden for a simple SR latch.")],
  ("Half adder", shapes([T(210, 18, "Half adder: Sum = A XOR B, Carry = A AND B", 13, bold=True),
     RECT(180, 40, 70, 40, fill="lightgreen", radius=4), T(215, 65, "XOR", 14), RECT(180, 110, 70, 40, fill="lightyellow", radius=4), T(215, 135, "AND", 14),
     T(30, 56, "A", 14), T(30, 126, "B", 14), LINE(45, 54, 180, 52, arrow="end"), LINE(45, 124, 180, 140, arrow="end"), LINE(80, 54, 80, 118, dash=True, color="grey"), LINE(80, 118, 180, 118, dash=True, color="grey", arrow="end"), LINE(95, 124, 95, 70, dash=True, color="grey"), LINE(95, 70, 180, 70, dash=True, color="grey", arrow="end"),
     LINE(250, 60, 320, 60, arrow="end"), T(355, 65, "Sum", 14), LINE(250, 130, 320, 130, arrow="end"), T(360, 135, "Carry", 14)], 420, 170),
   "A half adder drawn with one XOR gate giving the sum and one AND gate giving the carry.", "Half adder diagram: A and B go to an XOR gate (sum) and an AND gate (carry)."),
  [("Example 1 - Half adder", "Find Sum and Carry for A = 1, B = 1.", ["Sum = 1 XOR 1 = 0.", "Carry = 1 AND 1 = 1, so 1 + 1 = 10 in binary."], "Sum 0, carry 1"),
   ("Example 2 - Full adder", "Find Sum and Carry-out for A = 1, B = 1, Cin = 1.", ["Sum = 1 XOR 1 XOR 1 = 1. A XOR B = 0, so Carry-out = (1 AND 1) OR (1 AND 0) = 1.", "Check: 1 + 1 + 1 = 3 = binary 11: sum 1, carry 1."], "Sum 1, carry 1")],
  [M("The carry of a half adder is given by", "A AND B", ["A XOR B", "A OR B", "NOT A"], "Carry."),
   N("Number of inputs that give Sum = 1 in a half adder (out of 4 combinations)", 2, "01 and 10."),
   TF("A flip-flop can store one bit.", True, "Yes.")],
  [N("In a full adder, for A = 1, B = 0, Cin = 1 the sum is", 0, "1 XOR 0 XOR 1 = 0 (carry-out 1)."),
   O("Explain why a K-map uses the order 00, 01, 11, 10.", "In this Gray-code order neighbouring cells differ by only one variable, so adjacent 1s can be grouped to eliminate a variable.", "1 mark each point (2) plus 1 for example.")],
  P("Q = A'BC + ABC + AB'C + ABC' (A' = NOT A).",
    [pm("Simplified expression", "AB + BC + AC", ["A + B + C", "ABC", "AB + C"], "Majority function."),
     pn("Value of Q when A = 0, B = 1, C = 1", 1, "A'BC = 1."),
     pn("Value of Q when A = 1, B = 0, C = 0", 0, "No term is 1.")]),
  [("A full adder has", "three inputs", ["one input", "no outputs", "four outputs"], "A, B, Cin."),
   ("XOR gives 1 when", "the inputs differ", ["both inputs are 1", "both inputs are 0", "never"], "XOR."),
   ("A K-map group must contain", "1, 2, 4, 8 ... cells", ["3 cells", "5 cells", "any number"], "Powers of two."),
   ("A register is made of", "flip-flops", ["monitors", "cables", "routers"], "Registers."),
   ("A half adder cannot", "accept a carry-in", ["add two bits", "give a sum", "give a carry"], "Only two inputs.")],
  notes=["Full-adder table and K-map result verified in Python. Gate diagrams drawn without the correct gate shapes; replace with proper symbols after review."], minutes=35)

# ======================================================================= CH9 translators
c9 = p.chapter("translators", "Language translators", REF)
lesson(c9, "translators-compilers", "Compilers, interpreters and the stages of translation",
  ["Describe the stages of compilation", "Compare compilers, interpreters, assemblers and linkers", "Explain lexical, syntax and semantic errors", "Read a simple BNF definition"],
  [("definition", "Stages of compilation", "**Lexical analysis** splits the source code into **tokens** (keywords, identifiers, numbers, operators) and removes spaces and comments. **Syntax analysis** (parsing) checks the tokens against the grammar and builds a **syntax tree**. **Semantic analysis** checks meaning (for example, types). **Code generation** produces machine or intermediate code. **Optimisation** makes the code faster or smaller."),
   ("retenir", "Translators and tools", "A **compiler** translates the whole source program to an executable (fast run, errors reported together). An **interpreter** translates and runs line by line (easy testing, slower run, source needed at run time). An **assembler** translates assembly. A **linker** combines object files and libraries into one executable; a **loader** places it in memory to run. Some languages compile to **bytecode** that a virtual machine interprets."),
   ("methode", "Types of error", "**Syntax error**: breaks the grammar (a missing bracket); found at translation. **Logic error**: the program runs but gives wrong results (using + instead of -); found by testing. **Runtime error**: occurs while running (division by zero)."),
   ("methode", "BNF", "**Backus-Naur Form** defines a language's syntax with rules: <digit> ::= 0 | 1 | 2 | ... | 9 and <number> ::= <digit> | <digit><number>. The symbol | means 'or'; ::= means 'is defined as'. A string is valid if it can be built from the rules. Example: 305 is a valid <number> (digit, then digit, then digit)."),
   ("pieges", "Common mistakes", "- The compiler finds syntax errors, not logic errors.\n- Compiled code runs without the compiler; interpreted code needs the interpreter.\n- BNF rules can be recursive; a rule that never ends in a base case is invalid.")],
  ("Stages", flow("Compilation stages", ["Source code", "Lexical analysis (tokens)", "Syntax analysis (tree)", "Code generation", "Executable"], colors=["lightyellow", "lightblue", "lightblue", "lightgreen", "lightorange"]),
   "A flow from source code through lexical and syntax analysis and code generation to an executable.", "Flow of compilation stages ending in an executable."),
  [("Example 1 - Tokens", "Split the statement total = price * 3 into tokens.", ["Identifiers: total, price. Operators: = and *.", "Number: 3. So there are 5 tokens: total, =, price, *, 3."], "5 tokens"),
   ("Example 2 - BNF", "<digit> ::= 0|1|2|3|4|5|6|7|8|9 ; <number> ::= <digit> | <digit><number>. Is 4x7 valid?", ["Each character must be a digit.", "x is not a digit so 4x7 cannot be built: invalid."], "Invalid")],
  [M("The first stage of compilation is", "lexical analysis", ["code generation", "optimisation", "execution"], "Tokens first."),
   M("Which error is found by testing, not by the translator?", "Logic error", ["Syntax error", "Missing bracket", "Misspelt keyword"], "Logic errors."),
   TF("An interpreter translates and runs the program one statement at a time.", True, "Yes.")],
  [N("How many tokens are in: x = y + 4 ?", 5, "x, =, y, +, 4."),
   O("State two advantages of a compiler over an interpreter.", "The program runs faster; the executable can be distributed without the source code or translator; errors are reported before running.", "1 mark each, 2.")],
  P("Using <digit> ::= 0|1|2|3|4|5|6|7|8|9 and <number> ::= <digit> | <digit><number>.",
    [pm("Which is a valid <number>?", "2026", ["20a6", "-5", "1.5"], "Only digits are allowed."),
     pt("The rule for <number> is recursive.", True, "<number> uses itself on the right-hand side."),
     po("Why must a recursive rule have a non-recursive alternative?", "Otherwise the definition never ends; the alternative (<digit>) is the base case.", "1 mark each.")]),
  [("A linker", "combines object code and libraries", ["prints source code", "creates tokens", "types text"], "Linker."),
   ("A runtime error occurs", "while the program is running", ["during lexical analysis", "never", "only in assembly"], "Runtime."),
   ("Bytecode is run by", "a virtual machine", ["a monitor", "the keyboard", "a cable"], "VM."),
   ("A syntax tree is built during", "syntax analysis", ["lexical analysis", "linking only", "installation"], "Parsing."),
   ("In BNF the symbol | means", "or", ["and", "not", "end"], "Alternative.")],
  notes=["BNF kept at outline level; syntax diagrams not drawn. Stage names may differ slightly."], minutes=30)

# ======================================================================= MOCK
lessons_by = {}
mock(p, "1", "GCE A Level mock — Computer Science", 120,
  "Answer all questions. The paper is marked out of 20. This is an original practice paper; its format is to be checked against the official texts of the Cameroon GCE Board.",
  [("Section A - Short questions (6 marks)", [
      (c2.lessons[1], M("The complexity of binary search on n sorted items is", "O(log n)", ["O(n)", "O(n log n)", "O(n squared)"], "Halving each time.", points=1)),
      (c1.lessons[1], N("Value of floating-point mantissa 01101000 with exponent 0011 (8-bit mantissa, binary point after the first bit)", 6.5, "0.8125 x 8 = 6.5.", tol=0.001, points=1)),
      (c3.lessons[0], M("Hiding an object's data and giving access only through methods is", "encapsulation", ["inheritance", "polymorphism", "recursion"], "Encapsulation.", points=1)),
      (c4.lessons[0], TF("A table is in 3NF if no non-key field depends on another non-key field (and it is in 2NF).", True, "That is the 3NF condition.", points=1)),
      (c5.lessons[0], M("A hash of a password is stored because it", "cannot easily be reversed", ["can be decrypted with a key", "is shorter than nothing", "needs a firewall"], "One-way.", points=1)),
      (c8.lessons[0], N("In a full adder, A = 1, B = 1, Cin = 0. What is the sum bit?", 0, "1 XOR 1 XOR 0 = 0 (carry-out 1).", points=1))]),
   ("Section B - Structured questions (10 marks)", [
      (c2.lessons[0], P("A BST is built by inserting 50, 30, 70, 20, 40, 60, 80 in that order.",
         [pm("The in-order traversal is", "20, 30, 40, 50, 60, 70, 80", ["50, 30, 20, 40, 70, 60, 80", "20, 40, 30, 60, 80, 70, 50", "80, 70, 60, 50, 40, 30, 20"], "In-order of a BST is sorted.", pts=2),
          pn("The height (number of levels) of the tree", 3, "Three levels.", pts=1),
          po("State the pre-order traversal.", "50, 30, 20, 40, 70, 60, 80", "2 marks for fully correct; 1 mark if the root and left subtree are right.", pts=2)])),
      (c6.lessons[0], P("Round robin scheduling with quantum 2: P1 needs 5 units, P2 needs 3 and P3 needs 2 (all arrive at 0, queue order P1, P2, P3).",
         [pn("Time at which P3 finishes", 6, "P1 0-2, P2 2-4, P3 4-6.", pts=2),
          pn("Time at which P1 finishes", 10, "P1 runs 6-8 and 9-10.", pts=2),
          po("Name one disadvantage of a very small quantum.", "Many context switches waste CPU time.", "1 mark.", pts=1)]))]),
   ("Section C - Essay (4 marks)", [
      (c7.lessons[0], O("Compare the waterfall and agile approaches for developing a school management system.", "Waterfall: fixed requirements, stages in order, well documented, difficult to change later. Agile: short iterations with working software and user feedback, handles changing requirements, less documentation. For a school, if requirements are unclear or likely to change, agile is more suitable; if fixed and well understood, waterfall can work.", "1 mark each for a correct point on waterfall, agile, a comparison, and a justified recommendation (4 marks).", points=4))])])
write(p)
