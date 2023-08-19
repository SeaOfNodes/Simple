package com.seaofnodes.simple.transpile.printers;

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
                use crate::sea_of_nodes::types::Types;""");

        if (tests.stream().anyMatch(t -> t.assertStopRetCtrlIsProj || t.assertStopRetCtrlIsCProj || t.assertStopRetCtrlIsRegion)) {
            out.println("use crate::sea_of_nodes::nodes::Op;");
        }
        if (tests.stream().anyMatch(t -> t.parseErrorMessage != null && !t.iterate)) {
            out.println("use crate::sea_of_nodes::tests::test_error;");
        }
        if (tests.stream().anyMatch(t -> t.parseErrorMessage != null && t.iterate)) {
            out.println("use crate::sea_of_nodes::tests::test_error_iterate;");
        }
        if (tests.stream().anyMatch(t -> t.irPrinter && !t.irPrinterLLVM)) {
            out.println("use crate::sea_of_nodes::ir_printer::pretty_print;");
        }
        if (tests.stream().anyMatch(t -> t.irPrinterLLVM)) {
            out.println("use crate::sea_of_nodes::ir_printer::pretty_print_llvm;");
        }
        if (tests.stream().anyMatch(t -> !t.evaluations.isEmpty())) {
            out.println("use crate::sea_of_nodes::tests::evaluator::{evaluate, Object};");
        }

        for (TestMethod test : tests) {
            printTest(test, out);
        }
    }

    private void printTest(TestMethod test, PrintStream out) {
        out.println();
        out.println("#[test]");
        out.println("fn " + snakeCase(test.name) + "() {");
        if (test.parseErrorMessage != null) {
            if (test.iterate) {
                out.println("test_error_iterate(" + string(test.parserInput) + ", " + string(test.parseErrorMessage) + ");");
            } else {
                out.println("test_error(" + string(test.parserInput) + ", " + string(test.parseErrorMessage) + ");");
            }
        } else if (test.parserInput != null) {
            out.println("let arena = DroplessArena::new();");
            out.println("let types = Types::new(&arena);");
            if (test.parserArg == null) {
                out.println("let mut parser = Parser::new(" + string(test.parserInput) + ", &types);");
            } else {
                out.printf("let mut parser = Parser::new_with_arg(%s, &types, %s);\n", string(test.parserInput), switch (test.parserArg) {
                    case TestMethod.Arg.IntBot ignored -> "types.ty_int_bot";
                    case TestMethod.Arg.IntTop ignored -> "types.ty_int_top";
                    case TestMethod.Arg.IntConstant c -> "types.get_int(" + c.value() + ")";
                });
            }
            if (test.disablePeephole) {
                out.println("parser.nodes.disable_peephole = true;");
            }
            out.println("let stop = parser.parse().unwrap();");
            if (test.showAfterParse) {
                out.println("parser.show_graph();");
            }
            if (test.iterate) {
                out.println("parser.iterate(stop);");
                out.println("parser.type_check(stop).unwrap();\n");
            }
            if (test.showAfterIterate) {
                out.println("parser.show_graph();");
            }
            if (test.irPrinter) {
                out.println("println!(\"{}\", pretty_print" + (test.irPrinterLLVM ? "_llvm" : "") + "(&parser.nodes, stop, 99));");
            }
            if (test.assertStopEquals != null) {
                out.println("assert_eq!(parser.print(stop), " + string(test.assertStopEquals) + ");");
            }
            if (test.assertStopRetCtrlIsProj) {
                out.println("assert!(matches!(parser.nodes.ret_ctrl(stop), Op::Proj(_)));");
            }
            if (test.assertStopRetCtrlIsCProj) {
                out.println("assert!(matches!(parser.nodes.ret_ctrl(stop), Op::CProj(_)));");
            }
            if (test.assertStopRetCtrlIsRegion) {
                out.println("assert!(matches!(parser.nodes.ret_ctrl(stop), Op::Region{..}));");
            }
            for (var evaluation : test.evaluations) {
                var result = switch (evaluation.result()) {
                    case null -> "Object::Null";
                    case Integer l -> "Object::Long(" + l + ")";
                    case Long l -> "Object::Long(" + l + ")";
                    case Float d -> "Object::Double(" + d + ")";
                    case Double d -> "Object::Double(" + d + ")";
                    case String s -> string(s);
                    default ->
                            throw new RuntimeException("unexpected evaluation result: " + evaluation.result().getClass());
                };
                out.println("assert_eq!(" + result + ", evaluate(&parser.nodes, stop, Some(" + evaluation.parameter() + "), None).1);");
            }
        } else {
            out.println("todo!();");
        }
        out.println("}");
    }

    private String string(String value) {
        var escaped = value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
        if (escaped.contains("\n")) {
            return "\"\\\n" + escaped + "\"";
        } else {
            return "\"" + escaped + "\"";
        }
    }

    private String snakeCase(String identifier) {
        return identifier.replaceAll("([A-Z]+)|([0-9]+)", "_$0").toLowerCase();
    }
}
