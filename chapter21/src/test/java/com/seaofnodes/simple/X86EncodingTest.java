package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.x86_64_v2.CmpMemX86;
import org.junit.Test;
import static org.junit.Assert.*;

public class X86EncodingTest {
    @Test
    public void testMemoryCompare64Immediate32() {
        for (int immediate : new int[]{123456789, -123456789}) {
            String src = "int[] !a = new int[2]; a[0] = arg; return a[arg & 1] == " + immediate + ";";
            CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Encoding, "x86_64_v2", "SystemV");
            var cmp = code._stop.walk(n -> n instanceof CmpMemX86 ? n : null);
            assertNotNull("Must exercise a memory comparison", cmp);
            var enc = code._encoding;
            int start = enc._opStart[cmp._nid];

            // REX.W, 81 /7, SIB with scale 8, disp8, and a sign-extended imm32.
            assertEquals(0x48, enc.read1(start) & 0xF8);
            assertEquals(0x81, enc.read1(start + 1));
            assertEquals(0x7C, enc.read1(start + 2));
            assertEquals(0xC0, enc.read1(start + 3) & 0xC0);
            assertEquals(8, enc.read1(start + 4));
            assertEquals(immediate, enc.read4(start + 5));
            assertEquals("CMP r/m64 uses four immediate bytes", 9, enc._opLen[cmp._nid]);

            // SETE must immediately follow the CPU's nine-byte CMP instruction.
            assertEquals(0x0F, enc.read1(start + 9));
            assertEquals(0x94, enc.read1(start + 10));
        }
    }
}
