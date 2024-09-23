package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
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
        if( ts._fields.length==0 ) return;
        // Expand args type for more memory projections
        Type[] args = _args._types;
        int max = args.length;
        for( Field f : ts._fields )
            max = Math.max(max,f._alias);
        args = Arrays.copyOf(args, max+1);
        // For each of the fields we now add a mem projection.
        for( Field f : ts._fields ) {
            TypeMem tm_decl = TypeMem.make(f._alias,f._type.glb());
            args[f._alias] = tm_decl.dual();
            String name = Parser.memName(f._alias);
            Node n = new ProjNode(this, f._alias, name); // No 'compute' until ScopeNode gets typed
            n._type = args[f._alias];
            scope.define(name, tm_decl, n);
        }
        _type = _args = TypeTuple.make(args);
    }

    @Override public String label() { return "Start"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
      return sb.append(label());
    }

    @Override public boolean isMultiHead() { return true; }
    @Override public boolean blockHead() { return true; }
    @Override public CFGNode cfg0() { return this; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override public int idepth() { return 0; }
    @Override public CFGNode idom(Node dep) { return null; }

    @Override void _walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) { }

    @Override public int loopDepth() { return (_loopDepth=1); }

    @Override public Node getBlockStart() { return this; }
}
