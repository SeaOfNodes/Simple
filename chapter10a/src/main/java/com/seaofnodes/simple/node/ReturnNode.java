package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

import java.util.BitSet;

/** Keeps the return value and all preceding memory effects alive. */
public class ReturnNode extends Node {

    public ReturnNode(Node ctrl, Node data, ScopeNode scope) {
        super(ctrl, data, scope.lookup("$mem"));
    }

    public Node ctrl() { return in(0); }
    public Node expr() { return in(1); }

    @Override
    public String label() { return "Return"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("return ");
        expr()._print0(sb, visited);
        return sb.append(";");
    }

    @Override public boolean isCFG() { return true; }

    @Override
    public Type compute() {
        return TypeTuple.make(ctrl()._type,expr()._type);
    }

    @Override
    public Node idealize() {
        if( ctrl()._type==Type.XCONTROL )
            return ctrl();
        return null;
    }
}
