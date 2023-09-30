# Grammar for Chapter 1

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