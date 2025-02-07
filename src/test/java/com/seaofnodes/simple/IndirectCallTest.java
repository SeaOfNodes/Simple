package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import org.junit.Test;
import static org.junit.Assert.*;

public class IndirectCallTest {
    @Test public void testTargetOperand() {
        for( String cpu : new String[]{"x86_64_v2","riscv","arm"} ) {
            CodeGen code = new CodeGen("return 0;").driver(CodeGen.Phase.InstSelect,cpu,"SystemV");
            Node target = PhiNode.make("target",TypeFunPtr.BOT,code._start);
            target._type = TypeFunPtr.BOT;
            CallNode ideal = new CallNode(null,code._start,null,target);
            CallNode call = (CallNode)code._mach.instSelect(ideal);
            // The last input is the call target, not another argument.
            assertEquals("callr",((MachNode)call).op());
            RegMask mask = ((MachNode)call).regmap(call.nIns()-1);
            assertTrue(mask.test(5));
            assertTrue(mask.test(6));
            // Machine operands can lose their ideal type (e.g. a spill reload).
            // The call still needs the original signature for argument masks.
            target._type = Type.BOTTOM;
            assertSame(TypeFunPtr.BOT,call.tfp());
        }
    }
}
