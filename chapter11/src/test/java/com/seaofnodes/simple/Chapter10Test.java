package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.StoreNode;
import com.seaofnodes.simple.evaluator.Evaluator;
import java.util.BitSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter10Test {
    @Test public void testDeadReferenceReturn() {
        for( String body : new String[] {
            "return new S; return 0;",
            "if(1) return new S; else return 0;"
        } ) {
            String src = "struct S { int x; }; " + body;
            var stop = new Parser(src).parse().iterate();
            org.junit.Assert.assertTrue(body,
                stop.ret().expr()._type instanceof com.seaofnodes.simple.type.TypeMemPtr);
        }
    }



    // Issue #246: null-check guards start in Chapter 10; arrays arrive in Chapter 15.
    private static final String NULLABLE_POINT_SOURCE = """
        struct Point { int x; };
        Point point = new Point;
        point.x = 42;
        Point? p = null;
        if (arg) p = point;
        """;

    @Test
    public void testNullGuards() {
        for( String body : new String[] {
            "if (p != null) return p.x; return -1;",
            "if (null != p) return p.x; return -1;",
            "if (!!!!p) return p.x; return -1;",
            "if (!!!p) return -1; else return p.x;",
            "int b = !!p; if (b) return p.x + b - 1; return -1;"
        } ) {
            StopNode stop = new Parser(NULLABLE_POINT_SOURCE+body).parse().iterate();
            assertEquals(body,-1L,com.seaofnodes.simple.evaluator.Evaluator.evaluate(stop,0));
            assertEquals(body,42L,com.seaofnodes.simple.evaluator.Evaluator.evaluate(stop,1));
        }
    }

    @Test
    public void testNullGuardErrors() {
        for( String body : new String[] {
            "return p.x;",
            "if (p == null) return p.x; return -1;",
            "if (!!point) return p.x; return -1;",
            "if (!!p) { int x = p.x; } return p.x;"
        } ) {
            try {
                new Parser(NULLABLE_POINT_SOURCE+body).parse().iterate();
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
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testStruct() {
        Parser parser = new Parser("""
struct Bar {
    int a;
    int b;
}
struct Foo {
    int x;
}
Foo? foo = null;
Bar bar = new Bar;
bar.a = 1;
bar.a = 2;
return bar.a;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 2;", stop.toString());
    }

    @Test
    public void testExample() {
        Parser parser = new Parser("""
struct Vector2D { int x; int y; }
Vector2D v = new Vector2D;
v.x = 1;
if (arg)
    v.y = 2;
else
    v.y = 3;
return v;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return new Vector2D;", stop.toString());
    }

    @Test
    public void testBug() {
        Parser parser = new Parser("""
struct s0 {
    int v0;
}
s0? v1=null;
int v3=v1.zAicm;
""");
        try { parser.parse();  fail(); }
        catch( Exception e ) {  assertEquals("Accessing unknown field 'zAicm' from 'null'",e.getMessage());  }
    }

    @Test
    public void testBug2() {
        Parser parser = new Parser("""
struct s0 { int v0; }
arg=0+new s0.0;
""");
        try { parser.parse(); fail(); }
        catch( Exception e ) { assertEquals("Expected an identifier, found 'null'",e.getMessage()); }
    }

    @Test
    public void testLoop() {
        Parser parser = new Parser("""
struct Bar { int a; }
Bar bar = new Bar;
while (arg) {
    bar.a = bar.a + 2;
    arg = arg + 1;
}
return bar.a;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop12,0,(Phi_a+2));", stop.toString());
    }

    @Test
    public void testIf() {
        Parser parser = new Parser("""
struct Bar { int a; }
Bar bar = new Bar;
if (arg) bar = null;
bar.a = 1;
return bar.a;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *Bar",e.getMessage()); }
    }

    @Test
    public void testIf2() {
        Parser parser = new Parser("""
struct Bar { int a; }
Bar? bar = null;
if (arg) bar = new Bar;
bar.a = 1;
return bar.a;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Might be null accessing 'a'",e.getMessage()); }
    }

    @Test
    public void testIf3() {
        Parser parser = new Parser("""
struct Bar { int a; }
Bar bar = null;
if (arg) bar = null;
bar.a = 1;
return bar.a;
""");
        try { parser.parse(); fail(); }
        catch( Exception e ) { assertEquals("Type null is not of declared type *Bar", e.getMessage()); }
    }

    @Test
    public void testIfOrNull() {
        Parser parser = new Parser("""
struct Bar { int a; }
Bar? bar = new Bar;
if (arg) bar = null;
if( bar ) bar.a = 1;
return bar;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region17,null,new Bar);", stop.toString());
    }

    @Test
    public void testIfOrNull2() {
        Parser parser = new Parser(
"""
struct Bar { int a; }
Bar? bar = new Bar;
if (arg) bar = null;
int rez = 3;
if( !bar ) rez=4;
else bar.a = 1;
return rez;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Region35,4,3);", stop.toString());
    }

    @Test
    public void testWhileWithNullInside() {
        Parser parser = new Parser("""
struct s0 {int v0;}
s0? v0 = new s0;
int ret = 0;
while(arg) {
    ret = v0.v0;
    v0 = null;
    arg = arg - 1;
}
return ret;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) {
            assertEquals("Might be null accessing 'v0'", e.getMessage()); }
    }

    @Test
    public void testRedeclareStruct() {
        Parser parser = new Parser("""
struct s0 {
    int v0;
}
s0? v1=new s0;
s0? v1;
v1=new s0;
""");
        try { parser.parse(); fail(); }
        catch( Exception e ) { assertEquals("Redefining name 'v1'", e.getMessage()); }
    }

    @Test
    public void testIter() {
        // Build and use an iterator
        Parser parser = new Parser(
"""
struct Iter {
    int x;
    int len;
}
Iter i = new Iter;
i.len = arg;
int sum=0;
while( i.x < i.len ) {
    sum = sum + i.x;
    i.x = i.x + 1;
}
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop17,0,(Phi(Loop,0,(Phi_x+1))+Phi_sum));", stop.toString());
    }




    @Test
    public void test1() {
        Parser parser = new Parser("""
struct s0 {int v0;}
s0 ret = new s0;
while(arg) {
    s0 v0 = new s0;
    v0.v0 = arg;
    arg = arg-1;
    if (arg==5) ret=v0;

}
return ret;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop12,new s0,Phi(Region34,new s0,Phi_ret));", stop.toString());
    }

    @Test
    public void test2() {
        Parser parser = new Parser("""
struct s0 {int v0;}
s0 ret = new s0;
s0 v0 = new s0;
while(arg) {
    v0.v0 = arg;
    arg = arg-1;
    if (arg==5) ret=v0;

}
return ret;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop15,new s0,Phi(Region35,new s0,Phi_ret));", stop.toString());
    }


    @Test
    public void test3() {
        Parser parser = new Parser("""
struct s0 {int v0;}
s0 ret = new s0;
while(arg < 10) {
    s0 v0 = new s0;
    if (arg == 5) ret=v0;
    arg = arg + 1;
}
return ret;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop12,new s0,Phi(Region32,new s0,Phi_ret));", stop.toString());
    }

    @Test
    public void testBug3() {
        Parser parser = new Parser("""
struct s0 {
    int f0;
}
if(0>=0) return new s0;
return new s0;
int v0=null.f0;
""");
        try { parser.parse(); fail(); }
        catch( Exception e ) { assertEquals("Accessing unknown field 'f0' from 'null'", e.getMessage()); }
    }

    @Test
    public void testBug4() {
        Parser parser = new Parser("""
if(0) {
    while(0) if(arg) continue;
    int v0=0;
    while(1) {
        int arg=-arg;
        v0=arg;
    }
}
   """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testBug5() {
        Parser parser = new Parser("""
struct s0 {
    int f0;
}
if(0) return 0;
else return new s0;
if(new s0.f0) return 0;
    """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return new s0;", stop.toString());
    }

    @Test
    public void testBug6MissedWorklist() {
        Parser parser = new Parser("""
while(0) {}
int v4=0;
while(0<arg) {
    v4=v4+1;
    while(1) v4=-v4;
    while(0) arg=-1;
}
return 0;
    """);
        StopNode stop = parser.parse().iterate();
    }

    @Test
    public void testBug7() {
        Parser parser = new Parser("""
struct s0 {  int f0; }
s0 v0 = new s0;
while(v0.f0) {}
s0 v1 = v0;
return v1;
    """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return new s0;", stop.toString());
    }


    @Test
    public void testBug8() {
        Parser parser = new Parser("""
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
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testBug9() {
        Parser parser = new Parser("""
int v0=arg==0;
while(v0) continue;
return 0;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
    }

    @Test public void testReadBeforeStores() {
        var stop = new Parser("""
            struct S { int x; int y; }
            S a = new S; S b = new S;
            a.x = 11; b.x = 22;
            S p = a; if (arg) p = b;
            int before = p.x;
            a.y = 55; b.y = 66;
            a.x = 33; b.x = 44;
            return before*100 + p.x;
            """).parse().iterate();
        assertEquals(1133L, com.seaofnodes.simple.evaluator.Evaluator.evaluate(stop,0));
        assertEquals(2244L, com.seaofnodes.simple.evaluator.Evaluator.evaluate(stop,1));
    }

    static final String NESTED_MEMORY = """
            struct S { int x; int y; int z; }
            S s = new S;
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
        var stop = new Parser(NESTED_MEMORY).parse().iterate();
        for (int arg=0; arg<7; arg++) {
            var obj = (com.seaofnodes.simple.evaluator.Evaluator.Obj)
                com.seaofnodes.simple.evaluator.Evaluator.evaluate(stop,arg);
            org.junit.Assert.assertArrayEquals(new Object[] {
                5L+arg, 7L+arg*(arg+1)-(arg>=2 ? 2 : 0), 11L+2*arg-(arg>=2 ? 2 : 0)
            }, obj.fields());
        }
    }

    @Test public void testMemoryAtEarlyReturns() {
        var stop = new Parser("""
            struct S { int x; int y; int z; }
            S s = new S;
            s.x = 3; s.y = 5; s.z = 7;
            if (arg) { s.x = 11; s.z = 13; return s; }
            s.y = 17;
            return s;
            """).parse().iterate();
        for (int arg=0; arg<2; arg++) {
            var obj = (com.seaofnodes.simple.evaluator.Evaluator.Obj)
                com.seaofnodes.simple.evaluator.Evaluator.evaluate(stop,arg);
            org.junit.Assert.assertArrayEquals(arg==0 ? new Object[]{3L,17L,7L} : new Object[]{11L,5L,13L}, obj.fields());
        }
    }

    @Test public void testKeepLoadsAtMerge() {
        StopNode stop = new Parser("""
            struct S { int x; }
            S a = new S; S b = new S;
            a.x = arg; b.x = arg+1;
            S p = a; S q = b;
            if (arg<0) { p=b; q=a; }
            int v;
            if (arg>1) v=p.x; else v=q.x;
            return v;
            """).parse().iterate();
        // The one-step safety check stops at the memory aggregate.
        assertEquals(2,countMemoryNodes(stop,LoadNode.class,new BitSet()));
        assertEquals(-1L,Evaluator.evaluate(stop,-1));
        assertEquals(1L,Evaluator.evaluate(stop,0));
        assertEquals(3L,Evaluator.evaluate(stop,3));
    }

    @Test public void testDropStores() {
        StopNode stop = new Parser("""
            struct S { int x; }
            S s = new S;
            if (arg) s.x=arg+1; else s.x=arg+2;
            return s;
            """).parse().iterate();
        // These chapters still bind struct Stores to branch control.
        assertEquals(3,countMemoryNodes(stop,StoreNode.class,new BitSet()));
        assertEquals(2L,((Evaluator.Obj)Evaluator.evaluate(stop,0)).fields()[0]);
        assertEquals(4L,((Evaluator.Obj)Evaluator.evaluate(stop,3)).fields()[0]);
    }

    @Test public void testKeepReadsBeforeWrites() {
        StopNode stop = new Parser("""
            struct S { int x; int y; }
            S a = new S; S b = new S;
            a.x=arg; b.x=arg+1;
            S p=a; S q=b;
            if (arg<0) { p=b; q=a; }
            int v;
            if (arg>1) { v=p.x; p.x=41; p.y=5; }
            else       { v=q.x; q.x=42; }
            return v;
            """).parse().iterate();
        assertEquals(2,countMemoryNodes(stop,LoadNode.class,new BitSet()));
        assertEquals(-1L,Evaluator.evaluate(stop,-1));
        assertEquals(1L,Evaluator.evaluate(stop,0));
        assertEquals(3L,Evaluator.evaluate(stop,3));
    }

    private static int countMemoryNodes(Node n, Class<?> kind, BitSet seen) {
        if (n==null || seen.get(n._nid)) return 0;
        seen.set(n._nid);
        int cnt=kind.isInstance(n) ? 1 : 0;
        for (Node def : n._inputs) cnt+=countMemoryNodes(def,kind,seen);
        return cnt;
    }
}
