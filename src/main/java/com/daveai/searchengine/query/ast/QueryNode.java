package com.daveai.searchengine.query.ast;

import com.daveai.searchengine.models.Token;

import java.util.List;

public sealed interface QueryNode permits TermNode, PhraseNode, AndNode, OrNode, NotNode {}

