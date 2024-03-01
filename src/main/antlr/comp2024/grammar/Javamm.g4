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
    : IMPORT names+=ID (DOT names+=ID)* SEMI
    ;

classDecl
    : CLASS className=ID (EXTEND extendName=ID)? LCURLY varDecl* methodDecl* RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type locals [boolean isArray = false]
    : name=INT (LSQUARE RSQUARE {$isArray = true;})?
    | name=INT DOTS
    | name=BOOL
    | name=INT
    | name=STRING
    | name=ID
    ;

methodDecl locals [boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})? type name=ID LPAREN (type parameters+=ID ( COLON type parameters+=ID )* )? RPAREN LCURLY varDecl* stmt* RETURN expr SEMI RCURLY
    | (PUBLIC {$isPublic=true;})? STATIC VOID MAIN LPAREN STRING LSQUARE RSQUARE args=ID RPAREN LCURLY varDecl* stmt* RCURLY
    ;

stmt
    : LCURLY stmt* RCURLY
    | IF LPAREN expr RPAREN stmt ELSE stmt 
    | WHILE LPAREN expr RPAREN stmt 
    | expr SEMI
    | varName=ID EQUALS expr SEMI
    | varName=ID LSQUARE expr RSQUARE EQUALS expr SEMI
    ;

expr
    : LPAREN expr RPAREN
    | expr LSQUARE expr RSQUARE
    | LSQUARE (expr (COLON expr)*)? RSQUARE
    | expr DOT LENGTH
    | NOT expr
    | NEW INT LSQUARE expr RSQUARE
    | NEW ID LPAREN RPAREN
    | expr op=(MUL|DIV) expr
    | expr op=(ADD|SUB) expr
    | expr op=(LT|LTE|MT|MTE) expr
    | expr op=LOGICAND expr
    | expr op=LOGICOR expr
    | expr DOT caller=ID LPAREN ( expr ( COLON expr )* )? RPAREN
    | var=INTEGER
    | var=TRUE
    | var=FALSE
    | name = ID
    | THIS
    ;
