package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.util.SB;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter21Test {
    @Test public void testCoalescing() { com.seaofnodes.simple.codegen.RegAllocTestSupport.coalescing(); }

    @Test public void testNarrowStores() throws IOException {
        for( String type : new String[]{"i8","u8","i16","u16"} ) {
            String src = "struct S { "+type+" x; }; S !s = new S; s.x = arg; return 0;";
            CodeGen code = new CodeGen(src).driver("riscv","SystemV",null);
            int stores=0;
            for( var bb : code._cfg )
                for( var node : bb.outs() )
                    if( node instanceof com.seaofnodes.simple.node.cpus.riscv.StoreRISC st ) {
                        stores++;
                        assertEquals("Byte/short stores have no FP encoding",-1,st.regmap(4).nextReg((short)31));
                    }
            assertEquals(1,stores);
            byte[] image = new byte[1<<20];
            byte[] bits = code._encoding.bits();
            System.arraycopy(bits,0,image,0,bits.length);
            EvalRisc5 cpu = new EvalRisc5(image,1<<16);
            cpu.regs[riscv.A0] = 0x8765;
            assertEquals(0,cpu.step(100));
            assertEquals(type.endsWith("8") ? 0x65 : 0x8765,
                         type.endsWith("8") ? cpu.ld1z(1<<16) : cpu.ld2z(1<<16));
            code = new CodeGen(src).driver(CodeGen.Phase.Encoding,"x86_64_v2",TestC.CALL_CONVENTION);
            stores=0;
            for( var bb : code._cfg )
                for( var node : bb.outs() )
                    if( node instanceof com.seaofnodes.simple.node.cpus.x86_64_v2.StoreX86 st ) {
                        stores++;
                        assertEquals("Byte/short stores have no XMM encoding",-1,st.regmap(4).nextReg((short)15));
                    }
            assertEquals(1,stores);
        }
    }

    @Test public void testNativeExitStatus() throws IOException {
        Path source = Path.of("build/objs/nativeExit.c");
        Files.createDirectories(source.getParent());
        Files.writeString(source,"int main() { return 7; }\n");
        try {
            TestC.gcc(source.toString(),null,null,false,
                      "build/objs/nativeExit"+(TestC.OS.startsWith("Windows") ? ".exe" : ""));
        } catch( AssertionError error ) {
            assertTrue(error.getMessage(),error.getMessage().contains("Program exit status"));
            return;
        }
        fail("A failing native program must fail the test, even with empty stdout");
    }

    @Test public void testArmSubtractRegisters() {
        EvalArm64 cpu = new EvalArm64(new byte[16],16);
        cpu.st4(0,0xCB020020); // SUB X0,X1,X2, no shift or flag update.
        for( long[] pair : new long[][]{{7,3},{3,7},{Long.MIN_VALUE,1},{1L<<40,3}} ) {
            cpu._pc = 0;
            cpu.regs[1] = pair[0]; cpu.regs[2] = pair[1];
            cpu.N=true; cpu.Z=false; cpu.C=true; cpu.V=true;
            assertEquals(0,cpu.step(1));
            assertEquals(pair[0]-pair[1],cpu.regs[0]);
            assertTrue(cpu.N); assertFalse(cpu.Z); assertTrue(cpu.C); assertTrue(cpu.V);
        }
    }


    @Test public void testRisc64BitStore() {
        byte[] mem = new byte[24];
        EvalRisc5 cpu = new EvalRisc5(mem,mem.length);
        check64BitStore(mem,cpu::st8,cpu::ld8);
    }

    @Test public void testArm64BitStore() {
        byte[] mem = new byte[24];
        EvalArm64 cpu = new EvalArm64(mem,mem.length);
        check64BitStore(mem,cpu::st8,cpu::ld8);
    }

    // Check bytes independently of ld8, including overwrite and adjacent memory.
    private static void check64BitStore(byte[] mem,
                                       java.util.function.BiConsumer<Integer,Long> store,
                                       java.util.function.IntToLongFunction load) {
        java.util.Arrays.fill(mem,(byte)0xA5);
        for( long value : new long[]{0x0123456789ABCDEFL,0xFEDCBA9876543210L,
                                     -1L,0L,Long.MIN_VALUE,Long.MAX_VALUE} ) {
            store.accept(8,value);
            for( int i=0; i<mem.length; i++ ) {
                int expected = i>=8 && i<16 ? (int)(value >>> (8*(i-8)))&0xFF : 0xA5;
                assertEquals("byte "+i,expected,mem[i]&0xFF);
            }
            assertEquals(value,load.applyAsLong(8));
        }
    }


    @Test
    public void testJig() throws IOException {
        String src =
"""
struct s0 {
    bool v1;
    i16 v2;
    int v3;
    i8 v4;
    byte v5;
};
while(new s0.v3)
    while(new s0.v5<<new s0.v4) {}
if(0) {
    if(0) {
        flt !P5ZUD4=new s0.v2;
    }
    while(0) {}
}
return new s0.v1;
""";
        testCPU(src,"x86_64_v2", "Win64"  ,-1,null);
        testCPU(src,"riscv"    , "SystemV",-1,null);
        testCPU(src,"arm"      , "SystemV",-1,null);
    }

    static void testCPU( String src, String cpu, String os, int spills, String stop ) {
        CodeGen code = new CheckedCodeGen(src).driver(CodeGen.Phase.Encoding,cpu,os);
        int delta = spills>>3;
        if( delta==0 ) delta = 1;
        if( spills != -1 )
            assertEquals("Expect spills:",spills,code._regAlloc._spillScaled,delta);
        if( stop != null )
            assertEquals(stop, code._stop.toString());
    }


    @Test public void testBasic1() {
        String src = "return arg | 2;";
        testCPU(src,"x86_64_v2", "SystemV",1,"return (ori,mov(arg));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( arg | #2 );");
        testCPU(src,"arm"      , "SystemV",0,"return (ori,arg);");
    }

    @Test public void testInfinite() {
        String src = "struct S { int i; }; S !s = new S; while(1) s.i++; return s.i;";
        testCPU(src,"x86_64_v2", "SystemV",0,"return Top;");
        testCPU(src,"riscv"    , "SystemV",2,"return Top;");
        testCPU(src,"arm"      , "SystemV",2,"return Top;");
    }

    @Test
    public void testArray1() throws IOException {
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
        testCPU(src,"x86_64_v2", "SystemV",-1,"return .[];");
        testCPU(src,"riscv"    , "SystemV", 7,"return (add,.[],(mul,.[],1000));");
        testCPU(src,"arm"      , "SystemV", 5,"return (add,.[],(mul,.[],1000));");
    }

    @Test
    public void testAntiDeps1() throws IOException {
        String src =
"""
struct S { int f; };
var v0 = new S;
S? v1;
if (arg) v1 = new S;
if (v1) {
    v0.f = v1.f;
} else {
    v0.f = 2;
}
return v0;
""";
        testCPU(src,"x86_64_v2", "SystemV", 7,"return mov(mov(S));");
        testCPU(src,"riscv"    , "SystemV",10,"return mov(mov(S));");
        testCPU(src,"arm"      , "SystemV",10,"return mov(mov(S));");
    }

    @Test
    public void testString() throws IOException {
        String src =
"""
struct String {
    u8[] cs;
    int _hashCode;
};

// Compare two Strings
val equals = { String self, String s ->
    if( self == s ) return true;
    if( self.cs# != s.cs# ) return false;
    for( int i=0; i< self.cs#; i++ )
        if( self.cs[i] != s.cs[i] )
            return false;
    return true;
};

// Return the String hashCode (cached, and never 0)
val hashCode = { String self ->
    self._hashCode
    ?  self._hashCode
    : (self._hashCode = _hashCodeString(self));
};

val _hashCodeString = { String self ->
    int hash=0;
    for( int i=0; i< self.cs#; i++ )
        hash = hash*31 + self.cs[i];
    if( !hash ) hash = 123456789;
    return hash;
};
""";
        testCPU(src,"x86_64_v2", "SystemV", 9,null);
        testCPU(src,"riscv"    , "SystemV", 3,null);
        testCPU(src,"arm"      , "SystemV", 3,null);
    }

    @Test public void testStringExport() throws IOException {
        String src =
"""
struct String {
    u8[] cs;
    int _hashCode;
};

// Compare two Strings
val equals = { String self, String s ->
    if( self == s ) return true;
    if( self.cs# != s.cs# ) return false;
    for( int i=0; i< self.cs#; i++ )
        if( self.cs[i] != s.cs[i] )
            return false;
    return true;
};

// Return the String hashCode (cached, and never 0)
val hashCode = { String self ->
    self._hashCode
    ?  self._hashCode
    : (self._hashCode = _hashCodeString(self));
};

val _hashCodeString = { String self ->
    int hash=0;
    for( int i=0; i< self.cs#; i++ )
        hash = hash*31 + self.cs[i];
    if( !hash ) hash = 123456789;
    return hash;
};
""";
        TestC.run(src, "stringHash",null, "", 9);
    }

    @Test public void testLoop2() throws IOException {
        String src =
"""
int i = 0;
while(true) {
    i += 1;
    if( i==0 ) continue;
    if( i==1 ) continue;
    if( i==2 ) break;
    continue;
}
return i;
""";
        testCPU(src,"x86_64_v2", "Win64"  ,0,"return (inc,Phi(Loop,0,inc));");
        testCPU(src,"riscv"    , "SystemV",0,"return ( Phi(Loop,0,addi) + #1 );");
        testCPU(src,"arm"      , "SystemV",0,"return (inc,Phi(Loop,0,inc));");
    }

    @Test public void testNewtonExport() throws IOException {
        String result = """
0  0.000000   (0)
1  1.000000   (0)
2  1.414214   (2.22045e-16)
3  1.732051   (0)
4  2.000000   (0)
5  2.236068   (0)
6  2.449490   (0)
7  2.645751   (0)
8  2.828427   (4.44089e-16)
9  3.000000   (0)
""";
        String src =
"""
val test_sqrt = { flt x ->
    flt epsilon = 1e-15;
    flt guess = x;
    while( 1 ) {
        flt next = (x/guess + guess)/2;
        if( guess-epsilon <= next & next <= guess+epsilon ) return guess;
        //if( guess==next ) return guess;
        guess = next;
    }
};
""";
        TestC.run(src, "newtonFloat", null, result, 34);

        EvalRisc5 R5 = TestRisc5.build("newtonFloat",src, 0, 10, false);
        R5.fregs[riscv.FA0 - riscv.F_OFFSET] = 3.0;
        int trap_r5 = R5.step(1000);
        assertEquals(0,trap_r5);
        // Return register A0 holds fib(8)==55
        assertEquals(1.732051,R5.fregs[riscv.FA0 - riscv.F_OFFSET], 0.00001);

        // arm
        EvalArm64 A5 = TestArm64.build("newtonFloat", src,0, 10, false);
        A5.fregs[arm.D0 - arm.D_OFFSET] = 3.0;
        int trap_arm = A5.step(1000);
        assertEquals(0,trap_arm);
        assertEquals(1.732051, A5.fregs[arm.D0 - arm.D_OFFSET], 0.00001);
    }


    @Test public void testSieve() throws IOException {
        String src =
"""
val sieve = { int N ->
    // The main Sieve array
    bool[] !ary = new bool[N];
    // The primes less than N
    u32[] !primes = new u32[N>>1];
    // Number of primes so far, searching at index p
    int nprimes = 0, p=2;
    // Find primes while p^2 < N
    while( p*p < N ) {
        // skip marked non-primes
        while( ary[p] ) p++;
        // p is now a prime
        primes[nprimes++] = p;
        // Mark out the rest non-primes
        for( int i = p + p; i < ary#; i+= p )
            ary[i] = true;
        p++;
    }

    // Now just collect the remaining primes, no more marking
    for( ; p < N; p++ )
        if( !ary[p] )
            primes[nprimes++] = p;

    // Copy/shrink the result array
    u32[] !rez = new u32[nprimes];
    for( int j=0; j < nprimes; j++ )
        rez[j] = primes[j];
    return rez;
};
""";
        // The primes
        int[] primes = new int[]  { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, };
        SB sb = new SB().p(primes.length).p("[");
        for( int prime : primes )
            sb.p(prime).p(", ");
        String sprimes = sb.p("]").toString();

        // Compile, link against native C; expect the above string of primes to be printed out by C
        TestC.run(src, "sieve", null, sprimes, 186);

        // Evaluate on RISC5 emulator; expect return of an array of primes in
        // the simulated heap.
        EvalRisc5 R5 = TestRisc5.build("sieve", src, 100, 92, false);
        int trap = R5.step(10000);
        assertEquals(0,trap);
        // Return register A0 holds sieve(100)
        int ary = (int)R5.regs[riscv.A0];
        // Memory layout starting at ary(length,pad, prime1, primt2, prime3, prime4)
        assertEquals(primes.length, R5.ld4s(ary));
        for( int i=0; i<primes.length; i++ )
            assertEquals(primes[i], R5.ld4s(ary + 4 + i*4));

        // Evaluate on ARM5 emulator; expect return of an array of primes in
        // the simulated heap.
        EvalArm64 A5 = TestArm64.build("sieve", src, 100, 94, false);
        int trap_arm = A5.step(10000);
        assertEquals(0, trap_arm);
        int ary_arm = (int)A5.regs[arm.X0];
        // Memory layout starting at ary(length,pad, prime1, primt2, prime3, prime4)
        assertEquals(primes.length, A5.ld4s(ary_arm));
        for( int i = 0; i<primes.length; i++ )
            assertEquals(primes[i], A5.ld4s(ary_arm + 4 + i * 4));
    }

    @Test public void testFibExport() throws IOException {
        String src =
"""
val fib = {int n ->
    int f1=1;
    int f2=1;
    while( n-- > 1 ){
        int temp = f1+f2;
        f1=f2;
        f2=temp;
    }
    return f2;
};
""";
        String fib = "[1, 1, 2, 3, 5, 8, 13, 21, 34, 55]";
        TestC.run(src, "fib", null, fib, 24);

        EvalRisc5 R5 = TestRisc5.build("fib", src, 9, 17, false);
        int trap = R5.step(100);
        assertEquals(0,trap);
        // Return register A0 holds fib(8)==55
        assertEquals(55,R5.regs[riscv.A0]);

        // arm
        EvalArm64 A5 = TestArm64.build("fib", src, 9, 17, false);
        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);
        // Return register X0 holds fib(8)==55
        assertEquals(55, A5.regs[arm.X0]);
    }

    @Test public void testPerson() throws IOException {
        String src =
                """
                struct Person {
                    i32 age;
                };

                val fcn = { Person?[] ps, int x ->
                    if( ps[x] )
                        ps[x].age++;
                };
                """;
        String person = "6\n";
        TestC.run(src, "person", null, person, 0);

        // Memory layout starting at PS:
        int ps = 1<<16;         // Person array pointer starts at heap start
        // Person[3] = { len,pad,P0,P1,P2 }; // sizeof = 4*8
        // P0 = { age } // sizeof=8
        int p0 = ps+4*8+0*8;
        // P1 = { age } // sizeof=8
        int p1 = ps+4*8+1*8;
        // P2 = { age } // sizeof=8
        int p2 = ps+4*8+2*8;
        EvalRisc5 R5 = TestRisc5.build("person", src, ps, 0, false);
        R5.regs[riscv.A1] = 1;  // Index 1
        R5.st8(ps,3);           // Length
        R5.st8(ps+1*8,p0);
        R5.st8(ps+2*8,p1);
        R5.st8(ps+3*8,p2);
        R5.st8(p0, 5); // age= 5
        R5.st8(p1,17); // age=17
        R5.st8(p2,60); // age=60

        int trap = R5.step(100);
        assertEquals(0,trap);
        assertEquals( 5+0,R5.ld8(p0));
        assertEquals(17+1,R5.ld8(p1));
        assertEquals(60+0,R5.ld8(p2));

        EvalArm64 A5 = TestArm64.build("person", src, ps, 0, false);
        A5.regs[arm.X1] = 1;  // Index 1
        A5.st8(ps, 3);
        A5.st8(ps+1*8,p0);
        A5.st8(ps+2*8,p1);
        A5.st8(ps+3*8,p2);
        A5.st8(p0, 5); // age= 5
        A5.st8(p1,17); // age=17
        A5.st8(p2,60); // age=60

        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);
        assertEquals( 5+0, A5.ld8(p0));
        assertEquals(17+1, A5.ld8(p1));
        assertEquals(60+0, A5.ld8(p2));
    }

    @Test public void testArgCount() throws IOException {
        // Test passes more args than registers in Sys5, which is far far more
        // than what Win64 allows - so Win64 gets a lot more spills here.
        String src =
"""
val addAll = { int i0, flt f1, int i2, flt f3, int i4, flt f5, int i6, flt f7, int x8, flt f9, int i10, flt f11, int i12, flt f13, int i14, flt f15, int x16, flt f17 int x18, flt f19 ->
    return
    i0 + f1+ i2+ f3+ i4+ f5+ i6+ f7+ x8 +f9 +
    i10+f11+i12+f13+i14+f15+x16+f17+x18+f19 ;
};
""";
        String arg_count = "191.000000\n";

        TestC.run(src, "arg_count", null, arg_count, TestC.CALL_CONVENTION.equals("Win64") ? 32 : 15);

        EvalRisc5 R5 = TestRisc5.build("no_stack_arg_count", src, 0, 4, false);

        // Todo: handle stack(imaginary stack in emulator)
        // pass in float arguments
        R5.fregs[riscv.FA0 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA1 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA2 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA3 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA4 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA5 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA6 - riscv.F_OFFSET] = 1.1;
        R5.fregs[riscv.FA7 - riscv.F_OFFSET] = 1.1;

        // a0 is passed in arg
        R5.regs[riscv.A1] = 2;
        R5.regs[riscv.A2] = 2;
        R5.regs[riscv.A3] = 2;
        R5.regs[riscv.A4] = 2;
        R5.regs[riscv.A5] = 2;
        R5.regs[riscv.A6] = 2;
        R5.regs[riscv.A7] = 2;

        int trap = R5.step(100);
        assertEquals(0,trap);

        double result = R5.fregs[riscv.FA0 - riscv.F_OFFSET];

        assertEquals(22.8, result, 0.00001);

        // arm
        EvalArm64 A5 = TestArm64.build("no_stack_arg_count", src, 0, 4, false);

        A5.fregs[arm.D0 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D1 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D2 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D3 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D4 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D5 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D6 - arm.D_OFFSET] = 1.1;
        A5.fregs[arm.D7 - arm.D_OFFSET] = 1.1;

        A5.regs[arm.X1]  = 2;
        A5.regs[arm.X2]  = 2;
        A5.regs[arm.X3]  = 2;
        A5.regs[arm.X4]  = 2;
        A5.regs[arm.X5]  = 2;
        A5.regs[arm.X6]  = 2;
        A5.regs[arm.X7]  = 2;

        int trap_arm = A5.step(100);
        assertEquals(0,trap_arm);

        double result1 = A5.fregs[arm.D0 - arm.D_OFFSET];
        assertEquals(22.8, result1, 0.00001);
    }
}
