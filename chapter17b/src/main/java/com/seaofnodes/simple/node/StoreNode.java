package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.type.*;


/**
 * Store represents setting a value to a memory based object, in chapter 10
 * this means a field inside a struct.
 */
public class StoreNode extends MemOpNode {

    private final boolean _init; // Initializing writes are allowed to write null

    /**
     * @param name  The struct field we are assigning to
     * @param mem   The memory alias node - this is updated after a Store
     * @param ptr   The ptr to the struct base where we will store a value
     * @param off   The offset inside the struct base
     * @param value Value to be stored
     */
    public StoreNode(String name, int alias, Type glb, Node mem, Node ptr, Node off, Node value, boolean init) {
        super(name, alias, glb, mem, ptr, off, value);
        _init = init;
    }

    // Debugger label
    @Override boolean canDrop(MemOpNode other, Node dep) {
        return super.canDrop(other,dep) && _init==((StoreNode)other)._init;
    }

    @Override public String  label() { return "st_"+mlabel(); }
    @Override public boolean isMem() { return true; }

    public Node val() { return in(4); }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(".").p(_name).p("=").p( val()).p(";");
    }

    @Override
    public Type compute() {
        Type val = val()._type;
        if( _declaredType.isFRef() && val instanceof TypeMemPtr tmp && !tmp.isFRef() )
            _declaredType = tmp;
        Type t = val.join(_declaredType).meet(MemMergeNode.contents(mem(),_alias,this));
        return TypeMem.make(_alias,t);
    }

    @Override
    public Node idealize() {
        if( mem() instanceof MemMergeNode merge ) {
            setDef(1,IterPeeps.add(merge.alias(_alias)));
            return this;
        }


        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() && _alias==st._alias &&  // Must check same object
            off()==st.off() &&  // And same offset
            ptr()._type instanceof TypeMemPtr && // No bother if weird dead pointers
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            checkOnlyUse(st) ) {
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            setDef(1,st.mem());
            return this;
        }

        // Simple store-after-new on same address.  Should pick up the
        // an init-store being stomped by a first user store.
        if( mem() instanceof ProjNode st  && st .in(0) instanceof NewNode nnn &&
            ptr() instanceof ProjNode ptr && ptr.in(0) == nnn &&
            ptr()._type instanceof TypeMemPtr tmp && // No bother if weird dead pointers
            // Cannot fold a store of a single element over the array body initializer value
            !(tmp._obj.isAry() && tmp._obj._fields[1]._alias==_alias) &&
            // Very sad strong cutout: val has to be legal to hoist to a New
            // input, which means it cannot depend on the New.  Many many
            // things are legal here but difficult to check without doing a
            // full dominator check.  Example failure:
            // "struct C { C? c; }; C self = new C { c=self; }"
            val()._type.isHighOrConst() &&
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            checkOnlyUse(st) &&
            // Folding away a broken store
            err()==null ) {
            nnn.setDef(nnn.findAlias(_alias),val());
            // Must retype the NewNode
            nnn  ._type = nnn.  compute();
            mem()._type = mem().compute();
            return st;
        }

        return null;
    }

    // Phi(mem, Store(phi,ptr,off,val)) -> Store(mem,ptr,off,Phi(load(mem),val)).
    // With no observers of the backedge store, only the last value matters.
    // The entry load preserves the field even when the loop takes zero trips.
    Node sink(MemPhiNode phi) {
        addDepForwards(phi);
        if( nOuts()!=1 ) return null;
        ptr().addDep(phi);
        // Start with a fixed field of an allocation dominating the loop.
        // In particular, do not sink stores through a loop-varying address.
        if( !(ptr() instanceof ProjNode p) || !(p.in(0) instanceof NewNode obj) ||
            !(ptr()._type instanceof TypeMemPtr tmp) || tmp._obj.isAry() ||
            !(off() instanceof ConstantNode) || err()!=null ) return null;
        LoopNode loop = (LoopNode)phi.region();
        // Bulk splitting identifies precise slices by Region/alias. Finish it
        // before replacing this memory point with a Store.
        for( Node use : loop._outputs )
            if( use instanceof BulkMemPhiNode ) {
                use.addDepForwards(phi);
                return null;
            }
        obj.addDep(phi);
        CFGNode ctrl = loop.entry();
        while( ctrl!=null && ctrl!=obj.cfg0() ) {
            ctrl.addDep(phi);
            ctrl=ctrl.idom(phi);
        }
        if( ctrl==null ) return null;

        Node mem = phi.in(1);
        Node init = new LoadNode(_name,_alias,_declaredType,mem,ptr(),off()).peephole();
        Node value = new PhiNode(_name,_declaredType,loop,init,val()).peephole();
        Node sink = new StoreNode(_name,_alias,_declaredType,mem,ptr(),off(),value,_init);
        // Break the memory cycle, killing the old store. Keep the Phi alive
        // even if that store was its last use, until our caller replaces it.
        phi.keep();
        phi.setDef(2,mem);
        phi.unkeep();
        return sink;
    }

    // Check that "mem" has no uses except "this"
    private boolean checkOnlyUse(Node mem) {
        if( mem.nOuts()==1 ) return true;
        // Add deps on the other uses (can be e.g. ScopeNode mid-parse) so that
        // when the other uses go away we can retry.
        for( Node use : mem._outputs )
            if( use != this )
                use.addDep(this);
        return false;
    }

    @Override
    String err() {
        String err = super.err();
        if( err != null ) return err;
        TypeMemPtr tmp = (TypeMemPtr)ptr()._type;
        if( tmp._ro || tmp._obj.field(_name)._final )
            return "Cannot modify final field '"+_name+"'";
        Type t = val()._type;
        return _init || t.isa(_declaredType) ? null : "Cannot store "+t+" into field "+_declaredType+" "+_name;
    }
}
