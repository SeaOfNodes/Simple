package com.seaofnodes.simple;

import org.junit.Test;

public class FloatNegationNativeTest extends TestC {
    @Test public void negateRuntimeValues() throws Exception {
        // Keep negation out of constant folding, including both signs of zero.
        runSYS("""
            val neg_noInline={f64 x -> -x;};
            val main={ ->
                if( neg_noInline(2.5)!=-2.5 || neg_noInline(-3.75)!=3.75 ) return 1;
                if( 1.0/neg_noInline(0.0)!=-1e300*1e300 ) return 2;
                if( 1.0/neg_noInline(-0.0)!=1e300*1e300 ) return 3;
                if( neg_noInline(1e300*1e300)!=-1e300*1e300 ) return 4;
                f64 nan=neg_noInline(1e300*1e300-1e300*1e300);
                if( nan==nan ) return 5;
                return 0;
            };
            ""","floatNegation","",-1);
    }
}
