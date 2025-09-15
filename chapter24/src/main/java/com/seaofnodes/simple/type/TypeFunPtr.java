package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.util.Ary;
import java.util.*;

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
    public Type[] _sig;
    public Type _ret;
    // TODO:
    boolean _open;              // Extra args are true:BOTTOM, false:TOP
    // Cheesy easy implementation for a small set; 1 bit per unique function
    // within the same Type[].  Can be upgraded to a BitSet for larger classes
    // of functions.  Negative means "these 63 concrete bits plus infinite
    // unknown more"
    public long _fidxs; // 63 unique functions per signature

    private static final Ary<TypeFunPtr> FREE = new Ary<>(TypeFunPtr.class);
    private TypeFunPtr(byte nil, boolean open, Type[] sig, Type ret, long fidxs) { super(TFUNPTR,nil); init(nil,open,sig,ret,fidxs); }
    private TypeFunPtr init(byte nil, boolean open, Type[] sig, Type ret, long fidxs ) {
        assert sig != null;
        _nil  = nil;
        _open = open;
        _sig  = sig;
        _ret  = ret;
        _fidxs= fidxs;
        return this;
    }
    static TypeFunPtr malloc( byte nil, boolean open, Type[] sig, Type ret, long fidxs ) {
        return FREE.isEmpty() ? new TypeFunPtr(nil,open,sig,ret,fidxs) : FREE.pop().init(nil,open,sig,ret,fidxs);
    }
    @Override TypeFunPtr free(Type t) {
        TypeFunPtr fun = (TypeFunPtr)t;
        assert !fun.isFree() && !fun._terned;
        fun._sig = null;
        fun._ret = null;
        fun._dual = null;
        fun._hash = 0;
        FREE.push(fun);
        return this;
    }
    @Override boolean isFree() { return _sig==null; }

    // All fields directly listed
    public static TypeFunPtr make( byte nil, boolean open, Type[] sig, Type ret, long fidxs ) {
        TypeFunPtr fun = malloc(nil,open,sig,ret,fidxs);
        TypeFunPtr f2  = fun.intern();
        if( f2==fun ) return fun;
        return VISIT.isEmpty() ? f2.free(fun) : f2.delayFree(fun);
    }
    public static TypeFunPtr make( boolean nil, boolean open, Type[] sig, Type ret ) { return make((byte)(nil ? 3 : 2),open,sig,ret,-1); }


    @Override TypeFunPtr makeFrom( byte nil ) { return  nil ==_nil ? this : make(  nil,_open,_sig,_ret, _fidxs); }
    public    TypeFunPtr makeFrom( Type ret ) { return  ret ==_ret ? this : make( _nil,_open,_sig, ret, _fidxs); }
    public    TypeFunPtr makeFrom( int fidx ) { return make((byte)2, _open, _sig,_ret,1L<<fidx ); }


    public static final Type[] TEMPTY = new Type[0];
    static final Type[] TINT    = new Type[]{TypeInteger.BOT};
    static final Type[] TINTMEM = new Type[]{TypeInteger.BOT,TypeFloat.F32};
    static final Type[] TINTINT = new Type[]{TypeInteger.BOT,TypeInteger.BOT};
    public static TypeFunPtr BOT   = make((byte)3,true ,TEMPTY,Type.BOTTOM,-1);
    public static TypeFunPtr TEST  = make((byte)2,false,TINTMEM,TypeInteger.BOT, 1);
    public static TypeFunPtr TEST0 = make((byte)3,false,TINTMEM,TypeInteger.BOT, 3);
    public static TypeFunPtr MAIN  = make((byte)3,false,TINT   ,Type.BOTTOM,-1); // Main can return anything
    public static TypeFunPtr CALLOC= make((byte)3,false,TINTINT,TypeMemPtr .BOT,-1);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST0); ts.add(BOT);  ts.add(MAIN);ts.add(CALLOC); }

    @Override
    Type xmeet(Type t) {
        TypeFunPtr that = (TypeFunPtr) t;
        // if equal, no matters.
        // if short is BOT, chop     ; recurPre on short.
        // if short is TOP, copy long; recurPre on long.
        TypeFunPtr min = _sig.length < that._sig.length ? this : that;
        if( _sig.length != that._sig.length && (min._open ^ min==this) )
            return that.xmeet(this);
        // Recurse all common fields
        Type[] args = new Type[_sig.length];
        for( int i=0; i<min._sig.length; i++ )
            args[i] = _sig[i].meet(that._sig[i]);
        // Handle extra args
        for( int i=min._sig.length; i<_sig.length; i++ )
            args[i] = _sig[i];
        return make(xmeet0(that),_open | that._open, args, _ret.meet(that._ret), _fidxs | that._fidxs);
    }
    static Type[] xmeet( Type[] ts0, Type[] ts1) {
        if( ts0==ts1 ) return ts0;
        assert ts0.length==ts1.length;
        Type[] ts = new Type[ts0.length];
        for( int i=0; i<ts0.length; i++ )
            ts[i] = ts0[i].meet(ts1[i]);
        return ts;
    }

    @Override TypeFunPtr xdual() { return malloc(dual0(), !_open, xdual(_sig), _ret.dual(), ~_fidxs); }
    static Type[] xdual(Type[] ts) {
        Type[] dual = new Type[ts.length];
        for( int i=0; i<ts.length; i++ )
            dual[i] = ts[i].dual();
        return dual;
    }

    @Override TypeFunPtr rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        Type[] sigs = new Type[_sig.length];
        TypeFunPtr d = malloc(dual0(),!_open,sigs,null,~_fidxs);
        (_dual = d)._dual = this; // Cross link duals
        // Return rdual
        d._ret = _ret._terned ? _ret.dual() : _ret.rdual();
        // Signature rdual
        for( int i=0; i<_sig.length; i++ )
            sigs[i] = _sig[i]._terned ? _sig[i].dual() : _sig[i].rdual();
        return d;
    }

    // RHS is NIL; do not deep-dual when crossing the centerline
    @Override public Type meet0() { return _nil==3 ? this : make((byte)3,_open,_sig,_ret,_fidxs); }

    @Override public boolean isHigh() { return _nil <= 1 || (_nil==2 && _fidxs==0); }

    @Override boolean _isConstant() { return (_nil==2 && Long.bitCount(_fidxs)==1) || (_nil==3 && _fidxs==0); }

    @Override boolean _isFinal() { return true; }
    @Override boolean _isGLB(boolean mem) { return true; }
    @Override TypeFunPtr _glb(boolean mem) { return this; }
    @Override TypeFunPtr _close() {
        Type[] sig = new Type[_sig.length];
        TypeFunPtr fun = malloc(_nil,false,sig,null,_fidxs);
        // Now start the recursion
        fun._ret = _ret._close();
        for( int i=0; i<sig.length; i++ )
            sig[i] = _sig[i]._close();

        return fun;
    }

    @Override public int log_size() { return 2; } // (1<<2)==4-byte pointers

    public Type arg(int i) { return _sig[i]; }
    public long fidxs() { return _fidxs; }
    public Type ret() { return _ret; }
    public int nargs() { return _sig.length; }
    public int fidx() { assert Long.bitCount(_fidxs)==1; return Long.numberOfTrailingZeros(_fidxs); }
    public int nfcns() {
        if( _fidxs < 0 ) return Integer.MAX_VALUE; // Infinite choices
        return Long.bitCount(_fidxs);
    }


    // Used to walk over type structures
    @Override int nkids() { return _sig.length+1; }
    @Override Type at( int idx ) {
        return idx == _sig.length ? _ret : _sig[idx];
    }
    @Override void set( int idx, Type t ) {
        if( idx == _sig.length ) _ret = t;
        else _sig[idx] = t;
    }

    @Override
    int hash() {
        long hash = _ret.hashCode() ^ _fidxs ^ super.hash();
        for( Type arg : _sig )
            hash = hash*19 ^ arg.hashCode();
        return Utils.fold(hash);
    }

    @Override
    boolean eq(Type t) {
        // Recursive; so use cyclic equals
        if( !VISIT.isEmpty() ) {
            assert TypeStruct.CEQUALS.isEmpty();
            boolean rez = cycle_eq(t);
            TypeStruct.CEQUALS.clear();
            return rez;
        }
        TypeFunPtr fun = (TypeFunPtr)t; // Invariant
        if( !super.eq(fun) || _open != fun._open || _fidxs != fun._fidxs || _sig.length != fun._sig.length ) return false;
        if( _sig == fun._sig && _ret == fun._ret ) return true;

        if( !_ret.eq(fun._ret) ) return false;
        for( int i = 0; i < _sig.length; i++ )
            if( !_sig[i].eq(fun._sig[i]) )
                return false;
        return true;
    }
    @Override boolean cycle_eq(Type t) {
        if( t._type != TFUNPTR ) return false;
        TypeFunPtr fun = (TypeFunPtr)t; // Invariant
        if( !super.eq(fun) || _open != fun._open || _fidxs != fun._fidxs || _sig.length != fun._sig.length ) return false;
        if( _sig == fun._sig && _ret == fun._ret ) return true;

        // Check to see if we've ever compared this pair of types before;
        // if so, then assume the cycle is equal here.
        int pid = pid(fun);
        if( TypeStruct.CEQUALS.find(pid)!= -1 ) return true;
        TypeStruct.CEQUALS.push(pid);
        if( !_ret.cycle_eq(fun._ret) ) return false;
        for( int i = 0; i < _sig.length; i++ )
            if( !_sig[i].cycle_eq(fun._sig[i]) )
                return false;
        return true;
    }

    @Override public String str() { return "{"+printFIDX()+"}"; }

    SB _print(SB sb, BitSet visit, boolean html ) {
        sb.p(x()).p("{ ");
        if( _sig!=null )
            for( Type t : _sig )
                sb.p(t.str()).p(" "); // Short form in signature
        _ret.print(sb.p(html ? "&rarr; " : "-> "),visit,html).p(" #");
        // Print fidxs
        return sb.p(printFIDX()).p("}").p(q());
    }
    String printFIDX() {
        String tilde = isHigh() ? "~" : "";
        long fidxs = isHigh() ? ~_fidxs : _fidxs;
        String fidx = fidxs==0 ? ""
            : Long.bitCount(fidxs) == 1 ? ""+Long.numberOfTrailingZeros(fidxs)
            : fidxs == -1 ? "ALL"
            : "b"+Long.toBinaryString(fidxs); // Just some function bits
        return tilde+fidx;
    }

    // Usage: for( long fidxs=fidxs(); fidxs!=0; fidxs=nextFIDX(fidxs) ) { int fidx = Long.numberOfTrailingZeros(fidxs); ... }
    public static long nextFIDX(long fidxs) { return fidxs & (fidxs-1); }

}
