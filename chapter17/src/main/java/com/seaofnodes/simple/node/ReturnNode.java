package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;


/** Keeps all preceding memory effects and the return value alive: {ctrl, $mem, value}. */
public class ReturnNode extends CFGNode {

    public ReturnNode(Node ctrl, Node data, ScopeNode scope) {
        // A synthetic never-taken loop exit has no source scope.
        super(ctrl, scope == null ? null : scope.mem(), data);
    }

    public Node ctrl() { return in(0); }
    public Node mem () { return in(1); }
    public Node expr() { return in(2); }

    @Override
    public String label() { return "Return"; }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        p.p("return ");
        p.n(expr());
        return p.p(";");
    }

    @Override
    public Type compute() {
        // Return exposes the complete memory state.
        return TypeTuple.make(ctrl()._type,TypeMem.BOT,expr()._type);
    }

    @Override
    public Node idealize() {
        if( ctrl()._type==Type.XCONTROL )
            return ctrl();
        return null;
    }
}
