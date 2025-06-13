package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalCodeMotion;
import com.seaofnodes.simple.print.IRPrinter;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class Chapter24Test {

    @Test
    public void testBubbles() throws IOException {
        String src = Files.readString( Path.of("docs/examples/BubbleSort.smp"));
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
    }

    @Test
    public void demoPrint() {
        String src =
"""
val c = { int x ->
    int sum=0;
    for( int i=0; i<x; i++ ) sum += i;
    sum;
};
val b = { int y -> c(y) * c(y+1); };
val a = { int z -> b(z) * b(z+5); };
return a(3);
""";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
        String rez = IRPrinter.prettyPrint(code);
    }


    @Test @Ignore
    public void sieveOfEratosthenes() {
        String src =
"""
var ary = new bool[arg], primes = new int[arg];
var nprimes=0, p=0;
// Find primes while p^2 < arg
for( p=2; p*p < arg; p++ ) {
    // skip marked non-primes
    while( ary[p] ) p++;
    // p is now a prime
    primes[nprimes++] = p;
    // Mark out the rest non-primes
    for( int i = p + p; i < ary#; i += p )
        ary[i] = true;
}
// Now just collect the remaining primes, no more marking
for( ; p < arg; p++ )
    if( !ary[p] )
        primes[nprimes++] = p;
// Copy/shrink the result array
var !rez = new int[nprimes];
for( int j=0; j<nprimes; j++ )
    rez[j] = primes[j];
return rez;
""";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
        String rez = IRPrinter.prettyPrint(code);
    }
}
