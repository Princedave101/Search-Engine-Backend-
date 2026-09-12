package com.daveai.searchengine.query;

import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.ast.AndNode;
import com.daveai.searchengine.query.ast.NotNode;
import com.daveai.searchengine.query.ast.OrNode;
import com.daveai.searchengine.query.ast.PhraseNode;
import com.daveai.searchengine.query.ast.QueryNode;
import com.daveai.searchengine.query.ast.TermNode;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns ANTLR's parse tree for a query into the query's AST ({@link QueryNode}).
 * <p>
 * The generated {@code SearchQueryParser} builds a parse tree that mirrors the grammar rules, one level per
 * rule. This visitor walks that tree and keeps only the meaning: each word is cleaned by the
 * {@link TextProcessor} (lowercased, stop words removed) and levels that add nothing are collapsed.
 * <pre>
 * parse tree for "java -spring"                  AST
 * query                                          And
 *  └─ orExpr                                      ├─ Term(java)
 *      └─ andExpr                                 └─ Not
 *          ├─ notExpr ─ primary ─ TERM(java)          └─ Term(spring)
 *          └─ notExpr ─ NOT, notExpr ─ primary ─ TERM(spring)
 * </pre>
 * Every {@code visitXxx} method returns {@code null} when nothing searchable is left, for example when the
 * only word is a stop word. Parents drop those nulls, so a {@code null} never ends up inside a node.
 * <p>
 * The builder keeps no state between calls, so one instance can be shared by several threads.
 */
public class QueryAstBuilder extends SearchQueryBaseVisitor<QueryNode> {
    private final TextProcessor processor;

    public QueryAstBuilder(TextProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
    }

    /** {@code query : orExpr? EOF ;} An empty query has no orExpr. */
    @Override
    public QueryNode visitQuery(SearchQueryParser.QueryContext ctx) {
        if (ctx.orExpr() == null) {
            return null;
        }
        // Visit the orExpr directly. visitChildren(ctx) would return the result of the LAST child, which is EOF (null).
        return visit(ctx.orExpr());
    }

    /** {@code orExpr : andExpr (OR andExpr)* ;} */
    @Override
    public QueryNode visitOrExpr(SearchQueryParser.OrExprContext ctx) {
        List<QueryNode> children = visitAndDropNulls(ctx.andExpr());
        if (children.isEmpty()) {
            return null;                    // "the OR of": nothing left
        }
        if (children.size() == 1) {
            return children.getFirst();     // "java OR the": an Or with one child is just that child
        }
        return new OrNode(children);
    }

    /** {@code andExpr : notExpr (AND? notExpr)* ;} */
    @Override
    public QueryNode visitAndExpr(SearchQueryParser.AndExprContext ctx) {
        List<QueryNode> children = visitAndDropNulls(ctx.notExpr());
        if (children.isEmpty()) {
            return null;
        }
        if (children.size() == 1) {
            return children.getFirst();
        }
        return new AndNode(children);
    }

    /** {@code notExpr : NOT notExpr | primary ;} */
    @Override
    public QueryNode visitNotExpr(SearchQueryParser.NotExprContext ctx) {
        if (ctx.NOT() == null) {
            return visit(ctx.primary());
        }
        QueryNode child = visit(ctx.notExpr());
        if (child == null) {
            return null;                    // "-the": nothing to exclude
        }
        return new NotNode(child);
    }

    /** {@code primary : LPAREN orExpr RPAREN | PHRASE | TERM ;} */
    @Override
    public QueryNode visitPrimary(SearchQueryParser.PrimaryContext ctx) {
        if (ctx.TERM() != null) {
            // A TERM holds only letters and digits, so it gives no token (a stop word) or exactly one.
            List<Token> tokens = processor.processText(ctx.TERM().getText(), null);
            if (tokens.isEmpty()) {
                return null;
            }
            return new TermNode(tokens.getFirst().value());
        }

        if (ctx.PHRASE() != null) {
            String quoted = ctx.PHRASE().getText();
            String text = quoted.substring(1, quoted.length() - 1);   // strip the quotes
            // Stop words are removed but still counted, so "state of the art" gives state@0, art@3.
            List<Token> words = processor.processText(text, null);
            if (words.isEmpty()) {
                return null;
            }
            if (words.size() == 1) {
                return new TermNode(words.getFirst().value());
            }
            return new PhraseNode(words);
        }

        return visit(ctx.orExpr());         // brackets: ( orExpr )
    }

    /** Visits each context in order and keeps only the results that are not null (the "drop the nulls" step). */
    private List<QueryNode> visitAndDropNulls(List<? extends ParserRuleContext> contexts) {
        List<QueryNode> nodes = new ArrayList<>();
        for (ParserRuleContext context : contexts) {
            QueryNode node = visit(context);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }
}
