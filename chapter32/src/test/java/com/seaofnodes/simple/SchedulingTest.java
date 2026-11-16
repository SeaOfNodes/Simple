package com.seaofnodes.simple;

import org.junit.Test;

public class SchedulingTest {
    @Test public void nativeFoldedLoadsWithLaterOperands() throws Exception {
        TestC.runSYS("""
            val compare_noInline={ i64[] a, int flag ->
                i64 x=a[0];
                i64 y=flag ? a[1] : a[2];
                return x==y;
            };
            val add_noInline={ i64[] a, int flag ->
                i64 x=a[0];
                i64 y=flag ? a[1] : a[2];
                return x+y;
            };
            val main={ ->
                val a=new i64[3]; a[0]=17; a[1]=17; a[2]=23;
                if(!compare_noInline(a,1) || compare_noInline(a,0)) return 1;
                if(add_noInline(a,1)!=34 || add_noInline(a,0)!=40) return 2;
                return 0;
            };
            ""","scheduleLaterOperands","",-1);
    }

    @Test public void nativeLoadsAcrossWrites() throws Exception {
        TestC.runSYS("""
            val change_noInline={ i64[] a -> a[0]=a[1]; return a[0]; };
            val compare_noInline={ i64[] a ->
                i64 x=a[0];
                return x==change_noInline(a);
            };
            val add_noInline={ i64[] a ->
                i64 x=a[0];
                return x+change_noInline(a);
            };
            val main={ ->
                val a=new i64[2]; a[0]=17; a[1]=23;
                if(compare_noInline(a)) return 1;
                a[0]=17; a[1]=29;
                if(add_noInline(a)!=46) return 2;
                return 0;
            };
            ""","scheduleAcrossWrites","",-1);
    }
}
