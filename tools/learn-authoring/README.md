# Authoring « Apprendre » content (anglophone subsystem)

Format reference: `docs/LEARN.md` § 3 (JSON source format) and the existing packs `content/learn/fslc-*`, `gceol-*`.
`lessonlib.py` writes that format (no format change); the JSON under `content/learn/<pack>/` is what ships.

## Workflow
1. One generator script per pack (or group of packs): `tools/learn-authoring/anglophone/<pack id>.py`
   (`import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring"); from lessonlib import *`),
   then `python3 <script>` writes `content/learn/<pack id>/`.
2. Validate with the real validator: `tools/learn-authoring/check.sh <pack id> [...]` — must print `OK`
   (errors ✗ are blocking; fix them; read the `!` warnings too — "text overflows the frame", missing illustration…).
3. Full content tests (all packs, ~15 s): `/tmp/claude-0/-home-user-CastBridge/810643db-fe96-5ce6-aec6-b298ff5cb7e7/scratchpad/h/run.sh LearnContentTest`
   (3 tests about the embedded zip resources fail in this harness only; ignore `embeddedBudget`, `apiRoutes`,
   `libraryPrefersHigherVersionsAndSkipsBadPacks`). `answersAreConsistent`, `everyPackIsValid`, `figureTextsDoNotOverlap`… must PASS.
4. Do NOT `git commit` (the coordinator commits). Do NOT touch Kotlin, `docs/LEARN.md`, other agents' packs, `embedded.txt`.

## Constraints of the format (the validator enforces them)
- Pack = one level × one subject. `id` = folder name; every lesson/exercise/chapter id starts with the pack id (the helper does it).
- Text blocks ≤ 650 characters (one TV page), example steps ≤ 320: split long explanations into several `L.text`/`L.key`.
- Markdown subset: paragraphs, `- ` bullets, `1. ` lists, `**bold**`, `*italic*`, `$formula$` inline. NO headings, links, images, HTML inside md.
- LaTeX subset (for `$..$`, `tex=`, `L.formula`): `\frac`, `^`, `_`, `\sqrt[n]{}`, `\vec`, `\overline`, `\widehat`, `\text{}`, `\mathbb{R}`,
  `\left( \right)`, `\times \div \cdot \pm \leq \geq \neq \approx \infty \to \Rightarrow \Leftrightarrow \in \cup \cap \subset \emptyset \perp \parallel
  \angle \degree \circ \%`, Greek letters, `\lim \ln \log \exp \sin \cos \tan`, `\sum \int \partial`, `\quad`, `\,`. ANY other command is an ERROR
  (no `\begin{}`, `\mathrm`, `\operatorname`, `\overrightarrow`, `\xrightarrow`, `\mathbf`, `\binom`). Chemical formulae: plain text with Unicode
  subscripts (`H₂O`, `CO₂`, `Ca(OH)₂`) or `\text{H}_2\text{O}` in tex. When in doubt, test with check.sh. Plain text in md is fine for anything simple (e.g. `x² + 3x`).
- Figures: `shapes`, `plot` (y=f(x) curves), `timeline`, `bars`, `count`, `svg`; helpers in lessonlib (`shapes([T(..), LINE(..), …])`).
  Texts inside a figure must not overlap each other (test) and must stay inside the frame. Keep figures simple and well spaced.
  Every lesson needs ≥ 1 illustration (L.illustration, or `figure=` in an example), every pack ≥ 2; give a real `alt`.
- Fiche type (applied to EVERY lesson, exam pack or not): objectives; ≥ 2 `key` blocks (« l'essentiel », include one `pieges` or `attention`);
  **2 worked examples** (≥ 2 steps each); exercises **3 application + 2 approfondissement + 1 examen**; **5 self-check MCQs** (exactly 4 choices).
  Exercise kinds: `mcq`, `tf`, `num`, `match`, `open`, `problem` (≥ 2 parts). Every exercise has an explanation (corrigé); `open` has model + rubric.
  Use mixed kinds (not only MCQ); vary the position of the right answer (the helper shuffles).
- Exam-level packs (`exam=` set: FSLC Class 6, GCE-OL Form 5, GCE-AL Upper Sixth) also need ≥ 4 lessons, ≥ 20 exercises and ≥ 1 mock paper
  (`p.mock(...)`: sections of `mock=True` exercises whose points sum to exactly 20; no `review` flag inside a mock).
- Status is always `draft` (the helper sets it) + `reviewNotes` listing what a teacher must verify. NEVER write `reviewed`/`validated`.
  Put real doubts in `notes=[...]` of each lesson (specific: which fact/figure/convention is uncertain).

## Content quality rules (owner's rules — non-negotiable)
1. ORIGINAL writing in your own words. Never reproduce textbooks, past papers, mark schemes or copyrighted banks. Exercises are your own.
2. NO invented facts, dates, statistics or unchecked formulas. If unsure → leave it out, or state it vaguely ("in the early 1900s") and add it to the lesson's `notes`.
   Maths/science: compute every answer IN PYTHON inside the script (use the computed value as the answer, add `assert`s), re-derive every worked example
   step by step, check units, significant figures and that distractors in MCQs are really wrong. Numeric answers: set `tolerance` when rounding is involved.
   Chemistry/physics/biology: use standard textbook-level facts only; constants as in common school tables (state the value you use in the text, e.g. g = 10 m/s² or 9.8 – choose one and say so).
   History/geography/civics: only well-established facts of Cameroon and the world; no precise statistics unless certain (say "about"); avoid contested claims or political opinion; neutral tone.
3. Pedagogy: clear lesson (definition → rule/formula → method → worked examples → common mistakes), graded exercises, answers with explanations,
   exam-style exercise written by you in the style of the Cameroon GCE Board / FSLC. Age-appropriate language (very simple English for Class 1–3, short sentences, read-aloud friendly).
4. Cameroonian context in examples: places (Douala, Yaoundé, Bamenda, Buea, Limbe, Bafoussam, Garoua, Kumba, Bali…), FCFA prices, local crops (cocoa, coffee, cassava, plantain, maize,
   groundnut, banana), food (ndolé, eru, achu, fufu corn, puff-puff, koki), Mount Cameroon, River Wouri/Sanaga, Lake Nyos... Keep realistic numbers. No real private persons.
5. Respectful, inclusive; no stereotypes; health content (hygiene, nutrition…) conservative and flagged for review by a health worker.
6. Size: keep each pack well under 300 KB of JSON (the lot limit is 3 MB per class/level across subjects).

## Subjects not in the app catalog yet
`LearnCatalog.subjects` has: maths, english, sciences, physics, chemistry, biology, histoire-geo, economie (+ French-system ones). Until the platform
agents add keys, map provisionally and record the ideal key with `wanted=`:
Literature in English → `subject="english"`, `wanted="literature"` · Further Mathematics → `"maths"`, `wanted="further-maths"` ·
Geography / History / Citizenship / Social Studies → `"histoire-geo"`, `wanted="geography"|"history"|"citizenship"|"social-studies"` ·
Computer Science/ICT → `"sciences"`, `wanted="computer-science"` · Food Science & Nutrition → `"sciences"`, `wanted="food-science"` ·
Basic/Elementary Science → `"sciences"`. The pack `title` always names the real subject ("Literature in English — Form 3").

## Pack id convention
`<level>-<subject>`: `class1-english`, `class3-maths`, `form2-geography`, `lower6-physics`; exam packs at exam levels keep/extend the existing ids
(`fslc-english`, `fslc-maths`, `fslc-science`, `gceol-english`, `gceol-maths`, `gceol-biology`) or use `fslc-<subject>`, `gceol-<subject>`, `gceal-<subject>`.
Use `Pack(..., extend=True)` to ADD chapters to an existing pack without touching its present lessons.
