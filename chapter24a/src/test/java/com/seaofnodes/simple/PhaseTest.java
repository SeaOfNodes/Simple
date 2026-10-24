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

    @Test public void testStopAndResume() {
        ArrayList<String> phases = new ArrayList<>();
        CodeGen code = new CodeGen(SRC);
        code._obs = new GraphObserver<Node>() {
            @Override public void before(Node n) {}
            @Override public void after(Node n, Node repl, boolean applied) {}
            @Override public void phase(String phase) { phases.add(phase); }
        };
        code.driver(Phase.Iter);
        assertEquals(Phase.Iter,code._phase);
        assertEquals("Parse Iter",String.join(" ",phases));
        code.driver(Phase.TypeCheck);
        code.driver(Phase.TypeCheck); // Repeated requests do not rerun phases.
        code.driver(Phase.LoopTree);
        assertEquals(Phase.LoopTree,code._phase);
        code.driver(Phase.LocalSched);
        assertEquals("Parse Iter Opto IterPostOpto FinalizeIR TypeCheck LoopTree Schedule LocalSched",String.join(" ",phases));
        assertEquals("45",Eval2.eval(code,10));
    }

    @Test public void testResumeMachineCompilation() {
        CodeGen staged = new CodeGen(SRC).driver(Phase.TypeCheck);
        staged.driver(Phase.Select,"x86_64_v2","SystemV");
        staged.driver(Phase.Encoding,"x86_64_v2","SystemV");
        byte[] bits = staged._encoding._bits.toByteArray();
        CodeGen direct = new CodeGen(SRC).driver(Phase.Encoding,"x86_64_v2","SystemV");
        assertArrayEquals(bits,direct._encoding._bits.toByteArray());
    }

    @Test public void testOptoRequiresIter() {
        CodeGen code = new CodeGen(SRC).parse();
        try {
            code.opto();
            fail("Opto must not implicitly run Iter");
        } catch( AssertionError expected ) {
            assertEquals("Cannot run Opto after Parse",expected.getMessage());
        }
        code.iter().opto();
        assertEquals(Phase.Opto,code._phase);
        code.driver(Phase.LocalSched);
        assertEquals("45",Eval2.eval(code,10));
    }
}
