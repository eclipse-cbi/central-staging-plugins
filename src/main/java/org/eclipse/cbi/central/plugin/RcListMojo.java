package org.eclipse.cbi.central.plugin;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugin.AbstractMojo;
import org.eclipse.cbi.central.CentralPortalClient;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.Server;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import java.util.Map;

@Mojo(name = "rc-list", defaultPhase = LifecyclePhase.NONE)
public class RcListMojo extends AbstractCentralMojo {
    @Parameter(property = "central.namespace")
    protected String namespace;

    @Parameter(property = "central.name")
    protected String name;

    @Parameter(property = "central.version")
    protected String version;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() {
        try {
            getLog().info("Starting rc-list goal");
            initClient();
            String effectiveNamespace = (namespace != null && !namespace.isEmpty()) ? namespace : project.getGroupId();
            String effectiveName = (name != null && !name.isEmpty()) ? name : project.getArtifactId();
            String effectiveVersion = (version != null && !version.isEmpty()) ? version : project.getVersion();
            Map<String, Object> result = this.client.checkPublished(effectiveNamespace, effectiveName,
                    effectiveVersion);
            Object publishedFlag = result.get("published");
            String publishedStatus = publishedFlag != null ? publishedFlag.toString() : "N/A";
            getLog().info(String.format(
                    "Publication status:%n  Namespace: %s%n  Name: %s%n  Version: %s%n  Published: %s%n",
                    effectiveNamespace,
                    effectiveName,
                    effectiveVersion,
                    publishedStatus));
        } catch (Exception e) {
            getLog().error("Failed to list publications", e);
        }
    }
}
