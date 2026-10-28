package com.daveai.searchengine.query.ast;

/** A node of a parsed query. Sealed, so a switch over it must handle every node type. */
public sealed interface QueryNode permits TermNode, PhraseNode, AndNode, OrNode, NotNode {}
