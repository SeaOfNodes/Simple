# Chapter 8

In this chapter:

* We move from eager to lazy phi creation in loops.
* We add support for `break` and `continue` statements.

Here is the [complete language grammar](docs/08-grammar.md) for this chapter.

## Lazy Phi Creation

In [chapter 7](../chapter07/README.md), we created phis for all variables in the loop head eagerly. This approach creates some unnecessary 
phis but the redundant phis get cleaned up later.

In this chapter we add support for creating the phi's lazily. 

The main complication with creating phis lazily is that when we create a phi, it must be created in the innermost loop head Scope where
it would have been created had we done it eagerly. Since Scopes get duped in `if` and `while` statements, we cannot just create phis
in the current scope.

It turns out that the easiest way to create the phis lazily is to hook them up to name lookup. But how do we know that a phi must be
created when we access a name? We need a way to flag that a particular name mapping must be converted to a phi on lookup.

We do this as follows:

* in `ScopeNode.dup()`, if the Scope is being duplicated for a loop head, then instead of setting the name's definition to the original node, we set it to the Scope itself. This acts like a sentinel.
* Subsequently, when a lookup accesses a name, we notice that the def is set to the Scope node, and we create a Phi at this point. The relevant implementation is in `ScopeNode.update()`.
* When we merge scopes, we ensure lazy phi creation at the correct inner scope level by invoking lookup by name instead of directly accessing the defs.
* Finally, in `ScopeNode.endLoop()` we clean up any leftover sentinels, replacing the sentinel with the input from loop head.

## `continue` statement

In chapter7, we had a single backedge from the loop's end, flowing back to the loop head.

With the addition of a `continue` statement, we can have multiple backedges flowing back to the loop head.

We have several options regarding how we implement these backedges.

1. The traditional way would be to let each backedge from continue merge into the loop head. Phis would require as many inputs as there are edges.
2. The alternative is to maintain a single backedge flowing into the loop head, but create a separate merge point (Region) for the continues. 
3. A different approach to the one above is to create a stack of continue Regions rather than a single continue Region. This has the benefit of keeping our Phi's simple with just two inputs as they are now, whereas the other approaches require Phis with more than 2 inputs. On the other hand we end up with as many continue Regions as there are `continue` statements, and corresponding phis that are stacked.

In this chapter we have adopted option 3 as it is the simplest implementation op top of our basic `while` loop architecture.
We also experimented with option 2, but this is not adopted as a solution. For readers who would like to see this alternative solution, we provide a separate branch with such an implementation.

TODO explain why we didn't consider option 1 (as per Cliff many good things arise from having a single backedge to loop head, but its not stated what these good things are).

The implementation requires some careful handling of scopes. This is because we would like to only generate Phis for continues if necessary.

A `continue` can be invoked in nested scopes (such as nested `if` statement). Since we want to lazily create the continue region, the first time
we see a `continue`, we need to dupe the head scope, and merge the current scope into it to generate a continue scope and region. Subsequently, when we 
another `continue`, we need to construct a new region and merge the previous continue scope with the current scope.

The implementation does it somewhat differently. The first `continue` triggers a dupe of the current scope, but we truncate any nested lexical 
scopes within the scope, in order to remove any name bindings that were not visible in the head scope. This becomes the base scope for 
subsequent `continue` statements. After the loop is parsed, if we see that a continue scope was created, then we merge the current scope 
into the continue scope, and the continue scope becomes the active scope.

Since a `continue` targets the immediate `while` loop within which it occurs, we also must maintain a stack of the `continue` and `break` scopes.
This is done by saving the previous `continue`/`break` scopes before parsing a `while` loop and restoring these afterwards.

## `break` statement




