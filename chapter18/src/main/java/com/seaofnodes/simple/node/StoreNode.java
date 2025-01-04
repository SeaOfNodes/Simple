package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
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
    public StoreNode(Parser.Lexer loc, String name, int alias, Type glb, Node mem, Node ptr, Node off, Node value, boolean init) {
        super(loc, name, alias, glb, mem, ptr, off, value);
        _init = init;
    }

    // GraphVis DOT code and debugger labels
    @Override public String  label() { return "st_"+mlabel(); }
    // GraphVis node-internal labels
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
        Type t = Type.BOTTOM;               // No idea on field contents
        // Same alias, lift val to the declared type and then meet into other fields
        if( mem._alias == _alias ) {
            // Update declared forward ref to the actual
            if( _declaredType.isFRef() && val instanceof TypeMemPtr tmp && !tmp.isFRef() )
                _declaredType = tmp;
            val = val.join(_declaredType);
            t = val.meet(mem._t);
        }
        return TypeMem.make(_alias,t);
    }

    @Override
    public Node idealize() {

        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() &&  // Must check same object
            off()==st.off() &&  // And same offset (could be "same alias" but this handles arrays to same index)
            ptr()._type instanceof TypeMemPtr && // No bother if weird dead pointers
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            checkOnlyUse(st) ) {
            assert _name.equals(st._name); // Equiv class aliasing is perfect
            setDef(1,st.mem());
            return this;
        }

        // Simple store-after-new on same address.  Should pick up
        // an init-store being stomped by a first user store.
        if( mem() instanceof ProjNode st  && st .in(0) instanceof NewNode nnn &&
            ptr() instanceof ProjNode ptr && ptr.in(0) == nnn &&
            ptr()._type instanceof TypeMemPtr tmp && // No bother if weird dead pointers
            // Cannot fold a store of a single element over the array body initializer value
            !(tmp._obj.isAry() && tmp._obj._fields[1]._alias==_alias) &&
            // Very sad strong cutout: val has to be legal to hoist to a New
            // input, which means it cannot depend on the New.  Many, many
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
            return mem();
        }

        return null;
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
    public Parser.ParseException err() {
        Parser.ParseException err = super.err();
        if( err != null ) return err;
        TypeMemPtr tmp = (TypeMemPtr)ptr()._type;
        if( tmp._obj.field(_name)._final && !"[]".equals(_name) )
            return Parser.error("Cannot modify final field '"+_name+"'",_loc);
        Type t = val()._type;
        return _init || t.isa(_declaredType) ? null : Parser.error("Cannot store "+t+" into field "+_declaredType+" "+_name,_loc);
    }
}
