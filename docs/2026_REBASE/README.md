# A Simple Tutorial — REBASE 2026

Cliff Click's 30-minute presentation for an audience familiar with compilers,
SSA, and program analysis. Simple is an open-source, all-Java compiler tutorial
modeled after Java's C2.

## Run the deck

From the repository root:

    python -m http.server 8000 --bind 127.0.0.1 --directory docs/2026_REBASE

Open [the presentation](http://127.0.0.1:8000/). Keep that terminal running and
use a second terminal for the graph viewer. Opening `index.html` directly also
works; a local HTTP server gives the most consistent clipboard and presenter
window behavior.

There is no npm install, build step, CDN, remote font, or network dependency.
The portrait, CCC artwork, code, and captured graphs are all in this directory.
External links are ordinary links, not embedded services.

- **Right / Space:** next build; **Left:** previous build.
- **Page Down / Page Up:** next / previous slide.
- **Home / End:** title / closing slide.
- **L:** cycle the example and lattice tabs on the current slide.
- **O:** overview, including the optional Chapter 25 slide.
- **P:** open a synchronized presenter window with notes and a talk timer.
- **N:** show notes on the current screen; avoid this on the projected screen.
- **T:** start / pause the timer. It keeps running while a viewer tab is active.
- **F:** fullscreen; **B:** blank / unblank; **?:** shortcut reference.

The title is still. The still CCC GIF is on the biography slide. Other slides have
manual builds, with no automatic slide advancement. Reduced-motion preferences
disable slide transitions.

## The 30-minute route

| Time | Slide | What to show |
|---|---|---|
| 00:00–01:00 | A Simple Tutorial | The all-Java, open-source C2 connection |
| 01:00–02:00 | Who am I? | Short bio, Coffee Compiler Club, links |
| 02:00–07:00 | Chapters 1–4 | Direct SSA construction and peepholes; live Chapter 4 |
| 07:00–12:00 | Chapters 5–8 | Regions, Phis, and loops; live Chapter 8 |
| 12:00–17:00 | Chapters 9–17b | Memory SSA and alias slices; live Chapter 17b |
| 17:00–22:00 | Chapter 18 | Functions and calls; live demo of trivial inlining and constant folding |
| 22:00–24:00 | Chapters 19–21 | Instruction selection, allocation, encoding |
| 24:00–26:00 | Chapters 22–23 | I/O, C calls, methods, and cyclic types |
| 26:00–28:00 | Chapter 24 | Optimistic propagation through a loop |
| 28:00–30:00 | Takeaways | Discussion and links |

Chapter 25 is a two-minute optional slide. Normal forward navigation skips it.
Choose it in the overview or from the closing slide, replacing the final
discussion if desired. It is not an extra two minutes in the default schedule.

Each live segment includes roughly 45 seconds on the slide, 30 seconds to
switch tabs, three minutes in the viewer, and a short wrap-up. Speaker notes
contain the detailed beats and the cumulative time to leave each segment.

## Lattice views

The chapter slides have tabs in their header. Click a tab or press **L** to
cycle through the views; the example remains the initial view. Memory still
starts with the short alias example. Tabs synchronize with the presenter
window, and a URL such as `#memory/0/contents` opens a lattice directly.

| Slide | Lattice views |
|---|---|
| Chapters 1?4 | Global bounds, control, flat integer constants, tuples |
| Chapters 5?8 | Live/dead control and Phi input types; integers remain flat |
| Chapters 9?17b | Chapter 11 domains; typed memory contents; Chapter 14 ranges |
| Chapter 18 | Function target sets, with the other type components held fixed |
| Chapter 24 | Rich lattice across all type families |

Use about 30 seconds of each existing introduction for the lattice. On the
memory slide, spend about 20 seconds each on Domains and Memory types; Ranges
is available if useful and is revisited at Chapter 24. These views share the
existing time slots rather than adding slides to the 30-minute route. Right
advances to the next slide from a lattice; click Example or press L to return
to the source before switching to the viewer.

The SVG diagrams in `lattices.js` are drawn for projection. They distinguish
full domain overviews from selected sublattices and projections. Gray lines
show lattice order, not IR use/definition edges. The orange SCCP curve marks
an analysis update. In particular, Chapter 11's flat memory aliases are not
presented as Chapter 17b's full typed-memory lattice, and the function picture
shows only the target-set component.

Sources in this repository:

- Chapter 4 `docs/lattice.gv`, README, and `type/Type*.java`.
- Chapter 6 `docs/lattice.gv`, retained in Chapter 8's types and Phi rules.
- Chapter 11 `docs/lattice.gv` and `TypeMem.java` for the domain overview.
- Chapter 17b `TypeMem.java` for fixed-alias contents and their meet.
- Chapter 14 `TypeInteger.java` for range meet and endpoint-swapping dual.
- Chapter 18 `TypeFunPtr.java` for target-bit union and the remaining components.
- Chapter 24 README for optimistic propagation and the fixed-point example.

## Prepare the live demos

Use the repository's normal JDK and Make setup. Prebuild the demo chapters
before the session so compilation does not consume stage time:

    make -C chapter04 build CTAGS=
    make -C chapter08 build CTAGS=
    make -C chapter17b build CTAGS=
    make -C chapter18 build CTAGS=
    make -C chapter25 build CTAGS=

The deck's **Copy viewer command** button gives one of these commands:

    make -C chapter04 view
    make -C chapter08 view
    make -C chapter17b view
    make -C chapter18 view
    make -C chapter25 view

The Chapter 24 SCCP slide deliberately launches **Chapter 25's viewer** for
this talk, using the same example with a separately compiled standard library.
Chapter 24's viewer captures the library afresh on each compile (about 3,281
frames and 372 MB for this example), which can overwhelm the browser. The slide
topic, source, and lattice explanations remain Chapter 24.

Run one viewer at a time. Stop the previous one with Ctrl+C in its terminal
before launching the next. Each launch opens a viewer page and prints its URL;
the HTTP port can change. Keep only the current viewer tab to make switching
between slides and viewer predictable. All ordinary viewer sessions use
WebSocket port 12345.

On the slide, **Copy code**, switch to the viewer, paste into the source editor,
and click **Compile** (or Ctrl+Enter). Move focus out of the editor before using
Left/Right for playback. Structural peepholes use gather → rewrite → release.
Jump to **Last** to orient the audience or recover time; the larger examples
are not intended to be stepped through from beginning to end.

The viewer animates compilation, not runtime execution. It captures
parsing/optimization/type checking as supported by the chapter; it does not
show the global scheduler moving instructions. Discuss GCM from the slide.

| Source | Chapter | Checkpoint / result |
|---|---|---|
| [arithmetic.smp](demos/arithmetic.smp) | 04 | Final expression `2*arg+6`; 51 capture frames |
| [control.smp](demos/control.smp) | 08 | Sum 1 through `abs(arg)`; 65 frames |
| [memory.smp](demos/memory.smp) | 17b | Two loops, array/object memory; 319 frames, 54 final nodes |
| [aliases.smp](demos/aliases.smp) | 17b | Short follow-up: writing `p.y` leaves `p.x` unchanged; result 0 |
| [inline.smp](demos/inline.smp) | 18 | Two single-caller functions disappear; `return 11;`; 161 frames |
| [sccp.smp](demos/sccp.smp) | 24; viewer 25 | Return value 1 verified in the final graph; 76 frames, about 0.38 MB |

The large memory example has runtime result `4*arg+10`. It retains loops and
memory operations; that formula is not its claimed optimized graph.
For SCCP, proving a constant result alone does not license deleting effects
or a possibly nonterminating loop.

The five **Final graph** buttons open captured compiler graphs, available
offline. These are also a fallback if a viewer session fails. They were
generated with the shared graph renderer from actual compiler snapshots.

## Equivalence classes and research context

Slide 5 partitions whole memory into `Pair.x`, `Pair.y`, and all other
locations. A field class covers that field across all instances: accesses in
the same class may alias; accesses in different classes cannot. The second
build highlights the `Pair.y` slice changed by the Store. The array example
uses the corresponding `Totals.sum` / `Totals.visits` partition.

The linked precedent is Amer Diwan, Kathryn S. McKinley, and J. Eliot B. Moss,
[?Type-Based Alias Analysis,? PLDI 1998, pp. 106?117](https://people.cs.umass.edu/~moss/papers/pldi-1998-tbaa.pdf).
Sections 3.5?3.6 (printed p. 113; PDF page 8) compare redundant load elimination
(RLE) with a runtime upper bound. Their type-based analysis came close to that
bound on eight Modula-3 benchmarks; substantially more static opportunities
did not produce comparable runtime gains. The average remaining headroom was
at most 2.5% more heap loads eliminated, not a 2.5% speedup claim.

This is a useful precedent for simple alias models, rather than a measurement
of Simple's exact equivalence classes or a universal limit on Java optimization.
It is the closest identified match to the recalled oracle study; an exact
IBM/Java paper was not established. The visible citation identifies Modula-3,
and the speaker notes retain the scope and measurement details.

## GitHub hosting

Commit this directory with the repository. GitHub's normal source browser shows
HTML source; a GitHub Pages site serves the presentation as a running page.
No generated build output is required.

- If Pages serves the repository's `/docs` directory, the deck is at
  `/Simple/2026_REBASE/` on that Pages site.
- If Pages serves the repository root, the deck is at
  `/Simple/docs/2026_REBASE/`.
- An existing Pages workflow can copy this directory unchanged to the chosen
  location in its site artifact.

All asset paths are relative, so the folder can also be copied to another
static host. Setting up or changing the repository's Pages deployment is
separate from editing the deck.

## Editing

Slide 8's **Chapter 22** column opens an offline file explorer over the current
Chapter 25 standard library: `sys.smp`, all eight files directly under `sys/`,
and `sys/adt/bitset.smp`. Click folders and files; use **Up** or the breadcrumbs
to navigate. File views scroll, **Full width** expands the explorer, and
**Discussion** restores the normal column. **Copy file** copies the complete
source. Paths, file scrolling, and open views synchronize with the presenter.
While the file text has focus, navigation keys scroll it instead of advancing
slides. This is a bundled source snapshot, so it also works from `file://`.

The **Chapter 23** column flips to a complete [Counter example](demos/methods.smp)
with an instance field, `add` and `get` methods, and calls chained through
`self`. It passes Chapter 23 parsing, optimization, type checking, and evaluation:
inputs −5, 0, and 5 return 2, 7, and 12. The library explorer deliberately uses
the latest chapter; the short methods example is valid in Chapter 23 itself.

Refresh the library snapshot and embedded methods source from the repository:

```sh
python docs/2026_REBASE/tools/snapshot-library.py
```

Chapter 24 is titled **Sparse Conditional Constant Propagation**. Its **Rich
lattice** tab (`#sccp/0/rich`) shows the actual ordering of 143 representative
types from Chapter 24's `Type.gather()`, including their duals. The 256 edges
are transitively reduced within that sample. This covers every gathered type
family, without attempting to enumerate all intervals or recursive aggregate
types. Click the diagram to open the full-size SVG; node tooltips include the
full type spelling. The two takeaways are “It’s just a lattice, so the
fixed-point theory works” and “It’s a rich lattice, so we get rich results.”

Regenerate with Chapter 24 built and Graphviz installed:

```sh
java -ea --class-path chapter24/build/classes/main docs/2026_REBASE/tools/RichLattice.java
dot -Tsvg docs/2026_REBASE/assets/rich-lattice.gv -o docs/2026_REBASE/assets/rich-lattice.svg
```

Slide 7's three backend columns are clickable: each flips between its discussion
and a captured compiler dump. Enter or Space also flips a focused column.
Flips persist across builds and synchronize with the presenter window.
The shared [backend example](demos/backend.smp) takes an existing writable Box,
adds `abs(arg)` into its field, and returns the absolute value. No allocation
is needed in the example.

All three stages are captured from **Chapter 21**, targeting x86-64 System V;
Chapters 19 and 20 do not yet select unary negation. The Chapter 19 column shows
all 21 selected nodes, with concrete class names and every input slot. It omits
types and redundant use lists to fit. The Chapter 20 and 21 columns show
`CodeGen.asm()` after register allocation and encoding, respectively, with only
whitespace and divider lengths compacted. The unabridged captures are
[selected IR](assets/backend-selected.txt),
[allocated assembly](assets/backend-registers.txt), and
[encoded assembly](assets/backend-encoded.txt).

The allocated listing still has provisional offsets, a not-yet-encoded jump,
and callee-save bookkeeping. Encoding resolves the layout and removes the extra
jump. `NegX86` emits `48 f7 d8`; `MemAddX86` emits `48 01 07` to add `rax` into
`[rdi]`. The compiler printer calls its compare-with-zero `test`, although the
bytes `48 83 f8 00` encode `cmp rax, 0`.

Regenerate these captures and `backend.js` from the repository root:

```sh
make -C chapter21 build/classes/main/com/seaofnodes/simple/codegen/CodeGen.class
java -ea --class-path chapter21/build/classes/main docs/2026_REBASE/tools/BackendDump.java
```

- `deck.js`: speaker links and demo source strings.
- `backend.js`: generated backend source and compiler dumps.
- `stdlib.js`: generated library snapshot and methods source.
- `language.js`: library explorer and methods view.
- `slides.js`: slide content, manual builds, durations, and speaker notes.
- `lattices.js`: lattice diagrams, tab definitions, and their speaker notes.
- `slides.css`: layout and transitions.
- `app.js`: navigation, clipboard, notes, presenter synchronization, and timer.
- `demos/*.smp`: standalone copies of the examples. Keep these identical to
  their `deck.js` strings; `backend.smp` instead feeds `BackendDump.java`, and
  `methods.smp` feeds `snapshot-library.py`.
- `assets/*-final.json` and `.svg`: captured graph snapshots and their exports.

YouTube uses [@compilers](https://www.youtube.com/@compilers). The short bio is
adapted from Cliff's supplied 2024 biography. Portrait and CCC artwork were
supplied by Cliff and copied into `assets/`.

The bio and closing slides ask people to send an email for a Discord invite.

Captured SVGs can be regenerated from their JSON with
`graph/render_docs.py --browser msedge` followed by the relevant snapshot paths.
This optional regeneration tool uses Python Playwright; the deck itself does
not.

## Validation

The examples were run with assertions enabled in their own chapter compilers
and evaluators:

| Example | arg = -5 | arg = 0 | arg = 5 |
|---|---:|---:|---:|
| Arithmetic | -4 | 6 | 16 |
| Control | 15 | 0 | 15 |
| Memory | -10 | 10 | 30 |
| Aliases | 0 | 0 | 0 |
| Inlining | 11 | 11 | 11 |
| SCCP | 1 | 1 | 1 |

Chapter 4 is checked by parsing with constant argument types; later chapters
use their evaluator. The four live demos and alias follow-up also completed
their real graph-capture paths without diagnostics. Final snapshots verify
the arithmetic shape, zero alias-example result, and absence of Calls with
return value 11 after inlining.

Headless browser checks cover navigation/builds, clipboard buttons, demo
selection, fallback graphs, the optional route, presenter synchronization,
the timer, local-file loading, reduced-motion transitions, and slide overflow.
Lattice checks cover every tab, direct links, retained demo selection, and
presenter synchronization. The displayed meet/dual and target-union examples
were checked against the chapter 4, 8, 11, 14, 17b, 18, and 24 type implementations.
