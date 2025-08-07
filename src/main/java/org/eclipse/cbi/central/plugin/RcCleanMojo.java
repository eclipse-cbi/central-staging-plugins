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
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugin.MojoFailureException;
import java.util.Map;

@Mojo(name = "rc-clean", defaultPhase = LifecyclePhase.NONE)
public class RcCleanMojo extends AbstractCentralMojo {

    private static final String DEPLOYMENT_STATE = "deploymentState";
    private static final String FAILED_STATE = "FAILED";
    
    /**
     * If true, only simulate the clean (do not actually drop deployments).
     */
    @Parameter(property = "central.dryRun", defaultValue = "false")
    protected boolean dryRun;
    
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
     * Executes the rc-clean goal. Drops the deployment with the specified
     * deploymentId.
     */
    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-clean goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-clean: not execution root");
                return;
            }
            initClient();
            if (removeAll) {
                dropAllDeployments(removeFailedOnly, dryRun);
            } else if (deploymentId != null && !deploymentId.isEmpty()) {
                if (removeFailedOnly) {
                    Map<String, Object> status = client.getDeploymentStatus(deploymentId);
                    Object state = status.get(DEPLOYMENT_STATE);
                    if (!FAILED_STATE.equals(String.valueOf(state))) {
                        getLog().info("Deployment " + deploymentId + " is not in " + FAILED_STATE + " state. Skipping drop.");
                        return;
                    }
                }
                if (dryRun) {
                    getLog().info("[DRY RUN] Would clean deployment " + deploymentId);
                } else {
                    Map<String, Object> result = client.dropDeployment(deploymentId);
                    getLog().info("Clean deployment " + deploymentId + " result: " + result);
                }
            } else {
                // Drop the latest deployment if neither removeAll nor deploymentId is set
                String namespace = project.getGroupId();
                Map<String, Object> deploymentsResult = client.listDeployments(namespace, 0, 1, "createTimestamp",
                        "desc");
                Object deploymentsObj = deploymentsResult.get("deployments");
                if (deploymentsObj instanceof java.util.List<?> deployments && !deployments.isEmpty()) {
                    Map<?, ?> latestDep = (Map<?, ?>) deployments.get(0);
                    String latestId = String.valueOf(latestDep.get("deploymentId"));
                    String state = String.valueOf(latestDep.get(DEPLOYMENT_STATE));
                    if (removeFailedOnly && !FAILED_STATE.equals(state)) {
                        getLog().info("Latest deployment " + latestId + " is not in FAILED state. Skipping drop.");
                        return;
                    }
                    if (dryRun) {
                        getLog().info("[DRY RUN] Would drop latest deployment " + latestId + " (state: " + state + ")");
                    } else {
                        Map<String, Object> result = client.dropDeployment(latestId);
                        getLog().info(
                                "Dropped latest deployment " + latestId + " (state: " + state + ") result: " + result);
                    }
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
     * Drops all deployments in the namespace. If onlyFailed is true, only drops
     * deployments in FAILED state.
     */
    private void dropAllDeployments(boolean onlyFailed) throws MojoFailureException {
        dropAllDeployments(onlyFailed, false);
    }

    /**
     * Drops all deployments in the namespace. If onlyFailed is true, only drops
     * deployments in FAILED state.
     * If dryRun is true, only simulates the drop.
     */
    private void dropAllDeployments(boolean onlyFailed, boolean dryRun) throws MojoFailureException {
        String namespace = project.getGroupId();
        try {
            Map<String, Object> deploymentsResult = client.listDeployments(namespace, 0, 500, "createTimestamp",
                    "desc");
            Object deploymentsObj = deploymentsResult.get("deployments");
            if (deploymentsObj instanceof java.util.List<?> deployments) {
                for (Object depObj : deployments) {
                    if (depObj instanceof Map<?, ?> dep) {
                        if (!onlyFailed || FAILED_STATE.equals(String.valueOf(dep.get(DEPLOYMENT_STATE)))) {
                            dropSingleDeployment(dep, dryRun);
                        }
                    }
                }
            } else {
                getLog().info("No deployments found to drop.");
            }
        } catch (Exception e) {
            getLog().error("Error dropping deployments", e);
            throw new MojoFailureException("Error dropping deployments", e);
        }
    }

    /**
     * Drops a single deployment and logs the result or any error.
     */
    private void dropSingleDeployment(Map<?, ?> dep) throws MojoFailureException {
        dropSingleDeployment(dep, false);
    }

    /**
     * Drops a single deployment and logs the result or any error. If dryRun is
     * true, only simulates the drop.
     */
    private void dropSingleDeployment(Map<?, ?> dep, boolean dryRun) throws MojoFailureException {
        String id = String.valueOf(dep.get("deploymentId"));
        String state = String.valueOf(dep.get(DEPLOYMENT_STATE));
        if (dryRun) {
            getLog().info("[DRY RUN] Would drop deployment " + id + " (state: " + state + ")");
            return;
        }
        try {
            Map<String, Object> result = client.dropDeployment(id);
            getLog().info("Dropped deployment " + id + " (state: " + state + ") result: " + result);
        } catch (Exception e) {
            getLog().error("Failed to drop deployment " + id + " (state: " + state + ")", e);
            throw new MojoFailureException("Failed to drop deployment " + id + " (state: " + state + ")", e);
        }
    }
}
