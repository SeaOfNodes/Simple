package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BrainFuckTest {

    @Test
    public void testBrainfuck() {
        var program = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++.".getBytes(StandardCharsets.UTF_8);
        var encoded = new StringBuilder("u8[] program = new u8[").append(program.length).append("];");
        for (int i = 0; i < program.length; i++) {
            int value = program[i] & 0xFF;
            encoded.append("program[").append(i).append("] = ").append(value).append(";");
        }

        Parser parser = new Parser(
                encoded + """
int pc = 0;
int d = 0;
u8[] data = new u8[100];
u8[] output = new u8[0];

while (pc < program#) {
    int command = program[pc];
    if (command == 62) {
        d = d + 1;
    } else if (command == 60) {
        d = d - 1;
    } else if (command == 43) {
        data[d] = data[d] + 1;
    } else if (command == 45) {
        data[d] = data[d] - 1;
    } else if (command == 46) {
        u8[] old = output;
        output = new u8[output# + 1];
        int i = 0;
        while (i < old#) {
            output[i] = old[i];
            i = i + 1;
        }
        output[i] = data[d];
    } else if (command == 44) {
        data[d] = 42;
    } else if (command == 91) {
        if (data[d] == 0) {
            int d = 1;
            while (d > 0) {
                pc = pc + 1;
                command = program[pc];
                if (command == 91) d = d + 1;
                if (command == 93) d = d - 1;
            }
        }
    } else if (command == 93) {
        if (data[d] != 0) {
            int d = 1;
            while (d > 0) {
                pc = pc - 1;
                command = program[pc];
                if (command == 93) d = d + 1;
                if (command == 91) d = d - 1;
            }
        }
    }
    pc = pc + 1;
}
return output;
                """);
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("Hello World!\n", Evaluator.evaluate(stop, 0, 10000).toString());
    }
}
