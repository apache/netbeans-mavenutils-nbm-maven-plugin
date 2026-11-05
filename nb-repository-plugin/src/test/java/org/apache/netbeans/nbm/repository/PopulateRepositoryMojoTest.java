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
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.tools.ant.Project;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.DefaultLocalPathPrefixComposerFactory;
import org.eclipse.aether.internal.impl.DefaultTrackingFileManager;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.util.artifact.SubArtifact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.netbeans.nbbuild.MakeNBM;

/**
 *
 * @author Milos Kleint
 */
public class PopulateRepositoryMojoTest extends AbstractMojoTestCase {

    private final String LOCAL_REPO = "target/local-repo/";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + LOCAL_REPO));
    }

    public void testStripClusterName() {
        assertEquals("platform", PopulateRepositoryMojo.stripClusterName("platform9"));
        assertEquals("platform", PopulateRepositoryMojo.stripClusterName("platform11"));
        assertEquals("nb", PopulateRepositoryMojo.stripClusterName("nb6.9"));
        assertEquals("extra", PopulateRepositoryMojo.stripClusterName("extra"));
    }

    public void testInstall() throws Exception {
        PopulateRepositoryMojo mojo = (PopulateRepositoryMojo) lookupMojo("populate", new File(getBasedir(), "src/test/resources/PopulateMojoTest.xml"));
        setVariableValueToObject(mojo, "session", createMavenSession());
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

    public void testInstallNbmAndJar() throws Exception {
        PopulateRepositoryMojo mojo = preparePopulateRepositoryMojo();
        mojo.execute();

        File localRepo = mojo.session.getRepositorySession().getLocalRepository().getBasedir();
        assertFile("pom.xml has been registered", files(localRepo, "testgrp", "modules", "testmodule", "0.33", "testmodule-0.33.pom"));
        assertFile("Module JAR has been registered", files(localRepo, "testgrp", "modules", "testmodule", "0.33", "testmodule-0.33.jar"));
        assertFile("NBM file has been registered", files(localRepo, "testgrp", "modules", "testmodule", "0.33", "testmodule-0.33.nbm"));
    }

    public void testInstallWithCluster() throws Exception {
        PopulateRepositoryMojo mojo = preparePopulateRepositoryMojo();
        setVariableValueToObject(mojo, "defineCluster", true);
        setVariableValueToObject(mojo, "forcedVersion", "99-SNAPSHOT");
        mojo.execute();

        File localRepo = mojo.session.getRepositorySession().getLocalRepository().getBasedir();
        assertFile("pom.xml has been registered", files(localRepo, "testgrp", "modules", "testmodule", "99-SNAPSHOT", "testmodule-99-SNAPSHOT.pom"));
        assertFile("Module JAR has been registered", files(localRepo, "testgrp", "modules", "testmodule", "99-SNAPSHOT", "testmodule-99-SNAPSHOT.jar"));
        assertFile("NBM file has been registered", files(localRepo, "testgrp", "modules", "testmodule", "99-SNAPSHOT", "testmodule-99-SNAPSHOT.nbm"));

        File clusterPom = files(localRepo, "testgrp", "cluster", "extra", "99-SNAPSHOT", "extra-99-SNAPSHOT.pom");
        assertFile("Cluster pom created", clusterPom,
            "<dependency>",
            "  <groupId>testgrp.modules</groupId>",
            "  <artifactId>testmodule</artifactId>",
            "  <version>99-SNAPSHOT</version>",
            "  <type>nbm-file</type>",
            "</dependency>"
        );

        File clusterCatalog = files(localRepo, "testgrp", "cluster", "extra", "99-SNAPSHOT", "extra-99-SNAPSHOT.xml.gz");
        assertFile("Cluster catalog found", clusterCatalog);
    }

    private PopulateRepositoryMojo preparePopulateRepositoryMojo() throws IllegalAccessException, NoLocalRepositoryManagerException, Exception, IOException {
        Path installDir = Files.createTempDirectory("netbeansInstall");
        Path clusterDir = Files.createDirectory(installDir.resolve("extra"));
        Path modulesDir = Files.createDirectory(clusterDir.resolve("modules"));
        File moduleJar = Files.createFile(modulesDir.resolve("testmodule.jar")).toFile();
        assertEquals("Modules dir is inside of install dir", modulesDir.getParent().getParent(), installDir);
        Manifest moduleMan = new Manifest();
        moduleMan.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        moduleMan.getMainAttributes().putValue("OpenIDE-Module", "testmodule");
        moduleMan.getMainAttributes().putValue("OpenIDE-Module-Specification-Version", "0.33");
        try (var os = new JarOutputStream(new FileOutputStream(moduleJar), moduleMan)) {
            os.putNextEntry(new JarEntry("empty.txt"));
            os.closeEntry();
        }
        Path updatesDir = Files.createDirectory(modulesDir.resolve("update_tracking"));
        File updatesXml = Files.createFile(updatesDir.resolve("testmodule.xml")).toFile();
        try (var os = new FileWriter(updatesXml)) {
            os.write("""
            <?xml version="1.0" encoding="UTF-8"?><module codename="testgrp.testmodule">
                <module_version install_time="1793684081731" last="true" origin="installer" specification_version="0.33">
                </module_version>
            </module>
            """);
        }
        assertTrue(updatesXml.isFile());
        moduleJar.deleteOnExit();
        Path nbmDir = Files.createTempDirectory("nbms");
        File moduleNbm = nbmDir.resolve("testmodule.nbm").toFile();
        Project prj = new Project();
        MakeNBM makeNbm = new MakeNBM();
        makeNbm.setProject(prj);
        makeNbm.setFile(moduleNbm);
        makeNbm.setModule(moduleJar.getName());
        makeNbm.setProductDir(modulesDir.toFile());
        assertFalse("NBM file doesn't exist yet " + moduleNbm, moduleNbm.exists());
        makeNbm.execute();
        moduleNbm.deleteOnExit();
        assertTrue("NBM file was generated: " + moduleNbm, moduleNbm.length() > 0);


        PopulateRepositoryMojo mojo = (PopulateRepositoryMojo) lookupMojo("populate", new File(getBasedir(), "src/test/resources/PopulateMojoTest.xml"));
        setVariableValueToObject(mojo, "session", createMavenSession());
        mojo.netbeansInstallDirectory = installDir.toFile();
        mojo.netbeansNbmDirectory = nbmDir.toFile();
        setVariableValueToObject(mojo, "groupIdPrefix", "testgrp");
        return mojo;
    }

    public void testSplit() throws Exception {
        Dependency dep1 = PopulateRepositoryMojo.splitDependencyString("org.apache.maven:apache-maven:3.6.3:bin@zip");
        assertEquals("org.apache.maven", dep1.getGroupId());
        assertEquals("apache-maven", dep1.getArtifactId());
        assertEquals("3.6.3", dep1.getVersion());
        assertEquals("bin", dep1.getClassifier());
        assertEquals("zip", dep1.getType());

        Dependency dep2 = PopulateRepositoryMojo.splitDependencyString("org.apache.maven:apache-maven:3.6.3:myclassifier");
        assertEquals("org.apache.maven", dep2.getGroupId());
        assertEquals("apache-maven", dep2.getArtifactId());
        assertEquals("3.6.3", dep2.getVersion());
        assertEquals("myclassifier", dep2.getClassifier());
        assertEquals("jar", dep2.getType());

        Dependency dep3 = PopulateRepositoryMojo.splitDependencyString("org.apache.maven:apache-maven:3.6.3");
        assertEquals("org.apache.maven", dep3.getGroupId());
        assertEquals("apache-maven", dep3.getArtifactId());
        assertEquals("3.6.3", dep3.getVersion());
        assertEquals("", dep3.getClassifier());
        assertEquals("jar", dep3.getType());
    }

    public void testEncode() throws Exception {
        assertEquals("057558504e1d03e57ce6fd80ad983b3c2e803b40", PopulateRepositoryMojo.encode(new byte[]{
            (byte) 0x05, (byte) 0x75, (byte) 0x58, (byte) 0x50, (byte) 0x4e, (byte) 0x1d, (byte) 0x03, (byte) 0xe5, (byte) 0x7c, (byte) 0xe6,
            (byte) 0xfd, (byte) 0x80, (byte) 0xad, (byte) 0x98, (byte) 0x3b, (byte) 0x3c, (byte) 0x2e, (byte) 0x80, (byte) 0x3b, (byte) 0x40,}));
        assertEquals("ca70822c47a67fc3a11670270567c2d01566dae1", PopulateRepositoryMojo.encode(new byte[]{
            (byte) 0xca, (byte) 0x70, (byte) 0x82, (byte) 0x2c, (byte) 0x47, (byte) 0xa6, (byte) 0x7f, (byte) 0xc3, (byte) 0xa1, (byte) 0x16,
            (byte) 0x70, (byte) 0x27, (byte) 0x05, (byte) 0x67, (byte) 0xc2, (byte) 0xd0, (byte) 0x15, (byte) 0x66, (byte) 0xda, (byte) 0xe1,}));
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
                .thenReturn(new ConcurrentHashMap<String, Object>());
        return session;
    }

    private static File files(File root, String... segments) {
        File file = root;
        for (var s : segments) {
            file = new File(file, s);
        }
        return file;
    }

    private static void assertFile(String msg, File file, String... expectedLines) throws IOException {
        if (file.isFile()) {
            if (expectedLines.length > 0) {
                Set<String> notYetFound = new TreeSet<>();
                for (var l : expectedLines) {
                    notYetFound.add(l.trim());
                }
                for (var l : Files.readAllLines(file.toPath())) {
                    notYetFound.remove(l.trim());
                }
                if (!notYetFound.isEmpty()) {
                    fail("Expecting " + notYetFound + " lines in " + file);
                }
            }
            return;
        }
        var sb = new StringBuilder();
        sb.append(msg);
        for (;;) {
            File parentDir = file.getParentFile();
            if (parentDir == null) {
                break;
            }
            if (parentDir.isDirectory()) {
                sb.append("\nContent of ").append(parentDir.getPath());
                for (var ch : parentDir.listFiles()) {
                    sb.append("\n  ").append(ch.getName());
                }
                break;
            }
            sb.append("\nNot found ").append(parentDir);
            file = parentDir;
        }
        fail(sb.toString());
    }
}
