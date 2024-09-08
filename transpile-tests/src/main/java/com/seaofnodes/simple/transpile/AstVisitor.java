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
            if (node.getMethodSelect().toString().endsWith(".parse")) {
                current.showAfterParse = switch (node.getArguments().size()) {
                    case 0 -> false;
                    case 1 -> (Boolean) ((LiteralTree) node.getArguments().getFirst()).getValue();
                    default -> throw new RuntimeException("unexpected parse arguments" + node);
                };
            }
            if (node.getMethodSelect().toString().endsWith("assertEquals")) {
                var args = node.getArguments();
                if (inCatch) {
                    if (args.size() != 2)
                        throw new RuntimeException("Unexpected number of arguments " + node);
                    if (args.get(0).toString().contains("e.getMessage()")) {
                        current.parseErrorMessage = args.get(1).toString();
                    } else if (args.get(1).toString().contains("e.getMessage()")) {
                        current.parseErrorMessage = args.get(0).toString();
                    }
                } else {
                    if (args.size() != 2)
                        throw new RuntimeException("Unexpected number of arguments " + node);
                    if (args.get(0).toString().contains("stop.toString()")) {
                        current.assertStopEquals = args.get(1).toString();
                    } else if (args.get(1).toString().contains("stop.toString()")) {
                        current.assertStopEquals = args.get(0).toString();
                    }
                }
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
                    case 2 -> current.parserArg = Arg.parse(args.get(1).toString());
                    default -> throw new RuntimeException("Parser constructor with unexpected arguments: " + node);
                }
                current.parserInput = args.get(0).toString();
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
}
