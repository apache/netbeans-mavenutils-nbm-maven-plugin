/*
 * Copyright 2024 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.netbeans.nbm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import static org.codehaus.plexus.PlexusTestCase.getBasedir;
import static org.junit.Assert.assertThrows;

import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.netbeans.nbm.handlers.NbmApplicationArtifactHandler;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.mockito.Mockito;

/**
 *
 */
public class CreateClusterAppMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-app-simple/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterAppMojo createclusterappMojo = (CreateClusterAppMojo) lookupMojo("cluster-app", pom);
        MavenProject project = (MavenProject) getVariableValueFromObject(createclusterappMojo, "project");
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        MavenSession mocksession = newTestMavenSession(project);
        setVariableValueToObject(createclusterappMojo, "session", mocksession);
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        assertNotNull(createclusterappMojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        assertEquals("This goal only makes sense on project with nbm-application packaging", assertThrows.getMessage());
    }

    public void testValidPackagingNoLauncher() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-app-complete-harness/plugin-configa.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterAppMojo createclusterappMojo = (CreateClusterAppMojo) lookupMojo("cluster-app", pom);
        MavenProject project = (MavenProject) getVariableValueFromObject(createclusterappMojo, "project");
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        MavenSession mocksession = newTestMavenSession(project);
        setVariableValueToObject(createclusterappMojo, "session", mocksession);
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        assertNotNull(createclusterappMojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        assertEquals("We could not find org-netbeans-bootstrap among the modules in the application. Launchers could not be found.", assertThrows.getMessage());
    }

    public void testValidPackagingHarnessLauncher() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-app-complete-harness/plugin-configb.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterAppMojo createclusterappMojo = (CreateClusterAppMojo) lookupMojo("cluster-app", pom);
        MavenProject project = (MavenProject) getVariableValueFromObject(createclusterappMojo, "project");
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        MavenSession mocksession = newTestMavenSession(project);
        setVariableValueToObject(createclusterappMojo, "session", mocksession);
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        // F//ile buildfolder = (File) getVariableValueFromObject(createclusterappMojo, "outputDirectory");
        createDummyApp("project-cluster-app-complete-harnessb", "mybrand");
        assertNotNull(createclusterappMojo);
        createclusterappMojo.execute();
    }

    public void testValidPackagingHarnessLauncherDefault() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-app-complete-harness/plugin-configc.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterAppMojo createclusterappMojo = (CreateClusterAppMojo) lookupMojo("cluster-app", pom);
        MavenProject project = (MavenProject) getVariableValueFromObject(createclusterappMojo, "project");
        setVariableValueToObject(createclusterappMojo, "brandingToken", "mybrand");
        MavenSession mocksession = newTestMavenSession(project);
        setVariableValueToObject(createclusterappMojo, "session", mocksession);
        // F//ile buildfolder = (File) getVariableValueFromObject(createclusterappMojo, "outputDirectory");
        // createDummyApp("project-cluster-app-complete-harnessb", "mybrand");
        assertNotNull(createclusterappMojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        assertEquals("Failed to retrieve the nbm file from repository", assertThrows.getMessage());
    }

    private void createDummyApp(String folder, String branding) throws IOException {
        File file = new File(getBasedir(), "target/test-harness/" + folder + "/" + branding + "/harness/");
        file.mkdirs();
        File launchers = new File(file, "launchers/");
        launchers.mkdirs();
        for (String launchname : Arrays.asList("app.exe", "app64.exe", "app.sh")) {
            File launch = new File(launchers, launchname);
            launch.createNewFile();
        }

        /**
         * if (binDir.exists()) { File exe = new File(binDir, "app.exe");
         * FileUtils.copyFile(exe, destExe); File exe64 = new File(binDir,
         * "app64.exe"); if (exe64.isFile()) { FileUtils.copyFile(exe64,
         * destExe64); } File exew = new File(binDir, "app_w.exe"); if
         * (exew.exists()) //in 6.7 the _w.exe file is no more. {
         * FileUtils.copyFile(exew, destExeW); } File sh = new File(binDir,
         * "app.sh"); FileUtils.copyFile(sh, destSh);
         */
    }

    // overrides

    protected MavenSession newTestMavenSession(MavenProject project) {
        try {
            project.setArtifacts(Collections.emptySet());
            project.setDependencyArtifacts(Collections.emptySet());

            MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            MavenExecutionResult result = new DefaultMavenExecutionResult();
            DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
            repoSession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory(new DefaultLocalPathComposer()).newInstance(repoSession, new LocalRepository("")));
            MavenSession session = new MavenSession(getContainer(), repoSession, request, result);
            session.setCurrentProject(project);
            session.setProjects(Arrays.asList( project));
            return session;
        } catch (Exception e) {
            throw new  RuntimeException(e);
        }
    }
}

