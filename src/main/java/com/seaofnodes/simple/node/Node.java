package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.IRPrinter;
import com.seaofnodes.simple.print.JSViewer;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.*;
import java.util.function.Function;
import static com.seaofnodes.simple.codegen.CodeGen.CODE;

/**
 * All Nodes in the Sea of Nodes IR inherit from the Node class.
 * The Node class provides common functionality used by all subtypes.
 * Subtypes of Node specialize by overriding methods.
 */
public abstract class Node implements Cloneable {

    /**
     * Each node has a unique dense Node ID within a compilation context
     * The ID is useful for debugging, for using as an offset in a bitvector,
     * as well as for computing equality of nodes (to be implemented later).
     */
    public int _nid;

    /**
     * Inputs to the node. These are use-def references to Nodes.
     * <p>
     * Generally fixed length, ordered, nulls allowed, no unused trailing space.
     * Ordering is required because e.g. "a/b" is different from "b/a".
     * The first input (offset 0) is often a {@link CFGNode} node.
     */
    public Ary<Node> _inputs;

    /**
     * Outputs reference Nodes that are not null and have this Node as an
     * input.  These nodes are users of this node, thus these are def-use
     * references to Nodes.
     * <p>
     * Outputs directly match inputs, making a directed graph that can be
     * walked in either direction.  These outputs are typically used for
     * efficient optimizations but otherwise have no semantics meaning.
     */
    public Ary<Node> _outputs;


    /**
     * Current computed type for this Node.  This value changes as the graph
     * changes and more knowledge is gained about the program.
     */
    public Type _type;


    Node(Node... inputs) {
        _nid = CODE.getUID(); // allocate unique dense ID
        _inputs = new Ary<>(Node.class);
        Collections.addAll(_inputs,inputs);
        _outputs = new Ary<>(Node.class);
        for( Node n : _inputs )
            if( n != null )
                n.addUse( this );
    }

    // Make a Node using the existing arrays of nodes.
    // Used by any pass rewriting all Node classes but not the edges.
    Node( Node n ) {
        assert CodeGen.CODE._phase.ordinal() >= CodeGen.Phase.InstSelect.ordinal();
        _nid = CODE.getUID(); // allocate unique dense ID
        _inputs  = new Ary<>(n==null ? new Node[0] : n._inputs.asAry());
        _outputs = new Ary<>(Node.class);
        _type = n==null ? Type.BOTTOM : n._type;
        _deps = null;
        _hash = 0;
    }

    // Easy reading label for debugger, e.g. "Add" or "Region" or "EQ"
    public abstract String label();

    // Unique label for graph visualization, e.g. "Add12" or "Region30" or "EQ99"
    public String uniqueName() {
        // Get rid of $ as graphviz doesn't like it
        String label = label().replaceAll("\\$", "");
        return label + _nid;
    }

    // Graphical label, e.g. "+" or "Region" or "=="
    public String glabel() { return label(); }

    // Extra fun stuff, for assembly printing.  Jump labels, parser locations,
    // variable types, etc.
    public String comment() { return null; }

    // ------------------------------------------------------------------------

    // Debugger Printing.

    // {@code toString} is what you get in the debugger.  It has to print 1
    // line (because this is what a debugger typically displays by default) and
    // has to be robust with broken graph/nodes.
    @Override
    public final String toString() {  return print(); }

    // This is a *deep* print.  We print with a mutually recursive tik-tok
    // style; the common _print0 calls the per-Node _print1, which calls back
    // to _print0;
    public final String print() {
        return _print0(new StringBuilder(), new BitSet()).toString();
    }

    // This is the common print: check for repeats, check for DEAD and print
    // "DEAD" else call the per-Node print1.
    public final StringBuilder _print0(StringBuilder sb, BitSet visited) {
        if (visited.get(_nid) && !(this instanceof ConstantNode) )
            return sb.append(label());
        visited.set(_nid);
        return isDead()
            ? sb.append(uniqueName()).append(":DEAD")
            : _print1(sb, visited);
    }
    // Every Node implements this; a partial-line recursive print
    abstract public StringBuilder _print1(StringBuilder sb, BitSet visited);

    public String p(int depth) { return IRPrinter.prettyPrint(this,depth); }

    public boolean isConst() { return false; }

    // ------------------------------------------------------------------------
    // Graph Node & Edge manipulation

    /**
     * Gets the ith input node
     * @param i Offset of the input node
     * @return Input node or null
     */
    public Node in(int i) { return _inputs.get(i); }
    public Node out(int i) { return _outputs.get(i); }
    public final Ary<Node> outs() { return _outputs; }

    public int nIns() { return _inputs.size(); }

    public int nOuts() { return _outputs.size(); }

    public boolean isUnused() { return nOuts() == 0; }

    public CFGNode cfg0() { return (CFGNode)in(0); }

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
     * @return new_def for flow coding
     */
    public <N extends Node> N setDef(int idx, N new_def ) {
        unlock();
        Node old_def = in(idx);
        if( old_def == new_def ) return new_def; // No change
        // If new def is not null, add the corresponding def->use edge
        // This needs to happen before removing the old node's def->use edge as
        // the new_def might get killed if the old node kills it recursively.
        if( new_def != null )
            new_def.addUse(this);
        // Set the new_def over the old (killed) edge
        _inputs.set(idx,new_def);
        if( old_def != null ) {        // If the old def exists, remove a def->use edge
            if( old_def.delUse(this) ) // If we removed the last use, the old def is now dead
                old_def.kill();        // Kill old def
            else CODE.add(old_def);    // Else old lost a use, so onto worklist
        }
        moveDepsToWorklist();
        // Return new_def for easy flow-coding
        return new_def;
    }
    public <N extends Node> N setDefX(int idx, N new_def ) {
        while( nIns() <= idx ) addDef(null);
        return setDef(idx,new_def);
    }

    // Remove the numbered input, compressing the inputs in-place.  This
    // shuffles the order deterministically - which is suitable for Region and
    // Phi, but not for every Node.  If the def goes dead, it is recursively
    // killed, which may include 'this' Node.
    public Node delDef(int idx) {
        unlock();
        Node old_def = in(idx);
        _inputs.del(idx);
        if( old_def.delUse(this) ) // If we removed the last use, the old def is now dead
            old_def.kill();     // Kill old def
        old_def.moveDepsToWorklist();
        return this;
    }

    // Insert the numbered input, sliding other inputs to the right
    Node insertDef(int idx, Node new_def) {
        _inputs.add(idx,null);
        return setDef(idx,new_def);
    }


    /**
     * Add a new def to an existing Node.  Keep the edges correct by
     * adding the corresponding <em>def->use</em> edge.
     *
     * @param new_def the new definition, appended to the end of existing definitions
     * @return new_def for flow coding
     */
    public Node addDef(Node new_def) {
        unlock();
        // Add use->def edge
        _inputs.add(new_def);
        // If new def is not null, add the corresponding def->use edge
        if( new_def != null )
            new_def.addUse(this);
        return new_def;
    }

    // Breaks the edge invariants, used temporarily
    @SuppressWarnings("unchecked")
    protected <N extends Node> N addUse(Node n) { _outputs.add(n); return (N)this; }

    // Remove node 'use' from 'def's (i.e. our) output list, by compressing the list in-place.
    // Return true if the output list is empty afterward.
    // Error is 'use' does not exist; ok for 'use' to be null.
    protected boolean delUse( Node use ) {
        _outputs.del(_outputs.find(use));
        return _outputs.isEmpty();
    }

    // Shortcut for "popping" until n nodes.  A "pop" is basically a
    // setDef(last,null) followed by lowering the nIns() count.
    void popUntil(int n) {
        unlock();
        while( nIns() > n ) {
            Node old_def = _inputs.pop();
            if( old_def != null &&     // If it exists and
                old_def.delUse(this) ) // If we removed the last use, the old def is now dead
                old_def.kill();        // Kill old def
        }
    }

    /**
     * Kill a Node with no <em>uses</em>, by setting all of its <em>defs</em>
     * to null.  This may recursively kill more Nodes, and is basically dead
     * code elimination.
     */
    public void kill( ) {
        unlock();
        moveDepsToWorklist();
        assert isUnused();      // Has no uses, so it is dead
        _type=null;             // Flag as dead
        while( nIns()>0 ) { // Set all inputs to null, recursively killing unused Nodes
            Node old_def = _inputs.removeLast();
            // Revisit neighbor because removed use
            if( old_def != null && CODE.add(old_def).delUse(this) )
                old_def.kill(); // If we removed the last use, the old def is now dead
        }
        assert isDead();        // Really dead now
    }

    // Preserve CFG use-ordering when killing
    public void killOrdered() {
        CFGNode cfg = cfg0();
        cfg._outputs.remove(cfg._outputs.find(this));
        _inputs.set(0,null);
        kill();
    }


    // Mostly used for asserts and printing.
    public boolean isDead() { return isUnused() && nIns()==0 && _type==null; }

    // Shortcuts to stop DCE mid-parse
    // Add bogus null use to keep node alive
    public <N extends Node> N keep() { return addUse(null); }
    // Remove bogus null.
    @SuppressWarnings("unchecked")
    public <N extends Node> N unkeep() {
        delUse(null);
        return (N)this;
    }
    // Test "keep" status
    public boolean iskeep() { return _outputs.find(null) != -1; }
    public void unkill() {
        if( unkeep().isUnused() )
            kill();
    }


    // Replace self with nnn in the graph, making 'this' go dead
    public void subsume( Node nnn ) {
        assert nnn!=this;
        while( nOuts() > 0 ) {
            Node n = _outputs.removeLast();
            n.unlock();
            int idx = n._inputs.find(this);
            n._inputs.set(idx,nnn);
            nnn.addUse(n);
            CODE.addAll(n._outputs);
        }
        kill();
    }

    // insert `this` immediately after `def` in the same basic block.
    public void insertAfter( Node def ) {
        CFGNode cfg = def.cfg0();
        int i = cfg._outputs.find(def)+1;
        if( cfg instanceof CallEndNode ) {
            cfg = cfg.uctrl();  i=0;
        } else if( def.in(0) instanceof MultiNode ) {
            assert i==0;
            i = cfg._outputs.find(def.in(0))+1;
        }

        while( cfg.out(i) instanceof PhiNode || cfg.out(i) instanceof CalleeSaveNode )  i++;
        cfg._outputs.insert(this,i);
        _inputs.set(0,cfg);
    }

    // Insert this in front of use.in(uidx) with this, and insert this
    // immediately before use in the basic block.
    public void insertBefore( Node use, int uidx ) {
        CFGNode cfg = use.cfg0();
        int i;
        if( use instanceof PhiNode phi ) {
            cfg = phi.region().cfg(uidx);
            if( cfg instanceof CProjNode && cfg.in(0) instanceof NeverNode nvr )
                cfg = nvr.cfg0();
            i = cfg.nOuts()-1;
        } else {
            i = cfg._outputs.find(use);
        }
        cfg._outputs.insert(this,i);
        _inputs.set(0,cfg);
        if( _inputs._len > 1 && this instanceof SplitNode )
            setDefOrdered(1,use.in(uidx));
        use.setDefOrdered(uidx,this);
    }

    public void setDefOrdered( int idx, Node def) {
        // If old is dying, remove from CFG ordered
        Node old = in(idx);
        if( old!=null && old.nOuts()==1 ) {
            CFGNode cfg = old.cfg0();
            if( cfg!=null ) {
                cfg._outputs.remove(cfg._outputs.find(old));
                old._inputs.set(0,null);
            }
        }
        setDef(idx,def);
    }

    public void removeSplit() {
        CFGNode cfg = cfg0();
        cfg._outputs.remove(cfg._outputs.find(this));
        _inputs.set(0,null);
        if( _inputs._len > 1 ) subsume(in(1));
    }

    // ------------------------------------------------------------------------
    // Graph-based optimizations

    /**
     * Try to peephole at this node and return a better replacement Node.
     * Always returns some not-null Node (often this).
     */
    public final Node peephole( ) {
        if( _type==null )       // Brand-new node, never peeped before
            JSViewer.show();
        Node n = peepholeOpt();
        if( n!=null )           // Made progress?
            JSViewer.show();    // Show again
        return n==null ? this : deadCodeElim(n._nid >= _nid ? n.peephole() : n); // Cannot return null for no-progress
    }

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
     *
     * Unlike peephole above, this explicitly returns null for no-change, or not-null
     * for a better replacement (which can be this).
     * </ul>
     */
    public final Node peepholeOpt( ) {
        CODE.iterCnt();
        // Compute initial or improved Type
        Type old = setType(compute());

        // Replace constant computations from non-constants with a constant
        // node.  If peeps are disabled, still allow high Phis to collapse;
        // they typically come from dead Regions, and we want the Region to
        // collapse, which requires the Phis to die first.
        if( _type.isHighOrConst() && !isConst() )
            return ConstantNode.make(_type).peephole();

        // Global Value Numbering
        if( _hash==0 ) {
            Node n = CODE._gvn.get(this); // Will set _hash as a side effect
            if( n==null )
                CODE._gvn.put(this,this);  // Put in table now
            else {
                // Because of random worklist ordering, the two equal nodes
                // might have different types.  Because of monotonicity, both
                // types are valid.  To preserve monotonicity, the resulting
                // shared Node has to have the best of both types.
                n.setType(n._type.join(_type));
                _hash = 0; // Clear, since it never went in the table
                return deadCodeElim(n);// Return previous; does Common Subexpression Elimination
            }
        }

        // Ask each node for a better replacement
        Node n = idealize();
        if( n != null )         // Something changed
            return n;           // Report progress

        if( old!=_type ) return this; // Report progress;
        CODE.iterNop();
        return null;            // No progress
    }

    // m is the new Node, self is the old.
    // Return 'm', which may have zero uses but is alive nonetheless.
    // If self has zero uses (and is not 'm'), {@link #kill} self.
    private Node deadCodeElim(Node m) {
        // If self is going dead and not being returned here (Nodes returned
        // from peephole commonly have no uses (yet)), then kill self.
        if( m != this && isUnused() && !isDead() ) {
            // Killing self - and since self recursively kills self's inputs we
            // might end up killing 'm', which we are returning as a live Node.
            // So we add a bogus extra null output edge to stop kill().
            m.keep();      // Keep m alive
            kill();        // Kill self because replacing with 'm'
            m.unkeep();    // Okay to peephole m
        }
        return m;
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
     * {@link #compute()} needs to be stand-alone, and cannot recursively call compute
     * on its inputs, because programs are cyclic (have loops!) and this will just
     * infinitely recurse until stack overflow.  Instead, compute typically
     * computes a new type from the {@link #_type} field of its inputs.
     */
    public abstract Type compute();

    // Set the type.  Assert monotonic progress.
    // If changing, add users to worklist.
    public Type setType(Type type) {
        Type old = _type;
        assert old==null || type.isa(old); // Since _type not set, can just re-run this in assert in the debugger
        if( old == type ) return old;
        _type = type;       // Set _type late for easier assert debugging
        CODE.addAll(_outputs);
        moveDepsToWorklist();
        return old;
    }

    @SuppressWarnings("unchecked")
    public <N extends Node> N init() { _type = compute(); return (N)this; }

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
     * <pre>   MOV4 Rary,[Ridx<<2 +16] // or some such hardware-specific notation </pre>
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
     * careful that all idealizations are <em>monotonic</em>: all transforms
     * remove some feature, so that the set of available transforms always
     * shrinks.  If you don't, you risk an infinite peephole loop!
     *
     * @return Either a new or changed node, or null for no changes.
     */
    public abstract Node idealize();


    // Some of the peephole rules get complex, and search further afield than
    // just the nearest neighbor.  These peepholes can fail the pattern match
    // on a node some distance away, and if that node ever changes we should
    // retry the peephole.  Track a set of Nodes dependent on `this`, and
    // revisit them if `this` changes.
    Ary<Node> _deps;

    /**
     * Add a node to the list of dependencies.  Only add it if its not an input
     * or output of this node, that is, it is at least one step away.  The node
     * being added must benefit from this node being peepholed.
     */
    <N extends Node> N addDep( N dep ) {
        // Running peepholes during the big assert cannot have side effects
        // like adding dependencies.
        if( CODE._midAssert ) return dep;
        if( dep._deps==null ) dep._deps = new Ary<>(Node.class);
        if( dep._deps   .find(this) != -1 ) return dep; // Already on list
        if( dep._inputs .find(this) != -1 ) return dep; // No need for deps on immediate neighbors
        if( dep._outputs.find(this) != -1 ) return dep;
        dep._deps.add(this);
        return dep;
    }

    // Move the dependents onto a worklist, and clear for future dependents.
    public void moveDepsToWorklist( ) {
        if( _deps==null ) return;
        CODE.addAll(_deps);
        _deps.clear();
    }


    // Two nodes are equal if they have the same inputs and the same "opcode"
    // which means the same Java class, plus same internal parts.
    @Override public final boolean equals( Object o ) {
        if( o==this ) return true;
        if( o.getClass() != getClass() ) return false;
        Node n = (Node)o;
        int len = _inputs.size();
        if( len != n._inputs.size() ) return false;
        for( int i=0; i<len; i++ )
            if( in(i) != n.in(i) )
                return false;
        return eq(n);
    }
    // Subclasses add extra checks (such as ConstantNodes have same constant),
    // and can assume "this!=n" and has the same Java class.
    public boolean eq( Node n ) { return true; }


    // Cached hash.  If zero, then not computed AND this Node is NOT in the GVN
    // table - and can have its edges hacked (which will change his hash
    // anyway).  If Non-Zero then this Node is IN the GVN table, or is being
    // probed to see if it can be inserted.  His edges are "locked", because
    // hacking his edges will change his hash.
    int _hash;

    // If the _hash is set, then the Node is in the GVN table; remove it.
    void unlock() {
        if( _hash==0 ) return;
        Node old = CODE._gvn.remove(this); // Pull from table
        assert old==this;
        _hash=0;                // Out of table now
    }


    // Hash of opcode and inputs
    @Override public final int hashCode() {
        if( _hash != 0 ) return _hash;
        int hash = hash();
        for( Node n : _inputs )
            if( n != null )
                hash = hash ^ (hash<<17) ^ (hash>>13) ^ n._nid;
        if( hash==0 ) hash = 0xDEADBEEF; // Bad hash, so use some junky thing
        return (_hash=hash);
    }
    // Subclasses add extra hash info (such as ConstantNodes constant)
    int hash() { return 0; }

    // ------------------------------------------------------------------------
    //

    /** Is this Node Memory related */
    public boolean isMem() { return false; }

    /** Pinned in the schedule; these are data nodes whose input#0 is not allowed to change */
    public boolean isPinned() { return false; }

    // Semantic change to the graph (so NOT a peephole), used by the Parser.
    // If any input is a float, flip to a float-flavored opcode and widen any
    // non-float input.
    public final Node widen() {
        if( !hasFloatInput() ) return this;
        Node flt = copyF();
        if( flt==null ) return this;
        for( int i=1; i<nIns(); i++ )
            flt.setDef(i, in(i)._type instanceof TypeFloat ? in(i) : new ToFloatNode(in(i)).peephole());
        kill();
        return flt;
    }
    private boolean hasFloatInput() {
        for( int i=1; i<nIns(); i++ )
            if( in(i)._type instanceof TypeFloat )
                return true;
        return false;
    }
    Node copyF() { return null; }


    // ------------------------------------------------------------------------
    // Peephole utilities

    // Swap inputs without letting either input go dead during the swap.
    public Node swap12() {
        unlock();               // Hash is order dependent
        _inputs.swap(1,2);
        return this;
    }

    /**
     * Does this node contain all constants?  Ignores in(0), as is usually
     * control.  In an input is not a constant, we add dep as a dependency to
     * it because dep can make progress if the input becomes a constant later.
     * It is sufficient for one of the non-const inputs to have the dependency,
     * so we don't bother checking the rest.
     */
    boolean allCons(Node dep) {
        for( int i=1; i<nIns(); i++ )
            if( !(in(i)._type.isConstant()) ) {
                dep.addDep(in(i)); // If in(i) becomes a constant later, will trigger some peephole
                return false;
            }
        return true;
    }


    // Make a shallow copy (same class) of this Node, with given inputs and
    // empty outputs and a new Node ID.  The original inputs are ignored.
    // Does not need to be implemented in isCFG() nodes.
    Node copy(Node lhs, Node rhs) { throw Utils.TODO("Binary ops need to implement copy"); }

    public Node copy() {
        Node n;
        try { n = (Node)clone(); }
        catch( Exception e ) { throw new RuntimeException(e); }
        n._nid = CODE.getUID(); // allocate unique dense ID
        n._inputs  = new Ary<>(Node.class);
        n._outputs = new Ary<>(Node.class);
        n._deps = null;
        n._hash = 0;
        return n;
    }

    // Report any post-optimize errors
    public Parser.ParseException err() { return null; }

    // Common integer constants
    public static ConstantNode con(long x) { return (ConstantNode)(new ConstantNode(TypeInteger.constant(x)).peephole()); }

    // Utility to walk the entire graph applying a function; return the first
    // not-null result.
    final public <E> E walk( Function<Node,E> pred ) {
        assert CODE._visit.isEmpty();
        E rez = _walk(pred);
        CODE._visit.clear();
        return rez;
    }

    private <E> E _walk( Function<Node,E> pred ) {
        if( CODE._visit.get(_nid) ) return null; // Been there, done that
        CODE._visit.set(_nid);
        E x = pred.apply(this);
        if( x != null ) return x;
        for( Node def : _inputs  )  if( def != null && (x = def._walk(pred)) != null ) return x;
        for( Node use : _outputs )  if( use != null && (x = use._walk(pred)) != null ) return x;
        return null;
    }

    /**
     * Debugging utility to find a Node by index
     */
    public Node find(int nid) { return _find(nid, new BitSet()); }
    private Node _find(int nid, BitSet bs) {
        if( bs.get(_nid) ) return null; // Been there, done that
        bs.set(_nid);
        if( _nid==nid ) return this;
        Node x;
        for( Node def : _inputs  )  if( def != null && (x = def._find(nid,bs)) != null ) return x;
        for( Node use : _outputs )  if( use != null && (x = use._find(nid,bs)) != null ) return x;
        return null;
    }
}
