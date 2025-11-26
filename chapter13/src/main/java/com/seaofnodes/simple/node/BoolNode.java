package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeMemPtr;

import java.util.BitSet;

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
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(op()), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(doOp(i0.value(), i1.value()) ? 1 : 0);
        }
        if( in(1)._type instanceof TypeFloat i0 &&
            in(2)._type instanceof TypeFloat i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(doOp(i0.value(), i1.value()) ? 1 : 0);
        }
        return TypeInteger.BOT;
    }

    boolean doOp(long   lhs, long   rhs) { throw Utils.TODO(); }
    boolean doOp(double lhs, double rhs) { throw Utils.TODO(); }

    @Override
    public Node idealize() {
        // Compare of same
        if( in(1)==in(2) )
            return new ConstantNode(TypeInteger.constant(doOp(3,3)?1:0));

        // Equals pushes constant to the right; 5==X becomes X==5.
        if( this instanceof EQ ) {
            if( !(in(2) instanceof ConstantNode) ) {
                // con==noncon becomes noncon==con
                if( in(1) instanceof ConstantNode || in(1)._nid > in(2)._nid )
                // Equals sorts by NID otherwise: non.high == non.low becomes non.low == non.high
                    return in(1)._type instanceof TypeFloat ? new EQF(in(2),in(1)) : new EQ(in(2),in(1));
            }
            // Equals X==0 becomes a !X
            if( (in(2)._type == TypeInteger.ZERO || in(2)._type == TypeMemPtr.NULLPTR) )
                return new NotNode(in(1));
        }

        // Do we have ((x * (phi cons)) * con) ?
        // Do we have ((x * (phi cons)) * (phi cons)) ?
        // Push constant up through the phi: x * (phi con0*con0 con1*con1...)
        Node phicon = AddNode.phiCon(this,this instanceof EQ);
        if( phicon!=null ) return phicon;

        return null;
    }

    public static class EQ extends BoolNode { public EQ(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "=="; } boolean doOp(long lhs, long rhs) { return lhs == rhs; } Node copy(Node lhs, Node rhs) { return new EQ(lhs,rhs); } Node copyF() { return new EQF(null,null); } }
    public static class LT extends BoolNode { public LT(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<" ; } boolean doOp(long lhs, long rhs) { return lhs <  rhs; } Node copy(Node lhs, Node rhs) { return new LT(lhs,rhs); } Node copyF() { return new LTF(null,null); }}
    public static class LE extends BoolNode { public LE(Node lhs, Node rhs) { super(lhs,rhs); } String op() { return "<="; } boolean doOp(long lhs, long rhs) { return lhs <= rhs; } Node copy(Node lhs, Node rhs) { return new LE(lhs,rhs); } Node copyF() { return new LEF(null,null); } }

    public static class EQF extends EQ { public EQF(Node lhs, Node rhs) { super(lhs,rhs); } boolean doOp(double lhs, double rhs) { return lhs == rhs; } Node copyF() { return null; } }
    public static class LTF extends LT { public LTF(Node lhs, Node rhs) { super(lhs,rhs); } boolean doOp(double lhs, double rhs) { return lhs <  rhs; } Node copyF() { return null; } }
    public static class LEF extends LE { public LEF(Node lhs, Node rhs) { super(lhs,rhs); } boolean doOp(double lhs, double rhs) { return lhs <= rhs; } Node copyF() { return null; } }

}
