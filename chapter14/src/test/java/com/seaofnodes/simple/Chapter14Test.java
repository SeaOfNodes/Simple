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
        StopNode stop = parser.parse(false).iterate(true);
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
        StopNode stop = parser.parse(false).iterate(false);
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
        StopNode stop = parser.parse(false).iterate(false);
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
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return Phi(Loop9,123,((Phi_b+456)&255));", stop.toString());
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
        StopNode stop = parser.parse(false).iterate(false);
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
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3;", stop.toString());
        assertEquals(3L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testRefLoad() {
        Parser parser = new Parser(
"""
struct Foo { u1 b; }
Foo f = new Foo;
f.b = 123;
return f.b;
""");
        StopNode stop = parser.parse(false).iterate(false);
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
        StopNode stop = parser.parse(false).iterate(false);
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
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return (((((arg<<56)>>56)+1)<<56)>>56);", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testMask() {
        Parser parser = new Parser(
"""
u16 mask = (1<<16)-1;           // AND mask
int c = 123456789 & mask;
return c;                       //
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 52501;", stop.toString());
        assertEquals(52501L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testWrapMinus() {
        Parser parser = new Parser(
"""
int MAX = 9223372036854775807; //0x7FFFFFFFFFFFFFFF;
int i = (arg&MAX) + -MAX + -1; // Basically (e0) + Long.MIN_VALUE
int j = -i; // Negating Long.MIN_VALUE wraps, cannot constant fold
if (arg) j = 1;
return j;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return Phi(Region28,1,(-((arg&9223372036854775807)+-9223372036854775808)));", stop.toString());
        assertEquals(-9223372036854775808L, Evaluator.evaluate(stop,  0));
        assertEquals(1L, Evaluator.evaluate(stop,  1));
    }

    @Test
    public void testWrapShr() {
        Parser parser = new Parser(
"""
return (arg >>> 1)==0;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return (!(arg>>>1));", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
        assertEquals(1L, Evaluator.evaluate(stop, 1));
        assertEquals(0L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testOr() {
        Parser parser = new Parser(
"""
return (arg | 123 ^ 456) >>> 1;
""");
        StopNode stop = parser.parse(false).iterate(false);
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
        try { parser.parse(true).iterate(true); fail(); }
        catch( Exception e ) { assertEquals("Cannot '&' FltBot",e.getMessage()); }
    }

    @Test
    public void testLoadBug() {
        Parser parser = new Parser(
"""
struct A { int i; }
struct B { int i; }
A a = new A;
A t1 = new A;
B b = new B;
B t2 = new B;
int i;
if (arg) i = a.i;
else     i = b.i;
return i;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return Phi(Region31,.i,.i);", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testBug2() {
        Parser parser = new Parser(
"""
int z = 0;
while (1) {
    int j;
    if (arg&3) {
        j = arg >> 2;
    } else {
        j = (arg >> 3)+z;
    }
    return j+1;
}
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return ((arg>>Phi(Region32,2,3))+1);", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
        assertEquals(1L, Evaluator.evaluate(stop, 1));
        assertEquals(2L, Evaluator.evaluate(stop, 7));
        assertEquals(2L, Evaluator.evaluate(stop, 8));
    }

    @Test
    public void testBug3() {
        Parser parser = new Parser(
"""
flt f = arg;
bool b;
if (arg&3) b = f == 1.0;
else       b = f == 2.0;
if (arg&5) b = arg == 1;
return b;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return Phi(Region37,(arg==1),((flt)arg==Phi(Region23,1.0,2.0)));", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 1));
        assertEquals(0L, Evaluator.evaluate(stop, 2));
        assertEquals(0L, Evaluator.evaluate(stop, 3));
    }

    @Test
    public void testBug4() {
        Parser parser = new Parser(
"""
int i;
if (arg&7) i=3;
else       i=2;
return (arg == i) == 1;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return (arg==Phi(Region18,3,2));", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
        assertEquals(0L, Evaluator.evaluate(stop,  1));
        assertEquals(0L, Evaluator.evaluate(stop,  2));
        assertEquals(1L, Evaluator.evaluate(stop,  3));
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
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

}
