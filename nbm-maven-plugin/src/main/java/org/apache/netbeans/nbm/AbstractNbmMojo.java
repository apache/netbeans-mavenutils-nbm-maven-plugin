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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.netbeans.nbm.handlers.NbmFileArtifactHandler;
import org.apache.netbeans.nbm.model.Dependency;
import org.apache.netbeans.nbm.model.NetBeansModule;
import org.apache.netbeans.nbm.model.io.xpp3.NetBeansModuleXpp3Reader;
import org.apache.netbeans.nbm.utils.AbstractNetbeansMojo;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

public abstract class AbstractNbmMojo extends AbstractNetbeansMojo {
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    protected final RepositorySystem repositorySystem;
    protected final MavenProjectHelper mavenProjectHelper;
    protected final ProjectDependenciesResolver projectDependenciesResolver;
    protected final Artifacts artifacts;

    public AbstractNbmMojo(RepositorySystem repositorySystem, MavenProjectHelper mavenProjectHelper, ProjectDependenciesResolver projectDependenciesResolver, Artifacts artifacts) {
        this.repositorySystem = repositorySystem;
        this.mavenProjectHelper = mavenProjectHelper;
        this.projectDependenciesResolver = projectDependenciesResolver;
        this.artifacts = artifacts;
    }

    static Dependency resolveNetBeansDependency(Artifacts artifacts, Artifact artifact, List<Dependency> deps, ExamineManifest manifest, Log log) {
        String artId = artifact.getArtifactId();
        String grId = artifact.getGroupId();
        String id = grId + ":" + artId;
        for (Dependency dep : deps) {
            if (id.equals(dep.getId())) {
                if (manifest.isNetBeansModule()) {
                    return dep;
                } else {
                    if (dep.getExplicitValue() != null) {
                        return dep;
                    }
                    log.warn(id + " declared as module dependency in descriptor, but not a NetBeans module");
                    return null;
                }
            }
        }
        if ("nbm".equals(artifacts.getArtifactType(artifact).getId())) {
            Dependency dep = new Dependency();
            dep.setId(id);
            dep.setType("spec");
            log.debug("Adding nbm module dependency - " + id);
            return dep;
        }
        if (manifest.isNetBeansModule()) {
            Dependency dep = new Dependency();
            dep.setId(id);
            dep.setType("spec");
            log.debug("Adding direct NetBeans module dependency - " + id);
            return dep;
        }
        return null;
    }

    protected final NetBeansModule readModuleDescriptor(File descriptor) throws MojoExecutionException {
        if (descriptor == null) {
            throw new MojoExecutionException("The module descriptor has to be configured.");
        }
        if (!descriptor.exists()) {
            throw new MojoExecutionException("The module descriptor is missing: '" + descriptor + "'.");
        }
        try (Reader r = new FileReader(descriptor)) {
            NetBeansModuleXpp3Reader reader = new NetBeansModuleXpp3Reader();
            return reader.read(r);
        } catch (IOException | XmlPullParserException exc) {
            throw new MojoExecutionException("Error while reading module descriptor '" + descriptor + "'.", exc);
        }
    }

    protected final NetBeansModule createDefaultDescriptor(MavenProject project, boolean log) {
        if (log) {
            getLog().info("No Module Descriptor defined, trying to fallback to generated values:");
        }
        return new NetBeansModule();
    }

    static List<Artifact> getLibraryArtifacts(Artifacts artifacts, DependencyNode treeRoot, NetBeansModule module,
                                              Collection<Artifact> runtimeArtifacts,
                                              Map<Artifact, ExamineManifest> examinerCache, Log log,
                                              boolean useOsgiDependencies) throws MojoExecutionException {
        List<Artifact> include = new ArrayList<>();
        if (module != null) {
            List<String> librList = new ArrayList<>();
            if (module.getLibraries() != null) {
                librList.addAll(module.getLibraries());
            }
            CollectLibrariesNodeVisitor visitor = new CollectLibrariesNodeVisitor(artifacts, librList,
                    new ArrayList<>(runtimeArtifacts), examinerCache, log,
                    treeRoot, useOsgiDependencies);
            treeRoot.accept(visitor);
            include.addAll(visitor.getArtifacts());
        }
        return include;
    }

    protected List<ModuleWrapper> getModuleDependencyArtifacts(DependencyNode treeRoot, NetBeansModule module,
            Dependency[] customDependencies, MavenProject project,
            Map<Artifact, ExamineManifest> examinerCache,
            List<Artifact> libraryArtifacts, Log log,
            boolean useOsgiDependencies)
            throws MojoExecutionException {
        List<Dependency> deps = new ArrayList<>();
        if (customDependencies != null) {
            deps.addAll(Arrays.asList(customDependencies));
        }
        if (module != null && !module.getDependencies().isEmpty()) {
            log.warn("dependencies in module descriptor are deprecated, use the plugin's parameter moduleDependencies");

            //we need to make sure a dependency is not twice there, module deps override the config
            //(as is the case with other configurations)
            for (Dependency d : module.getDependencies()) {
                Dependency found = null;
                for (Dependency d2 : deps) {
                    if (d2.getId().equals(d.getId())) {
                        found = d2;
                        break;
                    }
                }
                if (found != null) {
                    deps.remove(found);
                }
                deps.add(d);
            }
        }
        List<ModuleWrapper> include = new ArrayList<ModuleWrapper>();

        // we get compile DIRECT dependencies only (to not have to discard below transitive deps)
        Set<String> compileScopes = new HashSet<>(Arrays.asList(JavaScopes.COMPILE, JavaScopes.PROVIDED, JavaScopes.SYSTEM));
        Collection<Artifact> artifacts= RepositoryUtils.toArtifacts(project.getDependencyArtifacts().stream()
                .filter(a -> a.getArtifactHandler().isAddedToClasspath())
                .filter(a -> compileScopes.contains(a.getScope()))
                .collect(Collectors.toList()));
        for (Artifact artifact : artifacts) {
            if (libraryArtifacts.contains(artifact)) {
                continue;
            }
            ExamineManifest depExaminator = examinerCache.get(artifact);
            if (depExaminator == null) {
                depExaminator = new ExamineManifest(log);
                depExaminator.setArtifactFile(artifact.getFile());
                depExaminator.checkFile();
                examinerCache.put(artifact, depExaminator);
            }
            Dependency dep = resolveNetBeansDependency(this.artifacts, artifact, deps, depExaminator, log);
            if (dep != null) {
                ModuleWrapper wr = new ModuleWrapper();
                wr.dependency = dep;
                wr.artifact = artifact;
                wr.transitive = false;
                include.add(wr);
            } else {
                if (useOsgiDependencies && depExaminator.isOsgiBundle()) {
                    ModuleWrapper wr = new ModuleWrapper();
                    wr.osgi = true;
                    String id = artifact.getGroupId() + ":" + artifact.getArtifactId();
                    for (Dependency depe : deps) {
                        if (id.equals(depe.getId())) {
                            wr.dependency = depe;
                        }
                    }
                    boolean print = false;
                    if (wr.dependency == null) {
                        Dependency depe = new Dependency();
                        depe.setId(id);
                        depe.setType("spec");
                        wr.dependency = depe;
                        print = true;
                    }

                    wr.artifact = artifact;
                    wr.transitive = false;
                    if (print) {
                        log.info("Adding OSGi bundle dependency - " + id);
                    }
                    include.add(wr);
                }
            }
        }
        return include;
    }

    public static class ModuleWrapper {
        Dependency dependency;
        Artifact artifact;
        boolean transitive = true;
        boolean osgi = false;
    }

    protected DependencyNode createDependencyTree(MavenProject project, boolean includeRuntime) throws MojoExecutionException {
        DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(project, session.getRepositorySession());
        if (includeRuntime) {
            request.setResolutionFilter(new ScopeDependencyFilter(JavaScopes.TEST));
        } else {
            request.setResolutionFilter(new ScopeDependencyFilter(JavaScopes.RUNTIME, JavaScopes.TEST));
        }

        try {
            DependencyResolutionResult result = projectDependenciesResolver.resolve(request);
            return result.getDependencyGraph();
        } catch (DependencyResolutionException exception) {
            throw new MojoExecutionException("Cannot build project dependency tree", exception);
        }

    }

    protected final ArtifactResult turnJarToNbmFile(Artifact art, MavenProject project)
            throws MojoExecutionException {
        if ("jar".equals(artifacts.getArtifactType(art).getId()) || "nbm".equals(artifacts.getArtifactType(art).getId())) {
            //TODO, it would be nice to have a check to see if the
            // "to-be-created" module nbm artifact is actually already in the
            // list of dependencies (as "nbm-file") or not..
            // that would be a timesaver
            ExamineManifest mnf = new ExamineManifest(getLog());
            File jar = art.getFile();
            if (!jar.isFile()) {
                //MNBMODULE-210 with recent CoS changes in netbeans (7.4) jar will be file as we link open projects in
                // the build via WorkspaceReader.
                // That's fine here, as all we need is to know if project is osgi or nbm module.
                // the nbm file has to be in local repository though.
                String path = artifacts.pathOf(art);
                File jar2 = new File(session.getRepositorySession().getLocalRepository().getBasedir(), path.replace("/", File.separator));
                File manifest = new File(jar, "META-INF/MANIFEST.MF");

                if (!jar2.isFile() || !manifest.isFile()) {
                    getLog().warn("MNBMODULE-131: need to at least run install phase on " + jar2);
                    return new ArtifactResult(null, null);
                }
                mnf.setManifestFile(manifest);
            } else {
                mnf.setJarFile(jar);
            }
            mnf.checkFile();
            if (mnf.isNetBeansModule()) {
                ArtifactType type = artifacts.getArtifactType(NbmFileArtifactHandler.NAME);
                HashMap<String, String> props = new HashMap<>(art.getProperties());
                props.putAll(type.getProperties());
                Artifact nbmArt = new DefaultArtifact(art.getGroupId(), art.getArtifactId(), art.getClassifier(), type.getExtension(), art.getVersion(), props, type);
                try {
                    ArtifactRequest request = new ArtifactRequest(nbmArt, project.getRemoteProjectRepositories(), "nbm");
                    org.eclipse.aether.resolution.ArtifactResult result = repositorySystem.resolveArtifact(session.getRepositorySession(), request);
                    nbmArt = result.getArtifact();
                } catch (ArtifactResolutionException ex) {
                    //shall we check before actually resolving from repos?
                    nbmArt = checkReactor(art, nbmArt);
                    if (nbmArt.getFile() == null) {
                        throw new MojoExecutionException("Failed to retrieve the nbm file from repository", ex);
                    }
                }
                return new ArtifactResult(nbmArt, mnf);
            }
            if (mnf.isOsgiBundle()) {
                return new ArtifactResult(null, mnf);
            }
        }
        return new ArtifactResult(null, null);
    }

    protected static final class ArtifactResult {
        private final Artifact converted;
        private final ExamineManifest manifest;

        ArtifactResult(Artifact conv, ExamineManifest manifest) {
            converted = conv;
            this.manifest = manifest;
        }

        boolean hasConvertedArtifact() {
            return converted != null;
        }

        Artifact getConvertedArtifact() {
            return converted;
        }

        public boolean isOSGiBundle() {
            return manifest != null && manifest.isOsgiBundle();
        }

        public ExamineManifest getExaminedManifest() {
            return manifest;
        }
    }

    private Artifact checkReactor(Artifact art, Artifact nbmArt) {
        if (art.getFile().getName().endsWith(".jar")) {
            String name = art.getFile().getName();
            name = name.substring(0, name.length() - ".jar".length()) + ".nbm";
            File fl = new File(art.getFile().getParentFile(), name);
            if (fl.exists()) {
                nbmArt = nbmArt.setFile(fl); // is resolved by having file set
            }
        }
        return nbmArt;
    }

    protected static Date getOutputTimestampOrNow(MavenProject project) {
        return MavenArchiver.parseBuildOutputTimestamp(project.getProperties().getProperty("project.build.outputTimestamp"))
                .map(Date::from)
                .orElseGet(Date::new);
    }
}
