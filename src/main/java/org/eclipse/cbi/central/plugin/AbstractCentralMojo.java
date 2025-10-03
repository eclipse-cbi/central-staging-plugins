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
import org.eclipse.cbi.central.CentralPortalClient;
import java.util.Base64;

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
     * Whether to automatically build the bearer token from username:password in
     * settings.xml.
     * When true, the bearer token is created by base64 encoding "username:password"
     * from the server entry.
     */
    @Parameter(property = "central.bearerCreate", defaultValue = "false")
    protected boolean bearerCreate;

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
     * The Maven settings decrypter for decrypting encrypted passwords.
     */
    @Component
    protected SettingsDecrypter settingsDecrypter;

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
        if (this.bearerToken != null && !this.bearerToken.isEmpty()) {
            getLog().info("Using provided bearer token from parameter -Dcentral.bearerToken.");
            return this.bearerToken;
        }
        if (this.settings != null) {
            Server server = this.settings.getServer(this.serverId);
            if (server != null) {
                // Decrypt the server credentials if encrypted
                Server decryptedServer = decryptServer(server);

                // If bearerCreate is true, build the token from username:password
                if (this.bearerCreate) {
                    String username = decryptedServer.getUsername();
                    String password = decryptedServer.getPassword();
                    getLog().info("Building bearer token from username:password in settings.xml server entry: "
                            + this.serverId);
                    if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                        String credentials = username + ":" + password;
                        return Base64.getEncoder().encodeToString(credentials.getBytes());
                    } else {
                        throw new IllegalArgumentException(
                                "central.bearerCreate is true but username or password is missing in settings.xml server '"
                                        + this.serverId + "'.");
                    }
                }
                // Otherwise, use the password field as the bearer token directly
                if (decryptedServer.getPassword() != null && !decryptedServer.getPassword().isEmpty()) {
                    getLog().info("Using bearer token from settings.xml server entry: " + this.serverId);
                    return decryptedServer.getPassword();
                }
            }
        }
        throw new IllegalArgumentException(
                "Bearer token must be provided via -Dcentral.bearerToken or settings.xml server '" + this.serverId
                        + "'.");
    }

    /**
     * Decrypts the server credentials if they are encrypted.
     *
     * @param server the server to decrypt
     * @return the decrypted server
     */
    private Server decryptServer(Server server) {
        if (this.settingsDecrypter != null) {
            SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
            SettingsDecryptionResult result = this.settingsDecrypter.decrypt(request);
            return result.getServer();
        }
        return server;
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
