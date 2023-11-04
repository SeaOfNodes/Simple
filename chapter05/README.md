# Chapter 5

In this chapter we extend the language grammar with the following features:

* We introduce the `if` statement.
* To support splitting of control flow and merging, we introduce new nodes: `Region` and `Phi`.
* Since we can now have multiple return points, we also introduce the `Stop` node as the termination.

Here is the [complete language grammar](docs/05-grammar.md) for this chapter.

## New Nodes

The following new nodes are introduced in this chapter:

| Node Name | Type    | Chapter | Description                                        | Inputs                                         | Value                                              |
|-----------|---------|---------|----------------------------------------------------|------------------------------------------------|----------------------------------------------------|
| If        | Control | 5       | A branching test, sub type of `MultiNode`          | A control node and a data predicate node       | A tuple of two values: one for true, one for false |
| Region    | Control | 5       | A merge point for multiple control flows           | An input for each control flow that is merging | Merged control                                     |
| Phi       | Data    | 5       | A phi function picks a value based on control flow | A Region, and data nodes for each control path | Depends on control flow path taken                 | 
| Stop      | Control | 5       | Termination of the program                         | All return nodes of the function               | None                                               |

## Recap

Here is a recap of the nodes introduced in previous chapters:

| Node Name | Type           | Chapter | Description                                    | Inputs                                                                        | Value                                                                      |
|-----------|----------------|---------|------------------------------------------------|-------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Multi     | Abstract class | 4       | A node that has a tuple result                 |                                                                               | A tuple                                                                    |
| Start     | Control        | 1       | Start of function, now a MultiNode             |                                                                               | A tuple with a ctrl token and an `arg` data node                           |
| Proj      | ?              | 4       | Projection nodes extract values from MultiNode | A MultiNode and index                                                         | Result is the extracted value from the input MultiNode at offset index     | 
| Bool      | Data           | 4       | Represents results of a comparison operator    | Two data nodes                                                                | Result is a comparison, represented as integer value where 1=true, 0=false |
| Return    | Control        | 1       | End of function                                | Predecessor control node and a data node for the return value of the function | Return value of the function                                               |
| Constant  | Data           | 1       | Represents constants such as integer literals  | None, however Start node is set as input to enable graph walking              | Value of the constant                                                      |
| Add       | Data           | 2       | Add two values                                 | Two data nodes without restrictions on the order                              | Result of the add operation                                                |
| Sub       | Data           | 2       | Subtract a value from another                  | Two data nodes, the first one is subtracted by the second one                 | Result of the subtraction                                                  |
| Mul       | Data           | 2       | Multiply two values                            | Two data nodes without restrictions on the order                              | Result of the multiplication                                               |
| Div       | Data           | 2       | Divide a value by another                      | Two data nodes, the first one is divided by the second one                    | Result of the division                                                     |
| Minus     | Data           | 2       | Negate a value                                 | One data node which value is negated                                          | Result of the negation                                                     |
| Scope     | ?              | 3       | Represents scopes in the graph                 | Nodes that represent the current value of variables                           | None                                                                       |

## `If` Nodes

`If` node takes in both control and data (predicate expression) and routes the
control token to one of the two control flows, represented by true and false
`Proj` nodes.

## `Phi` Nodes

A `Phi` reads in both data and control, and outputs a data value.  The control
input to the `Phi` points to a `Region` node.  The data inputs to the `Phi` are
one for each of the control inputs to that `Region`.  The result computed by a
`Phi` depends both on the data and the matching control input.  At most one
control input to the `Region` can be active at a time, and the `Phi` passes
through the data value from the matching input.[^1]

## `Region` Nodes

> Every instruction has a control input from a basic block. If the control input is an edge
> in our abstract graph, then the basic block must be a node in the abstract graph. So we
> define a REGION instruction to replace a basic block. A REGION instruction takes
> control from each predecessor block as input and produces a merged control as an output.[^2]

However:

> We can remove the control dependence for any given Node simply by replacing the
> pointer value with the special value NULL. This operation only makes sense for Nodes
> that represent data computations. PHI, IF, JUMP and STOP Nodes all require the control
> input for semantic correctness. REGION Nodes require several control inputs (one per
> CFG input to the basic block they represent). Almost all other Nodes can live without a
> control dependence. A data computation without a control dependence does not exactly
> reside in any particular basic block. Its correct behavior depends solely on the remaining
> data dependences. It, and the Nodes that depend on it or on which it depends, exists in a
> “sea” of Nodes, with little control structure.

> The “sea” of Nodes is useful for optimization, but does not represent any traditional
> intermediate representation such as a CFG. We need a way to serialize the graph and get
> back the control dependences. We do this with a simple global code motion algorithm.[^3]

Thus, we do not associate a control edge on every data node in the graph. 

We insert a `Region` node at a merge point where it takes control from each
predecessor's control edge, and produces a merged control as output.  Data flows
via `Phi` nodes at these merge points.

## Parsing an `if` Statement

When we parse an `if` statement, the control flow splits at that point.  We
must track the names being updated in each part of the `if` statement, and then
merge them at the end.  The implementation follows the description in *Combining Analyses, Combining Optimizations*[^4].

This involves following:

1. We create an `IfNode` with the current control token, i.e. the node mapped to
  `$ctrl`, and the `if` predicate expression as inputs.
2. We add two `ProjNodes` - one for the `True` branch (call if `ifT`), and the
  other for the `False` branch (call it `ifF`) - these extract values from the
  tuple result of the `IfNode`.
3. We duplicate the current `ScopeNode`.  The duplicated `ScopeNode` contains
  all the same symbol tables as the original, and has the same edges.
4. We set the control token to the `True` projection node `ifT`, and parse the true
  branch of the `if` statement.
5. We set the duplicated `ScopeNode` as the current one.
6. The control token is set to the `False` projection node `ifF`, and if there is an
  `else` statement we parse it.
7. At this point we have two `ScopeNode`s; the original one, potentially updated
  by the `True` branch, and the duplicate one, potentially updated by the
  `False` branch.  
8. We create a `Region` node to represent a merge point.
9. We *merge* the two `ScopeNode`s. We create `Phi` nodes for any names whose
  bindings differ.  The `Phi` nodes take the `Region` node as the control input
  and the two data nodes from each of the `ScopeNode`s.  The name binding is
  updated to point to the `Phi` node.
10. Finally, we set the `Region` node as the control token, and discard the duplicated `ScopeNode`.

Implementation is in [`parseIf` method in `Parser`](https://github.com/SeaOfNodes/Simple/blob/main/chapter05/src/main/java/com/seaofnodes/simple/Parser.java#L146-L186).

## Operations on ScopeNodes

As explained above, we duplicate ScopeNodes and merge them at a later point. There are some 
subtleties in how this is implemented that is worth going over.

### Duplicating a ScopeNode

Below is the code for duplicating a ScopeNode.

Our goals are:
1) Duplicate the name bindings across all stack levels
2) Make the new ScopeNode a user of all the bound nodes
3) Ensure that the order of defs in the duplicate is the same to allow easy merging

For implementation [see `scopeNode.dup()`](https://github.com/SeaOfNodes/Simple/blob/main/chapter05/src/main/java/com/seaofnodes/simple/node/ScopeNode.java#L99-L119)

### Merging two ScopeNodes

At the merge point we merge two ScopeNodes. The goals are:

1) Merge names whose bindings have changed between the two nodes. For each such name, a Phi node is created, referencing the two original data nodes.
2) A new Region node is created representing the merged control flow. The phis have this region node as the first input.
3) After the merge is completed, the duplicate is discarded, and its use of each of the nodes is also deleted.

The merging logic takes advantage of that fact that the two ScopeNodes have the bound nodes in the same order in the list of inputs. This was ensured during duplicating the ScopeNode. 
Although only the innermost occurrence of a name can have its binding changed, we scan all the nodes in our input list, and simply ignore ones where the binding has not changed.

For implementation [see `ScopeNode.mergeScopes()`](https://github.com/SeaOfNodes/Simple/blob/main/chapter05/src/main/java/com/seaofnodes/simple/node/ScopeNode.java#L121-L136)

## Example

We show the graph for the following code snippet:

```java
int a = 1; 
if (arg == 1) 
	a = arg+2; 
else
	a = arg-3;
return a; 
```

### Before Merging

Following shows the graph just before we merge the two branches of the `if` statement in a `Region` node.

![Graph1](./docs/05-graph1.svg)

Note the two `ScopeNode`s in the graph.
One has its `$ctrl` pointing to the `True` projection, while the other has `$ctrl` pointing to `False` projection.
The variable `a` is bound to the `Add` node in the `True` branch, whereas it is bound to the `Sub` node in the `False` branch.
Thus `a` will need a `Phi` node when merging the two scopes.

### After Merging

Below is the graph after we created a `Region` node and merged the two definitions of `a` in a `Phi` node.

![Graph2](./docs/05-graph2.svg)

The duplicate `ScopeNode` has been discarded and was merged into the other one.
Since `a` had two different definitions in both scopes a `Phi` node was created and is now referenced from `a`.
The `Phi` node's inputs are the `Region` node and the `Add` node from the `True` branch, and `Sub` node from the `False` branch.

### Finally

Here is the graph after the `return` statement was parsed and processed.

![Graph3](./docs/05-graph3.svg)

## More Peepholes

Phi's implement a peephole illustrated in the example:

```java
int a=arg==2;
if( arg==1 )
{
    a=arg==3;
}
return a;
```

Pre-peephole we have:

![Graph4](./docs/05-graph4.svg)

Post-peephole:

![Graph5](./docs/05-graph5.svg)

The implementation is in [`PhiNode.idealize()`](https://github.com/SeaOfNodes/Simple/blob/main/chapter05/src/main/java/com/seaofnodes/simple/node/PhiNode.java#L31-L71)

## More examples

```java
int c = 3;
int b = 2;
if (arg == 1) {
    b = 3;
    c = 4;
}
return c;
```

![Graph6](./docs/05-graph6.svg)

```java
int a=arg+1;
int b=arg+2;
if( arg==1 )
    b=b+a;
else
    a=b+1;
return a+b;
```

![Graph7](./docs/05-graph7.svg)

```java
int a=1;
if( arg==1 )
    if( arg==2 )
        a=2;
    else
        a=3;
else if( arg==3 )
    a=4;
else
    a=5;
return a;
```

![Graph8](./docs/05-graph8.svg)




[^1]: Click, C. (1995).
  Combining Analyses, Combining Optimizations, 132.

[^2]: Click, C. (1995).
  Combining Analyses, Combining Optimizations, 129.

[^3]: Click, C. (1995).
  Combining Analyses, Combining Optimizations, 86.

[^4]: Click, C. (1995).
  Combining Analyses, Combining Optimizations, 102-103.