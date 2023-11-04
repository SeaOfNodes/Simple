package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class DeadNode extends Node {

    public static final DeadNode DEAD_CTRL = new DeadNode(Type.XCONTROL);

    private DeadNode(Type t) {
        super();
        this._type = t;
    }

    @Override
    public String label() {
        return "DEAD";
    }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb.append(label());
    }

    @Override
    public Type compute() {
        return _type;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
