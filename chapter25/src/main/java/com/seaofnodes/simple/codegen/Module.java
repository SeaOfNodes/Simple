package com.seaofnodes.simple.codegen;




/***
Modules can refer to other modules, in a DAG, no cycles.
Module is also a class; a class comes from a file (or textually nested in another class, same as a file)
Class has a <clinit>, run once before any class instances can be made.
No fref's in a <clinit>, run top-to-bottom, can run any code there.
TODO: Need the java stall-if-wrong-thread for a multi-threaded <clinit> where several threads can ask for the same class

Each module has its *own* Global Table of Modules.
Will get some repeat modules (e.g. %smp)
But linker should pick the most recent, so everybody agrees on the same <clinit>

Global table of modules
  class:foo? %foo = null; // Module vars are just *classes*, set to null if not yet init'd; 3rd state for in-progress
  class:smp? %smp = null;
  ... more modules...

Entry point:
  %foo = new struct %foo;
  return %foo.<clinit>();  // in progress, will e.g. call %smp.<clinit>

--- File: foo.smp ----
1;
----------------------

struct foo {}; // no fields


class:foo { // class same as object/struct
  // Empty list of not-init'd sub-modules

  //
  val <clinit> = { self -> 1; }
}




During a <clinit>, if reference another module, check & recursive init:

--- File: helloWorld.smp ----
sys.io.p("hello");
----------------------

class:helloWorld { // class
  val <clinit> = { self ->
    // Lazy clinit for MODULE/class sys
    if( !%sys ) %sys = new class:sys().<clinit>()
    // Lazy clinit for class sys.io
    if( !%sys.%io ) %sys.%io = new class:sys.io.<clinit>();
    // Call print
    %sys.io.p("hello");
    // NOTICE MISSING ALL OTHER SYS NESTED CLASSES (e.g. ary, Scan, etc)
  }
};

class:sys { // class sys
  class:Scan? %Scan;
  class:ary?  %ary ;
  class:io?   %io  ;
  class:libc? %libc;

  class:io { // class sys.io
    val p = { str -> ... }
    val other_fcns=...;
    // <clinit> for io; since io.p refers to `libc` and `ary` must init here.
    if( !%sys.%libc ) %sys.%libc = new class:sys.libc.<clinit>();
    if( !%sys.%ary  ) %sys.%ary  = new class:sys.ary .<clinit>();
  }

  // No code in sys.<clinit>
  // So no sub-class init.
};

// FORWARD REFS
// auto-promote out of inner-most containing fcn;
// may promote out to top-level, and if so do extern lookup

--- File: test.smp

val fact = { x ->
  x ? 1 :
    fact // <<-- FRef promotes to test.<clinit>
};
// Assign fact, finds FREF, defs FREF

val foo = { ->
  int qux = bar+1;  // bar FREF, promotes to test.<clinit>
  int bar = qux+1;  // defines local 'bar'
  return bar;       // local bar scope ends
}
int bar=99; // defs FREF bar in test.<clinit>

=======================================================


Get a no-arg constructor if all fields can be init without args.
Does not have to be zero-init, if you can init from outside.

You can write an arg constructor
... no back to what i have...
if you need a not-null fill, write it inline.
If you want to force the fill in the struct, e.g. for extra checking, force a privaet final field.

struct ERRORHasPrivateFinal {
  int _x; // has private final needs init
  int _y=0; // has private final, and has init
}

var x = new ERRORHasPrivateFinal{}; // Error private final field _x not initialized
var x = new ERRORHasPrivateFinal{_x=99}; // Error cannot init private field _x

struct OKHasPrivateFinal {
  int _x; // has private final needs init
  val make { int x -> new OKHasPrivateFinal{ _x=x; } }
}
var x = new OKHasPrivateFinal{ _x=99; } // Error cannot init private field _x
var x = OKHasPrivateFinal.make(99)


=======================================================

Allocation:

--- File: Foo.smp --------------
struct Foo {
  int !x;     // Default init of 0
  String str; // Not-null, final
};
Foo GOLD = new Foo{str="GOLD"};
GOLD.x++;
--------------------------------

fcn <init>(Foo self) {
  mem = st(ctrl,mem,self,"x",0);
  return(ctrl,mem,self);
}

fcn <clinit> {

  // Set ts to final type post alloc: Foo:{ !x=0; str="str"; }
  (nnn,mem) = NewNode(ctrl,size,mem,ts);
  // returns merged memory

  (ctrl,mem,nnn) = Call Foo.<init>(nnn);

  mem = st(ctrl,mem[Foo.str],nnn,"str",con("GOLD"), sets_final=true);
  // Parser checks all fields set
  // Upcast nnn, why bother?
  assert/cast(nnn, Foo:{ !x=0, str="GOLD" });

  mem = st(ctrl,mem[class:Foo.GOLD],CLZ,"GOLD",nnn);

  i = ld(ctrl,mem[Foo.x],nnn,"x");
  i2 = add(i,1)
  mem = st(ctrl,mem[Foo.x],nnn,"x",i2);

}

-----------------------------------------------
Allocation: this time using private "self_mem"

The <init> call:
Fun(callers)
- phi_RPC
- phi_public_mem
- phi_self
- phi_self_mem [ts{x:TOP, str:TOP}]

self_mem = st(null,self_mem,phi_self_mem, "x", con(0))  [ts:{x:0, str:TOP}]
Return(Fun,public_mem, self_mem, phi_RPC)



Usage of init call:

NewNode[ts](ctrl, size)
- proj_ptr
- proj_self_mem [ts{x:TOP, str:TOP}]

Call(ctrl, public_mem, proj_ptr, proj_self_mem, tfp:<init>)
CallEnd
- ctrl
- proj_public_mem
- proj_self_mem [ts:{x:0, str:TOP}]

self_mem =
  st(null, proj_self_mem, self, "str", con("GOLD")) [ts:{x:0, str:"GOLD"}]

Parser checks no TOP,BOT fields in self_mem
Escape(public_mem, self_mem, ptr)

--------------------------------------------
EscapeNode:
Load/Store vs escape:
- if can tell ld/st ptr == Escape ptr, then move "into"  escape
- if can tell ld/st ptr != Escape ptr, then move "aside" escape

*/

public abstract class Module {
}
