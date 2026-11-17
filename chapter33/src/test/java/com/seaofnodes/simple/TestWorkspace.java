package com.seaofnodes.simple;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Prepare a Make test group's working directory without sharing native outputs. */
public class TestWorkspace {
    public static void main(String[] args) throws IOException {
        if( args.length!=2 || !args[0].matches("tests_group[0-7]|sanity_group[0-2]") || !args[1].matches("[A-Za-z0-9_]+") )
            throw new IllegalArgumentException("Expected test group and CPU_ABI");
        Path root = Path.of("").toAbsolutePath().normalize();
        Path work = root.resolve("build/tests").resolve(args[0]).normalize();
        if( !work.startsWith(root.resolve("build/tests")) )
            throw new IllegalArgumentException("Test workspace outside build/tests");
        // Classes and JARs use the parent's absolute classpath. Copy source
        // fixtures and native libraries; tests may write their own build tree.
        for( String tree : new String[]{"src","docs","build/release/"+args[1],"build/objs/lib_"+args[1]} )
            sync(root.resolve(tree),work.resolve(tree));
        Files.createDirectories(work.resolve("build/objs"));
    }

    private static void sync(Path source, Path target) throws IOException {
        if( !Files.isDirectory(source) ) throw new IOException("Missing test input: "+source);
        // Remove deleted/renamed inputs as well as refreshing changed files;
        // stale modules could otherwise make a test pass incorrectly. Only
        // copied input trees are cleaned, preserving generated test artifacts.
        if( Files.exists(target) ) Files.walkFileTree(target,new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path original = source.resolve(target.relativize(file));
                if( attrs.isSymbolicLink() || !Files.isRegularFile(original) ) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if( error!=null ) throw error;
                if( !Files.isDirectory(source.resolve(target.relativize(dir))) ) Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.walkFileTree(source,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path copy = target.resolve(source.relativize(file));
                if( !Files.exists(copy) || Files.size(copy)!=attrs.size() ||
                    !Files.getLastModifiedTime(copy).equals(attrs.lastModifiedTime()) )
                    Files.copy(file,copy,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
