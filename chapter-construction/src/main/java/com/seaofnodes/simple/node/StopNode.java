package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.Utils;

public class StopNode extends CFGNode {

    public final String _src;

    public StopNode(String src) {
        super();
        _src = src;
        _type = compute();
    }
    public StopNode(StopNode stop) { super(stop);  _src = stop==null ? null : stop._src; }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        // For the sake of many old tests, and single value prints as "return val"
        ReturnNode ret1 = ret();
        if( ret1!=null ) return p.n(ret1);
        p.p("Stop[ ");
        for( Node ret : _inputs )
            if( ret!=null ) {
                String name = ((ReturnNode)ret).fun()._name;
                if( name== null || !name.startsWith("sys.") )
                    p.n(ret).p(" ");
            }
        return p.p("]");
    }

    @Override public boolean blockHead() { return true; }


    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        Node ret1 = this;
        for( Node ret : _inputs ) {
            String name = ((ReturnNode)ret).fun()._name;
            if( name==null || !name.startsWith("sys.") )
                ret1 = ret1==this ? ((ReturnNode)ret) : null;
        }
        return ret1==this ? null : (ReturnNode)ret1;
    }

    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        int len = nIns();
        for( int i=0; i<nIns(); i++ )
            if( addDep(((ReturnNode)in(i)).fun()).isDead() )
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
