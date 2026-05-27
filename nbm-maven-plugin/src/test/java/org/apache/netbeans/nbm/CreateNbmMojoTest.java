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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.netbeans.nbm.handlers.NbmApplicationArtifactHandler;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 *
 */
@MojoTest
class CreateNbmMojoTest {

    @Test
    @InjectMojo(goal = "nbm", pom = "src/test/resources/unit/nbm-pom/plugin-config.xml")
    void testInvalidPackaging(CreateNbmMojo createnbmmojo) throws Exception {
        assertNotNull(createnbmmojo);
        createnbmmojo.execute();
    }

    @Test
    @InjectMojo(goal = "nbm", pom = "src/test/resources/unit/nbm-nbm/plugin-config.xml")
    @MojoParameter(name = "codeNameBase", value = "my.group.my-artifact")
    @MojoParameter(name = "finalName", value = "foo")
    @MojoParameter(name = "nbmJavahelpSource", value = "${project.basedir}/target/test-classes/unit/nbm-nbm/notexisting.help")

    void testValidPackaging(CreateNbmMojo createnbmmojo) throws Exception {
        assertNotNull(createnbmmojo);
        createDummyJarApp("project-nbm-nbm");
        createnbmmojo.execute();
    }

    @Test
    @InjectMojo(goal = "nbm", pom = "src/test/resources/unit/nbm-nbm-withcontent/plugin-config.xml")
    @MojoParameter(name = "codeNameBase", value = "my.group.my-artifact")
    @MojoParameter(name = "finalName", value = "foo")
    @MojoParameter(name = "nbmJavahelpSource", value = "${project.basedir}/target/test-classes/unit/nbm-nbm-withcontent/notexisting.help")
    void testValidPackagingWithContent(CreateNbmMojo createnbmmojo) throws Exception {
        assertNotNull(createnbmmojo);
        createDummyJarApp("project-nbm-nbm-withcontent");
        createnbmmojo.execute();
    }

    @Test
    @InjectMojo(goal = "nbm", pom = "src/test/resources/unit/nbm-nbm-withcontent-site/plugin-config.xml")
    @MojoParameter(name = "codeNameBase", value = "my.group.my-artifact")
    @MojoParameter(name = "finalName", value = "foo")
    @MojoParameter(name = "distributionUrl", value = "https://bit.netbeans.org/testme")

    @MojoParameter(name = "nbmJavahelpSource", value = "${project.basedir}/target/test-classes/unit/nbm-nbm-withcontent-site/notexisting.help")

    void testValidPackagingWithContentSite(CreateNbmMojo createnbmmojo) throws Exception {
        assertNotNull(createnbmmojo);
        createDummyJarApp("project-nbm-nbm-withcontent-site");
        createnbmmojo.execute();
    }

    @Test
    @InjectMojo(goal = "nbm", pom = "src/test/resources/unit/nbm-nbm-withcontent-site/plugin-config.xml")
    @MojoParameter(name = "codeNameBase", value = "my.group.my-artifact")
    @MojoParameter(name = "finalName", value = "foo")
    @MojoParameter(name = "distributionUrl", value = "id::default::url")
    @MojoParameter(name = "nbmJavahelpSource", value = "${project.basedir}/target/test-classes/unit/nbm-nbm-withcontent-site/notexisting.help")

    void testValidPackagingWithContentSiteAlt(CreateNbmMojo createnbmmojo) throws Exception {
        assertNotNull(createnbmmojo);
        MojoExtension.setVariableValueToObject(createnbmmojo, "session", newTestMavenSession());
        createDummyJarApp("project-nbm-nbm-withcontent-site");
        createnbmmojo.execute();
    }

    private void createDummyJarApp(String folder) throws IOException {
        File file = new File(MojoExtension.getBasedir(), "target/test-harness/" + folder + "/target/foo.jar");
        file.getParentFile().mkdirs();
        file.createNewFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module"), "test");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Name"), "test");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Specification-Version"), "test");
        try (FileOutputStream fos = new FileOutputStream(file); JarOutputStream jos = new JarOutputStream(fos, manifest);) {
            JarEntry jarAdd = new JarEntry("entry1");
            jos.putNextEntry(jarAdd);
            jos.closeEntry();
        }
    }

    // overrides
    protected MavenSession newTestMavenSession() {
        try {
            MavenProject project = new MavenProject();
            project.setPackaging(NbmApplicationArtifactHandler.NAME);
            project.setArtifacts(Collections.emptySet());
            project.setDependencyArtifacts(Collections.emptySet());

            DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
            repoSession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory(new DefaultLocalPathComposer()).newInstance(repoSession, new LocalRepository("")));
            MavenSession session = Mockito.mock(MavenSession.class);
            session.setCurrentProject(project);
            session.setProjects(Arrays.asList(project));
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
