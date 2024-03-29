package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.StructField;

import java.util.BitSet;

/**
 * Store represents setting a value to a memory based object, in chapter 10
 * this means a field inside a struct.
 */
public class StoreNode extends MemOpNode {

    /**
     * @param field The struct field we are assigning to
     * @param memSlice The memory alias node - this is updated after a Store
     * @param memPtr The ptr to the struct where we will store a value
     * @param value Value to be stored
     */
    public StoreNode(StructField field, Node memSlice, Node memPtr, Node value) {
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
