package com.seaofnodes.simple;


import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.print.ASMPrinter;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static com.seaofnodes.simple.Main.PORTS;
import static org.junit.Assert.*;

public class Chapter20Test {
    @Test public void testAllocatorFixedNeighbor() throws Exception { com.seaofnodes.simple.codegen.RegAllocTestSupport.uncoloredFixedNeighbor(); }

    @Test public void testNarrowStoreMasks() {
        for( String type : new String[]{"i8","u8","i16","u16"} ) {
            CodeGen code = new CodeGen("struct S { "+type+" x; }; S !s=new S; s.x=arg; return 0;")
                .parse().opto().typeCheck().instSelect(PORTS,"x86_64_v2","SystemV").GCM().localSched().regAlloc();
            int stores=0;
            for( var bb : code._cfg )
                for( var node : bb.outs() )
                    if( node instanceof com.seaofnodes.simple.node.cpus.x86_64_v2.StoreX86 st ) {
                        stores++;
                        assertEquals(-1,st.regmap(4).nextReg((short)15));
                    }
            assertEquals(1,stores);
        }
    }

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
            CodeGen code = new CodeGen(src,com.seaofnodes.simple.type.TypeInteger.BOT,seed).parse().opto().typeCheck();
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
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck().instSelect(PORTS,cpu,os).GCM().localSched().regAlloc().encode();
        com.seaofnodes.simple.codegen.RegAllocTestSupport.checkRegisters(code);
        SpillStats.record(code,"Chapter20",cpu,os);
        assertEquals("Expect spills: "+cpu,spills,code._regAlloc._spillScaled,Math.max(1,spills>>3));
        if( stop!=null ) assertEquals(stop,code._stop.toString());
    }

    private void testAllCPUs( String src, int spills, String stop ) {
        testTarget(src,"x86_64_v2", "SystemV",spills,stop);
        testTarget(src,"riscv"    , "SystemV",spills,stop);
        testTarget(src,"arm"      , "SystemV",spills,stop);
    }

    @Test public void testAlloc0() {
        testAllCPUs("return new u8[arg];", 1, "return [u8];");
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
        testTarget(src,"x86_64_v2", "SystemV",26,null);
        testTarget(src,"riscv"    , "SystemV",19,null);
        testTarget(src,"arm"      , "SystemV",26,null);
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
        testTarget(src,"x86_64_v2", "SystemV",25,null);
        testTarget(src,"riscv"    , "SystemV",21,null);
        testTarget(src,"arm"      , "SystemV",18,null);
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
        testTarget(src,"x86_64_v2", "SystemV",5,"return .[];");
        testTarget(src,"riscv"    , "SystemV",1,"return (add,.[],(mul,.[],1000));");
        testTarget(src,"arm"      , "SystemV",1,"return (add,.[],(muli,.[]));");
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
        testTarget(src,"x86_64_v2", "SystemV",21,null);
        testTarget(src,"riscv"    , "SystemV", 3,null);
        testTarget(src,"arm"      , "SystemV", 5,null);
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
}
