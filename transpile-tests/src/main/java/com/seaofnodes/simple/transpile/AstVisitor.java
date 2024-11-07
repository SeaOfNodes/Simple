package com.seaofnodes.simple.transpile;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;

import java.util.ArrayList;
import java.util.HashMap;

class AstVisitor extends TreeScanner<Void, Void> {
    final HashMap<String, TestClass> results = new HashMap<>();

    private String className;
    private TestMethod current;
    private boolean inCatch;

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        var name = node.getSimpleName().toString();
        if (name.matches("Chapter\\d+Test")) {
            var backup = className;
            className = name;
            super.visitClass(node, unused);
            className = backup;
        }
        return null;
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        if (className != null) {
            current = new TestMethod();
            current.name = node.getName().toString();
            super.visitMethod(node, unused);
            results.compute(className, (k, v) -> {
                if (v == null)
                    v = new TestClass(className, new ArrayList<>());
                v.methods().add(current);
                return v;
            });
            current = null;
        }
        return null;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (current != null) {
            var methodSelect = node.getMethodSelect().toString();
            var args = node.getArguments();

            if (methodSelect.endsWith(".parse")) {
                current.showAfterParse = switch (args.size()) {
                    case 0 -> false;
                    case 1 -> (Boolean) literal(args.getFirst());
                    default -> throw new RuntimeException("unexpected parse arguments" + node);
                };
            }
            if (methodSelect.endsWith(".iterate")) {
                current.iterate = true;
                current.showAfterIterate = switch (args.size()) {
                    case 0 -> false;
                    case 1 -> (Boolean) literal(args.getFirst());
                    default -> throw new RuntimeException("unexpected parse arguments" + node);
                };
            }
            if (methodSelect.endsWith("assertEquals")) {
                if (inCatch) {
                    if (args.size() != 2)
                        throw new RuntimeException("Unexpected number of arguments " + node);
                    if (args.get(1).toString().contains("e.getMessage()")) {
                        current.parseErrorMessage = (String) literal(args.get(0));
                    }
                } else {
                    if (args.size() != 2)
                        throw new RuntimeException("Unexpected number of arguments " + node);
                    if (args.get(1).toString().contains("evaluate(")) {
                        // visit evaluate(..) or evaluate(..).toString()
                        var eval = args.get(1).accept(new TreeScanner<MethodInvocationTree, Void>() {
                            @Override
                            public MethodInvocationTree reduce(MethodInvocationTree r1, MethodInvocationTree r2) {
                                return r1 != null ? r1 : r2;
                            }

                            @Override
                            public MethodInvocationTree visitMethodInvocation(MethodInvocationTree n, Void unused1) {
                                if (n.getMethodSelect().toString().endsWith(".evaluate"))
                                    return n;
                                return super.visitMethodInvocation(n, unused1);
                            }
                        }, null);

                        Object result = literal(args.get(0));
                        var evalArgs = eval.getArguments();
                        if (evalArgs.size() != 2 || !evalArgs.get(0).toString().equals("stop"))
                            throw new RuntimeException("Unexpected eval arguments " + node);
                        long parameter = Long.parseLong(literal(evalArgs.get(1)).toString());
                        current.evaluations.add(new TestMethod.Evaluation(result, parameter));
                    } else if (args.get(1).toString().endsWith(".toString()") || args.get(1).toString().endsWith(".print()")) {
                        if (current.assertStopEquals == null) {
                            // Assume the first one prints the stop node. Ignore assertions from evaluator:
                            //     assertEquals("int[] {\n  # :int;\n  [] :int;\n}",obj.struct().toString());
                            current.assertStopEquals = (String) literal(args.get(0));
                        }
                    }
                }
            }
            if (methodSelect.endsWith("assertTrue")) {
                var arg = args.getFirst().toString();
                current.assertStopRetCtrlIsProj = arg.contains("stop.ret().ctrl() instanceof ProjNode");
                current.assertStopRetCtrlIsCProj = arg.contains("stop.ret().ctrl() instanceof CProjNode");
                current.assertStopRetCtrlIsRegion = arg.contains("stop.ret().ctrl() instanceof RegionNode");
            }
            if (methodSelect.endsWith("prettyPrint")) {
                if (args.size() < 2 || args.size() > 3 || !args.get(0).toString().equals("stop") || !args.get(1).toString().equals("99"))
                    throw new RuntimeException("unexpected prettyPrint args " + node);
                current.irPrinter = true;
                current.irPrinterLLVM = args.size() == 3 && (Boolean) literal(args.get(2));
            }
        }
        return super.visitMethodInvocation(node, unused); // otherwise we would miss the first part of new Parser("").parse()
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        if (current != null) {
            if (node.getIdentifier().toString().equals("Parser")) {
                var args = node.getArguments();
                switch (args.size()) {
                    case 1 -> {
                    }
                    case 2 -> current.parserArg = switch (args.get(1).toString()) {
                        case "TypeInteger.BOT" -> new TestMethod.Arg.IntBot();
                        case "TypeInteger.TOP" -> new TestMethod.Arg.IntTop();
                        case String s -> {
                            var parts = s.split("[()]");
                            if (parts.length != 2 | !parts[0].equals("TypeInteger.constant"))
                                throw new RuntimeException("Unexpected Argument " + s);
                            yield new TestMethod.Arg.IntConstant(Long.parseLong(parts[1]));
                        }
                    };
                    default -> throw new RuntimeException("Parser constructor with unexpected arguments: " + node);
                }
                current.parserInput = (String) literal(args.get(0));
            } else if (node.getIdentifier().toString().equals("GraphVisualizer")) {
                // only happens in Chapter02Tests
                current.showAfterParse = true;
            }
        }
        return super.visitNewClass(node, unused);
    }

    @Override
    public Void visitCatch(CatchTree node, Void unused) {
        inCatch = true;
        super.visitCatch(node, unused);
        inCatch = false;
        return null;
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void unused) {
        if (current != null && node.getVariable().toString().equals("Node._disablePeephole")) {
            if ((Boolean) literal(node.getExpression())) {
                current.disablePeephole = true;
            }
        }
        return super.visitAssignment(node, unused);
    }

    private Object literal(ExpressionTree t) {
        return ((LiteralTree) t).getValue();
    }
}
