# Chapter 8

In this chapter:

* We move from eager to lazy phi creation in loops.
* We add support for `break` and `continue` statements.
* We implement a Sea of Nodes Graph Evaluator.

Here is the [complete language grammar](docs/08-grammar.md) for this chapter.

## Lazy Phi Creation

In [chapter 7](../chapter07/README.md), we created phis for all variables in the loop head eagerly. This approach creates some unnecessary 
phis but the redundant phis get cleaned up later.

In this chapter we add support for creating the phi's lazily. 

The main complication with creating phis lazily is that when we create a phi, it must be created in the innermost loop head scope where
it would have been created had we done it eagerly. Since scopes get duped in `if` and `while` statements, we cannot just create phis
in the current scope.

It turns out that the easiest way to create the phis lazily is to hook them up to name lookup. But how do we know that a phi must be
created when we access a name? We need a way to flag that a particular name mapping must be converted to a phi on lookup.

We do this as follows:

* In `ScopeNode.dup()`, if the Scope is being duplicated for a loop head, then instead of setting the name's definition to the original node, we set it to the Scope itself. This acts like a sentinel.
* Subsequently, when a lookup accesses a name, we notice that the def is set to the Scope node, and we create a Phi at this point. The relevant implementation is in `ScopeNode.update()`.
* When we merge scopes, we ensure lazy phi creation at the correct inner scope level by invoking lookup by name instead of directly accessing the defs.
* Finally, in `ScopeNode.endLoop()` we clean up any leftover sentinels, replacing the sentinel with the input from loop head.

## `continue` Statement

In chapter7, we had a single backedge from the loop's end flowing back to the loop head. 

With the addition of a `continue` statement, we can have multiple backedges flowing back to the loop head. 

We have several ways of implementing these backedges.

1. The traditional way would be to let each backedge from continue merge into the loop head. Phis would require as many inputs as there are edges.
2. The alternative is collect all continues at a single merge point (Region) and then create a single backedge flowing from the continue region to the loop head. 
3. A third approach is to create a stack of continue Regions rather than a single continue Region. This has the benefit of keeping our Phi's simple with just two inputs as they are now, whereas the other approaches require Phis with more than 2 inputs. On the other hand we end up with as many continue Regions as there are `continue` statements, and corresponding phis that are stacked.

In this chapter we adopt option 3 as it is the simplest implementation op top of our basic `while` loop architecture.
We also experimented with option 2. For readers who would like to see this alternative solution, we provide a separate branch with such an implementation.

TODO explain why we didn't consider option 1 (as per Cliff many good things arise from having a single backedge to loop head, but its not stated what these good things are).

The implementation requires some careful handling of scopes. This is because we would like to only generate Phis/Regions for continues if necessary.

In our [basic loop architecture](../chapter07/README.md), by the time we get to the loop backedge, we have already exited all nested blocks/scopes, we just need to stitch together the
current scope+ctrl into the Loop scope+ctrl and create Phi's as needed.

A `continue` however, can occur inside nested scopes (such as nested `if` statement). Since we want to lazily create the continue region, the first time
we see a `continue`, we need to dupe the head scope, and merge the current scope into it to generate a continue scope and region. Subsequently, when we 
see another `continue`, we need to construct a new region and merge the previous continue scope with the current scope.

The implementation is a variation of above.

* The first `continue` triggers a dupe of the current scope; we prune any nested lexical 
scopes deeper than the head scope, removing any name bindings that were not visible in the head scope. This is essentially ensuring that we "exit" any nested scopes.
* The continue scope becomes the base scope for the subsequent `continue` statement, thus forming a stack of continue scopes/regions. 
* After the loop is done, if we find that a continue scope was created within the loop, we must merge the current scope into the continue scope, and make the continue scope the active scope.

## `break` Statement

Implementing `break` is simpler than `continue`, because the exit scope is created before we parse the loop body. So this becomes the initial `break` scope.
When we see `break`, we merge the current scope, after pruning any nested lexical scopes, to the current break scope, and the resulting new scope becomes
the active break scope.

## Parser Considerations

Since a `continue`/`break` target the immediate `while` loop within which they occur, we maintain a stack of the `continue` and `break` scopes.
This is done by saving the previous `continue`/`break` scopes before parsing a `while` loop and restoring these afterward.

## Sea of Nodes Graph Evaluator

TODO