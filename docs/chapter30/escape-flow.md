# Outside-world escape flow

The `escape-flow` branch developed this design in Chapter29 and backported it
to Chapters 28a/28b. Chapter28a uses `LocalIds` and its existing initializer
root/lowering protocol; Chapter28b adds saved summaries and cofinite identity
remapping. Neither port imports Chapter29's deferred-construction model.

## The cycle

The outside world's access to local objects and functions is a value in the
Start/Stop cycle. Public declarations are not that value. The frozen public
interface inventory has been removed; public-method and public-alias tests
check the converged outside escape sets directly.

At Opto setup, the outer Start receives a constant foreign-identity seed:

```
foreign identities = FULL minus identities owned by this compilation
outside identities = foreign identities union discovered local escapes
```

Known native function and storage identities are foreign too. The infinite
tail permits future foreign functions even when no local callback has escaped.
The finite exclusions include registered imported identities, including bodies
represented by summaries. This scans identity tables, not the IR. Opto's
existing reset/worklist solve discovers the local escapes.

StartCU includes the shared outside state. Infinite FIDX sets must retain their
exclusions: `Opto.linkStart` checks each local function's membership rather than
treating every cofinite set as FULL. Serialization remaps a cofinite set by
remapping its finite exclusions and complementing again.

Before Opto, outside queries remain pessimistic. During SCCP, a consumer can
read the foreign seed before Start has run: otherwise a temporary finite set
can turn a generic foreign callback into an unrelated local function with the
same arity. No previous escape result is accumulated to repair value flow.

## Consumers and dependencies

* Memory and callback defaults for unknown callers use Start's memory
  projection. Parm applies its declared callback signature to the escaping
  FIDX set. Defaults whose values do not depend on escapes remain constants.
* Unknown/native CallEnd results use the outside state and the current call's
  exposed arguments. Callback results are bounded by their declared signature
  and the outside FIDX set. Explicit linked Return effects are retained.
* CallEnd registers a direct dependency on Start. Depending only on Call is
  insufficient: Call can stay CONTROL while the outside set changes. This
  first implementation uses `addDepForwards`; it does not change CallEnd's
  input layout or introduce a synthetic Fun/Return pair.
* Infinite calls use the outside continuation. Escaped local functions remain
  callable through Start, and their effects return through Stop. Attaching
  every same-arity body to an infinite call is incorrect: argument/result
  families may differ despite the equal arity.
* Outgoing Call argument exposure is independent of CallEnd, including a call
  that never returns. The shared fixed point also permits immediate callback
  round trips and reentrant calls.
* Opaque external values in Escape, Store, Return, and imported-summary paths
  use the current outside/input-memory bound. Explicit private values and
  already accumulated escapes remain included. Arbitrary local BOTTOM values
  still require FULL.
* Function-valued Loads use their memory input's FIDX bound, joined with the
  field declaration. They do not independently manufacture an escape set.

CLInit was another unconditional producer of FULL. During Opto it now depends
on the known initializer's Return and merges those effects with incoming
memory. It waits for live memory before publishing effects. Lowering computes
the replacement CallEnd's linked type before exposing its projections, so a
temporary unlinked MemBot cannot widen the already analyzed caller.

## Why the recursive tests needed local precision too

All four former Chapter12 printed-IR failures pass their original expectations.
The problem was not solely an outside-call default:

1. Convert widened a non-null pointer to a nullable declaration until its later
   identity rewrite. It now preserves non-nullness in compute. Preserving the
   entire source shape conditionally failed the existing monotonicity test;
   shape and access still follow the declaration.
2. A Load could wait until post-SCCP Store forwarding to recover that non-null
   pointer. In the meantime the next dereference produced BOTTOM, which fed
   FULL around the memory cycle and revived private constructors.
3. Forwarded addresses, resolved field aliases, and already-bypassed CLInit
   projections were also waiting for graph rewrites.

Load's existing forwarding search now supplies pointer value facts during
compute, without constructing or rewriting nodes. It can recognize a stored
address through a valid pointer conversion or a proven forwarded Load, use a
resolved named field before layout, and follow an existing CLInit bypass.
The same dependency mechanism reruns the search when its evidence changes.
Nullable/incomplete access checks still precede this sharpening; arbitrary
memory Phis and unknown stores remain barriers. This adds local queries, not a
new whole-program pass, analysis map, or second IR.

## Hidden class receivers

A class function's hidden `#self` defaults to its owning class singleton.
Seeding that input with TypePtr.PTR used to give FULL escapes when forwarded
to an unknown callback. For example:

```simple
val _hidden_noInline = { int n -> return n+2; };
val apply_noInline = { {int->int} f,int n -> return f(n); };
return apply_noInline;
```

`FunNode.defaultArg` now supplies the Test class singleton for this receiver,
both during parsing and when Opto reconnects unknown callers. The callback
parameter still reads Start dynamically. The printed-IR regression verifies
that only `<clinit>` and `apply_noInline` escape; `_hidden_noInline` stays private.

The function signature retains TypePtr.PTR in this slot. The backends use it
to identify the hidden ABI argument, and callback annotations must not bind
the receiver to the class where the annotation appears. Explicit calls still
supply their actual receiver; instance methods keep their declared object
type. This sharpens the default input, not every abstract pointer: a local
merge of a private object and private callback can also have an abstract
pointer type, so capping arbitrary TypePtr values at outside escapes is unsound.

## Remaining design question

Late imported identities also deserve further precision work: identities not
registered when Opto starts lie in the foreign tail. Existing import tests
exercise correctness, but this can remain conservative when bodies arrive
later. This limitation is not repaired by resetting the whole IR.

## Validation

The combined `make -k tests CHAPTERS="chapter28a chapter28b chapter29" CTAGS=`
run passes with assertions: 538 active tests in Chapter28a, 578 in Chapter28b,
and 601 in Chapter29. Shared printer and ISA checks pass too. The log is saved
under ignored `build/escape-backport-final.log`.

`EscapeFlowTest` covers opaque wrappers with three worklist seeds, foreign
callback parameters, private callbacks passed to and returned from native code,
and a CallEnd dependency that responds when only Start's escape set changes.
It also checks that public allocators escape while their private-memory helpers
do not. Class-init tests cover unknown callback cycles; saved-library tests in
28b/29 verify private-function exclusions survive identity remapping.
The existing suites cover nonreturning native calls, class-init cycles,
constructor rejection, serialization, imported callbacks, and three backends.
The four recursive-class examples also pass a separate 16-case worklist-seed
check. Backport IR expectations now retain public allocator Returns instead of
private-memory helper Returns. The String allocation/encoding/export fixtures
in 28a/28b/29 now declare `_hashCodeString` before the public `hashCode` wrapper,
so the implicit return exposes the wrapper. Their measured spill expectations
are 12/8/6 (x86/RISC-V/ARM); native x86 export expects 12. The reordered fixture
also measures 8 on RISC-V before the backport, versus the old order's 6.
