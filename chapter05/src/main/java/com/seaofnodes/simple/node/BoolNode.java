package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

abstract public class BoolNode extends Node {

    public BoolNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    abstract String op();       // String opcode name
    
    @Override
    public String label() { return getClass().getSimpleName(); }

    @Override
    public String glabel() { return op(); }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        in(1)._print0(sb.append("("));
        in(2)._print0(sb.append(op()));
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(doOp(i0.value(), i1.value()) ? 1 : 0);
            return i0.meet(i1);
        }
        return Type.BOTTOM;
    }

    abstract boolean doOp(long lhs, long rhs);

    @Override
    public Node idealize() {
        // Compare of same 
        if( in(1)==in(2) )
            return new ConstantNode(TypeInteger.constant(doOp(3,3)?1:0));

        return null;
    }

    public static class EQ extends BoolNode { public EQ(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "=="; } boolean doOp(long lhs, long rhs) { return lhs == rhs; } Node copy(Node lhs, Node rhs) { return new EQ(lhs,rhs); } }
    public static class LT extends BoolNode { public LT(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<" ; } boolean doOp(long lhs, long rhs) { return lhs <  rhs; } Node copy(Node lhs, Node rhs) { return new LT(lhs,rhs); } }
    public static class LE extends BoolNode { public LE(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<="; } boolean doOp(long lhs, long rhs) { return lhs <= rhs; } Node copy(Node lhs, Node rhs) { return new LE(lhs,rhs); } }
}
