# Chapter 1

In this chapter we aim to compile simple scripts such as:

```
return 1;
```

We will be implementing the `return` statement first.
The `return` statement accepts an `expression` as an argument.
For this chapter, the language only has one type of `expression` - an integer literal, as
shown in the example above. 

Here is the [complete grammar](docs/01-grammar.md) of the language for this chapter. 

To implement this simple language, we will introduce several components and data
structures.

## Implementation Language

Our implementation language is Java. We chose Java as it is widely available and understood.

## Architecture

We will design our parser and compiler to illustrate some key aspects of the Sea of Nodes
Intermediate Representation: 

* We will construct the intermediate representation directly as we
parse the language. There will not be an intermediate Abstract Syntax Tree representation.

* The compiler will perform a few basic local optimizations as we build the
IR; this is a key benefit of the Sea of Nodes IR.

## Data Structures

Our data structures will be based upon the descriptions provided in following papers:

* [From Quads to Graphs: An Intermediate Representation's Journey](http://softlib.rice.edu/pub/CRPC-TRs/reports/CRPC-TR93366-S.pdf)
* [Combining Analyses, Combining Optimzations](https://scholarship.rice.edu/bitstream/handle/1911/96451/TR95-252.pdf)
* [A Simple Graph-Based Intermediate Representation](https://www.oracle.com/technetwork/java/javase/tech/c2-ir95-150110.pdf)
* [Global Code Motion Global Value Numbering](https://courses.cs.washington.edu/courses/cse501/06wi/reading/click-pldi95.pdf)

Following the lead from above, we will represent our intermediate representation using an object oriented data model. Details of the
representation follow.

### Intermediate Representation as a Graph of Nodes

The intermediate representation is a graph of Node objects. The `Node` class is the base type for objects in the IR graph.
The `Node` class provides common capabilities that are inherited by all subtypes. 
Each subtype implements semantics relevant to that subtype.

There are two types of Nodes in the representation.

* **Control Nodes** - these represent the control flow subgraph (CFG) of the compiled program
* **Data Nodes** - these capture the data semantics

The following control and data nodes will be created in this chapter.

| Node Name | Type    | Description                                   | Inputs                                    | Value                                                 |
|-----------|---------|-----------------------------------------------|-------------------------------------------|-------------------------------------------------------|
| Start     | Control | Start of function                             | None                                      | None for now as we do not have function arguments yet |
| Return    | Control | Represents the termination of a function      | Predecessor control node, Data node value | Return value of the function                          |
| Constant  | Data    | Represents constants such as integer literals | None                                      | Value of the constant                                 |

All control nodes will implement a marker interface named `Control`.

Within a traditional basic block, instructions are executed in sequence. In the Sea of Nodes model, the correct sequence of instructions is determined by a scheduling 
algorithm that depends only on dependencies between nodes (including control dependencies) that are explicit as edges in the graph. This enables a number of optimizations 
at very little cost (nearly always small constant time) because all dependencies are always available.

### Unique Node ID

Each node will be assigned a unique Node ID upon creation. The Node ID will be allocated in a particular compilation context.

### Node Comparisons

Nodes will by default be compared such that two nodes are equal if they have the same class type, and if all input nodes have the same Node ID.

### Start Node

The Start node represents the start of the function. For now, we do not have any values in the Start node, because in this chapter our function does not 
accept any parameters. When we add parameters, the value of the Start node will be a tuple, and will require Projection nodes to extract the values. However,
this will be subject of a later chapter.

### Constant Node

The Constant node represents the constant value. At present the only constants we allow are integer literals, hence the Constant node contains
an integer value. As we add other types of constants, we will need to refactor how we represent Constant nodes.

The Constant node has no inputs. Its output is the value stored in it.

### Return Node

The Return node has two inputs. The first input is a control node. The second input is the data node that will supply the return value.

The output of the Return node is the value it obtained from the data node.
