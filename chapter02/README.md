# Chapter 2

In this chapter we extend the language grammar to include arithmetic operations such as addition, subtraction,
multiplication, division, and unary minus. This allows us to write statements such as:

```
return 1 + 2 * 3 + -5;
```

Here is the [complete language grammar](docs/02-grammar.md) for this chapter. 

## Extensions to Intermediate Representation

In chapter 1 we introduced following nodes.

| Node Name | Type    | Description                                   | Inputs                                                           | Value                                                 |
|-----------|---------|-----------------------------------------------|------------------------------------------------------------------|-------------------------------------------------------|
| Start     | Control | Start of function                             | None                                                             | None for now as we do not have function arguments yet |
| Return    | Control | Represents the termination of a function      | Predecessor control node, Data node value                        | Return value of the function                          |
| Constant  | Data    | Represents constants such as integer literals | None, however Start node is set as input to enable graph walking | Value of the constant                                 |

We extend the set of nodes by adding following additional node types.

| Node Name  | Type | Description                   | Inputs                                                                                   | Value                       |
|------------|------|-------------------------------|------------------------------------------------------------------------------------------|-----------------------------|
| Add        | Data | Add two values                | Two data nodes, representing values to be added, order not important                     | Result of the add operation |
| Minus      | Data | Subtract a value from another | Two data nodes, the value from the second to be subtracted from the first, order matters | Result of the subtract      |
| Mul        | Data | Multiply two values           | Two data nodes, representing values to be added, order not important                     | Result of the multiply      |
| Div        | Data | Divide a value by another     | Two data nodes, lhs divided by rhs, order matters                                        | Result of the division      |
| UnaryMinus | Data | Negate a value                | One data node, representing value to be negated                                          | Result of the unary minus   |

## Peephole Optimizations

Nodes in the graph can be peephole-optimized.  The graph is viewed through a
"peephole", a small chunk of graph, and if a certain pattern is detected we
locally rewrite the graph.

During parsing, these peephole optimizations are particularly easy to check and
apply: there are no uses (yet) of a just-created Node from a just-parsed piece
of syntax, so there's no effort to the "rewrite" part of the problem. We just
replace in-place before installing Nodes into the graph.

E.g. Suppose we already parsed out a ConstantNode(1) and a ConstantNode(2); then when we
parse a AddNode(ConstantNode(1),ConstantNode(2)), the peephole rule for constant math
replaces the AddNode with a ConstantNode(3).

## Constant Folding and Constant Propagation

In this chapter and next we focus on a particular peephole optimization:
constant folding and constant propagation. Since we do not have variables until Chapter 3, the
main feature we demonstrate now is constant folding. However, we introduce some additional
ideas into the compiler at this stage, to set the scene for Chapter 3.

It is useful for the compiler to know at various points of the program whether
a node's value is a constant. The compiler can use this knowledge to perform various
optimizations such as:

* Evaluate expressions at compile time and replace an expression with a constant
* By doing so, the compiler may be able to identify regions of code that are dead and no longer needed, such as when
  a conditional branch always take one of the branches
* Additional optimizations may be possible once the compiler knows that certain nodes have constant values

In order to achieve above, we annotate Nodes with Types.

The Type annotation serves two purposes:

* The Type defines the set of operations allowed on the value.
* For the purposes of constant propagation, we also capture a set of known values for each type for a data node.

The type itself is identified by the Java class sub-typing relationship; all types are subtype of
the class Type. For now, we only have the following hierarchy of types:

```
Type
+-- TypeInteger
```

It turns out that the set of values associated with a Type at a specific Node can be conveniently
represented as a "lattice". The lattice has the following structure:

![Lattice](./docs/02-lattice.svg)

A lattice element can be one of three types: the highest element is "top", denoted by T.
The lowest is bottom, denoted by ⊥, and all elements in the middle are constants. These represent the set
of values of the type.

Assigning ⊥ means that the Node's value is not a compile time constant, whereas
assigning T means that the Node's value may be some (as yet) undetermined constant. The transition of the
Node's type can occur from T to some constant to ⊥.

The following shows how we represent the Type and the Lattice:

```java
public class Type {
    enum LatticeLevel {TOP, VALUE, BOTTOM}

    ;

    public LatticeLevel _level;
}

public class TypeInteger extends Type {
    public long _lo;
    public long _hi;

    public boolean isConstant() {
        return _lo == _hi;
    }
}
```

We allow for ranges of values to be represented, and range where the lower bound equals the upper bound
is a constant.

There are other important properties of the Lattice that we discuss in Chapter 3, such as the "meet" operator
and its rules.

## Nodes Pre Peephole Optimization

The following visual shows how the graph looks like pre-peephole optimization:

![Example Visual](./docs/02-pre-peephole-ex1.svg)

* Control nodes appear as square boxes
* Control edges are in bold red
* The edges from Start to Constants are shown in dotted lines as these are not true def-use edges