package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static com.seaofnodes.simple.Main.PORTS;
import static org.junit.Assert.*;

public class Chapter20Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("return 0;");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(PORTS,cpu,os).GCM().localSched().regAlloc();
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
    }

    //  // Todo: need sp adjust, callee saves and caller saves
    // Todo: Hack split so extra mov is not needed
    @Test public void TestSplitHack() {
        CodeGen code = new CodeGen("""
         return {int i, flt f, int j->return i+f+j;};
           """);

        code.parse().opto().typeCheck().instSelect(PORTS,"x86_64_v2", "SystemV").GCM().localSched().regAlloc().printENCODINGX64();
    }

    // Todo: do spilling and invole stack slots/stack frame currently AAOIB
    @Test public void TestStackSlotTodo() {
        CodeGen code = new CodeGen("""
            return {flt f1, flt f2, flt f3, flt f4, flt f5, flt f6, flt f7, flt f7 flt f9->return f9;};
            """);

        code.parse().opto().typeCheck().instSelect(PORTS, "x86_64_v2", "SystemV").GCM().localSched().regAlloc().printENCODINGX64();
    }


    // Todo: Insert unconditional jumps
    @Test public void TestUnconditionalJumpTodo() {
        CodeGen code = new CodeGen("""
                if (arg == 0) arg += 2;
                else arg += arg;
                return arg;
            """);

        code.parse().opto().typeCheck().instSelect(PORTS,"x86_64_v2", "SystemV").GCM().localSched().regAlloc().printENCODINGX64();
    }

    @Test public void TestNotNodeTodo() {
        CodeGen code = new CodeGen("""
        return arg != 0;
            """);
        code.parse().opto().typeCheck().instSelect(PORTS, "x86_64_v2", "SystemV").GCM().localSched().regAlloc().printENCODINGX64();
    }

    @Test public void TestAddition() {
        CodeGen code = new CodeGen("""
        return arg + 2;
            """);
        code.parse().opto().typeCheck().instSelect(PORTS, "riscv", "SystemV").GCM().localSched().regAlloc().printENCODINGRISCV();
    }
    @Test public void testDoubleNotNodeTodo() {
        CodeGen code = new CodeGen("""
        return !!arg;
            """);
        code.parse().opto().typeCheck().instSelect(PORTS, "x86_64_v2", "SystemV").GCM().localSched().regAlloc().printENCODINGX64();
    }

    private static void testAllCPUs( String src, int spills, String stop ) {
        testCPU(src,"x86_64_v2", "SystemV",spills,stop);
        testCPU(src,"riscv"    , "SystemV",spills,stop);
        testCPU(src,"arm"      , "SystemV",spills,stop);
    }

    @Test public void testAlloc0() {
        testAllCPUs("return new u8[arg];", 1, "return [u8];");
    }

    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testCPU(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testCPU(src,"riscv"    , "SystemV",0,"return (ori,arg);");
        testCPU(src,"arm"      , "SystemV",0,"return (ori,arg);");
    }

    @Test
    public void testNewtonInteger() {
        String src =
"""
// Newtons approximation to the square root
val sqrt = { int x ->
    int guess = x;
    while( 1 ) {
        int next = (x/guess + guess)/2;
        if( next == guess ) return guess;
        guess = next;
    }
};
return sqrt(arg) + sqrt(arg+2);
""";
        testCPU(src,"x86_64_v2", "SystemV",22,null);
        testCPU(src,"riscv"    , "SystemV",14,null);
        testCPU(src,"arm"      , "SystemV",22,null);
    }

    @Test
    public void testNewtonFloat() {
        String src =
"""
// Newtons approximation to the square root
val sqrt = { flt x ->
    flt guess = x;
    while( 1 ) {
        flt next = (x/guess + guess)/2;
        if( next == guess ) return guess;
        guess = next;
    }
};
flt farg = arg;
return sqrt(farg) + sqrt(farg+2.0);
""";
        testCPU(src,"x86_64_v2", "SystemV",22,null);
        testCPU(src,"riscv"    , "SystemV",21,null);
        testCPU(src,"arm"      , "SystemV",12,null);
    }

    @Test
    public void testAlloc2() {
        String src = "int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];";
        testAllCPUs(src,1,"return .[];");
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
        testCPU(src,"riscv"    , "SystemV",1,"return (add,.[],(muli,.[]));");
        testCPU(src,"arm"      , "SystemV",1,"return (add,.[],(muli,.[]));");
    }

    @Test
    public void testString() {
        String src = """
struct String {
    u8[] cs;
    int _hashCode;
};

val equals = { String self, String s ->
    if( self == s ) return true;
    if( self.cs# != s.cs# ) return false;
    for( int i=0; i< self.cs#; i++ )
        if( self.cs[i] != s.cs[i] )
            return false;
    return true;
};

val hashCode = { String self ->
    self._hashCode
    ?  self._hashCode
    : (self._hashCode = _hashCodeString(self));
};

val _hashCodeString = { String self ->
    int hash=0;
    if( self.cs ) {
        for( int i=0; i< self.cs#; i++ )
            hash = hash*31 + self.cs[i];
    }
    if( !hash ) hash = 123456789;
    return hash;
};

String !s = new String { cs = new u8[17]; };
s.cs[0] =  67; // C
s.cs[1] = 108; // l
hashCode(s);
""";
        testCPU(src,"x86_64_v2", "SystemV",18,null);
        testCPU(src,"riscv"    , "SystemV", 3,null);
        testCPU(src,"arm"      , "SystemV", 3,null);
    }

    @Test
    public void testCast() {
        String src = "struct Bar { int x; }; var b = arg ? new Bar;  return b ? b.x++ + b.x++ : -1;";
        testAllCPUs(src,0,null);
    }

    @Test
    public void testFltArg() {
        String src = "return {int i, flt f, int j->return i+f+j;};";
        testAllCPUs(src,0,null);
    }

}
