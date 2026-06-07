package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;


/**
 * Represents a slice of memory corresponding to a set of aliases
 */
public class TypeMem extends Type {

    // Which slice of memory?
    //  1 and TOP means no slice.
    //  1 and BOT means all memory.
    //  N means alias slice#N.
    public int _alias;
    public Type _t;       // Memory contents, some scalar type
    public boolean _one;  // Private memory, corresponds to TMP _one
    public boolean _final;// Memory is only set in the constructor

    // Escaped aliases and functions - escaped into this memory.  These are
    // reachable from some chain of indirections from `_t`.  Bit zero is
    // used for mutable/immutable safety.  Sign bit repeats infinitely.
    public int[] _escFs;
    public int[] _escAs;

    static public int CNT;

    private static final Ary<TypeMem> FREE = new Ary<>(TypeMem.class);

    private TypeMem() { super(TMEM); CNT++; }
    private TypeMem init(int alias, Type t, boolean one, boolean xfinal, int[] fidxs, int[] aliases) {
        _alias = alias;
        _t = t;
        _one = one;
        _final = xfinal;

        assert _escFs == null && _escAs == null;
        _escFs = XInt.intern(fidxs);
        _escAs = XInt.intern(aliases);
        return this;
    }
    @Override TypeMem free(Type t) {
        TypeMem mem = (TypeMem)t;
        mem._alias= -99;
        mem._dual = null;
        mem._hash = 0;
        mem._t = null;
        mem._escFs = null;
        mem._escAs = null;
        FREE.push(mem);
        return this;
    }
    @Override boolean isFree() { return _alias == -99; }

    public static TypeMem malloc(int alias, Type t, boolean one, boolean xfinal, int[] fidxs, int[] aliases) {
        return (FREE.isEmpty() ? new TypeMem() : FREE.pop()).init(alias,t,one,xfinal,fidxs,aliases);
    }
    public static TypeMem make(int alias, Type t, boolean one, boolean xfinal, int[] fidxs, int[] aliases) {
        if( fidxs  ==null ) fidxs  = XInt.EMPTY;
        if( aliases==null ) aliases= XInt.EMPTY;
        TypeMem f = malloc(alias,t,one,xfinal, fidxs, aliases);
        TypeMem f2 = f.intern();
        if( f2==f ) return f;
        return VISIT.isEmpty() ? f2.free(f) : f2.delayFree(f);
    }
    public static TypeMem make(int alias, Type t) { return make(alias,t,false,false,null,null); }
    // Make a private memory
    public static TypeMem makePrivate(Type t) { return make(1,t,true,false,escapeFIDX(XInt.EMPTY,t), escapeAlias(XInt.EMPTY,t)); }

    public TypeMem makeFrom(Type t) { return make(_alias,t,_one,_final,_escFs,_escAs); }
    public TypeMem makeFrom(int[] fidxes, int[] aliases) {
        return make(_alias,_t,_one,_final,fidxes,aliases);
    }
    // Existing escapes plus `t` escapes
    public TypeMem escapes(Type t) {
        return make(_alias,_t,_one,_final, escapeFIDX(_escFs,t), escapeAlias(_escAs,t));
    }
    // Existing escapes plus `t` escapes
    public TypeMem escapesFrom(Type t) {
        return make(_alias,t,_one,_final, escapeFIDX(_escFs,t), escapeAlias(_escAs,t));
    }

    public static final TypeMem TOP = make(1, Type.TOP   ,true ,true , XInt.EMPTY, XInt.EMPTY);
    public static final TypeMem BOT = make(1, Type.BOTTOM,false,false, XInt.FULL , XInt.FULL );
    public static final TypeMem SELF_MEM = make(1, TypeStruct.BOT);

    public static void gather(ArrayList<Type> ts) { ts.add(make(2,Type.NIL)); ts.add(make(2,TypeInteger.ZERO)); ts.add(BOT); ts.add(SELF_MEM); }

    @Override
    TypeMem xmeet(Type t) {
        TypeMem that = (TypeMem) t; // Invariant: TypeMem and unequal
        if( this==TOP ) return that;
        if( that==TOP ) return this;
        if( this==BOT ) return BOT;
        if( that==BOT ) return BOT;
        // Treat a alias-1 has either very-high or very-low, according to "_t"
        int alias = _alias==that._alias ? _alias // Same-same
            : this._alias==1 && this._t.isHigh() ? that._alias // this is high-1 alias, so take that
            : that._alias==1 && that._t.isHigh() ? this._alias // that is high-1 alias, so take this
            : 1;
        Type mt = _t.meet(that._t);
        int[] fidxs  = XInt.meet( _escFs, that._escFs );
        int[] aliases= XInt.meet( _escAs, that._escAs );
        return make(alias, mt, _one & that._one, _final & that._final, fidxs, aliases);
    }

    @Override
    Type xdual() { return malloc(_alias,_t.dual(),!_one,!_final, XInt.dual(_escFs), XInt.dual(_escAs)); }

    @Override TypeMem rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMem d = malloc(_alias,null,!_one,!_final, XInt.dual(_escFs), XInt.dual(_escAs));
        (_dual = d)._dual = this; // Cross link duals
        d._t = _t._terned ? _t.dual() : _t.rdual();
        return d;
    }

    @Override public boolean isHigh() { return _t.isHigh(); }
    @Override boolean _isConstant() { return _one && _alias!= 1 && _t._isConstant(); }
    @Override public int log_size() { throw Utils.TODO(); }
    @Override boolean _isFinal() { return _t._isFinal(); }
    @Override boolean _isGLB(boolean mem) { return _t._isGLB(true); }
    @Override public Type _glb(boolean mem) { return make(_alias,_t._glb(true),_one,_final,_escFs,_escAs); }

    @Override TypeMem _close( String name, HashMap<String, Type> TYPES ) { return malloc(_alias,_t._close(name, TYPES ),_one,_final,_escFs,_escAs); }

    @Override Type _upgradeType(HashMap<String,Type> TYPES) {
        return make(_alias,_t._upgradeType(TYPES),_one,_final,_escFs,_escAs);
    }

    @Override int hash() { return 9876543 + _alias + _t.hashCode() + (_one ? 8196 : 0) + (_final ? 16392 : 0) + XInt.hash(_escFs) + XInt.hash(_escAs); }

    @Override boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t == that._t && _one == that._one && _final == that._final && _escFs == that._escFs && _escAs == that._escAs;
    }

    @Override boolean cycle_eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _one == that._one && _final == that._final && _escFs == that._escFs && _escAs == that._escAs && _t.cycle_eq(that._t);
    }

    @Override public int nkids() { return 1; }
    @Override public Type at( int idx ) { return _t; }
    @Override public void set( int idx, Type t ) { _t = t; }
    // one tag for alias#1, one tag for generic alias.
    // tag bit for _one, another bit for _final.
    @Override int TAGOFF() { return 8; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs ) {
        baos.write(TAGOFFS[_type]
                   + (_alias==1 ? 0 : 1)
                   + (!_one     ? 0 : 2)
                   + (!_final   ? 0 : 4) );
        if( _alias!=1 ) {
            // generic alias
            assert _alias <= 255;
            baos.packed1( _alias );
        }
        XInt.packed(baos,_escFs);
        XInt.packed(baos,_escAs);
    }
    static TypeMem packed( int tag, BAOS bais ) {
        int alias = (tag&1)==0 ? 1 : bais.packed1();
        boolean one   = (tag&2)==2;
        boolean xfinal= (tag&4)==4;
        int[] escFs = XInt.packed(bais);
        int[] escAs = XInt.packed(bais);
        return malloc(alias,null,one,xfinal,escFs,escAs);
    }

    @Override public SB _print(SB sb, BitSet visit, boolean html) {
        sb.p('#');
        if( !_final ) sb.p('!'); // Mutable memory
        if( _one ) sb.p('-');
        if( _alias==0 ) return sb.p(_t._type==TTOP ? "TOP" : "BOT");
        sb.p(_alias).p(":[");
        _t.print(sb,visit,html).p(",");
        XInt.str(sb,_escFs).p(",");
        return XInt.str(sb,_escAs).p("]");
    }

    @Override public String str() { return toString(); }


    // Tracking bulk escaped aliases and fidxs

    // Escape all FIDXs from t
    private static int[] escapeFIDX( int[] xs, Type t ) {
        return switch(t) {
        case TypeMemPtr tmp -> escapeFIDX(xs,tmp._obj);
        case TypeStruct ts  -> ts._open
                ? XInt.FULL         // Open structs can have any fields
                : xs;
        case TypeFunPtr tfp -> XInt.meet(xs,tfp.fidxs());
        case TypeMem    mem -> XInt.meet(xs,mem._escFs);
        case Type tt -> tt == BOTTOM ? XInt.FULL : xs;
        };
    }

    private static int[] escapeAlias( int[] xs, Type t ) {
        return switch(t) {
        case TypeMemPtr tmp -> escapeAlias(xs,tmp._obj);
        case TypeStruct ts  -> ts._open
            ? XInt.FULL         // Open structs can have any fields
            : XInt.meet(xs,ts.aliases());
        case TypeMem    mem -> XInt.meet(xs,mem._escAs);
        default -> xs;
        };
    }
}
