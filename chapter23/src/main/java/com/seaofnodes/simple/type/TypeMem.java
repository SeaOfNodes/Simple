package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.ArrayList;
import java.util.BitSet;


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
        assert alias>1 || (t==Type.TOP || t==Type.BOTTOM);
        _alias = alias;
        _t = t;
        return this;
    }
    @Override TypeMem free(Type t) {
        TypeMem mem = (TypeMem)t;
        mem._alias= -99;
        mem._dual = null;
        mem._hash = 0;
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

    public static void gather(ArrayList<Type> ts) { ts.add(make(2,Type.NIL)); ts.add(make(2,TypeInteger.ZERO)); ts.add(BOT); }

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
    @Override public int log_size() { throw Utils.TODO(); }
    @Override boolean _isFinal() { return _t._isFinal(); }
    @Override boolean _isGLB(boolean mem) { return _t._isGLB(true); }
    @Override public Type _glb(boolean mem) { return make(_alias,_t._glb(true)); }

    @Override int hash() { return 9876543 + _alias + _t.hashCode(); }

    @Override boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t == that._t;
    }

    @Override boolean cycle_eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t.cycle_eq(that._t);
    }

    @Override int nkids() { return 1; }
    @Override Type at( int idx ) { return _t; }
    @Override void set( int idx, Type t ) { _t = t; }

    @Override public SB _print(SB sb, BitSet visit, boolean html) {
        sb.p("#");
        if( _alias==0 ) return sb.p(_t._type==TTOP ? "TOP" : "BOT");
        return _t.print(sb.p(_alias).p(":"),visit,html);
    }

    @Override public String str() { return toString(); }
}
