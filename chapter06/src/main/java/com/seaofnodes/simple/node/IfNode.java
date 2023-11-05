package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeTuple;

public class IfNode extends MultiNode {

    public IfNode(Node ctrl, Node pred) {
        super(ctrl, pred);
    }

    @Override
    public String label() { return "If"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        sb.append("if( ");
        return in(1)._print0(sb).append(" )");
    }

    @Override public boolean isCFG() { return true; }

    public Node ctrl() { return in(0); }
    public Node pred() { return in(1); }

    @Override
    public Type compute() {
        // If the If node is not reachable then neither is any following Proj
        if (ctrl()._type != Type.CONTROL) return TypeTuple.IF_NEITHER;
        // If constant is 0 then false branch is reachable
        // Else true branch is reachable
        if (pred()._type instanceof TypeInteger ti && ti.isConstant()) {
            if (ti.value() == 0)          return TypeTuple.IF_FALSE;
            else                          return TypeTuple.IF_TRUE;
        }
        return TypeTuple.IF_BOTH;
    }

    @Override
    public Node idealize() {
        if (ctrl() instanceof RegionNode r) {
            Node live = r.singleLiveInput();
            if (live != null) {
                set_def(0, live);
                return this;
            }
        }
        return null;
    }
}
