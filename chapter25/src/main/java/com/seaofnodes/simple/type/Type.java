package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.*;
import java.util.*;

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

public class Type /*implements Cloneable*/ {
    // Interning tables

    // Main intern table; a collection of all unique types.
    static final HashMap<Type,Type> INTERN = new HashMap<>();
    // Starting intern table; used to rapidly reset the main table between tests
    private static final HashMap<Type,Type> INTERN0= new HashMap<>();
    // Lifetime management during cyclic installs is complex; this is used to
    // track created-but-already-interned objects
    private static final Ary<Type> FREES = new Ary<>(Type.class);
    // "visit" bit management, based solely on _uid
    static final BitSet BITS = new BitSet();

    // "visit" bit management.  Key varies by kind of visit:

    // - "close" an open cyclic type.  Used by the Parser, it will hit on
    //   same-named structs, even if the structs have different states of
    //   completion/fields.
    // - "uid/name" - Either the UID or name works (unlike "close" which must
    //   use the name).  Used to make cyclic copies with changes, like make
    //   read-only where there should be only 1 copy of a name in a cycle.
    // - "pair of uids" - For recursive MEET, where we need to key off two types
    static final HashMap<Object,Type> VISIT = new HashMap<>();

    // Every Type has a UID to allow visit bits on cyclic visits
    private static int UID=1;
    public char _uid;           // Unique ID for every type

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
    static final byte TCONARY =10; // Constant array
    static final byte TRPC    =11; // Return Program Control (Return PC or RPC)
    static final byte TTUPLE  =12; // Tuples; finite collections of unrelated Types, kept in parallel

    static final byte TCYCLIC =13; // Has internal pointers, needs recursive treatment
    static final byte TMEMPTR =13; // Memory pointer to a struct type
    static final byte TFUNPTR =14; // Function pointer; unique signature and code address (just a bit)
    static final byte TMEM    =15; // All memory (alias 0) or A slice of memory - with specific alias
    static final byte TSTRUCT =16; // Structs; tuples with named fields
    static final byte TFLD    =17; // Named fields in structs

    // Basic RTTI, useful for a lot of fast tests.
    public final byte _type;
    public boolean _terned;

    public boolean is_simple() { return _type < TSIMPLE; }
    private static final String[] STRS = new String[]{"Bot","Top","Ctrl","~Ctrl","null","~nil"};
    static final int[] CNTS = new int[TFLD+1];
    protected Type(byte type) {
        _type = type;           // RTTI
        _uid = (char)UID++;     // A unique ID for every type
        assert _uid!=0;         // Overflow
        CNTS[type]++;
    }

    public static final Type BOTTOM   = new Type( TBOT   ).intern(); // ALL
    public static final Type TOP      = BOTTOM.dual();
    public static final Type CONTROL  = new Type( TCTRL  ).intern(); // Ctrl
    public static final Type XCONTROL = CONTROL.dual();
    public static final Type NIL      = new Type( TNIL   ).intern(); // low null of all flavors
    public static final Type XNIL     = NIL.dual();
    public static Type[] gather() {
        ArrayList<Type> ts = new ArrayList<>();
        ts.add(BOTTOM);
        ts.add(CONTROL);
        ts.add(NIL);
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
        TypeConAry.gather(ts);

        int sz = ts.size();
        for( int i = 0; i < sz; i++ )
            ts.add(ts.get(i).dual());
        return ts.toArray(new Type[ts.size()]);
    }

    // Is high or on the lattice centerline.
    public boolean isHigh       () { return _type==TTOP || _type==TXCTRL || _type==TXNIL; }
    public boolean isHighOrConst() { return isHigh() || isConstant(); }

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
    <T extends Type> T intern() {
        //assert check();
        assert !_terned;        // Do not ask for already-interned
        T t2 = (T)INTERN.get(this);
        if( t2!=null ) {
            assert t2._dual != null;  // Prior is complete with dual
            assert this != t2;        // Do not hashcons twice, should not get self back
            return t2;                // Return prior
        }
        // Mid recursive type construction
        if( !VISIT.isEmpty() && _type >= TCYCLIC ) {
            // No intern attempt at all
            return (T)this;
        }
        // Not in type table
        _dual = null;           // No dual yet
        INTERN.put(this,this);  // Put in table without dual
        _terned = true;
        //Util.hash_quality_check_per(INTERN,"INTERN");
        T d = xdual(); // Compute dual without requiring table lookup, and not setting name
        _dual = d;
        if( this==d ) return d; // Self-symmetric?  Dual is self
        assert !equals(d);      // Self-symmetric is handled by caller
        assert d._dual==null;   // Else dual-dual not computed yet
        assert INTERN.get(d)==null;
        d._dual = (T)this;
        INTERN.put(d,d);        // Install dual also
        d._terned = true;
        //assert check();
        return (T)this;
    }

    // Recursive visit elsewhere, interns with dual already
    <T extends Type> T _intern() {
        //assert check();
        assert !_terned;
        Type t = INTERN.get(this);
        if( t!=null ) return (T)t.delayFree(this).delayFree(_dual);
        assert !INTERN.containsKey( _dual );
        INTERN.put(this,this);
        INTERN.put(_dual,_dual);
        _terned = _dual._terned = true;
        //assert check();
        return (T)this;
    }

    private static boolean check() {
        for( Type t : INTERN.keySet() ) {
            Type t2 = INTERN.get(t);
            assert t==t2;
        }
        return true;
    }

    public static Type find(int uid) {
        for( Type t : INTERN.keySet() )
            if( t._uid==uid ) return t;
        return null;
    }


    int _hash;          // Hash cache; not-zero when set.
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
    boolean       eq(Type t) { return this==t; }
    boolean cycle_eq(Type t) { assert _type < TCYCLIC; return eq(t); }
    // A pair of uids
    int pid( Type that ) {
        return _uid < that._uid ? (_uid<<16 | that._uid) : (that._uid<<16 | _uid);
    }

    // At/Set child at 'idx' to t
    Type at ( int idx ) { throw Utils.TODO(); }
    void set( int idx, Type t ) { throw Utils.TODO(); }
    int nkids() { assert _type < TTUPLE; return 0; }   // Number of kids


    // Clear and re-insert the basic Type INTERN table
    public static void reset() {
        VISIT.clear();
        FREES.clear();
        if( INTERN0.isEmpty() ) {
            // First time run all <clinit> and collect basic types
            Type t = TypeRPC  .BOT; // Force class load, intern
            Type u = TypeTuple.BOT; // Force class load, intern
            Type w = TypePtr  .PTR; // Force class load, intern
            INTERN0.putAll(INTERN);
        } else {
            // Later times, reset back to first time
            VISIT.put(0,CONTROL); // Defeat assert
            for( Iterator<Type> it = INTERN.keySet().iterator(); it.hasNext(); ) {
                Type t = it.next();
                if( !INTERN0.containsKey(t) )
                    { t._terned=false; t.free(t); }
                it.remove();
            }
            VISIT.clear();
            INTERN.putAll(INTERN0);
        }
    }

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


    // The dual is pre-computed on creation and available "for free" thereafter
    Type _dual;
    public final <T extends Type> T dual() { return (T)_dual; }

    <T extends Type> T xdual() {
        return (T)new Type(switch( _type ) {
            case TBOT  -> TTOP;
            case TTOP  -> TBOT;
            case TCTRL -> TXCTRL;
            case TXCTRL-> TCTRL;
            case TNIL  -> TXNIL;
            case TXNIL -> TNIL;
        default -> throw Utils.TODO(); // Should not reach here
        });
    }


    // ----------------------------------------------------------
    // Our lattice is defined with a MEET and a DUAL.
    // JOIN is dual of meet of both duals.
    public final Type join(Type t) {
        if( this==t ) return this;
        return dual().meet(t.dual()).dual();
    }

    // True if this "isa" t; e.g. 17 isa TypeInteger.BOT
    // Applies for pessimistic case
    public boolean isa( Type t )     { return meet(t)==t; }

    // True if this "isa" t up to named structures
    public boolean shallowISA( Type t ) { return isa(t); }

    public Type nonZero() { return TypePtr.NPTR; }

    // Make a zero version of this type, 0 for integers and null for pointers.
    public Type makeZero() { return Type.NIL; }

    // Is forward-reference
    public boolean isFRef() { return false; }

    // Cap at limits
    public Type oob() { return isHigh() ? TOP : BOTTOM; }

    // ----------------------------------------------------------

    // Cyclic types!  Flag the start of a cyclic type by putting a sentinel in
    // VISIT (and eventually clearing VISIT when done).  Then do a normal
    // recursive descent visit of all types; this will accumilate types without
    // interning them.  When done we have to visit the possibly cyclic type and
    // intern the whole cycle, possibly hitting the entire cycle on a prior
    // interned cycle.  Any sub-part might also be interned, including whole
    // disjoint cycles.

    // E.g. a new type: "A<->B -> C1<->D1" gets created; the "C1<->D1" cycle
    // already exists as "C0<->D0", but the "A<->B" cycle is new.  The "A<->B"
    // cycle will get interned - except the pointer to C1 needs to get replaced
    // with C0.  The "C1<->D1" cycle gets freed and "B" will point to the
    // "C0<->D0" cycle.

    Type recurOpen() { assert VISIT.isEmpty(); VISIT.put(0L,BOTTOM); return this; }
    Type recurClose() {
        VISIT.remove(0L);       // Just ignore the sentinel
        Ary<Type> ts = new Ary<>(Type.class);
        ts.addAll(VISIT.values());
        if( ts.find(this)== -1 ) ts.add(this);

        // Pass #1: replace already interned fields
        assert BITS.isEmpty();
        for( int i=0; i<ts._len; i++ )
            if( ts.at(i).tern()._terned )
                ts.del(i--);
        BITS.clear();

        // Pass#2: compute recursive duals
        for( Type t : ts )
            t.rdual();

        // Pass#3: Recursive install
        for( Type t : ts )
            if( !t._terned && INTERN.get(t)==null )
                t.install();
        // Upgrade the result
        Type rez = INTERN.get(this);
        assert rez!=null;

        // Pass#4?: Free up any created-but-already interned
        for( Type t : FREES )
            t.rfree();
        FREES.clear();
        VISIT.clear();
        return rez;
    }

    // Read-only recursive visits do not *make* cyclic types and so the cleanup
    // is much easier.
    static boolean recurClose(boolean rez) { VISIT.clear(); return rez; }

    final Type tern() {
        if( _terned ) return this;
        if( !BITS.get(_uid) ) {
            BITS.set(_uid);
            int nkids = nkids();
            for( int i=0; i<nkids; i++ )
                set(i,at(i).tern());
        }
        Type told = INTERN.get(this);
        return told==null ? this : told.delayFree(this);
    }

    // Recursive dual.  Visit all types recursively.  Each visited type is
    // *new*, and hits in the intern table, or not.  If not, we recursively
    // call rdual to get its dual, and cross-link the duals.  If hitting, we
    // use the intern type and mark the prior type for freeing.
    Type rdual() { assert !_terned; return xdual(); }

    // Recursively free type and children
    final void rfree() {
        if( isFree() ) return;
        int nkids = nkids();
        for( int i=0; i<nkids; i++ )
            if( !at(i)._terned )
                at(i).rfree();
        free(this);
    }

    Type free(Type free) { return this; }
    boolean isFree() { return false; }

    <T extends Type> T delayFree(Type free) {
        assert !free._terned;
        FREES.push(free);
        return (T)this;
    }

    final Type install() {
        if( this instanceof TypeStruct ) {
            Type x = _intern();     // Stop the recursion
            assert x==this;
        }
        int nkids = nkids();
        for( int i=0; i<nkids; i++ ) {
            Type kid = at(i);
            if( !kid._terned ) {
                Type ikid = kid.install();
                if( ikid != kid ) {
                    set(i,ikid);
                    _dual.set(i,ikid.dual());
                }
            }
        }
        return this instanceof TypeStruct ? this : _intern();
    }

    // Strict constant values, things on the lattice centerline.
    // Excludes both high and low values
    public final boolean isConstant() { return recurClose(recurOpen()._isConstant()); }
    boolean _isConstant() { return _type==TNIL; }

    // Are all reachable struct Fields are final?
    public final boolean isFinal() { return recurClose(recurOpen()._isFinal()); }
    boolean _isFinal() { assert _type < TCYCLIC; return true; }

    public final Type makeRO() {
        if( isFinal() ) return this;
        return recurOpen()._makeRO().recurClose();
    }
    Type _makeRO() { return this; }

    // Compute greatest lower bound in the lattice.  If values are in memory,
    // ints and floats cannot widen.
    public final boolean isGLB(boolean mem) { return recurClose(recurOpen()._isGLB(mem)); };
    boolean _isGLB(boolean mem) {
        return switch(_type) {
        case TBOT -> false;
        case TNIL -> false;
        case TCTRL -> true;
        case TXCTRL -> false;
        case TXNIL -> false;
        case TTOP -> false;
        default -> throw Utils.TODO();
        };
    }
    public final Type glb(boolean mem) {
        if( isGLB(mem) ) return this;
        Type glb = recurOpen()._glb(mem).recurClose();
        assert this.isa(glb);
        return glb;
    }
    Type _glb(boolean mem) { assert is_simple(); return Type.BOTTOM; }

    Type _close() { return this; }
    public Type widen() { return this; }

    // ----------------------------------------------------------

    // Size in bits to hold an instance of this type.
    // Sizes are expected to be between 1 and 64 bits.
    // Size 0 means this either takes no space (such as a known-zero field)
    // or isn't a scalar to be stored in memory.
    public int log_size () { return 3; } // log-size of a type; log_size for a struct is usually undefined
    public int size() { return 1<<log_size(); }
    public int alignment() { return log_size(); } // alignment; for structs, max align of Fields

    // ----------------------------------------------------------
    // Useful in the debugger, which calls toString everywhere.
    // This is a more verbose dev-friendly print.
    @Override
    public final String toString() {
        return print(new SB(), new BitSet(), false).toString();
    }
    public final String gprint() {
        return print(new SB(), new BitSet(), true).toString();
    }
    public final SB  print(SB sb) { return print(sb, new BitSet(), false); }
    public final SB gprint(SB sb) { return print(sb, new BitSet(), true ); }

    // Used during recursive printing, or combined with SB Node printing
    public final SB print(SB sb, BitSet visit, boolean html ) {
        if( visit.get(_uid) ) return sb.p(str());
        visit.set(_uid);
        return _print(sb,visit,html);
    }
    SB _print(SB sb, BitSet visit, boolean html ) { return sb.p(str()); }

    // This is used by error messages, and is a shorted print.
    public String str() { return STRS[_type]; }
}
