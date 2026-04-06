package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.ParseAll;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class Chapter25Test {


    @Test
    public void testModule0() throws IOException {
        String MODDIR = "src/test/java/com/seaofnodes/simple/test0";
        String BLDDIR = "build/objs/test0";
        String a_obj  = BLDDIR+"/A.o";
        String ab_obj = BLDDIR+"/A/B.o";
        // Remove any prior results so the test runs from scratch.
        delELFiles(new File(BLDDIR));

        // Compile MODDIR/A.smp into MODDIR/A.o
        // Since A refers to B also:
        // Compile MODDIR/A/B.smp into MODDIR/A/B.o
        ParseAll.reset();
        CodeGen code1 = new CodeGen(MODDIR, BLDDIR,null,
                                    "A",null,123L,TypeInteger.BOT);
        code1.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        // Verify produces A.o, A/B.o
        File  a_file = new File( a_obj);
        File ab_file = new File(ab_obj);
        Assert.assertTrue(  a_file.exists() );
        Assert.assertTrue( ab_file.exists() );
        long  a_msec1 =  a_file.lastModified();
        long ab_msec1 = ab_file.lastModified();

        // Link and execute: arg is true, so compute "5+1" as the exit code
        String rez = TestC.gcc("A", 1.2, a_obj, ab_obj);
        Assert.assertEquals("exec exit code: 6",rez);

        // Compile again A, expecting both A.o and A/B.o to be up-to-date and not compiled
        ParseAll.reset();
        CodeGen code2 = new CodeGen(MODDIR, BLDDIR,null,
                                   "A",null,123L,TypeInteger.BOT);
        code2.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        Assert.assertTrue(  a_file.exists() );
        Assert.assertTrue( ab_file.exists() );
        long  a_msec2 =  a_file.lastModified();
        long ab_msec2 = ab_file.lastModified();

        Assert.assertEquals(  a_msec1,  a_msec2 );
        Assert.assertEquals( ab_msec1, ab_msec2 );

        // Touch A.smp and recompile.  A/B.o should not recompile.
        new File(MODDIR+"/A.smp").setLastModified(System.currentTimeMillis());
        ParseAll.reset();
        CodeGen code3 = new CodeGen(MODDIR, BLDDIR,null,
                                   "A",null,123L,TypeInteger.BOT);
        code3.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        Assert.assertTrue(  a_file.exists() );
        Assert.assertTrue( ab_file.exists() );
        long  a_msec3 =  a_file.lastModified();
        long ab_msec3 = ab_file.lastModified();

        Assert.assertTrue(  a_msec1 < a_msec3 );
        Assert.assertEquals( ab_msec1, ab_msec3 );




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
