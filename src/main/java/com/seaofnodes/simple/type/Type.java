package com.seaofnodes.simple.type;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * These types are part of a Monotone Analysis Framework,
 * @see <a href="https://www.cse.psu.edu/~gxt29/teaching/cse597s21/slides/08monotoneFramework.pdf">see for example this set of slides</a>.
 * <p>
 * The types form a lattice; @see <a href="https://en.wikipedia.org/wiki/Lattice_(order)">a symmetric complete bounded (ranked) lattice.</a>
 * <p>
 * This wild lattice theory will be needed later to allow us to easily beef up
 * the analysis and optimization of the Simple compiler... but we don't need it
 * now, just know that it is coming along in a later Chapter.
 * <p>g
 * One of the fun things here is that while the theory is deep and subtle, the
 * actual implementation is darn near trivial and is generally really obvious
 * what we're doing with it.  Right now, it's just simple integer math to do
 * simple constant folding e.g. 1+2 == 3 stuff.
 */

public class Type {
    static final HashMap<Type,Type> INTERN = new HashMap<>();

    // ----------------------------------------------------------
    // Simple types are implemented fully here.  "Simple" means: the code and
    // type hierarchy are simple, not that the Type is conceptually simple.
    static final byte TBOT    = 0; // Bottom (ALL)
    static final byte TTOP    = 1; // Top    (ANY)
    static final byte TCTRL   = 2; // Ctrl flow bottom
    static final byte TXCTRL  = 3; // Ctrl flow top (mini-lattice: any-xctrl-ctrl-all)
    static final byte TNIL    = 4; // low null of all flavors
    static final byte TXNIL   = 5; // high or choice null
    static final byte TSIMPLE = 6; // End of the Simple Types

    static final byte TPTR    = 7; // All nil-able scalar values
    static final byte TINT    = 8; // All Integers; see TypeInteger
    static final byte TFLT    = 9; // All Floats  ; see TypeFloat
    static final byte TMEMPTR =10; // Memory pointer to a struct type
    static final byte TFUNPTR =11; // Function pointer; unique signature and code address (just a bit)
    static final byte TTUPLE  =12; // Tuples; finite collections of unrelated Types, kept in parallel
    static final byte TMEM    =13; // All memory (alias 0) or A slice of memory - with specific alias
    static final byte TSTRUCT =14; // Structs; tuples with named fields
    static final byte TFLD    =15; // Named fields in structs
    static final byte TRPC    =16; // Return Program Control (Return PC or RPC)

    public final byte _type;

    public boolean is_simple() { return _type < TSIMPLE; }
    private static final String[] STRS = new String[]{"Bot","Top","Ctrl","~Ctrl","null","~nil"};
    protected Type(byte type) { _type = type; }

    public static final Type BOTTOM   = new Type( TBOT   ).intern(); // ALL
    public static final Type TOP      = new Type( TTOP   ).intern(); // ANY
    public static final Type CONTROL  = new Type( TCTRL  ).intern(); // Ctrl
    public static final Type XCONTROL = new Type( TXCTRL ).intern(); // ~Ctrl
    public static final Type NIL      = new Type( TNIL   ).intern(); // low null of all flavors
    public static final Type XNIL     = new Type( TXNIL  ).intern(); // high or choice null
    public static Type[] gather() {
        ArrayList<Type> ts = new ArrayList<>();
        ts.add(BOTTOM);
        ts.add(CONTROL);
        ts.add(NIL);
        ts.add(XNIL);
        TypeNil.gather(ts);
        TypePtr.gather(ts);
        TypeInteger.gather(ts);
        TypeFloat.gather(ts);
        TypeMemPtr.gather(ts);
        TypeFunPtr.gather(ts);
        TypeMem.gather(ts);
        Field.gather(ts);
        TypeStruct.gather(ts);
        TypeTuple.gather(ts);
        TypeRPC.gather(ts);
        int sz = ts.size();
        for( int i = 0; i < sz; i++ )
            ts.add(ts.get(i).dual());
        return ts.toArray(new Type[ts.size()]);
    }

    // Is high or on the lattice centerline.
    public boolean isHigh       () { return _type==TTOP || _type==TXCTRL || _type==TXNIL; }
    public boolean isHighOrConst() { return isHigh() || isConstant(); }

    // Strict constant values, things on the lattice centerline.
    // Excludes both high and low values
    public boolean isConstant() { return _type==TNIL; }

    // ----------------------------------------------------------

    // Notes on Type interning: At the moment it is not easy to reset the
    // interned types because we hold static references to several types and
    // these are scattered around.  This means the INTERN cache will retain all
    // types from every run of the Parser.  For this to work correctly types
    // must be rigorous about defining when they are the same.  Also types need
    // to be immutable once defined.  The rationale for interning is
    // *correctness* with cyclic type definitions.  Simple structural recursive
    // checks go exponential with merely sharing, but with cycles they will
    // stack overflow and crash.  Interning means we do not need to have sharing
    // checks with every type compare, only during interning.

    // Factory method which interns "this"
    @SuppressWarnings("unchecked")
    public  <T extends Type> T intern() {
        T nnn = (T)INTERN.get(this);
        if( nnn==null )
            INTERN.put(nnn=(T)this,this);
        return nnn;
    }

    private int _hash;          // Hash cache; not-zero when set.
    @Override
    public final int hashCode() {
        if( _hash!=0 ) return _hash;
        _hash = hash();
        if( _hash==0 ) _hash = 0xDEADBEEF; // Bad hash from subclass; use some junk thing
        return _hash;
    }
    // Override in subclasses
    int hash() { return _type; }

    @Override
    public final boolean equals( Object o ) {
        if( o==this ) return true;
        if( !(o instanceof Type t)) return false;
        if( _type != t._type ) return false;
        return eq(t);
    }
    // Overridden in subclasses; subclass can assume "this!=t" and java classes are same
    boolean eq(Type t) { return true; }


    // ----------------------------------------------------------
    public final Type meet(Type t) {
        // Shortcut for the self case
        if( t == this ) return this;
        // Same-type is always safe in the subclasses
        if( _type==t._type ) return xmeet(t);
        // TypeNil vs TypeNil meet
        if( this instanceof TypeNil ptr0 && t instanceof TypeNil ptr1 )
            return ptr0.nmeet(ptr1);
        // Reverse; xmeet 2nd arg is never "is_simple" and never equal to "this".
        if(   is_simple() ) return this.xmeet(t   );
        if( t.is_simple() ) return t   .xmeet(this);
        return Type.BOTTOM;     // Mixing 2 unrelated types
    }

    // Compute meet right now.  Overridden in subclasses.
    // Handle cases where 'this.is_simple()' and unequal to 't'.
    // Subclassed xmeet calls can assert that '!t.is_simple()'.
    Type xmeet(Type t) {
        assert is_simple(); // Should be overridden in subclass
        // ANY meet anything is thing; thing meet ALL is ALL
        if( _type==TBOT || t._type==TTOP ) return this;
        if( _type==TTOP || t._type==TBOT ) return    t;

        // RHS TypeNil vs NIL/XNIL
        if( _type==  TNIL ) return t instanceof TypeNil ptr ? ptr.meet0() : (t._type==TXNIL ? TypePtr.PTR : BOTTOM);
        if( _type== TXNIL ) return t instanceof TypeNil ptr ? ptr.meetX() : (t._type== TNIL ? TypePtr.PTR : BOTTOM);
        // 'this' is only {TCTRL,TXCTRL}
        // Other non-simple RHS things bottom out
        if( !t.is_simple() ) return BOTTOM;
        // If RHS is NIL/XNIL
        if( t._type==TNIL || t._type==TXNIL ) return BOTTOM;
        // Both are {TCTRL,TXCTRL} and unequal to each other
        return _type==TCTRL || t._type==TCTRL ? CONTROL : XCONTROL;
    }

    public Type dual() {
        return switch( _type ) {
        case TBOT  -> TOP;
        case TTOP  -> BOTTOM;
        case TCTRL ->XCONTROL;
        case TXCTRL-> CONTROL;
        case TNIL  ->XNIL;
        case TXNIL -> NIL;
        default -> throw Utils.TODO(); // Should not reach here
        };
    }

    // ----------------------------------------------------------
    // Our lattice is defined with a MEET and a DUAL.
    // JOIN is dual of meet of both duals.
    public final Type join(Type t) {
        if( this==t ) return this;
        return dual().meet(t.dual()).dual();
    }

    // True if this "isa" t; e.g. 17 isa TypeInteger.BOT
    public boolean isa( Type t ) { return meet(t)==t; }

    // True if this "isa" t up to named structures
    public boolean shallowISA( Type t ) { return isa(t); }

    public Type nonZero() { return TypePtr.NPTR; }

    // Make a zero version of this type, 0 for integers and null for pointers.
    public Type makeZero() { return Type.NIL; }

    /** Compute greatest lower bound in the lattice */
    public Type glb() { return Type.BOTTOM; }

    // Is forward-reference
    public boolean isFRef() { return false; }

    // All reachable struct Fields are final
    public boolean isFinal() { return true; }

    // Make all reachable struct Fields final
    public Type makeRO() { return this; }

    // ----------------------------------------------------------

    // Size in bits to hold an instance of this type.
    // Sizes are expected to be between 1 and 64 bits.
    // Size 0 means this either takes no space (such as a known-zero field)
    // or isn't a scalar to be stored in memory.
    public int log_size() { return 3; }

    // ----------------------------------------------------------
    // Useful in the debugger, which calls toString everywhere.
    // This is a more verbose dev-friendly print.
    @Override
    public final String toString() {
        return print(new SB()).toString();
    }

    public SB  print(SB sb) { return sb.p(str()); }
    public SB gprint(SB sb) { return print(sb); }

    // This is used by error messages, and is a shorted print.
    public String str() { return STRS[_type]; }
}
