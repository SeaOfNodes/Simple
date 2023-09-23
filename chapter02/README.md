# Chapter 2

In this chapter we extend the language grammar to include arithmetic operations such as addition, subtraction,
multiplication, division, and unary minus. This allows us to write statements such as:

```
return 1 + 2 * 3 + -5;
```

Here is the [complete language grammar](docs/02-grammar.md) for this chapter. 

## Peephole Optimizations

Nodes in the graph can be peephole-optimized.  The graph is viewed through a
"peephole", a small chunk of graph, and if a certain pattern is detected we
locally rewrite the graph.

During parsing, these peephole optimizations are particularly easy to check and
apply: there are no uses (yet) of a just-created Node from a just-parsed piece
of syntax, so there's no effor to the "rewrite" part of the problem.  We just
replace in-place before installing Nodes into the graph.

E.g. Suppose we already parsed out a ConNode(1) and a ConNode(2); then when we
parse a AddNode(ConNode(1),ConNode(2)), the peephole rule for constant math
replaces the AddNode with a ConNode(3).

Right now, we limit the peephole rules to the AddNode, because otherwise we can
only parse constant expressions, and they would ALL fold up to a constant
during parsing.


## Nodes Pre Peephole Optimization

The following visual shows how the graph looks like pre-peephole optimization:

![Example Visual](./docs/02-pre-peephole-ex1.svg)

* Control nodes appear as square boxes
* Control edges are in bold red
* The edges from Start to Constants are shown in dotted lines as these are not true def-use edges