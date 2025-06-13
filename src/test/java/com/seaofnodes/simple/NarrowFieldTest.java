package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.isa.eval.EvalRisc5;
import org.junit.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class NarrowFieldTest {
    private static final String[] TYPES = {"u8", "i8", "u16", "i16", "u32", "i32"};
    private static final long[] MINS = {0, -128, 0, -32768, 0, Integer.MIN_VALUE};
    private static final long[] MAXS = {255, 127, 65535, 32767, 0xffffffffL, Integer.MAX_VALUE};

    // Both the method result and a later read must see the stored, narrowed value.
    private static String source(int i, boolean increment, boolean argument) {
        String name = "C"+i+(increment ? "Inc" : "Dec");
        long initial = argument ? 0 : (increment ? MAXS[i] : MINS[i]);
        return "struct "+name+" { "+TYPES[i]+" x="+initial+"; "
            +"val next_noInline={ -> x"+(increment ? "++" : "--")+"; return x; }; }; "
            +"var c=new "+name+"; "+(argument ? "c.x=arg; " : "")
            +"val a=c.next_noInline(); val b=c.next_noInline(); "
            +"return a+10*b+100*c.x;";
    }

    private static long expected(int i, boolean increment) {
        long first = increment ? MINS[i] : MAXS[i];
        long second = first+(increment ? 1 : -1);
        return first+110*second;
    }

    @Test public void testMethodResults() {
        for( int i = 0; i<TYPES.length; i++ )
            for( boolean increment : new boolean[]{true,false} )
                for( int seed : new int[]{2,13,123} ) {
                    String src = source(i,increment,false);
                    CodeGen code = new CodeGen(src,TypeInteger.BOT,seed,true).driver(CodeGen.Phase.TypeCheck);
                    assertEquals(src,Long.toString(expected(i,increment)),Eval2.eval(code,0));
                }
    }

    @Test public void testNativeMethodResults() throws IOException {
        StringBuilder src = new StringBuilder();
        StringBuilder declarations = new StringBuilder();
        StringBuilder checks = new StringBuilder();
        // Supply the initial value through the native argument to also exercise
        // narrowing an unknown full-width value before the method calls.
        for( int i = 0; i<TYPES.length; i++ )
            for( boolean increment : new boolean[]{true,false} ) {
                String name = "narrow"+i+(increment ? "Inc" : "Dec");
                src.append("val ").append(name).append("={int arg -> ")
                    .append(source(i,increment,true)).append(" }; ");
                declarations.append("__attribute__((CALL_CONV)) extern long long ").append(name).append("(long long);\n");
                checks.append("if (").append(name).append('(').append(increment ? MAXS[i] : MINS[i])
                    .append("LL)!=").append(expected(i,increment)).append("LL) return 1;\n");
            }
        Path dir = Path.of("build/objs");
        Files.createDirectories(dir);
        Path driver = dir.resolve("narrowField.c");
        Files.writeString(driver,declarations+"int main(void) {\n"+checks+"return 0; }\n");
        String abi = TestC.OS.startsWith("Windows") ? "ms_abi" : "sysv_abi";
        TestC.run(src.toString(),TestC.CALL_CONVENTION,null,abi,driver.toString(),"build/objs/narrowField","S","",-1);
    }

    @Test public void testRiscvMethodResults() throws IOException {
        for( int i = 0; i<TYPES.length; i++ )
            for( boolean increment : new boolean[]{true,false} ) {
                String src = source(i,increment,false);
                CodeGen code = new CheckedCodeGen(src).driver("riscv","SystemV",null);
                int entry = -1;
                for( var node : code._start._outputs )
                    if( node instanceof FunNode fun && "main".equals(fun._name) )
                        entry = code._encoding._opStart[fun._nid];
                assertTrue(entry>=0);
                byte[] image = new byte[1<<20];
                byte[] bits = code._encoding.bits();
                System.arraycopy(bits,0,image,0,bits.length);
                EvalRisc5 cpu = new EvalRisc5(image,1<<16);
                cpu._pc = entry;
                assertEquals(src,0,cpu.step(100000));
                assertEquals(src,expected(i,increment),cpu.regs[riscv.A0]);
            }
    }
}
