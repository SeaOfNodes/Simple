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