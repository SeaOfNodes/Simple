package com.seaofnodes.simple.transpile;

import com.sun.source.util.JavacTask;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.util.Comparator.comparing;

public class JUnitParser {

    /**
     * @param simpleRepo The path to the project root
     * @return A sorted map from chapter name to a sorted list of parsed test classes.
     */
    public static TreeMap<String, List<TestClass>> parseRepository(Path simpleRepo) throws IOException {
        var chapters = new TreeMap<String, List<TestClass>>();
        var inputs = simpleRepo.toFile().listFiles(File::isDirectory);
        if (inputs == null) throw new IOException("failed to read Simple project root");
        Arrays.sort(inputs);
        for (File file : inputs) {
            var chapter = file.getName();
            if (chapter.matches("chapter\\d+")) {
                if (Integer.parseInt(chapter.substring("chapter".length())) >= 19) {
                    System.out.println("TODO: parse " + chapter);
                    continue;
                }
                chapters.put(chapter, parseChapter(file.toPath()));
            }
        }
        checkTransitionToCodeGen(chapters);
        return chapters;
    }

    /**
     * @param input The path to a chapter in the repository.
     * @return a sorted list of parsed test classes
     */
    public static List<TestClass> parseChapter(Path input) throws IOException {
        var tests = new ArrayList<Path>();
        Files.walkFileTree(input, Set.of(), 20, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().matches("Chapter\\d+Test\\.java")) {
                    tests.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        var compiler = ToolProvider.getSystemJavaCompiler();
        var compilationUnits = compiler.getStandardFileManager(null, null, null).getJavaFileObjectsFromPaths(tests);

        var task = (JavacTask) compiler.getTask(null, null, null, null, null, compilationUnits);

        var visitor = new AstVisitor();
        for (var tree : task.parse()) {
            tree.accept(visitor, null);
        }

        var testClasses = new ArrayList<>(visitor.results.values());
        testClasses.sort(comparing(TestClass::name));
        return testClasses;
    }

    private static void checkTransitionToCodeGen(TreeMap<String, List<TestClass>> chapters) {
        var chapter17 = chapters.get("chapter17");
        var chapter18 = chapters.get("chapter18");

        for (int i = 0; i < 17; i++) {
            var t1 = chapter17.get(i);
            var t2 = chapter18.get(i);
            assertEquals(t1.name(), t2.name());
            var clazz = t1.name();

            var ms1 = t1.methods().stream().toList();
            var ms2 = t2.methods().stream().filter(m -> !(clazz.equals("Chapter13Test") && m.name.equals("testCoRecur2"))).filter(m -> !(clazz.equals("Chapter17Test") && m.name.matches("testInc9|testInt10|testVar16|testVar17|testTrinary6"))).toList();

            System.out.println(t1.name());
            assertEquals(ms1.size(), ms2.size());
            for (int j = 0; j < ms1.size(); j++) {
                checkMethod(clazz, ms1.get(j), ms2.get(j));
            }
        }
    }

    private static void checkMethod(String clazz, TestMethod m1, TestMethod m2) {
        assertEquals(m1.name, m2.name);
        System.out.println("    " + m1.name);

        assertEquals(m1.parserInput != null, m2.parserInput != null);
        if (!(m1.parserArg instanceof TestMethod.Arg.IntBot && m2.parserArg == null)) {
            assertEquals(m1.parserArg, m2.parserArg);
        }

        if (clazz.equals("Chapter10Test") && m2.name.equals("testBug3")) {
            return; // new one checks for error
        }

        assertEquals(m1.parseErrorMessage != null, m2.parseErrorMessage != null);

        if (!(clazz.equals("Chapter02Test") && m2.name.equals("testParseGrammar")) && !(clazz.equals("Chapter03Test") && m2.name.equals("testVarScopeNoPeephole"))) {
            assertEquals(m1.disablePeephole, m2.disablePeephole);
            assertEquals(m1.showAfterParse, m2.showAfterParse);
        }
        if (!clazz.equals("Chapter05Test") && m2.name.equals("testMerge4")) {
            assertEquals(m1.iterate, m2.iterate);
        }
        assertEquals(m1.showAfterIterate, m2.showAfterIterate);
        assertEquals(m1.irPrinter, m2.irPrinter);
        assertEquals(m1.irPrinterLLVM, m2.irPrinterLLVM);
        assertEquals(m1.assertStopEquals != null, m2.assertStopEquals != null);
        assertEquals(m1.assertStopRetCtrlIsProj, m2.assertStopRetCtrlIsProj);
        assertEquals(m1.assertStopRetCtrlIsCProj, m2.assertStopRetCtrlIsCProj || m2.assertCtrlIsFun);
        assertEquals(m1.assertStopRetCtrlIsRegion, m2.assertStopRetCtrlIsRegion);
        if (m1.evaluations.size() != m2.evaluations2.size()) {
            assertEquals(m1.evaluations, m2.evaluations);
        }
    }

    private static <T> void assertEquals(T a, T b) {
        if (!Objects.equals(a, b)) {
            throw new RuntimeException(a + " != " + b);
        }
    }

}
