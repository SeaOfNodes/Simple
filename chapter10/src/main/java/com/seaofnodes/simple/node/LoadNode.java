package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeField;

import java.util.BitSet;

public class LoadNode extends Node {

    TypeField _field;

    public LoadNode(TypeField field, Node memPtr, Node memSlice) {
        super(null, memPtr);
        _field = field;
    }

    @Override
    public String label() {
        return "Load";
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
