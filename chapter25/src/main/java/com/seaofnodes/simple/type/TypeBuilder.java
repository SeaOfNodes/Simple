package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.*;

import java.util.BitSet;

/**
 * Represents a builder for struct type.  Only one at a time for a particular
 * type name, interning can shortcut.  Equality is based on the name.  Once
 * defined, replaces itself with the corresponding TypeStruct in all future
 * situations.
 */
public class TypeBuilder extends TypeNil {
    public final String _name;
    private final boolean _one;
    private final Ary<Field> _flds;
    private TypeMemPtr _tmp;

    private TypeBuilder( String name, boolean one, Ary<Field> flds, byte nil ) {
        super(TBLD, nil);
        _name = name;
        _one  = one;
        _flds = flds;
    }


    public static TypeBuilder make( String name, boolean one) { return new TypeBuilder(name,one,new Ary<>(Field.class),(byte)2).intern(); }
    @Override TypeBuilder makeFrom(byte nil) { return new TypeBuilder(_name,_one,_flds,nil).intern(); }
    public TypeBuilder makeNullable() { return makeFrom((byte)3); }


    @Override public Type xmeet(Type other) {
        throw Utils.TODO();
    }

    @Override public Type nmeet(TypeNil other) {
        // TypeBuilder is *below* the built value
        if( other.isa(_tmp) ) return this;
        // Dualed built value is *above* other
        if( _tmp.dual().isa(other) ) return other;
        throw Utils.TODO();
    }

    @Override Type meet0() {
        if( _nil==3 ) return this;
        throw Utils.TODO();
    }

    // Basically a constant; type does not depend on e.g. field types.
    @Override TypeBuilder xdual() { return this; }

    // Add a field
    public void add( Field fld ) { assert _tmp==null; _flds.add(fld); }

    // Close: no more fields can be added, and make a TMP/TS out of the fields
    public TypeMemPtr close() {
        assert _nil==2;
        TypeStruct ts = TypeStruct.make(_name,false,_flds.asAry());
        _tmp = TypeMemPtr.make((byte)2, ts, _one);
        TypeBuilder bldq = makeNullable();
        bldq._tmp = TypeMemPtr.make((byte)3, ts, _one);
        return _tmp;
    }

    @Override Type _close( ) {
        TypeStruct ts = _tmp._obj._close();
        return TypeMemPtr.malloc(_nil,ts,false);
    }


    @Override boolean _isGLB(boolean mem) { return true; }
    @Override TypeBuilder _glb(boolean mem) { return this; }

    boolean eq(Type t) {
        TypeBuilder bld = (TypeBuilder)t; // Invariant
        return super.eq(bld) && _name==bld._name && _flds==bld._flds;
    }
    int hash() { return super.hash() ^ _name.hashCode(); }


    @Override
    SB _print( SB sb, BitSet visit, boolean html ) {
        sb.p(_name);
        if( html ) return sb;
        sb.p(":{");
        for( Field f : _flds )
            (f._t ==null ? sb.p("---") : f._t.print(sb,visit,html)).p(f._final ? " " : " !").p(f._fname).p("; ");
        if( _tmp==null ) sb.p("... ");
        return sb.p("}");
    }

    @Override public String str() { return _name+":{...}"; }
}
