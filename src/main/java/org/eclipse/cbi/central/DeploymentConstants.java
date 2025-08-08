/*
 * Copyright (c) 2025 Eclipse Foundation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.cbi.central;

/**
 * Constants for Central Portal deployment states and field names.
 */
public final class DeploymentConstants {

    /**
     * Field name for deployment state in API responses.
     */
    public static final String DEPLOYMENT_STATE = "deploymentState";

    /**
     * Deployment state: The deployment has been validated successfully.
     */
    public static final String VALIDATED_STATE = "VALIDATED";

    /**
     * Deployment state: The deployment is pending processing.
     */
    public static final String PENDING_STATE = "PENDING";

    /**
     * Deployment state: The deployment is currently being validated.
     */
    public static final String VALIDATING_STATE = "VALIDATING";

    /**
     * Deployment state: The deployment has failed validation.
     */
    public static final String FAILED_STATE = "FAILED";

    /**
     * Deployment state: The deployment has been published to Central.
     */
    public static final String PUBLISHED_STATE = "PUBLISHED";

    /**
     * Field name for deployment ID in API responses.
     */
    public static final String DEPLOYMENT_ID = "deploymentId";

    /**
     * Field name for deployed component versions in API responses.
     */
    public static final String DEPLOYED_COMPONENT_VERSIONS = "deployedComponentVersions";

    /**
     * Field name for deployments list in API responses.
     */
    public static final String DEPLOYMENTS = "deployments";

    /**
     * Field name for package URL (purl) in component data.
     */
    public static final String PURL = "purl";

    /**
     * Field name for creation timestamp in API responses.
     */
    public static final String CREATE_TIMESTAMP = "createTimestamp";

    /**
     * Field name for errors in API responses.
     */
    public static final String ERRORS = "errors";

    // Publishing types
    /**
     * Publishing type: Deployment stops in VALIDATED state and requires manual approval.
     */
    public static final String PUBLISHING_TYPE_USER_MANAGED = "USER_MANAGED";

    /**
     * Publishing type: Deployment automatically progresses to PUBLISHING when validation passes.
     */
    public static final String PUBLISHING_TYPE_AUTOMATIC = "AUTOMATIC";

    // Private constructor to prevent instantiation
    private DeploymentConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
