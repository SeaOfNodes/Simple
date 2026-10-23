# Approved chapter split

Cliff approved this teaching outline on 2026-10-19. It splits the material
currently in Chapters 24 and 25 into seven chapters. Use descriptive chapter
names while the chapter count changes; final numbers can wait. Cliff approved
starting with both proposed implementation boundaries and wants to review the
construction/escape border after the snapshots run. Native library construction
and optimizer tuning belong in appendices for now.

The teaching pattern is to introduce a difficult idea, then show how to make
it useful in a larger setting. Each chapter must remain a complete, runnable
compiler snapshot that preserves the language supported by its predecessor.

## Teaching sequence

| Topic | Main idea | Endpoint |
|---|---|---|
| SCCP within a function | Optimistic types, reachable control, Phi inputs, worklists, fixed points, and loop widening. Keep calls conservative at their interfaces. | Fold loop-carried values and eliminate branches that pessimistic iteration cannot resolve. |
| SCCP across functions | Discover call edges during analysis; merge arguments and returns; account for unknown callers and function pointers. | Specialize parameters, resolve call targets, handle recursion, and remove unreachable functions. |
| SSA before types are known | Syntax and lexical binding choose graph shape. Keep scalar types, deferred numeric modes, conversions, guards, incomplete objects, symbolic fields, and removal of parser-supplied Load/Phi bounds together. | Compile forward definitions without provisional types determining graph topology; settle types and layouts afterward. |
| Construction and private memory | Allocation helpers, hidden initializers, user constructors, private self-memory, field-completion checks, and explicit publication. | Preserve initialization and memory facts through constructor calls and publish a completed object. |
| Escape analysis and unknown callers | Discover which aliases and functions can reach code outside the known graph. | Preserve exposed memory and callable functions while specializing private code. |
| Modules and lazy class initialization | Multiple source files, namespaces, privacy, ownership, singleton storage, first-use initialization, parent order, cycle diagnostics, and lowering. | Compile sources together with defined class-initialization behavior. |
| Separate compilation | Preserve types, ideal IR, and identities across independent compilations. | Build a library and client separately, optimize imported bodies, link remaining calls, and check initialization dependencies across imports. |

The first incomplete-type chapter stays together. Its unresolved memory accesses
must work before construction introduces private memory; defer the private
construction protocol, not the machinery required to represent an unknown access.

## SCCP boundary

The first SCCP snapshot keeps unknown-caller Start inputs and disconnects call
edges during the optimistic solve. Parameters retain their declared types;
call results retain facts from the preceding pessimistic pass, which can be
sharper than the written signature. Calls still have whole-memory effects.
After SCCP, peepholes relink calls and FinalizeIR removes uncalled bodies while
preserving named exports. This is an intermediate compiler design, not a
textual division of Opto.java.

Ordinary loop widening belongs in the first chapter. The second adds argument
and result widening for recursion. Use `f(x-1)+1` to explain why these are
separate data cycles: the recursive return value never crosses Parm(x).

Chained comparisons do not need their own chapter. Their provisional home is
beside the current Chapter 23 short-circuit expressions: evaluate each operand
once and evaluate later operands only when earlier comparisons succeed.
The current Chapter 24 cold-loop-split allocator improvement belongs with the
tuning appendix.

## Construction and escape boundary to establish

Separate an explicit memory-publication operation from the analysis that
discovers exposure to unknown code.

| Construction chapter | Escape-analysis chapter |
|---|---|
| Tracks private memory while an object is built. | Tracks aliases and functions exposed through memory, calls, and returns. |
| Publishes initialized memory at an explicit boundary. | Propagates exposure through the reachable graph. |
| Conservatively preserves unknown callers for surviving function addresses, following the current Chapter 24 policy. | Uses escape facts to determine which functions need unknown callers. |
| Explains EscapeNode as a publication operation. | Explains the Start/Stop feedback cycle between known code and the external world. |

Verify this split against constructor calls and memory summaries. The earlier
snapshot can publish conservatively; the later one refines that behavior.
The name EscapeNode does not require teaching the full escape analysis at
the point where publication is introduced.

`chapter-construction` now implements that explicit boundary. Hidden initializer
and allocator functions carry whole private object memory; completion publishes
fields into public aliases. The new constructor functions reject early passing
or storing of `self`, including self-links, and access through a merged alias.
These are deliberate limits for review with the next escape snapshot. Normal
function addresses retain conservative unknown callers. Initializers capture
enclosing values as hidden arguments; writing back to captures is rejected.
Arrays follow the same construction/publication protocol. There are no modules
or class singletons in this snapshot.

Review on 2026-10-22 removed an unnecessary representation detour. Constructor
fields now stay in local SSA values and ordinary Phis; helpers emit ordinary
Stores at exit, with precise aliases gathered in MemMerge. Private memory is
only the handoff between helpers and publication. No global escape discovery
is needed to build that representation.

Construction and escape discovery need not be taught in this order. Keep the
current order provisionally, without inventing a constructor example to claim
that dependency. The callback example now lives in the EA chapter.

## Modules before separate compilation

Teach module semantics with all source files compiled in one invocation.
Include lazy initialization, parent requirements, and cycle checking there.
Separate compilation then adds serialized ideal IR and types, GlobalBits
identity remapping, imported type reconstruction and analysis, and Unlink
before machine compilation. Extend initialization checks across imported
bodies, preserving opaque CLInit nodes until lowering.

Keep code ownership distinct from unknown-caller edges in both chapters.
Keeping a function for emission must not imply arbitrary callers can reach it.

## Appendices and future work

- Native library construction: numeric C data symbols, address handling,
  modular sys, runtime and driver support, and larger examples. Preserve the
  native functionality already taught in earlier chapters.
- Optimizer and backend tuning: existing cloning/inlining choices, spill
  ranking by live-range area and copy cost, rematerialization, and splitting
  policies. Compare equivalent programs when explaining costs.

This split organizes implemented behavior. Deterministic inlining, argument
arrays, static pre-initialization of classes, CFG frequency estimates, and
constant instance-field storage remain future work. See the
[backport queue](chapter-backports.md) and [Chapter 25 roadmap](../chapter25/ROADMAP.md).

## Renumbering and validation

The first runnable extractions are
[SCCP Within a Function](../chapter-sccp/README.md) and
[SCCP Across Functions](../chapter-interprocedural-sccp/README.md), followed by
[SSA Before Types Are Known](../chapter-incomplete-types/README.md),
[Construction and Private Memory](../chapter-construction/README.md) and
[Escape Analysis and Unknown Callers](../chapter-escape/README.md).
The other two snapshots remain to be extracted. Numbered Chapters 24 and 25
remain comparison baselines during this work.

Choose final numbers after trying the implementation boundaries above.
Revisit 17a/17b during that pass: they currently separate permissions from useful
syntax. Chapters 10/11 separate whole-memory SSA from useful alias partitioning.
Preserve those teaching boundaries when choosing names and numbers.

The same progression guides this split: local SCCP to interprocedural SCCP,
private construction to escape discovery, and modules compiled together to
modules compiled independently.

Validate each intermediate compiler with its own Make suite, including execution
checks. Keep declarations, conversions, diagnostics, and type bounds consistent;
do not weaken checks to make an intermediate snapshot pass. Rebuild serialized
libraries when their compiler or object format changes.

## Escape snapshot ready for boundary review

`chapter-escape` adds function/alias exposure sets to TypeMem and closes the
Start/Stop feedback cycle during SCCP. Known calls propagate memory summaries;
exposed returns, fields, external arguments and native calls discover further
addresses. Native calls contribute even on paths with no live Return. Function
addresses kept entirely in known code no longer imply unknown callers.

The [recursive callback example](../chapter-escape/README.md) now belongs in
that chapter. Its base parameter becomes 2 while recursive calls remain.
Returning the callback or a containing object, or handing it to native code,
restores conservative arguments. FinalizeIR separates those analysis facts
from the roots needed to emit code.

Construction still keeps local field values and rejects early exposure of self.
Both runnable snapshots are available for Cliff's promised review/play session.
This extraction does not choose broader rules for partially initialized objects.
No modules, class singletons, serialized IR or identity remapping are included.
