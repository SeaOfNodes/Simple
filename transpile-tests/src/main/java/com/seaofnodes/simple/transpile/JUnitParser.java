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
        if (inputs == null)
            throw new IOException("failed to read Simple project root");
        Arrays.sort(inputs);
        for (File file : inputs) {
            var chapter = file.getName();
            if (chapter.matches("chapter\\d+")) {
                chapters.put(chapter, parseChapter(file.toPath()));
            }
        }
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
        var compilationUnits = compiler
                .getStandardFileManager(null, null, null)
                .getJavaFileObjectsFromPaths(tests);

        var task = (JavacTask) compiler.getTask(null, null, null, null, null, compilationUnits);

        var visitor = new AstVisitor();
        for (var tree : task.parse()) {
            try { tree.accept(visitor, null); }
            catch( IllegalArgumentException iae ) {}
        }

        var testClasses = new ArrayList<>(visitor.results.values());
        testClasses.sort(comparing(TestClass::name));
        return testClasses;
    }
}
