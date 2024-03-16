package com.seaofnodes.simple.type;

/**
 * Represents all of Memory
 */
public class TypeMem extends Type {
    public TypeMem() {
        super(TMEM);
    }

    @Override
    int hash() { return _type; }

    @Override
    boolean eq(Type t) {
        if (t instanceof TypeMem tmem)
            return true;
        return false;
    }

    public static TypeMem TALLMEM = new TypeMem().intern();
}
