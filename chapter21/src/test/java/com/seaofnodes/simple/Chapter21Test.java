package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static com.seaofnodes.simple.Main.PORTS;
import static org.junit.Assert.*;

public class Chapter21Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("return 0;");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(PORTS,cpu,os).GCM().localSched().regAlloc().encode();
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
    }

    private static void testAllCPUs( String src, int spills, String stop ) {
        testCPU(src,"x86_64_v2", "SystemV",spills,stop);
        testCPU(src,"riscv"    , "SystemV",spills,stop);
        testCPU(src,"arm"      , "SystemV",spills,stop);
    }


    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testCPU(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( arg | #2 );");
        testCPU(src,"arm"      , "SystemV",0,"return (ori,arg);");
    }

    @Test
    public void testArray1() {
        String src =
                """
                int[] !ary = new int[arg];
                // Fill [0,1,2,3,4,...]
                for( int i=0; i<ary#; i++ )
                    ary[i] = i;
                // Fill [0,1,3,6,10,...]
                for( int i=0; i<ary#-1; i++ )
                    ary[i+1] += ary[i];
                return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
                """;
        testCPU(src,"x86_64_v2", "SystemV",3,"return .[];");
        testCPU(src,"riscv"    , "SystemV",1,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV",1,"return (add,.[],(muli,.[]));");
    }


}
