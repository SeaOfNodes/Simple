package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.type.Field;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeTuple;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        return new CodeGen(root.toString(),root.resolve("obj").toString(),
                           "Root",null,seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
    }

    private void cycle(String entry, Map<String,String> sources, String expected) throws Exception {
        expected="Root."+expected.replace(" -> "," -> Root.");
        for( long seed : new long[]{1,123,987654321} ) {
            try { compile(entry,sources,seed); fail("Expected initialization cycle"); }
            catch( Parser.ParseException ex ) {
                assertCycle(ex.getMessage(),expected);
            }
        }
    }

    // The report lists internal SCC edges, including ordinary helper calls.
    private HashMap<String,HashSet<String>> reportEdges(String message) {
        String[] lines=message.split("\n");
        assertEquals(message,"Cyclic class initialization:",lines[0]);
        HashMap<String,HashSet<String>> edges=new HashMap<>();
        for( int i=1; i<lines.length; i++ ) {
            if( !lines[i].startsWith("  ") || lines[i].startsWith("    ") ) continue; // Diagnostic/source/caret.
            String[] edge=lines[i].trim().split(" -> ");
            assertEquals(message,2,edge.length);
            edges.computeIfAbsent(edge[0],key -> new HashSet<>()).add(edge[1]);
        }
        return edges;
    }

    // Follow helpers in the diagnostic, stopping at the next class initializer.
    private boolean reportedTouch(HashMap<String,HashSet<String>> edges, String from, String to, HashSet<String> seen) {
        if( !seen.add(from) || !edges.containsKey(from) ) return false;
        for( String dst : edges.get(from) )
            if( dst.equals(to) || (dst.contains(" [fidx ") && reportedTouch(edges,dst,to,seen)) ) return true;
        return false;
    }

    private void assertCycle(String message, String expected) {
        HashMap<String,HashSet<String>> edges=reportEdges(message);
        String[] path=expected.split(" -> ");
        for( int i=1; i<path.length; i++ )
            assertTrue("Missing "+path[i-1]+" -> "+path[i]+" in:\n"+message,
                       reportedTouch(edges,path[i-1],path[i],new HashSet<>()));
    }

    // Follow ordinary helpers, stopping at the next initializer. This checks
    // the class prerequisite relation without requiring a total init order.
    private boolean touches(CodeGen code, ClassInitDependencies deps, String from, String to) {
        int src=code.link(code._compunits.get(from)).sig().fidx();
        int dst=code.link(code._compunits.get(to)).sig().fidx();
        return touches(code,deps._edgess,src,dst,new BitSet());
    }

    private boolean touches(CodeGen code, int[][] edges, int src, int dst, BitSet seen) {
        if( src>=edges.length || seen.get(src) || edges[src]==null ) return false;
        seen.set(src);
        for( int edge : edges[src] ) {
            if( edge==dst ) return true;
            FunNode fun=code.link(edge);
            if( fun!=null && !fun.isClz() && touches(code,edges,edge,dst,seen) ) return true;
        }
        return false;
    }

    @Test public void testEdgesAndPassiveReferences() throws Exception {
        CodeGen code = compile("A",Map.of("A","int value=B.value;", "B","int value=arg|1;"),123);
        ClassInitDependencies deps=new ClassInitDependencies(code);
        assertTrue(touches(code,deps,"Root/A","Root/B"));
        code=compile("A",Map.of("A","val b=B;", "B","val a=A;"),123);
        deps=new ClassInitDependencies(code);
        assertFalse(touches(code,deps,"Root/A","Root/B"));
        assertFalse(touches(code,deps,"Root/B","Root/A"));
        assertTrue(touches(code,deps,"Root/A","Root"));
        assertTrue(touches(code,deps,"Root/B","Root"));
    }

    @Test public void testCycles() throws Exception {
        cycle("A",Map.of("A","val get_noInline={ ->\n  B.value;\n};\nint value=get_noInline();",
                          "B","int value=A.value;"),"A -> B -> A");
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
            ClassInitDependencies deps=new ClassInitDependencies(code);
            assertFalse(touches(code,deps,"Root/A","Root/B"));
            assertTrue(touches(code,deps,"Root/B","Root/A"));

            code=compile("A",Map.of(
                "A","val test_noInline={ -> false; }; val f=test_noInline() ? { -> B.value; } : { -> 1; }; int value=f();",
                "B","int value=A.value;"),seed);
            assertFalse(touches(code,new ClassInitDependencies(code),"Root/A","Root/B"));

            // An unused helper's body must not become an initializer dependency.
            code=compile("A",Map.of("A","val unused={ -> B.value; }; int value=1;",
                                    "B","int value=A.value;"),seed);
            assertFalse(touches(code,new ClassInitDependencies(code),"Root/A","Root/B"));
        }
        // Discarding the value of a field read cannot discard its active touch.
        cycle("A",Map.of("A","int value=1; B.value; return 0;",
                          "B","int value=2; A.value; return 0;"),"A -> B -> A");
    }

    @Test public void testRecursionAndNativeCalls() throws Exception {
        CodeGen code=compile("A",Map.of("A","val f={int n -> n ? f(n-1) : B.value; }; int value=f(arg);",
                                       "B","int value=arg|1;"),123);
        assertTrue(touches(code,new ClassInitDependencies(code),"Root/A","Root/B"));
        code=compile("A",Map.of("A","{int} native=\"C\"; return native();"),123);

    }

    private void object(Path dir, Path unused, String name, String source) throws Exception {
        Path file=dir.resolve("Root/"+name+".smp");
        Files.createDirectories(file.getParent());
        Files.writeString(file,source);
    }

    private void module(Path dir, Path unused, String name, Map<String,String> sources) throws Exception {
        for( var entry : sources.entrySet() ) object(dir,dir,entry.getKey(),entry.getValue());
    }

    private CodeGen entry(Path dir, String source, long seed) throws Exception {
        Files.writeString(dir.resolve("Root.smp"),"");
        object(dir,dir,"Main",source);
        return new CodeGen(dir.toString(),dir.toString(),"Root/Main",null,seed,true,TypeInteger.BOT);
    }

    private CodeGen emit(Path dir, String name, String source) throws Exception {
        return entry(dir,source,123).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
    }

    @Test public void testCycleReportDirection() throws Exception {
        cycle("A",Map.of("A","int value=B.value;", "B","int value=C.value;",
                          "C","int value=A.value;"),"A -> B -> C -> A");
        for( long seed : new long[]{1,123,987654321} ) {
            // Stack order A,B,C is not a cycle: B has no edge to C. All four
            // internal edges must be shown; edges to Root/D must be omitted.
            try {
                compile("A",Map.of("A","int value=B.value+C.value+D.value;",
                                   "B","int value=A.value;", "C","int value=A.value;",
                                   "D","int value=arg;"),seed);
                fail("Expected initialization cycle");
            } catch( Parser.ParseException ex ) {
                assertEquals(Map.of("Root.A",Set.of("Root.B","Root.C"),
                                    "Root.B",Set.of("Root.A"), "Root.C",Set.of("Root.A")),
                             reportEdges(ex.getMessage()));
            }
        }
    }

    @Test public void testCycleSourceLocations() throws Exception {
        for( String name : new String[]{"read", "read_noInline"} ) {
            try {
                compile("A",Map.of("A","int value=Helper."+name+"();",
                    "Helper","val "+name+"={ ->\n  B.value;\n};",
                    "B","int value=A.value;"),123);
                fail("Expected initialization cycle");
            } catch( Parser.ParseException ex ) {
                String message=ex.getMessage();
                assertCycle(message,"Root.A -> Root.B -> Root.A");
                // Inlining must keep Helper's source identity, not substitute A.
                assertTrue(message,message.contains("Helper.smp:2:4"));
                assertTrue(message,message.contains("  B.value;\n    ---^"));
                if( name.endsWith("noInline") ) {
                    assertTrue(message,message.contains("A.smp:1:31"));
                    assertTrue(message,message.contains("int value=Helper.read_noInline();"));
                }
            }
        }
    }

    @Test public void testSourceError() {
        String src="// π\r\n\treturn 1;\r\n";
        CodeGen code=new CodeGen(src).driver(CodeGen.Phase.Parse);
        CompUnit cu=code._compunits.get("Test");
        int pos=src.substring(0,src.indexOf('1')).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertEquals("Test.smp:2:9: error: example\n            return 1;\n    ---------------^",SourceError.format(cu,pos,"example"));
        assertEquals("Test.smp:3:1: error: example\n    \n    ^",SourceError.format(cu,src.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,"example"));
    }

    @Test public void testSelfInitialization() throws Exception {
        for( String name : new String[]{"read","read_noInline"} )
            for( long seed : new long[]{1,123,987654321} )
                compile("A",Map.of("A","int value=arg; val "+name+"={ -> A.value; }; int result="+name+"();"),seed);
    }

    @Test public void testInitializationMemory() throws Exception {
        String[] sources={
            "int value=B.value+B.value;",
            "int value; if(arg) value=B.value; else value=B.value+1; value=value+B.value;",
            "int value; if(arg) value=B.value; value=value+B.value;"
        };
        int[] counts={1,2,2};
        for( long seed : new long[]{1,123,987654321} )
            for( int i=0; i<sources.length; i++ ) {
                CodeGen code=compile("A",Map.of("A",sources[i],"B","int value=arg;"),seed);
                FunNode fun=code.link(code._compunits.get("Root/A"));
                CompUnit b=code._compunits.get("Root/B");
                int[] touches={0};
                code._stop.walk(n -> {
                    if( n instanceof CLInitNode init && init.fun()==fun && init.cu()==b ) {
                        touches[0]++;
                        TypeTuple state=(TypeTuple)init._type;
                        assertSame(TypeMem.BOT,state._types[1]);
                        TypeMem flag=(TypeMem)state._types[2];
                        assertEquals(b._clz.field("$init")._alias,flag._alias);
                        assertSame(TypeInteger.TRUE,flag._t);
                        assertNotNull(init.ready());
                    }
                    return null;
                });
                assertEquals(sources[i],counts[i],touches[0]);
            }
    }

    @Test public void testForwardInstanceTouch() {
        String src="""
            struct S { int x=7; };
            val _get={ -> _make().x; };
            val _make={ -> new S; };
            return _get();
            """;
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code=new CodeGen(src,seed,true).driver(CodeGen.Phase.Parse);
            CLInitNode init=code._stop.walk(n -> n instanceof CLInitNode c ? c : null);
            assertNotNull("The unresolved receiver needs a speculative check",init);
            assertNotNull("The readiness load is present from parsing",init.ready());
            code.driver(CodeGen.Phase.TypeCheck);
            assertNull("An instance needs neither CLInit nor its speculative $init load",
                code._stop.walk(n -> n instanceof CLInitNode ||
                    (n instanceof LoadNode ld && ld._name.equals("$init")) ? n : null));
            assertEquals("7",Eval2.eval(code,0));
        }
    }

    @Test public void testInitializerFlagStore() throws Exception {
        // A same-named struct must retain the file class's hidden flag.
        CodeGen code=compile("A",Map.of("A","struct A { int value; }; int value=arg;"),123);
        CompUnit a=code._compunits.get("Root/A");
        Field flag=a._clz.field("$init");
        assertNotNull(flag);
        assertFalse(flag._final);
        FunNode fun=code.link(a);
        int[] stores={0};
        code._stop.walk(n -> {
            if( n instanceof StoreNode st && st._name.equals("$init") && st._alias==flag._alias ) {
                stores[0]++;
                assertSame(TypeInteger.TRUE,st.val()._type);
                assertSame(fun,CFGNode.earlyCFG(st).fun());
            }
            return null;
        });
        assertEquals(1,stores[0]);
    }

    @Test public void testInitializationExecution() throws Exception {
        Path dir=tempDir("execution-");
        object(dir,dir,"State","int count=0;");
        object(dir,dir,"B","State.count++; int value=17+arg;");
        String src="int value=arg ? B.value+B.value : 0; return value+100*State.count;";
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code=entry(dir,src,seed).driver(CodeGen.Phase.TypeCheck);
            assertNotNull(code._stop.walk(n -> n instanceof CLInitNode ? n : null));
            assertEquals("0",Eval2.eval(code,0));
            assertNull(code._stop.walk(n -> n instanceof CLInitNode ? n : null));
            assertEquals("134",Eval2.eval(code,1));
        }

        emit(dir,"Main","val run={int take -> "+src.replace("arg ?","take ?")+" };");
        Path cfile=dir.resolve("check.c");
        Files.writeString(cfile,"""
            #include <stdint.h>
            __attribute__((CALL_CONV)) extern int64_t run(int64_t take) __asm__("Root.Main.run");
            int main(void) {
                return run(0)!=0 || run(1)!=134 || run(1)!=134 || run(0)!=100;
            }
            """);
        String exe=dir.resolve(TestC.OS.startsWith("Windows") ? "check.exe" : "check").toString();
        TestC.linkExe(dir.resolve("Root/Main.o").toString(),TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",
            cfile.toString(),null,exe);
        assertEquals("",TestC.exec(exe));
        for( String cpu : new String[]{"arm","riscv"} )
            entry(dir,src,123).driver(CodeGen.Phase.Encoding,cpu,"SystemV");
    }

    @Test public void testParentInitializationExecution() throws Exception {
        Path dir=tempDir("execution-");
        object(dir,dir,"State","int count=0;");
        module(dir,dir,"P",Map.of("P","val C=C; State.count=State.count*10+1;",
            "P/C","State.count=State.count*10+2; int value=arg;"));
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code=entry(dir,"return P.C.value+State.count;",seed).driver(CodeGen.Phase.TypeCheck);
            assertEquals("12",Eval2.eval(code,99));
        }
        emit(dir,"Main","val run={ -> P.C.value+State.count; };");
        Path cfile=dir.resolve("check.c");
        Files.writeString(cfile,"""
            #include <stdint.h>
            __attribute__((CALL_CONV)) extern int64_t run(void) __asm__("Root.Main.run");
            int main(void) { return run()!=12 || run()!=12; }
            """);
        String exe=dir.resolve(TestC.OS.startsWith("Windows") ? "check.exe" : "check").toString();
        TestC.linkExe(dir.resolve("Root/Main.o").toString(),TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",
            cfile.toString(),null,exe);
        assertEquals("",TestC.exec(exe));
    }

    @Test public void testNonreturningObject() throws Exception {
        Path dir=tempDir("nonreturning-");
        object(dir,dir,"Spin","val spin_noInline={ -> while(1) {} return 7; };");
        CodeGen code=entry(dir,"Spin.spin_noInline(); return 7;",123).driver(CodeGen.Phase.TypeCheck);
        assertNull(Eval2.eval(code,0,10));
    }


    @Test public void testOverlappingFunctionAndClassCycles() throws Exception {
        // Fixed function graph: Opto/inlining must not erase the overlap that
        // exercises SCC traversal. One clinit is legal, two in the SCC are not.
        CodeGen code=compile("A",Map.of("A","val b=B; int value=arg;", "B","int value=arg;"),123);
        int f=code._linker._len, g=f+1, a=f+2, b=f+3;
        String[] names={"f", "g", "A.<clinit>", "B.<clinit>"};
        for( int i=0; i<names.length; i++ ) {
            TypeFunPtr sig=TypeFunPtr.make1((byte)2,false,new Type[0],TypeInteger.BOT,i+f);
            CompUnit cu=code._compunits.get(i==3 ? "Root/B" : "Root/A");
            FunNode fun=new FunNode(null,sig,names[i],cu);
            fun._type=Type.CONTROL;
            code._linker.setX(i+f,fun);
        }
        ClassInitDependencies deps=new ClassInitDependencies(code);
        deps._edgess[f]=new int[]{g}; // f -> g
        deps._edgess[g]=new int[]{f}; // g -> f
        assertNull("Ordinary mutual recursion is legal",deps.check());
        deps._edgess[a]=new int[]{a}; // A -> A
        assertNull("A singleton initializer self-edge is legal",deps.check());
        deps._edgess[a]=new int[]{g}; // A -> g
        deps._edgess[f]=new int[]{g,a};
        assertNull("A cycle containing only one initializer is legal",deps.check());
        deps._edgess[a]=new int[]{b}; // A -> B
        deps._edgess[b]=new int[]{g}; // B -> g -> f -> A
        String forward=deps.check();
        deps._edgess[f]=new int[]{a,g};
        String reverse=deps.check();
        assertTrue("Both successor orders must reject the class cycle: "+forward+", "+reverse,
                   forward!=null && reverse!=null);
        assertCycle(forward,"Root.A -> Root.B -> Root.A");
        assertCycle(reverse,"Root.A -> Root.B -> Root.A");
    }
}
