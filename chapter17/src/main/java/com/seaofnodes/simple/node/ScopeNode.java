package com.seaofnodes.simple.node;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.type.*;
import java.util.*;

import static com.seaofnodes.simple.Utils.TODO;

/**
 * The Scope node is purely a parser helper - it tracks names to nodes with a
 * stack of hashmaps.
 */
public class ScopeNode extends ScopeMinNode {

    /**
     * The control is a name that binds to the currently active control
     * node in the graph
     */
    public static final String CTRL = "$ctrl";
    public static final String ARG0 = "arg";
    public static final String MEM0 = "$mem";

    // All active/live variables in all nested scopes, all run together
    public final Ary<Var> _vars;

    // Since of each nested lexical scope
    public final Ary<Integer> _lexSize;


    // A new ScopeNode
    public ScopeNode() { _vars = new Ary<>(Var.class); _lexSize = new Ary<>(Integer.class); }

    @Override public String label() { return "Scope"; }


    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("Scope[ ");
        int j=1;
        for( int i=0; i<nIns(); i++ ) {
            if( j < _lexSize._len && i == _lexSize.at(j) ) { sb.append("| "); j++; }
            Var v = _vars.get(i);
            v.type().print(sb);
            sb.append(" ");
            if( v._final ) sb.append("!");
            sb.append(v._name);
            sb.append("=");
            Node n = in(i);
            while( n instanceof ScopeNode loop ) {
                sb.append("Lazy_");
                n = loop.in(i);
            }
            if( n==null ) sb.append("___");
            else n._print0(sb, visited);
            sb.append(", ");
        }
        sb.setLength(sb.length()-2);
        return sb.append("]");
    }


    public Node ctrl() { return in(0); }
    public ScopeMinNode mem() { return (ScopeMinNode)in(1); }

    /**
     * The ctrl of a ScopeNode is always bound to the currently active
     * control node in the graph, via a special name '$ctrl' that is not
     * a valid identifier in the language grammar and hence cannot be
     * referenced in Simple code.
     *
     * @param n The node to be bound to '$ctrl'
     *
     * @return Node that was bound
     */
    public Node ctrl(Node n) { return setDef(0,n); }

    public void push() {
        _lexSize.push(_vars.size());
    }
    public void pop() {
        int n = _lexSize.pop();
        popUntil(n);
        _vars.setLen(n);
    }

    // Find name in reverse, return an index into _vars or -1.  Linear scan
    // instead of hashtable, but probably doesn't matter until the scan
    // typically hits many dozens of variables.
    int find( String name ) {
        for( int i=_vars.size()-1; i>=0; i-- )
            if( _vars.get(i)._name.equals(name) )
                return i;
        return -1;
    }

    /**
     * Create a new variable name in the current scope
     */
    public boolean define( String name, Type declaredType, boolean xfinal, Node init ) {
        assert name.charAt(0)!='$' || _lexSize.size()==1; // Later scopes do not define memory
        if( _lexSize._len > 1 )
            for( int i=_vars.size()-1; i>=_lexSize.last(); i-- )
                if( _vars.get(i)._name.equals(name) )
                    return false;   // Double define
        _vars.add(new Var(nIns(),name,declaredType,xfinal));
        addDef(init);
        return true;
    }

    // Read from memory
    public Node mem( int alias ) { return mem()._mem(alias,null); }
    // Write to memory
    public void mem( int alias, Node st ) { mem()._mem(alias,st); }


    /**
     * Lookup a name in all scopes starting from most deeply nested.
     *
     * @param name Name to be looked up
     * @return null if not found, or the implementing Node
     */
    public Var lookup( String name ) {
        int idx = find(name);
        // -1 is missed in all scopes, not found
        return idx == -1 ? null : update(_vars.at(idx),null);
    }

    /**
     * If the name is present in any scope, then redefine else null
     *
     * @param name Name being redefined
     * @param n    The node to bind to the name
     */
    public void update( String name, Node n ) {
        int idx = find(name);
        assert idx>=0;
        update(_vars.at(idx),n);
    }

    public Var update( ScopeNode.Var v, Node st ) {
        Node old = in(v._idx);
        if( old instanceof ScopeNode loop ) {
            // Lazy Phi!
            Node def = loop.in(v._idx);
            old = def instanceof PhiNode phi && loop.ctrl()==phi.region()
                // Loop already has a real Phi, use it
                ? def
                // Set real Phi in the loop head
                // The phi takes its one input (no backedge yet) from a recursive
                // lookup, which might have insert a Phi in every loop nest.
                : loop.setDef(v._idx,new PhiNode(v._name, v.glb(), loop.ctrl(), loop.in(loop.update(v,null)._idx),null).peephole());
            setDef(v._idx,old);
        }
        if( st!=null ) setDef(v._idx,st); // Set new value
        return v;
    }

    /**
     * Duplicate a ScopeNode; including all levels, up to Nodes.  So this is
     * neither shallow (would dup the Scope but not the internal HashMap
     * tables), nor deep (would dup the Scope, the HashMap tables, but then
     * also the program Nodes).
     * <p>
     * If the {@code loop} flag is set, the edges are filled in as the original
     * Scope, as an indication of Lazy Phis at loop heads.  The goal here is to
     * not make Phis at loop heads for variables which are never touched in the
     * loop body.
     * <p>
     * The new Scope is a full-fledged Node with proper use<->def edges.
     */
    public ScopeNode dup() { return dup(false); }
    public ScopeNode dup(boolean loop) {
        ScopeNode dup = new ScopeNode();
        // Our goals are:
        // 1) duplicate the name bindings of the ScopeNode across all stack levels
        // 2) Make the new ScopeNode a user of all the nodes bound
        // 3) Ensure that the order of defs is the same to allow easy merging
        dup._vars.addAll(_vars);
        dup._lexSize.addAll(_lexSize);
        dup.addDef(ctrl());     // Control input is just copied

        // Memory input is a shallow copy
        ScopeMinNode memdup = new ScopeMinNode(), mem = mem();
        memdup.addDef(null);
        memdup.addDef(loop ? this : mem.in(1));
        for( int i=2; i<mem.nIns(); i++ )
            // For lazy phis on loops we use a sentinel
            // that will trigger phi creation on update
            memdup.addDef(loop ? this : mem.in(i));
        dup.addDef(memdup);

        // Copy of other inputs
        for( int i=2; i<nIns(); i++ )
            // For lazy phis on loops we use a sentinel
            // that will trigger phi creation on update
            dup.addDef(loop ? this : in(i));
        return dup;
    }

    /**
     * Merges the names whose node bindings differ, by creating Phi node for such names
     * The names could occur at all stack levels, but a given name can only differ in the
     * innermost stack level where the name is bound.
     *
     * @param that The ScopeNode to be merged into this
     * @return A new node representing the merge point
     */
    public Node mergeScopes(ScopeNode that) {
        RegionNode r = (RegionNode) ctrl(new RegionNode(null,ctrl(), that.ctrl()).keep());
        mem()._merge(that.mem(),r);
        this ._merge(that      ,r);
        that.kill();            // Kill merged scope
        IterPeeps.add(r);
        return r.unkeep().peephole();
    }

    private void _merge(ScopeNode that, RegionNode r) {
        for( int i = 2; i < nIns(); i++)
            if( in(i) != that.in(i) ) { // No need for redundant Phis
                // If we are in lazy phi mode we need to a lookup
                // by name as it will trigger a phi creation
                Var v = _vars.at(i);
                Node lhs = this.in(this.update(v,null));
                Node rhs = that.in(that.update(v,null));
                setDef(i, new PhiNode(v._name, v.type(), r, lhs, rhs).peephole());
            }
    }

    // peephole the backedge scope into this loop head scope
    // We set the second input to the phi from the back edge (i.e. loop body)
    public void endLoop(ScopeNode back, ScopeNode exit ) {
        Node ctrl = ctrl();
        assert ctrl instanceof LoopNode loop && loop.inProgress();
        ctrl.setDef(2,back.ctrl());

        mem()._endLoopMem( this, back.mem(), exit.mem() );
        this ._endLoop   ( this, back      , exit       );
        back.kill();            // Loop backedge is dead
        // Now one-time do a useless-phi removal
        mem()._useless();
        this ._useless();

        // The exit mem's lazy default value had been the loop top,
        // now it goes back to predating the loop.
        exit.mem().setDef(1,mem().in(1));
    }

    // Fill in the backedge of any inserted Phis
    void _endLoop( ScopeNode scope, Node back, Node exit ) {
        for( int i=2; i<nIns(); i++ ) {
            if( back.in(i) != scope ) {
                PhiNode phi = (PhiNode)in(i);
                assert phi.region()==scope.ctrl() && phi.in(2)==null;
                phi.setDef(2,back.in(i)); // Fill backedge
            }
            if( exit.in(i) == scope ) // Replace a lazy-phi on the exit path also
                exit.setDef(i,in(i));
        }
    }


    // Up-casting: using the results of an If to improve a value.
    // E.g. "if( ptr ) ptr.field;" is legal because ptr is known not-null.

    // This Scope looks for direct variable uses, or certain simple
    // combinations, and replaces the variable with the upcast variant.
    public Node upcast( Node ctrl, Node pred, boolean invert ) {
        if( ctrl._type == Type.XCONTROL || pred.isDead() )
            return null; // Dead, do not do anything
        // Invert the If conditional
        if( invert )
            pred = pred instanceof NotNode not ? not.in(1) : IterPeeps.add(new NotNode(pred).peephole());

        // Direct use of a value as predicate.  This is a zero/null test.
        if( _inputs.find(pred) != -1 ) {
            if( !(pred._type instanceof TypeMemPtr tmp) )
                // Must be an `int`, since int and ptr are the only two value types
                // being tested. No representation for a generic not-null int, so no upcast.
                return null;
            if( tmp.isa(TypeMemPtr.VOIDPTR) )
                return null;    // Already not-null, no reason to upcast
            // Upcast the ptr to not-null ptr, and replace in scope
            return replace(pred,new CastNode(TypeMemPtr.VOIDPTR,ctrl,pred).peephole());
        }

        if( pred instanceof NotNode not ) {
            // Direct use of a !value as predicate.  This is a zero/null test.
            if( _inputs.find(not.in(1)) != -1 ) {
                Type tinit = not.in(1)._type.makeInit();
                if( not.in(1)._type.isa(tinit) ) return null; // Already zero/null, no reason to upcast
                return replace(not.in(1), new ConstantNode(tinit).peephole());
            }
        }
        // Apr/9/2024: Attempted to replace X with Y if guarded by a test of
        // X==Y.  This didn't seem to help very much, or at least in the test
        // cases seen so far was a very minor help.

        // No upcast
        return null;
    }

    private Node replace( Node old, Node cast ) {
        assert old!=null && old!=cast;
        for( int i=0; i<nIns(); i++ )
            if( in(i)==old )
                setDef(i,cast);
        return cast;
    }

}
