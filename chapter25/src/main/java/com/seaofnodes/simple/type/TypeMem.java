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

    private static final Ary<TypeMem> FREE = new Ary<>(TypeMem.class);
    private TypeMem(int alias, Type t) { super(TMEM); init(alias,t); }
    private TypeMem init(int alias, Type t) {
        _alias = alias;
        _t = t;
        return this;
    }
    @Override TypeMem free(Type t) {
        TypeMem mem = (TypeMem)t;
        mem._alias= -99;
        mem._dual = null;
        mem._hash = 0;
        mem._t = null;
        FREE.push(mem);
        return this;
    }
    @Override boolean isFree() { return _alias == -99; }

    public static TypeMem malloc(int alias, Type t) {
        return FREE.isEmpty() ? new TypeMem(alias,t) : FREE.pop().init(alias,t);
    }
    public static TypeMem make(int alias, Type t) {
        TypeMem f = malloc(alias,t);
        TypeMem f2 = f.intern();
        if( f2==f ) return f;
        return VISIT.isEmpty() ? f2.free(f) : f2.delayFree(f);
    }

    public static final TypeMem TOP = make(1, Type.TOP   );
    public static final TypeMem BOT = make(1, Type.BOTTOM);
    public static final TypeMem SELF_MEM = make(1, TypeStruct.BOT);

    public static void gather(ArrayList<Type> ts) { ts.add(make(2,Type.NIL)); ts.add(make(2,TypeInteger.ZERO)); ts.add(BOT); ts.add(SELF_MEM); }

    @Override
    TypeMem xmeet(Type t) {
        TypeMem that = (TypeMem) t; // Invariant: TypeMem and unequal
        if( this==TOP ) return that;
        if( that==TOP ) return this;
        if( this==BOT ) return BOT;
        if( that==BOT ) return BOT;
        int alias = _alias==that._alias ? _alias : 1;
        Type mt = _t.meet(that._t);
        return make(alias,mt);
    }

    @Override
    Type xdual() { return _t._dual==_t ? this : malloc(_alias,_t.dual()); }

    @Override TypeMem rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMem d = malloc(_alias,null);
        (_dual = d)._dual = this; // Cross link duals
        d._t = _t._terned ? _t.dual() : _t.rdual();
        return d;
    }

    @Override public boolean isHigh() { return _t.isHigh(); }
    //@Override boolean _isConstant() { return _t._isConstant(); }
    @Override public int log_size() { throw Utils.TODO(); }
    @Override boolean _isFinal() { return _t._isFinal(); }
    @Override boolean _isGLB(boolean mem) { return _t._isGLB(true); }
    @Override public Type _glb(boolean mem) { return make(_alias,_t._glb(true)); }

    @Override TypeMem _close( ) { return malloc(_alias,_t._close()); }

    @Override public Type upgradeType(HashMap<String,Type> TYPES) {
        return make(_alias,_t.upgradeType(TYPES));
    }

    @Override int hash() { return 9876543 + _alias + _t.hashCode(); }

    @Override boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t == that._t;
    }

    @Override boolean cycle_eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t.cycle_eq(that._t);
    }

    @Override public int nkids() { return 1; }
    @Override public Type at( int idx ) { return _t; }
    @Override public void set( int idx, Type t ) { _t = t; }
    // one tag for alias#1, one tag for generic alias
    @Override int TAGOFF() { return 2; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Integer,Integer> aliases ) {
        if( _alias==1 ) { baos.write(TAGOFFS[_type]); return; }
        // generic alias
        baos.write(TAGOFFS[_type] + 1);
        assert _alias <= 255;
        baos.packed1(aliases.get(_alias));
    }
    static TypeMem packed( int tag, BAOS bais ) {
        if( tag==0 ) return malloc(1,null);
        return malloc(bais.packed1(),null);
    }

    @Override public SB _print(SB sb, BitSet visit, boolean html) {
        sb.p("#");
        if( _alias==0 ) return sb.p(_t._type==TTOP ? "TOP" : "BOT");
        return _t.print(sb.p(_alias).p(":"),visit,html);
    }

    @Override public String str() { return toString(); }
}
