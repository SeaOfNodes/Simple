package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

public class MethodFieldsTest {
    @Test public void nestedFunctionStillRejectsLocalCapture() {
        String source = """
            struct Game {
                int count;
                val input = { int amount ->
                    val action = { -> count += amount; };
                    action();
                };
            };
            return 0;
            """;
        try {
            new CodeGen(source).driver(CodeGen.Phase.Parse);
            fail("Captured a nonconstant local parameter");
        } catch( Parser.ParseException e ) {
            assertEquals("Variable 'amount' is out of function scope and must be a final constant",e.getMessage());
        }
    }

    @Test public void nestedFunctionReceiver() throws Exception {
        String source = """
            struct Game {
                bool held, paused;
                int count;
                val debounce = { bool pressed, bool wasHeld, { bool } action ->
                    if( pressed && !wasHeld ) action();
                    return pressed;
                };
                val bump = { -> count++; return false; };
                val input = { bool pressed ->
                    if( true ) {
                        held = debounce(pressed,held,{ ->
                            paused = !paused;
                            bump();
                            val again = { -> count += 2; return false; };
                            again();
                            return false;
                        });
                    }
                };
                val named = { -> debounce(true,false,bump); };
            };
            val a = new Game, b = new Game;
            a.input(true); a.input(true);
            if( a.count != 3 || !a.paused || b.count != 0 || b.paused ) return 1;
            a.input(false); a.input(true);
            if( a.count != 6 || a.paused ) return 2;
            b.input(true); b.named();
            if( b.count != 4 || !b.paused || a.count != 6 ) return 3;
            return 0;
            """;
        assertEquals("0",Eval2.eval(new CodeGen(source).driver(CodeGen.Phase.LocalSched),0));
        TestC.runSF("val main = { -> "+source+" };","nestedFunctionReceiver","",-1);
    }

    @Test public void laterSiblingParameterType() throws Exception {
        String source = """
            struct Game {
                val input = { !Controls keys -> keys.presses++; return keys.presses; };
                val read = { Controls keys -> keys.presses; };
            };
            struct Controls { int presses = 4; };
            val game = new Game, keys = new Controls;
            if( game.input(keys) != 5 || game.read(keys) != 5 ) return 1;
            return 0;
            """;
        assertEquals("0",Eval2.eval(new CodeGen(source).driver(CodeGen.Phase.LocalSched),0));
        TestC.runSF("val main = { -> "+source+" };","forwardMethodType","",-1);
    }

    private static final String FIELDS = """
        int count = 100;
        struct Counter {
            val step = { int n ->
                count = count+n;
                ++count;
                count += 2;
                speed *= 2.0;
                return read();
            };
            val local = { int count -> count+1; };
            val read = { -> count; };
            int count = 3;
            f64 speed = 1.5;
        };
        val a = new Counter, b = new Counter;
        if( a.step(4) != 10 || b.read() != 3 || a.speed != 3.0 ) return 1;
        if( a.local(20) != 21 || a.read() != 10 || count != 100 ) return 2;
        if( b.step(1) != 7 || a.read() != 10 ) return 3;
        return 0;
        """;

    @Test public void laterFieldsAndMethods() throws Exception {
        assertEquals("0",Eval2.eval(new CodeGen(FIELDS).driver(CodeGen.Phase.LocalSched),0));
        TestC.runSF("val main = { -> "+FIELDS+" };","methodFields","",-1);
    }

    @Test public void laterGlobalAndRecursiveMethod() {
        String source = """
            struct Counter {
                val step = { i64 n ->
                    if( n == 0 ) return value;
                    value += increment;
                    return step(n-1);
                };
                int value = 1;
            };
            val increment = 2;
            return new Counter.step(3);
            """;
        assertEquals("7",Eval2.eval(new CodeGen(source).driver(CodeGen.Phase.LocalSched),0));
    }

    @Test public void laterFinalFieldStillRejectsWrites() {
        String source = "struct C { val change = { -> x = 2; }; int ~x = 1; }; return new C.change();";
        try {
            new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
            fail("Method wrote a final field");
        } catch( Parser.ParseException e ) {
            assertEquals("Cannot reassign final 'x'",e.getMessage());
        }
    }
}
