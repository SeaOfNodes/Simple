# Chapter 11: Global Code Motion

This chapter schedules the memory representation introduced in
[10b](../chapter10b/README.md): one parser-visible `$mem`, with `MemMerge`,
`MemPhi`, and `BulkMemPhi` recovering independent field chains. Start supplies
`{ctrl, $mem, arg}`. Return consumes `{ctrl, $mem, result}`, with the complete
memory aggregate in slot 1. Nodes consuming or producing both control and
memory consistently use slot 0 for control and slot 1 for memory.

The new scheduling rule is to distinguish a memory aggregate from a memory
write. `MemMerge` gets a placement from its inputs and uses, but emits no heap
operation and creates no load/store anti-dependency. A Store or MemPhi can
constrain a Load only for the same alias; a BulkMemPhi can constrain it only
while it still covers that alias. Both kinds of memory Phi remain attached to
their Region, just like scalar Phis.


# Table of Contents

1. [High Level Overview](#high-level-overview)
2. [Scheduling Walk Through](#scheduling-walk-through)
3. [Scheduling a Loop](#scheduling-a-loop)
4. [Components of the Global Code Motion Algorithm ](#components-of-the-global-code-motion-algorithm)
5. [Identification of Basic Blocks in SoN graph](#identification-of-basic-blocks-in-son-graph)
6. [Handling Infinite Loops](#handling-infinite-loops)
7. [Dominators](#dominators)
8. [Loop depth](#loop-depth)
9. [Early Schedule](#early-schedule)
10. [Late Schedule](#late-schedule)
11. [Inserting Anti Dependencies](#inserting-anti-dependencies)
12. [Video Walk Through](#video-walk-through)


You can also read [this chapter](https://github.com/SeaOfNodes/Simple/tree/linear-chapter11) in a linear Git revision history on the [linear](https://github.com/SeaOfNodes/Simple/tree/linear) branch and [compare](https://github.com/SeaOfNodes/Simple/compare/linear-chapter10b...linear-chapter11) it to the previous chapter.


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
* Schedule Late - in this phase we work backwards from Stop, scheduling uses before definitions, and move data nodes to a block between the first block calculated above,
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

These walkthrough diagrams show the precise `S.f` chain and the final
MemMerge consumed by Return. The unchanged default memory comes from Start;
any redundant bulk Phis have folded away. Arrows point from uses to definitions.

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

Note that the `arg == 5` comparison at this stage is not in the correct place.
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
  ensure that it meets the requirements of the algo.
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
  public int _idepth;
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
class LoopNode extends RegionNode {
  // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
  @Override public int idepth() { return _idepth==0 ? (_idepth=idom().idepth()+1) : _idepth; }
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
  public CFGNode idom() { return cfg(0); }
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
class LoopNode extends RegionNode {
  // Bypass Region idom, same as the default idom() using use in(1) instead of in(0)
  @Override public CFGNode idom() { return entry(); }
}
class StartNode extends CFGNode {
  @Override public CFGNode idom() { return null; }
}
class StopNode extends CFGNode {
  @Override public CFGNode idom() { return null; }
}
```

## Loop Depth

Simple's Sea of Nodes graph identifies loops explicitly via Loop nodes. Since the language provides a single way to create a loop, using the `while` statement,
it is not necessary to implement a generic loop discovery process.

We do however need to compute a loop depth. This is done similar to how we compute the dominator depth.

```java
class CFGNode {
  // Loop nesting depth
  public int _loopDepth;
  public int loopDepth() { return _loopDepth==0 ? (_loopDepth = cfg(0).loopDepth()) : _loopDepth; }
}
class RegionNode extends CFGNode {
  @Override public int loopDepth() { return _loopDepth==0 ? (_loopDepth = cfg(1).loopDepth()) : _loopDepth; }
}
class LoopNode extends RegionNode {
  @Override public int loopDepth() {
    if( _loopDepth!=0 ) return _loopDepth; // Was already set
    _loopDepth = entry()._loopDepth+1;     // Entry depth plus one
    // One-time tag loop exits
    for( CFGNode idom = back(); idom!=this; idom = idom.idom() ) {
      // Walk idom in loop, setting depth
      idom._loopDepth = _loopDepth;
      // Loop exit hits the CProj before the If, instead of jumping from
      // Region directly to If.
      if( idom instanceof CProjNode proj ) {
        assert proj.in(0) instanceof IfNode; // Loop exit test
        // Find the loop exit CProj, and set loop_depth
        for( Node use : proj.in(0)._outputs )
          if( use instanceof CProjNode proj2 && proj2 != idom )
            proj2._loopDepth = _loopDepth-1;
      }
    }
    return _loopDepth;
  }
}
class StartNode extends CFGNode {
  @Override public int loopDepth() { return (_loopDepth=1); }
}
class StopNode extends CFGNode {
  @Override public int loopDepth() { return (_loopDepth=1); }
}
```

## Early Schedule

The GCM algorithm proper starts with the computation of the early schedule, during which do an upward DFS walk on the "inputs" of each Node, starting from the bottom (Stop). We schedule each data node to the
first control block where they are dominated by their inputs.

A pre-condition of this is to ensure that infinite loops have been "fixed" as described earlier.

The implementation of early schedule is shown below:

```java
    private static void schedEarly() {
        ArrayList<CFGNode> rpo = new ArrayList<>();
        BitSet visit = new BitSet();
        _rpo_cfg(Parser.START, visit, rpo);
        // Reverse Post-Order on CFG
        for( int j=rpo.size()-1; j>=0; j-- ) {
            CFGNode cfg = rpo.get(j);
            cfg.loopDepth();
            for( Node n : cfg._inputs )
                _schedEarly(n,visit);
            // In dead infinite loops, entire code blocks may be unreachable
            // from below.  Reach down from the CFG to their Phis so their
            // inputs are scheduled too.
            if( cfg instanceof RegionNode ) {
                int len = cfg.nOuts();
                for( int i=0; i<len; i++ )
                    if( cfg.out(i) instanceof PhiNode phi )
                        _schedEarly(phi,visit);
            }
        }
    }

    // Post-Order of CFG
    private static void _rpo_cfg(Node n, BitSet visit, ArrayList<CFGNode> rpo) {
        if( !(n instanceof CFGNode cfg) || visit.get(cfg._nid) )
            return;             // Been there, done that
        visit.set(cfg._nid);
        for( Node use : cfg._outputs )
            _rpo_cfg(use,visit,rpo);
        rpo.add(cfg);
    }

    private static void _schedEarly(Node n, BitSet visit) {
        if( n==null || visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);
        // Schedule inputs first, except Phis: following their backedges would
        // enter a data cycle before its control has been scheduled.
        for( Node def : n._inputs )
            if( def!=null && !(def instanceof PhiNode) )
                _schedEarly(def,visit);
        // An existing edge 0 already supplies control (or a Phi/Proj binding).
        if( n.in(0)==null ) {
            // Schedule at deepest input
            CFGNode early = Parser.START; // Maximally early, lowest idepth
            for( int i=1; i<n.nIns(); i++ )
                if( n.in(i)!=null && n.in(i).cfg0().idepth() > early.idepth() )
                    early = n.in(i).cfg0(); // Latest/deepest input
            n.setDef(0,early);              // First place this can go
        }
    }
```

Existing control and Phi/Proj bindings are preserved. Floating nodes receive
the deepest input block as their earliest placement. MemMerge follows this
same rule, ignoring absent entries in its sparse alias table.

## Late Schedule

Late scheduling starts at Stop and uses a worklist to place uses before their
definitions. CFG nodes, Phis, projections, and allocations provide fixed
placements. Other nodes wait until their uses have been scheduled; a Load also
waits for memory users that can overwrite or merge its alias. The chosen block
lies between the early placement and the common dominator of all uses, favoring
shallower loops and then deeper control flow.

```java
    private static void schedLate( StopNode stop) {
        CFGNode[] late = new CFGNode[Node.UID()];
        Node[] ns = new Node[Node.UID()];
        // Record Load NIDs at all their CFG block choices, then check against
        // Store block choices to force a Load above an anti-dependent Store.
        int[] anti = new int[Node.UID()];
        // Breadth-first scheduling
        breadth(stop,ns,late,anti);

        // Copy the best placement choice into the control slot
        for( int i=0; i<late.length; i++ )
            if( ns[i] != null && !(ns[i] instanceof ProjNode) )
                ns[i].setDef(0,late[i]);
    }

    private static void breadth(Node stop, Node[] ns, CFGNode[] late, int[] anti) {
        // Things on the worklist have some (but perhaps not all) uses done.
        WorkList<Node> work = new WorkList<>();
        work.push(stop);
        Node n;
        outer:
        while( (n = work.pop()) != null ) {
            assert late[n._nid]==null; // No double visit
            // These I know the late schedule of, and need to set early for loops
            if( n instanceof CFGNode cfg ) late[n._nid] = cfg.blockHead() ? cfg : cfg.cfg(0);
            else if( n instanceof PhiNode phi ) late[n._nid] = phi.region();
            // These nodes have a fixed late placement at their original control.
            else if( n instanceof ProjNode || n instanceof NewNode || n==Parser.ZERO ) late[n._nid] = n.cfg0();
            else {

                // All uses done?
                for( Node use : n._outputs )
                    if( use!=null && late[use._nid]==null )
                        continue outer; // Nope, await all uses done

                // Loads need their memory inputs' uses also done
                if( n instanceof LoadNode ld )
                    for( Node memuse : ld.mem()._outputs )
                        if( antiUse(ld,memuse) && late[memuse._nid]==null )
                            continue outer;

                // All uses done, schedule
                _doSchedLate(n,ns,late,anti);
            }

            // A use just finished; reconsider its inputs and waiting loads,
            // even when the shared memory input was already scheduled.
            for( Node def : n._inputs ) {
                if( def==null ) continue;
                if( late[def._nid]==null ) work.push(def);
                for( Node out : def._outputs )
                    if( out instanceof LoadNode ld && late[ld._nid]==null )
                        work.push(ld);
            }
            if( n instanceof LoopNode loop )
                for( Node phi : loop._outputs )
                    if( phi instanceof PhiNode && late[phi._nid]==null )
                        work.push(phi);
        }
    }

    private static void _doSchedLate(Node n, Node[] ns, CFGNode[] late, int[] anti) {
        // Walk uses, gathering the LCA (Least Common Ancestor) of uses
        CFGNode early = n.in(0) instanceof CFGNode cfg ? cfg : n.in(0).cfg0();
        assert early != null;
        CFGNode lca = null;
        for( Node use : n._outputs )
            if( use != null )
              lca = use_block(n,use, late).domLCA(lca);

        // Loads may need anti-dependencies, raising their LCA
        if( n instanceof LoadNode load )
            lca = find_anti_dep(lca,load,early,late,anti);

        // Walk up from the LCA to the early, looking for best place.  This is
        // the lowest execution frequency, approximated by least loop depth and
        // deepest control flow.
        CFGNode best = lca;
        lca = lca.idom();       // Already found best for starting LCA
        for( ; lca != early.idom(); lca = lca.idom() )
            if( better(lca,best) )
                best = lca;
        assert !(best instanceof IfNode);
        ns  [n._nid] = n;
        late[n._nid] = best;
    }

    // Block of use.  Normally from late[] schedule, except for Phis, which go
    // to the matching Region input.
    private static CFGNode use_block(Node n, Node use, CFGNode[] late) {
        if( !(use instanceof PhiNode phi) )
            return late[use._nid];
        CFGNode found=null;
        for( int i=1; i<phi.nIns(); i++ )
            if( phi.in(i)==n )
                found = phi.region().cfg(i).domLCA(found); // Can be more than one matching input.
        assert found!=null;
        return found;
    }


    // Least loop depth first, then largest idepth
    private static boolean better( CFGNode lca, CFGNode best ) {
        return lca.loopDepth() < best.loopDepth() ||
            lca instanceof NeverNode ||
            lca.idepth() > best.idepth() ||
            best instanceof IfNode;
    }
```

## Inserting Anti Dependencies

To ensure that Loads and Stores to the same memory location are correctly ordered, we insert an edge from the Store to the Load as described below: the Store must wait for the earlier Load.
We call these edges anti-dependencies because they do not represent the Def-Use dependency that we normally capture in SoN, and are purely present as scheduling constraints.

We compute anti-dependencies DURING running schedule late. This is because we rely on the early-schedule, and the late-schedule of the Load's uses (before scheduling the Load).

Looking backwards from the Load, we follow its memory input to Start's memory
projection, a memory Phi, or a Store. Optimization has already selected the
Load's precise slice through any MemMerge. We inspect users of that memory
definition, keeping only Stores and memory Phis that cover the Load's alias.
Start's single memory projection may supply many aliases, so sharing a memory
input alone does not imply an anti-dependency.

MemMerge only packages slices; it does not overwrite any of them. Treating it
as a write could make a Load wait for an aggregate which itself depends on the
Load's result. The same alias filter is used both when deciding whether a Load
is ready to schedule and when computing its anti-dependencies. A completed
memory user wakes waiting loads even if their shared memory definition has
already been scheduled.

Since we're in the middle of "schedule late", we have already computed all the late schedules of a Load's users, and we have the Loads "Least Common Ancestor" of uses, the LCA or late position.
We inspect the set of mem-defs that might impact the Load, and either add an anti-dependency from Store to Load, or raise the Loads effective LCA.
For Phi mem-defs, we look at the Phi inputs and place an effective use on that block; this will be used to raise the Load's LCA.
For stores, we do the same - until/unless we find stores with the SAME block as the Load's LCA. Then we add the anti-dependency edge to force ordering within the same block.

The implementation is shown below.

```java
    // Only a store or memory Phi covering this alias can constrain a load.
    // MemMerge packages slices without overwriting them.
    private static boolean antiUse(LoadNode load, Node use) {
        return switch( use ) {
        case StoreNode st -> st._alias==load._alias;
        case MemPhiNode phi -> phi._alias==load._alias;
        case BulkMemPhiNode phi -> !phi.isSplit(load._alias);
        default -> false;
        };
    }

    private static CFGNode find_anti_dep(CFGNode lca, LoadNode load, CFGNode early, CFGNode[] late, int[] anti) {
        // We could skip final-field loads here.
        // Walk LCA->early, flagging Load's block location choices
        for( CFGNode cfg=lca; early!=null && cfg!=early.idom(); cfg = cfg.idom() )
            anti[cfg._nid] = load._nid;
        // Walk load->mem uses, looking for Stores causing an anti-dep
        for( Node mem : load.mem()._outputs ) {
            if( !antiUse(load,mem) ) continue;
            switch( mem ) {
            case StoreNode st:
                lca = anti_dep(load,late[st._nid],st.cfg0(),lca,st,anti);
                break;
            case PhiNode phi:
                // Repeat anti-dep for matching Phi inputs.
                // No anti-dep edges but may raise the LCA.
                for( int i=1; i<phi.nIns(); i++ )
                    if( phi.in(i)==load.mem() )
                        lca = anti_dep(load,phi.region().cfg(i),load.mem().cfg0(),lca,null,anti);
                break;
            default: throw Utils.TODO();
            }
        }
        return lca;
    }

    //
    private static CFGNode anti_dep( LoadNode load, CFGNode stblk, CFGNode defblk, CFGNode lca, Node st, int[] anti ) {
        // Preserve the full store range for the earlier evaluator scheduler.
        // It places nodes independently of GCM and may hoist this store.
        for( ; stblk != defblk.idom(); stblk = stblk.idom() ) {
            // Store and Load overlap, need anti-dependence
            if( anti[stblk._nid]==load._nid ) {
                lca = stblk.domLCA(lca); // Raise Loads LCA
                if( lca == stblk && st != null && Utils.find(st._inputs,load) == -1 ) // And if something moved,
                    st.addDef(load);   // Add anti-dep as well
                return lca;            // Cap this stores' anti-dep to here
            }
        }
        return lca;
    }
```

## Video Walk Through

Please watch the following video for a detailed walk through of the implementation.

* [Coffee Compiler Club - 21 June 2024](https://youtu.be/5Po0gxfE7LA?feature=shared).
* [Coffee Compiler Club - 26 June 2024](https://www.youtube.com/watch?v=HnN2YzmFb-8&t=0s)

[^1]: Cliff Click. (1995).
  Global Code Motion Global Value Numbering.
