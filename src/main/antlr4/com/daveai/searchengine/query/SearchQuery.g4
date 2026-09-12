grammar SearchQuery;

query   : orExpr? EOF ;
orExpr  : andExpr (OR andExpr)* ;
andExpr : notExpr (AND? notExpr)* ;   // two words side by side mean AND
notExpr : NOT notExpr | primary ;
primary : LPAREN orExpr RPAREN | PHRASE | TERM ;

OR     : 'OR' | '|' ;
AND    : 'AND' | '&' ;
NOT    : 'NOT' | '-' ;
LPAREN : '(' ;
RPAREN : ')' ;
PHRASE : '"' ~["]* '"' ;
TERM   : [\p{L}\p{N}]+ ;
WS     : [ \t\r\n]+ -> skip ;
OTHER  : . -> skip ;                  // ignore any other character: ? , ! ' etc.