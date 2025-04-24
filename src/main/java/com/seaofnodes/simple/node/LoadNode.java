package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;
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
    public LoadNode(Parser.Lexer loc, String name, int alias, Type glb, Node mem, Node ptr, Node off) {
        super(loc, name, alias, true, glb, mem, ptr, off);
    }

    // GraphVis DOT code (must be valid Java identifiers) and debugger labels
    @Override public String  label() { return "ld_"+mlabel(); }
    // GraphVis node-internal labels
    @Override public String glabel() { return "." +_name; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        if( mem()._type instanceof TypeMem mem ) {
            // Update declared forward ref to the actual
            if( _declaredType.isFRef() && mem._t instanceof TypeMemPtr tmp && !tmp.isFRef() )
                _declaredType = tmp;
            // No lifting if ptr might null-check
            if( err()==null )
                return _declaredType.join(mem._t);
        }
        return _declaredType;
    }

    @Override
    public Node idealize() {
        Node ptr = ptr();
        Node mem = mem();

        // Simple Load-after-Store on same address.
        if( mem instanceof StoreNode st &&
            ptr == st.ptr() && off() == st.off() ) { // Must check same object
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            return extend(st.val());
        }

        // Simple Load-after-New on same address.
        if( mem instanceof ProjNode p && p.in(0) instanceof NewNode nnn &&
            ptr == nnn.proj(1) ) // Must check same object
            return zero(nnn);   // Load zero from new

        // Uplift control to a prior dominating load.
        for( Node memuse : mem._outputs )
            // Find a prior load, has same mem,ptr,off but higher ctrl
            if( memuse != this && memuse instanceof LoadNode ld && ptr==ld.ptr() && off()==ld.off() &&
                cfg0()!=null && cfg0()._idom(ld.cfg0(),this) == ld.cfg0() ) // Higher control means load is legal earlier
                return ld;

        // Load-after-Store on same address, but bypassing provably unrelated
        // stores.  This is a more complex superset of the above two peeps.
        // "Provably unrelated" is really weak.
        if( ptr instanceof ReadOnlyNode ro )
            ptr = ro.in(1);
        outer:
        while( true ) {
            switch( mem ) {
            case StoreNode st:
                if( ptr == addDep(st.ptr()) && off() == st.off() )
                    return extend(castRO(st.val())); // Proved equal
                // Can we prove unequal?  Offsets do not overlap?
                if( !off()._type.join(st.off()._type).isHigh() && // Offsets overlap
                    !neverAlias(ptr,st.ptr()) )                   // And might alias
                    break outer; // Cannot tell, stop trying
                // Pointers cannot overlap
                mem = st.mem(); // Proved never equal
                break;
            case PhiNode phi:
                // Assume related
                addDep(phi);
                break outer;
            case ConstantNode top: break outer;  // Assume shortly dead
            case ProjNode mproj: // Memory projection
                switch( mproj.in(0) ) {
                case NewNode nnn1:
                    if( ptr instanceof ProjNode pproj && pproj.in(0) == mproj.in(0) )
                        return zero(nnn1);
                    if( !(ptr instanceof ProjNode pproj && pproj.in(0) instanceof NewNode) )
                        break outer; // Cannot tell, ptr not related to New
                    mem = nnn1.in(nnn1.findAlias(_alias));// Bypass unrelated New
                    break;
                case StartNode  start: break outer;
                case CallEndNode cend: break outer; // TODO: Bypass no-alias call
                default: throw Utils.TODO();
                }
                break;
            case MemMergeNode merge:  mem = merge.alias(_alias);  break;

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

    // Load a flavored zero from a New
    private Node zero(NewNode nnn) {
        TypeStruct ts = nnn._ptr._obj;
        Type zero = ts._fields[ts.findAlias(_alias)]._type.makeZero();
        return castRO(new ConstantNode(zero).peephole());
    }

    private Node ld( int idx ) {
        Node mem = mem(), ptr = ptr();
        return new LoadNode(_loc,_name,_alias,_declaredType,mem.in(idx),ptr instanceof PhiNode && ptr.in(0)==mem.in(0) ? ptr.in(idx) : ptr, off()).peephole();
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
        if( px._type instanceof TypeMem mem && mem._t.isHighOrConst() ) return true;
        if( px instanceof StoreNode st1 && ptr()==st1.ptr() && off()==st1.off() ) return true;
        addDep(px);
        return false;
    }

    // Read-Only is a deep property, and cannot be cast-away
    private Node castRO(Node rez) {
        if( ptr()._type.isFinal() && !rez._type.isFinal() )
            return new ReadOnlyNode(rez).peephole();
        return rez;
    }

    // When a load bypasses a store, the store might truncate bits - and the
    // load will need to zero/sign-extend.
    private Node extend(Node val) {
        if( !(_declaredType instanceof TypeInteger ti) ) return val;
        if( ti._min==0 )        // Unsigned
            return new AndNode(null,val,con(ti._max));
        // Signed extension
        int shift = Long.numberOfLeadingZeros(ti._max)-1;
        Node shf = con(shift);
        if( shf._type==TypeInteger.ZERO )
            return val;
        Node shl = new ShlNode(null,val,shf.keep()).peephole();
        return new SarNode(null,shl,shf.unkeep());
    }
}
