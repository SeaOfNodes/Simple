package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

public class ParmNode extends PhiNode {

    // Argument indices are mapped one-to-one on CallNode inputs
    public final int _idx;             // Argument index

    public ParmNode(String label, int idx, Type declaredType, Node... inputs) {
        super(label,declaredType,inputs);
        _idx = idx;
    }
    public ParmNode(ParmNode parm) { super(parm, parm._label, parm._declaredType); _idx = parm._idx; }

    @Override public String label() { return MemOpNode.mlabel(_label); }

    public FunNode fun() { return (FunNode)in(0); }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( "main".equals(fun()._name) && _label.equals("arg") )
            return p.p("arg");
        p.p("Parm_").p(_label).open();
        for( Node in : _inputs ) p.n(in).p(",");
        return p.unchar(',').close();
    }


    @Override
    public Node idealize() {
        if( inProgress() ) return null;
        return super.idealize();
    }

    // Always in-progress until we run out of unknown callers
    @Override public boolean inProgress() { return fun().inProgress(); }

    @Override public boolean eq( Node n ) {
        return ((ParmNode)n)._idx==_idx && super.eq(n);
    }
}
