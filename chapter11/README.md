# Chapter 11

The original input source program defines a sequence in which things happen. As we parse the program into Sea of Nodes representation
and perform various optimizations, this sequence is not fully maintained. The optimized Sea of Nodes graph is driven more
by dependencies between nodes rather that the sequence of instructions in the original source program. 

Our goal in this chapter is to look at how we can recover a schedule for executing instructions from an 
optimized Sea of Nodes graph. This schedule needs to preserve the semantics of the original source program,
but is otherwise allowed to reorder the execution of instructions.

Since the scheduling algorithm works across the implicit Basic Blocks in the SoN Graph,
we call this a Global Code Motion algorithm; the scheduler can move instructions across Basic Block boundaries.

The primary reference for this algorithm is the GCM GVN paper [^1]. However, the implementation in Simple has some
differences compared to the version described in the paper.

Since this is a complex topic, we will present a high level summary first and then delve into the details.

## High Level Overview

The Sea of Nodes graph has two virtual graphs within it.

* There is a control flow graph, represented by certain node types, such as Start, If, Region, etc. 
* There is a data graph, primarily driven by Def-Use edges between nodes that produce or consume values or memory.

From a scheduling point of view, the control flow graph is fixed, and immovable.
However, the nodes that consume or produce data values/memory have some flexibility in terms of when they are executed. The goal of the 
scheduling algorithm is to find the best placement of these nodes so that they are both optimum and correct.

At its core, the scheduling algorithm works in two phases:

* Schedule Early - in this phase, we do an upward DFS walk on the "inputs" of each Node, starting from the bottom (Stop). We schedule each data node to the 
  first control block where they are dominated by their inputs.
* Schedule Late - in this phase we do a downward DFS walk on the "outputs" of each Node starting from the top (Start), and move data nodes to a block between the first block calculated above, 
  and the last control block where they dominate all their uses. The placement is subject to the condition that it is in the shallowest loop nest possible, and is as control dependent as possible. 
  Additionally, the placement of Load instructions must ensure correct ordering between Loads and Stores of the same memory location.

## Scheduling Walk Through

Before we describe the steps in detail, it is instructive to walk through an example and see the changes that occur to the SoN graph during scheduling.

To motivate the discussion, we will use this example program.

```java
struct S { int f; }
S v=new S;
v.f = 2;
int i=new S.f;
i=v.f;
if (arg) v.f=1;
return i;
```

First lets look at the graph before scheduling.

![Graph1](./docs/graph1.svg)

Observe that

* Control nodes are colored in yellow; these are immovable.
* New nodes have control input and therefore these are already scheduled.
* Ditto for Phi nodes which are attached to the Region nodes.
* So what remains are the "floating" Data nodes that do not have a control input at this stage. In this example, these are the load `.f` and store `.f=` nodes.

Now, lets look at the graph after we run the early schedule.

![Graph2](./docs/graph2.svg)


* Observe that the load `.f` and the stores `.f=` now have control edges to the `$ctrl` projection from Start. Thus, the early schedule has put the Data nodes in the first basic block.
* This is because the inputs to these nodes are have the `$ctrl` projection as the immediate dominator.

The graph below shows the schedule post late scheduling.

![Graph3](./docs/graph3.svg)

The snip below shows the main changes in the graph:

![Graph3-snip](./docs/graph3-snip.jpg)

* The store `.f=` now has an anti-dependency on the load `.f`; this ensures that the store is scheduled after the load, as required by program semantics. We discuss anti-dependencies in detail later. 
* Observe also that the store `.f=` is now bound to the True branch of the If node. 




[^1]: Cliff Click. (1995).
  Global Code Motion Global Value Numbering.