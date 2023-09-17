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

