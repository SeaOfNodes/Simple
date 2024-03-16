package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

import java.util.BitSet;

public class NewNode extends Node {

    TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node... inputs) {
        super(inputs);
        this._ptr = ptr;
    }

    public TypeMemPtr ptr() { return _ptr; }

    @Override
    public String label() {
        return "New";
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb;
    }

    @Override
    public Type compute() {
        return null;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
