package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalBits;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
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
    public LoadNode(Parser.Lexer loc, String name, int alias, Node ctrl, Node mem, Node ptr, Node off) {
        super(loc, name, alias, true, Type.BOTTOM, ctrl, mem, ptr, off);
    }
    LoadNode( BAOS bais, String[] strs, Type[] types, GlobalBits fileAliases, GlobalBits aliases ) {
        super(bais,strs,types,fileAliases,aliases,true);
        _con = Type.BOTTOM;      // Serialized slot retained while MemOps share TypeNode
    }
    @Override public Tag serialTag() { return Tag.Load; }

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
                use instanceof NewNode || use instanceof EscapeNode || use instanceof CallNode ) return true;
        }
        return false;
    }

    // Debugger label
    @Override public String  label() { return "ld_"+mlabel(); }
    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append(".").append(_name); }

    @Override
    public Type compute() {
        Type mem0 = mem()._type;
        Type ptr0 = ptr()._type;
        // Validate argument types
        if( ptr0.isHigh() )
            return TypeScalar.TOP;
        if( !(mem0 instanceof TypeMem mem) )
            return mem0.isHigh() ? TypeScalar.TOP : TypeScalar.BOT;
        if( mem._t == Type.TOP )
            return TypeScalar.TOP;
        if( ptr0 == Type.NIL )
            return Type.BOTTOM; // Load from nil is error
        if( ptr0 == Type.BOTTOM )
            return Type.BOTTOM; // Error in is error out
        if( !(ptr0 instanceof TypeMemPtr tmp) )
            return scalar(mem._t);
        if( tmp.nullable() )
            return Type.BOTTOM; // Load from nil-able is error

        // Load field from object
        Field pfld = tmp._obj.field(_name);
        // No field?  Open objects might yet get the field when falling;
        // closed objects with missing field are an error.
        if( pfld == null )
            return tmp.isHigh() || tmp._obj.isHigh() ? TypeScalar.TOP : TypeScalar.BOT;

        Type t = pfld._t;
        // Load member of constant array
        if( t instanceof TypeConAry ary )
            t = ary.elem();     // TODO: if offset is known, can peek the constant
        if( mem._alias==_alias )
            t = t.join(mem._t);

        // A deeply read-only base produces a deeply read-only value.  The
        // generic declared field type must not cast this information away.
        if( ptr0.isFinal() )
            t = t.makeRO();
        return scalar(t);
    }

    // Loads produce ordinary scalar values.  Field/memory joins can still
    // expose the enclosing global lattice bounds through an uninitialized
    // Type.TOP/BOTTOM field; keep those results inside the scalar envelope.
    private static Type scalar(Type t) {
        return t==Type.TOP ? TypeScalar.TOP : t==Type.BOTTOM ? TypeScalar.BOT : t;
    }

    @Override
    public Node idealize() {
        Node mem = mem();
        Node ptr = ptr();

        // Null checks are handled by types. Array elements retain control for
        // range checks, but reading an array's length has no index to check.
        if( in(0)!=null && ptr._type instanceof TypeMemPtr tmp &&
            (!tmp._obj.isAry() || _name.equals("#")) ) {
            setDef(0,null);
            return this;
        }

        // Forward-ref loads eventually sharpen to a declared type
        Field fld;
        if( ptr._type instanceof TypeMemPtr tmp &&
            (fld=tmp._obj.field(_name)) != null &&
            _alias != fld._alias) {
            assert _alias==1 || _alias == fld._alias;
            assert !tmp._obj._open && !tmp._obj._fref;
            unlock();           // Alias participates in GVN semantics
            _alias = fld._alias;
            return this;
        }
        // Must sharpen alias first
        if( _alias == 1 )
            return null;

        if( mem instanceof MemMergeNode merge ) {
            setDef(1,merge.alias(_alias));
            // In support of uplifting control to a prior dominating load, if
            // we move our memory, recheck other loads to see if they can hoist.
            for( Node use : in(1).outs() )
                if( use instanceof LoadNode ld && use != this && ptr == ld.ptr() )
                    CodeGen.CODE.add(ld);

            return this;
        }

        // Uplift control to a prior dominating load.
        for( Node memuse : mem._outputs )
            // Find a prior load, has same mem,ptr,off but higher ctrl
            if( memuse != this && memuse instanceof LoadNode ld && ptr==ld.ptr() && off()==ld.off() &&
                cfg0()!=null && cfg0().domLCA(ld.cfg0(),this) == ld.cfg0() && // Higher control means load is legal earlier
                addDep(ld)._type.isa(_type) ) // and not rolling backwards
                return ld;

        Node win = find(mem,ptr,null,null);
        if( win!=null ) return folded(win);
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
                // Integer loop Phis widen to i64. Keep the load's declared
                // width at the result, as for ordinary typed loop variables.
                if( _type instanceof TypeInteger && !declaredType().isa(_type) ) return null;
                PhiNode phi = new PhiNode(_name,memphi.region()).keep();
                phi.setType(_type instanceof TypeInteger ? TypeInteger.BOT : _type);
                for( int i=1; i<memphi.nIns(); i++ ) {
                    Node p = ptr instanceof PhiNode pp && pp.region()==memphi.region() ? pp.in(i) : ptr;
                    phi.addDef(load(memphi.in(i),p,stop,phi));
                }
                CodeGen.CODE.add(phi.unkeep());
                return _type instanceof TypeInteger ? new ConvertNode(declaredType(),phi) : phi;
            }
        }

        return null;
    }

    // Load a flavored zero from a New
    private Node zero(NewNode nnn) {
        Type zero = declaredType().makeZero();
        assert zero.isa(_type); // Catch an uninitialized non-null field
        return castRO(new ConstantNode(zero).peephole());
    }

    // Search only; no new nodes or rewiring. Return a Store/New that folds,
    // the unchanged loop memory, or a merge whose every arm folds. Null fails.
    // Register dependencies so a later pointer/offset rewrite retries the query.
    private Node find(Node mem, Node ptr, Node stop, BitSet visit) {
        if( ptr instanceof ReadOnlyNode ro ) ptr=ro.in(1);
        Field fld;
        boolean external = ptr._type instanceof TypeMemPtr tmp &&
            (fld=tmp._obj.field(_name))!=null && fld._extern;
        while( mem!=null ) {
            addDep(mem);
            if( mem==stop ) return mem;
            switch( mem ) {
            case MemMergeNode merge: mem=merge.alias(_alias); break;
            case StoreNode st:
                // Alias 1 is still unresolved, not a proven different field.
                if( st._alias==1 ) return null;
                addDep(st.ptr());
                if( _alias==st._alias && (ptr==st.ptr() || ptr==st.nnptr()) && off()==st.off() ) {
                    // A full-width load cannot narrow the forwarded value.
                    if( declaredType() instanceof TypeInteger ti && ti.log_size()==3 &&
                        !addDep(st.val())._type.isa(_type) ) return null;
                    return st;
                }
                if( (external || _alias==st._alias) && (external || !neverAlias(ptr,st.ptr())) &&
                    !addDep(off())._type.join(addDep(st.off())._type).isHigh() ) return null;
                mem=st.mem();
                break;
            case ProjNode proj:
                // New's memory is private; public memory is joined at Escape.
                if( external || !(proj.in(0) instanceof NewNode nnn) ) return null;
                if( declaredType()==Type.BOTTOM ) return null;
                return nnn;
            case EscapeNode esc:
                if( external ) return null;
                addDep(esc.self());
                if( esc.self()==ptr ) { mem=esc.priv(); break; }
                if( neverAlias(ptr,esc.self()) ) { mem=esc.pub(); break; }
                return null;
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
                PhiNode phi=new PhiNode(_name,mp.region()).keep();
                for( int i=1; i<mp.nIns(); i++ ) phi.addDef(load(mp.in(i),ptr,stop,value));
                return CodeGen.CODE.add(phi.unkeep().peephole());
            }
            Node fold=folded(win);
            if( fold!=null ) return fold.peephole();
        }
        LoadNode ld=new LoadNode(_loc,_name,_alias,in(0),mem,ptr,off());
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
        if( !(declaredType() instanceof TypeInteger ti) ) return val;
        if( ti._min==0 )        // Unsigned
            return new AndNode(null,val,con(ti._max));
        // Signed extension
        int shift = Long.numberOfLeadingZeros(ti._max)-1;
        Node shf = con(shift);
        if( shift==0 ) {
            if( !val._type.isa(_type) )
                { addDep(val); return null; }
            return val;
        }
        Node shl = new ShlNode(null,val,shf.keep()).peephole();
        return new SarNode(null,shl,shf.unkeep());
    }
}
