package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a field in a struct. This is not a Type in the type system.
 */
public class Field extends Type {

    // The pair {fieldName,type} uniquely identifies a field.

    // Field name
    public final String _fname;
    // Type of the field
    public final Type _type;
    // Unique memory alias, not sensibly part of a "type" but very convenient here.
    public final int _alias;
    // Field must be written to exactly once, no more, no less
    public final boolean _final;
    // Field is final in the declaration; value is the same for all instances
    // and will be moved to the class object
    public final boolean _one;

    private Field(String fname, Type type, int alias, boolean xfinal, boolean one ) {
        super(TFLD);
        _fname = fname;
        _type  = type;
        _alias = alias;
        _final = xfinal;
        _one   = one;
    }
    // Make with existing alias
    public static Field make( String fname, Type type, int alias, boolean xfinal, boolean one ) {
        return new Field(fname,type,alias,xfinal,one).intern();
    }
    public Field makeFrom( Type type ) {
        return type == _type ? this : new Field(_fname,type,_alias,_final,_one).intern();
    }
    @Override public Field makeRO() { return _final ? this : make(_fname,_type.makeRO(),_alias,true,_one);  }
    @Override public boolean isFinal() { return _final && _type.isFinal(); }

    public static final Field TEST = make("test",Type.NIL,-2,false,false);
    public static final Field TEST2= make("test",Type.NIL,-2,true, false);
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(TEST2); }

    @Override Field xmeet( Type that ) {
        Field fld = (Field)that; // Invariant
        assert _fname.equals(fld._fname);
        assert _alias==fld._alias;
        assert _one  ==fld._one  ;
        return make(_fname,_type.meet(fld._type),_alias,_final | fld._final, _one);
    }

    @Override
    public Field dual() { return make(_fname,_type.dual(),_alias,!_final,_one); }

    @Override public boolean isConstant() { return _type.isConstant(); }

    @Override public Field glb(boolean mem) {
        Type glb = _type.glb(mem);
        return (glb==_type && _final ) ? this : make(_fname,glb,_alias,true,_one);
    }

    // Override in subclasses
    int hash() { return _fname.hashCode() ^ _type.hashCode() ^ _alias ^ (_final ? 1024 : 0) ^ (_one ? 2048 : 0); }

    boolean eq(Type t) {
        Field f = (Field)t;
        return _fname.equals(f._fname) && _type==f._type && _alias==f._alias && _final==f._final && _one==f._one;
    }


    @Override
    public SB print( SB sb ) {
        return _type.print(sb.p(_final?"":"!").p(_one?"$":"").p(_fname).p(":").p(_alias).p(" : "));
    }

    @Override public String str() { return (_final?"":"!")+(_one?"$":"")+_fname; }
}
