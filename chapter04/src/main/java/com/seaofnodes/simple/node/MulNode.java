package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class MulNode extends Node {
    public MulNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Mul"; }

    @Override public String glabel() { return "*"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        in(1)._print0(sb.append("("));
        in(2)._print0(sb.append("*"));
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()*i1.value());
            return i0.meet(i1);
        }
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Mul of 1.  We do not check for (1*x) because this will already
        // canonicalize to (x*1)
        if ( t2.isConstant() && t2 instanceof TypeInteger i && i.value()==1 )
            return lhs;

        // Move constants to RHS: con*arg becomes arg*con
        if ( t1.isConstant() && !t2.isConstant() )
            return swap12();

        return null;
    }
}
