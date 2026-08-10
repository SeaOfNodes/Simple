package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.io.IOException;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SelectiveLoadingTest {
    @Test public void testSharedConstantsInSeparateObjects() throws Exception {
        Path root = library("shared-constants-");
        Path src = Files.createDirectory(root.resolve("src"));
        Path obj = Files.createDirectory(root.resolve("obj"));
        write(src,"Lib","val sum_noInline = { int n -> Left.at_noInline(n)+Right.at_noInline(n); };");
        write(src,"Lib/Left","val at_noInline = { int n -> n+1000000000000; };");
        write(src,"Lib/Right","val at_noInline = { int n -> n*2+1000000000000; };");
        // Both bodies share a constant in the compiling graph. Each saved
        // object must contain it without pulling in the other unit's body.
        new CodeGen(src.toString(),obj.toString(),null,"Lib",null,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        CodeGen code = new CodeGen(null,null,new Ary<>(new String[]{obj.toString()}),
            "Use","return Lib.sum_noInline(arg);",123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertNull(code._compunits.get("Lib/Left")._src);
        assertNull(code._compunits.get("Lib/Right")._src);
        assertEquals("2000000000000",Eval2.eval(code,0));
        assertEquals("2000000000003",Eval2.eval(code,1));
    }

    @Test public void testUnreachableImportIsNotARoot() throws Exception {
        Path root = library("dead-import-");
        Ary<String> paths = new Ary<>(new String[]{root.toString()});
        object(root,paths,"Unused","val run_noInline = { int n -> n+1; };");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(null,root.toString(),paths,"Main","""
                val _false_noInline = { -> false; };
                if( _false_noInline() ) return Unused.run_noInline(arg);
                return 7;
                """,seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            CompUnit unused = code._compunits.get("Unused");
            assertNotNull("Parsing loaded the declaration",unused);
            for( FunNode fun : code._linker )
                assertTrue(fun==null || fun.isDead() || fun._compunit!=unused);
            assertEquals("7",Eval2.eval(code,0));
        }
    }

    private static Path library(String prefix) throws IOException {
        Path base = Files.createDirectories(Path.of("build/objs/selective"));
        return Files.createTempDirectory(base,prefix);
    }

    @Test public void testImportedCallbackChain() throws Exception {
        Path root = library("callback-chain-");
        Ary<String> paths = new Ary<>(new String[]{root.toString()});
        object(root,paths,"L0","val relay_noInline = { {int->int} f -> f; };");
        for( int i = 1; i<6; i++ )
            object(root,paths,"L"+i,"val relay_noInline = { {int->int} f -> L"+(i-1)+".relay_noInline(f); };");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(null,root.toString(),paths,"Main","""
                val _increment_noInline = { int n -> n+1; };
                val _decrement_noInline = { int n -> n-1; };
                val f = L5.relay_noInline(arg ? _increment_noInline : _decrement_noInline);
                return f(41);
                """,seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            assertNotNull(function(code,"relay_noInline").summary());
            assertEquals("42",Eval2.eval(code,1));
            assertEquals("40",Eval2.eval(code,0));
        }
    }

    @Test public void testImportedCallbackReturn() throws Exception {
        Path root = library("callback-return-");
        Ary<String> paths = new Ary<>(new String[]{root.toString()});
        object(root,paths,"Relay","val relay = { {int->int} f -> f; };");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(null,root.toString(),paths,"Main","""
                val _increment_noInline = { int n -> n+1; };
                val f = Relay.relay(_increment_noInline);
                return f(arg);
                """,seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            assertNull(function(code,"relay").summary());
            assertEquals("42",Eval2.eval(code,41));
            assertEquals("1",Eval2.eval(code,0));
        }
    }

    @Test public void testImportedFunctionField() throws Exception {
        Path root = library("function-field-");
        Ary<String> paths = new Ary<>(new String[]{root.toString()});
        object(root,paths,"Targets","var chosen = { int n -> n+1; };");
        object(root,paths,"Apply","val run = { int n -> val f = Targets.chosen; return f(n); };");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(null,root.toString(),paths,"Main",
                "return Apply.run(arg);",seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            assertNull(function(code,"run").summary());
            assertEquals("42",Eval2.eval(code,41));
            assertEquals("1",Eval2.eval(code,0));
        }
    }

    @Test public void testImportedUnknownCallback() throws Exception {
        Path root = library("unknown-callback-");
        Ary<String> paths = new Ary<>(new String[]{root.toString()});
        object(root,paths,"Apply","""
            val apply = { {int->int} f,int n -> f(n); };
            val apply_noInline = { {int->int} f,int n -> f(n); };
            """);
        for( String target : new String[]{"apply","apply_noInline","forward_noInline"} )
            for( long seed : new long[]{1,123,987654321} ) {
                CodeGen code = new CodeGen(null,root.toString(),paths,"Main",
                    "val _increment_noInline = { int n -> n+1; }; "+
                    "val forward_noInline = { {int->int} f,int n -> Apply.apply_noInline(f,n); }; return "+
                    (target.equals("forward_noInline") ? target : "Apply."+target)+"(_increment_noInline,arg);",
                    seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
                assertEquals("42",Eval2.eval(code,41));
                assertEquals("1",Eval2.eval(code,0));
            }
        CodeGen nativeCode = new CodeGen(null,root.toString(),paths,"NativeCallback","""
            val _increment_noInline = { int n -> n+1; };
            val run = { int n -> Apply.apply_noInline(_increment_noInline,n); };
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        Path driver = root.resolve("driver.c");
        Files.writeString(driver,"""
            __attribute__((CALL_CONV)) extern long long run(long long);
            int main(void) { return run(41)==42 && run(0)==1 ? 0 : 1; }
            """);
        String exe = root.resolve(TestC.OS.startsWith("Windows") ? "test.exe" : "test").toString();
        assertEquals("",TestC.gcc(nativeCode,
            TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",driver.toString(),null,exe));
    }

    @Test public void testUnknownCallbackEncoding() {
        for( String cpu : new String[]{"x86_64_v2","arm","riscv"} )
            new CodeGen("""
                val apply = { {int->int} f,int n -> f(n); };
                val apply_noInline = { {int->int} f,int n -> f(n); };
                """)
                .driver(CodeGen.Phase.Encoding,cpu,"SystemV");
    }

    @Test public void testImportedCallbackInitCycle() throws Exception {
        Path root = library("callback-cycle-");
        Path src = Files.createDirectory(root.resolve("src"));
        Ary<String> paths = new Ary<>(new String[]{root.toString()});
        object(root,paths,"Apply","val apply_noInline = { {int->int} f,int n -> f(n); };");
        write(src,"Root","val entry = A.value;");
        write(src,"Root/A","val callback = { int n -> B.value; }; int value = Apply.apply_noInline(callback,1);");
        write(src,"Root/B","int value = A.value;");
        for( long seed : new long[]{1,123,987654321} ) {
            try {
                new CodeGen(src.toString(),root.toString(),paths,"Root",null,seed,true,TypeInteger.BOT)
                    .driver(CodeGen.Phase.TypeCheck);
                fail("The unknown callback can close an initialization cycle");
            } catch( Parser.ParseException ex ) {
                assertTrue(ex.getMessage(),ex.getMessage().startsWith("Cyclic class initialization:"));
                assertTrue(ex.getMessage(),ex.getMessage().contains("Root.A"));
                assertTrue(ex.getMessage(),ex.getMessage().contains("Root.B"));
                assertTrue(ex.getMessage(),ex.getMessage().contains("unknown call"));
            }
        }
    }

    @Test public void testPossibleCycleDoesNotLoadConditionHelper() throws Exception {
        Path base = Path.of("build/objs/selective");
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base,"cycle-");
        Path src = Files.createDirectory(root.resolve("src"));
        Path lib = Files.createDirectory(root.resolve("lib"));
        StringBuilder helper = new StringBuilder("val test={bool n -> ");
        // Large parse, small final body: it will not request speculative loading.
        for( int i = 0; i<80; i++ ) helper.append("val x").append(i).append("=n+0;");
        helper.append("return n;};");
        object(lib,null,"Predicate",helper.toString());
        // All sources and objects agree. The summary cannot express test(n)==n.
        write(src,"Root","val entry=A;");
        write(src,"Root/A","int value=1; if(Predicate.test(false)) value=B.value;");
        write(src,"Root/B","int value=A.value;");
        CodeGen code = new CodeGen(src.toString(),root.toString(),new Ary<>(new String[]{lib.toString()}),
            "Root",null,123,true,TypeInteger.BOT).driver(CodeGen.Phase.FinalizeIR);
        assertNotNull(function(code,"test").summary());
        assertNotNull("Summaries alone report a potential cycle",new ClassInitDependencies(code).check());
        try {
            code.driver(CodeGen.Phase.TypeCheck);
            fail("Reject the possible cycle without loading the condition helper");
        } catch( Parser.ParseException ex ) {
            assertTrue(ex.getMessage(),ex.getMessage().startsWith("Cyclic class initialization:"));
            assertTrue(ex.getMessage(),ex.getMessage().contains("Root.A -> Root.B"));
            assertTrue(ex.getMessage(),ex.getMessage().contains("Root.B -> Root.A"));
        }
        assertNotNull(function(code,"test").summary());
        assertNull(code.getElf(code._compunits.get("Predicate")._obj)._nodes);
    }

    private static void object(Path dir, Ary<String> paths, String name, String source) {
        new CodeGen(null,dir.toString(),paths,name,source,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
    }

    @Test public void testSummariesInliningAndStaticLink() throws Exception {
        Path base = Path.of("build/objs/selective");
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base,"library-");
        Path src = Files.createDirectory(root.resolve("src"));
        Path lib = Files.createDirectory(root.resolve("lib"));
        Path app = Files.createDirectory(root.resolve("app"));
        write(src,"Lib","val api=Api; val work=Work; val unused=Unused;");
        write(src,"Lib/Api","""
            val p={int n -> Work.write_noInline(n);};
            val p_noInline={int n -> Work.write_noInline(n);};
            """);
        write(src,"Lib/Work","val write_noInline={int n -> n+1;};");
        write(src,"Lib/Unused","val f={int n -> n*2;};");
        new CodeGen(src.toString(),lib.toString(),null,"Lib",null,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        for( String unit : new String[]{"Lib","Lib/Api","Lib/Work","Lib/Unused"} )
            assertTrue(unit,Files.isRegularFile(lib.resolve(unit+".o")));

        Ary<String> paths = new Ary<>(new String[]{lib.toString()});
        CodeGen code = new CodeGen(null,app.toString(),paths,"Main",
            "if(arg==0) return -1; return Lib.api.p_noInline(arg);",123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertNotNull(function(code,"Lib.Api.p_noInline").summary());
        assertNotNull(function(code,"Lib.Work.write_noInline").summary());
        assertNotNull(function(code,"Lib.Unused.f").summary());
        assertEquals("-1",Eval2.eval(code,0));
        assertNotNull("An untaken branch does not load its target",function(code,"Lib.Api.p_noInline").summary());
        assertEquals("42",Eval2.eval(code,41));
        assertNull(function(code,"Lib.Work.write_noInline").summary());
        assertNotNull("Evaluation leaves unrelated objects unloaded",function(code,"Lib.Unused.f").summary());
        assertEquals("6",Eval2.eval(code,5));

        code=new CodeGen(null,app.toString(),paths,"Main",
            "val run={int n -> Lib.api.p(n);}; return run(arg);",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertNull("p requested Api's implementation",function(code,"Lib.Api.p").summary());
        assertNotNull("The remaining call needs a signature, not a body",function(code,"Lib.Work.write_noInline").summary());
        assertNotNull("A namespace reexport does not request IR",function(code,"Lib.Unused.f").summary());
        code.driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        Path driver = app.resolve("driver.c");
        Files.writeString(driver,"""
            __attribute__((CALL_CONV)) extern long long run(long long);
            int main(void) { return run(41)==42 ? 0 : 1; }
            """);
        String exe = app.resolve(TestC.OS.startsWith("Windows") ? "test.exe" : "test").toString();
        assertTrue("The compiler already resolved this transitive object",
            TestC.linkObjs(code).contains(lib.resolve("Lib/Work.o").toRealPath().toString()));
        assertEquals("",TestC.gcc(code,
            TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",driver.toString(),null,exe));

        Files.delete(lib.resolve("Lib/Work.o"));
        // Even an untaken branch requires a complete dependency closure for a
        // fresh program.
        try {
            Eval2.eval(new CodeGen(null,app.toString(),paths,"Missing",
                "if(arg==0) return -1; return Lib.api.p_noInline(arg);",123,true,TypeInteger.BOT),0);
            fail("Missing transitive code must fail before evaluation");
        } catch( RuntimeException ex ) { assertTrue(ex.getMessage(),ex.getMessage().contains("Lib/Work")); }
    }

    private static FunNode function(CodeGen code, String name) {
        for( FunNode fun : code._linker ) if( fun!=null && name.equals(fun._name) ) return fun;
        throw new AssertionError("Missing function "+name);
    }

    private static void write(Path src, String unit, String text) throws IOException {
        Path file = src.resolve(unit+".smp");
        Files.createDirectories(file.getParent());
        Files.writeString(file,text);
    }
}
