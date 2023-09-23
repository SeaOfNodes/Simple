package com.seaofnodes.simple.type;

import java.lang.Long;
import java.lang.StringBuilder;

public class TypeInteger extends Type {

    public final long _lo;
    public final long _hi;
    public static final TypeInteger INT = new TypeInteger(Long.MIN_VALUE,Long.MAX_VALUE);

    public TypeInteger (long lo, long hi) {
        _lo = lo;
        _hi = hi;
    }
    public TypeInteger(long value) {
        this(value, value);
    }

    @Override
    public String toString() {
      return _print(new StringBuilder()).toString();
    }

    @Override 
    public StringBuilder _print(StringBuilder sb) {
      if( isConstant() )
        return sb.append(_lo);
      if( this==INT )
        return sb.append("INT");
      return sb.append("[").append(_lo).append("..").append(_hi).append("]");
    }

    @Override
    public boolean isConstant() {
        return _lo == _hi;
    }

    public long getConstant() {
        if (isConstant())
            return _lo;
        throw new IllegalStateException("Not a constant");
    }

    @Override
    public boolean equals( Object o ) {
        if( o==this ) return true;
        if( !(o instanceof TypeInteger i) ) return false;
        return _lo==i._lo && _hi==i._hi;
    }
}
