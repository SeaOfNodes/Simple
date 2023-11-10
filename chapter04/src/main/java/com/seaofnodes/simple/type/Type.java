package com.seaofnodes.simple.type;


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

    // ----------------------------------------------------------
    // Simple types are implemented fully here.  "Simple" means: the code and
    // type hierarchy are simple, not that the Type is conceptually simple.
    static final byte TBOT    = 0; // Bottom (ALL)
    static final byte TTOP    = 1; // Top    (ANY)
    static final byte TCTRL   = 2; // Ctrl flow bottom
    static final byte TSIMPLE = 3; // End of the Simple Types
    static final byte TINT    = 4; // All Integers; see TypeInteger
    static final byte TTUPLE  = 5; // Tuples; finite collections of unrelated Types, kept in parallel

    public final byte _type;

    public boolean is_simple() { return _type < TSIMPLE; }
    private static final String[] STRS = new String[]{"BOTTOM","TOP","CONTROL"};
    protected Type(byte type) { _type = type; }

    public static final Type BOTTOM   = new Type( TBOT   ); // ALL
    public static final Type TOP      = new Type( TTOP   ); // ANY
    public static final Type CONTROL  = new Type( TCTRL  ); // Ctrl

    public boolean isConstant() { return _type == TTOP; }

    public StringBuilder _print(StringBuilder sb) {return is_simple() ? sb.append(STRS[_type]) : sb;}

    public Type meet(Type other) { return Type.BOTTOM; }

}
