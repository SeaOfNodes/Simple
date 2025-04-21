# Grammar for Chapter 22

English | [日本語](22-grammar.ja.md)

```antlrv4
grammar SimpleLanguage;

// Name lookup, assignable operands, type compatibility, and access permissions
// are checked by the compiler in addition to these syntax rules.
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

structDeclaration : 'struct' IDENTIFIER block ';' ;

declaration : declarationType binding (',' binding)* ;
declarationType : type | 'var' | 'val' ;
binding : bindingModifier? IDENTIFIER ('=' initializer)? ;
bindingModifier : '!' | '~' ;
// A declaration initializer consisting of the string "C" declares an extern
// symbol. Ordinary strings elsewhere are array-valued expressions.
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

expression : bitwiseExpression ('?' assignment (':' assignment)?)? ;

bitwiseExpression
    : comparisonExpression (('&' | '|' | '^') comparisonExpression)*
    ;

comparisonExpression
    : shiftExpression (('==' | '!=' | '<' | '<=' | '>' | '>=') shiftExpression)*
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
postfixSuffix : '.' IDENTIFIER | '[' assignment ']' | '#' | callSuffix ;

primaryExpression
    : INTEGER_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
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

// Struct initialization uses a block; array allocation supplies a length.
newExpression : 'new' type ('[' assignment ']' | block)? ;

INTEGER_LITERAL : '0' | [1-9] DIGIT* ;
FLOAT_LITERAL : DIGIT+ ('.' DIGIT* ('e' DIGIT+)? | 'e' '-'? DIGIT+) ;
STRING_LITERAL : '"' (ESCAPE | ~["\\])* '"' ;
CHARACTER_LITERAL : '\'' (ESCAPE | ~['\\]) '\'' ;
fragment ESCAPE : '\\' [0ntr\\'"] ;
IDENTIFIER : NON_DIGIT (NON_DIGIT | DIGIT)* ;
fragment NON_DIGIT : [a-zA-Z_] ;
fragment DIGIT : [0-9] ;
WHITESPACE : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
```
