package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;

import org.junit.Ignore;
import org.junit.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Chapter25Test {


    @Test
    public void testModules() throws IOException {
        String MODDIR = "src/test/java/com/seaofnodes/simple/modtest";

        // Remove any prior failed compiles
        // DEL modtest .o recursively

        // Compile modtest/par.o
        String src = Files.readString( Path.of(MODDIR+"/par.smp"));
        CodeGen code = new CodeGen(MODDIR, MODDIR,
                                   new Ary<>(String.class),"par",src,123L,TypeInteger.BOT);
        code.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        // Verify produces par.o, par/child{0-3}.o

        // Parent depends on existence of child1,2,3 - not contents.
        // Child1 depends on contents parent.
        // Child2 depends on contents parent(+child0), child1.
        // Child3 depends on existance parent.


    }


    @Test
    public void testHelloWorld() throws IOException {
        //// Produce lib/sys.o
        //CodeGen code = new CodeGen(com.seaofnodes.simple.sys.SYS).
        //    driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,"lib/sys.o",false);

        TestC.runSYS("sys.io.p(\"Hello, World!\");","helloWorld", "Hello, World!",0);
    }

    @Test @Ignore
    public void testBubbles() throws IOException {
        String src = Files.readString( Path.of("docs/examples/BubbleSort.smp"));
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
    }

}
