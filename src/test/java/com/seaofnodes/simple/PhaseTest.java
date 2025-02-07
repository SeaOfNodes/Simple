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
        assertEquals("Parse Iter TypeCheck LoopTree Schedule LocalSched",String.join(" ",phases));
        assertEquals("45",Eval2.eval(code,10));
    }

    @Test public void testResumeMachineCompilation() {
        CodeGen staged = new CodeGen(SRC).driver(Phase.TypeCheck);
        staged.driver(Phase.InstSelect,"x86_64_v2","SystemV");
        staged.driver(Phase.RegAlloc,"x86_64_v2","SystemV");
        assertEquals(Phase.RegAlloc,staged._phase);
        assertNotNull(staged._mach);
    }
}
