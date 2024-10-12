package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter14Test {

    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testRange() {
        Parser parser = new Parser(
"""
int b;
if( arg ) b=1; else b=0;
int c = 99;
if( b < 0 ) c = -1;
if( b > 2 ) c =  1;
return c;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 99;", stop.toString());
        assertEquals(99L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testU8() {
        Parser parser = new Parser(
"""
u8 b = 123;
b = b + 456;// Truncate
return b;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 67;", stop.toString());
        assertEquals(67L, Evaluator.evaluate(stop,  0));
    }


    @Test
    public void testU8While() {
        Parser parser = new Parser(
"""
u8 b = 123;
while( b ) b = b + 456;// Truncate
return b;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop11,123,((Phi_b+456)&255));", stop.toString());
    }

    @Test
    public void testU1() {
        Parser parser = new Parser(
"""
bool b = 123;
b = b + 456;// Truncate
u1 c = b;   // No more truncate needed
return c;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testAnd() {
        Parser parser = new Parser(
"""
int b = 123;
b = b+456 & 31;                 // Precedence
return b;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3;", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testRefLoad() {
        Parser parser = new Parser(
"""
struct Foo { u1 b; };
Foo f = new Foo;
f.b = 123;
return f.b;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testSigned() {
        Parser parser = new Parser(
"""
i8 b = 255;                     // Chopped
return b;                       // Sign extend
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return -1;", stop.toString());
        assertEquals(-1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testI8() {
        Parser parser = new Parser(
"""
i8 b = arg;
b = b + 1;// Truncate
return b;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (((((arg<<56)>>56)+1)<<56)>>56);", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
        assertEquals(-128L, Evaluator.evaluate(stop, 127));
    }

    @Test
    public void testMask() {
        Parser parser = new Parser(
"""
u16 mask = (1<<16)-1;           // AND mask
int c = 123456789 & mask;
return c;                       //
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 52501;", stop.toString());
        assertEquals(52501L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testOr() {
        Parser parser = new Parser(
"""
return (arg | 123 ^ 456) >>> 1;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (((arg|123)^456)>>>1);", stop.toString());
        assertEquals(217L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testMaskFloat() {
        Parser parser = new Parser(
"""
flt f = arg;
arg = f & 0;
return arg;
""");
        try { parser.parse().iterate(); fail(); }
        catch( Exception e ) { assertEquals("Cannot '&' FltBot",e.getMessage()); }
    }

    @Test
    public void testCloneAnd() {
        Parser parser = new Parser("""
int v0=0;
u32 v1 = 1&(1<<arg)&(1<<arg);
while(arg) v1=-v0;
while(v1) break;
return v1;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (Phi(Loop18,((1<<arg)&1),0)&Phi(Loop,Shl,4294967295));", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testAndHigh() {
        Parser parser = new Parser("""
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testTypes() {
        Parser parser = new Parser(
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
        StopNode stop = parser.parse().iterate();
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

}
