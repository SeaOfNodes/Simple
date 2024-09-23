package com.seaofnodes.simple.type;

import com.seaofnodes.simple.Utils;
import java.util.ArrayList;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {

    // A Struct has a name and a set of fields; the fields themselves have
    // names, types and aliases.  The name has no semantic meaning, but is
    // useful for debugging.  Briefly during parsing a Struct can be a
    // forward-ref; in this case the _fields array is null.

    // Its illegal to attempt to load a field from a forward-ref struct.

    // Example: "int rez = new S.x; struct S { int x; } return rez;" // Error, S not defined
    // Rewrite: "struct S { int x; } int rez = new S.x; return rez;" // Ok, no forward ref
    //
    // During the normal optimization run, struct types "bottom out" at further
    // struct references, so we don't have to handle e.g. cyclic types.  The
    // "bottom out" is again the forward-ref struct.
    public final String _name;
    public final Field[] _fields;

    TypeStruct(byte t, String name, Field[] fields) {
        super(t);
        _name = name;
        _fields = fields;
    }

    // All fields directly listed
    public static TypeStruct make(String name, Field[] fields) { return new TypeStruct(TSTRUCT, name, fields).intern(); }
    public static final TypeStruct TOP = make("$TOP",new Field[0]);
    public static final TypeStruct BOT = make("$BOT",new Field[0]);
    public static final TypeStruct TEST = make("test",new Field[]{Field.TEST});
    // Forward-ref version
    public static TypeStruct make(String name) { return make(name, null); }

    // A pair of self-cyclic types
    private static final TypeStruct S1F = make("S1");
    private static final TypeStruct S2F = make("S2");
    public  static final TypeStruct S1  = make("S1", new Field[]{ Field.make("a", -1, TypeInteger.BOT), Field.make("s2",-2,TypeMemPtr.make(S2F,false)) });
    private static final TypeStruct S2  = make("S2", new Field[]{ Field.make("b", -3, TypeFloat  .BOT), Field.make("s1",-4,TypeMemPtr.make(S1F,false)) });
    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(BOT); ts.add(S1); ts.add(S2); }

    public int find(String fname) {
        for( int i=0; i<_fields.length; i++ )
            if( _fields[i]._fname.equals(fname) )
                return i;
        return -1;
    }

    @Override
    Type xmeet(Type t) {
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
        if( this._fields==null ) return this;
        if( that._fields==null ) return that;
        if( _fields.length != that._fields.length ) return BOT;
        // Just do field meets
        Field[] flds = new Field[_fields.length];
        for( int i=0; i<_fields.length; i++ ) {
            Field f0 = _fields[i], f1 = that._fields[i];
            if( !f0._fname.equals(f1._fname) || f0._alias != f1._alias )
                return BOT;
            flds[i] = (Field)f0.meet(f1);
        }
        return make(_name,flds);
    }

    @Override
    public TypeStruct dual() {
        if( this==TOP ) return BOT;
        if( this==BOT ) return TOP;
        if( _fields == null ) return this;
        Field[] flds = new Field[_fields.length];
        for( int i=0; i<_fields.length; i++ )
            flds[i] = _fields[i].dual();
        return make(_name,flds);
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
        if( _fields!=null )
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
        if( _fields == ts._fields ) return true;
        if( _fields==null || ts._fields==null ) return false;
        if( _fields.length!=ts._fields.length )
            return false;
        for( int i = 0; i < _fields.length; i++ )
            if( _fields[i] != ts._fields[i] )
                return false;
        return true;
    }

    @Override
    int hash() {
        long hash = _name.hashCode();
        if( _fields != null )
            for( Field f : _fields )
                hash = Utils.rot(hash,13) ^ f.hashCode();
        return Utils.fold(hash);
    }

    @Override
    public StringBuilder print(StringBuilder sb) {
        sb.append(_name);
        if( _fields == null ) return sb; // Forward reference struct, just print the name
        sb.append(" {\n");
        for( Field f : _fields ) {
            sb.append("  ").append(f._fname).append(" :");
            f._type.print(sb).append(";\n");
        }
        return sb.append("}");
    }

    @Override public String str() { return _name; }


    // Field offsets as packed byte offsets
    private int[] _offs;  // Lazily computed and not part of the type semantics
    public int[] offsets() {    // Field byte offsets
        if( _offs != null ) return _offs;

        // Compute a layout for a collection of fields
        assert _fields != null; // No forward refs
        // Compute a layout
        int[] cnts = new int[4]; // Count of fields at log field size
        for( Field f : _fields )
            cnts[f._type.log_size()]++; // Log size is 0(byte), 1(i16/u16), 2(i32/f32), 3(i64/dbl)
        int off = 0, idx = 0; // Base common struct fields go here, e.g. Mark/Klass
        // Compute offsets to the start of each power-of-2 aligned fields.
        int[] offs = new int[4];
        for( int i=3; i>=0; i-- ) {
            offs[i] = off;
            off += cnts[i]<<(i+3);
        }
        // Assign offsets to all fields.
        // Really a hidden radix sort.
        _offs = new int[_fields.length+1];
        for( Field f : _fields ) {
            int log = f._type.log_size();
            _offs[idx++] = offs[log]; // Field offset
            offs[log] += 1<<(log+3); // Next field offset at same alignment
            cnts[log]--;             // Count down, should be all zero at end
        }
        _offs[_fields.length] = (off+63)& ~63; // Round out to max alignment
        return _offs;
    }

}
