package com.seaofnodes.simple.type;

import java.util.ArrayList;

/**
 * Float Type
 */
public class TypeFloat extends Type {

    public final static TypeFloat TOP = make(false, 0);
    public final static TypeFloat BOT = make(false, 1);
    public final static TypeFloat ZERO= make(true , 0);

    public final boolean _is_con;

    /**
     * The constant value or
     * if not constant then 1=bottom, 0=top.
     */
    public final double _con;

    private TypeFloat(boolean is_con, double con) {
        super(TFLT);
        _is_con = is_con;
        _con = con;
    }
    public static TypeFloat make(boolean is_con, double con) {
        return new TypeFloat(is_con,con).intern();
    }

    public static TypeFloat constant(double con) { return make(true, con); }

    public static void gather(ArrayList<Type> ts) { ts.add(ZERO); ts.add(BOT); }

    // FIXME this display format is problematic
    // In visualizer '#' gets prepended if its a constant
    @Override
    public StringBuilder print(StringBuilder sb) {
        if( this==TOP ) return sb.append("FltTop");
        if( this==BOT ) return sb.append("FltBot");
        return sb.append(_con);
    }

    @Override public String str() {
        if( this==TOP ) return "~flt";
        if( this==BOT ) return  "flt";
        return ""+_con;
    }

    /**
     * Display Type name in a format that's good for IR printer
     */
    @Override
    public StringBuilder typeName(StringBuilder sb) {
        if( this==TOP ) return sb.append("FltTop");
        if( this==BOT ) return sb.append("FltBot");
        return sb.append("Flt");
    }

    @Override public boolean isHigh() { return this==TOP; }
    @Override public boolean isHighOrConst() { return _is_con || _con==0; }
    @Override public boolean isConstant() { return _is_con; }

    public double value() { return _con; }

    @Override
    public Type xmeet(Type other) {
        // Invariant from caller: 'this' != 'other' and same class (TypeFloat)
        TypeFloat i = (TypeFloat)other; // Contract
        // BOT wins
        if ( this==BOT ) return this;
        if ( i   ==BOT ) return i   ;
        // TOP loses
        if ( i   ==TOP ) return this;
        if ( this==TOP ) return i   ;
        // Since both are constants, and are never equals (contract) unequals
        // constants fall to bottom
        return BOT;
    }

    @Override
    public Type dual() {
        if( isConstant() ) return this; // Constants are a self-dual
        return _con==0 ? BOT : TOP;
    }

    @Override
    public Type glb() { return BOT; }

    @Override public TypeFloat makeInit() { return ZERO; }

    @Override
    int hash() { return (int)(Double.hashCode(_con) ^ (_is_con ? 0 : 0x4000)); }
    @Override
    public boolean eq( Type t ) {
        TypeFloat i = (TypeFloat)t; // Contract
        return _con==i._con && _is_con==i._is_con;
    }
}
