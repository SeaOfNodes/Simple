package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents a Pointer to a function.
 * <p></p>
 * Functions have argument types and a return type, making a function
 * signature.  Within a signature there can be many instances of functions, and
 * each function is labeled with a small integer constant.  A TFP can represent
 * a single signature and a set of functions; the set might contain only 1
 * function or the zeroth function (the null ptr).
 * <p></p>
 * Functions with different signatures cannot be mixed, and will result in a
 * bottom function type, which can only be null-checked.
 */
public class TypeFunPtr extends TypeNil {
    // A TypeFunPtr is Signature and a set of functions.
    public final TypeTuple _sig;
    public final Type _ret;
    // Cheesy easy implementation for a small set; 1 bit per unique function
    // within the TypeTuple.  Can be upgraded to a BitSet for larger classes of
    // functions.  Negative means "these 63 concrete bits plus infinite unknown more"
    public final long _fidxs; // 63 unique functions per signature

    private TypeFunPtr(byte nil, TypeTuple sig, Type ret, long fidxs) {
        super(TFUNPTR,nil);
        assert sig != null;
        _sig = sig;
        _ret = ret;
        _fidxs = fidxs;
    }

    public static TypeFunPtr make( byte nil, TypeTuple sig, Type ret, long fidxs ) { return new TypeFunPtr(nil,sig,ret,fidxs).intern(); }
    public static TypeFunPtr make( boolean nil, TypeTuple sig, Type ret ) { return make((byte)(nil ? 3 : 2),sig,ret,-1); }
    @Override TypeFunPtr makeFrom( byte nil ) { return  nil ==_nil   ? this : make(  nil,_sig,_ret,   _fidxs); }
    public TypeFunPtr makeFrom( Type ret ) { return     ret ==_ret   ? this : make( _nil,_sig, ret,   _fidxs); }
    public TypeFunPtr makeFrom( int fidx ) { return make((byte)2, _sig,_ret,1L<<fidx ); }

    public static TypeFunPtr BOT   = make((byte)3,TypeTuple.BOT,Type.BOTTOM,-1);
    public static TypeFunPtr TEST  = make((byte)2,TypeTuple.TEST,TypeInteger.BOT,1);
    public static TypeFunPtr TEST0 = make((byte)3,TypeTuple.TEST,TypeInteger.BOT,3);
    public static TypeFunPtr MAIN  = make((byte)3,TypeTuple.MAIN,TypeInteger.BOT,-1);
    public static TypeFunPtr CALLOC= make((byte)3,TypeTuple.CALLOC,TypeMemPtr.BOT,-1);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST0); ts.add(BOT); ts.add(MAIN); }

    @Override
    Type xmeet(Type t) {
        TypeFunPtr that = (TypeFunPtr) t;
        return TypeFunPtr.make(xmeet0(that),(TypeTuple)_sig.meet(that._sig), _ret.meet(that._ret), _fidxs | that._fidxs);
    }

    @Override
    public TypeFunPtr dual() { return TypeFunPtr.make(dual0(), _sig.dual(), _ret.dual(), ~_fidxs); }

    // RHS is NIL; do not deep-dual when crossing the centerline
    @Override public Type meet0() { return _nil==3 ? this : make((byte)3,_sig,_ret,_fidxs); }

    @Override public TypeFunPtr glb() { return make((byte)3,_sig,_ret,-1L); }

    @Override public boolean isHigh    () { return _nil <= 1 || (_nil==2 && _fidxs==0); }
    @Override public boolean isConstant() { return (_nil==2 && Long.bitCount(_fidxs)==1) || (_nil==3 && _fidxs==0); }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    public Type arg(int i) { return _sig._types[i]; }
    public long fidxs() { return _fidxs; }
    public Type ret() { return _ret; }
    public int nargs() { return _sig._types.length; }
    public int fidx() { assert Long.bitCount(_fidxs)==1; return Long.numberOfTrailingZeros(_fidxs); }

    @Override
    int hash() { return Utils.fold(_sig.hashCode() ^ _ret.hashCode() ^ _fidxs ^ super.hash()); }

    @Override
    boolean eq(Type t) {
        TypeFunPtr ptr = (TypeFunPtr)t; // Invariant
        return _sig == ptr._sig  && _ret == ptr._ret && _fidxs == ptr._fidxs && super.eq(ptr);
    }

    @Override public String str() { return print(new SB()).toString(); }
    @Override public SB  print(SB sb) { return _print(sb,false,true); }
    public SB print(SB sb, boolean n) { return _print(sb,false,n); }
    @Override public SB gprint(SB sb) { return _print(sb,true ,true); }
    private static SB _print(SB sb, boolean g, Type t) { return g ? t.gprint(sb) : t.print(sb); }
    private SB _print(SB sb, boolean g, boolean n) {
        sb.p(x()).p("{ ");
        if( _sig._types!=null )
            for( Type t : _sig._types )
                _print(sb,g,t).p(" ");
        _print(sb.p(g ? "&rarr; " : "-> "),g,_ret).p(" #");
        if( isHigh() ) sb.p("~");
        long fidxs = isHigh() ? ~_fidxs : _fidxs;
        String fidx = fidxs==0 ? ""
            : Long.bitCount(fidxs) == 1 ? ""+Long.numberOfTrailingZeros(fidxs)
            : fidxs == -1 ? "ALL"
            : "b"+Long.toBinaryString(fidxs); // Just some function bits
        return sb.p(fidx).p("}").p(q());
    }

    // Usage: for( long fidxs=fidxs(); fidxs!=0; fidxs=nextFIDX(fidxs) { int fidxs = Long.numberOfTrailingZeros(fidxs); ... }
    public static long nextFIDX(long fidxs) { return fidxs & (fidxs-1); }

}
