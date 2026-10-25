package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class IncrementalBuildTest {
    @Test public void testTransitiveSourceChange() throws Exception {
        Path base=Path.of("build/objs/incremental");
        Files.createDirectories(base);
        Path root=Files.createTempDirectory(base,"chain-");
        Path src=Files.createDirectory(root.resolve("src"));
        Path obj=Files.createDirectory(root.resolve("obj"));
        write(src,"App","val marker=0;",1000);
        write(src,"App/B","val value={ -> 5; };",1000);
        write(src,"App/A","val value={ -> B.value()+1; };",1000);
        write(src,"App/C","val value={ -> A.value()+1; };",1000);
        write(src,"App/Main","val run={ -> C.value(); }; return run();",1000);
        compile(src,obj,"App");
        stamp(obj.resolve("App.o"),1500);
        compile(src,obj,"App/B");
        stamp(obj.resolve("App/B.o"),2000);
        compile(src,obj,"App/A");
        stamp(obj.resolve("App/A.o"),3000);
        compile(src,obj,"App/C");
        stamp(obj.resolve("App/C.o"),3500);
        CodeGen code=client(src,obj);
        assertNull("Reuse unchanged A",code._compunits.get("App/A")._src);
        assertNull("Reuse unchanged B",code._compunits.get("App/B")._src);
        assertNull("Reuse unchanged C",code._compunits.get("App/C")._src);
        assertEquals("7",Eval2.eval(code,0));

        write(src,"App/B","val value={ -> 7; };",4000);
        code=client(src,obj);
        assertEquals("9",Eval2.eval(code,0));
        assertNotNull(code._compunits.get("App/C")._src);
        assertNotNull("Reparse A before loading its stale inlined body",code._compunits.get("App/A")._src);
        assertNotNull(code._compunits.get("App/B")._src);
        compile(src,obj,"App/Main");
        checkNative(obj,new Ary<>(new String[]{obj.resolve("App.o").toString()}));

        Path clean=Files.createDirectory(root.resolve("clean"));
        assertEquals("9",Eval2.eval(client(src,clean),0));
        compile(src,clean,"App/Main");
        checkNative(clean,null);
    }

    @Test public void testBundledSourceChangeAndCycle() throws Exception {
        Path root=fixture("bundle-");
        Path src=root.resolve("src"), obj=root.resolve("obj");
        write(src,"App/A","val value={int n -> n ? B.value(n-1) : 1; };",1000);
        write(src,"App/B","val value={int n -> n ? A.value(n-1) : 5; };",1000);
        write(src,"App/Main","return A.value(1);",1000);
        compile(src,obj,"App/A");
        stamp(obj.resolve("App/A.o"),3000);
        assertFalse("A owns both sides of the cycle",Files.exists(obj.resolve("App/B.o")));
        CodeGen code=client(src,obj);
        assertEquals("5",Eval2.eval(code,0));
        assertNull(code._compunits.get("App/A")._src);
        assertNull(code._compunits.get("App/B")._src);

        write(src,"App/B","val value={int n -> n ? A.value(n-1) : 7; };",4000);
        code=client(src,obj);
        assertEquals("7",Eval2.eval(code,0));
        assertNotNull(code._compunits.get("App/A")._src);
        assertNotNull(code._compunits.get("App/B")._src);
    }

    @Test public void testMissingDependency() throws Exception {
        Path root=fixture("missing-");
        Path src=root.resolve("src"), obj=root.resolve("obj");
        write(src,"App/A","val value={ -> B.value()+1; };",1000);
        write(src,"App/B","val value={ -> 5; };",1000);
        write(src,"App/Main","return A.value();",1000);
        compile(src,obj,"App/B");
        stamp(obj.resolve("App/B.o"),2000);
        compile(src,obj,"App/A");
        stamp(obj.resolve("App/A.o"),3000);
        Files.delete(obj.resolve("App/B.o"));
        CodeGen code=client(src,obj);
        assertEquals("6",Eval2.eval(code,0));
        assertNotNull(code._compunits.get("App/A")._src);
        assertNotNull(code._compunits.get("App/B")._src);
        Files.delete(src.resolve("App/B.smp"));
        try {
            client(src,obj);
            fail("Missing source and object must not reuse A's inlined body");
        } catch( RuntimeException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("Undefined name 'B'"));
        }
    }

    @Test public void testLibraryDependency() throws Exception {
        Path root=fixture("library-");
        Path src=root.resolve("src"), obj=root.resolve("obj");
        Path lib=Files.createDirectory(root.resolve("lib"));
        var paths=new Ary<>(new String[]{lib.toString(),obj.toString()});
        library(lib,5);
        stamp(lib.resolve("LibB.o"),2000);
        write(src,"App/A","val value={ -> LibB.value()+1; };",1000);
        write(src,"App/Main","return A.value();",1000);
        new CodeGen(src.toString(),obj.toString(),paths,"App/A",null,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        stamp(obj.resolve("App/A.o"),3000);
        CodeGen code=new CodeGen(src.toString(),obj.toString(),paths,"App/Main",null,123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("6",Eval2.eval(code,0));
        assertNull("Unchanged library inputs permit reuse",code._compunits.get("App/A")._src);

        library(lib,7);
        stamp(lib.resolve("LibB.o"),4000);
        code=new CodeGen(src.toString(),obj.toString(),paths,"App/Main",null,123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("8",Eval2.eval(code,0));
        assertNotNull(code._compunits.get("App/A")._src);
    }

    private static void library(Path lib, int value) {
        new CodeGen(null,lib.toString(),null,"LibB","val value={ -> "+value+"; };",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
    }

    private static void checkNative(Path obj, Ary<String> libs) throws Exception {
        Path driver=obj.resolve("driver.c");
        Files.writeString(driver,"""
            __attribute__((CALL_CONV)) extern long long run(void) __asm__("App.Main.run");
            int main(void) { return run()==9 ? 0 : 1; }
            """);
        String exe=obj.resolve(TestC.OS.startsWith("Windows")?"test.exe":"test").toString();
        TestC.linkExe(obj.resolve("App/Main.o").toString(),
            TestC.CALL_CONVENTION.equals("win64")?"ms_abi":"sysv_abi",driver.toString(),libs,exe);
        assertEquals("",TestC.exec(TestC.TEST_TIMEOUT_SECONDS,exe));
    }

    private static Path fixture(String prefix) throws Exception {
        Path base=Path.of("build/objs/incremental");
        Files.createDirectories(base);
        Path root=Files.createTempDirectory(base,prefix);
        Path src=Files.createDirectory(root.resolve("src"));
        Path obj=Files.createDirectory(root.resolve("obj"));
        write(src,"App","val marker=0;",1000);
        compile(src,obj,"App");
        stamp(obj.resolve("App.o"),1500);
        return root;
    }

    private static void write(Path src, String name, String text, long time) throws Exception {
        Path path=src.resolve(name+".smp");
        Files.createDirectories(path.getParent());
        Files.writeString(path,text);
        stamp(path,time);
    }

    private static void stamp(Path path, long time) throws Exception {
        Files.setLastModifiedTime(path,FileTime.fromMillis(time));
    }

    private static void compile(Path src, Path obj, String name) {
        new CodeGen(src.toString(),obj.toString(),null,name,null,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
    }

    private static CodeGen client(Path src, Path obj) {
        return new CodeGen(src.toString(),obj.toString(),null,"App/Main",null,123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
    }
}
