package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IRPrinter;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

import java.util.*;
import java.util.function.Function;

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
     * Immediate dominator tree depth, used to approximate a real IDOM during
     * parsing where we do not have the whole program, and also peepholes
     * change the CFG incrementally.
     * <p>
     * See {@link <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">...</a>}
     */
    int _idepth;
    
    /**
     * A private Global Static mutable counter, for unique node id generation.
     * To make the compiler multithreaded, this field will have to move into a TLS.
     * Starting with value 1, to avoid bugs confusing node ID 0 with uninitialized values.
     * */
    private static int UNIQUE_ID = 1;
    public static int UID() { return UNIQUE_ID; }

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
    public final String toString() {  return print(); }

    // This is a *deep* print.  This version will fail on cycles, which we will
    // correct later when we can parse programs with loops.  We print with a
    // tik-tok style; the common _print0 calls the per-Node _print1, which
    // calls back to _print0;
    public final String print() {
        return _print0(new StringBuilder(), new BitSet()).toString();
    }
    
    // This is the common print: check for repeats, check for DEAD and print
    // "DEAD" else call the per-Node print1.
    final StringBuilder _print0(StringBuilder sb, BitSet visited) {
        if (visited.get(_nid) && !(this instanceof ConstantNode) )
            return sb.append(label());
        visited.set(_nid);
        return isDead()
            ? sb.append(uniqueName()).append(":DEAD")
            : _print1(sb, visited);
    }
    // Every Node implements this; a partial-line recursive print
    abstract StringBuilder _print1(StringBuilder sb, BitSet visited);


    // Print a node on 1 line, columnar aligned, as:
    // NNID NNAME DDEF DDEF  [[  UUSE UUSE  ]]  TYPE
    // 1234 sssss 1234 1234 1234 1234 1234 1234 tttttt
    public void _printLine(StringBuilder sb ) {
        sb.append("%4d %-7.7s ".formatted(_nid,label()));
        if( _inputs==null ) {
            sb.append("DEAD\n");
            return;
        }
        for( Node def : _inputs )
            sb.append(def==null ? "____ " : "%4d ".formatted(def._nid));
        for( int i = _inputs.size(); i<3; i++ )
            sb.append("     ");
        sb.append(" [[  ");
        for( Node use : _outputs )
            sb.append(use==null ? "____ " : "%4d ".formatted(use._nid));
        int lim = 5 - Math.max(_inputs.size(),3);
        for( int i = _outputs.size(); i<lim; i++ )
            sb.append("     ");
        sb.append(" ]]  ");
        if( _type!= null ) _type._print(sb);
        sb.append("\n");
    }

    public String p(int depth) { return IRPrinter.prettyPrint(this,depth); }
    
    public boolean isMultiHead() { return false; }
    public boolean isMultiTail() { return false; }
    
    // ------------------------------------------------------------------------
    // Graph Node & Edge manipulation
    
    /**
     * Gets the ith input node
     * @param i Offset of the input node
     * @return Input node or null
     */
    public Node in(int i) { return _inputs.get(i); }
    public Node out(int i) { return _outputs.get(i); }

    public int nIns() { return _inputs.size(); }

    public int nOuts() { return _outputs.size(); }

    public boolean isUnused() { return nOuts() == 0; }

    public boolean isCFG() { return false; }
  
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
    public Node setDef(int idx, Node new_def ) {
        unlock();
        Node old_def = in(idx);
        if( old_def == new_def ) return new_def; // No change
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
  
    // Remove the numbered input, compressing the inputs in-place.  This
    // shuffles the order deterministically - which is suitable for Region and
    // Phi, but not for every Node.  If the def goes dead, it is recursively
    // killed, which may include 'this' Node.
    Node delDef(int idx) {
        unlock();
        Node old_def = in(idx);
        if( old_def != null &&  // If the old def exists, remove a def->use edge
            old_def.delUse(this) ) // If we removed the last use, the old def is now dead
            old_def.kill();     // Kill old def
        old_def.moveDepsToWorklist();
        Utils.del(_inputs, idx);
        return this;
    }

    /**
     * Add a new def to an existing Node.  Keep the edges correct by
     * adding the corresponding <em>def->use</em> edge.
     *
     * @param new_def the new definition, appended to the end of existing definitions
     * @return new_def for flow coding
     */
    Node addDef(Node new_def) {
        unlock();
        // Add use->def edge
        _inputs.add(new_def);
        // If new def is not null, add the corresponding def->use edge
        if( new_def != null )
            new_def.addUse(this);
        return new_def;
    }

    // Breaks the edge invariants, used temporarily
    protected <N extends Node> N addUse(Node n) { _outputs.add(n); return (N)this; }

    // Remove node 'use' from 'def's (i.e. our) output list, by compressing the list in-place.
    // Return true if the output list is empty afterward.
    // Error is 'use' does not exist; ok for 'use' to be null.
    protected boolean delUse( Node use ) {
        Utils.del(_outputs, Utils.find(_outputs, use));
        return _outputs.isEmpty();
    }

    // Shortcut for "popping" n nodes.  A "pop" is basically a
    // setDef(last,null) followed by lowering the nIns() count.
    void popN(int n) {
        unlock();
        for( int i=0; i<n; i++ ) {
            Node old_def = _inputs.removeLast();
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
        assert isUnused();      // Has no uses, so it is dead
        _type=null;             // Flag as dead
        while( nIns()>0 ) { // Set all inputs to null, recursively killing unused Nodes
            Node old_def = _inputs.removeLast();
            if( old_def != null ) {
                IterPeeps.add(old_def);// Revisit neighbor because removed use
                if( old_def.delUse(this) ) // If we removed the last use, the old def is now dead
                    old_def.kill();        // Kill old def
            }
        }
        assert isDead();        // Really dead now
    }

    // Mostly used for asserts and printing.
    public boolean isDead() { return isUnused() && nIns()==0 && _type==null; }

    // Shortcuts to stop DCE mid-parse
    // Add bogus null use to keep node alive
    public <N extends Node> N keep() { return addUse(null); }
    // Remove bogus null.
    public <N extends Node> N unkeep() { delUse(null); return (N)this; }


    // Replace self with nnn in the graph, making 'this' go dead
    public void subsume( Node nnn ) {
        assert nnn!=this;
        while( nOuts() > 0 ) {
            Node n = _outputs.removeLast();
            n.unlock();
            int idx = Utils.find(n._inputs, this);
            n._inputs.set(idx,nnn);
            nnn.addUse(n);
        }
        kill();
    }
    
    // ------------------------------------------------------------------------
    // Graph-based optimizations
    
    /**
     * We allow disabling peephole opt so that we can observe the
     * full graph, vs the optimized graph.
     */
    public static boolean _disablePeephole = false;

    /**
     * Try to peephole at this node and return a better replacement Node.
     * Always returns some not-null Node (often this).
     */
    public final Node peephole( ) {
        if (_disablePeephole) {
            _type = compute();
            return this;        // Peephole optimizations turned off
        }
        Node n = peepholeOpt();
        return n==null ? this : deadCodeElim(n.peephole()); // Cannot return null for no-progress
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
        ITER_CNT++;
        // Compute initial or improved Type
        Type old = setType(compute());

        // Replace constant computations from non-constants with a constant node
        if (!(this instanceof ConstantNode) && _type.isHighOrConst() )
            return new ConstantNode(_type).peepholeOpt();

        // Global Value Numbering
        if( _hash==0 ) {
            Node n = GVN.get(this); // Will set _hash as a side effect
            if( n==null )
                GVN.put(this,this);  // Put in table now
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

        if( old==_type ) ITER_NOP_CNT++;
        return old==_type ? null : this; // Report progress
    }
    public static int ITER_CNT, ITER_NOP_CNT;

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
        IterPeeps.addAll(_outputs);
        moveDepsToWorklist();
        return old;
    }

    
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
    ArrayList<Node> _deps;

    /**
     * Add a node to the list o dependencies. Only add it if its not
     * an input or output of this node, that is, it is at least one step
     * away. The node being added must benefit from this node being peepholed.
     */
    Node addDep( Node dep ) {
        // Running peepholes during the big assert cannot have side effects
        // like adding dependencies.
        if( IterPeeps.midAssert() ) return this;
        if( _deps==null ) _deps = new ArrayList<>();
        if( Utils.find(_deps  ,dep) != -1 ) return this; // Already on list
        if( Utils.find(_inputs,dep) != -1 ) return this; // No need for deps on immediate neighbors
        if( Utils.find(_outputs,dep)!= -1 ) return this;
        _deps.add(dep);
        return this;
    }

    // Move the dependents onto a worklist, and clear for future dependents.
    public void moveDepsToWorklist( ) {
        if( _deps==null ) return;
        IterPeeps.addAll(_deps);
        _deps.clear();
    }
    
    
    // Global Value Numbering.  Hash over opcode and inputs; hits in this table
    // are structurally equal.
    public static final HashMap<Node,Node> GVN = new HashMap<>();

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
    boolean eq( Node n ) { return true; }


    // Cached hash.  If zero, then not computed AND this Node is NOT in the GVN
    // table - and can have its edges hacked (which will change his hash
    // anyway).  If Non-Zero then this Node is IN the GVN table, or is being
    // probed to see if it can be inserted.  His edges are "locked", because
    // hacking his edges will change his hash.
    int _hash;

    // If the _hash is set, then the Node is in the GVN table; remove it.
    void unlock() {
        if( _hash==0 ) return;
        Node old = GVN.remove(this); // Pull from table
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
    // Peephole utilities
    
    // Swap inputs without letting either input go dead during the swap.
    Node swap12() {
        unlock();               // Hash is order dependent
        Node tmp = in(1);
        _inputs.set(1,in(2));
        _inputs.set(2,tmp);
        return this;
    }

    /**
     * Does this node contain all constants?
     * Ignores in(0), as is usually control.
     * In an input is not a constant, we add dep as
     * a dependency to it, because dep can make progress
     * if the input becomes a constant later.
     * It is sufficient for one of the non-const
     * inputs to have the dependency so we don't bother
     * checking the rest.
     */
    boolean allCons(Node dep) {
        for( int i=1; i<nIns(); i++ )
            if( !(in(i)._type.isConstant()) ) {
                in(i).addDep(dep); // If in(i) becomes a constant later, will trigger some peephole
                return false;
            }
        return true;
    }

    // Return the immediate dominator of this Node and compute dom tree depth.
    Node idom() {
        Node idom = in(0);
        if( idom._idepth==0 ) idom.idom(); // Recursively set _idepth
        if( _idepth==0 ) _idepth = idom._idepth+1;
        return idom;
    }

    // Make a shallow copy (same class) of this Node, with given inputs and
    // empty outputs and a new Node ID.  The original inputs are ignored.
    // Does not need to be implemented in isCFG() nodes.
    Node copy(Node lhs, Node rhs) { throw Utils.TODO("Binary ops need to implement copy"); }

    /**
     * Used to allow repeating tests in the same JVM.  This just resets the
     * Node unique id generator, and is done as part of making a new Parser.
     */
    public static void reset() {
        UNIQUE_ID = 1;
        _disablePeephole=false;
        GVN.clear();
        ITER_CNT = ITER_NOP_CNT = 0;
    }


    // Utility to walk the entire graph applying a function; return the first
    // not-null result.
    private static final BitSet WVISIT = new BitSet();
    final public <E> E walk( Function<Node,E> pred ) {
        assert WVISIT.isEmpty();
        E rez = _walk(pred);
        WVISIT.clear();
        return rez;
    }
    
    private <E> E _walk( Function<Node,E> pred ) {
        if( WVISIT.get(_nid) ) return null; // Been there, done that
        WVISIT.set(_nid);
        E x = pred.apply(this);
        if( x != null ) return x;
        for( Node def : _inputs  )  if( def != null && (x = def._walk(pred)) != null ) return x;
        for( Node use : _outputs )  if( use != null && (x = use._walk(pred)) != null ) return x;
        return null;
    }
    
    /**
     * Debugging utility to find a Node by index
     */
    public Node find(int nid) {
        return walk( n -> n._nid==nid ? n : null );
    }
}
