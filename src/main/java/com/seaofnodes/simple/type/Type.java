package com.seaofnodes.simple.type;

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
    static final byte TSIMPLE = 4; // End of the Simple Types
    static final byte TINT    = 5; // All Integers; see TypeInteger
    static final byte TFLT    = 6; // All Integers; see TypeInteger
    static final byte TTUPLE  = 7; // Tuples; finite collections of unrelated Types, kept in parallel
    static final byte TMEM    = 8; // All memory (alias 0) or A slice of memory - with specific alias
    static final byte TMEMPTR = 9; // Memory pointer type
    static final byte TSTRUCT =10; // Structs; tuples with named fields
    static final byte TFLD    =11; // Fields into struct
    static final byte TARRAY  =12; // Array

    public final byte _type;

    public boolean is_simple() { return _type < TSIMPLE; }
    private static final String[] STRS = new String[]{"Bot","Top","Ctrl","~Ctrl"};
    protected Type(byte type) { _type = type; }

    public static final Type BOTTOM   = new Type( TBOT   ).intern(); // ALL
    public static final Type TOP      = new Type( TTOP   ).intern(); // ANY
    public static final Type CONTROL  = new Type( TCTRL  ).intern(); // Ctrl
    public static final Type XCONTROL = new Type( TXCTRL ).intern(); // ~Ctrl
    public static Type[] gather() {
        ArrayList<Type> ts = new ArrayList<>();
        ts.add(BOTTOM);
        ts.add(CONTROL);
        Field.gather(ts);
        TypeInteger.gather(ts);
        TypeFloat.gather(ts);
        TypeMem.gather(ts);
        TypeMemPtr.gather(ts);
        TypeStruct.gather(ts);
        TypeTuple.gather(ts);
        int sz = ts.size();
        for( int i = 0; i < sz; i++ )
            ts.add(ts.get(i).dual());
        return ts.toArray(new Type[ts.size()]);
    }

    // Is high or on the lattice centerline.
    public boolean isHigh       () { return _type==TTOP || _type==TXCTRL; }
    public boolean isHighOrConst() { return _type==TTOP || _type==TXCTRL; }

    // Strict constant values, things on the lattice centerline.
    // Excludes both high and low values
    public boolean isConstant() { return false; }

    /**
     * Display Type name in a format that's good for IR printer
     */
    public StringBuilder typeName( StringBuilder sb) { return print(sb); }


    // ----------------------------------------------------------

    // Notes on Type interning: At the moment it is not easy to reset the
    // interned types because we hold static references to several types and
    // these are scattered around.  This means the INTERN cache will retain all
    // types from every run of the Parser.  For this to work correctly types
    // must be rigorous about defining when they are the same.  Also types need
    // to be immutable once defined.  The rationale for interning is
    // *correctness* with cyclic type definitions.  Simple structural recursive
    // checks go exponential with merely sharing, but with cycles they will
    // stack overflow and crash.  Intering means we do not need to have sharing
    // checks with every type compare, only during interning.

    // Factory method which interns "this"
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
        // 'this' is {TCTRL,TXCTRL}
        if( !t.is_simple() ) return BOTTOM;
        // 't' is {TCTRL,TXCTRL}
        return _type==TCTRL || t._type==TCTRL ? CONTROL : XCONTROL;
    }

    public Type dual() {
        return switch( _type ) {
        case TBOT -> TOP;
        case TTOP -> BOTTOM;
        case TCTRL -> XCONTROL;
        case TXCTRL -> CONTROL;
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

    /** Compute greatest lower bound in the lattice */
    public Type glb() { return _type==TCTRL ? CONTROL : BOTTOM; }
    /** Compute least upper bound in the lattice */
    public Type lub() { return _type==TCTRL ? XCONTROL : TOP; }

    // Make an initial/default version of this type.  Typically, 0 for integers
    // and null for nullable pointers.
    public Type makeInit() { return this; }
    // Make a zero version of this type, 0 for integers and null for pointers.
    public Type makeZero() { return null; }
    // Make a non-zero version of this type, if possible.  Integers attempt to
    // exclude zero from their range and pointers become not-null.
    public Type nonZero() { return glb(); }

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
    public int log_size() { throw Utils.TODO(); }

    // ----------------------------------------------------------
    // Useful in the debugger, which calls toString everywhere.
    // This is a more verbose dev-friendly print.
    @Override
    public final String toString() {
        return print(new StringBuilder()).toString();
    }

    public StringBuilder print(StringBuilder sb) { return is_simple() ? sb.append(STRS[_type]) : sb;}

    // This is used by error messages, and is a shorted print.
    public String str() { return STRS[_type]; }
}
