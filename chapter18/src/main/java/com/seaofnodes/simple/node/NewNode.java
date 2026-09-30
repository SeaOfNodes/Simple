package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/** Allocate and initialize an object. Inputs {ctrl, $mem, size, fields...};
 *  results {ptr, $mem}.
 *  The memory input and result cover only the aliases in the allocated struct.
 */
public class NewNode extends Node implements MultiNode {

    public final TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node... nodes) {
        super(nodes);
        assert !ptr.nullable();
        _ptr = ptr;
        assert nodes.length==3+ptr._obj._fields.length;
        assert nodes[0]._type==Type.CONTROL || nodes[0]._type==Type.XCONTROL;
        assert nodes[1]._type instanceof TypeMem;
        assert nodes[2]._type instanceof TypeInteger || nodes[2]._type==Type.NIL;
        for( int i=3; i<nodes.length; i++ ) assert nodes[i]._type!=null;
    }

    public Node mem() { return in(1); }
    public Node size() { return in(2); }

    public Field field(int alias) {
        for( Field f : _ptr._obj._fields )
            if( f._alias==alias ) return f;
        return null;
    }

    @Override public String label() {
        return "new_"+(_ptr._obj.isAry() ? "ary_"+_ptr._obj._fields[1]._type.str() : _ptr._obj.str());
    }
    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("new ");
        return sb.append(_ptr._obj.str());
    }

    // Find matching alias input
    int findAlias(int alias) {
        return 3+_ptr._obj.findAlias(alias);
    }


    @Override
    public TypeTuple compute() {
        return TypeTuple.make(_ptr,TypeMem.BOT);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return _ptr.hashCode(); }
}
