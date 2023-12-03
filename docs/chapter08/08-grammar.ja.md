# 第8章の文法

[English](08-grammar.md) | 日本語

この文書は [原文](08-grammar.md) の日本語訳です。


[第8章に戻る](README.ja.md)

```antlrv4
grammar SimpleLanguage;

program
    : statement+ EOF
    ;

statement
    : returnStatement
    | declStatement
    | blockStatment
    | expressionStatement
    | ifStatement
    | whileStatement
    | breakStatement
    | continueStatment
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
    ;

blockStatement
    : '{' statement+ '}'
    ;

declStatement
    : 'int' IDENTIFIER '=' expression ';'
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
    | primaryExpression
    ;

primaryExpression
    : IDENTIFIER
    | INTEGER_LITERAL
    | 'true'
    | 'false'
    | '(' expression ')'
    ;

INTEGER_LITERAL
    : [1-9][0-9]*
    | [0]
    ;

IDENTIFIER
    : NON_DIGIT (NON_DIGIT | DIGIT)*
    ;

NON_DIGIT: [a-zA-Z_];
DEC_DIGIT: [0-9];
```
