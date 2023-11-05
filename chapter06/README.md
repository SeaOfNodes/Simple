# Chapter 6

In this chapter we do not add new language features. Our goal is to add peephole optimization to `if` statements.

## Type System Revision

We now need to extend the type system to introduce another lattice structure, in addition to the Integer lattice.
As before we denote  "top" by T and "bottom" by ⊥.

"ctrl" represents a live control, whereas "~ctrl" represents a dead control.

|       | ⊥ | Ctrl | ~Ctrl | T     | INT |
|-------|---|------|-------|-------|-----|
| T     | ⊥ | Ctrl | ~Ctrl | T     | INT |
| Ctrl  | ⊥ | Ctrl | Ctrl  | Ctrl  | ⊥   |
| ~Ctrl | ⊥ | Ctrl | ~Ctrl | ~Ctrl | ⊥   |
| ⊥     | ⊥ | ⊥    | ⊥     | ⊥     | ⊥   |

* "top" meets any type is that type
* "bottom" meets any type is "bottom"
* "top", "bottom", "ctrl", "~ctrl" are classed as simple types
* Any simple type meets non-simple results in "bottom"
* "ctrl" meets "~ctrl" is "ctrl"
* "INT" refers to the integer type

## Peephole of `if` 

* Our general strategy is to continue parsing the entire `if` statement even if we know that one branch is dead.
* This approach creates redundant nodes, which get cleaned up by dead code elimination. The benefits are that we do not introduce
  special logic during parsing, the normal graph building machinery takes care of cleaning up dead nodes.
* When we create an `If` node, and peephole it, we know whether the predicate is a constant integer value, which is either
  `1` or `0`. If `1` the `false` branch is dead, and vice versa.
* When we add the `Proj` nodes, in this scenario, we already know one of the projections is dead. The peephole of the
  relevant `Proj` node replaces the `Proj` with a `Constant` of type "~ctrl" indicating dead control.
* At this point, our deadcode elimination would kill the `If` node, since its not needed anymore. But we cannot do that because
  we need to continue the parse. The important observation is that *we cannot kill the `If` node until __both__ the `Proj` nodes
  have been created*. 
* To ensure this we temporarily add a dummy user to `If` which is removed after constructing the first `Proj` node.
* When we create the second `Proj` node, the `If` gets killed by deadcode elimination. The live `Proj` gets replaced by the
  parent of `If`, whereas the dead `Proj` gets replaced by "~ctrl".
* The parsing continues.
* The other changes are when we reach the merge point. 
* Again keeping with our strategy we merge as normal, including creating `Phi` nodes.
* But the `Region` node will have one of its inputs as "~ctrl". This will be seen by the `Phi` which then replaces itself
  with the live input. So each `Phi` just dies and is replaced.
* Finally, at the end, when the `Region` node is peepholed, we see that it has only one live input, and thus not needed anymore.


  