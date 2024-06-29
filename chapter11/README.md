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

## Scheduling a Loop

We show another example, this time involving a loop. 

```java
struct S { int f; }
S v = new S;
int i = arg;
while (arg > 0) {
    int j = i/3;
    if (arg == 5)
        v.f = j;
    arg = arg - 1;
}
return v;
```

The SoN graph prior to scheduling looks like this:

![Graph4](./docs/graph4.svg)

Following early schedule generation, we get:

![Graph5](./docs/graph5.svg)

Note that the `arg === 5` comparison at this stage is not in the correct place.
This is rectified after we complete late scheduling.

![Graph6](./docs/graph6.svg)

The final execution schedule shows that the expression `i/3` can be performed outside the loop because it only depends on the
original value of `arg`, and hence is loop invariant.

## Components of the Global Code Motion Algorithm

We have already alluded to several components of the GCM algorithm in passing above. Here we list them out as well as others we did not mention:

* The SoN graph has implicit basic blocks in the control flow nodes. For the GCM algo, we need to recognize these nodes more explicitly.
* When a program has an infinite loop, it poses a problem for the algo, as nodes can be unreachable from the Stop node. To work around this issue, we need to discover
  infinite loops and create a dummy edge connecting the loop to the Stop node.
* Loops are already identified in the Simple SoN graph, so we do not need a loop discovery step. However, we need to compute the loop depth associated with each CFG node.
* In [Chapter 6](../chapter06/README.md) we explained the concept of Dominators. Dominators are key to the GCM algo, and we extend our incremental dominator discovery algo to 
  ensure that it meets the requirements of the algo (TODO did we enhance it in this chapter?).
* We already mentioned the two phases of the algo - the Early Scheduling and the Late Scheduling.
* In addition, we need to add anti-dependency edges between Loads and Stores in certain scenarios to enforce correct execution order.
* There are a few changes to our Node hierarchy to help us implement the GCM algo more conveniently. These changes do not conceptually alter the Node hierarchy we inherited from the previous chapters. 

## Identification of Basic Blocks in SoN graph

The Sea of Nodes graph already captures the programs control flow graph. This information is implicit in the control nodes and edges from control nodes to other types of nodes.

To recap, Control starts at the Start node, via a projection that is bound to the name `$ctrl`. As the control flows in the program, this name binding gets updated, and control is
passed around, until it reaches the Stop node. 

The Basic Block structure of the CFG can be easily constructed by recognizing that certain control nodes start a Basic Block, whereas certain others end a BB.

Here is the list of all control nodes:

| Node   | Starts a BB                             | Ends a BB |
|--------|-----------------------------------------|-----------|
| Start  | Yes but only via the `$ctrl` projection | No        |
| CProj  | Yes if input control is an If node      | No        |
| Region | Yes                                     | No        |
| If     | No                                      | Yes       |
| Return | No                                      | Yes       |
| Stop   | Yes                                     | Yes       |

Changes to Node hierarchy in this chapter:

* All control nodes above now extend a base class CFG Node. This allows us to place common functionality of control flow nodes in the base class.
* The CProj node extends the regular Proj node and is used in following cases:
  * The `$ctrl` projection off Start
  * The True and False projections off If.
* The Never node is a special sub class of If that is used to handle infinite loops as explained later.
* The XCtrl node represents a dead control.

## Handling Infinite Loops

Here is an example of code that contains an infinite loop:

```java
while (1) {}
return 0;
```

First lets look at the graph resulting from this:

![Graph7](./docs/graph7.svg)

Now, look at the modified graph after we insert an edge from the infinite loop to Stop node.

![Graph8](./docs/graph8.svg)

The implementation is in the Loop node:

```java
    // If this is an unreachable loop, it may not have an exit.  If it does not
    // (i.e., infinite loop), force an exit to make it reachable.
    public void forceExit( StopNode stop ) {
        // Walk the backedge, then immediate dominator tree util we hit this
        // Loop again.  If we ever hit a CProj from an If (as opposed to
        // directly on the If) we found our exit.
        CFGNode x = back();
        while( x != this ) {
            if( x instanceof CProjNode exit )
                return;         // Found an exit, not an infinite loop
            x = x.idom();
        }
        // Found a no-exit loop.  Insert an exit
        NeverNode iff = new NeverNode(back());
        for( Node use : _outputs )
            if( use instanceof PhiNode phi )
                iff.addDef(use);
        CProjNode t = new CProjNode(iff,0,"True" );
        CProjNode f = new CProjNode(iff,1,"False");
        setDef(2,f);
        stop.addDef(new ReturnNode(t,Parser.ZERO,null));
    }
```

## Dominators

In Simple, we compute Dominators incrementally. Our approach relies on the fact that we may delete parts of the graph during peepholes,
but we never introduce new control structure via peepholes. This allows us to use a simple approach described below.

The CFG node is the base class for all control nodes. It maintains a conservative approximation of dominator depth via `_idepth`. This field is a cached value representing
the immediate dominator depth. Its initial value is `0`, which signifies that it has not yet been computed. On request, we compute this as shown below.

```java
class CFGNode {
  public int idepth() { return _idepth==0 ? (_idepth=idom().idepth()+1) : _idepth; }
}
class RegionNode extends CFGNode {
  // Immediate dominator of Region is a little more complicated.
  @Override public int idepth() {
    if( _idepth!=0 ) return _idepth;
    int d=0;
    for( Node n : _inputs )
      if( n!=null )
        d = Math.max(d,((CFGNode)n).idepth()+1);
    return _idepth=d;
  }
}
class StartNode extends CFGNode {
  @Override public int idepth() { return 0; }
}
class StopNode extends CFGNode {
  @Override public int idepth() {
    if( _idepth!=0 ) return _idepth;
    int d=0;
    for( Node n : _inputs )
      if( n!=null )
        d = Math.max(d,((CFGNode)n).idepth()+1);
    return _idepth=d;
  }
}
```
If portions of the control flow graph are deleted, then there will be gaps in the `_idepth`, but it still correctly reflects the
invariant that the value of `_idepth` increases as we go down the dominator tree.

Alongside the dominator depth, which is cached on first compute, a method is provided to get the immediate Dominator node. This
value is not cached as it is only valid at a point in time, and is invalidated as the graph changes.

We show the code that computes this value:

```java
class CFGNode {
  // Return the immediate dominator of this Node and compute dom tree depth.
  public final CFGNode idom() { return cfg(0); }
  // Return the LCA of two idoms
  public CFGNode idom(CFGNode rhs) {
    if( rhs==null ) return this;
    CFGNode lhs = this;
    while( lhs != rhs ) {
      var comp = lhs.idepth() - rhs.idepth();
      if( comp >= 0 ) lhs = lhs.idom();
      if( comp <= 0 ) rhs = rhs.idom();
    }
    return lhs;
  }
}
class RegionNode extends CFGNode {
  @Override public CFGNode idom() {
    CFGNode lca = null;
    // Walk the LHS & RHS idom trees in parallel until they match, or either fails.
    // Because this does not cache, it can be linear in the size of the program.
    for( int i=1; i<nIns(); i++ )
      lca = cfg(i).idom(lca);
    return lca;
  }
}
class StartNode extends CFGNode {
  @Override public CFGNode idom() { return null; }
}
class StopNode extends CFGNode {
  @Override public CFGNode idom() { return null; }
}
```



[^1]: Cliff Click. (1995).
  Global Code Motion Global Value Numbering.