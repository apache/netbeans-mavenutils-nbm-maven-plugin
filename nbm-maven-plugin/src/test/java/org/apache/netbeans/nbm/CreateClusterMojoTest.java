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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.project.MavenProject;
import static org.codehaus.plexus.PlexusTestCase.getBasedir;
import static org.junit.Assert.assertThrows;
import org.mockito.Mockito;

/**
 *
 */
public class CreateClusterMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testEmptyProject() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-simple/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterMojo createclustermojo = (CreateClusterMojo) lookupMojo("cluster", pom);
        MavenSession mocksession = Mockito.mock(MavenSession.class);
        setVariableValueToObject(createclustermojo, "session", mocksession);
        Mockito.doReturn(new MavenProjectStubImpl()).when(mocksession).getCurrentProject();
        assertNotNull(createclustermojo);
        MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclustermojo.execute());
        assertEquals("This goal only makes sense on reactor projects.", assertThrows.getMessage());
    }

    public void testNoClusterPathNbmPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-aggregate/plugin-configa.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterMojo createclustermojo = (CreateClusterMojo) lookupMojo("cluster", pom);
        MavenSession mocksession = Mockito.mock(MavenSession.class);
        List<MavenProject> lmp = new ArrayList<>();
        lmp.add(new MavenProjectStub() {
            @Override
            public String getPackaging() {
                return "nbm";
            }

            @Override
            public String getId() {
                return "foo";
            }

            @Override
            public Build getBuild() {
                Build mock = Mockito.mock(Build.class);
                Mockito.doReturn(getBasedir() + File.separator + "nbm").when(mock).getDirectory();
                return mock;
            }

        });
        Mockito.doReturn(lmp).when(mocksession).getProjects();
        Mockito.doReturn(new MavenProjectStubImpl()).when(mocksession).getCurrentProject();
        setVariableValueToObject(createclustermojo, "session", mocksession);
        assertNotNull(createclustermojo);
        MojoFailureException assertThrows = assertThrows(MojoFailureException.class, () -> createclustermojo.execute());
        assertEquals("The NetBeans binary directory structure for foo is not created yet." + "\n Please execute 'mvn install nbm:cluster' "
                + "to build all relevant projects in the reactor.", assertThrows.getMessage());
    }

    public void testNoClusterPathNbmPackagingClusterfolder() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-aggregate/plugin-configb.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterMojo createclustermojo = (CreateClusterMojo) lookupMojo("cluster", pom);
        MavenSession mocksession = Mockito.mock(MavenSession.class);
        List<MavenProject> lmp = new ArrayList<>();
        lmp.add(new MavenProjectStub() {
            @Override
            public String getPackaging() {
                return "nbm";
            }

            @Override
            public String getId() {
                return "foo";
            }

            @Override
            public Build getBuild() {
                Build mock = Mockito.mock(Build.class);
                Mockito.doReturn(getBasedir() + File.separator + "nbm").when(mock).getDirectory();
                try {
                    Files.createDirectories(Paths.get(getBasedir() + File.separator + "nbm"));
                } catch (IOException ex) {
                    System.getLogger(CreateClusterMojoTest.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
                return mock;
            }

        });
        Mockito.doReturn(lmp).when(mocksession).getProjects();
        Mockito.doReturn(new MavenProjectStubImpl()).when(mocksession).getCurrentProject();
        File clusterdir = (File) getVariableValueFromObject(createclustermojo, "nbmBuildDir");
        File file = new File(clusterdir, "clusters");
        file.mkdirs();
        setVariableValueToObject(createclustermojo, "session", mocksession);
        assertNotNull(createclustermojo);

        createclustermojo.execute();
        file.delete();
    }

    public void testNoClusterPathBundlePackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-aggregate/plugin-configd.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterMojo createclustermojo = (CreateClusterMojo) lookupMojo("cluster", pom);
        MavenSession mocksession = Mockito.mock(MavenSession.class);
        List<MavenProject> lmp = new ArrayList<>();
        lmp.add(new MavenProjectStub() {
            @Override
            public String getPackaging() {
                return "bundle";
            }

            @Override
            public String getId() {
                return "foo";
            }

            @Override
            public Build getBuild() {
                Build buildMock = Mockito.mock(Build.class);
                return buildMock;
            }

        });
        Mockito.doReturn(lmp).when(mocksession).getProjects();
        Mockito.doReturn(new MavenProjectStubImpl()).when(mocksession).getCurrentProject();
        setVariableValueToObject(createclustermojo, "session", mocksession);
        assertNotNull(createclustermojo);
        createclustermojo.execute();
    }

    public void testNoClusterPathBundlePackagingJar() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/cluster-aggregate/plugin-configc.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateClusterMojo createclustermojo = (CreateClusterMojo) lookupMojo("cluster", pom);
        MavenSession mocksession = Mockito.mock(MavenSession.class);
        createDummyJarApp("project-cluster-aggregatec");
        List<MavenProject> lmp = new ArrayList<>();
        lmp.add(new MavenProjectStub() {
            @Override
            public String getPackaging() {
                return "bundle";
            }

            @Override
            public String getId() {
                return "foo";
            }

            @Override
            public Artifact getArtifact() {
                return new ArtifactStub();
            }

            @Override
            public Properties getProperties() {
                Properties properties = new Properties();
                // ZIP dates allowed only: valid range 1980-01-01T00:00:02Z to 2099-12-31T23:59:59Z
                properties.put("project.build.outputTimestamp", "1980-01-01T00:00:02Z");
                return properties;
            }

            @Override
            public Build getBuild() {
                Build buildMock = Mockito.mock(Build.class);
                //   File jar = new File(proj.getBuild().getDirectory(), proj.getBuild().getFinalName() + ".jar");
                Mockito.doReturn("foo").when(buildMock).getFinalName();
                File file = new File(getBasedir(), "target/test-harness/project-cluster-aggregatec/target");
                Mockito.doReturn(file.getAbsolutePath()).when(buildMock).getDirectory();
                return buildMock;
            }

        });
        Mockito.doReturn(lmp).when(mocksession).getProjects();
        Mockito.doReturn(new MavenProjectStubImpl()).when(mocksession).getCurrentProject();
        setVariableValueToObject(createclustermojo, "project", lmp.get(0));
        setVariableValueToObject(createclustermojo, "session", mocksession);
        setVariableValueToObject(createclustermojo, "cluster", "cl");
        assertNotNull(createclustermojo);
        createclustermojo.execute();
    }

    private void createDummyJarApp(String folder) throws IOException {
        File file = new File(getBasedir(), "target/test-harness/" + folder + "/target/foo.jar");
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

    private static class MavenProjectStubImpl extends MavenProjectStub {

        public MavenProjectStubImpl() {
        }

        @Override
        public String getPackaging() {
            return "nbm";
        }

        @Override
        public String getId() {
            return "foo";
        }

        @Override
        public Build getBuild() {
            Build mock = Mockito.mock(Build.class);
            Mockito.doReturn(getBasedir() + File.separator + "nbm").when(mock).getDirectory();
            return mock;
        }

    }
}
