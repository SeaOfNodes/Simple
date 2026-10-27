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
    @Test public void testCycleRefinementLoadsConditionHelper() throws Exception {
        Path base = Path.of("build/objs/selective");
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base,"cycle-");
        Path stubs = Files.createDirectory(root.resolve("stubs"));
        Path lib = Files.createDirectory(root.resolve("lib"));
        object(stubs,null,"A","int value=arg;");
        object(stubs,null,"B","int value=arg;");
        StringBuilder helper = new StringBuilder("val test={bool n -> ");
        // Large parse, small final body: it will not request speculative loading.
        for( int i = 0; i<80; i++ ) helper.append("val x").append(i).append("=n+0;");
        helper.append("return n;};");
        object(lib,null,"Predicate",helper.toString());
        Ary<String> inputs = new Ary<>(new String[]{stubs.toString(),lib.toString()});
        object(lib,inputs,"A","int value=1; if(Predicate.test(false)) value=B.value;");
        object(lib,inputs,"B","int value=A.value;");
        CodeGen code = new CodeGen(null,root.toString(),new Ary<>(new String[]{lib.toString()}),
            "Main","return A.value;",123,true,TypeInteger.BOT).driver(CodeGen.Phase.FinalizeIR);
        assertNotNull(function(code,"test").summary());
        assertNotNull("Summaries alone report a potential cycle",new ClassInitDependencies(code).check());
        code.driver(CodeGen.Phase.TypeCheck);
        assertNull("The helper is outside A/B's apparent cycle",function(code,"test").summary());
        assertEquals("1",Eval2.eval(code,0));
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
