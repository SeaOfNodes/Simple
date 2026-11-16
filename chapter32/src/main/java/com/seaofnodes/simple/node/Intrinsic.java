package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.*;

// Fixed-semantics C functions. Recognition, effects and expansion live here;
// Call/CallEnd only delegate. These symbols also have ordinary C definitions
// for calls whose target is not known at compile time.
public final class Intrinsic {
    private Intrinsic() {}

    private enum Op {
        F64_BITS("simple_f64_bits",TypeFloat.F64,TypeInteger.BOT,true),
        F64_FROM_BITS("simple_f64_from_bits",TypeInteger.BOT,TypeFloat.F64,true);

        final String symbol;
        final Type arg, ret;
        final boolean pure;
        Op(String symbol, Type arg, Type ret, boolean pure) {
            this.symbol=symbol; this.arg=arg; this.ret=ret; this.pure=pure;
        }
        boolean signature(TypeFunPtr fp) {
            // Slot zero is the implicit receiver, omitted by the C ABI.
            return fp.nargs()==2 && fp.arg(1)==arg && fp.ret()==ret;
        }
        Expansion expand(CallNode call) {
            Node value=switch(this) {
            case F64_BITS -> new F64BitsNode(call.arg(3)).peephole();
            case F64_FROM_BITS -> new F64FromBitsNode(call.arg(3)).peephole();
            };
            return new Expansion(call.ctrl(),call.mem(),value);
        }
    }

    private static Op lookup(CallNode call) {
        if( !(call.fptr()._type instanceof TypeFunPtr fp) || !fp.notNull() || !fp.isConstant() ) return null;
        String symbol=CodeGen.CODE.externFunc(fp.fidx());
        if( symbol==null ) return null; // A Simple function with this name is ordinary code.
        for( Op op : Op.values() ) if( op.symbol.equals(symbol) ) return op;
        return null;
    }

    public static Parser.ParseException err(CallNode call) {
        Op op=lookup(call);
        return op==null || op.signature(call.tfp()) ? null : Parser.error(
            "Intrinsic '"+op.symbol+"' requires { "+op.arg.str()+" -> "+op.ret.str()+" }",call._loc);
    }

    // Both initial operations are pure and expose no references. Future atomic
    // operations need their own memory/escape semantics, not this passthrough.
    public static boolean pure(CallNode call) {
        Op op=lookup(call);
        return op!=null && op.pure && op.signature(call.tfp());
    }

    public static TypeTuple compute(CallNode call, Node dep) {
        if( !pure(call) ) return null;
        return TypeTuple.make(Type.CONTROL,dep.addDep(call.mem())._type,call.tfp().ret());
    }

    // An expansion replaces the complete call state, even for a pure value op.
    private record Expansion(Node control, Node memory, Node value) {
        void replace(CallEndNode end) {
            Node[] state={control,memory,value};
            for( Node n : state ) n.keep();
            end.keep();
            for( Node use : end._outputs.asAry() ) {
                if( use==null ) continue; // Our temporary keep.
                assert use instanceof Proj;
                use.subsume(state[((Proj)use).idx()]);
            }
            end.unkill();
            for( Node n : state ) n.unkill();
            CodeGen.CODE.invalidateIDepthCaches();
        }
    }

    // Same cheap-inlining worklist as ordinary functions, but no body/Return,
    // cloning, size heuristic, or import loading is needed.
    public static byte tryInline(CallEndNode end) {
        if( end.folding() || !(end.in(0) instanceof CallNode call) ) return 0;
        Op op=lookup(call);
        if( op==null || !op.signature(call.tfp()) || call.nargs()!=call.tfp().nargs() ||
            !end.goodArgs(call,call.tfp()) ) return 0;
        if( !CodeGen.CODE._midAssert ) op.expand(call).replace(end);
        return 1;
    }

    public abstract static class BitCastNode extends Node {
        BitCastNode(Node value) { super(null,value); }
        public abstract boolean toBits();
        @Override public Type compute() {
            Type input=in(1)._type;
            Type result=toBits() ? TypeInteger.BOT : TypeFloat.F64;
            if( input.isHigh() ) return result.dual();
            if( toBits() && input instanceof TypeFloat f && f.isConstant() )
                return TypeInteger.constant(Double.doubleToRawLongBits(f.value()));
            if( !toBits() && input instanceof TypeInteger i && i.isConstant() )
                return TypeFloat.constant(Double.longBitsToDouble(i.value()));
            return result;
        }
        @Override public Node idealize() {
            return in(1) instanceof BitCastNode cast && cast.toBits()!=toBits() ? cast.in(1) : null;
        }
    }
    public static final class F64BitsNode extends BitCastNode {
        public F64BitsNode(Node value) { super(value); }
        @Override public boolean toBits() { return true; }
        @Override public Tag serialTag() { return Tag.F64Bits; }
        @Override protected String format() { return "f64Bits(%1)"; }
    }
    public static final class F64FromBitsNode extends BitCastNode {
        public F64FromBitsNode(Node value) { super(value); }
        @Override public boolean toBits() { return false; }
        @Override public Tag serialTag() { return Tag.F64FromBits; }
        @Override protected String format() { return "f64FromBits(%1)"; }
    }
}
