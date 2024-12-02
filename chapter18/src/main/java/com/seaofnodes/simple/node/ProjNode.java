package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeTuple;

import java.util.BitSet;

public class ProjNode extends Node implements MultiUse {

    // Which slice of the incoming multipart value
    public final int _idx;

    // Debugging label
    public final String _label;

    public ProjNode(Node ctrl, int idx, String label) {
        super(ctrl);
        _idx = idx;
        _label = label;
    }

    @Override public String label() { return _label; }
    @Override public int idx() { return _idx; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(_label); }

    @Override public CFGNode cfg0() { return in(0).cfg0(); }

    @Override public boolean isMultiTail() { return in(0).isMultiHead(); }
    @Override public boolean isMem() { return _type instanceof TypeMem; }
    @Override public boolean isPinned() { return true; }

    @Override
    public Type compute() {
        Type t = in(0)._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override public Node idealize() { return null; }

    @Override
    boolean eq( Node n ) { return _idx == ((ProjNode)n)._idx; }

    @Override
    int hash() { return _idx; }
}
