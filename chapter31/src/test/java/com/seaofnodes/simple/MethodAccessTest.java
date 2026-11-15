package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class MethodAccessTest {
    @Test public void readersAcceptReadOnlyReceivers() throws Exception {
        String source = """
            struct Child { int n = 7; };
            struct Box {
                !Child child = new Child;
                int[] values = new int[]{3,5};
                int count = 2;
                { i64 } read = { -> count+child.n+values[1]; };
                val delegate = { -> read(); };
                val recurse = { i64 n -> n == 0 ? read() : recurse(n-1); };
                val draw = { !Child canvas -> canvas.n = count; };
                val outer = { -> later(count); };
            };
            val later = { int n -> n+1; };
            val check = { Box box ->
                val canvas = new Child;
                box.draw(canvas);
                return box.read()+box.delegate()+box.recurse(3)+canvas.n+box.outer();
            };
            return check(new Box);
            """;
        assertEquals("47",Eval2.eval(new CodeGen(source).driver(CodeGen.Phase.LocalSched),0));
        TestC.runSF("val main = { -> "+source.replace("return check(new Box);","return check(new Box) != 47;")+" };",
                    "readonlyMethods","",-1);
    }

    @Test public void writesThroughReceiverRequireAccess() {
        String[] methods = {
            "val act = { -> x = 2; };",
            "val act = { -> self.x++; };",
            "val act = { -> val nested = { -> x++; }; nested(); };",
            "val act = { -> val alias = self; alias.x += 1; };",
            "val act = { -> child.n = 2; };",
            "val act = { -> values[0]++; };",
            "val act = { -> write(); }; val write = { -> x++; };",
            "val act = { -> val f = x == 0 ? left : right; f(); }; val left = { -> x++; }; val right = { -> x--; };",
            "val act = { -> child.write(); };",
            "val act = { -> write(self); };",
            "{ int } act = \"C\";"
        };
        for( String method : methods ) {
            String source = """
                struct Child { int n; val write = { -> n++; }; };
                struct Box {
                    int x;
                    !Child child = new Child;
                    int[] values = new int[1];
                """+method+"""
                };
                val write = { !Box box -> box.x++; };
                val check = { Box box -> box.act(); return 0; };
                return check(new Box);
                """;
            try {
                new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
                fail("Accepted writer: "+method);
            } catch( Parser.ParseException e ) {
                assertTrue(method+": "+e.getMessage(),e.getMessage().contains("read-only"));
            }
            new CodeGen(source.replace("val check = { Box box", "val check = { !Box box"))
                .driver(CodeGen.Phase.TypeCheck);
        }
    }

    @Test public void returningWritableAliasesRequiresAccess() {
        String source = """
            struct Box { int x; val alias = { -> self; }; };
            val check = { Box box -> box.alias(); };
            return check(new Box);
            """;
        try {
            new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
            fail("Read-only receiver exposed as writable");
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("read-only"));
        }
        source = source.replace("val alias = { -> self; }", "val alias = { -> Box view = self; view; }");
        new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
    }

    @Test public void importedReceiverAccess() throws Exception {
        var root = Files.createDirectories(Path.of("build/objs/separate"));
        var lib = Files.createTempDirectory(root,"method-access-");
        new CodeGen(null,lib.toString(),null,"Counters","""
            struct Counter {
                int n = 7;
                val read = { -> n; };
                val write = { -> n++; };
            };
            val make = { -> new Counter; };
            """,126,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths = new Ary<>(new String[]{lib.toString()});
        String source = "val box = Counters.make(); val check = { Counters.Counter c -> c.read(); }; return check(box);";
        var code = new CodeGen(null,"build/objs",paths,"CounterReader",source,126,true,
                               TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertEquals("7",Eval2.eval(code,0));
        try {
            new CodeGen(null,"build/objs",paths,"CounterWriter",source.replace("c.read()","c.write()"),126,true,
                        TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
            fail("Imported writer accepted a read-only receiver");
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("read-only"));
        }
    }
}
