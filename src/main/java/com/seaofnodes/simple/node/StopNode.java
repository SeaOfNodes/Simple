package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.Utils;

public class StopNode extends CFGNode {

    public final String _src;
    private Type _closed;
    public void close() { _closed=_type; }

    public StopNode(String src) {
        super();
        _src = src;
        _type = compute();
    }
    public StopNode(StopNode stop) { super(stop);  _src = stop==null ? null : stop._src; _closed=stop==null ? null : stop._closed; }

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
        if( _closed!=null ) return _closed;
        CodeGen code=CodeGen.CODE;
        if( code._phase==null || code._phase.ordinal()<CodeGen.Phase.Opto.ordinal() ) return TypeMem.BOT;
        TypeMem mem=TypeMem.START;
        for( Node n : _inputs ) {
            ReturnNode ret=(ReturnNode)n;
            if( !ret.fun().unknownCallers() ) continue;
            // An external caller already holds its arguments. Writes through
            // those pointers are visible even if this function returns a scalar.
            for( Node use : ret.fun()._outputs )
                if( use instanceof ParmNode p && p._idx>=2 )
                    mem=mem.escapes(addDepForwards(p)._type);
            if( !(ret._type instanceof TypeTuple tt) || tt._types[0].isHigh() ) continue;
            mem=mem.escapes(tt.ret()).escapes(tt._types[1]);
        }
        for( CallNode call : code._calls )
            if( !call.isDead() ) mem=mem.escapes(call.exposed(this));
        return mem;
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
