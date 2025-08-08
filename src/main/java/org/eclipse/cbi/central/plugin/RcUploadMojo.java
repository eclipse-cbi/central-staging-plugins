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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Mojo(name = "rc-upload", defaultPhase = LifecyclePhase.NONE)
public class RcUploadMojo extends AbstractCentralMojo {

    /**
     * The project name to use for the upload bundle.
     */
    @Parameter(property = "central.projectName")
    protected String projectName;

    /**
     * The artifact file to upload. This should be a zip file containing the Maven
     * artifacts.
     */
    @Parameter(property = "central.artifactFile", required = true)
    protected File artifactFile;

    /**
     * The bundle name to use for the upload. If not specified, defaults to the
     * artifact filename without extension.
     */
    @Parameter(property = "central.bundleName")
    protected String bundleName;

    /**
     * The publishing type for the upload.
     * USER_MANAGED: Deployment stops in VALIDATED state and requires manual
     * approval.
     * AUTOMATIC: Deployment automatically progresses to PUBLISHING when validation
     * passes.
     * Available values: USER_MANAGED, AUTOMATIC
     */
    @Parameter(property = "central.publishingType", defaultValue = DeploymentConstants.PUBLISHING_TYPE_USER_MANAGED)
    protected String publishingType;

    /**
     * Maximum wait time in seconds for validation to complete.
     */
    @Parameter(property = "central.maxWaitTime", defaultValue = "300")
    protected int maxWaitTime;

    /**
     * Maximum wait time in seconds for publishing to complete.
     */
    @Parameter(property = "central.maxWaitTimePublishing", defaultValue = "600")
    protected int maxWaitTimePublishing;

    /**
     * Polling interval in seconds when checking deployment status.
     */
    @Parameter(property = "central.pollInterval", defaultValue = "5")
    protected int pollInterval;

    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-upload goal");

            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-upload: not execution root");
                return;
            }

            initClient();

            // Validate publishing type
            validatePublishingType();

            // Validate artifact file
            validateArtifactFile();

            // Upload bundle directly
            String deploymentId = uploadBundle(artifactFile.toPath(), determineBundleName(), publishingType);
            getLog().info("Upload initiated with deployment ID: " + deploymentId);

            // Wait for validation
            waitForValidation(deploymentId);

        } catch (Exception e) {
            getLog().error("Failed to upload to Central Portal", e);
            throw new MojoFailureException("Failed to upload to Central Portal", e);
        }
    }

    /**
     * Validates that the artifact file exists and is readable.
     */
    private void validateArtifactFile() throws MojoFailureException {
        if (!artifactFile.exists() || !artifactFile.isFile()) {
            throw new MojoFailureException(
                    "Artifact file does not exist or is not a file: " + artifactFile.getAbsolutePath());
        }
    }

    /**
     * Determines the effective bundle name to use for the upload.
     * If bundleName is provided, it will be used after trimming.
     * Otherwise, defaults to the artifact filename without extension.
     */
    private String determineBundleName() {
        if (bundleName != null && !bundleName.trim().isEmpty()) {
            return bundleName.trim();
        } else {
            // Default to artifact filename without extension
            String artifactFileName = artifactFile.getName();
            int dotIndex = artifactFileName.lastIndexOf('.');
            if (dotIndex > 0) {
                return artifactFileName.substring(0, dotIndex);
            } else {
                return artifactFileName;
            }
        }
    }

    /**
     * Validates the publishing type parameter.
     */
    private void validatePublishingType() throws MojoFailureException {
        if (publishingType == null || publishingType.trim().isEmpty()) {
            throw new MojoFailureException("Publishing type cannot be null or empty");
        }

        String normalizedType = publishingType.trim().toUpperCase();
        if (!DeploymentConstants.PUBLISHING_TYPE_USER_MANAGED.equals(normalizedType) &&
                !DeploymentConstants.PUBLISHING_TYPE_AUTOMATIC.equals(normalizedType)) {
            throw new MojoFailureException(
                    "Invalid publishing type: " + publishingType + ". " +
                            "Available values: " + DeploymentConstants.PUBLISHING_TYPE_USER_MANAGED + ", " +
                            DeploymentConstants.PUBLISHING_TYPE_AUTOMATIC);
        }

        // Normalize the value for consistency
        publishingType = normalizedType;

        getLog().info("Using publishing type: " + publishingType);
        if (DeploymentConstants.PUBLISHING_TYPE_USER_MANAGED.equals(publishingType)) {
            getLog().info("Deployment will stop in VALIDATED state and require manual approval");
        } else {
            getLog().info("Deployment will automatically progress to PUBLISHING when validation passes");
        }
    }

    /**
     * Uploads the bundle to Central Portal.
     */
    private String uploadBundle(Path bundleFile, String bundleName, String publishingType) throws IOException {
        getLog().info("Uploading bundle: " + bundleName + " from file: " + bundleFile.toAbsolutePath()
                + " with publishing type: " + publishingType);
        return client.uploadBundle(bundleFile, bundleName, publishingType);
    }

    /**
     * Waits for the deployment to be validated or published (depending on
     * publishing type).
     */
    private void waitForValidation(String deploymentId) throws MojoFailureException, IOException {
        getLog().info("Waiting for deployment validation...");

        long startTime = System.currentTimeMillis();
        long maxWaitMillis = maxWaitTime * 1000L;
        boolean isAutomatic = DeploymentConstants.PUBLISHING_TYPE_AUTOMATIC.equals(publishingType);

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Map<String, Object> status = client.getDeploymentStatus(deploymentId);
            String state = String.valueOf(status.get(DeploymentConstants.DEPLOYMENT_STATE));

            if (DeploymentConstants.VALIDATED_STATE.equals(state)) {
                Object errors = status.get(DeploymentConstants.ERRORS);
                if (errors == null || (errors instanceof java.util.List<?> list && list.isEmpty())) {
                    if (isAutomatic) {
                        getLog().info("Deployment validated! Automatic publishing is in progress...");
                        // For automatic mode, continue waiting for PUBLISHED state
                        waitForPublishing(deploymentId);
                        return;
                    } else {
                        getLog().info("Upload to Maven Central Portal successful!");
                        getLog().info("Deployment is in VALIDATED state and ready for manual approval.");
                        getLog().info("Deployment status: " + status);
                        return;
                    }
                } else {
                    throw new MojoFailureException("Deployment validated but has errors: " + errors);
                }
            } else if (DeploymentConstants.PUBLISHED_STATE.equals(state)) {
                getLog().info("Upload and publishing to Maven Central Portal successful!");
                getLog().info("Deployment status: " + status);
                return;
            } else if (DeploymentConstants.PENDING_STATE.equals(state)
                    || DeploymentConstants.VALIDATING_STATE.equals(state)) {
                getLog().info("Upload is being processed: " + state);
                try {
                    TimeUnit.SECONDS.sleep(pollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new MojoFailureException("Wait for validation was interrupted", e);
                }
            } else if (DeploymentConstants.FAILED_STATE.equals(state)) {
                throw new MojoFailureException("Upload failed with status: " + status);
            } else {
                throw new MojoFailureException("Upload has unexpected status: " + status);
            }
        }

        throw new MojoFailureException("Timeout waiting for deployment validation after " + maxWaitTime + " seconds");
    }

    /**
     * Waits for the deployment to be published (used in automatic mode).
     */
    private void waitForPublishing(String deploymentId) throws MojoFailureException, IOException {
        getLog().info("Waiting for deployment publishing...");
        
        long startTime = System.currentTimeMillis();
        long maxWaitMillis = maxWaitTimePublishing * 1000L;
        
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Map<String, Object> status = client.getDeploymentStatus(deploymentId);
            String state = String.valueOf(status.get(DeploymentConstants.DEPLOYMENT_STATE));

            if (DeploymentConstants.PUBLISHED_STATE.equals(state)) {
                getLog().info("Upload and publishing to Maven Central Portal successful!");
                getLog().info("Deployment status: " + status);
                return;
            } else if (DeploymentConstants.FAILED_STATE.equals(state)) {
                throw new MojoFailureException("Publishing failed with status: " + status);
            } else {
                getLog().info("Publishing in progress: " + state);
                try {
                    TimeUnit.SECONDS.sleep(pollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new MojoFailureException("Wait for publishing was interrupted", e);
                }
            }
        }

        throw new MojoFailureException("Timeout waiting for deployment publishing after " + maxWaitTimePublishing + " seconds");
    }
}
