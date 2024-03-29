package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeField;

import java.util.BitSet;

public class LoadNode extends MemOpNode {

    public LoadNode(TypeField field, Node memSlice, Node memPtr) {
        super(field, memSlice, memPtr, null);
    }

    @Override
    public String label() {
        return "Load";
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_field._fieldName); }

    @Override
    public Type compute() {
        return _field._fieldType;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
