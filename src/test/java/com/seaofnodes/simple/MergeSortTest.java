package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MergeSortTest {

    @Test public void testMergeSort0() throws IOException {
        int[] primes = new int[] { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, };
        SB sb = new SB().p(primes.length).p("[");
        for( int prime : primes )
            sb.p(prime).p(", ");
        String sprimes = sb.p("]").toString();
        TestC.run("sort", sprimes, 40);


        EvalRisc5 R5 = TestRisc5.build("src/test/java/com/seaofnodes/simple/progs", "sort", "merge_sort", 0, 36, false);
        // Allocate an array of primes, filled in reverse order
        int ps = R5._heap;
        R5._heap += (primes.length+1)*8; // 25 primes, and length
        R5.st4(ps,primes.length);
        for( int i=0; i<primes.length; i++ )
            R5.st8(ps+(primes.length-1-i+1)*8,primes[i]);
        // Pass array base when calling `merge_sort`
        R5.regs[riscv.A0] = ps;          // Array base to be sorted

        int trap = R5.step(100000);
        assertEquals(0,trap);

        assertEquals(primes.length,R5.ld4s(ps));
        for( int i=0; i<primes.length; i++ )
            assertEquals(primes[i], R5.ld8(ps+(i+1)*8));

        // TODO: replace with ARM Eval
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/sort.smp"));
        Chapter21Test.testCPU(src,"arm", "SystemV",36,null);
    }
}
