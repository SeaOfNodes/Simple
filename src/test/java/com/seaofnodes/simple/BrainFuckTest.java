package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BrainFuckTest {

    @Test public void testBrainFuck() throws IOException {
        String brain_fuck = "Hello World!\n";
        String src =
"""
// -*- mode: java;  -*-
val brain_fuck = { ->
    var !program = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++.";
    int d = 0;
    u8[] !output = new u8[0];
    u8[] !data = new u8[100];

    for( int pc = 0; pc < program#; pc++ ) {
        var command = program[pc];
        if (command == 62) {
            d++;
        } else if (command == 60) {
            d--;
        } else if (command == 43) {
            data[d]++;
        } else if (command == 45) {
            data[d]--;
        } else if (command == 46) {
            // Output a byte; increase the output array size
            var old = output;
            output = new u8[output# + 1];
            for( int i = 0; i < old#; i++ )
                output[i] = old[i];
            output[old#] = data[d]; // Add the extra byte on the end
        } else if (command == 44) {
            data[d] = 42;
        } else if (command == 91) {
            if (data[d] == 0) {
                for( int d = 1; d > 0; ) {
                    command = program[++pc];
                    if (command == 91) d++;
                    if (command == 93) d--;
                }
            }
        } else if (command == 93) {
            if (data[d]) {
                for( int d = 1; d > 0; ) {
                    command = program[--pc];
                    if (command == 93) d++;
                    if (command == 91) d--;
                }
            }
        }
    }
    return output;
};
""";
        TestC.run(src,"brain_fuck", null, brain_fuck, 40);

        EvalRisc5 R5 = TestRisc5.build("brain_fuck", src, 0, 28, false);
        int trap = R5.step(100000);
        assertEquals(0,trap);
        int ptr = (int)R5.regs[com.seaofnodes.simple.node.cpus.riscv.riscv.A0];
        assertEquals(brain_fuck.length(),R5.ld4s(ptr));
        for( int i=0; i<brain_fuck.length(); i++ )
            assertEquals(brain_fuck.charAt(i), R5.ld1z(ptr+4+i));

        EvalArm64 A5 = TestArm64.build("brain_fuck", src, 0, 28, false);
        int trap_arm = A5.step(100000);
        assertEquals(0,trap_arm);
        assertEquals(0,trap);
        int ptr_arm = (int)A5.regs[com.seaofnodes.simple.node.cpus.arm.arm.X0];
        assertEquals(brain_fuck.length(),A5.ld4s(ptr_arm));

        for( int i=0; i<brain_fuck.length(); i++ )
            assertEquals(brain_fuck.charAt(i), A5.ld1z(ptr+4+i));
    }

}
