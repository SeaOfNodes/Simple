package com.seaofnodes.simple;

//
//import com.seaofnodes.simple.codegen.CodeGen;
//import com.seaofnodes.simple.codegen.RegAllocTestSupport.CheckedCodeGen;
//import com.seaofnodes.simple.codegen.ElfReader;
//import com.seaofnodes.simple.codegen.ParseAll;
//import com.seaofnodes.simple.node.CallNode;
//import com.seaofnodes.simple.node.FunNode;
//import com.seaofnodes.simple.node.LoopNode;
//import com.seaofnodes.simple.type.TypeInteger;
//import com.seaofnodes.simple.util.Ary;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.ByteArrayOutputStream;
//import java.io.PrintStream;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
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
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.Encoding,"riscv","SystemV");
        assertEquals("return (arg+3);", code.print());
        assertEquals("3",Eval2.eval(code,0));
    }

}
