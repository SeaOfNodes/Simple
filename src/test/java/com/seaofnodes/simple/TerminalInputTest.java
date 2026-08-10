package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.util.Ary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerminalInputTest {
    @Test public void testLinkOnlyAndEnterEscape() throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String output="build/objs/linkOnlyGame/";
        // Linking must return without displaying the game or waiting for input.
        assertEquals("",TestC.exec(20,java,"-ea","-cp",System.getProperty("java.class.path"),
            Simple.class.getName(),"--link","-L",TestC.RELEASE_SYS_DIR,"-o",output,
            "docs/examples/TicTacToe.smp"));
        Path exe=Path.of(output,"TicTacToe"+(TestC.OS.startsWith("Windows") ? ".exe" : ""));
        assertTrue(Files.isRegularFile(exe));
        Process child=new ProcessBuilder(exe.toString()).redirectErrorStream(true).start();
        try {
            child.getOutputStream().write(new byte[]{'\r','\n',27});
            child.getOutputStream().flush(); // Keep stdin open: Escape must cause the exit.
            assertTrue("Escape did not quit",child.waitFor(10,TimeUnit.SECONDS));
            String text=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(text,0,child.exitValue());
            assertTrue(text,text.endsWith("Your move (1-9): \nGoodbye!\n"));
            assertFalse(text,text.contains("Choose a square"));
        } finally {
            child.destroyForcibly();
        }
    }

    @Test public void testRedirectedBytesAndEOF() throws IOException {
        TestC.run("""
            val main={ u8[~][] args ->
                if(sys.io.getch()!='5') return 1;
                if(sys.io.getch()!='q') return 2;
                if(sys.io.getch()!=-1) return 3;
                if(sys.libc.errno()!=sys.libc.EOF) return 4;
                sys.io.p("ok\n"); return 0;
            };
            ""","getchBytes",new Ary<>(new String[]{TestC.RELEASE_SYS_DIR}),
            TestC.CALL_CONVENTION,null,null,"5q","ok\n",-1);
    }

    @Test public void testReadError() throws IOException {
        TestC.runSYS("""
            val main={ u8[~][] args ->
                sys.io.close(sys.libc.STDIN);
                if(sys.io.getch()!=-1) return 1;
                if(sys.libc.errno()<=0) return 2;
                return 0;
            };
            ""","getchError","",-1);
    }

    @Test public void testGuardsStayInsideFunction() {
        String src="""
            val first={ int n -> while(true) { if(n) return 0; return 1; } };
            val second={ -> int sum=0; for(int i=0; i<3; i++) sum+=i; return sum; };
            return first(((arg<<32)>>32))+second();
            """;
        for(long seed:new long[]{1,123,456}) {
            CodeGen code=new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("4",Eval2.eval(code,0));
            assertEquals("3",Eval2.eval(code,1));
        }
    }

    @Test public void testForwardReferenceAfterContinue() {
        String src="""
            val run={ int n ->
                while(n) {
                    if(n==1) { n=0; continue; }
                    if(later(n)) return 7;
                    n=0;
                }
                return 3;
            };
            val later={ int n -> n==2; };
            return run(((arg<<32)>>32));
            """;
        for(long seed:new long[]{1,123,456}) {
            CodeGen code=new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("3",Eval2.eval(code,1));
            assertEquals("7",Eval2.eval(code,2));
            assertEquals("3",Eval2.eval(code,3));
            // The same merge is used by break, including the implicit loop exit.
            String breaking=src.replace("return 7;","break;").replace("return 3;","return n+3;");
            CodeGen code2=new CodeGen(breaking,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("3",Eval2.eval(code2,1));
            assertEquals("5",Eval2.eval(code2,2));
            assertEquals("3",Eval2.eval(code2,3));
        }
    }

    @Test public void testForwardReferenceWithLoopLocal() {
        String src="""
            val run={ int n ->
                while(n) {
                    int key=n;
                    n--;
                    if(key==1) continue;
                    if(later(key)) continue;
                    return key;
                }
                return 0;
            };
            val later={ int key -> key==2; };
            return run(((arg<<32)>>32));
            """;
        String nested=src.replace("if(later(key)) continue;",
            "if(later(key)) { int local=key+1; continue; }");
        String breaking=nested.replace("int local=key+1; continue;","int local=key+1; break;")
            .replace("return 0;","return n+10;");
        String twoRefs=src.replace("return key;","if(other(key)) continue; return key;")
            .replace("return run(((arg<<32)>>32));","val other={ int key -> key==3; }; return run(((arg<<32)>>32));");
        for(long seed:new long[]{1,123,456}) {
            for(String variant:new String[]{src,nested}) {
                CodeGen code=new CodeGen(variant,seed,true).driver(CodeGen.Phase.TypeCheck);
                assertEquals("0",Eval2.eval(code,1));
                assertEquals("0",Eval2.eval(code,2));
                assertEquals("3",Eval2.eval(code,3));
            }
            CodeGen code=new CodeGen(breaking,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("10",Eval2.eval(code,1));
            assertEquals("11",Eval2.eval(code,2));
            assertEquals("3",Eval2.eval(code,3));
            CodeGen code2=new CodeGen(twoRefs,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("0",Eval2.eval(code2,3));
            assertEquals("4",Eval2.eval(code2,4));
        }
    }

    @Test public void testInteractiveCLIShowsPromptBeforeInput() throws Exception {
        // Pipe input rather than a terminal: this catches CLI buffering and
        // requires exactly one byte, with no Enter.
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process child=new ProcessBuilder(java,"-ea","-cp",System.getProperty("java.class.path"),
            Simple.class.getName(),"-L",TestC.RELEASE_SYS_DIR,"-o","build/objs/interactiveGame/",
            "docs/examples/TicTacToe.smp").redirectErrorStream(true).start();
        try(var reader=Executors.newSingleThreadExecutor()) {
            try {
                var prompt=reader.submit(() -> readPrompt(child));
                String opening=prompt.get(20,TimeUnit.SECONDS);
                assertTrue(opening,opening.contains("You go first."));
                assertTrue(opening,opening.contains("   |   |   \n"));
                assertTrue(child.isAlive());
                child.getOutputStream().write('5');
                child.getOutputStream().flush();
                String turn=reader.submit(() -> readPrompt(child)).get(20,TimeUnit.SECONDS);
                assertEquals("5\n   |   |   \n---+---+---\n   | X |   \n---+---+---\n   |   |   \n"
                    +"CPU plays 1.\n O |   |   \n---+---+---\n   | X |   \n---+---+---\n   |   |   \n"
                    +"Your move (1-9): ",turn);
                assertTrue(child.isAlive());
                child.getOutputStream().write(27);
                child.getOutputStream().flush();
                assertTrue("Escape did not quit",child.waitFor(10,TimeUnit.SECONDS));
                assertEquals("\nGoodbye!\n",new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
                assertEquals(0,child.exitValue());
            } finally {
                child.destroyForcibly();
            }
        }
    }

    private static String readPrompt(Process child) throws IOException {
        var bytes=new ByteArrayOutputStream();
        while(bytes.size()<8192) {
            int ch=child.getInputStream().read();
            if(ch<0) throw new IOException("Exited before prompt: "+bytes.toString(StandardCharsets.UTF_8));
            bytes.write(ch);
            String text=bytes.toString(StandardCharsets.UTF_8);
            if(text.endsWith("Your move (1-9): ")) return text;
        }
        throw new IOException("No prompt: "+bytes.toString(StandardCharsets.UTF_8));
    }
}
