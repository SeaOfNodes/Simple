package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReceiverTest {
    @Test public void classAndInstanceFields() throws Exception {
        String source="""
            int total=1;
            val forward={ -> late(); };
            val early={ -> total; };
            val late={ -> early(); };
            val add_noInline={int n -> self.total=self.total+n; return total;};
            val read_noInline={ -> self.total; };
            val nested={ ->
                val inc={ -> self.total++; };
                inc();
                return total;
            };
            struct Counter {
                int total=100;
                val read=read_noInline;
            };
            struct Writer {
                int count=2;
                val step={ -> self.count++; total=count; return total; };
            };
            u16[] !table=new u16[2];
            val lookup={u8 c -> c<2 ? table[c] : 0;};
            val main={ ->
                total=1;
                table=new u16[2]; table[0]=7; table[1]=9;
                if( forward()!=1 ) return 4;
                if( lookup(1)!=9 || lookup(2)!=0 ) return 5;
                if( add_noInline(5)!=6 || nested()!=7 ) return 1;
                val counter=new Counter;
                if( counter.read()!=7 || counter.total!=100 ) return 2;
                val a=new Writer, b=new Writer;
                if( a.step()!=3 || total!=3 || a.count!=3 || b.count!=2 ) return 3;
                return 0;
            };
            return main();
            """;
        assertEquals("0",Eval2.eval(new CodeGen(source).driver(CodeGen.Phase.TypeCheck),0));
        TestC.runSF(source,"class_instance_receivers","",-1);
    }

    @Test public void importedCallbackKeepsItsClass() throws Exception {
        Path build=Path.of("build/objs/receivers");
        Files.createDirectories(build);
        Path lib=Files.createTempDirectory(build,"classes-");
        new CodeGen(null,lib.toString(),null,"Owner",
            "int value=7; val get_noInline={ -> self.value; };",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        new CodeGen(null,lib.toString(),null,"Forwarder",
            "int value=99; val apply_noInline={ {int} action -> action()+self.value; };",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{lib.toString()});
        String source="return Forwarder.apply_noInline(Owner.get_noInline)==106 ? 0 : 1;";
        CodeGen code=new CodeGen(null,null,paths,"ReceiverClient",source,123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("0",Eval2.eval(code,0));
        TestC.run(source,"imported_receivers",paths,TestC.CALL_CONVENTION,null,null,"",-1);
    }

    @Test public void classReceiverHasNoNativeArgument() throws Exception {
        String source="""
            int base=10;
            val calc={i64 a,i64 b,i64 c,i64 d,i64 e,i64 f,i64 g ->
                self.base+a+2*b+3*c+4*d+5*e+6*f+7*g;
            };
            val callback_noInline={i64 n -> self.base+n;};
            { {i64 -> i64} i64 -> i64 } invoke="C";
            val viaC={i64 n -> invoke(callback_noInline,n);};
            """;
        Path build=Path.of("build/objs/receivers");
        Files.createDirectories(build);
        Path driver=build.resolve("native_receiver.c");
        Files.writeString(driver,"""
            typedef long long (__attribute__((CALL_CONV)) *action_t)(long long);
            __attribute__((CALL_CONV)) long long invoke(action_t action, long long n) { return action(n); }
            __attribute__((CALL_CONV)) extern long long calc(long long,long long,long long,long long,long long,long long,long long);
            __attribute__((CALL_CONV)) extern long long viaC(long long);
            int main(void) { return calc(1,2,3,4,5,6,7)!=150 || viaC(5)!=15; }
            """);
        TestC.run(source,"native_receiver",null,TestC.CALL_CONVENTION,
            TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",driver.toString(),"",-1);
    }
}
