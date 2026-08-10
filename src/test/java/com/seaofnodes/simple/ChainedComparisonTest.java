package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChainedComparisonTest {
    @Test public void testAdjacentOperandsAndOperators() {
        String[] expressions = {"10 < arg < 20", "0 < arg <= 3", "3 >= arg > 0",
                                "0 <= arg < 3", "0 < arg <= 3 < 4"};
        String[][] expected = {{"0","0","1","0"}, {"0","1","0","0"},
                               {"0","1","0","0"}, {"1","0","0","0"},
                               {"0","1","0","0"}};
        int[] args = {0,3,15,30};
        for(int i=0;i<expressions.length;i++) {
            CodeGen code = new CodeGen("return "+expressions[i]+";").driver(CodeGen.Phase.TypeCheck);
            for(int j=0;j<args.length;j++) assertEquals(expressions[i],expected[i][j],Eval2.eval(code,args[j]));
        }
    }

    @Test public void testSingleEvaluationAndShortCircuit() {
        for(int seed:new int[]{2,13,123}) {
            CodeGen code = new CodeGen("int x=arg; int y=0; val b=0 < x++ <= (y+=3) < (y+=1); return x*100+y*10+b;",seed,true)
                .driver(CodeGen.Phase.TypeCheck);
            assertEquals("100",Eval2.eval(code,0));
            assertEquals("441",Eval2.eval(code,3));
            assertEquals("530",Eval2.eval(code,4));
        }
        try {
            new CodeGen("return 0 < arg > 1;").parse();
            fail("mixed directions");
        } catch(RuntimeException e) {
            assertEquals("Mixing relational directions in a chained relational test",e.getMessage());
        }
    }
}
