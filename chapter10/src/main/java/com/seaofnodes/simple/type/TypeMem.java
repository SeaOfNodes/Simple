package com.seaofnodes.simple.type;

import java.util.Objects;

/**
 * Represents a slice of memory corresponding to an alias
 */
public class TypeMem extends Type {

    private final AliasSource _aliasSource;

    private TypeMem(AliasSource aliasSource) {
        super(TMEM);
        this._aliasSource = aliasSource;
    }

    public static TypeMem make(AliasSource aliasSource) { return new TypeMem(aliasSource).intern(); }

    @Override
    protected Type xmeet(Type t) {
        TypeMem other = (TypeMem) t;
        if (other._aliasSource.alias() == _aliasSource.alias()) return this;
        else throw new RuntimeException("Unexpected meet between Mem types");
    }

    @Override
    int hash() {
        return Objects.hash(_type, _aliasSource.alias());
    }

    @Override
    boolean eq(Type t) {
        if (t instanceof TypeMem ts)
            return ts._aliasSource.alias() == _aliasSource.alias();
        return false;
    }

    @Override
    public StringBuilder _print(StringBuilder sb) { return sb.append("Mem#").append(_aliasSource.alias()); }
}
