package org.eclipse.cbi.central.plugin;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.MojoFailureException;
import java.util.Map;

@Mojo(name = "rc-status", defaultPhase = LifecyclePhase.NONE)
public class RcStatusMojo extends AbstractCentralMojo {
    /**
     * The namespace to use for publication status checks. Defaults to the project's
     * groupId if not set.
     */
    @Parameter(property = "central.namespace")
    protected String namespace;

    /**
     * The name (artifactId) to use for publication status checks. Defaults to the
     * project's artifactId if not set.
     */
    @Parameter(property = "central.name")
    protected String name;

    /**
     * The version to use for publication status checks. Defaults to the project's
     * version if not set.
     */
    @Parameter(property = "central.version")
    protected String version;

    /**
     * Executes the rc-status goal. Checks and prints the publication status for the
     * specified or effective GAV.
     */
    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-status goal");
            if (!project.isExecutionRoot() && (namespace != null && !namespace.isEmpty())) {
                getLog().info("Skipping rc-status: not execution root");
                return;
            }
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
            throw new MojoFailureException("Failed to list publications", e);
        }
    }
}
