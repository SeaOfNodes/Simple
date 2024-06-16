package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
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

        // Push a Load up through a Phi, as long as it collapses on at least one arm.
        //   BEFORE (2 Sts, 1 Ld):          AFTER (1 St):
        //   if( pred ) ptr.x = e0;         val = pred ? e0
        //   else       ptr.x = e1;                    : e1;
        //   val = ptr.x;                   ptr.x = val;
        if( mem() instanceof PhiNode phi && phi.nIns()== 3 &&
            ((phi.in(1) instanceof StoreNode st1 && ptr()==st1.ptr() && phi.in(2)!=phi ) ||
             (phi.in(2) instanceof StoreNode st2 && ptr()==st2.ptr() && phi.in(1)!=phi ) ) ) {
            Node ld1 = new LoadNode(_field,phi.in(1),ptr()).peephole();
            Node ld2 = new LoadNode(_field,phi.in(2),ptr()).peephole();
            return new PhiNode(_field._fname,_type,phi.region(),ld1,ld2);
        }

        return null;
    }

    @Override
    public void addAntiDeps() {
        // Look at users of our Memory Input
        for (Node n : mem()._outputs)
            // Store at same alias?
            if( n instanceof StoreNode st && st._field._alias == _field._alias &&
                // Add anti-dep once
                Utils.find(_outputs, st) == -1 )
                st.addDef(this);
    }
}
