# 第3章の文法

[English](03-grammar.md) | 日本語

この文書は [原文](03-grammar.md) の日本語訳です。

> 訳注: 原文の見出しは「Grammar for Chapter 1」ですが、このファイルは第3章に属するため、訳の見出しは第3章としています。文法は原文のまま保持しています。


[第3章に戻る](../README.ja.md)

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
    : additiveExpression
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
