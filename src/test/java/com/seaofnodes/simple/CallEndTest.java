package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallEndTest {
    @Test public void testReturnWideningMonotonicity() {
        new CodeGen("return 0;").parse();
        TypeFunPtr sig = TypeFunPtr.make1((byte)2,true,TypeFunPtr.TEMPTY,TypeInteger.I32,100);
        FunNode fun = new FunNode(null,sig,"f",null);
        Node value = ConstantNode.raw(Type.TOP);
        ReturnNode ret = new ReturnNode(ConstantNode.raw(Type.CONTROL),
            ConstantNode.raw(TypeMem.START),value,null,fun);
        Type[] inputs = new Type[]{Type.TOP,TypeInteger.TOP,TypeInteger.I32.dual(),
            TypeInteger.ZERO,TypeInteger.constant(Long.MAX_VALUE),
            TypeInteger.make(-1,1,(byte)0),TypeInteger.make(-1,1,(byte)1),
            TypeInteger.make(-1,1,(byte)2),TypeInteger.make(-1,1,(byte)3),
            TypeInteger.I32,TypeInteger.BOT,TypeScalar.BOT,Type.BOTTOM};
        Type[] outputs = new Type[inputs.length];
        for( int i=0; i<inputs.length; i++ ) {
            value._type = inputs[i];
            outputs[i] = ret.compute();
        }
        for( int i=0; i<inputs.length; i++ )
            for( int j=0; j<inputs.length; j++ )
                if( inputs[i].isa(inputs[j]) )
                    assertTrue(inputs[i]+" -> "+inputs[j],outputs[i].isa(outputs[j]));
    }

    // Growing the target set must not narrow the result, even when the linked
    // return has exhausted its integer widening budget.
    @Test public void testUnknownTargetMonotonicity() {
        CodeGen code = new CodeGen("return 0;").parse();
        TypeFunPtr sig = TypeFunPtr.make1((byte)2,true,TypeFunPtr.TEMPTY,TypeInteger.BOT,100);
        FunNode fun = new FunNode(null,sig,"f",null);
        code._linker.setX(100,fun);
        Node ctrl = ConstantNode.raw(Type.CONTROL);
        Node mem = ConstantNode.raw(TypeMem.START);
        Node value = ConstantNode.raw(TypeInteger.I32);
        ReturnNode ret = new ReturnNode(ctrl,mem,value,null,fun);
        ret._type = ret.compute();
        Node fptr = ConstantNode.raw(sig.makeFrom(TypeInteger.I32));
        CallNode call = new CallNode(null,ctrl,mem,fptr).init();
        CallEndNode end = new CallEndNode(call,TypeRPC.BOT);
        end.addDef(ret);
        Type linked = end.compute();
        TypeFunPtr unknown = TypeFunPtr.make(false,TypeFunPtr.TEMPTY,TypeInteger.I32);
        assertTrue(fptr._type.isa(unknown));
        fptr._type = unknown;
        assertTrue("Adding unknown targets must only lose precision",linked.isa(end.compute()));
    }

    @Test public void testUnlinkedCallIsPhaseIndependent() {
        CodeGen code = new CodeGen("return 0;").parse();
        TypeFunPtr sig = TypeFunPtr.make1((byte)2,true,TypeFunPtr.TEMPTY,TypeInteger.I32,100);
        CallNode call = new CallNode(null,ConstantNode.raw(Type.CONTROL),
            ConstantNode.raw(TypeMem.START),ConstantNode.raw(sig)).init();
        CallEndNode end = new CallEndNode(call,TypeRPC.BOT);
        Type expected = TypeTuple.make(Type.CONTROL,TypeMem.BOT,TypeInteger.I32);
        for( CodeGen.Phase phase : new CodeGen.Phase[]{CodeGen.Phase.Parse,
                CodeGen.Phase.Opto,CodeGen.Phase.IterPostOpto} ) {
            code._phase = phase;
            assertSame(phase.toString(),expected,end.compute());
        }
    }

    @Test public void testExternalTargets() {
        CodeGen code = new CodeGen("return 0;").parse();
        code.externFunc(100,"first");
        code.externFunc(101,"second");
        TypeFunPtr sig = TypeFunPtr.make1((byte)2,true,TypeFunPtr.TEMPTY,TypeInteger.I32,100);
        Node fptr = ConstantNode.raw(sig);
        CallNode call = new CallNode(null,ConstantNode.raw(Type.CONTROL),
            ConstantNode.raw(TypeMem.START),fptr).init();
        CallEndNode end = new CallEndNode(call,TypeRPC.BOT);
        Type first = end.compute();
        fptr._type = sig.meet(sig.makeFrom(101));
        assertTrue(first.isa(end.compute()));
        assertSame(TypeInteger.I32,((TypeTuple)end.compute()).ret());
        assertSame(Type.BOTTOM,((TypeMem)((TypeTuple)end.compute()).mem())._t);
    }
}
