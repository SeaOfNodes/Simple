/**
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.seaofnodes.simple.lexer;

import com.seaofnodes.simple.common.ErrorListener;
import com.seaofnodes.simple.lexer.ecstasy.Lexer;
import com.seaofnodes.simple.lexer.ecstasy.Source;
import com.seaofnodes.simple.lexer.ecstasy.Token;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestLexer {

    @Test
    public void testLexLineComment()
    {
        final String source = "//a comment\nx";
        Lexer lexer = new Lexer(new Source(source), new ErrorListener());
        Token token = lexer.next();
        Assertions.assertEquals(token.getId(), Token.Id.EOL_COMMENT);
        token = lexer.next();
        Assertions.assertEquals("x", token.getValueText());
    }
}
