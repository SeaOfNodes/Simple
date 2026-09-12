package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

public abstract class ArithNode extends Node implements ModeNode {
    // Source location for late reported errors
    Parser.Lexer _loc;
    // Mode is 0 for unknown, 1 for int, 2 for flt
    byte _mode;

    public ArithNode( Parser.Lexer loc, Node lhs, Node rhs) { super(null, lhs, rhs); _loc = loc; }
    public ArithNode( Parser.Lexer loc, Node lhs, Node rhs, byte mode) { this(loc,lhs,rhs); _mode=mode; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, IdentityHashMap<Node,Integer> anodes) {
        baos.packed1(_mode);
    }
    @Override public String glabel() { return op(); }
    abstract String op();

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(op()), visited);
        return sb.append(")");
    }

    abstract long doOp(long x, long y);
    abstract TypeInteger doOp(TypeInteger x, TypeInteger y);
    double doOp(double x, double y) { throw Utils.TODO(); }

    @Override public byte mode() { return _mode; }
    @Override public Node setMode(byte mode) {
        assert _mode==0 && (mode==1 || mode==2);
        unlock();               // _mode participates in GVN hash and equality
        _mode = mode;
        return this;
    }
    static byte mode( Type t1, Type t2 ) {
        if( t1 instanceof TypeFloat   || t2 instanceof TypeFloat   )  return 2;
        if( t1 instanceof TypeInteger && t2 instanceof TypeInteger )  return 1;
        if( t1 instanceof TypeMemPtr  && t2 instanceof TypeMemPtr  )  return 1;
        if( t1 instanceof TypeFunPtr  && t2 instanceof TypeFunPtr  )  return 1;
        return 0;
    }

    // Check that a settled mode agrees with all data inputs.  Takes the Node
    // instead of a varargs list both to avoid allocation and to keep this ready
    // to become shared mode-aware Node behavior later.
    static Parser.ParseException nodeErr(Node n, Parser.Lexer loc, String op, byte mode, boolean allowFloat) {
        Type bad = null;
        for( int i=1; i<n.nIns(); i++ ) {
            Type t = n.in(i)._type;
            if( t==null )
                return null;
            if( t.isHigh() )
                return null;
            // A weak input is not evidence of a bad mode while the graph is
            // still being built.  Loop Phis commonly await their backedge.
            if( CodeGen.CODE._phase.ordinal() < CodeGen.Phase.Opto.ordinal() && t == Type.BOTTOM )
                return null;
            bad = switch( t ) {
            case TypeInteger ti -> mode==1 ? null : t;
            case TypeMemPtr  ti -> mode==1 ? null : t;
            case TypeFunPtr  ti -> mode==1 ? null : t;
            case TypeFloat   tf -> mode==2 ? null : t;
            default -> t;
            };
            if( bad != null ) break;
        }
        boolean unsupportedFloat = false;
        if( !allowFloat )
            for( int i=1; i<n.nIns(); i++ )
                if( n.in(i)._type instanceof TypeFloat ) {
                    bad = n.in(i)._type;
                    unsupportedFloat = true;
                    break;
                }
        if( bad == null )
            return null;
        if( unsupportedFloat || n instanceof BoolNode )
            return Parser.error("Cannot '"+op+"' "+bad,loc);
        if( n.nIns()==3 )
            return Parser.error("Cannot "+n.in(1)._type+" "+op+" "+n.in(2)._type,loc);
        return Parser.error("Cannot '"+op+"' "+bad,loc);
    }

    // Arithmetic subclasses which have a floating-point interpretation override.
    boolean allowFloat() { return false; }

    // Generic airthmetic op math: high returns high; low returns low; 2
    // constants fold; only 2 non-constants call specialized math.
    @Override
    public final Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1==null || t2==null )
            return Type.TOP;
        if( t1.isHigh() || t2.isHigh() )
            return _mode==0 ? Type.TOP :
                _mode==1 ? TypeInteger.TOP :
                TypeFloat.F64.dual();
        byte mode = _mode==0 ? mode(t1,t2) : _mode;
        if( mode==0 )
            return Type.BOTTOM;
        if( mode==1 ) {
            if( t1 instanceof TypeInteger x &&
                t2 instanceof TypeInteger y )
                return x.isConstant() && y.isConstant()
                    ? con(x,y)
                    : doOp(x,y).makeWide( (byte)Math.max(x._widen,y._widen) );
            return TypeInteger.BOT;
        }
        // FP handling
        if( t1 instanceof TypeFloat f1 &&
            t2 instanceof TypeFloat f2 ) {
            if( f1.isConstant() && f2.isConstant() )
                return TypeFloat.constant(doOp(f1.value(),f2.value()));
            return t1.meet(t2);
        }
        return TypeFloat.F64;
    }

    private TypeInteger con( Type t1, Type t2 ) {
        return TypeInteger.constant( doOp( ((TypeInteger)t1).value(), ((TypeInteger)t2).value()) );
    }

    // Check for "op(Phi(con,x),Phi(con,y))" and push-up through the Phi.
    // Note that this is the exact reverse of Phi pulling a common op down
    // to reduce total op-count.  We don't get in an endless push-up
    // push-down peephole cycle because the constants all fold first.
    // Returns "Phi(op(con,con),op(x,y))".

    // Expected to be called after other ideal checks are done.
    @Override public Node idealize() {
        Type t1 = in(1)._type, t2 = in(2)._type;

        // Can we decide int vs flt?
        if( _mode==0 ) {
            byte mode = mode(t1,t2);
            if( mode!=0 ) return setMode(mode).init();
        }
        if( _mode==2 ) {
            if( t1 instanceof TypeInteger ) return copy(new ToFloatNode(in(1)).peephole(),in(2));
            if( t2 instanceof TypeInteger ) return copy(in(1),new ToFloatNode(in(2)).peephole());
        }

        if( in(1) instanceof PhiNode lhs &&
            in(2) instanceof PhiNode rhs &&
            lhs.nIns() >= 2 && !lhs.inProgress() &&
            // Disallow with self-looping phi; these will collapse
            (lhs.nIns() > 2 && lhs.in(2)!=lhs && rhs.nIns() > 2 && rhs.in(2)!=rhs) ) {
            Node r = lhs.region();
            if( r==rhs.region() && !(r instanceof FunNode) &&
                // For loops, the backedge *must* fold away, lest we unroll forever
                (!(r instanceof LoopNode) || isCon(lhs,rhs,2)) ) {
                // Profit check: only 1 instance of `this` will remain, all the
                // others will fold to constants.  For loops, *all* must fold away
                int cnt=0;
                for( int i=1; i<lhs.nIns(); i++ )
                    if( isCon(lhs,rhs,i) )
                        cnt++;
                if( lhs.nIns()-1 - cnt <= 1 ) {
                    // Profit!  Push up-phi and fold
                    Node[] ns = new Node[lhs.nIns()];
                    ns[0] = lhs.in(0);
                    for( int i=1; i<lhs.nIns(); i++ ) {
                        ns[i] = isCon(lhs,rhs,i)
                            ? ConstantNode.make(con(lhs.in(i)._type, rhs.in(i)._type)).peephole()
                            : copy(lhs.in(i), rhs.in(i)).peephole();
                    }
                    String label = lhs._label==rhs._label ? lhs._label : lhs._label + rhs._label;
                    return new PhiNode(label, ns).peephole();
                }
            }
        }

        return null;
    }

    static boolean isCon( PhiNode lhs, PhiNode rhs, int i ) {
        return lhs.in(i)._type.isConstant() && rhs.in(i)._type.isConstant();
    }


    static boolean overflow( long x, long y ) {
        if(    (x ^    y   ) < 0 ) return false; // unequal signs, never overflow
        return (x ^ (x + y)) < 0; // sum has unequal signs, so overflow
    }

    @Override public Parser.ParseException err() {
        return nodeErr(this,_loc,op(),_mode,allowFloat());
    }

    @Override public boolean eq( Node n ) { return _mode==((ArithNode)n)._mode; }
    @Override public int hash() { return _mode; }
}
