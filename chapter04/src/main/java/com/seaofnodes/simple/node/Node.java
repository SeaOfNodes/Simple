package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

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

    protected Node(Node ...inputs) {
        _nid = UNIQUE_ID++; // allocate unique dense ID
        _inputs = new ArrayList<>();
        Collections.addAll(_inputs,inputs);
        _outputs = new ArrayList<>();
        for( Node n : _inputs )
            if( n != null )
                n._outputs.add( this );
        // Do an initial type computation
        // _type = compute();
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

    /**
     * We allow disabling peephole opt so that we can observe the
     * full graph, vs the optimized graph.
     */
    public static boolean _disablePeephole = false;


    /**
     * Try to peephole at this node and return a better replacement Node if
     * possible.  We check and replace:
     * <ul>
     * <li>if the Type {@link Type#isConstant}, we replace with a {@link ConstantNode}</li>
     * <li>in a future chapter we will look for a
     * <a href="https://en.wikipedia.org/wiki/Common_subexpression_elimination">Common Subexpression</a>
     * to eliminate.</li>
     * <li>we ask the Node for a better replacement.  The "better replacement"
     * is things like "(1+x)" becomes "(x+1)" and "(1+(x+2)" becomes
     * "(x+(1+2))".  By canonicalizing expressions we fold common addressing
     * math constants, remove algebraic identities and generally simplify the
     * code. </li>
     * </ul>
     */
    public final Node peephole( ) {
        if (_disablePeephole)
            return this;        // Peephole optimizations turned off

        // Replace constant computations from non-constants with a constant node
        Type type = compute();
        if (!(this instanceof ConstantNode) && type.isConstant()) {
            kill();             // Kill `this` because replacing with a Constant
            return new ConstantNode(type);
        }

        // Future chapter: Global Value Numbering goes here
        
        // Ask each node for a replacement
        Node n = idealize();
        if( n != null )
          return n.peephole();  // Recursively reoptimize
        
        return this;            // No progress
    }

    /**
     * Kill a Node with no <em>uses</em>, by setting all of its <em>defs</em>
     * to null.  This may recursively kill more Nodes and is basically dead
     * code elimination.  This function is co-recursive with {@link #set_def}.
     */
    public void kill( ) {
        assert nOuts()==0;    // Has no uses, so it is dead
        for( int i=0; i<nIns(); i++ )
            set_def(i,null);  // Set all inputs to null, recursively killing unused Nodes
    }

    /**
     * Change a <em>def</em> into a Node.  Keeps the edges correct, by removing
     * the corresponding <em>use->def</em> edge.  This may make the original
     * <em>def</em> go dead.  This function is co-recursive with {@link #kill}.
     *     
     * @param idx which def to set
     * @param new_def the new definition
     * @return this for flow coding
     */
    Node set_def(int idx, Node new_def ) {
        Node old_def = in(idx);
        if( old_def != null ) { // If the old def exists, remove a use->def edge
            ArrayList<Node> outs = old_def._outputs;
            int lidx = outs.size()-1; // Last index
            
            // This 1-line hack compresses an element out of an ArrayList
            // without having to copy the contents.  The last element is
            // stuffed over the deleted element, and then the size is reduced.            
            outs.set(outs.indexOf(this),outs.get(lidx));
            outs.remove(lidx);  // Reduce ArrayList size without copying anything
            if( lidx == 0 )     // If we removed the last use, the old def is now dead
                old_def.kill(); // Kill old def
        }
        // Set the new_def over the old (killed) edge
        _inputs.set(idx,new_def);
        // If new def is not null, add the corresponding use->def edge
        if( new_def != null )
            new_def._outputs.add(this);
        // Return self for easy flow-coding
        return this;
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

    /**
     * This function rewrites the current Node into a more "idealized" form.
     * This is the bulk of our peephole rewrite rules, and we use this to
     * e.g. turn arbitary collections of adds and multiplies with mixed
     * constants into a normal form thats easy for hardware to implement.
     * Example: An array addressing expression:
     *    ary[idx+1]
     * might turn into Sea-of-Nodes IR:
     *    (ary+12)+((idx+1) * 4)
     * This expression can then be idealized into:
     *    ary + ((idx*4) + (12 + (1*4)))
     * And more folding:
     *    ary + ((idx<<2) + 16)
     * And during code-gen:
     *    MOV4 Rary,Ridx,16 // or some such hardware-specific notation
     *
     * idealize has a very specific calling convention:
     * - If NO change is made, return null
     * - If ANY change is made, return not-null; this can be "this"
     * - The returned Node does NOT call peephole() on itself; the peephole()
     *   call will recursively peephole it.
     * - Any NEW nodes that are not DIRECTLY returned DO call peephole().
     *
     * Examples:
     *   (x+5) ==> No change, return null
     *
     *   (5+x) ==> (x+5); self swapped arguments so return 'this';
     *
     *   ((x+1)+2) ==> (x + (1+2)) which returns 2 new Nodes.
     *   The new Node (1+2) calls peephole (which then folds into a constant).
     *   The new Node (x+3) does not call peephole, because peephole itself will call peephole.
     *
     * Since idealize calls peephole, and peephole calls idealize, you must be
     * careful that all idealizations are *monotonic*: all transforms remove
     * some feature, so that the set of available transforms always shrinks.
     * If you don't, you risk an infinite peephole loop!
     *
     * @return Either a new or changed node, or null for no changes.
     */
    public abstract Node idealize();

    /**
     * Used to allow repeating tests in the same JVM.  This just resets the
     * Node unique id generator, and is done as part of making a new Parser.
     */
    public static void reset() { UNIQUE_ID = 1; }
  
    /*
     * hashCode and equals implementation to be added in later chapter.
     */
  
}
