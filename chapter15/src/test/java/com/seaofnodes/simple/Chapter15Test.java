package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import org.junit.Ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter15Test {

    @Test
    public void testJig() {
        Parser parser = new Parser(
"""
return 3.14;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testBasic() {
        Parser parser = new Parser(
"""
int[] is = new int[2];
return is[1];
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testBasic2() {
        Parser parser = new Parser(
"""
int[] is = new int[2];
int[] is2 = new int[2];
return is[1];
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testBasic3() {
        Parser parser = new Parser(
"""
int[] a = new int[2];
a[0] = 1;
a[1] = 2;
return a[0];
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return .[];", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testBasic4() {
        Parser parser = new Parser(
"""
struct A { int i; }
A[] a = new A[2];
return a;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return A[];", stop.toString());
        assertEquals("Obj<A[]> {\n  #=2\n  []=null\n}", Evaluator.evaluate(stop, 0).toString());
    }

    @Test
    public void testTree() {
        Parser parser = new Parser(
"""
// Can we define a forward-reference array?
struct Tree { Tree[] _kids; }
Tree root = new Tree;
root._kids = new Tree[2];
root._kids[0] = new Tree;
return root;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return Tree;", stop.toString());
        assertEquals("Obj<Tree> {\n  _kids=Obj<Tree[]> {\n  #=2\n  []=Obj<Tree> {\n  _kids=null\n}\n}\n}", Evaluator.evaluate(stop,  0).toString());
    }


    @Test
    public void testRollingSum() {
        Parser parser = new Parser(
"""
int[] ary = new int[arg];
// Fill [0,1,2,3,4,...]
int i=0;
while( i < ary# ) {
    ary[i] = i;
    i = i+1;
}
// Fill [0,1,3,6,10,...]
i=0;
while( i < ary# - 1 ) {
    ary[i+1] = ary[i+1] + ary[i];
    i = i+1;
}
return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return (.[]+(.[]*1000));", stop.toString());
        assertEquals(1006L, Evaluator.evaluate(stop,  4));
    }

    @Test
    public void sieveOEratosthenes() {
        Parser parser = new Parser(
"""
int[] ary = new int[arg];
int[] primes = new int[arg];
int nprimes = 0;
// Find primes
int j=2;
while( j*j < arg ) {
    while( ary[j]==1 ) j = j + 1;
    // j is now a prime
    primes[nprimes] = j;  nprimes = nprimes + 1;
    // Mark out the rest non-primes
    int i = j + j;
    while( i < ary# ) {
        ary[i] = 1;
        i = i + j;
    }
    j = j + 1;
}
// Now just collect the remaining primes
while( j < arg ) {
    if( ary[j] == 0 ) {
        primes[nprimes] = j;  nprimes = nprimes + 1;
    }
    j = j + 1;
}
// Shrink the result array to size
int[] rez = new int[nprimes];
j = 0;
while( j < nprimes ) {
    rez[j] = primes[j];
    j = j + 1;
}

return rez;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return int[];", stop.toString());
        Evaluator.Obj obj = (Evaluator.Obj)Evaluator.evaluate(stop, 20);
        assertEquals("int[] {\n  # :int;\n  [] :int;\n}",obj.struct().toString());
        long nprimes = (Long)obj.fields()[0];
        long[] primes = new long[]{2,3,5,7,11,13,17,19};
        for( int i=0; i<nprimes; i++ )
            assertEquals(primes[i],(long)(Long)obj.fields()[i+1]);
    }


    @Test
    public void testBad0() {
        Parser parser = new Parser(
"""
return new flt;
""");
        try { parser.parse(false).iterate(false); fail(); }
        catch( Exception e ) { assertEquals("Cannot allocate a flt",e.getMessage()); }
    }

    @Test
    public void testBad1() {
        Parser parser = new Parser(
"""
int is = new int[2];
""");
        try { parser.parse(false).iterate(false); fail(); }
        catch( Exception e ) { assertEquals("Type *int[] is not of declared type int",e.getMessage()); }
    }

    @Test
    public void testBad2() {
        Parser parser = new Parser(
"""
int[] is = new int[3.14];
return is[1];
""");
        try { parser.parse(false).iterate(false); fail(); }
        catch( Exception e ) { assertEquals("Cannot allocate an array with length 3.14",e.getMessage()); }
    }

    @Test
    public void testBad3() {
        Parser parser = new Parser(
"""
int[] is = new int[arg];
return is[1];
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 0;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  4));
        try { Evaluator.evaluate(stop,0); }
        catch( ArrayIndexOutOfBoundsException e ) { assertEquals("Array index 1 out of bounds for array length 0",e.getMessage()); }
        try { Evaluator.evaluate(stop,-1); }
        catch( NegativeArraySizeException e ) { assertEquals("-1",e.getMessage()); }
    }

}
