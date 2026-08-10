# Grammar for Chapter 30

English | [日本語](30-grammar.ja.md)

The handwritten recursive-descent implementation lives in
[`com.seaofnodes.simple.parser`](../../src/main/java/com/seaofnodes/simple/parser/).
The production-to-code map below the grammar describes where syntax ends and
name resolution or graph construction begins. This grammar documents the
language; it is not input to a parser generator.

`int` is an alias for `i32`; `i64` explicitly selects 64-bit integer storage.
This alias does not change literal inference or intermediate arithmetic.

```antlrv4
grammar SimpleLanguage;

// Name lookup, assignable operands, type compatibility, and access permissions
// are checked by the compiler in addition to these syntax rules.
// A file is a compilation unit; its path supplies its namespace.
// Qualified names resolve nested types and other units without an import keyword.
program : statement* EOF ;

block : '{' statement* '}' ;

statement
    : block
    | returnStatement
    | ifStatement
    | whileStatement
    | forStatement
    | breakStatement
    | continueStatement
    | structDeclaration
    | declaration ';'
    | assignment ';'
    | ';'
    ;

returnStatement   : 'return' assignment ';' ;
ifStatement       : 'if' '(' assignment ')' statement ('else' statement)? ;
whileStatement    : 'while' '(' assignment ')' statement ;
breakStatement    : 'break' ';' ;
continueStatement : 'continue' ';' ;

forStatement
    : 'for' '(' (declaration | assignment)? ';' assignment? ';' assignment? ')' statement
    ;

structDeclaration : 'struct' IDENTIFIER '{' structMember* '}' ';' ;
structMember : constructorDeclaration | methodDeclaration | statement ;

// The constructor name must match the enclosing struct's name.
constructorDeclaration : 'new' IDENTIFIER '=' constructorExpression ';' ;
constructorExpression : '{' constructorParameterList? '->' statement* '}' ;
constructorParameterList : (constructorParameter ','?)+ ;
constructorParameter : type '^'? bindingModifier? IDENTIFIER ;
// ^ accepts an unfinished struct reference; ? independently permits null.
// Only self may be read while incomplete; ^ arguments may be stored/forwarded.
// A new result is incomplete iff an actual argument is. See construction-groups.md.

// A method is an ordinary fixed function binding in a struct definition.
// The compiler supplies self; it is not written in parameterList.
// Function types and expressions both receive their implicit receiver here.
methodDeclaration : 'val' bindingModifier? memberName '=' functionExpression ';' ;
memberName : IDENTIFIER | primitiveType ;

declaration : declarationType binding (',' binding)* ;
declarationType : type | 'var' | 'val' ;
binding : bindingModifier? IDENTIFIER ('=' initializer)? ;
// At class/struct member scope, memberName also permits primitive type names.
// Ordinary locals and parameters still require a non-keyword IDENTIFIER.
bindingModifier : '!' | '~' ;
// A declaration initializer consisting of the string "C" declares an extern
// symbol: a function, or explicit numeric native storage. Ordinary strings
// elsewhere are array-valued expressions.
initializer : assignment ;

// The prefix qualifies the named struct reference, not an enclosing array.
// Each [] / [~] suffix qualifies its own array layer. ? requires a reference.
type
    : functionType
    | primitiveType typeSuffix*
    | accessModifier? typeName typeSuffix*
    ;
accessModifier : '!' | '~' ;
typeSuffix : '?' | '[]' | '[~]' ;
typeName : IDENTIFIER ('.' IDENTIFIER)* ;

primitiveType
    : 'int' | 'i8' | 'i16' | 'i32' | 'i64'
    | 'u1' | 'u8' | 'u16' | 'u32' | 'byte' | 'bool'
    | 'flt' | 'f32' | 'f64'
    ;

assignment : expression (assignmentOperator assignment)? ;
assignmentOperator
    : '=' | '+=' | '-=' | '*=' | '/=' | '&=' | '|=' | '^='
    | '<<=' | '>>=' | '>>>='
    ;

expression : logicalExpression ('?' assignment (':' assignment)?)? ;

// Both logical operators short-circuit and associate to the right here.
logicalExpression : bitwiseExpression (('&&' | '||') logicalExpression)? ;

bitwiseExpression
    : equalityExpression (('&' | '|' | '^') equalityExpression)*
    ;

equalityExpression : comparisonExpression (('==' | '!=') comparisonExpression)* ;

// Chained comparisons test adjacent operands and short-circuit. A chain must
// keep its direction; equality has lower precedence and does not join a chain.
comparisonExpression
    : shiftExpression
      ( (('<' | '<=') shiftExpression)+ | (('>' | '>=') shiftExpression)+ )?
    ;

shiftExpression
    : additiveExpression (('<<' | '>>' | '>>>') additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression (('*' | '/') unaryExpression)*
    ;

unaryExpression
    : ('-' | '!') unaryExpression
    | updateOperator IDENTIFIER
    | postfixExpression
    ;

// Prefix updates require a local name; postfix updates also allow fields
// and array elements. All updates require an assignable operand.
updateOperator : '++' | '--' ;
postfixExpression : primaryExpression postfixSuffix* updateOperator? ;
postfixSuffix : '.' memberName | '[' assignment ']' | '#' | callSuffix | methodCallSuffix ;

// For a fixed function-valued member, object.name(args) passes object as self.
// This overlaps ordinary field selection followed by a call syntactically;
// name/type resolution determines which call form applies.
methodCallSuffix : '.' memberName callSuffix ;

primaryExpression
    : INTEGER_LITERAL
    | FLOAT_LITERAL
    | stringExpression
    | CHARACTER_LITERAL
    | 'true'
    | 'false'
    | 'null'
    | IDENTIFIER
    | '(' assignment ')'
    | newExpression
    | functionExpression
    ;

// A function is an expression bound with an ordinary declaration.
// Its last statement supplies the implicit result; return can exit earlier.
functionExpression : '{' parameterList? '->' statement* '}' ;
parameterList : (parameter ','?)+ ;
parameter : type bindingModifier? IDENTIFIER ;

// Function types have unnamed, space-separated argument types.
// {int} is the zero-argument type; its function expression still uses ->.
functionType
    : '{' type+ '->' type '}' '?'?
    | '{' type '}' '?'?
    ;

callSuffix : '(' argumentList? ')' ;
argumentList : assignment (',' assignment)* ','? ;

// Allocation invokes the declared constructor; inline initializer blocks from
// earlier chapters are replaced by constructor arguments.
newExpression : 'new' type ('[' assignment ']' | callSuffix)? ;

INTEGER_LITERAL : '0' | [1-9] DIGIT* ;
// The f suffix also accepts an integer-shaped token, e.g. 12f.
FLOAT_LITERAL : DIGIT+ ('.' DIGIT* ('e' DIGIT+)? | 'e' '-'? DIGIT+) 'f'?
              | DIGIT+ 'f' ;
// String text uses a lexical mode: whitespace/comments are literal here.
// Interpolation re-enters the ordinary expression grammar, recursively.
stringExpression : '"' (STRING_TEXT | ESCAPE | '%%' | interpolation)* '"' ;
interpolation : '%' format? '(' assignment ')' ;
format : FORMAT_FLAG* FORMAT_WIDTH? ('.' FORMAT_WIDTH?)? FORMAT_KIND ;
// Flags: - + space # 0. Width/precision: decimal digits, no expressions.
// Kinds: d i u o x X f F e E g G a A c s b.
// Duplicate/inapplicable flags and precision on c/b are rejected.
// STRING_TEXT excludes double quote, backslash, and percent.
CHARACTER_LITERAL : '\'' (ESCAPE | ~['\\]) '\'' ;
fragment ESCAPE : '\\' [0ntr\\'"%] ;
IDENTIFIER : NON_DIGIT (NON_DIGIT | DIGIT)* ;
fragment NON_DIGIT : [a-zA-Z_] ;
fragment DIGIT : [0-9] ;
WHITESPACE : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
```

## Reading the parser alongside the grammar

All paths below are within `src/main/java/com/seaofnodes/simple/parser/`.
The components share one `Parser` instance containing the cursor, active scope,
compilation unit, and graph utilities. They are package-private collaborators;
there is no inheritance chain, AST, or additional compiler pass.

| Productions | Implementation |
| --- | --- |
| `program` | [`Parser.parse`](../../src/main/java/com/seaofnodes/simple/parser/Parser.java): establish the source-unit scope, parse its initializer, resolve remaining file names, publish its class |
| `statement`, `block`, loops, jumps, `returnStatement` | [`Statements`](../../src/main/java/com/seaofnodes/simple/parser/Statements.java), starting at `parseStatement` |
| `declaration`, `binding`, `bindingModifier` | [`Declarations`](../../src/main/java/com/seaofnodes/simple/parser/Declarations.java): declaration/expression lookahead, then each binding and initializer |
| `structDeclaration`, `constructorDeclaration` | [`Structs`](../../src/main/java/com/seaofnodes/simple/parser/Structs.java), starting at `parseStruct` and `parseConstructorDeclaration`; field discovery and initialization checks follow these entry points |
| `type`, `typeName`, `functionType` | [`Types`](../../src/main/java/com/seaofnodes/simple/parser/Types.java): `parseType` and `parseFunctionType`; qualified-name lookup lives in `typeName`, `dottedName`, and `rootedName` |
| `assignment` through `primaryExpression` | [`Expressions`](../../src/main/java/com/seaofnodes/simple/parser/Expressions.java): `parseAssignment`, `parseExpression`, `parseLogical`, `parseBitwise`, `parseEquality`, `parseComparison`, `parseShift`, `parseAddition`, `parseMultiplication`, `parseUnary`, `parsePrimary` |
| `postfixExpression`, member selection and updates | `Expressions.parsePostfix` / `parsePostfixMethod` dispatch to [`References.parseField`](../../src/main/java/com/seaofnodes/simple/parser/References.java); `References.parseName` handles bare names, implicit receivers, and forward references |
| `functionExpression`, `parameterList`, calls, `argumentList` | [`Functions`](../../src/main/java/com/seaofnodes/simple/parser/Functions.java): `parseFunction`, `parseConstructor`, shared `parameters` and `arguments`, then `parseBody` |
| `newExpression` | [`Allocations.parseNew`](../../src/main/java/com/seaofnodes/simple/parser/Allocations.java); the allocation-helper graph builder follows the expression routines |
| `stringExpression`, `interpolation` | [`Strings.parse`](../../src/main/java/com/seaofnodes/simple/parser/Strings.java), using `Lexer.stringPart` / `stringFormat`; normal expression parsing resumes inside each interpolation |
| Literals, identifiers, comments, token matching | [`Lexer`](../../src/main/java/com/seaofnodes/simple/parser/Lexer.java); it also supplies source-location snapshots used by diagnostics and serialized IR |

`ControlFlow` shares guarded scope merging between `if`, `?:`, `&&`, and `||`.
`Conversions` shares binding, field, and result conversions. These
operations are compiler semantics rather than additional grammar productions.
`Parser` retains the type registry and the small control/memory/peephole helpers
used by several productions and by the rest of the compiler.

There are a few deliberate differences between a syntax-only parser and this
one-pass IR builder:

- A statement beginning with `{` might be a block or a function-type binding.
  `Declarations` and `Types` use speculative parsing and cursor restore;
  qualified type lookup also stops before a suffix that belongs to an expression.
- Bare assignment, field assignment, and compound updates share the grammar's
  precedence but use different IR paths. `References` decides between an SSA
  local, a constructor field's local value, and an actual memory access. A
  function-valued field may also supply an implicit receiver to a call.
- `for` updates are scanned first and parsed after the body, where their effects
  belong. Explicit constructor bodies are scanned first and parsed after field
  discovery, so later fields have local slots. Both use `Lexer.skipTo`, which
  ignores delimiters inside comments and quoted text.
- `Structs.parseInitializer` parses a file or struct body as a function while
  discovering fields. `Functions.parseBody` still owns function scope/return
  construction; moving it does not change constructor or memory semantics.

The syntax chart does not describe all name/type restrictions. In particular,
prefix updates require an already-known non-pointer binding, and a private-class
privacy policy is separate from recognizing a qualified type name. The existing
same-named function binding inside a struct is also recognized as a constructor;
`new Name = { ... };` is the explicit constructor spelling above.
