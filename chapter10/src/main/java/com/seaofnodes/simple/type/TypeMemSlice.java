package com.seaofnodes.simple.type;

import java.util.Objects;

/**
 * Represents a slice of memory corresponding to an alias
 */
public class TypeMemSlice extends Type {

    private final AliasSource _aliasSource;

    public TypeMemSlice(AliasSource aliasSource) {
        super(TMEMSLICE);
        this._aliasSource = aliasSource;
    }

    @Override
    int hash() {
        return Objects.hash(_type, _aliasSource.alias());
    }

    @Override
    boolean eq(Type t) {
        if (t instanceof TypeMemSlice ts)
            return ts._aliasSource.alias() == _aliasSource.alias();
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) {
        return sb.append(_aliasSource.aliasName());
    }
}
