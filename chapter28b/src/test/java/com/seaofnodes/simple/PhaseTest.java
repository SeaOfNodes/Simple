package com.seaofnodes.simple;

import com.seaofnodes.graph.GraphObserver;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.node.Node;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhaseTest {
    private static final String SRC = "int sum=0; for(int i=0; i<arg; i++) sum+=i; return sum;";

    @Test public void testIdealPhasesAndResume() {
        ArrayList<String> phases = new ArrayList<>();
        CodeGen code = new CodeGen(SRC);
        code._obs = new GraphObserver<Node>() {
            @Override public void before(Node n) {}
            @Override public void after(Node n, Node repl, boolean applied) {}
            @Override public void phase(String phase) { phases.add(phase); }
        };
        code.driver(Phase.TypeCheck);
        code.driver(Phase.TypeCheck); // A repeated request does not rerun anything.
        assertEquals("45",Eval2.eval(code,10));
        code.driver(Phase.LocalSched);
        assertEquals("Parse Iter Opto IterPostOpto FinalizeIR TypeCheck LoopTree Unlink Schedule LocalSched",
                     String.join(" ",phases));
        assertNull(code._serial);
        assertNull(code._mach);
    }

    @Test public void testEncodingWithoutSerialization() {
        for( String cpu : new String[]{"x86_64_v2","arm","riscv"} ) {
            CodeGen code = new CodeGen(SRC).driver(Phase.TypeCheck);
            code.driver(Phase.Unlink,cpu,"SystemV");
            code.driver(Phase.Select,cpu,"SystemV");
            code.driver(Phase.Encoding,cpu,"SystemV");
            assertNull(code._serial);
            byte[] staged = code._encoding._bits.toByteArray();
            CodeGen direct = new CodeGen(SRC).driver(Phase.Encoding,cpu,"SystemV");
            assertArrayEquals(staged,direct._encoding._bits.toByteArray());
        }
    }

    @Test public void testSchedulingScratchAcrossCompilations() {
        String src = """
            int[] !xs = new int[2];
            xs[0] = arg;
            int sum = 0;
            for( int i=0; i<5; i++ ) {
                int old = xs[arg&1];
                xs[0] = i;
                sum += old;
            }
            return sum;
            """;
        for( int round=0; round<3; round++ ) {
            CodeGen code = new CodeGen(src).driver(Phase.TypeCheck);
            // A small graph after many earlier node allocations, then fresh
            // compilations which reuse its IDs and scheduling scratch.
            if( round==0 ) code._uid += 100_000;
            assertEquals("6",Eval2.eval(code,0));
            assertEquals("0",Eval2.eval(code,1));
            assertEquals("8",Eval2.eval(code,2));
        }
    }

    @Test public void testExplicitSerializationAndMemoryExport() {
        CodeGen code = new CodeGen(SRC).driver(Phase.Serialize);
        assertNotNull(code._serial);
        assertEquals(Phase.Serialize,code._phase);
        code.driver(Phase.Encoding,"riscv","SystemV");
        assertNotNull(code._serial);

        CodeGen memory = new CodeGen(SRC).driver("riscv","SystemV",true,false);
        assertNull(memory._serial);
        assertEquals(Phase.Export,memory._phase);

        CodeGen late = new CodeGen(SRC).driver(Phase.Encoding,"riscv","SystemV");
        try {
            late.exportELF(false,false);
            fail("Cannot recover ideal IR after lowering and instruction selection");
        } catch( IllegalStateException expected ) {
            assertTrue(expected.getMessage().contains("Serialize before Lower"));
        }
    }
}
