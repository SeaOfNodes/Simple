package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter14Test {

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
    public void testRange() {
        CodeGen code = new CodeGen(
"""
int b;
if( arg ) b=1; else b=0;
int c = 99;
if( b < 0 ) c = -1;
if( b > 2 ) c =  1;
return c;
""");
        code.parse().opto();
        assertEquals("return 99;", code.print());
        assertEquals("99", Eval2.eval(code,  0));
    }

    @Test
    public void testU8() {
        CodeGen code = new CodeGen(
"""
u8 b = 123;
b = b + 456;// Truncate
return b;
""");
        code.parse().opto();
        assertEquals("return 67;", code.print());
        assertEquals("67", Eval2.eval(code,  0));
    }


    @Test
    public void testU8While() {
        CodeGen code = new CodeGen(
"""
u8 b = 123;
while( b ) b = b + 456;// Truncate
return b;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testU1() {
        CodeGen code = new CodeGen(
"""
bool b = 123;
b = b + 456;// Truncate
u1 c = b;   // No more truncate needed
return c;
""");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code,  0));
    }

    @Test
    public void testAnd() {
        CodeGen code = new CodeGen(
"""
int b = 123;
b = b+456 & 31;                 // Precedence
return b;
""");
        code.parse().opto();
        assertEquals("return 3;", code.print());
        assertEquals("3", Eval2.eval(code,  0));
    }

    @Test
    public void testRefLoad() {
        CodeGen code = new CodeGen(
"""
struct Foo { u1 b; };
Foo !f = new Foo;
f.b = 123;
return f.b;
""");
        code.parse().opto();
        assertEquals("return 1;", code.print());
        assertEquals("1", Eval2.eval(code,  0));
    }

    @Test
    public void testSigned() {
        CodeGen code = new CodeGen(
"""
i8 b = 255;                     // Chopped
return b;                       // Sign extend
""");
        code.parse().opto();
        assertEquals("return -1;", code.print());
        assertEquals("-1", Eval2.eval(code,  0));
    }

    @Test
    public void testI8() {
        CodeGen code = new CodeGen(
"""
i8 b = arg;
b = b + 1;// Truncate
return b;
""");
        code.parse().opto();
        assertEquals("return (((((arg<<56)>>56)+1)<<56)>>56);", code.print());
        assertEquals("1", Eval2.eval(code, 0));
        assertEquals("-128", Eval2.eval(code, 127));
    }

    @Test
    public void testMask() {
        CodeGen code = new CodeGen(
"""
u16 mask = (1<<16)-1;           // AND mask
int c = 123456789 & mask;
return c;                       //
""");
        code.parse().opto();
        assertEquals("return 52501;", code.print());
        assertEquals("52501", Eval2.eval(code,  0));
    }

    @Test
    public void testOr() {
        CodeGen code = new CodeGen(
"""
return (arg | 123 ^ 456) >>> 1;
""");
        code.parse().opto();
        assertEquals("return (((arg|123)^456)>>>1);", code.print());
        assertEquals("217", Eval2.eval(code,  0));
    }

    @Test
    public void testMaskFloat() {
        CodeGen code = new CodeGen(
"""
flt f = arg;
arg = f & 1;
return arg;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Cannot '&' flt",e.getMessage()); }
    }

    @Test
    public void testCloneAnd() {
        CodeGen code = new CodeGen("""
int v0=0;
u32 v1 = 1&(1<<arg)&(1<<arg);
while(arg) v1=-v0;
while(v1) break;
return v1;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,(((1<<arg)&1)&Shl),0);", code.print());
        assertEquals("1", Eval2.eval(code,  0));
    }

    @Test
    public void testAndHigh() {
        CodeGen code = new CodeGen("""
int v0=0;
if(0&0>>>0) {
    while(0) {
        u8 v1=0;
        v0=0>>>0;
        v1=arg;
        while(v1+0) {}
    }
}
return v0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

    @Test
    public void testTypes() {
        CodeGen code = new CodeGen(
"""
i8  xi8  = 123456789;  if( xi8  !=        21 ) return -8;
i16 xi16 = 123456789;  if( xi16 !=    -13035 ) return -16;
i32 xi32 = 123456789;  if( xi32 != 123456789 ) return -32;
i64 xi64 = 123456789;  if( xi64 != 123456789 ) return -64;
int xint = 123456789;  if( xint != 123456789 ) return -64;

u1  ui1  = 123456789;  if( ui1  !=         1 ) return 1;
u8  ui8  = 123456789;  if( ui8  !=        21 ) return 8;
u16 ui16 = 123456789;  if( ui16 !=     52501 ) return 16;
u32 ui32 = 123456789;  if( ui32 != 123456789 ) return 32;

flt fflt = 3.141592653589793;  if( fflt != 3.141592653589793 ) return 3;
f64 ff64 = 3.141592653589793;  if( ff64 != 3.141592653589793 ) return 3;
f32 ff32 = 3.141592653589793;  if( ff32 != 3.1415927410125732) return 5;

return 0;
""");
        code.parse().opto();
        assertEquals("return 0;", code.print());
        assertEquals("0", Eval2.eval(code,  0));
    }

}
