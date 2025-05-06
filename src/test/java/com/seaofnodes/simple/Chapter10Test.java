package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter10Test {

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
Bar !bar = new Bar;
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
Vector2D !v = new Vector2D;
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
Bar !bar = new Bar;
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
Bar !bar = new Bar;
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
Bar? !bar = null;
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
Bar bar = null;
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
Bar? !bar = new Bar;
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
Bar? !bar = new Bar;
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
s0? !v0 = new s0;
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
s0? v1;
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
Iter !i = new Iter;
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
s0 !ret = new s0;
while(arg) {
    s0 !v0 = new s0;
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
s0 !ret = new s0;
s0 !v0 = new s0;
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
s0 !ret = new s0;
while(arg < 10) {
    s0 !v0 = new s0;
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
        assertEquals("return s0;", code.print());
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

}
