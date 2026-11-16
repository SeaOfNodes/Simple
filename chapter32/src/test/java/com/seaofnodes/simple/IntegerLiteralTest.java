package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeFloat;
import org.junit.Test;
import static org.junit.Assert.*;

public class IntegerLiteralTest {
    private static void value(String expression, long expected) {
        var code = new CodeGen("return "+expression+";").parse();
        assertEquals(expression,TypeInteger.constant(expected),code.expr()._type);
    }

    @Test public void valuesAndBitPatterns() {
        value("0x00e47b57",14973783);
        value("0XfF",255);
        value("025755",11245); // A: rows 010, 101, 111, 101, 101.
        value("000000",0);
        value("0x7fffffffffffffff",Long.MAX_VALUE);
        value("0x8000000000000000",Long.MIN_VALUE);
        value("0xffffffffffffffff",-1);
        value("0777777777777777777777",Long.MAX_VALUE);
        value("01000000000000000000000",Long.MIN_VALUE);
        value("01777777777777777777777",-1);
        value("0x00000000000000000001",1);
        value("-0x2a + 052",0);
        value("0x1e+2",32); // e and f are hex digits, not float markers.
        value("0xff & 017",15);
        value("017<<0x4",240);
        value("0x0f",15);
        value("0123",83);
        value("000000000000000000000000000000000001",1);
        assertEquals(TypeFloat.constant(12.5),new CodeGen("return 012.5;").parse().expr()._type);
        assertEquals(TypeFloat.constant(12.0),new CodeGen("return 012f;").parse().expr()._type);
    }

    @Test public void nativeLiterals() throws Exception {
        TestC.runSF("""
            val check_noInline = { i64 mask, u32 color, int glyph ->
                return mask==-1 && color==14973783 && glyph==11245 ? 0 : 1;
            };
            val main = { -> return check_noInline(0xffffffffffffffff,0x00e47b57,025755); };
            ""","basedLiterals","",-1);
    }
}
