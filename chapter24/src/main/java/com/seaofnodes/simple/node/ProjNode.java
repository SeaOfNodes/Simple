package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeTuple;

import java.util.BitSet;

public class ProjNode extends Node {

    // Which slice of the incoming multipart value
    public final int _idx;

    // Debugging label
    public final String _label;

    public ProjNode(Node ctrl, int idx, String label) {
        super(new Node[]{ctrl});
        _idx = idx;
        _label = label;
    }
    public ProjNode(ProjNode p) { super(p); _idx = p._idx; _label = p._label; }

    @Override public String label() { return _label; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( _label != null )  return sb.append(_label);
        if( in(0) instanceof CallEndNode cend && cend.call()!=null )
            return cend.call()._print0(sb,visited);
        return sb.append("LONELY PROJ");
    }

    @Override public CFGNode cfg0() {
        return in(0) instanceof CFGNode cfg ? cfg : in(0).cfg0();
    }

    @Override public boolean isMem() { return _type instanceof TypeMem; }
    @Override public boolean isPinned() { return true; }

    @Override
    public Type compute() {
        Type t = in(0)._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override public Node idealize() { return ((MultiNode)in(0)).pcopy(_idx); }

    @Override
    public boolean eq( Node n ) { return _idx == ((ProjNode)n)._idx; }

    @Override
    int hash() { return _idx; }
}
