package com.seaofnodes.simple.type;

public class TypeInteger extends Type {

    public final long _lo;
    public final long _hi;

    public TypeInteger (long lo, long hi) {
        _lo = lo;
        _hi = hi;
    }
    public TypeInteger(long value) {
        this(value, value);
    }


    @Override
    public boolean isConstant() {
        return _lo == _hi;
    }

    @Override
    public TypeInteger isInt() {
        return this;
    }

    public long getConstant() {
        if (isConstant())
            return _lo;
        throw new IllegalStateException("Not a constant");
    }
}
