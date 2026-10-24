package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.cpus.x86_64_v2.CmpMemX86;
import com.seaofnodes.simple.node.cpus.x86_64_v2.NotX86;
import com.seaofnodes.simple.node.cpus.x86_64_v2.MulIX86;
import org.junit.Test;
import static org.junit.Assert.*;

public class X86EncodingTest {

    @Test
    public void testLogicalNotRegisters() {
        CodeGen code = new CodeGen("return !arg;")
            .driver(CodeGen.Phase.Select, "x86_64_v2", "SystemV");
        var not = code._stop.walk(n -> n instanceof NotX86 ? (NotX86)n : null);
        assertNotNull("Must exercise logical not", not);
        int[][] registers = {{0,0}, {1,1}, {7,7}, {9,9}, {1,9}, {9,1}, {6,0}, {15,8}};
        // TEST src,src; SETZ dstb; MOVZX dst32,dstb. Include overlapping
        // registers, low-byte registers requiring REX, and extended registers.
        String[] expected = {
            "4885c00f94c00fb6c0", "4885c90f94c10fb6c9",
            "4885ff400f94c7400fb6ff", "4d85c9410f94c1450fb6c9",
            "4d85c90f94c10fb6c9", "4885c9410f94c1450fb6c9",
            "4885c0400f94c6400fb6f6", "4d85c0410f94c7450fb6ff"
        };
        for (int i = 0; i < registers.length; i++) {
            int dst = registers[i][0], src = registers[i][1];
            var enc = new FixedRegisterEncoding(code, not, dst, not.in(1), src);
            not.encoding(enc);
            assertArrayEquals("Logical not dst=" + dst + ", src=" + src,
                java.util.HexFormat.of().parseHex(expected[i]), enc._bits.toByteArray());
        }
    }

    @Test
    public void testLogicalNotExecution() throws java.io.IOException {
        var dir = java.nio.file.Path.of("build/objs");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("x86LogicalNot.smp"),
            "val logicalNot = { int x -> return !x; };");
        java.nio.file.Files.writeString(dir.resolve("x86LogicalNot.c"), """
            #include <limits.h>
            extern long long logicalNot(long long);
            int main(void) {
                long long inputs[] = {0, 1, -1, LLONG_MIN, LLONG_MAX, 1LL << 32};
                for (unsigned i = 0; i < sizeof(inputs)/sizeof(inputs[0]); i++)
                    if (logicalNot(inputs[i]) != (inputs[i] == 0)) return 1;
                return 0;
            }
            """);
        com.seaofnodes.simple.TestC.run("build/objs", "x86LogicalNot", "", -1);
    }

    @Test
    public void testMultiplyImmediateRegisters() {
        // Destination, source, expected REX and ModRM. Cross the register-8
        // boundary in both directions, with low/low and high/high controls.
        int[][] cases = {{1, 9, 0x49, 0xC9}, {9, 1, 0x4C, 0xC9},
                         {1, 2, 0x48, 0xCA}, {9, 10, 0x4D, 0xCA}};
        for (int immediate : new int[]{11, -11, 123456789, -123456789}) {
            CodeGen code = new CodeGen("return arg * " + immediate + ";")
                .driver(CodeGen.Phase.Select, "x86_64_v2", "SystemV");
            var mul = code._stop.walk(n -> n instanceof MulIX86 ? n : null);
            assertNotNull("Must exercise immediate multiply", mul);
            for (int[] regs : cases) {
                var enc = new FixedRegisterEncoding(
                    code, mul, regs[0], mul.in(1), regs[1]);
                ((MulIX86)mul).encoding(enc);
                boolean small = immediate == 11 || immediate == -11;
                assertEquals("REX for dst=" + regs[0] + ", src=" + regs[1], regs[2], enc.read1(0));
                assertEquals(small ? 0x6B : 0x69, enc.read1(1));
                assertEquals(regs[3], enc.read1(2));
                assertEquals(small ? 4 : 7, enc._bits.size());
                assertEquals(immediate, small ? (byte)enc.read1(3) : enc.read4(3));
            }
        }
    }
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

    /** Exercise instruction encoders with explicit registers, independent of allocator choices. */
    private static class FixedRegisterEncoding extends Encoding {
        private final Node _dst, _src;
        private final short _dstReg, _srcReg;

        FixedRegisterEncoding(CodeGen code, Node dst, int dstReg, Node src, int srcReg) {
            super(code);
            _dst = dst;
            _src = src;
            _dstReg = (short)dstReg;
            _srcReg = (short)srcReg;
        }

        @Override public short reg(Node n) {
            if (n == _dst) return _dstReg;
            if (n == _src) return _srcReg;
            throw new AssertionError("Unexpected register lookup");
        }
    }
}
