# Grammar for Chapter 14

```antlrv4
grammar SimpleLanguage;

program : block EOF ;

block : statement+ ;


statement
    : '{' block '}'
    | returnStatement
    | ifStatement
    | whileStatement
    | breakStatement
    | continueStatement
    | metaStatement
    | structDeclaration
    | expressionStatement
    ;


returnStatement : 'return' expression ';' ;

ifStatement : 'if' '(' expression ')' statement ('else' statement)? ;

whileStatement : 'while' '(' expression ')' statement ;

breakStatement : 'break' ';' ;

continueStatement : 'continue' ';' ;

metaStatement : '#showGraph' ';' ;

structDeclaration : 'struct' IDENTIFIER '{' fields '}'  ;

fields : field+ ;

field : type IDENTIFIER ';'  ;


expressionStatement
    : type IDENTIFIER ';'
    | type IDENTIFIER '=' expression ';'
    |      IDENTIFIER '=' expression ';'
    |                     expression
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

type
    : PRIMTYPE
    | typeName ('?')?
    ;

typeName : IDENTIFIER ;


expression : bitWiseExpression ;

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
    | primaryExpression postFix*
    ;

primaryExpression
    : INTEGER_LITERAL
    | '(' expression ')'
    | 'true'
    | 'false'
    | 'null'
    | newExpression
    | IDENTIFIER
    ;

newExpression : 'new' IDENTIFIER ;

postFix : '.' IDENTIFIER [ '=' expression ] ;

INTEGER_LITERAL
    : [1-9]DIGIT*
    | [0]
    ;

IDENTIFIER : NON_DIGIT (NON_DIGIT | DIGIT)*  ;

NON_DIGIT: [a-zA-Z_];
DIGIT: [0-9];
```
