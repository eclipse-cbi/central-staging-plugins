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

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.eclipse.cbi.central.CentralPortalClient;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.MojoFailureException;
import java.util.Map;

@Mojo(name = "rc-clean", defaultPhase = LifecyclePhase.NONE)
public class RcCleanMojo extends AbstractCentralMojo {
    private static final String DEPLOYMENT_STATE = "deploymentState";
    /**
     * If true, drop all deployments in the namespace.
     */
    @Parameter(property = "central.removeAll", defaultValue = "false")
    protected boolean removeAll;

    /**
     * If true, only drop deployments in FAILED state (used with removeAll).
     */
    @Parameter(property = "central.removeFailedOnly", defaultValue = "true")
    protected boolean removeFailedOnly;
    /**
     * The deployment ID to drop. Otherwise, the latest deployment will be dropped.
     */
    @Parameter(property = "central.deploymentId")
    protected String deploymentId;

    /**
     * The Maven project instance for this execution.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Executes the rc-clean goal. Drops the deployment with the specified deploymentId.
     */
    @Override
    public void execute()throws MojoFailureException {
        try {
            getLog().info("Starting rc-clean goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-clean: not execution root");
                return;
            }
            initClient();
            if (removeAll) {
                dropAllDeployments(removeFailedOnly);
            } else if (deploymentId != null && !deploymentId.isEmpty()) {
                if (removeFailedOnly) {
                    Map<String, Object> status = client.getDeploymentStatus(deploymentId);
                    Object state = status.get(DEPLOYMENT_STATE);
                    if (!"FAILED".equals(String.valueOf(state))) {
                        getLog().info("Deployment " + deploymentId + " is not in FAILED state. Skipping drop.");
                        return;
                    }
                }
                Map<String, Object> result = client.dropDeployment(deploymentId);
                getLog().info("Clean deployment " + deploymentId + " result: " + result);
            } else {
                // Drop the latest deployment if neither removeAll nor deploymentId is set
                String namespace = project.getGroupId();
                Map<String, Object> deploymentsResult = client.listDeployments(namespace, 0, 1, "createTimestamp", "desc");
                Object deploymentsObj = deploymentsResult.get("deployments");
                if (deploymentsObj instanceof java.util.List<?> deployments && !deployments.isEmpty()) {
                    Map<?, ?> latestDep = (Map<?, ?>) deployments.get(0);
                    String latestId = String.valueOf(latestDep.get("deploymentId"));
                    String state = String.valueOf(latestDep.get(DEPLOYMENT_STATE));
                    if (removeFailedOnly && !"FAILED".equals(state)) {
                        getLog().info("Latest deployment " + latestId + " is not in FAILED state. Skipping drop.");
                        return;
                    }
                    Map<String, Object> result = client.dropDeployment(latestId);
                    getLog().info("Dropped latest deployment " + latestId + " (state: " + state + ") result: " + result);
                } else {
                    getLog().info("No deployments found to drop.");
                }
            }
        } catch (Exception e) {
            getLog().error("Failed to drop deployment", e);
            throw new MojoFailureException("Failed to drop deployment", e);
        }
    }

    /**
     * Drops all deployments in the namespace. If onlyFailed is true, only drops deployments in FAILED state.
     */
    private void dropAllDeployments(boolean onlyFailed) {
        String namespace = project.getGroupId();
        try {
            Map<String, Object> deploymentsResult = client.listDeployments(namespace, 0, 500, "createTimestamp", "desc");
            Object deploymentsObj = deploymentsResult.get("deployments");
            if (deploymentsObj instanceof java.util.List<?> deployments) {
                deployments.stream()
                    .filter(Map.class::isInstance)
                    .map(depObj -> (Map<?, ?>) depObj)
                    .filter(dep -> !onlyFailed || "FAILED".equals(String.valueOf(dep.get(DEPLOYMENT_STATE))))
                    .forEach(this::dropSingleDeployment);
            } else {
                getLog().info("No deployments found to drop.");
            }
        } catch (Exception e) {
            getLog().error("Error dropping deployments", e);
        }
    }

    /**
     * Drops a single deployment and logs the result or any error.
     */
    private void dropSingleDeployment(Map<?, ?> dep) {
        String id = String.valueOf(dep.get("deploymentId"));
        String state = String.valueOf(dep.get(DEPLOYMENT_STATE));
        try {
            Map<String, Object> result = client.dropDeployment(id);
            getLog().info("Dropped deployment " + id + " (state: " + state + ") result: " + result);
        } catch (Exception e) {
            getLog().warn("Failed to drop deployment " + id + " (state: " + state + ")", e);
        }
    }
}
