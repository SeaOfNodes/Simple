package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.TypeBot;

import java.util.*;

/**
 * The Scope node is a purely parser helper - it tracks names to nodes with a
 * stack of scopes.
 */
public class ScopeNode extends Node {

    /**
     * Stack of lexical scopes, each scope is a symbol table
     * that binds variable names to Nodes.
     * The top of this stack represents current scope.
     */
    public final Stack<HashMap<String, Integer>> _scopes = new Stack<>();

    @Override
    public String label() { return "Scope" + _nid; }

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
  
    @Override
    public TypeBot compute() { return TypeBot.BOTTOM; }

    @Override
    public Node idealize() { return null; }

    // Add an empty lexical scope
    public void push() {
        _scopes.push(new HashMap<>());
    }

    // Remove the current lexical scope, killing all unused nodes.
    public void pop() {
        HashMap<String,Integer> scope = _scopes.pop();
        pop_n(scope.size());
    }

    // Create a new name in the current scope
    public Node define( String name, Node n ) {
        HashMap<String,Integer> scope = _scopes.lastElement();
        if( scope.put(name,nIns()) != null )
            return null;        // Double define
        return add_def(n);
    }

    // Lookup a name in all scopes
    public Node lookup(String name) {
        for (int i = _scopes.size() - 1; i >= 0; i--) {
            var idx = _scopes.get(i).get(name);
            if (idx != null) return in(idx);
        }
        return null;
    }

    public Node ctrl() { assert lookup(Parser.CTRL)==in(0); return in(0); }
    
    public Node ctrl(Node n) {
        assert lookup(Parser.CTRL)==in(0);
        set_def(0,n);
        return n;
    }

  
    // If the name is present in any scope, then redefine
    public Node update(String name, Node n) {
        for (int i = _scopes.size() - 1; i >= 0; i--) {
            HashMap<String, Integer> scope = _scopes.get(i);
            Integer idx = scope.get(name);
            if (idx != null)           // Found prior def
                return set_def(idx,n); // Update def in scope
        }
        return null;
    }

    /**
     * Duplicate a ScopeNode; including all levels, up to Nodes.  So this is
     * neither shallow (would dup the Scope but not the internal HashMap
     * tables), nor deep (would dup the Scope, the HashMap tables, but then the
     * also program Nodes).
     *
     * The new Scope is a full-fledged Node with proper use<->def edges.
     */
    public ScopeNode dup() {
        ScopeNode dup = new ScopeNode();
        for( HashMap<String, Integer> tab : _scopes ) {
            dup.push();    // Create same level in the duplicate
            for( String name : tab.keySet() )
                // Ensure that dup is a user of n
                dup.define(name, in(tab.get(name)));
        }
        return dup;
    }

    public RegionNode mergeScopes(ScopeNode that) {
        RegionNode r = (RegionNode)ctrl(new RegionNode(ctrl(), that.ctrl()).peephole());
        for( int i=1; i<nIns(); i++ )
            if( in(1) != that.in(i) ) // No need for redundant Phis
                set_def(i,new PhiNode(r, in(i), that.in(i)).peephole());
        that.kill();            // Kill merged scope
        return r;
    }

    /**
     * Cleanup the scope and all its levels.
     * Ensure that the scope is removed as user of all the defs.
     */
    public void clear() {
        while (!_scopes.empty())
            pop();
    }
}
