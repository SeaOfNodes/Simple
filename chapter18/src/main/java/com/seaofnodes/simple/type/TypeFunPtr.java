package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a Pointer to a function.
 *
 * Functions have argument types and a return type, making a function
 * signature.  Within a signature there can be many instances of functions, and
 * each function is labeled with a small integer constant.  A TFP can represent
 * a single signature and a set of functions; the set might contain only 1
 * function or the zeroth function (the null ptr).
 *
 * Functions with different signatures cannot be mixed, and will result in a
 * bottom function type, which can only be null-checked.
 */
public class TypeFunPtr extends Type {
    // A TypeFunPtr is Signature and a set of functions.
    public final TypeTuple _sig;
    // Cheesy easy implementation for a small set; 1 bit per unique function
    // within the TypeTuple.  Can be upgraded to a BitSet for larger classes of
    // functions.
    private final long _fidxs; // 0 bit for null, and up to 63 unique functions per signature

    private TypeFunPtr(TypeTuple sig, long fidxs) {
        super(TFUNPTR);
        assert sig != null;
        _sig = sig;
        _fidxs = fidxs;
    }

    public static TypeFunPtr make( TypeTuple sig, long fidxs ) { return new TypeFunPtr(sig,fidxs).intern(); }
    // Make a TFP with all functions
    public static TypeFunPtr make( TypeTuple sig, boolean nil ) { return new TypeFunPtr(sig,-2|(nil ? 1 : 0)).intern(); }

    public static TypeFunPtr BOT     = make(TypeTuple.SIGBOT,-1);
    public static TypeFunPtr TOP     = BOT.dual();
    public static TypeFunPtr TEST    = make(TypeTuple.SIGTEST,2);
    public static TypeFunPtr TEST0   = make(TypeTuple.SIGTEST,3);
    public static TypeFunPtr NULLPTR = make(TypeTuple.SIGTOP,1);
    public static TypeFunPtr VOIDPTR = NULLPTR.dual();
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST0); ts.add(NULLPTR); ts.add(BOT); }

    @Override
    Type xmeet(Type t) {
        TypeFunPtr that = (TypeFunPtr) t;
        return TypeFunPtr.make((TypeTuple)_sig.meet(that._sig), _fidxs | that._fidxs);
    }

    @Override
    public TypeFunPtr dual() { return TypeFunPtr.make(_sig.dual(), ~_fidxs); }

    @Override public TypeFunPtr glb() { throw Utils.TODO(); }
    // Is forward-reference
    @Override public boolean isFRef() { return false; }

    @Override public boolean isHigh() { return this==TOP; }
    @Override public boolean isConstant() { return Long.bitCount(_fidxs)==1; }
    @Override public boolean isHighOrConst() { return Long.bitCount(_fidxs)<=1; }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    @Override
    int hash() { return Utils.fold(_sig.hashCode() ^ _fidxs); }

    @Override
    boolean eq(Type t) {
        TypeFunPtr ptr = (TypeFunPtr)t; // Invariant
        return _sig == ptr._sig  && _fidxs == ptr._fidxs;
    }


    @Override public String str() {
        if( this== NULLPTR) return "null";
        if( this== VOIDPTR) return "*void";
        return print(new SB()).toString();
    }

    // [void,name,MANY]*[,?]
    @Override public SB print(SB sb) {
        if( this== NULLPTR) return sb.p("null");
        if( this== VOIDPTR) return sb.p("*void");
        sb.p("{ ");
        for( int i=1; i<_sig._types.length; i++ )
            _sig._types[i].print(sb).p(" ");
        _sig._types[0].print(sb.p("-> "));
        sb.p(" #");
        long fidxs = _fidxs&-2; // Strip off the null flag bit
        String fidx = fidxs==0 ? ""
            : Long.bitCount(fidxs) == 1 ? ""+Long.numberOfTrailingZeros(fidxs)
            : fidxs == -2 ? "ALL"
            : "b"+Long.toBinaryString(fidxs); // Just some function bits
        sb.p(fidx).p("}");
        if( (_fidxs&1)==1 ) sb.p("?");
        return sb;
    }

    @Override public SB gprint(SB sb) {
        if( this== NULLPTR) return sb.p("null");
        if( this== VOIDPTR) return sb.p("*void");
        sb.p("{ ");
        for( int i=1; i<_sig._types.length; i++ )
            _sig._types[i].gprint(sb).p(" ");
        _sig._types[0].gprint(sb.p("-> "));
        sb.p(" #");
        long fidxs = _fidxs&-2; // Strip off the null flag bit
        String fidx = fidxs==0 ? ""
            : Long.bitCount(fidxs) == 1 ? ""+Long.numberOfTrailingZeros(fidxs)
            : fidxs == -2 ? "ALL"
            : "b"+Long.toBinaryString(fidxs); // Just some function bits
        sb.p(fidx).p("}");
        if( (_fidxs&1)==1 ) sb.p("?");
        return sb;
    }

}
