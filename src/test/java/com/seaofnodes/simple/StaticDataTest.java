package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.StaticData;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.StoreNode;
import com.seaofnodes.simple.type.TypeMem;
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
        for( var obj : code._encoding._data.entries() )
            if( obj.symbol.endsWith("Test.$class") ) return obj;
        throw new AssertionError("Missing Test class object");
    }

    // Read the image before <clinit>, then call the function through its data slot.
    @Test public void testEmulatorImage() {
        for( String cpu : new String[]{"arm","riscv"} ) {
            CodeGen code=new CodeGen(SRC).driver(CodeGen.Phase.TypeCheck);
            assertNull("Constant fields fold before instruction selection",
                       code._stop.walk(n -> n instanceof StoreNode st &&
                           (st._name.equals("n") || st._name.equals("f")) ? n : null));
            assertNotNull(code._stop.walk(n -> n instanceof ConstantNode &&
                n._type instanceof TypeMem mem && mem.isConstant() &&
                mem._t==TypeInteger.constant(42) ? n : null));
            assertNotNull("The runtime initialization flag still needs its Store",
                          code._stop.walk(n -> n instanceof StoreNode st &&
                              st._name.equals("$init") ? n : null));
            code.driver(cpu,"SystemV",true,false);
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
        CodeGen code=new CodeGen(null,dir.toString(),"Test",SRC,123L,true,
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

    // Native entries must agree too, both in static fields (before <clinit>)
    // and when code materializes the address. The native body may live above 4G.
    @Test public void testNativeFunctionIdentity() throws Exception {
        Path dir=Path.of("build/objs/static-native-functions");
        Files.createDirectories(dir);
        int[] offsets=new int[2];
        for( int i=0; i<2; i++ ) {
            String name=i==0 ? "A" : "B";
            String src="{ i32 -> i32 } abs = \"C\"; val get"+name+"={ -> abs; }; return 0;";
            CodeGen code=new CodeGen(null,dir.toString(),name,src,123L,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.TypeCheck);
            assertNull("The native address is initialized in the static image",
                       code._stop.walk(n -> n instanceof StoreNode st && st._name.equals("abs") ? n : null));
            code.driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
            for( var obj : code._encoding._data.entries() )
                if( obj.symbol.equals(name+".$class") ) {
                    TypeStruct ts=(TypeStruct)obj.type;
                    offsets[i]=ts.offset(ts.find("abs"));
                }
        }
        Path driver=dir.resolve("driver.c");
        Files.writeString(driver,"""
            #include <stdint.h>
            #include <string.h>
            extern unsigned char a[] __asm__("A.$class"), b[] __asm__("B.$class");
            typedef int32_t (__attribute__((CALL_CONV)) *fun)(int32_t);
            __attribute__((CALL_CONV)) extern fun getA(void), getB(void);
            int main(void) {
                uint32_t x,y;
                memcpy(&x,a+%d,sizeof(x));
                memcpy(&y,b+%d,sizeof(y));
                fun f=(fun)(uintptr_t)x;
                if(!x || x!=y || f!=getA() || f!=getB()) return 1;
                return f(-7)!=7;
            }
            """.formatted(offsets[0],offsets[1]));
        String exe=dir.resolve(TestC.OS.startsWith("Windows")?"test.exe":"test").toString();
        String conv=TestC.CALL_CONVENTION.equals("win64")?"ms_abi":"sysv_abi";
        // Either object's weak definition can win; every reference must agree.
        for( String first : new String[]{"A","B"} ) {
            String second=first.equals("A")?"B":"A";
            TestC.linkExe(dir.resolve(first+".o").toString(),conv,driver.toString(),
                          new Ary<>(new String[]{dir.resolve(second+".o").toString()}),exe);
            assertEquals("",TestC.exec(TestC.TEST_TIMEOUT_SECONDS,exe));
        }
    }

    // Two source files must reference the same mutable class object.
    @Test public void testSharedClass() throws Exception {
        Path dir=Path.of("build/objs/static-data-shared");
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("Main"));
        String[] names={"Globals","A","B"};
        String[] sources={"int count=0;",
                          "val a={ -> Globals; };", "val b={ -> Globals; };"};
        for( int i=0; i<names.length; i++ )
            Files.writeString(dir.resolve("Main/"+names[i]+".smp"),sources[i]);
        new CodeGen(dir.toString(),dir.toString(),"Main","val a={ -> A.a(); }; val b={ -> B.b(); };",123L,true,TypeInteger.BOT)
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
        TestC.linkExe(dir.resolve("Main.o").toString(),
                      TestC.CALL_CONVENTION.equals("win64")?"ms_abi":"sysv_abi",
                      driver.toString(),null,exe);
        assertEquals("",TestC.exec(TestC.TEST_TIMEOUT_SECONDS,exe));
    }
}
