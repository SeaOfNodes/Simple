package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;


import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter20Test {
    @Test public void testInlinedReturnValue() {
        String src = """
struct S { int x; };
val f = { S s -> s.x = g(); };
val g = { -> 123; };
S !s = new S;
return f(s);
""";
        // The entry of g can disappear before its Return is folded into f.
        // Exercise both worklist orders; deleting the entry does not kill 123.
        for( int seed=0; seed<64; seed++ ) {
            CodeGen code = new CodeGen(src,com.seaofnodes.simple.type.TypeInteger.BOT,seed,true).parse().opto().typeCheck();
            assertEquals("seed "+seed, "123", Eval2.eval(code,0));
        }
    }


    @org.junit.Rule public final org.junit.rules.ErrorCollector _errors = new org.junit.rules.ErrorCollector();

    @Test public void testAllocatorMasks() { com.seaofnodes.simple.codegen.RegAllocTestSupport.masks(); }
    @Test public void testAllocatorUnion() { com.seaofnodes.simple.codegen.RegAllocTestSupport.union(); }
    @Test public void testAllocatorCopyClobber() throws Exception { com.seaofnodes.simple.codegen.RegAllocTestSupport.copyClobber(); }
    @Test public void testAllocatorCommutativePhi() { com.seaofnodes.simple.codegen.RegAllocTestSupport.commutativePhi(); }
    @Test public void testAllocatorKills() { com.seaofnodes.simple.codegen.RegAllocTestSupport.killWithoutResult(); }
    @Test public void testAllocatorDependencies() { com.seaofnodes.simple.codegen.RegAllocTestSupport.nullUseMask(); }
    @Test public void testAllocatorCloneClass() { com.seaofnodes.simple.codegen.RegAllocTestSupport.cloneRegisterClass(); }

    @Test public void testPrintingRegisters() throws Exception {
        com.seaofnodes.simple.codegen.PrintRegTestSupport.check();
    }


    @Test
    public void testJig() {
        CodeGen code = new CodeGen("return 0;");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    // Collect differences so every target runs; JUnit still fails the test.
    private void testTarget(String src, String cpu, String os, int spills, String stop) {
        _errors.checkSucceeds(() -> { testCPU(src,cpu,os,spills,stop); return null; });
    }

    static void testCPU(String src, String cpu, String os, int spills, String stop) {
        CodeGen code = new CheckedCodeGen(src);
        code.driver(CodeGen.Phase.RegAlloc,cpu,os);
        SpillStats.record(code,"Chapter20",cpu,os);
        SpillStats.checkSpills(spills,code._regAlloc._spillScaled);
        if( stop!=null ) assertEquals(stop,code._stop.toString());
    }

    private void testAllCPUs( String src, int spills, String stop ) {
        testTarget(src,"x86_64_v2", "SystemV",spills,stop);
        testTarget(src,"riscv"    , "SystemV",spills,stop);
        testTarget(src,"arm"      , "SystemV",spills,stop);
    }

    @Test public void testAlloc0() {
        testTarget("return new u8[arg];","x86_64_v2","SystemV",4,"return []u8;");
        testTarget("return new u8[arg];","riscv","SystemV",5,"return []u8;");
        testTarget("return new u8[arg];","arm","SystemV",5,"return []u8;");
    }

    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testTarget(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testTarget(src,"riscv"    , "SystemV",0,"return ( arg | #2 );");
        testTarget(src,"arm"      , "SystemV",0,"return (ori,arg);");
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
        testTarget(src,"x86_64_v2", "SystemV",48,null);
        testTarget(src,"riscv"    , "SystemV",17,null);
        testTarget(src,"arm"      , "SystemV",18,null);
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
        testTarget(src,"x86_64_v2", "SystemV",23,null);
        testTarget(src,"riscv"    , "SystemV",18,null);
        testTarget(src,"arm"      , "SystemV",18,null);
    }

    @Test
    public void testAlloc2() {
        String src = "int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];";
        testTarget(src,"x86_64_v2","SystemV",3,"return .[];");
        testTarget(src,"riscv","SystemV",6,"return .[];");
        testTarget(src,"arm","SystemV",6,"return .[];");
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
        testTarget(src,"x86_64_v2", "SystemV",9,"return .[];");
        testTarget(src,"riscv"    , "SystemV",7,"return (add,.[],(mul,.[],1000));");
        testTarget(src,"arm"      , "SystemV",5,"return (add,.[],(mul,.[],1000));");
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
        testTarget(src,"x86_64_v2", "SystemV",9,null);
        testTarget(src,"riscv"    , "SystemV",4,null);
        testTarget(src,"arm"      , "SystemV",3,null);
    }

    @Test
    public void testCast() {
        String src = "struct Bar { int x; }; var b = arg ? new Bar;  return b ? b.x++ + b.x++ : -1;";
        testTarget(src,"x86_64_v2","SystemV",1,null);
        testTarget(src,"riscv","SystemV",2,null);
        testTarget(src,"arm","SystemV",2,null);
    }

    @Test
    public void testFltArg() {
        String src = "return {int i, flt f, int j->return i+f+j;};";
        testAllCPUs(src,0,null);
    }

    @Test
    public void testFlags1() {
        String src = """
bool b1 = arg == 1;
bool b2 = arg == 2;
if (b2) if (b1) return 1;
if (b1) return 2;
return 0;
""";
        testTarget(src,"x86_64_v2", "SystemV",0,"return Phi(Region,1,2,0);");
        testTarget(src,"riscv"    , "SystemV",0,"return Phi(Region,1,2,0);");
        testTarget(src,"arm"      , "SystemV",0,"return Phi(Region,1,2,0);");
    }

    @Test
    public void testFlags2() {
        String src = """
bool b1 = arg == 1;
while (arg > 0) {
    arg--;
    if (b1) arg--;
}
return arg;
""";
        testTarget(src,"x86_64_v2", "SystemV",3,null);
        testTarget(src,"riscv"    , "SystemV",2,null);
        testTarget(src,"arm"      , "SystemV",2,null);
    }
    // Original Chapter 20 allocation workload, kept fixed for cohort comparisons.
    @Test
    public void testBrainfuck() {
        var program = "++++++++[>++++[>++>+++>+++>+<<<<-]>+>+>->>+[<]<-]>>.>---.+++++++..+++.>>.<-.<.+++.------.--------.>>+.>++.".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var encoded = new StringBuilder("u8[] !program = new u8[").append(program.length).append("];");
        for (int i = 0; i < program.length; i++) {
            int value = program[i] & 0xFF;
            encoded.append("program[").append(i).append("] = ").append(value).append(";");
        }

        String src = encoded + """

int d = 0;
u8[] !output = new u8[0];
u8[] !data = new u8[100];

for( int pc = 0; pc < program#; pc++ ) {
    var command = program[pc];
    if (command == 62) {
        d++;
    } else if (command == 60) {
        d--;
    } else if (command == 43) {
        data[d]++;
    } else if (command == 45) {
        data[d]--;
    } else if (command == 46) {
        // Output a byte; increase the output array size
        var old = output;
        output = new u8[output# + 1];
        for( int i = 0; i < old#; i++ )
            output[i] = old[i];
        output[old#] = data[d]; // Add the extra byte on the end
    } else if (command == 44) {
        data[d] = 42;
    } else if (command == 91) {
        if (data[d] == 0) {
            for( int d = 1; d > 0; ) {
                command = program[++pc];
                if (command == 91) d++;
                if (command == 93) d--;
            }
        }
    } else if (command == 93) {
        if (data[d]) {
            for( int d = 1; d > 0; ) {
                command = program[--pc];
                if (command == 93) d++;
                if (command == 91) d--;
            }
        }
    }
}
return output;
""";
        testTarget(src,"x86_64_v2", "SystemV",40,null);
        testTarget(src,"riscv"    , "SystemV",28,null);
        testTarget(src,"arm"      , "SystemV",28,null);
        //assertEquals("Hello World!\n", Eval2.eval(code, 0, 10000));
    }
    // Original Chapter 20 allocation workload, kept fixed for cohort comparisons.
    @Test
    public void testMergeSort() {
        String src =
"""
// based on the top-down version from https://en.wikipedia.org/wiki/Merge_sort

val merge_sort = { int[] a, int[] b, int n ->
    copy_array(a, 0, n, b);
    split_merge(a, 0, n, b);
};

val split_merge = { int[] b, int begin, int end, int[] a ->
    if (end - begin <= 1)
        return 0;
    int middle = (end + begin) / 2;
    split_merge(a, begin, middle, b);
    split_merge(a, middle, end, b);
    merge(b, begin, middle, end, a);
    return 0;
};

val merge = { int[] b, int begin, int middle, int end, int[] a ->
    int i = begin, j = middle;

    for (int k = begin; k < end; k++) {
        // && and ||
        bool cond = false;
        if (i < middle) {
            if (j >= end)          cond = true;
            else if (a[i] <= a[j]) cond = true;
        }
        if (cond) b[k] = a[i++];
        else      b[k] = a[j++];
    }
};

val copy_array = { int[] a, int begin, int end, int[] b ->
    for (int k = begin; k < end; k++)
        b[k] = a[k];
};

int[] !a = new int[arg];
int[] !b = new int[a#];

for (int i = 0; i < a#; i++)
    a[i] = a# - i;

merge_sort(a, b, a#);

return a;
""";
        testTarget(src,"x86_64_v2", "SystemV",52,null);
        testTarget(src,"riscv"    , "SystemV",44,null);
        testTarget(src,"arm"      , "SystemV",44,null);
//assertEquals("int[ 1,2,3,4,5,6,7,8,9,10,11]", Eval2.eval(code, 11));
    }

}
