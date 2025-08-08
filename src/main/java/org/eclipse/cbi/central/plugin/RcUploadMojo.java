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
     * Whether to automatically publish the deployment after validation.
     * If true, the deployment will automatically progress to PUBLISHING when validation passes (AUTOMATIC mode).
     * If false, the deployment will stop in VALIDATED state and require manual approval (USER_MANAGED mode).
     */
    @Parameter(property = "central.automaticPublishing", defaultValue = "false")
    protected boolean automaticPublishing;

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

    /**
     * If true, wait for the complete publishing process to finish.
     * If false, return after validation is complete (for USER_MANAGED) or after publishing starts (for AUTOMATIC).
     */
    @Parameter(property = "central.waitForCompletion", defaultValue = "false")
    protected boolean waitForCompletion;

    @Override
    public void execute() throws MojoFailureException {
        try {
            getLog().info("Starting rc-upload goal");

            if (!project.isExecutionRoot()) {
                getLog().info("Skipping rc-upload: not execution root");
                return;
            }

            initClient();

            // Validate automatic publishing parameter
            validateAutomaticPublishing();

            // Validate artifact file
            validateArtifactFile();

            // Upload bundle directly
            String deploymentId = uploadBundle(artifactFile.toPath(), determineBundleName(), getPublishingType());
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
     * Gets the publishing type string based on the automaticPublishing boolean.
     */
    private String getPublishingType() {
        return automaticPublishing ? DeploymentConstants.PUBLISHING_TYPE_AUTOMATIC : DeploymentConstants.PUBLISHING_TYPE_USER_MANAGED;
    }

    /**
     * Validates the automatic publishing parameter and logs the selected mode.
     */
    private void validateAutomaticPublishing() {
        String publishingTypeStr = getPublishingType();
        getLog().info("Using publishing type: " + publishingTypeStr);
        if (automaticPublishing) {
            getLog().info("Deployment will automatically progress to PUBLISHING when validation passes");
        } else {
            getLog().info("Deployment will stop in VALIDATED state and require manual approval");
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
        boolean isAutomatic = automaticPublishing;

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Map<String, Object> status = client.getDeploymentStatus(deploymentId);
            String state = String.valueOf(status.get(DeploymentConstants.DEPLOYMENT_STATE));

            if (DeploymentConstants.VALIDATED_STATE.equals(state)) {
                Object errors = status.get(DeploymentConstants.ERRORS);
                if (hasNoErrors(errors)) {
                    if (isAutomatic) {
                        getLog().info("Deployment validated! Automatic publishing is in progress...");
                        if (waitForCompletion) {
                            // For automatic mode, continue waiting for PUBLISHED state
                            waitForPublishing(deploymentId);
                            return;
                        } else {
                            getLog().info("Automatic publishing started.");
                            logDeploymentStatus(status);
                            return;
                        }
                    } else {
                        getLog().info("Upload to Maven Central Portal successful!");
                        getLog().info("Deployment is in VALIDATED state and ready for manual approval.");
                        logDeploymentStatus(status);
                        return;
                    }
                } else {
                    String errorMessage = formatDeploymentErrors(status);
                    throw new MojoFailureException("Deployment validated but has errors: " + errorMessage);
                }
            } else if (DeploymentConstants.PUBLISHED_STATE.equals(state)) {
                getLog().info("Upload and publishing to Maven Central Portal successful!");
                logDeploymentStatus(status);
                return;
            } else if (DeploymentConstants.PUBLISHING_STATE.equals(state)) {
                if (waitForCompletion) {
                    getLog().info("Deployment is being uploaded to Maven Central: " + state);
                    try {
                        TimeUnit.SECONDS.sleep(pollInterval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new MojoFailureException("Wait for validation was interrupted", e);
                    }
                } else {
                    getLog().info("Publishing started. Use 'waitForCompletion=true' to wait for completion.");
                    logDeploymentStatus(status);
                    return;
                }
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
                String errorMessage = formatDeploymentErrors(status);
                throw new MojoFailureException("Upload failed: " + errorMessage);
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
                logDeploymentStatus(status);
                return;
            } else if (DeploymentConstants.FAILED_STATE.equals(state)) {
                String errorMessage = formatDeploymentErrors(status);
                throw new MojoFailureException("Publishing failed: " + errorMessage);
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

    /**
     * Formats deployment errors in a human-readable way.
     */
    private String formatDeploymentErrors(Map<String, Object> status) {
        StringBuilder errorMessage = new StringBuilder();
        
        // Add deployment info
        Object deploymentId = status.get(DeploymentConstants.DEPLOYMENT_ID);
        Object deploymentName = status.get("deploymentName");
        if (deploymentId != null) {
            errorMessage.append("\nDeployment ID: ").append(deploymentId);
        }
        if (deploymentName != null) {
            errorMessage.append("\nDeployment Name: ").append(deploymentName);
        }
        
        // Format errors
        Object errorsObj = status.get(DeploymentConstants.ERRORS);
        if (errorsObj instanceof Map<?, ?> errors) {
            errorMessage.append("\nErrors:");
            for (Map.Entry<?, ?> entry : errors.entrySet()) {
                String component = entry.getKey().toString();
                Object componentErrors = entry.getValue();
                
                errorMessage.append("\n  Component: ").append(component);
                if (componentErrors instanceof java.util.List<?> errorList) {
                    for (Object error : errorList) {
                        errorMessage.append("\n    - ").append(error.toString());
                    }
                } else {
                    errorMessage.append("\n    - ").append(componentErrors.toString());
                }
            }
        } else if (errorsObj != null) {
            errorMessage.append("\nErrors: ").append(errorsObj.toString());
        }
        
        return errorMessage.toString();
    }

    /**
     * Checks if there are no errors in the deployment.
     * Handles various error formats: null, empty List, empty Map.
     */
    private boolean hasNoErrors(Object errors) {
        if (errors == null) {
            return true;
        }
        
        if (errors instanceof java.util.List<?> list) {
            return list.isEmpty();
        }
        
        if (errors instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        
        // If it's a string, check if it's empty or just whitespace
        String errorStr = errors.toString().trim();
        return errorStr.isEmpty() || "{}".equals(errorStr) || "[]".equals(errorStr);
    }

    /**
     * Logs deployment status in a human-readable format.
     */
    private void logDeploymentStatus(Map<String, Object> status) {
        Object deploymentId = status.get(DeploymentConstants.DEPLOYMENT_ID);
        Object deploymentName = status.get("deploymentName");
        Object deploymentState = status.get(DeploymentConstants.DEPLOYMENT_STATE);
        Object purls = status.get("purls");

        getLog().info("Deployment Details:");
        if (deploymentId != null) {
            getLog().info("  ID: " + deploymentId);
        }
        if (deploymentName != null) {
            getLog().info("  Name: " + deploymentName);
        }
        if (deploymentState != null) {
            getLog().info("  State: " + deploymentState);
        }
        if (purls instanceof java.util.List<?> purlList && !purlList.isEmpty()) {
            getLog().info("  Components:");
            for (Object purl : purlList) {
                getLog().info("    - " + purl);
            }
        }
    }
}
