package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter10Test {

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
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 0;", stop.toString());
    }

    @Test
    public void testStruct() {
        Parser parser = new Parser("""
struct Bar {
    int a;
    int b;
};
struct Foo {
    int x;
};
Foo? foo = null;
Bar bar = new Bar;
bar.a = 1;
bar.a = 2;
return bar.a;
""");
        StopNode stop = parser.parse(false).iterate();
        assertEquals("return 2;", stop.toString());
    }

    @Test
    public void testExample() {
        Parser parser = new Parser("""
struct Vector2D { int x; int y; };
Vector2D v = new Vector2D;
v.x = 1;
if (arg)
    v.y = 2;
else
    v.y = 3;
return v;
""");
        StopNode stop = parser.parse(false).iterate();
        assertEquals("return Vector2D;", stop.toString());
    }

    @Test
    public void testBug() {
        Parser parser = new Parser("""
struct s0 {
    int v0;
};
s0? v1=null;
int v3=v1.zAicm;
""");
        try { parser.parse();  fail(); }
        catch( Exception e ) {  assertEquals("Accessing unknown field 'zAicm' from 'null'",e.getMessage());  }
    }

    @Test
    public void testBug2() {
        Parser parser = new Parser("""
struct s0 { int v0; };
arg=0+new s0.0;
""");
        try { parser.parse(); fail(); }
        catch( Exception e ) { assertEquals("Expected an identifier, found 'null'",e.getMessage()); }
    }

    @Test
    public void testLoop() {
        Parser parser = new Parser("""
struct Bar { int a; };
Bar bar = new Bar;
while (arg) {
    bar.a = bar.a + 2;
    arg = arg + 1;
}
return bar.a;
""");
        StopNode stop = parser.parse(false).iterate();
        assertEquals("return Phi(Loop15,0,(Phi_a+2));", stop.toString());
    }

    @Test
    public void testIf() {
        Parser parser = new Parser("""
struct Bar { int a; };
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
struct Bar { int a; };
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
struct Bar { int a; };
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
struct Bar { int a; };
Bar? bar = new Bar;
if (arg) bar = null;
if( bar ) bar.a = 1;
return bar;
""");
        StopNode stop = parser.parse(false).iterate();
        assertEquals("return Phi(Region35,(*void)Phi(Region22,null,Bar),null);", stop.toString());
    }

    @Test
    public void testIfOrNull2() {
        Parser parser = new Parser(
"""
struct Bar { int a; };
Bar? bar = new Bar;
if (arg) bar = null;
int rez = 3;
if( !bar ) rez=4;
else bar.a = 1;
return rez;
""");
        StopNode stop = parser.parse(false).iterate();
        assertEquals("return Phi(Region41,4,3);", stop.toString());
    }

    @Test
    public void testWhileWithNullInside() {
        Parser parser = new Parser("""
struct s0 {int v0;};
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
};
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
};
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
        assertEquals("return Phi(Loop21,0,(Phi(Loop,0,(Phi_x+1))+Phi_sum));", stop.toString());
    }


    @Test
    public void test1() {
        Parser parser = new Parser("""
struct s0 {int v0;};
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
        assertEquals("return Phi(Loop15,s0,Phi(Region39,s0,Phi_ret));", stop.toString());
    }

    @Test
    public void test2() {
        Parser parser = new Parser("""
struct s0 {int v0;};
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
        assertEquals("return Phi(Loop19,s0,Phi(Region40,s0,Phi_ret));", stop.toString());
    }


    @Test
    public void test3() {
        Parser parser = new Parser("""
struct s0 {int v0;};
s0 ret = new s0;
while(arg < 10) {
    s0 v0 = new s0;
    if (arg == 5) ret=v0;
    arg = arg + 1;
}
return ret;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop15,s0,Phi(Region37,s0,Phi_ret));", stop.toString());
    }

    @Test
    public void testBug3() {
        Parser parser = new Parser("""
struct s0 {
    int f0;
};
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
};
if(0) return 0;
else return new s0;
if(new s0.f0) return 0;
    """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return s0;", stop.toString());
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
struct s0 {  int f0; };
s0 v0 = new s0;
while(v0.f0) {}
s0 v1 = v0;
return v1;
    """);
        StopNode stop = parser.parse().iterate();
        assertEquals("return s0;", stop.toString());
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

}
