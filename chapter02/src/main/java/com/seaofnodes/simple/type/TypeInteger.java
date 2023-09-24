package com.seaofnodes.simple.type;

import java.lang.StringBuilder;

public class TypeInteger extends Type {

    public final long _con;

    public TypeInteger(long con) { _con = con; }

    @Override
    public String toString() {
      return _print(new StringBuilder()).toString();
    }

    @Override 
    public StringBuilder _print(StringBuilder sb) { return sb.append(_con); }

    @Override
    public boolean isConstant() { return true; }

    public long getConstant() { return _con; }

    @Override
    public boolean equals( Object o ) {
        if( o==this ) return true;
        if( !(o instanceof TypeInteger i) ) return false;
        return _con==i._con;
    }
}
