package com.seaofnodes.simple.type;

/**
 * Integer Type
 */
public class TypeInteger extends Type {

    public final static TypeInteger TOP = make(false, 0);
    public final static TypeInteger BOT = make(false, 1);
    public final static TypeInteger ZERO= make(true , 0);

    public final boolean _is_con;

    /**
     * The constant value or
     * if not constant then 1=bottom, 0=top.
     */
    public final long _con;

    private TypeInteger(boolean is_con, long con) {
        super(TINT);
        _is_con = is_con;
        _con = con;
    }
    public static TypeInteger make(boolean is_con, long con) {
        return new TypeInteger(is_con,con).intern();
    }

    public static TypeInteger constant(long con) { return make(true, con); }

    // FIXME this display format is problematic
    // In visualizer '#' gets prepended if its a constant
    @Override 
    public StringBuilder _print(StringBuilder sb) {
        if( this==TOP ) return sb.append("IntTop");
        if( this==BOT ) return sb.append("IntBot");
        return sb.append(_con);
    }

    /**
     * Display Type name in a format that's good for IR printer
     */
    @Override
    public StringBuilder typeName(StringBuilder sb) {
        if( this==TOP ) return sb.append("IntTop");
        if( this==BOT ) return sb.append("IntBot");
        return sb.append("Int");
    }

    @Override
    public boolean isHighOrConst() { return _is_con || _con==0; }

    @Override
    public boolean isConstant() { return _is_con; }

    public long value() { return _con; }

    @Override
    public Type xmeet(Type other) {
        // Invariant from caller: 'this' != 'other' and same class (TypeInteger)
        TypeInteger i = (TypeInteger)other; // Contract
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
    public Type widen() { return BOT; }

    @Override
    public Type dual() {
        if( isConstant() ) return this; // Constants are a self-dual
        return _con==0 ? BOT : TOP;
    }
    
    @Override
    int hash() { return (int)(_con ^ (_is_con ? 0 : 0x4000)); }
    @Override
    public boolean eq( Type t ) {
        TypeInteger i = (TypeInteger)t; // Contract
        return _con==i._con && _is_con==i._is_con;
    }
}
