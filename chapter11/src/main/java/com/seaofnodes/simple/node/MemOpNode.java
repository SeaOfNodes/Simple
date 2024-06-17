package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Field;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

/**
 * Convenience common base for Load and Store.
 */
abstract class MemOpNode extends Node {

    protected Field _field;

    public MemOpNode(Field field, Node memSlice, Node memPtr) {
        super(null, memSlice, memPtr);
        _field = field;
    }
    public MemOpNode(Field field, Node memSlice, Node memPtr, Node value) {
        this(field, memSlice, memPtr);
        addDef(value);
    }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }

    @Override
    boolean eq(Node n) {
        MemOpNode mem = (MemOpNode)n; // Invariant
        return _field.equals(mem._field);
    }

    @Override
    int hash() {
        return _field._alias;
    }

    @Override
    String err() {
        Type ptr = ptr()._type;
        return (ptr==Type.BOTTOM || (ptr instanceof TypeMemPtr tmp && tmp._nil) )
            ? "Might be null accessing '" + _field.str() + "'"
            : null;
    }
}
