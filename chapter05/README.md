# Chapter 5

In this chapter we extend the language grammar with following features:

* We introduce the `if` statement.
* To support splitting of control flow and merging, we introduce new nodes: `Region` and `Phi`.
* Since we can now have multiple return points, we also introduce the `Stop` node as the termination.

Here is the [complete language grammar](docs/05-grammar.md) for this chapter.

## Recap

Here is a recap of the nodes introduced in previous chapters:

| Node Name  | Type           | Chapter | Description                                    | Inputs                                                           | Value                                                                   |
|------------|----------------|---------|------------------------------------------------|------------------------------------------------------------------|-------------------------------------------------------------------------|
| MultiNode  | Abstract class | 4       | A node that has a tuple result                 |                                                                  | A tuple                                                                 |
| Start      | Control        | 1       | Start of function, now a MultiNode             | An input argument named `arg`.                                   | A tuple with a ctrl token and an `arg` data node                        |
| Proj       | ?              | 4       | Projection nodes extract values from MultiNode | A MultiNode and index                                            | Result is the extracted value from the input MultiNode at offset index  | 
| Bool       | Data           | 4       | Represents results of a comparison operator    | Two data nodes                                                   | Result a comparison, represented as integer value where 1=true, 0=false |
| Return     | Control        | 1       | End of function                                | Predecessor control node, Data node value                        | Return value of the function                                            |
| Constant   | Data           | 1       | Constants such as integer literals             | None, however Start node is set as input to enable graph walking | Value of the constant                                                   |
| Add        | Data           | 2       | Add two values                                 | Two data nodes, values are added, order not important            | Result of the add operation                                             |
| Sub        | Data           | 2       | Subtract a value from another                  | Two data nodes, values are subtracted, order matters             | Result of the subtract                                                  |
| Mul        | Data           | 2       | Multiply two values                            | Two data nodes, values are multiplied, order not important       | Result of the multiply                                                  |
| Div        | Data           | 2       | Divide a value by another                      | Two data nodes, values are divided, order matters                | Result of the division                                                  |
| UnaryMinus | Data           | 2       | Negate a value                                 | One data node, value is negated                                  | Result of the unary minus                                               |
| Scope      | ?              | 3       | Represents scopes in the graph                 | All nodes that define variables                                  | None                                                                    |

## New Nodes

Following new nodes are introduced in this chapter:

| Node Name | Type    | Chapter | Description                                                          | Inputs                                                                                         | Value                                                         |
|-----------|---------|---------|----------------------------------------------------------------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| If        | Control | 5       | Represents an `if` condition, sub type of `MultiNode`                | A control node and a data predicate node                                                       | A tuple of two values, one for true branch, another for false |
| Region    | Control | 5       | Represents a merge point from multiple control flow                  | An input for each control flow that is merging                                                 | None                                                          |
| Phi       | ?       | 5       | Represents the phi function that picks a value based on control flow | A region control node, and multiple data nodes that provide values from multiple control flows | Result is the extracted value depending on control flow taken | 
| Stop      | Control | 5       | Represents termination of the program                                | One or more Return nodes                                                                       | None                                                          |

## Parsing of `If` statement

When we parse an `if` statement, the control flow splits at that point. We must track the names being updated in each part of the `if` statement, and then merge them at the end.

This involves following:

* The `IfNode` is created with the current control token, i.e. the node mapped to `$ctrl` and the predicate expression as inputs.
* We add two `ProjNodes` - one for the `True` branch (call if `ifT`), and the other for the `False` branch (call it `ifF`) - these extract values from the tuple result of the `IfNode`.
* We now create a duplicate of the current `ScopeNode`. The duplicated `ScopeNode` must have all the stack levels as the original, and moreover the new node must be a user of all the names that are currently bound.
* The original `ScopeNode` is saved.
* We set the duplicate `ScopeNode` as current.
* The control token is updated to the `True` projection node `ifT`.
* We parse the true branch of the `if` statement.
* We reset the original `ScopeNode` as current.
* We set control token to the `False` projection node `ifF`.
* If there is an `else` statement we parse it.
* At this point we have two `ScopeNode`s; the original one, potentially update by the `False` branch, and the duplicate one, potentially updated by the `True` branch.
* We create a `Region` node to represent a merge point.
* We compare the two `ScopeNode`s. We create `Phi` nodes for any names whose bindings differ. The `Phi` nodes take the `Region` node as the control input and the two data nodes from each of the `ScopeNode`s.
* Finally, we set the `Region` node as the control token, and discard the duplicated `ScopeNode`.

## Example


