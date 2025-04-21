package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeConAryB;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMemPtr;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiteralTest {
    private static final String[] ESCAPES = {"\\0", "\\n", "\\t", "\\r", "\\\\", "\\\"", "\\'"};
    private static final int[] VALUES = {0, 10, 9, 13, 92, 34, 39};

    @Test public void testStringEscapes() {
        for (int i = 0; i < ESCAPES.length; i++)
            assertString(ESCAPES[i], new byte[]{(byte)VALUES[i]});
        assertString("a\\\"b\\\\c\\0d", new byte[]{97, 34, 98, 92, 99, 0, 100});
        assertString("", new byte[]{});
        assertString("plain", new byte[]{112, 108, 97, 105, 110});
    }

    private static void assertString(String text, byte[] expected) {
        var code = new CodeGen("return \"" + text + "\";").parse().opto().typeCheck();
        TypeMemPtr ptr = (TypeMemPtr)code.expr()._type;
        TypeConAryB bytes = (TypeConAryB)ptr._obj._con;
        assertArrayEquals(text, expected, bytes._ary);
    }

    @Test public void testCharacterEscapes() {
        for (int i = 0; i < ESCAPES.length; i++)
            assertCharacter(ESCAPES[i], VALUES[i]);
        assertCharacter("A", 65);
    }

    private static void assertCharacter(String text, int expected) {
        var code = new CodeGen("return '" + text + "';").parse().opto().typeCheck();
        assertEquals(text, expected, ((TypeInteger)code.expr()._type).value());
    }

    @Test public void testMalformedStrings() {
        assertBadLiteral("return \"\\q\";", "Unknown string escape");
        assertBadLiteral("return \"abc", "Unclosed string");
        assertBadLiteral("return \"abc\\", "Unclosed string");
        // The quote closes the string at EOF; the missing token is the semicolon.
        assertBadLiteral("return \"plain\"", ";");
    }

    @Test public void testMalformedCharacters() {
        assertBadLiteral("return '\\q';", "Unknown character escape");
        assertBadLiteral("return '", "Unclosed character");
        assertBadLiteral("return '\\", "Unclosed character");
        assertBadLiteral("return '';", "Syntax error");
        assertBadLiteral("return 'ab';", "Syntax error");
        assertBadLiteral("return 'a", "Syntax error");
    }

    private static void assertBadLiteral(String src, String message) {
        try {
            new CodeGen(src).parse();
            fail("Accepted malformed literal: " + src);
        } catch (Parser.ParseException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
        }
    }
}
