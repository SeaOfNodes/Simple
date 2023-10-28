package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;
import com.seaofnodes.simple.type.TypeTuple;

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
    StringBuilder _print1(StringBuilder sb) { return sb.append(_label); }

    @Override public boolean isCFG() { return _idx==0 || ctrl() instanceof IfNode; }

    public MultiNode ctrl() { return (MultiNode)in(0); }

    @Override
    public Type compute() {
        Type t = ctrl()._type;
        return t instanceof TypeTuple tt ? tt._types[_idx] : TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
