package com.seaofnodes.simple.type;

/**
 * Integer Type
 */
public class TypeInteger extends Type {

    public final static TypeInteger TOP = new TypeInteger(false, 0);
    public final static TypeInteger BOT = new TypeInteger(false, 1);
    public final static TypeInteger ZERO= new TypeInteger(true, 0);

    private final boolean _is_con;

    /**
     * The constant value or
     * if not constant then 0=bottom, 1=top.
     */
    private final long _con;

    public TypeInteger(boolean is_con, long con) {
        super(TINT);
        _is_con = is_con;
        _con = con;
    }

    public static TypeInteger constant(long con) { return new TypeInteger(true, con); }

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
    public Type meet(Type other) {
        if( this==other ) return this;
        if (!(other instanceof TypeInteger i)) return super.meet(other);
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
    public boolean equals( Object o ) {
        if( o==this ) return true;
        if( !(o instanceof TypeInteger i) ) return false;
        return _con==i._con && _is_con==i._is_con;
    }
}
