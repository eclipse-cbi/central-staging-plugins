/*
 * Copyright (c) 2025 Eclipse Foundation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.cbi.central;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

/**
 * Base class for repository API clients providing common HTTP client functionality,
 * authentication, and error handling.
 */
public abstract class BaseRepositoryClient {
    // Generic error codes with parameterized descriptions
    protected static final Map<Integer, String> ERROR_CODES = Map.of(
            400, "Bad request",
            401, "Unauthorized",
            403, "Forbidden",
            404, "Not found",
            500, "Internal server error");

    protected static final String HEADER_AUTH = "Authorization";
    protected static final String HEADER_ACCEPT = "Accept";
    protected static final String MEDIA_JSON = "application/json";
    
    protected final String baseUrl;
    protected final String username;
    protected final String password;
    protected final String bearerToken;
    protected final OkHttpClient client;
    protected final ObjectMapper objectMapper;

    /**
     * Creates a new repository client with Basic Authentication.
     * 
     * @param username Username for basic authentication
     * @param password Password for basic authentication
     * @param baseUrl Base URL for the API
     * @param defaultBaseUrl Default base URL if baseUrl is null or empty
     */
    protected BaseRepositoryClient(String username, String password, String baseUrl, String defaultBaseUrl) {
        this.username = username;
        this.password = password;
        this.bearerToken = null;
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : defaultBaseUrl;
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a new repository client with Bearer Token Authentication.
     * 
     * @param bearerToken Authentication token for API
     * @param baseUrl Base URL for the API
     * @param defaultBaseUrl Default base URL if baseUrl is null or empty
     */
    protected BaseRepositoryClient(String bearerToken, String baseUrl, String defaultBaseUrl) {
        this.bearerToken = bearerToken;
        this.username = null;
        this.password = null;
        this.baseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl : defaultBaseUrl;
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns a descriptive error message for a given HTTP code and context.
     */
    protected String errorDescription(int code, String context) {
        String base = ERROR_CODES.getOrDefault(code, "Unexpected error");
        if (context != null && !context.isEmpty()) {
            return base + " - " + context;
        }
        return base;
    }

    /**
     * Creates a base HTTP request builder with authentication and JSON headers.
     * Uses HTTP Basic Authentication if username/password are provided,
     * otherwise uses Bearer Token authentication.
     */
    protected Request.Builder baseRequest(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader(HEADER_ACCEPT, MEDIA_JSON);
        
        if (this.username != null && this.password != null) {
            // Use Basic Authentication
            String credentials = Credentials.basic(this.username, this.password);
            builder.addHeader(HEADER_AUTH, credentials);
        } else if (bearerToken != null) {
            // Use Bearer Token Authentication
            builder.addHeader(HEADER_AUTH, "Bearer " + this.bearerToken);
        } else {
            throw new IllegalStateException("No authentication method provided.");
        }
        
        return builder;
    }

    /**
     * Handles HTTP responses, parses JSON if expected, and throws exceptions for
     * error codes.
     *
     * @param response      The HTTP response
     * @param errorMessages Map of error codes to messages
     * @param expectJson    Whether to parse the response body as JSON
     * @return Parsed response as a Map
     * @throws IOException if an error or unexpected code occurs
     */
    protected Map<String, Object> handleResponse(Response response, Map<Integer, String> errorMessages,
            boolean expectJson) throws IOException {
        int code = response.code();
        String body = response.body() != null ? response.body().string() : "";
        if (code == 200 && expectJson) {
            return objectMapper.readValue(body, Map.class);
        } else if (code == 204 && !expectJson) {
            return Map.of("success", true, "message", "Operation completed successfully.");
        } else if (errorMessages.containsKey(code)) {
            throw new IOException(errorMessages.get(code) + " (" + code + "): " + body);
        } else {
            throw new IOException("Unexpected HTTP code " + code + ": " + body);
        }
    }

    /**
     * Gets the base URL for this client.
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
