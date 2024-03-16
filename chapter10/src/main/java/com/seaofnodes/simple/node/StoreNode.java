package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeField;

import java.util.BitSet;

public class StoreNode extends Node {

    TypeField _field;

    public StoreNode(TypeField field, Node memPtr, Node value, Node memSlice) {
        super(null, memPtr, value);
        _field = field;
    }

    @Override
    public String label() {
        return "Store";
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
