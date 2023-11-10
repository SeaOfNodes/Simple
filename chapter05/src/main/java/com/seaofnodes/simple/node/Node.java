package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
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
    
    @Override
    public final String toString() {
        // TODO: This needs a lot of work
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

    public int nOuts() { return _outputs.size(); }

    public boolean isUnused() { return nOuts() == 0; }

    public boolean isCFG() { return false; }
  
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
     * <li>we ask the Node for a better replacement.  The "better replacement"
     * is things like {@code (1+2)} becomes {@code 3} and {@code (1+(x+2))} becomes
     * {@code (x+(1+2))}.  By canonicalizing expressions we fold common addressing
     * math constants, remove algebraic identities and generally simplify the
     * code. </li>
     * </ul>
     */
    public final Node peephole( ) {
        // Compute initial or improved Type
        Type type = _type = compute();
        
        if (_disablePeephole)
            return this;        // Peephole optimizations turned off

        // Replace constant computations from non-constants with a constant node
        if (!(this instanceof ConstantNode) && type.isConstant())
            return deadCodeElim(new ConstantNode(type).peephole());

        // Future chapter: Global Value Numbering goes here
        
        // Ask each node for a better replacement
        Node n = idealize();
        if( n != null )         // Something changed
            // Recursively optimize
            return deadCodeElim(n.peephole());
        
        return this;            // No progress
    }

    // m is the new Node, self is the old.
    // Return 'm', which may have zero uses but is alive nonetheless.
    // If self has zero uses (and is not 'm'), {@link #kill} self.
    private Node deadCodeElim(Node m) {
        // If self is going dead and not being returned here (Nodes returned
        // from peephole commonly have no uses (yet)), then kill self.
        if( m != this && isUnused() ) {
            // Killing self - and since self recursively kills self's inputs we
            // might end up killing 'm', which we are returning as a live Node.
            // So we add a bogus extra null output edge to stop kill().
            m.addUse(null); // Add bogus null use to keep m alive
            kill();            // Kill self because replacing with 'm'
            m.delUse(null);    // Remove bogus null.
        }
        return m;
    }

    
    /**
     * Change a <em>def</em> into a Node.  Keeps the edges correct, by removing
     * the corresponding <em>use->def</em> edge.  This may make the original
     * <em>def</em> go dead.  This function is co-recursive with {@link #kill}.
     * <p>
     
     * This method is the normal path for altering a Node, because it does the
     * proper default edge maintenance.  It also <em>immediately</em> kills
     * Nodes that lose their last use; at times care must be taken to avoid
     * killing Nodes that are being used without having an output Node.  This
     * definitely happens in the middle of recursive {@link #peephole} calls.
     *     
     * @param idx which def to set
     * @param new_def the new definition
     * @return this for flow coding
     */
    Node set_def(int idx, Node new_def ) {
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
        return this;
    }

    /**
     * Add a new def to an existing Node.  Keep the edges correct by
     * adding the corresponding <em>def->use</em> edge.
     *
     * @param new_def the new definition, appended to the end of existing definitions
     * @return new_def for flow coding
     */
    Node add_def(Node new_def) {
        // Add use->def edge
        _inputs.add(new_def);
        // If new def is not null, add the corresponding def->use edge
        if( new_def != null )
            new_def.addUse(this);
        return new_def;
    }

    // Breaks the edge invariants, used temporarily
    private void addUse(Node n) { _outputs.add(n); }

    // Remove node 'use' from 'def's (i.e. our) output list, by compressing the list in-place.
    // Return true if the output list is empty afterward.
    // Error is 'use' does not exist; ok for 'use' to be null.
    protected boolean delUse( Node use ) {
        ArrayList<Node> outs = _outputs;
        int lidx = outs.size()-1; // Last index            
        // This 1-line hack compresses an element out of an ArrayList
        // without having to copy the contents.  The last element is
        // stuffed over the deleted element, and then the size is reduced.            
        outs.set(outs.indexOf(use),outs.get(lidx));
        outs.remove(lidx);  // Reduce ArrayList size without copying anything
        return lidx==0;
    }

    // Shortcut for "popping" n nodes.  A "pop" is basically a
    // set_def(last,null) followed by lowering the nIns() count.
    void pop_n(int n) {
        for( int i=0; i<n; i++ ) {
            Node old_def = _inputs.remove(_inputs.size()-1);
            if( old_def != null &&     // If it exists and
                old_def.delUse(this) ) // If we removed the last use, the old def is now dead
                old_def.kill();        // Kill old def
        }
    }
  
    /**
     * Kill a Node with no <em>uses</em>, by setting all of its <em>defs</em>
     * to null.  This may recursively kill more Nodes and is basically dead
     * code elimination.  This function is co-recursive with {@link #pop_n}.
     */
    public void kill( ) {
        assert isUnused();      // Has no uses, so it is dead
        pop_n(nIns());          // Set all inputs to null, recursively killing unused Nodes
        _type=null;             // Flag as dead
        assert isDead();        // Really dead now
    }

    // Mostly used for asserts and printing.
    boolean isDead() { return isUnused() && nIns()==0 && _type==null; }
  
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
     * e.g. turn arbitrary collections of adds and multiplies with mixed
     * constants into a normal form that's easy for hardware to implement.
     * Example: An array addressing expression:
     * <pre>   ary[idx+1]</pre>
     * might turn into Sea-of-Nodes IR:
     * <pre>   (ary+12)+((idx+1) * 4)</pre>
     * This expression can then be idealized into:
     * <pre>   ary + ((idx*4) + (12 + (1*4)))</pre>
     * And more folding:
     * <pre>   ary + ((idx<<2) + 16)</pre>
     * And during code-gen:
     * <pre>   MOV4 Rary,Ridx,16 // or some such hardware-specific notation </pre>
     * <p>
     * {@link #idealize} has a very specific calling convention:
     * <ul>
     * <li>If NO change is made, return {@code null}
     * <li>If ANY change is made, return not-null; this can be {@code this}
     * <li>The returned Node does NOT call {@link #peephole} on itself; the {@link #peephole} call will recursively peephole it.
     * <li>Any NEW nodes that are not directly returned DO call {@link #peephole}.
     * </ul>
     * <p>
     * Examples:
     * <table border="3">
     * <tr><th>    before       </th><th>       after     </th><th>return </th><th>comment  </th></tr>
     * <tr><td>{@code (x+5)    }</td><td>{@code   (x+5)  }</td><td>{@code null  }</td><td>No change</td></tr>
     * <tr><td>{@code (5+x)    }</td><td>{@code   (x+5)  }</td><td>{@code this  }</td><td>Swapped arguments</td></tr>
     * <tr><td>{@code ((x+1)+2)}</td><td>{@code (x+(1+2))}</td><td>{@code (x+_) }</td><td>Returns 2 new Nodes</td></tr>
     * </table>
     *
     * The last entry deserves more discussion.  The new Node {@code (1+2)}
     * created in {@link #idealize} calls {@link #peephole} (which then folds
     * into a constant).  The other new Node {@code (x+3)} does not call
     * peephole, because it is returned and peephole itself will recursively
     * call peephole.
     * <p>
     * Since idealize calls peephole and peephole calls idealize, you must be
     * careful that all idealizations are <em>monotonic</em>: all transforms remove
     * some feature, so that the set of available transforms always shrinks.
     * If you don't, you risk an infinite peephole loop!
     *
     * @return Either a new or changed node, or null for no changes.
     */
    public abstract Node idealize();


    // -----------------------
    // Peephole utilities
    
    // Swap inputs without letting either input go dead during the swap.
    Node swap12() {
        Node tmp = in(1);
        _inputs.set(1,in(2));
        _inputs.set(2,tmp);
        return this;
    }
    
    // does this node contain all constants?
    // Ignores in(0), as is usually control.
    boolean all_cons() {
        for( int i=1; i<nIns(); i++ )
            if( !(in(i)._type.isConstant()) )
                return false;
        return true;
    }

    // Make a shallow copy (same class) of this Node, with given inputs and
    // empty outputs and a new Node ID.  The original inputs are ignored.
    // Does not need to be implemented in isCFG() nodes.
    Node copy(Node lhs, Node rhs) { throw Parser.TODO("Binary ops need to implement copy"); }

    /**
     * Used to allow repeating tests in the same JVM.  This just resets the
     * Node unique id generator, and is done as part of making a new Parser.
     */
    public static void reset() { UNIQUE_ID = 1; }
}
