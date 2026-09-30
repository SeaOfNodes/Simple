package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.IterPeeps;
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
        super(name, alias, null, memSlice, memPtr);
        _declaredType = glb;
    }

    @Override boolean canDrop(MemOpNode other, Node dep) {
        return super.canDrop(other,dep) && _declaredType==((LoadNode)other)._declaredType && !clobbered(dep);
    }

    // Check only immediate memory users. Stores clobber memory; Phis and
    // aggregates might lead to a clobber, so stop rather than search further.
    private boolean clobbered(Node dep) {
        Node mem = mem();
        mem.addDepForwards(dep);
        for( Node use : mem._outputs ) {
            if( use==null ) continue;
            use.addDepForwards(dep);
            if( use instanceof StoreNode || use instanceof PhiNode ||
                use instanceof MemMergeNode ) return true;
        }
        return false;
    }

    @Override
    public String label() { return "Load"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        return _declaredType;
    }

    @Override
    public Node idealize() {
        if( mem() instanceof MemMergeNode merge ) {
            setDef(1,IterPeeps.add(merge.alias(_alias)));
            return this;
        }


        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr() == st.ptr() && _alias==st._alias ) { // Must check same object
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            return st.val();
        }

        // Push a Load up through a Phi, as long as it collapses on at least
        // one arm.  If at a Loop, the backedge MUST collapse - else we risk
        // spinning the same transform around the loop indefinitely.
        //   BEFORE (2 Sts, 1 Ld):          AFTER (1 St, 0 Ld):
        //   if( pred ) ptr.x = e0;         val = pred ? e0
        //   else       ptr.x = e1;                    : e1;
        //   val = ptr.x;                   ptr.x = val;
        if( mem() instanceof MemPhiNode memphi && memphi.region()._type == Type.CONTROL && memphi.nIns()== 3 ) {
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
        return px!=null && px.addDep(this) instanceof StoreNode st1 && ptr()==st1.ptr() && _alias==st1._alias;
    }
}
