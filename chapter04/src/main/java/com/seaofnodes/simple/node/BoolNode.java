package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;
import com.seaofnodes.simple.type.TypeInteger;

public class BoolNode extends Node {

    public String _op;
    public BoolNode(String op, Node lhs, Node rhs) {
        super(null, lhs, rhs);
        _op = op;
    }

    @Override
    public String label() {
        return "Comp"+_op;
    }

    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append(_op));
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(doOp(i0.value(), i1.value()));
            return i0.meet(i1);
        }
        return TypeBot.BOTTOM;
    }

    private long doOp(long lhs, long rhs) {
        switch (_op) {
            case "==": return asInt(lhs == rhs);
            case "!=": return asInt(lhs != rhs);
            case "<": return asInt(lhs < rhs);
            case "<=": return asInt(lhs <= rhs);
            case ">": return asInt(lhs > rhs);
            case ">=": return asInt(lhs >= rhs);
            default: throw new IllegalArgumentException("Unexpected boolean operator " + _op);
        }
    }

    private long asInt(boolean b) {
        return b ? 1 : 0;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
