package org.eclipse.cbi.central.plugin;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.Server;
import org.apache.maven.plugin.AbstractMojo;
import org.eclipse.cbi.central.CentralPortalClient;

public abstract class AbstractCentralMojo extends AbstractMojo {
    /**
     * The bearer token used for authentication with the Central Portal API.
     */
    @Parameter(property = "central.bearerToken")
    protected String bearerToken;

    /**
     * The server ID used to retrieve credentials from settings.xml.
     */
    @Parameter(property = "central.serverId", defaultValue = "central")
    protected String serverId;

    /**
     * The Central Portal API URL. If not set, the default is used.
     */
    @Parameter(property = "central.centralApiUrl")
    protected String centralApiUrl;

    /**
     * The Maven settings instance, used to retrieve server credentials.
     */
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    /**
     * The Maven project instance for this execution.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * The CentralPortalClient instance used for API interactions.
     */
    protected CentralPortalClient client;

    /**
     * Retrieves the bearer token for authentication. Checks the parameter, then
     * settings.xml server entry.
     *
     * @return the bearer token string
     * @throws IllegalArgumentException if no token is found
     */
    protected String getBearerToken() {
        if (bearerToken != null && !bearerToken.isEmpty()) {
            getLog().info("Using provided bearer token from parameter -Dcentral.bearerToken.");
            return bearerToken;
        }
        if (settings != null) {
            Server server = settings.getServer(serverId);
            if (server != null && server.getPassword() != null && !server.getPassword().isEmpty()) {
                getLog().info("Using bearer token from settings.xml server entry: " + serverId);
                return server.getPassword();
            }
        }
        throw new IllegalArgumentException(
                "Bearer token must be provided via -Dcentral.bearerToken or settings.xml server '" + serverId + "'.");
    }

    /**
     * Initializes the CentralPortalClient using the bearer token and optional API
     * URL.
     */
    protected void initClient() {
        client = centralApiUrl != null && centralApiUrl.isEmpty()
                ? new CentralPortalClient(getBearerToken(), centralApiUrl)
                : new CentralPortalClient(getBearerToken());
    }
}
