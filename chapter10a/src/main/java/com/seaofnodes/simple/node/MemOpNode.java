package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

/**
 * Convenience common base for Load and Store.
 */
public abstract class MemOpNode extends Node {

    public final String _name;

    public MemOpNode(String name, Node memSlice, Node memPtr, Node value) {
        super(null, memSlice, memPtr, value);
        _name  = name;
    }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }

    // Extra conditions for factoring matching memory operations through a Phi.
    // The caller has already checked the opcode, input shape and control.
    boolean canDrop(MemOpNode other, Node dep) {
        ptr().addDep(dep);
        if( !_name.equals(other._name) || err()!=null ) return false;
        return !(ptr()._type instanceof TypeMemPtr p &&
                 other.ptr()._type instanceof TypeMemPtr q && p._obj!=q._obj);
    }

    @Override
    boolean eq(Node n) {
        MemOpNode mem = (MemOpNode)n; // Invariant
        return _name.equals(mem._name);
    }

    @Override
    int hash() { return _name.hashCode(); }

    @Override
    String err() {
        Type ptr = ptr()._type;
        return (ptr==Type.BOTTOM || (ptr instanceof TypeMemPtr tmp && tmp._nil) )
            ? "Might be null accessing '" + _name + "'"
            : null;
    }
}
