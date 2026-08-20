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

    private static final String SYS_BLDDIR = "build/objs/lib_"+TestC.CPU_ABI;
    private static final File SYS_FILE = new File(SYS_BLDDIR+"/sys.o");

    @Test
    public void testForwardConstructor() {
        CodeGen code = new CodeGen("src/test/java/com/seaofnodes/simple/test_smp/forward_ctor",
                                   "build/objs/forward_ctor_parse",null,
                                   "m",null,123L,TypeInteger.BOT);
        code.driver(CodeGen.Phase.TypeCheck);
    }


    @Test
    public void testPostfixFieldUpdate() {
        String src = """
            struct V {
                u32 !len = 0;
                i64[] !buf;
                new V = { i64[] b -> buf=b; };
                val grow = { i64 n -> self; };
                val add = { i64 x -> grow(1).buf[len++] = x; self; };
            };
            return new V(new i64[1]).add(7).len;
            """;
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("1",Eval2.eval(code,0));
    }

    @Test
    public void testStringEscapes() {
        String src = "u8[~] s=\"A\\n\\t\\r\\\\\\\"B\"; " +
            "return s#==7 && s[0]=='A' && s[1]==10 && s[2]==9 && s[3]==13 && " +
            "s[4]==92 && s[5]==34 && s[6]=='B';";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("1",Eval2.eval(code,0));
    }

    @Test
    public void testCharacterEscapes() {
        CodeGen code = new CodeGen("return '\\n'==10 && '\\t'==9 && '\\r'==13;")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("1",Eval2.eval(code,0));
    }


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


    @Test
    public void testSys() {
        assertTrue("Missing "+SYS_FILE+"; run make tests_sys", SYS_FILE.exists());

        // Can read the ELF files
        CodeGen code1 = new CodeGen("return 0;");
        ElfReader sys_elf = ElfReader.load(SYS_FILE, null);
        sys_elf.loadPublicTypes(code1);

        // Elf files are sane

        // Sys depends on io, libc, char, collections, and array utilities.
        assertEquals(10,sys_elf._deps.length);
        assertSame("sys/aryu8" ,sys_elf._deps[0]);
        assertSame("sys/char"  ,sys_elf._deps[1]);
        assertSame("sys/io"    ,sys_elf._deps[2]);
        assertSame("sys/ary"   ,sys_elf._deps[3]);
        assertSame("sys/aryi64",sys_elf._deps[4]);
        assertSame("sys/adt/bitset",sys_elf._deps[5]);
        assertSame("sys",       sys_elf._deps[6]);
        assertSame("sys/scan"  ,sys_elf._deps[7]);
        assertSame("sys/adt"   ,sys_elf._deps[8]);
        assertSame("sys/libc"  ,sys_elf._deps[9]);
        assertSame("class:sys" ,sys_elf._clz._name);
    }

    @Test
    public void testHelloWorld() throws IOException {
        String expected = "Hello, World!\n";
        String prog = "return sys.io.p(\""+expected+"\") - "+expected.length()+";";
        TestC.run(prog,"helloWorld",new Ary<>(new String[]{SYS_BLDDIR}),
                  TestC.CALL_CONVENTION, null, null, expected,0);
    }

    @Test
    public void testHelloWorldDriver() throws Exception {
        Simple.main(new String[]{"-L",SYS_BLDDIR,"--norun","docs/examples/A_helloWorld.smp"});
    }

    @Test
    public void testHelloWorldDriverLibFile() throws Exception {
        Simple.main(new String[]{"-L",SYS_FILE.toString(),"docs/examples/A_helloWorld.smp"});
    }

    @Test
    public void testHelloWorldNoInline() throws Exception {
        String base = "helloWorldNoInline";
        String expected = "Hello, World!\n";
        String prog = "return sys.io.p_noInline(\""+expected+"\") - "+expected.length()+";";
        CodeGen code = new CodeGen(null,"build/objs",new Ary<>(new String[]{SYS_BLDDIR}),
                                   base,prog,123L,TypeInteger.BOT);
        code.driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,true);

        String obj = "build/objs/"+base+".o";
        String exe = "build/objs/"+base+(TestC.OS.startsWith("Windows") ? ".exe" : "");
        String syms = run(new String[]{"nm",obj});
        assertTrue(syms, syms.contains(" U sys.io.p_noInline"));

        String out = run(new String[]{"gcc",obj,SYS_FILE.toString(),TestC.runtimeObject(),"-lm","-g","-o",exe});
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
        String src = Files.readString( Path.of("docs/examples/BubbleSort.smp"));
        String exe = TestC.compile(src,"BubbleSort",new Ary<>(new String[]{SYS_BLDDIR}),
                                   TestC.CALL_CONVENTION,null,null,-1);
        assertEquals("[-17, 2, 3, 999]\n",
                     TestC.exec(exe,"[3,  2,-17, 999 ]"));
        assertEquals("[1, 2, 3, 4, 4, 5]\n",
                     TestC.exec(exe,"[4, 5, 3, 1, 4, 2]"));
        assertEquals("[1, 2, 3, 4, 5]\n",
                     TestC.exec(exe,"[1, 2, 3, 4, 5]"));
        assertEquals("[1, 2, 3, 4, 5, 6, 7, 8, 9]\n",
                     TestC.exec(exe,"[9, 8, 7, 6, 5, 4, 3, 2, 1]"));

        String usage = "Usage: please provide a list of at least two integers to sort in the format \"[1, 2, 3, 4, 5]\"\n";
        assertEquals(usage,TestC.exec(exe));
        assertEquals(usage,TestC.exec(exe,""));
        assertEquals(usage,TestC.exec(exe,"[1]"));
        assertEquals(usage,TestC.exec(exe,"[4 5 3]"));
    }

    @Test
    public void testCapitalize() throws IOException {
        String src = Files.readString(Path.of("docs/examples/Capitalize.smp"));
        String exe = TestC.compile(src,"Capitalize",new Ary<>(new String[]{SYS_BLDDIR}),
                                   TestC.CALL_CONVENTION,null,null,-1);
        assertEquals("Hello world\n",TestC.exec(exe,"hello world"));
        assertEquals("Hello World\n",TestC.exec(exe,"Hello World"));
        assertEquals("123 apples\n",TestC.exec(exe,"123 apples"));
        assertEquals("Usage: please provide a string\n",TestC.exec(exe));
        assertEquals("Usage: please provide a string\n",TestC.exec(exe,""));
        assertEquals("Use quotes around multiple strings.\n",TestC.exec(exe,"hello","world"));
    }

    @Test
    public void testDijkstra() throws IOException {
        String src = Files.readString(Path.of("docs/examples/Dijkstra.smp"));
        String matrix = "[0, 2, 0, 6, 0, 2, 0, 3, 8, 5, 0, 3, 0, 0, 7, 6, 8, 0, 0, 9, 0, 5, 7, 9, 0]";
        String exe = TestC.compile(src,"Dijkstra",new Ary<>(new String[]{SYS_BLDDIR}),
                                   TestC.CALL_CONVENTION,null,null,-1);
        assertEquals("2\n",TestC.exec(exe,matrix,"0","1"));
        String usage = "Usage: please provide three inputs: a serialized matrix, a source node and a destination node\n";
        assertEquals("7\n",TestC.exec(exe,matrix,"0","4"));
        assertEquals(usage,TestC.exec(exe));
        assertEquals(usage,TestC.exec(exe,"","",""));
        assertEquals(usage,TestC.exec(exe,"[1, 0, 3, 0, 5, 1]","1","2"));
        assertEquals(usage,TestC.exec(exe,"[0, 0, 0, 0]","0","1"));
    }

    @Test
    public void testFileIO() throws IOException {
        String src = Files.readString(Path.of("docs/examples/FileIO.smp"));
        String exe = TestC.compile(src,"FileIO",new Ary<>(new String[]{SYS_BLDDIR}),
                                   TestC.CALL_CONVENTION,null,null,-1);
        assertEquals("File I/O succeeded\n",TestC.exec(exe));
    }
}
