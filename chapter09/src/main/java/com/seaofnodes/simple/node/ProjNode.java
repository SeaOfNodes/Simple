package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeTuple;

import java.util.BitSet;

public class ProjNode extends Node {

    // Which slice of the incoming multi-part value
    public final int _idx;

    // Debugging label
    public final String _label;
    
    public ProjNode(MultiNode ctrl, int idx, String label) {
        super(ctrl);
        _idx = idx;
        _label = label;
    }

    @Override
    public String label() { return _label; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(_label); }

    @Override public boolean isCFG() { return _idx==0 || ctrl() instanceof IfNode; }
    @Override public boolean isMultiTail() { return in(0).isMultiHead(); }

    public MultiNode ctrl() { return (MultiNode)in(0); }

    @Override
    public Type compute() {
        Type t = ctrl()._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        if( ctrl()._type instanceof TypeTuple tt ) {
            if( tt._types[_idx]==Type.XCONTROL )
                return new ConstantNode(Type.XCONTROL).peephole(); // We are dead
            if( tt._types[1-_idx]==Type.XCONTROL ) // Only true for IfNodes
                return ctrl().in(0);               // We become our input control
        }
        return null;
    }

    @Override
    boolean eq( Node n ) { return _idx == ((ProjNode)n)._idx; }

    @Override
    int hash() { return _idx; }
}
