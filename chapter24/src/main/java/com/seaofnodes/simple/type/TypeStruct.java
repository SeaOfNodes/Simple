package com.seaofnodes.simple.type;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.*;
import java.util.*;

/**
 * Represents a struct type.
 */
public class TypeStruct extends Type {

    // A Struct has a name and a set of fields; the fields themselves have
    // names, types and aliases.  The name has no semantic meaning, but is
    // useful for debugging.

    // During parsing a mid-declaration struct is flagged as a "open".  It is
    // treated as having infinite fields with correct name and type BOTTOM.

    public String _name;  // Struct name
    public boolean _open; // infinite fields are all true:BOTTOM, false:TOP
    public Field[] _fields;

    private TypeStruct(String name, boolean open, Field[] fields) {
        super(TSTRUCT);
        _name = name;
        _open = open;
        _fields = fields;
    }

    private static final Ary<TypeStruct> FREE = new Ary<>(TypeStruct.class);
    // Return a filled-in TypeStruct; either from free list or alloc new.
    private static TypeStruct malloc(String name, boolean open, Field[] fields) {
        if( FREE.isEmpty() ) return new TypeStruct(name,open,fields);
        TypeStruct ts = FREE.pop();
        assert ts.isFree();
        ts._name = name;
        ts._open = open;
        ts._fields = fields;
        return ts;
    }
    // Free ts; return this.
    @Override TypeStruct free(Type t) {
        TypeStruct ts = (TypeStruct)t;
        assert !ts.isFree() && !ts._terned;
        ts._fields = null;
        ts._offs = null;
        ts._dual = null;
        ts._hash = 0;
        FREE.push(ts);
        return this;
    }
    @Override boolean isFree() { return _fields==null; }


    // All fields directly listed
    public static TypeStruct make(String name, boolean open, Field... fields) {
        TypeStruct ts = malloc(name, open, fields);
        TypeStruct t2 = ts.intern();
        if( t2==ts ) return ts;
        return VISIT.isEmpty() ? t2.free(ts) : ts.delayFree(ts);
    }
    // New open struct with no fields
    public static TypeStruct open( String name ) { return make(name,true); }

    // Array
    public static TypeStruct makeAry(String name, TypeInteger len, int lenAlias, Type body, int bodyAlias, boolean efinal) {
        return make(name,false,
                    Field.make("#" ,len , lenAlias,true  ,false),
                    Field.make("[]",body,bodyAlias,efinal,false));
    }
    public TypeStruct makeHigh() {
        Field[] fs = new Field[_fields.length];
        for( int i=0; i<_fields.length; i++ )
            fs[i] = _fields[i].makeFrom(Type.TOP);
        return make(_name,false,fs);
    }

    public TypeStruct add( Field f ) {
        assert _open && find(f._fname)==-1; // No double field names
        Field[] flds = Arrays.copyOf(_fields,_fields.length+1);
        flds[_fields.length] = f;
        return make(_name,true,flds);
    }
    public TypeStruct replace( Field f ) {
        assert !_open;
        Field[] flds = Arrays.copyOf(_fields,_fields.length);
        flds[find(f._fname)] = f;
        return make(_name,false,flds);
    }


    public final TypeStruct close() {
        return (TypeStruct)recurOpen()._close().recurClose();
    }
    @Override TypeStruct _close() {
        TypeStruct ts = (TypeStruct)VISIT.get(_name);
        if( ts!=null ) return ts;
        ts = recurPre(_name,false);
        Field[] flds = ts._fields;

        // Now start the recursion
        for( int i=0; i<flds.length; i++ )
            flds[i].setType(_fields[i]._t._close());

        return ts;
    }

    static final AryInt CEQUALS = new AryInt();

    public  static final TypeStruct BOT = open("$STRUCT");
    public  static final TypeStruct TOP = BOT.dual();
    public  static final TypeStruct TEST= make("test",false,Field.TEST);
    private static final TypeStruct ARY = makeAry("[]i64",TypeInteger.U32,-1,TypeInteger.BOT,-2,false);
    private static final TypeStruct STR = makeAry("[]u8" ,TypeInteger.U32,-1,TypeInteger.U8 ,-4,false);
    private static final TypeStruct ABC = makeAry("[]u8",TypeInteger.constant(3),-1,TypeConAryB.ABC,-4,true);

    // A pair of self-cyclic types
    private static final TypeStruct SINT0  = open("%SINT");
    private static final TypeStruct SFLT0  = open("%SFLT");
    private static final TypeStruct SINT1  = SINT0.add(Field.make("a", TypeInteger.U32, -1, false, false)).add(Field.make("s2",TypeMemPtr.make((byte)2,SFLT0),-2, false, false));
    private static final TypeStruct SFLT1  = SFLT0.add(Field.make("b", TypeFloat  .F32, -3, false, false)).add(Field.make("s1",TypeMemPtr.make((byte)2,SINT1),-4, false, false));
    public  static final TypeStruct SFLT2  = SFLT1.close();


    public static void gather(ArrayList<Type> ts) {
        ts.add(BOT);
        ts.add(TEST);
        ts.add(ARY);
        ts.add(STR);
        ts.add(ABC);
        ts.add(SINT0);
        ts.add(SFLT0);
        ts.add(SINT1);
        ts.add(SFLT1);
        ts.add(SFLT2);
        ts.add(((TypeMemPtr)(SFLT2.field("s1")._t))._obj);
        // Break cyclic init: built a struct
        Field fcalloc = Field.make("calloc",TypeFunPtr.CALLOC,-2,true,true);
        TypeStruct scalloc = make("calloc",false,fcalloc);
        ts.add(scalloc);

    }

    // Find field index by name
    public int find(String fname) {
        for( int i=0; i<_fields.length; i++ )
            if( _fields[i]._fname.equals(fname) )
                return i;
        return -1;
    }
    public Field field(String fname) {
        int idx = find(fname);
        return idx== -1 ? null : _fields[idx];
    }

    // Find field index by alias
    public int findAlias( int alias ) {
        for( int i=0; i<_fields.length; i++ )
            if( _fields[i]._alias==alias )
                return i;
        return -1;
    }

    @Override
    TypeStruct xmeet(Type t) {
        TypeStruct that = (TypeStruct) t;
        assert !isFree() && !that.isFree();
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

        // if equal, no matters.
        // if short is BOT, chop     ; recurPre on short.
        // if short is TOP, copy long; recurPre on long.
        TypeStruct min = _fields.length < that._fields.length ? this : that;
        if( _fields.length != that._fields.length && (min._open ^ min==this) )
            return that.xmeet(this);

        // Check all other fields are sanely similar; same struct type but
        // different field contents (e.g. field "age" is either 'int' or '18').
        int len = Math.min(_fields.length,that._fields.length);
        for( int i=0; i<len; i++ ) {
            Field f0 = _fields[i], f1 = that._fields[i];
            if( !f0._fname.equals(f1._fname) || f0._alias != f1._alias || f0._one != f1._one )
                return BOT;
        }

        // See if we get a mid-recursion hit; breaks cycles
        Integer pid = pid(that);
        TypeStruct ts = (TypeStruct)VISIT.get(pid);
        if( ts!=null ) return ts;
        // If not mid-recursion, we are now; flag recursion.
        boolean notRecur = VISIT.isEmpty();
        if( notRecur ) recurOpen();

        // Setup and install the type, prior to recursing, so we can find our
        // recursive self again.
        ts = recurPre(pid,_open | that._open);

        // Recurse all common fields
        Field[] flds = ts._fields;
        for( int i=0; i<len; i++ ) {
            Field f0 = _fields[i], f1 = that._fields[i];
            flds[i].setType(f0._t.meet(f1._t ));
            flds[i]._final = f0._final | f1._final;
        }

        // Handle extra fields via `_open`
        for( int i=len; i<_fields.length; i++ )
            flds[i].setType(_fields[i]._t );

        // Cleanup post-recursion; intern/install at cycle end
        if( notRecur ) ts = (TypeStruct)ts.recurClose();
        return ts;
    }

    @Override
    TypeStruct xdual() {
        if( _name=="$STRUCT" )
            return malloc("$STRUCT",false,new Field[0]);
        Field[] flds = new Field[_fields.length];
        for( int i=0; i<_fields.length; i++ )
            flds[i] = _fields[i].dual();
        return malloc(_name,!_open,flds);
    }

    // Recursive dual
    @Override TypeStruct rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        Field[] flds = new Field[_fields.length];
        TypeStruct d = malloc(_name,!_open,flds);
        (_dual = d)._dual = this; // Cross link duals
        for( int i=0; i<_fields.length; i++ )
            flds[i] = _fields[i]._terned ? _fields[i].dual() : _fields[i].rdual();
        return d;
    }

    // Is forward-reference
    @Override public boolean isFRef() { return _open; }

    @Override boolean _isConstant() {
        if( VISIT.containsKey(_uid) ) return true; // Cycles assume constant
        VISIT.put(_uid,this);
        // Check all fields for being constant
        for( Field field : _fields )
            if( !field._isConstant() )
                return false;
        return true;
    }

    // All fields are final
    @Override boolean _isFinal() {
        if( _open ) return false;     // May have more more non-final fields
        if( VISIT.containsKey(_uid) ) // Test: been here before?
            return true;              // Cycles assume final
        VISIT.put(_uid,this);         // Set: dont do this again
        for( Field fld : _fields )
            if( !fld._isFinal() )
                return false;
        return true;
    }

    private TypeStruct recurPre(Object key, boolean open) {
        // Make a clone of original; suitable for hashing so can build
        // e.g. TMPs to clone as part of cycles - but will fail the 'eq' check
        // until the entire cycle is built.
        Field[] flds  = new Field[_fields.length];
        for( int i=0; i<flds.length; i++ )
            flds[i] = _fields[i].malloc(); // Blank copy, but can be hashed
        TypeStruct ts = malloc(_name, open, flds );
        VISIT.put(key,ts);
        return ts;
    }

    // Make a read-only version
    @Override TypeStruct _makeRO() {
        // Check for already visited
        TypeStruct ts = (TypeStruct)VISIT.get(_name);
        if( ts!=null ) return ts;   // Already visited
        ts = recurPre(_name,_open); // Make a new type with blank fields
        Field[] flds = ts._fields;
        for( Field fld : flds ) fld._final = true;

        // Now start the recursion
        for( int i=0; i<flds.length; i++ )
            flds[i].setType(_fields[i]._t._makeRO());

        return ts;
    }


    // All fields are at GLB already
    boolean isGLB2() {
        if( VISIT.containsKey(_uid) ) return true; // Cycles assume GLB
        VISIT.put(_uid,this);
        for( Field fld : _fields )
            if( !fld.isGLB2() )
                return false;
        return true;
    }

    // Keeps the same struct, but lower-bounds all fields.
    public TypeStruct glb2() {
        TypeStruct ts = (TypeStruct)VISIT.get(_name);
        if( ts!=null ) return ts;
        ts = recurPre(_name,_open);
        Field[] flds = ts._fields;
        for( int i=0; i<flds.length; i++ )
            flds[i]._final = true ;

        // Now start the recursion
        for( int i=0; i<flds.length; i++ )
            flds[i].setType(_fields[i]._t._glb(true));

        return ts;
    }

    // log_size for a struct is not defined, unless its exactly some power of
    // 2.  *Total size* is well-defined, and is available in the offsets.
    @Override public int log_size() { throw Utils.TODO(); }
    @Override public int size() { return offset(_fields.length); }
    @Override public int alignment() {
        int align = 0;
        for( Field f : _fields )
            align = Math.max(align, f._t.alignment());
        return align;
    }

    // If false, always false.
    // If true , maybe true, need to check recursive fields.
    private boolean static_eq( TypeStruct ts ) {
        return _name.equals(ts._name) && _open==ts._open && _fields.length==ts._fields.length;
    }

    @Override boolean eq(Type t) {
        // Recursive; so use cyclic equals
        if( !VISIT.isEmpty() ) {
            assert CEQUALS.isEmpty();
            boolean rez = cycle_eq(t);
            CEQUALS.clear();
            return rez;
        }
        // Normal equals
        TypeStruct ts = (TypeStruct)t; // Invariant
        if( !static_eq(ts) ) return false;
        if( _fields==ts._fields ) return true;
        for( int i = 0; i < _fields.length; i++ )
            if( !_fields[i].eq(ts._fields[i]) )
                return false;
        return true;
    }

    @Override boolean cycle_eq(Type t) {
        if( this==t ) return true;
        TypeStruct ts = (TypeStruct)t; // Invariant
        if( !static_eq(ts) ) return false;
        if( _fields==ts._fields ) return true;
        // Check to see if we've ever compared this pair of types before;
        // if so, then assume the cycle is equal here.
        int pid = pid(ts);
        if( CEQUALS.find(pid)!= -1 ) return true;
        CEQUALS.push(pid);
        // Recursively check fields
        for( int i = 0; i < _fields.length; i++ )
            if( !_fields[i].cycle_eq(ts._fields[i]) )
                return false;
        return true;
    }

    @Override
    int hash() {
        long hash = _name.hashCode() ^ (_open ? 4 : 0);
        for( Field f : _fields )
            hash = Utils.rot(hash,13) ^ ((long)f._fname.hashCode() * f._alias);
        return Utils.fold(hash);
    }

    @Override int nkids() { return _fields.length; }
    @Override Type at( int idx ) { return _fields[idx]; }
    @Override void set( int idx, Type t ) { _fields[idx] = (Field)t; }

    @Override
    SB _print(SB sb, BitSet visit, boolean html ) {
        if( isFree() ) return sb.p("FREE:").p(_name);
        if( isAry() && field("[]")._t instanceof TypeConAry con )
            return sb.p(con.str());
        sb.p(_name);
        if( html || isAry() )
            return sb;
        sb.p(" {");
        for( Field f : _fields )
            (f._t ==null ? sb.p("---") : f._t.print(sb,visit,html)).p(f._final ? " " : " !").p(f._fname).p("; ");
        if( _open ) sb.p("... ");
        return sb.p("}");
    }

    @Override public String str() { return (isFree() ? "FREE:":"")+_name; }


    public boolean isAry() { return _fields.length>=2 && _fields[_fields.length-1]._fname=="[]"; }

    public int aryBase() {
        assert isAry();
        if( _offs==null ) _offs = offsets();
        return _offs[1];
    }
    public int aryScale() {
        assert isAry();
        return _fields[1]._t.log_size();
    }


    // Field offsets as packed byte offsets
    private int[] _offs;  // Lazily computed and not part of the type semantics
    public int offset(int idx) {
        if( _offs==null ) _offs = offsets();
        return _offs[idx];
    }
    private int[] offsets() {    // Field byte offsets
        assert CodeGen.CODE._phase.ordinal() >= CodeGen.Phase.Opto.ordinal();
        // Compute a layout for a collection of fields
        assert _fields != null; // No forward refs

        // Compute a layout
        _offs = new int[_fields.length+1];
        if( isAry() ) {         // Arrays used fixed u32 len, padding to elems, var length
            assert _fields[1]._fname=="[]";
            // Pad out to the element alignment
            int align = _fields[1]._t.alignment();
            _offs[1] = (4 + ((1<<align)-1)) & -(1<<align);
            if( _fields[1]._t instanceof TypeConAry ary ) // Array length is well-defined for constants, not defined for runtime arrays
                _offs[2] = _offs[1]+ary.len();

        } else {
            int[] cnts = new int[5]; // Count of fields at log field size
            int flen = _fields.length;
            for( int i=0; i<flen; i++ )
                if( !_fields[i]._one )
                    cnts[_fields[i]._t.log_size()]++; // Log size is 0(byte), 1(i16/u16), 2(i32/f32), 3(i64/dbl)
            int off = 0, idx = 0; // Base common struct fields go here, e.g. Mark/Klass
            // Compute offsets to the start of each power-of-2 aligned fields.
            int[] offs = new int[4];
            for( int i=3; i>=0; i-- ) {
                offs[i] = off;
                off += cnts[i]<<i;
            }
            // Assign offsets to all fields.
            // Really a hidden radix sort.
            for( int i=0; i<flen; i++ ) {
                if( _fields[i]._one ) continue;
                int log = _fields[i]._t.log_size();
                _offs[idx++] = offs[log]; // Field offset
                offs[log] += 1<<log;      // Next field offset at same alignment
                cnts[log]--;              // Count down, should be all zero at end
            }
            _offs[flen] = off; // Max struct size, no trailing padding
        }
        return _offs;
    }

    // Field order with increasing offset
    public int[] layout() {
        offset(0);
        int len = _offs.length;
        int[] is = new int[len];
        for( int i=0; i<len; i++ ) is[i] = i;
        // Stoopid hand rolled bubble sort
        for( int i=0; i<len; i++ )
            for( int j=i+1; j<len; j++ )
                if( _offs[is[j]] < _offs[is[i]] )
                    { int tmp = is[i]; is[i] = is[j]; is[j] = tmp; }
        return is;
    }

}
