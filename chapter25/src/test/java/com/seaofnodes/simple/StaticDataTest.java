package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.StaticData;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.isa.eval.EvalArm64;
import com.seaofnodes.isa.eval.EvalRisc5;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticDataTest {
    private static final String SRC="val n=42; val f={int x -> x+n;}; return 0;";

    private static StaticData.ObjectData root(CodeGen code) {
        return code._encoding._data.entries().stream()
            .filter(obj -> obj.symbol.endsWith("Test.$class")).findFirst().orElseThrow();
    }

    // Read the image before <clinit>, then call the function through its data slot.
    @Test public void testEmulatorImage() {
        for( String cpu : new String[]{"arm","riscv"} ) {
            CodeGen code=new CodeGen(SRC).driver(cpu,"SystemV",true,false);
            var e=code._encoding;
            var obj=root(code);
            TypeStruct ts=(TypeStruct)obj.type;
            int cp=(e._bits.size()+15)&-16, sd=(cp+e._cpool.size()+15)&-16;
            byte[] image=new byte[1<<20];
            System.arraycopy(e._bits.buf(),0,image,0,e._bits.size());
            System.arraycopy(e._cpool.buf(),0,image,cp,e._cpool.size());
            System.arraycopy(e._sdata.buf(),0,image,sd,e._sdata.size());
            int n=sd+obj.offset+ts.offset(ts.find("n"));
            int f=sd+obj.offset+ts.offset(ts.find("f"));
            if( cpu.equals("arm") ) {
                var arm=new EvalArm64(image,1<<16);
                assertEquals(42,arm.ld4s(n));
                arm._pc=arm.ld4s(f);
                arm.regs[0]=7;
                assertEquals(0,arm.step(1000));
                assertEquals(49,arm.regs[0]);
            } else {
                var r5=new EvalRisc5(image,1<<16);
                assertEquals(42,r5.ld4s(n));
                r5._pc=r5.ld4s(f);
                r5.regs[10]=7;
                assertEquals(0,r5.step(1000));
                assertEquals(49,r5.regs[10]);
            }
        }
    }

    @Test public void testNativeImage() throws Exception {
        Path dir=Path.of("build/objs/static-data");
        Files.createDirectories(dir);
        CodeGen code=new CodeGen(null,dir.toString(),null,"Test",SRC,123L,true,
                                TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        TypeStruct ts=(TypeStruct)root(code).type;
        Path driver=dir.resolve("driver.c");
        Files.writeString(driver,"""
            #include <stdint.h>
            #include <string.h>
            extern unsigned char data[] __asm__("Test.$class");
            typedef int64_t (__attribute__((CALL_CONV)) *fun)(int64_t);
            int main(void) {
                int64_t n; uint32_t f;
                memcpy(&n,data+%d,sizeof(n));
                memcpy(&f,data+%d,sizeof(f));
                return n!=42 || ((fun)(uintptr_t)f)(7)!=49;
            }
            """.formatted(ts.offset(ts.find("n")),ts.offset(ts.find("f"))));
        String exe=dir.resolve(TestC.OS.startsWith("Windows")?"test.exe":"test").toString();
        TestC.linkExe(dir.resolve("Test.o").toString(),
                      TestC.CALL_CONVENTION.equals("win64")?"ms_abi":"sysv_abi",
                      driver.toString(),null,exe);
        assertEquals("",TestC.exec(TestC.TEST_TIMEOUT_SECONDS,exe));
    }

    // Two separately compiled users must reference the same mutable class object.
    @Test public void testSharedClass() throws Exception {
        Path dir=Path.of("build/objs/static-data-shared");
        Files.createDirectories(dir);
        Path lib=dir.resolve("lib");
        Files.createDirectories(lib);
        var paths=new Ary<>(new String[]{lib.toString()});
        String[] names={"Globals","A","B"};
        String[] sources={"int count=0;",
                          "val a={ -> Globals; };", "val b={ -> Globals; };"};
        for( int i=0; i<names.length; i++ )
            new CodeGen(null,(i==0?lib:dir).toString(),i==0?null:paths,names[i],sources[i],123L,true,TypeInteger.BOT)
                .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        Path driver=dir.resolve("driver.c");
        Files.writeString(driver,"""
            #include <stdint.h>
            __attribute__((CALL_CONV)) extern int64_t *a(void),*b(void);
            int main(void) {
                int64_t *x=a(),*y=b();
                if(x!=y) return 1;
                *x=37;
                return *y!=37;
            }
            """);
        String exe=dir.resolve(TestC.OS.startsWith("Windows")?"test.exe":"test").toString();
        TestC.linkExe(lib.resolve("Globals.o").toString(),
                      TestC.CALL_CONVENTION.equals("win64")?"ms_abi":"sysv_abi",
                      driver.toString(),new Ary<>(new String[]{dir+"/A.o",dir+"/B.o"}),exe);
        assertEquals("",TestC.exec(TestC.TEST_TIMEOUT_SECONDS,exe));
    }
}
