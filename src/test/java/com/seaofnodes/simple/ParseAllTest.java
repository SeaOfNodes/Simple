package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.ParseAllTestSupport;
import com.seaofnodes.simple.type.TypeInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParseAllTest {
    @Test public void testTypeOnlyFileHasNoConstructor() throws Exception {
        Path base = Path.of("build/objs");
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base,"type-only-");
        Files.createDirectory(root.resolve("A"));
        Files.writeString(root.resolve("A/B.smp"),"int x = 0;");
        for( String src : new String[]{
                "B? b = null; return 0;",
                "val readB = { B b -> 0; }; return 0;"} ) {
            Files.writeString(root.resolve("A.smp"),src);
            CodeGen code = new CodeGen(root.toString(),root.resolve("obj").toString(),
                                      "A",null,123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            assertEquals("0",Eval2.eval(code,0));
        }
    }

    @Test public void testRequiredTypesBelongToTheirSource() throws Exception {
        Path base = Path.of("build/objs");
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base,"required-types-");
        Files.createDirectory(root.resolve("App"));
        Files.writeString(root.resolve("App.smp"),"val marker = 0;");
        Files.writeString(root.resolve("App/Main.smp"),"return A.x+C.y;");
        Files.writeString(root.resolve("App/A.smp"),"int x = 0; val readB = { B b -> b.x; };");
        Files.writeString(root.resolve("App/B.smp"),"int x = 0;");
        Files.writeString(root.resolve("App/C.smp"),"int y = 0;");
        CodeGen code = new CodeGen(root.toString(),root.resolve("obj").toString(),
                                  "App/Main",null,123,true,TypeInteger.BOT).parse();
        ParseAllTestSupport.checkRequiredTypes(code);
    }
}
