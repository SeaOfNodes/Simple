package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.*;

public class Chapter18Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen("""
return 0;
""");
        code.parse(true).opto().typeCheck().GCM();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testPhiParalleAssign() {
        CodeGen code = new CodeGen("""
int a = 1;
int b = 2;
while(arg--) {
  int t = a;
  a = b;
  b = t;
}
return a;
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("return Phi(Loop,1,Phi(Loop,2,Phi_a));", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
        assertEquals("2", Eval2.eval(code,  1));
        assertEquals("1", Eval2.eval(code,  2));
        assertEquals("2", Eval2.eval(code,  3));
    }


    // ---------------------------------------------------------------
    @Test
    public void testType0() {
        CodeGen code = new CodeGen("""
{int -> int}? x2 = null; // null function ptr
return x2;
""");
        code.parse().opto();
        assertEquals("return null;", code._stop.toString());
        assertEquals("null", Eval2.eval(code, 0 ) );
    }

    @Test
    public void testFcn0() {
        CodeGen code = new CodeGen("""
{int -> int}? sq = { int x ->
    x*x;
};
return sq;
""");
        code.parse().opto();
        assertEquals("Stop[ return { sq}; return (Parm_x(sq,int)*x); ]", code._stop.toString());
        assertEquals("{ sq}", Eval2.eval(code, 3));
    }

    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen("""
var sq = { int x ->
    x*x;
};
return sq(arg)+sq(3);
""");
        code.parse().opto();
        assertEquals("Stop[ return (sq( 3)+sq( arg)); return (Parm_x(sq,int,3,arg)*x); ]", code._stop.toString());
        assertEquals("13", Eval2.eval(code, 2));
    }

    // Recursive factorial test
    @Test
    public void testFcn2() {
        CodeGen code = new CodeGen("val fact = { int x -> x <= 1 ? 1 : x*fact(x-1); }; return fact(arg);");
        code.parse().opto();
        assertEquals("Stop[ return fact( arg); return Phi(Region,1,(Parm_x(fact,int,arg,(x-1))*fact( Sub))); ]", code._stop.toString());
        assertEquals( "1", Eval2.eval(code, 0));
        assertEquals( "1", Eval2.eval(code, 1));
        assertEquals( "2", Eval2.eval(code, 2));
        assertEquals( "6", Eval2.eval(code, 3));
        assertEquals("24", Eval2.eval(code, 4));
    }



    @Ignore @Test
    public void testFcn3() {
        CodeGen code = new CodeGen(
"""
val counter = { ->
    int cnt;
    val rez = new {int}?[2];
    rez[0] = { ->   cnt; };
    rez[1] = { -> ++cnt; };
    return rez;
};
val A = counter();
val B = counter();
int[] rez = new int[5];
rez[0] = A[0](); // Value of counter A
rez[1] = A[1](); // Increment counter A
rez[2] = B[0](); // Value of counter B
B[1]();          // Increment B
B[1]();          // Increment B
rez[3] = B[0](); // Value of counter B
rez[4] = A[0]() * 10 + B[0]();
return rez;
""");
        code.parse().opto();
        assertEquals("Stop[ return (const)[int]; return (const)[{ -> int #ALL}?]; return 0; return 1; ]", code._stop.toString());
        assertEquals("13", Eval2.eval(code, 0));
    }

}
