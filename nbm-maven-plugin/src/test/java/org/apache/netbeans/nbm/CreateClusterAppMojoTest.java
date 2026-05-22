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
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 *
 */
@MojoTest
class CreateClusterAppMojoTest {

    @Test
    @InjectMojo(goal = "cluster-app", pom = "src/test/resources/unit/cluster-app-simple/plugin-config.xml")
    @MojoParameter(name = "brandingToken", value = "mybrand")
    void testInvalidPackaging(CreateClusterAppMojo createclusterappMojo) throws Exception {
        MavenProject project = (MavenProject) MojoExtension.getVariableValueFromObject(createclusterappMojo, "project");
        MavenSession mocksession = newTestMavenSession(project);
        MojoExtension.setVariableValueToObject(createclusterappMojo, "session", mocksession);
        assertNotNull(createclusterappMojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        assertEquals("This goal only makes sense on project with nbm-application packaging", assertThrows.getMessage());
    }

    @Test
    @InjectMojo(goal = "cluster-app", pom = "src/test/resources/unit/cluster-app-complete-harness/plugin-configa.xml")
    @MojoParameter(name = "brandingToken", value = "mybrand")
    void testValidPackagingNoLauncher(CreateClusterAppMojo createclusterappMojo) throws Exception {
        MavenProject project = (MavenProject) MojoExtension.getVariableValueFromObject(createclusterappMojo, "project");
        MavenSession mocksession = newTestMavenSession(project);
        MojoExtension.setVariableValueToObject(createclusterappMojo, "session", mocksession);
        assertNotNull(createclusterappMojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        assertEquals("We could not find org-netbeans-bootstrap among the modules in the application. Launchers could not be found.", assertThrows.getMessage());
    }

    @Test
    @InjectMojo(goal = "cluster-app", pom = "src/test/resources/unit/cluster-app-complete-harness/plugin-configb.xml")
    @MojoParameter(name = "brandingToken", value = "mybrand")
    void testValidPackagingHarnessLauncher(CreateClusterAppMojo createclusterappMojo) throws Exception {
        MavenProject project = (MavenProject) MojoExtension.getVariableValueFromObject(createclusterappMojo, "project");
        MavenSession mocksession = newTestMavenSession(project);
        MojoExtension.setVariableValueToObject(createclusterappMojo, "session", mocksession);
        createDummyApp("project-cluster-app-complete-harnessb", "mybrand");
        assertNotNull(createclusterappMojo);
        createclusterappMojo.execute();
    }

    @Test
    @InjectMojo(goal = "cluster-app", pom = "src/test/resources/unit/cluster-app-complete-harness/plugin-configc.xml")
    @MojoParameter(name = "brandingToken", value = "mybrand")
    void testValidPackagingHarnessLauncherDefault(CreateClusterAppMojo createclusterappMojo) throws Exception {
        MavenProject project = (MavenProject) MojoExtension.getVariableValueFromObject(createclusterappMojo, "project");
        MavenSession mocksession = newTestMavenSession(project);
        MojoExtension.setVariableValueToObject(createclusterappMojo, "session", mocksession);
        assertNotNull(createclusterappMojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        assertEquals("Failed to retrieve the nbm file from repository", assertThrows.getMessage());
    }

    private void createDummyApp(String folder, String branding) throws IOException {
        File file = new File(MojoExtension.getBasedir(), "target/test-harness/" + folder + "/" + branding + "/harness/");
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
            DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
            repoSession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory(new DefaultLocalPathComposer()).newInstance(repoSession, new LocalRepository("")));
            MavenSession session = Mockito.mock(MavenSession.class);
            session.setCurrentProject(project);
            session.setProjects(Arrays.asList(project));
            Mockito.when(session.getRepositorySession()).thenReturn(repoSession);
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
