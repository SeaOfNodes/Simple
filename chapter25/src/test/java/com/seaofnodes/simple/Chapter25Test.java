package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalCodeMotion;
import com.seaofnodes.simple.print.IRPrinter;
import org.junit.Ignore;
import org.junit.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class Chapter25Test {

    @Test
    public void testSys() throws IOException {
        //// Produce lib/sys.o
        //CodeGen code = new CodeGen(com.seaofnodes.simple.sys.SYS).
        //    driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,"lib/sys.o",false);

        TestC.run("sys.io.p(\"Hello, World!\");",TestC.CALL_CONVENTION,null, null,null,"build/objs/helloWorld","","Hello, World!",0);
    }

    @Test @Ignore
    public void testBubbles() throws IOException {
        String src = Files.readString( Path.of("docs/examples/BubbleSort.smp"));
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.LoopTree);
    }

}
