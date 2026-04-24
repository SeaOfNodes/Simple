package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.ElfReader;
import com.seaofnodes.simple.codegen.ParseAll;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter25Test {


    @Test
    public void testModule0() throws IOException {
        String MODDIR = "src/test/java/com/seaofnodes/simple/test0";
        String BLDDIR = "build/objs/test0";
        String a_obj  = BLDDIR+"/A.o";
        String ab_obj = BLDDIR+"/A/B.o";
        // Remove any prior results so the test runs from scratch.
        delELFiles(new File(BLDDIR));
        writeB(MODDIR,5);

        // Compile MODDIR/A.smp into MODDIR/A.o
        // Since A refers to B also:
        // Compile MODDIR/A/B.smp into MODDIR/A/B.o
        CodeGen code1 = new CodeGen(MODDIR, BLDDIR,null,
                                    "A",null,123L,TypeInteger.BOT);
        code1.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        // Verify produces A.o, A/B.o
        File  a_file = new File( a_obj);
        File ab_file = new File(ab_obj);
        assertTrue(  a_file.exists() );
        assertTrue( ab_file.exists() );
        long  a_msec1 =  a_file.lastModified();
        long ab_msec1 = ab_file.lastModified();

        // Link and execute: arg is true, so compute "5+1" as the exit code
        String rez1 = TestC.gcc("A", 1.2, a_obj, ab_obj);
        assertEquals("exec exit code: 6",rez1);

        // Compile again A, expecting both A.o and A/B.o to be up-to-date and not compiled
        CodeGen code2 = new CodeGen(MODDIR, BLDDIR, null,
                                   "A",null,123L,TypeInteger.BOT);
        code2.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        assertTrue(  a_file.exists() );
        assertTrue( ab_file.exists() );
        long  a_msec2 =  a_file.lastModified();
        long ab_msec2 = ab_file.lastModified();

        assertEquals(  a_msec1,  a_msec2 );
        assertEquals( ab_msec1, ab_msec2 );

        // Touch A.smp and recompile.  A/B.o should not recompile.
        new File(MODDIR+"/A.smp").setLastModified(System.currentTimeMillis());
        CodeGen code3 = new CodeGen(MODDIR, BLDDIR,null,
                                   "A",null,123L,TypeInteger.BOT);
        code3.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        long  a_msec3 =  a_file.lastModified();
        long ab_msec3 = ab_file.lastModified();
        assertTrue  (  a_msec1 < a_msec3 );
        assertEquals( ab_msec1, ab_msec3 );

        // Link and execute: arg is true, so compute "5+1" as the exit code
        String rez3 = TestC.gcc("A", 1.2, a_obj, ab_obj);
        assertEquals("exec exit code: 6",rez3);


        // Modify B.smp and recompile A/B.o; it should recompile and A.o should not.
        writeB(MODDIR,7);
        CodeGen code4 = new CodeGen(MODDIR, BLDDIR,null,
                                   "A/B",null,123L,TypeInteger.BOT);
        code4.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        long  a_msec4 =  a_file.lastModified();
        long ab_msec4 = ab_file.lastModified();
        assertEquals(  a_msec3,   a_msec4 );
        assertTrue  ( ab_msec3 < ab_msec4 );

        // Link and execute: uses stale A.o, so remains '6' not '7+1' == 8
        String rez4 = TestC.gcc("A", 1.2, a_obj, ab_obj);
        assertEquals("exec exit code: 6",rez4);

        // Recompile A.o, it should recompile despite not being touched because
        // it depends on A/B.o which recompiled in the prior step.
        CodeGen code5 = new CodeGen(MODDIR, BLDDIR,null,
                                   "A",null,123L,TypeInteger.BOT);
        code5.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);

        long  a_msec5 =  a_file.lastModified();
        long ab_msec5 = ab_file.lastModified();
        assertTrue  (  a_msec4 < a_msec5 );
        assertEquals( ab_msec4, ab_msec5 );

        // Link and execute: updates A.o from B.o inlining, without compiling B.o
        String rez5 = TestC.gcc("A", 1.2, a_obj, ab_obj);
        assertEquals("exec exit code: 8",rez5);
        // Reset for next time
        writeB(MODDIR,5);
    }

    private void writeB(String MODDIR, int x) throws IOException {
        var bsmp = new FileWriter(MODDIR+"/A/B.smp");
        bsmp.write("val x="+x+";\n");
        bsmp.close();
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
    public void testSys() throws IOException {
        String MODDIR = "src/main/smp";
        String BLDDIR = "build/objs/sys";
        delELFiles(new File(BLDDIR));

        // Compile MODDIR/sys.smp into BLDDIR/sys.o
        // Since sys refers to sys.io also:
        // Compile MODDIR/sys/sys/io.smp into BLDDIR/sys/io.o
        CodeGen code1 = new CodeGen(MODDIR, BLDDIR,null,
                                    "sys",null,123L,TypeInteger.BOT);
        code1.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION);
        // Verify produces sys.o, sys/io.o
        File  sys_file = new File(BLDDIR+"/sys.o" );
        File   io_file = new File(BLDDIR+"/sys/io.o"  );
        File  ary_file = new File(BLDDIR+"/sys/Ary.o" );
        File libc_file = new File(BLDDIR+"/sys/libc.o");
        assertTrue( sys_file.exists() );
        assertTrue(  io_file.exists() );
        assertTrue( ary_file.exists() );
        assertTrue(libc_file.exists() );

        // Can read the ELF files
        ElfReader  sys_elf = ElfReader.load( sys_file, null);
        ElfReader   io_elf = ElfReader.load(  io_file, null);
        ElfReader  ary_elf = ElfReader.load( ary_file, null);
        ElfReader libc_elf = ElfReader.load(libc_file, null);
        sys_elf .loadSimple();
        io_elf  .loadSimple();
        ary_elf .loadSimple();
        libc_elf.loadSimple();

        // Elf files are sane

        // Sys depends on io, libc
        assertEquals(2,sys_elf._deps.length);
        assertSame("sys/io"   ,sys_elf._deps[0]);
        assertSame("sys/libc" ,sys_elf._deps[1]);
        assertSame("class:sys",sys_elf._clz._name);

        // io depends on ary, libc
        assertEquals(2,io_elf._deps.length);
        assertSame("sys/Ary"  ,io_elf._deps[0]);
        assertSame("sys/libc" ,io_elf._deps[1]);
        assertSame("class:sys.io",io_elf._clz._name);

        // ary depends on nothing
        assertEquals(0,ary_elf._deps.length);
        assertSame("class:sys.Ary",ary_elf._clz._name);

        // libc depends on nothing
        assertEquals(0,libc_elf._deps.length);
        assertSame("class:sys.libc",libc_elf._clz._name);

        // Compile helloWorld
        String expected = "Hello, World!";
        String prog = "sys.io.p(\""+expected+"\");";
        TestC.run(prog,"helloWorld",new Ary<>(new String[]{BLDDIR}),
                  TestC.CALL_CONVENTION, null, null, expected,0);
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
