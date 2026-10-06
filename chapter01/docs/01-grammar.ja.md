# 第1章の文法

[English](01-grammar.md) | 日本語

この文書は [第1章の文法定義](01-grammar.md) の日本語版です。文法定義そのものは原文と同一です。

[第1章：導入に戻る](../README.ja.md)

```antlrv4
grammar SimpleLanguage;

program
    : statement EOF
    ;

statement
    : 'return' expression ';'
    ;

expression
    : primaryExpression
    ;

primaryExpression
    : INTEGER_LITERAL
    ;

INTEGER_LITERAL
    : [1-9][0-9]*
    | [0]
    ;
```
