package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter13Test {
    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 3;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testLinkedList0() {
        CodeGen code = new CodeGen(
"""
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg ) {
    LLI !x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg-1;
}
return head.next.i;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Might be null accessing 'next'",e.getMessage()); }
    }

    @Test
    public void testLinkedList1() {
        CodeGen code = new CodeGen(
"""
struct _LLI { _LLI? next; int q; };
_LLI? !head = null;
while( arg ) {
    _LLI !x = new _LLI;
    x.next = head;
    x.q = arg;
    head = x;
    arg = arg-1;
}
if( !head ) return 0;
_LLI? next = head.next;
if( next==null ) return 1;
return next.q;
""");
        code.parse().opto().typeCheck();
        assertEquals("return Phi(Region,1,.q,0);", code.print());
        assertEquals("2", Eval2.eval(code,  3));
    }

    @Test
    public void testCoRecur() {
        CodeGen code = new CodeGen(
"""
struct _int0 { int i; _flt0? f; };
struct _flt0 { flt f; _int0? i; };
_int0 !i0 = new _int0;
i0.i = 17;
_flt0 !f0 = new _flt0;
f0.f = 3.14;
i0.f = f0;
f0.i = i0;
return f0.i.f.i.i;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 17;", code.print());
    }

    @Test
    public void testNullRef0() {
        CodeGen code = new CodeGen(
"""
struct _N { _N? next; int i; };
_N n = new _N;
return n.next;
""");
        code.parse().opto();
        assertEquals("return null;", code.print());
    }

    @Test
    public void testNullRef1() {
        CodeGen code = new CodeGen(
"""
struct M { int m; };
struct N { M next; int i; };
N n = new N { next = new M; };
return n.next;
""");
        code.parse().opto();
        assertEquals("Stop[ return (const)Test.M; return MEM[ 2:#!-2:0]; return MEM[ 2:___ 3:___ 4:___ 5:.next=(*Test.M)Bot; 6:#!-6:0]; ]", code.print());
    }

    @Test
    public void testNullRef2() {
        CodeGen code = new CodeGen(
"""
struct M { int m; };
struct N { M next; int i; };
N n = new N { next = null; };
return n.next;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *Test.M",e.getMessage()); }
    }

    @Test
    public void testNullRef3() {
        CodeGen code = new CodeGen(
"""
struct N { N? next; int i; };
N !n = new N;
n.i = 3.14;
return n.i;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Type 3.14 is not of declared type i64",e.getMessage()); }
    }

    @Test
    public void testNullRef4() {
        CodeGen code = new CodeGen("return -null-5/null-5;");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot '+' null",e.getMessage()); }
    }

    @Test public void testNullRef5() {
        CodeGen code = new CodeGen("return null+42;");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot '+' null",e.getMessage()); }
    }

    @Test
    public void testEmpty() {
        CodeGen code = new CodeGen(
"""
struct _S{};
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testForwardRef0() {
        CodeGen code = new CodeGen(
"""
struct S1 { S2? s; };
return new S2;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Unknown struct type 'Test.S2'",e.getMessage()); }
    }

    @Test
    public void testForwardRef1() {
        CodeGen code = new CodeGen(
"""
struct _S1 { _S2? s; };
struct _S2 { int x; };
return new _S1{}.s=new _S2;
""");
        code.parse().opto();
        assertEquals("return Test._S2;", code.print());
    }

    @Test
    public void testcheckNull() {
        CodeGen code = new CodeGen(
"""
struct I { int i; };
struct P { I? pi; };
P !p1 = new P;
P !p2 = new P;
p2.pi = new I;
p2.pi.i = 2;
if (arg) p1 = new P;
return p1.pi.i + 1;
""");
        try { code.parse().opto().typeCheck();  fail(); }
        catch( Exception e ) {  assertEquals("Might be null accessing 'i'",e.getMessage());  }
    }

    @Test
    public void testCoRecur2() {
        CodeGen code = new CodeGen(
"""
struct _A { _B? f0; _C? f1; };  _A !a = new _A;
struct _B { _C? f0; _A? f1; };  _B !b = new _B;
struct _C { _A? f0; _B? f1; };  _C !c = new _C;

a.f0=b;  a.f1=c;
b.f0=c;  b.f1=a;
c.f0=a;  c.f1=b;

return a.f0.f1.f0.f1.f0;

""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return Test._B;", code.print());
        assertEquals("Test._B{f0=Test._C{f0=Test._A{f0=$cyclic,f1=$cyclic},f1=$cyclic},f1=$cyclic}", Eval2.eval(code,  0));
    }

    @Test
    public void testCoRecur3() {
        CodeGen code = new CodeGen(
"""
struct _A { _L? a; _T? b; _F? c; };  _A !a = new _A;
struct _B { _M? a; _U? b; _G? c; };  _B !b = new _B;
struct _C { _N? a; _V? b; _H? c; };  _C !c = new _C;
struct _D { _O? a; _W? b; _I? c; };  _D !d = new _D;
struct _E { _P? a; _X? b; _J? c; };  _E !e = new _E;
struct _F { _Q? a; _Y? b; _K? c; };  _F !f = new _F;
struct _G { _R? a; _Z? b; _L? c; };  _G !g = new _G;
struct _H { _S? a; _A? b; _M? c; };  _H !h = new _H;
struct _I { _T? a; _B? b; _N? c; };  _I !i = new _I;
struct _J { _U? a; _C? b; _O? c; };  _J !j = new _J;
struct _K { _V? a; _D? b; _P? c; };  _K !k = new _K;
struct _L { _W? a; _E? b; _Q? c; };  _L !l = new _L;
struct _M { _X? a; _F? b; _R? c; };  _M !m = new _M;
struct _N { _Y? a; _G? b; _S? c; };  _N !n = new _N;
struct _O { _Z? a; _H? b; _T? c; };  _O !o = new _O;
struct _P { _A? a; _I? b; _U? c; };  _P !p = new _P;
struct _Q { _B? a; _J? b; _V? c; };  _Q !q = new _Q;
struct _R { _C? a; _K? b; _W? c; };  _R !r = new _R;
struct _S { _D? a; _L? b; _X? c; };  _S !s = new _S;
struct _T { _E? a; _M? b; _Y? c; };  _T !t = new _T;
struct _U { _F? a; _N? b; _Z? c; };  _U !u = new _U;
struct _V { _G? a; _O? b; _A? c; };  _V !v = new _V;
struct _W { _H? a; _P? b; _B? c; };  _W !w = new _W;
struct _X { _I? a; _Q? b; _C? c; };  _X !x = new _X;
struct _Y { _J? a; _R? b; _D? c; };  _Y !y = new _Y;
struct _Z { _K? a; _S? b; _E? c; };  _Z !z = new _Z;

a.a=l;  a.b=t; a.c=f;
b.a=m;  b.b=u; b.c=g;
c.a=n;  c.b=v; c.c=h;
d.a=o;  d.b=w; d.c=i;
e.a=p;  e.b=x; e.c=j;
f.a=q;  f.b=y; f.c=k;
g.a=r;  g.b=z; g.c=l;
h.a=s;  h.b=a; h.c=m;
i.a=t;  i.b=b; i.c=n;
j.a=u;  j.b=c; j.c=o;
k.a=v;  k.b=d; k.c=p;
l.a=w;  l.b=e; l.c=q;
m.a=x;  m.b=f; m.c=r;
n.a=y;  n.b=g; n.c=s;
o.a=z;  o.b=h; o.c=t;
p.a=a;  p.b=i; p.c=u;
q.a=b;  q.b=j; q.c=v;
r.a=c;  r.b=k; r.c=w;
s.a=d;  s.b=l; s.c=x;
t.a=e;  t.b=m; t.c=y;
u.a=f;  u.b=n; u.c=z;
v.a=g;  v.b=o; v.c=a;
w.a=h;  w.b=p; w.c=b;
x.a=i;  x.b=q; x.c=c;
y.a=j;  y.b=r; y.c=d;
z.a=k;  z.b=s; z.c=e;

return a.b.c.a.b.c.a.b.c.a.b.c.a.b.c.a.b.c;

""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return Test._R;", code.print());
        assertEquals("Test._R{a=Test._C{a=Test._N{a=Test._Y{a=Test._J{a=Test._U{a=Test._F{a=Test._Q{a=Test._B{a=Test._M{a=Test._X{a=Test._I{a=Test._T{a=Test._E{a=Test._P{a=Test._A{a=Test._L{a=Test._W{a=Test._H{a=Test._S{a=Test._D{a=Test._O{a=Test._Z{a=Test._K{a=Test._V{a=Test._G{a=$cyclic,b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic}", Eval2.eval(code,  0));
    }
}
