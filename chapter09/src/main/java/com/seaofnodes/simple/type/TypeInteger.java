package com.seaofnodes.simple.type;

/**
 * Integer Type
 */
public class TypeInteger extends Type {

    public final static TypeInteger TOP = make(false, 0);
    public final static TypeInteger BOT = make(false, 1);
    public final static TypeInteger ZERO= make(true , 0);

    private final boolean _is_con;

    /**
     * The constant value or
     * if not constant then 0=bottom, 1=top.
     */
    private final long _con;

    private TypeInteger(boolean is_con, long con) {
        super(TINT);
        _is_con = is_con;
        _con = con;
    }
    public static TypeInteger make(boolean is_con, long con) {
        return new TypeInteger(is_con,con).intern();
    }

    public static TypeInteger constant(long con) { return make(true, con); }

    public boolean isTop() { return !_is_con && _con==0; }
    public boolean isBot() { return !_is_con && _con==1; }

    @Override 
    public StringBuilder _print(StringBuilder sb) {
        if (isTop()) return sb.append("IntTop");
        if (isBot()) return sb.append("IntBot");
        return sb.append(_con);
    }

    @Override
    public boolean isConstant() { return _is_con; }

    public long value() { return _con; }

    @Override
    public Type xmeet(Type other) {
        TypeInteger i = (TypeInteger)other; // Contract
        // BOT wins
        if (   isBot() ) return this;
        if ( i.isBot() ) return i   ;
        // TOP loses
        if ( i.isTop() ) return this;
        if (   isTop() ) return i   ;
        assert isConstant() && i.isConstant();
        return _con==i._con ? this : TypeInteger.BOT;
    }

    @Override
    int hash() { return (int)(_con ^ (_is_con ? 0 : 0x4000)); }
    @Override
    public boolean eq( Type t ) {
        TypeInteger i = (TypeInteger)t; // Contract
        return _con==i._con && _is_con==i._is_con;
    }
}
