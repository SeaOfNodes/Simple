# Chapter split cleanup audit

This record distinguishes checked behavior, existing gaps, and future designs
while retiring the old combined Chapters 24/25. The final mapping is in the
[split record](chapter-split.md). Historical Java test names are retained as
regression identities; they do not identify current chapter boundaries.

## Corrections in this cleanup

- Chained comparisons now begin in Chapter 23. The early SCCP implementations
  incorrectly compared a boolean intermediate with the third operand:
  `10 < 30 < 20` returned true. Later implementations preserved the middle
  operand but reused the first operator, making `0 < 2 <= 2` false. All current
  snapshots from 23 onward now compare adjacent values using each written
  operator. Regression cases cover both directions, strict/non-strict mixtures,
  single evaluation, side effects, short-circuiting, and mixed-direction errors.
- Chapter 23's inherited, explicitly described fuzzer scratch contains many
  comparisons using the former left-associative grammar. It is retained as an
  ignored `testJigFuzzer`, consistent with the scratch-test policy; its mixed
  directions are no longer valid source. Later snapshots already omitted it.
- Native-library and tuning material is preserved in [appendices](../docs/appendices/README.md),
  and unfinished work is collected in the [roadmap](../docs/roadmap.md).
- The recursive-return diagnostic already passed. It remains enabled in the
  earlier snapshots and is enabled again in 28a/28b. The mutual-recursion test had an obsolete graph-string
  expectation; it now checks its existing four execution results in 24bÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“28b.
  In 24a, local SCCP cannot infer the recursive return type; the same example
  checks that conservative diagnostic instead. This makes the boundary explicit.
- Allocation/runtime measurement runs passed after the comparison correction;
  the changed spill goldens record the resulting smaller graphs. Historical
  published tables retain their original compiler labels and dates.

## Prefix-update follow-up

Prefix `++` and `--` now use the same name lookup as ordinary reads and postfix
updates in Chapters 23 through 28b. A method's field goes through memory; a
constructor-local field stays in SSA. Previously, the prefix path updated the
field declaration's parser slot and silently lost the write. The two-call
reproducer now returns 21 instead of 11. Narrow update results stay alive while
Store folding drops a redundant conversion from its own input.

`PrefixUpdateTest` covers increment/decrement, integer/float/byte fields,
multiple worklist seeds, local shadowing, final-field errors, native byte
wraparound, and module fields where supported. Existing final-constructor
coverage includes prefix updates in loops.

The focused Linux checks pass in all eight affected chapters, including native
SystemV byte-wraparound execution and the existing final-constructor tests.
The early final-field rejection is retained so an illegal prefix update never
enters the optimizer as a Store; native data bindings still update their writable
contents despite having a fixed address.

The Windows full suites cover 4,169 tests plus eight fuzzer regression runs;
shared printer and ISA checks pass. Focused reruns cover the final diagnostic
and prefix-result lifetime adjustments. The full Chapter 27 suite was rerun
after restricting the temporary keep to prefix results; its existing spill
expectations remain unchanged. No allocator goldens were updated.

## Implemented, with narrower limits than earlier prose suggested

The constructor-loop assertion is fixed in Chapters 25–27. Load forwarding now
waits for a stored loop Phi to acquire a type at least as precise as the Load,
and records a dependency to retry. The reduced case needs only a conditional
assignment in a loop; break/continue and final fields are not required.
Regressions cover the reduced case and the original constructor loop across
worklist seeds and execution inputs. Chapters 28a/28b already had the guard;
the corresponding earlier-chapter probes pass in 23/24a/24b.

- Separate compilation imports types and optimized IR without source parsing;
  it reruns analysis and type checking with the new callers.
- Loading is by object and recorded dependencies, not individual used members.
  A call to `sys.io.p` loads all ten bundled sys units. Independent, unrelated
  objects can remain unloaded. Loading does not imply class initialization.
- Timestamp checks do not yet provide a reliable incremental build system.
  The [design note](incremental-builds.md) records the missing pieces.
- Underscores select exports in modules; source privacy checks are still a
  proposal awaiting review. Do not treat hidden exports as inaccessible source.
- Constructor-local final fields use SSA values and exit Stores. Reads of an
  unfinished object are allowed only for initialized fields. Passing unfinished
  self to arbitrary helpers and accepting completion only after SCCP are not
  promised by the current chapter boundary.

## Existing failures and deferred validation

- The ideal evaluator does not truncate narrow stores in Chapter 23. A byte
  update can therefore disagree with generated code; the postfix-only
  reproducer is retained in the [roadmap](../docs/roadmap.md#narrow-stores-in-the-ideal-evaluator).
- `testModule0` is an ignored historical incremental-build experiment. It
  assumes per-source output objects and unchanged explicit-input builds, neither
  of which matches the present compiler's contract.
- Serialization's expensive byte-for-byte round-trip assertion remains disabled.
  Ordinary import tests do not establish that stronger property.
- Direct probes of the remaining ignored tests found a stale forward-method
  graph expectation and a missing `progs/sieve.smp` fixture in 28b. These and
  the interactive stdin test remain ignored. Ignored Jig and
  random-fuzzer entry points are intentional scratch/long-running tools.
  Audit these separately before advertising their behavior as tested.

Selective loading, full incremental builds, source privacy, compact pointers,
argument arrays, deterministic inlining, and broader constructor rules remain
future work. They are not prerequisites for completing the chapter numbering.

## Validation

The affected snapshots (23, 24a, 24b, 25, 26, 27, 28a, 28b) were checked with
their full Make suites on Windows and Linux, plus focused reruns after updating
test expectations. The final Windows coverage is 4,143 tests plus eight fuzzer
regression runs, with the shared printer and ISA tests also passing. The
native library/client cases and final Chapter 28b release build pass on both OSes.

A local linear-history dry run produced all 31 tags in teaching order. The
final source snapshot, chapter navigation, Maven module IDs, and reviewed local
documentation links were checked. Measurement capture in 24b and dynamic
measurements in 24b/28b pass; the historical published tables were not rewritten.
