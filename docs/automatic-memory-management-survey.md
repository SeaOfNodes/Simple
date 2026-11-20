# Automatic memory management: design survey

Research notes for Chapter 33, begun 2026-11-16. The base design below records
agreed decisions. The survey and discussion that follow retain alternatives
and earlier reasoning, including positions subsequently revised. None of the
new runtime memory-management behavior is implemented yet. Chapter 33 now has
an analysis-only allocation pass and small poster-child tests; see its
[README](../chapter33/README.md#first-allocation-plans) for the supported subset.

## Agreed base design — 2026-11-17

Placement and array-growth decisions updated 2026-11-18. Embedded objects,
references, and array layouts reviewed and accepted 2026-11-19; see the
[detailed design below](#agreed-design-embedded-objects-values-and-references).

- Reject programs whose lifetime safety the compiler cannot establish. Common
  cases should need little annotation; diagnostics must identify the lifetime
  conflict and offer a sensible restructuring or longer-lifetime choice.
- The compiler manages ordinary stack-like lifetimes. `region`, declared only
  at file top level, names an additional growable storage area with program
  lifetime and no language-level size bound. Labeled scopes name lexical
  allocation destinations, not separate growable areas. `new T(args)` infers
  placement; `new(destination) T(args)` selects a region or lexical destination.
  Destinations may pass through functions; the compiler must establish the
  lexical relationship from definition to use. Transferred or independently
  object-owned region lifetimes are not selected.
- Free lists populated by `delete` are compiler-managed and never user-named.
  They are distinct from allocation regions; reuse must preserve both type
  compatibility and the requested storage lifetime.
- Reclaiming lexical storage
  requires proof that no usable references into it survive. This includes stale
  references to deleted objects and references recoverable from retained fields
  or permitted historical pointer values. Deleting an object does not discharge
  any reference's lifetime obligation. Dead pointer bits need not be erased;
  they must be unusable after reclamation. If safety cannot be proved, reject
  reclamation or the enclosing lifetime arrangement. Earlier discussion of
  named lexical pools and explicit pool deletion predates the region/scope
  distinction; no explicit region-delete syntax has been selected.
- Infer safe reclamation boundaries through factory layers, while letting the
  programmer explicitly choose better groups of objects that die together.
  Returned allocations may be assigned to caller-owned storage; retaining an
  allocation in another object can require its storage to live at least as
  long as that retaining object's region. Extending a borrowed object's lifetime
  is not implicitly authorized by these allocation-destination choices.
- Apparent unbounded growth in an inferred region is a compile-time error,
  never a warning. A cheap explicit annotation chooses either the default
  stack destination or an appropriate pool for that growth. Choosing the default
  stack explicitly acknowledges retention there; it does not promise bounded
  space or cause earlier reclamation. Choosing a pool likewise still requires
  lifetime safety. For this growth check, repetition is significant when a loop
  has an unknown trip bound, a known upper bound greater than 10, or involves
  recursion. Loops bounded by at most 10 iterations are exempt from requiring
  a growth annotation on that account. The check concerns accumulating storage,
  not a blanket ban on allocation in large loops. This is a language check,
  not an optional profiling report or a check whose result depends on optimizer
  success. Syntax and details of the bound analysis remain to be specified.
- Support the common make-and-test loop: reclaim rejected candidates each
  iteration and retain the winner on loop exit, where analysis establishes
  that rejected candidates do not escape. The winning exit alone must not force
  every candidate to accumulate in an enclosing region. This applies to
  automatic reclamation and does not require explicit delete. The compiler
  mechanism for retaining the winning allocation graph remains to be chosen.
- Classes explicitly opt into being deletable; the spelling is undecided.
  `delete` permits storage recycling rather than immediately invalidating the
  storage. Automatic reclamation still governs the enclosing region's lifetime.
- Arrays are never user-deletable. Their storage follows automatic lifetimes.
  A container's obsolete backing storage may be internally recycled only when
  the compiler proves no usable references into it survive. This is distinct
  from class-slot recycling with permitted stale references after `delete`.
- `new E[N]` creates a fixed-length array, with N possibly computed at runtime.
  `new E[]` starts empty and permits append with `es += elem`. Access and
  indexed assignment are checked against the current length and do not extend
  it. The creating scope's end finishes growth through all aliases, while the
  storage can outlive that scope under allocation-destination inference.
  Braces can explicitly delimit growth; an earlier end can be inferred.
  Finishing fixes length, not element mutability. Physical placement can reserve
  fixed allocations ahead of a growing tail without moving their construction
  effects. Standard growable containers use embedded small vectors; arraylets
  and dedicated address-space reservations remain optional alternatives.
- Reads after deletion remain memory- and type-safe, but may observe permitted
  historical values. Reuse may change any field, including final fields.
  Mixed incarnations are possible; stale references do not require a particular
  free-list policy or force an otherwise effect-free unused allocation to run.
  Comparing an old reference with a replacement allocation may return equal.
- Ordinary construction rules still apply to new objects: uninitialized fields
  cannot be read, including indirectly through other objects. Recycled storage
  must remain safely readable: free-list metadata is separate from readable
  fields, and deletion/recycling must not clear fields to invalid values.
- Disallow storing incomplete references into deletable objects. This applies
  during their construction too, whether allocation returns fresh or recycled
  storage. In particular, an incomplete constructor `self` cannot be stored in
  a deletable object. A constructor may store a completed child; nested
  allocation itself is not forbidden. More precise proofs may relax this rule
  later, but are not required by the base design.
- Disallow deletion through an incomplete reference, including `delete(self)`
  while the constructor's receiver is incomplete. This avoids directly making
  unfinished storage available for recycling.
- Deleting an occupied slot makes it available; deleting an already available
  slot does nothing. Deleting through an old alias after reuse deletes the
  current occupant, whose references then acquire post-delete guarantees.
  No incarnation check is required to diagnose the programmer's mistake.
- Deletion of a slot currently under construction does nothing, including
  deletion through an old completed alias. Construction tracking need only
  exist during construction: use a short stack of pointers to active
  constructions, checked by delete. The
  availability bookkeeping must prevent duplicate free-list entries.
- Writes through stale references are ordinary checked Stores to the referenced
  slot and may modify its current occupant. A valid reference to that occupant
  must observe those writes under ordinary memory semantics. Historical-read
  permissions do not justify ignoring a potentially aliasing stale Store.
  Final-field assignment restrictions, read-only access, region-lifetime checks,
  and the prohibition on storing incomplete references remain in force.
- Constructors for deletable classes use potentially already escaped storage,
  with ordinary aliasable field memory from construction entry. Constructors
  for non-deletable classes retain the existing private-construction model.
  Incompleteness and private storage are separate properties: deletable `self`
  remains incomplete until this construction satisfies its initialization
  obligations, even though old aliases can access the storage.

### Why deletable objects cannot retain incomplete references

A recycled object initially contains valid old fields. That fact does not
justify overwriting one of them with a reference into an unfinished, genuinely
fresh object. A stale alias to the recycled object would expose that unfinished
object as if it were complete.

Schematic counterexample:

```text
// oldLink is complete; Link is deletable.
delete(oldLink)

// Root receives genuinely fresh storage.
root = new Root {
    link = new Link(self)  // reuses oldLink's slot
    helper()              // reads oldLink.parent.required
    required = some_prior_thing
}

// Link's constructor:
new Link(parent) {
    self.parent = parent  // REJECT: parent is an incomplete Root
}
```

The Store makes a fresh, uninitialized `Root.required` reachable through the
completed view `oldLink`. Historical-value permissions do not authorize reading
an invalid representation. Rejecting that Store preserves the safety promised
to stale references without requiring runtime incarnation tracking.

The earlier example with a back-reference into an already valid recycled parent
can be physically safe. The base rule deliberately rejects it if that reference
is still classified as incomplete. Proving continuous validity of old contents
separately from completion of the new incarnation is a possible later refinement.

### Why deletable constructors use aliasable memory

An old completed reference can write a mutable field of the recycled object
while its new constructor runs. No incomplete reference needs to escape:

```text
p = new Thing { x = 1; }  // mutable x; Thing is deletable
delete(p)
q = new Thing {
    x = 2;
    overwriteOld();      // writes p.x = 3
    print(self.x);
}
```

If q reuses p's slot, the read through self must observe 3. Updating public
memory in overwriteOld while forwarding 2 from unchanged private constructor
memory would be incorrect. Constructor Stores and calls therefore participate
in the ordinary field-memory chains from the start, including after inlining
and through separately compiled effect summaries. Calls proven not to affect
the field can still permit forwarding. Peepholes must not infer disjointness
merely because pointers come from distinct recycling allocations.

Old field contents do not satisfy the new incarnation's definite-initialization
requirements. Keep construction-completeness tracking separate from memory
privacy. Cliff suggested that withholding TMP._one might select the needed
aliasing behavior; this is an implementation candidate to audit, not a claim
that changing one flag is sufficient across TypeMem, constructor summaries,
publication, and initialization checks.

We choose this aliasable-memory model rather than mandatory retention of all
constructor field values followed by bulk publication, or a blanket prohibition
on writing other same-class objects during construction. Linked-list splicing
after construction remains ordinary mutation. Publishing incomplete self into
a deletable neighbor remains rejected by the separate incomplete-reference
rule. Any later relaxation requires an additional completion proof.

For final fields after publication, ordinary memory-chain reasoning can forward
the new constructor's value to an old alias when equality is established; that
is a permitted stale-read result. The earlier example of equal pointers with
historical final-field values was not a new semantic obstacle. Implementation
still needs an audit of freshness assumptions and memory-dependent value facts.

### Function lifetime contracts — 2026-11-18

Contracts describe lifetime relationships between arguments, results, and
retained storage. Ordinary function definitions infer these relationships;
separately compiled metadata exposes them to callers.

- Borrowing an argument for the duration of a call does not retain it afterward.
  Mutation is independent: sum and in-place sort can have the same lifetime
  contract while having different memory effects.
- A returned reference into an argument's reachable storage does not extend
  that storage's lifetime. If the result can come from either of two arguments,
  the caller must account for both possibilities unless it can establish which
  applies. The arguments need not belong to the same pool.
- Fresh results can be constructed in a caller-selected destination, with that
  relationship propagated through factory wrappers. References retained inside
  the new result must satisfy its destination's lifetime constraints.
- Retaining an argument through another argument, a global, or a result is an
  explicit part of the contract. A void result does not imply non-retention.
- Each call must satisfy the contract, and each implementation must satisfy
  the promises its callers use. An indirect-call interface has a common contract
  that every permissible target must satisfy.
- At an opaque callback interface, unspecified argument retention defaults to
  borrowing only for the call. Retention requires an explicit contract there;
  ordinary visible definitions can infer it. Implementations are checked against
  this default, rather than callers blindly assuming it. Result borrowing and
  fresh-result destinations still need their own specified or inferred rules.

Functions may be specialized according to how arguments are treated. Cliff
explicitly identifies this as an optimization, not a language design choice.
Specialization must preserve the contract and observable behavior; program
legality must not depend on whether specialization or inlining occurs. Exact
syntax for retention and growth contracts, analysis representation, and metadata
encoding remain open. Returned-borrow syntax is settled below.

Native functions use the same lifetime contracts as other external functions.
Their FFI wrapper type, written in Simple, states the borrowing, retention,
result-lifetime, and allocation-destination promises on which Simple callers
rely. There is no separate native lifetime mechanism. For a Simple implementation
the compiler can check those promises against its body; for native code the
FFI declaration is trusted. A mismatch is a bug in the binding contract or its
implementation, not a reason to weaken the ordinary call-site lifetime rules.

#### Returned-borrow syntax and FFI boundary — agreed 2026-11-19

`from` introduces a result's borrowed-storage origin in a function type.
Only arguments named in the `from` clause require parameter names; all other
arguments may remain unnamed. For example:

```text
{ (*Complex)[] as, (*Complex)[] -> Complex from as }
{ (*Complex)[] as, (*Complex)[] bs -> Complex from (as | bs) }
```

The first signature permits returning an interior reference such as `&as[i]`.
The second permits either argument as the source; their lifetimes need not
be equal. The caller accounts for all possible sources unless it can establish
which source applies. Names in `from` must resolve to named parameters of that
signature; no separate declaration of lifetime variables is required.

For a Complex containing only numeric coordinates, returning `*Complex`
copies the value and requires no borrowed-result relationship:

```text
{ (*Complex)[], (*Complex)[] -> *Complex }
```

Copies of object values containing reference fields still preserve those
references' lifetime obligations. A written contract is checked against the
body, not trusted, and does not silently extend allocations to make it fit.
Visible bodies infer relationships when omitted; separately compiled metadata
preserves inferred contracts. This syntax is agreed but not implemented.

For now, FFI functions may not directly return borrowed interior references
into Simple objects. Array-oriented native adapters can return an index or
range; Simple performs checked access and forms the reference, making its
origin visible to the compiler. Bindings returning interior field references
are deferred until a use case warrants revisiting the restriction. This limits
the available FFI contracts without introducing a separate lifetime system.

### Still open

The delete/recycling policy is largely settled above. Remaining language-design
questions concern the automatic lifetime system:

Cliff wants to settle these language choices before designing the analysis.
"How are we tracking this?" is explicitly deferred: representation of lifetime
constraints, path-sensitive escape information, loop-exit winners, recursive
summaries, and reclamation checkpoints are not selected implementation designs.
First settle the programmer-visible ownership and lifetime rules below.

- Allocation-destination analysis: implement the agreed inferred placement,
  named lexical scopes, top-level regions, and destination forwarding through
  factories. The source-level choice is settled; tracking it remains deferred.
- Apply the agreed growth threshold (unknown loop bound, bound greater than 10,
  or recursion) to repeated allocation that accumulates in an inferred owner,
  distinguishing temporary storage reclaimed each iteration or activation.
  Specify stable bound analysis and how loop-exit winners retain storage without
  retaining failed candidates. Apply the selected destination syntax through
  factories without annotating every helper.
  Errors should identify the accumulating allocation, retaining owner, and
  repetition, then show the explicit destination choices.
- References across regions: allowed stores, returns, and retained views;
  proving that reclamation leaves no accessible dangling storage, including
  historical pointer values permitted after delete. The safety obligation is
  settled; its enforcement remains part of the deferred analysis design.
- Remaining function-contract syntax and enforcement: returned-borrow `from`
  syntax is settled above; retention, destination, and growth interface
  spellings remain. Implement the agreed relationships
  and borrowing default across direct calls, separate compilation, and indirect
  calls, including the written FFI types for native functions. Define useful
  diagnostics and rewrite paths when a proof fails.
- Array-layout details: spell selectable length widths and construction/append
  for the combined struct-and-tail type. Fixed-buffer notation, embedded object
  access, and scope-based finishing are settled below. Any shrinking or
  reconstruction operations need explicit reference-validity rules; they are
  not implied by append or whole-object assignment.

Implementation obligations, rather than new permission to change semantics:

- Audit optimizer freshness assumptions, historical reads, pointer equality,
  and initialization tracking under aliasable construction.
- Implement real array bounds checks, eliminating them only with adequate
  proofs. Cliff notes the current unchecked accesses; memory safety is an
  intended guarantee, not a claim about the current compiler.
- Specify concrete syntax and runtime metadata for the agreed lifetime and
  recycling rules after the remaining lifetime choices are settled.

### Node-construction check — 2026-11-17

Cliff asked whether Simple's IR Nodes actually have recursive constructors,
rather than merely nested constructor expressions in argument lists. Inspection
of Chapter 33's Java sources found:

- The ideal-IR constructors inspected do not construct other Nodes. The base
  [Node constructor](../chapter33/src/main/java/com/seaofnodes/simple/node/Node.java)
  creates its edge arrays and installs use edges. Constructor worklist additions
  enqueue work rather than running peepholes recursively.
- Some machine constructors do construct other concrete Node classes. For example,
  [NegFX86](../chapter33/src/main/java/com/seaofnodes/simple/node/cpus/x86_64_v2/NegFX86.java)
  creates a ConstantNode and FltX86 inside its constructor body. ExtendX86,
  ExtendARM, and ExtendRISC create projection/parameter nodes in delegating
  constructor arguments. No path back into the same concrete Node constructor
  was found in this inspection; this is not a whole-program proof.
- `new AddNode(new MulNode(...), ...)` finishes constructing its argument before
  entering AddNode's constructor. Reserving/protecting the outer pool slot after
  argument evaluation avoids making that expression overlap pool construction.
- Cyclic graph topology is distinct from recursive construction. Completed
  Nodes can be wired into cycles afterward; deleting cyclic graph nodes need
  not be forbidden merely to simplify construction tracking.
- A Simple port has a separate publication issue: Node's base constructor calls
  `n.addUse(this)` on its inputs before the concrete subclass finishes.
  CallNode also registers itself in the global call list. If these containers
  are deletable, they cannot retain the incomplete receiver under the base
  rule. Moving reverse-edge wiring and registration after full construction
  provides a straightforward rewrite, without banning graph cycles.

A pool-wide busy bit cannot identify which slot is under construction. Ignoring
all deletions in that pool while busy would be a broader semantic policy than
the agreed per-slot rule. With at most one active construction per class-pool,
a nullable current-slot pointer suffices to identify that object. Reentrant
construction can instead use temporary constructor-frame records; delete can
search those records, with cost proportional to active construction depth.
Cliff accepted a short stack of construction pointers as the base approach;
the single-pointer case remains a possible optimization.

## Original survey

There are several approaches worth inspecting. For Simple, I would give the closest attention to inferred regions, Lobster-style ownership inference with an RC fallback, and ownership of whole object graphs. Goose is particularly useful because it shows how much cheaper memory management can become when the language constrains storage layout and lifetimes together.

I’d first adjust the three-way comparison slightly:

- GC’s implementation difficulty varies enormously. A basic collector is manageable; combining low latency, low memory overhead, concurrency, and high throughput is the difficult part. Conservative collectors such as [Boehm GC](https://www.hboehm.info/gc/) also offer a relatively small integration path, with different precision and reclamation tradeoffs.
- Reference counting need not charge for every pointer assignment. Borrow inference, moves, and compiler optimization can remove considerable traffic. However, reclaiming the last reference to a large graph can still cause a long burst of work.
- Static ownership removes runtime lifetime tracking for proven cases. It does not guarantee small, fixed allocation or destruction costs. Also, annotation burden and ownership restrictions are separate problems: Rust already elides many lifetime annotations, but the restrictions on sharing remain. [Rust lifetime elision](https://doc.rust-lang.org/reference/lifetime-elision.html), [Rust destruction](https://doc.rust-lang.org/std/ops/trait.Drop.html).

The useful question is: where does each approach put the cost—in runtime work, retained memory, compiler analysis, or restrictions on programs?

| Approach | Where it wins | What pays for that win |
|---|---|---|
| Inferred regions/arenas | Cheap allocation and bulk reclamation; potentially no lifetime syntax | Objects may remain allocated until their whole region dies; inference can choose overly long lifetimes |
| Ownership inference + RC fallback | Ordinary reference-oriented code; proven borrows avoid counting | Counts remain for sharing; cycles need another mechanism |
| Precise RC + reuse | Reclaims dead values promptly and reuses uniquely owned storage | Strong analysis; mutable aliasing and cycles complicate the model |
| Ownership of whole regions | Arbitrary graphs can live inside one owned unit | References crossing region boundaries require rules |
| Mutable value semantics | Local reasoning and straightforward ownership; efficient in-place updates | Shared mutable identity becomes a special case |
| Ownership + checked non-owning references | Flexible back-references without counting every alias | Runtime validity checks; a stale reference can fail instead of keeping its object alive |
| Isolated heaps with local GC | Limits the scope of collection and synchronization | Communication and sharing must respect isolation |
| RC/tracing hybrids | Preserve flexible object graphs while combining reclamation techniques | More runtime machinery; costs become workload-dependent |

These are overlapping ingredients, not mutually exclusive language designs.

Goose takes the region idea unusually far. Its dynamic storage lives in compiler-assigned data stacks. Allocation bumps a pointer; leaving an owning scope resets it. Variable-sized contents nest inline, so reclaiming an array of strings need not visit and free every string. Growable arrays reserve address space and remain stationary, allowing references to survive growth. These are substantial layout advantages as well as lifetime-management advantages. [Goose tutorial](https://github.com/aardappel/goose/blob/master/docs/tutorial.md#4-where-values-live).

For non-nested lifetimes, Goose supplies pools whose slots can be explicitly reused. Returning a slot does not end its storage lifetime: an old reference can subsequently observe the replacement object. That preserves memory/type safety, but the programmer still manages logical object identity. This matters for caches, graph nodes, and game entities. [Goose pool model](https://github.com/aardappel/goose/blob/master/docs/tutorial.md#11-lifetimes-that-do-not-nest).

The restrictions deserve equal attention. Goose uses whole-program compilation and does not support general runtime function pointers. Recursive functions cannot retain their own variable-sized locals across recursive calls; longer-lived storage must come from outside that recursion. Those restrictions make its static storage assignment tractable. [Compilation model](https://github.com/aardappel/goose/blob/master/docs/goose_spec.md#101-whole-program-call-graph-order), [recursion rules](https://github.com/aardappel/goose/blob/master/docs/reference.md#19-recursion).

My assessment: adopting Goose’s complete model would substantially change Simple. Borrowing its ideas about region ownership, destination construction, and inline storage is much more plausible.

There are seven other directions I would keep on the reading list:

1. Inferred regions: MLKit, with Spegion as a newer research direction.

   The compiler chooses allocation regions and inserts their creation and reclamation. The programmer can retain ordinary source syntax. MLKit is an established implementation and combines inferred regions with tracing collection. [MLKit](https://elsman.com/mlkit/).

   The central weakness is retention: one surviving object can keep a region containing substantial dead data alive. A safe region assignment is not necessarily a space-efficient assignment. This is especially relevant to long-running programs with caches and repeated updates.

   Spegion explores implicit, non-lexical regions, extending beyond strictly stack-shaped lifetimes. It deserves a paper read, though I would treat it as research input rather than a demonstrated production recipe. [Spegion paper](https://arxiv.org/abs/2506.02182).

2. Inferred borrowing with RC fallback: Lobster.

   This is probably the closest match to “ordinary programming, with the compiler doing more work.” Lobster picks owners and infers borrows. When another use needs ownership, it can insert a reference-count increment instead of forcing the programmer to restructure the program. [Lobster memory management](https://aardappel.github.io/lobster/memory_management.html).

   The fallback is important: analysis precision can affect performance without making every uncertain case a compile error. However, Lobster still has some borrowing restrictions, and its reference cycles are not reclaimed. We would need an explicit answer for Simple’s cyclic structures. [Lobster specification](https://aardappel.github.io/lobster/language_spec.html#values-ownership-and-borrowing).

   My assessment: this is the strongest candidate for preserving Simple’s existing feel.

3. Precise reference counting and storage reuse: Perceus/Koka and Lean.

   Perceus places count operations using explicit control flow, eliminates redundant operations, and enables reuse of storage when a value is uniquely owned. Its “functional but in-place” model makes apparently functional updates execute as mutation when sharing permits. The paper’s garbage-free result is explicitly for cycle-free programs. [Perceus paper](https://doi.org/10.1145/3453483.3454032).

   For Simple, the useful lesson is the compiler machinery, rather than adopting Koka’s programming model wholesale. Existing mutable aliases mean we cannot freely substitute functional updates and copies without changing observable behavior.

4. Own a region containing a graph: Verona/Reggio.

   Instead of requiring every object to fit a unique ownership tree, own a region that can contain an interconnected graph. Enforce rules at region boundaries, and manage memory within each region separately. Verona explores isolated regions, reference capabilities, and different local memory-management strategies. [Reggio paper](https://arxiv.org/abs/2309.02983).

   My assessment: this is particularly attractive for compilers, scenes, documents, and requests. A compilation owns its graph; graph edges need not each participate in ownership bookkeeping.

   It does not automatically solve deletion inside a long-lived region. That still needs recycling, individual reclamation, or local collection. But it moves the ownership problem to a coarser and often more natural boundary.

5. Mutable value semantics: Hylo.

   Hylo emphasizes values and controlled mutable access rather than unrestricted shared mutable references. The attraction is simpler local reasoning without exposing a Rust-style lifetime vocabulary everywhere. [Hylo introduction](https://hylo-lang.org/introduction/), [mutable value semantics paper](https://arxiv.org/abs/2106.12678).

   This is highly relevant to Simple’s proposed inline structs. It is a larger semantic change for existing reference-based objects: assigning something that behaves like an independent value differs from sharing one mutable object. I would inspect this as a complementary feature, rather than assume it can replace all existing references.

6. Ownership with generational references: Vale.

   A non-owning reference carries an object generation. When the compiler cannot prove validity, dereferencing checks that the object still has the expected generation. Ownership controls reclamation; checks protect observers and back-references. Static analysis and regions can eliminate some checks. [Vale’s design](https://verdagon.dev/blog/generational-references).

   This permits patterns that static borrow checking would reject, but changes the guarantee: retaining an observer does not necessarily retain the observed object. A later access can fail. For game entities and removable graph nodes, that may be exactly the desired behavior; for ordinary owning references, it may surprise users.

7. Local or hybrid collection: Erlang, Nim ORC, LXR.

   Erlang’s per-process heaps localize tracing collection, illustrating that GC need not operate over one globally shared heap. The price is isolation and communication rules. [Erlang collector](https://www.erlang.org/doc/apps/erts/GarbageCollection.html).

   Nim combines optimized reference counting with cycle collection. LXR combines coalesced reference counting, concurrent tracing for cycles, and selective copying. These demonstrate that RC and tracing can be complementary. They also retain real runtime complexity. [Nim memory management](https://nim-lang.org/docs/mm.html), [LXR paper](https://arxiv.org/abs/2210.17175).

For Simple, my preliminary preference is to investigate two designs side by side:

- Preserve ordinary references, infer borrowing and ownership where possible, and use a runtime fallback where proof fails.
- Infer or expose ownership of larger regions, allowing rich object graphs inside them.

Those could eventually combine, but combining mechanisms before understanding either would make the chapter harder to reason about.

Simple’s existing escape analysis is useful groundwork. It does not yet answer the stronger question “can this allocation, or this group of allocations, be reclaimed here?” Separate compilation also means any required lifetime facts must survive through summaries and interfaces.

I would compare candidates using a few deliberately troublesome programs: a returned tree, a doubly linked graph, a cache that evicts entries while clients hold references, a resizable buffer with outstanding views, and a native callback that retains an object. Those examples will expose the programming costs much more clearly than allocation benchmarks.

The first choice I’d want to discuss is what happens when the compiler cannot prove a lifetime: reject the program, keep the data longer, insert counting/collection, or allow a checked reference that might expire. That fallback will largely determine how Simple feels to use.

---

## Follow-up: pool lifetimes and recycling

Yes. I’d describe `delete` as making an object’s storage eligible for recycling. The reference remains valid, but a later allocation can replace the value it denotes—including its final fields. Automatic reclamation still determines when the enclosing storage disappears.

My first design would separate the lifetime of storage from how we reuse slots inside it.

1. A region owns storage; class pools organize that storage.

   Each region would contain allocation chunks and, where recycling is enabled, per-class free lists. A `delete` returns a slot to its class’s free list within that region.

   That distinction matters: “pool of `Node`” should not imply one program-wide pool. Two independent compilations should have independent `Node` pools, so finishing one compilation can reclaim all its storage.

   Destroying a region releases its chunks. It need not walk the object graph, and cycles within the region require no special treatment.

2. Ordinary scopes provide automatic regions.

   A function or block can own temporary storage. The compiler need not create a physical arena for every block: it could use stack slots, a saved bump-pointer position, or combine regions when doing so preserves the required lifetimes.

   A loop’s temporary region would normally end each iteration. Objects intended to survive iterations must belong to something outside the loop.

   I’d make the logical lifetime rules stable, while leaving the physical allocation strategy open to optimization.

3. Explicit pools provide a destination with a longer lifetime.

   A programmer could create a pool in an enclosing scope and pass it to builders. The pool itself has an owner; the compiler checks that references into it cannot survive that owner.

   Schematically, without choosing syntax:

   ```text
   compile(source):
       own pool ir

       graph = parse(source, destination=ir)
       optimize(graph)
       emit(graph)

       // ir's storage is reclaimed here
   ```

   `parse` can have its own temporary storage while allocating lasting nodes into `ir`. References between those nodes are ordinary pointers.

   We can start with pools whose owners are lexical scopes. Transferring ownership of a pool, or storing one inside another object, would be an extension rather than a prerequisite.

4. Functions carry inferred lifetime relationships.

   We need to distinguish three common results:

   - A reference borrowed from an argument.
   - A new object constructed in the caller’s destination.
   - A reference into longer-lived storage, such as a class-owned pool.

   For example, a tree factory should normally construct into the caller’s destination. We should not force every factory to spell a pool argument, nor allocate locally and then attempt to rescue an escaping graph.

   Where the destination is ambiguous, require an explicit choice. The resulting relationships become part of the separately compiled function’s summary. Indirect calls need an agreed lifetime contract too.

5. Check references at the points where they can escape.

   A useful conservative rule is: a reference stored in an object must point into storage that lasts at least as long as the containing object.

   That permits:

   - References within one region, including cycles.
   - Temporary objects referring to longer-lived objects.
   - Calls borrowing references for their duration.

   It rejects storing a temporary object into a longer-lived graph. The repair is to construct that object in the graph’s pool, shorten the holder’s lifetime, or explicitly copy the required data.

   This rule will reject some programs that clear the field before the temporary dies. I would initially accept that limitation rather than make safety depend on proving every later overwrite.

The compiler would track region identities separately from alias identities. An alias describes which memory a load or store may affect; a region describes how long that storage remains valid. We need both, but they answer different questions. Region information could disappear from ordinary references after checking, leaving normal machine pointers. Allocation and recycling would still need access to the appropriate region.

Your recycling semantics add one important optimizer rule: a potentially recycling allocation can change the fields of an existing reference. Thus `final` means fixed within an incarnation, and cannot justify folding a load across such an allocation without further proof.

I also agree with atomic replacement as the language rule. Single-threaded execution makes that achievable, but constructors can call back into code holding an old reference. We must arrange that construction exposes either the old valid value or the completed replacement, never the unfinished replacement.

The central choice in this proposal is explicit region ownership with mostly inferred reference lifetimes. The programmer chooses the lifetime of a graph; the compiler handles temporary storage and checks the boundaries. That seems like a small enough model to explain, while covering much more than individually stack-shaped objects.

## Follow-up: final fields, stale reads, and reconstruction

Discussion appended on 2026-11-17. The exchanges below preserve the progression
of the design: Cliff's historical-value rule revises the stronger coherence
assumptions in the first response. Examples are design sketches, not a claim
that recycling is implemented. The final exchange lists unresolved type-safety
issues.

### Cliff: constructor Stores and final-field optimization

I want to drill into the opts allowed on a final-field with a delete-and-reused
value. The change has to come by a Store in the constructor, so any
optimization restriction should be limited to the constructor (including
inlining it), which can be done with an empty "memory barrier" kind of Node.

```java
val p = old;
delete(old);
new Thing =  { ->
    fin_fld = "change";
    print(p.fin_fld); /*read old fin_fld after writing*/
};
```.

If we deny the p.fin_fld read from bypassing the store, then essentially we've
declared "old==new" Thing at the allocation top. What other issues arise and
how do they pan out (because user-visible for recycling final fields)?

### Assistant: initial analysis under stronger coherence assumptions

Yes—the restriction can be anchored at recycling construction. We don’t need to make final fields behave as if arbitrary code could write them. But the constructor’s effects must also reach callers, and recycling changes allocation-freshness assumptions as well as load ordering.

For your example, if allocation selects `old`’s slot, then `self == p` from the start. The store to `self.fin_fld` must reach the subsequent load through `p`, so it prints `"change"`. If another slot is selected, `p` retains its previous contents. Thus the general compiler fact is “may alias”; it becomes equality only when we know which slot was selected.

I see five main consequences.

1. The barrier must connect constructor memory to existing aliases.

   Today Simple explicitly assumes separate allocations cannot alias in [LoadNode.neverAlias](../chapter33/src/main/java/com/seaofnodes/simple/node/LoadNode.java). It also treats construction memory as private until publication.

   Neither assumption holds for a recycled slot. A barrier that merely stops load motion at constructor entry would be insufficient if subsequent constructor Stores still update private memory invisible to `p`.

   Your node could establish the connection between the selected slot and public memory. From there, ordinary Store/Load dependencies should do most of the work. It needs no hardware fence in this single-threaded model.

2. Ordering must work in both directions, including across calls.

   ```text
   before = p.fin_fld
   new Thing() { fin_fld = "change"; }
   after = p.fin_fld
   ```

   `before` must not sink past the overwrite; `after` must not hoist before it. Inlining must preserve those dependencies. A separately compiled call that can recycle `Thing` must report the corresponding memory effects.

   The useful scope is the affected fields and, where distinguishable, the pool—not all memory.

   | Optimization | When it remains valid |
   |---|---|
   | Forward a constructor Store to a Load | Pointers and field match |
   | Combine repeated final-field Loads | No intervening operation can rewrite that slot |
   | Hoist a final-field Load out of a loop | The loop cannot recycle its object |
   | Fold a declared constant shared by every incarnation | Recycling preserves that constant |
   | Assume two allocations differ | They are proven fresh or otherwise disjoint |

3. Previously learned values must remain tied to memory.

   Consider:

   ```text
   if (p.fin_fld == 7) {
       makeAnotherThing();   // possibly recycles p's slot
       print(p.fin_fld);
   }
   ```

   The second load cannot inherit `7` merely because the pointer is unchanged and the field is final. A saved scalar containing the first load remains `7`; a new load might differ.

   This matters if field-value facts have been embedded in pointer types or used for branch elimination. An empty barrier cannot repair an already unconditional constant. The relevant facts must depend on the memory version, or be restricted to properties true of every incarnation.

4. Construction becomes observable through old references.

   This corrects my earlier suggestion of atomic whole-object replacement. Your example deliberately permits observation inside construction. Single-threaded execution gives ordered observations; it does not make the whole constructor indivisible.

   A callback can observe one field already replaced and another still holding its old value. That can be legal under the proposed semantics, but it prevents rearranging constructor Stores across those observations.

   Type safety is the harder boundary. We cannot first zero a recycled object containing non-null fields and then let an old, fully initialized reference read those zeros. Nor can we publish a reference to an incomplete child through a field that old aliases treat as complete.

   Preserving old values until valid replacements are available helps. Supporting all existing partial-construction patterns may require additional restrictions or delayed publication of particular fields.

5. An otherwise unused allocation can have observable effects.

   ```text
   delete(old);
   new Thing() { fin_fld = "change"; }  // result discarded
   print(p.fin_fld);
   ```

   That allocation and initialization cannot disappear merely because nobody uses the returned pointer. Likewise, allocation reordering can change which deleted slot gets reused. We should decide how much pool-selection behavior is specified before allowing such transformations.

I think your approach is sound as an implementation direction: model recycling construction as an aliasing memory update, then retain ordinary optimization between those updates. The two substantial additions are withdrawing the fresh/private-object assumptions and defining which partially reconstructed states old references may safely observe.

### Cliff: stale reads may observe historical values

In example 5, I think you CAN discard the unused Thing; the rules do not say "delete/alloc use a simple linked list of frees". Semantics are more annoying: if you delete something, then use it anyways, you get something someone wrote at any time in the past. This is a classic dangling pointer bug, and the fix is easy: stop deleteing things. Hand-n-hand with `delete` come dangling ptr bugs. Actually redo your analysis here with this in mind, how many of these issues become allowed behavior because dangling-ptr loads can see anything from the past?

### Assistant: revised analysis under the historical-value rule

Yes. With that rule, I was imposing too much coherence on references used after `delete`. You’re allowing those reads to become unreliable, while retaining type and memory safety.

I’ll interpret “anything from the past” as a value previously valid for that slot and field—not arbitrary bits or a value from an unrelated object.

That substantially changes the analysis:

| Earlier concern | Under the revised rule |
|---|---|
| Discard an unused allocation | Allowed, absent other observable constructor effects |
| Reorder which deleted slots get reused | Allowed; stale references cannot demand a particular replacement |
| Keep a cached final-field value across recycling | Allowed for the stale reference |
| Let a stale load bypass constructor Stores | Allowed; seeing the old value satisfies the rule |
| Observe a mixture of old and new fields | Allowed; cross-field invariants are no longer promised through stale references |
| Expose uninitialized or invalid representations | Still forbidden |

In your original example, `print(p.fin_fld)` may therefore print the old value even after the constructor stores `"change"`. We don’t need the barrier to force that stale read to observe the Store.

The distinction is now between the stale reference and the current object’s valid references:

```text
p = old
delete(old)
q = new Thing() { fin_fld = "change"; }

print(p.fin_fld)  // historical value permitted
print(q.fin_fld)  // must observe q's initialization
```

Even if `p == q` numerically, they have different guarantees. That is a specification distinction; it doesn’t necessarily require runtime tags.

Revisiting the five issues:

1. Private constructor memory can remain private for optimization purposes.

   Old stale readers need not observe the new Stores, so they don’t force us to publish every constructor update to public memory. However, reads through `self` and valid references derived from the new object must still see the appropriate writes.

2. Most of the proposed barriers disappear.

   A cached old value is an allowed stale-read result. Constructor effects need not invalidate that cached value merely because storage might be reused.

   Reads performed before deletion still have ordinary semantics. We cannot move such a read later and accidentally substitute a replacement value that was impossible at the original read. Likewise, “past values” does not automatically authorize observing future writes.

3. Historical constant facts can survive.

   If we know `p.fin_fld` was `7`, keeping that value after deletion is permitted. The optimizer needn’t model every possible replacement merely to preserve stale-read behavior.

   This is permission to choose an allowed result. It doesn’t mean two actual reloads are guaranteed to agree.

4. Partial reconstruction becomes mostly a type-safety issue.

   Mixed incarnations and broken object invariants become acceptable stale-reference behavior. But an old reference must not read an uninitialized non-null pointer, an invalid function pointer, or a pointer into reclaimed storage.

   In particular, a historical pointer value is safe to return only while its target storage remains valid. Keeping the owning region alive is essential.

5. You’re right about unused allocation elimination.

   Removing an otherwise effect-free allocation removes some possible replacement observations, but stale readers can still observe older values. That fits the proposed freedom. Printing, I/O, or other ordinary side effects in the constructor still cannot be removed.

Two questions remain significant.

First, what may a stale reference write? Allowing stale reads to return historical values doesn’t specify whether a stale write can modify the current incarnation—including its supposedly final fields. We should settle reads and writes separately.

Second, pointer equality: ordinary alias optimizations must respect this distinction. Proving equal addresses must not let the compiler substitute a stale cached value for a load through the valid new reference.

So yes: most of my coherence objections disappear. The remaining obligations are preserving valid-reference behavior, keeping every permitted historical value memory/type-safe, and defining stale writes. This is considerably more permissive than the model I was analyzing.

### Cliff: pick one issue

Ok, pick an issue and list problems with it

### Assistant: type safety during reconstruction

Let’s pick type safety while a deleted slot is being reconstructed. Historical-value semantics relax which valid value a stale read sees; they don’t make every intermediate machine representation valid.

Three concrete problems:

1. Free-list bookkeeping can overwrite readable fields.

   A conventional pool might store its next-free pointer inside the deleted slot. But an old reference can still read that location as an integer, object reference, or function pointer.

   An arbitrary free-list pointer is not necessarily a valid value of that field’s type. We would need separate metadata, reserved header space, or some other representation that leaves readable fields valid.

2. Clearing the slot can destroy non-null guarantees.

   Suppose `Thing` contains a non-null `Child` reference. The previous constructor installed a valid child.

   ```text
   delete(old)
   // allocator selects old's slot and zeroes it
   // constructor calls code that reads old.child.value
   ```

   The stale read encounters null, even though its type allows the compiler to omit a null check. “Any past value” doesn’t permit this: null was never a valid initialized value of that field.

   Keeping the old field until its replacement is ready avoids that particular problem.

3. A valid pointer can lead to an incomplete object.

   Suppose the replacement constructor stores a newly allocated, unfinished `Child` into the recycled slot, then calls code holding `old`.

   ```text
   self.child = unfinishedChild
   callback()  // reads old.child.requiredField
   ```

   `old` looks fully constructed to the compiler. Following it can bypass the restrictions we normally impose on reads through an incomplete constructor receiver. Even preserving non-null pointers would not suffice.

   We would need to delay that field Store until the child is sufficiently initialized, reject the construction pattern, or prevent the callback from observing it.

These problems share one requirement: every field observable through a stale reference must always contain a safely usable value of its declared type. Keeping old values during reconstruction helps considerably, but the third case is where Simple’s existing partial-construction rules need particular attention.

--------------------------------------------------------


(1) - Solved with e.g. ArrayList/Vector/seperate_metadata of free items
(2) - Delete does not wipe any fields.
(3) - Yeah, dunno how to solve that directly.  Could be we restrict classes that are "deletable" in some ways; no nested-unfinished allocations

Me trying to expand your example above...

```simple
new Foo = { ->
    child = new Child(self);
    helper(); // uses old.child.notnull and sees self.child.notnull
    notnull = some_prior_thing;
};
```

Things coming off the free list *already* are type-safe, so the above example,
`helper` expects at least the guarantees it gets from any `old` thing, which is
true for things from the free list.

So - counter-example me here, or declare this is "OK"

I think we should ascribe solid semantics for "Simple plus `delete`" language.
Without `delete` the rules are as-is.  With delete some things change:

- Object used after delete can have *any* field change value, to another legal
  value, including final fields.  No "undefined behavior", but yes "final
  can change" and "writing into my new private object rewrote this old object".

- Legal to ask `if( new==old )` and have it return `true`

- New objects have the old prior semantics: you cannot read an uninitiualized
  field; even indirectly through a chain of ptrs.  Fields always hold valid
  values, including not-null fields.  Same normal restrictions for a set of
  nested iner-locking allocations.

--------------------------------------------------------

## Follow-up: syntax choices — 2026-11-18

There are several choices, but most should appear only where inference needs
help. Here is the inventory, with illustrative spellings, not proposed final
grammar.

### 1. Allocation destination

Keep ordinary allocation unchanged:

```text
new Thing(args)            // inferred destination
new Thing(args) in pool    // explicit destination
new Thing(args) in stack   // explicitly accept default-stack retention
```

We need to decide whether destination selection attaches to one allocation,
a surrounding block, or both. A block default would avoid repeating
annotations, but must propagate sensibly through factory calls. Also, `stack`
must mean the appropriate owning frame, not necessarily the frame executing
`new`.

### 2. Pool declaration and lifetime

We need a way to declare a global or lexical pool and name it as a destination.
Separate choices include:

- Whether a pool serves one class or multiple types.
- How to request a dedicated growth space.
- Whether lexical exit is sufficient initially, or we also expose explicit
  early reclamation.

These can largely be library operations, although the compiler must understand
their lifetime effects.

### 3. Growing arrays and finishing

We need to distinguish an in-place growing array from an ordinary fixed-length
array. For example:

```text
val a = new Pixel[grow];
a.push(pixel);
finish(a);
```

The choices are:

- A type suffix versus a constructor or builder type.
- Whether append uses ordinary method syntax.
- Whether `finish(a)` is a statement, or returns a fixed-length view:
  `val b = finish(a)`.

I lean toward a finish operation usable without introducing another name. It
changes growth permission for the storage, including aliases; it does not make
elements read-only. Existing `~` access syntax should remain separate.

Appending inline objects also needs a way to construct the new element directly
in its slot, without requiring an intermediate object and copy.

### 4. Inline versus separately stored objects

This is the biggest layout syntax choice. For illustration:

```text
inline Complex position;   // object occupies this field's storage
Complex other;             // existing reference field
```

Should embedding be chosen per field/type use, declared as a default on
`Complex`, or both? Per-use choice seems necessary: the same type may be
embedded in one place and shared through a reference elsewhere.

Arrays need the same distinction:

```text
Complex[]                 // array of references, under existing conventions
(inline Complex)[]        // array containing Complex objects
```

Those parentheses are illustrative. Whatever spelling we choose must clearly
distinguish embedding the elements from embedding the array descriptor or a
fixed-capacity array.

### 5. Value access versus interior-reference access

For embedded objects, we need to distinguish:

```text
copy of a[i]
reference to a[i]
```

Whether those become `copy(a[i])`, `&a[i]`, an explicit reference type, or
contextual operations is undecided. We also need assignment rules: does
assigning an inline object copy its fields, and what happens to existing
interior references?

Container-and-index handles can probably be ordinary library types. We should
not make the same syntax silently alternate between a direct reference and a
handle.

### 6. Lifetime and growth contracts

Most relationships should be inferred, but external functions and explicit
interfaces need spellings for:

- Result borrows storage from an argument.
- Argument may be retained in another argument or pool.
- Fresh result uses the caller's destination.
- Function may grow an array, only access it, or finish it.

These belong in function types or associated declarations. The existing `^`
already denotes incomplete references; I would keep that distinct from lifetime
relationships.

### 7. Deletable classes

We need a class qualifier and an operation:

```text
deletable struct Thing { ... };
delete(p);
```

The exact spelling is small compared with the rules already settled. It should
be apparent from the type that instances permit recycling.

I would choose inline/reference/value syntax first. It determines field
declarations, array element types, construction, assignment, and function
signatures. Allocation destinations and finish syntax are comparatively
independent and can stay lightweight.

## Agreed design: embedded objects, values, and references

Proposed 2026-11-18; reviewed and accepted in full by Cliff on 2026-11-19,
including the fixed-buffer notation. This resolves syntax items (4) and (5)
and builds on the restored [struct-value notes](roadmap.md#struct-values).
None of this is implemented yet; the examples describe the accepted direction,
not programs accepted by the current parser. Array-length and trailing-array
extensions agreed on 2026-11-19 follow at the end of this section.

### Objects have storage; references identify storage

Keep `T` as the existing reference type for a struct. Use `*T`, pronounced
"contents of T", for its object value. A field of type `*T` embeds the object;
a local or argument of that type has value semantics. Registers and scalar
replacement remain implementation choices when an address is not observable.

```text
struct Complex { f64 x, y; };
struct Example {
    Complex ref;       // reference to separately owned storage
    *Complex value;    // two embedded coordinates
};
```

Embedding is chosen at the type use, not forced on every use of the class.
An embedding cycle with no intervening reference is rejected: it would require
an infinitely sized object. Existing access and binding modifiers remain
independent of reference-versus-value representation.

### Contents, addresses, and copying

An addressable expression denotes a storage location. Reading that expression
in a value context obtains its value; assignment writes the destination
location; address-taking obtains a reference to the location. This distinction
lets field access avoid first copying the whole enclosing object.

```text
Complex p = new Complex(args);    // construct storage and obtain a reference
Complex q = p;                    // copy reference; same target
*Complex v = *p;                  // initialize a new object by copying contents
*Complex w = v;                   // another object copy
Complex r = &v;                   // borrow v's existing storage; no allocation
*p = v;                           // copy contents into p's existing target
```

`*p` denotes the target object; `&v` denotes its address. Taking an address
requires existing addressable storage, not an arbitrary temporary expression.
Give a temporary a named owner before borrowing it. Nullable references must
be proven non-null before dereferencing, following the existing safety rules.

No implicit conversion changes a reference into an object copy or an object
into a reference. `p = v` and `v = p` are type errors. A value copy copies
embedded values recursively and copies reference fields as references; it
does not clone the reachable object graph. Those copied references retain
their original lifetime obligations.

Field access and method syntax remain uniform: `p.x`, `v.x`, `p.len()`, and
`v.len()`. A method on an addressable object uses that object's storage as its
receiver; the call does not silently copy the receiver. Retaining that receiver
is governed by the ordinary function lifetime contract.

Preserve the useful binding distinction from the roadmap:

```text
holder.*c       // contents of the reference field: *(holder.c)
(*holder).c     // field c of the object referenced by holder
```

These are not the same operation. Ordinary prefix `*holder.c` binds as
`*(holder.c)`, with field selection preceding prefix dereference. The `.*`
form makes the field being dereferenced explicit. Dereferencing an already
inline value is an error. The old tentative alternative `ref.c = val.*c`
would therefore not be adopted: it cannot implicitly turn a reference
assignment into an object assignment.

### Construction and assignment are separate

Use `new *T(args)` for constructing an object value directly in its
destination, alongside `new T(args)` for constructing storage and returning
a reference. Value results may be constructed in caller-provided storage;
the language need not require a temporary allocation and copy.

Copy initialization can initialize final fields. Assignment into an existing
object can write only fields permitted at that program point. Thus `*p = v`
is not a back door for changing final fields or writing through read-only
references. Initially reject whole-object assignment when it would overwrite
protected fields; do not silently skip them.

Whole-object assignment preserves the destination's identity and addresses of
its embedded fields. Existing interior references observe the resulting normal
field updates. Semantically obtain the source value before writing the
destination so overlapping copies do not corrupt the source. Assignment does
not rerun constructors or perform user-defined copy callbacks.

Ending an object's lifetime and reconstructing its storage is a separate
operation, not an alternative interpretation of assignment. In particular,
reusing a removed vector element's slot must not invalidate surviving interior
references or silently bypass final-field rules. General reconstruction syntax
is outside this proposal.

### Arrays distinguish element layout from element access

```text
Complex[] refs;                 // array of Complex references
(*Complex)[] objects;           // array of embedded Complex objects
var a = new *Complex[N];        // allocate embedded element storage
```

Elements must still be completely initialized before exposure; the final line
is a layout sketch, not permission to omit required initialization. Array
initializers and append must support construction or copy initialization into
the element destination. Only initialized elements contribute to visible length.

For an embedded-element array:

```text
*Complex v = a[i];              // copy the element's value
Complex p = &a[i];              // borrow the element's fixed storage
a[i].x = 3;                    // update in place
a[i] = v;                      // whole-object assignment, subject to permissions
```

For `Complex[]`, `refs[i]` copies a reference, not a Complex. For `int[]`,
`a[i]` produces an integer value. Indexing itself therefore remains uniform;
the element type determines what is copied in a value context. Bounds checks
apply before access or address-taking.

Use `&V` as the type of a reference to storage holding
a value of type V. `&(*Complex)` is the existing `Complex` reference type;
`&int` references an integer slot; `&Complex` references a slot that itself
holds a Complex reference. This permits primitive and reference-slot borrowing
without inventing wrapper objects. Nested references carry the corresponding
storage lifetime constraints at each level.

### Interior references borrow the actual backing storage

`&a[i]` has a lifetime bounded by that particular array, not by a container
that happens to point to it. `&owner.value` is bounded by the owner's storage.
Creating a reference does not transfer ownership, allocate a copy, or extend
the source lifetime. Several references may alias; no blanket exclusive-borrow
rule is proposed. Access permissions and memory-effect rules still apply.

A function returning such a reference exposes that origin in its lifetime
contract. Functions can infer it; external declarations must state it. Exact
contract spelling remains item (6). Ordinary direct references need not carry
a runtime owner pointer merely because the checker tracks their origin.

An array never moves. A vector may select a different backing array. A direct
reference returned into the old array continues to identify the old element;
replacement does not retarget it. The old storage cannot be reclaimed or
recycled while that reference remains usable. Such a reference is not a promise
to follow the vector's current logical element.

An interface promising a stable reference to a current container element must
instead prevent conflicting relocation during the reference's usable lifetime,
or use a representation that preserves element storage. The implementer chooses
the contract; it must not depend on whether this particular append happened to
fit within capacity. Proven non-relocating operations can be allowed.

A `(container, index)` handle is an ordinary library value with explicitly
different behavior: it resolves the current storage and checks the current
length on access. Removal or reordering may change which element it identifies;
it does not promise persistent element identity.

### Embedded small vectors

The descriptor is an embedded object. Its small fixed buffer also needs an
embedded layout. The accepted extension of the same type notation is:

```text
T[4]               // reference to an array with fixed extent 4
*(T[4])            // the fixed array itself, embedded in its owner
(*T)[4]            // reference to an array of four embedded T objects
```

The parentheses distinguish embedding the array from embedding its elements.
An embedded array followed by other storage needs a statically known extent;
a dynamic trailing array is the additional layout described below. The fixed
extent can make a stored length redundant. Bounds checks still apply. This
fixed-buffer spelling was accepted on 2026-11-19 and is not part of the older
recovered notes.

A Node can then embed vector descriptors and small arrays of Node references.
Reading a small element needs no separately allocated descriptor or backing
array. Overflow uses a backing pointer. Initially the fixed buffer and overflow
pointer can coexist; sharing their bytes would require a safely discriminated
layout, which is not needed to obtain the main indirection savings.

Reference-valued `get` returns the stored Node reference and does not borrow
the vector slot. Value-valued `get` copies the embedded element. Direct slot
borrowing should be a separately named library operation with an explicit
storage contract; current indexing syntax need not gain overloads to implement
the first vector library.

### Interaction with delete

An embedded object cannot be independently deleted: its storage belongs to its
owner, not to a separately recyclable slot. This must be checked from allocation
origin even if a borrowed reference has nominal type T and T permits deletion.
Deleting the containing object, when permitted, affects the whole slot.

Consequently, an embedded child of a deletable owner inherits the possibility
of stale aliases and reconstruction, even if the child's nominal class is not
deletable. Its constructor cannot assume private memory merely from that class.
The prohibition on publishing incomplete references into deletable storage
also applies through embedded fields. Final-field reads through old interior
references follow the containing slot's post-delete rules.

Likewise, recycling a vector's old backing storage requires proving that no
usable references remain, including references recoverable through permitted
historical fields of a deletable owner. Merely replacing its backing pointer
does not establish that proof.

These are necessary consequences of embedding in recyclable storage, not a
proposal to weaken the existing type-safety or initialization guarantees.

### Array length is separate from element storage

Agreed addition, 2026-11-19: an embedded array's logical length and its element
data are separate layout components. Embedding must not force a four-byte
length header immediately before every buffer. A containing object can carry
the length separately from the inline elements. Fixed-buffer extent, a
container's used length, and any overflow capacity remain distinct quantities.

For `*(T[4])`, the array extent is the constant four. Constant propagation can
fold uses of that extent. Eliminating the redundant physical length field is
future work (possibly dead-field elimination); it is not part of the current
implementation task. A vector using that buffer can still have a separate
changing used length between zero and four.

Lengths need not occupy four bytes. A short-string representation can select
a one-byte unsigned length, for example. Each array representation has a known
maximum element count; an unsigned one-byte length supports a maximum of 255.
Allocation and growth must respect that maximum as well as any applicable
storage capacity. Preventing length wrap or truncation is a consequence of
enforcing the maximum, not a separate array rule. The length's storage width
is part of the layout contract, and access must use that representation.
The exact declaration spelling and conversions
between layouts remain to be specified; a universal four-byte header must not
be assumed by the design.

### A uniform struct with an array tail

Agreed addition, 2026-11-19: support a single object containing fixed named
fields followed by a variable-length array tail. The object supports named
field access and array access; no separately allocated backing array or wrapper
is required. The tail is physically last and may grow until finalized under
the same scope-based growth rules as other in-place growing arrays.

The older roadmap's field notation is a useful syntax candidate, not a newly
settled spelling:

```text
struct ShortText {
    // Other fixed named fields may precede the tail.
    u8 #;       // compact logical length
    u8 [];      // trailing byte elements in this same object
};
```

An unsigned one-byte length gives this representation a known maximum of 255
elements; a wider length can support a larger maximum. An open tail has a
changing length up to that maximum, rather than a fixed declared element count.
Named fields retain their offsets while the tail extends. Newly
appended elements must be initialized before becoming accessible. Indexing is
checked against the current logical length.

Finalization ends tail growth without moving the object or making its mutable
fields read-only. The resulting storage can survive the creating scope when
its inferred or explicit destination permits that lifetime. Interior references
remain tied to the same storage. The layout planner must preserve room for
physical growth; this type cannot silently relocate merely because a container
with replaceable backing storage could do so.

This generalizes embedded arrays to a dynamic final field. It does not permit
arbitrary variable-sized fields followed by other fields, and it does not add
array deletion. How to spell construction and append for the combined type,
and whether any combined type may independently opt into class deletion,
remain explicit follow-up questions; neither follows just from the layout.
