package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;


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
     * @param memSlice The whole-memory node - this is updated after a Store
     * @param memPtr The ptr to the struct from where we load a field
     */
    public LoadNode(String name, Type glb, Node memSlice, Node memPtr) {
        super(name, memSlice, memPtr, null);
        _declaredType = glb;
    }

    @Override boolean canDrop(MemOpNode other, Node dep) {
        return super.canDrop(other,dep) && _declaredType==((LoadNode)other)._declaredType && !clobbered(dep);
    }

    // A Store directly clobbers this memory. A Phi might lead to a clobber
    // through a cycle; conservatively stop rather than searching beyond it.
    private boolean clobbered(Node dep) {
        Node mem = mem();
        mem.addDepForwards(dep);
        for( Node use : mem._outputs ) {
            if( use==null ) continue;
            use.addDepForwards(dep);
            if( use instanceof StoreNode || use instanceof PhiNode ) return true;
        }
        return false;
    }

    @Override
    public String label() { return "Load"; }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) { return p.p(".").p(_name); }

    @Override
    public Type compute() {
        return _declaredType;
    }

    @Override
    public Node idealize() {

        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr() == st.ptr() && _name.equals(st._name) ) { // Must check same object
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
                Node ld1 = new LoadNode(_name,_declaredType,memphi.in(1),ptr()).peephole();
                Node ld2 = new LoadNode(_name,_declaredType,memphi.in(2),ptr()).peephole();
                return new PhiNode(_name,_type,memphi.region(),ld1,ld2);
            }
        }

        return null;
    }

    // Profitable if we find a matching Store on this Phi arm.
    private boolean profit(PhiNode phi, int idx) {
        Node px = phi.in(idx);
        return px!=null && px.addDep(this) instanceof StoreNode st1 && ptr()==st1.ptr() && _name.equals(st1._name);
    }
}
