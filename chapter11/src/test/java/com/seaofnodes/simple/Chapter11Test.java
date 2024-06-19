package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter11Test {

    // A placeholder test used to rapidly rotate through fuzzer produced issues
    @Test
    public void testFuzzer() {
        Parser parser = new Parser(
"""
return 0;
""");
        StopNode stop = parser.parse(false).iterate(false);
        assertEquals("return 0;", stop.toString());
    }


    @Test
    public void testPrimes() {
        Parser parser = new Parser(
"""
if( arg < 2 ) return 0;
int primeCount = 1;
int prime = 3;
while( prime <= arg ) {
    int isPrime = 1;
    // Check for even case, so the next loop need only check odds
    if( (prime/2)*2 == prime )
        continue;
    // Check odds up to sqrt of prime
    int j = 3;
    while( j*j <= prime ) {
        if( (prime/j)*j == prime ) {
            isPrime = 0;
            break;
        }
        j = j + 2;
    }
    if( isPrime )
        primeCount = primeCount + 1;
    prime = prime + 2;
}
return primeCount;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("Stop[ return 0; return Phi(Loop18,1,Phi(Region83,Phi_primeCount,Phi(Region77,(Phi_primeCount+1),Phi_primeCount))); ]", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop,  1)); // No primes 1 or below
        assertEquals(1L, Evaluator.evaluate(stop,  2)); // 2
        assertEquals(2L, Evaluator.evaluate(stop,  3)); // 2, 3
        assertEquals(2L, Evaluator.evaluate(stop,  4)); // 2, 3
        assertEquals(3L, Evaluator.evaluate(stop,  5)); // 2, 3, 5
        assertEquals(4L, Evaluator.evaluate(stop, 10)); // 2, 3, 5, 7
    }

    @Test
    public void testRegression1() {
        Parser parser = new Parser(
"""
struct S { int f; }
S v = new S;
S t = new S;
int i = 0;
if (arg) {
    if (arg+1) v = t;
    i = v.f;
} else {
    v.f = 2;
}
return i;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return Phi(Region31,.f,0);", stop.toString());
    }

    @Test
    public void testRegression2() {
        Parser parser = new Parser(
"""
struct S { int f; }
S v0 = new S;
S? v1;
if (arg) v1 = new S;
if (v1) {
    v0.f = v1.f;
} else {
    v0.f = 2;
}
return v0;
""");
        StopNode stop = parser.parse(false).iterate(true);
        assertEquals("return new S;", stop.toString());
    }
}
