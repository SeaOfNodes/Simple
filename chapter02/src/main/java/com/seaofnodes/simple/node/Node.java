package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

import java.util.*;

/**
 * All Nodes in the Sea of Nodes IR inherit from the Node class.
 * The Node class provides common functionality used by all subtypes.
 * Subtypes of Node specialize by overriding methods.
 */
public abstract class Node {

    /**
     * Each node has a unique dense Node ID within a compilation context
     * The ID is useful for debugging, for using as an offset in a bitvector,
     * as well as for computing equality of nodes (to be implemented later).
     */
    public final int _nid;

    /**
     * Inputs to the node. These are use-def references to Nodes.
     * <p>
     * Generally fixed length, ordered, nulls allowed, no unused trailing space.
     * Ordering is required because e.g. "a/b" is different from "b/a".
     * The first input (offset 0) is often a {@link #isCFG} node.
     */
    public final ArrayList<Node> _inputs;

    /**
     * Outputs reference Nodes that are not null and have this Node as an
     * input.  These nodes are users of this node, thus these are def-use
     * references to Nodes.
     * <p>
     * Outputs directly match inputs, making a directed graph that can be
     * walked in either direction.  These outputs are typically used for
     * efficient optimizations but otherwise have no semantics meaning.
     */
    public final ArrayList<Node> _outputs;


    /**
     * Current computed type for this Node.  This value changes as the graph
     * changes and more knowledge is gained about the program.
     */
    public Type _type;

    /**
     * A private Global Static mutable counter, for unique node id generation.
     * To make the compiler multi-threaded, this field will have to move into a TLS.
     * Starting with value 1, to avoid bugs confusing node ID 0 with uninitialized values.
     * */
    private static int UNIQUE_ID = 1;

    protected Node(Node... inputs) {
        _nid = UNIQUE_ID++; // allocate unique dense ID
        _inputs = new ArrayList<>();
        Collections.addAll(_inputs,inputs);
        _outputs = new ArrayList<>();
        for( Node n : _inputs )
            if( n != null )
                n.addUse( this );
    }

    // Easy reading label for debugger, e.g. "Add" or "Region" or "EQ"
    public abstract String label();

    // Unique label for graph visualization, e.g. "Add12" or "Region30" or "EQ99"
    public String uniqueName() { return label() + _nid; }

    // Graphical label, e.g. "+" or "Region" or "=="
    public String glabel() { return label(); }


    // ------------------------------------------------------------------------

    // Debugger Printing.

    // {@code toString} is what you get in the debugger.  It has to print 1
    // line (because this is what a debugger typically displays by default) and
    // has to be robust with broken graph/nodes.
    @Override
    public final String toString() {
        // TODO: The print evolves in later chapters.  For now, a simple
        // recursive print does well.  Later with control flow and later still
        // with loops, we need some serious algorithm work to get a decent
        // print.  So you should consider the "print" to always be a work-in-
        // progress as we march through the chapters.
        return print();
    }

    // This is a *deep* print.  This version will fail on cycles, which we will
    // correct later when we can parse programs with loops.  We print with a
    // tik-tok style; the common _print0 calls the per-Node _print1, which
    // calls back to _print0;
    public final String print() {
        return _print0(new StringBuilder()).toString();
    }
    // This is the common print: check for DEAD and print "DEAD" else call the
    // per-Node print1.
    final StringBuilder _print0(StringBuilder sb) {
        return isDead()
            ? sb.append(uniqueName()).append(":DEAD")
            : _print1(sb);
    }
    // Every Node implements this.
    abstract StringBuilder _print1(StringBuilder sb);


    /**
     * Gets the ith input node
     * @param i Offset of the input node
     * @return Input node or null
     */
    public Node in(int i) { return _inputs.get(i); }

    public int nIns() { return _inputs.size(); }

    public Node out(int i) { return _outputs.get(i); }

    public int nOuts() { return _outputs.size(); }

    public boolean isUnused() { return nOuts() == 0; }

    public boolean isCFG() { return false; }


    /**
     * Change a <em>def</em> into a Node.  Keeps the edges correct, by removing
     * the corresponding <em>use->def</em> edge.  This may make the original
     * <em>def</em> go dead.  This function is co-recursive with {@link #kill}.
     *
     * @param idx which def to set
     * @param new_def the new definition
     * @return new_def for flow coding
     */
    Node setDef(int idx, Node new_def ) {
        Node old_def = in(idx);
        if( old_def == new_def ) return this; // No change
        // If new def is not null, add the corresponding def->use edge
        // This needs to happen before removing the old node's def->use edge as
        // the new_def might get killed if the old node kills it recursively.
        if( new_def != null )
            new_def.addUse(this);
        if( old_def != null &&  // If the old def exists, remove a def->use edge
            old_def.delUse(this) ) // If we removed the last use, the old def is now dead
            old_def.kill();     // Kill old def
        // Set the new_def over the old (killed) edge
        _inputs.set(idx,new_def);
        // Return self for easy flow-coding
        return new_def;
    }

    // Breaks the edge invariants, used temporarily
    protected <N extends Node> N addUse(Node n) { _outputs.add(n); return (N)this; }

    // Remove node 'use' from 'def's (i.e. our) output list, by compressing the list in-place.
    // Return true if the output list is empty afterward.
    // Error is 'use' does not exist; ok for 'use' to be null.
    protected boolean delUse( Node use ) {
        Utils.del(_outputs, Utils.find(_outputs, use));
        return _outputs.size() == 0;
    }

    /**
     * Kill a Node with no <em>uses</em>, by setting all of its <em>defs</em>
     * to null.  This may recursively kill more Nodes and is basically dead
     * code elimination.  This function is co-recursive with {@link #setDef}.
     */
    public void kill( ) {
        assert isUnused();      // Has no uses, so it is dead
        for( int i=0; i<nIns(); i++ )
            setDef(i,null);  // Set all inputs to null, recursively killing unused Nodes
        _inputs.clear();
        _type=null;             // Flag as dead
        assert isDead();        // Really dead now
    }

    // Mostly used for asserts and printing.
    boolean isDead() { return isUnused() && nIns()==0 && _type==null; }
    /**
     * We allow disabling peephole opt so that we can observe the
     * full graph, vs the optimized graph.
     */
    public static boolean _disablePeephole = false;

    /**
     * Try to peephole at this node and return a better replacement Node if
     * possible.  We compute a {@link Type} and then check and replace:
     * <ul>
     * <li>if the Type {@link Type#isConstant}, we replace with a {@link ConstantNode}</li>
     * <li>in a future chapter we will look for a
     * <a href="https://en.wikipedia.org/wiki/Common_subexpression_elimination">Common Subexpression</a>
     * to eliminate.</li>
     * <li>we ask the Node for a better replacement (again, none enabled in this chapter)</li>
     * </ul>
     */
    public final Node peephole( ) {
        // Compute initial or improved Type
        Type type = _type = compute();

        if (_disablePeephole)
            return this;        // Peephole optimizations turned off

        // Replace constant computations from non-constants with a constant node
        if (!(this instanceof ConstantNode) && type.isConstant()) {
            kill();             // Kill `this` because replacing with a Constant
            return new ConstantNode(type).peephole();
        }

        // Future chapter: Global Value Numbering goes here

        // Ask each node for a better replacement
        Node n = idealize();
        if( n != null ) return n;

        return this;            // No progress
    }


    /**
     * This function needs to be
     * <a href="https://en.wikipedia.org/wiki/Monotonic_function">Monotonic</a>
     * as it is part of a Monotone Analysis Framework.
     * <a href="https://www.cse.psu.edu/~gxt29/teaching/cse597s21/slides/08monotoneFramework.pdf">See for example this set of slides</a>.
     * <p>
     * For Chapter 2, all our Types are really integer constants, and so all
     * the needed properties are trivially true, and we can ignore the high
     * theory.  Much later on, this will become important and allow us to do
     * many fancy complex optimizations trivially... because theory.
     * <p>
     * compute() needs to be stand-alone, and cannot recursively call compute
     * on its inputs programs are cyclic (have loops!) and this will just
     * infinitely recurse until stack overflow.  Instead, compute typically
     * computes a new type from the {@link #_type} field of its inputs.
     */
    public abstract Type compute();

    public abstract Node idealize();

    /**
     * Used to allow repeating tests in the same JVM.  This just resets the
     * Node unique id generator, and is done as part of making a new Parser.
     */
    public static void reset() { UNIQUE_ID = 1; }
}
