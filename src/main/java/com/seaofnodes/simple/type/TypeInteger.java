package com.seaofnodes.simple.type;

/**
 * Integer Type
 */
public class TypeInteger extends Type {

    public final static TypeInteger ZERO= new TypeInteger(0);

    /**
     * The constant value observed for this type
     */
    private final long _con;

    public TypeInteger(long con) {
        super(TINT);
        _con = con;
    }

    public static TypeInteger constant(long con) { return new TypeInteger(con); }

    @Override
    public String toString() {
      return _print(new StringBuilder()).toString();
    }

    @Override
    public StringBuilder _print(StringBuilder sb) { return sb.append(_con); }

    @Override
    public boolean isConstant() { return true; }

    public long value() { return _con; }

    @Override
    public boolean equals( Object o ) {
        if( o==this ) return true;
        if( !(o instanceof TypeInteger i) ) return false;
        return _con==i._con;
    }
}
