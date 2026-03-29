package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class Chapter25Test {


    @Test
    public void testModule0() {
        String MODDIR = "src/test/java/com/seaofnodes/simple/test0";
        // Remove any prior results so the test runs from scratch.
        delELFiles(new File(MODDIR));

        // Compile MODDIR/A.smp into MODDIR/A.o
        // Since A refers to B also:
        // Compile MODDIR/A/B.smp into MODDIR/A/B.o
        CodeGen code = new CodeGen(MODDIR, MODDIR,null,
                                   "A",null,123L,TypeInteger.BOT);
        code.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        // Verify produces A.o, A/B.o
        Assert.assertTrue( new File(MODDIR+"/A.o").exists() );
        Assert.assertTrue( new File(MODDIR+"/A/B.o").exists() );

        // Parent depends on existence of child1,2,3 - not contents.
        // Child1 depends on contents parent.
        // Child2 depends on contents parent(+child0), child1.
        // Child3 depends on existance parent.
        throw Utils.TODO();

    }


    @Test
    public void testModule5() {
        String MODDIR = "src/test/java/com/seaofnodes/simple/test5";
        // Remove any prior failed compiles
        delELFiles(new File(MODDIR));

        // Compile modtest/par.o
        CodeGen code = new CodeGen(MODDIR, MODDIR,new Ary<>(String.class),
                                   "par",null,123L,TypeInteger.BOT);
        code.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        // Verify produces par.o, par/child{0-3}.o

        // Parent depends on existence of child1,2,3 - not contents.
        // Child1 depends on contents parent.
        // Child2 depends on contents parent(+child0), child1.
        // Child3 depends on existance parent.
        throw Utils.TODO();

    }

    // Recursive search (TODO: gzip, archives) and delete all .o files
    private void delELFiles( File dir) {
        if( dir.isDirectory() )
            for( File f : dir.listFiles() )
                delELFiles(f);
        else if( dir.getName().endsWith(".o") )
            dir.delete();
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
