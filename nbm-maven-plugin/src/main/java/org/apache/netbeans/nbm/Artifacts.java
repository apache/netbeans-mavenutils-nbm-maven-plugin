package org.apache.netbeans.nbm;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.repository.RemoteRepository;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for working with Resolver {@link org.eclipse.aether.artifact.Artifact} classes.
 */
@Singleton
@Named
public final class Artifacts {
    /**
     * Resolver artifact type registry backed by Maven Artifacts (legacy) ArtifactHandlerManager; it
     * knows about all the artifacts handlers registered with Maven, like our nbm are.
     */
    private final ArtifactTypeRegistry artifactTypeRegistry;

    @Inject
    public Artifacts(ArtifactHandlerManager artifactHandlerManager) {
        this.artifactTypeRegistry = RepositoryUtils.newArtifactTypeRegistry(artifactHandlerManager);
    }

    public ArtifactType getArtifactType(String name) {
        requireNonNull(name);
        // Note: general contract say this call may return null, BUT see ctor: this instance is backed by
        // Maven ArtifactHandlerManager that NEVER returns null, so this instance does not either.
        // Also, we should use this call ONLY to ask for "known to exists" types as we defined them, like "nbm" is.
        return artifactTypeRegistry.get(name);
    }

    public ArtifactType getArtifactType(Artifact artifact) {
        requireNonNull(artifact);
        return getArtifactType(artifact.getProperty(ArtifactProperties.TYPE, artifact.getExtension()));
    }

    /**
     * TODO: reconsider what to do here
     * Copied from resolver just to make things move: BUT USE OF THIS METHOD AND WHOLE APPROACH IS QUESTIONABLE.
     * After refactoring this single method will need to be (ideally) removed, or, replaced somehow.
     * <p>
     * Story time: long, long time ago, the "local path" and "remote path" (relative) was same, but this is not true
     * anymore (in some cases). Hence, for local paths, recommended method is {@link RepositorySystemSession#getLocalRepositoryManager()}
     * and {@link org.eclipse.aether.repository.LocalRepositoryManager#getPathForLocalArtifact(Artifact)} (local as "installed").
     * or {@link org.eclipse.aether.repository.LocalRepositoryManager#getPathForRemoteArtifact(Artifact, RemoteRepository, String)}
     * but then remote repo (origin) is needed.
     * To get remote path, one need {@link org.eclipse.aether.spi.connector.layout.RepositoryLayout#getLocation(Artifact, boolean)}
     * instead, but this one is tricky.
     * <p>
     * Ideally, one should not need the "path" of repo, just GAV and let resolver resolve it.
     * Right now, codebase is broken if:
     * <ul>
     *     <li>user uses split local repository</li>
     *     <li>dependency processed is a SNAPSHOT</li>
     *     <li>etc...</li>
     * </ul>
     */
    public String pathOf(Artifact artifact) {
        requireNonNull(artifact);
        StringBuilder path = new StringBuilder(128);
        path.append(artifact.getGroupId().replace('.', '/')).append('/');
        path.append(artifact.getArtifactId()).append('/');
        path.append(artifact.getBaseVersion()).append('/');
        path.append(artifact.getArtifactId()).append('-');
        path.append(artifact.getVersion());
        if (!artifact.getClassifier().isEmpty()) {
            path.append('-').append(artifact.getClassifier());
        }
        if (!artifact.getExtension().isEmpty()) {
            path.append('.').append(artifact.getExtension());
        }
        return path.toString();
    }
}
