package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.StoreNode;
import org.junit.Test;
import static org.junit.Assert.*;

public class FinalConstructionTest {
    private static final String LOOP = """
        val step={int x -> x*2+1;};
        struct C {
            val x=1;
            new C={int n ->
                x=0; x=1;
                for(int i=0; i<n; i++) x=step(x);
            };
            val get={->x;};
        };
        return new C(arg).get();
        """;

    @Test public void testFinalConstructorLocals() throws java.io.IOException {
        var code=new CodeGen(LOOP).driver(CodeGen.Phase.LocalSched);
        assertEquals("1",Eval2.eval(code,0));
        assertEquals("15",Eval2.eval(code,3));
        assertEquals("63",Eval2.eval(code,5));
        code._stop.walk(n -> {
            if( n instanceof StoreNode st && st._name.equals("x") )
                assertEquals("Final field writes belong after the loop",0,st.cfg0().loopDepth());
            return null;
        });
        String[] sources={
            "struct C { val x=1; new C={->x=1; x=2;}; }; return new C().x;",
            "struct C { int ~x; new C={int n -> self.x=1; for(int i=0;i<n;i++) self.x*=2;}; }; return new C(arg).x;",
            "struct C { val x=1; new C={int n -> for(int i=0;i<n;i++) ++x;}; }; return new C(arg).x;",
            "struct C { val x=1.5; new C={->x=2.5;}; }; return new C().x;",
            "struct C { val x=\"a\"; new C={int n -> for(int i=0;i<n;i++) x=\"b\";}; }; return new C(arg).x[0];",
            "struct C { val x=new int[1]; new C={->x=new int[2]; x=new int[3];}; }; return new C().x#;"
        };
        String[] results={"2","8","4","2.5","98","3"};
        for( int i=0; i<sources.length; i++ )
            assertEquals(results[i],Eval2.eval(new CodeGen(sources[i]).driver(CodeGen.Phase.TypeCheck),3));

        String[] rejected={
            "struct C { val x=1; }; val c=new C(); c.x=2; return c.x;",
            "struct C { new C={->val x=1; x=2;}; }; return new C();",
            "struct C { val f={->1;}; new C={->f={->2;};}; }; return new C().f();"
        };
        for( String src : rejected ) {
            try { new CodeGen(src).driver(CodeGen.Phase.TypeCheck); fail(src); }
            catch( Parser.ParseException e ) { assertTrue(e.getMessage(),e.getMessage().startsWith("Cannot")); }
        }

        var dir=java.nio.file.Path.of("build/objs");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("finalConstruction.smp"),"val buildFinal={int arg ->"+LOOP+"};");
        java.nio.file.Files.writeString(dir.resolve("finalConstruction.c"),"""
            extern long long buildFinal(long long);
            int main(void) { return buildFinal(0)!=1 || buildFinal(3)!=15 || buildFinal(5)!=63; }
            """);
        TestC.run("build/objs","finalConstruction","",-1);
    }
}
