grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

COLON: ',' ;
DOT: '.' ;
DOTS: '...' ;
EQUALS : '=' ;
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSQUARE : '[' ;
RSQUARE : ']' ;
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;

LOGICAND: '&&' ;
LOGICOR: '||' ;
LT: '<' ;
LTE: '<=';
MT: '>' ;
MTE: '>=' ;

LENGTH: 'length' ;
CLASS : 'class' ;
INT : 'int' ;
BOOL : 'boolean' ;
STRING : 'String' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;
THIS : 'this' ;
TRUE : 'true' ;
FALSE : 'false' ;
NEW : 'new';
NOT : '!';
IMPORT: 'import' ;
EXTEND: 'extends' ;

IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

INTEGER : [0]|[1-9][0-9]* ;
ID : [a-zA-Z$_][a-zA-Z$0-9_]* ;
COMMENT : ('/*' .*? '*/'| '//' ~[\r\n]*) -> skip;
WS : [ \t\n\r\f]+ -> skip ;

program
    : importDecl* classDecl EOF
    ;

importDecl
    : IMPORT names+=ID (DOT names+=ID)* SEMI #Import
    ;

classDecl
    : CLASS className=ID (EXTEND extendName=ID)? LCURLY varDecl* methodDecl* RCURLY #Class
    ;

varDecl
    : typeName=type name=ID SEMI #Var
    ;

type locals [boolean isArray = false]
    : typeName=INT (LSQUARE RSQUARE {$isArray = true;})?
    | typeName=INT DOTS
    | typeName=BOOL
    | typeName=INT
    | typeName=STRING
    | typeName=ID
    ;

methodDecl locals [boolean isPublic=false]
    : (access=PUBLIC {$isPublic=true;})? type method=ID LPAREN (type parameters+=ID ( COLON type parameters+=ID )* )? RPAREN LCURLY varDecl* stmt* RETURN expr SEMI RCURLY #Method
    | (access=PUBLIC {$isPublic=true;})? STATIC VOID method=MAIN LPAREN STRING LSQUARE RSQUARE args=ID RPAREN LCURLY varDecl* stmt* RCURLY #Main
    ;

stmt
    : LCURLY stmt* RCURLY #Block
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElse
    | WHILE LPAREN expr RPAREN stmt #While
    | expr SEMI #Expression
    | varName=ID EQUALS expr SEMI #Assign
    | varName=ID LSQUARE expr RSQUARE EQUALS expr SEMI #AssignArray
    ;

expr
    : LPAREN expr RPAREN #Parenthesis
    | NOT expr #Not
    | expr LSQUARE expr RSQUARE #Array
    | expr DOT LENGTH #Length
    | expr DOT caller=ID LPAREN ( expr ( COLON expr )* )? RPAREN #CallMethod
    | LSQUARE (expr (COLON expr)*)? RSQUARE #NewArrayInt
    | NEW INT LSQUARE expr RSQUARE #NewArrayInt
    | NEW ID LPAREN RPAREN #NewObject
    | expr op=(MUL|DIV) expr #BinaryOp
    | expr op=(ADD|SUB) expr #BinaryOp
    | expr op=(LT|LTE|MT|MTE) expr #BinaryOp
    | expr op=LOGICAND expr #BinaryOp
    | expr op=LOGICOR expr #BinaryOp
    | var=INTEGER #Integer
    | var=TRUE #Boolean
    | var=FALSE #Boolean
    | name = ID #Id
    | THIS #This
    ;
