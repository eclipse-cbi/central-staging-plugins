/*
 * Copyright (c) 2025 Eclipse Foundation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.cbi.central.plugin;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.cbi.central.NexusClient;
import java.util.Base64;

/**
 * Abstract base class for Nexus Repository Manager Maven Mojos.
 * Handles common configuration and client initialization.
 */
public abstract class AbstractNexusMojo extends AbstractMojo {
    
    /**
     * The bearer token used for authentication with the Nexus Repository Manager API.
     */
    @Parameter(property = "nexus.bearerToken")
    protected String bearerToken;

    /**
     * The server ID used to retrieve credentials from settings.xml.
     */
    @Parameter(property = "nexus.serverId", defaultValue = "nexus")
    protected String serverId;

    /**
     * The Nexus Repository Manager API URL. If not set, the default is used.
     */
    @Parameter(property = "nexus.apiUrl")
    protected String nexusApiUrl;
    
    /**
     * The Maven settings instance, used to retrieve server credentials.
     */
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    /**
     * The Maven settings decrypter for decrypting encrypted passwords.
     */
    @Component
    protected SettingsDecrypter settingsDecrypter;

    /**
     * The Maven project instance for this execution.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    protected MavenProject project;

    /**
     * The NexusClient instance used for API interactions.
     */
    protected NexusClient client;

    /**
     * Retrieves the bearer token for authentication. Checks the parameter, then
     * settings.xml server entry.
     *
     * @return the bearer token string
     * @throws MojoFailureException if no token can be obtained
     */
    protected String getBearerToken() throws MojoFailureException {
        if (bearerToken != null && !bearerToken.isEmpty()) {
            return bearerToken;
        }
        
        // Try to get token from settings.xml
        if (settings != null && serverId != null) {
            Server server = settings.getServer(serverId);
            if (server != null) {
                return decryptPassword(server);
            }
        }
        
        throw new MojoFailureException(
            "No bearer token found. Provide -Dnexus.bearerToken=<token> or configure server '" 
            + serverId + "' in settings.xml");
    }

    /**
     * Decrypts the password from a server entry.
     *
     * @param server The server entry
     * @return The decrypted password
     */
    protected String decryptPassword(Server server) {
        if (settingsDecrypter != null && server.getPassword() != null) {
            SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
            SettingsDecryptionResult result = settingsDecrypter.decrypt(request);
            if (result.getServer() != null) {
                return result.getServer().getPassword();
            }
        }
        return server.getPassword();
    }

    /**
     * Initializes the Nexus Repository Manager API client.
     *
     * @throws MojoFailureException if initialization fails
     */
    protected void initClient() throws MojoFailureException {
        String token = getBearerToken();
        if (nexusApiUrl != null && !nexusApiUrl.isEmpty()) {
            client = new NexusClient(token, nexusApiUrl);
        } else {
            client = new NexusClient(token);
        }
        getLog().info("Nexus client initialized with base URL: " + client.getBaseUrl());
    }
}
