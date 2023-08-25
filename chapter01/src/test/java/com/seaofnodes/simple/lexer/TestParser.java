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
import com.seaofnodes.simple.parser.Ast;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Assert;
import org.junit.Test;

public class TestParser {

    @Test
    public void testParseSingleDecl()
    {
        final String source = "var i: Int;";
        Lexer lexer = new Lexer(new Source(source), new ErrorListener());
        Parser parser = new Parser(lexer);
        var ast = parser.parse();
        Assert.assertTrue(ast instanceof Ast.Block
            && ((Ast.Block) ast).stmtList.size() == 1);
    }

    @Test
    public void testParseTwoDecls() {
        final String source = "var i: Int;\nvar j: Int;";
        Lexer lexer = new Lexer(new Source(source), new ErrorListener());
        Parser parser = new Parser(lexer);
        var ast = parser.parse();
        Assert.assertTrue(ast instanceof Ast.Block
                && ((Ast.Block) ast).stmtList.size() == 2);
    }

    @Test
    public void testIfAndWhile() {
        final String source =
                """
                var i: Int;
                var j: Int;
                
                i = 0;
                j = 100;
                while ( 1 ) {
                    i = i + 1;
                    j = j - 1;
                    if ( i >= j ) break;
                }
                """;
        Lexer lexer = new Lexer(new Source(source), new ErrorListener());
        Parser parser = new Parser(lexer);
        var ast = parser.parse();
    }
}
