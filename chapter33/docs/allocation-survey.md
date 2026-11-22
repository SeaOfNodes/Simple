# Allocation survey: existing Simple programs

Measured 2026-11-21, at TypeCheck with assertions enabled. This surveys the
current analysis; it does not implement or validate stack allocation at runtime.

The fixed-size plans handle useful object graphs and native borrowing.
Variable-sized storage is now the broadest limitation; the original baseline
below records the earlier native-call and raw-address blockers.

## Update: lexical KeepAlive

The parser now retains converted addresses through their lexical scope exits.
This admits integer arguments forwarded through Simple wrappers and addresses
stored and reloaded within that scope. Conditional conversions, overwritten
locals, return/break/continue paths, implicit branch scopes, forward calls, and
imported bodies are covered by the chapter tests. A returned integer does not
promote its backing storage into the caller's lifetime.

The large-program totals remain 134/179 accepted for `sys` source and
52/68 for the examples' own functions. Those programs had already passed
the simpler borrowing checks or encountered another blocker first.

The current first-cause breakdown for the 45 rejected `sys` functions is:

| Cause | Direct | Blocked callers |
|---|---:|---:|
| Variable-size allocation | 7 | 32 |
| Recursion | 1 | 4 |
| Native pointer result without an allocation contract | 1 | 0 |

First-cause counts can change with optimization order when a function has several
blockers. The native-result requirement remains; this is not permission to assume
that a native-produced pointer is borrowed. The examples' remaining failures
are eleven missing imported bodies, four variable-size plans, and one indirect
callback.

The next substantial gains need variable-size plans and allocation contracts
for imported bodies. Recursion and Breakout's indirect callback remain separate
work. `_exit` still needs the agreed nonreturning handling. KeepAlive is erased
before scheduling; storage placement still uses function/iteration frames and
does not yet change runtime allocation.

## Update: native borrowing

After adopting the native non-retention rule and tracking pointer-derived
integer SSA values through calls, the same cohorts improve as follows. The
remaining sections preserve the original survey and its baseline snapshot.

| Cohort | Accepted before | Accepted now | With user allocations now |
|---|---:|---:|---:|
| `sys` source, 179 functions | 98 | 134 | 12 |
| Examples' own 68 functions | 43 | 52 | 4 |

Hello world's executable initializer now gets a supported plan. Clock's `_hand`
and `_analog` pass. Breakout improves from 19/24 to 22/24; its `_tone` buffer is
now accepted as scratch. `sys.time.local` now accepts the DateTime result and
its temporary native buffer.

Of the remaining 45 `sys` rejections, 38 stop at variable-sized allocation
(seven direct, 31 callers), five at recursion (one direct, four callers), and
two at native-produced Simple pointers without allocation contracts. No call
or raw-address rejection remains the first cause in that source cohort.
Precompiled-library examples still expose missing imported summaries, such as
`sys.ary.capacity`, `sys.ary.copy`, and `sys.aryu8.str0`. Breakout's `debounce`
also contains a genuinely indirect callback.

This change is analysis-only. `_exit` is accepted under the native borrowing
rule, but is not yet a nonreturning intrinsic. The ABI, allocation lowering,
and default runtime allocation behavior are unchanged.

## Original baseline

| Cohort | Function summaries | Accepted | Accepted with user allocations |
|---|---:|---:|---:|
| `sys`, compiled from source | 179 | 98 | 11 |
| Ten examples, their own functions only | 68 | 43 | 3 |
| Native argument conversion, compiled separately | 2 | 1 | 0 |

These counts include retained/exported functions and class initializers, not
just functions reachable by direct calls from an entry point. Native declarations
without a Simple body are not separate function summaries. A passing function
can still call other passing functions that allocate. The last column counts
fresh storage represented in that function's plan, including imported callee
sites; it excludes compiler class objects. It is not a count of dynamic objects.
All 87 other accepted `sys` functions have no remaining allocation sites.

## What passes

The accepted `sys` plans include 19 user-storage sites: 16 with result destinations
and three retained through constructor receiver `arg2`.

- `sys.time.utc` builds and returns a DateTime: a real fixed-size factory, with
  calendar computation and conditional paths.
- `sys.time.Timers.<new>` returns a timer object and its three fixed initial
  arrays. The initializer puts the arrays in the receiver's lifetime; the factory
  composes that into the returned graph.
- `sys.aryu8.<new>` and `sys.text.begin` return a byte-vector object plus its
  initial backing array. Growing that array is a separate, rejected operation.
- Bitset construction, Button construction, and scanner construction pass.
- All six `sys.scan` summaries and all six `sys.draw.Button` summaries pass.
  Canvas has 15 passing summaries out of 18: the drawing operations work on
  borrowed storage; construction and resizing encounter allocation/call barriers.
- Breakout's Game factory returns the Game and its 192-element brick array.
  Its initializer correctly gives the array the receiver's lifetime. Most game
  logic and drawing routines pass too.

The accepted `sys` functions contain 26 loop summaries. Ordinary loops over
existing storage are not the main obstacle.

## Where the library stops

The pass records the first rejection in a function. A caller can then inherit
that rejection. Following those call chains gives:

| First underlying reason | Directly rejected functions | Blocked callers | Total |
|---|---:|---:|---:|
| Opaque or indirect call | 35 | 18 | 53 |
| Raw-address conversion | 11 | 10 | 21 |
| Variable-size allocation | 2 | 2 | 4 |
| Recursion | 1 | 2 | 3 |
| Total | 49 | 32 | 81 |

These are first-cause counts, not independent fixes or predicted acceptance
after removing a barrier. For example, `sys.aryu8._grow` accounts for 16 rejected
summaries including itself. Its first rejection is the `_exit(1)` call used for
overflow failure, but it also allocates a variable-sized array. Accepting that
call alone would not make growth work.

Independent inspection of the collected IR finds 21 variable-sized NewNodes
across 19 `sys` functions. Only two functions currently report that as their
first direct failure: `sys.text.end` and `sys.adt.bitset._grow`.

There were no first-reported loop-retention, cyclic-field-path, or unmapped-origin
failures in this library cohort. Earlier rejection barriers can hide them.

## The examples

These use the normal precompiled `sys` library. Counts include only each
example's own functions, avoiding repeated counts of imported library exports.
The compiler may load and inline imported bodies; some others remain opaque.

| Example | Accepted / total | Accepted with allocations | Representative failure |
|---|---:|---:|---|
| Hello world | 0 / 1 | 0 | String address passed to output |
| BubbleSort | 2 / 4 | 0 | Output; sorting itself passes |
| Capitalize | 1 / 2 | 0 | Output; main also has a variable-sized array |
| FileIO | 1 / 4 | 0 | Native I/O and raw addresses |
| Dijkstra | 1 / 4 | 0 | Bitset growth; output |
| Graphics | 1 / 2 | 0 | Raw address passed to graphics |
| Clock | 3 / 7 | 0 | Graphics/time pointers; scalar libm calls |
| Breakout | 19 / 24 | 3 | Input/audio/graphics calls |
| TicTacToe | 4 / 8 | 0 | I/O, growth, an opaque imported `sys.ary.capacity` |
| TicTacToeGraphical | 11 / 12 | 0 | Main's graphics boundary |

No example's `main` receives a complete supported plan; hello world's executable
class initializer is rejected too. High per-function acceptance is not yet a
working automatically managed program.

## Analysis improvements versus storage decisions

| Case | What is missing | Likely action |
|---|---|---|
| Clock's `sin`/`cos`, clocks, input polling, `_exit` | Known native behavior is treated as opaque | Targeted native contracts/recognition; no application rewrite should be needed |
| Array passed to synchronous I/O or copying audio loader | Lifetime/provenance is lost at conversion to `i64` | Wrapper contract and provenance tracking through the native call |
| Opaque imported Simple function | No serialized allocation summary | Export/import lifetime summaries; avoid changing source merely to force inlining |
| Runtime-sized local scratch array | Immediate rejection of nonconstant size | Keep the lifetime result and represent a runtime size; later lower a local reservation |
| Finished variable-sized result, such as `sys.text.end` | Caller needs storage whose size the callee discovers | Destination/buffer/region choice under the agreed design; possibly a small library rewrite |
| Replaced vector/Canvas backing array | Dynamic sizing plus proof that old aliases are no longer usable | Analysis for reclaiming old buffers; explicit storage policy where proof fails |
| Fixed-size object repeatedly replacing `Holder.c` | Weak updates never kill the old reference | Overwrite/liveness analysis; this is not inherently unbounded storage |
| `Timers._add` retries recursively after growing | SCC support, plus three variable-sized arrays | Defer SCCs; changing the retry to a loop alone would not solve storage |

Native scalar signatures must not be accepted indiscriminately: an `i64` may
carry a raw pointer. Contracts must describe the actual wrapper, including
whether it retains a passed address or creates returned storage.

One concrete native allocation requires attention:
[`simple_text_float`](../src/main/c/simple_crt.c) uses `malloc` to create a
Simple-format byte array and returns it. There is no Simple NewNode for that
allocation. A non-retaining-call annotation is insufficient: the result needs
an allocation/ownership contract, or the wrapper should write into caller-provided
storage. This is a useful first native-interface design case.

The separately compiled [`simple_args`](../src/main/smp/sys/rt/simple_args.smp)
has two variable-sized allocation sites: an outer reference array and byte
arrays created inside its initializer loop. Its first rejection is the raw
destination passed to `memcpy`. Beyond that contract, it exercises the planned
collection destination and variable-sized returned graph.

## Small reductions

The survey probe also compiles four isolated cases, without native calls:

| Probe | Result |
|---|---|
| Allocate `i64[n]`, fill/read it, return a scalar | Rejected: variable-size allocation |
| Return a freshly allocated `i64[n]` | Rejected for the same reason, despite a different storage problem |
| Repeatedly replace `h.c` with a fresh fixed-size Thing, then read the last one | Rejected: Thing survives the backedge |
| Allocate one mutable Thing before the loop, overwrite its scalar field, borrow it each iteration | Accepted: one Thing in the function frame |

The overwrite reduction has no saved reference to an older Thing. It needs an
analysis improvement, not a language prohibition. Reusing one mutable object
is a possible rewrite for that particular program, not a general replacement
for allocations with distinct observable identities.

## Suggested next work

First, admit a small audited set of scalar native calls and specify borrowing
for the common array wrappers. Preserve those contracts across imported calls.
That removes the largest observational barrier without tackling SCCs.

Then distinguish local variable-sized scratch from returned/growing storage,
and tackle the fixed-size overwrite proof. Re-run this survey after those steps;
the first-cause distribution should become much more informative.

For an initial lowering implementation, the passing Game, Timers, byte-vector,
and DateTime factories already supply useful fixed-size object graphs. Use them
alongside the existing scratch/winner poster children. Do not wait for every
library operation to pass before validating real storage placement.

## Reproduction and limitations

[`AllocationSurvey.java`](AllocationSurvey.java) is a standalone observational
probe. It reads private analysis collections by reflection rather than adding
production compiler APIs. It does not change contracts, suppress rejections,
link native code, run games, or lower allocation plans.
The [function-level snapshot](allocation-survey-functions.tsv) contains the
cohorts above and the four reductions, with first-cause chains and site counts.
Full dumps, allocation sites, and call lists are written under
`build/allocation-survey/<name>-<mode>/`.

From `chapter33`, after building the Java compiler and the normal `sys` objects:

```powershell
javac -cp build/classes/main -d build/allocation-survey docs/AllocationSurvey.java
java -ea -Xss16m -Xmx2g -XX:ActiveProcessorCount=2 -cp 'build/classes/main;build/allocation-survey' AllocationSurvey sys source
java -ea -Xss16m -Xmx2g -XX:ActiveProcessorCount=2 -cp 'build/classes/main;build/allocation-survey' AllocationSurvey Clock object
java -ea -Xss16m -Xmx2g -XX:ActiveProcessorCount=2 -cp 'build/classes/main;build/allocation-survey' AllocationSurvey OverwrittenSlot probe
```

Use each example's filename without `.smp`; modes are `source` and `object`.
`RuntimeArgs source` compiles the native-startup helper. Probe names are
`LocalArray`, `ReturnedArray`, `OverwrittenSlot`, and `ReusedObject`.
On bash use `:` instead of `;` in the classpath.

The auxiliary all-source run succeeds for hello world, BubbleSort, Capitalize,
and Breakout. Breakout's own 19/24 result is unchanged with library source visible.
The other six examples stop in Parse on qualified `sys` types, before Allocation;
they are not counted as lifetime failures. The source-mode probe prepends a
private `sys` binding to request library discovery; that does not resolve those
type-name failures. A first all-source Breakout run exhausted the default Java
stack in `Node._walk`; all reported runs use `-Xss16m`.

Counts use seed 456 and optimized IR, so inlining and dead-code removal affect
which sites and functions remain. They measure the current conservative analysis,
not runtime allocation frequency, memory saved, or proof that future lowering is
correct. Existing compiler files were not changed for this survey.
