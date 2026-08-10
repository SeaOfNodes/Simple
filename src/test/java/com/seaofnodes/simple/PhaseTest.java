package com.seaofnodes.simple;

import com.seaofnodes.graph.GraphObserver;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.type.TypeInteger;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertNotNull(code._encoding);

        CodeGen memory = new CodeGen(SRC).driver("riscv","SystemV",true,false);
        assertNull(memory._serial);
        assertEquals(Phase.Export,memory._phase);

        // Keeping ideal IR must not force separate images when the caller
        // explicitly requests in-memory linking.
        CodeGen saved = new CodeGen(SRC).driver(Phase.Serialize);
        saved.driver("riscv","SystemV",true,false);
        assertNotNull(saved._serial);
        assertArrayEquals(memory._encoding._bits.toByteArray(),saved._encoding._bits.toByteArray());

        CodeGen late = new CodeGen(SRC).driver(Phase.Encoding,"riscv","SystemV");
        try {
            late.exportELF(false,false);
            fail("Cannot recover ideal IR after lowering and instruction selection");
        } catch( IllegalStateException expected ) {
            assertTrue(expected.getMessage().contains("Serialize before Lower"));
        }
    }

    @Test public void testStagedObjectExport() throws Exception {
        Path root = Files.createTempDirectory(Files.createDirectories(Path.of("build/objs")),"phase-export-");
        Path src = Files.createDirectories(root.resolve("src"));
        Files.createDirectories(src.resolve("Main"));
        Files.writeString(src.resolve("Main.smp"),"val run_noInline = { int n -> Helper.twice_noInline(n)+1; };");
        Files.writeString(src.resolve("Main/Helper.smp"),"val twice_noInline = { int n -> n*2; };");
        Path cfile = root.resolve("driver.c");
        Files.writeString(cfile,"""
            __attribute__((CALL_CONV)) extern long long run_noInline(long long);
            int main(void) { return run_noInline(20)!=41 || run_noInline(-3)!=-5; }
            """);
        for( boolean direct : new boolean[]{false,true} ) {
            Path obj = Files.createDirectories(root.resolve(direct ? "direct" : "driver"));
            CodeGen code = new CodeGen(src.toString(),obj.toString(),null,"Main",null,123,true,TypeInteger.BOT)
                .driver(Phase.Serialize);
            if( direct ) {
                code.driver(Phase.RegAlloc,TestC.CPU_PORT,TestC.CALL_CONVENTION);
                code.encode();
            } else {
                code.driver(Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION);
            }
            // Encoding has already separated the entry object's code from
            // its dependency. Export only writes the prepared encodings.
            assertNotNull(code._encoding);
            for( var block : code._encoding._cfg )
                if( block instanceof FunNode fun )
                    assertSame(code._compunits.get("Main"),fun._compunit);
            assertFalse(Files.exists(obj.resolve("Main.o")));
            if( direct ) code.exportELF(false,false);
            else code.driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
            assertTrue(Files.exists(obj.resolve("Main.o")));
            assertTrue(Files.exists(obj.resolve("Main/Helper.o")));
            String exe = obj.resolve(TestC.OS.startsWith("Windows") ? "run.exe" : "run").toString();
            assertEquals("",TestC.gcc(code,TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",
                                     cfile.toString(),null,exe));
        }
        // The same serialized units can instead form one linked memory image.
        CodeGen memory = new CodeGen(src.toString(),root.resolve("memory").toString(),null,
            "Main",null,123,true,TypeInteger.BOT).driver(Phase.Serialize);
        memory.driver("riscv","SystemV",true,false);
        boolean helper = false;
        for( var block : memory._encoding._cfg )
            if( block instanceof FunNode fun && fun._compunit==memory._compunits.get("Main/Helper") )
                helper = true;
        assertTrue("The combined image includes dependency bodies",helper);
    }
}
