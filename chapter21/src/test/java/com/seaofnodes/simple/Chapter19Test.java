package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter19Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("""
return 0;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }


    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        CodeGen code = new CodeGen(src).parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return Phi(Region,._hashCode,Phi(Region,123456789,Phi(Loop,0,(.[]+((Phi_hash<<5)-Phi_hash))))); return Phi(Region,1,0,0,1); ]", code._stop.toString());
        //assertEquals("-4898613127354160978", Eval2.eval(code,  2));
    }

    @Test
    public void testBasic0() {
        CodeGen code = new CodeGen("return 0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return 0;", code._stop.toString());
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg+1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (inc,arg);", code._stop.toString());
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen("return -17;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return -17;", code._stop.toString());
    }


    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen("return arg==1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (set==,(cmp,arg));", code._stop.toString());
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen("return arg<<1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (shli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen("return arg >> 1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (sari,arg);", code._stop.toString());
    }

    @Test
    public void testBasic6() {
        CodeGen code = new CodeGen("return arg >>> 1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (shri,arg);", code._stop.toString());
    }

    @Test
    public void testBasic7() {
        CodeGen code = new CodeGen("return arg / 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (div,arg,2);", code._stop.toString());
    }

    @Test
    public void testBasic8() {
        CodeGen code = new CodeGen("return arg * 6;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (muli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic9() {
        CodeGen code = new CodeGen("return arg & 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (andi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic10() {
        CodeGen code = new CodeGen("return arg | 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (ori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic11() {
        CodeGen code = new CodeGen("return arg ^ 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (xori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic12() {
        CodeGen code = new CodeGen("return arg + 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic13() {
        CodeGen code = new CodeGen("return arg - 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (subf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic14() {
        CodeGen code = new CodeGen("return arg * 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mulf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic15() {
        CodeGen code = new CodeGen("return arg / 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mulf,(cvtf,arg),0.5f);", code._stop.toString());
    }

    @Test
    public void testBasic16() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 / arg;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (div,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testBasic17() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 * arg;
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mul,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testToFloat() {
        CodeGen code = new CodeGen("""
int a = arg;
return a + 2.0;
"""
        ).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testIfStmt() {
        CodeGen code = new CodeGen(
"""
int a = 1;
if (arg == 1)
    a = arg+2;
else {
    a = arg-3;
}
return a;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Region,(addi,arg),(addi,arg));", code.print());
    }

    @Test
    public void testIfMerge2() {
        CodeGen code = new CodeGen(
"""
int a=arg+1;
int b=arg+2;
if( arg==1 )
    b=b+a;
else
    a=b+1;
return a+b;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (add,(add,Phi(Region,(shli,arg),arg),arg),Phi(Region,4,5));", code.print());
    }

    @Test
    public void testLoop() {
        CodeGen code = new CodeGen(
"""
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return sum;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Loop,0,(add,Phi_sum,Phi(Loop,0,(inc,Phi_i))));", code.print());
    }

    @Test
    public void testAlloc1() {
        CodeGen code = new CodeGen(
"""
struct S { int a; S? c; };
return new S;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return S;", code.print());
    }

    @Test
    public void testLea1() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x+7;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea2() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x*4+7;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea3() {
        CodeGen code = new CodeGen("int x = arg/3; return x*4+arg;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testAlloc2() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testAlloc3() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1]+3;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testArray1() {
        CodeGen code = new CodeGen(
"""
int[] !ary = new int[arg];
// Fill [0,1,2,3,4,...]
for( int i=0; i<ary#; i++ )
    ary[i] = i;
// Fill [0,1,3,6,10,...]
for( int i=0; i<ary#-1; i++ )
    ary[i+1] += ary[i];
return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testArray2() {
        CodeGen code = new CodeGen(
"""
flt[] !A = new flt[arg], !B = new flt[arg];
// Fill [0,1,2,3,4,...]
for( int i=0; i<A#; i++ )
    A[i] = i;
for( int i=0; i<A#; i++ )
    B[i] += A[i];
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testArray3() {
        CodeGen code = new CodeGen(
"""
byte[] !A = new byte[arg];
for( int i=0; i<A#; i++ )
    A[i]++;
return A[1];
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testNewton() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg;  return test_sqrt(farg);";
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Loop,(cvtf,arg),(mulf,(addf,(divf,cvtf,Phi_guess),Phi_guess),0.5f));", code.print());
    };


    @Test
    public void sieveOfEratosthenes() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/sieve.smp"));
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return [u32];", code.print());
        //assertEquals("u32[ 2,3,5,7,11,13,17,19]",Eval2.eval(code, 20));
    }


    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen(
"""
val fcn = arg ? { int x -> x*x; } : { int x -> x+x; };
return fcn(2)*10 + fcn(3);
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("Stop[ return (add,#2,(muli,#2)); return (mul,Parm_x($fun1,int),x); return (shli,Parm_x($fun2,int)); ]", code.print());
    }

    @Test
    public void testFcn2() {
        CodeGen code = new CodeGen(
"""
val sq = { int x -> x*x; };
return sq(arg) + sq(3);
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("Stop[ return (add,#2,#2); return (mul,Parm_x(sq,int),x); ]", code.print());
    }
}
