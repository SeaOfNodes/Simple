# Ownership analysis architecture review

2026-11-23.

I agree with the broad assessment. Native effects and the Timer rewrite can wait; neither needs to drive this redesign.

After reading the implementation, I think the main problem is that we discard information in one analysis and reconstruct it in another. Better terminology would help expose that, but we also need clearer boundaries between the questions being answered.

I would describe the task as: points-to analysis and reference liveness, feeding region inference and checks for safe storage reuse. Alias analysis supplies much of the evidence, but cannot by itself determine when storage can be reclaimed.

Your solution space looks right, with these additional proof obligations:

| Storage choice | What must be established |
|---|---|
| Fixed storage reserved in the current or an enclosing frame | Size, sufficient slots for simultaneously needed objects, and a frame lifetime covering every permitted access |
| Dynamically sized storage at a growing stack tip | Lifetime compatibility and an allocation order compatible with contiguous growth and stack reclamation |
| Recycled non-array object slots | Type-safe reuse while old aliases remain valid; backing storage must still outlive those aliases |
| Recycled arrays | No usable access to the old array survives deletion |
| Require a rewrite | The available analysis cannot establish a supported storage arrangement |

Two additions matter here. Fixed object size does not imply fixed storage demand: one allocation site can produce arbitrarily many simultaneously needed objects. And dynamically sized storage need not have a compile-time size bound, but growth still has ordering constraints—a retained inner allocation can obstruct an outer growing array.

The first two choices fit the established subject of region inference: infer storage regions and prove that their reclamation respects accesses. Tofte and Talpin give a formal foundation for that separation between lifetime reasoning and runtime regions. [Region-Based Memory Management](https://ropas.snu.ac.kr/lib/dock/ToTa1997.pdf)

The terminology I would use for our current machinery is:

| Current concept | Established terminology |
|---|---|
| Possible reference “origins” | Points-to sets over abstract objects |
| `arg3.c` | An access path rooted at a formal parameter |
| Union of everything stored into a field | A flow-insensitive may-points-to relation |
| Following field contents at a memory version | Flow-sensitive points-to analysis |
| Forgetting a field’s previous contents after a definite overwrite | Strong update |
| Retaining both previous and newly stored possibilities | Weak update |
| Substituting arguments and allocations at a call | Instantiation of a procedure summary |
| Proving no usable alias survives | A lifetime/deallocation proof using alias and liveness information |

These distinctions are standard in pointer analysis; in particular, flow sensitivity determines whether facts distinguish program points. [Cornell’s pointer-analysis notes](https://www.cs.cornell.edu/courses/cs4120/2026sp/notes.html?id=pointer)

One especially important distinction: strong updates do not require that an object have only one pointer to it. They require that the analysis establish which concrete storage cell is overwritten. Several aliases can all observe that same overwrite. Conversely, a points-to set containing one abstract allocation-site node may represent many concrete objects, so “set size one” alone does not justify a strong update. This is exactly the problem addressed by work on allocation-site abstractions and recency abstractions. [Balakrishnan and Reps](https://research.cs.wisc.edu/wpis/papers/sas06-recency.pdf)

That distinction should help us avoid making “unique” stand for several different properties.

The clearest architectural issue is visible in a tiny example:

```text
old = v.buf;
v.buf = next;
old.del();
```

`Allocation` accumulates both buffers in the field’s possible contents. `ArrayDelete` then follows memory SSA to recover that the field no longer contains `old`. Calls and loops require additional machinery because their summaries have also merged facts from different times.

The code currently has two reference representations, `Site` and `Ref`, two reference-resolution mechanisms, and two forms of call substitution. Their purposes differ, but the overlap is substantial. `arrayOwned`, outgoing call versions, and loop-entry versions bridge the difference between the representations.

My inference is that this mismatch accounts for a substantial part of the special cases.

I would move toward one account of how references and memory change, with separate consumers for placement and deletion. A procedure summary should describe a transformation from incoming memory to outgoing memory, including:

- Possible returned references and newly allocated objects.
- Fields that may change, and fields definitely overwritten.
- Storage that may be consumed.
- Preconditions and guarantees needed for those operations.

For `grow(v)`, the useful contract is approximately: the incoming buffer may be consumed; the outgoing buffer is valid; aliases that could access a consumed buffer must not remain usable. The outgoing buffer may be the incoming buffer on the no-growth path. Giving it a new analysis name must not imply that it is a physically distinct allocation.

That is also where ideas from separation logic are useful: explicit preconditions, postconditions, and reasoning about the portion of memory a procedure affects. We need not adopt a full separation-logic prover to benefit from those distinctions. [Reynolds](https://www.cs.cmu.edu/~jcr/seplogic.pdf)

A second addition to your assessment is that “no aliases” is stronger than the property we ultimately need. An unused local can still contain the old pointer. A field can contain it if the field is definitely overwritten before any subsequent read. The safety question is whether a future execution can use that reference to access reclaimed storage.

That introduces reference liveness alongside points-to information. A conservative first implementation can reject whenever it finds a surviving reference, but the architecture should make clear that this is an approximation. Otherwise every improvement in recognizing dead aliases becomes another ownership exception.

The non-array deletion semantics deserve a separate, explicit rule. Because old references remain usable, deleting an object does not end the lifetime of its backing slot. It makes the slot eligible for reuse under the language’s historical-value rules. References recoverable from those permitted historical values also retain lifetime obligations. Arrays instead require the proof that old access has ended before recycling. Both can use the same alias information, but their reuse conditions are fundamentally different.

Finally, loops need a stated abstraction for repeated allocations. One abstract object per allocation site conflates successive objects. Our loop versions are already attempting to recover that distinction. Recency abstraction—separating the most recent object from older objects—is a relevant established option, though we should evaluate whether it fits our cases rather than adopt it automatically.

I would start the redesign with a small abstract semantics: what an abstract object denotes, what information a memory state contains, the rules for loads/stores/calls/joins, and the exact conditions for reclamation and reuse. Then work the existing two-grow and loop-winner examples through those rules by hand.

The simplification target is that initializers, ordinary helpers, consecutive grows, and loop iterations follow those same rules. That would give us a theoretical explanation for accepted programs, and a precise description of where our chosen approximation requires the user to rewrite.
