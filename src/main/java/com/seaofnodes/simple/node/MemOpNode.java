package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Field;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

/**
 * Convenience common base for Load and Store.
 */
public abstract class MemOpNode extends Node {

    public final String _name;
    public final int _alias;
    public final Type _declaredType;


    public MemOpNode(String name, int alias, Type glb, Node... nodes ) {
        super(nodes);
        _name  = name;
        _alias = alias;
        _declaredType = glb;
    }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }

    @Override
    boolean eq(Node n) {
        MemOpNode mem = (MemOpNode)n; // Invariant
        return _alias==mem._alias;    // When comparing types error to use "equals"; always use "=="
    }

    @Override
    int hash() { return _alias; }

    @Override
    String err() {
        Type ptr = ptr()._type;
        return (ptr==Type.BOTTOM || (ptr instanceof TypeMemPtr tmp && tmp._nil) )
            ? "Might be null accessing '" + _name + "'"
            : null;
    }
}
