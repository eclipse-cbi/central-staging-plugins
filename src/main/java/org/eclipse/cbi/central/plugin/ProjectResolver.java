/*
 * Copyright (c) 2025, 2026 Eclipse Foundation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.cbi.central.plugin;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for resolving target Maven projects based on GAV coordinates,
 * reactor projects, or current project context.
 */
public final class ProjectResolver {

    private ProjectResolver() {
        // Utility class - prevent instantiation
    }

    /**
     * Resolves the target projects to process based on provided parameters.
     * If explicit GAV is provided, creates a synthetic project.
     * Otherwise, uses reactor projects or falls back to current project.
     * 
     * @param groupId         The explicit group/namespace ID, or null
     * @param artifactId      The explicit artifact/name ID, or null
     * @param version         The explicit version, or null
     * @param currentProject  The current Maven project, or null
     * @param reactorProjects The reactor projects for multi-module builds, or null
     * @param log             The Maven logger for info messages
     * @return List of Maven projects to process
     * @throws IllegalStateException if no project can be resolved
     */
    public static List<MavenProject> resolveTargetProjects(
            String groupId,
            String artifactId,
            String version,
            MavenProject currentProject,
            List<MavenProject> reactorProjects,
            Log log) {

        // If explicit GAV is provided, create a synthetic Maven project
        if (isNotBlank(groupId) || isNotBlank(artifactId) || isNotBlank(version)) {
            log.info("Explicit GAV provided: " + (isNotBlank(groupId) ? groupId : "<empty>")
                    + ":" + (isNotBlank(artifactId) ? artifactId : "<empty>")
                    + ":" + (isNotBlank(version) ? version : "<empty>"));

            Model model = new Model();
            model.setGroupId(groupId);
            model.setArtifactId(artifactId);
            model.setVersion(version);
            // Default to jar packaging for explicit GAV unless we can determine otherwise
            model.setPackaging("jar");

            return List.of(new MavenProject(model));
        }
        log.info("No explicit GAV provided, resolving from project context.");
        // If we have a project, use reactor projects if present
        if (currentProject != null) {
            if (reactorProjects != null && !reactorProjects.isEmpty()) {
                List<MavenProject> result = new ArrayList<>();
                for (MavenProject rp : reactorProjects) {
                    if (rp != null) {
                        result.add(rp);
                    }
                }
                log.info("Resolved " + result.size() + " reactor projects.");
                return result;
            }

            // Fallback to current project
            return List.of(currentProject);
        }

        // If no project and no explicit GAV, we can't proceed
        throw new IllegalStateException("No project found and no explicit GAV coordinates provided. " +
                "Please provide groupId, artifactId, and version parameters.");
    }

    /**
     * Checks if a string is not null and not blank.
     * 
     * @param str The string to check
     * @return true if the string is not null and not blank
     */
    private static boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }
}
