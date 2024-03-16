package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeField;

abstract class MemOpNode extends Node {

    protected TypeField _field;

    public MemOpNode(TypeField field, Node memSlice, Node memPtr, Node value) {
        super(null, memSlice, memPtr, value);
        this._field = field;
    }

    public Node memSlice() { return in(1); }
    public Node ptr()      { return in(2); }
    public Node value()    { return nIns() > 3 ? in(3) : null; }
}
