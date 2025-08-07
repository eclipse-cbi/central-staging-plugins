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
import org.apache.maven.plugin.MojoFailureException;
import java.util.Map;

@Mojo(name = "rc-release", defaultPhase = LifecyclePhase.NONE)
public class RcReleaseMojo extends AbstractCentralMojo {
    /**
     * If true, only simulate the release (do not actually publish the deployment).
     */
    @Parameter(property = "central.dryRun", defaultValue = "false")
    protected boolean dryRun;
    /**
     * The deployment state value representing a validated deployment.
     */
    private static final String STATE_VALIDATED = "VALIDATED";

    /**
     * The deployment ID to release. If not set, the latest VALIDATED deployment for
     * the GAV is used.
     */
    @Parameter(property = "central.deploymentId")
    protected String deploymentId;

    /**
     * Executes the rc-release goal. Publishes the latest VALIDATED deployment for
     * the given GAV, or the specified deploymentId.
     */
    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-release goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-release: not execution root");
                return;
            }
            initClient();
            String effectiveDeploymentId = deploymentId;
            if (effectiveDeploymentId == null || effectiveDeploymentId.isEmpty()) {
                effectiveDeploymentId = findLatestValidatedDeploymentId(project.getGroupId(), project.getArtifactId(),
                        project.getVersion());
                if (effectiveDeploymentId == null) {
                    getLog().warn("No VALIDATED deployment found for GAV: " + project.getGroupId() + ":"
                            + project.getArtifactId() + ":" + project.getVersion());
                    throw new IllegalArgumentException("No VALIDATED deployment found for GAV: " + project.getGroupId()
                            + ":" + project.getArtifactId() + ":" + project.getVersion());
                }
                getLog().info("Found latest VALIDATED deployment: " + effectiveDeploymentId);
            } else {
                getLog().info("Using provided deploymentId: " + effectiveDeploymentId);
            }
            getLog().info("Checking deployment status for deploymentId: " + effectiveDeploymentId);
            Map<String, Object> status = client.getDeploymentStatus(effectiveDeploymentId);
            Object state = status.get("deploymentState");
            getLog().info("Current deployment state: " + state);
            if (isValidatedState(state)) {
                getLog().info("Deployment is VALIDATED.");
                if (dryRun) {
                    getLog().info("[DRY RUN] Would publish deployment: " + effectiveDeploymentId);
                } else {
                    getLog().info("Publishing deployment...");
                    Map<String, Object> result = client.publishDeployment(effectiveDeploymentId);
                    getLog().info("Release result: " + result);
                }
            } else {
                getLog().warn("DeploymentId " + effectiveDeploymentId + " is not in VALIDATED state. Current state: "
                        + state);
                throw new IllegalArgumentException("DeploymentId " + effectiveDeploymentId
                        + " is not in VALIDATED state. Current state: " + state);
            }
        } catch (Exception e) {
            getLog().error("Failed to release deployment", e);
            throw new MojoFailureException("Failed to release deployment", e);
        }
    }

    /**
     * Finds the latest VALIDATED deployment for the given GAV.
     */
    private String findLatestValidatedDeploymentId(String groupId, String artifactId, String version) {
        try {
            String namespace = groupId;
            Map<String, Object> deploymentsResult = client.listDeployments(namespace, 0, 500, "createTimestamp",
                    "desc");
            Object deploymentsObj = deploymentsResult.get("deployments");
            String gavPurlPrefix = String.format("pkg:maven/%s/%s@%s", groupId, artifactId, version);
            if (deploymentsObj instanceof java.util.List<?> deployments) {
                for (Object depObj : deployments) {
                    if (depObj instanceof Map<?, ?> dep) {
                        Object depState = dep.get("deploymentState");
                        if (!isValidatedState(depState))
                            continue;
                        Object componentsObj = dep.get("deployedComponentVersions");
                        if (componentsObj instanceof java.util.List<?> components) {
                            for (Object compObj : components) {
                                if (compObj instanceof Map<?, ?> comp) {
                                    Object purlObj = comp.get("purl");
                                    if (purlObj != null && purlObj.toString().startsWith(gavPurlPrefix)) {
                                        return dep.get("deploymentId").toString();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLog().error("Error searching for latest VALIDATED deployment", e);
        }
        return null;
    }

    /**
     * Checks if the deployment state is VALIDATED.
     */
    private boolean isValidatedState(Object stateObj) {
        return stateObj != null && STATE_VALIDATED.equals(stateObj.toString());
    }
}
