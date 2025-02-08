package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MergeSortTest {

    @Test
    public void testMergeSort() {
        CodeGen code = new CodeGen(
"""
// based on the top-down version from https://en.wikipedia.org/wiki/Merge_sort

val merge_sort = { int[] a, int[] b, int n ->
    copy_array(a, 0, n, b);
    split_merge(a, 0, n, b);
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

int[] !a = new int[arg];
int[] !b = new int[a#];

for (int i = 0; i < a#; i++)
    a[i] = a# - i;

merge_sort(a, b, a#);

return a;
""");
        code.parse().opto();
        assertEquals("Stop[ return [int]; return 0; ]", code._stop.toString());
        assertEquals("int[ 1,2,3,4,5,6,7,8,9,10,11]", Eval2.eval(code, 11));
    }

}
