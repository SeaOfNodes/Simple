package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import static com.seaofnodes.simple.type.TypeInteger.*;

abstract public class BoolNode extends Node implements ModeNode {

    // Mode is 0 for unknown, 1 for int, 2 for flt
    byte _mode;

    public BoolNode(Node lhs, Node rhs) { super(null, lhs, rhs); }
    public BoolNode(Node lhs, Node rhs, byte mode) { super(null, lhs, rhs); _mode = mode; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> anodes) {
        baos.packed1(_mode);
    }

    abstract public String op(); // String opcode name

    @Override
    public String glabel() { return op(); }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(op()), visited);
        return sb.append(")");
    }

    @Override
    public TypeInteger compute() {
        Type t1 = in(1)._type;
        Type t2 = in(2)._type;
        if( t1==null || t2==null )
            return BOOL;
        // Exactly equals?
        if( t1.isHigh() || t2.isHigh() )
            return (TypeInteger)BOOL.dual();
        if( in(1)==in(2) )
            // LT fails, both EQ and LE succeed
            return this instanceof LT ? FALSE : TRUE;
        byte mode = _mode==0 ? ArithNode.mode(t1,t2) : _mode;
        if( mode==0 )
            return BOOL;
        // Handle integer compares
        if( mode==1 ) {
            if( t1 instanceof TypeInteger i1 &&
                t2 instanceof TypeInteger i2 )
                return doOp(i1,i2);
            return BOOL;
        }
        // Floating compare.  Mixed inputs are converted in idealize().
        if( t1 instanceof TypeFloat f1 &&
            t2 instanceof TypeFloat f2 &&
            f1.isConstant() && f2.isConstant() )
            return doOp(f1.value(), f2.value()) ? TRUE : FALSE;
        return BOOL;
    }

    TypeInteger doOp(TypeInteger t1, TypeInteger t2) { throw Utils.TODO(); }
    boolean doOp(double lhs, double rhs) { throw Utils.TODO(); }
    public boolean isFloat() { assert _mode!=0; return _mode==2; }
    @Override public byte mode() { return _mode; }
    @Override public Node setMode(byte mode) {
        assert _mode==0 && (mode==1 || mode==2);
        unlock();               // _mode participates in GVN hash and equality
        _mode = mode;
        return this;
    }

    @Override
    public Node idealize() {
        // Compare of same
        if( in(1)==in(2) )
            return this instanceof LT ? CodeGen.CODE.ZERO : ConstantNode.make(TRUE);
        // Can we decide int vs flt?
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1==null || t2==null )
            return null;
        if( _mode==0 ) {
            byte mode = ArithNode.mode(t1,t2);
            if( mode!=0 ) return setMode(mode).init();
        }
        if( _mode==2 ) {
            if( t1 instanceof TypeInteger ) return copy(new ToFloatNode(in(1)).peephole(),in(2));
            if( t2 instanceof TypeInteger ) return copy(in(1),new ToFloatNode(in(2)).peephole());
        }


        // Equals pushes constant to the right; 5==X becomes X==5.
        if( this instanceof EQ ) {
            if( !(in(2) instanceof ConstantNode) ) {
                // con==noncon becomes noncon==con
                if( in(1) instanceof ConstantNode || in(1)._nid > in(2)._nid )
                    // Equals sorts by NID otherwise: non.high == non.low becomes non.low == non.high
                    return new EQ(in(2),in(1));
            }
            // Equals X==0 becomes a !X
            if( (in(2)._type == ZERO || in(2)._type == Type.NIL) )
                return new NotNode(in(1));
        }

        // Do we have ((phi cons) cmp con) ?
        // Do we have ((phi cons) cmp (phi cons)) ?
        // Push the compare up through the phi: (phi con0 cmp con, con1 cmp con...)
        Node phicon = AddNode.phiCon(this,this instanceof EQ);
        if( phicon!=null ) return phicon;

        return null;
    }

    @Override public boolean eq( Node n ) { return _mode==((BoolNode)n)._mode; }
    @Override public int hash() { return _mode; }
    @Override public Parser.ParseException err() {
        return ArithNode.nodeErr(this,null,op(),_mode,!(this instanceof ULT));
    }

    public static class EQ extends BoolNode {
        public EQ(Node lhs, Node rhs) { super(lhs,rhs); }
        public EQ(Node lhs, Node rhs, byte mode) { super(lhs,rhs, mode); }
        @Override public Tag serialTag() { return Tag.EQ; }
        public String op() { return "=="; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1==i2 && i1.isConstant() ) return TRUE;
            if( i1._max < i2._min || i1._min > i2._max ) return FALSE;
            return BOOL;
        }
        @Override boolean doOp(double lhs, double rhs) { return lhs==rhs; }
        Node copy(Node lhs, Node rhs) { return new EQ(lhs,rhs,_mode); }
    }

    public static class NE extends BoolNode {
        public NE(Node lhs, Node rhs) { super(lhs,rhs); }
        public NE(Node lhs, Node rhs, byte mode) { super(lhs,rhs,mode); }
        @Override public Tag serialTag() { return Tag.NE; }
        public String op() { return "!="; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1==i2 && i1.isConstant() ) return TRUE;
            if( i1._max < i2._min || i1._min > i2._max ) return FALSE;
            return BOOL;
        }
        @Override boolean doOp(double lhs, double rhs) { return lhs!=rhs; }
        Node copy(Node lhs, Node rhs) { return new NE(lhs,rhs,_mode); }
    }

    public static class LT extends BoolNode {
        public LT(Node lhs, Node rhs) { super(lhs,rhs); }
        public LT(Node lhs, Node rhs, byte mode) { super(lhs,rhs, mode); }
        @Override public Tag serialTag() { return Tag.LT; }
        public String op() { return "<" ; }
        public String glabel() { return "&lt;"; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1._max <  i2._min ) return TRUE;
            if( i1._min >= i2._max ) return FALSE;
            return BOOL;
        }
        @Override boolean doOp(double lhs, double rhs) { return lhs<rhs; }
        Node copy(Node lhs, Node rhs) { return new LT(lhs,rhs,_mode); }
    }

    public static class LE extends BoolNode {
        public LE(Node lhs, Node rhs) { super(lhs,rhs); }
        public LE(Node lhs, Node rhs, byte mode) { super(lhs,rhs,mode); }
        @Override public Tag serialTag() { return Tag.LE; }
        public String op() { return "<="; }
        public String glabel() { return "&lt;="; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( i1._max <= i2._min ) return TRUE;
            if( i1._min >  i2._max ) return FALSE;
            return BOOL;
        }
        @Override boolean doOp(double lhs, double rhs) { return lhs<=rhs; }
        Node copy(Node lhs, Node rhs) { return new LE(lhs,rhs,_mode); }
    }

    // Unsigned less that, for range checks.  Not directly user writable.
    public static class ULT extends BoolNode {
        public ULT(Node lhs, Node rhs) { super(lhs,rhs,(byte)1); }
        public ULT(Node lhs, Node rhs, byte mode) {
            super(lhs,rhs,(byte)1);
            assert mode==1;
        }
        @Override public Tag serialTag() { return Tag.ULT; }
        public String op() { return "u<" ; }
        public String glabel() { return "u&lt;"; }
        TypeInteger doOp(TypeInteger i1, TypeInteger i2) {
            if( Long.compareUnsigned(i1._max,i2._min) <  0 ) return TRUE;
            if( Long.compareUnsigned(i1._min,i2._max) >= 0 ) return FALSE;
            return BOOL;
        }
        Node copy(Node lhs, Node rhs) { return new ULT(lhs,rhs); }
    }
}
