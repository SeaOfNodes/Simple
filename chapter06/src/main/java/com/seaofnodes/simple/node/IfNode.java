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
        if (ctrl()._type != Type.CONTROL) return TypeTuple.IF_BOTH_UNREACHABLE;
        // If test os not a constant true/false then assume both branches are reachable
        if (!pred()._type.isConstant())   return TypeTuple.IF_BOTH_REACHABLE;
        // If constant is 0 then false branch is reachable
        // Else true branch is reachable
        if (pred()._type instanceof TypeInteger ti) {
            if (ti.value() == 0)          return TypeTuple.IF_FALSE_REACHABLE;
            else                          return TypeTuple.IF_TRUE_REACHABLE;
        }
        throw new IllegalStateException("Unexpected predicate type " + pred()._type);
    }

    @Override
    public Node idealize() {
        return null;
    }
}
