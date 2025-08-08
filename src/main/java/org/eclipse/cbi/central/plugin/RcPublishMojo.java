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
import org.eclipse.cbi.central.DeploymentConstants;
import java.util.Map;

@Mojo(name = "rc-publish", defaultPhase = LifecyclePhase.NONE)
public class RcPublishMojo extends AbstractCentralMojo {
    /**
     * If true, only simulate the release (do not actually publish the deployment).
     */
    @Parameter(property = "central.dryRun", defaultValue = "false")
    protected boolean dryRun;

    /**
     * The deployment ID to release. If not set, the latest VALIDATED deployment for
     * the GAV is used.
     */
    @Parameter(property = "central.deploymentId")
    protected String deploymentId;

    /**
     * Executes the rc-publish goal. Publishes the latest VALIDATED deployment for
     * the given GAV, or the specified deploymentId.
     */
    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-publish goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-publish: not execution root");
                return;
            }
            initClient();
            String effectiveDeploymentId = deploymentId;
            if (effectiveDeploymentId == null || effectiveDeploymentId.isEmpty()) {
                effectiveDeploymentId = findLatestValidatedDeploymentId(project.getGroupId(), project.getArtifactId(),
                        project.getVersion());
                if (effectiveDeploymentId == null) {
                    getLog().warn("No VALIDATED deployment found for GAV: " +
                            project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
                    throw new IllegalArgumentException("No VALIDATED deployment found for GAV: " + project.getGroupId()
                            + ":" + project.getArtifactId() + ":" + project.getVersion());
                }
                getLog().info("Found latest VALIDATED deployment: " + effectiveDeploymentId);
            } else {
                getLog().info("Using provided deploymentId: " + effectiveDeploymentId);
            }
            getLog().info("Checking deployment status for deploymentId: " + effectiveDeploymentId);
            Map<String, Object> status = client.getDeploymentStatus(effectiveDeploymentId);
            Object state = status.get(DeploymentConstants.DEPLOYMENT_STATE);
            getLog().info("Current deployment state: " + state);
            if (isPublishableState(state)) {
                if (DeploymentConstants.VALIDATED_STATE.equals(state.toString())) {
                    getLog().info("Deployment is VALIDATED.");
                } else if (DeploymentConstants.PUBLISHING_STATE.equals(state.toString())) {
                    getLog().info("Deployment is already PUBLISHING.");
                }
                if (dryRun) {
                    getLog().info("[DRY RUN] Would publish deployment: " + effectiveDeploymentId);
                } else {
                    if (DeploymentConstants.PUBLISHING_STATE.equals(state.toString())) {
                        getLog().info("Deployment is already being published, no action needed.");
                    } else {
                        getLog().info("Publishing deployment...");
                        Map<String, Object> result = client.publishDeployment(effectiveDeploymentId);
                        getLog().info("Publish result: " + result);
                    }
                }
            } else {
                getLog().warn("DeploymentId " + effectiveDeploymentId + " is not in a publishable state (VALIDATED or PUBLISHING). Current state: "
                        + state);
                throw new IllegalArgumentException("DeploymentId " + effectiveDeploymentId
                        + " is not in a publishable state (VALIDATED or PUBLISHING). Current state: " + state);
            }
        } catch (Exception e) {
            getLog().error("Failed to publish deployment", e);
            throw new MojoFailureException("Failed to publish deployment", e);
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
            Object deploymentsObj = deploymentsResult.get(DeploymentConstants.DEPLOYMENTS);
            String gavPurlPrefix = String.format("pkg:maven/%s/%s@%s", groupId, artifactId, version);
            if (deploymentsObj instanceof java.util.List<?> deployments) {
                for (Object depObj : deployments) {
                    if (depObj instanceof Map<?, ?> dep) {
                        Object depState = dep.get(DeploymentConstants.DEPLOYMENT_STATE);
                        if (!DeploymentConstants.VALIDATED_STATE.equals(depState.toString()))
                            continue;
                        Object componentsObj = dep.get(DeploymentConstants.DEPLOYED_COMPONENT_VERSIONS);
                        if (componentsObj instanceof java.util.List<?> components) {
                            for (Object compObj : components) {
                                if (compObj instanceof Map<?, ?> comp) {
                                    Object purlObj = comp.get(DeploymentConstants.PURL);
                                    if (purlObj != null && purlObj.toString().startsWith(gavPurlPrefix)) {
                                        return dep.get(DeploymentConstants.DEPLOYMENT_ID).toString();
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
     * Checks if the deployment state is publishable (VALIDATED or already PUBLISHING).
     */
    private boolean isPublishableState(Object stateObj) {
        return stateObj != null && 
               (DeploymentConstants.VALIDATED_STATE.equals(stateObj.toString()) ||
                DeploymentConstants.PUBLISHING_STATE.equals(stateObj.toString()));
    }
}
