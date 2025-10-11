package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.util.SB;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MergeSortTest {

    @Test public void testMergeSort0() throws IOException {
        // Test merge sort on a small array of primes
        String src =
""" 
        val merge_sort = { int[] a ->
            int[] !b = new int[a#];
            copy_array (a, 0, a#, b);
            split_merge(a, 0, a#, b);
        };
        
        val split_merge = { int[] b, int begin, int end, int[] a ->
            if (end - begin <= 1)
                return 0;
            int middle = (end + begin) / 2;
            split_merge(a, begin, middle, b);
            split_merge(a, middle, end, b);
            merge(b, begin, middle, end, a);
            return 0;
        };
        
        val merge = { int[] b, int begin, int middle, int end, int[] a ->
            int i = begin, j = middle;
        
            for (int k = begin; k < end; k++) {
                // && and ||
                bool cond = false;
                if (i < middle) {
                    if (j >= end)          cond = true;
                    else if (a[i] <= a[j]) cond = true;
                }
                if (cond) b[k] = a[i++];
                else      b[k] = a[j++];
            }
        };
        
        val copy_array = { int[] a, int begin, int end, int[] b ->
            for (int k = begin; k < end; k++)
                b[k] = a[k];
        };
""";
        int[] primes = new int[] { 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, };
        SB sb = new SB().p(primes.length).p("[");
        for( int prime : primes )
            sb.p(prime).p(", ");
        String sprimes = sb.p("]").toString();
        TestC.run(src, "sort", null, sprimes, 40);


        EvalRisc5 R5 = TestRisc5.build("src/test/java/com/seaofnodes/simple/progs",src,  "sort", "merge_sort", 0, 36, false);
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
        Chapter21Test.testCPU(src,"arm", "SystemV",36,null);
    }
}
