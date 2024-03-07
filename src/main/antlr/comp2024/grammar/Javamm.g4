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
    : (access=PUBLIC)? CLASS name=ID (EXTEND extendName=ID)? LCURLY varDecl* methodDecl* RCURLY
    ;

varDecl
    : type name=ID SEMI #Var
    | type name=MAIN SEMI #VarMain
    ;

type locals [boolean isArray = false]
    : name=INT (LSQUARE RSQUARE {$isArray = true;})?
    | name=INT DOTS
    | name=BOOL
    | name=INT
    | name=STRING
    | name=ID
    | name=VOID
    ;

parameter
    : type name=ID #Parameters
    ;

methodDecl locals [boolean isPublic=false]
    : (access=PUBLIC {$isPublic=true;})? type name=ID LPAREN ( parameter ( COLON parameter )* )? RPAREN LCURLY varDecl* stmt* ret #Method
    | (access=PUBLIC {$isPublic=true;})? STATIC VOID name=MAIN LPAREN type LSQUARE RSQUARE args=ID RPAREN LCURLY varDecl* stmt* RCURLY #Main
    ;

ret
    : RETURN expr SEMI RCURLY #Return
    | RETURN MAIN SEMI RCURLY #ReturnMain
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
