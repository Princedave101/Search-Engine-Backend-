package com.daveai.searchengine.query;

import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.ast.AndNode;
import com.daveai.searchengine.query.ast.QueryNode;
import com.daveai.searchengine.query.ast.TermNode;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses the text a user typed into a {@link Query}: the text plus the AST that says what it means.
 * <pre>
 * "java -spring"
 *    ▼ SearchQueryLexer   (generated from SearchQuery.g4)   text → tokens
 *    ▼ SearchQueryParser  (generated from SearchQuery.g4)   tokens → parse tree
 *    ▼ QueryAstBuilder                                      parse tree → AST, words cleaned by TextProcessor
 * Query("java -spring", And[Term(java), Not(Term(spring))])
 * </pre>
 * Syntax: words side by side mean AND; {@code AND} or {@code &}; {@code OR} or {@code |}; {@code NOT} or
 * {@code -}; brackets; {@code "quoted phrases"}. Operators are uppercase only, so {@code or} is an ordinary word.
 * <p>
 * Parsing never fails. If the query has a syntax error, such as an unclosed bracket, the operators are
 * ignored and the remaining words are combined with AND.
 * <p>
 * The parser keeps no state between calls, so one instance can be shared by several threads.
 */
public class QueryParser {

    private final TextProcessor processor;
    private final QueryAstBuilder astBuilder;

    /**
     * @param processor the same processor the Indexer uses, so query words are cleaned exactly like document words
     */
    public QueryParser(TextProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
        this.astBuilder = new QueryAstBuilder(processor);
    }

    /**
     * Parses a query.
     *
     * @param raw the text the user typed; may be null
     * @return the parsed query; its root is null when nothing searchable is left (blank text, or only stop words)
     */
    public Query parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Query(raw, null);
        }

        SearchQueryLexer lexer = new SearchQueryLexer(CharStreams.fromString(raw));      // stage 1
        SearchQueryParser parser = new SearchQueryParser(new CommonTokenStream(lexer));  // stage 2
        parser.removeErrorListeners();                   // don't print errors to the console
        parser.setErrorHandler(new BailErrorStrategy()); // throw on bad syntax instead of guessing

        try {
            return new Query(raw, astBuilder.visit(parser.query()));                     // stage 3
        } catch (ParseCancellationException e) {
            return new Query(raw, andOfWords(raw));
        }
    }

    /** Fallback for a syntax error: ignores the operators and combines the remaining words with AND. */
    private QueryNode andOfWords(String raw) {
        List<QueryNode> terms = new ArrayList<>();
        for (Token token : processor.processText(raw, null)) {
            terms.add(new TermNode(token.value()));
        }
        if (terms.isEmpty()) {
            return null;
        }
        if (terms.size() == 1) {
            return terms.getFirst();
        }
        return new AndNode(terms);
    }
}
