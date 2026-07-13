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
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class Chapter25Test {

    private static final String SYS_MODDIR = "src/main/smp";
    private static final String SYS_BLDDIR = "build/objs/lib_"+TestC.CPU_ABI;
    private static final String RELEASE_SYS_BLDDIR = TestC.RELEASE_SYS_DIR;


    @Test @Ignore
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


    private File buildTestSys(boolean clean) {
        if( clean )
            delELFiles(new File(SYS_BLDDIR));
        File sys_file = new File(SYS_BLDDIR+"/sys.o" );
        if( sys_file.exists() )
            return sys_file;

        // Compile SYS_MODDIR/sys.smp into SYS_BLDDIR/sys.o.  Chapter25
        // compiler tests intentionally depend on this freshly built sys.o.
        CodeGen code1 = new CodeGen(SYS_MODDIR, SYS_BLDDIR,null,
                                    "sys",null,123L,TypeInteger.BOT);
        code1.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false,0);
        assertTrue( sys_file.exists() );
        return sys_file;
    }

    @Test
    public void testSys() {
        delELFiles(new File(SYS_BLDDIR));

        CodeGen code1 = new CodeGen(SYS_MODDIR, SYS_BLDDIR,null,
                                    "sys",null,123L,TypeInteger.BOT);
        code1.driver(CodeGen.Phase.Export,TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false,0);

        // Verify produces sys.o, sys/io.o
        File sys_file = new File(SYS_BLDDIR+"/sys.o" );
        assertTrue( sys_file.exists() );

        // Can read the ELF files
        ElfReader sys_elf = ElfReader.load( sys_file, null);
        sys_elf.loadSimple(code1);

        // Elf files are sane

        // Sys depends on io, libc, aryi64, aryu8
        assertEquals(6,sys_elf._deps.length);
        assertSame("sys/aryu8" ,sys_elf._deps[0]);
        assertSame("sys/io"    ,sys_elf._deps[1]);
        assertSame("sys/aryi64",sys_elf._deps[2]);
        assertSame("sys/Ary"   ,sys_elf._deps[3]);
        assertSame("sys/Scan"  ,sys_elf._deps[4]);
        assertSame("sys/libc"  ,sys_elf._deps[5]);
        assertSame("class:sys" ,sys_elf._clz._name);
    }

    @Test
    public void testHelloWorld() throws IOException {
        File sys_file = buildTestSys(false);
        assertTrue("Missing "+sys_file+"; testHelloWorld depends on testSys building it",  sys_file.exists());

        String expected = "Hello, World!";
        String prog = "return sys.io.p(\""+expected+"\") - "+expected.length()+";";
        TestC.run(prog,"helloWorld",new Ary<>(new String[]{SYS_BLDDIR}),
                  TestC.CALL_CONVENTION, null, null, expected,0);
    }

    @Test
    public void testHelloWorldDriver() throws Exception {
        File sys_file = new File(RELEASE_SYS_BLDDIR+"/sys.o");
        assertTrue("Missing "+sys_file+"; run make release or make tests_sys first",  sys_file.exists());
        Simple.main(new String[]{"-L",RELEASE_SYS_BLDDIR,"--norun","docs/examples/A_helloWorld.smp"});
    }

    @Test
    public void testHelloWorldDriverLibFile() throws Exception {
        File sys_file = new File(RELEASE_SYS_BLDDIR+"/sys.o");
        assertTrue("Missing "+sys_file+"; run make release or make tests_sys first",  sys_file.exists());
        Simple.main(new String[]{"-L",sys_file.toString(),"docs/examples/A_helloWorld.smp"});
    }

    @Test
    public void testHelloWorldNoInline() throws Exception {
        File sys_file = buildTestSys(false);
        assertTrue("Missing "+sys_file+"; testHelloWorldNoInline depends on testSys building it",  sys_file.exists());

        String base = "helloWorldNoInline";
        String expected = "Hello, World!";
        String prog = "return sys.io.p_noInline(\""+expected+"\") - "+expected.length()+";";
        CodeGen code = new CodeGen(null,"build/objs",new Ary<>(new String[]{SYS_BLDDIR}),
                                   base,prog,123L,TypeInteger.BOT);
        code.driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,true);

        String obj = "build/objs/"+base+".o";
        String exe = "build/objs/"+base+(TestC.OS.startsWith("Windows") ? ".exe" : "");
        String syms = run(new String[]{"nm",obj});
        assertTrue(syms, syms.contains(" U sys.io.p_noInline"));

        String out = run(new String[]{"gcc",obj,sys_file.toString(),"-lm","-g","-o",exe});
        assertEquals("",out);
        String rez = run(new String[]{exe});
        assertEquals(expected,rez);
    }

    private static String run(String[] cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean normal = p.waitFor(5, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(out, normal);
        assertEquals(out,0,p.exitValue());
        return out;
    }

    @Test
    public void testRedirectedRead() throws IOException {
        File sys_file = buildTestSys(false);
        assertTrue("Missing "+sys_file+"; testRedirectedRead depends on testSys building it",  sys_file.exists());
        String src = """
u8[] buf = new u8[10];
i64 ptr = buf;
int rez = sys.libc.read(0,ptr,buf#);
return  rez < buf# ? 0 : sys.libc._exit(-2);
""";
        TestC.run(src,"redirectedRead",new Ary<>(new String[]{SYS_BLDDIR}),
                  TestC.CALL_CONVENTION, null, null,
                  "abc", "", -1);
    }

    @Test
    public void testBubbles() throws IOException {
        File sys_file = buildTestSys(false);
        assertTrue("Missing "+sys_file+"; testBubbles depends on testSys building it",  sys_file.exists());
        String src = Files.readString( Path.of("docs/examples/BubbleSort.smp"));
        TestC.run(src,"BubbleSort",new Ary<>(new String[]{SYS_BLDDIR}),
                  TestC.CALL_CONVENTION, null, null,
                  "[3,  2,-17, 999 ] ", "[-17, 2, 3, 999]", -1);
    }

}
