package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrivacyTest {
    private static void rejects(String src, String message) {
        for( long seed : new long[]{1,13,123} )
            try {
                new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
                fail("Accepted private access: "+src);
            } catch( Parser.ParseException e ) {
                assertTrue(e.getMessage(),e.getMessage().contains(message));
                assertNotNull(e._loc);
                assertTrue(e.getMessage(),e.getMessage().contains(": error: "));
            }
    }

    @Test public void testFieldsAndMethods() {
        String box = "struct Box { int _x=7; val _get={ -> _x; }; }; val b=new Box(); ";
        for( String use : new String[]{"return b._x;",
                "b._x=8; return 0;", "b._x+=1; return 0;", "b._x++; return 0;",
                "return b._get();", "val f=b._get; return 0;"} )
            rejects(box+use,"Private field");
        // Constant fields must not bypass the check in generic constant folding.
        rejects("struct Box { val _x=7; }; return new Box()._x;","Private field");
        rejects("struct Box { flt _x=7.0; }; return new Box()._x;","Private field");

        for( String use : new String[]{"b._x; return 0;",
                "if(false) b._x; return 0;"} ) {
            CodeGen dead = new CodeGen(box+use).driver(CodeGen.Phase.TypeCheck);
            assertEquals("0",Eval2.eval(dead,0));
        }

        CodeGen code = new CodeGen("""
            struct Box {
                int _x;
                new Box={int n -> _x=n; ++_x;};
                val _get={ -> _x; };
                val sum={Box other -> _get()+other._x; };
            };
            val a=new Box(2);
            return a.sum(new Box(3));
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("7",Eval2.eval(code,0));
        // An underscore on a function-local variable does not make it a field.
        code = new CodeGen("val run={int n -> int _x=n; ++_x; return _x;}; return run(arg);")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("5",Eval2.eval(code,4));
    }

    @Test public void testNestedOwners() {
        // One shared forward ref can have both legal and illegal source uses.
        rejects("val own={ -> _x; }; struct Child { val get={ -> _x; }; }; val _x=7; return own()+new Child().get();","Private field");
        rejects("struct Child { val get={ -> _x; }; }; val _x=7; return new Child().get();","Private field");
        rejects("struct Child { val get={ -> _f(); }; }; val _f={ -> 7; }; return new Child().get();","Private field");
        rejects("int _x=7; struct Child { val get={ -> _x; }; }; return 0;","Private field");
        rejects("int _x=7; struct Child { val get={ -> _x=8; }; }; return 0;","Private field");
        rejects("int _x=7; struct Child { val get={ -> ++_x; }; }; return 0;","Private field");
        rejects("""
            struct Outer {
                int _x=7;
                struct Inner { val get={Outer o -> o._x;}; };
            };
            return 0;
            ""","Private field");
        rejects("""
            struct Outer {
                struct Inner { int _x=7; };
                val get={Inner i -> i._x;};
            };
            return 0;
            ""","Private field");
        rejects("""
            struct A { int _x=7; };
            struct B { val get={A a -> a._x;}; };
            return 0;
            ""","Private field");
    }

    @Test public void testPrivateClasses() {
        // The private root class name is in scope throughout its own body,
        // including nested classes, without exposing its private fields.
        new CodeGen(null,null,null,"_Private",
            "struct Child { val root={ -> _Private; }; }; return 0;",
            123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        CodeGen code = new CodeGen("""
            struct _Box {
                int _x=7;
                val get={ -> _x; };
                val sum={_Box b -> _x+b._x;};
            };
            val b=new _Box();
            return b.sum(new _Box());
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("14",Eval2.eval(code,0));

        String outer = "struct Outer { struct _Hidden { int x=7; struct Public { int x=8; }; }; }; ";
        for( String use : new String[]{"return new Outer._Hidden();",
                "Outer._Hidden? x; return 0;", "return Outer._Hidden;",
                "return new Outer._Hidden.Public();"} )
            rejects(outer+use,"Private class");
        rejects("struct _Box { int _x=7; }; return new _Box()._x;","Private field");


    }

    static final String LIB = """
        struct _Hidden { int _x=7; val get={ -> _x; }; };
        val make={ -> new _Hidden(); };
        int _secret=11;
        val read={ -> _secret; };
        { i32 -> i32 } _exit="C";
        """;

    @Test public void testSourceModules() throws Exception {
        Path base = Path.of("build/objs/privacy");
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base,"source-");
        Files.writeString(root.resolve("App.smp"),"return 0;");
        Files.createDirectory(root.resolve("App"));
        Files.writeString(root.resolve("App/Lib.smp"),LIB);
        Files.writeString(root.resolve("App/Client.smp"),"return Lib.make().get()+Lib.read();");
        CodeGen code = new CodeGen(root.toString(),root.resolve("obj").toString(),null,
            "App/Client",null,123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertEquals("18",Eval2.eval(code,0));
        for( String use : new String[]{"return Lib._secret;", "Lib._secret=0; return 0;",
                "return Lib._exit;", "return new Lib._Hidden();",
                "Lib._Hidden? h; return 0;", "return Lib._Hidden;",
                "return Lib.make()._x;"} ) {
            Files.writeString(root.resolve("App/Client.smp"),use);
            try {
                new CodeGen(root.toString(),root.resolve("obj").toString(),null,
                    "App/Client",null,123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
                fail("Accepted private access: "+use);
            } catch( Parser.ParseException e ) {
                assertTrue(use+": "+e.getMessage(),e.getMessage().contains("Private "));
                assertTrue(e.getMessage(),e.getMessage().contains("Client.smp:"));
            }
        }
    }

    @Test public void testForwardReceiverAndUnusedAccess() {
        for( String use : new String[]{"return make()._x;", "return make()._get();",
                "val b=make(); b._x=9; return b;"} )
            rejects("""
                val run={ -> %s };
                struct Box { int _x=7; val _get={ -> _x; }; };
                val make={ -> new Box(); };
                return run();
                """.formatted(use),"Private field");
        CodeGen code = new CodeGen("""
            struct Box {
                int _x=7;
                val read={Box b -> identity(b)._x; };
                val identity={Box b -> b; };
            };
            val b=new Box();
            return b.read(b);
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("7",Eval2.eval(code,0));
        // Unused forward accesses disappear through ordinary DCE.
        for( String use : new String[]{"make()._x;", "val f=make()._get;"} ) {
            CodeGen dead = new CodeGen("val run={ -> "+use+" return 0; }; "+
                "struct Box { int _x=7; val _get={ -> _x; }; }; "+
                "val make={ -> new Box(); }; return run();").driver(CodeGen.Phase.TypeCheck);
            assertEquals("0",Eval2.eval(dead,0));
        }
        // A legal inlined setter must not erase the preceding illegal store.
        rejects("""
            val run={ -> val b=make(); b._x=1; b.set(2); return b.get(); };
            struct Box {
                int _x=7;
                val set={int n -> _x=n;};
                val get={ -> _x;};
            };
            val make={ -> new Box(); };
            return run();
            ""","Private field");
        // A dead call has no return type; it must not strand a valid check.
        code = new CodeGen("""
            struct Box {
                int _x=7;
                val read={Box b -> if(false) identity(b)._x; return 7; };
                val identity={Box b -> b; };
            };
            val b=new Box();
            return b.read(b);
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("7",Eval2.eval(code,0));
    }

    @Test public void testImportedPrivacyAndNativeWrapper() throws Exception {
        Path base = Path.of("build/objs/privacy");
        Files.createDirectories(base);
        Path lib = Files.createTempDirectory(base,"import-");
        new CodeGen(null,lib.toString(),null,"Lib",LIB,123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        Ary<String> paths = new Ary<>(new String[]{lib.toString()});
        for( long seed : new long[]{1,13,123} ) {
            CodeGen code = new CodeGen(null,null,paths,"Client",
                "return Lib.make().get()+Lib.read();",seed,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.TypeCheck);
            assertNull(code._compunits.get("Lib")._src);
            assertEquals("18",Eval2.eval(code,0));
        }
        for( String use : new String[]{"return Lib._secret;", "return Lib._exit;",
                "return new Lib._Hidden();", "Lib._Hidden? h; return 0;",
                "return Lib._Hidden;", "return Lib.make()._x;"} ) {
            try {
                new CodeGen(null,null,paths,"Client",use,123,true,TypeInteger.BOT)
                    .driver(CodeGen.Phase.TypeCheck);
                fail("Accepted imported private access: "+use);
            } catch( Parser.ParseException e ) {
                assertTrue(e.getMessage(),e.getMessage().contains("Private "));
                assertTrue(e.getMessage(),e.getMessage().contains("Client.smp:"));
            }
        }
        Path driver = lib.resolve("driver.c");
        Files.writeString(driver,"""
            __attribute__((CALL_CONV)) extern long long run(void);
            int main(void) { return run()!=18; }
            """);
        String abi = TestC.CALL_CONVENTION.equals("win64") ? "ms_abi" : "sysv_abi";
        TestC.run("val run={ -> Lib.make().get()+Lib.read(); };","privacyImported",paths,
            TestC.CALL_CONVENTION,abi,driver.toString(),"",-1);

        // Exercise the real private native binding through its public wrapper.
        TestC.run("sys.libc.exit(0); return 99;","privacyExit",
            new Ary<>(new String[]{"build/objs/lib_"+TestC.CPU_ABI}),
            TestC.CALL_CONVENTION,null,null,"",-1);
    }
}
