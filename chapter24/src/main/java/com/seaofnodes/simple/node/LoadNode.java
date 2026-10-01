package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Utils;
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

    @Override boolean canDrop(MemOpNode other, Node dep) {
        return super.canDrop(other,dep) && !clobbered(dep);
    }

    // Check only immediate memory users. Writers clobber memory; Phis and
    // aggregates might lead to a clobber, so stop rather than search further.
    private boolean clobbered(Node dep) {
        Node mem = mem();
        dep.addDepForwards(mem);
        for( Node use : mem._outputs ) {
            if( use==null ) continue;
            dep.addDepForwards(use);
            if( use instanceof StoreNode || use instanceof PhiNode ||
                use instanceof MemMergeNode ||
                use instanceof NewNode || use instanceof CallNode ) return true;
        }
        return false;
    }

    // Debugger label
    @Override public String  label() { return "ld_"+mlabel(); }
    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        Type tmem = mem()._type;
        if( !(tmem instanceof TypeMem mem) )
            return tmem; // No memory yet?  Assume TOP/BOT
        assert !_declaredType.isFRef();
        // No lifting if ptr might null-check
        Type tptr = ptr()._type;
        if( err() != null )
            return _declaredType;
        if( !(tptr instanceof TypeMemPtr tmp) )
            return tptr; // No pointer yet?  Assume TOP/BOT
        // Load field from object
        Field f = tmp._obj.field(_name);
        // No field?  Open objects might yet get the field when falling;
        // closed objects with missing field are an error.
        if( f == null )
            return tmp.isHigh() ? Type.TOP : _declaredType;
        Type t = f._t;
        // Load member of constant array
        if( t instanceof TypeConAry ary )
            t = ary.elem();     // TODO: if offset is known, can peek the constant
        // Lift from declared type and memory input
        t = t.join(MemMergeNode.contents(mem(),_alias,this));
        if( _declaredType.isFinal() )
            t = t.makeRO(); // Deep final applied
        // Pinch between declared type
        t = t.join(_declaredType);
        return t;
    }

    @Override
    public Node idealize() {
        if( mem() instanceof CastNode cast ) {
            setDef(1,cast.in(1));
            return this;
        }
        if( mem() instanceof MemMergeNode merge ) {
            setDef(1,CodeGen.CODE.add(merge.alias(_alias)));
            return this;
        }
        Node ptr = ptr();
        Node mem = mem();

        Node win = find(mem,ptr,null,null);
        if( win!=null ) return folded(win);

        // Uplift control to a prior dominating load.
        for( Node memuse : mem._outputs )
            // Find a prior load, has same mem,ptr,off but higher ctrl
            if( memuse != this && memuse instanceof LoadNode ld && ptr==ld.ptr() && off()==ld.off() &&
                cfg0()!=null && cfg0().domLCA(ld.cfg0(),this) == ld.cfg0() ) // Higher control means load is legal earlier
                return ld;

        if( ptr instanceof ReadOnlyNode ro ) ptr=ro.in(1);

        // Push a Load up through a Phi, as long as it collapses on at least
        // one arm.  If at a Loop, the backedge MUST collapse - else we risk
        // spinning the same transform around the loop indefinitely.
        //   BEFORE (2 Sts, 1 Ld):          AFTER (1 St, 0 Ld):
        //   if( pred ) ptr.x = e0;         val = pred ? e0
        //   else       ptr.x = e1;                    : e1;
        //   val = ptr.x;                   ptr.x = val;
        if( mem() instanceof MemPhiNode memphi && memphi.region()._type == Type.CONTROL && memphi.nIns()== 3 &&
            // Array access control must not be moved across the merge.
            in(0)==null &&
            // Offset can be hoisted
            off() instanceof ConstantNode &&
            // Pointer can be hoisted
            hoistPtr(ptr,memphi)  ) {

            // Returning to this memory means no change only if the pointer
            // is also loop invariant. A pointer Phi may select another object.
            Node stop = memphi.region() instanceof LoopNode &&
                !(ptr instanceof PhiNode p && p.region()==memphi.region()) ? memphi : null;
            if( find(memphi.in(2),ptr,stop,new BitSet())!=null ||
                (!(memphi.region() instanceof LoopNode) && find(memphi.in(1),ptr,null,new BitSet())!=null) ) {
                PhiNode phi = new PhiNode(_name,_declaredType,memphi.region()).keep();
                phi.setType(_type); // Backedge searches may return this value Phi.
                for( int i=1; i<memphi.nIns(); i++ ) {
                    Node p = ptr instanceof PhiNode pp && pp.region()==memphi.region() ? pp.in(i) : ptr;
                    phi.addDef(load(memphi.in(i),p,stop,phi));
                }
                return CodeGen.CODE.add(phi.unkeep());
            }
        }

        return null;
    }

    // Load a flavored zero from a New
    private Node zero(NewNode nnn) {
        TypeStruct ts = nnn._ptr._obj;
        Type zero = ts._fields[ts.findAlias(_alias)]._t.makeZero();
        assert zero.isa(_type); // Catch an uninitialized non-null field
        return castRO(new ConstantNode(zero).peephole());
    }

    // Search only; no new nodes or rewiring. Return a Store/New that folds,
    // the unchanged loop memory, or a merge whose every arm folds. Null fails.
    // Register dependencies so a later pointer/offset rewrite retries the query.
    private Node find(Node mem, Node ptr, Node stop, BitSet visit) {
        if( ptr instanceof ReadOnlyNode ro ) ptr=ro.in(1);
        while( mem!=null ) {
            addDep(mem);
            if( mem==stop ) return mem;
            switch( mem ) {
            case MemMergeNode merge: mem=merge.alias(_alias); break;
            case CastNode cast: mem=cast.in(1); break;
            case StoreNode st:
                addDep(st.ptr());
                if( _alias==st._alias && ptr==st.ptr() && off()==st.off() ) return st;
                if( _alias==st._alias && !neverAlias(ptr,st.ptr()) &&
                    !addDep(off())._type.join(addDep(st.off())._type).isHigh() ) return null;
                mem=st.mem();
                break;
            case ProjNode proj:
                if( !(proj.in(0) instanceof NewNode nnn) ) return null;
                if( ptr instanceof ProjNode p && p.in(0)==nnn ) return nnn;
                if( !(ptr instanceof ProjNode p && p.in(0) instanceof NewNode) ) return null;
                mem=nnn.mem(); // Distinct allocations cannot overlap.
                break;
            case MemPhiNode phi:
                addDep(phi.region());
                // Only the original loop closes a successful cycle. Another
                // loop or an unfinished merge needs a separate proof.
                if( visit==null || !(phi.region() instanceof RegionNode r) || r instanceof LoopNode ||
                    r.inProgress() || phi.inProgress() || r._type!=Type.CONTROL ) return null;
                if( visit.get(phi._nid) ) return phi; // Already proved every arm.
                for( int i=1; i<phi.nIns(); i++ )
                    if( find(phi.in(i),ptr,stop,visit)==null ) return null;
                visit.set(phi._nid);
                return phi;
            default: return null;
            }
        }
        return null;
    }

    private Node folded(Node win) {
        return win instanceof StoreNode st ? extend(castRO(st.val())) : zero((NewNode)win);
    }

    // Materialize only after the profitability search succeeds. The value Phi
    // stands for a load which comes back around the loop with unchanged memory.
    private Node load(Node mem, Node ptr, Node stop, PhiNode value) {
        Node win=find(mem,ptr,stop,new BitSet());
        if( win!=null ) {
            if( win==stop ) return value;
            if( win instanceof MemPhiNode mp ) {
                PhiNode phi=new PhiNode(_name,_declaredType,mp.region()).keep();
                for( int i=1; i<mp.nIns(); i++ ) phi.addDef(load(mp.in(i),ptr,stop,value));
                return phi.unkeep().peephole();
            }
            return folded(win).peephole();
        }
        LoadNode ld=new LoadNode(_loc,_name,_alias,_declaredType,mem,ptr,off());
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
