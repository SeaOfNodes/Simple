package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeFloat;
import java.util.BitSet;

public class DivNode extends ArithNode {
    @Override boolean allowFloat() { return true; }
    public DivNode(Node lhs, Node rhs) { super(null, lhs, rhs); }
    public DivNode(Node lhs, Node rhs, byte mode) { super(null, lhs, rhs, mode); }
    @Override public Tag serialTag() { return Tag.Div; }

    @Override public String op() { return _mode==1 ? "//" : "/"; }

    @Override long   doOp( long  x,  long  y ) { return y==0 ? 0 : x / y; }
    @Override double doOp(double x, double y ) { return            x / y; }
    @Override TypeInteger doOp(TypeInteger x, TypeInteger y) {
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // Div of 1.
        if( in(2)._type == TypeInteger.TRUE )
            return in(1);

        // Div of constant
        if( _mode==2 ) {
            if( in(2)._type == TypeFloat.constant(1.) )
                return in(1);
            if( in(2)._type instanceof TypeFloat f && f.isConstant() )
                return new MulNode(in(1),ConstantNode.make(TypeFloat.constant(1.0/f.value())).peephole(),_mode);
        }

        return super.idealize();
    }

    @Override Node copy(Node lhs, Node rhs) { return new DivNode(lhs,rhs,_mode); }
}
