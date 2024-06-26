/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.response.googleaistudio;

import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.XContentParserUtils;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.inference.results.TextEmbeddingResults;
import org.elasticsearch.xpack.inference.external.http.HttpResult;
import org.elasticsearch.xpack.inference.external.request.Request;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.xpack.inference.external.response.XContentUtils.consumeUntilObjectEnd;
import static org.elasticsearch.xpack.inference.external.response.XContentUtils.moveToFirstToken;
import static org.elasticsearch.xpack.inference.external.response.XContentUtils.positionParserAtTokenAfterField;

public class GoogleAiStudioEmbeddingsResponseEntity {

    private static final String FAILED_TO_FIND_FIELD_TEMPLATE =
        "Failed to find required field [%s] in Google AI Studio embeddings response";

    /**
     * Parses the Google AI Studio batch embeddings response (will be used for single and batch embeddings).
     * For a request like:
     *
     * <pre>
     *     <code>
     *         {
     *             "inputs": ["Embed this", "Embed this, too"]
     *         }
     *     </code>
     * </pre>
     *
     * The response would look like:
     *
     * <pre>
     *     <code>
     *  {
     *     "embeddings": [
     *         {
     *             "values": [
     *                 -0.00606332,
     *                 0.058092743,
     *                 -0.06390548
     *             ]
     *         },
     *         {
     *             "values": [
     *               -0.00606332,
     *               -0.06390548,
     *                0.058092743
     *             ]
     *         }
     *     ]
     *  }
     *
     *     </code>
     * </pre>
     */

    public static TextEmbeddingResults fromResponse(Request request, HttpResult response) throws IOException {
        var parserConfig = XContentParserConfiguration.EMPTY.withDeprecationHandler(LoggingDeprecationHandler.INSTANCE);

        try (XContentParser jsonParser = XContentFactory.xContent(XContentType.JSON).createParser(parserConfig, response.body())) {
            moveToFirstToken(jsonParser);

            XContentParser.Token token = jsonParser.currentToken();
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, token, jsonParser);

            positionParserAtTokenAfterField(jsonParser, "embeddings", FAILED_TO_FIND_FIELD_TEMPLATE);

            List<TextEmbeddingResults.Embedding> embeddingList = XContentParserUtils.parseList(
                jsonParser,
                GoogleAiStudioEmbeddingsResponseEntity::parseEmbeddingObject
            );

            return new TextEmbeddingResults(embeddingList);
        }
    }

    private static TextEmbeddingResults.Embedding parseEmbeddingObject(XContentParser parser) throws IOException {
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);

        positionParserAtTokenAfterField(parser, "values", FAILED_TO_FIND_FIELD_TEMPLATE);

        List<Float> embeddingValuesList = XContentParserUtils.parseList(parser, GoogleAiStudioEmbeddingsResponseEntity::parseEmbeddingList);
        // parse and discard the rest of the object
        consumeUntilObjectEnd(parser);

        return TextEmbeddingResults.Embedding.of(embeddingValuesList);
    }

    private static float parseEmbeddingList(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.currentToken();
        XContentParserUtils.ensureExpectedToken(XContentParser.Token.VALUE_NUMBER, token, parser);
        return parser.floatValue();
    }

    private GoogleAiStudioEmbeddingsResponseEntity() {}
}
