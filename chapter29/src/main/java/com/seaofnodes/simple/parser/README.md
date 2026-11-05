# Parser layout

The parser builds Sea-of-Nodes IR directly. Each component shares the cursor,
active scope, and compilation state through its `Parser P` reference; there is no intermediate
AST. The [grammar map](../../../../../../../docs/29-grammar.md) connects language
productions to their entry points.

| File | Responsibility |
| --- | --- |
| `Parser.java` | Source-unit entry point, shared state, type registry, and common token/IR helpers. |
| `Lexer.java` | Tokens, literals, comments, source-location snapshots, and scanning deferred bodies. |
| `Statements.java` | Statement dispatch, blocks, loops, and jumps. |
| `Expressions.java` | Assignment and expression precedence, unary operators, primaries, and postfix dispatch. |
| `Declarations.java` | Bindings, initializers, mutability modifiers, and native declarations. |
| `Types.java` | Type syntax, qualified-name lookup, arrays, function types, and forward types. |
| `Functions.java` | Function expressions, parameters, arguments, calls, and function entry/return graphs. |
| `Structs.java` | Struct and constructor declarations, field discovery, and initialization Stores. |
| `Construction.java` | Selects private self memory during parsing; its comment explains BOT fields, the TypeMemPtr qualifier, and checks deferred to TypeCheck. |
| `References.java` | Bare-name and field resolution, implicit receivers, assignments, and forward references. |
| `Allocations.java` | Allocation expressions, strings, and declaration-side allocation helpers. |
| `ControlFlow.java` | Guarded scope merging shared by `if`, `?:`, `&&`, and `||`. |
| `Conversions.java` | Shared binding, field, and result conversions. |
| `Var.java` | Lexical binding metadata: name, type, scope slot, and mutability. |

Start with `Statements.parseStatement` or `Expressions.parseAssignment` to follow
the grammar. Loop updates are parsed after their bodies, and declared constructor
bodies after field discovery; `Lexer.skipTo` scans these deferred fragments.

`Parser` keeps the short cursor, scope/memory, IR-building, and diagnostic helpers
used by these components. Independent helpers live with their data: class-object
names in `TypeStruct`, postfix operator encoding in `Lexer`, known field-offset
creation in `ConFldOffNode.make`, and memory-alias labels in `ScopeNode.memName`.

Instance constructor fields live in ordinary memory, with one hidden `#selfMem`
binding per activation. Forward field types use `TypeScalar.BOT` for missing
initialization; there are no per-field snapshots or store-all constructor exits.
