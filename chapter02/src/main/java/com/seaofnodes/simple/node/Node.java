package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.lang.StringBuilder;

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
     * The first input (offset 0) is often a Control node.
     * @see Control
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
     * A private Global Static mutable counter, for unique node id generation.
     * To make the compiler multi-threaded, this field will have to move into a TLS.
     * Starting with value 1, to avoid bugs confusing node ID 0 with uninitialized values.
     * */
    private static int UNIQUE_ID = 1;

    protected Node(Node ...inputs) {
        _nid = UNIQUE_ID++; // allocate unique dense ID
        _inputs = new ArrayList<>();
        Collections.addAll(_inputs,inputs);
        _outputs = new ArrayList<>();
        for( Node n : _inputs )
            if( n != null )
                n._outputs.add( this );
        // Do an initial type computation
        _type = compute();
    }

    public abstract String label();

    public String uniqueName() { return label() + _nid; }

    @Override
    public final String toString() {
        // TODO: This needs a lot of work
        return uniqueName();
    }

    // This is a *deep* print.  This version will fail on cycles, which we will
    // correct later when we can parse programs with loops.
    public final String print() {
        return _print(new StringBuilder()).toString();
    }
    abstract StringBuilder _print(StringBuilder sb);

    
    /**
     * Gets the ith input node
     * @param i Offset of the input node
     * @return Input node or null
     */
    public Node in(int i) { return _inputs.get(i); }

    public int nIns() { return _inputs.size(); }

    /**
     * Gets the ith output node
     * @param i Offset of the output node
     * @return Output node (not null)
     */
    public Node out(int i) { return _outputs.get(i); }

    public int nOuts() { return _outputs.size(); }


    // Try to peephole at this node and return a better replacment Node if
    // possible
    public final Node peephole( ) {
        // Replace constant computations with a constant node
        Type type = compute();
        if (!(this instanceof ConstantNode) && type.isConstant())
            return new ConstantNode(type);

        // Future chapter: Global Value Numbering goes here
        
        // Ask each node for a replacement
        Node n = idealize();
        if( n != null ) return n;
        
        return this;
    }

    /**
     * Current computed type for this Node.  This value changes as the graph
     * changes, and more knowledge is gained about the program.
     */
    public Type _type;
    
    /**
     * This function needs to be
     * @see <a href="https://en.wikipedia.org/wiki/Monotonic_function">Monotonic</a>
     * as it is part of a Monotone Analysis Framework, 
     * @see <a href="https://www.cse.psu.edu/~gxt29/teaching/cse597s21/slides/08monotoneFramework.pdf">see for example this set of slides</a>.
     * <p>
     * For Chapter 2, all our Types are really integer constants, and so all
     * the needed properties are trivially true and we can ignore the high
     * theory.  Much later on, this will become important and allow us to do
     * many fancy complex optimizations trivially... because theory.
     * <p>
     * Compute() needs to be stand-alone, and cannot recursively call compute()
     * on its inputs programs are cyclic (have loops!) and this will just
     * infinitely recurse until stack overflow.  Instead, compute typically
     * computes a new type from the _type field of its inputs.
     */
    public abstract Type compute();

    public abstract Node idealize();
    
    public static void reset() { UNIQUE_ID = 1; }
  
    /*
     * hashCode and equals implementation to be added in later chapter.
     */
  
}
