package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.isa.eval.EvalArm64;
import com.seaofnodes.isa.eval.EvalRisc5;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeAddressTest {
    @Test public void testReturnedNativeAddress() throws IOException {
        Path dir = Path.of("build/objs");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("nativeAddress.smp"), """
            val getAbs = { ->
                { i32 -> i32 } abs = "C";
                return abs;
            };
            """);
        Files.writeString(dir.resolve("nativeAddress.c"), """
            typedef int (*func)(int);
            extern func getAbs(void);
            int main(void) {
                func f = getAbs();
                return f(-7) != 7 || f(9) != 9;
            }
            """);
        TestC.run("build/objs", "nativeAddress", "", -1);
    }

    @Test public void testEmulatedNativeAddress() throws IOException {
        // No native call: return the address itself, patched to the emulator's
        // known write entry. Both ports share Encoding's symbol lookup.
        String src = "{ int -> int } write = \"C\"; return write;";
        for( String cpu : new String[]{"arm","riscv"} ) {
            CodeGen code = new CodeGen(src).driver(cpu,"SystemV",null);
            byte[] bits = code._encoding.bits();
            byte[] image = new byte[1<<20];
            System.arraycopy(bits,0,image,0,bits.length);
            if( cpu.equals("arm") ) {
                EvalArm64 eval = new EvalArm64(image,1<<16);
                assertEquals(0,eval.step(100));
                assertEquals(Encoding.SENTINEL_WRITE,eval.regs[0]);
            } else {
                EvalRisc5 eval = new EvalRisc5(image,1<<16);
                assertEquals(0,eval.step(100));
                assertEquals(Encoding.SENTINEL_WRITE,eval.regs[10]);
            }
        }
    }
}
