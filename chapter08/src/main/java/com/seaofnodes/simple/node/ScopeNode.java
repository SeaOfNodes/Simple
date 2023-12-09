package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;

import java.util.*;

/**
 * The Scope node is purely a parser helper - it tracks names to nodes with a
 * stack of scopes.
 */
public class ScopeNode extends Node {

    /**
     * The control is a name that binds to the currently active control
     * node in the graph
     */
    public static final String CTRL = "$ctrl";
    public static final String ARG0 = "arg";

    /**
     * Names for every input edge
     */
    public final Stack<HashMap<String, Integer>> _scopes;


    // A new ScopeNode
    public ScopeNode() {
        _scopes = new Stack<>();
        _type = Type.BOTTOM;
    }
    
    
    @Override public String label() { return "Scope"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("Scope[ ");
        String[] names = reverseNames();
        for( int j=0; j<nIns(); j++ ) {
            sb.append(names[j]).append(":");
            Node n = in(j);
            while( n instanceof ScopeNode loop ) {
                sb.append("Lazy_");
                n = loop.in(j);
            }
            n._print0(sb, visited).append(" ");
        }
        sb.setLength(sb.length()-1);
        return sb.append("]");
    }

    /**
     * Recover the names for all variable bindings.
     * The result is an array of names that is aligned with the
     * inputs to the Node.
     *
     * This is an expensive operation.
     */
    public String[] reverseNames() {
        String[] names = new String[nIns()];
        for( HashMap<String,Integer> syms : _scopes )
            for( String name : syms.keySet() )
                names[syms.get(name)] = name;
        return names;
    }
    
    @Override public Type compute() { return Type.BOTTOM; }

    @Override public Node idealize() { return null; }

    public void push() { _scopes.push(new HashMap<>());  }
    public void pop() { popN(_scopes.pop().size());  }

    /**
     * Create a new name in the current scope
     */
    public Node define( String name, Node n ) {
        HashMap<String,Integer> syms = _scopes.lastElement();
        if( syms.put(name,nIns()) != null )
            return null;        // Double define
        return addDef(n);
    }
    /**
     * Lookup a name in all scopes starting from most deeply nested.
     *
     * @param name Name to be looked up
     * @see #lookup(String, int)
     */
    public Node lookup( String name ) {
        var idx = loopupName(name);
        return idx < 0 ? null : lookup(name,idx);
    }
    /**
     * If the name is present in any scope, then redefine else null
     *
     * @param name Name being redefined
     * @param n    The node to bind to the name
     */
    public Node update( String name, Node n ) {
        var idx = loopupName(name);
        if (idx < 0) return null;
        lookup(name, idx);
        return setDef(idx, n);
    }

    private int loopupName( String name ) {
        for (int i=_scopes.size()-1; i>=0; i--) {
            var syms = _scopes.get(i); // Get the symbol table for nesting level
            var idx = syms.get(name);
            if (idx != null) return idx;
        }
        return -1;
    }

    /**
     * Both recursive lookup and update.
     *
     * A shared implementation allows us to create lazy phis both during
     * lookups and updates; the lazy phi creation is part of chapter 8.
     *
     * @param name  Name whose binding is being updated or looked up
     * @param idx   The index of the variable
     * @return node being looked up, or the one that was updated
     */
    private Node lookup( String name, int idx ) {
        Node old = in(idx);
        if( old instanceof ScopeNode loop ) {
            // Lazy Phi!
            old = loop.in(idx) instanceof PhiNode phi && loop.ctrl()==phi.region()
                // Loop already has a real Phi, use it
                ? loop.in(idx)
                // Set real Phi in the loop head
                // The phi takes its one input (no backedge yet) from a recursive
                // lookup, which might have insert a Phi in every loop nest.
                : loop.setDef(idx,new PhiNode(name,loop.ctrl(),loop.lookup(name,idx),null).peephole());
            setDef(idx,old);
        }
        return old; // Not lazy, so this is the answer
    }

    public Node ctrl() { return in(0); }

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

    /**
     * Duplicate a ScopeNode; including all levels, up to Nodes.  So this is
     * neither shallow (would dup the Scope but not the internal HashMap
     * tables), nor deep (would dup the Scope, the HashMap tables, but then
     * also the program Nodes).
     * <p>
     * If the {@code loop} flag is set, the edges are filled in as the original
     * Scope, as a indication of Lazy Phis at loop heads.  The goal here is to
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
        for( HashMap<String,Integer> syms : _scopes )
            dup._scopes.push(new HashMap<>(syms));

        dup.addDef(ctrl());      // Control input is just copied
        for( int i=1; i<nIns(); i++ ) {
            if ( !loop ) { dup.addDef(in(i)); }
            else if (Parser.LAZY) {
                // For lazy phi we need to set a sentinel that will
                // trigger phi creation on update
                dup.addDef(this); // Add a sentinel which is self
            }
            else {
                String[] names = reverseNames(); // Get the variable names
                // Create a phi node with second input as null - to be filled in
                // by endLoop() below
                dup.addDef(new PhiNode(names[i], ctrl(), in(i), null).peephole());
                // Ensure our node has the same phi in case we created one
                setDef(i, dup.in(i));
            }
        }
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
        String[] ns = reverseNames();
        // Note that we skip i==0, which is bound to '$ctrl'
        for (int i = 1; i < nIns(); i++) {
            if( in(i) != that.in(i) ) { // No need for redundant Phis
                // If we are in lazy phi mode we need to a lookup
                // by name as it will triger a phi creation
                Node phi;
                if (Parser.LAZY)
                    phi = new PhiNode(ns[i], r, this.lookup(ns[i], i), that.lookup(ns[i], i)).peephole();
                else
                    phi = new PhiNode(ns[i], r, in(i), that.in(i)).peephole();
                setDef(i, phi);
            }
        }
        that.kill();            // Kill merged scope
        return r.unkeep().peephole();
    }
    
    // Merge the backedge scope into this loop head scope
    // We set the second input to the phi from the back edge (i.e. loop body)
    public void endLoop(ScopeNode back, ScopeNode exit ) {
        Node ctrl = ctrl();
        assert ctrl instanceof LoopNode loop && loop.inProgress();
        ctrl.setDef(2,back.ctrl());
        for( int i=1; i<nIns(); i++ ) {
            if( back.in(i) != this ) {
                PhiNode phi = (PhiNode)in(i);
                assert phi.region()==ctrl && phi.in(2)==null;
                phi.setDef(2,back.in(i));
                // Do an eager useless-phi removal
                Node in = phi.peephole();
                if( in != phi )
                    phi.subsume(in);
            }
            if( exit.in(i) == this ) // Replace a lazy-phi on the exit path also
                exit.setDef(i,in(i));
        }
        back.kill();            // Loop backedge is dead
    }

    public void addJumpFrom(ScopeNode current) {
        ctrl().addDef(current.ctrl());
        String[] names = reverseNames();
        for (int i=1; i<nIns(); i++) {
            Node out = in(i);
            Node in = current.in(i);
            if (out != in) {
                if (Parser.LAZY) {
                    out = this.lookup(names[i], i);
                    in = current.lookup(names[i], i);
                }
                if (out instanceof PhiNode p && p.region() == ctrl()) {
                    p.addDef(in);
                } else {
                    Node[] ins = new Node[ctrl().nIns()];
                    ins[0] = ctrl();
                    for (int j=1; j<ins.length-1; j++) ins[j] = out;
                    ins[ins.length-1] = in;
                    setDef(i, new PhiNode(names[i], ins));
                }
            }
        }
    }

    public ScopeNode dupForScope(ScopeNode head) {
        ScopeNode dup = new ScopeNode();
        for( HashMap<String,Integer> syms : head._scopes )
            dup._scopes.push(new HashMap<>(syms));
        dup.addDef(ctrl());      // Control input is just copied
        for( int i=1; i<head.nIns(); i++ ) {
            dup.addDef(in(i));
        }
        return dup;
    }

    public void doPeeps() {
        for( int i=1; i<nIns(); i++ ) {
            Node n = in(i);
            if (n instanceof PhiNode phi && phi.region() == ctrl()) {
                Node in = phi.peephole();
                if( in != phi )
                    phi.subsume(in);
            }
        }
        Node ctrl = ctrl().peephole();
        if( ctrl != ctrl() )
            ctrl().subsume(ctrl);
    }

}
