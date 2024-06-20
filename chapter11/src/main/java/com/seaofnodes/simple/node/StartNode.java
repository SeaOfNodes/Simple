package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;

/**
 * The Start node represents the start of the function.
 *
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 */
public class StartNode extends CFGNode implements MultiNode {

    // This field is NOT final, because the tuple expands as we add memory aliases
    TypeTuple _args;

    public StartNode(Type[] args) {
        super();
        _args = TypeTuple.make(args);
        _type = _args;
    }

    /**
     * Creates a projection for each of the struct's fields, using the field alias
     * as the key.
     */
    public void addMemProj( TypeStruct ts, ScopeNode scope) {
        if( ts._fields.length==0 ) return; // No projections to add
        int len = _args._types.length;
        int max = ts._fields[ts._fields.length-1]._alias; // Max alias
        if( len > max ) return; // Already big enough
        // resize the tuple's type array to include all fields of the struct
        Type[] args = Arrays.copyOf(_args._types, max+1);
        // The new members of the tuple get a mem type with an alias
        for( int alias = len; alias <= max; alias++ )
            args[alias] = TypeMem.make(alias);
        _type = _args = TypeTuple.make(args);
        // For each of the fields we now add a mem projection.  Note that the
        // alias matches the slot of the field in the tuple
        for( int alias = len; alias <= max; alias++ ) {
            String name = "$"+alias;
            Node n = new ProjNode(this, alias, name).peephole();
            scope.define(name, args[alias], n);
        }
    }

    @Override
    public String label() { return "Start"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override CFGNode idom() { return null; }

    @Override void _walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) { }

    @Override int loopDepth() { return (_loopDepth=1); }

    @Override public Node getBlockStart() { return this; }
}
