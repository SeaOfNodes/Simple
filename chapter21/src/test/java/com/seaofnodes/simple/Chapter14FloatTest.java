package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter14FloatTest {
    @Test public void testNumericAssignments() {
        String[][] tests = {
            {"int rounded=17.3/2.5; return rounded;", "6", "6"},
            {"int rounded=arg/2.5; return rounded;", "6", "-6"},
            {"int rounded=0; rounded=arg/2.5; return rounded;", "6", "-6"},
            {"i8 rounded=arg*16.0; return rounded;", "16", "-16"},
            {"int rounded=1e100; return rounded;", "9223372036854775807", "9223372036854775807"},
            {"int rounded=-1e100; return rounded;", "-9223372036854775808", "-9223372036854775808"},
            {"int rounded=1e300*1e300-1e300*1e300; return rounded;", "0", "0"},
            {"f32 rounded=arg+16777216.0; return rounded;", "1.6777232E7", "1.6777199E7"}
        };
        for( String[] test : tests ) {
            var code = new CodeGen(test[0]).parse().opto().typeCheck();
            assertEquals(test[0],test[1],Eval2.eval(code,17));
            assertEquals(test[0],test[2],Eval2.eval(code,-17));
        }
    }


    @Test public void testSubZeroFloat() {
        var code = new CodeGen("flt x = arg; return 0-x;").parse().opto().typeCheck();
        // Compare strings so +0.0 and -0.0 remain distinct.
        assertEquals("0.0", Eval2.eval(code,0));
        assertEquals("-1.0", Eval2.eval(code,1));
    }

    @Test public void testDeadNumericReturns() {
        for( String[] test : new String[][] {
            {"if(1) return 7; else return 2.5;", "7"},
            {"if(0) return 7; else return 2.5;", "2.5"},
            {"return 7; return 2.5;", "7"},
            {"return 2.5; return 7;", "2.5"}
        } ) {
            String src = test[0];
            var code = new CodeGen(src).parse().opto().typeCheck();
            assertEquals(src,test[1],Eval2.eval(code,0));
        }
    }


    @Ignore
    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
    }

    @Test
    public void testFloat() {
        CodeGen code = new CodeGen(
"""
return 3.14;
""");
        code.parse().opto();
        assertEquals("return 3.14;", code.print());
        assertEquals("3.14", Eval2.eval(code,  0));
    }

    @Test
    public void testSquareRoot() {
        CodeGen code = new CodeGen(
"""
flt guess = arg;
while( 1 ) {
    flt next = (arg/guess + guess)/2;
    if( next == guess ) break;
    guess = next;
}
return guess;
""");
        code.parse().opto();
        assertEquals("return Phi(Loop,(flt)arg,(((ToFloat/Phi_guess)+Phi_guess)*0.5f));", code.print());
        assertEquals("3.0", Eval2.eval(code,  9));
        assertEquals("1.414213562373095", Eval2.eval(code,  2));
    }

    @Test
    public void testFPOps() {
        CodeGen code = new CodeGen(
"""
flt x = arg;
return x+1==x;
""");
        code.parse().opto();
        assertEquals("return ((flt)arg==(ToFloat+1.0f));", code.print());
        assertEquals("0", Eval2.eval(code, 1));
    }
}
