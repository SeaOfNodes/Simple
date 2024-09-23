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

    @Override public String  label() { return     _name; }
    @Override public String glabel() { return "."+_name; }

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

        // Simple Load-after-Store on same address.
        if( mem() instanceof StoreNode st &&
            ptr() == st.ptr() ) { // Must check same object
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            return st.val();
        }

        // Simple Load-after-New on same address.
        if( mem() instanceof ProjNode p && p.in(0) instanceof NewNode nnn &&
            ptr() == nnn.proj(1) )  // Must check same object
            return new ConstantNode(_declaredType.makeInit());

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
                Node ld1 = new LoadNode(_name,_alias,_declaredType,memphi.in(1),ptr(), off()).peephole();
                Node ld2 = new LoadNode(_name,_alias,_declaredType,memphi.in(2),ptr(), off()).peephole();
                return new PhiNode(_name,_declaredType,memphi.region(),ld1,ld2);
            }
        }

        return null;
    }

    // Profitable if we find a matching Store on this Phi arm.
    private boolean profit(PhiNode phi, int idx) {
        Node px = phi.in(idx);
        if( px==null ) return false;
        if( px._type instanceof TypeMem mem && mem._t.isHighOrConst() ) { px.addDep(this); return true; }
        if( px instanceof StoreNode st1 && ptr()==st1.ptr() )           { px.addDep(this); return true; }
        return false;
    }
}
