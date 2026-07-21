package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;
import static com.seaofnodes.simple.util.Utils.TODO;

// Lift a value that is about to be stored, indexed by field name and a dynamic
// base value.
//
// - ptrs can be converted to ints for FFI calls (but not vice-versa)
// - ints can be converted to flts
// - loaded narrow ints can be sign- or zero-extended
//
public class LiftNode extends Node {
    public final String _fld;   // Field name
    public LiftNode(Node base, String fld, Node val) { super(base,val); _fld = fld; }

    @Override public String label() { return "Lift"; }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("Lift"); }
    Node base() { return in(0); }
    Node val () { return in(1); }
    @Override public Type compute() {
        if( val()._type.isHigh() ) return Type.TOP;
        return val()._type.glb(false);
    }

    // Do All The Things that need to be done when mixing
    // things of different types in memory.
    @Override public Node idealize() {
        // Dunno the destination type yet.
        if( !(base()._type instanceof TypeMemPtr ptr) ) return null;
        // Destination is a struct, but field not appeared yet
        Field fld = ptr._obj.field(_fld);
        if( fld == null ) return null;
        // Declared field type
        Type t = fld._t;
        Node val = val();

        // Correct type, nothing to lift?
        if( val._type.isa(t) )
            return val;

        // Convert an array pointer to a raw integer for FFI calls
        if( t == TypeInteger.BOT && val._type instanceof TypeMemPtr tmp && tmp._obj.isAry() )
            throw TODO("Convert ary to int for FFI calls");
            //val = peep(new AddNode(peep(new CheckCastNode(t,ctrl(),val)),off(tmp._obj,"[]")));

        // Auto-widen int to float
        if( (val._type instanceof TypeInteger || val._type==Type.NIL) && t instanceof TypeFloat )
            return new ToFloatNode(val);


        // For integer stores, just silently truncate.
        if( val._type instanceof TypeInteger && t instanceof TypeInteger )
            // Silently truncate.  I.e., just "as if" store the whole value
            // but the hardware op will only store the indicated bits
            // (e.g. storing a large value 12345 into a byte field).
            return val;

        if( val._type instanceof TypeFloat tval && t instanceof TypeFloat t0 && !tval.isa(t0) )
            // Float rounding
            throw TODO("float round test");
            //return peep(new RoundF32Node(val));

        // Leave the Lift in place until types sharpen,
        // or we eventually call err() and fail.
        return null;
    }



    // LiftExpr must fold away before typing is complete
    @Override public Parser.ParseException err() {
        TypeMemPtr tmp = (TypeMemPtr)base()._type; // Expect at least this much
        Field fld = tmp._obj.field(_fld);
        return Parser.error( "Type " + val()._type.str() + " is not of declared type " + fld._t.str(), null );
    }
}
