package com.daveai.searchengine.query.ast;

import com.daveai.searchengine.models.Token;

import java.util.List;

public record PhraseNode(List<Token> words)     implements QueryNode {}
