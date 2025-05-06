package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrefixUpdateTest {
    @Test public void testMethodFieldWrites() {
        String[] fields = {"int x=0", "int x=3", "u8 x=0", "f32 x=0"};
        String[] updates = {"++x", "--x", "++x", "++x"};
        String[] results = {"221", "112", "221", "221.0"};
        for(int i=0;i<fields.length;i++)
            for(int seed:new int[]{2,13,123}) {
                String src="struct C { "+fields[i]+"; val change_noInline={ -> "+updates[i]+"; }; }; "
                    +"var c=new C; val a=c.change_noInline(); val b=c.change_noInline(); return a+10*b+100*c.x;";
                CodeGen code=new CodeGen(src,TypeInteger.BOT,seed,true).driver(CodeGen.Phase.TypeCheck);
                assertEquals(fields[i],results[i],Eval2.eval(code,0));
            }
    }

    @Test public void testLocalAndFinalUpdates() {
        CodeGen code=new CodeGen("int x=arg; return ++x*100 + --x*10 + x;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("433",Eval2.eval(code,3));
        code=new CodeGen("struct C { int x=7; val f={int x -> ++x;}; }; var c=new C; return c.f(3)+10*c.x;")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("74",Eval2.eval(code,0));
        for(String src:new String[]{"val x=1; return ++x;",
                                   "struct C { val x=1; val f={ -> ++x; }; }; var c=new C; return c.f();"}) {
            try {
                new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
                fail("final update");
            } catch(RuntimeException e) {
                assertTrue(e.getMessage(),e.getMessage().contains("final"));
            }
        }
    }

    @Test public void testNativeByteOverflow() throws java.io.IOException {
        String src="val prefixUpdate={int arg -> "
            +"struct C { u8 x=255; val inc_noInline={ -> ++x; }; }; "
            +"var c=new C; val a=c.inc_noInline(); val b=c.inc_noInline(); return a+10*b+100*c.x; };";
        var dir=java.nio.file.Path.of("build/objs");
        java.nio.file.Files.createDirectories(dir);
        var driver=dir.resolve("prefixUpdate.c");
        java.nio.file.Files.writeString(driver,"""
            __attribute__((CALL_CONV)) extern long long prefixUpdate(long long);
            int main(void) { return prefixUpdate(0)!=110; }
            """);
        String abi=TestC.OS.startsWith("Windows") ? "ms_abi" : "sysv_abi";
        TestC.run(src,TestC.CALL_CONVENTION,null,abi,driver.toString(),"build/objs/prefixUpdate","S","",-1);
    }
}
