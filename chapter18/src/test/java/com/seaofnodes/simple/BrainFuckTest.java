package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BrainFuckTest {

    @Test
    public void testBrainfuck() {
        var program = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++.".getBytes(StandardCharsets.UTF_8);
        var encoded = new StringBuilder("u8[] !program = new u8[").append(program.length).append("];");
        for (int i = 0; i < program.length; i++) {
            int value = program[i] & 0xFF;
            encoded.append("program[").append(i).append("] = ").append(value).append(";");
        }

        CodeGen code = new CodeGen(
                encoded + """

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
                """);
        code.parse().opto().typeCheck().GCM();
        assertEquals("Hello World!\n", Eval2.eval(code, 0, 10000));
    }
}
