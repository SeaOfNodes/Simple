package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

import java.util.BitSet;
import static com.seaofnodes.simple.util.Utils.TODO;

// Lift a value that is about to be loaded or stored, indexed by field name and
// a dynamic base value.
//
// - ptrs can be converted to ints for FFI calls (but not vice-versa)
// - ints can be converted to flts
// - loaded narrow ints can be sign- or zero-extended
//
public class LiftNode extends Node {
    public final String _fld;   // Field name
    public final boolean _isLoad;
    public LiftNode(Node base, String fld, Node val, boolean isLoad) { super(base,val); _fld = fld; _isLoad = isLoad; }

    @Override public String label() { return "Lift"; }
    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) { return sb.append("Lift"); }
    Node base() { return in(0); }
    Node val () { return in(1); }
    @Override public Type compute() { return Type.BOTTOM; }

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
            //val = peep(new AddNode(peep(new CastNode(t,ctrl(),val)),off(tmp._obj,"[]")));

        // Auto-widen int to float
        if( (val._type instanceof TypeInteger || val._type==Type.NIL) && t instanceof TypeFloat )
            return new ToFloatNode(val);


        // Auto-widen narrow ints
        if( _isLoad ) {
            // For loads, emit code to force the loaded value to match the
            // declared sign/zero bits.
            if( val._type instanceof TypeInteger tval && t instanceof TypeInteger t0 && !tval.isa(t0) ) {
                // Example: loading a i32 into a u8.  Semantics are to simply
                // truncate the sign bits.
                if( t0._min==0 )        // Unsigned
                    throw TODO("AND Mask test");
                //return peep(new AndNode(null,val,con(t0._max)));
                // Signed extension; e.g. loading a i32 into a i64
                int shift = Long.numberOfLeadingZeros(t0._max)-1;
                assert (1L<<shift) == t0._max; // Expect one of the well-defined integer result types
                Node shf = con(shift);
                assert shf._type!=TypeInteger.ZERO;
                //return peep(new SarNode(null,peep(new ShlNode(null,val,shf.keep())),shf.unkeep()));
                throw TODO("sign-extend test");
            }
        } else {
            // For integer stores, just silently truncate.
            if( val._type instanceof TypeInteger && t instanceof TypeInteger )
                // Silently truncate.  I.e., just "as if" store the whole value
                // but the hardware op will only store the indicated bits
                // (e.g. storing a large value 12345 into a byte field).
                return val;
        }

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
