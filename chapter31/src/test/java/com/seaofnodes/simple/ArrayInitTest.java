package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArrayInitTest {
    private static void value(String src, long expected) {
        CodeGen code=new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals(Long.toString(expected),Eval2.eval(code,0,100000));
    }

    private static void bad(String src, String message) {
        try {
            new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
            fail("Expected: "+message);
        } catch( com.seaofnodes.simple.parser.Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains(message));
        }
    }

    @Test public void lists() {
        value("val a=new u16[3]{025755,1,65538}; return a#*100000+a[0]+a[1]+a[2];",311248);
        value("val a=new int[]{4,7,9,}; a[1]=2; return a#+a[0]+a[1]+a[2];",18);
        value("val a=new int[]{}; return a#;",0);
        bad("val a=new int[2]{1}; return a[0];","initializer has 1 elements");
    }

    @Test public void generators() {
        value("int n=6; val a=new int[n]{int i -> i*i;}; return a[5]+a#;",31);
        value("int base=7; val a=new int[3]{int i -> return base+i;}; return a[2];",9);
        value("val a=new int[3]{int i -> if(i==1) return 9; i+1;}; return a[0]+a[1]+a[2];",13);
        value("val a=new int[0]{int i -> 1/i;}; return a#;",0);
        bad("val a=new int[]{int i -> i;}; return a#;","requires a length");
        bad("val f={-> int x=1; val a=new int[2]{int i -> x++;}; return a[0];}; return f();","captured local");
    }

    @Test public void referencesAndReadonly() {
        value("struct S { int x; new S={i64 v -> x=v;}; }; val a=new S[~3]{int i -> new S(i+4);}; return a[2].x;",6);
        value("val a=new u8[~]{1,2,3}; return a[1];",2);
        value("val names=new u8[~][~2]{\"Alice\",\"Bob\"}; return names[1][0];",66);
        value("val a=new int[ ~ ]{4,5}; return a[1];",5);
        value("val a=new {i64 -> i64}[]{ {i64 x -> x+1;}, {i64 x -> x*2;} }; return a[1](7);",14);
        value("struct S { int x=1; }; val a=new !S[1]{new S}; a[0].x=8; return a[0].x;",8);
        bad("struct S { int x=1; }; val a=new !S[~1]{new S}; a[0].x=8; return a[0].x;","final field");
        value("struct S { int x; }; val a=new S?[~2]{u32 i -> null;}; return a[1]==null;",1);
        bad("val a=new int[~2]{1,2}; a[0]=3; return a[0];","final field");
        bad("struct S { int x; }; val a=new S[1]{null}; return a#;","declared type");
    }

    @Test public void nativeInitializers() throws Exception {
        TestC.runSF("""
            val main = { ->
                int n=8;
                val a=new u16[~n]{int i -> i*i+1;};
                val b=new u8[]{257,2,3};
                return a[7]==50 && b[0]==1 && a#==8 ? 0 : 1;
            };
            ""","arrayInitializers","",-1);
    }

    @Test public void nestedBodies() {
        value("""
            val f={ ->
                int offset=5;
                val a=new int[4]{int i ->
                    int sum=offset;
                    for(int j=0;j<i;j++) { if(j==1) continue; sum+=j; }
                    if(i==2) return 20;
                    return sum;
                };
                return a[0]+a[1]+a[2]+a[3];
            };
            return f();
            """,37);
        value("""
            val a=new int[][3]{int i ->
                new int[2]{int j -> i*10+j;};
            };
            return a[2][1]+a[0][0];
            """,21);
        value("""
            val a=new int[2]{int i ->
                val twice={i64 x -> return x*2;};
                twice(i);
            };
            return a[1];
            """,2);
    }

    @Test public void orderAndIdentity() throws Exception {
        TestC.runSF("""
            struct Counter {
                int calls=0;
                val next={->calls++;};
            };
            struct Item {
                int x;
                new Item={i64 v -> x=v;};
            };
            val build_noInline={u32 n, !Counter c ->
                new Item[~n]{u32 i -> new Item(c.next());};
            };
            val main={->
                val c=new Counter;
                val empty=build_noInline(0,c);
                if(empty#!=0 || c.calls!=0) return 1;
                val a=build_noInline(4,c);
                if(c.calls!=4 || a[0].x!=0 || a[3].x!=3 || a[0]==a[1]) return 2;
                val b=new int[]{c.next(),c.next(),c.next()};
                if(b[0]!=4 || b[2]!=6) return 3;
                val d=new int[c.next()]{i64 i -> c.next();};
                if(d#!=7 || d[0]!=8 || d[6]!=14 || c.calls!=15) return 4;
                return 0;
            };
            ""","arrayInitOrder","",-1);
    }

    @Test public void diagnostics() {
        bad("val f={u32 n -> new int[n]{1,2};}; return f(arg)#;","length must equal that constant");
        bad("val a=new int[2]{u8 i -> i;}; return a[0];","generator index");
        bad("val a=new int[2]{int return -> 0;}; return a[0];","index name");
        bad("val a=new int[2]{int i -> i++;}; return a[0];","final 'i'");
        bad("val f={->int x=0; return new int[2]{int i -> x=i;};}; return f()#;","captured local");
        bad("val a=new int[2]{int i -> break;}; return a[0];","No active loop");
        bad("val a=new int[-1]{}; return a#;","outside the u32 range");
        bad("val a=new int[1.5]{int i -> i;}; return a#;","integer");
        bad("val a=new int[~]{1,2}; val b=a; b[0]++; return b[0];","final field");
        for( String init : new String[]{"self","int i -> self;"} )
            bad("struct C { int x; new C={-> val a=new C[1]{"+init+"}; x=a#;}; }; return new C().x;",
                "fully initialized elements");
    }

    @Test public void forwardReferencesAndLoopScopes() {
        value("""
            val f={ ->
                int sum=0;
                for(int k=0;k<3;k++) {
                    val a=new int[2]{int i ->
                        if(i) return later(i);
                        later(3);
                    };
                    if(k==1) continue;
                    sum+=a[0]+a[1];
                }
                return sum;
            };
            val later={i64 i -> i*2;};
            return f();
            """,16);
    }

    @Test public void optimizationOrder() {
        String src="""
            struct Box { int x; new Box={i64 v -> x=v;}; };
            val f={u32 n ->
                int base=7;
                val a=new Box[~n]{u32 i ->
                    int x=base;
                    for(int j=0;j<i;j++) x+=j;
                    if(i==2) return new Box(20);
                    new Box(x);
                };
                return n==0 ? 0 : a[n-1].x;
            };
            return f(arg&7);
            """;
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code=new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("0",Eval2.eval(code,0));
            assertEquals("20",Eval2.eval(code,3));
            assertEquals("13",Eval2.eval(code,5));
        }
    }
}
