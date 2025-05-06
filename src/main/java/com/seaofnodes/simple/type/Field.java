package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import java.util.*;

/**
 * Represents a field in a struct. This is not a Type in the type system.
 */
public class Field extends Type {

    // The pair {fieldName,type} uniquely identifies a field.

    // Field name
    public String _fname;
    // Type of the field
    public Type _t;
    // Unique memory alias, not sensibly part of a "type" but very convenient here.
    public int _alias;
    // Field must be written to exactly once, no more, no less
    public boolean _final;
    // Field is final in the declaration; value is the same for all instances
    // and will be moved to the class object
    public boolean _one;

    private static final Ary<Field> FREE = new Ary<>(Field.class);
    private Field(String fname, Type type, int alias, boolean xfinal, boolean one ) { super(TFLD); init(fname,type,alias,xfinal,one); }
    private Field init(String fname, Type type, int alias, boolean xfinal, boolean one ) {
        _fname = fname;
        _t     = type;
        _alias = alias;
        _final = xfinal;
        _one   = one;
        return this;
    }

    // Return a filled-in Field; either from free list or alloc new.
    private static Field malloc(String fname, Type type, int alias, boolean xfinal, boolean one ) {
        return FREE.isEmpty() ? new Field(fname,type,alias,xfinal,one) : FREE.pop().init(fname,type,alias,xfinal,one);
    }
    // Malloc-from
    public Field malloc( ) { return malloc(_fname,null,_alias,_final,_one); }
    @Override Field free(Type t) {
        Field f = (Field)t;
        assert !f.isFree() && !f._terned;
        f._t    = null;
        f._dual = null;
        f._hash = 0;
        FREE.push(f);
        return this;
    }
    @Override boolean isFree() { return _t ==null; }

    // Make and intern with listed fields
    public static Field make( String fname, Type type, int alias, boolean xfinal, boolean one ) {
        Field f = malloc(fname,type,alias,xfinal,one);
        Field f2 = f.intern();
        if( f2==f ) return f;
        return VISIT.isEmpty() ? f2.free(f) : f2.delayFree(f);
    }
    public Field makeFrom( Type type ) {
        return type == _t ? this : make(_fname,type,_alias,_final,_one);
    }
    public Field makeFrom( boolean xfinal ) {
        return xfinal == _final ? this : make(_fname,_t,_alias,xfinal,_one);
    }

    // Cycle-making-breaking
    void setType(Type t) { assert _t ==null; _t = t; }

    public static final Field BOT   = make(" ",Type.BOTTOM,-999,true,false);
    public static final Field TEST  = make("test",Type.NIL,-2,false,false);
    public static final Field TEST2 = make("test",Type.NIL,-2,true, false);
    public static final Field FLT   = make("flt ",TypeFloat.F32,-2,false,false);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST2); ts.add(FLT); }

    @Override Field xmeet( Type that ) {
        Field fld = (Field)that; // Invariant
        if( this==BOT || fld==BOT ) return BOT;
        if( this==BOT.dual() ) return fld ;
        if( fld ==BOT.dual() ) return this;
        if( _fname!=fld._fname ) { assert !_fname.equals(fld._fname); return BOT; }
        assert _alias==fld._alias;
        assert _one  ==fld._one  ;
        return make(_fname, _t.meet(fld._t ),_alias,_final | fld._final, _one);
    }

    @Override
    Field xdual() { return malloc(_fname, _t.dual(),_alias,!_final,_one); }

    @Override Field rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        Field d = malloc(_fname,null,_alias,!_final,_one);
        (_dual = d)._dual = this; // Cross link duals
        d._t = _t._terned ? _t.dual() : _t.rdual();
        return d;
    }

    @Override boolean _isConstant() { return _t._isConstant(); }
    @Override boolean _isFinal() { return _final && _t._isFinal(); }
    @Override Field _makeRO() { return _final ? this : make(_fname, _t._makeRO(),_alias,true,_one);  }
    boolean isGLB2() { return _final && _t._isGLB(true); }
    Field glb2() {
        Type glb = _t._glb(true);
        return (glb== _t && _final ) ? this : make(_fname,glb,_alias,true,_one);
    }
    @Override Field _close() { return makeFrom( _t._close()); }

    // Override in subclasses
    int hash() { return _fname.hashCode() ^ _t.hashCode() ^ _alias ^ (_final ? 1024 : 0) ^ (_one ? 2048 : 0); }

    private boolean static_eq( Field f ) {
        return _fname.equals(f._fname) && _alias==f._alias && _final==f._final && _one==f._one && _t !=null && f._t !=null;
    }
    @Override boolean eq( Type t ) {
        Field f = (Field)t;
        return static_eq(f) && _t ==f._t;
    }
    @Override boolean cycle_eq(Type t) {
        Field f = (Field)t;
        return static_eq(f) && _t.cycle_eq(f._t );
    }

    @Override int nkids() { return 1; }
    @Override Type at( int idx ) { return _t; }
    @Override void set( int idx, Type t ) { _t = t; }

    @Override
    public SB _print( SB sb, BitSet visit, boolean html ) {
        sb.p(_final?"":"!").p(_one?"$":"").p(_fname).p(":").p(_alias).p(" : ");
        return _t ==null ? sb.p("---") : _t.print(sb,visit,html);
    }

    @Override public String str() { return (_final?"":"!")+(_one?"$":"")+_fname; }
}
