package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

import java.util.BitSet;

/**
 * Cast a pointer to read-only
 */
public class ReadOnlyNode extends Node {
    public ReadOnlyNode( Node n ) { super(null,n); }
    public ReadOnlyNode( ReadOnlyNode n ) { super(n); }
    @Override public String label() { return "ReadOnly"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append("(const)"),visited);
    }
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
