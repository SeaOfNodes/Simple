package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.Field;

import java.util.BitSet;

/**
 * Load represents extracting a value from inside a memory object,
 * in chapter 10 this means Struct fields.
 */
public class LoadNode extends MemOpNode {

    /**
     * Load a value from a ptr.field.
     *
     * @param field The struct field we are loading
     * @param memSlice The memory alias node - this is updated after a Store
     * @param memPtr The ptr to the struct from where we load a field
     */
    public LoadNode(Field field, Node memSlice, Node memPtr) {
        super(field, memSlice, memPtr, null);
    }

    @Override
    public String label() { return "Load"; }

    @Override
    public String glabel() { return "."+_field._fname; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_field._fname); }

    @Override
    public Type compute() {
        return _field._type;
    }

    @Override
    public Node idealize() {

        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr() == st.ptr() ) { // Must check same object
            assert _field==st._field; // Equiv class aliasing is perfect
            return st.val();
        }

        return null;
    }
}
