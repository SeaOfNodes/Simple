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
    // Cheesy easy implementation for a small set; 1 bit per unique function
    // within the TypeTuple.  Can be upgraded to a BitSet for larger classes of
    // functions.
    private final long _fidxs; // 64 unique functions per signature

    private TypeFunPtr(byte nil, TypeTuple sig, long fidxs) {
        super(TFUNPTR,nil);
        assert sig != null;
        _sig = sig;
        _fidxs = fidxs;
    }

    static TypeFunPtr make( byte nil, TypeTuple sig, long fidxs ) { return new TypeFunPtr(nil,sig,fidxs).intern(); }
    public static TypeFunPtr make( boolean nil, TypeTuple sig ) { return make((byte)(nil ? 3 : 2),sig,-1); }
    @Override TypeFunPtr makeFrom( byte nil ) { return nil==_nil ? this : make(nil,_sig,_fidxs); }

    // Compute "function indices": FIDX
    private static final HashMap<TypeTuple,Integer> FIDXS = new HashMap<>();
    private static int nextFIDX(TypeTuple sig) {
        Integer i = FIDXS.get(sig);
        int fidx = i==null ? 0 : i;
        FIDXS.put(sig,fidx+1);  // Track count per sig
        assert fidx<64;         // TODO: need a larger FIDX space
        return fidx;            // Return
    }
    public static TypeFunPtr makeFun( TypeTuple sig ) {
        return make((byte)2,sig,1L<<nextFIDX(sig));
    }

    public static TypeFunPtr BOT     = make((byte)3,TypeTuple.SIGBOT,-1);
    public static TypeFunPtr TEST    = make((byte)2,TypeTuple.SIGTEST,1);
    public static TypeFunPtr TEST0   = make((byte)3,TypeTuple.SIGTEST,3);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST0); ts.add(BOT); }

    @Override
    Type xmeet(Type t) {
        TypeFunPtr that = (TypeFunPtr) t;
        return TypeFunPtr.make(xmeet0(that),(TypeTuple)_sig.meet(that._sig), _fidxs | that._fidxs);
    }

    @Override
    public TypeFunPtr dual() { return TypeFunPtr.make(dual0(), _sig.dual(), ~_fidxs); }

    // RHS is NIL
    @Override public Type meet0() { return _nil==3 ? this : make((byte)3,_sig,_fidxs); }

    @Override public TypeFunPtr glb() { throw Utils.TODO(); }

    @Override public boolean isConstant() { return Long.bitCount(_fidxs)==1; }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    @Override
    int hash() { return Utils.fold(_sig.hashCode() ^ _fidxs ^ super.hash()); }

    @Override
    boolean eq(Type t) {
        TypeFunPtr ptr = (TypeFunPtr)t; // Invariant
        return _sig == ptr._sig  && _fidxs == ptr._fidxs && super.eq(ptr);
    }


    @Override public String str() { return print(new SB()).toString(); }
    @Override public SB  print(SB sb) { return _print(sb,false); }
    @Override public SB gprint(SB sb) { return _print(sb,true ); }
    private static SB _print(SB sb, boolean g, Type t) { return g ? t.gprint(sb) : t.print(sb); }
    private SB _print(SB sb, boolean g) {
        sb.p(x()).p("{ ");
        for( int i=1; i<_sig._types.length; i++ )
            _print(sb,g,_sig._types[i]).p(" ");
        _print(sb.p("-> "),g,_sig._types[0]).p(" #");
        long fidxs = isHigh() ? ~_fidxs : _fidxs;
        String fidx = fidxs==0 ? ""
            : Long.bitCount(fidxs) == 1 ? ""+Long.numberOfTrailingZeros(fidxs)
            : fidxs == -1 ? "ALL"
            : "b"+Long.toBinaryString(fidxs); // Just some function bits
        return sb.p(fidx).p("}").p(q());
    }

}
