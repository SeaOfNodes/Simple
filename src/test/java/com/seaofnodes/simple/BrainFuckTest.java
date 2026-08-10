package com.seaofnodes.simple;

import com.seaofnodes.isa.eval.EvalRisc5;

import com.seaofnodes.isa.eval.EvalArm64;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BrainFuckTest {

    @Test public void testBrainFuckAryu8() throws IOException {
        String brain_fuck = "Hello World!\n";
        String src =
"""
// -*- mode: java;  -*-
val brain_fuck = { ->
    var !program = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++.";
    int d = 0;
    val output = new sys.aryu8();
    u8[] !data = new u8[100];

    for( int pc = 0; pc < program#; pc++ ) {
        var command = program[pc];
        if (command == '>') {
            d++;
        } else if (command == '<') {
            d--;
        } else if (command == '+') {
            data[d]++;
        } else if (command == '-') {
            data[d]--;
        } else if (command == '.') {
            output.add(data[d]);
        } else if (command == ',') {
            data[d] = 42;
        } else if (command == '[') {
            if (data[d] == 0) {
                for( int d = 1; d > 0; ) {
                    command = program[++pc];
                    if (command == 91) d++;
                    if (command == 93) d--;
                }
            }
        } else if (command == ']') {
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
val main = { ->
    brain_fuck().write(sys.libc.STDOUT);
    return 0;
};
""";
        TestC.runSYS(src,"brain_fuck_aryu8",brain_fuck,-1);
    }

    @Test public void testBrainFuck() throws IOException {
        // Retain the original workload for cross-target allocation comparisons.
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/spill/BrainFuckTest-testBrainFuck.smp"));
        String brain_fuck = "Hello World!\n";
        TestC.runC(src,"brain_fuck", brain_fuck,165);

        EvalRisc5 R5 = TestRisc5.build( src, "brain_fuck", 0, 25, false);
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
            assertEquals(brain_fuck.charAt(i), A5.ld1z(ptr_arm+4+i));
    }

}
