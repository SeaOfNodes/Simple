package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/**
 *  Allocation!  Allocate a chunk of memory, and pre-zero it.
 *  The inputs include control and size, and ALL aliases being set.
 *  The output is large tuple, one for every alias plus the created pointer.
 *  New is expected to be followed by projections for every alias.
 */
public class NewNode extends Node implements MultiNode {

    TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node... nodes) {
        super(nodes);
        // Control in slot 0
        assert nodes[0]._type==Type.CONTROL || nodes[0]._type == Type.XCONTROL;
        // Malloc-length in slot 1
        assert nodes[1]._type instanceof TypeInteger;
        // Memory slices in remaining slots
        for( int i=2; i<nodes.length; i++ )
            assert nodes[i]._type instanceof TypeMem;

        _ptr = ptr;
    }

    @Override public String label() { return "new_" + _ptr._obj.str(); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("new ");
        return sb.append(_ptr._obj.str());
    }

    @Override
    public TypeTuple compute() {
        Type[] ts = new Type[nIns()];
        ts[0] = Type.CONTROL;
        ts[1] = _ptr;
        Field[] fs = _ptr._obj._fields;
        for( int i=0; i<fs.length; i++ )
            ts[i+2] = TypeMem.make(fs[i]._alias,fs[i]._type.makeInit()).meet( in(i+2)._type );
        return TypeTuple.make(ts);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return _ptr.hashCode(); }
}
