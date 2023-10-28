package com.seaofnodes.simple.node;

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

    /**
     * We duplicate ScopeNodes when branches occur, so it is useful to have
     * a unique name for each ScopeNode; this helps when we want to visualize the
     * graph
     */
    private int _id;

    private static int _idCounter = 0;

    public ScopeNode() { _id = _idCounter++; }
  
    @Override
    public String label() { return "Scope" + _id; }

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
     * Duplicate a ScopeNode; including all levels.
     *
     * We do it in a way that ensures that the new ScopeNode becomes
     * additional user of all defined names
     */
    public ScopeNode dup() {
        ScopeNode dupScope = new ScopeNode();
        for (HashMap<String, Integer> tab: _scopes) {
            dupScope.push(); // Create same level in the duplicate
            for (Map.Entry<String, Integer> e: tab.entrySet()) {
                String name = e.getKey();
                Integer idx = e.getValue();
                Node n = in(idx);
                dupScope.define(name, n); // Ensure that dupScope is a user of n
            }
        }
        return dupScope;
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
