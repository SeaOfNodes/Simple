package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;

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

    @Override public String  label() { return "." +_name+"="; }
    @Override public String glabel() { return "." +_name+"="; }
    @Override public boolean isMem() { return true; }

    public Node val() { return in(4); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_name).append("=").append( val()).append(";");
    }

    @Override
    public Type compute() {
        Type val = val()._type;
        TypeMem mem = (TypeMem)mem()._type; // Invariant
        Type t = mem._alias==_alias
            ? val.meet(mem._t)  // Meet into existing memory
            : Type.BOTTOM;
        return TypeMem.make(_alias,t);
    }

    @Override
    public Node idealize() {

        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() &&  // Must check same object
            off()==st.off() &&  // And same offset
            ptr()._type instanceof TypeMemPtr && // No bother if weird dead pointers
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            st.checkNoUseBeyond(this) ) {
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            setDef(1,st.mem());
            return this;
        }

        return null;
    }

    // Check that `this` has no uses beyond `that`
    private boolean checkNoUseBeyond(Node that) {
        if( nOuts()==1 ) return true;
        // Add deps on the other uses (can be e.g. ScopeNode mid-parse) so that
        // when the other uses go away we can retry.
        for( Node use : _outputs )
            if( use != that )
                use.addDep(that);
        return false;
    }

    @Override
    String err() {
        String err = super.err();
        if( err != null ) return err;
        Type t = val()._type;
        return _init || t.isa(_declaredType) ? null : "Cannot store "+t+" into field "+_declaredType+" "+_name;
    }
}
