package com.seaofnodes.simple;

import com.seaofnodes.isa.eval.EvalRisc5;

import com.seaofnodes.isa.eval.EvalArm64;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegAllocTestSupport;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.TypeMemPtr;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

// Execution regressions are separate from the fixed spill-measurement cohorts.
public class MemoryEncodingTest {
    @Test public void testLoopLoadSearch() {
        String[] sources = {
            """
            u8[] !program=new u8[106]; u8[] !out=new u8[0];
            int sum=0;
            for(int i=0; i<arg; i++) {
                sum+=program#;
                if(i==1) out=new u8[i+1];
                else if(i==3) out=new u8[2];
            }
            return sum+out#;
            """,
            // One possibly aliasing arm must reject the unchanged-memory proof.
            """
            struct S { int x; }; !S !a=new S; !S !b=new S;
            a.x=10; b.x=20; !S !p=a; if(arg&1) p=b;
            int sum=0;
            for(int i=0; i<6; i++) {
                if(i==2) p.x=30;
                else if(i==4) b.x=40;
                sum+=a.x;
            }
            return sum;
            """,
            // Unchanged memory does not imply an unchanged loop-carried pointer.
            """
            struct S { int x; }; !S !a=new S; !S !b=new S;
            a.x=10; b.x=20; !S !p=a; int sum=0;
            for(int i=0; i<6; i++) {
                if(i==2) a.x=30;
                sum+=p.x;
                p=b; if(i&1) p=a;
            }
            return sum;
            """,
            // All arms fold, but their values differ: build a value Phi.
            """
            struct S { int x; }; !S !a=new S; a.x=1; int sum=0;
            for(int i=0; i<6; i++) {
                sum+=a.x;
                if(i&1) a.x=2;
                else if(i==2) a.x=3;
                else a.x=4;
            }
            return sum;
            """
        };
        for( int p=0; p<sources.length; p++ ) {
            CodeGen code=new CodeGen(sources[p]).parse().opto();
            if( p==0 ) assertNull(code._stop.walk(n ->
                n instanceof LoadNode ld && ld.ptr() instanceof ProjNode ptr &&
                ptr.in(0) instanceof NewNode nn &&
                nn.size()._type==com.seaofnodes.simple.type.TypeInteger.constant(110) ? ld : null));
            for( int arg=0; arg<7; arg++ ) {
                long expected=switch(p) {
                case 0 -> 106*arg+(arg>=2 ? 2 : 0);
                case 1 -> (arg&1)==0 ? 140 : 60;
                case 2 -> 130;
                default -> 16;
                };
                assertEquals(Long.toString(expected),Eval2.eval(code,arg));
            }
        }
    }

    private static final String[] SOURCES = {
        Chapter16Test.CONSTRUCTOR_MEMORY,
        Chapter18Test.CALL_MEMORY,
        Chapter18Test.RECURSIVE_MEMORY,
        Chapter18Test.INLINE_MEMORY,
        "int[] !a = new int[7]; a[arg]=arg+3; return a[0]+1;",
        """
        int[] !a = new int[7];
        for( int i=0; i<7; i++ ) a[i]=i+arg;
        for( int i=0; i<6; i++ ) a[i+1]+=a[i];
        return a[6];
        """
    };

    private static long expected(int program, long arg) {
        return switch(program) {
        case 0 -> arg==0 ? 111249 : 222351+arg;
        case 1 -> arg==0 ? 10101325 : 20202322;
        case 2 -> (10*(arg+1)+arg*(arg+1)/2)*100+10+arg;
        case 3 -> 400944+arg*10001;
        case 4 -> arg==0 ? 4 : 1;
        case 5 -> 21+7*arg;
        default -> throw new AssertionError();
        };
    }

    @Test public void testEmulatedMemory() throws IOException {
        for( String target : new String[]{"riscv","arm"} )
            for( int p=0; p<SOURCES.length; p++ ) {
                CodeGen code = new CodeGen(SOURCES[p]).driver(target,"SystemV",null);
                RegAllocTestSupport.checkRegisters(code);
                int entry=0;
                for( var n : code._start._outputs )
                    if( n instanceof FunNode fun && "main".equals(fun._name) )
                        entry=code._encoding._opStart[fun._nid];
                for( int arg=0; arg<7; arg++ ) {
                    byte[] image = new byte[1<<20];
                    byte[] bits = code._encoding.bits();
                    System.arraycopy(bits,0,image,0,bits.length);
                    long result;
                    if( target.equals("riscv") ) {
                        EvalRisc5 cpu = new EvalRisc5(image,1<<16);
                        cpu._pc=entry;
                        cpu.regs[riscv.A0]=arg;
                        assertEquals(target+" program "+p+" arg "+arg,0,cpu.step(100000));
                        result=cpu.regs[riscv.A0];
                    } else {
                        EvalArm64 cpu = new EvalArm64(image,1<<16);
                        cpu._pc=entry;
                        cpu.regs[arm.X0]=arg;
                        assertEquals(target+" program "+p+" arg "+arg,0,cpu.step(100000));
                        result=cpu.regs[arm.X0];
                    }
                    assertEquals(target+" program "+p+" arg "+arg,expected(p,arg),result);
                }
            }
    }

    @Test public void testNeverMemory() {
        // A synthetic exit must retain both precise slices of a no-exit loop.
        for( String target : new String[]{"x86_64_v2","riscv","arm"} ) {
            CodeGen code = new CodeGen("""
                struct S { int x; int y; };
                !S !a=new S;
                if( arg ) while( 1 ) { a.x+=arg; a.y+=a.x; }
                return a.x+a.y;
                """).driver(CodeGen.Phase.LoopTree);
            assertNotNull(code._stop.walk(n -> {
                if( n instanceof MemMergeNode mem &&
                    mem.in(0) instanceof CProjNode exit && exit.in(0) instanceof NeverNode ) {
                    var fields = ((TypeMemPtr)Parser.TYPES.get("S"))._obj._fields;
                    assertTrue(mem.alias(fields[0]._alias) instanceof MemPhiNode);
                    assertTrue(mem.alias(fields[1]._alias) instanceof MemPhiNode);
                    return mem;
                }
                return null;
            }));
            code.instSelect(target,"SystemV").GCM().localSched().regAlloc();
            RegAllocTestSupport.checkRegisters(code);
            code.encode();
        }
    }

    @Test public void testNativeMemory() throws IOException {
        Files.createDirectories(Path.of("build/objs"));
        for( int p=0; p<SOURCES.length; p++ ) {
            String file="build/objs/memory"+p;
            StringBuilder c = new StringBuilder("extern long long memory(long long);\nint main() {\n");
            for( int arg=0; arg<7; arg++ )
                c.append("if(memory(").append(arg).append(")!=").append(expected(p,arg)).append("LL) return ").append(arg+1).append(";\n");
            c.append("return 0;\n}\n");
            Files.writeString(Path.of(file+".c"),c);
            TestC.run("val memory = { int arg -> "+SOURCES[p]+" };",
                TestC.CALL_CONVENTION,null,"",file+".c",file,"S","",-1);
        }
    }
}
