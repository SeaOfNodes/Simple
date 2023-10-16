package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;
import com.seaofnodes.simple.type.TypeInteger;

abstract public class BoolNode extends Node {

    public BoolNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
        // Do an initial type computation
        _type = compute();
    }

    abstract String op();       // String opcode name
    abstract boolean same();    // Op on equal inputs
    
    @Override
    public String label() { return op(); }

    @Override
    StringBuilder _print(StringBuilder sb) {
        in(1)._print(sb.append("("));
        in(2)._print(sb.append(op()));
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
        return TypeBot.BOTTOM;
    }

    abstract boolean doOp(long lhs, long rhs);

    @Override
    public Node idealize() {
        // Compare of same 
        if( in(1)==in(2) )
            return new ConstantNode(TypeInteger.constant(same()));

        return null;
    }

    public static class EQNode extends BoolNode { public EQNode(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "=="; } boolean doOp(long lhs, long rhs) { return lhs == rhs; } boolean same() { return 1; } }
    public static class NENode extends BoolNode { public NENode(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "!="; } boolean doOp(long lhs, long rhs) { return lhs != rhs; } boolean same() { return 0; } }
    public static class LTNode extends BoolNode { public LTNode(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<" ; } boolean doOp(long lhs, long rhs) { return lhs <  rhs; } boolean same() { return 0; } }
    public static class LENode extends BoolNode { public LENode(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<="; } boolean doOp(long lhs, long rhs) { return lhs <= rhs; } boolean same() { return 1; } }
    public static class GTNode extends BoolNode { public GTNode(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return ">" ; } boolean doOp(long lhs, long rhs) { return lhs >  rhs; } boolean same() { return 0; } }
    public static class GENode extends BoolNode { public GENode(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return ">="; } boolean doOp(long lhs, long rhs) { return lhs >= rhs; } boolean same() { return 1; } }
}
