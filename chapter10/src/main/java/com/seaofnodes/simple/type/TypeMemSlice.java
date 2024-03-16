package com.seaofnodes.simple.type;

import java.util.Objects;

public class TypeMemSlice extends Type {

    private final TypeField _field;

    public TypeMemSlice(TypeField _field) {
        super(TMEMSLICE);
        this._field = _field;
    }

    @Override
    int hash() {
        return Objects.hash(_type, _field._alias);
    }

    @Override
    boolean eq(Type t) {
        if (t instanceof TypeMemSlice ts)
            return ts._field._alias == _field._alias;
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(_field.aliasName());
    }
}
