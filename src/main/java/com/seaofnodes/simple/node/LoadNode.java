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

    Type _declaredType;
    /**
     * Load a value from a ptr.field.
     *
     * @param name  The field we are loading
     * @param memSlice The memory alias node - this is updated after a Store
     * @param memPtr The ptr to the struct from where we load a field
     */
    public LoadNode(String name, int alias, Type glb, Node memSlice, Node memPtr) {
        super(name, alias, memSlice, memPtr, null);
        _declaredType = glb;
    }

    @Override
    public String label() { return "Load"; }

    @Override
    public String glabel() { return "."+_name; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        return _declaredType;
    }

    @Override
    public Node idealize() {

        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr() == st.ptr() ) { // Must check same object
            assert Utils.eq(_name,st._name); // Equiv class aliasing is perfect
            return st.val();
        }

        // Push a Load up through a Phi, as long as it collapses on at least
        // one arm.  If at a Loop, the backedge MUST collapse - else we risk
        // spinning the same transform around the loop indefinitely.
        //   BEFORE (2 Sts, 1 Ld):          AFTER (1 St, 0 Ld):
        //   if( pred ) ptr.x = e0;         val = pred ? e0
        //   else       ptr.x = e1;                    : e1;
        //   val = ptr.x;                   ptr.x = val;
        if( mem() instanceof PhiNode memphi && memphi.region()._type == Type.CONTROL && memphi.nIns()== 3 ) {
            // Profit on RHS/Loop backedge
            if( profit(memphi,2) ||
                // Else must not be a loop to count profit on LHS.
                (!(memphi.region() instanceof LoopNode) && profit(memphi,1)) ) {
                Node ld1 = new LoadNode(_name,_alias,_declaredType,memphi.in(1),ptr()).peephole();
                Node ld2 = new LoadNode(_name,_alias,_declaredType,memphi.in(2),ptr()).peephole();
                return new PhiNode(_name,_type,memphi.region(),ld1,ld2);
            }
        }

        return null;
    }

    // Profitable if we find a matching Store on this Phi arm.
    private boolean profit(PhiNode phi, int idx) {
        Node px = phi.in(idx);
        return px!=null && px.addDep(this) instanceof StoreNode st1 && ptr()==st1.ptr();
    }
}
