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
TERM   : WORD ('-' WORD)* ;           // a '-' inside a word (e-mail) is part of it, not NOT
fragment WORD : [\p{L}\p{N}]+ ;
WS     : [ \t\r\n]+ -> skip ;
OTHER  : . -> skip ;                  // ignore any other character: ? , ! ' etc.