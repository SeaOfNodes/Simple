package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;
import static org.junit.Assert.*;

public class ClassInitDependenciesTest {
    private final ArrayList<Path> dirs = new ArrayList<>();

    @Rule public final TestRule cleanup = (test, description) -> new Statement() {
        @Override public void evaluate() throws Throwable {
            // Keep all fixtures if compilation or any later assertion fails.
            test.evaluate();
            for( Path dir : dirs ) {
                Path[] files;
                try( var paths=Files.walk(dir) ) {
                    files=paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                }
                for( Path file : files ) Files.delete(file);
            }
        }
    };

    private Path tempDir(String prefix) throws Exception {
        Path build=Path.of("build/init-deps");
        Files.createDirectories(build);
        Path dir=Files.createTempDirectory(build,prefix);
        dirs.add(dir);
        return dir;
    }

    private CodeGen compile(String entry, Map<String,String> sources, long seed) throws Exception {
        Path root=tempDir("case-");
        Files.writeString(root.resolve("Root.smp"),"val entry="+entry+";");
        for( var source : sources.entrySet() ) {
            Path path=root.resolve("Root/"+source.getKey()+".smp");
            Files.createDirectories(path.getParent());
            Files.writeString(path,source.getValue());
        }
        return new CodeGen(root.toString(),root.resolve("obj").toString(),null,
                           "Root",null,seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
    }

    private void cycle(String entry, Map<String,String> sources, String expected) throws Exception {
        expected="Root."+expected.replace(" -> "," -> Root.");
        for( long seed : new long[]{1,123,987654321} ) {
            try { compile(entry,sources,seed); fail("Expected initialization cycle"); }
            catch( Parser.ParseException ex ) {
                assertTrue(ex.getMessage(),ex.getMessage().startsWith("Cyclic class initialization: "+expected));
                assertTrue("Include a source witness",ex.getMessage().contains(" at 1:"));
            }
        }
    }

    @Test public void testOrderAndPassiveReferences() throws Exception {
        CodeGen code = compile("A",Map.of("A","int value=B.value;", "B","int value=arg|1;"),123);
        assertEquals("[Root, Root.B, Root.A]",code._classInitDeps.order().stream().map(c -> c._cname).toList().toString());
        assertTrue(code._compunits.get("Root/A")._initComplete);
        code=compile("A",Map.of("A","val b=B;", "B","val a=A;"),123);
        for( CompUnit cu : code._classInitDeps.order() ) {
            if( cu._par==null ) assertNull(cu._classInitDeps);
            else {
                assertEquals(1,cu._classInitDeps.size());
                assertSame(cu._par,cu._classInitDeps.at(0));
            }
            assertTrue(cu._initComplete);
        }
    }

    @Test public void testCycles() throws Exception {
        cycle("A",Map.of("A","int value=B.value;", "B","int value=A.value;"),"A -> B -> A");
        cycle("A",Map.of("A","val get={ -> B.value; }; int value=get();",
                          "B","int value=A.value;"),"A -> B -> A");
        cycle("A",Map.of("A","int value=get(); val get={ -> B.value; };",
                          "B","int value=A.value;"),"A -> B -> A");
        cycle("A",Map.of("A","int value=0; if(arg) value=B.value;",
                          "B","int value=A.value;"),"A -> B -> A");
        cycle("A",Map.of("A","val f=arg ? { -> B.value; } : { -> 0; }; int value=f();",
                          "B","int value=A.value;"),"A -> B -> A");
        cycle("P",Map.of("P","int value=A.value;", "P/A","int value=arg;"),"P -> P.A -> P");
    }

    @Test public void testSiblingsAndNonreturningCall() throws Exception {
        cycle("P",Map.of("P","val a=A; val b=B;", "P/A","int value=B.value;",
                          "P/B","int value=A.value;"),"P.A -> P.B -> P.A");
        cycle("A",Map.of("A","val get={ -> val x=B.value; while(1) {} return x; }; int value=get();",
                          "B","int value=A.value;"),"A -> B -> A");
        // An out-of-line nonreturning call retains its continuation's touches.
        cycle("A",Map.of(
            "A","int value=1; val stop_noInline={ -> while(1) {} return 1; }; stop_noInline(); return B.value;",
            "B","int value=A.value;"),"A -> B -> A");
        String source="val stop_noInline={ -> while(1) {} return 1; }; stop_noInline(); return 2;";
        CodeGen code=new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
        assertNull(Eval2.eval(code,0,10)); // Evaluation reaches its loop budget.
        for( String cpu : new String[]{"x86_64_v2","arm","riscv"} )
            new CodeGen(source).driver(CodeGen.Phase.Encoding,cpu,"SystemV");
    }

    @Test public void testOptimizedDependencies() throws Exception {
        for( long seed : new long[]{1,123,987654321} ) {
            // SCCP learns this condition from a call; the parser cannot decide it.
            CodeGen code=compile("A",Map.of(
                "A","val test_noInline={ -> false; }; int value=1; if(test_noInline()) value=B.value;",
                "B","int value=A.value;"),seed);
            CompUnit a=code._compunits.get("Root/A"), b=code._compunits.get("Root/B");
            assertTrue(a._initComplete);
            assertEquals(-1,a._classInitDeps.find(b));
            assertTrue(b._classInitDeps.find(a)>=0);

            code=compile("A",Map.of(
                "A","val test_noInline={ -> false; }; val f=test_noInline() ? { -> B.value; } : { -> 1; }; int value=f();",
                "B","int value=A.value;"),seed);
            a=code._compunits.get("Root/A"); b=code._compunits.get("Root/B");
            assertTrue(a._initComplete);
            assertEquals(-1,a._classInitDeps.find(b));

            // An unused helper's body must not become an initializer dependency.
            code=compile("A",Map.of("A","val unused={ -> B.value; }; int value=1;",
                                    "B","int value=A.value;"),seed);
            assertEquals(-1,code._compunits.get("Root/A")._classInitDeps.find(code._compunits.get("Root/B")));
        }
        // Discarding the value of a field read cannot discard its active touch.
        cycle("A",Map.of("A","int value=1; B.value; return 0;",
                          "B","int value=2; A.value; return 0;"),"A -> B -> A");
    }

    @Test public void testRecursionAndIncompleteEffects() throws Exception {
        CodeGen code=compile("A",Map.of("A","val f={int n -> n ? f(n-1) : B.value; }; int value=f(arg);",
                                       "B","int value=arg|1;"),123);
        assertTrue(code._compunits.get("Root/A")._initComplete);
        assertTrue(code._compunits.get("Root/A")._classInitDeps.find(code._compunits.get("Root/B"))>=0);
        code=compile("A",Map.of("A","{int} native=\"C\"; return native();"),123);
        assertFalse("Unknown native effects are not certified empty",code._compunits.get("Root/A")._initComplete);

        Path dir=tempDir("import-");
        new CodeGen(null,dir.toString(),null,"B","int value=1;",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        code=new CodeGen(null,dir.toString(),new Ary<>(new String[]{dir.toString()}),
                         "A","return B.value;",123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertFalse("Imported summaries wait for checkpoint 2",code._compunits.get("B")._initComplete);
        assertFalse(code._compunits.get("A")._initComplete);
    }
}
