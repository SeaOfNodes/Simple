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

    public MemOpNode(String name, int alias, Type glb, Node mem, Node ptr, Node off) {
        super(null, mem, ptr, off);
        _name  = name;
        _alias = alias;
        _declaredType = glb;
    }
    public MemOpNode(String name, int alias, Type glb, Node mem, Node ptr, Node off, Node value) {
        this(name, alias, glb, mem, ptr, off);
        addDef(value);
    }

    //
    String mlabel() { return _name.equals("[]") ? "ary" : (_name.equals("#") ? "len" : _name); }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }
    public Node off() { return in(3); }

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
        // Already an error, but better error messages come from elsewhere
        if( ptr == Type.BOTTOM ) return null;
        // Better be a not-nil TMP
        if( ptr instanceof TypeMemPtr tmp && !tmp._nil )
            return null;
        return "Might be null accessing '" + _name + "'";
    }
}
