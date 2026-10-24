package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import org.junit.Test;
import static org.junit.Assert.*;

// The new module boundary must preserve the preceding chapters' examples.
public class BoundaryTest {
    @Test public void testLocalConstruction() throws java.io.IOException {
        String src="struct Counter { int x; int y; int z; new Counter={int n -> while(x<n) { int old=x; y=x+1; x=y+1; z=old; }}; }; val c=new Counter(arg); return c.x*100+c.z;";
        CodeGen code=new CodeGen(src).driver(CodeGen.Phase.LocalSched);
        assertEquals("2018",Eval2.eval(code,19));
        assertEquals("0",Eval2.eval(code,0));
        code._stop.walk(n -> {
            if( n instanceof StoreNode st ) assertEquals(0,st.cfg0().loopDepth());
            return null;
        });
        code=new CodeGen("struct Byte { new Byte={int n -> self.x=n; self.x+=3;}; u8 x; }; return new Byte(255).x;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("2",Eval2.eval(code,0));
        // data is still unset when the constructor reads and updates x.
        code=new CodeGen("struct Box { int x=7; int[] data; new Box={ -> self.x++; int n=self.x; data=new int[n]; }; }; return new Box().data#;")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("8",Eval2.eval(code,0));
        for( String field : new String[]{"data","self.data"} ) {
            try {
                new CodeGen("struct Box { int[] data; new Box={ -> int n="+field+"#; data=new int[n]; }; }; return new Box();")
                    .driver(CodeGen.Phase.TypeCheck);
                fail("An uninitialized field must not be read");
            } catch( Parser.ParseException e ) {
                assertTrue(e.getMessage(),e.getMessage().contains("might not be initialized"));
            }
        }
        java.nio.file.Path cfile=java.nio.file.Path.of("build/objs/constructionBoundary.c");
        java.nio.file.Files.createDirectories(cfile.getParent());
        java.nio.file.Files.writeString(cfile,"""
            __attribute__((CALL_CONV)) extern long long count(long long);
            int main(void) { return count(19)!=2018 || count(0)!=0; }
            """);
        TestC.run("val count={int arg ->"+src+"};","constructionBoundary",null,TestC.CALL_CONVENTION,
                  TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi",cfile.toString(),"",-1);
    }

    @Test public void testRecursiveCallback() {
        String src="""
            val run={int n ->
                val power={int base,int exponent -> if(exponent<=0) return 1; return base*power(base,exponent-1);};
                val sumPowers={int n,{int int -> int} f -> if(n<=0) return 0; return f(2,n)+sumPowers(n-1,f);};
                return sumPowers(n,power);
            };
            return run(arg);
            """;
        for( long seed : new long[]{2,13,123} ) {
            CodeGen code=new CodeGen(src,seed,true).driver(CodeGen.Phase.Opto);
            ParmNode base=code._start.walk(n -> n instanceof ParmNode p && p._label.equals("base") ? p : null);
            assertNotNull(base);
            assertSame(TypeInteger.constant(2),base._type);
            code.driver(CodeGen.Phase.TypeCheck);
            assertEquals("14",Eval2.eval(code,3));
        }
    }

    @Test public void testNativeExposure() {
        for( String tail : new String[]{
            "retain(power); return power(2,n);",
            "if(n) { retain(power); while(true) {} } return power(2,n);"
        } ) {
            String src="val run={int n -> val power={int base,int exponent -> if(exponent<=0) return 1; return base*power(base,exponent-1);}; { {int int -> int} -> int } retain=\"C\";"+tail+"}; return run(arg);";
            CodeGen code=new CodeGen(src).driver(CodeGen.Phase.Opto);
            ParmNode base=code._start.walk(n -> n instanceof ParmNode p && p._label.equals("base") ? p : null);
            assertNotNull(base);
            assertSame(TypeInteger.BOT,base._type);
            code.driver(CodeGen.Phase.TypeCheck);
        }
    }

    @Test public void testLazyTables() {
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code=new CodeGen("src/test/smp/lazy","build/objs",null,"App/Main",null,seed,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.TypeCheck);
            assertEquals("0",Eval2.eval(code,0));
            assertEquals("125",Eval2.eval(code,1));
        }
    }
}
