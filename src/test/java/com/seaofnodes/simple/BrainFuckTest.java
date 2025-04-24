package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BrainFuckTest {

    @Test public void testBrainFuck() throws IOException {
        String brain_fuck = "Hello World!\n";
        TestC.run("brain_fuck", brain_fuck, 40);

        EvalRisc5 R5 = TestRisc5.build("brain_fuck", 0, 44, false);
        int trap = R5.step(100000);
        assertEquals(0,trap);
        int ptr = (int)R5.regs[riscv.A0];
        assertEquals(brain_fuck.length(),R5.ld4s(ptr));
        for( int i=0; i<brain_fuck.length(); i++ )
            assertEquals(brain_fuck.charAt(i), R5.ld1z(ptr+4+i));

        EvalArm64 A5 = TestArm64.build("brain_fuck", 0, 28, false);
        int trap_arm = A5.step(100000);
        assertEquals(0,trap_arm);
        assertEquals(0,trap);
        int ptr_arm = (int)A5.regs[arm.X0];
        assertEquals(brain_fuck.length(),A5.ld4s(ptr_arm));

        for( int i=0; i<brain_fuck.length(); i++ )
            assertEquals(brain_fuck.charAt(i), A5.ld1z(ptr+4+i));
    }

}
