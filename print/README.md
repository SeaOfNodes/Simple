# Shared debug printers

Chapters share printing algorithms here while retaining the small hooks that
describe their own nodes. This module imports no compiler classes and requires
only the JDK. `make -C print tests` runs its independent contracts; the root
`make tests` also runs them.

## BaseNode: the common node view

Each chapter's `Node` extends `BaseNode<Node>`. The base provides the node ID
field, naming, `print()`/`toString()`, and the expression hook. The compiler
allocates IDs as before, passing one to `super(...)`; cloning still assigns a
fresh ID from the compiler's allocator. Graph storage and rewrites remain local.

The common read-only contract is `in(i)`, `nIns()`, `out(i)`, and `nOuts()`.
Optional dependency access defaults to an empty set. Existing `label()` and
`isDead()` methods fit directly; `typeName()` exposes the chapter's available
type text. Both printers and the viewer use this contract, so their adapters
need no ID, edge, dead-node, or dependency forwarding methods.

## Expressions: format strings or fluent hooks

Chapter 2's arithmetic nodes need just one line:

```java
@Override protected String format() { return "(%1+%2)"; }
```

`%1` means input slot 1, `%10` means slot 10, and `%%` prints a literal `%`.
Null or unattached inputs print `____`. A missing `format()` uses the node's
label. All expression prints track repeats; constants whose type text fits in
32 characters expand every time. Larger constants use their unique name on
repeat. Naming defaults to `label()+_nid`, with no separate constant/cast/guard
prefix protocol; constants use the label `Con` and print their value normally.

Variable-length or conditional output uses the protected hook:

```java
@Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
    p.p("Phi(");
    for( Node in : _inputs ) p.n(in).p(",");
    return p.unchar(',').close();
}
```

`p()` appends text, `n()` prints a node in the same context, and `open()`/`close()`
append parentheses. `p.prt(this,"(%1+%2)")` also works inside a custom hook.
An outside-package subclass can override either hook without exposing it as a
public compiler API. Calling an operand's `toString()` starts a separate
context; use `n()` or a format slot to retain repeat tracking.

`unchar(',')` and `unchar(", ")` remove only a matching trailing separator.
`unchar()` and `unchar(n)` remove one or several characters unconditionally.
All return the context for chaining.

## IR dumps

From Chapter 8, a chapter's `IRPrinter` is an `IRAdapter<Node>` plus the familiar
debugger entry points. The common node view supplies raw edges, identity, and
labels; the adapter adds node kinds and chapter-specific type spelling. Features
have defaults so early chapters need not implement functions, modules, or memory
annotations.

The supported paths are `Node.toString()`/`print()` for compact expressions,
`Node.p(depth)` from Chapter 8 for a bounded IR dump, and `CodeGen.toString()`
from Chapter 18 for a whole-program dump. Chapters 20-24 use the recorded
schedule after scheduling. `IRPrinter.printLine(Node, SB)` remains for evaluator
traces and compiler diagnostics; there is no separate `Node._printLine` entry
point. The viewer captures `BaseNode` facts through `SimpleGraphObserver` and
`SimpleGraphAdapter`, independently of the text printers.

The shared IR printer owns row formatting, bounded backward slices, CFG reverse
postorder, Phi and projection grouping, and diagnostic data placement. It walks
control before data, keeping a loop's closing merge below its body. Function
dumps stop at call-graph boundaries. Chapter 25 supplies compilation-unit roots
and function ownership; the shared code does not know its module representation.
Scheduled whole-program dumps retain the compiler's existing block order.

Printer state is private. Never compute dominators, schedule code, reorder use
lists, resolve aliases, or compute a type's layout to produce a dump. The code
must tolerate missing inputs during graph construction. Print callbacks must
obey the same read-only rule.

## Assembly listings

`CodeGen.asm()` is the assembly debugger entry point from Chapter 19 onward;
inside `RegAlloc`, use `_code.asm()`. It calls the chapter's `ASMPrinter`
adapter and then the shared assembly printer. Keep this path even when it has
no ordinary source callers: it is an intentional debugger API.

Register spelling comes from `CodeGen.reg(...)`: `N<node-id>` before a live
range exists, `V<LRG-id>` while that range has no chosen register, then the
selected register or stack location. Diagnostic LRG lookup follows leaders
without compressing them or changing the node mapping. After encoding, the
same entry point includes recorded instruction bytes and constant-pool dumps
(and static data where supported).

From Chapter 19, `AssemblyAdapter<Node>` supplies machine-specific instruction
spelling, register names, visibility, and scheduled blocks. Machine nodes retain
their `asm` hooks. Encoding chapters additionally expose recorded instruction
offsets, lengths, bytes, and constant-pool metadata. The shared implementation
owns traversal, columns, Phi rows, byte order, multiline instructions, and data
sections. Type sizes and alignment come from encoding metadata, never fresh
layout queries.

## Build integration

`print.mk` adds the shared sources to each chapter's Make build, just as the
viewer adds its sources through `graph.mk`. Maven source paths and IDEA module
dependencies include `print/` too. The graph module depends on `print/` for
`BaseNode`; Make does not require Maven. Linearized
checkouts carry the shared directory and find it at their repository root.
