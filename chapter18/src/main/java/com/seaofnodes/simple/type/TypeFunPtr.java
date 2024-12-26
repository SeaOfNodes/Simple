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
    private final long _fidxs; // 63 unique functions per signature
    public String _name; // Optional debug function name; only for named single function

    private TypeFunPtr(byte nil, TypeTuple sig, Type ret, long fidxs) {
        super(TFUNPTR,nil);
        assert sig != null;
        _sig = sig;
        _ret = ret;
        _fidxs = fidxs;
    }

    static TypeFunPtr make( byte nil, TypeTuple sig, Type ret, long fidxs ) { return new TypeFunPtr(nil,sig,ret,fidxs).intern(); }
    public static TypeFunPtr make( boolean nil, TypeTuple sig, Type ret ) { return make((byte)(nil ? 3 : 2),sig,ret,-1); }
    @Override TypeFunPtr makeFrom( byte nil ) { return nil==_nil ? this : make(nil,_sig,_ret,_fidxs); }
    public TypeFunPtr makeFrom( int fidx ) { return make(_nil,_sig,_ret,1L<<fidx); }

    // Compute "function indices": FIDX
    private static final HashMap<TypeTuple,Integer> FIDXS = new HashMap<>();
    private static int nextFIDX(TypeTuple sig) {
        Integer i = FIDXS.get(sig);
        int fidx = i==null ? 0 : i;
        FIDXS.put(sig,fidx+1);  // Track count per sig
        assert fidx<64;         // TODO: need a larger FIDX space
        return fidx;            // Return
    }
    public static TypeFunPtr makeFun( TypeTuple sig, Type ret ) {
        return make((byte)2,sig,ret,1L<<nextFIDX(sig));
    }

    public static TypeFunPtr BOT   = make((byte)3,TypeTuple.BOT,Type.BOTTOM,-1);
    public static TypeFunPtr TEST  = make((byte)2,TypeTuple.TEST,TypeInteger.BOT,1);
    public static TypeFunPtr TEST0 = make((byte)3,TypeTuple.TEST,TypeInteger.BOT,3);
    public static TypeFunPtr MAIN  = makeFun(TypeTuple.MAIN,Type.BOTTOM);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST0); ts.add(BOT); ts.add(MAIN); }

    @Override
    Type xmeet(Type t) {
        TypeFunPtr that = (TypeFunPtr) t;
        return TypeFunPtr.make(xmeet0(that),(TypeTuple)_sig.meet(that._sig), _ret.meet(that._ret),_fidxs | that._fidxs);
    }

    @Override
    public TypeFunPtr dual() { return TypeFunPtr.make(dual0(), _sig.dual(), _ret.dual(), ~_fidxs); }

    // RHS is NIL
    @Override public Type meet0() { return _nil==3 ? this : make((byte)3,_sig,_ret,_fidxs); }

    @Override public TypeFunPtr glb() { throw Utils.TODO(); }

    @Override public boolean isConstant() { return _nil==2 && Long.bitCount(_fidxs)==1; }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    public Type arg(int i) { return _sig._types[i]; }
    public long fidxs() { return _fidxs; }

    @Override
    int hash() { return Utils.fold(_sig.hashCode() ^ _ret.hashCode() ^ _fidxs ^ super.hash()); }

    @Override
    boolean eq(Type t) {
        TypeFunPtr ptr = (TypeFunPtr)t; // Invariant
        return _sig == ptr._sig  && _ret == ptr._ret && _fidxs == ptr._fidxs && super.eq(ptr);
    }

    public void setName(String name) {
        assert _name==null || _name.equals(name);
        assert _fidxs > 0 && Long.bitCount(_fidxs) == 1;
        _name = name;
    }

    @Override public String str() { return print(new SB()).toString(); }
    @Override public SB  print(SB sb) { return _print(sb,false); }
    @Override public SB gprint(SB sb) { return _print(sb,true ); }
    private static SB _print(SB sb, boolean g, Type t) { return g ? t.gprint(sb) : t.print(sb); }
    private SB _print(SB sb, boolean g) {
        sb.p(x()).p("{ ");
        if( _name!=null ) sb.p(_name);
        else {
            for( Type t : _sig._types )
                _print(sb,g,t).p(" ");
            _print(sb.p("-> "),g,_ret).p(" #");
            long fidxs = isHigh() ? ~_fidxs : _fidxs;
            String fidx = fidxs==0 ? ""
                : Long.bitCount(fidxs) == 1 ? ""+Long.numberOfTrailingZeros(fidxs)
                : fidxs == -1 ? "ALL"
                : "b"+Long.toBinaryString(fidxs); // Just some function bits
            sb.p(fidx);
        }
        return sb.p("}").p(q());
    }

    // Usage: for( long fidxs=fidxs(); fidxs!=0; fidxs=nextFIDX(fidxs) { int fidxs = Long.numberOfTrailingZeros(fidxs); ... }
    public static long nextFIDX(long fidxs) { return fidxs & (fidxs-1); }

}
