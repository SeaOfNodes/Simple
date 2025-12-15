package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;

public class Chapter18Test {

    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 0;
""");
        code.driver(Phase.LocalSched);
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    @Test
    public void testPhiParalleAssign() {
        CodeGen code = new CodeGen(
"""
int a = 1;
int b = 2;
while(arg--) {
  int t = a;
  a = b;
  b = t;
}
return a;
""");
        code.parse().opto().typeCheck();
        assertEquals("return Phi(Loop,1,Phi(Loop,2,Phi_a));", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
        assertEquals("2", Eval2.eval(code,  1));
        assertEquals("1", Eval2.eval(code,  2));
        assertEquals("2", Eval2.eval(code,  3));
    }


    // ---------------------------------------------------------------
    @Test
    public void testType0() {
        CodeGen code = new CodeGen(
"""
{int -> int}? x2 = null; // null function ptr
return x2;
""");
        code.parse().opto();
        assertEquals("return null;", code._stop.toString());
        assertEquals("null", Eval2.eval(code, 0 ) );
    }

    @Test
    public void testFcn0() {
        CodeGen code = new CodeGen(
"""
{int -> int}? _sq = { int x ->
    x*x;
};
""");
        code.parse().opto();
        assertEquals("return { _sq};", code._stop.toString());
        //assertEquals("{ int -> int #1}", Eval2.eval(code, 3));
    }

    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen(
"""
var _sq = { int x ->
    x*x;
};
return _sq(arg)+_sq(3);
""");
        code.driver(Phase.LocalSched);
        assertEquals("return ((arg*arg)+9);", code._stop.toString());
        assertEquals("13", Eval2.eval(code, 2));
    }

    // Function scope test
    @Test
    public void testFcn2A() {
        CodeGen code = new CodeGen(
"""
int cnt=1;                      // Global scope; exactly one of these
return { -> cnt++; };           // Escape global-scope variable is OK
""");
        code.parse().opto();
        assertEquals("return { -> 1 #1};", code._stop.toString());
        assertEquals("{ -> 1 #1}", Eval2.eval(code, 0));
    }

    // Function scope test
    @Test
    public void testFcn2B() {
        CodeGen code = new CodeGen(
"""
val ctr = { ->
    int cnt; // Function-local var
    return { -> cnt=cnt+1; cnt; };       // Escape cnt out of scope
};
val A = ctr();                  // Make a counter
val B = ctr();                  // Make a counter
A();                            // Increment counter A
return A()*10 + B();            // Incr A and B; result: 2*10+1 == 21
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Variable 'cnt' is out of function scope and must be a final constant",e.getMessage()); }
    }

    // Function scope test
    @Test
    public void testFcn3() {
        CodeGen code = new CodeGen(
"""
val cnt=2;
return { -> cnt; }();
""");
        code.parse().opto();
        assertEquals("return 2;", code._stop.toString());
        assertEquals("2", Eval2.eval(code, 0));
    }

    // Function variables
    @Test
    public void testFcn4() {
        CodeGen code = new CodeGen(
"""
var fcn = arg ? { int x -> x*x; } : { int x -> x+x; };
return fcn(3);
""");
        code.parse().opto();
        assertEquals("return #2;", code._stop.toString());
        assertEquals("6", Eval2.eval(code, 0));
        assertEquals("9", Eval2.eval(code, 1));
    }

    // Recursive factorial test
    @Test
    public void testFcn5() {
        CodeGen code = new CodeGen("val _fact = { int x -> x <= 1 ? 1 : x*_fact(x-1); }; return _fact(arg);");
        code.parse().opto().typeCheck();
        assertEquals("return Phi(Region,1,(arg*#2));", code._stop.toString());
        assertEquals( "1", Eval2.eval(code, 0));
        assertEquals( "1", Eval2.eval(code, 1));
        assertEquals( "2", Eval2.eval(code, 2));
        assertEquals( "6", Eval2.eval(code, 3));
        assertEquals("24", Eval2.eval(code, 4));
    }

    @Test
    public void testFcn6() {
        CodeGen code = new CodeGen(
"""
struct _S { int i; };
val _newS = { int x -> return new _S { i=x; }; };
return _newS(1).i;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 1;", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
    }

    // Only forward refs to direct function calls allowed
    @Test
    public void testFcn7() {
        CodeGen code = new CodeGen(
"""
val _f = {->1;};
val _g = {->2;};
if( arg ? _f : _g ) return 1;
return 2;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 1;", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
    }

    @Test
    public void testFcn8() {
        CodeGen code = new CodeGen(
"""
{int -> int}? _i2i = null;
var _id = {{int->int} f-> return f;};
for(;;) {
    if (_i2i) return _i2i(arg);
    var _x = {int i-> return i;};
    arg = _x(3);
    _i2i = _id(_x);
}
""");
        code.driver(Phase.LocalSched);
        assertEquals("return Phi(Loop,arg,3);", code._stop.toString());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testFcn9() {
        CodeGen code = new CodeGen(
"""
{int -> int}? i2i = null;
for(;;) {
    if (i2i) return i2i(arg);
    var x = {int i-> return i;};
    arg = x(3);
}
""");
        code.driver(Phase.LocalSched);
        assertEquals("Stop[ return Parm_i(x,i64); return Top; ]", code._stop.toString());
        assertEquals("null", Eval2.eval(code,  0));
    }


    @Test
    public void testFcn10() {
        CodeGen code = new CodeGen(
"""
struct _Person {
  int age;
};

val fcn = { _Person?[] ps, int x ->
  val tmp = ps[x];
  if( ps[x] )
    ps[x].age++;
};

var ps = new _Person?[2];
ps[0] = new _Person;
ps[1] = new _Person;
return fcn(ps,1);
""");
        code.driver(Phase.LocalSched);
        assertEquals("Stop[ return Phi(Region,.age,0); return 0; ]", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  0));
    }

    // Function break
    @Test
    public void testErr1() {
        CodeGen code = new CodeGen(
"""
for(;;) {
    val f = { ->
        break;
    };
    f();
    return 2;
}
return 1;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("No active loop for a break or continue",e.getMessage()); }
    }

    // Calling and inlining a null function
    @Test
    public void testErr2() {
        CodeGen code = new CodeGen(
"""
{int -> int}? !i2i = { int i -> return i; };
for(;;) {
    if (i2i(2) == arg) break;
    i2i = null;
}
return 0;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Might be null calling { i64 -> i64 #1}?",e.getMessage()); }
    }


    @Test
    public void testErr3() {
        CodeGen code = new CodeGen(
"""
val f = { int i, int j -> return i+j; };
return f();
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Expecting 2 arguments, but found 0",e.getMessage()); }
    }


    @Test
    public void testErr4() {
        CodeGen code = new CodeGen(
"""
struct S {
    {int} f = { -> x(); return 0; }; // Do not let fref x be a field
};
val x = { -> return 1; };
S? s = null;
for(;;) {
    if (s) return s.x;
}
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Accessing unknown field 'x' from '*Test.S'",e.getMessage()); }
    }

    @Test
    public void testErr5() {
        CodeGen code = new CodeGen(
"""
val g = { ->
    g();
};
return g();
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("No defined return type",e.getMessage()); }
    }


    // Mutual recursion.  Fails without SCCP to lift the recursive return types.
    @Ignore @Test
    public void testFcnMutRec() {
        CodeGen code = new CodeGen(
"""
val is_even = { int x -> x ? is_odd (x-1) : true ; };
val is_odd  = { int x -> x ? is_even(x-1) : false; };
return is_even(arg);
""");
        code.parse().opto().typeCheck();
        assertEquals("Stop[ return #2; return Phi(Region,0,1,#2); ]", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        assertEquals("0", Eval2.eval(code, 1));
        assertEquals("1", Eval2.eval(code, 2));
        assertEquals("0", Eval2.eval(code, 3));
    }

    // Forward ref to 'x' means that
    @Ignore @Test
    public void testForwardRef1() {
        CodeGen code = new CodeGen(
"""
struct S {
    { int } f = { -> return x(); };
};
val x = { -> return 1; };
S? s = null;
for(;;) {
    if (s) return s.f;
}
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  0));
    }

    // Inline hidden called more than once
    @Test
    public void testInline() {
        CodeGen code = new CodeGen(
"""
{int->int}?! _i2i_noInline = {int i->return i;};
{{int->int}->{int->int}}! _f2f = {{int->int} f->return f;};
val _o = _i2i_noInline;
if (arg) _i2i_noInline = null;
if (_i2i_noInline) return _i2i_noInline(arg);
return _f2f(_o)(1);
""");
        code.driver(Phase.LocalSched);
        assertEquals("return Phi(Region,#2,#2);", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  2));
    }

    @Test
    public void testOperField() {
        CodeGen code = new CodeGen(
"""
struct _Person {
    int coffee_count;
};
_Person !p = new _Person;
p.coffee_count += 1;
return p.coffee_count;
""");
        code.driver(Phase.LocalSched);
        assertEquals("return 1;", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  2));
    }

}
