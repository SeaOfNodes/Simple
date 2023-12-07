package com.seaofnodes.simple.node;

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
    StringBuilder _print1(StringBuilder sb) {
        sb.append(label());
        for( HashMap<String,Integer> scope : _scopes ) {
            sb.append("[");
            boolean first=true;
            for( String name : scope.keySet() ) {
                if( !first ) sb.append(", ");
                first=false;
                sb.append(name).append(":");
                Node n = in(scope.get(name));
                if( n==null ) sb.append("null");
                else n._print0(sb);
            }
            sb.append("]");
        }
        return sb;
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
     * @see #update(String, Node, int)
     */
    public Node lookup( String name ) { return update(name,null,_scopes.size()-1);  }
    /**
     * If the name is present in any scope, then redefine else null
     *
     * @param name Name being redefined
     * @param n    The node to bind to the name
     */
    public Node update( String name, Node n ) { return update(name,n,_scopes.size()-1); }
    /**
     * Both recursive lookup and update.
     *
     * A shared implementation allows us to create lazy phis both during
     * lookups and updates; the lazy phi creation is part of chapter 8.
     *
     * @param name  Name whose binding is being updated or looked up
     * @param n     If null, do a lookup, else update binding
     * @param nestingLevel The starting nesting level
     * @return node being looked up, or the one that was updated
     */
    private Node update( String name, Node n, int nestingLevel ) {
        if( nestingLevel<0 ) return null;  // Missed in all scopes, not found
        var syms = _scopes.get(nestingLevel); // Get the symbol table for nesting level
        var idx = syms.get(name);
        if( idx == null ) return update(name,n,nestingLevel-1); // Not found in this scope, recursively look in parent scope
        Node old = in(idx);
        // If n is null we are looking up rather than updating, hence return existing value
        return n==null ? old : setDef(idx,n);
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
     * The new Scope is a full-fledged Node with proper use<->def edges.
     */
    public ScopeNode dup() {
        ScopeNode dup = new ScopeNode();
        // Our goals are:
        // 1) duplicate the name bindings of the ScopeNode across all stack levels
        // 2) Make the new ScopeNode a user of all the nodes bound
        // 3) Ensure that the order of defs is the same to allow easy merging
        for( HashMap<String,Integer> syms : _scopes )
            dup._scopes.push(new HashMap<>(syms));
        dup.addDef(ctrl());      // Control input is just copied
        for( int i=1; i<nIns(); i++ )
            dup.addDef(in(i));
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
        RegionNode r = (RegionNode) ctrl(new RegionNode(null,ctrl(), that.ctrl()).peephole());
        String[] ns = reverseNames();
        // Note that we skip i==0, which is bound to '$ctrl'
        for (int i = 1; i < nIns(); i++)
            if( in(i) != that.in(i) ) // No need for redundant Phis
                setDef(i, new PhiNode(ns[i], r, in(i), that.in(i)).peephole());
        that.kill();            // Kill merged scope
        return r;
    }

}
