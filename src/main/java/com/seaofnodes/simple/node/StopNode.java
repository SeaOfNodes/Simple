package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;

public class StopNode extends CFGNode {

    public final String _src;

    public StopNode(String src) {
        super();
        _src = src;
        _type = compute();
    }
    public StopNode(StopNode stop) { super(stop);  _src = stop._src; }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        // For the sake of many old tests, and single value prints as "return val"
        if( ret()!=null ) return p.n(ret());
        p.p("Stop[ ");
        for( Node ret : _inputs )
            p.n(ret).p(" ");
        return p.p("]");
    }

    @Override public boolean blockHead() { return true; }


    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        return nIns()==1 && in(0) instanceof ReturnNode ret ? ret : null;
    }

    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( ((ReturnNode)in(i)).fun().isDead() )
                delDef(i--);
        if( len != nIns() ) return this;
        return null;
    }

    @Override public int idepth() {
        if( validIDepth() ) return _idepth;
        int d=0;
        for( Node ret : _inputs )
            d = Math.max(d,((ReturnNode)ret).idepth()+1);
        return cacheIDepth(d);
    }
}
