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
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.apache.netbeans.nbm.model.Dependency;
import org.apache.netbeans.nbm.model.NetBeansModule;
import org.apache.netbeans.nbm.stubs.ArtifactHandlerManagerStub;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author mkleint
 */
class AbstractNbmMojoTest {

    Log log = null;
    DependencyNode treeRoot = null;
    Artifacts artifacts = new Artifacts(new ArtifactHandlerManagerStub());

    @BeforeEach
    void setUp() throws Exception {
        log = new SystemStreamLog();
        treeRoot = createNode(null, "root", "root", "1.0", "jar", "", true, new ArrayList<>(), new HashMap<>());
    }

    /**
     * Test of matchesLibrary method, of class AbstractNbmMojo.
     */
    @Test
    void testMatchesLibrary() {
        DependencyVisitorSupport subject = new DependencyVisitorSupport(log, artifacts) {
            @Override
            public boolean visitEnter(DependencyNode node) {
                return false;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                return false;
            }
        };
        System.out.println("matchesLibrary");
        Artifact artifact = createArtifact("group", "artifact", "1.0", "jar");
        List<String> libraries = new ArrayList<>();
        libraries.add("group:artifact");
        ExamineManifest depExaminator = createNonModule();
        boolean result = subject.matchesLibrary(artifact, "compile", libraries, depExaminator, false);
        assumeTrue(result, "explicitly defined libraries in descriptor are included");

        artifact = createArtifact("group", "artifact", "1.0", "jar");
        libraries = new ArrayList<>();
        result = subject.matchesLibrary(artifact, "provided", libraries, depExaminator, false);
        assumeFalse(result, "provided artifacts are not included");

        artifact = createArtifact("group", "artifact", "1.0", "jar");
        libraries = new ArrayList<>();
        result = subject.matchesLibrary(artifact, "system", libraries, depExaminator, false);
        assumeFalse(result, "system artifacts are not included");

        artifact = createArtifact("group", "artifact", "1.0", "jar");
        libraries = new ArrayList<>();
        libraries.add("group:artifact");
        depExaminator = createModule();
        result = subject.matchesLibrary(artifact, "compile", libraries, depExaminator, false);
        assumeTrue(result, "netbeans modules are included if explicitly marked in descriptor");

        libraries = new ArrayList<>();
        result = subject.matchesLibrary(artifact, "compile", libraries, depExaminator, false);
        assumeFalse(result, "netbeans modules are omitted");

        artifact = createArtifact("group", "artifact", "1.0", "nbm");
        libraries = new ArrayList<>();
        result = subject.matchesLibrary(artifact, "compile", libraries, depExaminator, false);
        assumeFalse(result, "netbeans modules are omitted");

    }

    /**
     * Test of resolveNetBeansDependency method, of class AbstractNbmMojo.
     */
    @Test
    void testResolveNetBeansDependency() {
        Artifact artifact = createArtifact("group", "artifact", "1.0", "jar");
        List<Dependency> deps = new ArrayList<>();
        ExamineManifest manifest = createNonModule();
        Dependency result = AbstractNbmMojo.resolveNetBeansDependency(artifacts, artifact, deps, manifest, log);
        assertNull(result, "not a NetBeans module");

        manifest = createModule();
        result = AbstractNbmMojo.resolveNetBeansDependency(artifacts, artifact, deps, manifest, log);
        assertNotNull(result, "is a NetBeans module");

        artifact = createArtifact("group", "artifact", "1.0", "nbm");
        manifest = createNonModule();
        result = AbstractNbmMojo.resolveNetBeansDependency(artifacts, artifact, deps, manifest, log);
        assertNotNull(result, "nbm type is a NetBeans module");

        artifact = createArtifact("group", "artifact", "1.0", "jar");
        deps = new ArrayList<>();
        Dependency d = new Dependency();
        d.setId("group:artifact");
        deps.add(d);
        manifest = createNonModule();
        result = AbstractNbmMojo.resolveNetBeansDependency(artifacts, artifact, deps, manifest, log);
        assertNull(result, "not a NetBeans module, declared in deps but without explicit value");

        d.setExplicitValue("XXX > 1.0");
        result = AbstractNbmMojo.resolveNetBeansDependency(artifacts, artifact, deps, manifest, log);
        assertEquals(result, d, "not a NetBeans module but declared with explicit value");

        d.setExplicitValue(null);
        manifest = createModule();
        result = AbstractNbmMojo.resolveNetBeansDependency(artifacts, artifact, deps, manifest, log);
        assertEquals(result, d, "netbeans module defined in descriptor");
    }

    /**
     * Module is not a library
     *
     * @throws java.lang.Exception if AbstractNbmMojo.getLibraryArtifacts fail
     */
    @Test
    void testGetLibraryArtifacts1() throws Exception {
        System.out.println("getLibraryArtifacts1");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<>();
        List<Artifact> runtimes = new ArrayList<>();
        DependencyNode module = createNode(treeRoot, "gr1", "ar1", "1.0", "jar", "compile", true, runtimes, examinerCache);
        treeRoot.setChildren(Collections.singletonList(module));
        NetBeansModule mdl = new NetBeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(artifacts, treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(0, result.size());
    }

    /**
     * direct dependency is a library
     *
     * @throws java.lang.Exception if AbstractNbmMojo.getLibraryArtifacts fail
     */
    @Test
    void testGetLibraryArtifact2() throws Exception {
        System.out.println("getLibraryArtifacts2");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<>();
        List<Artifact> runtimes = new ArrayList<>();
        DependencyNode library = createNode(treeRoot, "gr1", "ar1", "1.0", "jar", "compile", false, runtimes, examinerCache);
        treeRoot.setChildren(Collections.singletonList(library));
        NetBeansModule mdl = new NetBeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(artifacts, treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(1, result.size());
    }

    /**
     * transitive dependency gets included as well.
     *
     * @throws java.lang.Exception if AbstractNbmMojo.getLibraryArtifacts fail
     */
    @Test
    void testGetLibraryArtifact3() throws Exception {
        System.out.println("getLibraryArtifacts3");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<>();
        List<Artifact> runtimes = new ArrayList<>();
        DependencyNode library = createNode(treeRoot, "gr1", "ar1", "1.0", "jar", "compile", false, runtimes, examinerCache);
        treeRoot.setChildren(Collections.singletonList(library));
        DependencyNode translibrary = createNode(library, "gr2", "ar2", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        ((DefaultDependencyNode) library).setChildren(Collections.singletonList(translibrary));

        NetBeansModule mdl = new NetBeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(artifacts, treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(2, result.size());
    }

    /**
     * transitive dependency of a module doesn't get included as library
     *
     * @throws java.lang.Exception if AbstractNbmMojo.getLibraryArtifacts fail
     */
    @Test
    void testGetLibraryArtifact4() throws Exception {
        System.out.println("getLibraryArtifacts4");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<>();
        List<Artifact> runtimes = new ArrayList<>();
        DependencyNode module = createNode(treeRoot, "gr1", "ar1", "1.0", "jar", "compile", true, runtimes, examinerCache);
        treeRoot.setChildren(Collections.singletonList(module));
        DependencyNode translibrary = createNode(module, "gr2", "ar2", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        ((DefaultDependencyNode) module).setChildren(Collections.singletonList(translibrary));
        NetBeansModule mdl = new NetBeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(artifacts, treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(0, result.size());
    }

    /**
     * transitive dependency of a library is a duplicate of a transitive
     * dependency of a module -&gt;doesn't get included.
     *
     * @throws java.lang.Exception if AbstractNbmMojo.getLibraryArtifacts fail
     */
    @Test
    void testGetLibraryArtifact5() throws Exception {
        System.out.println("getLibraryArtifacts5");
        Map<Artifact, ExamineManifest> examinerCache = new HashMap<>();
        List<Artifact> runtimes = new ArrayList<>();
        DependencyNode module = createNode(treeRoot, "gr1", "ar1", "1.0", "jar", "compile", true, runtimes, examinerCache);
        DependencyNode translibrary = createNode(module, "gr2", "ar2", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        ((DefaultDependencyNode) module).setChildren(Collections.singletonList(translibrary));

        DependencyNode library = createNode(treeRoot, "gr3", "ar3", "1.0", "jar", "compile", false, runtimes, examinerCache);
        DependencyNode translibrary2 = createNode(library, "gr4", "ar4", "1.0", "jar", "runtime", false, runtimes, examinerCache);
        ((DefaultDependencyNode) library).setChildren(Collections.singletonList(translibrary2));
        treeRoot.setChildren(Arrays.asList(new DependencyNode[]{
            module, library
        }));

        NetBeansModule mdl = new NetBeansModule();
        List<Artifact> result = AbstractNbmMojo.getLibraryArtifacts(artifacts, treeRoot, mdl, runtimes, examinerCache, log, false);
        assertEquals(2, result.size());
        assertEquals(ArtifactIdUtils.toId(result.get(0)), ArtifactIdUtils.toId(library.getArtifact()));
        assertEquals(ArtifactIdUtils.toId(result.get(1)), ArtifactIdUtils.toId(translibrary2.getArtifact()));
    }

    /**
     * Test of getOutputTimestampOrNow method, of class AbstractNbmMojo.
     */
    @Test
    void testGetOutputTimestampOrNow() {
        MavenProject project = new MavenProject();

        assumeFalse(AbstractNbmMojo.getOutputTimestampOrNow(project).toInstant().equals(Instant.ofEpochSecond(1570300662)), "missing property returns new Date()");

        project.getProperties().put("project.build.outputTimestamp", "2019-10-05T18:37:42Z");
        assumeTrue(AbstractNbmMojo.getOutputTimestampOrNow(project).toInstant().equals(Instant.ofEpochSecond(1570300662)), "valid formatted property");

        project.getProperties().put("project.build.outputTimestamp", "1570300662");
        assumeTrue(AbstractNbmMojo.getOutputTimestampOrNow(project).toInstant().equals(Instant.ofEpochSecond(1570300662)), "valid formatted property");
    }

    private DependencyNode createNode(DependencyNode parent, String gr, String art, String ver, String pack, String scope, boolean isModule, List<Artifact> runtimes, Map<Artifact, ExamineManifest> cache) {
        Artifact a = createArtifact(gr, art, ver, pack);
        org.eclipse.aether.graph.Dependency dependency = new org.eclipse.aether.graph.Dependency(a, scope);
        DependencyNode nd = new DefaultDependencyNode(dependency);
        ExamineManifest manifest = isModule ? createModule() : createNonModule();
        runtimes.add(a);
        cache.put(a, manifest);
        nd.setChildren(Collections.emptyList());
        if (parent != null) {
            ArrayList<DependencyNode> children = new ArrayList<>(parent.getChildren());
            children.add(nd);
            parent.setChildren(children);
        }
        return nd;
    }

//    private DependencyNode createNode(Artifact a, int state) {
//        DependencyNode nd = new DefaultDependencyNode(a, state, a);
//        return nd;
//    }
    private Artifact createArtifact(String gr, String art, String ver, String pack) {
        ArtifactType artifactType = artifacts.getArtifactType(pack);
        DefaultArtifact a = new DefaultArtifact(gr, art, artifactType.getExtension(), ver, null, artifactType);
        return a.setFile(new File(gr + File.separator + art + File.separator + art + "-" + ver + ".jar"));
    }

    private ExamineManifest createNonModule() {
        ExamineManifest manifest = new ExamineManifest(log);
        manifest.setNetBeansModule(false);
        return manifest;
    }

    private ExamineManifest createModule() {
        ExamineManifest manifest = new ExamineManifest(log);
        manifest.setNetBeansModule(true);
        return manifest;
    }
}
