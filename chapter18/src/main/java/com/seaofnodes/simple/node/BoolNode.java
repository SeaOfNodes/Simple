package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;
import static com.seaofnodes.simple.type.TypeInteger.*;

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
    public TypeInteger compute() {
        Type t1 = in(1)._type;
        Type t2 = in(2)._type;
        // Exactly equals?
        if( in(1)==in(2) )
            // LT fails, both EQ and LE succeed
            return this instanceof LT ? TypeInteger.FALSE : TypeInteger.TRUE;
        if( t1.isHigh() || t2.isHigh() )
            return BOOL.dual();
        if( t1 instanceof TypeInteger i1 &&
            t2 instanceof TypeInteger i2 )
            return doOp(i1,i2);
        if( t1 instanceof TypeFloat f1 &&
            t2 instanceof TypeFloat f2 &&
            f1.isConstant() && f2.isConstant() )
            return doOp(f1.value(), f2.value()) ? TypeInteger.TRUE : TypeInteger.FALSE;
        return BOOL;
    }

    TypeInteger doOp(TypeInteger t1, TypeInteger t2) { throw Utils.TODO(); }
    boolean doOp(double lhs, double rhs) { throw Utils.TODO(); }
    Node copyF(Node lhs, Node rhs) { return null; }

    @Override
    public Node idealize() {
        // Compare of same
        if( in(1)==in(2) )
            return this instanceof LT ? Parser.ZERO : new ConstantNode(TypeInteger.TRUE);

        // Equals pushes constant to the right; 5==X becomes X==5.
        if( this instanceof EQ ) {
            if( !(in(2) instanceof ConstantNode) ) {
                // con==noncon becomes noncon==con
                if( in(1) instanceof ConstantNode || in(1)._nid > in(2)._nid )
                // Equals sorts by NID otherwise: non.high == non.low becomes non.low == non.high
                    return in(1)._type instanceof TypeFloat ? new EQF(in(2),in(1)) : new EQ(in(2),in(1));
            }
            // Equals X==0 becomes a !X
            if( (in(2)._type == ZERO || in(2)._type == Type.NIL) )
                return new NotNode(in(1));
        }

        // Do we have ((x * (phi cons)) * con) ?
        // Do we have ((x * (phi cons)) * (phi cons)) ?
        // Push constant up through the phi: x * (phi con0*con0 con1*con1...)
        Node phicon = AddNode.phiCon(this,this instanceof EQ);
        if( phicon!=null ) return phicon;

        return null;
    }

    public static class EQ extends BoolNode {
        public EQ(Node lhs, Node rhs) { super(lhs,rhs); }
        String op() { return "=="; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1==i2 && i1.isConstant() ) return TRUE;
            if( i1._max < i2._min || i1._min > i2._max ) return FALSE;
            return BOOL;
        }
        Node copy(Node lhs, Node rhs) { return new EQ(lhs,rhs); }
        Node copyF() { return new EQF(null,null); }
    }
    public static class LT extends BoolNode {
        public LT(Node lhs, Node rhs) { super(lhs,rhs); }
        String op() { return "<" ; }
        public String glabel() { return "&lt;"; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1._max <  i2._min ) return TRUE;
            if( i1._min >= i2._max ) return FALSE;
            return BOOL;
        }
        Node copy(Node lhs, Node rhs) { return new LT(lhs,rhs); }
        Node copyF() { return new LTF(null,null); }
    }
    public static class LE extends BoolNode {
        public LE(Node lhs, Node rhs) { super(lhs,rhs); }
        String op() { return "<="; }
        public String glabel() { return "&lt;="; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1._max <= i2._min ) return TRUE;
            if( i1._min >  i2._max ) return FALSE;
            return BOOL;
        }
        Node copy(Node lhs, Node rhs) { return new LE(lhs,rhs); }
        Node copyF() { return new LEF(null,null); }
    }

    public static class EQF extends EQ { public EQF(Node lhs, Node rhs) { super(lhs,rhs); } boolean doOp(double lhs, double rhs) { return lhs == rhs; } }
    public static class LTF extends LT { public LTF(Node lhs, Node rhs) { super(lhs,rhs); } boolean doOp(double lhs, double rhs) { return lhs <  rhs; } }
    public static class LEF extends LE { public LEF(Node lhs, Node rhs) { super(lhs,rhs); } boolean doOp(double lhs, double rhs) { return lhs <= rhs; } }

}
