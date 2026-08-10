package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EscapeFlowTest {
    // A cofinite outside set must not revive private allocation helpers.
    @Test public void testOpaqueRoundTrip() {
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen("""
                struct _A { B? !b; new _A = { B? p -> b=p; }; };
                val wrap = { B? b -> return new _A(b); };
                return wrap;
                """,seed,true).driver(CodeGen.Phase.TypeCheck);
            TypeMem outside = ((TypeTuple)code._start._type).mem();
            assertTrue(XInt.isHigh(outside._escFs));
            assertNotSame(XInt.FULL,outside._escFs);
            for( FunNode fun : code._linker )
                if( fun!=null && fun._name!=null && fun._name.contains("_A.") )
                    assertFalse(fun._name,fun.unknownCallers());
        }
    }

    // Foreign callbacks remain possible. The default callback argument reads
    // Start memory rather than a frozen declaration constant.
    @Test public void testUnknownCallerCallback() {
        CodeGen code = new CodeGen("""
            val _hidden_noInline = { int n -> int result=n+2; return result; };
            val apply_noInline = { {int->int} f,int n -> return f(n); };
            return apply_noInline;
            """).parse();
        TypeMemPtr self = (TypeMemPtr)named(code,"apply_noInline").parm(2).in(1)._type;
        assertTrue(self._one);
        assertEquals("class:Test",self._obj._name);
        code.driver(CodeGen.Phase.TypeCheck);
        FunNode apply = named(code,"apply_noInline");
        assertTrue(apply.unknownCallers());
        ParmNode callback = apply.parm(3);
        assertTrue(callback.in(1) instanceof ProjNode);
        TypeFunPtr type = (TypeFunPtr)callback._type;
        assertTrue(XInt.isHigh(type.fidxs()));
        assertSame(TypeInteger.I32,apply.ret().expr()._type);
        // Only <clinit> and apply escape; forwarding #self must not expose _hidden.
        assertEquals("Stop[ return { apply_noInline}; return #2; ]",code.print());
    }

    @Test public void testNativeCallbackRoundTrip() {
        CodeGen code = new CodeGen("""
            val _callback_noInline = { int n -> int result=n+1; return result; };
            { {int->int} -> {int->int} } bounce = "C";
            return bounce(_callback_noInline)(((arg<<32)>>32));
            """).driver(CodeGen.Phase.TypeCheck);
        FunNode callback = named(code,"_callback_noInline");
        assertTrue(callback.unknownCallers());
        assertTrue(XInt.bit(((TypeTuple)code._start._type).mem()._escFs,callback.sig().fidx()));
    }

    // Call remains CONTROL. Changing only the outside set must wake CallEnd
    // and extend the possible callback returned by the native factory.
    @Test public void testOutsideReturnDependency() {
        CodeGen code = new CodeGen("return 0;").parse();
        code._phase = CodeGen.Phase.Opto;
        code.externFunc(100,"factory");
        TypeFunPtr callback = TypeFunPtr.make(false,TypeFunPtr.TEMPTY,TypeInteger.BOT);
        TypeFunPtr factory = TypeFunPtr.make1((byte)2,true,TypeFunPtr.TEMPTY,callback,100);
        CallNode call = new CallNode(null,ConstantNode.raw(Type.CONTROL),
            ConstantNode.raw(TypeMem.START),ConstantNode.raw(factory)).init();
        CallEndNode end = new CallEndNode(call,TypeRPC.BOT);
        int[] foreign = XInt.dual(XInt.make(7));
        code._start._type = TypeTuple.make(Type.CONTROL,TypeMem.START.makeFrom(foreign,XInt.EMPTY),TypeInteger.BOT);
        TypeTuple before = (TypeTuple)end.compute();
        assertFalse(XInt.bit(((TypeFunPtr)before.ret()).fidxs(),7));
        boolean depends = false;
        for( int i = 0; i<code._start.nDeps(); i++ ) depends |= code._start.dep(i)==end;
        assertTrue(depends);
        code._start._type = TypeTuple.make(Type.CONTROL,TypeMem.START.makeFrom(XInt.FULL,XInt.EMPTY),TypeInteger.BOT);
        TypeTuple after = (TypeTuple)end.compute();
        assertTrue(before.isa(after));
        assertTrue(XInt.bit(((TypeFunPtr)after.ret()).fidxs(),7));
    }

    // Public construction exposes the allocator; private-memory helpers
    // remain internal and can inline into it.
    @Test public void testPublicAllocator() {
        CodeGen code = new CodeGen("""
            struct Box { int n; new Box = { int x -> n=x; }; };
            return new Box(((arg<<32)>>32)).n;
            """).parse();
        FunNode allocator = named(code,"Test.Box.<new>");
        int[] helpers = XInt.EMPTY;
        for( FunNode fun : code._linker )
            if( fun!=null && fun.sig().ret() instanceof TypeMem )
                helpers = XInt.make(helpers,fun.sig().fidx());
        assertNotSame(XInt.EMPTY,helpers);
        code.driver(CodeGen.Phase.TypeCheck);
        TypeMem outside = ((TypeTuple)code._start._type).mem();
        assertTrue(allocator.unknownCallers());
        assertTrue(XInt.bit(outside._escFs,allocator.sig().fidx()));
        for( int fidx = XInt.next(helpers,0); fidx>=0; fidx = XInt.next(helpers,fidx) )
            assertFalse(XInt.bit(outside._escFs,fidx));
        assertEquals("42",Eval2.eval(code,42));
    }

    private static FunNode named(CodeGen code, String name) {
        for( FunNode fun : code._linker )
            if( fun!=null && name.equals(fun._name) ) return fun;
        throw new AssertionError("Missing function "+name);
    }
}
