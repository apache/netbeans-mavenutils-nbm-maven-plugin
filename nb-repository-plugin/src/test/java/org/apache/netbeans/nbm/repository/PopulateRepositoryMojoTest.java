package org.apache.netbeans.nbm.repository;

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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.DefaultLocalPathPrefixComposerFactory;
import org.eclipse.aether.internal.impl.DefaultTrackingFileManager;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.util.artifact.SubArtifact;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.MojoExecutionException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 * @author Milos Kleint
 */
@MojoTest
class PopulateRepositoryMojoTest {

    private final String LOCAL_REPO = "target/local-repo/";

    @Test
    @InjectMojo(goal = "populate", pom = "src/test/resources/PopulateMojoTest.xml")
    void testInstall(PopulateRepositoryMojo mojo) throws Exception {

        MojoExtension.setVariableValueToObject(mojo, "session", createMavenSession());
        File f1 = File.createTempFile("PopulateRepositoryMojoTest", ".jar");
        f1.deleteOnExit();
        Artifact art1 = mojo.createArtifact("testarg", "1.0", "testgrp");
        art1 = art1.setFile(f1);
        File f2 = File.createTempFile("PopulateRepositoryMojoTest", ".nbm");
        f2.deleteOnExit();
        Artifact art2 = new SubArtifact(art1, "", "nbm", f2);
        mojo.install(art1);
        mojo.install(art2);
        File localRepo = mojo.session.getRepositorySession().getLocalRepository().getBasedir();
        assertTrue(new File(localRepo, "testgrp/testarg/1.0/testarg-1.0.nbm").isFile());
        assertTrue(new File(localRepo, "testgrp/testarg/1.0/testarg-1.0.jar").isFile());
    }

    @Test
    @InjectMojo(goal = "populate", pom = "src/test/resources/PopulateMojoTest.xml")
    void testRetryFailedDeploymentCount_Default(PopulateRepositoryMojo mojo) throws Exception {

        MojoExtension.setVariableValueToObject(mojo, "session", createMavenSession());

        // Mock repository system
        RepositorySystem repositorySystem = mock(RepositorySystem.class);
        MojoExtension.setVariableValueToObject(mojo, "repositorySystem", repositorySystem);

        // Create a deploy request
        DeployRequest deployRequest = new DeployRequest();

        // Default: 0 retries => 1 attempt; success on first try
        doAnswer(invocation -> null).when(repositorySystem).deploy(any(), any(DeployRequest.class));

        mojo.deploy(deployRequest);

        verify(repositorySystem, times(1)).deploy(any(), any(DeployRequest.class));
    }

    @Test
    @MojoParameter(name = "retryFailedDeploymentCount", value = "2")
    @InjectMojo(goal = "populate", pom = "src/test/resources/PopulateMojoTest.xml")
    void testRetryFailedDeploymentCount_CustomValue(PopulateRepositoryMojo mojo) throws Exception {
        MojoExtension.setVariableValueToObject(mojo, "session", createMavenSession());

        // 2 retries => 3 attempts total (1 initial + 2 retries)
        // Mock repository system
        RepositorySystem repositorySystem = mock(RepositorySystem.class);
        MojoExtension.setVariableValueToObject(mojo, "repositorySystem", repositorySystem);

        // Create a deploy request
        DeployRequest deployRequest = new DeployRequest();

        doThrow(new DeploymentException("Temporary failure"))
                .doThrow(new DeploymentException("Temporary failure"))
                .doAnswer(invocation -> null)
                .when(repositorySystem).deploy(any(), any(DeployRequest.class));

        mojo.deploy(deployRequest);

        verify(repositorySystem, times(3)).deploy(any(), any(DeployRequest.class));
    }

    @Test
    @MojoParameter(name = "retryFailedDeploymentCount", value = "0")
    @InjectMojo(goal = "populate", pom = "src/test/resources/PopulateMojoTest.xml")
    void testRetryFailedDeploymentCount_ZeroRetriesSingleAttemptOnFailure(PopulateRepositoryMojo mojo) throws Exception {

        MojoExtension.setVariableValueToObject(mojo, "session", createMavenSession());

        // 0 retries => 1 attempt total (initial attempt only)
        // Mock repository system
        RepositorySystem repositorySystem = mock(RepositorySystem.class);
        MojoExtension.setVariableValueToObject(mojo, "repositorySystem", repositorySystem);

        DeployRequest deployRequest = new DeployRequest();

        doThrow(new DeploymentException("Deployment failed"))
                .when(repositorySystem).deploy(any(), any(DeployRequest.class));

        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> {
            mojo.deploy(deployRequest);
        });
        assertTrue(assertThrows.getMessage().contains("Error deploying artifact"));

        // max(0, 0) + 1 = 1 deploy call
        verify(repositorySystem, times(1)).deploy(any(), any(DeployRequest.class));
    }

    @Test
    @MojoParameter(name = "retryFailedDeploymentCount", value = "-5")
    @InjectMojo(goal = "populate", pom = "src/test/resources/PopulateMojoTest.xml")
    void testRetryFailedDeploymentCount_NegativeClampedToZeroRetries(PopulateRepositoryMojo mojo) throws Exception {

        MojoExtension.setVariableValueToObject(mojo, "session", createMavenSession());

        // Negative retry count is clamped to 0 => 1 attempt total
        // Mock repository system
        RepositorySystem repositorySystem = mock(RepositorySystem.class);
        MojoExtension.setVariableValueToObject(mojo, "repositorySystem", repositorySystem);

        DeployRequest deployRequest = new DeployRequest();

        doThrow(new DeploymentException("Deployment failed"))
                .when(repositorySystem).deploy(any(), any(DeployRequest.class));

        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> {
            mojo.deploy(deployRequest);
        });
        assertTrue(assertThrows.getMessage().contains("Error deploying artifact"));

        // max(0, -5) + 1 = 1 deploy call
        verify(repositorySystem, times(1)).deploy(any(), any(DeployRequest.class));
    }

    @Test
    @MojoParameter(name = "retryFailedDeploymentCount", value = "3")
    @InjectMojo(goal = "populate", pom = "src/test/resources/PopulateMojoTest.xml")
    void testRetryFailedDeploymentCount_AllAttemptsFailure(PopulateRepositoryMojo mojo) throws Exception {
        MojoExtension.setVariableValueToObject(mojo, "session", createMavenSession());

        // 3 retries => 4 attempts total (1 initial + 3 retries)
        // Mock repository system
        RepositorySystem repositorySystem = mock(RepositorySystem.class);
        MojoExtension.setVariableValueToObject(mojo, "repositorySystem", repositorySystem);

        // Create a deploy request
        DeployRequest deployRequest = new DeployRequest();

        // Always fail
        doThrow(new DeploymentException("Persistent failure"))
                .when(repositorySystem).deploy(any(), any(DeployRequest.class));

        // Call deploy - should throw exception after all retries exhausted
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> {
            mojo.deploy(deployRequest);
        });
        assertTrue(assertThrows.getMessage().contains("Error deploying artifact"));

        verify(repositorySystem, times(4)).deploy(any(), any(DeployRequest.class));
    }

    private MavenSession createMavenSession() throws NoLocalRepositoryManagerException {
        MavenSession session = mock(MavenSession.class);
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new EnhancedLocalRepositoryManagerFactory(
                new DefaultLocalPathComposer(), new DefaultTrackingFileManager(), new DefaultLocalPathPrefixComposerFactory()
        )
                .newInstance(repositorySession, new LocalRepository(LOCAL_REPO)));
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        buildingRequest.setRepositorySession(repositorySession);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);
        when(session.getRepositorySession()).thenReturn(repositorySession);
        when(session.getPluginContext(any(PluginDescriptor.class), any(MavenProject.class)))
                .thenReturn(new ConcurrentHashMap<>());
        return session;
    }
}
