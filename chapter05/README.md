# Chapter 5

In this chapter we extend the language grammar with following features:

* If introduce `if` statement.
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

New Nodes introduced in this chapter:

| Node Name | Type    | Chapter | Description                                                          | Inputs                                                                                         | Value                                                         |
|-----------|---------|---------|----------------------------------------------------------------------|------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| If        | Control | 5       | Represents an `if` condition                                         | A control node and a data predicate node                                                       | A tuple of two values, one for true branch, another for false |
| Region    | Control | 5       | Represents a merge point from multiple control flow                  | An input for each control flow that is merging                                                 | None                                                          |
| Phi       | ?       | 5       | Represents the phi function that picks a value based on control flow | A region control node, and multiple data nodes that provide values from multiple control flows | Result is the extracted value depending on control flow taken | 
| Stop      | Control | 5       | Represents termination of the program                                | One or more Return nodes                                                                       | None                                                          |

