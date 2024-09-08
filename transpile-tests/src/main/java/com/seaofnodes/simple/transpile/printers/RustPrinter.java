package com.seaofnodes.simple.transpile.printers;

import com.seaofnodes.simple.transpile.Arg;
import com.seaofnodes.simple.transpile.JUnitParser;
import com.seaofnodes.simple.transpile.TestClass;
import com.seaofnodes.simple.transpile.TestMethod;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RustPrinter {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: <path-to-simple-repo> <output-dir>");
            System.exit(1);
        }
        var printer = new RustPrinter();
        var output = Path.of(args[1]);

        for (var e : JUnitParser.parseRepository(Path.of(args[0])).entrySet()) {
            printer.printToFile(e.getKey(), e.getValue(), output);
        }
    }

    private void printToFile(String chapter, List<TestClass> testClasses, Path outputDirectory) throws IOException, InterruptedException {
        var output = outputDirectory.resolve(chapter);
        Files.createDirectories(output);
        for (var testClass : testClasses) {
            var outputFile = output.resolve(testClass.name().replace("Test", "").toLowerCase() + ".rs");
            System.out.println(outputFile);
            Files.write(outputFile, printAndRustfmt(testClass.methods()));
        }
    }

    private byte[] printAndRustfmt(ArrayList<TestMethod> tests) throws IOException, InterruptedException {
        var p = Runtime.getRuntime().exec(new String[]{"rustfmt"});
        try (var print = new PrintStream(new BufferedOutputStream(p.getOutputStream()))) {
            printFile(tests, print);
        }
        p.waitFor();

        var errors = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!errors.isEmpty() || p.exitValue() != 0) {
            throw new RuntimeException("rustfmt exit=" + p.exitValue() + " stderr=" + errors);
        }

        return p.getInputStream().readAllBytes();
    }

    private void printFile(ArrayList<TestMethod> tests, PrintStream out) {
        out.println("""
                use crate::datastructures::arena::DroplessArena;
                use crate::sea_of_nodes::parser::Parser;
                use crate::sea_of_nodes::types::Types;
                """);

        for (TestMethod test : tests) {
            if (test.parseErrorMessage != null) {
                out.println("use crate::sea_of_nodes::tests::test_error;");
                break;
            }
        }

        for (TestMethod test : tests) {
            printTest(test, out);
        }
    }

    private void printTest(TestMethod test, PrintStream out) {
        out.println();
        out.println("#[test]");
        out.println("fn " + test.name + "() {");
        if (test.parseErrorMessage != null) {
            out.println("test_error(" + test.parserInput + ", " + test.parseErrorMessage + ")");
        } else if (test.parserInput != null) {
            out.println("let arena = DroplessArena::new();");
            out.println("let types = Types::new(&arena);");
            out.printf("let mut parser = Parser::new(%s%s, &types);\n", multiline(test.parserInput), switch (test.parserArg) {
                case null -> "";
                case Arg.IntBot ignored -> ", types.ty_int_bot";
                case Arg.IntTop ignored -> ", types.ty_int_top";
                case Arg.IntConstant c -> ", types.get_int(" + c.value() + ")";
            });
            out.println("let stop = parser.parse().unwrap();");
            if (test.showAfterParse) {
                out.println("parser.show_graph();");
            }
            if (test.assertStopEquals != null) {
                out.printf("assert_eq!(parser.print(stop), %s);\n", test.assertStopEquals);
            }
        }
        out.println("}");
    }

    private String multiline(String string) {
        if (!string.contains("\\n")) {
            return string;
        }
        return string.replaceFirst("^\"", "\"\\\\\\\\n").replace("\\n", "\n");
    }
}
