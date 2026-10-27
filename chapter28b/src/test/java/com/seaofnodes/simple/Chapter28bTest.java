package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Ignore;
import org.junit.Test;
//
import static org.junit.Assert.*;

public class Chapter28bTest {

    @Ignore
    @Test
    public void testJig() {
        String src = """
struct C {
    int x;
    new C = { int n ->
        while(n--)
            if(n) x=1;
    };
};
return new C(arg).x;
""";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("Stop[ return Phi(Loop,0,Phi(Region,1,Phi_x)); return MEM[ 2:___ 3:.x=Phi(Loop,0,Phi(Region,1,Phi_x));]; return MEM[ 2:___ 3:.x=0;]; return Test.C; ]", code.print());
        assertEquals("1",Eval2.eval(code,3));
    }

}
