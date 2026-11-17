# Automatic memory management: design survey

Research notes for Chapter 33, 2026-11-16. These are candidates for discussion,
not a selected design.

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
