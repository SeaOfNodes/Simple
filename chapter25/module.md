
# Modules for Simple

Goals:
- 1-liner valid program (for easy tutorial/newbie starts)
- - Compile and run with minimal effort
- Separate compilation and namespaces; external libs
- - Compile to ELF or other linkable/executable format
- Easy private fields & classes
- Output files are compatible with existing .o/.exe/lib/dll infrastructure
- No `#include` or `import` files
- Structure / file system heirarchy per module
- - Perhaps many files make a module
- - easy namespaces within a module
- - Nested names in parent file or nested file
- Class initializers guarenteed:
- - execute exactly once
- - lazily on first touch
- - complete on or before first instances are made
- - - (exception allows for golden instances in <clinit>)


### Goal: Valid 1-liner, minimal effort

This makes a module and a class `hello` out of the current working directory
name.  Compiling without more arguments also links and executes.

---
File: hello.smp
```
sys.io.p("Hello World!");
```
---

Compile and run:
```
> smp hello.smp
Hello World!
```

Module and Class name is `hello` extracted from file name, compiled in memory
and `exec'd`.  No files made.


### Goal: separate compilation

Name a build directory target during compilation to get `.o` files.
The build target directory structure will match the source, and
does not have to be nested.

As before, since no module is mentioned on the compile line, `Pet` becomes both
the module and a class.

---
File: Pet.smp
```
// <clinit>
// static fields listed here
int default_leggies = 4;

// Default (no arg) <init> inside struct with same name as the file
struct Pet {
  str name;
  int leggies = default_leggies;
}

float yet_another_static_field;
```
---

Compile to `Pet.o` in the `build/` directory:
```
> smp Pet.smp -o build/
> ls build
Pet.o
```


### Goal: nested namespaces

This makes a module and class `Pet` based on the file name in `myRepo`.  It
also makes a nested class `Pet.Vet`.  These are used from a different module
`Owner`.

```
~/myRepo
  Pet.smp
  build/

~/otherRepo
  Owner.smp
```

---
File: myRepo/Pet.smp
```
int default_leggies = 4;

struct Pet {
  str name;
  int leggies = default_leggies;
  // no `struct` allowed here
}

// Nested class Vet inside of file Pet
struct Vet {
  str name;
  str address;
  long credit_card_num;
}
```
---
File: otherRepo/Owner.smp
```
Pet dog = Pet{name="Fido"};
Pet snake = Pet{name="slither", leggies = 0; };
Pet.Vet vet = Vet{name="Linda"; address="101 Main St"; credit_card_num=1234};

sys.io.p("I took "+dog.name+" to "+vet.name+"\n");
```
---

Compile to `Pet.o` in the `build/` directory; since the compile line does not
mention some other module root, `Pet` must be the module (and is also a class).
Since `Pet` is the module, `Vet` is a class within the module `Pet`.

```
> smp Pet.smp -o build/
> ls build
Pet.o
```

The code for both `Pet` and `Pet.Vet` are in the same `Pet.o` file.

Compile and run `Owner.smp`, specifying the `Pet` repo to satisfy the free
variables `Pet` and `Pet.Vet`.

```
> smp Owner.smp -lib ~/myRepo/build
I took Fido to Linda
```

### Goal: nested namespace in a nested file

Same as the last example but with nested files:

```
~/myRepo
  Pet.smp
  Pet/
    Vet.smp
  build/
    Pet.o
    Pet/
      Vet.o

~/otherRepo
  Owner.smp
```

---
File: Pet.smp
```
int default_leggies = 4;

struct Pet {
  str name;
  int leggies = default_leggies;
  // no `struct` allowed here
}
```
---
File: Pet/Vet.smp
```
// Nested class Vet inside of file Pet
struct Vet {
  str name;
  str address;
  long credit_card_num;
}
```
---

Compile and run `Owner.smp`:
```
> smp Owner.smp -lib ~/myRepo/build
I took Fido to Linda
```


### Goal: Class Initializers are Lazy

```
Parent.smp
Parent/
  ChildA.smp
  ChildB.smp
```

---
File: Parent.smp
```
sys.io.p("In Parent <clinit>");
sys.io.p(ChildB.str);
```
---
File: ChildA.smp
```
// <clinit> for ChildA
sys.io.p("In ChildA <clinit>");
str str = "A";
```
---
File: ChildB.smp
```
// <clinit> for ChildB
sys.io.p("In ChildB <clinit>");
str str = "B";
```

Compile and run:
```
> smp Parent.smp
In Parent <clinit>
In ChildB <clinit>
B

```

### Goal: Easy private fields

Leading underscore makes field or class private.

### Goal: similar to C compiler driver syntax

```
Simple --cpu x86_64_v2 --abi Win64 --norun -o lib/lib/sys_x86_64_v2_win64.o src/main/smp/sys.smp
```

Module not mentioned, so top-level file is module: `sys`
Module directory not mentioned, so is prefix of file name `src/main/smp`.
Build dir not mentioned, so is prefix of output file: `lib/`
Output file is default `smp.o`, here renamed.
