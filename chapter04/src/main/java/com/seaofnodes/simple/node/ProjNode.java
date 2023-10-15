package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;

public class ProjNode extends Node {

    public final int _idx;

    public ProjNode(MultiNode ctrl, int idx) {
        super((Node) ctrl);
        _idx = idx;
        // Do an initial type computation
        _type = compute();
    }

    @Override
    public String label() {
        return "Proj" + _idx;
    }

    @Override
    StringBuilder _print(StringBuilder sb) { return sb.append("Proj_" +_nid); }

    public Node ctrl() { return in(0); }

    @Override
    public Type compute() {
        return ctrl() instanceof MultiNode multi ? multi.projType(this) : TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
