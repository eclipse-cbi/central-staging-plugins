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
import org.apache.maven.plugin.AbstractMojo;
import org.eclipse.cbi.central.CentralPortalClient;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.Server;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.Parameter;
import java.util.Map;

@Mojo(name = "rc-release", defaultPhase = LifecyclePhase.NONE)
public class RcReleaseMojo extends AbstractCentralMojo {

    @Parameter(property = "central.deploymentId")
    protected String deploymentId;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() {
        try {
            getLog().info("Starting rc-release goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-release: not execution root");
                return;
            }
            initClient();
            String effectiveDeploymentId = deploymentId;
            if (effectiveDeploymentId == null || effectiveDeploymentId.isEmpty()) {
                // TODO 
                // getLog().info("No deploymentId provided, using GAV from current pom");
                // String groupId = project.getGroupId();
                // String artifactId = project.getArtifactId();
                // String version = project.getVersion();
                // getLog().info("Checking published status for GAV: " + groupId + ":" + artifactId + ":" + version);
                // Map<String, Object> published = client.checkPublished(groupId, artifactId, version);
                // Object publishedFlag = published.get("published");
                // if (publishedFlag != null && Boolean.TRUE.equals(publishedFlag)) {
                //     getLog().info("Artifact is already published on Central. Skipping release.");
                //     return;
                // }
                // Object depId = published.get("deploymentId");
                // if (depId != null) {
                //     effectiveDeploymentId = depId.toString();
                //     getLog().info("Found deploymentId from published response: " + effectiveDeploymentId);
                // } else {
                //     getLog().warn("No deploymentId found for GAV: " + groupId + ":" + artifactId + ":" + version);
                //     throw new IllegalArgumentException(
                //             "No deploymentId found for GAV: " + groupId + ":" + artifactId + ":" + version);
                // }
            } else {
                getLog().info("Using provided deploymentId: " + effectiveDeploymentId);
            }
            getLog().info("Checking deployment status for deploymentId: " + effectiveDeploymentId);
            Map<String, Object> status = client.getDeploymentStatus(effectiveDeploymentId);
            Object state = status.get("deploymentState");
            getLog().info("Current deployment state: " + state);
            if (state != null && "VALIDATED".equals(state.toString())) {
                getLog().info("Deployment is VALIDATED. Publishing deployment...");
                Map<String, Object> result = client.publishDeployment(effectiveDeploymentId);
                getLog().info("Release result: " + result);
            } else {
                getLog().warn("DeploymentId " + effectiveDeploymentId + " is not in VALIDATED state. Current state: "
                        + state);
            }
        } catch (Exception e) {
            getLog().error("Failed to release deployment", e);
        }
    }
}
