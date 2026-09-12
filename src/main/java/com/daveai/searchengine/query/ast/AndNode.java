package com.daveai.searchengine.query.ast;

import java.util.List;

public record AndNode(List<QueryNode> children) implements QueryNode {}
