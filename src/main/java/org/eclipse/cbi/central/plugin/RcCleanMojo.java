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
import java.util.Map;

@Mojo(name = "rc-clean", defaultPhase = LifecyclePhase.NONE)
public class RcCleanMojo extends AbstractCentralMojo {
    @Parameter(property = "central.deploymentId", required = true)
    protected String deploymentId;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Override
    public void execute() {
        try {
            getLog().info("Starting rc-clean goal");
            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-clean: not execution root");
                return;
            }
            initClient();
            Map<String, Object> result = client.dropDeployment(deploymentId);
            getLog().info("Clean deployment " + deploymentId + "result: " + result);
        } catch (Exception e) {
            getLog().error("Failed to drop deployment", e);
        }
    }
}
