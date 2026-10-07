# 第12章の文法

[English](12-grammar.md) | 日本語

この文書は [原文](12-grammar.md) の日本語訳です。

[第12章に戻る](../README.ja.md)

```antlrv4
grammar SimpleLanguage;

program
    : statement+ EOF
    ;

statement
    : returnStatement
    | structDeclaration
    | declStatement
    | blockStatement
    | expressionStatement
    | ifStatement
    | whileStatement
    | breakStatement
    | continueStatement
    ;

PRIMTYPE
    : 'int'
    ;

field
    : PRIMTYPE IDENTIFIER ';'
    | structName ('?')? IDENTIFIER ';'
    ;

fields
    : field+
    ;

structDeclaration
    : 'struct' IDENTIFIER '{' fields '}'
    ;

whileStatement
    : 'while' '(' expression ')' statement
    ;

breakStatement
    : 'break' ';'
    ;

continueStatement
    : 'continue' ';'
    ;

ifStatement
    : 'if' '(' expression ')' statement ('else' statement)?
    ;


expressionStatement
    : IDENTIFIER '=' expression ';'
    | fieldExpression '=' expression ';'
    ;

blockStatement
    : '{' statement+ '}'
    ;

structName
    : IDENTIFIER
    ;

declStatement
    : PRIMTYPE IDENTIFIER '=' expression ';'
    | structName ('?')? IDENTIFIER '=' expression ';'
    ;

returnStatement
    : 'return' expression ';'
    ;

expression
    : comparisonExpression
    ;

comparisonExpression
    : additiveExpression (('==' | '!='| '>'| '<'| '>='| '<=') additiveExpression)*
    ;

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : unaryExpression (('*' | '/') unaryExpression)*
    ;

unaryExpression
    : ('-') unaryExpression
    | '!' unaryExpression
    | primaryExpression
    ;

newExpression
    : 'new' IDENTIFIER
    ;

fieldExpression
    : primaryExpression '.' IDENTIFIER
    ;

primaryExpression
    : IDENTIFIER
    | INTEGER_LITERAL
    | 'true'
    | 'false'
    | 'null'
    | newExpression
    | '(' expression ')'
    | fieldExpression
    ;

INTEGER_LITERAL
    : [1-9][0-9]*
    | [0]
    ;

IDENTIFIER
    : NON_DIGIT (NON_DIGIT | DEC_DIGIT)*
    ;

NON_DIGIT: [a-zA-Z_];
DEC_DIGIT: [0-9];
```
