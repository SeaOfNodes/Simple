package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.ArrayList;

/**
 * Load represents extracting a value from inside a memory object,
 * in chapter 10 this means Struct fields.
 */
public class LoadNode extends MemOpNode {

    /**
     * Load a value from a ptr.field.
     *
     * @param name  The field we are loading
     * @param mem   The memory alias node - this is updated after a Store
     * @param ptr   The ptr to the struct base from where we load a field
     * @param off   The offset inside the struct base
     */
    public LoadNode(String name, int alias, Type glb, Node mem, Node ptr, Node off) {
        super(name, alias, glb, mem, ptr, off);
    }

    @Override boolean canDrop(MemOpNode other, Node dep) {
        return super.canDrop(other,dep) && !clobbered(dep);
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
                use instanceof MemMergeNode ||
                use instanceof NewNode ) return true;
        }
        return false;
    }

    // Debugger label
    @Override public String  label() { return "ld_"+mlabel(); }
    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        Type t = MemMergeNode.contents(mem(),_alias,this);
        // Update declared forward ref to the actual.
        if( _declaredType.isFRef() && t instanceof TypeMemPtr tmp && !tmp.isFRef() )
            _declaredType = tmp;
        return err()==null ? _declaredType.join(t) : _declaredType;
    }

    @Override
    public Node idealize() {
        if( mem() instanceof MemMergeNode merge ) {
            setDef(1,IterPeeps.add(merge.alias(_alias)));
            return this;
        }
        Node ptr = ptr();

        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            _alias==st._alias && ptr == st.ptr() && off() == st.off() ) { // Must check same object
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            return st.val();
        }

        // Simple Load-after-New on same address.
        if( mem() instanceof ProjNode p && p.in(0) instanceof NewNode nnn &&
            ptr == nnn.proj(0) ) // Must check same object
            return nnn.in(nnn.findAlias(_alias)); // Load from New init

        // Load-after-Store on same address, but bypassing provably unrelated
        // stores.  This is a more complex superset of the above two peeps.
        // "Provably unrelated" is really weak.
        if( ptr instanceof ReadOnlyNode ro )
            ptr = ro.in(1);
        Node mem = mem();
        outer:
        while( true ) {
            mem.addDep(this);
            switch( mem ) {
            case MemMergeNode merge:
                mem = merge.alias(_alias);
                break;
            case StoreNode st:
                if( _alias==st._alias && ptr == st.ptr() && off() == st.off() )
                    return castRO(st.val()); // Proved equal
                // Can we prove unequal?  Offsets do not overlap?
                if( _alias==st._alias && !off()._type.join(st.off()._type).isHigh() && // Offsets overlap
                    !neverAlias(ptr,st.ptr()) )                   // And might alias
                    break outer; // Cannot tell, stop trying
                // Pointers cannot overlap
                mem = st.mem(); // Proved never equal
                break;
            case PhiNode phi:      break outer;  // Assume related
            case ConstantNode top: break outer;  // Assume shortly dead
            case ProjNode mproj:
                if( mproj.in(0) instanceof NewNode nnn1 ) {
                    if( ptr instanceof ProjNode pproj && pproj.in(0) == mproj.in(0) )
                        return castRO(nnn1.in(nnn1.findAlias(_alias))); // Load from New init
                    if( !(ptr instanceof ProjNode pproj && pproj.in(0) instanceof NewNode nnn2) )
                        break outer; // Cannot tell, ptr not related to New
                    mem = nnn1.mem();// Bypass unrelated New
                    break;
                } else break outer;
            default:
                break outer;
            }
        }

        // Push a Load up through a Phi, as long as it collapses on at least
        // one arm.  If at a Loop, the backedge MUST collapse - else we risk
        // spinning the same transform around the loop indefinitely.
        //   BEFORE (2 Sts, 1 Ld):          AFTER (1 St, 0 Ld):
        //   if( pred ) ptr.x = e0;         val = pred ? e0
        //   else       ptr.x = e1;                    : e1;
        //   val = ptr.x;                   ptr.x = val;
        if( mem() instanceof MemPhiNode memphi && memphi.region()._type == Type.CONTROL && memphi.nIns()== 3 &&
            // Offset can be hoisted
            off() instanceof ConstantNode &&
            // Pointer can be hoisted
            hoistPtr(ptr,memphi)  ) {

            // Profit on RHS/Loop backedge
            if( profit(memphi,2) ||
                // Else must not be a loop to count profit on LHS.
                (!(memphi.region() instanceof LoopNode) && profit(memphi,1)) ) {
                Node ld1 = ld(1);
                Node ld2 = ld(2);
                return new PhiNode(_name,_declaredType,memphi.region(),ld1,ld2);
            }
        }

        return null;
    }

    private Node ld( int idx ) {
        Node mem = mem(), ptr = ptr();
        LoadNode ld = new LoadNode(_name,_alias,_declaredType,mem.in(idx),ptr instanceof PhiNode && ptr.in(0)==mem.in(0) ? ptr.in(idx) : ptr,off());
        ld.setDef(0,in(0));
        return ld.peephole();
    }

    private static boolean neverAlias( Node ptr1, Node ptr2 ) {
        return ptr1.in(0) != ptr2.in(0) &&
            // Unrelated allocations
            ptr1 instanceof ProjNode && ptr1.in(0) instanceof NewNode &&
            ptr2 instanceof ProjNode && ptr2.in(0) instanceof NewNode;
    }

    private static boolean hoistPtr(Node ptr, PhiNode memphi ) {
        // Can I hoist ptr above the Region?
        if( !(memphi.region() instanceof RegionNode r) )
            return false;       // Dead or dying Region/Phi
        // If ptr from same Region, then yes, just use hoisted split pointers
        if( ptr instanceof PhiNode pphi && pphi.region() == r )
            return true;

        // No, so can we lift this ptr?
        CFGNode cptr = ptr.cfg0();
        if( cptr != null )
            // Pointer is controlled high
            // TODO: Really needs to be the LCA of all inputs is high
            return cptr.idepth() <= r.idepth();

        // Dunno without a longer walk
        return false;
    }

    // Profitable if we find a matching Store on this Phi arm.
    private boolean profit(PhiNode phi, int idx) {
        Node px = phi.in(idx);
        if( px==null ) return false;
        px.addDep(this);
        if( px._type instanceof TypeMem mem && mem._t.isHighOrConst() ) return true;
        if( px instanceof StoreNode st1 && _alias==st1._alias && ptr()==st1.ptr() && off()==st1.off() ) return true;
        return false;
    }

    // Read-Only is a deep property, and cannot be cast-away
    private Node castRO(Node rez) {
        if( ptr()._type.isFinal() && !rez._type.isFinal() )
            return new ReadOnlyNode(rez).peephole();
        return rez;
    }

    // Memory writers which must follow this read. A packaging node is not a
    // write, but can hide New's input, so follow this alias through aggregates.
    public ArrayList<Node> antiDeps() {
        ArrayList<Node> deps = new ArrayList<>();
        antiDeps(mem(),deps,new BitSet());
        return deps;
    }

    private void antiDeps(Node mem, ArrayList<Node> deps, BitSet visit) {
        if( visit.get(mem._nid) ) return;
        visit.set(mem._nid);
        for( Node use : mem._outputs ) {
            if( use instanceof MemMergeNode merge ) {
                Node slice = _alias<merge.nIns() ? merge.in(_alias) : null;
                if( (slice==mem) || (slice==null && merge.in(1)==mem) )
                    antiDeps(merge,deps,visit);
            } else if( (use instanceof StoreNode st && st._alias==_alias) ||
                       (use instanceof NewNode nnn && nnn.field(_alias)!=null) ||
                       (use instanceof MemPhiNode phi && phi._alias==_alias) ||
                       (use instanceof BulkMemPhiNode phi && !phi.isSplit(_alias)) ) {
                if( !deps.contains(use) ) deps.add(use);
            }
        }
    }
}
