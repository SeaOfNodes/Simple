package com.seaofnodes.simple;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.node.*;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;

public abstract class Eval2 {

    private static Object[] DATA;
    public static String eval( CodeGen code, long arg ) { return eval(code,arg,1000); }
    public static String eval( CodeGen code, long arg, int timeout ) {
        if( code._phase.ordinal() < CodeGen.Phase.TypeCheck .ordinal() )  code.typeCheck();
        if( code._phase.ordinal() < CodeGen.Phase.Schedule  .ordinal() )  code.GCM();
        if( code._phase.ordinal() < CodeGen.Phase.LocalSched.ordinal() )  code.localSched();


        // Timer for when we run too long.  Ticks once per backedge or function call
        int loopCnt = 0;

        // Current value for each Node; boxed Long or Double; or an Object[] of
        // fields, which themselves are just more Objects.
        DATA = new Object[Node.UID()];

        // Start at the START
        CFGNode C = code._start, prior = null;

        // Phi cache; size is limited to number of Phis at a merge point
        Object[] phiCache = new Object[256];

        // Evaluate until we return or timeout.  Each outer loop step computes
        // all data nodes under some new Control.
        SB sb = null; //new SB(); // TRACE
        IdentityHashMap visit = new IdentityHashMap();
        while( true ) {
            // Compute input path on Phis for this Region,
            // or path is invalid for non-Region
            int path = C instanceof RegionNode r ? r._inputs.find( prior ) : -1;

            // Run the worklist dry, computing data nodes under control of C
            if( sb!=null ) { IRPrinter.printLine(C,sb.p("--- ")); System.out.print(sb); sb.clear();  }

            // Parallel assign Phis.  First parallel read and cache
            int i;
            for( i = 0; i < C.nOuts(); i++ )
                if( C.out(i) instanceof PhiNode phi )
                    phiCache[i] = isArg(phi) ? arg : val(phi.in(path));
                else break;
            // Parallel assign; might assign before read, so read from cache
            for( int j=0; j < i; j++ )
                DATA[C.out(j)._nid] = phiCache[j];
            // Now compute the rest of the nodes
            for( ; i < C.nOuts(); i++ )
                step(C.out(i), sb, visit);

            // Find the next control target
            prior = C;
            C = C.uctrl();      // Next unique control target
            switch( prior ) {
            case  FunNode fun :    if( loopCnt++ > timeout ) return null;  break; // Timeout
            case LoopNode loop:    if( loopCnt++ > timeout ) return null;  break; // Timeout
            case IfNode iff:       C = iff.cproj( val(iff.pred())==null || (val(iff.pred()) instanceof Long x && x==0L)  ? 1 : 0);  break;
            case RegionNode r:     break;
            case CProjNode cproj:  break;
                // TODO: Need a Call-stack here
            case ReturnNode ret:   return prettyPrint(ret.expr()._type,val(ret.expr()));
            default:               throw Utils.TODO();
            }
        }

    }

    private static boolean isArg( PhiNode phi ) {
        // Arg#2 into main.
        // TODO: Needs some love for recursive main
        return phi instanceof ParmNode parm && parm.fun()._sig==TypeFunPtr.MAIN && parm._idx==2;
    }

    // Take a worklist step: one data op from the current Control is updated.
    private static void step(Node n, SB sb, IdentityHashMap visit) {
        // Only data nodes
        if( n instanceof CFGNode )  return;
        // Compute new value
        DATA[n._nid] = compute(n);
        // Trace
        if( sb!=null ) {
            IRPrinter.printLine(n,sb).unchar();
            _print( n._type, DATA[n._nid], sb.p('\t'), visit );
            System.out.println(sb);
            sb.clear();
            visit.clear();
        }
        // Also do any following projections, which are not in the local schedule otherwise
        if( n instanceof MultiNode )
            for( Node use : n._outputs )
                step(use,sb,visit);
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
        case MulNode      mul  -> x(mul.in(1)) *  x(mul.in(2));
        case NewNode      alloc-> alloc(alloc);
        case NotNode      not  -> x(not.in(1)) == 0 ? 1L : 0L;
        case OrNode       or   -> x(or .in(1)) |  x(or .in(2));
        case ProjNode     proj -> proj._type instanceof TypeMem ? "$mem" : val(proj.in(0));
        case ReadOnlyNode read -> val(read.in(1));
        case SarNode      sar  -> x(sar.in(1)) >> x(sar.in(2));
        case ScopeMinNode merge-> "$mem";
        case ShlNode      shl  -> x(shl.in(1)) << x(shl.in(2));
        case ShrNode      shr  -> x(shr.in(1)) >>>x(shr.in(2));
        case StoreNode    st   -> store(st);
        case SubNode      sub  -> x(sub.in(1)) -  x(sub.in(2));
        case ToFloatNode  toflt-> (double)x(toflt.in(1));
        case XorNode      xor  -> x(xor.in(1)) ^  x(xor.in(2));
        default -> throw Utils.TODO();
        };
    }

    // Fetch without unboxing
    static Object val( Node n ) { return DATA[n._nid]; }
    // Fetch and unbox as primitive long
    static long   x( Node n ) { return DATA[n._nid]==null ? 0 : (Long)  DATA[n._nid];  }
    // Fetch and unbox as primitive double
    static double d( Node n ) { return DATA[n._nid]==null ? 0 : (Double)DATA[n._nid];  }
    // Java box ints and floats.  Other things can just be null or some informative string
    private static Object con( Type t ) {
        if( t instanceof TypeInteger i )
            return i.isConstant() ? (Long)i.value() : "INT";
        if( t instanceof TypeFloat f )
            return f.isConstant() ? (Double)f.value() : "FLT";
        //
        return null;
    }

    // Convert array size to array element count
    private static int offToIdx( long off, TypeStruct t) {
        off -= t.aryBase();
        int scale = t.aryScale();
        long mask = (1L << scale)-1;
        assert (off & mask) == 0;
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
            var elem = type._fields[1]._type;
            if (elem instanceof TypeInteger) {
                Arrays.fill( ary, 0L );
            } else if (elem instanceof TypeFloat) {
                Arrays.fill( ary, 0D );
            } else {
                assert elem instanceof TypeMemPtr;
            }
            // Length value
            //body[0] = vall(alloc.in(2+2));
            return ary;
        }

        int num = type._fields.length;
        Object[] ptr = new Object[num];
        for( int i=0; i<num; i++ )
            ptr[i] = val(alloc.in(2+i+num));
        return ptr;
    }

    private static Object load( LoadNode ld ) {
        TypeMemPtr tmp = (TypeMemPtr)ld.ptr()._type;
        Object[] fs = (Object[])val(ld.ptr());
        if( ld._name.equals("#") )
            return (long)fs.length;
        int idx = tmp._obj.isAry()
            ? offToIdx(x(ld.off()),tmp._obj)
            : tmp._obj.find(ld._name);
        return fs[idx];
    }

    private static Object store( StoreNode st ) {
        TypeMemPtr tmp = (TypeMemPtr)st.ptr()._type;
        Object[] fs = (Object[])val(st.ptr());
        int idx = tmp._obj.isAry()
            ? offToIdx(x(st.off()),tmp._obj)
            : tmp._obj.find(st._name);
        Object val = val(st.val());
        fs[idx] = val;
        return "$mem";
    }


    // Use type to print x as a string
    static String prettyPrint( Type t, Object x ) { return _print(t,x,new SB(), new IdentityHashMap()).toString(); }
    static SB _print( Type t, Object x, SB sb, IdentityHashMap visit ) {
        if( x instanceof String s ) return sb.p(s);
        if( x == null ) return sb.p("null");
        return switch( t ) {
        case TypeInteger i -> sb.p(  (Long)x);
        case TypeFloat   f -> sb.p((Double)x);
        case TypeMemPtr tmp -> {
            if( visit.containsKey(x) ) yield sb.p("$cyclic");
            visit.put(x,x);
            // Since never can close the type cycle, without cyclic types, have
            // to handle FRefs even here
            if( tmp.isFRef() )
                tmp = tmp.makeFrom(((TypeMemPtr)Parser.TYPES.get(tmp._obj._name))._obj);

            Object[] xs = (Object[])x; // Array of fields
            if( tmp._obj.isAry() ) {
                Type elem = tmp._obj._fields[1]._type;
                if( elem == TypeInteger.U8 ) {
                    // Shortcut u8[] as a String
                    for( Object o : xs )
                        sb.p((char)(long)(Long)o);
                } else {
                    // Array of elements
                    elem.print(sb).p("[ ");
                    for( Object o : xs )
                        _print( elem, o, sb, visit ).p( "," );
                    sb.unchar().p("]");
                }
            } else {
                sb.p(tmp._obj._name).p("{");
                Field[] flds = tmp._obj._fields;
                for( int i=0; i<flds.length; i++ )
                    _print( flds[i]._type,xs[i], sb.p(flds[i]._fname).p("="), visit ).p(",");
                sb.unchar().p("}");
            }
            yield sb;
        }
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
