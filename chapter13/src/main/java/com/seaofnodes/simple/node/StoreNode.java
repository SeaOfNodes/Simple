package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;

/**
 * Store represents setting a value to a memory based object, in chapter 10
 * this means a field inside a struct.
 */
public class StoreNode extends MemOpNode {

    /**
     * @param name The struct field we are assigning to
     * @param memSlice The memory alias node - this is updated after a Store
     * @param memPtr The ptr to the struct where we will store a value
     * @param value Value to be stored
     */
    public StoreNode(String name, int alias, Node memSlice, Node memPtr, Node value) {
        super(name, alias, memSlice, memPtr, value);
    }

    @Override
    public String label() { return "."+_name+"="; }
    @Override
    public boolean isMem() { return true; }
    public Node val() { return in(3); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_name).append("=").append( val()).append(";");
    }

    @Override
    public Type compute() { return TypeMem.make(_alias); }

    @Override
    public Node idealize() {

        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() &&  // Must check same object
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

}
