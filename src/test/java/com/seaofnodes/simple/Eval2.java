package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.IRPrinter;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.*;

public abstract class Eval2 {

    // A mapping from Node ID to evaluation data Object.
    // Data Objsts are a boxed Long or Double; or an Object[] of fields, which
    // themselves are just more Objects.
    private static class Frame {
        private static int UID=1;

        private final IdentityHashMap<Node,Object> _data;
        private Frame _prior;   // Prior frame in this sequence, means when making a new frame we can find old stuff
        private int _refcnt;    // TODO: Use this in a sentence (free/recycle unused stack frames)
        final int _uid = UID++; // Debugging aid
        Frame(Frame prior) {
            _prior = prior;
            if( prior!=null ) prior._refcnt++;
            _data = new IdentityHashMap<>();
            _refcnt = 1;
        }

        // Dec ref count
        void dec() {
            assert _refcnt>0;
            if( --_refcnt == 0 ) {
                Frame prior = _prior;
                _prior = null;
                _data.clear();
                // TODO: FREE LIST?
                if( prior!=null )
                    prior.dec();
            }
        }

        // Put a mapping from Node->Object
        Node put(Node n, Object d) {
            assert d!=null;
            return put0(n,d);
        }
        // Put a mapping from Node->Object?
        Node put0(Node n, Object d) {
            assert _refcnt>0;
            _data.put(n,d);
            return n;
        }
        // Get a mapped Node, perhaps using the prior stack frame crawl
        Object get(Node n) {
            assert _refcnt>0;
            Object d = _data.get(n);
            return d != null ? d
                : _prior == null ? null
                : _prior.get(n);
        }

        // Short debugging print
        public String p() {
            return "#"+_uid + (_prior==null ? "" : " >> " + _prior.p());
        }
        // Long debugging print
        @Override public String toString() {
            String s = "";
            if( _prior != null ) s+= _prior+" << ";
            s += "#"+_uid+"[ ";
            for( Node n : _data.keySet() )
                s += n + ":" + _data.get(n) + ",";
            s += "]";

            return s;
        }
    }

    // Classic Closure; an environment (stack of frames) and an execution point
    private static class Closure {
        // Where to return too; either a CallEnd or a Function
        final CFGNode _cc;

        // Stack frame at the execution point.  Includes Closures which
        // recursively include other stack frames
        private Frame _frame;

        Closure(CFGNode cc, Frame frame) {
            _cc = cc;           // One of Fun, CallEnd or Stop (null)
            _frame = frame;
            assert _frame._refcnt >=0;
            _frame._refcnt++;
        }

        FunNode fun() { return (FunNode)_cc; }
        CallEndNode cend() { return (CallEndNode)_cc; }
        boolean isStop() { return _cc instanceof StopNode; } // or Stop

        //
        void pushFrame() {
            _frame = new Frame(_frame);
            _frame._refcnt++;
        }

        @Override public String toString() {
            return _cc.toString();
        }
    }

    // CodeGen has the linker tables
    public static CodeGen CODE;

    // Current active Frame, to avoid having to pass it everywhere
    public static Frame F;

    public static String eval( CodeGen code, long arg ) { return eval(code,arg,1000); }
    public static String eval( CodeGen code, long arg, int timeout ) {
        if( code._start.uctrl()==null ) return ""; // The empty program
        SB trace = null; // = new SB(); // TRACE, set to null for off, new SB() for on
        // Force local scheduling phase
        code.driver(CodeGen.Phase.LocalSched);
        // Set global, so don't have to pass everywhere
        CODE = code;

        // Timer for when we run too long.  Ticks once per backedge or function call
        int loopCnt = 0;

        // Current value for each Node; boxed Long or Double; or an Object[] of
        // fields, which themselves are just more Objects.
        F = new Frame(null);

        // Start at the START
        CFGNode BB = code._start, prior = null;
        // Return from main hits Stop
        F.put(BB,new Closure(code._stop,F));


        // ------------
        // Evaluate until exit or timeout.  Each outer loop step computes
        // all data nodes under some new Control.
        while( true ) {
            assert BB!=null;
            traceCtrl(BB,trace);
            if( trace!=null ) System.out.println(F.p());

            // Parallel assign Phis
            int i = BB instanceof RegionNode r
                    ? parallelAssignPhis(r,prior,arg,trace)
                    : 0;

            // Compute the data nodes until BB control
            for( ; i < BB.nOuts(); i++ )
                if( !(BB.out(i) instanceof CFGNode) )
                    step(BB.out(i), trace);

            // Find the next control target
            prior = BB;
            BB = BB.uctrl();      // Next unique control target
            switch( prior ) {
            case StartNode start:  F = new Frame(F); break; // Frame for the call to main, as-if Called
            case CallNode  call :  BB = call(call); break;
            case IfNode    iff  :  Object p = val(iff.pred()); BB = iff.cproj( p==null || (p instanceof Long x && x==0L)  ? 1 : 0);  break;
            case ReturnNode ret :  if( clj(ret.rpc()).isStop() ) return exit(ret); else BB = ret(ret); break;
            case FunNode   fun  :  if( loopCnt++ > timeout ) return null;  break; // Timeout
            case LoopNode  loop :  if( loopCnt++ > timeout ) return null;  break; // Timeout
            case CallEndNode cend: break;
            case CProjNode cproj:  break;
            case RegionNode r   :  break;
            default:               throw Utils.TODO();
            }
            assert !BB.isDead();
        }
    }


    // Phi cache; size is limited to number of Phis at a merge point
    private static final Object[] PHICACHE = new Object[256];
    static int parallelAssignPhis(RegionNode r, CFGNode prior, long arg, SB trace) {

        // Compute input path on Phis for this Region
        int path = r._inputs.find( prior );
        // Parameters read from prior frame, Phis from local frame
        Frame frame = (r instanceof FunNode ? F._prior : F);
        boolean isMain = r instanceof FunNode fun && fun.sig().isa(CodeGen.CODE._main);

        // Parallel assign Phis.  First parallel read and cache
        int i;
        for( i = 0; i < r.nOuts(); i++ ) {
            if( !(r.out(i) instanceof PhiNode phi) ) break;
            if( isMain && phi instanceof ParmNode parm && parm._idx==2 )
                PHICACHE[i] = arg; // Reading the initial arguments to main()
            else {
                Node n = phi instanceof ParmNode parm
                    // RPC reads the Call directly;
                    // Parms may not be linked, so read call args directly
                    ? (parm._idx==0 ? prior : prior.in(parm._idx))
                    // Phis read from path input
                    :  phi.in(path);
                PHICACHE[i] = frame.get(n);
            }
        }
        // Parallel assign; might assign before read, so read from cache
        for( int j=0; j < i; j++ )
            traceData(F.put0(r.out(j),PHICACHE[j]),trace);
        // Return point in basic block past last Phi
        return i;
    }


    // Make a call.
    private static CFGNode call( CallNode call ) {
        // Set up the Return PC and return frame (which is just the current frame).
        // Really making a Continuation, but it's just a Closure
        Closure cont = new Closure(call.cend(),F);
        // Set the return continuation into the current frame RPC
        F.put(call, cont );
        // Next Frame to use
        F = new Frame(F);
        // Target function and environment
        return CODE.link(tfp(call.fptr()));
    }

    private static CFGNode ret( ReturnNode ret ) {
        // Fetch return expression value and continuation from current frame
        Object rez = val(ret.expr());
        Closure cont = clj(ret.rpc());
        // Lower Frame reference count; might delete frame
        F.dec();                // Lower ref count
        // Install replacement Frame; set return result over the CallEnd
        F = cont._frame;
        F.put0(cont._cc,rez);   // Set return value into CallEnd
        return cont._cc;        // Return CallEnd as the new control
    }

    private static String exit( ReturnNode ret ) {
        return prettyPrint(ret.expr()._type,val(ret.expr()));
    }

    // Take a worklist step: one data op from the current Control is updated.
    private static void step(Node n, SB trace) {
        if( n instanceof PhiNode ) return;
        // Compute new value
        F.put0(n,compute(n));
        traceData(n,trace);
        // Also do any following projections, which are not in the local schedule otherwise
        if( n instanceof MultiNode )
            for( Node use : n._outputs )
                step(use,trace);
    }

    // From here down shamelessly copied from Evaluator, written by @Xmilia
    private static Object compute( Node n ) {
        return switch( n ) {
        case AddFNode     adf  -> d(adf.in(1)) +  d(adf.in(2));
        case AddNode      add  -> x(add.in(1)) +  x(add.in(2));
        case AndNode      and  -> x(and.in(1)) &  x(and.in(2));
        case BoolNode.EQF eqf  -> d(eqf.in(1)) == d(eqf.in(2)) ? 1L : 0L;
        case BoolNode.LEF lef  -> d(lef.in(1)) <= d(lef.in(2)) ? 1L : 0L;
        case BoolNode.LTF ltf  -> d(ltf.in(1)) <  d(ltf.in(2)) ? 1L : 0L;
        case BoolNode.EQ  eq   -> Objects.equals(val(eq.in(1)), val(eq.in(2))) ? 1L : 0L; // Bool EQ supports pointers, nil, and integers
        case BoolNode.LE  le   -> x(le .in(1)) <= x(le .in(2)) ? 1L : 0L;
        case BoolNode.LT  lt   -> x(lt .in(1)) <  x(lt .in(2)) ? 1L : 0L;
        case CastNode    cast  -> val(cast.in(1));
        case ConstantNode con  -> con(con._con);
        case DivFNode     dvf  -> d(dvf.in(2))==0 ? 0D : d(dvf.in(1)) /  d(dvf.in(2));
        case DivNode      div  -> x(div.in(2))==0 ? 0L : x(div.in(1)) /  x(div.in(2));
        case LoadNode     ld   -> load(ld);
        case MinusFNode   mnf  -> - d(mnf.in(1));
        case MinusNode    sub  -> - x(sub.in(1));
        case MulFNode     mlf  -> d(mlf.in(1)) *  d(mlf.in(2));
        case MulNode      mul  -> x(mul.in(1)) *  x(mul.in(2));
        case NewNode      alloc-> alloc(alloc);
        case NotNode      not  -> x(not.in(1)) == 0 ? 1L : 0L;
        case OrNode       or   -> x(or .in(1)) |  x(or .in(2));
        case ProjNode     proj -> proj._type instanceof TypeMem ? "$mem" : val(proj.in(0));
        case ReadOnlyNode read -> val(read.in(1));
        case SarNode      sar  -> x(sar.in(1)) >> x(sar.in(2));
        case MemMergeNode merge-> "$mem";
        case ShlNode      shl  -> x(shl.in(1)) << x(shl.in(2));
        case ShrNode      shr  -> x(shr.in(1)) >>>x(shr.in(2));
        case StoreNode    st   -> store(st);
        case SubNode      sub  -> x(sub.in(1)) -  x(sub.in(2));
        case SubFNode     sbf  -> d(sbf.in(1)) -  d(sbf.in(2));
        case ToFloatNode  toflt-> (double)x(toflt.in(1));
        case XorNode      xor  -> x(xor.in(1)) ^  x(xor.in(2));
        default -> throw Utils.TODO();
        };
    }

    // Fetch without unboxing, searching up-Frame
    static Object val( Node n ) { return F.get(n); }
    // Fetch and unbox as primitive long
    static long   x( Node n ) { Object d = F.get(n); return d==null ? 0 : (Long)d;  }
    // Fetch and unbox as primitive double
    static double d( Node n ) { Object d = F.get(n); return d==null ? 0 : (Double)d;  }
    // Fetch and unbox a function constant
    static TypeFunPtr tfp(Node n) { return (TypeFunPtr)F.get(n); }
    // Fetch and unbox a closure
    static Closure clj(Node n) { return (Closure)F.get(n); }

    // Java box ints and floats.  Other things can just be null or some informative string
    private static Object con( Type t ) {
        return switch ( t ) {
        case TypeInteger i -> i.isConstant() ? (Long  )i.value() : "INT";
        case TypeFloat   f -> f.isConstant() ? (Double)f.value() : "FLT";
        case TypeFunPtr tfp -> tfp;
        case TypeMem mem -> "MEM";
        case TypeMemPtr tmp -> {
            if( tmp._obj.isAry() ) { // Constant array ptr
                Type elem = tmp._obj.field("[]")._t;
                if( elem instanceof TypeConAry con ) {
                    Object[] xs = new Object[con.len()];
                    for( int i=0; i<con.len(); i++ )
                        xs[i] = con.at8(i);
                    yield xs;
                } else {
                    // Generic constant array, used as a default input to a
                    // function; should never execute.
                    yield new Object[0];
                }

            } else {
                // Generic TMP (since Simple is not currently making actual
                // memory constants), used as a default input to a function;
                // should never execute.
                yield tmp.toString();
            }
        }
        default -> null;
        };
    }

    // Convert array size to array element count
    private static int offToIdx( long off, TypeStruct t) {
        off -= t.aryBase();
        int scale = t.aryScale();
        assert (off & ((1L << scale)-1)) == 0;
        return (int)(off>>scale);
    }

    // Builds and returns a pointer Object
    private static Object alloc(NewNode alloc) {
        TypeStruct type = alloc._ptr._obj;
        if( type.isAry() ) {
            long sz = (Long)val(alloc.in(1));
            long x = offToIdx(sz, type);
            int n = (int)x;
            if( n!=x || n<0 )
                throw new NegativeArraySizeException(""+n);
            Object[] ary = new Object[n]; // Array body
            var elem = type._fields[1]._t;
            if (elem instanceof TypeInteger) {
                Arrays.fill( ary, 0L );
            } else if (elem instanceof TypeFloat) {
                Arrays.fill( ary, 0D );
            } else {
                assert elem instanceof TypeMemPtr || elem instanceof TypeFunPtr;
            }
            return ary;
        }

        int num = type._fields.length;
        Object[] ptr = new Object[num];
        for( int i=0; i<num; i++ )
            ptr[i] = con(type._fields[i]._t.makeZero());
        return ptr;
    }

    private static Object load( LoadNode ld ) {
        Object f = val(ld.ptr());
        // Check for dense constant array
        if( f instanceof TypeMemPtr tmp ) {
            //assert tmp._obj._con != TypeConAry.BOT;
            //if( ld._name.equals("#") )
            //    return (long)tmp._obj._con.len();
            //int idx = offToIdx(x(ld.off()),tmp._obj);
            //return tmp._obj._con.at(idx);
            throw Utils.TODO();
        }
        Object[] fs = (Object[])f;
        if( ld._name.equals("#") )
            return (long)fs.length;
        TypeMemPtr tmp = (TypeMemPtr)ld.ptr()._type;
        int idx = tmp._obj.isAry()
            ? offToIdx(x(ld.off()),tmp._obj)
            : tmp._obj.find(ld._name);
        return fs[idx];
    }

    private static Object store( StoreNode st ) {
        if( st._name.equals("#") )
            return "$mem";      // Eval stores the length in the java object[], no need to set it now
        TypeMemPtr tmp = (TypeMemPtr)st.ptr()._type;
        Object[] fs = (Object[])val(st.ptr());
        int idx = tmp._obj.isAry()
            ? offToIdx(x(st.off()),tmp._obj)
            : tmp._obj.find(st._name);
        Object val = val(st.val());
        fs[idx] = val;
        return "$mem";
    }


    // ------------------------------------------------------------------------
    private static final IdentityHashMap<Object,Object> VISIT = new IdentityHashMap<>();


    static void traceCtrl( CFGNode BB, SB sb ) {
        if( sb==null ) return;
        IRPrinter.printLine(BB,sb.p("--- "));
        System.out.print(sb);
        sb.clear();
    }
    static void traceData( Node n, SB sb ) {
        if( sb==null ) return;
        IRPrinter.printLine(n,sb).unchar();
        _print( n._type, F.get(n), sb.p('\t'), VISIT );
        System.out.println(sb);
        sb.clear();
        VISIT.clear();
    }

    // Use type to print x as a string.  Uses a new VISIT table so the debugger
    // doesn't try to reuse VISIT if stopping mid-print.  The trace code reuses
    // VISIT endlessly for some minor speed hope
    static String prettyPrint( Type t, Object x ) { return _print(t,x,new SB(), new IdentityHashMap<>()).toString(); }
    static SB _print( Type t, Object x, SB sb, IdentityHashMap<Object,Object> visit ) {
        if( x instanceof String s ) return sb.p(s);
        if( x == null ) return sb.p("null");
        return switch( t ) {
        case TypeInteger i -> sb.p(  (Long)x);
        case TypeFloat   f -> sb.p((Double)x);
        case TypeMemPtr tmp -> {
            if( visit.containsKey(x) ) yield sb.p("$cyclic");
            visit.put(x,x);
            assert !tmp.isFRef();

            Object[] xs = (Object[])x; // Array of fields
            if( tmp._obj.isAry() ) {
                Type elem = tmp._obj._fields[1]._t;
                if( elem == TypeInteger.U8 ) {
                    // Shortcut u8[] as a String
                    for( Object o : xs )
                        sb.p((char)(long)(Long)o);
                } else {
                    // Array of elements
                    elem.print(sb).p("[ ");
                    if( elem instanceof TypeConAry ) elem = TypeInteger.ZERO;
                    for( Object o : xs )
                        _print( elem, o, sb, visit ).p( "," );
                    sb.unchar().p("]");
                }
            } else {
                sb.p(tmp._obj._name).p("{");
                Field[] flds = tmp._obj._fields;
                for( int i=0; i<flds.length; i++ )
                    _print( flds[i]._t,xs[i], sb.p(flds[i]._fname).p("="), visit ).p(",");
                sb.unchar().p("}");
            }
            yield sb;
        }
        case TypeFunPtr tfp -> sb.p(x.toString());
        case TypeRPC rpc -> sb.p(x.toString());
        case TypeMem mem -> sb.p("$mem");
        case TypeTuple tt -> {
            if( tt._types.length>1 && tt._types[1] instanceof TypeMemPtr )
                // Assume a NewNode
                yield _print( tt._types[1], x, sb, visit );
            throw Utils.TODO();
        }
        case Type tt -> {
            if( tt == Type.NIL ) yield sb.p("null");
            throw Utils.TODO();
        }
        };
    }
}
