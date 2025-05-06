package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;

/**
 * Build a compound object
 */
public class StructNode extends Node {

    public final TypeStruct _ts;
    public StructNode(TypeStruct ts) { _ts=ts; assert !ts._open; }

    @Override public String label() { return _ts==null ? "STRUCT?" : _ts.str(); }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( _ts==null ) return p.p("STRUCT?");
        p.p(_ts._name).p(" {");
        for( int i=0; i<nIns(); i++ ) {
            p.p(_ts._fields[i]._fname).p(":");
            p.p(in(i)==null ? Type.BOTTOM : in(i)._type);
            p.p("; ");
        }
        return p.unchar("; ").p("}");
    }

    @Override
    public TypeStruct compute() {
        if( _ts==null ) return TypeStruct.BOT;
        Field[] fs = new Field[_ts._fields.length];
        for( int i=0; i<fs.length; i++ )
            fs[i] = _ts._fields[i].makeFrom(in(i)==null ? Type.TOP : in(i)._type);
        return TypeStruct.make(_ts._name,false,fs);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    public boolean eq(Node n) { return _ts == ((StructNode)n)._ts; }

    @Override
    int hash() { return _ts==null ? 0 : _ts.hashCode(); }
}
