package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import com.seaofnodes.simple.node.*;
import java.util.BitSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter10Test {
    @Test public void testDeadReferenceReturn() {
        for( String body : new String[] {
            "return new S; return 0;",
            "if(1) return new S; else return 0;"
        } ) {
            String src = "struct S { int x; }; " + body;
            var code = new CodeGen(src).parse().opto().typeCheck();
            org.junit.Assert.assertTrue(body,
                code.expr()._type instanceof com.seaofnodes.simple.type.TypeMemPtr);
        }
    }



    // Issue #246: null-check guards start in Chapter 10; arrays arrive in Chapter 15.
    private static final String NULLABLE_POINT_SOURCE = """
        struct Point { int x; };
        !Point?[] !points = new !Point?[2];
        points[arg] = new Point { x = 42; };
        Point? p = points[1];
        """;

    @Test
    public void testNullGuards() {
        for( String body : new String[] {
            "if (p != null) return p.x; return -1;",
            "if (null != p) return p.x; return -1;",
            "if (!!!!p) return p.x; return -1;",
            "if (!!!p) return -1; return p.x;",
            "int b = !!p; if (b) return p.x + b - 1; return -1;",
            "if (!(p == null || arg == 0)) return p.x; return -1;"
        } ) {
            CodeGen code = new CodeGen(NULLABLE_POINT_SOURCE+body).parse().opto().typeCheck();
            assertEquals(body,"-1",Eval2.eval(code,0));
            assertEquals(body,"42",Eval2.eval(code,1));
        }
    }

    @Test
    public void testShortCircuitGuardScheduling() {
        // The call result exists only on the RHS path, not above the merge.
        CodeGen code = new CodeGen("""
            val f = { int n -> (n+1)&7; };
            int x = 0;
            if (!!(arg && (x=f(arg)))) return x;
            return -1;
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("-1",Eval2.eval(code,0));
        assertEquals("2",Eval2.eval(code,1));
        assertEquals("-1",Eval2.eval(code,7));
        code.driver(CodeGen.Phase.LocalSched);
    }

    @Test
    public void testNullGuardErrors() {
        for( String body : new String[] {
            "return p.x;",
            "if (p == null) return p.x; return -1;",
            "if (!!points) return p.x; return -1;",
            "if (!!p) { int x = p.x; } return p.x;"
        } ) {
            try {
                new CodeGen(NULLABLE_POINT_SOURCE+body).parse().opto().typeCheck();
                fail(body);
            } catch( RuntimeException e ) {
                // Known null is rejected during parsing in these chapters.
                String expected = body.startsWith("if (p == null)")
                    ? "Accessing unknown field 'x' from 'null'" : "Might be null accessing 'x'";
                assertEquals(body,expected,e.getMessage());
            }
        }
    }

    @Test
    public void testFuzzer() {
        CodeGen code = new CodeGen(
"""
int a = arg/3;
int b = arg*5;
int x = arg*7;
int y = arg/11;
int p; int g; int h;
if( (arg/13)==0 ) {
    p = x + y;
    g = x;
    h = y;
} else {
    p = a + b;
    g = a;
    h = b;
}
int r = g+h;
return p-r;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testStruct() {
        CodeGen code = new CodeGen("""
struct Bar {
    int a;
    int b;
};
struct Foo {
    int x;
};
Foo? foo = null;
!Bar !bar = new Bar;
bar.a = 1;
bar.a = 2;
return bar.a;
""");
        code.parse().opto();
        assertEquals("return 2;", code.print());
    }

    @Test
    public void testExample() {
        CodeGen code = new CodeGen("""
struct Vector2D { int x; int y; };
!Vector2D !v = new Vector2D;
v.x = 1;
if (arg)
    v.y = 2;
else
    v.y = 3;
return v;
""");
        code.parse().opto();
        assertEquals("return Vector2D;", code.print());
    }

    @Test
    public void testBug() {
        CodeGen code = new CodeGen("""
struct s0 {
    int v0;
};
s0? v1=null;
int v3=v1.zAicm;
""");
        try { code.parse();  fail(); }
        catch( Exception e ) {  assertEquals("Accessing unknown field 'zAicm' from 'null'",e.getMessage());  }
    }

    @Test
    public void testBug2() {
        CodeGen code = new CodeGen("""
struct s0 { int v0; };
arg=0+new s0.0;
""");
        try { code.parse(); fail(); }
        catch( Exception e ) { assertEquals("Expected an identifier, found 'null'",e.getMessage()); }
    }

    @Test
    public void testLoop() {
        CodeGen code = new CodeGen("""
struct Bar { int a; };
!Bar !bar = new Bar;
while (arg) {
    bar.a = bar.a + 2;
    arg = arg + 1;
}
return bar.a;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi_a+2));", code.print());
    }

    @Test
    public void testIf() {
        CodeGen code = new CodeGen("""
struct Bar { int a; };
!Bar !bar = new Bar;
if (arg) bar = null;
bar.a = 1;
return bar.a;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *Bar",e.getMessage()); }
    }

    @Test
    public void testIf2() {
        CodeGen code = new CodeGen("""
struct Bar { int a; };
!Bar? !bar = null;
if (arg) bar = new Bar;
bar.a = 1;
return bar.a;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Might be null accessing 'a'",e.getMessage()); }
    }

    @Test
    public void testIf3() {
        CodeGen code = new CodeGen("""
struct Bar { int a; };
!Bar !bar = null;
if (arg) bar = null;
bar.a = 1;
return bar.a;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *Bar", e.getMessage()); }
    }

    @Test
    public void testIfOrNull() {
        CodeGen code = new CodeGen("""
struct Bar { int a; };
!Bar? !bar = new Bar;
if (arg) bar = null;
if( bar ) bar.a = 1;
return bar;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,null,Bar);", code.print());
    }

    @Test
    public void testIfOrNull2() {
        CodeGen code = new CodeGen(
"""
struct Bar { int a; };
!Bar? !bar = new Bar;
if (arg) bar = null;
int rez = 3;
if( !bar ) rez=4;
else bar.a = 1;
return rez;
""");
        code.parse().opto();
        assertEquals("return Phi(Region,4,3);", code.print());
    }

    @Test
    public void testWhileWithNullInside() {
        CodeGen code = new CodeGen("""
struct s0 {int v0;};
!s0? !v0 = new s0;
int ret = 0;
while(arg) {
    ret = v0.v0;
    v0 = null;
    arg = arg - 1;
}
return ret;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) {
            assertEquals("Might be null accessing 'v0'", e.getMessage()); }
    }

    @Test
    public void testRedeclareStruct() {
        CodeGen code = new CodeGen("""
struct s0 {
    int v0;
};
s0? v1=new s0;
!s0? !v1;
v1=new s0;
""");
        try { code.parse(); fail(); }
        catch( Exception e ) { assertEquals("Redefining name 'v1'", e.getMessage()); }
    }

    @Test
    public void testIter() {
        // Build and use an iterator
        CodeGen code = new CodeGen(
"""
struct Iter {
    int x;
    int len;
};
!Iter !i = new Iter;
i.len = arg;
int sum=0;
while( i.x < i.len ) {
    sum = sum + i.x;
    i.x = i.x + 1;
}
return sum;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,0,(Phi(Loop,0,(Phi_x+1))+Phi_sum));", code.print());
    }


    @Test
    public void test1() {
        CodeGen code = new CodeGen("""
struct s0 {int v0;};
!s0 !ret = new s0;
while(arg) {
    !s0 !v0 = new s0;
    v0.v0 = arg;
    arg = arg-1;
    if (arg==5) ret=v0;

}
return ret;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,s0,Phi(Region,s0,Phi_ret));", code.print());
    }

    @Test
    public void test2() {
        CodeGen code = new CodeGen("""
struct s0 {int v0;};
!s0 !ret = new s0;
!s0 !v0 = new s0;
while(arg) {
    v0.v0 = arg;
    arg = arg-1;
    if (arg==5) ret=v0;

}
return ret;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,s0,Phi(Region,s0,Phi_ret));", code.print());
    }


    @Test
    public void test3() {
        CodeGen code = new CodeGen("""
struct s0 {int v0;};
!s0 !ret = new s0;
while(arg < 10) {
    !s0 !v0 = new s0;
    if (arg == 5) ret=v0;
    arg = arg + 1;
}
return ret;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,s0,Phi(Region,s0,Phi_ret));", code.print());
    }

    @Test
    public void testBug3() {
        CodeGen code = new CodeGen("""
struct s0 { int f0; };
return new s0;
int v0=null.f0;
""");
        try { code.parse();  fail(); }
        catch( Exception e ) {  assertEquals("Syntax error, expected ;: .",e.getMessage());  }
    }

    @Test
    public void testBug4() {
        CodeGen code = new CodeGen("""
if(0) {
    while(0) if(arg) continue;
    int v0=0;
    while(1) {
        int v2=-arg;
        v0=v2;
    }
}
return 0;
   """);
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testBug5() {
        CodeGen code = new CodeGen("""
struct s0 {
    int f0;
};
if(0) return 0;
else return new s0;
if(new s0.f0) return 0;
    """);
        code.parse().opto();
        assertEquals("return s0;", code.print());
    }

    @Test
    public void testBug6MissedWorklist() {
        CodeGen code = new CodeGen("""
while(0) {}
int v4=0;
while(0<arg) {
    v4=v4+1;
    while(1) v4=-v4;
    while(0) arg=-1;
}
return 0;
    """);
        code.parse().opto();
    }

    @Test
    public void testBug7() {
        CodeGen code = new CodeGen("""
struct s0 {  int f0; };
s0 v0 = new s0;
while(v0.f0) {}
s0 v1 = v0;
return v1;
    """);
        code.parse().opto();
        assertEquals("return (const)s0;", code.print());
    }


    @Test
    public void testBug8() {
        CodeGen code = new CodeGen("""
int v2=0;
while(0)
while(0) {}
{
    {
        {
            int v36=0;
            {
                while(0) {
                    {
                        while(-v2) {
                            {
                                while(v36) {
                                                while(v2) return 0;
                                                break;
                                }                            }
                            if(-v2) break;
                        }
                    }
                }
            }
        }    }
}
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testBug9() {
        CodeGen code = new CodeGen("""
int v0=arg==0;
while(v0) continue;
return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test public void testReadBeforeStores() {
        var code = new CodeGen("""
            struct S { int x; int y; };
            !S !a = new S; !S !b = new S;
            a.x = 11; b.x = 22;
            !S !p = a; if (arg) p = b;
            int before = p.x;
            a.y = 55; b.y = 66;
            a.x = 33; b.x = 44;
            return before*100 + p.x;
            """).parse().opto();
        StopNode stop = code._stop;
        assertEquals("1133",Eval2.eval(code,0));
        assertEquals("2244",Eval2.eval(code,1));
    }

    static final String NESTED_MEMORY = """
            struct S { int x; int y; int z; };
            !S !s = new S;
            s.x = 5; s.y = 7; s.z = 11;
            while (arg > 0) {
                s.x = s.x + 1;
                int j = 3;
                while (j > 0) {
                    j = j - 1;
                    if (j == 1) continue;
                    s.y = s.y + arg;
                    if (arg == 2) break;
                    s.z = s.z + 1;
                }
                arg = arg - 1;
            }
            return s;
            """;

    @Test public void testMemoryAcrossNestedLoops() {
        var code = new CodeGen(NESTED_MEMORY).parse().opto();
        StopNode stop = code._stop;
        for (int arg=0; arg<7; arg++) {
            assertEquals("S{x="+(5L+arg)+",y="+(7L+arg*(arg+1)-(arg>=2 ? 2 : 0))+
                         ",z="+(11L+2*arg-(arg>=2 ? 2 : 0))+"}",Eval2.eval(code,arg));
        }
    }

    @Test public void testMemoryAtEarlyReturns() {
        var code = new CodeGen("""
            struct S { int x; int y; int z; };
            !S !s = new S;
            s.x = 3; s.y = 5; s.z = 7;
            if (arg) { s.x = 11; s.z = 13; return s; }
            s.y = 17;
            return s;
            """).parse().opto();
        StopNode stop = code._stop;
        for (int arg=0; arg<2; arg++) {
            assertEquals(arg==0 ? "S{x=3,y=17,z=7}" : "S{x=11,y=5,z=13}",Eval2.eval(code,arg));
        }
    }

    @Test public void testKeepLoadsAtMerge() {
        var code = new CodeGen("""
            struct S { int x; };
            !S !a = new S; !S !b = new S;
            a.x = arg; b.x = arg+1;
            !S !p = a; !S !q = b;
            if (arg<0) { p=b; q=a; }
            int v;
            if (arg>1) v=p.x; else v=q.x;
            return v;
            """).parse().opto();
        StopNode stop = code._stop;
        // The one-step safety check stops at the memory aggregate.
        assertEquals(2,countMemoryNodes(stop,LoadNode.class,new BitSet()));
        assertEquals("-1",Eval2.eval(code,-1));
        assertEquals("1",Eval2.eval(code,0));
        assertEquals("3",Eval2.eval(code,3));
    }

    @Test public void testDropStores() {
        var code = new CodeGen("""
            struct S { int x; };
            !S !s = new S;
            if (arg) s.x=arg+1; else s.x=arg+2;
            return s;
            """).parse().opto();
        StopNode stop = code._stop;
        assertEquals(1,countMemoryNodes(stop,StoreNode.class,new BitSet()));
        assertEquals("S{x=2}",Eval2.eval(code,0));
        assertEquals("S{x=4}",Eval2.eval(code,3));
    }

    @Test public void testKeepReadsBeforeWrites() {
        var code = new CodeGen("""
            struct S { int x; int y; };
            !S !a = new S; !S !b = new S;
            a.x=arg; b.x=arg+1;
            !S !p=a; !S !q=b;
            if (arg<0) { p=b; q=a; }
            int v;
            if (arg>1) { v=p.x; p.x=41; p.y=5; }
            else       { v=q.x; q.x=42; }
            return v;
            """).parse().opto();
        StopNode stop = code._stop;
        assertEquals(2,countMemoryNodes(stop,LoadNode.class,new BitSet()));
        assertEquals("-1",Eval2.eval(code,-1));
        assertEquals("1",Eval2.eval(code,0));
        assertEquals("3",Eval2.eval(code,3));
    }

    private static int countMemoryNodes(Node n, Class<?> kind, BitSet seen) {
        if (n==null || seen.get(n._nid)) return 0;
        seen.set(n._nid);
        int cnt=kind.isInstance(n) ? 1 : 0;
        for (Node def : n._inputs) cnt+=countMemoryNodes(def,kind,seen);
        return cnt;
    }
}
