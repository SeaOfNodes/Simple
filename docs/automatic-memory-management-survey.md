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

--------------------------------------------------------
