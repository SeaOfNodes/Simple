package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.Utils;
import java.util.BitSet;

/**
 *  Allocation!  Allocate a chunk of memory, and pre-zero it.
 *  The inputs include control and size, and ALL aliases being set.
 *  The output is large tuple, one for every alias plus the created pointer.
 *  New is expected to be followed by projections for every alias.
 */
public class NewNode extends Node implements MultiNode {

    public final TypeMemPtr _ptr;
    public final int _len;

    public NewNode(TypeMemPtr ptr, Node... nodes) {
        super(nodes);
        _ptr = ptr;
        _len = ptr._obj._fields.length;
        // Control in slot 0
        assert nodes[0]._type==Type.CONTROL || nodes[0]._type == Type.XCONTROL;
        // Malloc-length in slot 1
        assert nodes[1]._type instanceof TypeInteger;
        for( int i=0; i<_len; i++ ) {
            // Memory slices for all fields.
            assert nodes[2+     i]._type instanceof TypeMem;
            // Value  slices for all fields.
            assert nodes[2+_len+i]._type instanceof Type;
        }
    }

    @Override public String label() {
        return "new_"+glabel();
    }
    @Override public String glabel() {
        return _ptr._obj.isAry() ? "ary_"+_ptr._obj._fields[1]._type.str() : _ptr._obj.str();
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("new ");
        return sb.append(_ptr._obj.str());
    }

    // Find matching alias input
    int findAlias(int alias) {
        return 2+_ptr._obj.findAlias(alias)+_len;
    }


    @Override
    public TypeTuple compute() {
        Field[] fs = _ptr._obj._fields;
        Type[] ts = new Type[fs.length+2];
        ts[0] = Type.CONTROL;
        ts[1] = _ptr;
        for( int i=0; i<fs.length; i++ ) {
            TypeMem mem = (TypeMem)in(i+2)._type;
            Type tfld = in(2+_len+i)._type.meet(mem._t);
            ts[i+2] = TypeMem.make(fs[i]._alias,tfld);
        }
        return TypeTuple.make(ts);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return _ptr.hashCode(); }
}
