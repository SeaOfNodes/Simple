package com.seaofnodes.simple;

import com.seaofnodes.isa.eval.EvalRisc5;

import com.seaofnodes.isa.eval.EvalArm64;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.codegen.ParseAll;
import com.seaofnodes.simple.node.CallNode;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.LoopNode;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class ModulesTest {

    @Test public void testFlagSpills() throws IOException {
        com.seaofnodes.simple.codegen.FlagSpillTestSupport.run();
    }

    @Ignore
    @Test
    public void testJig() {
        String src = "{ i32 -> i32 } close = \"C\"; val main = { u8[] a -> if (a# > 1) return close(0);  return a[0]; };";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Encoding,"riscv","SystemV");
        assertEquals("return (arg+3);", code.print());
        assertEquals("3",Eval2.eval(code,0));
    }

    @Test public void testConditionalClassInitX86() throws IOException {
        Path build=Path.of("build/objs");
        Files.createDirectories(build);
        Path dir=Files.createTempDirectory(build,"init_flags_");
        Files.createDirectories(dir.resolve("Main"));
        Files.writeString(dir.resolve("Main/B.smp"),"int value=17+arg;");
        // GCM can hoist the fused B.$init compare above the test of take.
        // Its flags then cross another flag definition and need a legal split.
        new CheckedCodeGen(dir.toString(),dir.toString(),
            "Main","val run={int take -> return take ? B.value : 0; };",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.Encoding,"x86_64_v2","win64");
        // Remove successful fixtures; preserve the object if compilation fails.
        Files.delete(dir.resolve("Main/B.smp"));
        Files.delete(dir.resolve("Main"));
        Files.delete(dir);
    }

    @Test public void testPublicInstanceMethod() {
        String src = """
            struct Scan {
                val visible = { u8 c -> c==255; };
                val _hidden = { u8 c -> c==1; };
            };
            struct _Private {
                val hiddenClassMethod = { u8 c -> c==2; };
            };
            return new Scan.visible(44);
            """;
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(src,seed,true).driver(CodeGen.Phase.Parse);
            FunNode visible = null;
            int checked = 0;
            for( FunNode fun : code._linker ) {
                if( fun==null || fun._name==null ) continue;
                if( fun._name.equals("visible") ) {
                    assertTrue(code.publicFIDX(fun.sig().fidx()));
                    visible = fun;
                    checked++;
                }
                if( fun._name.equals("_hidden") || fun._name.equals("hiddenClassMethod") ) {
                    assertFalse(code.publicFIDX(fun.sig().fidx()));
                    checked++;
                }
            }
            assertEquals(3,checked);
            code.driver(CodeGen.Phase.TypeCheck);
            assertTrue(visible.unknownCallers());
            assertEquals(TypeInteger.U8,visible.parm(3)._type);
            assertEquals("0",Eval2.eval(code,0));
        }
    }

    @Test public void testNeverReturnPointerRiscV() {
        new CodeGen("""
            struct _S { int x; };
            !_S !s = new _S;
            if(arg) while(1) { s.x += arg; }
            return s;
            """).driver(CodeGen.Phase.Encoding,"riscv","SystemV");
    }

    @Test public void testInlineNeverExit() {
        String[] sources={
            "val _spin={ -> while(1) {} return 1; }; _spin(); return 2;",
            // Two callers require cloning, including the non-exiting arm.
            "val _spin={int x -> if(x) while(1) {} return 7; }; return _spin(arg)+_spin(arg);"
        };
        for( int i=0; i<sources.length; i++ ) {
            for( long seed : new long[]{1,123,987654321} ) {
                CodeGen code=new CodeGen(sources[i],seed,true).driver(CodeGen.Phase.TypeCheck);
                assertNull("Calls should inline",code._start.walk(n -> n instanceof CallNode ? n : null));
                assertNotNull("The non-exiting path must survive",code._start.walk(n -> n instanceof LoopNode && n!=code._start ? n : null));
                if( i==1 ) assertEquals("14",Eval2.eval(code,0,10));
                assertNull(Eval2.eval(code,1,10));
            }
            for( String cpu : new String[]{"x86_64_v2","arm","riscv"} )
                new CheckedCodeGen(sources[i]).driver(CodeGen.Phase.Encoding,cpu,"SystemV");
        }
    }

    @Test
    public void testExternData() throws IOException {
        String src = """
            i32 counter="C";
            i8 small="C";
            f64 fraction="C";
            {int} bump="C";
            val test_global={ ->
                val before=counter;
                counter=before;
                counter=++counter+1;
                counter+=1;
                val mid=bump();
                small++;
                fraction=fraction+0.5;
                return before*10000+mid*100+counter;
            };
            """;
        TestC.runC(src,"extern_data","",-1);
    }

    @Test
    public void testExternDataEncoding() {
        for( String cpu : new String[]{"arm","riscv"} ) {
            CodeGen code = new CheckedCodeGen("i32 counter=\"C\"; counter=counter+3; return counter;");
            // Exercise signed low-12 relocation arithmetic, past the first page.
            int addr = 0x3800;
            code._externDataAddresses.put("counter",addr);
            code.driver(cpu,"SystemV",true,false);
            byte[] image = new byte[1<<20];
            var bits = code._encoding._bits;
            System.arraycopy(bits.buf(),0,image,0,bits.size());
            if( cpu.equals("arm") ) {
                EvalArm64 arm = new EvalArm64(image,1<<16);
                arm.st4(addr,-7);
                assertEquals(0,arm.step(1000));
                assertEquals(-4,arm.regs[0]);
                assertEquals(-4,arm.ld4s(addr));
            } else {
                EvalRisc5 r5 = new EvalRisc5(image,1<<16);
                r5.st4(addr,-7);
                assertEquals(0,r5.step(1000));
                assertEquals(-4,r5.regs[10]);
                assertEquals(-4,r5.ld4s(addr));
            }
        }
    }


    @Test public void testPrintingConstantPool() throws Exception {
        com.seaofnodes.simple.codegen.PrintRegTestSupport.checkConstantPool();
    }


    @Test
    public void testForwardConstructor() {
        CodeGen code = new CodeGen("src/test/java/com/seaofnodes/simple/test_smp/forward_ctor",
                                   "build/objs/forward_ctor_parse",
                                   "m",null,123L,true,TypeInteger.BOT);
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

    @Test
    public void testFuzzerUnresolvedNumericLoop() {
        CodeGen code = new CodeGen("""
            arg=0;
            while(arg+0)
                while(-arg)
                    arg=0;
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("return 0;",code.print());
    }

    @Test
    public void testFuzzerBadLCA() {
        new CodeGen("""
            int v0=arg;
            if(v0&&--v0?0:arg) {}
            """).driver(CodeGen.Phase.LocalSched);
    }


    // Recursive search (TODO: gzip, archives) and delete all .o files


    @Test
    public void testConstantClassInitialization() throws IOException {
        TestC.runSF("val x = 42; return 0;","constantClassInt","",0);
        TestC.runSF("val f = { -> 7; }; return 0;","constantClassFunction","",0);
    }


}
