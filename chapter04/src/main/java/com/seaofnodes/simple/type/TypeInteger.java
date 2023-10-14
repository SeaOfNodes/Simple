package com.seaofnodes.simple.type;

/**
 * Integer Type
 */
public class TypeInteger extends Type {

    public final static TypeInteger TOP = new TypeInteger(false, 0);
    public final static TypeInteger BOTTOM = new TypeInteger(false, 1);

    private final boolean _is_con;

    /**
     * The constant value or
     * if not constant then 0=bottom, 1=top.
     */
    private final long _con;

    public TypeInteger(boolean is_con, long con) { _is_con = is_con; _con = con; }

    public static TypeInteger constant(long con) { return new TypeInteger(true, con); }

    public boolean isTop() { return TOP.equals(this); }

    public boolean isBottom() { return BOTTOM.equals(this); }

    @Override
    public String toString() {
      return _print(new StringBuilder()).toString();
    }

    @Override 
    public StringBuilder _print(StringBuilder sb) { return sb.append(_con); }

    @Override
    public boolean isConstant() { return _is_con; }

    public long value() { return _con; }

    @Override
    public Type meet(Type other) {
        if (other instanceof TypeInteger i) {
            if (isConstant() && equals(i)) {
                return this;
            }
            else if (isBottom() || i.isBottom()) {
                return TypeInteger.BOTTOM;
            }
            else if (isConstant() && i.isTop()) {
                return this;
            }
            else if (isTop() && i.isConstant()) {
                return i;
            }
        }
        return super.meet(other);
    }

    @Override
    public boolean equals( Object o ) {
        if( o==this ) return true;
        if( !(o instanceof TypeInteger i) ) return false;
        return _con==i._con && _is_con==i._is_con;
    }
}
