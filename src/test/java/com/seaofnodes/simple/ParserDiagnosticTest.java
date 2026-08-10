package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserDiagnosticTest {
    private static Parser.ParseException rejected(String source) {
        try {
            new CodeGen(source).driver(CodeGen.Phase.Parse);
            throw new AssertionError("Accepted invalid source");
        } catch( Parser.ParseException e ) {
            return e;
        }
    }

    private static void diagnostic(String source, int line, int column, String message) {
        var error=rejected(source);
        assertEquals(message,error.getMessage());
        assertEquals("Test.smp:"+line+":"+column+": error: "+message
            +"\n    "+source.split("\n",-1)[line-1]+"\n    "+"_".repeat(column-1)+"^",error.diagnostic());
    }

    @Test public void testNumericLiteralErrors() {
        diagnostic("return 9223372036854775808;",1,8,"Integer literal is outside the signed 64-bit range");
        diagnostic("return 1e;",1,8,"Invalid floating-point literal '1e'");
        diagnostic("return 1.2.3f;",1,8,"Invalid floating-point literal '1.2.3f'");
        diagnostic("return 012;",1,8,"Syntax error: integer values cannot start with '0'");
    }

    @Test public void testUnclosedCommentsAndStrings() {
        diagnostic("/* never closed",1,1,"Unclosed block comment");
        diagnostic("return 0; /*",1,11,"Unclosed block comment");
        diagnostic("return \"abc\n",1,8,"Unclosed string");
        diagnostic("return \"abc\\",1,8,"Unclosed string");
        // A comment at byte zero used to index -1; a final // was not recognized.
        new CodeGen("/* ok */ return 0; //").driver(CodeGen.Phase.Parse);
    }

    @Test public void testCharacterLiteralsAndEscapes() {
        diagnostic("return '';",1,8,"Empty character literal");
        diagnostic("return 'ab';",1,8,"Character literal must contain exactly one byte");
        diagnostic("return 'a ';",1,8,"Character literal must contain exactly one byte");
        diagnostic("return '",1,8,"Unclosed character literal");
        diagnostic("return 'a\n",1,8,"Unclosed character literal");
        diagnostic("return '\\q';",1,9,"Unknown character escape \\q");
        diagnostic("return \"\\q\";",1,9,"Unknown string escape \\q");
        new CodeGen("return ' ' + '\\'' + '\\\\';").driver(CodeGen.Phase.Parse);
    }

    @Test public void testDeferredLiteralErrors() {
        diagnostic("struct S {\n new S={ ->\n  val x='ab';\n };\n};",3,9,
                   "Character literal must contain exactly one byte");
        diagnostic("struct S {\n new S={ ->\n  val x=\"abc",3,9,"Unclosed string");
        diagnostic("for(int i=0; i<1; i+='ab') {}",1,22,
                   "Character literal must contain exactly one byte");
        diagnostic("struct S {\n new S={ ->\n  val x=\"outer %(\"bad\\q\")\";\n };\n};",3,22,
                   "Unknown string escape \\q");
    }

    @Test public void testIdentifierAndKeywordLocations() {
        diagnostic("int = 1;",1,5,"Expected an identifier, found '='");
        diagnostic("int true=1;",1,5,"Expected an identifier, found 'true'");
        diagnostic("int",1,4,"Expected an identifier, found 'EOF'");
        diagnostic("return if;",1,8,"Syntax error, expected `an identifier or expression` but found `if`");
        diagnostic("\n  break;",2,3,"No active loop for a break or continue");
        diagnostic("\n  continue;",2,3,"No active loop for a break or continue");
    }

    @Test public void testFinalAssignmentLocations() {
        diagnostic("val x=1;\n  x=2;",2,3,"Cannot reassign final 'x'");
        diagnostic("val x=1;\n  x++;",2,3,"Cannot reassign final 'x'");
        diagnostic("val x=1;\n  ++x;",2,5,"Cannot reassign final 'x'");
    }

    @Test public void testParameterAndConstructorLocations() {
        diagnostic("val f={int x, int x -> x;};",1,19,"Duplicate parameter 'x'");
        diagnostic("val f={int ^x -> x;};",1,12,"'^' requires a struct parameter");
        diagnostic("struct S { new Wrong={ -> }; };",1,16,"Constructor name 'Wrong' must match type name 'S'");
        diagnostic("int? x;",1,4,"Type i32 cannot be null");
        diagnostic("return new int;",1,12,"Cannot allocate a i32");
    }

    @Test public void testMemberLocation() {
        diagnostic("struct S { int x; };\nval s=new S;\nreturn s.missing;",3,10,
                   "Accessing unknown field 'missing' from '*Test.S'");
        diagnostic("struct S { int x; };\nval s=new S;\nreturn s.;",3,10,"Expected an identifier, found ';'");
    }

    @Test public void testInitializerAndOperatorLocations() {
        diagnostic("val x = null;",1,9,"Cannot infer a type from null; declare a nullable type explicitly");
        diagnostic("u8[] bytes=\"C\";",1,6,"C data binding requires an integer or float type");
        diagnostic("return 1 < 2 > 3;",1,14,"Mixing relational directions in a chained relational test");
        var shift=rejected("return 1 << 2.0;");
        assertTrue(shift.getMessage(),shift.getMessage().startsWith("Cannot '<<'"));
        assertEquals("Test.smp:1:10: error: "+shift.getMessage()
            +"\n    return 1 << 2.0;\n    _________^",shift.diagnostic());
    }

    @Test public void testCLILexicalErrorsWithoutStackTrace() throws Exception {
        checkCLI("return 9223372036854775808;",
            ":1:8: error: Integer literal is outside the signed 64-bit range"
            +"\n    return 9223372036854775808;\n    _______^\n");
        checkCLI("/* missing close",
            ":1:1: error: Unclosed block comment\n    /* missing close\n    ^\n");
    }

    @Test public void testMissingInitializer() {
        var error=rejected("// declaration\n\nval _validMove() {\n");
        assertEquals("Syntax error, expected `=expression` but found `(`",error.getMessage());
        assertEquals("Test.smp:3:15: error: "+error.getMessage()
            +"\n    val _validMove() {\n    ______________^",error.diagnostic());
    }

    @Test public void testFoundIdentifierPointsAtItsStart() {
        var error=rejected("int x=1\nreturn x;");
        assertEquals("Test.smp:2:1: error: Syntax error, expected `;` but found `return`"
            +"\n    return x;\n    ^",error.diagnostic());
    }

    @Test public void testEOF() {
        var error=rejected("return 1");
        assertEquals("Test.smp:1:9: error: Syntax error, expected `;` but found `EOF`"
            +"\n    return 1\n    ________^",error.diagnostic());
    }

    @Test public void testCRLFTabsAndUTF8() {
        var error=rejected("// π\r\n\tint x=1\r\n\treturn x;");
        assertEquals("Test.smp:3:9: error: Syntax error, expected `;` but found `return`"
            +"\n            return x;\n    ________^",error.diagnostic());
        var unicode=rejected("val x=\"π\" return 1;");
        assertEquals("Test.smp:1:11: error: Syntax error, expected `;` but found `return`"
            +"\n    val x=\"π\" return 1;\n    __________^",unicode.diagnostic());
    }

    @Test public void testDeferredForUpdateLocation() {
        // The update is parsed after the body, so lexer line counters can be stale.
        var error=rejected("for(int i=0; i<3; i+=) {\n    int x=i;\n}\nreturn 0;");
        assertEquals("Test.smp:1:22: error: Syntax error, expected `an identifier or expression` but found `)`"
            +"\n    for(int i=0; i<3; i+=) {\n    _____________________^",error.diagnostic());
    }

    @Test public void testCLIReportsSourceWithoutStackTrace() throws Exception {
        checkCLI("// declaration\nval _validMove() {\n",
            ":2:15: error: Syntax error, expected `=expression` but found `(`"
            +"\n    val _validMove() {\n    ______________^\n");
    }

    @Test public void testUndefinedNameKeepsItsUseSite() {
        var error=rejected("val make={ -> val board=new u8[9]; board; };\n"
            +"val read={ ->\n    return board[0];\n};\nreturn read();");
        assertEquals("Test.smp:3:12: error: Undefined name 'board'"
            +"\n        return board[0];\n    ___________^",error.diagnostic());
    }

    @Test public void testUndefinedAssignmentPointsAtName() {
        var error=rejected("\n    board = 1;");
        assertEquals("Test.smp:2:5: error: Undefined name 'board'"
            +"\n        board = 1;\n    ____^",error.diagnostic());
    }

    @Test public void testCLIUndefinedNameWithoutStackTrace() throws Exception {
        checkCLI("// unresolved\nreturn board[0];\n",
            ":2:8: error: Undefined name 'board'"
            +"\n    return board[0];\n    _______^\n");
    }

    private static void checkCLI(String text, String expected) throws Exception {
        Path dir=Files.createDirectories(Path.of("build/diagnostics/source with spaces"));
        Path source=dir.resolve("Bad.smp").toAbsolutePath();
        Files.writeString(source,text);
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process child=new ProcessBuilder(java,"-ea","-cp",System.getProperty("java.class.path"),
            Simple.class.getName(),"--norun","-o","build/diagnostics/objects/",source.toString()).start();
        try {
            assertTrue("Compiler did not exit",child.waitFor(20,TimeUnit.SECONDS));
            assertEquals(1,child.exitValue());
            assertEquals("",new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
            String diagnostic=new String(child.getErrorStream().readAllBytes(),StandardCharsets.UTF_8)
                .replace("\r\n","\n");
            assertEquals(source+expected,diagnostic);
        } finally {
            child.destroyForcibly();
        }
    }
}
