# Grammar for Chapter 1

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
