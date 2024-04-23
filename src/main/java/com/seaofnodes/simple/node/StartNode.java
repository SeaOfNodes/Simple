package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;

/**
 * The Start node represents the start of the function.
 *
 * Start initially has 1 input (arg) from outside and the initial control.
 * In ch10 we also add mem aliases as structs get defined; each field in struct
 * adds a distinct alias to Start's tuple.
 */
public class StartNode extends MultiNode {

    // This field is NOT final, because the tuple expands as we add memory aliases
    TypeTuple _args;

    // The alias number of the first field in the struct.
    //
    public final HashMap<String,Integer> _aliasStarts;


    public StartNode(Type[] args) {
        super();
        _args = TypeTuple.make(args);
        _type = _args;
        _aliasStarts = new HashMap<>();
    }

    /**
     * Creates a projection for each of the struct's fields, using the field alias
     * as the key.
     */
    public void addMemProj( TypeStruct ts, ScopeNode scope) {
        int len = _args._types.length;
        _aliasStarts.put(ts._name,len);

        // resize the tuple's type array to include all fields of the struct
        int max = len + ts._fields.length;
        Type[] args = Arrays.copyOf(_args._types, max);

        // The new members of the tuple get a mem type with an alias
        for( int alias = len; alias < max; alias++ )
            args[alias] = TypeMem.make(alias);
        _type = _args = TypeTuple.make(args);
        // For each of the fields we now add a mem projection.  Note that the
        // alias matches the slot of the field in the tuple
        for( int alias = len; alias < max; alias++ ) {
            String name = Parser.memName(alias);
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

    @Override public boolean isCFG() { return true; }
    @Override public boolean isMultiHead() { return true; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override int idepth() { return 0; }
    @Override Node idom() { return null; }
}
