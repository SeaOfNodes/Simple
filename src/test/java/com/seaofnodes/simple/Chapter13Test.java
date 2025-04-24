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
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
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
struct LLI { LLI? next; int i; };
LLI? !head = null;
while( arg ) {
    LLI !x = new LLI;
    x.next = head;
    x.i = arg;
    head = x;
    arg = arg-1;
}
if( !head ) return 0;
LLI? next = head.next;
if( next==null ) return 1;
return next.i;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,0,1,.i);", code.print());
        assertEquals("2", Eval2.eval(code,  3));
    }

    @Test
    public void testCoRecur() {
        CodeGen code = new CodeGen(
"""
struct int0 { int i; flt0? f; };
struct flt0 { flt f; int0? i; };
int0 !i0 = new int0;
i0.i = 17;
flt0 !f0 = new flt0;
f0.f = 3.14;
i0.f = f0;
f0.i = i0;
return f0.i.f.i.i;
""");
        code.parse().opto();
        assertEquals("return 17;", code.print());
    }

    @Test
    public void testNullRef0() {
        CodeGen code = new CodeGen(
"""
struct N { N? next; int i; };
N n = new N;
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
        assertEquals("return (const)M;", code.print());
    }

    @Test
    public void testNullRef2() {
        CodeGen code = new CodeGen(
"""
struct M { int m; };
struct N { M next; int i; };
N n = new N { next = null; }
return n.next;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *M",e.getMessage()); }
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
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Type 3.14 is not of declared type int",e.getMessage()); }
    }

    @Test
    public void testNullRef4() {
        CodeGen code = new CodeGen("-null-5/null-5;");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot 'Add' null",e.getMessage()); }
    }

    @Test public void testNullRef5() {
        CodeGen code = new CodeGen("return null+42;");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot 'Add' null",e.getMessage()); }
    }

    @Test
    public void testEmpty() {
        CodeGen code = new CodeGen(
"""
struct S{};
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
        catch( Exception e ) { assertEquals("Unknown struct type 'S2'",e.getMessage()); }
    }

    @Test
    public void testForwardRef1() {
        CodeGen code = new CodeGen(
"""
struct S1 { S2? s; };
struct S2 { int x; };
return new S1.s=new S2;
""");
        code.parse().opto();
        assertEquals("return S2;", code.print());
    }

    @Test
    public void testcheckNull() {
        CodeGen code = new CodeGen(
"""
struct I {int i;};
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
struct A { L? a; T? b; F? c; };  A? !a = new A;
struct B { M? a; U? b; G? c; };  B? !b = new B;
struct C { N? a; V? b; H? c; };  C? !c = new C;
struct D { O? a; W? b; I? c; };  D? !d = new D;
struct E { P? a; X? b; J? c; };  E? !e = new E;
struct F { Q? a; Y? b; K? c; };  F? !f = new F;
struct G { R? a; Z? b; L? c; };  G? !g = new G;
struct H { S? a; A? b; M? c; };  H? !h = new H;
struct I { T? a; B? b; N? c; };  I? !i = new I;
struct J { U? a; C? b; O? c; };  J? !j = new J;
struct K { V? a; D? b; P? c; };  K? !k = new K;
struct L { W? a; E? b; Q? c; };  L? !l = new L;
struct M { X? a; F? b; R? c; };  M? !m = new M;
struct N { Y? a; G? b; S? c; };  N? !n = new N;
struct O { Z? a; H? b; T? c; };  O? !o = new O;
struct P { A? a; I? b; U? c; };  P? !p = new P;
struct Q { B? a; J? b; V? c; };  Q? !q = new Q;
struct R { C? a; K? b; W? c; };  R? !r = new R;
struct S { D? a; L? b; X? c; };  S? !s = new S;
struct T { E? a; M? b; Y? c; };  T? !t = new T;
struct U { F? a; N? b; Z? c; };  U? !u = new U;
struct V { G? a; O? b; A? c; };  V? !v = new V;
struct W { H? a; P? b; B? c; };  W? !w = new W;
struct X { I? a; Q? b; C? c; };  X? !x = new X;
struct Y { J? a; R? b; D? c; };  Y? !y = new Y;
struct Z { K? a; S? b; E? c; };  Z? !z = new Z;

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
        assertEquals("return R;", code._stop.toString());
        assertEquals("R{a=C{a=N{a=Y{a=J{a=U{a=F{a=Q{a=B{a=M{a=X{a=I{a=T{a=E{a=P{a=A{a=L{a=W{a=H{a=S{a=D{a=O{a=Z{a=K{a=V{a=G{a=$cyclic,b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic},b=$cyclic,c=$cyclic}", Eval2.eval(code,  0));
    }

}
