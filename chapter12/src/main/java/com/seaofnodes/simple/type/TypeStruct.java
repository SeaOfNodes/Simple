package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {

    // For now in terms of the lattice
    // a struct type just stays as is

    public final String _name;
    public       Field[] _fields;

    public TypeStruct(String name, Field[] fields) {
        super(Type.TSTRUCT);
        _name = name;
        _fields = fields;
    }
    public TypeStruct(String name) { this(name,null); }

    public static TypeStruct make(String name, Field[] fields) { return new TypeStruct(name, fields).intern(); }
    public static TypeStruct make(String name, ArrayList<Field> fields) { return make(name,fields.toArray(new Field[fields.size()])); }
    public TypeStruct finish( ArrayList<Field> fields ) {
        _fields = fields.toArray(new Field[fields.size()]);
        return intern();
    }

    public static final TypeStruct TOP = make("$TOP",new Field [0]);
    public static final TypeStruct BOT = make("$BOT",new Field [0]);
    public static final TypeStruct TEST = make("test",new Field[]{Field.TEST});

    // A pair of self-cyclic types
    public  static final TypeStruct S1 = new TypeStruct("S1", new Field[]{ Field.make("a", TypeInteger.BOT), null });
    private static final TypeStruct S2 = new TypeStruct("S2", new Field[]{ Field.make("b", TypeFloat  .BOT), null });
    static {
        TypeMemPtr tmp1 = new TypeMemPtr(S1,false);
        TypeMemPtr tmp2 = new TypeMemPtr(S2,false);
        S1._fields[1] = new Field("s2",tmp2);
        S2._fields[1] = new Field("s1",tmp1);
        TypeStruct xs1 = S1.intern(); assert xs1 == S1;
        TypeStruct xs2 = S2.intern(); assert xs2 == S2;
    }

    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(BOT); ts.add(S1); }

    public int find(String fname) {
        for( int i=0; i<_fields.length; i++ )
            if( _fields[i]._fname.equals(fname) )
                return i;
        return -1;
    }

    @Override Type xmeet(Type t ) {
        TypeStruct that = (TypeStruct) t;
        if( this==TOP ) return that;
        if( that==TOP ) return this;
        if( this==BOT ) return BOT;
        if( that==BOT ) return BOT;

        // Within the same compilation unit, struct names are unique.  If the
        // names differ, its different structs.  Across many compilation units,
        // structs with the same name but different field layouts can be
        // interned... which begs the question:
        // "What is the meet of structs from two different compilation units?"
        // And the answer is: "don't ask".
        if( !_name.equals(that._name) )
            return BOT;         // It's a struct; that's about all we know
        // Stop cycles
        if( VISIT.containsKey(_name) )
            return VISIT.get(_name);
        // Now all fields should be the same, so just do field meets
        assert _fields.length == that._fields.length;
        Field[] flds = new Field[_fields.length];
        TypeStruct s = new TypeStruct(_name,flds);
        VISIT.put(_name,s);
        for( int i=0; i<_fields.length; i++ )
            flds[i] = (Field)_fields[i]._meet(that._fields[i]);
        return s;
    }

    @Override TypeStruct _dual( ) {
        if( this==TOP ) return BOT;
        if( this==BOT ) return TOP;
        // Stop cycles
        if( VISIT.containsKey(_name) )
            return VISIT.get(_name);
        Field[] flds = new Field[_fields.length];
        TypeStruct s = new TypeStruct(_name,flds);
        VISIT.put(_name,s);
        for( int i=0; i<_fields.length; i++ )
            flds[i] = _fields[i]._dual();
        return s;
    }
    @Override Type cyclic_intern() {
        if( VISIT.containsKey(_name) )
            return VISIT.get(_name);
        TypeStruct ts = intern();
        if( ts==this )
            VISIT.put(_name,ts);
        return ts;
    }

    // Keeps the same struct, but lower-bounds all fields.
    @Override
    public TypeStruct glb() {
        if( _glb() ) return this;
        // Need to glb each field
        Field[] flds = new Field[_fields.length];
        for( int i=0; i<_fields.length; i++ )
            flds[i] = _fields[i].glb();
        return make(_name,flds);
    }
    private boolean _glb() {
        for( Field f : _fields )
            if( f.glb() != f )
                return false;
        return true;
    }

    @Override
    boolean eq(Type t) {
        TypeStruct ts = (TypeStruct)t; // Invariant
        if( !_name.equals(ts._name) )
            return false;
        if( _fields==ts._fields ) return true; // Definitely equals
        if( _fields.length != ts._fields.length ) return false; // Definitely not equals
        for( int i = 0; i < _fields.length; i++ )
            if( !_fields[i]._fname.equals(ts._fields[i]._fname) )
                return false;   // Names unequal
        // Must do cycle-aware equals
        if( EQ_VISIT.get(_uid) )
            return true;        // Assume repeated checks are equals
        EQ_VISIT.set(_uid);
        for( int i = 0; i < _fields.length; i++ )
            if( !_fields[i]._type.eq(ts._fields[i]._type) )
                return false;   // Types unequal
        return true;
    }

    @Override
    int hash() {
        long hash = _name.hashCode();
        for( Field f : _fields )
            hash = Utils.rot(hash,13) ^ f.hashCode();
        return Utils.fold(hash);
    }

    @Override StringBuilder _print(StringBuilder sb, BitSet visit, int d) {
        sb.append(_name);
        if( visit.get(_uid) )
            return sb;
        visit.set(_uid);
        sb.append(" {\n");
        for( Field f : _fields ) {
          sb.append( "  ".repeat(d) );
            sb.append(f==null ? "----" : f._fname).append(" :");
            if( f!=null ) f._type._print(sb,visit,d+1);
            sb.append(";\n");
        }
      sb.append( "  ".repeat(d-1) );
        return sb.append("}");
    }

    @Override public String str() { return _name; }
}
