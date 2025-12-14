package com.seaofnodes.simple.node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.AryInt;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Store represents setting a value to a memory based object, in chapter 10
 * this means a field inside a struct.
 */
public class StoreNode extends MemOpNode {

    final boolean _init; // Initializing writes are allowed to write null

    /**
     * @param name  The struct field we are assigning to
     * @param mem   The memory alias node - this is updated after a Store
     * @param ptr   The ptr to the struct base where we will store a value
     * @param off   The offset inside the struct base
     * @param value Value to be stored
     */
    public StoreNode(Parser.Lexer loc, String name, int alias, Type glb, Node mem, Node ptr, Node off, Node value, boolean init) {
        super(loc, name, alias, false, glb, mem, ptr, off, value);
        _init = init;
    }
    StoreNode( BAOS bais, String[] strs, Type[] types, AryInt aliases ) {
        super(bais,strs,types,aliases,false);
        addDef(null);
        _init = bais.read() != 0;
    }
    @Override public Tag serialTag() { return Tag.Store; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        super.packed(baos,strs,types,aliases);
        baos.write(_init ? 1 : 0);
    }

    // GraphVis DOT code and debugger labels
    @Override public String  label() { return "st_"+mlabel(); }
    // GraphVis node-internal labels
    @Override public String glabel() { return "." +_name+"="; }
    @Override public boolean isMem() { return true; }

    public Node val() { return in(4); }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_name).append("=").append( val()).append(";");
    }

    @Override
    public Type compute() {
        Type val = val()._type;
        Type mem0 = mem()._type;
        if( mem0 == Type.TOP ) return TypeMem.TOP;
        TypeMem mem = (TypeMem)mem0; // Invariant
        if( mem == TypeMem.TOP ) return TypeMem.TOP;

        // Allocation uses a private TypeMem and nothing else does.  This
        // memory is truly private; a temporary singleton until it escapes -
        // which is never does in a constructor.
        if( mem._one )
            // Just track the stored value
            return TypeMem.make(_alias,val,true);

        // Normal aliasing Store
        Type t = Type.BOTTOM;               // No idea on field contents
        // Same alias, lift val to the declared type and then meet into other fields
        if( mem._alias == _alias ) {
            // Sharpen memory value; required for narrowing stores where the parser inserts
            // zero/sign masking and somebody reads the TypeMem type.
            val = val.join(declType());
            t = val.meet(mem._t);
        }
        return TypeMem.make(_alias,t,mem._one);
    }

    @Override
    public Node idealize() {
        if( mem() instanceof CastNode cast ) {
            setDef(1,cast.in(1));
            return this;
        }

        // Simple store-after-store on same address.  Should pick up the
        // required init-store being stomped by a first user store.
        if( mem() instanceof StoreNode st &&
            ptr()==st.ptr() &&  // Must check same object
            off()==st.off() &&  // And same offset (could be "same alias" but this handles arrays to same index)
            ptr()._type instanceof TypeMemPtr && // No bother if weird dead pointers
            // Must have exactly one use of "this" or you get weird
            // non-serializable memory effects in the worse case.
            checkOnlyUse(st) ) {
            assert _name==st._name; // Equiv class aliasing is perfect
            setDef(1,st.mem());
            return this;
        }

        // Simple store-after-MemMerge to a known alias can bypass.  Happens when inlining.
        if( mem() instanceof MemMergeNode mem ) {
            setDef(1,mem.alias(_alias));
            return this;
        }

        // Move stores of new private instances into the private memory space.
        // Lame EA by any other name.
        if( mem() instanceof EscapeNode esc ) {
            // Trivial bypass Escape; store either goes to the public side or the private side
            if( ptr() == esc.self() &&
                // Multiple users of same alias, one of them must be (eventually) dead.
                esc.nOuts() == 1 ) {
                esc.setDef(2,new StoreNode( _loc, _name, _alias, declType(), esc.priv(), ptr(), off(), val(), false ).peephole());
                return esc;
            } else {
                for( Node use : esc._outputs )
                    addDep(use); // Recheck once esc loses a use
                // CNC: TODO: Trivial bypass Escape to public side
            }
        }


        // Value is automatically truncated by narrow store
        if( val() instanceof AndNode and && and.in(2)._type.isConstant()  ) {
            int log = declType().log_size();
            if( log<3 ) {       // And-mask vs narrow store
                long mask = ((TypeInteger)and.in(2)._type).value();
                long bits = (1L<<(8<<log))-1;
                // Mask does not mask any of the stored bits
                if( (bits&mask)==bits )
                    // So and-mask is already covered by the store
                    { setDef(4,and.in(1)); return this; }
            }
        }

        // Store will chop high order bits off; math to change those bits can be dropped.
        if( val() instanceof SarNode shr &&
            shr.in(1) instanceof ShlNode shl &&
            shr.in(2)._type.isConstant() &&
            shl.in(2)._type.isConstant() ) {
            TypeInteger shrC = (TypeInteger) shr.in(2)._type;

            // size of the thing that sign-extends
            int base_size = (1 << shr.in(1)._type.log_size()) << 3;
            int not_affected_bits = base_size - (int) shrC.value();
            int store_size = (1 << log_size()) << 3;
            // if the store is unrelated to the shift amount, then get rid of the shift
            if( shl.in(2)._type == shr.in(2)._type && shrC.value() >= store_size && not_affected_bits >= store_size) {
                setDef(4, shl.in(1));
                return this;
            }
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
                addDep(use);
        return false;
    }

    @Override
    public Parser.ParseException err() {
        Parser.ParseException err = super.err();
        if( err != null ) return err;
        if( ptr()._type == Type.TOP )
            return null; // This means we have an error input, report elsewhere
        TypeMemPtr tmp = (TypeMemPtr)ptr()._type;
        if( tmp._obj.field(_name)._final && !_init )
            return Parser.error("Cannot modify final field '"+_name+"'",_loc);
        return null;
    }
}
