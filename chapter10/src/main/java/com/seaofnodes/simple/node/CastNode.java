package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.IterPeeps;

import java.util.BitSet;

// Upcast (join) the input to a t.  Used after guard test to lift an input.
// Can also be used to make a type-assertion if ctrl is null.

// Disallowed to peephole on creation, as the Cast is ALSO a marker in the
// Scope of what got upcast - can needs to be removed from the Scope after the
// If goes out of scope.
public class CastNode extends Node {
    private final Type _t;
    boolean _peep;
    public CastNode(Type t, Node ctrl, Node in) {
        super(ctrl, in);
        _t = t;
        _peep=false;
        setType(compute());
    }

    @Override public String label() { return "("+_t.str()+")"; }

    @Override
    public String uniqueName() { return "Cast_" + _nid; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append(label()), visited);
    }

    @Override
    public Type compute() {
        if( !_peep ) return in(1)._type.glb();
        return in(1)._type.join(_t);
    }

    @Override
    public Node idealize() {
        if( !_peep ) return null;
        // Remove if already lifted enough
        return in(1)._type.isa(_t) ? in(1) : null;
    }
}
