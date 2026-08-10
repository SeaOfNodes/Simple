package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

/**
 * Cast a pointer to read-only
 */
public class ReadOnlyNode extends Node {
    public ReadOnlyNode( Node n ) { super(null,n); }
    public ReadOnlyNode( ReadOnlyNode n ) { super(n); }
    @Override public Tag serialTag() { return Tag.ReadOnly; }

    @Override protected String format() { return "(const)%1"; }
    @Override public Type compute() {
        Type t = in(1)._type;
        return t instanceof TypeMemPtr tmp ? tmp.makeRO() : t;
    }

    @Override public Node idealize() {
        if( in(1)._type instanceof TypeMemPtr tmp && tmp.isFinal() )
            return in(1);
        return null;
    }

}
