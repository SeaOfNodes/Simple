package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;

public class MathTest {
    @Test public void nativeValues() throws Exception {
        TestC.runSYS("""
            val near={ f64 x, f64 y -> x==x && x-y<1e-12 && y-x<1e-12; };
            val main={ ->
                val angles=new f64[~]{-sys.math.PI/2,0.0,sys.math.PI/6,sys.math.PI/2};
                val sines=new f64[~]{-1.0,0.0,0.5,1.0};
                val cosines=new f64[~]{0.0,1.0,0.8660254037844386,0.0};
                for(int i=0;i<angles#;i++) {
                    if(!near(sys.math.sin(angles[i]),sines[i])) return 1;
                    if(!near(sys.math.cos(angles[i]),cosines[i])) return 2;
                }
                if(!near(sys.math.exp(1.0),sys.math.E)) return 3;
                if(!near(sys.math.log(sys.math.E),1.0)) return 4;
                if(!near(sys.math.sqrt(2.0),1.4142135623730951)) return 5;
                if(!near(sys.math.log(sys.math.exp(-3.5)),-3.5)) return 6;
                if(sys.math.sqrt(9.0)!=3.0) return 7;
                if(sys.math.exp(0.0)!=1.0 || sys.math.log(1.0)!=0.0) return 8;
                return 0;
            };
            ""","mathValues","",-1);
    }

    @Test public void nativeSpecialValues() throws Exception {
        TestC.runSYS("""
            val nan={ f64 x -> x!=x; };
            val main={ ->
                f64 inf=sys.math.f64FromBits(0x7ff0000000000000);
                f64 qnan=sys.math.f64FromBits(0x7ff8000000000001);
                if(!nan(sys.math.sin(inf)) || !nan(sys.math.cos(inf))) return 1;
                if(!nan(sys.math.log(-1.0)) || !nan(sys.math.sqrt(-1.0))) return 2;
                if(sys.math.log(0.0)!=-inf || sys.math.log(inf)!=inf) return 3;
                if(sys.math.exp(inf)!=inf || sys.math.exp(-inf)!=0.0) return 4;
                if(sys.math.sqrt(inf)!=inf) return 5;
                if(sys.math.f64Bits(sys.math.sin(-0.0))!=sys.integer.MIN_I64) return 6;
                if(sys.math.f64Bits(sys.math.sqrt(-0.0))!=sys.integer.MIN_I64) return 7;
                if(sys.math.cos(-0.0)!=1.0) return 8;
                if(!nan(sys.math.sin(qnan)) || !nan(sys.math.cos(qnan))) return 9;
                if(!nan(sys.math.exp(qnan)) || !nan(sys.math.log(qnan)) ||
                   !nan(sys.math.sqrt(qnan))) return 10;
                if(sys.math.exp(1000.0)!=inf || sys.math.exp(-1000.0)!=0.0) return 11;
                return 0;
            };
            ""","mathSpecial","",-1);
    }

    @Test public void indirectBindings() throws Exception {
        TestC.runSYS("""
            val apply_noInline={ { f64 -> f64 } f, f64 x -> f(x); };
            val main={ ->
                if(apply_noInline(sys.math.sin,0.0)!=0.0) return 1;
                if(apply_noInline(sys.math.cos,0.0)!=1.0) return 2;
                if(apply_noInline(sys.math.exp,0.0)!=1.0) return 3;
                if(apply_noInline(sys.math.log,1.0)!=0.0) return 4;
                if(apply_noInline(sys.math.sqrt,9.0)!=3.0) return 5;
                return 0;
            };
            ""","mathIndirect","",-1);
    }

    @Test public void allBackends() {
        // Load just math from source so each backend selects its own C ABI.
        String source="""
            f64 x=arg;
            return math.sin(x)+math.cos(x)+math.exp(x)+
                   math.log(x)+math.sqrt(x);
            """;
        for(String cpu:new String[]{"x86_64_v2","arm","riscv"})
            new CheckedCodeGen("src/main/smp/sys",null,null,"MathCalls",source,126,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.Encoding,cpu,"SystemV");
    }
}
