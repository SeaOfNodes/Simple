# Chapter 1

In this chapter we aim to compile simple scripts such as:

```
return 1;
```

The first statement to be implemented in the language is the `return` statement.
The `return` statement shall accept an argument, which must be an `expression`.
For this chapter, the language shall only have one type of `expression` - an integer literal, as
shown in the example above. 

Here is the [complete grammar](docs/01-grammar.md) of the language for this chapter. 

To implement this simple language, we will need to introduce several components and data
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
| Region    | Control | Represent a Basic Block                       | Predecessor control nodes in the CFG      | Each region represents one value                      |
| Return    | Control | Represents the termination of a function      | Predecessor control node, Data node value | Return value of the function                          |
| Constant  | Data    | Represents constants such as integer literals | None                                      | Value of the constant                                 |

All control nodes will implement a marker interface named `Control`.

Within a traditional basic block, instructions are executed in sequence. In the Sea of Nodes model, the correct sequence of instruction within a 
region will be determined by a scheduling algorithm, to be discussed in later chapters.

### Unique Node ID

Each node will be assigned a unique Node ID upon creation. The Node ID will be allocated in a particular compilation context.

### Node Comparisons

Nodes will by default be compared such that two nodes are equal if they have the same class type, and if all input nodes have the same Node ID.

### Start Node

TODO

### Region Node

TODO

### Return Node

TODO

### Constant Node

TODO