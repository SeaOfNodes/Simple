package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeStruct;
import java.util.BitSet;

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

    // GraphVis DOT code (must be valid Java identifiers) and debugger labels
    @Override public String  label() { return "ld_"+mlabel(); }
    // GraphVis node-internal labels
    @Override public String glabel() { return "." +_name; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        if( mem()._type instanceof TypeMem mem &&
            // No constant folding if ptr might null-check
            _declaredType != mem._t && err()==null ) {
            assert mem._alias == _alias;
            return  _declaredType.join(mem._t);
        }
        return _declaredType;
    }

    @Override
    public Node idealize() {
        Node ptr = ptr();

        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr == st.ptr() && off() == st.off() ) { // Must check same object
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            return st.val();
        }

        // Simple Load-after-New on same address.
        if( mem() instanceof ProjNode p && p.in(0) instanceof NewNode nnn &&
            ptr == nnn.proj(1) ) // Must check same object
            return nnn.findAlias(_alias); // Load from New init

        // Load-after-Store on same address, but bypassing provably unrelated
        // stores.  This is a more complex superset of the above two peeps.
        // "Provably unrelated" is really weak.
        Node mem = mem();
        outer:
        while( true ) {
            switch( mem ) {
            case StoreNode st:
                if( ptr == st.ptr() && off() == st.off() )
                    return st.val(); // Proved equal
                // Can we prove unequal?  Offsets do not overlap?
                if( !off()._type.join(st.off()._type).isHigh() && // Offsets overlap
                    !neverAlias(ptr,st.ptr()) )                   // And might alias
                    break outer; // Cannot tell, stop trying
                // Pointers cannot overlap
                mem = st.mem(); // Proved never equal
                break;
            case PhiNode phi:
                break outer;    // Assume related
            case ProjNode mproj:
                if( mproj.in(0) instanceof NewNode nnn1 ) {
                    if( ptr instanceof ProjNode pproj && pproj.in(0) == mproj.in(0) )
                        return nnn1.findAlias(_alias); // Load from New init
                    if( !(ptr instanceof ProjNode pproj && pproj.in(0) instanceof NewNode nnn2) )
                        break outer; // Cannot tell, ptr not related to New
                    mem = nnn1.in(_alias);// Bypass unrelated New
                    break;
                } else throw Utils.TODO();
            default:
                throw Utils.TODO();
            }
        }

        // Push a Load up through a Phi, as long as it collapses on at least
        // one arm.  If at a Loop, the backedge MUST collapse - else we risk
        // spinning the same transform around the loop indefinitely.
        //   BEFORE (2 Sts, 1 Ld):          AFTER (1 St, 0 Ld):
        //   if( pred ) ptr.x = e0;         val = pred ? e0
        //   else       ptr.x = e1;                    : e1;
        //   val = ptr.x;                   ptr.x = val;
        if( mem() instanceof PhiNode memphi && memphi.region()._type == Type.CONTROL && memphi.nIns()== 3 &&
            off() instanceof ConstantNode ) {
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
        return new LoadNode(_name,_alias,_declaredType,mem.in(idx),ptr instanceof PhiNode && ptr.in(0)==mem.in(0) ? ptr.in(idx) : ptr, off()).peephole();
    }
    private static boolean neverAlias( Node ptr1, Node ptr2 ) {
        return ptr1.in(0) != ptr2.in(0) &&
            // Unrelated allocations
            ptr1 instanceof ProjNode && ptr1.in(0) instanceof NewNode &&
            ptr2 instanceof ProjNode && ptr2.in(0) instanceof NewNode;
    }

    // Profitable if we find a matching Store on this Phi arm.
    private boolean profit(PhiNode phi, int idx) {
        Node px = phi.in(idx);
        if( px==null ) return false;
        if( px._type instanceof TypeMem mem && mem._t.isHighOrConst() ) { px.addDep(this); return true; }
        if( px instanceof StoreNode st1 && ptr()==st1.ptr() && off()==st1.off() ) { px.addDep(this); return true; }
        return false;
    }
}
