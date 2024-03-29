package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeField;

import java.util.BitSet;

public class StoreNode extends MemOpNode {

    public StoreNode(TypeField field, Node memSlice, Node memPtr, Node value) {
        super(field, memSlice, memPtr, value);
    }

    @Override
    public String label() {
        return "Store";
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_field._fieldName).append("=").append(value()).append(";");
    }

    @Override
    public Type compute() {
        return memSlice()._type;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
