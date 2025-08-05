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

@Mojo(name = "rc-deployment-list", defaultPhase = LifecyclePhase.NONE)
public class RcDeploymentListMojo extends AbstractCentralMojo {
    /**
     * If true, show all deployments. If false or not set, only show the latest.
     */
    @Parameter(property = "central.showAllDeployments", defaultValue = "false")
    protected boolean showAllDeployments;

    @Parameter(property = "central.namespace")
    protected String namespace;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() {
        try {
            getLog().info("Starting rc-deployment-list goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-deployment-list: not execution root");
                return;
            }
            initClient();
            String effectiveNamespace = (namespace != null && !namespace.isEmpty()) ? namespace : project.getGroupId();
            Map<String, Object> result = client.listDeployments(effectiveNamespace, 0, 500, "createTimestamp", "desc");
            Object deploymentsObj = result.get("deployments");
            if (deploymentsObj instanceof java.util.List) {
                java.util.List<?> deployments = (java.util.List<?>) deploymentsObj;
                if (deployments.isEmpty()) {
                    getLog().info("No deployments found.");
                    return;
                }
                if (showAllDeployments) {
                    for (Object depObj : deployments) {
                        printDeployment(depObj);
                    }
                } else {
                    printDeployment(deployments.get(0));
                }
            } else {
                getLog().info("No deployments found.");
            }
        } catch (Exception e) {
            getLog().error("Failed to list deployments", e);
        }
    }

    private void printDeployment(Object depObj) {
        if (depObj instanceof Map) {
            Map<?, ?> dep = (Map<?, ?>) depObj;
            Object deploymentId = dep.get("deploymentId");
            Object deploymentState = dep.get("deploymentState");
            Object createTimestamp = dep.get("createTimestamp");
            String dateStr = "";
            if (createTimestamp instanceof Number) {
                long ts = ((Number) createTimestamp).longValue();
                java.time.Instant instant = java.time.Instant.ofEpochMilli(ts);
                java.time.ZoneId zone = java.time.ZoneId.systemDefault();
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
                dateStr = fmt.format(instant);
            }
            getLog().info(
                    "DeploymentId: " + deploymentId + ", State: " + deploymentState + ", Created: " + dateStr);
            Object componentsObj = dep.get("deployedComponentVersions");
            if (componentsObj instanceof java.util.List) {
                java.util.List<?> components = (java.util.List<?>) componentsObj;
                for (Object compObj : components) {
                    if (compObj instanceof Map) {
                        Map<?, ?> comp = (Map<?, ?>) compObj;
                        Object purl = comp.get("purl");
                        Object errorsObj = comp.get("errors");
                        getLog().info("  Component: " + purl);
                        if (errorsObj instanceof java.util.List) {
                            java.util.List<?> errors = (java.util.List<?>) errorsObj;
                            if (errors.isEmpty()) {
                                getLog().info("    Errors: (none)");
                            } else {
                                getLog().info("    Errors:");
                                for (Object err : errors) {
                                    getLog().info("      - " + err);
                                }
                            }
                        } else {
                            getLog().info("    Errors: (none)");
                        }
                    }
                }
            }
        }
    }
}
