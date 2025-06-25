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
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.netbeans.nbm.model.Dependency;
import org.apache.netbeans.nbm.model.NetBeansModule;
import org.apache.netbeans.nbm.model.io.xpp3.NetBeansModuleXpp3Reader;
import org.apache.netbeans.nbm.utils.AbstractNetbeansMojo;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public abstract class AbstractNbmMojo extends AbstractNetbeansMojo {

    static boolean matchesLibrary(Artifact artifact, List<String> libraries, ExamineManifest depExaminator,
            Log log, boolean useOsgiDependencies) {
        String artId = artifact.getArtifactId();
        String grId = artifact.getGroupId();
        String id = grId + ":" + artId;
        boolean explicit = libraries.remove(id);
        if (explicit) {
            log.debug(id + " included as module library, explicitly declared in module descriptor.");
            return explicit;
        }
        if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope()) || Artifact.SCOPE_SYSTEM.equals(
                artifact.getScope())) {
            log.debug(id + " omitted as module library, has scope 'provided/system'");
            return false;
        }
        if ("nbm".equals(artifact.getType())) {
            return false;
        }
        if (depExaminator.isNetBeansModule() || (useOsgiDependencies && depExaminator.isOsgiBundle())) {
            //TODO I can see how someone might want to include an osgi bundle as library, not dependency.
            // I guess it won't matter much in 6.9+, in older versions it could be a problem.
            return false;
        }
        log.debug(id + " included as module library, squeezed through all the filters.");
        return true;
    }

    static Dependency resolveNetBeansDependency(Artifact artifact, List<Dependency> deps, ExamineManifest manifest, Log log) {
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
        if ("nbm".equals(artifact.getType())) {
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
            NetBeansModule module = reader.read(r);
            return module;
        } catch (IOException | XmlPullParserException exc) {
            throw new MojoExecutionException("Error while reading module descriptor '" + descriptor + "'.", exc);
        }
    }

    protected final NetBeansModule createDefaultDescriptor(MavenProject project, boolean log) {

        if (log) {
            getLog().info("No Module Descriptor defined, trying to fallback to generated values:");
        }
        NetBeansModule module = new NetBeansModule();
        return module;
    }

    static List<Artifact> getLibraryArtifacts(DependencyNode treeRoot, NetBeansModule module,
            List<Artifact> runtimeArtifacts,
            Map<Artifact, ExamineManifest> examinerCache, Log log,
            boolean useOsgiDependencies)
            throws MojoExecutionException {
        List<Artifact> include = new ArrayList<>();
        if (module != null) {
            List<String> librList = new ArrayList<>();
            if (module.getLibraries() != null) {
                librList.addAll(module.getLibraries());
            }
            CollectLibrariesNodeVisitor visitor = new CollectLibrariesNodeVisitor(librList,
                    runtimeArtifacts, examinerCache, log,
                    treeRoot, useOsgiDependencies);
            treeRoot.accept(visitor);
            include.addAll(visitor.getArtifacts());
        }
        return include;
    }

    static List<ModuleWrapper> getModuleDependencyArtifacts(DependencyNode treeRoot, NetBeansModule module,
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

        @SuppressWarnings("unchecked")
        List<Artifact> artifacts = project.getCompileArtifacts();
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
            Dependency dep = resolveNetBeansDependency(artifact, deps, depExaminator, log);
            if (dep != null) {
                ModuleWrapper wr = new ModuleWrapper();
                wr.dependency = dep;
                wr.artifact = artifact;
                wr.transitive = false;
                //only direct deps matter to us..
                if (depExaminator.isNetBeansModule() && artifact.getDependencyTrail().size() > 2) {
                    log.debug(
                            artifact.getId()
                            + " omitted as NetBeans module dependency, not a direct one. "
                            + "Declare it in the pom for inclusion.");
                    wr.transitive = true;

                }
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
                    //only direct deps matter to us..
                    if (artifact.getDependencyTrail().size() > 2) {
                        log.debug(
                                artifact.getId()
                                + " omitted as NetBeans module OSGi dependency, not a direct one. "
                                + "Declare it in the pom for inclusion.");
                        wr.transitive = true;

                    } else {
                        if (print) {
                            log.info("Adding OSGi bundle dependency - " + id);
                        }
                    }

                    include.add(wr);
                }
            }
        }
        return include;
    }

    static class ModuleWrapper {

        Dependency dependency;

        Artifact artifact;

        boolean transitive = true;

        boolean osgi = false;

    }

    //copied from dependency:tree mojo
    protected DependencyNode createDependencyTree(ProjectBuildingRequest project, DependencyGraphBuilder dependencyGraphBuilder,
            String scope)
            throws MojoExecutionException {
        ArtifactFilter artifactFilter = createResolvingArtifactFilter(scope);
        try {
            return dependencyGraphBuilder.buildDependencyGraph(project, artifactFilter);
        } catch (DependencyGraphBuilderException exception) {
            throw new MojoExecutionException("Cannot build project dependency tree", exception);
        }

    }

    //copied from dependency:tree mojo
    /**
     * Gets the artifact filter to use when resolving the dependency tree.
     *
     * @return the artifact filter
     */
    private ArtifactFilter createResolvingArtifactFilter(String scope) {
        ArtifactFilter filter;

        // filter scope
        if (scope != null) {
            getLog().debug("+ Resolving dependency tree for scope '" + scope + "'");

            filter = new ScopeArtifactFilter(scope);
        } else {
            filter = null;
        }

        return filter;
    }

    protected final ArtifactResult turnJarToNbmFile(Artifact art, ArtifactFactory artifactFactory,
            ArtifactResolver artifactResolver, MavenProject project,
            MavenSession session)
            throws MojoExecutionException {
        ArtifactRepository localRepository = session.getLocalRepository();
        if ("jar".equals(art.getType()) || "nbm".equals(art.getType())) {
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
                String path = localRepository.pathOf(art);
                File jar2 = new File(localRepository.getBasedir(), path.replace("/", File.separator));
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
                Artifact nbmArt = artifactFactory.createDependencyArtifact(
                        art.getGroupId(),
                        art.getArtifactId(),
                        art.getVersionRange(),
                        "nbm-file",
                        art.getClassifier(),
                        art.getScope());
                try {
                    ProjectBuildingRequest pBuildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                    pBuildingRequest.setLocalRepository(localRepository);
                    pBuildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
                    nbmArt = artifactResolver.resolveArtifact(pBuildingRequest, nbmArt).getArtifact();
                } catch (ArtifactResolverException ex) {                   
                    //shall be check before actually resolving from repos?
                    checkReactor(art, nbmArt);
                    if (!nbmArt.isResolved()) {
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

    private void checkReactor(Artifact art, Artifact nbmArt) {
        if (art.getFile().getName().endsWith(".jar")) {
            String name = art.getFile().getName();
            name = name.substring(0, name.length() - ".jar".length()) + ".nbm";
            File fl = new File(art.getFile().getParentFile(), name);
            if (fl.exists()) {
                nbmArt.setFile(fl);
                nbmArt.setResolved(true);
            }
        }
    }

    static Date getOutputTimestampOrNow(MavenProject project) {
        return MavenArchiver.parseBuildOutputTimestamp(project.getProperties().getProperty("project.build.outputTimestamp"))
                .map(Date::from)
                .orElseGet(Date::new);
    }
}
