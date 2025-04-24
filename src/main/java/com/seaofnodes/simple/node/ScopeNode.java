package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import java.util.*;

/**
 * The Scope node is purely a parser helper - it tracks names to nodes with a
 * stack of variables.
 */
public class ScopeNode extends MemMergeNode {

    /**
     * The control is a name that binds to the currently active control
     * node in the graph
     */
    public static final String CTRL = "$ctrl";
    public static final String ARG0 = "arg";
    public static final String MEM0 = "$mem";

    // All active/live variables in all nested scopes, all run together
    public final Ary<Var> _vars;

    // Size of each nested lexical scope
    public final Ary<Integer> _lexSize;

    // Lexical scope is one of normal Block, constructor or function
    public enum Kind { Block, Constructor, Function };
    public final Ary<Kind> _kinds;

    // Extra guards; tested predicates and casted results
    private final Ary<Node> _guards;

    // A new ScopeNode
    public ScopeNode() {
        super(true);
        _vars   = new Ary<>(Var    .class);
        _lexSize= new Ary<>(Integer.class);
        _kinds  = new Ary<>(Kind   .class);
        _guards = new Ary<>(Node   .class);
    }

    @Override public String label() { return "Scope"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("Scope[ ");
        int j=1;
        for( int i=0; i<nIns(); i++ ) {
            if( j < _lexSize._len && i == _lexSize.at(j) ) { sb.append("| "); j++; }
            Var v = _vars.get(i);
            sb.append(v.type().print(new SB()));
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
    public MemMergeNode mem() { return (MemMergeNode)in(1); }

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
    public <N extends Node> N ctrl(N n) { return setDef(0,n); }
    public Node mem(Node n) { return setDef(1,n); }

    public void push(Kind kind) {
        assert _lexSize._len==_kinds._len;
        _lexSize.push(_vars.size());
        _kinds  .push(kind);
    }

    // Pop a lexical scope
    public void pop() {
        assert _lexSize._len==_kinds._len;
        promote();
        int n = _lexSize.pop();
        _kinds.pop();
        popUntil(n);
        _vars.setLen(n);
    }


    // Look for forward references in the last lexical scope and promote to the
    // next outer lexical scope.  At the last scope declare them an error.
    public void promote() {
        int n = _lexSize.last();
        for( int i=n; i<nIns(); i++ ) {
            Var v = _vars.at(i);
            if( !v.isFRef() ) continue;
            if( _lexSize._len==1 )
                throw Parser.error("Undefined name '" + v._name + "'",v._loc);
            _vars.swap(n,i);
            _inputs.swap(n,i);
            v._idx = n;
            n++;
            _lexSize.set(_lexSize._len-1,n);
        }
    }


    public boolean inCon() { return _kinds.last() == Kind.Constructor; }

    // Is v outside any current function scope?
    public boolean outOfFunction( Var v ) {
        for( int i=_lexSize._len-1; i>=0 && v._idx<_lexSize.at(i); i-- )
            if( _kinds.at(i)==Kind.Function )
                return true;
        return false;
    }


    // Find name in reverse, return an index into _vars or -1.  Linear scan
    // instead of hashtable, but probably doesn't matter until the scan
    // typically hits many dozens of variables.
    int find( String name ) {
        for( int i=_vars.size()-1; i>=0; i-- )
            if( _vars.at(i)._name.equals(name) )
                return i;
        return -1;
    }

    /**
     * Create a new variable name in the current scope
     */
    public boolean define( String name, Type declaredType, boolean xfinal, Node init, Parser.Lexer loc ) {
        assert _lexSize.isEmpty() || name.charAt(0)!='$' ; // Later scopes do not define memory
        if( _lexSize._len > 0 )
            for( int i=_vars.size()-1; i>=_lexSize.last(); i-- ) {
                Var n = _vars.at(i);
                if( n._name.equals(name) ) {
                    if( !n.isFRef() ) return false;       // Double define
                    FRefNode fref = (FRefNode)in(n._idx); // Get forward ref
                    if( !xfinal || !declaredType.isConstant() ) throw fref.err();  // Must be a final constant
                    n.defFRef(declaredType,xfinal,loc);   // Declare full correct type, final, source location
                    setDef(n._idx,fref.addDef(init));     // Set FRef to defined; tell parser also
                }
            }
        Var v = new Var(nIns(),name,declaredType,xfinal,loc,init==Parser.XCTRL);
        _vars.add(v);
        // Creating a forward reference
        if( init==Parser.XCTRL )
            init = new FRefNode(v).init();
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

    public Var update( Var v, Node st ) {
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
                : loop.setDef(v._idx,new PhiNode(v._name, v.lazyGLB(), loop.ctrl(), loop.in(loop.update(v,null)._idx),null).peephole());
            setDef(v._idx,old);
        }
        assert !v._final || st==null;
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
        dup._vars   .addAll(_vars   );
        dup._lexSize.addAll(_lexSize);
        dup._kinds  .addAll(_kinds  );
        dup._guards .addAll(_guards );
        // The dup'd guards all need dup'd keepers, to keep proper accounting
        // when later removing all guards
        for( Node n : _guards )
            if( !(n instanceof CFGNode) )
                n.keep();
        dup.addDef(ctrl());     // Control input is just copied

        // Memory input is a shallow copy
        MemMergeNode memdup = new MemMergeNode(true), mem = mem();
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
            dup.addDef(loop && !_vars.at(i)._final ? this : in(i));
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
    public RegionNode mergeScopes(ScopeNode that, Parser.Lexer loc) {
        RegionNode r = ctrl(new RegionNode(loc,null,ctrl(), that.ctrl()).keep());
        mem()._merge(that.mem(),r);
        this ._merge(that      ,r);
        that.kill();            // Kill merged scope
        CodeGen.CODE.add(r);
        return r.unkeep();
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

    // Balance arms of an IF.  Extra lonely defs are thrown: "if(pred) int x;".
    // Forward refs are copied to the other side, "as if" they were there all along.
    public void balanceIf( ScopeNode scope ) {
        for( int i = nIns(); i < scope.nIns(); i++ ) {
            Var n = scope._vars.at(i);
            if( n.isFRef() ) {  // RHS has forward refs
                _vars.add(n);   // Copy to LHS
                addDef(scope.in(i));
            } else
                throw Parser.error("Cannot define a '"+n._name+"' on one arm of an if",n._loc);
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
            if( _vars.at(i)._final ) continue; // Final vars did not get modified in the loop
            if( _vars.at(i).type().isHighOrConst() ) continue; // Cannot lift higher than a constant, so no Phi
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
    public void addGuards( Node ctrl, Node pred, boolean invert ) {
        assert ctrl instanceof CFGNode;
        _guards.add(ctrl);      // Marker to distinguish 0,1,2 guards
        // add pred & its cast to the normal input list, with special Vars
        if( pred==null || pred.isDead() )
            return;           // Dead, do not add any guards
        // Invert the If conditional
        if( invert )
            pred = pred instanceof NotNode not ? not.in(1) : CodeGen.CODE.add(new NotNode(pred).peephole());
        // This is a zero/null test.
        // Compute the positive test type.
        Type tnz = pred._type.nonZero();
        if( tnz!=null )
            _addGuard(tnz,ctrl,pred);

        // Compute the negative test type.
        if( pred instanceof NotNode not ) {
            Node npred = not.in(1);
            Type tzero = npred._type.makeZero();
            _addGuard(tzero,ctrl,npred);
        }
    }

    private void _addGuard(Type guard, Node ctrl, Node pred) {
        Type tcast = guard.join(pred._type);
        if( tcast != pred._type && !tcast.isHigh() ) {
            Node cast = new CastNode(tcast,ctrl,pred.keep()).peephole().keep();
            _guards.add(pred);
            _guards.add(cast);
            replace(pred,cast);
        }
    }


    // Remove matching pred/cast pairs from this guarded region.
    public ScopeNode removeGuards( Node ctrl ) {
        assert ctrl instanceof CFGNode;
        // 0,1 or 2 guards
        while( true ) {
            Node g = _guards.pop();
            if( g == ctrl ) break;
            if( g instanceof CFGNode ) continue;
            g            .unkill(); // Pop/kill cast
            _guards.pop().unkill(); // Pop/kill pred
        }
        return this;
    }

    // If we find a guarded instance of pred, replace with the upcasted version
    public Node upcastGuard( Node pred ) {
        // If finding an instanceof pred, replace with cast.
        // Otherwise, just pred itself.
        int i = _guards._len;
        while( i > 0 ) {
            Node  cast = _guards.at(--i);
            if( cast instanceof CFGNode ) continue; // Marker between guard sets
            Node xpred = _guards.at(--i);
            if( xpred == pred )
                return cast;
        }
        return pred;
    }

    // Kill guards also
    @Override public void kill() {
        for( Node n : _guards )
            if( !(n instanceof CFGNode) )
                n.unkill();
        _guards.clear();
        // Can have lazy uses remaining
        if( isUnused() )
            super.kill();
    }


    private void replace( Node old, Node cast ) {
        assert old!=null && old!=cast;
        for( int i=0; i<nIns(); i++ )
            if( in(i)==old )
                setDef(i,cast);
    }

}
