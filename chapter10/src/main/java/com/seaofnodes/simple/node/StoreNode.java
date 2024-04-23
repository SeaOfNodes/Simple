package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.Field;
import com.seaofnodes.simple.type.TypeMem;

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
    public StoreNode(Field field, Node memSlice, Node memPtr, Node value) {
        super(field, memSlice, memPtr, value);
    }

    @Override
    public String label() { return "Store"; }
    @Override
    public String glabel() { return "."+_field._fname+" ="; }
    @Override
    public boolean isMem() { return true; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_field._fname).append("=").append( val()).append(";");
    }

    @Override
    public Type compute() {
        return TypeMem.make(_field._alias);
    }

    @Override
    public Node idealize() {
        return null;
    }
}
