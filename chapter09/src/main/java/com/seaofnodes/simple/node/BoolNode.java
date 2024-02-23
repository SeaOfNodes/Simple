package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

abstract public class BoolNode extends IntDataNode {

    public BoolNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    abstract String op();       // String opcode name
    
    @Override
    public String label() { return getClass().getSimpleName(); }

    @Override
    public String glabel() { return op(); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(op()), visited);
        return sb.append(")");
    }

    @Override
    public Type intCompute(TypeInteger i1, TypeInteger i2) {
        if( i1._is_con && i2._is_con )
            return TypeInteger.constant( doOp(i1._con,i2._con) ? 1 : 0 );
        return TypeInteger.BOT;
    }

    abstract boolean doOp(long lhs, long rhs);

    @Override
    public Node idealize() {
        // Compare of same 
        if( in(1)==in(2) )
            return new ConstantNode(TypeInteger.constant(doOp(3,3)?1:0));

        // Do we have ((x * (phi cons)) * con) ?
        // Do we have ((x * (phi cons)) * (phi cons)) ?
        // Push constant up through the phi: x * (phi con0*con0 con1*con1...)
        Node phicon = AddNode.phiCon(this,false);
        if( phicon!=null ) return phicon;

        return null;
    }

    public static class EQ extends BoolNode { public EQ(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "=="; } boolean doOp(long lhs, long rhs) { return lhs == rhs; } Node copy(Node lhs, Node rhs) { return new EQ(lhs,rhs); } }
    public static class LT extends BoolNode { public LT(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<" ; } boolean doOp(long lhs, long rhs) { return lhs <  rhs; } Node copy(Node lhs, Node rhs) { return new LT(lhs,rhs); } }
    public static class LE extends BoolNode { public LE(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<="; } boolean doOp(long lhs, long rhs) { return lhs <= rhs; } Node copy(Node lhs, Node rhs) { return new LE(lhs,rhs); } }
}
