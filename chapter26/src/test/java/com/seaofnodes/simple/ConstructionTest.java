package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.isa.eval.EvalArm64;
import com.seaofnodes.isa.eval.EvalRisc5;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConstructionTest {
    @Test public void testConditionalConstructorLoop() {
        String[] bodies = {
            "while(n--) if(n) x=1;",
            "for(int i=0;i<n;i++) { if(i==1) continue; if(i==3) break; x+=i+1; }"
        };
        int[][] expected = {{0,0,1,1,1,1}, {0,1,1,4,4,4}};
        for( int i=0; i<bodies.length; i++ )
            for( long seed : new long[]{123,456,789} ) {
                String src = "struct C { int x; new C={int n -> "+bodies[i]+"}; }; return new C(arg).x;";
                var code = new CodeGen(src,com.seaofnodes.simple.type.TypeInteger.BOT,seed,true)
                    .driver(CodeGen.Phase.TypeCheck);
                for( int arg=0; arg<expected[i].length; arg++ )
                    assertEquals("case="+i+" seed="+seed+" arg="+arg,
                        Integer.toString(expected[i][arg]),Eval2.eval(code,arg));
            }
    }

    private static final String CONSTRUCT = """
        struct Box {
            int x=7;
            int[] data;
            new Box = { int n ->
                data=new int[2];
                x=n;
                data[0]=x+1;
            };
            val get = { -> x+data[0]; };
        };
        val a=new Box(arg);
        val b=new Box(arg+10);
        return a.get()*100+b.get();
        """;

    @Test public void testConstructorCalls() throws java.io.IOException {
        var code=new CodeGen(CONSTRUCT).driver(CodeGen.Phase.FinalizeIR);
        assertEquals("525",Eval2.eval(code,2));
        for( String cpu : new String[]{"arm","riscv"} ) {
            code=new CodeGen(CONSTRUCT).driver(cpu,"SystemV",null);
            byte[] memory=new byte[1<<20];
            byte[] bits=code._encoding.bits();
            System.arraycopy(bits,0,memory,0,bits.length);
            if( cpu.equals("arm") ) {
                EvalArm64 eval=new EvalArm64(memory,1<<16);
                eval.regs[0]=2;
                assertEquals(0,eval.step(10000));
                assertEquals(525,eval.regs[0]);
            } else {
                EvalRisc5 eval=new EvalRisc5(memory,1<<16);
                eval.regs[10]=2;
                assertEquals(0,eval.step(10000));
                assertEquals(525,eval.regs[10]);
            }
        }
        // Field updates are local SSA values, including the old x used after
        // updating y and x. Only constructor exits should write these fields.
        String counter="struct Counter { int x; int y; int z; new Counter={int n -> while(x<n) { int old=x; y=x+1; x=y+1; z=old; }}; }; val c=new Counter(arg); return c.x*100+c.z;";
        var loop=new CodeGen(counter)
            .driver(CodeGen.Phase.LocalSched);
        assertEquals("0",Eval2.eval(loop,0));
        assertEquals("2018",Eval2.eval(loop,19));
        loop._stop.walk(n -> {
            if( n instanceof com.seaofnodes.simple.node.StoreNode st )
                assertEquals("Constructor Stores belong outside the loop",0,st.cfg0().loopDepth());
            return null;
        });
        var defaults=new CodeGen("struct Buffer { int[] data=new int[1]; }; val a=new Buffer; val b=new Buffer; a.data[0]=7; return b.data[0];")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("0",Eval2.eval(defaults,0));
        java.nio.file.Path dir=java.nio.file.Path.of("build/objs");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("construction.smp"),"val construct={int arg ->"+CONSTRUCT+"}; val count={int arg ->"+counter+"};");
        java.nio.file.Files.writeString(dir.resolve("construction.c"),"""
            extern long long construct(long long);
            extern long long count(long long);
            int main(void) { return construct(2)!=525 || construct(0)!=121 || count(19)!=2018 || count(0)!=0; }
            """);
        TestC.run("build/objs","construction","",-1);
    }

    @Test public void testCompletionBeforePublication() {
        String src="struct Box { int[] data; new Box={int n -> if(n) data=new int[2];}; }; return new Box(arg);";
        try { new CodeGen(src).driver(CodeGen.Phase.TypeCheck); fail(); }
        catch( Parser.ParseException e ) {
            assertEquals("'Box' is not fully initialized, field 'data' needs to be set in a constructor",e.getMessage());
        }
        for( String early : new String[]{
            "val use={!Box b -> b.x;}; struct Box { int x; new Box={->use(self);}; }; return new Box;",
            "struct Box { Box? next; new Box={->next=self;}; }; return new Box;",
            "struct Wrap { Box? item; }; struct Box { new Box={->new Wrap { item=self; };}; }; return new Box;"
        } ) {
            try { new CodeGen(early).driver(CodeGen.Phase.TypeCheck); fail(); }
            catch( Parser.ParseException e ) {
                assertEquals("Cannot publish self before the constructor finishes",e.getMessage());
            }
        }
    }

    @Test public void testForwardMethod() {
        var code=new CodeGen("val run={!Box b -> b.get();}; struct Box { int x=7; val get={->x;}; }; return run(new Box);")
            .driver(CodeGen.Phase.FinalizeIR);
        assertEquals("7",Eval2.eval(code,0));
        code=new CodeGen("struct Byte { new Byte={int n -> self.x=n; self.x+=3;}; u8 x; }; return new Byte(255).x;")
            .driver(CodeGen.Phase.FinalizeIR);
        assertEquals("2",Eval2.eval(code,0));
    }
}
