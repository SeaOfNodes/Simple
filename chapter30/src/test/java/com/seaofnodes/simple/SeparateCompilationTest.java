package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class SeparateCompilationTest {
    @Test public void testImportedNativeExposure() throws Exception {
        Path build=Path.of("build/objs/separate");
        Files.createDirectories(build);
        Path lib=Files.createTempDirectory(build,"callbacks-");
        String src="""
            val run={int n ->
                val power={int base,int exponent ->
                    if(exponent<=0) return 1;
                    return base*power(base,exponent-1);
                };
                { {int int -> int} -> int } retain="C";
                retain(power);
                return power(2,n);
            };
            """;
        new CodeGen(null,lib.toString(),null,"Callbacks",src,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        CodeGen code=new CodeGen(null,null,new Ary<>(new String[]{lib.toString()}),
            "Client","return Callbacks.run(arg);",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.Opto);
        // This assertion inspects a body, not just its saved summary.
        ParseAll.loadCompUnit(code,code._compunits.get("Callbacks"));
        assertNull(code._compunits.get("Callbacks")._src);
        ParmNode base=code._start.walk(n -> n instanceof ParmNode p && p._label.equals("base") ? p : null);
        assertNotNull(base);
        assertSame("Native code may call the saved callback with any base",TypeInteger.BOT,base._type);
        code.driver(CodeGen.Phase.TypeCheck);
    }

    // The client root contains no library source. The saved constructor must
    // keep local loop values, and retained calls must use the library's storage.
    @Test public void testImportedConstructorAndState() throws Exception {
        Path fixtures=Path.of("src/test/smp/separate");
        Path build=Path.of("build/objs/separate");
        Files.createDirectories(build);
        Path lib=Files.createTempDirectory(build,"library-");
        Path client=Files.createTempDirectory(build,"client-");
        new CodeGen(fixtures.resolve("library").toString(),lib.toString(),null,
            "Counters",null,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        assertTrue(Files.isRegularFile(lib.resolve("Counters.o")));
        assertFalse(Files.exists(fixtures.resolve("client/Counters.smp")));
        var paths=new Ary<>(new String[]{lib.toString()});
        for( long seed : new long[]{2,13,123} ) {
            CodeGen code=new CodeGen(fixtures.resolve("client").toString(),client.toString(),paths,
                "Main",null,seed,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            CompUnit imported=code._compunits.get("Counters");
            assertNotNull(imported);
            assertNull("Library source must not be parsed",imported._src);
            assertEquals(lib.resolve("Counters.o").toRealPath(),imported._obj.toPath().toRealPath());
            assertEquals("0",Eval2.eval(code,0));
            assertEquals("107",Eval2.eval(code,3));
            assertEquals("131",Eval2.eval(code,5));
        }
        new CodeGen(fixtures.resolve("client").toString(),client.toString(),paths,
            "Main",null,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        Path driver=client.resolve("driver.c");
        Files.writeString(driver,"""
            #include <stdio.h>
            __attribute__((CALL_CONV)) extern long long run(long long);
            int main(void) {
                long long args[]={0,3,5,0,3}, expected[]={0,107,231,0,307};
                for(int i=0;i<5;i++) {
                    long long actual=run(args[i]);
                    if(actual!=expected[i]) {
                        printf("call %d: got %lld, expected %lld\\n",i,actual,expected[i]);
                        return 1;
                    }
                }
                return 0;
            }
            """);
        String exe=client.resolve(TestC.OS.startsWith("Windows")?"test.exe":"test").toString();
        TestC.linkExe(client.resolve("Main.o").toString(),
            TestC.CALL_CONVENTION.equals("win64")?"ms_abi":"sysv_abi",driver.toString(),
            new Ary<>(new String[]{lib.resolve("Counters.o").toString()}),exe);
        assertEquals("",TestC.exec(TestC.TEST_TIMEOUT_SECONDS,exe));
    }
}
