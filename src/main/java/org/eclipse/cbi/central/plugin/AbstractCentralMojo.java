package org.eclipse.cbi.central.plugin;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.Server;
import org.apache.maven.plugin.AbstractMojo;
import org.eclipse.cbi.central.CentralPortalClient;

public abstract class AbstractCentralMojo extends AbstractMojo {
    @Parameter(property = "central.bearerToken")
    protected String bearerToken;

    @Parameter(property = "central.serverId", defaultValue = "central")
    protected String serverId;

    @Parameter(property = "central.centralApiUrl")
    protected String centralApiUrl;

    @Component
    protected Settings settings;

    protected CentralPortalClient client;

    protected String getBearerToken() {
        if (bearerToken != null && !bearerToken.isEmpty()) {
            return bearerToken;
        }
        if (settings != null) {
            Server server = settings.getServer(serverId);
            if (server != null && server.getPassword() != null && !server.getPassword().isEmpty()) {
                return server.getPassword();
            }
        }
        throw new IllegalArgumentException(
                "Bearer token must be provided via -Dcentral.bearerToken or settings.xml server '" + serverId + "'.");
    }

    protected void initClient() {
        client = centralApiUrl != null && centralApiUrl.isEmpty()
                ? new CentralPortalClient(getBearerToken(), centralApiUrl)
                : new CentralPortalClient(getBearerToken());
    }
}
