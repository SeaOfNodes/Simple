# 第2章の文法

[English](02-grammar.md) | 日本語

この文書は [第2章の文法定義](02-grammar.md) の日本語版です。
文法定義そのものは原文と同一です。

[第2章：算術演算に戻る](../README.ja.md)

```antlrv4
grammar SimpleLanguage;

program
    : statement EOF
    ;

statement
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
    : INTEGER_LITERAL
    | '(' expression ')'
    ;

INTEGER_LITERAL
    : [1-9][0-9]*
    | [0]
    ;
```
