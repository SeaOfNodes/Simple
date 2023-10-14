package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;

public class ProjNode extends Node {

    public final int _idx;

    public ProjNode(Node ctrl, int idx) {
        super(ctrl);
        _idx = idx;
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
        if (ctrl() instanceof MultiNode multi) {
            return multi.projType(this);
        }
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
