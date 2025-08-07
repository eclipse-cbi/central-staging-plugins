package org.eclipse.cbi.central.plugin;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugin.MojoFailureException;
import java.util.Map;

@Mojo(name = "rc-list", defaultPhase = LifecyclePhase.NONE)
public class RcListMojo extends AbstractCentralMojo {
    /**
     * If true, show all deployments. If false or not set, only show the latest.
     */
    @Parameter(property = "central.showAllDeployments", defaultValue = "false")
    protected boolean showAllDeployments;

    /**
     * The namespace to use for listing deployments. Defaults to the project's
     * groupId if not set.
     */
    @Parameter(property = "central.namespace")
    protected String namespace;

    /**
     * Executes the rc-list goal. Lists deployments for the given namespace.
     * If showAllDeployments is true, lists all deployments; otherwise, only the
     * latest.
     */
    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-list goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-list: not execution root");
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
            throw new MojoFailureException("Failed to list deployments", e);
        }
    }

    /**
     * Prints details of a deployment, including deployment ID, state, creation
     * date,
     * and deployed component versions with their errors (if any).
     *
     * @param depObj The deployment object to print (expected to be a Map).
     */
    private void printDeployment(Object depObj) {
        if (!(depObj instanceof Map)) {
            return;
        }
        Map<?, ?> dep = (Map<?, ?>) depObj;
        Object deploymentId = dep.get("deploymentId");
        Object deploymentState = dep.get("deploymentState");
        Object createTimestamp = dep.get("createTimestamp");
        String dateStr = formatTimestamp(createTimestamp);
        getLog().info("DeploymentId: " + deploymentId + ", State: " + deploymentState + ", Created: " + dateStr);
        Object componentsObj = dep.get("deployedComponentVersions");
        if (componentsObj instanceof java.util.List) {
            printComponents((java.util.List<?>) componentsObj);
        }
    }

    /**
     * Formats a timestamp object to a human-readable date string.
     */
    private String formatTimestamp(Object timestampObj) {
        if (timestampObj instanceof Number number) {
            long ts = number.longValue();
            java.time.Instant instant = java.time.Instant.ofEpochMilli(ts);
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
            return fmt.format(instant);
        }
        return "";
    }

    /**
     * Prints details of deployed components and their errors.
     */
    private void printComponents(java.util.List<?> components) {
        for (Object compObj : components) {
            if (!(compObj instanceof Map)) {
                continue;
            }
            Map<?, ?> comp = (Map<?, ?>) compObj;
            Object purl = comp.get("purl");
            Object errorsObj = comp.get("errors");
            getLog().info("  Component: " + purl);
            printErrors(errorsObj);
        }
    }

    /**
     * Prints errors for a component, if any.
     */
    private void printErrors(Object errorsObj) {
        if (errorsObj instanceof java.util.List<?> errors) {
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
