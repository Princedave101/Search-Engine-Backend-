package com.daveai.searchengine.query.ast;

import java.util.List;

public record OrNode(List<QueryNode> children) implements QueryNode {}
