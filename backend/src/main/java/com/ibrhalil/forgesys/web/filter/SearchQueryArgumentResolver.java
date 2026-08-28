package com.ibrhalil.forgesys.web.filter;

import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.exception.SearchQueryDecodingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Binds {@link SearchQuery} parameters from the {@code sq} request param (K-55):
 * URL-safe base64 (unpadded) → UTF-8 JSON → {@link SearchRequest}. The payload shape
 * mirrors the POST /search body; the frontend's versioned blob carries an extra
 * {@code v} field, tolerated via {@code @JsonIgnoreProperties} on the DTO. An absent
 * param resolves {@link SearchQuery#empty()} — the endpoint's legacy flat-param path
 * stays intact. Malformed or oversized input fails fast with
 * {@link SearchQueryDecodingException} (400 {@code validation_error}).
 */
@Component
public class SearchQueryArgumentResolver implements HandlerMethodArgumentResolver {

    static final String PARAM = "sq";

    private final ObjectMapper objectMapper;
    private final int maxLength;

    public SearchQueryArgumentResolver(ObjectMapper objectMapper,
            @Value("${forgesys.web.search-query.max-length:4096}") int maxLength) {
        this.objectMapper = objectMapper;
        this.maxLength = maxLength;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SearchQuery.class.equals(parameter.getParameterType());
    }

    @Override
    public SearchQuery resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String sq = webRequest.getParameter(PARAM);
        if (sq == null || sq.isBlank()) {
            return SearchQuery.empty();
        }
        if (sq.length() > maxLength) {
            throw new SearchQueryDecodingException("Search query exceeds the maximum allowed length");
        }
        byte[] json;
        try {
            json = Base64.getUrlDecoder().decode(sq);
        } catch (IllegalArgumentException ex) {
            throw new SearchQueryDecodingException("Search query is not URL-safe base64");
        }
        try {
            return SearchQuery.of(objectMapper.readValue(json, SearchRequest.class));
        } catch (RuntimeException ex) {
            throw new SearchQueryDecodingException("Search query is not a valid search request payload");
        }
    }
}
