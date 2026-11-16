package com.seaofnodes.simple;

import org.junit.Test;

public class FloatComparisonNativeTest extends TestC {
    @Test public void longFloatingBranches() throws Exception {
        StringBuilder source=new StringBuilder("val far_noInline={f64 a,f64 b,i64[] out -> if(a<b){");
        for(int i=0;i<32;i++) source.append("out[").append(i).append("]=").append(i+1).append(';');
        source.append("""
                return 1; } return 0; };
            val main={ ->
                val out=new i64[32];
                if( far_noInline(1.0,2.0,out)!=1 || out[31]!=32 ) return 1;
                out[31]=0;
                if( far_noInline(2.0,1.0,out)!=0 || out[31]!=0 ) return 2;
                f64 nan=1e300*1e300-1e300*1e300;
                if( far_noInline(nan,1.0,out)!=0 || out[31]!=0 ) return 3;
                if( far_noInline(1.0,nan,out)!=0 || out[31]!=0 ) return 4;
                return 0;
            };
            """);
        runSYS(source.toString(),"floatLongBranches","",-1);
    }

    @Test public void branchesAndValues() throws Exception {
        String[] literals={"-1.0","0.0","2.0","1e300*1e300","-1e300*1e300",
            "1e300*1e300-1e300*1e300"};
        double[] values={-1,0,2,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN};
        StringBuilder source=new StringBuilder("""
            val branch_noInline={ f64 a, f64 b ->
                int bits=0;
                if( a<b ) bits+=1;
                if( a<=b ) bits+=2;
                if( a==b ) bits+=4;
                if( a!=b ) bits+=8;
                if( a>=b ) bits+=16;
                if( a>b ) bits+=32;
                return bits;
            };
            val value_noInline={ f64 a, f64 b ->
                val lt=a<b, le=a<=b, eq=a==b, ne=a!=b, ge=a>=b, gt=a>b;
                return lt+le*2+eq*4+ne*8+ge*16+gt*32;
            };
            val check_noInline={ f64 a, f64 b, int expected, int tag ->
                val branches=branch_noInline(a,b), values=value_noInline(a,b);
                if( branches!=expected || values!=expected ) {
                    sys.io.p("case %(tag): branches %(branches), values %(values), expected %(expected)\\n");
                    return 1;
                }
                return 0;
            };
            val main={ ->
            """);
        for(int i=0;i<values.length;i++) for(int j=0;j<values.length;j++) {
            double a=values[i],b=values[j];
            int expected=(a<b ? 1 : 0)|(a<=b ? 2 : 0)|(a==b ? 4 : 0)|
                (a!=b ? 8 : 0)|(a>=b ? 16 : 0)|(a>b ? 32 : 0);
            source.append("if(check_noInline(").append(literals[i]).append(',').append(literals[j])
                .append(',').append(expected).append(',').append(i*values.length+j)
                .append(")!=0) return 1;");
        }
        source.append("return 0; };");
        runSYS(source.toString(),"floatComparisons","",-1);
    }
}
