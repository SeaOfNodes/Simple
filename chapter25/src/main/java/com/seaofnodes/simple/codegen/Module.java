package com.seaofnodes.simple.codegen;


/***
    Modules!?!?!?

    Goal: under no circumstances can you load from an uninitialized variable
    (although you can see default inits for fields that have defaults).
    Goal: nested tree-structured name spaces, to control complexity.
    Goal: field decl order within a file is fairly flexible.

    A Module a root-level SMP file, and the full file path must be provided on
    the command line.  All other Simple structs are relative to this file.

    Example: "simple /user/Dave/hello.smp"

    Defines a module "hello" in directory "/user/Dave", compiles and produces
    a `hello` or `hello.exe` output file, and runs it.

    A SMP file has some <finit> file-init code which is static-global.
    This code is executed on "first touch" of the file (defined below).

    Example: hello.smp contains:
        sys.io.p("Hello World!");
    On first-touch, will print "Hello World!".

    A SMP file is a lexical scope, and code inside it can reference those names.

    Example: hello.smp contains:
        val greet = "Hello World!"; // Declare file-scoped variable
        sys.io.p(greet);            // Reference file-scoped variable

    Static code is NOT allowed to have forward-references AND be executed
    before the <finit> finishes initializing all variables.  After var
    initialization, a singleton instance of the implied file struct is made,
    and then <finit> code can call anything.

    Example:
        sys.io.p(greet); // Illegal forward reference
        val greet = "Oops!";

    Example:
        val delay = { -> greet };    // Legal forward ref, but cannot execute (yet)
        val greet = "Hello World!";  // Ref is resolved
        sys.io.p(delay());           // Allowed to call delay()
        val nprimes = { N ->
          // compute number of primes < N
        }
        // Can otherwise execute any code in a <finit>
        sys.io.p(nprimes(100));

    Inside a directory, you can have sibling SMP files:
    File hello.smp contains:
        sys.io.p(I9A.GREETING);  // File hello references sibling file I9A static globals
    File I9A.smp contains:
        val GREETING = translate("Hello World!");
        val translate = { str msg -> ... };

    <finit> code cannot reference sibling files (nor child files, see below)
    until all static variables are initialized.  This prevents cyclic
    initialization bugs, where file A uses vars from file B and vice-versa.

    File Chicken.smp contains:
        val lay = Egg.hatch; // Illegal, defining var 'lay' after calling sibling Egg
    File Egg.smp contains:
        val hatch = Chicken.lay; // Illegal

    You can make cross references, if they get delayed until after all fields
    are declared.

    File Chicken.smp contains:
        int lay = 2;
        lay = Egg.egg;

    File Egg.smp contains:
        int egg = 3;
        egg = Chicken.lay;

    The problem here, of course, is that you need to touch one of Chicken or
    Egg first.


    Inside a SMP file, you can define structs; structs are also a lexical
    scope.  Bare code in the struct is part of the struct's <init> and only
    runs when an instance is created.  Structs do not have static <clinit> (nor
    <finit>).  Structs <init> cannot have any forward references, and locally
    declared functions cannot have both forward references and be executed (or
    passed along to a possibly-executing function).

    In the same directory as a SMP file, Simple allows other directories with
    names matching SMP files.  Inside these dirs can be more SMP files that are
    lexically nested, similar to embedded structs.


*/

public abstract class Module {
}
