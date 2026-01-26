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
import java.io.IOException;
import java.util.Map;

/**
 * Nexus Repository Manager REST API Client.
 * 
 * This client provides a Java interface to the Nexus Repository Manager REST API v1
 * available at https://repo3.eclipse.org/service/rest/v1.
 * 
 * The Nexus Repository Manager API provides comprehensive repository management capabilities:
 * 1. Repository browsing and searching
 * 2. Component and asset management
 * 3. Security and access control
 * 4. Staging and promotion workflows
 * 
 * @see <a href="https://help.sonatype.com/repomanager3/rest-and-integration-api">Nexus Repository Manager REST API Documentation</a>
 */
public class NexusClient extends BaseRepositoryClient {
    // Context strings for error descriptions
    private static final String CTX_COMPONENT_NOT_FOUND = "Component not found";
    private static final String CTX_GET_COMPONENT = "Get component";
    private static final String CTX_SEARCH_COMPONENTS = "Search components";
    private static final String CTX_DELETE_COMPONENT = "Delete component";
    private static final String CTX_UPLOAD_COMPONENT = "Upload component";
    private static final String CTX_LIST_REPOSITORIES = "List repositories";

    private static final String DEFAULT_BASE_URL = "https://repo3.eclipse.org/service/rest/v1";

    /**
     * Creates a new Nexus Repository Manager API client with default base URL.
     * 
     * @param username Username for basic authentication
     * @param password Password for basic authentication
     */
    public NexusClient(String username, String password) {
        this(username, password, null);
    }

    /**
     * Creates a new Nexus Repository Manager API client with custom base URL.
     * 
     * @param username Username for basic authentication
     * @param password Password for basic authentication
     * @param baseUrl Custom API base URL (defaults to https://repo3.eclipse.org/service/rest/v1)
     */
    public NexusClient(String username, String password, String baseUrl) {
        super(username, password, baseUrl, DEFAULT_BASE_URL);
    }

    /**
     * Lists all repositories accessible with the current authentication.
     *
     * @return List of repositories as a Map
     * @throws IOException if the request fails
     */
    public Map<String, Object> listRepositories() throws IOException {
        String url = baseUrl + "/repositories";
        Request request = baseRequest(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, Map.of(
                    401, errorDescription(401, CTX_LIST_REPOSITORIES),
                    403, errorDescription(403, CTX_LIST_REPOSITORIES),
                    500, errorDescription(500, CTX_LIST_REPOSITORIES)), true);
        }
    }

    /**
     * Searches for components in repositories matching the given criteria.
     *
     * @param repository The repository to search in (null for all repositories)
     * @param group      The group/namespace to search for
     * @param name       The component name to search for
     * @param version    The version to search for
     * @return Search results as a Map
     * @throws IOException if the request fails
     */
    public Map<String, Object> searchComponents(String repository, String group, String name, String version) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(baseUrl + "/search");
        urlBuilder.append("?");
        if (repository != null && !repository.isEmpty()) {
            urlBuilder.append("repository=").append(repository).append("&");
        }
        if (group != null && !group.isEmpty()) {
            urlBuilder.append("group=").append(group).append("&");
        }
        if (name != null && !name.isEmpty()) {
            urlBuilder.append("name=").append(name).append("&");
        }
        if (version != null && !version.isEmpty()) {
            urlBuilder.append("version=").append(version).append("&");
        }
        String url = urlBuilder.toString();
        
        Request request = baseRequest(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, Map.of(
                    400, errorDescription(400, CTX_SEARCH_COMPONENTS),
                    401, errorDescription(401, CTX_SEARCH_COMPONENTS),
                    403, errorDescription(403, CTX_SEARCH_COMPONENTS),
                    500, errorDescription(500, CTX_SEARCH_COMPONENTS)), true);
        }
    }

    /**
     * Gets details about a specific component by its ID.
     *
     * @param componentId The component ID
     * @return Component details as a Map
     * @throws IOException if the request fails
     */
    public Map<String, Object> getComponent(String componentId) throws IOException {
        String url = baseUrl + "/components/" + componentId;
        Request request = baseRequest(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, Map.of(
                    401, errorDescription(401, CTX_GET_COMPONENT),
                    403, errorDescription(403, CTX_GET_COMPONENT),
                    404, errorDescription(404, CTX_COMPONENT_NOT_FOUND),
                    500, errorDescription(500, CTX_GET_COMPONENT)), true);
        }
    }

    /**
     * Deletes a component by its ID.
     *
     * @param componentId The component ID to delete
     * @return Result of the delete operation as a Map
     * @throws IOException if the request fails
     */
    public Map<String, Object> deleteComponent(String componentId) throws IOException {
        String url = baseUrl + "/components/" + componentId;
        Request request = baseRequest(url).delete().build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String body = response.body() != null ? response.body().string() : "";
            if (code == 204) {
                return Map.of("success", true, "message", "Component deleted successfully.");
            } else if (code == 401) {
                throw new IOException(errorDescription(401, CTX_DELETE_COMPONENT) + " (" + code + "): " + body);
            } else if (code == 403) {
                throw new IOException(errorDescription(403, CTX_DELETE_COMPONENT) + " (" + code + "): " + body);
            } else if (code == 404) {
                throw new IOException(errorDescription(404, CTX_COMPONENT_NOT_FOUND) + " (" + code + "): " + body);
            } else if (code == 500) {
                throw new IOException(errorDescription(500, CTX_DELETE_COMPONENT) + " (" + code + "): " + body);
            } else {
                throw new IOException("Unexpected HTTP code " + code + ": " + body);
            }
        }
    }

    /**
     * Uploads a component to a Nexus repository.
     * 
     * This operation uploads artifacts to a Nexus hosted repository.
     * 
     * API Endpoint: POST /components
     * 
     * @param repository   The target repository name
     * @param componentFile The path to the file to upload
     * @param group        The group/namespace (e.g., "org.eclipse.example")
     * @param artifactId   The artifact ID
     * @param version      The version
     * @param packaging    The packaging type (e.g., "jar", "pom")
     * @return Upload result as a Map
     * @throws IOException if the upload fails or API returns an error
     */
    public Map<String, Object> uploadComponent(String repository, java.nio.file.Path componentFile, 
                                                String group, String artifactId, String version, String packaging) throws IOException {
        String url = baseUrl + "/components?repository=" + repository;
        
        // Create multipart form data for file upload
        RequestBody fileBody = RequestBody.create(componentFile.toFile(), 
            MediaType.parse("application/octet-stream"));
        
        MultipartBody.Builder formBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("maven2.groupId", group)
                .addFormDataPart("maven2.artifactId", artifactId)
                .addFormDataPart("maven2.version", version)
                .addFormDataPart("maven2.asset1", componentFile.getFileName().toString(), fileBody)
                .addFormDataPart("maven2.asset1.extension", packaging);

        RequestBody formBody = formBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader(HEADER_AUTH, "Bearer " + bearerToken)
                .post(formBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            String body = response.body() != null ? response.body().string() : "";
            
            if (code == 204) {
                return Map.of("success", true, "message", "Component uploaded successfully.");
            } else if (code == 400) {
                throw new IOException(errorDescription(400, CTX_UPLOAD_COMPONENT) + " (" + code + "): " + body);
            } else if (code == 401) {
                throw new IOException(errorDescription(401, CTX_UPLOAD_COMPONENT) + " (" + code + "): " + body);
            } else if (code == 403) {
                throw new IOException(errorDescription(403, CTX_UPLOAD_COMPONENT) + " (" + code + "): " + body);
            } else if (code == 500) {
                throw new IOException(errorDescription(500, CTX_UPLOAD_COMPONENT) + " (" + code + "): " + body);
            } else {
                throw new IOException("Unexpected HTTP code " + code + ": " + body);
            }
        }
    }
}
