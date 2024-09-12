package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;

/** Allocate and zero an object. Inputs {ctrl, $mem, size}; results {ptr, $mem}.
 *  The memory input and result cover only the aliases in the allocated struct.
 */
public class NewNode extends Node implements MultiNode {

    public final TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node ctrl, Node mem, Node size) {
        super(ctrl,mem,size);
        assert ctrl._type==Type.CONTROL || ctrl._type==Type.XCONTROL;
        assert mem._type instanceof TypeMem;
        assert size._type instanceof TypeInteger;
        _ptr = ptr;
    }

    public Node mem() { return in(1); }
    public Node size() { return in(2); }

    public Field field(int alias) {
        for( Field f : _ptr._obj._fields )
            if( f._alias==alias ) return f;
        return null;
    }

    @Override public String label() {
        return "new_" + (_ptr._obj.isAry() ? "ary_"+_ptr._obj._fields[1]._type.str() : _ptr._obj.str());
    }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p("new ").p(_ptr._obj.str());
    }

    @Override
    public TypeTuple compute() {
        return TypeTuple.make(_ptr,TypeMem.BOT);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return _ptr.hashCode(); }
}
