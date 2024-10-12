# Grammar for Chapter 16

```antlrv4
grammar SimpleLanguage;

program
    : statement+ EOF
    ;

statement
    : returnStatement
    | structDeclaration
    | declStatement
    | blockStatment
    | expressionStatement
    | ifStatement
    | whileStatement
    | breakStatement
    | continueStatment
    | metaStatement
    ;

PRIMTYPE
    : 'int'
    | 'i8'
    | 'i16'
    | 'i32'
    | 'i64'
    | 'u8'
    | 'u16'
    | 'u32'
    | 'flt'
    | 'f32'
    | 'f64'
    | 'bool'
    ;

field
    : PRIMTYPE IDENTIFIER ';'
    | structName IDENTIFIER ('?')? ';'
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

metaStatement
    : '#showGraph' ';'
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
    | structName IDENTIFIER ('?')? '=' expression ';'
    ;

returnStatement
    : 'return' expression ';'
    ;

expression
    : bitWiseExpression
    ;

bitWiseExpression
    : comparisonExpression ( '&' | '|' | '^' ) comparisonExpression)*
    ;
    
comparisonExpression
    : shiftExpression (('==' | '!='| '>'| '<'| '>='| '<=') shiftExpression)*
    ;

shiftExpression
    : additiveExpression ( '<<' | '>>>' | '>>' ) additiveExpression)*
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
    : 'new' IDENTIFIER blockStatement
    : 'new' IDENTIFIER '[' expression ']'
    ;

fieldExpression
    : primaryExpresson '.' IDENTIFIER
    | primaryExpresson '[' expression ']'
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
    : NON_DIGIT (NON_DIGIT | DIGIT)*
    ;

NON_DIGIT: [a-zA-Z_];
DEC_DIGIT: [0-9];
```
